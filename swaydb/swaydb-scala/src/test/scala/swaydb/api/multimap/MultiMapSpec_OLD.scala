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
//import swaydb.multimap.{MultiKey, MultiValue}
//import swaydb.serializers.Default._
//import swaydb.slice.Slice
//import swaydb.slice.order.KeyOrder
//import swaydb.testkit.RunThis._
//import swaydb.utils.StorageUnits._
//import swaydb.{Bag, Glass, MultiMap, Prepare}
//
//import scala.collection.mutable.ListBuffer
//import scala.concurrent.duration._
//import swaydb.testkit.TestKit._
//import swaydb.core.file.CoreFileTestKit._
//
//class MultiMapSpecOLD0 extends MultiMapSpec_OLD {
//  val keyValueCount: Int = 1000
//
//  override def newDB()(implicit sweeper: CoreTestSweeper): MultiMap[Int, Int, String, Nothing, Glass] =
//    swaydb.persistent.MultiMap[Int, Int, String, Nothing, Glass](dir = genDirPath()).sweep(_.delete())
//}
//
//class MultiMapSpecOLD1 extends MultiMapSpec_OLD {
//  val keyValueCount: Int = 1000
//
//  override def newDB()(implicit sweeper: CoreTestSweeper): MultiMap[Int, Int, String, Nothing, Glass] =
//    swaydb.persistent.MultiMap[Int, Int, String, Nothing, Glass](dir = genDirPath(), logSize = 1.byte).sweep(_.delete())
//}
//
//class MultiMapSpecOLD2 extends MultiMapSpec_OLD {
//  val keyValueCount: Int = 1000
//
//  override def newDB()(implicit sweeper: CoreTestSweeper): MultiMap[Int, Int, String, Nothing, Glass] =
//    swaydb.memory.MultiMap[Int, Int, String, Nothing, Glass]().sweep(_.delete())
//}
//
//class MultiMapSpecOLD3 extends MultiMapSpec_OLD {
//  val keyValueCount: Int = 1000
//
//  override def newDB()(implicit sweeper: CoreTestSweeper): MultiMap[Int, Int, String, Nothing, Glass] =
//    swaydb.memory.MultiMap[Int, Int, String, Nothing, Glass](logSize = 1.byte).sweep(_.delete())
//}
//
///**
// * OLD test-cases when [[MultiMap]] was called Extension. Keeping these test around
// * because cover some use-cases.
// */
//sealed trait MultiMapSpec_OLD extends TestBaseAPI {
//
//  val keyValueCount: Int
//
//  def newDB()(implicit sweeper: CoreTestSweeper): MultiMap[Int, Int, String, Nothing, Glass]
//
//  implicit val bag = Bag.glass
//
//  //  implicit val mapKeySerializer = MultiKey.serializer(IntSerializer)
//  implicit val keyOrder: KeyOrder[Slice[Byte]] = KeyOrder.default
//
//
//  "initialising" should {
//    "create an empty rootMap" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//
//          val rootMap = newDB()
//
//          rootMap.materialize.toList shouldBe empty
//
//          //assert
//          getInnerMap(rootMap).materialize.toList shouldBe
//            List(
//              (MultiKey.Start(MultiMap.rootMapId), MultiValue.None),
//              (MultiKey.KeysStart(MultiMap.rootMapId), MultiValue.None),
//              (MultiKey.KeysEnd(MultiMap.rootMapId), MultiValue.None),
//              (MultiKey.ChildrenStart(MultiMap.rootMapId), MultiValue.None),
//              (MultiKey.ChildrenEnd(MultiMap.rootMapId), MultiValue.None),
//              (MultiKey.End(MultiMap.rootMapId), MultiValue.None)
//            )
//      }
//    }
//
//    "create a non-empty rootMap" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//
//          val rootMap = newDB()
//          rootMap.put(1, "one")
//          rootMap.put(2, "two")
//
//          rootMap.get(1).value shouldBe "one"
//          rootMap.get(2).value shouldBe "two"
//
//          rootMap.materialize.toList should contain only((1, "one"), (2, "two"))
//
//          //assert
//          getInnerMap(rootMap).materialize.toList shouldBe
//            List(
//              (MultiKey.Start(MultiMap.rootMapId), MultiValue.None),
//              (MultiKey.KeysStart(MultiMap.rootMapId), MultiValue.None),
//              (MultiKey.Key(MultiMap.rootMapId, 1), MultiValue.Their("one")),
//              (MultiKey.Key(MultiMap.rootMapId, 2), MultiValue.Their("two")),
//              (MultiKey.KeysEnd(MultiMap.rootMapId), MultiValue.None),
//              (MultiKey.ChildrenStart(MultiMap.rootMapId), MultiValue.None),
//              (MultiKey.ChildrenEnd(MultiMap.rootMapId), MultiValue.None),
//              (MultiKey.End(MultiMap.rootMapId), MultiValue.None)
//            )
//      }
//    }
//
//    "create with childMap" in {
//      runThis(times = 10.times, log = true) {
//        CoreTestSweeper {
//          implicit sweeper =>
//
//            val rootMap = newDB()
//            rootMap.put(1, "one")
//            rootMap.put(2, "two")
//
//            rootMap.getChild(1) shouldBe empty
//
//            val childMap = rootMap.child(1)
//
//            rootMap.getChild(1) shouldBe defined
//
//            childMap.put(1, "childMap one")
//            childMap.put(2, "childMap two")
//
//            rootMap.materialize shouldBe ListBuffer((1, "one"), (2, "two"))
//            childMap.materialize shouldBe ListBuffer((1, "childMap one"), (2, "childMap two"))
//
//            val expectedRootId = MultiMap.rootMapId
//            val expectedChildId = MultiMap.rootMapId + 1
//
//            //assert
//            getInnerMap(rootMap).materialize.toList shouldBe
//              List(
//                (MultiKey.Start(expectedRootId), MultiValue.None),
//                (MultiKey.KeysStart(expectedRootId), MultiValue.None),
//                (MultiKey.Key(expectedRootId, 1), MultiValue.Their("one")),
//                (MultiKey.Key(expectedRootId, 2), MultiValue.Their("two")),
//                (MultiKey.KeysEnd(expectedRootId), MultiValue.None),
//                (MultiKey.ChildrenStart(expectedRootId), MultiValue.None),
//                (MultiKey.Child(expectedRootId, 1), MultiValue.MapId(expectedChildId)),
//                (MultiKey.ChildrenEnd(expectedRootId), MultiValue.None),
//                (MultiKey.End(expectedRootId), MultiValue.None),
//
//
//                //childMaps entries
//                (MultiKey.Start(expectedChildId), MultiValue.None),
//                (MultiKey.KeysStart(expectedChildId), MultiValue.None),
//                (MultiKey.Key(expectedChildId, 1), MultiValue.Their("childMap one")),
//                (MultiKey.Key(expectedChildId, 2), MultiValue.Their("childMap two")),
//                (MultiKey.KeysEnd(expectedChildId), MultiValue.None),
//                (MultiKey.ChildrenStart(expectedChildId), MultiValue.None),
//                (MultiKey.ChildrenEnd(expectedChildId), MultiValue.None),
//                (MultiKey.End(expectedChildId), MultiValue.None)
//              )
//        }
//      }
//    }
//  }
//
//  "childMap" should {
//    "remove all entries" in {
//      implicit val ec = TestExecutionContext.executionContext
//
//      runThis(10.times) {
//        CoreTestSweeper {
//          implicit sweeper =>
//
//            val rootMap = newDB()
//            rootMap.put(1, "one")
//            rootMap.put(2, "two")
//
//            val childMap = rootMap.child(1)
//
//            childMap.put(1, "childMap one")
//            childMap.put(2, "childMap two")
//
//            eitherOne(
//              left = {
//                rootMap.clearKeyValues()
//                childMap.clearKeyValues()
//              },
//              right = {
//                rootMap.remove(1, 2)
//                childMap.remove(1, 2)
//              }
//            )
//
//            childMap.materialize.toList shouldBe empty
//
//            childMap.put(3, "three")
//            childMap.materialize.toList shouldBe List((3, "three"))
//        }
//      }
//    }
//
//    "remove" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//
//          val rootMap = newDB()
//
//          val child1 = rootMap.child(1)
//          child1.put(1, "one")
//          child1.put(2, "two")
//
//          val child2 = rootMap.child(2)
//
//          val child1Get = rootMap.getChild(1).value
//          eitherOne(
//            child1Get.materialize.toList shouldBe ListBuffer((1, "one"), (2, "two")),
//            child1.materialize.toList shouldBe ListBuffer((1, "one"), (2, "two"))
//          )
//
//          rootMap.childrenKeys.materialize.toList shouldBe List(1, 2)
//          rootMap.removeChild(1)
//          rootMap.childrenKeys.materialize.toList shouldBe List(2)
//          rootMap.removeChild(2)
//          rootMap.childrenKeys.materialize.toList shouldBe empty
//      }
//    }
//
//
//    "expire key" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//
//          val rootMap = newDB()
//          rootMap.put(1, "one", 500.millisecond)
//          rootMap.put(2, "two")
//
//          val childMap = rootMap.child(1)
//
//          childMap.put(1, "childMap one", 500.millisecond)
//          childMap.put(2, "childMap two")
//
//          eventual {
//            rootMap.get(1) shouldBe empty
//            childMap.get(1) shouldBe empty
//          }
//
//        //assert
//        //      rootMap.baseMap().toList shouldBe
//        //        List(
//        //          (MultiKey.Start(MultiMap_Experimental.rootMapId), None),
//        //          (MultiKey.EntriesStart(MultiMap_Experimental.rootMapId), None),
//        //          //          (MultiKey.Entry(MultiMap_Experimental.rootMapId, 1), Some("one")),//expired
//        //          (MultiKey.Entry(MultiMap_Experimental.rootMapId, 2), Some("two")),
//        //          (MultiKey.EntriesEnd(MultiMap_Experimental.rootMapId), None),
//        //          (MultiKey.SubMapsStart(MultiMap_Experimental.rootMapId), None),
//        //          (MultiKey.SubMap(MultiMap_Experimental.rootMapId, 1), Some("sub map")),
//        //          (MultiKey.SubMapsEnd(MultiMap_Experimental.rootMapId), None),
//        //          (MultiKey.End(MultiMap_Experimental.rootMapId), None),
//        //
//        //          //childMaps entries
//        //          (MultiKey.Start(1), Some("sub map")),
//        //          (MultiKey.EntriesStart(1), None),
//        //          //          (MultiKey.Entry(1, 1), Some("childMap one")), //expired
//        //          (MultiKey.Entry(1, 2), Some("childMap two")),
//        //          (MultiKey.EntriesEnd(1), None),
//        //          (MultiKey.SubMapsStart(1), None),
//        //          (MultiKey.SubMapsEnd(1), None),
//        //          (MultiKey.End(1), None)
//        //        )
//      }
//    }
//
//    "expire range keys" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//
//          val rootMap = newDB()
//          rootMap.put(1, "one")
//          rootMap.put(2, "two")
//
//          val childMap = rootMap.child(1)
//
//          childMap.put(1, "childMap two")
//          childMap.put(2, "childMap two")
//          childMap.put(3, "childMap two")
//          childMap.put(4, "childMap two")
//
//          rootMap.expire(1, 2, 100.millisecond) //expire all key-values from rootMap
//          childMap.expire(2, 3, 100.millisecond) //expire some from childMap
//
//          eventual {
//            rootMap.get(1) shouldBe empty
//            rootMap.get(2) shouldBe empty
//            childMap.get(1).value shouldBe "childMap two"
//            childMap.get(2) shouldBe empty
//            childMap.get(3) shouldBe empty
//            childMap.get(4).value shouldBe "childMap two"
//          }
//
//        //assert
//        //      rootMap.baseMap().toList shouldBe
//        //        List(
//        //          (MultiKey.Start(MultiMap_Experimental.rootMapId), None),
//        //          (MultiKey.EntriesStart(MultiMap_Experimental.rootMapId), None),
//        //          (MultiKey.EntriesEnd(MultiMap_Experimental.rootMapId), None),
//        //          (MultiKey.SubMapsStart(MultiMap_Experimental.rootMapId), None),
//        //          (MultiKey.SubMap(MultiMap_Experimental.rootMapId, 1), Some("sub map")),
//        //          (MultiKey.SubMapsEnd(MultiMap_Experimental.rootMapId), None),
//        //          (MultiKey.End(MultiMap_Experimental.rootMapId), None),
//        //
//        //          //childMaps entries
//        //          (MultiKey.Start(1), Some("sub map")),
//        //          (MultiKey.EntriesStart(1), None),
//        //          (MultiKey.Entry(1, 1), Some("childMap two")),
//        //          (MultiKey.Entry(1, 4), Some("childMap two")),
//        //          (MultiKey.EntriesEnd(1), None),
//        //          (MultiKey.SubMapsStart(1), None),
//        //          (MultiKey.SubMapsEnd(1), None),
//        //          (MultiKey.End(1), None)
//        //        )
//      }
//    }
//
//    "update range keys" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//
//          val rootMap = newDB()
//          rootMap.put(1, "one")
//          rootMap.put(2, "two")
//
//          val childMap = rootMap.child(1)
//
//          childMap.put(1, "childMap two")
//          childMap.put(2, "childMap two")
//          childMap.put(3, "childMap two")
//          childMap.put(4, "childMap two")
//
//          eitherOne(
//            left = {
//              rootMap.update(1, 2, "updated") //update all key-values from rootMap
//              childMap.update(2, 3, "updated") //update some from childMap
//            },
//            right = {
//              rootMap.update(1, "updated")
//              rootMap.update(2, "updated")
//              childMap.update(2, "updated")
//              childMap.update(3, "updated")
//            }
//          )
//
//          rootMap.get(1).value shouldBe "updated"
//          rootMap.get(2).value shouldBe "updated"
//          childMap.get(2).value shouldBe "updated"
//          childMap.get(3).value shouldBe "updated"
//      }
//    }
//
//    "batch put" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//
//          val rootMap = newDB()
//          rootMap.commit(
//            Prepare.Put(1, "one"),
//            Prepare.Put(2, "two")
//          )
//
//          val childMap = rootMap.child(1)
//          childMap.commit(
//            Prepare.Put(1, "one one"),
//            Prepare.Put(2, "two two")
//          )
//
//          rootMap.get(1).value shouldBe "one"
//          rootMap.get(2).value shouldBe "two"
//          childMap.get(1).value shouldBe "one one"
//          childMap.get(2).value shouldBe "two two"
//      }
//    }
//
//    "batch update" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//
//          val rootMap = newDB()
//          rootMap.commit(
//            Prepare.Put(1, "one"),
//            Prepare.Put(2, "two")
//          )
//
//          rootMap.commit(
//            Prepare.Update(1, "one updated"),
//            Prepare.Update(2, "two updated")
//          )
//
//          val childMap = rootMap.child(1)
//          childMap.commit(
//            Prepare.Put(1, "one one"),
//            Prepare.Put(2, "two two")
//          )
//
//          childMap.commit(
//            Prepare.Update(1, "one one updated"),
//            Prepare.Update(2, "two two updated")
//          )
//
//          rootMap.get(1).value shouldBe "one updated"
//          rootMap.get(2).value shouldBe "two updated"
//          childMap.get(1).value shouldBe "one one updated"
//          childMap.get(2).value shouldBe "two two updated"
//      }
//    }
//
//    "batch expire" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//
//          val rootMap = newDB()
//          rootMap.commit(
//            Prepare.Put(1, "one"),
//            Prepare.Put(2, "two")
//          )
//
//          rootMap.commit(
//            Prepare.Expire(1, 100.millisecond),
//            Prepare.Expire(2, 100.millisecond)
//          )
//
//          val childMap = rootMap.child(1)
//          childMap.commit(
//            Prepare.Put(1, "one one"),
//            Prepare.Put(2, "two two")
//          )
//
//          childMap.commit(
//            Prepare.Expire(1, 100.millisecond),
//            Prepare.Expire(2, 100.millisecond)
//          )
//
//          eventual {
//            rootMap.materialize shouldBe empty
//            childMap.materialize shouldBe empty
//          }
//      }
//    }
//
//    "batchPut" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//
//          val rootMap = newDB()
//          rootMap.put((1, "one"), (2, "two"))
//
//          val childMap = rootMap.child(1)
//          childMap.put((1, "one one"), (2, "two two"))
//
//          rootMap.materialize shouldBe ListBuffer((1, "one"), (2, "two"))
//          childMap.materialize shouldBe ListBuffer((1, "one one"), (2, "two two"))
//      }
//    }
//
//    "batchUpdate" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//
//          val rootMap = newDB()
//          rootMap.put((1, "one"), (2, "two"))
//          rootMap.update((1, "one updated"), (2, "two updated"))
//
//          val childMap = rootMap.child(1)
//          childMap.put((1, "one one"), (2, "two two"))
//          childMap.update((1, "one one updated"), (2, "two two updated"))
//
//          rootMap.materialize shouldBe ListBuffer((1, "one updated"), (2, "two updated"))
//          childMap.materialize shouldBe ListBuffer((1, "one one updated"), (2, "two two updated"))
//      }
//    }
//
//    "batchRemove" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//
//          val rootMap = newDB()
//          rootMap.put((1, "one"), (2, "two"))
//          rootMap.remove(1, 2)
//
//          val childMap = rootMap.child(1)
//          childMap.put((1, "one one"), (2, "two two"))
//          childMap.remove(1, 2)
//
//          rootMap.materialize shouldBe empty
//          childMap.materialize shouldBe empty
//      }
//    }
//
//    "batchExpire" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//
//          val rootMap = newDB()
//          rootMap.put((1, "one"), (2, "two"))
//          rootMap.expire((1, 1.second.fromNow))
//
//          val childMap = rootMap.child(1)
//          childMap.put((1, "one one"), (2, "two two"))
//          childMap.expire((1, 1.second.fromNow), (2, 1.second.fromNow))
//
//          eventual {
//            rootMap.materialize.toList should contain only ((2, "two"))
//            childMap.materialize shouldBe empty
//          }
//      }
//    }
//
//    "get" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//
//          val rootMap = newDB()
//          rootMap.put((1, "one"), (2, "two"))
//
//          val childMap = rootMap.child(1)
//          childMap.put((1, "one one"), (2, "two two"))
//
//          rootMap.get(1).value shouldBe "one"
//          rootMap.get(2).value shouldBe "two"
//          childMap.get(1).value shouldBe "one one"
//          childMap.get(2).value shouldBe "two two"
//
//          rootMap.remove(1, 2)
//          childMap.remove(1, 2)
//
//          rootMap.get(1) shouldBe empty
//          rootMap.get(2) shouldBe empty
//          childMap.get(1) shouldBe empty
//          childMap.get(2) shouldBe empty
//      }
//    }
//
//    "value when sub map is removed" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//          val rootMap = newDB()
//          rootMap.put((1, "one"), (2, "two"))
//
//          val childMap = rootMap.child(1)
//          childMap.put((1, "one one"), (2, "two two"))
//
//          rootMap.get(1).value shouldBe "one"
//          rootMap.get(2).value shouldBe "two"
//          childMap.get(1).value shouldBe "one one"
//          childMap.get(2).value shouldBe "two two"
//
//          rootMap.remove(1, 2)
//          rootMap.removeChild(1)
//
//          rootMap.get(1) shouldBe empty
//          rootMap.get(2) shouldBe empty
//          childMap.get(1) shouldBe empty
//          childMap.get(2) shouldBe empty
//      }
//    }
//
//    "getKey" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//
//          val rootMap = newDB()
//          rootMap.put((1, "one"), (2, "two"))
//
//          val childMap = rootMap.child(1)
//          childMap.put((11, "one one"), (22, "two two"))
//
//          rootMap.getKey(1).value shouldBe 1
//          rootMap.getKey(2).value shouldBe 2
//          childMap.getKey(11).value shouldBe 11
//          childMap.getKey(22).value shouldBe 22
//
//          rootMap.remove(1, 2)
//          rootMap.removeChild(1)
//
//          rootMap.get(1) shouldBe empty
//          rootMap.get(2) shouldBe empty
//          childMap.get(11) shouldBe empty
//          childMap.get(22) shouldBe empty
//      }
//    }
//
//    "getKeyValue" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//
//          val rootMap = newDB()
//          rootMap.put((1, "one"), (2, "two"))
//
//          val childMap = rootMap.child(1)
//          childMap.put((11, "one one"), (22, "two two"))
//
//          rootMap.getKeyValue(1).value shouldBe(1, "one")
//          rootMap.getKeyValue(2).value shouldBe(2, "two")
//          childMap.getKeyValue(11).value shouldBe(11, "one one")
//          childMap.getKeyValue(22).value shouldBe(22, "two two")
//
//          rootMap.remove(1, 2)
//          rootMap.removeChild(1)
//
//          rootMap.getKeyValue(1) shouldBe empty
//          rootMap.getKeyValue(2) shouldBe empty
//          childMap.getKeyValue(11) shouldBe empty
//          childMap.getKeyValue(22) shouldBe empty
//      }
//    }
//
//    "keys" in {
//      CoreTestSweeper {
//        implicit sweeper =>
//
//          val rootMap = newDB()
//          rootMap.put((1, "one"), (2, "two"))
//
//          val childMap = rootMap.child(1)
//          childMap.put((11, "one one"), (22, "two two"))
//
//          rootMap.materialize.toList.map(_._1) should contain inOrderOnly(1, 2)
//          childMap.materialize.toList.map(_._1) should contain inOrderOnly(11, 22)
//      }
//    }
//  }
//
//
//  //  "Map" should {
//  //
//  //    "return entries ranges" in {
//  //      Map.entriesRangeKeys(Seq(1, 2, 3)) shouldBe ((MultiKey.MapEntriesStart(Seq(1, 2, 3)), MultiKey.MapEntriesEnd(Seq(1, 2, 3))))
//  //    }
//  //
//  //    "return empty childMap range keys for a empty SubMap" in {
//  //      val db = newDB()
//  //
//  //      val rootMap = db.children.init(1, "rootMap")
//  //      Map.childSubMapRanges(rootMap).get shouldBe empty
//  //
//  //      db.delete()
//  //    }
//  //
//  //    "return childMap that has only one child childMap" in {
//  //      val rootMap = newDB()
//  //
//  //      val firstMap = rootMap.children.init(1, "rootMap")
//  //      val secondMap = firstMap.children.init(2, "second map")
//  //
//  //      Map.childSubMapRanges(firstMap).get should contain only ((MultiKey.SubMap(Seq(1), 2), MultiKey.MapStart(Seq(1, 2)), MultiKey.MapEnd(Seq(1, 2))))
//  //      Map.childSubMapRanges(secondMap).get shouldBe empty
//  //
//  //      rootMap.delete()
//  //    }
//  //
//  //    "return childMaps of 3 nested maps" in {
//  //      val db = newDB()
//  //
//  //      val firstMap = db.children.init(1, "first")
//  //      val secondMap = firstMap.children.init(2, "second")
//  //      val thirdMap = secondMap.children.init(2, "third")
//  //
//  //      Map.childSubMapRanges(firstMap).get should contain inOrderOnly((MultiKey.SubMap(Seq(1), 2), MultiKey.MapStart(Seq(1, 2)), MultiKey.MapEnd(Seq(1, 2))), (MultiKey.SubMap(Seq(1, 2), 2), MultiKey.MapStart(Seq(1, 2, 2)), MultiKey.MapEnd(Seq(1, 2, 2))))
//  //      Map.childSubMapRanges(secondMap).get should contain only ((MultiKey.SubMap(Seq(1, 2), 2), MultiKey.MapStart(Seq(1, 2, 2)), MultiKey.MapEnd(Seq(1, 2, 2))))
//  //      Map.childSubMapRanges(thirdMap).get shouldBe empty
//  //
//  //      db.delete()
//  //    }
//  //
//  //    "returns multiple child childMap that also contains nested childMaps" in {
//  //      val db = newDB()
//  //
//  //      val firstMap = db.children.init(1, "firstMap")
//  //      val secondMap = firstMap.children.init(2, "childMap")
//  //
//  //      secondMap.children.init(2, "childMap")
//  //      secondMap.children.init(3, "childMap3")
//  //      val childMap4 = secondMap.children.init(4, "childMap4")
//  //      childMap4.children.init(44, "childMap44")
//  //      val childMap5 = secondMap.children.init(5, "childMap5")
//  //      val childMap55 = childMap5.children.init(55, "childMap55")
//  //      childMap55.children.init(5555, "childMap55")
//  //      childMap55.children.init(6666, "childMap55")
//  //      childMap5.children.init(555, "childMap555")
//  //
//  //      val mapHierarchy =
//  //        List(
//  //          (MultiKey.SubMap(Seq(1), 2), MultiKey.MapStart(Seq(1, 2)), MultiKey.MapEnd(Seq(1, 2))),
//  //          (MultiKey.SubMap(Seq(1, 2), 2), MultiKey.MapStart(Seq(1, 2, 2)), MultiKey.MapEnd(Seq(1, 2, 2))),
//  //          (MultiKey.SubMap(Seq(1, 2), 3), MultiKey.MapStart(Seq(1, 2, 3)), MultiKey.MapEnd(Seq(1, 2, 3))),
//  //          (MultiKey.SubMap(Seq(1, 2), 4), MultiKey.MapStart(Seq(1, 2, 4)), MultiKey.MapEnd(Seq(1, 2, 4))),
//  //          (MultiKey.SubMap(Seq(1, 2, 4), 44), MultiKey.MapStart(Seq(1, 2, 4, 44)), MultiKey.MapEnd(Seq(1, 2, 4, 44))),
//  //          (MultiKey.SubMap(Seq(1, 2), 5), MultiKey.MapStart(Seq(1, 2, 5)), MultiKey.MapEnd(Seq(1, 2, 5))),
//  //          (MultiKey.SubMap(Seq(1, 2, 5), 55), MultiKey.MapStart(Seq(1, 2, 5, 55)), MultiKey.MapEnd(Seq(1, 2, 5, 55))),
//  //          (MultiKey.SubMap(Seq(1, 2, 5, 55), 5555), MultiKey.MapStart(Seq(1, 2, 5, 55, 5555)), MultiKey.MapEnd(Seq(1, 2, 5, 55, 5555))),
//  //          (MultiKey.SubMap(Seq(1, 2, 5, 55), 6666), MultiKey.MapStart(Seq(1, 2, 5, 55, 6666)), MultiKey.MapEnd(Seq(1, 2, 5, 55, 6666))),
//  //          (MultiKey.SubMap(Seq(1, 2, 5), 555), MultiKey.MapStart(Seq(1, 2, 5, 555)), MultiKey.MapEnd(Seq(1, 2, 5, 555)))
//  //        )
//  //
//  //      Map.childSubMapRanges(firstMap).get shouldBe mapHierarchy
//  //      Map.childSubMapRanges(secondMap).get shouldBe mapHierarchy.drop(1)
//  //
//  //      db.delete()
//  //    }
//  //  }
//  //
//
//  "SubMap" when {
//    "children.init on a non existing map" should {
//      "create a new childMap" in {
//        CoreTestSweeper {
//          implicit sweeper =>
//
//            val root = newDB()
//
//            val first = root.child(1)
//            val second = first.child(2)
//            first.getChild(2) shouldBe defined
//            second.getChild(2) shouldBe empty
//        }
//      }
//    }
//
//    "children.init on a existing map" should {
//      "replace existing map" in {
//        CoreTestSweeper {
//          implicit sweeper =>
//
//            val root = newDB()
//
//            val first = root.child(1)
//            val second = first.child(2)
//            val secondAgain = first.replaceChild(2)
//
//            first.getChild(2) shouldBe defined
//        }
//      }
//
//      "replace existing map and all it's entries" in {
//        CoreTestSweeper {
//          implicit sweeper =>
//
//            val root = newDB()
//
//            val first = root.child(1)
//            val second = first.child(2)
//            //write entries to second map
//            second.put(1, "one")
//            second.put(2, "two")
//            second.put(3, "three")
//            //assert second map has these entries
//            second.materialize shouldBe List((1, "one"), (2, "two"), (3, "three"))
//
//            val secondAgain = first.replaceChild(2)
//
//            //map value value updated
//            first.getChild(2) shouldBe defined
//            //all the old entries are removed
//            second.materialize shouldBe empty
//        }
//      }
//
//      "replace existing map and all it's entries and also all existing maps childMap and all their entries" in {
//        CoreTestSweeper {
//          implicit sweeper =>
//            val root = newDB()
//
//            //MAP HIERARCHY
//            //first
//            //   second
//            //       third
//            //           fourth
//            val first = root.child(1)
//            val second = first.child(2)
//            //third map that is the child map of second map
//            val third = second.child(3)
//            val fourth = third.child(4)
//
//            first.put(1, "first one")
//            first.put(2, "first two")
//            first.put(3, "first three")
//
//            second.put(1, "second one")
//            second.put(2, "second two")
//            second.put(3, "second three")
//
//            third.put(1, "third one")
//            third.put(2, "third two")
//            third.put(3, "third three")
//
//            fourth.put(1, "fourth one")
//            fourth.put(2, "fourth two")
//            fourth.put(3, "fourth three")
//
//            /**
//             * Assert that the all maps' content is accurate
//             */
//            first.materialize shouldBe List((1, "first one"), (2, "first two"), (3, "first three"))
//            second.materialize shouldBe List((1, "second one"), (2, "second two"), (3, "second three"))
//            third.materialize shouldBe List((1, "third one"), (2, "third two"), (3, "third three"))
//            fourth.materialize shouldBe List((1, "fourth one"), (2, "fourth two"), (3, "fourth three"))
//
//            root.childrenKeys.materialize.toList should contain only 1
//            first.childrenKeys.materialize.toList should contain only 2
//            second.childrenKeys.materialize.toList should contain only 3
//            third.childrenKeys.materialize.toList should contain only 4
//            fourth.childrenKeys.materialize.toList shouldBe empty
//
//            //submit put on second map and assert that all it's contents are replaced.
//            first.replaceChild(2)
//            first.getChild(2).value.materialize.toList shouldBe empty
//            first.getChild(2).value.children.materialize.toList shouldBe empty
//
//            //map value value updated
//            first.getChild(2) shouldBe defined
//            //all the old entries are removed
//            second.materialize shouldBe empty
//            third.materialize shouldBe empty
//            fourth.materialize shouldBe empty
//
//            //second has no children anymore.
//            second.getChild(3) shouldBe empty
//            second.getChild(4) shouldBe empty
//
//            first.materialize shouldBe List((1, "first one"), (2, "first two"), (3, "first three"))
//        }
//      }
//    }
//
//    //    "clear" should {
//    //      "remove all key-values from a map" in {
//    //
//    //        val root = newDB()
//    //        val first = root.children.init(1, "first")
//    //        val second = first.children.init(2, "second")
//    //        second.put(1, "second one")
//    //        second.put(2, "second two")
//    //        second.put(3, "second three")
//    //        //third map that is the child map of second map
//    //        val third = second.children.init(3, "third")
//    //        third.put(1, "third one")
//    //        third.put(2, "third two")
//    //        third.put(3, "third three")
//    //
//    //        second.materialize should have size 3
//    //        second.clear()
//    //        second.materialize shouldBe empty
//    //
//    //        third.materialize should have size 3
//    //        second.maps.clear(3)
//    //        third.materialize shouldBe empty
//    //
//    //        root.delete()
//    //      }
//    //    }
//  }
//}
