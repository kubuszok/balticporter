package com.badlogic.gdx.graphics.g3d.attributes

class ColorAttribute(type$p: scala.Long) extends com.badlogic.gdx.graphics.g3d.Attribute(type$p) {
  final val color: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color()
  def this(`type`: scala.Long, color: com.badlogic.gdx.graphics.Color) = {
    this(`type`)
    if (color != null) {
      this.color.set(color)
    } else ()
  }
  def this(`type`: scala.Long, r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float) = {
    this(`type`)
    this.color.set(r, g, b, a)
  }
  def this(copyFrom: ColorAttribute) = {
    this(copyFrom.`type`, copyFrom.color)
  }
  if (!ColorAttribute.is(type$p)) {
    throw new com.badlogic.gdx.utils.GdxRuntimeException("Invalid type specified")
  } else ()
  @java.lang.Override
  override def copy(): com.badlogic.gdx.graphics.g3d.Attribute = {
    return new ColorAttribute(this)
  }
  @java.lang.Override
  override def hashCode(): scala.Int = {
    var result: scala.Int = super.hashCode()
    result = (953 * result) + this.color.toIntBits()
    return result
  }
  @java.lang.Override
  override def compareTo(o: com.badlogic.gdx.graphics.g3d.Attribute): scala.Int = {
    if (`type` != o.`type`) {
      return (`type` - o.`type`).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    } else ()
    return o.asInstanceOf[ColorAttribute].color.toIntBits() - this.color.toIntBits()
  }
}
object ColorAttribute {
  export com.badlogic.gdx.graphics.g3d.Attribute.{Ambient => _, AmbientAlias => _, AmbientLight => _, AmbientLightAlias => _, Diffuse => _, DiffuseAlias => _, Emissive => _, EmissiveAlias => _, Fog => _, FogAlias => _, Mask => _, Reflection => _, ReflectionAlias => _, Specular => _, SpecularAlias => _, createAmbient => _, createAmbientLight => _, createDiffuse => _, createEmissive => _, createFog => _, createReflection => _, createSpecular => _, is => _, *}
  final val DiffuseAlias: java.lang.String = "diffuseColor"
  final val Diffuse: scala.Long = com.badlogic.gdx.graphics.g3d.Attribute.register(ColorAttribute.DiffuseAlias)
  final val SpecularAlias: java.lang.String = "specularColor"
  final val Specular: scala.Long = com.badlogic.gdx.graphics.g3d.Attribute.register(ColorAttribute.SpecularAlias)
  final val AmbientAlias: java.lang.String = "ambientColor"
  final val Ambient: scala.Long = com.badlogic.gdx.graphics.g3d.Attribute.register(ColorAttribute.AmbientAlias)
  final val EmissiveAlias: java.lang.String = "emissiveColor"
  final val Emissive: scala.Long = com.badlogic.gdx.graphics.g3d.Attribute.register(ColorAttribute.EmissiveAlias)
  final val ReflectionAlias: java.lang.String = "reflectionColor"
  final val Reflection: scala.Long = com.badlogic.gdx.graphics.g3d.Attribute.register(ColorAttribute.ReflectionAlias)
  final val AmbientLightAlias: java.lang.String = "ambientLightColor"
  final val AmbientLight: scala.Long = com.badlogic.gdx.graphics.g3d.Attribute.register(ColorAttribute.AmbientLightAlias)
  final val FogAlias: java.lang.String = "fogColor"
  final val Fog: scala.Long = com.badlogic.gdx.graphics.g3d.Attribute.register(ColorAttribute.FogAlias)
  var Mask: scala.Long = (((((ColorAttribute.Ambient | ColorAttribute.Diffuse) | ColorAttribute.Specular) | ColorAttribute.Emissive) | ColorAttribute.Reflection) | ColorAttribute.AmbientLight) | ColorAttribute.Fog
  final def is(mask: scala.Long): scala.Boolean = {
    return (mask & ColorAttribute.Mask) != 0
  }
  final def createAmbient(color: com.badlogic.gdx.graphics.Color): ColorAttribute = {
    return new ColorAttribute(ColorAttribute.Ambient, color)
  }
  final def createAmbient(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): ColorAttribute = {
    return new ColorAttribute(ColorAttribute.Ambient, r, g, b, a)
  }
  final def createDiffuse(color: com.badlogic.gdx.graphics.Color): ColorAttribute = {
    return new ColorAttribute(ColorAttribute.Diffuse, color)
  }
  final def createDiffuse(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): ColorAttribute = {
    return new ColorAttribute(ColorAttribute.Diffuse, r, g, b, a)
  }
  final def createSpecular(color: com.badlogic.gdx.graphics.Color): ColorAttribute = {
    return new ColorAttribute(ColorAttribute.Specular, color)
  }
  final def createSpecular(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): ColorAttribute = {
    return new ColorAttribute(ColorAttribute.Specular, r, g, b, a)
  }
  final def createReflection(color: com.badlogic.gdx.graphics.Color): ColorAttribute = {
    return new ColorAttribute(ColorAttribute.Reflection, color)
  }
  final def createReflection(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): ColorAttribute = {
    return new ColorAttribute(ColorAttribute.Reflection, r, g, b, a)
  }
  final def createEmissive(color: com.badlogic.gdx.graphics.Color): ColorAttribute = {
    return new ColorAttribute(ColorAttribute.Emissive, color)
  }
  final def createEmissive(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): ColorAttribute = {
    return new ColorAttribute(ColorAttribute.Emissive, r, g, b, a)
  }
  final def createAmbientLight(color: com.badlogic.gdx.graphics.Color): ColorAttribute = {
    return new ColorAttribute(ColorAttribute.AmbientLight, color)
  }
  final def createAmbientLight(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): ColorAttribute = {
    return new ColorAttribute(ColorAttribute.AmbientLight, r, g, b, a)
  }
  final def createFog(color: com.badlogic.gdx.graphics.Color): ColorAttribute = {
    return new ColorAttribute(ColorAttribute.Fog, color)
  }
  final def createFog(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): ColorAttribute = {
    return new ColorAttribute(ColorAttribute.Fog, r, g, b, a)
  }
}