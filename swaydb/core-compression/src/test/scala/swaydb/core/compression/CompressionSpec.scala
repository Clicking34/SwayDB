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

package swaydb.core.compression

import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import swaydb.core.compression.CompressionTestKit._
import swaydb.serializers.Default._
import swaydb.serializers._
import swaydb.slice.Slice
import swaydb.utils.ByteSizeOf

import scala.util.Random

class CompressionSpec extends AnyWordSpec {

  private def assertSuccessfulCompression(compression: CoreCompression) = {
    val string = "12345-12345-12345-12345" * Math.abs(Random.nextInt(99) + 1)
    val bytes: Slice[Byte] = string
    val compressedBytes: Slice[Byte] = compression.compressor.compress(bytes).get
    val decompressedBytes = compression.decompressor.decompress(compressedBytes, bytes.size)
    val decompressedString = decompressedBytes.readString()
    decompressedString shouldBe string
  }

  private def assertUnsuccessfulCompression(compression: CoreCompression) = {
    val string = "12345-12345-12345-12345" * Math.abs(Random.nextInt(99) + 1)
    val bytes: Slice[Byte] = string
    compression.compressor.compress(bytes) shouldBe empty
  }

  "Compression" should {

    "successfully compress bytes" when {
      "UnCompressed" in {
        assertSuccessfulCompression(CoreCompression.UnCompressed)
      }

      "Snappy" in {
        assertSuccessfulCompression(CoreCompression.Snappy(minCompressionPercentage = 10))
      }

      "LZ4" in {
        (1 to 100) foreach {
          _ =>
            val compressor = CoreCompressor.randomLZ4(minCompressionSavingsPercent = 10)
            val decompressor = CoreDecompressor.randomLZ4()
            //            println("compressor: " + compressor)
            //            println("decompressor: " + decompressor)
            assertSuccessfulCompression(CoreCompression.LZ4(compressor, decompressor))
        }
      }
    }

    "successfully compress large byte array" in {
      val count = 10000
      val slice = Slice.allocate[Byte]((count + 1) * ByteSizeOf.long)

      val from = 1
      (from to (from + count)) map {
        long =>
          slice addAll Slice.writeLong(long)
      }

      val compressor = CoreCompressor.randomLZ4(minCompressionSavingsPercent = 20)

      def doCompression() = {
        val compressedBytes = compressor.compress(slice).get
        CoreCompressor.isCompressionSatisfied(20, compressedBytes.size, slice.size, compressor.getClass.getSimpleName) shouldBe true
        compressedBytes.size should be < slice.size
      }

      (1 to 10) foreach {
        _ =>
          doCompression()
      }
    }

    "return None" when {
      "Snappy" in {
        assertUnsuccessfulCompression(CoreCompression.Snappy(minCompressionPercentage = 100))
      }

      "LZ4" in {
        (1 to 100) foreach {
          _ =>
            val compressor = CoreCompressor.randomLZ4(minCompressionSavingsPercent = 100)
            val decompressor = CoreDecompressor.randomLZ4()
            //            println("compressor: " + compressor)
            //            println("decompressor: " + decompressor)
            assertUnsuccessfulCompression(CoreCompression.LZ4(compressor, decompressor))
        }
      }
    }

    "compress with header space" when {
      val string = "12345-12345-12345-12345" * 100
      val bytes: Slice[Byte] = string

      "lz4" in {
        (1 to 100) foreach {
          _ =>
            val compressed = CoreCompressor.randomLZ4().compress(10, bytes).get
            compressed.take(10) foreach (_ shouldBe 0.toByte)

            val decompressedBytes = CoreDecompressor.randomLZ4().decompress(compressed.drop(10), bytes.size)
            decompressedBytes shouldBe bytes
        }
      }

      "snappy" in {
        (1 to 100) foreach {
          _ =>
            val compressed = CoreCompressor.Snappy(Int.MinValue).compress(10, bytes).get
            compressed.take(10) foreach (_ shouldBe 0.toByte)

            val decompressedBytes = CoreDecompressor.Snappy.decompress(compressed.drop(10), bytes.size)
            decompressedBytes shouldBe bytes
        }
      }

      "UnCompressed" in {
        (1 to 100) foreach {
          _ =>
            val compressed = CoreCompressor.UnCompressed.compress(10, bytes).get
            compressed.take(10) foreach (_ shouldBe 0.toByte)

            val decompressedBytes = CoreDecompressor.UnCompressed.decompress(compressed.drop(10), bytes.size)
            decompressedBytes shouldBe bytes
        }
      }
    }
  }
}
