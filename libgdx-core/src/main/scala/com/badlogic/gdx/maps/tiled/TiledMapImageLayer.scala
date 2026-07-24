package com.badlogic.gdx.maps.tiled

class TiledMapImageLayer extends com.badlogic.gdx.maps.MapLayer {
  private var region: com.badlogic.gdx.graphics.g2d.TextureRegion = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureRegion]
  private var x: scala.Float = 0.0f
  private var y: scala.Float = 0.0f
  private var repeatX: scala.Boolean = false
  private var repeatY: scala.Boolean = false
  var supportsTransparency$field: scala.Boolean = false
  def this(region: com.badlogic.gdx.graphics.g2d.TextureRegion, x: scala.Float, y: scala.Float, repeatX: scala.Boolean, repeatY: scala.Boolean) = {
    this()
    this.region = region
    this.x = x
    this.y = y
    this.repeatX = repeatX
    this.repeatY = repeatY
    this.supportsTransparency$field = this.checkTransparencySupport(region)
  }
  private def checkTransparencySupport(region: com.badlogic.gdx.graphics.g2d.TextureRegion): scala.Boolean = {
    val format: com.badlogic.gdx.graphics.Pixmap#Format = region.getTexture().getTextureData().getFormat()
    return (format != null) && this.formatHasAlpha(format)
  }
  private def formatHasAlpha(format: com.badlogic.gdx.graphics.Pixmap#Format): scala.Boolean = {
    format match {
      case com.badlogic.gdx.graphics.Pixmap.Format.Alpha | com.badlogic.gdx.graphics.Pixmap.Format.LuminanceAlpha | com.badlogic.gdx.graphics.Pixmap.Format.RGBA4444 | com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888 => {
        return true
      }
      case _ => {
        return false
      }
    }
  }
  def supportsTransparency(): scala.Boolean = {
    return this.supportsTransparency$field
  }
  def getTextureRegion(): com.badlogic.gdx.graphics.g2d.TextureRegion = {
    return this.region
  }
  def setTextureRegion(region: com.badlogic.gdx.graphics.g2d.TextureRegion): scala.Unit = {
    this.region = region
  }
  def getX(): scala.Float = {
    return this.x
  }
  def setX(x: scala.Float): scala.Unit = {
    this.x = x
  }
  def getY(): scala.Float = {
    return this.y
  }
  def setY(y: scala.Float): scala.Unit = {
    this.y = y
  }
  def isRepeatX(): scala.Boolean = {
    return this.repeatX
  }
  def setRepeatX(repeatX: scala.Boolean): scala.Unit = {
    this.repeatX = repeatX
  }
  def isRepeatY(): scala.Boolean = {
    return this.repeatY
  }
  def setRepeatY(repeatY: scala.Boolean): scala.Unit = {
    this.repeatY = repeatY
  }
}