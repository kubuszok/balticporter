package com.badlogic.gdx.scenes.scene2d.ui

class TextButton(text: java.lang.String, style$p: com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle) extends com.badlogic.gdx.scenes.scene2d.ui.Button {
  private var label: com.badlogic.gdx.scenes.scene2d.ui.Label = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Label]
  private var style: com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle]
  def this(text: java.lang.String, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin) = {
    this(text, skin.get(classOf[com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle]))
    this.setSkin(skin)
  }
  def this(text: java.lang.String, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin, styleName: java.lang.String) = {
    this(text, skin.get(styleName, classOf[com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle]))
    this.setSkin(skin)
  }
  this.setStyle(style$p)
  this.label = this.newLabel(text, new com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle(style$p.font, style$p.fontColor))
  this.label.setAlignment(com.badlogic.gdx.utils.Align.center)
  this.add(this.label).grow()
  this.setSize(this.getPrefWidth(), this.getPrefHeight())
  def newLabel(text: java.lang.String, style: com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle): com.badlogic.gdx.scenes.scene2d.ui.Label = {
    return new com.badlogic.gdx.scenes.scene2d.ui.Label(text, style)
  }
  override def setStyle(style: com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle): scala.Unit = {
    if (style == null) {
      throw new java.lang.NullPointerException("style cannot be null")
    } else ()
    if (!style.isInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle]) {
      throw new java.lang.IllegalArgumentException("style must be a TextButtonStyle.")
    } else ()
    this.style = style.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle]
    super.setStyle(style)
    if (this.label != null) {
      val textButtonStyle: com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle = style.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle]
      val labelStyle: com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle = this.label.getStyle()
      labelStyle.font = textButtonStyle.font
      labelStyle.fontColor = textButtonStyle.fontColor
      this.label.setStyle(labelStyle)
    } else ()
  }
  override def getStyle(): com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle = {
    return this.style
  }
  @com.badlogic.gdx.utils.Null
  def getFontColor(): com.badlogic.gdx.graphics.Color = {
    if (this.isDisabled() && (this.style.disabledFontColor != null)) {
      return this.style.disabledFontColor
    } else ()
    if (this.isPressed()) {
      if (this.isChecked() && (this.style.checkedDownFontColor != null)) {
        return this.style.checkedDownFontColor
      } else ()
      if (this.style.downFontColor != null) {
        return this.style.downFontColor
      } else ()
    } else ()
    if (this.isOver()) {
      if (this.isChecked()) {
        if (this.style.checkedOverFontColor != null) {
          return this.style.checkedOverFontColor
        } else ()
      } else {
        if (this.style.overFontColor != null) {
          return this.style.overFontColor
        } else ()
      }
    } else ()
    val focused: scala.Boolean = this.hasKeyboardFocus()
    if (this.isChecked()) {
      if (focused && (this.style.checkedFocusedFontColor != null)) {
        return this.style.checkedFocusedFontColor
      } else ()
      if (this.style.checkedFontColor != null) {
        return this.style.checkedFontColor
      } else ()
      if (this.isOver() && (this.style.overFontColor != null)) {
        return this.style.overFontColor
      } else ()
    } else ()
    if (focused && (this.style.focusedFontColor != null)) {
      return this.style.focusedFontColor
    } else ()
    return this.style.fontColor
  }
  override def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float): scala.Unit = {
    this.label.getStyle().fontColor = this.getFontColor()
    super.draw(batch, parentAlpha)
  }
  def setLabel(label: com.badlogic.gdx.scenes.scene2d.ui.Label): scala.Unit = {
    if (label == null) {
      throw new java.lang.IllegalArgumentException("label cannot be null.")
    } else ()
    this.getLabelCell().setActor(label)
    this.label = label
  }
  def getLabel(): com.badlogic.gdx.scenes.scene2d.ui.Label = {
    return this.label
  }
  def getLabelCell(): com.badlogic.gdx.scenes.scene2d.ui.Cell[com.badlogic.gdx.scenes.scene2d.ui.Label] = {
    return this.getCell(this.label)
  }
  def setText(text: java.lang.String): scala.Unit = {
    this.label.setText(text)
  }
  def getText(): java.lang.CharSequence = {
    return this.label.getText()
  }
  override def toString(): java.lang.String = {
    val name: java.lang.String = this.getName()
    if (name != null) {
      return name
    } else ()
    var className: java.lang.String = this.getClass().getName()
    val dotIndex: scala.Int = className.lastIndexOf('.')
    if (dotIndex != (-1)) {
      className = className.substring(dotIndex + 1)
    } else ()
    return (((if (className.indexOf('$') != (-1)) "TextButton " else "") + className) + ": ") + this.label.getText()
  }
}
object TextButton {
  export com.badlogic.gdx.scenes.scene2d.ui.Button.{TextButtonStyle => _, *}
  class TextButtonStyle extends com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle {
    var font: com.badlogic.gdx.graphics.g2d.BitmapFont = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.BitmapFont]
    var fontColor: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
    var downFontColor: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
    var overFontColor: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
    var focusedFontColor: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
    var disabledFontColor: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
    var checkedFontColor: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
    var checkedDownFontColor: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
    var checkedOverFontColor: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
    var checkedFocusedFontColor: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
    def this(up: com.badlogic.gdx.scenes.scene2d.utils.Drawable, down: com.badlogic.gdx.scenes.scene2d.utils.Drawable, checked: com.badlogic.gdx.scenes.scene2d.utils.Drawable, font: com.badlogic.gdx.graphics.g2d.BitmapFont) = {
      this()
      this.up = up
      this.down = down
      this.checked = checked
      this.font = font
    }
    def this(style: com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle) = {
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
      this.font = style.font
      if (style.fontColor != null) {
        this.fontColor = new com.badlogic.gdx.graphics.Color(style.fontColor)
      } else ()
      if (style.downFontColor != null) {
        this.downFontColor = new com.badlogic.gdx.graphics.Color(style.downFontColor)
      } else ()
      if (style.overFontColor != null) {
        this.overFontColor = new com.badlogic.gdx.graphics.Color(style.overFontColor)
      } else ()
      if (style.focusedFontColor != null) {
        this.focusedFontColor = new com.badlogic.gdx.graphics.Color(style.focusedFontColor)
      } else ()
      if (style.disabledFontColor != null) {
        this.disabledFontColor = new com.badlogic.gdx.graphics.Color(style.disabledFontColor)
      } else ()
      if (style.checkedFontColor != null) {
        this.checkedFontColor = new com.badlogic.gdx.graphics.Color(style.checkedFontColor)
      } else ()
      if (style.checkedDownFontColor != null) {
        this.checkedDownFontColor = new com.badlogic.gdx.graphics.Color(style.checkedDownFontColor)
      } else ()
      if (style.checkedOverFontColor != null) {
        this.checkedOverFontColor = new com.badlogic.gdx.graphics.Color(style.checkedOverFontColor)
      } else ()
      if (style.checkedFocusedFontColor != null) {
        this.checkedFocusedFontColor = new com.badlogic.gdx.graphics.Color(style.checkedFocusedFontColor)
      } else ()
    }
  }
}