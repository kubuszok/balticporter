package com.badlogic.gdx.scenes.scene2d.utils

class ActorGestureListener(halfTapSquareSize: scala.Float, tapCountInterval: scala.Float, longPressDuration: scala.Float, maxFlingDelay: scala.Float) extends com.badlogic.gdx.scenes.scene2d.EventListener {
  private var detector: com.badlogic.gdx.input.GestureDetector = null.asInstanceOf[com.badlogic.gdx.input.GestureDetector]
  var event: com.badlogic.gdx.scenes.scene2d.InputEvent = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.InputEvent]
  var actor: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
  var touchDownTarget: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
  def this() = {
    this(20, 0.4f, 1.1f, java.lang.Integer.MAX_VALUE)
  }
  this.detector = new com.badlogic.gdx.input.GestureDetector(halfTapSquareSize, tapCountInterval, longPressDuration, maxFlingDelay, new com.badlogic.gdx.input.GestureDetector.GestureAdapter() {
    private final val initialPointer1: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
    private final val initialPointer2: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
    private final val pointer1: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
    private final val pointer2: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
    override def tap(stageX: scala.Float, stageY: scala.Float, count: scala.Int, button: scala.Int): scala.Boolean = {
      ActorGestureListener.this.actor.stageToLocalCoordinates(ActorGestureListener.tmpCoords.set(stageX, stageY))
      ActorGestureListener.this.tap(ActorGestureListener.this.event, ActorGestureListener.tmpCoords.x, ActorGestureListener.tmpCoords.y, count, button)
      return true
    }
    override def longPress(stageX: scala.Float, stageY: scala.Float): scala.Boolean = {
      ActorGestureListener.this.actor.stageToLocalCoordinates(ActorGestureListener.tmpCoords.set(stageX, stageY))
      return ActorGestureListener.this.longPress(ActorGestureListener.this.actor, ActorGestureListener.tmpCoords.x, ActorGestureListener.tmpCoords.y)
    }
    override def fling(velocityX: scala.Float, velocityY: scala.Float, button: scala.Int): scala.Boolean = {
      stageToLocalAmount(ActorGestureListener.tmpCoords.set(velocityX, velocityY))
      ActorGestureListener.this.fling(ActorGestureListener.this.event, ActorGestureListener.tmpCoords.x, ActorGestureListener.tmpCoords.y, button)
      return true
    }
    override def pan(stageX: scala.Float, stageY: scala.Float, deltaX$arg: scala.Float, deltaY$arg: scala.Float): scala.Boolean = {
      var deltaX: scala.Float = deltaX$arg
      var deltaY: scala.Float = deltaY$arg
      stageToLocalAmount(ActorGestureListener.tmpCoords.set(deltaX, deltaY))
      deltaX = ActorGestureListener.tmpCoords.x
      deltaY = ActorGestureListener.tmpCoords.y
      ActorGestureListener.this.actor.stageToLocalCoordinates(ActorGestureListener.tmpCoords.set(stageX, stageY))
      ActorGestureListener.this.pan(ActorGestureListener.this.event, ActorGestureListener.tmpCoords.x, ActorGestureListener.tmpCoords.y, deltaX, deltaY)
      return true
    }
    override def panStop(stageX: scala.Float, stageY: scala.Float, pointer: scala.Int, button: scala.Int): scala.Boolean = {
      ActorGestureListener.this.actor.stageToLocalCoordinates(ActorGestureListener.tmpCoords.set(stageX, stageY))
      ActorGestureListener.this.panStop(ActorGestureListener.this.event, ActorGestureListener.tmpCoords.x, ActorGestureListener.tmpCoords.y, pointer, button)
      return true
    }
    override def zoom(initialDistance: scala.Float, distance: scala.Float): scala.Boolean = {
      ActorGestureListener.this.zoom(ActorGestureListener.this.event, initialDistance, distance)
      return true
    }
    override def pinch(stageInitialPointer1: com.badlogic.gdx.math.Vector2, stageInitialPointer2: com.badlogic.gdx.math.Vector2, stagePointer1: com.badlogic.gdx.math.Vector2, stagePointer2: com.badlogic.gdx.math.Vector2): scala.Boolean = {
      ActorGestureListener.this.actor.stageToLocalCoordinates(initialPointer1.set(stageInitialPointer1))
      ActorGestureListener.this.actor.stageToLocalCoordinates(initialPointer2.set(stageInitialPointer2))
      ActorGestureListener.this.actor.stageToLocalCoordinates(pointer1.set(stagePointer1))
      ActorGestureListener.this.actor.stageToLocalCoordinates(pointer2.set(stagePointer2))
      ActorGestureListener.this.pinch(ActorGestureListener.this.event, initialPointer1, initialPointer2, pointer1, pointer2)
      return true
    }
    private def stageToLocalAmount(amount: com.badlogic.gdx.math.Vector2): scala.Unit = {
      ActorGestureListener.this.actor.stageToLocalCoordinates(amount)
      amount.sub(ActorGestureListener.this.actor.stageToLocalCoordinates(ActorGestureListener.tmpCoords2.set(0, 0)))
    }
  })
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