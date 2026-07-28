package com.badlogic.gdx.scenes.scene2d.actions

class TouchableAction extends com.badlogic.gdx.scenes.scene2d.Action {
  private var touchable: com.badlogic.gdx.scenes.scene2d.Touchable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Touchable]
  override def act(delta: scala.Float): scala.Boolean = {
    target.setTouchable(this.touchable)
    return true
  }
  def getTouchable(): com.badlogic.gdx.scenes.scene2d.Touchable = {
    return this.touchable
  }
  def setTouchable(touchable: com.badlogic.gdx.scenes.scene2d.Touchable): scala.Unit = {
    this.touchable = touchable
  }
}