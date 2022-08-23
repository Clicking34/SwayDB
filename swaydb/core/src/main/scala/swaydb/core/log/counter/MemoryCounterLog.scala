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

package swaydb.core.log.counter

import java.util.concurrent.atomic.AtomicLong

private[swaydb] object MemoryCounterLog {
  def apply(): MemoryCounterLog =
    new MemoryCounterLog(new AtomicLong(CounterLog.startId))
}

private[swaydb] class MemoryCounterLog(count: AtomicLong) extends CounterLog {

  def next: Long =
    count.incrementAndGet()

  override def close(): Unit =
    ()
}