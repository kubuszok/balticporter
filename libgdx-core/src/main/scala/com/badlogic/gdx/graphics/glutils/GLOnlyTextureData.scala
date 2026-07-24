package com.badlogic.gdx.graphics.glutils

class GLOnlyTextureData extends com.badlogic.gdx.graphics.TextureData {
  var width: scala.Int = 0
  var height: scala.Int = 0
  var isPrepared$field: scala.Boolean = false
  var mipLevel: scala.Int = 0
  var internalFormat: scala.Int = 0
  var format: scala.Int = 0
  var `type`: scala.Int = 0
  def this(width: scala.Int, height: scala.Int, mipMapLevel: scala.Int, internalFormat: scala.Int, format: scala.Int, `type`: scala.Int) = {
    this()
    this.width = width
    this.height = height
    this.mipLevel = mipMapLevel
    this.internalFormat = internalFormat
    this.format = format
    this.`type` = `type`
  }
  def getType(): com.badlogic.gdx.graphics.TextureData.TextureDataType = {
    return com.badlogic.gdx.graphics.TextureData.TextureDataType.Custom
  }
  def isPrepared(): scala.Boolean = {
    return this.isPrepared$field
  }
  def prepare(): scala.Unit = {
    if (this.isPrepared$field) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Already prepared")
    } else ()
    this.isPrepared$field = true
  }
  def consumeCustomData(target: scala.Int): scala.Unit = {
    com.badlogic.gdx.Gdx.gl.glTexImage2D(target, this.mipLevel, this.internalFormat, this.width, this.height, 0, this.format, this.`type`, null)
  }
  def consumePixmap(): com.badlogic.gdx.graphics.Pixmap = {
    throw new com.badlogic.gdx.utils.GdxRuntimeException("This TextureData implementation does not return a Pixmap")
  }
  def disposePixmap(): scala.Boolean = {
    throw new com.badlogic.gdx.utils.GdxRuntimeException("This TextureData implementation does not return a Pixmap")
  }
  def getWidth(): scala.Int = {
    return this.width
  }
  def getHeight(): scala.Int = {
    return this.height
  }
  def getFormat(): com.badlogic.gdx.graphics.Pixmap.Format = {
    return com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888
  }
  def useMipMaps(): scala.Boolean = {
    return false
  }
  def isManaged(): scala.Boolean = {
    return false
  }
}
object GLOnlyTextureData {
  export com.badlogic.gdx.graphics.TextureData.*
}