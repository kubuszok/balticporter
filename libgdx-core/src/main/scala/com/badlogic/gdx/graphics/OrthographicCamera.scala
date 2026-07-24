package com.badlogic.gdx.graphics

class OrthographicCamera extends com.badlogic.gdx.graphics.Camera {
  var zoom: scala.Float = 1
  private final val tmp: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  def this(viewportWidth: scala.Float, viewportHeight: scala.Float) = {
    this()
    this.viewportWidth = viewportWidth
    this.viewportHeight = viewportHeight
    this.near = 0
    this.update()
  }
  def this() = {
    this()
    this.near = 0
  }
  def update(): scala.Unit = {
    this.update(true)
  }
  def update(updateFrustum: scala.Boolean): scala.Unit = {
    projection.setToOrtho((this.zoom * (-viewportWidth)) / 2, this.zoom * (viewportWidth / 2), this.zoom * (-(viewportHeight / 2)), (this.zoom * viewportHeight) / 2, near, far)
    view.setToLookAt(direction, up)
    view.translate(-this.position.x, -this.position.y, -this.position.z)
    combined.set(projection)
    com.badlogic.gdx.math.Matrix4.mul(this.combined.`val`, this.view.`val`)
    if (updateFrustum) {
      invProjectionView.set(combined)
      com.badlogic.gdx.math.Matrix4.inv(this.invProjectionView.`val`)
      frustum.update(invProjectionView)
    } else ()
  }
  def setToOrtho(yDown: scala.Boolean): scala.Unit = {
    this.setToOrtho(yDown, com.badlogic.gdx.Gdx.graphics.getWidth(), com.badlogic.gdx.Gdx.graphics.getHeight())
  }
  def setToOrtho(yDown: scala.Boolean, viewportWidth: scala.Float, viewportHeight: scala.Float): scala.Unit = {
    if (yDown) {
      up.set(0, -1, 0)
      direction.set(0, 0, 1)
    } else {
      up.set(0, 1, 0)
      direction.set(0, 0, -1)
    }
    position.set((this.zoom * viewportWidth) / 2.0f, (this.zoom * viewportHeight) / 2.0f, 0)
    this.viewportWidth = viewportWidth
    this.viewportHeight = viewportHeight
    this.update()
  }
  def rotate(angle: scala.Float): scala.Unit = {
    this.rotate(direction, angle)
  }
  def translate(x: scala.Float, y: scala.Float): scala.Unit = {
    this.translate(x, y, 0)
  }
  def translate(vec: com.badlogic.gdx.math.Vector2): scala.Unit = {
    this.translate(vec.x, vec.y, 0)
  }
}