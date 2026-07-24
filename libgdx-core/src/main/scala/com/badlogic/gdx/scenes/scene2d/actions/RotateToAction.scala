package com.badlogic.gdx.scenes.scene2d.actions

class RotateToAction extends com.badlogic.gdx.scenes.scene2d.actions.TemporalAction {
  private var start: scala.Float = 0.0f
  private var `end`: scala.Float = 0.0f
  private var useShortestDirection: scala.Boolean = false
  def this(useShortestDirection: scala.Boolean) = {
    this()
    this.useShortestDirection = useShortestDirection
  }
  protected def begin(): scala.Unit = {
    this.start = target.getRotation()
  }
  protected def update(percent: scala.Float): scala.Unit = {
    var rotation: scala.Float = 0.0f
    if (percent == 0) {
      rotation = this.start
    } else {
      if (percent == 1) {
        rotation = this.`end`
      } else {
        if (this.useShortestDirection) {
          rotation = com.badlogic.gdx.math.MathUtils.lerpAngleDeg(this.start, this.`end`, percent)
        } else {
          rotation = this.start + ((this.`end` - this.start) * percent)
        }
      }
    }
    target.setRotation(rotation)
  }
  def getRotation(): scala.Float = {
    return this.`end`
  }
  def setRotation(rotation: scala.Float): scala.Unit = {
    this.`end` = rotation
  }
  def isUseShortestDirection(): scala.Boolean = {
    return this.useShortestDirection
  }
  def setUseShortestDirection(useShortestDirection: scala.Boolean): scala.Unit = {
    this.useShortestDirection = useShortestDirection
  }
}