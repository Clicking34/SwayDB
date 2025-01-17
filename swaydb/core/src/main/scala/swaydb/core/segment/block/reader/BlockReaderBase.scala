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

package swaydb.core.segment.block.reader

import com.typesafe.scalalogging.LazyLogging
import swaydb.core.segment.block.{BlockCache, BlockCacheSource, BlockCacheState, BlockOffset}
import swaydb.slice._

/**
 * Defers [[ReaderBase]] related operations to [[BlockReader]].
 */
private[block] trait BlockReaderBase extends ReaderBase with BlockCacheSource with LazyLogging {

  private[reader] val reader: Reader

  def offset: BlockOffset

  def blockCache: Option[BlockCacheState]

  //start offset BlockRefReader. BlockCache uses this to maintain
  //consistent cache even if it gets transferred to another file.
  def rootBlockRefOffset: BlockOffset

  private var position: Int = 0

  override def isFile: Boolean =
    reader.isFile

  override def remaining(): Int =
    offset.size - position

  def moveTo(position: Int): BlockReaderBase = {
    this.position = position
    this
  }

  override def hasMore: Boolean =
    hasAtLeast(1)

  override def hasAtLeast(atLeastSize: Int): Boolean =
    hasAtLeast(position, atLeastSize)

  def hasAtLeast(fromPosition: Int, atLeastSize: Int): Boolean =
    (offset.size - fromPosition) >= atLeastSize

  override def size: Int =
    offset.size

  override def getPosition: Int =
    position

  override def read(size: Int): Slice[Byte] = {
    val remaining = this.remaining()
    if (remaining <= 0) {
      Slice.emptyBytes
    } else {
      val bytesToRead = size min remaining

      val bytes =
        if (reader.isFile && blockCache.isDefined)
          BlockCache.getOrSeek(
            position = offset.start + position - rootBlockRefOffset.start,
            size = bytesToRead,
            source = this,
            state = blockCache.get
          )
        else
          reader
            .moveTo(offset.start + position)
            .read(bytesToRead)

      position += bytesToRead
      bytes
    }
  }

  override def read(size: Int, blockSize: Int): SliceRO[Byte] = {
    val remaining = this.remaining()
    if (remaining <= 0) {
      Slice.emptyBytes
    } else {
      val bytesToRead = size min remaining

      val bytes =
        if (reader.isFile && blockCache.isDefined)
          BlockCache.getOrSeek(
            position = offset.start + position - rootBlockRefOffset.start,
            size = bytesToRead,
            source = this,
            state = blockCache.get
          )
        else
          reader
            .moveTo(offset.start + position)
            .read(bytesToRead, blockSize)

      position += bytesToRead
      bytes
    }
  }

  override def readFromSource(position: Int, size: Int): Slice[Byte] =
    reader
      .moveTo(position + rootBlockRefOffset.start)
      .read(size)

  override def readFromSource(position: Int, size: Int, blockSize: Int): SliceRO[Byte] =
    reader
      .moveTo(position + rootBlockRefOffset.start)
      .read(size, blockSize)

  override def blockCacheMaxBytes: Int =
    rootBlockRefOffset.size

  def readFullBlock(): Slice[Byte] =
    reader
      .moveTo(offset.start)
      .read(offset.size)

  def readFullBlockOrNone(): SliceOption[Byte] =
    if (offset.size == 0)
      Slice.Null
    else
      readFullBlock()

  override def readRemaining(): Slice[Byte] =
    read(remaining())
}
