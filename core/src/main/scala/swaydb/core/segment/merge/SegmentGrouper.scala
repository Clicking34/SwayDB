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

package swaydb.core.segment.merge

import com.typesafe.scalalogging.LazyLogging
import swaydb.core.data.{Memory, Persistent, Value, _}

/**
 * SegmentGroups will always group key-values with Groups at the head of key-value List. Groups cannot be randomly
 * added in the middle.
 */
private[core] object SegmentGrouper extends LazyLogging {

  def add(keyValue: KeyValue,
          builder: MergeStats[Memory, Iterable],
          isLastLevel: Boolean): Unit = {
    val keyValueToMerge = getOrNull(keyValue, isLastLevel)
    if (keyValueToMerge != null)
      builder add keyValueToMerge.toMemory
  }

  def getOrNull(keyValue: KeyValue,
                isLastLevel: Boolean): Memory =
    keyValue match {
      case fixed: KeyValue.Fixed =>
        fixed match {
          case put: Memory.Put =>
            if (!isLastLevel || put.hasTimeLeft())
              put
            else
              null

          case put: Persistent.Put =>
            if (!isLastLevel || put.hasTimeLeft())
              put.toMemory()
            else
              null

          case remove: Memory.Fixed =>
            if (!isLastLevel)
              remove
            else
              null

          case remove: Persistent.Fixed =>
            if (!isLastLevel)
              remove.toMemory()
            else
              null
        }

      case range: KeyValue.Range =>
        if (isLastLevel) {
          val fromValue = range.fetchFromValueUnsafe
          if (fromValue.isDefined)
            fromValue.get match {
              case put @ Value.Put(fromValue, deadline, time) =>
                if (put.hasTimeLeft())
                  Memory.Put(
                    key = range.fromKey,
                    value = fromValue,
                    deadline = deadline,
                    time = time
                  )
                else
                  null

              case _: Value.Remove | _: Value.Update | _: Value.Function | _: Value.PendingApply =>
                null
            }
          else
            null
        } else {
          val (fromValue, rangeValue) = range.fetchFromAndRangeValueUnsafe
          Memory.Range(
            fromKey = range.fromKey,
            toKey = range.toKey,
            fromValue = fromValue,
            rangeValue = rangeValue
          )
        }
    }
}
