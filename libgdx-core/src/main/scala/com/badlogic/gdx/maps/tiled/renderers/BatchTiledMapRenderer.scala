package com.badlogic.gdx.maps.tiled.renderers

abstract class BatchTiledMapRenderer extends com.badlogic.gdx.maps.tiled.TiledMapRenderer with com.badlogic.gdx.utils.Disposable {
  var map: com.badlogic.gdx.maps.tiled.TiledMap = null.asInstanceOf[com.badlogic.gdx.maps.tiled.TiledMap]
  var unitScale: scala.Float = 0.0f
  var batch: com.badlogic.gdx.graphics.g2d.Batch = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.Batch]
  var viewBounds: com.badlogic.gdx.math.Rectangle = null.asInstanceOf[com.badlogic.gdx.math.Rectangle]
  var imageBounds: com.badlogic.gdx.math.Rectangle = new com.badlogic.gdx.math.Rectangle()
  var repeatedImageBounds: com.badlogic.gdx.math.Rectangle = new com.badlogic.gdx.math.Rectangle()
  var ownsBatch: scala.Boolean = false
  var vertices: scala.Array[scala.Float] = new scala.Array[scala.Float](BatchTiledMapRenderer.NUM_VERTICES)
  def this(map: com.badlogic.gdx.maps.tiled.TiledMap, unitScale: scala.Float) = {
    this()
    this.map = map
    this.unitScale = unitScale
    this.viewBounds = new com.badlogic.gdx.math.Rectangle()
    this.batch = new com.badlogic.gdx.graphics.g2d.SpriteBatch()
    this.ownsBatch = true
  }
  def this(map: com.badlogic.gdx.maps.tiled.TiledMap) = {
    this(map, 1.0f)
  }
  def this(map: com.badlogic.gdx.maps.tiled.TiledMap, unitScale: scala.Float, batch: com.badlogic.gdx.graphics.g2d.Batch) = {
    this()
    this.map = map
    this.unitScale = unitScale
    this.viewBounds = new com.badlogic.gdx.math.Rectangle()
    this.batch = batch
    this.ownsBatch = false
  }
  def this(map: com.badlogic.gdx.maps.tiled.TiledMap, batch: com.badlogic.gdx.graphics.g2d.Batch) = {
    this(map, 1.0f, batch)
  }
  def getMap(): com.badlogic.gdx.maps.tiled.TiledMap = {
    return this.map
  }
  def setMap(map: com.badlogic.gdx.maps.tiled.TiledMap): scala.Unit = {
    this.map = map
  }
  def getUnitScale(): scala.Float = {
    return this.unitScale
  }
  def getBatch(): com.badlogic.gdx.graphics.g2d.Batch = {
    return this.batch
  }
  def getViewBounds(): com.badlogic.gdx.math.Rectangle = {
    return this.viewBounds
  }
  @java.lang.Override
  override def setView(camera: com.badlogic.gdx.graphics.OrthographicCamera): scala.Unit = {
    this.batch.setProjectionMatrix(camera.combined)
    val width: scala.Float = camera.viewportWidth * camera.zoom
    val height: scala.Float = camera.viewportHeight * camera.zoom
    val w: scala.Float = (width * java.lang.Math.abs(camera.up.y)) + (height * java.lang.Math.abs(camera.up.x))
    val h: scala.Float = (height * java.lang.Math.abs(camera.up.y)) + (width * java.lang.Math.abs(camera.up.x))
    this.viewBounds.set(camera.position.x - (w / 2), camera.position.y - (h / 2), w, h)
  }
  @java.lang.Override
  override def setView(projection: com.badlogic.gdx.math.Matrix4, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    this.batch.setProjectionMatrix(projection)
    this.viewBounds.set(x, y, width, height)
  }
  @java.lang.Override
  override def render(): scala.Unit = {
    this.beginRender()
    for (layer <- this.map.getLayers()) {
      this.renderMapLayer(layer)
    }
    this.endRender()
  }
  @java.lang.Override
  override def render(layers: scala.Array[scala.Int]): scala.Unit = {
    this.beginRender()
    for (layerIdx <- layers) {
      val layer: com.badlogic.gdx.maps.MapLayer = this.map.getLayers().get(layerIdx)
      this.renderMapLayer(layer)
    }
    this.endRender()
  }
  def renderMapLayer(layer: com.badlogic.gdx.maps.MapLayer): scala.Unit = {
    if (!layer.isVisible()) {
      return
    } else ()
    if (layer.isInstanceOf[com.badlogic.gdx.maps.MapGroupLayer]) {
      val childLayers: com.badlogic.gdx.maps.MapLayers = layer.asInstanceOf[com.badlogic.gdx.maps.MapGroupLayer].getLayers();
      { var i: scala.Int = 0; while (i < childLayers.size()) { {
        val childLayer: com.badlogic.gdx.maps.MapLayer = childLayers.get(i)
        if (!childLayer.isVisible()) {
          /* continue */ ()
        } else ()
        this.renderMapLayer(childLayer)
      }; i = i + 1 } }
    } else {
      if (layer.isInstanceOf[com.badlogic.gdx.maps.tiled.TiledMapTileLayer]) {
        this.renderTileLayer(layer.asInstanceOf[com.badlogic.gdx.maps.tiled.TiledMapTileLayer])
      } else {
        if (layer.isInstanceOf[com.badlogic.gdx.maps.tiled.TiledMapImageLayer]) {
          this.renderImageLayer(layer.asInstanceOf[com.badlogic.gdx.maps.tiled.TiledMapImageLayer])
        } else {
          this.renderObjects(layer)
        }
      }
    }
  }
  @java.lang.Override
  override def renderObjects(layer: com.badlogic.gdx.maps.MapLayer): scala.Unit = {
    for (`object` <- layer.getObjects()) {
      this.renderObject(`object`)
    }
  }
  @java.lang.Override
  override def renderObject(`object`: com.badlogic.gdx.maps.MapObject): scala.Unit = {
    ()
  }
  @java.lang.Override
  override def renderImageLayer(layer: com.badlogic.gdx.maps.tiled.TiledMapImageLayer): scala.Unit = {
    val batchColor: com.badlogic.gdx.graphics.Color = this.batch.getColor()
    val color: scala.Float = this.getImageLayerColor(layer, batchColor)
    val vertices: scala.Array[scala.Float] = this.vertices
    val region: com.badlogic.gdx.graphics.g2d.TextureRegion = layer.getTextureRegion()
    if (region == null) {
      return
    } else ()
    val x: scala.Float = layer.getX()
    val y: scala.Float = layer.getY()
    val x1: scala.Float = (x * this.unitScale) - (this.viewBounds.x * (layer.getParallaxX() - 1))
    val y1: scala.Float = (y * this.unitScale) - (this.viewBounds.y * (layer.getParallaxY() - 1))
    val x2: scala.Float = x1 + (region.getRegionWidth() * this.unitScale)
    val y2: scala.Float = y1 + (region.getRegionHeight() * this.unitScale)
    this.imageBounds.set(x1, y1, x2 - x1, y2 - y1)
    if ((!layer.isRepeatX()) && (!layer.isRepeatY())) {
      if (this.viewBounds.contains(this.imageBounds) || this.viewBounds.overlaps(this.imageBounds)) {
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
        this.batch.draw(region.getTexture(), vertices, 0, BatchTiledMapRenderer.NUM_VERTICES)
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
          this.repeatedImageBounds.set(rx1, ry1, rx2 - rx1, ry2 - ry1)
          if (this.viewBounds.contains(this.repeatedImageBounds) || this.viewBounds.overlaps(this.repeatedImageBounds)) {
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
            this.batch.draw(region.getTexture(), vertices, 0, BatchTiledMapRenderer.NUM_VERTICES)
          } else ()
        }; j = j + 1 } }
      }; i = i + 1 } }
    }
  }
  def getImageLayerColor(layer: com.badlogic.gdx.maps.tiled.TiledMapImageLayer, batchColor: com.badlogic.gdx.graphics.Color): scala.Float = {
    val combinedTint: com.badlogic.gdx.graphics.Color = layer.getCombinedTintColor()
    val supportsTransparency: scala.Boolean = layer.supportsTransparency()
    val alphaMultiplier: scala.Float = if (supportsTransparency) 1.0f else combinedTint.a
    val opacityMultiplier: scala.Float = if (supportsTransparency) combinedTint.a else 1.0f
    return com.badlogic.gdx.graphics.Color.toFloatBits(batchColor.r * (combinedTint.r * alphaMultiplier), batchColor.g * (combinedTint.g * alphaMultiplier), batchColor.b * (combinedTint.b * alphaMultiplier), batchColor.a * (layer.getOpacity() * opacityMultiplier))
  }
  def getTileLayerColor(layer: com.badlogic.gdx.maps.tiled.TiledMapTileLayer, batchColor: com.badlogic.gdx.graphics.Color): scala.Float = {
    return com.badlogic.gdx.graphics.Color.toFloatBits(batchColor.r * layer.getCombinedTintColor().r, batchColor.g * layer.getCombinedTintColor().g, batchColor.b * layer.getCombinedTintColor().b, (batchColor.a * layer.getCombinedTintColor().a) * layer.getOpacity())
  }
  def beginRender(): scala.Unit = {
    com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile.updateAnimationBaseTime()
    this.batch.begin()
  }
  def endRender(): scala.Unit = {
    this.batch.`end`()
  }
  @java.lang.Override
  override def dispose(): scala.Unit = {
    if (this.ownsBatch) {
      this.batch.dispose()
    } else ()
  }
}
object BatchTiledMapRenderer {
  final val NUM_VERTICES: scala.Int = 20
}