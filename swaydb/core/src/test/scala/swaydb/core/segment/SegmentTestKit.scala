package swaydb.core.segment

import org.scalatest.matchers.should.Matchers._
import org.scalatest.PrivateMethodTester._
import swaydb.{Error, IO, TestExecutionContext}
import swaydb.config._
import swaydb.config.CoreConfigTestKit._
import swaydb.core.{CoreSpecType, CoreTestSweeper}
import swaydb.core.CoreTestSweeper._
import swaydb.core.segment.assigner.Assignable
import swaydb.core.segment.block.binarysearch.BinarySearchIndexBlockConfig
import swaydb.core.segment.block.bloomfilter.BloomFilterBlockConfig
import swaydb.core.segment.block.hashindex.HashIndexBlockConfig
import swaydb.core.segment.block.segment.SegmentBlockConfig
import swaydb.core.segment.block.segment.transient.TransientSegment
import swaydb.core.segment.block.sortedindex.SortedIndexBlockConfig
import swaydb.core.segment.block.values.ValuesBlockConfig
import swaydb.core.segment.block.SegmentBlockTestKit._
import swaydb.core.segment.data._
import swaydb.core.segment.data.merge.stats.MergeStats
import swaydb.core.segment.data.merge.KeyValueGrouper
import swaydb.core.segment.data.KeyValueTestKit._
import swaydb.core.segment.data.Memory.PendingApply
import swaydb.core.segment.distributor.PathDistributor
import swaydb.core.segment.entry.id.BaseEntryIdFormatA
import swaydb.core.segment.entry.writer.EntryWriter
import swaydb.core.segment.io.{SegmentCompactionIO, SegmentReadIO}
import swaydb.core.segment.ref.SegmentRef
import swaydb.core.segment.ref.search.SegmentSearchTestKit._
import swaydb.core.segment.ref.search.ThreadReadState
import swaydb.core.util.DefIO
import swaydb.effect.EffectTestKit._
import swaydb.effect.IOValues._
import swaydb.serializers._
import swaydb.serializers.Default._
import swaydb.slice.order.{KeyOrder, TimeOrder}
import swaydb.slice.Slice
import swaydb.slice.SliceTestKit._
import swaydb.testkit.TestKit._
import swaydb.utils.OperatingSystem

import java.nio.file.Path
import java.util.concurrent.ConcurrentSkipListMap
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext

object SegmentTestKit {

  val allBaseEntryIds = BaseEntryIdFormatA.baseIds

  def getSegmentsCache(segment: PersistentSegment): ConcurrentSkipListMap[Slice[Byte], SegmentRef] =
    segment invokePrivate PrivateMethod[ConcurrentSkipListMap[Slice[Byte], SegmentRef]](Symbol("segmentsCache"))()

  implicit class SegmentIOImplicits(io: SegmentReadIO.type) {
    def random: SegmentReadIO =
      random(cacheOnAccess = randomBoolean())

    def random(cacheOnAccess: Boolean = randomBoolean(),
               includeReserved: Boolean = true): SegmentReadIO =
      SegmentReadIO(
        fileOpenIO = genThreadSafeIOStrategy(cacheOnAccess, includeReserved),
        segmentBlockIO = _ => genIOStrategy(cacheOnAccess, includeReserved),
        hashIndexBlockIO = _ => genIOStrategy(cacheOnAccess, includeReserved),
        bloomFilterBlockIO = _ => genIOStrategy(cacheOnAccess, includeReserved),
        binarySearchIndexBlockIO = _ => genIOStrategy(cacheOnAccess, includeReserved),
        sortedIndexBlockIO = _ => genIOStrategy(cacheOnAccess, includeReserved),
        valuesBlockIO = _ => genIOStrategy(cacheOnAccess, includeReserved),
        segmentFooterBlockIO = _ => genIOStrategy(cacheOnAccess, includeReserved)
      )
  }

