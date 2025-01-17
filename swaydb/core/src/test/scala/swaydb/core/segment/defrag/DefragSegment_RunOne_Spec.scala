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

package swaydb.core.segment.defrag

import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import swaydb.config.CoreConfigTestKit._
import swaydb.config.MMAP
import swaydb.core.{CoreSpecType, CoreTestSweeper}
import swaydb.core.log.timer.TestTimer
import swaydb.core.segment.{PersistentSegment, Segment, GenSegment}
import swaydb.core.segment.assigner.Assignable
import swaydb.core.segment.block.binarysearch.BinarySearchIndexBlockConfig
import swaydb.core.segment.block.bloomfilter.BloomFilterBlockConfig
import swaydb.core.segment.block.hashindex.HashIndexBlockConfig
import swaydb.core.segment.block.segment.SegmentBlockConfig
import swaydb.core.segment.block.sortedindex.SortedIndexBlockConfig
import swaydb.core.segment.block.values.ValuesBlockConfig
import swaydb.core.segment.data.{KeyValue, Memory, SegmentKeyOrders}
import swaydb.core.segment.data.merge.stats.MergeStats
import swaydb.core.segment.data.KeyValueTestKit._
import swaydb.core.segment.io.SegmentReadIO
import swaydb.core.segment.SegmentTestKit._
import swaydb.core.segment.block.SegmentBlockTestKit._
import swaydb.serializers._
import swaydb.serializers.Default._
import swaydb.slice.Slice
import swaydb.slice.order.{KeyOrder, TimeOrder}
import swaydb.testkit.RunThis._
import swaydb.testkit.TestKit._
import swaydb.TestExecutionContext

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext
import swaydb.effect.EffectTestKit._

class DefragSegment_RunOne_Spec extends AnyWordSpec {

  private implicit val ec: ExecutionContext = TestExecutionContext.executionContext
  private implicit val timer: TestTimer = TestTimer.Empty

  private implicit val keyOrder: KeyOrder[Slice[Byte]] = KeyOrder.default
  private implicit val segmentKeyOrders: SegmentKeyOrders = SegmentKeyOrders(keyOrder)
  private implicit val timerOrder: TimeOrder[Slice[Byte]] = TimeOrder.long
  private implicit def segmentReadIO: SegmentReadIO = SegmentReadIO.random
  private implicit val keyValueKeyOrder: Ordering[KeyValue] = keyOrder.on[KeyValue](_.key)

  "NO GAPS - no key-values to merge" should {
    "result in empty" in {
      runThis(20.times, log = true) {
        CoreTestSweeper {
          implicit sweeper =>
            import sweeper._

            //HEAD - EMPTY
            //MID  - EMPTY
            //GAP  - EMPTY

            //SEG  - [1 - 10]

            implicit val coreSpecType: CoreSpecType = CoreSpecType.random()
            implicit val valuesConfig: ValuesBlockConfig = ValuesBlockConfig.random
            implicit val sortedIndexConfig: SortedIndexBlockConfig = SortedIndexBlockConfig.random
            implicit val binarySearchIndexConfig: BinarySearchIndexBlockConfig = BinarySearchIndexBlockConfig.random
            implicit val hashIndexConfig: HashIndexBlockConfig = HashIndexBlockConfig.random
            implicit val bloomFilterConfig: BloomFilterBlockConfig = BloomFilterBlockConfig.random
            implicit val segmentConfig: SegmentBlockConfig = SegmentBlockConfig.random

            val segment = GenSegment(randomizedKeyValues())

            val mergeResult =
              DefragPersistentSegment.runOnSegment(
                segment = segment,
                nullSegment = Segment.Null,
                headGap = ListBuffer.empty,
                tailGap = ListBuffer.empty,
                newKeyValues = Iterator.empty,
                removeDeletes = false,
                createdInLevel = 1,
                pathDistributor = sweeper.pathDistributor,
                segmentRefCacheLife = randomSegmentRefCacheLife(),
                mmap = MMAP.randomForSegment()
              ).await

            mergeResult.input shouldBe Segment.Null
            mergeResult.output.isEmpty shouldBe true
        }
      }
    }
  }

