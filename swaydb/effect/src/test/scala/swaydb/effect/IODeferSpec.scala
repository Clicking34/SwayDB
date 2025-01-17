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

package swaydb.effect

import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import swaydb.{Error, IO}
import swaydb.Error.Segment.ExceptionHandler
import swaydb.IO.Defer
import swaydb.effect.IOValues._

import java.io.FileNotFoundException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random

class IODeferSpec extends AnyWordSpec with MockFactory {

  val unknownError = swaydb.Error.Fatal(this.getClass.getSimpleName + " test exception.")
  val recoverableError = swaydb.Error.FileNotFound(new FileNotFoundException())

  "apply" in {
    //asserts that deferred operation does not get invoke on creating.
    IO.Defer(fail())
    IO.Defer(fail(), unknownError)
  }

  "io" in {
    val deferred = IO.Defer.io(IO(fail()))
    deferred.isReady shouldBe true
    deferred.isComplete shouldBe false
  }

  "future" when {
    def testFuture[E: IO.ExceptionHandler, A](future: Future[A], expectedOutcome: IO[E, A]) = {
      val timeBeforeDeferred = System.currentTimeMillis()

      future.isCompleted shouldBe false
      val defer = IO.fromFuture[A](future)
      future.isCompleted shouldBe false
      defer.isPending shouldBe true
      defer.isReady shouldBe false

      val timeAfterDeferred = System.currentTimeMillis()

      //creating a future should not block on executing thread.
      (timeAfterDeferred - timeBeforeDeferred) should be <= 200.millisecond.toMillis

      defer.runBlockingIO match {
        case IO.Right(value) =>
          value shouldBe expectedOutcome.get

        case IO.Left(error) =>
          //on future failure the result Exception is wrapped within another Exception to stop recovery.
          error.exception.getCause shouldBe expectedOutcome.asInstanceOf[IO.Left[E, A]].exception
      }
    }

    "failure" in {
      (1 to 5) foreach {
        _ =>
          //if the future returns a recoverable error it should still not perform recovery.
          //since the future is complete with failure and is not recoverable.
          val error = if (Random.nextBoolean()) recoverableError else unknownError

          def future: Future[Int] =
            Future {
              Thread.sleep(2.seconds.toMillis)
              throw error.exception
            }

          testFuture(future, IO.Left(error: swaydb.Error.IO))
      }
    }

    "success" in {
      (1 to 5) foreach {
        _ =>
          def future: Future[Int] =
            Future {
              Thread.sleep(2.seconds.toMillis)
              Int.MaxValue
            }

          testFuture(future, IO.Right(Int.MaxValue))
      }
    }

    "concurrent success" in {
      (1 to 5) foreach {
        _ =>
          val futures: Seq[Future[Int]] =
            (1 to 5) map {
              _ =>
                Future {
                  val sleeping = Random.nextInt(10)
                  println(s"Sleep for $sleeping.seconds")
                  Thread.sleep(sleeping.seconds.toMillis)
                  println(s"Completed sleep $sleeping")
                  1
                }
            }

          val defer1 = IO.fromFuture[Int](futures(0))
          val defer2 = IO.fromFuture[Int](futures(1))
          val defer3 = IO.fromFuture[Int](futures(2))
          val defer4 = IO.fromFuture[Int](futures(3))
          val defer5 = IO.fromFuture[Int](futures(4))

          val createDefers = {
            defer1 flatMap {
              int1 =>
                defer2 flatMap {
                  int2 =>
                    defer3 flatMap {
                      int3 =>
                        defer4 flatMap {
                          int4 =>
                            defer5 map {
                              int5 =>
                                int1 + int2 + int3 + int4 + int5
                            }
                        }
                    }
                }
            }
          }
          if (Random.nextBoolean()) {
            createDefers.runIO shouldBe IO.Right(5)
            createDefers.runFutureIO shouldBe IO.Right(5)
          } else {
            createDefers.runFutureIO shouldBe IO.Right(5)
            createDefers.runIO shouldBe IO.Right(5)
          }
      }
    }

    "concurrent success" when {
      "future is initialised within deferred" in {
        (1 to 5) foreach {
          _ =>
            def future: Future[Int] =
              Future {
                val sleeping = Random.nextInt(10)
                println(s"Sleep for $sleeping.seconds")
                Thread.sleep(sleeping.seconds.toMillis)
                println(s"Completed sleep $sleeping.seconds")
                1
              }

            val createDefers =
            //              IO.Defer.unit.and(IO.fromFuture[Int](future)) //this re-creates Future again and again on reboot.
              IO.fromFuture[Int](future)

            if (Random.nextBoolean()) {
              createDefers.runIO shouldBe IO.Right(1)
              createDefers.runFutureIO shouldBe IO.Right(1)
            } else {
              createDefers.runFutureIO shouldBe IO.Right(1)
              createDefers.runIO shouldBe IO.Right(1)
            }
        }
      }
    }

  }

