package com.badlogic.gdx.scenes.scene2d.ui

class SplitPane extends com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup with com.badlogic.gdx.scenes.scene2d.ui.Styleable[com.badlogic.gdx.scenes.scene2d.ui.SplitPane.SplitPaneStyle] {
  var style: com.badlogic.gdx.scenes.scene2d.ui.SplitPane.SplitPaneStyle = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.SplitPane.SplitPaneStyle]
  private var firstWidget: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
  private var secondWidget: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
  var vertical: scala.Boolean = false
  var splitAmount: scala.Float = 0.5f
  var minAmount: scala.Float = 0.0f
  var maxAmount: scala.Float = 1
  private final val firstWidgetBounds: com.badlogic.gdx.math.Rectangle = new com.badlogic.gdx.math.Rectangle()
  private final val secondWidgetBounds: com.badlogic.gdx.math.Rectangle = new com.badlogic.gdx.math.Rectangle()
  final val handleBounds: com.badlogic.gdx.math.Rectangle = new com.badlogic.gdx.math.Rectangle()
  var cursorOverHandle: scala.Boolean = false
  private final val tempScissors: com.badlogic.gdx.math.Rectangle = new com.badlogic.gdx.math.Rectangle()
  var lastPoint: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  var handlePosition: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  def this(firstWidget: com.badlogic.gdx.scenes.scene2d.Actor, secondWidget: com.badlogic.gdx.scenes.scene2d.Actor, vertical: scala.Boolean, style: com.badlogic.gdx.scenes.scene2d.ui.SplitPane.SplitPaneStyle) = {
    this()
    this.vertical = vertical
    this.setStyle(style)
    this.setFirstWidget(firstWidget)
    this.setSecondWidget(secondWidget)
    this.setSize(this.getPrefWidth(), this.getPrefHeight())
    this.initialize()
  }
  def this(firstWidget: com.badlogic.gdx.scenes.scene2d.Actor, secondWidget: com.badlogic.gdx.scenes.scene2d.Actor, vertical: scala.Boolean, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin, styleName: java.lang.String) = {
    this(firstWidget, secondWidget, vertical, skin.get(styleName, classOf[com.badlogic.gdx.scenes.scene2d.ui.SplitPane.SplitPaneStyle]))
  }
  def this(firstWidget: com.badlogic.gdx.scenes.scene2d.Actor, secondWidget: com.badlogic.gdx.scenes.scene2d.Actor, vertical: scala.Boolean, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin) = {
    this(firstWidget, secondWidget, vertical, skin, "default-" + (if (vertical) "vertical" else "horizontal"))
  }
  private def initialize(): scala.Unit = {
    this.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener())
  }
  def setStyle(style: com.badlogic.gdx.scenes.scene2d.ui.SplitPane.SplitPaneStyle): scala.Unit = {
    this.style = style
    this.invalidateHierarchy()
  }
  def getStyle(): com.badlogic.gdx.scenes.scene2d.ui.SplitPane.SplitPaneStyle = {
    return this.style
  }
  def layout(): scala.Unit = {
    this.clampSplitAmount()
    if (!this.vertical) {
      this.calculateHorizBoundsAndPositions()
    } else {
      this.calculateVertBoundsAndPositions()
    }
    val firstWidget: com.badlogic.gdx.scenes.scene2d.Actor = this.firstWidget
    if (firstWidget != null) {
      val firstWidgetBounds: com.badlogic.gdx.math.Rectangle = this.firstWidgetBounds
      firstWidget.setBounds(firstWidgetBounds.x, firstWidgetBounds.y, firstWidgetBounds.width, firstWidgetBounds.height)
      if (firstWidget.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) {
        firstWidget.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout].validate()
      } else ()
    } else ()
    val secondWidget: com.badlogic.gdx.scenes.scene2d.Actor = this.secondWidget
    if (secondWidget != null) {
      val secondWidgetBounds: com.badlogic.gdx.math.Rectangle = this.secondWidgetBounds
      secondWidget.setBounds(secondWidgetBounds.x, secondWidgetBounds.y, secondWidgetBounds.width, secondWidgetBounds.height)
      if (secondWidget.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) {
        secondWidget.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout].validate()
      } else ()
    } else ()
  }
  def getPrefWidth(): scala.Float = {
    val first: scala.Float = if (this.firstWidget == null) 0 else if (this.firstWidget.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) this.firstWidget.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout].getPrefWidth() else this.firstWidget.getWidth()
    val second: scala.Float = if (this.secondWidget == null) 0 else if (this.secondWidget.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) this.secondWidget.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout].getPrefWidth() else this.secondWidget.getWidth()
    if (this.vertical) {
      return java.lang.Math.max(first, second)
    } else ()
    return (first + this.style.handle.getMinWidth()) + second
  }
  def getPrefHeight(): scala.Float = {
    val first: scala.Float = if (this.firstWidget == null) 0 else if (this.firstWidget.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) this.firstWidget.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout].getPrefHeight() else this.firstWidget.getHeight()
    val second: scala.Float = if (this.secondWidget == null) 0 else if (this.secondWidget.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) this.secondWidget.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout].getPrefHeight() else this.secondWidget.getHeight()
    if (!this.vertical) {
      return java.lang.Math.max(first, second)
    } else ()
    return (first + this.style.handle.getMinHeight()) + second
  }
  def getMinWidth(): scala.Float = {
    val first: scala.Float = if (this.firstWidget.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) this.firstWidget.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout].getMinWidth() else 0
    val second: scala.Float = if (this.secondWidget.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) this.secondWidget.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout].getMinWidth() else 0
    if (this.vertical) {
      return java.lang.Math.max(first, second)
    } else ()
    return (first + this.style.handle.getMinWidth()) + second
  }
  def getMinHeight(): scala.Float = {
    val first: scala.Float = if (this.firstWidget.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) this.firstWidget.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout].getMinHeight() else 0
    val second: scala.Float = if (this.secondWidget.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) this.secondWidget.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout].getMinHeight() else 0
    if (!this.vertical) {
      return java.lang.Math.max(first, second)
    } else ()
    return (first + this.style.handle.getMinHeight()) + second
  }
  def setVertical(vertical: scala.Boolean): scala.Unit = {
    if (this.vertical == vertical) {
      return
    } else ()
    this.vertical = vertical
    this.invalidateHierarchy()
  }
  def isVertical(): scala.Boolean = {
    return this.vertical
  }
  private def calculateHorizBoundsAndPositions(): scala.Unit = {
    val handle: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.style.handle
    val height: scala.Float = this.getHeight()
    val availWidth: scala.Float = this.getWidth() - handle.getMinWidth()
    val leftAreaWidth: scala.Float = (availWidth * this.splitAmount).asInstanceOf[scala.Int]
    val rightAreaWidth: scala.Float = availWidth - leftAreaWidth
    val handleWidth: scala.Float = handle.getMinWidth()
    this.firstWidgetBounds.set(0, 0, leftAreaWidth, height)
    this.secondWidgetBounds.set(leftAreaWidth + handleWidth, 0, rightAreaWidth, height)
    this.handleBounds.set(leftAreaWidth, 0, handleWidth, height)
  }
  private def calculateVertBoundsAndPositions(): scala.Unit = {
    val handle: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.style.handle
    val width: scala.Float = this.getWidth()
    val height: scala.Float = this.getHeight()
    val availHeight: scala.Float = height - handle.getMinHeight()
    val topAreaHeight: scala.Float = (availHeight * this.splitAmount).asInstanceOf[scala.Int]
    val bottomAreaHeight: scala.Float = availHeight - topAreaHeight
    val handleHeight: scala.Float = handle.getMinHeight()
    this.firstWidgetBounds.set(0, height - topAreaHeight, width, topAreaHeight)
    this.secondWidgetBounds.set(0, 0, width, bottomAreaHeight)
    this.handleBounds.set(0, bottomAreaHeight, width, handleHeight)
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float): scala.Unit = {
    val stage: com.badlogic.gdx.scenes.scene2d.Stage = this.getStage()
    if (stage == null) {
      return
    } else ()
    this.validate()
    val color: com.badlogic.gdx.graphics.Color = this.getColor()
    val alpha: scala.Float = color.a * parentAlpha
    this.applyTransform(batch, this.computeTransform())
    if ((this.firstWidget != null) && this.firstWidget.isVisible()) {
      batch.flush()
      stage.calculateScissors(this.firstWidgetBounds, this.tempScissors)
      if (com.badlogic.gdx.scenes.scene2d.utils.ScissorStack.pushScissors(this.tempScissors)) {
        this.firstWidget.draw(batch, alpha)
        batch.flush()
        com.badlogic.gdx.scenes.scene2d.utils.ScissorStack.popScissors()
      } else ()
    } else ()
    if ((this.secondWidget != null) && this.secondWidget.isVisible()) {
      batch.flush()
      stage.calculateScissors(this.secondWidgetBounds, this.tempScissors)
      if (com.badlogic.gdx.scenes.scene2d.utils.ScissorStack.pushScissors(this.tempScissors)) {
        this.secondWidget.draw(batch, alpha)
        batch.flush()
        com.badlogic.gdx.scenes.scene2d.utils.ScissorStack.popScissors()
      } else ()
    } else ()
    batch.setColor(color.r, color.g, color.b, alpha)
    this.style.handle.draw(batch, this.handleBounds.x, this.handleBounds.y, this.handleBounds.width, this.handleBounds.height)
    this.resetTransform(batch)
  }
  def setSplitAmount(splitAmount: scala.Float): scala.Unit = {
    this.splitAmount = splitAmount
    this.invalidate()
  }
  def getSplitAmount(): scala.Float = {
    return this.splitAmount
  }
  def clampSplitAmount(): scala.Unit = {
    var effectiveMinAmount: scala.Float = this.minAmount
    var effectiveMaxAmount: scala.Float = this.maxAmount
    if (this.vertical) {
      val availableHeight: scala.Float = this.getHeight() - this.style.handle.getMinHeight()
      if (this.firstWidget.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) {
        effectiveMinAmount = java.lang.Math.max(effectiveMinAmount, java.lang.Math.min(this.firstWidget.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout].getMinHeight() / availableHeight, 1))
      } else ()
      if (this.secondWidget.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) {
        effectiveMaxAmount = java.lang.Math.min(effectiveMaxAmount, 1 - java.lang.Math.min(this.secondWidget.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout].getMinHeight() / availableHeight, 1))
      } else ()
    } else {
      val availableWidth: scala.Float = this.getWidth() - this.style.handle.getMinWidth()
      if (this.firstWidget.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) {
        effectiveMinAmount = java.lang.Math.max(effectiveMinAmount, java.lang.Math.min(this.firstWidget.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout].getMinWidth() / availableWidth, 1))
      } else ()
      if (this.secondWidget.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) {
        effectiveMaxAmount = java.lang.Math.min(effectiveMaxAmount, 1 - java.lang.Math.min(this.secondWidget.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout].getMinWidth() / availableWidth, 1))
      } else ()
    }
    if (effectiveMinAmount > effectiveMaxAmount) {
      this.splitAmount = 0.5f * (effectiveMinAmount + effectiveMaxAmount)
    } else {
      this.splitAmount = java.lang.Math.max(java.lang.Math.min(this.splitAmount, effectiveMaxAmount), effectiveMinAmount)
    }
  }
  def getMinSplitAmount(): scala.Float = {
    return this.minAmount
  }
  def setMinSplitAmount(minAmount: scala.Float): scala.Unit = {
    if ((minAmount < 0) || (minAmount > 1)) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("minAmount has to be >= 0 and <= 1")
    } else ()
    this.minAmount = minAmount
  }
  def getMaxSplitAmount(): scala.Float = {
    return this.maxAmount
  }
  def setMaxSplitAmount(maxAmount: scala.Float): scala.Unit = {
    if ((maxAmount < 0) || (maxAmount > 1)) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("maxAmount has to be >= 0 and <= 1")
    } else ()
    this.maxAmount = maxAmount
  }
  def setFirstWidget(widget: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    if (this.firstWidget != null) {
      super.removeActor(this.firstWidget)
    } else ()
    this.firstWidget = widget
    if (widget != null) {
      super.addActor(widget)
    } else ()
    this.invalidate()
  }
  def setSecondWidget(widget: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    if (this.secondWidget != null) {
      super.removeActor(this.secondWidget)
    } else ()
    this.secondWidget = widget
    if (widget != null) {
      super.addActor(widget)
    } else ()
    this.invalidate()
  }
  def addActor(actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    throw new java.lang.UnsupportedOperationException("Use SplitPane#setWidget.")
  }
  def addActorAt(index: scala.Int, actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    throw new java.lang.UnsupportedOperationException("Use SplitPane#setWidget.")
  }
  def addActorBefore(actorBefore: com.badlogic.gdx.scenes.scene2d.Actor, actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    throw new java.lang.UnsupportedOperationException("Use SplitPane#setWidget.")
  }
  def removeActor(actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Boolean = {
    if (actor == null) {
      throw new java.lang.IllegalArgumentException("actor cannot be null.")
    } else ()
    if (actor == this.firstWidget) {
      this.setFirstWidget(null)
      return true
    } else ()
    if (actor == this.secondWidget) {
      this.setSecondWidget(null)
      return true
    } else ()
    return true
  }
  def removeActor(actor: com.badlogic.gdx.scenes.scene2d.Actor, unfocus: scala.Boolean): scala.Boolean = {
    if (actor == null) {
      throw new java.lang.IllegalArgumentException("actor cannot be null.")
    } else ()
    if (actor == this.firstWidget) {
      super.removeActor(actor, unfocus)
      this.firstWidget = null
      this.invalidate()
      return true
    } else ()
    if (actor == this.secondWidget) {
      super.removeActor(actor, unfocus)
      this.secondWidget = null
      this.invalidate()
      return true
    } else ()
    return false
  }
  def removeActorAt(index: scala.Int, unfocus: scala.Boolean): com.badlogic.gdx.scenes.scene2d.Actor = {
    val actor: com.badlogic.gdx.scenes.scene2d.Actor = super.removeActorAt(index, unfocus)
    if (actor == this.firstWidget) {
      super.removeActor(actor, unfocus)
      this.firstWidget = null
      this.invalidate()
    } else {
      if (actor == this.secondWidget) {
        super.removeActor(actor, unfocus)
        this.secondWidget = null
        this.invalidate()
      } else ()
    }
    return actor
  }
  def isCursorOverHandle(): scala.Boolean = {
    return this.cursorOverHandle
  }
}
object SplitPane {
  export com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup.*
  class SplitPaneStyle {
    var handle: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    def this(handle: com.badlogic.gdx.scenes.scene2d.utils.Drawable) = {
      this()
      this.handle = handle
    }
    def this(style: com.badlogic.gdx.scenes.scene2d.ui.SplitPane.SplitPaneStyle) = {
      this()
      this.handle = style.handle
    }
  }
}