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
//package swaydb.core.compaction.throttle.behaviour
//
//import swaydb.IO
//import swaydb.config.MMAP
//import swaydb.core.CommonAssertions._
//import swaydb.core.CoreTestData._
//import swaydb.core.compaction.throttle.LevelState
//import swaydb.core.segment.Segment
//import swaydb.core.segment.block.segment.SegmentBlockConfig
//import swaydb.core.util.DefIO
//import swaydb.core.{ACoreSpec, CoreTestSweeper, TestExecutionContext, GenForceSave}
//import swaydb.core.level.ALevelSpec
//import swaydb.serializers.Default._
//import swaydb.serializers._
//import swaydb.slice.Slice
//import swaydb.testkit.RunThis._
//import swaydb.utils.OperatingSystem
//import swaydb.utils.StorageUnits._
//
//import scala.concurrent.duration._
//import swaydb.testkit.TestKit._
//
//class BehaviourWakeUp_singleLevel_compactNonEmptyLastLevel_Spec0 extends BehaviourWakeUp_singleLevel_compactNonEmptyLastLevel_Spec
//
//class BehaviourWakeUp_singleLevel_compactNonEmptyLastLevel_Spec1 extends BehaviourWakeUp_singleLevel_compactNonEmptyLastLevel_Spec {
//  override def levelFoldersCount = 10
//  override def mmapSegments = MMAP.On(OperatingSystem.isWindows(), forceSave = GenForceSave.mmap())
//  override def level0MMAP = MMAP.On(OperatingSystem.isWindows(), forceSave = GenForceSave.mmap())
//  override def appendixStorageMMAP = MMAP.On(OperatingSystem.isWindows(), forceSave = GenForceSave.mmap())
//}
//
//class BehaviourWakeUp_singleLevel_compactNonEmptyLastLevel_Spec2 extends BehaviourWakeUp_singleLevel_compactNonEmptyLastLevel_Spec {
//  override def levelFoldersCount = 10
//  override def mmapSegments = MMAP.Off(forceSave = GenForceSave.standard())
//  override def level0MMAP = MMAP.Off(forceSave = GenForceSave.standard())
//  override def appendixStorageMMAP = MMAP.Off(forceSave = GenForceSave.standard())
//}
//
//class BehaviourWakeUp_compactLastLevel_singleLevel_Spec extends BehaviourWakeUp_singleLevel_compactNonEmptyLastLevel_Spec {
//  override def isMemorySpec = true
//}
//
//sealed trait BehaviourWakeUp_singleLevel_compactNonEmptyLastLevel_Spec extends AnyWordSpec {
//
//  implicit val ec = TestExecutionContext.executionContext
//
//  "ignore compaction" when {
//    "empty" in {
//      runThis(10.times, log = true) {
//        CoreTestSweeper {
//          implicit sweeper =>
//            import sweeper._
//
//            val level = TestLevel()
//            //get expected deadline early and expect the state's deadline to be greater than this
//            //to account for the time taken running this test
//            val expectedDeadline = LevelState.longSleep
//
//            val state =
//              BehaviorWakeUp.compactLastLevel(
//                level = level,
//                stateId = Int.MaxValue,
//                pushStrategy = randomPushStrategy()
//              ).awaitInf
//
//            val sleeping = state.shouldBeInstanceOf[LevelState.Sleeping]
//
//            sleeping.stateId shouldBe Int.MaxValue
//            //deadline should be greater than long sleep.
//            sleeping.sleepDeadline should be > expectedDeadline
//        }
//      }
//    }
//
//    "there is only one file with no expired key-values" in {
//      runThis(10.times, log = true) {
//        CoreTestSweeper {
//          implicit sweeper =>
//            import sweeper._
//
//            val keyValues =
//              Slice.range(1, 6) mapToSlice {
//                key =>
//                  randomPutKeyValue(key, randomString(), someOrNone(1.hour.fromNow))
//              }
//
//            val level =
//              TestLevel(
//                keyValues = keyValues,
//                segmentConfig = SegmentBlockConfig.random2(deleteDelay = Duration.Zero, mmap = mmapSegments, minSegmentSize = 100.mb)
//              )
//            //get expected deadline early and expect the state's deadline to be greater than this
//            //to account for the time taken running this test
//            val expectedDeadline = level.nextCompactionDelay.fromNow
//
//            val segmentsBeforeCompaction = level.segments()
//            level.isEmpty shouldBe false
//            segmentsBeforeCompaction should have size 1
//
//            val state =
//              BehaviorWakeUp.compactLastLevel(
//                level = level,
//                stateId = Int.MaxValue,
//                pushStrategy = randomPushStrategy()
//              ).awaitInf
//
//            val sleeping = state.shouldBeInstanceOf[LevelState.Sleeping]
//
//            sleeping.stateId shouldBe Int.MaxValue
//            //deadline should be greater than long sleep.
//            sleeping.sleepDeadline should be > expectedDeadline
//
//            level.segments() shouldBe segmentsBeforeCompaction
//        }
//      }
//    }
//
//    "there are multiple large files with no expired key-values" in {
//      runThis(10.times, log = true) {
//        CoreTestSweeper {
//          implicit sweeper =>
//            import sweeper._
//
//            val keyValues =
//              Slice.range(1, 6) mapToSlice {
//                key =>
//                  randomPutKeyValue(key, randomString(), someOrNone(1.hour.fromNow))
//              }
//
//            val level =
//              TestLevel(
//                keyValues = keyValues,
//                segmentConfig = SegmentBlockConfig.random2(deleteDelay = Duration.Zero, mmap = mmapSegments, minSegmentSize = 1.byte)
//              )
//            //get expected deadline early and expect the state's deadline to be greater than this
//            //to account for the time taken running this test
//            val expectedDeadline = level.nextCompactionDelay.fromNow
//
//            val segmentsBeforeCompaction = level.segments()
//            level.isEmpty shouldBe false
//            segmentsBeforeCompaction should have size keyValues.size
//
//            val state =
//              BehaviorWakeUp.compactLastLevel(
//                level = level,
//                stateId = Int.MaxValue,
//                pushStrategy = randomPushStrategy()
//              ).awaitInf
//
//            val sleeping = state.shouldBeInstanceOf[LevelState.Sleeping]
//
//            sleeping.stateId shouldBe Int.MaxValue
//            //deadline should be greater than long sleep.
//            sleeping.sleepDeadline should be > expectedDeadline
//
//            level.segments() shouldBe segmentsBeforeCompaction
//        }
//      }
//    }
//
//    "all segments are small but there are only 2 segments (minimum is 3)" in {
//      runThis(10.times, log = true) {
//        CoreTestSweeper {
//          implicit sweeper =>
//            import sweeper._
//
//            val keyValues =
//              Slice.range(1, 15) mapToSlice {
//                key =>
//                  randomPutKeyValue(key, randomString(), someOrNone(1.hour.fromNow))
//              }
//
//            val testSegments = keyValues.grouped(2).map(GenSegment(_)).toList
//
//            val level =
//              TestLevel(
//                segmentConfig = SegmentBlockConfig.random2(deleteDelay = Duration.Zero, mmap = mmapSegments, minSegmentSize = 100.mb)
//              )
//
//            testSegments.foreach(_.segmentSize should be < level.minSegmentSize)
//
//            level.commitPersisted(Seq(DefIO(Segment.Null, testSegments))) shouldBe IO.unit
//
//            //3 small segments are created
//            level.segments() should have size testSegments.size
//
//            //get expected deadline early and expect the state's deadline to be greater than this
//            //to account for the time taken running this test
//            val expectedDeadline = level.nextCompactionDelay.fromNow
//
//            val segmentsBeforeCompaction = level.segments()
//
//            val state =
//              BehaviorWakeUp.compactLastLevel(
//                level = level,
//                stateId = Int.MaxValue,
//                pushStrategy = randomPushStrategy()
//              ).awaitInf
//
//            val sleeping = state.shouldBeInstanceOf[LevelState.Sleeping]
//
//            sleeping.stateId shouldBe Int.MaxValue
//            //deadline should be greater than long sleep.
//            sleeping.sleepDeadline should be > expectedDeadline
//
//            level.segments() should have size testSegments.size
//            level.segments().flatMap(_.iterator(randomBoolean())) shouldBe keyValues
//
//            //segments are not deleted
//            testSegments.foreach(_.existsOnDiskOrMemory() shouldBe true)
//            level.segments() shouldBe segmentsBeforeCompaction
//        }
//      }
//    }
//  }
//
//  "run compaction" when {
//    "all segments are small" in {
//      runThis(10.times, log = true) {
//        CoreTestSweeper {
//          implicit sweeper =>
//            import sweeper._
//
//            val keyValues =
//              Slice.range(1, 15) mapToSlice {
//                key =>
//                  randomPutKeyValue(key, randomString(), someOrNone(1.hour.fromNow))
//              }
//
//            //create Segments
//            val testSegments = keyValues.grouped(3).map(GenSegment(_)).toList
//
//            //create level
//            val level =
//              TestLevel(
//                segmentConfig = SegmentBlockConfig.random2(deleteDelay = Duration.Zero, mmap = mmapSegments, minSegmentSize = 100.mb)
//              )
//
//            //all Segments are small
//            testSegments.foreach(_.segmentSize should be < level.minSegmentSize)
//
//            //commit the Segments to the Level
//            level.commitPersisted(Seq(DefIO(Segment.Null, testSegments))) shouldBe IO.unit
//
//            //3 small segments are created
//            level.segments() should have size testSegments.size
//
//            //get expected deadline early and expect the state's deadline to be greater than this
//            //to account for the time taken running this test
//            val expectedDeadline = level.nextCompactionDelay.fromNow
//
//            //run compaction
//            val state =
//              BehaviorWakeUp.compactLastLevel(
//                level = level,
//                stateId = Int.MaxValue,
//                pushStrategy = randomPushStrategy()
//              ).awaitInf
//
//            val sleeping = state.shouldBeInstanceOf[LevelState.Sleeping]
//
//            sleeping.stateId shouldBe Int.MaxValue
//            //deadline should be greater than long sleep.
//            sleeping.sleepDeadline should be > expectedDeadline
//
//            level.segments() should have size 1
//            level.segments().flatMap(_.iterator(randomBoolean())) shouldBe keyValues
//
//            //segments are deleted
//            testSegments.foreach(_.existsOnDiskOrMemory() shouldBe false)
//        }
//      }
//    }
//
//    "there are expired key-values" in {
//      runThis(10.times, log = true) {
//        CoreTestSweeper {
//          implicit sweeper =>
//            import sweeper._
//
//            val keyValues =
//              Slice.range(1, 15) mapToSlice {
//                key =>
//                  val deadline = if (key % 5 == 0) Some(expiredDeadline()) else None
//                  randomPutKeyValue(key, randomString(), deadline)
//              }
//
//            //create Segments
//            val testSegments = keyValues.mapToSlice(keyValue => GenSegment(Slice(keyValue))).toList
//
//            //create level
//            val level =
//              TestLevel(
//                segmentConfig = SegmentBlockConfig.random2(deleteDelay = Duration.Zero, mmap = mmapSegments, minSegmentSize = 100.mb)
//              )
//
//            //all Segments are small
//            testSegments.foreach(_.segmentSize should be < level.minSegmentSize)
//
//            //commit the Segments to the Level
//            level.commitPersisted(Seq(DefIO(Segment.Null, testSegments))) shouldBe IO.unit
//
//            //3 small segments are created
//            level.segments() should have size testSegments.size
//
//            //get expected deadline early and expect the state's deadline to be greater than this
//            //to account for the time taken running this test
//            val expectedDeadline = level.nextCompactionDelay.fromNow
//
//            //run compaction
//            val state =
//              BehaviorWakeUp.compactLastLevel(
//                level = level,
//                stateId = Int.MaxValue,
//                pushStrategy = randomPushStrategy()
//              ).awaitInf
//
//            val sleeping = state.shouldBeInstanceOf[LevelState.Sleeping]
//
//            sleeping.stateId shouldBe Int.MaxValue
//            //deadline should be greater than long sleep.
//            sleeping.sleepDeadline should be > expectedDeadline
//
//            //3 segments are deleted because their key-values are expired
//            level.segments() should have size (testSegments.size - 3)
//            level.segments().flatMap(_.iterator(randomBoolean())) shouldBe unexpiredPuts(keyValues)
//        }
//      }
//    }
//  }
//}
