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

package swaydb.core.segment.block

import swaydb.slice.SliceReader

object BlockHeader {
  val uncompressedBlockId: Byte = 0.toByte
  val compressedBlockID: Byte = 1.toByte
}

class BlockHeader[O](val compressionInfo: BlockCompressionInfoOption,
                     val headerReader: SliceReader,
                     val headerSize: Int,
                     val offset: O)
