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
//package swaydb.core.compaction.task.assigner
//
//import org.scalamock.scalatest.MockFactory
//import org.scalatest.OptionValues._
//import swaydb.core.CommonAssertions._
//import swaydb.core.CoreTestData._
//import swaydb.core.level.zero.LevelZero.LevelZeroLog
//import swaydb.core.segment.Segment
//import swaydb.core.segment.data.Memory
//import swaydb.core.{ACoreSpec, CoreTestSweeper, TestExecutionContext, TestTimer}
//import swaydb.core.log.ALogSpec
//import swaydb.serializers.Default._
//import swaydb.serializers._
//import swaydb.slice.Slice
//import swaydb.slice.order.{KeyOrder, TimeOrder}
//import swaydb.testkit.RunThis._
//import swaydb.testkit.TestKit._
//
//class LevelZeroTaskAssigner_mergeStack_Spec extends ALogSpec {
//
//  implicit val timer = TestTimer.Empty
//  implicit val keyOrder = KeyOrder.default
//  implicit val timeOrder = TimeOrder.long
//  implicit val segmentOrdering = keyOrder.on[Segment](_.minKey)
//  implicit val ec = TestExecutionContext.executionContext
//
//  "stack is empty" in {
//    CoreTestSweeper {
//      implicit sweeper =>
//        val stack: Iterable[Either[LevelZeroLog, Iterable[Memory]]] =
//          List.empty
//
//        LevelZeroTaskAssigner.mergeStack(stack).awaitInf shouldBe Iterable.empty
//    }
//  }
//
//  "stack.size == 1" in {
//    runThis(10.times, log = true) {
//      CoreTestSweeper {
//        implicit sweeper =>
//          val keyValue = Memory.put(1, 1)
//
//          val stack: Iterable[Either[LevelZeroLog, Iterable[Memory]]] =
//            List(
//              eitherOne(
//                Left(GenLog(Slice(keyValue))),
//                Right(Slice(keyValue))
//              )
//            )
//
//          LevelZeroTaskAssigner.mergeStack(stack).awaitInf should contain only keyValue
//
//      }
//    }
//  }
//
//  "stack.size == 2" in {
//    runThis(10.times, log = true) {
//      CoreTestSweeper {
//        implicit sweeper =>
//
//          val stack: Iterable[Either[LevelZeroLog, Iterable[Memory]]] =
//            List(
//              Left(GenLog(Slice(Memory.put(1, 2)))),
//              Right(Slice(Memory.put(1, 1)))
//            )
//
//          LevelZeroTaskAssigner.mergeStack(stack).awaitInf should contain only Memory.put(1, 2)
//
//      }
//    }
//  }
//
//  "stack.size == 3" in {
//    runThis(10.times, log = true) {
//      CoreTestSweeper {
//        implicit sweeper =>
//
//          val stack: Iterable[Either[LevelZeroLog, Iterable[Memory]]] =
//            List(
//              Left(GenLog(Slice(Memory.put(1, 2)))),
//              Right(Slice(Memory.put(1, 1))),
//              Right(Slice(Memory.put(1, 1), Memory.put(2, 2)))
//            )
//
//          LevelZeroTaskAssigner.mergeStack(stack).awaitInf should contain only(Memory.put(1, 2), Memory.put(2, 2))
//      }
//    }
//  }
//
//
//  "random size" in {
//    runThis(100.times, log = true) {
//      CoreTestSweeper {
//        implicit sweeper =>
//
//          val stackedKeyValues =
//            (1 to (randomIntMax(10) max 1)) map {
//              _ =>
//                //ignore pendingApply and ranges so that the order of merge is not important for the outcome.
//                randomizedKeyValues(startId = Some(0), count = randomIntMax(100) max 1, addPendingApply = false, addRanges = false, addFunctions = false)
//            }
//
//          val expected =
//            stackedKeyValues.foldRight(Option.empty[Slice[Memory]]) {
//              case (newerKeyValues, mergedKeyValues) =>
//                mergedKeyValues match {
//                  case Some(olderKeyValues) =>
//                    Some(
//                      merge(
//                        newKeyValues = newerKeyValues,
//                        oldKeyValues = olderKeyValues,
//                        isLastLevel = false
//                      ).toSlice
//                    )
//
//                  case None =>
//                    Some(newerKeyValues)
//                }
//            }.value
//
//          val stack: Iterable[Either[LevelZeroLog, Iterable[Memory]]] =
//            stackedKeyValues map {
//              keyValues =>
//                eitherOne(
//                  left = Left(GenLog(keyValues)),
//                  right = Right(keyValues)
//                )
//            }
//
//          val actual = LevelZeroTaskAssigner.mergeStack(stack).awaitInf
//
//          actual shouldBe expected
//      }
//    }
//  }
//}
