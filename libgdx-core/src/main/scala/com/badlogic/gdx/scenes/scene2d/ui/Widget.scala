package com.badlogic.gdx.scenes.scene2d.ui

class Widget extends com.badlogic.gdx.scenes.scene2d.Actor with com.badlogic.gdx.scenes.scene2d.utils.Layout {
  var needsLayout$field: scala.Boolean = true
  private var fillParent: scala.Boolean = false
  private var layoutEnabled: scala.Boolean = true
  override def getMinWidth(): scala.Float = {
    return this.getPrefWidth()
  }
  override def getMinHeight(): scala.Float = {
    return this.getPrefHeight()
  }
  override def getPrefWidth(): scala.Float = {
    return 0
  }
  override def getPrefHeight(): scala.Float = {
    return 0
  }
  override def getMaxWidth(): scala.Float = {
    return 0
  }
  override def getMaxHeight(): scala.Float = {
    return 0
  }
  override def setLayoutEnabled(enabled: scala.Boolean): scala.Unit = {
    this.layoutEnabled = enabled
    if (enabled) {
      this.invalidateHierarchy()
    } else ()
  }
  override def validate(): scala.Unit = {
    if (!this.layoutEnabled) {
      return
    } else ()
    val parent: com.badlogic.gdx.scenes.scene2d.Group = this.getParent()
    if (this.fillParent && (parent != null)) {
      var parentWidth: scala.Float = 0.0f
      var parentHeight: scala.Float = 0.0f
      val stage: com.badlogic.gdx.scenes.scene2d.Stage = this.getStage()
      if ((stage != null) && (parent == stage.getRoot())) {
        parentWidth = stage.getWidth()
        parentHeight = stage.getHeight()
      } else {
        parentWidth = parent.getWidth()
        parentHeight = parent.getHeight()
      }
      this.setSize(parentWidth, parentHeight)
    } else ()
    if (!this.needsLayout$field) {
      return
    } else ()
    this.needsLayout$field = false
    this.layout()
  }
  def needsLayout(): scala.Boolean = {
    return this.needsLayout$field
  }
  override def invalidate(): scala.Unit = {
    this.needsLayout$field = true
  }
  override def invalidateHierarchy(): scala.Unit = {
    if (!this.layoutEnabled) {
      return
    } else ()
    this.invalidate()
    val parent: com.badlogic.gdx.scenes.scene2d.Group = this.getParent()
    if (parent.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) {
      parent.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout].invalidateHierarchy()
    } else ()
  }
  override def sizeChanged(): scala.Unit = {
    this.invalidate()
  }
  override def pack(): scala.Unit = {
    this.setSize(this.getPrefWidth(), this.getPrefHeight())
    this.validate()
  }
  override def setFillParent(fillParent: scala.Boolean): scala.Unit = {
    this.fillParent = fillParent
  }
  override def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float): scala.Unit = {
    this.validate()
  }
  override def layout(): scala.Unit = {
    ()
  }
}
object Widget {
  export com.badlogic.gdx.scenes.scene2d.Actor.*
}