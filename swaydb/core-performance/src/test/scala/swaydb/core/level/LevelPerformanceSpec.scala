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
////
////package swaydb.core.level
////
////import swaydb.effect.IOValues._
////import swaydb.testkit.RunThis._
////import swaydb.core.{TestBase, CoreTestSweeper}
////import swaydb.core.TestData._
////import swaydb.core.segment.cache.sweeper.MemorySweeper
////import swaydb.core.segment.ReadState
////import swaydb.core.segment.block.binarysearch.{BinarySearchEntryFormat, BinarySearchIndexBlock}
////import swaydb.core.segment.block.{BloomFilterBlock, SegmentBlock, SortedIndexBlock, ValuesBlock}
////import swaydb.core.segment.block.hashindex.{HashIndexBlock, HashIndexEntryFormat}
////import swaydb.Benchmark
////import swaydb.config.{IOAction, IOStrategy}
////import swaydb.slice.order.KeyOrder
////import swaydb.slice.Slice
////import swaydb.config.util.StorageUnits._
////import swaydb.core.CommonAssertions._
////import scala.util.Random
////
//////@formatter:off
////class LevelPerformanceSpec0 extends LevelPerformanceSpec {
////  override def mmapSegmentsOnWrite = false
////  override def mmapSegmentsOnRead = false
////}
////
////class LevelPerformanceSpec1 extends LevelPerformanceSpec {
////  override def levelFoldersCount = 10
////  override def mmapSegmentsOnWrite = true
////  override def mmapSegmentsOnRead = true
////  override def level0MMAP = MMAP.Enabled(OperatingSystem.isWindows(), forceSave = GenForceSave.mmap())
////  override def appendixStorageMMAP = MMAP.Enabled(OperatingSystem.isWindows(), forceSave = GenForceSave.mmap())
////}
////
////class LevelPerformanceSpec2 extends LevelPerformanceSpec {
////  override def levelFoldersCount = 10
////  override def mmapSegmentsOnWrite = false
////  override def mmapSegmentsOnRead = false
////  override def level0MMAP = MMAP.Disabled(forceSave = GenForceSave.standard())
////  override def appendixStorageMMAP = MMAP.Disabled(forceSave = GenForceSave.standard())
////}
////
////class LevelPerformanceSpec3 extends LevelPerformanceSpec {
////  override def inMemoryStorage = true
////}
//////@formatter:on
////
////sealed trait LevelPerformanceSpec extends TestBase {
////
////  implicit val keyOrder: KeyOrder[Slice[Byte]] = KeyOrder.default
////
////  implicit val keyValueMemorySweeper: Option[MemorySweeper.KeyValue] = CoreTestSweeper.someMemorySweeper10
////  val keyValuesCount = 100
////
////  val keyValues =
////    randomPutKeyValues(1000000, startId = Some(0), addPutDeadlines = false)
////  //  val keyValues = randomIntKeyValues(250000)
////
////  //  override def deleteFiles: Boolean = false
////
////  val shuffledKeyValues = Random.shuffle(keyValues)
////
////  def assertGet(level: Level) = {
////    val readState = ReadState.limitHashMap(10, 2)
////    shuffledKeyValues foreach {
////      keyValue =>
////        //        val key = keyValue.key.readInt()
////        //        if (key % 1000 == 0)
////        //          println(s"Reading $key")
////        level.get(keyValue.key, readState).runIO.get.get
////      //        level.get(keyValue.key).runIO
////      //        val got = level.get(keyValue.key, readState).runIO.get.get
////      //        got shouldBe keyValue
////      //        got.getOrFetchValue() shouldBe keyValue
////      //        println("value: " + level.get(keyValue.key).runIO._2.runIO.asInt())
////    }
////  }
////
////  def readLower(level: Level) = {
////    val readState = ReadState.limitHashMap(10, 2)
////    (1 until keyValues.size) foreach {
////      index =>
////        //        println(s"index: $index")
////        level.lower(keyValues(index).key, readState)
////      //        val keyValue = level.lower(keyValues(index).key).runIO
////      //        keyValue.key shouldBe keyValues(index - 1).key
////      //        keyValue.getOrFetchValue().runIO.value shouldBe keyValues(index - 1).getOrFetchValue().runIO.value
////    }
////  }
////
////  def readHigher(level: Level) =
////    (0 until keyValues.size - 1) foreach {
////      index =>
////      //        level.higher(keyValues(index).key)
////      //        val keyValue = level.higher(keyValues(index).key).runIO
////      //        keyValue.key shouldBe keyValues(index + 1).key
////      //        keyValue.getOrFetchValue().runIO.value shouldBe keyValues(index + 1).getOrFetchValue().runIO.value
////    }
////
////  def strategy(action: IOAction): IOStrategy =
////    action match {
////      case IOAction.OpenResource =>
////        IOStrategy.SynchronisedIO(cacheOnAccess = true)
////      case IOAction.ReadDataOverview =>
////        IOStrategy.SynchronisedIO(cacheOnAccess = true)
////      case IOAction.ReadCompressedData(compressedSize, decompressedSize) =>
////        ???
////      case IOAction.ReadUncompressedData(size) =>
////        IOStrategy.SynchronisedIO(cacheOnAccess = false)
////    }
////
////  var level =
////    TestLevel(
////      segmentSize = 2.mb,
////      sortedIndexConfig =
////        SegmentIndexBlockConfig(
////          ioStrategy = _ => IOStrategy.ConcurrentIO(cacheOnAccess = true),
////          prefixCompressionResetCount = 0,
////          prefixCompressKeysOnly = false,
////          enableAccessPositionIndex = true,
////          normaliseIndex = false,
////          compressions = _ => Seq.empty
////        ),
////      binarySearchIndexConfig =
////        BinarySearchIndexConfig(
////          enabled = true,
////          format = BinarySearchEntryFormat.CopyKey,
////          minimumNumberOfKeys = 1,
////          searchSortedIndexDirectlyIfPossible = false,
////          fullIndex = true,
////          ioStrategy = _ => IOStrategy.ConcurrentIO(cacheOnAccess = true),
////          compressions = _ => Seq.empty
////        ),
////      //      binarySearchIndexConfig =
////      //        BinarySearchIndexConfig.disabled,
////      valuesConfig =
////        ValuesBlockConfig(
////          compressDuplicateValues = true,
////          compressDuplicateRangeValues = true,
////          ioStrategy = _ => IOStrategy.ConcurrentIO(cacheOnAccess = true),
////          compressions = _ => Seq.empty
////        ),
////      hashIndexConfig =
////        HashIndexConfig(
////          maxProbe = 1,
////          format = HashIndexEntryFormat.Reference,
////          minimumNumberOfKeys = 5,
////          minimumNumberOfHits = 5,
////          allocateSpace = _.requiredSpace,
////          ioStrategy = _ => IOStrategy.ConcurrentIO(cacheOnAccess = true),
////          compressions = _ => Seq.empty
////        ),
////      //      hashIndexConfig = HashIndexConfig.disabled,
////      bloomFilterConfig =
////        BloomFilterConfig.disabled,
////      //        BloomFilterConfig(
////      //          falsePositiveRate = 0.001,
////      //          minimumNumberOfKeys = 2,
////      //          optimalMaxProbe = _ => 1,
////      //          blockIO = _ => IOStrategy.SynchronisedIO(cacheOnAccess = true),
////      //          compressions = _ => Seq.empty
////      //        ),
////      segmentConfig = SegmentBlockConfig.default,
////      pushForward = PushForwardStrategy.On,
////      deleteDelay = Duration.Zero
////    )
////
////  level.put(keyValues).runRandomIO.get
////
////  def reopenLevel() = {
////    println("Re-opening Level")
////    level.segments().foreach {
////      segment =>
////        segment.clearCachedKeyValues()
////        segment.close.runRandomIO.get
////    }
////    level = level.reopen
////  }
////
////  "Level read performance 1" in {
////    Benchmark(s"read ${keyValues.size} key values when Level persistent = ${levelStorage.persistent}, mmapSegmentWrites = ${levelStorage.mmapSegmentsOnWrite}, mmapSegmentReads = ${levelStorage.mmapSegmentsOnRead}") {
////      assertGet(level)
////    }
////
////    Benchmark(s"read ${keyValues.size} key values when Level persistent = ${levelStorage.persistent}, mmapSegmentWrites = ${levelStorage.mmapSegmentsOnWrite}, mmapSegmentReads = ${levelStorage.mmapSegmentsOnRead}") {
////      assertGet(level)
////    }
////  }
////
////  "Level read benchmark 2" in {
////    Benchmark(s"read ${keyValues.size} key values when Level persistent = ${levelStorage.persistent}, mmapSegmentWrites = ${levelStorage.mmapSegmentsOnWrite}, mmapSegmentReads = ${levelStorage.mmapSegmentsOnRead}") {
////      assertGet(level)
////    }
////  }
////
////  "Level read benchmark 3" in {
////    Benchmark(s"read ${keyValues.size} key values when Level persistent = ${levelStorage.persistent}, mmapSegmentWrites = ${levelStorage.mmapSegmentsOnWrite}, mmapSegmentReads = ${levelStorage.mmapSegmentsOnRead}") {
////      readLower(level)
////    }
////  }
////
////  "Level read benchmark 4" in {
////    if (levelStorage.persistent) reopenLevel()
////    Benchmark(s"read ${keyValues.size} key values when Level persistent = ${levelStorage.persistent}, mmapSegmentWrites = ${levelStorage.mmapSegmentsOnWrite}, mmapSegmentReads = ${levelStorage.mmapSegmentsOnRead}") {
////      readLower(level)
////    }
////  }
////
////  "Level read benchmark 5" in {
////    if (levelStorage.persistent) reopenLevel()
////    Benchmark(s"read ${keyValues.size} key values when Level persistent = ${levelStorage.persistent}, mmapSegmentWrites = ${levelStorage.mmapSegmentsOnWrite}, mmapSegmentReads = ${levelStorage.mmapSegmentsOnRead}") {
////      readHigher(level)
////    }
////  }
////
////  "Level read benchmark 6" in {
////    Benchmark(s"read ${keyValues.size} key values when Level persistent = ${levelStorage.persistent}, mmapSegmentWrites = ${levelStorage.mmapSegmentsOnWrite}, mmapSegmentReads = ${levelStorage.mmapSegmentsOnRead}") {
////      readHigher(level)
////    }
////  }
////}