  "NO GAPS - Segment gets merged into itself own key-values" in {
    runThis(20.times, log = true) {
      CoreTestSweeper {
        implicit sweeper =>
          import sweeper._

          //HEAD - EMPTY
          //MID  - [1 - 10]
          //GAP  - EMPTY

          //SEG  - [1 - 10]

          implicit val coreSpecType: CoreSpecType = CoreSpecType.random()
          implicit val valuesConfig: ValuesBlockConfig = ValuesBlockConfig.random
          implicit val sortedIndexConfig: SortedIndexBlockConfig = SortedIndexBlockConfig.random
          implicit val binarySearchIndexConfig: BinarySearchIndexBlockConfig = BinarySearchIndexBlockConfig.random
          implicit val hashIndexConfig: HashIndexBlockConfig = HashIndexBlockConfig.random
          implicit val bloomFilterConfig: BloomFilterBlockConfig = BloomFilterBlockConfig.random
          implicit val segmentConfig: SegmentBlockConfig = SegmentBlockConfig.random

          val segment = GenSegment(randomizedKeyValues())

          val mergeResult =
            DefragPersistentSegment.runOnSegment(
              segment = segment,
              nullSegment = Segment.Null,
              headGap = ListBuffer.empty,
              tailGap = ListBuffer.empty,
              newKeyValues = segment.iterator(randomBoolean()),
              removeDeletes = false,
              createdInLevel = 1,
              pathDistributor = sweeper.pathDistributor,
              segmentRefCacheLife = randomSegmentRefCacheLife(),
              mmap = MMAP.randomForSegment()
            ).await

          mergeResult.input shouldBe segment
          mergeResult.output should have size 1

          mergeResult.output.flatMap(_.iterator(randomBoolean())) shouldBe segment.iterator(randomBoolean())
      }
    }
  }

  "Segment gets merged into itself and removeDeletes = true" in {
    runThis(20.times, log = true) {
      CoreTestSweeper {
        implicit sweeper =>
          import sweeper._

          //all key-values are removable so it doesn't matter if it contains gaps or not all key-values should get cleared.

          //HEAD - EMPTY | [1,2,3]
          //MID  - [4,5,6]
          //GAP  - EMPTY | [7,8,9]

          //SEG  - [4,5,6]

          implicit val coreSpecType: CoreSpecType = CoreSpecType.random()
          implicit val valuesConfig: ValuesBlockConfig = ValuesBlockConfig.random
          implicit val sortedIndexConfig: SortedIndexBlockConfig = SortedIndexBlockConfig.random
          implicit val binarySearchIndexConfig: BinarySearchIndexBlockConfig = BinarySearchIndexBlockConfig.random
          implicit val hashIndexConfig: HashIndexBlockConfig = HashIndexBlockConfig.random
          implicit val bloomFilterConfig: BloomFilterBlockConfig = BloomFilterBlockConfig.random
          implicit val segmentConfig: SegmentBlockConfig = SegmentBlockConfig.random

          lazy val headSegment = GenSegment(keyValues = Slice(randomUpdateKeyValue(1), randomRemoveAny(2, 3)))
          val midSegment = GenSegment(keyValues = Slice(randomUpdateKeyValue(4), randomRemoveAny(5, 6)))
          lazy val tailSegment = GenSegment(keyValues = Slice(randomUpdateKeyValue(7), randomRemoveAny(8, 9)))

          val headGap: ListBuffer[Assignable.Gap[MergeStats.Persistent.Builder[Memory, ListBuffer]]] =
            eitherOne(ListBuffer.empty, ListBuffer(headSegment))

          val tailGap: ListBuffer[Assignable.Gap[MergeStats.Persistent.Builder[Memory, ListBuffer]]] =
            eitherOne(ListBuffer.empty, ListBuffer(tailSegment))

          val newKeyValues =
            eitherOne(
              Iterator.empty,
              midSegment.iterator(randomBoolean())
            )

          val mergeResult =
            DefragPersistentSegment.runOnSegment(
              segment = midSegment,
              nullSegment = Segment.Null,
              headGap = headGap,
              tailGap = tailGap,
              newKeyValues = newKeyValues,
              removeDeletes = true,
              createdInLevel = 1,
              pathDistributor = sweeper.pathDistributor,
              segmentRefCacheLife = randomSegmentRefCacheLife(),
              mmap = MMAP.randomForSegment()
            ).await

          mergeResult.input shouldBe midSegment
          mergeResult.output.isEmpty shouldBe true
      }
    }
  }