  "runIO" when {
    "successes" in {
      def doAssert[E](deferred: Defer[E, Int]) = {
        deferred.isComplete shouldBe false
        deferred.isReady shouldBe true

        deferred.runIO.get shouldBe 1
        deferred.isComplete shouldBe true
        deferred.isReady shouldBe true
      }

      doAssert(IO.Defer(1))
      doAssert(IO.Defer(1, unknownError))
      doAssert(IO.Defer(() => 1, Some(unknownError)))
      doAssert(IO.Defer(() => 1, None))
    }

    "failures" in {
      def doAssert[E](deferred: Defer[E, Int]) = {
        deferred.isComplete shouldBe false
        deferred.isReady shouldBe true

        deferred.runIO shouldBe IO.Left(unknownError)
        deferred.isComplete shouldBe false
        deferred.isReady shouldBe true
      }

      doAssert(IO.Defer[swaydb.Error.Segment, Int](throw unknownError.exception))
      doAssert(IO.Defer(throw unknownError.exception, unknownError)) //is not reserved Error
      doAssert(IO.Defer(throw unknownError.exception, recoverableError: swaydb.Error.IO))
      doAssert(IO.Defer(if (Random.nextBoolean()) throw recoverableError.exception else throw unknownError.exception, recoverableError: swaydb.Error.IO))
      doAssert(IO.Defer(() => throw unknownError.exception, Some(unknownError)))
    }
  }

  "map" when {
    "success" in {
      val mock = mockFunction[Int, Int]
      mock expects 1 returning 2

      val deferred =
        IO.Defer(1) map {
          int =>
            int shouldBe 1
            mock(int)
        }
      deferred.isReady shouldBe true
      deferred.isComplete shouldBe false

      deferred.runIO shouldBe IO.Right(2)

      deferred.isReady shouldBe true
      deferred.isComplete shouldBe true

      //deferred value is initialised initialised so the mock function is not invoked again.
      deferred.runIO shouldBe IO.Right(2)
    }

    "non-recoverable failure" in {
      var timesRun = 0

      val deferred =
        IO.Defer[swaydb.Error.Segment, Int](1) map {
          int =>
            int shouldBe 1
            timesRun += 1
            throw unknownError.exception
        }

      deferred.isReady shouldBe true
      deferred.isComplete shouldBe false

      deferred.runIO shouldBe IO.Left(unknownError)
      timesRun shouldBe 1
    }

    "recoverable failure" in {
      var timesRecovered = 0

      val deferred =
        IO.Defer[swaydb.Error.Segment, Int](1) map {
          int =>
            int shouldBe 1
            //return recoverable errors 10 times and then non-recoverable errors.
            if (timesRecovered < 10) {
              timesRecovered += 1
              throw recoverableError.exception
            }
            else
              throw unknownError.exception
        }

      deferred.isReady shouldBe true
      deferred.isComplete shouldBe false

      deferred.runIO shouldBe IO.Left(unknownError)
      timesRecovered shouldBe 10
    }
  }

