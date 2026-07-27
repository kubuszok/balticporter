package com.badlogic.gdx.graphics.g2d

class PolygonRegion(region$p: com.badlogic.gdx.graphics.g2d.TextureRegion, vertices$p: scala.Array[scala.Float], triangles$p: scala.Array[scala.Short]) {
  var textureCoords: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  var vertices: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  var triangles: scala.Array[scala.Short] = null.asInstanceOf[scala.Array[scala.Short]]
  var region: com.badlogic.gdx.graphics.g2d.TextureRegion = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureRegion]
  var textureCoords$p: scala.Array[scala.Float] = {
    this.textureCoords = new scala.Array[scala.Float](vertices$p.length)
    this.textureCoords
  }
  val u: scala.Float = region$p.u
  val v: scala.Float = region$p.v
  val uvWidth: scala.Float = region$p.u2 - u
  val uvHeight: scala.Float = region$p.v2 - v
  val width: scala.Int = region$p.regionWidth
  val height: scala.Int = region$p.regionHeight
  this.region = region$p
  this.vertices = vertices$p
  this.triangles = triangles$p;
  { var i: scala.Int = 0; val n: scala.Int = vertices$p.length; while (i < n) { {
    textureCoords$p(i) = u + (uvWidth * (vertices$p(i) / width))
    textureCoords$p(i + 1) = v + (uvHeight * (1 - (vertices$p(i + 1) / height)))
  }; i = i + 2 } }
  def getVertices(): scala.Array[scala.Float] = {
    return this.vertices
  }
  def getTriangles(): scala.Array[scala.Short] = {
    return this.triangles
  }
  def getTextureCoords(): scala.Array[scala.Float] = {
    return this.textureCoords
  }
  def getRegion(): com.badlogic.gdx.graphics.g2d.TextureRegion = {
    return this.region
  }
}