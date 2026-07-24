package com.badlogic.gdx.math

class Matrix3 extends java.io.Serializable {
  var `val`: scala.Array[scala.Float] = new scala.Array[scala.Float](9)
  private var tmp: scala.Array[scala.Float] = new scala.Array[scala.Float](9)
  def this(matrix: Matrix3) = {
    this()
    this.set(matrix)
  }
  def this(values: scala.Array[scala.Float]) = {
    this()
    this.set(values)
  }
  def this() = {
    this()
    this.idt()
  }
  def idt(): Matrix3 = {
    val `val`: scala.Array[scala.Float] = this.`val`
    `val`(Matrix3.M00) = 1
    `val`(Matrix3.M10) = 0
    `val`(Matrix3.M20) = 0
    `val`(Matrix3.M01) = 0
    `val`(Matrix3.M11) = 1
    `val`(Matrix3.M21) = 0
    `val`(Matrix3.M02) = 0
    `val`(Matrix3.M12) = 0
    `val`(Matrix3.M22) = 1
    return this
  }
  def mul(m: Matrix3): Matrix3 = {
    val `val`: scala.Array[scala.Float] = this.`val`
    val v00: scala.Float = ((`val`(Matrix3.M00) * m.`val`(Matrix3.M00)) + (`val`(Matrix3.M01) * m.`val`(Matrix3.M10))) + (`val`(Matrix3.M02) * m.`val`(Matrix3.M20))
    val v01: scala.Float = ((`val`(Matrix3.M00) * m.`val`(Matrix3.M01)) + (`val`(Matrix3.M01) * m.`val`(Matrix3.M11))) + (`val`(Matrix3.M02) * m.`val`(Matrix3.M21))
    val v02: scala.Float = ((`val`(Matrix3.M00) * m.`val`(Matrix3.M02)) + (`val`(Matrix3.M01) * m.`val`(Matrix3.M12))) + (`val`(Matrix3.M02) * m.`val`(Matrix3.M22))
    val v10: scala.Float = ((`val`(Matrix3.M10) * m.`val`(Matrix3.M00)) + (`val`(Matrix3.M11) * m.`val`(Matrix3.M10))) + (`val`(Matrix3.M12) * m.`val`(Matrix3.M20))
    val v11: scala.Float = ((`val`(Matrix3.M10) * m.`val`(Matrix3.M01)) + (`val`(Matrix3.M11) * m.`val`(Matrix3.M11))) + (`val`(Matrix3.M12) * m.`val`(Matrix3.M21))
    val v12: scala.Float = ((`val`(Matrix3.M10) * m.`val`(Matrix3.M02)) + (`val`(Matrix3.M11) * m.`val`(Matrix3.M12))) + (`val`(Matrix3.M12) * m.`val`(Matrix3.M22))
    val v20: scala.Float = ((`val`(Matrix3.M20) * m.`val`(Matrix3.M00)) + (`val`(Matrix3.M21) * m.`val`(Matrix3.M10))) + (`val`(Matrix3.M22) * m.`val`(Matrix3.M20))
    val v21: scala.Float = ((`val`(Matrix3.M20) * m.`val`(Matrix3.M01)) + (`val`(Matrix3.M21) * m.`val`(Matrix3.M11))) + (`val`(Matrix3.M22) * m.`val`(Matrix3.M21))
    val v22: scala.Float = ((`val`(Matrix3.M20) * m.`val`(Matrix3.M02)) + (`val`(Matrix3.M21) * m.`val`(Matrix3.M12))) + (`val`(Matrix3.M22) * m.`val`(Matrix3.M22))
    `val`(Matrix3.M00) = v00
    `val`(Matrix3.M10) = v10
    `val`(Matrix3.M20) = v20
    `val`(Matrix3.M01) = v01
    `val`(Matrix3.M11) = v11
    `val`(Matrix3.M21) = v21
    `val`(Matrix3.M02) = v02
    `val`(Matrix3.M12) = v12
    `val`(Matrix3.M22) = v22
    return this
  }
  def mulLeft(m: Matrix3): Matrix3 = {
    val `val`: scala.Array[scala.Float] = this.`val`
    val v00: scala.Float = ((m.`val`(Matrix3.M00) * `val`(Matrix3.M00)) + (m.`val`(Matrix3.M01) * `val`(Matrix3.M10))) + (m.`val`(Matrix3.M02) * `val`(Matrix3.M20))
    val v01: scala.Float = ((m.`val`(Matrix3.M00) * `val`(Matrix3.M01)) + (m.`val`(Matrix3.M01) * `val`(Matrix3.M11))) + (m.`val`(Matrix3.M02) * `val`(Matrix3.M21))
    val v02: scala.Float = ((m.`val`(Matrix3.M00) * `val`(Matrix3.M02)) + (m.`val`(Matrix3.M01) * `val`(Matrix3.M12))) + (m.`val`(Matrix3.M02) * `val`(Matrix3.M22))
    val v10: scala.Float = ((m.`val`(Matrix3.M10) * `val`(Matrix3.M00)) + (m.`val`(Matrix3.M11) * `val`(Matrix3.M10))) + (m.`val`(Matrix3.M12) * `val`(Matrix3.M20))
    val v11: scala.Float = ((m.`val`(Matrix3.M10) * `val`(Matrix3.M01)) + (m.`val`(Matrix3.M11) * `val`(Matrix3.M11))) + (m.`val`(Matrix3.M12) * `val`(Matrix3.M21))
    val v12: scala.Float = ((m.`val`(Matrix3.M10) * `val`(Matrix3.M02)) + (m.`val`(Matrix3.M11) * `val`(Matrix3.M12))) + (m.`val`(Matrix3.M12) * `val`(Matrix3.M22))
    val v20: scala.Float = ((m.`val`(Matrix3.M20) * `val`(Matrix3.M00)) + (m.`val`(Matrix3.M21) * `val`(Matrix3.M10))) + (m.`val`(Matrix3.M22) * `val`(Matrix3.M20))
    val v21: scala.Float = ((m.`val`(Matrix3.M20) * `val`(Matrix3.M01)) + (m.`val`(Matrix3.M21) * `val`(Matrix3.M11))) + (m.`val`(Matrix3.M22) * `val`(Matrix3.M21))
    val v22: scala.Float = ((m.`val`(Matrix3.M20) * `val`(Matrix3.M02)) + (m.`val`(Matrix3.M21) * `val`(Matrix3.M12))) + (m.`val`(Matrix3.M22) * `val`(Matrix3.M22))
    `val`(Matrix3.M00) = v00
    `val`(Matrix3.M10) = v10
    `val`(Matrix3.M20) = v20
    `val`(Matrix3.M01) = v01
    `val`(Matrix3.M11) = v11
    `val`(Matrix3.M21) = v21
    `val`(Matrix3.M02) = v02
    `val`(Matrix3.M12) = v12
    `val`(Matrix3.M22) = v22
    return this
  }
  def setToRotation(degrees: scala.Float): Matrix3 = {
    return this.setToRotationRad(com.badlogic.gdx.math.MathUtils.degreesToRadians * degrees)
  }
  def setToRotationRad(radians: scala.Float): Matrix3 = {
    val cos: scala.Float = java.lang.Math.cos(radians).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    val sin: scala.Float = java.lang.Math.sin(radians).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    val `val`: scala.Array[scala.Float] = this.`val`
    `val`(Matrix3.M00) = cos
    `val`(Matrix3.M10) = sin
    `val`(Matrix3.M20) = 0
    `val`(Matrix3.M01) = -sin
    `val`(Matrix3.M11) = cos
    `val`(Matrix3.M21) = 0
    `val`(Matrix3.M02) = 0
    `val`(Matrix3.M12) = 0
    `val`(Matrix3.M22) = 1
    return this
  }
  def setToRotation(axis: com.badlogic.gdx.math.Vector3, degrees: scala.Float): Matrix3 = {
    return this.setToRotation(axis, com.badlogic.gdx.math.MathUtils.cosDeg(degrees), com.badlogic.gdx.math.MathUtils.sinDeg(degrees))
  }
  def setToRotation(axis: com.badlogic.gdx.math.Vector3, cos: scala.Float, sin: scala.Float): Matrix3 = {
    val `val`: scala.Array[scala.Float] = this.`val`
    val oc: scala.Float = 1.0f - cos
    `val`(Matrix3.M00) = ((oc * axis.x) * axis.x) + cos
    `val`(Matrix3.M01) = ((oc * axis.x) * axis.y) - (axis.z * sin)
    `val`(Matrix3.M02) = ((oc * axis.z) * axis.x) + (axis.y * sin)
    `val`(Matrix3.M10) = ((oc * axis.x) * axis.y) + (axis.z * sin)
    `val`(Matrix3.M11) = ((oc * axis.y) * axis.y) + cos
    `val`(Matrix3.M12) = ((oc * axis.y) * axis.z) - (axis.x * sin)
    `val`(Matrix3.M20) = ((oc * axis.z) * axis.x) - (axis.y * sin)
    `val`(Matrix3.M21) = ((oc * axis.y) * axis.z) + (axis.x * sin)
    `val`(Matrix3.M22) = ((oc * axis.z) * axis.z) + cos
    return this
  }
  def setToTranslation(x: scala.Float, y: scala.Float): Matrix3 = {
    val `val`: scala.Array[scala.Float] = this.`val`
    `val`(Matrix3.M00) = 1
    `val`(Matrix3.M10) = 0
    `val`(Matrix3.M20) = 0
    `val`(Matrix3.M01) = 0
    `val`(Matrix3.M11) = 1
    `val`(Matrix3.M21) = 0
    `val`(Matrix3.M02) = x
    `val`(Matrix3.M12) = y
    `val`(Matrix3.M22) = 1
    return this
  }
  def setToTranslation(translation: com.badlogic.gdx.math.Vector2): Matrix3 = {
    val `val`: scala.Array[scala.Float] = this.`val`
    `val`(Matrix3.M00) = 1
    `val`(Matrix3.M10) = 0
    `val`(Matrix3.M20) = 0
    `val`(Matrix3.M01) = 0
    `val`(Matrix3.M11) = 1
    `val`(Matrix3.M21) = 0
    `val`(Matrix3.M02) = translation.x
    `val`(Matrix3.M12) = translation.y
    `val`(Matrix3.M22) = 1
    return this
  }
  def setToScaling(scaleX: scala.Float, scaleY: scala.Float): Matrix3 = {
    val `val`: scala.Array[scala.Float] = this.`val`
    `val`(Matrix3.M00) = scaleX
    `val`(Matrix3.M10) = 0
    `val`(Matrix3.M20) = 0
    `val`(Matrix3.M01) = 0
    `val`(Matrix3.M11) = scaleY
    `val`(Matrix3.M21) = 0
    `val`(Matrix3.M02) = 0
    `val`(Matrix3.M12) = 0
    `val`(Matrix3.M22) = 1
    return this
  }
  def setToScaling(scale: com.badlogic.gdx.math.Vector2): Matrix3 = {
    val `val`: scala.Array[scala.Float] = this.`val`
    `val`(Matrix3.M00) = scale.x
    `val`(Matrix3.M10) = 0
    `val`(Matrix3.M20) = 0
    `val`(Matrix3.M01) = 0
    `val`(Matrix3.M11) = scale.y
    `val`(Matrix3.M21) = 0
    `val`(Matrix3.M02) = 0
    `val`(Matrix3.M12) = 0
    `val`(Matrix3.M22) = 1
    return this
  }
  def toString(): java.lang.String = {
    val `val`: scala.Array[scala.Float] = this.`val`
    return ((((((((((((((((((("[" + `val`(Matrix3.M00)) + "|") + `val`(Matrix3.M01)) + "|") + `val`(Matrix3.M02)) + "]\n") + "[") + `val`(Matrix3.M10)) + "|") + `val`(Matrix3.M11)) + "|") + `val`(Matrix3.M12)) + "]\n") + "[") + `val`(Matrix3.M20)) + "|") + `val`(Matrix3.M21)) + "|") + `val`(Matrix3.M22)) + "]"
  }
  def det(): scala.Float = {
    val `val`: scala.Array[scala.Float] = this.`val`
    return ((((((`val`(Matrix3.M00) * `val`(Matrix3.M11)) * `val`(Matrix3.M22)) + ((`val`(Matrix3.M01) * `val`(Matrix3.M12)) * `val`(Matrix3.M20))) + ((`val`(Matrix3.M02) * `val`(Matrix3.M10)) * `val`(Matrix3.M21))) - ((`val`(Matrix3.M00) * `val`(Matrix3.M12)) * `val`(Matrix3.M21))) - ((`val`(Matrix3.M01) * `val`(Matrix3.M10)) * `val`(Matrix3.M22))) - ((`val`(Matrix3.M02) * `val`(Matrix3.M11)) * `val`(Matrix3.M20))
  }
  def inv(): Matrix3 = {
    val det: scala.Float = this.det()
    if (det == 0) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Can't invert a singular matrix")
    } else ()
    val inv_det: scala.Float = 1.0f / det
    val `val`: scala.Array[scala.Float] = this.`val`
    val v00: scala.Float = (`val`(Matrix3.M11) * `val`(Matrix3.M22)) - (`val`(Matrix3.M21) * `val`(Matrix3.M12))
    val v10: scala.Float = (`val`(Matrix3.M20) * `val`(Matrix3.M12)) - (`val`(Matrix3.M10) * `val`(Matrix3.M22))
    val v20: scala.Float = (`val`(Matrix3.M10) * `val`(Matrix3.M21)) - (`val`(Matrix3.M20) * `val`(Matrix3.M11))
    val v01: scala.Float = (`val`(Matrix3.M21) * `val`(Matrix3.M02)) - (`val`(Matrix3.M01) * `val`(Matrix3.M22))
    val v11: scala.Float = (`val`(Matrix3.M00) * `val`(Matrix3.M22)) - (`val`(Matrix3.M20) * `val`(Matrix3.M02))
    val v21: scala.Float = (`val`(Matrix3.M20) * `val`(Matrix3.M01)) - (`val`(Matrix3.M00) * `val`(Matrix3.M21))
    val v02: scala.Float = (`val`(Matrix3.M01) * `val`(Matrix3.M12)) - (`val`(Matrix3.M11) * `val`(Matrix3.M02))
    val v12: scala.Float = (`val`(Matrix3.M10) * `val`(Matrix3.M02)) - (`val`(Matrix3.M00) * `val`(Matrix3.M12))
    val v22: scala.Float = (`val`(Matrix3.M00) * `val`(Matrix3.M11)) - (`val`(Matrix3.M10) * `val`(Matrix3.M01))
    `val`(Matrix3.M00) = inv_det * v00
    `val`(Matrix3.M10) = inv_det * v10
    `val`(Matrix3.M20) = inv_det * v20
    `val`(Matrix3.M01) = inv_det * v01
    `val`(Matrix3.M11) = inv_det * v11
    `val`(Matrix3.M21) = inv_det * v21
    `val`(Matrix3.M02) = inv_det * v02
    `val`(Matrix3.M12) = inv_det * v12
    `val`(Matrix3.M22) = inv_det * v22
    return this
  }
  def set(mat: Matrix3): Matrix3 = {
    java.lang.System.arraycopy(mat.`val`, 0, this.`val`, 0, this.`val`.length)
    return this
  }
  def set(affine: com.badlogic.gdx.math.Affine2): Matrix3 = {
    val `val`: scala.Array[scala.Float] = this.`val`
    `val`(Matrix3.M00) = affine.m00
    `val`(Matrix3.M10) = affine.m10
    `val`(Matrix3.M20) = 0
    `val`(Matrix3.M01) = affine.m01
    `val`(Matrix3.M11) = affine.m11
    `val`(Matrix3.M21) = 0
    `val`(Matrix3.M02) = affine.m02
    `val`(Matrix3.M12) = affine.m12
    `val`(Matrix3.M22) = 1
    return this
  }
  def set(mat: com.badlogic.gdx.math.Matrix4): Matrix3 = {
    val `val`: scala.Array[scala.Float] = this.`val`
    `val`(Matrix3.M00) = mat.`val`(com.badlogic.gdx.math.Matrix4.M00)
    `val`(Matrix3.M10) = mat.`val`(com.badlogic.gdx.math.Matrix4.M10)
    `val`(Matrix3.M20) = mat.`val`(com.badlogic.gdx.math.Matrix4.M20)
    `val`(Matrix3.M01) = mat.`val`(com.badlogic.gdx.math.Matrix4.M01)
    `val`(Matrix3.M11) = mat.`val`(com.badlogic.gdx.math.Matrix4.M11)
    `val`(Matrix3.M21) = mat.`val`(com.badlogic.gdx.math.Matrix4.M21)
    `val`(Matrix3.M02) = mat.`val`(com.badlogic.gdx.math.Matrix4.M02)
    `val`(Matrix3.M12) = mat.`val`(com.badlogic.gdx.math.Matrix4.M12)
    `val`(Matrix3.M22) = mat.`val`(com.badlogic.gdx.math.Matrix4.M22)
    return this
  }
  def set(values: scala.Array[scala.Float]): Matrix3 = {
    java.lang.System.arraycopy(values, 0, this.`val`, 0, this.`val`.length)
    return this
  }
  def trn(vector: com.badlogic.gdx.math.Vector2): Matrix3 = {
    this.`val`(Matrix3.M02) = this.`val`(Matrix3.M02) + vector.x
    this.`val`(Matrix3.M12) = this.`val`(Matrix3.M12) + vector.y
    return this
  }
  def trn(x: scala.Float, y: scala.Float): Matrix3 = {
    this.`val`(Matrix3.M02) = this.`val`(Matrix3.M02) + x
    this.`val`(Matrix3.M12) = this.`val`(Matrix3.M12) + y
    return this
  }
  def trn(vector: com.badlogic.gdx.math.Vector3): Matrix3 = {
    this.`val`(Matrix3.M02) = this.`val`(Matrix3.M02) + vector.x
    this.`val`(Matrix3.M12) = this.`val`(Matrix3.M12) + vector.y
    return this
  }
  def translate(x: scala.Float, y: scala.Float): Matrix3 = {
    val tmp: scala.Array[scala.Float] = this.tmp
    tmp(Matrix3.M00) = 1
    tmp(Matrix3.M10) = 0
    tmp(Matrix3.M01) = 0
    tmp(Matrix3.M11) = 1
    tmp(Matrix3.M02) = x
    tmp(Matrix3.M12) = y
    Matrix3.mul(this.`val`, tmp)
    return this
  }
  def translate(translation: com.badlogic.gdx.math.Vector2): Matrix3 = {
    val tmp: scala.Array[scala.Float] = this.tmp
    tmp(Matrix3.M00) = 1
    tmp(Matrix3.M10) = 0
    tmp(Matrix3.M01) = 0
    tmp(Matrix3.M11) = 1
    tmp(Matrix3.M02) = translation.x
    tmp(Matrix3.M12) = translation.y
    Matrix3.mul(this.`val`, tmp)
    return this
  }
  def rotate(degrees: scala.Float): Matrix3 = {
    return this.rotateRad(com.badlogic.gdx.math.MathUtils.degreesToRadians * degrees)
  }
  def rotateRad(radians: scala.Float): Matrix3 = {
    if (radians == 0) {
      return this
    } else ()
    val cos: scala.Float = java.lang.Math.cos(radians).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    val sin: scala.Float = java.lang.Math.sin(radians).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    val tmp: scala.Array[scala.Float] = this.tmp
    tmp(Matrix3.M00) = cos
    tmp(Matrix3.M10) = sin
    tmp(Matrix3.M01) = -sin
    tmp(Matrix3.M11) = cos
    tmp(Matrix3.M02) = 0
    tmp(Matrix3.M12) = 0
    Matrix3.mul(this.`val`, tmp)
    return this
  }
  def scale(scaleX: scala.Float, scaleY: scala.Float): Matrix3 = {
    val tmp: scala.Array[scala.Float] = this.tmp
    tmp(Matrix3.M00) = scaleX
    tmp(Matrix3.M10) = 0
    tmp(Matrix3.M01) = 0
    tmp(Matrix3.M11) = scaleY
    tmp(Matrix3.M02) = 0
    tmp(Matrix3.M12) = 0
    Matrix3.mul(this.`val`, tmp)
    return this
  }
  def scale(scale: com.badlogic.gdx.math.Vector2): Matrix3 = {
    val tmp: scala.Array[scala.Float] = this.tmp
    tmp(Matrix3.M00) = scale.x
    tmp(Matrix3.M10) = 0
    tmp(Matrix3.M01) = 0
    tmp(Matrix3.M11) = scale.y
    tmp(Matrix3.M02) = 0
    tmp(Matrix3.M12) = 0
    Matrix3.mul(this.`val`, tmp)
    return this
  }
  def getValues(): scala.Array[scala.Float] = {
    return this.`val`
  }
  def getTranslation(position: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.math.Vector2 = {
    position.x = this.`val`(Matrix3.M02)
    position.y = this.`val`(Matrix3.M12)
    return position
  }
  def getScale(scale: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.math.Vector2 = {
    val `val`: scala.Array[scala.Float] = this.`val`
    scale.x = java.lang.Math.sqrt((`val`(Matrix3.M00) * `val`(Matrix3.M00)) + (`val`(Matrix3.M01) * `val`(Matrix3.M01))).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    scale.y = java.lang.Math.sqrt((`val`(Matrix3.M10) * `val`(Matrix3.M10)) + (`val`(Matrix3.M11) * `val`(Matrix3.M11))).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    return scale
  }
  def getRotation(): scala.Float = {
    return com.badlogic.gdx.math.MathUtils.radiansToDegrees * java.lang.Math.atan2(this.`val`(Matrix3.M10), this.`val`(Matrix3.M00)).asInstanceOf[scala.Float]
  }
  def getRotationRad(): scala.Float = {
    return java.lang.Math.atan2(this.`val`(Matrix3.M10), this.`val`(Matrix3.M00)).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  }
  def scl(scale: scala.Float): Matrix3 = {
    this.`val`(Matrix3.M00) = this.`val`(Matrix3.M00) * scale
    this.`val`(Matrix3.M11) = this.`val`(Matrix3.M11) * scale
    return this
  }
  def scl(scale: com.badlogic.gdx.math.Vector2): Matrix3 = {
    this.`val`(Matrix3.M00) = this.`val`(Matrix3.M00) * scale.x
    this.`val`(Matrix3.M11) = this.`val`(Matrix3.M11) * scale.y
    return this
  }
  def scl(scale: com.badlogic.gdx.math.Vector3): Matrix3 = {
    this.`val`(Matrix3.M00) = this.`val`(Matrix3.M00) * scale.x
    this.`val`(Matrix3.M11) = this.`val`(Matrix3.M11) * scale.y
    return this
  }
  def transpose(): Matrix3 = {
    val `val`: scala.Array[scala.Float] = this.`val`
    val v01: scala.Float = `val`(Matrix3.M10)
    val v02: scala.Float = `val`(Matrix3.M20)
    val v10: scala.Float = `val`(Matrix3.M01)
    val v12: scala.Float = `val`(Matrix3.M21)
    val v20: scala.Float = `val`(Matrix3.M02)
    val v21: scala.Float = `val`(Matrix3.M12)
    `val`(Matrix3.M01) = v01
    `val`(Matrix3.M02) = v02
    `val`(Matrix3.M10) = v10
    `val`(Matrix3.M12) = v12
    `val`(Matrix3.M20) = v20
    `val`(Matrix3.M21) = v21
    return this
  }
}
object Matrix3 {
  private final val serialVersionUID: scala.Long = 7907569533774959788L
  final val M00: scala.Int = 0
  final val M01: scala.Int = 3
  final val M02: scala.Int = 6
  final val M10: scala.Int = 1
  final val M11: scala.Int = 4
  final val M12: scala.Int = 7
  final val M20: scala.Int = 2
  final val M21: scala.Int = 5
  final val M22: scala.Int = 8
  private def mul(mata: scala.Array[scala.Float], matb: scala.Array[scala.Float]): scala.Unit = {
    val v00: scala.Float = ((mata(Matrix3.M00) * matb(Matrix3.M00)) + (mata(Matrix3.M01) * matb(Matrix3.M10))) + (mata(Matrix3.M02) * matb(Matrix3.M20))
    val v01: scala.Float = ((mata(Matrix3.M00) * matb(Matrix3.M01)) + (mata(Matrix3.M01) * matb(Matrix3.M11))) + (mata(Matrix3.M02) * matb(Matrix3.M21))
    val v02: scala.Float = ((mata(Matrix3.M00) * matb(Matrix3.M02)) + (mata(Matrix3.M01) * matb(Matrix3.M12))) + (mata(Matrix3.M02) * matb(Matrix3.M22))
    val v10: scala.Float = ((mata(Matrix3.M10) * matb(Matrix3.M00)) + (mata(Matrix3.M11) * matb(Matrix3.M10))) + (mata(Matrix3.M12) * matb(Matrix3.M20))
    val v11: scala.Float = ((mata(Matrix3.M10) * matb(Matrix3.M01)) + (mata(Matrix3.M11) * matb(Matrix3.M11))) + (mata(Matrix3.M12) * matb(Matrix3.M21))
    val v12: scala.Float = ((mata(Matrix3.M10) * matb(Matrix3.M02)) + (mata(Matrix3.M11) * matb(Matrix3.M12))) + (mata(Matrix3.M12) * matb(Matrix3.M22))
    val v20: scala.Float = ((mata(Matrix3.M20) * matb(Matrix3.M00)) + (mata(Matrix3.M21) * matb(Matrix3.M10))) + (mata(Matrix3.M22) * matb(Matrix3.M20))
    val v21: scala.Float = ((mata(Matrix3.M20) * matb(Matrix3.M01)) + (mata(Matrix3.M21) * matb(Matrix3.M11))) + (mata(Matrix3.M22) * matb(Matrix3.M21))
    val v22: scala.Float = ((mata(Matrix3.M20) * matb(Matrix3.M02)) + (mata(Matrix3.M21) * matb(Matrix3.M12))) + (mata(Matrix3.M22) * matb(Matrix3.M22))
    mata(Matrix3.M00) = v00
    mata(Matrix3.M10) = v10
    mata(Matrix3.M20) = v20
    mata(Matrix3.M01) = v01
    mata(Matrix3.M11) = v11
    mata(Matrix3.M21) = v21
    mata(Matrix3.M02) = v02
    mata(Matrix3.M12) = v12
    mata(Matrix3.M22) = v22
  }
}