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

package swaydb.core.file

import com.typesafe.scalalogging.LazyLogging
import swaydb.slice.{Reader, Slice, SliceRO}

import java.nio.file.Path

object FileReader {
  @inline def apply(file: CoreFile) =
    new FileReader(file)
}

private[core] class FileReader(private val file: CoreFile) extends Reader with LazyLogging {

  private var position: Int = 0

  final override def isFile: Boolean =
    true

  def path: Path =
    file.path

  def isLoaded(): Boolean =
    file.isLoaded()

  override def size(): Int =
    file.fileSize()

  def moveTo(newPosition: Int): FileReader = {
    position = newPosition max 0
    this
  }

  def hasMore: Boolean =
    position < size()

  def hasAtLeast(size: Int): Boolean =
    (file.fileSize() - position) >= size

  override def copy(): FileReader =
    new FileReader(file = file)

  override def getPosition: Int = position

  def transfer(position: Int, count: Int, transferTo: CoreFile): Unit =
    file.transfer(position = position, count = count, transferTo = transferTo)

  override def read(size: Int): Slice[Byte] =
    if (size <= 0) {
      Slice.emptyBytes
    } else {
      val bytes =
        file.read(
          position = position,
          size = size
        )

      position += size
      bytes
    }

  override def read(size: Int, blockSize: Int): SliceRO[Byte] =
    if (size <= 0) {
      Slice.emptyBytes
    } else {
      val bytes =
        file.read(
          position = position,
          size = size,
          blockSize = blockSize
        )

      position += size
      bytes
    }

  override def readRemaining(): Slice[Byte] =
    read(remaining())
}
