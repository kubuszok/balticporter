package com.badlogic.gdx.maps.objects

class PolygonMapObject extends com.badlogic.gdx.maps.MapObject {
  private var polygon: com.badlogic.gdx.math.Polygon = null.asInstanceOf[com.badlogic.gdx.math.Polygon]
  def this(vertices: scala.Array[scala.Float]) = {
    this()
    this.polygon = new com.badlogic.gdx.math.Polygon(vertices)
  }
  def this(polygon: com.badlogic.gdx.math.Polygon) = {
    this()
    this.polygon = polygon
  }
  def getPolygon(): com.badlogic.gdx.math.Polygon = {
    return this.polygon
  }
  def setPolygon(polygon: com.badlogic.gdx.math.Polygon): scala.Unit = {
    this.polygon = polygon
  }
}