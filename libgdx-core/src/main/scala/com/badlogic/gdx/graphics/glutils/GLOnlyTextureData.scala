package com.badlogic.gdx.graphics.glutils

class GLOnlyTextureData(width$p: scala.Int, height$p: scala.Int, mipMapLevel: scala.Int, internalFormat$p: scala.Int, format$p: scala.Int, type$p: scala.Int) extends com.badlogic.gdx.graphics.TextureData {
  var width: scala.Int = 0
  var height: scala.Int = 0
  var isPrepared$field: scala.Boolean = false
  var mipLevel: scala.Int = 0
  var internalFormat: scala.Int = 0
  var format: scala.Int = 0
  var `type`: scala.Int = 0
  this.width = width$p
  this.height = height$p
  this.mipLevel = mipMapLevel
  this.internalFormat = internalFormat$p
  this.format = format$p
  this.`type` = type$p
  @java.lang.Override
  override def getType(): com.badlogic.gdx.graphics.TextureData.TextureDataType = {
    return com.badlogic.gdx.graphics.TextureData.TextureDataType.Custom
  }
  @java.lang.Override
  override def isPrepared(): scala.Boolean = {
    return this.isPrepared$field
  }
  @java.lang.Override
  override def prepare(): scala.Unit = {
    if (this.isPrepared$field) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Already prepared")
    } else ()
    this.isPrepared$field = true
  }
  @java.lang.Override
  override def consumeCustomData(target: scala.Int): scala.Unit = {
    com.badlogic.gdx.Gdx.gl.glTexImage2D(target, this.mipLevel, this.internalFormat, this.width, this.height, 0, this.format, this.`type`, null)
  }
  @java.lang.Override
  override def consumePixmap(): com.badlogic.gdx.graphics.Pixmap = {
    throw new com.badlogic.gdx.utils.GdxRuntimeException("This TextureData implementation does not return a Pixmap")
  }
  @java.lang.Override
  override def disposePixmap(): scala.Boolean = {
    throw new com.badlogic.gdx.utils.GdxRuntimeException("This TextureData implementation does not return a Pixmap")
  }
  @java.lang.Override
  override def getWidth(): scala.Int = {
    return this.width
  }
  @java.lang.Override
  override def getHeight(): scala.Int = {
    return this.height
  }
  @java.lang.Override
  override def getFormat(): com.badlogic.gdx.graphics.Pixmap.Format = {
    return com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888
  }
  @java.lang.Override
  override def useMipMaps(): scala.Boolean = {
    return false
  }
  @java.lang.Override
  override def isManaged(): scala.Boolean = {
    return false
  }
}
object GLOnlyTextureData {
  export com.badlogic.gdx.graphics.TextureData.*
}