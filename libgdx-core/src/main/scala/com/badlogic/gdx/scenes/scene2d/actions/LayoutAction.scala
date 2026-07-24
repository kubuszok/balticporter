package com.badlogic.gdx.scenes.scene2d.actions

class LayoutAction extends com.badlogic.gdx.scenes.scene2d.Action {
  private var enabled: scala.Boolean = false
  def setTarget(actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    if ((actor != null) && (!actor.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout])) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Actor must implement layout: " + actor)
    } else ()
    super.setTarget(actor)
  }
  def act(delta: scala.Float): scala.Boolean = {
    target.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout].setLayoutEnabled(this.enabled)
    return true
  }
  def isEnabled(): scala.Boolean = {
    return this.enabled
  }
  def setLayoutEnabled(enabled: scala.Boolean): scala.Unit = {
    this.enabled = enabled
  }
}