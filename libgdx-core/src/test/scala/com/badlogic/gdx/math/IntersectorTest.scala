package com.badlogic.gdx.math

class IntersectorTest extends balticporter.runtime.PortedSuite {
  testCase("testSplitTriangle", {
    val plane: com.badlogic.gdx.math.Plane = new com.badlogic.gdx.math.Plane(new com.badlogic.gdx.math.Vector3(1, 0, 0), 0)
    val split: com.badlogic.gdx.math.Intersector.SplitTriangle = new com.badlogic.gdx.math.Intersector.SplitTriangle(3);
    {
      val fTriangle: scala.Array[scala.Float] = scala.Array[scala.Float](-10, 0, 10, -1, 0, 0, -12, 0, 10)
      com.badlogic.gdx.math.Intersector.splitTriangle(fTriangle, plane, split)
      balticporter.runtime.Asserts.assertTrue(split.numBack == 1)
      balticporter.runtime.Asserts.assertTrue(split.numFront == 0)
      balticporter.runtime.Asserts.assertTrue(split.total == 1)
      balticporter.runtime.Asserts.assertTrue(IntersectorTest.triangleEquals(split.back, 0, 3, fTriangle))
      fTriangle(4) = 5.0f
      balticporter.runtime.Asserts.assertFalse("Test is broken", IntersectorTest.triangleEquals(split.back, 0, 3, fTriangle))
    };
    {
      val fTriangle: scala.Array[scala.Float] = scala.Array[scala.Float](10, 0, 10, 1, 0, 0, 12, 0, 10)
      com.badlogic.gdx.math.Intersector.splitTriangle(fTriangle, plane, split)
      balticporter.runtime.Asserts.assertTrue(split.numBack == 0)
      balticporter.runtime.Asserts.assertTrue(split.numFront == 1)
      balticporter.runtime.Asserts.assertTrue(split.total == 1)
      balticporter.runtime.Asserts.assertTrue(IntersectorTest.triangleEquals(split.front, 0, 3, fTriangle))
    };
    {
      val triangle: scala.Array[scala.Float] = scala.Array[scala.Float](-10, 0, 10, 10, 0, 0, -10, 0, -10)
      com.badlogic.gdx.math.Intersector.splitTriangle(triangle, plane, split)
      balticporter.runtime.Asserts.assertTrue(split.numBack == 2)
      balticporter.runtime.Asserts.assertTrue(split.numFront == 1)
      balticporter.runtime.Asserts.assertTrue(split.total == 3)
      balticporter.runtime.Asserts.assertTrue(IntersectorTest.triangleEquals(split.front, 0, 3, scala.Array[scala.Float](0, 0, 5, 10, 0, 0, 0, 0, -5)))
      val firstWay: scala.Array[scala.Array[scala.Float]] = scala.Array[scala.Array[scala.Float]](scala.Array[scala.Float](-10, 0, 10, 0, 0, 5, 0, 0, -5), scala.Array[scala.Float](-10, 0, 10, 0, 0, -5, -10, 0, -10))
      val secondWay: scala.Array[scala.Array[scala.Float]] = scala.Array[scala.Array[scala.Float]](scala.Array[scala.Float](-10, 0, 10, 0, 0, 5, -10, 0, -10), scala.Array[scala.Float](0, 0, 5, 0, 0, -5, -10, 0, -10))
      val base: scala.Array[scala.Float] = split.back
      val first: scala.Boolean = (IntersectorTest.triangleEquals(base, 0, 3, firstWay(0)) && IntersectorTest.triangleEquals(base, 9, 3, firstWay(1))) || (IntersectorTest.triangleEquals(base, 0, 3, firstWay(1)) && IntersectorTest.triangleEquals(base, 9, 3, firstWay(0)))
      val second: scala.Boolean = (IntersectorTest.triangleEquals(base, 0, 3, secondWay(0)) && IntersectorTest.triangleEquals(base, 9, 3, secondWay(1))) || (IntersectorTest.triangleEquals(base, 0, 3, secondWay(1)) && IntersectorTest.triangleEquals(base, 9, 3, secondWay(0)))
      balticporter.runtime.Asserts.assertTrue(((("Either first or second way must be right (first: " + first) + ", second: ") + second) + ")", first ^ second)
    };
    {
      val triangle: scala.Array[scala.Float] = scala.Array[scala.Float](10, 0, 10, -10, 0, 0, 10, 0, -10)
      com.badlogic.gdx.math.Intersector.splitTriangle(triangle, plane, split)
      balticporter.runtime.Asserts.assertTrue(split.numBack == 1)
      balticporter.runtime.Asserts.assertTrue(split.numFront == 2)
      balticporter.runtime.Asserts.assertTrue(split.total == 3)
      balticporter.runtime.Asserts.assertTrue(IntersectorTest.triangleEquals(split.back, 0, 3, scala.Array[scala.Float](0, 0, 5, -10, 0, 0, 0, 0, -5)))
      val firstWay: scala.Array[scala.Array[scala.Float]] = scala.Array[scala.Array[scala.Float]](scala.Array[scala.Float](10, 0, 10, 0, 0, 5, 0, 0, -5), scala.Array[scala.Float](10, 0, 10, 0, 0, -5, 10, 0, -10))
      val secondWay: scala.Array[scala.Array[scala.Float]] = scala.Array[scala.Array[scala.Float]](scala.Array[scala.Float](10, 0, 10, 0, 0, 5, 10, 0, -10), scala.Array[scala.Float](0, 0, 5, 0, 0, -5, 10, 0, -10))
      val base: scala.Array[scala.Float] = split.front
      val first: scala.Boolean = (IntersectorTest.triangleEquals(base, 0, 3, firstWay(0)) && IntersectorTest.triangleEquals(base, 9, 3, firstWay(1))) || (IntersectorTest.triangleEquals(base, 0, 3, firstWay(1)) && IntersectorTest.triangleEquals(base, 9, 3, firstWay(0)))
      val second: scala.Boolean = (IntersectorTest.triangleEquals(base, 0, 3, secondWay(0)) && IntersectorTest.triangleEquals(base, 9, 3, secondWay(1))) || (IntersectorTest.triangleEquals(base, 0, 3, secondWay(1)) && IntersectorTest.triangleEquals(base, 9, 3, secondWay(0)))
      balticporter.runtime.Asserts.assertTrue(((("Either first or second way must be right (first: " + first) + ", second: ") + second) + ")", first ^ second)
    }
  })
  testCase("intersectSegmentCircle", {
    val circle: com.badlogic.gdx.math.Circle = new com.badlogic.gdx.math.Circle(5.0f, 5.0f, 4.0f)
    var intersects: scala.Boolean = com.badlogic.gdx.math.Intersector.intersectSegmentCircle(new com.badlogic.gdx.math.Vector2(0, 1.0f), new com.badlogic.gdx.math.Vector2(12.0f, 3.0f), circle, null)
    balticporter.runtime.Asserts.assertTrue(intersects)
    intersects = com.badlogic.gdx.math.Intersector.intersectSegmentCircle(new com.badlogic.gdx.math.Vector2(0, 5.0f), new com.badlogic.gdx.math.Vector2(2.0f, 5.0f), circle, null)
    balticporter.runtime.Asserts.assertTrue(intersects)
    intersects = com.badlogic.gdx.math.Intersector.intersectSegmentCircle(new com.badlogic.gdx.math.Vector2(5.5f, 6.0f), new com.badlogic.gdx.math.Vector2(7.0f, 5.5f), circle, null)
    balticporter.runtime.Asserts.assertTrue(intersects)
    intersects = com.badlogic.gdx.math.Intersector.intersectSegmentCircle(new com.badlogic.gdx.math.Vector2(0.0f, 6.0f), new com.badlogic.gdx.math.Vector2(0.5f, 2.0f), circle, null)
    balticporter.runtime.Asserts.assertFalse(intersects)
    val mtv: com.badlogic.gdx.math.Intersector.MinimumTranslationVector = new com.badlogic.gdx.math.Intersector.MinimumTranslationVector()
    intersects = com.badlogic.gdx.math.Intersector.intersectSegmentCircle(new com.badlogic.gdx.math.Vector2(1.5f, 6.0f), new com.badlogic.gdx.math.Vector2(1.5f, 3.0f), circle, mtv)
    balticporter.runtime.Asserts.assertTrue(intersects)
    balticporter.runtime.Asserts.assertTrue(mtv.normal.equals(new com.badlogic.gdx.math.Vector2(-1.0f, 0)))
    balticporter.runtime.Asserts.assertTrue(mtv.depth == 0.5f)
    intersects = com.badlogic.gdx.math.Intersector.intersectSegmentCircle(new com.badlogic.gdx.math.Vector2(4.0f, 5.0f), new com.badlogic.gdx.math.Vector2(6.0f, 5.0f), circle, mtv)
    balticporter.runtime.Asserts.assertTrue(intersects)
    balticporter.runtime.Asserts.assertTrue(mtv.normal.equals(new com.badlogic.gdx.math.Vector2(0, 1.0f)) || mtv.normal.equals(new com.badlogic.gdx.math.Vector2(0.0f, -1.0f)))
    balticporter.runtime.Asserts.assertTrue(mtv.depth == 4.0f)
    intersects = com.badlogic.gdx.math.Intersector.intersectSegmentCircle(new com.badlogic.gdx.math.Vector2(4.0f, 5.0f), new com.badlogic.gdx.math.Vector2(5.0f, 5.0f), circle, mtv)
    balticporter.runtime.Asserts.assertTrue(intersects)
    balticporter.runtime.Asserts.assertTrue(mtv.normal.equals(new com.badlogic.gdx.math.Vector2(0, 1.0f)) || mtv.normal.equals(new com.badlogic.gdx.math.Vector2(0.0f, -1.0f)))
    balticporter.runtime.Asserts.assertTrue(mtv.depth == 4.0f)
  })
  testCase("testIntersectPlanes", {
    val NEAR: scala.Int = 0
    val FAR: scala.Int = 1
    val LEFT: scala.Int = 2
    val RIGHT: scala.Int = 3
    val TOP: scala.Int = 4
    val BOTTOM: scala.Int = 5
    val planes: scala.Array[com.badlogic.gdx.math.Plane] = new scala.Array[com.badlogic.gdx.math.Plane](6)
    planes(NEAR) = new com.badlogic.gdx.math.Plane(new com.badlogic.gdx.math.Vector3(0.0f, 0.0f, 1.0f), -0.1f)
    planes(FAR) = new com.badlogic.gdx.math.Plane(new com.badlogic.gdx.math.Vector3(0.0f, -0.0f, -1.0f), 99.99771f)
    planes(LEFT) = new com.badlogic.gdx.math.Plane(new com.badlogic.gdx.math.Vector3(-0.69783056f, 0.0f, 0.71626294f), -9.3877316E-7f)
    planes(RIGHT) = new com.badlogic.gdx.math.Plane(new com.badlogic.gdx.math.Vector3(0.6978352f, 0.0f, 0.71625835f), -0.0f)
    planes(TOP) = new com.badlogic.gdx.math.Plane(new com.badlogic.gdx.math.Vector3(0.0f, -0.86602545f, 0.5f), -0.0f)
    planes(BOTTOM) = new com.badlogic.gdx.math.Plane(new com.badlogic.gdx.math.Vector3(-0.0f, 0.86602545f, 0.5f), -0.0f)
    val intersection: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
    com.badlogic.gdx.math.Intersector.intersectPlanes(planes(TOP), planes(FAR), planes(LEFT), intersection)
    balticporter.runtime.Asserts.assertEquals(102.63903f, intersection.x, 0.1f)
    balticporter.runtime.Asserts.assertEquals(57.7337f, intersection.y, 0.1f)
    balticporter.runtime.Asserts.assertEquals(100, intersection.z, 0.1f)
    com.badlogic.gdx.math.Intersector.intersectPlanes(planes(TOP), planes(FAR), planes(RIGHT), intersection)
    balticporter.runtime.Asserts.assertEquals(-102.63903f, intersection.x, 0.1f)
    balticporter.runtime.Asserts.assertEquals(57.7337f, intersection.y, 0.1f)
    balticporter.runtime.Asserts.assertEquals(100, intersection.z, 0.1f)
    com.badlogic.gdx.math.Intersector.intersectPlanes(planes(BOTTOM), planes(FAR), planes(LEFT), intersection)
    balticporter.runtime.Asserts.assertEquals(102.63903f, intersection.x, 0.1f)
    balticporter.runtime.Asserts.assertEquals(-57.7337f, intersection.y, 0.1f)
    balticporter.runtime.Asserts.assertEquals(100, intersection.z, 0.1f)
    com.badlogic.gdx.math.Intersector.intersectPlanes(planes(BOTTOM), planes(FAR), planes(RIGHT), intersection)
    balticporter.runtime.Asserts.assertEquals(-102.63903f, intersection.x, 0.1f)
    balticporter.runtime.Asserts.assertEquals(-57.7337f, intersection.y, 0.1f)
    balticporter.runtime.Asserts.assertEquals(100, intersection.z, 0.1f)
  })
  testCase("testIsPointInTriangle2D", {
    balticporter.runtime.Asserts.assertFalse(com.badlogic.gdx.math.Intersector.isPointInTriangle(new com.badlogic.gdx.math.Vector2(0.1f, 0), new com.badlogic.gdx.math.Vector2(0, 0), new com.badlogic.gdx.math.Vector2(1, 1), new com.badlogic.gdx.math.Vector2(-1, -1)))
    balticporter.runtime.Asserts.assertTrue(com.badlogic.gdx.math.Intersector.isPointInTriangle(new com.badlogic.gdx.math.Vector2(0, 0.1f), new com.badlogic.gdx.math.Vector2(-1, 1), new com.badlogic.gdx.math.Vector2(1, 1), new com.badlogic.gdx.math.Vector2(-1, -2)))
  })
  testCase("testIsPointInTriangle3D", {
    balticporter.runtime.Asserts.assertFalse(com.badlogic.gdx.math.Intersector.isPointInTriangle(new com.badlogic.gdx.math.Vector3(0.1f, 0, 0), new com.badlogic.gdx.math.Vector3(0, 0, 0), new com.badlogic.gdx.math.Vector3(1, 1, 0), new com.badlogic.gdx.math.Vector3(-1, -1, 0)))
    balticporter.runtime.Asserts.assertTrue(com.badlogic.gdx.math.Intersector.isPointInTriangle(new com.badlogic.gdx.math.Vector3(0, 0.1f, 0), new com.badlogic.gdx.math.Vector3(-1, 1, 0), new com.badlogic.gdx.math.Vector3(1, 1, 0), new com.badlogic.gdx.math.Vector3(-1, -2, 0)))
    balticporter.runtime.Asserts.assertTrue(com.badlogic.gdx.math.Intersector.isPointInTriangle(new com.badlogic.gdx.math.Vector3(0.2f, 0, 1.25f), new com.badlogic.gdx.math.Vector3(-1, 1, 0), new com.badlogic.gdx.math.Vector3(1.4f, 0.99f, 2.5f), new com.badlogic.gdx.math.Vector3(-1, -2, 0)))
    balticporter.runtime.Asserts.assertFalse(com.badlogic.gdx.math.Intersector.isPointInTriangle(new com.badlogic.gdx.math.Vector3(2.6f, 0, 3.75f), new com.badlogic.gdx.math.Vector3(-1, 1, 0), new com.badlogic.gdx.math.Vector3(1.4f, 0.99f, 2.5f), new com.badlogic.gdx.math.Vector3(-1, -2, 0)))
    balticporter.runtime.Asserts.assertTrue(com.badlogic.gdx.math.Intersector.isPointInTriangle(new com.badlogic.gdx.math.Vector3(0, -0.5f, 0.5f), new com.badlogic.gdx.math.Vector3(-1, 1, 0), new com.badlogic.gdx.math.Vector3(1, 1, 1), new com.badlogic.gdx.math.Vector3(-1, -2, 0)))
    val epsilon: scala.Float = 1.0E-7f
    val almost1: scala.Float = 1 - epsilon
    balticporter.runtime.Asserts.assertFalse(com.badlogic.gdx.math.Intersector.isPointInTriangle(new com.badlogic.gdx.math.Vector3(0, -0.5f, 0.5f), new com.badlogic.gdx.math.Vector3(-1, 1, 0), new com.badlogic.gdx.math.Vector3(almost1, 1, 1), new com.badlogic.gdx.math.Vector3(-1, -2, 0)))
    balticporter.runtime.Asserts.assertFalse(com.badlogic.gdx.math.Intersector.isPointInTriangle(new com.badlogic.gdx.math.Vector3(199.0f, 1.0f, 500.0f), new com.badlogic.gdx.math.Vector3(-1, 1, 0), new com.badlogic.gdx.math.Vector3(1, 1, 5.0f), new com.badlogic.gdx.math.Vector3(-1, -2, 0)))
    balticporter.runtime.Asserts.assertFalse(com.badlogic.gdx.math.Intersector.isPointInTriangle(new com.badlogic.gdx.math.Vector3(-5120.8345f, 8946.126f, -3270.5813f), new com.badlogic.gdx.math.Vector3(50.008057f, 22.20586f, 124.62208f), new com.badlogic.gdx.math.Vector3(62.282288f, 22.205864f, 109.665924f), new com.badlogic.gdx.math.Vector3(70.92052f, 7.205861f, 115.437805f)))
  })
  testCase("testIntersectPolygons", {
    val intersectionPolygon: com.badlogic.gdx.math.Polygon = new com.badlogic.gdx.math.Polygon()
    balticporter.runtime.Asserts.assertFalse(com.badlogic.gdx.math.Intersector.intersectPolygons(new com.badlogic.gdx.math.Polygon(scala.Array[scala.Float](3200.1453f, 88.00839f, 3233.9087f, 190.34174f, 3266.2905f, 0.0f)), new com.badlogic.gdx.math.Polygon(scala.Array[scala.Float](3213.0f, 131.0f, 3214.0f, 131.0f, 3214.0f, 130.0f, 3213.0f, 130.0f)), intersectionPolygon))
    balticporter.runtime.Asserts.assertEquals(0, intersectionPolygon.getVertexCount())
  })
  testCase("testIntersectPolygonsWithVertexLyingOnEdge", {
    val p1: com.badlogic.gdx.math.Polygon = new com.badlogic.gdx.math.Polygon(scala.Array[scala.Float](1, -1, 2, -1, 2, -2, 1, -2))
    val p2: com.badlogic.gdx.math.Polygon = new com.badlogic.gdx.math.Polygon(scala.Array[scala.Float](0.5f, -1.5f, 1.5f, -1.5f, 1.5f, -2.5f))
    val intersectionPolygon: com.badlogic.gdx.math.Polygon = new com.badlogic.gdx.math.Polygon()
    val checkResult: scala.Boolean = com.badlogic.gdx.math.Intersector.intersectPolygons(p1, p2, intersectionPolygon)
    balticporter.runtime.Asserts.assertTrue(checkResult)
    balticporter.runtime.Asserts.assertEquals(4, intersectionPolygon.getVertexCount())
    balticporter.runtime.Asserts.assertEquals(new com.badlogic.gdx.math.Vector2(1.0f, -2.0f), intersectionPolygon.getVertex(0, new com.badlogic.gdx.math.Vector2()))
    balticporter.runtime.Asserts.assertEquals(new com.badlogic.gdx.math.Vector2(1.0f, -1.5f), intersectionPolygon.getVertex(1, new com.badlogic.gdx.math.Vector2()))
    balticporter.runtime.Asserts.assertEquals(new com.badlogic.gdx.math.Vector2(1.5f, -1.5f), intersectionPolygon.getVertex(2, new com.badlogic.gdx.math.Vector2()))
    balticporter.runtime.Asserts.assertEquals(new com.badlogic.gdx.math.Vector2(1.5f, -2.0f), intersectionPolygon.getVertex(3, new com.badlogic.gdx.math.Vector2()))
  })
  testCase("testIntersectPolygonsWithTransformationsOnProvidedResultPolygon", {
    val p1: com.badlogic.gdx.math.Polygon = new com.badlogic.gdx.math.Polygon(scala.Array[scala.Float](1, -1, 2, -1, 2, -2, 1, -2))
    val p2: com.badlogic.gdx.math.Polygon = new com.badlogic.gdx.math.Polygon(scala.Array[scala.Float](0.5f, -1.5f, 1.5f, -1.5f, 1.5f, -2.5f))
    val intersectionPolygon: com.badlogic.gdx.math.Polygon = new com.badlogic.gdx.math.Polygon(new scala.Array[scala.Float](8))
    intersectionPolygon.setScale(5, 5)
    intersectionPolygon.setOrigin(10, 20)
    intersectionPolygon.setPosition(-33, -33)
    intersectionPolygon.setRotation(48)
    val checkResult: scala.Boolean = com.badlogic.gdx.math.Intersector.intersectPolygons(p1, p2, intersectionPolygon)
    balticporter.runtime.Asserts.assertTrue(checkResult)
    balticporter.runtime.Asserts.assertArrayEquals(scala.Array[scala.Float](1, -2, 1, -1.5f, 1.5f, -1.5f, 1.5f, -2), intersectionPolygon.getVertices(), 0)
    balticporter.runtime.Asserts.assertArrayEquals(scala.Array[scala.Float](1, -2, 1, -1.5f, 1.5f, -1.5f, 1.5f, -2), intersectionPolygon.getTransformedVertices(), 0)
    intersectionPolygon.setScale(2, 2)
    balticporter.runtime.Asserts.assertArrayEquals(scala.Array[scala.Float](2 * 1, 2 * (-2), 2 * 1, 2 * (-1.5f), 2 * 1.5f, 2 * (-1.5f), 2 * 1.5f, 2 * (-2)), intersectionPolygon.getTransformedVertices(), 0)
  })
}
object IntersectorTest {
  private def triangleEquals(base: scala.Array[scala.Float], baseOffset: scala.Int, stride: scala.Int, comp: scala.Array[scala.Float]): scala.Boolean = {
    balticporter.runtime.Asserts.assertTrue(stride >= 3)
    balticporter.runtime.Asserts.assertTrue((base.length - baseOffset) >= 9)
    balticporter.runtime.Asserts.assertTrue(comp.length == 9)
    var offset: scala.Int = -1;
    { var i: scala.Int = 0; while (i < 3) { {
      val b: scala.Int = baseOffset + (i * stride)
      if ((com.badlogic.gdx.math.MathUtils.isEqual(base(b), comp(0)) && com.badlogic.gdx.math.MathUtils.isEqual(base(b + 1), comp(1))) && com.badlogic.gdx.math.MathUtils.isEqual(base(b + 2), comp(2))) {
        offset = i
        /* break */ ()
      } else ()
    }; i = i + 1 } }
    balticporter.runtime.Asserts.assertTrue("Triangles do not have common first vertex.", offset != (-1));
    { var i: scala.Int = 0; while (i < 3) { {
      val b: scala.Int = baseOffset + (((offset + i) * stride) % (3 * stride))
      val c: scala.Int = i * stride
      if (((!com.badlogic.gdx.math.MathUtils.isEqual(base(b), comp(c))) || (!com.badlogic.gdx.math.MathUtils.isEqual(base(b + 1), comp(c + 1)))) || (!com.badlogic.gdx.math.MathUtils.isEqual(base(b + 2), comp(c + 2)))) {
        return false
      } else ()
    }; i = i + 1 } }
    return true
  }
}