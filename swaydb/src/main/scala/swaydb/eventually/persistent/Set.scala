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
 */

package swaydb.eventually.persistent

import java.nio.file.Path

import com.typesafe.scalalogging.LazyLogging
import swaydb.configs.level.DefaultEventuallyPersistentConfig
import swaydb.core.Core
import swaydb.core.function.FunctionStore
import swaydb.data.accelerate.{Accelerator, LevelZeroMeter}
import swaydb.data.config._
import swaydb.data.order.{KeyOrder, TimeOrder}
import swaydb.data.slice.Slice
import swaydb.data.util.StorageUnits._
import swaydb.serializers.Serializer
import swaydb.{IO, KeyOrderConverter, SwayDB}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, _}
import scala.reflect.ClassTag

object Set extends LazyLogging {

  implicit val timeOrder: TimeOrder[Slice[Byte]] = TimeOrder.long
  implicit def functionStore: FunctionStore = FunctionStore.memory()

  /**
   * For custom configurations read documentation on website: http://www.swaydb.io/configuring-levels
   */
  def apply[A, F, BAG[_]](dir: Path,
                          maxSegmentsOpen: Int = 1000,
                          mapSize: Int = 4.mb,
                          maxMemoryLevelSize: Int = 100.mb,
                          maxSegmentsToPush: Int = 5,
                          memoryLevelSegmentSize: Int = 2.mb,
                          memoryLevelMaxKeyValuesCountPerSegment: Int = 200000,
                          persistentLevelSegmentSize: Int = 4.mb,
                          persistentLevelAppendixFlushCheckpointSize: Int = 2.mb,
                          persistentLevelMaxKeyValuesPerSegment: Int = 100000,
                          mmapPersistentSegments: MMAP = MMAP.WriteAndRead,
                          mmapPersistentAppendix: Boolean = true,
                          otherDirs: Seq[Dir] = Seq.empty,
                          keyValueCacheCheckDelay: FiniteDuration = 5.seconds,
                          segmentsOpenCheckDelay: FiniteDuration = 5.seconds,
                          blockSize: Int = 4098.bytes,
                          memoryCacheSize: Int = 100.mb,
                          memorySweeperPollInterval: FiniteDuration = 10.seconds,
                          fileSweeperPollInterval: FiniteDuration = 10.seconds,
                          mightContainFalsePositiveRate: Double = 0.01,
                          compressDuplicateValues: Boolean = true,
                          deleteSegmentsEventually: Boolean = true,
                          cacheKeyValueIds: Boolean = true,
                          acceleration: LevelZeroMeter => Accelerator = Accelerator.noBrakes(),
                          threadStateCache: ThreadStateCache = ThreadStateCache.Limit(hashMapMaxSize = 100, maxProbe = 10))(implicit serializer: Serializer[A],
                                                                                                                            functionClassTag: ClassTag[F],
                                                                                                                            bag: swaydb.Bag[BAG],
                                                                                                                            keyOrder: Either[KeyOrder[Slice[Byte]], KeyOrder[A]] = Left(KeyOrder.default),
                                                                                                                            fileSweeperEC: ExecutionContext = SwayDB.sweeperExecutionContext,
                                                                                                                            memorySweeperEC: ExecutionContext = SwayDB.sweeperExecutionContext): IO[swaydb.Error.Boot, swaydb.Set[A, F, BAG]] = {
    implicit val bytesKeyOrder: KeyOrder[Slice[Byte]] = KeyOrderConverter.typedToBytes(keyOrder)

    Core(
      enableTimer = functionClassTag != ClassTag.Nothing,
      cacheKeyValueIds = cacheKeyValueIds,
      threadStateCache = threadStateCache,
      config =
        DefaultEventuallyPersistentConfig(
          dir = dir,
          otherDirs = otherDirs,
          mapSize = mapSize,
          maxMemoryLevelSize = maxMemoryLevelSize,
          memoryLevelMaxKeyValuesCountPerSegment = memoryLevelMaxKeyValuesCountPerSegment,
          maxSegmentsToPush = maxSegmentsToPush,
          memoryLevelMinSegmentSize = memoryLevelSegmentSize,
          persistentLevelMinSegmentSize = persistentLevelSegmentSize,
          persistentLevelMaxKeyValuesPerSegment = persistentLevelMaxKeyValuesPerSegment,
          persistentLevelAppendixFlushCheckpointSize = persistentLevelAppendixFlushCheckpointSize,
          mmapPersistentSegments = mmapPersistentSegments,
          mmapPersistentAppendix = mmapPersistentAppendix,
          mightContainFalsePositiveRate = mightContainFalsePositiveRate,
          compressDuplicateValues = compressDuplicateValues,
          deleteSegmentsEventually = deleteSegmentsEventually,
          acceleration = acceleration
        ),
      fileCache =
        FileCache.Enable.default(
          maxOpen = maxSegmentsOpen,
          interval = fileSweeperPollInterval,
          ec = fileSweeperEC
        ),
      memoryCache =
        MemoryCache.Enabled.default(
          minIOSeekSize = blockSize,
          skipBlockCacheSeekSize = blockSize * 10,
          memorySize = memoryCacheSize,
          interval = memorySweeperPollInterval,
          ec = memorySweeperEC
        )
    ) map {
      db =>
        swaydb.Set[A, F, BAG](db.toBag)
    }
  }
}
