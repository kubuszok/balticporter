package com.badlogic.gdx.graphics.g3d.utils

class FirstPersonCameraController(camera$p: com.badlogic.gdx.graphics.Camera) extends com.badlogic.gdx.InputAdapter {
  var camera: com.badlogic.gdx.graphics.Camera = null.asInstanceOf[com.badlogic.gdx.graphics.Camera]
  final val keys: com.badlogic.gdx.utils.IntIntMap = new com.badlogic.gdx.utils.IntIntMap()
  var strafeLeftKey: scala.Int = com.badlogic.gdx.Input.Keys.A
  var strafeRightKey: scala.Int = com.badlogic.gdx.Input.Keys.D
  var forwardKey: scala.Int = com.badlogic.gdx.Input.Keys.W
  var backwardKey: scala.Int = com.badlogic.gdx.Input.Keys.S
  var upKey: scala.Int = com.badlogic.gdx.Input.Keys.Q
  var downKey: scala.Int = com.badlogic.gdx.Input.Keys.E
  var autoUpdate: scala.Boolean = true
  var velocity: scala.Float = 5
  var degreesPerPixel: scala.Float = 0.5f
  final val tmp: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  this.camera = camera$p
  @java.lang.Override
  override def keyDown(keycode: scala.Int): scala.Boolean = {
    this.keys.put(keycode, keycode)
    return true
  }
  @java.lang.Override
  override def keyUp(keycode: scala.Int): scala.Boolean = {
    this.keys.remove(keycode, 0)
    return true
  }
  def setVelocity(velocity: scala.Float): scala.Unit = {
    this.velocity = velocity
  }
  def setDegreesPerPixel(degreesPerPixel: scala.Float): scala.Unit = {
    this.degreesPerPixel = degreesPerPixel
  }
  @java.lang.Override
  override def touchDragged(screenX: scala.Int, screenY: scala.Int, pointer: scala.Int): scala.Boolean = {
    val deltaX: scala.Float = (-com.badlogic.gdx.Gdx.input.getDeltaX()) * this.degreesPerPixel
    val deltaY: scala.Float = (-com.badlogic.gdx.Gdx.input.getDeltaY()) * this.degreesPerPixel
    this.camera.direction.rotate(this.camera.up, deltaX)
    this.tmp.set(this.camera.direction).crs(this.camera.up).nor()
    this.camera.direction.rotate(this.tmp, deltaY)
    return true
  }
  def update(): scala.Unit = {
    this.update(com.badlogic.gdx.Gdx.graphics.getDeltaTime())
  }
  def update(deltaTime: scala.Float): scala.Unit = {
    if (this.keys.containsKey(this.forwardKey)) {
      this.tmp.set(this.camera.direction).nor().scl(deltaTime * this.velocity)
      this.camera.position.add(this.tmp)
    } else ()
    if (this.keys.containsKey(this.backwardKey)) {
      this.tmp.set(this.camera.direction).nor().scl((-deltaTime) * this.velocity)
      this.camera.position.add(this.tmp)
    } else ()
    if (this.keys.containsKey(this.strafeLeftKey)) {
      this.tmp.set(this.camera.direction).crs(this.camera.up).nor().scl((-deltaTime) * this.velocity)
      this.camera.position.add(this.tmp)
    } else ()
    if (this.keys.containsKey(this.strafeRightKey)) {
      this.tmp.set(this.camera.direction).crs(this.camera.up).nor().scl(deltaTime * this.velocity)
      this.camera.position.add(this.tmp)
    } else ()
    if (this.keys.containsKey(this.upKey)) {
      this.tmp.set(this.camera.up).nor().scl(deltaTime * this.velocity)
      this.camera.position.add(this.tmp)
    } else ()
    if (this.keys.containsKey(this.downKey)) {
      this.tmp.set(this.camera.up).nor().scl((-deltaTime) * this.velocity)
      this.camera.position.add(this.tmp)
    } else ()
    if (this.autoUpdate) {
      this.camera.update(true)
    } else ()
  }
}