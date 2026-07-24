package com.badlogic.gdx.math

class Vector3 extends java.io.Serializable with com.badlogic.gdx.math.Vector[Vector3] {
  var x: scala.Float = 0.0f
  var y: scala.Float = 0.0f
  var z: scala.Float = 0.0f
  def this(x: scala.Float, y: scala.Float, z: scala.Float) = {
    this()
    this.set(x, y, z)
  }
  def this(vector: Vector3) = {
    this()
    this.set(vector)
  }
  def this(values: scala.Array[scala.Float]) = {
    this()
    this.set(values(0), values(1), values(2))
  }
  def this(vector: com.badlogic.gdx.math.Vector2, z: scala.Float) = {
    this()
    this.set(vector.x, vector.y, z)
  }
  def set(x: scala.Float, y: scala.Float, z: scala.Float): Vector3 = {
    this.x = x
    this.y = y
    this.z = z
    return this
  }
  def set(vector: Vector3): Vector3 = {
    return this.set(vector.x, vector.y, vector.z)
  }
  def set(values: scala.Array[scala.Float]): Vector3 = {
    return this.set(values(0), values(1), values(2))
  }
  def set(vector: com.badlogic.gdx.math.Vector2, z: scala.Float): Vector3 = {
    return this.set(vector.x, vector.y, z)
  }
  def setFromSpherical(azimuthalAngle: scala.Float, polarAngle: scala.Float): Vector3 = {
    val cosPolar: scala.Float = com.badlogic.gdx.math.MathUtils.cos(polarAngle)
    val sinPolar: scala.Float = com.badlogic.gdx.math.MathUtils.sin(polarAngle)
    val cosAzim: scala.Float = com.badlogic.gdx.math.MathUtils.cos(azimuthalAngle)
    val sinAzim: scala.Float = com.badlogic.gdx.math.MathUtils.sin(azimuthalAngle)
    return this.set(cosAzim * sinPolar, sinAzim * sinPolar, cosPolar)
  }
  def setToRandomDirection(): Vector3 = {
    val u: scala.Float = com.badlogic.gdx.math.MathUtils.random()
    val v: scala.Float = com.badlogic.gdx.math.MathUtils.random()
    val theta: scala.Float = com.badlogic.gdx.math.MathUtils.PI2 * u
    val phi: scala.Float = java.lang.Math.acos((2.0f * v) - 1.0f).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    return this.setFromSpherical(theta, phi)
  }
  def cpy(): Vector3 = {
    return new Vector3(this)
  }
  def add(vector: Vector3): Vector3 = {
    return this.add(vector.x, vector.y, vector.z)
  }
  def add(x: scala.Float, y: scala.Float, z: scala.Float): Vector3 = {
    return this.set(this.x + x, this.y + y, this.z + z)
  }
  def add(values: scala.Float): Vector3 = {
    return this.set(this.x + values, this.y + values, this.z + values)
  }
  def sub(a_vec: Vector3): Vector3 = {
    return this.sub(a_vec.x, a_vec.y, a_vec.z)
  }
  def sub(x: scala.Float, y: scala.Float, z: scala.Float): Vector3 = {
    return this.set(this.x - x, this.y - y, this.z - z)
  }
  def sub(value: scala.Float): Vector3 = {
    return this.set(this.x - value, this.y - value, this.z - value)
  }
  def scl(scalar: scala.Float): Vector3 = {
    return this.set(this.x * scalar, this.y * scalar, this.z * scalar)
  }
  def scl(other: Vector3): Vector3 = {
    return this.set(this.x * other.x, this.y * other.y, this.z * other.z)
  }
  def scl(vx: scala.Float, vy: scala.Float, vz: scala.Float): Vector3 = {
    return this.set(this.x * vx, this.y * vy, this.z * vz)
  }
  def mulAdd(vec: Vector3, scalar: scala.Float): Vector3 = {
    this.x = this.x + (vec.x * scalar)
    this.y = this.y + (vec.y * scalar)
    this.z = this.z + (vec.z * scalar)
    return this
  }
  def mulAdd(vec: Vector3, mulVec: Vector3): Vector3 = {
    this.x = this.x + (vec.x * mulVec.x)
    this.y = this.y + (vec.y * mulVec.y)
    this.z = this.z + (vec.z * mulVec.z)
    return this
  }
  def len(): scala.Float = {
    return java.lang.Math.sqrt(((this.x * this.x) + (this.y * this.y)) + (this.z * this.z)).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  }
  def len2(): scala.Float = {
    return ((this.x * this.x) + (this.y * this.y)) + (this.z * this.z)
  }
  def idt(vector: Vector3): scala.Boolean = {
    return ((this.x == vector.x) && (this.y == vector.y)) && (this.z == vector.z)
  }
  def dst(vector: Vector3): scala.Float = {
    val a: scala.Float = vector.x - this.x
    val b: scala.Float = vector.y - this.y
    val c: scala.Float = vector.z - this.z
    return java.lang.Math.sqrt(((a * a) + (b * b)) + (c * c)).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  }
  def dst(x: scala.Float, y: scala.Float, z: scala.Float): scala.Float = {
    val a: scala.Float = x - this.x
    val b: scala.Float = y - this.y
    val c: scala.Float = z - this.z
    return java.lang.Math.sqrt(((a * a) + (b * b)) + (c * c)).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  }
  def dst2(point: Vector3): scala.Float = {
    val a: scala.Float = point.x - this.x
    val b: scala.Float = point.y - this.y
    val c: scala.Float = point.z - this.z
    return ((a * a) + (b * b)) + (c * c)
  }
  def dst2(x: scala.Float, y: scala.Float, z: scala.Float): scala.Float = {
    val a: scala.Float = x - this.x
    val b: scala.Float = y - this.y
    val c: scala.Float = z - this.z
    return ((a * a) + (b * b)) + (c * c)
  }
  def nor(): Vector3 = {
    val len2: scala.Float = this.len2()
    if ((len2 == 0.0f) || (len2 == 1.0f)) {
      return this
    } else ()
    return this.scl(1.0f / java.lang.Math.sqrt(len2).asInstanceOf[scala.Float])
  }
  def dot(vector: Vector3): scala.Float = {
    return ((this.x * vector.x) + (this.y * vector.y)) + (this.z * vector.z)
  }
  def dot(x: scala.Float, y: scala.Float, z: scala.Float): scala.Float = {
    return ((this.x * x) + (this.y * y)) + (this.z * z)
  }
  def crs(vector: Vector3): Vector3 = {
    return this.set((this.y * vector.z) - (this.z * vector.y), (this.z * vector.x) - (this.x * vector.z), (this.x * vector.y) - (this.y * vector.x))
  }
  def crs(x: scala.Float, y: scala.Float, z: scala.Float): Vector3 = {
    return this.set((this.y * z) - (this.z * y), (this.z * x) - (this.x * z), (this.x * y) - (this.y * x))
  }
  def mul4x3(matrix: scala.Array[scala.Float]): Vector3 = {
    return this.set((((this.x * matrix(0)) + (this.y * matrix(3))) + (this.z * matrix(6))) + matrix(9), (((this.x * matrix(1)) + (this.y * matrix(4))) + (this.z * matrix(7))) + matrix(10), (((this.x * matrix(2)) + (this.y * matrix(5))) + (this.z * matrix(8))) + matrix(11))
  }
  def mul(matrix: com.badlogic.gdx.math.Matrix4): Vector3 = {
    val l_mat: scala.Array[scala.Float] = matrix.`val`
    return this.set((((this.x * l_mat(com.badlogic.gdx.math.Matrix4.M00)) + (this.y * l_mat(com.badlogic.gdx.math.Matrix4.M01))) + (this.z * l_mat(com.badlogic.gdx.math.Matrix4.M02))) + l_mat(com.badlogic.gdx.math.Matrix4.M03), (((this.x * l_mat(com.badlogic.gdx.math.Matrix4.M10)) + (this.y * l_mat(com.badlogic.gdx.math.Matrix4.M11))) + (this.z * l_mat(com.badlogic.gdx.math.Matrix4.M12))) + l_mat(com.badlogic.gdx.math.Matrix4.M13), (((this.x * l_mat(com.badlogic.gdx.math.Matrix4.M20)) + (this.y * l_mat(com.badlogic.gdx.math.Matrix4.M21))) + (this.z * l_mat(com.badlogic.gdx.math.Matrix4.M22))) + l_mat(com.badlogic.gdx.math.Matrix4.M23))
  }
  def traMul(matrix: com.badlogic.gdx.math.Matrix4): Vector3 = {
    val l_mat: scala.Array[scala.Float] = matrix.`val`
    return this.set((((this.x * l_mat(com.badlogic.gdx.math.Matrix4.M00)) + (this.y * l_mat(com.badlogic.gdx.math.Matrix4.M10))) + (this.z * l_mat(com.badlogic.gdx.math.Matrix4.M20))) + l_mat(com.badlogic.gdx.math.Matrix4.M30), (((this.x * l_mat(com.badlogic.gdx.math.Matrix4.M01)) + (this.y * l_mat(com.badlogic.gdx.math.Matrix4.M11))) + (this.z * l_mat(com.badlogic.gdx.math.Matrix4.M21))) + l_mat(com.badlogic.gdx.math.Matrix4.M31), (((this.x * l_mat(com.badlogic.gdx.math.Matrix4.M02)) + (this.y * l_mat(com.badlogic.gdx.math.Matrix4.M12))) + (this.z * l_mat(com.badlogic.gdx.math.Matrix4.M22))) + l_mat(com.badlogic.gdx.math.Matrix4.M32))
  }
  def mul(matrix: com.badlogic.gdx.math.Matrix3): Vector3 = {
    val l_mat: scala.Array[scala.Float] = matrix.`val`
    return this.set(((this.x * l_mat(com.badlogic.gdx.math.Matrix3.M00)) + (this.y * l_mat(com.badlogic.gdx.math.Matrix3.M01))) + (this.z * l_mat(com.badlogic.gdx.math.Matrix3.M02)), ((this.x * l_mat(com.badlogic.gdx.math.Matrix3.M10)) + (this.y * l_mat(com.badlogic.gdx.math.Matrix3.M11))) + (this.z * l_mat(com.badlogic.gdx.math.Matrix3.M12)), ((this.x * l_mat(com.badlogic.gdx.math.Matrix3.M20)) + (this.y * l_mat(com.badlogic.gdx.math.Matrix3.M21))) + (this.z * l_mat(com.badlogic.gdx.math.Matrix3.M22)))
  }
  def traMul(matrix: com.badlogic.gdx.math.Matrix3): Vector3 = {
    val l_mat: scala.Array[scala.Float] = matrix.`val`
    return this.set(((this.x * l_mat(com.badlogic.gdx.math.Matrix3.M00)) + (this.y * l_mat(com.badlogic.gdx.math.Matrix3.M10))) + (this.z * l_mat(com.badlogic.gdx.math.Matrix3.M20)), ((this.x * l_mat(com.badlogic.gdx.math.Matrix3.M01)) + (this.y * l_mat(com.badlogic.gdx.math.Matrix3.M11))) + (this.z * l_mat(com.badlogic.gdx.math.Matrix3.M21)), ((this.x * l_mat(com.badlogic.gdx.math.Matrix3.M02)) + (this.y * l_mat(com.badlogic.gdx.math.Matrix3.M12))) + (this.z * l_mat(com.badlogic.gdx.math.Matrix3.M22)))
  }
  def mul(quat: com.badlogic.gdx.math.Quaternion): Vector3 = {
    return quat.transform(this)
  }
  def prj(matrix: com.badlogic.gdx.math.Matrix4): Vector3 = {
    val l_mat: scala.Array[scala.Float] = matrix.`val`
    val l_w: scala.Float = 1.0f / ((((this.x * l_mat(com.badlogic.gdx.math.Matrix4.M30)) + (this.y * l_mat(com.badlogic.gdx.math.Matrix4.M31))) + (this.z * l_mat(com.badlogic.gdx.math.Matrix4.M32))) + l_mat(com.badlogic.gdx.math.Matrix4.M33))
    return this.set(((((this.x * l_mat(com.badlogic.gdx.math.Matrix4.M00)) + (this.y * l_mat(com.badlogic.gdx.math.Matrix4.M01))) + (this.z * l_mat(com.badlogic.gdx.math.Matrix4.M02))) + l_mat(com.badlogic.gdx.math.Matrix4.M03)) * l_w, ((((this.x * l_mat(com.badlogic.gdx.math.Matrix4.M10)) + (this.y * l_mat(com.badlogic.gdx.math.Matrix4.M11))) + (this.z * l_mat(com.badlogic.gdx.math.Matrix4.M12))) + l_mat(com.badlogic.gdx.math.Matrix4.M13)) * l_w, ((((this.x * l_mat(com.badlogic.gdx.math.Matrix4.M20)) + (this.y * l_mat(com.badlogic.gdx.math.Matrix4.M21))) + (this.z * l_mat(com.badlogic.gdx.math.Matrix4.M22))) + l_mat(com.badlogic.gdx.math.Matrix4.M23)) * l_w)
  }
  def rot(matrix: com.badlogic.gdx.math.Matrix4): Vector3 = {
    val l_mat: scala.Array[scala.Float] = matrix.`val`
    return this.set(((this.x * l_mat(com.badlogic.gdx.math.Matrix4.M00)) + (this.y * l_mat(com.badlogic.gdx.math.Matrix4.M01))) + (this.z * l_mat(com.badlogic.gdx.math.Matrix4.M02)), ((this.x * l_mat(com.badlogic.gdx.math.Matrix4.M10)) + (this.y * l_mat(com.badlogic.gdx.math.Matrix4.M11))) + (this.z * l_mat(com.badlogic.gdx.math.Matrix4.M12)), ((this.x * l_mat(com.badlogic.gdx.math.Matrix4.M20)) + (this.y * l_mat(com.badlogic.gdx.math.Matrix4.M21))) + (this.z * l_mat(com.badlogic.gdx.math.Matrix4.M22)))
  }
  def unrotate(matrix: com.badlogic.gdx.math.Matrix4): Vector3 = {
    val l_mat: scala.Array[scala.Float] = matrix.`val`
    return this.set(((this.x * l_mat(com.badlogic.gdx.math.Matrix4.M00)) + (this.y * l_mat(com.badlogic.gdx.math.Matrix4.M10))) + (this.z * l_mat(com.badlogic.gdx.math.Matrix4.M20)), ((this.x * l_mat(com.badlogic.gdx.math.Matrix4.M01)) + (this.y * l_mat(com.badlogic.gdx.math.Matrix4.M11))) + (this.z * l_mat(com.badlogic.gdx.math.Matrix4.M21)), ((this.x * l_mat(com.badlogic.gdx.math.Matrix4.M02)) + (this.y * l_mat(com.badlogic.gdx.math.Matrix4.M12))) + (this.z * l_mat(com.badlogic.gdx.math.Matrix4.M22)))
  }
  def untransform(matrix: com.badlogic.gdx.math.Matrix4): Vector3 = {
    val l_mat: scala.Array[scala.Float] = matrix.`val`
    this.x = this.x - l_mat(com.badlogic.gdx.math.Matrix4.M03)
    this.y = this.y - l_mat(com.badlogic.gdx.math.Matrix4.M03)
    this.z = this.z - l_mat(com.badlogic.gdx.math.Matrix4.M03)
    return this.set(((this.x * l_mat(com.badlogic.gdx.math.Matrix4.M00)) + (this.y * l_mat(com.badlogic.gdx.math.Matrix4.M10))) + (this.z * l_mat(com.badlogic.gdx.math.Matrix4.M20)), ((this.x * l_mat(com.badlogic.gdx.math.Matrix4.M01)) + (this.y * l_mat(com.badlogic.gdx.math.Matrix4.M11))) + (this.z * l_mat(com.badlogic.gdx.math.Matrix4.M21)), ((this.x * l_mat(com.badlogic.gdx.math.Matrix4.M02)) + (this.y * l_mat(com.badlogic.gdx.math.Matrix4.M12))) + (this.z * l_mat(com.badlogic.gdx.math.Matrix4.M22)))
  }
  def rotate(degrees: scala.Float, axisX: scala.Float, axisY: scala.Float, axisZ: scala.Float): Vector3 = {
    return this.mul(Vector3.tmpMat.setToRotation(axisX, axisY, axisZ, degrees))
  }
  def rotateRad(radians: scala.Float, axisX: scala.Float, axisY: scala.Float, axisZ: scala.Float): Vector3 = {
    return this.mul(Vector3.tmpMat.setToRotationRad(axisX, axisY, axisZ, radians))
  }
  def rotate(axis: Vector3, degrees: scala.Float): Vector3 = {
    Vector3.tmpMat.setToRotation(axis, degrees)
    return this.mul(Vector3.tmpMat)
  }
  def rotateRad(axis: Vector3, radians: scala.Float): Vector3 = {
    Vector3.tmpMat.setToRotationRad(axis, radians)
    return this.mul(Vector3.tmpMat)
  }
  def isUnit(): scala.Boolean = {
    return this.isUnit(1.0E-9f)
  }
  def isUnit(margin: scala.Float): scala.Boolean = {
    return java.lang.Math.abs(this.len2() - 1.0f) < margin
  }
  def isZero(): scala.Boolean = {
    return ((this.x == 0) && (this.y == 0)) && (this.z == 0)
  }
  def isZero(margin: scala.Float): scala.Boolean = {
    return this.len2() < margin
  }
  def isOnLine(other: Vector3, epsilon: scala.Float): scala.Boolean = {
    return Vector3.len2((this.y * other.z) - (this.z * other.y), (this.z * other.x) - (this.x * other.z), (this.x * other.y) - (this.y * other.x)) <= epsilon
  }
  def isOnLine(other: Vector3): scala.Boolean = {
    return Vector3.len2((this.y * other.z) - (this.z * other.y), (this.z * other.x) - (this.x * other.z), (this.x * other.y) - (this.y * other.x)) <= com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR
  }
  def isCollinear(other: Vector3, epsilon: scala.Float): scala.Boolean = {
    return this.isOnLine(other, epsilon) && this.hasSameDirection(other)
  }
  def isCollinear(other: Vector3): scala.Boolean = {
    return this.isOnLine(other) && this.hasSameDirection(other)
  }
  def isCollinearOpposite(other: Vector3, epsilon: scala.Float): scala.Boolean = {
    return this.isOnLine(other, epsilon) && this.hasOppositeDirection(other)
  }
  def isCollinearOpposite(other: Vector3): scala.Boolean = {
    return this.isOnLine(other) && this.hasOppositeDirection(other)
  }
  def isPerpendicular(vector: Vector3): scala.Boolean = {
    return com.badlogic.gdx.math.MathUtils.isZero(this.dot(vector))
  }
  def isPerpendicular(vector: Vector3, epsilon: scala.Float): scala.Boolean = {
    return com.badlogic.gdx.math.MathUtils.isZero(this.dot(vector), epsilon)
  }
  def hasSameDirection(vector: Vector3): scala.Boolean = {
    return this.dot(vector) > 0
  }
  def hasOppositeDirection(vector: Vector3): scala.Boolean = {
    return this.dot(vector) < 0
  }
  def lerp(target: Vector3, alpha: scala.Float): Vector3 = {
    this.x = this.x + (alpha * (target.x - this.x))
    this.y = this.y + (alpha * (target.y - this.y))
    this.z = this.z + (alpha * (target.z - this.z))
    return this
  }
  def interpolate(target: Vector3, alpha: scala.Float, interpolator: com.badlogic.gdx.math.Interpolation): Vector3 = {
    return this.lerp(target, interpolator.apply(0.0f, 1.0f, alpha))
  }
  def slerp(target: Vector3, alpha: scala.Float): Vector3 = {
    val dot: scala.Float = this.dot(target)
    if ((dot > 0.9995) || (dot < (-0.9995))) {
      return this.lerp(target, alpha)
    } else ()
    val theta0: scala.Float = java.lang.Math.acos(dot).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    val theta: scala.Float = theta0 * alpha
    val st: scala.Float = java.lang.Math.sin(theta).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    val tx: scala.Float = target.x - (this.x * dot)
    val ty: scala.Float = target.y - (this.y * dot)
    val tz: scala.Float = target.z - (this.z * dot)
    val l2: scala.Float = ((tx * tx) + (ty * ty)) + (tz * tz)
    val dl: scala.Float = st * (if (l2 < 1.0E-4f) 1.0f else 1.0f / java.lang.Math.sqrt(l2).asInstanceOf[scala.Float])
    return this.scl(java.lang.Math.cos(theta).asInstanceOf[scala.Float].asInstanceOf[scala.Float]).add(tx * dl, ty * dl, tz * dl).nor()
  }
  def toString(): java.lang.String = {
    return ((((("(" + this.x) + ",") + this.y) + ",") + this.z) + ")"
  }
  def fromString(v: java.lang.String): Vector3 = {
    val s0: scala.Int = v.indexOf(',', 1)
    val s1: scala.Int = v.indexOf(',', s0 + 1)
    if ((((s0 != (-1)) && (s1 != (-1))) && (v.charAt(0) == '(')) && (v.charAt(v.length() - 1) == ')')) {
      try {
        val x: scala.Float = java.lang.Float.parseFloat(v.substring(1, s0))
        val y: scala.Float = java.lang.Float.parseFloat(v.substring(s0 + 1, s1))
        val z: scala.Float = java.lang.Float.parseFloat(v.substring(s1 + 1, v.length() - 1))
        return this.set(x, y, z)
      } catch {
        case ex: java.lang.NumberFormatException => {
          ()
        }
      }
    } else ()
    throw new com.badlogic.gdx.utils.GdxRuntimeException("Malformed Vector3: " + v)
  }
  def limit(limit: scala.Float): Vector3 = {
    return this.limit2(limit * limit)
  }
  def limit2(limit2: scala.Float): Vector3 = {
    val len2: scala.Float = this.len2()
    if (len2 > limit2) {
      this.scl(java.lang.Math.sqrt(limit2 / len2).asInstanceOf[scala.Float].asInstanceOf[scala.Float])
    } else ()
    return this
  }
  def setLength(len: scala.Float): Vector3 = {
    return this.setLength2(len * len)
  }
  def setLength2(len2: scala.Float): Vector3 = {
    val oldLen2: scala.Float = this.len2()
    return if ((oldLen2 == 0) || (oldLen2 == len2)) this else this.scl(java.lang.Math.sqrt(len2 / oldLen2).asInstanceOf[scala.Float].asInstanceOf[scala.Float])
  }
  def clamp(min: scala.Float, max: scala.Float): Vector3 = {
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
  def hashCode(): scala.Int = {
    val prime: scala.Int = 31
    var result: scala.Int = 1
    result = (prime * result) + com.badlogic.gdx.utils.NumberUtils.floatToIntBits(this.x)
    result = (prime * result) + com.badlogic.gdx.utils.NumberUtils.floatToIntBits(this.y)
    result = (prime * result) + com.badlogic.gdx.utils.NumberUtils.floatToIntBits(this.z)
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
    val other: Vector3 = obj.asInstanceOf[Vector3].asInstanceOf[Vector3]
    if (com.badlogic.gdx.utils.NumberUtils.floatToIntBits(this.x) != com.badlogic.gdx.utils.NumberUtils.floatToIntBits(other.x)) {
      return false
    } else ()
    if (com.badlogic.gdx.utils.NumberUtils.floatToIntBits(this.y) != com.badlogic.gdx.utils.NumberUtils.floatToIntBits(other.y)) {
      return false
    } else ()
    if (com.badlogic.gdx.utils.NumberUtils.floatToIntBits(this.z) != com.badlogic.gdx.utils.NumberUtils.floatToIntBits(other.z)) {
      return false
    } else ()
    return true
  }
  def epsilonEquals(other: Vector3, epsilon: scala.Float): scala.Boolean = {
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
    return true
  }
  def epsilonEquals(x: scala.Float, y: scala.Float, z: scala.Float, epsilon: scala.Float): scala.Boolean = {
    if (java.lang.Math.abs(x - this.x) > epsilon) {
      return false
    } else ()
    if (java.lang.Math.abs(y - this.y) > epsilon) {
      return false
    } else ()
    if (java.lang.Math.abs(z - this.z) > epsilon) {
      return false
    } else ()
    return true
  }
  def epsilonEquals(other: Vector3): scala.Boolean = {
    return this.epsilonEquals(other, com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
  }
  def epsilonEquals(x: scala.Float, y: scala.Float, z: scala.Float): scala.Boolean = {
    return this.epsilonEquals(x, y, z, com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
  }
  def setZero(): Vector3 = {
    this.x = 0
    this.y = 0
    this.z = 0
    return this
  }
}
object Vector3 {
  private final val serialVersionUID: scala.Long = 3840054589595372522L
  final val X: Vector3 = new Vector3(1, 0, 0)
  final val Y: Vector3 = new Vector3(0, 1, 0)
  final val Z: Vector3 = new Vector3(0, 0, 1)
  final val Zero: Vector3 = new Vector3(0, 0, 0)
  final val One: Vector3 = new Vector3(1, 1, 1)
  private final val tmpMat: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
  def len(x: scala.Float, y: scala.Float, z: scala.Float): scala.Float = {
    return java.lang.Math.sqrt(((x * x) + (y * y)) + (z * z)).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  }
  def len2(x: scala.Float, y: scala.Float, z: scala.Float): scala.Float = {
    return ((x * x) + (y * y)) + (z * z)
  }
  def dst(x1: scala.Float, y1: scala.Float, z1: scala.Float, x2: scala.Float, y2: scala.Float, z2: scala.Float): scala.Float = {
    val a: scala.Float = x2 - x1
    val b: scala.Float = y2 - y1
    val c: scala.Float = z2 - z1
    return java.lang.Math.sqrt(((a * a) + (b * b)) + (c * c)).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  }
  def dst2(x1: scala.Float, y1: scala.Float, z1: scala.Float, x2: scala.Float, y2: scala.Float, z2: scala.Float): scala.Float = {
    val a: scala.Float = x2 - x1
    val b: scala.Float = y2 - y1
    val c: scala.Float = z2 - z1
    return ((a * a) + (b * b)) + (c * c)
  }
  def dot(x1: scala.Float, y1: scala.Float, z1: scala.Float, x2: scala.Float, y2: scala.Float, z2: scala.Float): scala.Float = {
    return ((x1 * x2) + (y1 * y2)) + (z1 * z2)
  }
}