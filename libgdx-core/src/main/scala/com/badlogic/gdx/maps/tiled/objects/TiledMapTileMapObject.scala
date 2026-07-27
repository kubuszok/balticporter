package com.badlogic.gdx.maps.tiled.objects

class TiledMapTileMapObject(tile$p: com.badlogic.gdx.maps.tiled.TiledMapTile, flipHorizontally$p: scala.Boolean, flipVertically$p: scala.Boolean) extends com.badlogic.gdx.maps.objects.TextureMapObject {
  private var flipHorizontally: scala.Boolean = false
  private var flipVertically: scala.Boolean = false
  private var tile: com.badlogic.gdx.maps.tiled.TiledMapTile = null.asInstanceOf[com.badlogic.gdx.maps.tiled.TiledMapTile]
  val textureRegion$p: com.badlogic.gdx.graphics.g2d.TextureRegion = new com.badlogic.gdx.graphics.g2d.TextureRegion(tile$p.getTextureRegion())
  this.flipHorizontally = flipHorizontally$p
  this.flipVertically = flipVertically$p
  this.tile = tile$p
  textureRegion$p.flip(flipHorizontally$p, flipVertically$p)
  this.setTextureRegion(textureRegion$p)
  def isFlipHorizontally(): scala.Boolean = {
    return this.flipHorizontally
  }
  def setFlipHorizontally(flipHorizontally: scala.Boolean): scala.Unit = {
    this.flipHorizontally = flipHorizontally
  }
  def isFlipVertically(): scala.Boolean = {
    return this.flipVertically
  }
  def setFlipVertically(flipVertically: scala.Boolean): scala.Unit = {
    this.flipVertically = flipVertically
  }
  def getTile(): com.badlogic.gdx.maps.tiled.TiledMapTile = {
    return this.tile
  }
  def setTile(tile: com.badlogic.gdx.maps.tiled.TiledMapTile): scala.Unit = {
    this.tile = tile
  }
}