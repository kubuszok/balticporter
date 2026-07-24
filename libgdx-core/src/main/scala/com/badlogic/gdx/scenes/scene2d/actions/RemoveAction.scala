package com.badlogic.gdx.scenes.scene2d.actions

class RemoveAction extends com.badlogic.gdx.scenes.scene2d.Action {
  private var action: com.badlogic.gdx.scenes.scene2d.Action = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Action]
  def act(delta: scala.Float): scala.Boolean = {
    target.removeAction(this.action)
    return true
  }
  def getAction(): com.badlogic.gdx.scenes.scene2d.Action = {
    return this.action
  }
  def setAction(action: com.badlogic.gdx.scenes.scene2d.Action): scala.Unit = {
    this.action = action
  }
  def reset(): scala.Unit = {
    super.reset()
    this.action = null
  }
}