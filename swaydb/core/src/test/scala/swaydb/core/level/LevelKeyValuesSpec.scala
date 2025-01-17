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
//package swaydb.core.level
//
//import org.scalamock.scalatest.MockFactory
//import org.scalatest.PrivateMethodTester
//import swaydb.IO
//import swaydb.effect.IOValues._
//import swaydb.config.MMAP
//import swaydb.core.CommonAssertions._
//import swaydb.core.CoreTestData._
//import swaydb.core._
//import swaydb.core.segment.block.segment.SegmentBlockConfig
//import swaydb.core.segment.data._
//import swaydb.core.segment.ref.search.ThreadReadState
//import swaydb.serializers.Default._
//import swaydb.serializers._
//import swaydb.slice.Slice
//import swaydb.slice.order.{KeyOrder, TimeOrder}
//import swaydb.testkit.RunThis._
//import swaydb.utils.OperatingSystem
//import swaydb.utils.StorageUnits._
//
//import scala.concurrent.duration._
//import swaydb.testkit.TestKit._
//
//class LevelKeyValuesSpec0 extends LevelKeyValuesSpec
//
//class LevelKeyValuesSpec1 extends LevelKeyValuesSpec {
//  override def levelFoldersCount = 10
//  override def mmapSegments = MMAP.On(OperatingSystem.isWindows(), forceSave = GenForceSave.mmap())
//  override def level0MMAP = MMAP.On(OperatingSystem.isWindows(), forceSave = GenForceSave.mmap())
//  override def appendixStorageMMAP = MMAP.On(OperatingSystem.isWindows(), forceSave = GenForceSave.mmap())
//}
//
//class LevelKeyValuesSpec2 extends LevelKeyValuesSpec {
//  override def levelFoldersCount = 10
//  override def mmapSegments = MMAP.Off(forceSave = GenForceSave.standard())
//  override def level0MMAP = MMAP.Off(forceSave = GenForceSave.standard())
//  override def appendixStorageMMAP = MMAP.Off(forceSave = GenForceSave.standard())
//}
//
//class LevelKeyValuesSpec3 extends LevelKeyValuesSpec {
//  override def isMemorySpec = true
//}
//
//sealed trait LevelKeyValuesSpec extends AnyWordSpec {
//
//  implicit val keyOrder: KeyOrder[Slice[Byte]] = KeyOrder.default
//  implicit val testTimer: TestTimer = TestTimer.Empty
//  implicit val timeOrder: TimeOrder[Slice[Byte]] = TimeOrder.long
//  val keyValuesCount = 100
//  implicit val ec = TestExecutionContext.executionContext
//
//  //  override def deleteFiles: Boolean =
//  //    false
//
//  "put KeyValues" should {
//    "write a key-values to the Level" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//          import sweeper._
//
//          val level = TestLevel()
//
//          val keyValues = randomPutKeyValues(startId = Some(1))
//          level.put(keyValues).runRandomIO.get
//          level.put(Slice(keyValues.head)).runRandomIO.get
//
//          level.segments() foreach {
//            segment =>
//              segment.createdInLevel shouldBe level.levelNumber
//          }
//
//          assertReads(keyValues, level)
//
//          if (isPersistent) {
//            val reopen = level.reopen
//            assertReads(keyValues, reopen)
//          }
//      }
//    }
//
//    "return an empty level if all the key values in the Level were REMOVED and if Level is the only Level" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//          import sweeper._
//
//          val level = TestLevel(segmentConfig = SegmentBlockConfig.random(minSegmentSize = 1.kb, mmap = mmapSegments))
//
//          val keyValues = randomPutKeyValues(keyValuesCount)
//          level.put(keyValues) shouldBe IO.unit
//
//          val deleteKeyValues = Slice.allocate[Memory](keyValues.size * 2)
//          keyValues foreach {
//            keyValue =>
//              deleteKeyValues add Memory.remove(keyValue.key)
//          }
//          //also add another set of Delete key-values where the keys do not belong to the Level but since there is no lower level
//          //these delete keys should also be removed
//          val lastKeyValuesId = keyValues.last.key.read[Int] + 10000
//
//          (lastKeyValuesId until keyValues.size + lastKeyValuesId) foreach {
//            id =>
//              deleteKeyValues add Memory.remove(id, randomly(expiredDeadline()))
//          }
//
//          level.put(deleteKeyValues).get
//          level.segmentFilesInAppendix shouldBe 0
//
//          level.isEmpty shouldBe true
//          if (isPersistent) {
//            level.reopen.isEmpty shouldBe true
//            level.segmentFilesOnDisk() shouldBe empty
//          }
//      }
//    }
//
//    "not return an empty level if all the key values in the Level were REMOVED but it has lower level" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//          import sweeper._
//
//          val level = TestLevel(nextLevel = Some(TestLevel()))
//
//          val keyValues = randomPutKeyValues()
//          level.put(keyValues).runRandomIO.get
//
//          val deleteKeyValues = Slice.allocate[Memory](keyValues.size)
//          keyValues foreach {
//            keyValue =>
//              deleteKeyValues add Memory.remove(keyValue.key)
//          }
//
//          level.put(deleteKeyValues).runRandomIO.get
//          level.isEmpty shouldBe false
//          keyValues foreach {
//            keyValue =>
//              level.get(keyValue.key, ThreadReadState.random).runRandomIO.get.toOptionPut shouldBe empty
//          }
//      }
//    }
//
//    "return an empty level if all the key values in the Level were REMOVED by RANGE and if Level is the only Level" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//          import sweeper._
//
//          val level = TestLevel(segmentConfig = SegmentBlockConfig.random(minSegmentSize = 1.kb, mmap = mmapSegments))
//
//          val keyValues = randomPutKeyValues(keyValuesCount)
//          level.put(keyValues).runRandomIO.get
//
//          level.put(Slice(Memory.Range(keyValues.head.key, keyValues.last.key.readInt() + 1, Value.FromValue.Null, Value.remove(None)))).runRandomIO.get
//          level.segmentFilesInAppendix shouldBe 0
//
//          level.isEmpty shouldBe true
//          if (isPersistent) {
//            level.reopen.isEmpty shouldBe true
//            level.segmentFilesOnDisk() shouldBe empty
//          }
//      }
//    }
//
//    "not return an empty level if all the key values in the Level were REMOVED by RANGE but it has a lower Level" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//          import sweeper._
//
//          val level = TestLevel(segmentConfig = SegmentBlockConfig.random(minSegmentSize = 1.kb, mmap = mmapSegments), nextLevel = Some(TestLevel()))
//
//          val keyValues = randomPutKeyValues(keyValuesCount)
//          level.put(keyValues).runRandomIO.get
//          val segmentsCountBeforeRemove = level.segmentFilesInAppendix
//
//          level.put(Slice(Memory.Range(keyValues.head.key, keyValues.last.key.readInt() + 1, Value.FromValue.Null, Value.remove(None)))).runRandomIO.get
//          level.segmentFilesInAppendix shouldBe segmentsCountBeforeRemove
//
//          level.isEmpty shouldBe false
//          if (isPersistent) {
//            level.reopen.isEmpty shouldBe false
//            level.segmentFilesOnDisk() should have size segmentsCountBeforeRemove
//          }
//      }
//    }
//
//    "return an empty level if all the key values in the Level were EXPIRED and if Level is the only Level" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//          import sweeper._
//
//          val level = TestLevel(segmentConfig = SegmentBlockConfig.random(minSegmentSize = 1.kb, mmap = mmapSegments))
//
//          val keyValues = randomPutKeyValues(keyValuesCount)
//          level.put(keyValues).runRandomIO.get
//
//          val deleteKeyValues = Slice.allocate[Memory](keyValues.size * 2)
//          keyValues foreach {
//            keyValue =>
//              deleteKeyValues add Memory.remove(keyValue.key, 1.seconds)
//          }
//          //also add another set of Delete key-values where the keys do not belong to the Level but since there is no lower level
//          //these delete keys should also be removed
//          val lastKeyValuesId = keyValues.last.key.read[Int] + 1
//          (lastKeyValuesId until keyValues.size + lastKeyValuesId) foreach {
//            id =>
//              deleteKeyValues add Memory.remove(id, randomly(expiredDeadline()))
//          }
//
//          level.nextLevel shouldBe empty
//
//          level.put(deleteKeyValues).runRandomIO.get
//
//          sleep(2.seconds)
//
//          level.segments() foreach {
//            segment =>
//              level.commit(level.refresh(Seq(segment), removeDeletedRecords = false).awaitInf) shouldBe IO.unit
//          }
//
//          //expired key-values return empty after 2.seconds
//          keyValues foreach {
//            keyValue =>
//              level.get(keyValue.key, ThreadReadState.random).runRandomIO.get.toOptionPut shouldBe empty
//          }
//
//          level.segmentFilesInAppendix shouldBe 0
//
//          level.isEmpty shouldBe true
//
//          if (isPersistent) {
//            level.reopen.isEmpty shouldBe true
//            level.segmentFilesOnDisk() shouldBe empty
//          }
//      }
//    }
//
//    "not return an empty level if all the key values in the Level were EXPIRED and if Level has a lower Level" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//          import sweeper._
//
//          val level = TestLevel(segmentConfig = SegmentBlockConfig.random(minSegmentSize = 1.kb, mmap = mmapSegments), nextLevel = Some(TestLevel()))
//
//          val keyValues = randomPutKeyValues(keyValuesCount)
//          level.put(keyValues).runRandomIO.get
//
//          val deleteKeyValues = Slice.allocate[Memory](keyValues.size * 2)
//          keyValues foreach {
//            keyValue =>
//              deleteKeyValues add Memory.remove(keyValue.key, 0.seconds)
//          }
//          //also add another set of Delete key-values where the keys do not belong to the Level but since there is no lower level
//          //these delete keys should also be removed
//          val lastKeyValuesId = keyValues.last.key.read[Int] + 1
//          (lastKeyValuesId until keyValues.size + lastKeyValuesId) foreach {
//            id =>
//              deleteKeyValues add Memory.remove(id, randomly(expiredDeadline()))
//          }
//
//          level.put(deleteKeyValues).runRandomIO.get
//
//          //expired key-values return empty.
//          keyValues foreach {
//            keyValue =>
//              level.get(keyValue.key, ThreadReadState.random).runRandomIO.get.toOptionPut shouldBe empty
//          }
//
//          //sleep for 2.seconds and Segments should still exists.
//          sleep(2.seconds)
//          level.isEmpty shouldBe false
//          level.segmentFilesInAppendix should be >= 1
//
//          if (isPersistent) {
//            level.reopen.isEmpty shouldBe false
//            level.segmentFilesOnDisk().size should be >= 1
//          }
//      }
//    }
//
//    "return an empty level if all the key values in the Level were EXPIRED by RANGE and if Level is the only Level" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//          import sweeper._
//
//          val level = TestLevel(segmentConfig = SegmentBlockConfig.random(minSegmentSize = 1.kb, mmap = mmapSegments))
//
//          val keyValues = randomPutKeyValues(keyValuesCount)
//          level.put(keyValues).runRandomIO.get
//
//          level.put(Slice(Memory.Range(keyValues.head.key, keyValues.last.key.readInt() + 1, Value.FromValue.Null, Value.remove(2.seconds.fromNow)))).runRandomIO.get
//
//          //expired key-values return empty after 2.seconds
//          eventual(5.seconds) {
//            keyValues foreach {
//              keyValue =>
//                level.get(keyValue.key, ThreadReadState.random).runRandomIO.get.toOptionPut shouldBe empty
//            }
//          }
//
//          level.commit(level.refresh(level.segments(), removeDeletedRecords = false).await) shouldBe IO.unit
//
//          level.segmentFilesInAppendix shouldBe 0
//
//          level.isEmpty shouldBe true
//
//          if (isPersistent) {
//            level.reopen.isEmpty shouldBe true
//            level.segmentFilesOnDisk() shouldBe empty
//          }
//      }
//    }
//
//    "not return an empty level if all the key values in the Level were EXPIRED by RANGE and if Level has a last Level" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//          import sweeper._
//
//          val level = TestLevel(segmentConfig = SegmentBlockConfig.random(minSegmentSize = 1.kb, mmap = mmapSegments), nextLevel = Some(TestLevel()))
//
//          val keyValues = randomPutKeyValues(keyValuesCount)
//          level.put(keyValues).runRandomIO.get
//
//          level.put(Slice(Memory.Range(keyValues.head.key, keyValues.last.key.readInt() + 1, Value.FromValue.Null, Value.remove(2.seconds.fromNow)))).runRandomIO.get
//
//          //expired key-values return empty after 2.seconds
//          eventual(5.seconds) {
//            keyValues foreach {
//              keyValue =>
//                level.get(keyValue.key, ThreadReadState.random).runRandomIO.get.toOptionPut shouldBe empty
//            }
//          }
//
//          level.segmentFilesInAppendix should be >= 1
//
//          level.isEmpty shouldBe false
//
//          if (isPersistent) {
//            level.reopen.isEmpty shouldBe false
//            level.segmentFilesOnDisk().size should be >= 1
//          }
//      }
//    }
//  }
//}
