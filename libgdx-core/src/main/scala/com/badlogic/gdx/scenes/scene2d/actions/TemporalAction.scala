package com.badlogic.gdx.scenes.scene2d.actions

abstract class TemporalAction extends com.badlogic.gdx.scenes.scene2d.Action with com.badlogic.gdx.scenes.scene2d.actions.FinishableAction {
  var duration: scala.Float = 0.0f
  private var time: scala.Float = 0.0f
  var interpolation: com.badlogic.gdx.math.Interpolation = null.asInstanceOf[com.badlogic.gdx.math.Interpolation]
  private var reverse: scala.Boolean = false
  private var began: scala.Boolean = false
  private var complete: scala.Boolean = false
  def this(duration: scala.Float) = {
    this()
    this.duration = duration
  }
  def this(duration: scala.Float, interpolation: com.badlogic.gdx.math.Interpolation) = {
    this()
    this.duration = duration
    this.interpolation = interpolation
  }
  def act(delta: scala.Float): scala.Boolean = {
    if (this.complete) {
      return true
    } else ()
    val pool: com.badlogic.gdx.utils.Pool[?] = this.getPool().asInstanceOf[com.badlogic.gdx.utils.Pool[?]]
    this.setPool(null)
    try {
      if (!this.began) {
        this.begin()
        this.began = true
      } else ()
      this.time = this.time + delta
      this.complete = this.time >= this.duration
      var percent: scala.Float = if (this.complete) 1 else this.time / this.duration
      if (this.interpolation != null) {
        percent = this.interpolation.apply(percent)
      } else ()
      this.update(if (this.reverse) 1 - percent else percent)
      if (this.complete) {
        this.`end`()
      } else ()
      return this.complete
    } finally {
      this.setPool(pool.asInstanceOf[com.badlogic.gdx.utils.Pool[?]])
    }
  }
  def begin(): scala.Unit = {
    ()
  }
  def `end`(): scala.Unit = {
    ()
  }
  def update(percent: scala.Float): scala.Unit
  def finish(): scala.Unit = {
    this.time = this.duration
  }
  def restart(): scala.Unit = {
    this.time = 0
    this.began = false
    this.complete = false
  }
  def reset(): scala.Unit = {
    super.reset()
    this.reverse = false
    this.interpolation = null
  }
  def getTime(): scala.Float = {
    return this.time
  }
  def setTime(time: scala.Float): scala.Unit = {
    this.time = time
  }
  def getDuration(): scala.Float = {
    return this.duration
  }
  def setDuration(duration: scala.Float): scala.Unit = {
    this.duration = duration
  }
  @com.badlogic.gdx.utils.Null
  def getInterpolation(): com.badlogic.gdx.math.Interpolation = {
    return this.interpolation
  }
  def setInterpolation(interpolation: com.badlogic.gdx.math.Interpolation): scala.Unit = {
    this.interpolation = interpolation
  }
  def isReverse(): scala.Boolean = {
    return this.reverse
  }
  def setReverse(reverse: scala.Boolean): scala.Unit = {
    this.reverse = reverse
  }
  def isComplete(): scala.Boolean = {
    return this.complete
  }
}