package com.badlogic.gdx.maps.tiled.renderers

class HexagonalTiledMapRenderer extends com.badlogic.gdx.maps.tiled.renderers.BatchTiledMapRenderer {
  private var staggerAxisX: scala.Boolean = true
  private var staggerIndexEven: scala.Boolean = false
  private var hexSideLength: scala.Float = 0.0f
  def this(map: com.badlogic.gdx.maps.tiled.TiledMap, unitScale: scala.Float, batch: com.badlogic.gdx.graphics.g2d.Batch) = {
    this()
    this.init(map)
  }
  def this(map: com.badlogic.gdx.maps.tiled.TiledMap, unitScale: scala.Float) = {
    this()
    this.init(map)
  }
  def this(map: com.badlogic.gdx.maps.tiled.TiledMap, batch: com.badlogic.gdx.graphics.g2d.Batch) = {
    this()
    this.init(map)
  }
  def this(map: com.badlogic.gdx.maps.tiled.TiledMap) = {
    this()
    this.init(map)
  }
  private def init(map: com.badlogic.gdx.maps.tiled.TiledMap): scala.Unit = {
    val axis: java.lang.String = map.getProperties().get("staggeraxis", classOf[java.lang.String])
    if (axis != null) {
      if (axis.equals("x")) {
        this.staggerAxisX = true
      } else {
        this.staggerAxisX = false
      }
    } else ()
    val index: java.lang.String = map.getProperties().get("staggerindex", classOf[java.lang.String])
    if (index != null) {
      if (index.equals("even")) {
        this.staggerIndexEven = true
      } else {
        this.staggerIndexEven = false
      }
    } else ()
    if ((!this.staggerAxisX) && ((map.getProperties().get[java.lang.Integer]("height", classOf[java.lang.Integer]) % 2) == 0)) {
      this.staggerIndexEven = !this.staggerIndexEven
    } else ()
    var length: java.lang.Integer = map.getProperties().get[java.lang.Integer]("hexsidelength", classOf[java.lang.Integer])
    if (length != null) {
      this.hexSideLength = length.intValue()
    } else {
      if (this.staggerAxisX) {
        length = map.getProperties().get[java.lang.Integer]("tilewidth", classOf[java.lang.Integer])
        if (length != null) {
          this.hexSideLength = 0.5f * length.intValue()
        } else {
          if (map.getLayers().size() > 0) {
            val tmtl: com.badlogic.gdx.maps.tiled.TiledMapTileLayer = map.getLayers().get(0).asInstanceOf[com.badlogic.gdx.maps.tiled.TiledMapTileLayer]
            this.hexSideLength = 0.5f * tmtl.getTileWidth()
          } else {
            this.hexSideLength = 0.0f
          }
        }
      } else {
        length = map.getProperties().get[java.lang.Integer]("tileheight", classOf[java.lang.Integer])
        if (length != null) {
          this.hexSideLength = 0.5f * length.intValue()
        } else {
          if (map.getLayers().size() > 0) {
            val tmtl: com.badlogic.gdx.maps.tiled.TiledMapTileLayer = map.getLayers().get(0).asInstanceOf[com.badlogic.gdx.maps.tiled.TiledMapTileLayer]
            this.hexSideLength = 0.5f * tmtl.getTileHeight()
          } else {
            this.hexSideLength = 0.0f
          }
        }
      }
    }
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
    val layerHexLength: scala.Float = this.hexSideLength * unitScale
    if (this.staggerAxisX) {
      val tileWidthLowerCorner: scala.Float = (layerTileWidth - layerHexLength) / 2
      val tileWidthUpperCorner: scala.Float = (layerTileWidth + layerHexLength) / 2
      val layerTileHeight50: scala.Float = layerTileHeight * 0.5f
      val row1: scala.Int = java.lang.Math.max(0, (((this.viewBounds.y - layerTileHeight50) - layerOffsetY) / layerTileHeight).asInstanceOf[scala.Int].asInstanceOf[scala.Int])
      val row2: scala.Int = java.lang.Math.min(layerHeight, ((((this.viewBounds.y + this.viewBounds.height) + layerTileHeight) - layerOffsetY) / layerTileHeight).asInstanceOf[scala.Int].asInstanceOf[scala.Int])
      val col1: scala.Int = java.lang.Math.max(0, (((this.viewBounds.x - tileWidthLowerCorner) - layerOffsetX) / tileWidthUpperCorner).asInstanceOf[scala.Int].asInstanceOf[scala.Int])
      val col2: scala.Int = java.lang.Math.min(layerWidth, ((((this.viewBounds.x + this.viewBounds.width) + tileWidthUpperCorner) - layerOffsetX) / tileWidthUpperCorner).asInstanceOf[scala.Int].asInstanceOf[scala.Int])
      val colA: scala.Int = if (this.staggerIndexEven == ((col1 % 2) == 0)) col1 + 1 else col1
      val colB: scala.Int = if (this.staggerIndexEven == ((col1 % 2) == 0)) col1 else col1 + 1;
      { var row: scala.Int = row2 - 1; while (row >= row1) { {
        { var col: scala.Int = colA; while (col < col2) { {
          this.renderCell(layer.getCell(col, row), (tileWidthUpperCorner * col) + layerOffsetX, (layerTileHeight50 + (layerTileHeight * row)) + layerOffsetY, color)
        }; col = col + 2 } };
        { var col: scala.Int = colB; while (col < col2) { {
          this.renderCell(layer.getCell(col, row), (tileWidthUpperCorner * col) + layerOffsetX, (layerTileHeight * row) + layerOffsetY, color)
        }; col = col + 2 } }
      }; row = row - 1 } }
    } else {
      val tileHeightLowerCorner: scala.Float = (layerTileHeight - layerHexLength) / 2
      val tileHeightUpperCorner: scala.Float = (layerTileHeight + layerHexLength) / 2
      val layerTileWidth50: scala.Float = layerTileWidth * 0.5f
      val row1: scala.Int = java.lang.Math.max(0, (((this.viewBounds.y - tileHeightLowerCorner) - layerOffsetY) / tileHeightUpperCorner).asInstanceOf[scala.Int].asInstanceOf[scala.Int])
      val row2: scala.Int = java.lang.Math.min(layerHeight, ((((this.viewBounds.y + this.viewBounds.height) + tileHeightUpperCorner) - layerOffsetY) / tileHeightUpperCorner).asInstanceOf[scala.Int].asInstanceOf[scala.Int])
      val col1: scala.Int = java.lang.Math.max(0, (((this.viewBounds.x - layerTileWidth50) - layerOffsetX) / layerTileWidth).asInstanceOf[scala.Int].asInstanceOf[scala.Int])
      val col2: scala.Int = java.lang.Math.min(layerWidth, ((((this.viewBounds.x + this.viewBounds.width) + layerTileWidth) - layerOffsetX) / layerTileWidth).asInstanceOf[scala.Int].asInstanceOf[scala.Int])
      var shiftX: scala.Float = 0;
      { var row: scala.Int = row2 - 1; while (row >= row1) { {
        if (((row % 2) == 0) == this.staggerIndexEven) {
          shiftX = layerTileWidth50
        } else {
          shiftX = 0
        };
        { var col: scala.Int = col1; while (col < col2) { {
          this.renderCell(layer.getCell(col, row), ((layerTileWidth * col) + shiftX) + layerOffsetX, (tileHeightUpperCorner * row) + layerOffsetY, color)
        }; col = col + 1 } }
      }; row = row - 1 } }
    }
  }
  private def renderCell(cell: com.badlogic.gdx.maps.tiled.TiledMapTileLayer.Cell, x: scala.Float, y: scala.Float, color: scala.Float): scala.Unit = {
    if (cell != null) {
      val tile: com.badlogic.gdx.maps.tiled.TiledMapTile = cell.getTile()
      if (tile != null) {
        if (tile.isInstanceOf[com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile]) {
          return
        } else ()
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
        if (rotations == 2) {
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
        } else ()
        batch.draw(region.getTexture(), vertices, 0, com.badlogic.gdx.maps.tiled.renderers.BatchTiledMapRenderer.NUM_VERTICES)
      } else ()
    } else ()
  }
  def renderImageLayer(layer: com.badlogic.gdx.maps.tiled.TiledMapImageLayer): scala.Unit = {
    val batchColor: com.badlogic.gdx.graphics.Color = batch.getColor()
    val color: scala.Float = this.getImageLayerColor(layer, batchColor)
    val vertices: scala.Array[scala.Float] = this.vertices
    val region: com.badlogic.gdx.graphics.g2d.TextureRegion = layer.getTextureRegion()
    if (region == null) {
      return
    } else ()
    val tileHeight: scala.Int = this.getMap().getProperties().get[java.lang.Integer]("tileheight", classOf[java.lang.Integer])
    val mapHeight: scala.Int = this.getMap().getProperties().get[java.lang.Integer]("height", classOf[java.lang.Integer])
    val layerHexLength: scala.Float = this.hexSideLength
    val totalHeightPixels: scala.Float = (mapHeight * tileHeight) * unitScale
    val hexMapHeightPixels: scala.Float = (((mapHeight * tileHeight) * (3.0f / 4.0f)) + (layerHexLength * 0.5f)) * unitScale
    var imageLayerYOffset: scala.Float = 0
    val layerTileHeight: scala.Float = tileHeight * unitScale
    val halfTileHeight: scala.Float = layerTileHeight * 0.5f
    if (this.staggerAxisX) {
      imageLayerYOffset = halfTileHeight
    } else {
      imageLayerYOffset = -(totalHeightPixels - hexMapHeightPixels)
    }
    val x: scala.Float = layer.getX()
    val y: scala.Float = layer.getY()
    val x1: scala.Float = (x * unitScale) - (this.viewBounds.x * (layer.getParallaxX() - 1))
    val y1: scala.Float = ((y * unitScale) - (this.viewBounds.y * (layer.getParallaxY() - 1))) + imageLayerYOffset
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
      startY = startY - (startY % this.imageBounds.height);
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
  def isStaggerAxisX(): scala.Boolean = {
    return this.staggerAxisX
  }
  def setStaggerAxisX(staggerAxisX: scala.Boolean): scala.Unit = {
    this.staggerAxisX = staggerAxisX
  }
  def isStaggerIndexEven(): scala.Boolean = {
    return this.staggerIndexEven
  }
  def setStaggerIndexEven(staggerIndexEven: scala.Boolean): scala.Unit = {
    this.staggerIndexEven = staggerIndexEven
  }
  def getHexSideLength(): scala.Float = {
    return this.hexSideLength
  }
  def setHexSideLength(hexSideLength: scala.Float): scala.Unit = {
    this.hexSideLength = hexSideLength
  }
}
object HexagonalTiledMapRenderer {
  export com.badlogic.gdx.maps.tiled.renderers.BatchTiledMapRenderer.*
}