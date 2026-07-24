package com.badlogic.gdx.scenes.scene2d.utils

abstract class FocusListener extends com.badlogic.gdx.scenes.scene2d.EventListener {
  def handle(event: com.badlogic.gdx.scenes.scene2d.Event): scala.Boolean = {
    if (!event.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.FocusListener.FocusEvent]) {
      return false
    } else ()
    val focusEvent: com.badlogic.gdx.scenes.scene2d.utils.FocusListener.FocusEvent = event.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.FocusListener.FocusEvent]
    focusEvent.getType() match {
      case com.badlogic.gdx.scenes.scene2d.utils.FocusListener.FocusEvent.Type.keyboard => {
        this.keyboardFocusChanged(focusEvent, event.getTarget(), focusEvent.isFocused())
      }
      case com.badlogic.gdx.scenes.scene2d.utils.FocusListener.FocusEvent.Type.scroll => {
        this.scrollFocusChanged(focusEvent, event.getTarget(), focusEvent.isFocused())
      }
    }
    return false
  }
  def keyboardFocusChanged(event: com.badlogic.gdx.scenes.scene2d.utils.FocusListener.FocusEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor, focused: scala.Boolean): scala.Unit = {
    ()
  }
  def scrollFocusChanged(event: com.badlogic.gdx.scenes.scene2d.utils.FocusListener.FocusEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor, focused: scala.Boolean): scala.Unit = {
    ()
  }
}
object FocusListener {
  class FocusEvent extends com.badlogic.gdx.scenes.scene2d.Event {
    private var focused: scala.Boolean = false
    private var `type`: com.badlogic.gdx.scenes.scene2d.utils.FocusListener.FocusEvent.Type = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.FocusListener.FocusEvent.Type]
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
    def getType(): com.badlogic.gdx.scenes.scene2d.utils.FocusListener.FocusEvent.Type = {
      return this.`type`
    }
    def setType(focusType: com.badlogic.gdx.scenes.scene2d.utils.FocusListener.FocusEvent.Type): scala.Unit = {
      this.`type` = focusType
    }
    def getRelatedActor(): com.badlogic.gdx.scenes.scene2d.Actor = {
      return this.relatedActor
    }
    def setRelatedActor(relatedActor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
      this.relatedActor = relatedActor
    }
  }
  object FocusEvent {
    sealed abstract class Type {
      def name(): java.lang.String = this.toString()
    }
    object Type {
      case object keyboard extends Type
      case object scroll extends Type
      def values(): scala.Array[Type] = scala.Array(keyboard, scroll)
      def valueOf(name: java.lang.String): Type = name match {
        case "keyboard" => keyboard
        case "scroll" => scroll
        case _ => throw new java.lang.IllegalArgumentException(name)
      }
    }
  }
}