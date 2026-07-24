package com.badlogic.gdx.scenes.scene2d.utils

class DragScrollListener extends com.badlogic.gdx.scenes.scene2d.utils.DragListener {
  var scroll$field: com.badlogic.gdx.scenes.scene2d.ui.ScrollPane = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.ScrollPane]
  private var scrollUp: com.badlogic.gdx.utils.Timer#Task = null.asInstanceOf[com.badlogic.gdx.utils.Timer#Task]
  private var scrollDown: com.badlogic.gdx.utils.Timer#Task = null.asInstanceOf[com.badlogic.gdx.utils.Timer#Task]
  var interpolation: com.badlogic.gdx.math.Interpolation = com.badlogic.gdx.math.Interpolation.exp5In
  var minSpeed: scala.Float = 15
  var maxSpeed: scala.Float = 75
  var tickSecs: scala.Float = 0.05f
  var startTime: scala.Long = 0L
  var rampTime: scala.Long = 1750
  var padTop: scala.Float = 0.0f
  var padBottom: scala.Float = 0.0f
  def this(scroll: com.badlogic.gdx.scenes.scene2d.ui.ScrollPane) = {
    this()
    this.scroll$field = scroll
    this.scrollUp = new com.badlogic.gdx.utils.Timer#Task()
    this.scrollDown = new com.badlogic.gdx.utils.Timer#Task()
  }
  def setup(minSpeedPixels: scala.Float, maxSpeedPixels: scala.Float, tickSecs: scala.Float, rampSecs: scala.Float): scala.Unit = {
    this.minSpeed = minSpeedPixels
    this.maxSpeed = maxSpeedPixels
    this.tickSecs = tickSecs
    this.rampTime = (rampSecs * 1000).asInstanceOf[scala.Long]
  }
  def getScrollPixels(): scala.Float = {
    return this.interpolation.apply(this.minSpeed, this.maxSpeed, java.lang.Math.min(1, (java.lang.System.currentTimeMillis() - this.startTime) / this.rampTime.asInstanceOf[scala.Float]))
  }
  def drag(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int): scala.Unit = {
    event.getListenerActor().localToActorCoordinates(this.scroll$field, DragScrollListener.tmpCoords.set(x, y))
    if (this.isAbove(DragScrollListener.tmpCoords.y)) {
      this.scrollDown.cancel()
      if (!this.scrollUp.isScheduled()) {
        this.startTime = java.lang.System.currentTimeMillis()
        com.badlogic.gdx.utils.Timer.schedule(this.scrollUp, this.tickSecs, this.tickSecs)
      } else ()
      return
    } else {
      if (this.isBelow(DragScrollListener.tmpCoords.y)) {
        this.scrollUp.cancel()
        if (!this.scrollDown.isScheduled()) {
          this.startTime = java.lang.System.currentTimeMillis()
          com.badlogic.gdx.utils.Timer.schedule(this.scrollDown, this.tickSecs, this.tickSecs)
        } else ()
        return
      } else ()
    }
    this.scrollUp.cancel()
    this.scrollDown.cancel()
  }
  def dragStop(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int): scala.Unit = {
    this.scrollUp.cancel()
    this.scrollDown.cancel()
  }
  protected def isAbove(y: scala.Float): scala.Boolean = {
    return y >= (this.scroll$field.getHeight() - this.padTop)
  }
  protected def isBelow(y: scala.Float): scala.Boolean = {
    return y < this.padBottom
  }
  protected def scroll(y: scala.Float): scala.Unit = {
    this.scroll$field.setScrollY(y)
  }
  def setPadding(padTop: scala.Float, padBottom: scala.Float): scala.Unit = {
    this.padTop = padTop
    this.padBottom = padBottom
  }
}
object DragScrollListener {
  final val tmpCoords: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
}