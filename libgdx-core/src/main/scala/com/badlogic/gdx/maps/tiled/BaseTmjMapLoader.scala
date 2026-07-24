package com.badlogic.gdx.maps.tiled

abstract class BaseTmjMapLoader[P <: com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters] extends com.badlogic.gdx.maps.tiled.BaseTiledMapLoader[P] {
  var json: com.badlogic.gdx.utils.JsonReader = new com.badlogic.gdx.utils.JsonReader()
  var root: com.badlogic.gdx.utils.JsonValue = null.asInstanceOf[com.badlogic.gdx.utils.JsonValue]
  var templateCache: com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.utils.JsonValue] = null.asInstanceOf[com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.utils.JsonValue]]
  def this(resolver: com.badlogic.gdx.assets.loaders.FileHandleResolver) = {
    this()
  }
  def getDependencies(fileName: java.lang.String, tmjFile: com.badlogic.gdx.files.FileHandle, parameter: P): com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]] = {
    this.root = this.json.parse(tmjFile)
    val textureParameter: com.badlogic.gdx.assets.loaders.TextureLoader.TextureParameter = new com.badlogic.gdx.assets.loaders.TextureLoader.TextureParameter()
    if (parameter != null) {
      textureParameter.genMipMaps = parameter.generateMipMaps
      textureParameter.minFilter = parameter.textureMinFilter
      textureParameter.magFilter = parameter.textureMagFilter
    } else ()
    return this.getDependencyAssetDescriptors(tmjFile, textureParameter)
  }
  def loadTiledMap(tmjFile: com.badlogic.gdx.files.FileHandle, parameter: P, imageResolver: com.badlogic.gdx.maps.ImageResolver): com.badlogic.gdx.maps.tiled.TiledMap = {
    this.map = new com.badlogic.gdx.maps.tiled.TiledMap()
    this.idToObject = new com.badlogic.gdx.utils.IntMap[com.badlogic.gdx.maps.MapObject]()
    this.runOnEndOfLoadTiled = new com.badlogic.gdx.utils.Array[java.lang.Runnable]()
    this.templateCache = new com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.utils.JsonValue]()
    if (parameter != null) {
      this.convertObjectToTileSpace = parameter.convertObjectToTileSpace
      this.flipY = parameter.flipY
      this.loadProjectFile(parameter.projectFilePath)
    } else {
      this.convertObjectToTileSpace = false
      this.flipY = true
    }
    val mapOrientation: java.lang.String = this.root.getString("orientation", null)
    val mapWidth: scala.Int = this.root.getInt("width", 0)
    val mapHeight: scala.Int = this.root.getInt("height", 0)
    val tileWidth: scala.Int = this.root.getInt("tilewidth", 0)
    val tileHeight: scala.Int = this.root.getInt("tileheight", 0)
    val hexSideLength: scala.Int = this.root.getInt("hexsidelength", 0)
    val staggerAxis: java.lang.String = this.root.getString("staggeraxis", null)
    val staggerIndex: java.lang.String = this.root.getString("staggerindex", null)
    val mapBackgroundColor: java.lang.String = this.root.getString("backgroundcolor", null)
    val mapProperties: com.badlogic.gdx.maps.MapProperties = map.getProperties()
    if (mapOrientation != null) {
      mapProperties.put("orientation", mapOrientation)
    } else ()
    mapProperties.put("width", mapWidth.asInstanceOf[java.lang.Object])
    mapProperties.put("height", mapHeight.asInstanceOf[java.lang.Object])
    mapProperties.put("tilewidth", tileWidth.asInstanceOf[java.lang.Object])
    mapProperties.put("tileheight", tileHeight.asInstanceOf[java.lang.Object])
    mapProperties.put("hexsidelength", hexSideLength.asInstanceOf[java.lang.Object])
    if (staggerAxis != null) {
      mapProperties.put("staggeraxis", staggerAxis)
    } else ()
    if (staggerIndex != null) {
      mapProperties.put("staggerindex", staggerIndex)
    } else ()
    if (mapBackgroundColor != null) {
      mapProperties.put("backgroundcolor", mapBackgroundColor)
    } else ()
    this.mapTileWidth = tileWidth
    this.mapTileHeight = tileHeight
    this.mapWidthInPixels = mapWidth * tileWidth
    this.mapHeightInPixels = mapHeight * tileHeight
    if (mapOrientation != null) {
      if ("staggered".equals(mapOrientation)) {
        if (mapHeight > 1) {
          this.mapWidthInPixels = this.mapWidthInPixels + (tileWidth / 2)
          this.mapHeightInPixels = (mapHeightInPixels / 2) + (tileHeight / 2)
        } else ()
      } else ()
    } else ()
    val properties: com.badlogic.gdx.utils.JsonValue = this.root.get("properties")
    if (properties != null) {
      this.loadProperties(map.getProperties(), properties)
    } else ()
    val tileSets: com.badlogic.gdx.utils.JsonValue = this.root.get("tilesets")
    for (element <- tileSets) {
      this.loadTileSet(element, tmjFile, imageResolver)
    }
    val layers: com.badlogic.gdx.utils.JsonValue = this.root.get("layers")
    for (element <- layers) {
      this.loadLayer(map, map.getLayers(), element, tmjFile, imageResolver)
    }
    val groups: com.badlogic.gdx.utils.Array[com.badlogic.gdx.maps.MapGroupLayer] = map.getLayers().getByType(classOf[com.badlogic.gdx.maps.MapGroupLayer])
    while (groups.notEmpty()) {
      val group: com.badlogic.gdx.maps.MapGroupLayer = groups.first()
      groups.removeIndex(0)
      for (child <- group.getLayers()) {
        child.setParallaxX(child.getParallaxX() * group.getParallaxX())
        child.setParallaxY(child.getParallaxY() * group.getParallaxY())
        if (child.isInstanceOf[com.badlogic.gdx.maps.MapGroupLayer]) {
          groups.add(child.asInstanceOf[com.badlogic.gdx.maps.MapGroupLayer])
        } else ()
      }
    }
    for (runnable <- runOnEndOfLoadTiled) {
      runnable.run()
    }
    runOnEndOfLoadTiled = null
    return map
  }
  def loadLayer(map: com.badlogic.gdx.maps.tiled.TiledMap, parentLayers: com.badlogic.gdx.maps.MapLayers, element: com.badlogic.gdx.utils.JsonValue, tmjFile: com.badlogic.gdx.files.FileHandle, imageResolver: com.badlogic.gdx.maps.ImageResolver): scala.Unit = {
    val `type`: java.lang.String = element.getString("type", "")
    `type` match {
      case "group" => {
        this.loadLayerGroup(map, parentLayers, element, tmjFile, imageResolver)
      }
      case "tilelayer" => {
        this.loadTileLayer(map, parentLayers, element)
      }
      case "objectgroup" => {
        this.loadObjectGroup(map, parentLayers, element, tmjFile)
      }
      case "imagelayer" => {
        this.loadImageLayer(map, parentLayers, element, tmjFile, imageResolver)
      }
    }
  }
  def loadLayerGroup(map: com.badlogic.gdx.maps.tiled.TiledMap, parentLayers: com.badlogic.gdx.maps.MapLayers, element: com.badlogic.gdx.utils.JsonValue, tmjFile: com.badlogic.gdx.files.FileHandle, imageResolver: com.badlogic.gdx.maps.ImageResolver): scala.Unit = {
    if (element.getString("type", "").equals("group")) {
      val groupLayer: com.badlogic.gdx.maps.MapGroupLayer = new com.badlogic.gdx.maps.MapGroupLayer()
      this.loadBasicLayerInfo(groupLayer, element)
      val properties: com.badlogic.gdx.utils.JsonValue = element.get("properties")
      if (properties != null) {
        this.loadProperties(groupLayer.getProperties(), properties)
      } else ()
      val layers: com.badlogic.gdx.utils.JsonValue = element.get("layers")
      if (layers != null) {
        for (child <- layers) {
          this.loadLayer(map, groupLayer.getLayers(), child, tmjFile, imageResolver)
        }
      } else ()
      for (layer <- groupLayer.getLayers()) {
        layer.setParent(groupLayer)
      }
      parentLayers.add(groupLayer)
    } else ()
  }
  def loadTileLayer(map: com.badlogic.gdx.maps.tiled.TiledMap, parentLayers: com.badlogic.gdx.maps.MapLayers, element: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    if (element.getString("type", "").equals("tilelayer")) {
      val width: scala.Int = element.getInt("width", 0)
      val height: scala.Int = element.getInt("height", 0)
      val tileWidth: scala.Int = map.getProperties().get[java.lang.Integer]("tilewidth", classOf[java.lang.Integer])
      val tileHeight: scala.Int = map.getProperties().get[java.lang.Integer]("tileheight", classOf[java.lang.Integer])
      val layer: com.badlogic.gdx.maps.tiled.TiledMapTileLayer = new com.badlogic.gdx.maps.tiled.TiledMapTileLayer(width, height, tileWidth, tileHeight)
      this.loadBasicLayerInfo(layer, element)
      val ids: scala.Array[scala.Int] = BaseTmjMapLoader.getTileIds(element, width, height)
      val tileSets: com.badlogic.gdx.maps.tiled.TiledMapTileSets = map.getTileSets();
      { var y: scala.Int = 0; while (y < height) { {
        { var x: scala.Int = 0; while (x < width) { {
          val id: scala.Int = ids((y * width) + x)
          val flipHorizontally: scala.Boolean = (id & com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.FLAG_FLIP_HORIZONTALLY) != 0
          val flipVertically: scala.Boolean = (id & com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.FLAG_FLIP_VERTICALLY) != 0
          val flipDiagonally: scala.Boolean = (id & com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.FLAG_FLIP_DIAGONALLY) != 0
          val tile: com.badlogic.gdx.maps.tiled.TiledMapTile = tileSets.getTile(id & (~com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.MASK_CLEAR))
          if (tile != null) {
            val cell: com.badlogic.gdx.maps.tiled.TiledMapTileLayer.Cell = this.createTileLayerCell(flipHorizontally, flipVertically, flipDiagonally)
            cell.setTile(tile)
            layer.setCell(x, if (flipY) (height - 1) - y else y, cell)
          } else ()
        }; x = x + 1 } }
      }; y = y + 1 } }
      val properties: com.badlogic.gdx.utils.JsonValue = element.get("properties")
      if (properties != null) {
        this.loadProperties(layer.getProperties(), properties)
      } else ()
      parentLayers.add(layer)
    } else ()
  }
  def loadObjectGroup(map: com.badlogic.gdx.maps.tiled.TiledMap, parentLayers: com.badlogic.gdx.maps.MapLayers, element: com.badlogic.gdx.utils.JsonValue, tmjFile: com.badlogic.gdx.files.FileHandle): scala.Unit = {
    if (element.getString("type", "").equals("objectgroup")) {
      val layer: com.badlogic.gdx.maps.MapLayer = new com.badlogic.gdx.maps.MapLayer()
      this.loadBasicLayerInfo(layer, element)
      val properties: com.badlogic.gdx.utils.JsonValue = element.get("properties")
      if (properties != null) {
        this.loadProperties(layer.getProperties(), properties)
      } else ()
      for (objectElement <- element.get("objects")) {
        var elementToLoad: com.badlogic.gdx.utils.JsonValue = objectElement
        if (objectElement.has("template")) {
          elementToLoad = this.resolveTemplateObject(map, layer, objectElement, tmjFile)
        } else ()
        this.loadObject(map, layer, elementToLoad)
      }
      parentLayers.add(layer)
    } else ()
  }
  def loadImageLayer(map: com.badlogic.gdx.maps.tiled.TiledMap, parentLayers: com.badlogic.gdx.maps.MapLayers, element: com.badlogic.gdx.utils.JsonValue, tmjFile: com.badlogic.gdx.files.FileHandle, imageResolver: com.badlogic.gdx.maps.ImageResolver): scala.Unit = {
    if (element.getString("type", "").equals("imagelayer")) {
      val x: scala.Float = element.getFloat("offsetx", 0)
      var y: scala.Float = element.getFloat("offsety", 0)
      if (flipY) {
        y = mapHeightInPixels - y
      } else ()
      val imageSrc: java.lang.String = element.getString("image", "")
      val repeatX: scala.Boolean = element.getInt("repeatx", 0) == 1
      val repeatY: scala.Boolean = element.getInt("repeaty", 0) == 1
      var texture: com.badlogic.gdx.graphics.g2d.TextureRegion = null
      if (!imageSrc.isEmpty()) {
        val handle: com.badlogic.gdx.files.FileHandle = com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.getRelativeFileHandle(tmjFile, imageSrc)
        texture = imageResolver.getImage(handle.path())
        y = y - texture.getRegionHeight()
      } else ()
      val layer: com.badlogic.gdx.maps.tiled.TiledMapImageLayer = new com.badlogic.gdx.maps.tiled.TiledMapImageLayer(texture, x, y, repeatX, repeatY)
      this.loadBasicLayerInfo(layer, element)
      val properties: com.badlogic.gdx.utils.JsonValue = element.get("properties")
      if (properties != null) {
        this.loadProperties(layer.getProperties(), properties)
      } else ()
      parentLayers.add(layer)
    } else ()
  }
  def loadBasicLayerInfo(layer: com.badlogic.gdx.maps.MapLayer, element: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    val name: java.lang.String = element.getString("name")
    val opacity: scala.Float = element.getFloat("opacity", 1.0f)
    val tintColor: java.lang.String = element.getString("tintcolor", "#ffffffff")
    val visible: scala.Boolean = element.getBoolean("visible", true)
    val offsetX: scala.Float = element.getFloat("offsetx", 0)
    val offsetY: scala.Float = element.getFloat("offsety", 0)
    val parallaxX: scala.Float = element.getFloat("parallaxx", 1.0f)
    val parallaxY: scala.Float = element.getFloat("parallaxy", 1.0f)
    layer.setName(name)
    layer.setOpacity(opacity)
    layer.setVisible(visible)
    layer.setOffsetX(offsetX)
    layer.setOffsetY(offsetY)
    layer.setParallaxX(parallaxX)
    layer.setParallaxY(parallaxY)
    layer.setTintColor(com.badlogic.gdx.graphics.Color.valueOf(com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.tiledColorToLibGDXColor(tintColor)))
  }
  def loadObject(map: com.badlogic.gdx.maps.tiled.TiledMap, layer: com.badlogic.gdx.maps.MapLayer, element: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    this.loadObject(map, layer.getObjects(), element, mapHeightInPixels)
  }
  def loadObject(map: com.badlogic.gdx.maps.tiled.TiledMap, tile: com.badlogic.gdx.maps.tiled.TiledMapTile, element: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    this.loadObject(map, tile.getObjects(), element, tile.getTextureRegion().getRegionHeight())
  }
  def loadObject(map: com.badlogic.gdx.maps.tiled.TiledMap, objects: com.badlogic.gdx.maps.MapObjects, element: com.badlogic.gdx.utils.JsonValue, heightInPixels: scala.Float): scala.Unit = {
    var `object`: com.badlogic.gdx.maps.MapObject = null
    val scaleX: scala.Float = if (convertObjectToTileSpace) 1.0f / mapTileWidth else 1.0f
    val scaleY: scala.Float = if (convertObjectToTileSpace) 1.0f / mapTileHeight else 1.0f
    val x: scala.Float = element.getFloat("x", 0) * scaleX
    val y: scala.Float = (if (flipY) heightInPixels - element.getFloat("y", 0) else element.getFloat("y", 0)) * scaleY
    val width: scala.Float = element.getFloat("width", 0) * scaleX
    val height: scala.Float = element.getFloat("height", 0) * scaleY
    if (element.size$field > 0) {
      var child: com.badlogic.gdx.utils.JsonValue = null.asInstanceOf[com.badlogic.gdx.utils.JsonValue]
      if ({
        child = element.get("polygon")
        child
      } != null) {
        val vertices: scala.Array[scala.Float] = new scala.Array[scala.Float](child.size$field * 2)
        var index: scala.Int = 0
        for (point <- child) {
          vertices({ index += 1; index }) = point.getFloat("x", 0) * scaleX
          vertices({ index += 1; index }) = (point.getFloat("y", 0) * scaleY) * (if (flipY) -1 else 1)
        }
        val polygon: com.badlogic.gdx.math.Polygon = new com.badlogic.gdx.math.Polygon(vertices)
        polygon.setPosition(x, y)
        `object` = new com.badlogic.gdx.maps.objects.PolygonMapObject(polygon)
      } else {
        if ({
          child = element.get("polyline")
          child
        } != null) {
          val vertices: scala.Array[scala.Float] = new scala.Array[scala.Float](child.size$field * 2)
          var index: scala.Int = 0
          for (point <- child) {
            vertices({ index += 1; index }) = point.getFloat("x", 0) * scaleX
            vertices({ index += 1; index }) = (point.getFloat("y", 0) * scaleY) * (if (flipY) -1 else 1)
          }
          val polyline: com.badlogic.gdx.math.Polyline = new com.badlogic.gdx.math.Polyline(vertices)
          polyline.setPosition(x, y)
          `object` = new com.badlogic.gdx.maps.objects.PolylineMapObject(polyline)
        } else {
          if (element.get("ellipse") != null) {
            `object` = new com.badlogic.gdx.maps.objects.EllipseMapObject(x, if (flipY) y - height else y, width, height)
          } else {
            if ({
              child = element.get("point")
              child
            } != null) {
              `object` = new com.badlogic.gdx.maps.objects.PointMapObject(x, if (flipY) y - height else y)
            } else {
              if ({
                child = element.get("text")
                child
              } != null) {
                val textMapObject: com.badlogic.gdx.maps.objects.TextMapObject = new com.badlogic.gdx.maps.objects.TextMapObject(x, if (flipY) y - height else y, width, height, child.getString("text", ""))
                textMapObject.setFontFamily(child.getString("fontfamily", ""))
                textMapObject.setPixelSize(child.getInt("pixelSize", 16))
                textMapObject.setHorizontalAlign(child.getString("halign", "left"))
                textMapObject.setVerticalAlign(child.getString("valign", "top"))
                textMapObject.setBold(child.getBoolean("bold", false))
                textMapObject.setItalic(child.getBoolean("italic", false))
                textMapObject.setUnderline(child.getBoolean("underline", false))
                textMapObject.setStrikeout(child.getBoolean("strikeout", false))
                textMapObject.setWrap(child.getBoolean("wrap", false))
                textMapObject.setKerning(child.getBoolean("kerning", true))
                val textColor: java.lang.String = child.getString("color", "#000000")
                textMapObject.setColor(com.badlogic.gdx.graphics.Color.valueOf(com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.tiledColorToLibGDXColor(textColor)))
                `object` = textMapObject
              } else ()
            }
          }
        }
      }
    } else ()
    if (`object` == null) {
      var gid: java.lang.String = null.asInstanceOf[java.lang.String]
      if ({
        gid = element.getString("gid", null)
        gid
      } != null) {
        val id: scala.Int = java.lang.Long.parseLong(gid).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
        val flipHorizontally: scala.Boolean = (id & com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.FLAG_FLIP_HORIZONTALLY) != 0
        val flipVertically: scala.Boolean = (id & com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.FLAG_FLIP_VERTICALLY) != 0
        val tile: com.badlogic.gdx.maps.tiled.TiledMapTile = map.getTileSets().getTile(id & (~com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.MASK_CLEAR))
        val tiledMapTileMapObject: com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject = new com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject(tile, flipHorizontally, flipVertically)
        val textureRegion: com.badlogic.gdx.graphics.g2d.TextureRegion = tiledMapTileMapObject.getTextureRegion()
        tiledMapTileMapObject.getProperties().put("gid", id.asInstanceOf[java.lang.Object])
        tiledMapTileMapObject.setX(x)
        tiledMapTileMapObject.setY(if (flipY) y else y - height)
        val objectWidth: scala.Float = element.getFloat("width", textureRegion.getRegionWidth())
        val objectHeight: scala.Float = element.getFloat("height", textureRegion.getRegionHeight())
        tiledMapTileMapObject.setScaleX(scaleX * (objectWidth / textureRegion.getRegionWidth()))
        tiledMapTileMapObject.setScaleY(scaleY * (objectHeight / textureRegion.getRegionHeight()))
        tiledMapTileMapObject.setRotation(element.getFloat("rotation", 0))
        `object` = tiledMapTileMapObject
      } else {
        `object` = new com.badlogic.gdx.maps.objects.RectangleMapObject(x, if (flipY) y - height else y, width, height)
      }
    } else ()
    `object`.setName(element.getString("name", null))
    val rotation: java.lang.String = element.getString("rotation", null)
    if (rotation != null) {
      `object`.getProperties().put("rotation", java.lang.Float.parseFloat(rotation).asInstanceOf[java.lang.Object])
    } else ()
    val `type`: java.lang.String = element.getString("type", null)
    if (`type` != null) {
      `object`.getProperties().put("type", `type`)
    } else ()
    val id: scala.Int = element.getInt("id", 0)
    if (id != 0) {
      `object`.getProperties().put("id", id.asInstanceOf[java.lang.Object])
    } else ()
    `object`.getProperties().put("x", x.asInstanceOf[java.lang.Object])
    if (`object`.isInstanceOf[com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject]) {
      `object`.getProperties().put("y", y.asInstanceOf[java.lang.Object])
    } else {
      `object`.getProperties().put("y", (if (flipY) y - height else y).asInstanceOf[java.lang.Object])
    }
    `object`.getProperties().put("width", width.asInstanceOf[java.lang.Object])
    `object`.getProperties().put("height", height.asInstanceOf[java.lang.Object])
    `object`.setVisible(element.getBoolean("visible", true))
    val properties: com.badlogic.gdx.utils.JsonValue = element.get("properties")
    if (properties != null) {
      this.loadProperties(`object`.getProperties(), properties)
    } else ()
    this.loadMapPropertiesClassDefaults(`type`, `object`.getProperties())
    idToObject.put(id, `object`)
    objects.add(`object`)
  }
  def resolveTemplateObject(map: com.badlogic.gdx.maps.tiled.TiledMap, layer: com.badlogic.gdx.maps.MapLayer, mapElement: com.badlogic.gdx.utils.JsonValue, tmjFile: com.badlogic.gdx.files.FileHandle): com.badlogic.gdx.utils.JsonValue = {
    val tjFileName: java.lang.String = mapElement.getString("template")
    var templateElement: com.badlogic.gdx.utils.JsonValue = this.templateCache.get(tjFileName)
    if (templateElement == null) {
      try {
        templateElement = this.json.parse(com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.getRelativeFileHandle(tmjFile, tjFileName))
      } catch {
        case e: java.lang.Exception => {
          throw new com.badlogic.gdx.utils.GdxRuntimeException("Error parsing template file: " + tjFileName, e)
        }
      }
      this.templateCache.put(tjFileName, templateElement)
    } else ()
    val templateObjectElement: com.badlogic.gdx.utils.JsonValue = templateElement.get("object")
    return this.mergeParentElementWithTemplate(mapElement, templateObjectElement)
  }
  def mergeJsonObject(parentObject: com.badlogic.gdx.utils.JsonValue, templateObject: com.badlogic.gdx.utils.JsonValue): com.badlogic.gdx.utils.JsonValue = {
    if (templateObject == null) {
      return parentObject
    } else ()
    if (parentObject == null) {
      return templateObject
    } else ()
    val merged: com.badlogic.gdx.utils.JsonValue = new com.badlogic.gdx.utils.JsonValue(com.badlogic.gdx.utils.JsonValue.ValueType.`object`)
    for (child <- templateObject) {
      merged.addChild(child.name(), this.cloneElementShallow(child))
    }
    for (child <- parentObject) {
      merged.setChild(child.name(), this.cloneElementShallow(child))
    }
    return merged
  }
  def cloneElementShallow(src: com.badlogic.gdx.utils.JsonValue): com.badlogic.gdx.utils.JsonValue = {
    var clone: com.badlogic.gdx.utils.JsonValue = null.asInstanceOf[com.badlogic.gdx.utils.JsonValue]
    src.`type`() match {
      case com.badlogic.gdx.utils.JsonValue.ValueType.stringValue => {
        clone = new com.badlogic.gdx.utils.JsonValue(src.asString())
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.doubleValue => {
        clone = new com.badlogic.gdx.utils.JsonValue(src.asDouble())
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.longValue => {
        clone = new com.badlogic.gdx.utils.JsonValue(src.asLong())
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.booleanValue => {
        clone = new com.badlogic.gdx.utils.JsonValue(src.asBoolean())
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.nullValue => {
        clone = new com.badlogic.gdx.utils.JsonValue(null.asInstanceOf[java.lang.String])
      }
      case _ => {
        clone = new com.badlogic.gdx.utils.JsonValue(src)
      }
    }
    clone.setName(src.name())
    return clone
  }
  def mergeJsonProperties(parentProps: com.badlogic.gdx.utils.JsonValue, templateProps: com.badlogic.gdx.utils.JsonValue): com.badlogic.gdx.utils.JsonValue = {
    if (templateProps == null) {
      return parentProps
    } else ()
    if (parentProps == null) {
      return templateProps
    } else ()
    val merged: com.badlogic.gdx.utils.JsonValue = new com.badlogic.gdx.utils.JsonValue(com.badlogic.gdx.utils.JsonValue.ValueType.array)
    for (property <- templateProps) {
      merged.addChild(new com.badlogic.gdx.utils.JsonValue(property))
    }
    for (property <- parentProps) {
      val propName: java.lang.String = property.getString("name", null)
      if (propName == null) {
        /* continue */ ()
      } else ()
      for (child <- merged) {
        if (propName.equals(child.getString("name", null))) {
          child.remove()
          /* break */ ()
        } else ()
      }
      merged.addChild(new com.badlogic.gdx.utils.JsonValue(property))
    }
    return merged
  }
  def mergeParentElementWithTemplate(parent: com.badlogic.gdx.utils.JsonValue, template: com.badlogic.gdx.utils.JsonValue): com.badlogic.gdx.utils.JsonValue = {
    if (template == null) {
      return parent
    } else ()
    if (parent == null) {
      return template
    } else ()
    val merged: com.badlogic.gdx.utils.JsonValue = new com.badlogic.gdx.utils.JsonValue(com.badlogic.gdx.utils.JsonValue.ValueType.`object`)
    for (child <- template) {
      merged.addChild(this.cloneElementShallow(child))
    }
    for (child <- parent) {
      val key: java.lang.String = child.name()
      key match {
        case "properties" => {
          merged.setChild(key, this.mergeJsonProperties(child, template.get("properties")))
        }
        case "text" => {
          merged.setChild(key, this.mergeJsonObject(child, template.get("text")))
        }
        case _ => {
          merged.setChild(key, this.cloneElementShallow(child))
        }
      }
    }
    return merged
  }
  private def loadProperties(properties: com.badlogic.gdx.maps.MapProperties, element: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    if ((element == null) || (!"properties".equals(element.name()))) {
      return
    } else ()
    for (property <- element) {
      val name: java.lang.String = property.getString("name", null)
      var value: java.lang.String = property.getString("value", null)
      val `type`: java.lang.String = property.getString("type", null)
      if ((value == null) && (!"class".equals(`type`))) {
        value = property.asString()
      } else ()
      `type` match {
        case "object" => {
          this.loadObjectProperty(properties, name, value)
        }
        case "class" => {
          val classProperties: com.badlogic.gdx.maps.MapProperties = new com.badlogic.gdx.maps.MapProperties()
          val className: java.lang.String = property.getString("propertytype")
          classProperties.put("type", className)
          properties.put(name, classProperties)
          this.loadJsonClassProperties(className, classProperties, property.get("value"))
        }
        case _ => {
          this.loadBasicProperty(properties, name, value, `type`)
        }
      }
    }
  }
  def loadTileSet(element$arg: com.badlogic.gdx.utils.JsonValue, tmjFile: com.badlogic.gdx.files.FileHandle, imageResolver: com.badlogic.gdx.maps.ImageResolver): scala.Unit = {
    var element: com.badlogic.gdx.utils.JsonValue = element$arg
    if (element.getString("firstgid") != null) {
      val firstgid: scala.Int = element.getInt("firstgid", 1)
      var imageSource: java.lang.String = ""
      var imageWidth: scala.Int = 0
      var imageHeight: scala.Int = 0
      var image: com.badlogic.gdx.files.FileHandle = null
      val source: java.lang.String = element.getString("source", null)
      if (source != null) {
        val tsj: com.badlogic.gdx.files.FileHandle = com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.getRelativeFileHandle(tmjFile, source)
        try {
          element = this.json.parse(tsj)
          if (element.has("image")) {
            imageSource = element.getString("image")
            imageWidth = element.getInt("imagewidth", 0)
            imageHeight = element.getInt("imageheight", 0)
            image = com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.getRelativeFileHandle(tsj, imageSource)
          } else ()
        } catch {
          case e: com.badlogic.gdx.utils.SerializationException => {
            throw new com.badlogic.gdx.utils.GdxRuntimeException("Error parsing external tileSet.")
          }
        }
      } else {
        if (element.has("image")) {
          imageSource = element.getString("image")
          imageWidth = element.getInt("imagewidth", 0)
          imageHeight = element.getInt("imageheight", 0)
          image = com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.getRelativeFileHandle(tmjFile, imageSource)
        } else ()
      }
      val name: java.lang.String = element.getString("name", null)
      val tilewidth: scala.Int = element.getInt("tilewidth", 0)
      val tileheight: scala.Int = element.getInt("tileheight", 0)
      val spacing: scala.Int = element.getInt("spacing", 0)
      val margin: scala.Int = element.getInt("margin", 0)
      val offset: com.badlogic.gdx.utils.JsonValue = element.get("tileoffset")
      var offsetX: scala.Int = 0
      var offsetY: scala.Int = 0
      if (offset != null) {
        offsetX = offset.getInt("x", 0)
        offsetY = offset.getInt("y", 0)
      } else ()
      val tileSet: com.badlogic.gdx.maps.tiled.TiledMapTileSet = new com.badlogic.gdx.maps.tiled.TiledMapTileSet()
      tileSet.setName(name)
      val tileSetProperties: com.badlogic.gdx.maps.MapProperties = tileSet.getProperties()
      val properties: com.badlogic.gdx.utils.JsonValue = element.get("properties")
      if (properties != null) {
        this.loadProperties(tileSetProperties, properties)
      } else ()
      tileSetProperties.put("firstgid", firstgid.asInstanceOf[java.lang.Object])
      var tiles: com.badlogic.gdx.utils.JsonValue = element.get("tiles")
      if (tiles == null) {
        tiles = new com.badlogic.gdx.utils.JsonValue(com.badlogic.gdx.utils.JsonValue.ValueType.array)
      } else ()
      this.addStaticTiles(tmjFile, imageResolver, tileSet, element, tiles, name, firstgid, tilewidth, tileheight, spacing, margin, source, offsetX, offsetY, imageSource, imageWidth, imageHeight, image)
      val animatedTiles: com.badlogic.gdx.utils.Array[com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile]()
      for (tileElement <- tiles) {
        val localtid: scala.Int = tileElement.getInt("id", 0)
        var tile: com.badlogic.gdx.maps.tiled.TiledMapTile = tileSet.getTile(firstgid + localtid)
        if (tile != null) {
          val animatedTile: com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile = this.createAnimatedTile(tileSet, tile, tileElement, firstgid)
          if (animatedTile != null) {
            animatedTiles.add(animatedTile)
            tile = animatedTile
          } else ()
          this.addTileProperties(tile, tileElement)
          this.addTileObjectGroup(tile, tileElement)
        } else ()
      }
      for (animatedTile <- animatedTiles) {
        tileSet.putTile(animatedTile.getId(), animatedTile)
      }
      map.getTileSets().addTileSet(tileSet)
    } else ()
  }
  def addStaticTiles(tmjFile: com.badlogic.gdx.files.FileHandle, imageResolver: com.badlogic.gdx.maps.ImageResolver, tileSet: com.badlogic.gdx.maps.tiled.TiledMapTileSet, element: com.badlogic.gdx.utils.JsonValue, tiles: com.badlogic.gdx.utils.JsonValue, name: java.lang.String, firstgid: scala.Int, tilewidth: scala.Int, tileheight: scala.Int, spacing: scala.Int, margin: scala.Int, source: java.lang.String, offsetX: scala.Int, offsetY: scala.Int, imageSource: java.lang.String, imageWidth: scala.Int, imageHeight: scala.Int, image: com.badlogic.gdx.files.FileHandle): scala.Unit
  private def addTileProperties(tile: com.badlogic.gdx.maps.tiled.TiledMapTile, tileElement: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    val terrain: java.lang.String = tileElement.getString("terrain", null)
    val tileProperties: com.badlogic.gdx.maps.MapProperties = tile.getProperties()
    if (terrain != null) {
      tileProperties.put("terrain", terrain)
    } else ()
    val probability: java.lang.String = tileElement.getString("probability", null)
    if (probability != null) {
      tileProperties.put("probability", probability)
    } else ()
    val `type`: java.lang.String = tileElement.getString("type", null)
    if (`type` != null) {
      tileProperties.put("type", `type`)
    } else ()
    val properties: com.badlogic.gdx.utils.JsonValue = tileElement.get("properties")
    if (properties != null) {
      this.loadProperties(tileProperties, properties)
    } else ()
    this.loadMapPropertiesClassDefaults(`type`, tileProperties)
  }
  private def addTileObjectGroup(tile: com.badlogic.gdx.maps.tiled.TiledMapTile, tileElement: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    val objectgroupElement: com.badlogic.gdx.utils.JsonValue = tileElement.get("objectgroup")
    if (objectgroupElement != null) {
      for (objectElement <- objectgroupElement.get("objects")) {
        this.loadObject(this.map, tile, objectElement)
      }
    } else ()
  }
  def createAnimatedTile(tileSet: com.badlogic.gdx.maps.tiled.TiledMapTileSet, tile: com.badlogic.gdx.maps.tiled.TiledMapTile, tileElement: com.badlogic.gdx.utils.JsonValue, firstgid: scala.Int): com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile = {
    val animationElement: com.badlogic.gdx.utils.JsonValue = tileElement.get("animation")
    if (animationElement != null) {
      val staticTiles: com.badlogic.gdx.utils.Array[com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile]()
      val intervals: com.badlogic.gdx.utils.IntArray = new com.badlogic.gdx.utils.IntArray()
      for (frameValue <- animationElement) {
        staticTiles.add(tileSet.getTile(firstgid + frameValue.getInt("tileid")).asInstanceOf[com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile])
        intervals.add(frameValue.getInt("duration"))
      }
      val animatedTile: com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile = new com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile(intervals, staticTiles)
      animatedTile.setId(tile.getId())
      return animatedTile
    } else ()
    return null
  }
}
object BaseTmjMapLoader {
  export com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.{getTileIds => _, *}
  def getTileIds(element: com.badlogic.gdx.utils.JsonValue, width: scala.Int, height: scala.Int): scala.Array[scala.Int] = {
    val data: com.badlogic.gdx.utils.JsonValue = element.get("data")
    val encoding: java.lang.String = element.getString("encoding", null)
    var ids: scala.Array[scala.Int] = null.asInstanceOf[scala.Array[scala.Int]]
    if (((encoding == null) || encoding.isEmpty()) || encoding.equals("csv")) {
      ids = data.asIntArray()
    } else {
      if (encoding.equals("base64")) {
        var is: java.io.InputStream = null
        try {
          val compression: java.lang.String = element.getString("compression", null)
          val bytes: scala.Array[scala.Byte] = com.badlogic.gdx.utils.Base64Coder.decode(data.asString())
          if ((compression == null) || compression.isEmpty()) {
            is = new java.io.ByteArrayInputStream(bytes)
          } else {
            if (compression.equals("gzip")) {
              is = new java.io.BufferedInputStream(new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(bytes), bytes.length))
            } else {
              if (compression.equals("zlib")) {
                is = new java.io.BufferedInputStream(new java.util.zip.InflaterInputStream(new java.io.ByteArrayInputStream(bytes)))
              } else {
                throw new com.badlogic.gdx.utils.GdxRuntimeException(("Unrecognised compression (" + compression) + ") for TMJ Layer Data")
              }
            }
          }
          val temp: scala.Array[scala.Byte] = new scala.Array[scala.Byte](4)
          ids = new scala.Array[scala.Int](width * height);
          { var y: scala.Int = 0; while (y < height) { {
            { var x: scala.Int = 0; while (x < width) { {
              var read: scala.Int = is.read(temp)
              while (read < temp.length) {
                val curr: scala.Int = is.read(temp, read, temp.length - read)
                if (curr == (-1)) {
                  /* break */ ()
                } else ()
                read = read + curr
              }
              if (read != temp.length) {
                throw new com.badlogic.gdx.utils.GdxRuntimeException("Error Reading TMJ Layer Data: Premature end of tile data")
              } else ()
              ids((y * width) + x) = ((com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.unsignedByteToInt(temp(0)) | (com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.unsignedByteToInt(temp(1)) << 8)) | (com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.unsignedByteToInt(temp(2)) << 16)) | (com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.unsignedByteToInt(temp(3)) << 24)
            }; x = x + 1 } }
          }; y = y + 1 } }
        } catch {
          case e: java.io.IOException => {
            throw new com.badlogic.gdx.utils.GdxRuntimeException("Error Reading TMJ Layer Data - IOException: " + e.getMessage())
          }
        } finally {
          com.badlogic.gdx.utils.StreamUtils.closeQuietly(is)
        }
      } else {
        throw new com.badlogic.gdx.utils.GdxRuntimeException(("Unrecognised encoding (" + encoding) + ") for TMJ Layer Data")
      }
    }
    return ids
  }
}