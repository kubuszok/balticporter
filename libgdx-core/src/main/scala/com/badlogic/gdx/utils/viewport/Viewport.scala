package com.badlogic.gdx.utils.viewport

abstract class Viewport {
  private var camera: com.badlogic.gdx.graphics.Camera = null.asInstanceOf[com.badlogic.gdx.graphics.Camera]
  private var worldWidth: scala.Float = 0.0f
  private var worldHeight: scala.Float = 0.0f
  private var screenX: scala.Int = 0
  private var screenY: scala.Int = 0
  private var screenWidth: scala.Int = 0
  private var screenHeight: scala.Int = 0
  private final val tmp: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  def apply(): scala.Unit = {
    this.apply(false)
  }
  def apply(centerCamera: scala.Boolean): scala.Unit = {
    com.badlogic.gdx.graphics.glutils.HdpiUtils.glViewport(this.screenX, this.screenY, this.screenWidth, this.screenHeight)
    this.camera.viewportWidth = this.worldWidth
    this.camera.viewportHeight = this.worldHeight
    if (centerCamera) {
      this.camera.position.set(this.worldWidth / 2, this.worldHeight / 2, 0)
    } else ()
    this.camera.update()
  }
  final def update(screenWidth: scala.Int, screenHeight: scala.Int): scala.Unit = {
    this.update(screenWidth, screenHeight, false)
  }
  def update(screenWidth: scala.Int, screenHeight: scala.Int, centerCamera: scala.Boolean): scala.Unit = {
    this.apply(centerCamera)
  }
  def unproject(touchCoords: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.math.Vector2 = {
    this.tmp.set(touchCoords.x, touchCoords.y, 1)
    this.camera.unproject(this.tmp, this.screenX, this.screenY, this.screenWidth, this.screenHeight)
    touchCoords.set(this.tmp.x, this.tmp.y)
    return touchCoords
  }
  def project(worldCoords: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.math.Vector2 = {
    this.tmp.set(worldCoords.x, worldCoords.y, 1)
    this.camera.project(this.tmp, this.screenX, this.screenY, this.screenWidth, this.screenHeight)
    worldCoords.set(this.tmp.x, this.tmp.y)
    return worldCoords
  }
  def unproject(screenCoords: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    this.camera.unproject(screenCoords, this.screenX, this.screenY, this.screenWidth, this.screenHeight)
    return screenCoords
  }
  def project(worldCoords: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    this.camera.project(worldCoords, this.screenX, this.screenY, this.screenWidth, this.screenHeight)
    return worldCoords
  }
  def getPickRay(touchX: scala.Float, touchY: scala.Float): com.badlogic.gdx.math.collision.Ray = {
    return this.camera.getPickRay(touchX, touchY, this.screenX, this.screenY, this.screenWidth, this.screenHeight)
  }
  def calculateScissors(batchTransform: com.badlogic.gdx.math.Matrix4, area: com.badlogic.gdx.math.Rectangle, scissor: com.badlogic.gdx.math.Rectangle): scala.Unit = {
    com.badlogic.gdx.scenes.scene2d.utils.ScissorStack.calculateScissors(this.camera, this.screenX, this.screenY, this.screenWidth, this.screenHeight, batchTransform, area, scissor)
  }
  def toScreenCoordinates(worldCoords: com.badlogic.gdx.math.Vector2, transformMatrix: com.badlogic.gdx.math.Matrix4): com.badlogic.gdx.math.Vector2 = {
    this.tmp.set(worldCoords.x, worldCoords.y, 0)
    this.tmp.mul(transformMatrix)
    this.camera.project(this.tmp, this.screenX, this.screenY, this.screenWidth, this.screenHeight)
    this.tmp.y = com.badlogic.gdx.Gdx.graphics.getHeight() - this.tmp.y
    worldCoords.x = this.tmp.x
    worldCoords.y = this.tmp.y
    return worldCoords
  }
  def getCamera(): com.badlogic.gdx.graphics.Camera = {
    return this.camera
  }
  def setCamera(camera: com.badlogic.gdx.graphics.Camera): scala.Unit = {
    this.camera = camera
  }
  def getWorldWidth(): scala.Float = {
    return this.worldWidth
  }
  def setWorldWidth(worldWidth: scala.Float): scala.Unit = {
    this.worldWidth = worldWidth
  }
  def getWorldHeight(): scala.Float = {
    return this.worldHeight
  }
  def setWorldHeight(worldHeight: scala.Float): scala.Unit = {
    this.worldHeight = worldHeight
  }
  def setWorldSize(worldWidth: scala.Float, worldHeight: scala.Float): scala.Unit = {
    this.worldWidth = worldWidth
    this.worldHeight = worldHeight
  }
  def getScreenX(): scala.Int = {
    return this.screenX
  }
  def setScreenX(screenX: scala.Int): scala.Unit = {
    this.screenX = screenX
  }
  def getScreenY(): scala.Int = {
    return this.screenY
  }
  def setScreenY(screenY: scala.Int): scala.Unit = {
    this.screenY = screenY
  }
  def getScreenWidth(): scala.Int = {
    return this.screenWidth
  }
  def setScreenWidth(screenWidth: scala.Int): scala.Unit = {
    this.screenWidth = screenWidth
  }
  def getScreenHeight(): scala.Int = {
    return this.screenHeight
  }
  def setScreenHeight(screenHeight: scala.Int): scala.Unit = {
    this.screenHeight = screenHeight
  }
  def setScreenPosition(screenX: scala.Int, screenY: scala.Int): scala.Unit = {
    this.screenX = screenX
    this.screenY = screenY
  }
  def setScreenSize(screenWidth: scala.Int, screenHeight: scala.Int): scala.Unit = {
    this.screenWidth = screenWidth
    this.screenHeight = screenHeight
  }
  def setScreenBounds(screenX: scala.Int, screenY: scala.Int, screenWidth: scala.Int, screenHeight: scala.Int): scala.Unit = {
    this.screenX = screenX
    this.screenY = screenY
    this.screenWidth = screenWidth
    this.screenHeight = screenHeight
  }
  def getLeftGutterWidth(): scala.Int = {
    return this.screenX
  }
  def getRightGutterX(): scala.Int = {
    return this.screenX + this.screenWidth
  }
  def getRightGutterWidth(): scala.Int = {
    return com.badlogic.gdx.Gdx.graphics.getWidth() - (this.screenX + this.screenWidth)
  }
  def getBottomGutterHeight(): scala.Int = {
    return this.screenY
  }
  def getTopGutterY(): scala.Int = {
    return this.screenY + this.screenHeight
  }
  def getTopGutterHeight(): scala.Int = {
    return com.badlogic.gdx.Gdx.graphics.getHeight() - (this.screenY + this.screenHeight)
  }
}