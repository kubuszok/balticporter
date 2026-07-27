package com.badlogic.gdx.scenes.scene2d.ui

class Button extends com.badlogic.gdx.scenes.scene2d.ui.Table with com.badlogic.gdx.scenes.scene2d.utils.Disableable with com.badlogic.gdx.scenes.scene2d.ui.Styleable[com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle] {
  private var style: com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle]
  var isChecked$field: scala.Boolean = false
  var isDisabled$field: scala.Boolean = false
  var buttonGroup: com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup[?] = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup[?]]
  private var clickListener: com.badlogic.gdx.scenes.scene2d.utils.ClickListener = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.ClickListener]
  private var programmaticChangeEvents: scala.Boolean = true
  def this(skin: com.badlogic.gdx.scenes.scene2d.ui.Skin) = {
    this()
    this.initialize()
    this.setStyle(skin.get(classOf[com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle]))
    this.setSize(this.getPrefWidth(), this.getPrefHeight())
  }
  def this(skin: com.badlogic.gdx.scenes.scene2d.ui.Skin, styleName: java.lang.String) = {
    this()
    this.initialize()
    this.setStyle(skin.get(styleName, classOf[com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle]))
    this.setSize(this.getPrefWidth(), this.getPrefHeight())
  }
  def this(child: com.badlogic.gdx.scenes.scene2d.Actor, style: com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle) = {
    this()
    this.initialize()
    this.add(child)
    this.setStyle(style)
    this.setSize(this.getPrefWidth(), this.getPrefHeight())
  }
  def this(child: com.badlogic.gdx.scenes.scene2d.Actor, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin, styleName: java.lang.String) = {
    this(child, skin.get(styleName, classOf[com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle]))
    this.setSkin(skin)
  }
  def this(style: com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle) = {
    this()
    this.initialize()
    this.setStyle(style)
    this.setSize(this.getPrefWidth(), this.getPrefHeight())
  }
  def this(up: com.badlogic.gdx.scenes.scene2d.utils.Drawable) = {
    this(new com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle(up, null, null))
  }
  def this(up: com.badlogic.gdx.scenes.scene2d.utils.Drawable, down: com.badlogic.gdx.scenes.scene2d.utils.Drawable) = {
    this(new com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle(up, down, null))
  }
  def this(up: com.badlogic.gdx.scenes.scene2d.utils.Drawable, down: com.badlogic.gdx.scenes.scene2d.utils.Drawable, checked: com.badlogic.gdx.scenes.scene2d.utils.Drawable) = {
    this(new com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle(up, down, checked))
  }
  def this(child: com.badlogic.gdx.scenes.scene2d.Actor, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin) = {
    this(child, skin.get(classOf[com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle]))
  }
  this.initialize()
  private def initialize(): scala.Unit = {
    this.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled)
    this.addListener({
      this.clickListener = new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
        override def clicked(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float): scala.Unit = {
          if (Button.this.isDisabled()) {
            return
          } else ()
          Button.this.setChecked(!Button.this.isChecked$field, true)
        }
      }
      this.clickListener
    })
  }
  def setChecked(isChecked: scala.Boolean): scala.Unit = {
    this.setChecked(isChecked, this.programmaticChangeEvents)
  }
  def setChecked(isChecked: scala.Boolean, fireEvent: scala.Boolean): scala.Unit = {
    if (this.isChecked$field == isChecked) {
      return
    } else ()
    if ((this.buttonGroup != null) && (!this.buttonGroup.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup[Button]].canCheck(this.asInstanceOf[Button], isChecked))) {
      return
    } else ()
    this.isChecked$field = isChecked
    if (fireEvent) {
      val changeEvent: com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent = com.badlogic.gdx.scenes.scene2d.Actor.POOLS.obtain(classOf[com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent])
      if (this.fire(changeEvent)) {
        this.isChecked$field = !isChecked
      } else ()
      com.badlogic.gdx.scenes.scene2d.Actor.POOLS.free(changeEvent)
    } else ()
  }
  def toggle(): scala.Unit = {
    this.setChecked(!this.isChecked$field)
  }
  def isChecked(): scala.Boolean = {
    return this.isChecked$field
  }
  def isPressed(): scala.Boolean = {
    return this.clickListener.isVisualPressed()
  }
  def isOver(): scala.Boolean = {
    return this.clickListener.isOver()
  }
  def getClickListener(): com.badlogic.gdx.scenes.scene2d.utils.ClickListener = {
    return this.clickListener
  }
  def isDisabled(): scala.Boolean = {
    return this.isDisabled$field
  }
  def setDisabled(isDisabled: scala.Boolean): scala.Unit = {
    this.isDisabled$field = isDisabled
  }
  def setProgrammaticChangeEvents(programmaticChangeEvents: scala.Boolean): scala.Unit = {
    this.programmaticChangeEvents = programmaticChangeEvents
  }
  def getProgrammaticChangeEvents(): scala.Boolean = {
    return this.programmaticChangeEvents
  }
  def setStyle(style: com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle): scala.Unit = {
    if (style == null) {
      throw new java.lang.IllegalArgumentException("style cannot be null.")
    } else ()
    this.style = style
    this.setBackground(this.getBackgroundDrawable())
  }
  def getStyle(): com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle = {
    return this.style
  }
  def getButtonGroup(): com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup[?] = {
    return this.buttonGroup.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup[?]]
  }
  def getBackgroundDrawable(): com.badlogic.gdx.scenes.scene2d.utils.Drawable = {
    if (this.isDisabled() && (this.style.disabled != null)) {
      return this.style.disabled
    } else ()
    if (this.isPressed()) {
      if (this.isChecked() && (this.style.checkedDown != null)) {
        return this.style.checkedDown
      } else ()
      if (this.style.down != null) {
        return this.style.down
      } else ()
    } else ()
    if (this.isOver()) {
      if (this.isChecked()) {
        if (this.style.checkedOver != null) {
          return this.style.checkedOver
        } else ()
      } else {
        if (this.style.over != null) {
          return this.style.over
        } else ()
      }
    } else ()
    val focused: scala.Boolean = this.hasKeyboardFocus()
    if (this.isChecked()) {
      if (focused && (this.style.checkedFocused != null)) {
        return this.style.checkedFocused
      } else ()
      if (this.style.checked != null) {
        return this.style.checked
      } else ()
      if (this.isOver() && (this.style.over != null)) {
        return this.style.over
      } else ()
    } else ()
    if (focused && (this.style.focused != null)) {
      return this.style.focused
    } else ()
    return this.style.up
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float): scala.Unit = {
    this.validate()
    this.setBackground(this.getBackgroundDrawable())
    var offsetX: scala.Float = 0
    var offsetY: scala.Float = 0
    if (this.isPressed() && (!this.isDisabled())) {
      offsetX = this.style.pressedOffsetX
      offsetY = this.style.pressedOffsetY
    } else {
      if (this.isChecked() && (!this.isDisabled())) {
        offsetX = this.style.checkedOffsetX
        offsetY = this.style.checkedOffsetY
      } else {
        offsetX = this.style.unpressedOffsetX
        offsetY = this.style.unpressedOffsetY
      }
    }
    val offset: scala.Boolean = (offsetX != 0) || (offsetY != 0)
    val children: com.badlogic.gdx.utils.Array[com.badlogic.gdx.scenes.scene2d.Actor] = this.getChildren()
    if (offset) {
      { var i: scala.Int = 0; while (i < children.size) { {
        children.get(i).moveBy(offsetX, offsetY)
      }; i = i + 1 } }
    } else ()
    super.draw(batch, parentAlpha)
    if (offset) {
      { var i: scala.Int = 0; while (i < children.size) { {
        children.get(i).moveBy(-offsetX, -offsetY)
      }; i = i + 1 } }
    } else ()
    val stage: com.badlogic.gdx.scenes.scene2d.Stage = this.getStage()
    if (((stage != null) && stage.getActionsRequestRendering()) && (this.isPressed() != this.clickListener.isPressed())) {
      com.badlogic.gdx.Gdx.graphics.requestRendering()
    } else ()
  }
  def getPrefWidth(): scala.Float = {
    var width: scala.Float = super.getPrefWidth()
    if (this.style.up != null) {
      width = java.lang.Math.max(width, this.style.up.getMinWidth())
    } else ()
    if (this.style.down != null) {
      width = java.lang.Math.max(width, this.style.down.getMinWidth())
    } else ()
    if (this.style.checked != null) {
      width = java.lang.Math.max(width, this.style.checked.getMinWidth())
    } else ()
    return width
  }
  def getPrefHeight(): scala.Float = {
    var height: scala.Float = super.getPrefHeight()
    if (this.style.up != null) {
      height = java.lang.Math.max(height, this.style.up.getMinHeight())
    } else ()
    if (this.style.down != null) {
      height = java.lang.Math.max(height, this.style.down.getMinHeight())
    } else ()
    if (this.style.checked != null) {
      height = java.lang.Math.max(height, this.style.checked.getMinHeight())
    } else ()
    return height
  }
  def getMinWidth(): scala.Float = {
    return this.getPrefWidth()
  }
  def getMinHeight(): scala.Float = {
    return this.getPrefHeight()
  }
}
object Button {
  export com.badlogic.gdx.scenes.scene2d.ui.Table.{ButtonStyle => _, *}
  class ButtonStyle {
    var up: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var down: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var over: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var focused: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var disabled: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var checked: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var checkedOver: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var checkedDown: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var checkedFocused: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var pressedOffsetX: scala.Float = 0.0f
    var pressedOffsetY: scala.Float = 0.0f
    var unpressedOffsetX: scala.Float = 0.0f
    var unpressedOffsetY: scala.Float = 0.0f
    var checkedOffsetX: scala.Float = 0.0f
    var checkedOffsetY: scala.Float = 0.0f
    def this(up: com.badlogic.gdx.scenes.scene2d.utils.Drawable, down: com.badlogic.gdx.scenes.scene2d.utils.Drawable, checked: com.badlogic.gdx.scenes.scene2d.utils.Drawable) = {
      this()
      this.up = up
      this.down = down
      this.checked = checked
    }
    def this(style: com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle) = {
      this()
      this.up = style.up
      this.down = style.down
      this.over = style.over
      this.focused = style.focused
      this.disabled = style.disabled
      this.checked = style.checked
      this.checkedOver = style.checkedOver
      this.checkedDown = style.checkedDown
      this.checkedFocused = style.checkedFocused
      this.pressedOffsetX = style.pressedOffsetX
      this.pressedOffsetY = style.pressedOffsetY
      this.unpressedOffsetX = style.unpressedOffsetX
      this.unpressedOffsetY = style.unpressedOffsetY
      this.checkedOffsetX = style.checkedOffsetX
      this.checkedOffsetY = style.checkedOffsetY
    }
  }
}