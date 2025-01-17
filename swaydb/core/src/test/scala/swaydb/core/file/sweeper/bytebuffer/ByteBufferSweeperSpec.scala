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

package swaydb.core.file.sweeper.bytebuffer

import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import swaydb.{ActorConfig, Bag, Glass, IO, TestExecutionContext}
import swaydb.core.CoreTestSweeper
import swaydb.core.CoreTestSweeper._
import swaydb.core.file.{CoreFile, ForceSaveApplier, MMAPFile}
import swaydb.core.file.sweeper.FileSweeper
import swaydb.core.file.sweeper.bytebuffer.ByteBufferSweeper.ByteBufferSweeperActor
import swaydb.core.file.CoreFileTestKit._
import swaydb.effect.Effect
import swaydb.effect.IOValues._
import swaydb.testkit.RunThis._
import swaydb.testkit.TestKit._
import swaydb.utils.OperatingSystem
import swaydb.Bag.{Async, AsyncRetryable}
import swaydb.config.GenForceSave
import swaydb.effect.EffectTestKit._
import swaydb.effect.EffectTestSweeper._
import swaydb.slice.SliceTestKit._

import java.nio.channels.FileChannel
import java.nio.channels.FileChannel.MapMode
import java.nio.file.{Path, Paths, StandardOpenOption}
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Random

class ByteBufferSweeperSpec extends AnyWordSpec with MockFactory {

  implicit val ec: ExecutionContext = TestExecutionContext.executionContext
  implicit val futureBag: AsyncRetryable[Future] = Bag.future

  "clear a MMAP file" in {
    CoreTestSweeper.repeat(10.times) {
      implicit sweeper =>
        import sweeper._

        implicit val fileSweeper: FileSweeper =
          FileSweeper(1, ActorConfig.Basic("FileSweet test - clear a MMAP file", TestExecutionContext.executionContext)).sweep()

        val file: CoreFile =
          CoreFile.mmapWriteableReadable(
            path = genFilePath(),
            fileOpenIOStrategy = genThreadSafeIOStrategy(cacheOnAccess = true),
            autoClose = true,
            deleteAfterClean = OperatingSystem.isWindows(),
            forceSave = GenForceSave.mmap(),
            bytes = Array(genBytesSlice())
          )

        val innerFile = invokePrivate_file(file).shouldBeInstanceOf[MMAPFile]

        fileSweeper.closer.terminateAndRecover[Glass, Unit](_ => ())
        fileSweeper.deleter.terminateAndRecover[Glass, Unit](_ => ())

        eventual(2.seconds) {
          innerFile.isBufferEmpty shouldBe true
        }
    }
  }

  "it should not fatal terminate" when {
    "concurrently reading a closed MMAP file" in {
      CoreTestSweeper {
        implicit sweeper =>
          import sweeper._
          implicit val ec = TestExecutionContext.executionContext

          implicit val fileSweeper = FileSweeper(1, ActorConfig.Timer("FileSweeper Test Timer", 0.second, TestExecutionContext.executionContext)).sweep()
          implicit val cleaner: ByteBufferSweeperActor = ByteBufferSweeper(messageReschedule = 0.millisecond).sweep()
          val bytes = genBytesSlice()

          val files =
            (1 to 10) map {
              _ =>
                CoreFile.mmapWriteableReadable(
                  path = genFilePath(),
                  fileOpenIOStrategy = genThreadSafeIOStrategy(cacheOnAccess = true),
                  autoClose = true,
                  deleteAfterClean = OperatingSystem.isWindows(),
                  forceSave = GenForceSave.mmap(),
                  bytes = bytes
                )
            }

          val timeout = 20.seconds.fromNow

          val readingFutures =
            files map {
              file =>
                Future {
                  while (timeout.hasTimeLeft())
                    file.get(position = 0).runRandomIO.get shouldBe bytes.head
                }
            }

          //close memory mapped files (that performs unsafe Buffer cleanup)
          //and repeatedly reading from it should not cause fatal shutdown.

          val closing =
            Future {
              files map {
                file =>
                  while (timeout.hasTimeLeft())
                    file.close()
              }
            }

          //keep this test running for a few seconds.
          sleep(timeout)

          fileSweeper.closer.terminateAndRecover(_ => ()).await(10.seconds)
          fileSweeper.deleter.terminateAndRecover(_ => ()).await(10.seconds)
          fileSweeper.messageCount shouldBe 0
          closing.await(1.second)
          Future.sequence(readingFutures).await(1.second)
      }
    }
  }

