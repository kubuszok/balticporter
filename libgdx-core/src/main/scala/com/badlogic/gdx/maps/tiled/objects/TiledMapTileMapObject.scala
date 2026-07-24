package com.badlogic.gdx.maps.tiled.objects

class TiledMapTileMapObject extends com.badlogic.gdx.maps.objects.TextureMapObject {
  private var flipHorizontally: scala.Boolean = false
  private var flipVertically: scala.Boolean = false
  private var tile: com.badlogic.gdx.maps.tiled.TiledMapTile = null.asInstanceOf[com.badlogic.gdx.maps.tiled.TiledMapTile]
  def this(tile: com.badlogic.gdx.maps.tiled.TiledMapTile, flipHorizontally: scala.Boolean, flipVertically: scala.Boolean) = {
    this()
    this.flipHorizontally = flipHorizontally
    this.flipVertically = flipVertically
    this.tile = tile
    val textureRegion: com.badlogic.gdx.graphics.g2d.TextureRegion = new com.badlogic.gdx.graphics.g2d.TextureRegion(tile.getTextureRegion())
    textureRegion.flip(flipHorizontally, flipVertically)
    this.setTextureRegion(textureRegion)
  }
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