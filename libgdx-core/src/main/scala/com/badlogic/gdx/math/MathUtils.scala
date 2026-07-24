package com.badlogic.gdx.math

object MathUtils {
  final val nanoToSec: scala.Float = 1 / 1.0E9f
  final val FLOAT_ROUNDING_ERROR: scala.Float = 1.0E-6f
  final val PI: scala.Float = java.lang.Math.PI.asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  final val PI2: scala.Float = MathUtils.PI * 2
  final val HALF_PI: scala.Float = MathUtils.PI / 2
  final val E: scala.Float = java.lang.Math.E.asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  private final val SIN_BITS: scala.Int = 14
  private final val SIN_MASK: scala.Int = ~((-1) << MathUtils.SIN_BITS)
  private final val SIN_COUNT: scala.Int = MathUtils.SIN_MASK + 1
  private final val radFull: scala.Float = MathUtils.PI2
  private final val degFull: scala.Float = 360
  private final val radToIndex: scala.Float = MathUtils.SIN_COUNT / MathUtils.radFull
  private final val degToIndex: scala.Float = MathUtils.SIN_COUNT / MathUtils.degFull
  final val radiansToDegrees: scala.Float = 180.0f / MathUtils.PI
  final val radDeg: scala.Float = MathUtils.radiansToDegrees
  final val degreesToRadians: scala.Float = MathUtils.PI / 180
  final val degRad: scala.Float = MathUtils.degreesToRadians
  var random$field: java.util.Random = new com.badlogic.gdx.math.RandomXS128()
  private final val BIG_ENOUGH_INT: scala.Int = 16 * 1024
  private final val BIG_ENOUGH_FLOOR: scala.Double = MathUtils.BIG_ENOUGH_INT
  private final val CEIL: scala.Double = 0.9999999
  private final val BIG_ENOUGH_CEIL: scala.Double = 16384.999999999996
  private final val BIG_ENOUGH_ROUND: scala.Double = MathUtils.BIG_ENOUGH_INT + 0.5f
  def sin(radians: scala.Float): scala.Float = {
    return com.badlogic.gdx.math.MathUtils.Sin.table((radians * MathUtils.radToIndex).asInstanceOf[scala.Int] & MathUtils.SIN_MASK)
  }
  def cos(radians: scala.Float): scala.Float = {
    return com.badlogic.gdx.math.MathUtils.Sin.table(((radians + MathUtils.HALF_PI) * MathUtils.radToIndex).asInstanceOf[scala.Int] & MathUtils.SIN_MASK)
  }
  def sinDeg(degrees: scala.Float): scala.Float = {
    return com.badlogic.gdx.math.MathUtils.Sin.table((degrees * MathUtils.degToIndex).asInstanceOf[scala.Int] & MathUtils.SIN_MASK)
  }
  def cosDeg(degrees: scala.Float): scala.Float = {
    return com.badlogic.gdx.math.MathUtils.Sin.table(((degrees + 90) * MathUtils.degToIndex).asInstanceOf[scala.Int] & MathUtils.SIN_MASK)
  }
  def tan(radians$arg: scala.Float): scala.Float = {
    var radians: scala.Float = radians$arg
    radians = radians / MathUtils.PI
    radians = radians + 0.5f
    radians = (radians - java.lang.Math.floor(radians)).asInstanceOf[scala.Float]
    radians = radians - 0.5f
    radians = radians * MathUtils.PI
    val x2: scala.Float = radians * radians
    val x4: scala.Float = x2 * x2
    return (radians * (((0.0010582011f * x4) - (0.11111111f * x2)) + 1.0f)) / (((0.015873017f * x4) - (0.44444445f * x2)) + 1.0f)
  }
  def tanDeg(degrees$arg: scala.Float): scala.Float = {
    var degrees: scala.Float = degrees$arg
    degrees = degrees * (1.0f / 180.0f)
    degrees = degrees + 0.5f
    degrees = (degrees - java.lang.Math.floor(degrees)).asInstanceOf[scala.Float]
    degrees = degrees - 0.5f
    degrees = degrees * MathUtils.PI
    val x2: scala.Float = degrees * degrees
    val x4: scala.Float = x2 * x2
    return (degrees * (((0.0010582011f * x4) - (0.11111111f * x2)) + 1.0f)) / (((0.015873017f * x4) - (0.44444445f * x2)) + 1.0f)
  }
  def atanUnchecked(i: scala.Double): scala.Float = {
    val n: scala.Double = java.lang.Math.abs(i)
    val c: scala.Double = (n - 1.0) / (n + 1.0)
    val c2: scala.Double = c * c
    val c3: scala.Double = c * c2
    val c5: scala.Double = c3 * c2
    val c7: scala.Double = c5 * c2
    val c9: scala.Double = c7 * c2
    val c11: scala.Double = c9 * c2
    return (java.lang.Math.signum(i) * ((java.lang.Math.PI * 0.25) + ((((((0.99997726 * c) - (0.33262347 * c3)) + (0.19354346 * c5)) - (0.11643287 * c7)) + (0.05265332 * c9)) - (0.0117212 * c11)))).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  }
  def atan2(y: scala.Float, x$arg: scala.Float): scala.Float = {
    var x: scala.Float = x$arg
    var n: scala.Float = y / x
    if (n != n) {
      n = if (y == x) 1.0f else -1.0f
    } else {
      if ((n - n) != (n - n)) {
        x = 0.0f
      } else ()
    }
    if (x > 0) {
      return MathUtils.atanUnchecked(n)
    } else {
      if (x < 0) {
        if (y >= 0) {
          return MathUtils.atanUnchecked(n) + MathUtils.PI
        } else ()
        return MathUtils.atanUnchecked(n) - MathUtils.PI
      } else {
        if (y > 0) {
          return x + MathUtils.HALF_PI
        } else {
          if (y < 0) {
            return x - MathUtils.HALF_PI
          } else ()
        }
      }
    }
    return x + y
  }
  def atanUncheckedDeg(i: scala.Double): scala.Double = {
    val n: scala.Double = java.lang.Math.abs(i)
    val c: scala.Double = (n - 1.0) / (n + 1.0)
    val c2: scala.Double = c * c
    val c3: scala.Double = c * c2
    val c5: scala.Double = c3 * c2
    val c7: scala.Double = c5 * c2
    val c9: scala.Double = c7 * c2
    val c11: scala.Double = c9 * c2
    return java.lang.Math.signum(i) * (45.0 + ((((((57.2944766070562 * c) - (19.05792099799635 * c3)) + (11.089223410359068 * c5)) - (6.6711120475953765 * c7)) + (3.016813013351768 * c9)) - (0.6715752908287405 * c11)))
  }
  def atan2Deg(y: scala.Float, x$arg: scala.Float): scala.Float = {
    var x: scala.Float = x$arg
    var n: scala.Float = y / x
    if (n != n) {
      n = if (y == x) 1.0f else -1.0f
    } else {
      if ((n - n) != (n - n)) {
        x = 0.0f
      } else ()
    }
    if (x > 0) {
      return MathUtils.atanUncheckedDeg(n).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    } else {
      if (x < 0) {
        if (y >= 0) {
          return (MathUtils.atanUncheckedDeg(n) + 180.0).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
        } else ()
        return (MathUtils.atanUncheckedDeg(n) - 180.0).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
      } else {
        if (y > 0) {
          return x + 90.0f
        } else {
          if (y < 0) {
            return x - 90.0f
          } else ()
        }
      }
    }
    return x + y
  }
  def atan2Deg360(y: scala.Float, x$arg: scala.Float): scala.Float = {
    var x: scala.Float = x$arg
    var n: scala.Float = y / x
    if (n != n) {
      n = if (y == x) 1.0f else -1.0f
    } else {
      if ((n - n) != (n - n)) {
        x = 0.0f
      } else ()
    }
    if (x > 0) {
      if (y >= 0) {
        return MathUtils.atanUncheckedDeg(n).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
      } else {
        return (MathUtils.atanUncheckedDeg(n) + 360.0).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
      }
    } else {
      if (x < 0) {
        return (MathUtils.atanUncheckedDeg(n) + 180.0).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
      } else {
        if (y > 0) {
          return x + 90.0f
        } else {
          if (y < 0) {
            return x + 270.0f
          } else ()
        }
      }
    }
    return x + y
  }
  def acos(a: scala.Float): scala.Float = {
    val a2: scala.Float = a * a
    val a3: scala.Float = a * a2
    if (a >= 0.0f) {
      return java.lang.Math.sqrt(1.0f - a).asInstanceOf[scala.Float] * (((1.5707288f - (0.2121144f * a)) + (0.074261f * a2)) - (0.0187293f * a3))
    } else ()
    return 3.1415927f - (java.lang.Math.sqrt(1.0f + a).asInstanceOf[scala.Float] * (((1.5707288f + (0.2121144f * a)) + (0.074261f * a2)) + (0.0187293f * a3)))
  }
  def asin(a: scala.Float): scala.Float = {
    val a2: scala.Float = a * a
    val a3: scala.Float = a * a2
    if (a >= 0.0f) {
      return 1.5707964f - (java.lang.Math.sqrt(1.0f - a).asInstanceOf[scala.Float] * (((1.5707288f - (0.2121144f * a)) + (0.074261f * a2)) - (0.0187293f * a3)))
    } else ()
    return (-1.5707964f) + (java.lang.Math.sqrt(1.0f + a).asInstanceOf[scala.Float] * (((1.5707288f + (0.2121144f * a)) + (0.074261f * a2)) + (0.0187293f * a3)))
  }
  def atan(i: scala.Float): scala.Float = {
    val n: scala.Double = java.lang.Math.min(java.lang.Math.abs(i), java.lang.Double.MAX_VALUE)
    val c: scala.Double = (n - 1.0) / (n + 1.0)
    val c2: scala.Double = c * c
    val c3: scala.Double = c * c2
    val c5: scala.Double = c3 * c2
    val c7: scala.Double = c5 * c2
    val c9: scala.Double = c7 * c2
    val c11: scala.Double = c9 * c2
    return java.lang.Math.signum(i) * ((java.lang.Math.PI * 0.25) + ((((((0.99997726 * c) - (0.33262347 * c3)) + (0.19354346 * c5)) - (0.11643287 * c7)) + (0.05265332 * c9)) - (0.0117212 * c11))).asInstanceOf[scala.Float]
  }
  def asinDeg(a: scala.Float): scala.Float = {
    val a2: scala.Float = a * a
    val a3: scala.Float = a * a2
    if (a >= 0.0f) {
      return 90.0f - (java.lang.Math.sqrt(1.0f - a).asInstanceOf[scala.Float] * (((89.99613f - (12.15326f * a)) + (4.254842f * a2)) - (1.0731099f * a3)))
    } else ()
    return (java.lang.Math.sqrt(1.0f + a).asInstanceOf[scala.Float] * (((89.99613f + (12.15326f * a)) + (4.254842f * a2)) + (1.0731099f * a3))) - 90.0f
  }
  def acosDeg(a: scala.Float): scala.Float = {
    val a2: scala.Float = a * a
    val a3: scala.Float = a * a2
    if (a >= 0.0f) {
      return java.lang.Math.sqrt(1.0f - a).asInstanceOf[scala.Float] * (((89.99613f - (12.153259f * a)) + (4.254842f * a2)) - (1.0731097f * a3))
    } else ()
    return 180.0f - (java.lang.Math.sqrt(1.0f + a).asInstanceOf[scala.Float] * (((89.99613f + (12.153259f * a)) + (4.254842f * a2)) + (1.0731097f * a3)))
  }
  def atanDeg(i: scala.Float): scala.Float = {
    val n: scala.Double = java.lang.Math.min(java.lang.Math.abs(i), java.lang.Double.MAX_VALUE)
    val c: scala.Double = (n - 1.0) / (n + 1.0)
    val c2: scala.Double = c * c
    val c3: scala.Double = c * c2
    val c5: scala.Double = c3 * c2
    val c7: scala.Double = c5 * c2
    val c9: scala.Double = c7 * c2
    val c11: scala.Double = c9 * c2
    return (java.lang.Math.signum(i) * (45.0 + ((((((57.2944766070562 * c) - (19.05792099799635 * c3)) + (11.089223410359068 * c5)) - (6.6711120475953765 * c7)) + (3.016813013351768 * c9)) - (0.6715752908287405 * c11)))).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  }
  def random(range: scala.Int): scala.Int = {
    return MathUtils.random$field.nextInt(range + 1)
  }
  def random(start: scala.Int, `end`: scala.Int): scala.Int = {
    return start + MathUtils.random$field.nextInt((`end` - start) + 1)
  }
  def random(range: scala.Long): scala.Long = {
    return MathUtils.random(0L, range)
  }
  def random(start$arg: scala.Long, end$arg: scala.Long): scala.Long = {
    var start: scala.Long = start$arg
    var `end`: scala.Long = end$arg
    val rand: scala.Long = MathUtils.random$field.nextLong()
    if (`end` < start) {
      val t: scala.Long = `end`
      `end` = start
      start = t
    } else ()
    val bound: scala.Long = (`end` - start) + 1L
    val randLow: scala.Long = rand & 4294967295L
    val boundLow: scala.Long = bound & 4294967295L
    val randHigh: scala.Long = rand >>> 32
    val boundHigh: scala.Long = bound >>> 32
    return ((start + ((randHigh * boundLow) >>> 32)) + ((randLow * boundHigh) >>> 32)) + (randHigh * boundHigh)
  }
  def randomBoolean(): scala.Boolean = {
    return MathUtils.random$field.nextBoolean()
  }
  def randomBoolean(chance: scala.Float): scala.Boolean = {
    return MathUtils.random() < chance
  }
  def random(): scala.Float = {
    return MathUtils.random$field.nextFloat()
  }
  def random(range: scala.Float): scala.Float = {
    return MathUtils.random$field.nextFloat() * range
  }
  def random(start: scala.Float, `end`: scala.Float): scala.Float = {
    return start + (MathUtils.random$field.nextFloat() * (`end` - start))
  }
  def randomSign(): scala.Int = {
    return 1 | (MathUtils.random$field.nextInt() >> 31)
  }
  def randomTriangular(): scala.Float = {
    return MathUtils.random$field.nextFloat() - MathUtils.random$field.nextFloat()
  }
  def randomTriangular(max: scala.Float): scala.Float = {
    return (MathUtils.random$field.nextFloat() - MathUtils.random$field.nextFloat()) * max
  }
  def randomTriangular(min: scala.Float, max: scala.Float): scala.Float = {
    return MathUtils.randomTriangular(min, max, (min + max) * 0.5f)
  }
  def randomTriangular(min: scala.Float, max: scala.Float, mode: scala.Float): scala.Float = {
    val u: scala.Float = MathUtils.random$field.nextFloat()
    val d: scala.Float = max - min
    if (u <= ((mode - min) / d)) {
      return min + java.lang.Math.sqrt((u * d) * (mode - min)).asInstanceOf[scala.Float]
    } else ()
    return max - java.lang.Math.sqrt(((1 - u) * d) * (max - mode)).asInstanceOf[scala.Float]
  }
  def nextPowerOfTwo(value$arg: scala.Int): scala.Int = {
    var value: scala.Int = value$arg
    if (value == 0) {
      return 1
    } else ()
    value = value - 1
    value = value | (value >> 1)
    value = value | (value >> 2)
    value = value | (value >> 4)
    value = value | (value >> 8)
    value = value | (value >> 16)
    return value + 1
  }
  def isPowerOfTwo(value: scala.Int): scala.Boolean = {
    return (value != 0) && ((value & (value - 1)) == 0)
  }
  def clamp(value: scala.Short, min: scala.Short, max: scala.Short): scala.Short = {
    if (value < min) {
      return min
    } else ()
    if (value > max) {
      return max
    } else ()
    return value
  }
  def clamp(value: scala.Int, min: scala.Int, max: scala.Int): scala.Int = {
    if (value < min) {
      return min
    } else ()
    if (value > max) {
      return max
    } else ()
    return value
  }
  def clamp(value: scala.Long, min: scala.Long, max: scala.Long): scala.Long = {
    if (value < min) {
      return min
    } else ()
    if (value > max) {
      return max
    } else ()
    return value
  }
  def clamp(value: scala.Float, min: scala.Float, max: scala.Float): scala.Float = {
    if (value < min) {
      return min
    } else ()
    if (value > max) {
      return max
    } else ()
    return value
  }
  def clamp(value: scala.Double, min: scala.Double, max: scala.Double): scala.Double = {
    if (value < min) {
      return min
    } else ()
    if (value > max) {
      return max
    } else ()
    return value
  }
  def lerp(fromValue: scala.Float, toValue: scala.Float, progress: scala.Float): scala.Float = {
    return fromValue + ((toValue - fromValue) * progress)
  }
  def norm(rangeStart: scala.Float, rangeEnd: scala.Float, value: scala.Float): scala.Float = {
    return (value - rangeStart) / (rangeEnd - rangeStart)
  }
  def map(inRangeStart: scala.Float, inRangeEnd: scala.Float, outRangeStart: scala.Float, outRangeEnd: scala.Float, value: scala.Float): scala.Float = {
    return outRangeStart + (((value - inRangeStart) * (outRangeEnd - outRangeStart)) / (inRangeEnd - inRangeStart))
  }
  def lerpAngle(fromRadians: scala.Float, toRadians: scala.Float, progress: scala.Float): scala.Float = {
    val delta: scala.Float = (((((toRadians - fromRadians) % MathUtils.PI2) + MathUtils.PI2) + MathUtils.PI) % MathUtils.PI2) - MathUtils.PI
    return (((fromRadians + (delta * progress)) % MathUtils.PI2) + MathUtils.PI2) % MathUtils.PI2
  }
  def lerpAngleDeg(fromDegrees: scala.Float, toDegrees: scala.Float, progress: scala.Float): scala.Float = {
    val delta: scala.Float = (((((toDegrees - fromDegrees) % 360.0f) + 360.0f) + 180.0f) % 360.0f) - 180.0f
    return (((fromDegrees + (delta * progress)) % 360.0f) + 360.0f) % 360.0f
  }
  def floor(value: scala.Float): scala.Int = {
    return (value + MathUtils.BIG_ENOUGH_FLOOR).asInstanceOf[scala.Int] - MathUtils.BIG_ENOUGH_INT
  }
  def floorPositive(value: scala.Float): scala.Int = {
    return value.asInstanceOf[scala.Int].asInstanceOf[scala.Int]
  }
  def ceil(value: scala.Float): scala.Int = {
    return MathUtils.BIG_ENOUGH_INT - (MathUtils.BIG_ENOUGH_FLOOR - value).asInstanceOf[scala.Int]
  }
  def ceilPositive(value: scala.Float): scala.Int = {
    return (value + MathUtils.CEIL).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
  }
  def round(value: scala.Float): scala.Int = {
    return (value + MathUtils.BIG_ENOUGH_ROUND).asInstanceOf[scala.Int] - MathUtils.BIG_ENOUGH_INT
  }
  def roundPositive(value: scala.Float): scala.Int = {
    return (value + 0.5f).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
  }
  def isZero(value: scala.Float): scala.Boolean = {
    return java.lang.Math.abs(value) <= MathUtils.FLOAT_ROUNDING_ERROR
  }
  def isZero(value: scala.Float, tolerance: scala.Float): scala.Boolean = {
    return java.lang.Math.abs(value) <= tolerance
  }
  def isEqual(a: scala.Float, b: scala.Float): scala.Boolean = {
    return java.lang.Math.abs(a - b) <= MathUtils.FLOAT_ROUNDING_ERROR
  }
  def isEqual(a: scala.Float, b: scala.Float, tolerance: scala.Float): scala.Boolean = {
    return java.lang.Math.abs(a - b) <= tolerance
  }
  def log(a: scala.Float, value: scala.Float): scala.Float = {
    return (java.lang.Math.log(value) / java.lang.Math.log(a)).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  }
  def log2(value: scala.Float): scala.Float = {
    return MathUtils.log(2, value)
  }
  object Sin {
    final val table: scala.Array[scala.Float] = new scala.Array[scala.Float](MathUtils.SIN_COUNT)
  }
}