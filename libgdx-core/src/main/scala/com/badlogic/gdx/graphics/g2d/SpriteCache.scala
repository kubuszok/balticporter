package com.badlogic.gdx.graphics.g2d

class SpriteCache(size: scala.Int, shader$p: com.badlogic.gdx.graphics.glutils.ShaderProgram, useIndices: scala.Boolean) extends com.badlogic.gdx.utils.Disposable {
  private var mesh: com.badlogic.gdx.graphics.Mesh = null.asInstanceOf[com.badlogic.gdx.graphics.Mesh]
  private var drawing: scala.Boolean = false
  private final val transformMatrix: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
  private final val projectionMatrix: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
  private var caches: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.SpriteCache.Cache] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.SpriteCache.Cache]]
  private final val combinedMatrix: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
  private var shader: com.badlogic.gdx.graphics.glutils.ShaderProgram = null.asInstanceOf[com.badlogic.gdx.graphics.glutils.ShaderProgram]
  private var currentCache: com.badlogic.gdx.graphics.g2d.SpriteCache.Cache = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.SpriteCache.Cache]
  private final val textures: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.Texture] = new com.badlogic.gdx.utils.Array(8).asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.Texture]]
  private final val counts: com.badlogic.gdx.utils.IntArray = new com.badlogic.gdx.utils.IntArray(8)
  private final val color: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color(1, 1, 1, 1)
  private var colorPacked: scala.Float = com.badlogic.gdx.graphics.Color.WHITE_FLOAT_BITS
  private var customShader: com.badlogic.gdx.graphics.glutils.ShaderProgram = null
  var renderCalls: scala.Int = 0
  var totalRenderCalls: scala.Int = 0
  def this(size: scala.Int, useIndices: scala.Boolean) = {
    this(size, SpriteCache.createDefaultShader(), useIndices)
  }
  def this() = {
    this(1000, false)
  }
  this.shader = shader$p
  if (useIndices && (size > 8191)) {
    throw new java.lang.IllegalArgumentException("Can't have more than 8191 sprites per batch: " + size)
  } else ()
  this.mesh = new com.badlogic.gdx.graphics.Mesh(true, size * (if (useIndices) 4 else 6), if (useIndices) size * 6 else 0, scala.Array[com.badlogic.gdx.graphics.VertexAttribute](new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Position, 2, com.badlogic.gdx.graphics.glutils.ShaderProgram.POSITION_ATTRIBUTE), new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorPacked, 4, com.badlogic.gdx.graphics.glutils.ShaderProgram.COLOR_ATTRIBUTE), new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.TextureCoordinates, 2, com.badlogic.gdx.graphics.glutils.ShaderProgram.TEXCOORD_ATTRIBUTE + "0")))
  this.mesh.setAutoBind(false)
  if (useIndices) {
    val length: scala.Int = size * 6
    val indices: scala.Array[scala.Short] = new scala.Array[scala.Short](length)
    var j: scala.Short = 0.asInstanceOf[scala.Short];
    { var i: scala.Int = 0; while (i < length) { {
      indices(i + 0) = j
      indices(i + 1) = (j + 1).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
      indices(i + 2) = (j + 2).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
      indices(i + 3) = (j + 2).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
      indices(i + 4) = (j + 3).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
      indices(i + 5) = j
    }; i = i + 6; j = (j + 4).asInstanceOf[scala.Short] } }
    this.mesh.setIndices(indices)
  } else ()
  this.projectionMatrix.setToOrtho2D(0, 0, com.badlogic.gdx.Gdx.graphics.getWidth(), com.badlogic.gdx.Gdx.graphics.getHeight())
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
  def beginCache(): scala.Unit = {
    if (this.drawing) {
      throw new java.lang.IllegalStateException("end must be called before beginCache")
    } else ()
    if (this.currentCache != null) {
      throw new java.lang.IllegalStateException("endCache must be called before begin.")
    } else ()
    val verticesPerImage: scala.Int = if (this.mesh.getNumIndices() > 0) 4 else 6
    val verticesBuffer: java.nio.FloatBuffer = this.mesh.getVerticesBuffer(true)
    this.currentCache = new com.badlogic.gdx.graphics.g2d.SpriteCache.Cache(this.caches.size, verticesBuffer.limit())
    this.caches.add(this.currentCache)
    verticesBuffer.compact()
  }
  def beginCache(cacheID: scala.Int): scala.Unit = {
    if (this.drawing) {
      throw new java.lang.IllegalStateException("end must be called before beginCache")
    } else ()
    if (this.currentCache != null) {
      throw new java.lang.IllegalStateException("endCache must be called before begin.")
    } else ()
    val verticesBuffer: java.nio.Buffer = this.mesh.getVerticesBuffer(true).asInstanceOf[java.nio.Buffer]
    if (cacheID == (this.caches.size - 1)) {
      val oldCache: com.badlogic.gdx.graphics.g2d.SpriteCache.Cache = this.caches.removeIndex(cacheID)
      verticesBuffer.limit(oldCache.offset)
      this.beginCache()
      return
    } else ()
    this.currentCache = this.caches.get(cacheID)
    verticesBuffer.position(this.currentCache.offset)
  }
  def endCache(): scala.Int = {
    if (this.currentCache == null) {
      throw new java.lang.IllegalStateException("beginCache must be called before endCache.")
    } else ()
    val cache: com.badlogic.gdx.graphics.g2d.SpriteCache.Cache = this.currentCache
    val cacheCount: scala.Int = this.mesh.getVerticesBuffer(false).position() - cache.offset
    if (cache.textures == null) {
      cache.maxCount = cacheCount
      cache.textureCount = this.textures.size
      cache.textures = this.textures.toArray(((size: scala.Int) => new scala.Array[com.badlogic.gdx.graphics.Texture](size)))
      cache.counts = new scala.Array[scala.Int](cache.textureCount);
      { var i: scala.Int = 0; val n: scala.Int = this.counts.size; while (i < n) { {
        cache.counts(i) = this.counts.get(i)
      }; i = i + 1 } }
      this.mesh.getVerticesBuffer(true).asInstanceOf[java.nio.Buffer].flip()
    } else {
      if (cacheCount > cache.maxCount) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException(((("If a cache is not the last created, it cannot be redefined with more entries than when it was first created: " + cacheCount) + " (") + cache.maxCount) + " max)")
      } else ()
      cache.textureCount = this.textures.size
      if (cache.textures.length < cache.textureCount) {
        cache.textures = new scala.Array[com.badlogic.gdx.graphics.Texture](cache.textureCount)
      } else ();
      { var i: scala.Int = 0; val n: scala.Int = cache.textureCount; while (i < n) { {
        cache.textures(i) = this.textures.get(i)
      }; i = i + 1 } }
      if (cache.counts.length < cache.textureCount) {
        cache.counts = new scala.Array[scala.Int](cache.textureCount)
      } else ();
      { var i: scala.Int = 0; val n: scala.Int = cache.textureCount; while (i < n) { {
        cache.counts(i) = this.counts.get(i)
      }; i = i + 1 } }
      val vertices: java.nio.FloatBuffer = this.mesh.getVerticesBuffer(true)
      vertices.asInstanceOf[java.nio.Buffer].position(0)
      val lastCache: com.badlogic.gdx.graphics.g2d.SpriteCache.Cache = this.caches.get(this.caches.size - 1)
      vertices.asInstanceOf[java.nio.Buffer].limit(lastCache.offset + lastCache.maxCount)
    }
    this.currentCache = null
    this.textures.clear()
    this.counts.clear()
    return cache.id
  }
  def clear(): scala.Unit = {
    this.caches.clear()
    this.mesh.getVerticesBuffer(true).asInstanceOf[java.nio.Buffer].clear().flip()
  }
  def add(texture: com.badlogic.gdx.graphics.Texture, vertices: scala.Array[scala.Float], offset: scala.Int, length: scala.Int): scala.Unit = {
    if (this.currentCache == null) {
      throw new java.lang.IllegalStateException("beginCache must be called before add.")
    } else ()
    val verticesPerImage: scala.Int = if (this.mesh.getNumIndices() > 0) 4 else 6
    val count: scala.Int = (length / (verticesPerImage * com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE)) * 6
    val lastIndex: scala.Int = this.textures.size - 1
    if ((lastIndex < 0) || (this.textures.get(lastIndex) != texture)) {
      this.textures.add(texture)
      this.counts.add(count)
    } else {
      this.counts.incr(lastIndex, count)
    }
    this.mesh.getVerticesBuffer(true).put(vertices, offset, length)
  }
  def add(texture: com.badlogic.gdx.graphics.Texture, x: scala.Float, y: scala.Float): scala.Unit = {
    val fx2: scala.Float = x + texture.getWidth()
    val fy2: scala.Float = y + texture.getHeight()
    SpriteCache.tempVertices(0) = x
    SpriteCache.tempVertices(1) = y
    SpriteCache.tempVertices(2) = this.colorPacked
    SpriteCache.tempVertices(3) = 0
    SpriteCache.tempVertices(4) = 1
    SpriteCache.tempVertices(5) = x
    SpriteCache.tempVertices(6) = fy2
    SpriteCache.tempVertices(7) = this.colorPacked
    SpriteCache.tempVertices(8) = 0
    SpriteCache.tempVertices(9) = 0
    SpriteCache.tempVertices(10) = fx2
    SpriteCache.tempVertices(11) = fy2
    SpriteCache.tempVertices(12) = this.colorPacked
    SpriteCache.tempVertices(13) = 1
    SpriteCache.tempVertices(14) = 0
    if (this.mesh.getNumIndices() > 0) {
      SpriteCache.tempVertices(15) = fx2
      SpriteCache.tempVertices(16) = y
      SpriteCache.tempVertices(17) = this.colorPacked
      SpriteCache.tempVertices(18) = 1
      SpriteCache.tempVertices(19) = 1
      this.add(texture, SpriteCache.tempVertices, 0, 20)
    } else {
      SpriteCache.tempVertices(15) = fx2
      SpriteCache.tempVertices(16) = fy2
      SpriteCache.tempVertices(17) = this.colorPacked
      SpriteCache.tempVertices(18) = 1
      SpriteCache.tempVertices(19) = 0
      SpriteCache.tempVertices(20) = fx2
      SpriteCache.tempVertices(21) = y
      SpriteCache.tempVertices(22) = this.colorPacked
      SpriteCache.tempVertices(23) = 1
      SpriteCache.tempVertices(24) = 1
      SpriteCache.tempVertices(25) = x
      SpriteCache.tempVertices(26) = y
      SpriteCache.tempVertices(27) = this.colorPacked
      SpriteCache.tempVertices(28) = 0
      SpriteCache.tempVertices(29) = 1
      this.add(texture, SpriteCache.tempVertices, 0, 30)
    }
  }
  def add(texture: com.badlogic.gdx.graphics.Texture, x: scala.Float, y: scala.Float, srcWidth: scala.Int, srcHeight: scala.Int, u: scala.Float, v: scala.Float, u2: scala.Float, v2: scala.Float, color: scala.Float): scala.Unit = {
    val fx2: scala.Float = x + srcWidth
    val fy2: scala.Float = y + srcHeight
    SpriteCache.tempVertices(0) = x
    SpriteCache.tempVertices(1) = y
    SpriteCache.tempVertices(2) = color
    SpriteCache.tempVertices(3) = u
    SpriteCache.tempVertices(4) = v
    SpriteCache.tempVertices(5) = x
    SpriteCache.tempVertices(6) = fy2
    SpriteCache.tempVertices(7) = color
    SpriteCache.tempVertices(8) = u
    SpriteCache.tempVertices(9) = v2
    SpriteCache.tempVertices(10) = fx2
    SpriteCache.tempVertices(11) = fy2
    SpriteCache.tempVertices(12) = color
    SpriteCache.tempVertices(13) = u2
    SpriteCache.tempVertices(14) = v2
    if (this.mesh.getNumIndices() > 0) {
      SpriteCache.tempVertices(15) = fx2
      SpriteCache.tempVertices(16) = y
      SpriteCache.tempVertices(17) = color
      SpriteCache.tempVertices(18) = u2
      SpriteCache.tempVertices(19) = v
      this.add(texture, SpriteCache.tempVertices, 0, 20)
    } else {
      SpriteCache.tempVertices(15) = fx2
      SpriteCache.tempVertices(16) = fy2
      SpriteCache.tempVertices(17) = color
      SpriteCache.tempVertices(18) = u2
      SpriteCache.tempVertices(19) = v2
      SpriteCache.tempVertices(20) = fx2
      SpriteCache.tempVertices(21) = y
      SpriteCache.tempVertices(22) = color
      SpriteCache.tempVertices(23) = u2
      SpriteCache.tempVertices(24) = v
      SpriteCache.tempVertices(25) = x
      SpriteCache.tempVertices(26) = y
      SpriteCache.tempVertices(27) = color
      SpriteCache.tempVertices(28) = u
      SpriteCache.tempVertices(29) = v
      this.add(texture, SpriteCache.tempVertices, 0, 30)
    }
  }
  def add(texture: com.badlogic.gdx.graphics.Texture, x: scala.Float, y: scala.Float, srcX: scala.Int, srcY: scala.Int, srcWidth: scala.Int, srcHeight: scala.Int): scala.Unit = {
    val invTexWidth: scala.Float = 1.0f / texture.getWidth()
    val invTexHeight: scala.Float = 1.0f / texture.getHeight()
    val u: scala.Float = srcX * invTexWidth
    val v: scala.Float = (srcY + srcHeight) * invTexHeight
    val u2: scala.Float = (srcX + srcWidth) * invTexWidth
    val v2: scala.Float = srcY * invTexHeight
    val fx2: scala.Float = x + srcWidth
    val fy2: scala.Float = y + srcHeight
    SpriteCache.tempVertices(0) = x
    SpriteCache.tempVertices(1) = y
    SpriteCache.tempVertices(2) = this.colorPacked
    SpriteCache.tempVertices(3) = u
    SpriteCache.tempVertices(4) = v
    SpriteCache.tempVertices(5) = x
    SpriteCache.tempVertices(6) = fy2
    SpriteCache.tempVertices(7) = this.colorPacked
    SpriteCache.tempVertices(8) = u
    SpriteCache.tempVertices(9) = v2
    SpriteCache.tempVertices(10) = fx2
    SpriteCache.tempVertices(11) = fy2
    SpriteCache.tempVertices(12) = this.colorPacked
    SpriteCache.tempVertices(13) = u2
    SpriteCache.tempVertices(14) = v2
    if (this.mesh.getNumIndices() > 0) {
      SpriteCache.tempVertices(15) = fx2
      SpriteCache.tempVertices(16) = y
      SpriteCache.tempVertices(17) = this.colorPacked
      SpriteCache.tempVertices(18) = u2
      SpriteCache.tempVertices(19) = v
      this.add(texture, SpriteCache.tempVertices, 0, 20)
    } else {
      SpriteCache.tempVertices(15) = fx2
      SpriteCache.tempVertices(16) = fy2
      SpriteCache.tempVertices(17) = this.colorPacked
      SpriteCache.tempVertices(18) = u2
      SpriteCache.tempVertices(19) = v2
      SpriteCache.tempVertices(20) = fx2
      SpriteCache.tempVertices(21) = y
      SpriteCache.tempVertices(22) = this.colorPacked
      SpriteCache.tempVertices(23) = u2
      SpriteCache.tempVertices(24) = v
      SpriteCache.tempVertices(25) = x
      SpriteCache.tempVertices(26) = y
      SpriteCache.tempVertices(27) = this.colorPacked
      SpriteCache.tempVertices(28) = u
      SpriteCache.tempVertices(29) = v
      this.add(texture, SpriteCache.tempVertices, 0, 30)
    }
  }
  def add(texture: com.badlogic.gdx.graphics.Texture, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float, srcX: scala.Int, srcY: scala.Int, srcWidth: scala.Int, srcHeight: scala.Int, flipX: scala.Boolean, flipY: scala.Boolean): scala.Unit = {
    val invTexWidth: scala.Float = 1.0f / texture.getWidth()
    val invTexHeight: scala.Float = 1.0f / texture.getHeight()
    var u: scala.Float = srcX * invTexWidth
    var v: scala.Float = (srcY + srcHeight) * invTexHeight
    var u2: scala.Float = (srcX + srcWidth) * invTexWidth
    var v2: scala.Float = srcY * invTexHeight
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
    SpriteCache.tempVertices(0) = x
    SpriteCache.tempVertices(1) = y
    SpriteCache.tempVertices(2) = this.colorPacked
    SpriteCache.tempVertices(3) = u
    SpriteCache.tempVertices(4) = v
    SpriteCache.tempVertices(5) = x
    SpriteCache.tempVertices(6) = fy2
    SpriteCache.tempVertices(7) = this.colorPacked
    SpriteCache.tempVertices(8) = u
    SpriteCache.tempVertices(9) = v2
    SpriteCache.tempVertices(10) = fx2
    SpriteCache.tempVertices(11) = fy2
    SpriteCache.tempVertices(12) = this.colorPacked
    SpriteCache.tempVertices(13) = u2
    SpriteCache.tempVertices(14) = v2
    if (this.mesh.getNumIndices() > 0) {
      SpriteCache.tempVertices(15) = fx2
      SpriteCache.tempVertices(16) = y
      SpriteCache.tempVertices(17) = this.colorPacked
      SpriteCache.tempVertices(18) = u2
      SpriteCache.tempVertices(19) = v
      this.add(texture, SpriteCache.tempVertices, 0, 20)
    } else {
      SpriteCache.tempVertices(15) = fx2
      SpriteCache.tempVertices(16) = fy2
      SpriteCache.tempVertices(17) = this.colorPacked
      SpriteCache.tempVertices(18) = u2
      SpriteCache.tempVertices(19) = v2
      SpriteCache.tempVertices(20) = fx2
      SpriteCache.tempVertices(21) = y
      SpriteCache.tempVertices(22) = this.colorPacked
      SpriteCache.tempVertices(23) = u2
      SpriteCache.tempVertices(24) = v
      SpriteCache.tempVertices(25) = x
      SpriteCache.tempVertices(26) = y
      SpriteCache.tempVertices(27) = this.colorPacked
      SpriteCache.tempVertices(28) = u
      SpriteCache.tempVertices(29) = v
      this.add(texture, SpriteCache.tempVertices, 0, 30)
    }
  }
  def add(texture: com.badlogic.gdx.graphics.Texture, x: scala.Float, y: scala.Float, originX: scala.Float, originY: scala.Float, width: scala.Float, height: scala.Float, scaleX: scala.Float, scaleY: scala.Float, rotation: scala.Float, srcX: scala.Int, srcY: scala.Int, srcWidth: scala.Int, srcHeight: scala.Int, flipX: scala.Boolean, flipY: scala.Boolean): scala.Unit = {
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
    val invTexWidth: scala.Float = 1.0f / texture.getWidth()
    val invTexHeight: scala.Float = 1.0f / texture.getHeight()
    var u: scala.Float = srcX * invTexWidth
    var v: scala.Float = (srcY + srcHeight) * invTexHeight
    var u2: scala.Float = (srcX + srcWidth) * invTexWidth
    var v2: scala.Float = srcY * invTexHeight
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
    SpriteCache.tempVertices(0) = x1
    SpriteCache.tempVertices(1) = y1
    SpriteCache.tempVertices(2) = this.colorPacked
    SpriteCache.tempVertices(3) = u
    SpriteCache.tempVertices(4) = v
    SpriteCache.tempVertices(5) = x2
    SpriteCache.tempVertices(6) = y2
    SpriteCache.tempVertices(7) = this.colorPacked
    SpriteCache.tempVertices(8) = u
    SpriteCache.tempVertices(9) = v2
    SpriteCache.tempVertices(10) = x3
    SpriteCache.tempVertices(11) = y3
    SpriteCache.tempVertices(12) = this.colorPacked
    SpriteCache.tempVertices(13) = u2
    SpriteCache.tempVertices(14) = v2
    if (this.mesh.getNumIndices() > 0) {
      SpriteCache.tempVertices(15) = x4
      SpriteCache.tempVertices(16) = y4
      SpriteCache.tempVertices(17) = this.colorPacked
      SpriteCache.tempVertices(18) = u2
      SpriteCache.tempVertices(19) = v
      this.add(texture, SpriteCache.tempVertices, 0, 20)
    } else {
      SpriteCache.tempVertices(15) = x3
      SpriteCache.tempVertices(16) = y3
      SpriteCache.tempVertices(17) = this.colorPacked
      SpriteCache.tempVertices(18) = u2
      SpriteCache.tempVertices(19) = v2
      SpriteCache.tempVertices(20) = x4
      SpriteCache.tempVertices(21) = y4
      SpriteCache.tempVertices(22) = this.colorPacked
      SpriteCache.tempVertices(23) = u2
      SpriteCache.tempVertices(24) = v
      SpriteCache.tempVertices(25) = x1
      SpriteCache.tempVertices(26) = y1
      SpriteCache.tempVertices(27) = this.colorPacked
      SpriteCache.tempVertices(28) = u
      SpriteCache.tempVertices(29) = v
      this.add(texture, SpriteCache.tempVertices, 0, 30)
    }
  }
  def add(region: com.badlogic.gdx.graphics.g2d.TextureRegion, x: scala.Float, y: scala.Float): scala.Unit = {
    this.add(region, x, y, region.getRegionWidth(), region.getRegionHeight())
  }
  def add(region: com.badlogic.gdx.graphics.g2d.TextureRegion, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    val fx2: scala.Float = x + width
    val fy2: scala.Float = y + height
    val u: scala.Float = region.u
    val v: scala.Float = region.v2
    val u2: scala.Float = region.u2
    val v2: scala.Float = region.v
    SpriteCache.tempVertices(0) = x
    SpriteCache.tempVertices(1) = y
    SpriteCache.tempVertices(2) = this.colorPacked
    SpriteCache.tempVertices(3) = u
    SpriteCache.tempVertices(4) = v
    SpriteCache.tempVertices(5) = x
    SpriteCache.tempVertices(6) = fy2
    SpriteCache.tempVertices(7) = this.colorPacked
    SpriteCache.tempVertices(8) = u
    SpriteCache.tempVertices(9) = v2
    SpriteCache.tempVertices(10) = fx2
    SpriteCache.tempVertices(11) = fy2
    SpriteCache.tempVertices(12) = this.colorPacked
    SpriteCache.tempVertices(13) = u2
    SpriteCache.tempVertices(14) = v2
    if (this.mesh.getNumIndices() > 0) {
      SpriteCache.tempVertices(15) = fx2
      SpriteCache.tempVertices(16) = y
      SpriteCache.tempVertices(17) = this.colorPacked
      SpriteCache.tempVertices(18) = u2
      SpriteCache.tempVertices(19) = v
      this.add(region.texture, SpriteCache.tempVertices, 0, 20)
    } else {
      SpriteCache.tempVertices(15) = fx2
      SpriteCache.tempVertices(16) = fy2
      SpriteCache.tempVertices(17) = this.colorPacked
      SpriteCache.tempVertices(18) = u2
      SpriteCache.tempVertices(19) = v2
      SpriteCache.tempVertices(20) = fx2
      SpriteCache.tempVertices(21) = y
      SpriteCache.tempVertices(22) = this.colorPacked
      SpriteCache.tempVertices(23) = u2
      SpriteCache.tempVertices(24) = v
      SpriteCache.tempVertices(25) = x
      SpriteCache.tempVertices(26) = y
      SpriteCache.tempVertices(27) = this.colorPacked
      SpriteCache.tempVertices(28) = u
      SpriteCache.tempVertices(29) = v
      this.add(region.texture, SpriteCache.tempVertices, 0, 30)
    }
  }
  def add(region: com.badlogic.gdx.graphics.g2d.TextureRegion, x: scala.Float, y: scala.Float, originX: scala.Float, originY: scala.Float, width: scala.Float, height: scala.Float, scaleX: scala.Float, scaleY: scala.Float, rotation: scala.Float): scala.Unit = {
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
    SpriteCache.tempVertices(0) = x1
    SpriteCache.tempVertices(1) = y1
    SpriteCache.tempVertices(2) = this.colorPacked
    SpriteCache.tempVertices(3) = u
    SpriteCache.tempVertices(4) = v
    SpriteCache.tempVertices(5) = x2
    SpriteCache.tempVertices(6) = y2
    SpriteCache.tempVertices(7) = this.colorPacked
    SpriteCache.tempVertices(8) = u
    SpriteCache.tempVertices(9) = v2
    SpriteCache.tempVertices(10) = x3
    SpriteCache.tempVertices(11) = y3
    SpriteCache.tempVertices(12) = this.colorPacked
    SpriteCache.tempVertices(13) = u2
    SpriteCache.tempVertices(14) = v2
    if (this.mesh.getNumIndices() > 0) {
      SpriteCache.tempVertices(15) = x4
      SpriteCache.tempVertices(16) = y4
      SpriteCache.tempVertices(17) = this.colorPacked
      SpriteCache.tempVertices(18) = u2
      SpriteCache.tempVertices(19) = v
      this.add(region.texture, SpriteCache.tempVertices, 0, 20)
    } else {
      SpriteCache.tempVertices(15) = x3
      SpriteCache.tempVertices(16) = y3
      SpriteCache.tempVertices(17) = this.colorPacked
      SpriteCache.tempVertices(18) = u2
      SpriteCache.tempVertices(19) = v2
      SpriteCache.tempVertices(20) = x4
      SpriteCache.tempVertices(21) = y4
      SpriteCache.tempVertices(22) = this.colorPacked
      SpriteCache.tempVertices(23) = u2
      SpriteCache.tempVertices(24) = v
      SpriteCache.tempVertices(25) = x1
      SpriteCache.tempVertices(26) = y1
      SpriteCache.tempVertices(27) = this.colorPacked
      SpriteCache.tempVertices(28) = u
      SpriteCache.tempVertices(29) = v
      this.add(region.texture, SpriteCache.tempVertices, 0, 30)
    }
  }
  def add(sprite: com.badlogic.gdx.graphics.g2d.Sprite): scala.Unit = {
    if (this.mesh.getNumIndices() > 0) {
      this.add(sprite.getTexture(), sprite.getVertices(), 0, com.badlogic.gdx.graphics.g2d.Sprite.SPRITE_SIZE)
      return
    } else ()
    val spriteVertices: scala.Array[scala.Float] = sprite.getVertices()
    java.lang.System.arraycopy(spriteVertices, 0, SpriteCache.tempVertices, 0, 3 * com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE)
    java.lang.System.arraycopy(spriteVertices, 2 * com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE, SpriteCache.tempVertices, 3 * com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE, com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE)
    java.lang.System.arraycopy(spriteVertices, 3 * com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE, SpriteCache.tempVertices, 4 * com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE, com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE)
    java.lang.System.arraycopy(spriteVertices, 0, SpriteCache.tempVertices, 5 * com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE, com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE)
    this.add(sprite.getTexture(), SpriteCache.tempVertices, 0, 30)
  }
  def begin(): scala.Unit = {
    if (this.drawing) {
      throw new java.lang.IllegalStateException("end must be called before begin.")
    } else ()
    if (this.currentCache != null) {
      throw new java.lang.IllegalStateException("endCache must be called before begin")
    } else ()
    this.renderCalls = 0
    this.combinedMatrix.set(this.projectionMatrix).mul(this.transformMatrix)
    com.badlogic.gdx.Gdx.gl20.glDepthMask(false)
    if (this.customShader != null) {
      this.customShader.bind()
      this.customShader.setUniformMatrix("u_proj", this.projectionMatrix)
      this.customShader.setUniformMatrix("u_trans", this.transformMatrix)
      this.customShader.setUniformMatrix("u_projTrans", this.combinedMatrix)
      this.customShader.setUniformi("u_texture", 0)
      this.mesh.bind(this.customShader)
    } else {
      this.shader.bind()
      this.shader.setUniformMatrix("u_projectionViewMatrix", this.combinedMatrix)
      this.shader.setUniformi("u_texture", 0)
      this.mesh.bind(this.shader)
    }
    this.drawing = true
  }
  def `end`(): scala.Unit = {
    if (!this.drawing) {
      throw new java.lang.IllegalStateException("begin must be called before end.")
    } else ()
    this.drawing = false
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    gl.glDepthMask(true)
    if (this.customShader != null) {
      this.mesh.unbind(this.customShader)
    } else {
      this.mesh.unbind(this.shader)
    }
  }
  def draw(cacheID: scala.Int): scala.Unit = {
    if (!this.drawing) {
      throw new java.lang.IllegalStateException("SpriteCache.begin must be called before draw.")
    } else ()
    val cache: com.badlogic.gdx.graphics.g2d.SpriteCache.Cache = this.caches.get(cacheID)
    val verticesPerImage: scala.Int = if (this.mesh.getNumIndices() > 0) 4 else 6
    var offset: scala.Int = (cache.offset / (verticesPerImage * com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE)) * 6
    val textures: scala.Array[com.badlogic.gdx.graphics.Texture] = cache.textures
    val counts: scala.Array[scala.Int] = cache.counts
    val textureCount: scala.Int = cache.textureCount;
    { var i: scala.Int = 0; while (i < textureCount) { {
      val count: scala.Int = counts(i)
      textures(i).bind()
      if (this.customShader != null) {
        this.mesh.render(this.customShader, com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, offset, count)
      } else {
        this.mesh.render(this.shader, com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, offset, count)
      }
      offset = offset + count
    }; i = i + 1 } }
    this.renderCalls = this.renderCalls + textureCount
    this.totalRenderCalls = this.totalRenderCalls + textureCount
  }
  def draw(cacheID: scala.Int, offset$arg: scala.Int, length$arg: scala.Int): scala.Unit = {
    var offset: scala.Int = offset$arg
    var length: scala.Int = length$arg
    if (!this.drawing) {
      throw new java.lang.IllegalStateException("SpriteCache.begin must be called before draw.")
    } else ()
    val cache: com.badlogic.gdx.graphics.g2d.SpriteCache.Cache = this.caches.get(cacheID)
    val verticesPerImage: scala.Int = if (this.mesh.getNumIndices() > 0) 4 else 6
    offset = ((cache.offset / (verticesPerImage * com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE)) * 6) + (offset * 6)
    length = length * 6
    val textures: scala.Array[com.badlogic.gdx.graphics.Texture] = cache.textures
    val counts: scala.Array[scala.Int] = cache.counts
    val textureCount: scala.Int = cache.textureCount;
    { var i: scala.Int = 0; while (i < textureCount) { {
      textures(i).bind()
      var count: scala.Int = counts(i)
      if (count > length) {
        i = textureCount
        count = length
      } else {
        length = length - count
      }
      if (this.customShader != null) {
        this.mesh.render(this.customShader, com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, offset, count)
      } else {
        this.mesh.render(this.shader, com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, offset, count)
      }
      offset = offset + count
    }; i = i + 1 } }
    this.renderCalls = this.renderCalls + cache.textureCount
    this.totalRenderCalls = this.totalRenderCalls + textureCount
  }
  def dispose(): scala.Unit = {
    this.mesh.dispose()
    if (this.shader != null) {
      this.shader.dispose()
    } else ()
  }
  def getProjectionMatrix(): com.badlogic.gdx.math.Matrix4 = {
    return this.projectionMatrix
  }
  def setProjectionMatrix(projection: com.badlogic.gdx.math.Matrix4): scala.Unit = {
    if (this.drawing) {
      throw new java.lang.IllegalStateException("Can't set the matrix within begin/end.")
    } else ()
    this.projectionMatrix.set(projection)
  }
  def getTransformMatrix(): com.badlogic.gdx.math.Matrix4 = {
    return this.transformMatrix
  }
  def setTransformMatrix(transform: com.badlogic.gdx.math.Matrix4): scala.Unit = {
    if (this.drawing) {
      throw new java.lang.IllegalStateException("Can't set the matrix within begin/end.")
    } else ()
    this.transformMatrix.set(transform)
  }
  def setShader(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram): scala.Unit = {
    this.customShader = shader
  }
  def getCustomShader(): com.badlogic.gdx.graphics.glutils.ShaderProgram = {
    return this.customShader
  }
  def isDrawing(): scala.Boolean = {
    return this.drawing
  }
}
object SpriteCache {
  private final val tempVertices: scala.Array[scala.Float] = new scala.Array[scala.Float](com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE * 6)
  def createDefaultShader(): com.badlogic.gdx.graphics.glutils.ShaderProgram = {
    val vertexShader: java.lang.String = (((((((((((((((((((((((("attribute vec4 " + com.badlogic.gdx.graphics.glutils.ShaderProgram.POSITION_ATTRIBUTE) + ";\n") + "attribute vec4 ") + com.badlogic.gdx.graphics.glutils.ShaderProgram.COLOR_ATTRIBUTE) + ";\n") + "attribute vec2 ") + com.badlogic.gdx.graphics.glutils.ShaderProgram.TEXCOORD_ATTRIBUTE) + "0;\n") + "uniform mat4 u_projectionViewMatrix;\n") + "varying vec4 v_color;\n") + "varying vec2 v_texCoords;\n") + "\n") + "void main()\n") + "{\n") + "   v_color = ") + com.badlogic.gdx.graphics.glutils.ShaderProgram.COLOR_ATTRIBUTE) + ";\n") + "   v_color.a = v_color.a * (255.0/254.0);\n") + "   v_texCoords = ") + com.badlogic.gdx.graphics.glutils.ShaderProgram.TEXCOORD_ATTRIBUTE) + "0;\n") + "   gl_Position =  u_projectionViewMatrix * ") + com.badlogic.gdx.graphics.glutils.ShaderProgram.POSITION_ATTRIBUTE) + ";\n") + "}\n"
    val fragmentShader: java.lang.String = (((((((("#ifdef GL_ES\n" + "precision mediump float;\n") + "#endif\n") + "varying vec4 v_color;\n") + "varying vec2 v_texCoords;\n") + "uniform sampler2D u_texture;\n") + "void main()\n") + "{\n") + "  gl_FragColor = v_color * texture2D(u_texture, v_texCoords);\n") + "}"
    val shader: com.badlogic.gdx.graphics.glutils.ShaderProgram = new com.badlogic.gdx.graphics.glutils.ShaderProgram(vertexShader, fragmentShader)
    if (!shader.isCompiled()) {
      throw new java.lang.IllegalArgumentException("Error compiling shader: " + shader.getLog())
    } else ()
    return shader
  }
  class Cache(id$p: scala.Int, offset$p: scala.Int) {
    var id: scala.Int = 0
    var offset: scala.Int = 0
    var maxCount: scala.Int = 0
    var textureCount: scala.Int = 0
    var textures: scala.Array[com.badlogic.gdx.graphics.Texture] = null.asInstanceOf[scala.Array[com.badlogic.gdx.graphics.Texture]]
    var counts: scala.Array[scala.Int] = null.asInstanceOf[scala.Array[scala.Int]]
    this.id = id$p
    this.offset = offset$p
  }
}