  "HEAD GAP only" in {
    runThis(20.times, log = true) {
      CoreTestSweeper {
        implicit sweeper =>
          import sweeper._
          //all key-values are removable so it doesn't matter if it contains gaps or not, all key-values should get cleared.

          //HEAD - [0 - 49]
          //MID  - EMPTY
          //GAP  - EMPTY

          //SEG  - [50 - 99]

          implicit val coreSpecType: CoreSpecType = CoreSpecType.random()
          implicit val valuesConfig: ValuesBlockConfig = ValuesBlockConfig.random
          implicit val sortedIndexConfig: SortedIndexBlockConfig = SortedIndexBlockConfig.random
          implicit val binarySearchIndexConfig: BinarySearchIndexBlockConfig = BinarySearchIndexBlockConfig.random
          implicit val hashIndexConfig: HashIndexBlockConfig = HashIndexBlockConfig.random
          implicit val bloomFilterConfig: BloomFilterBlockConfig = BloomFilterBlockConfig.random

          val keyValues = randomPutKeyValues(100, startId = Some(0)).groupedSlice(2)
          keyValues should have size 2

          val headSegment = GenSegment.one(keyValues = keyValues.head).shouldBeInstanceOf[PersistentSegment]
          val midSegment = GenSegment.one(keyValues = keyValues.last)

          implicit val segmentConfig: SegmentBlockConfig = SegmentBlockConfig.random.copy(minSize = headSegment.segmentSize min midSegment.segmentSize)

          val removeDeletes = randomBoolean()
          val createdInLevel = randomIntMax(100)

          val mergeResult =
            DefragPersistentSegment.runOnSegment(
              segment = midSegment,
              nullSegment = Segment.Null,
              headGap = ListBuffer(headSegment),
              tailGap = ListBuffer.empty,
              newKeyValues = Iterator.empty,
              removeDeletes = removeDeletes,
              createdInLevel = createdInLevel,
              pathDistributor = sweeper.pathDistributor,
              segmentRefCacheLife = randomSegmentRefCacheLife(),
              mmap = MMAP.randomForSegment()
            ).awaitInf

          mergeResult.input shouldBe Segment.Null
          mergeResult.output should have size 1
          mergeResult.output.head.segmentSize shouldBe headSegment.segmentSize
          mergeResult.output.head.createdInLevel shouldBe headSegment.createdInLevel
          mergeResult.output.head.iterator(randomBoolean()).toList shouldBe headSegment.iterator(randomBoolean())
      }
    }
  }

