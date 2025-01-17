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

package swaydb.core.log.applied

import com.typesafe.scalalogging.LazyLogging
import swaydb.{Error, IO}
import swaydb.config.MMAP
import swaydb.core.file.ForceSaveApplier
import swaydb.core.file.sweeper.bytebuffer.ByteBufferSweeper.ByteBufferSweeperActor
import swaydb.core.file.sweeper.FileSweeper
import swaydb.core.log
import swaydb.core.log.{Log, PersistentLog, LogRecoveryResult}
import swaydb.core.log.serialiser.{KeyLogEntryReader, KeyLogEntryWriter}
import swaydb.core.segment.CoreFunctionStore
import swaydb.effect.Effect
import swaydb.slice.Slice
import swaydb.slice.order.KeyOrder

import java.nio.file.Path
import scala.collection.mutable.ListBuffer

case object AppliedFunctionsLog extends LazyLogging {

  val folderName = "def-applied"

  def apply(dir: Path,
            fileSize: Int,
            mmap: MMAP.Log)(implicit bufferCleaner: ByteBufferSweeperActor,
                            forceSaveApplier: ForceSaveApplier): LogRecoveryResult[log.PersistentLog[Slice[Byte], Unit, AppliedFunctionsLogCache]] = {
    val folder = dir.resolve(folderName)
    Effect.createDirectoriesIfAbsent(folder)

    implicit val functionsEntryWriter = KeyLogEntryWriter.KeyPutLogEntryWriter
    implicit val functionsEntryReader = KeyLogEntryReader.KeyLogEntryReader
    implicit val fileSweeper: FileSweeper = FileSweeper.Off
    implicit val keyOrder = KeyOrder.default

    PersistentLog[Slice[Byte], Unit, AppliedFunctionsLogCache](
      folder = folder,
      mmap = mmap,
      flushOnOverflow = true,
      fileSize = fileSize,
      dropCorruptedTailEntries = false
    )
  }

  def validate(appliedFunctions: Log[Slice[Byte], Unit, AppliedFunctionsLogCache],
               functionStore: CoreFunctionStore): IO[Error.Level, Unit] = {
    val missingFunctions = ListBuffer.empty[String]
    logger.debug("Checking for missing functions.")

    appliedFunctions.cache.iterator.foreach {
      case (functionId, _) =>
        if (functionStore.notContains(functionId))
          missingFunctions += functionId.readString()
    }

    if (missingFunctions.isEmpty)
      IO.unit
    else
      IO.Left[Error.Level, Unit](Error.MissingFunctions(missingFunctions))
  }
}
