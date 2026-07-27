package com.badlogic.gdx.graphics.glutils

class CustomTexture3DData(width$p: scala.Int, height$p: scala.Int, depth$p: scala.Int, mipMapLevel$p: scala.Int, glFormat$p: scala.Int, glInternalFormat$p: scala.Int, glType$p: scala.Int) extends com.badlogic.gdx.graphics.Texture3DData {
  private var width: scala.Int = 0
  private var height: scala.Int = 0
  private var depth: scala.Int = 0
  private var mipMapLevel: scala.Int = 0
  private var glFormat: scala.Int = 0
  private var glInternalFormat: scala.Int = 0
  private var glType: scala.Int = 0
  private var pixels: java.nio.ByteBuffer = null.asInstanceOf[java.nio.ByteBuffer]
  this.width = width$p
  this.height = height$p
  this.depth = depth$p
  this.glFormat = glFormat$p
  this.glInternalFormat = glInternalFormat$p
  this.glType = glType$p
  this.mipMapLevel = mipMapLevel$p
  def isPrepared(): scala.Boolean = {
    return true
  }
  def prepare(): scala.Unit = {
    ()
  }
  def getWidth(): scala.Int = {
    return this.width
  }
  def getHeight(): scala.Int = {
    return this.height
  }
  def getDepth(): scala.Int = {
    return this.depth
  }
  def useMipMaps(): scala.Boolean = {
    return false
  }
  def isManaged(): scala.Boolean = {
    return this.pixels != null
  }
  def getInternalFormat(): scala.Int = {
    return this.glInternalFormat
  }
  def getGLType(): scala.Int = {
    return this.glType
  }
  def getGLFormat(): scala.Int = {
    return this.glFormat
  }
  def getMipMapLevel(): scala.Int = {
    return this.mipMapLevel
  }
  def getPixels(): java.nio.ByteBuffer = {
    if (this.pixels == null) {
      var numChannels: scala.Int = 0
      if ((((this.glFormat == com.badlogic.gdx.graphics.GL30.GL_RED) || (this.glFormat == com.badlogic.gdx.graphics.GL30.GL_RED_INTEGER)) || (this.glFormat == com.badlogic.gdx.graphics.GL30.GL_LUMINANCE)) || (this.glFormat == com.badlogic.gdx.graphics.GL30.GL_ALPHA)) {
        numChannels = 1
      } else {
        if (((this.glFormat == com.badlogic.gdx.graphics.GL30.GL_RG) || (this.glFormat == com.badlogic.gdx.graphics.GL30.GL_RG_INTEGER)) || (this.glFormat == com.badlogic.gdx.graphics.GL30.GL_LUMINANCE_ALPHA)) {
          numChannels = 2
        } else {
          if ((this.glFormat == com.badlogic.gdx.graphics.GL30.GL_RGB) || (this.glFormat == com.badlogic.gdx.graphics.GL30.GL_RGB_INTEGER)) {
            numChannels = 3
          } else {
            if ((this.glFormat == com.badlogic.gdx.graphics.GL30.GL_RGBA) || (this.glFormat == com.badlogic.gdx.graphics.GL30.GL_RGBA_INTEGER)) {
              numChannels = 4
            } else {
              throw new com.badlogic.gdx.utils.GdxRuntimeException("unsupported glFormat: " + this.glFormat)
            }
          }
        }
      }
      var bytesPerChannel: scala.Int = 0
      if ((this.glType == com.badlogic.gdx.graphics.GL30.GL_UNSIGNED_BYTE) || (this.glType == com.badlogic.gdx.graphics.GL30.GL_BYTE)) {
        bytesPerChannel = 1
      } else {
        if (((this.glType == com.badlogic.gdx.graphics.GL30.GL_UNSIGNED_SHORT) || (this.glType == com.badlogic.gdx.graphics.GL30.GL_SHORT)) || (this.glType == com.badlogic.gdx.graphics.GL30.GL_HALF_FLOAT)) {
          bytesPerChannel = 2
        } else {
          if (((this.glType == com.badlogic.gdx.graphics.GL30.GL_UNSIGNED_INT) || (this.glType == com.badlogic.gdx.graphics.GL30.GL_INT)) || (this.glType == com.badlogic.gdx.graphics.GL30.GL_FLOAT)) {
            bytesPerChannel = 4
          } else {
            throw new com.badlogic.gdx.utils.GdxRuntimeException("unsupported glType: " + this.glType)
          }
        }
      }
      val bytesPerPixel: scala.Int = numChannels * bytesPerChannel
      this.pixels = com.badlogic.gdx.utils.BufferUtils.newByteBuffer(((this.width * this.height) * this.depth) * bytesPerPixel)
    } else ()
    return this.pixels
  }
  def consume3DData(): scala.Unit = {
    com.badlogic.gdx.Gdx.gl30.glTexImage3D(com.badlogic.gdx.graphics.GL30.GL_TEXTURE_3D, this.mipMapLevel, this.glInternalFormat, this.width, this.height, this.depth, 0, this.glFormat, this.glType, this.pixels)
  }
}