package com.badlogic.gdx.scenes.scene2d.ui

class Image(drawable$p: com.badlogic.gdx.scenes.scene2d.utils.Drawable, scaling$p: com.badlogic.gdx.utils.Scaling, align$p: scala.Int) extends com.badlogic.gdx.scenes.scene2d.ui.Widget {
  private var scaling: com.badlogic.gdx.utils.Scaling = null.asInstanceOf[com.badlogic.gdx.utils.Scaling]
  private var align: scala.Int = com.badlogic.gdx.utils.Align.center
  private var imageX: scala.Float = 0.0f
  private var imageY: scala.Float = 0.0f
  private var imageWidth: scala.Float = 0.0f
  private var imageHeight: scala.Float = 0.0f
  private var drawable: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
  def this(drawable: com.badlogic.gdx.scenes.scene2d.utils.Drawable) = {
    this(drawable, com.badlogic.gdx.utils.Scaling.stretch, com.badlogic.gdx.utils.Align.center)
  }
  def this() = {
    this(null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable])
  }
  def this(patch: com.badlogic.gdx.graphics.g2d.NinePatch) = {
    this(new com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable(patch), com.badlogic.gdx.utils.Scaling.stretch, com.badlogic.gdx.utils.Align.center)
  }
  def this(region: com.badlogic.gdx.graphics.g2d.TextureRegion) = {
    this(new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(region), com.badlogic.gdx.utils.Scaling.stretch, com.badlogic.gdx.utils.Align.center)
  }
  def this(texture: com.badlogic.gdx.graphics.Texture) = {
    this(new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(new com.badlogic.gdx.graphics.g2d.TextureRegion(texture)))
  }
  def this(skin: com.badlogic.gdx.scenes.scene2d.ui.Skin, drawableName: java.lang.String) = {
    this(skin.getDrawable(drawableName), com.badlogic.gdx.utils.Scaling.stretch, com.badlogic.gdx.utils.Align.center)
  }
  def this(drawable: com.badlogic.gdx.scenes.scene2d.utils.Drawable, scaling: com.badlogic.gdx.utils.Scaling) = {
    this(drawable, scaling, com.badlogic.gdx.utils.Align.center)
  }
  this.setDrawable(drawable$p)
  this.scaling = scaling$p
  this.align = align$p
  this.setSize(this.getPrefWidth(), this.getPrefHeight())
  def layout(): scala.Unit = {
    if (this.drawable == null) {
      return
    } else ()
    val regionWidth: scala.Float = this.drawable.getMinWidth()
    val regionHeight: scala.Float = this.drawable.getMinHeight()
    val width: scala.Float = this.getWidth()
    val height: scala.Float = this.getHeight()
    val size: com.badlogic.gdx.math.Vector2 = this.scaling.apply(regionWidth, regionHeight, width, height)
    this.imageWidth = size.x
    this.imageHeight = size.y
    if ((this.align & com.badlogic.gdx.utils.Align.left) != 0) {
      this.imageX = 0
    } else {
      if ((this.align & com.badlogic.gdx.utils.Align.right) != 0) {
        this.imageX = (width - this.imageWidth).asInstanceOf[scala.Int]
      } else {
        this.imageX = ((width / 2) - (this.imageWidth / 2)).asInstanceOf[scala.Int]
      }
    }
    if ((this.align & com.badlogic.gdx.utils.Align.top) != 0) {
      this.imageY = (height - this.imageHeight).asInstanceOf[scala.Int]
    } else {
      if ((this.align & com.badlogic.gdx.utils.Align.bottom) != 0) {
        this.imageY = 0
      } else {
        this.imageY = ((height / 2) - (this.imageHeight / 2)).asInstanceOf[scala.Int]
      }
    }
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float): scala.Unit = {
    this.validate()
    val color: com.badlogic.gdx.graphics.Color = this.getColor()
    batch.setColor(color.r, color.g, color.b, color.a * parentAlpha)
    val x: scala.Float = this.getX()
    val y: scala.Float = this.getY()
    val scaleX: scala.Float = this.getScaleX()
    val scaleY: scala.Float = this.getScaleY()
    if (this.drawable.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.TransformDrawable]) {
      val rotation: scala.Float = this.getRotation()
      if (((scaleX != 1) || (scaleY != 1)) || (rotation != 0)) {
        this.drawable.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.TransformDrawable].draw(batch, x + this.imageX, y + this.imageY, this.getOriginX() - this.imageX, this.getOriginY() - this.imageY, this.imageWidth, this.imageHeight, scaleX, scaleY, rotation)
        return
      } else ()
    } else ()
    if (this.drawable != null) {
      this.drawable.draw(batch, x + this.imageX, y + this.imageY, this.imageWidth * scaleX, this.imageHeight * scaleY)
    } else ()
  }
  def setDrawable(skin: com.badlogic.gdx.scenes.scene2d.ui.Skin, drawableName: java.lang.String): scala.Unit = {
    this.setDrawable(skin.getDrawable(drawableName))
  }
  def setDrawable(drawable: com.badlogic.gdx.scenes.scene2d.utils.Drawable): scala.Unit = {
    if (this.drawable == drawable) {
      return
    } else ()
    if (drawable != null) {
      if ((this.getPrefWidth() != drawable.getMinWidth()) || (this.getPrefHeight() != drawable.getMinHeight())) {
        this.invalidateHierarchy()
      } else ()
    } else {
      this.invalidateHierarchy()
    }
    this.drawable = drawable
  }
  def getDrawable(): com.badlogic.gdx.scenes.scene2d.utils.Drawable = {
    return this.drawable
  }
  def setScaling(scaling: com.badlogic.gdx.utils.Scaling): scala.Unit = {
    if (scaling == null) {
      throw new java.lang.IllegalArgumentException("scaling cannot be null.")
    } else ()
    this.scaling = scaling
    this.invalidate()
  }
  def setAlign(align: scala.Int): scala.Unit = {
    this.align = align
    this.invalidate()
  }
  def getAlign(): scala.Int = {
    return this.align
  }
  def getMinWidth(): scala.Float = {
    return 0
  }
  def getMinHeight(): scala.Float = {
    return 0
  }
  def getPrefWidth(): scala.Float = {
    if (this.drawable != null) {
      return this.drawable.getMinWidth()
    } else ()
    return 0
  }
  def getPrefHeight(): scala.Float = {
    if (this.drawable != null) {
      return this.drawable.getMinHeight()
    } else ()
    return 0
  }
  def getImageX(): scala.Float = {
    return this.imageX
  }
  def getImageY(): scala.Float = {
    return this.imageY
  }
  def getImageWidth(): scala.Float = {
    return this.imageWidth
  }
  def getImageHeight(): scala.Float = {
    return this.imageHeight
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
    return (((if (className.indexOf('$') != (-1)) "Image " else "") + className) + ": ") + this.drawable
  }
}
object Image {
  export com.badlogic.gdx.scenes.scene2d.ui.Widget.*
}