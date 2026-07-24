package com.badlogic.gdx.graphics

trait TextureArrayData {
  def isPrepared(): scala.Boolean
  def prepare(): scala.Unit
  def consumeTextureArrayData(): scala.Unit
  def getWidth(): scala.Int
  def getHeight(): scala.Int
  def getDepth(): scala.Int
  def isManaged(): scala.Boolean
  def getInternalFormat(): scala.Int
  def getGLType(): scala.Int
}
object TextureArrayData {
  object Factory {
    def loadFromFiles(format: com.badlogic.gdx.graphics.Pixmap.Format, useMipMaps: scala.Boolean, files: scala.Array[com.badlogic.gdx.files.FileHandle]): TextureArrayData = {
      return new com.badlogic.gdx.graphics.glutils.FileTextureArrayData(format, useMipMaps, files)
    }
  }
}