package com.badlogic.gdx.scenes.scene2d.ui

class Tooltip[T <: com.badlogic.gdx.scenes.scene2d.Actor] extends com.badlogic.gdx.scenes.scene2d.InputListener {
  private var manager: com.badlogic.gdx.scenes.scene2d.ui.TooltipManager = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.TooltipManager]
  var container: com.badlogic.gdx.scenes.scene2d.ui.Container[T] = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Container[T]]
  var instant: scala.Boolean = false
  var always: scala.Boolean = false
  var touchIndependent: scala.Boolean = false
  var targetActor: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
  def this(contents: T, manager: com.badlogic.gdx.scenes.scene2d.ui.TooltipManager) = {
    this()
    this.manager = manager
    this.container = new com.badlogic.gdx.scenes.scene2d.ui.Container(contents)
    this.container.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled)
  }
  def this(contents: T) = {
    this(contents, com.badlogic.gdx.scenes.scene2d.ui.TooltipManager.getInstance())
  }
  def getManager(): com.badlogic.gdx.scenes.scene2d.ui.TooltipManager = {
    return this.manager
  }
  def getContainer(): com.badlogic.gdx.scenes.scene2d.ui.Container[T] = {
    return this.container
  }
  def setActor(contents: T): scala.Unit = {
    this.container.setActor(contents)
  }
  def getActor(): T = {
    return this.container.getActor()
  }
  def setInstant(instant: scala.Boolean): scala.Unit = {
    this.instant = instant
  }
  def setAlways(always: scala.Boolean): scala.Unit = {
    this.always = always
  }
  def setTouchIndependent(touchIndependent: scala.Boolean): scala.Unit = {
    this.touchIndependent = touchIndependent
  }
  def touchDown(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, button: scala.Int): scala.Boolean = {
    if (this.instant) {
      this.container.toFront()
      return false
    } else ()
    this.manager.touchDown(this)
    return false
  }
  def mouseMoved(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float): scala.Boolean = {
    if (this.container.hasParent()) {
      return false
    } else ()
    this.setContainerPosition(event.getListenerActor(), x, y)
    return true
  }
  private def setContainerPosition(actor: com.badlogic.gdx.scenes.scene2d.Actor, x: scala.Float, y: scala.Float): scala.Unit = {
    this.targetActor = actor
    val stage: com.badlogic.gdx.scenes.scene2d.Stage = actor.getStage()
    if (stage == null) {
      return
    } else ()
    this.container.setSize(this.manager.maxWidth, java.lang.Integer.MAX_VALUE)
    this.container.validate()
    this.container.width(this.container.getActor().getWidth())
    this.container.pack()
    val offsetX: scala.Float = this.manager.offsetX
    val offsetY: scala.Float = this.manager.offsetY
    val dist: scala.Float = this.manager.edgeDistance
    var point: com.badlogic.gdx.math.Vector2 = actor.localToStageCoordinates(Tooltip.tmp.set(x + offsetX, (y - offsetY) - this.container.getHeight()))
    if (point.y < dist) {
      point = actor.localToStageCoordinates(Tooltip.tmp.set(x + offsetX, y + offsetY))
    } else ()
    if (point.x < dist) {
      point.x = dist
    } else ()
    if ((point.x + this.container.getWidth()) > (stage.getWidth() - dist)) {
      point.x = (stage.getWidth() - dist) - this.container.getWidth()
    } else ()
    if ((point.y + this.container.getHeight()) > (stage.getHeight() - dist)) {
      point.y = (stage.getHeight() - dist) - this.container.getHeight()
    } else ()
    this.container.setPosition(point.x, point.y)
    point = actor.localToStageCoordinates(Tooltip.tmp.set(actor.getWidth() / 2, actor.getHeight() / 2))
    point.sub(this.container.getX(), this.container.getY())
    this.container.setOrigin(point.x, point.y)
  }
  def enter(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, fromActor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    if (pointer != (-1)) {
      return
    } else ()
    if (this.touchIndependent && com.badlogic.gdx.Gdx.input.isTouched()) {
      return
    } else ()
    val actor: com.badlogic.gdx.scenes.scene2d.Actor = event.getListenerActor()
    if ((fromActor != null) && fromActor.isDescendantOf(actor)) {
      return
    } else ()
    this.setContainerPosition(actor, x, y)
    this.manager.enter(this)
  }
  def exit(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, toActor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    if ((toActor != null) && toActor.isDescendantOf(event.getListenerActor())) {
      return
    } else ()
    this.hide()
  }
  def hide(): scala.Unit = {
    this.manager.hide(this)
  }
}
object Tooltip {
  export com.badlogic.gdx.scenes.scene2d.InputListener.{tmp => _, *}
  var tmp: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
}