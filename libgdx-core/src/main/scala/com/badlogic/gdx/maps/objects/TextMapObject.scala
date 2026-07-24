package com.badlogic.gdx.maps.objects

class TextMapObject extends com.badlogic.gdx.maps.MapObject {
  private var rotation: scala.Float = 0.0f
  private var text: java.lang.String = ""
  private var pixelSize: scala.Int = 16
  private var fontFamily: java.lang.String = ""
  private var bold: scala.Boolean = false
  private var italic: scala.Boolean = false
  private var underline: scala.Boolean = false
  private var strikeout: scala.Boolean = false
  private var kerning: scala.Boolean = true
  private var wrap: scala.Boolean = true
  private var horizontalAlign: java.lang.String = "left"
  private var verticalAlign: java.lang.String = "top"
  private var rectangle: com.badlogic.gdx.math.Rectangle = null.asInstanceOf[com.badlogic.gdx.math.Rectangle]
  def this(x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float, text: java.lang.String) = {
    this()
    this.rectangle = new com.badlogic.gdx.math.Rectangle(x, y, width, height)
    this.text = text
  }
  def getRectangle(): com.badlogic.gdx.math.Rectangle = {
    return this.rectangle
  }
  def getX(): scala.Float = {
    return this.rectangle.getX()
  }
  def getY(): scala.Float = {
    return this.rectangle.getY()
  }
  def getWidth(): scala.Float = {
    return this.rectangle.getWidth()
  }
  def getHeight(): scala.Float = {
    return this.rectangle.getHeight()
  }
  def getRotation(): scala.Float = {
    return this.rotation
  }
  def setRotation(rotation: scala.Float): scala.Unit = {
    this.rotation = rotation
  }
  def getText(): java.lang.String = {
    return this.text
  }
  def setText(text: java.lang.String): scala.Unit = {
    this.text = text
  }
  def getHorizontalAlign(): java.lang.String = {
    return this.horizontalAlign
  }
  def setHorizontalAlign(horizontalAlign: java.lang.String): scala.Unit = {
    this.horizontalAlign = horizontalAlign
  }
  def getVerticalAlign(): java.lang.String = {
    return this.verticalAlign
  }
  def setVerticalAlign(verticalAlign: java.lang.String): scala.Unit = {
    this.verticalAlign = verticalAlign
  }
  def getPixelSize(): scala.Int = {
    return this.pixelSize
  }
  def setPixelSize(pixelSize: scala.Int): scala.Unit = {
    this.pixelSize = pixelSize
  }
  def getFontFamily(): java.lang.String = {
    return this.fontFamily
  }
  def setFontFamily(fontFamily: java.lang.String): scala.Unit = {
    this.fontFamily = fontFamily
  }
  def isBold(): scala.Boolean = {
    return this.bold
  }
  def setBold(bold: scala.Boolean): scala.Unit = {
    this.bold = bold
  }
  def isItalic(): scala.Boolean = {
    return this.italic
  }
  def setItalic(italic: scala.Boolean): scala.Unit = {
    this.italic = italic
  }
  def isUnderline(): scala.Boolean = {
    return this.underline
  }
  def setUnderline(underline: scala.Boolean): scala.Unit = {
    this.underline = underline
  }
  def isStrikeout(): scala.Boolean = {
    return this.strikeout
  }
  def setStrikeout(strikeout: scala.Boolean): scala.Unit = {
    this.strikeout = strikeout
  }
  def isKerning(): scala.Boolean = {
    return this.kerning
  }
  def setKerning(kerning: scala.Boolean): scala.Unit = {
    this.kerning = kerning
  }
  def isWrap(): scala.Boolean = {
    return this.wrap
  }
  def setWrap(wrap: scala.Boolean): scala.Unit = {
    this.wrap = wrap
  }
}