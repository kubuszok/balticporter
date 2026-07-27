package com.badlogic.gdx.maps.objects

class EllipseMapObject(x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float) extends com.badlogic.gdx.maps.MapObject {
  private var ellipse: com.badlogic.gdx.math.Ellipse = null.asInstanceOf[com.badlogic.gdx.math.Ellipse]
  def this() = {
    this(0.0f, 0.0f, 1.0f, 1.0f)
  }
  this.ellipse = new com.badlogic.gdx.math.Ellipse(x, y, width, height)
  def getEllipse(): com.badlogic.gdx.math.Ellipse = {
    return this.ellipse
  }
}