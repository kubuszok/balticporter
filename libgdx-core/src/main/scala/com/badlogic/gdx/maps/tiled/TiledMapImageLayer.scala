package com.badlogic.gdx.maps.tiled

class TiledMapImageLayer(region$p: com.badlogic.gdx.graphics.g2d.TextureRegion, x$p: scala.Float, y$p: scala.Float, repeatX$p: scala.Boolean, repeatY$p: scala.Boolean) extends com.badlogic.gdx.maps.MapLayer {
  private var region: com.badlogic.gdx.graphics.g2d.TextureRegion = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureRegion]
  private var x: scala.Float = 0.0f
  private var y: scala.Float = 0.0f
  private var repeatX: scala.Boolean = false
  private var repeatY: scala.Boolean = false
  var supportsTransparency$field: scala.Boolean = false
  this.region = region$p
  this.x = x$p
  this.y = y$p
  this.repeatX = repeatX$p
  this.repeatY = repeatY$p
  this.supportsTransparency$field = this.checkTransparencySupport(region$p)
  private def checkTransparencySupport(region: com.badlogic.gdx.graphics.g2d.TextureRegion): scala.Boolean = {
    val format: com.badlogic.gdx.graphics.Pixmap.Format = region.getTexture().getTextureData().getFormat()
    return (format != null) && this.formatHasAlpha(format)
  }
  private def formatHasAlpha(format: com.badlogic.gdx.graphics.Pixmap.Format): scala.Boolean = {
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