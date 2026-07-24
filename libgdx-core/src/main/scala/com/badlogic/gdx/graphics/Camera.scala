package com.badlogic.gdx.graphics

abstract class Camera {
  final val position: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val direction: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3(0, 0, -1)
  final val up: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3(0, 1, 0)
  final val projection: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
  final val view: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
  final val combined: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
  final val invProjectionView: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
  var near: scala.Float = 1
  var far: scala.Float = 100
  var viewportWidth: scala.Float = 0
  var viewportHeight: scala.Float = 0
  final val frustum: com.badlogic.gdx.math.Frustum = new com.badlogic.gdx.math.Frustum()
  private final val tmpVec: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  private final val ray: com.badlogic.gdx.math.collision.Ray = new com.badlogic.gdx.math.collision.Ray(new com.badlogic.gdx.math.Vector3(), new com.badlogic.gdx.math.Vector3())
  def update(): scala.Unit
  def update(updateFrustum: scala.Boolean): scala.Unit
  def lookAt(x: scala.Float, y: scala.Float, z: scala.Float): scala.Unit = {
    this.tmpVec.set(x, y, z).sub(this.position).nor()
    if (!this.tmpVec.isZero()) {
      val dot: scala.Float = this.tmpVec.dot(this.up)
      if (java.lang.Math.abs(dot - 1) < 1.0E-9f) {
        this.up.set(this.direction).scl(-1)
      } else {
        if (java.lang.Math.abs(dot + 1) < 1.0E-9f) {
          this.up.set(this.direction)
        } else ()
      }
      this.direction.set(this.tmpVec)
      this.normalizeUp()
    } else ()
  }
  def lookAt(target: com.badlogic.gdx.math.Vector3): scala.Unit = {
    this.lookAt(target.x, target.y, target.z)
  }
  def normalizeUp(): scala.Unit = {
    this.tmpVec.set(this.direction).crs(this.up)
    this.up.set(this.tmpVec).crs(this.direction).nor()
  }
  def rotate(angle: scala.Float, axisX: scala.Float, axisY: scala.Float, axisZ: scala.Float): scala.Unit = {
    this.direction.rotate(angle, axisX, axisY, axisZ)
    this.up.rotate(angle, axisX, axisY, axisZ)
  }
  def rotate(axis: com.badlogic.gdx.math.Vector3, angle: scala.Float): scala.Unit = {
    this.direction.rotate(axis, angle)
    this.up.rotate(axis, angle)
  }
  def rotate(transform: com.badlogic.gdx.math.Matrix4): scala.Unit = {
    this.direction.rot(transform)
    this.up.rot(transform)
  }
  def rotate(quat: com.badlogic.gdx.math.Quaternion): scala.Unit = {
    quat.transform(this.direction)
    quat.transform(this.up)
  }
  def rotateAround(point: com.badlogic.gdx.math.Vector3, axis: com.badlogic.gdx.math.Vector3, angle: scala.Float): scala.Unit = {
    this.tmpVec.set(point)
    this.tmpVec.sub(this.position)
    this.translate(this.tmpVec)
    this.rotate(axis, angle)
    this.tmpVec.rotate(axis, angle)
    this.translate(-this.tmpVec.x, -this.tmpVec.y, -this.tmpVec.z)
  }
  def transform(transform: com.badlogic.gdx.math.Matrix4): scala.Unit = {
    this.position.mul(transform)
    this.rotate(transform)
  }
  def translate(x: scala.Float, y: scala.Float, z: scala.Float): scala.Unit = {
    this.position.add(x, y, z)
  }
  def translate(vec: com.badlogic.gdx.math.Vector3): scala.Unit = {
    this.position.add(vec)
  }
  def unproject(touchCoords: com.badlogic.gdx.math.Vector3, viewportX: scala.Float, viewportY: scala.Float, viewportWidth: scala.Float, viewportHeight: scala.Float): com.badlogic.gdx.math.Vector3 = {
    var x: scala.Float = touchCoords.x - viewportX
    var y: scala.Float = (com.badlogic.gdx.Gdx.graphics.getHeight() - touchCoords.y) - viewportY
    touchCoords.x = ((2 * x) / viewportWidth) - 1
    touchCoords.y = ((2 * y) / viewportHeight) - 1
    touchCoords.z = (2 * touchCoords.z) - 1
    touchCoords.prj(this.invProjectionView)
    return touchCoords
  }
  def unproject(touchCoords: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    this.unproject(touchCoords, 0, 0, com.badlogic.gdx.Gdx.graphics.getWidth(), com.badlogic.gdx.Gdx.graphics.getHeight())
    return touchCoords
  }
  def project(worldCoords: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    this.project(worldCoords, 0, 0, com.badlogic.gdx.Gdx.graphics.getWidth(), com.badlogic.gdx.Gdx.graphics.getHeight())
    return worldCoords
  }
  def project(worldCoords: com.badlogic.gdx.math.Vector3, viewportX: scala.Float, viewportY: scala.Float, viewportWidth: scala.Float, viewportHeight: scala.Float): com.badlogic.gdx.math.Vector3 = {
    worldCoords.prj(this.combined)
    worldCoords.x = ((viewportWidth * (worldCoords.x + 1)) / 2) + viewportX
    worldCoords.y = ((viewportHeight * (worldCoords.y + 1)) / 2) + viewportY
    worldCoords.z = (worldCoords.z + 1) / 2
    return worldCoords
  }
  def getPickRay(touchX: scala.Float, touchY: scala.Float, viewportX: scala.Float, viewportY: scala.Float, viewportWidth: scala.Float, viewportHeight: scala.Float): com.badlogic.gdx.math.collision.Ray = {
    this.unproject(this.ray.origin.set(touchX, touchY, 0), viewportX, viewportY, viewportWidth, viewportHeight)
    this.unproject(this.ray.direction.set(touchX, touchY, 1), viewportX, viewportY, viewportWidth, viewportHeight)
    this.ray.direction.sub(this.ray.origin).nor()
    return this.ray
  }
  def getPickRay(touchX: scala.Float, touchY: scala.Float): com.badlogic.gdx.math.collision.Ray = {
    return this.getPickRay(touchX, touchY, 0, 0, com.badlogic.gdx.Gdx.graphics.getWidth(), com.badlogic.gdx.Gdx.graphics.getHeight())
  }
}