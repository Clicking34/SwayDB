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
 * If you modify this Program or any covered work, only by linking or combining
 * it with separate works, the licensors of this Program grant you additional
 * permission to convey the resulting work.
 */

package swaydb.core.segment.defrag

import swaydb.Aggregator
import swaydb.Aggregator.nothingAggregator
import swaydb.core.data.{Memory, MergeResult}
import swaydb.core.function.FunctionStore
import swaydb.core.merge.MergeStats
import swaydb.core.merge.MergeStats.Persistent
import swaydb.core.segment.{MemorySegment, PersistentSegment, PersistentSegmentMany, PersistentSegmentOne, SegmentSource}
import swaydb.core.segment.SegmentSource._
import swaydb.core.segment.assigner.{Assignable, GapAggregator, SegmentAssigner, SegmentAssignment}
import swaydb.core.segment.block.binarysearch.BinarySearchIndexBlock
import swaydb.core.segment.block.bloomfilter.BloomFilterBlock
import swaydb.core.segment.block.hashindex.HashIndexBlock
import swaydb.core.segment.block.segment.SegmentBlock
import swaydb.core.segment.block.segment.data.TransientSegment
import swaydb.core.segment.block.sortedindex.SortedIndexBlock
import swaydb.core.segment.block.values.ValuesBlock
import swaydb.core.util.IDGenerator
import swaydb.data.order.{KeyOrder, TimeOrder}
import swaydb.data.slice.Slice
import swaydb.data.util.Futures
import swaydb.data.util.Futures._

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}

object DefragSegment {

  /**
   * Builds a [[Future]] that executes defragmentation and merge on a single Segment.
   */
  def runOne[SEG, NULL_SEG >: SEG](segment: Option[SEG],
                                   nullSegment: NULL_SEG,
                                   headGap: ListBuffer[Assignable.Gap[MergeStats.Persistent.Builder[Memory, ListBuffer]]],
                                   tailGap: ListBuffer[Assignable.Gap[MergeStats.Persistent.Builder[Memory, ListBuffer]]],
                                   mergeableCount: Int,
                                   mergeable: Iterator[Assignable],
                                   removeDeletes: Boolean,
                                   createdInLevel: Int)(implicit executionContext: ExecutionContext,
                                                        keyOrder: KeyOrder[Slice[Byte]],
                                                        timeOrder: TimeOrder[Slice[Byte]],
                                                        functionStore: FunctionStore,
                                                        segmentSource: SegmentSource[SEG],
                                                        valuesConfig: ValuesBlock.Config,
                                                        sortedIndexConfig: SortedIndexBlock.Config,
                                                        binarySearchIndexConfig: BinarySearchIndexBlock.Config,
                                                        hashIndexConfig: HashIndexBlock.Config,
                                                        bloomFilterConfig: BloomFilterBlock.Config,
                                                        segmentConfig: SegmentBlock.Config): Future[MergeResult[NULL_SEG, Slice[TransientSegment.Persistent]]] =
    Future {
      Defrag.run(
        segment = segment,
        nullSegment = nullSegment,
        fragments = ListBuffer.empty,
        headGap = headGap,
        tailGap = tailGap,
        mergeableCount = mergeableCount,
        mergeable = mergeable,
        removeDeletes = removeDeletes,
        createdInLevel = createdInLevel
      )
    } flatMap {
      mergeResult =>
        commitFragments(
          fragments = mergeResult.result,
          createdInLevel = createdInLevel
        ) map {
          persistentSegments =>
            mergeResult.updateResult(persistentSegments)
        }
    }

