package com.badlogic.gdx.graphics.g2d

trait PolygonBatch extends com.badlogic.gdx.graphics.g2d.Batch {
  def draw(region: com.badlogic.gdx.graphics.g2d.PolygonRegion, x: scala.Float, y: scala.Float): scala.Unit
  def draw(region: com.badlogic.gdx.graphics.g2d.PolygonRegion, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit
  def draw(region: com.badlogic.gdx.graphics.g2d.PolygonRegion, x: scala.Float, y: scala.Float, originX: scala.Float, originY: scala.Float, width: scala.Float, height: scala.Float, scaleX: scala.Float, scaleY: scala.Float, rotation: scala.Float): scala.Unit
  def draw(texture: com.badlogic.gdx.graphics.Texture, polygonVertices: scala.Array[scala.Float], verticesOffset: scala.Int, verticesCount: scala.Int, polygonTriangles: scala.Array[scala.Short], trianglesOffset: scala.Int, trianglesCount: scala.Int): scala.Unit
}