package com.badlogic.gdx.graphics.glutils

class FileTextureArrayData extends com.badlogic.gdx.graphics.TextureArrayData {
  private var textureDatas: scala.Array[com.badlogic.gdx.graphics.TextureData] = null.asInstanceOf[scala.Array[com.badlogic.gdx.graphics.TextureData]]
  private var prepared: scala.Boolean = false
  private var format: com.badlogic.gdx.graphics.Pixmap.Format = null.asInstanceOf[com.badlogic.gdx.graphics.Pixmap.Format]
  private var depth: scala.Int = 0
  var useMipMaps: scala.Boolean = false
  def this(format: com.badlogic.gdx.graphics.Pixmap.Format, useMipMaps: scala.Boolean, files: scala.Array[com.badlogic.gdx.files.FileHandle]) = {
    this()
    this.format = format
    this.useMipMaps = useMipMaps
    this.depth = files.length
    this.textureDatas = new scala.Array[com.badlogic.gdx.graphics.TextureData](files.length);
    { var i: scala.Int = 0; while (i < files.length) { {
      this.textureDatas(i) = com.badlogic.gdx.graphics.TextureData.Factory.loadFromFile(files(i), format, useMipMaps)
    }; i = i + 1 } }
  }
  def this(format: com.badlogic.gdx.graphics.Pixmap.Format, useMipMaps: scala.Boolean, textureDatas: scala.Array[com.badlogic.gdx.graphics.TextureData]) = {
    this()
    this.format = format
    this.useMipMaps = useMipMaps
    this.depth = textureDatas.length
    this.textureDatas = textureDatas
  }
  @java.lang.Override
  def isPrepared(): scala.Boolean = {
    return this.prepared
  }
  @java.lang.Override
  def prepare(): scala.Unit = {
    var width: scala.Int = -1
    var height: scala.Int = -1
    for (data <- this.textureDatas) {
      if (!data.isPrepared()) {
        data.prepare()
      } else ()
      if (width == (-1)) {
        width = data.getWidth()
        height = data.getHeight()
        /* continue */ ()
      } else ()
      if ((width != data.getWidth()) || (height != data.getHeight())) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Error whilst preparing TextureArray: TextureArray Textures must have equal dimensions.")
      } else ()
    }
    this.prepared = true
  }
  @java.lang.Override
  def consumeTextureArrayData(): scala.Unit = {
    var containsCustomData: scala.Boolean = false;
    { var i: scala.Int = 0; while (i < this.textureDatas.length) { {
      if (this.textureDatas(i).getType() == com.badlogic.gdx.graphics.TextureData.TextureDataType.Custom) {
        this.textureDatas(i).consumeCustomData(com.badlogic.gdx.graphics.GL30.GL_TEXTURE_2D_ARRAY)
        containsCustomData = true
      } else {
        val texData: com.badlogic.gdx.graphics.TextureData = this.textureDatas(i)
        var pixmap: com.badlogic.gdx.graphics.Pixmap = texData.consumePixmap()
        var disposePixmap: scala.Boolean = texData.disposePixmap()
        if (texData.getFormat() != pixmap.getFormat()) {
          val temp: com.badlogic.gdx.graphics.Pixmap = new com.badlogic.gdx.graphics.Pixmap(pixmap.getWidth(), pixmap.getHeight(), texData.getFormat())
          temp.setBlending(com.badlogic.gdx.graphics.Pixmap.Blending.None)
          temp.drawPixmap(pixmap, 0, 0, 0, 0, pixmap.getWidth(), pixmap.getHeight())
          if (texData.disposePixmap()) {
            pixmap.dispose()
          } else ()
          pixmap = temp
          disposePixmap = true
        } else ()
        com.badlogic.gdx.Gdx.gl30.glTexSubImage3D(com.badlogic.gdx.graphics.GL30.GL_TEXTURE_2D_ARRAY, 0, 0, 0, i, pixmap.getWidth(), pixmap.getHeight(), 1, pixmap.getGLInternalFormat(), pixmap.getGLType(), pixmap.getPixels())
        if (disposePixmap) {
          pixmap.dispose()
        } else ()
      }
    }; i = i + 1 } }
    if (this.useMipMaps && (!containsCustomData)) {
      com.badlogic.gdx.Gdx.gl20.glGenerateMipmap(com.badlogic.gdx.graphics.GL30.GL_TEXTURE_2D_ARRAY)
    } else ()
  }
  @java.lang.Override
  def getWidth(): scala.Int = {
    return this.textureDatas(0).getWidth()
  }
  @java.lang.Override
  def getHeight(): scala.Int = {
    return this.textureDatas(0).getHeight()
  }
  @java.lang.Override
  def getDepth(): scala.Int = {
    return this.depth
  }
  @java.lang.Override
  def getInternalFormat(): scala.Int = {
    return com.badlogic.gdx.graphics.Pixmap.Format.toGlFormat(this.format)
  }
  @java.lang.Override
  def getGLType(): scala.Int = {
    return com.badlogic.gdx.graphics.Pixmap.Format.toGlType(this.format)
  }
  @java.lang.Override
  def isManaged(): scala.Boolean = {
    for (data <- this.textureDatas) {
      if (!data.isManaged()) {
        return false
      } else ()
    }
    return true
  }
}
object FileTextureArrayData {
  export com.badlogic.gdx.graphics.TextureArrayData.*
}