  implicit class SegmentsImplicits(actual: Iterable[Segment]) {

    def shouldBe(expected: Iterable[Segment]): Unit =
      actual.zip(expected) foreach {
        case (left, right) =>
          left shouldBe right
      }

    def shouldHaveSameKeyValuesAs(expected: Iterable[Segment]): Unit =
      actual.flatMap(_.iterator(randomBoolean())).runRandomIO.get shouldBe expected.flatMap(_.iterator(randomBoolean())).runRandomIO.get
  }

  implicit class SegmentImplicits(actual: Segment) {

    def clearCachedKeyValues(): Unit =
      actual match {
        case _: MemorySegment =>
        //Ignore. MemorySegment never clear cache.

        case segment: PersistentSegment =>
          segment.clearCachedKeyValues()

        case segment =>
          fail(s"Unknown segment $segment")
      }

    def clearAllCaches(): Unit =
      actual match {
        case _: MemorySegment =>
        //ignore no closing needed

        case segment: PersistentSegment =>
          segment.clearAllCaches()

        case segment =>
          fail(s"Unknown segment $segment")
      }

    def close(): Unit =
      actual match {
        case _: MemorySegment =>
        //ignore no closing needed

        case segment: PersistentSegment =>
          segment.close()

        case segment =>
          fail(s"Unknown segment $segment")
      }

    def shouldBe(expected: Segment): Unit =
      shouldBe(expected = expected, ignoreReads = false)

    def shouldBeIgnoreReads(expected: Segment): Unit =
      shouldBe(expected = expected, ignoreReads = true)

    def shouldBe(expected: Segment, ignoreReads: Boolean): Unit = {
      actual.path shouldBe expected.path
      actual.segmentNumber shouldBe expected.segmentNumber
      actual.segmentSize shouldBe expected.segmentSize
      actual.minKey shouldBe expected.minKey
      actual.maxKey shouldBe expected.maxKey
      actual.hasRange shouldBe expected.hasRange

      actual.updateCount shouldBe expected.updateCount
      actual.putDeadlineCount shouldBe expected.putDeadlineCount
      actual.putCount shouldBe expected.putCount
      actual.rangeCount shouldBe expected.rangeCount
      actual.keyValueCount shouldBe expected.keyValueCount

      actual.hasBloomFilter() shouldBe expected.hasBloomFilter
      actual.minMaxFunctionId shouldBe expected.minMaxFunctionId
      actual.nearestPutDeadline shouldBe expected.nearestPutDeadline
      actual.persistent shouldBe actual.persistent
      actual.existsOnDisk() shouldBe expected.existsOnDisk()
      actual.segmentNumber shouldBe expected.segmentNumber
      actual.getClass shouldBe expected.getClass
      if (!ignoreReads)
        assertReads(Slice.from(expected.iterator(randomBoolean()), expected.keyValueCount).runRandomIO.get, segment = actual)
    }

    def shouldContainAll(keyValues: Slice[KeyValue]): Unit =
      keyValues.foreach {
        keyValue =>
          actual.get(keyValue.key, ThreadReadState.random).runRandomIO.get.getUnsafe shouldBe keyValue
      }
  }

  def dump(segments: Iterable[Segment])(implicit functionStore: CoreFunctionStore): Iterable[String] =
    Seq(s"Segments: ${segments.size}") ++ {
      segments map {
        segment =>
          val stringInfos: Slice[String] =
            Slice.from(segment.iterator(randomBoolean()), segment.keyValueCount) mapToSlice {
              keyValue =>
                keyValue.toMemory() match {
                  case response: Memory =>
                    response match {
                      case fixed: Memory.Fixed =>
                        fixed match {
                          case Memory.Put(key, value, deadline, time) =>
                            s"""PUT - ${key.readInt()} -> ${value.toOptionC.map(_.readInt())}, ${deadline.map(_.hasTimeLeft())}, ${time.time.readLong()}"""

                          case Memory.Update(key, value, deadline, time) =>
                            s"""UPDATE - ${key.readInt()} -> ${value.toOptionC.map(_.readInt())}, ${deadline.map(_.hasTimeLeft())}, ${time.time.readLong()}"""

                          case Memory.Function(key, function, time) =>
                            s"""FUNCTION - ${key.readInt()} -> ${functionStore.get(function)}, ${time.time.readLong()}"""

                          case PendingApply(key, applies) =>
                            //                        s"""
                            //                           |${key.readInt()} -> ${functionStore.find(function)}, ${time.time.readLong()}
                            //                        """.stripMargin
                            "PENDING-APPLY"

                          case Memory.Remove(key, deadline, time) =>
                            s"""REMOVE - ${key.readInt()} -> ${deadline.map(_.hasTimeLeft())}, ${time.time.readLong()}"""
                        }

                      case Memory.Range(fromKey, toKey, fromValue, rangeValue) =>
                        s"""RANGE - ${fromKey.readInt()} -> ${toKey.readInt()}, $fromValue (${fromValue.toOptionS.map(Value.hasTimeLeft)}), $rangeValue (${Value.hasTimeLeft(rangeValue)})"""
                    }
                }
            }

          s"""
             |segment: ${segment.path}
             |${stringInfos.mkString("\n")}
             |""".stripMargin + "\n"
      }
    }

