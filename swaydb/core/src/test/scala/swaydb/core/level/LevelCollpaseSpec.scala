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
//import org.scalatest.OptionValues._
//import swaydb.IO
//import swaydb.effect.IOValues._
//import swaydb.config.MMAP
//import swaydb.core.CommonAssertions._
//import swaydb.core.CoreTestData._
//import swaydb.core._
//import swaydb.core.log.ALogSpec
//import swaydb.core.segment.block.segment.SegmentBlockConfig
//import swaydb.core.segment.data._
//import swaydb.core.segment.ref.search.ThreadReadState
//import swaydb.slice.Slice
//import swaydb.slice.order.{KeyOrder, TimeOrder}
//import swaydb.testkit.RunThis._
//import swaydb.utils.OperatingSystem
//import swaydb.utils.StorageUnits._
//
//import scala.collection.mutable.ListBuffer
//import scala.concurrent.duration._
//
//class LevelCollapseSpec0 extends LevelCollapseSpec
//
//class LevelCollapseSpec1 extends LevelCollapseSpec {
//  override def levelFoldersCount = 10
//  override def mmapSegments = MMAP.On(OperatingSystem.isWindows(), forceSave = GenForceSave.mmap())
//  override def level0MMAP = MMAP.On(OperatingSystem.isWindows(), forceSave = GenForceSave.mmap())
//  override def appendixStorageMMAP = MMAP.On(OperatingSystem.isWindows(), forceSave = GenForceSave.mmap())
//}
//
//class LevelCollapseSpec2 extends LevelCollapseSpec {
//  override def levelFoldersCount = 10
//  override def mmapSegments = MMAP.Off(forceSave = GenForceSave.standard())
//  override def level0MMAP = MMAP.Off(forceSave = GenForceSave.standard())
//  override def appendixStorageMMAP = MMAP.Off(forceSave = GenForceSave.standard())
//}
//
//class LevelCollapseSpec3 extends LevelCollapseSpec {
//  override def isMemorySpec = true
//}
//
//sealed trait LevelCollapseSpec extends AnyWordSpec {
//
//  implicit val keyOrder: KeyOrder[Slice[Byte]] = KeyOrder.default
//  implicit val testTimer: TestTimer = TestTimer.Empty
//  implicit val timeOrder: TimeOrder[Slice[Byte]] = TimeOrder.long
//  implicit val ec = TestExecutionContext.executionContext
//  val keyValuesCount = 100
//
//  //  override def deleteFiles: Boolean =
//  //    false
//
//  "collapse small Segments to 50% of the size when the Segment's size was reduced by deleting 50% of it's key-values" in {
//    CoreTestSweeper {
//      implicit sweeper =>
//        import sweeper._
//
//        //disable throttling so that it does not automatically collapse small Segments
//        val level = TestLevel(segmentConfig = SegmentBlockConfig.random(minSegmentSize = 1.kb, mmap = mmapSegments, deleteDelay = Duration.Zero))
//        val keyValues = randomPutKeyValues(1000, addPutDeadlines = false, startId = Some(0))(TestTimer.Empty)
//        level.put(keyValues).runRandomIO.get
//
//        val segmentCountBeforeDelete = level.segmentsCount()
//        segmentCountBeforeDelete > 1 shouldBe true
//
//        assertAllSegmentsCreatedInLevel(level)
//
//        val keyValuesNoDeleted = ListBuffer.empty[KeyValue]
//        val deleteEverySecond =
//          keyValues.zipWithIndex.toList flatMap {
//            case (keyValue, index) =>
//              if (index % 2 == 0)
//                Some(Memory.Remove(keyValue.key, None, Time.empty))
//              else {
//                keyValuesNoDeleted += keyValue
//                None
//              }
//          }
//        //delete half of the key values which will create small Segments
//        level.put(Slice.wrap(deleteEverySecond.toArray)).runRandomIO.get
//
//        level.collapse(level.segments(), removeDeletedRecords = false).awaitInf match {
//          case LevelCollapseResult.Empty =>
//            fail(s"Expected: ${LevelCollapseResult.Collapsed.getClass.getSimpleName}. Actor: ${LevelCollapseResult.Empty.productPrefix}")
//
//          case collapsed: LevelCollapseResult.Collapsed =>
//            level.commit(collapsed) shouldBe IO.unit
//        }
//
//        //since every second key-value was delete, the number of Segments is reduced to half
//        level.segmentFilesInAppendix shouldBe <=((segmentCountBeforeDelete / 2) + 1) //+1 for odd number of key-values
//        assertReads(Slice.wrap(keyValuesNoDeleted.toArray), level)
//
//    }
//  }
//
//  "collapse all small Segments into one of the existing small Segments, if the Segment was reopened with a larger segment size" in {
//    if (isPersistent) //memory Level cannot be reopened.
//      runThis(10.times, log = true) {
//        CoreTestSweeper {
//          implicit sweeper =>
//            import sweeper._
//
//            //          implicit val compressionType: Option[KeyValueCompressionType] = randomCompressionTypeOption(keyValuesCount)
//            //disable throttling so that it does not automatically collapse small Segments
//            val level = TestLevel(segmentConfig = SegmentBlockConfig.random(minSegmentSize = 1.kb, deleteDelay = Duration.Zero, mmap = mmapSegments))
//
//            assertAllSegmentsCreatedInLevel(level)
//
//            val keyValues = randomPutKeyValues(1000, startId = Some(0), valueSize = 0, addPutDeadlines = false)(TestTimer.Empty)
//            level.put(keyValues) shouldBe IO.unit
//
//            level.segmentsCount() > 1 shouldBe true
//            level.closeNoSweep().runRandomIO.get
//
//            //reopening the Level will make the Segments unreadable.
//            //reopen the Segments
//
//            //reopen the Level with larger min segment size
//            val reopenLevel = level.reopen(segmentSize = 20.mb)
//
//            reopenLevel.collapse(reopenLevel.segments(), removeDeletedRecords = false).await match {
//              case LevelCollapseResult.Empty =>
//                fail(s"Expected: ${LevelCollapseResult.Collapsed.getClass.getSimpleName}. Actor: ${LevelCollapseResult.Empty.productPrefix}")
//
//              case collapsed: LevelCollapseResult.Collapsed =>
//                reopenLevel.commit(collapsed) shouldBe IO.unit
//            }
//
//            //resulting segments is 1
//            eventually {
//              level.segmentFilesOnDisk() should have size 1
//            }
//            //can still read Segments
//            assertReads(keyValues, reopenLevel)
//            val reopen2 = reopenLevel.reopen
//            eventual(assertReads(keyValues, reopen2))
//
//        }
//      }
//  }
//
//  "clear expired key-values" in {
//    //this test is similar as the above collapsing small Segment test.
//    //Remove or expiring key-values should have the same result
//    CoreTestSweeper {
//      implicit sweeper =>
//        import sweeper._
//
//        val level = TestLevel(segmentConfig = SegmentBlockConfig.random(minSegmentSize = 1.kb, mmap = mmapSegments))
//        val expiryAt = 5.seconds.fromNow
//        val keyValues = randomPutKeyValues(1000, valueSize = 0, startId = Some(0), addPutDeadlines = false)(TestTimer.Empty)
//        level.put(keyValues).runRandomIO.get
//        val segmentCountBeforeDelete = level.segmentsCount()
//        segmentCountBeforeDelete > 1 shouldBe true
//
//        val keyValuesNotExpired = ListBuffer.empty[KeyValue]
//        val expireEverySecond =
//          keyValues.zipWithIndex.toList flatMap {
//            case (keyValue, index) =>
//              if (index % 2 == 0)
//                Some(Memory.Remove(keyValue.key, Some(expiryAt + index.millisecond), Time.empty))
//              else {
//                keyValuesNotExpired += keyValue
//                None
//              }
//          }
//
//        //delete half of the key values which will create small Segments
//        level.put(Slice.wrap(expireEverySecond.toArray)).runRandomIO.get
//        keyValues.zipWithIndex foreach {
//          case (keyValue, index) =>
//            if (index % 2 == 0)
//              level.get(keyValue.key, ThreadReadState.random).runRandomIO.get.toOptionPut.value.deadline should contain(expiryAt + index.millisecond)
//        }
//
//        sleep(20.seconds)
//
//        level.collapse(level.segments(), removeDeletedRecords = false).awaitInf match {
//          case LevelCollapseResult.Empty =>
//            fail("")
//
//          case collapsed: LevelCollapseResult.Collapsed =>
//            level.commit(collapsed) shouldBe IO.unit
//        }
//
//        level.segmentFilesInAppendix should be <= ((segmentCountBeforeDelete / 2) + 1)
//
//        assertReads(Slice.wrap(keyValuesNotExpired.toArray), level)
//    }
//  }
//
//  "update createdInLevel" in new ALogSpec {
//    CoreTestSweeper {
//      implicit sweeper =>
//        import sweeper._
//
//        val level = TestLevel(segmentConfig = SegmentBlockConfig.random(minSegmentSize = 1.kb, mmap = mmapSegments))
//
//        val keyValues = randomPutKeyValues(keyValuesCount, addExpiredPutDeadlines = false)
//        val maps = GenLog(keyValues)
//        level.putMap(maps).get
//
//        val nextLevel = TestLevel()
//        nextLevel.putSegments(level.segments()).get
//
//        if (isPersistent) nextLevel.segments() foreach (_.createdInLevel shouldBe level.levelNumber)
//
//        nextLevel.collapse(nextLevel.segments(), removeDeletedRecords = false).awaitInf match {
//          case LevelCollapseResult.Empty =>
//            fail("")
//
//          case collapsed: LevelCollapseResult.Collapsed =>
//            nextLevel.commit(collapsed) shouldBe IO.unit
//        }
//
//        nextLevel.segments() foreach (_.createdInLevel shouldBe nextLevel.levelNumber)
//    }
//  }
//}
