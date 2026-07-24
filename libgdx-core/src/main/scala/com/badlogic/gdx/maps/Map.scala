package com.badlogic.gdx.maps

class Map extends com.badlogic.gdx.utils.Disposable {
  private var layers: com.badlogic.gdx.maps.MapLayers = new com.badlogic.gdx.maps.MapLayers()
  private var properties: com.badlogic.gdx.maps.MapProperties = new com.badlogic.gdx.maps.MapProperties()
  def getLayers(): com.badlogic.gdx.maps.MapLayers = {
    return this.layers
  }
  def getProperties(): com.badlogic.gdx.maps.MapProperties = {
    return this.properties
  }
  def dispose(): scala.Unit = {
    ()
  }
}