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

import swaydb.config.accelerate.LevelZeroMeter
import swaydb.config.compaction.LevelMeter
import swaydb.core.util.Bytes
import swaydb.serializers.Serializer
import swaydb.slice.Slice
import swaydb.slice.order.KeyOrder
import swaydb.stream.{From, SourceFree}

import java.nio.file.Path
import scala.collection.compat.IterableOnce
import scala.collection.mutable
import scala.concurrent.duration.{Deadline, FiniteDuration}

object SetMap {
  /**
   * Combines two serialisers into a single Serialiser.
   */
  def serialiser[A, B](aSerializer: Serializer[A],
                       bSerializer: Serializer[B]): Serializer[(A, B)] =
    new Serializer[(A, B)] {
      override def write(data: (A, B)): Slice[Byte] = {
        val keyBytes = aSerializer.write(data._1)

        val valueBytes =
          if (data._2 == null)
            Slice.emptyBytes //value can be null when
          else
            bSerializer.write(data._2)

        Slice
          .allocate[Byte](Bytes.sizeOfUnsignedInt(keyBytes.size) + keyBytes.size + Bytes.sizeOfUnsignedInt(valueBytes.size) + valueBytes.size)
          .addUnsignedInt(keyBytes.size)
          .addAll(keyBytes)
          .addUnsignedInt(valueBytes.size)
          .addAll(valueBytes)
      }

      override def read(slice: Slice[Byte]): (A, B) = {
        val reader = slice.createReader()

        val keyBytes = reader.read(reader.readUnsignedInt())
        val valuesBytes = reader.read(reader.readUnsignedInt())

        val key = aSerializer.read(keyBytes)
        val value = bSerializer.read(valuesBytes)
        (key, value)
      }
    }

  /**
   * Partial ordering based on [[SetMap.serialiser]].
   */
  def ordering[K](defaultOrdering: Either[KeyOrder[Slice[Byte]], KeyOrder[K]])(implicit keySerializer: Serializer[K]): KeyOrder[Slice[Byte]] =
    defaultOrdering match {
      case Left(untypedOrdering) =>
        new KeyOrder[Slice[Byte]] {
          override def compare(left: Slice[Byte], right: Slice[Byte]): Int = {
            val readerLeft = left.createReader()
            val readerRight = right.createReader()

            val leftKey = readerLeft.read(readerLeft.readUnsignedInt())
            val rightKey = readerRight.read(readerRight.readUnsignedInt())

            untypedOrdering.compare(leftKey, rightKey)
          }

          private[swaydb] override def comparableKey(key: Slice[Byte]): Slice[Byte] = {
            val reader = key.createReader()
            reader.read(reader.readUnsignedInt())
          }
        }

      case Right(typedOrdering) =>
        new KeyOrder[Slice[Byte]] {
          override def compare(left: Slice[Byte], right: Slice[Byte]): Int = {
            val readerLeft = left.createReader()
            val readerRight = right.createReader()

            val leftUntypedKey = readerLeft.read(readerLeft.readUnsignedInt())
            val rightUntypedKey = readerRight.read(readerRight.readUnsignedInt())

            val leftTypedKey = keySerializer.read(leftUntypedKey)
            val rightTypedKey = keySerializer.read(rightUntypedKey)

            typedOrdering.compare(leftTypedKey, rightTypedKey)
          }

          private[swaydb] override def comparableKey(key: Slice[Byte]): Slice[Byte] = {
            val reader = key.createReader()
            reader.read(reader.readUnsignedInt())
          }
        }
    }
}

/**
 * A [[SetMap]] is a simple wrapper around [[Set]] to provide
 * [[Map]] like API on [[Set]] storage format.
 *
 * [[SetMap]] has limited write APIs as compared to [[swaydb.Map]]
 * and range & update operations are not supported.
 */
