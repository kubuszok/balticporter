package com.badlogic.gdx.graphics.g3d.attributes

class IntAttribute extends com.badlogic.gdx.graphics.g3d.Attribute {
  var value: scala.Int = 0
  def this(`type`: scala.Long) = {
    this()
    this.`type` = `type`
    this.typeBit = java.lang.Long.numberOfTrailingZeros(`type`)
  }
  def this(`type`: scala.Long, value: scala.Int) = {
    this()
    this.`type` = `type`
    this.typeBit = java.lang.Long.numberOfTrailingZeros(`type`)
    this.value = value
  }
  def copy(): com.badlogic.gdx.graphics.g3d.Attribute = {
    return new IntAttribute(`type`, this.value)
  }
  def hashCode(): scala.Int = {
    var result: scala.Int = super.hashCode()
    result = (983 * result) + this.value
    return result
  }
  def compareTo(o: com.badlogic.gdx.graphics.g3d.Attribute): scala.Int = {
    if (`type` != o.`type`) {
      return (`type` - o.`type`).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    } else ()
    return this.value - o.asInstanceOf[IntAttribute].value
  }
}
object IntAttribute {
  export com.badlogic.gdx.graphics.g3d.Attribute.{CullFaceAlias => _, CullFace => _, createCullFace => _, *}
  final val CullFaceAlias: java.lang.String = "cullface"
  final val CullFace: scala.Long = com.badlogic.gdx.graphics.g3d.Attribute.register(IntAttribute.CullFaceAlias)
  def createCullFace(value: scala.Int): IntAttribute = {
    return new IntAttribute(IntAttribute.CullFace, value)
  }
}