package com.badlogic.gdx.maps.tiled.renderers

class OrthoCachedTiledMapRenderer(map$p: com.badlogic.gdx.maps.tiled.TiledMap, unitScale$p: scala.Float, cacheSize: scala.Int) extends com.badlogic.gdx.maps.tiled.TiledMapRenderer with com.badlogic.gdx.utils.Disposable {
  var map: com.badlogic.gdx.maps.tiled.TiledMap = null.asInstanceOf[com.badlogic.gdx.maps.tiled.TiledMap]
  var spriteCache: com.badlogic.gdx.graphics.g2d.SpriteCache = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.SpriteCache]
  final val vertices: scala.Array[scala.Float] = new scala.Array[scala.Float](20)
  var blending: scala.Boolean = false
  var unitScale: scala.Float = 0.0f
  final val viewBounds: com.badlogic.gdx.math.Rectangle = new com.badlogic.gdx.math.Rectangle()
  final val cacheBounds: com.badlogic.gdx.math.Rectangle = new com.badlogic.gdx.math.Rectangle()
  var overCache: scala.Float = 0.5f
  var maxTileWidth: scala.Float = 0.0f
  var maxTileHeight: scala.Float = 0.0f
  var cached: scala.Boolean = false
  var count: scala.Int = 0
  var canCacheMoreN: scala.Boolean = false
  var canCacheMoreE: scala.Boolean = false
  var canCacheMoreW: scala.Boolean = false
  var canCacheMoreS: scala.Boolean = false
  var imageBounds: com.badlogic.gdx.math.Rectangle = new com.badlogic.gdx.math.Rectangle()
  def this(map: com.badlogic.gdx.maps.tiled.TiledMap) = {
    this(map, 1, 2000)
  }
  def this(map: com.badlogic.gdx.maps.tiled.TiledMap, unitScale: scala.Float) = {
    this(map, unitScale, 2000)
  }
  this.map = map$p
  this.unitScale = unitScale$p
  this.spriteCache = new com.badlogic.gdx.graphics.g2d.SpriteCache(cacheSize, true)
  def setView(camera: com.badlogic.gdx.graphics.OrthographicCamera): scala.Unit = {
    this.spriteCache.setProjectionMatrix(camera.combined)
    val width: scala.Float = (camera.viewportWidth * camera.zoom) + ((this.maxTileWidth * 2) * this.unitScale)
    val height: scala.Float = (camera.viewportHeight * camera.zoom) + ((this.maxTileHeight * 2) * this.unitScale)
    this.viewBounds.set(camera.position.x - (width / 2), camera.position.y - (height / 2), width, height)
    if ((((this.canCacheMoreW && (this.viewBounds.x < (this.cacheBounds.x - OrthoCachedTiledMapRenderer.tolerance))) || (this.canCacheMoreS && (this.viewBounds.y < (this.cacheBounds.y - OrthoCachedTiledMapRenderer.tolerance)))) || (this.canCacheMoreE && ((this.viewBounds.x + this.viewBounds.width) > ((this.cacheBounds.x + this.cacheBounds.width) + OrthoCachedTiledMapRenderer.tolerance)))) || (this.canCacheMoreN && ((this.viewBounds.y + this.viewBounds.height) > ((this.cacheBounds.y + this.cacheBounds.height) + OrthoCachedTiledMapRenderer.tolerance)))) {
      this.cached = false
    } else ()
  }
  def setView(projection: com.badlogic.gdx.math.Matrix4, x$arg: scala.Float, y$arg: scala.Float, width$arg: scala.Float, height$arg: scala.Float): scala.Unit = {
    var x: scala.Float = x$arg
    var y: scala.Float = y$arg
    var width: scala.Float = width$arg
    var height: scala.Float = height$arg
    this.spriteCache.setProjectionMatrix(projection)
    x = x - (this.maxTileWidth * this.unitScale)
    y = y - (this.maxTileHeight * this.unitScale)
    width = width + ((this.maxTileWidth * 2) * this.unitScale)
    height = height + ((this.maxTileHeight * 2) * this.unitScale)
    this.viewBounds.set(x, y, width, height)
    if ((((this.canCacheMoreW && (this.viewBounds.x < (this.cacheBounds.x - OrthoCachedTiledMapRenderer.tolerance))) || (this.canCacheMoreS && (this.viewBounds.y < (this.cacheBounds.y - OrthoCachedTiledMapRenderer.tolerance)))) || (this.canCacheMoreE && ((this.viewBounds.x + this.viewBounds.width) > ((this.cacheBounds.x + this.cacheBounds.width) + OrthoCachedTiledMapRenderer.tolerance)))) || (this.canCacheMoreN && ((this.viewBounds.y + this.viewBounds.height) > ((this.cacheBounds.y + this.cacheBounds.height) + OrthoCachedTiledMapRenderer.tolerance)))) {
      this.cached = false
    } else ()
  }
  def render(): scala.Unit = {
    if (!this.cached) {
      this.cached = true
      this.count = 0
      this.spriteCache.clear()
      val extraWidth: scala.Float = this.viewBounds.width * this.overCache
      val extraHeight: scala.Float = this.viewBounds.height * this.overCache
      this.cacheBounds.x = this.viewBounds.x - extraWidth
      this.cacheBounds.y = this.viewBounds.y - extraHeight
      this.cacheBounds.width = this.viewBounds.width + (extraWidth * 2)
      this.cacheBounds.height = this.viewBounds.height + (extraHeight * 2)
      for (layer <- this.map.getLayers()) {
        this.spriteCache.beginCache()
        if (layer.isInstanceOf[com.badlogic.gdx.maps.tiled.TiledMapTileLayer]) {
          this.renderTileLayer(layer.asInstanceOf[com.badlogic.gdx.maps.tiled.TiledMapTileLayer])
        } else {
          if (layer.isInstanceOf[com.badlogic.gdx.maps.tiled.TiledMapImageLayer]) {
            this.renderImageLayer(layer.asInstanceOf[com.badlogic.gdx.maps.tiled.TiledMapImageLayer])
          } else ()
        }
        this.spriteCache.endCache()
      }
    } else ()
    if (this.blending) {
      com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND)
      com.badlogic.gdx.Gdx.gl.glBlendFunc(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA)
    } else ()
    this.spriteCache.begin()
    val mapLayers: com.badlogic.gdx.maps.MapLayers = this.map.getLayers();
    { var i: scala.Int = 0; val j: scala.Int = mapLayers.getCount(); while (i < j) { {
      val layer: com.badlogic.gdx.maps.MapLayer = mapLayers.get(i)
      if (layer.isVisible()) {
        this.spriteCache.draw(i)
        this.renderObjects(layer)
      } else ()
    }; i = i + 1 } }
    this.spriteCache.`end`()
    if (this.blending) {
      com.badlogic.gdx.Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND)
    } else ()
  }
  def render(layers: scala.Array[scala.Int]): scala.Unit = {
    if (!this.cached) {
      this.cached = true
      this.count = 0
      this.spriteCache.clear()
      val extraWidth: scala.Float = this.viewBounds.width * this.overCache
      val extraHeight: scala.Float = this.viewBounds.height * this.overCache
      this.cacheBounds.x = this.viewBounds.x - extraWidth
      this.cacheBounds.y = this.viewBounds.y - extraHeight
      this.cacheBounds.width = this.viewBounds.width + (extraWidth * 2)
      this.cacheBounds.height = this.viewBounds.height + (extraHeight * 2)
      for (layer <- this.map.getLayers()) {
        this.spriteCache.beginCache()
        if (layer.isInstanceOf[com.badlogic.gdx.maps.tiled.TiledMapTileLayer]) {
          this.renderTileLayer(layer.asInstanceOf[com.badlogic.gdx.maps.tiled.TiledMapTileLayer])
        } else {
          if (layer.isInstanceOf[com.badlogic.gdx.maps.tiled.TiledMapImageLayer]) {
            this.renderImageLayer(layer.asInstanceOf[com.badlogic.gdx.maps.tiled.TiledMapImageLayer])
          } else ()
        }
        this.spriteCache.endCache()
      }
    } else ()
    if (this.blending) {
      com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND)
      com.badlogic.gdx.Gdx.gl.glBlendFunc(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA)
    } else ()
    this.spriteCache.begin()
    val mapLayers: com.badlogic.gdx.maps.MapLayers = this.map.getLayers()
    for (i <- layers) {
      val layer: com.badlogic.gdx.maps.MapLayer = mapLayers.get(i)
      if (layer.isVisible()) {
        this.spriteCache.draw(i)
        this.renderObjects(layer)
      } else ()
    }
    this.spriteCache.`end`()
    if (this.blending) {
      com.badlogic.gdx.Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND)
    } else ()
  }
  def renderObjects(layer: com.badlogic.gdx.maps.MapLayer): scala.Unit = {
    for (`object` <- layer.getObjects()) {
      this.renderObject(`object`)
    }
  }
  def renderObject(`object`: com.badlogic.gdx.maps.MapObject): scala.Unit = {
    ()
  }
  def renderTileLayer(layer: com.badlogic.gdx.maps.tiled.TiledMapTileLayer): scala.Unit = {
    val color: scala.Float = com.badlogic.gdx.graphics.Color.toFloatBits(layer.getCombinedTintColor().r, layer.getCombinedTintColor().g, layer.getCombinedTintColor().b, layer.getOpacity() * layer.getCombinedTintColor().a)
    val layerWidth: scala.Int = layer.getWidth()
    val layerHeight: scala.Int = layer.getHeight()
    val layerTileWidth: scala.Float = layer.getTileWidth() * this.unitScale
    val layerTileHeight: scala.Float = layer.getTileHeight() * this.unitScale
    val layerOffsetX: scala.Float = (layer.getRenderOffsetX() * this.unitScale) - (this.viewBounds.x * (layer.getParallaxX() - 1))
    val layerOffsetY: scala.Float = ((-layer.getRenderOffsetY()) * this.unitScale) - (this.viewBounds.y * (layer.getParallaxY() - 1))
    val col1: scala.Int = java.lang.Math.max(0, ((this.cacheBounds.x - layerOffsetX) / layerTileWidth).asInstanceOf[scala.Int].asInstanceOf[scala.Int])
    val col2: scala.Int = java.lang.Math.min(layerWidth, ((((this.cacheBounds.x + this.cacheBounds.width) + layerTileWidth) - layerOffsetX) / layerTileWidth).asInstanceOf[scala.Int].asInstanceOf[scala.Int])
    val row1: scala.Int = java.lang.Math.max(0, ((this.cacheBounds.y - layerOffsetY) / layerTileHeight).asInstanceOf[scala.Int].asInstanceOf[scala.Int])
    val row2: scala.Int = java.lang.Math.min(layerHeight, ((((this.cacheBounds.y + this.cacheBounds.height) + layerTileHeight) - layerOffsetY) / layerTileHeight).asInstanceOf[scala.Int].asInstanceOf[scala.Int])
    this.canCacheMoreN = row2 < layerHeight
    this.canCacheMoreE = col2 < layerWidth
    this.canCacheMoreW = col1 > 0
    this.canCacheMoreS = row1 > 0
    val vertices: scala.Array[scala.Float] = this.vertices;
    { var row: scala.Int = row2; while (row >= row1) { {
      { var col: scala.Int = col1; while (col < col2) { {
        val cell: com.badlogic.gdx.maps.tiled.TiledMapTileLayer.Cell = layer.getCell(col, row)
        if (cell == null) {
          /* continue */ ()
        } else ()
        val tile: com.badlogic.gdx.maps.tiled.TiledMapTile = cell.getTile()
        if (tile == null) {
          /* continue */ ()
        } else ()
        this.count = this.count + 1
        val flipX: scala.Boolean = cell.getFlipHorizontally()
        val flipY: scala.Boolean = cell.getFlipVertically()
        val rotations: scala.Int = cell.getRotation()
        val region: com.badlogic.gdx.graphics.g2d.TextureRegion = tile.getTextureRegion()
        val texture: com.badlogic.gdx.graphics.Texture = region.getTexture()
        val x1: scala.Float = ((col * layerTileWidth) + (tile.getOffsetX() * this.unitScale)) + layerOffsetX
        val y1: scala.Float = ((row * layerTileHeight) + (tile.getOffsetY() * this.unitScale)) + layerOffsetY
        val x2: scala.Float = x1 + (region.getRegionWidth() * this.unitScale)
        val y2: scala.Float = y1 + (region.getRegionHeight() * this.unitScale)
        val adjustX: scala.Float = 0.5f / texture.getWidth()
        val adjustY: scala.Float = 0.5f / texture.getHeight()
        val u1: scala.Float = region.getU() + adjustX
        val v1: scala.Float = region.getV2() - adjustY
        val u2: scala.Float = region.getU2() - adjustX
        val v2: scala.Float = region.getV() + adjustY
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
        this.spriteCache.add(texture, vertices, 0, OrthoCachedTiledMapRenderer.NUM_VERTICES)
      }; col = col + 1 } }
    }; row = row - 1 } }
  }
  def renderImageLayer(layer: com.badlogic.gdx.maps.tiled.TiledMapImageLayer): scala.Unit = {
    val combinedTint: com.badlogic.gdx.graphics.Color = layer.getCombinedTintColor()
    val supportsTransparency: scala.Boolean = layer.supportsTransparency()
    val alphaMultiplier: scala.Float = if (supportsTransparency) 1.0f else combinedTint.a
    val opacityMultiplier: scala.Float = if (supportsTransparency) combinedTint.a else 1.0f
    val color: scala.Float = com.badlogic.gdx.graphics.Color.toFloatBits(combinedTint.r * alphaMultiplier, combinedTint.g * alphaMultiplier, combinedTint.b * alphaMultiplier, layer.getOpacity() * opacityMultiplier)
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
      this.spriteCache.add(region.getTexture(), vertices, 0, OrthoCachedTiledMapRenderer.NUM_VERTICES)
    } else {
      val repeatX: scala.Int = if (layer.isRepeatX()) java.lang.Math.ceil((this.cacheBounds.width / this.imageBounds.width) + 4).asInstanceOf[scala.Int] else 0
      val repeatY: scala.Int = if (layer.isRepeatY()) java.lang.Math.ceil((this.cacheBounds.height / this.imageBounds.height) + 4).asInstanceOf[scala.Int] else 0
      var startX: scala.Float = this.cacheBounds.x
      var startY: scala.Float = this.cacheBounds.y
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
          this.spriteCache.add(region.getTexture(), vertices, 0, OrthoCachedTiledMapRenderer.NUM_VERTICES)
        }; j = j + 1 } }
      }; i = i + 1 } }
    }
  }
  def invalidateCache(): scala.Unit = {
    this.cached = false
  }
  def isCached(): scala.Boolean = {
    return this.cached
  }
  def setOverCache(overCache: scala.Float): scala.Unit = {
    this.overCache = overCache
  }
  def setMaxTileSize(maxPixelWidth: scala.Float, maxPixelHeight: scala.Float): scala.Unit = {
    this.maxTileWidth = maxPixelWidth
    this.maxTileHeight = maxPixelHeight
  }
  def setBlending(blending: scala.Boolean): scala.Unit = {
    this.blending = blending
  }
  def getSpriteCache(): com.badlogic.gdx.graphics.g2d.SpriteCache = {
    return this.spriteCache
  }
  def dispose(): scala.Unit = {
    this.spriteCache.dispose()
  }
}
object OrthoCachedTiledMapRenderer {
  private final val tolerance: scala.Float = 1.0E-5f
  final val NUM_VERTICES: scala.Int = 20
}