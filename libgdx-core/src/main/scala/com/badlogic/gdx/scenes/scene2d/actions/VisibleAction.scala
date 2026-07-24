package com.badlogic.gdx.scenes.scene2d.actions

class VisibleAction extends com.badlogic.gdx.scenes.scene2d.Action {
  private var visible: scala.Boolean = false
  def act(delta: scala.Float): scala.Boolean = {
    target.setVisible(this.visible)
    return true
  }
  def isVisible(): scala.Boolean = {
    return this.visible
  }
  def setVisible(visible: scala.Boolean): scala.Unit = {
    this.visible = visible
  }
}