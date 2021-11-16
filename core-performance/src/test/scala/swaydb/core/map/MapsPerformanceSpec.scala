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
//import swaydb.IOValues._
//import swaydb.core.CommonAssertions._
//import swaydb.core.TestData._
//import swaydb.core.sweeper.FileSweeper
//import swaydb.core.data.Memory
//import swaydb.core.io.file.Effect
//import swaydb.core.level.zero.LevelZeroSkipListMerger
//import swaydb.core.util.Benchmark
//import swaydb.core.{TestBase, TestSweeper, TestTimer}
//import swaydb.data.accelerate.Accelerator
//import swaydb.data.config.RecoveryMode
//import swaydb.data.order.{KeyOrder, TimeOrder}
//import swaydb.slice.Slice


//import swaydb.data.util.StorageUnits._
//
//class MapsPerformanceSpec extends TestBase {
//
//  implicit val keyOrder: KeyOrder[Slice[Byte]] = KeyOrder.default
//  implicit def testTimer: TestTimer = TestTimer.random
//  implicit val fileSweeper: FileSweeper = TestSweeper.fileSweeper
//  implicit val memorySweeper = TestSweeper.memorySweeperMax
//  implicit val timeOrder: TimeOrder[Slice[Byte]] = TimeOrder.long
//
//  import swaydb.core.log.serializer.LevelZeroLogEntryReader._
//  import swaydb.core.log.serializer.LevelZeroLogEntryWriter._
//
//  implicit val skipListMerger = LevelZeroSkipListMerger
//
//  "Maps" should {
//    "write key values" in {
//      //      val keyValues = randomIntKeyValues(2000000)
//      val keyValues = randomKeyValues(2000)
//
//      def testWrite(maps: Maps[Slice[Byte], Memory]) =
//        keyValues foreach {
//          keyValue =>
//            maps.write {
//              time =>
//                LogEntry.Put[Slice[Byte], Memory.Put](keyValue.key, Memory.Put(keyValue.key, keyValue.getOrFetchValue, None, time.next))(Level0PutWriter)
//            }.runRandomIO.right.value
//        }
//
//      def testRead(maps: Maps[Slice[Byte], Memory]) =
//        keyValues foreach {
//          keyValue =>
//            maps.get(keyValue.key)
//          //            maps.get(keyValue.key).runIO shouldBe ((ValueType.Add, keyValue.getOrFetchValue.runIO.value))
//        }
//
//      val dir1 = Effect.createDirectoryIfAbsent(testDir.resolve(1.toString))
//
//      val map1 = Maps.persistent[Slice[Byte], Memory](dir1, mmap = true, 4.mb, Accelerator.noBrakes(), RecoveryMode.ReportFailure).runRandomIO.right.value
//      Benchmark(s"MMAP = true - writing ${keyValues.size} keys") {
//        testWrite(map1)
//      }
//      Benchmark(s"MMAP = true - reading ${keyValues.size} keys") {
//        testRead(map1)
//      }
//
//      val dir2 = Effect.createDirectoryIfAbsent(testDir.resolve(2.toString))
//      val map2 = Maps.persistent[Slice[Byte], Memory](dir2, mmap = false, 4.mb, Accelerator.noBrakes(), RecoveryMode.ReportFailure).runRandomIO.right.value
//      Benchmark(s"MMAP = false - writing ${keyValues.size} keys") {
//        testWrite(map2)
//      }
//      Benchmark(s"MMAP = false - reading ${keyValues.size} keys") {
//        testRead(map2)
//      }
//
//      val map3 = Maps.memory(4.mb, Accelerator.noBrakes())
//      Benchmark(s"In-memory - writing ${keyValues.size} keys") {
//        testWrite(map3)
//      }
//
//      Benchmark(s"In-memory - reading ${keyValues.size} keys") {
//        testRead(map3)
//      }
//
//      map1.close.runRandomIO.right.value
//      map2.close.runRandomIO.right.value
//      map3.close.runRandomIO.right.value
//    }
//  }
//}
