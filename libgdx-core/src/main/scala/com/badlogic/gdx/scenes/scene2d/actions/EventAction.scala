package com.badlogic.gdx.scenes.scene2d.actions

abstract class EventAction[T <: com.badlogic.gdx.scenes.scene2d.Event](eventClass$p: java.lang.Class[? <: T]) extends com.badlogic.gdx.scenes.scene2d.Action {
  var eventClass: java.lang.Class[? <: T] = null.asInstanceOf[java.lang.Class[? <: T]]
  var result: scala.Boolean = false
  var active: scala.Boolean = false
  private final val listener: com.badlogic.gdx.scenes.scene2d.EventListener = new com.badlogic.gdx.scenes.scene2d.EventListener() {
    override def handle(event: com.badlogic.gdx.scenes.scene2d.Event): scala.Boolean = {
      if ((!EventAction.this.active) || (!EventAction.this.eventClass.isInstance(event))) {
        return false
      } else ()
      EventAction.this.result = EventAction.this.handle(event.asInstanceOf[T])
      return EventAction.this.result
    }
  }
  this.eventClass = eventClass$p
  override def restart(): scala.Unit = {
    this.result = false
    this.active = false
  }
  override def setTarget(newTarget: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    if (target != null) {
      target.removeListener(this.listener)
    } else ()
    super.setTarget(newTarget)
    if (newTarget != null) {
      newTarget.addListener(this.listener)
    } else ()
  }
  def handle(event: T): scala.Boolean
  override def act(delta: scala.Float): scala.Boolean = {
    this.active = true
    return this.result
  }
  def isActive(): scala.Boolean = {
    return this.active
  }
  def setActive(active: scala.Boolean): scala.Unit = {
    this.active = active
  }
}