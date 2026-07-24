package com.badlogic.gdx.utils

object ScreenUtils {
  def clear(color: com.badlogic.gdx.graphics.Color): scala.Unit = {
    ScreenUtils.clear(color.r, color.g, color.b, color.a, false)
  }
  def clear(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): scala.Unit = {
    ScreenUtils.clear(r, g, b, a, false)
  }
  def clear(color: com.badlogic.gdx.graphics.Color, clearDepth: scala.Boolean): scala.Unit = {
    ScreenUtils.clear(color.r, color.g, color.b, color.a, clearDepth)
  }
  def clear(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float, clearDepth: scala.Boolean): scala.Unit = {
    ScreenUtils.clear(r, g, b, a, clearDepth, false)
  }
  def clear(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float, clearDepth: scala.Boolean, applyAntialiasing: scala.Boolean): scala.Unit = {
    com.badlogic.gdx.Gdx.gl.glClearColor(r, g, b, a)
    var mask: scala.Int = com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT
    if (clearDepth) {
      mask = mask | com.badlogic.gdx.graphics.GL20.GL_DEPTH_BUFFER_BIT
    } else ()
    if (applyAntialiasing && com.badlogic.gdx.Gdx.graphics.getBufferFormat().coverageSampling) {
      mask = mask | com.badlogic.gdx.graphics.GL20.GL_COVERAGE_BUFFER_BIT_NV
    } else ()
    com.badlogic.gdx.Gdx.gl.glClear(mask)
  }
  def getFrameBufferTexture(): com.badlogic.gdx.graphics.g2d.TextureRegion = {
    val w: scala.Int = com.badlogic.gdx.Gdx.graphics.getBackBufferWidth()
    val h: scala.Int = com.badlogic.gdx.Gdx.graphics.getBackBufferHeight()
    return ScreenUtils.getFrameBufferTexture(0, 0, w, h)
  }
  def getFrameBufferTexture(x: scala.Int, y: scala.Int, w: scala.Int, h: scala.Int): com.badlogic.gdx.graphics.g2d.TextureRegion = {
    val potW: scala.Int = com.badlogic.gdx.math.MathUtils.nextPowerOfTwo(w)
    val potH: scala.Int = com.badlogic.gdx.math.MathUtils.nextPowerOfTwo(h)
    val pixmap: com.badlogic.gdx.graphics.Pixmap = com.badlogic.gdx.graphics.Pixmap.createFromFrameBuffer(x, y, w, h)
    val potPixmap: com.badlogic.gdx.graphics.Pixmap = new com.badlogic.gdx.graphics.Pixmap(potW, potH, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888)
    potPixmap.setBlending(com.badlogic.gdx.graphics.Pixmap.Blending.None)
    potPixmap.drawPixmap(pixmap, 0, 0)
    val texture: com.badlogic.gdx.graphics.Texture = new com.badlogic.gdx.graphics.Texture(potPixmap)
    val textureRegion: com.badlogic.gdx.graphics.g2d.TextureRegion = new com.badlogic.gdx.graphics.g2d.TextureRegion(texture, 0, h, w, -h)
    potPixmap.dispose()
    pixmap.dispose()
    return textureRegion
  }
  def getFrameBufferPixmap(x: scala.Int, y: scala.Int, w: scala.Int, h: scala.Int): com.badlogic.gdx.graphics.Pixmap = {
    return com.badlogic.gdx.graphics.Pixmap.createFromFrameBuffer(x, y, w, h)
  }
  def getFrameBufferPixels(flipY: scala.Boolean): scala.Array[scala.Byte] = {
    val w: scala.Int = com.badlogic.gdx.Gdx.graphics.getBackBufferWidth()
    val h: scala.Int = com.badlogic.gdx.Gdx.graphics.getBackBufferHeight()
    return ScreenUtils.getFrameBufferPixels(0, 0, w, h, flipY)
  }
  def getFrameBufferPixels(x: scala.Int, y: scala.Int, w: scala.Int, h: scala.Int, flipY: scala.Boolean): scala.Array[scala.Byte] = {
    com.badlogic.gdx.Gdx.gl.glPixelStorei(com.badlogic.gdx.graphics.GL20.GL_PACK_ALIGNMENT, 1)
    val pixels: java.nio.ByteBuffer = com.badlogic.gdx.utils.BufferUtils.newByteBuffer((w * h) * 4)
    com.badlogic.gdx.Gdx.gl.glReadPixels(x, y, w, h, com.badlogic.gdx.graphics.GL20.GL_RGBA, com.badlogic.gdx.graphics.GL20.GL_UNSIGNED_BYTE, pixels)
    val numBytes: scala.Int = (w * h) * 4
    val lines: scala.Array[scala.Byte] = new Array[scala.Byte](numBytes)
    if (flipY) {
      val numBytesPerLine: scala.Int = w * 4;
      { var i: scala.Int = 0; while (i < h) { {
        pixels.asInstanceOf[java.nio.Buffer].position(((h - i) - 1) * numBytesPerLine)
        pixels.get(lines, i * numBytesPerLine, numBytesPerLine)
      }; i = i + 1 } }
    } else {
      pixels.asInstanceOf[java.nio.Buffer].clear()
      pixels.get(lines)
    }
    return lines
  }
}