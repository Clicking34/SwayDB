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
//
//package swaydb.effect
//
//import org.scalatest.matchers.should.Matchers._
//import org.scalatest.wordspec.AnyWordSpec
//import swaydb.IO
//import swaydb.IO.ExceptionHandler.Throwable
//import swaydb.slice.Slice
//import swaydb.slice.Slice
//import swaydb.slice.Slice
//
//import scala.collection.mutable.ListBuffer
//
//class IOSpec extends AnyWordSpec {
//
//  "CatchLeak" when {
//    "exception" in {
//      val exception = new Exception("Failed")
//
//      def io: IO.Right[swaydb.Error.Segment, Unit] = throw exception
//
//      IO.Catch(io).left.get shouldBe swaydb.Error.Fatal(exception)
//    }
//
//    "no exception" in {
//      IO.Catch(IO.none).get shouldBe empty
//    }
//  }
//
//  "foreachIO" should {
//    "terminate in first failure returning the failure" in {
//      val slice = Slice(1, 2, 3, 4)
//      var iterations = 0
//
//      val result: Option[IO.Left[Throwable, Int]] =
//        slice foreachIO {
//          item =>
//            iterations += 1
//            if (item == 3)
//              IO.failed(s"result at item $item")
//            else
//              IO.Right(item)
//        }
//
//      result.isDefined shouldBe true
//      result.get.exception.getMessage shouldBe "result at item 3"
//      iterations shouldBe 3
//    }
//
//    "iterate all even on failure if failFast = false" in {
//      val slice = Slice(1, 2, 3, 4)
//      var iterations = 0
//
//      val result: Option[IO.Left[Throwable, Int]] =
//        slice.foreachIO(
//          item => {
//            iterations += 1
//            if (item == 3)
//              IO.failed(s"result at item $item")
//            else {
//              IO.Right(item)
//            }
//          },
//          failFast = false
//        )
//
//      result.isDefined shouldBe true
//      result.get.exception.getMessage shouldBe "result at item 3"
//      iterations shouldBe 4
//    }
//
//    "finish iteration on IO.Right IO's" in {
//      val slice = Slice(1, 2, 3, 4)
//
//      val result: Option[IO.Left[Throwable, Int]] =
//        slice.foreachIO {
//          item =>
//            IO.Right(item)
//        }
//
//      result.isEmpty shouldBe true
//    }
//  }
//
//  "mapIO" should {
//
//    "allow map on Slice" in {
//      val slice = Slice(1, 2, 3, 4, 5)
//
//      val result: IO[Throwable, Slice[Int]] =
//        slice.mapRecoverIO {
//          item =>
//            IO.Right(item + 1)
//        }
//
//      result.get.toArray shouldBe Array(2, 3, 4, 5, 6)
//    }
//
//    "allow map on Slice, terminating at first failure returning the failure and cleanUp should be invoked" in {
//      val slice = Slice(1, 2, 3, 4, 5)
//
//      val intsCleanedUp = ListBuffer.empty[Int]
//
//      val result: IO[Throwable, Slice[Int]] =
//        slice.mapRecoverIO(
//          block = item => if (item == 3) IO.failed(s"Failed at $item") else IO.Right(item),
//          recover = (ints: Slice[Int], _: IO.Left[Throwable, Slice[Int]]) => ints.foreach(intsCleanedUp += _)
//        )
//
//      result.isLeft shouldBe true
//      result.left.get.getMessage shouldBe "Failed at 3"
//
//      intsCleanedUp should contain inOrderOnly(1, 2)
//    }
//  }
//
//  "flatMapIO" should {
//
//    "return flattened list" in {
//      val slice = Slice(1, 2, 3, 4, 5)
//
//      val result: IO[Throwable, Iterable[String]] =
//        slice flatMapRecoverIO {
//          item =>
//            IO(Seq(item.toString))
//        }
//
//      result.get.toArray shouldBe Array("1", "2", "3", "4", "5")
//    }
//
//    "return failure" in {
//      val slice = Slice(1, 2, 3, 4, 5)
//
//      val result: IO[Throwable, Iterable[String]] =
//        slice flatMapRecoverIO {
//          item =>
//            if (item < 3) {
//              IO(List(item.toString))
//            }
//            else {
//              IO.failed("Kaboom!")
//            }
//        }
//      result.left.get.getMessage shouldBe "Kaboom!"
//    }
//  }
//
//  "foldLeftIO" should {
//    "allow fold, terminating at first failure returning the failure" in {
//      val slice = Slice("one", "two", "three")
//
//      val result: IO[Throwable, Int] =
//        slice.foldLeftRecoverIO(0) {
//          case (count, item) =>
//            if (item == "two")
//              IO.failed(s"Failed at $item")
//            else
//              IO.Right(count + 1)
//        }
//
//      result.isLeft shouldBe true
//      result.left.get.getMessage shouldBe "Failed at two"
//    }
//
//    "allow fold on Slice" in {
//      val slice = Slice("one", "two", "three")
//
//      val result: IO[Throwable, Int] =
//        slice.foldLeftRecoverIO(0) {
//          case (count, _) =>
//            IO.Right(count + 1)
//        }
//
//      result.isRight shouldBe true
//      result.get shouldBe 3
//    }
//  }
//
//  "untilSome" should {
//    "return first as success" in {
//      val slice = Slice(1, 2, 3, 4)
//      var iterations = 0
//
//      val result: IO[Throwable, Option[(Int, Int)]] =
//        slice untilSome {
//          item => {
//            iterations += 1
//            IO.Right(Some(item))
//          }
//        }
//
//      result.get should contain((1, 1))
//      iterations shouldBe 1
//    }
//
//    "return last as success" in {
//      val slice = Slice(1, 2, 3, 4)
//      var iterations = 0
//
//      val result: IO[Throwable, Option[(Int, Int)]] =
//        slice untilSome {
//          item => {
//            iterations += 1
//            if (item == 4)
//              IO.Right(Some(item))
//            else
//              IO.none
//          }
//        }
//
//      result.get should contain((4, 4))
//      iterations shouldBe 4
//    }
//
//    "return mid result that resulted in Some value" in {
//      val slice = Slice(1, 2, 3, 4)
//      var iterations = 0
//
//      val result: IO[Throwable, Option[(Int, Int)]] =
//        slice untilSome {
//          item => {
//            iterations += 1
//            if (item == 3)
//              IO.Right(Some(item))
//            else
//              IO.none
//          }
//        }
//
//      result.get should contain((3, 3))
//      iterations shouldBe 3
//    }
//
//    "return None if all iterations return None" in {
//      val slice = Slice(1, 2, 3, 4)
//      var iterations = 0
//
//      val result: IO[Throwable, Option[(Nothing, Int)]] =
//        slice untilSome {
//          _ => {
//            iterations += 1
//            IO.none
//          }
//        }
//
//      result.get shouldBe empty
//      iterations shouldBe 4
//    }
//
//    "terminate early on failure" in {
//      val slice = Slice(1, 2, 3, 4)
//      var iterations = 0
//
//      val result: IO[Throwable, Option[(Nothing, Int)]] =
//        slice untilSome {
//          item => {
//            iterations += 1
//            IO.failed(s"Failed at $item")
//          }
//        }
//
//      result.isLeft shouldBe true
//      result.left.get.getMessage shouldBe "Failed at 1"
//      iterations shouldBe 1
//    }
//  }
//
//  "tryOrNone" when {
//    "exception" in {
//      IO.tryOrNone(throw new Exception("Failed")) shouldBe empty
//    }
//
//    "no exception" in {
//      IO.tryOrNone("success").get shouldBe "success"
//    }
//  }
//
//  "Failure fromTry" in {
//    val exception = new Exception("failed")
//    val failure = IO.fromTry(scala.util.Failure(exception))
//    failure shouldBe a[IO.Left[_, _]]
//    failure.asInstanceOf[IO.Left[_, _]].exception shouldBe exception
//  }
//
//  "Success fromTry" in {
//    val failure = IO.fromTry(scala.util.Success(1))
//    failure shouldBe a[IO.Right[_, _]]
//    failure.asInstanceOf[IO.Right[_, _]].get shouldBe 1
//  }
//}
