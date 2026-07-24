package com.badlogic.gdx.maps.tiled

class TiledMap extends com.badlogic.gdx.maps.Map {
  private var tilesets: com.badlogic.gdx.maps.tiled.TiledMapTileSets = null.asInstanceOf[com.badlogic.gdx.maps.tiled.TiledMapTileSets]
  private var ownedResources: com.badlogic.gdx.utils.Array[? <: com.badlogic.gdx.utils.Disposable] = null.asInstanceOf[com.badlogic.gdx.utils.Array[? <: com.badlogic.gdx.utils.Disposable]]
  def this() = {
    this()
    this.tilesets = new com.badlogic.gdx.maps.tiled.TiledMapTileSets()
  }
  def getTileSets(): com.badlogic.gdx.maps.tiled.TiledMapTileSets = {
    return this.tilesets
  }
  def setOwnedResources(resources: com.badlogic.gdx.utils.Array[? <: com.badlogic.gdx.utils.Disposable]): scala.Unit = {
    this.ownedResources = resources
  }
  def dispose(): scala.Unit = {
    if (this.ownedResources != null) {
      for (resource <- this.ownedResources) {
        resource.dispose()
      }
    } else ()
  }
}