  "flatMap" when {
    "successful" in {
      val mock1 = mockFunction[Int, Int]
      mock1 expects 1 returning 2

      val mock2 = mockFunction[Int, Int]
      mock2 expects 2 returning 3

      val mock3 = mockFunction[Int, Int]
      mock3 expects 3 returning 4

      val deferred =
        IO.Defer(1) flatMap {
          int =>
            val nextInt = mock1(int)
            IO.Defer(nextInt) flatMap {
              int =>
                val nextInt = mock2(int)
                IO.Defer(nextInt) flatMap {
                  int =>
                    val nextInt = mock3(int)
                    IO.Defer(nextInt)
                }
            }
        }

      deferred.isReady shouldBe true

      deferred.runIO shouldBe IO.Right(4)
      deferred.runIO shouldBe IO.Right(4)
    }

    "recoverable & non-recoverable failure" in {
      val value1 = mockFunction[Int]("value1")
      value1.expects() returning 1

      val value2 = mockFunction[Int, Int]("value2")
      value2 expects 1 returning 2

      val value3 = mockFunction[Int, Int]("value3")
      value3 expects 2 returning 3 repeat 4 //only expected this to be invoked multiple times since it's not cached.

      val value4 = mockFunction[Int, Int]("value4")
      value4 expects 3 returning 4

      //have 2 deferred as val so that their values get cached within.
      val secondDeferredCache = IO.Defer[swaydb.Error.Segment, Int](value2(1))
      val fourthDeferredCache = IO.Defer[swaydb.Error.Segment, Int](value4(3))
      //set the current error to throw.
      //the deferred tree below will set to be unknownError if a recoverable error is provided.
      var throwError: Option[Error] = Option(unknownError)

      val deferred: Defer[Error.Segment, Int] =
        IO.Defer[swaydb.Error.Segment, Int](value1()) flatMap {
          int =>
            int shouldBe 1
            secondDeferredCache flatMap {
              int =>
                secondDeferredCache.isComplete shouldBe true
                int shouldBe 2
                IO.Defer[swaydb.Error.Segment, Int](value3(int)) flatMap {
                  int =>
                    int shouldBe 3
                    fourthDeferredCache flatMap {
                      int =>
                        int shouldBe 4
                        fourthDeferredCache.isComplete shouldBe true
                        throwError map {
                          error =>
                            //if it's recoverable reset the error to be unknown so that call successfully succeeds.
                            //instead of running into an infinite loop.
                            if (error == recoverableError)
                              throwError = Some(unknownError)
                            throw error.exception
                        } getOrElse {
                          //if there is not error succeed.
                          IO.Defer[swaydb.Error.Segment, Int](int + 1)
                        }
                    }
                }
            }
        }

      deferred.isReady shouldBe true

      throwError = Some(unknownError)
      deferred.runBlockingIO shouldBe IO.Left(unknownError)
      throwError = Some(recoverableError)
      //recoverableErrors are never returned
      deferred.runBlockingIO shouldBe IO.Left(unknownError)
      throwError = None
      deferred.runBlockingIO shouldBe IO.Right(5)
    }
  }

  "flatMapIO" when {
    "successful deferred and IO" in {
      val deferred = IO.Defer[swaydb.Error.Segment, Int](10)

      deferred.isComplete shouldBe false
      deferred.isReady shouldBe true

      val ioDeferred: Defer[Error.Segment, Int] =
        deferred flatMapIO {
          result =>
            result shouldBe 10
            IO.Right[swaydb.Error.Segment, Int](result + 1)
        }

      ioDeferred.isComplete shouldBe false
      ioDeferred.isReady shouldBe true

      ioDeferred.runBlockingIO shouldBe IO.Right(11)
    }

    "successful deferred and failed IO" in {
      val deferred = IO.Defer[swaydb.Error.Segment, Int](10)

      deferred.isComplete shouldBe false
      deferred.isReady shouldBe true

      val failure = IO.failed[Error.Segment, Int]("Kaboom!")

      val ioDeferred: Defer[Error.Segment, Int] =
        deferred flatMapIO {
          result =>
            result shouldBe 10
            failure
        }

      ioDeferred.isComplete shouldBe false
      ioDeferred.isReady shouldBe true

      ioDeferred.runBlockingIO shouldBe IO.Left(swaydb.Error.Fatal(failure.exception))
    }

    "failed non-recoverable deferred and successful IO" in {
      val failure = IO.failed("Kaboom!")
      val deferred: Defer[Error.Segment, Int] = IO.Defer[swaydb.Error.Segment, Int](throw failure.exception)

      deferred.isComplete shouldBe false
      deferred.isReady shouldBe true

      val ioDeferred: Defer[Error.Segment, Int] =
        deferred flatMapIO {
          _ =>
            fail("should not have run")
        }

      ioDeferred.isComplete shouldBe false
      ioDeferred.isReady shouldBe true

      ioDeferred.runBlockingIO shouldBe IO.Left(swaydb.Error.Fatal(failure.exception))
    }

    "failed recoverable deferred and successful IO" in {
      (1 to 100) foreach {
        _ =>
          var errorToUse = Option(recoverableError)

          val deferred: Defer[Error.Segment, Int] =
            IO.Defer[swaydb.Error.Segment, Int] {
              errorToUse map {
                error =>
                  //first time around throw the recoverable error and then no error.
                  errorToUse = None
                  throw error.exception
              } getOrElse {
                10
              }
            }

          deferred.isComplete shouldBe false
          deferred.isReady shouldBe true

          val ioDeferred: Defer[Error.Segment, Int] =
            deferred flatMapIO {
              int =>
                int shouldBe 10
                IO.Right[swaydb.Error.Segment, Int](int + 1)
            }

          ioDeferred.isComplete shouldBe false
          ioDeferred.isReady shouldBe true

          ioDeferred.runBlockingIO shouldBe IO.Right(11)
      }
    }
  }

