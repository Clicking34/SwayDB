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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with SwayDB. If not, see <https://www.gnu.org/licenses/>.
 */

package swaydb.core.segment.format.a.entry.reader.value

import scala.util.{Failure, Success, Try}
import swaydb.data.slice.{Reader, Slice}

object LazyFunctionReader {
  def apply(reader: Reader,
            offset: Int,
            length: Int): LazyFunctionReader =
    new LazyFunctionReader {
      override val valueReader: Reader = reader

      override def valueLength: Int = length

      override def valueOffset: Int = offset
    }
}

trait LazyFunctionReader extends LazyValueReader {

  def getOrFetchFunction: Try[Slice[Byte]] =
    super.getOrFetchValue flatMap {
      case Some(value) =>
        Success(value)
      case None =>
        Failure(new Exception("Empty functionId."))
    }

  override def isValueDefined: Boolean =
    super.isValueDefined
}
