package com.badlogic.gdx.graphics.glutils

class FileTextureData(file$p: com.badlogic.gdx.files.FileHandle, preloadedPixmap: com.badlogic.gdx.graphics.Pixmap, format$p: com.badlogic.gdx.graphics.Pixmap.Format, useMipMaps$p: scala.Boolean) extends com.badlogic.gdx.graphics.TextureData {
  var file: com.badlogic.gdx.files.FileHandle = null.asInstanceOf[com.badlogic.gdx.files.FileHandle]
  var width: scala.Int = 0
  var height: scala.Int = 0
  var format: com.badlogic.gdx.graphics.Pixmap.Format = null.asInstanceOf[com.badlogic.gdx.graphics.Pixmap.Format]
  var pixmap: com.badlogic.gdx.graphics.Pixmap = null.asInstanceOf[com.badlogic.gdx.graphics.Pixmap]
  var useMipMaps$field: scala.Boolean = false
  var isPrepared$field: scala.Boolean = false
  this.file = file$p
  this.pixmap = preloadedPixmap
  this.format = format$p
  this.useMipMaps$field = useMipMaps$p
  if (this.pixmap != null) {
    this.width = this.pixmap.getWidth()
    this.height = this.pixmap.getHeight()
    if (format$p == null) {
      this.format = this.pixmap.getFormat()
    } else ()
  } else ()
  @java.lang.Override
  override def isPrepared(): scala.Boolean = {
    return this.isPrepared$field
  }
  @java.lang.Override
  override def prepare(): scala.Unit = {
    if (this.isPrepared$field) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Already prepared")
    } else ()
    if (this.pixmap == null) {
      if (this.file.`extension`().equals("cim")) {
        this.pixmap = com.badlogic.gdx.graphics.PixmapIO.readCIM(this.file)
      } else {
        this.pixmap = new com.badlogic.gdx.graphics.Pixmap(this.file)
      }
      this.width = this.pixmap.getWidth()
      this.height = this.pixmap.getHeight()
      if (this.format == null) {
        this.format = this.pixmap.getFormat()
      } else ()
    } else ()
    this.isPrepared$field = true
  }
  @java.lang.Override
  override def consumePixmap(): com.badlogic.gdx.graphics.Pixmap = {
    if (!this.isPrepared$field) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Call prepare() before calling getPixmap()")
    } else ()
    this.isPrepared$field = false
    var pixmap: com.badlogic.gdx.graphics.Pixmap = this.pixmap
    this.pixmap = null
    return pixmap
  }
  @java.lang.Override
  override def disposePixmap(): scala.Boolean = {
    return true
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
    return this.format
  }
  @java.lang.Override
  override def useMipMaps(): scala.Boolean = {
    return this.useMipMaps$field
  }
  @java.lang.Override
  override def isManaged(): scala.Boolean = {
    return true
  }
  def getFileHandle(): com.badlogic.gdx.files.FileHandle = {
    return this.file
  }
  @java.lang.Override
  override def getType(): com.badlogic.gdx.graphics.TextureData.TextureDataType = {
    return com.badlogic.gdx.graphics.TextureData.TextureDataType.Pixmap
  }
  @java.lang.Override
  override def consumeCustomData(target: scala.Int): scala.Unit = {
    throw new com.badlogic.gdx.utils.GdxRuntimeException("This TextureData implementation does not upload data itself")
  }
  override def toString(): java.lang.String = {
    return this.file.toString()
  }
}
object FileTextureData {
  export com.badlogic.gdx.graphics.TextureData.*
}