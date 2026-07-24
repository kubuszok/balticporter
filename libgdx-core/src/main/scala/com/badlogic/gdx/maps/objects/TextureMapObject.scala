package com.badlogic.gdx.maps.objects

class TextureMapObject extends com.badlogic.gdx.maps.MapObject {
  private var x: scala.Float = 0.0f
  private var y: scala.Float = 0.0f
  private var originX: scala.Float = 0.0f
  private var originY: scala.Float = 0.0f
  private var scaleX: scala.Float = 1.0f
  private var scaleY: scala.Float = 1.0f
  private var rotation: scala.Float = 0.0f
  private var textureRegion: com.badlogic.gdx.graphics.g2d.TextureRegion = null
  def this(textureRegion: com.badlogic.gdx.graphics.g2d.TextureRegion) = {
    this()
    this.textureRegion = textureRegion
  }
  def getX(): scala.Float = {
    return this.x
  }
  def setX(x: scala.Float): scala.Unit = {
    this.x = x
  }
  def getY(): scala.Float = {
    return this.y
  }
  def setY(y: scala.Float): scala.Unit = {
    this.y = y
  }
  def getOriginX(): scala.Float = {
    return this.originX
  }
  def setOriginX(x: scala.Float): scala.Unit = {
    this.originX = x
  }
  def getOriginY(): scala.Float = {
    return this.originY
  }
  def setOriginY(y: scala.Float): scala.Unit = {
    this.originY = y
  }
  def getScaleX(): scala.Float = {
    return this.scaleX
  }
  def setScaleX(x: scala.Float): scala.Unit = {
    this.scaleX = x
  }
  def getScaleY(): scala.Float = {
    return this.scaleY
  }
  def setScaleY(y: scala.Float): scala.Unit = {
    this.scaleY = y
  }
  def getRotation(): scala.Float = {
    return this.rotation
  }
  def setRotation(rotation: scala.Float): scala.Unit = {
    this.rotation = rotation
  }
  def getTextureRegion(): com.badlogic.gdx.graphics.g2d.TextureRegion = {
    return this.textureRegion
  }
  def setTextureRegion(region: com.badlogic.gdx.graphics.g2d.TextureRegion): scala.Unit = {
    this.textureRegion = region
  }
}