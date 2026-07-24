package com.badlogic.gdx.graphics.glutils

class PixmapTextureData extends com.badlogic.gdx.graphics.TextureData {
  var pixmap: com.badlogic.gdx.graphics.Pixmap = null.asInstanceOf[com.badlogic.gdx.graphics.Pixmap]
  var format: com.badlogic.gdx.graphics.Pixmap#Format = null.asInstanceOf[com.badlogic.gdx.graphics.Pixmap#Format]
  var useMipMaps$field: scala.Boolean = false
  var disposePixmap$field: scala.Boolean = false
  var managed: scala.Boolean = false
  def this(pixmap: com.badlogic.gdx.graphics.Pixmap, format: com.badlogic.gdx.graphics.Pixmap#Format, useMipMaps: scala.Boolean, disposePixmap: scala.Boolean, managed: scala.Boolean) = {
    this()
    this.pixmap = pixmap
    this.format = if (format == null) pixmap.getFormat() else format
    this.useMipMaps$field = useMipMaps
    this.disposePixmap$field = disposePixmap
    this.managed = managed
  }
  def this(pixmap: com.badlogic.gdx.graphics.Pixmap, format: com.badlogic.gdx.graphics.Pixmap#Format, useMipMaps: scala.Boolean, disposePixmap: scala.Boolean) = {
    this(pixmap, format, useMipMaps, disposePixmap, false)
  }
  def disposePixmap(): scala.Boolean = {
    return this.disposePixmap$field
  }
  def consumePixmap(): com.badlogic.gdx.graphics.Pixmap = {
    return this.pixmap
  }
  def getWidth(): scala.Int = {
    return this.pixmap.getWidth()
  }
  def getHeight(): scala.Int = {
    return this.pixmap.getHeight()
  }
  def getFormat(): com.badlogic.gdx.graphics.Pixmap#Format = {
    return this.format
  }
  def useMipMaps(): scala.Boolean = {
    return this.useMipMaps$field
  }
  def isManaged(): scala.Boolean = {
    return this.managed
  }
  def getType(): com.badlogic.gdx.graphics.TextureData#TextureDataType = {
    return com.badlogic.gdx.graphics.TextureData.TextureDataType.Pixmap
  }
  def consumeCustomData(target: scala.Int): scala.Unit = {
    throw new com.badlogic.gdx.utils.GdxRuntimeException("This TextureData implementation does not upload data itself")
  }
  def isPrepared(): scala.Boolean = {
    return true
  }
  def prepare(): scala.Unit = {
    throw new com.badlogic.gdx.utils.GdxRuntimeException("prepare() must not be called on a PixmapTextureData instance as it is already prepared.")
  }
}