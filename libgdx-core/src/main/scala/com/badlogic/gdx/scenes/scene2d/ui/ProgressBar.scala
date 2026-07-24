package com.badlogic.gdx.scenes.scene2d.ui

class ProgressBar extends com.badlogic.gdx.scenes.scene2d.ui.Widget with com.badlogic.gdx.scenes.scene2d.utils.Disableable with com.badlogic.gdx.scenes.scene2d.ui.Styleable[com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle] {
  private var style: com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle]
  var min: scala.Float = 0.0f
  var max: scala.Float = 0.0f
  var stepSize: scala.Float = 0.0f
  private var value: scala.Float = 0.0f
  private var animateFromValue: scala.Float = 0.0f
  var position: scala.Float = 0.0f
  var vertical: scala.Boolean = false
  private var animateDuration: scala.Float = 0.0f
  private var animateTime: scala.Float = 0.0f
  private var animateInterpolation: com.badlogic.gdx.math.Interpolation = com.badlogic.gdx.math.Interpolation.linear
  private var visualInterpolation: com.badlogic.gdx.math.Interpolation = com.badlogic.gdx.math.Interpolation.linear
  var disabled: scala.Boolean = false
  var round$field: scala.Boolean = true
  private var programmaticChangeEvents: scala.Boolean = true
  def this(min: scala.Float, max: scala.Float, stepSize: scala.Float, vertical: scala.Boolean, style: com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle) = {
    this()
    if (min > max) {
      throw new java.lang.IllegalArgumentException((("max must be > min. min,max: " + min) + ", ") + max)
    } else ()
    if (stepSize <= 0) {
      throw new java.lang.IllegalArgumentException("stepSize must be > 0: " + stepSize)
    } else ()
    this.setStyle(style)
    this.min = min
    this.max = max
    this.stepSize = stepSize
    this.vertical = vertical
    this.value = min
    this.setSize(this.getPrefWidth(), this.getPrefHeight())
  }
  def this(min: scala.Float, max: scala.Float, stepSize: scala.Float, vertical: scala.Boolean, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin) = {
    this(min, max, stepSize, vertical, skin.get("default-" + (if (vertical) "vertical" else "horizontal"), classOf[com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle]))
  }
  def this(min: scala.Float, max: scala.Float, stepSize: scala.Float, vertical: scala.Boolean, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin, styleName: java.lang.String) = {
    this(min, max, stepSize, vertical, skin.get(styleName, classOf[com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle]))
  }
  def setStyle(style: com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle): scala.Unit = {
    if (style == null) {
      throw new java.lang.IllegalArgumentException("style cannot be null.")
    } else ()
    this.style = style
    this.invalidateHierarchy()
  }
  def getStyle(): com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle = {
    return this.style
  }
  def act(delta: scala.Float): scala.Unit = {
    super.act(delta)
    if (this.animateTime > 0) {
      this.animateTime = this.animateTime - delta
      val stage: com.badlogic.gdx.scenes.scene2d.Stage = this.getStage()
      if ((stage != null) && stage.getActionsRequestRendering()) {
        com.badlogic.gdx.Gdx.graphics.requestRendering()
      } else ()
    } else ()
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float): scala.Unit = {
    val style: com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle = this.style
    val disabled: scala.Boolean = this.disabled
    val knob: com.badlogic.gdx.scenes.scene2d.utils.Drawable = style.knob
    val currentKnob: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.getKnobDrawable()
    val bg: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.getBackgroundDrawable()
    val knobBefore: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.getKnobBeforeDrawable()
    val knobAfter: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.getKnobAfterDrawable()
    val color: com.badlogic.gdx.graphics.Color = this.getColor()
    val x: scala.Float = this.getX()
    val y: scala.Float = this.getY()
    var width: scala.Float = this.getWidth()
    var height: scala.Float = this.getHeight()
    val knobHeight: scala.Float = if (knob == null) 0 else knob.getMinHeight()
    val knobWidth: scala.Float = if (knob == null) 0 else knob.getMinWidth()
    val percent: scala.Float = this.getVisualPercent()
    batch.setColor(color.r, color.g, color.b, color.a * parentAlpha)
    if (this.vertical) {
      var bgTopHeight: scala.Float = 0
      var bgBottomHeight: scala.Float = 0
      if (bg != null) {
        this.drawRound(batch, bg, x + ((width - bg.getMinWidth()) * 0.5f), y, bg.getMinWidth(), height)
        bgTopHeight = bg.getTopHeight()
        bgBottomHeight = bg.getBottomHeight()
        height = height - (bgTopHeight + bgBottomHeight)
      } else ()
      val total: scala.Float = height - knobHeight
      val beforeHeight: scala.Float = com.badlogic.gdx.math.MathUtils.clamp(total * percent, 0, total)
      this.position = bgBottomHeight + beforeHeight
      val knobHeightHalf: scala.Float = knobHeight * 0.5f
      if (knobBefore != null) {
        this.drawRound(batch, knobBefore, x + ((width - knobBefore.getMinWidth()) * 0.5f), y + bgBottomHeight, knobBefore.getMinWidth(), beforeHeight + knobHeightHalf)
      } else ()
      if (knobAfter != null) {
        this.drawRound(batch, knobAfter, x + ((width - knobAfter.getMinWidth()) * 0.5f), (y + this.position) + knobHeightHalf, knobAfter.getMinWidth(), total - (if (this.round$field) java.lang.Math.ceil(beforeHeight - knobHeightHalf).asInstanceOf[scala.Float] else beforeHeight - knobHeightHalf))
      } else ()
      if (currentKnob != null) {
        val w: scala.Float = currentKnob.getMinWidth()
        val h: scala.Float = currentKnob.getMinHeight()
        this.drawRound(batch, currentKnob, x + ((width - w) * 0.5f), (y + this.position) + ((knobHeight - h) * 0.5f), w, h)
      } else ()
    } else {
      var bgLeftWidth: scala.Float = 0
      var bgRightWidth: scala.Float = 0
      if (bg != null) {
        this.drawRound(batch, bg, x, java.lang.Math.round(y + ((height - bg.getMinHeight()) * 0.5f)), width, java.lang.Math.round(bg.getMinHeight()))
        bgLeftWidth = bg.getLeftWidth()
        bgRightWidth = bg.getRightWidth()
        width = width - (bgLeftWidth + bgRightWidth)
      } else ()
      val total: scala.Float = width - knobWidth
      val beforeWidth: scala.Float = com.badlogic.gdx.math.MathUtils.clamp(total * percent, 0, total)
      this.position = bgLeftWidth + beforeWidth
      val knobWidthHalf: scala.Float = knobWidth * 0.5f
      if (knobBefore != null) {
        this.drawRound(batch, knobBefore, x + bgLeftWidth, y + ((height - knobBefore.getMinHeight()) * 0.5f), beforeWidth + knobWidthHalf, knobBefore.getMinHeight())
      } else ()
      if (knobAfter != null) {
        this.drawRound(batch, knobAfter, (x + this.position) + knobWidthHalf, y + ((height - knobAfter.getMinHeight()) * 0.5f), total - (if (this.round$field) java.lang.Math.ceil(beforeWidth - knobWidthHalf).asInstanceOf[scala.Float] else beforeWidth - knobWidthHalf), knobAfter.getMinHeight())
      } else ()
      if (currentKnob != null) {
        val w: scala.Float = currentKnob.getMinWidth()
        val h: scala.Float = currentKnob.getMinHeight()
        this.drawRound(batch, currentKnob, (x + this.position) + ((knobWidth - w) * 0.5f), y + ((height - h) * 0.5f), w, h)
      } else ()
    }
  }
  private def drawRound(batch: com.badlogic.gdx.graphics.g2d.Batch, drawable: com.badlogic.gdx.scenes.scene2d.utils.Drawable, x$arg: scala.Float, y$arg: scala.Float, w$arg: scala.Float, h$arg: scala.Float): scala.Unit = {
    var x: scala.Float = x$arg
    var y: scala.Float = y$arg
    var w: scala.Float = w$arg
    var h: scala.Float = h$arg
    if (this.round$field) {
      x = java.lang.Math.floor(x).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
      y = java.lang.Math.floor(y).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
      w = java.lang.Math.ceil(w).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
      h = java.lang.Math.ceil(h).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
    } else ()
    drawable.draw(batch, x, y, w, h)
  }
  def getValue(): scala.Float = {
    return this.value
  }
  def getVisualValue(): scala.Float = {
    if (this.animateTime > 0) {
      return this.animateInterpolation.apply(this.animateFromValue, this.value, 1 - (this.animateTime / this.animateDuration))
    } else ()
    return this.value
  }
  def updateVisualValue(): scala.Unit = {
    this.animateTime = 0
  }
  def getPercent(): scala.Float = {
    if (this.min == this.max) {
      return 0
    } else ()
    return (this.value - this.min) / (this.max - this.min)
  }
  def getVisualPercent(): scala.Float = {
    if (this.min == this.max) {
      return 0
    } else ()
    return this.visualInterpolation.apply((this.getVisualValue() - this.min) / (this.max - this.min))
  }
  def getBackgroundDrawable(): com.badlogic.gdx.scenes.scene2d.utils.Drawable = {
    if (this.disabled && (this.style.disabledBackground != null)) {
      return this.style.disabledBackground
    } else ()
    return this.style.background
  }
  def getKnobDrawable(): com.badlogic.gdx.scenes.scene2d.utils.Drawable = {
    if (this.disabled && (this.style.disabledKnob != null)) {
      return this.style.disabledKnob
    } else ()
    return this.style.knob
  }
  def getKnobBeforeDrawable(): com.badlogic.gdx.scenes.scene2d.utils.Drawable = {
    if (this.disabled && (this.style.disabledKnobBefore != null)) {
      return this.style.disabledKnobBefore
    } else ()
    return this.style.knobBefore
  }
  def getKnobAfterDrawable(): com.badlogic.gdx.scenes.scene2d.utils.Drawable = {
    if (this.disabled && (this.style.disabledKnobAfter != null)) {
      return this.style.disabledKnobAfter
    } else ()
    return this.style.knobAfter
  }
  def getKnobPosition(): scala.Float = {
    return this.position
  }
  def setValue(value$arg: scala.Float): scala.Boolean = {
    var value: scala.Float = value$arg
    value = this.clamp(this.round(value))
    val oldValue: scala.Float = this.value
    if (value == oldValue) {
      return false
    } else ()
    val oldVisualValue: scala.Float = this.getVisualValue()
    this.value = value
    if (this.programmaticChangeEvents) {
      val changeEvent: com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent = com.badlogic.gdx.scenes.scene2d.Actor.POOLS.obtain(classOf[com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent])
      val cancelled: scala.Boolean = this.fire(changeEvent)
      com.badlogic.gdx.scenes.scene2d.Actor.POOLS.free(changeEvent)
      if (cancelled) {
        this.value = oldValue
        return false
      } else ()
    } else ()
    if (this.animateDuration > 0) {
      this.animateFromValue = oldVisualValue
      this.animateTime = this.animateDuration
    } else ()
    return true
  }
  def round(value: scala.Float): scala.Float = {
    return java.lang.Math.round(value / this.stepSize) * this.stepSize
  }
  def clamp(value: scala.Float): scala.Float = {
    return com.badlogic.gdx.math.MathUtils.clamp(value, this.min, this.max)
  }
  def setRange(min: scala.Float, max: scala.Float): scala.Unit = {
    if (min > max) {
      throw new java.lang.IllegalArgumentException((("min must be <= max: " + min) + " <= ") + max)
    } else ()
    this.min = min
    this.max = max
    if (this.value < min) {
      this.setValue(min)
    } else {
      if (this.value > max) {
        this.setValue(max)
      } else ()
    }
  }
  def setStepSize(stepSize: scala.Float): scala.Unit = {
    if (stepSize <= 0) {
      throw new java.lang.IllegalArgumentException("steps must be > 0: " + stepSize)
    } else ()
    this.stepSize = stepSize
  }
  def getPrefWidth(): scala.Float = {
    if (this.vertical) {
      val knob: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.style.knob
      val bg: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.getBackgroundDrawable()
      return java.lang.Math.max(if (knob == null) 0 else knob.getMinWidth(), if (bg == null) 0 else bg.getMinWidth())
    } else {
      return 140
    }
  }
  def getPrefHeight(): scala.Float = {
    if (this.vertical) {
      return 140
    } else {
      val knob: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.style.knob
      val bg: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.getBackgroundDrawable()
      return java.lang.Math.max(if (knob == null) 0 else knob.getMinHeight(), if (bg == null) 0 else bg.getMinHeight())
    }
  }
  def getMinValue(): scala.Float = {
    return this.min
  }
  def getMaxValue(): scala.Float = {
    return this.max
  }
  def getStepSize(): scala.Float = {
    return this.stepSize
  }
  def setAnimateDuration(duration: scala.Float): scala.Unit = {
    this.animateDuration = duration
  }
  def setAnimateInterpolation(animateInterpolation: com.badlogic.gdx.math.Interpolation): scala.Unit = {
    if (animateInterpolation == null) {
      throw new java.lang.IllegalArgumentException("animateInterpolation cannot be null.")
    } else ()
    this.animateInterpolation = animateInterpolation
  }
  def setVisualInterpolation(interpolation: com.badlogic.gdx.math.Interpolation): scala.Unit = {
    this.visualInterpolation = interpolation
  }
  def setRound(round: scala.Boolean): scala.Unit = {
    this.round$field = round
  }
  def setDisabled(disabled: scala.Boolean): scala.Unit = {
    this.disabled = disabled
  }
  def isAnimating(): scala.Boolean = {
    return this.animateTime > 0
  }
  def isDisabled(): scala.Boolean = {
    return this.disabled
  }
  def isVertical(): scala.Boolean = {
    return this.vertical
  }
  def setProgrammaticChangeEvents(programmaticChangeEvents: scala.Boolean): scala.Unit = {
    this.programmaticChangeEvents = programmaticChangeEvents
  }
  def getProgrammaticChangeEvents(): scala.Boolean = {
    return this.programmaticChangeEvents
  }
}
object ProgressBar {
  export com.badlogic.gdx.scenes.scene2d.ui.Widget.{ProgressBarStyle => _, *}
  class ProgressBarStyle {
    var background: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var disabledBackground: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var knob: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var disabledKnob: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var knobBefore: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var disabledKnobBefore: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var knobAfter: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var disabledKnobAfter: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    def this(background: com.badlogic.gdx.scenes.scene2d.utils.Drawable, knob: com.badlogic.gdx.scenes.scene2d.utils.Drawable) = {
      this()
      this.background = background
      this.knob = knob
    }
    def this(style: com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle) = {
      this()
      this.background = style.background
      this.disabledBackground = style.disabledBackground
      this.knob = style.knob
      this.disabledKnob = style.disabledKnob
      this.knobBefore = style.knobBefore
      this.disabledKnobBefore = style.disabledKnobBefore
      this.knobAfter = style.knobAfter
      this.disabledKnobAfter = style.disabledKnobAfter
    }
  }
}