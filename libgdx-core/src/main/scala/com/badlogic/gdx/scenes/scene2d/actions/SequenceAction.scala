package com.badlogic.gdx.scenes.scene2d.actions

class SequenceAction extends com.badlogic.gdx.scenes.scene2d.actions.ParallelAction {
  private var index: scala.Int = 0
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
    if (this.index >= this.actions.size) {
      return true
    } else ()
    val pool: com.badlogic.gdx.utils.Pool[?] = this.getPool()
    this.setPool(null)
    try {
      if (actions.get(this.index).act(delta)) {
        if (actor == null) {
          return true
        } else ()
        this.index = this.index + 1
        if (this.index >= this.actions.size) {
          return true
        } else ()
      } else ()
      return false
    } finally {
      this.setPool(pool)
    }
  }
  def restart(): scala.Unit = {
    super.restart()
    this.index = 0
  }
}