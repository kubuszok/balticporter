package com.badlogic.gdx.scenes.scene2d

abstract class Action extends com.badlogic.gdx.utils.Pool.Poolable {
  var actor: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
  var target: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
  private var pool: com.badlogic.gdx.utils.Pool[?] = null.asInstanceOf[com.badlogic.gdx.utils.Pool[?]]
  def act(delta: scala.Float): scala.Boolean
  def restart(): scala.Unit = {
    ()
  }
  def setActor(actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    this.actor = actor
    if (this.target == null) {
      this.setTarget(actor)
    } else ()
    if (actor == null) {
      if (this.pool != null) {
        this.pool.asInstanceOf[com.badlogic.gdx.utils.Pool[java.lang.Object]].free(this.asInstanceOf[java.lang.Object])
        this.pool = null
      } else ()
    } else ()
  }
  def getActor(): com.badlogic.gdx.scenes.scene2d.Actor = {
    return this.actor
  }
  def setTarget(target: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    this.target = target
  }
  def getTarget(): com.badlogic.gdx.scenes.scene2d.Actor = {
    return this.target
  }
  override def reset(): scala.Unit = {
    this.actor = null
    this.target = null
    this.pool = null
    this.restart()
  }
  @com.badlogic.gdx.utils.Null
  def getPool(): com.badlogic.gdx.utils.Pool[?] = {
    return this.pool.asInstanceOf[com.badlogic.gdx.utils.Pool[?]]
  }
  def setPool(pool: com.badlogic.gdx.utils.Pool[?]): scala.Unit = {
    this.pool = pool.asInstanceOf[com.badlogic.gdx.utils.Pool[?]]
  }
  override def toString(): java.lang.String = {
    var name: java.lang.String = this.getClass().getName()
    val dotIndex: scala.Int = name.lastIndexOf('.')
    if (dotIndex != (-1)) {
      name = name.substring(dotIndex + 1)
    } else ()
    if (name.endsWith("Action")) {
      name = name.substring(0, name.length() - 6)
    } else ()
    return name
  }
}