  "recover" when {
    "non-recoverable failure" in {
      import swaydb.Error.Segment.ExceptionHandler

      val deferred =
        IO.Defer[Error.Segment, Int](1) flatMap {
          i =>
            IO.Defer(i + 1) flatMap {
              i =>
                IO.Defer(i + 1) flatMap {
                  i =>
                    throw unknownError.exception
                }
            }
        } recover {
          case error: Error.Segment =>
            error shouldBe unknownError
            1
        }

      deferred.runIO shouldBe IO.Right(1)
    }

    "recoverable failure" in {
      @volatile var failureCount = 0
      import swaydb.Error.Segment.ExceptionHandler

      def deferred =
        IO.Defer[Error.Segment, Int](1) flatMap {
          i =>
            IO.Defer(i + 1) flatMap {
              i =>
                IO.Defer(i + 1) flatMap {
                  i =>
                    if (failureCount >= 6) {
                      IO.Defer(i + 1)
                    } else {
                      failureCount += 1
                      throw recoverableError.exception
                    }
                }
            }
        } recover {
          case _: Error.Segment =>
            fail("Didn't not expect recovery")
        }

      deferred.runBlockingIO shouldBe IO.Right(4)
      failureCount shouldBe 6
      failureCount = 0
      deferred.runFutureIO shouldBe IO.Right(4)
      failureCount shouldBe 6
    }

    "recoverable failure with non-recoverable failure result" in {
      @volatile var failureCount = 0
      import swaydb.Error.Segment.ExceptionHandler

      def deferred =
        IO.Defer[Error.Segment, Int](1) flatMap {
          i =>
            IO.Defer(i + 1) flatMap {
              i =>
                IO.Defer(i + 1) flatMap {
                  i =>
                    if (failureCount >= 6) {
                      throw unknownError.exception
                    } else {
                      failureCount += 1
                      throw recoverableError.exception
                    }
                }
            }
        } recover {
          case error: Error.Segment =>
            error shouldBe unknownError
            Int.MaxValue
        }

      deferred.runBlockingIO shouldBe IO.Right(Int.MaxValue)
      failureCount shouldBe 6
      failureCount = 0
      deferred.runFutureIO shouldBe IO.Right(Int.MaxValue)
      failureCount shouldBe 6
    }
  }

