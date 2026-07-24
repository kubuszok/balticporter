package com.badlogic.gdx.scenes.scene2d.utils

abstract class FocusListener extends com.badlogic.gdx.scenes.scene2d.EventListener {
  def handle(event: com.badlogic.gdx.scenes.scene2d.Event): scala.Boolean = {
    if (!event.isInstanceOf[FocusEvent]) {
      return false
    } else ()
    val focusEvent: FocusEvent = event.asInstanceOf[FocusEvent]
    focusEvent.getType() match {
      case Type.keyboard => {
        this.keyboardFocusChanged(focusEvent, event.getTarget(), focusEvent.isFocused())
      }
      case Type.scroll => {
        this.scrollFocusChanged(focusEvent, event.getTarget(), focusEvent.isFocused())
      }
    }
    return false
  }
  def keyboardFocusChanged(event: FocusEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor, focused: scala.Boolean): scala.Unit = {
    ()
  }
  def scrollFocusChanged(event: FocusEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor, focused: scala.Boolean): scala.Unit = {
    ()
  }
  class FocusEvent extends com.badlogic.gdx.scenes.scene2d.Event {
    private var focused: scala.Boolean = false
    private var `type`: Type = null.asInstanceOf[Type]
    private var relatedActor: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
    def reset(): scala.Unit = {
      super.reset()
      this.relatedActor = null
    }
    def isFocused(): scala.Boolean = {
      return this.focused
    }
    def setFocused(focused: scala.Boolean): scala.Unit = {
      this.focused = focused
    }
    def getType(): Type = {
      return this.`type`
    }
    def setType(focusType: Type): scala.Unit = {
      this.`type` = focusType
    }
    def getRelatedActor(): com.badlogic.gdx.scenes.scene2d.Actor = {
      return this.relatedActor
    }
    def setRelatedActor(relatedActor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
      this.relatedActor = relatedActor
    }
    sealed abstract class Type
    object Type {
      case object keyboard extends Type
      case object scroll extends Type
      def values(): Array[Type] = Array(keyboard, scroll)
    }
  }
}