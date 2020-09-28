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
 * If you modify this Program, or any covered work, by linking or combining
 * it with other code, such other code is not for that reason alone subject
 * to any of the requirements of the GNU Affero GPL version 3.
 */

package swaydb.core.level.zero

import swaydb.core.util.queue.{VolatileQueue, Walker}

object LeveledSkipLists {

  def apply(skipList: LevelSkipList, hasRange: Boolean): LeveledSkipLists =
    new LeveledSkipLists(
      queue = VolatileQueue[LevelSkipList](skipList),
      currentLevel = skipList
    )
}

class LeveledSkipLists private[zero](queue: VolatileQueue[LevelSkipList],
                                     @volatile private var currentLevel: LevelSkipList) {

  @inline def current = currentLevel

  def addFirst(skipList: LevelSkipList): Unit = {
    queue.addHead(skipList)
    currentLevel = skipList
  }

  def isEmpty: Boolean =
    queue.isEmpty || queue.iterator.forall(_.skipList.isEmpty)

  @inline def size: Int =
    queue.iterator.foldLeft(0)(_ + _.skipList.size)

  @inline def queueSize: Int =
    queue.size

  @inline def iterator =
    queue.iterator

  @inline def hasRange =
    iterator.exists(_.hasRange)

  def walker: Walker[LevelSkipList] =
    queue

}
