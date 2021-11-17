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
//package swaydb.core.file
//
//import swaydb.IOValues._
//import swaydb.testkit.RunThis._
//import swaydb.core.TestData._
//import swaydb.core.sweeper.FileSweeper
//import swaydb.core.sweeper.ByteBufferSweeper.ByteBufferSweeperActor
//import swaydb.core.sweeper.FileSweeper
//import swaydb.core.util.{Benchmark, BlockCacheFileIDGenerator}
//import swaydb.core.{TestBase, TestSweeper}
//import swaydb.config.util.OperatingSystem
//import swaydb.config.util.StorageUnits._
//
//import scala.concurrent.Future
//import scala.concurrent.duration._
//
//class DBFileStressWriteSpec extends TestBase {
//
//  "DBFile" should {
//    //use a larger size (200000) to test on larger data-set.
//    val bytes = randomByteChunks(size = 20000, sizePerChunk = 50.bytes)
//
//    "write key values to a StandardFile" in {
//      val path = randomFilePath
//
//      val file = DBFile.standardWrite(path, randomIOAccess(true), autoClose = false, blockCacheFileId = BlockCacheFileIDGenerator.nextID).runRandomIO.right.value
//      Benchmark("write 1 million key values to a StandardFile") {
//        bytes foreach {
//          byteChunk =>
//            file.append(byteChunk).runRandomIO.right.value
//        }
//      }
//      file.close().runRandomIO.right.value
//    }
//
//    "write key values to a StandardFile concurrently" in {
//      val path = randomFilePath
//
//      val file = DBFile.standardWrite(path, randomIOAccess(true), autoClose = false, blockCacheFileId = BlockCacheFileIDGenerator.nextID).runRandomIO.right.value
//      Benchmark("write 1 million key values to a StandardFile concurrently") {
//        Future.sequence {
//          bytes map {
//            chunk =>
//              Future(file.append(chunk).runRandomIO.right.value)
//          }
//        } await 20.seconds
//      }
//      file.close().runRandomIO.right.value
//    }
//
//    "write key values to a MMAPlFile" in {
//      val path = randomFilePath
//
//      val file =
//        DBFile.mmapInit(
//          path = path,
//          ioStrategy = randomIOAccess(true),
//          bufferSize = bytes.size * 50,
//          blockCacheFileId = BlockCacheFileIDGenerator.nextID,
//          autoClose = false,
//          deleteAfterClean = OperatingSystem.isWindows
//        ).runRandomIO.right.value
//
//      Benchmark("write 1 million key values to a MMAPlFile") {
//        bytes foreach {
//          chunk =>
//            file.append(chunk).runRandomIO.right.value
//        }
//      }
//      file.close().runRandomIO.right.value
//    }
//
//    "write key values to a MMAPlFile concurrently" in {
//      val path = randomFilePath
//
//      val file =
//        DBFile.mmapInit(
//          path = path,
//          ioStrategy = randomIOAccess(true),
//          bufferSize = bytes.size * 50,
//          blockCacheFileId = BlockCacheFileIDGenerator.nextID,
//          autoClose = false,
//          deleteAfterClean = OperatingSystem.isWindows
//        ).runRandomIO.right.value
//
//      Benchmark("write 1 million key values to a MMAPlFile concurrently") {
//        Future.sequence {
//          bytes map {
//            chunk =>
//              Future(file.append(chunk).runRandomIO.right.value)
//          }
//        } await 20.seconds
//      }
//      file.close().runRandomIO.right.value
//    }
//  }
//}
