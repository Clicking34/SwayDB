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

package swaydb.core.segment.io

import com.typesafe.scalalogging.LazyLogging
import swaydb.Error.Segment.ExceptionHandler
import swaydb.config.{ForceSave, MMAP, SegmentRefCacheLife}
import swaydb.core.file.sweeper.bytebuffer.ByteBufferSweeper.ByteBufferSweeperActor
import swaydb.core.file.sweeper.FileSweeper
import swaydb.core.file.{CoreFile, ForceSaveApplier}
import swaydb.core.segment._
import swaydb.core.segment.block.segment.transient.TransientSegment
import swaydb.core.segment.cache.sweeper.MemorySweeper
import swaydb.core.util.DefIO
import swaydb.slice.Slice
import swaydb.SliceIOImplicits._
import swaydb.slice.order.{KeyOrder, TimeOrder}
import swaydb.utils.IDGenerator
import swaydb.{Error, IO}
import swaydb.core.segment.data.SegmentKeyOrders
import swaydb.core.segment.distributor.PathDistributor

import java.nio.file.Path
import scala.collection.mutable.ListBuffer

object SegmentWritePersistentIO extends SegmentWriteIO[TransientSegment.Persistent, PersistentSegment] with LazyLogging {

  override def minKey(segment: PersistentSegment): Slice[Byte] =
    segment.minKey

  def persistMerged(pathDistributor: PathDistributor,
                    segmentRefCacheLife: SegmentRefCacheLife,
                    mmap: MMAP.Segment,
                    mergeResult: Iterable[DefIO[SegmentOption, Iterable[TransientSegment.Persistent]]])(implicit keyOrders: SegmentKeyOrders,
                                                                                                        timeOrder: TimeOrder[Slice[Byte]],
                                                                                                        functionStore: CoreFunctionStore,
                                                                                                        fileSweeper: FileSweeper,
                                                                                                        bufferCleaner: ByteBufferSweeperActor,
                                                                                                        keyValueMemorySweeper: Option[MemorySweeper.KeyValue],
                                                                                                        blockCacheSweeper: Option[MemorySweeper.Block],
                                                                                                        segmentReadIO: SegmentReadIO,
                                                                                                        idGenerator: IDGenerator,
                                                                                                        forceSaveApplier: ForceSaveApplier): IO[Error.Segment, Iterable[DefIO[SegmentOption, Iterable[PersistentSegment]]]] =
    mergeResult.collect {
      //collect the ones with source set or has new segments to write
      case mergeResult if mergeResult.input.isSomeS || mergeResult.output.nonEmpty =>
        mergeResult

    }.mapRecoverIO[DefIO[SegmentOption, Iterable[PersistentSegment]]](
      mergeResult =>
        persistTransient(
          pathDistributor = pathDistributor,
          segmentRefCacheLife = segmentRefCacheLife,
          mmap = mmap,
          transient = mergeResult.output
        ) map {
          segment =>
            mergeResult.withOutput(segment)
        },
      recover =
        (segments, _) =>
          segments foreach {
            segmentToDelete =>
              segmentToDelete
                .output
                .foreachIO(segment => IO(segment.delete()), failFast = false)
                .foreach {
                  error =>
                    logger.error(s"Failed to delete Segment in recovery", error.exception)
                }
          }
    )

