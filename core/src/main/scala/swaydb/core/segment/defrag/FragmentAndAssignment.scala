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

package swaydb.core.segment.defrag

import swaydb.core.data.Memory
import swaydb.core.merge.stats.MergeStats
import swaydb.core.segment.assigner.{Assignable, Assignment}
import swaydb.core.segment.block.segment.transient.TransientSegment

import scala.collection.mutable.ListBuffer

case class FragmentAndAssignment[SEG](fragments: ListBuffer[TransientSegment.Fragment[MergeStats.Persistent.Builder[Memory, ListBuffer]]],
                                      assignments: ListBuffer[Assignment[ListBuffer[Assignable.Gap[MergeStats.Persistent.Builder[Memory, ListBuffer]]], ListBuffer[Assignable], SEG]])
