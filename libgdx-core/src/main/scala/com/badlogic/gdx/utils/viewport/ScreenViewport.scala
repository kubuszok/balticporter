package com.badlogic.gdx.utils.viewport

class ScreenViewport(camera$p: com.badlogic.gdx.graphics.Camera) extends com.badlogic.gdx.utils.viewport.Viewport {
  private var unitsPerPixel: scala.Float = 1
  def this() = {
    this(new com.badlogic.gdx.graphics.OrthographicCamera())
  }
  this.setCamera(camera$p)
  override def update(screenWidth: scala.Int, screenHeight: scala.Int, centerCamera: scala.Boolean): scala.Unit = {
    this.setScreenBounds(0, 0, screenWidth, screenHeight)
    this.setWorldSize(screenWidth * this.unitsPerPixel, screenHeight * this.unitsPerPixel)
    this.apply(centerCamera)
  }
  def getUnitsPerPixel(): scala.Float = {
    return this.unitsPerPixel
  }
  def setUnitsPerPixel(unitsPerPixel: scala.Float): scala.Unit = {
    this.unitsPerPixel = unitsPerPixel
  }
}