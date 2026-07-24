package com.badlogic.gdx.math

class DelaunayTriangulator {
  private final val quicksortStack: com.badlogic.gdx.utils.IntArray = new com.badlogic.gdx.utils.IntArray()
  private var sortedPoints: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  private final val triangles: com.badlogic.gdx.utils.ShortArray = new com.badlogic.gdx.utils.ShortArray(false, 16)
  private final val originalIndices: com.badlogic.gdx.utils.ShortArray = new com.badlogic.gdx.utils.ShortArray(false, 0)
  private final val edges: com.badlogic.gdx.utils.IntArray = new com.badlogic.gdx.utils.IntArray()
  private final val complete: com.badlogic.gdx.utils.BooleanArray = new com.badlogic.gdx.utils.BooleanArray(false, 16)
  private final val superTriangle: scala.Array[scala.Float] = new Array[scala.Float](6)
  private final val centroid: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  def computeTriangles(points: com.badlogic.gdx.utils.FloatArray, sorted: scala.Boolean): com.badlogic.gdx.utils.ShortArray = {
    return this.computeTriangles(points.items, 0, points.size, sorted)
  }
  def computeTriangles(polygon: scala.Array[scala.Float], sorted: scala.Boolean): com.badlogic.gdx.utils.ShortArray = {
    return this.computeTriangles(polygon, 0, polygon.length, sorted)
  }
  def computeTriangles(points$arg: scala.Array[scala.Float], offset$arg: scala.Int, count: scala.Int, sorted: scala.Boolean): com.badlogic.gdx.utils.ShortArray = {
    var points: scala.Array[scala.Float] = points$arg
    var offset: scala.Int = offset$arg
    if (count > 32767) {
      throw new java.lang.IllegalArgumentException("count must be <= " + 32767)
    } else ()
    val triangles: com.badlogic.gdx.utils.ShortArray = this.triangles
    triangles.clear()
    if (count < 6) {
      return triangles
    } else ()
    triangles.ensureCapacity(count)
    if (!sorted) {
      if ((this.sortedPoints == null) || (this.sortedPoints.length < count)) {
        this.sortedPoints = new Array[scala.Float](count)
      } else ()
      java.lang.System.arraycopy(points, offset, this.sortedPoints, 0, count)
      points = this.sortedPoints
      offset = 0
      this.sort(points, count)
    } else ()
    val `end`: scala.Int = offset + count
    var xmin: scala.Float = points(0)
    var ymin: scala.Float = points(1)
    var xmax: scala.Float = xmin
    var ymax: scala.Float = ymin;
    { var i: scala.Int = offset + 2; while (i < `end`) { {
      var value: scala.Float = points(i)
      if (value < xmin) {
        xmin = value
      } else ()
      if (value > xmax) {
        xmax = value
      } else ()
      i = i + 1
      value = points(i)
      if (value < ymin) {
        ymin = value
      } else ()
      if (value > ymax) {
        ymax = value
      } else ()
    }; i = i + 1 } }
    val dx: scala.Float = xmax - xmin
    val dy: scala.Float = ymax - ymin
    val dmax: scala.Float = (if (dx > dy) dx else dy) * 20.0f
    val xmid: scala.Float = (xmax + xmin) / 2.0f
    val ymid: scala.Float = (ymax + ymin) / 2.0f
    val superTriangle: scala.Array[scala.Float] = this.superTriangle
    superTriangle(0) = xmid - dmax
    superTriangle(1) = ymid - dmax
    superTriangle(2) = xmid
    superTriangle(3) = ymid + dmax
    superTriangle(4) = xmid + dmax
    superTriangle(5) = ymid - dmax
    val edges: com.badlogic.gdx.utils.IntArray = this.edges
    edges.ensureCapacity(count / 2)
    val complete: com.badlogic.gdx.utils.BooleanArray = this.complete
    complete.clear()
    complete.ensureCapacity(count)
    triangles.add(`end`)
    triangles.add(`end` + 2)
    triangles.add(`end` + 4)
    complete.add(false);
    { var pointIndex: scala.Int = offset; while (pointIndex < `end`) { {
      val x: scala.Float = points(pointIndex)
      val y: scala.Float = points(pointIndex + 1)
      val trianglesArray: scala.Array[scala.Short] = triangles.items
      val completeArray: scala.Array[scala.Boolean] = complete.items;
      { var triangleIndex: scala.Int = triangles.size - 1; while (triangleIndex >= 0) { {
        val completeIndex: scala.Int = triangleIndex / 3
        if (completeArray(completeIndex)) {
          /* continue */ ()
        } else ()
        val p1: scala.Int = trianglesArray(triangleIndex - 2)
        val p2: scala.Int = trianglesArray(triangleIndex - 1)
        val p3: scala.Int = trianglesArray(triangleIndex)
        var x1: scala.Float = 0.0f
        var y1: scala.Float = 0.0f
        var x2: scala.Float = 0.0f
        var y2: scala.Float = 0.0f
        var x3: scala.Float = 0.0f
        var y3: scala.Float = 0.0f
        if (p1 >= `end`) {
          var i: scala.Int = p1 - `end`
          x1 = superTriangle(i)
          y1 = superTriangle(i + 1)
        } else {
          x1 = points(p1)
          y1 = points(p1 + 1)
        }
        if (p2 >= `end`) {
          var i: scala.Int = p2 - `end`
          x2 = superTriangle(i)
          y2 = superTriangle(i + 1)
        } else {
          x2 = points(p2)
          y2 = points(p2 + 1)
        }
        if (p3 >= `end`) {
          var i: scala.Int = p3 - `end`
          x3 = superTriangle(i)
          y3 = superTriangle(i + 1)
        } else {
          x3 = points(p3)
          y3 = points(p3 + 1)
        }
        this.circumCircle(x, y, x1, y1, x2, y2, x3, y3) match {
          case DelaunayTriangulator.COMPLETE => {
            completeArray(completeIndex) = true
          }
          case DelaunayTriangulator.INSIDE => {
            edges.add(p1, p2, p2, p3)
            edges.add(p3, p1)
            triangles.removeRange(triangleIndex - 2, triangleIndex)
            complete.removeIndex(completeIndex)
          }
        }
      }; triangleIndex = triangleIndex - 3 } }
      val edgesArray: scala.Array[scala.Int] = edges.items;
      { var i: scala.Int = 0; val n: scala.Int = edges.size; while (i < n) { {
        val p1: scala.Int = edgesArray(i)
        if (p1 == (-1)) {
          /* continue */ ()
        } else ()
        val p2: scala.Int = edgesArray(i + 1)
        var skip: scala.Boolean = false;
        { var ii: scala.Int = i + 2; while (ii < n) { {
          if ((p1 == edgesArray(ii + 1)) && (p2 == edgesArray(ii))) {
            skip = true
            edgesArray(ii) = -1
          } else ()
        }; ii = ii + 2 } }
        if (skip) {
          /* continue */ ()
        } else ()
        triangles.add(p1)
        triangles.add(edgesArray(i + 1))
        triangles.add(pointIndex)
        complete.add(false)
      }; i = i + 2 } }
      edges.clear()
    }; pointIndex = pointIndex + 2 } }
    val trianglesArray: scala.Array[scala.Short] = triangles.items;
    { var i: scala.Int = triangles.size - 1; while (i >= 0) { {
      if (((trianglesArray(i) >= `end`) || (trianglesArray(i - 1) >= `end`)) || (trianglesArray(i - 2) >= `end`)) {
        triangles.removeIndex(i)
        triangles.removeIndex(i - 1)
        triangles.removeIndex(i - 2)
      } else ()
    }; i = i - 3 } }
    if (!sorted) {
      val originalIndicesArray: scala.Array[scala.Short] = this.originalIndices.items;
      { var i: scala.Int = 0; val n: scala.Int = triangles.size; while (i < n) { {
        trianglesArray(i) = (originalIndicesArray(trianglesArray(i) / 2) * 2).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
      }; i = i + 1 } }
    } else ()
    if (offset == 0) {
      { var i: scala.Int = 0; val n: scala.Int = triangles.size; while (i < n) { {
        trianglesArray(i) = (trianglesArray(i) / 2).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
      }; i = i + 1 } }
    } else {
      { var i: scala.Int = 0; val n: scala.Int = triangles.size; while (i < n) { {
        trianglesArray(i) = ((trianglesArray(i) - offset) / 2).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
      }; i = i + 1 } }
    }
    return triangles
  }
  private def circumCircle(xp: scala.Float, yp: scala.Float, x1: scala.Float, y1: scala.Float, x2: scala.Float, y2: scala.Float, x3: scala.Float, y3: scala.Float): scala.Int = {
    var xc: scala.Float = 0.0f
    var yc: scala.Float = 0.0f
    val y1y2: scala.Float = java.lang.Math.abs(y1 - y2)
    val y2y3: scala.Float = java.lang.Math.abs(y2 - y3)
    if (y1y2 < DelaunayTriangulator.EPSILON) {
      if (y2y3 < DelaunayTriangulator.EPSILON) {
        return DelaunayTriangulator.INCOMPLETE
      } else ()
      val m2: scala.Float = (-(x3 - x2)) / (y3 - y2)
      val mx2: scala.Float = (x2 + x3) / 2.0f
      val my2: scala.Float = (y2 + y3) / 2.0f
      xc = (x2 + x1) / 2.0f
      yc = (m2 * (xc - mx2)) + my2
    } else {
      val m1: scala.Float = (-(x2 - x1)) / (y2 - y1)
      val mx1: scala.Float = (x1 + x2) / 2.0f
      val my1: scala.Float = (y1 + y2) / 2.0f
      if (y2y3 < DelaunayTriangulator.EPSILON) {
        xc = (x3 + x2) / 2.0f
        yc = (m1 * (xc - mx1)) + my1
      } else {
        val m2: scala.Float = (-(x3 - x2)) / (y3 - y2)
        val mx2: scala.Float = (x2 + x3) / 2.0f
        val my2: scala.Float = (y2 + y3) / 2.0f
        xc = ((((m1 * mx1) - (m2 * mx2)) + my2) - my1) / (m1 - m2)
        yc = (m1 * (xc - mx1)) + my1
      }
    }
    var dx: scala.Float = x2 - xc
    var dy: scala.Float = y2 - yc
    val rsqr: scala.Float = (dx * dx) + (dy * dy)
    dx = xp - xc
    dx = dx * dx
    dy = yp - yc
    if (((dx + (dy * dy)) - rsqr) <= DelaunayTriangulator.EPSILON) {
      return DelaunayTriangulator.INSIDE
    } else ()
    return if ((xp > xc) && (dx > rsqr)) DelaunayTriangulator.COMPLETE else DelaunayTriangulator.INCOMPLETE
  }
  private def sort(values: scala.Array[scala.Float], count: scala.Int): scala.Unit = {
    val pointCount: scala.Int = count / 2
    this.originalIndices.clear()
    this.originalIndices.ensureCapacity(pointCount)
    val originalIndicesArray: scala.Array[scala.Short] = this.originalIndices.items;
    { var i: scala.Short = 0.asInstanceOf[scala.Short]; while (i < pointCount) { {
      originalIndicesArray(i) = i
    }; i = i + 1 } }
    var lower: scala.Int = 0
    var upper: scala.Int = count - 1
    val stack: com.badlogic.gdx.utils.IntArray = this.quicksortStack
    stack.add(lower)
    stack.add(upper - 1)
    while (stack.size > 0) {
      upper = stack.pop()
      lower = stack.pop()
      if (upper <= lower) {
        /* continue */ ()
      } else ()
      var i: scala.Int = this.quicksortPartition(values, lower, upper, originalIndicesArray)
      if ((i - lower) > (upper - i)) {
        stack.add(lower)
        stack.add(i - 2)
      } else ()
      stack.add(i + 2)
      stack.add(upper)
      if ((upper - i) >= (i - lower)) {
        stack.add(lower)
        stack.add(i - 2)
      } else ()
    }
  }
  private def quicksortPartition(values: scala.Array[scala.Float], lower: scala.Int, upper: scala.Int, originalIndices: scala.Array[scala.Short]): scala.Int = {
    val value: scala.Float = values(lower)
    var up: scala.Int = upper
    var down: scala.Int = lower + 2
    var tempValue: scala.Float = 0.0f
    var tempIndex: scala.Short = 0
    while (down < up) {
      while ((down < up) && (values(down) <= value)) {
        down = down + 2
      }
      while (values(up) > value) {
        up = up - 2
      }
      if (down < up) {
        tempValue = values(down)
        values(down) = values(up)
        values(up) = tempValue
        tempValue = values(down + 1)
        values(down + 1) = values(up + 1)
        values(up + 1) = tempValue
        tempIndex = originalIndices(down / 2)
        originalIndices(down / 2) = originalIndices(up / 2)
        originalIndices(up / 2) = tempIndex
      } else ()
    }
    if (value > values(up)) {
      values(lower) = values(up)
      values(up) = value
      tempValue = values(lower + 1)
      values(lower + 1) = values(up + 1)
      values(up + 1) = tempValue
      tempIndex = originalIndices(lower / 2)
      originalIndices(lower / 2) = originalIndices(up / 2)
      originalIndices(up / 2) = tempIndex
    } else ()
    return up
  }
  def trim(triangles: com.badlogic.gdx.utils.ShortArray, points: scala.Array[scala.Float], hull: scala.Array[scala.Float], offset: scala.Int, count: scala.Int): scala.Unit = {
    val trianglesArray: scala.Array[scala.Short] = triangles.items;
    { var i: scala.Int = triangles.size - 1; while (i >= 0) { {
      val p1: scala.Int = trianglesArray(i - 2) * 2
      val p2: scala.Int = trianglesArray(i - 1) * 2
      val p3: scala.Int = trianglesArray(i) * 2
      com.badlogic.gdx.math.GeometryUtils.triangleCentroid(points(p1), points(p1 + 1), points(p2), points(p2 + 1), points(p3), points(p3 + 1), this.centroid)
      if (!com.badlogic.gdx.math.Intersector.isPointInPolygon(hull, offset, count, this.centroid.x, this.centroid.y)) {
        triangles.removeIndex(i)
        triangles.removeIndex(i - 1)
        triangles.removeIndex(i - 2)
      } else ()
    }; i = i - 3 } }
  }
}
object DelaunayTriangulator {
  private final val EPSILON: scala.Float = 1.0E-6f
  private final val INSIDE: scala.Int = 0
  private final val COMPLETE: scala.Int = 1
  private final val INCOMPLETE: scala.Int = 2
}