  implicit class ReopenSegment(segment: PersistentSegment)(implicit keyOrder: KeyOrder[Slice[Byte]] = KeyOrder.default,
                                                           timeOrder: TimeOrder[Slice[Byte]] = TimeOrder.long,
                                                           sweeper: CoreTestSweeper,
                                                           segmentIO: SegmentReadIO = SegmentReadIO.random) {

    import sweeper._

    implicit val keyOrders: SegmentKeyOrders =
      SegmentKeyOrders(keyOrder)

    def tryReopen: PersistentSegment =
      tryReopen(segment.path)

    def tryReopen(path: Path): PersistentSegment = {
      val reopenedSegment =
        PersistentSegment(
          path = path,
          formatId = segment.formatId,
          createdInLevel = segment.createdInLevel,
          segmentRefCacheLife = randomSegmentRefCacheLife(),
          mmap = MMAP.randomForSegment(),
          minKey = segment.minKey,
          maxKey = segment.maxKey,
          segmentSize = segment.segmentSize,
          minMaxFunctionId = segment.minMaxFunctionId,
          updateCount = segment.updateCount,
          rangeCount = segment.rangeCount,
          putCount = segment.putCount,
          putDeadlineCount = segment.putDeadlineCount,
          keyValueCount = segment.keyValueCount,
          nearestExpiryDeadline = segment.nearestPutDeadline,
          copiedFrom = someOrNone(segment)
        ).sweep()

      segment.close()
      reopenedSegment
    }

    def reopen: PersistentSegment =
      tryReopen.runRandomIO.get

    def reopen(path: Path): PersistentSegment =
      tryReopen(path).runRandomIO.get

    def get(key: Slice[Byte]): KeyValueOption =
      segment.get(key, ThreadReadState.random)

    def get(key: Int): KeyValueOption =
      segment.get(key, ThreadReadState.random)

    def higher(key: Int): KeyValueOption =
      segment.higher(key, ThreadReadState.random)

    def higher(key: Slice[Byte]): KeyValueOption =
      segment.higher(key, ThreadReadState.random)

    def lower(key: Int): KeyValueOption =
      segment.lower(key, ThreadReadState.random)

    def lower(key: Slice[Byte]): KeyValueOption =
      segment.lower(key, ThreadReadState.random)
  }

  implicit class AssignablesImplicits(keyValues: ListBuffer[Assignable]) {

    /**
     * Ensures that the [[keyValues]] contains only expanded [[KeyValue]]s
     * and no collections.
     */
    def expectKeyValues(): Iterable[KeyValue] =
      keyValues collect {
        case collection: Assignable.Collection =>
          fail(s"Expected KeyValue found ${collection.getClass} with ${collection.keyValueCount} key-values.")

        case keyValue: KeyValue =>
          keyValue

      }

    def expectSegments(): Iterable[Segment] =
      keyValues collect {
        case collection: Assignable.Collection =>
          collection match {
            case segment: Segment =>
              segment

            case other =>
              fail(s"Expected ${Segment.productPrefix} found ${other.getClass}.")
          }

        case keyValue: KeyValue =>
          fail(s"Expected ${Segment.productPrefix} found ${keyValue.getClass}.")
      }

    def expectSegmentRefs(): Iterable[SegmentRef] =
      keyValues collect {
        case collection: Assignable.Collection =>
          collection match {
            case segment: SegmentRef =>
              segment

            case other =>
              fail(s"Expected ${SegmentRef.productPrefix} found ${other.getClass}.")
          }

        case keyValue: KeyValue =>
          fail(s"Expected ${SegmentRef.productPrefix} found ${keyValue.getClass}.")
      }
  }