  def persistTransient(pathDistributor: PathDistributor,
                       segmentRefCacheLife: SegmentRefCacheLife,
                       mmap: MMAP.Segment,
                       transient: Iterable[TransientSegment.Persistent])(implicit keyOrders: SegmentKeyOrders,
                                                                         timeOrder: TimeOrder[Slice[Byte]],
                                                                         functionStore: CoreFunctionStore,
                                                                         fileSweeper: FileSweeper,
                                                                         bufferCleaner: ByteBufferSweeperActor,
                                                                         keyValueMemorySweeper: Option[MemorySweeper.KeyValue],
                                                                         blockCacheSweeper: Option[MemorySweeper.Block],
                                                                         segmentReadIO: SegmentReadIO,
                                                                         idGenerator: IDGenerator,
                                                                         forceSaveApplier: ForceSaveApplier): IO[Error.Segment, Iterable[PersistentSegment]] =
    transient.flatMapRecoverIO[PersistentSegment](
      ioBlock =
        segment =>
          IO {
            if (segment.hasEmptyByteSlice) {
              //This is fatal!! Empty Segments should never be created. If this does have for whatever reason it should
              //not be allowed so that whatever is creating this Segment (eg: compaction) does not progress with a success response.
              throw new Exception("Empty key-values submitted to persistent Segment.")
            } else {
              val path = pathDistributor.next().resolve(IDGenerator.segment(idGenerator.nextId()))

              segment match {
                case segment: TransientSegment.One =>
                  val file: CoreFile =
                    segmentFile(
                      path = path,
                      mmap = mmap,
                      segmentSize = segment.segmentSize,
                      byteTransporter =
                        file => {
                          file.append(segment.fileHeader)
                          //toArray is not expensive. bodyBytes is a very small number < 12
                          file.appendBatch(segment.bodyBytes.toArray)
                        }
                    )

                  Slice(
                    PersistentSegmentOne(
                      file = file,
                      segment = segment
                    )
                  )

                case segment: TransientSegment.RemoteRef =>
                  val segmentSize = segment.segmentSize

                  val file: CoreFile =
                    segmentFile(
                      path = path,
                      mmap = mmap,
                      segmentSize = segmentSize,
                      byteTransporter =
                        file => {
                          file.append(segment.fileHeader)
                          segment.ref.segmentBlockCache.transfer(0, segment.segmentSizeIgnoreHeader, file)
                        }
                    )

                  Slice(
                    PersistentSegmentOne(
                      file = file,
                      segment = segment
                    )
                  )

                case segment: TransientSegment.RemotePersistentSegment =>
                  Slice(
                    PersistentSegment.copyFrom(
                      segment = segment.segment,
                      pathDistributor = pathDistributor,
                      segmentRefCacheLife = segmentRefCacheLife,
                      mmap = mmap
                    )
                  )

                case segment: TransientSegment.Many =>
                  val file: CoreFile =
                    segmentFile(
                      path = path,
                      mmap = mmap,
                      segmentSize = segment.segmentSize,
                      byteTransporter =
                        file => {
                          file.append(segment.fileHeader)
                          //toArray is not expensive. bodyBytes is a very small number < 12
                          file.appendBatch(segment.listSegment.bodyBytes.toArray)

                          writeOrTransfer(
                            segments = segment.segments,
                            target = file
                          )
                        }
                    )

                  Slice(
                    PersistentSegmentMany(
                      file = file,
                      segmentRefCacheLife = segmentRefCacheLife,
                      segment = segment
                    )
                  )
              }
            }
          },
      recover =
        (segments: Iterable[PersistentSegment], _: IO.Left[swaydb.Error.Segment, Slice[Segment]]) =>
          segments
            .foreachIO(segment => IO(segment.delete()), failFast = false)
            .foreach {
              error =>
                logger.error(s"Failed to delete Segment in recovery", error.exception)
            }
    )

  /**
   * Write segments to target file and also attempts to batch transfer bytes.
   */
  private def writeOrTransfer(segments: Slice[TransientSegment.OneOrRemoteRef],
                              target: CoreFile): Unit = {

    /**
     * Batch transfer segments to remote file. This defers transfer to the operating
     * system skipping the JVM heap.
     */
    def batchTransfer(sameFileRemotes: ListBuffer[TransientSegment.RemoteRef]): Unit =
      if (sameFileRemotes.nonEmpty)
        if (sameFileRemotes.size == 1) {
          val remote = sameFileRemotes.head
          remote.ref.segmentBlockCache.transfer(0, remote.segmentSizeIgnoreHeader, target)
          sameFileRemotes.clear()
        } else {
          val start = sameFileRemotes.head.ref.offset().start
          val end = sameFileRemotes.last.ref.offset().end
          sameFileRemotes.head.ref.segmentBlockCache.transferIgnoreOffset(start, end - start + 1, target)
          sameFileRemotes.clear()
        }

    /**
     * Writes bytes to target file and also tries to transfer Remote Refs which belongs to
     * same file to be transferred as a single IO operation i.e. batchableRemotes.
     */
    val pendingRemotes =
      segments.foldLeft(ListBuffer.empty[TransientSegment.RemoteRef]) {
        case (batchableRemotes, nextRemoteOrOne) =>
          nextRemoteOrOne match {
            case nextRemote: TransientSegment.RemoteRef =>
              if (batchableRemotes.isEmpty || (batchableRemotes.last.ref.path.getParent == nextRemote.ref.path.getParent && batchableRemotes.last.ref.offset().end + 1 == nextRemote.ref.offset().start)) {
                batchableRemotes += nextRemote
              } else {
                batchTransfer(batchableRemotes)
                batchableRemotes += nextRemote
              }

            case nextOne: TransientSegment.One =>
              batchTransfer(batchableRemotes)
              //toArray is not expensive. bodyBytes is a very small number < 12
              target.appendBatch(nextOne.bodyBytes.toArray)
              batchableRemotes
          }
      }

    //transfer any remaining remotes.
    batchTransfer(pendingRemotes)
  }

