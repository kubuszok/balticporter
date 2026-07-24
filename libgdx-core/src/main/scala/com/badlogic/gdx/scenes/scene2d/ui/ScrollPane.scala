package com.badlogic.gdx.scenes.scene2d.ui

class ScrollPane extends com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup with com.badlogic.gdx.scenes.scene2d.ui.Styleable[com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.ScrollPaneStyle] {
  private var style: com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.ScrollPaneStyle = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.ScrollPaneStyle]
  private var actor: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
  final val actorArea: com.badlogic.gdx.math.Rectangle = new com.badlogic.gdx.math.Rectangle()
  final val hScrollBounds: com.badlogic.gdx.math.Rectangle = new com.badlogic.gdx.math.Rectangle()
  final val hKnobBounds: com.badlogic.gdx.math.Rectangle = new com.badlogic.gdx.math.Rectangle()
  final val vScrollBounds: com.badlogic.gdx.math.Rectangle = new com.badlogic.gdx.math.Rectangle()
  final val vKnobBounds: com.badlogic.gdx.math.Rectangle = new com.badlogic.gdx.math.Rectangle()
  private final val actorCullingArea: com.badlogic.gdx.math.Rectangle = new com.badlogic.gdx.math.Rectangle()
  private var flickScrollListener: com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener]
  var scrollX$field: scala.Boolean = false
  var scrollY$field: scala.Boolean = false
  var vScrollOnRight: scala.Boolean = true
  var hScrollOnBottom: scala.Boolean = true
  var amountX: scala.Float = 0.0f
  var amountY: scala.Float = 0.0f
  var visualAmountX: scala.Float = 0.0f
  var visualAmountY: scala.Float = 0.0f
  var maxX: scala.Float = 0.0f
  var maxY: scala.Float = 0.0f
  var touchScrollH: scala.Boolean = false
  var touchScrollV: scala.Boolean = false
  final val lastPoint: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  var fadeScrollBars: scala.Boolean = true
  var smoothScrolling: scala.Boolean = true
  var scrollBarTouch: scala.Boolean = true
  var fadeAlpha: scala.Float = 0.0f
  var fadeAlphaSeconds: scala.Float = 1
  var fadeDelay: scala.Float = 0.0f
  var fadeDelaySeconds: scala.Float = 1
  var cancelTouchFocus$field: scala.Boolean = true
  var flickScroll: scala.Boolean = true
  var flingTime: scala.Float = 1.0f
  var flingTimer: scala.Float = 0.0f
  var velocityX: scala.Float = 0.0f
  var velocityY: scala.Float = 0.0f
  private var overscrollX: scala.Boolean = true
  private var overscrollY: scala.Boolean = true
  private var overscrollDistance: scala.Float = 50
  private var overscrollSpeedMin: scala.Float = 30
  private var overscrollSpeedMax: scala.Float = 200
  private var forceScrollX: scala.Boolean = false
  private var forceScrollY: scala.Boolean = false
  var disableX: scala.Boolean = false
  var disableY: scala.Boolean = false
  var clamp$field: scala.Boolean = true
  private var scrollbarsOnTop: scala.Boolean = false
  private var variableSizeKnobs: scala.Boolean = true
  var draggingPointer: scala.Int = -1
  def this(actor: com.badlogic.gdx.scenes.scene2d.Actor, style: com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.ScrollPaneStyle) = {
    this()
    if (style == null) {
      throw new java.lang.IllegalArgumentException("style cannot be null.")
    } else ()
    this.style = style
    this.setActor(actor)
    this.setSize(150, 150)
    this.addCaptureListener()
    this.flickScrollListener = this.getFlickScrollListener()
    this.addListener(this.flickScrollListener)
    this.addScrollListener()
  }
  def this(actor: com.badlogic.gdx.scenes.scene2d.Actor, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin, styleName: java.lang.String) = {
    this(actor, skin.get(styleName, classOf[com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.ScrollPaneStyle]))
  }
  def this(actor: com.badlogic.gdx.scenes.scene2d.Actor, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin) = {
    this(actor, skin.get(classOf[com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.ScrollPaneStyle]))
  }
  def this(actor: com.badlogic.gdx.scenes.scene2d.Actor) = {
    this(actor, new com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.ScrollPaneStyle())
  }
  def addCaptureListener(): scala.Unit = {
    this.addCaptureListener(new com.badlogic.gdx.scenes.scene2d.InputListener())
  }
  def getFlickScrollListener(): com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener = {
    return new com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener()
  }
  def addScrollListener(): scala.Unit = {
    this.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener())
  }
  def setScrollbarsVisible(visible: scala.Boolean): scala.Unit = {
    if (visible) {
      this.fadeAlpha = this.fadeAlphaSeconds
      this.fadeDelay = this.fadeDelaySeconds
    } else {
      this.fadeAlpha = 0
      this.fadeDelay = 0
    }
  }
  def cancelTouchFocus(): scala.Unit = {
    val stage: com.badlogic.gdx.scenes.scene2d.Stage = this.getStage()
    if (stage != null) {
      stage.cancelTouchFocusExcept(this.flickScrollListener, this)
    } else ()
  }
  def cancel(): scala.Unit = {
    this.draggingPointer = -1
    this.touchScrollH = false
    this.touchScrollV = false
    this.flickScrollListener.getGestureDetector().cancel()
  }
  def clamp(): scala.Unit = {
    if (!this.clamp$field) {
      return
    } else ()
    this.scrollX(if (this.overscrollX) com.badlogic.gdx.math.MathUtils.clamp(this.amountX, -this.overscrollDistance, this.maxX + this.overscrollDistance) else com.badlogic.gdx.math.MathUtils.clamp(this.amountX, 0, this.maxX))
    this.scrollY(if (this.overscrollY) com.badlogic.gdx.math.MathUtils.clamp(this.amountY, -this.overscrollDistance, this.maxY + this.overscrollDistance) else com.badlogic.gdx.math.MathUtils.clamp(this.amountY, 0, this.maxY))
  }
  def setStyle(style: com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.ScrollPaneStyle): scala.Unit = {
    if (style == null) {
      throw new java.lang.IllegalArgumentException("style cannot be null.")
    } else ()
    this.style = style
    this.invalidateHierarchy()
  }
  def getStyle(): com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.ScrollPaneStyle = {
    return this.style
  }
  def act(delta: scala.Float): scala.Unit = {
    super.act(delta)
    val panning: scala.Boolean = this.flickScrollListener.getGestureDetector().isPanning()
    var animating: scala.Boolean = false
    if (((((this.fadeAlpha > 0) && this.fadeScrollBars) && (!panning)) && (!this.touchScrollH)) && (!this.touchScrollV)) {
      this.fadeDelay = this.fadeDelay - delta
      if (this.fadeDelay <= 0) {
        this.fadeAlpha = java.lang.Math.max(0, this.fadeAlpha - delta)
      } else ()
      animating = true
    } else ()
    if (this.flingTimer > 0) {
      this.setScrollbarsVisible(true)
      val alpha: scala.Float = this.flingTimer / this.flingTime
      this.amountX = this.amountX - ((this.velocityX * alpha) * delta)
      this.amountY = this.amountY - ((this.velocityY * alpha) * delta)
      this.clamp()
      if (this.amountX == (-this.overscrollDistance)) {
        this.velocityX = 0
      } else ()
      if (this.amountX >= (this.maxX + this.overscrollDistance)) {
        this.velocityX = 0
      } else ()
      if (this.amountY == (-this.overscrollDistance)) {
        this.velocityY = 0
      } else ()
      if (this.amountY >= (this.maxY + this.overscrollDistance)) {
        this.velocityY = 0
      } else ()
      this.flingTimer = this.flingTimer - delta
      if (this.flingTimer <= 0) {
        this.velocityX = 0
        this.velocityY = 0
      } else ()
      animating = true
    } else ()
    if (((this.smoothScrolling && (this.flingTimer <= 0)) && (!panning)) && (((!this.touchScrollH) || (this.scrollX$field && ((this.maxX / (this.hScrollBounds.width - this.hKnobBounds.width)) > (this.actorArea.width * 0.1f)))) && ((!this.touchScrollV) || (this.scrollY$field && ((this.maxY / (this.vScrollBounds.height - this.vKnobBounds.height)) > (this.actorArea.height * 0.1f)))))) {
      if (this.visualAmountX != this.amountX) {
        if (this.visualAmountX < this.amountX) {
          this.visualScrollX(java.lang.Math.min(this.amountX, this.visualAmountX + java.lang.Math.max(200 * delta, ((this.amountX - this.visualAmountX) * 7) * delta)))
        } else {
          this.visualScrollX(java.lang.Math.max(this.amountX, this.visualAmountX - java.lang.Math.max(200 * delta, ((this.visualAmountX - this.amountX) * 7) * delta)))
        }
        animating = true
      } else ()
      if (this.visualAmountY != this.amountY) {
        if (this.visualAmountY < this.amountY) {
          this.visualScrollY(java.lang.Math.min(this.amountY, this.visualAmountY + java.lang.Math.max(200 * delta, ((this.amountY - this.visualAmountY) * 7) * delta)))
        } else {
          this.visualScrollY(java.lang.Math.max(this.amountY, this.visualAmountY - java.lang.Math.max(200 * delta, ((this.visualAmountY - this.amountY) * 7) * delta)))
        }
        animating = true
      } else ()
    } else {
      if (this.visualAmountX != this.amountX) {
        this.visualScrollX(this.amountX)
      } else ()
      if (this.visualAmountY != this.amountY) {
        this.visualScrollY(this.amountY)
      } else ()
    }
    if (!panning) {
      if (this.overscrollX && this.scrollX$field) {
        if (this.amountX < 0) {
          this.setScrollbarsVisible(true)
          this.amountX = this.amountX + ((this.overscrollSpeedMin + (((this.overscrollSpeedMax - this.overscrollSpeedMin) * (-this.amountX)) / this.overscrollDistance)) * delta)
          if (this.amountX > 0) {
            this.scrollX(0)
          } else ()
          animating = true
        } else {
          if (this.amountX > this.maxX) {
            this.setScrollbarsVisible(true)
            this.amountX = this.amountX - ((this.overscrollSpeedMin + (((this.overscrollSpeedMax - this.overscrollSpeedMin) * (-(this.maxX - this.amountX))) / this.overscrollDistance)) * delta)
            if (this.amountX < this.maxX) {
              this.scrollX(this.maxX)
            } else ()
            animating = true
          } else ()
        }
      } else ()
      if (this.overscrollY && this.scrollY$field) {
        if (this.amountY < 0) {
          this.setScrollbarsVisible(true)
          this.amountY = this.amountY + ((this.overscrollSpeedMin + (((this.overscrollSpeedMax - this.overscrollSpeedMin) * (-this.amountY)) / this.overscrollDistance)) * delta)
          if (this.amountY > 0) {
            this.scrollY(0)
          } else ()
          animating = true
        } else {
          if (this.amountY > this.maxY) {
            this.setScrollbarsVisible(true)
            this.amountY = this.amountY - ((this.overscrollSpeedMin + (((this.overscrollSpeedMax - this.overscrollSpeedMin) * (-(this.maxY - this.amountY))) / this.overscrollDistance)) * delta)
            if (this.amountY < this.maxY) {
              this.scrollY(this.maxY)
            } else ()
            animating = true
          } else ()
        }
      } else ()
    } else ()
    if (animating) {
      val stage: com.badlogic.gdx.scenes.scene2d.Stage = this.getStage()
      if ((stage != null) && stage.getActionsRequestRendering()) {
        com.badlogic.gdx.Gdx.graphics.requestRendering()
      } else ()
    } else ()
  }
  def layout(): scala.Unit = {
    val bg: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.style.background
    val hScrollKnob: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.style.hScrollKnob
    val vScrollKnob: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.style.vScrollKnob
    var bgLeftWidth: scala.Float = 0
    var bgRightWidth: scala.Float = 0
    var bgTopHeight: scala.Float = 0
    var bgBottomHeight: scala.Float = 0
    if (bg != null) {
      bgLeftWidth = bg.getLeftWidth()
      bgRightWidth = bg.getRightWidth()
      bgTopHeight = bg.getTopHeight()
      bgBottomHeight = bg.getBottomHeight()
    } else ()
    var width: scala.Float = this.getWidth()
    var height: scala.Float = this.getHeight()
    this.actorArea.set(bgLeftWidth, bgBottomHeight, (width - bgLeftWidth) - bgRightWidth, (height - bgTopHeight) - bgBottomHeight)
    if (this.actor == null) {
      return
    } else ()
    var scrollbarHeight: scala.Float = 0
    var scrollbarWidth: scala.Float = 0
    if (hScrollKnob != null) {
      scrollbarHeight = hScrollKnob.getMinHeight()
    } else ()
    if (this.style.hScroll != null) {
      scrollbarHeight = java.lang.Math.max(scrollbarHeight, this.style.hScroll.getMinHeight())
    } else ()
    if (vScrollKnob != null) {
      scrollbarWidth = vScrollKnob.getMinWidth()
    } else ()
    if (this.style.vScroll != null) {
      scrollbarWidth = java.lang.Math.max(scrollbarWidth, this.style.vScroll.getMinWidth())
    } else ()
    var actorWidth: scala.Float = 0.0f
    var actorHeight: scala.Float = 0.0f
    if (this.actor.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) {
      val layout: com.badlogic.gdx.scenes.scene2d.utils.Layout = this.actor.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]
      actorWidth = layout.getPrefWidth()
      actorHeight = layout.getPrefHeight()
    } else {
      actorWidth = this.actor.getWidth()
      actorHeight = this.actor.getHeight()
    }
    this.scrollX$field = this.forceScrollX || ((actorWidth > this.actorArea.width) && (!this.disableX))
    this.scrollY$field = this.forceScrollY || ((actorHeight > this.actorArea.height) && (!this.disableY))
    if (!this.scrollbarsOnTop) {
      if (this.scrollY$field) {
        this.actorArea.width = this.actorArea.width - scrollbarWidth
        if (!this.vScrollOnRight) {
          this.actorArea.x = this.actorArea.x + scrollbarWidth
        } else ()
        if (((!this.scrollX$field) && (actorWidth > this.actorArea.width)) && (!this.disableX)) {
          this.scrollX$field = true
        } else ()
      } else ()
      if (this.scrollX$field) {
        this.actorArea.height = this.actorArea.height - scrollbarHeight
        if (this.hScrollOnBottom) {
          this.actorArea.y = this.actorArea.y + scrollbarHeight
        } else ()
        if (((!this.scrollY$field) && (actorHeight > this.actorArea.height)) && (!this.disableY)) {
          this.scrollY$field = true
          this.actorArea.width = this.actorArea.width - scrollbarWidth
          if (!this.vScrollOnRight) {
            this.actorArea.x = this.actorArea.x + scrollbarWidth
          } else ()
        } else ()
      } else ()
    } else ()
    actorWidth = if (this.disableX) this.actorArea.width else java.lang.Math.max(this.actorArea.width, actorWidth)
    actorHeight = if (this.disableY) this.actorArea.height else java.lang.Math.max(this.actorArea.height, actorHeight)
    this.maxX = actorWidth - this.actorArea.width
    this.maxY = actorHeight - this.actorArea.height
    this.scrollX(com.badlogic.gdx.math.MathUtils.clamp(this.amountX, 0, this.maxX))
    this.scrollY(com.badlogic.gdx.math.MathUtils.clamp(this.amountY, 0, this.maxY))
    if (this.scrollX$field) {
      if (hScrollKnob != null) {
        var x: scala.Float = if (this.scrollbarsOnTop) bgLeftWidth else this.actorArea.x
        var y: scala.Float = if (this.hScrollOnBottom) bgBottomHeight else (height - bgTopHeight) - scrollbarHeight
        this.hScrollBounds.set(x, y, this.actorArea.width, scrollbarHeight)
        if (this.scrollY$field && this.scrollbarsOnTop) {
          this.hScrollBounds.width = this.hScrollBounds.width - scrollbarWidth
          if (!this.vScrollOnRight) {
            this.hScrollBounds.x = this.hScrollBounds.x + scrollbarWidth
          } else ()
        } else ()
        if (this.variableSizeKnobs) {
          this.hKnobBounds.width = java.lang.Math.max(hScrollKnob.getMinWidth(), ((this.hScrollBounds.width * this.actorArea.width) / actorWidth).asInstanceOf[scala.Int])
        } else {
          this.hKnobBounds.width = hScrollKnob.getMinWidth()
        }
        if (this.hKnobBounds.width > actorWidth) {
          this.hKnobBounds.width = 0
        } else ()
        this.hKnobBounds.height = hScrollKnob.getMinHeight()
        this.hKnobBounds.x = this.hScrollBounds.x + ((this.hScrollBounds.width - this.hKnobBounds.width) * this.getScrollPercentX()).asInstanceOf[scala.Int]
        this.hKnobBounds.y = this.hScrollBounds.y
      } else {
        this.hScrollBounds.set(0, 0, 0, 0)
        this.hKnobBounds.set(0, 0, 0, 0)
      }
    } else ()
    if (this.scrollY$field) {
      if (vScrollKnob != null) {
        var x: scala.Float = if (this.vScrollOnRight) (width - bgRightWidth) - scrollbarWidth else bgLeftWidth
        var y: scala.Float = if (this.scrollbarsOnTop) bgBottomHeight else this.actorArea.y
        this.vScrollBounds.set(x, y, scrollbarWidth, this.actorArea.height)
        if (this.scrollX$field && this.scrollbarsOnTop) {
          this.vScrollBounds.height = this.vScrollBounds.height - scrollbarHeight
          if (this.hScrollOnBottom) {
            this.vScrollBounds.y = this.vScrollBounds.y + scrollbarHeight
          } else ()
        } else ()
        this.vKnobBounds.width = vScrollKnob.getMinWidth()
        if (this.variableSizeKnobs) {
          this.vKnobBounds.height = java.lang.Math.max(vScrollKnob.getMinHeight(), ((this.vScrollBounds.height * this.actorArea.height) / actorHeight).asInstanceOf[scala.Int])
        } else {
          this.vKnobBounds.height = vScrollKnob.getMinHeight()
        }
        if (this.vKnobBounds.height > actorHeight) {
          this.vKnobBounds.height = 0
        } else ()
        this.vKnobBounds.x = if (this.vScrollOnRight) (width - bgRightWidth) - vScrollKnob.getMinWidth() else bgLeftWidth
        this.vKnobBounds.y = this.vScrollBounds.y + ((this.vScrollBounds.height - this.vKnobBounds.height) * (1 - this.getScrollPercentY())).asInstanceOf[scala.Int]
      } else {
        this.vScrollBounds.set(0, 0, 0, 0)
        this.vKnobBounds.set(0, 0, 0, 0)
      }
    } else ()
    this.updateActorPosition()
    if (this.actor.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) {
      this.actor.setSize(actorWidth, actorHeight)
      this.actor.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout].validate()
    } else ()
  }
  private def updateActorPosition(): scala.Unit = {
    var x: scala.Float = this.actorArea.x - (if (this.scrollX$field) this.visualAmountX.asInstanceOf[scala.Int] else 0)
    var y: scala.Float = this.actorArea.y - (if (this.scrollY$field) this.maxY - this.visualAmountY else this.maxY).asInstanceOf[scala.Int]
    this.actor.setPosition(x, y)
    if (this.actor.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Cullable]) {
      this.actorCullingArea.x = this.actorArea.x - x
      this.actorCullingArea.y = this.actorArea.y - y
      this.actorCullingArea.width = this.actorArea.width
      this.actorCullingArea.height = this.actorArea.height
      this.actor.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Cullable].setCullingArea(this.actorCullingArea)
    } else ()
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float): scala.Unit = {
    if (this.actor == null) {
      return
    } else ()
    this.validate()
    this.applyTransform(batch, this.computeTransform())
    if (this.scrollX$field) {
      this.hKnobBounds.x = this.hScrollBounds.x + ((this.hScrollBounds.width - this.hKnobBounds.width) * this.getVisualScrollPercentX()).asInstanceOf[scala.Int]
    } else ()
    if (this.scrollY$field) {
      this.vKnobBounds.y = this.vScrollBounds.y + ((this.vScrollBounds.height - this.vKnobBounds.height) * (1 - this.getVisualScrollPercentY())).asInstanceOf[scala.Int]
    } else ()
    this.updateActorPosition()
    val color: com.badlogic.gdx.graphics.Color = this.getColor()
    var alpha: scala.Float = color.a * parentAlpha
    if (this.style.background != null) {
      batch.setColor(color.r, color.g, color.b, alpha)
      this.style.background.draw(batch, 0, 0, this.getWidth(), this.getHeight())
    } else ()
    batch.flush()
    if (this.clipBegin(this.actorArea.x, this.actorArea.y, this.actorArea.width, this.actorArea.height)) {
      this.drawChildren(batch, parentAlpha)
      batch.flush()
      this.clipEnd()
    } else ()
    batch.setColor(color.r, color.g, color.b, alpha)
    if (this.fadeScrollBars) {
      alpha = alpha * com.badlogic.gdx.math.Interpolation.fade.apply(this.fadeAlpha / this.fadeAlphaSeconds)
    } else ()
    this.drawScrollBars(batch, color.r, color.g, color.b, alpha)
    this.resetTransform(batch)
  }
  def drawScrollBars(batch: com.badlogic.gdx.graphics.g2d.Batch, r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): scala.Unit = {
    if (a <= 0) {
      return
    } else ()
    batch.setColor(r, g, b, a)
    val x: scala.Boolean = this.scrollX$field && (this.hKnobBounds.width > 0)
    val y: scala.Boolean = this.scrollY$field && (this.vKnobBounds.height > 0)
    if (x) {
      if (y && (this.style.corner != null)) {
        this.style.corner.draw(batch, this.hScrollBounds.x + this.hScrollBounds.width, this.hScrollBounds.y, this.vScrollBounds.width, this.vScrollBounds.y)
      } else ()
      if (this.style.hScroll != null) {
        this.style.hScroll.draw(batch, this.hScrollBounds.x, this.hScrollBounds.y, this.hScrollBounds.width, this.hScrollBounds.height)
      } else ()
      if (this.style.hScrollKnob != null) {
        this.style.hScrollKnob.draw(batch, this.hKnobBounds.x, this.hKnobBounds.y, this.hKnobBounds.width, this.hKnobBounds.height)
      } else ()
    } else ()
    if (y) {
      if (this.style.vScroll != null) {
        this.style.vScroll.draw(batch, this.vScrollBounds.x, this.vScrollBounds.y, this.vScrollBounds.width, this.vScrollBounds.height)
      } else ()
      if (this.style.vScrollKnob != null) {
        this.style.vScrollKnob.draw(batch, this.vKnobBounds.x, this.vKnobBounds.y, this.vKnobBounds.width, this.vKnobBounds.height)
      } else ()
    } else ()
  }
  def fling(flingTime: scala.Float, velocityX: scala.Float, velocityY: scala.Float): scala.Unit = {
    this.flingTimer = flingTime
    this.velocityX = velocityX
    this.velocityY = velocityY
  }
  def getPrefWidth(): scala.Float = {
    var width: scala.Float = 0
    if (this.actor.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) {
      width = this.actor.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout].getPrefWidth()
    } else {
      if (this.actor != null) {
        width = this.actor.getWidth()
      } else ()
    }
    val background: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.style.background
    if (background != null) {
      width = java.lang.Math.max((width + background.getLeftWidth()) + background.getRightWidth(), background.getMinWidth())
    } else ()
    if (this.scrollY$field) {
      var scrollbarWidth: scala.Float = 0
      if (this.style.vScrollKnob != null) {
        scrollbarWidth = this.style.vScrollKnob.getMinWidth()
      } else ()
      if (this.style.vScroll != null) {
        scrollbarWidth = java.lang.Math.max(scrollbarWidth, this.style.vScroll.getMinWidth())
      } else ()
      width = width + scrollbarWidth
    } else ()
    return width
  }
  def getPrefHeight(): scala.Float = {
    var height: scala.Float = 0
    if (this.actor.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) {
      height = this.actor.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout].getPrefHeight()
    } else {
      if (this.actor != null) {
        height = this.actor.getHeight()
      } else ()
    }
    val background: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.style.background
    if (background != null) {
      height = java.lang.Math.max((height + background.getTopHeight()) + background.getBottomHeight(), background.getMinHeight())
    } else ()
    if (this.scrollX$field) {
      var scrollbarHeight: scala.Float = 0
      if (this.style.hScrollKnob != null) {
        scrollbarHeight = this.style.hScrollKnob.getMinHeight()
      } else ()
      if (this.style.hScroll != null) {
        scrollbarHeight = java.lang.Math.max(scrollbarHeight, this.style.hScroll.getMinHeight())
      } else ()
      height = height + scrollbarHeight
    } else ()
    return height
  }
  def getMinWidth(): scala.Float = {
    return 0
  }
  def getMinHeight(): scala.Float = {
    return 0
  }
  def setActor(actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    if (this.actor == this) {
      throw new java.lang.IllegalArgumentException("actor cannot be the ScrollPane.")
    } else ()
    if (this.actor != null) {
      super.removeActor(this.actor)
    } else ()
    this.actor = actor
    if (actor != null) {
      super.addActor(actor)
    } else ()
  }
  def getActor(): com.badlogic.gdx.scenes.scene2d.Actor = {
    return this.actor
  }
  def setWidget(actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    this.setActor(actor)
  }
  def getWidget(): com.badlogic.gdx.scenes.scene2d.Actor = {
    return this.actor
  }
  def addActor(actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    throw new java.lang.UnsupportedOperationException("Use ScrollPane#setActor.")
  }
  def addActorAt(index: scala.Int, actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    throw new java.lang.UnsupportedOperationException("Use ScrollPane#setActor.")
  }
  def addActorBefore(actorBefore: com.badlogic.gdx.scenes.scene2d.Actor, actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    throw new java.lang.UnsupportedOperationException("Use ScrollPane#setActor.")
  }
  def addActorAfter(actorAfter: com.badlogic.gdx.scenes.scene2d.Actor, actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    throw new java.lang.UnsupportedOperationException("Use ScrollPane#setActor.")
  }
  def removeActor(actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Boolean = {
    if (actor == null) {
      throw new java.lang.IllegalArgumentException("actor cannot be null.")
    } else ()
    if (actor != this.actor) {
      return false
    } else ()
    this.setActor(null)
    return true
  }
  def removeActor(actor: com.badlogic.gdx.scenes.scene2d.Actor, unfocus: scala.Boolean): scala.Boolean = {
    if (actor == null) {
      throw new java.lang.IllegalArgumentException("actor cannot be null.")
    } else ()
    if (actor != this.actor) {
      return false
    } else ()
    this.actor = null
    return super.removeActor(actor, unfocus)
  }
  def removeActorAt(index: scala.Int, unfocus: scala.Boolean): com.badlogic.gdx.scenes.scene2d.Actor = {
    var actor: com.badlogic.gdx.scenes.scene2d.Actor = super.removeActorAt(index, unfocus)
    if (actor == this.actor) {
      this.actor = null
    } else ()
    return actor
  }
  def hit(x: scala.Float, y: scala.Float, touchable: scala.Boolean): com.badlogic.gdx.scenes.scene2d.Actor = {
    if ((((x < 0) || (x >= this.getWidth())) || (y < 0)) || (y >= this.getHeight())) {
      return null
    } else ()
    if ((touchable && (this.getTouchable() == com.badlogic.gdx.scenes.scene2d.Touchable.enabled)) && this.isVisible()) {
      if ((this.scrollX$field && this.touchScrollH) && this.hScrollBounds.contains(x, y)) {
        return this
      } else ()
      if ((this.scrollY$field && this.touchScrollV) && this.vScrollBounds.contains(x, y)) {
        return this
      } else ()
    } else ()
    return super.hit(x, y, touchable)
  }
  def scrollX(pixelsX: scala.Float): scala.Unit = {
    this.amountX = pixelsX
  }
  def scrollY(pixelsY: scala.Float): scala.Unit = {
    this.amountY = pixelsY
  }
  def visualScrollX(pixelsX: scala.Float): scala.Unit = {
    this.visualAmountX = pixelsX
  }
  def visualScrollY(pixelsY: scala.Float): scala.Unit = {
    this.visualAmountY = pixelsY
  }
  def getMouseWheelX(): scala.Float = {
    return java.lang.Math.min(this.actorArea.width, java.lang.Math.max(this.actorArea.width * 0.9f, this.maxX * 0.1f) / 4)
  }
  def getMouseWheelY(): scala.Float = {
    return java.lang.Math.min(this.actorArea.height, java.lang.Math.max(this.actorArea.height * 0.9f, this.maxY * 0.1f) / 4)
  }
  def setScrollX(pixels: scala.Float): scala.Unit = {
    this.scrollX(com.badlogic.gdx.math.MathUtils.clamp(pixels, 0, this.maxX))
  }
  def getScrollX(): scala.Float = {
    return this.amountX
  }
  def setScrollY(pixels: scala.Float): scala.Unit = {
    this.scrollY(com.badlogic.gdx.math.MathUtils.clamp(pixels, 0, this.maxY))
  }
  def getScrollY(): scala.Float = {
    return this.amountY
  }
  def updateVisualScroll(): scala.Unit = {
    this.visualAmountX = this.amountX
    this.visualAmountY = this.amountY
  }
  def getVisualScrollX(): scala.Float = {
    return if (!this.scrollX$field) 0 else this.visualAmountX
  }
  def getVisualScrollY(): scala.Float = {
    return if (!this.scrollY$field) 0 else this.visualAmountY
  }
  def getVisualScrollPercentX(): scala.Float = {
    if (this.maxX == 0) {
      return 0
    } else ()
    return com.badlogic.gdx.math.MathUtils.clamp(this.visualAmountX / this.maxX, 0, 1)
  }
  def getVisualScrollPercentY(): scala.Float = {
    if (this.maxY == 0) {
      return 0
    } else ()
    return com.badlogic.gdx.math.MathUtils.clamp(this.visualAmountY / this.maxY, 0, 1)
  }
  def getScrollPercentX(): scala.Float = {
    if (this.maxX == 0) {
      return 0
    } else ()
    return com.badlogic.gdx.math.MathUtils.clamp(this.amountX / this.maxX, 0, 1)
  }
  def setScrollPercentX(percentX: scala.Float): scala.Unit = {
    this.scrollX(this.maxX * com.badlogic.gdx.math.MathUtils.clamp(percentX, 0, 1))
  }
  def getScrollPercentY(): scala.Float = {
    if (this.maxY == 0) {
      return 0
    } else ()
    return com.badlogic.gdx.math.MathUtils.clamp(this.amountY / this.maxY, 0, 1)
  }
  def setScrollPercentY(percentY: scala.Float): scala.Unit = {
    this.scrollY(this.maxY * com.badlogic.gdx.math.MathUtils.clamp(percentY, 0, 1))
  }
  def setFlickScroll(flickScroll: scala.Boolean): scala.Unit = {
    if (this.flickScroll == flickScroll) {
      return
    } else ()
    this.flickScroll = flickScroll
    if (flickScroll) {
      this.addListener(this.flickScrollListener)
    } else {
      this.removeListener(this.flickScrollListener)
    }
    this.invalidate()
  }
  def setFlickScrollTapSquareSize(halfTapSquareSize: scala.Float): scala.Unit = {
    this.flickScrollListener.getGestureDetector().setTapSquareSize(halfTapSquareSize)
  }
  def scrollTo(x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    this.scrollTo(x, y, width, height, false, false)
  }
  def scrollTo(x: scala.Float, y$arg: scala.Float, width: scala.Float, height: scala.Float, centerHorizontal: scala.Boolean, centerVertical: scala.Boolean): scala.Unit = {
    var y: scala.Float = y$arg
    this.validate()
    var amountX: scala.Float = this.amountX
    if (centerHorizontal) {
      amountX = x + ((width - this.actorArea.width) / 2)
    } else {
      amountX = com.badlogic.gdx.math.MathUtils.clamp(amountX, x, (x + width) - this.actorArea.width)
    }
    this.scrollX(com.badlogic.gdx.math.MathUtils.clamp(amountX, 0, this.maxX))
    var amountY: scala.Float = this.amountY
    y = this.maxY - y
    if (centerVertical) {
      amountY = y + ((this.actorArea.height + height) / 2)
    } else {
      amountY = com.badlogic.gdx.math.MathUtils.clamp(amountY, y + height, y + this.actorArea.height)
    }
    this.scrollY(com.badlogic.gdx.math.MathUtils.clamp(amountY, 0, this.maxY))
  }
  def getMaxX(): scala.Float = {
    return this.maxX
  }
  def getMaxY(): scala.Float = {
    return this.maxY
  }
  def getScrollBarHeight(): scala.Float = {
    if (!this.scrollX$field) {
      return 0
    } else ()
    var height: scala.Float = 0
    if (this.style.hScrollKnob != null) {
      height = this.style.hScrollKnob.getMinHeight()
    } else ()
    if (this.style.hScroll != null) {
      height = java.lang.Math.max(height, this.style.hScroll.getMinHeight())
    } else ()
    return height
  }
  def getScrollBarWidth(): scala.Float = {
    if (!this.scrollY$field) {
      return 0
    } else ()
    var width: scala.Float = 0
    if (this.style.vScrollKnob != null) {
      width = this.style.vScrollKnob.getMinWidth()
    } else ()
    if (this.style.vScroll != null) {
      width = java.lang.Math.max(width, this.style.vScroll.getMinWidth())
    } else ()
    return width
  }
  def getScrollWidth(): scala.Float = {
    return this.actorArea.width
  }
  def getScrollHeight(): scala.Float = {
    return this.actorArea.height
  }
  def isScrollX(): scala.Boolean = {
    return this.scrollX$field
  }
  def isScrollY(): scala.Boolean = {
    return this.scrollY$field
  }
  def setScrollingDisabled(x: scala.Boolean, y: scala.Boolean): scala.Unit = {
    if ((x == this.disableX) && (y == this.disableY)) {
      return
    } else ()
    this.disableX = x
    this.disableY = y
    this.invalidate()
  }
  def isScrollingDisabledX(): scala.Boolean = {
    return this.disableX
  }
  def isScrollingDisabledY(): scala.Boolean = {
    return this.disableY
  }
  def isLeftEdge(): scala.Boolean = {
    return (!this.scrollX$field) || (this.amountX <= 0)
  }
  def isRightEdge(): scala.Boolean = {
    return (!this.scrollX$field) || (this.amountX >= this.maxX)
  }
  def isTopEdge(): scala.Boolean = {
    return (!this.scrollY$field) || (this.amountY <= 0)
  }
  def isBottomEdge(): scala.Boolean = {
    return (!this.scrollY$field) || (this.amountY >= this.maxY)
  }
  def isDragging(): scala.Boolean = {
    return this.draggingPointer != (-1)
  }
  def isPanning(): scala.Boolean = {
    return this.flickScrollListener.getGestureDetector().isPanning()
  }
  def isFlinging(): scala.Boolean = {
    return this.flingTimer > 0
  }
  def setVelocityX(velocityX: scala.Float): scala.Unit = {
    this.velocityX = velocityX
  }
  def getVelocityX(): scala.Float = {
    return this.velocityX
  }
  def setVelocityY(velocityY: scala.Float): scala.Unit = {
    this.velocityY = velocityY
  }
  def getVelocityY(): scala.Float = {
    return this.velocityY
  }
  def setOverscroll(overscrollX: scala.Boolean, overscrollY: scala.Boolean): scala.Unit = {
    this.overscrollX = overscrollX
    this.overscrollY = overscrollY
  }
  def setupOverscroll(distance: scala.Float, speedMin: scala.Float, speedMax: scala.Float): scala.Unit = {
    this.overscrollDistance = distance
    this.overscrollSpeedMin = speedMin
    this.overscrollSpeedMax = speedMax
  }
  def getOverscrollDistance(): scala.Float = {
    return this.overscrollDistance
  }
  def setForceScroll(x: scala.Boolean, y: scala.Boolean): scala.Unit = {
    this.forceScrollX = x
    this.forceScrollY = y
  }
  def isForceScrollX(): scala.Boolean = {
    return this.forceScrollX
  }
  def isForceScrollY(): scala.Boolean = {
    return this.forceScrollY
  }
  def setFlingTime(flingTime: scala.Float): scala.Unit = {
    this.flingTime = flingTime
  }
  def setClamp(clamp: scala.Boolean): scala.Unit = {
    this.clamp$field = clamp
  }
  def setScrollBarPositions(bottom: scala.Boolean, right: scala.Boolean): scala.Unit = {
    this.hScrollOnBottom = bottom
    this.vScrollOnRight = right
  }
  def setFadeScrollBars(fadeScrollBars: scala.Boolean): scala.Unit = {
    if (this.fadeScrollBars == fadeScrollBars) {
      return
    } else ()
    this.fadeScrollBars = fadeScrollBars
    if (!fadeScrollBars) {
      this.fadeAlpha = this.fadeAlphaSeconds
    } else ()
    this.invalidate()
  }
  def setupFadeScrollBars(fadeAlphaSeconds: scala.Float, fadeDelaySeconds: scala.Float): scala.Unit = {
    this.fadeAlphaSeconds = fadeAlphaSeconds
    this.fadeDelaySeconds = fadeDelaySeconds
  }
  def getFadeScrollBars(): scala.Boolean = {
    return this.fadeScrollBars
  }
  def setScrollBarTouch(scrollBarTouch: scala.Boolean): scala.Unit = {
    this.scrollBarTouch = scrollBarTouch
  }
  def setSmoothScrolling(smoothScrolling: scala.Boolean): scala.Unit = {
    this.smoothScrolling = smoothScrolling
  }
  def setScrollbarsOnTop(scrollbarsOnTop: scala.Boolean): scala.Unit = {
    this.scrollbarsOnTop = scrollbarsOnTop
    this.invalidate()
  }
  def getVariableSizeKnobs(): scala.Boolean = {
    return this.variableSizeKnobs
  }
  def setVariableSizeKnobs(variableSizeKnobs: scala.Boolean): scala.Unit = {
    this.variableSizeKnobs = variableSizeKnobs
  }
  def setCancelTouchFocus(cancelTouchFocus: scala.Boolean): scala.Unit = {
    this.cancelTouchFocus$field = cancelTouchFocus
  }
  def drawDebug(shapes: com.badlogic.gdx.graphics.glutils.ShapeRenderer): scala.Unit = {
    this.drawDebugBounds(shapes)
    this.applyTransform(shapes, this.computeTransform())
    if (this.clipBegin(this.actorArea.x, this.actorArea.y, this.actorArea.width, this.actorArea.height)) {
      this.drawDebugChildren(shapes)
      shapes.flush()
      this.clipEnd()
    } else ()
    this.resetTransform(shapes)
  }
}
object ScrollPane {
  export com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup.*
  class ScrollPaneStyle {
    var background: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var corner: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var hScroll: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var hScrollKnob: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var vScroll: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var vScrollKnob: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    def this(background: com.badlogic.gdx.scenes.scene2d.utils.Drawable, hScroll: com.badlogic.gdx.scenes.scene2d.utils.Drawable, hScrollKnob: com.badlogic.gdx.scenes.scene2d.utils.Drawable, vScroll: com.badlogic.gdx.scenes.scene2d.utils.Drawable, vScrollKnob: com.badlogic.gdx.scenes.scene2d.utils.Drawable) = {
      this()
      this.background = background
      this.hScroll = hScroll
      this.hScrollKnob = hScrollKnob
      this.vScroll = vScroll
      this.vScrollKnob = vScrollKnob
    }
    def this(style: com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.ScrollPaneStyle) = {
      this()
      this.background = style.background
      this.corner = style.corner
      this.hScroll = style.hScroll
      this.hScrollKnob = style.hScrollKnob
      this.vScroll = style.vScroll
      this.vScrollKnob = style.vScrollKnob
    }
  }
}