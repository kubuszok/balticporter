package com.badlogic.gdx.graphics.glutils

class FloatFrameBuffer extends com.badlogic.gdx.graphics.glutils.FrameBuffer {
  def this(width: scala.Int, height: scala.Int, hasDepth: scala.Boolean) = {
    this()
    this.checkExtensions()
    var bufferBuilder: com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FloatFrameBufferBuilder = new com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FloatFrameBufferBuilder(width, height)
    bufferBuilder.addFloatAttachment(com.badlogic.gdx.graphics.GL30.GL_RGBA32F, com.badlogic.gdx.graphics.GL30.GL_RGBA, com.badlogic.gdx.graphics.GL30.GL_FLOAT, false)
    if (hasDepth) {
      bufferBuilder.addBasicDepthRenderBuffer()
    } else ()
    this.bufferBuilder = bufferBuilder
    this.build()
  }
  def this(bufferBuilder: com.badlogic.gdx.graphics.glutils.GLFrameBuffer.GLFrameBufferBuilder[? <: com.badlogic.gdx.graphics.glutils.GLFrameBuffer[com.badlogic.gdx.graphics.Texture]]) = {
    this()
    this.checkExtensions()
  }
  this.checkExtensions()
  def createTexture(attachmentSpec: com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferTextureAttachmentSpec): com.badlogic.gdx.graphics.Texture = {
    val data: com.badlogic.gdx.graphics.glutils.FloatTextureData = new com.badlogic.gdx.graphics.glutils.FloatTextureData(this.bufferBuilder.width, this.bufferBuilder.height, attachmentSpec.internalFormat, attachmentSpec.format, attachmentSpec.`type`, attachmentSpec.isGpuOnly)
    val result: com.badlogic.gdx.graphics.Texture = new com.badlogic.gdx.graphics.Texture(data)
    if ((com.badlogic.gdx.Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Desktop) || (com.badlogic.gdx.Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Applet)) {
      result.setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.Linear, com.badlogic.gdx.graphics.Texture.TextureFilter.Linear)
    } else {
      result.setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest, com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest)
    }
    result.setWrap(com.badlogic.gdx.graphics.Texture.TextureWrap.ClampToEdge, com.badlogic.gdx.graphics.Texture.TextureWrap.ClampToEdge)
    return result
  }
  private def checkExtensions(): scala.Unit = {
    if (com.badlogic.gdx.Gdx.graphics.isGL30Available() && (com.badlogic.gdx.Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.WebGL)) {
      if (!com.badlogic.gdx.Gdx.graphics.supportsExtension("EXT_color_buffer_float")) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Extension EXT_color_buffer_float not supported!")
      } else ()
    } else ()
  }
}