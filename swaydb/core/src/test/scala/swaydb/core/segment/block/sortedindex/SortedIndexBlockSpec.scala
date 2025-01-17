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

package swaydb.core.segment.block.sortedindex

import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import swaydb.{Benchmark, Compression}
import swaydb.config.{PrefixCompression, UncompressedBlockInfo}
import swaydb.config.compression.{LZ4Compressor, LZ4Decompressor, LZ4Instance}
import swaydb.config.CoreConfigTestKit._
import swaydb.core.compression.CompressionTestKit._
import swaydb.core.segment.block.Block
import swaydb.core.segment.block.reader.{BlockRefReader, UnblockedReader}
import swaydb.core.segment.block.values.{ValuesBlock, ValuesBlockConfig, ValuesBlockOffset}
import swaydb.core.segment.data.Persistent
import swaydb.core.segment.data.merge.stats.MergeStats
import swaydb.core.segment.data.KeyValueTestKit._
import swaydb.core.segment.io.SegmentReadIO
import swaydb.core.segment.SegmentTestKit._
import swaydb.core.segment.block.SegmentBlockTestKit._
import swaydb.core.segment.TestCoreFunctionStore
import swaydb.effect.EffectTestKit._
import swaydb.slice.order.KeyOrder
import swaydb.slice.Slice
import swaydb.testkit.RunThis._
import swaydb.testkit.TestKit._

import scala.collection.mutable.ListBuffer
import scala.collection.parallel.CollectionConverters._

class SortedIndexBlockSpec extends AnyWordSpec {

  private implicit val order: KeyOrder[Slice[Byte]] = KeyOrder.default
  private implicit def segmentIO: SegmentReadIO = SegmentReadIO.random
  private implicit val testFunctionStore: TestCoreFunctionStore = TestCoreFunctionStore()

  "Config" should {
    "disable prefixCompression when normalise defined" in {
      runThis(100.times) {
        val prefixCompression =
          PrefixCompression.On(
            keysOnly = randomBoolean(),
            interval = randomPrefixCompressionInterval()
          )

        //test via User created object.
        val configFromUserConfig =
          SortedIndexBlockConfig(
            swaydb.config.SortedIndex.On(
              prefixCompression = prefixCompression,
              enablePositionIndex = randomBoolean(),
              optimiseForReverseIteration = randomBoolean(),
              blockIOStrategy = _ => genIOStrategy(),
              compressions = _ => eitherOne(Seq.empty, Seq(Compression.LZ4((LZ4Instance.Fastest, LZ4Compressor.Fast(Int.MinValue)), (LZ4Instance.Fastest, LZ4Decompressor.Fast))))
            )
          )

        runThis(100.times) {
          val index = randomIntMax(1000)
          configFromUserConfig.shouldPrefixCompress(index) shouldBe prefixCompression.interval.shouldCompress(index)
        }

        configFromUserConfig.normaliseIndex shouldBe false

        //internal creation
        val internalConfig =
          SortedIndexBlockConfig(
            ioStrategy = _ => genIOStrategy(),
            //prefix compression is enabled, so normaliseIndex even though true will set to false in the Config.
            shouldPrefixCompress = prefixCompression.interval.shouldCompress,
            prefixCompressKeysOnly = randomBoolean(),
            enableAccessPositionIndex = randomBoolean(),
            optimiseForReverseIteration = randomBoolean(),
            normaliseIndex = true,
            compressions = _ => randomCompressions(),
            enablePrefixCompression = randomBoolean()
          )

        internalConfig.enablePrefixCompression shouldBe false
        internalConfig.normaliseIndex shouldBe true
      }
    }

    "normalise if prefix compression is disabled" in {
      runThis(100.times) {
        val prefixCompression = PrefixCompression.Off(true)

        //use created config
        val configFromUserConfig =
          SortedIndexBlockConfig(
            swaydb.config.SortedIndex.On(
              prefixCompression = prefixCompression,
              enablePositionIndex = randomBoolean(),
              optimiseForReverseIteration = randomBoolean(),
              blockIOStrategy = _ => genIOStrategy(),
              compressions = _ => eitherOne(Seq.empty, Seq(Compression.LZ4((LZ4Instance.Fastest, LZ4Compressor.Fast(Int.MinValue)), (LZ4Instance.Fastest, LZ4Decompressor.Fast))))
            )
          )

        runThis(100.times) {
          val index = randomIntMax(1000)
          configFromUserConfig.shouldPrefixCompress(index) shouldBe false
        }

        configFromUserConfig.normaliseIndex shouldBe true

        //internal creation
        val internalConfig =
          SortedIndexBlockConfig(
            ioStrategy = _ => genIOStrategy(),
            //prefix compression is disabled, normaliseIndex will always return true.
            shouldPrefixCompress = _ => false,
            prefixCompressKeysOnly = randomBoolean(),
            enableAccessPositionIndex = randomBoolean(),
            optimiseForReverseIteration = randomBoolean(),
            normaliseIndex = true,
            compressions = _ => randomCompressions(),
            enablePrefixCompression = true
          )

        internalConfig.enablePrefixCompression shouldBe false
        internalConfig.normaliseIndex shouldBe true
      }
    }
  }

