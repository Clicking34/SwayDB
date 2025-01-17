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
//package swaydb.api.multimap
//
//import org.scalatest.OptionValues._
//import swaydb.api.TestBaseAPI
//import swaydb.core.CommonAssertions._
//import swaydb.core.CoreTestSweeper._
//import swaydb.core.{CoreTestSweeper, TestExecutionContext}
//import swaydb.serializers.Default._
//import swaydb.testkit.RunThis._
//import swaydb.utils.StorageUnits._
//import swaydb.{Bag, Glass, MultiMap}
//
//import scala.util.Random
//import swaydb.testkit.TestKit._
//import swaydb.core.file.CoreFileTestKit._
//
//class FromMultiMapSpec0 extends FromMultiMapSpec {
//  val keyValueCount: Int = 1000
//
//  override def newDB()(implicit sweeper: CoreTestSweeper): MultiMap[Int, Int, String, Nothing, Glass] =
//    swaydb.persistent.MultiMap[Int, Int, String, Nothing, Glass](dir = genDirPath()).sweep(_.delete())
//}
//
//class FromMultiMapSpec1 extends FromMultiMapSpec {
//  val keyValueCount: Int = 1000
//
//  override def newDB()(implicit sweeper: CoreTestSweeper): MultiMap[Int, Int, String, Nothing, Glass] =
//    swaydb.persistent.MultiMap[Int, Int, String, Nothing, Glass](dir = genDirPath(), logSize = 1.byte).sweep(_.delete())
//}
//
//class FromMultiMapSpec2 extends FromMultiMapSpec {
//  val keyValueCount: Int = 1000
//
//  override def newDB()(implicit sweeper: CoreTestSweeper): MultiMap[Int, Int, String, Nothing, Glass] =
//    swaydb.memory.MultiMap[Int, Int, String, Nothing, Glass]().sweep(_.delete())
//}
//
//class FromMultiMapSpec3 extends FromMultiMapSpec {
//  val keyValueCount: Int = 1000
//
//  override def newDB()(implicit sweeper: CoreTestSweeper): MultiMap[Int, Int, String, Nothing, Glass] =
//    swaydb.memory.MultiMap[Int, Int, String, Nothing, Glass](logSize = 1.byte).sweep(_.delete())
//}
//
//sealed trait FromMultiMapSpec extends TestBaseAPI {
//
//  val keyValueCount: Int
//
//  def newDB()(implicit sweeper: CoreTestSweeper): MultiMap[Int, Int, String, Nothing, Glass]
//
//  implicit val bag = Bag.glass
//
//  "From" should {
//
//    "return empty on an empty Map" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//
//          val root = newDB()
//
//          val child1 = root.child(1)
//          root.child(1) //for testing that initialising an existing map does not create a new map
//          val child2 = root.child(2)
//
//          Seq(root, child1, child2) foreach {
//            map =>
//              map.get(1) shouldBe empty
//              map.materialize.toList shouldBe empty
//              map.from(1).materialize.toList shouldBe empty
//              map.from(1).reverse.materialize.toList shouldBe empty
//          }
//
//          root.childrenKeys.materialize.toList should contain only(1, 2)
//          child1.childrenKeys.materialize.toList shouldBe empty
//      }
//    }
//
//    "if the map contains only 1 element" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//
//          val db = newDB()
//
//          val rootMap = db.child(1)
//          val firstMap = rootMap.child(2)
//
//          firstMap.put(1, "one")
//
//          firstMap
//            .from(2)
//            .materialize
//            .toList shouldBe empty
//
//          firstMap
//            .after(1)
//            .materialize
//            .toList shouldBe empty
//
//          firstMap
//            .from(1)
//            .materialize
//            .toList should contain only ((1, "one"))
//
//          firstMap
//            .fromOrBefore(2)
//            .materialize
//            .toList should contain only ((1, "one"))
//
//          firstMap
//            .fromOrBefore(1)
//            .materialize
//            .toList should contain only ((1, "one"))
//
//          firstMap
//            .after(0)
//            .materialize
//            .toList should contain only ((1, "one"))
//
//          firstMap
//            .fromOrAfter(0)
//            .materialize
//            .toList should contain only ((1, "one"))
//
//          firstMap
//            .materialize
//            .size shouldBe 1
//
//          firstMap.head.value shouldBe ((1, "one"))
//          firstMap.last.value shouldBe ((1, "one"))
//      }
//    }
//
//    "Sibling maps" in {
//      implicit val ec = TestExecutionContext.executionContext
//
//      CoreTestSweeper {
//        implicit sweeper =>
//
//          val db = newDB()
//
//          val rootMap = db.child(1)
//
//          val subMap1: Glass[MultiMap[Int, Int, String, Nothing, Glass]] = rootMap.child(2)
//          subMap1.put(1, "one")
//          subMap1.put(2, "two")
//
//          val subMap2 = rootMap.child(3)
//          subMap2.put(3, "three")
//          subMap2.put(4, "four")
//
//          runThisParallel(20.times) {
//            Seq(
//              () => subMap1.from(3).materialize shouldBe empty,
//              () => subMap1.after(2).materialize shouldBe empty,
//              () => subMap1.from(1).materialize.toList should contain inOrderOnly((1, "one"), (2, "two")),
//              () => subMap1.fromOrBefore(2).materialize.toList should contain only ((2, "two")),
//              () => subMap1.fromOrBefore(1).materialize.toList should contain inOrderOnly((1, "one"), (2, "two")),
//              () => subMap1.after(0).materialize.toList should contain inOrderOnly((1, "one"), (2, "two")),
//              () => subMap1.fromOrAfter(0).materialize.toList should contain inOrderOnly((1, "one"), (2, "two")),
//              () => subMap1.count shouldBe 2,
//              () => subMap1.head.value shouldBe ((1, "one")),
//              () => subMap1.last.value shouldBe ((2, "two")),
//
//              () => subMap2.from(5).materialize shouldBe empty,
//              () => subMap2.after(4).materialize shouldBe empty,
//              () => subMap2.from(3).materialize.toList should contain inOrderOnly((3, "three"), (4, "four")),
//              () => subMap2.fromOrBefore(5).materialize.toList should contain only ((4, "four")),
//              () => subMap2.fromOrBefore(3).materialize.toList should contain inOrderOnly((3, "three"), (4, "four")),
//              () => subMap2.after(0).materialize.toList should contain inOrderOnly((3, "three"), (4, "four")),
//              () => subMap2.fromOrAfter(1).materialize.toList should contain inOrderOnly((3, "three"), (4, "four")),
//              () => subMap2.count shouldBe 2,
//              () => subMap2.head.value shouldBe ((3, "three")),
//              () => subMap2.last.value shouldBe ((4, "four"))
//            ).runThisRandomly()
//          }
//      }
//    }
//
//    "nested maps" in {
//      implicit val ec = TestExecutionContext.executionContext
//
//      CoreTestSweeper {
//        implicit sweeper =>
//
//          val db = newDB()
//
//          val rootMap = db.child(1)
//
//          val subMap1 = rootMap.child(2)
//          subMap1.put(1, "one")
//          subMap1.put(2, "two")
//
//          val subMap2 = subMap1.child(3)
//          subMap2.put(3, "three")
//          subMap2.put(4, "four")
//
//          runThisParallel(20.times) {
//            Seq(
//              () => subMap1.from(4).materialize.toList shouldBe empty,
//              () => subMap1.after(3).materialize.toList shouldBe empty,
//              () => subMap1.from(1).materialize.toList should contain inOrderOnly((1, "one"), (2, "two")),
//              () => subMap1.childrenKeys.materialize.toList should contain only 3,
//              () => subMap1.fromOrBefore(2).materialize.toList should contain only ((2, "two")),
//
//              () => subMap1.fromOrBefore(1).materialize.toList should contain inOrderOnly((1, "one"), (2, "two")),
//              () => subMap1.after(0).materialize.toList should contain inOrderOnly((1, "one"), (2, "two")),
//              () => subMap1.fromOrAfter(0).materialize.toList should contain inOrderOnly((1, "one"), (2, "two")),
//              () => subMap1.count shouldBe 2,
//              () => subMap1.head.value shouldBe ((1, "one")),
//              () => subMap1.childrenKeys.last.value shouldBe 3,
//
//              () => subMap2.from(5).materialize.toList shouldBe empty,
//              () => subMap2.after(4).materialize.toList shouldBe empty,
//              () => subMap2.from(3).materialize.toList should contain inOrderOnly((3, "three"), (4, "four")),
//              () => subMap2.fromOrBefore(5).materialize.toList should contain only ((4, "four")),
//              () => subMap2.fromOrBefore(3).materialize.toList should contain inOrderOnly((3, "three"), (4, "four")),
//              () => subMap2.after(0).materialize.toList should contain inOrderOnly((3, "three"), (4, "four")),
//              () => subMap2.fromOrAfter(1).materialize.toList should contain inOrderOnly((3, "three"), (4, "four")),
//              () => subMap2.count shouldBe 2,
//              () => subMap2.head.value shouldBe ((3, "three")),
//              () => subMap2.last.value shouldBe ((4, "four"))
//            ).runThisRandomly()
//          }
//      }
//    }
//
//    "if the map contains multiple non empty subMap" in {
//      implicit val ec = TestExecutionContext.executionContext
//
//      runThis(100.times, log = true) {
//        CoreTestSweeper {
//          implicit sweeper =>
//
//            val root = newDB()
//
//            //map hierarchy
//            //rootMap
//            //   |_____ (1, "one")
//            //          (2, "two")
//            //          (3, "three")
//            //          (4, "four")
//            //              maps ---> (2, "sub map")
//            //                |              |___________ (11, "one one")
//            //                |                           (22, "two two")
//            //                |                           (33, "three three")
//            //                |                           (44, "four four")
//            //                |-----> (3, "sub map")
//            //                              |___________ (111, "one one one")
//            //                                           (222, "two two two")
//            //                                           (333, "three three three")
//            //                                           (444, "four four four")
//            val rootMap = root.child(1)
//            val childMap1 = rootMap.child(2)
//            val childMap2 = rootMap.child(3)
//
//            def doInserts(skipRandomly: Boolean) = {
//              //insert entries to rootMap
//              def skip = skipRandomly && Random.nextBoolean()
//
//              if (!skip)
//                Seq(
//                  () => rootMap.put(1, "one"),
//                  () => rootMap.put(2, "two"),
//                  () => rootMap.put(3, "three"),
//                  () => rootMap.put(4, "four")
//                ).runThisRandomly()
//
//              if (!skip)
//                Seq(
//                  () => childMap1.put(11, "one one"),
//                  () => childMap1.put(22, "two two"),
//                  () => childMap1.put(33, "three three"),
//                  () => childMap1.put(44, "four four")
//                ).runThisRandomly()
//
//              if (!skip)
//                Seq(
//                  () => childMap2.put(111, "one one one"),
//                  () => childMap2.put(222, "two two two"),
//                  () => childMap2.put(333, "three three three"),
//                  () => childMap2.put(444, "four four four")
//                ).runThisRandomly()
//            }
//
//            //perform initial write.
//            doInserts(skipRandomly = false)
//
//            //random read in random order
//            runThisParallel(100.times) {
//              Seq(
//                //randomly also perform insert which would just create duplicate data.
//                () => eitherOne(doInserts(skipRandomly = true), (), ()),
//
//                () => rootMap.childrenKeys.materialize.toList should contain only(2, 3),
//                () => rootMap.from(1).materialize.toList shouldBe List((1, "one"), (2, "two"), (3, "three"), (4, "four")),
//                //reverse from the map.
//                () => rootMap.before(2).reverse.map { case (key, value) => (key, value) }.materialize shouldBe List((1, "one")),
//                () => rootMap.before(3).reverse.map { case (key, value) => (key, value) }.materialize shouldBe List((2, "two"), (1, "one")),
//                () => rootMap.before(4).reverse.map { case (key, value) => (key, value) }.materialize shouldBe List((3, "three"), (2, "two"), (1, "one")),
//                () => rootMap.before(5).reverse.map { case (key, value) => (key, value) }.materialize shouldBe List((4, "four"), (3, "three"), (2, "two"), (1, "one")),
//                () => rootMap.from(3).reverse.map { case (key, value) => (key, value) }.materialize shouldBe List((3, "three"), (2, "two"), (1, "one")),
//
//                //forward from entry
//                () => rootMap.from(1).materialize shouldBe List((1, "one"), (2, "two"), (3, "three"), (4, "four")),
//                () => rootMap.fromOrAfter(1).materialize shouldBe List((1, "one"), (2, "two"), (3, "three"), (4, "four")),
//                () => rootMap.fromOrBefore(1).materialize shouldBe List((1, "one"), (2, "two"), (3, "three"), (4, "four")),
//                () => rootMap.after(2).materialize shouldBe List((3, "three"), (4, "four")),
//
//                () => childMap1.materialize shouldBe List((11, "one one"), (22, "two two"), (33, "three three"), (44, "four four")),
//                () => childMap1.from(11).materialize shouldBe List((11, "one one"), (22, "two two"), (33, "three three"), (44, "four four")),
//                () => childMap1.from(22).materialize shouldBe List((22, "two two"), (33, "three three"), (44, "four four")),
//                () => childMap1.from(33).materialize shouldBe List((33, "three three"), (44, "four four")),
//                () => childMap1.from(44).materialize shouldBe List((44, "four four")),
//
//                () => childMap1.from(11).reverse.map { case (key, value) => (key, value) }.materialize shouldBe List((11, "one one")),
//                () => childMap1.from(22).reverse.map { case (key, value) => (key, value) }.materialize shouldBe List((22, "two two"), (11, "one one")),
//                () => childMap1.from(33).reverse.map { case (key, value) => (key, value) }.materialize shouldBe List((33, "three three"), (22, "two two"), (11, "one one")),
//                () => childMap1.from(44).reverse.map { case (key, value) => (key, value) }.materialize shouldBe List((44, "four four"), (33, "three three"), (22, "two two"), (11, "one one")),
//
//                () => childMap1.children.materialize shouldBe empty
//              ).runThisRandomly()
//            }
//        }
//      }
//    }
//  }
//}
