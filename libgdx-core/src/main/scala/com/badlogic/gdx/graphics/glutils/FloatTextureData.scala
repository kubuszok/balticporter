package com.badlogic.gdx.graphics.glutils

class FloatTextureData(w: scala.Int, h: scala.Int, internalFormat$p: scala.Int, format$p: scala.Int, type$p: scala.Int, isGpuOnly$p: scala.Boolean) extends com.badlogic.gdx.graphics.TextureData {
  var width: scala.Int = 0
  var height: scala.Int = 0
  var internalFormat: scala.Int = 0
  var format: scala.Int = 0
  var `type`: scala.Int = 0
  var isGpuOnly: scala.Boolean = false
  var isPrepared$field: scala.Boolean = false
  var buffer: java.nio.FloatBuffer = null.asInstanceOf[java.nio.FloatBuffer]
  this.width = w
  this.height = h
  this.internalFormat = internalFormat$p
  this.format = format$p
  this.`type` = type$p
  this.isGpuOnly = isGpuOnly$p
  @java.lang.Override
  def getType(): com.badlogic.gdx.graphics.TextureData.TextureDataType = {
    return com.badlogic.gdx.graphics.TextureData.TextureDataType.Custom
  }
  @java.lang.Override
  def isPrepared(): scala.Boolean = {
    return this.isPrepared$field
  }
  @java.lang.Override
  def prepare(): scala.Unit = {
    if (this.isPrepared$field) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Already prepared")
    } else ()
    if (!this.isGpuOnly) {
      var amountOfFloats: scala.Int = 4
      if (com.badlogic.gdx.Gdx.graphics.getGLVersion().getType().equals(com.badlogic.gdx.graphics.glutils.GLVersion.Type.OpenGL)) {
        if ((this.internalFormat == com.badlogic.gdx.graphics.GL30.GL_RGBA16F) || (this.internalFormat == com.badlogic.gdx.graphics.GL30.GL_RGBA32F)) {
          amountOfFloats = 4
        } else ()
        if ((this.internalFormat == com.badlogic.gdx.graphics.GL30.GL_RGB16F) || (this.internalFormat == com.badlogic.gdx.graphics.GL30.GL_RGB32F)) {
          amountOfFloats = 3
        } else ()
        if ((this.internalFormat == com.badlogic.gdx.graphics.GL30.GL_RG16F) || (this.internalFormat == com.badlogic.gdx.graphics.GL30.GL_RG32F)) {
          amountOfFloats = 2
        } else ()
        if ((this.internalFormat == com.badlogic.gdx.graphics.GL30.GL_R16F) || (this.internalFormat == com.badlogic.gdx.graphics.GL30.GL_R32F)) {
          amountOfFloats = 1
        } else ()
      } else ()
      this.buffer = com.badlogic.gdx.utils.BufferUtils.newFloatBuffer((this.width * this.height) * amountOfFloats)
    } else ()
    this.isPrepared$field = true
  }
  @java.lang.Override
  def consumeCustomData(target: scala.Int): scala.Unit = {
    if (((com.badlogic.gdx.Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android) || (com.badlogic.gdx.Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.iOS)) || ((com.badlogic.gdx.Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.WebGL) && (!com.badlogic.gdx.Gdx.graphics.isGL30Available()))) {
      if (!com.badlogic.gdx.Gdx.graphics.supportsExtension("OES_texture_float")) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Extension OES_texture_float not supported!")
      } else ()
      com.badlogic.gdx.Gdx.gl.glTexImage2D(target, 0, com.badlogic.gdx.graphics.GL20.GL_RGBA, this.width, this.height, 0, com.badlogic.gdx.graphics.GL20.GL_RGBA, com.badlogic.gdx.graphics.GL20.GL_FLOAT, this.buffer)
    } else {
      if (!com.badlogic.gdx.Gdx.graphics.isGL30Available()) {
        if (!com.badlogic.gdx.Gdx.graphics.supportsExtension("GL_ARB_texture_float")) {
          throw new com.badlogic.gdx.utils.GdxRuntimeException("Extension GL_ARB_texture_float not supported!")
        } else ()
      } else ()
      com.badlogic.gdx.Gdx.gl.glTexImage2D(target, 0, this.internalFormat, this.width, this.height, 0, this.format, com.badlogic.gdx.graphics.GL20.GL_FLOAT, this.buffer)
    }
  }
  @java.lang.Override
  def consumePixmap(): com.badlogic.gdx.graphics.Pixmap = {
    throw new com.badlogic.gdx.utils.GdxRuntimeException("This TextureData implementation does not return a Pixmap")
  }
  @java.lang.Override
  def disposePixmap(): scala.Boolean = {
    throw new com.badlogic.gdx.utils.GdxRuntimeException("This TextureData implementation does not return a Pixmap")
  }
  @java.lang.Override
  def getWidth(): scala.Int = {
    return this.width
  }
  @java.lang.Override
  def getHeight(): scala.Int = {
    return this.height
  }
  @java.lang.Override
  def getFormat(): com.badlogic.gdx.graphics.Pixmap.Format = {
    return com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888
  }
  @java.lang.Override
  def useMipMaps(): scala.Boolean = {
    return false
  }
  @java.lang.Override
  def isManaged(): scala.Boolean = {
    return true
  }
  def getBuffer(): java.nio.FloatBuffer = {
    return this.buffer
  }
}
object FloatTextureData {
  export com.badlogic.gdx.graphics.TextureData.*
}