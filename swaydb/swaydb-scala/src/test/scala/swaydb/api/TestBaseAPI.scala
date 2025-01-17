///*
// * Copyright 2018 Simer JS Plaha (simer.j@gmail.com - @simerplaha)
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// * http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package swaydb.api
//
//import org.scalatest.PrivateMethodTester._
//import org.scalatest.exceptions.TestFailedException
//import swaydb.IO.ApiIO
//import swaydb.effect.IOValues._
//import swaydb._
//import swaydb.config.sequencer.Sequencer
//import swaydb.core.{Core, ACoreSpec, TestExecutionContext}
//import swaydb.multimap.{MultiKey, MultiValue}
//import swaydb.slice.Slice
//import swaydb.testkit.RunThis._
//import swaydb.testkit.TestKit._
//
//import scala.annotation.tailrec
//import scala.concurrent.Future
//import scala.concurrent.duration._
//import scala.util.Random
//
//trait TestBaseAPI extends ACoreSpec {
//
//  val keyValueCount: Int
//
//  implicit class MultiMapInnerMap[M, K, V, F, BAG[_]](root: MultiMap[M, K, V, F, BAG]) {
//    def multiMapReflection = {
//      val function = PrivateMethod[Map[MultiKey[M, K], MultiValue[V], PureFunction[MultiKey[M, K], MultiValue[V], Apply.Map[MultiValue[V]]], BAG]](Symbol("multiMap"))
//      root.invokePrivate(function())
//    }
//  }
//
//  def getInnerMap[M, K, V, F, BAG[_]](root: MultiMap[M, K, V, F, BAG]): Map[MultiKey[M, K], MultiValue[V], PureFunction[MultiKey[M, K], MultiValue[V], Apply.Map[MultiValue[V]]], BAG] = {
//    val function = PrivateMethod[Map[MultiKey[M, K], MultiValue[V], PureFunction[MultiKey[M, K], MultiValue[V], Apply.Map[MultiValue[V]]], BAG]](Symbol("multiMap"))
//    root.invokePrivate(function())
//  }
//
//  def getCore[A, F, BAG[_]](root: Set[A, F, BAG]): Core[BAG] = {
//    val function = PrivateMethod[Core[BAG]](Symbol("core"))
//    root.invokePrivate(function())
//  }
//
//  def getCore[K, V, BAG[_]](root: SetMapT[K, V, BAG]): Core[BAG] = {
//    val function = PrivateMethod[Core[BAG]](Symbol("core"))
//    root.invokePrivate(function())
//  }
//
//  def getSequencer[K, V, BAG[+_]](root: SetMapT[K, V, BAG]): Sequencer[BAG] = {
//    val function = PrivateMethod[Core[BAG]](Symbol("core"))
//    val core = root.invokePrivate(function())
//    getSequencer(core)
//  }
//
//  def getSequencer[BAG[+_]](core: Core[BAG]): Sequencer[BAG] = {
//    val function = PrivateMethod[Sequencer[BAG]](Symbol("sequencer"))
//    core.invokePrivate(function())
//  }
//
//  def printMap[BAG[_]](root: MultiMap[_, _, _, _, BAG]): Unit = {
//    root.multiMapReflection.toBag[Glass].materialize.foreach {
//      map =>
//        println(map)
//        map._1 match {
//          case MultiKey.End(_) => println //new line
//          case _ =>
//        }
//    }
//
//    println("-" * 100)
//  }
//
//  /**
//   * Randomly adds child Maps to [[MultiMap]] and returns the last added Map.
//   */
//  def generateRandomNestedMaps(root: MultiMap[Int, Int, String, Nothing, IO.ApiIO]): MultiMap[Int, Int, String, Nothing, ApiIO] = {
//    val range = 1 to Random.nextInt(100)
//
//    val sub =
//      range.foldLeft(root) {
//        case (root, id) =>
//          val sub =
//            if (Random.nextBoolean())
//              root.child(id).value
//            else
//              root
//
//          if (Random.nextBoolean())
//            root
//          else
//            sub
//      }
//
//    sub
//  }
//
//  def doAssertEmpty[V](db: SetMapT[Int, V, IO.ApiIO]) =
//    (1 to keyValueCount) foreach {
//      i =>
//        try
//          db.get(i).right.value shouldBe empty
//        catch {
//          case _: TestFailedException =>
//            //if it's not empty then check if the expiration was going to occur in near millisecond time.
//            db.expiration(i).value match {
//              case Some(deadline) =>
//                //print out for debugging
//                //                import swaydb.config.util.FiniteDurations._
//                //                println("Time-left: " + deadline.timeLeft.asString)
//
//                //if timeLeft is false then read again should return empty now
//                deadline.hasTimeLeft() shouldBe false
//                db.get(i).right.value shouldBe empty
//
//              case None =>
//                //if expiration is none then the key-value should return empty.
//                db.get(i).right.value shouldBe empty
//            }
//        }
//    }
//
//  def pluralSegment(count: Int) = if (count == 1) "Segment" else "Segments"
//
//  //recursively go through all levels and assert they do no have any Segments.
//  //Note: Could change this test to use Future with delays instead of blocking but the blocking code is probably more easier to read.
//
//  def assertLevelsAreEmpty(db: SetMapT[Int, String, IO.ApiIO], submitUpdates: Boolean) = {
//    println("Checking levels are empty.")
//
//    @tailrec
//    def checkEmpty(levelNumber: Int, expectedLastLevelEmpty: Boolean): Unit = {
//      db.levelMeter(levelNumber) match {
//        case Some(meter) if db.levelMeter(levelNumber + 1).nonEmpty => //is not the last Level. Check if this level contains no Segments.
//          //          db.isEmpty shouldBe true //isEmpty will always return true since all key-values were removed.
//          if (meter.segmentsCount == 0) { //this Level is empty, jump to next Level.
//            println(s"Level $levelNumber is empty.")
//            checkEmpty(levelNumber + 1, expectedLastLevelEmpty)
//          } else {
//            val interval = (levelNumber * 3).seconds //Level is not empty, try again with delay.
//            println(s"Level $levelNumber contains ${meter.segmentsCount} ${pluralSegment(meter.segmentsCount)}. Will check again after $interval.")
//            sleep(interval)
//            checkEmpty(levelNumber, expectedLastLevelEmpty)
//          }
//        case _ => //is the last Level which will contains Segments.
//          if (!expectedLastLevelEmpty) {
//            if (submitUpdates) {
//              println(s"Level $levelNumber. Submitting updated to trigger remove.")
//              (1 to 500000) foreach { //submit multiple update range key-values so that a map gets submitted for compaction and to trigger merge on copied Segments in last Level.
//                i =>
//                  db match {
//                    case map @ Map(core) =>
//                      map.update(1, 1000000, value = "just triggering update to assert remove").right.value
//
//                    case SetMap(set) =>
//                      getCore(set).update(fromKey = Slice.writeInt(1), to = Slice.writeInt(1000000), value = Slice.Null).right.value
//                  }
//
//                  if (i == 100000) sleep(2.seconds)
//              }
//            }
//            //update submitted, now expect the merge to unsafeGet triggered on the Segments in the last Level and Compaction to remove all key-values.
//          }
//
//          //          db.isEmpty shouldBe true //isEmpty will always return true since all key-values were removed.
//
//          val segmentsCount = db.levelMeter(levelNumber).map(_.segmentsCount) getOrElse -1
//          if (segmentsCount != 0) {
//            println(s"Level $levelNumber contains $segmentsCount ${pluralSegment(segmentsCount)}. Will check again after 8.seconds.")
//            sleep(8.seconds)
//            checkEmpty(levelNumber, true)
//          } else {
//            println(s"Compaction completed. Level $levelNumber is empty.\n")
//          }
//      }
//    }
//
//    implicit val ec = TestExecutionContext.executionContext
//    //this test might take a while depending on the Compaction speed but it should not run for too long hence the timeout.
//    Future(checkEmpty(1, false)).await(10.minutes)
//  }
//
//  def doExpire(from: Int, to: Int, deadline: Deadline, db: SetMapT[Int, String, IO.ApiIO]): Unit =
//    db match {
//      case db @ Map(_) =>
//        eitherOne(
//          left = (from to to) foreach (i => db.expire(i, deadline).right.value),
//          right = db.expire(from, to, deadline).right.value
//        )
//
//      case _ =>
//        (from to to) foreach (i => db.expire(i, deadline).right.value)
//    }
//
//  def doRemove(from: Int, to: Int, db: SetMapT[Int, String, IO.ApiIO]): Unit =
//    db match {
//      case db @ Map(_) =>
//        eitherOne(
//          left = (from to to) foreach (i => db.remove(i).right.value),
//          right = db.remove(from = from, to = to).right.value
//        )
//
//      case _ =>
//        (from to to) foreach (i => db.remove(i).right.value)
//    }
//
//  def doUpdateOrIgnore(from: Int, to: Int, value: String, db: SetMapT[Int, String, IO.ApiIO]): Unit =
//    db match {
//      case db @ Map(_) =>
//        eitherOne(
//          left = (from to to) foreach (i => db.update(i, value = value).right.value),
//          right = db.update(from, to, value = value).right.value
//        )
//
//      case _ =>
//        ()
//    }
//}
