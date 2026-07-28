package com.badlogic.gdx.maps.tiled

class TiledMapTileSets extends balticporter.runtime.JavaIterable[com.badlogic.gdx.maps.tiled.TiledMapTileSet] {
  private var tilesets: com.badlogic.gdx.utils.Array[com.badlogic.gdx.maps.tiled.TiledMapTileSet] = null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.maps.tiled.TiledMapTileSet]]
  this.tilesets = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.maps.tiled.TiledMapTileSet]()
  def getTileSet(index: scala.Int): com.badlogic.gdx.maps.tiled.TiledMapTileSet = {
    return this.tilesets.get(index)
  }
  def getTileSet(name: java.lang.String): com.badlogic.gdx.maps.tiled.TiledMapTileSet = {
    for (tileset <- this.tilesets) {
      if (name.equals(tileset.getName())) {
        return tileset
      } else ()
    }
    return null
  }
  def addTileSet(tileset: com.badlogic.gdx.maps.tiled.TiledMapTileSet): scala.Unit = {
    this.tilesets.add(tileset)
  }
  def removeTileSet(index: scala.Int): scala.Unit = {
    this.tilesets.removeIndex(index)
  }
  def removeTileSet(tileset: com.badlogic.gdx.maps.tiled.TiledMapTileSet): scala.Unit = {
    this.tilesets.removeValue(tileset, true)
  }
  def getTile(id: scala.Int): com.badlogic.gdx.maps.tiled.TiledMapTile = {
    { var i: scala.Int = this.tilesets.size - 1; while (i >= 0) { {
      val tileset: com.badlogic.gdx.maps.tiled.TiledMapTileSet = this.tilesets.get(i)
      val tile: com.badlogic.gdx.maps.tiled.TiledMapTile = tileset.getTile(id)
      if (tile != null) {
        return tile
      } else ()
    }; i = i - 1 } }
    return null
  }
  @java.lang.Override
  def iterator(): balticporter.runtime.JavaIterator[com.badlogic.gdx.maps.tiled.TiledMapTileSet] = {
    return this.tilesets.iterator()
  }
}