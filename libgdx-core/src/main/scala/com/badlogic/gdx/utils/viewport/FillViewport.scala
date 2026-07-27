package com.badlogic.gdx.utils.viewport

class FillViewport extends com.badlogic.gdx.utils.viewport.ScalingViewport {
  def this(worldWidth: scala.Float, worldHeight: scala.Float) = {
    this()
    this.scaling = com.badlogic.gdx.utils.Scaling.fill
    this.setWorldSize(worldWidth, worldHeight)
    this.setCamera(new com.badlogic.gdx.graphics.OrthographicCamera())
  }
  def this(worldWidth: scala.Float, worldHeight: scala.Float, camera: com.badlogic.gdx.graphics.Camera) = {
    this()
    this.scaling = com.badlogic.gdx.utils.Scaling.fill
    this.setWorldSize(worldWidth, worldHeight)
    this.setCamera(camera)
  }
}