  "MULTIPLE HEAD GAPs" in {
    runThis(20.times, log = true) {
      CoreTestSweeper {
        implicit sweeper =>
          import sweeper._
          //all key-values are removable so it doesn't matter if it contains gaps or not all key-values should get cleared.

          //HEAD - [0 - 10, 11 - 20, 21 - 30, 31 - 40, 41 - 50]
          //MID  - EMPTY
          //GAP  - EMPTY

          //SEG  - [51 - 99]

          implicit val coreSpecType: CoreSpecType = CoreSpecType.random()
          implicit val valuesConfig: ValuesBlockConfig = ValuesBlockConfig.random
          implicit val sortedIndexConfig: SortedIndexBlockConfig = SortedIndexBlockConfig.random
          implicit val binarySearchIndexConfig: BinarySearchIndexBlockConfig = BinarySearchIndexBlockConfig.random
          implicit val hashIndexConfig: HashIndexBlockConfig = HashIndexBlockConfig.random
          implicit val bloomFilterConfig: BloomFilterBlockConfig = BloomFilterBlockConfig.random

          val keyValues = randomPutKeyValues(100, startId = Some(0)).groupedSlice(10)
          keyValues should have size 10

          val headSegments: Slice[Segment] =
            keyValues.take(5) mapToSlice {
              keyValues =>
                GenSegment.one(keyValues = keyValues)
            }

          headSegments should have size 5

          val midSegment = GenSegment.one(keyValues = keyValues.drop(headSegments.size).flatten)

          val minSize = headSegments.mapToSlice(_.segmentSize).min min midSegment.segmentSize
          implicit val segmentConfig: SegmentBlockConfig = SegmentBlockConfig.random.copy(minSize = minSize)

          val removeDeletes = randomBoolean()
          val createdInLevel = randomIntMax(100)

          val mergeResult =
            DefragPersistentSegment.runOnSegment(
              segment = midSegment,
              nullSegment = Segment.Null,
              headGap = ListBuffer.from(headSegments),
              tailGap = ListBuffer.empty,
              newKeyValues = Iterator.empty,
              removeDeletes = removeDeletes,
              createdInLevel = createdInLevel,
              pathDistributor = sweeper.pathDistributor,
              segmentRefCacheLife = randomSegmentRefCacheLife(),
              mmap = MMAP.randomForSegment()
            ).await

          mergeResult.input shouldBe Segment.Null

          mergeResult.output.flatMap(_.iterator(randomBoolean())).toList shouldBe headSegments.flatMap(_.iterator(randomBoolean()))
          mergeResult.output.foreach(_.clearAllCaches())
          mergeResult.output.flatMap(_.iterator(randomBoolean())).toList shouldBe headSegments.flatMap(_.iterator(randomBoolean())).toList
      }
    }
  }

  "MULTIPLE HEAD and TAIL GAPs" in {
    runThis(20.times, log = true) {
      CoreTestSweeper {
        implicit sweeper =>
          import sweeper._

          //all key-values are removable so it doesn't matter if it contains gaps or not all key-values should get cleared.

          //HEAD - [0 - 10, 11 - 20, 21 - 30, 31 - 40, 41 - 50]
          //MID  - EMPTY
          //GAP  - [71 - 80, 81 - 90]

          //SEG  - [51 - 60, 61 - 70]

          implicit val coreSpecType: CoreSpecType = CoreSpecType.random()
          implicit val valuesConfig: ValuesBlockConfig = ValuesBlockConfig.random
          implicit val sortedIndexConfig: SortedIndexBlockConfig = SortedIndexBlockConfig.random
          implicit val binarySearchIndexConfig: BinarySearchIndexBlockConfig = BinarySearchIndexBlockConfig.random
          implicit val hashIndexConfig: HashIndexBlockConfig = HashIndexBlockConfig.random
          implicit val bloomFilterConfig: BloomFilterBlockConfig = BloomFilterBlockConfig.random

          val keyValues = randomPutKeyValues(100, startId = Some(0)).groupedSlice(10)
          keyValues should have size 10

          val headSegments: Slice[Segment] =
            keyValues.take(5) mapToSlice {
              keyValues =>
                GenSegment.one(keyValues = keyValues)
            }

          headSegments should have size 5

          val midSegment = GenSegment.one(keyValues = keyValues.drop(headSegments.size).take(2).flatten)

          val tailSegments: Slice[Segment] =
            keyValues.drop(7) mapToSlice {
              keyValues =>
                GenSegment.one(keyValues = keyValues)
            }

          val minSize = (headSegments ++ tailSegments).mapToSlice(_.segmentSize).min min midSegment.segmentSize
          implicit val segmentConfig: SegmentBlockConfig = SegmentBlockConfig.random.copy(minSize = minSize)

          val removeDeletes = randomBoolean()
          val createdInLevel = randomIntMax(100)

          val mergeResult =
            DefragPersistentSegment.runOnSegment(
              segment = midSegment,
              nullSegment = Segment.Null,
              headGap = ListBuffer.from(headSegments),
              tailGap = ListBuffer.from(tailSegments),
              newKeyValues = Iterator.empty,
              removeDeletes = removeDeletes,
              createdInLevel = createdInLevel,
              pathDistributor = sweeper.pathDistributor,
              segmentRefCacheLife = randomSegmentRefCacheLife(),
              mmap = MMAP.randomForSegment()
            ).await

          mergeResult.input shouldBe Segment.Null

          mergeResult.output.flatMap(_.iterator(randomBoolean())).toList shouldBe (keyValues.take(5) ++ keyValues.drop(7)).flatten
          mergeResult.output.foreach(_.clearAllCaches())
          mergeResult.output.flatMap(_.iterator(randomBoolean())).toList shouldBe (keyValues.take(5) ++ keyValues.drop(7)).flatten
      }
    }
  }

