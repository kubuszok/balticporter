package com.badlogic.gdx.maps.objects

class CircleMapObject extends com.badlogic.gdx.maps.MapObject {
  private var circle: com.badlogic.gdx.math.Circle = null.asInstanceOf[com.badlogic.gdx.math.Circle]
  def this(x: scala.Float, y: scala.Float, radius: scala.Float) = {
    this()
    this.circle = new com.badlogic.gdx.math.Circle(x, y, radius)
  }
  def getCircle(): com.badlogic.gdx.math.Circle = {
    return this.circle
  }
}