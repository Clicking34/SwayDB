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

package swaydb.core.log.applied

import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import swaydb.config.CoreConfigTestKit._
import swaydb.config.MMAP
import swaydb.core._
import swaydb.core.CoreTestSweeper._
import swaydb.core.file.CoreFileTestKit._
import swaydb.core.log.LogEntry
import swaydb.core.log.serialiser._
import swaydb.core.log.LogTestKit._
import swaydb.serializers._
import swaydb.serializers.Default._
import swaydb.slice.Slice
import swaydb.slice.order.KeyOrder
import swaydb.testkit.RunThis._
import swaydb.testkit.TestKit._
import swaydb.utils.StorageUnits._
import swaydb.effect.EffectTestKit._

class AppliedFunctionsLogSpec extends AnyWordSpec {

  implicit val keyOrder: KeyOrder[Slice[Byte]] = KeyOrder.default

  implicit val functionsEntryWriter = KeyLogEntryWriter.KeyPutLogEntryWriter
  implicit val functionsEntryReader = KeyLogEntryReader.KeyLogEntryReader

  "initialise and reopen" in {
    CoreTestSweeper.repeat(10.times) {
      implicit sweeper =>
        import sweeper._

        val mapResult =
          AppliedFunctionsLog(
            dir = genDirPath(),
            fileSize = randomIntMax(1.kb) max 1,
            mmap = MMAP.randomForLog()
          )

        //start successful
        mapResult.result.get shouldBe (())

        val map = mapResult.item.sweep()

        //write random functionIds
        val functionIds =
          (1 to (randomIntMax(1000) max 10)) map {
            i =>
              val functionId = randomString()
              map.writeSync(LogEntry.Put(functionId, Slice.Null)) shouldBe true
              functionId
          }

        //should contain
        functionIds foreach {
          functionId =>
            map.cache.skipList.contains(functionId) shouldBe true
        }

        //randomly reopening results in the same skipList
        functionIds.foldLeft(map.reopen) {
          case (reopened, functionId) =>
            reopened.cache.skipList.size shouldBe functionIds.size
            reopened.cache.skipList.contains(functionId) shouldBe true

            if (randomBoolean())
              reopened
            else
              reopened.reopen.sweep()
        }
    }
  }
}
