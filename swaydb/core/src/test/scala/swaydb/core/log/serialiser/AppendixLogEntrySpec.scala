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

package swaydb.core.log.serialiser

import org.scalatest.OptionValues._
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import swaydb.config.CoreConfigTestKit._
import swaydb.config.{GenForceSave, MMAP}
import swaydb.core.{CoreSpecType, CoreTestSweeper}
import swaydb.core.log.LogEntry
import swaydb.core.segment.{Segment, SegmentOption, GenSegment}
import swaydb.core.segment.data.KeyValueTestKit._
import swaydb.core.segment.data.SegmentKeyOrders
import swaydb.core.segment.io.SegmentReadIO
import swaydb.core.segment.SegmentTestKit._
import swaydb.core.skiplist.SkipListConcurrent
import swaydb.serializers._
import swaydb.serializers.Default._
import swaydb.slice.{Slice, SliceOption}
import swaydb.slice.order.{KeyOrder, TimeOrder}
import swaydb.utils.OperatingSystem

class AppendixLogEntrySpec extends AnyWordSpec {

  implicit val keyOrder = KeyOrder.default
  implicit val keyOrders: SegmentKeyOrders = SegmentKeyOrders(keyOrder)
  implicit val timeOrder: TimeOrder[Slice[Byte]] = TimeOrder.long
  implicit def segmentIO: SegmentReadIO = SegmentReadIO.random

