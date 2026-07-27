package com.badlogic.gdx.scenes.scene2d.utils

class DragAndDrop {
  var dragSource: com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Source = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Source]
  var payload: com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Payload = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Payload]
  var dragActor: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
  var removeDragActor: scala.Boolean = false
  var target: com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Target = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Target]
  var isValidTarget: scala.Boolean = false
  final val targets: com.badlogic.gdx.utils.Array[com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Target] = new com.badlogic.gdx.utils.Array(8).asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Target]]
  final val sourceListeners: com.badlogic.gdx.utils.ObjectMap[com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Source, com.badlogic.gdx.scenes.scene2d.utils.DragListener] = new com.badlogic.gdx.utils.ObjectMap(8).asInstanceOf[com.badlogic.gdx.utils.ObjectMap[com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Source, com.badlogic.gdx.scenes.scene2d.utils.DragListener]]
  private var tapSquareSize: scala.Float = 8
  private var button: scala.Int = 0
  var dragActorX: scala.Float = 0
  var dragActorY: scala.Float = 0
  var touchOffsetX: scala.Float = 0.0f
  var touchOffsetY: scala.Float = 0.0f
  var dragValidTime: scala.Long = 0L
  var dragTime: scala.Int = 250
  var activePointer: scala.Int = -1
  var cancelTouchFocus: scala.Boolean = true
  var keepWithinStage: scala.Boolean = true
  def addSource(source: com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Source): scala.Unit = {
    val listener: com.badlogic.gdx.scenes.scene2d.utils.DragListener = new com.badlogic.gdx.scenes.scene2d.utils.DragListener() {
      override def dragStart(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int): scala.Unit = {
        if (DragAndDrop.this.activePointer != (-1)) {
          event.stop()
          return
        } else ()
        DragAndDrop.this.activePointer = pointer
        DragAndDrop.this.dragValidTime = java.lang.System.currentTimeMillis() + DragAndDrop.this.dragTime
        DragAndDrop.this.dragSource = source
        DragAndDrop.this.payload = source.dragStart(event, getTouchDownX(), getTouchDownY(), pointer)
        event.stop()
        if (DragAndDrop.this.cancelTouchFocus && (DragAndDrop.this.payload != null)) {
          val stage: com.badlogic.gdx.scenes.scene2d.Stage = source.getActor().getStage()
          if (stage != null) {
            stage.cancelTouchFocusExcept(this, source.getActor())
          } else ()
        } else ()
      }
      override def drag(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int): scala.Unit = {
        if (DragAndDrop.this.payload == null) {
          return
        } else ()
        if (pointer != DragAndDrop.this.activePointer) {
          return
        } else ()
        source.drag(event, x, y, pointer)
        val stage: com.badlogic.gdx.scenes.scene2d.Stage = event.getStage()
        val oldDragActor: com.badlogic.gdx.scenes.scene2d.Actor = DragAndDrop.this.dragActor
        var oldDragActorX: scala.Float = 0
        var oldDragActorY: scala.Float = 0
        if (oldDragActor != null) {
          oldDragActorX = oldDragActor.getX()
          oldDragActorY = oldDragActor.getY()
          oldDragActor.setPosition(java.lang.Integer.MAX_VALUE, java.lang.Integer.MAX_VALUE)
        } else ()
        val stageX: scala.Float = event.getStageX() + DragAndDrop.this.touchOffsetX
        val stageY: scala.Float = event.getStageY() + DragAndDrop.this.touchOffsetY
        var hit: com.badlogic.gdx.scenes.scene2d.Actor = event.getStage().hit(stageX, stageY, true)
        if (hit == null) {
          hit = event.getStage().hit(stageX, stageY, false)
        } else ()
        if (oldDragActor != null) {
          oldDragActor.setPosition(oldDragActorX, oldDragActorY)
        } else ()
        var newTarget: com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Target = null
        DragAndDrop.this.isValidTarget = false
        if (hit != null) {
          { var i: scala.Int = 0; val n: scala.Int = DragAndDrop.this.targets.size; while (i < n) { {
            var target: com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Target = DragAndDrop.this.targets.get(i)
            if (!target.actor.isAscendantOf(hit)) {
              /* continue */ ()
            } else ()
            newTarget = target
            target.actor.stageToLocalCoordinates(DragAndDrop.tmpVector.set(stageX, stageY))
            /* break */ ()
          }; i = i + 1 } }
        } else ()
        if (newTarget != DragAndDrop.this.target) {
          if (DragAndDrop.this.target != null) {
            DragAndDrop.this.target.reset(source, DragAndDrop.this.payload)
          } else ()
          DragAndDrop.this.target = newTarget
        } else ()
        if (newTarget != null) {
          DragAndDrop.this.isValidTarget = newTarget.drag(source, DragAndDrop.this.payload, DragAndDrop.tmpVector.x, DragAndDrop.tmpVector.y, pointer)
        } else ()
        var actor: com.badlogic.gdx.scenes.scene2d.Actor = null
        if (DragAndDrop.this.target != null) {
          actor = if (DragAndDrop.this.isValidTarget) DragAndDrop.this.payload.validDragActor else DragAndDrop.this.payload.invalidDragActor
        } else ()
        if (actor == null) {
          actor = DragAndDrop.this.payload.dragActor
        } else ()
        if (actor != oldDragActor) {
          if ((oldDragActor != null) && DragAndDrop.this.removeDragActor) {
            oldDragActor.remove()
          } else ()
          DragAndDrop.this.dragActor = actor
          DragAndDrop.this.removeDragActor = actor.getStage() == null
          if (DragAndDrop.this.removeDragActor) {
            stage.addActor(actor)
          } else ()
        } else ()
        if (actor == null) {
          return
        } else ()
        var actorX: scala.Float = (event.getStageX() - actor.getWidth()) + DragAndDrop.this.dragActorX
        var actorY: scala.Float = event.getStageY() + DragAndDrop.this.dragActorY
        if (DragAndDrop.this.keepWithinStage) {
          if (actorX < 0) {
            actorX = 0
          } else ()
          if (actorY < 0) {
            actorY = 0
          } else ()
          if ((actorX + actor.getWidth()) > stage.getWidth()) {
            actorX = stage.getWidth() - actor.getWidth()
          } else ()
          if ((actorY + actor.getHeight()) > stage.getHeight()) {
            actorY = stage.getHeight() - actor.getHeight()
          } else ()
        } else ()
        actor.setPosition(actorX, actorY)
      }
      override def dragStop(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int): scala.Unit = {
        if (pointer != DragAndDrop.this.activePointer) {
          return
        } else ()
        DragAndDrop.this.activePointer = -1
        if (DragAndDrop.this.payload == null) {
          return
        } else ()
        if (java.lang.System.currentTimeMillis() < DragAndDrop.this.dragValidTime) {
          DragAndDrop.this.isValidTarget = false
        } else {
          if ((!DragAndDrop.this.isValidTarget) && (DragAndDrop.this.target != null)) {
            val stageX: scala.Float = event.getStageX() + DragAndDrop.this.touchOffsetX
            val stageY: scala.Float = event.getStageY() + DragAndDrop.this.touchOffsetY
            DragAndDrop.this.target.actor.stageToLocalCoordinates(DragAndDrop.tmpVector.set(stageX, stageY))
            DragAndDrop.this.isValidTarget = DragAndDrop.this.target.drag(source, DragAndDrop.this.payload, DragAndDrop.tmpVector.x, DragAndDrop.tmpVector.y, pointer)
          } else ()
        }
        if ((DragAndDrop.this.dragActor != null) && DragAndDrop.this.removeDragActor) {
          DragAndDrop.this.dragActor.remove()
        } else ()
        if (DragAndDrop.this.isValidTarget) {
          val stageX: scala.Float = event.getStageX() + DragAndDrop.this.touchOffsetX
          val stageY: scala.Float = event.getStageY() + DragAndDrop.this.touchOffsetY
          DragAndDrop.this.target.actor.stageToLocalCoordinates(DragAndDrop.tmpVector.set(stageX, stageY))
          DragAndDrop.this.target.drop(source, DragAndDrop.this.payload, DragAndDrop.tmpVector.x, DragAndDrop.tmpVector.y, pointer)
        } else ()
        source.dragStop(event, x, y, pointer, DragAndDrop.this.payload, if (DragAndDrop.this.isValidTarget) DragAndDrop.this.target else null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Target])
        if (DragAndDrop.this.target != null) {
          DragAndDrop.this.target.reset(source, DragAndDrop.this.payload)
        } else ()
        DragAndDrop.this.dragSource = null
        DragAndDrop.this.payload = null
        DragAndDrop.this.target = null
        DragAndDrop.this.isValidTarget = false
        DragAndDrop.this.dragActor = null
      }
    }
    listener.setTapSquareSize(this.tapSquareSize)
    listener.setButton(this.button)
    source.actor.addCaptureListener(listener)
    this.sourceListeners.put(source, listener)
  }
  def removeSource(source: com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Source): scala.Unit = {
    val dragListener: com.badlogic.gdx.scenes.scene2d.utils.DragListener = this.sourceListeners.remove(source)
    source.actor.removeCaptureListener(dragListener)
  }
  def addTarget(target: com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Target): scala.Unit = {
    this.targets.add(target)
  }
  def removeTarget(target: com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Target): scala.Unit = {
    this.targets.removeValue(target, true)
  }
  def clear(): scala.Unit = {
    this.targets.clear()
    for (entry <- this.sourceListeners.entries()) {
      entry.key.actor.removeCaptureListener(entry.value)
    }
    this.sourceListeners.clear(8)
  }
  def cancelTouchFocusExcept(except: com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Source): scala.Unit = {
    val listener: com.badlogic.gdx.scenes.scene2d.utils.DragListener = this.sourceListeners.get(except)
    if (listener == null) {
      return
    } else ()
    val stage: com.badlogic.gdx.scenes.scene2d.Stage = except.getActor().getStage()
    if (stage != null) {
      stage.cancelTouchFocusExcept(listener, except.getActor())
    } else ()
  }
  def setTapSquareSize(halfTapSquareSize: scala.Float): scala.Unit = {
    this.tapSquareSize = halfTapSquareSize
  }
  def setButton(button: scala.Int): scala.Unit = {
    this.button = button
  }
  def setDragActorPosition(dragActorX: scala.Float, dragActorY: scala.Float): scala.Unit = {
    this.dragActorX = dragActorX
    this.dragActorY = dragActorY
  }
  def setTouchOffset(touchOffsetX: scala.Float, touchOffsetY: scala.Float): scala.Unit = {
    this.touchOffsetX = touchOffsetX
    this.touchOffsetY = touchOffsetY
  }
  def isDragging(): scala.Boolean = {
    return this.payload != null
  }
  def getDragActor(): com.badlogic.gdx.scenes.scene2d.Actor = {
    return this.dragActor
  }
  def getDragPayload(): com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Payload = {
    return this.payload
  }
  def getDragSource(): com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Source = {
    return this.dragSource
  }
  def setDragTime(dragMillis: scala.Int): scala.Unit = {
    this.dragTime = dragMillis
  }
  def getDragTime(): scala.Int = {
    return this.dragTime
  }
  def isDragValid(): scala.Boolean = {
    return (this.payload != null) && (java.lang.System.currentTimeMillis() >= this.dragValidTime)
  }
  def setCancelTouchFocus(cancelTouchFocus: scala.Boolean): scala.Unit = {
    this.cancelTouchFocus = cancelTouchFocus
  }
  def setKeepWithinStage(keepWithinStage: scala.Boolean): scala.Unit = {
    this.keepWithinStage = keepWithinStage
  }
}
object DragAndDrop {
  final val tmpVector: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  abstract class Source(actor$p: com.badlogic.gdx.scenes.scene2d.Actor) {
    var actor: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
    if (actor$p == null) {
      throw new java.lang.IllegalArgumentException("actor cannot be null.")
    } else ()
    this.actor = actor$p
    def dragStart(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int): com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Payload
    def drag(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int): scala.Unit = {
      ()
    }
    def dragStop(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, payload: com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Payload, target: com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Target): scala.Unit = {
      ()
    }
    def getActor(): com.badlogic.gdx.scenes.scene2d.Actor = {
      return this.actor
    }
  }
  abstract class Target(actor$p: com.badlogic.gdx.scenes.scene2d.Actor) {
    var actor: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
    val stage: com.badlogic.gdx.scenes.scene2d.Stage = actor$p.getStage()
    if (actor$p == null) {
      throw new java.lang.IllegalArgumentException("actor cannot be null.")
    } else ()
    this.actor = actor$p
    if ((stage != null) && (actor$p == stage.getRoot())) {
      throw new java.lang.IllegalArgumentException("The stage root cannot be a drag and drop target.")
    } else ()
    def drag(source: com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Source, payload: com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Payload, x: scala.Float, y: scala.Float, pointer: scala.Int): scala.Boolean
    def reset(source: com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Source, payload: com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Payload): scala.Unit = {
      ()
    }
    def drop(source: com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Source, payload: com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Payload, x: scala.Float, y: scala.Float, pointer: scala.Int): scala.Unit
    def getActor(): com.badlogic.gdx.scenes.scene2d.Actor = {
      return this.actor
    }
  }
  class Payload {
    var dragActor: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
    var validDragActor: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
    var invalidDragActor: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
    var `object`: java.lang.Object = null.asInstanceOf[java.lang.Object]
    def setDragActor(dragActor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
      this.dragActor = dragActor
    }
    def getDragActor(): com.badlogic.gdx.scenes.scene2d.Actor = {
      return this.dragActor
    }
    def setValidDragActor(validDragActor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
      this.validDragActor = validDragActor
    }
    def getValidDragActor(): com.badlogic.gdx.scenes.scene2d.Actor = {
      return this.validDragActor
    }
    def setInvalidDragActor(invalidDragActor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
      this.invalidDragActor = invalidDragActor
    }
    def getInvalidDragActor(): com.badlogic.gdx.scenes.scene2d.Actor = {
      return this.invalidDragActor
    }
    def getObject(): java.lang.Object = {
      return this.`object`
    }
    def setObject(`object`: java.lang.Object): scala.Unit = {
      this.`object` = `object`
    }
  }
}