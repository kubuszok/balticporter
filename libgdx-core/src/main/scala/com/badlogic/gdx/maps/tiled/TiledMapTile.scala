package com.badlogic.gdx.maps.tiled

trait TiledMapTile {
  def getId(): scala.Int
  def setId(id: scala.Int): scala.Unit
  def getBlendMode(): com.badlogic.gdx.maps.tiled.TiledMapTile.BlendMode
  def setBlendMode(blendMode: com.badlogic.gdx.maps.tiled.TiledMapTile.BlendMode): scala.Unit
  def getTextureRegion(): com.badlogic.gdx.graphics.g2d.TextureRegion
  def setTextureRegion(textureRegion: com.badlogic.gdx.graphics.g2d.TextureRegion): scala.Unit
  def getOffsetX(): scala.Float
  def setOffsetX(offsetX: scala.Float): scala.Unit
  def getOffsetY(): scala.Float
  def setOffsetY(offsetY: scala.Float): scala.Unit
  def getProperties(): com.badlogic.gdx.maps.MapProperties
  def getObjects(): com.badlogic.gdx.maps.MapObjects
}
object TiledMapTile {
  sealed abstract class BlendMode
  object BlendMode {
    case object NONE extends BlendMode
    case object ALPHA extends BlendMode
    def values(): Array[BlendMode] = Array(NONE, ALPHA)
  }
}