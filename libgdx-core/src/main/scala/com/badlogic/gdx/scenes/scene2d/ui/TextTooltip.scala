package com.badlogic.gdx.scenes.scene2d.ui

class TextTooltip(text: java.lang.String, manager$p: com.badlogic.gdx.scenes.scene2d.ui.TooltipManager, style$p: com.badlogic.gdx.scenes.scene2d.ui.TextTooltip.TextTooltipStyle) extends com.badlogic.gdx.scenes.scene2d.ui.Tooltip[com.badlogic.gdx.scenes.scene2d.ui.Label](null, manager$p) with com.badlogic.gdx.scenes.scene2d.ui.Styleable[com.badlogic.gdx.scenes.scene2d.ui.TextTooltip.TextTooltipStyle] {
  private var style: com.badlogic.gdx.scenes.scene2d.ui.TextTooltip.TextTooltipStyle = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.TextTooltip.TextTooltipStyle]
  def this(text: java.lang.String, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin) = {
    this(text, com.badlogic.gdx.scenes.scene2d.ui.TooltipManager.getInstance(), skin.get(classOf[com.badlogic.gdx.scenes.scene2d.ui.TextTooltip.TextTooltipStyle]))
  }
  def this(text: java.lang.String, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin, styleName: java.lang.String) = {
    this(text, com.badlogic.gdx.scenes.scene2d.ui.TooltipManager.getInstance(), skin.get(styleName, classOf[com.badlogic.gdx.scenes.scene2d.ui.TextTooltip.TextTooltipStyle]))
  }
  def this(text: java.lang.String, style: com.badlogic.gdx.scenes.scene2d.ui.TextTooltip.TextTooltipStyle) = {
    this(text, com.badlogic.gdx.scenes.scene2d.ui.TooltipManager.getInstance(), style)
  }
  def this(text: java.lang.String, manager: com.badlogic.gdx.scenes.scene2d.ui.TooltipManager, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin) = {
    this(text, manager, skin.get(classOf[com.badlogic.gdx.scenes.scene2d.ui.TextTooltip.TextTooltipStyle]))
  }
  def this(text: java.lang.String, manager: com.badlogic.gdx.scenes.scene2d.ui.TooltipManager, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin, styleName: java.lang.String) = {
    this(text, manager, skin.get(styleName, classOf[com.badlogic.gdx.scenes.scene2d.ui.TextTooltip.TextTooltipStyle]))
  }
  container.setActor(this.newLabel(text, style$p.label))
  this.setStyle(style$p)
  def newLabel(text: java.lang.String, style: com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle): com.badlogic.gdx.scenes.scene2d.ui.Label = {
    return new com.badlogic.gdx.scenes.scene2d.ui.Label(text, style)
  }
  override def setStyle(style: com.badlogic.gdx.scenes.scene2d.ui.TextTooltip.TextTooltipStyle): scala.Unit = {
    if (style == null) {
      throw new java.lang.NullPointerException("style cannot be null")
    } else ()
    this.style = style
    container.setBackground(style.background)
    container.maxWidth(style.wrapWidth)
    val wrap: scala.Boolean = style.wrapWidth != 0
    container.fill(wrap)
    val label: com.badlogic.gdx.scenes.scene2d.ui.Label = container.getActor()
    label.setStyle(style.label)
    label.setWrap(wrap)
  }
  override def getStyle(): com.badlogic.gdx.scenes.scene2d.ui.TextTooltip.TextTooltipStyle = {
    return this.style
  }
}
object TextTooltip {
  export com.badlogic.gdx.scenes.scene2d.ui.Tooltip.{TextTooltipStyle => _, *}
  class TextTooltipStyle {
    var label: com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle]
    var background: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var wrapWidth: scala.Float = 0.0f
    def this(label: com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle, background: com.badlogic.gdx.scenes.scene2d.utils.Drawable) = {
      this()
      this.label = label
      this.background = background
    }
    def this(style: com.badlogic.gdx.scenes.scene2d.ui.TextTooltip.TextTooltipStyle) = {
      this()
      this.label = new com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle(style.label)
      this.background = style.background
      this.wrapWidth = style.wrapWidth
    }
  }
}