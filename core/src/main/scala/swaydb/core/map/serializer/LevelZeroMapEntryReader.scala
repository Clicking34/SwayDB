/*
 * Copyright (C) 2018 Simer Plaha (@simerplaha)
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

package swaydb.core.map.serializer

import swaydb.core.data.{Memory, Value}
import swaydb.core.map.MapEntry
import swaydb.data.slice.{Reader, Slice}

import scala.util.{Failure, Success, Try}

object LevelZeroMapEntryReader {

  implicit val putSerializer = ValueSerializers.PutSerializerWithSize

  implicit object Level0RemoveReader extends MapEntryReader[MapEntry.Put[Slice[Byte], Memory.Remove]] {

    override def read(reader: Reader): Try[Option[MapEntry.Put[Slice[Byte], Memory.Remove]]] =
      for {
        keyLength <- reader.readInt()
        key <- reader.read(keyLength).map(_.unslice())
      } yield {
        Some(MapEntry.Put(key, Memory.Remove(key))(LevelZeroMapEntryWriter.Level0RemoveWriter))
      }
  }

  implicit object Level0AddReader extends MapEntryReader[MapEntry.Put[Slice[Byte], Memory.Put]] {

    override def read(reader: Reader): Try[Option[MapEntry.Put[Slice[Byte], Memory.Put]]] =
      for {
        keyLength <- reader.readInt()
        key <- reader.read(keyLength).map(_.unslice())
        valueLength <- reader.readInt()
        value <- if (valueLength == 0) Success(None) else reader.read(valueLength).map(Some(_))
      } yield {
        Some(MapEntry.Put(key, Memory.Put(key, value))(LevelZeroMapEntryWriter.Level0PutWriter))
      }
  }

  implicit object Level0RangeReader extends MapEntryReader[MapEntry.Put[Slice[Byte], Memory.Range]] {

    override def read(reader: Reader): Try[Option[MapEntry.Put[Slice[Byte], Memory.Range]]] =
      for {
        fromKeyLength <- reader.readInt()
        fromKey <- reader.read(fromKeyLength).map(_.unslice())
        toKeyLength <- reader.readInt()
        toKey <- reader.read(toKeyLength).map(_.unslice())
        rangeValueId <- reader.readInt()
        valueLength <- reader.readInt()
        valueBytes <- if (valueLength == 0) Success(Slice.emptyByteSlice) else reader.read(valueLength)
        (fromValue, rangeValue) <- RangeValueSerializer.read(rangeValueId, valueBytes)
      } yield {
        Some(MapEntry.Put(fromKey, Memory.Range(fromKey, toKey, fromValue, rangeValue))(LevelZeroMapEntryWriter.Level0PutRangeWriter))
      }
  }

  implicit object Level0Reader extends MapEntryReader[MapEntry[Slice[Byte], Memory]] {
    override def read(reader: Reader): Try[Option[MapEntry[Slice[Byte], Memory]]] =
      reader.foldLeftTry(Option.empty[MapEntry[Slice[Byte], Memory]]) {
        case (previousEntry, reader) =>
          reader.readInt() flatMap {
            entryId =>
              if (entryId == LevelZeroMapEntryWriter.Level0PutWriter.id)
                Level0AddReader.read(reader) map {
                  nextEntry =>
                    nextEntry flatMap {
                      nextEntry =>
                        previousEntry.map(_ ++ nextEntry) orElse Some(nextEntry)
                    }
                }
              else if (entryId == LevelZeroMapEntryWriter.Level0RemoveWriter.id)
                Level0RemoveReader.read(reader) map {
                  nextEntry =>
                    nextEntry flatMap {
                      nextEntry =>
                        previousEntry.map(_ ++ nextEntry) orElse Some(nextEntry)
                    }
                }
              else if (entryId == LevelZeroMapEntryWriter.Level0PutRangeWriter.id)
                Level0RangeReader.read(reader) map {
                  nextEntry =>
                    nextEntry flatMap {
                      nextEntry =>
                        previousEntry.map(_ ++ nextEntry) orElse Some(nextEntry)
                    }
                }
              else
                Failure(new IllegalArgumentException(s"Invalid entry type $entryId."))
          }
      }
  }
}