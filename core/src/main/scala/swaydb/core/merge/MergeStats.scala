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

package swaydb.core.merge

import swaydb.Aggregator
import swaydb.core.data
import swaydb.core.segment.entry.id.KeyValueId
import swaydb.core.util.Bytes
import swaydb.data.util.ByteSizeOf

import scala.collection.mutable.ListBuffer

private[core] sealed trait MergeStats[-FROM, +T[_]] extends Aggregator[FROM, T[data.Memory]] {

  def add(keyValue: FROM): Unit

  def addAll(from: Iterable[FROM]): Unit =
    from foreach add

  def keyValues: T[data.Memory]

  override def result: T[data.Memory] =
    keyValues
}

private[core] case object MergeStats {

  private[core] sealed trait Result[-FROM, +T[_]] {
    def keyValues: T[data.Memory]
  }

  implicit val memoryToMemory: data.Memory => data.Memory =
    (memory: data.Memory) => memory

  def persistentBuilder[FROM](keyValues: Iterable[FROM])(implicit convert: FROM => data.Memory): MergeStats.Persistent.Builder[FROM, ListBuffer] = {
    val stats = persistent[FROM, ListBuffer](Aggregator.listBuffer)
    keyValues foreach stats.add
    stats
  }

  def memoryBuilder[FROM](keyValues: Iterable[FROM])(implicit convert: FROM => data.Memory): MergeStats.Memory.Builder[FROM, ListBuffer] = {
    val stats = memory[FROM, ListBuffer](Aggregator.listBuffer)
    keyValues foreach stats.add
    stats
  }

  def bufferBuilder[FROM](keyValues: Iterable[FROM])(implicit convert: FROM => data.Memory): MergeStats.Buffer[FROM, ListBuffer] = {
    val stats = buffer[FROM, ListBuffer](Aggregator.listBuffer)
    keyValues foreach stats.add
    stats
  }

  def persistent[FROM, T[_]](aggregator: Aggregator[swaydb.core.data.Memory, T[swaydb.core.data.Memory]])(implicit converterOrNull: FROM => data.Memory): MergeStats.Persistent.Builder[FROM, T] =
    new Persistent.Builder(
      maxMergedKeySize = 0,
      totalMergedKeysSize = 0,
      maxTimeSize = 0,
      totalTimesSize = 0,
      maxValueSize = 0,
      totalKeyValueCount = 0,
      totalValuesSize = 0,
      totalValuesCount = 0,
      totalDeadlineKeyValues = 0,
      totalRangeCount = 0,
      hasRange = false,
      mightContainRemoveRange = false,
      hasPut = false,
      aggregator = aggregator
    )

  def memory[FROM, T[_]](aggregator: Aggregator[swaydb.core.data.Memory, T[swaydb.core.data.Memory]])(implicit converterOrNull: FROM => data.Memory): MergeStats.Memory.Builder[FROM, T] =
    new Memory.Builder[FROM, T](
      aggregator = aggregator
    )

  def buffer[FROM, T[_]](aggregator: Aggregator[swaydb.core.data.Memory, T[swaydb.core.data.Memory]])(implicit converterOrNull: FROM => data.Memory): MergeStats.Buffer[FROM, T] =
    new Buffer(aggregator)

  sealed trait Persistent[-FROM, +T[_]] {
    def maxSortedIndexSize(hasAccessPositionIndex: Boolean, optimiseForReverseIteration: Boolean): Int
    def size: Int
    def isEmpty: Boolean
    def keyValues: T[data.Memory]

  }

  object Persistent {
    class Builder[-FROM, +T[_]](var maxMergedKeySize: Int,
                                var totalMergedKeysSize: Int,
                                var maxTimeSize: Int,
                                var totalTimesSize: Int,
                                var maxValueSize: Int,
                                var totalKeyValueCount: Int,
                                var totalValuesSize: Int,
                                var totalValuesCount: Int,
                                var totalDeadlineKeyValues: Int,
                                var totalRangeCount: Int,
                                var hasRange: Boolean,
                                var mightContainRemoveRange: Boolean,
                                var hasPut: Boolean,
                                aggregator: Aggregator[swaydb.core.data.Memory, T[swaydb.core.data.Memory]])(implicit converterOrNull: FROM => data.Memory) extends Persistent[FROM, T] with MergeStats[FROM, T] {

      def close(hasAccessPositionIndex: Boolean, optimiseForReverseIteration: Boolean): MergeStats.Persistent.Closed[T] =
        new MergeStats.Persistent.Closed[T](
          isEmpty = this.isEmpty,
          keyValuesCount = size,
          keyValues = keyValues,
          totalValuesSize = totalValuesSize,
          maxSortedIndexSize =
            maxSortedIndexSize(
              hasAccessPositionIndex = hasAccessPositionIndex,
              optimiseForReverseIteration = optimiseForReverseIteration
            )
        )

      def hasDeadline = totalDeadlineKeyValues > 0

      def size = totalKeyValueCount

      def isEmpty: Boolean =
        size == 0

      def keyValues: T[data.Memory] =
        aggregator.result

      /**
       * Format - keySize|key|keyValueId|accessIndex?|previousIndexOffset?|deadline|valueOffset|valueLength|time
       */
      def maxSortedIndexSize(hasAccessPositionIndex: Boolean, optimiseForReverseIteration: Boolean): Int = {
        val maxSize =
          (Bytes.sizeOfUnsignedInt(maxMergedKeySize) * totalKeyValueCount) +
            totalMergedKeysSize +
            (if (hasAccessPositionIndex) Bytes.sizeOfUnsignedInt(totalKeyValueCount) * totalKeyValueCount else 0) +
            (KeyValueId.maxKeyValueIdByteSize * totalKeyValueCount) + //keyValueId
            totalDeadlineKeyValues * ByteSizeOf.varLong + //deadline
            (Bytes.sizeOfUnsignedInt(totalValuesSize) * totalValuesCount) + //valueOffset
            (Bytes.sizeOfUnsignedInt(maxValueSize) * totalValuesCount) + //valueLength
            totalTimesSize

        if (optimiseForReverseIteration)
          maxSize + (Bytes.sizeOfUnsignedInt(maxSize) * totalKeyValueCount)
        else
          maxSize
      }

      def updateStats(keyValue: data.Memory): Unit = {
        maxMergedKeySize = this.maxMergedKeySize max keyValue.mergedKey.size
        totalMergedKeysSize = this.totalMergedKeysSize + keyValue.mergedKey.size

        val persistentTimeSize = keyValue.persistentTime.size
        val timeSize = Bytes.sizeOfUnsignedInt(persistentTimeSize) + persistentTimeSize
        maxTimeSize = this.maxTimeSize max timeSize
        totalTimesSize = this.totalTimesSize + timeSize

        val valueSize = if (keyValue.value.isSomeC) keyValue.value.getC.size else 0
        maxValueSize = this.maxValueSize max valueSize
        totalValuesSize = this.totalValuesSize + valueSize

        if (keyValue.value.existsC(_.nonEmpty))
          totalValuesCount += 1

        if (keyValue.deadline.isDefined)
          totalDeadlineKeyValues = this.totalDeadlineKeyValues + 1

        if (keyValue.isRange) {
          this.hasRange = true
          if (keyValue.mightContainRemoveRange)
            this.mightContainRemoveRange = true
          this.totalRangeCount = this.totalRangeCount + 1
        } else if (keyValue.isPut) {
          this.hasPut = true
        }
      }

      def add(from: FROM): Unit = {
        val keyValueOrNull = converterOrNull(from)
        if (keyValueOrNull != null) {
          totalKeyValueCount += 1
          updateStats(keyValueOrNull)
          aggregator add keyValueOrNull
        }
      }
    }

    class Closed[+T[_]](val isEmpty: Boolean,
                        val keyValuesCount: Int,
                        val keyValues: T[data.Memory],
                        val maxSortedIndexSize: Int,
                        val totalValuesSize: Int)
  }

  object Memory {
    def calculateSize(keyValue: data.Memory): Int =
      keyValue.key.size + {
        if (keyValue.value.isSomeC)
          keyValue.value.getC.size
        else
          0
      }

    class Builder[-FROM, +T[_]](aggregator: Aggregator[swaydb.core.data.Memory, T[swaydb.core.data.Memory]])(implicit converterOrNull: FROM => data.Memory) extends MergeStats[FROM, T] {
      var isEmpty: Boolean = true

      def close: Memory.Closed[T] =
        new Closed[T](
          isEmpty = isEmpty,
          keyValues = keyValues
        )

      override def keyValues: T[data.Memory] =
        aggregator.result

      override def add(from: FROM): Unit = {
        val keyValueOrNull = converterOrNull(from)
        if (keyValueOrNull != null) {
          aggregator add keyValueOrNull
          isEmpty = false
        }
      }
    }

    class Closed[+T[_]](val isEmpty: Boolean,
                        val keyValues: T[data.Memory])
  }

  class Buffer[-FROM, +T[_]](aggregator: Aggregator[swaydb.core.data.Memory, T[swaydb.core.data.Memory]])(implicit converterOrNull: FROM => data.Memory) extends MergeStats[FROM, T] {

    override def add(from: FROM): Unit = {
      val keyValueOrNull = converterOrNull(from)
      if (keyValueOrNull != null)
        aggregator add keyValueOrNull
    }

    override def keyValues: T[data.Memory] =
      aggregator.result
  }
}