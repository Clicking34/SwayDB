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
//import swaydb.testkit.RunThis.runThis
//import swaydb.core.file.CoreFileTestKit._
//
//class SwayDBSourceSpec0 extends SwayDBSourceSpec {
//  override def newDB()(implicit sweeper: CoreTestSweeper): Map[Int, String, Nothing, IO.ApiIO] =
//    swaydb.persistent.Map[Int, String, Nothing, IO.ApiIO](genDirPath()).right.value.sweep(_.delete().get)
//
//  override val keyValueCount: Int = 100
//}
//
//class SwayDBSource_SetMap_Spec0 extends SwayDBSourceSpec {
//  override def newDB()(implicit sweeper: CoreTestSweeper): SetMap[Int, String, IO.ApiIO] =
//    swaydb.persistent.SetMap[Int, String, IO.ApiIO](genDirPath()).right.value.sweep(_.delete().get)
//
//  override val keyValueCount: Int = 100
//}
//
//class SwayDBSourceSpec1 extends SwayDBSourceSpec {
//
//  override val keyValueCount: Int = 100
//
//  override def newDB()(implicit sweeper: CoreTestSweeper): Map[Int, String, Nothing, IO.ApiIO] =
//    swaydb.persistent.Map[Int, String, Nothing, IO.ApiIO](genDirPath(), logSize = 1.byte).right.value.sweep(_.delete().get)
//}
//
//class SwayDBSourceSpec2 extends SwayDBSourceSpec {
//
//  override val keyValueCount: Int = 100
//
//  override def newDB()(implicit sweeper: CoreTestSweeper): Map[Int, String, Nothing, IO.ApiIO] =
//    swaydb.memory.Map[Int, String, Nothing, IO.ApiIO](logSize = 1.byte).right.value.sweep(_.delete().get)
//}
//
//class SwayDBSourceSpec3 extends SwayDBSourceSpec {
//
//  override val keyValueCount: Int = 100
//
//  override def newDB()(implicit sweeper: CoreTestSweeper): Map[Int, String, Nothing, IO.ApiIO] =
//    swaydb.memory.Map[Int, String, Nothing, IO.ApiIO]().right.value.sweep(_.delete().get)
//}
//
//class MultiMapSwayDBSourceSpec4 extends SwayDBSourceSpec {
//  val keyValueCount: Int = 10000
//
//  override def newDB()(implicit sweeper: CoreTestSweeper): MapT[Int, String, Nothing, IO.ApiIO] =
//    generateRandomNestedMaps(swaydb.persistent.MultiMap[Int, Int, String, Nothing, IO.ApiIO](dir = genDirPath()).get).sweep(_.delete().get)
//}
//
//class MultiMapSwayDBSourceSpec5 extends SwayDBSourceSpec {
//  val keyValueCount: Int = 10000
//
//  override def newDB()(implicit sweeper: CoreTestSweeper): MapT[Int, String, Nothing, IO.ApiIO] =
//    generateRandomNestedMaps(swaydb.memory.MultiMap[Int, Int, String, Nothing, IO.ApiIO]().get).sweep(_.delete().get)
//}
//
//sealed trait SwayDBSourceSpec extends TestBaseAPI {
//
//  def newDB()(implicit sweeper: CoreTestSweeper): SetMapT[Int, String, IO.ApiIO]
//
//  implicit val bag = Bag.glass
//
//  "it" should {
//
//    "transformValue" in {
//      runThis(times = repeatTest, log = true) {
//        CoreTestSweeper {
//          implicit sweeper =>
//
//            val db = newDB()
//
//            (1 to 100) foreach {
//              i =>
//                db.put(i, i.toString).right.value
//            }
//
//            val result =
//              db
//                .from(10)
//                .transformValue {
//                  item =>
//                    item._2
//                }
//
//            result.materialize.value shouldBe (10 to 100).toList.map(_.toString)
//
//        }
//      }
//    }
//  }
//}
