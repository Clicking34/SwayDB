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

import swaydb.OK
import swaydb.slice.{Slice, SliceReader}
import swaydb.slice.utils.ByteSlice
import swaydb.utils.TupleOrNone

private[swaydb] object Bytes extends ByteSlice {

  def commonPrefixBytesCount(previous: Slice[Byte],
                             next: Slice[Byte]): Int = {
    val min = Math.min(previous.size, next.size)
    var i = 0
    while (i < min && previous(i) == next(i))
      i += 1
    i
  }

  def commonPrefixBytes(previous: Slice[Byte],
                        next: Slice[Byte]): Slice[Byte] = {
    val commonBytes = commonPrefixBytesCount(previous, next)
    if (previous.size <= next.size)
      next take commonBytes
    else
      previous take commonBytes
  }

  def compress(previous: Slice[Byte],
               next: Slice[Byte],
               minimumCommonBytes: Int): TupleOrNone[Int, Slice[Byte]] = {
    val commonBytes = Bytes.commonPrefixBytesCount(previous, next)
    if (commonBytes < minimumCommonBytes)
      TupleOrNone.None
    else
      TupleOrNone.Some(commonBytes, next.drop(commonBytes))
  }

  def compressFull(previous: Option[Slice[Byte]],
                   next: Slice[Byte]): Option[OK] =
    previous match {
      case Some(previous) =>
        compressFull(
          previous = previous,
          next = next
        )

      case None =>
        None
    }

  def compressFull(previous: Slice[Byte],
                   next: Slice[Byte]): Option[OK] =
    if (previous.size < next.size)
      None
    else
      compress(previous, next, next.size) match {
        case TupleOrNone.None =>
          None

        case TupleOrNone.Some(_, _) =>
          OK.someOK
      }

  def compressExact(previous: Slice[Byte],
                    next: Slice[Byte]): Option[OK] =
    if (previous.size != next.size)
      None
    else
      compressFull(previous, next)

  def decompress(previous: Slice[Byte],
                 next: Slice[Byte],
                 commonBytes: Int): Slice[Byte] = {
    val missingCommonBytes = previous.slice(0, commonBytes - 1)
    val fullKey = new Array[Byte](commonBytes + next.size)
    var i = 0
    while (i < commonBytes) {
      fullKey(i) = missingCommonBytes(i)
      i += 1
    }
    var x = 0
    while (x < next.size) {
      fullKey(i) = next(x)
      x += 1
      i += 1
    }
    Slice.wrap(fullKey)
  }

  def compressJoin(left: Slice[Byte],
                   right: Slice[Byte]): Slice[Byte] =
    compressJoin(
      left = left,
      right = right,
      tail = Slice.emptyBytes
    )

  def compressJoin(left: Slice[Byte],
                   right: Slice[Byte],
                   tail: Byte): Slice[Byte] =
    compressJoin(
      left = left,
      right = right,
      tail = Slice(tail)
    )

  /**
   * Merges the input bytes into a single byte array extracting common bytes.
   *
   * If there are no common bytes the compress will result in 2 more additional bytes.
   *
   * tail bytes are also appended to the the result. When decompressing tail bytes should be stripped.
   */
  def compressJoin(left: Slice[Byte],
                   right: Slice[Byte],
                   tail: Slice[Byte]): Slice[Byte] = {
    val commonBytes = commonPrefixBytesCount(left, right)
    val rightWithoutCommonBytes =
      if (commonBytes != 0)
        right.drop(commonBytes)
      else
        right

    //if right was fully compressed just store right bytes with commonBytes integer. During read commonBytes int will be checked
    //to see if its the same size as left and the same left bytes will be returned for right as well.
    if (rightWithoutCommonBytes.isEmpty) {
      val compressedSlice = Slice.allocate[Byte](left.size + sizeOfUnsignedInt(commonBytes) + sizeOfUnsignedInt(left.size) + tail.size)
      compressedSlice addAll left
      compressedSlice addUnsignedInt commonBytes
      compressedSlice addAll ByteSlice.writeUnsignedIntReversed(left.size) //store key1's byte size to the end to allow further merges with other keys.
      compressedSlice addAll tail
    } else {
      val size =
        left.size +
          sizeOfUnsignedInt(commonBytes) +
          sizeOfUnsignedInt(rightWithoutCommonBytes.size) +
          rightWithoutCommonBytes.size +
          sizeOfUnsignedInt(left.size) +
          tail.size

      val compressedSlice = Slice.allocate[Byte](size)
      compressedSlice addAll left
      compressedSlice addUnsignedInt commonBytes
      compressedSlice addUnsignedInt rightWithoutCommonBytes.size
      compressedSlice addAll rightWithoutCommonBytes
      compressedSlice addAll ByteSlice.writeUnsignedIntReversed(left.size) //store key1's byte size to the end to allow further merges with other keys.
      compressedSlice addAll tail
    }
  }

  def decompressJoin(bytes: Slice[Byte]): (Slice[Byte], Slice[Byte]) = {

    val reader = SliceReader(bytes)
    val (leftBytesSize, lastBytesRead) = ByteSlice.readLastUnsignedInt(bytes)
    val left = reader.read(leftBytesSize)
    val commonBytes = reader.readUnsignedInt()
    val hasMore = reader.hasAtLeast(lastBytesRead + 1) //if there are more bytes to read.

    val right =
      if (!hasMore && commonBytes == leftBytesSize) { //if right was fully compressed then right == left, return left.
        left
      } else {
        val rightSize = reader.readUnsignedInt()
        val right = reader.read(rightSize)
        decompress(left, right, commonBytes)
      }

    (left, right)
  }

  def normalise(bytes: Slice[Byte], toSize: Int): Slice[Byte] = {
    assert(bytes.size < toSize, s"bytes.size(${bytes.size}) >= toSize($toSize)")
    val finalSlice = Slice.allocate[Byte](toSize)
    var zeroesToAdd = toSize - bytes.size - 1
    while (zeroesToAdd > 0) {
      finalSlice add Bytes.zero
      zeroesToAdd -= 1
    }
    finalSlice add Bytes.one
    finalSlice addAll bytes
  }

  def normalise(appendHeader: Slice[Byte],
                bytes: Slice[Byte],
                toSize: Int): Slice[Byte] = {
    assert((appendHeader.size + bytes.size) < toSize, s"appendHeader.size(${appendHeader.size}) + bytes.size(${bytes.size}) >= toSize($toSize)")
    val finalSlice = Slice.allocate[Byte](appendHeader.size + toSize)
    finalSlice addAll appendHeader
    var zeroesToAdd = toSize - appendHeader.size - bytes.size - 1
    while (zeroesToAdd > 0) {
      finalSlice add Bytes.zero
      zeroesToAdd -= 1
    }
    finalSlice add Bytes.one
    finalSlice addAll bytes
  }

  /**
   * Does not validate if the input bytes are normalised bytes.
   * It simply drops upto the first 1.byte.
   *
   * Similar function [[Slice.dropTo]] which is not directly to avoid
   * creation of [[Some]] object.
   */
  def deNormalise(bytes: Slice[Byte]): Slice[Byte] =
    bytes drop (bytes.indexOf(Bytes.one).get + 1)
}
