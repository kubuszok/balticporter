package com.badlogic.gdx.maps

class MapObject {
  private var name: java.lang.String = ""
  private var opacity: scala.Float = 1.0f
  private var visible: scala.Boolean = true
  private var properties: com.badlogic.gdx.maps.MapProperties = new com.badlogic.gdx.maps.MapProperties()
  private var color: com.badlogic.gdx.graphics.Color = com.badlogic.gdx.graphics.Color.WHITE.cpy()
  def getName(): java.lang.String = {
    return this.name
  }
  def setName(name: java.lang.String): scala.Unit = {
    this.name = name
  }
  def getColor(): com.badlogic.gdx.graphics.Color = {
    return this.color
  }
  def setColor(color: com.badlogic.gdx.graphics.Color): scala.Unit = {
    this.color = color
  }
  def getOpacity(): scala.Float = {
    return this.opacity
  }
  def setOpacity(opacity: scala.Float): scala.Unit = {
    this.opacity = opacity
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
}