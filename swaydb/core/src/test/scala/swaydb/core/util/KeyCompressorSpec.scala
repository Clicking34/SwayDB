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

package swaydb.core.util

import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import swaydb.core.segment.data.KeyValueTestKit._
import swaydb.core.segment.data.Memory
import swaydb.core.segment.TestCoreFunctionStore
import swaydb.effect.IOValues._
import swaydb.serializers._
import swaydb.serializers.Default._
import swaydb.slice.{MaxKey, Slice}
import swaydb.slice.order.KeyOrder
import swaydb.testkit.RunThis._

class KeyCompressorSpec extends AnyWordSpec {

  private implicit val keyOrder: KeyOrder[Slice[Byte]] = KeyOrder.default
  private implicit val testCoreFunctionStore: TestCoreFunctionStore = TestCoreFunctionStore()

  "None, Fixed" in {
    runThis(20.times) {

      val last = randomFixedKeyValue(2)

      val (minKey, maxKey, compressedKey) =
        KeyCompressor.compress(
          head = Memory.Null,
          last = last
        )

      minKey shouldBe last.key
      maxKey shouldBe MaxKey.Fixed(last.key)

      KeyCompressor.decompress(compressedKey).runRandomIO.get shouldBe ((last.key, MaxKey.Fixed(last.key)))
    }
  }

  "Some(Fixed), Fixed" in {
    runThis(20.times) {
      val head = randomFixedKeyValue(1)
      val last = randomFixedKeyValue(2)

      val (minKey, maxKey, compressedKey) =
        KeyCompressor.compress(
          head = head,
          last = last
        )

      minKey shouldBe head.key
      maxKey shouldBe MaxKey.Fixed(last.key)

      KeyCompressor.decompress(compressedKey).runRandomIO.get shouldBe ((head.key, MaxKey.Fixed(last.key)))
    }
  }

  "None, Range" in {
    runThis(20.times) {
      val last = randomRangeKeyValue(1, 10)

      val (minKey, maxKey, compressedKey) =
        KeyCompressor.compress(
          head = Memory.Null,
          last = last
        )

      minKey shouldBe last.key
      maxKey shouldBe MaxKey.Range(last.fromKey, last.toKey)

      KeyCompressor.decompress(compressedKey).runRandomIO.get shouldBe ((last.key, MaxKey.Range(last.fromKey, last.toKey)))
    }
  }

  "Some(_), Range" in {
    runThis(20.times) {
      val head = randomPutKeyValues(1, startId = Some(0)).head
      val last = randomRangeKeyValue(100, 200)

      val (minKey, maxKey, compressedKey) =
        KeyCompressor.compress(
          head = head,
          last = last
        )

      minKey shouldBe head.key
      maxKey shouldBe MaxKey.Range(last.fromKey, last.toKey)

      KeyCompressor.decompress(compressedKey).runRandomIO.get shouldBe ((head.key, MaxKey.Range(last.fromKey, last.toKey)))
    }
  }
}
