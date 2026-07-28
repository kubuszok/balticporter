package com.badlogic.gdx.graphics

final class VertexAttribute(usage$p: scala.Int, numComponents$p: scala.Int, type$p: scala.Int, normalized$p: scala.Boolean, alias$p: java.lang.String, unit$p: scala.Int) {
  var usage: scala.Int = 0
  var numComponents: scala.Int = 0
  var normalized: scala.Boolean = false
  var `type`: scala.Int = 0
  var offset: scala.Int = 0
  var alias: java.lang.String = null.asInstanceOf[java.lang.String]
  var unit: scala.Int = 0
  private var usageIndex: scala.Int = 0
  def this(usage: scala.Int, numComponents: scala.Int, alias: java.lang.String, unit: scala.Int) = {
    this(usage, numComponents, if (usage == com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorPacked) com.badlogic.gdx.graphics.GL20.GL_UNSIGNED_BYTE else com.badlogic.gdx.graphics.GL20.GL_FLOAT, usage == com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorPacked, alias, unit)
  }
  def this(usage: scala.Int, numComponents: scala.Int, alias: java.lang.String) = {
    this(usage, numComponents, alias, 0)
  }
  def this(usage: scala.Int, numComponents: scala.Int, `type`: scala.Int, normalized: scala.Boolean, alias: java.lang.String) = {
    this(usage, numComponents, `type`, normalized, alias, 0)
  }
  this.usage = usage$p
  this.numComponents = numComponents$p
  this.`type` = type$p
  this.normalized = normalized$p
  this.alias = alias$p
  this.unit = unit$p
  this.usageIndex = java.lang.Integer.numberOfTrailingZeros(usage$p)
  def copy(): VertexAttribute = {
    return new VertexAttribute(this.usage, this.numComponents, this.`type`, this.normalized, this.alias, this.unit)
  }
  @java.lang.Override
  def equals(obj: java.lang.Object): scala.Boolean = {
    if (!obj.isInstanceOf[VertexAttribute]) {
      return false
    } else ()
    return this.equals(obj.asInstanceOf[VertexAttribute].asInstanceOf[VertexAttribute])
  }
  def equals(other: VertexAttribute): scala.Boolean = {
    return ((((((other != null) && (this.usage == other.usage)) && (this.numComponents == other.numComponents)) && (this.`type` == other.`type`)) && (this.normalized == other.normalized)) && this.alias.equals(other.alias)) && (this.unit == other.unit)
  }
  def getKey(): scala.Int = {
    return (this.usageIndex << 8) + (this.unit & 255)
  }
  def getSizeInBytes(): scala.Int = {
    this.`type` match {
      case com.badlogic.gdx.graphics.GL20.GL_UNSIGNED_INT | com.badlogic.gdx.graphics.GL20.GL_INT | com.badlogic.gdx.graphics.GL20.GL_FLOAT | com.badlogic.gdx.graphics.GL20.GL_FIXED => {
        return 4 * this.numComponents
      }
      case com.badlogic.gdx.graphics.GL20.GL_UNSIGNED_BYTE | com.badlogic.gdx.graphics.GL20.GL_BYTE => {
        return this.numComponents
      }
      case com.badlogic.gdx.graphics.GL20.GL_UNSIGNED_SHORT | com.badlogic.gdx.graphics.GL20.GL_SHORT => {
        return 2 * this.numComponents
      }
    }
    return 0
  }
  @java.lang.Override
  def hashCode(): scala.Int = {
    var result: scala.Int = this.getKey()
    result = (541 * result) + this.numComponents
    result = (541 * result) + this.alias.hashCode()
    return result
  }
}
object VertexAttribute {
  def Position(): VertexAttribute = {
    return new VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Position, 3, com.badlogic.gdx.graphics.glutils.ShaderProgram.POSITION_ATTRIBUTE)
  }
  def TexCoords(unit: scala.Int): VertexAttribute = {
    return new VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.TextureCoordinates, 2, com.badlogic.gdx.graphics.glutils.ShaderProgram.TEXCOORD_ATTRIBUTE + unit, unit)
  }
  def Normal(): VertexAttribute = {
    return new VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal, 3, com.badlogic.gdx.graphics.glutils.ShaderProgram.NORMAL_ATTRIBUTE)
  }
  def ColorPacked(): VertexAttribute = {
    return new VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorPacked, 4, com.badlogic.gdx.graphics.GL20.GL_UNSIGNED_BYTE, true, com.badlogic.gdx.graphics.glutils.ShaderProgram.COLOR_ATTRIBUTE)
  }
  def ColorUnpacked(): VertexAttribute = {
    return new VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorUnpacked, 4, com.badlogic.gdx.graphics.GL20.GL_FLOAT, false, com.badlogic.gdx.graphics.glutils.ShaderProgram.COLOR_ATTRIBUTE)
  }
  def Tangent(): VertexAttribute = {
    return new VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Tangent, 3, com.badlogic.gdx.graphics.glutils.ShaderProgram.TANGENT_ATTRIBUTE)
  }
  def Binormal(): VertexAttribute = {
    return new VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.BiNormal, 3, com.badlogic.gdx.graphics.glutils.ShaderProgram.BINORMAL_ATTRIBUTE)
  }
  def BoneWeight(unit: scala.Int): VertexAttribute = {
    return new VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.BoneWeight, 2, com.badlogic.gdx.graphics.glutils.ShaderProgram.BONEWEIGHT_ATTRIBUTE + unit, unit)
  }
}