package com.badlogic.gdx.scenes.scene2d

class InputListener extends com.badlogic.gdx.scenes.scene2d.EventListener {
  override def handle(e: com.badlogic.gdx.scenes.scene2d.Event): scala.Boolean = {
    if (!e.isInstanceOf[com.badlogic.gdx.scenes.scene2d.InputEvent]) {
      return false
    } else ()
    val event: com.badlogic.gdx.scenes.scene2d.InputEvent = e.asInstanceOf[com.badlogic.gdx.scenes.scene2d.InputEvent]
    event.getType() match {
      case com.badlogic.gdx.scenes.scene2d.InputEvent.Type.keyDown => {
        return this.keyDown(event, event.getKeyCode())
      }
      case com.badlogic.gdx.scenes.scene2d.InputEvent.Type.keyUp => {
        return this.keyUp(event, event.getKeyCode())
      }
      case com.badlogic.gdx.scenes.scene2d.InputEvent.Type.keyTyped => {
        return this.keyTyped(event, event.getCharacter())
      }
    }
    event.toCoordinates(event.getListenerActor(), InputListener.tmpCoords)
    event.getType() match {
      case com.badlogic.gdx.scenes.scene2d.InputEvent.Type.touchDown => {
        val handled: scala.Boolean = this.touchDown(event, InputListener.tmpCoords.x, InputListener.tmpCoords.y, event.getPointer(), event.getButton())
        if (handled && event.getTouchFocus()) {
          event.getStage().addTouchFocus(this, event.getListenerActor(), event.getTarget(), event.getPointer(), event.getButton())
        } else ()
        return handled
      }
      case com.badlogic.gdx.scenes.scene2d.InputEvent.Type.touchUp => {
        this.touchUp(event, InputListener.tmpCoords.x, InputListener.tmpCoords.y, event.getPointer(), event.getButton())
        return true
      }
      case com.badlogic.gdx.scenes.scene2d.InputEvent.Type.touchDragged => {
        this.touchDragged(event, InputListener.tmpCoords.x, InputListener.tmpCoords.y, event.getPointer())
        return true
      }
      case com.badlogic.gdx.scenes.scene2d.InputEvent.Type.mouseMoved => {
        return this.mouseMoved(event, InputListener.tmpCoords.x, InputListener.tmpCoords.y)
      }
      case com.badlogic.gdx.scenes.scene2d.InputEvent.Type.scrolled => {
        return this.scrolled(event, InputListener.tmpCoords.x, InputListener.tmpCoords.y, event.getScrollAmountX(), event.getScrollAmountY())
      }
      case com.badlogic.gdx.scenes.scene2d.InputEvent.Type.enter => {
        this.enter(event, InputListener.tmpCoords.x, InputListener.tmpCoords.y, event.getPointer(), event.getRelatedActor())
        return false
      }
      case com.badlogic.gdx.scenes.scene2d.InputEvent.Type.exit => {
        this.exit(event, InputListener.tmpCoords.x, InputListener.tmpCoords.y, event.getPointer(), event.getRelatedActor())
        return false
      }
    }
    return false
  }
  def touchDown(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, button: scala.Int): scala.Boolean = {
    return false
  }
  def touchUp(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, button: scala.Int): scala.Unit = {
    ()
  }
  def touchDragged(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int): scala.Unit = {
    ()
  }
  def mouseMoved(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float): scala.Boolean = {
    return false
  }
  def enter(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, fromActor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    ()
  }
  def exit(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, toActor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    ()
  }
  def scrolled(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, amountX: scala.Float, amountY: scala.Float): scala.Boolean = {
    return false
  }
  def keyDown(event: com.badlogic.gdx.scenes.scene2d.InputEvent, keycode: scala.Int): scala.Boolean = {
    return false
  }
  def keyUp(event: com.badlogic.gdx.scenes.scene2d.InputEvent, keycode: scala.Int): scala.Boolean = {
    return false
  }
  def keyTyped(event: com.badlogic.gdx.scenes.scene2d.InputEvent, character: scala.Char): scala.Boolean = {
    return false
  }
}
object InputListener {
  private final val tmpCoords: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
}