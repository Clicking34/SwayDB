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

package swaydb.stream

import swaydb.Bag

/**
 * Provides a starting point from a [[StreamFree]] and implements APIs to dictate [[From]] where
 * there stream should start.
 *
 * @param from    start point for this stream
 * @param reverse runs stream in reverse order
 * @tparam K the type of key
 * @tparam T the type of value.
 */
private[swaydb] abstract class SourceFree[K, T](from: Option[From[K]],
                                                reverse: Boolean) extends StreamFree[T] { self =>

  /**
   * Always invoked once at the begining of the [[StreamFree]].
   *
   * @param from    where to fetch the head from
   * @param reverse if this stream is in reverse order.
   *
   * @return first element of this stream
   */
  private[swaydb] def headOrNull[BAG[_]](from: Option[From[K]], reverse: Boolean)(implicit bag: Bag[BAG]): BAG[T]

  /**
   * Invoked after [[headOrNull]] is invoked.
   *
   * @param previous previously read element
   * @param reverse  if this stream is in reverse iteration.
   *
   * @return next element in this stream.
   */
  private[swaydb] def nextOrNull[BAG[_]](previous: T, reverse: Boolean)(implicit bag: Bag[BAG]): BAG[T]

  private[swaydb] def headOrNull[BAG[_]](implicit bag: Bag[BAG]): BAG[T] =
    self.headOrNull(from, reverse)

  private[swaydb] def nextOrNull[BAG[_]](previous: T)(implicit bag: Bag[BAG]): BAG[T] =
    self.nextOrNull(previous, reverse)

  def from(key: K): SourceFree[K, T] =
    new SourceFree[K, T](from = Some(From(key = key, orBefore = false, orAfter = false, before = false, after = false)), reverse = self.reverse) {
      override private[swaydb] def headOrNull[BAG[_]](from: Option[From[K]], reverse: Boolean)(implicit bag: Bag[BAG]) = self.headOrNull(from, reverse)
      override private[swaydb] def nextOrNull[BAG[_]](previous: T, reverse: Boolean)(implicit bag: Bag[BAG]) = self.nextOrNull(previous, reverse)
    }

  def before(key: K): SourceFree[K, T] =
    new SourceFree[K, T](from = Some(From(key = key, orBefore = false, orAfter = false, before = true, after = false)), reverse = self.reverse) {
      override private[swaydb] def headOrNull[BAG[_]](from: Option[From[K]], reverse: Boolean)(implicit bag: Bag[BAG]) = self.headOrNull(from, reverse)
      override private[swaydb] def nextOrNull[BAG[_]](previous: T, reverse: Boolean)(implicit bag: Bag[BAG]) = self.nextOrNull(previous, reverse)
    }

  def fromOrBefore(key: K): SourceFree[K, T] =
    new SourceFree[K, T](from = Some(From(key = key, orBefore = true, orAfter = false, before = false, after = false)), reverse = self.reverse) {
      override private[swaydb] def headOrNull[BAG[_]](from: Option[From[K]], reverse: Boolean)(implicit bag: Bag[BAG]) = self.headOrNull(from, reverse)
      override private[swaydb] def nextOrNull[BAG[_]](previous: T, reverse: Boolean)(implicit bag: Bag[BAG]) = self.nextOrNull(previous, reverse)
    }

  def after(key: K): SourceFree[K, T] =
    new SourceFree[K, T](from = Some(From(key = key, orBefore = false, orAfter = false, before = false, after = true)), reverse = self.reverse) {
      override private[swaydb] def headOrNull[BAG[_]](from: Option[From[K]], reverse: Boolean)(implicit bag: Bag[BAG]) = self.headOrNull(from, reverse)
      override private[swaydb] def nextOrNull[BAG[_]](previous: T, reverse: Boolean)(implicit bag: Bag[BAG]) = self.nextOrNull(previous, reverse)
    }

  def fromOrAfter(key: K): SourceFree[K, T] =
    new SourceFree[K, T](from = Some(From(key = key, orBefore = false, orAfter = true, before = false, after = false)), reverse = self.reverse) {
      override private[swaydb] def headOrNull[BAG[_]](from: Option[From[K]], reverse: Boolean)(implicit bag: Bag[BAG]) = self.headOrNull(from, reverse)
      override private[swaydb] def nextOrNull[BAG[_]](previous: T, reverse: Boolean)(implicit bag: Bag[BAG]) = self.nextOrNull(previous, reverse)
    }

  def reverse: SourceFree[K, T] =
    new SourceFree[K, T](from = from, reverse = true) {
      override private[swaydb] def headOrNull[BAG[_]](from: Option[From[K]], reverse: Boolean)(implicit bag: Bag[BAG]) = self.headOrNull(from, reverse)
      override private[swaydb] def nextOrNull[BAG[_]](previous: T, reverse: Boolean)(implicit bag: Bag[BAG]) = self.nextOrNull(previous, reverse)
    }

  /**
   * This function is used internally to convert Scala types to Java types and is used before applying
   * any of the above from operations.
   *
   * For real world use-cases returning [[SourceFree]] on each [[StreamFree]] operation will lead to confusing API
   * since [[from]] could be set multiple times and executing tail from operations would start a new stream
   * ignoring prior stream operations which is not a valid API.
   */
  private[swaydb] def transformValue[B](f: T => B): SourceFree[K, B] =
    new SourceFree[K, B](from = self.from, reverse = self.reverse) { transformSource =>

      var previousA: T = _

      override private[swaydb] def headOrNull[BAG[_]](from: Option[From[K]], reverse: Boolean)(implicit bag: Bag[BAG]) =
        bag.map(self.headOrNull(from, reverse)) {
          previousAOrNull =>
            previousA = previousAOrNull
            if (previousAOrNull == null)
              null.asInstanceOf[B]
            else
              f(previousAOrNull)
        }

      override private[swaydb] def nextOrNull[BAG[_]](previous: B, reverse: Boolean)(implicit bag: Bag[BAG]) =
        if (previousA == null)
          bag.success(null.asInstanceOf[B])
        else
          bag.map(self.nextOrNull(transformSource.previousA, reverse)) {
            nextA =>
              previousA = nextA
              if (nextA == null)
                null.asInstanceOf[B]
              else
                f(nextA)
          }
    }
}
