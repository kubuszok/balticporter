package com.badlogic.gdx.graphics.g3d.attributes

class CubemapAttribute(type$p: scala.Long) extends com.badlogic.gdx.graphics.g3d.Attribute(type$p) {
  var textureDescription: com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor[com.badlogic.gdx.graphics.Cubemap] = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor[com.badlogic.gdx.graphics.Cubemap]]
  def this(`type`: scala.Long, textureDescription: com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor[com.badlogic.gdx.graphics.Cubemap]) = {
    this(`type`)
    this.textureDescription.set(textureDescription)
  }
  def this(`type`: scala.Long, texture: com.badlogic.gdx.graphics.Cubemap) = {
    this(`type`)
    this.textureDescription.texture = texture
  }
  def this(copyFrom: CubemapAttribute) = {
    this(copyFrom.`type`, copyFrom.textureDescription)
  }
  if (!CubemapAttribute.is(type$p)) {
    throw new com.badlogic.gdx.utils.GdxRuntimeException("Invalid type specified")
  } else ()
  this.textureDescription = new com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor[com.badlogic.gdx.graphics.Cubemap]()
  def copy(): com.badlogic.gdx.graphics.g3d.Attribute = {
    return new CubemapAttribute(this)
  }
  def hashCode(): scala.Int = {
    var result: scala.Int = super.hashCode()
    result = (967 * result) + this.textureDescription.hashCode()
    return result
  }
  def compareTo(o: com.badlogic.gdx.graphics.g3d.Attribute): scala.Int = {
    if (`type` != o.`type`) {
      return (`type` - o.`type`).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    } else ()
    return this.textureDescription.compareTo(o.asInstanceOf[CubemapAttribute].textureDescription)
  }
}
object CubemapAttribute {
  export com.badlogic.gdx.graphics.g3d.Attribute.{EnvironmentMap => _, EnvironmentMapAlias => _, Mask => _, is => _, *}
  final val EnvironmentMapAlias: java.lang.String = "environmentCubemap"
  final val EnvironmentMap: scala.Long = com.badlogic.gdx.graphics.g3d.Attribute.register(CubemapAttribute.EnvironmentMapAlias)
  var Mask: scala.Long = CubemapAttribute.EnvironmentMap
  final def is(mask: scala.Long): scala.Boolean = {
    return (mask & CubemapAttribute.Mask) != 0
  }
}