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
//import swaydb.testkit.EitherValues._
//import swaydb.core.CoreTestData._
//import swaydb.core.level.zero.LevelZero.LevelZeroLog
//import swaydb.core.segment.data.{Memory, Time, Value}
//import swaydb.core.{ACoreSpec, CoreTestSweeper, TestTimer}
//import swaydb.core.log.ALogSpec
//import swaydb.serializers.Default._
//import swaydb.serializers._
//import swaydb.slice.{MaxKey, Slice}
//import swaydb.slice.order.{KeyOrder, TimeOrder}
//import swaydb.testkit.RunThis._
//
//import scala.jdk.CollectionConverters._
//import scala.util.Random
//
//class LevelZeroTaskAssigner_createStacks_Range_Spec extends ALogSpec {
//
//  implicit val timer = TestTimer.Empty
//  implicit val keyOrder = KeyOrder.default
//  implicit val timeOrder = TimeOrder.long
//
//  implicit def range(rangeKeys: (Int, Int)): Memory.Range =
//    Memory.Range(rangeKeys._1, rangeKeys._2, Value.FromValue.Null, Value.Update(Slice.Null, None, Time.empty))
//
//  implicit def fixed(key: Int): Memory.Put =
//    Memory.put(key)
//
//  /**
//   * The following test-cases are hard to describe in the test-case.
//   * See the key-values in the comments to view the test inputs.
//   */
//  def createStacks(keyValues: Slice[Memory]*)(test: scala.collection.Map[Slice[Byte], LevelZeroTaskAssigner.Stack] => Unit): Unit =
//    CoreTestSweeper {
//      implicit sweeper =>
//        val logs: Iterable[LevelZeroLog] = keyValues.map(GenLog(_))
//        val stacks = LevelZeroTaskAssigner.createStacks(logs)
//        test(stacks.asScala)
//    }
//
//  "1" in {
//    //1 - 10
//    //1 - 10
//    createStacks(
//      Slice((1, 10)),
//      Slice((1, 10))
//    ) {
//      stacks: scala.collection.Map[Slice[Byte], LevelZeroTaskAssigner.Stack] =>
//        stacks should have size 1
//        val (minKey, stack) = stacks.head
//
//        minKey shouldBe 1.serialise
//        stack.minKey shouldBe 1.serialise
//        stack.maxKey shouldBe MaxKey.Range[Slice[Byte]](1, 10)
//        stack.stack should have size 2
//        stack.stack.head.leftValue.cache.valuesIterator().toList should contain only range(1, 10)
//        stack.stack.last.rightValue should contain only range(1, 10)
//    }
//  }
//
//  "2" in {
//    //1   - 10
//    //  2 - 10
//    createStacks(
//      Slice((1, 10)),
//      Slice((2, 10))
//    ) {
//      stacks: scala.collection.Map[Slice[Byte], LevelZeroTaskAssigner.Stack] =>
//        stacks should have size 1
//        val (minKey, stack) = stacks.head
//
//        minKey shouldBe 1.serialise
//        stack.minKey shouldBe 1.serialise
//        stack.maxKey shouldBe MaxKey.Range[Slice[Byte]](2, 10)
//        stack.stack should have size 2
//        stack.stack.head.leftValue.cache.valuesIterator().toList should contain only range(1, 10)
//        stack.stack.last.rightValue should contain only range(2, 10)
//    }
//
//    //FLIPPED
//    //  2 - 10
//    //1   - 10
//    createStacks(
//      Slice((2, 10)),
//      Slice((1, 10))
//    ) {
//      stacks: scala.collection.Map[Slice[Byte], LevelZeroTaskAssigner.Stack] =>
//        stacks should have size 1
//        val (minKey, stack) = stacks.head
//
//        minKey shouldBe 1.serialise
//        stack.minKey shouldBe 1.serialise
//        stack.maxKey shouldBe MaxKey.Range[Slice[Byte]](1, 10)
//        stack.stack should have size 2
//        stack.stack.head.leftValue.cache.valuesIterator().toList should contain only range(2, 10)
//        stack.stack.last.rightValue should contain only range(1, 10)
//    }
//  }
//
//  "3" in {
//    //1   - 10
//    //  2 -    11
//    createStacks(
//      Slice((1, 10)),
//      Slice((2, 11))
//    ) {
//      stacks: scala.collection.Map[Slice[Byte], LevelZeroTaskAssigner.Stack] =>
//        stacks should have size 1
//        val (minKey, stack) = stacks.head
//
//        minKey shouldBe 1.serialise
//        stack.minKey shouldBe 1.serialise
//        stack.maxKey shouldBe MaxKey.Range[Slice[Byte]](2, 11)
//        stack.stack should have size 2
//        stack.stack.head.leftValue.cache.valuesIterator().toList should contain only range(1, 10)
//        stack.stack.last.rightValue should contain only range(2, 11)
//    }
//
//    //FLIPPED
//    //  2 -    11
//    //1   - 10
//    createStacks(
//      Slice((2, 11)),
//      Slice((1, 10))
//    ) {
//      stacks: scala.collection.Map[Slice[Byte], LevelZeroTaskAssigner.Stack] =>
//        stacks should have size 1
//        val (minKey, stack) = stacks.head
//
//        minKey shouldBe 1.serialise
//        stack.minKey shouldBe 1.serialise
//        stack.maxKey shouldBe MaxKey.Range[Slice[Byte]](2, 11)
//        stack.stack should have size 2
//        stack.stack.head.leftValue.cache.valuesIterator().toList should contain only range(2, 11)
//        stack.stack.last.rightValue should contain only range(1, 10)
//    }
//  }
//
//  "4" in {
//    runThis(10.times, log = true) {
//      //1   - 10
//      //      10  - 20
//      createStacks(
//        //in any order the result is always the same
//        Random.shuffle(
//          List(
//            Slice(range(1, 10)),
//            Slice(range(10, 20))
//          )
//        ): _*
//      ) {
//        stacks: scala.collection.Map[Slice[Byte], LevelZeroTaskAssigner.Stack] =>
//          stacks should have size 2
//
//          val (headKey, headValue) = stacks.head
//          headKey shouldBe 1.serialise
//          headValue.minKey shouldBe 1.serialise
//          headValue.maxKey shouldBe MaxKey.Range[Slice[Byte]](1, 10)
//          headValue.stack should have size 1
//          headValue.stack.head.leftValue.cache.valuesIterator().toList should contain only range(1, 10)
//
//          val (lastKey, lastValue) = stacks.last
//          lastKey shouldBe 10.serialise
//          lastValue.minKey shouldBe 10.serialise
//          lastValue.maxKey shouldBe MaxKey.Range[Slice[Byte]](10, 20)
//          lastValue.stack should have size 1
//          lastValue.stack.head.leftValue.cache.valuesIterator().toList should contain only range(10, 20)
//      }
//    }
//  }
//
//  "5 - all overlaps" in {
//    runThis(10.times, log = true) {
//      //      10   -   20
//      //1  -  10
//      //   5   -   15
//      createStacks(
//        Slice(range(10, 20)),
//        Slice(range(1, 10)),
//        Slice(range(5, 15))
//      ) {
//        stacks: scala.collection.Map[Slice[Byte], LevelZeroTaskAssigner.Stack] =>
//          stacks should have size 1
//
//          val (headKey, headValue) = stacks.head
//          headKey shouldBe 1.serialise
//          headValue.minKey shouldBe 1.serialise
//          headValue.maxKey shouldBe MaxKey.Range[Slice[Byte]](10, 20)
//          headValue.stack should have size 3
//          headValue.stack.head.leftValue.cache.valuesIterator().toList should contain only range(1, 10)
//          headValue.stack(1).leftValue.cache.valuesIterator().toList should contain only range(10, 20)
//          headValue.stack(2).rightValue should contain only range(5, 15)
//      }
//    }
//  }
//
//  "5 - total range overlaps" in {
//    runThis(10.times, log = true) {
//      //             20   -   30
//      //1  -  9
//      //         10
//      //                          40 - 50
//      //1                -             50 <------ OVERLAPS TOTAL
//      createStacks(
//        Slice(range(20, 30)),
//        Slice(range(1, 9)),
//        Slice(fixed(10)),
//        Slice(range(40, 50)),
//        Slice(range(1, 50))
//      ) {
//        stacks: scala.collection.Map[Slice[Byte], LevelZeroTaskAssigner.Stack] =>
//          stacks should have size 1
//
//          val (headKey, headValue) = stacks.head
//          headKey shouldBe 1.serialise
//          headValue.minKey shouldBe 1.serialise
//          headValue.maxKey shouldBe MaxKey.Range[Slice[Byte]](1, 50)
//          headValue.stack should have size 5
//          headValue.stack.head.leftValue.cache.valuesIterator().toList should contain only range(1, 9)
//          headValue.stack(1).leftValue.cache.valuesIterator().toList should contain only fixed(10)
//          headValue.stack(2).leftValue.cache.valuesIterator().toList should contain only range(20, 30)
//          headValue.stack(3).leftValue.cache.valuesIterator().toList should contain only range(40, 50)
//          headValue.stack(4).rightValue should contain only range(1, 50)
//      }
//    }
//  }
//
//  "5 - 1 fixed overlaps" in {
//    runThis(10.times, log = true) {
//      //             20   -   30
//      //1  -  9
//      //         10
//      //                          40 - 50
//      //                  25  <- only this overlaps
//      createStacks(
//        Slice(range(20, 30)),
//        Slice(range(1, 9)),
//        Slice(fixed(10)),
//        Slice(range(40, 50)),
//        Slice(fixed(25))
//      ) {
//        stacks: scala.collection.Map[Slice[Byte], LevelZeroTaskAssigner.Stack] =>
//          stacks should have size 4
//
//          val (headKey, headValue) = stacks.head
//          headKey shouldBe 1.serialise
//          headValue.minKey shouldBe 1.serialise
//          headValue.maxKey shouldBe MaxKey.Range[Slice[Byte]](1, 9)
//          headValue.stack should have size 1
//          headValue.stack.head.leftValue.cache.valuesIterator().toList should contain only range(1, 9)
//
//          val (secondKey, secondValue) = stacks.drop(1).head
//          secondKey shouldBe 10.serialise
//          secondValue.minKey shouldBe 10.serialise
//          secondValue.maxKey shouldBe MaxKey.Fixed[Slice[Byte]](10)
//          secondValue.stack should have size 1
//          secondValue.stack.head.leftValue.cache.valuesIterator().toList should contain only fixed(10)
//
//          val (thirdKey, thirdValue) = stacks.drop(2).head
//          thirdKey shouldBe 20.serialise
//          thirdValue.minKey shouldBe 20.serialise
//          thirdValue.maxKey shouldBe MaxKey.Range[Slice[Byte]](20, 30)
//          thirdValue.stack should have size 2
//          thirdValue.stack.head.leftValue.cache.valuesIterator().toList should contain only range(20, 30)
//          thirdValue.stack.last.rightValue.toList should contain only fixed(25)
//
//          val (lastKey, lastValue) = stacks.last
//          lastKey shouldBe 40.serialise
//          lastValue.minKey shouldBe 40.serialise
//          lastValue.maxKey shouldBe MaxKey.Range[Slice[Byte]](40, 50)
//          lastValue.stack should have size 1
//          lastValue.stack.head.leftValue.cache.valuesIterator().toList should contain only range(40, 50)
//      }
//    }
//  }
//
//  "5 - 1 fixed overlap and 1 range overlap" in {
//    runThis(10.times, log = true) {
//      //             20   -   30
//      //1  -  9
//      //         10
//      //                          40 - 50
//      //                  25  <- only this overlaps
//      //         10   -   25
//      createStacks(
//        Slice(range(20, 30)),
//        Slice(range(1, 9)),
//        Slice(fixed(10)),
//        Slice(range(40, 50)),
//        Slice(fixed(25)),
//        Slice(range(10, 25)),
//      ) {
//        stacks: scala.collection.Map[Slice[Byte], LevelZeroTaskAssigner.Stack] =>
//          stacks should have size 3
//
//          val (headKey, headValue) = stacks.head
//          headKey shouldBe 1.serialise
//          headValue.minKey shouldBe 1.serialise
//          headValue.maxKey shouldBe MaxKey.Range[Slice[Byte]](1, 9)
//          headValue.stack should have size 1
//          headValue.stack.head.leftValue.cache.valuesIterator().toList should contain only range(1, 9)
//
//          val (secondKey, secondValue) = stacks.drop(1).head
//          secondKey shouldBe 10.serialise
//          secondValue.minKey shouldBe 10.serialise
//          secondValue.maxKey shouldBe MaxKey.Range[Slice[Byte]](20, 30)
//          secondValue.stack should have size 4
//          secondValue.stack.head.leftValue.cache.valuesIterator().toList should contain only fixed(10)
//          secondValue.stack(1).leftValue.cache.valuesIterator().toList should contain only range(20, 30)
//          secondValue.stack(2).rightValue.toList should contain only fixed(25)
//          secondValue.stack(3).rightValue.toList should contain only range(10, 25)
//
//          val (lastKey, lastValue) = stacks.last
//          lastKey shouldBe 40.serialise
//          lastValue.minKey shouldBe 40.serialise
//          lastValue.maxKey shouldBe MaxKey.Range[Slice[Byte]](40, 50)
//          lastValue.stack should have size 1
//          lastValue.stack.head.leftValue.cache.valuesIterator().toList should contain only range(40, 50)
//      }
//    }
//  }
//
//  "6" in {
//    runThis(10.times, log = true) {
//      //      10        -        100
//      //1  -  10
//      //1  -      11
//      //          11   -    30
//      //                    30  -    101
//      //      10        -        100
//      //      10                     101
//      createStacks(
//        Slice(range(10, 100)),
//        Slice(range(1, 10)),
//        Slice(range(1, 11)),
//        Slice(range(11, 30)),
//        Slice(range(30, 101)),
//        Slice(range(10, 100)),
//        Slice(range(10, 101))
//      ) {
//        stacks: scala.collection.Map[Slice[Byte], LevelZeroTaskAssigner.Stack] =>
//          stacks should have size 1
//
//          val (headKey, headValue) = stacks.head
//          headKey shouldBe 1.serialise
//          headValue.minKey shouldBe 1.serialise
//          headValue.maxKey shouldBe MaxKey.Range[Slice[Byte]](10, 101)
//          headValue.stack should have size 7
//          headValue.stack.head.leftValue.cache.valuesIterator().toList should contain only range(1, 10)
//          headValue.stack(1).leftValue.cache.valuesIterator().toList should contain only range(10, 100)
//          headValue.stack(2).rightValue.toList should contain only range(1, 11)
//          headValue.stack(3).rightValue.toList should contain only range(11, 30)
//          headValue.stack(4).rightValue.toList should contain only range(30, 101)
//          headValue.stack(5).rightValue.toList should contain only range(10, 100)
//          headValue.stack(6).rightValue.toList should contain only range(10, 101)
//      }
//    }
//  }
//}
