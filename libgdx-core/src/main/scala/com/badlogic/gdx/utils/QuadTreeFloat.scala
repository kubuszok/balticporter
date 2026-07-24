package com.badlogic.gdx.utils

class QuadTreeFloat extends com.badlogic.gdx.utils.Pool#Poolable {
  var maxValues: scala.Int = 0
  var maxDepth: scala.Int = 0
  var x: scala.Float = 0.0f
  var y: scala.Float = 0.0f
  var width: scala.Float = 0.0f
  var height: scala.Float = 0.0f
  var depth: scala.Int = 0
  var nw: QuadTreeFloat = null.asInstanceOf[QuadTreeFloat]
  var ne: QuadTreeFloat = null.asInstanceOf[QuadTreeFloat]
  var sw: QuadTreeFloat = null.asInstanceOf[QuadTreeFloat]
  var se: QuadTreeFloat = null.asInstanceOf[QuadTreeFloat]
  var values: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  var count: scala.Int = 0
  def this(maxValues: scala.Int, maxDepth: scala.Int) = {
    this()
    this.maxValues = maxValues * 3
    this.maxDepth = maxDepth
    this.values = new Array[scala.Float](this.maxValues)
  }
  def setBounds(x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    this.x = x
    this.y = y
    this.width = width
    this.height = height
  }
  def add(value: scala.Float, valueX: scala.Float, valueY: scala.Float): scala.Unit = {
    var count: scala.Int = this.count
    if (count == (-1)) {
      this.addToChild(value, valueX, valueY)
      return
    } else ()
    if (this.depth < this.maxDepth) {
      if (count == this.maxValues) {
        this.split(value, valueX, valueY)
        return
      } else ()
    } else {
      if (count == this.values.length) {
        this.values = java.util.Arrays.copyOf(this.values, this.growValues())
      } else ()
    }
    this.values(count) = value
    this.values(count + 1) = valueX
    this.values(count + 2) = valueY
    this.count = this.count + 3
  }
  private def split(value: scala.Float, valueX: scala.Float, valueY: scala.Float): scala.Unit = {
    val values: scala.Array[scala.Float] = this.values
    { var i: scala.Int = 0; while (i < this.maxValues) { {
      this.addToChild(values(i), values(i + 1), values(i + 2))
    }; i = i + 3 } }
    this.count = -1
    this.addToChild(value, valueX, valueY)
  }
  private def addToChild(value: scala.Float, valueX: scala.Float, valueY: scala.Float): scala.Unit = {
    var child: QuadTreeFloat = null.asInstanceOf[QuadTreeFloat]
    val halfWidth: scala.Float = this.width / 2
    val halfHeight: scala.Float = this.height / 2
    if (valueX < (this.x + halfWidth)) {
      if (valueY < (this.y + halfHeight)) {
        child = if (this.sw != null) this.sw else {
          this.sw = this.obtainChild(this.x, this.y, halfWidth, halfHeight, this.depth + 1)
          this.sw
        }
      } else {
        child = if (this.nw != null) this.nw else {
          this.nw = this.obtainChild(this.x, this.y + halfHeight, halfWidth, halfHeight, this.depth + 1)
          this.nw
        }
      }
    } else {
      if (valueY < (this.y + halfHeight)) {
        child = if (this.se != null) this.se else {
          this.se = this.obtainChild(this.x + halfWidth, this.y, halfWidth, halfHeight, this.depth + 1)
          this.se
        }
      } else {
        child = if (this.ne != null) this.ne else {
          this.ne = this.obtainChild(this.x + halfWidth, this.y + halfHeight, halfWidth, halfHeight, this.depth + 1)
          this.ne
        }
      }
    }
    child.add(value, valueX, valueY)
  }
  private def obtainChild(x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float, depth: scala.Int): QuadTreeFloat = {
    val child: QuadTreeFloat = QuadTreeFloat.pool.obtain()
    child.x = x
    child.y = y
    child.width = width
    child.height = height
    child.depth = depth
    return child
  }
  protected def growValues(): scala.Int = {
    return this.count + (10 * 3)
  }
  def query(centerX: scala.Float, centerY: scala.Float, radius: scala.Float, results: com.badlogic.gdx.utils.FloatArray): scala.Unit = {
    this.query(centerX, centerY, radius * radius, centerX - radius, centerY - radius, radius * 2, results)
  }
  private def query(centerX: scala.Float, centerY: scala.Float, radiusSqr: scala.Float, rectX: scala.Float, rectY: scala.Float, rectSize: scala.Float, results: com.badlogic.gdx.utils.FloatArray): scala.Unit = {
    if (!((((this.x < (rectX + rectSize)) && ((this.x + this.width) > rectX)) && (this.y < (rectY + rectSize))) && ((this.y + this.height) > rectY))) {
      return
    } else ()
    val count: scala.Int = this.count
    if (count != (-1)) {
      val values: scala.Array[scala.Float] = this.values
      { var i: scala.Int = 1; while (i < count) { {
        val px: scala.Float = values(i)
        val py: scala.Float = values(i + 1)
        val dx: scala.Float = px - centerX
        val dy: scala.Float = py - centerY
        val d: scala.Float = (dx * dx) + (dy * dy)
        if (d <= radiusSqr) {
          results.add(values(i - 1))
          results.add(px)
          results.add(py)
          results.add(d)
        } else ()
      }; i = i + 3 } }
    } else {
      if (this.nw != null) {
        this.nw.query(centerX, centerY, radiusSqr, rectX, rectY, rectSize, results)
      } else ()
      if (this.sw != null) {
        this.sw.query(centerX, centerY, radiusSqr, rectX, rectY, rectSize, results)
      } else ()
      if (this.ne != null) {
        this.ne.query(centerX, centerY, radiusSqr, rectX, rectY, rectSize, results)
      } else ()
      if (this.se != null) {
        this.se.query(centerX, centerY, radiusSqr, rectX, rectY, rectSize, results)
      } else ()
    }
  }
  def query(rect: com.badlogic.gdx.math.Rectangle, results: com.badlogic.gdx.utils.FloatArray): scala.Unit = {
    if ((((this.x >= (rect.x + rect.width)) || ((this.x + this.width) <= rect.x)) || (this.y >= (rect.y + rect.height))) || ((this.y + this.height) <= rect.y)) {
      return
    } else ()
    val count: scala.Int = this.count
    if (count != (-1)) {
      val values: scala.Array[scala.Float] = this.values
      { var i: scala.Int = 1; while (i < count) { {
        val px: scala.Float = values(i)
        val py: scala.Float = values(i + 1)
        if (rect.contains(px, py)) {
          results.add(values(i - 1))
          results.add(px)
          results.add(py)
        } else ()
      }; i = i + 3 } }
    } else {
      if (this.nw != null) {
        this.nw.query(rect, results)
      } else ()
      if (this.sw != null) {
        this.sw.query(rect, results)
      } else ()
      if (this.ne != null) {
        this.ne.query(rect, results)
      } else ()
      if (this.se != null) {
        this.se.query(rect, results)
      } else ()
    }
  }
  def nearest(x: scala.Float, y: scala.Float, result: com.badlogic.gdx.utils.FloatArray): scala.Boolean = {
    result.clear()
    result.add(0)
    result.add(0)
    result.add(0)
    result.add(java.lang.Float.POSITIVE_INFINITY)
    this.findNearestInternal(x, y, result)
    var nearValue: scala.Float = result.first()
    var nearX: scala.Float = result.get(1)
    var nearY: scala.Float = result.get(2)
    var nearDist: scala.Float = result.get(3)
    val found: scala.Boolean = nearDist != java.lang.Float.POSITIVE_INFINITY
    if (!found) {
      nearDist = java.lang.Math.max(this.width, this.height)
      nearDist = nearDist * nearDist
    } else ()
    result.clear()
    this.query(x, y, java.lang.Math.sqrt(nearDist).asInstanceOf[scala.Float], result)
    { var i: scala.Int = 3; val n: scala.Int = result.size; while (i < n) { {
      val dist: scala.Float = result.get(i)
      if (dist < nearDist) {
        nearDist = dist
        nearValue = result.get(i - 3)
        nearX = result.get(i - 2)
        nearY = result.get(i - 1)
      } else ()
    }; i = i + 4 } }
    if ((!found) && result.isEmpty()) {
      return false
    } else ()
    result.clear()
    result.add(nearValue)
    result.add(nearX)
    result.add(nearY)
    result.add(nearDist)
    return true
  }
  private def findNearestInternal(x: scala.Float, y: scala.Float, result: com.badlogic.gdx.utils.FloatArray): scala.Unit = {
    if (!((((this.x < x) && ((this.x + this.width) > x)) && (this.y < y)) && ((this.y + this.height) > y))) {
      return
    } else ()
    val count: scala.Int = this.count
    if (count != (-1)) {
      var nearValue: scala.Float = result.first()
      var nearX: scala.Float = result.get(1)
      var nearY: scala.Float = result.get(2)
      var nearDist: scala.Float = result.get(3)
      val values: scala.Array[scala.Float] = this.values
      { var i: scala.Int = 1; while (i < count) { {
        val px: scala.Float = values(i)
        val py: scala.Float = values(i + 1)
        val dx: scala.Float = px - x
        val dy: scala.Float = py - y
        val dist: scala.Float = (dx * dx) + (dy * dy)
        if (dist < nearDist) {
          nearDist = dist
          nearValue = values(i - 1)
          nearX = px
          nearY = py
        } else ()
      }; i = i + 3 } }
      result.set(0, nearValue)
      result.set(1, nearX)
      result.set(2, nearY)
      result.set(3, nearDist)
    } else {
      if (this.nw != null) {
        this.nw.findNearestInternal(x, y, result)
      } else ()
      if (this.sw != null) {
        this.sw.findNearestInternal(x, y, result)
      } else ()
      if (this.ne != null) {
        this.ne.findNearestInternal(x, y, result)
      } else ()
      if (this.se != null) {
        this.se.findNearestInternal(x, y, result)
      } else ()
    }
  }
  def reset(): scala.Unit = {
    if (this.count == (-1)) {
      if (this.nw != null) {
        QuadTreeFloat.pool.free(this.nw)
        this.nw = null
      } else ()
      if (this.sw != null) {
        QuadTreeFloat.pool.free(this.sw)
        this.sw = null
      } else ()
      if (this.ne != null) {
        QuadTreeFloat.pool.free(this.ne)
        this.ne = null
      } else ()
      if (this.se != null) {
        QuadTreeFloat.pool.free(this.se)
        this.se = null
      } else ()
    } else ()
    this.count = 0
    if (this.values.length > this.maxValues) {
      this.values = new Array[scala.Float](this.maxValues)
    } else ()
  }
}
object QuadTreeFloat {
  final val VALUE: scala.Int = 0
  final val X: scala.Int = 1
  final val Y: scala.Int = 2
  final val DISTSQR: scala.Int = 3
  private final val pool: com.badlogic.gdx.utils.Pool[QuadTreeFloat] = new com.badlogic.gdx.utils.Pool(128, 4096)
}