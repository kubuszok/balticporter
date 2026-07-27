package com.badlogic.gdx.scenes.scene2d.actions

class IntAction extends com.badlogic.gdx.scenes.scene2d.actions.TemporalAction {
  private var start: scala.Int = 0
  private var `end`: scala.Int = 0
  private var value: scala.Int = 0
  def this(start: scala.Int, `end`: scala.Int) = {
    this()
    this.start = start
    this.`end` = `end`
  }
  def this(start: scala.Int, `end`: scala.Int, duration: scala.Float) = {
    this()
    this.duration = duration
    this.start = start
    this.`end` = `end`
  }
  def this(start: scala.Int, `end`: scala.Int, duration: scala.Float, interpolation: com.badlogic.gdx.math.Interpolation) = {
    this()
    this.duration = duration
    this.interpolation = interpolation
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
        this.value = (this.start + ((this.`end` - this.start) * percent)).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
      }
    }
  }
  def getValue(): scala.Int = {
    return this.value
  }
  def setValue(value: scala.Int): scala.Unit = {
    this.value = value
  }
  def getStart(): scala.Int = {
    return this.start
  }
  def setStart(start: scala.Int): scala.Unit = {
    this.start = start
  }
  def getEnd(): scala.Int = {
    return this.`end`
  }
  def setEnd(`end`: scala.Int): scala.Unit = {
    this.`end` = `end`
  }
}