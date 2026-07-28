package com.badlogic.gdx.graphics.glutils

class FrameBuffer extends com.badlogic.gdx.graphics.glutils.GLFrameBuffer[com.badlogic.gdx.graphics.Texture] {
  def this(bufferBuilder: com.badlogic.gdx.graphics.glutils.GLFrameBuffer.GLFrameBufferBuilder[? <: com.badlogic.gdx.graphics.glutils.GLFrameBuffer[com.badlogic.gdx.graphics.Texture]]) = {
    this()
    this.bufferBuilder = bufferBuilder
    this.build()
  }
  def this(format: com.badlogic.gdx.graphics.Pixmap.Format, width: scala.Int, height: scala.Int, hasDepth: scala.Boolean, hasStencil: scala.Boolean) = {
    this()
    val frameBufferBuilder: com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferBuilder = new com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferBuilder(width, height)
    frameBufferBuilder.addBasicColorTextureAttachment(format)
    if (hasDepth) {
      frameBufferBuilder.addBasicDepthRenderBuffer()
    } else ()
    if (hasStencil) {
      frameBufferBuilder.addBasicStencilRenderBuffer()
    } else ()
    this.bufferBuilder = frameBufferBuilder
    this.build()
  }
  def this(format: com.badlogic.gdx.graphics.Pixmap.Format, width: scala.Int, height: scala.Int, hasDepth: scala.Boolean) = {
    this(format, width, height, hasDepth, false)
  }
  @java.lang.Override
  override def createTexture(attachmentSpec: com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferTextureAttachmentSpec): com.badlogic.gdx.graphics.Texture = {
    val data: com.badlogic.gdx.graphics.glutils.GLOnlyTextureData = new com.badlogic.gdx.graphics.glutils.GLOnlyTextureData(this.bufferBuilder.width, this.bufferBuilder.height, 0, attachmentSpec.internalFormat, attachmentSpec.format, attachmentSpec.`type`)
    val result: com.badlogic.gdx.graphics.Texture = new com.badlogic.gdx.graphics.Texture(data)
    val webGLDepth: scala.Boolean = attachmentSpec.isDepth && (com.badlogic.gdx.Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.WebGL)
    if (!webGLDepth) {
      result.setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.Linear, com.badlogic.gdx.graphics.Texture.TextureFilter.Linear)
    } else ()
    result.setWrap(com.badlogic.gdx.graphics.Texture.TextureWrap.ClampToEdge, com.badlogic.gdx.graphics.Texture.TextureWrap.ClampToEdge)
    return result
  }
  @java.lang.Override
  override def disposeColorTexture(colorTexture: com.badlogic.gdx.graphics.Texture): scala.Unit = {
    colorTexture.dispose()
  }
  @java.lang.Override
  override def attachFrameBufferColorTexture(texture: com.badlogic.gdx.graphics.Texture): scala.Unit = {
    com.badlogic.gdx.Gdx.gl20.glFramebufferTexture2D(com.badlogic.gdx.graphics.GL20.GL_FRAMEBUFFER, com.badlogic.gdx.graphics.GL20.GL_COLOR_ATTACHMENT0, com.badlogic.gdx.graphics.GL20.GL_TEXTURE_2D, texture.getTextureObjectHandle(), 0)
  }
}
object FrameBuffer {
  export com.badlogic.gdx.graphics.glutils.GLFrameBuffer.{unbind => _, *}
  override def unbind(): scala.Unit = {
    com.badlogic.gdx.graphics.glutils.GLFrameBuffer.unbind()
  }
}