  "recordCleanRequest & recordCleanSuccessful" should {
    "create a record on empty and remove on all clean" in {
      val path = Paths.get("test")
      val map = mutable.HashMap.empty[Path, mutable.HashMap[Long, ByteBufferCommand.Clean]]

      implicit val forceSaveApplier = ForceSaveApplier.Off

      val command = ByteBufferCommand.Clean(null, () => false, new AtomicBoolean(false), path, GenForceSave.mmap())

      ByteBufferSweeper.recordCleanRequest(command, map)
      map should have size 1

      val storedCommand = map.get(path).get
      storedCommand should have size 1
      storedCommand.head._1 shouldBe command.id
      storedCommand.head._2 shouldBe command

      ByteBufferSweeper.recordCleanSuccessful(command, map)
      map shouldBe empty
      map.get(path) shouldBe empty
    }

    "increment record if non-empty and decrement on success" in {
      CoreTestSweeper {
        implicit sweeper =>

          val path = Paths.get("test")
          val map = mutable.HashMap.empty[Path, mutable.HashMap[Long, ByteBufferCommand.Clean]]

          implicit val forceSaveApplier = ForceSaveApplier.Off

          val command = ByteBufferCommand.Clean(null, () => false, new AtomicBoolean(false), path, GenForceSave.mmap())

          ByteBufferSweeper.recordCleanRequest(command, map)
          map should have size 1
          ByteBufferSweeper.recordCleanSuccessful(command, map)
          map should have size 0

          //submit clean request 100 times
          val commands =
            (1 to 100) map {
              i =>
                val command = ByteBufferCommand.Clean(null, () => false, new AtomicBoolean(false), path, GenForceSave.mmap())
                ByteBufferSweeper.recordCleanRequest(command, map)
                map(path).size shouldBe i
                command
            }

          //results in 100 requests
          val expectedSize = 100
          map(path).size shouldBe expectedSize

          commands.foldLeft(expectedSize) {
            case (expectedSize, command) =>
              map(path).size shouldBe expectedSize
              ByteBufferSweeper.recordCleanSuccessful(command, map)
              if (expectedSize == 1)
                map.get(path) shouldBe empty
              else
                map(path).size shouldBe (expectedSize - 1)

              expectedSize - 1
          }

          //at the end when all records are cleaned the map is set to empty.
          map shouldBe empty
      }
    }
  }

