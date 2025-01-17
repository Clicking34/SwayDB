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

package swaydb.core.segment.data.merge

import swaydb.core.segment.CoreFunctionStore
import swaydb.core.segment.data._
import swaydb.slice.Slice
import swaydb.slice.order.TimeOrder

private[core] object FunctionMerger {

  def apply(newKeyValue: KeyValue.Function,
            oldKeyValue: KeyValue.Put)(implicit timeOrder: TimeOrder[Slice[Byte]],
                                       functionStore: CoreFunctionStore): KeyValue.Fixed = {

    def applyOutput(output: SegmentFunctionOutput) =
      output match {
        case SegmentFunctionOutput.Nothing =>
          oldKeyValue.copyWithTime(newKeyValue.time)

        case SegmentFunctionOutput.Remove =>
          Memory.Remove(oldKeyValue.key, None, newKeyValue.time)

        case SegmentFunctionOutput.Expire(deadline) =>
          oldKeyValue.copyWithDeadlineAndTime(Some(deadline), newKeyValue.time)

        case SegmentFunctionOutput.Update(value, deadline) =>
          Memory.Put(oldKeyValue.key, value.asSliceOption(), deadline.orElse(oldKeyValue.deadline), newKeyValue.time)
      }

    if (newKeyValue.time > oldKeyValue.time) {
      val function = newKeyValue.getOrFetchFunction()
      functionStore.get(function) match {
        case Some(functionId) =>
          functionId match {
            case SegmentFunction.Value(f) =>
              val output = f(oldKeyValue.getOrFetchValue())
              applyOutput(output)

            case SegmentFunction.ValueDeadline(f) =>
              val value = oldKeyValue.getOrFetchValue()
              val output = f(value, oldKeyValue.deadline)
              applyOutput(output)

            case function: SegmentFunction.RequiresKey =>
              function match {
                case SegmentFunction.Key(f) =>
                  applyOutput(f(oldKeyValue.key))

                case SegmentFunction.KeyValue(f) =>
                  val oldValue = oldKeyValue.getOrFetchValue()
                  val output = f(oldKeyValue.key, oldValue)
                  applyOutput(output)

                case SegmentFunction.KeyDeadline(f) =>
                  val output = f(oldKeyValue.key, oldKeyValue.deadline)
                  applyOutput(output)

                case SegmentFunction.KeyValueDeadline(f) =>
                  val oldValue = oldKeyValue.getOrFetchValue()
                  val output = f(oldKeyValue.key, oldValue, oldKeyValue.deadline)
                  applyOutput(output)
              }
          }

        case None =>
          throw swaydb.Exception.FunctionNotFound(function.readString())
      }
    }

    else
      oldKeyValue
  }

  def apply(newKeyValue: KeyValue.Function,
            oldKeyValue: KeyValue.Update)(implicit timeOrder: TimeOrder[Slice[Byte]],
                                          functionStore: CoreFunctionStore): KeyValue.Fixed = {

    def applyOutput(output: SegmentFunctionOutput) =
      output match {
        case SegmentFunctionOutput.Nothing =>
          oldKeyValue.copyWithTime(newKeyValue.time)

        case SegmentFunctionOutput.Remove =>
          Memory.Remove(oldKeyValue.key, None, newKeyValue.time)

        case SegmentFunctionOutput.Expire(deadline) =>
          oldKeyValue.copyWithDeadlineAndTime(Some(deadline), newKeyValue.time)

        case SegmentFunctionOutput.Update(value, deadline) =>
          Memory.Update(oldKeyValue.key, value.asSliceOption(), deadline.orElse(oldKeyValue.deadline), newKeyValue.time)
      }

    def toPendingApply(): Memory.PendingApply = {
      val oldValue = oldKeyValue.toFromValue()
      val newValue = newKeyValue.toFromValue()
      Memory.PendingApply(oldKeyValue.key, Slice(oldValue, newValue))
    }

    if (newKeyValue.time > oldKeyValue.time) {
      val function = newKeyValue.getOrFetchFunction()
      functionStore.get(function) match {
        case Some(functionId) =>
          functionId match {
            case SegmentFunction.Value(f) =>
              val value = oldKeyValue.getOrFetchValue()
              applyOutput(f(value))

            case SegmentFunction.ValueDeadline(f) =>
              //if deadline is not set, then the deadline of this key might have another update in lower levels.
              //so stash update.
              if (oldKeyValue.deadline.isEmpty) {
                toPendingApply()
              } else {
                val value = oldKeyValue.getOrFetchValue()
                val output = f(value, oldKeyValue.deadline)
                applyOutput(output)
              }

            case function: SegmentFunction.RequiresKey =>
              if (oldKeyValue.key.isEmpty)
                toPendingApply()
              else
                function match {
                  case SegmentFunction.Key(f) =>
                    applyOutput(f(oldKeyValue.key))

                  case SegmentFunction.KeyValue(f) =>
                    val oldValue = oldKeyValue.getOrFetchValue()
                    val output = f(oldKeyValue.key, oldValue)
                    applyOutput(output)

                  case SegmentFunction.KeyDeadline(f) =>
                    if (oldKeyValue.deadline.isEmpty)
                      toPendingApply()
                    else
                      applyOutput(f(oldKeyValue.key, oldKeyValue.deadline))

                  case SegmentFunction.KeyValueDeadline(f) =>
                    if (oldKeyValue.deadline.isEmpty) {
                      toPendingApply()
                    } else {
                      val oldValue = oldKeyValue.getOrFetchValue()
                      val output = f(oldKeyValue.key, oldValue, oldKeyValue.deadline)
                      applyOutput(output)
                    }
                }
          }

        case None =>
          throw swaydb.Exception.FunctionNotFound(function.readString())
      }
    }
    else
      oldKeyValue
  }

  def apply(newKeyValue: KeyValue.Function,
            oldKeyValue: KeyValue.Remove)(implicit timeOrder: TimeOrder[Slice[Byte]],
                                          functionStore: CoreFunctionStore): KeyValue.Fixed = {

    def applyOutput(output: SegmentFunctionOutput) =
      output match {
        case SegmentFunctionOutput.Nothing =>
          oldKeyValue.copyWithTime(newKeyValue.time)

        case SegmentFunctionOutput.Remove =>
          Memory.Remove(oldKeyValue.key, None, newKeyValue.time)

        case SegmentFunctionOutput.Expire(deadline) =>
          Memory.Remove(oldKeyValue.key, Some(deadline), newKeyValue.time)

        case SegmentFunctionOutput.Update(value, deadline) =>
          Memory.Update(oldKeyValue.key, value.asSliceOption(), deadline.orElse(oldKeyValue.deadline), newKeyValue.time)
      }

    def toPendingApply() =
      Memory.PendingApply(
        key = oldKeyValue.key,
        applies = Slice(oldKeyValue.toRemoveValue(), newKeyValue.toFromValue())
      )

    if (newKeyValue.time > oldKeyValue.time) {
      val function = newKeyValue.getOrFetchFunction()
      oldKeyValue.deadline match {
        case None =>
          oldKeyValue.copyWithTime(newKeyValue.time)

        case Some(_) =>
          functionStore.get(function) match {
            case Some(function) =>
              function match {
                case _: SegmentFunction.RequiresKey if oldKeyValue.key.isEmpty =>
                  //key is unknown since it's empty. Stash the merge.
                  toPendingApply()

                case _: SegmentFunction.RequiresValue =>
                  //value is not known since remove has deadline set - PendingApply!
                  toPendingApply()

                case SegmentFunction.Key(f) =>
                  applyOutput(f(oldKeyValue.key))

                case SegmentFunction.KeyDeadline(f) =>
                  applyOutput(f(oldKeyValue.key, oldKeyValue.deadline))
              }

            case None =>
              throw swaydb.Exception.FunctionNotFound(function.readString())
          }
      }
    }

    else
      oldKeyValue
  }

  def apply(newKeyValue: KeyValue.Function,
            oldKeyValue: KeyValue.Function)(implicit timeOrder: TimeOrder[Slice[Byte]]): KeyValue.Fixed =
    if (newKeyValue.time > oldKeyValue.time)
      Memory.PendingApply(
        key = newKeyValue.key,
        applies = Slice(oldKeyValue.toFromValue(), newKeyValue.toFromValue())
      )
    else
      oldKeyValue

  def apply(newKeyValue: KeyValue.Function,
            oldKeyValue: KeyValue.Fixed)(implicit timeOrder: TimeOrder[Slice[Byte]],
                                         functionStore: CoreFunctionStore): KeyValue.Fixed =
    oldKeyValue match {
      case oldKeyValue: KeyValue.Put =>
        FunctionMerger(newKeyValue, oldKeyValue)

      case oldKeyValue: KeyValue.Remove =>
        FunctionMerger(newKeyValue, oldKeyValue)

      case oldKeyValue: KeyValue.Update =>
        FunctionMerger(newKeyValue, oldKeyValue)

      case oldKeyValue: KeyValue.Function =>
        FunctionMerger(newKeyValue, oldKeyValue)

      case oldKeyValue: KeyValue.PendingApply =>
        FunctionMerger(newKeyValue, oldKeyValue)
    }

  def apply(newKeyValue: KeyValue.Function,
            oldKeyValue: Value.Apply)(implicit timeOrder: TimeOrder[Slice[Byte]],
                                      functionStore: CoreFunctionStore): KeyValue.Fixed =
    oldKeyValue match {
      case oldKeyValue: Value.Remove =>
        FunctionMerger(newKeyValue, oldKeyValue.toMemory(newKeyValue.key): KeyValue.Fixed)

      case oldKeyValue: Value.Update =>
        FunctionMerger(newKeyValue, oldKeyValue.toMemory(newKeyValue.key): KeyValue.Fixed)

      case oldKeyValue: Value.Function =>
        FunctionMerger(newKeyValue, oldKeyValue.toMemory(newKeyValue.key): KeyValue.Fixed)
    }

  def apply(newKeyValue: KeyValue.Function,
            oldKeyValue: KeyValue.PendingApply)(implicit timeOrder: TimeOrder[Slice[Byte]],
                                                functionStore: CoreFunctionStore): KeyValue.Fixed =
    if (newKeyValue.time > oldKeyValue.time)
      FixedMerger(
        newer = newKeyValue,
        oldApplies = oldKeyValue.getOrFetchApplies()
      )
    else
      oldKeyValue
}