  "init" should {
    "initialise index" in {
      runThis(100.times, log = true) {
        val sortedIndexConfig = SortedIndexBlockConfig.random
        val valuesConfig = ValuesBlockConfig.random
        val keyValues = Benchmark("Generating key-values")(
          MergeStats
            .persistentBuilder(randomizedKeyValues(randomIntMax(1000) max 1))
            .close(
              hasAccessPositionIndex = sortedIndexConfig.enableAccessPositionIndex,
              optimiseForReverseIteration = sortedIndexConfig.optimiseForReverseIteration
            )
        )

        val state = SortedIndexBlock.init(keyValues, valuesConfig, sortedIndexConfig)

        val uncompressedBlockInfo = UncompressedBlockInfo(keyValues.maxSortedIndexSize)
        val compressions = sortedIndexConfig.compressions(uncompressedBlockInfo)
        //just check for non-empty. Tests uses random so they result will always be different
        state.compressions(uncompressedBlockInfo).nonEmpty shouldBe compressions.nonEmpty
        state.enableAccessPositionIndex shouldBe sortedIndexConfig.enableAccessPositionIndex
        state.header shouldBe null
        state.compressibleBytes.allocatedSize should be > 1 //should have size more than the header bytes.
      }
    }
  }

  "write, close, readAll & get" in {
    runThis(100.times, log = true) {
      val sortedIndexConfig = SortedIndexBlockConfig.random
      val valuesConfig = ValuesBlockConfig.random
      val stats =
        Benchmark("Generating key-values") {
          MergeStats
            .persistentBuilder(randomizedKeyValues(randomIntMax(1000) max 1))
            .close(
              hasAccessPositionIndex = sortedIndexConfig.enableAccessPositionIndex,
              optimiseForReverseIteration = sortedIndexConfig.optimiseForReverseIteration
            )
        }

      val sortedIndex = SortedIndexBlock.init(stats, valuesConfig, sortedIndexConfig)
      val values = ValuesBlock.init(stats, valuesConfig, sortedIndex.builder)

      val keyValues = stats.keyValues

      keyValues foreach {
        keyValue =>
          SortedIndexBlock.write(keyValue, sortedIndex)
          values foreachS {
            valuesBlock =>
              ValuesBlock.write(keyValue, valuesBlock)
          }
      }

      //      println(s"sortedIndex.underlyingArraySize: ${sortedIndex.bytes.underlyingArraySize}")
      //      println(s"sortedIndex.size               : ${sortedIndex.bytes.size}")
      //      println(s"sortedIndex.extra              : ${sortedIndex.bytes.underlyingArraySize - sortedIndex.bytes.size}")
      //      println

      val closedSortedIndexBlock = SortedIndexBlock.close(sortedIndex)
      val ref = BlockRefReader[SortedIndexBlockOffset](closedSortedIndexBlock.blockBytes)
      val header = Block.readHeader(ref.copy())
      val sortedIndexBlock = SortedIndexBlock.read(header)

      val valuesBlockReader: UnblockedReader[ValuesBlockOffset, ValuesBlock] =
        values mapS {
          valuesBlock =>
            val closedState = ValuesBlock.close(valuesBlock)
            Block.unblock[ValuesBlockOffset, ValuesBlock](closedState.blockBytes)
        } orNull

      val expectsCompressions = sortedIndexConfig.compressions(UncompressedBlockInfo(sortedIndex.compressibleBytes.size)).nonEmpty
      sortedIndexBlock.compressionInfo.isSomeS shouldBe expectsCompressions
      sortedIndexBlock.enableAccessPositionIndex shouldBe sortedIndexConfig.enableAccessPositionIndex
      if (!sortedIndexConfig.enablePrefixCompression) sortedIndexBlock.hasPrefixCompression shouldBe false
      sortedIndexBlock.headerSize should be > 0
      sortedIndexBlock.offset.start shouldBe header.headerSize
      sortedIndexBlock.normalised shouldBe sortedIndexConfig.normaliseIndex

      if (sortedIndexConfig.normaliseIndex)
        sortedIndex.indexEntries.size shouldBe keyValues.size
      else
        sortedIndex.indexEntries shouldBe empty

      val compressibleSortedIndexReader = Block.unblock(ref.copy())
      val cacheableSortedIndexReader = SortedIndexBlock.unblockedReader(closedSortedIndexBlock)

      Seq(compressibleSortedIndexReader, cacheableSortedIndexReader) foreach {
        sortedIndexReader =>
          //values are not required for this test. Create an empty reader.
          /**
           * TEST - READ ALL
           */
          val readAllKeyValues = SortedIndexBlock.toSlice(keyValues.size, sortedIndexReader, valuesBlockReader)
          keyValues shouldBe readAllKeyValues

          /**
           * TEST - Iterator
           */
          val keyValuesIterator = SortedIndexBlock.iterator(sortedIndexReader, valuesBlockReader)
          keyValuesIterator.toList shouldBe readAllKeyValues

          /**
           * TEST - READ ONE BY ONE
           */
          val searchedKeyValues = ListBuffer.empty[Persistent]
          keyValues.foldLeft(Option.empty[Persistent]) {
            case (previous, keyValue) =>
              val searchedKeyValue = SortedIndexBlock.seekAndMatch(keyValue.key, previous.getOrElse(Persistent.Null), sortedIndexReader, valuesBlockReader).getS
              searchedKeyValue.key shouldBe keyValue.key
              searchedKeyValues += searchedKeyValue
              //randomly set previous
              eitherOne(Some(searchedKeyValue), previous, None)
          }

          keyValues shouldBe searchedKeyValues

          /**
           * SEARCH CONCURRENTLY
           */
          searchedKeyValues.zip(keyValues).par foreach {
            case (persistent, memory) =>
              val searchedPersistent = SortedIndexBlock.seekAndMatch(persistent.key, Persistent.Null, sortedIndexReader.copy(), valuesBlockReader)
              searchedPersistent.getS shouldBe persistent
              searchedPersistent.getS shouldBe memory
          }
      }
    }
  }
}
