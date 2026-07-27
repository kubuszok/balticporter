package com.badlogic.gdx.scenes.scene2d.ui

class Dialog extends com.badlogic.gdx.scenes.scene2d.ui.Window {
  var contentTable: com.badlogic.gdx.scenes.scene2d.ui.Table = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Table]
  var buttonTable: com.badlogic.gdx.scenes.scene2d.ui.Table = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Table]
  private var skin: com.badlogic.gdx.scenes.scene2d.ui.Skin = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Skin]
  var values: com.badlogic.gdx.utils.ObjectMap[com.badlogic.gdx.scenes.scene2d.Actor, java.lang.Object] = new com.badlogic.gdx.utils.ObjectMap().asInstanceOf[com.badlogic.gdx.utils.ObjectMap[com.badlogic.gdx.scenes.scene2d.Actor, java.lang.Object]]
  var cancelHide: scala.Boolean = false
  var previousKeyboardFocus: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
  var previousScrollFocus: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
  var focusListener: com.badlogic.gdx.scenes.scene2d.utils.FocusListener = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.FocusListener]
  var ignoreTouchDown: com.badlogic.gdx.scenes.scene2d.InputListener = new com.badlogic.gdx.scenes.scene2d.InputListener()
  def this(title: java.lang.String, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin) = {
    this()
    this.setSkin(skin)
    this.skin = skin
    this.initialize()
  }
  def this(title: java.lang.String, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin, windowStyleName: java.lang.String) = {
    this()
    this.setSkin(skin)
    this.skin = skin
    this.initialize()
  }
  def this(title: java.lang.String, windowStyle: com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle) = {
    this()
    this.initialize()
  }
  private def initialize(): scala.Unit = {
    this.setModal(true)
    this.defaults().space(6)
    this.add({
      this.contentTable = new com.badlogic.gdx.scenes.scene2d.ui.Table(this.skin)
      this.contentTable
    }).grow()
    this.row()
    this.add({
      this.buttonTable = new com.badlogic.gdx.scenes.scene2d.ui.Table(this.skin)
      this.buttonTable
    }).fillX()
    this.contentTable.defaults().space(6)
    this.buttonTable.defaults().space(6)
    this.buttonTable.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener())
    this.focusListener = new com.badlogic.gdx.scenes.scene2d.utils.FocusListener()
  }
  def setStage(stage: com.badlogic.gdx.scenes.scene2d.Stage): scala.Unit = {
    if (stage == null) {
      this.addListener(this.focusListener)
    } else {
      this.removeListener(this.focusListener)
    }
    super.setStage(stage)
  }
  def getContentTable(): com.badlogic.gdx.scenes.scene2d.ui.Table = {
    return this.contentTable
  }
  def getButtonTable(): com.badlogic.gdx.scenes.scene2d.ui.Table = {
    return this.buttonTable
  }
  def text(text: java.lang.String): Dialog = {
    if (this.skin == null) {
      throw new java.lang.IllegalStateException("This method may only be used if the dialog was constructed with a Skin.")
    } else ()
    return this.text(text, this.skin.get(classOf[com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle]))
  }
  def text(text: java.lang.String, labelStyle: com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle): Dialog = {
    return this.text(new com.badlogic.gdx.scenes.scene2d.ui.Label(text, labelStyle))
  }
  def text(label: com.badlogic.gdx.scenes.scene2d.ui.Label): Dialog = {
    this.contentTable.add(label)
    return this
  }
  def button(text: java.lang.String): Dialog = {
    return this.button(text, null)
  }
  def button(text: java.lang.String, `object`: java.lang.Object): Dialog = {
    if (this.skin == null) {
      throw new java.lang.IllegalStateException("This method may only be used if the dialog was constructed with a Skin.")
    } else ()
    return this.button(text, `object`, this.skin.get(classOf[com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle]))
  }
  def button(text: java.lang.String, `object`: java.lang.Object, buttonStyle: com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle): Dialog = {
    return this.button(new com.badlogic.gdx.scenes.scene2d.ui.TextButton(text, buttonStyle), `object`)
  }
  def button(button: com.badlogic.gdx.scenes.scene2d.ui.Button): Dialog = {
    return this.button(button, null)
  }
  def button(button: com.badlogic.gdx.scenes.scene2d.ui.Button, `object`: java.lang.Object): Dialog = {
    this.buttonTable.add(button)
    this.setObject(button, `object`)
    return this
  }
  def show(stage: com.badlogic.gdx.scenes.scene2d.Stage, action: com.badlogic.gdx.scenes.scene2d.Action): Dialog = {
    this.clearActions()
    this.removeCaptureListener(this.ignoreTouchDown)
    this.previousKeyboardFocus = null
    var actor: com.badlogic.gdx.scenes.scene2d.Actor = stage.getKeyboardFocus()
    if ((actor != null) && (!actor.isDescendantOf(this))) {
      this.previousKeyboardFocus = actor
    } else ()
    this.previousScrollFocus = null
    actor = stage.getScrollFocus()
    if ((actor != null) && (!actor.isDescendantOf(this))) {
      this.previousScrollFocus = actor
    } else ()
    stage.addActor(this)
    this.pack()
    stage.cancelTouchFocus()
    stage.setKeyboardFocus(this)
    stage.setScrollFocus(this)
    if (action != null) {
      this.addAction(action)
    } else ()
    return this
  }
  def show(stage: com.badlogic.gdx.scenes.scene2d.Stage): Dialog = {
    this.show(stage, com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(com.badlogic.gdx.scenes.scene2d.actions.Actions.alpha(0), com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeIn(0.4f, com.badlogic.gdx.math.Interpolation.fade)))
    this.setPosition(java.lang.Math.round((stage.getWidth() - this.getWidth()) / 2), java.lang.Math.round((stage.getHeight() - this.getHeight()) / 2))
    return this
  }
  def hide(action: com.badlogic.gdx.scenes.scene2d.Action): scala.Unit = {
    val stage: com.badlogic.gdx.scenes.scene2d.Stage = this.getStage()
    if (stage != null) {
      this.removeListener(this.focusListener)
      if ((this.previousKeyboardFocus != null) && (this.previousKeyboardFocus.getStage() == null)) {
        this.previousKeyboardFocus = null
      } else ()
      var actor: com.badlogic.gdx.scenes.scene2d.Actor = stage.getKeyboardFocus()
      if ((actor == null) || actor.isDescendantOf(this)) {
        stage.setKeyboardFocus(this.previousKeyboardFocus)
      } else ()
      if ((this.previousScrollFocus != null) && (this.previousScrollFocus.getStage() == null)) {
        this.previousScrollFocus = null
      } else ()
      actor = stage.getScrollFocus()
      if ((actor == null) || actor.isDescendantOf(this)) {
        stage.setScrollFocus(this.previousScrollFocus)
      } else ()
    } else ()
    if (action != null) {
      this.addCaptureListener(this.ignoreTouchDown)
      this.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(action, com.badlogic.gdx.scenes.scene2d.actions.Actions.removeListener(this.ignoreTouchDown, true), com.badlogic.gdx.scenes.scene2d.actions.Actions.removeActor()))
    } else {
      this.remove()
    }
  }
  def hide(): scala.Unit = {
    this.hide(com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeOut(0.4f, com.badlogic.gdx.math.Interpolation.fade))
  }
  def setObject(actor: com.badlogic.gdx.scenes.scene2d.Actor, `object`: java.lang.Object): scala.Unit = {
    this.values.put(actor, `object`)
  }
  def key(keycode: scala.Int, `object`: java.lang.Object): Dialog = {
    this.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener())
    return this
  }
  def result(`object`: java.lang.Object): scala.Unit = {
    ()
  }
  def cancel(): scala.Unit = {
    this.cancelHide = true
  }
}
object Dialog {
  export com.badlogic.gdx.scenes.scene2d.ui.Window.*
}