  "recoverWith" when {
    "non-recoverable failure" in {
      import swaydb.Error.Segment.ExceptionHandler

      def deferred =
        IO.Defer[Error.Segment, Int](1) flatMap {
          i =>
            IO.Defer(i + 1) flatMap {
              i =>
                IO.Defer(i + 1) flatMap {
                  i =>
                    throw unknownError.exception
                }
            }
        } recoverWith {
          case error: Error.Segment =>
            error shouldBe unknownError
            IO.Defer(1)
        }

      deferred.runBlockingIO shouldBe IO.Right(1)
      deferred.runFutureIO shouldBe IO.Right(1)
    }

    "non-recoverable failure when recoverWith result in recoverable Failure" in {
      import swaydb.Error.Segment.ExceptionHandler

      @volatile var failureCount = 0

      def recoveredDeferred =
        IO.Defer[Error.Segment, Int](1) flatMap {
          i =>
            IO.Defer(i + 1) flatMap {
              i =>
                IO.Defer(i + 1) flatMap {
                  i =>
                    if (failureCount >= 6) {
                      IO.Defer(i + 1)
                    } else {
                      failureCount += 1
                      throw recoverableError.exception
                    }
                }
            }
        } recover {
          case _: Error.Segment =>
            fail("Didn't not expect recovery")
        }

      val deferred =
        IO.Defer[Error.Segment, Int](1) flatMap {
          i =>
            IO.Defer(i + 1) flatMap {
              i =>
                IO.Defer(i + 1) flatMap {
                  i =>
                    throw unknownError.exception
                }
            }
        } recoverWith {
          case error: Error.Segment =>
            error shouldBe unknownError
            recoveredDeferred
        }

      deferred.runBlockingIO shouldBe IO.Right(4)
      deferred.runFutureIO shouldBe IO.Right(4)
    }

    "recoverable failure" in {
      import swaydb.Error.Segment.ExceptionHandler

      @volatile var failureCount = 0

      def deferred =
        IO.Defer[Error.Segment, Int](1) flatMap {
          i =>
            IO.Defer(i + 1) flatMap {
              i =>
                IO.Defer(i + 1) flatMap {
                  i =>
                    if (failureCount >= 6) {
                      IO.Defer(i + 1)
                    } else {
                      failureCount += 1
                      throw recoverableError.exception
                    }
                }
            }
        } recoverWith {
          case _: Error.Segment =>
            fail("Didn't not expect recovery")
        }

      deferred.runBlockingIO shouldBe IO.Right(4)
      failureCount shouldBe 6
      failureCount = 0
      deferred.runFutureIO shouldBe IO.Right(4)
      failureCount shouldBe 6
    }

    "recoverable failure with non-recoverable failure result" in {
      @volatile var failureCount = 0
      import swaydb.Error.Segment.ExceptionHandler

      def deferred =
        IO.Defer[Error.Segment, Int](1) flatMap {
          i =>
            IO.Defer(i + 1) flatMap {
              i =>
                IO.Defer(i + 1) flatMap {
                  i =>
                    if (failureCount >= 6) {
                      throw unknownError.exception
                    } else {
                      failureCount += 1
                      throw recoverableError.exception
                    }
                }
            }
        } recoverWith {
          case error: Error.Segment =>
            error shouldBe unknownError
            IO.Defer(Int.MaxValue)
        }

      deferred.runBlockingIO shouldBe IO.Right(Int.MaxValue)
      failureCount shouldBe 6
      failureCount = 0
      deferred.runFutureIO shouldBe IO.Right(Int.MaxValue)
      failureCount shouldBe 6
    }
  }

  "concurrent randomly releases" in {
    import swaydb.Error.Segment.ExceptionHandler

    val defers: Seq[IO.Defer[Error.Segment, Int]] =
      (1 to 100) map {
        i =>
          if (Random.nextBoolean()) {
            var i = 0
            IO.Defer[Error.Segment, Int] {
              if (i == 10) {
                1
              } else {
                i += 1
                throw recoverableError.exception
              }
            }
          } else if (Random.nextBoolean())
            IO.Defer[Error.Segment, Int] {
              val sleeping = Random.nextInt(3)
              println(s"Sleep for $sleeping.seconds")
              Thread.sleep(sleeping.seconds.toMillis)
              1
            }
          else if (Random.nextBoolean())
            IO.Defer[Error.Segment, Int] {
              if (Random.nextBoolean()) {
                1
              } else {
                throw recoverableError.exception
              }
            }
          else
            IO.Defer[swaydb.Error.Segment, Int](1)
      }

    val flattenedDefers =
      defers.foldLeft(IO.Defer[Error.Segment, Int](1)) {
        case (previousDefer, nextDefer) =>
          previousDefer flatMap {
            _ =>
              nextDefer
          }
      }

    flattenedDefers.runIO.get shouldBe 1
  }
}
