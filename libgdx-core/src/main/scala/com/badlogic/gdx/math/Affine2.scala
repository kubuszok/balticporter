package com.badlogic.gdx.math

final class Affine2 extends java.io.Serializable {
  var m00: scala.Float = 1
  var m01: scala.Float = 0
  var m02: scala.Float = 0
  var m10: scala.Float = 0
  var m11: scala.Float = 1
  var m12: scala.Float = 0
  def this(other: Affine2) = {
    this()
    this.set(other)
  }
  def idt(): Affine2 = {
    this.m00 = 1
    this.m01 = 0
    this.m02 = 0
    this.m10 = 0
    this.m11 = 1
    this.m12 = 0
    return this
  }
  def set(other: Affine2): Affine2 = {
    this.m00 = other.m00
    this.m01 = other.m01
    this.m02 = other.m02
    this.m10 = other.m10
    this.m11 = other.m11
    this.m12 = other.m12
    return this
  }
  def set(matrix: com.badlogic.gdx.math.Matrix3): Affine2 = {
    val other: scala.Array[scala.Float] = matrix.`val`
    this.m00 = other(com.badlogic.gdx.math.Matrix3.M00)
    this.m01 = other(com.badlogic.gdx.math.Matrix3.M01)
    this.m02 = other(com.badlogic.gdx.math.Matrix3.M02)
    this.m10 = other(com.badlogic.gdx.math.Matrix3.M10)
    this.m11 = other(com.badlogic.gdx.math.Matrix3.M11)
    this.m12 = other(com.badlogic.gdx.math.Matrix3.M12)
    return this
  }
  def set(matrix: com.badlogic.gdx.math.Matrix4): Affine2 = {
    val other: scala.Array[scala.Float] = matrix.`val`
    this.m00 = other(com.badlogic.gdx.math.Matrix4.M00)
    this.m01 = other(com.badlogic.gdx.math.Matrix4.M01)
    this.m02 = other(com.badlogic.gdx.math.Matrix4.M03)
    this.m10 = other(com.badlogic.gdx.math.Matrix4.M10)
    this.m11 = other(com.badlogic.gdx.math.Matrix4.M11)
    this.m12 = other(com.badlogic.gdx.math.Matrix4.M13)
    return this
  }
  def setToTranslation(x: scala.Float, y: scala.Float): Affine2 = {
    this.m00 = 1
    this.m01 = 0
    this.m02 = x
    this.m10 = 0
    this.m11 = 1
    this.m12 = y
    return this
  }
  def setToTranslation(trn: com.badlogic.gdx.math.Vector2): Affine2 = {
    return this.setToTranslation(trn.x, trn.y)
  }
  def setToScaling(scaleX: scala.Float, scaleY: scala.Float): Affine2 = {
    this.m00 = scaleX
    this.m01 = 0
    this.m02 = 0
    this.m10 = 0
    this.m11 = scaleY
    this.m12 = 0
    return this
  }
  def setToScaling(scale: com.badlogic.gdx.math.Vector2): Affine2 = {
    return this.setToScaling(scale.x, scale.y)
  }
  def setToRotation(degrees: scala.Float): Affine2 = {
    val cos: scala.Float = com.badlogic.gdx.math.MathUtils.cosDeg(degrees)
    val sin: scala.Float = com.badlogic.gdx.math.MathUtils.sinDeg(degrees)
    this.m00 = cos
    this.m01 = -sin
    this.m02 = 0
    this.m10 = sin
    this.m11 = cos
    this.m12 = 0
    return this
  }
  def setToRotationRad(radians: scala.Float): Affine2 = {
    val cos: scala.Float = com.badlogic.gdx.math.MathUtils.cos(radians)
    val sin: scala.Float = com.badlogic.gdx.math.MathUtils.sin(radians)
    this.m00 = cos
    this.m01 = -sin
    this.m02 = 0
    this.m10 = sin
    this.m11 = cos
    this.m12 = 0
    return this
  }
  def setToRotation(cos: scala.Float, sin: scala.Float): Affine2 = {
    this.m00 = cos
    this.m01 = -sin
    this.m02 = 0
    this.m10 = sin
    this.m11 = cos
    this.m12 = 0
    return this
  }
  def setToShearing(shearX: scala.Float, shearY: scala.Float): Affine2 = {
    this.m00 = 1
    this.m01 = shearX
    this.m02 = 0
    this.m10 = shearY
    this.m11 = 1
    this.m12 = 0
    return this
  }
  def setToShearing(shear: com.badlogic.gdx.math.Vector2): Affine2 = {
    return this.setToShearing(shear.x, shear.y)
  }
  def setToTrnRotScl(x: scala.Float, y: scala.Float, degrees: scala.Float, scaleX: scala.Float, scaleY: scala.Float): Affine2 = {
    this.m02 = x
    this.m12 = y
    if (degrees == 0) {
      this.m00 = scaleX
      this.m01 = 0
      this.m10 = 0
      this.m11 = scaleY
    } else {
      val sin: scala.Float = com.badlogic.gdx.math.MathUtils.sinDeg(degrees)
      val cos: scala.Float = com.badlogic.gdx.math.MathUtils.cosDeg(degrees)
      this.m00 = cos * scaleX
      this.m01 = (-sin) * scaleY
      this.m10 = sin * scaleX
      this.m11 = cos * scaleY
    }
    return this
  }
  def setToTrnRotScl(trn: com.badlogic.gdx.math.Vector2, degrees: scala.Float, scale: com.badlogic.gdx.math.Vector2): Affine2 = {
    return this.setToTrnRotScl(trn.x, trn.y, degrees, scale.x, scale.y)
  }
  def setToTrnRotRadScl(x: scala.Float, y: scala.Float, radians: scala.Float, scaleX: scala.Float, scaleY: scala.Float): Affine2 = {
    this.m02 = x
    this.m12 = y
    if (radians == 0) {
      this.m00 = scaleX
      this.m01 = 0
      this.m10 = 0
      this.m11 = scaleY
    } else {
      val sin: scala.Float = com.badlogic.gdx.math.MathUtils.sin(radians)
      val cos: scala.Float = com.badlogic.gdx.math.MathUtils.cos(radians)
      this.m00 = cos * scaleX
      this.m01 = (-sin) * scaleY
      this.m10 = sin * scaleX
      this.m11 = cos * scaleY
    }
    return this
  }
  def setToTrnRotRadScl(trn: com.badlogic.gdx.math.Vector2, radians: scala.Float, scale: com.badlogic.gdx.math.Vector2): Affine2 = {
    return this.setToTrnRotRadScl(trn.x, trn.y, radians, scale.x, scale.y)
  }
  def setToTrnScl(x: scala.Float, y: scala.Float, scaleX: scala.Float, scaleY: scala.Float): Affine2 = {
    this.m00 = scaleX
    this.m01 = 0
    this.m02 = x
    this.m10 = 0
    this.m11 = scaleY
    this.m12 = y
    return this
  }
  def setToTrnScl(trn: com.badlogic.gdx.math.Vector2, scale: com.badlogic.gdx.math.Vector2): Affine2 = {
    return this.setToTrnScl(trn.x, trn.y, scale.x, scale.y)
  }
  def setToProduct(l: Affine2, r: Affine2): Affine2 = {
    this.m00 = (l.m00 * r.m00) + (l.m01 * r.m10)
    this.m01 = (l.m00 * r.m01) + (l.m01 * r.m11)
    this.m02 = ((l.m00 * r.m02) + (l.m01 * r.m12)) + l.m02
    this.m10 = (l.m10 * r.m00) + (l.m11 * r.m10)
    this.m11 = (l.m10 * r.m01) + (l.m11 * r.m11)
    this.m12 = ((l.m10 * r.m02) + (l.m11 * r.m12)) + l.m12
    return this
  }
  def inv(): Affine2 = {
    val det: scala.Float = this.det()
    if (det == 0) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Can't invert a singular affine matrix")
    } else ()
    val invDet: scala.Float = 1.0f / det
    val tmp00: scala.Float = this.m11
    val tmp01: scala.Float = -this.m01
    val tmp02: scala.Float = (this.m01 * this.m12) - (this.m11 * this.m02)
    val tmp10: scala.Float = -this.m10
    val tmp11: scala.Float = this.m00
    val tmp12: scala.Float = (this.m10 * this.m02) - (this.m00 * this.m12)
    this.m00 = invDet * tmp00
    this.m01 = invDet * tmp01
    this.m02 = invDet * tmp02
    this.m10 = invDet * tmp10
    this.m11 = invDet * tmp11
    this.m12 = invDet * tmp12
    return this
  }
  def mul(other: Affine2): Affine2 = {
    val tmp00: scala.Float = (this.m00 * other.m00) + (this.m01 * other.m10)
    val tmp01: scala.Float = (this.m00 * other.m01) + (this.m01 * other.m11)
    val tmp02: scala.Float = ((this.m00 * other.m02) + (this.m01 * other.m12)) + this.m02
    val tmp10: scala.Float = (this.m10 * other.m00) + (this.m11 * other.m10)
    val tmp11: scala.Float = (this.m10 * other.m01) + (this.m11 * other.m11)
    val tmp12: scala.Float = ((this.m10 * other.m02) + (this.m11 * other.m12)) + this.m12
    this.m00 = tmp00
    this.m01 = tmp01
    this.m02 = tmp02
    this.m10 = tmp10
    this.m11 = tmp11
    this.m12 = tmp12
    return this
  }
  def preMul(other: Affine2): Affine2 = {
    val tmp00: scala.Float = (other.m00 * this.m00) + (other.m01 * this.m10)
    val tmp01: scala.Float = (other.m00 * this.m01) + (other.m01 * this.m11)
    val tmp02: scala.Float = ((other.m00 * this.m02) + (other.m01 * this.m12)) + other.m02
    val tmp10: scala.Float = (other.m10 * this.m00) + (other.m11 * this.m10)
    val tmp11: scala.Float = (other.m10 * this.m01) + (other.m11 * this.m11)
    val tmp12: scala.Float = ((other.m10 * this.m02) + (other.m11 * this.m12)) + other.m12
    this.m00 = tmp00
    this.m01 = tmp01
    this.m02 = tmp02
    this.m10 = tmp10
    this.m11 = tmp11
    this.m12 = tmp12
    return this
  }
  def translate(x: scala.Float, y: scala.Float): Affine2 = {
    this.m02 = this.m02 + ((this.m00 * x) + (this.m01 * y))
    this.m12 = this.m12 + ((this.m10 * x) + (this.m11 * y))
    return this
  }
  def translate(trn: com.badlogic.gdx.math.Vector2): Affine2 = {
    return this.translate(trn.x, trn.y)
  }
  def preTranslate(x: scala.Float, y: scala.Float): Affine2 = {
    this.m02 = this.m02 + x
    this.m12 = this.m12 + y
    return this
  }
  def preTranslate(trn: com.badlogic.gdx.math.Vector2): Affine2 = {
    return this.preTranslate(trn.x, trn.y)
  }
  def scale(scaleX: scala.Float, scaleY: scala.Float): Affine2 = {
    this.m00 = this.m00 * scaleX
    this.m01 = this.m01 * scaleY
    this.m10 = this.m10 * scaleX
    this.m11 = this.m11 * scaleY
    return this
  }
  def scale(scale: com.badlogic.gdx.math.Vector2): Affine2 = {
    return this.scale(scale.x, scale.y)
  }
  def preScale(scaleX: scala.Float, scaleY: scala.Float): Affine2 = {
    this.m00 = this.m00 * scaleX
    this.m01 = this.m01 * scaleX
    this.m02 = this.m02 * scaleX
    this.m10 = this.m10 * scaleY
    this.m11 = this.m11 * scaleY
    this.m12 = this.m12 * scaleY
    return this
  }
  def preScale(scale: com.badlogic.gdx.math.Vector2): Affine2 = {
    return this.preScale(scale.x, scale.y)
  }
  def rotate(degrees: scala.Float): Affine2 = {
    if (degrees == 0) {
      return this
    } else ()
    val cos: scala.Float = com.badlogic.gdx.math.MathUtils.cosDeg(degrees)
    val sin: scala.Float = com.badlogic.gdx.math.MathUtils.sinDeg(degrees)
    val tmp00: scala.Float = (this.m00 * cos) + (this.m01 * sin)
    val tmp01: scala.Float = (this.m00 * (-sin)) + (this.m01 * cos)
    val tmp10: scala.Float = (this.m10 * cos) + (this.m11 * sin)
    val tmp11: scala.Float = (this.m10 * (-sin)) + (this.m11 * cos)
    this.m00 = tmp00
    this.m01 = tmp01
    this.m10 = tmp10
    this.m11 = tmp11
    return this
  }
  def rotateRad(radians: scala.Float): Affine2 = {
    if (radians == 0) {
      return this
    } else ()
    val cos: scala.Float = com.badlogic.gdx.math.MathUtils.cos(radians)
    val sin: scala.Float = com.badlogic.gdx.math.MathUtils.sin(radians)
    val tmp00: scala.Float = (this.m00 * cos) + (this.m01 * sin)
    val tmp01: scala.Float = (this.m00 * (-sin)) + (this.m01 * cos)
    val tmp10: scala.Float = (this.m10 * cos) + (this.m11 * sin)
    val tmp11: scala.Float = (this.m10 * (-sin)) + (this.m11 * cos)
    this.m00 = tmp00
    this.m01 = tmp01
    this.m10 = tmp10
    this.m11 = tmp11
    return this
  }
  def preRotate(degrees: scala.Float): Affine2 = {
    if (degrees == 0) {
      return this
    } else ()
    val cos: scala.Float = com.badlogic.gdx.math.MathUtils.cosDeg(degrees)
    val sin: scala.Float = com.badlogic.gdx.math.MathUtils.sinDeg(degrees)
    val tmp00: scala.Float = (cos * this.m00) - (sin * this.m10)
    val tmp01: scala.Float = (cos * this.m01) - (sin * this.m11)
    val tmp02: scala.Float = (cos * this.m02) - (sin * this.m12)
    val tmp10: scala.Float = (sin * this.m00) + (cos * this.m10)
    val tmp11: scala.Float = (sin * this.m01) + (cos * this.m11)
    val tmp12: scala.Float = (sin * this.m02) + (cos * this.m12)
    this.m00 = tmp00
    this.m01 = tmp01
    this.m02 = tmp02
    this.m10 = tmp10
    this.m11 = tmp11
    this.m12 = tmp12
    return this
  }
  def preRotateRad(radians: scala.Float): Affine2 = {
    if (radians == 0) {
      return this
    } else ()
    val cos: scala.Float = com.badlogic.gdx.math.MathUtils.cos(radians)
    val sin: scala.Float = com.badlogic.gdx.math.MathUtils.sin(radians)
    val tmp00: scala.Float = (cos * this.m00) - (sin * this.m10)
    val tmp01: scala.Float = (cos * this.m01) - (sin * this.m11)
    val tmp02: scala.Float = (cos * this.m02) - (sin * this.m12)
    val tmp10: scala.Float = (sin * this.m00) + (cos * this.m10)
    val tmp11: scala.Float = (sin * this.m01) + (cos * this.m11)
    val tmp12: scala.Float = (sin * this.m02) + (cos * this.m12)
    this.m00 = tmp00
    this.m01 = tmp01
    this.m02 = tmp02
    this.m10 = tmp10
    this.m11 = tmp11
    this.m12 = tmp12
    return this
  }
  def shear(shearX: scala.Float, shearY: scala.Float): Affine2 = {
    var tmp0: scala.Float = this.m00 + (shearY * this.m01)
    var tmp1: scala.Float = this.m01 + (shearX * this.m00)
    this.m00 = tmp0
    this.m01 = tmp1
    tmp0 = this.m10 + (shearY * this.m11)
    tmp1 = this.m11 + (shearX * this.m10)
    this.m10 = tmp0
    this.m11 = tmp1
    return this
  }
  def shear(shear: com.badlogic.gdx.math.Vector2): Affine2 = {
    return this.shear(shear.x, shear.y)
  }
  def preShear(shearX: scala.Float, shearY: scala.Float): Affine2 = {
    val tmp00: scala.Float = this.m00 + (shearX * this.m10)
    val tmp01: scala.Float = this.m01 + (shearX * this.m11)
    val tmp02: scala.Float = this.m02 + (shearX * this.m12)
    val tmp10: scala.Float = this.m10 + (shearY * this.m00)
    val tmp11: scala.Float = this.m11 + (shearY * this.m01)
    val tmp12: scala.Float = this.m12 + (shearY * this.m02)
    this.m00 = tmp00
    this.m01 = tmp01
    this.m02 = tmp02
    this.m10 = tmp10
    this.m11 = tmp11
    this.m12 = tmp12
    return this
  }
  def preShear(shear: com.badlogic.gdx.math.Vector2): Affine2 = {
    return this.preShear(shear.x, shear.y)
  }
  def det(): scala.Float = {
    return (this.m00 * this.m11) - (this.m01 * this.m10)
  }
  def getTranslation(position: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.math.Vector2 = {
    position.x = this.m02
    position.y = this.m12
    return position
  }
  def isTranslation(): scala.Boolean = {
    return (((this.m00 == 1) && (this.m11 == 1)) && (this.m01 == 0)) && (this.m10 == 0)
  }
  def isIdt(): scala.Boolean = {
    return (((((this.m00 == 1) && (this.m02 == 0)) && (this.m12 == 0)) && (this.m11 == 1)) && (this.m01 == 0)) && (this.m10 == 0)
  }
  def applyTo(point: com.badlogic.gdx.math.Vector2): scala.Unit = {
    var x: scala.Float = point.x
    var y: scala.Float = point.y
    point.x = ((this.m00 * x) + (this.m01 * y)) + this.m02
    point.y = ((this.m10 * x) + (this.m11 * y)) + this.m12
  }
  def toString(): java.lang.String = {
    return ((((((((((("[" + this.m00) + "|") + this.m01) + "|") + this.m02) + "]\n[") + this.m10) + "|") + this.m11) + "|") + this.m12) + "]\n[0.0|0.0|0.1]"
  }
}
object Affine2 {
  private final val serialVersionUID: scala.Long = 1524569123485049187L
}