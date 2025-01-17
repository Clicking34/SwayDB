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

package swaydb.core.util

import swaydb.core.segment.CoreFunctionStore
import swaydb.core.segment.data.Value.{FromValue, FromValueOption}
import swaydb.core.segment.data.{KeyValue, Memory, Value}
import swaydb.slice.Slice
import swaydb.utils.{SomeOrNone, SomeOrNoneCovariant}

import scala.annotation.tailrec

private[core] object MinMax {

  implicit class MinMaxByteImplicits(minMax: MinMax[Slice[Byte]]) {
    @inline def cut(): MinMax[Slice[Byte]] =
      MinMax(
        min = minMax.min.cut(),
        max = minMax.max.cut()
      )
  }

  @inline private def minimum[T](left: T,
                                 right: T)(implicit ordering: Ordering[T]): T =
    if (ordering.compare(left, right) <= 0)
      left
    else
      right

  @inline private def maximum[T](left: T,
                                 right: T)(implicit ordering: Ordering[T]): T =
    if (ordering.compare(left, right) >= 0)
      left
    else
      right

  @inline private def pickOne[T](someKey1: Option[T],
                                 someKey2: Option[T],
                                 condition: (T, T) => T): Option[T] =
    someKey1 match {
      case Some(key1) =>
        someKey2 match {
          case Some(key2) =>
            Some(condition(key1, key2))

          case None =>
            someKey1
        }

      case None =>
        someKey2
    }

  /**
   * Picks the smallest of the two. Favours left if equal.
   */
  def minFavourLeft[T](left: Option[T],
                       right: Option[T])(implicit ordering: Ordering[T]): Option[T] =
    pickOne[T](left, right, minimum)

  /**
   * Picks the largest of the two. Favours left if equal.
   */
  def maxFavourLeft[T](left: Option[T],
                       right: Option[T])(implicit ordering: Ordering[T]): Option[T] =
    pickOne[T](left, right, maximum)

  def maxFavourLeft[T](left: T,
                       right: Option[T])(implicit ordering: Ordering[T]): T =
    right match {
      case Some(right) =>
        maximum[T](left, right)

      case None =>
        left
    }

  def minFavourLeft[T](left: T,
                       right: T)(implicit ordering: Ordering[T]): T =
    minimum[T](left, right)

  def maxFavourLeft[T](left: T,
                       right: T)(implicit ordering: Ordering[T]): T =
    maximum[T](left, right)

  def maxFavourLeftC[T <: SomeOrNoneCovariant[T, SOME], SOME <: T](left: T,
                                                                   right: T)(implicit ordering: Ordering[SOME]): T =
    if (left.isNoneC)
      right
    else if (right.isNoneC)
      left
    else
      maximum(left.getC, right.getC)

  def minFavourLeftC[T <: SomeOrNoneCovariant[T, SOME], SOME <: T](left: T,
                                                                   right: T)(implicit ordering: Ordering[SOME]): T =
    if (left.isNoneC)
      right
    else if (right.isNoneC)
      left
    else
      minimum(left.getC, right.getC)

  def maxFavourLeftS[T <: SomeOrNone[T, SOME], SOME <: T](left: T,
                                                          right: T)(implicit ordering: Ordering[SOME]): T =
    if (left.isNoneS)
      right
    else if (right.isNoneS)
      left
    else
      maximum(left.getS, right.getS)

  def minFavourLeftS[T <: SomeOrNone[T, SOME], SOME <: T](left: T,
                                                          right: T)(implicit ordering: Ordering[SOME]): T =
    if (left.isNoneS)
      right
    else if (right.isNoneS)
      left
    else
      minimum(left.getS, right.getS)

  def maxFavourLeft[T](left: Option[T],
                       right: T)(implicit ordering: Ordering[T]): T =
    left match {
      case Some(left) =>
        maximum[T](left, right)

      case None =>
        right
    }

  def contains[T](key: T, minMax: MinMax[T])(implicit order: Ordering[T]): Boolean =
    minMax.max match {
      case Some(maxValue) =>
        import order._
        key >= minMax.min && key <= maxValue

      case None =>
        order.equiv(key, minMax.min)
    }

  def minMaxFunction(function: Option[Value],
                     current: Option[MinMax[Slice[Byte]]]): Option[MinMax[Slice[Byte]]] =
    function flatMap {
      function =>
        minMaxFunction(
          function = function,
          current = current
        )
    } orElse current

  def minMaxFunction(function: Value,
                     current: Option[MinMax[Slice[Byte]]]): Option[MinMax[Slice[Byte]]] =
    function match {
      case _: Value.Remove | _: Value.Update | _: Value.Put =>
        current

      case function: Value.Function =>
        Some(
          minMaxFunction(
            function = function,
            current = current
          )
        )

      case Value.PendingApply(applies) =>
        minMaxFunction(
          functions = applies,
          current = current
        )
    }

  def minMaxFunction(function: Value.Function,
                     current: Option[MinMax[Slice[Byte]]]): MinMax[Slice[Byte]] =
    minMax(
      current = current,
      next = function.function
    )(CoreFunctionStore.order)

  def minMaxFunction(function: Memory.Function,
                     current: Option[MinMax[Slice[Byte]]]): MinMax[Slice[Byte]] =
    minMax(
      current = current,
      next = function.function
    )(CoreFunctionStore.order)

  def minMaxFunctionOption(left: Option[MinMax[Slice[Byte]]],
                           right: Option[MinMax[Slice[Byte]]]): Option[MinMax[Slice[Byte]]] =
    minMax(
      left = left,
      right = right
    )(CoreFunctionStore.order)

  def minMaxFunction(function: KeyValue.Function,
                     current: Option[MinMax[Slice[Byte]]]): MinMax[Slice[Byte]] =
    minMax(
      current = current,
      next = function.getOrFetchFunction()
    )(CoreFunctionStore.order)

  def minMaxFunction(range: Memory.Range,
                     current: Option[MinMax[Slice[Byte]]]): Option[MinMax[Slice[Byte]]] =
    minMaxFunction(
      fromValue = range.fromValue,
      rangeValue = range.rangeValue,
      current = current
    )

  def minMaxFunction(fromValue: FromValueOption,
                     rangeValue: Value.RangeValue,
                     current: Option[MinMax[Slice[Byte]]]): Option[MinMax[Slice[Byte]]] =
    minMaxFunction(
      function = rangeValue,
      current =
        fromValue match {
          case FromValue.Null =>
            current

          case fromValue: Value.FromValue =>
            minMaxFunction(
              function = fromValue,
              current = current
            )
        }
    )

  def minMaxFunction(range: KeyValue.Range,
                     current: Option[MinMax[Slice[Byte]]]): Option[MinMax[Slice[Byte]]] = {
    val (fromValue, rangeValue) = range.fetchFromAndRangeValueUnsafe()
    minMaxFunction(
      fromValue = fromValue,
      rangeValue = rangeValue,
      current = current
    )
  }

  @tailrec
  def minMaxFunction(functions: Slice[Value],
                     current: Option[MinMax[Slice[Byte]]]): Option[MinMax[Slice[Byte]]] =
    functions.headOption match {
      case Some(function) =>
        minMaxFunction(
          functions = functions.dropHead(),
          current =
            minMaxFunction(
              function = function,
              current = current
            )
        )

      case None =>
        current
    }

  def minMax[T](left: Option[MinMax[T]],
                right: Option[MinMax[T]])(implicit order: Ordering[T]): Option[MinMax[T]] =
    (left, right) match {
      case (Some(left), Some(right)) =>
        val minMaxLeft = MinMax.minMax(left, right.min)
        val minMaxRight = right.max.map(MinMax.minMax(minMaxLeft, _))
        minMaxRight orElse Some(minMaxLeft)

      case (None, right @ Some(_)) =>
        right

      case (left @ Some(_), None) =>
        left

      case (None, None) =>
        None
    }

  def minMax[T](current: Option[MinMax[T]],
                next: T)(implicit order: Ordering[T]): MinMax[T] =
    current match {
      case Some(currentMinMax) =>
        minMax(currentMinMax, next)

      case None =>
        MinMax(min = next, None)
    }

  def minMax[T](current: MinMax[T],
                next: T)(implicit order: Ordering[T]): MinMax[T] = {
    import order._
    val minCompare = order.compare(next, current.min)
    if (minCompare == 0)
      MinMax(
        min = next,
        max = current.max
      )
    else if (minCompare < 0)
      MinMax(
        min = next,
        max = current.max orElse Some(current.min)
      )
    else if (current.max.forall(_ <= next))
      current.copy(max = Some(next))
    else
      current
  }
}

private[core] case class MinMax[T](min: T,
                                   max: Option[T])
