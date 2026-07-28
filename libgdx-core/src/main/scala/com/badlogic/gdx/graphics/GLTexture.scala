package com.badlogic.gdx.graphics

abstract class GLTexture(glTarget$p: scala.Int, glHandle$p: scala.Int) extends com.badlogic.gdx.utils.Disposable {
  var glTarget: scala.Int = 0
  var glHandle: scala.Int = 0
  var minFilter: com.badlogic.gdx.graphics.Texture.TextureFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest
  var magFilter: com.badlogic.gdx.graphics.Texture.TextureFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest
  var uWrap: com.badlogic.gdx.graphics.Texture.TextureWrap = com.badlogic.gdx.graphics.Texture.TextureWrap.ClampToEdge
  var vWrap: com.badlogic.gdx.graphics.Texture.TextureWrap = com.badlogic.gdx.graphics.Texture.TextureWrap.ClampToEdge
  var anisotropicFilterLevel: scala.Float = 1.0f
  def this(glTarget: scala.Int) = {
    this(glTarget, com.badlogic.gdx.Gdx.gl.glGenTexture())
  }
  this.glTarget = glTarget$p
  this.glHandle = glHandle$p
  def getWidth(): scala.Int
  def getHeight(): scala.Int
  def getDepth(): scala.Int
  def isManaged(): scala.Boolean
  def reload(): scala.Unit
  def bind(): scala.Unit = {
    com.badlogic.gdx.Gdx.gl.glBindTexture(this.glTarget, this.glHandle)
  }
  def bind(unit: scala.Int): scala.Unit = {
    com.badlogic.gdx.Gdx.gl.glActiveTexture(com.badlogic.gdx.graphics.GL20.GL_TEXTURE0 + unit)
    com.badlogic.gdx.Gdx.gl.glBindTexture(this.glTarget, this.glHandle)
  }
  def getMinFilter(): com.badlogic.gdx.graphics.Texture.TextureFilter = {
    return this.minFilter
  }
  def getMagFilter(): com.badlogic.gdx.graphics.Texture.TextureFilter = {
    return this.magFilter
  }
  def getUWrap(): com.badlogic.gdx.graphics.Texture.TextureWrap = {
    return this.uWrap
  }
  def getVWrap(): com.badlogic.gdx.graphics.Texture.TextureWrap = {
    return this.vWrap
  }
  def getTextureObjectHandle(): scala.Int = {
    return this.glHandle
  }
  def unsafeSetWrap(u: com.badlogic.gdx.graphics.Texture.TextureWrap, v: com.badlogic.gdx.graphics.Texture.TextureWrap): scala.Unit = {
    this.unsafeSetWrap(u, v, false)
  }
  def unsafeSetWrap(u: com.badlogic.gdx.graphics.Texture.TextureWrap, v: com.badlogic.gdx.graphics.Texture.TextureWrap, force: scala.Boolean): scala.Unit = {
    if ((u != null) && (force || (this.uWrap != u))) {
      com.badlogic.gdx.Gdx.gl.glTexParameteri(this.glTarget, com.badlogic.gdx.graphics.GL20.GL_TEXTURE_WRAP_S, u.getGLEnum())
      this.uWrap = u
    } else ()
    if ((v != null) && (force || (this.vWrap != v))) {
      com.badlogic.gdx.Gdx.gl.glTexParameteri(this.glTarget, com.badlogic.gdx.graphics.GL20.GL_TEXTURE_WRAP_T, v.getGLEnum())
      this.vWrap = v
    } else ()
  }
  def setWrap(u: com.badlogic.gdx.graphics.Texture.TextureWrap, v: com.badlogic.gdx.graphics.Texture.TextureWrap): scala.Unit = {
    this.uWrap = u
    this.vWrap = v
    this.bind()
    com.badlogic.gdx.Gdx.gl.glTexParameteri(this.glTarget, com.badlogic.gdx.graphics.GL20.GL_TEXTURE_WRAP_S, u.getGLEnum())
    com.badlogic.gdx.Gdx.gl.glTexParameteri(this.glTarget, com.badlogic.gdx.graphics.GL20.GL_TEXTURE_WRAP_T, v.getGLEnum())
  }
  def unsafeSetFilter(minFilter: com.badlogic.gdx.graphics.Texture.TextureFilter, magFilter: com.badlogic.gdx.graphics.Texture.TextureFilter): scala.Unit = {
    this.unsafeSetFilter(minFilter, magFilter, false)
  }
  def unsafeSetFilter(minFilter: com.badlogic.gdx.graphics.Texture.TextureFilter, magFilter: com.badlogic.gdx.graphics.Texture.TextureFilter, force: scala.Boolean): scala.Unit = {
    if ((minFilter != null) && (force || (this.minFilter != minFilter))) {
      com.badlogic.gdx.Gdx.gl.glTexParameteri(this.glTarget, com.badlogic.gdx.graphics.GL20.GL_TEXTURE_MIN_FILTER, minFilter.getGLEnum())
      this.minFilter = minFilter
    } else ()
    if ((magFilter != null) && (force || (this.magFilter != magFilter))) {
      com.badlogic.gdx.Gdx.gl.glTexParameteri(this.glTarget, com.badlogic.gdx.graphics.GL20.GL_TEXTURE_MAG_FILTER, magFilter.getGLEnum())
      this.magFilter = magFilter
    } else ()
  }
  def setFilter(minFilter: com.badlogic.gdx.graphics.Texture.TextureFilter, magFilter: com.badlogic.gdx.graphics.Texture.TextureFilter): scala.Unit = {
    this.minFilter = minFilter
    this.magFilter = magFilter
    this.bind()
    com.badlogic.gdx.Gdx.gl.glTexParameteri(this.glTarget, com.badlogic.gdx.graphics.GL20.GL_TEXTURE_MIN_FILTER, minFilter.getGLEnum())
    com.badlogic.gdx.Gdx.gl.glTexParameteri(this.glTarget, com.badlogic.gdx.graphics.GL20.GL_TEXTURE_MAG_FILTER, magFilter.getGLEnum())
  }
  def unsafeSetAnisotropicFilter(level: scala.Float): scala.Float = {
    return this.unsafeSetAnisotropicFilter(level, false)
  }
  def unsafeSetAnisotropicFilter(level$arg: scala.Float, force: scala.Boolean): scala.Float = {
    var level: scala.Float = level$arg
    val max: scala.Float = GLTexture.getMaxAnisotropicFilterLevel()
    if (max == 1.0f) {
      return 1.0f
    } else ()
    level = java.lang.Math.min(level, max)
    if ((!force) && com.badlogic.gdx.math.MathUtils.isEqual(level, this.anisotropicFilterLevel, 0.1f)) {
      return this.anisotropicFilterLevel
    } else ()
    com.badlogic.gdx.Gdx.gl20.glTexParameterf(com.badlogic.gdx.graphics.GL20.GL_TEXTURE_2D, com.badlogic.gdx.graphics.GL20.GL_TEXTURE_MAX_ANISOTROPY_EXT, level)
    return {
      this.anisotropicFilterLevel = level
      this.anisotropicFilterLevel
    }
  }
  def setAnisotropicFilter(level$arg: scala.Float): scala.Float = {
    var level: scala.Float = level$arg
    val max: scala.Float = GLTexture.getMaxAnisotropicFilterLevel()
    if (max == 1.0f) {
      return 1.0f
    } else ()
    level = java.lang.Math.min(level, max)
    if (com.badlogic.gdx.math.MathUtils.isEqual(level, this.anisotropicFilterLevel, 0.1f)) {
      return level
    } else ()
    this.bind()
    com.badlogic.gdx.Gdx.gl20.glTexParameterf(com.badlogic.gdx.graphics.GL20.GL_TEXTURE_2D, com.badlogic.gdx.graphics.GL20.GL_TEXTURE_MAX_ANISOTROPY_EXT, level)
    return {
      this.anisotropicFilterLevel = level
      this.anisotropicFilterLevel
    }
  }
  def getAnisotropicFilter(): scala.Float = {
    return this.anisotropicFilterLevel
  }
  def delete(): scala.Unit = {
    if (this.glHandle != 0) {
      com.badlogic.gdx.Gdx.gl.glDeleteTexture(this.glHandle)
      this.glHandle = 0
    } else ()
  }
  @java.lang.Override
  def dispose(): scala.Unit = {
    this.delete()
  }
}
object GLTexture {
  private var maxAnisotropicFilterLevel: scala.Float = 0
  def getMaxAnisotropicFilterLevel(): scala.Float = {
    if (GLTexture.maxAnisotropicFilterLevel > 0) {
      return GLTexture.maxAnisotropicFilterLevel
    } else ()
    if (com.badlogic.gdx.Gdx.graphics.supportsExtension("GL_EXT_texture_filter_anisotropic")) {
      val buffer: java.nio.FloatBuffer = com.badlogic.gdx.utils.BufferUtils.newFloatBuffer(16)
      buffer.asInstanceOf[java.nio.Buffer].position(0)
      buffer.asInstanceOf[java.nio.Buffer].limit(buffer.capacity())
      com.badlogic.gdx.Gdx.gl20.glGetFloatv(com.badlogic.gdx.graphics.GL20.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT, buffer)
      return {
        GLTexture.maxAnisotropicFilterLevel = buffer.get(0)
        GLTexture.maxAnisotropicFilterLevel
      }
    } else ()
    return {
      GLTexture.maxAnisotropicFilterLevel = 1.0f
      GLTexture.maxAnisotropicFilterLevel
    }
  }
  def uploadImageData(target: scala.Int, data: com.badlogic.gdx.graphics.TextureData): scala.Unit = {
    GLTexture.uploadImageData(target, data, 0)
  }
  def uploadImageData(target: scala.Int, data: com.badlogic.gdx.graphics.TextureData, miplevel: scala.Int): scala.Unit = {
    if (data == null) {
      return
    } else ()
    if (!data.isPrepared()) {
      data.prepare()
    } else ()
    val `type`: com.badlogic.gdx.graphics.TextureData.TextureDataType = data.getType()
    if (`type` == com.badlogic.gdx.graphics.TextureData.TextureDataType.Custom) {
      data.consumeCustomData(target)
      return
    } else ()
    var pixmap: com.badlogic.gdx.graphics.Pixmap = data.consumePixmap()
    var disposePixmap: scala.Boolean = data.disposePixmap()
    if (data.getFormat() != pixmap.getFormat()) {
      val tmp: com.badlogic.gdx.graphics.Pixmap = new com.badlogic.gdx.graphics.Pixmap(pixmap.getWidth(), pixmap.getHeight(), data.getFormat())
      tmp.setBlending(com.badlogic.gdx.graphics.Pixmap.Blending.None)
      tmp.drawPixmap(pixmap, 0, 0, 0, 0, pixmap.getWidth(), pixmap.getHeight())
      if (data.disposePixmap()) {
        pixmap.dispose()
      } else ()
      pixmap = tmp
      disposePixmap = true
    } else ()
    com.badlogic.gdx.Gdx.gl.glPixelStorei(com.badlogic.gdx.graphics.GL20.GL_UNPACK_ALIGNMENT, 1)
    if (data.useMipMaps()) {
      com.badlogic.gdx.graphics.glutils.MipMapGenerator.generateMipMap(target, pixmap, pixmap.getWidth(), pixmap.getHeight())
    } else {
      com.badlogic.gdx.Gdx.gl.glTexImage2D(target, miplevel, pixmap.getGLInternalFormat(), pixmap.getWidth(), pixmap.getHeight(), 0, pixmap.getGLFormat(), pixmap.getGLType(), pixmap.getPixels())
    }
    if (disposePixmap) {
      pixmap.dispose()
    } else ()
  }
}