package com.badlogic.gdx.scenes.scene2d.actions

abstract class DelegateAction extends com.badlogic.gdx.scenes.scene2d.Action {
  var action: com.badlogic.gdx.scenes.scene2d.Action = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Action]
  def setAction(action: com.badlogic.gdx.scenes.scene2d.Action): scala.Unit = {
    this.action = action
  }
  def getAction(): com.badlogic.gdx.scenes.scene2d.Action = {
    return this.action
  }
  def delegate(delta: scala.Float): scala.Boolean
  override final def act(delta: scala.Float): scala.Boolean = {
    val pool: com.badlogic.gdx.utils.Pool[?] = this.getPool().asInstanceOf[com.badlogic.gdx.utils.Pool[?]]
    this.setPool(null)
    try {
      return this.delegate(delta)
    } finally {
      this.setPool(pool.asInstanceOf[com.badlogic.gdx.utils.Pool[?]])
    }
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
  override def setActor(actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    if (this.action != null) {
      this.action.setActor(actor)
    } else ()
    super.setActor(actor)
  }
  override def setTarget(target: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    if (this.action != null) {
      this.action.setTarget(target)
    } else ()
    super.setTarget(target)
  }
  override def toString(): java.lang.String = {
    return super.toString() + (if (this.action == null) "" else ("(" + this.action) + ")")
  }
}