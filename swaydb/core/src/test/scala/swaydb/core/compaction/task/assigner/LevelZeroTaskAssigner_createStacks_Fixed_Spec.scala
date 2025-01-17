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
//import swaydb.core.segment.data.Memory
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
//class LevelZeroTaskAssigner_createStacks_Fixed_Spec extends ALogSpec {
//
//  implicit val timer = TestTimer.Empty
//  implicit val keyOrder = KeyOrder.default
//  implicit val timeOrder = TimeOrder.long
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
//    //1
//    //1
//    createStacks(
//      Slice(Memory.put(1, 2)),
//      Slice(Memory.put(1, 1))
//    ) {
//      stacks: scala.collection.Map[Slice[Byte], LevelZeroTaskAssigner.Stack] =>
//        stacks should have size 1
//        val (key, value) = stacks.head
//
//        key shouldBe 1.serialise
//        value.stack should have size 2
//        value.stack.head.leftValue.cache.valuesIterator().toList should contain only Memory.put(1, 2)
//        value.stack.last.rightValue should contain only Memory.put(1, 1)
//    }
//  }
//
//  "2" in {
//    runThis(10.times, log = true) {
//      //1
//      //   2
//      createStacks(
//        //result is always the same in any order
//        Random.shuffle(
//          List(
//            Slice(Memory.put(1, 1)),
//            Slice(Memory.put(2, 2))
//          )
//        ): _*
//      ) {
//        stacks: scala.collection.Map[Slice[Byte], LevelZeroTaskAssigner.Stack] =>
//          stacks should have size 2
//
//          val (headKey, headValue) = stacks.head
//          headKey shouldBe 1.serialise
//          headValue.minKey shouldBe 1.serialise
//          headValue.maxKey shouldBe MaxKey.Fixed(1.serialise)
//          headValue.stack should have size 1
//          headValue.stack.head.leftValue.cache.valuesIterator().toList should contain only Memory.put(1, 1)
//
//          val (lastKey, lastValue) = stacks.last
//          lastKey shouldBe 2.serialise
//          lastValue.minKey shouldBe 2.serialise
//          lastValue.maxKey shouldBe MaxKey.Fixed(2.serialise)
//          lastValue.stack should have size 1
//          lastValue.stack.head.leftValue.cache.valuesIterator().toList should contain only Memory.put(2, 2)
//      }
//    }
//  }
//
//  "3" in {
//    runThis(10.times, log = true) {
//      //      10 15 20 30
//      //1 2 3
//      createStacks(
//        //result is always the same in any order
//        Random.shuffle(
//          List(
//            Slice(Memory.put(10), Memory.put(15), Memory.put(20), Memory.put(30)),
//            Slice(Memory.put(1), Memory.put(2), Memory.put(3))
//          )
//        ): _*
//      ) {
//        stacks: scala.collection.Map[Slice[Byte], LevelZeroTaskAssigner.Stack] =>
//          stacks should have size 2
//
//          val (headKey, headValue) = stacks.head
//          headKey shouldBe 1.serialise
//          headValue.minKey shouldBe 1.serialise
//          headValue.maxKey shouldBe MaxKey.Fixed(3.serialise)
//          headValue.stack should have size 1
//          headValue.stack.head.leftValue.cache.valuesIterator().toList should contain only(Memory.put(1), Memory.put(2), Memory.put(3))
//
//          val (lastKey, lastValue) = stacks.last
//          lastKey shouldBe 10.serialise
//          lastValue.minKey shouldBe 10.serialise
//          lastValue.maxKey shouldBe MaxKey.Fixed(30.serialise)
//          lastValue.stack should have size 1
//          lastValue.stack.head.leftValue.cache.valuesIterator().toList should contain only(Memory.put(10), Memory.put(15), Memory.put(20), Memory.put(30))
//      }
//    }
//  }
//
//  "4" in {
//    runThis(10.times, log = true) {
//      //same test as above but now there is an overlap on key 10. So only one map entry is created with both maps in the stack for merging.
//      //      10 15 20 30
//      //1 2 3 10
//      createStacks(
//        Slice(Memory.put(10), Memory.put(15), Memory.put(20), Memory.put(30)),
//        Slice(Memory.put(1), Memory.put(2), Memory.put(3), Memory.put(10))
//      ) {
//        stacks: scala.collection.Map[Slice[Byte], LevelZeroTaskAssigner.Stack] =>
//          stacks should have size 1
//
//          val (headKey, headValue) = stacks.head
//          headKey shouldBe 1.serialise
//          headValue.minKey shouldBe 1.serialise
//          headValue.maxKey shouldBe MaxKey.Fixed(30.serialise)
//          headValue.stack should have size 2
//          headValue.stack.head.leftValue.cache.valuesIterator().toList should contain only(Memory.put(10), Memory.put(15), Memory.put(20), Memory.put(30))
//          headValue.stack.last.rightValue.toList should contain only(Memory.put(1), Memory.put(2), Memory.put(3), Memory.put(10))
//      }
//
//      //FLIPPED
//      //1 2 3 10
//      //      10 15 20 30
//      createStacks(
//        Slice(Memory.put(1), Memory.put(2), Memory.put(3), Memory.put(10)),
//        Slice(Memory.put(10), Memory.put(15), Memory.put(20), Memory.put(30))
//      ) {
//        stacks: scala.collection.Map[Slice[Byte], LevelZeroTaskAssigner.Stack] =>
//          stacks should have size 1
//
//          val (headKey, headValue) = stacks.head
//          headKey shouldBe 1.serialise
//          headValue.minKey shouldBe 1.serialise
//          headValue.maxKey shouldBe MaxKey.Fixed(30.serialise)
//          headValue.stack should have size 2
//          headValue.stack.head.leftValue.cache.valuesIterator().toList should contain only(Memory.put(1), Memory.put(2), Memory.put(3), Memory.put(10))
//          headValue.stack.last.rightValue.toList should contain only(Memory.put(10), Memory.put(15), Memory.put(20), Memory.put(30))
//      }
//    }
//  }
//
//  "5" in {
//    runThis(10.times, log = true) {
//      //    10 15 20 30
//      // 8 9             31
//      //      14
//      //             30
//      //                     49
//      createStacks(
//        Slice(Memory.put(10), Memory.put(15), Memory.put(20), Memory.put(30)),
//        Slice(Memory.put(8), Memory.put(9), Memory.put(31)),
//        Slice(Memory.put(14)),
//        Slice(Memory.put(30)),
//        Slice(Memory.put(49))
//      ) {
//        stacks: scala.collection.Map[Slice[Byte], LevelZeroTaskAssigner.Stack] =>
//          stacks should have size 2
//
//          val (headKey, headValue) = stacks.head
//          headKey shouldBe 8.serialise
//          headValue.minKey shouldBe 8.serialise
//          headValue.maxKey shouldBe MaxKey.Fixed(31.serialise)
//          headValue.stack should have size 4
//          headValue.stack.head.leftValue.cache.valuesIterator().toList should contain only(Memory.put(10), Memory.put(15), Memory.put(20), Memory.put(30))
//          headValue.stack(1).rightValue.toList should contain only(Memory.put(8), Memory.put(9), Memory.put(31))
//          headValue.stack(2).rightValue.toList should contain only Memory.put(14)
//          headValue.stack(3).rightValue.toList should contain only Memory.put(30)
//
//          val (lastKey, lastValue) = stacks.last
//          lastKey shouldBe 49.serialise
//          lastValue.minKey shouldBe 49.serialise
//          lastValue.maxKey shouldBe MaxKey.Fixed(49.serialise)
//          lastValue.stack should have size 1
//          lastValue.stack.head.leftValue.cache.valuesIterator().toList should contain only Memory.put(49)
//      }
//    }
//  }
//
//  "6" in {
//    runThis(10.times, log = true) {
//      // 1 2 3 4 5 6
//      // 1 2 3
//      //       4 5 6
//      createStacks(
//        Slice(Memory.put(1), Memory.put(2), Memory.put(3), Memory.put(4), Memory.put(5), Memory.put(6)),
//        Slice(Memory.put(1), Memory.put(2), Memory.put(3)),
//        Slice(Memory.put(4), Memory.put(5), Memory.put(6))
//      ) {
//        stacks: scala.collection.Map[Slice[Byte], LevelZeroTaskAssigner.Stack] =>
//          stacks should have size 1
//
//          val (headKey, headValue) = stacks.head
//          headKey shouldBe 1.serialise
//          headValue.minKey shouldBe 1.serialise
//          headValue.maxKey shouldBe MaxKey.Fixed(6.serialise)
//          headValue.stack should have size 3
//          headValue.stack.head.leftValue.cache.valuesIterator().toList should contain only(Memory.put(1), Memory.put(2), Memory.put(3), Memory.put(4), Memory.put(5), Memory.put(6))
//          headValue.stack(1).rightValue.toList should contain only(Memory.put(1), Memory.put(2), Memory.put(3))
//          headValue.stack(2).rightValue.toList should contain only(Memory.put(4), Memory.put(5), Memory.put(6))
//
//      }
//    }
//  }
//
//  "7 - 2 overlaps" in {
//    runThis(10.times, log = true) {
//      // 1 2 3
//      //       4 5 6
//      // 1 2 3 4 5 6
//      createStacks(
//        Slice(Memory.put(1), Memory.put(2), Memory.put(3)),
//        Slice(Memory.put(4), Memory.put(5), Memory.put(6)),
//        Slice(Memory.put(1), Memory.put(2), Memory.put(3), Memory.put(4), Memory.put(5), Memory.put(6))
//      ) {
//        stacks: scala.collection.Map[Slice[Byte], LevelZeroTaskAssigner.Stack] =>
//          stacks should have size 2
//
//          val (headKey, headValue) = stacks.head
//          headKey shouldBe 1.serialise
//          headValue.minKey shouldBe 1.serialise
//          headValue.maxKey shouldBe MaxKey.Fixed(3.serialise)
//          headValue.stack should have size 2
//          headValue.stack.head.leftValue.cache.valuesIterator().toList should contain only(Memory.put(1), Memory.put(2), Memory.put(3))
//          headValue.stack(1).rightValue.toList should contain only(Memory.put(1), Memory.put(2), Memory.put(3))
//
//          val (lastKey, lastValue) = stacks.last
//          lastKey shouldBe 4.serialise
//          lastValue.minKey shouldBe 4.serialise
//          lastValue.maxKey shouldBe MaxKey.Fixed(6.serialise)
//          lastValue.stack should have size 2
//          lastValue.stack.head.leftValue.cache.valuesIterator().toList should contain only(Memory.put(4), Memory.put(5), Memory.put(6))
//          lastValue.stack(1).rightValue.toList should contain only(Memory.put(4), Memory.put(5), Memory.put(6))
//
//      }
//    }
//  }
//
//  "7 - 4 overlaps" in {
//    runThis(10.times, log = true) {
//      // 1 2 3
//      //       4 5 6
//      //             7 8 9
//      //                   10 11 12
//      // 1 2 3 4 5 6 7 8 9 10 11 12
//      createStacks(
//        Slice.range(1, 3).mapToSlice(key => Memory.put(key)),
//        Slice.range(4, 6).mapToSlice(key => Memory.put(key)),
//        Slice.range(7, 9).mapToSlice(key => Memory.put(key)),
//        Slice.range(10, 12).mapToSlice(key => Memory.put(key)),
//        Slice.range(1, 12).mapToSlice(key => Memory.put(key)),
//      ) {
//        stacksMap: scala.collection.Map[Slice[Byte], LevelZeroTaskAssigner.Stack] =>
//          val stacks = stacksMap.toList
//          stacks should have size 4
//
//          val (headKey, headValue) = stacks.head
//          headKey shouldBe 1.serialise
//          headValue.minKey shouldBe 1.serialise
//          headValue.maxKey shouldBe MaxKey.Fixed(3.serialise)
//          headValue.stack should have size 2
//          headValue.stack.head.leftValue.cache.valuesIterator().toList should contain theSameElementsInOrderAs Slice.range(1, 3).mapToSlice(key => Memory.put(key))
//          headValue.stack(1).rightValue.toList should contain theSameElementsInOrderAs Slice.range(1, 3).mapToSlice(key => Memory.put(key))
//
//          val (secondKey, secondValue) = stacks.drop(1).head
//          secondKey shouldBe 4.serialise
//          secondValue.minKey shouldBe 4.serialise
//          secondValue.maxKey shouldBe MaxKey.Fixed(6.serialise)
//          secondValue.stack should have size 2
//          secondValue.stack.head.leftValue.cache.valuesIterator().toList should contain theSameElementsInOrderAs Slice.range(4, 6).mapToSlice(key => Memory.put(key))
//          secondValue.stack(1).rightValue.toList should contain theSameElementsInOrderAs Slice.range(4, 6).mapToSlice(key => Memory.put(key))
//
//          val (thirdKey, thirdValue) = stacks.drop(2).head
//          thirdKey shouldBe 7.serialise
//          thirdValue.minKey shouldBe 7.serialise
//          thirdValue.maxKey shouldBe MaxKey.Fixed(9.serialise)
//          thirdValue.stack should have size 2
//          thirdValue.stack.head.leftValue.cache.valuesIterator().toList should contain theSameElementsInOrderAs Slice.range(7, 9).mapToSlice(key => Memory.put(key))
//          thirdValue.stack(1).rightValue.toList should contain theSameElementsInOrderAs Slice.range(7, 9).mapToSlice(key => Memory.put(key))
//
//          val (lastKey, lastValue) = stacks.last
//          lastKey shouldBe 10.serialise
//          lastValue.minKey shouldBe 10.serialise
//          lastValue.maxKey shouldBe MaxKey.Fixed(12.serialise)
//          lastValue.stack should have size 2
//          lastValue.stack.head.leftValue.cache.valuesIterator().toList should contain theSameElementsInOrderAs Slice.range(10, 12).mapToSlice(key => Memory.put(key))
//          lastValue.stack(1).rightValue.toList should contain theSameElementsInOrderAs Slice.range(10, 12).mapToSlice(key => Memory.put(key))
//      }
//    }
//  }
//
//  "7 - 4 overlaps - flipped" in {
//    runThis(10.times, log = true) {
//      // 1 2 3 4 5 6 7 8 9 10 11 12
//      // 1 2 3
//      //       4 5 6
//      //             7 8 9
//      //                   10 11 12
//      createStacks(
//        Slice.range(1, 12).mapToSlice(key => Memory.put(key)),
//        Slice.range(1, 3).mapToSlice(key => Memory.put(key)),
//        Slice.range(4, 6).mapToSlice(key => Memory.put(key)),
//        Slice.range(7, 9).mapToSlice(key => Memory.put(key)),
//        Slice.range(10, 12).mapToSlice(key => Memory.put(key))
//      ) {
//        stacksMap: scala.collection.Map[Slice[Byte], LevelZeroTaskAssigner.Stack] =>
//          val stacks = stacksMap.toList
//          stacks should have size 1
//
//          val (headKey, headValue) = stacks.head
//          headKey shouldBe 1.serialise
//          headValue.minKey shouldBe 1.serialise
//          headValue.maxKey shouldBe MaxKey.Fixed(12.serialise)
//          headValue.stack should have size 5
//          headValue.stack.head.leftValue.cache.valuesIterator().toList should contain theSameElementsInOrderAs Slice.range(1, 12).mapToSlice(key => Memory.put(key))
//          headValue.stack(1).rightValue.toList should contain theSameElementsInOrderAs Slice.range(1, 3).mapToSlice(key => Memory.put(key))
//          headValue.stack(2).rightValue.toList should contain theSameElementsInOrderAs Slice.range(4, 6).mapToSlice(key => Memory.put(key))
//          headValue.stack(3).rightValue.toList should contain theSameElementsInOrderAs Slice.range(7, 9).mapToSlice(key => Memory.put(key))
//          headValue.stack(4).rightValue.toList should contain theSameElementsInOrderAs Slice.range(10, 12).mapToSlice(key => Memory.put(key))
//
//      }
//    }
//  }
//
//  "8 - fan out" in {
//    //fan out test to assert that min and max keys are updated appropriately when all maps are conflicting.
//    runThis(10.times, log = true) {
//      //     5 6
//      //   4     7
//      //  3       8
//      // 2         9
//      createStacks(
//        Slice(Memory.put(5), Memory.put(6)),
//        Slice(Memory.put(4), Memory.put(7)),
//        Slice(Memory.put(3), Memory.put(8)),
//        Slice(Memory.put(2), Memory.put(9)),
//      ) {
//        stacks: scala.collection.Map[Slice[Byte], LevelZeroTaskAssigner.Stack] =>
//          stacks should have size 1
//
//          val (headKey, headValue) = stacks.head
//          headKey shouldBe 2.serialise
//          headValue.minKey shouldBe 2.serialise
//          headValue.maxKey shouldBe MaxKey.Fixed(9.serialise)
//          headValue.stack should have size 4
//          headValue.stack.head.leftValue.cache.valuesIterator().toList should contain only(Memory.put(5), Memory.put(6))
//          headValue.stack(1).rightValue.toList should contain only(Memory.put(4), Memory.put(7))
//          headValue.stack(2).rightValue.toList should contain only(Memory.put(3), Memory.put(8))
//          headValue.stack(3).rightValue.toList should contain only(Memory.put(2), Memory.put(9))
//      }
//    }
//  }
//
//  "8 - fan in" in {
//    //fan out test to assert that min and max keys are updated appropriately when all maps are conflicting.
//    runThis(10.times, log = true) {
//      // 2         9
//      //  3       8
//      //   4     7
//      //     5 6
//      createStacks(
//        Slice(Memory.put(2), Memory.put(9)),
//        Slice(Memory.put(3), Memory.put(8)),
//        Slice(Memory.put(4), Memory.put(7)),
//        Slice(Memory.put(5), Memory.put(6)),
//      ) {
//        stacks: scala.collection.Map[Slice[Byte], LevelZeroTaskAssigner.Stack] =>
//          stacks should have size 1
//
//          val (headKey, headValue) = stacks.head
//          headKey shouldBe 2.serialise
//          headValue.minKey shouldBe 2.serialise
//          headValue.maxKey shouldBe MaxKey.Fixed(9.serialise)
//          headValue.stack should have size 4
//          headValue.stack.head.leftValue.cache.valuesIterator().toList should contain only(Memory.put(2), Memory.put(9))
//          headValue.stack(1).rightValue.toList should contain only(Memory.put(3), Memory.put(8))
//          headValue.stack(2).rightValue.toList should contain only(Memory.put(4), Memory.put(7))
//          headValue.stack(3).rightValue.toList should contain only(Memory.put(5), Memory.put(6))
//      }
//    }
//  }
//}
