package com.badlogic.gdx.utils.viewport

class ScalingViewport extends com.badlogic.gdx.utils.viewport.Viewport {
  var scaling: com.badlogic.gdx.utils.Scaling = null.asInstanceOf[com.badlogic.gdx.utils.Scaling]
  def this(scaling: com.badlogic.gdx.utils.Scaling, worldWidth: scala.Float, worldHeight: scala.Float, camera: com.badlogic.gdx.graphics.Camera) = {
    this()
    this.scaling = scaling
    this.setWorldSize(worldWidth, worldHeight)
    this.setCamera(camera)
  }
  def this(scaling: com.badlogic.gdx.utils.Scaling, worldWidth: scala.Float, worldHeight: scala.Float) = {
    this(scaling, worldWidth, worldHeight, new com.badlogic.gdx.graphics.OrthographicCamera())
  }
  override def update(screenWidth: scala.Int, screenHeight: scala.Int, centerCamera: scala.Boolean): scala.Unit = {
    val scaled: com.badlogic.gdx.math.Vector2 = this.scaling.apply(this.getWorldWidth(), this.getWorldHeight(), screenWidth, screenHeight)
    val viewportWidth: scala.Int = java.lang.Math.round(scaled.x)
    val viewportHeight: scala.Int = java.lang.Math.round(scaled.y)
    this.setScreenBounds((screenWidth - viewportWidth) / 2, (screenHeight - viewportHeight) / 2, viewportWidth, viewportHeight)
    this.apply(centerCamera)
  }
  def getScaling(): com.badlogic.gdx.utils.Scaling = {
    return this.scaling
  }
  def setScaling(scaling: com.badlogic.gdx.utils.Scaling): scala.Unit = {
    this.scaling = scaling
  }
}