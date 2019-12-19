/*
 * Copyright (c) 2019 Simer Plaha (@simerplaha)
 *
 * This file is a part of SwayDB.
 *
 * SwayDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SwayDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with SwayDB. If not, see <https://www.gnu.org/licenses/>.
 */

package swaydb.core.segment.format.a.block

import com.typesafe.scalalogging.LazyLogging
import swaydb.compression.CompressionInternal
import swaydb.core.data.{KeyValue, Memory, Persistent}
import swaydb.core.io.reader.Reader
import swaydb.core.segment.format.a.block.KeyMatcher.Result
import swaydb.core.segment.format.a.block.reader.UnblockedReader
import swaydb.core.segment.format.a.entry.id.KeyValueId
import swaydb.core.segment.format.a.entry.reader.EntryReader
import swaydb.core.segment.format.a.entry.writer._
import swaydb.core.segment.merge.MergeStats
import swaydb.core.util.{Bytes, FiniteDurations, MinMax}
import swaydb.data.MaxKey
import swaydb.data.config.{IOAction, IOStrategy, UncompressedBlockInfo}
import swaydb.data.order.KeyOrder
import swaydb.data.slice.Slice
import swaydb.data.util.{ByteSizeOf, Functions}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.Deadline

private[core] object SortedIndexBlock extends LazyLogging {

  val blockName = this.getClass.getSimpleName.dropRight(1)

  //use default writers
  implicit val timeWriter: TimeWriter = TimeWriter
  implicit val valueWriter: ValueWriter = ValueWriter
  implicit val deadlineWriter: DeadlineWriter = DeadlineWriter
  implicit val keyWriter: KeyWriter = KeyWriter

  implicit object SortedIndexBlockOps extends BlockOps[SortedIndexBlock.Offset, SortedIndexBlock] {
    override def updateBlockOffset(block: SortedIndexBlock, start: Int, size: Int): SortedIndexBlock =
      block.copy(offset = createOffset(start = start, size = size))

    override def createOffset(start: Int, size: Int): Offset =
      SortedIndexBlock.Offset(start = start, size = size)

    override def readBlock(header: Block.Header[Offset]): SortedIndexBlock =
      SortedIndexBlock.read(header)
  }

  object Config {
    val disabled =
      Config(
        ioStrategy = (dataType: IOAction) => IOStrategy.SynchronisedIO(cacheOnAccess = dataType.isCompressed),
        prefixCompressionResetCount = 0,
        prefixCompressKeysOnly = false,
        enableAccessPositionIndex = false,
        normaliseIndex = false,
        compressions = (_: UncompressedBlockInfo) => Seq.empty
      )

    def apply(config: swaydb.data.config.SortedKeyIndex): Config =
      config match {
        case config: swaydb.data.config.SortedKeyIndex.Enable =>
          apply(config)
      }

    def apply(enable: swaydb.data.config.SortedKeyIndex.Enable): Config =
      Config(
        enableAccessPositionIndex = enable.enablePositionIndex,
        prefixCompressionResetCount = enable.prefixCompression.resetCount max 0,
        prefixCompressKeysOnly = enable.prefixCompression.keysOnly && enable.prefixCompression.resetCount > 1,
        normaliseIndex = enable.prefixCompression.normaliseIndexForBinarySearch,
        //cannot normalise if prefix compression is enabled.
        ioStrategy = Functions.safe(IOStrategy.synchronisedStoredIfCompressed, enable.ioStrategy),
        compressions =
          Functions.safe(
            default = (_: UncompressedBlockInfo) => Seq.empty[CompressionInternal],
            function = enable.compressions(_: UncompressedBlockInfo) map CompressionInternal.apply
          )
      )

    def apply(ioStrategy: IOAction => IOStrategy,
              prefixCompressionResetCount: Int,
              prefixCompressKeysOnly: Boolean,
              enableAccessPositionIndex: Boolean,
              normaliseIndex: Boolean,
              compressions: UncompressedBlockInfo => Seq[CompressionInternal]): Config =
      new Config(
        ioStrategy = ioStrategy,
        prefixCompressionResetCount = if (normaliseIndex) 0 else prefixCompressionResetCount max 0,
        prefixCompressKeysOnly = prefixCompressKeysOnly && prefixCompressionResetCount > 1,
        enableAccessPositionIndex = enableAccessPositionIndex,
        //cannot normalise if prefix compression is enabled.
        normaliseIndex = normaliseIndex,
        compressions = compressions
      )
  }

  /**
   * Do not create [[Config]] directly. Use one of the apply functions.
   */
  class Config private(val ioStrategy: IOAction => IOStrategy,
                       val prefixCompressionResetCount: Int,
                       val prefixCompressKeysOnly: Boolean,
                       val enableAccessPositionIndex: Boolean,
                       val normaliseIndex: Boolean,
                       val compressions: UncompressedBlockInfo => Seq[CompressionInternal]) {

    def enablePrefixCompression: Boolean =
      prefixCompressionResetCount > 1

    def copy(ioStrategy: IOAction => IOStrategy = ioStrategy,
             prefixCompressionResetCount: Int = prefixCompressionResetCount,
             enableAccessPositionIndex: Boolean = enableAccessPositionIndex,
             normaliseIndex: Boolean = normaliseIndex,
             compressions: UncompressedBlockInfo => Seq[CompressionInternal] = compressions) =
    //do not use new here. Submit this to the apply function to that rules for creating the config gets applied.
      Config(
        ioStrategy = ioStrategy,
        prefixCompressionResetCount = prefixCompressionResetCount,
        enableAccessPositionIndex = enableAccessPositionIndex,
        prefixCompressKeysOnly = prefixCompressKeysOnly,
        normaliseIndex = normaliseIndex,
        compressions = compressions
      )
  }

  //IndexEntries that are used to create secondary indexes - binarySearchIndex & hashIndex
  class SecondaryIndexEntry(var indexOffset: Int, //mutable because if the bytes are normalised then this is adjust during close.
                            val mergedKey: Slice[Byte],
                            val unmergedKey: Slice[Byte],
                            val keyType: Byte)

  case class Offset(start: Int, size: Int) extends BlockOffset

  /**
   * [[State]] is mostly mutable because these vars calculate runtime stats of
   * a Segment which is used to build other blocks. Immutable version of this
   * resulted in very slow compaction because immutable compaction was creation
   * millions of temporary objects every second causing GC halts.
   */
  class State(var bytes: Slice[Byte],
              var header: Slice[Byte],
              var minKey: Slice[Byte],
              var lastKeyValue: Memory,
              var maxKey: MaxKey[Slice[Byte]],
              var smallestIndexEntrySize: Int,
              var largestIndexEntrySize: Int,
              var largestMergedKeySize: Int,
              var largestUncompressedMergedKeySize: Int,
              var entriesCount: Int,
              var prefixCompressedCount: Int,
              var nearestDeadline: Option[Deadline],
              var rangeCount: Int,
              var hasPut: Boolean,
              var hasRemoveRange: Boolean,
              var minMaxFunctionId: Option[MinMax[Slice[Byte]]],
              val enableAccessPositionIndex: Boolean,
              val compressDuplicateRangeValues: Boolean,
              val normaliseIndex: Boolean,
              val compressions: UncompressedBlockInfo => Seq[CompressionInternal],
              val secondaryIndexEntries: ListBuffer[SecondaryIndexEntry],
              val indexEntries: ListBuffer[Slice[Byte]],
              val builder: EntryWriter.Builder) {

    def blockBytes: Slice[Byte] =
      header ++ bytes

    def uncompressedPrefixCount: Int =
      entriesCount - prefixCompressedCount

    def hasPrefixCompression: Boolean =
      builder.segmentHasPrefixCompression

    def prefixCompressKeysOnly =
      builder.prefixCompressKeysOnly

    def isPreNormalised: Boolean =
      hasSameIndexSizes()

    def hasSameIndexSizes(): Boolean =
      smallestIndexEntrySize == largestIndexEntrySize

    def blockSize: Int =
      header.size + bytes.size
  }

  //sortedIndex's byteSize is not know at the time of creation headerSize includes hasCompression
  //  val headerSize = {
  //    val size =
  //      Block.headerSize(true) +
  //        ByteSizeOf.boolean + //enablePositionIndex
  //        ByteSizeOf.boolean + //normalisedForBinarySearch
  //        ByteSizeOf.boolean + //hasPrefixCompression
  //        ByteSizeOf.boolean + //prefixCompressKeysOnly
  //        ByteSizeOf.boolean + //isPreNormalised
  //        ByteSizeOf.varInt + // normalisedEntrySize
  //        ByteSizeOf.varInt // segmentMaxSortedIndexEntrySize
  //
  //    Bytes.sizeOfUnsignedInt(size) + size
  //  }

  def init(keyValues: MergeStats.Persistent[Iterable],
           valuesConfig: ValuesBlock.Config,
           sortedIndexConfig: SortedIndexBlock.Config): SortedIndexBlock.State =
    init(
      maxSize = keyValues maxSortedIndexSize sortedIndexConfig.enableAccessPositionIndex,
      compressDuplicateValues = valuesConfig.compressDuplicateValues,
      compressDuplicateRangeValues = valuesConfig.compressDuplicateRangeValues,
      sortedIndexConfig = sortedIndexConfig
    )

  def init(maxSize: Int,
           compressDuplicateValues: Boolean,
           compressDuplicateRangeValues: Boolean,
           sortedIndexConfig: SortedIndexBlock.Config): SortedIndexBlock.State =
    init(
      bytes = Slice.create[Byte](maxSize),
      compressDuplicateValues = compressDuplicateValues,
      compressDuplicateRangeValues = compressDuplicateRangeValues,
      sortedIndexConfig = sortedIndexConfig
    )

  def init(bytes: Slice[Byte],
           compressDuplicateValues: Boolean,
           compressDuplicateRangeValues: Boolean,
           sortedIndexConfig: SortedIndexBlock.Config): SortedIndexBlock.State = {
    //    bytes moveWritePosition headerSize

    val builder =
      EntryWriter.Builder(
        enablePrefixCompression = sortedIndexConfig.enablePrefixCompression,
        prefixCompressKeysOnly = sortedIndexConfig.prefixCompressKeysOnly,
        compressDuplicateValues = compressDuplicateValues,
        enableAccessPositionIndex = sortedIndexConfig.enableAccessPositionIndex,
        bytes = bytes
      )

    new State(
      bytes = bytes,
      header = null,
      minKey = null,
      maxKey = null,
      lastKeyValue = null,
      smallestIndexEntrySize = Int.MaxValue,
      largestIndexEntrySize = 0,
      largestMergedKeySize = 0,
      largestUncompressedMergedKeySize = 0,
      entriesCount = 0,
      prefixCompressedCount = 0,
      nearestDeadline = None,
      rangeCount = 0,
      hasPut = false,
      hasRemoveRange = false,
      minMaxFunctionId = None,
      enableAccessPositionIndex = sortedIndexConfig.enableAccessPositionIndex,
      compressDuplicateRangeValues = compressDuplicateRangeValues,
      normaliseIndex = sortedIndexConfig.normaliseIndex,
      compressions = sortedIndexConfig.compressions,
      secondaryIndexEntries = ListBuffer.empty,
      indexEntries = ListBuffer.empty,
      builder = builder
    )
  }

  def write(keyValue: Memory,
            state: SortedIndexBlock.State): Unit = {
    //currentWritePositionInThisSlice is used here because state.bytes can be a sub-slice.
    val positionBeforeWrite = state.bytes.currentWritePositionInThisSlice

    if (state.minKey == null)
      state.minKey = keyValue.key.unslice()

    keyValue match {
      case keyValue: Memory.Put =>
        state.hasPut = true
        state.nearestDeadline = FiniteDurations.getNearestDeadline(state.nearestDeadline, keyValue.deadline)

        EntryWriter.write(
          current = keyValue,
          builder = state.builder
        )

      case keyValue: Memory.Update =>
        EntryWriter.write(
          current = keyValue,
          builder = state.builder
        )

      case keyValue: Memory.Function =>
        state.minMaxFunctionId = Some(MinMax.minMaxFunction(keyValue, state.minMaxFunctionId))
        EntryWriter.write(
          current = keyValue,
          builder = state.builder
        )

      case keyValue: Memory.PendingApply =>
        state.minMaxFunctionId = MinMax.minMaxFunction(keyValue.applies, state.minMaxFunctionId)
        EntryWriter.write(
          current = keyValue,
          builder = state.builder
        )

      case keyValue: Memory.Remove =>
        EntryWriter.write(
          current = keyValue,
          builder = state.builder
        )

      case keyValue: Memory.Range =>
        state.minMaxFunctionId = MinMax.minMaxFunction(keyValue, state.minMaxFunctionId)
        state.hasPut = state.hasPut || keyValue.fromValue.exists(_.isPut)
        state.hasRemoveRange = state.hasRemoveRange || keyValue.rangeValue.hasRemoveMayBe
        state.rangeCount += 1
        //ranges have a special config "compressDuplicateRangeValues" for duplicate value compression.
        //the following set and resets the compressDuplicateValues the builder accordingly.
        val compressDuplicateValues = state.builder.compressDuplicateValues

        if (compressDuplicateValues && state.compressDuplicateRangeValues)
          state.builder.compressDuplicateValues = true

        EntryWriter.write(
          current = keyValue,
          builder = state.builder
        )

        //reset
        state.builder.compressDuplicateValues = compressDuplicateValues
    }

    if (state.builder.isCurrentPrefixCompressed)
      state.prefixCompressedCount += 1
    else
      state.largestUncompressedMergedKeySize = state.largestUncompressedMergedKeySize max keyValue.mergedKey.size

    //if indexEntry is normalised or prefixCompressed skip create SearchIndexEntry since they are not required.
    if (state.builder.isCurrentPrefixCompressed) {
      //skip creating indexEntry.
      state.builder.isCurrentPrefixCompressed = false
    } else {
      val entry =
        new SecondaryIndexEntry(
          indexOffset = positionBeforeWrite,
          mergedKey = keyValue.mergedKey,
          unmergedKey = keyValue.key,
          keyType = keyValue.id
        )

      state.secondaryIndexEntries += entry
    }

    if (state.normaliseIndex)
      state.indexEntries += state.bytes.slice(positionBeforeWrite, state.builder.bytes.currentWritePositionInThisSlice - 1)

    val indexEntrySize = state.bytes.currentWritePositionInThisSlice - positionBeforeWrite

    state.smallestIndexEntrySize = state.smallestIndexEntrySize min indexEntrySize
    state.largestIndexEntrySize = state.largestIndexEntrySize max indexEntrySize

    state.largestMergedKeySize = state.largestMergedKeySize max keyValue.mergedKey.size

    state.entriesCount += 1

    state.builder.previous = Some(keyValue)
  }

  private def normaliseIfRequired(state: State): Slice[Byte] =
    if (state.normaliseIndex) {
      val difference = state.largestIndexEntrySize - state.smallestIndexEntrySize
      if (difference == 0) {
        state.bytes
      } else {
        //because bytes are normalised searchIndexEntries's indexOffsets is required to be adjusted/padded so the indexOffsets require reset.
        var adjustedIndexEntryOffset = 0
        val secondaryIndexEntries = state.secondaryIndexEntries.iterator
        //temporary check.
        assert(state.indexEntries.size == state.secondaryIndexEntries.size, s"${state.indexEntries.size} != ${state.secondaryIndexEntries.size}")

        val bytesRequired = state.largestIndexEntrySize * state.entriesCount
        val bytes = Slice.create[Byte](bytesRequired)

        state.indexEntries foreach {
          entry =>
            //mutate the index offset.
            secondaryIndexEntries.next().indexOffset = adjustedIndexEntryOffset
            adjustedIndexEntryOffset += state.largestIndexEntrySize

            bytes addAll entry

            //pad any missing bytes.
            val missedBytes = state.largestIndexEntrySize - entry.size
            if (missedBytes > 0)
              bytes moveWritePosition (bytes.currentWritePosition + missedBytes) //fill in the missing bytes to maintain fixed size for each entry.
        }
        bytes
      }
    } else {
      state.bytes
    }

  def close(state: State): State = {
    val normalisedBytes: Slice[Byte] = normaliseIfRequired(state)

    val compressionResult =
      Block.compress(
        bytes = normalisedBytes,
        compressions = state.compressions(UncompressedBlockInfo(normalisedBytes.size)),
        blockName = blockName
      )

    state.bytes = compressionResult.compressedBytes getOrElse normalisedBytes

    compressionResult.headerBytes addBoolean state.enableAccessPositionIndex
    compressionResult.headerBytes addBoolean state.hasPrefixCompression
    compressionResult.headerBytes addBoolean state.prefixCompressKeysOnly
    compressionResult.headerBytes addBoolean state.normaliseIndex
    compressionResult.headerBytes addBoolean state.isPreNormalised
    compressionResult.headerBytes addUnsignedInt state.largestIndexEntrySize

    compressionResult.fixHeaderSize()

    state.maxKey =
      state.lastKeyValue match {
        case range: Memory.Range =>
          MaxKey.Range(range.fromKey.unslice(), range.toKey.unslice())

        case keyValue: Memory.Fixed =>
          MaxKey.Fixed(keyValue.key.unslice())
      }

    state.header = compressionResult.headerBytes

    state
  }

  def read(header: Block.Header[SortedIndexBlock.Offset]): SortedIndexBlock = {
    val enableAccessPositionIndex = header.headerReader.readBoolean()
    val hasPrefixCompression = header.headerReader.readBoolean()
    val prefixCompressKeysOnly = header.headerReader.readBoolean()
    val normaliseForBinarySearch = header.headerReader.readBoolean()
    val isPreNormalised = header.headerReader.readBoolean()
    val segmentMaxIndexEntrySize = header.headerReader.readUnsignedInt()

    SortedIndexBlock(
      offset = header.offset,
      enableAccessPositionIndex = enableAccessPositionIndex,
      hasPrefixCompression = hasPrefixCompression,
      prefixCompressKeysOnly = prefixCompressKeysOnly,
      normalised = normaliseForBinarySearch,
      segmentMaxIndexEntrySize = segmentMaxIndexEntrySize,
      isPreNormalised = isPreNormalised,
      headerSize = header.headerSize,
      compressionInfo = header.compressionInfo
    )
  }

  private def readKeyValue(previous: Persistent,
                           indexReader: UnblockedReader[SortedIndexBlock.Offset, SortedIndexBlock],
                           valuesReader: Option[UnblockedReader[ValuesBlock.Offset, ValuesBlock]]): Persistent =
    readKeyValue(
      keySizeOption = Some(previous.nextKeySize),
      sortedIndexReader = indexReader moveTo previous.nextIndexOffset,
      valuesReader = valuesReader,
      previous = Some(previous)
    )

  private def readKeyValue(fromPosition: Int,
                           indexReader: UnblockedReader[SortedIndexBlock.Offset, SortedIndexBlock],
                           valuesReader: Option[UnblockedReader[ValuesBlock.Offset, ValuesBlock]]): Persistent =
    readKeyValue(
      keySizeOption = None,
      sortedIndexReader = indexReader moveTo fromPosition,
      valuesReader = valuesReader,
      previous = None
    )

  /**
   * Pre-requisite: The position of the index on the reader should be set.
   */
  private def readKeyValue(keySizeOption: Option[Int],
                           sortedIndexReader: UnblockedReader[SortedIndexBlock.Offset, SortedIndexBlock],
                           valuesReader: Option[UnblockedReader[ValuesBlock.Offset, ValuesBlock]],
                           previous: Option[Persistent]): Persistent = {
    val positionBeforeRead = sortedIndexReader.getPosition

    //try reading entry bytes within one seek.
    val (maxIndexSize, headerInteger, indexEntryReader) =
      keySizeOption match {
        case Some(keySize) if keySize > 0 =>
          sortedIndexReader skip Bytes.sizeOfUnsignedInt(keySize)

          val indexSize =
            if (sortedIndexReader.block.isBinarySearchable)
              sortedIndexReader.block.segmentMaxIndexEntrySize
            else
              EntryWriter.maxEntrySize(keySize, sortedIndexReader.block.enableAccessPositionIndex)

          (indexSize, keySize, sortedIndexReader)

        case _ =>
          //if the reader has a block cache try fetching the bytes required within one seek.
          if (sortedIndexReader.block.isBinarySearchable) {
            val indexSize = sortedIndexReader.block.segmentMaxIndexEntrySize
            val bytes = sortedIndexReader.read(sortedIndexReader.block.segmentMaxIndexEntrySize + ByteSizeOf.varInt)
            val reader = Reader(bytes)
            val headerInteger = reader.readUnsignedInt()
            (indexSize, headerInteger, reader)
          } else if (sortedIndexReader.hasBlockCache) {
            //read the minimum number of bytes required for parse this indexEntry.
            val bytes = sortedIndexReader.read(ByteSizeOf.varInt)
            val (headerInteger, headerIntegerByteSize) = bytes.readUnsignedIntWithByteSize()
            //open the slice if it's a subslice,
            val openBytes = bytes.openEnd()

            val maxIndexSize = headerInteger + headerIntegerByteSize + EntryWriter.maxEntrySize(sortedIndexReader.block.enableAccessPositionIndex)

            //check if the read bytes are enough to parse the entry.
            val expectedSize = maxIndexSize + ByteSizeOf.varInt

            //if openBytes results in enough bytes to then read the open bytes only.
            if (openBytes.size >= expectedSize)
              (maxIndexSize, headerInteger, Reader(openBytes, headerIntegerByteSize))
            else
              (maxIndexSize, headerInteger, sortedIndexReader.moveTo(positionBeforeRead + headerIntegerByteSize))
          } else {
            val headerInteger = sortedIndexReader.readUnsignedInt()
            val indexSize = EntryWriter.maxEntrySize(headerInteger, sortedIndexReader.block.enableAccessPositionIndex)
            (indexSize, headerInteger, sortedIndexReader)
          }
      }

    //read all bytes for this index entry plus the next 5 bytes to fetch next index entry's size.
    val indexEntry = indexEntryReader read (maxIndexSize + ByteSizeOf.varInt)

    EntryReader.parse(
      headerInteger = headerInteger,
      indexEntry = indexEntry,
      normalisedByteSize = if (sortedIndexReader.block.normalised) sortedIndexReader.block.segmentMaxIndexEntrySize else 0,
      mightBeCompressed = sortedIndexReader.block.hasPrefixCompression,
      keyCompressionOnly = sortedIndexReader.block.prefixCompressKeysOnly,
      sortedIndexEndOffset = sortedIndexReader.offset.size - 1,
      valuesReader = valuesReader,
      indexOffset = positionBeforeRead,
      hasAccessPositionIndex = sortedIndexReader.block.enableAccessPositionIndex,
      previous = previous
    )
  }

  def readPartial(fromOffset: Int,
                  sortedIndexReader: UnblockedReader[SortedIndexBlock.Offset, SortedIndexBlock],
                  valuesReader: Option[UnblockedReader[ValuesBlock.Offset, ValuesBlock]]): Persistent.Partial = {

    sortedIndexReader moveTo fromOffset

    //try reading entry bytes within one seek.
    val (headerInteger, indexEntryReader) =
    //if the reader has a block cache try fetching the bytes required within one seek.
      if (sortedIndexReader.hasBlockCache) {
        //read the minimum number of bytes required for parse this indexEntry.
        val bytes = sortedIndexReader.read(ByteSizeOf.varInt)
        val (headerInteger, headerIntegerByteSize) = bytes.readUnsignedIntWithByteSize()
        //open the slice if it's a subslice,
        val openBytes = bytes.openEnd()

        val maxIndexSize = headerInteger + headerIntegerByteSize + KeyValueId.maxKeyValueIdByteSize

        //if openBytes results in enough bytes to then read the open bytes only.
        if (openBytes.size >= maxIndexSize)
          (headerInteger, Reader(openBytes, headerIntegerByteSize))
        else
          (headerInteger, sortedIndexReader.moveTo(fromOffset + headerIntegerByteSize))
      } else {
        val headerInteger = sortedIndexReader.readUnsignedInt()
        (headerInteger, sortedIndexReader)
      }

    EntryReader.parsePartial(
      offset = fromOffset,
      headerInteger = headerInteger,
      indexEntry = indexEntryReader,
      sortedIndex = sortedIndexReader,
      valuesReader = valuesReader
    )
  }

  def readAll(keyValueCount: Int,
              sortedIndexReader: UnblockedReader[SortedIndexBlock.Offset, SortedIndexBlock],
              valuesReader: Option[UnblockedReader[ValuesBlock.Offset, ValuesBlock]]): Slice[KeyValue] = {
    val builder = Slice.newBuilder[KeyValue](keyValueCount)

    readAll(
      keyValueCount = keyValueCount,
      sortedIndexReader = sortedIndexReader,
      valuesReader = valuesReader,
      builder = builder
    )

    builder.result
  }

  def readAll[T](keyValueCount: Int,
                 sortedIndexReader: UnblockedReader[SortedIndexBlock.Offset, SortedIndexBlock],
                 valuesReader: Option[UnblockedReader[ValuesBlock.Offset, ValuesBlock]],
                 builder: mutable.Builder[KeyValue, T]): Unit = {
    sortedIndexReader moveTo 0

    (1 to keyValueCount).foldLeft(Option.empty[Persistent]) {
      case (previousMayBe, _) =>
        val nextKeySize =
          previousMayBe map {
            previous =>
              //If previous is known, keep reading same reader
              // and set the next position of the reader to be of the next index's offset.
              sortedIndexReader moveTo previous.nextIndexOffset
              previous.nextKeySize
          }

        val next =
          readKeyValue(
            keySizeOption = nextKeySize,
            sortedIndexReader = sortedIndexReader,
            valuesReader = valuesReader,
            previous = previousMayBe
          )

        builder += next
        Some(next)
    }
  }

  def seekAndMatch(key: Slice[Byte],
                   startFrom: Option[Persistent],
                   sortedIndexReader: UnblockedReader[SortedIndexBlock.Offset, SortedIndexBlock],
                   valuesReader: Option[UnblockedReader[ValuesBlock.Offset, ValuesBlock]])(implicit order: KeyOrder[Slice[Byte]]): Option[Persistent] =
  //    if (startFrom.exists(from => order.gteq(from.key, key))) //TODO - to be removed via macros. this is for internal use only. Detects that a higher startFrom key does not get passed to this.
  //      IO.Left(swaydb.Error.Fatal("startFrom key is greater than target key."))
  //    else
    seekAndMatchToPersistent(
      matcher = KeyMatcher.Get(key),
      startFrom = startFrom,
      indexReader = sortedIndexReader,
      valuesReader = valuesReader
    )

  def matchOrSeek(key: Slice[Byte],
                  startFrom: Persistent,
                  sortedIndexReader: UnblockedReader[SortedIndexBlock.Offset, SortedIndexBlock],
                  valuesReader: Option[UnblockedReader[ValuesBlock.Offset, ValuesBlock]])(implicit ordering: KeyOrder[Slice[Byte]]): Option[Persistent] =
  //    if (ordering.gteq(startFrom.key, key)) //TODO - to be removed via macros. this is for internal use only. Detects that a higher startFrom key does not get passed to this.
  //      IO.Left(swaydb.Error.Fatal("startFrom key is greater than target key."))
  //    else
    SortedIndexBlock.matchOrSeek(
      matcher = KeyMatcher.Get(key),
      previous = startFrom,
      next = None,
      indexReader = sortedIndexReader,
      valuesReader = valuesReader
    ) match {
      case matched: KeyMatcher.Result.Matched =>
        Some(matched.result.toPersistent)

      case _: KeyMatcher.Result.AheadOrNoneOrEnd | _: KeyMatcher.Result.BehindStopped =>
        None
    }

  def searchSeekOne(key: Slice[Byte],
                    start: Persistent,
                    indexReader: UnblockedReader[SortedIndexBlock.Offset, SortedIndexBlock],
                    valuesReader: Option[UnblockedReader[ValuesBlock.Offset, ValuesBlock]])(implicit order: KeyOrder[Slice[Byte]]): Option[Persistent] =
  //    if (order.gteq(start.key, key)) //TODO - to be removed via macros. this is for internal use only. Detects that a higher startFrom key does not get passed to this.
  //      IO.Left(swaydb.Error.Fatal("startFrom key is greater than target key."))
  //    else
    seekAndMatchToPersistent(
      matcher = KeyMatcher.Get.MatchOnly(key),
      startFrom = Some(start),
      indexReader = indexReader,
      valuesReader = valuesReader
    )

  def searchHigher(key: Slice[Byte],
                   startFrom: Option[Persistent],
                   sortedIndexReader: UnblockedReader[SortedIndexBlock.Offset, SortedIndexBlock],
                   valuesReader: Option[UnblockedReader[ValuesBlock.Offset, ValuesBlock]])(implicit order: KeyOrder[Slice[Byte]]): Option[Persistent] =
  //    if (startFrom.exists(from => order.gt(from.key, key))) //TODO - to be removed via macros. this is for internal use only. Detects that a higher startFrom key does not get passed to this.
  //      IO.Left(swaydb.Error.Fatal("startFrom key is greater than target key."))
  //    else
    seekAndMatchToPersistent(
      matcher = KeyMatcher.Higher(key),
      startFrom = startFrom,
      indexReader = sortedIndexReader,
      valuesReader = valuesReader
    )

  def matchOrSeekHigher(key: Slice[Byte],
                        startFrom: Option[Persistent],
                        sortedIndexReader: UnblockedReader[SortedIndexBlock.Offset, SortedIndexBlock],
                        valuesReader: Option[UnblockedReader[ValuesBlock.Offset, ValuesBlock]])(implicit order: KeyOrder[Slice[Byte]]): Option[Persistent] =
  //    if (startFrom.exists(from => order.gt(from.key, key))) //TODO - to be removed via macros. this is for internal use only. Detects that a higher startFrom key does not get passed to this.
  //      IO.Left(swaydb.Error.Fatal("startFrom key is greater than target key."))
  //    else
    startFrom match {
      case Some(lower) =>
        SortedIndexBlock.matchOrSeek(
          matcher = KeyMatcher.Higher(key),
          previous = lower,
          next = None,
          indexReader = sortedIndexReader,
          valuesReader = valuesReader
        ) match {
          case matched: KeyMatcher.Result.Matched =>
            Some(matched.result.toPersistent)

          case _: KeyMatcher.Result.AheadOrNoneOrEnd | _: KeyMatcher.Result.BehindStopped =>
            None
        }

      case None =>
        seekAndMatchToPersistent(
          matcher = KeyMatcher.Higher(key),
          startFrom = startFrom,
          indexReader = sortedIndexReader,
          valuesReader = valuesReader
        )
    }

  def searchHigherMatchOnly(key: Slice[Byte],
                            startFrom: Persistent,
                            sortedIndexReader: UnblockedReader[SortedIndexBlock.Offset, SortedIndexBlock],
                            valuesReader: Option[UnblockedReader[ValuesBlock.Offset, ValuesBlock]])(implicit order: KeyOrder[Slice[Byte]]): Option[Persistent] =
  //    if (order.gt(startFrom.key, key)) //TODO - to be removed via macros. this is for internal use only. Detects that a higher startFrom key does not get passed to this.
  //      IO.Left(swaydb.Error.Fatal("startFrom key is greater than target key."))
  //    else
    seekAndMatchToPersistent(
      matcher = KeyMatcher.Higher.MatchOnly(key),
      startFrom = Some(startFrom),
      indexReader = sortedIndexReader,
      valuesReader = valuesReader
    )

  def seekLowerAndMatch(key: Slice[Byte],
                        startFrom: Option[Persistent],
                        sortedIndexReader: UnblockedReader[SortedIndexBlock.Offset, SortedIndexBlock],
                        valuesReader: Option[UnblockedReader[ValuesBlock.Offset, ValuesBlock]])(implicit order: KeyOrder[Slice[Byte]]): Option[Persistent] =
  //    if (startFrom.exists(from => order.gteq(from.key, key))) //TODO - to be removed via macros. this is for internal use only. Detects that a higher startFrom key does not get passed to this.
  //      IO.Left(swaydb.Error.Fatal("startFrom key is greater than target key."))
  //    else
    seekAndMatchToPersistent(
      matcher = KeyMatcher.Lower(key),
      startFrom = startFrom,
      indexReader = sortedIndexReader,
      valuesReader = valuesReader
    )

  def matchOrSeekLower(key: Slice[Byte],
                       startFrom: Option[Persistent],
                       next: Option[Persistent],
                       sortedIndexReader: UnblockedReader[SortedIndexBlock.Offset, SortedIndexBlock],
                       valuesReader: Option[UnblockedReader[ValuesBlock.Offset, ValuesBlock]])(implicit order: KeyOrder[Slice[Byte]]): Option[Persistent] =
  //    if (startFrom.exists(from => order.gteq(from.key, key)) ||
  //      next.exists(next => next match {
  //        case range: Partial.RangeT => order.lt(range.toKey, key)
  //        case fixed: Partial.Fixed => order.lt(fixed.key, key)
  //      }
  //      )
  //    ) //TODO - to be removed via macros. this is for internal use only. Detects that a higher startFrom key does not get passed to this.
  //      IO.Left(swaydb.Error.Fatal("invalid startFrom or next."))
  //    else
    startFrom match {
      case Some(lower) =>
        SortedIndexBlock.matchOrSeek(
          matcher = KeyMatcher.Lower(key),
          previous = lower,
          next = next,
          indexReader = sortedIndexReader,
          valuesReader = valuesReader
        ) match {
          case matched: KeyMatcher.Result.Matched =>
            Some(matched.result.toPersistent)

          case _: KeyMatcher.Result.AheadOrNoneOrEnd | _: KeyMatcher.Result.BehindStopped =>
            None
        }

      case None =>
        next match {
          case Some(next) =>
            SortedIndexBlock.matchOrSeek(
              previous = next,
              next = None,
              matcher = KeyMatcher.Lower(key),
              indexReader = sortedIndexReader,
              valuesReader = valuesReader
            ) match {
              case matched: KeyMatcher.Result.Matched =>
                Some(matched.result.toPersistent)

              case _: KeyMatcher.Result.AheadOrNoneOrEnd | _: KeyMatcher.Result.BehindStopped =>
                None
            }

          case None =>
            seekAndMatchToPersistent(
              matcher = KeyMatcher.Lower(key),
              startFrom = startFrom,
              indexReader = sortedIndexReader,
              valuesReader = valuesReader
            )
        }
    }

  private def seekAndMatchToPersistent(matcher: KeyMatcher,
                                       startFrom: Option[Persistent],
                                       indexReader: UnblockedReader[SortedIndexBlock.Offset, SortedIndexBlock],
                                       valuesReader: Option[UnblockedReader[ValuesBlock.Offset, ValuesBlock]]): Option[Persistent] =
    startFrom match {
      case Some(startFrom) =>
        seekAndMatchOrSeek(
          previous = startFrom,
          next = None,
          matcher = matcher,
          indexReader = indexReader,
          valuesReader = valuesReader
        ) match {
          case matched: KeyMatcher.Result.Matched =>
            Some(matched.result.toPersistent)

          case _: KeyMatcher.Result.AheadOrNoneOrEnd | _: KeyMatcher.Result.BehindStopped =>
            None
        }

      //No start from. Get the first index entry from the File and start from there.
      case None =>
        val keyValue =
          readKeyValue(
            fromPosition = 0,
            indexReader = indexReader,
            valuesReader = valuesReader
          )

        matchOrSeekToPersistent(
          previous = keyValue,
          next = None,
          matcher = matcher,
          indexReader = indexReader,
          valuesReader = valuesReader
        )
    }

  def seekAndMatchOrSeek(matcher: KeyMatcher,
                         fromOffset: Int,
                         indexReader: UnblockedReader[SortedIndexBlock.Offset, SortedIndexBlock],
                         valuesReader: Option[UnblockedReader[ValuesBlock.Offset, ValuesBlock]]): Result.Complete = {
    val persistent =
      readKeyValue(
        fromPosition = fromOffset,
        indexReader = indexReader,
        valuesReader = valuesReader
      )

    //        ////////println("matchOrSeekAndPersistent")
    matchOrSeek(
      previous = persistent,
      next = None,
      matcher = matcher,
      indexReader = indexReader,
      valuesReader = valuesReader
    )
  }

  def seekAndMatchOrSeekToPersistent(matcher: KeyMatcher,
                                     fromOffset: Int,
                                     indexReader: UnblockedReader[SortedIndexBlock.Offset, SortedIndexBlock],
                                     valuesReader: Option[UnblockedReader[ValuesBlock.Offset, ValuesBlock]]): Option[Persistent] = {
    val persistent =
      readKeyValue(
        fromPosition = fromOffset,
        indexReader = indexReader,
        valuesReader = valuesReader
      )

    //        ////////println("matchOrSeekAndPersistent")
    matchOrSeekToPersistent(
      previous = persistent,
      next = None,
      matcher = matcher,
      indexReader = indexReader,
      valuesReader = valuesReader
    )
  }

  def readAndMatch(matcher: KeyMatcher,
                   fromOffset: Int,
                   sortedIndexReader: UnblockedReader[SortedIndexBlock.Offset, SortedIndexBlock],
                   valuesReader: Option[UnblockedReader[ValuesBlock.Offset, ValuesBlock]]): KeyMatcher.Result = {
    val persistent =
      readKeyValue(
        fromPosition = fromOffset,
        indexReader = sortedIndexReader,
        valuesReader = valuesReader
      )

    matcher(
      previous = persistent,
      next = None,
      hasMore = hasMore(persistent)
    )
  }

  def read(fromOffset: Int,
           sortedIndexReader: UnblockedReader[SortedIndexBlock.Offset, SortedIndexBlock],
           valuesReader: Option[UnblockedReader[ValuesBlock.Offset, ValuesBlock]]): Persistent =
    readKeyValue(
      fromPosition = fromOffset,
      indexReader = sortedIndexReader,
      valuesReader = valuesReader
    )

  def readPreviousAndMatch(matcher: KeyMatcher,
                           next: Persistent,
                           fromOffset: Int,
                           sortedIndexReader: UnblockedReader[SortedIndexBlock.Offset, SortedIndexBlock],
                           valuesReader: Option[UnblockedReader[ValuesBlock.Offset, ValuesBlock]]): KeyMatcher.Result = {
    val persistent =
      readKeyValue(
        fromPosition = fromOffset,
        indexReader = sortedIndexReader,
        valuesReader = valuesReader
      )

    matcher(
      previous = persistent,
      next = Some(next),
      hasMore = hasMore(next)
    )
  }

  def readSeekAndMatch(matcher: KeyMatcher,
                       previous: Persistent,
                       fromOffset: Int,
                       sortedIndexReader: UnblockedReader[SortedIndexBlock.Offset, SortedIndexBlock],
                       valuesReader: Option[UnblockedReader[ValuesBlock.Offset, ValuesBlock]]): KeyMatcher.Result = {
    val persistent =
      readKeyValue(
        fromPosition = fromOffset,
        indexReader = sortedIndexReader,
        valuesReader = valuesReader
      )

    matcher(
      previous = previous,
      next = Some(persistent),
      hasMore = hasMore(persistent)
    )
  }

  def readNextKeyValue(previous: Persistent,
                       sortedIndexReader: UnblockedReader[SortedIndexBlock.Offset, SortedIndexBlock],
                       valuesReader: Option[UnblockedReader[ValuesBlock.Offset, ValuesBlock]]): Persistent =
    readKeyValue(
      previous = previous,
      indexReader = sortedIndexReader,
      valuesReader = valuesReader
    )

  def findAndMatchOrSeekMatch(matcher: KeyMatcher,
                              fromOffset: Int,
                              sortedIndex: UnblockedReader[SortedIndexBlock.Offset, SortedIndexBlock],
                              valuesReader: Option[UnblockedReader[ValuesBlock.Offset, ValuesBlock]]): KeyMatcher.Result = {
    val persistent =
      readKeyValue(
        fromPosition = fromOffset,
        indexReader = sortedIndex,
        valuesReader = valuesReader
      )

    matchOrSeek(
      previous = persistent,
      next = None,
      matcher = matcher,
      indexReader = sortedIndex,
      valuesReader = valuesReader
    )
  }

  @tailrec
  def matchOrSeek(previous: Persistent,
                  next: Option[Persistent],
                  matcher: KeyMatcher,
                  indexReader: UnblockedReader[SortedIndexBlock.Offset, SortedIndexBlock],
                  valuesReader: Option[UnblockedReader[ValuesBlock.Offset, ValuesBlock]]): KeyMatcher.Result.Complete =
    matcher(
      previous = previous,
      next = next,
      hasMore = hasMore(next getOrElse previous)
    ) match {
      case result: KeyMatcher.Result.Complete =>
        result

      case behind: KeyMatcher.Result.BehindFetchNext =>
        //        assert(previous.key.readInt() <= previousKeyValue.key.readInt())
        val readFrom = (next getOrElse behind.previous).toPersistent

        val nextNextKeyValue =
          readKeyValue(
            previous = readFrom,
            indexReader = indexReader,
            valuesReader = valuesReader
          )

        matchOrSeek(
          previous = readFrom,
          next = Some(nextNextKeyValue),
          matcher = matcher,
          indexReader = indexReader,
          valuesReader = valuesReader
        )
    }

  def seekAndMatchOrSeek(previous: Persistent,
                         next: Option[Persistent],
                         matcher: KeyMatcher,
                         indexReader: UnblockedReader[SortedIndexBlock.Offset, SortedIndexBlock],
                         valuesReader: Option[UnblockedReader[ValuesBlock.Offset, ValuesBlock]]): KeyMatcher.Result.Complete = {
    val nextNextKeyValue =
      readKeyValue(
        previous = next getOrElse previous,
        indexReader = indexReader,
        valuesReader = valuesReader
      )

    matchOrSeek(
      previous = next getOrElse previous,
      next = Some(nextNextKeyValue),
      matcher = matcher,
      indexReader = indexReader,
      valuesReader = valuesReader
    )
  }

  def matchOrSeekToPersistent(previous: Persistent,
                              next: Option[Persistent],
                              matcher: KeyMatcher,
                              indexReader: UnblockedReader[SortedIndexBlock.Offset, SortedIndexBlock],
                              valuesReader: Option[UnblockedReader[ValuesBlock.Offset, ValuesBlock]]): Option[Persistent] =
    matchOrSeek(
      previous = previous,
      next = next,
      matcher = matcher,
      indexReader = indexReader,
      valuesReader = valuesReader
    ) match {
      case matched: KeyMatcher.Result.Matched =>
        Some(matched.result.toPersistent)

      case _: KeyMatcher.Result.AheadOrNoneOrEnd | _: KeyMatcher.Result.BehindStopped =>
        None
    }

  /**
   * If key-value is read from copied HashIndex then keyValue.nextKeySize can be 0 (unknown) so always
   * use nextIndexOffset to determine is there are more key-values.
   */
  private def hasMore(keyValue: Persistent) =
    keyValue.nextIndexOffset > -1
}

private[core] case class SortedIndexBlock(offset: SortedIndexBlock.Offset,
                                          enableAccessPositionIndex: Boolean,
                                          hasPrefixCompression: Boolean,
                                          prefixCompressKeysOnly: Boolean,
                                          normalised: Boolean,
                                          isPreNormalised: Boolean,
                                          headerSize: Int,
                                          segmentMaxIndexEntrySize: Int,
                                          compressionInfo: Option[Block.CompressionInfo]) extends Block[SortedIndexBlock.Offset] {
  val isBinarySearchable =
    !hasPrefixCompression && (normalised || isPreNormalised)

  val isNotPreNormalised =
    !hasPrefixCompression && normalised && !isPreNormalised

  val hasNormalisedBytes =
    !isPreNormalised && normalised
}
