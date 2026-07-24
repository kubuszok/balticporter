package com.badlogic.gdx.scenes.scene2d.utils

class NinePatchDrawable extends com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable with com.badlogic.gdx.scenes.scene2d.utils.TransformDrawable {
  private var patch: com.badlogic.gdx.graphics.g2d.NinePatch = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.NinePatch]
  def this(patch: com.badlogic.gdx.graphics.g2d.NinePatch) = {
    this()
    this.setPatch(patch)
  }
  def this(drawable: NinePatchDrawable) = {
    this()
    this.patch = drawable.patch
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    this.patch.draw(batch, x, y, width, height)
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, x: scala.Float, y: scala.Float, originX: scala.Float, originY: scala.Float, width: scala.Float, height: scala.Float, scaleX: scala.Float, scaleY: scala.Float, rotation: scala.Float): scala.Unit = {
    this.patch.draw(batch, x, y, originX, originY, width, height, scaleX, scaleY, rotation)
  }
  def setPatch(patch: com.badlogic.gdx.graphics.g2d.NinePatch): scala.Unit = {
    this.patch = patch
    if (patch != null) {
      this.setMinWidth(patch.getTotalWidth())
      this.setMinHeight(patch.getTotalHeight())
      this.setTopHeight(patch.getPadTop())
      this.setRightWidth(patch.getPadRight())
      this.setBottomHeight(patch.getPadBottom())
      this.setLeftWidth(patch.getPadLeft())
    } else ()
  }
  def getPatch(): com.badlogic.gdx.graphics.g2d.NinePatch = {
    return this.patch
  }
  def tint(tint: com.badlogic.gdx.graphics.Color): NinePatchDrawable = {
    val drawable: NinePatchDrawable = new NinePatchDrawable(this)
    drawable.patch = new com.badlogic.gdx.graphics.g2d.NinePatch(drawable.getPatch(), tint)
    return drawable
  }
}