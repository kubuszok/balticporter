package com.badlogic.gdx.input

class GestureDetector(halfTapRectangleWidth: scala.Float, halfTapRectangleHeight: scala.Float, tapCountInterval$p: scala.Float, longPressDuration: scala.Float, maxFlingDelay$p: scala.Float, listener$p: com.badlogic.gdx.input.GestureDetector.GestureListener) extends com.badlogic.gdx.InputAdapter {
  var listener: com.badlogic.gdx.input.GestureDetector.GestureListener = null.asInstanceOf[com.badlogic.gdx.input.GestureDetector.GestureListener]
  private var tapRectangleWidth: scala.Float = 0.0f
  private var tapRectangleHeight: scala.Float = 0.0f
  private var tapCountInterval: scala.Long = 0L
  private var longPressSeconds: scala.Float = 0.0f
  private var maxFlingDelay: scala.Long = 0L
  private var inTapRectangle: scala.Boolean = false
  private var tapCount: scala.Int = 0
  private var lastTapTime: scala.Long = 0L
  private var lastTapX: scala.Float = 0.0f
  private var lastTapY: scala.Float = 0.0f
  private var lastTapButton: scala.Int = 0
  private var lastTapPointer: scala.Int = 0
  var longPressFired: scala.Boolean = false
  private var pinching: scala.Boolean = false
  private var panning: scala.Boolean = false
  private final val tracker: com.badlogic.gdx.input.GestureDetector.VelocityTracker = new com.badlogic.gdx.input.GestureDetector.VelocityTracker()
  private var tapRectangleCenterX: scala.Float = 0.0f
  private var tapRectangleCenterY: scala.Float = 0.0f
  private var touchDownTime: scala.Long = 0L
  var pointer1: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  private final val pointer2: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  private final val initialPointer1: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  private final val initialPointer2: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  private final val longPressTask: com.badlogic.gdx.utils.Timer.Task = new com.badlogic.gdx.utils.Timer.Task() {
    override def run(): scala.Unit = {
      if (!GestureDetector.this.longPressFired) {
        GestureDetector.this.longPressFired = GestureDetector.this.listener.longPress(GestureDetector.this.pointer1.x, GestureDetector.this.pointer1.y)
      } else ()
    }
  }
  def this(halfTapSquareSize: scala.Float, tapCountInterval: scala.Float, longPressDuration: scala.Float, maxFlingDelay: scala.Float, listener: com.badlogic.gdx.input.GestureDetector.GestureListener) = {
    this(halfTapSquareSize, halfTapSquareSize, tapCountInterval, longPressDuration, maxFlingDelay, listener)
  }
  def this(listener: com.badlogic.gdx.input.GestureDetector.GestureListener) = {
    this(20, 0.4f, 1.1f, java.lang.Integer.MAX_VALUE, listener)
  }
  if (listener$p == null) {
    throw new java.lang.IllegalArgumentException("listener cannot be null.")
  } else ()
  this.tapRectangleWidth = halfTapRectangleWidth
  this.tapRectangleHeight = halfTapRectangleHeight
  this.tapCountInterval = (tapCountInterval$p * 1000000000L).asInstanceOf[scala.Long].asInstanceOf[scala.Long]
  this.longPressSeconds = longPressDuration
  this.maxFlingDelay = (maxFlingDelay$p * 1000000000L).asInstanceOf[scala.Long].asInstanceOf[scala.Long]
  this.listener = listener$p
  def touchDown(x: scala.Int, y: scala.Int, pointer: scala.Int, button: scala.Int): scala.Boolean = {
    return this.touchDown(x.asInstanceOf[scala.Float], y.asInstanceOf[scala.Float], pointer, button)
  }
  def touchDown(x: scala.Float, y: scala.Float, pointer: scala.Int, button: scala.Int): scala.Boolean = {
    if (pointer > 1) {
      return false
    } else ()
    if (pointer == 0) {
      this.pointer1.set(x, y)
      this.touchDownTime = com.badlogic.gdx.Gdx.input.getCurrentEventTime()
      this.tracker.start(x, y, this.touchDownTime)
      if (com.badlogic.gdx.Gdx.input.isTouched(1)) {
        this.inTapRectangle = false
        this.pinching = true
        this.initialPointer1.set(this.pointer1)
        this.initialPointer2.set(this.pointer2)
        this.longPressTask.cancel()
      } else {
        this.inTapRectangle = true
        this.pinching = false
        this.longPressFired = false
        this.tapRectangleCenterX = x
        this.tapRectangleCenterY = y
        if (!this.longPressTask.isScheduled()) {
          com.badlogic.gdx.utils.Timer.schedule(this.longPressTask, this.longPressSeconds)
        } else ()
      }
    } else {
      this.pointer2.set(x, y)
      this.inTapRectangle = false
      this.pinching = true
      this.initialPointer1.set(this.pointer1)
      this.initialPointer2.set(this.pointer2)
      this.longPressTask.cancel()
    }
    return this.listener.touchDown(x, y, pointer, button)
  }
  def touchDragged(x: scala.Int, y: scala.Int, pointer: scala.Int): scala.Boolean = {
    return this.touchDragged(x.asInstanceOf[scala.Float], y.asInstanceOf[scala.Float], pointer)
  }
  def touchDragged(x: scala.Float, y: scala.Float, pointer: scala.Int): scala.Boolean = {
    if (pointer > 1) {
      return false
    } else ()
    if (this.longPressFired) {
      return false
    } else ()
    if (pointer == 0) {
      this.pointer1.set(x, y)
    } else {
      this.pointer2.set(x, y)
    }
    if (this.pinching) {
      val result: scala.Boolean = this.listener.pinch(this.initialPointer1, this.initialPointer2, this.pointer1, this.pointer2)
      return this.listener.zoom(this.initialPointer1.dst(this.initialPointer2), this.pointer1.dst(this.pointer2)) || result
    } else ()
    this.tracker.update(x, y, com.badlogic.gdx.Gdx.input.getCurrentEventTime())
    if (this.inTapRectangle && (!this.isWithinTapRectangle(x, y, this.tapRectangleCenterX, this.tapRectangleCenterY))) {
      this.longPressTask.cancel()
      this.inTapRectangle = false
    } else ()
    if (!this.inTapRectangle) {
      this.panning = true
      return this.listener.pan(x, y, this.tracker.deltaX, this.tracker.deltaY)
    } else ()
    return false
  }
  def touchUp(x: scala.Int, y: scala.Int, pointer: scala.Int, button: scala.Int): scala.Boolean = {
    return this.touchUp(x.asInstanceOf[scala.Float], y.asInstanceOf[scala.Float], pointer, button)
  }
  def touchUp(x: scala.Float, y: scala.Float, pointer: scala.Int, button: scala.Int): scala.Boolean = {
    if (pointer > 1) {
      return false
    } else ()
    if (this.inTapRectangle && (!this.isWithinTapRectangle(x, y, this.tapRectangleCenterX, this.tapRectangleCenterY))) {
      this.inTapRectangle = false
    } else ()
    val wasPanning: scala.Boolean = this.panning
    this.panning = false
    this.longPressTask.cancel()
    if (this.longPressFired) {
      return false
    } else ()
    if (this.inTapRectangle) {
      if ((((this.lastTapButton != button) || (this.lastTapPointer != pointer)) || ((com.badlogic.gdx.utils.TimeUtils.nanoTime() - this.lastTapTime) > this.tapCountInterval)) || (!this.isWithinTapRectangle(x, y, this.lastTapX, this.lastTapY))) {
        this.tapCount = 0
      } else ()
      this.tapCount = this.tapCount + 1
      this.lastTapTime = com.badlogic.gdx.utils.TimeUtils.nanoTime()
      this.lastTapX = x
      this.lastTapY = y
      this.lastTapButton = button
      this.lastTapPointer = pointer
      this.touchDownTime = 0
      return this.listener.tap(x, y, this.tapCount, button)
    } else ()
    if (this.pinching) {
      this.pinching = false
      this.listener.pinchStop()
      this.panning = true
      if (pointer == 0) {
        this.tracker.start(this.pointer2.x, this.pointer2.y, com.badlogic.gdx.Gdx.input.getCurrentEventTime())
      } else {
        this.tracker.start(this.pointer1.x, this.pointer1.y, com.badlogic.gdx.Gdx.input.getCurrentEventTime())
      }
      return false
    } else ()
    var handled: scala.Boolean = false
    if (wasPanning && (!this.panning)) {
      handled = this.listener.panStop(x, y, pointer, button)
    } else ()
    val time: scala.Long = com.badlogic.gdx.Gdx.input.getCurrentEventTime()
    if ((time - this.touchDownTime) <= this.maxFlingDelay) {
      this.tracker.update(x, y, time)
      handled = this.listener.fling(this.tracker.getVelocityX(), this.tracker.getVelocityY(), button) || handled
    } else ()
    this.touchDownTime = 0
    return handled
  }
  def touchCancelled(screenX: scala.Int, screenY: scala.Int, pointer: scala.Int, button: scala.Int): scala.Boolean = {
    this.cancel()
    return super.touchCancelled(screenX, screenY, pointer, button)
  }
  def cancel(): scala.Unit = {
    this.longPressTask.cancel()
    this.longPressFired = true
  }
  def isLongPressed(): scala.Boolean = {
    return this.isLongPressed(this.longPressSeconds)
  }
  def isLongPressed(duration: scala.Float): scala.Boolean = {
    if (this.touchDownTime == 0) {
      return false
    } else ()
    return (com.badlogic.gdx.utils.TimeUtils.nanoTime() - this.touchDownTime) > (duration * 1000000000L).asInstanceOf[scala.Long]
  }
  def isPanning(): scala.Boolean = {
    return this.panning
  }
  def reset(): scala.Unit = {
    this.longPressTask.cancel()
    this.touchDownTime = 0
    this.panning = false
    this.inTapRectangle = false
    this.tracker.lastTime = 0
  }
  private def isWithinTapRectangle(x: scala.Float, y: scala.Float, centerX: scala.Float, centerY: scala.Float): scala.Boolean = {
    return (java.lang.Math.abs(x - centerX) < this.tapRectangleWidth) && (java.lang.Math.abs(y - centerY) < this.tapRectangleHeight)
  }
  def invalidateTapSquare(): scala.Unit = {
    this.inTapRectangle = false
  }
  def setTapSquareSize(halfTapSquareSize: scala.Float): scala.Unit = {
    this.setTapRectangleSize(halfTapSquareSize, halfTapSquareSize)
  }
  def setTapRectangleSize(halfTapRectangleWidth: scala.Float, halfTapRectangleHeight: scala.Float): scala.Unit = {
    this.tapRectangleWidth = halfTapRectangleWidth
    this.tapRectangleHeight = halfTapRectangleHeight
  }
  def setTapCountInterval(tapCountInterval: scala.Float): scala.Unit = {
    this.tapCountInterval = (tapCountInterval * 1000000000L).asInstanceOf[scala.Long].asInstanceOf[scala.Long]
  }
  def setLongPressSeconds(longPressSeconds: scala.Float): scala.Unit = {
    this.longPressSeconds = longPressSeconds
  }
  def setMaxFlingDelay(maxFlingDelay: scala.Long): scala.Unit = {
    this.maxFlingDelay = maxFlingDelay
  }
}
object GestureDetector {
  trait GestureListener {
    def touchDown(x: scala.Float, y: scala.Float, pointer: scala.Int, button: scala.Int): scala.Boolean
    def tap(x: scala.Float, y: scala.Float, count: scala.Int, button: scala.Int): scala.Boolean
    def longPress(x: scala.Float, y: scala.Float): scala.Boolean
    def fling(velocityX: scala.Float, velocityY: scala.Float, button: scala.Int): scala.Boolean
    def pan(x: scala.Float, y: scala.Float, deltaX: scala.Float, deltaY: scala.Float): scala.Boolean
    def panStop(x: scala.Float, y: scala.Float, pointer: scala.Int, button: scala.Int): scala.Boolean
    def zoom(initialDistance: scala.Float, distance: scala.Float): scala.Boolean
    def pinch(initialPointer1: com.badlogic.gdx.math.Vector2, initialPointer2: com.badlogic.gdx.math.Vector2, pointer1: com.badlogic.gdx.math.Vector2, pointer2: com.badlogic.gdx.math.Vector2): scala.Boolean
    def pinchStop(): scala.Unit
  }
  class GestureAdapter extends com.badlogic.gdx.input.GestureDetector.GestureListener {
    def touchDown(x: scala.Float, y: scala.Float, pointer: scala.Int, button: scala.Int): scala.Boolean = {
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
    def panStop(x: scala.Float, y: scala.Float, pointer: scala.Int, button: scala.Int): scala.Boolean = {
      return false
    }
    def zoom(initialDistance: scala.Float, distance: scala.Float): scala.Boolean = {
      return false
    }
    def pinch(initialPointer1: com.badlogic.gdx.math.Vector2, initialPointer2: com.badlogic.gdx.math.Vector2, pointer1: com.badlogic.gdx.math.Vector2, pointer2: com.badlogic.gdx.math.Vector2): scala.Boolean = {
      return false
    }
    def pinchStop(): scala.Unit = {
      ()
    }
  }
  class VelocityTracker {
    var sampleSize: scala.Int = 10
    var lastX: scala.Float = 0.0f
    var lastY: scala.Float = 0.0f
    var deltaX: scala.Float = 0.0f
    var deltaY: scala.Float = 0.0f
    var lastTime: scala.Long = 0L
    var numSamples: scala.Int = 0
    var meanX: scala.Array[scala.Float] = new scala.Array[scala.Float](this.sampleSize)
    var meanY: scala.Array[scala.Float] = new scala.Array[scala.Float](this.sampleSize)
    var meanTime: scala.Array[scala.Long] = new scala.Array[scala.Long](this.sampleSize)
    def start(x: scala.Float, y: scala.Float, timeStamp: scala.Long): scala.Unit = {
      this.lastX = x
      this.lastY = y
      this.deltaX = 0
      this.deltaY = 0
      this.numSamples = 0;
      { var i: scala.Int = 0; while (i < this.sampleSize) { {
        this.meanX(i) = 0
        this.meanY(i) = 0
        this.meanTime(i) = 0
      }; i = i + 1 } }
      this.lastTime = timeStamp
    }
    def update(x: scala.Float, y: scala.Float, currTime: scala.Long): scala.Unit = {
      this.deltaX = x - this.lastX
      this.deltaY = y - this.lastY
      this.lastX = x
      this.lastY = y
      val deltaTime: scala.Long = currTime - this.lastTime
      this.lastTime = currTime
      val index: scala.Int = this.numSamples % this.sampleSize
      this.meanX(index) = this.deltaX
      this.meanY(index) = this.deltaY
      this.meanTime(index) = deltaTime
      this.numSamples = this.numSamples + 1
    }
    def getVelocityX(): scala.Float = {
      val meanX: scala.Float = this.getAverage(this.meanX, this.numSamples)
      val meanTime: scala.Float = this.getAverage(this.meanTime, this.numSamples) / 1.0E9f
      if (meanTime == 0) {
        return 0
      } else ()
      return meanX / meanTime
    }
    def getVelocityY(): scala.Float = {
      val meanY: scala.Float = this.getAverage(this.meanY, this.numSamples)
      val meanTime: scala.Float = this.getAverage(this.meanTime, this.numSamples) / 1.0E9f
      if (meanTime == 0) {
        return 0
      } else ()
      return meanY / meanTime
    }
    private def getAverage(values: scala.Array[scala.Float], numSamples$arg: scala.Int): scala.Float = {
      var numSamples: scala.Int = numSamples$arg
      numSamples = java.lang.Math.min(this.sampleSize, numSamples)
      var sum: scala.Float = 0;
      { var i: scala.Int = 0; while (i < numSamples) { {
        sum = sum + values(i)
      }; i = i + 1 } }
      return sum / numSamples
    }
    private def getAverage(values: scala.Array[scala.Long], numSamples$arg: scala.Int): scala.Long = {
      var numSamples: scala.Int = numSamples$arg
      numSamples = java.lang.Math.min(this.sampleSize, numSamples)
      var sum: scala.Long = 0;
      { var i: scala.Int = 0; while (i < numSamples) { {
        sum = sum + values(i)
      }; i = i + 1 } }
      if (numSamples == 0) {
        return 0
      } else ()
      return sum / numSamples
    }
    private def getSum(values: scala.Array[scala.Float], numSamples$arg: scala.Int): scala.Float = {
      var numSamples: scala.Int = numSamples$arg
      numSamples = java.lang.Math.min(this.sampleSize, numSamples)
      var sum: scala.Float = 0;
      { var i: scala.Int = 0; while (i < numSamples) { {
        sum = sum + values(i)
      }; i = i + 1 } }
      if (numSamples == 0) {
        return 0
      } else ()
      return sum
    }
  }
}