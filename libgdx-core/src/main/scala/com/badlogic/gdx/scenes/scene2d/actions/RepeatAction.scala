package com.badlogic.gdx.scenes.scene2d.actions

class RepeatAction extends com.badlogic.gdx.scenes.scene2d.actions.DelegateAction with com.badlogic.gdx.scenes.scene2d.actions.FinishableAction {
  private var repeatCount: scala.Int = 0
  private var executedCount: scala.Int = 0
  private var finished: scala.Boolean = false
  def delegate(delta: scala.Float): scala.Boolean = {
    if (this.executedCount == this.repeatCount) {
      return true
    } else ()
    if (action.act(delta)) {
      if (this.finished) {
        return true
      } else ()
      if (this.repeatCount > 0) {
        this.executedCount = this.executedCount + 1
      } else ()
      if (this.executedCount == this.repeatCount) {
        return true
      } else ()
      if (action != null) {
        action.restart()
      } else ()
    } else ()
    return false
  }
  def finish(): scala.Unit = {
    this.finished = true
  }
  def restart(): scala.Unit = {
    super.restart()
    this.executedCount = 0
    this.finished = false
  }
  def setCount(count: scala.Int): scala.Unit = {
    this.repeatCount = count
  }
  def getCount(): scala.Int = {
    return this.repeatCount
  }
}
object RepeatAction {
  final val FOREVER: scala.Int = -1
}