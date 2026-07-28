package com.badlogic.gdx.graphics.g3d.utils

final class DefaultTextureBinder(method$p: scala.Int, offset$p: scala.Int, count$arg: scala.Int) extends com.badlogic.gdx.graphics.g3d.utils.TextureBinder {
  private var offset: scala.Int = 0
  private var count: scala.Int = 0
  private var textures: scala.Array[com.badlogic.gdx.graphics.GLTexture] = null.asInstanceOf[scala.Array[com.badlogic.gdx.graphics.GLTexture]]
  private var unitsLRU: scala.Array[scala.Int] = null.asInstanceOf[scala.Array[scala.Int]]
  private var method: scala.Int = 0
  private var reused: scala.Boolean = false
  private var reuseCount: scala.Int = 0
  private var bindCount: scala.Int = 0
  private final val tempDesc: com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor[?] = new com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor().asInstanceOf[com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor[?]]
  private var currentTexture: scala.Int = 0
  var count$p: scala.Int = count$arg
  val max: scala.Int = java.lang.Math.min(DefaultTextureBinder.getMaxTextureUnits(), DefaultTextureBinder.MAX_GLES_UNITS)
  def this(method: scala.Int, offset: scala.Int) = {
    this(method, offset, -1)
  }
  def this(method: scala.Int) = {
    this(method, 0)
  }
  if (count$p < 0) {
    count$p = max - offset$p
  } else ()
  if (((offset$p < 0) || (count$p < 0)) || ((offset$p + count$p) > max)) {
    throw new com.badlogic.gdx.utils.GdxRuntimeException("Illegal arguments")
  } else ()
  this.method = method$p
  this.offset = offset$p
  this.count = count$p
  this.textures = new scala.Array[com.badlogic.gdx.graphics.GLTexture](count$p)
  this.unitsLRU = if (method$p == DefaultTextureBinder.LRU) new scala.Array[scala.Int](count$p) else null.asInstanceOf[scala.Array[scala.Int]]
  @java.lang.Override
  override def begin(): scala.Unit = {
    { var i: scala.Int = 0; while (i < this.count) { {
      this.textures(i) = null
      if (this.unitsLRU != null) {
        this.unitsLRU(i) = i
      } else ()
    }; i = i + 1 } }
  }
  @java.lang.Override
  override def `end`(): scala.Unit = {
    com.badlogic.gdx.Gdx.gl.glActiveTexture(com.badlogic.gdx.graphics.GL20.GL_TEXTURE0)
  }
  @java.lang.Override
  override final def bind(textureDesc: com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor[?]): scala.Int = {
    return this.bindTexture(textureDesc.asInstanceOf[com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor[?]], false)
  }
  @java.lang.Override
  override final def bind(texture: com.badlogic.gdx.graphics.GLTexture): scala.Int = {
    this.tempDesc.asInstanceOf[com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor[com.badlogic.gdx.graphics.GLTexture]].set(texture.asInstanceOf[com.badlogic.gdx.graphics.GLTexture], null, null, null, null)
    return this.bindTexture(this.tempDesc.asInstanceOf[com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor[?]], false)
  }
  private final def bindTexture(textureDesc: com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor[?], rebind: scala.Boolean): scala.Int = {
    var idx: scala.Int = 0
    var result: scala.Int = 0
    val texture: com.badlogic.gdx.graphics.GLTexture = textureDesc.asInstanceOf[com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor[com.badlogic.gdx.graphics.GLTexture]].texture
    this.reused = false
    this.method match {
      case DefaultTextureBinder.ROUNDROBIN => {
        result = this.offset + {
          idx = this.bindTextureRoundRobin(texture)
          idx
        }
      }
      case DefaultTextureBinder.LRU => {
        result = this.offset + {
          idx = this.bindTextureLRU(texture)
          idx
        }
      }
      case _ => {
        return -1
      }
    }
    if (this.reused) {
      this.reuseCount = this.reuseCount + 1
      if (rebind) {
        texture.bind(result)
      } else {
        com.badlogic.gdx.Gdx.gl.glActiveTexture(com.badlogic.gdx.graphics.GL20.GL_TEXTURE0 + result)
      }
    } else {
      this.bindCount = this.bindCount + 1
    }
    texture.unsafeSetWrap(textureDesc.uWrap, textureDesc.vWrap)
    texture.unsafeSetFilter(textureDesc.minFilter, textureDesc.magFilter)
    return result
  }
  private final def bindTextureRoundRobin(texture: com.badlogic.gdx.graphics.GLTexture): scala.Int = {
    { var i: scala.Int = 0; while (i < this.count) { {
      val idx: scala.Int = (this.currentTexture + i) % this.count
      if (this.textures(idx) == texture) {
        this.reused = true
        return idx
      } else ()
    }; i = i + 1 } }
    this.currentTexture = (this.currentTexture + 1) % this.count
    this.textures(this.currentTexture) = texture
    texture.bind(this.offset + this.currentTexture)
    return this.currentTexture
  }
  private final def bindTextureLRU(texture: com.badlogic.gdx.graphics.GLTexture): scala.Int = {
    var i: scala.Int = 0;
    { i = 0; while (i < this.count) { {
      val idx: scala.Int = this.unitsLRU(i)
      if (this.textures(idx) == texture) {
        this.reused = true
        /* break */ ()
      } else ()
      if (this.textures(idx) == null) {
        /* break */ ()
      } else ()
    }; i = i + 1 } }
    if (i >= this.count) {
      i = this.count - 1
    } else ()
    val idx: scala.Int = this.unitsLRU(i)
    while (i > 0) {
      this.unitsLRU(i) = this.unitsLRU(i - 1)
      i = i - 1
    }
    this.unitsLRU(0) = idx
    if (!this.reused) {
      this.textures(idx) = texture
      texture.bind(this.offset + idx)
    } else ()
    return idx
  }
  @java.lang.Override
  override final def getBindCount(): scala.Int = {
    return this.bindCount
  }
  @java.lang.Override
  override final def getReuseCount(): scala.Int = {
    return this.reuseCount
  }
  @java.lang.Override
  override final def resetCounts(): scala.Unit = {
    this.bindCount = {
      this.reuseCount = 0
      this.reuseCount
    }
  }
}
object DefaultTextureBinder {
  final val ROUNDROBIN: scala.Int = 0
  final val LRU: scala.Int = 1
  final val MAX_GLES_UNITS: scala.Int = 32
  private def getMaxTextureUnits(): scala.Int = {
    val buffer: java.nio.IntBuffer = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(16)
    com.badlogic.gdx.Gdx.gl.glGetIntegerv(com.badlogic.gdx.graphics.GL20.GL_MAX_TEXTURE_IMAGE_UNITS, buffer)
    return buffer.get(0)
  }
}