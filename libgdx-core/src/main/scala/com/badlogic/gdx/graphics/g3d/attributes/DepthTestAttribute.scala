package com.badlogic.gdx.graphics.g3d.attributes

class DepthTestAttribute extends com.badlogic.gdx.graphics.g3d.Attribute {
  var depthFunc: scala.Int = 0
  var depthRangeNear: scala.Float = 0.0f
  var depthRangeFar: scala.Float = 0.0f
  var depthMask: scala.Boolean = false
  def this(`type`: scala.Long, depthFunc: scala.Int, depthRangeNear: scala.Float, depthRangeFar: scala.Float, depthMask: scala.Boolean) = {
    this()
    if (!DepthTestAttribute.is(`type`)) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Invalid type specified")
    } else ()
    this.depthFunc = depthFunc
    this.depthRangeNear = depthRangeNear
    this.depthRangeFar = depthRangeFar
    this.depthMask = depthMask
  }
  def this(depthFunc: scala.Int, depthRangeNear: scala.Float, depthRangeFar: scala.Float, depthMask: scala.Boolean) = {
    this(DepthTestAttribute.Type, depthFunc, depthRangeNear, depthRangeFar, depthMask)
  }
  def this(depthFunc: scala.Int, depthRangeNear: scala.Float, depthRangeFar: scala.Float) = {
    this(depthFunc, depthRangeNear, depthRangeFar, true)
  }
  def this(depthFunc: scala.Int, depthMask: scala.Boolean) = {
    this(depthFunc, 0, 1, depthMask)
  }
  def this(depthMask: scala.Boolean) = {
    this(com.badlogic.gdx.graphics.GL20.GL_LEQUAL, depthMask)
  }
  def this(depthFunc: scala.Int) = {
    this(depthFunc, true)
  }
  def this(rhs: DepthTestAttribute) = {
    this(rhs.`type`, rhs.depthFunc, rhs.depthRangeNear, rhs.depthRangeFar, rhs.depthMask)
  }
  def copy(): com.badlogic.gdx.graphics.g3d.Attribute = {
    return new DepthTestAttribute(this)
  }
  def hashCode(): scala.Int = {
    var result: scala.Int = super.hashCode()
    result = (971 * result) + this.depthFunc
    result = (971 * result) + com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.depthRangeNear)
    result = (971 * result) + com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.depthRangeFar)
    result = (971 * result) + (if (this.depthMask) 1 else 0)
    return result
  }
  def compareTo(o: com.badlogic.gdx.graphics.g3d.Attribute): scala.Int = {
    if (`type` != o.`type`) {
      return (`type` - o.`type`).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    } else ()
    val other: DepthTestAttribute = o.asInstanceOf[DepthTestAttribute]
    if (this.depthFunc != other.depthFunc) {
      return this.depthFunc - other.depthFunc
    } else ()
    if (this.depthMask != other.depthMask) {
      return if (this.depthMask) -1 else 1
    } else ()
    if (!com.badlogic.gdx.math.MathUtils.isEqual(this.depthRangeNear, other.depthRangeNear)) {
      return if (this.depthRangeNear < other.depthRangeNear) -1 else 1
    } else ()
    if (!com.badlogic.gdx.math.MathUtils.isEqual(this.depthRangeFar, other.depthRangeFar)) {
      return if (this.depthRangeFar < other.depthRangeFar) -1 else 1
    } else ()
    return 0
  }
}
object DepthTestAttribute {
  export com.badlogic.gdx.graphics.g3d.Attribute.{Alias => _, Type => _, Mask => _, is => _, *}
  final val Alias: java.lang.String = "depthStencil"
  final val Type: scala.Long = com.badlogic.gdx.graphics.g3d.Attribute.register(DepthTestAttribute.Alias)
  var Mask: scala.Long = DepthTestAttribute.Type
  final def is(mask: scala.Long): scala.Boolean = {
    return (mask & DepthTestAttribute.Mask) != 0
  }
}