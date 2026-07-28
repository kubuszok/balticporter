package com.badlogic.gdx.scenes.scene2d

class InputEvent extends com.badlogic.gdx.scenes.scene2d.Event {
  private var `type`: com.badlogic.gdx.scenes.scene2d.InputEvent.Type = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.InputEvent.Type]
  private var stageX: scala.Float = 0.0f
  private var stageY: scala.Float = 0.0f
  private var scrollAmountX: scala.Float = 0.0f
  private var scrollAmountY: scala.Float = 0.0f
  private var pointer: scala.Int = 0
  private var button: scala.Int = 0
  private var keyCode: scala.Int = 0
  private var character: scala.Char = '\u0000'
  private var relatedActor: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
  private var touchFocus: scala.Boolean = true
  def reset(): scala.Unit = {
    super.reset()
    this.relatedActor = null
    this.button = -1
  }
  def getStageX(): scala.Float = {
    return this.stageX
  }
  def setStageX(stageX: scala.Float): scala.Unit = {
    this.stageX = stageX
  }
  def getStageY(): scala.Float = {
    return this.stageY
  }
  def setStageY(stageY: scala.Float): scala.Unit = {
    this.stageY = stageY
  }
  def getType(): com.badlogic.gdx.scenes.scene2d.InputEvent.Type = {
    return this.`type`
  }
  def setType(`type`: com.badlogic.gdx.scenes.scene2d.InputEvent.Type): scala.Unit = {
    this.`type` = `type`
  }
  def getPointer(): scala.Int = {
    return this.pointer
  }
  def setPointer(pointer: scala.Int): scala.Unit = {
    this.pointer = pointer
  }
  def getButton(): scala.Int = {
    return this.button
  }
  def setButton(button: scala.Int): scala.Unit = {
    this.button = button
  }
  def getKeyCode(): scala.Int = {
    return this.keyCode
  }
  def setKeyCode(keyCode: scala.Int): scala.Unit = {
    this.keyCode = keyCode
  }
  def getCharacter(): scala.Char = {
    return this.character
  }
  def setCharacter(character: scala.Char): scala.Unit = {
    this.character = character
  }
  def getScrollAmountX(): scala.Float = {
    return this.scrollAmountX
  }
  def getScrollAmountY(): scala.Float = {
    return this.scrollAmountY
  }
  def setScrollAmountX(scrollAmount: scala.Float): scala.Unit = {
    this.scrollAmountX = scrollAmount
  }
  def setScrollAmountY(scrollAmount: scala.Float): scala.Unit = {
    this.scrollAmountY = scrollAmount
  }
  @com.badlogic.gdx.utils.Null
  def getRelatedActor(): com.badlogic.gdx.scenes.scene2d.Actor = {
    return this.relatedActor
  }
  def setRelatedActor(relatedActor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    this.relatedActor = relatedActor
  }
  def toCoordinates(actor: com.badlogic.gdx.scenes.scene2d.Actor, actorCoords: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.math.Vector2 = {
    actorCoords.set(this.stageX, this.stageY)
    actor.stageToLocalCoordinates(actorCoords)
    return actorCoords
  }
  def isTouchFocusCancel(): scala.Boolean = {
    return (this.stageX == java.lang.Integer.MIN_VALUE) || (this.stageY == java.lang.Integer.MIN_VALUE)
  }
  def getTouchFocus(): scala.Boolean = {
    return this.touchFocus
  }
  def setTouchFocus(touchFocus: scala.Boolean): scala.Unit = {
    this.touchFocus = touchFocus
  }
  def toString(): java.lang.String = {
    return this.`type`.toString()
  }
}
object InputEvent {
  sealed abstract class Type {
    def name(): java.lang.String = this.toString()
  }
  object Type {
    case object touchDown extends Type
    case object touchUp extends Type
    case object touchDragged extends Type
    case object mouseMoved extends Type
    case object enter extends Type
    case object exit extends Type
    case object scrolled extends Type
    case object keyDown extends Type
    case object keyUp extends Type
    case object keyTyped extends Type
    def values(): scala.Array[Type] = scala.Array(touchDown, touchUp, touchDragged, mouseMoved, enter, exit, scrolled, keyDown, keyUp, keyTyped)
    def valueOf(name: java.lang.String): Type = name match {
      case "touchDown" => touchDown
      case "touchUp" => touchUp
      case "touchDragged" => touchDragged
      case "mouseMoved" => mouseMoved
      case "enter" => enter
      case "exit" => exit
      case "scrolled" => scrolled
      case "keyDown" => keyDown
      case "keyUp" => keyUp
      case "keyTyped" => keyTyped
      case _ => throw new java.lang.IllegalArgumentException(name)
    }
  }
}