package com.badlogic.gdx.scenes.scene2d.actions

class AddAction extends com.badlogic.gdx.scenes.scene2d.Action {
  private var action: com.badlogic.gdx.scenes.scene2d.Action = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Action]
  override def act(delta: scala.Float): scala.Boolean = {
    target.addAction(this.action)
    return true
  }
  def getAction(): com.badlogic.gdx.scenes.scene2d.Action = {
    return this.action
  }
  def setAction(action: com.badlogic.gdx.scenes.scene2d.Action): scala.Unit = {
    this.action = action
  }
  override def restart(): scala.Unit = {
    if (this.action != null) {
      this.action.restart()
    } else ()
  }
  override def reset(): scala.Unit = {
    super.reset()
    this.action = null
  }
}