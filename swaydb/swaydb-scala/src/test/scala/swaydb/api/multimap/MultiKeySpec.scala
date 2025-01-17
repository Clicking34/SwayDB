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
//import org.scalatest.matchers.should.Matchers._
//import org.scalatest.wordspec.AnyWordSpec
//import swaydb.core.CoreTestData._
//import swaydb.multimap.MultiKey
//import swaydb.serializers.Default._
//import swaydb.serializers.Serializer
//import swaydb.slice.Slice
//import swaydb.slice.order.KeyOrder
//
//import scala.collection.SortedSet
//import scala.util.{Random, Try}
//import swaydb.testkit.TestKit._
//
//class MultiKeySpec extends AnyWordSpec {
//
//  "mapKeySerializer" should {
//    def doAssert[T, K](key: MultiKey[T, K])(implicit keySerializer: Serializer[K],
//                                            tableSerializer: Serializer[T]) = {
//      val mapKeySerializer = MultiKey.serializer[T, K](keySerializer, tableSerializer)
//      val wrote = mapKeySerializer.write(key)
//      val read = mapKeySerializer.read(wrote)
//      read shouldBe key
//    }
//
//    "write & read empty keys" in {
//      doAssert[Int, Int](MultiKey.Start(0))
//      doAssert[Int, Int](MultiKey.KeysStart(0))
//      doAssert[Int, Int](MultiKey.Key(0, 100))
//      doAssert[Int, Int](MultiKey.KeysEnd(0))
//      doAssert[Int, Int](MultiKey.ChildrenStart(0))
//      doAssert[Int, Int](MultiKey.Child(0, 1000))
//      doAssert[Int, Int](MultiKey.ChildrenEnd(0))
//      doAssert[Int, Int](MultiKey.End(0))
//    }
//
//    "write & read MapKeys with Int key" in {
//      doAssert[Int, Int](MultiKey.Start(1))
//      doAssert[Int, Int](MultiKey.KeysStart(1))
//      doAssert[Int, Int](MultiKey.Key(1, 100))
//      doAssert[Int, Int](MultiKey.KeysEnd(1))
//      doAssert[Int, Int](MultiKey.ChildrenStart(1))
//      doAssert[Int, Int](MultiKey.Child(1, 1000))
//      doAssert[Int, Int](MultiKey.ChildrenEnd(1))
//      doAssert[Int, Int](MultiKey.End(1))
//    }
//
//
//    "write & read MapKeys with Int String" in {
//      doAssert[String, String](MultiKey.Start(1))
//      doAssert[String, String](MultiKey.KeysStart(1))
//      doAssert[String, String](MultiKey.Key(1, "one key"))
//      doAssert[String, String](MultiKey.KeysEnd(1))
//      doAssert[String, String](MultiKey.ChildrenStart(1))
//      doAssert[String, String](MultiKey.Child(1, "one sub map"))
//      doAssert[String, String](MultiKey.ChildrenEnd(1))
//      doAssert[String, String](MultiKey.End(1))
//    }
//
//    "write & read MapKeys with large single value" in {
//      doAssert[String, String](MultiKey.Start(Long.MaxValue))
//      doAssert[String, String](MultiKey.KeysStart(Long.MaxValue))
//      doAssert[String, String](MultiKey.KeysEnd(Long.MaxValue))
//      doAssert[String, String](MultiKey.Key(Long.MaxValue, randomCharacters(100000)))
//      doAssert[String, String](MultiKey.ChildrenStart(Long.MaxValue))
//      doAssert[String, String](MultiKey.Child(Long.MaxValue, randomCharacters(100000)))
//      doAssert[String, String](MultiKey.ChildrenEnd(Long.MaxValue))
//      doAssert[String, String](MultiKey.End(Long.MaxValue))
//    }
//
//    "write & read MapKeys with Double" in {
//      doAssert[Double, Double](MultiKey.Start(Long.MaxValue))
//      doAssert[Double, Double](MultiKey.KeysStart(Long.MaxValue))
//      doAssert[Double, Double](MultiKey.Key(Long.MaxValue, Double.MaxValue))
//      doAssert[Double, Double](MultiKey.KeysEnd(Long.MaxValue))
//      doAssert[Double, Double](MultiKey.ChildrenStart(Long.MaxValue))
//      doAssert[Double, Double](MultiKey.Child(Long.MaxValue, Double.MaxValue))
//      doAssert[Double, Double](MultiKey.ChildrenEnd(Long.MaxValue))
//      doAssert[Double, Double](MultiKey.End(Long.MaxValue))
//    }
//  }
//
//  "ordering" should {
//    "ordering MapKeys in the order of Start, Entry & End" in {
//      val order = KeyOrder(Ordering.by[Slice[Byte], Int](_.readInt())(Ordering.Int))
//      val mapKeySerializer = MultiKey.serializer[Int, Int](IntSerialiser, IntSerialiser)
//      implicit val mapKeyOrder = Ordering.by[MultiKey[Int, Int], Slice[Byte]](mapKeySerializer.write)(MultiKey.ordering(order))
//
//      val keys =
//        Seq(
//          MultiKey.Start(0),
//          MultiKey.ChildrenStart(0),
//          MultiKey.ChildrenEnd(0),
//          MultiKey.End(0),
//
//          MultiKey.Start(1),
//          MultiKey.ChildrenStart(1),
//          MultiKey.ChildrenEnd(1),
//          MultiKey.End(1),
//
//          MultiKey.Start(2),
//          MultiKey.KeysStart(2),
//          MultiKey.Key(2, 1),
//          MultiKey.KeysEnd(2),
//          MultiKey.ChildrenStart(2),
//          MultiKey.Child(2, 1000),
//          MultiKey.ChildrenEnd(2),
//          MultiKey.End(2),
//
//          MultiKey.Start(100),
//          MultiKey.KeysStart(100),
//          MultiKey.Key(100, 2),
//          MultiKey.Key(100, 3),
//          MultiKey.Key(100, 4),
//          MultiKey.Key(100, 5),
//          MultiKey.KeysEnd(100),
//          MultiKey.ChildrenStart(100),
//          MultiKey.Child(100, 1000),
//          MultiKey.Child(100, 2000),
//          MultiKey.Child(100, 3000),
//          MultiKey.ChildrenEnd(100),
//          MultiKey.End(100),
//
//          MultiKey.Start(200),
//          MultiKey.KeysStart(200),
//          MultiKey.Key(200, 2),
//          MultiKey.Key(200, 3),
//          MultiKey.KeysEnd(200),
//          MultiKey.ChildrenStart(200),
//          MultiKey.Child(200, 1000),
//          MultiKey.Child(200, 2000),
//          MultiKey.ChildrenEnd(200),
//          MultiKey.End(200)
//        )
//
//      //shuffle and create a list
//      val map = SortedSet[MultiKey[Int, Int]](Random.shuffle(keys): _*)(mapKeyOrder)
//
//      //key-values should
//      map.toList shouldBe keys
//    }
//
//    "ordering MapKeys in the order of Start, Entry & End when keys are large String" in {
//      val order = KeyOrder(Ordering.by[Slice[Byte], String](_.readString())(Ordering.String))
//      val mapKeySerializer = MultiKey.serializer[String, String](StringSerializer, StringSerializer)
//      implicit val mapKeyOrder = Ordering.by[MultiKey[String, String], Slice[Byte]](mapKeySerializer.write)(MultiKey.ordering(order))
//
//      val stringLength = 100000
//
//      val randomString1 = "a" + randomCharacters(stringLength)
//      val randomString2 = "b" + randomCharacters(stringLength)
//      val randomString3 = "c" + randomCharacters(stringLength)
//      val randomString4 = "d" + randomCharacters(stringLength)
//      val randomString5 = "e" + randomCharacters(stringLength)
//
//      val keys =
//        Seq(
//          MultiKey.Start(1),
//          MultiKey.ChildrenStart(1),
//          MultiKey.ChildrenEnd(1),
//          MultiKey.End(1),
//
//          MultiKey.Start(2),
//          MultiKey.KeysStart(2),
//          MultiKey.Key(2, randomString1),
//          MultiKey.Key(2, randomString2),
//          MultiKey.Key(2, randomString3),
//          MultiKey.KeysEnd(2),
//          MultiKey.ChildrenStart(2),
//          MultiKey.Child(2, randomString3),
//          MultiKey.Child(2, randomString4),
//          MultiKey.Child(2, randomString5),
//          MultiKey.ChildrenEnd(2),
//          MultiKey.End(2),
//
//          MultiKey.Start(3),
//          MultiKey.Key(3, randomString3),
//          MultiKey.Key(3, randomString4),
//          MultiKey.Key(3, randomString5),
//          MultiKey.End(3)
//        )
//
//      //shuffle and create a list
//      val map = SortedSet[MultiKey[String, String]](Random.shuffle(keys): _*)(mapKeyOrder)
//
//      //key-values should
//      map.toList shouldBe keys
//    }
//
//    "remove duplicate key-values" in {
//      val order = KeyOrder(Ordering.by[Slice[Byte], Int](_.readInt())(Ordering.Int))
//      val mapKeySerializer = MultiKey.serializer[Int, Int](IntSerialiser, IntSerialiser)
//      implicit val mapKeyOrder = Ordering.by[MultiKey[Int, Int], Slice[Byte]](mapKeySerializer.write)(MultiKey.ordering(order))
//
//      val keys: Seq[() => MultiKey[Int, Int]] =
//        Seq(
//          () => MultiKey.Start(0),
//          () => MultiKey.KeysStart(0),
//          () => MultiKey.KeysEnd(0),
//          () => MultiKey.End(0),
//          () => MultiKey.Start(0),
//          () => MultiKey.KeysStart(0),
//          () => MultiKey.KeysEnd(0),
//          () => MultiKey.End(0),
//
//          () => MultiKey.Start(2),
//          () => MultiKey.KeysStart(2),
//          () => MultiKey.Key(2, 2),
//          () => MultiKey.Key(2, 2),
//          () => MultiKey.KeysEnd(2),
//          () => MultiKey.ChildrenStart(2),
//          () => MultiKey.Child(2, 1000), //depending on the order of inserion for this test either
//          () => MultiKey.Child(2, 1000), //3 or 4 gets selected but in reality subMaps with same key will not have different subMapIds.
//          () => MultiKey.ChildrenEnd(2),
//          () => MultiKey.End(2),
//
//          () => MultiKey.Start(100),
//          () => MultiKey.Key(100, 4),
//          () => MultiKey.Key(100, 5),
//          () => MultiKey.End(100)
//        )
//
//      //shuffle and create a list
//      val map = SortedSet[MultiKey[Int, Int]](Random.shuffle(keys).map(_ ()): _*)(mapKeyOrder)
//
//      val expected1 =
//        Seq(
//          MultiKey.Start(0),
//          MultiKey.KeysStart(0),
//          MultiKey.KeysEnd(0),
//          MultiKey.End(0),
//
//          MultiKey.Start(2),
//          MultiKey.KeysStart(2),
//          MultiKey.Key(2, 2),
//          MultiKey.KeysEnd(2),
//          MultiKey.ChildrenStart(2),
//          MultiKey.Child(2, 1000),
//          MultiKey.ChildrenEnd(2),
//          MultiKey.End(2),
//
//          MultiKey.Start(100),
//          MultiKey.Key(100, 4),
//          MultiKey.Key(100, 5),
//          MultiKey.End(100)
//        )
//
//      val expected2 =
//        Seq(
//          MultiKey.Start(0),
//          MultiKey.KeysStart(0),
//          MultiKey.KeysEnd(0),
//          MultiKey.End(0),
//
//          MultiKey.Start(2),
//          MultiKey.KeysStart(2),
//          MultiKey.Key(2, 2),
//          MultiKey.KeysEnd(2),
//          MultiKey.ChildrenStart(2),
//          MultiKey.Child(2, 1000),
//          MultiKey.ChildrenEnd(2),
//          MultiKey.End(2),
//
//          MultiKey.Start(100),
//          MultiKey.Key(100, 4),
//          MultiKey.Key(100, 5),
//          MultiKey.End(100)
//        )
//
//      //key-values should
//      Try(map.toList shouldBe expected1) getOrElse {
//        map.toList shouldBe expected2
//      }
//    }
//  }
//}
