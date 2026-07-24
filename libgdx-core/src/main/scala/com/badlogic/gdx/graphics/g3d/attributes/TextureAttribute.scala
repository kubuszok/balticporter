package com.badlogic.gdx.graphics.g3d.attributes

class TextureAttribute extends com.badlogic.gdx.graphics.g3d.Attribute {
  var textureDescription: com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor[com.badlogic.gdx.graphics.Texture] = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor[com.badlogic.gdx.graphics.Texture]]
  var offsetU: scala.Float = 0
  var offsetV: scala.Float = 0
  var scaleU: scala.Float = 1
  var scaleV: scala.Float = 1
  var uvIndex: scala.Int = 0
  def this(`type`: scala.Long) = {
    this()
    if (!TextureAttribute.is(`type`)) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Invalid type specified")
    } else ()
    this.textureDescription = new com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor[com.badlogic.gdx.graphics.Texture]()
  }
  def this[T <: com.badlogic.gdx.graphics.Texture](`type`: scala.Long, textureDescription: com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor[T], offsetU: scala.Float, offsetV: scala.Float, scaleU: scala.Float, scaleV: scala.Float, uvIndex: scala.Int) = {
    this(`type`, textureDescription)
    this.offsetU = offsetU
    this.offsetV = offsetV
    this.scaleU = scaleU
    this.scaleV = scaleV
    this.uvIndex = uvIndex
  }
  def this[T <: com.badlogic.gdx.graphics.Texture](`type`: scala.Long, textureDescription: com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor[T], offsetU: scala.Float, offsetV: scala.Float, scaleU: scala.Float, scaleV: scala.Float) = {
    this(`type`, textureDescription, offsetU, offsetV, scaleU, scaleV, 0)
  }
  def this[T <: com.badlogic.gdx.graphics.Texture](`type`: scala.Long, textureDescription: com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor[T]) = {
    this(`type`)
    this.textureDescription.set(textureDescription)
  }
  def this(`type`: scala.Long, texture: com.badlogic.gdx.graphics.Texture) = {
    this(`type`)
    this.textureDescription.texture = texture
  }
  def this(`type`: scala.Long, region: com.badlogic.gdx.graphics.g2d.TextureRegion) = {
    this(`type`)
    this.set(region)
  }
  def this(copyFrom: TextureAttribute) = {
    this(copyFrom.`type`, copyFrom.textureDescription, copyFrom.offsetU, copyFrom.offsetV, copyFrom.scaleU, copyFrom.scaleV, copyFrom.uvIndex)
  }
  def set(region: com.badlogic.gdx.graphics.g2d.TextureRegion): scala.Unit = {
    this.textureDescription.texture = region.getTexture()
    this.offsetU = region.getU()
    this.offsetV = region.getV()
    this.scaleU = region.getU2() - this.offsetU
    this.scaleV = region.getV2() - this.offsetV
  }
  def copy(): com.badlogic.gdx.graphics.g3d.Attribute = {
    return new TextureAttribute(this)
  }
  def hashCode(): scala.Int = {
    var result: scala.Int = super.hashCode()
    result = (991 * result) + this.textureDescription.hashCode()
    result = (991 * result) + com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.offsetU)
    result = (991 * result) + com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.offsetV)
    result = (991 * result) + com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.scaleU)
    result = (991 * result) + com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(this.scaleV)
    result = (991 * result) + this.uvIndex
    return result
  }
  def compareTo(o: com.badlogic.gdx.graphics.g3d.Attribute): scala.Int = {
    if (`type` != o.`type`) {
      return if (`type` < o.`type`) -1 else 1
    } else ()
    val other: TextureAttribute = o.asInstanceOf[TextureAttribute]
    val c: scala.Int = this.textureDescription.compareTo(other.textureDescription)
    if (c != 0) {
      return c
    } else ()
    if (this.uvIndex != other.uvIndex) {
      return this.uvIndex - other.uvIndex
    } else ()
    if (!com.badlogic.gdx.math.MathUtils.isEqual(this.scaleU, other.scaleU)) {
      return if (this.scaleU > other.scaleU) 1 else -1
    } else ()
    if (!com.badlogic.gdx.math.MathUtils.isEqual(this.scaleV, other.scaleV)) {
      return if (this.scaleV > other.scaleV) 1 else -1
    } else ()
    if (!com.badlogic.gdx.math.MathUtils.isEqual(this.offsetU, other.offsetU)) {
      return if (this.offsetU > other.offsetU) 1 else -1
    } else ()
    if (!com.badlogic.gdx.math.MathUtils.isEqual(this.offsetV, other.offsetV)) {
      return if (this.offsetV > other.offsetV) 1 else -1
    } else ()
    return 0
  }
}
object TextureAttribute {
  final val DiffuseAlias: java.lang.String = "diffuseTexture"
  final val Diffuse: scala.Long = com.badlogic.gdx.graphics.g3d.Attribute.register(TextureAttribute.DiffuseAlias)
  final val SpecularAlias: java.lang.String = "specularTexture"
  final val Specular: scala.Long = com.badlogic.gdx.graphics.g3d.Attribute.register(TextureAttribute.SpecularAlias)
  final val BumpAlias: java.lang.String = "bumpTexture"
  final val Bump: scala.Long = com.badlogic.gdx.graphics.g3d.Attribute.register(TextureAttribute.BumpAlias)
  final val NormalAlias: java.lang.String = "normalTexture"
  final val Normal: scala.Long = com.badlogic.gdx.graphics.g3d.Attribute.register(TextureAttribute.NormalAlias)
  final val AmbientAlias: java.lang.String = "ambientTexture"
  final val Ambient: scala.Long = com.badlogic.gdx.graphics.g3d.Attribute.register(TextureAttribute.AmbientAlias)
  final val EmissiveAlias: java.lang.String = "emissiveTexture"
  final val Emissive: scala.Long = com.badlogic.gdx.graphics.g3d.Attribute.register(TextureAttribute.EmissiveAlias)
  final val ReflectionAlias: java.lang.String = "reflectionTexture"
  final val Reflection: scala.Long = com.badlogic.gdx.graphics.g3d.Attribute.register(TextureAttribute.ReflectionAlias)
  protected var Mask: scala.Long = (((((TextureAttribute.Diffuse | TextureAttribute.Specular) | TextureAttribute.Bump) | TextureAttribute.Normal) | TextureAttribute.Ambient) | TextureAttribute.Emissive) | TextureAttribute.Reflection
  final def is(mask: scala.Long): scala.Boolean = {
    return (mask & TextureAttribute.Mask) != 0
  }
  def createDiffuse(texture: com.badlogic.gdx.graphics.Texture): TextureAttribute = {
    return new TextureAttribute(TextureAttribute.Diffuse, texture)
  }
  def createDiffuse(region: com.badlogic.gdx.graphics.g2d.TextureRegion): TextureAttribute = {
    return new TextureAttribute(TextureAttribute.Diffuse, region)
  }
  def createSpecular(texture: com.badlogic.gdx.graphics.Texture): TextureAttribute = {
    return new TextureAttribute(TextureAttribute.Specular, texture)
  }
  def createSpecular(region: com.badlogic.gdx.graphics.g2d.TextureRegion): TextureAttribute = {
    return new TextureAttribute(TextureAttribute.Specular, region)
  }
  def createNormal(texture: com.badlogic.gdx.graphics.Texture): TextureAttribute = {
    return new TextureAttribute(TextureAttribute.Normal, texture)
  }
  def createNormal(region: com.badlogic.gdx.graphics.g2d.TextureRegion): TextureAttribute = {
    return new TextureAttribute(TextureAttribute.Normal, region)
  }
  def createBump(texture: com.badlogic.gdx.graphics.Texture): TextureAttribute = {
    return new TextureAttribute(TextureAttribute.Bump, texture)
  }
  def createBump(region: com.badlogic.gdx.graphics.g2d.TextureRegion): TextureAttribute = {
    return new TextureAttribute(TextureAttribute.Bump, region)
  }
  def createAmbient(texture: com.badlogic.gdx.graphics.Texture): TextureAttribute = {
    return new TextureAttribute(TextureAttribute.Ambient, texture)
  }
  def createAmbient(region: com.badlogic.gdx.graphics.g2d.TextureRegion): TextureAttribute = {
    return new TextureAttribute(TextureAttribute.Ambient, region)
  }
  def createEmissive(texture: com.badlogic.gdx.graphics.Texture): TextureAttribute = {
    return new TextureAttribute(TextureAttribute.Emissive, texture)
  }
  def createEmissive(region: com.badlogic.gdx.graphics.g2d.TextureRegion): TextureAttribute = {
    return new TextureAttribute(TextureAttribute.Emissive, region)
  }
  def createReflection(texture: com.badlogic.gdx.graphics.Texture): TextureAttribute = {
    return new TextureAttribute(TextureAttribute.Reflection, texture)
  }
  def createReflection(region: com.badlogic.gdx.graphics.g2d.TextureRegion): TextureAttribute = {
    return new TextureAttribute(TextureAttribute.Reflection, region)
  }
}