  implicit class TransientSegmentPersistentImplicits(segment: TransientSegment.Persistent) {

    def persist(pathDistributor: PathDistributor,
                segmentRefCacheLife: SegmentRefCacheLife = randomSegmentRefCacheLife(),
                mmap: MMAP.Segment = MMAP.randomForSegment())(implicit keyOrder: KeyOrder[Slice[Byte]],
                                                              segmentReadIO: SegmentReadIO,
                                                              timeOrder: TimeOrder[Slice[Byte]],
                                                              testCaseSweeper: CoreTestSweeper): IO[Error.Segment, Slice[PersistentSegment]] =
      Slice(segment).persist(
        pathDistributor = pathDistributor,
        segmentRefCacheLife = segmentRefCacheLife,
        mmap = mmap
      )
  }

  implicit class TransientSegmentsImplicits(segments: Slice[TransientSegment.Persistent]) {

    def persist(pathDistributor: PathDistributor,
                segmentRefCacheLife: SegmentRefCacheLife = randomSegmentRefCacheLife(),
                mmap: MMAP.Segment = MMAP.randomForSegment())(implicit keyOrder: KeyOrder[Slice[Byte]],
                                                              segmentReadIO: SegmentReadIO,
                                                              timeOrder: TimeOrder[Slice[Byte]],
                                                              testCaseSweeper: CoreTestSweeper): IO[Error.Segment, Slice[PersistentSegment]] = {
      //      import testCaseSweeper._
      //
      //      val persistedSegments =
      //        SegmentWritePersistentIO.persistTransient(
      //          pathDistributor = pathDistributor,
      //          segmentRefCacheLife = segmentRefCacheLife,
      //          mmap = mmap,
      //          transient = segments
      //        )
      //
      //      persistedSegments.foreach(_.foreach(_.sweep()))
      //
      //      persistedSegments map {
      //        persistedSegments =>
      //          Slice.from(persistedSegments, persistedSegments.size)
      //      }
      ???
    }
  }

