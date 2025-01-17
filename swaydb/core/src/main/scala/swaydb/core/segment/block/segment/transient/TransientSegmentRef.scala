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

package swaydb.core.segment.block.segment.transient

import swaydb.core.segment.block.binarysearch.{BinarySearchIndexBlock, BinarySearchIndexBlockOffset}
import swaydb.core.segment.block.bloomfilter.{BloomFilterBlock, BloomFilterBlockOffset}
import swaydb.core.segment.block.hashindex.{HashIndexBlock, HashIndexBlockOffset}
import swaydb.core.segment.block.reader.UnblockedReader
import swaydb.core.segment.block.segment.footer.SegmentFooterBlock
import swaydb.core.segment.block.sortedindex.{SortedIndexBlock, SortedIndexBlockOffset, SortedIndexBlockState}
import swaydb.core.segment.block.values.{ValuesBlock, ValuesBlockOffset}
import swaydb.core.util.MinMax
import swaydb.slice.{MaxKey, Slice, SliceOption}

import scala.concurrent.duration.Deadline

class TransientSegmentRef( //key info
                           val minKey: Slice[Byte],
                           val maxKey: MaxKey[Slice[Byte]],
                           val functionMinMax: Option[MinMax[Slice[Byte]]],
                           val nearestDeadline: Option[Deadline],
                           val createdInLevel: Int,
                           //counts
                           val updateCount: Int,
                           val rangeCount: Int,
                           val putCount: Int,
                           val putDeadlineCount: Int,
                           val keyValueCount: Int,
                           //values
                           val valuesBlockHeader: SliceOption[Byte],
                           val valuesBlock: SliceOption[Byte],
                           val valuesUnblockedReader: Option[UnblockedReader[ValuesBlockOffset, ValuesBlock]],
                           //sortedIndex
                           val sortedIndexClosedState: SortedIndexBlockState,
                           val sortedIndexBlockHeader: Slice[Byte],
                           val sortedIndexBlock: Slice[Byte],
                           val sortedIndexUnblockedReader: Option[UnblockedReader[SortedIndexBlockOffset, SortedIndexBlock]],
                           //hashIndex
                           val hashIndexBlockHeader: SliceOption[Byte],
                           val hashIndexBlock: SliceOption[Byte],
                           val hashIndexUnblockedReader: Option[UnblockedReader[HashIndexBlockOffset, HashIndexBlock]],
                           //binarySearch
                           val binarySearchIndexBlockHeader: SliceOption[Byte],
                           val binarySearchIndexBlock: SliceOption[Byte],
                           val binarySearchUnblockedReader: Option[UnblockedReader[BinarySearchIndexBlockOffset, BinarySearchIndexBlock]],
                           //bloomFilter
                           val bloomFilterBlockHeader: SliceOption[Byte],
                           val bloomFilterBlock: SliceOption[Byte],
                           val bloomFilterUnblockedReader: Option[UnblockedReader[BloomFilterBlockOffset, BloomFilterBlock]],
                           //footer
                           val footerBlock: Slice[Byte]) {

  val (segmentBytesWithoutHeader, segmentSizeWithoutHeader): (Slice[Slice[Byte]], Int) = {
    val allBytes = Slice.allocate[Slice[Byte]](12)
    var segmentSize = 0

    /**
     * VALUES BLOCK
     */
    valuesBlockHeader foreachC {
      valuesBlockHeader =>
        allBytes add valuesBlockHeader
        segmentSize += valuesBlockHeader.size
    }

    valuesBlock foreachC {
      valuesBlock =>
        allBytes add valuesBlock
        segmentSize += valuesBlock.size
    }

    /**
     * SORTED-INDEX BLOCK
     */
    allBytes add sortedIndexBlockHeader
    segmentSize += sortedIndexBlockHeader.size

    allBytes add sortedIndexBlock
    segmentSize += sortedIndexBlock.size

    /**
     * HashIndex
     */
    hashIndexBlockHeader foreachC {
      hashIndexBlockHeader =>
        allBytes add hashIndexBlockHeader
        segmentSize += hashIndexBlockHeader.size
    }

    hashIndexBlock foreachC {
      hashIndexBlock =>
        allBytes add hashIndexBlock
        segmentSize += hashIndexBlock.size
    }

    /**
     * Binary search index
     */
    binarySearchIndexBlockHeader foreachC {
      binarySearchIndexBlockHeader =>
        allBytes add binarySearchIndexBlockHeader
        segmentSize += binarySearchIndexBlockHeader.size
    }

    binarySearchIndexBlock foreachC {
      binarySearchIndexBlock =>
        allBytes add binarySearchIndexBlock
        segmentSize += binarySearchIndexBlock.size
    }

    /**
     * BoomFilter
     */
    bloomFilterBlockHeader foreachC {
      bloomFilterBlockHeader =>
        allBytes add bloomFilterBlockHeader
        segmentSize += bloomFilterBlockHeader.size
    }

    bloomFilterBlock foreachC {
      bloomFilterBlock =>
        allBytes add bloomFilterBlock
        segmentSize += bloomFilterBlock.size
    }

    /**
     * Footer
     */
    allBytes add footerBlock
    segmentSize += footerBlock.size

    (allBytes, segmentSize)
  }

  //If sortedIndexUnblockedReader is defined then caching is enabled
  //so read footer block.

  val footerUnblocked: Option[SegmentFooterBlock] =
    if (sortedIndexUnblockedReader.isDefined)
      Some(
        SegmentFooterBlock.readCRCPassed(
          footerStartOffset = 0,
          footerSize = footerBlock.size,
          footerBytes = footerBlock
        )
      )
    else
      None

  def flattenSegmentBytesWithoutHeader: Slice[Byte] = {
    val slice = Slice.allocate[Byte](segmentSizeWithoutHeader)
    segmentBytesWithoutHeader foreach (slice addAll _)
    assert(slice.isFull)
    slice
  }
}
