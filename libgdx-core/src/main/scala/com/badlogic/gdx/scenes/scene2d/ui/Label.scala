package com.badlogic.gdx.scenes.scene2d.ui

class Label(text$p: java.lang.CharSequence, style$p: com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle) extends com.badlogic.gdx.scenes.scene2d.ui.Widget with com.badlogic.gdx.scenes.scene2d.ui.Styleable[com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle] {
  private var style: com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle]
  final val layout$field: com.badlogic.gdx.graphics.g2d.GlyphLayout = new com.badlogic.gdx.graphics.g2d.GlyphLayout()
  private var prefWidth: scala.Float = 0.0f
  private var prefHeight: scala.Float = 0.0f
  private final val text: com.badlogic.gdx.utils.CharArray = new com.badlogic.gdx.utils.CharArray()
  private var intValue: scala.Int = java.lang.Integer.MIN_VALUE
  private var cache: com.badlogic.gdx.graphics.g2d.BitmapFontCache = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.BitmapFontCache]
  private var labelAlign: scala.Int = com.badlogic.gdx.utils.Align.left
  private var lineAlign: scala.Int = com.badlogic.gdx.utils.Align.left
  private var wrap: scala.Boolean = false
  private var lastPrefHeight: scala.Float = 0.0f
  private var prefSizeInvalid: scala.Boolean = true
  private var fontScaleX: scala.Float = 1
  private var fontScaleY: scala.Float = 1
  private var fontScaleChanged: scala.Boolean = false
  private var ellipsis: java.lang.String = null.asInstanceOf[java.lang.String]
  def this(text: java.lang.CharSequence, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin) = {
    this(text, skin.get(classOf[com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle]))
  }
  def this(text: java.lang.CharSequence, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin, styleName: java.lang.String) = {
    this(text, skin.get(styleName, classOf[com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle]))
  }
  def this(text: java.lang.CharSequence, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin, fontName: java.lang.String, color: com.badlogic.gdx.graphics.Color) = {
    this(text, new com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle(skin.getFont(fontName), color))
  }
  def this(text: java.lang.CharSequence, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin, fontName: java.lang.String, colorName: java.lang.String) = {
    this(text, new com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle(skin.getFont(fontName), skin.getColor(colorName)))
  }
  if (text$p != null) {
    this.text.append(text$p)
  } else ()
  this.setStyle(style$p)
  if ((text$p != null) && (text$p.length() > 0)) {
    this.setSize(this.getPrefWidth(), this.getPrefHeight())
  } else ()
  override def setStyle(style: com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle): scala.Unit = {
    if (style == null) {
      throw new java.lang.IllegalArgumentException("style cannot be null.")
    } else ()
    if (style.font == null) {
      throw new java.lang.IllegalArgumentException("Missing LabelStyle font.")
    } else ()
    this.style = style
    this.cache = style.font.newFontCache()
    this.invalidateHierarchy()
  }
  override def getStyle(): com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle = {
    return this.style
  }
  def setText(value: scala.Int): scala.Boolean = {
    if (this.intValue == value) {
      return false
    } else ()
    this.text.clear()
    (this.text.append: (scala.Int) => com.badlogic.gdx.utils.CharArray)(value)
    this.intValue = value
    this.invalidateHierarchy()
    return true
  }
  def setText(newText: java.lang.CharSequence): scala.Unit = {
    if (newText == null) {
      if (this.text.size == 0) {
        return
      } else ()
      this.text.clear()
    } else {
      if (newText.isInstanceOf[com.badlogic.gdx.utils.CharArray]) {
        if (this.text.equals(newText)) {
          return
        } else ()
        this.text.clear()
        this.text.append(newText.asInstanceOf[com.badlogic.gdx.utils.CharArray])
      } else {
        if (this.textEquals(newText)) {
          return
        } else ()
        this.text.clear()
        this.text.append(newText)
      }
    }
    this.intValue = java.lang.Integer.MIN_VALUE
    this.invalidateHierarchy()
  }
  def textEquals(other: java.lang.CharSequence): scala.Boolean = {
    val length: scala.Int = this.text.size
    val chars: scala.Array[scala.Char] = this.text.items
    if (length != other.length()) {
      return false
    } else ();
    { var i: scala.Int = 0; while (i < length) { {
      if (chars(i) != other.charAt(i)) {
        return false
      } else ()
    }; i = i + 1 } }
    return true
  }
  def getText(): com.badlogic.gdx.utils.CharArray = {
    return this.text
  }
  override def invalidate(): scala.Unit = {
    super.invalidate()
    this.prefSizeInvalid = true
  }
  private def scaleAndComputePrefSize(): scala.Unit = {
    val font: com.badlogic.gdx.graphics.g2d.BitmapFont = this.cache.getFont()
    val oldScaleX: scala.Float = font.getScaleX()
    val oldScaleY: scala.Float = font.getScaleY()
    if (this.fontScaleChanged) {
      font.getData().setScale(this.fontScaleX, this.fontScaleY)
    } else ()
    this.computePrefSize(Label.prefSizeLayout)
    if (this.fontScaleChanged) {
      font.getData().setScale(oldScaleX, oldScaleY)
    } else ()
  }
  def computePrefSize(layout: com.badlogic.gdx.graphics.g2d.GlyphLayout): scala.Unit = {
    this.prefSizeInvalid = false
    if (this.wrap && (this.ellipsis == null)) {
      var width: scala.Float = this.getWidth()
      if (this.style.background != null) {
        width = (java.lang.Math.max(width, this.style.background.getMinWidth()) - this.style.background.getLeftWidth()) - this.style.background.getRightWidth()
      } else ()
      layout.setText(this.cache.getFont(), this.text, com.badlogic.gdx.graphics.Color.WHITE, width, com.badlogic.gdx.utils.Align.left, true)
    } else {
      layout.setText(this.cache.getFont(), this.text)
    }
    this.prefWidth = layout.width
    this.prefHeight = layout.height
  }
  override def layout(): scala.Unit = {
    val font: com.badlogic.gdx.graphics.g2d.BitmapFont = this.cache.getFont()
    val oldScaleX: scala.Float = font.getScaleX()
    val oldScaleY: scala.Float = font.getScaleY()
    if (this.fontScaleChanged) {
      font.getData().setScale(this.fontScaleX, this.fontScaleY)
    } else ()
    val wrap: scala.Boolean = this.wrap && (this.ellipsis == null)
    if (wrap) {
      val prefHeight: scala.Float = this.getPrefHeight()
      if (prefHeight != this.lastPrefHeight) {
        this.lastPrefHeight = prefHeight
        this.invalidateHierarchy()
      } else ()
    } else ()
    var width: scala.Float = this.getWidth()
    var height: scala.Float = this.getHeight()
    val background: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.style.background
    var x: scala.Float = 0
    var y: scala.Float = 0
    if (background != null) {
      x = background.getLeftWidth()
      y = background.getBottomHeight()
      width = width - (background.getLeftWidth() + background.getRightWidth())
      height = height - (background.getBottomHeight() + background.getTopHeight())
    } else ()
    val layout: com.badlogic.gdx.graphics.g2d.GlyphLayout = this.layout$field
    var textWidth: scala.Float = 0.0f
    var textHeight: scala.Float = 0.0f
    if (wrap || (this.text.indexOf("\n") != (-1))) {
      layout.setText(font, this.text, 0, this.text.size, com.badlogic.gdx.graphics.Color.WHITE, width, this.lineAlign, wrap, this.ellipsis)
      textWidth = layout.width
      textHeight = layout.height
      if ((this.labelAlign & com.badlogic.gdx.utils.Align.left) == 0) {
        if ((this.labelAlign & com.badlogic.gdx.utils.Align.right) != 0) {
          x = x + (width - textWidth)
        } else {
          x = x + ((width - textWidth) / 2)
        }
      } else ()
    } else {
      textWidth = width
      textHeight = font.getData().capHeight
    }
    if ((this.labelAlign & com.badlogic.gdx.utils.Align.top) != 0) {
      y = y + (if (this.cache.getFont().isFlipped()) 0 else height - textHeight)
      y = y + this.style.font.getDescent()
    } else {
      if ((this.labelAlign & com.badlogic.gdx.utils.Align.bottom) != 0) {
        y = y + (if (this.cache.getFont().isFlipped()) height - textHeight else 0)
        y = y - this.style.font.getDescent()
      } else {
        y = y + ((height - textHeight) / 2)
      }
    }
    if (!this.cache.getFont().isFlipped()) {
      y = y + textHeight
    } else ()
    layout.setText(font, this.text, 0, this.text.size, com.badlogic.gdx.graphics.Color.WHITE, textWidth, this.lineAlign, wrap, this.ellipsis)
    this.cache.setText(layout, x, y)
    if (this.fontScaleChanged) {
      font.getData().setScale(oldScaleX, oldScaleY)
    } else ()
  }
  override def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float): scala.Unit = {
    this.validate()
    val color: com.badlogic.gdx.graphics.Color = Label.tempColor.set(this.getColor())
    color.a = color.a * parentAlpha
    if (this.style.background != null) {
      batch.setColor(color.r, color.g, color.b, color.a)
      this.style.background.draw(batch, this.getX(), this.getY(), this.getWidth(), this.getHeight())
    } else ()
    if (this.style.fontColor != null) {
      color.mul(this.style.fontColor)
    } else ()
    this.cache.tint(color)
    this.cache.setPosition(this.getX(), this.getY())
    this.cache.draw(batch)
  }
  override def getPrefWidth(): scala.Float = {
    if (this.wrap) {
      return 0
    } else ()
    if (this.prefSizeInvalid) {
      this.scaleAndComputePrefSize()
    } else ()
    var width: scala.Float = this.prefWidth
    val background: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.style.background
    if (background != null) {
      width = java.lang.Math.max((width + background.getLeftWidth()) + background.getRightWidth(), background.getMinWidth())
    } else ()
    return width
  }
  override def getPrefHeight(): scala.Float = {
    if (this.prefSizeInvalid) {
      this.scaleAndComputePrefSize()
    } else ()
    var descentScaleCorrection: scala.Float = 1
    if (this.fontScaleChanged) {
      descentScaleCorrection = this.fontScaleY / this.style.font.getScaleY()
    } else ()
    var height: scala.Float = this.prefHeight - ((this.style.font.getDescent() * descentScaleCorrection) * 2)
    val background: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.style.background
    if (background != null) {
      height = java.lang.Math.max((height + background.getTopHeight()) + background.getBottomHeight(), background.getMinHeight())
    } else ()
    return height
  }
  def getGlyphLayout(): com.badlogic.gdx.graphics.g2d.GlyphLayout = {
    return this.layout$field
  }
  def setWrap(wrap: scala.Boolean): scala.Unit = {
    this.wrap = wrap
    this.invalidateHierarchy()
  }
  def getWrap(): scala.Boolean = {
    return this.wrap
  }
  def getLabelAlign(): scala.Int = {
    return this.labelAlign
  }
  def getLineAlign(): scala.Int = {
    return this.lineAlign
  }
  def setAlignment(alignment: scala.Int): scala.Unit = {
    this.setAlignment(alignment, alignment)
  }
  def setAlignment(labelAlign: scala.Int, lineAlign: scala.Int): scala.Unit = {
    this.labelAlign = labelAlign
    if ((lineAlign & com.badlogic.gdx.utils.Align.left) != 0) {
      this.lineAlign = com.badlogic.gdx.utils.Align.left
    } else {
      if ((lineAlign & com.badlogic.gdx.utils.Align.right) != 0) {
        this.lineAlign = com.badlogic.gdx.utils.Align.right
      } else {
        this.lineAlign = com.badlogic.gdx.utils.Align.center
      }
    }
    this.invalidate()
  }
  def setFontScale(fontScale: scala.Float): scala.Unit = {
    this.setFontScale(fontScale, fontScale)
  }
  def setFontScale(fontScaleX: scala.Float, fontScaleY: scala.Float): scala.Unit = {
    this.fontScaleChanged = true
    this.fontScaleX = fontScaleX
    this.fontScaleY = fontScaleY
    this.invalidateHierarchy()
  }
  def getFontScaleX(): scala.Float = {
    return this.fontScaleX
  }
  def setFontScaleX(fontScaleX: scala.Float): scala.Unit = {
    this.setFontScale(fontScaleX, this.fontScaleY)
  }
  def getFontScaleY(): scala.Float = {
    return this.fontScaleY
  }
  def setFontScaleY(fontScaleY: scala.Float): scala.Unit = {
    this.setFontScale(this.fontScaleX, fontScaleY)
  }
  def setEllipsis(ellipsis: java.lang.String): scala.Unit = {
    this.ellipsis = ellipsis
  }
  def setEllipsis(ellipsis: scala.Boolean): scala.Unit = {
    if (ellipsis) {
      this.ellipsis = "..."
    } else {
      this.ellipsis = null
    }
  }
  def getBitmapFontCache(): com.badlogic.gdx.graphics.g2d.BitmapFontCache = {
    return this.cache
  }
  override def toString(): java.lang.String = {
    val name: java.lang.String = this.getName()
    if (name != null) {
      return name
    } else ()
    var className: java.lang.String = this.getClass().getName()
    val dotIndex: scala.Int = className.lastIndexOf('.')
    if (dotIndex != (-1)) {
      className = className.substring(dotIndex + 1)
    } else ()
    return (((if (className.indexOf('$') != (-1)) "Label " else "") + className) + ": ") + this.text
  }
}
object Label {
  export com.badlogic.gdx.scenes.scene2d.ui.Widget.{LabelStyle => _, prefSizeLayout => _, tempColor => _, *}
  private final val tempColor: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color()
  private final val prefSizeLayout: com.badlogic.gdx.graphics.g2d.GlyphLayout = new com.badlogic.gdx.graphics.g2d.GlyphLayout()
  class LabelStyle {
    var font: com.badlogic.gdx.graphics.g2d.BitmapFont = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.BitmapFont]
    var fontColor: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
    var background: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    def this(font: com.badlogic.gdx.graphics.g2d.BitmapFont, fontColor: com.badlogic.gdx.graphics.Color) = {
      this()
      this.font = font
      this.fontColor = fontColor
    }
    def this(style: com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle) = {
      this()
      this.font = style.font
      if (style.fontColor != null) {
        this.fontColor = new com.badlogic.gdx.graphics.Color(style.fontColor)
      } else ()
      this.background = style.background
    }
  }
}