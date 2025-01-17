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
//import swaydb.core.CoreTestData._
//import swaydb.core.level.zero.LevelZero.LevelZeroLog
//import swaydb.core.{ACoreSpec, CoreTestSweeper, TestTimer}
//import swaydb.core.log.ALogSpec
//import swaydb.serializers.Default._
//import swaydb.serializers._
//import swaydb.slice.MaxKey
//import swaydb.slice.order.{KeyOrder, TimeOrder}
//import swaydb.testkit.RunThis._
//import swaydb.testkit.TestKit._
//
//import scala.collection.mutable.ListBuffer
//import scala.jdk.CollectionConverters._
//
//class LevelZeroTaskAssigner_createStacks_Spec extends ALogSpec {
//
//  implicit val timer = TestTimer.Empty
//  implicit val keyOrder = KeyOrder.default
//  implicit val timeOrder = TimeOrder.long
//
//  "input.size == 0" in {
//    LevelZeroTaskAssigner.createStacks(List.empty) shouldBe empty
//  }
//
//  "input.size = 1" in {
//    CoreTestSweeper {
//      implicit sweeper =>
//        val logs: List[LevelZeroLog] =
//          List(
//            GenLog(randomizedKeyValues(startId = Some(0)))
//          )
//
//        val stacks = LevelZeroTaskAssigner.createStacks(logs).asScala
//
//        stacks should have size 1
//        stacks.head._1 shouldBe 0.serialise
//        stacks.head._2.stack should contain only Left(logs.head)
//    }
//  }
//
//  "input.size = random but no overlaps" in {
//    runThis(20.times, log = true) {
//      CoreTestSweeper {
//        implicit sweeper =>
//          val logs: Iterable[LevelZeroLog] =
//            (0 to randomIntMax(10)).foldLeft(ListBuffer.empty[LevelZeroLog]) {
//              case (logs, _) =>
//                val startId =
//                  logs.lastOption match {
//                    case Some(last) =>
//                      last.cache.lastOptimised.getS.maxKey match {
//                        case MaxKey.Fixed(maxKey) =>
//                          maxKey.readInt() + 1
//
//                        case MaxKey.Range(_, maxKey) =>
//                          maxKey.readInt()
//                      }
//
//                    case None =>
//                      0
//                  }
//
//                logs += GenLog(randomizedKeyValues(startId = Some(startId)))
//            }
//
//          val stacks = LevelZeroTaskAssigner.createStacks(logs).asScala
//          stacks should have size logs.size
//          stacks.values.map(_.stack) shouldBe logs.map(map => ListBuffer(Left(map)))
//      }
//    }
//  }
//}
