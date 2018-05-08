/*
 * Copyright (C) 2018 Simer Plaha (@simerplaha)
 *
 * This file is a part of SwayDB.
 *
 * SwayDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SwayDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with SwayDB. If not, see <https://www.gnu.org/licenses/>.
 */
package swaydb.core.level.zero

import java.util.concurrent.ConcurrentSkipListMap

import org.scalatest.{Matchers, WordSpec}
import swaydb.core.CommonAssertions
import swaydb.core.data.{Memory, Value}
import swaydb.data.slice.Slice
import swaydb.serializers.Default._
import swaydb.serializers._

import scala.collection.JavaConverters._
import scala.concurrent.duration._

class LevelZeroSkipListMergerSpec extends WordSpec with Matchers with CommonAssertions {
  implicit val ordering = swaydb.order.KeyOrder.default
  implicit val merger = swaydb.core.level.zero.LevelZeroSkipListMerge(10.seconds)

  import merger._

  "insert" should {
    "insert a Fixed value to an empty skipList" in {
      val skipList = new ConcurrentSkipListMap[Slice[Byte], Memory](ordering)

      insert(1, Memory.Put(1, "one"), skipList)
      skipList should have size 1

      skipList.asScala.head shouldBe ((1: Slice[Byte], Memory.Put(1, "one")))
    }

    "insert multiple fixed key-values" in {
      val skipList = new ConcurrentSkipListMap[Slice[Byte], Memory](ordering)

      (0 to 9) foreach {
        i =>
          insert(i, Memory.Put(i, i), skipList)
      }

      skipList should have size 10

      skipList.asScala.zipWithIndex foreach {
        case ((key, value), index) =>
          key shouldBe (index: Slice[Byte])
          value shouldBe Memory.Put(index, index)
      }
    }

    "insert multiple non-overlapping ranges" in {
      //10 | 20 | 40 | 100
      //1  | 10 | 30 | 50
      val skipList = new ConcurrentSkipListMap[Slice[Byte], Memory](ordering)
      insert(1, Memory.Range(1, 10, None, Value.Update(10)), skipList)
      insert(10, Memory.Range(10, 20, None, Value.Remove(None)), skipList)
      insert(30, Memory.Range(30, 40, None, Value.Update(40)), skipList)
      insert(50, Memory.Range(50, 100, Some(Value.Put(20)), Value.Remove(None)), skipList)

      val skipListArray = skipList.asScala.toArray
      skipListArray(0) shouldBe ((1: Slice[Byte], Memory.Range(1, 10, None, Value.Update(10))))
      skipListArray(1) shouldBe ((10: Slice[Byte], Memory.Range(10, 20, None, Value.Remove(None))))
      skipListArray(2) shouldBe ((30: Slice[Byte], Memory.Range(30, 40, None, Value.Update(40))))
      skipListArray(3) shouldBe ((50: Slice[Byte], Memory.Range(50, 100, Some(Value.Put(20)), Value.Remove(None))))
    }

    "insert overlapping ranges when insert fromKey is less than existing range's fromKey" in {
      //1-15
      //  20
      //  10

      //result:
      //15 | 20
      //1  | 15

      val skipList = new ConcurrentSkipListMap[Slice[Byte], Memory](ordering)

      insert(10, Memory.Range(10, 20, None, Value.Update(20)), skipList)
      insert(1, Memory.Range(1, 15, None, Value.Update(40)), skipList)
      skipList should have size 3

      skipList.get(1: Slice[Byte]) shouldBe Memory.Range(1, 10, None, Value.Update(40))
      skipList.get(10: Slice[Byte]) shouldBe Memory.Range(10, 15, None, Value.Update(40))
      skipList.get(15: Slice[Byte]) shouldBe Memory.Range(15, 20, None, Value.Update(20))
    }

    "insert overlapping ranges when insert fromKey is less than existing range's from key and fromKey is set" in {
      //1-15
      //  20 (R - Put(20)
      //  10 (Put(10))

      //result:
      //10 | 15 | 20
      //1  | 10 | 15

      val skipList = new ConcurrentSkipListMap[Slice[Byte], Memory](ordering)

      //insert with put
      insert(10, Memory.Range(10, 20, Some(Value.Put(10)), Value.Update(20)), skipList)
      insert(1, Memory.Range(1, 15, None, Value.Update(40)), skipList)
      skipList should have size 3
      val skipListArray = skipList.asScala.toArray

      skipListArray(0) shouldBe(1: Slice[Byte], Memory.Range(1, 10, None, Value.Update(40)))
      skipListArray(1) shouldBe(10: Slice[Byte], Memory.Range(10, 15, Some(Value.Put(40)), Value.Update(40)))
      skipListArray(2) shouldBe(15: Slice[Byte], Memory.Range(15, 20, None, Value.Update(20)))
    }

    "insert overlapping ranges when insert fromKey is greater than existing range's fromKey" in {
      val skipList = new ConcurrentSkipListMap[Slice[Byte], Memory](ordering)
      //10
      //1
      insert(1, Memory.Range(1, 15, None, Value.Update(40)), skipList)
      insert(10, Memory.Range(10, 20, None, Value.Update(20)), skipList)
      skipList should have size 3

      skipList.get(1: Slice[Byte]) shouldBe Memory.Range(1, 10, None, Value.Update(40))
      skipList.get(10: Slice[Byte]) shouldBe Memory.Range(10, 15, None, Value.Update(20))
      skipList.get(15: Slice[Byte]) shouldBe Memory.Range(15, 20, None, Value.Update(20))

    }

    "insert overlapping ranges when insert fromKey is greater than existing range's fromKey and fromKey is set" in {
      val skipList = new ConcurrentSkipListMap[Slice[Byte], Memory](ordering)
      //15
      //1 (Put(1))
      insert(1, Memory.Range(1, 15, Some(Value.Put(1)), Value.Update(40)), skipList)
      insert(10, Memory.Range(10, 20, None, Value.Update(20)), skipList)

      skipList should have size 3

      skipList.get(1: Slice[Byte]) shouldBe Memory.Range(1, 10, Some(Value.Put(1)), Value.Update(40))
      skipList.get(10: Slice[Byte]) shouldBe Memory.Range(10, 15, None, Value.Update(20))
      skipList.get(15: Slice[Byte]) shouldBe Memory.Range(15, 20, None, Value.Update(20))
    }

    "insert overlapping ranges without values set and no splits required" in {
      val skipList = new ConcurrentSkipListMap[Slice[Byte], Memory](ordering)
      insert(1, Memory.Range(1, 5, None, Value.Update(5)), skipList)
      insert(5, Memory.Range(5, 10, None, Value.Update(10)), skipList)
      insert(10, Memory.Range(10, 20, None, Value.Update(20)), skipList)
      insert(20, Memory.Range(20, 30, None, Value.Update(30)), skipList)
      insert(30, Memory.Range(30, 40, None, Value.Update(40)), skipList)
      insert(40, Memory.Range(40, 50, None, Value.Update(50)), skipList)

      insert(10, Memory.Range(10, 100, None, Value.Update(100)), skipList)
      skipList should have size 7

      skipList.get(1: Slice[Byte]) shouldBe Memory.Range(1, 5, None, Value.Update(5))
      skipList.get(5: Slice[Byte]) shouldBe Memory.Range(5, 10, None, Value.Update(10))
      skipList.get(10: Slice[Byte]) shouldBe Memory.Range(10, 20, None, Value.Update(100))
      skipList.get(20: Slice[Byte]) shouldBe Memory.Range(20, 30, None, Value.Update(100))
      skipList.get(30: Slice[Byte]) shouldBe Memory.Range(30, 40, None, Value.Update(100))
      skipList.get(40: Slice[Byte]) shouldBe Memory.Range(40, 50, None, Value.Update(100))
      skipList.get(50: Slice[Byte]) shouldBe Memory.Range(50, 100, None, Value.Update(100))
    }

    "insert overlapping ranges with values set and no splits required" in {
      val skipList = new ConcurrentSkipListMap[Slice[Byte], Memory](ordering)
      insert(1, Memory.Range(1, 5, Some(Value.Put(1)), Value.Update(5)), skipList)
      insert(5, Memory.Range(5, 10, None, Value.Update(10)), skipList)
      insert(10, Memory.Range(10, 20, Some(Value.Put(10)), Value.Update(20)), skipList)
      insert(20, Memory.Range(20, 30, None, Value.Update(30)), skipList)
      insert(30, Memory.Range(30, 40, Some(Value.Put(30)), Value.Update(40)), skipList)
      insert(40, Memory.Range(40, 50, None, Value.Update(50)), skipList)

      insert(10, Memory.Range(10, 100, None, Value.Update(100)), skipList)
      skipList should have size 7

      skipList.get(1: Slice[Byte]) shouldBe Memory.Range(1, 5, Some(Value.Put(1)), Value.Update(5))
      skipList.get(5: Slice[Byte]) shouldBe Memory.Range(5, 10, None, Value.Update(10))
      skipList.get(10: Slice[Byte]) shouldBe Memory.Range(10, 20, Some(Value.Put(100)), Value.Update(100))
      skipList.get(20: Slice[Byte]) shouldBe Memory.Range(20, 30, None, Value.Update(100))
      skipList.get(30: Slice[Byte]) shouldBe Memory.Range(30, 40, Some(Value.Put(100)), Value.Update(100))
      skipList.get(40: Slice[Byte]) shouldBe Memory.Range(40, 50, None, Value.Update(100))
      skipList.get(50: Slice[Byte]) shouldBe Memory.Range(50, 100, None, Value.Update(100))
    }

    "insert overlapping ranges with values set and splits required" in {
      val skipList = new ConcurrentSkipListMap[Slice[Byte], Memory](ordering)
      insert(1, Memory.Range(1, 5, Some(Value.Put(1)), Value.Update(5)), skipList)
      insert(5, Memory.Range(5, 10, None, Value.Update(10)), skipList)
      insert(10, Memory.Range(10, 20, Some(Value.Put(10)), Value.Update(20)), skipList)
      insert(20, Memory.Range(20, 30, None, Value.Update(30)), skipList)
      insert(30, Memory.Range(30, 40, Some(Value.Put(30)), Value.Update(40)), skipList)
      insert(40, Memory.Range(40, 50, None, Value.Update(50)), skipList)

      insert(7, Memory.Range(7, 35, None, Value.Update(100)), skipList)
      skipList should have size 8

      skipList.get(1: Slice[Byte]) shouldBe Memory.Range(1, 5, Some(Value.Put(1)), Value.Update(5))
      skipList.get(5: Slice[Byte]) shouldBe Memory.Range(5, 7, None, Value.Update(10))
      skipList.get(7: Slice[Byte]) shouldBe Memory.Range(7, 10, None, Value.Update(100))
      skipList.get(10: Slice[Byte]) shouldBe Memory.Range(10, 20, Some(Value.Put(100)), Value.Update(100))
      skipList.get(20: Slice[Byte]) shouldBe Memory.Range(20, 30, None, Value.Update(100))
      skipList.get(30: Slice[Byte]) shouldBe Memory.Range(30, 35, Some(Value.Put(100)), Value.Update(100))
      skipList.get(35: Slice[Byte]) shouldBe Memory.Range(35, 40, None, Value.Update(40))
      skipList.get(40: Slice[Byte]) shouldBe Memory.Range(40, 50, None, Value.Update(50))
    }
  }
}