  implicit class GenSegmentImplicits(segment: Segment) {

    import swaydb.testkit.RunThis._

    def put(headGap: Iterable[KeyValue],
            tailGap: Iterable[KeyValue],
            newKeyValues: Iterator[Assignable],
            removeDeletes: Boolean,
            createdInLevel: Int,
            valuesConfig: ValuesBlockConfig,
            sortedIndexConfig: SortedIndexBlockConfig,
            binarySearchIndexConfig: BinarySearchIndexBlockConfig,
            hashIndexConfig: HashIndexBlockConfig,
            bloomFilterConfig: BloomFilterBlockConfig,
            segmentConfig: SegmentBlockConfig,
            pathDistributor: PathDistributor,
            segmentRefCacheLife: SegmentRefCacheLife,
            mmapSegment: MMAP.Segment)(implicit executionContext: ExecutionContext,
                                       keyOrder: KeyOrder[Slice[Byte]],
                                       segmentReadIO: SegmentReadIO,
                                       timeOrder: TimeOrder[Slice[Byte]],
                                       testCaseSweeper: CoreTestSweeper,
                                       compactionActor: SegmentCompactionIO.Actor): DefIO[SegmentOption, Slice[Segment]] = {
      def toMemory(keyValue: KeyValue) = if (removeDeletes) KeyValueGrouper.toLastLevelOrNull(keyValue) else keyValue.toMemory()
      import testCaseSweeper.idGenerator

      segment match {
        case segment: MemorySegment =>

          val putResult =
            segment.put(
              headGap = ListBuffer(Assignable.Stats(MergeStats.memoryBuilder(headGap)(toMemory))),
              tailGap = ListBuffer(Assignable.Stats(MergeStats.memoryBuilder(tailGap)(toMemory))),
              newKeyValues = newKeyValues,
              removeDeletes = removeDeletes,
              createdInLevel = createdInLevel,
              segmentConfig = segmentConfig
            ).awaitInf

          putResult.input match {
            case MemorySegment.Null =>
              DefIO(
                input = Segment.Null,
                output = putResult.output.toSlice
              )

            case segment: MemorySegment =>
              DefIO(
                input = segment,
                output = putResult.output.toSlice
              )
          }

        case segment: PersistentSegment =>

          val putResult =
            segment.put(
              headGap = ListBuffer(Assignable.Stats(MergeStats.persistentBuilder(headGap)(toMemory))),
              tailGap = ListBuffer(Assignable.Stats(MergeStats.persistentBuilder(tailGap)(toMemory))),
              newKeyValues = newKeyValues,
              removeDeletes = removeDeletes,
              createdInLevel = createdInLevel,
              valuesConfig = valuesConfig,
              sortedIndexConfig = sortedIndexConfig,
              binarySearchIndexConfig = binarySearchIndexConfig,
              hashIndexConfig = hashIndexConfig,
              bloomFilterConfig = bloomFilterConfig,
              segmentConfig = segmentConfig,
              pathDistributor = pathDistributor,
              segmentRefCacheLife = segmentRefCacheLife,
              mmap = mmapSegment
            ).awaitInf

          putResult.input match {
            case PersistentSegment.Null =>
              DefIO(
                input = Segment.Null,
                output = putResult.output.toSlice
              )

            case segment: PersistentSegment =>
              DefIO(
                input = segment,
                output = putResult.output.toSlice
              )
          }

      }
    }

    def refresh(removeDeletes: Boolean,
                createdInLevel: Int,
                valuesConfig: ValuesBlockConfig,
                sortedIndexConfig: SortedIndexBlockConfig,
                binarySearchIndexConfig: BinarySearchIndexBlockConfig,
                hashIndexConfig: HashIndexBlockConfig,
                bloomFilterConfig: BloomFilterBlockConfig,
                segmentConfig: SegmentBlockConfig,
                pathDistributor: PathDistributor)(implicit executionContext: ExecutionContext,
                                                  keyOrder: KeyOrder[Slice[Byte]],
                                                  segmentReadIO: SegmentReadIO,
                                                  timeOrder: TimeOrder[Slice[Byte]],
                                                  testCaseSweeper: CoreTestSweeper): Slice[Segment] = {
      import testCaseSweeper.idGenerator

      segment match {
        case segment: MemorySegment =>
          segment.refresh(
            removeDeletes = removeDeletes,
            createdInLevel = createdInLevel,
            segmentConfig = segmentConfig
          ).output

        case segment: PersistentSegment =>
          val putResult =
            segment.refresh(
              removeDeletes = removeDeletes,
              createdInLevel = createdInLevel,
              valuesConfig = valuesConfig,
              sortedIndexConfig = sortedIndexConfig,
              binarySearchIndexConfig = binarySearchIndexConfig,
              hashIndexConfig = hashIndexConfig,
              bloomFilterConfig = bloomFilterConfig,
              segmentConfig = segmentConfig
            ).awaitInf.output

          putResult.persist(pathDistributor).get
      }
    }
  }

  def randomBuilder(enablePrefixCompressionForCurrentWrite: Boolean = randomBoolean(),
                    prefixCompressKeysOnly: Boolean = randomBoolean(),
                    compressDuplicateValues: Boolean = randomBoolean(),
                    enableAccessPositionIndex: Boolean = randomBoolean(),
                    optimiseForReverseIteration: Boolean = randomBoolean(),
                    allocateBytes: Int = 10000): EntryWriter.Builder = {
    val builder =
      EntryWriter.Builder(
        prefixCompressKeysOnly = prefixCompressKeysOnly,
        compressDuplicateValues = compressDuplicateValues,
        enableAccessPositionIndex = enableAccessPositionIndex,
        optimiseForReverseIteration = optimiseForReverseIteration,
        bytes = Slice.allocate[Byte](allocateBytes)
      )

    builder.enablePrefixCompressionForCurrentWrite = enablePrefixCompressionForCurrentWrite
    builder
  }

