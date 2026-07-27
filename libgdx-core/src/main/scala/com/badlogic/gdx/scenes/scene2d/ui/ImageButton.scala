package com.badlogic.gdx.scenes.scene2d.ui

class ImageButton extends com.badlogic.gdx.scenes.scene2d.ui.Button {
  private var image: com.badlogic.gdx.scenes.scene2d.ui.Image = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Image]
  private var style: com.badlogic.gdx.scenes.scene2d.ui.ImageButton.ImageButtonStyle = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.ImageButton.ImageButtonStyle]
  def this(style: com.badlogic.gdx.scenes.scene2d.ui.ImageButton.ImageButtonStyle) = {
    this()
    this.image = this.newImage()
    this.add(this.image)
    this.setStyle(style)
    this.setSize(this.getPrefWidth(), this.getPrefHeight())
  }
  def this(skin: com.badlogic.gdx.scenes.scene2d.ui.Skin) = {
    this(skin.get(classOf[com.badlogic.gdx.scenes.scene2d.ui.ImageButton.ImageButtonStyle]))
    this.setSkin(skin)
  }
  def this(skin: com.badlogic.gdx.scenes.scene2d.ui.Skin, styleName: java.lang.String) = {
    this(skin.get(styleName, classOf[com.badlogic.gdx.scenes.scene2d.ui.ImageButton.ImageButtonStyle]))
    this.setSkin(skin)
  }
  def this(imageUp: com.badlogic.gdx.scenes.scene2d.utils.Drawable) = {
    this(new com.badlogic.gdx.scenes.scene2d.ui.ImageButton.ImageButtonStyle(null, null, null, imageUp, null, null))
  }
  def this(imageUp: com.badlogic.gdx.scenes.scene2d.utils.Drawable, imageDown: com.badlogic.gdx.scenes.scene2d.utils.Drawable) = {
    this(new com.badlogic.gdx.scenes.scene2d.ui.ImageButton.ImageButtonStyle(null, null, null, imageUp, imageDown, null))
  }
  def this(imageUp: com.badlogic.gdx.scenes.scene2d.utils.Drawable, imageDown: com.badlogic.gdx.scenes.scene2d.utils.Drawable, imageChecked: com.badlogic.gdx.scenes.scene2d.utils.Drawable) = {
    this(new com.badlogic.gdx.scenes.scene2d.ui.ImageButton.ImageButtonStyle(null, null, null, imageUp, imageDown, imageChecked))
  }
  def newImage(): com.badlogic.gdx.scenes.scene2d.ui.Image = {
    return new com.badlogic.gdx.scenes.scene2d.ui.Image(null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable], com.badlogic.gdx.utils.Scaling.fit)
  }
  def setStyle(style: com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle): scala.Unit = {
    if (!style.isInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.ImageButton.ImageButtonStyle]) {
      throw new java.lang.IllegalArgumentException("style must be an ImageButtonStyle.")
    } else ()
    this.style = style.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.ImageButton.ImageButtonStyle]
    super.setStyle(style)
    if (this.image != null) {
      this.updateImage()
    } else ()
  }
  def getStyle(): com.badlogic.gdx.scenes.scene2d.ui.ImageButton.ImageButtonStyle = {
    return this.style
  }
  def getImageDrawable(): com.badlogic.gdx.scenes.scene2d.utils.Drawable = {
    if (this.isDisabled() && (this.style.imageDisabled != null)) {
      return this.style.imageDisabled
    } else ()
    if (this.isPressed()) {
      if (this.isChecked() && (this.style.imageCheckedDown != null)) {
        return this.style.imageCheckedDown
      } else ()
      if (this.style.imageDown != null) {
        return this.style.imageDown
      } else ()
    } else ()
    if (this.isOver()) {
      if (this.isChecked()) {
        if (this.style.imageCheckedOver != null) {
          return this.style.imageCheckedOver
        } else ()
      } else {
        if (this.style.imageOver != null) {
          return this.style.imageOver
        } else ()
      }
    } else ()
    if (this.isChecked()) {
      if (this.style.imageChecked != null) {
        return this.style.imageChecked
      } else ()
      if (this.isOver() && (this.style.imageOver != null)) {
        return this.style.imageOver
      } else ()
    } else ()
    return this.style.imageUp
  }
  def updateImage(): scala.Unit = {
    this.image.setDrawable(this.getImageDrawable())
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float): scala.Unit = {
    this.updateImage()
    super.draw(batch, parentAlpha)
  }
  def getImage(): com.badlogic.gdx.scenes.scene2d.ui.Image = {
    return this.image
  }
  def getImageCell(): com.badlogic.gdx.scenes.scene2d.ui.Cell[?] = {
    return this.getCell(this.image).asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
  }
  def toString(): java.lang.String = {
    val name: java.lang.String = this.getName()
    if (name != null) {
      return name
    } else ()
    var className: java.lang.String = this.getClass().getName()
    val dotIndex: scala.Int = className.lastIndexOf('.')
    if (dotIndex != (-1)) {
      className = className.substring(dotIndex + 1)
    } else ()
    return (((if (className.indexOf('$') != (-1)) "ImageButton " else "") + className) + ": ") + this.image.getDrawable()
  }
}
object ImageButton {
  export com.badlogic.gdx.scenes.scene2d.ui.Button.{ImageButtonStyle => _, *}
  class ImageButtonStyle extends com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle {
    var imageUp: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var imageDown: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var imageOver: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var imageDisabled: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var imageChecked: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var imageCheckedDown: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var imageCheckedOver: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    def this(up: com.badlogic.gdx.scenes.scene2d.utils.Drawable, down: com.badlogic.gdx.scenes.scene2d.utils.Drawable, checked: com.badlogic.gdx.scenes.scene2d.utils.Drawable, imageUp: com.badlogic.gdx.scenes.scene2d.utils.Drawable, imageDown: com.badlogic.gdx.scenes.scene2d.utils.Drawable, imageChecked: com.badlogic.gdx.scenes.scene2d.utils.Drawable) = {
      this()
      this.imageUp = imageUp
      this.imageDown = imageDown
      this.imageChecked = imageChecked
    }
    def this(style: com.badlogic.gdx.scenes.scene2d.ui.ImageButton.ImageButtonStyle) = {
      this()
      this.imageUp = style.imageUp
      this.imageDown = style.imageDown
      this.imageOver = style.imageOver
      this.imageDisabled = style.imageDisabled
      this.imageChecked = style.imageChecked
      this.imageCheckedDown = style.imageCheckedDown
      this.imageCheckedOver = style.imageCheckedOver
    }
    def this(style: com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle) = {
      this()
    }
  }
}