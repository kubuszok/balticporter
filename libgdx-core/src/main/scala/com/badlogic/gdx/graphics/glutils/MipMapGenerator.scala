package com.badlogic.gdx.graphics.glutils

object MipMapGenerator {
  private var useHWMipMap: scala.Boolean = true
  def setUseHardwareMipMap(useHWMipMap: scala.Boolean): scala.Unit = {
    MipMapGenerator.useHWMipMap = useHWMipMap
  }
  def generateMipMap(pixmap: com.badlogic.gdx.graphics.Pixmap, textureWidth: scala.Int, textureHeight: scala.Int): scala.Unit = {
    MipMapGenerator.generateMipMap(com.badlogic.gdx.graphics.GL20.GL_TEXTURE_2D, pixmap, textureWidth, textureHeight)
  }
  def generateMipMap(target: scala.Int, pixmap: com.badlogic.gdx.graphics.Pixmap, textureWidth: scala.Int, textureHeight: scala.Int): scala.Unit = {
    if (!MipMapGenerator.useHWMipMap) {
      MipMapGenerator.generateMipMapCPU(target, pixmap, textureWidth, textureHeight)
      return
    } else ()
    if (((com.badlogic.gdx.Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android) || (com.badlogic.gdx.Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.WebGL)) || (com.badlogic.gdx.Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.iOS)) {
      MipMapGenerator.generateMipMapGLES20(target, pixmap)
    } else {
      MipMapGenerator.generateMipMapDesktop(target, pixmap, textureWidth, textureHeight)
    }
  }
  private def generateMipMapGLES20(target: scala.Int, pixmap: com.badlogic.gdx.graphics.Pixmap): scala.Unit = {
    com.badlogic.gdx.Gdx.gl.glTexImage2D(target, 0, pixmap.getGLInternalFormat(), pixmap.getWidth(), pixmap.getHeight(), 0, pixmap.getGLFormat(), pixmap.getGLType(), pixmap.getPixels())
    com.badlogic.gdx.Gdx.gl20.glGenerateMipmap(target)
  }
  private def generateMipMapDesktop(target: scala.Int, pixmap: com.badlogic.gdx.graphics.Pixmap, textureWidth: scala.Int, textureHeight: scala.Int): scala.Unit = {
    if (((com.badlogic.gdx.Gdx.graphics.supportsExtension("GL_ARB_framebuffer_object") || com.badlogic.gdx.Gdx.graphics.supportsExtension("GL_EXT_framebuffer_object")) || com.badlogic.gdx.Gdx.gl20.getClass().getName().equals("com.badlogic.gdx.backends.lwjgl3.angle.Lwjgl3GLES20")) || (com.badlogic.gdx.Gdx.gl30 != null)) {
      com.badlogic.gdx.Gdx.gl.glTexImage2D(target, 0, pixmap.getGLInternalFormat(), pixmap.getWidth(), pixmap.getHeight(), 0, pixmap.getGLFormat(), pixmap.getGLType(), pixmap.getPixels())
      com.badlogic.gdx.Gdx.gl20.glGenerateMipmap(target)
    } else {
      MipMapGenerator.generateMipMapCPU(target, pixmap, textureWidth, textureHeight)
    }
  }
  private def generateMipMapCPU(target: scala.Int, pixmap$arg: com.badlogic.gdx.graphics.Pixmap, textureWidth: scala.Int, textureHeight: scala.Int): scala.Unit = {
    var pixmap: com.badlogic.gdx.graphics.Pixmap = pixmap$arg
    com.badlogic.gdx.Gdx.gl.glTexImage2D(target, 0, pixmap.getGLInternalFormat(), pixmap.getWidth(), pixmap.getHeight(), 0, pixmap.getGLFormat(), pixmap.getGLType(), pixmap.getPixels())
    if ((com.badlogic.gdx.Gdx.gl20 == null) && (textureWidth != textureHeight)) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("texture width and height must be square when using mipmapping.")
    } else ()
    var width: scala.Int = pixmap.getWidth() / 2
    var height: scala.Int = pixmap.getHeight() / 2
    var level: scala.Int = 1
    while (true) {
      val tmp: com.badlogic.gdx.graphics.Pixmap = new com.badlogic.gdx.graphics.Pixmap(width, height, pixmap.getFormat())
      tmp.setBlending(com.badlogic.gdx.graphics.Pixmap.Blending.None)
      tmp.drawPixmap(pixmap, 0, 0, pixmap.getWidth(), pixmap.getHeight(), 0, 0, width, height)
      if (level > 1) {
        pixmap.dispose()
      } else ()
      pixmap = tmp
      com.badlogic.gdx.Gdx.gl.glTexImage2D(target, level, pixmap.getGLInternalFormat(), pixmap.getWidth(), pixmap.getHeight(), 0, pixmap.getGLFormat(), pixmap.getGLType(), pixmap.getPixels())
      width = pixmap.getWidth() / 2
      height = pixmap.getHeight() / 2
      if ((width == 0) && (height == 0)) {
        /* break */ ()
      } else ()
      if (width == 0) {
        width = 1
      } else ()
      if (height == 0) {
        height = 1
      } else ()
      level = level + 1
    }
  }
}