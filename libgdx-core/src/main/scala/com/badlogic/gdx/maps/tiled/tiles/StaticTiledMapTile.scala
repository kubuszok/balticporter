package com.badlogic.gdx.maps.tiled.tiles

class StaticTiledMapTile extends com.badlogic.gdx.maps.tiled.TiledMapTile {
  private var id: scala.Int = 0
  private var blendMode: com.badlogic.gdx.maps.tiled.TiledMapTile.BlendMode = com.badlogic.gdx.maps.tiled.TiledMapTile.BlendMode.ALPHA
  private var properties: com.badlogic.gdx.maps.MapProperties = null.asInstanceOf[com.badlogic.gdx.maps.MapProperties]
  private var objects: com.badlogic.gdx.maps.MapObjects = null.asInstanceOf[com.badlogic.gdx.maps.MapObjects]
  private var textureRegion: com.badlogic.gdx.graphics.g2d.TextureRegion = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureRegion]
  private var offsetX: scala.Float = 0.0f
  private var offsetY: scala.Float = 0.0f
  def this(textureRegion: com.badlogic.gdx.graphics.g2d.TextureRegion) = {
    this()
    this.textureRegion = textureRegion
  }
  def this(copy: StaticTiledMapTile) = {
    this()
    if (copy.properties != null) {
      this.getProperties().putAll(copy.properties)
    } else ()
    this.objects = copy.objects
    this.textureRegion = copy.textureRegion
    this.id = copy.id
  }
  @java.lang.Override
  def getId(): scala.Int = {
    return this.id
  }
  @java.lang.Override
  def setId(id: scala.Int): scala.Unit = {
    this.id = id
  }
  @java.lang.Override
  def getBlendMode(): com.badlogic.gdx.maps.tiled.TiledMapTile.BlendMode = {
    return this.blendMode
  }
  @java.lang.Override
  def setBlendMode(blendMode: com.badlogic.gdx.maps.tiled.TiledMapTile.BlendMode): scala.Unit = {
    this.blendMode = blendMode
  }
  @java.lang.Override
  def getProperties(): com.badlogic.gdx.maps.MapProperties = {
    if (this.properties == null) {
      this.properties = new com.badlogic.gdx.maps.MapProperties()
    } else ()
    return this.properties
  }
  @java.lang.Override
  def getObjects(): com.badlogic.gdx.maps.MapObjects = {
    if (this.objects == null) {
      this.objects = new com.badlogic.gdx.maps.MapObjects()
    } else ()
    return this.objects
  }
  @java.lang.Override
  def getTextureRegion(): com.badlogic.gdx.graphics.g2d.TextureRegion = {
    return this.textureRegion
  }
  @java.lang.Override
  def setTextureRegion(textureRegion: com.badlogic.gdx.graphics.g2d.TextureRegion): scala.Unit = {
    this.textureRegion = textureRegion
  }
  @java.lang.Override
  def getOffsetX(): scala.Float = {
    return this.offsetX
  }
  @java.lang.Override
  def setOffsetX(offsetX: scala.Float): scala.Unit = {
    this.offsetX = offsetX
  }
  @java.lang.Override
  def getOffsetY(): scala.Float = {
    return this.offsetY
  }
  @java.lang.Override
  def setOffsetY(offsetY: scala.Float): scala.Unit = {
    this.offsetY = offsetY
  }
}
object StaticTiledMapTile {
  export com.badlogic.gdx.maps.tiled.TiledMapTile.*
}