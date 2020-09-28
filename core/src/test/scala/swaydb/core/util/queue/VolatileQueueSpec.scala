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

package swaydb.core.util.queue

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import swaydb.core.TestData._
import swaydb.core.TestExecutionContext
import swaydb.data.RunThis._

import scala.collection.mutable.ListBuffer
import scala.collection.parallel.CollectionConverters._
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class VolatileQueueSpec extends AnyWordSpec with Matchers {

  implicit class WalkerToList[A >: Null](walker: Walker[A]) {
    //used for testing only. Always use iterator instead
    def toList: List[A] = {
      val buffer = ListBuffer.empty[A]

      walker.foreach {
        item =>
          buffer += item
      }

      buffer.toList
    }

  }

  def expect[A >: Null](queue: VolatileQueue[A])(headOrNull: A,
                                                 lastOrNull: A,
                                                 headOption: Option[A],
                                                 size: Int,
                                                 list: List[A]): Unit = {

    //head and last
    queue.headOrNull() shouldBe headOrNull
    queue.lastOrNull() shouldBe lastOrNull
    queue.headOption() shouldBe headOption

    //size
    queue.size shouldBe size

    //iterator
    queue.iterator.toList shouldBe list

    //walker
    queue.walker.toList shouldBe list

  }

  "addHead" when {
    "empty" should {
      "return empty state" in {
        val queue: VolatileQueue[Integer] = VolatileQueue[Integer]()

        expect(queue)(
          headOrNull = null,
          lastOrNull = null,
          headOption = None,
          size = 0,
          list = List.empty,
        )
      }

      "insert head element" in {
        val queue = VolatileQueue[Integer]()
        queue.addHead(10)

        expect(queue)(
          headOrNull = 10,
          lastOrNull = 10,
          headOption = Some(10),
          size = 1,
          list = List(10),
        )
      }
    }

    "nonEmpty" should {
      "insert head element" in {
        val queue: VolatileQueue[Integer] = VolatileQueue[Integer](1)

        expect(queue)(
          headOrNull = 1,
          lastOrNull = 1,
          headOption = Some(1),
          size = 1,
          list = List(1),
        )

        queue.addHead(0)

        expect(queue)(
          headOrNull = 0,
          lastOrNull = 1,
          headOption = Some(0),
          size = 2,
          list = List(0, 1),
        )
      }

      "insert multiple head element" in {
        val queue: VolatileQueue[Integer] = VolatileQueue[Integer]()

        queue.addHead(3)
        queue.addHead(2)
        queue.addHead(1)

        expect(queue)(
          headOrNull = 1,
          lastOrNull = 3,
          headOption = Some(1),
          size = 3,
          list = List(1, 2, 3),
        )
      }
    }
  }

  "addLast" when {
    "empty" should {
      "insert last element" in {
        val queue = VolatileQueue[Integer]()
        queue.addLast(10)

        expect(queue)(
          headOrNull = 10,
          lastOrNull = 10,
          headOption = Some(10),
          size = 1,
          list = List(10),
        )
      }
    }

    "nonEmpty" should {
      "insert head element" in {
        val queue: VolatileQueue[Integer] = VolatileQueue[Integer](0)

        queue.addLast(1)

        expect(queue)(
          headOrNull = 0,
          lastOrNull = 1,
          headOption = Some(0),
          size = 2,
          list = List(0, 1),
        )
      }

      "insert multiple head element" in {
        val queue: VolatileQueue[Integer] = VolatileQueue[Integer]()

        queue.addLast(1)
        queue.addLast(2)
        queue.addLast(3)

        expect(queue)(
          headOrNull = 1,
          lastOrNull = 3,
          headOption = Some(1),
          size = 3,
          list = List(1, 2, 3),
        )
      }
    }
  }

  "removeLast" when {
    "empty" should {
      "fail" in {
        val queue: VolatileQueue[Integer] = VolatileQueue[Integer]()

        assertThrows[Exception](queue.removeLast(10))

        expect(queue)(
          headOrNull = null,
          lastOrNull = null,
          headOption = None,
          size = 0,
          list = List.empty,
        )
      }
    }

    "invalid last" should {
      "fail" in {
        val queue: VolatileQueue[Integer] = VolatileQueue[Integer](0)

        //last value is 0, not 10
        assertThrows[Exception](queue.removeLast(10))

        expect(queue)(
          headOrNull = 0,
          lastOrNull = 0,
          headOption = Some(0),
          size = 1,
          list = List(0),
        )
      }
    }

    "non empty" should {
      "remove last" in {
        val queue: VolatileQueue[Integer] = VolatileQueue[Integer]()
        queue.addLast(1)
        queue.addLast(2)
        queue.addLast(3)
        queue.addLast(4)

        queue.addHead(0)

        expect(queue)(
          headOrNull = 0,
          lastOrNull = 4,
          headOption = Some(0),
          size = 5,
          list = List(0, 1, 2, 3, 4),
        )

        queue.removeLast(4)
        expect(queue)(
          headOrNull = 0,
          lastOrNull = 3,
          headOption = Some(0),
          size = 4,
          list = List(0, 1, 2, 3),
        )

        queue.removeLast(3)
        expect(queue)(
          headOrNull = 0,
          lastOrNull = 2,
          headOption = Some(0),
          size = 3,
          list = List(0, 1, 2),
        )

        queue.removeLast(2)
        expect(queue)(
          headOrNull = 0,
          lastOrNull = 1,
          headOption = Some(0),
          size = 2,
          list = List(0, 1),
        )

        queue.removeLast(1)
        expect(queue)(
          headOrNull = 0,
          lastOrNull = 0,
          headOption = Some(0),
          size = 1,
          list = List(0),
        )

        queue.removeLast(0)
        expect(queue)(
          headOrNull = null,
          lastOrNull = null,
          headOption = None,
          size = 0,
          list = List.empty,
        )

        //add last
        queue.addLast(Int.MaxValue)
        expect(queue)(
          headOrNull = Int.MaxValue,
          lastOrNull = Int.MaxValue,
          headOption = Some(Int.MaxValue),
          size = 1,
          list = List(Int.MaxValue),
        )

        queue.removeLast(Int.MaxValue)
        expect(queue)(
          headOrNull = null,
          lastOrNull = null,
          headOption = None,
          size = 0,
          list = List.empty,
        )

        //add head
        queue.addHead(Int.MaxValue)
        expect(queue)(
          headOrNull = Int.MaxValue,
          lastOrNull = Int.MaxValue,
          headOption = Some(Int.MaxValue),
          size = 1,
          list = List(Int.MaxValue),
        )

        queue.removeLast(Int.MaxValue)
        expect(queue)(
          headOrNull = null,
          lastOrNull = null,
          headOption = None,
          size = 0,
          list = List.empty,
        )
      }
    }
  }

  "concurrent" in {
    implicit val ec = TestExecutionContext.executionContext

    val queue = VolatileQueue[Integer]()

    val add =
      Future {
        (1 to 100000).par foreach {
          i =>
            if (i % 1000 == 0) println(s"Write: $i")
            if (randomBoolean())
              queue.addHead(i)
            else
              queue.addLast(i)
        }
      }

    val remove =
      Future {
        (1 to 10000).par foreach {
          i =>
            if (i % 100 == 0) println(s"Remove: $i")

            try
              queue.removeLast(queue.lastOrNull())
            catch {
              case exception: Exception =>

            }
        }
      }

    val read =
      Future {
        (1 to 10000).par foreach {
          i =>
            if (i % 100 == 0) println(s"Read: $i")
            if (randomBoolean())
              queue.iterator.foreach(_ => ())
            else
              queue.walker.foreach(_ => ())
        }
      }

    Future.sequence(Seq(add, remove, read)).await(1.minute)
  }

  "read" in {
    val queue = VolatileQueue[Integer]()

    (1 to 100000) foreach {
      i =>
        queue.addLast(i)
    }

    (1 to 10000).par foreach {
      i =>
        if (i % 100 == 0) println(s"Iteration: $i")

        if (randomBoolean()) {
          queue.iterator.foldLeft(1) {
            case (expected, next) =>
              next shouldBe expected
              expected + 1
          }
        }
        else
          queue.walker.toList shouldBe (1 to 100000)
    }
  }
}
