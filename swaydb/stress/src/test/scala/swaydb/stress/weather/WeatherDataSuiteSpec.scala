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
//package swaydb.stress.weather
//
//import swaydb.IO
//import swaydb.config.Atomic
//import swaydb.core.CoreTestSweeper
//import swaydb.core.CoreTestSweeper._
//import swaydb.core.file.CoreFileTestKit._
//import swaydb.serializers.Default._
//
//class Memory_NonAtomic_WeatherDataSpec extends WeatherDataSpec {
//  override def newDB()(implicit sweeper: CoreTestSweeper) =
//    swaydb.memory.Map[Int, WeatherData, Nothing, IO.ApiIO](atomic = Atomic.Off).get.sweep(_.delete().get)
//}
//
//class Memory_Atomic_WeatherDataSpec extends WeatherDataSpec {
//  override def newDB()(implicit sweeper: CoreTestSweeper) =
//    swaydb.memory.Map[Int, WeatherData, Nothing, IO.ApiIO](atomic = Atomic.On).get.sweep(_.delete().get)
//}
//
//class Memory_NonAtomic_MultiMap_WeatherDataSpec extends WeatherDataSpec {
//  override def newDB()(implicit sweeper: CoreTestSweeper) =
//    swaydb.memory.MultiMap[Int, Int, WeatherData, Nothing, IO.ApiIO](atomic = Atomic.Off).get.sweep(_.delete().get)
//}
//
//class Persistent_NonAtomic_WeatherDataSpec extends WeatherDataSpec {
//  override def newDB()(implicit sweeper: CoreTestSweeper) =
//    swaydb.persistent.Map[Int, WeatherData, Nothing, IO.ApiIO](
//      dir = genDirPath(),
//      //      acceleration = Accelerator.brake(),
//      //      atomic = Atomic.Off,
//      //      mmapLogs = MMAP.randomForMap(),
//      //      mmapAppendixLogs = MMAP.randomForMap(),
//      //      cacheKeyValueIds = randomBoolean(),
//      //      acceleration = Accelerator.brake(),
//      //      segmentConfig = swaydb.persistent.DefaultConfigs.segmentConfig(true).copy(deleteDelay = Duration.Zero),
//      //      memoryCache = swaydb.persistent.DefaultConfigs.memoryCache.copy(cacheCapacity = 10.mb)
//    ).get.sweep(_.delete().get)
//}
//
//class Persistent_Atomic_WeatherDataSpec extends WeatherDataSpec {
//  override def newDB()(implicit sweeper: CoreTestSweeper) =
//    swaydb.persistent.Map[Int, WeatherData, Nothing, IO.ApiIO](
//      dir = genDirPath(),
//      //      acceleration = Accelerator.brake(),
//      atomic = Atomic.On
//      //      mmapLogs = MMAP.randomForMap(),
//      //      mmapAppendixLogs = MMAP.randomForMap(),
//      //      cacheKeyValueIds = randomBoolean(),
//      //      acceleration = Accelerator.brake(),
//      //      segmentConfig = swaydb.persistent.DefaultConfigs.segmentConfig(true).copy(deleteDelay = Duration.Zero),
//      //      memoryCache = swaydb.persistent.DefaultConfigs.memoryCache.copy(cacheCapacity = 10.mb)
//    ).get.sweep(_.delete().get)
//}
//
//class Persistent_MultiMap_WeatherDataSpec extends WeatherDataSpec {
//  override def newDB()(implicit sweeper: CoreTestSweeper) =
//    swaydb.persistent.MultiMap[Int, Int, WeatherData, Nothing, IO.ApiIO](
//      dir = genDirPath(),
//      //      acceleration = Accelerator.brake(),
//      //      mmapLogs = MMAP.randomForMap(),
//      //      mmapAppendixLogs = MMAP.randomForMap(),
//      //      cacheKeyValueIds = randomBoolean(),
//      //      acceleration = Accelerator.brake(),
//      //      segmentConfig = swaydb.persistent.DefaultConfigs.segmentConfig(randomBoolean()).copyWithMmap(MMAP.randomForSegment()),
//      //      memoryCache = swaydb.persistent.DefaultConfigs.memoryCache.copy(cacheCapacity = 10.mb)
//    ).get.sweep(_.delete().get)
//}
//
//class Persistent_SetMap_WeatherDataSpec extends WeatherDataSpec {
//  override def newDB()(implicit sweeper: CoreTestSweeper) =
//    swaydb.persistent.SetMap[Int, WeatherData, IO.ApiIO](
//      dir = genDirPath(),
//      //      acceleration = Accelerator.brake(),
//      //      mmapLogs = MMAP.randomForMap(),
//      //      mmapAppendixLogs = MMAP.randomForMap(),
//      //      cacheKeyValueIds = randomBoolean(),
//      //      acceleration = Accelerator.brake(),
//      //      segmentConfig = swaydb.persistent.DefaultConfigs.segmentConfig(randomBoolean()).copyWithMmap(MMAP.randomForSegment()),
//      //      memoryCache = swaydb.persistent.DefaultConfigs.memoryCache.copy(cacheCapacity = 10.mb),
//    ).get.sweep(_.delete().get)
//}
//
//class Memory_SetMap_WeatherDataSpec extends WeatherDataSpec {
//  override def newDB()(implicit sweeper: CoreTestSweeper) =
//    swaydb.memory.SetMap[Int, WeatherData, IO.ApiIO]().get.sweep(_.delete().get)
//}
//
//class EventuallyPersistent_WeatherDataSpec extends WeatherDataSpec {
//  //  override def newDB()(implicit sweeper: CoreTestSweeper) = swaydb.eventually.persistent.Map[Int, WeatherData, Nothing, IO.ApiIO](genDirPath(), maxOpenSegments = 10, memoryCacheSize = 10.mb, maxMemoryLevelSize = 500.mb).get
//  override def newDB()(implicit sweeper: CoreTestSweeper) =
//    swaydb.eventually.persistent.Map[Int, WeatherData, Nothing, IO.ApiIO](
//      dir = genDirPath(),
//      //      acceleration = Accelerator.brake(),
//      //      cacheKeyValueIds = randomBoolean(),
//      //      mmapPersistentLevelAppendixLogs = MMAP.randomForMap(),
//      //      segmentConfig = swaydb.persistent.DefaultConfigs.segmentConfig(randomBoolean()).copyWithMmap(MMAP.randomForSegment())
//    ).get.sweep(_.delete().get)
//}
//
//class EventuallyPersistent_MultiMap_WeatherDataSpec extends WeatherDataSpec {
//  //  override def newDB()(implicit sweeper: CoreTestSweeper) = swaydb.eventually.persistent.Map[Int, WeatherData, Nothing, IO.ApiIO](genDirPath(), maxOpenSegments = 10, memoryCacheSize = 10.mb, maxMemoryLevelSize = 500.mb).get
//  override def newDB()(implicit sweeper: CoreTestSweeper) =
//    swaydb.eventually.persistent.MultiMap[Int, Int, WeatherData, Nothing, IO.ApiIO](
//      dir = genDirPath(),
//      //      acceleration = Accelerator.brake(),
//      //      cacheKeyValueIds = randomBoolean(),
//      //      mmapPersistentLevelAppendixLogs = MMAP.randomForMap(),
//      //      segmentConfig = swaydb.persistent.DefaultConfigs.segmentConfig(randomBoolean()).copyWithMmap(MMAP.randomForSegment())
//    ).get.sweep(_.delete().get)
//}
