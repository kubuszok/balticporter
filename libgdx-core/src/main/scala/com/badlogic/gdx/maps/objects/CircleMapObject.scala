package com.badlogic.gdx.maps.objects

class CircleMapObject(x: scala.Float, y: scala.Float, radius: scala.Float) extends com.badlogic.gdx.maps.MapObject {
  private var circle: com.badlogic.gdx.math.Circle = null.asInstanceOf[com.badlogic.gdx.math.Circle]
  def this() = {
    this(0.0f, 0.0f, 1.0f)
  }
  this.circle = new com.badlogic.gdx.math.Circle(x, y, radius)
  def getCircle(): com.badlogic.gdx.math.Circle = {
    return this.circle
  }
}