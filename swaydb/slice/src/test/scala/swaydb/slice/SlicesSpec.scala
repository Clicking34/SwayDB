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

package swaydb.slice

import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec

class SlicesSpec extends AnyWordSpec {

  "get" when {
    "empty" in {
      assertThrows[NoSuchElementException](Slices[Int](Array.empty[Slice[Int]]))
    }

    "all slices are equal" in {
      val slices = Slices(Array(Slice.range(0, 4), Slice.range(5, 9), Slice.range(10, 14)))

      slices.blockSize shouldBe 5
      slices.size shouldBe 15

      Slice.range(0, 14) foreach {
        int =>
          slices.get(int) shouldBe int
          slices.unsafeGet(int) shouldBe int
      }

      assertThrows[ArrayIndexOutOfBoundsException](slices.get(15))

      slices.toArray shouldBe Slice.range(0, 14).toArray
    }

    "it contains a small last slice" in {
      val slices = Slices(Array(Slice.range(0, 4), Slice.range(5, 9), Slice.range(10, 11)))

      slices.blockSize shouldBe 5
      slices.size shouldBe 12

      Slice.range(0, 11) foreach {
        int =>
          slices.get(int) shouldBe int
          slices.unsafeGet(int) shouldBe int
      }

      assertThrows[ArrayIndexOutOfBoundsException](slices.get(12))

      slices.toArray shouldBe Slice.range(0, 11).toArray
    }
  }

  "Take" when {
    "all slices are equal" in {
      val slices = Slices(Array(Slice.range(0, 4), Slice.range(5, 9), Slice.range(10, 14)))
      slices.take(2) shouldBe Slice.range(0, 1)
      slices.take(4) shouldBe Slice.range(0, 3)
      slices.take(5) shouldBe Slice.range(0, 4)
      slices.take(6).asInstanceOf[Slices[Int]].slices shouldBe Array(Slice.range(0, 4), Slice(5))
      slices.take(7).asInstanceOf[Slices[Int]].slices shouldBe Array(Slice.range(0, 4), Slice(5, 6))
      slices.take(8).asInstanceOf[Slices[Int]].slices shouldBe Array(Slice.range(0, 4), Slice(5, 6, 7))
      slices.take(9).asInstanceOf[Slices[Int]].slices shouldBe Array(Slice.range(0, 4), Slice(5, 6, 7, 8))
      slices.take(10).asInstanceOf[Slices[Int]].slices shouldBe Array(Slice.range(0, 4), Slice(5, 6, 7, 8, 9))
      slices.take(11).asInstanceOf[Slices[Int]].slices shouldBe Array(Slice.range(0, 4), Slice.range(5, 9), Slice(10))
      slices.take(12).asInstanceOf[Slices[Int]].slices shouldBe Array(Slice.range(0, 4), Slice.range(5, 9), Slice(10, 11))
      slices.take(13).asInstanceOf[Slices[Int]].slices shouldBe Array(Slice.range(0, 4), Slice.range(5, 9), Slice(10, 11, 12))
      slices.take(14).asInstanceOf[Slices[Int]].slices shouldBe Array(Slice.range(0, 4), Slice.range(5, 9), Slice(10, 11, 12, 13))
      slices.take(15).asInstanceOf[Slices[Int]].slices shouldBe Array(Slice.range(0, 4), Slice.range(5, 9), Slice(10, 11, 12, 13, 14))
      slices.take(20).asInstanceOf[Slices[Int]].slices shouldBe Array(Slice.range(0, 4), Slice.range(5, 9), Slice(10, 11, 12, 13, 14))
    }
  }

  "append" when {
    "slice" when {
      "empty" in {
        val leftSlices = Slices(1, 2, 3)
        val slices = leftSlices append Slice.empty[Int]
        slices shouldBe leftSlices
        //no new instance is created
        slices.hashCode() shouldBe leftSlices.hashCode()
      }

      "nonempty" in {
        val slices = Slices(1, 2, 3) append Slice(4)
        slices shouldBe Slices(1, 2, 3, 4)
        slices.slices shouldBe Array(Slice(1, 2, 3), Slice(4))
        slices.blockSize shouldBe 3
      }
    }

    "slices" when {
      //No test for "empty" because empty Slices are not possible

      "nonempty" in {
        val slices = Slices(1, 2, 3) append Slices(4)
        slices shouldBe Slices(1, 2, 3, 4)
        slices.slices shouldBe Array(Slice(1, 2, 3), Slice(4))
        slices.blockSize shouldBe 3
      }
    }
  }
}
