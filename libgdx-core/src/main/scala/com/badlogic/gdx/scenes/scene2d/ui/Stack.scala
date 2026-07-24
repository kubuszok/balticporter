package com.badlogic.gdx.scenes.scene2d.ui

class Stack extends com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup {
  private var prefWidth: scala.Float = 0.0f
  private var prefHeight: scala.Float = 0.0f
  private var minWidth: scala.Float = 0.0f
  private var minHeight: scala.Float = 0.0f
  private var maxWidth: scala.Float = 0.0f
  private var maxHeight: scala.Float = 0.0f
  private var sizeInvalid: scala.Boolean = true
  def this(actors: scala.Array[com.badlogic.gdx.scenes.scene2d.Actor]) = {
    this()
    for (actor <- actors) {
      this.addActor(actor)
    }
  }
  def this() = {
    this()
    this.setTransform(false)
    this.setWidth(150)
    this.setHeight(150)
    this.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.childrenOnly)
  }
  def invalidate(): scala.Unit = {
    super.invalidate()
    this.sizeInvalid = true
  }
  private def computeSize(): scala.Unit = {
    this.sizeInvalid = false
    this.prefWidth = 0
    this.prefHeight = 0
    this.minWidth = 0
    this.minHeight = 0
    this.maxWidth = 0
    this.maxHeight = 0
    val children: com.badlogic.gdx.utils.SnapshotArray[com.badlogic.gdx.scenes.scene2d.Actor] = this.getChildren();
    { var i: scala.Int = 0; val n: scala.Int = children.size; while (i < n) { {
      val child: com.badlogic.gdx.scenes.scene2d.Actor = children.get(i)
      var childMaxWidth: scala.Float = 0.0f
      var childMaxHeight: scala.Float = 0.0f
      if (child.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) {
        val layout: com.badlogic.gdx.scenes.scene2d.utils.Layout = child.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]
        this.prefWidth = java.lang.Math.max(this.prefWidth, layout.getPrefWidth())
        this.prefHeight = java.lang.Math.max(this.prefHeight, layout.getPrefHeight())
        this.minWidth = java.lang.Math.max(this.minWidth, layout.getMinWidth())
        this.minHeight = java.lang.Math.max(this.minHeight, layout.getMinHeight())
        childMaxWidth = layout.getMaxWidth()
        childMaxHeight = layout.getMaxHeight()
      } else {
        this.prefWidth = java.lang.Math.max(this.prefWidth, child.getWidth())
        this.prefHeight = java.lang.Math.max(this.prefHeight, child.getHeight())
        this.minWidth = java.lang.Math.max(this.minWidth, child.getWidth())
        this.minHeight = java.lang.Math.max(this.minHeight, child.getHeight())
        childMaxWidth = 0
        childMaxHeight = 0
      }
      if (childMaxWidth > 0) {
        this.maxWidth = if (this.maxWidth == 0) childMaxWidth else java.lang.Math.min(this.maxWidth, childMaxWidth)
      } else ()
      if (childMaxHeight > 0) {
        this.maxHeight = if (this.maxHeight == 0) childMaxHeight else java.lang.Math.min(this.maxHeight, childMaxHeight)
      } else ()
    }; i = i + 1 } }
  }
  def add(actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    this.addActor(actor)
  }
  def layout(): scala.Unit = {
    if (this.sizeInvalid) {
      this.computeSize()
    } else ()
    val width: scala.Float = this.getWidth()
    val height: scala.Float = this.getHeight()
    val children: com.badlogic.gdx.utils.Array[com.badlogic.gdx.scenes.scene2d.Actor] = this.getChildren();
    { var i: scala.Int = 0; val n: scala.Int = children.size; while (i < n) { {
      val child: com.badlogic.gdx.scenes.scene2d.Actor = children.get(i)
      child.setBounds(0, 0, width, height)
      if (child.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) {
        child.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout].validate()
      } else ()
    }; i = i + 1 } }
  }
  def getPrefWidth(): scala.Float = {
    if (this.sizeInvalid) {
      this.computeSize()
    } else ()
    return this.prefWidth
  }
  def getPrefHeight(): scala.Float = {
    if (this.sizeInvalid) {
      this.computeSize()
    } else ()
    return this.prefHeight
  }
  def getMinWidth(): scala.Float = {
    if (this.sizeInvalid) {
      this.computeSize()
    } else ()
    return this.minWidth
  }
  def getMinHeight(): scala.Float = {
    if (this.sizeInvalid) {
      this.computeSize()
    } else ()
    return this.minHeight
  }
  def getMaxWidth(): scala.Float = {
    if (this.sizeInvalid) {
      this.computeSize()
    } else ()
    return this.maxWidth
  }
  def getMaxHeight(): scala.Float = {
    if (this.sizeInvalid) {
      this.computeSize()
    } else ()
    return this.maxHeight
  }
}