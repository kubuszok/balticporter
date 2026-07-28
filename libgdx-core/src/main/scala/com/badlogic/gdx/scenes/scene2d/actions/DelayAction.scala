package com.badlogic.gdx.scenes.scene2d.actions

class DelayAction extends com.badlogic.gdx.scenes.scene2d.actions.DelegateAction with com.badlogic.gdx.scenes.scene2d.actions.FinishableAction {
  private var duration: scala.Float = 0.0f
  private var time: scala.Float = 0.0f
  def this(duration: scala.Float) = {
    this()
    this.duration = duration
  }
  override def delegate(delta$arg: scala.Float): scala.Boolean = {
    var delta: scala.Float = delta$arg
    if (this.time < this.duration) {
      this.time = this.time + delta
      if (this.time < this.duration) {
        return false
      } else ()
      delta = this.time - this.duration
    } else ()
    if (action == null) {
      return true
    } else ()
    return action.act(delta)
  }
  override def finish(): scala.Unit = {
    this.time = this.duration
  }
  override def restart(): scala.Unit = {
    super.restart()
    this.time = 0
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
}