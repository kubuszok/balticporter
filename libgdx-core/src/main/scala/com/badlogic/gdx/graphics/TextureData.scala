package com.badlogic.gdx.graphics

trait TextureData {
  def getType(): com.badlogic.gdx.graphics.TextureData.TextureDataType
  def isPrepared(): scala.Boolean
  def prepare(): scala.Unit
  def consumePixmap(): com.badlogic.gdx.graphics.Pixmap
  def disposePixmap(): scala.Boolean
  def consumeCustomData(target: scala.Int): scala.Unit
  def getWidth(): scala.Int
  def getHeight(): scala.Int
  def getFormat(): com.badlogic.gdx.graphics.Pixmap.Format
  def useMipMaps(): scala.Boolean
  def isManaged(): scala.Boolean
}
object TextureData {
  sealed abstract class TextureDataType
  object TextureDataType {
    case object Pixmap extends TextureDataType
    case object Custom extends TextureDataType
    def values(): Array[TextureDataType] = Array(Pixmap, Custom)
  }
  object Factory {
    def loadFromFile(file: com.badlogic.gdx.files.FileHandle, useMipMaps: scala.Boolean): TextureData = {
      return com.badlogic.gdx.graphics.TextureData.Factory.loadFromFile(file, null, useMipMaps)
    }
    def loadFromFile(file: com.badlogic.gdx.files.FileHandle, format: com.badlogic.gdx.graphics.Pixmap.Format, useMipMaps: scala.Boolean): TextureData = {
      if (file == null) {
        return null
      } else ()
      if (file.name().endsWith(".cim")) {
        return new com.badlogic.gdx.graphics.glutils.FileTextureData(file, com.badlogic.gdx.graphics.PixmapIO.readCIM(file), format, useMipMaps)
      } else ()
      if (file.name().endsWith(".etc1")) {
        return new com.badlogic.gdx.graphics.glutils.ETC1TextureData(file, useMipMaps)
      } else ()
      if (file.name().endsWith(".ktx") || file.name().endsWith(".zktx")) {
        return new com.badlogic.gdx.graphics.glutils.KTXTextureData(file, useMipMaps)
      } else ()
      return new com.badlogic.gdx.graphics.glutils.FileTextureData(file, new com.badlogic.gdx.graphics.Pixmap(file), format, useMipMaps)
    }
  }
}