package com.badlogic.gdx.scenes.scene2d.utils

class ActorGestureListener extends com.badlogic.gdx.scenes.scene2d.EventListener {
  private var detector: com.badlogic.gdx.input.GestureDetector = null.asInstanceOf[com.badlogic.gdx.input.GestureDetector]
  var event: com.badlogic.gdx.scenes.scene2d.InputEvent = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.InputEvent]
  var actor: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
  var touchDownTarget: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
  def this(halfTapSquareSize: scala.Float, tapCountInterval: scala.Float, longPressDuration: scala.Float, maxFlingDelay: scala.Float) = {
    this()
    this.detector = new com.badlogic.gdx.input.GestureDetector(halfTapSquareSize, tapCountInterval, longPressDuration, maxFlingDelay, new com.badlogic.gdx.input.GestureDetector#GestureAdapter())
  }
  def handle(e: com.badlogic.gdx.scenes.scene2d.Event): scala.Boolean = {
    if (!e.isInstanceOf[com.badlogic.gdx.scenes.scene2d.InputEvent]) {
      return false
    } else ()
    var event: com.badlogic.gdx.scenes.scene2d.InputEvent = e.asInstanceOf[com.badlogic.gdx.scenes.scene2d.InputEvent]
    event.getType() match {
      case com.badlogic.gdx.scenes.scene2d.InputEvent.Type.touchDown => {
        this.actor = event.getListenerActor()
        this.touchDownTarget = event.getTarget()
        this.detector.touchDown(event.getStageX(), event.getStageY(), event.getPointer(), event.getButton())
        this.actor.stageToLocalCoordinates(ActorGestureListener.tmpCoords.set(event.getStageX(), event.getStageY()))
        this.touchDown(event, ActorGestureListener.tmpCoords.x, ActorGestureListener.tmpCoords.y, event.getPointer(), event.getButton())
        if (event.getTouchFocus()) {
          event.getStage().addTouchFocus(this, event.getListenerActor(), event.getTarget(), event.getPointer(), event.getButton())
        } else ()
        return true
      }
      case com.badlogic.gdx.scenes.scene2d.InputEvent.Type.touchUp => {
        val touchFocusCancel: scala.Boolean = event.isTouchFocusCancel()
        if (touchFocusCancel) {
          this.detector.reset()
        } else {
          this.event = event
          this.actor = event.getListenerActor()
          this.detector.touchUp(event.getStageX(), event.getStageY(), event.getPointer(), event.getButton())
          this.actor.stageToLocalCoordinates(ActorGestureListener.tmpCoords.set(event.getStageX(), event.getStageY()))
          this.touchUp(event, ActorGestureListener.tmpCoords.x, ActorGestureListener.tmpCoords.y, event.getPointer(), event.getButton())
        }
        this.event = null
        this.actor = null
        this.touchDownTarget = null
        return !touchFocusCancel
      }
      case com.badlogic.gdx.scenes.scene2d.InputEvent.Type.touchDragged => {
        this.event = event
        this.actor = event.getListenerActor()
        this.detector.touchDragged(event.getStageX(), event.getStageY(), event.getPointer())
        return true
      }
    }
    return false
  }
  def touchDown(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, button: scala.Int): scala.Unit = {
    ()
  }
  def touchUp(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, button: scala.Int): scala.Unit = {
    ()
  }
  def tap(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, count: scala.Int, button: scala.Int): scala.Unit = {
    ()
  }
  def longPress(actor: com.badlogic.gdx.scenes.scene2d.Actor, x: scala.Float, y: scala.Float): scala.Boolean = {
    return false
  }
  def fling(event: com.badlogic.gdx.scenes.scene2d.InputEvent, velocityX: scala.Float, velocityY: scala.Float, button: scala.Int): scala.Unit = {
    ()
  }
  def pan(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, deltaX: scala.Float, deltaY: scala.Float): scala.Unit = {
    ()
  }
  def panStop(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, button: scala.Int): scala.Unit = {
    ()
  }
  def zoom(event: com.badlogic.gdx.scenes.scene2d.InputEvent, initialDistance: scala.Float, distance: scala.Float): scala.Unit = {
    ()
  }
  def pinch(event: com.badlogic.gdx.scenes.scene2d.InputEvent, initialPointer1: com.badlogic.gdx.math.Vector2, initialPointer2: com.badlogic.gdx.math.Vector2, pointer1: com.badlogic.gdx.math.Vector2, pointer2: com.badlogic.gdx.math.Vector2): scala.Unit = {
    ()
  }
  def getGestureDetector(): com.badlogic.gdx.input.GestureDetector = {
    return this.detector
  }
  def getTouchDownTarget(): com.badlogic.gdx.scenes.scene2d.Actor = {
    return this.touchDownTarget
  }
}
object ActorGestureListener {
  final val tmpCoords: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  final val tmpCoords2: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
}