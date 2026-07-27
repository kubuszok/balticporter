package com.badlogic.gdx.scenes.scene2d.actions

class ParallelAction extends com.badlogic.gdx.scenes.scene2d.Action {
  var actions: com.badlogic.gdx.utils.Array[com.badlogic.gdx.scenes.scene2d.Action] = new com.badlogic.gdx.utils.Array(4).asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.scenes.scene2d.Action]]
  private var complete: scala.Boolean = false
  def this(action1: com.badlogic.gdx.scenes.scene2d.Action) = {
    this()
    this.addAction(action1)
  }
  def this(action1: com.badlogic.gdx.scenes.scene2d.Action, action2: com.badlogic.gdx.scenes.scene2d.Action) = {
    this()
    this.addAction(action1)
    this.addAction(action2)
  }
  def this(action1: com.badlogic.gdx.scenes.scene2d.Action, action2: com.badlogic.gdx.scenes.scene2d.Action, action3: com.badlogic.gdx.scenes.scene2d.Action) = {
    this()
    this.addAction(action1)
    this.addAction(action2)
    this.addAction(action3)
  }
  def this(action1: com.badlogic.gdx.scenes.scene2d.Action, action2: com.badlogic.gdx.scenes.scene2d.Action, action3: com.badlogic.gdx.scenes.scene2d.Action, action4: com.badlogic.gdx.scenes.scene2d.Action) = {
    this()
    this.addAction(action1)
    this.addAction(action2)
    this.addAction(action3)
    this.addAction(action4)
  }
  def this(action1: com.badlogic.gdx.scenes.scene2d.Action, action2: com.badlogic.gdx.scenes.scene2d.Action, action3: com.badlogic.gdx.scenes.scene2d.Action, action4: com.badlogic.gdx.scenes.scene2d.Action, action5: com.badlogic.gdx.scenes.scene2d.Action) = {
    this()
    this.addAction(action1)
    this.addAction(action2)
    this.addAction(action3)
    this.addAction(action4)
    this.addAction(action5)
  }
  def act(delta: scala.Float): scala.Boolean = {
    if (this.complete) {
      return true
    } else ()
    this.complete = true
    val pool: com.badlogic.gdx.utils.Pool[?] = this.getPool().asInstanceOf[com.badlogic.gdx.utils.Pool[?]]
    this.setPool(null)
    try {
      val actions: com.badlogic.gdx.utils.Array[com.badlogic.gdx.scenes.scene2d.Action] = this.actions;
      { var i: scala.Int = 0; val n: scala.Int = actions.size; while ((i < n) && (actor != null)) { {
        val currentAction: com.badlogic.gdx.scenes.scene2d.Action = actions.get(i)
        if ((currentAction.getActor() != null) && (!currentAction.act(delta))) {
          this.complete = false
        } else ()
        if (actor == null) {
          return true
        } else ()
      }; i = i + 1 } }
      return this.complete
    } finally {
      this.setPool(pool.asInstanceOf[com.badlogic.gdx.utils.Pool[?]])
    }
  }
  def restart(): scala.Unit = {
    this.complete = false
    val actions: com.badlogic.gdx.utils.Array[com.badlogic.gdx.scenes.scene2d.Action] = this.actions;
    { var i: scala.Int = 0; val n: scala.Int = actions.size; while (i < n) { {
      actions.get(i).restart()
    }; i = i + 1 } }
  }
  def reset(): scala.Unit = {
    super.reset()
    this.actions.clear()
  }
  def addAction(action: com.badlogic.gdx.scenes.scene2d.Action): scala.Unit = {
    this.actions.add(action)
    if (actor != null) {
      action.setActor(actor)
    } else ()
  }
  def setActor(actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    val actions: com.badlogic.gdx.utils.Array[com.badlogic.gdx.scenes.scene2d.Action] = this.actions;
    { var i: scala.Int = 0; val n: scala.Int = actions.size; while (i < n) { {
      actions.get(i).setActor(actor)
    }; i = i + 1 } }
    super.setActor(actor)
  }
  def getActions(): com.badlogic.gdx.utils.Array[com.badlogic.gdx.scenes.scene2d.Action] = {
    return this.actions
  }
  def toString(): java.lang.String = {
    val buffer: java.lang.StringBuilder = new java.lang.StringBuilder(64)
    buffer.append(super.toString())
    buffer.append('(')
    val actions: com.badlogic.gdx.utils.Array[com.badlogic.gdx.scenes.scene2d.Action] = this.actions;
    { var i: scala.Int = 0; val n: scala.Int = actions.size; while (i < n) { {
      if (i > 0) {
        buffer.append(", ")
      } else ()
      buffer.append(actions.get(i))
    }; i = i + 1 } }
    buffer.append(')')
    return buffer.toString()
  }
}