  /**
   * Builds a [[Future]] pipeline that executes assignment, defragmentation and merge on multiple Segments. This is
   * used by [[PersistentSegmentMany]].
   *
   * @return [[MergeResult.source]] is true if this Segment was replaced or else it will be false.
   *         [[swaydb.core.segment.ref.SegmentRef]] is not being used here because the input is an [[Iterator]] of [[SEG]].
   */
  def runMany[SEG >: Null, NULL_SEG >: SEG](headGap: ListBuffer[Assignable.Gap[MergeStats.Persistent.Builder[Memory, ListBuffer]]],
                                            tailGap: ListBuffer[Assignable.Gap[MergeStats.Persistent.Builder[Memory, ListBuffer]]],
                                            nullSegment: NULL_SEG,
                                            segments: => Iterator[SEG],
                                            assignableCount: Int,
                                            assignables: Iterator[Assignable],
                                            removeDeletes: Boolean,
                                            createdInLevel: Int)(implicit idGenerator: IDGenerator,
                                                                 executionContext: ExecutionContext,
                                                                 keyOrder: KeyOrder[Slice[Byte]],
                                                                 timeOrder: TimeOrder[Slice[Byte]],
                                                                 functionStore: FunctionStore,
                                                                 segmentSource: SegmentSource[SEG],
                                                                 valuesConfig: ValuesBlock.Config,
                                                                 sortedIndexConfig: SortedIndexBlock.Config,
                                                                 binarySearchIndexConfig: BinarySearchIndexBlock.Config,
                                                                 hashIndexConfig: HashIndexBlock.Config,
                                                                 bloomFilterConfig: BloomFilterBlock.Config,
                                                                 segmentConfig: SegmentBlock.Config): Future[MergeResult[Boolean, Slice[TransientSegment.Persistent]]] =
    if (assignableCount == 0)
      DefragSegment.runOne[SEG, NULL_SEG](
        segment = Option.empty[SEG],
        nullSegment = nullSegment,
        headGap = headGap,
        tailGap = tailGap,
        mergeableCount = 0,
        mergeable = Assignable.emptyIterator,
        removeDeletes = removeDeletes,
        createdInLevel = createdInLevel
      ) map {
        result =>
          assert(result.source == nullSegment, s"${result.source} is not $nullSegment")
          result.updateSource(false)
      }
    else
      Futures
        .unit
        .flatMapUnit {
          val headFragmentsFuture =
            if (headGap.isEmpty)
              Future.successful(ListBuffer.empty[TransientSegment.Fragment])
            else
              Future {
                DefragGap.run(
                  gap = headGap,
                  fragments = ListBuffer.empty,
                  removeDeletes = removeDeletes,
                  createdInLevel = createdInLevel,
                  hasNext = false
                )
              }

          val assignmentsFuture =
            Future {
              assignAllSegments(
                segments = segments,
                assignableCount = assignableCount,
                assignables = assignables,
                removeDeletes = removeDeletes
              )
            }

          for {
            headFragments <- headFragmentsFuture
            assignments <- assignmentsFuture
          } yield (headFragments, assignments)
        }
        .flatMap {
          case (headFragments, assignments) =>
            Future.traverse(assignments) {
              assignment =>
                Future {
                  Defrag.run(
                    segment = Some(assignment.segment),
                    nullSegment = nullSegment,
                    fragments = ListBuffer.empty,
                    headGap = assignment.headGap.result,
                    tailGap = assignment.tailGap.result,
                    mergeableCount = assignment.midOverlap.size,
                    mergeable = assignment.midOverlap.iterator,
                    removeDeletes = removeDeletes,
                    createdInLevel = createdInLevel
                  )
                }
            } map {
              buffer =>
                headFragments ++= buffer.flatMap(_.result)
            }
        }
        .map {
          fragments: ListBuffer[TransientSegment.Fragment] =>
            if (tailGap.isEmpty)
              fragments
            else
              DefragGap.run(
                gap = tailGap,
                fragments = fragments,
                removeDeletes = removeDeletes,
                createdInLevel = createdInLevel,
                hasNext = false
              )
        }
        .flatMap {
          fragments =>
            commitFragments(
              fragments = fragments,
              createdInLevel = createdInLevel
            ) map {
              transientSegments =>
                MergeResult(
                  source = true, //replaced
                  result = transientSegments
                )
            }
        }

