package com.badlogic.gdx.graphics.g2d

class Sprite extends com.badlogic.gdx.graphics.g2d.TextureRegion {
  final val vertices: scala.Array[scala.Float] = new scala.Array[scala.Float](Sprite.SPRITE_SIZE)
  private final val color: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color(1, 1, 1, 1)
  private var packedColor: scala.Float = com.badlogic.gdx.graphics.Color.WHITE_FLOAT_BITS
  private var x: scala.Float = 0.0f
  private var y: scala.Float = 0.0f
  var width: scala.Float = 0.0f
  var height: scala.Float = 0.0f
  private var originX: scala.Float = 0.0f
  private var originY: scala.Float = 0.0f
  private var rotation: scala.Float = 0.0f
  private var scaleX: scala.Float = 1
  private var scaleY: scala.Float = 1
  private var dirty: scala.Boolean = true
  private var bounds: com.badlogic.gdx.math.Rectangle = null.asInstanceOf[com.badlogic.gdx.math.Rectangle]
  def this(texture: com.badlogic.gdx.graphics.Texture, srcX: scala.Int, srcY: scala.Int, srcWidth: scala.Int, srcHeight: scala.Int) = {
    this()
    if (texture == null) {
      throw new java.lang.IllegalArgumentException("texture cannot be null.")
    } else ()
    this.texture = texture
    (this.setRegion: (scala.Int, scala.Int, scala.Int, scala.Int) => scala.Unit)(srcX, srcY, srcWidth, srcHeight)
    this.setColor(1, 1, 1, 1)
    this.setSize(java.lang.Math.abs(srcWidth), java.lang.Math.abs(srcHeight))
    this.setOrigin(this.width / 2, this.height / 2)
  }
  def this(texture: com.badlogic.gdx.graphics.Texture) = {
    this(texture, 0, 0, texture.getWidth(), texture.getHeight())
  }
  def this(texture: com.badlogic.gdx.graphics.Texture, srcWidth: scala.Int, srcHeight: scala.Int) = {
    this(texture, 0, 0, srcWidth, srcHeight)
  }
  def this(region: com.badlogic.gdx.graphics.g2d.TextureRegion) = {
    this()
    this.setRegion(region)
    this.setColor(1, 1, 1, 1)
    this.setSize(region.getRegionWidth(), region.getRegionHeight())
    this.setOrigin(this.width / 2, this.height / 2)
  }
  def this(region: com.badlogic.gdx.graphics.g2d.TextureRegion, srcX: scala.Int, srcY: scala.Int, srcWidth: scala.Int, srcHeight: scala.Int) = {
    this()
    this.setRegion(region, srcX, srcY, srcWidth, srcHeight)
    this.setColor(1, 1, 1, 1)
    this.setSize(java.lang.Math.abs(srcWidth), java.lang.Math.abs(srcHeight))
    this.setOrigin(this.width / 2, this.height / 2)
  }
  def this(sprite: Sprite) = {
    this()
    this.set(sprite)
  }
  this.setColor(1, 1, 1, 1)
  def set(sprite: Sprite): scala.Unit = {
    if (sprite == null) {
      throw new java.lang.IllegalArgumentException("sprite cannot be null.")
    } else ()
    java.lang.System.arraycopy(sprite.vertices, 0, this.vertices, 0, Sprite.SPRITE_SIZE)
    texture = sprite.texture
    u = sprite.u
    v = sprite.v
    u2 = sprite.u2
    v2 = sprite.v2
    this.x = sprite.x
    this.y = sprite.y
    this.width = sprite.width
    this.height = sprite.height
    regionWidth = sprite.regionWidth
    regionHeight = sprite.regionHeight
    this.originX = sprite.originX
    this.originY = sprite.originY
    this.rotation = sprite.rotation
    this.scaleX = sprite.scaleX
    this.scaleY = sprite.scaleY
    this.color.set(sprite.color)
    this.dirty = sprite.dirty
  }
  def setBounds(x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    this.x = x
    this.y = y
    this.width = width
    this.height = height
    if (this.dirty) {
      return
    } else ()
    if (((this.rotation != 0) || (this.scaleX != 1)) || (this.scaleY != 1)) {
      this.dirty = true
      return
    } else ()
    val x2: scala.Float = x + width
    val y2: scala.Float = y + height
    val vertices: scala.Array[scala.Float] = this.vertices
    vertices(com.badlogic.gdx.graphics.g2d.Batch.X1) = x
    vertices(com.badlogic.gdx.graphics.g2d.Batch.Y1) = y
    vertices(com.badlogic.gdx.graphics.g2d.Batch.X2) = x
    vertices(com.badlogic.gdx.graphics.g2d.Batch.Y2) = y2
    vertices(com.badlogic.gdx.graphics.g2d.Batch.X3) = x2
    vertices(com.badlogic.gdx.graphics.g2d.Batch.Y3) = y2
    vertices(com.badlogic.gdx.graphics.g2d.Batch.X4) = x2
    vertices(com.badlogic.gdx.graphics.g2d.Batch.Y4) = y
  }
  def setSize(width: scala.Float, height: scala.Float): scala.Unit = {
    this.width = width
    this.height = height
    if (this.dirty) {
      return
    } else ()
    if (((this.rotation != 0) || (this.scaleX != 1)) || (this.scaleY != 1)) {
      this.dirty = true
      return
    } else ()
    val x2: scala.Float = this.x + width
    val y2: scala.Float = this.y + height
    val vertices: scala.Array[scala.Float] = this.vertices
    vertices(com.badlogic.gdx.graphics.g2d.Batch.X1) = this.x
    vertices(com.badlogic.gdx.graphics.g2d.Batch.Y1) = this.y
    vertices(com.badlogic.gdx.graphics.g2d.Batch.X2) = this.x
    vertices(com.badlogic.gdx.graphics.g2d.Batch.Y2) = y2
    vertices(com.badlogic.gdx.graphics.g2d.Batch.X3) = x2
    vertices(com.badlogic.gdx.graphics.g2d.Batch.Y3) = y2
    vertices(com.badlogic.gdx.graphics.g2d.Batch.X4) = x2
    vertices(com.badlogic.gdx.graphics.g2d.Batch.Y4) = this.y
  }
  def setPosition(x: scala.Float, y: scala.Float): scala.Unit = {
    this.x = x
    this.y = y
    if (this.dirty) {
      return
    } else ()
    if (((this.rotation != 0) || (this.scaleX != 1)) || (this.scaleY != 1)) {
      this.dirty = true
      return
    } else ()
    val x2: scala.Float = x + this.width
    val y2: scala.Float = y + this.height
    val vertices: scala.Array[scala.Float] = this.vertices
    vertices(com.badlogic.gdx.graphics.g2d.Batch.X1) = x
    vertices(com.badlogic.gdx.graphics.g2d.Batch.Y1) = y
    vertices(com.badlogic.gdx.graphics.g2d.Batch.X2) = x
    vertices(com.badlogic.gdx.graphics.g2d.Batch.Y2) = y2
    vertices(com.badlogic.gdx.graphics.g2d.Batch.X3) = x2
    vertices(com.badlogic.gdx.graphics.g2d.Batch.Y3) = y2
    vertices(com.badlogic.gdx.graphics.g2d.Batch.X4) = x2
    vertices(com.badlogic.gdx.graphics.g2d.Batch.Y4) = y
  }
  def setOriginBasedPosition(x: scala.Float, y: scala.Float): scala.Unit = {
    this.setPosition(x - this.originX, y - this.originY)
  }
  def setX(x: scala.Float): scala.Unit = {
    this.x = x
    if (this.dirty) {
      return
    } else ()
    if (((this.rotation != 0) || (this.scaleX != 1)) || (this.scaleY != 1)) {
      this.dirty = true
      return
    } else ()
    val x2: scala.Float = x + this.width
    val vertices: scala.Array[scala.Float] = this.vertices
    vertices(com.badlogic.gdx.graphics.g2d.Batch.X1) = x
    vertices(com.badlogic.gdx.graphics.g2d.Batch.X2) = x
    vertices(com.badlogic.gdx.graphics.g2d.Batch.X3) = x2
    vertices(com.badlogic.gdx.graphics.g2d.Batch.X4) = x2
  }
  def setY(y: scala.Float): scala.Unit = {
    this.y = y
    if (this.dirty) {
      return
    } else ()
    if (((this.rotation != 0) || (this.scaleX != 1)) || (this.scaleY != 1)) {
      this.dirty = true
      return
    } else ()
    val y2: scala.Float = y + this.height
    val vertices: scala.Array[scala.Float] = this.vertices
    vertices(com.badlogic.gdx.graphics.g2d.Batch.Y1) = y
    vertices(com.badlogic.gdx.graphics.g2d.Batch.Y2) = y2
    vertices(com.badlogic.gdx.graphics.g2d.Batch.Y3) = y2
    vertices(com.badlogic.gdx.graphics.g2d.Batch.Y4) = y
  }
  def setCenterX(x: scala.Float): scala.Unit = {
    this.setX(x - (this.width / 2))
  }
  def setCenterY(y: scala.Float): scala.Unit = {
    this.setY(y - (this.height / 2))
  }
  def setCenter(x: scala.Float, y: scala.Float): scala.Unit = {
    this.setPosition(x - (this.width / 2), y - (this.height / 2))
  }
  def translateX(xAmount: scala.Float): scala.Unit = {
    this.x = this.x + xAmount
    if (this.dirty) {
      return
    } else ()
    if (((this.rotation != 0) || (this.scaleX != 1)) || (this.scaleY != 1)) {
      this.dirty = true
      return
    } else ()
    val vertices: scala.Array[scala.Float] = this.vertices
    vertices(com.badlogic.gdx.graphics.g2d.Batch.X1) = vertices(com.badlogic.gdx.graphics.g2d.Batch.X1) + xAmount
    vertices(com.badlogic.gdx.graphics.g2d.Batch.X2) = vertices(com.badlogic.gdx.graphics.g2d.Batch.X2) + xAmount
    vertices(com.badlogic.gdx.graphics.g2d.Batch.X3) = vertices(com.badlogic.gdx.graphics.g2d.Batch.X3) + xAmount
    vertices(com.badlogic.gdx.graphics.g2d.Batch.X4) = vertices(com.badlogic.gdx.graphics.g2d.Batch.X4) + xAmount
  }
  def translateY(yAmount: scala.Float): scala.Unit = {
    this.y = this.y + yAmount
    if (this.dirty) {
      return
    } else ()
    if (((this.rotation != 0) || (this.scaleX != 1)) || (this.scaleY != 1)) {
      this.dirty = true
      return
    } else ()
    val vertices: scala.Array[scala.Float] = this.vertices
    vertices(com.badlogic.gdx.graphics.g2d.Batch.Y1) = vertices(com.badlogic.gdx.graphics.g2d.Batch.Y1) + yAmount
    vertices(com.badlogic.gdx.graphics.g2d.Batch.Y2) = vertices(com.badlogic.gdx.graphics.g2d.Batch.Y2) + yAmount
    vertices(com.badlogic.gdx.graphics.g2d.Batch.Y3) = vertices(com.badlogic.gdx.graphics.g2d.Batch.Y3) + yAmount
    vertices(com.badlogic.gdx.graphics.g2d.Batch.Y4) = vertices(com.badlogic.gdx.graphics.g2d.Batch.Y4) + yAmount
  }
  def translate(xAmount: scala.Float, yAmount: scala.Float): scala.Unit = {
    this.x = this.x + xAmount
    this.y = this.y + yAmount
    if (this.dirty) {
      return
    } else ()
    if (((this.rotation != 0) || (this.scaleX != 1)) || (this.scaleY != 1)) {
      this.dirty = true
      return
    } else ()
    val vertices: scala.Array[scala.Float] = this.vertices
    vertices(com.badlogic.gdx.graphics.g2d.Batch.X1) = vertices(com.badlogic.gdx.graphics.g2d.Batch.X1) + xAmount
    vertices(com.badlogic.gdx.graphics.g2d.Batch.Y1) = vertices(com.badlogic.gdx.graphics.g2d.Batch.Y1) + yAmount
    vertices(com.badlogic.gdx.graphics.g2d.Batch.X2) = vertices(com.badlogic.gdx.graphics.g2d.Batch.X2) + xAmount
    vertices(com.badlogic.gdx.graphics.g2d.Batch.Y2) = vertices(com.badlogic.gdx.graphics.g2d.Batch.Y2) + yAmount
    vertices(com.badlogic.gdx.graphics.g2d.Batch.X3) = vertices(com.badlogic.gdx.graphics.g2d.Batch.X3) + xAmount
    vertices(com.badlogic.gdx.graphics.g2d.Batch.Y3) = vertices(com.badlogic.gdx.graphics.g2d.Batch.Y3) + yAmount
    vertices(com.badlogic.gdx.graphics.g2d.Batch.X4) = vertices(com.badlogic.gdx.graphics.g2d.Batch.X4) + xAmount
    vertices(com.badlogic.gdx.graphics.g2d.Batch.Y4) = vertices(com.badlogic.gdx.graphics.g2d.Batch.Y4) + yAmount
  }
  def setColor(tint: com.badlogic.gdx.graphics.Color): scala.Unit = {
    this.color.set(tint)
    this.packedColor = tint.toFloatBits()
    val vertices: scala.Array[scala.Float] = this.vertices
    vertices(com.badlogic.gdx.graphics.g2d.Batch.C1) = this.packedColor
    vertices(com.badlogic.gdx.graphics.g2d.Batch.C2) = this.packedColor
    vertices(com.badlogic.gdx.graphics.g2d.Batch.C3) = this.packedColor
    vertices(com.badlogic.gdx.graphics.g2d.Batch.C4) = this.packedColor
  }
  def setAlpha(a: scala.Float): scala.Unit = {
    if (this.color.a != a) {
      this.color.a = a
      this.packedColor = this.color.toFloatBits()
      this.vertices(com.badlogic.gdx.graphics.g2d.Batch.C1) = this.packedColor
      this.vertices(com.badlogic.gdx.graphics.g2d.Batch.C2) = this.packedColor
      this.vertices(com.badlogic.gdx.graphics.g2d.Batch.C3) = this.packedColor
      this.vertices(com.badlogic.gdx.graphics.g2d.Batch.C4) = this.packedColor
    } else ()
  }
  def setColor(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): scala.Unit = {
    this.color.set(r, g, b, a)
    this.packedColor = this.color.toFloatBits()
    val vertices: scala.Array[scala.Float] = this.vertices
    vertices(com.badlogic.gdx.graphics.g2d.Batch.C1) = this.packedColor
    vertices(com.badlogic.gdx.graphics.g2d.Batch.C2) = this.packedColor
    vertices(com.badlogic.gdx.graphics.g2d.Batch.C3) = this.packedColor
    vertices(com.badlogic.gdx.graphics.g2d.Batch.C4) = this.packedColor
  }
  def setPackedColor(packedColor: scala.Float): scala.Unit = {
    if ((packedColor != this.packedColor) || (((packedColor == 0.0f) && (this.packedColor == 0.0f)) && (java.lang.Float.floatToIntBits(packedColor) != java.lang.Float.floatToIntBits(this.packedColor)))) {
      this.packedColor = packedColor
      com.badlogic.gdx.graphics.Color.abgr8888ToColor(this.color, packedColor)
      val vertices: scala.Array[scala.Float] = this.vertices
      vertices(com.badlogic.gdx.graphics.g2d.Batch.C1) = packedColor
      vertices(com.badlogic.gdx.graphics.g2d.Batch.C2) = packedColor
      vertices(com.badlogic.gdx.graphics.g2d.Batch.C3) = packedColor
      vertices(com.badlogic.gdx.graphics.g2d.Batch.C4) = packedColor
    } else ()
  }
  def setOrigin(originX: scala.Float, originY: scala.Float): scala.Unit = {
    this.originX = originX
    this.originY = originY
    this.dirty = true
  }
  def setOriginCenter(): scala.Unit = {
    this.originX = this.width / 2
    this.originY = this.height / 2
    this.dirty = true
  }
  def setRotation(degrees: scala.Float): scala.Unit = {
    this.rotation = degrees
    this.dirty = true
  }
  def getRotation(): scala.Float = {
    return this.rotation
  }
  def rotate(degrees: scala.Float): scala.Unit = {
    if (degrees == 0) {
      return
    } else ()
    this.rotation = this.rotation + degrees
    this.dirty = true
  }
  def rotate90(clockwise: scala.Boolean): scala.Unit = {
    val vertices: scala.Array[scala.Float] = this.vertices
    if (clockwise) {
      var temp: scala.Float = vertices(com.badlogic.gdx.graphics.g2d.Batch.V1)
      vertices(com.badlogic.gdx.graphics.g2d.Batch.V1) = vertices(com.badlogic.gdx.graphics.g2d.Batch.V4)
      vertices(com.badlogic.gdx.graphics.g2d.Batch.V4) = vertices(com.badlogic.gdx.graphics.g2d.Batch.V3)
      vertices(com.badlogic.gdx.graphics.g2d.Batch.V3) = vertices(com.badlogic.gdx.graphics.g2d.Batch.V2)
      vertices(com.badlogic.gdx.graphics.g2d.Batch.V2) = temp
      temp = vertices(com.badlogic.gdx.graphics.g2d.Batch.U1)
      vertices(com.badlogic.gdx.graphics.g2d.Batch.U1) = vertices(com.badlogic.gdx.graphics.g2d.Batch.U4)
      vertices(com.badlogic.gdx.graphics.g2d.Batch.U4) = vertices(com.badlogic.gdx.graphics.g2d.Batch.U3)
      vertices(com.badlogic.gdx.graphics.g2d.Batch.U3) = vertices(com.badlogic.gdx.graphics.g2d.Batch.U2)
      vertices(com.badlogic.gdx.graphics.g2d.Batch.U2) = temp
    } else {
      var temp: scala.Float = vertices(com.badlogic.gdx.graphics.g2d.Batch.V1)
      vertices(com.badlogic.gdx.graphics.g2d.Batch.V1) = vertices(com.badlogic.gdx.graphics.g2d.Batch.V2)
      vertices(com.badlogic.gdx.graphics.g2d.Batch.V2) = vertices(com.badlogic.gdx.graphics.g2d.Batch.V3)
      vertices(com.badlogic.gdx.graphics.g2d.Batch.V3) = vertices(com.badlogic.gdx.graphics.g2d.Batch.V4)
      vertices(com.badlogic.gdx.graphics.g2d.Batch.V4) = temp
      temp = vertices(com.badlogic.gdx.graphics.g2d.Batch.U1)
      vertices(com.badlogic.gdx.graphics.g2d.Batch.U1) = vertices(com.badlogic.gdx.graphics.g2d.Batch.U2)
      vertices(com.badlogic.gdx.graphics.g2d.Batch.U2) = vertices(com.badlogic.gdx.graphics.g2d.Batch.U3)
      vertices(com.badlogic.gdx.graphics.g2d.Batch.U3) = vertices(com.badlogic.gdx.graphics.g2d.Batch.U4)
      vertices(com.badlogic.gdx.graphics.g2d.Batch.U4) = temp
    }
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
    if (this.dirty) {
      this.dirty = false
      val vertices: scala.Array[scala.Float] = this.vertices
      var localX: scala.Float = -this.originX
      var localY: scala.Float = -this.originY
      var localX2: scala.Float = localX + this.width
      var localY2: scala.Float = localY + this.height
      val worldOriginX: scala.Float = this.x - localX
      val worldOriginY: scala.Float = this.y - localY
      if ((this.scaleX != 1) || (this.scaleY != 1)) {
        localX = localX * this.scaleX
        localY = localY * this.scaleY
        localX2 = localX2 * this.scaleX
        localY2 = localY2 * this.scaleY
      } else ()
      if (this.rotation != 0) {
        val cos: scala.Float = com.badlogic.gdx.math.MathUtils.cosDeg(this.rotation)
        val sin: scala.Float = com.badlogic.gdx.math.MathUtils.sinDeg(this.rotation)
        val localXCos: scala.Float = localX * cos
        val localXSin: scala.Float = localX * sin
        val localYCos: scala.Float = localY * cos
        val localYSin: scala.Float = localY * sin
        val localX2Cos: scala.Float = localX2 * cos
        val localX2Sin: scala.Float = localX2 * sin
        val localY2Cos: scala.Float = localY2 * cos
        val localY2Sin: scala.Float = localY2 * sin
        val x1: scala.Float = (localXCos - localYSin) + worldOriginX
        val y1: scala.Float = (localYCos + localXSin) + worldOriginY
        vertices(com.badlogic.gdx.graphics.g2d.Batch.X1) = x1
        vertices(com.badlogic.gdx.graphics.g2d.Batch.Y1) = y1
        val x2: scala.Float = (localXCos - localY2Sin) + worldOriginX
        val y2: scala.Float = (localY2Cos + localXSin) + worldOriginY
        vertices(com.badlogic.gdx.graphics.g2d.Batch.X2) = x2
        vertices(com.badlogic.gdx.graphics.g2d.Batch.Y2) = y2
        val x3: scala.Float = (localX2Cos - localY2Sin) + worldOriginX
        val y3: scala.Float = (localY2Cos + localX2Sin) + worldOriginY
        vertices(com.badlogic.gdx.graphics.g2d.Batch.X3) = x3
        vertices(com.badlogic.gdx.graphics.g2d.Batch.Y3) = y3
        vertices(com.badlogic.gdx.graphics.g2d.Batch.X4) = x1 + (x3 - x2)
        vertices(com.badlogic.gdx.graphics.g2d.Batch.Y4) = y3 - (y2 - y1)
      } else {
        val x1: scala.Float = localX + worldOriginX
        val y1: scala.Float = localY + worldOriginY
        val x2: scala.Float = localX2 + worldOriginX
        val y2: scala.Float = localY2 + worldOriginY
        vertices(com.badlogic.gdx.graphics.g2d.Batch.X1) = x1
        vertices(com.badlogic.gdx.graphics.g2d.Batch.Y1) = y1
        vertices(com.badlogic.gdx.graphics.g2d.Batch.X2) = x1
        vertices(com.badlogic.gdx.graphics.g2d.Batch.Y2) = y2
        vertices(com.badlogic.gdx.graphics.g2d.Batch.X3) = x2
        vertices(com.badlogic.gdx.graphics.g2d.Batch.Y3) = y2
        vertices(com.badlogic.gdx.graphics.g2d.Batch.X4) = x2
        vertices(com.badlogic.gdx.graphics.g2d.Batch.Y4) = y1
      }
    } else ()
    return this.vertices
  }
  def getBoundingRectangle(): com.badlogic.gdx.math.Rectangle = {
    val vertices: scala.Array[scala.Float] = this.getVertices()
    var minx: scala.Float = vertices(com.badlogic.gdx.graphics.g2d.Batch.X1)
    var miny: scala.Float = vertices(com.badlogic.gdx.graphics.g2d.Batch.Y1)
    var maxx: scala.Float = vertices(com.badlogic.gdx.graphics.g2d.Batch.X1)
    var maxy: scala.Float = vertices(com.badlogic.gdx.graphics.g2d.Batch.Y1)
    minx = if (minx > vertices(com.badlogic.gdx.graphics.g2d.Batch.X2)) vertices(com.badlogic.gdx.graphics.g2d.Batch.X2) else minx
    minx = if (minx > vertices(com.badlogic.gdx.graphics.g2d.Batch.X3)) vertices(com.badlogic.gdx.graphics.g2d.Batch.X3) else minx
    minx = if (minx > vertices(com.badlogic.gdx.graphics.g2d.Batch.X4)) vertices(com.badlogic.gdx.graphics.g2d.Batch.X4) else minx
    maxx = if (maxx < vertices(com.badlogic.gdx.graphics.g2d.Batch.X2)) vertices(com.badlogic.gdx.graphics.g2d.Batch.X2) else maxx
    maxx = if (maxx < vertices(com.badlogic.gdx.graphics.g2d.Batch.X3)) vertices(com.badlogic.gdx.graphics.g2d.Batch.X3) else maxx
    maxx = if (maxx < vertices(com.badlogic.gdx.graphics.g2d.Batch.X4)) vertices(com.badlogic.gdx.graphics.g2d.Batch.X4) else maxx
    miny = if (miny > vertices(com.badlogic.gdx.graphics.g2d.Batch.Y2)) vertices(com.badlogic.gdx.graphics.g2d.Batch.Y2) else miny
    miny = if (miny > vertices(com.badlogic.gdx.graphics.g2d.Batch.Y3)) vertices(com.badlogic.gdx.graphics.g2d.Batch.Y3) else miny
    miny = if (miny > vertices(com.badlogic.gdx.graphics.g2d.Batch.Y4)) vertices(com.badlogic.gdx.graphics.g2d.Batch.Y4) else miny
    maxy = if (maxy < vertices(com.badlogic.gdx.graphics.g2d.Batch.Y2)) vertices(com.badlogic.gdx.graphics.g2d.Batch.Y2) else maxy
    maxy = if (maxy < vertices(com.badlogic.gdx.graphics.g2d.Batch.Y3)) vertices(com.badlogic.gdx.graphics.g2d.Batch.Y3) else maxy
    maxy = if (maxy < vertices(com.badlogic.gdx.graphics.g2d.Batch.Y4)) vertices(com.badlogic.gdx.graphics.g2d.Batch.Y4) else maxy
    if (this.bounds == null) {
      this.bounds = new com.badlogic.gdx.math.Rectangle()
    } else ()
    this.bounds.x = minx
    this.bounds.y = miny
    this.bounds.width = maxx - minx
    this.bounds.height = maxy - miny
    return this.bounds
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch): scala.Unit = {
    batch.draw(texture, this.getVertices(), 0, Sprite.SPRITE_SIZE)
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, alphaModulation: scala.Float): scala.Unit = {
    val oldAlpha: scala.Float = this.getColor().a
    this.setAlpha(oldAlpha * alphaModulation)
    this.draw(batch)
    this.setAlpha(oldAlpha)
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
  def getScaleX(): scala.Float = {
    return this.scaleX
  }
  def getScaleY(): scala.Float = {
    return this.scaleY
  }
  def getColor(): com.badlogic.gdx.graphics.Color = {
    return this.color
  }
  def getPackedColor(): scala.Float = {
    return this.packedColor
  }
  def setRegion(u: scala.Float, v: scala.Float, u2: scala.Float, v2: scala.Float): scala.Unit = {
    super.setRegion(u, v, u2, v2)
    val vertices: scala.Array[scala.Float] = this.vertices
    vertices(com.badlogic.gdx.graphics.g2d.Batch.U1) = u
    vertices(com.badlogic.gdx.graphics.g2d.Batch.V1) = v2
    vertices(com.badlogic.gdx.graphics.g2d.Batch.U2) = u
    vertices(com.badlogic.gdx.graphics.g2d.Batch.V2) = v
    vertices(com.badlogic.gdx.graphics.g2d.Batch.U3) = u2
    vertices(com.badlogic.gdx.graphics.g2d.Batch.V3) = v
    vertices(com.badlogic.gdx.graphics.g2d.Batch.U4) = u2
    vertices(com.badlogic.gdx.graphics.g2d.Batch.V4) = v2
  }
  def setU(u: scala.Float): scala.Unit = {
    super.setU(u)
    this.vertices(com.badlogic.gdx.graphics.g2d.Batch.U1) = u
    this.vertices(com.badlogic.gdx.graphics.g2d.Batch.U2) = u
  }
  def setV(v: scala.Float): scala.Unit = {
    super.setV(v)
    this.vertices(com.badlogic.gdx.graphics.g2d.Batch.V2) = v
    this.vertices(com.badlogic.gdx.graphics.g2d.Batch.V3) = v
  }
  def setU2(u2: scala.Float): scala.Unit = {
    super.setU2(u2)
    this.vertices(com.badlogic.gdx.graphics.g2d.Batch.U3) = u2
    this.vertices(com.badlogic.gdx.graphics.g2d.Batch.U4) = u2
  }
  def setV2(v2: scala.Float): scala.Unit = {
    super.setV2(v2)
    this.vertices(com.badlogic.gdx.graphics.g2d.Batch.V1) = v2
    this.vertices(com.badlogic.gdx.graphics.g2d.Batch.V4) = v2
  }
  def setFlip(x: scala.Boolean, y: scala.Boolean): scala.Unit = {
    var performX: scala.Boolean = false
    var performY: scala.Boolean = false
    if (this.isFlipX() != x) {
      performX = true
    } else ()
    if (this.isFlipY() != y) {
      performY = true
    } else ()
    this.flip(performX, performY)
  }
  def flip(x: scala.Boolean, y: scala.Boolean): scala.Unit = {
    super.flip(x, y)
    val vertices: scala.Array[scala.Float] = this.vertices
    if (x) {
      var temp: scala.Float = vertices(com.badlogic.gdx.graphics.g2d.Batch.U1)
      vertices(com.badlogic.gdx.graphics.g2d.Batch.U1) = vertices(com.badlogic.gdx.graphics.g2d.Batch.U3)
      vertices(com.badlogic.gdx.graphics.g2d.Batch.U3) = temp
      temp = vertices(com.badlogic.gdx.graphics.g2d.Batch.U2)
      vertices(com.badlogic.gdx.graphics.g2d.Batch.U2) = vertices(com.badlogic.gdx.graphics.g2d.Batch.U4)
      vertices(com.badlogic.gdx.graphics.g2d.Batch.U4) = temp
    } else ()
    if (y) {
      var temp: scala.Float = vertices(com.badlogic.gdx.graphics.g2d.Batch.V1)
      vertices(com.badlogic.gdx.graphics.g2d.Batch.V1) = vertices(com.badlogic.gdx.graphics.g2d.Batch.V3)
      vertices(com.badlogic.gdx.graphics.g2d.Batch.V3) = temp
      temp = vertices(com.badlogic.gdx.graphics.g2d.Batch.V2)
      vertices(com.badlogic.gdx.graphics.g2d.Batch.V2) = vertices(com.badlogic.gdx.graphics.g2d.Batch.V4)
      vertices(com.badlogic.gdx.graphics.g2d.Batch.V4) = temp
    } else ()
  }
  def scroll(xAmount: scala.Float, yAmount: scala.Float): scala.Unit = {
    val vertices: scala.Array[scala.Float] = this.vertices
    if (xAmount != 0) {
      var u: scala.Float = (vertices(com.badlogic.gdx.graphics.g2d.Batch.U1) + xAmount) % 1
      var u2: scala.Float = u + (this.width / texture.getWidth())
      this.u = u
      this.u2 = u2
      vertices(com.badlogic.gdx.graphics.g2d.Batch.U1) = u
      vertices(com.badlogic.gdx.graphics.g2d.Batch.U2) = u
      vertices(com.badlogic.gdx.graphics.g2d.Batch.U3) = u2
      vertices(com.badlogic.gdx.graphics.g2d.Batch.U4) = u2
    } else ()
    if (yAmount != 0) {
      var v: scala.Float = (vertices(com.badlogic.gdx.graphics.g2d.Batch.V2) + yAmount) % 1
      var v2: scala.Float = v + (this.height / texture.getHeight())
      this.v = v
      this.v2 = v2
      vertices(com.badlogic.gdx.graphics.g2d.Batch.V1) = v2
      vertices(com.badlogic.gdx.graphics.g2d.Batch.V2) = v
      vertices(com.badlogic.gdx.graphics.g2d.Batch.V3) = v
      vertices(com.badlogic.gdx.graphics.g2d.Batch.V4) = v2
    } else ()
  }
}
object Sprite {
  export com.badlogic.gdx.graphics.g2d.TextureRegion.{SPRITE_SIZE => _, VERTEX_SIZE => _, *}
  final val VERTEX_SIZE: scala.Int = (2 + 1) + 2
  final val SPRITE_SIZE: scala.Int = 4 * Sprite.VERTEX_SIZE
}