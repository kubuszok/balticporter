package com.badlogic.gdx.math

class Quaternion extends java.io.Serializable {
  var x: scala.Float = 0.0f
  var y: scala.Float = 0.0f
  var z: scala.Float = 0.0f
  var w: scala.Float = 0.0f
  def this(x: scala.Float, y: scala.Float, z: scala.Float, w: scala.Float) = {
    this()
    this.set(x, y, z, w)
  }
  def this(quaternion: Quaternion) = {
    this()
    this.set(quaternion)
  }
  def this(axis: com.badlogic.gdx.math.Vector3, angle: scala.Float) = {
    this()
    this.set(axis, angle)
  }
  this.idt()
  def set(x: scala.Float, y: scala.Float, z: scala.Float, w: scala.Float): Quaternion = {
    this.x = x
    this.y = y
    this.z = z
    this.w = w
    return this
  }
  def set(quaternion: Quaternion): Quaternion = {
    return this.set(quaternion.x, quaternion.y, quaternion.z, quaternion.w)
  }
  def set(axis: com.badlogic.gdx.math.Vector3, angle: scala.Float): Quaternion = {
    return this.setFromAxis(axis.x, axis.y, axis.z, angle)
  }
  def cpy(): Quaternion = {
    return new Quaternion(this)
  }
  def len(): scala.Float = {
    return java.lang.Math.sqrt((((this.x * this.x) + (this.y * this.y)) + (this.z * this.z)) + (this.w * this.w)).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  }
  def toString(): java.lang.String = {
    return ((((((("[" + this.x) + "|") + this.y) + "|") + this.z) + "|") + this.w) + "]"
  }
  def setEulerAngles(yaw: scala.Float, pitch: scala.Float, roll: scala.Float): Quaternion = {
    return this.setEulerAnglesRad(yaw * com.badlogic.gdx.math.MathUtils.degreesToRadians, pitch * com.badlogic.gdx.math.MathUtils.degreesToRadians, roll * com.badlogic.gdx.math.MathUtils.degreesToRadians)
  }
  def setEulerAnglesRad(yaw: scala.Float, pitch: scala.Float, roll: scala.Float): Quaternion = {
    val hr: scala.Float = roll * 0.5f
    val shr: scala.Float = java.lang.Math.sin(hr).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    val chr: scala.Float = java.lang.Math.cos(hr).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    val hp: scala.Float = pitch * 0.5f
    val shp: scala.Float = java.lang.Math.sin(hp).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    val chp: scala.Float = java.lang.Math.cos(hp).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    val hy: scala.Float = yaw * 0.5f
    val shy: scala.Float = java.lang.Math.sin(hy).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    val chy: scala.Float = java.lang.Math.cos(hy).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    val chy_shp: scala.Float = chy * shp
    val shy_chp: scala.Float = shy * chp
    val chy_chp: scala.Float = chy * chp
    val shy_shp: scala.Float = shy * shp
    this.x = (chy_shp * chr) + (shy_chp * shr)
    this.y = (shy_chp * chr) - (chy_shp * shr)
    this.z = (chy_chp * shr) - (shy_shp * chr)
    this.w = (chy_chp * chr) + (shy_shp * shr)
    return this
  }
  def getGimbalPole(): scala.Int = {
    val t: scala.Float = (this.y * this.x) + (this.z * this.w)
    return if (t > 0.499f) 1 else if (t < (-0.499f)) -1 else 0
  }
  def getRollRad(): scala.Float = {
    val pole: scala.Int = this.getGimbalPole()
    return if (pole == 0) com.badlogic.gdx.math.MathUtils.atan2(2.0f * ((this.w * this.z) + (this.y * this.x)), 1.0f - (2.0f * ((this.x * this.x) + (this.z * this.z)))) else (pole.asInstanceOf[scala.Float] * 2.0f) * com.badlogic.gdx.math.MathUtils.atan2(this.y, this.w)
  }
  def getRoll(): scala.Float = {
    return this.getRollRad() * com.badlogic.gdx.math.MathUtils.radiansToDegrees
  }
  def getPitchRad(): scala.Float = {
    val pole: scala.Int = this.getGimbalPole()
    return if (pole == 0) java.lang.Math.asin(com.badlogic.gdx.math.MathUtils.clamp(2.0f * ((this.w * this.x) - (this.z * this.y)), -1.0f, 1.0f)).asInstanceOf[scala.Float] else (pole.asInstanceOf[scala.Float] * com.badlogic.gdx.math.MathUtils.PI) * 0.5f
  }
  def getPitch(): scala.Float = {
    return this.getPitchRad() * com.badlogic.gdx.math.MathUtils.radiansToDegrees
  }
  def getYawRad(): scala.Float = {
    return if (this.getGimbalPole() == 0) com.badlogic.gdx.math.MathUtils.atan2(2.0f * ((this.y * this.w) + (this.x * this.z)), 1.0f - (2.0f * ((this.y * this.y) + (this.x * this.x)))) else 0.0f
  }
  def getYaw(): scala.Float = {
    return this.getYawRad() * com.badlogic.gdx.math.MathUtils.radiansToDegrees
  }
  def len2(): scala.Float = {
    return (((this.x * this.x) + (this.y * this.y)) + (this.z * this.z)) + (this.w * this.w)
  }
  def nor(): Quaternion = {
    var len: scala.Float = this.len2()
    if ((len != 0.0f) && (!com.badlogic.gdx.math.MathUtils.isEqual(len, 1.0f))) {
      len = java.lang.Math.sqrt(len).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
      this.w = this.w / len
      this.x = this.x / len
      this.y = this.y / len
      this.z = this.z / len
    } else ()
    return this
  }
  def conjugate(): Quaternion = {
    this.x = -this.x
    this.y = -this.y
    this.z = -this.z
    return this
  }
  def transform(v: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    Quaternion.tmp2.set(this)
    Quaternion.tmp2.conjugate()
    Quaternion.tmp2.mulLeft(Quaternion.tmp1.set(v.x, v.y, v.z, 0)).mulLeft(this)
    v.x = Quaternion.tmp2.x
    v.y = Quaternion.tmp2.y
    v.z = Quaternion.tmp2.z
    return v
  }
  def mul(other: Quaternion): Quaternion = {
    val newX: scala.Float = (((this.w * other.x) + (this.x * other.w)) + (this.y * other.z)) - (this.z * other.y)
    val newY: scala.Float = (((this.w * other.y) + (this.y * other.w)) + (this.z * other.x)) - (this.x * other.z)
    val newZ: scala.Float = (((this.w * other.z) + (this.z * other.w)) + (this.x * other.y)) - (this.y * other.x)
    val newW: scala.Float = (((this.w * other.w) - (this.x * other.x)) - (this.y * other.y)) - (this.z * other.z)
    this.x = newX
    this.y = newY
    this.z = newZ
    this.w = newW
    return this
  }
  def mul(x: scala.Float, y: scala.Float, z: scala.Float, w: scala.Float): Quaternion = {
    val newX: scala.Float = (((this.w * x) + (this.x * w)) + (this.y * z)) - (this.z * y)
    val newY: scala.Float = (((this.w * y) + (this.y * w)) + (this.z * x)) - (this.x * z)
    val newZ: scala.Float = (((this.w * z) + (this.z * w)) + (this.x * y)) - (this.y * x)
    val newW: scala.Float = (((this.w * w) - (this.x * x)) - (this.y * y)) - (this.z * z)
    this.x = newX
    this.y = newY
    this.z = newZ
    this.w = newW
    return this
  }
  def mulLeft(other: Quaternion): Quaternion = {
    val newX: scala.Float = (((other.w * this.x) + (other.x * this.w)) + (other.y * this.z)) - (other.z * this.y)
    val newY: scala.Float = (((other.w * this.y) + (other.y * this.w)) + (other.z * this.x)) - (other.x * this.z)
    val newZ: scala.Float = (((other.w * this.z) + (other.z * this.w)) + (other.x * this.y)) - (other.y * this.x)
    val newW: scala.Float = (((other.w * this.w) - (other.x * this.x)) - (other.y * this.y)) - (other.z * this.z)
    this.x = newX
    this.y = newY
    this.z = newZ
    this.w = newW
    return this
  }
  def mulLeft(x: scala.Float, y: scala.Float, z: scala.Float, w: scala.Float): Quaternion = {
    val newX: scala.Float = (((w * this.x) + (x * this.w)) + (y * this.z)) - (z * this.y)
    val newY: scala.Float = (((w * this.y) + (y * this.w)) + (z * this.x)) - (x * this.z)
    val newZ: scala.Float = (((w * this.z) + (z * this.w)) + (x * this.y)) - (y * this.x)
    val newW: scala.Float = (((w * this.w) - (x * this.x)) - (y * this.y)) - (z * this.z)
    this.x = newX
    this.y = newY
    this.z = newZ
    this.w = newW
    return this
  }
  def add(quaternion: Quaternion): Quaternion = {
    this.x = this.x + quaternion.x
    this.y = this.y + quaternion.y
    this.z = this.z + quaternion.z
    this.w = this.w + quaternion.w
    return this
  }
  def add(qx: scala.Float, qy: scala.Float, qz: scala.Float, qw: scala.Float): Quaternion = {
    this.x = this.x + qx
    this.y = this.y + qy
    this.z = this.z + qz
    this.w = this.w + qw
    return this
  }
  def toMatrix(matrix: scala.Array[scala.Float]): scala.Unit = {
    val xx: scala.Float = this.x * this.x
    val xy: scala.Float = this.x * this.y
    val xz: scala.Float = this.x * this.z
    val xw: scala.Float = this.x * this.w
    val yy: scala.Float = this.y * this.y
    val yz: scala.Float = this.y * this.z
    val yw: scala.Float = this.y * this.w
    val zz: scala.Float = this.z * this.z
    val zw: scala.Float = this.z * this.w
    matrix(com.badlogic.gdx.math.Matrix4.M00) = 1 - (2 * (yy + zz))
    matrix(com.badlogic.gdx.math.Matrix4.M01) = 2 * (xy - zw)
    matrix(com.badlogic.gdx.math.Matrix4.M02) = 2 * (xz + yw)
    matrix(com.badlogic.gdx.math.Matrix4.M03) = 0
    matrix(com.badlogic.gdx.math.Matrix4.M10) = 2 * (xy + zw)
    matrix(com.badlogic.gdx.math.Matrix4.M11) = 1 - (2 * (xx + zz))
    matrix(com.badlogic.gdx.math.Matrix4.M12) = 2 * (yz - xw)
    matrix(com.badlogic.gdx.math.Matrix4.M13) = 0
    matrix(com.badlogic.gdx.math.Matrix4.M20) = 2 * (xz - yw)
    matrix(com.badlogic.gdx.math.Matrix4.M21) = 2 * (yz + xw)
    matrix(com.badlogic.gdx.math.Matrix4.M22) = 1 - (2 * (xx + yy))
    matrix(com.badlogic.gdx.math.Matrix4.M23) = 0
    matrix(com.badlogic.gdx.math.Matrix4.M30) = 0
    matrix(com.badlogic.gdx.math.Matrix4.M31) = 0
    matrix(com.badlogic.gdx.math.Matrix4.M32) = 0
    matrix(com.badlogic.gdx.math.Matrix4.M33) = 1
  }
  def idt(): Quaternion = {
    return this.set(0, 0, 0, 1)
  }
  def isIdentity(): scala.Boolean = {
    return ((com.badlogic.gdx.math.MathUtils.isZero(this.x) && com.badlogic.gdx.math.MathUtils.isZero(this.y)) && com.badlogic.gdx.math.MathUtils.isZero(this.z)) && com.badlogic.gdx.math.MathUtils.isEqual(this.w, 1.0f)
  }
  def isIdentity(tolerance: scala.Float): scala.Boolean = {
    return ((com.badlogic.gdx.math.MathUtils.isZero(this.x, tolerance) && com.badlogic.gdx.math.MathUtils.isZero(this.y, tolerance)) && com.badlogic.gdx.math.MathUtils.isZero(this.z, tolerance)) && com.badlogic.gdx.math.MathUtils.isEqual(this.w, 1.0f, tolerance)
  }
  def setFromAxis(axis: com.badlogic.gdx.math.Vector3, degrees: scala.Float): Quaternion = {
    return this.setFromAxis(axis.x, axis.y, axis.z, degrees)
  }
  def setFromAxisRad(axis: com.badlogic.gdx.math.Vector3, radians: scala.Float): Quaternion = {
    return this.setFromAxisRad(axis.x, axis.y, axis.z, radians)
  }
  def setFromAxis(x: scala.Float, y: scala.Float, z: scala.Float, degrees: scala.Float): Quaternion = {
    return this.setFromAxisRad(x, y, z, degrees * com.badlogic.gdx.math.MathUtils.degreesToRadians)
  }
  def setFromAxisRad(x: scala.Float, y: scala.Float, z: scala.Float, radians: scala.Float): Quaternion = {
    var d: scala.Float = com.badlogic.gdx.math.Vector3.len(x, y, z)
    if (d == 0.0f) {
      return this.idt()
    } else ()
    d = 1.0f / d
    val l_ang: scala.Float = if (radians < 0) com.badlogic.gdx.math.MathUtils.PI2 - ((-radians) % com.badlogic.gdx.math.MathUtils.PI2) else radians % com.badlogic.gdx.math.MathUtils.PI2
    val l_sin: scala.Float = java.lang.Math.sin(l_ang / 2).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    val l_cos: scala.Float = java.lang.Math.cos(l_ang / 2).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    return this.set((d * x) * l_sin, (d * y) * l_sin, (d * z) * l_sin, l_cos).nor()
  }
  def setFromMatrix(normalizeAxes: scala.Boolean, matrix: com.badlogic.gdx.math.Matrix4): Quaternion = {
    return this.setFromAxes(normalizeAxes, matrix.`val`(com.badlogic.gdx.math.Matrix4.M00), matrix.`val`(com.badlogic.gdx.math.Matrix4.M01), matrix.`val`(com.badlogic.gdx.math.Matrix4.M02), matrix.`val`(com.badlogic.gdx.math.Matrix4.M10), matrix.`val`(com.badlogic.gdx.math.Matrix4.M11), matrix.`val`(com.badlogic.gdx.math.Matrix4.M12), matrix.`val`(com.badlogic.gdx.math.Matrix4.M20), matrix.`val`(com.badlogic.gdx.math.Matrix4.M21), matrix.`val`(com.badlogic.gdx.math.Matrix4.M22))
  }
  def setFromMatrix(matrix: com.badlogic.gdx.math.Matrix4): Quaternion = {
    return this.setFromMatrix(false, matrix)
  }
  def setFromMatrix(normalizeAxes: scala.Boolean, matrix: com.badlogic.gdx.math.Matrix3): Quaternion = {
    return this.setFromAxes(normalizeAxes, matrix.`val`(com.badlogic.gdx.math.Matrix3.M00), matrix.`val`(com.badlogic.gdx.math.Matrix3.M01), matrix.`val`(com.badlogic.gdx.math.Matrix3.M02), matrix.`val`(com.badlogic.gdx.math.Matrix3.M10), matrix.`val`(com.badlogic.gdx.math.Matrix3.M11), matrix.`val`(com.badlogic.gdx.math.Matrix3.M12), matrix.`val`(com.badlogic.gdx.math.Matrix3.M20), matrix.`val`(com.badlogic.gdx.math.Matrix3.M21), matrix.`val`(com.badlogic.gdx.math.Matrix3.M22))
  }
  def setFromMatrix(matrix: com.badlogic.gdx.math.Matrix3): Quaternion = {
    return this.setFromMatrix(false, matrix)
  }
  def setFromAxes(xx: scala.Float, xy: scala.Float, xz: scala.Float, yx: scala.Float, yy: scala.Float, yz: scala.Float, zx: scala.Float, zy: scala.Float, zz: scala.Float): Quaternion = {
    return this.setFromAxes(false, xx, xy, xz, yx, yy, yz, zx, zy, zz)
  }
  def setFromAxes(normalizeAxes: scala.Boolean, xx$arg: scala.Float, xy$arg: scala.Float, xz$arg: scala.Float, yx$arg: scala.Float, yy$arg: scala.Float, yz$arg: scala.Float, zx$arg: scala.Float, zy$arg: scala.Float, zz$arg: scala.Float): Quaternion = {
    var xx: scala.Float = xx$arg
    var xy: scala.Float = xy$arg
    var xz: scala.Float = xz$arg
    var yx: scala.Float = yx$arg
    var yy: scala.Float = yy$arg
    var yz: scala.Float = yz$arg
    var zx: scala.Float = zx$arg
    var zy: scala.Float = zy$arg
    var zz: scala.Float = zz$arg
    if (normalizeAxes) {
      val lx: scala.Float = 1.0f / com.badlogic.gdx.math.Vector3.len(xx, xy, xz)
      val ly: scala.Float = 1.0f / com.badlogic.gdx.math.Vector3.len(yx, yy, yz)
      val lz: scala.Float = 1.0f / com.badlogic.gdx.math.Vector3.len(zx, zy, zz)
      xx = xx * lx
      xy = xy * lx
      xz = xz * lx
      yx = yx * ly
      yy = yy * ly
      yz = yz * ly
      zx = zx * lz
      zy = zy * lz
      zz = zz * lz
    } else ()
    val t: scala.Float = (xx + yy) + zz
    if (t >= 0) {
      var s: scala.Float = java.lang.Math.sqrt(t + 1).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
      this.w = 0.5f * s
      s = 0.5f / s
      this.x = (zy - yz) * s
      this.y = (xz - zx) * s
      this.z = (yx - xy) * s
    } else {
      if ((xx > yy) && (xx > zz)) {
        var s: scala.Float = java.lang.Math.sqrt(((1.0 + xx) - yy) - zz).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
        this.x = s * 0.5f
        s = 0.5f / s
        this.y = (yx + xy) * s
        this.z = (xz + zx) * s
        this.w = (zy - yz) * s
      } else {
        if (yy > zz) {
          var s: scala.Float = java.lang.Math.sqrt(((1.0 + yy) - xx) - zz).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
          this.y = s * 0.5f
          s = 0.5f / s
          this.x = (yx + xy) * s
          this.z = (zy + yz) * s
          this.w = (xz - zx) * s
        } else {
          var s: scala.Float = java.lang.Math.sqrt(((1.0 + zz) - xx) - yy).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
          this.z = s * 0.5f
          s = 0.5f / s
          this.x = (xz + zx) * s
          this.y = (zy + yz) * s
          this.w = (yx - xy) * s
        }
      }
    }
    return this
  }
  def setFromCross(v1: com.badlogic.gdx.math.Vector3, v2: com.badlogic.gdx.math.Vector3): Quaternion = {
    val dot: scala.Float = com.badlogic.gdx.math.MathUtils.clamp(v1.dot(v2), -1.0f, 1.0f)
    val angle: scala.Float = java.lang.Math.acos(dot).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    return this.setFromAxisRad((v1.y * v2.z) - (v1.z * v2.y), (v1.z * v2.x) - (v1.x * v2.z), (v1.x * v2.y) - (v1.y * v2.x), angle)
  }
  def setFromCross(x1: scala.Float, y1: scala.Float, z1: scala.Float, x2: scala.Float, y2: scala.Float, z2: scala.Float): Quaternion = {
    val dot: scala.Float = com.badlogic.gdx.math.MathUtils.clamp(com.badlogic.gdx.math.Vector3.dot(x1, y1, z1, x2, y2, z2), -1.0f, 1.0f)
    val angle: scala.Float = java.lang.Math.acos(dot).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    return this.setFromAxisRad((y1 * z2) - (z1 * y2), (z1 * x2) - (x1 * z2), (x1 * y2) - (y1 * x2), angle)
  }
  def slerp(`end`: Quaternion, alpha: scala.Float): Quaternion = {
    val d: scala.Float = (((this.x * `end`.x) + (this.y * `end`.y)) + (this.z * `end`.z)) + (this.w * `end`.w)
    val absDot: scala.Float = if (d < 0.0f) -d else d
    var scale0: scala.Float = 1.0f - alpha
    var scale1: scala.Float = alpha
    if ((1 - absDot) > 0.1) {
      val angle: scala.Float = java.lang.Math.acos(absDot).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
      val invSinTheta: scala.Float = 1.0f / java.lang.Math.sin(angle).asInstanceOf[scala.Float]
      scale0 = java.lang.Math.sin((1.0f - alpha) * angle).asInstanceOf[scala.Float] * invSinTheta
      scale1 = java.lang.Math.sin(alpha * angle).asInstanceOf[scala.Float] * invSinTheta
    } else ()
    if (d < 0.0f) {
      scale1 = -scale1
    } else ()
    this.x = (scale0 * this.x) + (scale1 * `end`.x)
    this.y = (scale0 * this.y) + (scale1 * `end`.y)
    this.z = (scale0 * this.z) + (scale1 * `end`.z)
    this.w = (scale0 * this.w) + (scale1 * `end`.w)
    return this
  }
  def slerp(q: scala.Array[Quaternion]): Quaternion = {
    val w: scala.Float = 1.0f / q.length
    this.set(q(0)).exp(w);
    { var i: scala.Int = 1; while (i < q.length) { {
      this.mul(Quaternion.tmp1.set(q(i)).exp(w))
    }; i = i + 1 } }
    this.nor()
    return this
  }
  def slerp(q: scala.Array[Quaternion], w: scala.Array[scala.Float]): Quaternion = {
    this.set(q(0)).exp(w(0));
    { var i: scala.Int = 1; while (i < q.length) { {
      this.mul(Quaternion.tmp1.set(q(i)).exp(w(i)))
    }; i = i + 1 } }
    this.nor()
    return this
  }
  def exp(alpha: scala.Float): Quaternion = {
    val norm: scala.Float = this.len()
    val normExp: scala.Float = java.lang.Math.pow(norm, alpha).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    val theta: scala.Float = java.lang.Math.acos(this.w / norm).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    var coeff: scala.Float = 0
    if (java.lang.Math.abs(theta) < 0.001) {
      coeff = (normExp * alpha) / norm
    } else {
      coeff = ((normExp * java.lang.Math.sin(alpha * theta)) / (norm * java.lang.Math.sin(theta))).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    }
    this.w = (normExp * java.lang.Math.cos(alpha * theta)).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    this.x = this.x * coeff
    this.y = this.y * coeff
    this.z = this.z * coeff
    this.nor()
    return this
  }
  def hashCode(): scala.Int = {
    val prime: scala.Int = 31
    var result: scala.Int = 1
    result = (prime * result) + com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.w)
    result = (prime * result) + com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.x)
    result = (prime * result) + com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.y)
    result = (prime * result) + com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.z)
    return result
  }
  def equals(obj: java.lang.Object): scala.Boolean = {
    if (this == obj) {
      return true
    } else ()
    if (obj == null) {
      return false
    } else ()
    if (!obj.isInstanceOf[Quaternion]) {
      return false
    } else ()
    val other: Quaternion = obj.asInstanceOf[Quaternion].asInstanceOf[Quaternion]
    return (((com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.w) == com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(other.w)) && (com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.x) == com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(other.x))) && (com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.y) == com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(other.y))) && (com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.z) == com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(other.z))
  }
  def dot(other: Quaternion): scala.Float = {
    return (((this.x * other.x) + (this.y * other.y)) + (this.z * other.z)) + (this.w * other.w)
  }
  def dot(x: scala.Float, y: scala.Float, z: scala.Float, w: scala.Float): scala.Float = {
    return (((this.x * x) + (this.y * y)) + (this.z * z)) + (this.w * w)
  }
  def mul(scalar: scala.Float): Quaternion = {
    this.x = this.x * scalar
    this.y = this.y * scalar
    this.z = this.z * scalar
    this.w = this.w * scalar
    return this
  }
  def getAxisAngle(axis: com.badlogic.gdx.math.Vector3): scala.Float = {
    return this.getAxisAngleRad(axis) * com.badlogic.gdx.math.MathUtils.radiansToDegrees
  }
  def getAxisAngleRad(axis: com.badlogic.gdx.math.Vector3): scala.Float = {
    if (this.w > 1) {
      this.nor()
    } else ()
    val angle: scala.Float = (2.0 * java.lang.Math.acos(this.w)).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    val s: scala.Double = java.lang.Math.sqrt(1 - (this.w * this.w))
    if (s < com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR) {
      axis.x = this.x
      axis.y = this.y
      axis.z = this.z
    } else {
      axis.x = (this.x / s).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
      axis.y = (this.y / s).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
      axis.z = (this.z / s).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    }
    return angle
  }
  def getAngleRad(): scala.Float = {
    return (2.0 * java.lang.Math.acos(if (this.w > 1) this.w / this.len() else this.w)).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  }
  def getAngle(): scala.Float = {
    return this.getAngleRad() * com.badlogic.gdx.math.MathUtils.radiansToDegrees
  }
  def getSwingTwist(axisX: scala.Float, axisY: scala.Float, axisZ: scala.Float, swing: Quaternion, twist: Quaternion): scala.Unit = {
    val d: scala.Float = com.badlogic.gdx.math.Vector3.dot(this.x, this.y, this.z, axisX, axisY, axisZ)
    twist.set(axisX * d, axisY * d, axisZ * d, this.w).nor()
    if (d < 0) {
      twist.mul(-1.0f)
    } else ()
    swing.set(twist).conjugate().mulLeft(this)
  }
  def getSwingTwist(axis: com.badlogic.gdx.math.Vector3, swing: Quaternion, twist: Quaternion): scala.Unit = {
    this.getSwingTwist(axis.x, axis.y, axis.z, swing, twist)
  }
  def getAngleAroundRad(axisX: scala.Float, axisY: scala.Float, axisZ: scala.Float): scala.Float = {
    val d: scala.Float = com.badlogic.gdx.math.Vector3.dot(this.x, this.y, this.z, axisX, axisY, axisZ)
    val l2: scala.Float = Quaternion.len2(axisX * d, axisY * d, axisZ * d, this.w)
    return if (com.badlogic.gdx.math.MathUtils.isZero(l2)) 0.0f else (2.0 * java.lang.Math.acos(com.badlogic.gdx.math.MathUtils.clamp(((if (d < 0) -this.w else this.w) / java.lang.Math.sqrt(l2)).asInstanceOf[scala.Float].asInstanceOf[scala.Float], -1.0f, 1.0f))).asInstanceOf[scala.Float]
  }
  def getAngleAroundRad(axis: com.badlogic.gdx.math.Vector3): scala.Float = {
    return this.getAngleAroundRad(axis.x, axis.y, axis.z)
  }
  def getAngleAround(axisX: scala.Float, axisY: scala.Float, axisZ: scala.Float): scala.Float = {
    return this.getAngleAroundRad(axisX, axisY, axisZ) * com.badlogic.gdx.math.MathUtils.radiansToDegrees
  }
  def getAngleAround(axis: com.badlogic.gdx.math.Vector3): scala.Float = {
    return this.getAngleAround(axis.x, axis.y, axis.z)
  }
}
object Quaternion {
  private final val serialVersionUID: scala.Long = -7661875440774897168L
  private var tmp1: Quaternion = new Quaternion(0, 0, 0, 0)
  private var tmp2: Quaternion = new Quaternion(0, 0, 0, 0)
  final def len(x: scala.Float, y: scala.Float, z: scala.Float, w: scala.Float): scala.Float = {
    return java.lang.Math.sqrt((((x * x) + (y * y)) + (z * z)) + (w * w)).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  }
  final def len2(x: scala.Float, y: scala.Float, z: scala.Float, w: scala.Float): scala.Float = {
    return (((x * x) + (y * y)) + (z * z)) + (w * w)
  }
  final def dot(x1: scala.Float, y1: scala.Float, z1: scala.Float, w1: scala.Float, x2: scala.Float, y2: scala.Float, z2: scala.Float, w2: scala.Float): scala.Float = {
    return (((x1 * x2) + (y1 * y2)) + (z1 * z2)) + (w1 * w2)
  }
}