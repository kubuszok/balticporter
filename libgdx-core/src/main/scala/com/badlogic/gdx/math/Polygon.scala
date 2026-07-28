package com.badlogic.gdx.math

class Polygon extends com.badlogic.gdx.math.Shape2D {
  private var localVertices: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  private var worldVertices: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  private var x: scala.Float = 0.0f
  private var y: scala.Float = 0.0f
  private var originX: scala.Float = 0.0f
  private var originY: scala.Float = 0.0f
  private var rotation: scala.Float = 0.0f
  private var scaleX: scala.Float = 1
  private var scaleY: scala.Float = 1
  var dirty$field: scala.Boolean = true
  private var bounds: com.badlogic.gdx.math.Rectangle = null.asInstanceOf[com.badlogic.gdx.math.Rectangle]
  def this(vertices: scala.Array[scala.Float]) = {
    this()
    if (vertices.length < 6) {
      throw new java.lang.IllegalArgumentException("polygons must contain at least 3 points.")
    } else ()
    this.localVertices = vertices
  }
  this.localVertices = new scala.Array[scala.Float](0)
  def getVertices(): scala.Array[scala.Float] = {
    return this.localVertices
  }
  def getTransformedVertices(): scala.Array[scala.Float] = {
    if (!this.dirty$field) {
      return this.worldVertices
    } else ()
    this.dirty$field = false
    val localVertices: scala.Array[scala.Float] = this.localVertices
    if ((this.worldVertices == null) || (this.worldVertices.length != localVertices.length)) {
      this.worldVertices = new scala.Array[scala.Float](localVertices.length)
    } else ()
    var worldVertices: scala.Array[scala.Float] = this.worldVertices
    val positionX: scala.Float = this.x
    val positionY: scala.Float = this.y
    val originX: scala.Float = this.originX
    val originY: scala.Float = this.originY
    val scaleX: scala.Float = this.scaleX
    val scaleY: scala.Float = this.scaleY
    val scale: scala.Boolean = (scaleX != 1) || (scaleY != 1)
    val rotation: scala.Float = this.rotation
    val cos: scala.Float = com.badlogic.gdx.math.MathUtils.cosDeg(rotation)
    val sin: scala.Float = com.badlogic.gdx.math.MathUtils.sinDeg(rotation);
    { var i: scala.Int = 0; val n: scala.Int = localVertices.length; while (i < n) { {
      var x: scala.Float = localVertices(i) - originX
      var y: scala.Float = localVertices(i + 1) - originY
      if (scale) {
        x = x * scaleX
        y = y * scaleY
      } else ()
      if (rotation != 0) {
        val oldX: scala.Float = x
        x = (cos * x) - (sin * y)
        y = (sin * oldX) + (cos * y)
      } else ()
      worldVertices(i) = (positionX + x) + originX
      worldVertices(i + 1) = (positionY + y) + originY
    }; i = i + 2 } }
    return worldVertices
  }
  def setOrigin(originX: scala.Float, originY: scala.Float): scala.Unit = {
    this.originX = originX
    this.originY = originY
    this.dirty$field = true
  }
  def setPosition(x: scala.Float, y: scala.Float): scala.Unit = {
    this.x = x
    this.y = y
    this.dirty$field = true
  }
  def setVertices(vertices: scala.Array[scala.Float]): scala.Unit = {
    if (vertices.length < 6) {
      throw new java.lang.IllegalArgumentException("polygons must contain at least 3 points.")
    } else ()
    this.localVertices = vertices
    this.dirty$field = true
  }
  def setVertex(vertexNum: scala.Int, x: scala.Float, y: scala.Float): scala.Unit = {
    if ((vertexNum < 0) || (vertexNum > ((this.localVertices.length / 2) - 1))) {
      throw new java.lang.IllegalArgumentException(("the vertex " + vertexNum) + " doesn't exist")
    } else ()
    this.localVertices(2 * vertexNum) = x
    this.localVertices((2 * vertexNum) + 1) = y
    this.dirty$field = true
  }
  def translate(x: scala.Float, y: scala.Float): scala.Unit = {
    this.x = this.x + x
    this.y = this.y + y
    this.dirty$field = true
  }
  def setRotation(degrees: scala.Float): scala.Unit = {
    this.rotation = degrees
    this.dirty$field = true
  }
  def rotate(degrees: scala.Float): scala.Unit = {
    this.rotation = this.rotation + degrees
    this.dirty$field = true
  }
  def setScale(scaleX: scala.Float, scaleY: scala.Float): scala.Unit = {
    this.scaleX = scaleX
    this.scaleY = scaleY
    this.dirty$field = true
  }
  def scale(amount: scala.Float): scala.Unit = {
    this.scaleX = this.scaleX + amount
    this.scaleY = this.scaleY + amount
    this.dirty$field = true
  }
  def dirty(): scala.Unit = {
    this.dirty$field = true
  }
  def area(): scala.Float = {
    val vertices: scala.Array[scala.Float] = this.getTransformedVertices()
    return com.badlogic.gdx.math.GeometryUtils.polygonArea(vertices, 0, vertices.length)
  }
  def getVertexCount(): scala.Int = {
    return this.localVertices.length / 2
  }
  def getVertex(vertexNum: scala.Int, pos: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.math.Vector2 = {
    if ((vertexNum < 0) || (vertexNum > this.getVertexCount())) {
      throw new java.lang.IllegalArgumentException(("the vertex " + vertexNum) + " doesn't exist")
    } else ()
    val vertices: scala.Array[scala.Float] = this.getTransformedVertices()
    return pos.set(vertices(2 * vertexNum), vertices((2 * vertexNum) + 1))
  }
  def getCentroid(centroid: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.math.Vector2 = {
    val vertices: scala.Array[scala.Float] = this.getTransformedVertices()
    return com.badlogic.gdx.math.GeometryUtils.polygonCentroid(vertices, 0, vertices.length, centroid)
  }
  def getBoundingRectangle(): com.badlogic.gdx.math.Rectangle = {
    val vertices: scala.Array[scala.Float] = this.getTransformedVertices()
    var minX: scala.Float = vertices(0)
    var minY: scala.Float = vertices(1)
    var maxX: scala.Float = vertices(0)
    var maxY: scala.Float = vertices(1)
    val numFloats: scala.Int = vertices.length;
    { var i: scala.Int = 2; while (i < numFloats) { {
      minX = if (minX > vertices(i)) vertices(i) else minX
      minY = if (minY > vertices(i + 1)) vertices(i + 1) else minY
      maxX = if (maxX < vertices(i)) vertices(i) else maxX
      maxY = if (maxY < vertices(i + 1)) vertices(i + 1) else maxY
    }; i = i + 2 } }
    if (this.bounds == null) {
      this.bounds = new com.badlogic.gdx.math.Rectangle()
    } else ()
    this.bounds.x = minX
    this.bounds.y = minY
    this.bounds.width = maxX - minX
    this.bounds.height = maxY - minY
    return this.bounds
  }
  @java.lang.Override
  override def contains(x: scala.Float, y: scala.Float): scala.Boolean = {
    val vertices: scala.Array[scala.Float] = this.getTransformedVertices()
    val numFloats: scala.Int = vertices.length
    var intersects: scala.Int = 0;
    { var i: scala.Int = 0; while (i < numFloats) { {
      val x1: scala.Float = vertices(i)
      val y1: scala.Float = vertices(i + 1)
      val x2: scala.Float = vertices((i + 2) % numFloats)
      val y2: scala.Float = vertices((i + 3) % numFloats)
      if ((((y1 <= y) && (y < y2)) || ((y2 <= y) && (y < y1))) && (x < ((((x2 - x1) / (y2 - y1)) * (y - y1)) + x1))) {
        intersects = intersects + 1
      } else ()
    }; i = i + 2 } }
    return (intersects & 1) == 1
  }
  @java.lang.Override
  override def contains(point: com.badlogic.gdx.math.Vector2): scala.Boolean = {
    return this.contains(point.x, point.y)
  }
  def getX(): scala.Float = {
    return this.x
  }
  def getY(): scala.Float = {
    return this.y
  }
  def getOriginX(): scala.Float = {
    return this.originX
  }
  def getOriginY(): scala.Float = {
    return this.originY
  }
  def getRotation(): scala.Float = {
    return this.rotation
  }
  def getScaleX(): scala.Float = {
    return this.scaleX
  }
  def getScaleY(): scala.Float = {
    return this.scaleY
  }
  def resetTransformations(): scala.Unit = {
    this.scaleX = 1
    this.scaleY = 1
    this.originX = 0
    this.originY = 0
    this.x = 0
    this.y = 0
    this.rotation = 0
    this.dirty$field = true
  }
}