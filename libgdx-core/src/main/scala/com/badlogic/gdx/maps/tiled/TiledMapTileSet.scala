package com.badlogic.gdx.maps.tiled

class TiledMapTileSet extends balticporter.runtime.JavaIterable[com.badlogic.gdx.maps.tiled.TiledMapTile] {
  private var name: java.lang.String = null.asInstanceOf[java.lang.String]
  private var tiles: com.badlogic.gdx.utils.IntMap[com.badlogic.gdx.maps.tiled.TiledMapTile] = null.asInstanceOf[com.badlogic.gdx.utils.IntMap[com.badlogic.gdx.maps.tiled.TiledMapTile]]
  private var properties: com.badlogic.gdx.maps.MapProperties = null.asInstanceOf[com.badlogic.gdx.maps.MapProperties]
  this.tiles = new com.badlogic.gdx.utils.IntMap[com.badlogic.gdx.maps.tiled.TiledMapTile]()
  this.properties = new com.badlogic.gdx.maps.MapProperties()
  def getName(): java.lang.String = {
    return this.name
  }
  def setName(name: java.lang.String): scala.Unit = {
    this.name = name
  }
  def getProperties(): com.badlogic.gdx.maps.MapProperties = {
    return this.properties
  }
  def getTile(id: scala.Int): com.badlogic.gdx.maps.tiled.TiledMapTile = {
    return this.tiles.get(id)
  }
  @java.lang.Override
  override def iterator(): balticporter.runtime.JavaIterator[com.badlogic.gdx.maps.tiled.TiledMapTile] = {
    return this.tiles.values().iterator()
  }
  def putTile(id: scala.Int, tile: com.badlogic.gdx.maps.tiled.TiledMapTile): scala.Unit = {
    this.tiles.put(id, tile)
  }
  def removeTile(id: scala.Int): scala.Unit = {
    this.tiles.remove(id)
  }
  def size(): scala.Int = {
    return this.tiles.size
  }
}