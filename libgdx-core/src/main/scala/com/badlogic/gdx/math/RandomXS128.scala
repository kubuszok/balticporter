package com.badlogic.gdx.math

class RandomXS128 extends java.util.Random {
  private var seed0: scala.Long = 0L
  private var seed1: scala.Long = 0L
  def this(seed: scala.Long) = {
    this()
    this.setSeed(seed)
  }
  def this(seed0: scala.Long, seed1: scala.Long) = {
    this()
    this.setState(seed0, seed1)
  }
  this.setSeed(new java.util.Random().nextLong())
  @java.lang.Override
  override def nextLong(): scala.Long = {
    var s1: scala.Long = this.seed0
    val s0: scala.Long = this.seed1
    this.seed0 = s0
    s1 = s1 ^ (s1 << 23)
    return {
      this.seed1 = ((s1 ^ s0) ^ (s1 >>> 17)) ^ (s0 >>> 26)
      this.seed1
    } + s0
  }
  @java.lang.Override
  override final def next(bits: scala.Int): scala.Int = {
    return (this.nextLong() & ((1L << bits) - 1)).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
  }
  @java.lang.Override
  override def nextInt(): scala.Int = {
    return this.nextLong().asInstanceOf[scala.Int].asInstanceOf[scala.Int]
  }
  @java.lang.Override
  override def nextInt(n: scala.Int): scala.Int = {
    return this.nextLong(n).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
  }
  override def nextLong(n: scala.Long): scala.Long = {
    {
      if (n <= 0) {
        throw new java.lang.IllegalArgumentException("n must be positive")
      } else ();
      { ; while (true) { {
        val bits: scala.Long = this.nextLong() >>> 1
        val value: scala.Long = bits % n
        if (((bits - value) + (n - 1)) >= 0) {
          return value
        } else ()
      };  } }
    }
    throw new java.lang.RuntimeException("unreachable")
  }
  @java.lang.Override
  override def nextDouble(): scala.Double = {
    return (this.nextLong() >>> 11) * RandomXS128.NORM_DOUBLE
  }
  @java.lang.Override
  override def nextFloat(): scala.Float = {
    return ((this.nextLong() >>> 40) * RandomXS128.NORM_FLOAT).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  }
  @java.lang.Override
  override def nextBoolean(): scala.Boolean = {
    return (this.nextLong() & 1) != 0
  }
  @java.lang.Override
  override def nextBytes(bytes: scala.Array[scala.Byte]): scala.Unit = {
    var n: scala.Int = 0
    var i: scala.Int = bytes.length
    while (i != 0) {
      n = if (i < 8) i else 8;
      { var bits: scala.Long = this.nextLong(); while ({ n -= 1; n } != 0) { {
        bytes({ i -= 1; i }) = bits.asInstanceOf[scala.Byte].asInstanceOf[scala.Byte]
      }; bits = bits >> 8 } }
    }
  }
  @java.lang.Override
  override def setSeed(seed: scala.Long): scala.Unit = {
    val seed0: scala.Long = RandomXS128.murmurHash3(if (seed == 0) java.lang.Long.MIN_VALUE else seed)
    this.setState(seed0, RandomXS128.murmurHash3(seed0))
  }
  def setState(seed0: scala.Long, seed1: scala.Long): scala.Unit = {
    this.seed0 = seed0
    this.seed1 = seed1
  }
  def getState(seed: scala.Int): scala.Long = {
    return if (seed == 0) this.seed0 else this.seed1
  }
}
object RandomXS128 {
  private final val NORM_DOUBLE: scala.Double = 1.0 / (1L << 53)
  private final val NORM_FLOAT: scala.Double = 1.0 / (1L << 24)
  private final def murmurHash3(x$arg: scala.Long): scala.Long = {
    var x: scala.Long = x$arg
    x = x ^ (x >>> 33)
    x = x * -49064778989728563L
    x = x ^ (x >>> 33)
    x = x * -4265267296055464877L
    x = x ^ (x >>> 33)
    return x
  }
}