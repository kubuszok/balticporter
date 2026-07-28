package com.badlogic.gdx.graphics.g2d

class PolygonSpriteBatch(maxVertices: scala.Int, maxTriangles: scala.Int, defaultShader: com.badlogic.gdx.graphics.glutils.ShaderProgram) extends com.badlogic.gdx.graphics.g2d.PolygonBatch {
  private var mesh: com.badlogic.gdx.graphics.Mesh = null.asInstanceOf[com.badlogic.gdx.graphics.Mesh]
  private var vertices: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  private var triangles: scala.Array[scala.Short] = null.asInstanceOf[scala.Array[scala.Short]]
  private var vertexIndex: scala.Int = 0
  private var triangleIndex: scala.Int = 0
  private var lastTexture: com.badlogic.gdx.graphics.Texture = null.asInstanceOf[com.badlogic.gdx.graphics.Texture]
  private var invTexWidth: scala.Float = 0
  private var invTexHeight: scala.Float = 0
  private var drawing: scala.Boolean = false
  private final val transformMatrix: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
  private final val projectionMatrix: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
  private final val combinedMatrix: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
  private var blendingDisabled: scala.Boolean = false
  private var blendSrcFunc: scala.Int = com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA
  private var blendDstFunc: scala.Int = com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA
  private var blendSrcFuncAlpha: scala.Int = com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA
  private var blendDstFuncAlpha: scala.Int = com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA
  private var shader: com.badlogic.gdx.graphics.glutils.ShaderProgram = null.asInstanceOf[com.badlogic.gdx.graphics.glutils.ShaderProgram]
  private var customShader: com.badlogic.gdx.graphics.glutils.ShaderProgram = null.asInstanceOf[com.badlogic.gdx.graphics.glutils.ShaderProgram]
  private var ownsShader: scala.Boolean = false
  private final val color: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color(1, 1, 1, 1)
  var colorPacked: scala.Float = com.badlogic.gdx.graphics.Color.WHITE_FLOAT_BITS
  var renderCalls: scala.Int = 0
  var totalRenderCalls: scala.Int = 0
  var maxTrianglesInBatch: scala.Int = 0
  var vertexDataType: com.badlogic.gdx.graphics.Mesh.VertexDataType = com.badlogic.gdx.graphics.Mesh.VertexDataType.VertexArray
  def this(size: scala.Int, defaultShader: com.badlogic.gdx.graphics.glutils.ShaderProgram) = {
    this(size, size * 2, defaultShader)
  }
  def this() = {
    this(2000, null)
  }
  def this(size: scala.Int) = {
    this(size, size * 2, null)
  }
  if (maxVertices > 32767) {
    throw new java.lang.IllegalArgumentException("Can't have more than 32767 vertices per batch: " + maxVertices)
  } else ()
  if (com.badlogic.gdx.Gdx.gl30 != null) {
    vertexDataType = com.badlogic.gdx.graphics.Mesh.VertexDataType.VertexBufferObjectWithVAO
  } else ()
  this.mesh = new com.badlogic.gdx.graphics.Mesh(vertexDataType, false, maxVertices, maxTriangles * 3, scala.Array[com.badlogic.gdx.graphics.VertexAttribute](new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Position, 2, com.badlogic.gdx.graphics.glutils.ShaderProgram.POSITION_ATTRIBUTE), new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorPacked, 4, com.badlogic.gdx.graphics.glutils.ShaderProgram.COLOR_ATTRIBUTE), new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.TextureCoordinates, 2, com.badlogic.gdx.graphics.glutils.ShaderProgram.TEXCOORD_ATTRIBUTE + "0")))
  this.vertices = new scala.Array[scala.Float](maxVertices * com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE)
  this.triangles = new scala.Array[scala.Short](maxTriangles * 3)
  if (defaultShader == null) {
    this.shader = com.badlogic.gdx.graphics.g2d.SpriteBatch.createDefaultShader()
    this.ownsShader = true
  } else {
    this.shader = defaultShader
  }
  this.projectionMatrix.setToOrtho2D(0, 0, com.badlogic.gdx.Gdx.graphics.getWidth(), com.badlogic.gdx.Gdx.graphics.getHeight())
  @java.lang.Override
  def begin(): scala.Unit = {
    if (this.drawing) {
      throw new java.lang.IllegalStateException("PolygonSpriteBatch.end must be called before begin.")
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
  @java.lang.Override
  def `end`(): scala.Unit = {
    if (!this.drawing) {
      throw new java.lang.IllegalStateException("PolygonSpriteBatch.begin must be called before end.")
    } else ()
    if (this.vertexIndex > 0) {
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
  @java.lang.Override
  def setColor(tint: com.badlogic.gdx.graphics.Color): scala.Unit = {
    this.color.set(tint)
    this.colorPacked = tint.toFloatBits()
  }
  @java.lang.Override
  def setColor(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): scala.Unit = {
    this.color.set(r, g, b, a)
    this.colorPacked = this.color.toFloatBits()
  }
  @java.lang.Override
  def setPackedColor(packedColor: scala.Float): scala.Unit = {
    com.badlogic.gdx.graphics.Color.abgr8888ToColor(this.color, packedColor)
    this.colorPacked = packedColor
  }
  @java.lang.Override
  def getColor(): com.badlogic.gdx.graphics.Color = {
    return this.color
  }
  @java.lang.Override
  def getPackedColor(): scala.Float = {
    return this.colorPacked
  }
  @java.lang.Override
  def draw(region: com.badlogic.gdx.graphics.g2d.PolygonRegion, x: scala.Float, y: scala.Float): scala.Unit = {
    if (!this.drawing) {
      throw new java.lang.IllegalStateException("PolygonSpriteBatch.begin must be called before draw.")
    } else ()
    val triangles: scala.Array[scala.Short] = this.triangles
    val regionTriangles: scala.Array[scala.Short] = region.triangles
    val regionTrianglesLength: scala.Int = regionTriangles.length
    val regionVertices: scala.Array[scala.Float] = region.vertices
    val regionVerticesLength: scala.Int = regionVertices.length
    val texture: com.badlogic.gdx.graphics.Texture = region.region.texture
    if (texture != this.lastTexture) {
      this.switchTexture(texture)
    } else {
      if (((this.triangleIndex + regionTrianglesLength) > triangles.length) || ((this.vertexIndex + ((regionVerticesLength * com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE) / 2)) > this.vertices.length)) {
        this.flush()
      } else ()
    }
    var triangleIndex: scala.Int = this.triangleIndex
    var vertexIndex: scala.Int = this.vertexIndex
    val startVertex: scala.Int = vertexIndex / com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE;
    { var i: scala.Int = 0; while (i < regionTrianglesLength) { {
      triangles({ triangleIndex += 1; triangleIndex }) = (regionTriangles(i) + startVertex).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    }; i = i + 1 } }
    this.triangleIndex = triangleIndex
    val vertices: scala.Array[scala.Float] = this.vertices
    val color: scala.Float = this.colorPacked
    val textureCoords: scala.Array[scala.Float] = region.textureCoords;
    { var i: scala.Int = 0; while (i < regionVerticesLength) { {
      vertices({ vertexIndex += 1; vertexIndex }) = regionVertices(i) + x
      vertices({ vertexIndex += 1; vertexIndex }) = regionVertices(i + 1) + y
      vertices({ vertexIndex += 1; vertexIndex }) = color
      vertices({ vertexIndex += 1; vertexIndex }) = textureCoords(i)
      vertices({ vertexIndex += 1; vertexIndex }) = textureCoords(i + 1)
    }; i = i + 2 } }
    this.vertexIndex = vertexIndex
  }
  @java.lang.Override
  def draw(region: com.badlogic.gdx.graphics.g2d.PolygonRegion, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    if (!this.drawing) {
      throw new java.lang.IllegalStateException("PolygonSpriteBatch.begin must be called before draw.")
    } else ()
    val triangles: scala.Array[scala.Short] = this.triangles
    val regionTriangles: scala.Array[scala.Short] = region.triangles
    val regionTrianglesLength: scala.Int = regionTriangles.length
    val regionVertices: scala.Array[scala.Float] = region.vertices
    val regionVerticesLength: scala.Int = regionVertices.length
    val textureRegion: com.badlogic.gdx.graphics.g2d.TextureRegion = region.region
    val texture: com.badlogic.gdx.graphics.Texture = textureRegion.texture
    if (texture != this.lastTexture) {
      this.switchTexture(texture)
    } else {
      if (((this.triangleIndex + regionTrianglesLength) > triangles.length) || ((this.vertexIndex + ((regionVerticesLength * com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE) / 2)) > this.vertices.length)) {
        this.flush()
      } else ()
    }
    var triangleIndex: scala.Int = this.triangleIndex
    var vertexIndex: scala.Int = this.vertexIndex
    val startVertex: scala.Int = vertexIndex / com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE;
    { var i: scala.Int = 0; val n: scala.Int = regionTriangles.length; while (i < n) { {
      triangles({ triangleIndex += 1; triangleIndex }) = (regionTriangles(i) + startVertex).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    }; i = i + 1 } }
    this.triangleIndex = triangleIndex
    val vertices: scala.Array[scala.Float] = this.vertices
    val color: scala.Float = this.colorPacked
    val textureCoords: scala.Array[scala.Float] = region.textureCoords
    val sX: scala.Float = width / textureRegion.regionWidth
    val sY: scala.Float = height / textureRegion.regionHeight;
    { var i: scala.Int = 0; while (i < regionVerticesLength) { {
      vertices({ vertexIndex += 1; vertexIndex }) = (regionVertices(i) * sX) + x
      vertices({ vertexIndex += 1; vertexIndex }) = (regionVertices(i + 1) * sY) + y
      vertices({ vertexIndex += 1; vertexIndex }) = color
      vertices({ vertexIndex += 1; vertexIndex }) = textureCoords(i)
      vertices({ vertexIndex += 1; vertexIndex }) = textureCoords(i + 1)
    }; i = i + 2 } }
    this.vertexIndex = vertexIndex
  }
  @java.lang.Override
  def draw(region: com.badlogic.gdx.graphics.g2d.PolygonRegion, x: scala.Float, y: scala.Float, originX: scala.Float, originY: scala.Float, width: scala.Float, height: scala.Float, scaleX: scala.Float, scaleY: scala.Float, rotation: scala.Float): scala.Unit = {
    if (!this.drawing) {
      throw new java.lang.IllegalStateException("PolygonSpriteBatch.begin must be called before draw.")
    } else ()
    val triangles: scala.Array[scala.Short] = this.triangles
    val regionTriangles: scala.Array[scala.Short] = region.triangles
    val regionTrianglesLength: scala.Int = regionTriangles.length
    val regionVertices: scala.Array[scala.Float] = region.vertices
    val regionVerticesLength: scala.Int = regionVertices.length
    val textureRegion: com.badlogic.gdx.graphics.g2d.TextureRegion = region.region
    val texture: com.badlogic.gdx.graphics.Texture = textureRegion.texture
    if (texture != this.lastTexture) {
      this.switchTexture(texture)
    } else {
      if (((this.triangleIndex + regionTrianglesLength) > triangles.length) || ((this.vertexIndex + ((regionVerticesLength * com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE) / 2)) > this.vertices.length)) {
        this.flush()
      } else ()
    }
    var triangleIndex: scala.Int = this.triangleIndex
    var vertexIndex: scala.Int = this.vertexIndex
    val startVertex: scala.Int = vertexIndex / com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE;
    { var i: scala.Int = 0; while (i < regionTrianglesLength) { {
      triangles({ triangleIndex += 1; triangleIndex }) = (regionTriangles(i) + startVertex).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    }; i = i + 1 } }
    this.triangleIndex = triangleIndex
    val vertices: scala.Array[scala.Float] = this.vertices
    val color: scala.Float = this.colorPacked
    val textureCoords: scala.Array[scala.Float] = region.textureCoords
    val worldOriginX: scala.Float = x + originX
    val worldOriginY: scala.Float = y + originY
    val sX: scala.Float = width / textureRegion.regionWidth
    val sY: scala.Float = height / textureRegion.regionHeight
    val cos: scala.Float = com.badlogic.gdx.math.MathUtils.cosDeg(rotation)
    val sin: scala.Float = com.badlogic.gdx.math.MathUtils.sinDeg(rotation)
    var fx: scala.Float = 0.0f
    var fy: scala.Float = 0.0f;
    { var i: scala.Int = 0; while (i < regionVerticesLength) { {
      fx = ((regionVertices(i) * sX) - originX) * scaleX
      fy = ((regionVertices(i + 1) * sY) - originY) * scaleY
      vertices({ vertexIndex += 1; vertexIndex }) = ((cos * fx) - (sin * fy)) + worldOriginX
      vertices({ vertexIndex += 1; vertexIndex }) = ((sin * fx) + (cos * fy)) + worldOriginY
      vertices({ vertexIndex += 1; vertexIndex }) = color
      vertices({ vertexIndex += 1; vertexIndex }) = textureCoords(i)
      vertices({ vertexIndex += 1; vertexIndex }) = textureCoords(i + 1)
    }; i = i + 2 } }
    this.vertexIndex = vertexIndex
  }
  @java.lang.Override
  def draw(texture: com.badlogic.gdx.graphics.Texture, polygonVertices: scala.Array[scala.Float], verticesOffset: scala.Int, verticesCount: scala.Int, polygonTriangles: scala.Array[scala.Short], trianglesOffset: scala.Int, trianglesCount: scala.Int): scala.Unit = {
    if (!this.drawing) {
      throw new java.lang.IllegalStateException("PolygonSpriteBatch.begin must be called before draw.")
    } else ()
    val triangles: scala.Array[scala.Short] = this.triangles
    val vertices: scala.Array[scala.Float] = this.vertices
    if (texture != this.lastTexture) {
      this.switchTexture(texture)
    } else {
      if (((this.triangleIndex + trianglesCount) > triangles.length) || ((this.vertexIndex + verticesCount) > vertices.length)) {
        this.flush()
      } else ()
    }
    var triangleIndex: scala.Int = this.triangleIndex
    var vertexIndex: scala.Int = this.vertexIndex
    val startVertex: scala.Int = vertexIndex / com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE;
    { var i: scala.Int = trianglesOffset; val n: scala.Int = i + trianglesCount; while (i < n) { {
      triangles({ triangleIndex += 1; triangleIndex }) = (polygonTriangles(i) + startVertex).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    }; i = i + 1 } }
    this.triangleIndex = triangleIndex
    java.lang.System.arraycopy(polygonVertices, verticesOffset, vertices, vertexIndex, verticesCount)
    this.vertexIndex = this.vertexIndex + verticesCount
  }
  @java.lang.Override
  def draw(texture: com.badlogic.gdx.graphics.Texture, x: scala.Float, y: scala.Float, originX: scala.Float, originY: scala.Float, width: scala.Float, height: scala.Float, scaleX: scala.Float, scaleY: scala.Float, rotation: scala.Float, srcX: scala.Int, srcY: scala.Int, srcWidth: scala.Int, srcHeight: scala.Int, flipX: scala.Boolean, flipY: scala.Boolean): scala.Unit = {
    if (!this.drawing) {
      throw new java.lang.IllegalStateException("PolygonSpriteBatch.begin must be called before draw.")
    } else ()
    val triangles: scala.Array[scala.Short] = this.triangles
    val vertices: scala.Array[scala.Float] = this.vertices
    if (texture != this.lastTexture) {
      this.switchTexture(texture)
    } else {
      if (((this.triangleIndex + 6) > triangles.length) || ((this.vertexIndex + com.badlogic.gdx.graphics.g2d.Sprite.SPRITE_SIZE) > vertices.length)) {
        this.flush()
      } else ()
    }
    var triangleIndex: scala.Int = this.triangleIndex
    val startVertex: scala.Int = this.vertexIndex / com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE
    triangles({ triangleIndex += 1; triangleIndex }) = startVertex.asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 1).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 2).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 2).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 3).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = startVertex.asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    this.triangleIndex = triangleIndex
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
    var idx: scala.Int = this.vertexIndex
    vertices({ idx += 1; idx }) = x1
    vertices({ idx += 1; idx }) = y1
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u
    vertices({ idx += 1; idx }) = v
    vertices({ idx += 1; idx }) = x2
    vertices({ idx += 1; idx }) = y2
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u
    vertices({ idx += 1; idx }) = v2
    vertices({ idx += 1; idx }) = x3
    vertices({ idx += 1; idx }) = y3
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u2
    vertices({ idx += 1; idx }) = v2
    vertices({ idx += 1; idx }) = x4
    vertices({ idx += 1; idx }) = y4
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u2
    vertices({ idx += 1; idx }) = v
    this.vertexIndex = idx
  }
  @java.lang.Override
  def draw(texture: com.badlogic.gdx.graphics.Texture, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float, srcX: scala.Int, srcY: scala.Int, srcWidth: scala.Int, srcHeight: scala.Int, flipX: scala.Boolean, flipY: scala.Boolean): scala.Unit = {
    if (!this.drawing) {
      throw new java.lang.IllegalStateException("PolygonSpriteBatch.begin must be called before draw.")
    } else ()
    val triangles: scala.Array[scala.Short] = this.triangles
    val vertices: scala.Array[scala.Float] = this.vertices
    if (texture != this.lastTexture) {
      this.switchTexture(texture)
    } else {
      if (((this.triangleIndex + 6) > triangles.length) || ((this.vertexIndex + com.badlogic.gdx.graphics.g2d.Sprite.SPRITE_SIZE) > vertices.length)) {
        this.flush()
      } else ()
    }
    var triangleIndex: scala.Int = this.triangleIndex
    val startVertex: scala.Int = this.vertexIndex / com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE
    triangles({ triangleIndex += 1; triangleIndex }) = startVertex.asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 1).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 2).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 2).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 3).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = startVertex.asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    this.triangleIndex = triangleIndex
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
    var idx: scala.Int = this.vertexIndex
    vertices({ idx += 1; idx }) = x
    vertices({ idx += 1; idx }) = y
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u
    vertices({ idx += 1; idx }) = v
    vertices({ idx += 1; idx }) = x
    vertices({ idx += 1; idx }) = fy2
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u
    vertices({ idx += 1; idx }) = v2
    vertices({ idx += 1; idx }) = fx2
    vertices({ idx += 1; idx }) = fy2
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u2
    vertices({ idx += 1; idx }) = v2
    vertices({ idx += 1; idx }) = fx2
    vertices({ idx += 1; idx }) = y
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u2
    vertices({ idx += 1; idx }) = v
    this.vertexIndex = idx
  }
  @java.lang.Override
  def draw(texture: com.badlogic.gdx.graphics.Texture, x: scala.Float, y: scala.Float, srcX: scala.Int, srcY: scala.Int, srcWidth: scala.Int, srcHeight: scala.Int): scala.Unit = {
    if (!this.drawing) {
      throw new java.lang.IllegalStateException("PolygonSpriteBatch.begin must be called before draw.")
    } else ()
    val triangles: scala.Array[scala.Short] = this.triangles
    val vertices: scala.Array[scala.Float] = this.vertices
    if (texture != this.lastTexture) {
      this.switchTexture(texture)
    } else {
      if (((this.triangleIndex + 6) > triangles.length) || ((this.vertexIndex + com.badlogic.gdx.graphics.g2d.Sprite.SPRITE_SIZE) > vertices.length)) {
        this.flush()
      } else ()
    }
    var triangleIndex: scala.Int = this.triangleIndex
    val startVertex: scala.Int = this.vertexIndex / com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE
    triangles({ triangleIndex += 1; triangleIndex }) = startVertex.asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 1).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 2).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 2).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 3).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = startVertex.asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    this.triangleIndex = triangleIndex
    val u: scala.Float = srcX * this.invTexWidth
    val v: scala.Float = (srcY + srcHeight) * this.invTexHeight
    val u2: scala.Float = (srcX + srcWidth) * this.invTexWidth
    val v2: scala.Float = srcY * this.invTexHeight
    val fx2: scala.Float = x + srcWidth
    val fy2: scala.Float = y + srcHeight
    val color: scala.Float = this.colorPacked
    var idx: scala.Int = this.vertexIndex
    vertices({ idx += 1; idx }) = x
    vertices({ idx += 1; idx }) = y
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u
    vertices({ idx += 1; idx }) = v
    vertices({ idx += 1; idx }) = x
    vertices({ idx += 1; idx }) = fy2
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u
    vertices({ idx += 1; idx }) = v2
    vertices({ idx += 1; idx }) = fx2
    vertices({ idx += 1; idx }) = fy2
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u2
    vertices({ idx += 1; idx }) = v2
    vertices({ idx += 1; idx }) = fx2
    vertices({ idx += 1; idx }) = y
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u2
    vertices({ idx += 1; idx }) = v
    this.vertexIndex = idx
  }
  @java.lang.Override
  def draw(texture: com.badlogic.gdx.graphics.Texture, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float, u: scala.Float, v: scala.Float, u2: scala.Float, v2: scala.Float): scala.Unit = {
    if (!this.drawing) {
      throw new java.lang.IllegalStateException("PolygonSpriteBatch.begin must be called before draw.")
    } else ()
    val triangles: scala.Array[scala.Short] = this.triangles
    val vertices: scala.Array[scala.Float] = this.vertices
    if (texture != this.lastTexture) {
      this.switchTexture(texture)
    } else {
      if (((this.triangleIndex + 6) > triangles.length) || ((this.vertexIndex + com.badlogic.gdx.graphics.g2d.Sprite.SPRITE_SIZE) > vertices.length)) {
        this.flush()
      } else ()
    }
    var triangleIndex: scala.Int = this.triangleIndex
    val startVertex: scala.Int = this.vertexIndex / com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE
    triangles({ triangleIndex += 1; triangleIndex }) = startVertex.asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 1).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 2).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 2).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 3).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = startVertex.asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    this.triangleIndex = triangleIndex
    val fx2: scala.Float = x + width
    val fy2: scala.Float = y + height
    val color: scala.Float = this.colorPacked
    var idx: scala.Int = this.vertexIndex
    vertices({ idx += 1; idx }) = x
    vertices({ idx += 1; idx }) = y
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u
    vertices({ idx += 1; idx }) = v
    vertices({ idx += 1; idx }) = x
    vertices({ idx += 1; idx }) = fy2
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u
    vertices({ idx += 1; idx }) = v2
    vertices({ idx += 1; idx }) = fx2
    vertices({ idx += 1; idx }) = fy2
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u2
    vertices({ idx += 1; idx }) = v2
    vertices({ idx += 1; idx }) = fx2
    vertices({ idx += 1; idx }) = y
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u2
    vertices({ idx += 1; idx }) = v
    this.vertexIndex = idx
  }
  @java.lang.Override
  def draw(texture: com.badlogic.gdx.graphics.Texture, x: scala.Float, y: scala.Float): scala.Unit = {
    this.draw(texture, x, y, texture.getWidth(), texture.getHeight())
  }
  @java.lang.Override
  def draw(texture: com.badlogic.gdx.graphics.Texture, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    if (!this.drawing) {
      throw new java.lang.IllegalStateException("PolygonSpriteBatch.begin must be called before draw.")
    } else ()
    val triangles: scala.Array[scala.Short] = this.triangles
    val vertices: scala.Array[scala.Float] = this.vertices
    if (texture != this.lastTexture) {
      this.switchTexture(texture)
    } else {
      if (((this.triangleIndex + 6) > triangles.length) || ((this.vertexIndex + com.badlogic.gdx.graphics.g2d.Sprite.SPRITE_SIZE) > vertices.length)) {
        this.flush()
      } else ()
    }
    var triangleIndex: scala.Int = this.triangleIndex
    val startVertex: scala.Int = this.vertexIndex / com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE
    triangles({ triangleIndex += 1; triangleIndex }) = startVertex.asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 1).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 2).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 2).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 3).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = startVertex.asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    this.triangleIndex = triangleIndex
    val fx2: scala.Float = x + width
    val fy2: scala.Float = y + height
    val u: scala.Float = 0
    val v: scala.Float = 1
    val u2: scala.Float = 1
    val v2: scala.Float = 0
    val color: scala.Float = this.colorPacked
    var idx: scala.Int = this.vertexIndex
    vertices({ idx += 1; idx }) = x
    vertices({ idx += 1; idx }) = y
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u
    vertices({ idx += 1; idx }) = v
    vertices({ idx += 1; idx }) = x
    vertices({ idx += 1; idx }) = fy2
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u
    vertices({ idx += 1; idx }) = v2
    vertices({ idx += 1; idx }) = fx2
    vertices({ idx += 1; idx }) = fy2
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u2
    vertices({ idx += 1; idx }) = v2
    vertices({ idx += 1; idx }) = fx2
    vertices({ idx += 1; idx }) = y
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u2
    vertices({ idx += 1; idx }) = v
    this.vertexIndex = idx
  }
  @java.lang.Override
  def draw(texture: com.badlogic.gdx.graphics.Texture, spriteVertices: scala.Array[scala.Float], offset$arg: scala.Int, count$arg: scala.Int): scala.Unit = {
    var offset: scala.Int = offset$arg
    var count: scala.Int = count$arg
    if (!this.drawing) {
      throw new java.lang.IllegalStateException("PolygonSpriteBatch.begin must be called before draw.")
    } else ()
    val triangles: scala.Array[scala.Short] = this.triangles
    val vertices: scala.Array[scala.Float] = this.vertices
    var triangleCount: scala.Int = (count / com.badlogic.gdx.graphics.g2d.Sprite.SPRITE_SIZE) * 6
    var batch: scala.Int = 0
    if (texture != this.lastTexture) {
      this.switchTexture(texture)
      batch = java.lang.Math.min(java.lang.Math.min(count, vertices.length - (vertices.length % com.badlogic.gdx.graphics.g2d.Sprite.SPRITE_SIZE)), (triangles.length / 6) * com.badlogic.gdx.graphics.g2d.Sprite.SPRITE_SIZE)
      triangleCount = (batch / com.badlogic.gdx.graphics.g2d.Sprite.SPRITE_SIZE) * 6
    } else {
      if (((this.triangleIndex + triangleCount) > triangles.length) || ((this.vertexIndex + count) > vertices.length)) {
        this.flush()
        batch = java.lang.Math.min(java.lang.Math.min(count, vertices.length - (vertices.length % com.badlogic.gdx.graphics.g2d.Sprite.SPRITE_SIZE)), (triangles.length / 6) * com.badlogic.gdx.graphics.g2d.Sprite.SPRITE_SIZE)
        triangleCount = (batch / com.badlogic.gdx.graphics.g2d.Sprite.SPRITE_SIZE) * 6
      } else {
        batch = count
      }
    }
    var vertexIndex: scala.Int = this.vertexIndex
    var vertex: scala.Short = (vertexIndex / com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    var triangleIndex: scala.Int = this.triangleIndex;
    { val n: scala.Int = triangleIndex + triangleCount; while (triangleIndex < n) { {
      triangles(triangleIndex) = vertex
      triangles(triangleIndex + 1) = (vertex + 1).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
      triangles(triangleIndex + 2) = (vertex + 2).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
      triangles(triangleIndex + 3) = (vertex + 2).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
      triangles(triangleIndex + 4) = (vertex + 3).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
      triangles(triangleIndex + 5) = vertex
    }; triangleIndex = triangleIndex + 6; vertex = (vertex + 4).asInstanceOf[scala.Short] } }
    while (true) {
      java.lang.System.arraycopy(spriteVertices, offset, vertices, vertexIndex, batch)
      this.vertexIndex = vertexIndex + batch
      this.triangleIndex = triangleIndex
      count = count - batch
      if (count == 0) {
        /* break */ ()
      } else ()
      offset = offset + batch
      this.flush()
      vertexIndex = 0
      if (batch > count) {
        batch = java.lang.Math.min(count, (triangles.length / 6) * com.badlogic.gdx.graphics.g2d.Sprite.SPRITE_SIZE)
        triangleIndex = (batch / com.badlogic.gdx.graphics.g2d.Sprite.SPRITE_SIZE) * 6
      } else ()
    }
  }
  @java.lang.Override
  def draw(region: com.badlogic.gdx.graphics.g2d.TextureRegion, x: scala.Float, y: scala.Float): scala.Unit = {
    this.draw(region, x, y, region.getRegionWidth(), region.getRegionHeight())
  }
  @java.lang.Override
  def draw(region: com.badlogic.gdx.graphics.g2d.TextureRegion, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    if (!this.drawing) {
      throw new java.lang.IllegalStateException("PolygonSpriteBatch.begin must be called before draw.")
    } else ()
    val triangles: scala.Array[scala.Short] = this.triangles
    val vertices: scala.Array[scala.Float] = this.vertices
    val texture: com.badlogic.gdx.graphics.Texture = region.texture
    if (texture != this.lastTexture) {
      this.switchTexture(texture)
    } else {
      if (((this.triangleIndex + 6) > triangles.length) || ((this.vertexIndex + com.badlogic.gdx.graphics.g2d.Sprite.SPRITE_SIZE) > vertices.length)) {
        this.flush()
      } else ()
    }
    var triangleIndex: scala.Int = this.triangleIndex
    val startVertex: scala.Int = this.vertexIndex / com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE
    triangles({ triangleIndex += 1; triangleIndex }) = startVertex.asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 1).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 2).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 2).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 3).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = startVertex.asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    this.triangleIndex = triangleIndex
    val fx2: scala.Float = x + width
    val fy2: scala.Float = y + height
    val u: scala.Float = region.u
    val v: scala.Float = region.v2
    val u2: scala.Float = region.u2
    val v2: scala.Float = region.v
    val color: scala.Float = this.colorPacked
    var idx: scala.Int = this.vertexIndex
    vertices({ idx += 1; idx }) = x
    vertices({ idx += 1; idx }) = y
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u
    vertices({ idx += 1; idx }) = v
    vertices({ idx += 1; idx }) = x
    vertices({ idx += 1; idx }) = fy2
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u
    vertices({ idx += 1; idx }) = v2
    vertices({ idx += 1; idx }) = fx2
    vertices({ idx += 1; idx }) = fy2
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u2
    vertices({ idx += 1; idx }) = v2
    vertices({ idx += 1; idx }) = fx2
    vertices({ idx += 1; idx }) = y
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u2
    vertices({ idx += 1; idx }) = v
    this.vertexIndex = idx
  }
  @java.lang.Override
  def draw(region: com.badlogic.gdx.graphics.g2d.TextureRegion, x: scala.Float, y: scala.Float, originX: scala.Float, originY: scala.Float, width: scala.Float, height: scala.Float, scaleX: scala.Float, scaleY: scala.Float, rotation: scala.Float): scala.Unit = {
    if (!this.drawing) {
      throw new java.lang.IllegalStateException("PolygonSpriteBatch.begin must be called before draw.")
    } else ()
    val triangles: scala.Array[scala.Short] = this.triangles
    val vertices: scala.Array[scala.Float] = this.vertices
    val texture: com.badlogic.gdx.graphics.Texture = region.texture
    if (texture != this.lastTexture) {
      this.switchTexture(texture)
    } else {
      if (((this.triangleIndex + 6) > triangles.length) || ((this.vertexIndex + com.badlogic.gdx.graphics.g2d.Sprite.SPRITE_SIZE) > vertices.length)) {
        this.flush()
      } else ()
    }
    var triangleIndex: scala.Int = this.triangleIndex
    val startVertex: scala.Int = this.vertexIndex / com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE
    triangles({ triangleIndex += 1; triangleIndex }) = startVertex.asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 1).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 2).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 2).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 3).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = startVertex.asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    this.triangleIndex = triangleIndex
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
    var idx: scala.Int = this.vertexIndex
    vertices({ idx += 1; idx }) = x1
    vertices({ idx += 1; idx }) = y1
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u
    vertices({ idx += 1; idx }) = v
    vertices({ idx += 1; idx }) = x2
    vertices({ idx += 1; idx }) = y2
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u
    vertices({ idx += 1; idx }) = v2
    vertices({ idx += 1; idx }) = x3
    vertices({ idx += 1; idx }) = y3
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u2
    vertices({ idx += 1; idx }) = v2
    vertices({ idx += 1; idx }) = x4
    vertices({ idx += 1; idx }) = y4
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u2
    vertices({ idx += 1; idx }) = v
    this.vertexIndex = idx
  }
  @java.lang.Override
  def draw(region: com.badlogic.gdx.graphics.g2d.TextureRegion, x: scala.Float, y: scala.Float, originX: scala.Float, originY: scala.Float, width: scala.Float, height: scala.Float, scaleX: scala.Float, scaleY: scala.Float, rotation: scala.Float, clockwise: scala.Boolean): scala.Unit = {
    if (!this.drawing) {
      throw new java.lang.IllegalStateException("PolygonSpriteBatch.begin must be called before draw.")
    } else ()
    val triangles: scala.Array[scala.Short] = this.triangles
    val vertices: scala.Array[scala.Float] = this.vertices
    val texture: com.badlogic.gdx.graphics.Texture = region.texture
    if (texture != this.lastTexture) {
      this.switchTexture(texture)
    } else {
      if (((this.triangleIndex + 6) > triangles.length) || ((this.vertexIndex + com.badlogic.gdx.graphics.g2d.Sprite.SPRITE_SIZE) > vertices.length)) {
        this.flush()
      } else ()
    }
    var triangleIndex: scala.Int = this.triangleIndex
    val startVertex: scala.Int = this.vertexIndex / com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE
    triangles({ triangleIndex += 1; triangleIndex }) = startVertex.asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 1).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 2).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 2).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 3).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = startVertex.asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    this.triangleIndex = triangleIndex
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
    var idx: scala.Int = this.vertexIndex
    vertices({ idx += 1; idx }) = x1
    vertices({ idx += 1; idx }) = y1
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u1
    vertices({ idx += 1; idx }) = v1
    vertices({ idx += 1; idx }) = x2
    vertices({ idx += 1; idx }) = y2
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u2
    vertices({ idx += 1; idx }) = v2
    vertices({ idx += 1; idx }) = x3
    vertices({ idx += 1; idx }) = y3
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u3
    vertices({ idx += 1; idx }) = v3
    vertices({ idx += 1; idx }) = x4
    vertices({ idx += 1; idx }) = y4
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u4
    vertices({ idx += 1; idx }) = v4
    this.vertexIndex = idx
  }
  @java.lang.Override
  def draw(region: com.badlogic.gdx.graphics.g2d.TextureRegion, width: scala.Float, height: scala.Float, transform: com.badlogic.gdx.math.Affine2): scala.Unit = {
    if (!this.drawing) {
      throw new java.lang.IllegalStateException("PolygonSpriteBatch.begin must be called before draw.")
    } else ()
    val triangles: scala.Array[scala.Short] = this.triangles
    val vertices: scala.Array[scala.Float] = this.vertices
    val texture: com.badlogic.gdx.graphics.Texture = region.texture
    if (texture != this.lastTexture) {
      this.switchTexture(texture)
    } else {
      if (((this.triangleIndex + 6) > triangles.length) || ((this.vertexIndex + com.badlogic.gdx.graphics.g2d.Sprite.SPRITE_SIZE) > vertices.length)) {
        this.flush()
      } else ()
    }
    var triangleIndex: scala.Int = this.triangleIndex
    val startVertex: scala.Int = this.vertexIndex / com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE
    triangles({ triangleIndex += 1; triangleIndex }) = startVertex.asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 1).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 2).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 2).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = (startVertex + 3).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    triangles({ triangleIndex += 1; triangleIndex }) = startVertex.asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    this.triangleIndex = triangleIndex
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
    var idx: scala.Int = this.vertexIndex
    vertices({ idx += 1; idx }) = x1
    vertices({ idx += 1; idx }) = y1
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u
    vertices({ idx += 1; idx }) = v
    vertices({ idx += 1; idx }) = x2
    vertices({ idx += 1; idx }) = y2
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u
    vertices({ idx += 1; idx }) = v2
    vertices({ idx += 1; idx }) = x3
    vertices({ idx += 1; idx }) = y3
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u2
    vertices({ idx += 1; idx }) = v2
    vertices({ idx += 1; idx }) = x4
    vertices({ idx += 1; idx }) = y4
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u2
    vertices({ idx += 1; idx }) = v
    this.vertexIndex = idx
  }
  @java.lang.Override
  def flush(): scala.Unit = {
    if (this.vertexIndex == 0) {
      return
    } else ()
    this.renderCalls = this.renderCalls + 1
    this.totalRenderCalls = this.totalRenderCalls + 1
    val trianglesInBatch: scala.Int = this.triangleIndex
    if (trianglesInBatch > this.maxTrianglesInBatch) {
      this.maxTrianglesInBatch = trianglesInBatch
    } else ()
    this.lastTexture.bind()
    val mesh: com.badlogic.gdx.graphics.Mesh = this.mesh
    mesh.setVertices(this.vertices, 0, this.vertexIndex)
    mesh.setIndices(this.triangles, 0, trianglesInBatch)
    if (this.blendingDisabled) {
      com.badlogic.gdx.Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND)
    } else {
      com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND)
      if (this.blendSrcFunc != (-1)) {
        com.badlogic.gdx.Gdx.gl.glBlendFuncSeparate(this.blendSrcFunc, this.blendDstFunc, this.blendSrcFuncAlpha, this.blendDstFuncAlpha)
      } else ()
    }
    mesh.render(if (this.customShader != null) this.customShader else this.shader, com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, 0, trianglesInBatch)
    this.vertexIndex = 0
    this.triangleIndex = 0
  }
  @java.lang.Override
  def disableBlending(): scala.Unit = {
    this.flush()
    this.blendingDisabled = true
  }
  @java.lang.Override
  def enableBlending(): scala.Unit = {
    this.flush()
    this.blendingDisabled = false
  }
  @java.lang.Override
  def setBlendFunction(srcFunc: scala.Int, dstFunc: scala.Int): scala.Unit = {
    this.setBlendFunctionSeparate(srcFunc, dstFunc, srcFunc, dstFunc)
  }
  @java.lang.Override
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
  @java.lang.Override
  def getBlendSrcFunc(): scala.Int = {
    return this.blendSrcFunc
  }
  @java.lang.Override
  def getBlendDstFunc(): scala.Int = {
    return this.blendDstFunc
  }
  @java.lang.Override
  def getBlendSrcFuncAlpha(): scala.Int = {
    return this.blendSrcFuncAlpha
  }
  @java.lang.Override
  def getBlendDstFuncAlpha(): scala.Int = {
    return this.blendDstFuncAlpha
  }
  @java.lang.Override
  def dispose(): scala.Unit = {
    this.mesh.dispose()
    if (this.ownsShader && (this.shader != null)) {
      this.shader.dispose()
    } else ()
  }
  @java.lang.Override
  def getProjectionMatrix(): com.badlogic.gdx.math.Matrix4 = {
    return this.projectionMatrix
  }
  @java.lang.Override
  def getTransformMatrix(): com.badlogic.gdx.math.Matrix4 = {
    return this.transformMatrix
  }
  @java.lang.Override
  def setProjectionMatrix(projection: com.badlogic.gdx.math.Matrix4): scala.Unit = {
    if (this.drawing) {
      this.flush()
    } else ()
    this.projectionMatrix.set(projection)
    if (this.drawing) {
      this.setupMatrices()
    } else ()
  }
  @java.lang.Override
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
  @java.lang.Override
  def setShader(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram): scala.Unit = {
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
  @java.lang.Override
  def getShader(): com.badlogic.gdx.graphics.glutils.ShaderProgram = {
    if (this.customShader == null) {
      return this.shader
    } else ()
    return this.customShader
  }
  @java.lang.Override
  def isBlendingEnabled(): scala.Boolean = {
    return !this.blendingDisabled
  }
  @java.lang.Override
  def isDrawing(): scala.Boolean = {
    return this.drawing
  }
}
object PolygonSpriteBatch {
  export com.badlogic.gdx.graphics.g2d.PolygonBatch.*
}