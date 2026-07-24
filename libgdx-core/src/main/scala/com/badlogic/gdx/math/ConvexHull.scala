package com.badlogic.gdx.math

class ConvexHull {
  private final val quicksortStack: com.badlogic.gdx.utils.IntArray = new com.badlogic.gdx.utils.IntArray()
  private var sortedPoints: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  private final val hull: com.badlogic.gdx.utils.FloatArray = new com.badlogic.gdx.utils.FloatArray()
  private final val indices: com.badlogic.gdx.utils.IntArray = new com.badlogic.gdx.utils.IntArray()
  private final val originalIndices: com.badlogic.gdx.utils.ShortArray = new com.badlogic.gdx.utils.ShortArray(false, 0)
  def computePolygon(points: com.badlogic.gdx.utils.FloatArray, sorted: scala.Boolean): com.badlogic.gdx.utils.FloatArray = {
    return this.computePolygon(points.items, 0, points.size, sorted)
  }
  def computePolygon(polygon: scala.Array[scala.Float], sorted: scala.Boolean): com.badlogic.gdx.utils.FloatArray = {
    return this.computePolygon(polygon, 0, polygon.length, sorted)
  }
  def computePolygon(points$arg: scala.Array[scala.Float], offset$arg: scala.Int, count: scala.Int, sorted: scala.Boolean): com.badlogic.gdx.utils.FloatArray = {
    var points: scala.Array[scala.Float] = points$arg
    var offset: scala.Int = offset$arg
    var `end`: scala.Int = offset + count
    if (!sorted) {
      if ((this.sortedPoints == null) || (this.sortedPoints.length < count)) {
        this.sortedPoints = new scala.Array[scala.Float](count)
      } else ()
      java.lang.System.arraycopy(points, offset, this.sortedPoints, 0, count)
      points = this.sortedPoints
      offset = 0
      `end` = count
      this.sort(points, count)
    } else ()
    val hull: com.badlogic.gdx.utils.FloatArray = this.hull
    hull.clear();
    { var i: scala.Int = offset; while (i < `end`) { {
      val x: scala.Float = points(i)
      val y: scala.Float = points(i + 1)
      while ((hull.size >= 4) && (this.ccw(x, y) <= 0)) {
        hull.size = hull.size - 2
      }
      hull.add(x)
      hull.add(y)
    }; i = i + 2 } };
    { var i: scala.Int = `end` - 4; val t: scala.Int = hull.size + 2; while (i >= offset) { {
      val x: scala.Float = points(i)
      val y: scala.Float = points(i + 1)
      while ((hull.size >= t) && (this.ccw(x, y) <= 0)) {
        hull.size = hull.size - 2
      }
      hull.add(x)
      hull.add(y)
    }; i = i - 2 } }
    return hull
  }
  def computeIndices(points: com.badlogic.gdx.utils.FloatArray, sorted: scala.Boolean, yDown: scala.Boolean): com.badlogic.gdx.utils.IntArray = {
    return this.computeIndices(points.items, 0, points.size, sorted, yDown)
  }
  def computeIndices(polygon: scala.Array[scala.Float], sorted: scala.Boolean, yDown: scala.Boolean): com.badlogic.gdx.utils.IntArray = {
    return this.computeIndices(polygon, 0, polygon.length, sorted, yDown)
  }
  def computeIndices(points$arg: scala.Array[scala.Float], offset$arg: scala.Int, count: scala.Int, sorted: scala.Boolean, yDown: scala.Boolean): com.badlogic.gdx.utils.IntArray = {
    var points: scala.Array[scala.Float] = points$arg
    var offset: scala.Int = offset$arg
    if (count > 32767) {
      throw new java.lang.IllegalArgumentException("count must be <= " + 32767)
    } else ()
    var `end`: scala.Int = offset + count
    if (!sorted) {
      if ((this.sortedPoints == null) || (this.sortedPoints.length < count)) {
        this.sortedPoints = new scala.Array[scala.Float](count)
      } else ()
      java.lang.System.arraycopy(points, offset, this.sortedPoints, 0, count)
      points = this.sortedPoints
      offset = 0
      `end` = count
      this.sortWithIndices(points, count, yDown)
    } else ()
    val indices: com.badlogic.gdx.utils.IntArray = this.indices
    indices.clear()
    val hull: com.badlogic.gdx.utils.FloatArray = this.hull
    hull.clear();
    { var i: scala.Int = offset; var index: scala.Int = i / 2; while (i < `end`) { {
      val x: scala.Float = points(i)
      val y: scala.Float = points(i + 1)
      while ((hull.size >= 4) && (this.ccw(x, y) <= 0)) {
        hull.size = hull.size - 2
        indices.size = indices.size - 1
      }
      hull.add(x)
      hull.add(y)
      indices.add(index)
    }; i = i + 2; index = index + 1 } };
    { var i: scala.Int = `end` - 4; var index: scala.Int = i / 2; val t: scala.Int = hull.size + 2; while (i >= offset) { {
      val x: scala.Float = points(i)
      val y: scala.Float = points(i + 1)
      while ((hull.size >= t) && (this.ccw(x, y) <= 0)) {
        hull.size = hull.size - 2
        indices.size = indices.size - 1
      }
      hull.add(x)
      hull.add(y)
      indices.add(index)
    }; i = i - 2; index = index - 1 } }
    if (!sorted) {
      val originalIndicesArray: scala.Array[scala.Short] = this.originalIndices.items
      val indicesArray: scala.Array[scala.Int] = indices.items;
      { var i: scala.Int = 0; val n: scala.Int = indices.size; while (i < n) { {
        indicesArray(i) = originalIndicesArray(indicesArray(i))
      }; i = i + 1 } }
    } else ()
    return indices
  }
  private def ccw(p3x: scala.Float, p3y: scala.Float): scala.Float = {
    val hull: com.badlogic.gdx.utils.FloatArray = this.hull
    val size: scala.Int = hull.size
    val p1x: scala.Float = hull.get(size - 4)
    val p1y: scala.Float = hull.get(size - 3)
    val p2x: scala.Float = hull.get(size - 2)
    val p2y: scala.Float = hull.peek()
    return ((p2x - p1x) * (p3y - p1y)) - ((p2y - p1y) * (p3x - p1x))
  }
  private def sort(values: scala.Array[scala.Float], count: scala.Int): scala.Unit = {
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
      val i: scala.Int = this.quicksortPartition(values, lower, upper)
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
  private def quicksortPartition(values: scala.Array[scala.Float], lower: scala.Int, upper: scala.Int): scala.Int = {
    val x: scala.Float = values(lower)
    val y: scala.Float = values(lower + 1)
    var up: scala.Int = upper
    var down: scala.Int = lower
    var temp: scala.Float = 0.0f
    while (down < up) {
      while ((down < up) && (values(down) <= x)) {
        down = down + 2
      }
      while ((values(up) > x) || ((values(up) == x) && (values(up + 1) < y))) {
        up = up - 2
      }
      if (down < up) {
        temp = values(down)
        values(down) = values(up)
        values(up) = temp
        temp = values(down + 1)
        values(down + 1) = values(up + 1)
        values(up + 1) = temp
      } else ()
    }
    if ((x > values(up)) || ((x == values(up)) && (y < values(up + 1)))) {
      values(lower) = values(up)
      values(up) = x
      values(lower + 1) = values(up + 1)
      values(up + 1) = y
    } else ()
    return up
  }
  private def sortWithIndices(values: scala.Array[scala.Float], count: scala.Int, yDown: scala.Boolean): scala.Unit = {
    val pointCount: scala.Int = count / 2
    this.originalIndices.clear()
    this.originalIndices.ensureCapacity(pointCount)
    val originalIndicesArray: scala.Array[scala.Short] = this.originalIndices.items;
    { var i: scala.Short = 0.asInstanceOf[scala.Short]; while (i < pointCount) { {
      originalIndicesArray(i) = i
    }; i = (i + 1).asInstanceOf[scala.Short] } }
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
      var i: scala.Int = this.quicksortPartitionWithIndices(values, lower, upper, yDown, originalIndicesArray)
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
  private def quicksortPartitionWithIndices(values: scala.Array[scala.Float], lower: scala.Int, upper: scala.Int, yDown: scala.Boolean, originalIndices: scala.Array[scala.Short]): scala.Int = {
    val x: scala.Float = values(lower)
    val y: scala.Float = values(lower + 1)
    var up: scala.Int = upper
    var down: scala.Int = lower
    var temp: scala.Float = 0.0f
    var tempIndex: scala.Short = 0
    while (down < up) {
      while ((down < up) && (values(down) <= x)) {
        down = down + 2
      }
      if (yDown) {
        while ((values(up) > x) || ((values(up) == x) && (values(up + 1) < y))) {
          up = up - 2
        }
      } else {
        while ((values(up) > x) || ((values(up) == x) && (values(up + 1) > y))) {
          up = up - 2
        }
      }
      if (down < up) {
        temp = values(down)
        values(down) = values(up)
        values(up) = temp
        temp = values(down + 1)
        values(down + 1) = values(up + 1)
        values(up + 1) = temp
        tempIndex = originalIndices(down / 2)
        originalIndices(down / 2) = originalIndices(up / 2)
        originalIndices(up / 2) = tempIndex
      } else ()
    }
    if ((x > values(up)) || ((x == values(up)) && (if (yDown) y < values(up + 1) else y > values(up + 1)))) {
      values(lower) = values(up)
      values(up) = x
      values(lower + 1) = values(up + 1)
      values(up + 1) = y
      tempIndex = originalIndices(lower / 2)
      originalIndices(lower / 2) = originalIndices(up / 2)
      originalIndices(up / 2) = tempIndex
    } else ()
    return up
  }
}