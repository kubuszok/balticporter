package com.badlogic.gdx.math

class Matrix4 extends java.io.Serializable {
  final val `val`: scala.Array[scala.Float] = new scala.Array[scala.Float](16)
  def this(position: com.badlogic.gdx.math.Vector3, rotation: com.badlogic.gdx.math.Quaternion, scale: com.badlogic.gdx.math.Vector3) = {
    this()
    this.set(position, rotation, scale)
  }
  def this(matrix: Matrix4) = {
    this()
    this.set(matrix)
  }
  def this(values: scala.Array[scala.Float]) = {
    this()
    this.set(values)
  }
  def this(quaternion: com.badlogic.gdx.math.Quaternion) = {
    this()
    this.set(quaternion)
  }
  def this() = {
    this()
    this.`val`(Matrix4.M00) = 1.0f
    this.`val`(Matrix4.M11) = 1.0f
    this.`val`(Matrix4.M22) = 1.0f
    this.`val`(Matrix4.M33) = 1.0f
  }
  def set(matrix: Matrix4): Matrix4 = {
    return this.set(matrix.`val`)
  }
  def set(values: scala.Array[scala.Float]): Matrix4 = {
    java.lang.System.arraycopy(values, 0, this.`val`, 0, this.`val`.length)
    return this
  }
  def set(quaternion: com.badlogic.gdx.math.Quaternion): Matrix4 = {
    return this.set(quaternion.x, quaternion.y, quaternion.z, quaternion.w)
  }
  def set(quaternionX: scala.Float, quaternionY: scala.Float, quaternionZ: scala.Float, quaternionW: scala.Float): Matrix4 = {
    return this.set(0.0f, 0.0f, 0.0f, quaternionX, quaternionY, quaternionZ, quaternionW)
  }
  def set(position: com.badlogic.gdx.math.Vector3, orientation: com.badlogic.gdx.math.Quaternion): Matrix4 = {
    return this.set(position.x, position.y, position.z, orientation.x, orientation.y, orientation.z, orientation.w)
  }
  def set(translationX: scala.Float, translationY: scala.Float, translationZ: scala.Float, quaternionX: scala.Float, quaternionY: scala.Float, quaternionZ: scala.Float, quaternionW: scala.Float): Matrix4 = {
    val xs: scala.Float = quaternionX * 2.0f
    val ys: scala.Float = quaternionY * 2.0f
    val zs: scala.Float = quaternionZ * 2.0f
    val wx: scala.Float = quaternionW * xs
    val wy: scala.Float = quaternionW * ys
    val wz: scala.Float = quaternionW * zs
    val xx: scala.Float = quaternionX * xs
    val xy: scala.Float = quaternionX * ys
    val xz: scala.Float = quaternionX * zs
    val yy: scala.Float = quaternionY * ys
    val yz: scala.Float = quaternionY * zs
    val zz: scala.Float = quaternionZ * zs
    this.`val`(Matrix4.M00) = 1.0f - (yy + zz)
    this.`val`(Matrix4.M01) = xy - wz
    this.`val`(Matrix4.M02) = xz + wy
    this.`val`(Matrix4.M03) = translationX
    this.`val`(Matrix4.M10) = xy + wz
    this.`val`(Matrix4.M11) = 1.0f - (xx + zz)
    this.`val`(Matrix4.M12) = yz - wx
    this.`val`(Matrix4.M13) = translationY
    this.`val`(Matrix4.M20) = xz - wy
    this.`val`(Matrix4.M21) = yz + wx
    this.`val`(Matrix4.M22) = 1.0f - (xx + yy)
    this.`val`(Matrix4.M23) = translationZ
    this.`val`(Matrix4.M30) = 0.0f
    this.`val`(Matrix4.M31) = 0.0f
    this.`val`(Matrix4.M32) = 0.0f
    this.`val`(Matrix4.M33) = 1.0f
    return this
  }
  def set(position: com.badlogic.gdx.math.Vector3, orientation: com.badlogic.gdx.math.Quaternion, scale: com.badlogic.gdx.math.Vector3): Matrix4 = {
    return this.set(position.x, position.y, position.z, orientation.x, orientation.y, orientation.z, orientation.w, scale.x, scale.y, scale.z)
  }
  def set(translationX: scala.Float, translationY: scala.Float, translationZ: scala.Float, quaternionX: scala.Float, quaternionY: scala.Float, quaternionZ: scala.Float, quaternionW: scala.Float, scaleX: scala.Float, scaleY: scala.Float, scaleZ: scala.Float): Matrix4 = {
    val xs: scala.Float = quaternionX * 2.0f
    val ys: scala.Float = quaternionY * 2.0f
    val zs: scala.Float = quaternionZ * 2.0f
    val wx: scala.Float = quaternionW * xs
    val wy: scala.Float = quaternionW * ys
    val wz: scala.Float = quaternionW * zs
    val xx: scala.Float = quaternionX * xs
    val xy: scala.Float = quaternionX * ys
    val xz: scala.Float = quaternionX * zs
    val yy: scala.Float = quaternionY * ys
    val yz: scala.Float = quaternionY * zs
    val zz: scala.Float = quaternionZ * zs
    this.`val`(Matrix4.M00) = scaleX * (1.0f - (yy + zz))
    this.`val`(Matrix4.M01) = scaleY * (xy - wz)
    this.`val`(Matrix4.M02) = scaleZ * (xz + wy)
    this.`val`(Matrix4.M03) = translationX
    this.`val`(Matrix4.M10) = scaleX * (xy + wz)
    this.`val`(Matrix4.M11) = scaleY * (1.0f - (xx + zz))
    this.`val`(Matrix4.M12) = scaleZ * (yz - wx)
    this.`val`(Matrix4.M13) = translationY
    this.`val`(Matrix4.M20) = scaleX * (xz - wy)
    this.`val`(Matrix4.M21) = scaleY * (yz + wx)
    this.`val`(Matrix4.M22) = scaleZ * (1.0f - (xx + yy))
    this.`val`(Matrix4.M23) = translationZ
    this.`val`(Matrix4.M30) = 0.0f
    this.`val`(Matrix4.M31) = 0.0f
    this.`val`(Matrix4.M32) = 0.0f
    this.`val`(Matrix4.M33) = 1.0f
    return this
  }
  def set(xAxis: com.badlogic.gdx.math.Vector3, yAxis: com.badlogic.gdx.math.Vector3, zAxis: com.badlogic.gdx.math.Vector3, pos: com.badlogic.gdx.math.Vector3): Matrix4 = {
    this.`val`(Matrix4.M00) = xAxis.x
    this.`val`(Matrix4.M01) = xAxis.y
    this.`val`(Matrix4.M02) = xAxis.z
    this.`val`(Matrix4.M10) = yAxis.x
    this.`val`(Matrix4.M11) = yAxis.y
    this.`val`(Matrix4.M12) = yAxis.z
    this.`val`(Matrix4.M20) = zAxis.x
    this.`val`(Matrix4.M21) = zAxis.y
    this.`val`(Matrix4.M22) = zAxis.z
    this.`val`(Matrix4.M03) = pos.x
    this.`val`(Matrix4.M13) = pos.y
    this.`val`(Matrix4.M23) = pos.z
    this.`val`(Matrix4.M30) = 0.0f
    this.`val`(Matrix4.M31) = 0.0f
    this.`val`(Matrix4.M32) = 0.0f
    this.`val`(Matrix4.M33) = 1.0f
    return this
  }
  def cpy(): Matrix4 = {
    return new Matrix4(this)
  }
  def trn(vector: com.badlogic.gdx.math.Vector3): Matrix4 = {
    this.`val`(Matrix4.M03) = this.`val`(Matrix4.M03) + vector.x
    this.`val`(Matrix4.M13) = this.`val`(Matrix4.M13) + vector.y
    this.`val`(Matrix4.M23) = this.`val`(Matrix4.M23) + vector.z
    return this
  }
  def trn(x: scala.Float, y: scala.Float, z: scala.Float): Matrix4 = {
    this.`val`(Matrix4.M03) = this.`val`(Matrix4.M03) + x
    this.`val`(Matrix4.M13) = this.`val`(Matrix4.M13) + y
    this.`val`(Matrix4.M23) = this.`val`(Matrix4.M23) + z
    return this
  }
  def getValues(): scala.Array[scala.Float] = {
    return this.`val`
  }
  def mul(matrix: Matrix4): Matrix4 = {
    Matrix4.mul(this.`val`, matrix.`val`)
    return this
  }
  def mulLeft(matrix: Matrix4): Matrix4 = {
    Matrix4.tmpMat.set(matrix)
    Matrix4.mul(Matrix4.tmpMat.`val`, this.`val`)
    return this.set(Matrix4.tmpMat)
  }
  def tra(): Matrix4 = {
    val m01: scala.Float = this.`val`(Matrix4.M01)
    val m02: scala.Float = this.`val`(Matrix4.M02)
    val m03: scala.Float = this.`val`(Matrix4.M03)
    val m12: scala.Float = this.`val`(Matrix4.M12)
    val m13: scala.Float = this.`val`(Matrix4.M13)
    val m23: scala.Float = this.`val`(Matrix4.M23)
    this.`val`(Matrix4.M01) = this.`val`(Matrix4.M10)
    this.`val`(Matrix4.M02) = this.`val`(Matrix4.M20)
    this.`val`(Matrix4.M03) = this.`val`(Matrix4.M30)
    this.`val`(Matrix4.M10) = m01
    this.`val`(Matrix4.M12) = this.`val`(Matrix4.M21)
    this.`val`(Matrix4.M13) = this.`val`(Matrix4.M31)
    this.`val`(Matrix4.M20) = m02
    this.`val`(Matrix4.M21) = m12
    this.`val`(Matrix4.M23) = this.`val`(Matrix4.M32)
    this.`val`(Matrix4.M30) = m03
    this.`val`(Matrix4.M31) = m13
    this.`val`(Matrix4.M32) = m23
    return this
  }
  def idt(): Matrix4 = {
    this.`val`(Matrix4.M00) = 1.0f
    this.`val`(Matrix4.M01) = 0.0f
    this.`val`(Matrix4.M02) = 0.0f
    this.`val`(Matrix4.M03) = 0.0f
    this.`val`(Matrix4.M10) = 0.0f
    this.`val`(Matrix4.M11) = 1.0f
    this.`val`(Matrix4.M12) = 0.0f
    this.`val`(Matrix4.M13) = 0.0f
    this.`val`(Matrix4.M20) = 0.0f
    this.`val`(Matrix4.M21) = 0.0f
    this.`val`(Matrix4.M22) = 1.0f
    this.`val`(Matrix4.M23) = 0.0f
    this.`val`(Matrix4.M30) = 0.0f
    this.`val`(Matrix4.M31) = 0.0f
    this.`val`(Matrix4.M32) = 0.0f
    this.`val`(Matrix4.M33) = 1.0f
    return this
  }
  def inv(): Matrix4 = {
    val l_det: scala.Float = (((((((((((((((((((((((((this.`val`(Matrix4.M30) * this.`val`(Matrix4.M21)) * this.`val`(Matrix4.M12)) * this.`val`(Matrix4.M03)) - (((this.`val`(Matrix4.M20) * this.`val`(Matrix4.M31)) * this.`val`(Matrix4.M12)) * this.`val`(Matrix4.M03))) - (((this.`val`(Matrix4.M30) * this.`val`(Matrix4.M11)) * this.`val`(Matrix4.M22)) * this.`val`(Matrix4.M03))) + (((this.`val`(Matrix4.M10) * this.`val`(Matrix4.M31)) * this.`val`(Matrix4.M22)) * this.`val`(Matrix4.M03))) + (((this.`val`(Matrix4.M20) * this.`val`(Matrix4.M11)) * this.`val`(Matrix4.M32)) * this.`val`(Matrix4.M03))) - (((this.`val`(Matrix4.M10) * this.`val`(Matrix4.M21)) * this.`val`(Matrix4.M32)) * this.`val`(Matrix4.M03))) - (((this.`val`(Matrix4.M30) * this.`val`(Matrix4.M21)) * this.`val`(Matrix4.M02)) * this.`val`(Matrix4.M13))) + (((this.`val`(Matrix4.M20) * this.`val`(Matrix4.M31)) * this.`val`(Matrix4.M02)) * this.`val`(Matrix4.M13))) + (((this.`val`(Matrix4.M30) * this.`val`(Matrix4.M01)) * this.`val`(Matrix4.M22)) * this.`val`(Matrix4.M13))) - (((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M31)) * this.`val`(Matrix4.M22)) * this.`val`(Matrix4.M13))) - (((this.`val`(Matrix4.M20) * this.`val`(Matrix4.M01)) * this.`val`(Matrix4.M32)) * this.`val`(Matrix4.M13))) + (((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M21)) * this.`val`(Matrix4.M32)) * this.`val`(Matrix4.M13))) + (((this.`val`(Matrix4.M30) * this.`val`(Matrix4.M11)) * this.`val`(Matrix4.M02)) * this.`val`(Matrix4.M23))) - (((this.`val`(Matrix4.M10) * this.`val`(Matrix4.M31)) * this.`val`(Matrix4.M02)) * this.`val`(Matrix4.M23))) - (((this.`val`(Matrix4.M30) * this.`val`(Matrix4.M01)) * this.`val`(Matrix4.M12)) * this.`val`(Matrix4.M23))) + (((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M31)) * this.`val`(Matrix4.M12)) * this.`val`(Matrix4.M23))) + (((this.`val`(Matrix4.M10) * this.`val`(Matrix4.M01)) * this.`val`(Matrix4.M32)) * this.`val`(Matrix4.M23))) - (((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M11)) * this.`val`(Matrix4.M32)) * this.`val`(Matrix4.M23))) - (((this.`val`(Matrix4.M20) * this.`val`(Matrix4.M11)) * this.`val`(Matrix4.M02)) * this.`val`(Matrix4.M33))) + (((this.`val`(Matrix4.M10) * this.`val`(Matrix4.M21)) * this.`val`(Matrix4.M02)) * this.`val`(Matrix4.M33))) + (((this.`val`(Matrix4.M20) * this.`val`(Matrix4.M01)) * this.`val`(Matrix4.M12)) * this.`val`(Matrix4.M33))) - (((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M21)) * this.`val`(Matrix4.M12)) * this.`val`(Matrix4.M33))) - (((this.`val`(Matrix4.M10) * this.`val`(Matrix4.M01)) * this.`val`(Matrix4.M22)) * this.`val`(Matrix4.M33))) + (((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M11)) * this.`val`(Matrix4.M22)) * this.`val`(Matrix4.M33))
    if (l_det == 0.0f) {
      throw new java.lang.RuntimeException("non-invertible matrix")
    } else ()
    val m00: scala.Float = ((((((this.`val`(Matrix4.M12) * this.`val`(Matrix4.M23)) * this.`val`(Matrix4.M31)) - ((this.`val`(Matrix4.M13) * this.`val`(Matrix4.M22)) * this.`val`(Matrix4.M31))) + ((this.`val`(Matrix4.M13) * this.`val`(Matrix4.M21)) * this.`val`(Matrix4.M32))) - ((this.`val`(Matrix4.M11) * this.`val`(Matrix4.M23)) * this.`val`(Matrix4.M32))) - ((this.`val`(Matrix4.M12) * this.`val`(Matrix4.M21)) * this.`val`(Matrix4.M33))) + ((this.`val`(Matrix4.M11) * this.`val`(Matrix4.M22)) * this.`val`(Matrix4.M33))
    val m01: scala.Float = ((((((this.`val`(Matrix4.M03) * this.`val`(Matrix4.M22)) * this.`val`(Matrix4.M31)) - ((this.`val`(Matrix4.M02) * this.`val`(Matrix4.M23)) * this.`val`(Matrix4.M31))) - ((this.`val`(Matrix4.M03) * this.`val`(Matrix4.M21)) * this.`val`(Matrix4.M32))) + ((this.`val`(Matrix4.M01) * this.`val`(Matrix4.M23)) * this.`val`(Matrix4.M32))) + ((this.`val`(Matrix4.M02) * this.`val`(Matrix4.M21)) * this.`val`(Matrix4.M33))) - ((this.`val`(Matrix4.M01) * this.`val`(Matrix4.M22)) * this.`val`(Matrix4.M33))
    val m02: scala.Float = ((((((this.`val`(Matrix4.M02) * this.`val`(Matrix4.M13)) * this.`val`(Matrix4.M31)) - ((this.`val`(Matrix4.M03) * this.`val`(Matrix4.M12)) * this.`val`(Matrix4.M31))) + ((this.`val`(Matrix4.M03) * this.`val`(Matrix4.M11)) * this.`val`(Matrix4.M32))) - ((this.`val`(Matrix4.M01) * this.`val`(Matrix4.M13)) * this.`val`(Matrix4.M32))) - ((this.`val`(Matrix4.M02) * this.`val`(Matrix4.M11)) * this.`val`(Matrix4.M33))) + ((this.`val`(Matrix4.M01) * this.`val`(Matrix4.M12)) * this.`val`(Matrix4.M33))
    val m03: scala.Float = ((((((this.`val`(Matrix4.M03) * this.`val`(Matrix4.M12)) * this.`val`(Matrix4.M21)) - ((this.`val`(Matrix4.M02) * this.`val`(Matrix4.M13)) * this.`val`(Matrix4.M21))) - ((this.`val`(Matrix4.M03) * this.`val`(Matrix4.M11)) * this.`val`(Matrix4.M22))) + ((this.`val`(Matrix4.M01) * this.`val`(Matrix4.M13)) * this.`val`(Matrix4.M22))) + ((this.`val`(Matrix4.M02) * this.`val`(Matrix4.M11)) * this.`val`(Matrix4.M23))) - ((this.`val`(Matrix4.M01) * this.`val`(Matrix4.M12)) * this.`val`(Matrix4.M23))
    val m10: scala.Float = ((((((this.`val`(Matrix4.M13) * this.`val`(Matrix4.M22)) * this.`val`(Matrix4.M30)) - ((this.`val`(Matrix4.M12) * this.`val`(Matrix4.M23)) * this.`val`(Matrix4.M30))) - ((this.`val`(Matrix4.M13) * this.`val`(Matrix4.M20)) * this.`val`(Matrix4.M32))) + ((this.`val`(Matrix4.M10) * this.`val`(Matrix4.M23)) * this.`val`(Matrix4.M32))) + ((this.`val`(Matrix4.M12) * this.`val`(Matrix4.M20)) * this.`val`(Matrix4.M33))) - ((this.`val`(Matrix4.M10) * this.`val`(Matrix4.M22)) * this.`val`(Matrix4.M33))
    val m11: scala.Float = ((((((this.`val`(Matrix4.M02) * this.`val`(Matrix4.M23)) * this.`val`(Matrix4.M30)) - ((this.`val`(Matrix4.M03) * this.`val`(Matrix4.M22)) * this.`val`(Matrix4.M30))) + ((this.`val`(Matrix4.M03) * this.`val`(Matrix4.M20)) * this.`val`(Matrix4.M32))) - ((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M23)) * this.`val`(Matrix4.M32))) - ((this.`val`(Matrix4.M02) * this.`val`(Matrix4.M20)) * this.`val`(Matrix4.M33))) + ((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M22)) * this.`val`(Matrix4.M33))
    val m12: scala.Float = ((((((this.`val`(Matrix4.M03) * this.`val`(Matrix4.M12)) * this.`val`(Matrix4.M30)) - ((this.`val`(Matrix4.M02) * this.`val`(Matrix4.M13)) * this.`val`(Matrix4.M30))) - ((this.`val`(Matrix4.M03) * this.`val`(Matrix4.M10)) * this.`val`(Matrix4.M32))) + ((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M13)) * this.`val`(Matrix4.M32))) + ((this.`val`(Matrix4.M02) * this.`val`(Matrix4.M10)) * this.`val`(Matrix4.M33))) - ((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M12)) * this.`val`(Matrix4.M33))
    val m13: scala.Float = ((((((this.`val`(Matrix4.M02) * this.`val`(Matrix4.M13)) * this.`val`(Matrix4.M20)) - ((this.`val`(Matrix4.M03) * this.`val`(Matrix4.M12)) * this.`val`(Matrix4.M20))) + ((this.`val`(Matrix4.M03) * this.`val`(Matrix4.M10)) * this.`val`(Matrix4.M22))) - ((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M13)) * this.`val`(Matrix4.M22))) - ((this.`val`(Matrix4.M02) * this.`val`(Matrix4.M10)) * this.`val`(Matrix4.M23))) + ((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M12)) * this.`val`(Matrix4.M23))
    val m20: scala.Float = ((((((this.`val`(Matrix4.M11) * this.`val`(Matrix4.M23)) * this.`val`(Matrix4.M30)) - ((this.`val`(Matrix4.M13) * this.`val`(Matrix4.M21)) * this.`val`(Matrix4.M30))) + ((this.`val`(Matrix4.M13) * this.`val`(Matrix4.M20)) * this.`val`(Matrix4.M31))) - ((this.`val`(Matrix4.M10) * this.`val`(Matrix4.M23)) * this.`val`(Matrix4.M31))) - ((this.`val`(Matrix4.M11) * this.`val`(Matrix4.M20)) * this.`val`(Matrix4.M33))) + ((this.`val`(Matrix4.M10) * this.`val`(Matrix4.M21)) * this.`val`(Matrix4.M33))
    val m21: scala.Float = ((((((this.`val`(Matrix4.M03) * this.`val`(Matrix4.M21)) * this.`val`(Matrix4.M30)) - ((this.`val`(Matrix4.M01) * this.`val`(Matrix4.M23)) * this.`val`(Matrix4.M30))) - ((this.`val`(Matrix4.M03) * this.`val`(Matrix4.M20)) * this.`val`(Matrix4.M31))) + ((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M23)) * this.`val`(Matrix4.M31))) + ((this.`val`(Matrix4.M01) * this.`val`(Matrix4.M20)) * this.`val`(Matrix4.M33))) - ((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M21)) * this.`val`(Matrix4.M33))
    val m22: scala.Float = ((((((this.`val`(Matrix4.M01) * this.`val`(Matrix4.M13)) * this.`val`(Matrix4.M30)) - ((this.`val`(Matrix4.M03) * this.`val`(Matrix4.M11)) * this.`val`(Matrix4.M30))) + ((this.`val`(Matrix4.M03) * this.`val`(Matrix4.M10)) * this.`val`(Matrix4.M31))) - ((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M13)) * this.`val`(Matrix4.M31))) - ((this.`val`(Matrix4.M01) * this.`val`(Matrix4.M10)) * this.`val`(Matrix4.M33))) + ((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M11)) * this.`val`(Matrix4.M33))
    val m23: scala.Float = ((((((this.`val`(Matrix4.M03) * this.`val`(Matrix4.M11)) * this.`val`(Matrix4.M20)) - ((this.`val`(Matrix4.M01) * this.`val`(Matrix4.M13)) * this.`val`(Matrix4.M20))) - ((this.`val`(Matrix4.M03) * this.`val`(Matrix4.M10)) * this.`val`(Matrix4.M21))) + ((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M13)) * this.`val`(Matrix4.M21))) + ((this.`val`(Matrix4.M01) * this.`val`(Matrix4.M10)) * this.`val`(Matrix4.M23))) - ((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M11)) * this.`val`(Matrix4.M23))
    val m30: scala.Float = ((((((this.`val`(Matrix4.M12) * this.`val`(Matrix4.M21)) * this.`val`(Matrix4.M30)) - ((this.`val`(Matrix4.M11) * this.`val`(Matrix4.M22)) * this.`val`(Matrix4.M30))) - ((this.`val`(Matrix4.M12) * this.`val`(Matrix4.M20)) * this.`val`(Matrix4.M31))) + ((this.`val`(Matrix4.M10) * this.`val`(Matrix4.M22)) * this.`val`(Matrix4.M31))) + ((this.`val`(Matrix4.M11) * this.`val`(Matrix4.M20)) * this.`val`(Matrix4.M32))) - ((this.`val`(Matrix4.M10) * this.`val`(Matrix4.M21)) * this.`val`(Matrix4.M32))
    val m31: scala.Float = ((((((this.`val`(Matrix4.M01) * this.`val`(Matrix4.M22)) * this.`val`(Matrix4.M30)) - ((this.`val`(Matrix4.M02) * this.`val`(Matrix4.M21)) * this.`val`(Matrix4.M30))) + ((this.`val`(Matrix4.M02) * this.`val`(Matrix4.M20)) * this.`val`(Matrix4.M31))) - ((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M22)) * this.`val`(Matrix4.M31))) - ((this.`val`(Matrix4.M01) * this.`val`(Matrix4.M20)) * this.`val`(Matrix4.M32))) + ((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M21)) * this.`val`(Matrix4.M32))
    val m32: scala.Float = ((((((this.`val`(Matrix4.M02) * this.`val`(Matrix4.M11)) * this.`val`(Matrix4.M30)) - ((this.`val`(Matrix4.M01) * this.`val`(Matrix4.M12)) * this.`val`(Matrix4.M30))) - ((this.`val`(Matrix4.M02) * this.`val`(Matrix4.M10)) * this.`val`(Matrix4.M31))) + ((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M12)) * this.`val`(Matrix4.M31))) + ((this.`val`(Matrix4.M01) * this.`val`(Matrix4.M10)) * this.`val`(Matrix4.M32))) - ((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M11)) * this.`val`(Matrix4.M32))
    val m33: scala.Float = ((((((this.`val`(Matrix4.M01) * this.`val`(Matrix4.M12)) * this.`val`(Matrix4.M20)) - ((this.`val`(Matrix4.M02) * this.`val`(Matrix4.M11)) * this.`val`(Matrix4.M20))) + ((this.`val`(Matrix4.M02) * this.`val`(Matrix4.M10)) * this.`val`(Matrix4.M21))) - ((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M12)) * this.`val`(Matrix4.M21))) - ((this.`val`(Matrix4.M01) * this.`val`(Matrix4.M10)) * this.`val`(Matrix4.M22))) + ((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M11)) * this.`val`(Matrix4.M22))
    val inv_det: scala.Float = 1.0f / l_det
    this.`val`(Matrix4.M00) = m00 * inv_det
    this.`val`(Matrix4.M10) = m10 * inv_det
    this.`val`(Matrix4.M20) = m20 * inv_det
    this.`val`(Matrix4.M30) = m30 * inv_det
    this.`val`(Matrix4.M01) = m01 * inv_det
    this.`val`(Matrix4.M11) = m11 * inv_det
    this.`val`(Matrix4.M21) = m21 * inv_det
    this.`val`(Matrix4.M31) = m31 * inv_det
    this.`val`(Matrix4.M02) = m02 * inv_det
    this.`val`(Matrix4.M12) = m12 * inv_det
    this.`val`(Matrix4.M22) = m22 * inv_det
    this.`val`(Matrix4.M32) = m32 * inv_det
    this.`val`(Matrix4.M03) = m03 * inv_det
    this.`val`(Matrix4.M13) = m13 * inv_det
    this.`val`(Matrix4.M23) = m23 * inv_det
    this.`val`(Matrix4.M33) = m33 * inv_det
    return this
  }
  def det(): scala.Float = {
    return (((((((((((((((((((((((((this.`val`(Matrix4.M30) * this.`val`(Matrix4.M21)) * this.`val`(Matrix4.M12)) * this.`val`(Matrix4.M03)) - (((this.`val`(Matrix4.M20) * this.`val`(Matrix4.M31)) * this.`val`(Matrix4.M12)) * this.`val`(Matrix4.M03))) - (((this.`val`(Matrix4.M30) * this.`val`(Matrix4.M11)) * this.`val`(Matrix4.M22)) * this.`val`(Matrix4.M03))) + (((this.`val`(Matrix4.M10) * this.`val`(Matrix4.M31)) * this.`val`(Matrix4.M22)) * this.`val`(Matrix4.M03))) + (((this.`val`(Matrix4.M20) * this.`val`(Matrix4.M11)) * this.`val`(Matrix4.M32)) * this.`val`(Matrix4.M03))) - (((this.`val`(Matrix4.M10) * this.`val`(Matrix4.M21)) * this.`val`(Matrix4.M32)) * this.`val`(Matrix4.M03))) - (((this.`val`(Matrix4.M30) * this.`val`(Matrix4.M21)) * this.`val`(Matrix4.M02)) * this.`val`(Matrix4.M13))) + (((this.`val`(Matrix4.M20) * this.`val`(Matrix4.M31)) * this.`val`(Matrix4.M02)) * this.`val`(Matrix4.M13))) + (((this.`val`(Matrix4.M30) * this.`val`(Matrix4.M01)) * this.`val`(Matrix4.M22)) * this.`val`(Matrix4.M13))) - (((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M31)) * this.`val`(Matrix4.M22)) * this.`val`(Matrix4.M13))) - (((this.`val`(Matrix4.M20) * this.`val`(Matrix4.M01)) * this.`val`(Matrix4.M32)) * this.`val`(Matrix4.M13))) + (((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M21)) * this.`val`(Matrix4.M32)) * this.`val`(Matrix4.M13))) + (((this.`val`(Matrix4.M30) * this.`val`(Matrix4.M11)) * this.`val`(Matrix4.M02)) * this.`val`(Matrix4.M23))) - (((this.`val`(Matrix4.M10) * this.`val`(Matrix4.M31)) * this.`val`(Matrix4.M02)) * this.`val`(Matrix4.M23))) - (((this.`val`(Matrix4.M30) * this.`val`(Matrix4.M01)) * this.`val`(Matrix4.M12)) * this.`val`(Matrix4.M23))) + (((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M31)) * this.`val`(Matrix4.M12)) * this.`val`(Matrix4.M23))) + (((this.`val`(Matrix4.M10) * this.`val`(Matrix4.M01)) * this.`val`(Matrix4.M32)) * this.`val`(Matrix4.M23))) - (((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M11)) * this.`val`(Matrix4.M32)) * this.`val`(Matrix4.M23))) - (((this.`val`(Matrix4.M20) * this.`val`(Matrix4.M11)) * this.`val`(Matrix4.M02)) * this.`val`(Matrix4.M33))) + (((this.`val`(Matrix4.M10) * this.`val`(Matrix4.M21)) * this.`val`(Matrix4.M02)) * this.`val`(Matrix4.M33))) + (((this.`val`(Matrix4.M20) * this.`val`(Matrix4.M01)) * this.`val`(Matrix4.M12)) * this.`val`(Matrix4.M33))) - (((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M21)) * this.`val`(Matrix4.M12)) * this.`val`(Matrix4.M33))) - (((this.`val`(Matrix4.M10) * this.`val`(Matrix4.M01)) * this.`val`(Matrix4.M22)) * this.`val`(Matrix4.M33))) + (((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M11)) * this.`val`(Matrix4.M22)) * this.`val`(Matrix4.M33))
  }
  def det3x3(): scala.Float = {
    return ((((((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M11)) * this.`val`(Matrix4.M22)) + ((this.`val`(Matrix4.M01) * this.`val`(Matrix4.M12)) * this.`val`(Matrix4.M20))) + ((this.`val`(Matrix4.M02) * this.`val`(Matrix4.M10)) * this.`val`(Matrix4.M21))) - ((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M12)) * this.`val`(Matrix4.M21))) - ((this.`val`(Matrix4.M01) * this.`val`(Matrix4.M10)) * this.`val`(Matrix4.M22))) - ((this.`val`(Matrix4.M02) * this.`val`(Matrix4.M11)) * this.`val`(Matrix4.M20))
  }
  def setToProjection(near: scala.Float, far: scala.Float, fovy: scala.Float, aspectRatio: scala.Float): Matrix4 = {
    this.idt()
    val l_fd: scala.Float = (1.0 / java.lang.Math.tan((fovy * (java.lang.Math.PI / 180)) / 2.0)).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    val l_a1: scala.Float = (far + near) / (near - far)
    val l_a2: scala.Float = ((2 * far) * near) / (near - far)
    this.`val`(Matrix4.M00) = l_fd / aspectRatio
    this.`val`(Matrix4.M10) = 0
    this.`val`(Matrix4.M20) = 0
    this.`val`(Matrix4.M30) = 0
    this.`val`(Matrix4.M01) = 0
    this.`val`(Matrix4.M11) = l_fd
    this.`val`(Matrix4.M21) = 0
    this.`val`(Matrix4.M31) = 0
    this.`val`(Matrix4.M02) = 0
    this.`val`(Matrix4.M12) = 0
    this.`val`(Matrix4.M22) = l_a1
    this.`val`(Matrix4.M32) = -1
    this.`val`(Matrix4.M03) = 0
    this.`val`(Matrix4.M13) = 0
    this.`val`(Matrix4.M23) = l_a2
    this.`val`(Matrix4.M33) = 0
    return this
  }
  def setToProjection(left: scala.Float, right: scala.Float, bottom: scala.Float, top: scala.Float, near: scala.Float, far: scala.Float): Matrix4 = {
    val x: scala.Float = (2.0f * near) / (right - left)
    val y: scala.Float = (2.0f * near) / (top - bottom)
    val a: scala.Float = (right + left) / (right - left)
    val b: scala.Float = (top + bottom) / (top - bottom)
    val l_a1: scala.Float = (far + near) / (near - far)
    val l_a2: scala.Float = ((2 * far) * near) / (near - far)
    this.`val`(Matrix4.M00) = x
    this.`val`(Matrix4.M10) = 0
    this.`val`(Matrix4.M20) = 0
    this.`val`(Matrix4.M30) = 0
    this.`val`(Matrix4.M01) = 0
    this.`val`(Matrix4.M11) = y
    this.`val`(Matrix4.M21) = 0
    this.`val`(Matrix4.M31) = 0
    this.`val`(Matrix4.M02) = a
    this.`val`(Matrix4.M12) = b
    this.`val`(Matrix4.M22) = l_a1
    this.`val`(Matrix4.M32) = -1
    this.`val`(Matrix4.M03) = 0
    this.`val`(Matrix4.M13) = 0
    this.`val`(Matrix4.M23) = l_a2
    this.`val`(Matrix4.M33) = 0
    return this
  }
  def setToOrtho2D(x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): Matrix4 = {
    this.setToOrtho(x, x + width, y, y + height, 0, 1)
    return this
  }
  def setToOrtho2D(x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float, near: scala.Float, far: scala.Float): Matrix4 = {
    this.setToOrtho(x, x + width, y, y + height, near, far)
    return this
  }
  def setToOrtho(left: scala.Float, right: scala.Float, bottom: scala.Float, top: scala.Float, near: scala.Float, far: scala.Float): Matrix4 = {
    val x_orth: scala.Float = 2 / (right - left)
    val y_orth: scala.Float = 2 / (top - bottom)
    val z_orth: scala.Float = (-2) / (far - near)
    val tx: scala.Float = (-(right + left)) / (right - left)
    val ty: scala.Float = (-(top + bottom)) / (top - bottom)
    val tz: scala.Float = (-(far + near)) / (far - near)
    this.`val`(Matrix4.M00) = x_orth
    this.`val`(Matrix4.M10) = 0
    this.`val`(Matrix4.M20) = 0
    this.`val`(Matrix4.M30) = 0
    this.`val`(Matrix4.M01) = 0
    this.`val`(Matrix4.M11) = y_orth
    this.`val`(Matrix4.M21) = 0
    this.`val`(Matrix4.M31) = 0
    this.`val`(Matrix4.M02) = 0
    this.`val`(Matrix4.M12) = 0
    this.`val`(Matrix4.M22) = z_orth
    this.`val`(Matrix4.M32) = 0
    this.`val`(Matrix4.M03) = tx
    this.`val`(Matrix4.M13) = ty
    this.`val`(Matrix4.M23) = tz
    this.`val`(Matrix4.M33) = 1
    return this
  }
  def setTranslation(vector: com.badlogic.gdx.math.Vector3): Matrix4 = {
    this.`val`(Matrix4.M03) = vector.x
    this.`val`(Matrix4.M13) = vector.y
    this.`val`(Matrix4.M23) = vector.z
    return this
  }
  def setTranslation(x: scala.Float, y: scala.Float, z: scala.Float): Matrix4 = {
    this.`val`(Matrix4.M03) = x
    this.`val`(Matrix4.M13) = y
    this.`val`(Matrix4.M23) = z
    return this
  }
  def setToTranslation(vector: com.badlogic.gdx.math.Vector3): Matrix4 = {
    this.idt()
    this.`val`(Matrix4.M03) = vector.x
    this.`val`(Matrix4.M13) = vector.y
    this.`val`(Matrix4.M23) = vector.z
    return this
  }
  def setToTranslation(x: scala.Float, y: scala.Float, z: scala.Float): Matrix4 = {
    this.idt()
    this.`val`(Matrix4.M03) = x
    this.`val`(Matrix4.M13) = y
    this.`val`(Matrix4.M23) = z
    return this
  }
  def setToTranslationAndScaling(translation: com.badlogic.gdx.math.Vector3, scaling: com.badlogic.gdx.math.Vector3): Matrix4 = {
    this.idt()
    this.`val`(Matrix4.M03) = translation.x
    this.`val`(Matrix4.M13) = translation.y
    this.`val`(Matrix4.M23) = translation.z
    this.`val`(Matrix4.M00) = scaling.x
    this.`val`(Matrix4.M11) = scaling.y
    this.`val`(Matrix4.M22) = scaling.z
    return this
  }
  def setToTranslationAndScaling(translationX: scala.Float, translationY: scala.Float, translationZ: scala.Float, scalingX: scala.Float, scalingY: scala.Float, scalingZ: scala.Float): Matrix4 = {
    this.idt()
    this.`val`(Matrix4.M03) = translationX
    this.`val`(Matrix4.M13) = translationY
    this.`val`(Matrix4.M23) = translationZ
    this.`val`(Matrix4.M00) = scalingX
    this.`val`(Matrix4.M11) = scalingY
    this.`val`(Matrix4.M22) = scalingZ
    return this
  }
  def setToRotation(axis: com.badlogic.gdx.math.Vector3, degrees: scala.Float): Matrix4 = {
    if (degrees == 0) {
      this.idt()
      return this
    } else ()
    return this.set(Matrix4.quat.set(axis, degrees))
  }
  def setToRotationRad(axis: com.badlogic.gdx.math.Vector3, radians: scala.Float): Matrix4 = {
    if (radians == 0) {
      this.idt()
      return this
    } else ()
    return this.set(Matrix4.quat.setFromAxisRad(axis, radians))
  }
  def setToRotation(axisX: scala.Float, axisY: scala.Float, axisZ: scala.Float, degrees: scala.Float): Matrix4 = {
    if (degrees == 0) {
      this.idt()
      return this
    } else ()
    return this.set(Matrix4.quat.setFromAxis(axisX, axisY, axisZ, degrees))
  }
  def setToRotationRad(axisX: scala.Float, axisY: scala.Float, axisZ: scala.Float, radians: scala.Float): Matrix4 = {
    if (radians == 0) {
      this.idt()
      return this
    } else ()
    return this.set(Matrix4.quat.setFromAxisRad(axisX, axisY, axisZ, radians))
  }
  def setToRotation(v1: com.badlogic.gdx.math.Vector3, v2: com.badlogic.gdx.math.Vector3): Matrix4 = {
    return this.set(Matrix4.quat.setFromCross(v1, v2))
  }
  def setToRotation(x1: scala.Float, y1: scala.Float, z1: scala.Float, x2: scala.Float, y2: scala.Float, z2: scala.Float): Matrix4 = {
    return this.set(Matrix4.quat.setFromCross(x1, y1, z1, x2, y2, z2))
  }
  def setFromEulerAngles(yaw: scala.Float, pitch: scala.Float, roll: scala.Float): Matrix4 = {
    Matrix4.quat.setEulerAngles(yaw, pitch, roll)
    return this.set(Matrix4.quat)
  }
  def setFromEulerAnglesRad(yaw: scala.Float, pitch: scala.Float, roll: scala.Float): Matrix4 = {
    Matrix4.quat.setEulerAnglesRad(yaw, pitch, roll)
    return this.set(Matrix4.quat)
  }
  def setToScaling(vector: com.badlogic.gdx.math.Vector3): Matrix4 = {
    this.idt()
    this.`val`(Matrix4.M00) = vector.x
    this.`val`(Matrix4.M11) = vector.y
    this.`val`(Matrix4.M22) = vector.z
    return this
  }
  def setToScaling(x: scala.Float, y: scala.Float, z: scala.Float): Matrix4 = {
    this.idt()
    this.`val`(Matrix4.M00) = x
    this.`val`(Matrix4.M11) = y
    this.`val`(Matrix4.M22) = z
    return this
  }
  def setToLookAt(direction: com.badlogic.gdx.math.Vector3, up: com.badlogic.gdx.math.Vector3): Matrix4 = {
    Matrix4.l_vez.set(direction).nor()
    Matrix4.l_vex.set(direction).crs(up).nor()
    Matrix4.l_vey.set(Matrix4.l_vex).crs(Matrix4.l_vez).nor()
    this.idt()
    this.`val`(Matrix4.M00) = Matrix4.l_vex.x
    this.`val`(Matrix4.M01) = Matrix4.l_vex.y
    this.`val`(Matrix4.M02) = Matrix4.l_vex.z
    this.`val`(Matrix4.M10) = Matrix4.l_vey.x
    this.`val`(Matrix4.M11) = Matrix4.l_vey.y
    this.`val`(Matrix4.M12) = Matrix4.l_vey.z
    this.`val`(Matrix4.M20) = -Matrix4.l_vez.x
    this.`val`(Matrix4.M21) = -Matrix4.l_vez.y
    this.`val`(Matrix4.M22) = -Matrix4.l_vez.z
    return this
  }
  def setToLookAt(position: com.badlogic.gdx.math.Vector3, target: com.badlogic.gdx.math.Vector3, up: com.badlogic.gdx.math.Vector3): Matrix4 = {
    Matrix4.tmpVec.set(target).sub(position)
    this.setToLookAt(Matrix4.tmpVec, up)
    this.mul(Matrix4.tmpMat.setToTranslation(-position.x, -position.y, -position.z))
    return this
  }
  def setToWorld(position: com.badlogic.gdx.math.Vector3, forward: com.badlogic.gdx.math.Vector3, up: com.badlogic.gdx.math.Vector3): Matrix4 = {
    Matrix4.tmpForward.set(forward).nor()
    Matrix4.right.set(Matrix4.tmpForward).crs(up).nor()
    Matrix4.tmpUp.set(Matrix4.right).crs(Matrix4.tmpForward).nor()
    this.set(Matrix4.right, Matrix4.tmpUp, Matrix4.tmpForward.scl(-1), position)
    return this
  }
  def lerp(matrix: Matrix4, alpha: scala.Float): Matrix4 = {
    { var i: scala.Int = 0; while (i < 16) { {
      this.`val`(i) = (this.`val`(i) * (1 - alpha)) + (matrix.`val`(i) * alpha)
    }; i = i + 1 } }
    return this
  }
  def avg(other: Matrix4, w: scala.Float): Matrix4 = {
    this.getScale(Matrix4.tmpVec)
    other.getScale(Matrix4.tmpForward)
    this.getRotation(Matrix4.quat)
    other.getRotation(Matrix4.quat2)
    this.getTranslation(Matrix4.tmpUp)
    other.getTranslation(Matrix4.right)
    this.setToScaling(Matrix4.tmpVec.scl(w).add(Matrix4.tmpForward.scl(1 - w)))
    this.rotate(Matrix4.quat.slerp(Matrix4.quat2, 1 - w))
    this.setTranslation(Matrix4.tmpUp.scl(w).add(Matrix4.right.scl(1 - w)))
    return this
  }
  def avg(t: scala.Array[Matrix4]): Matrix4 = {
    val w: scala.Float = 1.0f / t.length
    Matrix4.tmpVec.set(t(0).getScale(Matrix4.tmpUp).scl(w))
    Matrix4.quat.set(t(0).getRotation(Matrix4.quat2).exp(w))
    Matrix4.tmpForward.set(t(0).getTranslation(Matrix4.tmpUp).scl(w));
    { var i: scala.Int = 1; while (i < t.length) { {
      Matrix4.tmpVec.add(t(i).getScale(Matrix4.tmpUp).scl(w))
      Matrix4.quat.mul(t(i).getRotation(Matrix4.quat2).exp(w))
      Matrix4.tmpForward.add(t(i).getTranslation(Matrix4.tmpUp).scl(w))
    }; i = i + 1 } }
    Matrix4.quat.nor()
    this.setToScaling(Matrix4.tmpVec)
    this.rotate(Matrix4.quat)
    this.setTranslation(Matrix4.tmpForward)
    return this
  }
  def avg(t: scala.Array[Matrix4], w: scala.Array[scala.Float]): Matrix4 = {
    Matrix4.tmpVec.set(t(0).getScale(Matrix4.tmpUp).scl(w(0)))
    Matrix4.quat.set(t(0).getRotation(Matrix4.quat2).exp(w(0)))
    Matrix4.tmpForward.set(t(0).getTranslation(Matrix4.tmpUp).scl(w(0)));
    { var i: scala.Int = 1; while (i < t.length) { {
      Matrix4.tmpVec.add(t(i).getScale(Matrix4.tmpUp).scl(w(i)))
      Matrix4.quat.mul(t(i).getRotation(Matrix4.quat2).exp(w(i)))
      Matrix4.tmpForward.add(t(i).getTranslation(Matrix4.tmpUp).scl(w(i)))
    }; i = i + 1 } }
    Matrix4.quat.nor()
    this.setToScaling(Matrix4.tmpVec)
    this.rotate(Matrix4.quat)
    this.setTranslation(Matrix4.tmpForward)
    return this
  }
  def set(mat: com.badlogic.gdx.math.Matrix3): Matrix4 = {
    this.`val`(0) = mat.`val`(0)
    this.`val`(1) = mat.`val`(1)
    this.`val`(2) = mat.`val`(2)
    this.`val`(3) = 0
    this.`val`(4) = mat.`val`(3)
    this.`val`(5) = mat.`val`(4)
    this.`val`(6) = mat.`val`(5)
    this.`val`(7) = 0
    this.`val`(8) = 0
    this.`val`(9) = 0
    this.`val`(10) = 1
    this.`val`(11) = 0
    this.`val`(12) = mat.`val`(6)
    this.`val`(13) = mat.`val`(7)
    this.`val`(14) = 0
    this.`val`(15) = mat.`val`(8)
    return this
  }
  def set(affine: com.badlogic.gdx.math.Affine2): Matrix4 = {
    this.`val`(Matrix4.M00) = affine.m00
    this.`val`(Matrix4.M10) = affine.m10
    this.`val`(Matrix4.M20) = 0
    this.`val`(Matrix4.M30) = 0
    this.`val`(Matrix4.M01) = affine.m01
    this.`val`(Matrix4.M11) = affine.m11
    this.`val`(Matrix4.M21) = 0
    this.`val`(Matrix4.M31) = 0
    this.`val`(Matrix4.M02) = 0
    this.`val`(Matrix4.M12) = 0
    this.`val`(Matrix4.M22) = 1
    this.`val`(Matrix4.M32) = 0
    this.`val`(Matrix4.M03) = affine.m02
    this.`val`(Matrix4.M13) = affine.m12
    this.`val`(Matrix4.M23) = 0
    this.`val`(Matrix4.M33) = 1
    return this
  }
  def setAsAffine(affine: com.badlogic.gdx.math.Affine2): Matrix4 = {
    this.`val`(Matrix4.M00) = affine.m00
    this.`val`(Matrix4.M10) = affine.m10
    this.`val`(Matrix4.M01) = affine.m01
    this.`val`(Matrix4.M11) = affine.m11
    this.`val`(Matrix4.M03) = affine.m02
    this.`val`(Matrix4.M13) = affine.m12
    return this
  }
  def setAsAffine(mat: Matrix4): Matrix4 = {
    this.`val`(Matrix4.M00) = mat.`val`(Matrix4.M00)
    this.`val`(Matrix4.M10) = mat.`val`(Matrix4.M10)
    this.`val`(Matrix4.M01) = mat.`val`(Matrix4.M01)
    this.`val`(Matrix4.M11) = mat.`val`(Matrix4.M11)
    this.`val`(Matrix4.M03) = mat.`val`(Matrix4.M03)
    this.`val`(Matrix4.M13) = mat.`val`(Matrix4.M13)
    return this
  }
  def scl(scale: com.badlogic.gdx.math.Vector3): Matrix4 = {
    this.`val`(Matrix4.M00) = this.`val`(Matrix4.M00) * scale.x
    this.`val`(Matrix4.M11) = this.`val`(Matrix4.M11) * scale.y
    this.`val`(Matrix4.M22) = this.`val`(Matrix4.M22) * scale.z
    return this
  }
  def scl(x: scala.Float, y: scala.Float, z: scala.Float): Matrix4 = {
    this.`val`(Matrix4.M00) = this.`val`(Matrix4.M00) * x
    this.`val`(Matrix4.M11) = this.`val`(Matrix4.M11) * y
    this.`val`(Matrix4.M22) = this.`val`(Matrix4.M22) * z
    return this
  }
  def scl(scale: scala.Float): Matrix4 = {
    this.`val`(Matrix4.M00) = this.`val`(Matrix4.M00) * scale
    this.`val`(Matrix4.M11) = this.`val`(Matrix4.M11) * scale
    this.`val`(Matrix4.M22) = this.`val`(Matrix4.M22) * scale
    return this
  }
  def getTranslation(position: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    position.x = this.`val`(Matrix4.M03)
    position.y = this.`val`(Matrix4.M13)
    position.z = this.`val`(Matrix4.M23)
    return position
  }
  def getRotation(rotation: com.badlogic.gdx.math.Quaternion, normalizeAxes: scala.Boolean): com.badlogic.gdx.math.Quaternion = {
    return rotation.setFromMatrix(normalizeAxes, this)
  }
  def getRotation(rotation: com.badlogic.gdx.math.Quaternion): com.badlogic.gdx.math.Quaternion = {
    return rotation.setFromMatrix(this)
  }
  def getScaleXSquared(): scala.Float = {
    return ((this.`val`(Matrix4.M00) * this.`val`(Matrix4.M00)) + (this.`val`(Matrix4.M01) * this.`val`(Matrix4.M01))) + (this.`val`(Matrix4.M02) * this.`val`(Matrix4.M02))
  }
  def getScaleYSquared(): scala.Float = {
    return ((this.`val`(Matrix4.M10) * this.`val`(Matrix4.M10)) + (this.`val`(Matrix4.M11) * this.`val`(Matrix4.M11))) + (this.`val`(Matrix4.M12) * this.`val`(Matrix4.M12))
  }
  def getScaleZSquared(): scala.Float = {
    return ((this.`val`(Matrix4.M20) * this.`val`(Matrix4.M20)) + (this.`val`(Matrix4.M21) * this.`val`(Matrix4.M21))) + (this.`val`(Matrix4.M22) * this.`val`(Matrix4.M22))
  }
  def getScaleX(): scala.Float = {
    return if (com.badlogic.gdx.math.MathUtils.isZero(this.`val`(Matrix4.M01)) && com.badlogic.gdx.math.MathUtils.isZero(this.`val`(Matrix4.M02))) java.lang.Math.abs(this.`val`(Matrix4.M00)) else java.lang.Math.sqrt(this.getScaleXSquared()).asInstanceOf[scala.Float]
  }
  def getScaleY(): scala.Float = {
    return if (com.badlogic.gdx.math.MathUtils.isZero(this.`val`(Matrix4.M10)) && com.badlogic.gdx.math.MathUtils.isZero(this.`val`(Matrix4.M12))) java.lang.Math.abs(this.`val`(Matrix4.M11)) else java.lang.Math.sqrt(this.getScaleYSquared()).asInstanceOf[scala.Float]
  }
  def getScaleZ(): scala.Float = {
    return if (com.badlogic.gdx.math.MathUtils.isZero(this.`val`(Matrix4.M20)) && com.badlogic.gdx.math.MathUtils.isZero(this.`val`(Matrix4.M21))) java.lang.Math.abs(this.`val`(Matrix4.M22)) else java.lang.Math.sqrt(this.getScaleZSquared()).asInstanceOf[scala.Float]
  }
  def getScale(scale: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    return scale.set(this.getScaleX(), this.getScaleY(), this.getScaleZ())
  }
  def toNormalMatrix(): Matrix4 = {
    this.`val`(Matrix4.M03) = 0
    this.`val`(Matrix4.M13) = 0
    this.`val`(Matrix4.M23) = 0
    return this.inv().tra()
  }
  def toString(): java.lang.String = {
    return (((((((((((((((((((((((((((((((((("[" + this.`val`(Matrix4.M00)) + "|") + this.`val`(Matrix4.M01)) + "|") + this.`val`(Matrix4.M02)) + "|") + this.`val`(Matrix4.M03)) + "]\n") + "[") + this.`val`(Matrix4.M10)) + "|") + this.`val`(Matrix4.M11)) + "|") + this.`val`(Matrix4.M12)) + "|") + this.`val`(Matrix4.M13)) + "]\n") + "[") + this.`val`(Matrix4.M20)) + "|") + this.`val`(Matrix4.M21)) + "|") + this.`val`(Matrix4.M22)) + "|") + this.`val`(Matrix4.M23)) + "]\n") + "[") + this.`val`(Matrix4.M30)) + "|") + this.`val`(Matrix4.M31)) + "|") + this.`val`(Matrix4.M32)) + "|") + this.`val`(Matrix4.M33)) + "]\n"
  }
  def translate(translation: com.badlogic.gdx.math.Vector3): Matrix4 = {
    return this.translate(translation.x, translation.y, translation.z)
  }
  def translate(x: scala.Float, y: scala.Float, z: scala.Float): Matrix4 = {
    this.`val`(Matrix4.M03) = this.`val`(Matrix4.M03) + (((this.`val`(Matrix4.M00) * x) + (this.`val`(Matrix4.M01) * y)) + (this.`val`(Matrix4.M02) * z))
    this.`val`(Matrix4.M13) = this.`val`(Matrix4.M13) + (((this.`val`(Matrix4.M10) * x) + (this.`val`(Matrix4.M11) * y)) + (this.`val`(Matrix4.M12) * z))
    this.`val`(Matrix4.M23) = this.`val`(Matrix4.M23) + (((this.`val`(Matrix4.M20) * x) + (this.`val`(Matrix4.M21) * y)) + (this.`val`(Matrix4.M22) * z))
    this.`val`(Matrix4.M33) = this.`val`(Matrix4.M33) + (((this.`val`(Matrix4.M30) * x) + (this.`val`(Matrix4.M31) * y)) + (this.`val`(Matrix4.M32) * z))
    return this
  }
  def rotate(axis: com.badlogic.gdx.math.Vector3, degrees: scala.Float): Matrix4 = {
    if (degrees == 0) {
      return this
    } else ()
    Matrix4.quat.set(axis, degrees)
    return this.rotate(Matrix4.quat)
  }
  def rotateRad(axis: com.badlogic.gdx.math.Vector3, radians: scala.Float): Matrix4 = {
    if (radians == 0) {
      return this
    } else ()
    Matrix4.quat.setFromAxisRad(axis, radians)
    return this.rotate(Matrix4.quat)
  }
  def rotate(axisX: scala.Float, axisY: scala.Float, axisZ: scala.Float, degrees: scala.Float): Matrix4 = {
    if (degrees == 0) {
      return this
    } else ()
    Matrix4.quat.setFromAxis(axisX, axisY, axisZ, degrees)
    return this.rotate(Matrix4.quat)
  }
  def rotateRad(axisX: scala.Float, axisY: scala.Float, axisZ: scala.Float, radians: scala.Float): Matrix4 = {
    if (radians == 0) {
      return this
    } else ()
    Matrix4.quat.setFromAxisRad(axisX, axisY, axisZ, radians)
    return this.rotate(Matrix4.quat)
  }
  def rotate(rotation: com.badlogic.gdx.math.Quaternion): Matrix4 = {
    val x: scala.Float = rotation.x
    val y: scala.Float = rotation.y
    val z: scala.Float = rotation.z
    val w: scala.Float = rotation.w
    val xx: scala.Float = x * x
    val xy: scala.Float = x * y
    val xz: scala.Float = x * z
    val xw: scala.Float = x * w
    val yy: scala.Float = y * y
    val yz: scala.Float = y * z
    val yw: scala.Float = y * w
    val zz: scala.Float = z * z
    val zw: scala.Float = z * w
    val r00: scala.Float = 1 - (2 * (yy + zz))
    val r01: scala.Float = 2 * (xy - zw)
    val r02: scala.Float = 2 * (xz + yw)
    val r10: scala.Float = 2 * (xy + zw)
    val r11: scala.Float = 1 - (2 * (xx + zz))
    val r12: scala.Float = 2 * (yz - xw)
    val r20: scala.Float = 2 * (xz - yw)
    val r21: scala.Float = 2 * (yz + xw)
    val r22: scala.Float = 1 - (2 * (xx + yy))
    val m00: scala.Float = ((this.`val`(Matrix4.M00) * r00) + (this.`val`(Matrix4.M01) * r10)) + (this.`val`(Matrix4.M02) * r20)
    val m01: scala.Float = ((this.`val`(Matrix4.M00) * r01) + (this.`val`(Matrix4.M01) * r11)) + (this.`val`(Matrix4.M02) * r21)
    val m02: scala.Float = ((this.`val`(Matrix4.M00) * r02) + (this.`val`(Matrix4.M01) * r12)) + (this.`val`(Matrix4.M02) * r22)
    val m10: scala.Float = ((this.`val`(Matrix4.M10) * r00) + (this.`val`(Matrix4.M11) * r10)) + (this.`val`(Matrix4.M12) * r20)
    val m11: scala.Float = ((this.`val`(Matrix4.M10) * r01) + (this.`val`(Matrix4.M11) * r11)) + (this.`val`(Matrix4.M12) * r21)
    val m12: scala.Float = ((this.`val`(Matrix4.M10) * r02) + (this.`val`(Matrix4.M11) * r12)) + (this.`val`(Matrix4.M12) * r22)
    val m20: scala.Float = ((this.`val`(Matrix4.M20) * r00) + (this.`val`(Matrix4.M21) * r10)) + (this.`val`(Matrix4.M22) * r20)
    val m21: scala.Float = ((this.`val`(Matrix4.M20) * r01) + (this.`val`(Matrix4.M21) * r11)) + (this.`val`(Matrix4.M22) * r21)
    val m22: scala.Float = ((this.`val`(Matrix4.M20) * r02) + (this.`val`(Matrix4.M21) * r12)) + (this.`val`(Matrix4.M22) * r22)
    val m30: scala.Float = ((this.`val`(Matrix4.M30) * r00) + (this.`val`(Matrix4.M31) * r10)) + (this.`val`(Matrix4.M32) * r20)
    val m31: scala.Float = ((this.`val`(Matrix4.M30) * r01) + (this.`val`(Matrix4.M31) * r11)) + (this.`val`(Matrix4.M32) * r21)
    val m32: scala.Float = ((this.`val`(Matrix4.M30) * r02) + (this.`val`(Matrix4.M31) * r12)) + (this.`val`(Matrix4.M32) * r22)
    this.`val`(Matrix4.M00) = m00
    this.`val`(Matrix4.M10) = m10
    this.`val`(Matrix4.M20) = m20
    this.`val`(Matrix4.M30) = m30
    this.`val`(Matrix4.M01) = m01
    this.`val`(Matrix4.M11) = m11
    this.`val`(Matrix4.M21) = m21
    this.`val`(Matrix4.M31) = m31
    this.`val`(Matrix4.M02) = m02
    this.`val`(Matrix4.M12) = m12
    this.`val`(Matrix4.M22) = m22
    this.`val`(Matrix4.M32) = m32
    return this
  }
  def rotate(v1: com.badlogic.gdx.math.Vector3, v2: com.badlogic.gdx.math.Vector3): Matrix4 = {
    return this.rotate(Matrix4.quat.setFromCross(v1, v2))
  }
  def rotateTowardDirection(direction: com.badlogic.gdx.math.Vector3, up: com.badlogic.gdx.math.Vector3): Matrix4 = {
    Matrix4.l_vez.set(direction).nor()
    Matrix4.l_vex.set(direction).crs(up).nor()
    Matrix4.l_vey.set(Matrix4.l_vex).crs(Matrix4.l_vez).nor()
    val m00: scala.Float = ((this.`val`(Matrix4.M00) * Matrix4.l_vex.x) + (this.`val`(Matrix4.M01) * Matrix4.l_vex.y)) + (this.`val`(Matrix4.M02) * Matrix4.l_vex.z)
    val m01: scala.Float = ((this.`val`(Matrix4.M00) * Matrix4.l_vey.x) + (this.`val`(Matrix4.M01) * Matrix4.l_vey.y)) + (this.`val`(Matrix4.M02) * Matrix4.l_vey.z)
    val m02: scala.Float = ((this.`val`(Matrix4.M00) * (-Matrix4.l_vez.x)) + (this.`val`(Matrix4.M01) * (-Matrix4.l_vez.y))) + (this.`val`(Matrix4.M02) * (-Matrix4.l_vez.z))
    val m10: scala.Float = ((this.`val`(Matrix4.M10) * Matrix4.l_vex.x) + (this.`val`(Matrix4.M11) * Matrix4.l_vex.y)) + (this.`val`(Matrix4.M12) * Matrix4.l_vex.z)
    val m11: scala.Float = ((this.`val`(Matrix4.M10) * Matrix4.l_vey.x) + (this.`val`(Matrix4.M11) * Matrix4.l_vey.y)) + (this.`val`(Matrix4.M12) * Matrix4.l_vey.z)
    val m12: scala.Float = ((this.`val`(Matrix4.M10) * (-Matrix4.l_vez.x)) + (this.`val`(Matrix4.M11) * (-Matrix4.l_vez.y))) + (this.`val`(Matrix4.M12) * (-Matrix4.l_vez.z))
    val m20: scala.Float = ((this.`val`(Matrix4.M20) * Matrix4.l_vex.x) + (this.`val`(Matrix4.M21) * Matrix4.l_vex.y)) + (this.`val`(Matrix4.M22) * Matrix4.l_vex.z)
    val m21: scala.Float = ((this.`val`(Matrix4.M20) * Matrix4.l_vey.x) + (this.`val`(Matrix4.M21) * Matrix4.l_vey.y)) + (this.`val`(Matrix4.M22) * Matrix4.l_vey.z)
    val m22: scala.Float = ((this.`val`(Matrix4.M20) * (-Matrix4.l_vez.x)) + (this.`val`(Matrix4.M21) * (-Matrix4.l_vez.y))) + (this.`val`(Matrix4.M22) * (-Matrix4.l_vez.z))
    val m30: scala.Float = ((this.`val`(Matrix4.M30) * Matrix4.l_vex.x) + (this.`val`(Matrix4.M31) * Matrix4.l_vex.y)) + (this.`val`(Matrix4.M32) * Matrix4.l_vex.z)
    val m31: scala.Float = ((this.`val`(Matrix4.M30) * Matrix4.l_vey.x) + (this.`val`(Matrix4.M31) * Matrix4.l_vey.y)) + (this.`val`(Matrix4.M32) * Matrix4.l_vey.z)
    val m32: scala.Float = ((this.`val`(Matrix4.M30) * (-Matrix4.l_vez.x)) + (this.`val`(Matrix4.M31) * (-Matrix4.l_vez.y))) + (this.`val`(Matrix4.M32) * (-Matrix4.l_vez.z))
    this.`val`(Matrix4.M00) = m00
    this.`val`(Matrix4.M10) = m10
    this.`val`(Matrix4.M20) = m20
    this.`val`(Matrix4.M30) = m30
    this.`val`(Matrix4.M01) = m01
    this.`val`(Matrix4.M11) = m11
    this.`val`(Matrix4.M21) = m21
    this.`val`(Matrix4.M31) = m31
    this.`val`(Matrix4.M02) = m02
    this.`val`(Matrix4.M12) = m12
    this.`val`(Matrix4.M22) = m22
    this.`val`(Matrix4.M32) = m32
    return this
  }
  def rotateTowardTarget(target: com.badlogic.gdx.math.Vector3, up: com.badlogic.gdx.math.Vector3): Matrix4 = {
    Matrix4.tmpVec.set(target.x - this.`val`(Matrix4.M03), target.y - this.`val`(Matrix4.M13), target.z - this.`val`(Matrix4.M23))
    return this.rotateTowardDirection(Matrix4.tmpVec, up)
  }
  def scale(scaleX: scala.Float, scaleY: scala.Float, scaleZ: scala.Float): Matrix4 = {
    this.`val`(Matrix4.M00) = this.`val`(Matrix4.M00) * scaleX
    this.`val`(Matrix4.M01) = this.`val`(Matrix4.M01) * scaleY
    this.`val`(Matrix4.M02) = this.`val`(Matrix4.M02) * scaleZ
    this.`val`(Matrix4.M10) = this.`val`(Matrix4.M10) * scaleX
    this.`val`(Matrix4.M11) = this.`val`(Matrix4.M11) * scaleY
    this.`val`(Matrix4.M12) = this.`val`(Matrix4.M12) * scaleZ
    this.`val`(Matrix4.M20) = this.`val`(Matrix4.M20) * scaleX
    this.`val`(Matrix4.M21) = this.`val`(Matrix4.M21) * scaleY
    this.`val`(Matrix4.M22) = this.`val`(Matrix4.M22) * scaleZ
    this.`val`(Matrix4.M30) = this.`val`(Matrix4.M30) * scaleX
    this.`val`(Matrix4.M31) = this.`val`(Matrix4.M31) * scaleY
    this.`val`(Matrix4.M32) = this.`val`(Matrix4.M32) * scaleZ
    return this
  }
  def extract4x3Matrix(dst: scala.Array[scala.Float]): scala.Unit = {
    dst(0) = this.`val`(Matrix4.M00)
    dst(1) = this.`val`(Matrix4.M10)
    dst(2) = this.`val`(Matrix4.M20)
    dst(3) = this.`val`(Matrix4.M01)
    dst(4) = this.`val`(Matrix4.M11)
    dst(5) = this.`val`(Matrix4.M21)
    dst(6) = this.`val`(Matrix4.M02)
    dst(7) = this.`val`(Matrix4.M12)
    dst(8) = this.`val`(Matrix4.M22)
    dst(9) = this.`val`(Matrix4.M03)
    dst(10) = this.`val`(Matrix4.M13)
    dst(11) = this.`val`(Matrix4.M23)
  }
  def hasRotationOrScaling(): scala.Boolean = {
    return !((((((((com.badlogic.gdx.math.MathUtils.isEqual(this.`val`(Matrix4.M00), 1) && com.badlogic.gdx.math.MathUtils.isEqual(this.`val`(Matrix4.M11), 1)) && com.badlogic.gdx.math.MathUtils.isEqual(this.`val`(Matrix4.M22), 1)) && com.badlogic.gdx.math.MathUtils.isZero(this.`val`(Matrix4.M01))) && com.badlogic.gdx.math.MathUtils.isZero(this.`val`(Matrix4.M02))) && com.badlogic.gdx.math.MathUtils.isZero(this.`val`(Matrix4.M10))) && com.badlogic.gdx.math.MathUtils.isZero(this.`val`(Matrix4.M12))) && com.badlogic.gdx.math.MathUtils.isZero(this.`val`(Matrix4.M20))) && com.badlogic.gdx.math.MathUtils.isZero(this.`val`(Matrix4.M21)))
  }
}
object Matrix4 {
  private final val serialVersionUID: scala.Long = -2717655254359579617L
  final val M00: scala.Int = 0
  final val M01: scala.Int = 4
  final val M02: scala.Int = 8
  final val M03: scala.Int = 12
  final val M10: scala.Int = 1
  final val M11: scala.Int = 5
  final val M12: scala.Int = 9
  final val M13: scala.Int = 13
  final val M20: scala.Int = 2
  final val M21: scala.Int = 6
  final val M22: scala.Int = 10
  final val M23: scala.Int = 14
  final val M30: scala.Int = 3
  final val M31: scala.Int = 7
  final val M32: scala.Int = 11
  final val M33: scala.Int = 15
  final val quat: com.badlogic.gdx.math.Quaternion = new com.badlogic.gdx.math.Quaternion()
  final val quat2: com.badlogic.gdx.math.Quaternion = new com.badlogic.gdx.math.Quaternion()
  final val l_vez: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val l_vex: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val l_vey: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val tmpVec: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val tmpMat: Matrix4 = new Matrix4()
  final val right: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val tmpForward: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val tmpUp: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  private val mulVec$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("mulVec").orElseThrow(), java.lang.foreign.FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT))
  private val prj$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("prj").orElseThrow(), java.lang.foreign.FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT))
  private val rot$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("rot").orElseThrow(), java.lang.foreign.FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT))
  def mulVec(mat: scala.Array[scala.Float], vecs: scala.Array[scala.Float], offset: scala.Int, numVecs: scala.Int, stride: scala.Int): scala.Unit = { mulVec$handle.invokeExact(mat, vecs, offset, numVecs, stride); () }
  def prj(mat: scala.Array[scala.Float], vecs: scala.Array[scala.Float], offset: scala.Int, numVecs: scala.Int, stride: scala.Int): scala.Unit = { prj$handle.invokeExact(mat, vecs, offset, numVecs, stride); () }
  def rot(mat: scala.Array[scala.Float], vecs: scala.Array[scala.Float], offset: scala.Int, numVecs: scala.Int, stride: scala.Int): scala.Unit = { rot$handle.invokeExact(mat, vecs, offset, numVecs, stride); () }
  def mul(mata: scala.Array[scala.Float], matb: scala.Array[scala.Float]): scala.Unit = {
    val m00: scala.Float = (((mata(Matrix4.M00) * matb(Matrix4.M00)) + (mata(Matrix4.M01) * matb(Matrix4.M10))) + (mata(Matrix4.M02) * matb(Matrix4.M20))) + (mata(Matrix4.M03) * matb(Matrix4.M30))
    val m01: scala.Float = (((mata(Matrix4.M00) * matb(Matrix4.M01)) + (mata(Matrix4.M01) * matb(Matrix4.M11))) + (mata(Matrix4.M02) * matb(Matrix4.M21))) + (mata(Matrix4.M03) * matb(Matrix4.M31))
    val m02: scala.Float = (((mata(Matrix4.M00) * matb(Matrix4.M02)) + (mata(Matrix4.M01) * matb(Matrix4.M12))) + (mata(Matrix4.M02) * matb(Matrix4.M22))) + (mata(Matrix4.M03) * matb(Matrix4.M32))
    val m03: scala.Float = (((mata(Matrix4.M00) * matb(Matrix4.M03)) + (mata(Matrix4.M01) * matb(Matrix4.M13))) + (mata(Matrix4.M02) * matb(Matrix4.M23))) + (mata(Matrix4.M03) * matb(Matrix4.M33))
    val m10: scala.Float = (((mata(Matrix4.M10) * matb(Matrix4.M00)) + (mata(Matrix4.M11) * matb(Matrix4.M10))) + (mata(Matrix4.M12) * matb(Matrix4.M20))) + (mata(Matrix4.M13) * matb(Matrix4.M30))
    val m11: scala.Float = (((mata(Matrix4.M10) * matb(Matrix4.M01)) + (mata(Matrix4.M11) * matb(Matrix4.M11))) + (mata(Matrix4.M12) * matb(Matrix4.M21))) + (mata(Matrix4.M13) * matb(Matrix4.M31))
    val m12: scala.Float = (((mata(Matrix4.M10) * matb(Matrix4.M02)) + (mata(Matrix4.M11) * matb(Matrix4.M12))) + (mata(Matrix4.M12) * matb(Matrix4.M22))) + (mata(Matrix4.M13) * matb(Matrix4.M32))
    val m13: scala.Float = (((mata(Matrix4.M10) * matb(Matrix4.M03)) + (mata(Matrix4.M11) * matb(Matrix4.M13))) + (mata(Matrix4.M12) * matb(Matrix4.M23))) + (mata(Matrix4.M13) * matb(Matrix4.M33))
    val m20: scala.Float = (((mata(Matrix4.M20) * matb(Matrix4.M00)) + (mata(Matrix4.M21) * matb(Matrix4.M10))) + (mata(Matrix4.M22) * matb(Matrix4.M20))) + (mata(Matrix4.M23) * matb(Matrix4.M30))
    val m21: scala.Float = (((mata(Matrix4.M20) * matb(Matrix4.M01)) + (mata(Matrix4.M21) * matb(Matrix4.M11))) + (mata(Matrix4.M22) * matb(Matrix4.M21))) + (mata(Matrix4.M23) * matb(Matrix4.M31))
    val m22: scala.Float = (((mata(Matrix4.M20) * matb(Matrix4.M02)) + (mata(Matrix4.M21) * matb(Matrix4.M12))) + (mata(Matrix4.M22) * matb(Matrix4.M22))) + (mata(Matrix4.M23) * matb(Matrix4.M32))
    val m23: scala.Float = (((mata(Matrix4.M20) * matb(Matrix4.M03)) + (mata(Matrix4.M21) * matb(Matrix4.M13))) + (mata(Matrix4.M22) * matb(Matrix4.M23))) + (mata(Matrix4.M23) * matb(Matrix4.M33))
    val m30: scala.Float = (((mata(Matrix4.M30) * matb(Matrix4.M00)) + (mata(Matrix4.M31) * matb(Matrix4.M10))) + (mata(Matrix4.M32) * matb(Matrix4.M20))) + (mata(Matrix4.M33) * matb(Matrix4.M30))
    val m31: scala.Float = (((mata(Matrix4.M30) * matb(Matrix4.M01)) + (mata(Matrix4.M31) * matb(Matrix4.M11))) + (mata(Matrix4.M32) * matb(Matrix4.M21))) + (mata(Matrix4.M33) * matb(Matrix4.M31))
    val m32: scala.Float = (((mata(Matrix4.M30) * matb(Matrix4.M02)) + (mata(Matrix4.M31) * matb(Matrix4.M12))) + (mata(Matrix4.M32) * matb(Matrix4.M22))) + (mata(Matrix4.M33) * matb(Matrix4.M32))
    val m33: scala.Float = (((mata(Matrix4.M30) * matb(Matrix4.M03)) + (mata(Matrix4.M31) * matb(Matrix4.M13))) + (mata(Matrix4.M32) * matb(Matrix4.M23))) + (mata(Matrix4.M33) * matb(Matrix4.M33))
    mata(Matrix4.M00) = m00
    mata(Matrix4.M10) = m10
    mata(Matrix4.M20) = m20
    mata(Matrix4.M30) = m30
    mata(Matrix4.M01) = m01
    mata(Matrix4.M11) = m11
    mata(Matrix4.M21) = m21
    mata(Matrix4.M31) = m31
    mata(Matrix4.M02) = m02
    mata(Matrix4.M12) = m12
    mata(Matrix4.M22) = m22
    mata(Matrix4.M32) = m32
    mata(Matrix4.M03) = m03
    mata(Matrix4.M13) = m13
    mata(Matrix4.M23) = m23
    mata(Matrix4.M33) = m33
  }
  def mulVec(mat: scala.Array[scala.Float], vec: scala.Array[scala.Float]): scala.Unit = {
    val x: scala.Float = (((vec(0) * mat(Matrix4.M00)) + (vec(1) * mat(Matrix4.M01))) + (vec(2) * mat(Matrix4.M02))) + mat(Matrix4.M03)
    val y: scala.Float = (((vec(0) * mat(Matrix4.M10)) + (vec(1) * mat(Matrix4.M11))) + (vec(2) * mat(Matrix4.M12))) + mat(Matrix4.M13)
    val z: scala.Float = (((vec(0) * mat(Matrix4.M20)) + (vec(1) * mat(Matrix4.M21))) + (vec(2) * mat(Matrix4.M22))) + mat(Matrix4.M23)
    vec(0) = x
    vec(1) = y
    vec(2) = z
  }
  def prj(mat: scala.Array[scala.Float], vec: scala.Array[scala.Float]): scala.Unit = {
    val inv_w: scala.Float = 1.0f / ((((vec(0) * mat(Matrix4.M30)) + (vec(1) * mat(Matrix4.M31))) + (vec(2) * mat(Matrix4.M32))) + mat(Matrix4.M33))
    val x: scala.Float = ((((vec(0) * mat(Matrix4.M00)) + (vec(1) * mat(Matrix4.M01))) + (vec(2) * mat(Matrix4.M02))) + mat(Matrix4.M03)) * inv_w
    val y: scala.Float = ((((vec(0) * mat(Matrix4.M10)) + (vec(1) * mat(Matrix4.M11))) + (vec(2) * mat(Matrix4.M12))) + mat(Matrix4.M13)) * inv_w
    val z: scala.Float = ((((vec(0) * mat(Matrix4.M20)) + (vec(1) * mat(Matrix4.M21))) + (vec(2) * mat(Matrix4.M22))) + mat(Matrix4.M23)) * inv_w
    vec(0) = x
    vec(1) = y
    vec(2) = z
  }
  def rot(mat: scala.Array[scala.Float], vec: scala.Array[scala.Float]): scala.Unit = {
    val x: scala.Float = ((vec(0) * mat(Matrix4.M00)) + (vec(1) * mat(Matrix4.M01))) + (vec(2) * mat(Matrix4.M02))
    val y: scala.Float = ((vec(0) * mat(Matrix4.M10)) + (vec(1) * mat(Matrix4.M11))) + (vec(2) * mat(Matrix4.M12))
    val z: scala.Float = ((vec(0) * mat(Matrix4.M20)) + (vec(1) * mat(Matrix4.M21))) + (vec(2) * mat(Matrix4.M22))
    vec(0) = x
    vec(1) = y
    vec(2) = z
  }
  def inv(values: scala.Array[scala.Float]): scala.Boolean = {
    val l_det: scala.Float = Matrix4.det(values)
    if (l_det == 0) {
      return false
    } else ()
    val m00: scala.Float = ((((((values(Matrix4.M12) * values(Matrix4.M23)) * values(Matrix4.M31)) - ((values(Matrix4.M13) * values(Matrix4.M22)) * values(Matrix4.M31))) + ((values(Matrix4.M13) * values(Matrix4.M21)) * values(Matrix4.M32))) - ((values(Matrix4.M11) * values(Matrix4.M23)) * values(Matrix4.M32))) - ((values(Matrix4.M12) * values(Matrix4.M21)) * values(Matrix4.M33))) + ((values(Matrix4.M11) * values(Matrix4.M22)) * values(Matrix4.M33))
    val m01: scala.Float = ((((((values(Matrix4.M03) * values(Matrix4.M22)) * values(Matrix4.M31)) - ((values(Matrix4.M02) * values(Matrix4.M23)) * values(Matrix4.M31))) - ((values(Matrix4.M03) * values(Matrix4.M21)) * values(Matrix4.M32))) + ((values(Matrix4.M01) * values(Matrix4.M23)) * values(Matrix4.M32))) + ((values(Matrix4.M02) * values(Matrix4.M21)) * values(Matrix4.M33))) - ((values(Matrix4.M01) * values(Matrix4.M22)) * values(Matrix4.M33))
    val m02: scala.Float = ((((((values(Matrix4.M02) * values(Matrix4.M13)) * values(Matrix4.M31)) - ((values(Matrix4.M03) * values(Matrix4.M12)) * values(Matrix4.M31))) + ((values(Matrix4.M03) * values(Matrix4.M11)) * values(Matrix4.M32))) - ((values(Matrix4.M01) * values(Matrix4.M13)) * values(Matrix4.M32))) - ((values(Matrix4.M02) * values(Matrix4.M11)) * values(Matrix4.M33))) + ((values(Matrix4.M01) * values(Matrix4.M12)) * values(Matrix4.M33))
    val m03: scala.Float = ((((((values(Matrix4.M03) * values(Matrix4.M12)) * values(Matrix4.M21)) - ((values(Matrix4.M02) * values(Matrix4.M13)) * values(Matrix4.M21))) - ((values(Matrix4.M03) * values(Matrix4.M11)) * values(Matrix4.M22))) + ((values(Matrix4.M01) * values(Matrix4.M13)) * values(Matrix4.M22))) + ((values(Matrix4.M02) * values(Matrix4.M11)) * values(Matrix4.M23))) - ((values(Matrix4.M01) * values(Matrix4.M12)) * values(Matrix4.M23))
    val m10: scala.Float = ((((((values(Matrix4.M13) * values(Matrix4.M22)) * values(Matrix4.M30)) - ((values(Matrix4.M12) * values(Matrix4.M23)) * values(Matrix4.M30))) - ((values(Matrix4.M13) * values(Matrix4.M20)) * values(Matrix4.M32))) + ((values(Matrix4.M10) * values(Matrix4.M23)) * values(Matrix4.M32))) + ((values(Matrix4.M12) * values(Matrix4.M20)) * values(Matrix4.M33))) - ((values(Matrix4.M10) * values(Matrix4.M22)) * values(Matrix4.M33))
    val m11: scala.Float = ((((((values(Matrix4.M02) * values(Matrix4.M23)) * values(Matrix4.M30)) - ((values(Matrix4.M03) * values(Matrix4.M22)) * values(Matrix4.M30))) + ((values(Matrix4.M03) * values(Matrix4.M20)) * values(Matrix4.M32))) - ((values(Matrix4.M00) * values(Matrix4.M23)) * values(Matrix4.M32))) - ((values(Matrix4.M02) * values(Matrix4.M20)) * values(Matrix4.M33))) + ((values(Matrix4.M00) * values(Matrix4.M22)) * values(Matrix4.M33))
    val m12: scala.Float = ((((((values(Matrix4.M03) * values(Matrix4.M12)) * values(Matrix4.M30)) - ((values(Matrix4.M02) * values(Matrix4.M13)) * values(Matrix4.M30))) - ((values(Matrix4.M03) * values(Matrix4.M10)) * values(Matrix4.M32))) + ((values(Matrix4.M00) * values(Matrix4.M13)) * values(Matrix4.M32))) + ((values(Matrix4.M02) * values(Matrix4.M10)) * values(Matrix4.M33))) - ((values(Matrix4.M00) * values(Matrix4.M12)) * values(Matrix4.M33))
    val m13: scala.Float = ((((((values(Matrix4.M02) * values(Matrix4.M13)) * values(Matrix4.M20)) - ((values(Matrix4.M03) * values(Matrix4.M12)) * values(Matrix4.M20))) + ((values(Matrix4.M03) * values(Matrix4.M10)) * values(Matrix4.M22))) - ((values(Matrix4.M00) * values(Matrix4.M13)) * values(Matrix4.M22))) - ((values(Matrix4.M02) * values(Matrix4.M10)) * values(Matrix4.M23))) + ((values(Matrix4.M00) * values(Matrix4.M12)) * values(Matrix4.M23))
    val m20: scala.Float = ((((((values(Matrix4.M11) * values(Matrix4.M23)) * values(Matrix4.M30)) - ((values(Matrix4.M13) * values(Matrix4.M21)) * values(Matrix4.M30))) + ((values(Matrix4.M13) * values(Matrix4.M20)) * values(Matrix4.M31))) - ((values(Matrix4.M10) * values(Matrix4.M23)) * values(Matrix4.M31))) - ((values(Matrix4.M11) * values(Matrix4.M20)) * values(Matrix4.M33))) + ((values(Matrix4.M10) * values(Matrix4.M21)) * values(Matrix4.M33))
    val m21: scala.Float = ((((((values(Matrix4.M03) * values(Matrix4.M21)) * values(Matrix4.M30)) - ((values(Matrix4.M01) * values(Matrix4.M23)) * values(Matrix4.M30))) - ((values(Matrix4.M03) * values(Matrix4.M20)) * values(Matrix4.M31))) + ((values(Matrix4.M00) * values(Matrix4.M23)) * values(Matrix4.M31))) + ((values(Matrix4.M01) * values(Matrix4.M20)) * values(Matrix4.M33))) - ((values(Matrix4.M00) * values(Matrix4.M21)) * values(Matrix4.M33))
    val m22: scala.Float = ((((((values(Matrix4.M01) * values(Matrix4.M13)) * values(Matrix4.M30)) - ((values(Matrix4.M03) * values(Matrix4.M11)) * values(Matrix4.M30))) + ((values(Matrix4.M03) * values(Matrix4.M10)) * values(Matrix4.M31))) - ((values(Matrix4.M00) * values(Matrix4.M13)) * values(Matrix4.M31))) - ((values(Matrix4.M01) * values(Matrix4.M10)) * values(Matrix4.M33))) + ((values(Matrix4.M00) * values(Matrix4.M11)) * values(Matrix4.M33))
    val m23: scala.Float = ((((((values(Matrix4.M03) * values(Matrix4.M11)) * values(Matrix4.M20)) - ((values(Matrix4.M01) * values(Matrix4.M13)) * values(Matrix4.M20))) - ((values(Matrix4.M03) * values(Matrix4.M10)) * values(Matrix4.M21))) + ((values(Matrix4.M00) * values(Matrix4.M13)) * values(Matrix4.M21))) + ((values(Matrix4.M01) * values(Matrix4.M10)) * values(Matrix4.M23))) - ((values(Matrix4.M00) * values(Matrix4.M11)) * values(Matrix4.M23))
    val m30: scala.Float = ((((((values(Matrix4.M12) * values(Matrix4.M21)) * values(Matrix4.M30)) - ((values(Matrix4.M11) * values(Matrix4.M22)) * values(Matrix4.M30))) - ((values(Matrix4.M12) * values(Matrix4.M20)) * values(Matrix4.M31))) + ((values(Matrix4.M10) * values(Matrix4.M22)) * values(Matrix4.M31))) + ((values(Matrix4.M11) * values(Matrix4.M20)) * values(Matrix4.M32))) - ((values(Matrix4.M10) * values(Matrix4.M21)) * values(Matrix4.M32))
    val m31: scala.Float = ((((((values(Matrix4.M01) * values(Matrix4.M22)) * values(Matrix4.M30)) - ((values(Matrix4.M02) * values(Matrix4.M21)) * values(Matrix4.M30))) + ((values(Matrix4.M02) * values(Matrix4.M20)) * values(Matrix4.M31))) - ((values(Matrix4.M00) * values(Matrix4.M22)) * values(Matrix4.M31))) - ((values(Matrix4.M01) * values(Matrix4.M20)) * values(Matrix4.M32))) + ((values(Matrix4.M00) * values(Matrix4.M21)) * values(Matrix4.M32))
    val m32: scala.Float = ((((((values(Matrix4.M02) * values(Matrix4.M11)) * values(Matrix4.M30)) - ((values(Matrix4.M01) * values(Matrix4.M12)) * values(Matrix4.M30))) - ((values(Matrix4.M02) * values(Matrix4.M10)) * values(Matrix4.M31))) + ((values(Matrix4.M00) * values(Matrix4.M12)) * values(Matrix4.M31))) + ((values(Matrix4.M01) * values(Matrix4.M10)) * values(Matrix4.M32))) - ((values(Matrix4.M00) * values(Matrix4.M11)) * values(Matrix4.M32))
    val m33: scala.Float = ((((((values(Matrix4.M01) * values(Matrix4.M12)) * values(Matrix4.M20)) - ((values(Matrix4.M02) * values(Matrix4.M11)) * values(Matrix4.M20))) + ((values(Matrix4.M02) * values(Matrix4.M10)) * values(Matrix4.M21))) - ((values(Matrix4.M00) * values(Matrix4.M12)) * values(Matrix4.M21))) - ((values(Matrix4.M01) * values(Matrix4.M10)) * values(Matrix4.M22))) + ((values(Matrix4.M00) * values(Matrix4.M11)) * values(Matrix4.M22))
    val inv_det: scala.Float = 1.0f / l_det
    values(Matrix4.M00) = m00 * inv_det
    values(Matrix4.M10) = m10 * inv_det
    values(Matrix4.M20) = m20 * inv_det
    values(Matrix4.M30) = m30 * inv_det
    values(Matrix4.M01) = m01 * inv_det
    values(Matrix4.M11) = m11 * inv_det
    values(Matrix4.M21) = m21 * inv_det
    values(Matrix4.M31) = m31 * inv_det
    values(Matrix4.M02) = m02 * inv_det
    values(Matrix4.M12) = m12 * inv_det
    values(Matrix4.M22) = m22 * inv_det
    values(Matrix4.M32) = m32 * inv_det
    values(Matrix4.M03) = m03 * inv_det
    values(Matrix4.M13) = m13 * inv_det
    values(Matrix4.M23) = m23 * inv_det
    values(Matrix4.M33) = m33 * inv_det
    return true
  }
  def det(values: scala.Array[scala.Float]): scala.Float = {
    return (((((((((((((((((((((((((values(Matrix4.M30) * values(Matrix4.M21)) * values(Matrix4.M12)) * values(Matrix4.M03)) - (((values(Matrix4.M20) * values(Matrix4.M31)) * values(Matrix4.M12)) * values(Matrix4.M03))) - (((values(Matrix4.M30) * values(Matrix4.M11)) * values(Matrix4.M22)) * values(Matrix4.M03))) + (((values(Matrix4.M10) * values(Matrix4.M31)) * values(Matrix4.M22)) * values(Matrix4.M03))) + (((values(Matrix4.M20) * values(Matrix4.M11)) * values(Matrix4.M32)) * values(Matrix4.M03))) - (((values(Matrix4.M10) * values(Matrix4.M21)) * values(Matrix4.M32)) * values(Matrix4.M03))) - (((values(Matrix4.M30) * values(Matrix4.M21)) * values(Matrix4.M02)) * values(Matrix4.M13))) + (((values(Matrix4.M20) * values(Matrix4.M31)) * values(Matrix4.M02)) * values(Matrix4.M13))) + (((values(Matrix4.M30) * values(Matrix4.M01)) * values(Matrix4.M22)) * values(Matrix4.M13))) - (((values(Matrix4.M00) * values(Matrix4.M31)) * values(Matrix4.M22)) * values(Matrix4.M13))) - (((values(Matrix4.M20) * values(Matrix4.M01)) * values(Matrix4.M32)) * values(Matrix4.M13))) + (((values(Matrix4.M00) * values(Matrix4.M21)) * values(Matrix4.M32)) * values(Matrix4.M13))) + (((values(Matrix4.M30) * values(Matrix4.M11)) * values(Matrix4.M02)) * values(Matrix4.M23))) - (((values(Matrix4.M10) * values(Matrix4.M31)) * values(Matrix4.M02)) * values(Matrix4.M23))) - (((values(Matrix4.M30) * values(Matrix4.M01)) * values(Matrix4.M12)) * values(Matrix4.M23))) + (((values(Matrix4.M00) * values(Matrix4.M31)) * values(Matrix4.M12)) * values(Matrix4.M23))) + (((values(Matrix4.M10) * values(Matrix4.M01)) * values(Matrix4.M32)) * values(Matrix4.M23))) - (((values(Matrix4.M00) * values(Matrix4.M11)) * values(Matrix4.M32)) * values(Matrix4.M23))) - (((values(Matrix4.M20) * values(Matrix4.M11)) * values(Matrix4.M02)) * values(Matrix4.M33))) + (((values(Matrix4.M10) * values(Matrix4.M21)) * values(Matrix4.M02)) * values(Matrix4.M33))) + (((values(Matrix4.M20) * values(Matrix4.M01)) * values(Matrix4.M12)) * values(Matrix4.M33))) - (((values(Matrix4.M00) * values(Matrix4.M21)) * values(Matrix4.M12)) * values(Matrix4.M33))) - (((values(Matrix4.M10) * values(Matrix4.M01)) * values(Matrix4.M22)) * values(Matrix4.M33))) + (((values(Matrix4.M00) * values(Matrix4.M11)) * values(Matrix4.M22)) * values(Matrix4.M33))
  }
}