package com.badlogic.gdx.math

class Vector4 extends java.io.Serializable with com.badlogic.gdx.math.Vector[Vector4] {
  var x: scala.Float = 0.0f
  var y: scala.Float = 0.0f
  var z: scala.Float = 0.0f
  var w: scala.Float = 0.0f
  def this(x: scala.Float, y: scala.Float, z: scala.Float, w: scala.Float) = {
    this()
    this.set(x, y, z, w)
  }
  def this(vector: Vector4) = {
    this()
    this.set(vector.x, vector.y, vector.z, vector.w)
  }
  def this(values: scala.Array[scala.Float]) = {
    this()
    this.set(values(0), values(1), values(2), values(3))
  }
  def this(vector: com.badlogic.gdx.math.Vector2, z: scala.Float, w: scala.Float) = {
    this()
    this.set(vector.x, vector.y, z, w)
  }
  def this(vector: com.badlogic.gdx.math.Vector3, w: scala.Float) = {
    this()
    this.set(vector.x, vector.y, vector.z, w)
  }
  def set(x: scala.Float, y: scala.Float, z: scala.Float, w: scala.Float): Vector4 = {
    this.x = x
    this.y = y
    this.z = z
    this.w = w
    return this
  }
  @java.lang.Override
  override def set(vector: Vector4): Vector4 = {
    return this.set(vector.x, vector.y, vector.z, vector.w)
  }
  def set(values: scala.Array[scala.Float]): Vector4 = {
    return this.set(values(0), values(1), values(2), values(3))
  }
  def set(vector: com.badlogic.gdx.math.Vector2, z: scala.Float, w: scala.Float): Vector4 = {
    return this.set(vector.x, vector.y, z, w)
  }
  def set(vector: com.badlogic.gdx.math.Vector3, w: scala.Float): Vector4 = {
    return this.set(vector.x, vector.y, vector.z, w)
  }
  @java.lang.Override
  override def setToRandomDirection(): Vector4 = {
    var v1: scala.Float = 0.0f
    var v2: scala.Float = 0.0f
    var s: scala.Float = 0.0f
    var multiplier: scala.Float = 0.0f
    while ({ {
      v1 = (com.badlogic.gdx.math.MathUtils.random() - 0.5f) * 2
      v2 = (com.badlogic.gdx.math.MathUtils.random() - 0.5f) * 2
      s = (v1 * v1) + (v2 * v2)
    }; (s >= 1) || (s == 0) }) ()
    multiplier = java.lang.Math.sqrt(((-2) * java.lang.Math.log(s)) / s).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    this.x = v1 * multiplier
    this.y = v2 * multiplier
    while ({ {
      v1 = (com.badlogic.gdx.math.MathUtils.random() - 0.5f) * 2
      v2 = (com.badlogic.gdx.math.MathUtils.random() - 0.5f) * 2
      s = (v1 * v1) + (v2 * v2)
    }; (s >= 1) || (s == 0) }) ()
    multiplier = java.lang.Math.sqrt(((-2) * java.lang.Math.log(s)) / s).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    this.z = v1 * multiplier
    this.w = v2 * multiplier
    return this.nor()
  }
  @java.lang.Override
  override def cpy(): Vector4 = {
    return new Vector4(this)
  }
  @java.lang.Override
  override def add(vector: Vector4): Vector4 = {
    return this.add(vector.x, vector.y, vector.z, vector.w)
  }
  def add(x: scala.Float, y: scala.Float, z: scala.Float, w: scala.Float): Vector4 = {
    return this.set(this.x + x, this.y + y, this.z + z, this.w + w)
  }
  def add(values: scala.Float): Vector4 = {
    return this.set(this.x + values, this.y + values, this.z + values, this.w + values)
  }
  @java.lang.Override
  override def sub(a_vec: Vector4): Vector4 = {
    return this.sub(a_vec.x, a_vec.y, a_vec.z, a_vec.w)
  }
  def sub(x: scala.Float, y: scala.Float, z: scala.Float, w: scala.Float): Vector4 = {
    return this.set(this.x - x, this.y - y, this.z - z, this.w - w)
  }
  def sub(value: scala.Float): Vector4 = {
    return this.set(this.x - value, this.y - value, this.z - value, this.w - value)
  }
  @java.lang.Override
  override def scl(scalar: scala.Float): Vector4 = {
    return this.set(this.x * scalar, this.y * scalar, this.z * scalar, this.w * scalar)
  }
  @java.lang.Override
  override def scl(other: Vector4): Vector4 = {
    return this.set(this.x * other.x, this.y * other.y, this.z * other.z, this.w * other.w)
  }
  def scl(vx: scala.Float, vy: scala.Float, vz: scala.Float, vw: scala.Float): Vector4 = {
    return this.set(this.x * vx, this.y * vy, this.z * vz, this.w * vw)
  }
  @java.lang.Override
  override def mulAdd(vec: Vector4, scalar: scala.Float): Vector4 = {
    this.x = this.x + (vec.x * scalar)
    this.y = this.y + (vec.y * scalar)
    this.z = this.z + (vec.z * scalar)
    this.w = this.w + (vec.w * scalar)
    return this
  }
  @java.lang.Override
  override def mulAdd(vec: Vector4, mulVec: Vector4): Vector4 = {
    this.x = this.x + (vec.x * mulVec.x)
    this.y = this.y + (vec.y * mulVec.y)
    this.z = this.z + (vec.z * mulVec.z)
    this.w = this.w + (vec.w * mulVec.w)
    return this
  }
  @java.lang.Override
  override def len(): scala.Float = {
    return java.lang.Math.sqrt((((this.x * this.x) + (this.y * this.y)) + (this.z * this.z)) + (this.w * this.w)).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  }
  @java.lang.Override
  override def len2(): scala.Float = {
    return (((this.x * this.x) + (this.y * this.y)) + (this.z * this.z)) + (this.w * this.w)
  }
  def idt(vector: Vector4): scala.Boolean = {
    return (((this.x == vector.x) && (this.y == vector.y)) && (this.z == vector.z)) && (this.w == vector.w)
  }
  @java.lang.Override
  override def dst(vector: Vector4): scala.Float = {
    val a: scala.Float = vector.x - this.x
    val b: scala.Float = vector.y - this.y
    val c: scala.Float = vector.z - this.z
    val d: scala.Float = vector.w - this.w
    return java.lang.Math.sqrt((((a * a) + (b * b)) + (c * c)) + (d * d)).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  }
  def dst(x: scala.Float, y: scala.Float, z: scala.Float, w: scala.Float): scala.Float = {
    val a: scala.Float = x - this.x
    val b: scala.Float = y - this.y
    val c: scala.Float = z - this.z
    val d: scala.Float = w - this.w
    return java.lang.Math.sqrt((((a * a) + (b * b)) + (c * c)) + (d * d)).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  }
  @java.lang.Override
  override def dst2(point: Vector4): scala.Float = {
    val a: scala.Float = point.x - this.x
    val b: scala.Float = point.y - this.y
    val c: scala.Float = point.z - this.z
    val d: scala.Float = point.w - this.w
    return (((a * a) + (b * b)) + (c * c)) + (d * d)
  }
  def dst2(x: scala.Float, y: scala.Float, z: scala.Float, w: scala.Float): scala.Float = {
    val a: scala.Float = x - this.x
    val b: scala.Float = y - this.y
    val c: scala.Float = z - this.z
    val d: scala.Float = w - this.w
    return (((a * a) + (b * b)) + (c * c)) + (d * d)
  }
  @java.lang.Override
  override def nor(): Vector4 = {
    val len2: scala.Float = this.len2()
    if ((len2 == 0.0f) || (len2 == 1.0f)) {
      return this
    } else ()
    return this.scl(1.0f / java.lang.Math.sqrt(len2).asInstanceOf[scala.Float])
  }
  @java.lang.Override
  override def dot(vector: Vector4): scala.Float = {
    return (((this.x * vector.x) + (this.y * vector.y)) + (this.z * vector.z)) + (this.w * vector.w)
  }
  def dot(x: scala.Float, y: scala.Float, z: scala.Float, w: scala.Float): scala.Float = {
    return (((this.x * x) + (this.y * y)) + (this.z * z)) + (this.w * w)
  }
  @java.lang.Override
  override def isUnit(): scala.Boolean = {
    return this.isUnit(1.0E-9f)
  }
  @java.lang.Override
  override def isUnit(margin: scala.Float): scala.Boolean = {
    return java.lang.Math.abs(this.len2() - 1.0f) < margin
  }
  @java.lang.Override
  override def isZero(): scala.Boolean = {
    return (((this.x == 0) && (this.y == 0)) && (this.z == 0)) && (this.w == 0)
  }
  @java.lang.Override
  override def isZero(margin: scala.Float): scala.Boolean = {
    return this.len2() < margin
  }
  @java.lang.Override
  override def isOnLine(other: Vector4, epsilon: scala.Float): scala.Boolean = {
    var flags: scala.Int = 0
    var dx: scala.Float = 0
    var dy: scala.Float = 0
    var dz: scala.Float = 0
    var dw: scala.Float = 0
    if (com.badlogic.gdx.math.MathUtils.isZero(this.x, epsilon)) {
      if (!com.badlogic.gdx.math.MathUtils.isZero(other.x, epsilon)) {
        return false
      } else ()
    } else {
      dx = this.x / other.x
      flags = flags | 1
    }
    if (com.badlogic.gdx.math.MathUtils.isZero(this.y, epsilon)) {
      if (!com.badlogic.gdx.math.MathUtils.isZero(other.y, epsilon)) {
        return false
      } else ()
    } else {
      dy = this.y / other.y
      flags = flags | 2
    }
    if (com.badlogic.gdx.math.MathUtils.isZero(this.z, epsilon)) {
      if (!com.badlogic.gdx.math.MathUtils.isZero(other.z, epsilon)) {
        return false
      } else ()
    } else {
      dz = this.z / other.z
      flags = flags | 4
    }
    if (com.badlogic.gdx.math.MathUtils.isZero(this.w, epsilon)) {
      if (!com.badlogic.gdx.math.MathUtils.isZero(other.w, epsilon)) {
        return false
      } else ()
    } else {
      dw = this.w / other.w
      flags = flags | 8
    }
    flags match {
      case 0 | 1 | 2 | 4 | 8 => {
        return true
      }
      case 3 => {
        return com.badlogic.gdx.math.MathUtils.isEqual(dx, dy, epsilon)
      }
      case 5 => {
        return com.badlogic.gdx.math.MathUtils.isEqual(dx, dz, epsilon)
      }
      case 9 => {
        return com.badlogic.gdx.math.MathUtils.isEqual(dx, dw, epsilon)
      }
      case 6 => {
        return com.badlogic.gdx.math.MathUtils.isEqual(dy, dz, epsilon)
      }
      case 10 => {
        return com.badlogic.gdx.math.MathUtils.isEqual(dy, dw, epsilon)
      }
      case 12 => {
        return com.badlogic.gdx.math.MathUtils.isEqual(dz, dw, epsilon)
      }
      case 7 => {
        return com.badlogic.gdx.math.MathUtils.isEqual(dx, dy, epsilon) && com.badlogic.gdx.math.MathUtils.isEqual(dx, dz, epsilon)
      }
      case 11 => {
        return com.badlogic.gdx.math.MathUtils.isEqual(dx, dy, epsilon) && com.badlogic.gdx.math.MathUtils.isEqual(dx, dw, epsilon)
      }
      case 13 => {
        return com.badlogic.gdx.math.MathUtils.isEqual(dx, dz, epsilon) && com.badlogic.gdx.math.MathUtils.isEqual(dx, dw, epsilon)
      }
      case 14 => {
        return com.badlogic.gdx.math.MathUtils.isEqual(dy, dz, epsilon) && com.badlogic.gdx.math.MathUtils.isEqual(dy, dw, epsilon)
      }
      case _ => {
        return (com.badlogic.gdx.math.MathUtils.isEqual(dx, dy, epsilon) && com.badlogic.gdx.math.MathUtils.isEqual(dx, dz, epsilon)) && com.badlogic.gdx.math.MathUtils.isEqual(dx, dw, epsilon)
      }
    }
  }
  @java.lang.Override
  override def isOnLine(other: Vector4): scala.Boolean = {
    return this.isOnLine(other, com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
  }
  @java.lang.Override
  override def isCollinear(other: Vector4, epsilon: scala.Float): scala.Boolean = {
    return this.isOnLine(other, epsilon) && this.hasSameDirection(other)
  }
  @java.lang.Override
  override def isCollinear(other: Vector4): scala.Boolean = {
    return this.isOnLine(other) && this.hasSameDirection(other)
  }
  @java.lang.Override
  override def isCollinearOpposite(other: Vector4, epsilon: scala.Float): scala.Boolean = {
    return this.isOnLine(other, epsilon) && this.hasOppositeDirection(other)
  }
  @java.lang.Override
  override def isCollinearOpposite(other: Vector4): scala.Boolean = {
    return this.isOnLine(other) && this.hasOppositeDirection(other)
  }
  @java.lang.Override
  override def isPerpendicular(vector: Vector4): scala.Boolean = {
    return com.badlogic.gdx.math.MathUtils.isZero(this.dot(vector))
  }
  @java.lang.Override
  override def isPerpendicular(vector: Vector4, epsilon: scala.Float): scala.Boolean = {
    return com.badlogic.gdx.math.MathUtils.isZero(this.dot(vector), epsilon)
  }
  @java.lang.Override
  override def hasSameDirection(vector: Vector4): scala.Boolean = {
    return this.dot(vector) > 0
  }
  @java.lang.Override
  override def hasOppositeDirection(vector: Vector4): scala.Boolean = {
    return this.dot(vector) < 0
  }
  @java.lang.Override
  override def lerp(target: Vector4, alpha: scala.Float): Vector4 = {
    this.x = this.x + (alpha * (target.x - this.x))
    this.y = this.y + (alpha * (target.y - this.y))
    this.z = this.z + (alpha * (target.z - this.z))
    this.w = this.w + (alpha * (target.w - this.w))
    return this
  }
  @java.lang.Override
  override def interpolate(target: Vector4, alpha: scala.Float, interpolator: com.badlogic.gdx.math.Interpolation): Vector4 = {
    return this.lerp(target, interpolator.apply(alpha))
  }
  @java.lang.Override
  override def toString(): java.lang.String = {
    return ((((((("(" + this.x) + ",") + this.y) + ",") + this.z) + ",") + this.w) + ")"
  }
  def fromString(v: java.lang.String): Vector4 = {
    val s0: scala.Int = v.indexOf(',', 1)
    val s1: scala.Int = v.indexOf(',', s0 + 1)
    val s2: scala.Int = v.indexOf(',', s1 + 1)
    if ((((s0 != (-1)) && (s1 != (-1))) && (v.charAt(0) == '(')) && (v.charAt(v.length() - 1) == ')')) {
      try {
        val x: scala.Float = java.lang.Float.parseFloat(v.substring(1, s0))
        val y: scala.Float = java.lang.Float.parseFloat(v.substring(s0 + 1, s1))
        val z: scala.Float = java.lang.Float.parseFloat(v.substring(s1 + 1, s2))
        val w: scala.Float = java.lang.Float.parseFloat(v.substring(s2 + 1, v.length() - 1))
        return this.set(x, y, z, w)
      } catch {
        case ex: java.lang.NumberFormatException => {
          ()
        }
      }
    } else ()
    throw new com.badlogic.gdx.utils.GdxRuntimeException("Malformed Vector4: " + v)
  }
  @java.lang.Override
  override def limit(limit: scala.Float): Vector4 = {
    return this.limit2(limit * limit)
  }
  @java.lang.Override
  override def limit2(limit2: scala.Float): Vector4 = {
    val len2: scala.Float = this.len2()
    if (len2 > limit2) {
      this.scl(java.lang.Math.sqrt(limit2 / len2).asInstanceOf[scala.Float].asInstanceOf[scala.Float])
    } else ()
    return this
  }
  @java.lang.Override
  override def setLength(len: scala.Float): Vector4 = {
    return this.setLength2(len * len)
  }
  @java.lang.Override
  override def setLength2(len2: scala.Float): Vector4 = {
    val oldLen2: scala.Float = this.len2()
    return if ((oldLen2 == 0) || (oldLen2 == len2)) this else this.scl(java.lang.Math.sqrt(len2 / oldLen2).asInstanceOf[scala.Float].asInstanceOf[scala.Float])
  }
  @java.lang.Override
  override def clamp(min: scala.Float, max: scala.Float): Vector4 = {
    val len2: scala.Float = this.len2()
    if (len2 == 0.0f) {
      return this
    } else ()
    val max2: scala.Float = max * max
    if (len2 > max2) {
      return this.scl(java.lang.Math.sqrt(max2 / len2).asInstanceOf[scala.Float].asInstanceOf[scala.Float])
    } else ()
    val min2: scala.Float = min * min
    if (len2 < min2) {
      return this.scl(java.lang.Math.sqrt(min2 / len2).asInstanceOf[scala.Float].asInstanceOf[scala.Float])
    } else ()
    return this
  }
  @java.lang.Override
  override def hashCode(): scala.Int = {
    val prime: scala.Int = 31
    var result: scala.Int = 1
    result = (prime * result) + com.badlogic.gdx.utils.NumberUtils.floatToIntBits(this.x)
    result = (prime * result) + com.badlogic.gdx.utils.NumberUtils.floatToIntBits(this.y)
    result = (prime * result) + com.badlogic.gdx.utils.NumberUtils.floatToIntBits(this.z)
    result = (prime * result) + com.badlogic.gdx.utils.NumberUtils.floatToIntBits(this.w)
    return result
  }
  @java.lang.Override
  override def equals(obj: java.lang.Object): scala.Boolean = {
    if (this == obj) {
      return true
    } else ()
    if (obj == null) {
      return false
    } else ()
    if (this.getClass() != obj.getClass()) {
      return false
    } else ()
    val other: Vector4 = obj.asInstanceOf[Vector4].asInstanceOf[Vector4]
    if (com.badlogic.gdx.utils.NumberUtils.floatToIntBits(this.x) != com.badlogic.gdx.utils.NumberUtils.floatToIntBits(other.x)) {
      return false
    } else ()
    if (com.badlogic.gdx.utils.NumberUtils.floatToIntBits(this.y) != com.badlogic.gdx.utils.NumberUtils.floatToIntBits(other.y)) {
      return false
    } else ()
    if (com.badlogic.gdx.utils.NumberUtils.floatToIntBits(this.z) != com.badlogic.gdx.utils.NumberUtils.floatToIntBits(other.z)) {
      return false
    } else ()
    if (com.badlogic.gdx.utils.NumberUtils.floatToIntBits(this.w) != com.badlogic.gdx.utils.NumberUtils.floatToIntBits(other.w)) {
      return false
    } else ()
    return true
  }
  @java.lang.Override
  override def epsilonEquals(other: Vector4, epsilon: scala.Float): scala.Boolean = {
    if (other == null) {
      return false
    } else ()
    if (java.lang.Math.abs(other.x - this.x) > epsilon) {
      return false
    } else ()
    if (java.lang.Math.abs(other.y - this.y) > epsilon) {
      return false
    } else ()
    if (java.lang.Math.abs(other.z - this.z) > epsilon) {
      return false
    } else ()
    if (java.lang.Math.abs(other.w - this.w) > epsilon) {
      return false
    } else ()
    return true
  }
  def epsilonEquals(x: scala.Float, y: scala.Float, z: scala.Float, w: scala.Float, epsilon: scala.Float): scala.Boolean = {
    if (java.lang.Math.abs(x - this.x) > epsilon) {
      return false
    } else ()
    if (java.lang.Math.abs(y - this.y) > epsilon) {
      return false
    } else ()
    if (java.lang.Math.abs(z - this.z) > epsilon) {
      return false
    } else ()
    if (java.lang.Math.abs(w - this.w) > epsilon) {
      return false
    } else ()
    return true
  }
  def epsilonEquals(other: Vector4): scala.Boolean = {
    return this.epsilonEquals(other, com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
  }
  def epsilonEquals(x: scala.Float, y: scala.Float, z: scala.Float, w: scala.Float): scala.Boolean = {
    return this.epsilonEquals(x, y, z, w, com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
  }
  @java.lang.Override
  override def setZero(): Vector4 = {
    this.x = 0
    this.y = 0
    this.z = 0
    this.w = 0
    return this
  }
}
object Vector4 {
  private final val serialVersionUID: scala.Long = -5394070284130414492L
  final val X: Vector4 = new Vector4(1, 0, 0, 0)
  final val Y: Vector4 = new Vector4(0, 1, 0, 0)
  final val Z: Vector4 = new Vector4(0, 0, 1, 0)
  final val W: Vector4 = new Vector4(0, 0, 0, 1)
  final val Zero: Vector4 = new Vector4(0, 0, 0, 0)
  final val One: Vector4 = new Vector4(1, 1, 1, 1)
  def len(x: scala.Float, y: scala.Float, z: scala.Float, w: scala.Float): scala.Float = {
    return java.lang.Math.sqrt((((x * x) + (y * y)) + (z * z)) + (w * w)).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  }
  def len2(x: scala.Float, y: scala.Float, z: scala.Float, w: scala.Float): scala.Float = {
    return (((x * x) + (y * y)) + (z * z)) + (w * w)
  }
  def dst(x1: scala.Float, y1: scala.Float, z1: scala.Float, w1: scala.Float, x2: scala.Float, y2: scala.Float, z2: scala.Float, w2: scala.Float): scala.Float = {
    val a: scala.Float = x2 - x1
    val b: scala.Float = y2 - y1
    val c: scala.Float = z2 - z1
    val d: scala.Float = w2 - w1
    return java.lang.Math.sqrt((((a * a) + (b * b)) + (c * c)) + (d * d)).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  }
  def dst2(x1: scala.Float, y1: scala.Float, z1: scala.Float, w1: scala.Float, x2: scala.Float, y2: scala.Float, z2: scala.Float, w2: scala.Float): scala.Float = {
    val a: scala.Float = x2 - x1
    val b: scala.Float = y2 - y1
    val c: scala.Float = z2 - z1
    val d: scala.Float = w2 - w1
    return (((a * a) + (b * b)) + (c * c)) + (d * d)
  }
  def dot(x1: scala.Float, y1: scala.Float, z1: scala.Float, w1: scala.Float, x2: scala.Float, y2: scala.Float, z2: scala.Float, w2: scala.Float): scala.Float = {
    return (((x1 * x2) + (y1 * y2)) + (z1 * z2)) + (w1 * w2)
  }
}