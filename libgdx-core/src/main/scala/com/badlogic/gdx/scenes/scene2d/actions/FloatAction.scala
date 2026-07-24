package com.badlogic.gdx.scenes.scene2d.actions

class FloatAction extends com.badlogic.gdx.scenes.scene2d.actions.TemporalAction {
  private var start: scala.Float = 0.0f
  private var `end`: scala.Float = 0.0f
  private var value: scala.Float = 0.0f
  def this(start: scala.Float, `end`: scala.Float, duration: scala.Float, interpolation: com.badlogic.gdx.math.Interpolation) = {
    this()
    this.start = start
    this.`end` = `end`
  }
  def this(start: scala.Float, `end`: scala.Float, duration: scala.Float) = {
    this()
    this.start = start
    this.`end` = `end`
  }
  def this(start: scala.Float, `end`: scala.Float) = {
    this()
    this.start = start
    this.`end` = `end`
  }
  this.start = 0
  this.`end` = 1
  def begin(): scala.Unit = {
    this.value = this.start
  }
  def update(percent: scala.Float): scala.Unit = {
    if (percent == 0) {
      this.value = this.start
    } else {
      if (percent == 1) {
        this.value = this.`end`
      } else {
        this.value = this.start + ((this.`end` - this.start) * percent)
      }
    }
  }
  def getValue(): scala.Float = {
    return this.value
  }
  def setValue(value: scala.Float): scala.Unit = {
    this.value = value
  }
  def getStart(): scala.Float = {
    return this.start
  }
  def setStart(start: scala.Float): scala.Unit = {
    this.start = start
  }
  def getEnd(): scala.Float = {
    return this.`end`
  }
  def setEnd(`end`: scala.Float): scala.Unit = {
    this.`end` = `end`
  }
}