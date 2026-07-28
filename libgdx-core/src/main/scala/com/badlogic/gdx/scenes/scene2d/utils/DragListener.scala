package com.badlogic.gdx.scenes.scene2d.utils

class DragListener extends com.badlogic.gdx.scenes.scene2d.InputListener {
  private var tapSquareSize: scala.Float = 14
  private var touchDownX: scala.Float = -1
  private var touchDownY: scala.Float = -1
  private var stageTouchDownX: scala.Float = -1
  private var stageTouchDownY: scala.Float = -1
  private var dragStartX: scala.Float = 0.0f
  private var dragStartY: scala.Float = 0.0f
  private var dragLastX: scala.Float = 0.0f
  private var dragLastY: scala.Float = 0.0f
  private var dragX: scala.Float = 0.0f
  private var dragY: scala.Float = 0.0f
  private var pressedPointer: scala.Int = -1
  private var button: scala.Int = 0
  private var dragging: scala.Boolean = false
  override def touchDown(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, button: scala.Int): scala.Boolean = {
    if (this.pressedPointer != (-1)) {
      return false
    } else ()
    if (((pointer == 0) && (this.button != (-1))) && (button != this.button)) {
      return false
    } else ()
    this.pressedPointer = pointer
    this.touchDownX = x
    this.touchDownY = y
    this.stageTouchDownX = event.getStageX()
    this.stageTouchDownY = event.getStageY()
    return true
  }
  override def touchDragged(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int): scala.Unit = {
    if (pointer != this.pressedPointer) {
      return
    } else ()
    if ((!this.dragging) && ((java.lang.Math.abs(this.touchDownX - x) > this.tapSquareSize) || (java.lang.Math.abs(this.touchDownY - y) > this.tapSquareSize))) {
      this.dragging = true
      this.dragStartX = x
      this.dragStartY = y
      this.dragStart(event, x, y, pointer)
      this.dragX = x
      this.dragY = y
    } else ()
    if (this.dragging) {
      this.dragLastX = this.dragX
      this.dragLastY = this.dragY
      this.dragX = x
      this.dragY = y
      this.drag(event, x, y, pointer)
    } else ()
  }
  override def touchUp(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, button: scala.Int): scala.Unit = {
    if ((pointer == this.pressedPointer) && ((this.button == (-1)) || (button == this.button))) {
      if (this.dragging) {
        this.dragStop(event, x, y, pointer)
      } else ()
      this.cancel()
    } else ()
  }
  def dragStart(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int): scala.Unit = {
    ()
  }
  def drag(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int): scala.Unit = {
    ()
  }
  def dragStop(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int): scala.Unit = {
    ()
  }
  def cancel(): scala.Unit = {
    this.dragging = false
    this.pressedPointer = -1
  }
  def isDragging(): scala.Boolean = {
    return this.dragging
  }
  def setTapSquareSize(halfTapSquareSize: scala.Float): scala.Unit = {
    this.tapSquareSize = halfTapSquareSize
  }
  def getTapSquareSize(): scala.Float = {
    return this.tapSquareSize
  }
  def getTouchDownX(): scala.Float = {
    return this.touchDownX
  }
  def getTouchDownY(): scala.Float = {
    return this.touchDownY
  }
  def getStageTouchDownX(): scala.Float = {
    return this.stageTouchDownX
  }
  def getStageTouchDownY(): scala.Float = {
    return this.stageTouchDownY
  }
  def getDragStartX(): scala.Float = {
    return this.dragStartX
  }
  def setDragStartX(dragStartX: scala.Float): scala.Unit = {
    this.dragStartX = dragStartX
  }
  def getDragStartY(): scala.Float = {
    return this.dragStartY
  }
  def setDragStartY(dragStartY: scala.Float): scala.Unit = {
    this.dragStartY = dragStartY
  }
  def getDragX(): scala.Float = {
    return this.dragX
  }
  def getDragY(): scala.Float = {
    return this.dragY
  }
  def getDragDistance(): scala.Float = {
    return com.badlogic.gdx.math.Vector2.len(this.dragX - this.dragStartX, this.dragY - this.dragStartY)
  }
  def getDeltaX(): scala.Float = {
    return this.dragX - this.dragLastX
  }
  def getDeltaY(): scala.Float = {
    return this.dragY - this.dragLastY
  }
  def getButton(): scala.Int = {
    return this.button
  }
  def setButton(button: scala.Int): scala.Unit = {
    this.button = button
  }
}
object DragListener {
  export com.badlogic.gdx.scenes.scene2d.InputListener.*
}