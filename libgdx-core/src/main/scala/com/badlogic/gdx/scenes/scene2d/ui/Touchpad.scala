package com.badlogic.gdx.scenes.scene2d.ui

class Touchpad extends com.badlogic.gdx.scenes.scene2d.ui.Widget with com.badlogic.gdx.scenes.scene2d.ui.Styleable[com.badlogic.gdx.scenes.scene2d.ui.Touchpad.TouchpadStyle] {
  private var style: com.badlogic.gdx.scenes.scene2d.ui.Touchpad.TouchpadStyle = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Touchpad.TouchpadStyle]
  var touched: scala.Boolean = false
  var resetOnTouchUp: scala.Boolean = true
  private var deadzoneRadius: scala.Float = 0.0f
  private final val knobBounds: com.badlogic.gdx.math.Circle = new com.badlogic.gdx.math.Circle(0, 0, 0)
  private final val touchBounds: com.badlogic.gdx.math.Circle = new com.badlogic.gdx.math.Circle(0, 0, 0)
  private final val deadzoneBounds: com.badlogic.gdx.math.Circle = new com.badlogic.gdx.math.Circle(0, 0, 0)
  private final val knobPosition: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  private final val knobPercent: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  def this(deadzoneRadius: scala.Float, style: com.badlogic.gdx.scenes.scene2d.ui.Touchpad.TouchpadStyle) = {
    this()
    if (deadzoneRadius < 0) {
      throw new java.lang.IllegalArgumentException("deadzoneRadius must be > 0")
    } else ()
    this.deadzoneRadius = deadzoneRadius
    this.knobPosition.set(this.getWidth() / 2.0f, this.getHeight() / 2.0f)
    this.setStyle(style)
    this.setSize(this.getPrefWidth(), this.getPrefHeight())
    this.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener())
  }
  def this(deadzoneRadius: scala.Float, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin, styleName: java.lang.String) = {
    this(deadzoneRadius, skin.get(styleName, classOf[com.badlogic.gdx.scenes.scene2d.ui.Touchpad.TouchpadStyle]))
  }
  def this(deadzoneRadius: scala.Float, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin) = {
    this(deadzoneRadius, skin.get(classOf[com.badlogic.gdx.scenes.scene2d.ui.Touchpad.TouchpadStyle]))
  }
  def calculatePositionAndValue(x: scala.Float, y: scala.Float, isTouchUp: scala.Boolean): scala.Unit = {
    val oldPositionX: scala.Float = this.knobPosition.x
    val oldPositionY: scala.Float = this.knobPosition.y
    val oldPercentX: scala.Float = this.knobPercent.x
    val oldPercentY: scala.Float = this.knobPercent.y
    val centerX: scala.Float = this.knobBounds.x
    val centerY: scala.Float = this.knobBounds.y
    this.knobPosition.set(centerX, centerY)
    this.knobPercent.set(0.0f, 0.0f)
    if (!isTouchUp) {
      if (!this.deadzoneBounds.contains(x, y)) {
        this.knobPercent.set((x - centerX) / this.knobBounds.radius, (y - centerY) / this.knobBounds.radius)
        val length: scala.Float = this.knobPercent.len()
        if (length > 1) {
          this.knobPercent.scl(1 / length)
        } else ()
        if (this.knobBounds.contains(x, y)) {
          this.knobPosition.set(x, y)
        } else {
          this.knobPosition.set(this.knobPercent).nor().scl(this.knobBounds.radius).add(this.knobBounds.x, this.knobBounds.y)
        }
      } else ()
    } else ()
    if ((oldPercentX != this.knobPercent.x) || (oldPercentY != this.knobPercent.y)) {
      val changeEvent: com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent = com.badlogic.gdx.scenes.scene2d.Actor.POOLS.obtain(classOf[com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent])
      if (this.fire(changeEvent)) {
        this.knobPercent.set(oldPercentX, oldPercentY)
        this.knobPosition.set(oldPositionX, oldPositionY)
      } else ()
      com.badlogic.gdx.scenes.scene2d.Actor.POOLS.free(changeEvent)
    } else ()
  }
  def setStyle(style: com.badlogic.gdx.scenes.scene2d.ui.Touchpad.TouchpadStyle): scala.Unit = {
    if (style == null) {
      throw new java.lang.IllegalArgumentException("style cannot be null")
    } else ()
    this.style = style
    this.invalidateHierarchy()
  }
  def getStyle(): com.badlogic.gdx.scenes.scene2d.ui.Touchpad.TouchpadStyle = {
    return this.style
  }
  def hit(x: scala.Float, y: scala.Float, touchable: scala.Boolean): com.badlogic.gdx.scenes.scene2d.Actor = {
    if (touchable && (this.getTouchable() != com.badlogic.gdx.scenes.scene2d.Touchable.enabled)) {
      return null
    } else ()
    if (!this.isVisible()) {
      return null
    } else ()
    return if (this.touchBounds.contains(x, y)) this else null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
  }
  def layout(): scala.Unit = {
    val halfWidth: scala.Float = this.getWidth() / 2
    val halfHeight: scala.Float = this.getHeight() / 2
    var radius: scala.Float = java.lang.Math.min(halfWidth, halfHeight)
    this.touchBounds.set(halfWidth, halfHeight, radius)
    if (this.style.knob != null) {
      radius = radius - (java.lang.Math.max(this.style.knob.getMinWidth(), this.style.knob.getMinHeight()) / 2)
    } else ()
    this.knobBounds.set(halfWidth, halfHeight, radius)
    this.deadzoneBounds.set(halfWidth, halfHeight, this.deadzoneRadius)
    this.knobPosition.set(halfWidth, halfHeight)
    this.knobPercent.set(0, 0)
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float): scala.Unit = {
    this.validate()
    val c: com.badlogic.gdx.graphics.Color = this.getColor()
    batch.setColor(c.r, c.g, c.b, c.a * parentAlpha)
    var x: scala.Float = this.getX()
    var y: scala.Float = this.getY()
    val w: scala.Float = this.getWidth()
    val h: scala.Float = this.getHeight()
    val bg: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.style.background
    if (bg != null) {
      bg.draw(batch, x, y, w, h)
    } else ()
    val knob: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.style.knob
    if (knob != null) {
      x = x + (this.knobPosition.x - (knob.getMinWidth() / 2.0f))
      y = y + (this.knobPosition.y - (knob.getMinHeight() / 2.0f))
      knob.draw(batch, x, y, knob.getMinWidth(), knob.getMinHeight())
    } else ()
  }
  def getPrefWidth(): scala.Float = {
    return if (this.style.background != null) this.style.background.getMinWidth() else 0
  }
  def getPrefHeight(): scala.Float = {
    return if (this.style.background != null) this.style.background.getMinHeight() else 0
  }
  def isTouched(): scala.Boolean = {
    return this.touched
  }
  def getResetOnTouchUp(): scala.Boolean = {
    return this.resetOnTouchUp
  }
  def setResetOnTouchUp(reset: scala.Boolean): scala.Unit = {
    this.resetOnTouchUp = reset
  }
  def setDeadzone(deadzoneRadius: scala.Float): scala.Unit = {
    if (deadzoneRadius < 0) {
      throw new java.lang.IllegalArgumentException("deadzoneRadius must be > 0")
    } else ()
    this.deadzoneRadius = deadzoneRadius
    this.invalidate()
  }
  def getKnobX(): scala.Float = {
    return this.knobPosition.x
  }
  def getKnobY(): scala.Float = {
    return this.knobPosition.y
  }
  def getKnobPercentX(): scala.Float = {
    return this.knobPercent.x
  }
  def getKnobPercentY(): scala.Float = {
    return this.knobPercent.y
  }
}
object Touchpad {
  export com.badlogic.gdx.scenes.scene2d.ui.Widget.{TouchpadStyle => _, *}
  class TouchpadStyle {
    var background: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var knob: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    def this(background: com.badlogic.gdx.scenes.scene2d.utils.Drawable, knob: com.badlogic.gdx.scenes.scene2d.utils.Drawable) = {
      this()
      this.background = background
      this.knob = knob
    }
    def this(style: com.badlogic.gdx.scenes.scene2d.ui.Touchpad.TouchpadStyle) = {
      this()
      this.background = style.background
      this.knob = style.knob
    }
  }
}