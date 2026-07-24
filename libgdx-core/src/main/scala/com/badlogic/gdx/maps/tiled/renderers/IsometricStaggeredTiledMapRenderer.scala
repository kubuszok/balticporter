package com.badlogic.gdx.maps.tiled.renderers

class IsometricStaggeredTiledMapRenderer extends com.badlogic.gdx.maps.tiled.renderers.BatchTiledMapRenderer {
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
    val layerOffsetX: scala.Float = (layer.getRenderOffsetX() * unitScale) - (this.viewBounds.x * (layer.getParallaxX() - 1))
    val layerOffsetY: scala.Float = ((-layer.getRenderOffsetY()) * unitScale) - (this.viewBounds.y * (layer.getParallaxY() - 1))
    val layerTileWidth: scala.Float = layer.getTileWidth() * unitScale
    val layerTileHeight: scala.Float = layer.getTileHeight() * unitScale
    val layerTileWidth50: scala.Float = layerTileWidth * 0.5f
    val layerTileHeight50: scala.Float = layerTileHeight * 0.5f
    val minX: scala.Int = java.lang.Math.max(0, (((this.viewBounds.x - layerTileWidth50) - layerOffsetX) / layerTileWidth).asInstanceOf[scala.Int])
    val maxX: scala.Int = java.lang.Math.min(layerWidth, (((((this.viewBounds.x + this.viewBounds.width) + layerTileWidth) + layerTileWidth50) - layerOffsetX) / layerTileWidth).asInstanceOf[scala.Int])
    val minY: scala.Int = java.lang.Math.max(0, (((this.viewBounds.y - layerTileHeight) - layerOffsetY) / layerTileHeight).asInstanceOf[scala.Int])
    val maxY: scala.Int = java.lang.Math.min(layerHeight, ((((this.viewBounds.y + this.viewBounds.height) + layerTileHeight) - layerOffsetY) / layerTileHeight50).asInstanceOf[scala.Int])
    { var y: scala.Int = maxY - 1; while (y >= minY) { {
      val offsetX: scala.Float = if ((y % 2) == 1) layerTileWidth50 else 0
      { var x: scala.Int = maxX - 1; while (x >= minX) { {
        val cell: com.badlogic.gdx.maps.tiled.TiledMapTileLayer#Cell = layer.getCell(x, y)
        if (cell == null) {
          /* continue */ ()
        } else ()
        val tile: com.badlogic.gdx.maps.tiled.TiledMapTile = cell.getTile()
        if (tile != null) {
          val flipX: scala.Boolean = cell.getFlipHorizontally()
          val flipY: scala.Boolean = cell.getFlipVertically()
          val rotations: scala.Int = cell.getRotation()
          val region: com.badlogic.gdx.graphics.g2d.TextureRegion = tile.getTextureRegion()
          val x1: scala.Float = (((x * layerTileWidth) - offsetX) + (tile.getOffsetX() * unitScale)) + layerOffsetX
          val y1: scala.Float = ((y * layerTileHeight50) + (tile.getOffsetY() * unitScale)) + layerOffsetY
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
      }; x = x - 1 } }
    }; y = y - 1 } }
  }
  def renderImageLayer(layer: com.badlogic.gdx.maps.tiled.TiledMapImageLayer): scala.Unit = {
    val batchColor: com.badlogic.gdx.graphics.Color = batch.getColor()
    val color: scala.Float = this.getImageLayerColor(layer, batchColor)
    val vertices: scala.Array[scala.Float] = this.vertices
    val region: com.badlogic.gdx.graphics.g2d.TextureRegion = layer.getTextureRegion()
    if (region == null) {
      return
    } else ()
    val tileWidth: scala.Int = this.getMap().getProperties().get("tilewidth", classOf[java.lang.Class])
    val halfTileWidth: scala.Float = (tileWidth * 0.5f) * unitScale
    val x: scala.Float = layer.getX()
    val y: scala.Float = layer.getY()
    val x1: scala.Float = ((x * unitScale) - (this.viewBounds.x * (layer.getParallaxX() - 1))) - halfTileWidth
    val y1: scala.Float = (y * unitScale) - (this.viewBounds.y * (layer.getParallaxY() - 1))
    val x2: scala.Float = x1 + (region.getRegionWidth() * unitScale)
    val y2: scala.Float = y1 + (region.getRegionHeight() * unitScale)
    imageBounds.set(x1, y1, x2 - x1, y2 - y1)
    if ((!layer.isRepeatX()) && (!layer.isRepeatY())) {
      if (viewBounds.contains(imageBounds) || viewBounds.overlaps(imageBounds)) {
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
        batch.draw(region.getTexture(), vertices, 0, com.badlogic.gdx.maps.tiled.renderers.BatchTiledMapRenderer.NUM_VERTICES)
      } else ()
    } else {
      val repeatX: scala.Int = if (layer.isRepeatX()) java.lang.Math.ceil((this.viewBounds.width / this.imageBounds.width) + 4).asInstanceOf[scala.Int] else 0
      val repeatY: scala.Int = if (layer.isRepeatY()) java.lang.Math.ceil((this.viewBounds.height / this.imageBounds.height) + 4).asInstanceOf[scala.Int] else 0
      var startX: scala.Float = this.viewBounds.x
      var startY: scala.Float = this.viewBounds.y
      startX = startX - (startX % this.imageBounds.width)
      startY = startY - (startY % this.imageBounds.height)
      { var i: scala.Int = 0; while (i <= repeatX) { {
        { var j: scala.Int = 0; while (j <= repeatY) { {
          var rx1: scala.Float = x1
          var ry1: scala.Float = y1
          var rx2: scala.Float = x2
          var ry2: scala.Float = y2
          if (layer.isRepeatX()) {
            rx1 = (startX + ((i - 2) * this.imageBounds.width)) + (x1 % this.imageBounds.width)
            rx2 = rx1 + this.imageBounds.width
          } else ()
          if (layer.isRepeatY()) {
            ry1 = (startY + ((j - 2) * this.imageBounds.height)) + (y1 % this.imageBounds.height)
            ry2 = ry1 + this.imageBounds.height
          } else ()
          repeatedImageBounds.set(rx1, ry1, rx2 - rx1, ry2 - ry1)
          if (viewBounds.contains(repeatedImageBounds) || viewBounds.overlaps(repeatedImageBounds)) {
            val ru1: scala.Float = region.getU()
            val rv1: scala.Float = region.getV2()
            val ru2: scala.Float = region.getU2()
            val rv2: scala.Float = region.getV()
            vertices(com.badlogic.gdx.graphics.g2d.Batch.X1) = rx1
            vertices(com.badlogic.gdx.graphics.g2d.Batch.Y1) = ry1
            vertices(com.badlogic.gdx.graphics.g2d.Batch.C1) = color
            vertices(com.badlogic.gdx.graphics.g2d.Batch.U1) = ru1
            vertices(com.badlogic.gdx.graphics.g2d.Batch.V1) = rv1
            vertices(com.badlogic.gdx.graphics.g2d.Batch.X2) = rx1
            vertices(com.badlogic.gdx.graphics.g2d.Batch.Y2) = ry2
            vertices(com.badlogic.gdx.graphics.g2d.Batch.C2) = color
            vertices(com.badlogic.gdx.graphics.g2d.Batch.U2) = ru1
            vertices(com.badlogic.gdx.graphics.g2d.Batch.V2) = rv2
            vertices(com.badlogic.gdx.graphics.g2d.Batch.X3) = rx2
            vertices(com.badlogic.gdx.graphics.g2d.Batch.Y3) = ry2
            vertices(com.badlogic.gdx.graphics.g2d.Batch.C3) = color
            vertices(com.badlogic.gdx.graphics.g2d.Batch.U3) = ru2
            vertices(com.badlogic.gdx.graphics.g2d.Batch.V3) = rv2
            vertices(com.badlogic.gdx.graphics.g2d.Batch.X4) = rx2
            vertices(com.badlogic.gdx.graphics.g2d.Batch.Y4) = ry1
            vertices(com.badlogic.gdx.graphics.g2d.Batch.C4) = color
            vertices(com.badlogic.gdx.graphics.g2d.Batch.U4) = ru2
            vertices(com.badlogic.gdx.graphics.g2d.Batch.V4) = rv1
            batch.draw(region.getTexture(), vertices, 0, com.badlogic.gdx.maps.tiled.renderers.BatchTiledMapRenderer.NUM_VERTICES)
          } else ()
        }; j = j + 1 } }
      }; i = i + 1 } }
    }
  }
}