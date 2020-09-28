/*
 * Copyright (c) 2020 Simer JS Plaha (simer.j@gmail.com - @simerplaha)
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with SwayDB. If not, see <https://www.gnu.org/licenses/>.
 *
 * Additional permission under the GNU Affero GPL version 3 section 7:
 * If you modify this Program, or any covered work, by linking or combining
 * it with other code, such other code is not for that reason alone subject
 * to any of the requirements of the GNU Affero GPL version 3.
 */

package swaydb.core.util.series

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import swaydb.core.util.series.growable.SeriesGrowable

import scala.collection.mutable.ListBuffer
import scala.reflect.ClassTag

class Volatile_SeriesGrowableSpec extends SeriesGrowableSpec {
  def create[T >: Null : ClassTag](lengthPerSeries: Int): SeriesGrowable[T] =
    SeriesGrowable.volatile[T](lengthPerSeries)
}

sealed trait SeriesGrowableSpec extends AnyWordSpec with Matchers {

  def create[T >: Null : ClassTag](lengthPerSlice: Int): SeriesGrowable[T]

  "throw ArrayIndexOutOfBoundsException" when {
    "empty" in {
      val series = create[Integer](0)
      assertThrows[ArrayIndexOutOfBoundsException](series.get(0))
      series.depth shouldBe 1
      series.length shouldBe 0
    }

    "nonEmpty" in {
      val series = SeriesGrowable.volatile[Integer](1)
      series.add(1)
      series.get(0) shouldBe 1
      series.iterator.toList should contain only 1
      series.depth shouldBe 1
      series.length shouldBe 1

      assertThrows[ArrayIndexOutOfBoundsException](series.get(1))
    }
  }

  "add" should {
    "increase depth" when {
      "space is not available" in {
        val series = create[Integer](2)
        series.depth shouldBe 1
        series.isEmpty shouldBe true

        series.add(1)
        series.depth shouldBe 1
        series.isEmpty shouldBe false

        series.add(2)
        series.depth shouldBe 1
        series.isEmpty shouldBe false

        series.add(3)
        series.depth shouldBe 2
        series.isEmpty shouldBe false

        series.length shouldBe 3
      }
    }
  }

  "lastOrNull and headOrNull" when {
    "empty" in {
      val series = create[Integer](2)
      series.headOrNull shouldBe null
      series.lastOrNull shouldBe null

      series.isEmpty shouldBe true
      series.length shouldBe 0
    }

    "nonEmpty" in {
      val series = create[Integer](2)
      series.isEmpty shouldBe true

      series.add(1)
      series.headOrNull shouldBe 1
      series.lastOrNull shouldBe 1
      series.length shouldBe 1

      series.add(2)
      series.headOrNull shouldBe 1
      series.lastOrNull shouldBe 2
      series.length shouldBe 2
    }
  }

  "findReverse" should {
    "throw ArrayIndexOutOfBoundsException" when {
      "empty" in {
        val series = create[Integer](0)
        assertThrows[ArrayIndexOutOfBoundsException](series.findReverse(0, 1)(_ => fail("should not have executed")))

        val series2 = create[Integer](2)
        assertThrows[ArrayIndexOutOfBoundsException](series2.findReverse(0, 1)(_ => fail("should not have executed")))
      }

      "nonEmpty" in {
        val series = create[Integer](1)
        series.add(1)
        assertThrows[ArrayIndexOutOfBoundsException](series.findReverse(2, 1)(_ => fail("should not have executed")))

        val series2 = create[Integer](2)
        series.add(1)
        series.add(2)
        assertThrows[ArrayIndexOutOfBoundsException](series2.findReverse(2, 1)(_ => fail("should not have executed")))
      }
    }

    "findReverse" when {
      "single item" in {
        val series = create[Integer](1)
        series.add(1)

        var iterations = 0
        val found = series.findReverse(0, Int.MinValue) {
          integer =>
            iterations += 1
            integer == 1
        }

        found shouldBe 1
        iterations shouldBe 1
      }

      "multiple items" should {
        val series = create[Integer](5)
        series.add(1)
        series.add(2)
        series.add(3)
        series.add(4)
        series.add(5)

        "start from index provided 1" in {
          val visits = ListBuffer.empty[Int]
          val found =
            series.findReverse(from = 4, nonResult = Int.MinValue) {
              integer =>
                visits += integer
                integer == 1
            }

          found shouldBe 1
          visits shouldBe List(5, 4, 3, 2, 1)
        }

        "start from index provided 2" in {
          val visits = ListBuffer.empty[Int]
          val found =
            series.findReverse(from = 3, nonResult = Int.MinValue) {
              integer =>
                visits += integer
                integer == 1
            }

          found shouldBe 1
          visits shouldBe List(4, 3, 2, 1)
        }

        "start from index provided 3" in {
          val visits = ListBuffer.empty[Int]
          val found =
            series.findReverse(from = 0, nonResult = Int.MinValue) {
              integer =>
                visits += integer
                integer == 1
            }

          found shouldBe 1
          visits shouldBe List(1)
        }

        "return none" when {
          "item not found" in {

            val visits = ListBuffer.empty[Int]
            val found =
              series.findReverse(4, Int.MinValue) {
                integer =>
                  visits += integer
                  integer == 100
              }

            found shouldBe Int.MinValue
            visits shouldBe List(5, 4, 3, 2, 1)
          }
        }
      }
    }

    "find" when {
      "single item" in {
        val series = create[Integer](1)
        series.add(1)

        var iterations = 0
        val found = series.find(0, Int.MinValue) {
          integer =>
            iterations += 1
            integer == 1
        }

        found shouldBe 1
        iterations shouldBe 1
      }

      "multiple items" should {
        val series = create[Integer](5)
        series.add(1)
        series.add(2)
        series.add(3)
        series.add(4)
        series.add(5)

        "start from index provided 1" in {
          val visits = ListBuffer.empty[Int]
          val found =
            series.find(from = 0, nonResult = Int.MinValue) {
              integer =>
                visits += integer
                integer == 5
            }

          found shouldBe 5
          visits shouldBe List(1, 2, 3, 4, 5)
        }

        "start from index provided 2" in {
          val visits = ListBuffer.empty[Int]
          val found =
            series.find(from = 1, nonResult = Int.MinValue) {
              integer =>
                visits += integer
                integer == 5
            }

          found shouldBe 5
          visits shouldBe List(2, 3, 4, 5)
        }

        "start from index provided 3" in {
          val visits = ListBuffer.empty[Int]
          val found =
            series.find(from = 4, nonResult = Int.MinValue) {
              integer =>
                visits += integer
                integer == 5
            }

          found shouldBe 5
          visits shouldBe List(5)
        }

        "return none" when {
          "item not found" in {

            val visits = ListBuffer.empty[Int]
            val found =
              series.find(0, Int.MinValue) {
                integer =>
                  visits += integer
                  integer == 100
              }

            found shouldBe Int.MinValue
            visits shouldBe List(1, 2, 3, 4, 5)
          }
        }
      }
    }

  }

}