  "clean ByteBuffer" should {
    "initialise cleaner" in {
      CoreTestSweeper {
        implicit sweeper =>

          val path = genFilePath()
          val file = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)
          val buffer = file.map(MapMode.READ_WRITE, 0, 1000)

          val forceSave = GenForceSave.mmap()
          val alreadyForced = randomBoolean()
          val forced = new AtomicBoolean(alreadyForced)

          implicit val applier: ForceSaveApplier =
            if (alreadyForced)
              mock[ForceSaveApplier] //mock it so it not get invoked
            else
              ForceSaveApplier.On

          val command = ByteBufferCommand.Clean(buffer, () => false, forced, path, forceSave)

          val cleanResult = ByteBufferSweeper.initCleanerAndPerformClean(ByteBufferSweeper.State.initial(), buffer, command)
          cleanResult shouldBe a[IO.Right[_, _]]
          cleanResult.get.cleaner shouldBe defined

          if (alreadyForced)
            forced.get() shouldBe true
          else if (forceSave.enabledBeforeClean)
            forced.get() shouldBe true
          else
            forced.get() shouldBe false

          Effect.exists(path) shouldBe true
          Effect.delete(path)
          Effect.exists(path) shouldBe false
      }
    }
  }

  /**
   * These tests are slow because [[ByteBufferSweeperActor]] is a timer actor.
   */

  "clean and delete" when {

    "deleteFile" when {
      "delete after clean" in {
        CoreTestSweeper {
          implicit sweeper =>
            import sweeper._

            val filePath = genFilePath()
            val folderPath = filePath.getParent

            val file = FileChannel.open(filePath, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)
            val buffer = file.map(MapMode.READ_WRITE, 0, 1000)

            val forceSave = GenForceSave.mmap()
            val alreadyForced = randomBoolean()
            val forced = new AtomicBoolean(alreadyForced)

            implicit val applier: ForceSaveApplier =
              if (alreadyForced)
                mock[ForceSaveApplier] //mock it so it not get invoked
              else
                ForceSaveApplier.On

            //clean first
            cleaner.actor() send ByteBufferCommand.Clean(buffer, () => false, forced, filePath, forceSave)(applier)
            //and then delete
            cleaner.actor() send ByteBufferCommand.DeleteFile(filePath)

            //file is eventually deleted but the folder is not deleted
            eventual(2.minutes) {
              Effect.exists(filePath) shouldBe false
              Effect.exists(folderPath) shouldBe true
            }

            if (alreadyForced)
              forced.get() shouldBe true
            else if (forceSave.enabledBeforeClean)
              forced.get() shouldBe true
            else
              forced.get() shouldBe false

            //state should be cleared
            cleaner.actor().ask(ByteBufferCommand.IsAllClean[Unit]).await(1.minute)
        }
      }

      "delete before clean" in {
        CoreTestSweeper {
          implicit sweeper =>
            import sweeper._

            val filePath = genFilePath()
            val folderPath = filePath.getParent

            val file = FileChannel.open(filePath, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)
            val buffer = file.map(MapMode.READ_WRITE, 0, 1000)

            val forceSave = GenForceSave.mmap()
            val alreadyForced = randomBoolean()
            val forced = new AtomicBoolean(alreadyForced)

            implicit val applier: ForceSaveApplier =
              if (alreadyForced)
                mock[ForceSaveApplier] //mock it so it not get invoked
              else
                ForceSaveApplier.On

            //delete first this will result is delete reschedule on windows.
            cleaner.actor() send ByteBufferCommand.DeleteFile(filePath)
            cleaner.actor() send ByteBufferCommand.Clean(buffer, () => false, forced, filePath, forceSave)(applier)

            //file is eventually deleted but the folder is not deleted
            eventual(2.minutes) {
              Effect.exists(filePath) shouldBe false
              Effect.exists(folderPath) shouldBe true
            }

            if (alreadyForced)
              forced.get() shouldBe true
            else if (forceSave.enabledBeforeClean)
              forced.get() shouldBe true
            else
              forced.get() shouldBe false

            //state should be cleared
            cleaner.actor().ask(ByteBufferCommand.IsAllClean[Unit]).await(1.minute)
        }
      }
    }

    "deleteFolder" when {
      "delete after clean" in {
        CoreTestSweeper {
          implicit sweeper =>
            import sweeper._

            val filePath = genFilePath()
            val folderPath = filePath.getParent

            val file = FileChannel.open(filePath, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)
            val buffer = file.map(MapMode.READ_WRITE, 0, 1000)

            val forceSave = GenForceSave.mmap()
            val alreadyForced = randomBoolean()
            val forced = new AtomicBoolean(alreadyForced)

            implicit val applier: ForceSaveApplier =
              if (alreadyForced)
                mock[ForceSaveApplier] //mock it so it not get invoked
              else
                ForceSaveApplier.On

            //clean first
            cleaner.actor() send ByteBufferCommand.Clean(buffer, () => false, forced, filePath, forceSave)(applier)
            //and then delete
            cleaner.actor() send ByteBufferCommand.DeleteFolder(folderPath, filePath)

            eventual(2.minutes) {
              Effect.exists(folderPath) shouldBe false
              Effect.exists(filePath) shouldBe false
            }

            if (alreadyForced)
              forced.get() shouldBe true
            else if (forceSave.enabledBeforeClean)
              forced.get() shouldBe true
            else
              forced.get() shouldBe false

            //state should be cleared
            cleaner.actor().ask(ByteBufferCommand.IsAllClean[Unit]).await(1.minute)
        }
      }

      "delete before clean" in {
        CoreTestSweeper {
          implicit sweeper =>
            import sweeper._

            val filePath = genFilePath()
            val folderPath = filePath.getParent

            val file = FileChannel.open(filePath, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)
            val buffer = file.map(MapMode.READ_WRITE, 0, 1000)

            val forceSave = GenForceSave.mmap()
            val alreadyForced = randomBoolean()
            val forced = new AtomicBoolean(alreadyForced)

            implicit val applier: ForceSaveApplier =
              if (alreadyForced)
                mock[ForceSaveApplier] //mock it so it not get invoked
              else
                ForceSaveApplier.On

            //delete first this will result is delete reschedule on windows.
            cleaner.actor() send ByteBufferCommand.DeleteFolder(folderPath, filePath)
            cleaner.actor() send ByteBufferCommand.Clean(buffer, () => false, forced, filePath, forceSave)(applier)

            eventual(2.minutes) {
              Effect.exists(folderPath) shouldBe false
              Effect.exists(filePath) shouldBe false
            }

            if (alreadyForced)
              forced.get() shouldBe true
            else if (forceSave.enabledBeforeClean)
              forced.get() shouldBe true
            else
              forced.get() shouldBe false

            //state should be cleared
            cleaner.actor().ask(ByteBufferCommand.IsAllClean[Unit]).await(1.minute)
        }
      }
    }

    "IsClean" should {
      "return true if ByteBufferCleaner is empty" in {
        CoreTestSweeper {
          implicit sweeper =>

            implicit val cleaner: ByteBufferSweeperActor = ByteBufferSweeper(0, 1.seconds).sweep()

            (cleaner.actor() ask ByteBufferCommand.IsClean(Paths.get("somePath"))).await(1.minute) shouldBe true

            cleaner.actor().terminate[Glass]()
        }
      }

      "return true if ByteBufferCleaner has cleaned the file" in {
        CoreTestSweeper {
          implicit sweeper =>

            implicit val cleaner: ByteBufferSweeperActor = ByteBufferSweeper(messageReschedule = 2.seconds).sweep()

            val filePath = genFilePath()

            val file = FileChannel.open(filePath, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)
            val buffer = file.map(MapMode.READ_WRITE, 0, 1000)
            Effect.exists(filePath) shouldBe true

            val forceSave = GenForceSave.mmap()
            val alreadyForced = randomBoolean()
            val forced = new AtomicBoolean(alreadyForced)

            implicit val applier: ForceSaveApplier =
              if (alreadyForced)
                mock[ForceSaveApplier] //mock it so it not get invoked
              else
                ForceSaveApplier.On

            val hasReference = new AtomicBoolean(true)
            //clean will get rescheduled first.
            cleaner.actor() send ByteBufferCommand.Clean(buffer, hasReference.get, forced, filePath, forceSave)
            //since this is the second message and clean is rescheduled this will get processed.
            (cleaner.actor() ask ByteBufferCommand.IsClean(filePath)).await(10.seconds) shouldBe false

            hasReference.set(false)
            //eventually clean is executed
            eventual(5.seconds) {
              (cleaner.actor() ask ByteBufferCommand.IsClean(filePath)).await(10.seconds) shouldBe true
            }

            if (alreadyForced)
              forced.get() shouldBe true
            else if (forceSave.enabledBeforeClean)
              forced.get() shouldBe true
            else
              forced.get() shouldBe false

            cleaner.actor().isEmpty shouldBe true
        }
      }

      "return true if ByteBufferCleaner has cleaned and delete the file" in {
        CoreTestSweeper {
          implicit sweeper =>
            import sweeper._

            def sendRandomRequests(fileNumber: Int): Path = {
              val filePath = genTestDirectory().resolve(s"$fileNumber.test").sweep()
              val file = FileChannel.open(filePath, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)
              val buffer = file.map(MapMode.READ_WRITE, 0, 1000)

              val forceSave = GenForceSave.mmap()
              val alreadyForced = randomBoolean()
              val forced = new AtomicBoolean(alreadyForced)

              implicit val applier: ForceSaveApplier = ForceSaveApplier.On

              runThis(randomIntMax(10) max 1) {
                Seq(
                  () => cleaner.actor() send ByteBufferCommand.Clean(buffer, () => false, forced, filePath, forceSave)(applier),
                  () => cleaner.actor() send ByteBufferCommand.DeleteFolder(filePath, filePath)
                ).runThisRandomly()
              }

              eventual(10.seconds) {
                if (alreadyForced)
                  forced.get() shouldBe true
                else if (forceSave.enabledBeforeClean)
                  forced.get() shouldBe true
                else
                  forced.get() shouldBe false
              }

              //also randomly terminate
              if (Random.nextDouble() < 0.0001)
                cleaner.actor().terminate()

              filePath
            }

            sendRandomRequests(0)

            val paths = (1 to 100) map (i => sendRandomRequests(i))

            eventual(2.minutes) {
              //ByteBufferCleaner is a timer Actor with 5.seconds interval so await enough
              //seconds for the Actor to process stashed request/command.
              (cleaner.actor() ask ByteBufferCommand.IsAllClean[Unit]).await(30.seconds) shouldBe true
            }

            //execute all pending Delete commands.
            cleaner.actor().receiveAllForce[Glass, Unit](_ => ())

            //there might me some delete messages waiting to be scheduled.
            eventual(1.minute) {
              paths.forall(Effect.notExists) shouldBe true
            }
        }
      }
    }

    "IsTerminatedAndCleaned" when {
      "ByteBufferCleaner is empty" in {
        CoreTestSweeper {
          implicit sweeper =>

            implicit val cleaner: ByteBufferSweeperActor = ByteBufferSweeper(messageReschedule = 2.seconds).sweep()

            cleaner.actor().terminate()
            cleaner.actor().isTerminated shouldBe true

            //its terminates and there are no clean commands so this returns true.
            (cleaner.actor() ask ByteBufferCommand.IsTerminated[Unit]).await(2.seconds) shouldBe true
        }
      }

      "return true if ByteBufferCleaner has cleaned and delete the file" in {
        CoreTestSweeper {
          implicit sweeper =>
            import sweeper._

            def sendRandomRequests(): Path = {
              val filePath = genFilePath()
              val file = FileChannel.open(filePath, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)
              val buffer = file.map(MapMode.READ_WRITE, 0, 1000)

              val forceSave = GenForceSave.mmap()
              val alreadyForced = randomBoolean()
              val forced = new AtomicBoolean(alreadyForced)

              implicit val applier: ForceSaveApplier =
                if (alreadyForced)
                  mock[ForceSaveApplier] //mock it so it not get invoked
                else
                  ForceSaveApplier.On

              //randomly submit clean and delete in any order and random number of times.
              runThis(randomIntMax(100) max 1) {
                Seq(
                  () => runThis(randomIntMax(10) max 1)(cleaner.actor() send ByteBufferCommand.Clean(buffer, () => false, forced, filePath, forceSave)(forceSaveApplier)),
                  () => runThis(randomIntMax(10) max 1)(cleaner.actor() send ByteBufferCommand.DeleteFolder(filePath, filePath))
                ).runThisRandomly()
              }

              eventual(10.seconds) {
                if (alreadyForced)
                  forced.get() shouldBe true
                else if (forceSave.enabledBeforeClean)
                  forced.get() shouldBe true
                else
                  forced.get() shouldBe false
              }

              filePath
            }

            sendRandomRequests()

            val paths = (1 to 100) map (_ => sendRandomRequests())

            //execute all pending Delete commands.
            eventual(1.minute) {
              cleaner.actor().terminateAndRecover[Future, Unit](_ => ()).await(1.minute)
            }

            eventual(1.minute) {
              (cleaner.actor() ask ByteBufferCommand.IsTerminated[Unit]).await(2.seconds) shouldBe true
            }

            //there might be some delete messages waiting to be scheduled.
            eventual(1.minute) {
              paths.forall(Effect.notExists) shouldBe true
            }
        }
      }
    }
  }
}
