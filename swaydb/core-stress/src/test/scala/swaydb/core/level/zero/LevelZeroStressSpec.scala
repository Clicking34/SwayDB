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
//package swaydb.core.level.zero
//
//import swaydb.core.TestBase
//import swaydb.core.util.{Benchmark, Delay}
//import swaydb.slice.Slice
//import swaydb.slice.order.KeyOrder
//import swaydb.serializers.Default._
//import swaydb.core.IOAssert._
//
//import scala.concurrent.Future
//import scala.concurrent.duration._
//import scala.util.Random
//import swaydb.serializers._
//import swaydb.core.TestData._
//import swaydb.core.CommonAssertions._
//import swaydb.testkit.RunThis._
//
//
////@formatter:off
//class LevelZeroStressSpec0 extends LevelZeroStressSpec
//
//class LevelZeroStressSpec1 extends LevelZeroStressSpec {
//  override def levelFoldersCount = 10
//  override def mmapSegmentsOnWrite = true
//  override def mmapSegmentsOnRead = true
//  override def level0MMAP = MMAP.Enabled(OperatingSystem.isWindows(), forceSave = GenForceSave.mmap())
//  override def appendixStorageMMAP = MMAP.Enabled(OperatingSystem.isWindows(), forceSave = GenForceSave.mmap())
//}
//
//class LevelZeroStressSpec2 extends LevelZeroStressSpec {
//  override def levelFoldersCount = 10
//  override def mmapSegmentsOnWrite = false
//  override def mmapSegmentsOnRead = false
//  override def level0MMAP = MMAP.Disabled(forceSave = GenForceSave.standard())
//  override def appendixStorageMMAP = MMAP.Disabled(forceSave = GenForceSave.standard())
//}
//
//class LevelZeroStressSpec3 extends LevelZeroStressSpec {
//  override def inMemoryStorage = true
//}
//
//sealed trait LevelZeroStressSpec extends TestBase with Benchmark {
//
//  implicit val keyOrder: KeyOrder[Slice[Byte]] = KeyOrder.default
//
////  override def deleteFiles = false
//
////  val keyValueCount = 1000000
//  val keyValueCount = 100
//
//  "Concurrently put, value, read lower and higher 1 million key-values" in {
//    val level1 = TestLevel(nextLevel = Some(TestLevel()))
//    val zero = TestLevelZero(Some(level1))
//    val keyValues = randomIntKeyStringValues(keyValueCount, valueSize = 17)
//
//    println("***********************************************************************************************")
//    println("************************ FINISHED CREATING TEST DATA --- STARTING TEST ************************")
//    println("***********************************************************************************************")
//
//    def doPut =
//      keyValues foreach {
//        keyValue =>
//          val key = keyValue.key.read[Int]
//          if (key % 100000 == 0)
//            println(s"PUT. Current written KeyValue : $key")
//          zero.put(keyValue.key, keyValue.getOrFetchValue()).runIO
//      }
//
//    def doGet =
//      keyValues foreach {
//        keyValue =>
//          val key = keyValue.key.read[Int]
//          if (key % 100000 == 0)
//            println(s"GET. KeyValue : $key")
//          zero.get(keyValue.key).runIO shouldBe keyValue.getOrFetchValue()
//      }
//
//    def readLower =
//      (1 until keyValues.size).foreach {
//        index =>
//          if (index % 100000 == 0)
//            println(s"Read lower key index. Current read index: $index")
//          val expectedLower = keyValues(index - 1)
//          val (key, value) = zero.lower(keyValues(index).key).runIO
//          key shouldBe expectedLower.key
//          value shouldBe expectedLower.getOrFetchValue()
//      }
//
//    def readHigher =
//      (0 until keyValues.size - 1).foreach {
//        index =>
//          if (index % 100000 == 0)
//            println(s"Read higher key index. Current read index: $index")
//          val expectedLower = keyValues(index + 1)
//          val (key, value) = zero.higher(keyValues(index).key).runIO
//          key shouldBe expectedLower.key
//          value shouldBe expectedLower.getOrFetchValue()
//      }
//
//    //Write all keys values to make sure that data is actually there.
//    benchmark("write benchmark") {
//      doPut
//    }
//    //Asynchronous writes & reads test A.K.A Bombarding test
//    //random tests to do concurrent reads and write.
//    // Tweak with the Level configurations above to see how it handles for large load.
//    // This test only a 2 level configuration (level0 and level1) so it is not a performance test but a stress test.
//    def futures: Future[Seq[Unit]] =
//      Future.sequence {
//        Seq(
//          Delay.future(randomNextInt(200).milli)(doGet),
//          Delay.future(randomNextInt(100).milli)(doPut),
//          Delay.future(randomNextInt(300).milli)(readLower),
//          Delay.future(randomNextInt(400).milli)(readHigher)
//        )
//      }
//
//    benchmark("Concurrent read write benchmark") {
//      futures runThis 3.times await 5.minutes
//    }
//
//    println("****************************************************************************")
//    println("*********************************** DONE ***********************************")
//    println("****************************************************************************")
//
////    Thread.sleep(1000000)
//  }
//}
