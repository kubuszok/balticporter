package com.badlogic.gdx.math

class Vector2 extends java.io.Serializable with com.badlogic.gdx.math.Vector[Vector2] {
  var x: scala.Float = 0.0f
  var y: scala.Float = 0.0f
  def this(x: scala.Float, y: scala.Float) = {
    this()
    this.x = x
    this.y = y
  }
  def this(v: Vector2) = {
    this()
    this.set(v)
  }
  def cpy(): Vector2 = {
    return new Vector2(this)
  }
  def len(): scala.Float = {
    return java.lang.Math.sqrt((this.x * this.x) + (this.y * this.y)).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  }
  def len2(): scala.Float = {
    return (this.x * this.x) + (this.y * this.y)
  }
  def set(v: Vector2): Vector2 = {
    this.x = v.x
    this.y = v.y
    return this
  }
  def set(x: scala.Float, y: scala.Float): Vector2 = {
    this.x = x
    this.y = y
    return this
  }
  def sub(v: Vector2): Vector2 = {
    this.x = this.x - v.x
    this.y = this.y - v.y
    return this
  }
  def sub(x: scala.Float, y: scala.Float): Vector2 = {
    this.x = this.x - x
    this.y = this.y - y
    return this
  }
  def nor(): Vector2 = {
    val len: scala.Float = this.len()
    if (len != 0) {
      this.x = this.x / len
      this.y = this.y / len
    } else ()
    return this
  }
  def add(v: Vector2): Vector2 = {
    this.x = this.x + v.x
    this.y = this.y + v.y
    return this
  }
  def add(x: scala.Float, y: scala.Float): Vector2 = {
    this.x = this.x + x
    this.y = this.y + y
    return this
  }
  def dot(v: Vector2): scala.Float = {
    return (this.x * v.x) + (this.y * v.y)
  }
  def dot(ox: scala.Float, oy: scala.Float): scala.Float = {
    return (this.x * ox) + (this.y * oy)
  }
  def scl(scalar: scala.Float): Vector2 = {
    this.x = this.x * scalar
    this.y = this.y * scalar
    return this
  }
  def scl(x: scala.Float, y: scala.Float): Vector2 = {
    this.x = this.x * x
    this.y = this.y * y
    return this
  }
  def scl(v: Vector2): Vector2 = {
    this.x = this.x * v.x
    this.y = this.y * v.y
    return this
  }
  def mulAdd(vec: Vector2, scalar: scala.Float): Vector2 = {
    this.x = this.x + (vec.x * scalar)
    this.y = this.y + (vec.y * scalar)
    return this
  }
  def mulAdd(vec: Vector2, mulVec: Vector2): Vector2 = {
    this.x = this.x + (vec.x * mulVec.x)
    this.y = this.y + (vec.y * mulVec.y)
    return this
  }
  def idt(vector: Vector2): scala.Boolean = {
    return (this.x == vector.x) && (this.y == vector.y)
  }
  def dst(v: Vector2): scala.Float = {
    val x_d: scala.Float = v.x - this.x
    val y_d: scala.Float = v.y - this.y
    return java.lang.Math.sqrt((x_d * x_d) + (y_d * y_d)).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  }
  def dst(x: scala.Float, y: scala.Float): scala.Float = {
    val x_d: scala.Float = x - this.x
    val y_d: scala.Float = y - this.y
    return java.lang.Math.sqrt((x_d * x_d) + (y_d * y_d)).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  }
  def dst2(v: Vector2): scala.Float = {
    val x_d: scala.Float = v.x - this.x
    val y_d: scala.Float = v.y - this.y
    return (x_d * x_d) + (y_d * y_d)
  }
  def dst2(x: scala.Float, y: scala.Float): scala.Float = {
    val x_d: scala.Float = x - this.x
    val y_d: scala.Float = y - this.y
    return (x_d * x_d) + (y_d * y_d)
  }
  def limit(limit: scala.Float): Vector2 = {
    return this.limit2(limit * limit)
  }
  def limit2(limit2: scala.Float): Vector2 = {
    val len2: scala.Float = this.len2()
    if (len2 > limit2) {
      return this.scl(java.lang.Math.sqrt(limit2 / len2).asInstanceOf[scala.Float].asInstanceOf[scala.Float])
    } else ()
    return this
  }
  def clamp(min: scala.Float, max: scala.Float): Vector2 = {
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
  def setLength(len: scala.Float): Vector2 = {
    return this.setLength2(len * len)
  }
  def setLength2(len2: scala.Float): Vector2 = {
    val oldLen2: scala.Float = this.len2()
    return if ((oldLen2 == 0) || (oldLen2 == len2)) this else this.scl(java.lang.Math.sqrt(len2 / oldLen2).asInstanceOf[scala.Float].asInstanceOf[scala.Float])
  }
  def toString(): java.lang.String = {
    return ((("(" + this.x) + ",") + this.y) + ")"
  }
  def fromString(v: java.lang.String): Vector2 = {
    val s: scala.Int = v.indexOf(',', 1)
    if (((s != (-1)) && (v.charAt(0) == '(')) && (v.charAt(v.length() - 1) == ')')) {
      try {
        val x: scala.Float = java.lang.Float.parseFloat(v.substring(1, s))
        val y: scala.Float = java.lang.Float.parseFloat(v.substring(s + 1, v.length() - 1))
        return this.set(x, y)
      } catch {
        case ex: java.lang.NumberFormatException => {
          ()
        }
      }
    } else ()
    throw new com.badlogic.gdx.utils.GdxRuntimeException("Malformed Vector2: " + v)
  }
  def mul(mat: com.badlogic.gdx.math.Matrix3): Vector2 = {
    var x: scala.Float = ((this.x * mat.`val`(0)) + (this.y * mat.`val`(3))) + mat.`val`(6)
    var y: scala.Float = ((this.x * mat.`val`(1)) + (this.y * mat.`val`(4))) + mat.`val`(7)
    this.x = x
    this.y = y
    return this
  }
  def crs(v: Vector2): scala.Float = {
    return (this.x * v.y) - (this.y * v.x)
  }
  def crs(x: scala.Float, y: scala.Float): scala.Float = {
    return (this.x * y) - (this.y * x)
  }
  def angle(): scala.Float = {
    var angle: scala.Float = java.lang.Math.atan2(this.y, this.x).asInstanceOf[scala.Float] * com.badlogic.gdx.math.MathUtils.radiansToDegrees
    if (angle < 0) {
      angle = angle + 360
    } else ()
    return angle
  }
  def angle(reference: Vector2): scala.Float = {
    return java.lang.Math.atan2(this.crs(reference), this.dot(reference)).asInstanceOf[scala.Float] * com.badlogic.gdx.math.MathUtils.radiansToDegrees
  }
  def angleDeg(): scala.Float = {
    var angle: scala.Float = java.lang.Math.atan2(this.y, this.x).asInstanceOf[scala.Float] * com.badlogic.gdx.math.MathUtils.radiansToDegrees
    if (angle < 0) {
      angle = angle + 360
    } else ()
    return angle
  }
  def angleDeg(reference: Vector2): scala.Float = {
    var angle: scala.Float = java.lang.Math.atan2(reference.crs(this), reference.dot(this)).asInstanceOf[scala.Float] * com.badlogic.gdx.math.MathUtils.radiansToDegrees
    if (angle < 0) {
      angle = angle + 360
    } else ()
    return angle
  }
  def angleRad(): scala.Float = {
    return java.lang.Math.atan2(this.y, this.x).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  }
  def angleRad(reference: Vector2): scala.Float = {
    return java.lang.Math.atan2(reference.crs(this), reference.dot(this)).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  }
  def setAngle(degrees: scala.Float): Vector2 = {
    return this.setAngleRad(degrees * com.badlogic.gdx.math.MathUtils.degreesToRadians)
  }
  def setAngleDeg(degrees: scala.Float): Vector2 = {
    return this.setAngleRad(degrees * com.badlogic.gdx.math.MathUtils.degreesToRadians)
  }
  def setAngleRad(radians: scala.Float): Vector2 = {
    this.set(this.len(), 0.0f)
    this.rotateRad(radians)
    return this
  }
  def rotate(degrees: scala.Float): Vector2 = {
    return this.rotateRad(degrees * com.badlogic.gdx.math.MathUtils.degreesToRadians)
  }
  def rotateAround(reference: Vector2, degrees: scala.Float): Vector2 = {
    return this.sub(reference).rotateDeg(degrees).add(reference)
  }
  def rotateDeg(degrees: scala.Float): Vector2 = {
    return this.rotateRad(degrees * com.badlogic.gdx.math.MathUtils.degreesToRadians)
  }
  def rotateRad(radians: scala.Float): Vector2 = {
    val cos: scala.Float = java.lang.Math.cos(radians).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    val sin: scala.Float = java.lang.Math.sin(radians).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    val newX: scala.Float = (this.x * cos) - (this.y * sin)
    val newY: scala.Float = (this.x * sin) + (this.y * cos)
    this.x = newX
    this.y = newY
    return this
  }
  def rotateAroundDeg(reference: Vector2, degrees: scala.Float): Vector2 = {
    return this.sub(reference).rotateDeg(degrees).add(reference)
  }
  def rotateAroundRad(reference: Vector2, radians: scala.Float): Vector2 = {
    return this.sub(reference).rotateRad(radians).add(reference)
  }
  def rotate90(dir: scala.Int): Vector2 = {
    var x: scala.Float = this.x
    if (dir >= 0) {
      this.x = -this.y
      this.y = x
    } else {
      this.x = this.y
      this.y = -x
    }
    return this
  }
  def lerp(target: Vector2, alpha: scala.Float): Vector2 = {
    val invAlpha: scala.Float = 1.0f - alpha
    this.x = (this.x * invAlpha) + (target.x * alpha)
    this.y = (this.y * invAlpha) + (target.y * alpha)
    return this
  }
  def interpolate(target: Vector2, alpha: scala.Float, interpolation: com.badlogic.gdx.math.Interpolation): Vector2 = {
    return this.lerp(target, interpolation.apply(alpha))
  }
  def setToRandomDirection(): Vector2 = {
    val theta: scala.Float = com.badlogic.gdx.math.MathUtils.random(0.0f, com.badlogic.gdx.math.MathUtils.PI2)
    return this.set(com.badlogic.gdx.math.MathUtils.cos(theta), com.badlogic.gdx.math.MathUtils.sin(theta))
  }
  def hashCode(): scala.Int = {
    val prime: scala.Int = 31
    var result: scala.Int = 1
    result = (prime * result) + com.badlogic.gdx.utils.NumberUtils.floatToIntBits(this.x)
    result = (prime * result) + com.badlogic.gdx.utils.NumberUtils.floatToIntBits(this.y)
    return result
  }
  def equals(obj: java.lang.Object): scala.Boolean = {
    if (this == obj) {
      return true
    } else ()
    if (obj == null) {
      return false
    } else ()
    if (this.getClass() != obj.getClass()) {
      return false
    } else ()
    val other: Vector2 = obj.asInstanceOf[Vector2].asInstanceOf[Vector2]
    if (com.badlogic.gdx.utils.NumberUtils.floatToIntBits(this.x) != com.badlogic.gdx.utils.NumberUtils.floatToIntBits(other.x)) {
      return false
    } else ()
    if (com.badlogic.gdx.utils.NumberUtils.floatToIntBits(this.y) != com.badlogic.gdx.utils.NumberUtils.floatToIntBits(other.y)) {
      return false
    } else ()
    return true
  }
  def epsilonEquals(other: Vector2, epsilon: scala.Float): scala.Boolean = {
    if (other == null) {
      return false
    } else ()
    if (java.lang.Math.abs(other.x - this.x) > epsilon) {
      return false
    } else ()
    if (java.lang.Math.abs(other.y - this.y) > epsilon) {
      return false
    } else ()
    return true
  }
  def epsilonEquals(x: scala.Float, y: scala.Float, epsilon: scala.Float): scala.Boolean = {
    if (java.lang.Math.abs(x - this.x) > epsilon) {
      return false
    } else ()
    if (java.lang.Math.abs(y - this.y) > epsilon) {
      return false
    } else ()
    return true
  }
  def epsilonEquals(other: Vector2): scala.Boolean = {
    return this.epsilonEquals(other, com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
  }
  def epsilonEquals(x: scala.Float, y: scala.Float): scala.Boolean = {
    return this.epsilonEquals(x, y, com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
  }
  def isUnit(): scala.Boolean = {
    return this.isUnit(1.0E-9f)
  }
  def isUnit(margin: scala.Float): scala.Boolean = {
    return java.lang.Math.abs(this.len2() - 1.0f) < margin
  }
  def isZero(): scala.Boolean = {
    return (this.x == 0) && (this.y == 0)
  }
  def isZero(margin: scala.Float): scala.Boolean = {
    return this.len2() < margin
  }
  def isOnLine(other: Vector2): scala.Boolean = {
    return com.badlogic.gdx.math.MathUtils.isZero((this.x * other.y) - (this.y * other.x))
  }
  def isOnLine(other: Vector2, epsilon: scala.Float): scala.Boolean = {
    return com.badlogic.gdx.math.MathUtils.isZero((this.x * other.y) - (this.y * other.x), epsilon)
  }
  def isCollinear(other: Vector2, epsilon: scala.Float): scala.Boolean = {
    return this.isOnLine(other, epsilon) && (this.dot(other) > 0.0f)
  }
  def isCollinear(other: Vector2): scala.Boolean = {
    return this.isOnLine(other) && (this.dot(other) > 0.0f)
  }
  def isCollinearOpposite(other: Vector2, epsilon: scala.Float): scala.Boolean = {
    return this.isOnLine(other, epsilon) && (this.dot(other) < 0.0f)
  }
  def isCollinearOpposite(other: Vector2): scala.Boolean = {
    return this.isOnLine(other) && (this.dot(other) < 0.0f)
  }
  def isPerpendicular(vector: Vector2): scala.Boolean = {
    return com.badlogic.gdx.math.MathUtils.isZero(this.dot(vector))
  }
  def isPerpendicular(vector: Vector2, epsilon: scala.Float): scala.Boolean = {
    return com.badlogic.gdx.math.MathUtils.isZero(this.dot(vector), epsilon)
  }
  def hasSameDirection(vector: Vector2): scala.Boolean = {
    return this.dot(vector) > 0
  }
  def hasOppositeDirection(vector: Vector2): scala.Boolean = {
    return this.dot(vector) < 0
  }
  def setZero(): Vector2 = {
    this.x = 0
    this.y = 0
    return this
  }
}
object Vector2 {
  private final val serialVersionUID: scala.Long = 913902788239530931L
  final val X: Vector2 = new Vector2(1, 0)
  final val Y: Vector2 = new Vector2(0, 1)
  final val Zero: Vector2 = new Vector2(0, 0)
  final val One: Vector2 = new Vector2(1, 1)
  def len(x: scala.Float, y: scala.Float): scala.Float = {
    return java.lang.Math.sqrt((x * x) + (y * y)).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  }
  def len2(x: scala.Float, y: scala.Float): scala.Float = {
    return (x * x) + (y * y)
  }
  def dot(x1: scala.Float, y1: scala.Float, x2: scala.Float, y2: scala.Float): scala.Float = {
    return (x1 * x2) + (y1 * y2)
  }
  def dst(x1: scala.Float, y1: scala.Float, x2: scala.Float, y2: scala.Float): scala.Float = {
    val x_d: scala.Float = x2 - x1
    val y_d: scala.Float = y2 - y1
    return java.lang.Math.sqrt((x_d * x_d) + (y_d * y_d)).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  }
  def dst2(x1: scala.Float, y1: scala.Float, x2: scala.Float, y2: scala.Float): scala.Float = {
    val x_d: scala.Float = x2 - x1
    val y_d: scala.Float = y2 - y1
    return (x_d * x_d) + (y_d * y_d)
  }
  def angleDeg(x: scala.Float, y: scala.Float): scala.Float = {
    var angle: scala.Float = java.lang.Math.atan2(y, x).asInstanceOf[scala.Float] * com.badlogic.gdx.math.MathUtils.radiansToDegrees
    if (angle < 0) {
      angle = angle + 360
    } else ()
    return angle
  }
  def angleRad(x: scala.Float, y: scala.Float): scala.Float = {
    return java.lang.Math.atan2(y, x).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  }
}