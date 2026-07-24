package com.badlogic.gdx.scenes.scene2d.utils

abstract class ChangeListener extends com.badlogic.gdx.scenes.scene2d.EventListener {
  def handle(event: com.badlogic.gdx.scenes.scene2d.Event): scala.Boolean = {
    if (!event.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent]) {
      return false
    } else ()
    this.changed(event.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent], event.getTarget())
    return false
  }
  def changed(event: com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit
}
object ChangeListener {
  class ChangeEvent extends com.badlogic.gdx.scenes.scene2d.Event
}