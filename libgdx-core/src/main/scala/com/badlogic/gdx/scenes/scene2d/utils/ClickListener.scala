package com.badlogic.gdx.scenes.scene2d.utils

class ClickListener extends com.badlogic.gdx.scenes.scene2d.InputListener {
  private var tapSquareSize: scala.Float = 14
  private var touchDownX: scala.Float = -1
  private var touchDownY: scala.Float = -1
  private var pressedPointer: scala.Int = -1
  private var pressedButton: scala.Int = -1
  private var button: scala.Int = 0
  private var pressed: scala.Boolean = false
  private var over: scala.Boolean = false
  private var cancelled: scala.Boolean = false
  private var visualPressedTime: scala.Long = 0L
  private var tapCountInterval: scala.Long = (0.4f * 1000000000L).asInstanceOf[scala.Long].asInstanceOf[scala.Long]
  private var tapCount: scala.Int = 0
  private var lastTapTime: scala.Long = 0L
  def this(button: scala.Int) = {
    this()
    this.button = button
  }
  def touchDown(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, button: scala.Int): scala.Boolean = {
    if (this.pressed) {
      return false
    } else ()
    if (((pointer == 0) && (this.button != (-1))) && (button != this.button)) {
      return false
    } else ()
    this.pressed = true
    this.pressedPointer = pointer
    this.pressedButton = button
    this.touchDownX = x
    this.touchDownY = y
    this.setVisualPressed(true)
    return true
  }
  def touchDragged(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int): scala.Unit = {
    if ((pointer != this.pressedPointer) || this.cancelled) {
      return
    } else ()
    this.pressed = this.isOver(event.getListenerActor(), x, y)
    if (!this.pressed) {
      this.invalidateTapSquare()
    } else ()
  }
  def touchUp(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, button: scala.Int): scala.Unit = {
    if (pointer == this.pressedPointer) {
      if (!this.cancelled) {
        var touchUpOver: scala.Boolean = this.isOver(event.getListenerActor(), x, y)
        if (((touchUpOver && (pointer == 0)) && (this.button != (-1))) && (button != this.button)) {
          touchUpOver = false
        } else ()
        if (touchUpOver) {
          val time: scala.Long = com.badlogic.gdx.utils.TimeUtils.nanoTime()
          if ((time - this.lastTapTime) > this.tapCountInterval) {
            this.tapCount = 0
          } else ()
          this.tapCount = this.tapCount + 1
          this.lastTapTime = time
          this.clicked(event, x, y)
        } else ()
      } else ()
      this.pressed = false
      this.pressedPointer = -1
      this.pressedButton = -1
      this.cancelled = false
    } else ()
  }
  def enter(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, fromActor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    if ((pointer == (-1)) && (!this.cancelled)) {
      this.over = true
    } else ()
  }
  def exit(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, toActor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    if ((pointer == (-1)) && (!this.cancelled)) {
      this.over = false
    } else ()
  }
  def cancel(): scala.Unit = {
    if (this.pressedPointer == (-1)) {
      return
    } else ()
    this.cancelled = true
    this.pressed = false
  }
  def clicked(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float): scala.Unit = {
    ()
  }
  def isOver(actor: com.badlogic.gdx.scenes.scene2d.Actor, x: scala.Float, y: scala.Float): scala.Boolean = {
    val hit: com.badlogic.gdx.scenes.scene2d.Actor = actor.hit(x, y, true)
    if ((hit == null) || (!hit.isDescendantOf(actor))) {
      return this.inTapSquare(x, y)
    } else ()
    return true
  }
  def inTapSquare(x: scala.Float, y: scala.Float): scala.Boolean = {
    if ((this.touchDownX == (-1)) && (this.touchDownY == (-1))) {
      return false
    } else ()
    return (java.lang.Math.abs(x - this.touchDownX) < this.tapSquareSize) && (java.lang.Math.abs(y - this.touchDownY) < this.tapSquareSize)
  }
  def inTapSquare(): scala.Boolean = {
    return this.touchDownX != (-1)
  }
  def invalidateTapSquare(): scala.Unit = {
    this.touchDownX = -1
    this.touchDownY = -1
  }
  def isPressed(): scala.Boolean = {
    return this.pressed
  }
  def isVisualPressed(): scala.Boolean = {
    if (this.pressed) {
      return true
    } else ()
    if (this.visualPressedTime <= 0) {
      return false
    } else ()
    if (this.visualPressedTime > com.badlogic.gdx.utils.TimeUtils.millis()) {
      return true
    } else ()
    this.visualPressedTime = 0
    return false
  }
  def setVisualPressed(visualPressed: scala.Boolean): scala.Unit = {
    if (visualPressed) {
      this.visualPressedTime = com.badlogic.gdx.utils.TimeUtils.millis() + (ClickListener.visualPressedDuration * 1000).asInstanceOf[scala.Long]
    } else {
      this.visualPressedTime = 0
    }
  }
  def isOver(): scala.Boolean = {
    return this.over || this.pressed
  }
  def setTapSquareSize(halfTapSquareSize: scala.Float): scala.Unit = {
    this.tapSquareSize = halfTapSquareSize
  }
  def getTapSquareSize(): scala.Float = {
    return this.tapSquareSize
  }
  def setTapCountInterval(tapCountInterval: scala.Float): scala.Unit = {
    this.tapCountInterval = (tapCountInterval * 1000000000L).asInstanceOf[scala.Long].asInstanceOf[scala.Long]
  }
  def getTapCount(): scala.Int = {
    return this.tapCount
  }
  def setTapCount(tapCount: scala.Int): scala.Unit = {
    this.tapCount = tapCount
  }
  def getTouchDownX(): scala.Float = {
    return this.touchDownX
  }
  def getTouchDownY(): scala.Float = {
    return this.touchDownY
  }
  def getPressedButton(): scala.Int = {
    return this.pressedButton
  }
  def getPressedPointer(): scala.Int = {
    return this.pressedPointer
  }
  def getButton(): scala.Int = {
    return this.button
  }
  def setButton(button: scala.Int): scala.Unit = {
    this.button = button
  }
}
object ClickListener {
  export com.badlogic.gdx.scenes.scene2d.InputListener.{visualPressedDuration => _, *}
  var visualPressedDuration: scala.Float = 0.1f
}