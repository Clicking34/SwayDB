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
//package swaydb.core.level.seek
//
//import org.scalatest.matchers.should.Matchers._
//import org.scalatest.wordspec.AnyWordSpec
//import swaydb.core.CommonAssertions._
//import swaydb.core.CoreTestData._
//import swaydb.core.segment.data.Value
//import swaydb.core.{CoreTestData, TestTimer}
//import swaydb.serializers.Default._
//import swaydb.serializers._
//import swaydb.slice.order.{KeyOrder, TimeOrder}
//import swaydb.testkit.RunThis._
//import swaydb.testkit.TestKit._
//
//class LowerSpec extends AnyWordSpec {
//
//  implicit val keyOrder = KeyOrder.default
//  implicit val timeOrder = TimeOrder.long
//  implicit val functionStore = CoreTestData.functionStore
//  implicit val time = TestTimer.Empty
//
//  "lowerFromValue" in {
//    runThis(100.times) {
//      Lower.lowerFromValue(key = 1, fromKey = 2, fromValue = Value.FromValue.Null).toOptionPut shouldBe empty
//      Lower.lowerFromValue(key = 2, fromKey = 1, fromValue = Value.FromValue.Null).toOptionPut shouldBe empty
//
//      Lower.lowerFromValue(key = 2, fromKey = 1, fromValue = randomFromValueOption(addPut = false)).toOptionPut shouldBe empty
//      Lower.lowerFromValue(key = 1, fromKey = 2, fromValue = randomFromValueOption(addPut = false)).toOptionPut shouldBe empty
//
//      Lower.lowerFromValue(key = 2, fromKey = 1, fromValue = Value.put(genStringSliceOptional(), Some(expiredDeadline()))).toOptionPut shouldBe empty
//      Lower.lowerFromValue(key = 1, fromKey = 2, fromValue = Value.put(genStringSliceOptional(), Some(expiredDeadline()))).toOptionPut shouldBe empty
//
//      val put = Value.put(genStringSliceOptional(), randomDeadlineOption(false))
//      Lower.lowerFromValue(key = 2, fromKey = 1, fromValue = put) shouldBe put.toMemory(1)
//      Lower.lowerFromValue(key = 1, fromKey = 2, fromValue = put).toOptionPut shouldBe empty
//    }
//  }
//}
