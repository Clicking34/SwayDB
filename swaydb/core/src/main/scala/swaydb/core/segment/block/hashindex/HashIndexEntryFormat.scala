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

package swaydb.core.segment.block.hashindex

import swaydb.config.IndexFormat
import swaydb.core.segment.block.reader.UnblockedReader
import swaydb.core.segment.block.sortedindex.{SortedIndexBlock, SortedIndexBlockOffset}
import swaydb.core.segment.block.values.{ValuesBlock, ValuesBlockOffset}
import swaydb.core.segment.data.Persistent.Partial
import swaydb.core.segment.data.{Memory, Persistent}
import swaydb.core.util.{Bytes, CRC32}
import swaydb.macros.MacroSealed
import swaydb.slice.{Slice, SliceMut, SliceReader}
import swaydb.utils.ByteSizeOf

private[core] sealed trait HashIndexEntryFormat {
  def id: Byte

  def isCopy: Boolean

  def isReference: Boolean = !isCopy

  def bytesToAllocatePerEntry(largestIndexOffset: Int,
                              largestMergedKeySize: Int): Int

  def readOrNull(entry: Slice[Byte],
                 hashIndexReader: UnblockedReader[HashIndexBlockOffset, HashIndexBlock],
                 sortedIndex: UnblockedReader[SortedIndexBlockOffset, SortedIndexBlock],
                 valuesOrNull: UnblockedReader[ValuesBlockOffset, ValuesBlock]): Persistent.Partial
}

private[core] object HashIndexEntryFormat {

  def apply(indexFormat: IndexFormat): HashIndexEntryFormat =
    indexFormat match {
      case IndexFormat.Reference =>
        HashIndexEntryFormat.Reference

      case IndexFormat.CopyKey =>
        HashIndexEntryFormat.CopyKey
    }

  object Reference extends HashIndexEntryFormat {
    //ids start from 1 instead of 0 to account for entries that don't allow zero bytes.
    override val id: Byte = 0.toByte

    override val isCopy: Boolean = false

    override def bytesToAllocatePerEntry(largestIndexOffset: Int,
                                         largestMergedKeySize: Int): Int =
      Bytes sizeOfUnsignedInt (largestIndexOffset + 1)

    def write(indexOffset: Int,
              mergedKey: Slice[Byte],
              keyType: Byte,
              bytes: SliceMut[Byte]): Unit =
      bytes addNonZeroUnsignedInt (indexOffset + 1)

    override def readOrNull(entry: Slice[Byte],
                            hashIndexReader: UnblockedReader[HashIndexBlockOffset, HashIndexBlock],
                            sortedIndex: UnblockedReader[SortedIndexBlockOffset, SortedIndexBlock],
                            valuesOrNull: UnblockedReader[ValuesBlockOffset, ValuesBlock]): Persistent.Partial = {
      val (possibleOffset, bytesRead) = Bytes.readUnsignedIntNonZeroWithByteSize(entry)
      //      //println(s"Key: ${key.readInt()}: read hashIndex: ${index + block.headerSize} probe: $probe, sortedIndex: ${possibleOffset - 1} = reading now!")
      if (possibleOffset == 0 || entry.existsFor(bytesRead, _ == Bytes.zero)) {
        ////println(s"Key: ${key.readInt()}: read hashIndex: ${index + block.headerSize} probe: $probe, sortedIndex: ${possibleOffset - 1}, possibleValue: $possibleOffset, containsZero: ${possibleValueWithoutHeader.take(bytesRead).exists(_ == 0)} = failed")
        null
      } else {
        SortedIndexBlock.readPartialKeyValue(
          fromOffset = possibleOffset - 1,
          sortedIndexReader = sortedIndex,
          valuesReaderOrNull = valuesOrNull
        )
      }
    }
  }

  object CopyKey extends HashIndexEntryFormat {
    override val id: Byte = 2.toByte

    override val isCopy: Boolean = true

    override def bytesToAllocatePerEntry(largestIndexOffset: Int,
                                         largestMergedKeySize: Int): Int = {
      val sizeOfLargestIndexOffset = Bytes.sizeOfUnsignedInt(largestIndexOffset)
      val sizeOfLargestKeySize = Bytes.sizeOfUnsignedInt(largestMergedKeySize)
      sizeOfLargestKeySize + largestMergedKeySize + ByteSizeOf.byte + sizeOfLargestIndexOffset + ByteSizeOf.varLong //crc
    }

    def write(indexOffset: Int,
              mergedKey: Slice[Byte],
              keyType: Byte,
              bytes: SliceMut[Byte]): Long = {
      val position = bytes.currentWritePosition
      bytes addUnsignedInt mergedKey.size
      bytes addAll mergedKey
      bytes add keyType
      bytes addUnsignedInt indexOffset
      val bytesToCRC = bytes.take(position, bytes.currentWritePosition - position)
      val crc = CRC32.forBytes(bytesToCRC)
      bytes addUnsignedLong crc

      if (bytes.get(bytes.currentWritePosition - 1) == 0) //if the last byte is 0 add one to avoid next write overwriting this entry's last byte.
        bytes add Bytes.one

      crc
    }

    override def readOrNull(entry: Slice[Byte],
                            hashIndexReader: UnblockedReader[HashIndexBlockOffset, HashIndexBlock],
                            sortedIndex: UnblockedReader[SortedIndexBlockOffset, SortedIndexBlock],
                            valuesOrNull: UnblockedReader[ValuesBlockOffset, ValuesBlock]): Persistent.Partial =
      try {
        val reader = SliceReader(entry)
        val keySize = reader.readUnsignedInt()
        val entryKey = reader.read(keySize)
        val keyType = reader.get()
        val indexOffset_ = reader.readUnsignedInt()
        val entrySize = reader.getPosition
        val readCRC = reader.readUnsignedLong()

        var readPersistentValue: Persistent = null

        def parsePersistent: Persistent = {
          if (readPersistentValue == null)
            readPersistentValue =
              SortedIndexBlock.read(
                fromOffset = indexOffset_,
                keySize = keySize,
                sortedIndexReader = sortedIndex,
                valuesReaderOrNull = valuesOrNull
              )

          readPersistentValue
        }

        if (readCRC == -1 || readCRC < hashIndexReader.block.minimumCRC || readCRC != CRC32.forBytes(entry.take(entrySize))) {
          null
        } else {
          //create a temporary partially read key-value for matcher.
          if (keyType == Memory.Range.id)
            new Partial.Range {
              val (fromKey, toKey) = Bytes.decompressJoin(entryKey)

              override def indexOffset: Int =
                indexOffset_

              override def key: Slice[Byte] =
                fromKey

              override def toPersistent: Persistent =
                parsePersistent
            }
          else if (keyType == Memory.Put.id || keyType == Memory.Remove.id || keyType == Memory.Update.id || keyType == Memory.Function.id || keyType == Memory.PendingApply.id)
            new Partial.Fixed {
              override def indexOffset: Int =
                indexOffset_

              override def key: Slice[Byte] =
                entryKey

              override def toPersistent: Persistent =
                parsePersistent
            }
          else
            null

        }
      } catch {
        case _: ArrayIndexOutOfBoundsException =>
          //println("ArrayIndexOutOfBoundsException")
          null
      }
  }

  val formats: Array[HashIndexEntryFormat] = MacroSealed.array[HashIndexEntryFormat]
}
