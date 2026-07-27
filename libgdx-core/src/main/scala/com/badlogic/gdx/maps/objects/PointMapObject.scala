package com.badlogic.gdx.maps.objects

class PointMapObject(x: scala.Float, y: scala.Float) extends com.badlogic.gdx.maps.MapObject {
  private var point: com.badlogic.gdx.math.Vector2 = null.asInstanceOf[com.badlogic.gdx.math.Vector2]
  def this() = {
    this(0, 0)
  }
  this.point = new com.badlogic.gdx.math.Vector2(x, y)
  def getPoint(): com.badlogic.gdx.math.Vector2 = {
    return this.point
  }
}