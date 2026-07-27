package com.badlogic.gdx.graphics.g3d.utils

class CameraInputController(gestureListener$p: com.badlogic.gdx.graphics.g3d.utils.CameraInputController.CameraGestureListener, camera$p: com.badlogic.gdx.graphics.Camera) extends com.badlogic.gdx.input.GestureDetector(gestureListener$p) {
  var rotateButton: scala.Int = com.badlogic.gdx.Input.Buttons.LEFT
  var rotateAngle: scala.Float = 360.0f
  var translateButton: scala.Int = com.badlogic.gdx.Input.Buttons.RIGHT
  var translateUnits: scala.Float = 10.0f
  var forwardButton: scala.Int = com.badlogic.gdx.Input.Buttons.MIDDLE
  var activateKey: scala.Int = 0
  var activatePressed: scala.Boolean = false
  var alwaysScroll: scala.Boolean = true
  var scrollFactor: scala.Float = -0.1f
  var pinchZoomFactor: scala.Float = 10.0f
  var autoUpdate: scala.Boolean = true
  var target: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  var translateTarget: scala.Boolean = true
  var forwardTarget: scala.Boolean = true
  var scrollTarget: scala.Boolean = false
  var forwardKey: scala.Int = com.badlogic.gdx.Input.Keys.W
  var forwardPressed: scala.Boolean = false
  var backwardKey: scala.Int = com.badlogic.gdx.Input.Keys.S
  var backwardPressed: scala.Boolean = false
  var rotateRightKey: scala.Int = com.badlogic.gdx.Input.Keys.A
  var rotateRightPressed: scala.Boolean = false
  var rotateLeftKey: scala.Int = com.badlogic.gdx.Input.Keys.D
  var rotateLeftPressed: scala.Boolean = false
  var controlsInverted: scala.Boolean = false
  var camera: com.badlogic.gdx.graphics.Camera = null.asInstanceOf[com.badlogic.gdx.graphics.Camera]
  var button: scala.Int = -1
  private var startX: scala.Float = 0.0f
  private var startY: scala.Float = 0.0f
  private final val tmpV1: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  private final val tmpV2: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  var gestureListener: com.badlogic.gdx.graphics.g3d.utils.CameraInputController.CameraGestureListener = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.utils.CameraInputController.CameraGestureListener]
  private var touched: scala.Int = 0
  private var multiTouch: scala.Boolean = false
  def this(camera: com.badlogic.gdx.graphics.Camera) = {
    this(new com.badlogic.gdx.graphics.g3d.utils.CameraInputController.CameraGestureListener(), camera)
  }
  this.gestureListener = gestureListener$p
  this.gestureListener.controller = this
  this.camera = camera$p
  def update(): scala.Unit = {
    if (((this.rotateRightPressed || this.rotateLeftPressed) || this.forwardPressed) || this.backwardPressed) {
      val delta: scala.Float = com.badlogic.gdx.Gdx.graphics.getDeltaTime()
      if (this.rotateRightPressed) {
        this.camera.rotate(this.camera.up, (-delta) * this.rotateAngle)
      } else ()
      if (this.rotateLeftPressed) {
        this.camera.rotate(this.camera.up, delta * this.rotateAngle)
      } else ()
      if (this.forwardPressed) {
        this.camera.translate(this.tmpV1.set(this.camera.direction).scl(delta * this.translateUnits))
        if (this.forwardTarget) {
          this.target.add(this.tmpV1)
        } else ()
      } else ()
      if (this.backwardPressed) {
        this.camera.translate(this.tmpV1.set(this.camera.direction).scl((-delta) * this.translateUnits))
        if (this.forwardTarget) {
          this.target.add(this.tmpV1)
        } else ()
      } else ()
      if (this.autoUpdate) {
        this.camera.update()
      } else ()
    } else ()
  }
  def touchDown(screenX: scala.Int, screenY: scala.Int, pointer: scala.Int, button: scala.Int): scala.Boolean = {
    this.touched = this.touched | (1 << pointer)
    this.multiTouch = !com.badlogic.gdx.math.MathUtils.isPowerOfTwo(this.touched)
    if (this.multiTouch) {
      this.button = -1
    } else {
      if ((this.button < 0) && ((this.activateKey == 0) || this.activatePressed)) {
        this.startX = screenX
        this.startY = screenY
        this.button = button
      } else ()
    }
    return super.touchDown(screenX, screenY, pointer, button) || ((this.activateKey == 0) || this.activatePressed)
  }
  def touchUp(screenX: scala.Int, screenY: scala.Int, pointer: scala.Int, button: scala.Int): scala.Boolean = {
    this.touched = this.touched & ((-1) ^ (1 << pointer))
    this.multiTouch = !com.badlogic.gdx.math.MathUtils.isPowerOfTwo(this.touched)
    if (button == this.button) {
      this.button = -1
    } else ()
    return super.touchUp(screenX, screenY, pointer, button) || this.activatePressed
  }
  def setInvertedControls(invertControls: scala.Boolean): scala.Unit = {
    if (this.controlsInverted != invertControls) {
      this.rotateAngle = -this.rotateAngle
    } else ()
    this.controlsInverted = invertControls
  }
  def process(deltaX: scala.Float, deltaY: scala.Float, button: scala.Int): scala.Boolean = {
    if (button == this.rotateButton) {
      this.tmpV1.set(this.camera.direction).crs(this.camera.up).y = 0.0f
      this.camera.rotateAround(this.target, this.tmpV1.nor(), deltaY * this.rotateAngle)
      this.camera.rotateAround(this.target, com.badlogic.gdx.math.Vector3.Y, deltaX * (-this.rotateAngle))
    } else {
      if (button == this.translateButton) {
        this.camera.translate(this.tmpV1.set(this.camera.direction).crs(this.camera.up).nor().scl((-deltaX) * this.translateUnits))
        this.camera.translate(this.tmpV2.set(this.camera.up).scl((-deltaY) * this.translateUnits))
        if (this.translateTarget) {
          this.target.add(this.tmpV1).add(this.tmpV2)
        } else ()
      } else {
        if (button == this.forwardButton) {
          this.camera.translate(this.tmpV1.set(this.camera.direction).scl(deltaY * this.translateUnits))
          if (this.forwardTarget) {
            this.target.add(this.tmpV1)
          } else ()
        } else ()
      }
    }
    if (this.autoUpdate) {
      this.camera.update()
    } else ()
    return true
  }
  def touchDragged(screenX: scala.Int, screenY: scala.Int, pointer: scala.Int): scala.Boolean = {
    val result: scala.Boolean = super.touchDragged(screenX, screenY, pointer)
    if (result || (this.button < 0)) {
      return result
    } else ()
    val deltaX: scala.Float = (screenX - this.startX) / com.badlogic.gdx.Gdx.graphics.getWidth()
    val deltaY: scala.Float = (this.startY - screenY) / com.badlogic.gdx.Gdx.graphics.getHeight()
    this.startX = screenX
    this.startY = screenY
    return this.process(deltaX, deltaY, this.button)
  }
  def scrolled(amountX: scala.Float, amountY: scala.Float): scala.Boolean = {
    return this.zoom((amountY * this.scrollFactor) * this.translateUnits)
  }
  def zoom(amount: scala.Float): scala.Boolean = {
    if (((!this.alwaysScroll) && (this.activateKey != 0)) && (!this.activatePressed)) {
      return false
    } else ()
    this.camera.translate(this.tmpV1.set(this.camera.direction).scl(amount))
    if (this.scrollTarget) {
      this.target.add(this.tmpV1)
    } else ()
    if (this.autoUpdate) {
      this.camera.update()
    } else ()
    return true
  }
  def pinchZoom(amount: scala.Float): scala.Boolean = {
    return this.zoom(this.pinchZoomFactor * amount)
  }
  def keyDown(keycode: scala.Int): scala.Boolean = {
    if (keycode == this.activateKey) {
      this.activatePressed = true
    } else ()
    if (keycode == this.forwardKey) {
      this.forwardPressed = true
    } else {
      if (keycode == this.backwardKey) {
        this.backwardPressed = true
      } else {
        if (keycode == this.rotateRightKey) {
          this.rotateRightPressed = true
        } else {
          if (keycode == this.rotateLeftKey) {
            this.rotateLeftPressed = true
          } else ()
        }
      }
    }
    return false
  }
  def keyUp(keycode: scala.Int): scala.Boolean = {
    if (keycode == this.activateKey) {
      this.activatePressed = false
      this.button = -1
    } else ()
    if (keycode == this.forwardKey) {
      this.forwardPressed = false
    } else {
      if (keycode == this.backwardKey) {
        this.backwardPressed = false
      } else {
        if (keycode == this.rotateRightKey) {
          this.rotateRightPressed = false
        } else {
          if (keycode == this.rotateLeftKey) {
            this.rotateLeftPressed = false
          } else ()
        }
      }
    }
    return false
  }
}
object CameraInputController {
  export com.badlogic.gdx.input.GestureDetector.{CameraGestureListener => _, *}
  class CameraGestureListener extends com.badlogic.gdx.input.GestureDetector.GestureAdapter {
    var controller: CameraInputController = null.asInstanceOf[CameraInputController]
    private var previousZoom: scala.Float = 0.0f
    def touchDown(x: scala.Float, y: scala.Float, pointer: scala.Int, button: scala.Int): scala.Boolean = {
      this.previousZoom = 0
      return false
    }
    def tap(x: scala.Float, y: scala.Float, count: scala.Int, button: scala.Int): scala.Boolean = {
      return false
    }
    def longPress(x: scala.Float, y: scala.Float): scala.Boolean = {
      return false
    }
    def fling(velocityX: scala.Float, velocityY: scala.Float, button: scala.Int): scala.Boolean = {
      return false
    }
    def pan(x: scala.Float, y: scala.Float, deltaX: scala.Float, deltaY: scala.Float): scala.Boolean = {
      return false
    }
    def zoom(initialDistance: scala.Float, distance: scala.Float): scala.Boolean = {
      val newZoom: scala.Float = distance - initialDistance
      val amount: scala.Float = newZoom - this.previousZoom
      this.previousZoom = newZoom
      val w: scala.Float = com.badlogic.gdx.Gdx.graphics.getWidth()
      val h: scala.Float = com.badlogic.gdx.Gdx.graphics.getHeight()
      return this.controller.pinchZoom(amount / (if (w > h) h else w))
    }
    def pinch(initialPointer1: com.badlogic.gdx.math.Vector2, initialPointer2: com.badlogic.gdx.math.Vector2, pointer1: com.badlogic.gdx.math.Vector2, pointer2: com.badlogic.gdx.math.Vector2): scala.Boolean = {
      return false
    }
  }
}