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

package swaydb.core.segment.block.values

import org.scalatest.OptionValues._
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import swaydb.core.log.timer.TestTimer
import swaydb.core.segment.block.Block
import swaydb.core.segment.block.reader.{BlockedReader, BlockRefReader}
import swaydb.core.segment.block.SegmentBlockTestKit._
import swaydb.core.segment.data.KeyValueTestKit._
import swaydb.core.segment.data.Memory
import swaydb.core.segment.entry.writer.EntryWriter
import swaydb.core.segment.TestCoreFunctionStore
import swaydb.serializers._
import swaydb.serializers.Default._
import swaydb.slice.Slice
import swaydb.testkit.RunThis._
import swaydb.testkit.TestKit._

import scala.collection.mutable.ListBuffer
import scala.collection.parallel.CollectionConverters._
import scala.concurrent.duration._

class ValuesBlockSpec extends AnyWordSpec {

  implicit val timer: TestTimer = TestTimer.Empty
  private implicit val testFunctionStore: TestCoreFunctionStore = TestCoreFunctionStore()

  "init" should {
    "not initialise if values do not exists" in {
      runThis(100.times) {
        val keyValues =
          Slice(Memory.put(key = 1, value = Slice.emptyBytes, removeAfter = 10.seconds), Memory.remove(key = 1))
            .toPersistentMergeBuilder
            .close(randomBoolean(), randomBoolean())

        keyValues.keyValuesCount shouldBe 2
        keyValues.totalValuesSize shouldBe 0

        ValuesBlock.init(
          stats = keyValues,
          valuesConfig = ValuesBlockConfig.random,
          builder = EntryWriter.Builder(randomBoolean(), randomBoolean(), randomBoolean(), randomBoolean(), Slice.emptyBytes.asMut())
        ).isNoneS shouldBe true
      }
    }

    "initialise values exists" in {
      runThis(100.times) {
        val keyValues =
          Slice(Memory.put(key = 1, value = Slice.writeInt(1), removeAfter = 10.seconds), randomFixedTransientKeyValue(2, 3))
            .toPersistentMergeBuilder
            .close(randomBoolean(), randomBoolean())

        ValuesBlock.init(
          stats = keyValues,
          valuesConfig = ValuesBlockConfig.random,
          builder = EntryWriter.Builder(randomBoolean(), randomBoolean(), randomBoolean(), randomBoolean(), Slice.emptyBytes.asMut())
        ).isSomeS shouldBe true
      }
    }
  }

  "close" should {
    "prepare for persisting & concurrent read values" in {
      runThis(100.times, log = true) {
        val stats =
          randomizedKeyValues(count = randomIntMax(10000) max 1, valueSize = randomIntMax(100) max 1)
            .toPersistentMergeBuilder
            .close(randomBoolean(), randomBoolean())

        val state =
          ValuesBlock.init(
            stats = stats,
            valuesConfig = ValuesBlockConfig.random,
            builder = EntryWriter.Builder(randomBoolean(), randomBoolean(), randomBoolean(), randomBoolean(), Slice.emptyBytes.asMut())
          ).getS

        val keyValues = stats.keyValues

        keyValues foreach {
          keyValue =>
            ValuesBlock.write(
              keyValue = keyValue,
              state = state
            )
        }

        ValuesBlock.close(state)

        val ref = BlockRefReader[ValuesBlockOffset](state.blockBytes)
        val blocked = BlockedReader(ref.copy())

        val manuallyReadBlock = ValuesBlock.read(Block.readHeader[ValuesBlockOffset](ref.copy()))
        manuallyReadBlock.decompressionAction shouldBe blocked.block.decompressionAction
        manuallyReadBlock.offset shouldBe blocked.block.offset
        manuallyReadBlock.headerSize shouldBe blocked.block.headerSize

        val uncompressedUnblockedReader = Block.unblock(blocked, randomBoolean())
        val cachedUnblockedReader = ValuesBlock.unblockedReader(state)

        Seq(uncompressedUnblockedReader, cachedUnblockedReader) foreach {
          unblockedReader =>

            val keyValuesOffset = ListBuffer.empty[(Int, Slice[Byte])]

            keyValues.foldLeft(0) {
              case (offset, keyValue) =>
                val valueBytes = keyValue.value
                if (valueBytes.isNoneC) {
                  offset
                } else {
                  keyValuesOffset += ((offset, valueBytes.getC))
                  ValuesBlock.read(offset, valueBytes.getC.size, unblockedReader).value shouldBe valueBytes.getC
                  offset + valueBytes.getC.size
                }
            }

            //concurrent read values
            keyValuesOffset.par foreach {
              case (offset, value) =>
                ValuesBlock.read(offset, value.size, unblockedReader.copy()) should contain(value)
            }
        }
      }
    }
  }
}
