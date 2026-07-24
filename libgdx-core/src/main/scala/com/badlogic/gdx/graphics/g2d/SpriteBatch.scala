package com.badlogic.gdx.graphics.g2d

class SpriteBatch extends com.badlogic.gdx.graphics.g2d.Batch {
  private var currentDataType: com.badlogic.gdx.graphics.Mesh.VertexDataType = null.asInstanceOf[com.badlogic.gdx.graphics.Mesh.VertexDataType]
  private var mesh: com.badlogic.gdx.graphics.Mesh = null.asInstanceOf[com.badlogic.gdx.graphics.Mesh]
  var vertices: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  var idx: scala.Int = 0
  var lastTexture: com.badlogic.gdx.graphics.Texture = null
  var invTexWidth: scala.Float = 0
  var invTexHeight: scala.Float = 0
  var drawing: scala.Boolean = false
  private final val transformMatrix: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
  private final val projectionMatrix: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
  private final val combinedMatrix: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
  private var blendingDisabled: scala.Boolean = false
  private var blendSrcFunc: scala.Int = com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA
  private var blendDstFunc: scala.Int = com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA
  private var blendSrcFuncAlpha: scala.Int = com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA
  private var blendDstFuncAlpha: scala.Int = com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA
  private var shader: com.badlogic.gdx.graphics.glutils.ShaderProgram = null.asInstanceOf[com.badlogic.gdx.graphics.glutils.ShaderProgram]
  private var customShader: com.badlogic.gdx.graphics.glutils.ShaderProgram = null
  private var ownsShader: scala.Boolean = false
  private final val color: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color(1, 1, 1, 1)
  var colorPacked: scala.Float = com.badlogic.gdx.graphics.Color.WHITE_FLOAT_BITS
  var renderCalls: scala.Int = 0
  var totalRenderCalls: scala.Int = 0
  var maxSpritesInBatch: scala.Int = 0
  def this(size: scala.Int, defaultShader: com.badlogic.gdx.graphics.glutils.ShaderProgram) = {
    this()
    if (size > 8191) {
      throw new java.lang.IllegalArgumentException("Can't have more than 8191 sprites per batch: " + size)
    } else ()
    var vertexDataType: com.badlogic.gdx.graphics.Mesh.VertexDataType = if (com.badlogic.gdx.Gdx.gl30 != null) com.badlogic.gdx.graphics.Mesh.VertexDataType.VertexBufferObjectWithVAO else SpriteBatch.defaultVertexDataType
    if (SpriteBatch.overrideVertexType != null) {
      vertexDataType = SpriteBatch.overrideVertexType
    } else ()
    this.currentDataType = vertexDataType
    this.mesh = new com.badlogic.gdx.graphics.Mesh(this.currentDataType, false, size * 4, size * 6, new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Position, 2, com.badlogic.gdx.graphics.glutils.ShaderProgram.POSITION_ATTRIBUTE), new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorPacked, 4, com.badlogic.gdx.graphics.glutils.ShaderProgram.COLOR_ATTRIBUTE), new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.TextureCoordinates, 2, com.badlogic.gdx.graphics.glutils.ShaderProgram.TEXCOORD_ATTRIBUTE + "0"))
    this.projectionMatrix.setToOrtho2D(0, 0, com.badlogic.gdx.Gdx.graphics.getWidth(), com.badlogic.gdx.Gdx.graphics.getHeight())
    this.vertices = new scala.Array[scala.Float](size * com.badlogic.gdx.graphics.g2d.Sprite.SPRITE_SIZE)
    val len: scala.Int = size * 6
    val indices: scala.Array[scala.Short] = new scala.Array[scala.Short](len)
    var j: scala.Short = 0.asInstanceOf[scala.Short];
    { var i: scala.Int = 0; while (i < len) { {
      indices(i) = j
      indices(i + 1) = (j + 1).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
      indices(i + 2) = (j + 2).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
      indices(i + 3) = (j + 2).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
      indices(i + 4) = (j + 3).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
      indices(i + 5) = j
    }; i = i + 6; j = j + 4 } }
    this.mesh.setIndices(indices)
    if (defaultShader == null) {
      this.shader = SpriteBatch.createDefaultShader()
      this.ownsShader = true
    } else {
      this.shader = defaultShader
    }
    if (vertexDataType != com.badlogic.gdx.graphics.Mesh.VertexDataType.VertexArray) {
      this.mesh.getIndexData().bind()
      this.mesh.getIndexData().unbind()
    } else ()
  }
  def this(size: scala.Int) = {
    this(size, null)
  }
  def begin(): scala.Unit = {
    if (this.drawing) {
      throw new java.lang.IllegalStateException("SpriteBatch.end must be called before begin.")
    } else ()
    this.renderCalls = 0
    com.badlogic.gdx.Gdx.gl.glDepthMask(false)
    if (this.customShader != null) {
      this.customShader.bind()
    } else {
      this.shader.bind()
    }
    this.setupMatrices()
    this.drawing = true
  }
  def `end`(): scala.Unit = {
    if (!this.drawing) {
      throw new java.lang.IllegalStateException("SpriteBatch.begin must be called before end.")
    } else ()
    if (this.idx > 0) {
      this.flush()
    } else ()
    this.lastTexture = null
    this.drawing = false
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl
    gl.glDepthMask(true)
    if (this.isBlendingEnabled()) {
      gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND)
    } else ()
  }
  def setColor(tint: com.badlogic.gdx.graphics.Color): scala.Unit = {
    this.color.set(tint)
    this.colorPacked = tint.toFloatBits()
  }
  def setColor(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): scala.Unit = {
    this.color.set(r, g, b, a)
    this.colorPacked = this.color.toFloatBits()
  }
  def getColor(): com.badlogic.gdx.graphics.Color = {
    return this.color
  }
  def setPackedColor(packedColor: scala.Float): scala.Unit = {
    com.badlogic.gdx.graphics.Color.abgr8888ToColor(this.color, packedColor)
    this.colorPacked = packedColor
  }
  def getPackedColor(): scala.Float = {
    return this.colorPacked
  }
  def draw(texture: com.badlogic.gdx.graphics.Texture, x: scala.Float, y: scala.Float, originX: scala.Float, originY: scala.Float, width: scala.Float, height: scala.Float, scaleX: scala.Float, scaleY: scala.Float, rotation: scala.Float, srcX: scala.Int, srcY: scala.Int, srcWidth: scala.Int, srcHeight: scala.Int, flipX: scala.Boolean, flipY: scala.Boolean): scala.Unit = {
    if (!this.drawing) {
      throw new java.lang.IllegalStateException("SpriteBatch.begin must be called before draw.")
    } else ()
    val vertices: scala.Array[scala.Float] = this.vertices
    if (texture != this.lastTexture) {
      this.switchTexture(texture)
    } else {
      if (this.idx == vertices.length) {
        this.flush()
      } else ()
    }
    val worldOriginX: scala.Float = x + originX
    val worldOriginY: scala.Float = y + originY
    var fx: scala.Float = -originX
    var fy: scala.Float = -originY
    var fx2: scala.Float = width - originX
    var fy2: scala.Float = height - originY
    if ((scaleX != 1) || (scaleY != 1)) {
      fx = fx * scaleX
      fy = fy * scaleY
      fx2 = fx2 * scaleX
      fy2 = fy2 * scaleY
    } else ()
    val p1x: scala.Float = fx
    val p1y: scala.Float = fy
    val p2x: scala.Float = fx
    val p2y: scala.Float = fy2
    val p3x: scala.Float = fx2
    val p3y: scala.Float = fy2
    val p4x: scala.Float = fx2
    val p4y: scala.Float = fy
    var x1: scala.Float = 0.0f
    var y1: scala.Float = 0.0f
    var x2: scala.Float = 0.0f
    var y2: scala.Float = 0.0f
    var x3: scala.Float = 0.0f
    var y3: scala.Float = 0.0f
    var x4: scala.Float = 0.0f
    var y4: scala.Float = 0.0f
    if (rotation != 0) {
      val cos: scala.Float = com.badlogic.gdx.math.MathUtils.cosDeg(rotation)
      val sin: scala.Float = com.badlogic.gdx.math.MathUtils.sinDeg(rotation)
      x1 = (cos * p1x) - (sin * p1y)
      y1 = (sin * p1x) + (cos * p1y)
      x2 = (cos * p2x) - (sin * p2y)
      y2 = (sin * p2x) + (cos * p2y)
      x3 = (cos * p3x) - (sin * p3y)
      y3 = (sin * p3x) + (cos * p3y)
      x4 = x1 + (x3 - x2)
      y4 = y3 - (y2 - y1)
    } else {
      x1 = p1x
      y1 = p1y
      x2 = p2x
      y2 = p2y
      x3 = p3x
      y3 = p3y
      x4 = p4x
      y4 = p4y
    }
    x1 = x1 + worldOriginX
    y1 = y1 + worldOriginY
    x2 = x2 + worldOriginX
    y2 = y2 + worldOriginY
    x3 = x3 + worldOriginX
    y3 = y3 + worldOriginY
    x4 = x4 + worldOriginX
    y4 = y4 + worldOriginY
    var u: scala.Float = srcX * this.invTexWidth
    var v: scala.Float = (srcY + srcHeight) * this.invTexHeight
    var u2: scala.Float = (srcX + srcWidth) * this.invTexWidth
    var v2: scala.Float = srcY * this.invTexHeight
    if (flipX) {
      val tmp: scala.Float = u
      u = u2
      u2 = tmp
    } else ()
    if (flipY) {
      val tmp: scala.Float = v
      v = v2
      v2 = tmp
    } else ()
    val color: scala.Float = this.colorPacked
    var idx: scala.Int = this.idx
    vertices(idx) = x1
    vertices(idx + 1) = y1
    vertices(idx + 2) = color
    vertices(idx + 3) = u
    vertices(idx + 4) = v
    vertices(idx + 5) = x2
    vertices(idx + 6) = y2
    vertices(idx + 7) = color
    vertices(idx + 8) = u
    vertices(idx + 9) = v2
    vertices(idx + 10) = x3
    vertices(idx + 11) = y3
    vertices(idx + 12) = color
    vertices(idx + 13) = u2
    vertices(idx + 14) = v2
    vertices(idx + 15) = x4
    vertices(idx + 16) = y4
    vertices(idx + 17) = color
    vertices(idx + 18) = u2
    vertices(idx + 19) = v
    this.idx = idx + 20
  }
  def draw(texture: com.badlogic.gdx.graphics.Texture, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float, srcX: scala.Int, srcY: scala.Int, srcWidth: scala.Int, srcHeight: scala.Int, flipX: scala.Boolean, flipY: scala.Boolean): scala.Unit = {
    if (!this.drawing) {
      throw new java.lang.IllegalStateException("SpriteBatch.begin must be called before draw.")
    } else ()
    val vertices: scala.Array[scala.Float] = this.vertices
    if (texture != this.lastTexture) {
      this.switchTexture(texture)
    } else {
      if (this.idx == vertices.length) {
        this.flush()
      } else ()
    }
    var u: scala.Float = srcX * this.invTexWidth
    var v: scala.Float = (srcY + srcHeight) * this.invTexHeight
    var u2: scala.Float = (srcX + srcWidth) * this.invTexWidth
    var v2: scala.Float = srcY * this.invTexHeight
    val fx2: scala.Float = x + width
    val fy2: scala.Float = y + height
    if (flipX) {
      val tmp: scala.Float = u
      u = u2
      u2 = tmp
    } else ()
    if (flipY) {
      val tmp: scala.Float = v
      v = v2
      v2 = tmp
    } else ()
    val color: scala.Float = this.colorPacked
    var idx: scala.Int = this.idx
    vertices(idx) = x
    vertices(idx + 1) = y
    vertices(idx + 2) = color
    vertices(idx + 3) = u
    vertices(idx + 4) = v
    vertices(idx + 5) = x
    vertices(idx + 6) = fy2
    vertices(idx + 7) = color
    vertices(idx + 8) = u
    vertices(idx + 9) = v2
    vertices(idx + 10) = fx2
    vertices(idx + 11) = fy2
    vertices(idx + 12) = color
    vertices(idx + 13) = u2
    vertices(idx + 14) = v2
    vertices(idx + 15) = fx2
    vertices(idx + 16) = y
    vertices(idx + 17) = color
    vertices(idx + 18) = u2
    vertices(idx + 19) = v
    this.idx = idx + 20
  }
  def draw(texture: com.badlogic.gdx.graphics.Texture, x: scala.Float, y: scala.Float, srcX: scala.Int, srcY: scala.Int, srcWidth: scala.Int, srcHeight: scala.Int): scala.Unit = {
    if (!this.drawing) {
      throw new java.lang.IllegalStateException("SpriteBatch.begin must be called before draw.")
    } else ()
    val vertices: scala.Array[scala.Float] = this.vertices
    if (texture != this.lastTexture) {
      this.switchTexture(texture)
    } else {
      if (this.idx == vertices.length) {
        this.flush()
      } else ()
    }
    val u: scala.Float = srcX * this.invTexWidth
    val v: scala.Float = (srcY + srcHeight) * this.invTexHeight
    val u2: scala.Float = (srcX + srcWidth) * this.invTexWidth
    val v2: scala.Float = srcY * this.invTexHeight
    val fx2: scala.Float = x + srcWidth
    val fy2: scala.Float = y + srcHeight
    val color: scala.Float = this.colorPacked
    var idx: scala.Int = this.idx
    vertices(idx) = x
    vertices(idx + 1) = y
    vertices(idx + 2) = color
    vertices(idx + 3) = u
    vertices(idx + 4) = v
    vertices(idx + 5) = x
    vertices(idx + 6) = fy2
    vertices(idx + 7) = color
    vertices(idx + 8) = u
    vertices(idx + 9) = v2
    vertices(idx + 10) = fx2
    vertices(idx + 11) = fy2
    vertices(idx + 12) = color
    vertices(idx + 13) = u2
    vertices(idx + 14) = v2
    vertices(idx + 15) = fx2
    vertices(idx + 16) = y
    vertices(idx + 17) = color
    vertices(idx + 18) = u2
    vertices(idx + 19) = v
    this.idx = idx + 20
  }
  def draw(texture: com.badlogic.gdx.graphics.Texture, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float, u: scala.Float, v: scala.Float, u2: scala.Float, v2: scala.Float): scala.Unit = {
    if (!this.drawing) {
      throw new java.lang.IllegalStateException("SpriteBatch.begin must be called before draw.")
    } else ()
    val vertices: scala.Array[scala.Float] = this.vertices
    if (texture != this.lastTexture) {
      this.switchTexture(texture)
    } else {
      if (this.idx == vertices.length) {
        this.flush()
      } else ()
    }
    val fx2: scala.Float = x + width
    val fy2: scala.Float = y + height
    val color: scala.Float = this.colorPacked
    var idx: scala.Int = this.idx
    vertices(idx) = x
    vertices(idx + 1) = y
    vertices(idx + 2) = color
    vertices(idx + 3) = u
    vertices(idx + 4) = v
    vertices(idx + 5) = x
    vertices(idx + 6) = fy2
    vertices(idx + 7) = color
    vertices(idx + 8) = u
    vertices(idx + 9) = v2
    vertices(idx + 10) = fx2
    vertices(idx + 11) = fy2
    vertices(idx + 12) = color
    vertices(idx + 13) = u2
    vertices(idx + 14) = v2
    vertices(idx + 15) = fx2
    vertices(idx + 16) = y
    vertices(idx + 17) = color
    vertices(idx + 18) = u2
    vertices(idx + 19) = v
    this.idx = idx + 20
  }
  def draw(texture: com.badlogic.gdx.graphics.Texture, x: scala.Float, y: scala.Float): scala.Unit = {
    this.draw(texture, x, y, texture.getWidth(), texture.getHeight())
  }
  def draw(texture: com.badlogic.gdx.graphics.Texture, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    if (!this.drawing) {
      throw new java.lang.IllegalStateException("SpriteBatch.begin must be called before draw.")
    } else ()
    val vertices: scala.Array[scala.Float] = this.vertices
    if (texture != this.lastTexture) {
      this.switchTexture(texture)
    } else {
      if (this.idx == vertices.length) {
        this.flush()
      } else ()
    }
    val fx2: scala.Float = x + width
    val fy2: scala.Float = y + height
    val u: scala.Float = 0
    val v: scala.Float = 1
    val u2: scala.Float = 1
    val v2: scala.Float = 0
    val color: scala.Float = this.colorPacked
    var idx: scala.Int = this.idx
    vertices(idx) = x
    vertices(idx + 1) = y
    vertices(idx + 2) = color
    vertices(idx + 3) = u
    vertices(idx + 4) = v
    vertices(idx + 5) = x
    vertices(idx + 6) = fy2
    vertices(idx + 7) = color
    vertices(idx + 8) = u
    vertices(idx + 9) = v2
    vertices(idx + 10) = fx2
    vertices(idx + 11) = fy2
    vertices(idx + 12) = color
    vertices(idx + 13) = u2
    vertices(idx + 14) = v2
    vertices(idx + 15) = fx2
    vertices(idx + 16) = y
    vertices(idx + 17) = color
    vertices(idx + 18) = u2
    vertices(idx + 19) = v
    this.idx = idx + 20
  }
  def draw(texture: com.badlogic.gdx.graphics.Texture, spriteVertices: scala.Array[scala.Float], offset$arg: scala.Int, count$arg: scala.Int): scala.Unit = {
    var offset: scala.Int = offset$arg
    var count: scala.Int = count$arg
    if (!this.drawing) {
      throw new java.lang.IllegalStateException("SpriteBatch.begin must be called before draw.")
    } else ()
    val verticesLength: scala.Int = this.vertices.length
    var remainingVertices: scala.Int = verticesLength
    if (texture != this.lastTexture) {
      this.switchTexture(texture)
    } else {
      remainingVertices = remainingVertices - this.idx
      if (remainingVertices == 0) {
        this.flush()
        remainingVertices = verticesLength
      } else ()
    }
    var copyCount: scala.Int = java.lang.Math.min(remainingVertices, count)
    java.lang.System.arraycopy(spriteVertices, offset, this.vertices, this.idx, copyCount)
    this.idx = this.idx + copyCount
    count = count - copyCount
    while (count > 0) {
      offset = offset + copyCount
      this.flush()
      copyCount = java.lang.Math.min(verticesLength, count)
      java.lang.System.arraycopy(spriteVertices, offset, this.vertices, 0, copyCount)
      this.idx = this.idx + copyCount
      count = count - copyCount
    }
  }
  def draw(region: com.badlogic.gdx.graphics.g2d.TextureRegion, x: scala.Float, y: scala.Float): scala.Unit = {
    this.draw(region, x, y, region.getRegionWidth(), region.getRegionHeight())
  }
  def draw(region: com.badlogic.gdx.graphics.g2d.TextureRegion, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    if (!this.drawing) {
      throw new java.lang.IllegalStateException("SpriteBatch.begin must be called before draw.")
    } else ()
    val vertices: scala.Array[scala.Float] = this.vertices
    val texture: com.badlogic.gdx.graphics.Texture = region.texture
    if (texture != this.lastTexture) {
      this.switchTexture(texture)
    } else {
      if (this.idx == vertices.length) {
        this.flush()
      } else ()
    }
    val fx2: scala.Float = x + width
    val fy2: scala.Float = y + height
    val u: scala.Float = region.u
    val v: scala.Float = region.v2
    val u2: scala.Float = region.u2
    val v2: scala.Float = region.v
    val color: scala.Float = this.colorPacked
    var idx: scala.Int = this.idx
    vertices(idx) = x
    vertices(idx + 1) = y
    vertices(idx + 2) = color
    vertices(idx + 3) = u
    vertices(idx + 4) = v
    vertices(idx + 5) = x
    vertices(idx + 6) = fy2
    vertices(idx + 7) = color
    vertices(idx + 8) = u
    vertices(idx + 9) = v2
    vertices(idx + 10) = fx2
    vertices(idx + 11) = fy2
    vertices(idx + 12) = color
    vertices(idx + 13) = u2
    vertices(idx + 14) = v2
    vertices(idx + 15) = fx2
    vertices(idx + 16) = y
    vertices(idx + 17) = color
    vertices(idx + 18) = u2
    vertices(idx + 19) = v
    this.idx = idx + 20
  }
  def draw(region: com.badlogic.gdx.graphics.g2d.TextureRegion, x: scala.Float, y: scala.Float, originX: scala.Float, originY: scala.Float, width: scala.Float, height: scala.Float, scaleX: scala.Float, scaleY: scala.Float, rotation: scala.Float): scala.Unit = {
    if (!this.drawing) {
      throw new java.lang.IllegalStateException("SpriteBatch.begin must be called before draw.")
    } else ()
    val vertices: scala.Array[scala.Float] = this.vertices
    val texture: com.badlogic.gdx.graphics.Texture = region.texture
    if (texture != this.lastTexture) {
      this.switchTexture(texture)
    } else {
      if (this.idx == vertices.length) {
        this.flush()
      } else ()
    }
    val worldOriginX: scala.Float = x + originX
    val worldOriginY: scala.Float = y + originY
    var fx: scala.Float = -originX
    var fy: scala.Float = -originY
    var fx2: scala.Float = width - originX
    var fy2: scala.Float = height - originY
    if ((scaleX != 1) || (scaleY != 1)) {
      fx = fx * scaleX
      fy = fy * scaleY
      fx2 = fx2 * scaleX
      fy2 = fy2 * scaleY
    } else ()
    val p1x: scala.Float = fx
    val p1y: scala.Float = fy
    val p2x: scala.Float = fx
    val p2y: scala.Float = fy2
    val p3x: scala.Float = fx2
    val p3y: scala.Float = fy2
    val p4x: scala.Float = fx2
    val p4y: scala.Float = fy
    var x1: scala.Float = 0.0f
    var y1: scala.Float = 0.0f
    var x2: scala.Float = 0.0f
    var y2: scala.Float = 0.0f
    var x3: scala.Float = 0.0f
    var y3: scala.Float = 0.0f
    var x4: scala.Float = 0.0f
    var y4: scala.Float = 0.0f
    if (rotation != 0) {
      val cos: scala.Float = com.badlogic.gdx.math.MathUtils.cosDeg(rotation)
      val sin: scala.Float = com.badlogic.gdx.math.MathUtils.sinDeg(rotation)
      x1 = (cos * p1x) - (sin * p1y)
      y1 = (sin * p1x) + (cos * p1y)
      x2 = (cos * p2x) - (sin * p2y)
      y2 = (sin * p2x) + (cos * p2y)
      x3 = (cos * p3x) - (sin * p3y)
      y3 = (sin * p3x) + (cos * p3y)
      x4 = x1 + (x3 - x2)
      y4 = y3 - (y2 - y1)
    } else {
      x1 = p1x
      y1 = p1y
      x2 = p2x
      y2 = p2y
      x3 = p3x
      y3 = p3y
      x4 = p4x
      y4 = p4y
    }
    x1 = x1 + worldOriginX
    y1 = y1 + worldOriginY
    x2 = x2 + worldOriginX
    y2 = y2 + worldOriginY
    x3 = x3 + worldOriginX
    y3 = y3 + worldOriginY
    x4 = x4 + worldOriginX
    y4 = y4 + worldOriginY
    val u: scala.Float = region.u
    val v: scala.Float = region.v2
    val u2: scala.Float = region.u2
    val v2: scala.Float = region.v
    val color: scala.Float = this.colorPacked
    var idx: scala.Int = this.idx
    vertices(idx) = x1
    vertices(idx + 1) = y1
    vertices(idx + 2) = color
    vertices(idx + 3) = u
    vertices(idx + 4) = v
    vertices(idx + 5) = x2
    vertices(idx + 6) = y2
    vertices(idx + 7) = color
    vertices(idx + 8) = u
    vertices(idx + 9) = v2
    vertices(idx + 10) = x3
    vertices(idx + 11) = y3
    vertices(idx + 12) = color
    vertices(idx + 13) = u2
    vertices(idx + 14) = v2
    vertices(idx + 15) = x4
    vertices(idx + 16) = y4
    vertices(idx + 17) = color
    vertices(idx + 18) = u2
    vertices(idx + 19) = v
    this.idx = idx + 20
  }
  def draw(region: com.badlogic.gdx.graphics.g2d.TextureRegion, x: scala.Float, y: scala.Float, originX: scala.Float, originY: scala.Float, width: scala.Float, height: scala.Float, scaleX: scala.Float, scaleY: scala.Float, rotation: scala.Float, clockwise: scala.Boolean): scala.Unit = {
    if (!this.drawing) {
      throw new java.lang.IllegalStateException("SpriteBatch.begin must be called before draw.")
    } else ()
    val vertices: scala.Array[scala.Float] = this.vertices
    val texture: com.badlogic.gdx.graphics.Texture = region.texture
    if (texture != this.lastTexture) {
      this.switchTexture(texture)
    } else {
      if (this.idx == vertices.length) {
        this.flush()
      } else ()
    }
    val worldOriginX: scala.Float = x + originX
    val worldOriginY: scala.Float = y + originY
    var fx: scala.Float = -originX
    var fy: scala.Float = -originY
    var fx2: scala.Float = width - originX
    var fy2: scala.Float = height - originY
    if ((scaleX != 1) || (scaleY != 1)) {
      fx = fx * scaleX
      fy = fy * scaleY
      fx2 = fx2 * scaleX
      fy2 = fy2 * scaleY
    } else ()
    val p1x: scala.Float = fx
    val p1y: scala.Float = fy
    val p2x: scala.Float = fx
    val p2y: scala.Float = fy2
    val p3x: scala.Float = fx2
    val p3y: scala.Float = fy2
    val p4x: scala.Float = fx2
    val p4y: scala.Float = fy
    var x1: scala.Float = 0.0f
    var y1: scala.Float = 0.0f
    var x2: scala.Float = 0.0f
    var y2: scala.Float = 0.0f
    var x3: scala.Float = 0.0f
    var y3: scala.Float = 0.0f
    var x4: scala.Float = 0.0f
    var y4: scala.Float = 0.0f
    if (rotation != 0) {
      val cos: scala.Float = com.badlogic.gdx.math.MathUtils.cosDeg(rotation)
      val sin: scala.Float = com.badlogic.gdx.math.MathUtils.sinDeg(rotation)
      x1 = (cos * p1x) - (sin * p1y)
      y1 = (sin * p1x) + (cos * p1y)
      x2 = (cos * p2x) - (sin * p2y)
      y2 = (sin * p2x) + (cos * p2y)
      x3 = (cos * p3x) - (sin * p3y)
      y3 = (sin * p3x) + (cos * p3y)
      x4 = x1 + (x3 - x2)
      y4 = y3 - (y2 - y1)
    } else {
      x1 = p1x
      y1 = p1y
      x2 = p2x
      y2 = p2y
      x3 = p3x
      y3 = p3y
      x4 = p4x
      y4 = p4y
    }
    x1 = x1 + worldOriginX
    y1 = y1 + worldOriginY
    x2 = x2 + worldOriginX
    y2 = y2 + worldOriginY
    x3 = x3 + worldOriginX
    y3 = y3 + worldOriginY
    x4 = x4 + worldOriginX
    y4 = y4 + worldOriginY
    var u1: scala.Float = 0.0f
    var v1: scala.Float = 0.0f
    var u2: scala.Float = 0.0f
    var v2: scala.Float = 0.0f
    var u3: scala.Float = 0.0f
    var v3: scala.Float = 0.0f
    var u4: scala.Float = 0.0f
    var v4: scala.Float = 0.0f
    if (clockwise) {
      u1 = region.u2
      v1 = region.v2
      u2 = region.u
      v2 = region.v2
      u3 = region.u
      v3 = region.v
      u4 = region.u2
      v4 = region.v
    } else {
      u1 = region.u
      v1 = region.v
      u2 = region.u2
      v2 = region.v
      u3 = region.u2
      v3 = region.v2
      u4 = region.u
      v4 = region.v2
    }
    val color: scala.Float = this.colorPacked
    var idx: scala.Int = this.idx
    vertices(idx) = x1
    vertices(idx + 1) = y1
    vertices(idx + 2) = color
    vertices(idx + 3) = u1
    vertices(idx + 4) = v1
    vertices(idx + 5) = x2
    vertices(idx + 6) = y2
    vertices(idx + 7) = color
    vertices(idx + 8) = u2
    vertices(idx + 9) = v2
    vertices(idx + 10) = x3
    vertices(idx + 11) = y3
    vertices(idx + 12) = color
    vertices(idx + 13) = u3
    vertices(idx + 14) = v3
    vertices(idx + 15) = x4
    vertices(idx + 16) = y4
    vertices(idx + 17) = color
    vertices(idx + 18) = u4
    vertices(idx + 19) = v4
    this.idx = idx + 20
  }
  def draw(region: com.badlogic.gdx.graphics.g2d.TextureRegion, width: scala.Float, height: scala.Float, transform: com.badlogic.gdx.math.Affine2): scala.Unit = {
    if (!this.drawing) {
      throw new java.lang.IllegalStateException("SpriteBatch.begin must be called before draw.")
    } else ()
    val vertices: scala.Array[scala.Float] = this.vertices
    val texture: com.badlogic.gdx.graphics.Texture = region.texture
    if (texture != this.lastTexture) {
      this.switchTexture(texture)
    } else {
      if (this.idx == vertices.length) {
        this.flush()
      } else ()
    }
    val x1: scala.Float = transform.m02
    val y1: scala.Float = transform.m12
    val x2: scala.Float = (transform.m01 * height) + transform.m02
    val y2: scala.Float = (transform.m11 * height) + transform.m12
    val x3: scala.Float = ((transform.m00 * width) + (transform.m01 * height)) + transform.m02
    val y3: scala.Float = ((transform.m10 * width) + (transform.m11 * height)) + transform.m12
    val x4: scala.Float = (transform.m00 * width) + transform.m02
    val y4: scala.Float = (transform.m10 * width) + transform.m12
    val u: scala.Float = region.u
    val v: scala.Float = region.v2
    val u2: scala.Float = region.u2
    val v2: scala.Float = region.v
    val color: scala.Float = this.colorPacked
    var idx: scala.Int = this.idx
    vertices(idx) = x1
    vertices(idx + 1) = y1
    vertices(idx + 2) = color
    vertices(idx + 3) = u
    vertices(idx + 4) = v
    vertices(idx + 5) = x2
    vertices(idx + 6) = y2
    vertices(idx + 7) = color
    vertices(idx + 8) = u
    vertices(idx + 9) = v2
    vertices(idx + 10) = x3
    vertices(idx + 11) = y3
    vertices(idx + 12) = color
    vertices(idx + 13) = u2
    vertices(idx + 14) = v2
    vertices(idx + 15) = x4
    vertices(idx + 16) = y4
    vertices(idx + 17) = color
    vertices(idx + 18) = u2
    vertices(idx + 19) = v
    this.idx = idx + 20
  }
  def flush(): scala.Unit = {
    if (this.idx == 0) {
      return
    } else ()
    this.renderCalls = this.renderCalls + 1
    this.totalRenderCalls = this.totalRenderCalls + 1
    val spritesInBatch: scala.Int = this.idx / 20
    if (spritesInBatch > this.maxSpritesInBatch) {
      this.maxSpritesInBatch = spritesInBatch
    } else ()
    val count: scala.Int = spritesInBatch * 6
    this.lastTexture.bind()
    val mesh: com.badlogic.gdx.graphics.Mesh = this.mesh
    mesh.setVertices(this.vertices, 0, this.idx)
    if (this.currentDataType == com.badlogic.gdx.graphics.Mesh.VertexDataType.VertexArray) {
      val indicesBuffer: java.nio.Buffer = mesh.getIndicesBuffer(true).asInstanceOf[java.nio.Buffer]
      indicesBuffer.position(0)
      indicesBuffer.limit(count)
    } else ()
    if (this.blendingDisabled) {
      com.badlogic.gdx.Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND)
    } else {
      com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND)
      if (this.blendSrcFunc != (-1)) {
        com.badlogic.gdx.Gdx.gl.glBlendFuncSeparate(this.blendSrcFunc, this.blendDstFunc, this.blendSrcFuncAlpha, this.blendDstFuncAlpha)
      } else ()
    }
    mesh.render(if (this.customShader != null) this.customShader else this.shader, com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, 0, count)
    this.idx = 0
  }
  def disableBlending(): scala.Unit = {
    if (this.blendingDisabled) {
      return
    } else ()
    this.flush()
    this.blendingDisabled = true
  }
  def enableBlending(): scala.Unit = {
    if (!this.blendingDisabled) {
      return
    } else ()
    this.flush()
    this.blendingDisabled = false
  }
  def setBlendFunction(srcFunc: scala.Int, dstFunc: scala.Int): scala.Unit = {
    this.setBlendFunctionSeparate(srcFunc, dstFunc, srcFunc, dstFunc)
  }
  def setBlendFunctionSeparate(srcFuncColor: scala.Int, dstFuncColor: scala.Int, srcFuncAlpha: scala.Int, dstFuncAlpha: scala.Int): scala.Unit = {
    if ((((this.blendSrcFunc == srcFuncColor) && (this.blendDstFunc == dstFuncColor)) && (this.blendSrcFuncAlpha == srcFuncAlpha)) && (this.blendDstFuncAlpha == dstFuncAlpha)) {
      return
    } else ()
    this.flush()
    this.blendSrcFunc = srcFuncColor
    this.blendDstFunc = dstFuncColor
    this.blendSrcFuncAlpha = srcFuncAlpha
    this.blendDstFuncAlpha = dstFuncAlpha
  }
  def getBlendSrcFunc(): scala.Int = {
    return this.blendSrcFunc
  }
  def getBlendDstFunc(): scala.Int = {
    return this.blendDstFunc
  }
  def getBlendSrcFuncAlpha(): scala.Int = {
    return this.blendSrcFuncAlpha
  }
  def getBlendDstFuncAlpha(): scala.Int = {
    return this.blendDstFuncAlpha
  }
  def dispose(): scala.Unit = {
    this.mesh.dispose()
    if (this.ownsShader && (this.shader != null)) {
      this.shader.dispose()
    } else ()
  }
  def getProjectionMatrix(): com.badlogic.gdx.math.Matrix4 = {
    return this.projectionMatrix
  }
  def getTransformMatrix(): com.badlogic.gdx.math.Matrix4 = {
    return this.transformMatrix
  }
  def setProjectionMatrix(projection: com.badlogic.gdx.math.Matrix4): scala.Unit = {
    if (this.drawing) {
      this.flush()
    } else ()
    this.projectionMatrix.set(projection)
    if (this.drawing) {
      this.setupMatrices()
    } else ()
  }
  def setTransformMatrix(transform: com.badlogic.gdx.math.Matrix4): scala.Unit = {
    if (this.drawing) {
      this.flush()
    } else ()
    this.transformMatrix.set(transform)
    if (this.drawing) {
      this.setupMatrices()
    } else ()
  }
  def setupMatrices(): scala.Unit = {
    this.combinedMatrix.set(this.projectionMatrix).mul(this.transformMatrix)
    if (this.customShader != null) {
      this.customShader.setUniformMatrix("u_projTrans", this.combinedMatrix)
      this.customShader.setUniformi("u_texture", 0)
    } else {
      this.shader.setUniformMatrix("u_projTrans", this.combinedMatrix)
      this.shader.setUniformi("u_texture", 0)
    }
  }
  def switchTexture(texture: com.badlogic.gdx.graphics.Texture): scala.Unit = {
    this.flush()
    this.lastTexture = texture
    this.invTexWidth = 1.0f / texture.getWidth()
    this.invTexHeight = 1.0f / texture.getHeight()
  }
  def setShader(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram): scala.Unit = {
    if (shader == this.customShader) {
      return
    } else ()
    if (this.drawing) {
      this.flush()
    } else ()
    this.customShader = shader
    if (this.drawing) {
      if (this.customShader != null) {
        this.customShader.bind()
      } else {
        this.shader.bind()
      }
      this.setupMatrices()
    } else ()
  }
  def getShader(): com.badlogic.gdx.graphics.glutils.ShaderProgram = {
    if (this.customShader == null) {
      return this.shader
    } else ()
    return this.customShader
  }
  def isBlendingEnabled(): scala.Boolean = {
    return !this.blendingDisabled
  }
  def isDrawing(): scala.Boolean = {
    return this.drawing
  }
}
object SpriteBatch {
  var defaultVertexDataType: com.badlogic.gdx.graphics.Mesh.VertexDataType = com.badlogic.gdx.graphics.Mesh.VertexDataType.VertexBufferObject
  var overrideVertexType: com.badlogic.gdx.graphics.Mesh.VertexDataType = null
  def createDefaultShader(): com.badlogic.gdx.graphics.glutils.ShaderProgram = {
    val vertexShader: java.lang.String = (((((((((((((((((((((((("attribute vec4 " + com.badlogic.gdx.graphics.glutils.ShaderProgram.POSITION_ATTRIBUTE) + ";\n") + "attribute vec4 ") + com.badlogic.gdx.graphics.glutils.ShaderProgram.COLOR_ATTRIBUTE) + ";\n") + "attribute vec2 ") + com.badlogic.gdx.graphics.glutils.ShaderProgram.TEXCOORD_ATTRIBUTE) + "0;\n") + "uniform mat4 u_projTrans;\n") + "varying vec4 v_color;\n") + "varying vec2 v_texCoords;\n") + "\n") + "void main()\n") + "{\n") + "   v_color = ") + com.badlogic.gdx.graphics.glutils.ShaderProgram.COLOR_ATTRIBUTE) + ";\n") + "   v_color.a = v_color.a * (255.0/254.0);\n") + "   v_texCoords = ") + com.badlogic.gdx.graphics.glutils.ShaderProgram.TEXCOORD_ATTRIBUTE) + "0;\n") + "   gl_Position =  u_projTrans * ") + com.badlogic.gdx.graphics.glutils.ShaderProgram.POSITION_ATTRIBUTE) + ";\n") + "}\n"
    val fragmentShader: java.lang.String = ((((((((((("#ifdef GL_ES\n" + "#define LOWP lowp\n") + "precision mediump float;\n") + "#else\n") + "#define LOWP \n") + "#endif\n") + "varying LOWP vec4 v_color;\n") + "varying vec2 v_texCoords;\n") + "uniform sampler2D u_texture;\n") + "void main()\n") + "{\n") + "  gl_FragColor = v_color * texture2D(u_texture, v_texCoords);\n") + "}"
    val shader: com.badlogic.gdx.graphics.glutils.ShaderProgram = new com.badlogic.gdx.graphics.glutils.ShaderProgram(vertexShader, fragmentShader)
    if (!shader.isCompiled()) {
      throw new java.lang.IllegalArgumentException("Error compiling shader: " + shader.getLog())
    } else ()
    return shader
  }
}