package com.badlogic.gdx.scenes.scene2d.actions

class SizeToAction extends com.badlogic.gdx.scenes.scene2d.actions.TemporalAction {
  private var startWidth: scala.Float = 0.0f
  private var startHeight: scala.Float = 0.0f
  private var endWidth: scala.Float = 0.0f
  private var endHeight: scala.Float = 0.0f
  protected def begin(): scala.Unit = {
    this.startWidth = target.getWidth()
    this.startHeight = target.getHeight()
  }
  protected def update(percent: scala.Float): scala.Unit = {
    var width: scala.Float = 0.0f
    var height: scala.Float = 0.0f
    if (percent == 0) {
      width = this.startWidth
      height = this.startHeight
    } else {
      if (percent == 1) {
        width = this.endWidth
        height = this.endHeight
      } else {
        width = this.startWidth + ((this.endWidth - this.startWidth) * percent)
        height = this.startHeight + ((this.endHeight - this.startHeight) * percent)
      }
    }
    target.setSize(width, height)
  }
  def setSize(width: scala.Float, height: scala.Float): scala.Unit = {
    this.endWidth = width
    this.endHeight = height
  }
  def getWidth(): scala.Float = {
    return this.endWidth
  }
  def setWidth(width: scala.Float): scala.Unit = {
    this.endWidth = width
  }
  def getHeight(): scala.Float = {
    return this.endHeight
  }
  def setHeight(height: scala.Float): scala.Unit = {
    this.endHeight = height
  }
}