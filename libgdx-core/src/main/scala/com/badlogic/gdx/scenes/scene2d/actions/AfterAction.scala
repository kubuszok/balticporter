package com.badlogic.gdx.scenes.scene2d.actions

class AfterAction extends com.badlogic.gdx.scenes.scene2d.actions.DelegateAction {
  private var waitForActions: com.badlogic.gdx.utils.Array[com.badlogic.gdx.scenes.scene2d.Action] = new com.badlogic.gdx.utils.Array(false, 4)
  def setTarget(target: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    if (target != null) {
      this.waitForActions.addAll(target.getActions())
    } else ()
    super.setTarget(target)
  }
  def restart(): scala.Unit = {
    super.restart()
    this.waitForActions.clear()
  }
  protected def delegate(delta: scala.Float): scala.Boolean = {
    val currentActions: com.badlogic.gdx.utils.Array[com.badlogic.gdx.scenes.scene2d.Action] = target.getActions()
    if (currentActions.size == 1) {
      this.waitForActions.clear()
    } else ()
    { var i: scala.Int = this.waitForActions.size - 1; while (i >= 0) { {
      val action: com.badlogic.gdx.scenes.scene2d.Action = this.waitForActions.get(i)
      val index: scala.Int = currentActions.indexOf(action, true)
      if (index == (-1)) {
        this.waitForActions.removeIndex(i)
      } else ()
    }; i = i - 1 } }
    if (this.waitForActions.size > 0) {
      return false
    } else ()
    return action.act(delta)
  }
}