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
//package swaydb.core.level
//
//import org.scalamock.scalatest.MockFactory
//import org.scalatest.PrivateMethodTester
//import swaydb.effect.IOValues._
//import swaydb.config.{Atomic, MMAP, OptimiseWrites}
//import swaydb.core.CommonAssertions._
//import swaydb.core.CoreTestSweeper._
//import swaydb.core.CoreTestData._
//import swaydb.core._
//import swaydb.core.level.zero.LevelZeroLogCache
//import swaydb.core.log.{Log, LogEntry}
//import swaydb.core.segment.data._
//import swaydb.core.segment.ref.search.ThreadReadState
//import swaydb.serializers.Default._
//import swaydb.serializers._
//import swaydb.slice.Slice
//import swaydb.slice.order.{KeyOrder, TimeOrder}
//import swaydb.utils.OperatingSystem
//import swaydb.utils.StorageUnits._
//import swaydb.testkit.TestKit._
//
//class LevelMapSpec0 extends LevelMapSpec
//
//class LevelMapSpec1 extends LevelMapSpec {
//  override def levelFoldersCount = 10
//  override def mmapSegments = MMAP.On(OperatingSystem.isWindows(), forceSave = GenForceSave.mmap())
//  override def level0MMAP = MMAP.On(OperatingSystem.isWindows(), forceSave = GenForceSave.mmap())
//  override def appendixStorageMMAP = MMAP.On(OperatingSystem.isWindows(), forceSave = GenForceSave.mmap())
//}
//
//class LevelMapSpec2 extends LevelMapSpec {
//  override def levelFoldersCount = 10
//  override def mmapSegments = MMAP.Off(forceSave = GenForceSave.standard())
//  override def level0MMAP = MMAP.Off(forceSave = GenForceSave.standard())
//  override def appendixStorageMMAP = MMAP.Off(forceSave = GenForceSave.standard())
//}
//
//class LevelMapSpec3 extends LevelMapSpec {
//  override def isMemorySpec = true
//}
//
//sealed trait LevelMapSpec extends AnyWordSpec {
//
//  implicit val keyOrder: KeyOrder[Slice[Byte]] = KeyOrder.default
//  implicit val testTimer: TestTimer = TestTimer.Empty
//  implicit val timeOrder: TimeOrder[Slice[Byte]] = TimeOrder.long
//  implicit val ec = TestExecutionContext.executionContext
//  val keyValuesCount = 100
//
//  //  override def deleteFiles: Boolean =
//  //    false
//
//  "putMap on a single Level" should {
//    import swaydb.core.log.serialiser.MemoryKeyValueReader._
//    import swaydb.core.log.serialiser.MemoryKeyValueWriter._
//
//    def createTestMap()(implicit sweeper: CoreTestSweeper) = {
//      import sweeper._
//
//      implicit val optimiseWrites = OptimiseWrites.random
//      implicit val atomic: Atomic = Atomic.random
//
//      val map =
//        if (isPersistent)
//          PersistentLog[Slice[Byte], Memory, LevelZeroLogCache](
//            folder = randomIntDirectory,
//            mmap = MMAP.On(OperatingSystem.isWindows(), GenForceSave.mmap()),
//            flushOnOverflow = true,
//            fileSize = 1.mb,
//            dropCorruptedTailEntries = false
//          ).runRandomIO.get.item.sweep()
//        else
//          MemoryLog[Slice[Byte], Memory, LevelZeroLogCache]()
//
//      val keyValues = randomPutKeyValues(keyValuesCount, addRemoves = true, addPutDeadlines = false)
//      keyValues foreach {
//        keyValue =>
//          map.writeSync(LogEntry.Put(keyValue.key, keyValue))
//      }
//
//      (map, keyValues)
//    }
//
//    "succeed" when {
//      "writing to an empty Level" in {
//        CoreTestSweeper {
//          implicit sweeper =>
//            import sweeper._
//
//            val (map, keyValues) = createTestMap()
//
//            val level = TestLevel()
//            level.putMap(map).get
//            //since this is a new Segment and Level has no sub-level, all the deleted key-values will value removed.
//            val (deletedKeyValues, otherKeyValues) = keyValues.partition(_.isInstanceOf[Memory.Remove])
//
//            assertReads(otherKeyValues, level)
//
//            //deleted key-values do not exist.
//            deletedKeyValues foreach {
//              deleted =>
//                level.get(deleted.key, ThreadReadState.random).runRandomIO.get.toOptionPut shouldBe empty
//            }
//        }
//      }
//
//      "writing to a non empty Level" in {
//        CoreTestSweeper {
//          implicit sweeper =>
//            import sweeper._
//
//            val (map, keyValues) = createTestMap()
//
//            val level = TestLevel()
//
//            //creating a Segment with existing string key-values
//            val existingKeyValues = Array(Memory.put("one", "one"), Memory.put("two", "two"), Memory.put("three", "three"))
//
//            val sortedExistingKeyValues =
//              Slice.wrap(
//                Array(
//                  //also randomly set expired deadline for Remove.
//                  Memory.put("one", "one"), Memory.put("two", "two"), Memory.put("three", "three"), Memory.remove("four", randomly(expiredDeadline()))
//                ).sorted(keyOrder.on[KeyValue](_.key)))
//
//            level.put(sortedExistingKeyValues).runRandomIO.get
//
//            //put a new map
//            level.putMap(map).get
//            assertGet(keyValues.filterNot(_.isInstanceOf[Memory.Remove]), level)
//
//            level.get("one", ThreadReadState.random).runRandomIO.get.getPut shouldBe existingKeyValues(0)
//            level.get("two", ThreadReadState.random).runRandomIO.get.getPut shouldBe existingKeyValues(1)
//            level.get("three", ThreadReadState.random).runRandomIO.get.getPut shouldBe existingKeyValues(2)
//            level.get("four", ThreadReadState.random).runRandomIO.get.toOptionPut shouldBe empty
//        }
//      }
//    }
//  }
//}