  /**
   * Assigns key-values [[Assignable]]s to segments [[SEG]].
   */
  def assignAllSegments[SEG >: Null](segments: Iterator[SEG],
                                     assignableCount: Int,
                                     assignables: Iterator[Assignable],
                                     removeDeletes: Boolean)(implicit keyOrder: KeyOrder[Slice[Byte]],
                                                             segmentSource: SegmentSource[SEG]): ListBuffer[SegmentAssignment[ListBuffer[Assignable.Gap[Persistent.Builder[Memory, ListBuffer]]], SEG]] = {
    implicit val creator: Aggregator.Creator[Assignable, ListBuffer[Assignable.Gap[Persistent.Builder[Memory, ListBuffer]]]] =
      GapAggregator.persistent(removeDeletes)

    val (segmentsIterator, segmentsIteratorDuplicate) = segments.duplicate

    //assign key-values to Segment and then perform merge.
    val assignments =
      SegmentAssigner.assignUnsafeGaps[ListBuffer[Assignable.Gap[MergeStats.Persistent.Builder[Memory, ListBuffer]]], SEG](
        assignablesCount = assignableCount,
        assignables = assignables,
        segments = segmentsIterator
      )

    val nothingAssignableGap = nothingAggregator[Assignable]()

    val hasMissing =
      segmentsIteratorDuplicate.foldLeft(false) {
        case (missing, segment) =>
          if (!assignments.exists(_.segment == segment)) {
            assignments +=
              SegmentAssignment(
                segment = segment,
                headGap = nothingAssignableGap,
                midOverlap = ListBuffer.empty,
                tailGap = nothingAssignableGap
              )

            true
          } else {
            missing
          }
      }

    if (hasMissing)
      assignments.sortBy(_.segment.minKey)(keyOrder)
    else
      assignments
  }

  /**
   * Converts [[MergeStats]] to [[TransientSegment.One]] instances for grouping.
   */
  def commitFragments(fragments: ListBuffer[TransientSegment.Fragment],
                      createdInLevel: Int)(implicit executionContext: ExecutionContext,
                                           keyOrder: KeyOrder[Slice[Byte]],
                                           timeOrder: TimeOrder[Slice[Byte]],
                                           functionStore: FunctionStore,
                                           valuesConfig: ValuesBlock.Config,
                                           sortedIndexConfig: SortedIndexBlock.Config,
                                           binarySearchIndexConfig: BinarySearchIndexBlock.Config,
                                           hashIndexConfig: HashIndexBlock.Config,
                                           bloomFilterConfig: BloomFilterBlock.Config,
                                           segmentConfig: SegmentBlock.Config): Future[Slice[TransientSegment.Persistent]] =
    Future.traverse(fragments) {
      case TransientSegment.Stats(stats) =>
        Future {
          val mergeStats =
            stats.close(
              hasAccessPositionIndex = sortedIndexConfig.enableAccessPositionIndex,
              optimiseForReverseIteration = sortedIndexConfig.optimiseForReverseIteration
            )

          SegmentBlock.writeOnes(
            mergeStats = mergeStats,
            createdInLevel = createdInLevel,
            bloomFilterConfig = bloomFilterConfig,
            hashIndexConfig = hashIndexConfig,
            binarySearchIndexConfig = binarySearchIndexConfig,
            sortedIndexConfig = sortedIndexConfig,
            valuesConfig = valuesConfig,
            segmentConfig = segmentConfig
          )
        }

      case segment: TransientSegment.Remote =>
        Future.successful(Slice(segment))

      case TransientSegment.Fence =>
        TransientSegment.Fence.futureSuccessful

    } flatMap {
      singletonOrFence =>
        commitFencedSingletons(
          singletonOrFence = singletonOrFence,
          createdInLevel = createdInLevel
        )
    }

