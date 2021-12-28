package swaydb.config

import swaydb.config.compaction.PushStrategy
import swaydb.config.GenForceSave
import swaydb.testkit.TestKit.{eitherOne, randomBoolean, randomIntMax}
import swaydb.utils.OperatingSystem

import scala.util.Random

object CoreConfigTestKit {

  def randomSegmentRefCacheLife(): SegmentRefCacheLife =
    if (randomBoolean())
      SegmentRefCacheLife.Permanent
    else
      SegmentRefCacheLife.Temporary

  def randomPrefixCompressionInterval(): PrefixCompression.Interval =
    eitherOne(
      PrefixCompression.Interval.ResetCompressionAt(randomIntMax(100)),
      PrefixCompression.Interval.ResetCompressionAt(randomIntMax()),
      PrefixCompression.Interval.CompressAt(randomIntMax(100)),
      PrefixCompression.Interval.CompressAt(randomIntMax())
    )

  def randomPushStrategy(): PushStrategy =
    if (randomBoolean())
      PushStrategy.Immediately
    else
      PushStrategy.OnOverflow


  implicit class AtomicImplicits(atomic: Atomic.type) {

    def all: Seq[Atomic] =
      Seq(
        Atomic.On,
        Atomic.Off
      )

    def random: Atomic =
      if (randomBoolean())
        Atomic.On
      else
        Atomic.Off
  }

  implicit class OptimiseWritesImplicits(optimise: OptimiseWrites.type) {

    def randomAll: Seq[OptimiseWrites] =
      Seq(
        OptimiseWrites.RandomOrder,
        OptimiseWrites.SequentialOrder(initialSkipListLength = randomIntMax(100))
      )

    def random: OptimiseWrites =
      if (randomBoolean())
        OptimiseWrites.RandomOrder
      else
        OptimiseWrites.SequentialOrder(
          initialSkipListLength = randomIntMax(100)
        )
  }


  implicit class MMAPImplicits(mmap: MMAP.type) {
    def randomForSegment(): MMAP.Segment =
      if (Random.nextBoolean())
        MMAP.On(OperatingSystem.isWindows(), GenForceSave.mmap())
      else if (Random.nextBoolean())
        MMAP.ReadOnly(OperatingSystem.isWindows())
      else
        MMAP.Off(GenForceSave.standard())

    def randomForLog(): MMAP.Log =
      if (Random.nextBoolean())
        MMAP.On(OperatingSystem.isWindows(), GenForceSave.mmap())
      else
        MMAP.Off(GenForceSave.standard())
  }

}
