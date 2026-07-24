package com.badlogic.gdx.scenes.scene2d.actions

class TimeScaleAction extends com.badlogic.gdx.scenes.scene2d.actions.DelegateAction {
  private var scale: scala.Float = 0.0f
  protected def delegate(delta: scala.Float): scala.Boolean = {
    if (action == null) {
      return true
    } else ()
    return action.act(delta * this.scale)
  }
  def getScale(): scala.Float = {
    return this.scale
  }
  def setScale(scale: scala.Float): scala.Unit = {
    this.scale = scale
  }
}