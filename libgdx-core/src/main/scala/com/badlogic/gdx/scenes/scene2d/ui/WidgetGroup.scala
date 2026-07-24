package com.badlogic.gdx.scenes.scene2d.ui

class WidgetGroup extends com.badlogic.gdx.scenes.scene2d.Group with com.badlogic.gdx.scenes.scene2d.utils.Layout {
  var needsLayout$field: scala.Boolean = true
  private var fillParent: scala.Boolean = false
  private var layoutEnabled: scala.Boolean = true
  def this(actors: scala.Array[com.badlogic.gdx.scenes.scene2d.Actor]) = {
    this()
    for (actor <- actors) {
      this.addActor(actor)
    }
  }
  def getMinWidth(): scala.Float = {
    return this.getPrefWidth()
  }
  def getMinHeight(): scala.Float = {
    return this.getPrefHeight()
  }
  def getPrefWidth(): scala.Float = {
    return 0
  }
  def getPrefHeight(): scala.Float = {
    return 0
  }
  def getMaxWidth(): scala.Float = {
    return 0
  }
  def getMaxHeight(): scala.Float = {
    return 0
  }
  def setLayoutEnabled(enabled: scala.Boolean): scala.Unit = {
    this.layoutEnabled = enabled
    this.setLayoutEnabled(this, enabled)
  }
  private def setLayoutEnabled(parent: com.badlogic.gdx.scenes.scene2d.Group, enabled: scala.Boolean): scala.Unit = {
    val children: com.badlogic.gdx.utils.SnapshotArray[com.badlogic.gdx.scenes.scene2d.Actor] = parent.getChildren()
    { var i: scala.Int = 0; val n: scala.Int = children.size; while (i < n) { {
      val actor: com.badlogic.gdx.scenes.scene2d.Actor = children.get(i)
      if (actor.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) {
        actor.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout].setLayoutEnabled(enabled)
      } else {
        if (actor.isInstanceOf[com.badlogic.gdx.scenes.scene2d.Group]) {
          this.setLayoutEnabled(actor.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Group], enabled)
        } else ()
      }
    }; i = i + 1 } }
  }
  def validate(): scala.Unit = {
    if (!this.layoutEnabled) {
      return
    } else ()
    val parent: com.badlogic.gdx.scenes.scene2d.Group = this.getParent()
    if (this.fillParent && (parent != null)) {
      val stage: com.badlogic.gdx.scenes.scene2d.Stage = this.getStage()
      if ((stage != null) && (parent == stage.getRoot())) {
        this.setSize(stage.getWidth(), stage.getHeight())
      } else {
        this.setSize(parent.getWidth(), parent.getHeight())
      }
    } else ()
    if (!this.needsLayout$field) {
      return
    } else ()
    this.needsLayout$field = false
    this.layout()
    if (this.needsLayout$field) {
      if (parent.isInstanceOf[WidgetGroup]) {
        return
      } else ()
      { var i: scala.Int = 0; while (i < 5) { {
        this.needsLayout$field = false
        this.layout()
        if (!this.needsLayout$field) {
          /* break */ ()
        } else ()
      }; i = i + 1 } }
    } else ()
  }
  def needsLayout(): scala.Boolean = {
    return this.needsLayout$field
  }
  def invalidate(): scala.Unit = {
    this.needsLayout$field = true
  }
  def invalidateHierarchy(): scala.Unit = {
    this.invalidate()
    val parent: com.badlogic.gdx.scenes.scene2d.Group = this.getParent()
    if (parent.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) {
      parent.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout].invalidateHierarchy()
    } else ()
  }
  protected def childrenChanged(): scala.Unit = {
    this.invalidateHierarchy()
  }
  protected def sizeChanged(): scala.Unit = {
    this.invalidate()
  }
  def pack(): scala.Unit = {
    this.setSize(this.getPrefWidth(), this.getPrefHeight())
    this.validate()
    this.setSize(this.getPrefWidth(), this.getPrefHeight())
    this.validate()
  }
  def setFillParent(fillParent: scala.Boolean): scala.Unit = {
    this.fillParent = fillParent
  }
  def layout(): scala.Unit = {
    ()
  }
  def hit(x: scala.Float, y: scala.Float, touchable: scala.Boolean): com.badlogic.gdx.scenes.scene2d.Actor = {
    this.validate()
    return super.hit(x, y, touchable)
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float): scala.Unit = {
    this.validate()
    super.draw(batch, parentAlpha)
  }
}