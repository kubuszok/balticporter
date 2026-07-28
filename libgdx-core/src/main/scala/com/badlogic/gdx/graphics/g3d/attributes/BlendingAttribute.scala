package com.badlogic.gdx.graphics.g3d.attributes

class BlendingAttribute(blended$p: scala.Boolean, sourceFunc: scala.Int, destFunc: scala.Int, opacity$p: scala.Float) extends com.badlogic.gdx.graphics.g3d.Attribute(BlendingAttribute.Type) {
  var blended: scala.Boolean = false
  var sourceFunction: scala.Int = 0
  var destFunction: scala.Int = 0
  var opacity: scala.Float = 1.0f
  def this(copyFrom: BlendingAttribute) = {
    this((copyFrom == null) || copyFrom.blended, if (copyFrom == null) com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA else copyFrom.sourceFunction, if (copyFrom == null) com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA else copyFrom.destFunction, if (copyFrom == null) 1.0f else copyFrom.opacity)
  }
  def this() = {
    this(null)
  }
  def this(sourceFunc: scala.Int, destFunc: scala.Int, opacity: scala.Float) = {
    this(true, sourceFunc, destFunc, opacity)
  }
  def this(sourceFunc: scala.Int, destFunc: scala.Int) = {
    this(sourceFunc, destFunc, 1.0f)
  }
  def this(blended: scala.Boolean, opacity: scala.Float) = {
    this(blended, com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA, opacity)
  }
  def this(opacity: scala.Float) = {
    this(true, opacity)
  }
  this.blended = blended$p
  this.sourceFunction = sourceFunc
  this.destFunction = destFunc
  this.opacity = opacity$p
  @java.lang.Override
  override def copy(): BlendingAttribute = {
    return new BlendingAttribute(this)
  }
  @java.lang.Override
  override def hashCode(): scala.Int = {
    var result: scala.Int = super.hashCode()
    result = (947 * result) + (if (this.blended) 1 else 0)
    result = (947 * result) + this.sourceFunction
    result = (947 * result) + this.destFunction
    result = (947 * result) + com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.opacity)
    return result
  }
  @java.lang.Override
  override def compareTo(o: com.badlogic.gdx.graphics.g3d.Attribute): scala.Int = {
    if (`type` != o.`type`) {
      return (`type` - o.`type`).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    } else ()
    val other: BlendingAttribute = o.asInstanceOf[BlendingAttribute]
    if (this.blended != other.blended) {
      return if (this.blended) 1 else -1
    } else ()
    if (this.sourceFunction != other.sourceFunction) {
      return this.sourceFunction - other.sourceFunction
    } else ()
    if (this.destFunction != other.destFunction) {
      return this.destFunction - other.destFunction
    } else ()
    return if (com.badlogic.gdx.math.MathUtils.isEqual(this.opacity, other.opacity)) 0 else if (this.opacity < other.opacity) 1 else -1
  }
}
object BlendingAttribute {
  export com.badlogic.gdx.graphics.g3d.Attribute.{Alias => _, Type => _, is => _, *}
  final val Alias: java.lang.String = "blended"
  final val Type: scala.Long = com.badlogic.gdx.graphics.g3d.Attribute.register(BlendingAttribute.Alias)
  final def is(mask: scala.Long): scala.Boolean = {
    return (mask & BlendingAttribute.Type) == mask
  }
}