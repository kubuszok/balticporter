package com.badlogic.gdx.utils.viewport

class ScreenViewport extends com.badlogic.gdx.utils.viewport.Viewport {
  private var unitsPerPixel: scala.Float = 1
  def this(camera: com.badlogic.gdx.graphics.Camera) = {
    this()
    this.setCamera(camera)
  }
  def update(screenWidth: scala.Int, screenHeight: scala.Int, centerCamera: scala.Boolean): scala.Unit = {
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