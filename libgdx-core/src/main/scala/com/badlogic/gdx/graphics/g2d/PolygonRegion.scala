package com.badlogic.gdx.graphics.g2d

class PolygonRegion {
  var textureCoords: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  var vertices: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  var triangles: scala.Array[scala.Short] = null.asInstanceOf[scala.Array[scala.Short]]
  var region: com.badlogic.gdx.graphics.g2d.TextureRegion = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureRegion]
  def this(region: com.badlogic.gdx.graphics.g2d.TextureRegion, vertices: scala.Array[scala.Float], triangles: scala.Array[scala.Short]) = {
    this()
    this.region = region
    this.vertices = vertices
    this.triangles = triangles
    var textureCoords: scala.Array[scala.Float] = {
      this.textureCoords = new scala.Array[scala.Float](vertices.length)
      this.textureCoords
    }
    val u: scala.Float = region.u
    val v: scala.Float = region.v
    val uvWidth: scala.Float = region.u2 - u
    val uvHeight: scala.Float = region.v2 - v
    val width: scala.Int = region.regionWidth
    val height: scala.Int = region.regionHeight;
    { var i: scala.Int = 0; val n: scala.Int = vertices.length; while (i < n) { {
      textureCoords(i) = u + (uvWidth * (vertices(i) / width))
      textureCoords(i + 1) = v + (uvHeight * (1 - (vertices(i + 1) / height)))
    }; i = i + 2 } }
  }
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