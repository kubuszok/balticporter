package com.badlogic.gdx.graphics.glutils

class MipMapTextureData(mipMapData: scala.Array[com.badlogic.gdx.graphics.TextureData]) extends com.badlogic.gdx.graphics.TextureData {
  var mips: scala.Array[com.badlogic.gdx.graphics.TextureData] = null.asInstanceOf[scala.Array[com.badlogic.gdx.graphics.TextureData]]
  this.mips = new scala.Array[com.badlogic.gdx.graphics.TextureData](mipMapData.length)
  java.lang.System.arraycopy(mipMapData, 0, this.mips, 0, mipMapData.length)
  @java.lang.Override
  override def getType(): com.badlogic.gdx.graphics.TextureData.TextureDataType = {
    return com.badlogic.gdx.graphics.TextureData.TextureDataType.Custom
  }
  @java.lang.Override
  override def isPrepared(): scala.Boolean = {
    return true
  }
  @java.lang.Override
  override def prepare(): scala.Unit = {
    ()
  }
  @java.lang.Override
  override def consumePixmap(): com.badlogic.gdx.graphics.Pixmap = {
    throw new com.badlogic.gdx.utils.GdxRuntimeException("It's compressed, use the compressed method")
  }
  @java.lang.Override
  override def disposePixmap(): scala.Boolean = {
    return false
  }
  @java.lang.Override
  override def consumeCustomData(target: scala.Int): scala.Unit = {
    { var i: scala.Int = 0; while (i < this.mips.length) { {
      com.badlogic.gdx.graphics.GLTexture.uploadImageData(target, this.mips(i), i)
    }; i = i + 1 } }
  }
  @java.lang.Override
  override def getWidth(): scala.Int = {
    return this.mips(0).getWidth()
  }
  @java.lang.Override
  override def getHeight(): scala.Int = {
    return this.mips(0).getHeight()
  }
  @java.lang.Override
  override def getFormat(): com.badlogic.gdx.graphics.Pixmap.Format = {
    return this.mips(0).getFormat()
  }
  @java.lang.Override
  override def useMipMaps(): scala.Boolean = {
    return false
  }
  @java.lang.Override
  override def isManaged(): scala.Boolean = {
    return true
  }
}
object MipMapTextureData {
  export com.badlogic.gdx.graphics.TextureData.*
}