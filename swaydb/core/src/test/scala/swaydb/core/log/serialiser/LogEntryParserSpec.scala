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

package swaydb.core.log.serialiser


import org.scalatest.OptionValues._
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import swaydb.Error.Log.ExceptionHandler
import swaydb.core.log.timer.TestTimer
import swaydb.core.segment.data._
import swaydb.core.segment.data.KeyValueTestKit._
import swaydb.core.segment.io.SegmentReadIO
import swaydb.core.segment.SegmentTestKit._
import swaydb.core.segment.TestCoreFunctionStore
import swaydb.core.skiplist.SkipListConcurrent
import swaydb.effect.IOValues._
import swaydb.serializers._
import swaydb.serializers.Default._
import swaydb.slice.{Slice, SliceOption}
import swaydb.slice.order.{KeyOrder, TimeOrder}


class LogEntryParserSpec extends AnyWordSpec {

  implicit val keyOrder: KeyOrder[Slice[Byte]] = KeyOrder.default
  implicit def testTimer: TestTimer = TestTimer.Empty
  implicit val timeOrder: TimeOrder[Slice[Byte]] = TimeOrder.long
  implicit def segmentIO: SegmentReadIO = SegmentReadIO.random

  "MemoryMapCodec" should {
    "write and read empty bytes" in {
      import MemoryLogEntryWriter.LogEntryPutWriter
      val map = SkipListConcurrent[SliceOption[Byte], MemoryOption, Slice[Byte], Memory](Slice.Null, Memory.Null)(keyOrder)
      val bytes = LogEntryParser.write(map.iterator)
      bytes.isFull shouldBe true

      import MemoryLogEntryReader.KeyValueLogEntryPutReader
      LogEntryParser.read[Slice[Byte], Memory](bytes, dropCorruptedTailEntries = false).runRandomIO.get.item shouldBe empty
    }

    "write and read key values" in {
      implicit val testFunctionStore: TestCoreFunctionStore = TestCoreFunctionStore()
      val keyValues = randomKeyValues(1000, addRemoves = true)

      val map = SkipListConcurrent[SliceOption[Byte], MemoryOption, Slice[Byte], Memory](Slice.Null, Memory.Null)(keyOrder)
      keyValues foreach {
        keyValue =>
          map.put(keyValue.key, Memory.put(keyValue.key, keyValue.getOrFetchValue()))
      }

      import MemoryLogEntryWriter.LogEntryPutWriter
      val bytes = LogEntryParser.write(map.iterator)
      bytes.isFull shouldBe true

      //re-read the bytes written to map and it should contain all the original entries
      import MemoryLogEntryReader.KeyValueLogEntryPutReader
      val readMap = SkipListConcurrent[SliceOption[Byte], MemoryOption, Slice[Byte], Memory](Slice.Null, Memory.Null)(keyOrder)
      LogEntryParser.read[Slice[Byte], Memory](bytes, dropCorruptedTailEntries = false).runRandomIO.get.item.value applyBatch readMap
      keyValues foreach {
        keyValue =>
          val value = readMap.get(keyValue.key)
          value.getUnsafe shouldBe Memory.put(keyValue.key, keyValue.getOrFetchValue())
      }
    }

    "read bytes to map and ignore empty written byte(s)" in {
      implicit val testFunctionStore: TestCoreFunctionStore = TestCoreFunctionStore()

      val keyValues = randomKeyValues(1000, addRemoves = true)

      val map = SkipListConcurrent[SliceOption[Byte], MemoryOption, Slice[Byte], Memory](Slice.Null, Memory.Null)(keyOrder)
      keyValues foreach {
        keyValue =>
          map.put(keyValue.key, Memory.put(keyValue.key, keyValue.getOrFetchValue()))
      }

      def assertBytes(bytesWithEmpty: Slice[Byte]) = {
        //re-read the bytes written to map and it should contain all the original entries
        import MemoryLogEntryReader.KeyValueLogEntryPutReader
        val readMap = SkipListConcurrent[SliceOption[Byte], MemoryOption, Slice[Byte], Memory](Slice.Null, Memory.Null)(keyOrder)
        LogEntryParser.read(bytesWithEmpty, dropCorruptedTailEntries = false).runRandomIO.get.item.value applyBatch readMap
        keyValues foreach {
          keyValue =>
            val value = readMap.get(keyValue.key)
            value.getUnsafe shouldBe Memory.put(keyValue.key, keyValue.getOrFetchValue())
        }
      }

      import MemoryLogEntryWriter.LogEntryPutWriter
      //first write creates bytes that have no empty bytes
      val bytes = LogEntryParser.write(map.iterator)
      bytes.isFull shouldBe true

      //1 empty byte.
      val bytesWith1EmptyByte = Slice.allocate[Byte](bytes.size + 1)
      bytes foreach bytesWith1EmptyByte.add
      bytesWith1EmptyByte.isFull shouldBe false
      assertBytes(bytesWith1EmptyByte)

      //10 empty bytes. This is to check that key-values are still read if there are enough bytes to create a CRC
      val bytesWith20EmptyByte = Slice.allocate[Byte](bytes.size + 10)
      bytes foreach bytesWith20EmptyByte.add
      bytesWith20EmptyByte.isFull shouldBe false
      assertBytes(bytesWith20EmptyByte)
    }

    "only skip entries that are do not pass the CRC check if skipOnCorruption is true" in {
      def createKeyValueSkipList(keyValues: Slice[Memory]) = {
        val map = SkipListConcurrent[SliceOption[Byte], MemoryOption, Slice[Byte], Memory](Slice.Null, Memory.Null)(keyOrder)
        keyValues foreach {
          keyValue =>
            map.put(keyValue.key, Memory.put(keyValue.key, keyValue.getOrFetchValue()))
        }
        map
      }

      val keyValues1 = Slice(Memory.put(1, 1), Memory.put(2, 2), Memory.put(3, 3), Memory.put(4, 4), Memory.put(5, 5))
      val keyValues2 = Slice(Memory.put(6, 6), Memory.put(7, 7), Memory.put(8, 8), Memory.put(9, 9), Memory.put(10, 10))
      val allKeyValues = keyValues1 ++ keyValues2

      val skipList1 = createKeyValueSkipList(keyValues1)
      val skipList2 = createKeyValueSkipList(keyValues2)

      import MemoryLogEntryWriter.LogEntryPutWriter
      val bytes1 = LogEntryParser.write(skipList1.iterator)
      val bytes2 = LogEntryParser.write(skipList2.iterator)
      //combined the bytes of both the entries so that are in one single file.
      val allBytes = Slice.wrap((bytes1 ++ bytes2).toArray)

      val map = SkipListConcurrent[SliceOption[Byte], MemoryOption, Slice[Byte], Memory](Slice.Null, Memory.Null)(keyOrder)

      //re-read allBytes and write it to skipList and it should contain all the original entries
      import MemoryLogEntryReader.KeyValueLogEntryPutReader
      val logEntry = LogEntryParser.read(allBytes, dropCorruptedTailEntries = false).runRandomIO.get.item.value
      logEntry applyBatch map
      map should have size allKeyValues.size
      allKeyValues foreach {
        keyValue =>
          val value = map.get(keyValue.key)
          value.getUnsafe shouldBe Memory.put(keyValue.key, keyValue.getOrFetchValue())
      }

      //corrupt bytes in bytes2 and read the bytes again. keyValues2 should not exist as it's key-values are corrupted.
      val corruptedBytes2: Slice[Byte] = allBytes.dropRight(1)
      LogEntryParser.read(corruptedBytes2, dropCorruptedTailEntries = false).left.runRandomIO.get.exception shouldBe a[IllegalStateException]
      //enable skip corrupt entries.
      val logEntryWithTailCorruptionSkipOnCorruption = LogEntryParser.read(corruptedBytes2, dropCorruptedTailEntries = true).runRandomIO.get.item.value
      map.clear()
      logEntryWithTailCorruptionSkipOnCorruption applyBatch map
      map should have size 5 //only one entry is corrupted
      keyValues1 foreach {
        keyValue =>
          val value = map.get(keyValue.key).getUnsafe
          value shouldBe Memory.put(keyValue.key, keyValue.getOrFetchValue())
      }

      //corrupt bytes of bytes1
      val corruptedBytes1: Slice[Byte] = allBytes.drop(1)
      //all bytes are corrupted, failure occurs.
      LogEntryParser.read(corruptedBytes1, dropCorruptedTailEntries = false).left.runRandomIO.get.exception shouldBe a[IllegalStateException]
      LogEntryParser.read(corruptedBytes1, dropCorruptedTailEntries = true).runRandomIO.get.item shouldBe empty
    }
  }
}
