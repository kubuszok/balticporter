package com.badlogic.gdx.scenes.scene2d.actions

class AlphaAction extends com.badlogic.gdx.scenes.scene2d.actions.TemporalAction {
  private var start: scala.Float = 0.0f
  private var `end`: scala.Float = 0.0f
  private var color: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
  def begin(): scala.Unit = {
    if (this.color == null) {
      this.color = target.getColor()
    } else ()
    this.start = this.color.a
  }
  def update(percent: scala.Float): scala.Unit = {
    if (percent == 0) {
      this.color.a = this.start
    } else {
      if (percent == 1) {
        this.color.a = this.`end`
      } else {
        this.color.a = this.start + ((this.`end` - this.start) * percent)
      }
    }
  }
  def reset(): scala.Unit = {
    super.reset()
    this.color = null
  }
  @com.badlogic.gdx.utils.Null
  def getColor(): com.badlogic.gdx.graphics.Color = {
    return this.color
  }
  def setColor(color: com.badlogic.gdx.graphics.Color): scala.Unit = {
    this.color = color
  }
  def getAlpha(): scala.Float = {
    return this.`end`
  }
  def setAlpha(alpha: scala.Float): scala.Unit = {
    this.`end` = alpha
  }
}