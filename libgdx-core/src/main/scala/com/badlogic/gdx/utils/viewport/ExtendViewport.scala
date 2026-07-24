package com.badlogic.gdx.utils.viewport

class ExtendViewport extends com.badlogic.gdx.utils.viewport.Viewport {
  private var minWorldWidth: scala.Float = 0.0f
  private var minWorldHeight: scala.Float = 0.0f
  private var maxWorldWidth: scala.Float = 0.0f
  private var maxWorldHeight: scala.Float = 0.0f
  private var scaling: com.badlogic.gdx.utils.Scaling = com.badlogic.gdx.utils.Scaling.fit
  def this(minWorldWidth: scala.Float, minWorldHeight: scala.Float, maxWorldWidth: scala.Float, maxWorldHeight: scala.Float, camera: com.badlogic.gdx.graphics.Camera) = {
    this()
    this.minWorldWidth = minWorldWidth
    this.minWorldHeight = minWorldHeight
    this.maxWorldWidth = maxWorldWidth
    this.maxWorldHeight = maxWorldHeight
    this.setCamera(camera)
  }
  def this(minWorldWidth: scala.Float, minWorldHeight: scala.Float) = {
    this(minWorldWidth, minWorldHeight, 0, 0, new com.badlogic.gdx.graphics.OrthographicCamera())
  }
  def this(minWorldWidth: scala.Float, minWorldHeight: scala.Float, camera: com.badlogic.gdx.graphics.Camera) = {
    this(minWorldWidth, minWorldHeight, 0, 0, camera)
  }
  def this(minWorldWidth: scala.Float, minWorldHeight: scala.Float, maxWorldWidth: scala.Float, maxWorldHeight: scala.Float) = {
    this(minWorldWidth, minWorldHeight, maxWorldWidth, maxWorldHeight, new com.badlogic.gdx.graphics.OrthographicCamera())
  }
  def update(screenWidth: scala.Int, screenHeight: scala.Int, centerCamera: scala.Boolean): scala.Unit = {
    var worldWidth: scala.Float = this.minWorldWidth
    var worldHeight: scala.Float = this.minWorldHeight
    val scaled: com.badlogic.gdx.math.Vector2 = this.scaling.apply(worldWidth, worldHeight, screenWidth, screenHeight)
    var viewportWidth: scala.Int = java.lang.Math.round(scaled.x)
    var viewportHeight: scala.Int = java.lang.Math.round(scaled.y)
    if (viewportWidth < screenWidth) {
      val toViewportSpace: scala.Float = viewportHeight / worldHeight
      val toWorldSpace: scala.Float = worldHeight / viewportHeight
      var lengthen: scala.Float = (screenWidth - viewportWidth) * toWorldSpace
      if (this.maxWorldWidth > 0) {
        lengthen = java.lang.Math.min(lengthen, this.maxWorldWidth - this.minWorldWidth)
      } else ()
      worldWidth = worldWidth + lengthen
      viewportWidth = viewportWidth + java.lang.Math.round(lengthen * toViewportSpace)
    } else ()
    if (viewportHeight < screenHeight) {
      val toViewportSpace: scala.Float = viewportWidth / worldWidth
      val toWorldSpace: scala.Float = worldWidth / viewportWidth
      var lengthen: scala.Float = (screenHeight - viewportHeight) * toWorldSpace
      if (this.maxWorldHeight > 0) {
        lengthen = java.lang.Math.min(lengthen, this.maxWorldHeight - this.minWorldHeight)
      } else ()
      worldHeight = worldHeight + lengthen
      viewportHeight = viewportHeight + java.lang.Math.round(lengthen * toViewportSpace)
    } else ()
    this.setWorldSize(worldWidth, worldHeight)
    this.setScreenBounds((screenWidth - viewportWidth) / 2, (screenHeight - viewportHeight) / 2, viewportWidth, viewportHeight)
    this.apply(centerCamera)
  }
  def getMinWorldWidth(): scala.Float = {
    return this.minWorldWidth
  }
  def setMinWorldWidth(minWorldWidth: scala.Float): scala.Unit = {
    this.minWorldWidth = minWorldWidth
  }
  def getMinWorldHeight(): scala.Float = {
    return this.minWorldHeight
  }
  def setMinWorldHeight(minWorldHeight: scala.Float): scala.Unit = {
    this.minWorldHeight = minWorldHeight
  }
  def getMaxWorldWidth(): scala.Float = {
    return this.maxWorldWidth
  }
  def setMaxWorldWidth(maxWorldWidth: scala.Float): scala.Unit = {
    this.maxWorldWidth = maxWorldWidth
  }
  def getMaxWorldHeight(): scala.Float = {
    return this.maxWorldHeight
  }
  def setMaxWorldHeight(maxWorldHeight: scala.Float): scala.Unit = {
    this.maxWorldHeight = maxWorldHeight
  }
  def setScaling(scaling: com.badlogic.gdx.utils.Scaling): scala.Unit = {
    this.scaling = scaling
  }
}