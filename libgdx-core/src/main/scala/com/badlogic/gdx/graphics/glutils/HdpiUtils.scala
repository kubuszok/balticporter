package com.badlogic.gdx.graphics.glutils

object HdpiUtils {
  private var mode: com.badlogic.gdx.graphics.glutils.HdpiMode = com.badlogic.gdx.graphics.glutils.HdpiMode.Logical
  def setMode(mode: com.badlogic.gdx.graphics.glutils.HdpiMode): scala.Unit = {
    HdpiUtils.mode = mode
  }
  def glScissor(x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int): scala.Unit = {
    if ((HdpiUtils.mode == com.badlogic.gdx.graphics.glutils.HdpiMode.Logical) && ((com.badlogic.gdx.Gdx.graphics.getWidth() != com.badlogic.gdx.Gdx.graphics.getBackBufferWidth()) || (com.badlogic.gdx.Gdx.graphics.getHeight() != com.badlogic.gdx.Gdx.graphics.getBackBufferHeight()))) {
      com.badlogic.gdx.Gdx.gl.glScissor(HdpiUtils.toBackBufferX(x), HdpiUtils.toBackBufferY(y), HdpiUtils.toBackBufferX(width), HdpiUtils.toBackBufferY(height))
    } else {
      com.badlogic.gdx.Gdx.gl.glScissor(x, y, width, height)
    }
  }
  def glViewport(x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int): scala.Unit = {
    if ((HdpiUtils.mode == com.badlogic.gdx.graphics.glutils.HdpiMode.Logical) && ((com.badlogic.gdx.Gdx.graphics.getWidth() != com.badlogic.gdx.Gdx.graphics.getBackBufferWidth()) || (com.badlogic.gdx.Gdx.graphics.getHeight() != com.badlogic.gdx.Gdx.graphics.getBackBufferHeight()))) {
      com.badlogic.gdx.Gdx.gl.glViewport(HdpiUtils.toBackBufferX(x), HdpiUtils.toBackBufferY(y), HdpiUtils.toBackBufferX(width), HdpiUtils.toBackBufferY(height))
    } else {
      com.badlogic.gdx.Gdx.gl.glViewport(x, y, width, height)
    }
  }
  def toLogicalX(backBufferX: scala.Int): scala.Int = {
    return ((backBufferX * com.badlogic.gdx.Gdx.graphics.getWidth()) / com.badlogic.gdx.Gdx.graphics.getBackBufferWidth().asInstanceOf[scala.Float]).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
  }
  def toLogicalY(backBufferY: scala.Int): scala.Int = {
    return ((backBufferY * com.badlogic.gdx.Gdx.graphics.getHeight()) / com.badlogic.gdx.Gdx.graphics.getBackBufferHeight().asInstanceOf[scala.Float]).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
  }
  def toBackBufferX(logicalX: scala.Int): scala.Int = {
    return ((logicalX * com.badlogic.gdx.Gdx.graphics.getBackBufferWidth()) / com.badlogic.gdx.Gdx.graphics.getWidth().asInstanceOf[scala.Float]).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
  }
  def toBackBufferY(logicalY: scala.Int): scala.Int = {
    return ((logicalY * com.badlogic.gdx.Gdx.graphics.getBackBufferHeight()) / com.badlogic.gdx.Gdx.graphics.getHeight().asInstanceOf[scala.Float]).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
  }
}