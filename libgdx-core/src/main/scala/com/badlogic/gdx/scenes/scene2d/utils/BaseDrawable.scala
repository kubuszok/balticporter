package com.badlogic.gdx.scenes.scene2d.utils

class BaseDrawable extends com.badlogic.gdx.scenes.scene2d.utils.Drawable {
  var name: java.lang.String = null.asInstanceOf[java.lang.String]
  var leftWidth: scala.Float = 0.0f
  var rightWidth: scala.Float = 0.0f
  var topHeight: scala.Float = 0.0f
  var bottomHeight: scala.Float = 0.0f
  var minWidth: scala.Float = 0.0f
  var minHeight: scala.Float = 0.0f
  def this(drawable: com.badlogic.gdx.scenes.scene2d.utils.Drawable) = {
    this()
    if (drawable.isInstanceOf[BaseDrawable]) {
      this.name = drawable.asInstanceOf[BaseDrawable].getName()
    } else ()
    this.leftWidth = drawable.getLeftWidth()
    this.rightWidth = drawable.getRightWidth()
    this.topHeight = drawable.getTopHeight()
    this.bottomHeight = drawable.getBottomHeight()
    this.minWidth = drawable.getMinWidth()
    this.minHeight = drawable.getMinHeight()
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    ()
  }
  def getLeftWidth(): scala.Float = {
    return this.leftWidth
  }
  def setLeftWidth(leftWidth: scala.Float): scala.Unit = {
    this.leftWidth = leftWidth
  }
  def getRightWidth(): scala.Float = {
    return this.rightWidth
  }
  def setRightWidth(rightWidth: scala.Float): scala.Unit = {
    this.rightWidth = rightWidth
  }
  def getTopHeight(): scala.Float = {
    return this.topHeight
  }
  def setTopHeight(topHeight: scala.Float): scala.Unit = {
    this.topHeight = topHeight
  }
  def getBottomHeight(): scala.Float = {
    return this.bottomHeight
  }
  def setBottomHeight(bottomHeight: scala.Float): scala.Unit = {
    this.bottomHeight = bottomHeight
  }
  def getMinWidth(): scala.Float = {
    return this.minWidth
  }
  def setMinWidth(minWidth: scala.Float): scala.Unit = {
    this.minWidth = minWidth
  }
  def getMinHeight(): scala.Float = {
    return this.minHeight
  }
  def setMinHeight(minHeight: scala.Float): scala.Unit = {
    this.minHeight = minHeight
  }
  def getName(): java.lang.String = {
    return this.name
  }
  def setName(name: java.lang.String): scala.Unit = {
    this.name = name
  }
  def toString(): java.lang.String = {
    if (this.name == null) {
      return this.getClass().getSimpleName()
    } else ()
    return this.name
  }
}