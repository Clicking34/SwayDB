/*
 * Copyright 2018 Simer JS Plaha (simer.j@gmail.com - @simerplaha)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package swaydb.core.segment.assigner

import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import swaydb.core.{CoreSpecType, CoreTestSweeper}
import swaydb.core.log.timer.TestTimer
import swaydb.core.segment.{PersistentSegment, PersistentSegmentMany, PersistentSegmentOne, Segment, GenSegment}
import swaydb.core.segment.block.segment.SegmentBlockConfig
import swaydb.core.segment.data.KeyValueTestKit._
import swaydb.core.segment.io.SegmentReadIO
import swaydb.core.segment.SegmentTestKit._
import swaydb.core.segment.block.SegmentBlockTestKit._
import swaydb.slice.Slice
import swaydb.slice.order.KeyOrder
import swaydb.slice.SliceTestKit._
import swaydb.testkit.RunThis._
import swaydb.testkit.TestKit._
import swaydb.utils.PipeOps._

import scala.collection.mutable.ListBuffer

class SegmentAssigner_Assign_Spec extends AnyWordSpec {
  implicit val keyOrder: KeyOrder[Slice[Byte]] = KeyOrder.default
  implicit val testTimer: TestTimer = TestTimer.Empty
  implicit def segmentIO: SegmentReadIO = SegmentReadIO.random

  def keyValueCount: Int = 1000

  "assign new Segment to Segment" when {
    "both have the same key values" in {
      CoreTestSweeper.foreachRepeat(10.times, CoreSpecType.all) {
        (_sweeper, _specType) =>

          implicit val sweeper: CoreTestSweeper = _sweeper
          implicit val specType: CoreSpecType = _specType

          import sweeper.testCoreFunctionStore

          val segment = GenSegment.one(randomizedKeyValues())

          //assign the Segment's key-values to itself.
          /**
           * Test with no gaps
           */
          {
            val noGaps = Assigner.assignUnsafeNoGaps(Slice(segment), Slice(segment), randomBoolean())
            noGaps should have size 1
            val assignedSegments = noGaps.head.midOverlap.result.expectSegments()
            assignedSegments should have size 1
            assignedSegments.head shouldBe segment
            //just to reduce GC workload. Do not create gap instances when it's noGap is true.
            //even without null the output is still Nothing which is useless.
            noGaps.head.headGap shouldBe null
            noGaps.head.tailGap shouldBe null
          }

          /**
           * Test with gaps
           */
          {
            val gaps = Assigner.assignUnsafeGaps[ListBuffer[Assignable]](Slice(segment), Slice(segment), randomBoolean())
            gaps should have size 1
            val assignedSegments = gaps.head.midOverlap.result.expectSegments()
            assignedSegments.head shouldBe segment

            gaps.head.headGap.result shouldBe empty
            gaps.head.tailGap.result shouldBe empty
          }

          if (specType.isPersistent) {
            val segment = GenSegment.one(randomizedKeyValues()).asInstanceOf[PersistentSegment]

            val segmentRef =
              segment match {
                case one: PersistentSegmentOne =>
                  one.ref

                case got =>
                  fail(s"Expected ${PersistentSegmentOne.productPrefix} got ${got.getClass.getSimpleName}")
              }

            /**
             * Test with REFs
             */
            val noGaps = Assigner.assignUnsafeGaps[ListBuffer[Assignable]](Slice(segmentRef), Slice(segment), randomBoolean())
            noGaps should have size 1
            val assignedSegments = noGaps.head.midOverlap.result.expectSegmentRefs()
            assignedSegments.head.path shouldBe segmentRef.path
            assignedSegments.head.hashCode() shouldBe segmentRef.hashCode()

            noGaps.head.headGap.result shouldBe empty
            noGaps.head.tailGap.result shouldBe empty
          }
      }
    }

    "there are two Segments and all have same key-values" in {
      CoreTestSweeper.foreachRepeat(10.times, CoreSpecType.all) {
        (_sweeper, _specType) =>

          implicit val sweeper: CoreTestSweeper = _sweeper
          implicit val specType: CoreSpecType = _specType

          import sweeper.testCoreFunctionStore

          val keyValues = randomKeyValues(count = 1000, startId = Some(1)).groupedSlice(2)
          keyValues should have size 2

          val segment1 = GenSegment.one(keyValues.head)
          val segment2 = GenSegment.one(keyValues.last)

          //assign the Segment's key-values to itself.
          /**
           * Test with no gaps
           */
          Assigner.assignUnsafeNoGaps(Slice(segment1, segment2), Slice(segment1, segment2), randomBoolean()) ==> {
            noGaps =>
              //got two assignments
              noGaps should have size 2

              //first segment is segment1
              val assignment1 = noGaps.head
              val assignedSegmentsHead = assignment1.midOverlap.result.expectSegments()
              assignedSegmentsHead should have size 1
              assignedSegmentsHead.head shouldBe segment1
              assignment1.segment shouldBe segment1
              assignment1.headGap shouldBe null
              assignment1.tailGap shouldBe null

              val assignment2 = noGaps.last
              val assignedSegmentsLast = assignment2.midOverlap.result.expectSegments()
              assignedSegmentsLast should have size 1
              assignedSegmentsLast.head shouldBe segment2
              assignment2.segment shouldBe segment2
              assignment2.headGap shouldBe null
              assignment2.tailGap shouldBe null
          }

          /**
           * Test with gaps
           */
          Assigner.assignUnsafeGaps[ListBuffer[Assignable]](Slice(segment1, segment2), Slice(segment1, segment2), randomBoolean()) ==> {
            gaps =>
              gaps should have size 2

              //first segment is segment1
              val assignment1 = gaps.head
              val assignedSegmentsHead = assignment1.midOverlap.result.expectSegments()
              assignedSegmentsHead should have size 1
              assignedSegmentsHead.head shouldBe segment1
              assignment1.segment shouldBe segment1
              assignment1.headGap.result shouldBe empty
              assignment1.tailGap.result shouldBe empty

              val assignment2 = gaps.last
              val assignedSegmentsLast = assignment2.midOverlap.result.expectSegments()
              assignedSegmentsLast should have size 1
              assignedSegmentsLast.head shouldBe segment2
              assignment2.segment shouldBe segment2
              assignment2.headGap.result shouldBe empty
              assignment2.tailGap.result shouldBe empty
          }
      }
    }

    "input Segment expands to all Segment" in {
      CoreTestSweeper.foreachRepeat(10.times, CoreSpecType.all) {
        (_sweeper, _specType) =>

          implicit val sweeper: CoreTestSweeper = _sweeper
          implicit val specType: CoreSpecType = _specType

          import sweeper.testCoreFunctionStore

          val keyValues = randomKeyValues(count = 1000, startId = Some(1))
          val keyValuesGrouped = keyValues.groupedSlice(2)
          keyValuesGrouped should have size 2

          val inputSegment = GenSegment.one(keyValues)

          val segment1 = GenSegment.one(keyValuesGrouped.head)
          val segment2 = GenSegment.one(keyValuesGrouped.last)

          //assign the Segment's key-values to itself.
          /**
           * Test with no gaps
           */
          Assigner.assignUnsafeNoGaps(Slice(inputSegment), Slice(segment1, segment2), randomBoolean()) ==> {
            noGaps =>
              //got two assignments
              noGaps should have size 2

              //first segment is segment1
              val assignment1 = noGaps.head
              assignment1.midOverlap.result.expectKeyValues() shouldBe keyValuesGrouped.head
              assignment1.segment shouldBe segment1
              assignment1.headGap shouldBe null
              assignment1.tailGap shouldBe null

              val assignment2 = noGaps.last
              assignment2.midOverlap.result.expectKeyValues() shouldBe keyValuesGrouped.last
              assignment2.segment shouldBe segment2
              assignment2.headGap shouldBe null
              assignment2.tailGap shouldBe null
          }

          /**
           * Test with gaps
           */
          Assigner.assignUnsafeGaps[ListBuffer[Assignable]](Slice(inputSegment), Slice(segment1, segment2), randomBoolean()) ==> {
            gaps =>
              gaps should have size 2

              //first segment is segment1
              val assignment1 = gaps.head
              assignment1.midOverlap.result.expectKeyValues() shouldBe keyValuesGrouped.head
              assignment1.segment shouldBe segment1
              assignment1.headGap.result shouldBe empty
              assignment1.tailGap.result shouldBe empty

              val assignment2 = gaps.last
              assignment2.midOverlap.result.expectKeyValues() shouldBe keyValuesGrouped.last
              assignment2.segment shouldBe segment2
              assignment2.headGap.result shouldBe empty
              assignment2.tailGap.result shouldBe empty
          }
      }
    }

    "Segment belongs to next Segment" in {
      CoreTestSweeper.foreachRepeat(10.times, CoreSpecType.all) {
        (_sweeper, _specType) =>

          implicit val sweeper: CoreTestSweeper = _sweeper
          implicit val specType: CoreSpecType = _specType

          import sweeper.testCoreFunctionStore

          val keyValues = randomKeyValues(count = 1000, startId = Some(1)).groupedSlice(2)
          keyValues should have size 2

          val inputSegment = GenSegment.one(keyValues.last)

          val segment1 = GenSegment.one(keyValues.head)
          val segment2 = GenSegment.one(keyValues.last)

          //assign the Segment's key-values to itself.
          /**
           * Test with no gaps
           */
          Assigner.assignUnsafeNoGaps(Slice(inputSegment), Slice(segment1, segment2), randomBoolean()) ==> {
            noGaps =>
              noGaps should have size 1

              val assignment = noGaps.head
              val assignedSegments = assignment.midOverlap.result.expectSegments()
              assignedSegments should have size 1
              assignedSegments.head shouldBe inputSegment
              assignment.segment shouldBe segment2
              assignment.headGap shouldBe null
              assignment.tailGap shouldBe null
          }

          /**
           * Test with gaps
           */
          Assigner.assignUnsafeGaps[ListBuffer[Assignable]](Slice(inputSegment), Slice(segment1, segment2), randomBoolean()) ==> {
            gaps =>
              gaps should have size 1

              val assignment = gaps.head
              val assignedSegments = assignment.midOverlap.result.expectSegments()
              assignedSegments should have size 1
              assignedSegments.head shouldBe inputSegment
              assignment.segment shouldBe segment2
              assignment.headGap.result shouldBe empty
              assignment.tailGap.result shouldBe empty
          }
      }
    }

    "multiple input Segments have gaps" in {
      CoreTestSweeper.foreachRepeat(10.times, CoreSpecType.all) {
        (_sweeper, _specType) =>

          implicit val sweeper: CoreTestSweeper = _sweeper
          implicit val specType: CoreSpecType = _specType

          import sweeper.testCoreFunctionStore

          val keyValues = randomKeyValues(count = 1000, startId = Some(1)).groupedSlice(10)

          //create key-values with head and tail gap to be of 5
          val expectedAssignments =
            keyValues mapToSlice {
              keyValues =>
                val dropCount = 5
                val headGap = keyValues.take(dropCount)
                val tailGap = keyValues.takeRight(dropCount)
                val mid = keyValues.drop(dropCount).dropRight(dropCount)
                (headGap, mid, tailGap)
            }

          //create Segments with only mid key-values
          val segments =
            expectedAssignments mapToSlice {
              case (_, midKeyValues, _) =>
                GenSegment.one(midKeyValues)
            }

          val inputSegments =
            keyValues mapToSlice (GenSegment.one(_))

          //Expect full Segments to get assigned without expanding or assigning gaps.

          /**
           * Test with no gaps
           */
          Assigner.assignUnsafeNoGaps(inputSegments, segments, randomBoolean()) ==> {
            assignments =>
              assignments should have size segments.size

              assignments.zipWithIndex foreach {
                case (assignment, index) =>
                  assignment.segment shouldBe segments.get(index)
                  val assignedSegments = assignment.midOverlap.result.expectSegments()
                  assignedSegments should have size 1
                  assignedSegments.head shouldBe inputSegments.get(index)
                  assignment.headGap shouldBe null
                  assignment.tailGap shouldBe null
              }
          }

          /**
           * Test with gaps
           */
          Assigner.assignUnsafeGaps[ListBuffer[Assignable]](inputSegments, segments, randomBoolean()) ==> {
            assignments =>
              assignments should have size segments.size

              assignments.zipWithIndex foreach {
                case (assignment, index) =>
                  assignment.segment shouldBe segments.get(index)
                  val assignedSegments = assignment.midOverlap.result.expectSegments()
                  assignedSegments should have size 1
                  assignedSegments.head shouldBe inputSegments.get(index)
                  assignment.headGap.result shouldBe empty
                  assignment.tailGap.result shouldBe empty
              }
          }
      }
    }

    "multiple input Segments have gaps Segments" in {
      CoreTestSweeper.foreachRepeat(10.times, CoreSpecType.all) {
        (_sweeper, _specType) =>

          implicit val sweeper: CoreTestSweeper = _sweeper
          implicit val specType: CoreSpecType = _specType

          import sweeper.testCoreFunctionStore

          val keyValues = randomKeyValues(count = 100, startId = Some(1)).groupedSlice(10)

          //create key-values with head and tail gap to be of 5
          val expectedAssignments =
            keyValues mapToSlice {
              keyValues =>
                val dropCount = 2
                val headGap = keyValues.take(dropCount)
                val tailGap = keyValues.takeRight(dropCount)
                val mid = keyValues.drop(dropCount).dropRight(dropCount)
                (headGap, mid, tailGap)
            }

          val gapedSegment: Slice[(Segment, Segment, Segment)] =
            expectedAssignments mapToSlice {
              case (headGap, midKeyValues, tailGap) =>
                (GenSegment.one(headGap), GenSegment.one(midKeyValues), GenSegment.one(tailGap))
            }

          //input all Segments and gap Segments flattened
          val inputSegments =
            gapedSegment.flatMap[Segment] {
              case (headGap, midKeyValues, tailGap) =>
                Slice(headGap, midKeyValues, tailGap)
            }

          //existing Segments are just the midKeyValues
          val existingSegments =
            expectedAssignments mapToSlice {
              case (_, midKeyValues, _) =>
                GenSegment.one(midKeyValues)
            }

          /**
           * Test with no gaps
           */
          Assigner.assignUnsafeNoGaps(inputSegments, existingSegments, randomBoolean()) ==> {
            assignments =>
              assignments should have size existingSegments.size

              assignments.zipWithIndex foreach {
                case (assignment, index) =>
                  assignment.segment shouldBe existingSegments.get(index)
                  val assignedSegments = assignment.midOverlap.result.expectSegments()

                  if (index == 0) //first will get 2 assigned since tail gap get assigned to next Segment
                    assignedSegments should have size 2
                  else if (index == existingSegments.size - 1)
                    assignedSegments should have size 4 //last will have 4 assigned
                  else
                    assignedSegments should have size 3 //mid will have 3 segments assigned

                  assignment.headGap shouldBe null
                  assignment.tailGap shouldBe null
              }
          }

          /**
           * Test with gaps
           */
          Assigner.assignUnsafeGaps[ListBuffer[Assignable]](inputSegments, existingSegments, randomBoolean()) ==> {
            assignments =>
              assignments should have size existingSegments.size

              assignments.zipWithIndex foreach {
                case (assignment, index) =>
                  assignment.segment shouldBe existingSegments.get(index)
                  val assignedSegments = assignment.midOverlap.result.expectSegments()

                  assignedSegments should have size 1

                  (assignment.headGap.result ++ assignment.tailGap.result) should not be empty
              }
          }
      }
    }
  }

  /**
   * Tests handling [[PersistentSegmentMany]] and assigning of [[swaydb.core.segment.ref.SegmentRef]]s.
   */

  "PersistentSegmentMany" when {
    "head Segments do not overlap" in {
      CoreTestSweeper.repeat(10.times) {
        implicit sweeper =>
          import sweeper.testCoreFunctionStore

          implicit val specType: CoreSpecType = CoreSpecType.Persistent

          val keyValues = randomPutKeyValues(1000, startId = Some(0))
          val keyValuesGrouped = keyValues.groupedSlice(10)

          //[SEG-1, SEG-2, SEG-3, SEG-4, SEG-5]
          val inputSegment =
            GenSegment
              .many(keyValues = keyValuesGrouped.take(5).flatten, segmentConfig = SegmentBlockConfig.random.copy(mmap = mmapSegments, minSize = Int.MaxValue, maxCount = keyValues.size / keyValuesGrouped.size))
              .asInstanceOf[Slice[PersistentSegmentMany]]

          //[........., SEG-6, SEG-7, SEG-8, SEG-9, SEG-10]
          val existingSegment =
            GenSegment
              .many(keyValues = keyValuesGrouped.drop(5).flatten, segmentConfig = SegmentBlockConfig.random.copy(mmap = mmapSegments, minSize = Int.MaxValue, maxCount = keyValues.size / keyValuesGrouped.size))
              .asInstanceOf[Slice[PersistentSegmentMany]]

          inputSegment should have size 1
          existingSegment should have size 1

          val gaps = Assigner.assignUnsafeGaps[ListBuffer[Assignable]](inputSegment, existingSegment, randomBoolean())
          gaps should have size 1

          gaps.head.headGap.result should have size 1
          gaps.head.headGap.result.toSlice shouldBe inputSegment
          gaps.head.midOverlap.result shouldBe empty
          gaps.head.tailGap.result shouldBe empty
      }
    }

    "tail Segments do not overlap" in {
      CoreTestSweeper.repeat(10.times) {
        implicit sweeper =>
          import sweeper.testCoreFunctionStore

          implicit val specType: CoreSpecType = CoreSpecType.Persistent

          val keyValues = randomPutKeyValues(1000, startId = Some(0))
          val keyValuesGrouped = keyValues.groupedSlice(10)

          //[........., SEG-6, SEG-7, SEG-8, SEG-9, SEG-10]
          val inputSegment =
            GenSegment
              .many(keyValues = keyValuesGrouped.drop(5).flatten, segmentConfig = SegmentBlockConfig.random.copy(mmap = mmapSegments, minSize = Int.MaxValue, maxCount = keyValues.size / keyValuesGrouped.size))
              .asInstanceOf[Slice[PersistentSegmentMany]]

          //[SEG-1, SEG-2, SEG-3, SEG-4, SEG-5]
          val existingSegment =
            GenSegment
              .many(keyValues = keyValuesGrouped.take(5).flatten, segmentConfig = SegmentBlockConfig.random.copy(mmap = mmapSegments, minSize = Int.MaxValue, maxCount = keyValues.size / keyValuesGrouped.size))
              .asInstanceOf[Slice[PersistentSegmentMany]]

          inputSegment should have size 1
          existingSegment should have size 1

          val gaps = Assigner.assignUnsafeGaps[ListBuffer[Assignable]](inputSegment, existingSegment, randomBoolean())
          gaps should have size 1

          gaps.head.headGap.result shouldBe empty
          gaps.head.midOverlap.result shouldBe empty
          gaps.head.tailGap.result should have size 1
          gaps.head.tailGap.result.toSlice shouldBe inputSegment
      }
    }

    "head SegmentRef is a gap Segment" in {
      CoreTestSweeper.repeat(10.times) {
        implicit sweeper =>
          import sweeper.testCoreFunctionStore

          implicit val specType: CoreSpecType = CoreSpecType.Persistent

          val keyValues = randomPutKeyValues(1000, startId = Some(0))
          val keyValuesGrouped = keyValues.groupedSlice(10)

          //[SEG-1, SEG-2, SEG-3 ... SEG-10]
          val inputSegment =
            GenSegment
              .many(keyValues = keyValues, segmentConfig = SegmentBlockConfig.random.copy(mmap = mmapSegments, minSize = Int.MaxValue, maxCount = keyValues.size / keyValuesGrouped.size))
              .asInstanceOf[Slice[PersistentSegmentMany]]

          //[_____, SEG-2, SEG-3 ... SEG-10]
          val existingSegment =
            GenSegment
              .many(keyValues = keyValuesGrouped.dropHead().flatten, segmentConfig = SegmentBlockConfig.random.copy(mmap = mmapSegments, minSize = Int.MaxValue, maxCount = keyValues.size / keyValuesGrouped.size))
              .asInstanceOf[Slice[PersistentSegmentMany]]

          inputSegment should have size 1
          existingSegment should have size 1

          val gaps = Assigner.assignUnsafeGaps[ListBuffer[Assignable]](inputSegment, existingSegment, randomBoolean())
          gaps should have size 1

          gaps.head.headGap.result.expectSegmentRefs() should have size 1
          gaps.head.headGap.result.expectSegmentRefs() should contain only inputSegment.head.segmentRefs(randomBoolean()).toList.head
          gaps.head.midOverlap.result.expectSegmentRefs() shouldBe inputSegment.head.segmentRefs(randomBoolean()).toList.drop(1)
          gaps.head.tailGap.result shouldBe empty
      }
    }

    "last SegmentRef is a gap Segment" in {
      CoreTestSweeper.repeat(10.times) {
        implicit sweeper =>

          import sweeper.testCoreFunctionStore

          implicit val specType: CoreSpecType = CoreSpecType.Persistent

          val keyValues = randomPutKeyValues(1000, startId = Some(0))
          val keyValuesGrouped = keyValues.groupedSlice(10)

          //[SEG-1, SEG-2, SEG-3 ... SEG-10]
          val inputSegment =
            GenSegment
              .many(keyValues = keyValues, segmentConfig = SegmentBlockConfig.random.copy(mmap = mmapSegments, minSize = Int.MaxValue, maxCount = keyValues.size / keyValuesGrouped.size))
              .asInstanceOf[Slice[PersistentSegmentMany]]

          //[SEG-1, SEG-2, SEG-3 ... ______]
          val existingSegment =
            GenSegment
              .many(keyValues = keyValuesGrouped.dropRight(1).flatten, segmentConfig = SegmentBlockConfig.random.copy(mmap = mmapSegments, minSize = Int.MaxValue, maxCount = keyValues.size / keyValuesGrouped.size))
              .asInstanceOf[Slice[PersistentSegmentMany]]

          inputSegment should have size 1
          existingSegment should have size 1

          val gaps = Assigner.assignUnsafeGaps[ListBuffer[Assignable]](inputSegment, existingSegment, randomBoolean())
          gaps should have size 1

          gaps.head.headGap.result shouldBe empty
          gaps.head.midOverlap.result.expectSegmentRefs() shouldBe inputSegment.head.segmentRefs(randomBoolean()).toList.dropRight(1)
          gaps.head.tailGap.result.expectSegmentRefs() should have size 1
          gaps.head.tailGap.result.expectSegmentRefs() should contain only inputSegment.head.segmentRefs(randomBoolean()).toList.last
      }
    }

    "mid SegmentRefs are gap Segment and there is only one Segment" in {
      CoreTestSweeper.repeat(10.times) {
        implicit sweeper =>
          import sweeper.testCoreFunctionStore

          implicit val specType: CoreSpecType = CoreSpecType.Persistent

          val keyValues = randomPutKeyValues(1000, startId = Some(0))
          val keyValuesGrouped = keyValues.groupedSlice(10)

          //[SEG-1, SEG-2, GAP-3, GAP-4 ... SEG-10]
          val inputSegment =
            GenSegment
              .many(keyValues = keyValues, segmentConfig = SegmentBlockConfig.random.copy(mmap = mmapSegments, minSize = Int.MaxValue, maxCount = keyValues.size / keyValuesGrouped.size))
              .asInstanceOf[Slice[PersistentSegmentMany]]

          //[SEG-1, SEG-2, _____, _____ ... SEG-10]
          val existingKeyValues = (keyValuesGrouped.take(2) ++ keyValuesGrouped.drop(4)).flatten
          val existingSegment =
            GenSegment
              .many(keyValues = existingKeyValues, segmentConfig = SegmentBlockConfig.random.copy(mmap = mmapSegments, minSize = Int.MaxValue, maxCount = keyValues.size / keyValuesGrouped.size))
              .asInstanceOf[Slice[PersistentSegmentMany]]

          inputSegment should have size 1
          existingSegment should have size 1

          //all SegmentsRefs get assigned
          val gaps = Assigner.assignUnsafeGaps[ListBuffer[Assignable]](inputSegment, existingSegment, randomBoolean())
          gaps should have size 1

          gaps.head.headGap.result shouldBe empty
          gaps.head.midOverlap.result.expectSegmentRefs() shouldBe inputSegment.head.segmentRefs(randomBoolean()).toList
          gaps.head.tailGap.result shouldBe empty
      }
    }

    "mid SegmentRefs are gap Segment between two Segments" in {
      CoreTestSweeper.repeat(10.times) {
        implicit sweeper =>

          import sweeper.testCoreFunctionStore

          implicit val specType: CoreSpecType = CoreSpecType.Persistent

          val keyValues = randomPutKeyValues(1000, startId = Some(0))
          val keyValuesGrouped = keyValues.groupedSlice(10)

          //[SEG-1, SEG-2, GAP-3, GAP-4 ... SEG-10]
          val inputSegment =
            GenSegment
              .many(keyValues = keyValues, segmentConfig = SegmentBlockConfig.random.copy(mmap = mmapSegments, minSize = Int.MaxValue, maxCount = keyValues.size / keyValuesGrouped.size))
              .asInstanceOf[Slice[PersistentSegmentMany]]

          //[SEG-1, SEG-2]
          val existingSegment1 =
            GenSegment
              .many(keyValues = keyValuesGrouped.take(2).flatten, segmentConfig = SegmentBlockConfig.random.copy(mmap = mmapSegments, minSize = Int.MaxValue, maxCount = keyValues.size / keyValuesGrouped.size))
              .asInstanceOf[Slice[PersistentSegmentMany]]

          //[..................., SEG-5, SEG-6 .... SEG-10]
          val existingSegment2 =
            GenSegment
              .many(keyValues = keyValuesGrouped.drop(4).flatten, segmentConfig = SegmentBlockConfig.random.copy(mmap = mmapSegments, minSize = Int.MaxValue, maxCount = keyValues.size / keyValuesGrouped.size))
              .asInstanceOf[Slice[PersistentSegmentMany]]

          inputSegment should have size 1
          existingSegment1 should have size 1
          existingSegment2 should have size 1

          //all SegmentsRefs get assigned
          val gaps = Assigner.assignUnsafeGaps[ListBuffer[Assignable]](inputSegment, existingSegment1 ++ existingSegment2, randomBoolean())
          gaps should have size 2

          gaps.head.headGap.result shouldBe empty
          gaps.head.midOverlap.result.expectSegmentRefs() shouldBe inputSegment.head.segmentRefs(randomBoolean()).toList.take(2)
          gaps.head.tailGap.result.expectSegmentRefs() shouldBe inputSegment.head.segmentRefs(randomBoolean()).toList.drop(2).take(2)

          gaps.last.headGap.result shouldBe empty
          gaps.last.midOverlap.result.expectSegmentRefs() shouldBe inputSegment.head.segmentRefs(randomBoolean()).toList.drop(4)
          gaps.last.tailGap.result shouldBe empty
      }
    }

    "mid PersistentSegmentMany spreads onto next Segment" in {
      CoreTestSweeper.repeat(10.times) {
        implicit sweeper =>
          import sweeper.testCoreFunctionStore

          implicit val specType: CoreSpecType = CoreSpecType.Persistent

          val keyValues = randomPutKeyValues(100, startId = Some(0))
          val keyValuesGrouped = keyValues.groupedSlice(10)

          //also assign a key-value for existingSegment1 so that inputSegment2 gets expanded
          val inputKeyValues = keyValues.take(1)

          //[............, SEG-3, SEG-4, SEG-5, SEG-6, SEG-7] - gets
          val inputSegment1 =
            GenSegment
              .many(keyValues = keyValuesGrouped.drop(2).take(5).flatten, segmentConfig = SegmentBlockConfig.random.copy(mmap = mmapSegments, minSize = Int.MaxValue, maxCount = keyValues.size / keyValuesGrouped.size))
              .asInstanceOf[Slice[PersistentSegmentMany]]

          //[................................................, SEG-8, SEG-9, SEG-10]
          val inputSegment2 =
            GenSegment
              .many(keyValues = keyValuesGrouped.drop(7).flatten, segmentConfig = SegmentBlockConfig.random.copy(mmap = mmapSegments, minSize = Int.MaxValue, maxCount = keyValues.size / keyValuesGrouped.size))
              .asInstanceOf[Slice[PersistentSegmentMany]]

          //[SEG-1, SEG-2, _____, _____]
          val existingSegment1 =
            GenSegment
              .many(keyValues = keyValuesGrouped.take(2).flatten, segmentConfig = SegmentBlockConfig.random.copy(mmap = mmapSegments, minSize = Int.MaxValue, maxCount = keyValues.size / keyValuesGrouped.size))
              .asInstanceOf[Slice[PersistentSegmentMany]]

          //[.........................., SEG-5, SEG-6, .....]
          val existingSegment2 =
            GenSegment
              .many(keyValues = keyValuesGrouped.drop(4).take(2).flatten, segmentConfig = SegmentBlockConfig.random.copy(mmap = mmapSegments, minSize = Int.MaxValue, maxCount = keyValues.size / keyValuesGrouped.size))
              .asInstanceOf[Slice[PersistentSegmentMany]]

          inputSegment1 should have size 1
          inputSegment2 should have size 1
          existingSegment1 should have size 1
          existingSegment2 should have size 1

          //all SegmentsRefs get assigned
          val gaps = Assigner.assignUnsafeGaps[ListBuffer[Assignable]](inputKeyValues ++ inputSegment1 ++ inputSegment2, existingSegment1 ++ existingSegment2, randomBoolean())
          gaps should have size 2

          gaps.head.headGap.result shouldBe empty
          gaps.head.midOverlap.result.expectKeyValues() shouldBe inputKeyValues
          gaps.head.tailGap.result.expectSegmentRefs() shouldBe inputSegment1.head.segmentRefs(randomBoolean()).take(2).toList

          gaps.last.headGap.result shouldBe empty
          gaps.last.midOverlap.result.expectSegmentRefs() shouldBe inputSegment1.head.segmentRefs(randomBoolean()).drop(2).take(2).toList
          gaps.last.tailGap.result should have size 2 //one SegmentRef from inputSegment1 gets assigned and the entire PersistentSegmentMany (inputSegment2)
          gaps.last.tailGap.result.head shouldBe inputSegment1.head.segmentRefs(randomBoolean()).toList.last
          gaps.last.tailGap.result.last shouldBe inputSegment2.head
      }
    }
  }
}
