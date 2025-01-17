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

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import swaydb.slice.Slice
import swaydb.slice.SliceTestKit._
import swaydb.testkit.RunThis._
import swaydb.testkit.TestKit._

class CRC32Spec extends AnyFlatSpec {

  it should "apply CRC on bytes" in {
    runThis(100.times) {
      val bytes = genBytesSlice(randomIntMax(100) + 1)
      CRC32.forBytes(bytes) should be >= 1L
    }
  }

  it should "return 0 for empty bytes" in {
    CRC32.forBytes(Slice.emptyBytes) shouldBe 0L
  }
}
