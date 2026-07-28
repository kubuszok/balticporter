package com.badlogic.gdx.scenes.scene2d.actions

class ScaleToAction extends com.badlogic.gdx.scenes.scene2d.actions.TemporalAction {
  private var startX: scala.Float = 0.0f
  private var startY: scala.Float = 0.0f
  private var endX: scala.Float = 0.0f
  private var endY: scala.Float = 0.0f
  override def begin(): scala.Unit = {
    this.startX = target.getScaleX()
    this.startY = target.getScaleY()
  }
  override def update(percent: scala.Float): scala.Unit = {
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
    target.setScale(x, y)
  }
  def setScale(x: scala.Float, y: scala.Float): scala.Unit = {
    this.endX = x
    this.endY = y
  }
  def setScale(scale: scala.Float): scala.Unit = {
    this.endX = scale
    this.endY = scale
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
}