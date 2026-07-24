package com.badlogic.gdx.math

object GeometryUtils {
  private final val tmp1: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  private final val tmp2: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  private final val tmp3: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  def toBarycoord(p: com.badlogic.gdx.math.Vector2, a: com.badlogic.gdx.math.Vector2, b: com.badlogic.gdx.math.Vector2, c: com.badlogic.gdx.math.Vector2, barycentricOut: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.math.Vector2 = {
    val v0: com.badlogic.gdx.math.Vector2 = GeometryUtils.tmp1.set(b).sub(a)
    val v1: com.badlogic.gdx.math.Vector2 = GeometryUtils.tmp2.set(c).sub(a)
    val v2: com.badlogic.gdx.math.Vector2 = GeometryUtils.tmp3.set(p).sub(a)
    val d00: scala.Float = v0.dot(v0)
    val d01: scala.Float = v0.dot(v1)
    val d11: scala.Float = v1.dot(v1)
    val d20: scala.Float = v2.dot(v0)
    val d21: scala.Float = v2.dot(v1)
    val denom: scala.Float = (d00 * d11) - (d01 * d01)
    barycentricOut.x = ((d11 * d20) - (d01 * d21)) / denom
    barycentricOut.y = ((d00 * d21) - (d01 * d20)) / denom
    return barycentricOut
  }
  def barycoordInsideTriangle(barycentric: com.badlogic.gdx.math.Vector2): scala.Boolean = {
    return ((barycentric.x >= 0) && (barycentric.y >= 0)) && ((barycentric.x + barycentric.y) <= 1)
  }
  def fromBarycoord(barycentric: com.badlogic.gdx.math.Vector2, a: com.badlogic.gdx.math.Vector2, b: com.badlogic.gdx.math.Vector2, c: com.badlogic.gdx.math.Vector2, interpolatedOut: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.math.Vector2 = {
    val u: scala.Float = (1 - barycentric.x) - barycentric.y
    interpolatedOut.x = ((u * a.x) + (barycentric.x * b.x)) + (barycentric.y * c.x)
    interpolatedOut.y = ((u * a.y) + (barycentric.x * b.y)) + (barycentric.y * c.y)
    return interpolatedOut
  }
  def fromBarycoord(barycentric: com.badlogic.gdx.math.Vector2, a: scala.Float, b: scala.Float, c: scala.Float): scala.Float = {
    val u: scala.Float = (1 - barycentric.x) - barycentric.y
    return ((u * a) + (barycentric.x * b)) + (barycentric.y * c)
  }
  def lowestPositiveRoot(a: scala.Float, b: scala.Float, c: scala.Float): scala.Float = {
    val det: scala.Float = (b * b) - ((4 * a) * c)
    if (det < 0) {
      return java.lang.Float.NaN
    } else ()
    val sqrtD: scala.Float = java.lang.Math.sqrt(det).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    val invA: scala.Float = 1 / (2 * a)
    var r1: scala.Float = ((-b) - sqrtD) * invA
    var r2: scala.Float = ((-b) + sqrtD) * invA
    if (r1 > r2) {
      val tmp: scala.Float = r2
      r2 = r1
      r1 = tmp
    } else ()
    if (r1 > 0) {
      return r1
    } else ()
    if (r2 > 0) {
      return r2
    } else ()
    return java.lang.Float.NaN
  }
  def colinear(x1: scala.Float, y1: scala.Float, x2: scala.Float, y2: scala.Float, x3: scala.Float, y3: scala.Float): scala.Boolean = {
    val dx21: scala.Float = x2 - x1
    val dy21: scala.Float = y2 - y1
    val dx32: scala.Float = x3 - x2
    val dy32: scala.Float = y3 - y2
    val det: scala.Float = (dx32 * dy21) - (dx21 * dy32)
    return java.lang.Math.abs(det) < com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR
  }
  def triangleCentroid(x1: scala.Float, y1: scala.Float, x2: scala.Float, y2: scala.Float, x3: scala.Float, y3: scala.Float, centroid: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.math.Vector2 = {
    centroid.x = ((x1 + x2) + x3) / 3
    centroid.y = ((y1 + y2) + y3) / 3
    return centroid
  }
  def triangleCircumcenter(x1: scala.Float, y1: scala.Float, x2: scala.Float, y2: scala.Float, x3: scala.Float, y3: scala.Float, circumcenter: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.math.Vector2 = {
    val dx21: scala.Float = x2 - x1
    val dy21: scala.Float = y2 - y1
    val dx32: scala.Float = x3 - x2
    val dy32: scala.Float = y3 - y2
    val dx13: scala.Float = x1 - x3
    val dy13: scala.Float = y1 - y3
    var det: scala.Float = (dx32 * dy21) - (dx21 * dy32)
    if (java.lang.Math.abs(det) < com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR) {
      throw new java.lang.IllegalArgumentException("Triangle points must not be colinear.")
    } else ()
    det = det * 2
    val sqr1: scala.Float = (x1 * x1) + (y1 * y1)
    val sqr2: scala.Float = (x2 * x2) + (y2 * y2)
    val sqr3: scala.Float = (x3 * x3) + (y3 * y3)
    circumcenter.set((((sqr1 * dy32) + (sqr2 * dy13)) + (sqr3 * dy21)) / det, (-(((sqr1 * dx32) + (sqr2 * dx13)) + (sqr3 * dx21))) / det)
    return circumcenter
  }
  def triangleCircumradius(x1: scala.Float, y1: scala.Float, x2: scala.Float, y2: scala.Float, x3: scala.Float, y3: scala.Float): scala.Float = {
    var m1: scala.Float = 0.0f
    var m2: scala.Float = 0.0f
    var mx1: scala.Float = 0.0f
    var mx2: scala.Float = 0.0f
    var my1: scala.Float = 0.0f
    var my2: scala.Float = 0.0f
    var x: scala.Float = 0.0f
    var y: scala.Float = 0.0f
    if (java.lang.Math.abs(y2 - y1) < com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR) {
      m2 = (-(x3 - x2)) / (y3 - y2)
      mx2 = (x2 + x3) / 2
      my2 = (y2 + y3) / 2
      x = (x2 + x1) / 2
      y = (m2 * (x - mx2)) + my2
    } else {
      if (java.lang.Math.abs(y3 - y2) < com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR) {
        m1 = (-(x2 - x1)) / (y2 - y1)
        mx1 = (x1 + x2) / 2
        my1 = (y1 + y2) / 2
        x = (x3 + x2) / 2
        y = (m1 * (x - mx1)) + my1
      } else {
        m1 = (-(x2 - x1)) / (y2 - y1)
        m2 = (-(x3 - x2)) / (y3 - y2)
        mx1 = (x1 + x2) / 2
        mx2 = (x2 + x3) / 2
        my1 = (y1 + y2) / 2
        my2 = (y2 + y3) / 2
        x = ((((m1 * mx1) - (m2 * mx2)) + my2) - my1) / (m1 - m2)
        y = (m1 * (x - mx1)) + my1
      }
    }
    val dx: scala.Float = x1 - x
    val dy: scala.Float = y1 - y
    return java.lang.Math.sqrt((dx * dx) + (dy * dy)).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  }
  def triangleQuality(x1: scala.Float, y1: scala.Float, x2: scala.Float, y2: scala.Float, x3: scala.Float, y3: scala.Float): scala.Float = {
    val sqLength1: scala.Float = (x1 * x1) + (y1 * y1)
    val sqLength2: scala.Float = (x2 * x2) + (y2 * y2)
    val sqLength3: scala.Float = (x3 * x3) + (y3 * y3)
    return java.lang.Math.sqrt(java.lang.Math.min(sqLength1, java.lang.Math.min(sqLength2, sqLength3))).asInstanceOf[scala.Float] / GeometryUtils.triangleCircumradius(x1, y1, x2, y2, x3, y3)
  }
  def triangleArea(x1: scala.Float, y1: scala.Float, x2: scala.Float, y2: scala.Float, x3: scala.Float, y3: scala.Float): scala.Float = {
    return java.lang.Math.abs(((x1 - x3) * (y2 - y1)) - ((x1 - x2) * (y3 - y1))) * 0.5f
  }
  def quadrilateralCentroid(x1: scala.Float, y1: scala.Float, x2: scala.Float, y2: scala.Float, x3: scala.Float, y3: scala.Float, x4: scala.Float, y4: scala.Float, centroid: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.math.Vector2 = {
    val avgX1: scala.Float = ((x1 + x2) + x3) / 3
    val avgY1: scala.Float = ((y1 + y2) + y3) / 3
    val avgX2: scala.Float = ((x1 + x4) + x3) / 3
    val avgY2: scala.Float = ((y1 + y4) + y3) / 3
    centroid.x = avgX1 - ((avgX1 - avgX2) / 2)
    centroid.y = avgY1 - ((avgY1 - avgY2) / 2)
    return centroid
  }
  def polygonCentroid(polygon: scala.Array[scala.Float], offset: scala.Int, count: scala.Int, centroid: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.math.Vector2 = {
    if (count < 6) {
      throw new java.lang.IllegalArgumentException("A polygon must have 3 or more coordinate pairs.")
    } else ()
    var area: scala.Float = 0
    var x: scala.Float = 0
    var y: scala.Float = 0
    val last: scala.Int = (offset + count) - 2
    var x1: scala.Float = polygon(last)
    var y1: scala.Float = polygon(last + 1);
    { var i: scala.Int = offset; while (i <= last) { {
      val x2: scala.Float = polygon(i)
      val y2: scala.Float = polygon(i + 1)
      val a: scala.Float = (x1 * y2) - (x2 * y1)
      area = area + a
      x = x + ((x1 + x2) * a)
      y = y + ((y1 + y2) * a)
      x1 = x2
      y1 = y2
    }; i = i + 2 } }
    if (area == 0) {
      centroid.x = 0
      centroid.y = 0
    } else {
      area = area * 0.5f
      centroid.x = x / (6 * area)
      centroid.y = y / (6 * area)
    }
    return centroid
  }
  def polygonArea(polygon: scala.Array[scala.Float], offset: scala.Int, count: scala.Int): scala.Float = {
    var area: scala.Float = 0
    val last: scala.Int = (offset + count) - 2
    var x1: scala.Float = polygon(last)
    var y1: scala.Float = polygon(last + 1);
    { var i: scala.Int = offset; while (i <= last) { {
      val x2: scala.Float = polygon(i)
      val y2: scala.Float = polygon(i + 1)
      area = area + ((x1 * y2) - (x2 * y1))
      x1 = x2
      y1 = y2
    }; i = i + 2 } }
    return java.lang.Math.abs(area * 0.5f)
  }
  def ensureCCW(polygon: scala.Array[scala.Float]): scala.Unit = {
    GeometryUtils.ensureCCW(polygon, 0, polygon.length)
  }
  def ensureCCW(polygon: scala.Array[scala.Float], offset: scala.Int, count: scala.Int): scala.Unit = {
    if (!GeometryUtils.isClockwise(polygon, offset, count)) {
      return
    } else ()
    GeometryUtils.reverseVertices(polygon, offset, count)
  }
  def ensureClockwise(polygon: scala.Array[scala.Float]): scala.Unit = {
    GeometryUtils.ensureClockwise(polygon, 0, polygon.length)
  }
  def ensureClockwise(polygon: scala.Array[scala.Float], offset: scala.Int, count: scala.Int): scala.Unit = {
    if (GeometryUtils.isClockwise(polygon, offset, count)) {
      return
    } else ()
    GeometryUtils.reverseVertices(polygon, offset, count)
  }
  def reverseVertices(polygon: scala.Array[scala.Float], offset: scala.Int, count: scala.Int): scala.Unit = {
    val lastX: scala.Int = (offset + count) - 2;
    { var i: scala.Int = offset; val n: scala.Int = offset + (count / 2); while (i < n) { {
      val other: scala.Int = lastX - i
      val x: scala.Float = polygon(i)
      val y: scala.Float = polygon(i + 1)
      polygon(i) = polygon(other)
      polygon(i + 1) = polygon(other + 1)
      polygon(other) = x
      polygon(other + 1) = y
    }; i = i + 2 } }
  }
  def isClockwise(polygon: scala.Array[scala.Float], offset: scala.Int, count: scala.Int): scala.Boolean = {
    if (count <= 2) {
      return false
    } else ()
    var area: scala.Float = 0
    val last: scala.Int = (offset + count) - 2
    var x1: scala.Float = polygon(last)
    var y1: scala.Float = polygon(last + 1);
    { var i: scala.Int = offset; while (i <= last) { {
      val x2: scala.Float = polygon(i)
      val y2: scala.Float = polygon(i + 1)
      area = area + ((x1 * y2) - (x2 * y1))
      x1 = x2
      y1 = y2
    }; i = i + 2 } }
    return area < 0
  }
  def isCCW(polygon: scala.Array[scala.Float], offset: scala.Int, count: scala.Int): scala.Boolean = {
    return !GeometryUtils.isClockwise(polygon, offset, count)
  }
}