  "MULTIPLE HEAD and TAIL GAPs - that does not open mid segment" in {
    runThis(20.times, log = true) {
      CoreTestSweeper {
        implicit sweeper =>
          import sweeper._

          //HEAD - [0 - 10, 11 - 20, 21 - 30, 31 - 40, 41 - 50]
          //MID  - EMPTY
          //GAP  - [71 - 80, 81 - 90]

          //SEG  - [51 - 60, 61 - 70]

          implicit val coreSpecType: CoreSpecType = CoreSpecType.random()
          implicit val valuesConfig: ValuesBlockConfig = ValuesBlockConfig.random
          implicit val sortedIndexConfig: SortedIndexBlockConfig = SortedIndexBlockConfig.random
          implicit val binarySearchIndexConfig: BinarySearchIndexBlockConfig = BinarySearchIndexBlockConfig.random
          implicit val hashIndexConfig: HashIndexBlockConfig = HashIndexBlockConfig.random
          implicit val bloomFilterConfig: BloomFilterBlockConfig = BloomFilterBlockConfig.random

          val keyValues = randomPutKeyValues(100, startId = Some(0)).groupedSlice(10)
          keyValues should have size 10

          val headSegments: Slice[Segment] =
            keyValues.take(5) mapToSlice {
              keyValues =>
                GenSegment.one(keyValues = keyValues)
            }

          headSegments should have size 5

          val midSegment = GenSegment.one(keyValues = keyValues.drop(headSegments.size).take(2).flatten)

          val tailSegments: Slice[Segment] =
            keyValues.drop(7) mapToSlice {
              keyValues =>
                GenSegment.one(keyValues = keyValues)
            }

          //segmentSize is always <= midSegment.segmentSize so that it does not get expanded. This test is to ensure
          //that gap Segments do not join when midSegments are not expanded.
          implicit val segmentConfig: SegmentBlockConfig = SegmentBlockConfig.random.copy(minSize = randomIntMax(midSegment.segmentSize))

          val removeDeletes = randomBoolean()
          val createdInLevel = randomIntMax(100)

          val mergeResult =
            DefragPersistentSegment.runOnSegment(
              segment = midSegment,
              nullSegment = Segment.Null,
              headGap = ListBuffer.from(headSegments),
              tailGap = ListBuffer.from(tailSegments),
              newKeyValues = Iterator.empty,
              removeDeletes = removeDeletes,
              createdInLevel = createdInLevel,
              pathDistributor = sweeper.pathDistributor,
              segmentRefCacheLife = randomSegmentRefCacheLife(),
              mmap = MMAP.randomForSegment()
            ).awaitInf

          mergeResult.input shouldBe Segment.Null

          mergeResult.output.exists(_.minKey == tailSegments.head.minKey) shouldBe true

          //there exists a Segment without tailSegment's minKey.
          mergeResult.output.exists(_.minKey == tailSegments.head.minKey) shouldBe true

          mergeResult.output.flatMap(_.iterator(randomBoolean())).toList shouldBe (keyValues.take(5) ++ keyValues.drop(7)).flatten
          mergeResult.output.foreach(_.clearAllCaches())
          mergeResult.output.flatMap(_.iterator(randomBoolean())).toList shouldBe (keyValues.take(5) ++ keyValues.drop(7)).flatten
      }
    }
  }

