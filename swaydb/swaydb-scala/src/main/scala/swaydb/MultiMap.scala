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
import swaydb.core.file.ForceSaveApplier
import swaydb.core.file.sweeper.bytebuffer.ByteBufferSweeper.ByteBufferSweeperActor
import swaydb.core.log.counter.{CounterLog, PersistentCounterLog}
import swaydb.core.util.Times._
import swaydb.multimap.{MultiKey, MultiPrepare, MultiValue, Schema}
import swaydb.serializers.{Serializer, _}
import swaydb.slice.Slice
import swaydb.stream.{From, SourceFree, StreamFree}

import java.nio.file.Path
import scala.collection.compat.IterableOnce
import scala.collection.mutable
import scala.concurrent.duration.{Deadline, FiniteDuration}

object MultiMap {

  val folderName = "gen-multimap"

  //this should start from 1 because 0 will be used for format changes.
  val rootMapId: Long = CounterLog.startId

  private[swaydb] def withPersistentCounter[M, K, V, F, BAG[_]](path: Path,
                                                                mmap: swaydb.config.MMAP.Log,
                                                                map: swaydb.Map[MultiKey[M, K], MultiValue[V], PureFunction[MultiKey[M, K], MultiValue[V], Apply.Map[MultiValue[V]]], BAG])(implicit bag: swaydb.Bag[BAG],
                                                                                                                                                                                            keySerializer: Serializer[K],
                                                                                                                                                                                            mapKeySerializer: Serializer[M],
                                                                                                                                                                                            valueSerializer: Serializer[V]): BAG[MultiMap[M, K, V, F, BAG]] = {
    implicit val writer = swaydb.core.log.serialiser.KeyValueLogEntryWriter.KeyValueLogEntryPutWriter
    implicit val reader = swaydb.core.log.serialiser.KeyValueLogEntryReader.KeyValueLogEntryPutReader
    implicit val core: ByteBufferSweeperActor = map.protectedSweeper
    implicit val forceSaveApplier = ForceSaveApplier.On

    PersistentCounterLog(
      path = path.resolve(MultiMap.folderName),
      mmap = mmap,
      mod = 1000,
      fileSize = 1.mb
    ) match {
      case IO.Right(counter) =>
        implicit val implicitCounter: CounterLog = counter
        swaydb.MultiMap[M, K, V, F, BAG](map)

      case IO.Left(error) =>
        bag.failure(error.exception)
    }
  }

  /**
   * Given the inner [[swaydb.Map]] instance this creates a parent [[MultiMap]] instance.
   */
  private[swaydb] def apply[M, K, V, F, BAG[_]](rootMap: swaydb.Map[MultiKey[M, K], MultiValue[V], PureFunction[MultiKey[M, K], MultiValue[V], Apply.Map[MultiValue[V]]], BAG])(implicit bag: swaydb.Bag[BAG],
                                                                                                                                                                                keySerializer: Serializer[K],
                                                                                                                                                                                mapKeySerializer: Serializer[M],
                                                                                                                                                                                valueSerializer: Serializer[V],
                                                                                                                                                                                counter: CounterLog): BAG[MultiMap[M, K, V, F, BAG]] =
    bag.flatMap(rootMap.isEmpty) {
      isEmpty =>

        def initialEntries: BAG[Unit] =
          rootMap.commit(
            Seq(
              Prepare.Put(MultiKey.Start(MultiMap.rootMapId), MultiValue.None),
              Prepare.Put(MultiKey.KeysStart(MultiMap.rootMapId), MultiValue.None),
              Prepare.Put(MultiKey.KeysEnd(MultiMap.rootMapId), MultiValue.None),
              Prepare.Put(MultiKey.ChildrenStart(MultiMap.rootMapId), MultiValue.None),
              Prepare.Put(MultiKey.ChildrenEnd(MultiMap.rootMapId), MultiValue.None),
              Prepare.Put(MultiKey.End(MultiMap.rootMapId), MultiValue.None)
            )
          )

        //RootMap has empty keys so if this database is new commit initial entries.
        if (isEmpty)
          bag.transform(initialEntries) {
            _ =>
              swaydb.MultiMap[M, K, V, F, BAG](
                multiMap = rootMap,
                mapKey = null.asInstanceOf[M],
                mapId = MultiMap.rootMapId
              )
          }
        else
          bag.success(
            swaydb.MultiMap[M, K, V, F, BAG](
              multiMap = rootMap,
              mapKey = null.asInstanceOf[M],
              mapId = MultiMap.rootMapId
            )
          )
    }