  /**
   * Groups [[TransientSegment.Singleton]] for persistence and does a final check and expand small Segments.
   */
  def commitFencedSingletons(singletonOrFence: ListBuffer[Slice[TransientSegment.SingletonOrFence]],
                             createdInLevel: Int)(implicit executionContext: ExecutionContext,
                                                  keyOrder: KeyOrder[Slice[Byte]],
                                                  valuesConfig: ValuesBlock.Config,
                                                  sortedIndexConfig: SortedIndexBlock.Config,
                                                  binarySearchIndexConfig: BinarySearchIndexBlock.Config,
                                                  hashIndexConfig: HashIndexBlock.Config,
                                                  segmentConfig: SegmentBlock.Config): Future[Slice[TransientSegment.Persistent]] = {
    val singletonOrFences = Slice.of[TransientSegment.SingletonOrFence](singletonOrFence.foldLeft(0)(_ + _.size))
    singletonOrFence foreach singletonOrFences.addAll

    val groups = ListBuffer(ListBuffer.empty[TransientSegment.OneOrRemoteRef])

    val remoteSegments = ListBuffer.empty[TransientSegment.RemoteSegment]

    @inline def startNewGroup(): Unit =
      if (groups.last.nonEmpty)
        groups += ListBuffer.empty

    //do another check and expand small Segments.
    singletonOrFences foreach {
      case ref: TransientSegment.RemoteRef =>
        groups.last += ref

      case remoteSegment: TransientSegment.RemoteSegment =>
        if (remoteSegment.segment.segmentSize < segmentConfig.minSize) {
          remoteSegment.segment match {
            case _: MemorySegment =>
              startNewGroup()
              remoteSegments += remoteSegment

            case segment: PersistentSegment =>
              segment match {
                case many: PersistentSegmentMany =>
                  many.segmentRefsIterator() foreach (ref => groups.last += TransientSegment.RemoteRef(ref))

                case one: PersistentSegmentOne =>
                  groups.last += TransientSegment.RemoteRef(one.ref)
              }
          }
        } else {
          startNewGroup()
          remoteSegments += remoteSegment
        }

      case one: TransientSegment.One =>
        groups.last += one

      case TransientSegment.Fence =>
        startNewGroup()
    }

    commitGroups(
      groups = groups,
      remoteSegments = remoteSegments,
      createdInLevel = createdInLevel
    )
  }

  /**
   * Commits Groups.
   */
  private def commitGroups(groups: Iterable[Iterable[TransientSegment.OneOrRemoteRef]],
                           remoteSegments: Iterable[TransientSegment.RemoteSegment],
                           createdInLevel: Int)(implicit executionContext: ExecutionContext,
                                                keyOrder: KeyOrder[Slice[Byte]],
                                                valuesConfig: ValuesBlock.Config,
                                                sortedIndexConfig: SortedIndexBlock.Config,
                                                binarySearchIndexConfig: BinarySearchIndexBlock.Config,
                                                hashIndexConfig: HashIndexBlock.Config,
                                                segmentConfig: SegmentBlock.Config): Future[Slice[TransientSegment.Persistent]] =
    Future.traverse(Slice.from(groups, groups.size)) {
      ones =>
        Future {
          SegmentBlock.writeOneOrMany(
            createdInLevel = createdInLevel,
            ones = Slice.from(ones, ones.size),
            sortedIndexConfig = sortedIndexConfig,
            hashIndexConfig = hashIndexConfig,
            binarySearchIndexConfig = binarySearchIndexConfig,
            valuesConfig = valuesConfig,
            segmentConfig = segmentConfig
          )
        }
    } map {
      slices =>
        val slice = Slice.of[TransientSegment.Persistent](slices.foldLeft(remoteSegments.size)(_ + _.size))

        @inline def addAll(segments: Iterable[TransientSegment.Persistent]): Unit =
          segments foreach {
            case ref: TransientSegment.RemoteRef =>
              //if a single SegmentRef is being written as an independent Segment then set the header.
              slice add ref.copyWithFileHeader(PersistentSegmentOne.formatIdSlice)

            case transient =>
              slice add transient
          }

        slices foreach addAll

        if (remoteSegments.nonEmpty) {
          addAll(remoteSegments)
          slice.sortBy(_.minKey)(keyOrder)
        } else {
          slice
        }
    }
}