package com.badlogic.gdx.math

class PolygonTest extends munit.FunSuite {
  test("testZeroRotation")({
    val vertices: scala.Array[scala.Float] = scala.Array[scala.Float](0, 0, 3, 0, 3, 4)
    val polygon: com.badlogic.gdx.math.Polygon = new com.badlogic.gdx.math.Polygon(vertices)
    polygon.rotate(0)
    balticporter.runtime.Asserts.assertArrayEquals("The polygon's vertices don't correspond.", polygon.getTransformedVertices(), polygon.getVertices(), 1.0f)
  })
  test("test360Rotation")({
    val vertices: scala.Array[scala.Float] = scala.Array[scala.Float](0, 0, 3, 0, 3, 4)
    val polygon: com.badlogic.gdx.math.Polygon = new com.badlogic.gdx.math.Polygon(vertices)
    polygon.rotate(360)
    balticporter.runtime.Asserts.assertArrayEquals("The polygon's vertices don't correspond.", polygon.getTransformedVertices(), polygon.getVertices(), 1.0f)
  })
  test("testConcavePolygonArea")({
    val concaveVertices: scala.Array[scala.Float] = scala.Array[scala.Float](0, 0, 2, 4, 4, 0, 2, 2)
    val concavePolygon: com.badlogic.gdx.math.Polygon = new com.badlogic.gdx.math.Polygon(concaveVertices)
    val expectedArea: scala.Float = 4.0f
    balticporter.runtime.Asserts.assertEquals("The area doesn't correspond.", expectedArea, concavePolygon.area(), 1.0f)
  })
  test("testTriangleArea")({
    val triangleVertices: scala.Array[scala.Float] = scala.Array[scala.Float](0, 0, 2, 3, 4, 0)
    val triangle: com.badlogic.gdx.math.Polygon = new com.badlogic.gdx.math.Polygon(triangleVertices)
    val expectedArea: scala.Float = 6.0f
    balticporter.runtime.Asserts.assertEquals("The area doesn't correspond.", expectedArea, triangle.area(), 1.0f)
  })
}