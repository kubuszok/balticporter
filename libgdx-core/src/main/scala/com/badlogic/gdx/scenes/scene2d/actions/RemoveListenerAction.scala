package com.badlogic.gdx.scenes.scene2d.actions

class RemoveListenerAction extends com.badlogic.gdx.scenes.scene2d.Action {
  private var listener: com.badlogic.gdx.scenes.scene2d.EventListener = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.EventListener]
  private var capture: scala.Boolean = false
  override def act(delta: scala.Float): scala.Boolean = {
    if (this.capture) {
      target.removeCaptureListener(this.listener)
    } else {
      target.removeListener(this.listener)
    }
    return true
  }
  def getListener(): com.badlogic.gdx.scenes.scene2d.EventListener = {
    return this.listener
  }
  def setListener(listener: com.badlogic.gdx.scenes.scene2d.EventListener): scala.Unit = {
    this.listener = listener
  }
  def getCapture(): scala.Boolean = {
    return this.capture
  }
  def setCapture(capture: scala.Boolean): scala.Unit = {
    this.capture = capture
  }
  override def reset(): scala.Unit = {
    super.reset()
    this.listener = null
  }
}