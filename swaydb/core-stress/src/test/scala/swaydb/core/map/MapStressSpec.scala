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
//
//package swaydb.core.log
//
//import org.scalatest.OptionValues._
//import swaydb.effect.IOValues._
//import swaydb.core.CommonAssertions._
//import swaydb.core.TestData._
//import swaydb.core.file.sweeper.FileSweeper
//import swaydb.core.segment.data.Memory
//import swaydb.core.level.zero.LevelZeroSkipListMerger
//import swaydb.core.log.serializer.MemoryKeyValueWriter.Level0PutWriter
//import swaydb.core.{TestBase, CoreTestSweeper, TestTimer}
//import swaydb.slice.order.{KeyOrder, TimeOrder}
//import swaydb.slice.Slice
//import swaydb.config.util.StorageUnits._
//
//class MapStressSpec extends TestBase {
//
//  implicit val keyOrder: KeyOrder[Slice[Byte]] = KeyOrder.default
//
//  implicit val skipListMerger = LevelZeroSkipListMerger
//  implicit val fileSweeper: FileSweeper = CoreTestSweeper.fileSweeper
//  implicit val memorySweeper = CoreTestSweeper.memorySweeperMax
//  implicit val timeOrder: TimeOrder[Slice[Byte]] = TimeOrder.long
//
//  implicit def testTimer: TestTimer = TestTimer.Empty
//
//  "Map" should {
//    "write entries when flushOnOverflow is true and map size is 1.kb" in {
//      val keyValues = randomKeyValues(100)
//
//      def test(map: Map[SliceOption[Byte], MemoryOptional, Slice[Byte], Memory]) = {
//        keyValues foreach {
//          keyValue =>
//            val entry = LogEntry.Put[Slice[Byte], Memory.Put](keyValue.key, Memory.put(keyValue.key, keyValue.getOrFetchValue()))(Level0PutWriter)
//            map.write(entry).runRandomIO.get shouldBe true
//        }
//
//        testRead(map)
//      }
//
//      def testRead(map: Map[SliceOption[Byte], MemoryOptional, Slice[Byte], Memory]) =
//        keyValues foreach {
//          keyValue =>
//            map.get(keyValue.key).value shouldBe Memory.put(keyValue.key, keyValue.getOrFetchValue())
//        }
//
//      val dir1 = genDir()
//      val dir2 = genDir()
//
//      import swaydb.core.log.serializer.MemoryKeyValueReader.Level0Reader
//      import swaydb.core.log.serializer.MemoryKeyValueWriter.Level0LogEntryPutWriter
//
//      test(Map.persistent[SliceOption[Byte], MemoryOptional, Slice[Byte], Memory](dir1, mmap = true, flushOnOverflow = true, 1.kb, dropCorruptedTailEntries = false).runRandomIO.get.item)
//      test(Map.persistent[SliceOption[Byte], MemoryOptional, Slice[Byte], Memory](dir2, mmap = false, flushOnOverflow = true, 1.kb, dropCorruptedTailEntries = false).runRandomIO.get.item)
//      test(Map.memory[Slice[Byte], Memory](flushOnOverflow = true, fileSize = 1.kb))
//
//      //reopen - all the entries should value recovered for persistent maps. Also switch mmap types.
//      testRead(Map.persistent[SliceOption[Byte], MemoryOptional, Slice[Byte], Memory](dir1, mmap = false, flushOnOverflow = true, 1.kb, dropCorruptedTailEntries = false).runRandomIO.get.item)
//      testRead(Map.persistent[SliceOption[Byte], MemoryOptional, Slice[Byte], Memory](dir2, mmap = true, flushOnOverflow = true, 1.kb, dropCorruptedTailEntries = false).runRandomIO.get.item)
//
//      //write the same data again
//      test(Map.persistent[SliceOption[Byte], MemoryOptional, Slice[Byte], Memory](dir1, mmap = true, flushOnOverflow = true, 1.kb, dropCorruptedTailEntries = false).runRandomIO.get.item)
//      test(Map.persistent[SliceOption[Byte], MemoryOptional, Slice[Byte], Memory](dir2, mmap = false, flushOnOverflow = true, 1.kb, dropCorruptedTailEntries = false).runRandomIO.get.item)
//
//      //read again
//      testRead(Map.persistent[SliceOption[Byte], MemoryOptional, Slice[Byte], Memory](dir1, mmap = false, flushOnOverflow = true, 1.kb, dropCorruptedTailEntries = false).runRandomIO.get.item)
//      testRead(Map.persistent[SliceOption[Byte], MemoryOptional, Slice[Byte], Memory](dir2, mmap = true, flushOnOverflow = true, 1.kb, dropCorruptedTailEntries = false).runRandomIO.get.item)
//    }
//  }
//}