case class SetMap[K, V, BAG[_]] private(private val set: Set[(K, V), Nothing, BAG])(implicit bag: Bag[BAG]) extends SetMapT[K, V, BAG] { self =>

  private final val nullValue: V = null.asInstanceOf[V]

  def path: Path =
    set.path

  def put(key: K, value: V): BAG[Unit] =
    set.add((key, value))

  def put(key: K, value: V, expireAfter: FiniteDuration): BAG[Unit] =
    set.add((key, value), expireAfter.fromNow)

  def put(key: K, value: V, expireAt: Deadline): BAG[Unit] =
    set.add((key, value), expireAt)

  def put(keyValues: (K, V)*): BAG[Unit] =
    put(keyValues)

  def put(keyValues: Stream[(K, V), BAG]): BAG[Unit] =
    set.add(keyValues)

  def put(keyValues: IterableOnce[(K, V)]): BAG[Unit] =
    set.add(keyValues)

  def remove(key: K): BAG[Unit] =
    set.remove((key, nullValue))

  def remove(keys: K*): BAG[Unit] =
    set.remove(keys.map(key => (key, nullValue)))

  def remove(keys: Stream[K, BAG]): BAG[Unit] =
    set.remove(keys.map((_, nullValue)))

  def remove(keys: IterableOnce[K]): BAG[Unit] =
    set.remove(keys.map((_, nullValue)))

  def expire(key: K, after: FiniteDuration): BAG[Unit] =
    set.expire((key, nullValue), after)

  def expire(key: K, at: Deadline): BAG[Unit] =
    set.expire((key, nullValue), at)

  def expiration(key: K): BAG[Option[Deadline]] =
    set.expiration((key, nullValue))

  def clearKeyValues(): BAG[Unit] =
    set.clear()

  /**
   * Returns target value for the input key.
   */
  def get(key: K): BAG[Option[V]] =
    bag.map(set.get((key, nullValue)))(_.map(_._2))

  /**
   * Returns target full key for the input partial key.
   *
   * This function is mostly used for Set databases where partial ordering on the Key is provided.
   */
  def getKey(key: K): BAG[Option[K]] =
    bag.map(set.get((key, nullValue)))(_.map(_._1))

  def getKeyValue(key: K): BAG[Option[(K, V)]] =
    set.get((key, nullValue))

  def contains(key: K): BAG[Boolean] =
    set.contains((key, nullValue))

  def mightContain(key: K): BAG[Boolean] =
    set.mightContain((key, nullValue))

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

  def timeLeft(key: K): BAG[Option[FiniteDuration]] =
    bag.map(expiration(key))(_.map(_.timeLeft))

  override def head: BAG[Option[(K, V)]] =
    set.head

  private[swaydb] def free: SourceFree[K, (K, V)] =
    new SourceFree[K, (K, V)](None, false) {

      var innerSource: SourceFree[(K, V), (K, V)] = _

      override private[swaydb] def headOrNull[BAG[_]](from: Option[From[K]], reverse: Boolean)(implicit bag: Bag[BAG]) = {
        innerSource =
          from match {
            case Some(from) =>
              val start =
                if (from.before)
                  set.before((from.key, nullValue))
                else if (from.after)
                  set.after((from.key, nullValue))
                else if (from.orBefore)
                  set.fromOrBefore((from.key, nullValue))
                else if (from.orAfter)
                  set.fromOrAfter((from.key, nullValue))
                else
                  set.from((from.key, nullValue))

              if (reverse)
                start
                  .reverse
                  .free
              else
                start
                  .free

            case None =>
              if (reverse)
                set
                  .reverse
                  .free
              else
                set.free
          }

        innerSource.headOrNull
      }

      override private[swaydb] def nextOrNull[BAG[_]](previous: (K, V), reverse: Boolean)(implicit bag: Bag[BAG]) =
        innerSource.nextOrNull(previous, reverse)
    }

  override def keys: Stream[K, BAG] =
    self.map(_._1)

  override def values: Stream[V, BAG] =
    self.map(_._2)

  def sizeOfBloomFilterEntries: BAG[Int] =
    set.sizeOfBloomFilterEntries

  def isEmpty: BAG[Boolean] =
    set.isEmpty

  def nonEmpty: BAG[Boolean] =
    set.nonEmpty

  override def last: BAG[Option[(K, V)]] =
    set.last

  private def copy(): Unit = ()

  override def asScala: mutable.Map[K, V] =
    ScalaMap[K, V](toBag[Glass](Bag.glass))

  override private[swaydb] def keySet: mutable.Set[K] =
    ScalaSet[K, V](toBag[Glass](Bag.glass), nullValue)

  /**
   * Returns an Async API of type O where the [[Bag]] is known.
   */
  def toBag[X[_]](implicit bag: Bag[X]): SetMap[K, V, X] =
    SetMap(set = set.toBag[X])

  def close(): BAG[Unit] =
    set.close()

  def delete(): BAG[Unit] =
    set.delete()

  override def equals(other: Any): Boolean =
    other match {
      case other: SetMap[_, _, _] =>
        other.path == this.path

      case _ =>
        false
    }

  override def hashCode(): Int =
    path.hashCode()

  override def toString(): String =
    s"SetMap(path = $path)"

}
