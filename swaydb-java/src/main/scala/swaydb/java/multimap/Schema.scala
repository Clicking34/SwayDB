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

package swaydb.java.multimap

import java.time.Duration
import java.util.Optional

import swaydb.Bag.Less
import swaydb.java.data.util.Java._
import swaydb.java.{MultiMap, Stream}
import swaydb.{Bag, Expiration, PureFunction}

import scala.compat.java8.DurationConverters._

case class Schema[M, K, V, F](asScala: swaydb.multimap.Schema[M, K, V, F, Bag.Less])(implicit evd: F <:< PureFunction.Map[K, V]) {

  def defaultExpiration: Optional[Expiration] =
    asScala.defaultExpiration.asJava

  /**
   * Creates new or initialises the existing map.
   */
  def init(mapKey: M): MultiMap[M, K, V, F] =
    MultiMap(asScala.init(mapKey))

  def init[K2 <: K](mapKey: M, keyType: Class[K2]): MultiMap[M, K2, V, F] =
    MultiMap(asScala.init(mapKey, keyType))(evd.asInstanceOf[F <:< PureFunction.Map[K2, V]])

  def init[K2 <: K, V2 <: V](mapKey: M, keyType: Class[K2], valueType: Class[V2]): MultiMap[M, K2, V2, F] =
    MultiMap(asScala.init(mapKey, keyType, valueType))(evd.asInstanceOf[F <:< PureFunction.Map[K2, V2]])

  /**
   * Creates new or initialises the existing map.
   */
  def init(mapKey: M, expireAfter: Duration): MultiMap[M, K, V, F] =
    MultiMap(asScala.init(mapKey, expireAfter.toScala))

  def init[K2 <: K](mapKey: M, keyType: Class[K2], expireAfter: Duration): MultiMap[M, K2, V, F] =
    MultiMap(asScala.init(mapKey, keyType, expireAfter.toScala))(evd.asInstanceOf[F <:< PureFunction.Map[K2, V]])

  def init[K2 <: K, V2 <: V](mapKey: M, keyType: Class[K2], valueType: Class[V2], expireAfter: Duration): MultiMap[M, K2, V2, F] =
    MultiMap(asScala.init(mapKey, keyType, valueType, expireAfter.toScala))(evd.asInstanceOf[F <:< PureFunction.Map[K2, V2]])


  /**
   * Clears existing entries before creating the Map.
   *
   * @note Put has slower immediate write performance for preceding key-value entries.
   *       Always use [[init]] if clearing existing entries is not required.
   */
  def replace(mapKey: M): MultiMap[M, K, V, F] =
    MultiMap(asScala.replace(mapKey))

  def replace[K2 <: K](mapKey: M, keyType: Class[K2]): MultiMap[M, K2, V, F] =
    MultiMap(asScala.replace(mapKey, keyType))(evd.asInstanceOf[F <:< PureFunction.Map[K2, V]])

  def replace[K2 <: K, V2 <: V](mapKey: M, keyType: Class[K2], valueType: Class[V2]): MultiMap[M, K2, V2, F] =
    MultiMap(asScala.replace(mapKey, keyType, valueType))(evd.asInstanceOf[F <:< PureFunction.Map[K2, V2]])


  /**
   * Clears existing entries before creating the Map.
   *
   * @note Put has slower immediate write performance for preceding key-value entries.
   *       Always use [[init]] if clearing existing entries is not required.
   */
  def replace(mapKey: M, expireAfter: Duration): MultiMap[M, K, V, F] =
    MultiMap(asScala.replace(mapKey, expireAfter.toScala))

  def replace[K2 <: K](mapKey: M, keyType: Class[K2], expireAfter: Duration): MultiMap[M, K2, V, F] =
    MultiMap(asScala.replace(mapKey, keyType, expireAfter.toScala))(evd.asInstanceOf[F <:< PureFunction.Map[K2, V]])

  def replace[K2 <: K, V2 <: V](mapKey: M, keyType: Class[K2], valueType: Class[V2], expireAfter: Duration): MultiMap[M, K2, V2, F] =
    MultiMap(asScala.replace(mapKey, keyType, valueType, expireAfter.toScala))(evd.asInstanceOf[F <:< PureFunction.Map[K2, V2]])

  /**
   * @return false if the map does not exist else true on successful remove.
   */
  def remove(mapKey: M): Boolean =
    asScala.remove(mapKey)

  /**
   * Returns the child Map
   */
  def get(mapKey: M): Optional[MultiMap[M, K, V, F]] =
    asScala.get(mapKey) match {
      case Some(map) =>
        Optional.of(MultiMap(map))

      case None =>
        Optional.empty()
    }

  def get[K2 <: K](mapKey: M, keyType: Class[K2]): Optional[MultiMap[M, K2, V, F]] =
    asScala.get(mapKey, keyType) match {
      case Some(map) =>
        Optional.of(MultiMap(map)(evd.asInstanceOf[F <:< PureFunction.Map[K2, V]]))

      case None =>
        Optional.empty()
    }

  def get[K2 <: K, V2 <: V](mapKey: M, keyType: Class[K2], valueType: Class[V2]): Optional[MultiMap[M, K2, V2, F]] =
    asScala.get(mapKey, keyType, valueType) match {
      case Some(map) =>
        Optional.of(MultiMap(map)(evd.asInstanceOf[F <:< PureFunction.Map[K2, V2]]))

      case None =>
        Optional.empty()
    }

  /**
   * Keys of all child Maps.
   */
  def keys: Stream[M] =
    Stream.fromScala(asScala.keys)

  def flatten: Stream[swaydb.MultiMap[M, K, V, F, Less]] =
    new Stream(asScala.flatten)

  def stream: Stream[MultiMap[M, K, V, F]] =
    Stream.fromScala(asScala.stream.map(MultiMap(_)))

  def isEmpty: Boolean =
    asScala.isEmpty

  def nonEmpty: Boolean =
    asScala.nonEmpty
}
