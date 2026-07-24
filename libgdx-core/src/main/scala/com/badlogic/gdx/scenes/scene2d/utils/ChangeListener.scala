package com.badlogic.gdx.scenes.scene2d.utils

abstract class ChangeListener extends com.badlogic.gdx.scenes.scene2d.EventListener {
  def handle(event: com.badlogic.gdx.scenes.scene2d.Event): scala.Boolean = {
    if (!event.isInstanceOf[ChangeEvent]) {
      return false
    } else ()
    this.changed(event.asInstanceOf[ChangeEvent], event.getTarget())
    return false
  }
  def changed(event: ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit
  class ChangeEvent extends com.badlogic.gdx.scenes.scene2d.Event
}