  private[swaydb] def failure(expected: Class[_], actual: Class[_]) = throw new IllegalStateException(s"Internal error: ${expected.getName} expected but found ${actual.getName}.")

  private[swaydb] def failure(expected: String, actual: String) = throw exception(expected, actual)

  private[swaydb] def exception(expected: String, actual: String) = new IllegalStateException(s"Internal error: $expected expected but found $actual.")

  /**
   * Converts [[Prepare]] statements of this [[MultiMap]] to inner [[Map]]'s statements.
   */
  def toInnerPrepare[M, K, V, F](prepare: MultiPrepare[M, K, V, F]): Prepare[MultiKey[M, K], MultiValue[V], PureFunction[MultiKey[M, K], MultiValue[V], Apply.Map[MultiValue[V]]]] =
    toInnerPrepare(prepare.mapId, prepare.defaultExpiration, prepare.prepare)

  /**
   * Converts [[Prepare]] statements of this [[MultiMap]] to inner [[Map]]'s statements.
   */
  def toInnerPrepare[M, K, V, F](mapId: Long, defaultExpiration: Option[Deadline], prepare: Prepare[K, V, F]): Prepare[MultiKey[M, K], MultiValue[V], PureFunction[MultiKey[M, K], MultiValue[V], Apply.Map[MultiValue[V]]]] =
    prepare match {
      case Prepare.Put(key, value, deadline) =>
        Prepare.Put(MultiKey.Key(mapId, key), MultiValue.Their(value), deadline earlier defaultExpiration)

      case Prepare.Remove(from, to, deadline) =>
        to match {
          case Some(to) =>
            Prepare.Remove(MultiKey.Key(mapId, from), Some(MultiKey.Key(mapId, to)), deadline earlier defaultExpiration)

          case None =>
            Prepare.Remove[MultiKey[M, K]](from = MultiKey.Key(mapId, from), to = None, deadline = deadline earlier defaultExpiration)
        }

      case Prepare.Update(from, to, value) =>
        to match {
          case Some(to) =>
            Prepare.Update[MultiKey[M, K], MultiValue[V]](MultiKey.Key(mapId, from), Some(MultiKey.Key(mapId, to)), value = MultiValue.Their(value))

          case None =>
            Prepare.Update[MultiKey[M, K], MultiValue[V]](key = MultiKey.Key(mapId, from), value = MultiValue.Their(value))
        }

      case Prepare.ApplyFunction(from, to, function) =>
        // Temporary solution: casted because the actual instance itself not used internally.
        // Core only uses the String value of function.id which is searched in functionStore to validate function.
        val castedFunction = function.asInstanceOf[PureFunction[MultiKey[M, K], MultiValue[V], Apply.Map[MultiValue[V]]]]

        to match {
          case Some(to) =>
            Prepare.ApplyFunction(from = MultiKey.Key(mapId, from), to = Some(MultiKey.Key(mapId, to)), function = castedFunction)

          case None =>
            Prepare.ApplyFunction(from = MultiKey.Key(mapId, from), to = None, function = castedFunction)
        }

      case Prepare.Add(elem, deadline) =>
        Prepare.Put(MultiKey.Key(mapId, elem), MultiValue.None, deadline earlier defaultExpiration)
    }
}

/**
 * [[MultiMap]] extends [[swaydb.Map]]'s API to allow storing multiple Maps withing a single Map.
 *
 * [[MultiMap]] is just a simple extension that uses custom data types ([[MultiKey]]) and
 * KeyOrder ([[MultiKey.ordering]]) for it's API.
 */
