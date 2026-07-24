package com.badlogic.gdx.maps

class MapLayer {
  private var name: java.lang.String = ""
  private var opacity: scala.Float = 1.0f
  private var tintColor: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color(com.badlogic.gdx.graphics.Color.WHITE)
  private var tempColor: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color(com.badlogic.gdx.graphics.Color.WHITE)
  private var visible: scala.Boolean = true
  private var offsetX: scala.Float = 0.0f
  private var offsetY: scala.Float = 0.0f
  private var renderOffsetX: scala.Float = 0.0f
  private var renderOffsetY: scala.Float = 0.0f
  private var parallaxX: scala.Float = 1
  private var parallaxY: scala.Float = 1
  private var renderOffsetDirty: scala.Boolean = true
  private var parent: MapLayer = null.asInstanceOf[MapLayer]
  private var objects: com.badlogic.gdx.maps.MapObjects = new com.badlogic.gdx.maps.MapObjects()
  private var properties: com.badlogic.gdx.maps.MapProperties = new com.badlogic.gdx.maps.MapProperties()
  def getName(): java.lang.String = {
    return this.name
  }
  def setName(name: java.lang.String): scala.Unit = {
    this.name = name
  }
  def getOpacity(): scala.Float = {
    if (this.parent != null) {
      return this.opacity * this.parent.getOpacity()
    } else {
      return this.opacity
    }
  }
  def setOpacity(opacity: scala.Float): scala.Unit = {
    this.opacity = opacity
  }
  def getCombinedTintColor(): com.badlogic.gdx.graphics.Color = {
    if (this.parent != null) {
      return this.tempColor.set(this.tintColor).mul(this.parent.getCombinedTintColor())
    } else {
      return this.tempColor.set(this.tintColor)
    }
  }
  def getTintColor(): com.badlogic.gdx.graphics.Color = {
    return this.tintColor
  }
  def setTintColor(tintColor: com.badlogic.gdx.graphics.Color): scala.Unit = {
    this.tintColor.set(tintColor)
  }
  def getOffsetX(): scala.Float = {
    return this.offsetX
  }
  def setOffsetX(offsetX: scala.Float): scala.Unit = {
    this.offsetX = offsetX
    this.invalidateRenderOffset()
  }
  def getOffsetY(): scala.Float = {
    return this.offsetY
  }
  def setOffsetY(offsetY: scala.Float): scala.Unit = {
    this.offsetY = offsetY
    this.invalidateRenderOffset()
  }
  def getParallaxX(): scala.Float = {
    return this.parallaxX
  }
  def setParallaxX(parallaxX: scala.Float): scala.Unit = {
    this.parallaxX = parallaxX
  }
  def getParallaxY(): scala.Float = {
    return this.parallaxY
  }
  def setParallaxY(parallaxY: scala.Float): scala.Unit = {
    this.parallaxY = parallaxY
  }
  def getRenderOffsetX(): scala.Float = {
    if (this.renderOffsetDirty) {
      this.calculateRenderOffsets()
    } else ()
    return this.renderOffsetX
  }
  def getRenderOffsetY(): scala.Float = {
    if (this.renderOffsetDirty) {
      this.calculateRenderOffsets()
    } else ()
    return this.renderOffsetY
  }
  def invalidateRenderOffset(): scala.Unit = {
    this.renderOffsetDirty = true
  }
  def getParent(): MapLayer = {
    return this.parent
  }
  def setParent(parent: MapLayer): scala.Unit = {
    if (parent == this) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Can't set self as the parent")
    } else ()
    this.parent = parent
  }
  def getObjects(): com.badlogic.gdx.maps.MapObjects = {
    return this.objects
  }
  def isVisible(): scala.Boolean = {
    return this.visible
  }
  def setVisible(visible: scala.Boolean): scala.Unit = {
    this.visible = visible
  }
  def getProperties(): com.badlogic.gdx.maps.MapProperties = {
    return this.properties
  }
  def calculateRenderOffsets(): scala.Unit = {
    if (this.parent != null) {
      this.parent.calculateRenderOffsets()
      this.renderOffsetX = this.parent.getRenderOffsetX() + this.offsetX
      this.renderOffsetY = this.parent.getRenderOffsetY() + this.offsetY
    } else {
      this.renderOffsetX = this.offsetX
      this.renderOffsetY = this.offsetY
    }
    this.renderOffsetDirty = false
  }
}