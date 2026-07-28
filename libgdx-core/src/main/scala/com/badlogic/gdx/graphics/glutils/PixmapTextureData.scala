package com.badlogic.gdx.graphics.glutils

class PixmapTextureData(pixmap$p: com.badlogic.gdx.graphics.Pixmap, format$p: com.badlogic.gdx.graphics.Pixmap.Format, useMipMaps$p: scala.Boolean, disposePixmap$p: scala.Boolean, managed$p: scala.Boolean) extends com.badlogic.gdx.graphics.TextureData {
  var pixmap: com.badlogic.gdx.graphics.Pixmap = null.asInstanceOf[com.badlogic.gdx.graphics.Pixmap]
  var format: com.badlogic.gdx.graphics.Pixmap.Format = null.asInstanceOf[com.badlogic.gdx.graphics.Pixmap.Format]
  var useMipMaps$field: scala.Boolean = false
  var disposePixmap$field: scala.Boolean = false
  var managed: scala.Boolean = false
  def this(pixmap: com.badlogic.gdx.graphics.Pixmap, format: com.badlogic.gdx.graphics.Pixmap.Format, useMipMaps: scala.Boolean, disposePixmap: scala.Boolean) = {
    this(pixmap, format, useMipMaps, disposePixmap, false)
  }
  this.pixmap = pixmap$p
  this.format = if (format$p == null) pixmap$p.getFormat() else format$p
  this.useMipMaps$field = useMipMaps$p
  this.disposePixmap$field = disposePixmap$p
  this.managed = managed$p
  @java.lang.Override
  def disposePixmap(): scala.Boolean = {
    return this.disposePixmap$field
  }
  @java.lang.Override
  def consumePixmap(): com.badlogic.gdx.graphics.Pixmap = {
    return this.pixmap
  }
  @java.lang.Override
  def getWidth(): scala.Int = {
    return this.pixmap.getWidth()
  }
  @java.lang.Override
  def getHeight(): scala.Int = {
    return this.pixmap.getHeight()
  }
  @java.lang.Override
  def getFormat(): com.badlogic.gdx.graphics.Pixmap.Format = {
    return this.format
  }
  @java.lang.Override
  def useMipMaps(): scala.Boolean = {
    return this.useMipMaps$field
  }
  @java.lang.Override
  def isManaged(): scala.Boolean = {
    return this.managed
  }
  @java.lang.Override
  def getType(): com.badlogic.gdx.graphics.TextureData.TextureDataType = {
    return com.badlogic.gdx.graphics.TextureData.TextureDataType.Pixmap
  }
  @java.lang.Override
  def consumeCustomData(target: scala.Int): scala.Unit = {
    throw new com.badlogic.gdx.utils.GdxRuntimeException("This TextureData implementation does not upload data itself")
  }
  @java.lang.Override
  def isPrepared(): scala.Boolean = {
    return true
  }
  @java.lang.Override
  def prepare(): scala.Unit = {
    throw new com.badlogic.gdx.utils.GdxRuntimeException("prepare() must not be called on a PixmapTextureData instance as it is already prepared.")
  }
}
object PixmapTextureData {
  export com.badlogic.gdx.graphics.TextureData.*
}