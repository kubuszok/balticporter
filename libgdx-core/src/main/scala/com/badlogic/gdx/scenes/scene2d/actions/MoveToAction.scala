package com.badlogic.gdx.scenes.scene2d.actions

class MoveToAction extends com.badlogic.gdx.scenes.scene2d.actions.TemporalAction {
  private var startX: scala.Float = 0.0f
  private var startY: scala.Float = 0.0f
  private var endX: scala.Float = 0.0f
  private var endY: scala.Float = 0.0f
  private var alignment: scala.Int = com.badlogic.gdx.utils.Align.bottomLeft
  def begin(): scala.Unit = {
    this.startX = target.getX(this.alignment)
    this.startY = target.getY(this.alignment)
  }
  def update(percent: scala.Float): scala.Unit = {
    var x: scala.Float = 0.0f
    var y: scala.Float = 0.0f
    if (percent == 0) {
      x = this.startX
      y = this.startY
    } else {
      if (percent == 1) {
        x = this.endX
        y = this.endY
      } else {
        x = this.startX + ((this.endX - this.startX) * percent)
        y = this.startY + ((this.endY - this.startY) * percent)
      }
    }
    target.setPosition(x, y, this.alignment)
  }
  def reset(): scala.Unit = {
    super.reset()
    this.alignment = com.badlogic.gdx.utils.Align.bottomLeft
  }
  def setStartPosition(x: scala.Float, y: scala.Float): scala.Unit = {
    this.startX = x
    this.startY = y
  }
  def setPosition(x: scala.Float, y: scala.Float): scala.Unit = {
    this.endX = x
    this.endY = y
  }
  def setPosition(x: scala.Float, y: scala.Float, alignment: scala.Int): scala.Unit = {
    this.endX = x
    this.endY = y
    this.alignment = alignment
  }
  def getX(): scala.Float = {
    return this.endX
  }
  def setX(x: scala.Float): scala.Unit = {
    this.endX = x
  }
  def getY(): scala.Float = {
    return this.endY
  }
  def setY(y: scala.Float): scala.Unit = {
    this.endY = y
  }
  def getStartX(): scala.Float = {
    return this.startX
  }
  def getStartY(): scala.Float = {
    return this.startY
  }
  def getAlignment(): scala.Int = {
    return this.alignment
  }
  def setAlignment(alignment: scala.Int): scala.Unit = {
    this.alignment = alignment
  }
}