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

package swaydb.core.segment.entry.id

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._

class KeyValueIdSpec extends AnyFlatSpec {

  it should "not have overlapping ids" in {

    KeyValueId.all.foldLeft(-1) {
      case (previousId, keyValueId) =>
        keyValueId.minKey_Compressed_KeyValueId == previousId + 1
        keyValueId.maxKey_Compressed_KeyValueId > keyValueId.minKey_Compressed_KeyValueId + 1

        keyValueId.minKey_Uncompressed_KeyValueId == keyValueId.maxKey_Compressed_KeyValueId + 1
        keyValueId.maxKey_Uncompressed_KeyValueId > keyValueId.minKey_Uncompressed_KeyValueId + 1

        keyValueId.maxKey_Uncompressed_KeyValueId
    }
  }

  it should "not allow conflicting ids and adjust baseIds to entryIds and vice versa" in {
    KeyValueId.all foreach { //for all ids
      keyValueId =>
        BaseEntryIdFormatA.baseIds foreach { //for all base ids for each
          baseEntryId =>

            val otherIds = KeyValueId.all.filter(_ != keyValueId)
            otherIds should not be empty

            val entryId = keyValueId.adjustBaseIdToKeyValueIdKey_Compressed(baseEntryId.baseId)
            if (keyValueId == KeyValueId.Put) entryId shouldBe baseEntryId.baseId //not change. Put's are default.

            //entryId should have this entryId
            keyValueId.hasKeyValueId(keyValueId = entryId) shouldBe true

            //others should not
            otherIds.foreach(_.hasKeyValueId(entryId) shouldBe false)

            //bump to entryID to be uncompressed.
            val uncompressedEntryId = keyValueId.adjustBaseIdToKeyValueIdKey_UnCompressed(baseEntryId.baseId)
            uncompressedEntryId should be >= entryId
            //id should have this entryId
            keyValueId.hasKeyValueId(keyValueId = uncompressedEntryId) shouldBe true
            //others should not
            otherIds.foreach(_.hasKeyValueId(uncompressedEntryId) shouldBe false)

            //adjust uncompressed entryId to base should return back to original.
            keyValueId.adjustKeyValueIdToBaseId(uncompressedEntryId) shouldBe baseEntryId.baseId
        }
    }
  }
}
