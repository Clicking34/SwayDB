/////*
//// * Copyright 2018 Simer JS Plaha (simer.j@gmail.com - @simerplaha)
//// *
//// * Licensed under the Apache License, Version 2.0 (the "License");
//// * you may not use this file except in compliance with the License.
//// * You may obtain a copy of the License at
//// *
//// * http://www.apache.org/licenses/LICENSE-2.0
//// *
//// * Unless required by applicable law or agreed to in writing, software
//// * distributed under the License is distributed on an "AS IS" BASIS,
//// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//// * See the License for the specific language governing permissions and
//// * limitations under the License.
//// */
////
////package swaydb.core.level
////
////import org.scalamock.scalatest.MockFactory
////import swaydb.effect.IOValues._
////import swaydb.config.MMAP
////import swaydb.config.storage.LevelStorage
////import swaydb.core.CommonAssertions._
////import swaydb.core.CoreTestSweeper._
////import swaydb.core.CoreTestData._
////import swaydb.core._
////import swaydb.core.segment.block.segment.SegmentBlockConfig
////import swaydb.core.segment.data._
////import swaydb.core.segment.io.SegmentCompactionIO
////import swaydb.effect.Effect._
////import swaydb.effect.Effect.implicits._
////import swaydb.effect.{Dir, Effect}
////import swaydb.slice.Slice
////import swaydb.slice.order.{KeyOrder, TimeOrder}
////import swaydb.testkit.RunThis._
////import swaydb.utils.{Extension, IDGenerator, OperatingSystem}
////import swaydb.utils.PipeOps._
////import swaydb.utils.StorageUnits._
////import swaydb.{Glass, IO}
////
////import java.nio.file.{FileAlreadyExistsException, NoSuchFileException}
////import scala.concurrent.duration.{Duration, DurationInt}
////import scala.util.Random
////
////class LevelSegmentSpec0 extends LevelSegmentSpec
////
////class LevelSegmentSpec1 extends LevelSegmentSpec {
////  override def levelFoldersCount = 10
////  override def mmapSegments = MMAP.On(OperatingSystem.isWindows(), forceSave = GenForceSave.mmap())
////  override def level0MMAP = MMAP.On(OperatingSystem.isWindows(), forceSave = GenForceSave.mmap())
////  override def appendixStorageMMAP = MMAP.On(OperatingSystem.isWindows(), forceSave = GenForceSave.mmap())
////}
////
////class LevelSegmentSpec2 extends LevelSegmentSpec {
////  override def levelFoldersCount = 10
////  override def mmapSegments = MMAP.Off(forceSave = GenForceSave.standard())
////  override def level0MMAP = MMAP.Off(forceSave = GenForceSave.standard())
////  override def appendixStorageMMAP = MMAP.Off(forceSave = GenForceSave.standard())
////}
////
////class LevelSegmentSpec3 extends LevelSegmentSpec {
////  override def isMemorySpec = true
////}
////
////sealed trait LevelSegmentSpec extends AnyWordSpec {
////
////  implicit val keyOrder: KeyOrder[Slice[Byte]] = KeyOrder.default
////  implicit val testTimer: TestTimer = TestTimer.Empty
////  implicit val timeOrder: TimeOrder[Slice[Byte]] = TimeOrder.long
////  val keyValuesCount = 100
////  implicit val ec = TestExecutionContext.executionContext
////
////  //  override def deleteFiles: Boolean =
////  //    false
////
////  "writing Segments to single level" should {
////    "succeed" when {
////      "level is empty" in {
////        CoreTestSweeper {
////          implicit sweeper =>
////            import sweeper._
////
////            val level = TestLevel()
////            val keyValues = randomIntKeyStringValues(keyValuesCount)
////            val segment = GenSegment(keyValues)
////            segment.close.runRandomIO.get
////            level.put(segment) shouldBe IO.unit
////            assertReads(keyValues, level)
////        }
////      }
////
////      "level is non-empty" in {
////        runThis(10.times, log = true) {
////          CoreTestSweeper {
////            implicit sweeper =>
////              import sweeper._
////
////              //small Segment size so that small Segments do not collapse when running this test
////              // as reads do not value retried on failure in Level, they only value retried in LevelZero.
////              val level = TestLevel(segmentConfig = SegmentBlockConfig.random(minSegmentSize = 100.bytes, mmap = mmapSegments))
////              val keyValues = randomIntKeyStringValues(keyValuesCount)
////              val segment = GenSegment(keyValues)
////              level.put(segment) shouldBe IO.unit
////
////              val keyValues2 = randomIntKeyStringValues(keyValuesCount * 10)
////              val segment2 = GenSegment(keyValues2).runRandomIO.get
////              level.put(segment2).get
////
////              assertGet(keyValues, level)
////              assertGet(keyValues2, level)
////          }
////        }
////      }
////
////      "writing multiple Segments to an empty Level" in {
////        CoreTestSweeper {
////          implicit sweeper =>
////            import sweeper._
////
////            val level = TestLevel()
////            val keyValues = randomIntKeyStringValues(keyValuesCount * 3, valueSize = 1000)
////
////            val (keyValues1, keyValues2, keyValues3) =
////              keyValues
////                .splitAt(keyValues.size / 3)
////                .==> {
////                  case (split1, split2) =>
////                    val (two, three) = split2.splitAt(split2.size / 2)
////                    (split1, two, three)
////                }
////
////            val segments = Seq(GenSegment(keyValues1).runRandomIO.get, GenSegment(keyValues2).runRandomIO.get, GenSegment(keyValues3).runRandomIO.get)
////            level.putSegments(segments) shouldBe IO.unit
////
////            assertReads(keyValues, level)
////        }
////      }
////
////      "writing multiple Segments to a non empty Level" in {
////        CoreTestSweeper {
////          implicit sweeper =>
////            import sweeper._
////
////            val level = TestLevel()
////            val allKeyValues = randomPutKeyValues(keyValuesCount * 3, valueSize = 1000, addPutDeadlines = false)(TestTimer.Empty)
////            val slicedKeyValues = allKeyValues.groupedSlice(3)
////            val keyValues1 = slicedKeyValues(0)
////            val keyValues2 = slicedKeyValues(1)
////            val keyValues3 = slicedKeyValues(2)
////
////            //create a level with key-values
////            level.put(keyValues2) shouldBe IO.unit
////            level.isEmpty shouldBe false
////
////            val segments =
////              Seq(
////                GenSegment(keyValues1, segmentConfig = SegmentBlockConfig.random(minSegmentSize = Int.MaxValue, mmap = mmapSegments)),
////                GenSegment(keyValues3, segmentConfig = SegmentBlockConfig.random(minSegmentSize = Int.MaxValue, mmap = mmapSegments))
////              )
////
////            level.putSegments(segments) shouldBe IO.unit
////
////            assertReads(allKeyValues, level)
////        }
////      }
////
////      "distribute Segments to multiple directories based on the distribution ratio" in {
////        if (isPersistent) {
////          CoreTestSweeper {
////            implicit sweeper =>
////              import sweeper._
////
////              val dir = testClassDir.resolve("distributeSegmentsTest").sweep()
////
////              def assertDistribution() = {
////                def assert() = {
////                  dir.resolve(1.toString).files(Extension.Seg) should have size 7
////                  dir.resolve(2.toString).files(Extension.Seg) should have size 14
////                  dir.resolve(3.toString).files(Extension.Seg) should have size 21
////                  dir.resolve(4.toString).files(Extension.Seg) should have size 28
////                  dir.resolve(5.toString).files(Extension.Seg) should have size 30
////                }
////
////                if (OperatingSystem.isWindows())
////                  eventual(10.seconds) {
////                    sweeper.receiveAll()
////                    assert()
////                  }
////                else
////                  assert()
////              }
////
////              val storage =
////                LevelStorage.Persistent(
////                  dir = dir.resolve(1.toString),
////                  otherDirs =
////                    Seq(
////                      Dir(dir.resolve(2.toString), 2),
////                      Dir(dir.resolve(3.toString), 3),
////                      Dir(dir.resolve(4.toString), 4),
////                      Dir(dir.resolve(5.toString), 5)
////                    ),
////                  appendixMMAP = MMAP.randomForLog(),
////                  appendixFlushCheckpointSize = 4.mb
////                )
////
////              val keyValues = randomPutKeyValues(100)
////
////              val level = TestLevel(segmentConfig = SegmentBlockConfig.random(minSegmentSize = 1.byte, deleteDelay = Duration.Zero, mmap = mmapSegments), levelStorage = storage)
////
////              level.put(keyValues).runRandomIO.get
////              level.segmentsCount() shouldBe keyValues.size
////              assertDistribution()
////
////              //write the same key-values again so that all Segments are updated. This should still maintain the Segment distribution
////              level.put(keyValues).runRandomIO.get
////              assertDistribution()
////
////              //shuffle key-values should still maintain distribution order
////              Random.shuffle(keyValues.grouped(10)) foreach {
////                keyValues =>
////                  level.put(keyValues).runRandomIO.get
////              }
////              assertDistribution()
////
////              //delete some key-values
////              Random.shuffle(keyValues.grouped(10)).take(2) foreach {
////                keyValues =>
////                  val deleteKeyValues = keyValues.mapToSlice(keyValue => Memory.remove(keyValue.key)).toSlice
////                  level.put(deleteKeyValues).runRandomIO.get
////              }
////
////              level.put(keyValues).runRandomIO.get
////              assertDistribution()
////          }
////        }
////      }
////    }
////
////    "fail" when {
////      "fail when writing a deleted segment" in {
////        CoreTestSweeper {
////          implicit sweeper =>
////            import sweeper._
////
////            val level = TestLevel()
////
////            val keyValues = randomIntKeyStringValues()
////            val segment = GenSegment(keyValues)
////            segment.delete()
////
////            if (isWindowsAndMMAPSegments())
////              sweeper.receiveAll()
////
////            val result = level.put(segment).left.get
////            if (isPersistent)
////              result.exception shouldBe a[NoSuchFileException]
////            else
////              result.exception shouldBe a[Exception]
////
////            level.isEmpty shouldBe true
////
////            //if it's a persistent Level, reopen to ensure that Segment did not value committed.
////            if (isPersistent) level.reopen.isEmpty shouldBe true
////        }
////      }
////
////      "revert copy if merge fails" in {
////        if (isPersistent)
////          runThis(10.times, log = true) {
////            CoreTestSweeper {
////              implicit sweeper =>
////
////                val keyValues = randomKeyValues(100)(TestTimer.Empty).groupedSlice(10).toArray
////                val segmentToMerge = keyValues map (keyValues => GenSegment(keyValues))
////
////                val level = TestLevel(segmentConfig = SegmentBlockConfig.random(minSegmentSize = 150.bytes, deleteDelay = Duration.Zero, mmap = mmapSegments))
////
////                {
////                  implicit val compactionIO: SegmentCompactionIO.Actor =
////                    SegmentCompactionIO.create().sweep()
////
////                  level.putSegments(segmentToMerge) shouldBe IO.unit
////                }
////
////                //segment to copy
////                val id = IDGenerator.segment(level.segmentIDGenerator.next + 9)
////                level.pathDistributor.queuedPaths foreach { //create this file in all paths.
////                  _ =>
////                    Effect.createFile(level.pathDistributor.next().resolve(id))
////                }
////
////                val appendixBeforePut = level.segments()
////                val levelFilesBeforePut = level.segmentFilesOnDisk()
////
////                {
////                  implicit val compactionIO: SegmentCompactionIO.Actor =
////                    SegmentCompactionIO.create()
////
////                  level.putSegments(segmentToMerge).left.get.exception shouldBe a[FileAlreadyExistsException]
////
////                  compactionIO.terminate[Glass]() shouldBe unit
////                }
////
////                if (isWindowsAndMMAPSegments())
////                  eventual(10.seconds) {
////                    sweeper.receiveAll()
////                    level.segmentFilesOnDisk() shouldBe levelFilesBeforePut
////                  }
////                else
////                  level.segmentFilesOnDisk() shouldBe levelFilesBeforePut
////
////                level.segments().map(_.path) shouldBe appendixBeforePut.map(_.path)
////            }
////          }
////      }
////    }
////  }
////}
