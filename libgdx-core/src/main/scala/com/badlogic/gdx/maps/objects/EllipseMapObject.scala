package com.badlogic.gdx.maps.objects

class EllipseMapObject extends com.badlogic.gdx.maps.MapObject {
  private var ellipse: com.badlogic.gdx.math.Ellipse = null.asInstanceOf[com.badlogic.gdx.math.Ellipse]
  def this(x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float) = {
    this()
    this.ellipse = new com.badlogic.gdx.math.Ellipse(x, y, width, height)
  }
  def getEllipse(): com.badlogic.gdx.math.Ellipse = {
    return this.ellipse
  }
}