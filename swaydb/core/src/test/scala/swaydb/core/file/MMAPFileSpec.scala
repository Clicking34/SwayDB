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

package swaydb.core.file

import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import swaydb.config.GenForceSave
import swaydb.core.CoreTestSweeper
import swaydb.core.file.sweeper.bytebuffer.ByteBufferCleaner
import swaydb.core.file.CoreFileTestKit._
import swaydb.effect.Effect
import swaydb.slice.Slice
import swaydb.slice.SliceTestKit._
import swaydb.testkit.RunThis._
import swaydb.testkit.TestKit._
import swaydb.utils.StorageUnits._

import java.nio.channels.FileChannel
import java.nio.channels.FileChannel.MapMode
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicBoolean
import swaydb.effect.EffectTestKit._

class MMAPFileSpec extends AnyWordSpec with MockFactory {

  "BEHAVIOUR TEST - cleared MappedByteBuffer without forceSave" should {
    "not fatal JVM terminate" when {
      "writing, reading & copying" in {
        CoreTestSweeper {
          implicit sweeper =>
            runThis(50.times, log = true) {
              //create random path and byte slice
              val path = genFilePath()
              val bytes = genBytesSlice(10.mb)

              /**
               * BEHAVIOR 1 - Open a memory-mapped file in read & write mode.
               */
              {
                //create a readWriteChannel
                val readWriteChannel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)
                val readWriteBuff = readWriteChannel.map(MapMode.READ_WRITE, 0, bytes.size)
                //save bytes to the channel randomly in groups of small slices or one large slice
                eitherOne(
                  readWriteBuff.put(bytes.toByteBufferWrap()),
                  bytes.groupedSlice(randomIntMax(10000) max 1).foreach {
                    slice =>
                      readWriteBuff.put(slice.toByteBufferWrap())
                  },
                  bytes.groupedSlice(randomIntMax(1000) max 1).foreach {
                    slice =>
                      readWriteBuff.put(slice.toByteBufferWrap())
                  },
                  bytes.groupedSlice(randomIntMax(100) max 1).foreach {
                    slice =>
                      readWriteBuff.put(slice.toByteBufferWrap())
                  },
                  bytes.groupedSlice(randomIntMax(10) max 1).foreach {
                    slice =>
                      readWriteBuff.put(slice.toByteBufferWrap())
                  }
                )

                //forceSave configurations
                val forceSave = GenForceSave.mmap()
                val alreadyForced = randomBoolean()
                val forced = new AtomicBoolean(alreadyForced)

                implicit val applier: ForceSaveApplier =
                  if (alreadyForced)
                    mock[ForceSaveApplier] //mock it so it not get invoked
                  else
                    ForceSaveApplier.On

                //do not forceSave and clear the in-memory bytes.
                ByteBufferCleaner.initialiseCleaner(readWriteBuff, path, forced, forceSave).get

                if (alreadyForced)
                  forced.get() shouldBe true
                else if (forceSave.enabledBeforeClean)
                  forced.get() shouldBe true
                else
                  forced.get() shouldBe false

                //copy the file and read and aldo read the bytes form path in any order should succeed.
                Seq(
                  () => {
                    //copy file to another path
                    val path2 = Effect.copy(path, genFilePath())
                    Slice.wrap(Effect.readAllBytes(path2)) shouldBe bytes
                  },
                  () =>
                    //read all the bytes from disk and they exist
                    Slice.wrap(Effect.readAllBytes(path)) shouldBe bytes
                ).runThisRandomly()
              }

              /**
               * BEHAVIOR 2 - REOPEN the above memory-mapped file in read mode.
               */
              {
                //open the file as another memory-mapped file in readonly mode
                val readOnlyChannel = FileChannel.open(path, StandardOpenOption.READ)
                val readOnlyBuff = readOnlyChannel.map(MapMode.READ_ONLY, 0, bytes.size)
                //read all bytes from the readOnly buffer
                val array = new Array[Byte](bytes.size)
                readOnlyBuff.get(array)
                array shouldBe bytes.toArray

                val forceSaveAgain = GenForceSave.mmap()
                val alreadyForced2 = randomBoolean()
                val forcedAgain = new AtomicBoolean(alreadyForced2)

                implicit val applier: ForceSaveApplier =
                  if (alreadyForced2)
                    mock[ForceSaveApplier] //mock it so it not get invoked
                  else
                    ForceSaveApplier.On

                //clear the buffer again
                ByteBufferCleaner.initialiseCleaner(readOnlyBuff, path, forcedAgain, forceSaveAgain).get

                if (alreadyForced2)
                  forcedAgain.get() shouldBe true
                else if (forceSaveAgain.enabledBeforeClean)
                  forcedAgain.get() shouldBe true
                else
                  forcedAgain.get() shouldBe false

                //read bytes from disk and they should exist.
                Slice.wrap(Effect.readAllBytes(path)) shouldBe bytes
              }
            }
        }
      }
    }
  }
}
