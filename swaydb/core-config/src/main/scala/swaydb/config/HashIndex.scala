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

package swaydb.config

import swaydb.Compression
import swaydb.config.builder.HashIndexBuilder
import swaydb.effect.{IOAction, IOStrategy}
import swaydb.utils.Java.JavaFunction

import scala.jdk.CollectionConverters._

sealed trait HashIndex {
  def toOption =
    this match {
      case HashIndex.Off        => None
      case enable: HashIndex.On => Some(enable)
    }
}

object HashIndex {
  def off: HashIndex.Off = Off

  sealed trait Off extends HashIndex
  case object Off extends Off

  def builder(): HashIndexBuilder.Step0 =
    HashIndexBuilder.builder()

  case class On(maxProbe: Int,
                minimumNumberOfKeys: Int,
                minimumNumberOfHits: Int,
                indexFormat: IndexFormat,
                allocateSpace: RequiredSpace => Int,
                blockIOStrategy: IOAction => IOStrategy,
                compression: UncompressedBlockInfo => Iterable[Compression]) extends HashIndex {

    def copyWithMaxProbe(maxProbe: Int) =
      this.copy(maxProbe = maxProbe)

    def copyWithMinimumNumberOfKeys(minimumNumberOfKeys: Int) =
      this.copy(minimumNumberOfKeys = minimumNumberOfKeys)

    def copyWithMinimumNumberOfHits(minimumNumberOfHits: Int) =
      this.copy(minimumNumberOfHits = minimumNumberOfHits)

    def copyWithIndexFormat(indexFormat: IndexFormat) =
      this.copy(indexFormat = indexFormat)

    def copyWithAllocateSpace(allocateSpace: JavaFunction[RequiredSpace, Int]) =
      this.copy(allocateSpace = allocateSpace.apply)

    def copyWithBlockIOStrategy(blockIOStrategy: JavaFunction[IOAction, IOStrategy]) =
      this.copy(blockIOStrategy = blockIOStrategy.apply)

    def copyWithCompressions(compression: JavaFunction[UncompressedBlockInfo, java.lang.Iterable[Compression]]) =
      this.copy(compression = compression.apply(_).asScala)
  }

  object RequiredSpace {
    def apply(_requiredSpace: Int, _numberOfKeys: Int): RequiredSpace =
      new RequiredSpace {
        override def requiredSpace: Int = _requiredSpace

        override def numberOfKeys: Int = _numberOfKeys
      }
  }

  trait RequiredSpace {
    def requiredSpace: Int

    def numberOfKeys: Int
  }
}
