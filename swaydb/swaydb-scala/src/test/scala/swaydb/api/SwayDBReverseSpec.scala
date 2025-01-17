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
//import swaydb.effect.IOValues._
//import swaydb._
//import swaydb.core.CoreTestSweeper
//import swaydb.core.CoreTestSweeper._
//import swaydb.serializers.Default._
//import swaydb.slice.Slice
//import swaydb.slice.order.KeyOrder
//import swaydb.testkit.RunThis._
//import swaydb.core.file.CoreFileTestKit._
//
//class SwayDBReverse_Persistent_Spec extends SwayDBReverseSpec {
//  implicit val order: KeyOrder[Slice[Byte]] = KeyOrder.reverseLexicographic
//
//  val keyValueCount: Int = 10000
//
//  override def newDB()(implicit sweeper: CoreTestSweeper): Map[Int, String, Nothing, IO.ApiIO] =
//    swaydb.persistent.Map[Int, String, Nothing, IO.ApiIO](dir = genDirPath()).right.value.sweep(_.delete().get)
//}
//
//class SwayDBReverse_Memory_Spec extends SwayDBReverseSpec {
//  implicit val order: KeyOrder[Slice[Byte]] = KeyOrder.reverseLexicographic
//
//  val keyValueCount: Int = 100000
//
//  override def newDB()(implicit sweeper: CoreTestSweeper): Map[Int, String, Nothing, IO.ApiIO] =
//    swaydb.memory.Map[Int, String, Nothing, IO.ApiIO]().right.value.sweep(_.delete().get)
//}
//
//sealed trait SwayDBReverseSpec extends TestBaseAPI {
//
//  val keyValueCount: Int
//
//  def newDB()(implicit sweeper: CoreTestSweeper): Map[Int, String, Nothing, IO.ApiIO]
//
//  implicit val bag = Bag.apiIO
//
//  "Do reverse ordering" in {
//    runThis(times = repeatTest, log = true) {
//      CoreTestSweeper {
//        implicit sweeper =>
//
//          val db = newDB()
//
//          (1 to keyValueCount) foreach {
//            i =>
//              db.put(i, i.toString).right.value
//          }
//
//          db
//            .keys
//            .foldLeft(keyValueCount + 1) {
//              case (expected, actual) =>
//                actual shouldBe expected - 1
//                actual
//            }
//      }
//    }
//  }
//}
