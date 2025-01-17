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

package swaydb.core.segment.cache.sweeper

import swaydb.core.cache.CacheUnsafe
import swaydb.core.segment.data.Persistent
import swaydb.core.skiplist.SkipList
import swaydb.slice.{Slice, SliceOption}
import swaydb.utils.StorageUnits.StorageIntImplicits
import swaydb.utils.{ByteSizeOf, HashedMap}

import java.util.concurrent.ConcurrentSkipListMap
import scala.ref.WeakReference

protected sealed trait MemorySweeperCommand
protected object MemorySweeperCommand {

  val commandWeight = 264.bytes

  private[sweeper] class SweepKeyValue(val keyValueRef: WeakReference[Persistent],
                                       val skipListRef: WeakReference[SkipList[_, _, Slice[Byte], _]]) extends MemorySweeperCommand

  private[sweeper] class SweepCache(val weight: Int,
                                    val cache: WeakReference[swaydb.core.cache.Cache[_, _, _]]) extends MemorySweeperCommand

  private[sweeper] class SweepSkipListMap(val key: Slice[Byte],
                                          val weight: Int,
                                          val cache: WeakReference[ConcurrentSkipListMap[Slice[Byte], _]]) extends MemorySweeperCommand

  private[sweeper] class SweepBlockCache(val key: Long,
                                         val valueSize: Int,
                                         val map: CacheUnsafe[Unit, HashedMap.Concurrent[Long, SliceOption[Byte], Slice[Byte]]]) extends MemorySweeperCommand

  def weigher(command: MemorySweeperCommand): Int =
    command match {
      case command: MemorySweeperCommand.SweepBlockCache =>
        ByteSizeOf.long + command.valueSize + 264

      case command: MemorySweeperCommand.SweepCache =>
        ByteSizeOf.long + command.weight + 264

      case command: MemorySweeperCommand.SweepSkipListMap =>
        ByteSizeOf.long + command.weight + 264

      case command: MemorySweeperCommand.SweepKeyValue =>
        //accessing underlying instead of keyValueRef.get to avoid creating Option.
        val keyValueOrNull = command.keyValueRef.underlying.get()

        if (keyValueOrNull == null)
          264 //264 for the weight of WeakReference itself.
        else
          MemorySweeperCommand.weight(keyValueOrNull).toInt
    }

  def weight(keyValue: Persistent) = {
    val otherBytes = (Math.ceil(keyValue.key.size + keyValue.valueLength / 8.0) - 1.0) * 8
    //        if (keyValue.hasRemoveMayBe) (168 + otherBytes).toLong else (264 + otherBytes).toLong
    (264 * 2) + otherBytes
  }

}
