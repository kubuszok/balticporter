package com.badlogic.gdx.graphics.glutils

class MipMapTextureData extends com.badlogic.gdx.graphics.TextureData {
  var mips: scala.Array[com.badlogic.gdx.graphics.TextureData] = null.asInstanceOf[scala.Array[com.badlogic.gdx.graphics.TextureData]]
  def this(mipMapData: scala.Array[com.badlogic.gdx.graphics.TextureData]) = {
    this()
    this.mips = new Array[com.badlogic.gdx.graphics.TextureData](mipMapData.length)
    java.lang.System.arraycopy(mipMapData, 0, this.mips, 0, mipMapData.length)
  }
  def getType(): com.badlogic.gdx.graphics.TextureData#TextureDataType = {
    return com.badlogic.gdx.graphics.TextureData.TextureDataType.Custom
  }
  def isPrepared(): scala.Boolean = {
    return true
  }
  def prepare(): scala.Unit = {
    ()
  }
  def consumePixmap(): com.badlogic.gdx.graphics.Pixmap = {
    throw new com.badlogic.gdx.utils.GdxRuntimeException("It's compressed, use the compressed method")
  }
  def disposePixmap(): scala.Boolean = {
    return false
  }
  def consumeCustomData(target: scala.Int): scala.Unit = {
    { var i: scala.Int = 0; while (i < this.mips.length) { {
      com.badlogic.gdx.graphics.GLTexture.uploadImageData(target, this.mips(i), i)
    }; i = i + 1 } }
  }
  def getWidth(): scala.Int = {
    return this.mips(0).getWidth()
  }
  def getHeight(): scala.Int = {
    return this.mips(0).getHeight()
  }
  def getFormat(): com.badlogic.gdx.graphics.Pixmap#Format = {
    return this.mips(0).getFormat()
  }
  def useMipMaps(): scala.Boolean = {
    return false
  }
  def isManaged(): scala.Boolean = {
    return true
  }
}