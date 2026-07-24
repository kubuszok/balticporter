package com.badlogic.gdx.scenes.scene2d.ui

class Slider extends com.badlogic.gdx.scenes.scene2d.ui.ProgressBar {
  var button: scala.Int = -1
  var draggingPointer: scala.Int = -1
  var mouseOver: scala.Boolean = false
  private var visualInterpolationInverse: com.badlogic.gdx.math.Interpolation = com.badlogic.gdx.math.Interpolation.linear
  private var snapValues: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  private var threshold: scala.Float = 0.0f
  def this(min: scala.Float, max: scala.Float, stepSize: scala.Float, vertical: scala.Boolean, style: com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle) = {
    this()
    this.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener())
  }
  def this(min: scala.Float, max: scala.Float, stepSize: scala.Float, vertical: scala.Boolean, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin, styleName: java.lang.String) = {
    this(min, max, stepSize, vertical, skin.get(styleName, classOf[com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle]))
  }
  def this(min: scala.Float, max: scala.Float, stepSize: scala.Float, vertical: scala.Boolean, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin) = {
    this(min, max, stepSize, vertical, skin.get("default-" + (if (vertical) "vertical" else "horizontal"), classOf[com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle]))
  }
  def getStyle(): com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle = {
    return super.getStyle().asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle]
  }
  def isOver(): scala.Boolean = {
    return this.mouseOver
  }
  def getBackgroundDrawable(): com.badlogic.gdx.scenes.scene2d.utils.Drawable = {
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
  def getKnobDrawable(): com.badlogic.gdx.scenes.scene2d.utils.Drawable = {
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
  def getKnobBeforeDrawable(): com.badlogic.gdx.scenes.scene2d.utils.Drawable = {
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
  def getKnobAfterDrawable(): com.badlogic.gdx.scenes.scene2d.utils.Drawable = {
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
  def setSnapToValues(values: scala.Array[scala.Float], threshold: scala.Float): scala.Unit = {
    this.setSnapToValues(threshold, values)
  }
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
    }
    def this(style: com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle) = {
      this()
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