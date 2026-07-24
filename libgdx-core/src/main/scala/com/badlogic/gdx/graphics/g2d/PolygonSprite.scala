package com.badlogic.gdx.graphics.g2d

class PolygonSprite {
  var region: com.badlogic.gdx.graphics.g2d.PolygonRegion = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.PolygonRegion]
  private var x: scala.Float = 0.0f
  private var y: scala.Float = 0.0f
  private var width: scala.Float = 0.0f
  private var height: scala.Float = 0.0f
  private var scaleX: scala.Float = 1.0f
  private var scaleY: scala.Float = 1.0f
  private var rotation: scala.Float = 0.0f
  private var originX: scala.Float = 0.0f
  private var originY: scala.Float = 0.0f
  private var vertices: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  private var dirty: scala.Boolean = false
  private var bounds: com.badlogic.gdx.math.Rectangle = new com.badlogic.gdx.math.Rectangle()
  private final val color: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color(1.0f, 1.0f, 1.0f, 1.0f)
  def this(region: com.badlogic.gdx.graphics.g2d.PolygonRegion) = {
    this()
    this.setRegion(region)
    this.setSize(region.region.regionWidth, region.region.regionHeight)
    this.setOrigin(this.width / 2, this.height / 2)
  }
  def this(sprite: PolygonSprite) = {
    this()
    this.set(sprite)
  }
  def set(sprite: PolygonSprite): scala.Unit = {
    if (sprite == null) {
      throw new java.lang.IllegalArgumentException("sprite cannot be null.")
    } else ()
    this.setRegion(sprite.region)
    this.x = sprite.x
    this.y = sprite.y
    this.width = sprite.width
    this.height = sprite.height
    this.originX = sprite.originX
    this.originY = sprite.originY
    this.rotation = sprite.rotation
    this.scaleX = sprite.scaleX
    this.scaleY = sprite.scaleY
    this.color.set(sprite.color)
  }
  def setBounds(x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    this.x = x
    this.y = y
    this.width = width
    this.height = height
    this.dirty = true
  }
  def setSize(width: scala.Float, height: scala.Float): scala.Unit = {
    this.width = width
    this.height = height
    this.dirty = true
  }
  def setPosition(x: scala.Float, y: scala.Float): scala.Unit = {
    this.translate(x - this.x, y - this.y)
  }
  def setX(x: scala.Float): scala.Unit = {
    this.translateX(x - this.x)
  }
  def setY(y: scala.Float): scala.Unit = {
    this.translateY(y - this.y)
  }
  def translateX(xAmount: scala.Float): scala.Unit = {
    this.x = this.x + xAmount
    if (this.dirty) {
      return
    } else ()
    val vertices: scala.Array[scala.Float] = this.vertices;
    { var i: scala.Int = 0; while (i < vertices.length) { {
      vertices(i) = vertices(i) + xAmount
    }; i = i + com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE } }
  }
  def translateY(yAmount: scala.Float): scala.Unit = {
    this.y = this.y + yAmount
    if (this.dirty) {
      return
    } else ()
    val vertices: scala.Array[scala.Float] = this.vertices;
    { var i: scala.Int = 1; while (i < vertices.length) { {
      vertices(i) = vertices(i) + yAmount
    }; i = i + com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE } }
  }
  def translate(xAmount: scala.Float, yAmount: scala.Float): scala.Unit = {
    this.x = this.x + xAmount
    this.y = this.y + yAmount
    if (this.dirty) {
      return
    } else ()
    val vertices: scala.Array[scala.Float] = this.vertices;
    { var i: scala.Int = 0; while (i < vertices.length) { {
      vertices(i) = vertices(i) + xAmount
      vertices(i + 1) = vertices(i + 1) + yAmount
    }; i = i + com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE } }
  }
  def setColor(tint: com.badlogic.gdx.graphics.Color): scala.Unit = {
    this.color.set(tint)
    val color: scala.Float = tint.toFloatBits()
    val vertices: scala.Array[scala.Float] = this.vertices;
    { var i: scala.Int = 2; while (i < vertices.length) { {
      vertices(i) = color
    }; i = i + com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE } }
  }
  def setColor(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): scala.Unit = {
    this.color.set(r, g, b, a)
    val packedColor: scala.Float = this.color.toFloatBits()
    val vertices: scala.Array[scala.Float] = this.vertices;
    { var i: scala.Int = 2; while (i < vertices.length) { {
      vertices(i) = packedColor
    }; i = i + com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE } }
  }
  def setOrigin(originX: scala.Float, originY: scala.Float): scala.Unit = {
    this.originX = originX
    this.originY = originY
    this.dirty = true
  }
  def setRotation(degrees: scala.Float): scala.Unit = {
    this.rotation = degrees
    this.dirty = true
  }
  def rotate(degrees: scala.Float): scala.Unit = {
    this.rotation = this.rotation + degrees
    this.dirty = true
  }
  def setScale(scaleXY: scala.Float): scala.Unit = {
    this.scaleX = scaleXY
    this.scaleY = scaleXY
    this.dirty = true
  }
  def setScale(scaleX: scala.Float, scaleY: scala.Float): scala.Unit = {
    this.scaleX = scaleX
    this.scaleY = scaleY
    this.dirty = true
  }
  def scale(amount: scala.Float): scala.Unit = {
    this.scaleX = this.scaleX + amount
    this.scaleY = this.scaleY + amount
    this.dirty = true
  }
  def getVertices(): scala.Array[scala.Float] = {
    if (!this.dirty) {
      return this.vertices
    } else ()
    this.dirty = false
    val originX: scala.Float = this.originX
    val originY: scala.Float = this.originY
    val scaleX: scala.Float = this.scaleX
    val scaleY: scala.Float = this.scaleY
    val region: com.badlogic.gdx.graphics.g2d.PolygonRegion = this.region
    val vertices: scala.Array[scala.Float] = this.vertices
    val regionVertices: scala.Array[scala.Float] = region.vertices
    val worldOriginX: scala.Float = this.x + originX
    val worldOriginY: scala.Float = this.y + originY
    val sX: scala.Float = this.width / region.region.getRegionWidth()
    val sY: scala.Float = this.height / region.region.getRegionHeight()
    val cos: scala.Float = com.badlogic.gdx.math.MathUtils.cosDeg(this.rotation)
    val sin: scala.Float = com.badlogic.gdx.math.MathUtils.sinDeg(this.rotation)
    var fx: scala.Float = 0.0f
    var fy: scala.Float = 0.0f;
    { var i: scala.Int = 0; var v: scala.Int = 0; val n: scala.Int = regionVertices.length; while (i < n) { {
      fx = ((regionVertices(i) * sX) - originX) * scaleX
      fy = ((regionVertices(i + 1) * sY) - originY) * scaleY
      vertices(v) = ((cos * fx) - (sin * fy)) + worldOriginX
      vertices(v + 1) = ((sin * fx) + (cos * fy)) + worldOriginY
    }; i = i + 2; v = v + 5 } }
    return vertices
  }
  def getBoundingRectangle(): com.badlogic.gdx.math.Rectangle = {
    val vertices: scala.Array[scala.Float] = this.getVertices()
    var minx: scala.Float = vertices(0)
    var miny: scala.Float = vertices(1)
    var maxx: scala.Float = vertices(0)
    var maxy: scala.Float = vertices(1);
    { var i: scala.Int = 5; while (i < vertices.length) { {
      var x: scala.Float = vertices(i)
      var y: scala.Float = vertices(i + 1)
      minx = if (minx > x) x else minx
      maxx = if (maxx < x) x else maxx
      miny = if (miny > y) y else miny
      maxy = if (maxy < y) y else maxy
    }; i = i + 5 } }
    this.bounds.x = minx
    this.bounds.y = miny
    this.bounds.width = maxx - minx
    this.bounds.height = maxy - miny
    return this.bounds
  }
  def draw(spriteBatch: com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch): scala.Unit = {
    val region: com.badlogic.gdx.graphics.g2d.PolygonRegion = this.region
    spriteBatch.draw(region.region.texture, this.getVertices(), 0, this.vertices.length, region.triangles, 0, region.triangles.length)
  }
  def draw(spriteBatch: com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch, alphaModulation: scala.Float): scala.Unit = {
    val color: com.badlogic.gdx.graphics.Color = this.getColor()
    val oldAlpha: scala.Float = color.a
    color.a = color.a * alphaModulation
    this.setColor(color)
    this.draw(spriteBatch)
    color.a = oldAlpha
    this.setColor(color)
  }
  def getX(): scala.Float = {
    return this.x
  }
  def getY(): scala.Float = {
    return this.y
  }
  def getWidth(): scala.Float = {
    return this.width
  }
  def getHeight(): scala.Float = {
    return this.height
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
  def getColor(): com.badlogic.gdx.graphics.Color = {
    return this.color
  }
  def getPackedColor(): com.badlogic.gdx.graphics.Color = {
    com.badlogic.gdx.graphics.Color.abgr8888ToColor(this.color, this.vertices(2))
    return this.color
  }
  def setRegion(region: com.badlogic.gdx.graphics.g2d.PolygonRegion): scala.Unit = {
    this.region = region
    val regionVertices: scala.Array[scala.Float] = region.vertices
    val textureCoords: scala.Array[scala.Float] = region.textureCoords
    val verticesLength: scala.Int = (regionVertices.length / 2) * 5
    if ((this.vertices == null) || (this.vertices.length != verticesLength)) {
      this.vertices = new Array[scala.Float](verticesLength)
    } else ()
    val floatColor: scala.Float = this.color.toFloatBits()
    var vertices: scala.Array[scala.Float] = this.vertices;
    { var i: scala.Int = 0; var v: scala.Int = 2; while (v < verticesLength) { {
      vertices(v) = floatColor
      vertices(v + 1) = textureCoords(i)
      vertices(v + 2) = textureCoords(i + 1)
    }; i = i + 2; v = v + 5 } }
    this.dirty = true
  }
  def getRegion(): com.badlogic.gdx.graphics.g2d.PolygonRegion = {
    return this.region
  }
}