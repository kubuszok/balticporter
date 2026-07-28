package com.badlogic.gdx.scenes.scene2d.ui

class Slider(min$p: scala.Float, max$p: scala.Float, stepSize$p: scala.Float, vertical$p: scala.Boolean, style$p: com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle) extends com.badlogic.gdx.scenes.scene2d.ui.ProgressBar(min$p, max$p, stepSize$p, vertical$p, style$p) {
  var button: scala.Int = -1
  var draggingPointer: scala.Int = -1
  var mouseOver: scala.Boolean = false
  private var visualInterpolationInverse: com.badlogic.gdx.math.Interpolation = com.badlogic.gdx.math.Interpolation.linear
  private var snapValues: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  private var threshold: scala.Float = 0.0f
  def this(min: scala.Float, max: scala.Float, stepSize: scala.Float, vertical: scala.Boolean, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin) = {
    this(min, max, stepSize, vertical, skin.get("default-" + (if (vertical) "vertical" else "horizontal"), classOf[com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle]))
  }
  def this(min: scala.Float, max: scala.Float, stepSize: scala.Float, vertical: scala.Boolean, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin, styleName: java.lang.String) = {
    this(min, max, stepSize, vertical, skin.get(styleName, classOf[com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle]))
  }
  this.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
    override def touchDown(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, button: scala.Int): scala.Boolean = {
      if (disabled) {
        return false
      } else ()
      if ((Slider.this.button != (-1)) && (Slider.this.button != button)) {
        return false
      } else ()
      if (Slider.this.draggingPointer != (-1)) {
        return false
      } else ()
      Slider.this.draggingPointer = pointer
      Slider.this.calculatePositionAndValue(x, y)
      return true
    }
    override def touchUp(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, button: scala.Int): scala.Unit = {
      if (pointer != Slider.this.draggingPointer) {
        return
      } else ()
      Slider.this.draggingPointer = -1
      if (event.isTouchFocusCancel() || (!Slider.this.calculatePositionAndValue(x, y))) {
        val changeEvent: com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent = com.badlogic.gdx.scenes.scene2d.Actor.POOLS.obtain(classOf[com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent])
        Slider.this.fire(changeEvent)
        com.badlogic.gdx.scenes.scene2d.Actor.POOLS.free(changeEvent)
      } else ()
    }
    override def touchDragged(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int): scala.Unit = {
      Slider.this.calculatePositionAndValue(x, y)
    }
    override def enter(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, fromActor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
      if (pointer == (-1)) {
        Slider.this.mouseOver = true
      } else ()
    }
    override def exit(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, toActor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
      if (pointer == (-1)) {
        Slider.this.mouseOver = false
      } else ()
    }
  })
  override def getStyle(): ?T = {
    return super.getStyle().asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle]
  }
  def isOver(): scala.Boolean = {
    return this.mouseOver
  }
  @com.badlogic.gdx.utils.Null
  override def getBackgroundDrawable(): com.badlogic.gdx.scenes.scene2d.utils.Drawable = {
    val style: com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle = super.getStyle().asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle]
    if (disabled && (style.disabledBackground != null)) {
      return style.disabledBackground
    } else ()
    if (this.isDragging() && (style.backgroundDown != null)) {
      return style.backgroundDown
    } else ()
    if (this.mouseOver && (style.backgroundOver != null)) {
      return style.backgroundOver
    } else ()
    return style.background
  }
  @com.badlogic.gdx.utils.Null
  override def getKnobDrawable(): com.badlogic.gdx.scenes.scene2d.utils.Drawable = {
    val style: com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle = super.getStyle().asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle]
    if (disabled && (style.disabledKnob != null)) {
      return style.disabledKnob
    } else ()
    if (this.isDragging() && (style.knobDown != null)) {
      return style.knobDown
    } else ()
    if (this.mouseOver && (style.knobOver != null)) {
      return style.knobOver
    } else ()
    return style.knob
  }
  override def getKnobBeforeDrawable(): com.badlogic.gdx.scenes.scene2d.utils.Drawable = {
    val style: com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle = super.getStyle().asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle]
    if (disabled && (style.disabledKnobBefore != null)) {
      return style.disabledKnobBefore
    } else ()
    if (this.isDragging() && (style.knobBeforeDown != null)) {
      return style.knobBeforeDown
    } else ()
    if (this.mouseOver && (style.knobBeforeOver != null)) {
      return style.knobBeforeOver
    } else ()
    return style.knobBefore
  }
  override def getKnobAfterDrawable(): com.badlogic.gdx.scenes.scene2d.utils.Drawable = {
    val style: com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle = super.getStyle().asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle]
    if (disabled && (style.disabledKnobAfter != null)) {
      return style.disabledKnobAfter
    } else ()
    if (this.isDragging() && (style.knobAfterDown != null)) {
      return style.knobAfterDown
    } else ()
    if (this.mouseOver && (style.knobAfterOver != null)) {
      return style.knobAfterOver
    } else ()
    return style.knobAfter
  }
  def calculatePositionAndValue(x: scala.Float, y: scala.Float): scala.Boolean = {
    val style: com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle = this.getStyle()
    val knob: com.badlogic.gdx.scenes.scene2d.utils.Drawable = style.knob
    val bg: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.getBackgroundDrawable()
    var value: scala.Float = 0.0f
    val oldPosition: scala.Float = position
    val min: scala.Float = this.getMinValue()
    val max: scala.Float = this.getMaxValue()
    if (vertical) {
      val height: scala.Float = (this.getHeight() - bg.getTopHeight()) - bg.getBottomHeight()
      val knobHeight: scala.Float = if (knob == null) 0 else knob.getMinHeight()
      position = (y - bg.getBottomHeight()) - (knobHeight * 0.5f)
      value = min + ((max - min) * this.visualInterpolationInverse.apply(position / (height - knobHeight)))
      position = java.lang.Math.max(java.lang.Math.min(0, bg.getBottomHeight()), position)
      position = java.lang.Math.min(height - knobHeight, position)
    } else {
      val width: scala.Float = (this.getWidth() - bg.getLeftWidth()) - bg.getRightWidth()
      val knobWidth: scala.Float = if (knob == null) 0 else knob.getMinWidth()
      position = (x - bg.getLeftWidth()) - (knobWidth * 0.5f)
      value = min + ((max - min) * this.visualInterpolationInverse.apply(position / (width - knobWidth)))
      position = java.lang.Math.max(java.lang.Math.min(0, bg.getLeftWidth()), position)
      position = java.lang.Math.min(width - knobWidth, position)
    }
    val oldValue: scala.Float = value
    if ((!com.badlogic.gdx.Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_LEFT)) && (!com.badlogic.gdx.Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_RIGHT))) {
      value = this.snap(value)
    } else ()
    val valueSet: scala.Boolean = this.setValue(value)
    if (value == oldValue) {
      position = oldPosition
    } else ()
    return valueSet
  }
  def snap(value: scala.Float): scala.Float = {
    if ((this.snapValues == null) || (this.snapValues.length == 0)) {
      return value
    } else ()
    var bestDiff: scala.Float = -1
    var bestValue: scala.Float = 0;
    { var i: scala.Int = 0; while (i < this.snapValues.length) { {
      val snapValue: scala.Float = this.snapValues(i)
      val diff: scala.Float = java.lang.Math.abs(value - snapValue)
      if (diff <= this.threshold) {
        if ((bestDiff == (-1)) || (diff < bestDiff)) {
          bestDiff = diff
          bestValue = snapValue
        } else ()
      } else ()
    }; i = i + 1 } }
    return if (bestDiff == (-1)) value else bestValue
  }
  def setSnapToValues(threshold: scala.Float, values: scala.Array[scala.Float]): scala.Unit = {
    if ((values != null) && (values.length == 0)) {
      throw new java.lang.IllegalArgumentException("values cannot be empty.")
    } else ()
    this.snapValues = values
    this.threshold = threshold
  }
  @java.lang.Deprecated
  def setSnapToValues(values: scala.Array[scala.Float], threshold: scala.Float): scala.Unit = {
    this.setSnapToValues(threshold, values)
  }
  @com.badlogic.gdx.utils.Null
  def getSnapToValues(): scala.Array[scala.Float] = {
    return this.snapValues
  }
  def getSnapToValuesThreshold(): scala.Float = {
    return this.threshold
  }
  def isDragging(): scala.Boolean = {
    return this.draggingPointer != (-1)
  }
  def setButton(button: scala.Int): scala.Unit = {
    this.button = button
  }
  def setVisualInterpolationInverse(interpolation: com.badlogic.gdx.math.Interpolation): scala.Unit = {
    this.visualInterpolationInverse = interpolation
  }
  def setVisualPercent(percent: scala.Float): scala.Unit = {
    this.setValue(min + ((max - min) * this.visualInterpolationInverse.apply(percent)))
  }
}
object Slider {
  export com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.{SliderStyle => _, *}
  class SliderStyle extends com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle {
    var backgroundOver: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var backgroundDown: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var knobOver: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var knobDown: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var knobBeforeOver: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var knobBeforeDown: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var knobAfterOver: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var knobAfterDown: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    def this(background: com.badlogic.gdx.scenes.scene2d.utils.Drawable, knob: com.badlogic.gdx.scenes.scene2d.utils.Drawable) = {
      this()
      this.background = background
      this.knob = knob
    }
    def this(style: com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle) = {
      this()
      this.background = style.background
      this.disabledBackground = style.disabledBackground
      this.knob = style.knob
      this.disabledKnob = style.disabledKnob
      this.knobBefore = style.knobBefore
      this.disabledKnobBefore = style.disabledKnobBefore
      this.knobAfter = style.knobAfter
      this.disabledKnobAfter = style.disabledKnobAfter
      this.backgroundOver = style.backgroundOver
      this.backgroundDown = style.backgroundDown
      this.knobOver = style.knobOver
      this.knobDown = style.knobDown
      this.knobBeforeOver = style.knobBeforeOver
      this.knobBeforeDown = style.knobBeforeDown
      this.knobAfterOver = style.knobAfterOver
      this.knobAfterDown = style.knobAfterDown
    }
  }
}