case class MultiMap[M, K, V, F, BAG[_]] private(private val multiMap: Map[MultiKey[M, K], MultiValue[V], PureFunction[MultiKey[M, K], MultiValue[V], Apply.Map[MultiValue[V]]], BAG],
                                                mapKey: M,
                                                private[swaydb] val mapId: Long,
                                                defaultExpiration: Option[Deadline] = None)(implicit keySerializer: Serializer[K],
                                                                                            mapKeySerializer: Serializer[M],
                                                                                            valueSerializer: Serializer[V],
                                                                                            counter: CounterLog,
                                                                                            override val bag: Bag[BAG]) extends Schema[M, K, V, F, BAG](multiMap = multiMap, mapId = mapId, defaultExpiration = defaultExpiration) with MapT[K, V, F, BAG] { self =>

  override def path: Path =
    multiMap.path

  def put(key: K, value: V): BAG[Unit] =
    multiMap.put(MultiKey.Key(mapId, key), MultiValue.Their(value), defaultExpiration)

  def put(key: K, value: V, expireAfter: FiniteDuration): BAG[Unit] =
    put(key, value, expireAfter.fromNow)

  def put(key: K, value: V, expireAt: Deadline): BAG[Unit] =
    multiMap.put(MultiKey.Key(mapId, key), MultiValue.Their(value), defaultExpiration earlier expireAt)

  override def put(keyValues: (K, V)*): BAG[Unit] =
    put(keyValues)

  override def put(keyValues: Stream[(K, V), BAG]): BAG[Unit] = {
    val stream: Stream[Prepare[MultiKey[M, K], MultiValue[V], PureFunction[MultiKey[M, K], MultiValue[V], Apply.Map[MultiValue[V]]]], BAG] =
      keyValues.map {
        case (key, value) =>
          Prepare.Put(MultiKey.Key(mapId, key), MultiValue.Their(value), defaultExpiration)
      }

    multiMap.commit(stream)
  }

  override def put(keyValues: IterableOnce[(K, V)]): BAG[Unit] = {
    val iterator =
      keyValues.map {
        case (key, value) =>
          Prepare.Put(MultiKey.Key(mapId, key), MultiValue.Their(value), defaultExpiration)
      }

    multiMap.commit(iterator)
  }

  def remove(key: K): BAG[Unit] =
    multiMap.remove(MultiKey.Key(mapId, key))

  def remove(from: K, to: K): BAG[Unit] =
    multiMap.remove(MultiKey.Key(mapId, from), MultiKey.Key(mapId, to))

  def remove(keys: K*): BAG[Unit] =
    multiMap.remove {
      keys.map(key => MultiKey.Key(mapId, key))
    }

  def remove(keys: Stream[K, BAG]): BAG[Unit] =
    bag.flatMap(keys.materialize)(remove)

  def remove(keys: IterableOnce[K]): BAG[Unit] =
    multiMap.remove(keys.map(key => MultiKey.Key(mapId, key)))

  def expire(key: K, after: FiniteDuration): BAG[Unit] =
    multiMap.expire(MultiKey.Key(mapId, key), defaultExpiration.earlier(after.fromNow))

  def expire(key: K, at: Deadline): BAG[Unit] =
    multiMap.expire(MultiKey.Key(mapId, key), defaultExpiration.earlier(at))

  def expire(from: K, to: K, after: FiniteDuration): BAG[Unit] =
    multiMap.expire(MultiKey.Key(mapId, from), MultiKey.Key(mapId, to), defaultExpiration.earlier(after.fromNow))

  def expire(from: K, to: K, at: Deadline): BAG[Unit] =
    multiMap.expire(MultiKey.Key(mapId, from), MultiKey.Key(mapId, to), defaultExpiration.earlier(at))

  def expire(keys: (K, Deadline)*): BAG[Unit] =
    bag.suspend(expire(keys))

  def expire(keys: Stream[(K, Deadline), BAG]): BAG[Unit] =
    bag.flatMap(keys.materialize)(expire)

  def expire(keys: IterableOnce[(K, Deadline)]): BAG[Unit] = {
    val iterator: IterableOnce[Prepare.Remove[MultiKey.Key[K]]] =
      keys.map {
        case (key, deadline) =>
          Prepare.Expire(MultiKey.Key(mapId, key), deadline.earlier(defaultExpiration))
      }

    multiMap.commit(iterator)
  }

  def update(key: K, value: V): BAG[Unit] =
    multiMap.update(MultiKey.Key(mapId, key), MultiValue.Their(value))

  def update(from: K, to: K, value: V): BAG[Unit] =
    multiMap.update(MultiKey.Key(mapId, from), MultiKey.Key(mapId, to), MultiValue.Their(value))

  def update(keyValues: (K, V)*): BAG[Unit] = {
    val updates =
      keyValues.map {
        case (key, value) =>
          Prepare.Update(MultiKey.Key(mapId, key), MultiValue.Their(value))
      }

    multiMap.commit(updates)
  }

  def update(keyValues: Stream[(K, V), BAG]): BAG[Unit] =
    bag.flatMap(keyValues.materialize)(update)

  def update(keyValues: IterableOnce[(K, V)]): BAG[Unit] = {
    val updates =
      keyValues.map {
        case (key, value) =>
          Prepare.Update(MultiKey.Key(mapId, key), MultiValue.Their(value))
      }

    multiMap.commit(updates)
  }

  def clearKeyValues(): BAG[Unit] = {
    val entriesStart = MultiKey.KeysStart(mapId)
    val entriesEnd = MultiKey.KeysEnd(mapId)

    val entries =
      Seq(
        Prepare.Remove(entriesStart, entriesEnd),
        Prepare.Put(entriesStart, MultiValue.None),
        Prepare.Put(entriesEnd, MultiValue.None)
      )

    multiMap.commit(entries)
  }

  /**
   * @note In other operations like [[expire]], [[remove]], [[put]] the input expiration value is compared with [[defaultExpiration]]
   *       to get the nearest expiration. But functions does not check if the custom logic within the function expires
   *       key-values earlier than [[defaultExpiration]].
   */
  def applyFunction(key: K, function: F)(implicit evd: F <:< PureFunction.Map[K, V]): BAG[Unit] =
    multiMap.applyFunction(
      MultiKey.Key(mapId, key),
      function.asInstanceOf[PureFunction.Map[MultiKey[M, K], MultiValue[V]]]
    )

  /**
   * @note In other operations like [[expire]], [[remove]], [[put]] the input expiration value is compared with [[defaultExpiration]]
   *       to get the nearest expiration. But functions does not check if the custom logic within the function expires
   *       key-values earlier than [[defaultExpiration]].
   */
  def applyFunction(from: K, to: K, function: F)(implicit evd: F <:< PureFunction.Map[K, V]): BAG[Unit] =
    multiMap.applyFunction(
      from = MultiKey.Key(mapId, from),
      to = MultiKey.Key(mapId, to),
      function = function.asInstanceOf[PureFunction.Map[MultiKey[M, K], MultiValue[V]]]
    )

  /**
   * Commits transaction to global map.
   */
  def commitMultiPrepare(transaction: IterableOnce[MultiPrepare[M, K, V, F]]): BAG[Unit] =
    multiMap.commit {
      transaction map {
        transaction =>
          MultiMap.toInnerPrepare(transaction)
      }
    }

  def commit(prepare: Prepare[K, V, F]*): BAG[Unit] =
    multiMap.commit(prepare.map(prepare => MultiMap.toInnerPrepare(mapId, defaultExpiration, prepare)))

  def commit(prepare: Stream[Prepare[K, V, F], BAG]): BAG[Unit] =
    bag.flatMap(prepare.materialize) {
      prepares =>
        commit(prepares)
    }

  override def commit(prepare: IterableOnce[Prepare[K, V, F]]): BAG[Unit] =
    multiMap.commit(prepare.map(prepare => MultiMap.toInnerPrepare(mapId, defaultExpiration, prepare)))

  def get(key: K): BAG[Option[V]] =
    bag.flatMap(multiMap.get(MultiKey.Key(mapId, key))) {
      case Some(value) =>
        value match {
          case their: MultiValue.Their[V] =>
            bag.success(Some(their.value))

          case _: MultiValue.Our =>
            bag.failure(MultiMap.failure(classOf[MultiValue.Their[_]], classOf[MultiValue.Our]))

        }

      case None =>
        bag.none
    }

  def getKey(key: K): BAG[Option[K]] =
    bag.map(multiMap.getKey(MultiKey.Key(mapId, key))) {
      case Some(MultiKey.Key(_, key)) =>
        Some(key)

      case Some(entry) =>
        MultiMap.failure(MultiKey.Key.getClass, entry.getClass)

      case None =>
        None
    }

  def getKeyValue(key: K): BAG[Option[(K, V)]] =
    bag.map(multiMap.getKeyValue(MultiKey.Key(mapId, key))) {
      case Some((MultiKey.Key(_, key), their: MultiValue.Their[V])) =>
        Some((key, their.value))

      case Some((MultiKey.Key(_, _), _: MultiValue.Our)) =>
        MultiMap.failure(classOf[MultiValue.Their[V]], classOf[MultiValue.Our])

      case Some(entry) =>
        MultiMap.failure(MultiKey.Key.getClass, entry.getClass)

      case None =>
        None
    }

  override def getKeyDeadline(key: K): BAG[Option[(K, Option[Deadline])]] =
    bag.map(multiMap.getKeyDeadline(MultiKey.Key(mapId, key))) {
      case Some((MultiKey.Key(_, key), deadline)) =>
        Some((key, deadline))

      case Some(entry) =>
        MultiMap.failure(MultiKey.Key.getClass, entry.getClass)

      case None =>
        None
    }

  override def getKeyValueDeadline(key: K): BAG[Option[((K, V), Option[Deadline])]] =
    bag.map(multiMap.getKeyValueDeadline(MultiKey.Key(mapId, key))) {
      case Some(((MultiKey.Key(_, key), value: MultiValue.Their[V]), deadline)) =>
        Some(((key, value.value), deadline))

      case Some(((key, value), _)) =>
        throw new Exception(
          s"Expected key ${classOf[MultiKey.Key[_]].getSimpleName}. Got ${key.getClass.getSimpleName}. " +
            s"Expected value ${classOf[MultiValue.Their[_]].getSimpleName}. Got ${value.getClass.getSimpleName}. "
        )

      case None =>
        None
    }

  def contains(key: K): BAG[Boolean] =
    multiMap.contains(MultiKey.Key(mapId, key))

  def mightContain(key: K): BAG[Boolean] =
    multiMap.mightContain(MultiKey.Key(mapId, key))

  def mightContainFunction(function: F)(implicit evd: F <:< PureFunction.Map[K, V]): BAG[Boolean] =
    multiMap.mightContainFunction(function.asInstanceOf[PureFunction.Map[MultiKey[M, K], MultiValue[V]]])

  def keys: Stream[K, BAG] =
    map(_._1)

  def values: Stream[V, BAG] =
    map(_._2)

  private[swaydb] def keySet: mutable.Set[K] =
    throw new NotImplementedError("KeySet function is not yet implemented. Please request for this on GitHub - https://github.com/simerplaha/SwayDB/issues.")

  def levelZeroMeter: LevelZeroMeter =
    multiMap.levelZeroMeter

  def levelMeter(levelNumber: Int): Option[LevelMeter] =
    multiMap.levelMeter(levelNumber)

  def sizeOfSegments: Long =
    multiMap.sizeOfSegments

  def blockCacheSize(): Option[Long] =
    multiMap.blockCacheSize()

  def cachedKeyValuesSize(): Option[Long] =
    multiMap.cachedKeyValuesSize()

  def openedFiles(): Option[Long] =
    multiMap.openedFiles()

  def pendingDeletes(): Option[Long] =
    multiMap.pendingDeletes()

  def keySize(key: K): Int =
    (key: Slice[Byte]).size

  def valueSize(value: V): Int =
    (value: Slice[Byte]).size

  def expiration(key: K): BAG[Option[Deadline]] =
    multiMap.expiration(MultiKey.Key(mapId, key))

  def timeLeft(key: K): BAG[Option[FiniteDuration]] =
    bag.map(expiration(key))(_.map(_.timeLeft))

  override def head: BAG[Option[(K, V)]] =
    bag.transform(headOrNull)(Option(_))

  private def headOrNull(from: Option[From[K]], reverse: Boolean): StreamFree[(K, V)] = {
    val stream =
      from match {
        case Some(from) =>
          val start =
            if (from.before)
              multiMap.before(MultiKey.Key(mapId, from.key))
            else if (from.after)
              multiMap.after(MultiKey.Key(mapId, from.key))
            else if (from.orBefore)
              multiMap.fromOrBefore(MultiKey.Key(mapId, from.key))
            else if (from.orAfter)
              multiMap.fromOrAfter(MultiKey.Key(mapId, from.key))
            else
              multiMap.from(MultiKey.Key(mapId, from.key))

          if (reverse)
            start.reverse
          else
            start

        case None =>
          if (reverse)
            multiMap
              .before(MultiKey.KeysEnd(mapId))
              .reverse
          else
            multiMap
              .after(MultiKey.KeysStart(mapId))
      }

    //restricts this Stream to fetch entries of this Map only.
    stream
      .free
      .takeWhile {
        case (MultiKey.Key(mapId, _), _) =>
          mapId == mapId

        case _ =>
          false
      }
      .collect {
        case (MultiKey.Key(_, key), their: MultiValue.Their[V]) =>
          (key, their.value)
      }
  }

  private[swaydb] def free: SourceFree[K, (K, V)] =
    new SourceFree[K, (K, V)](from = None, reverse = false) {

      var freeStream: StreamFree[(K, V)] = _

      override private[swaydb] def headOrNull[BAG[_]](from: Option[From[K]], reverse: Boolean)(implicit bag: Bag[BAG]) = {
        freeStream = self.headOrNull(from = from, reverse = reverse)
        freeStream.headOrNull
      }

      override private[swaydb] def nextOrNull[BAG[_]](previous: (K, V), reverse: Boolean)(implicit bag: Bag[BAG]) =
        freeStream.nextOrNull(previous)
    }

  def sizeOfBloomFilterEntries: BAG[Int] =
    multiMap.sizeOfBloomFilterEntries

  def isEmpty: BAG[Boolean] =
    bag.map(head)(_.isEmpty)

  def nonEmpty: BAG[Boolean] =
    bag.map(head)(_.nonEmpty)

  override def last: BAG[Option[(K, V)]] =
    stream.reverse.head

  override def clearAppliedFunctions(): BAG[Iterable[String]] =
    multiMap.clearAppliedFunctions()

  override def clearAppliedAndRegisteredFunctions(): BAG[Iterable[String]] =
    multiMap.clearAppliedAndRegisteredFunctions()

  override def isFunctionApplied(function: F)(implicit evd: F <:< PureFunction.Map[K, V]): Boolean =
    multiMap.isFunctionApplied(function.asInstanceOf[PureFunction.Map[MultiKey[M, K], MultiValue[V]]])

  /**
   * Returns an Async API of type O where the [[Bag]] is known.
   */
  def toBag[X[_]](implicit bag: Bag[X]): MultiMap[M, K, V, F, X] =
    MultiMap(
      multiMap = multiMap.toBag[X],
      mapKey = mapKey,
      mapId = mapId,
      defaultExpiration = defaultExpiration
    )

  def asScala: scala.collection.mutable.Map[K, V] =
    ScalaMap[K, V](toBag[Glass](Bag.glass))

  def close(): BAG[Unit] =
    bag.and(bag(counter.close())) {
      multiMap.close()
    }

  def delete(): BAG[Unit] =
    bag.and(bag(counter.close())) {
      multiMap.delete()
    }

  override def equals(other: Any): Boolean =
    other match {
      case other: MultiMap[_, _, _, _, _] =>
        other.path == this.path && other.mapId == this.mapId

      case _ =>
        false
    }

  override def hashCode(): Int =
    mapId.hashCode()

  override def toString(): String =
    s"MultiMap(key = $mapKey, defaultExpiration = $defaultExpiration, path = $path)"
}
