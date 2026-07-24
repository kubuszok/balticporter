package com.badlogic.gdx.math

class EarClippingTriangulator {
  private final val indicesArray: com.badlogic.gdx.utils.ShortArray = new com.badlogic.gdx.utils.ShortArray()
  private var indices: scala.Array[scala.Short] = null.asInstanceOf[scala.Array[scala.Short]]
  private var vertices: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  private var vertexCount: scala.Int = 0
  private final val vertexTypes: com.badlogic.gdx.utils.IntArray = new com.badlogic.gdx.utils.IntArray()
  private final val triangles: com.badlogic.gdx.utils.ShortArray = new com.badlogic.gdx.utils.ShortArray()
  def computeTriangles(vertices: com.badlogic.gdx.utils.FloatArray): com.badlogic.gdx.utils.ShortArray = {
    return this.computeTriangles(vertices.items, 0, vertices.size)
  }
  def computeTriangles(vertices: scala.Array[scala.Float]): com.badlogic.gdx.utils.ShortArray = {
    return this.computeTriangles(vertices, 0, vertices.length)
  }
  def computeTriangles(vertices: scala.Array[scala.Float], offset: scala.Int, count: scala.Int): com.badlogic.gdx.utils.ShortArray = {
    this.vertices = vertices
    var vertexCount: scala.Int = {
      this.vertexCount = count / 2
      this.vertexCount
    }
    val vertexOffset: scala.Int = offset / 2
    val indicesArray: com.badlogic.gdx.utils.ShortArray = this.indicesArray
    indicesArray.clear()
    indicesArray.ensureCapacity(vertexCount)
    indicesArray.size = vertexCount
    var indices: scala.Array[scala.Short] = {
      this.indices = indicesArray.items
      this.indices
    }
    if (com.badlogic.gdx.math.GeometryUtils.isClockwise(vertices, offset, count)) {
      { var i: scala.Short = 0.asInstanceOf[scala.Short]; while (i < vertexCount) { {
        indices(i) = (vertexOffset + i).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
      }; i = i + 1 } }
    } else {
      { var i: scala.Int = 0; val n: scala.Int = vertexCount - 1; while (i < vertexCount) { {
        indices(i) = ((vertexOffset + n) - i).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
      }; i = i + 1 } }
    }
    val vertexTypes: com.badlogic.gdx.utils.IntArray = this.vertexTypes
    vertexTypes.clear()
    vertexTypes.ensureCapacity(vertexCount);
    { var i: scala.Int = 0; val n: scala.Int = vertexCount; while (i < n) { {
      vertexTypes.add(this.classifyVertex(i))
    }; i = i + 1 } }
    val triangles: com.badlogic.gdx.utils.ShortArray = this.triangles
    triangles.clear()
    triangles.ensureCapacity(java.lang.Math.max(0, vertexCount - 2) * 3)
    this.triangulate()
    return triangles
  }
  private def triangulate(): scala.Unit = {
    val vertexTypes: scala.Array[scala.Int] = this.vertexTypes.items
    while (this.vertexCount > 3) {
      val earTipIndex: scala.Int = this.findEarTip()
      this.cutEarTip(earTipIndex)
      val previousIndex: scala.Int = this.previousIndex(earTipIndex)
      val nextIndex: scala.Int = if (earTipIndex == this.vertexCount) 0 else earTipIndex
      vertexTypes(previousIndex) = this.classifyVertex(previousIndex)
      vertexTypes(nextIndex) = this.classifyVertex(nextIndex)
    }
    if (this.vertexCount == 3) {
      val triangles: com.badlogic.gdx.utils.ShortArray = this.triangles
      val indices: scala.Array[scala.Short] = this.indices
      triangles.add(indices(0))
      triangles.add(indices(1))
      triangles.add(indices(2))
    } else ()
  }
  private def classifyVertex(index: scala.Int): scala.Int = {
    val indices: scala.Array[scala.Short] = this.indices
    val previous: scala.Int = indices(this.previousIndex(index)) * 2
    val current: scala.Int = indices(index) * 2
    val next: scala.Int = indices(this.nextIndex(index)) * 2
    val vertices: scala.Array[scala.Float] = this.vertices
    return EarClippingTriangulator.computeSpannedAreaSign(vertices(previous), vertices(previous + 1), vertices(current), vertices(current + 1), vertices(next), vertices(next + 1))
  }
  private def findEarTip(): scala.Int = {
    val vertexCount: scala.Int = this.vertexCount;
    { var i: scala.Int = 0; while (i < vertexCount) { {
      if (this.isEarTip(i)) {
        return i
      } else ()
    }; i = i + 1 } }
    val vertexTypes: scala.Array[scala.Int] = this.vertexTypes.items;
    { var i: scala.Int = 0; while (i < vertexCount) { {
      if (vertexTypes(i) != EarClippingTriangulator.CONCAVE) {
        return i
      } else ()
    }; i = i + 1 } }
    return 0
  }
  private def isEarTip(earTipIndex: scala.Int): scala.Boolean = {
    val vertexTypes: scala.Array[scala.Int] = this.vertexTypes.items
    if (vertexTypes(earTipIndex) == EarClippingTriangulator.CONCAVE) {
      return false
    } else ()
    val previousIndex: scala.Int = this.previousIndex(earTipIndex)
    val nextIndex: scala.Int = this.nextIndex(earTipIndex)
    val indices: scala.Array[scala.Short] = this.indices
    val p1: scala.Int = indices(previousIndex) * 2
    val p2: scala.Int = indices(earTipIndex) * 2
    val p3: scala.Int = indices(nextIndex) * 2
    val vertices: scala.Array[scala.Float] = this.vertices
    val p1x: scala.Float = vertices(p1)
    val p1y: scala.Float = vertices(p1 + 1)
    val p2x: scala.Float = vertices(p2)
    val p2y: scala.Float = vertices(p2 + 1)
    val p3x: scala.Float = vertices(p3)
    val p3y: scala.Float = vertices(p3 + 1);
    { var i: scala.Int = this.nextIndex(nextIndex); while (i != previousIndex) { {
      if (vertexTypes(i) != EarClippingTriangulator.CONVEX) {
        val v: scala.Int = indices(i) * 2
        val vx: scala.Float = vertices(v)
        val vy: scala.Float = vertices(v + 1)
        if (EarClippingTriangulator.computeSpannedAreaSign(p3x, p3y, p1x, p1y, vx, vy) >= 0) {
          if (EarClippingTriangulator.computeSpannedAreaSign(p1x, p1y, p2x, p2y, vx, vy) >= 0) {
            if (EarClippingTriangulator.computeSpannedAreaSign(p2x, p2y, p3x, p3y, vx, vy) >= 0) {
              return false
            } else ()
          } else ()
        } else ()
      } else ()
    }; i = this.nextIndex(i) } }
    return true
  }
  private def cutEarTip(earTipIndex: scala.Int): scala.Unit = {
    val indices: scala.Array[scala.Short] = this.indices
    val triangles: com.badlogic.gdx.utils.ShortArray = this.triangles
    triangles.add(indices(this.previousIndex(earTipIndex)))
    triangles.add(indices(earTipIndex))
    triangles.add(indices(this.nextIndex(earTipIndex)))
    this.indicesArray.removeIndex(earTipIndex)
    this.vertexTypes.removeIndex(earTipIndex)
    this.vertexCount = this.vertexCount - 1
  }
  private def previousIndex(index: scala.Int): scala.Int = {
    return (if (index == 0) this.vertexCount else index) - 1
  }
  private def nextIndex(index: scala.Int): scala.Int = {
    return (index + 1) % this.vertexCount
  }
}
object EarClippingTriangulator {
  private final val CONCAVE: scala.Int = -1
  private final val CONVEX: scala.Int = 1
  private def computeSpannedAreaSign(p1x: scala.Float, p1y: scala.Float, p2x: scala.Float, p2y: scala.Float, p3x: scala.Float, p3y: scala.Float): scala.Int = {
    var area: scala.Float = p1x * (p3y - p2y)
    area = area + (p2x * (p1y - p3y))
    area = area + (p3x * (p2y - p1y))
    return java.lang.Math.signum(area).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
  }
}