///*
// * Copyright 2018 Simer JS Plaha (simer.j@gmail.com - @simerplaha)
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// * http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package swaydb.core.segment
//
//import org.scalatest.OptionValues.convertOptionToValuable
//import swaydb.Error.Segment.ExceptionHandler
//import swaydb.effect.IOValues._
//import swaydb.config.MMAP
//import swaydb.core.CommonAssertions._
//import swaydb.core.PrivateMethodInvokers._
//import swaydb.core.CoreTestSweeper._
//import swaydb.core.CoreTestData._
//import swaydb.core._
//import swaydb.core.file.sweeper.FileSweeper
//import swaydb.core.file.sweeper.bytebuffer.ByteBufferSweeper
//import swaydb.core.segment.cache.sweeper.MemorySweeperTestKit
//
//import scala.concurrent.ExecutionContext
//////import swaydb.core.segment.block.BlockCacheState
//import swaydb.core.segment.block.binarysearch.BinarySearchIndexBlockConfig
//import swaydb.core.segment.block.bloomfilter.BloomFilterBlockConfig
//import swaydb.core.segment.block.hashindex.HashIndexBlockConfig
//import swaydb.core.segment.block.segment.SegmentBlockConfig
//import swaydb.core.segment.block.sortedindex.SortedIndexBlockConfig
//import swaydb.core.segment.block.values.ValuesBlockConfig
//import swaydb.core.segment.data.Value.FromValue
//import swaydb.core.segment.data._
//import swaydb.core.segment.data.merge.KeyValueMerger
//import swaydb.core.segment.data.merge.stats.MergeStats
//import swaydb.core.segment.io.SegmentReadIO
//import swaydb.core.segment.ref.SegmentRef
//import swaydb.core.segment.ref.search.ThreadReadState
//import swaydb.utils.{ByteSizeOf, Extension, IDGenerator, OperatingSystem}
//import swaydb.effect.Effect._
//import swaydb.effect.Effect.implicits._
//import swaydb.effect.{Dir, Effect}
//import swaydb.serializers.Default._
//import swaydb.serializers._
//import swaydb.slice.order.{KeyOrder, TimeOrder}
//import swaydb.slice.{MaxKey, Slice}
//import swaydb.testkit.RunThis._
//import swaydb.utils.StorageUnits._
//import swaydb.{ActorConfig, Benchmark, IO}
//import swaydb.core.level.ALevelSpec
//
//import java.nio.file.{FileAlreadyExistsException, NoSuchFileException}
//import scala.collection.mutable.ListBuffer
//import scala.concurrent.duration._
//import scala.jdk.CollectionConverters._
//import scala.util.Random
//import swaydb.testkit.TestKit._
//
//import swaydb.core.segment.data.KeyValueTestKit._
//import swaydb.core.segment.SegmentTestKit._
//import swaydb.slice.SliceTestKit._
//import swaydb.core.segment.block.SegmentBlockTestKit._
//import swaydb.config.CoreConfigTestKit._
//import org.scalatest.matchers.should.Matchers._
//import swaydb.core.segment.data.merge.SegmentMergeTestKit._
//import swaydb.core.log.timer.TestTimer
//import swaydb.effect.EffectTestKit._
//import swaydb.core.compression.CompressionTestKit._
//import swaydb.core.segment.block.SegmentBlockTestKit._
//import swaydb.core.file.CoreFileTestKit._
//import swaydb.core.segment.ref.search.SegmentSearchTestKit._
//import org.scalatest.wordspec.AnyWordSpec
//import swaydb.actor.ActorTestKit._
//import swaydb.utils.UtilsTestKit._
//import swaydb.core.CoreSpecType
//import swaydb.TestExecutionContext
//import swaydb.slice.order.TimeOrder
//import swaydb.slice.Slice
//
//class SegmentWriteSpec extends AnyWordSpec {
//
//  val keyValuesCount = 100
//
//  implicit val testTimer: TestTimer = TestTimer.Empty
//  implicit val ec: ExecutionContext = TestExecutionContext.executionContext
//  implicit val keyOrder: KeyOrder[Slice[Byte]] = KeyOrder.default
//  implicit val timeOrder: TimeOrder[Slice[Byte]] = TimeOrder.long
//
//  implicit def segmentIO: SegmentReadIO = SegmentReadIO.random
//
//  "Segment" should {
//
//    "create a Segment" in {
//      runThis(100.times, log = true) {
//        CoreTestSweeper {
//          implicit sweeper =>
//            assertSegment(
//              keyValues =
//                randomizedKeyValues(eitherOne(randomIntMax(keyValuesCount) max 1, keyValuesCount)),
//              //            Slice(randomPutKeyValue(1, None, None)(previous = None, sortedIndexConfig = SegmentIndexBlockConfig.random.copy(disableKeyPrefixCompression = true, prefixCompressionResetCount = 0))),
//
//              assert =
//                (keyValues, segment) => {
//                  segment.keyValueCount shouldBe keyValues.size
//
//                  assertReads(keyValues, segment)
//
//                  segment.segmentNumber shouldBe Effect.numberFileId(segment.path)._1
//
//                  segment.minKey shouldBe keyValues.head.key
//                  segment.maxKey shouldBe {
//                    keyValues.last match {
//                      case _: Memory.Fixed =>
//                        MaxKey.Fixed[Slice[Byte]](keyValues.last.key)
//
//                      case range: Memory.Range =>
//                        MaxKey.Range[Slice[Byte]](range.fromKey, range.toKey)
//                    }
//                  }
//                  //ensure that min and max keys are slices
//                  segment.minKey.underlyingArraySize shouldBe 4
//                  segment.maxKey match {
//                    case MaxKey.Fixed(maxKey) =>
//                      maxKey.underlyingArraySize shouldBe 4
//
//                    case MaxKey.Range(fromKey, maxKey) =>
//                      fromKey.underlyingArraySize shouldBe 4
//                      maxKey.underlyingArraySize shouldBe 4
//                  }
//
//                  assertBloom(keyValues, segment)
//                }
//            )
//        }
//      }
//    }
//
//    "set minKey & maxKey to be Fixed if the last key-value is a Fixed key-value" in {
//      runThis(50.times) {
//        CoreTestSweeper {
//          implicit sweeper =>
//            assertSegment(
//              keyValues =
//                Slice(randomRangeKeyValue(1, 10), randomFixedKeyValue(11)),
//
//              assert =
//                (keyValues, segment) => {
//                  segment.minKey shouldBe (1: Slice[Byte])
//                  segment.maxKey shouldBe MaxKey.Fixed[Slice[Byte]](11)
//                  segment.minKey.underlyingArraySize shouldBe ByteSizeOf.int
//                  segment.maxKey.maxKey.underlyingArraySize shouldBe ByteSizeOf.int
//                  segment.rangeCount shouldBe 1
//                  segment.keyValueCount shouldBe 2
//                }
//            )
//        }
//      }
//    }
//
//    "set minKey & maxKey to be Range if the last key-value is a Range key-value" in {
//      runThis(50.times) {
//        CoreTestSweeper {
//          implicit sweeper =>
//            assertSegment(
//              keyValues = Slice(randomFixedKeyValue(0), randomRangeKeyValue(1, 10)),
//
//              assert =
//                (keyValues, segment) => {
//                  segment.minKey shouldBe (0: Slice[Byte])
//                  segment.maxKey shouldBe MaxKey.Range[Slice[Byte]](1, 10)
//                }
//            )
//        }
//      }
//    }
//
//    "un-slice Segment's minKey & maxKey and also un-slice cache key-values" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//          import sweeper._
//          //set this MemorySweeper as root sweeper.
//          CoreTestSweepers.createMemorySweeperMax().value.sweep()
//
//          //assert that all key-values added to cache are not sub-slices.
//          def assertCacheKeyValuesAreCut(segment: Segment) = {
//            val skipList =
//              segment match {
//                case segment: MemorySegment =>
//                  Slice(segment.skipList)
//
//                case segment: PersistentSegmentOne =>
//                  Slice(segment.ref.skipList.get)
//
//                case segment: PersistentSegmentMany =>
//                  segment.segmentRefs(randomBoolean()).map(_.skipList.get)
//              }
//
//            skipList.size should be > 0
//
//            skipList foreach {
//              skipList =>
//                skipList.toIterable foreach {
//                  case (key, value: KeyValue) =>
//                    key.shouldBeCut()
//                    assertCut(value)
//                }
//            }
//          }
//
//          def assertMinAndMaxKeyAreCut(segment: Segment) = {
//            segment.minKey.underlyingArraySize shouldBe 1
//            segment.maxKey match {
//              case MaxKey.Fixed(maxKey) =>
//                maxKey.underlyingArraySize shouldBe 1
//
//              case MaxKey.Range(fromKey, maxKey) =>
//                fromKey.underlyingArraySize shouldBe 1
//                maxKey.underlyingArraySize shouldBe 1
//            }
//          }
//
//          def doAssert(keyValues: Slice[Memory]) = {
//
//            //read key-values so they are all part of the same byte array.
//            val readKeyValues = writeAndRead(keyValues).get.mapToSlice(_.toMemory())
//
//            //assert that readKeyValues keys are not sliced.
//            readKeyValues foreach assertNotCut
//
//            //Create Segment with sub-slice key-values and assert min & maxKey and also check that cached key-values are un-sliced.
//            assertSegment(
//              keyValues = readKeyValues,
//
//              assert =
//                (keyValues, segment) => {
//                  assertMinAndMaxKeyAreCut(segment)
//                  //if Persistent Segment, read all key-values from disk so that they value added to cache.
//                  if (isPersistent) assertGet(readKeyValues, segment)
//                  //assert key-values added to cache are un-sliced
//                  assertCacheKeyValuesAreCut(segment)
//                }
//            )
//          }
//
//          runThis(100.times, log = true) {
//            //unique data to avoid compression.
//            //this test asserts that key-values are not sliced unnecessarily.
//            val data = ('a' to 'z').toArray
//            var index = 0
//
//            def nextData: String = {
//              val next = data(index)
//              index += 1
//              next.toString
//            }
//
//            val uninitialisedKeyValues =
//              List(
//                () => randomFixedKeyValue(nextData, nextData),
//                () => randomFixedKeyValue(nextData, nextData),
//                () => randomRangeKeyValue(nextData, nextData, randomFromValueOption(nextData), randomRangeValue(nextData))
//              )
//
//            doAssert(
//              Random.shuffle(uninitialisedKeyValues ++ uninitialisedKeyValues).toSlice.mapToSlice(_ ())
//            )
//          }
//      }
//    }
//
//    "not create bloomFilter if the Segment has Remove range key-values or function key-values and set hasRange to true" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//          //adjustSegmentConfig = false so that Many Segments do not get created.
//
//          def doAssert(keyValues: Slice[KeyValue], segment: Segment) = {
//            segment.hasBloomFilter() shouldBe false
//            assertBloom(keyValues, segment)
//            segment.hasRange.runRandomIO.get shouldBe true
//            segment.close.runRandomIO.get
//          }
//
//          assertSegment(
//            keyValues = Slice(Memory.put(0), Memory.Range(1, 10, FromValue.Null, Value.remove(randomDeadlineOption(), Time.empty))),
//            ensureOneSegmentOnly = true,
//            assert = doAssert
//          )
//
//          assertSegment(
//            keyValues = Slice(Memory.put(0), Memory.Range(1, 10, Value.remove(None, Time.empty), Value.remove(randomDeadlineOption(), Time.empty))),
//            ensureOneSegmentOnly = true,
//            assert = doAssert
//          )
//
//          assertSegment(
//            keyValues = Slice(Memory.put(0), Memory.Range(1, 10, Value.update(Slice.Null, randomDeadlineOption(), Time.empty), Value.remove(randomDeadlineOption(), Time.empty))),
//            ensureOneSegmentOnly = true,
//            assert = doAssert
//          )
//
//          assertSegment(
//            keyValues = Slice(Memory.put(0), Memory.Range(1, 10, Value.put(1, randomDeadlineOption(), Time.empty), Value.remove(randomDeadlineOption(), Time.empty))),
//            ensureOneSegmentOnly = true,
//            assert = doAssert
//          )
//      }
//
//      //      assertSegment(
//      //        keyValues = Slice(Memory.put(0), Memory.Range(1, 10, Some(Value.PendingApply(Some(1), randomDeadlineOption(), Time.empty)), Value.remove(randomDeadlineOption(), Time.empty))),
//      //        assert = doAssert
//      //      )
//    }
//
//    "create bloomFilter if the Segment has no Remove range key-values but has update range key-values. And set hasRange to true" in {
//      if (isPersistent) {
//        CoreTestSweeper {
//          implicit sweeper =>
//            assertSegment(
//              keyValues =
//                Slice(Memory.put(0), Memory.put(1, 1), Memory.remove(2, randomDeadlineOption())),
//
//              bloomFilterConfig =
//                BloomFilterBlockConfig(
//                  falsePositiveRate = 0.001,
//                  minimumNumberOfKeys = 0,
//                  optimalMaxProbe = optimalMaxProbe => optimalMaxProbe,
//                  ioStrategy = _ => genIOStrategy(),
//                  compressions = _ => randomCompressionsOrEmpty()
//                ),
//
//              assert =
//                (keyValues, segment) => {
//                  segment.hasBloomFilter().runRandomIO.get shouldBe true
//                  segment.hasRange.runRandomIO.get shouldBe false
//
//                  segment.rangeCount shouldBe 0
//                  segment.updateCount shouldBe 1
//                  segment.putCount shouldBe 2
//                  segment.putDeadlineCount shouldBe 0
//
//                  segment.close.runRandomIO.get
//                }
//            )
//
//            assertSegment(
//              keyValues =
//                Slice(Memory.put(0), Memory.Range(1, 10, FromValue.Null, Value.update(10, randomDeadlineOption()))),
//
//              bloomFilterConfig =
//                BloomFilterBlockConfig(
//                  falsePositiveRate = 0.001,
//                  minimumNumberOfKeys = 0,
//                  optimalMaxProbe = optimalMaxProbe => optimalMaxProbe,
//                  ioStrategy = _ => genIOStrategy(),
//                  compressions = _ => randomCompressionsOrEmpty()
//                ),
//
//              assert =
//                (keyValues, segment) => {
//                  segment.hasBloomFilter().runRandomIO.get shouldBe true
//                  segment.hasRange.runRandomIO.get shouldBe true
//
//                  segment.rangeCount shouldBe 1
//                  segment.updateCount shouldBe 0
//                  segment.putCount shouldBe 1
//                  segment.putDeadlineCount shouldBe 0
//
//                  segment.close.runRandomIO.get
//                }
//            )
//        }
//      }
//    }
//
//    "set hasRange to true if the Segment contains Range key-values" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//          def doAssert(keyValues: Slice[KeyValue], segment: Segment): Unit = {
//            segment.hasRange shouldBe true
//            segment.putCount shouldBe getPuts(keyValues).size
//            segment.putDeadlineCount shouldBe getPutsWithDeadline(keyValues).size
//            segment.updateCount shouldBe getUpdates(keyValues).size
//            segment.rangeCount shouldBe getRanges(keyValues).size
//          }
//
//          assertSegment(
//            keyValues = Slice(Memory.put(0), Memory.Range(1, 10, FromValue.Null, Value.update(10))),
//            assert = doAssert
//          )
//
//          assertSegment(
//            keyValues = Slice(Memory.put(0), Memory.Range(1, 10, Value.remove(None, Time.empty), Value.update(10))),
//            assert = doAssert
//          )
//
//          assertSegment(
//            keyValues = Slice(Memory.put(0), Memory.Range(1, 10, Value.put(1), Value.update(10))),
//            assert = doAssert
//          )
//
//          assertSegment(
//            keyValues = Slice(Memory.put(0), Memory.Range(1, 10, FromValue.Null, Value.remove(None, Time.empty))),
//            assert = doAssert
//          )
//
//          assertSegment(
//            keyValues = Slice(Memory.put(0), Memory.Range(1, 10, Value.remove(10.seconds.fromNow), Value.remove(None, Time.empty))),
//            assert = doAssert
//          )
//
//          assertSegment(
//            keyValues = Slice(Memory.put(0), Memory.Range(1, 10, Value.put(1), Value.remove(None, Time.empty))),
//            assert = doAssert
//          )
//
//          runThis(100.times) {
//            assertSegment(
//              keyValues = Slice(Memory.put(0), randomRangeKeyValue(1, 10)),
//              assert = doAssert
//            )
//          }
//
//          assertSegment(
//            keyValues = randomPutKeyValues(keyValuesCount, addRemoves = true, addRanges = true, addPutDeadlines = true, addRemoveDeadlines = true),
//            assert = doAssert
//          )
//      }
//    }
//
//    "not overwrite a Segment if it already exists" in {
//      if (isMemorySpec) {
//        //memory Segments do not check for overwrite. No tests required
//      } else {
//        CoreTestSweeper {
//          implicit sweeper =>
//            assertSegment(
//              keyValues =
//                randomPutKeyValues(keyValuesCount),
//
//              assert =
//                (keyValues, segment) => {
//                  val failedKV = randomKeyValues(keyValuesCount, addRemoves = true)
//                  val reopenedSegment = IO(GenSegment(failedKV, path = segment.path))
//                  reopenedSegment.left.right.value.exception shouldBe a[FileAlreadyExistsException]
//                  //data remained unchanged
//                  assertReads(keyValues, segment)
//                  val readState = ThreadReadState.random
//                  failedKV foreach {
//                    keyValue =>
//                      segment.get(keyValue.key, readState).runRandomIO.get.toOption shouldBe empty
//                  }
//                  assertBloom(keyValues, segment)
//                }
//            )
//        }
//      }
//    }
//
//    "initialise a segment that already exists" in {
//      if (isMemorySpec) {
//        //memory Segments cannot re-initialise Segments after shutdown.
//      } else {
//        runThis(10.times) {
//          CoreTestSweeper {
//            implicit sweeper =>
//              //set this MemorySweeper as root sweeper.
//              CoreTestSweepers.createKeyValueSweeperBlock().value.sweep()
//
//              assertSegment(
//                keyValues =
//                  randomizedKeyValues(keyValuesCount),
//
//                segmentConfig = SegmentBlockConfig.random(cacheBlocksOnCreate = false, mmap = mmapSegments),
//
//                closeAfterCreate =
//                  true,
//
//                testAgainAfterAssert =
//                  false,
//
//                assert =
//                  (keyValues, segment) => {
//                    segment.isOpen shouldBe false
//                    segment.isCached shouldBe false
//                    segment.isKeyValueCacheEmpty shouldBe true
//
//                    assertHigher(keyValues, segment)
//
//                    segment.isOpen shouldBe true
//                    segment.isCached shouldBe true
//                    segment.isKeyValueCacheEmpty shouldBe false
//
//                    assertBloom(keyValues, segment)
//                    segment.close.runRandomIO.get
//                    segment.isOpen shouldBe false
//                    segment.isCached shouldBe false
//
//                    segment.isKeyValueCacheEmpty shouldBe segment.isInstanceOf[PersistentSegmentMany]
//                  }
//              )
//          }
//        }
//      }
//    }
//
//    "initialise a segment that already exists but Segment info is unknown" in {
//      if (isMemorySpec) {
//        //memory Segments cannot re-initialise Segments after shutdown.
//      } else {
//        runThis(100.times, log = true) {
//          CoreTestSweeper {
//            implicit sweeper =>
//              import sweeper._
//
//              assertSegment(
//                keyValues = randomizedKeyValues(keyValuesCount, startId = Some(0)),
//
//                assert =
//                  (keyValues, segment) => {
//                    val readSegment =
//                      Segment(
//                        path = segment.path,
//                        mmap = MMAP.randomForSegment(),
//                        checkExists = randomBoolean()
//                      ).sweep()
//
//                    readSegment shouldBe segment
//                  }
//              )
//          }
//        }
//      }
//    }
//
//    "fail initialisation if the segment does not exist" in {
//      if (isMemorySpec) {
//        //memory Segments do not value re-initialised
//      } else {
//        CoreTestSweeper {
//          implicit sweeper =>
//
//            //memory-mapped files on windows get submitted to ByteBufferCleaner.
//            implicit val bufferCleaner = ByteBufferSweeper(messageReschedule = 0.seconds).sweep()
//            bufferCleaner.actor
//
//            //create a segment and delete it
//            val segment = GenSegment(randomizedKeyValues(100))
//            segment.delete()
//
//            eventual(20.seconds) {
//              assertThrows[NoSuchFileException](segment.asInstanceOf[PersistentSegment].tryReopen)
//            }
//        }
//      }
//    }
//  }
//
//  "deleteSegments" should {
//    "delete multiple segments" in {
//      runThis(100.times, log = true) {
//        CoreTestSweeper {
//          implicit sweeper =>
//
//            implicit val bufferCleaner = ByteBufferSweeper(messageReschedule = 0.seconds).sweep()
//
//            val segment1 = GenSegment(randomizedKeyValues(keyValuesCount))
//            val segment2 = GenSegment(randomizedKeyValues(keyValuesCount))
//            val segment3 = GenSegment(randomizedKeyValues(keyValuesCount))
//
//            val deleted = Segment.deleteSegments(Seq(segment1, segment2, segment3))
//            deleted shouldBe 3
//
//            eventual(10.seconds) {
//              //files should be closed
//              segment1.isOpen shouldBe false
//              segment2.isOpen shouldBe false
//              segment3.isOpen shouldBe false
//
//              segment1.isCached shouldBe false
//              segment2.isCached shouldBe false
//              segment3.isCached shouldBe false
//
//              segment1.existsOnDisk() shouldBe false
//              segment2.existsOnDisk() shouldBe false
//              segment3.existsOnDisk() shouldBe false
//            }
//        }
//      }
//    }
//  }
//
//  "open a closed Segment on read and clear footer" in {
//    runThis(10.times) {
//      CoreTestSweeper {
//        implicit sweeper =>
//          //        implicit val fileSweeper = FileSweeper.Disabled
//          implicit val blockCache: Option[BlockCacheState] = None
//          implicit val fileSweeper = FileSweeper(50, ActorConfig.TimeLoop("", 10.seconds, TestExecutionContext.executionContext)).sweep()
//
//          val keyValues = randomizedKeyValues(keyValuesCount)
//          val segment = GenSegment(keyValues)
//
//          def close(): Unit = {
//            segment.close()
//            if (isPersistent) {
//              //also clear the cache so that if the key-value is a group on open file is still reopened
//              //instead of just reading from in-memory Group key-value.
//              eitherOne(segment.clearCachedKeyValues(), segment.clearAllCaches())
//              segment.isCached shouldBe false
//              segment.isOpen shouldBe false
//            }
//          }
//
//          def open(keyValue: KeyValue): Unit = {
//            segment.get(keyValue.key, ThreadReadState.random).runRandomIO.value.getUnsafe shouldBe keyValue
//            segment.isCached shouldBe true
//            segment.isOpen shouldBe true
//          }
//
//          keyValues foreach {
//            keyValue =>
//              close()
//              open(keyValue)
//          }
//      }
//    }
//  }
//
//  "fail read and write operations on a Segment that does not exists" when {
//    def executeTest(gaped: Boolean): Unit =
//      CoreTestSweeper {
//        implicit sweeper =>
//          import sweeper._
//
//          implicit val bufferCleaner = ByteBufferSweeper(messageReschedule = 0.seconds).sweep()
//          bufferCleaner.actor
//
//          val keyValues = randomizedKeyValues(keyValuesCount)
//
//          val valuesConfig: ValuesBlockConfig = ValuesBlockConfig.random
//          val sortedIndexConfig: SortedIndexBlockConfig = SortedIndexBlockConfig.random
//          val binarySearchIndexConfig: BinarySearchIndexBlockConfig = BinarySearchIndexBlockConfig.random
//          val hashIndexConfig: HashIndexBlockConfig = HashIndexBlockConfig.random
//          val bloomFilterConfig: BloomFilterBlockConfig = BloomFilterBlockConfig.random
//          val segmentConfig: SegmentBlockConfig = SegmentBlockConfig.random(mmap = mmapSegments)
//
//          val segment =
//            GenSegment(
//              keyValues = keyValues,
//              valuesConfig = valuesConfig,
//              sortedIndexConfig = sortedIndexConfig,
//              binarySearchIndexConfig = binarySearchIndexConfig,
//              hashIndexConfig = hashIndexConfig,
//              bloomFilterConfig = bloomFilterConfig,
//              segmentConfig = segmentConfig
//            )
//
//          segment.delete()
//
//          //segment eventually gets deleted
//          eventual(5.seconds) {
//            segment.isOpen shouldBe false
//            segment.isCached shouldBe false
//            segment.existsOnDisk() shouldBe false
//          }
//
//          assertThrows[NoSuchFileException](segment.get(keyValues.head.key, ThreadReadState.random))
//
//          val (head, mid, tail) =
//            if (gaped) {
//              //if gaped, slice and merge
//              val groups = keyValues.groupedSlice(3)
//              groups should have size 3
//              (groups.head, groups.dropHead().head, groups.last)
//            } else {
//              (Iterable.empty[KeyValue], keyValues, Iterable.empty[KeyValue])
//            }
//
//          IO {
//            segment.put(
//              headGap = head,
//              tailGap = tail,
//              newKeyValues = mid.iterator,
//              removeDeletes = false,
//              createdInLevel = 0,
//              valuesConfig = valuesConfig,
//              sortedIndexConfig = sortedIndexConfig,
//              binarySearchIndexConfig = binarySearchIndexConfig,
//              hashIndexConfig = hashIndexConfig,
//              bloomFilterConfig = bloomFilterConfig,
//              segmentConfig = segmentConfig,
//              pathDistributor = createPathDistributor(),
//              segmentRefCacheLife = randomSegmentRefCacheLife(),
//              mmapSegment = mmapSegments
//            )
//          }.left.get.exception shouldBe a[NoSuchFileException]
//
//          IO {
//            segment.refresh(
//              removeDeletes = false,
//              createdInLevel = 0,
//              valuesConfig = valuesConfig,
//              sortedIndexConfig = sortedIndexConfig,
//              binarySearchIndexConfig = binarySearchIndexConfig,
//              hashIndexConfig = hashIndexConfig,
//              bloomFilterConfig = bloomFilterConfig,
//              segmentConfig = segmentConfig,
//              pathDistributor = createPathDistributor()
//            )
//          }.left.get.exception shouldBe a[NoSuchFileException]
//
//          segment.isOpen shouldBe false
//          segment.isCached shouldBe false
//
//          if (isPersistent && gaped) {
//            //if gap wait for gap segments to be created.
//            sleep(2.seconds)
//            //failure should triggered recovery and then cleanup so the directory should be empty.
//            Effect.isEmptyOrNotExists(segment.path.getParent).value shouldBe true
//          }
//      }
//
//    "gaped" in {
//      executeTest(gaped = true)
//    }
//
//    "no gaps" in {
//      executeTest(gaped = false)
//    }
//  }
//
//  "reopen closed channel for read when closed by LimitQueue" in {
//    if (isMemorySpec) {
//      //memory Segments do not value closed via
//    } else {
//      runThis(5.times, log = true) {
//        CoreTestSweeper {
//          implicit sweeper =>
//            implicit val fileSweeper = FileSweeper(1, ActorConfig.TimeLoop("", 2.second, ec)).sweep()
//
//            val keyValues = randomizedKeyValues(keyValuesCount)
//
//            val segmentConfig = SegmentBlockConfig.random(cacheBlocksOnCreate = true, mmap = MMAP.Off(GenForceSave.standard()), cacheOnAccess = true)
//
//            val segment1 =
//              GenSegment(
//                keyValues = keyValues,
//                segmentConfig = segmentConfig
//              )
//
//            segment1.isOpen shouldBe !segmentConfig.cacheBlocksOnCreate
//            segment1.get(keyValues.head.key, ThreadReadState.random).getUnsafe shouldBe keyValues.head
//            segment1.isOpen shouldBe !segmentConfig.cacheBlocksOnCreate
//
//            //create another segment should close segment 1
//            val segment2 = GenSegment(keyValues)
//            segment2.keyValueCount shouldBe keyValues.size
//
//            eventual(5.seconds) {
//              //segment one is closed
//              segment1.isOpen shouldBe false
//            }
//
//            eventual(5.second) {
//              //when it's close clear all the caches so that key-values do not get read from the cache.
//              eitherOne(segment1.clearAllCaches(), segment1.clearCachedKeyValues())
//              //read one key value from Segment1 so that it's reopened and added to the cache. This will also remove Segment 2 from cache
//              segment1.get(keyValues.head.key, ThreadReadState.random).getUnsafe shouldBe keyValues.head
//              segment1.isOpen shouldBe true
//            }
//
//            eventual(5.seconds) {
//              //segment2 is closed
//              segment2.isOpen shouldBe false
//            }
//        }
//      }
//    }
//  }
//
//  "delete" should {
//    "close the channel and delete the file" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//
//          //set this MemorySweeper as root sweeper.
//          CoreTestSweepers.createKeyValueSweeperBlock().value.sweep()
//
//          ByteBufferSweeper(messageReschedule = 0.seconds).sweep()
//
//          val keyValues = randomizedKeyValues(keyValuesCount)
//          val segment = GenSegment(keyValues)
//          assertReads(keyValues, segment) //populate the cache
//
//          segment.cachedKeyValueSize shouldBe keyValues.size
//
//          segment.delete()
//          segment.cachedKeyValueSize shouldBe keyValues.size //cache is not cleared
//
//          eventual(5.seconds) {
//            if (isPersistent) {
//              segment.isOpen shouldBe false
//              segment.isFooterDefined shouldBe false //on delete in-memory footer is cleared
//            }
//            segment.existsOnDisk() shouldBe false
//          }
//      }
//    }
//  }
//
//  "copyTo" should {
//    "copy the segment to a target path without deleting the original" in {
//      if (isPersistent) {
//        CoreTestSweeper {
//          implicit sweeper =>
//
//            val keyValues = randomizedKeyValues(keyValuesCount)
//            val keyValuesReadOnly = keyValues
//
//            val segment = GenSegment(keyValues).asInstanceOf[PersistentSegment]
//            val targetPath = genIntDir.resolve(nextId + s".${Extension.Seg}")
//
//            segment.copyTo(targetPath)
//            segment.existsOnDisk() shouldBe true
//
//            val copiedSegment = segment.reopen(targetPath)
//            copiedSegment.iterator(randomBoolean()).toSlice shouldBe keyValuesReadOnly
//            copiedSegment.path shouldBe targetPath
//
//            //original segment should still exist
//            segment.iterator(randomBoolean()).toList shouldBe keyValuesReadOnly
//        }
//      }
//    }
//  }
//
//  "copyToPersist" should {
//    "copy the segment and persist it to disk" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//          import sweeper._
//          val valuesConfig: ValuesBlockConfig = ValuesBlockConfig.random
//          val sortedIndexConfig: SortedIndexBlockConfig = SortedIndexBlockConfig.random
//          val binarySearchIndexConfig: BinarySearchIndexBlockConfig = BinarySearchIndexBlockConfig.random
//          val hashIndexConfig: HashIndexBlockConfig = HashIndexBlockConfig.random
//          val bloomFilterConfig: BloomFilterBlockConfig = BloomFilterBlockConfig.random
//          val segmentConfig: SegmentBlockConfig = SegmentBlockConfig.random
//
//          val keyValues = randomizedKeyValues(keyValuesCount)
//
//          val segment =
//            GenSegment(
//              keyValues = keyValues,
//              valuesConfig = valuesConfig,
//              sortedIndexConfig = sortedIndexConfig,
//              binarySearchIndexConfig = binarySearchIndexConfig,
//              hashIndexConfig = hashIndexConfig,
//              bloomFilterConfig = bloomFilterConfig,
//              segmentConfig = segmentConfig
//            )
//
//          val pathDistributor = createPathDistributor()
//
//          val segments =
//            Segment.copyToPersist(
//              segment = segment,
//              createdInLevel = 0,
//              pathDistributor = pathDistributor,
//              valuesConfig = valuesConfig,
//              sortedIndexConfig = sortedIndexConfig,
//              binarySearchIndexConfig = binarySearchIndexConfig,
//              hashIndexConfig = hashIndexConfig,
//              bloomFilterConfig = bloomFilterConfig,
//              segmentConfig = segmentConfig.copy(minSize = Segment.segmentSizeForMerge(segment, randomBoolean()) / 10),
//              removeDeletes = false,
//            ).awaitInf.map(_.sweep())
//
//          if (isPersistent)
//            segments.size shouldBe 1
//          else
//            segments.size should be > 1
//
//          segments.foreach(_.existsOnDisk() shouldBe true)
//          segments.flatMap(_.iterator(randomBoolean())) shouldBe keyValues
//      }
//    }
//
//    "copy the segment and persist it to disk when remove deletes is true" in {
//      runThis(10.times) {
//        CoreTestSweeper {
//          implicit sweeper =>
//            import sweeper._
//
//            val keyValues = randomizedKeyValues(keyValuesCount)
//            val segment = GenSegment(keyValues, segmentConfig = SegmentBlockConfig.random(minSegmentSize = Int.MaxValue, maxKeyValuesPerSegment = Int.MaxValue))
//
//            val valuesConfig: ValuesBlockConfig = ValuesBlockConfig.random
//            val sortedIndexConfig: SortedIndexBlockConfig = SortedIndexBlockConfig.random
//            val binarySearchIndexConfig: BinarySearchIndexBlockConfig = BinarySearchIndexBlockConfig.random
//            val hashIndexConfig: HashIndexBlockConfig = HashIndexBlockConfig.random
//            val bloomFilterConfig: BloomFilterBlockConfig = BloomFilterBlockConfig.random
//            val segmentConfig: SegmentBlockConfig = SegmentBlockConfig.random.copy(minSize = Segment.segmentSizeForMerge(segment, randomBoolean()) / 10)
//
//            val pathDistributor = createPathDistributor()
//
//            val segments =
//              Segment.copyToPersist(
//                segment = segment,
//                createdInLevel = 0,
//                pathDistributor = pathDistributor,
//                valuesConfig = valuesConfig,
//                sortedIndexConfig = sortedIndexConfig,
//                binarySearchIndexConfig = binarySearchIndexConfig,
//                hashIndexConfig = hashIndexConfig,
//                bloomFilterConfig = bloomFilterConfig,
//                segmentConfig = segmentConfig,
//                removeDeletes = true
//              ).awaitInf.map(_.sweep())
//
//            segments.foreach(_.existsOnDisk() shouldBe true)
//
//            if (isPersistent)
//              segments.flatMap(_.iterator(randomBoolean())) shouldBe keyValues //persistent Segments are simply copied and are not checked for removed key-values.
//            else
//              segments.flatMap(_.iterator(randomBoolean())) shouldBe keyValues.collect { //memory Segments does a split/merge and apply lastLevel rules.
//                case keyValue: Memory.Put if keyValue.hasTimeLeft() =>
//                  keyValue
//
//                case Memory.Range(fromKey, _, put @ Value.Put(_, deadline, _), _) if deadline.forall(_.hasTimeLeft()) =>
//                  put.toMemory(fromKey)
//              }
//        }
//      }
//    }
//
//    "revert copy if Segment initialisation fails after copy" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//          import sweeper._
//
//          val keyValues = randomizedKeyValues(keyValuesCount)
//          val segment = GenSegment(keyValues)
//
//          val valuesConfig: ValuesBlockConfig = ValuesBlockConfig.random
//          val sortedIndexConfig: SortedIndexBlockConfig = SortedIndexBlockConfig.random
//          val binarySearchIndexConfig: BinarySearchIndexBlockConfig = BinarySearchIndexBlockConfig.random
//          val hashIndexConfig: HashIndexBlockConfig = HashIndexBlockConfig.random
//          val bloomFilterConfig: BloomFilterBlockConfig = BloomFilterBlockConfig.random
//          val segmentConfig: SegmentBlockConfig = SegmentBlockConfig.random.copy(minSize = Segment.segmentSizeForMerge(segment, randomBoolean()) / 10)
//
//          val pathDistributor = createPathDistributor()
//
//          pathDistributor.dirs.foreach(_.path.sweep())
//
//          val segmentNumber = idGenerator.next()
//          val conflictingPath = pathDistributor.next().resolve(IDGenerator.segment(segmentNumber))
//          Effect.createFile(conflictingPath).sweep() //path already taken.
//
//          implicit val segmentIDGenerator: IDGenerator = IDGenerator(segmentNumber - 1)
//
//          assertThrows[FileAlreadyExistsException] {
//            Segment.copyToPersist(
//              segment = segment,
//              createdInLevel = 0,
//              pathDistributor = pathDistributor,
//              valuesConfig = valuesConfig,
//              sortedIndexConfig = sortedIndexConfig,
//              binarySearchIndexConfig = binarySearchIndexConfig,
//              hashIndexConfig = hashIndexConfig,
//              bloomFilterConfig = bloomFilterConfig,
//              segmentConfig = segmentConfig,
//              removeDeletes = true
//            )(keyOrder = keyOrder,
//              timeOrder = timeOrder,
//              functionStore = functionStore,
//              keyValueMemorySweeper = keyValueMemorySweeper,
//              fileSweeper = fileSweeper,
//              bufferCleaner = cleaner,
//              blockCacheSweeper = blockSweeperCache,
//              forceSaveApplier = forceSaveApplier,
//              segmentIO = segmentIO,
//              idGenerator = segmentIDGenerator,
//              ec = TestExecutionContext.executionContext
//            ).awaitInf.map(_.sweep())
//          }
//
//          //windows
//          if (isWindowsAndMMAPSegments())
//            sweeper.receiveAll()
//
//          Effect.size(conflictingPath) shouldBe 0
//          if (isPersistent) segment.existsOnDisk() shouldBe true //original Segment remains untouched
//      }
//    }
//
//    "revert copy of Key-values if creating at least one Segment fails" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//          import sweeper._
//
//          val keyValues = randomizedKeyValues(keyValuesCount)
//
//          val valuesConfig: ValuesBlockConfig = ValuesBlockConfig.random
//          val sortedIndexConfig: SortedIndexBlockConfig = SortedIndexBlockConfig.random
//          val binarySearchIndexConfig: BinarySearchIndexBlockConfig = BinarySearchIndexBlockConfig.random
//          val hashIndexConfig: HashIndexBlockConfig = HashIndexBlockConfig.random
//          val bloomFilterConfig: BloomFilterBlockConfig = BloomFilterBlockConfig.random
//          val segmentConfig: SegmentBlockConfig = SegmentBlockConfig.random
//
//          //used to calculate the size of Segment
//          val temporarySegment =
//            GenSegment(
//              keyValues = keyValues,
//              valuesConfig = valuesConfig,
//              sortedIndexConfig = sortedIndexConfig,
//              binarySearchIndexConfig = binarySearchIndexConfig,
//              hashIndexConfig = hashIndexConfig,
//              bloomFilterConfig = bloomFilterConfig,
//              segmentConfig = segmentConfig
//            )
//
//          val pathDistributor = createPathDistributor()
//
//          pathDistributor.dirs.foreach(_.path.sweep())
//
//          val segmentNumber = idGenerator.next()
//
//          Effect.createFile(pathDistributor.next().resolve(IDGenerator.segment(segmentNumber + 4))).sweep() //path already taken.
//
//          levelStorage.dirs foreach {
//            dir =>
//              Effect.createDirectoriesIfAbsent(dir.path).sweep()
//              IO(Effect.createFile(dir.path.resolve(IDGenerator.segment(segmentNumber + 4))).sweep()) //path already taken.
//          }
//
//          val filesBeforeCopy = pathDistributor.next().files(Extension.Seg)
//          filesBeforeCopy.size shouldBe 1
//
//          assertThrows[FileAlreadyExistsException] {
//            Segment.copyToPersist(
//              keyValues = keyValues,
//              createdInLevel = 0,
//              pathDistributor = pathDistributor,
//              valuesConfig = valuesConfig,
//              sortedIndexConfig = sortedIndexConfig,
//              binarySearchIndexConfig = binarySearchIndexConfig,
//              hashIndexConfig = hashIndexConfig,
//              bloomFilterConfig = bloomFilterConfig,
//              segmentConfig = segmentConfig.copy(minSize = Segment.segmentSizeForMerge(temporarySegment, randomBoolean()) / 20),
//              removeDeletes = false
//            ).awaitInf.map(_.sweep())
//          }
//
//          //process all delete requests
//          if (OperatingSystem.isWindows())
//            eventual(1.minute) {
//              sweeper.receiveAll()
//              pathDistributor.next().files(Extension.Seg) shouldBe filesBeforeCopy
//            }
//          else
//            pathDistributor.next().files(Extension.Seg) shouldBe filesBeforeCopy
//
//      }
//    }
//
//    "transfer blockCache on copy" in {
//      //when a Segment is copied it's BlockCache bytes should still be accessible.
//      if (isPersistent)
//        runThis(50.times, log = true) {
//          CoreTestSweeper {
//            implicit sweeper =>
//              MemorySweeperTestKit.createBlockCacheBlockSweeper().foreach(_.sweep())
//
//              import sweeper._
//
//              val keyValues = randomizedKeyValues(keyValuesCount)
//
//              //disable caching so that blockCache gets used.
//              val valuesConfig: ValuesBlockConfig = ValuesBlockConfig.random(hasCompression = false, cacheOnAccess = false)
//              val sortedIndexConfig: SortedIndexBlockConfig = SortedIndexBlockConfig.random(hasCompression = false, cacheOnAccess = false)
//              val binarySearchIndexConfig: BinarySearchIndexBlockConfig = BinarySearchIndexBlockConfig.random(hasCompression = false, cacheOnAccess = false)
//              val hashIndexConfig: HashIndexBlockConfig = HashIndexBlockConfig.random(hasCompression = false, cacheOnAccess = false)
//              val bloomFilterConfig: BloomFilterBlockConfig = BloomFilterBlockConfig.random(hasCompression = false, cacheOnAccess = false)
//              val segmentConfig: SegmentBlockConfig = SegmentBlockConfig.random(cacheOnAccess = false, cacheBlocksOnCreate = false, hasCompression = false)
//
//              //used to calculate the size of Segment
//              val segment =
//                GenSegment(
//                  keyValues = keyValues,
//                  valuesConfig = valuesConfig,
//                  sortedIndexConfig = sortedIndexConfig,
//                  binarySearchIndexConfig = binarySearchIndexConfig,
//                  hashIndexConfig = hashIndexConfig,
//                  bloomFilterConfig = bloomFilterConfig,
//                  segmentConfig = segmentConfig
//                ).asInstanceOf[PersistentSegment]
//
//              segment.clearAllCaches() //fresh start without any existing cached bytes so that blockCache gets used.
//
//              //blockCache is initialised
//              def getInnerSegments(segment: PersistentSegment): (Option[SegmentRef], List[SegmentRef]) =
//                segment match {
//                  case many: PersistentSegmentMany =>
//                    val listSegment = many.listSegmentCache.get()
//                    val tailSegments = getSegmentsCache(many).asScala.values
//                    (listSegment, tailSegments.toList)
//
//                  case one: PersistentSegmentOne =>
//                    (None, List(one.ref))
//                }
//
//              val (preCachingListSegment, preCachingTailSegments) = getInnerSegments(segment)
//              preCachingListSegment shouldBe empty
//              segment match {
//                case _: PersistentSegmentMany =>
//                  preCachingTailSegments shouldBe empty
//
//                case _: PersistentSegmentOne =>
//                  preCachingTailSegments should not be empty
//              }
//
//              assertGetSequential(keyValues, segment)
//
//              val (postCachingListSegment, postCachingTailSegments) = getInnerSegments(segment)
//              segment match {
//                case many: PersistentSegmentMany =>
//                  postCachingListSegment shouldBe defined
//
//                case one: PersistentSegmentOne =>
//                  postCachingListSegment shouldBe empty
//              }
//              postCachingTailSegments should not be empty
//
//              val pathDistributor = createPathDistributor()
//              pathDistributor.dirs.foreach(_.path.sweep())
//
//              //copy the segment
//              val copiedSegments =
//                Segment.copyToPersist(
//                  segment = segment,
//                  createdInLevel = 0,
//                  pathDistributor = pathDistributor,
//                  valuesConfig = valuesConfig,
//                  sortedIndexConfig = sortedIndexConfig,
//                  binarySearchIndexConfig = binarySearchIndexConfig,
//                  hashIndexConfig = hashIndexConfig,
//                  bloomFilterConfig = bloomFilterConfig,
//                  segmentConfig = segmentConfig,
//                  removeDeletes = false
//                ).awaitInf.map(_.sweep())
//
//              copiedSegments should have size 1
//              val copiedSegment = copiedSegments.head
//              println("SegmentType - " + copiedSegment.getClass.getSimpleName)
//              copiedSegment.isOpen shouldBe false //it's not opened
//
//              def assertBlockCachesAreCopied() = {
//                val (postCopyListSegment, postCopyTailSegments) = getInnerSegments(copiedSegment)
//                segment match {
//                  case _: PersistentSegmentMany =>
//                    val postCopyListSegmentBlockCache = postCopyListSegment.value.blockCache()
//                    val postCacheListSegmentBlockCache = postCachingListSegment.value.blockCache()
//                    postCopyListSegmentBlockCache shouldBe postCacheListSegmentBlockCache
//                    postCopyListSegmentBlockCache.hashCode() shouldBe postCacheListSegmentBlockCache.hashCode()
//
//                  case _: PersistentSegmentOne =>
//                    postCachingListSegment shouldBe empty
//
//                }
//                val postCopyTailSegmentsBlockCache = postCopyTailSegments.map(_.blockCache())
//                val postCachingTailSegmentsBlockCache = postCachingTailSegments.map(_.blockCache())
//                postCopyTailSegmentsBlockCache should have size postCachingTailSegments.size
//                postCopyTailSegmentsBlockCache should contain allElementsOf postCachingTailSegmentsBlockCache
//                postCopyTailSegmentsBlockCache.map(_.hashCode()).sorted shouldBe postCachingTailSegmentsBlockCache.map(_.hashCode()).sorted
//              }
//
//              /**
//               * Assert on copiedSegment
//               */
//              assertBlockCachesAreCopied()
//              assertGetSequential(keyValues, copiedSegment) //do initial read from copied segment
//              assertBlockCachesAreCopied()
//
//              /**
//               * Second read on copiedSegment - do clear on copied segment and read.
//               */
//              copiedSegment.clearAllCaches()
//              Seq(
//                () => assertGetSequential(keyValues, copiedSegment),
//                () => assertGetSequential(keyValues, segment)
//              ).runThisRandomlyInParallel()
//          }
//        }
//    }
//
//    //    "debugger" in {
//    //      //when a Segment is copied it's BlockCache bytes should still be accessible.
//    //      if (persistent)
//    //        runThis(100.times, log = true) {
//    //          CoreTestSweeper {
//    //            implicit sweeper =>
//    //              CoreTestSweeper.createBlockCacheBlockSweeper().foreach(_.sweep())
//    //
//    //              import sweeper._
//    //
//    //              val keyValues = randomPutKeyValues(100, startId = Some(0))
//    //
//    //              //disable caching so that blockCache gets used.
//    //              val valuesConfig: ValuesBlockConfig = ValuesBlockConfig.random(hasCompression = false, cacheOnAccess = false)
//    //              val sortedIndexConfig: SegmentIndexBlockConfig = SegmentIndexBlockConfig.random(hasCompression = false, cacheOnAccess = false)
//    //              val binarySearchIndexConfig: BinarySearchIndexConfig = BinarySearchIndexConfig.random(hasCompression = false, cacheOnAccess = false)
//    //              val hashIndexConfig: HashIndexConfig = HashIndexConfig.random(hasCompression = false, cacheOnAccess = false)
//    //              val bloomFilterConfig: BloomFilterConfig = BloomFilterConfig.random(hasCompression = false, cacheOnAccess = false)
//    //              val segmentConfig: SegmentBlockConfig = SegmentBlockConfig.random(cacheOnAccess = false, cacheBlocksOnCreate = true, hasCompression = false).copy(minSize = Int.MaxValue, maxCount = keyValues.size / 10)
//    //
//    //              val path = testSegmentFile
//    //              implicit val pathDistributor: pathDistributor = pathDistributor(Seq(Dir(path.getParent, 1)), () => Seq.empty)
//    //
//    //              //used to calculate the size of Segment
//    //              val segment =
//    //                GenSegment.many(
//    //                  keyValues = keyValues,
//    //                  valuesConfig = valuesConfig,
//    //                  sortedIndexConfig = sortedIndexConfig,
//    //                  binarySearchIndexConfig = binarySearchIndexConfig,
//    //                  hashIndexConfig = hashIndexConfig,
//    //                  bloomFilterConfig = bloomFilterConfig,
//    //                  segmentConfig = segmentConfig
//    //                )
//    //
//    //              segment should have size 1
//    //              val many = segment.head.asInstanceOf[PersistentSegmentMany]
//    //              many.blockCache.value.map.asScala shouldBe empty
//    //              many.listSegmentCache.value(()).keyValueCount > 15
//    //
//    //              val manyListSegmentId = many.listSegmentCache.value(()).blockCacheId
//    //
//    //              val threadState = ThreadReadState.random
//    //
//    //              //              Random.shuffle(keyValues.toList) foreach {
//    //              //                keyValue =>
//    //              //                  many.get(keyValue.key, threadState).getUnsafe shouldBe keyValue
//    //              //              }
//    //
//    //              //              keyValues.take(11).takeRight(1) foreach {
//    //              //                keyValue =>
//    //              //                  println(s"get: ${keyValue.key.readInt()}")
//    //              //                  many.get(keyValue.key, threadState).getUnsafe shouldBe keyValue
//    //              //              }
//    //
//    //              //              val keyValuesToWrite = keyValues.drop(randomIntMax(keyValues.size / 2)).take(randomIntMax(keyValues.size))
//    //              val keyValuesToWrite = keyValues.take(5)
//    //
//    //              val newSegmentsResult =
//    //                many.put(
//    //                  headGap = Iterable.empty,
//    //                  tailGap = Iterable.empty,
//    //                  mergeableCount = keyValuesToWrite.size,
//    //                  mergeable = keyValuesToWrite.iterator,
//    //                  removeDeletes = false,
//    //                  createdInLevel = 1,
//    //                  segmentParallelism = randomParallelMerge().segment,
//    //                  valuesConfig = valuesConfig,
//    //                  sortedIndexConfig = sortedIndexConfig,
//    //                  binarySearchIndexConfig = binarySearchIndexConfig,
//    //                  hashIndexConfig = hashIndexConfig,
//    //                  bloomFilterConfig = bloomFilterConfig,
//    //                  segmentConfig = segmentConfig,
//    //                  pathDistributor = pathDistributor
//    //                )
//    //
//    //              newSegmentsResult.replaced shouldBe true
//    //              val newSegments = newSegmentsResult.result
//    //
//    //              newSegments should have size 1
//    //              val newMany = newSegments.head.asInstanceOf[PersistentSegmentMany]
//    //
//    //              println(many.getAllSegmentRefs().map(_.path.getFileName).toList)
//    //              println(newMany.getAllSegmentRefs().map(_.path.getFileName).toList)
//    //
//    //              //              many.getAllSegmentRefs().map(_.path.getFileName).toList shouldBe newMany.getAllSegmentRefs().map(_.path.getFileName).toList
//    //
//    //              println(many.getAllSegmentRefs().map(_.blockCacheId).toList)
//    //              println(newMany.getAllSegmentRefs().map(_.blockCacheId).toList)
//    //
//    //              //              newMany.listSegmentCache.value(()).blockCacheId should be > manyListSegmentId
//    //              //
//    //              //              //              Random.shuffle(keyValues.toList) foreach {
//    //              //              keyValues.take(11).takeRight(1) foreach {
//    //              //                keyValue =>
//    //              //                  println(s"get: ${keyValue.key.readInt()}")
//    //              //                  newMany.get(keyValue.key, threadState).getUnsafe shouldBe keyValue
//    //              //              }
//    //
//    //              many.blockCache.get.clear()
//    //              many.keyValueCount
//    //              try
//    //                newMany.getAllSegmentRefs().toList.drop(1).head.keyValueCount
//    //              catch {
//    //                case throwable: Throwable =>
//    //                  throw throwable
//    //              }
//    //
//    //            //              newMany.get(keyValues.last.key, threadState).getUnsafe shouldBe keyValues.last
//    //          }
//    //        }
//    //    }
//  }
//
//  "copyToMemory" should {
//    "copy persistent segment and store it in Memory" in {
//      runThis(100.times) {
//        CoreTestSweeper {
//          implicit sweeper =>
//            import sweeper._
//            val keyValues = randomizedKeyValues(keyValuesCount)
//            val segment = GenSegment(keyValues, segmentConfig = SegmentBlockConfig.random(minSegmentSize = Int.MaxValue, maxKeyValuesPerSegment = Int.MaxValue))
//
//            val memorySize = keyValues.foldLeft(0)(_ + MergeStats.Memory.calculateSize(_))
//
//            val pathDistributor = createPathDistributor()
//            pathDistributor.dirs.foreach(_.path.sweep())
//
//            val segments =
//              Segment.copyToMemory(
//                segment = segment,
//                createdInLevel = 0,
//                pathDistributor = pathDistributor,
//                removeDeletes = false,
//                minSegmentSize =
//                //there are too many conditions that will not split the segments so set the size of each segment to be too small
//                //for the split to occur.
//                  memorySize / 10,
//                maxKeyValueCountPerSegment = randomIntMax(keyValues.size),
//                initialiseIteratorsInOneSeek = randomBoolean()
//              ).mapToSlice(_.sweep())
//
//            segments.size should be >= 2 //ensures that splits occurs. Memory Segments do not value written to disk without splitting.
//
//            segments.foreach(_.existsOnDisk() shouldBe false)
//            segments.flatMap(_.iterator(randomBoolean())) shouldBe keyValues
//        }
//      }
//    }
//
//    "copy the segment and persist it to disk when removeDeletes is true" in {
//      runThis(10.times) {
//        CoreTestSweeper {
//          implicit sweeper =>
//            import sweeper._
//
//            val keyValues = randomizedKeyValues(keyValuesCount)
//            val segment = GenSegment(keyValues)
//
//            val pathDistributor = createPathDistributor()
//            pathDistributor.dirs.foreach(_.path.sweep())
//
//            val memorySize = keyValues.foldLeft(0)(_ + MergeStats.Memory.calculateSize(_))
//
//            val segments =
//              Segment.copyToMemory(
//                segment = segment,
//                createdInLevel = 0,
//                pathDistributor = pathDistributor,
//                removeDeletes = true,
//                minSegmentSize = memorySize / 1000,
//                maxKeyValueCountPerSegment = randomIntMax(keyValues.size),
//                initialiseIteratorsInOneSeek = randomBoolean()
//              ).mapToSlice(_.sweep())
//
//            segments.foreach(_.existsOnDisk() shouldBe false)
//
//            segments.size should be >= 2 //ensures that splits occurs. Memory Segments do not value written to disk without splitting.
//
//            //some key-values could value expired while unexpired key-values are being collected. So try again!
//            segments.flatMap(_.iterator(randomBoolean())) shouldBe keyValues.collect {
//              case keyValue: Memory.Put if keyValue.hasTimeLeft() =>
//                keyValue
//              case Memory.Range(fromKey, _, put @ Value.Put(_, deadline, _), _) if deadline.forall(_.hasTimeLeft()) =>
//                put.toMemory(fromKey)
//            }
//        }
//      }
//    }
//  }
//
//  "put" should {
//    "return None for empty byte arrays for values" in {
//      runThis(10.times) {
//        CoreTestSweeper {
//          implicit sweeper =>
//
//            val keyValuesWithEmptyValues = ListBuffer.empty[Memory]
//
//            (1 to 100).foldLeft(0) {
//              case (i, _) =>
//                var key = i
//
//                def nextKey = {
//                  key += 1
//                  key
//                }
//
//                keyValuesWithEmptyValues +=
//                  eitherOne(
//                    left = randomFixedKeyValue(nextKey, Slice.empty),
//                    mid = randomRangeKeyValue(nextKey, nextKey, randomFromValueOption(Slice.emptyBytes), randomRangeValue(Slice.emptyBytes)),
//                    right = randomFixedKeyValue(nextKey, Slice.empty)
//                  )
//                key
//            }
//
//            val segment = GenSegment(keyValuesWithEmptyValues.toSlice, segmentConfig = SegmentBlockConfig.random(minSegmentSize = Int.MaxValue, maxKeyValuesPerSegment = Int.MaxValue))
//
//            def valuesValueShouldBeNone(value: Value): Unit =
//              value match {
//                case Value.Update(value, deadline, time) =>
//                  value shouldBe Slice.Null
//                case Value.Put(value, deadline, time) =>
//                  value shouldBe Slice.Null
//                case Value.PendingApply(applies) =>
//                  applies foreach valuesValueShouldBeNone
//                case Value.Function(function, time) =>
//                //nothing to assert
//                case Value.Remove(deadline, time) =>
//                //nothing to assert
//              }
//
//            segment.iterator(randomBoolean()) foreach {
//              case keyValue: KeyValue.Put =>
//                keyValue.getOrFetchValue() shouldBe Slice.Null
//
//              case keyValue: KeyValue.Update =>
//                keyValue.getOrFetchValue() shouldBe Slice.Null
//
//              case keyValue: KeyValue.Range =>
//                val (fromValue, rangeValue) = keyValue.fetchFromAndRangeValueUnsafe()
//                Seq(fromValue.toOptionS, Some(rangeValue)).flatten foreach valuesValueShouldBeNone
//
//              case apply: KeyValue.PendingApply =>
//                apply.getOrFetchApplies() foreach valuesValueShouldBeNone
//
//              case _: KeyValue.Function =>
//              //nothing to assert
//              case _: KeyValue.Remove =>
//              //nothing to assert
//            }
//        }
//      }
//    }
//
//    "reopen closed channel" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//          import sweeper._
//
//          val keyValues1 = randomizedKeyValues(keyValuesCount)
//
//          val segment = GenSegment(keyValues1)
//          segment.close()
//          if (isPersistent) segment.isOpen shouldBe false
//
//          val keyValues2 = randomizedKeyValues(keyValuesCount)
//
//          segment.put(
//            headGap = Iterable.empty,
//            tailGap = Iterable.empty,
//            newKeyValues = keyValues2.iterator,
//            removeDeletes = false,
//            createdInLevel = 0,
//            valuesConfig = ValuesBlockConfig.random,
//            sortedIndexConfig = SortedIndexBlockConfig.random,
//            binarySearchIndexConfig = BinarySearchIndexBlockConfig.random,
//            hashIndexConfig = HashIndexBlockConfig.random,
//            bloomFilterConfig = BloomFilterBlockConfig.random,
//            segmentConfig = SegmentBlockConfig.random.copy(minSize = 1.mb),
//            pathDistributor = createPathDistributor(),
//            segmentRefCacheLife = randomSegmentRefCacheLife(),
//            mmapSegment = mmapSegments
//          ).output.mapToSlice(_.sweep())
//
//          if (isPersistent) segment.isOpen shouldBe true
//      }
//    }
//
//    "return a new segment with merged key values" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//          import sweeper._
//
//          val keyValues = Slice(Memory.put(1, 1))
//
//          val valuesConfig: ValuesBlockConfig = ValuesBlockConfig.random
//          val sortedIndexConfig: SortedIndexBlockConfig = SortedIndexBlockConfig.random
//          val binarySearchIndexConfig: BinarySearchIndexBlockConfig = BinarySearchIndexBlockConfig.random
//          val hashIndexConfig: HashIndexBlockConfig = HashIndexBlockConfig.random
//          val bloomFilterConfig: BloomFilterBlockConfig = BloomFilterBlockConfig.random
//          val segmentConfig: SegmentBlockConfig = SegmentBlockConfig.random
//
//          val segment =
//            GenSegment(
//              keyValues = keyValues,
//              valuesConfig = valuesConfig,
//              sortedIndexConfig = sortedIndexConfig,
//              binarySearchIndexConfig = binarySearchIndexConfig,
//              hashIndexConfig = hashIndexConfig,
//              bloomFilterConfig = bloomFilterConfig,
//              segmentConfig = segmentConfig
//            )
//
//          val newKeyValues = Slice(Memory.put(2, 2))
//
//          val newSegments =
//            segment.put(
//              headGap = Iterable.empty[KeyValue],
//              tailGap = Iterable.empty[KeyValue],
//              newKeyValues = newKeyValues.iterator,
//              removeDeletes = false,
//              createdInLevel = 0,
//              valuesConfig = valuesConfig,
//              sortedIndexConfig = sortedIndexConfig,
//              binarySearchIndexConfig = binarySearchIndexConfig,
//              hashIndexConfig = hashIndexConfig,
//              bloomFilterConfig = bloomFilterConfig,
//              segmentConfig = segmentConfig.copy(minSize = 4.mb),
//              pathDistributor = createPathDistributor(),
//              segmentRefCacheLife = randomSegmentRefCacheLife(),
//              mmapSegment = mmapSegments
//            ).output.mapToSlice(_.sweep())
//
//          newSegments should have size 1
//
//          val allReadKeyValues = newSegments.flatMap(_.iterator(randomBoolean()))
//
//          allReadKeyValues should have size 2
//
//          val builder = MergeStats.random()
//
//          KeyValueMerger.merge(
//            headGap = Iterable.empty[KeyValue],
//            tailGap = Iterable.empty[KeyValue],
//            newKeyValues = newKeyValues.iterator,
//            oldKeyValues = keyValues.iterator,
//            stats = builder,
//            isLastLevel = false,
//            initialiseIteratorsInOneSeek = randomBoolean()
//          )
//
//          val expectedKeyValues = builder.result
//
//          expectedKeyValues.result should have size 2
//
//          allReadKeyValues shouldBe expectedKeyValues.result
//      }
//    }
//
//    "return multiple new segments with merged key values" when {
//      def doTest(gaped: Boolean) =
//        runThis(2.times, log = true) {
//          CoreTestSweeper {
//            implicit sweeper =>
//              import sweeper._
//
//              val oldKeyValues = randomizedKeyValues(30, startId = Some(0))
//
//              val (headKeyValues, midKeyValues, tailKeyValues, oldSegment, expectedKeyValues) =
//                if (gaped) {
//                  val groups = oldKeyValues.groupedSlice(3)
//                  groups should have size 3
//
//                  val head = groups.head
//                  val mid = groups.dropHead().head
//                  val tail = groups.last
//
//                  val builder = MergeStats.random()
//
//                  KeyValueMerger.merge(
//                    newKeyValues = mid,
//                    oldKeyValues = mid,
//                    stats = builder,
//                    isLastLevel = false,
//                    initialiseIteratorsInOneSeek = randomBoolean()
//                  )
//
//                  val expectedKeyValues =
//                    head ++ builder.result ++ tail
//
//                  (head, mid, tail, GenSegment(mid), expectedKeyValues)
//                } else {
//                  val newKeyValues = oldKeyValues
//
//                  val builder = MergeStats.random()
//
//                  KeyValueMerger.merge(
//                    newKeyValues = newKeyValues,
//                    oldKeyValues = oldKeyValues,
//                    stats = builder,
//                    isLastLevel = false,
//                    initialiseIteratorsInOneSeek = randomBoolean()
//                  )
//
//                  (Iterable.empty[KeyValue], newKeyValues, Iterable.empty[KeyValue], GenSegment(oldKeyValues), builder.result)
//                }
//
//              val valuesConfig: ValuesBlockConfig = ValuesBlockConfig.random
//              val sortedIndexConfig: SortedIndexBlockConfig = SortedIndexBlockConfig.random
//              val binarySearchIndexConfig: BinarySearchIndexBlockConfig = BinarySearchIndexBlockConfig.random
//              val hashIndexConfig: HashIndexBlockConfig = HashIndexBlockConfig.random
//              val bloomFilterConfig: BloomFilterBlockConfig = BloomFilterBlockConfig.random
//              val segmentConfig: SegmentBlockConfig = SegmentBlockConfig.random
//
//              val newSegments =
//                oldSegment.put(
//                  headGap = headKeyValues,
//                  tailGap = tailKeyValues,
//                  newKeyValues = midKeyValues.iterator,
//                  removeDeletes = false,
//                  createdInLevel = 0,
//                  valuesConfig = valuesConfig,
//                  sortedIndexConfig = sortedIndexConfig,
//                  binarySearchIndexConfig = binarySearchIndexConfig,
//                  hashIndexConfig = hashIndexConfig,
//                  bloomFilterConfig = bloomFilterConfig,
//                  segmentConfig = segmentConfig.copy(minSize = oldSegment.segmentSize / 10),
//                  pathDistributor = createPathDistributor(),
//                  segmentRefCacheLife = randomSegmentRefCacheLife(),
//                  mmapSegment = mmapSegments
//                ).output.mapToSlice(_.sweep())
//
//              newSegments.size should be > 1
//
//              newSegments.flatMap(_.iterator(randomBoolean())) shouldBe expectedKeyValues
//          }
//        }
//
//      "gaped" in {
//        doTest(gaped = true)
//      }
//
//      "no gaps" in {
//        doTest(gaped = false)
//      }
//    }
//
//    "fail put and delete partially written batch Segments if there was a failure in creating one of them" in {
//      if (isMemorySpec)
//        cancel("Test not need for Memory Segment")
//      else
//        runThis(10.times, log = true) {
//          CoreTestSweeper {
//            implicit sweeper =>
//              import sweeper._
//
//              val valuesConfig: ValuesBlockConfig = ValuesBlockConfig.random
//              val sortedIndexConfig: SortedIndexBlockConfig = SortedIndexBlockConfig.random
//              val binarySearchIndexConfig: BinarySearchIndexBlockConfig = BinarySearchIndexBlockConfig.random
//              val hashIndexConfig: HashIndexBlockConfig = HashIndexBlockConfig.random
//              val bloomFilterConfig: BloomFilterBlockConfig = BloomFilterBlockConfig.random
//              val segmentConfig: SegmentBlockConfig = SegmentBlockConfig.random
//
//              val keyValues = randomizedKeyValues(keyValuesCount)
//              val segment = GenSegment(keyValues)
//              val newKeyValues = randomizedKeyValues(keyValuesCount)
//
//              val tenthSegmentId = {
//                val segmentNumber = (segment.path.fileId._1 + 10).toSegmentFileId
//                segment.path.getParent.resolve(segmentNumber)
//              }
//
//              //create a segment with the next id in sequence which should fail put with FileAlreadyExistsException
//              val segmentToFailPut = GenSegment(path = tenthSegmentId)
//
//              assertThrows[FileAlreadyExistsException] {
//                segment.put(
//                  //randomly create gaps
//                  headGap = eitherOne(Iterable.empty[KeyValue], randomizedKeyValues(keyValuesCount)),
//                  tailGap = eitherOne(Iterable.empty[KeyValue], randomizedKeyValues(keyValuesCount)),
//                  newKeyValues = newKeyValues.iterator,
//                  removeDeletes = false,
//                  createdInLevel = 0,
//                  valuesConfig = valuesConfig,
//                  sortedIndexConfig = sortedIndexConfig,
//                  binarySearchIndexConfig = binarySearchIndexConfig,
//                  hashIndexConfig = hashIndexConfig,
//                  bloomFilterConfig = bloomFilterConfig,
//                  segmentConfig = segmentConfig.copy(minSize = 50.bytes),
//                  pathDistributor = pathDistributor(Seq(Dir(segment.path.getParent, 1)), () => Seq.empty),
//                  segmentRefCacheLife = randomSegmentRefCacheLife(),
//                  mmapSegment = mmapSegments
//                ).output.mapToSlice(_.sweep())
//              }
//
//              //the folder should contain only the original segment and the segmentToFailPut
//              def assertFinalFiles() = segment.path.getParent.files(Extension.Seg) should contain only(segment.path, segmentToFailPut.path)
//
//              //if windows execute all stashed actor messages
//              if (OperatingSystem.isWindows())
//                eventual(10.seconds) {
//                  sweeper.receiveAll()
//                  assertFinalFiles()
//                }
//              else
//                eventual(10.seconds) {
//                  assertFinalFiles()
//                }
//          }
//        }
//    }
//
//    "return new segment with deleted KeyValues if all keys were deleted and removeDeletes is false" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//          import sweeper._
//
//          implicit def testTimer: TestTimer = TestTimer.Empty
//
//          val keyValues = Slice(
//            Memory.put(1),
//            Memory.put(2),
//            Memory.put(3),
//            Memory.put(4),
//            Memory.Range(5, 10, FromValue.Null, Value.Update(Slice.Null, None, testTimer.next))
//          )
//          val segment = GenSegment(keyValues)
//          assertGet(keyValues, segment)
//
//          val deleteKeyValues = Slice(Memory.remove(1), Memory.remove(2), Memory.remove(3), Memory.remove(4), Memory.Range(5, 10, FromValue.Null, Value.remove(None)))
//
//          val valuesConfig: ValuesBlockConfig = ValuesBlockConfig.random
//          val sortedIndexConfig: SortedIndexBlockConfig = SortedIndexBlockConfig.random
//          val binarySearchIndexConfig: BinarySearchIndexBlockConfig = BinarySearchIndexBlockConfig.random
//          val hashIndexConfig: HashIndexBlockConfig = HashIndexBlockConfig.random
//          val bloomFilterConfig: BloomFilterBlockConfig = BloomFilterBlockConfig.random
//          val segmentConfig: SegmentBlockConfig = SegmentBlockConfig.random
//
//          val deletedSegment =
//            segment.put(
//              headGap = Iterable.empty[KeyValue],
//              tailGap = Iterable.empty[KeyValue],
//              newKeyValues = deleteKeyValues.iterator,
//              removeDeletes = false,
//              createdInLevel = 0,
//              valuesConfig = valuesConfig,
//              sortedIndexConfig = sortedIndexConfig,
//              binarySearchIndexConfig = binarySearchIndexConfig,
//              hashIndexConfig = hashIndexConfig,
//              bloomFilterConfig = bloomFilterConfig,
//              segmentConfig = segmentConfig.copy(minSize = 4.mb),
//              pathDistributor = createPathDistributor(),
//              segmentRefCacheLife = randomSegmentRefCacheLife(),
//              mmapSegment = mmapSegments
//            ).output.mapToSlice(_.sweep())
//
//          deletedSegment should have size 1
//          val newDeletedSegment = deletedSegment.head
//          newDeletedSegment.iterator(randomBoolean()).toList shouldBe deleteKeyValues
//
//          assertGet(keyValues, segment)
//          if (isPersistent) assertGet(keyValues, segment.asInstanceOf[PersistentSegment].reopen)
//      }
//    }
//
//    "return new segment with updated KeyValues if all keys values were updated to None" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//          import sweeper._
//
//          implicit val testTimer: TestTimer = TestTimer.Incremental()
//
//          val keyValues = randomizedKeyValues(count = keyValuesCount)
//          val segment = GenSegment(keyValues)
//
//          val updatedKeyValues = Slice.of[Memory](keyValues.size)
//          keyValues.foreach(keyValue => updatedKeyValues add Memory.put(keyValue.key, Slice.Null))
//
//          val valuesConfig: ValuesBlockConfig = ValuesBlockConfig.random
//          val sortedIndexConfig: SortedIndexBlockConfig = SortedIndexBlockConfig.random
//          val binarySearchIndexConfig: BinarySearchIndexBlockConfig = BinarySearchIndexBlockConfig.random
//          val hashIndexConfig: HashIndexBlockConfig = HashIndexBlockConfig.random
//          val bloomFilterConfig: BloomFilterBlockConfig = BloomFilterBlockConfig.random
//          val segmentConfig: SegmentBlockConfig = SegmentBlockConfig.random
//
//          val updatedSegments =
//            segment.put(
//              headGap = Iterable.empty[KeyValue],
//              tailGap = Iterable.empty[KeyValue],
//              newKeyValues = updatedKeyValues.iterator,
//              removeDeletes = true,
//              createdInLevel = 0,
//              valuesConfig = valuesConfig,
//              sortedIndexConfig = sortedIndexConfig,
//              binarySearchIndexConfig = binarySearchIndexConfig,
//              hashIndexConfig = hashIndexConfig,
//              bloomFilterConfig = bloomFilterConfig,
//              segmentConfig = segmentConfig.copy(minSize = 4.mb),
//              pathDistributor = createPathDistributor(),
//              segmentRefCacheLife = randomSegmentRefCacheLife(),
//              mmapSegment = mmapSegments
//            ).output.mapToSlice(_.sweep())
//
//          updatedSegments.flatMap(_.iterator(randomBoolean())).toList shouldBe updatedKeyValues
//
//        //          assertGet(updatedKeyValues, updatedSegments)
//      }
//    }
//
//    "merge existing segment file with new KeyValues returning new segment file with updated KeyValues" in {
//      runThis(10.times) {
//        CoreTestSweeper {
//          implicit sweeper =>
//            import sweeper._
//
//            implicit val testTimer: TestTimer = TestTimer.Incremental()
//            //ranges value split to make sure there are no ranges.
//            val keyValues1 = randomizedKeyValues(count = keyValuesCount, addRanges = false)
//            val segment1 = GenSegment(keyValues1)
//
//            val keyValues2Unclosed = Slice.of[Memory](keyValues1.size * 100)
//            keyValues1 foreach {
//              keyValue =>
//                keyValues2Unclosed add randomPutKeyValue(keyValue.key)
//            }
//
//            val keyValues2Closed = keyValues2Unclosed.close()
//
//            val segmentConfig2 = SegmentBlockConfig.random.copy(Int.MaxValue, if (isMemorySpec) keyValues2Closed.size else randomIntMax(keyValues2Closed.size))
//
//            val segment2 = GenSegment(keyValues2Closed, segmentConfig = segmentConfig2)
//
//            val valuesConfig: ValuesBlockConfig = ValuesBlockConfig.random
//            val sortedIndexConfig: SortedIndexBlockConfig = SortedIndexBlockConfig.random
//            val binarySearchIndexConfig: BinarySearchIndexBlockConfig = BinarySearchIndexBlockConfig.random
//            val hashIndexConfig: HashIndexBlockConfig = HashIndexBlockConfig.random
//            val bloomFilterConfig: BloomFilterBlockConfig = BloomFilterBlockConfig.random
//            val segmentConfig: SegmentBlockConfig = SegmentBlockConfig.random
//
//            val mergedSegments =
//              segment1.put(
//                headGap = Iterable.empty[KeyValue],
//                tailGap = Iterable.empty[KeyValue],
//                newKeyValues = segment2.iterator(randomBoolean()),
//                removeDeletes = false,
//                createdInLevel = 0,
//                valuesConfig = valuesConfig,
//                sortedIndexConfig = sortedIndexConfig,
//                binarySearchIndexConfig = binarySearchIndexConfig,
//                hashIndexConfig = hashIndexConfig,
//                bloomFilterConfig = bloomFilterConfig,
//                segmentConfig = segmentConfig.copy(minSize = 10.mb),
//                pathDistributor = createPathDistributor(),
//                segmentRefCacheLife = randomSegmentRefCacheLife(),
//                mmapSegment = mmapSegments
//              ).output.mapToSlice(_.sweep())
//
//            //            mergedSegments.size shouldBe 1
//
//            //test merged segment should contain all
//            val readState = ThreadReadState.random
//            //            keyValues2Closed foreach {
//            //              keyValue =>
//            //                mergedSegment.get(keyValue.key, readState).getUnsafe shouldBe keyValue
//            //            }
//
//            mergedSegments.flatMap(_.iterator(randomBoolean())) shouldBe keyValues2Closed
//        }
//      }
//    }
//
//    "return no new segments if all the KeyValues in the Segment were deleted and if remove deletes is true" in {
//      runThis(50.times, log = true) {
//        CoreTestSweeper {
//          implicit sweeper =>
//            import sweeper._
//
//            val keyValues =
//              Slice(
//                randomFixedKeyValue(1),
//                randomFixedKeyValue(2),
//                randomFixedKeyValue(3),
//                randomFixedKeyValue(4),
//                randomRangeKeyValue(5, 10, randomRangeValue(), randomRangeValue())
//              )
//
//            val segment = GenSegment(keyValues)
//
//            val deleteKeyValues = Slice.of[Memory](keyValues.size)
//            (1 to 4).foreach(key => deleteKeyValues add Memory.remove(key))
//            deleteKeyValues add Memory.Range(5, 10, FromValue.Null, Value.remove(None))
//
//            segment.put(
//              headGap = eitherOne(Iterable.empty[KeyValue], Slice(Memory.remove(0))),
//              tailGap = eitherOne(Iterable.empty[KeyValue], Slice(Memory.remove(10), Memory.Range(11, 20, FromValue.Null, Value.remove(None)))),
//              newKeyValues = deleteKeyValues.iterator,
//              removeDeletes = true,
//              createdInLevel = 0,
//              valuesConfig = ValuesBlockConfig.random,
//              sortedIndexConfig = SortedIndexBlockConfig.random,
//              binarySearchIndexConfig = BinarySearchIndexBlockConfig.random,
//              hashIndexConfig = HashIndexBlockConfig.random,
//              bloomFilterConfig = BloomFilterBlockConfig.random,
//              segmentConfig = SegmentBlockConfig.random.copy(minSize = 4.mb),
//              pathDistributor = createPathDistributor(),
//              segmentRefCacheLife = randomSegmentRefCacheLife(),
//              mmapSegment = mmapSegments
//            ).output.isEmpty shouldBe true
//        }
//      }
//    }
//
//    "slice Put range into slice with fromValue set to Remove" in {
//      runThis(10.times) {
//        CoreTestSweeper {
//          implicit sweeper =>
//            import sweeper._
//
//            implicit val testTimer: TestTimer = TestTimer.Empty
//
//            val keyValues = Slice(Memory.Range(1, 10, FromValue.Null, Value.update(10)))
//            val segment = GenSegment(keyValues)
//
//            val deleteKeyValues = Slice.of[Memory](10)
//            (1 to 10).foreach(key => deleteKeyValues add Memory.remove(key))
//
//            val removedRanges =
//              segment.put(
//                headGap = Iterable.empty[KeyValue],
//                tailGap = Iterable.empty[KeyValue],
//                newKeyValues = deleteKeyValues.iterator,
//                removeDeletes = false,
//                createdInLevel = 0,
//                valuesConfig = ValuesBlockConfig.random,
//                sortedIndexConfig = SortedIndexBlockConfig.random,
//                binarySearchIndexConfig = BinarySearchIndexBlockConfig.random,
//                hashIndexConfig = HashIndexBlockConfig.random,
//                bloomFilterConfig = BloomFilterBlockConfig.random,
//                segmentConfig = SegmentBlockConfig.random,
//                pathDistributor = createPathDistributor(),
//                segmentRefCacheLife = randomSegmentRefCacheLife(),
//                mmapSegment = mmapSegments
//              ).output.mapToSlice(_.sweep()).flatMap(_.iterator(randomBoolean())).toList
//
//            val expected: Seq[Memory] = (1 to 9).map(key => Memory.Range(key, key + 1, Value.remove(None), Value.update(10))) :+ Memory.remove(10)
//
//            removedRanges shouldBe expected
//        }
//      }
//    }
//
//    "return 1 new segment with only 1 key-value if all the KeyValues in the Segment were deleted but 1" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//          import sweeper._
//
//          implicit val testTimer: TestTimer = TestTimer.Empty
//
//          val keyValues = randomKeyValues(count = keyValuesCount)
//          val segment = GenSegment(keyValues)
//
//          val deleteKeyValues = Slice.of[Memory.Remove](keyValues.size - 1)
//          keyValues.drop(1).foreach(keyValue => deleteKeyValues add Memory.remove(keyValue.key))
//
//          val newSegments =
//            segment.put(
//              headGap = Iterable.empty[KeyValue],
//              tailGap = Iterable.empty[KeyValue],
//              newKeyValues = deleteKeyValues.iterator,
//              removeDeletes = true,
//              createdInLevel = 0,
//              valuesConfig = ValuesBlockConfig.random,
//              sortedIndexConfig = SortedIndexBlockConfig.random,
//              binarySearchIndexConfig = BinarySearchIndexBlockConfig.random,
//              hashIndexConfig = HashIndexBlockConfig.random,
//              bloomFilterConfig = BloomFilterBlockConfig.random,
//              segmentConfig = SegmentBlockConfig.random.copy(minSize = 4.mb),
//              pathDistributor = createPathDistributor(),
//              segmentRefCacheLife = randomSegmentRefCacheLife(),
//              mmapSegment = mmapSegments
//            ).output.mapToSlice(_.sweep())
//
//          newSegments.size shouldBe 1
//          newSegments.head.keyValueCount shouldBe 1
//
//          val newSegment = newSegments.head
//          val keyValue = keyValues.head
//
//          newSegment.get(keyValue.key, ThreadReadState.random).getUnsafe shouldBe keyValue
//
//          newSegment.lower(keyValue.key, ThreadReadState.random).toOption shouldBe empty
//          newSegment.higher(keyValue.key, ThreadReadState.random).toOption shouldBe empty
//      }
//    }
//
//    "distribute new Segments to multiple folders equally" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//          import sweeper._
//
//          val keyValues1 = Slice(Memory.put(1, 1), Memory.put(2, 2), Memory.put(3, 3), Memory.put(4, 4), Memory.put(5, 5), Memory.put(6, 6))
//          val segment = GenSegment(keyValues1)
//
//          val keyValues2 = Slice(Memory.put(7, 7), Memory.put(8, 8), Memory.put(9, 9), Memory.put(10, 10), Memory.put(11, 11), Memory.put(12, 12))
//
//          val dirs = (1 to 6) map (_ => Dir(genIntDir, 1))
//
//          val segmentSizeForMerge = Segment.segmentSizeForMerge(segment, randomBoolean())
//
//          val pathDistributor = pathDistributor(dirs, () => Seq(segment))
//
//          val segments =
//            if (isPersistent)
//              segment.put(
//                headGap = Iterable.empty[KeyValue],
//                tailGap = Iterable.empty[KeyValue],
//                newKeyValues = keyValues2.iterator,
//                removeDeletes = false,
//                createdInLevel = 0,
//                valuesConfig = ValuesBlockConfig.random,
//                sortedIndexConfig = SortedIndexBlockConfig.random,
//                binarySearchIndexConfig = BinarySearchIndexBlockConfig.random,
//                hashIndexConfig = HashIndexBlockConfig.random,
//                bloomFilterConfig = BloomFilterBlockConfig.random,
//                segmentConfig = SegmentBlockConfig.random.copy(minSize = segmentSizeForMerge / 4),
//                pathDistributor = pathDistributor,
//                segmentRefCacheLife = randomSegmentRefCacheLife(),
//                mmapSegment = mmapSegments
//              ).output.mapToSlice(_.sweep())
//            else
//              segment.put(
//                headGap = Iterable.empty[KeyValue],
//                tailGap = Iterable.empty[KeyValue],
//                newKeyValues = keyValues2.iterator,
//                removeDeletes = false,
//                createdInLevel = 0,
//                valuesConfig = ValuesBlockConfig.random,
//                sortedIndexConfig = SortedIndexBlockConfig.random,
//                binarySearchIndexConfig = BinarySearchIndexBlockConfig.random,
//                hashIndexConfig = HashIndexBlockConfig.random,
//                bloomFilterConfig = BloomFilterBlockConfig.random,
//                segmentConfig = SegmentBlockConfig.random.copy(minSize = 21.bytes),
//                pathDistributor = pathDistributor,
//                segmentRefCacheLife = randomSegmentRefCacheLife(),
//                mmapSegment = mmapSegments
//              ).output.mapToSlice(_.sweep())
//
//          //all returned segments contain all the KeyValues ???
//          //      segments should have size 5
//          //      segments(0).getAll().value shouldBe keyValues1.slice(0, 1).cut()
//          //      segments(1).getAll().value shouldBe keyValues1.slice(2, 3).cut()
//          //      segments(2).getAll().value shouldBe keyValues1.slice(4, 5).cut()
//          //      segments(3).getAll().value shouldBe keyValues2.slice(0, 1).cut()
//          //      segments(4).getAll().value shouldBe keyValues2.slice(2, 3).cut()
//          //      segments(5).getAll().value shouldBe keyValues2.slice(4, 5).cut()
//
//          //all the paths are used to write Segments
//          segments(0).path.getParent shouldBe dirs(0).path
//          segments(1).path.getParent shouldBe dirs(1).path
//          segments(2).path.getParent shouldBe dirs(2).path
//          segments(3).path.getParent shouldBe dirs(3).path
//
//          if (isPersistent)
//            segments(4).path.getParent shouldBe dirs(4).path
//
//        //all paths are used ???
//        //      distributor.queuedPaths shouldBe empty
//      }
//    }
//  }
//
//  "refresh" should {
//    "return new Segment with Removed key-values removed" in {
//      if (isPersistent) {
//        CoreTestSweeper {
//          implicit sweeper =>
//
//            val keyValues =
//              (1 to 100) map {
//                key =>
//                  eitherOne(randomRemoveKeyValue(key), randomRangeKeyValue(key, key + 1, FromValue.Null, randomRangeValue()))
//              } toSlice
//
//            val segment = GenSegment(keyValues).asInstanceOf[PersistentSegment]
//            segment.keyValueCount shouldBe keyValues.size
//            segment.iterator(randomBoolean()).toList shouldBe keyValues
//
//            val reopened = segment.reopen(segment.path)
//            reopened.keyValueCount shouldBe keyValues.size
//            reopened.refresh(
//              removeDeletes = true,
//              createdInLevel = 0,
//              valuesConfig = ValuesBlockConfig.random,
//              sortedIndexConfig = SortedIndexBlockConfig.random,
//              binarySearchIndexConfig = BinarySearchIndexBlockConfig.random,
//              hashIndexConfig = HashIndexBlockConfig.random,
//              bloomFilterConfig = BloomFilterBlockConfig.random,
//              segmentConfig = SegmentBlockConfig.random
//            ).await.output shouldBe empty
//        }
//      }
//    }
//
//    "return no new Segments if all the key-values in the Segment were expired" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//
//          val keyValues1 = (1 to 100).map(key => randomPutKeyValue(key, deadline = Some(1.second.fromNow))).toSlice
//          val segment = GenSegment(keyValues1)
//          segment.keyValueCount shouldBe keyValues1.size
//
//          sleep(2.seconds)
//
//          segment.refresh(
//            removeDeletes = true,
//            createdInLevel = 0,
//            valuesConfig = ValuesBlockConfig.random,
//            sortedIndexConfig = SortedIndexBlockConfig.random,
//            binarySearchIndexConfig = BinarySearchIndexBlockConfig.random,
//            hashIndexConfig = HashIndexBlockConfig.random,
//            bloomFilterConfig = BloomFilterBlockConfig.random,
//            segmentConfig = SegmentBlockConfig.random,
//            pathDistributor = createPathDistributor()
//          ).isEmpty shouldBe true
//      }
//    }
//
//    "return all key-values when removeDeletes is false and when Segment was created in another Level" in {
//      runThis(5.times) {
//        CoreTestSweeper {
//          implicit sweeper =>
//
//            val keyValues: Slice[Memory.Put] = (1 to 100).map(key => Memory.put(key, key, 1.second)).toSlice
//
//            val segment =
//              Benchmark(s"Created Segment: ${keyValues.size} keyValues", inlinePrint = true) {
//                GenSegment(
//                  keyValues = keyValues,
//                  createdInLevel = 3
//                )
//              }
//
//            segment.keyValueCount shouldBe keyValues.size
//
//            sleep(2.seconds)
//
//            val refresh =
//              Benchmark(s"Refresh Segment: ${keyValues.size} keyValues", inlinePrint = true) {
//                segment.refresh(
//                  removeDeletes = false,
//                  createdInLevel = 5,
//                  valuesConfig = ValuesBlockConfig.random,
//                  sortedIndexConfig = SortedIndexBlockConfig.random,
//                  binarySearchIndexConfig = BinarySearchIndexBlockConfig.random,
//                  hashIndexConfig = HashIndexBlockConfig.random,
//                  bloomFilterConfig = BloomFilterBlockConfig.random,
//                  segmentConfig = SegmentBlockConfig.random,
//                  pathDistributor = createPathDistributor()
//                ).mapToSlice(_.sweep())
//              }
//
//            refresh.flatMap(_.iterator(randomBoolean())).toList shouldBe keyValues
//        }
//      }
//    }
//
//    "return all key-values when removeDeletes is false and when Segment was created in same Level" in {
//      runThis(5.times, log = true) {
//        CoreTestSweeper {
//          implicit sweeper =>
//
//            CoreTestSweepers.createMemorySweeperMax().value.sweep()
//
//            val keyValues: Slice[Memory.Put] = (1 to (randomIntMax(100000) max 2)).map(key => Memory.put(key, key, 1.second)).toSlice
//
//            val enableCompression = randomBoolean()
//
//            val shouldPrefixCompress = randomBoolean()
//
//            val valuesConfig = ValuesBlockConfig.random(hasCompression = enableCompression)
//            val sortedIndexConfig = SortedIndexBlockConfig.random(hasCompression = enableCompression, shouldPrefixCompress = shouldPrefixCompress)
//            val binarySearchIndexConfig = BinarySearchIndexBlockConfig.random(hasCompression = enableCompression)
//            val hashIndexConfig = HashIndexBlockConfig.random(hasCompression = enableCompression)
//            val bloomFilterConfig = BloomFilterBlockConfig.random(hasCompression = enableCompression)
//
//            val segmentConfig: SegmentBlockConfig =
//              SegmentBlockConfig.random(
//                hasCompression = enableCompression,
//                minSegmentSize = Int.MaxValue,
//                //this test-case fails when maxKeyValuesPerSegment less than the original keyValues.size.
//                //in reality maxKeyValuesPerSegment will never been less than original key-value size.
//                //read the test-case comments below.
//                //                maxKeyValuesPerSegment = if (memory) keyValues.size else randomIntMax(keyValues.size)
//                maxKeyValuesPerSegment = keyValues.size
//              )
//
//            val segment =
//              Benchmark(s"Created Segment: ${keyValues.size} keyValues", inlinePrint = true) {
//                GenSegment(
//                  keyValues = keyValues,
//                  createdInLevel = 5,
//                  valuesConfig = valuesConfig,
//                  sortedIndexConfig = sortedIndexConfig,
//                  binarySearchIndexConfig = binarySearchIndexConfig,
//                  hashIndexConfig = hashIndexConfig,
//                  bloomFilterConfig = bloomFilterConfig,
//                  segmentConfig = segmentConfig
//                )
//              }
//
//            segment.keyValueCount shouldBe keyValues.size
//
//            sleep(2.seconds)
//
//            val refresh =
//              Benchmark(s"Refresh Segment: ${keyValues.size} keyValues", inlinePrint = true) {
//                segment.refresh(
//                  removeDeletes = false,
//                  createdInLevel = 5,
//                  valuesConfig = valuesConfig,
//                  sortedIndexConfig = sortedIndexConfig,
//                  binarySearchIndexConfig = binarySearchIndexConfig,
//                  hashIndexConfig = hashIndexConfig,
//                  bloomFilterConfig = bloomFilterConfig,
//                  segmentConfig = segmentConfig.copy(minSize = Int.MaxValue),
//                  pathDistributor = createPathDistributor()
//                ).mapToSlice(_.sweep())
//              }
//
//            refresh should have size 1
//            refresh.head shouldContainAll keyValues
//            println
//        }
//      }
//    }
//
//    /**
//     * The above test-case fails in the following refresh scenario when [[SegmentBlockConfig.maxCount]] specified
//     * on refresh is less than the number of key-values in the original Segment. This will never occur in reality
//     * as we do not allow dynamically changing the Level's [[SegmentBlockConfig.maxCount]] value. This will
//     * also not occur during copying from another level because then refresh re-calculates the byte size of the Segment
//     * based on the current level.
//     *
//     * Marking this test as ignored because it's still a valid test for the future when we want to allow dynamically
//     * updating the Levels config.
//     */
//    "debugger for previous test-case" ignore {
//      runThis(10.times, log = true) {
//        CoreTestSweeper {
//          implicit sweeper =>
//
//            CoreTestSweepers.createMemorySweeperMax().value.sweep()
//
//            val keyValues: Slice[Memory.Put] = (1 to 2).map(key => Memory.put(key, Slice.Null)).toSlice
//
//            val enableCompression = false
//
//            val shouldPrefixCompress = true
//
//            val valuesConfig = ValuesBlockConfig.random(hasCompression = enableCompression)
//            val sortedIndexConfig = SortedIndexBlockConfig.random(hasCompression = enableCompression, shouldPrefixCompress = shouldPrefixCompress)
//            val binarySearchIndexConfig = BinarySearchIndexBlockConfig.disabled()
//            val hashIndexConfig = HashIndexBlockConfig.disabled()
//            val bloomFilterConfig = BloomFilterBlockConfig.disabled()
//
//            val segmentConfig: SegmentBlockConfig =
//              SegmentBlockConfig.random(
//                hasCompression = enableCompression,
//                minSegmentSize = Int.MaxValue,
//                maxKeyValuesPerSegment = 1
//              )
//
//            val segment =
//              Benchmark(s"Created Segment: ${keyValues.size} keyValues", inlinePrint = true) {
//                GenSegment(
//                  keyValues = keyValues,
//                  createdInLevel = 5,
//                  valuesConfig = valuesConfig,
//                  sortedIndexConfig = sortedIndexConfig,
//                  binarySearchIndexConfig = binarySearchIndexConfig,
//                  hashIndexConfig = hashIndexConfig,
//                  bloomFilterConfig = bloomFilterConfig,
//                  segmentConfig = segmentConfig
//                )
//              }
//
//            segment.keyValueCount shouldBe keyValues.size
//
//            try {
//              val refresh =
//                Benchmark(s"Refresh Segment: ${keyValues.size} keyValues", inlinePrint = true) {
//                  segment.refresh(
//                    removeDeletes = false,
//                    createdInLevel = 5,
//                    valuesConfig = valuesConfig,
//                    sortedIndexConfig = sortedIndexConfig,
//                    binarySearchIndexConfig = binarySearchIndexConfig,
//                    hashIndexConfig = hashIndexConfig,
//                    bloomFilterConfig = bloomFilterConfig,
//                    segmentConfig = segmentConfig.copy(minSize = Int.MaxValue),
//                    pathDistributor = createPathDistributor()
//                  ).mapToSlice(_.sweep())
//                }
//
//              refresh should have size 1
//              refresh.head shouldContainAll keyValues
//              println
//            } catch {
//              case exception: Exception =>
//                throw exception
//
//              //                segment.refresh(
//              //                  removeDeletes = false,
//              //                  createdInLevel = 5,
//              //                  valuesConfig = valuesConfig,
//              //                  sortedIndexConfig = sortedIndexConfig,
//              //                  binarySearchIndexConfig = binarySearchIndexConfig,
//              //                  hashIndexConfig = hashIndexConfig,
//              //                  bloomFilterConfig = bloomFilterConfig,
//              //                  segmentConfig = segmentConfig.copy(minSize = Int.MaxValue)
//              //                )
//            }
//        }
//      }
//    }
//  }
//}
