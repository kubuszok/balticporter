package com.badlogic.gdx.graphics.glutils

class FacedCubemapData(positiveX: com.badlogic.gdx.graphics.TextureData, negativeX: com.badlogic.gdx.graphics.TextureData, positiveY: com.badlogic.gdx.graphics.TextureData, negativeY: com.badlogic.gdx.graphics.TextureData, positiveZ: com.badlogic.gdx.graphics.TextureData, negativeZ: com.badlogic.gdx.graphics.TextureData) extends com.badlogic.gdx.graphics.CubemapData {
  final val data: scala.Array[com.badlogic.gdx.graphics.TextureData] = new scala.Array[com.badlogic.gdx.graphics.TextureData](6)
  def this() = {
    this(null.asInstanceOf[com.badlogic.gdx.graphics.TextureData], null.asInstanceOf[com.badlogic.gdx.graphics.TextureData], null.asInstanceOf[com.badlogic.gdx.graphics.TextureData], null.asInstanceOf[com.badlogic.gdx.graphics.TextureData], null.asInstanceOf[com.badlogic.gdx.graphics.TextureData], null.asInstanceOf[com.badlogic.gdx.graphics.TextureData])
  }
  def this(positiveX: com.badlogic.gdx.files.FileHandle, negativeX: com.badlogic.gdx.files.FileHandle, positiveY: com.badlogic.gdx.files.FileHandle, negativeY: com.badlogic.gdx.files.FileHandle, positiveZ: com.badlogic.gdx.files.FileHandle, negativeZ: com.badlogic.gdx.files.FileHandle) = {
    this(com.badlogic.gdx.graphics.TextureData.Factory.loadFromFile(positiveX, false), com.badlogic.gdx.graphics.TextureData.Factory.loadFromFile(negativeX, false), com.badlogic.gdx.graphics.TextureData.Factory.loadFromFile(positiveY, false), com.badlogic.gdx.graphics.TextureData.Factory.loadFromFile(negativeY, false), com.badlogic.gdx.graphics.TextureData.Factory.loadFromFile(positiveZ, false), com.badlogic.gdx.graphics.TextureData.Factory.loadFromFile(negativeZ, false))
  }
  def this(positiveX: com.badlogic.gdx.files.FileHandle, negativeX: com.badlogic.gdx.files.FileHandle, positiveY: com.badlogic.gdx.files.FileHandle, negativeY: com.badlogic.gdx.files.FileHandle, positiveZ: com.badlogic.gdx.files.FileHandle, negativeZ: com.badlogic.gdx.files.FileHandle, useMipMaps: scala.Boolean) = {
    this(com.badlogic.gdx.graphics.TextureData.Factory.loadFromFile(positiveX, useMipMaps), com.badlogic.gdx.graphics.TextureData.Factory.loadFromFile(negativeX, useMipMaps), com.badlogic.gdx.graphics.TextureData.Factory.loadFromFile(positiveY, useMipMaps), com.badlogic.gdx.graphics.TextureData.Factory.loadFromFile(negativeY, useMipMaps), com.badlogic.gdx.graphics.TextureData.Factory.loadFromFile(positiveZ, useMipMaps), com.badlogic.gdx.graphics.TextureData.Factory.loadFromFile(negativeZ, useMipMaps))
  }
  def this(positiveX: com.badlogic.gdx.graphics.Pixmap, negativeX: com.badlogic.gdx.graphics.Pixmap, positiveY: com.badlogic.gdx.graphics.Pixmap, negativeY: com.badlogic.gdx.graphics.Pixmap, positiveZ: com.badlogic.gdx.graphics.Pixmap, negativeZ: com.badlogic.gdx.graphics.Pixmap, useMipMaps: scala.Boolean) = {
    this(if (positiveX == null) null.asInstanceOf[com.badlogic.gdx.graphics.TextureData] else new com.badlogic.gdx.graphics.glutils.PixmapTextureData(positiveX, null, useMipMaps, false), if (negativeX == null) null.asInstanceOf[com.badlogic.gdx.graphics.TextureData] else new com.badlogic.gdx.graphics.glutils.PixmapTextureData(negativeX, null, useMipMaps, false), if (positiveY == null) null.asInstanceOf[com.badlogic.gdx.graphics.TextureData] else new com.badlogic.gdx.graphics.glutils.PixmapTextureData(positiveY, null, useMipMaps, false), if (negativeY == null) null.asInstanceOf[com.badlogic.gdx.graphics.TextureData] else new com.badlogic.gdx.graphics.glutils.PixmapTextureData(negativeY, null, useMipMaps, false), if (positiveZ == null) null.asInstanceOf[com.badlogic.gdx.graphics.TextureData] else new com.badlogic.gdx.graphics.glutils.PixmapTextureData(positiveZ, null, useMipMaps, false), if (negativeZ == null) null.asInstanceOf[com.badlogic.gdx.graphics.TextureData] else new com.badlogic.gdx.graphics.glutils.PixmapTextureData(negativeZ, null, useMipMaps, false))
  }
  def this(positiveX: com.badlogic.gdx.graphics.Pixmap, negativeX: com.badlogic.gdx.graphics.Pixmap, positiveY: com.badlogic.gdx.graphics.Pixmap, negativeY: com.badlogic.gdx.graphics.Pixmap, positiveZ: com.badlogic.gdx.graphics.Pixmap, negativeZ: com.badlogic.gdx.graphics.Pixmap) = {
    this(positiveX, negativeX, positiveY, negativeY, positiveZ, negativeZ, false)
  }
  def this(width: scala.Int, height: scala.Int, depth: scala.Int, format: com.badlogic.gdx.graphics.Pixmap.Format) = {
    this(new com.badlogic.gdx.graphics.glutils.PixmapTextureData(new com.badlogic.gdx.graphics.Pixmap(depth, height, format), null, false, true), new com.badlogic.gdx.graphics.glutils.PixmapTextureData(new com.badlogic.gdx.graphics.Pixmap(depth, height, format), null, false, true), new com.badlogic.gdx.graphics.glutils.PixmapTextureData(new com.badlogic.gdx.graphics.Pixmap(width, depth, format), null, false, true), new com.badlogic.gdx.graphics.glutils.PixmapTextureData(new com.badlogic.gdx.graphics.Pixmap(width, depth, format), null, false, true), new com.badlogic.gdx.graphics.glutils.PixmapTextureData(new com.badlogic.gdx.graphics.Pixmap(width, height, format), null, false, true), new com.badlogic.gdx.graphics.glutils.PixmapTextureData(new com.badlogic.gdx.graphics.Pixmap(width, height, format), null, false, true))
  }
  this.data(0) = positiveX
  this.data(1) = negativeX
  this.data(2) = positiveY
  this.data(3) = negativeY
  this.data(4) = positiveZ
  this.data(5) = negativeZ
  def isManaged(): scala.Boolean = {
    for (data <- this.data) {
      if (!data.isManaged()) {
        return false
      } else ()
    }
    return true
  }
  def load(side: com.badlogic.gdx.graphics.Cubemap.CubemapSide, file: com.badlogic.gdx.files.FileHandle): scala.Unit = {
    this.data(side.index) = com.badlogic.gdx.graphics.TextureData.Factory.loadFromFile(file, false)
  }
  def load(side: com.badlogic.gdx.graphics.Cubemap.CubemapSide, pixmap: com.badlogic.gdx.graphics.Pixmap): scala.Unit = {
    this.data(side.index) = if (pixmap == null) null.asInstanceOf[com.badlogic.gdx.graphics.TextureData] else new com.badlogic.gdx.graphics.glutils.PixmapTextureData(pixmap, null, false, false)
  }
  def isComplete(): scala.Boolean = {
    { var i: scala.Int = 0; while (i < this.data.length) { {
      if (this.data(i) == null) {
        return false
      } else ()
    }; i = i + 1 } }
    return true
  }
  def getTextureData(side: com.badlogic.gdx.graphics.Cubemap.CubemapSide): com.badlogic.gdx.graphics.TextureData = {
    return this.data(side.index)
  }
  def getWidth(): scala.Int = {
    var tmp: scala.Int = 0
    var width: scala.Int = 0
    if ((this.data(com.badlogic.gdx.graphics.Cubemap.CubemapSide.PositiveZ.index) != null) && ({
      tmp = this.data(com.badlogic.gdx.graphics.Cubemap.CubemapSide.PositiveZ.index).getWidth()
      tmp
    } > width)) {
      width = tmp
    } else ()
    if ((this.data(com.badlogic.gdx.graphics.Cubemap.CubemapSide.NegativeZ.index) != null) && ({
      tmp = this.data(com.badlogic.gdx.graphics.Cubemap.CubemapSide.NegativeZ.index).getWidth()
      tmp
    } > width)) {
      width = tmp
    } else ()
    if ((this.data(com.badlogic.gdx.graphics.Cubemap.CubemapSide.PositiveY.index) != null) && ({
      tmp = this.data(com.badlogic.gdx.graphics.Cubemap.CubemapSide.PositiveY.index).getWidth()
      tmp
    } > width)) {
      width = tmp
    } else ()
    if ((this.data(com.badlogic.gdx.graphics.Cubemap.CubemapSide.NegativeY.index) != null) && ({
      tmp = this.data(com.badlogic.gdx.graphics.Cubemap.CubemapSide.NegativeY.index).getWidth()
      tmp
    } > width)) {
      width = tmp
    } else ()
    return width
  }
  def getHeight(): scala.Int = {
    var tmp: scala.Int = 0
    var height: scala.Int = 0
    if ((this.data(com.badlogic.gdx.graphics.Cubemap.CubemapSide.PositiveZ.index) != null) && ({
      tmp = this.data(com.badlogic.gdx.graphics.Cubemap.CubemapSide.PositiveZ.index).getHeight()
      tmp
    } > height)) {
      height = tmp
    } else ()
    if ((this.data(com.badlogic.gdx.graphics.Cubemap.CubemapSide.NegativeZ.index) != null) && ({
      tmp = this.data(com.badlogic.gdx.graphics.Cubemap.CubemapSide.NegativeZ.index).getHeight()
      tmp
    } > height)) {
      height = tmp
    } else ()
    if ((this.data(com.badlogic.gdx.graphics.Cubemap.CubemapSide.PositiveX.index) != null) && ({
      tmp = this.data(com.badlogic.gdx.graphics.Cubemap.CubemapSide.PositiveX.index).getHeight()
      tmp
    } > height)) {
      height = tmp
    } else ()
    if ((this.data(com.badlogic.gdx.graphics.Cubemap.CubemapSide.NegativeX.index) != null) && ({
      tmp = this.data(com.badlogic.gdx.graphics.Cubemap.CubemapSide.NegativeX.index).getHeight()
      tmp
    } > height)) {
      height = tmp
    } else ()
    return height
  }
  def isPrepared(): scala.Boolean = {
    return false
  }
  def prepare(): scala.Unit = {
    if (!this.isComplete()) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("You need to complete your cubemap data before using it")
    } else ();
    { var i: scala.Int = 0; while (i < this.data.length) { {
      if (!this.data(i).isPrepared()) {
        this.data(i).prepare()
      } else ()
    }; i = i + 1 } }
  }
  def consumeCubemapData(): scala.Unit = {
    { var i: scala.Int = 0; while (i < this.data.length) { {
      if (this.data(i).getType() == com.badlogic.gdx.graphics.TextureData.TextureDataType.Custom) {
        this.data(i).consumeCustomData(com.badlogic.gdx.graphics.GL20.GL_TEXTURE_CUBE_MAP_POSITIVE_X + i)
      } else {
        var pixmap: com.badlogic.gdx.graphics.Pixmap = this.data(i).consumePixmap()
        var disposePixmap: scala.Boolean = this.data(i).disposePixmap()
        if (this.data(i).getFormat() != pixmap.getFormat()) {
          val tmp: com.badlogic.gdx.graphics.Pixmap = new com.badlogic.gdx.graphics.Pixmap(pixmap.getWidth(), pixmap.getHeight(), this.data(i).getFormat())
          tmp.setBlending(com.badlogic.gdx.graphics.Pixmap.Blending.None)
          tmp.drawPixmap(pixmap, 0, 0, 0, 0, pixmap.getWidth(), pixmap.getHeight())
          if (this.data(i).disposePixmap()) {
            pixmap.dispose()
          } else ()
          pixmap = tmp
          disposePixmap = true
        } else ()
        com.badlogic.gdx.Gdx.gl.glPixelStorei(com.badlogic.gdx.graphics.GL20.GL_UNPACK_ALIGNMENT, 1)
        com.badlogic.gdx.Gdx.gl.glTexImage2D(com.badlogic.gdx.graphics.GL20.GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, pixmap.getGLInternalFormat(), pixmap.getWidth(), pixmap.getHeight(), 0, pixmap.getGLFormat(), pixmap.getGLType(), pixmap.getPixels())
        if (disposePixmap) {
          pixmap.dispose()
        } else ()
      }
    }; i = i + 1 } }
  }
}