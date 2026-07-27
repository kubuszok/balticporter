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
    val listener: com.badlogic.gdx.scenes.scene2d.utils.DragListener = new com.badlogic.gdx.scenes.scene2d.utils.DragListener()
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
  abstract class Source {
    var actor: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
    def this(actor: com.badlogic.gdx.scenes.scene2d.Actor) = {
      this()
      if (actor == null) {
        throw new java.lang.IllegalArgumentException("actor cannot be null.")
      } else ()
      this.actor = actor
    }
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
  abstract class Target {
    var actor: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
    def this(actor: com.badlogic.gdx.scenes.scene2d.Actor) = {
      this()
      if (actor == null) {
        throw new java.lang.IllegalArgumentException("actor cannot be null.")
      } else ()
      this.actor = actor
      val stage: com.badlogic.gdx.scenes.scene2d.Stage = actor.getStage()
      if ((stage != null) && (actor == stage.getRoot())) {
        throw new java.lang.IllegalArgumentException("The stage root cannot be a drag and drop target.")
      } else ()
    }
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