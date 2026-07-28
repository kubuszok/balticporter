package com.badlogic.gdx.math

class ConvexHullTest extends munit.FunSuite {
  test("testComputePolygon")({
    val convexHull: com.badlogic.gdx.math.ConvexHull = new com.badlogic.gdx.math.ConvexHull()
    val rawPolygon: scala.Array[scala.Float] = scala.Array[scala.Float](0, 0, 0, 1, 1, 1, 1, 0)
    val polygonCounterclockwise: scala.Array[scala.Float] = scala.Array[scala.Float](1, 0, 1, 1, 0, 1, 0, 0)
    this.assertArraySimilar(convexHull.computePolygon(rawPolygon, 0, 8, false), polygonCounterclockwise, 0, 8)
    this.assertArraySimilar(convexHull.computePolygon(rawPolygon, false), polygonCounterclockwise, 0, 8)
    this.assertArraySimilar(convexHull.computePolygon(rawPolygon, 2, 6, false), polygonCounterclockwise, 0, 6)
    this.assertArraySimilar(convexHull.computePolygon(rawPolygon, 0, 6, false), polygonCounterclockwise, 2, 6)
    this.assertArraySimilar(new com.badlogic.gdx.math.ConvexHull().computePolygon(rawPolygon, 0, 8, false), polygonCounterclockwise, 0, 8)
    this.assertArraySimilar(new com.badlogic.gdx.math.ConvexHull().computePolygon(rawPolygon, false), polygonCounterclockwise, 0, 8)
    this.assertArraySimilar(new com.badlogic.gdx.math.ConvexHull().computePolygon(rawPolygon, 2, 6, false), polygonCounterclockwise, 0, 6)
    this.assertArraySimilar(new com.badlogic.gdx.math.ConvexHull().computePolygon(rawPolygon, 0, 6, false), polygonCounterclockwise, 2, 6)
  })
  private def assertArraySimilar(array: com.badlogic.gdx.utils.FloatArray, witness: scala.Array[scala.Float], witnessOffset: scala.Int, witnessCount: scala.Int): scala.Unit = {
    val witnessLength: scala.Int = witnessCount + witnessOffset
    assert((witnessCount + witnessOffset) <= witness.length)
    assertEquals(array.size, witnessCount + 2)
    assertEquals(0, array.items(array.size - 2), array.items(0))
    assertEquals(0, array.items(array.size - 1), array.items(1));
    { var offset: scala.Int = 0; while (offset < witnessLength) { {
      var contentMatches: scala.Boolean = true;
      { var i: scala.Int = 0; while (i < witnessLength) { {
        val j: scala.Int = ((offset + i) % witnessCount) + witnessOffset
        if (array.get(i) != witness(j)) {
          contentMatches = false
          /* break */ ()
        } else ()
      }; i = i + 1 } }
      if (contentMatches) {
        return
      } else ()
    }; offset = offset + 1 } }
    fail((("Array items " + array.toString()) + " does not match witness array ") + java.util.Arrays.toString(witness))
  }
}