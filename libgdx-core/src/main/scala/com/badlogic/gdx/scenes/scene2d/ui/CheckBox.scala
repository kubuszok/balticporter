package com.badlogic.gdx.scenes.scene2d.ui

class CheckBox(text: java.lang.String, style$p: com.badlogic.gdx.scenes.scene2d.ui.CheckBox.CheckBoxStyle) extends com.badlogic.gdx.scenes.scene2d.ui.TextButton(text, style$p) {
  private var image: com.badlogic.gdx.scenes.scene2d.ui.Image = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Image]
  private var imageCell: com.badlogic.gdx.scenes.scene2d.ui.Cell[?] = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
  private var style: com.badlogic.gdx.scenes.scene2d.ui.CheckBox.CheckBoxStyle = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.CheckBox.CheckBoxStyle]
  val label$p: com.badlogic.gdx.scenes.scene2d.ui.Label = this.getLabel()
  def this(text: java.lang.String, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin) = {
    this(text, skin.get(classOf[com.badlogic.gdx.scenes.scene2d.ui.CheckBox.CheckBoxStyle]))
  }
  def this(text: java.lang.String, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin, styleName: java.lang.String) = {
    this(text, skin.get(styleName, classOf[com.badlogic.gdx.scenes.scene2d.ui.CheckBox.CheckBoxStyle]))
  }
  label$p.setAlignment(com.badlogic.gdx.utils.Align.left)
  this.image = this.newImage()
  this.image.setDrawable(style$p.checkboxOff)
  this.clearChildren()
  this.imageCell = this.add(this.image).asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
  this.add(label$p)
  this.setSize(this.getPrefWidth(), this.getPrefHeight())
  def newImage(): com.badlogic.gdx.scenes.scene2d.ui.Image = {
    return new com.badlogic.gdx.scenes.scene2d.ui.Image(null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable], com.badlogic.gdx.utils.Scaling.none)
  }
  def setStyle(style: com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle): scala.Unit = {
    if (!style.isInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.CheckBox.CheckBoxStyle]) {
      throw new java.lang.IllegalArgumentException("style must be a CheckBoxStyle.")
    } else ()
    this.style = style.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.CheckBox.CheckBoxStyle]
    super.setStyle(style)
  }
  def getStyle(): com.badlogic.gdx.scenes.scene2d.ui.CheckBox.CheckBoxStyle = {
    return this.style
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float): scala.Unit = {
    this.image.setDrawable(this.getImageDrawable())
    super.draw(batch, parentAlpha)
  }
  def getImageDrawable(): com.badlogic.gdx.scenes.scene2d.utils.Drawable = {
    if (this.isDisabled()) {
      if (isChecked$field && (this.style.checkboxOnDisabled != null)) {
        return this.style.checkboxOnDisabled
      } else ()
      return this.style.checkboxOffDisabled
    } else ()
    val over: scala.Boolean = this.isOver() && (!this.isDisabled())
    if (isChecked$field && (this.style.checkboxOn != null)) {
      return if (over && (this.style.checkboxOnOver != null)) this.style.checkboxOnOver else this.style.checkboxOn
    } else ()
    if (over && (this.style.checkboxOver != null)) {
      return this.style.checkboxOver
    } else ()
    return this.style.checkboxOff
  }
  def getImage(): com.badlogic.gdx.scenes.scene2d.ui.Image = {
    return this.image
  }
  def getImageCell(): com.badlogic.gdx.scenes.scene2d.ui.Cell[?] = {
    return this.imageCell.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
  }
}
object CheckBox {
  export com.badlogic.gdx.scenes.scene2d.ui.TextButton.{CheckBoxStyle => _, *}
  class CheckBoxStyle extends com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle {
    var checkboxOn: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var checkboxOff: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var checkboxOnOver: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var checkboxOver: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var checkboxOnDisabled: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var checkboxOffDisabled: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    def this(checkboxOff: com.badlogic.gdx.scenes.scene2d.utils.Drawable, checkboxOn: com.badlogic.gdx.scenes.scene2d.utils.Drawable, font: com.badlogic.gdx.graphics.g2d.BitmapFont, fontColor: com.badlogic.gdx.graphics.Color) = {
      this()
      this.checkboxOff = checkboxOff
      this.checkboxOn = checkboxOn
      this.font = font
      this.fontColor = fontColor
    }
    def this(style: com.badlogic.gdx.scenes.scene2d.ui.CheckBox.CheckBoxStyle) = {
      this()
      this.checkboxOff = style.checkboxOff
      this.checkboxOn = style.checkboxOn
      this.checkboxOnOver = style.checkboxOnOver
      this.checkboxOver = style.checkboxOver
      this.checkboxOnDisabled = style.checkboxOnDisabled
      this.checkboxOffDisabled = style.checkboxOffDisabled
    }
  }
}