  "LogEntryWriterAppendix & LogEntryReaderAppendix" should {

    "write Add segment" in {
      CoreTestSweeper.foreach(CoreSpecType.all) {
        (_sweeper, _specType) =>

          implicit val sweeper: CoreTestSweeper = _sweeper
          implicit val specType: CoreSpecType = _specType

          import sweeper._

          val segment = GenSegment(randomizedKeyValues(100))

          val appendixReader =
            SegmentLogEntryReader(
              mmapSegment =
                MMAP.On(
                  deleteAfterClean = OperatingSystem.isWindows(),
                  forceSave = GenForceSave.mmap()
                ),
              segmentRefCacheLife = randomSegmentRefCacheLife()
            )

          import SegmentLogEntryWriter.SegmentLogEntryPutWriter
          val entry = LogEntry.Put[Slice[Byte], Segment](segment.minKey, segment)

          val slice = Slice.allocate[Byte](entry.entryBytesSize)
          entry writeTo slice
          slice.isFull shouldBe true //this ensures that bytesRequiredFor is returning the correct size

          import appendixReader.SegmentLogEntryPutReader
          LogEntryReader.read[LogEntry.Put[Slice[Byte], Segment]](slice.drop(1)) shouldBe entry

          import appendixReader.SegmentLogEntryReader
          val readEntry = LogEntryReader.read[LogEntry[Slice[Byte], Segment]](slice)
          readEntry shouldBe entry

          val skipList = SkipListConcurrent[SliceOption[Byte], SegmentOption, Slice[Byte], Segment](Slice.Null, Segment.Null)(keyOrder)
          readEntry applyBatch skipList
          val scalaSkipList = skipList.toIterable

          scalaSkipList should have size 1
          val (headKey, headValue) = scalaSkipList.head
          headKey shouldBe segment.minKey
          headValue shouldBe segment
      }
    }

    "write Remove Segment" in {
      CoreTestSweeper.foreach(CoreSpecType.all) {
        (_sweeper, _specType) =>

          implicit val sweeper: CoreTestSweeper = _sweeper
          implicit val specType: CoreSpecType = _specType

          import sweeper._

          val appendixReader =
            SegmentLogEntryReader(
              mmapSegment =
                MMAP.On(
                  deleteAfterClean = OperatingSystem.isWindows(),
                  forceSave = GenForceSave.mmap()
                ),
              segmentRefCacheLife = randomSegmentRefCacheLife()
            )

          import SegmentLogEntryWriter.SegmentLogEntryRemoveWriter
          val entry = LogEntry.Remove[Slice[Byte]](1)

          val slice = Slice.allocate[Byte](entry.entryBytesSize)
          entry writeTo slice
          slice.isFull shouldBe true //this ensures that bytesRequiredFor is returning the correct size

          import appendixReader.SegmentLogEntryRemoveReader
          LogEntryReader.read[LogEntry.Remove[Slice[Byte]]](slice.drop(1)).key shouldBe entry.key

          import appendixReader.SegmentLogEntryReader
          val readEntry = LogEntryReader.read[LogEntry[Slice[Byte], Segment]](slice)
          readEntry shouldBe entry

          val skipList = SkipListConcurrent[SliceOption[Byte], SegmentOption, Slice[Byte], Segment](Slice.Null, Segment.Null)(keyOrder)
          readEntry applyBatch skipList
          skipList shouldBe empty
      }
    }

    "write and remove key-value" in {
      CoreTestSweeper.foreach(CoreSpecType.all) {
        (_sweeper, _specType) =>

          implicit val sweeper: CoreTestSweeper = _sweeper
          implicit val specType: CoreSpecType = _specType

          import SegmentLogEntryWriter.{SegmentLogEntryPutWriter, SegmentLogEntryRemoveWriter}
          import sweeper._

          val appendixReader = SegmentLogEntryReader(
            mmapSegment =
              MMAP.On(
                deleteAfterClean = OperatingSystem.isWindows(),
                forceSave = GenForceSave.mmap()
              ),
            segmentRefCacheLife = randomSegmentRefCacheLife()
          )

          val segment1 = GenSegment(randomizedKeyValues(100))
          val segment2 = GenSegment(randomizedKeyValues(100))
          val segment3 = GenSegment(randomizedKeyValues(100))
          val segment4 = GenSegment(randomizedKeyValues(100))
          val segment5 = GenSegment(randomizedKeyValues(100))

          val entry: LogEntry[Slice[Byte], Segment] =
            (LogEntry.Put[Slice[Byte], Segment](segment1.minKey, segment1): LogEntry[Slice[Byte], Segment]) ++
              LogEntry.Put[Slice[Byte], Segment](segment2.minKey, segment2) ++
              LogEntry.Remove[Slice[Byte]](segment1.minKey) ++
              LogEntry.Put[Slice[Byte], Segment](segment3.minKey, segment3) ++
              LogEntry.Put[Slice[Byte], Segment](segment4.minKey, segment4) ++
              LogEntry.Remove[Slice[Byte]](segment2.minKey) ++
              LogEntry.Put[Slice[Byte], Segment](segment5.minKey, segment5)

          val slice = Slice.allocate[Byte](entry.entryBytesSize)
          entry writeTo slice
          slice.isFull shouldBe true //this ensures that bytesRequiredFor is returning the correct size

          import appendixReader.SegmentLogEntryReader
          val readEntry = LogEntryReader.read[LogEntry[Slice[Byte], Segment]](slice)
          readEntry shouldBe entry

          val skipList = SkipListConcurrent[SliceOption[Byte], SegmentOption, Slice[Byte], Segment](Slice.Null, Segment.Null)(keyOrder)
          readEntry applyBatch skipList

          def scalaSkipList = skipList.toIterable

          assertSkipList()

          def assertSkipList() = {
            scalaSkipList should have size 3
            scalaSkipList.get(segment1.minKey) shouldBe empty
            scalaSkipList.get(segment2.minKey) shouldBe empty
            scalaSkipList.get(segment3.minKey).value shouldBe segment3
            scalaSkipList.get(segment4.minKey).value shouldBe segment4
            scalaSkipList.get(segment5.minKey).value shouldBe segment5
          }

          //write skip list to bytes should result in the same skip list as before
          val bytes = LogEntryParser.write[Slice[Byte], Segment](skipList.iterator)
          val crcEntries = LogEntryParser.read[Slice[Byte], Segment](bytes, false).get.item.value
          skipList.clear()
          crcEntries applyBatch skipList
          assertSkipList()
      }
    }
  }
}