  "MULTIPLE HEAD and TAIL GAPs - which might open mid segment" in {
    runThis(20.times, log = true) {
      CoreTestSweeper {
        implicit sweeper =>
          import sweeper._

          //HEAD - [0 - 10, 11 - 20, 21 - 30, 31 - 40, 41 - 50]
          //MID  - EMPTY
          //GAP  - [71 - 80, 81 - 90]

          //SEG  - [51 - 60, 61 - 70]

          implicit val coreSpecType: CoreSpecType = CoreSpecType.random()
          implicit val valuesConfig: ValuesBlockConfig = ValuesBlockConfig.random
          implicit val sortedIndexConfig: SortedIndexBlockConfig = SortedIndexBlockConfig.random
          implicit val binarySearchIndexConfig: BinarySearchIndexBlockConfig = BinarySearchIndexBlockConfig.random
          implicit val hashIndexConfig: HashIndexBlockConfig = HashIndexBlockConfig.random
          implicit val bloomFilterConfig: BloomFilterBlockConfig = BloomFilterBlockConfig.random

          val keyValues = randomPutKeyValues(100, startId = Some(0)).groupedSlice(10)
          keyValues should have size 10

          val headSegments: Slice[Segment] =
            keyValues.take(5) mapToSlice {
              keyValues =>
                GenSegment.one(keyValues = keyValues)
            }

          headSegments should have size 5

          val midSegment = GenSegment.one(keyValues = keyValues.drop(headSegments.size).take(2).flatten)

          val tailSegments: Slice[Segment] =
            keyValues.drop(7) mapToSlice {
              keyValues =>
                GenSegment.one(keyValues = keyValues)
            }

          val allSegments = headSegments ++ Seq(midSegment) ++ tailSegments

          //segmentSize to be something random which gives it a chance
          implicit val segmentConfig: SegmentBlockConfig = SegmentBlockConfig.random.copy(minSize = randomIntMax(allSegments.map(_.segmentSize).max * 2))

          val removeDeletes = randomBoolean()
          val createdInLevel = randomIntMax(100)

          val mergeResult =
            DefragPersistentSegment.runOnSegment(
              segment = midSegment,
              nullSegment = Segment.Null,
              headGap = ListBuffer.from(headSegments),
              tailGap = ListBuffer.from(tailSegments),
              newKeyValues = Iterator.empty,
              removeDeletes = removeDeletes,
              createdInLevel = createdInLevel,
              pathDistributor = sweeper.pathDistributor,
              segmentRefCacheLife = randomSegmentRefCacheLife(),
              mmap = MMAP.randomForSegment()
            ).awaitInf

          val expectedKeyValues =
            mergeResult.input match {
              case Segment.Null =>
                //segment was not replaced so only gap key-values exists.
                println(s"Segment: Segment.Null")
                (keyValues.take(5) ++ keyValues.drop(7)).flatten

              case segment: Segment =>
                //if segment was replaced and all key-values are expected in final segments.
                println(s"Segment: Segment.${segment.getClass.getSimpleName}")
                keyValues.flatten
            }

          mergeResult.output.flatMap(_.iterator(randomBoolean())).toList shouldBe expectedKeyValues
          mergeResult.output.foreach(_.clearAllCaches())
          mergeResult.output.flatMap(_.iterator(randomBoolean())).toList shouldBe expectedKeyValues
      }
    }
  }
}
