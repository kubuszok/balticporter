package com.badlogic.gdx.graphics.g2d

class CpuSpriteBatch(size: scala.Int, defaultShader: com.badlogic.gdx.graphics.glutils.ShaderProgram) extends com.badlogic.gdx.graphics.g2d.SpriteBatch(size, defaultShader) {
  private final val virtualMatrix: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
  private final val adjustAffine: com.badlogic.gdx.math.Affine2 = new com.badlogic.gdx.math.Affine2()
  private var adjustNeeded: scala.Boolean = false
  private var haveIdentityRealMatrix: scala.Boolean = true
  private final val tmpAffine: com.badlogic.gdx.math.Affine2 = new com.badlogic.gdx.math.Affine2()
  def this(size: scala.Int) = {
    this(size, null)
  }
  def this() = {
    this(1000)
  }
  def flushAndSyncTransformMatrix(): scala.Unit = {
    this.flush()
    if (this.adjustNeeded) {
      this.haveIdentityRealMatrix = CpuSpriteBatch.checkIdt(this.virtualMatrix)
      if ((!this.haveIdentityRealMatrix) && (this.virtualMatrix.det() == 0)) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Transform matrix is singular, can't sync")
      } else ()
      this.adjustNeeded = false
      super.setTransformMatrix(this.virtualMatrix)
    } else ()
  }
  def getTransformMatrix(): com.badlogic.gdx.math.Matrix4 = {
    return if (this.adjustNeeded) this.virtualMatrix else super.getTransformMatrix()
  }
  def setTransformMatrix(transform: com.badlogic.gdx.math.Matrix4): scala.Unit = {
    val realMatrix: com.badlogic.gdx.math.Matrix4 = super.getTransformMatrix()
    if (CpuSpriteBatch.checkEqual(realMatrix, transform)) {
      this.adjustNeeded = false
    } else {
      if (this.isDrawing()) {
        this.virtualMatrix.setAsAffine(transform)
        this.adjustNeeded = true
        if (this.haveIdentityRealMatrix) {
          this.adjustAffine.set(transform)
        } else {
          this.tmpAffine.set(transform)
          this.adjustAffine.set(realMatrix).inv().mul(this.tmpAffine)
        }
      } else {
        realMatrix.setAsAffine(transform)
        this.haveIdentityRealMatrix = CpuSpriteBatch.checkIdt(realMatrix)
      }
    }
  }
  def setTransformMatrix(transform: com.badlogic.gdx.math.Affine2): scala.Unit = {
    val realMatrix: com.badlogic.gdx.math.Matrix4 = super.getTransformMatrix()
    if (CpuSpriteBatch.checkEqual(realMatrix, transform)) {
      this.adjustNeeded = false
    } else {
      this.virtualMatrix.setAsAffine(transform)
      if (this.isDrawing()) {
        this.adjustNeeded = true
        if (this.haveIdentityRealMatrix) {
          this.adjustAffine.set(transform)
        } else {
          this.adjustAffine.set(realMatrix).inv().mul(transform)
        }
      } else {
        realMatrix.setAsAffine(transform)
        this.haveIdentityRealMatrix = CpuSpriteBatch.checkIdt(realMatrix)
      }
    }
  }
  def draw(texture: com.badlogic.gdx.graphics.Texture, x: scala.Float, y: scala.Float, originX: scala.Float, originY: scala.Float, width: scala.Float, height: scala.Float, scaleX: scala.Float, scaleY: scala.Float, rotation: scala.Float, srcX: scala.Int, srcY: scala.Int, srcWidth: scala.Int, srcHeight: scala.Int, flipX: scala.Boolean, flipY: scala.Boolean): scala.Unit = {
    if (!this.adjustNeeded) {
      super.draw(texture, x, y, originX, originY, width, height, scaleX, scaleY, rotation, srcX, srcY, srcWidth, srcHeight, flipX, flipY)
    } else {
      this.drawAdjusted(texture, x, y, originX, originY, width, height, scaleX, scaleY, rotation, srcX, srcY, srcWidth, srcHeight, flipX, flipY)
    }
  }
  def draw(texture: com.badlogic.gdx.graphics.Texture, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float, srcX: scala.Int, srcY: scala.Int, srcWidth: scala.Int, srcHeight: scala.Int, flipX: scala.Boolean, flipY: scala.Boolean): scala.Unit = {
    if (!this.adjustNeeded) {
      super.draw(texture, x, y, width, height, srcX, srcY, srcWidth, srcHeight, flipX, flipY)
    } else {
      this.drawAdjusted(texture, x, y, 0, 0, width, height, 1, 1, 0, srcX, srcY, srcWidth, srcHeight, flipX, flipY)
    }
  }
  def draw(texture: com.badlogic.gdx.graphics.Texture, x: scala.Float, y: scala.Float, srcX: scala.Int, srcY: scala.Int, srcWidth: scala.Int, srcHeight: scala.Int): scala.Unit = {
    if (!this.adjustNeeded) {
      super.draw(texture, x, y, srcX, srcY, srcWidth, srcHeight)
    } else {
      this.drawAdjusted(texture, x, y, 0, 0, srcWidth, srcHeight, 1, 1, 0, srcX, srcY, srcWidth, srcHeight, false, false)
    }
  }
  def draw(texture: com.badlogic.gdx.graphics.Texture, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float, u: scala.Float, v: scala.Float, u2: scala.Float, v2: scala.Float): scala.Unit = {
    if (!this.adjustNeeded) {
      super.draw(texture, x, y, width, height, u, v, u2, v2)
    } else {
      this.drawAdjustedUV(texture, x, y, 0, 0, width, height, 1, 1, 0, u, v, u2, v2, false, false)
    }
  }
  def draw(texture: com.badlogic.gdx.graphics.Texture, x: scala.Float, y: scala.Float): scala.Unit = {
    if (!this.adjustNeeded) {
      super.draw(texture, x, y)
    } else {
      this.drawAdjusted(texture, x, y, 0, 0, texture.getWidth(), texture.getHeight(), 1, 1, 0, 0, 1, 1, 0, false, false)
    }
  }
  def draw(texture: com.badlogic.gdx.graphics.Texture, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    if (!this.adjustNeeded) {
      super.draw(texture, x, y, width, height)
    } else {
      this.drawAdjusted(texture, x, y, 0, 0, width, height, 1, 1, 0, 0, 1, 1, 0, false, false)
    }
  }
  def draw(region: com.badlogic.gdx.graphics.g2d.TextureRegion, x: scala.Float, y: scala.Float): scala.Unit = {
    if (!this.adjustNeeded) {
      super.draw(region, x, y)
    } else {
      this.drawAdjusted(region, x, y, 0, 0, region.getRegionWidth(), region.getRegionHeight(), 1, 1, 0)
    }
  }
  def draw(region: com.badlogic.gdx.graphics.g2d.TextureRegion, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    if (!this.adjustNeeded) {
      super.draw(region, x, y, width, height)
    } else {
      this.drawAdjusted(region, x, y, 0, 0, width, height, 1, 1, 0)
    }
  }
  def draw(region: com.badlogic.gdx.graphics.g2d.TextureRegion, x: scala.Float, y: scala.Float, originX: scala.Float, originY: scala.Float, width: scala.Float, height: scala.Float, scaleX: scala.Float, scaleY: scala.Float, rotation: scala.Float): scala.Unit = {
    if (!this.adjustNeeded) {
      super.draw(region, x, y, originX, originY, width, height, scaleX, scaleY, rotation)
    } else {
      this.drawAdjusted(region, x, y, originX, originY, width, height, scaleX, scaleY, rotation)
    }
  }
  def draw(region: com.badlogic.gdx.graphics.g2d.TextureRegion, x: scala.Float, y: scala.Float, originX: scala.Float, originY: scala.Float, width: scala.Float, height: scala.Float, scaleX: scala.Float, scaleY: scala.Float, rotation: scala.Float, clockwise: scala.Boolean): scala.Unit = {
    if (!this.adjustNeeded) {
      super.draw(region, x, y, originX, originY, width, height, scaleX, scaleY, rotation, clockwise)
    } else {
      this.drawAdjusted(region, x, y, originX, originY, width, height, scaleX, scaleY, rotation, clockwise)
    }
  }
  def draw(texture: com.badlogic.gdx.graphics.Texture, spriteVertices: scala.Array[scala.Float], offset: scala.Int, count: scala.Int): scala.Unit = {
    if ((count % com.badlogic.gdx.graphics.g2d.Sprite.SPRITE_SIZE) != 0) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("invalid vertex count")
    } else ()
    if (!this.adjustNeeded) {
      super.draw(texture, spriteVertices, offset, count)
    } else {
      this.drawAdjusted(texture, spriteVertices, offset, count)
    }
  }
  def draw(region: com.badlogic.gdx.graphics.g2d.TextureRegion, width: scala.Float, height: scala.Float, transform: com.badlogic.gdx.math.Affine2): scala.Unit = {
    if (!this.adjustNeeded) {
      super.draw(region, width, height, transform)
    } else {
      this.drawAdjusted(region, width, height, transform)
    }
  }
  private def drawAdjusted(region: com.badlogic.gdx.graphics.g2d.TextureRegion, x: scala.Float, y: scala.Float, originX: scala.Float, originY: scala.Float, width: scala.Float, height: scala.Float, scaleX: scala.Float, scaleY: scala.Float, rotation: scala.Float): scala.Unit = {
    this.drawAdjustedUV(region.texture, x, y, originX, originY, width, height, scaleX, scaleY, rotation, region.u, region.v2, region.u2, region.v, false, false)
  }
  private def drawAdjusted(texture: com.badlogic.gdx.graphics.Texture, x: scala.Float, y: scala.Float, originX: scala.Float, originY: scala.Float, width: scala.Float, height: scala.Float, scaleX: scala.Float, scaleY: scala.Float, rotation: scala.Float, srcX: scala.Int, srcY: scala.Int, srcWidth: scala.Int, srcHeight: scala.Int, flipX: scala.Boolean, flipY: scala.Boolean): scala.Unit = {
    val invTexWidth: scala.Float = 1.0f / texture.getWidth()
    val invTexHeight: scala.Float = 1.0f / texture.getHeight()
    val u: scala.Float = srcX * invTexWidth
    val v: scala.Float = (srcY + srcHeight) * invTexHeight
    val u2: scala.Float = (srcX + srcWidth) * invTexWidth
    val v2: scala.Float = srcY * invTexHeight
    this.drawAdjustedUV(texture, x, y, originX, originY, width, height, scaleX, scaleY, rotation, u, v, u2, v2, flipX, flipY)
  }
  private def drawAdjustedUV(texture: com.badlogic.gdx.graphics.Texture, x: scala.Float, y: scala.Float, originX: scala.Float, originY: scala.Float, width: scala.Float, height: scala.Float, scaleX: scala.Float, scaleY: scala.Float, rotation: scala.Float, u$arg: scala.Float, v$arg: scala.Float, u2$arg: scala.Float, v2$arg: scala.Float, flipX: scala.Boolean, flipY: scala.Boolean): scala.Unit = {
    var u: scala.Float = u$arg
    var v: scala.Float = v$arg
    var u2: scala.Float = u2$arg
    var v2: scala.Float = v2$arg
    if (!drawing) {
      throw new java.lang.IllegalStateException("CpuSpriteBatch.begin must be called before draw.")
    } else ()
    if (texture != lastTexture) {
      this.switchTexture(texture)
    } else {
      if (idx == this.vertices.length) {
        super.flush()
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
    val t: com.badlogic.gdx.math.Affine2 = this.adjustAffine
    vertices(idx + 0) = ((t.m00 * x1) + (t.m01 * y1)) + t.m02
    vertices(idx + 1) = ((t.m10 * x1) + (t.m11 * y1)) + t.m12
    vertices(idx + 2) = colorPacked
    vertices(idx + 3) = u
    vertices(idx + 4) = v
    vertices(idx + 5) = ((t.m00 * x2) + (t.m01 * y2)) + t.m02
    vertices(idx + 6) = ((t.m10 * x2) + (t.m11 * y2)) + t.m12
    vertices(idx + 7) = colorPacked
    vertices(idx + 8) = u
    vertices(idx + 9) = v2
    vertices(idx + 10) = ((t.m00 * x3) + (t.m01 * y3)) + t.m02
    vertices(idx + 11) = ((t.m10 * x3) + (t.m11 * y3)) + t.m12
    vertices(idx + 12) = colorPacked
    vertices(idx + 13) = u2
    vertices(idx + 14) = v2
    vertices(idx + 15) = ((t.m00 * x4) + (t.m01 * y4)) + t.m02
    vertices(idx + 16) = ((t.m10 * x4) + (t.m11 * y4)) + t.m12
    vertices(idx + 17) = colorPacked
    vertices(idx + 18) = u2
    vertices(idx + 19) = v
    idx = idx + com.badlogic.gdx.graphics.g2d.Sprite.SPRITE_SIZE
  }
  private def drawAdjusted(region: com.badlogic.gdx.graphics.g2d.TextureRegion, x: scala.Float, y: scala.Float, originX: scala.Float, originY: scala.Float, width: scala.Float, height: scala.Float, scaleX: scala.Float, scaleY: scala.Float, rotation: scala.Float, clockwise: scala.Boolean): scala.Unit = {
    if (!drawing) {
      throw new java.lang.IllegalStateException("CpuSpriteBatch.begin must be called before draw.")
    } else ()
    if (region.texture != lastTexture) {
      this.switchTexture(region.texture)
    } else {
      if (idx == this.vertices.length) {
        super.flush()
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
    val t: com.badlogic.gdx.math.Affine2 = this.adjustAffine
    vertices(idx + 0) = ((t.m00 * x1) + (t.m01 * y1)) + t.m02
    vertices(idx + 1) = ((t.m10 * x1) + (t.m11 * y1)) + t.m12
    vertices(idx + 2) = colorPacked
    vertices(idx + 3) = u1
    vertices(idx + 4) = v1
    vertices(idx + 5) = ((t.m00 * x2) + (t.m01 * y2)) + t.m02
    vertices(idx + 6) = ((t.m10 * x2) + (t.m11 * y2)) + t.m12
    vertices(idx + 7) = colorPacked
    vertices(idx + 8) = u2
    vertices(idx + 9) = v2
    vertices(idx + 10) = ((t.m00 * x3) + (t.m01 * y3)) + t.m02
    vertices(idx + 11) = ((t.m10 * x3) + (t.m11 * y3)) + t.m12
    vertices(idx + 12) = colorPacked
    vertices(idx + 13) = u3
    vertices(idx + 14) = v3
    vertices(idx + 15) = ((t.m00 * x4) + (t.m01 * y4)) + t.m02
    vertices(idx + 16) = ((t.m10 * x4) + (t.m11 * y4)) + t.m12
    vertices(idx + 17) = colorPacked
    vertices(idx + 18) = u4
    vertices(idx + 19) = v4
    idx = idx + com.badlogic.gdx.graphics.g2d.Sprite.SPRITE_SIZE
  }
  private def drawAdjusted(region: com.badlogic.gdx.graphics.g2d.TextureRegion, width: scala.Float, height: scala.Float, transform: com.badlogic.gdx.math.Affine2): scala.Unit = {
    if (!drawing) {
      throw new java.lang.IllegalStateException("CpuSpriteBatch.begin must be called before draw.")
    } else ()
    if (region.texture != lastTexture) {
      this.switchTexture(region.texture)
    } else {
      if (idx == this.vertices.length) {
        super.flush()
      } else ()
    }
    var t: com.badlogic.gdx.math.Affine2 = transform
    val x1: scala.Float = t.m02
    val y1: scala.Float = t.m12
    val x2: scala.Float = (t.m01 * height) + t.m02
    val y2: scala.Float = (t.m11 * height) + t.m12
    val x3: scala.Float = ((t.m00 * width) + (t.m01 * height)) + t.m02
    val y3: scala.Float = ((t.m10 * width) + (t.m11 * height)) + t.m12
    val x4: scala.Float = (t.m00 * width) + t.m02
    val y4: scala.Float = (t.m10 * width) + t.m12
    val u: scala.Float = region.u
    val v: scala.Float = region.v2
    val u2: scala.Float = region.u2
    val v2: scala.Float = region.v
    t = this.adjustAffine
    vertices(idx + 0) = ((t.m00 * x1) + (t.m01 * y1)) + t.m02
    vertices(idx + 1) = ((t.m10 * x1) + (t.m11 * y1)) + t.m12
    vertices(idx + 2) = colorPacked
    vertices(idx + 3) = u
    vertices(idx + 4) = v
    vertices(idx + 5) = ((t.m00 * x2) + (t.m01 * y2)) + t.m02
    vertices(idx + 6) = ((t.m10 * x2) + (t.m11 * y2)) + t.m12
    vertices(idx + 7) = colorPacked
    vertices(idx + 8) = u
    vertices(idx + 9) = v2
    vertices(idx + 10) = ((t.m00 * x3) + (t.m01 * y3)) + t.m02
    vertices(idx + 11) = ((t.m10 * x3) + (t.m11 * y3)) + t.m12
    vertices(idx + 12) = colorPacked
    vertices(idx + 13) = u2
    vertices(idx + 14) = v2
    vertices(idx + 15) = ((t.m00 * x4) + (t.m01 * y4)) + t.m02
    vertices(idx + 16) = ((t.m10 * x4) + (t.m11 * y4)) + t.m12
    vertices(idx + 17) = colorPacked
    vertices(idx + 18) = u2
    vertices(idx + 19) = v
    idx = idx + com.badlogic.gdx.graphics.g2d.Sprite.SPRITE_SIZE
  }
  private def drawAdjusted(texture: com.badlogic.gdx.graphics.Texture, spriteVertices: scala.Array[scala.Float], offset$arg: scala.Int, count$arg: scala.Int): scala.Unit = {
    var offset: scala.Int = offset$arg
    var count: scala.Int = count$arg
    if (!drawing) {
      throw new java.lang.IllegalStateException("CpuSpriteBatch.begin must be called before draw.")
    } else ()
    if (texture != lastTexture) {
      this.switchTexture(texture)
    } else ()
    val t: com.badlogic.gdx.math.Affine2 = this.adjustAffine
    var copyCount: scala.Int = java.lang.Math.min(this.vertices.length - idx, count)
    while ({ {
      count = count - copyCount
      while (copyCount > 0) {
        val x: scala.Float = spriteVertices(offset)
        val y: scala.Float = spriteVertices(offset + 1)
        vertices(idx) = ((t.m00 * x) + (t.m01 * y)) + t.m02
        vertices(idx + 1) = ((t.m10 * x) + (t.m11 * y)) + t.m12
        vertices(idx + 2) = spriteVertices(offset + 2)
        vertices(idx + 3) = spriteVertices(offset + 3)
        vertices(idx + 4) = spriteVertices(offset + 4)
        idx = idx + com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE
        offset = offset + com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE
        copyCount = copyCount - com.badlogic.gdx.graphics.g2d.Sprite.VERTEX_SIZE
      }
      if (count > 0) {
        super.flush()
        copyCount = java.lang.Math.min(this.vertices.length, count)
      } else ()
    }; count > 0 }) ()
  }
}
object CpuSpriteBatch {
  export com.badlogic.gdx.graphics.g2d.SpriteBatch.{checkEqual => _, checkIdt => _, *}
  private def checkEqual(a: com.badlogic.gdx.math.Matrix4, b: com.badlogic.gdx.math.Matrix4): scala.Boolean = {
    if (a == b) {
      return true
    } else ()
    return (((((a.`val`(com.badlogic.gdx.math.Matrix4.M00) == b.`val`(com.badlogic.gdx.math.Matrix4.M00)) && (a.`val`(com.badlogic.gdx.math.Matrix4.M10) == b.`val`(com.badlogic.gdx.math.Matrix4.M10))) && (a.`val`(com.badlogic.gdx.math.Matrix4.M01) == b.`val`(com.badlogic.gdx.math.Matrix4.M01))) && (a.`val`(com.badlogic.gdx.math.Matrix4.M11) == b.`val`(com.badlogic.gdx.math.Matrix4.M11))) && (a.`val`(com.badlogic.gdx.math.Matrix4.M03) == b.`val`(com.badlogic.gdx.math.Matrix4.M03))) && (a.`val`(com.badlogic.gdx.math.Matrix4.M13) == b.`val`(com.badlogic.gdx.math.Matrix4.M13))
  }
  private def checkEqual(matrix: com.badlogic.gdx.math.Matrix4, affine: com.badlogic.gdx.math.Affine2): scala.Boolean = {
    val `val`: scala.Array[scala.Float] = matrix.getValues()
    return (((((`val`(com.badlogic.gdx.math.Matrix4.M00) == affine.m00) && (`val`(com.badlogic.gdx.math.Matrix4.M10) == affine.m10)) && (`val`(com.badlogic.gdx.math.Matrix4.M01) == affine.m01)) && (`val`(com.badlogic.gdx.math.Matrix4.M11) == affine.m11)) && (`val`(com.badlogic.gdx.math.Matrix4.M03) == affine.m02)) && (`val`(com.badlogic.gdx.math.Matrix4.M13) == affine.m12)
  }
  private def checkIdt(matrix: com.badlogic.gdx.math.Matrix4): scala.Boolean = {
    val `val`: scala.Array[scala.Float] = matrix.getValues()
    return (((((`val`(com.badlogic.gdx.math.Matrix4.M00) == 1) && (`val`(com.badlogic.gdx.math.Matrix4.M10) == 0)) && (`val`(com.badlogic.gdx.math.Matrix4.M01) == 0)) && (`val`(com.badlogic.gdx.math.Matrix4.M11) == 1)) && (`val`(com.badlogic.gdx.math.Matrix4.M03) == 0)) && (`val`(com.badlogic.gdx.math.Matrix4.M13) == 0)
  }
}