package com.badlogic.gdx.math

class PolygonTest {
  @org.junit.Test
  def testZeroRotation(): scala.Unit = {
    val vertices: scala.Array[scala.Float] = scala.Array[scala.Float](0, 0, 3, 0, 3, 4)
    val polygon: com.badlogic.gdx.math.Polygon = new com.badlogic.gdx.math.Polygon(vertices)
    polygon.rotate(0)
    org.junit.Assert.assertArrayEquals("The polygon's vertices don't correspond.", polygon.getTransformedVertices(), polygon.getVertices(), 1.0f)
  }
  @org.junit.Test
  def test360Rotation(): scala.Unit = {
    val vertices: scala.Array[scala.Float] = scala.Array[scala.Float](0, 0, 3, 0, 3, 4)
    val polygon: com.badlogic.gdx.math.Polygon = new com.badlogic.gdx.math.Polygon(vertices)
    polygon.rotate(360)
    org.junit.Assert.assertArrayEquals("The polygon's vertices don't correspond.", polygon.getTransformedVertices(), polygon.getVertices(), 1.0f)
  }
  @org.junit.Test
  def testConcavePolygonArea(): scala.Unit = {
    val concaveVertices: scala.Array[scala.Float] = scala.Array[scala.Float](0, 0, 2, 4, 4, 0, 2, 2)
    val concavePolygon: com.badlogic.gdx.math.Polygon = new com.badlogic.gdx.math.Polygon(concaveVertices)
    val expectedArea: scala.Float = 4.0f
    org.junit.Assert.assertEquals("The area doesn't correspond.", expectedArea, concavePolygon.area(), 1.0f)
  }
  @org.junit.Test
  def testTriangleArea(): scala.Unit = {
    val triangleVertices: scala.Array[scala.Float] = scala.Array[scala.Float](0, 0, 2, 3, 4, 0)
    val triangle: com.badlogic.gdx.math.Polygon = new com.badlogic.gdx.math.Polygon(triangleVertices)
    val expectedArea: scala.Float = 6.0f
    org.junit.Assert.assertEquals("The area doesn't correspond.", expectedArea, triangle.area(), 1.0f)
  }
}