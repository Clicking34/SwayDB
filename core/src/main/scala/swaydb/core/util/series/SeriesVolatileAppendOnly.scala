/*
 * Copyright (c) 2020 Simer JS Plaha (simer.j@gmail.com - @simerplaha)
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
 *
 * Additional permission under the GNU Affero GPL version 3 section 7:
 * If you modify this Program or any covered work, only by linking or
 * combining it with separate works, the licensors of this Program grant
 * you additional permission to convey the resulting work.
 */

package swaydb.core.util.series


object SeriesVolatileAppendOnly {

  def apply[T >: Null](limit: Int): SeriesVolatileAppendOnly[T] =
    new SeriesVolatileAppendOnly[T](Array.fill[Item[T]](limit)(new Item[T](null)))

}

class SeriesVolatileAppendOnly[T >: Null](array: Array[Item[T]]) { self =>
  //Not volatile because series do not allow concurrent writes only concurrent reads.
  private var writePosition = 0

  def get(index: Int): T =
    if (index >= writePosition)
      throw new ArrayIndexOutOfBoundsException(index)
    else
      array(index).value

  def add(item: T): Unit = {
    array(writePosition).value = item
    writePosition += 1
  }

  def length: Int =
    writePosition

  def innerArrayLength =
    array.length

  def lastOrNull: T =
    if (writePosition == 0)
      null
    else
      array(writePosition - 1).value

  def isFull =
    array.length == writePosition

  def headOrNull: T =
    array(0).value

  def iterator: Iterator[T] =
    array
      .iterator
      .take(writePosition)
      .map(_.value)

}
