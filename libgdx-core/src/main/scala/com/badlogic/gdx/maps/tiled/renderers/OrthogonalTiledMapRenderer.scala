package com.badlogic.gdx.maps.tiled.renderers

class OrthogonalTiledMapRenderer extends com.badlogic.gdx.maps.tiled.renderers.BatchTiledMapRenderer {
  def this(map: com.badlogic.gdx.maps.tiled.TiledMap, unitScale: scala.Float, batch: com.badlogic.gdx.graphics.g2d.Batch) = {
    this()
  }
  def this(map: com.badlogic.gdx.maps.tiled.TiledMap, batch: com.badlogic.gdx.graphics.g2d.Batch) = {
    this()
  }
  def this(map: com.badlogic.gdx.maps.tiled.TiledMap, unitScale: scala.Float) = {
    this()
  }
  def this(map: com.badlogic.gdx.maps.tiled.TiledMap) = {
    this()
  }
  def renderTileLayer(layer: com.badlogic.gdx.maps.tiled.TiledMapTileLayer): scala.Unit = {
    val batchColor: com.badlogic.gdx.graphics.Color = batch.getColor()
    val color: scala.Float = this.getTileLayerColor(layer, batchColor)
    val layerWidth: scala.Int = layer.getWidth()
    val layerHeight: scala.Int = layer.getHeight()
    val layerTileWidth: scala.Float = layer.getTileWidth() * unitScale
    val layerTileHeight: scala.Float = layer.getTileHeight() * unitScale
    val layerOffsetX: scala.Float = (layer.getRenderOffsetX() * unitScale) - (this.viewBounds.x * (layer.getParallaxX() - 1))
    val layerOffsetY: scala.Float = ((-layer.getRenderOffsetY()) * unitScale) - (this.viewBounds.y * (layer.getParallaxY() - 1))
    val col1: scala.Int = java.lang.Math.max(0, ((this.viewBounds.x - layerOffsetX) / layerTileWidth).asInstanceOf[scala.Int])
    val col2: scala.Int = java.lang.Math.min(layerWidth, ((((this.viewBounds.x + this.viewBounds.width) + layerTileWidth) - layerOffsetX) / layerTileWidth).asInstanceOf[scala.Int])
    val row1: scala.Int = java.lang.Math.max(0, ((this.viewBounds.y - layerOffsetY) / layerTileHeight).asInstanceOf[scala.Int])
    val row2: scala.Int = java.lang.Math.min(layerHeight, ((((this.viewBounds.y + this.viewBounds.height) + layerTileHeight) - layerOffsetY) / layerTileHeight).asInstanceOf[scala.Int])
    var y: scala.Float = (row2 * layerTileHeight) + layerOffsetY
    val xStart: scala.Float = (col1 * layerTileWidth) + layerOffsetX
    val vertices: scala.Array[scala.Float] = this.vertices
    { var row: scala.Int = row2; while (row >= row1) { {
      var x: scala.Float = xStart
      { var col: scala.Int = col1; while (col < col2) { {
        val cell: com.badlogic.gdx.maps.tiled.TiledMapTileLayer#Cell = layer.getCell(col, row)
        if (cell == null) {
          x = x + layerTileWidth
          /* continue */ ()
        } else ()
        val tile: com.badlogic.gdx.maps.tiled.TiledMapTile = cell.getTile()
        if (tile != null) {
          val flipX: scala.Boolean = cell.getFlipHorizontally()
          val flipY: scala.Boolean = cell.getFlipVertically()
          val rotations: scala.Int = cell.getRotation()
          val region: com.badlogic.gdx.graphics.g2d.TextureRegion = tile.getTextureRegion()
          val x1: scala.Float = x + (tile.getOffsetX() * unitScale)
          val y1: scala.Float = y + (tile.getOffsetY() * unitScale)
          val x2: scala.Float = x1 + (region.getRegionWidth() * unitScale)
          val y2: scala.Float = y1 + (region.getRegionHeight() * unitScale)
          val u1: scala.Float = region.getU()
          val v1: scala.Float = region.getV2()
          val u2: scala.Float = region.getU2()
          val v2: scala.Float = region.getV()
          vertices(com.badlogic.gdx.graphics.g2d.Batch.X1) = x1
          vertices(com.badlogic.gdx.graphics.g2d.Batch.Y1) = y1
          vertices(com.badlogic.gdx.graphics.g2d.Batch.C1) = color
          vertices(com.badlogic.gdx.graphics.g2d.Batch.U1) = u1
          vertices(com.badlogic.gdx.graphics.g2d.Batch.V1) = v1
          vertices(com.badlogic.gdx.graphics.g2d.Batch.X2) = x1
          vertices(com.badlogic.gdx.graphics.g2d.Batch.Y2) = y2
          vertices(com.badlogic.gdx.graphics.g2d.Batch.C2) = color
          vertices(com.badlogic.gdx.graphics.g2d.Batch.U2) = u1
          vertices(com.badlogic.gdx.graphics.g2d.Batch.V2) = v2
          vertices(com.badlogic.gdx.graphics.g2d.Batch.X3) = x2
          vertices(com.badlogic.gdx.graphics.g2d.Batch.Y3) = y2
          vertices(com.badlogic.gdx.graphics.g2d.Batch.C3) = color
          vertices(com.badlogic.gdx.graphics.g2d.Batch.U3) = u2
          vertices(com.badlogic.gdx.graphics.g2d.Batch.V3) = v2
          vertices(com.badlogic.gdx.graphics.g2d.Batch.X4) = x2
          vertices(com.badlogic.gdx.graphics.g2d.Batch.Y4) = y1
          vertices(com.badlogic.gdx.graphics.g2d.Batch.C4) = color
          vertices(com.badlogic.gdx.graphics.g2d.Batch.U4) = u2
          vertices(com.badlogic.gdx.graphics.g2d.Batch.V4) = v1
          if (flipX) {
            var temp: scala.Float = vertices(com.badlogic.gdx.graphics.g2d.Batch.U1)
            vertices(com.badlogic.gdx.graphics.g2d.Batch.U1) = vertices(com.badlogic.gdx.graphics.g2d.Batch.U3)
            vertices(com.badlogic.gdx.graphics.g2d.Batch.U3) = temp
            temp = vertices(com.badlogic.gdx.graphics.g2d.Batch.U2)
            vertices(com.badlogic.gdx.graphics.g2d.Batch.U2) = vertices(com.badlogic.gdx.graphics.g2d.Batch.U4)
            vertices(com.badlogic.gdx.graphics.g2d.Batch.U4) = temp
          } else ()
          if (flipY) {
            var temp: scala.Float = vertices(com.badlogic.gdx.graphics.g2d.Batch.V1)
            vertices(com.badlogic.gdx.graphics.g2d.Batch.V1) = vertices(com.badlogic.gdx.graphics.g2d.Batch.V3)
            vertices(com.badlogic.gdx.graphics.g2d.Batch.V3) = temp
            temp = vertices(com.badlogic.gdx.graphics.g2d.Batch.V2)
            vertices(com.badlogic.gdx.graphics.g2d.Batch.V2) = vertices(com.badlogic.gdx.graphics.g2d.Batch.V4)
            vertices(com.badlogic.gdx.graphics.g2d.Batch.V4) = temp
          } else ()
          if (rotations != 0) {
            rotations match {
              case com.badlogic.gdx.maps.tiled.TiledMapTileLayer.Cell.ROTATE_90 => {
                var tempV: scala.Float = vertices(com.badlogic.gdx.graphics.g2d.Batch.V1)
                vertices(com.badlogic.gdx.graphics.g2d.Batch.V1) = vertices(com.badlogic.gdx.graphics.g2d.Batch.V2)
                vertices(com.badlogic.gdx.graphics.g2d.Batch.V2) = vertices(com.badlogic.gdx.graphics.g2d.Batch.V3)
                vertices(com.badlogic.gdx.graphics.g2d.Batch.V3) = vertices(com.badlogic.gdx.graphics.g2d.Batch.V4)
                vertices(com.badlogic.gdx.graphics.g2d.Batch.V4) = tempV
                var tempU: scala.Float = vertices(com.badlogic.gdx.graphics.g2d.Batch.U1)
                vertices(com.badlogic.gdx.graphics.g2d.Batch.U1) = vertices(com.badlogic.gdx.graphics.g2d.Batch.U2)
                vertices(com.badlogic.gdx.graphics.g2d.Batch.U2) = vertices(com.badlogic.gdx.graphics.g2d.Batch.U3)
                vertices(com.badlogic.gdx.graphics.g2d.Batch.U3) = vertices(com.badlogic.gdx.graphics.g2d.Batch.U4)
                vertices(com.badlogic.gdx.graphics.g2d.Batch.U4) = tempU
              }
              case com.badlogic.gdx.maps.tiled.TiledMapTileLayer.Cell.ROTATE_180 => {
                var tempU: scala.Float = vertices(com.badlogic.gdx.graphics.g2d.Batch.U1)
                vertices(com.badlogic.gdx.graphics.g2d.Batch.U1) = vertices(com.badlogic.gdx.graphics.g2d.Batch.U3)
                vertices(com.badlogic.gdx.graphics.g2d.Batch.U3) = tempU
                tempU = vertices(com.badlogic.gdx.graphics.g2d.Batch.U2)
                vertices(com.badlogic.gdx.graphics.g2d.Batch.U2) = vertices(com.badlogic.gdx.graphics.g2d.Batch.U4)
                vertices(com.badlogic.gdx.graphics.g2d.Batch.U4) = tempU
                var tempV: scala.Float = vertices(com.badlogic.gdx.graphics.g2d.Batch.V1)
                vertices(com.badlogic.gdx.graphics.g2d.Batch.V1) = vertices(com.badlogic.gdx.graphics.g2d.Batch.V3)
                vertices(com.badlogic.gdx.graphics.g2d.Batch.V3) = tempV
                tempV = vertices(com.badlogic.gdx.graphics.g2d.Batch.V2)
                vertices(com.badlogic.gdx.graphics.g2d.Batch.V2) = vertices(com.badlogic.gdx.graphics.g2d.Batch.V4)
                vertices(com.badlogic.gdx.graphics.g2d.Batch.V4) = tempV
              }
              case com.badlogic.gdx.maps.tiled.TiledMapTileLayer.Cell.ROTATE_270 => {
                var tempV: scala.Float = vertices(com.badlogic.gdx.graphics.g2d.Batch.V1)
                vertices(com.badlogic.gdx.graphics.g2d.Batch.V1) = vertices(com.badlogic.gdx.graphics.g2d.Batch.V4)
                vertices(com.badlogic.gdx.graphics.g2d.Batch.V4) = vertices(com.badlogic.gdx.graphics.g2d.Batch.V3)
                vertices(com.badlogic.gdx.graphics.g2d.Batch.V3) = vertices(com.badlogic.gdx.graphics.g2d.Batch.V2)
                vertices(com.badlogic.gdx.graphics.g2d.Batch.V2) = tempV
                var tempU: scala.Float = vertices(com.badlogic.gdx.graphics.g2d.Batch.U1)
                vertices(com.badlogic.gdx.graphics.g2d.Batch.U1) = vertices(com.badlogic.gdx.graphics.g2d.Batch.U4)
                vertices(com.badlogic.gdx.graphics.g2d.Batch.U4) = vertices(com.badlogic.gdx.graphics.g2d.Batch.U3)
                vertices(com.badlogic.gdx.graphics.g2d.Batch.U3) = vertices(com.badlogic.gdx.graphics.g2d.Batch.U2)
                vertices(com.badlogic.gdx.graphics.g2d.Batch.U2) = tempU
              }
            }
          } else ()
          batch.draw(region.getTexture(), vertices, 0, com.badlogic.gdx.maps.tiled.renderers.BatchTiledMapRenderer.NUM_VERTICES)
        } else ()
        x = x + layerTileWidth
      }; col = col + 1 } }
      y = y - layerTileHeight
    }; row = row - 1 } }
  }
}