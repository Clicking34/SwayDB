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

package swaydb.core.log.counter

import swaydb.core.log.{LogCache, LogCacheBuilder, LogEntry}
import swaydb.slice.Slice

private object PersistentCounterLogCache {
  implicit def builder =
    new LogCacheBuilder[PersistentCounterLogCache] {
      override def create(): PersistentCounterLogCache =
        new PersistentCounterLogCache
    }
}

private class PersistentCounterLogCache extends LogCache[Slice[Byte], Slice[Byte]] {
  var entryOrNull: LogEntry[Slice[Byte], Slice[Byte]] = _

  override def writeAtomic(entry: LogEntry[Slice[Byte], Slice[Byte]]): Unit =
    this.entryOrNull = entry

  override def writeNonAtomic(entry: LogEntry[Slice[Byte], Slice[Byte]]): Unit =
    writeAtomic(entry)

  override def iterator: Iterator[(Slice[Byte], Slice[Byte])] =
    entryOrNull match {
      case null =>
        Iterator.empty

      case LogEntry.Put(key, value) =>
        Iterator((key, value))

      case entry: LogEntry[Slice[Byte], Slice[Byte]] =>
        entry.entries.iterator map {
          case LogEntry.Put(key, value) =>
            (key, value)
        }
    }

  override def isEmpty: Boolean =
    entryOrNull == null

  override def maxKeyValueCount: Int =
    if (entryOrNull == null)
      0
    else
      1

}
