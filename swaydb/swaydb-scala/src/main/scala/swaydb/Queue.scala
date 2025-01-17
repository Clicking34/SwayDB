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

package swaydb

import com.typesafe.scalalogging.LazyLogging
import swaydb.config.accelerate.LevelZeroMeter
import swaydb.config.compaction.LevelMeter
import swaydb.core.util.Bytes
import swaydb.serializers.Serializer
import swaydb.slice.Slice
import swaydb.slice.order.KeyOrder
import swaydb.stream.StreamFree

import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import scala.annotation.tailrec
import scala.collection.compat.IterableOnce
import scala.concurrent.duration.{Deadline, FiniteDuration}

object Queue {

  private[swaydb] def fromSet[A, BAG[_]](set: swaydb.Set[(Long, A), Nothing, BAG])(implicit bag: Bag[BAG]): BAG[Queue[A]] =
    bag.flatMap(set.head) {
      headOption =>
        bag.map(set.last) {
          lastOption =>

            val first: Long =
              headOption match {
                case Some((first, _)) =>
                  first

                case None =>
                  0
              }

            val last: Long =
              lastOption match {
                case Some((used, _)) =>
                  used + 1

                case None =>
                  0
              }

            swaydb.Queue(
              set = set.toBag[Glass],
              pushIds = new AtomicLong(last),
              popIds = new AtomicLong(first)
            )
        }
    }

  /**
   * Combines two serialisers into a single Serialiser.
   */
  def serialiser[A](bSerializer: Serializer[A]): Serializer[(Long, A)] =
    new Serializer[(Long, A)] {
      override def write(data: (Long, A)): Slice[Byte] = {
        val valueBytes =
          if (data._2 == null)
            Slice.emptyBytes //value can be null when
          else
            bSerializer.write(data._2)

        Slice
          .allocate[Byte](Bytes.sizeOfUnsignedLong(data._1) + Bytes.sizeOfUnsignedInt(valueBytes.size) + valueBytes.size)
          .addUnsignedLong(data._1)
          .addUnsignedInt(valueBytes.size)
          .addAll(valueBytes)
      }

      override def read(slice: Slice[Byte]): (Long, A) = {
        val reader = slice.createReader()

        val key = reader.readUnsignedLong()

        val valuesBytes = reader.read(reader.readUnsignedInt())
        val value = bSerializer.read(valuesBytes)

        (key, value)
      }
    }

  /**
   * Partial ordering based on [[SetMap.serialiser]].
   */
  def ordering: KeyOrder[Slice[Byte]] =
    new KeyOrder[Slice[Byte]] {
      override def compare(left: Slice[Byte], right: Slice[Byte]): Int =
        left.readUnsignedLong() compare right.readUnsignedLong()

      private[swaydb] override def comparableKey(key: Slice[Byte]): Slice[Byte] = {
        val (_, byteSize) = key.readUnsignedLongWithByteSize()
        key.take(byteSize)
      }
    }
}

/**
 * Provides a [[Set]] instance the ability to be used as a queue.
 */
case class Queue[A] private(private val set: Set[(Long, A), Nothing, Glass],
                            private val pushIds: AtomicLong,
                            private val popIds: AtomicLong) extends Stream[A, Glass]()(Bag.glass) with LazyLogging {

  private val nullA = null.asInstanceOf[A]

  /**
   * Stores all failed items that get processed first before picking
   * next task from [[set]].
   */
  private val retryQueue = new ConcurrentLinkedQueue[java.lang.Long]()

  def path: Path =
    set.path

  def push(elem: A): Unit =
    set.add((pushIds.getAndIncrement(), elem))

  def push(elem: A, expireAfter: FiniteDuration): Unit =
    set.add((pushIds.getAndIncrement(), elem), expireAfter.fromNow)

  def push(elem: A, expireAt: Deadline): Unit =
    set.add((pushIds.getAndIncrement(), elem), expireAt)

  def push(elems: A*): Unit =
    push(elems)

  def push(elems: Stream[A, Glass]): Unit =
    set.add {
      elems.map {
        item =>
          (pushIds.getAndIncrement(), item)
      }
    }

  def push(elems: IterableOnce[A]): Unit =
    set.add {
      elems.map {
        item =>
          (pushIds.getAndIncrement(), item)
      }
    }

  def pop(): Option[A] =
    Option(popOrElse(nullA))

  def popOrNull(): A =
    popOrElse(nullA)

  /**
   * Safely pick the next job.
   */
  @inline private def popAndRecoverOrNull(nextId: Long): A =
    try
      set.get((nextId, nullA)) match {
        case Some(keyValue) =>
          set.remove(keyValue)
          keyValue._2

        case None =>
          nullA
      }
    catch {
      case throwable: Throwable =>
        retryQueue add nextId
        logger.warn(s"Failed to process taskId: $nextId. Added it to retry queue.", throwable)
        nullA
    }

  /**
   * If threads were racing forward than there were actual items to process, then reset
   * [[popIds]] so that the race/overflow is controlled and pushed back to the last
   * queued job.
   */
  @inline private def brakeRecover(nextId: Long): Boolean =
    popIds.get() == nextId && {
      try {
        val headOrNull = set.headOrNull
        //Only the last thread that accessed popIds can reset the popIds counter and continue
        //processing to avoid any conflicting updates.
        headOrNull != null && popIds.compareAndSet(nextId, headOrNull._1)
      } catch {
        case throwable: Throwable =>
          logger.error(s"Failed to brakeRecover taskId: $nextId", throwable)
          false
      }
    }

  @tailrec
  final def popOrElse[B <: A](orElse: => B): A =
    if (popIds.get() < pushIds.get()) {
      //check if there is a previously failed job to process
      val retryId = retryQueue.poll()

      val nextId: Long =
        if (retryId == null)
          popIds.getAndIncrement() //pick next job
        else
          retryId

      //pop the next job from the map safely.
      val valueOrNull = popAndRecoverOrNull(nextId)

      if (valueOrNull == null)
        if (brakeRecover(nextId + 1))
          popOrElse(orElse)
        else
          orElse
      else
        valueOrNull
    } else {
      orElse
    }

  override private[swaydb] def free: StreamFree[A] =
    set.free.map(_._2)

  private def copy(): Unit = ()

  def levelZeroMeter: LevelZeroMeter =
    set.levelZeroMeter

  def levelMeter(levelNumber: Int): Option[LevelMeter] =
    set.levelMeter(levelNumber)

  def sizeOfSegments: Long =
    set.sizeOfSegments

  def blockCacheSize(): Option[Long] =
    set.blockCacheSize()

  def cachedKeyValuesSize(): Option[Long] =
    set.cachedKeyValuesSize()

  def openedFiles(): Option[Long] =
    set.openedFiles()

  def pendingDeletes(): Option[Long] =
    set.pendingDeletes()

  def close(): Unit =
    set.close()

  def delete(): Unit =
    set.delete()

  override def equals(other: Any): Boolean =
    other match {
      case other: Queue[_] =>
        other.path == this.path

      case _ =>
        false
    }

  override def hashCode(): Int =
    path.hashCode()

  override def toString(): String =
    s"Queue(path = $path)"
}
