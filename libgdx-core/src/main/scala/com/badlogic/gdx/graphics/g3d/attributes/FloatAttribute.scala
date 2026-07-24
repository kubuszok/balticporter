package com.badlogic.gdx.graphics.g3d.attributes

class FloatAttribute extends com.badlogic.gdx.graphics.g3d.Attribute {
  var value: scala.Float = 0.0f
  def this(`type`: scala.Long, value: scala.Float) = {
    this()
    this.value = value
  }
  def this(`type`: scala.Long) = {
    this()
  }
  def copy(): com.badlogic.gdx.graphics.g3d.Attribute = {
    return new FloatAttribute(`type`, this.value)
  }
  def hashCode(): scala.Int = {
    var result: scala.Int = super.hashCode()
    result = (977 * result) + com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.value)
    return result
  }
  def compareTo(o: com.badlogic.gdx.graphics.g3d.Attribute): scala.Int = {
    if (`type` != o.`type`) {
      return (`type` - o.`type`).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    } else ()
    val v: scala.Float = o.asInstanceOf[FloatAttribute].value
    return if (com.badlogic.gdx.math.MathUtils.isEqual(this.value, v)) 0 else if (this.value < v) -1 else 1
  }
}
object FloatAttribute {
  export com.badlogic.gdx.graphics.g3d.Attribute.{ShininessAlias => _, Shininess => _, AlphaTestAlias => _, AlphaTest => _, createShininess => _, createAlphaTest => _, *}
  final val ShininessAlias: java.lang.String = "shininess"
  final val Shininess: scala.Long = com.badlogic.gdx.graphics.g3d.Attribute.register(FloatAttribute.ShininessAlias)
  final val AlphaTestAlias: java.lang.String = "alphaTest"
  final val AlphaTest: scala.Long = com.badlogic.gdx.graphics.g3d.Attribute.register(FloatAttribute.AlphaTestAlias)
  def createShininess(value: scala.Float): FloatAttribute = {
    return new FloatAttribute(FloatAttribute.Shininess, value)
  }
  def createAlphaTest(value: scala.Float): FloatAttribute = {
    return new FloatAttribute(FloatAttribute.AlphaTest, value)
  }
}