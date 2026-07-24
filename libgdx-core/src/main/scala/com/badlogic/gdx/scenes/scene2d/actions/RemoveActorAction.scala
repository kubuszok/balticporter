package com.badlogic.gdx.scenes.scene2d.actions

class RemoveActorAction extends com.badlogic.gdx.scenes.scene2d.Action {
  private var removed: scala.Boolean = false
  def act(delta: scala.Float): scala.Boolean = {
    if (!this.removed) {
      this.removed = true
      target.remove()
    } else ()
    return true
  }
  def restart(): scala.Unit = {
    this.removed = false
  }
}