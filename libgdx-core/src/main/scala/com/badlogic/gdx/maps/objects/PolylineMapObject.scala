package com.badlogic.gdx.maps.objects

class PolylineMapObject extends com.badlogic.gdx.maps.MapObject {
  private var polyline: com.badlogic.gdx.math.Polyline = null.asInstanceOf[com.badlogic.gdx.math.Polyline]
  def this(vertices: scala.Array[scala.Float]) = {
    this()
    this.polyline = new com.badlogic.gdx.math.Polyline(vertices)
  }
  def this(polyline: com.badlogic.gdx.math.Polyline) = {
    this()
    this.polyline = polyline
  }
  this.polyline = new com.badlogic.gdx.math.Polyline(new scala.Array[scala.Float](0))
  def getPolyline(): com.badlogic.gdx.math.Polyline = {
    return this.polyline
  }
  def setPolyline(polyline: com.badlogic.gdx.math.Polyline): scala.Unit = {
    this.polyline = polyline
  }
}