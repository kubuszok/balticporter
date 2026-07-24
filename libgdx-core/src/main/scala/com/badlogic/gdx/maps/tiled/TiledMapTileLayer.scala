package com.badlogic.gdx.maps.tiled

class TiledMapTileLayer extends com.badlogic.gdx.maps.MapLayer {
  private var width: scala.Int = 0
  private var height: scala.Int = 0
  private var tileWidth: scala.Int = 0
  private var tileHeight: scala.Int = 0
  private var cells: scala.Array[scala.Array[Cell]] = null.asInstanceOf[scala.Array[scala.Array[Cell]]]
  def this(width: scala.Int, height: scala.Int, tileWidth: scala.Int, tileHeight: scala.Int) = {
    this()
    this.width = width
    this.height = height
    this.tileWidth = tileWidth
    this.tileHeight = tileHeight
    this.cells = new Array[scala.Array[Cell]](width, height)
  }
  def getWidth(): scala.Int = {
    return this.width
  }
  def getHeight(): scala.Int = {
    return this.height
  }
  def getTileWidth(): scala.Int = {
    return this.tileWidth
  }
  def getTileHeight(): scala.Int = {
    return this.tileHeight
  }
  def getCell(x: scala.Int, y: scala.Int): Cell = {
    if ((x < 0) || (x >= this.width)) {
      return null
    } else ()
    if ((y < 0) || (y >= this.height)) {
      return null
    } else ()
    return this.cells(x)(y)
  }
  def setCell(x: scala.Int, y: scala.Int, cell: Cell): scala.Unit = {
    if ((x < 0) || (x >= this.width)) {
      return
    } else ()
    if ((y < 0) || (y >= this.height)) {
      return
    } else ()
    this.cells(x)(y) = cell
  }
  class Cell {
    private var tile: com.badlogic.gdx.maps.tiled.TiledMapTile = null.asInstanceOf[com.badlogic.gdx.maps.tiled.TiledMapTile]
    private var flipHorizontally: scala.Boolean = false
    private var flipVertically: scala.Boolean = false
    private var rotation: scala.Int = 0
    def getTile(): com.badlogic.gdx.maps.tiled.TiledMapTile = {
      return this.tile
    }
    def setTile(tile: com.badlogic.gdx.maps.tiled.TiledMapTile): Cell = {
      this.tile = tile
      return this
    }
    def getFlipHorizontally(): scala.Boolean = {
      return this.flipHorizontally
    }
    def setFlipHorizontally(flipHorizontally: scala.Boolean): Cell = {
      this.flipHorizontally = flipHorizontally
      return this
    }
    def getFlipVertically(): scala.Boolean = {
      return this.flipVertically
    }
    def setFlipVertically(flipVertically: scala.Boolean): Cell = {
      this.flipVertically = flipVertically
      return this
    }
    def getRotation(): scala.Int = {
      return this.rotation
    }
    def setRotation(rotation: scala.Int): Cell = {
      this.rotation = rotation
      return this
    }
  }
  object Cell {
    final val ROTATE_0: scala.Int = 0
    final val ROTATE_90: scala.Int = 1
    final val ROTATE_180: scala.Int = 2
    final val ROTATE_270: scala.Int = 3
  }
}