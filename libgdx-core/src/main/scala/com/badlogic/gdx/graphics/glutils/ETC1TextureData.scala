package com.badlogic.gdx.graphics.glutils

class ETC1TextureData extends com.badlogic.gdx.graphics.TextureData {
  var file: com.badlogic.gdx.files.FileHandle = null.asInstanceOf[com.badlogic.gdx.files.FileHandle]
  var data: com.badlogic.gdx.graphics.glutils.ETC1.ETC1Data = null.asInstanceOf[com.badlogic.gdx.graphics.glutils.ETC1.ETC1Data]
  var useMipMaps$field: scala.Boolean = false
  var width: scala.Int = 0
  var height: scala.Int = 0
  var isPrepared$field: scala.Boolean = false
  def this(file: com.badlogic.gdx.files.FileHandle, useMipMaps: scala.Boolean) = {
    this()
    this.file = file
    this.useMipMaps$field = useMipMaps
  }
  def this(file: com.badlogic.gdx.files.FileHandle) = {
    this(file, false)
  }
  def this(encodedImage: com.badlogic.gdx.graphics.glutils.ETC1.ETC1Data, useMipMaps: scala.Boolean) = {
    this()
    this.data = encodedImage
    this.useMipMaps$field = useMipMaps
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
    if ((this.file == null) && (this.data == null)) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Can only load once from ETC1Data")
    } else ()
    if (this.file != null) {
      this.data = new com.badlogic.gdx.graphics.glutils.ETC1.ETC1Data(this.file)
    } else ()
    this.width = this.data.width
    this.height = this.data.height
    this.isPrepared$field = true
  }
  def consumeCustomData(target: scala.Int): scala.Unit = {
    if (!this.isPrepared$field) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Call prepare() before calling consumeCompressedData()")
    } else ()
    if (!com.badlogic.gdx.Gdx.graphics.supportsExtension("GL_OES_compressed_ETC1_RGB8_texture")) {
      val pixmap: com.badlogic.gdx.graphics.Pixmap = com.badlogic.gdx.graphics.glutils.ETC1.decodeImage(this.data, com.badlogic.gdx.graphics.Pixmap.Format.RGB565)
      com.badlogic.gdx.Gdx.gl.glTexImage2D(target, 0, pixmap.getGLInternalFormat(), pixmap.getWidth(), pixmap.getHeight(), 0, pixmap.getGLFormat(), pixmap.getGLType(), pixmap.getPixels())
      if (this.useMipMaps$field) {
        com.badlogic.gdx.graphics.glutils.MipMapGenerator.generateMipMap(target, pixmap, pixmap.getWidth(), pixmap.getHeight())
      } else ()
      pixmap.dispose()
      this.useMipMaps$field = false
    } else {
      com.badlogic.gdx.Gdx.gl.glCompressedTexImage2D(target, 0, com.badlogic.gdx.graphics.glutils.ETC1.ETC1_RGB8_OES, this.width, this.height, 0, this.data.compressedData.capacity() - this.data.dataOffset, this.data.compressedData)
      if (this.useMipMaps()) {
        com.badlogic.gdx.Gdx.gl20.glGenerateMipmap(com.badlogic.gdx.graphics.GL20.GL_TEXTURE_2D)
      } else ()
    }
    this.data.dispose()
    this.data = null
    this.isPrepared$field = false
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
    return com.badlogic.gdx.graphics.Pixmap.Format.RGB565
  }
  def useMipMaps(): scala.Boolean = {
    return this.useMipMaps$field
  }
  def isManaged(): scala.Boolean = {
    return true
  }
}
object ETC1TextureData {
  export com.badlogic.gdx.graphics.TextureData.*
}