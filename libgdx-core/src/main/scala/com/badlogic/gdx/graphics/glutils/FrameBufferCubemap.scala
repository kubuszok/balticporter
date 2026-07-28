package com.badlogic.gdx.graphics.glutils

class FrameBufferCubemap extends com.badlogic.gdx.graphics.glutils.GLFrameBuffer[com.badlogic.gdx.graphics.Cubemap] {
  private var currentSide: scala.Int = 0
  def this(bufferBuilder: com.badlogic.gdx.graphics.glutils.GLFrameBuffer.GLFrameBufferBuilder[? <: com.badlogic.gdx.graphics.glutils.GLFrameBuffer[com.badlogic.gdx.graphics.Cubemap]]) = {
    this()
    this.bufferBuilder = bufferBuilder
    this.build()
  }
  def this(format: com.badlogic.gdx.graphics.Pixmap.Format, width: scala.Int, height: scala.Int, hasDepth: scala.Boolean, hasStencil: scala.Boolean) = {
    this()
    val frameBufferBuilder: com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferCubemapBuilder = new com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferCubemapBuilder(width, height)
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
  override def createTexture(attachmentSpec: com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferTextureAttachmentSpec): ?T = {
    val data: com.badlogic.gdx.graphics.glutils.GLOnlyTextureData = new com.badlogic.gdx.graphics.glutils.GLOnlyTextureData(this.bufferBuilder.width, this.bufferBuilder.height, 0, attachmentSpec.internalFormat, attachmentSpec.format, attachmentSpec.`type`)
    val result: com.badlogic.gdx.graphics.Cubemap = new com.badlogic.gdx.graphics.Cubemap(data, data, data, data, data, data)
    result.setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.Linear, com.badlogic.gdx.graphics.Texture.TextureFilter.Linear)
    result.setWrap(com.badlogic.gdx.graphics.Texture.TextureWrap.ClampToEdge, com.badlogic.gdx.graphics.Texture.TextureWrap.ClampToEdge)
    return result
  }
  @java.lang.Override
  override def disposeColorTexture(colorTexture: com.badlogic.gdx.graphics.Cubemap): scala.Unit = {
    colorTexture.dispose()
  }
  @java.lang.Override
  override def attachFrameBufferColorTexture(texture: com.badlogic.gdx.graphics.Cubemap): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    val glHandle: scala.Int = texture.getTextureObjectHandle()
    val sides: scala.Array[com.badlogic.gdx.graphics.Cubemap.CubemapSide] = com.badlogic.gdx.graphics.Cubemap.CubemapSide.values()
    for (side <- sides) {
      gl.glFramebufferTexture2D(com.badlogic.gdx.graphics.GL20.GL_FRAMEBUFFER, com.badlogic.gdx.graphics.GL20.GL_COLOR_ATTACHMENT0, side.glEnum, glHandle, 0)
    }
  }
  @java.lang.Override
  override def bind(): scala.Unit = {
    this.currentSide = -1
    super.bind()
  }
  def nextSide(): scala.Boolean = {
    if (this.currentSide > 5) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("No remaining sides.")
    } else {
      if (this.currentSide == 5) {
        return false
      } else ()
    }
    this.currentSide = this.currentSide + 1
    this.bindSide(this.getSide())
    return true
  }
  def bindSide(side: com.badlogic.gdx.graphics.Cubemap.CubemapSide): scala.Unit = {
    com.badlogic.gdx.Gdx.gl20.glFramebufferTexture2D(com.badlogic.gdx.graphics.GL20.GL_FRAMEBUFFER, com.badlogic.gdx.graphics.GL20.GL_COLOR_ATTACHMENT0, side.glEnum, this.getColorBufferTexture().getTextureObjectHandle(), 0)
  }
  def getSide(): com.badlogic.gdx.graphics.Cubemap.CubemapSide = {
    return if (this.currentSide < 0) null.asInstanceOf[com.badlogic.gdx.graphics.Cubemap.CubemapSide] else FrameBufferCubemap.cubemapSides(this.currentSide)
  }
}
object FrameBufferCubemap {
  export com.badlogic.gdx.graphics.glutils.GLFrameBuffer.{cubemapSides => _, *}
  private final val cubemapSides: scala.Array[com.badlogic.gdx.graphics.Cubemap.CubemapSide] = com.badlogic.gdx.graphics.Cubemap.CubemapSide.values()
}