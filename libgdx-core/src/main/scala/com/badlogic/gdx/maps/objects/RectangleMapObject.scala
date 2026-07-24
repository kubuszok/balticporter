package com.badlogic.gdx.maps.objects

class RectangleMapObject extends com.badlogic.gdx.maps.MapObject {
  private var rectangle: com.badlogic.gdx.math.Rectangle = null.asInstanceOf[com.badlogic.gdx.math.Rectangle]
  def this(x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float) = {
    this()
    this.rectangle = new com.badlogic.gdx.math.Rectangle(x, y, width, height)
  }
  def getRectangle(): com.badlogic.gdx.math.Rectangle = {
    return this.rectangle
  }
}