  def mmapSegments: MMAP.Segment =
    MMAP.On(OperatingSystem.isWindows(), GenForceSave.mmap())

  def isWindowsAndMMAPSegments(): Boolean =
    OperatingSystem.isWindows() && mmapSegments.mmapReads && mmapSegments.mmapWrites

  //  def testSegmentFile()(implicit coreSpecType: CoreSpecType,
  //                        sweeper: CoreTestSweeper): Path =
  //    if (coreSpecType.isMemorySpec)
  //      randomIntDirectory
  //        .resolve(sweeper.idGenerator.nextSegmentId())
  //        .sweep()
  //    else
  //      Effect
  //        .createDirectoriesIfAbsent(randomIntDirectory)
  //        .resolve(sweeper.idGenerator.nextSegmentId())
  //        .sweep()


  def assertSegment[T](keyValues: Slice[Memory],
                       assert: (Slice[Memory], Segment) => T,
                       segmentConfig: SegmentBlockConfig = SegmentBlockConfig.random.copy(mmap = mmapSegments),
                       ensureOneSegmentOnly: Boolean = false,
                       testAgainAfterAssert: Boolean = true,
                       closeAfterCreate: Boolean = false,
                       valuesConfig: ValuesBlockConfig = ValuesBlockConfig.random,
                       sortedIndexConfig: SortedIndexBlockConfig = SortedIndexBlockConfig.random,
                       binarySearchIndexConfig: BinarySearchIndexBlockConfig = BinarySearchIndexBlockConfig.random,
                       hashIndexConfig: HashIndexBlockConfig = HashIndexBlockConfig.random,
                       bloomFilterConfig: BloomFilterBlockConfig = BloomFilterBlockConfig.random)(implicit keyOrder: KeyOrder[Slice[Byte]] = KeyOrder.default,
                                                                                                  sweeper: CoreTestSweeper,
                                                                                                  coreSpecType: CoreSpecType,
                                                                                                  segmentIO: SegmentReadIO = SegmentReadIO.random,
                                                                                                  ec: ExecutionContext = TestExecutionContext.executionContext) = {
    println(s"assertSegment - keyValues: ${keyValues.size}")

    val segment =
      if (ensureOneSegmentOnly)
        GenSegment.one(
          keyValues = keyValues,
          valuesConfig = valuesConfig,
          sortedIndexConfig = sortedIndexConfig,
          binarySearchIndexConfig = binarySearchIndexConfig,
          hashIndexConfig = hashIndexConfig,
          bloomFilterConfig = bloomFilterConfig,
          segmentConfig = segmentConfig
        )
      else
        GenSegment(
          keyValues = keyValues,
          valuesConfig = valuesConfig,
          sortedIndexConfig = sortedIndexConfig,
          binarySearchIndexConfig = binarySearchIndexConfig,
          hashIndexConfig = hashIndexConfig,
          bloomFilterConfig = bloomFilterConfig,
          segmentConfig = segmentConfig
        )

    if (closeAfterCreate) segment.close()

    assert(keyValues, segment) //first
    if (testAgainAfterAssert) {
      assert(keyValues, segment) //with cache populated

      //clear cache and assert
      segment.clearCachedKeyValues()
      assert(keyValues, segment) //same Segment but test with cleared cache.

      //clear all caches and assert
      segment.clearAllCaches()
      assert(keyValues, segment) //same Segment but test with cleared cache.
    }

    segment match {
      case segment: PersistentSegment =>
        val segmentReopened = segment.reopen //reopen
        if (closeAfterCreate) segmentReopened.close()
        assert(keyValues, segmentReopened)

        if (testAgainAfterAssert) assert(keyValues, segmentReopened)
        segmentReopened.close()

      case _: Segment =>
      //memory segment cannot be reopened
    }

    segment.close()
  }

}
