package com.badlogic.gdx.scenes.scene2d.actions

class RemoveActorAction extends com.badlogic.gdx.scenes.scene2d.Action {
  private var removed: scala.Boolean = false
  override def act(delta: scala.Float): scala.Boolean = {
    if (!this.removed) {
      this.removed = true
      target.remove()
    } else ()
    return true
  }
  override def restart(): scala.Unit = {
    this.removed = false
  }
}