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

package swaydb.core.segment.defrag

import swaydb.core.file.sweeper.FileSweeper
import swaydb.core.segment._
import swaydb.core.segment.assigner.Assignable
import swaydb.core.segment.block.segment.SegmentBlockConfig
import swaydb.core.segment.block.segment.transient.TransientSegment
import swaydb.core.segment.data.Memory
import swaydb.core.segment.data.merge.stats.MergeStats
import swaydb.core.segment.distributor.PathDistributor
import swaydb.core.util.DefIO
import swaydb.slice.Slice
import swaydb.slice.order.{KeyOrder, TimeOrder}
import swaydb.utils.IDGenerator

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}

object DefragMemorySegment {

  /**
   * Default [[MergeStats.Memory]] and build new [[MemorySegment]].
   */
  def runOnSegment[SEG, NULL_SEG >: SEG](segment: SEG,
                                         nullSegment: NULL_SEG,
                                         headGap: Iterable[Assignable.Gap[MergeStats.Memory.Builder[Memory, ListBuffer]]],
                                         tailGap: Iterable[Assignable.Gap[MergeStats.Memory.Builder[Memory, ListBuffer]]],
                                         newKeyValues: => Iterator[Assignable],
                                         removeDeletes: Boolean,
                                         createdInLevel: Int,
                                         pathDistributor: PathDistributor)(implicit executionContext: ExecutionContext,
                                                                           keyOrder: KeyOrder[Slice[Byte]],
                                                                           timeOrder: TimeOrder[Slice[Byte]],
                                                                           functionStore: CoreFunctionStore,
                                                                           defragSource: DefragSource[SEG],
                                                                           segmentConfig: SegmentBlockConfig,
                                                                           fileSweeper: FileSweeper,
                                                                           idGenerator: IDGenerator): Future[DefIO[NULL_SEG, Iterable[MemorySegment]]] =
    Future {
      Defrag.runOnSegment(
        segment = segment,
        nullSegment = nullSegment,
        fragments = ListBuffer.empty[TransientSegment.Fragment[MergeStats.Memory.Builder[Memory, ListBuffer]]],
        headGap = headGap,
        tailGap = tailGap,
        newKeyValues = newKeyValues,
        removeDeletes = removeDeletes,
        createdInLevel = createdInLevel,
        createFence = (_: SEG) => TransientSegment.Fence
      )
    } flatMap {
      mergeResult =>
        commitSegments(
          mergeResult = mergeResult.output,
          removeDeletes = removeDeletes,
          createdInLevel = createdInLevel,
          pathDistributor = pathDistributor
        ) map {
          result =>
            mergeResult.withOutput(result.flatten)
        }
    }

  /**
   * Default [[MergeStats.Memory]] and build new [[MemorySegment]].
   */
  def runOnGaps[SEG, NULL_SEG >: SEG](nullSegment: NULL_SEG,
                                      headGap: Iterable[Assignable.Gap[MergeStats.Memory.Builder[Memory, ListBuffer]]],
                                      tailGap: Iterable[Assignable.Gap[MergeStats.Memory.Builder[Memory, ListBuffer]]],
                                      removeDeletes: Boolean,
                                      createdInLevel: Int,
                                      pathDistributor: PathDistributor)(implicit executionContext: ExecutionContext,
                                                                        keyOrder: KeyOrder[Slice[Byte]],
                                                                        timeOrder: TimeOrder[Slice[Byte]],
                                                                        functionStore: CoreFunctionStore,
                                                                        defragSource: DefragSource[SEG],
                                                                        segmentConfig: SegmentBlockConfig,
                                                                        fileSweeper: FileSweeper,
                                                                        idGenerator: IDGenerator): Future[DefIO[NULL_SEG, Iterable[MemorySegment]]] =
    Defrag.runOnGaps(
      fragments = ListBuffer.empty[TransientSegment.Fragment[MergeStats.Memory.Builder[Memory, ListBuffer]]],
      headGap = headGap,
      tailGap = tailGap,
      removeDeletes = removeDeletes,
      createdInLevel = createdInLevel,
      fence = TransientSegment.Fence
    ) flatMap {
      mergeResult =>
        commitSegments(
          mergeResult = mergeResult,
          removeDeletes = removeDeletes,
          createdInLevel = createdInLevel,
          pathDistributor = pathDistributor
        ) map {
          result =>
            DefIO(
              input = nullSegment,
              output = result.flatten
            )
        }
    }

  /**
   * Converts all de-fragmented [[TransientSegment.Fragment]]s to [[MemorySegment]].
   *
   * Unlike [[DefragPersistentSegment]] which converts [[TransientSegment.Fragment]] to
   * [[TransientSegment.Persistent]] which then get persisted via [[swaydb.core.segment.io.SegmentWritePersistentIO]]
   * here [[TransientSegment.Fragment]]s are converted directly to [[MemorySegment]] instead of converting them within
   * [[swaydb.core.segment.io.SegmentWriteMemoryIO]] because we can have more concurrency here as
   * [[swaydb.core.segment.io.SegmentWriteIO]] instances are not concurrent.
   */
  private def commitSegments[NULL_SEG >: SEG, SEG](mergeResult: ListBuffer[TransientSegment.Fragment[MergeStats.Memory.Builder[Memory, ListBuffer]]],
                                                   removeDeletes: Boolean,
                                                   createdInLevel: Int,
                                                   pathDistributor: PathDistributor)(implicit executionContext: ExecutionContext,
                                                                                     keyOrder: KeyOrder[Slice[Byte]],
                                                                                     timeOrder: TimeOrder[Slice[Byte]],
                                                                                     functionStore: CoreFunctionStore,
                                                                                     segmentConfig: SegmentBlockConfig,
                                                                                     fileSweeper: FileSweeper,
                                                                                     idGenerator: IDGenerator): Future[ListBuffer[Slice[MemorySegment]]] =
    Future.traverse(mergeResult) {
      case remote: TransientSegment.Remote =>
        Future {
          remote match {
            case ref: TransientSegment.RemoteRef =>
              MemorySegment(
                keyValues = ref.iterator(segmentConfig.initialiseIteratorsInOneSeek),
                pathDistributor = pathDistributor,
                removeDeletes = removeDeletes,
                minSegmentSize = segmentConfig.minSize,
                maxKeyValueCountPerSegment = segmentConfig.maxCount,
                createdInLevel = createdInLevel
              )

            case TransientSegment.RemotePersistentSegment(segment) =>
              MemorySegment.copyFrom(
                segment = segment,
                createdInLevel = createdInLevel,
                pathDistributor = pathDistributor,
                removeDeletes = removeDeletes,
                minSegmentSize = segmentConfig.minSize,
                maxKeyValueCountPerSegment = segmentConfig.maxCount,
                initialiseIteratorsInOneSeek = segmentConfig.initialiseIteratorsInOneSeek
              )
          }
        }

      case TransientSegment.Stats(stats) =>
        if (stats.isEmpty)
          Future.successful(Slice.empty)
        else
          Future {
            MemorySegment(
              minSegmentSize = segmentConfig.minSize,
              maxKeyValueCountPerSegment = segmentConfig.maxCount,
              pathDistributor = pathDistributor,
              createdInLevel = createdInLevel,
              stats = stats.close()
            )
          }

      case TransientSegment.Fence =>
        Future.successful(Slice.empty)
    }
}