  private def segmentFile(path: Path,
                          mmap: MMAP.Segment,
                          segmentSize: Int,
                          byteTransporter: CoreFile => Unit)(implicit segmentReadIO: SegmentReadIO,
                                                             fileSweeper: FileSweeper,
                                                             bufferCleaner: ByteBufferSweeperActor,
                                                             forceSaveApplier: ForceSaveApplier): CoreFile =
    mmap match {
      case MMAP.On(deleteAfterClean, forceSave) => //if both read and writes are mmaped. Keep the file open.
        CoreFile.mmapWriteableReadableTransfer(
          path = path,
          fileOpenIOStrategy = segmentReadIO.fileOpenIO,
          autoClose = true,
          deleteAfterClean = deleteAfterClean,
          forceSave = forceSave,
          bufferSize = segmentSize,
          transfer = byteTransporter
        )

      case MMAP.ReadOnly(deleteAfterClean) =>
        val standardWrite =
          CoreFile.standardWritable(
            path = path,
            fileOpenIOStrategy = segmentReadIO.fileOpenIO,
            autoClose = true,
            forceSave = ForceSave.Off
          )

        try
          byteTransporter(standardWrite)
        catch {
          case throwable: Throwable =>
            logger.error(s"Failed to write $mmap file with applier. Closing file: $path", throwable)
            standardWrite.close()
            throw throwable
        }

        standardWrite.close()

        CoreFile.mmapReadable(
          path = standardWrite.path,
          fileOpenIOStrategy = segmentReadIO.fileOpenIO,
          autoClose = true,
          deleteAfterClean = deleteAfterClean
        )

      case _: MMAP.Off =>
        val standardWrite =
          CoreFile.standardWritable(
            path = path,
            fileOpenIOStrategy = segmentReadIO.fileOpenIO,
            autoClose = true,
            forceSave = ForceSave.Off
          )

        try
          byteTransporter(standardWrite)
        catch {
          case throwable: Throwable =>
            logger.error(s"Failed to write $mmap file with applier. Closing file: $path", throwable)
            standardWrite.close()
            throw throwable
        }

        standardWrite.close()

        CoreFile.standardReadable(
          path = standardWrite.path,
          fileOpenIOStrategy = segmentReadIO.fileOpenIO,
          autoClose = true
        )

      //another case if mmapReads is false, write bytes in mmaped mode and then close and re-open for read. Currently not inuse.
      //    else if (mmap.mmapWrites && !mmap.mmapReads) {
      //      val file =
      //        CoreFile.mmapWriteAndRead(
      //          path = path,
      //          autoClose = true,
      //          ioStrategy = SegmentIO.segmentBlockIO(IOAction.OpenResource),
      //          blockCacheFileId = BlockCacheFileIDGenerator.nextID,
      //          bytes = segmentBytes
      //        )
      //
      //      //close immediately to force flush the bytes to disk. Having mmapWrites == true and mmapReads == false,
      //      //is probably not the most efficient and should not be used.
      //      file.close()
      //      CoreFile.standardRead(
      //        path = file.path,
      //        ioStrategy = SegmentIO.segmentBlockIO(IOAction.OpenResource),
      //        blockCacheFileId = BlockCacheFileIDGenerator.nextID,
      //        autoClose = true
      //      )
      //    }
    }
}
