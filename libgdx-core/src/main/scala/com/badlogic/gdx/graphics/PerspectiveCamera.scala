package com.badlogic.gdx.graphics

class PerspectiveCamera extends com.badlogic.gdx.graphics.Camera {
  var fieldOfView: scala.Float = 67
  final val tmp: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  def this(fieldOfViewY: scala.Float, viewportWidth: scala.Float, viewportHeight: scala.Float) = {
    this()
    this.fieldOfView = fieldOfViewY
    this.viewportWidth = viewportWidth
    this.viewportHeight = viewportHeight
    this.update()
  }
  @java.lang.Override
  def update(): scala.Unit = {
    this.update(true)
  }
  @java.lang.Override
  def update(updateFrustum: scala.Boolean): scala.Unit = {
    val aspect: scala.Float = viewportWidth / viewportHeight
    projection.setToProjection(java.lang.Math.abs(near), java.lang.Math.abs(far), this.fieldOfView, aspect)
    view.setToLookAt(position, this.tmp.set(position).add(direction), up)
    combined.set(projection)
    com.badlogic.gdx.math.Matrix4.mul(this.combined.`val`, this.view.`val`)
    if (updateFrustum) {
      invProjectionView.set(combined)
      com.badlogic.gdx.math.Matrix4.inv(this.invProjectionView.`val`)
      frustum.update(invProjectionView)
    } else ()
  }
}