package com.badlogic.gdx.maps.tiled

abstract class BaseTmxMapLoader[P <: com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters](resolver$p: com.badlogic.gdx.assets.loaders.FileHandleResolver) extends com.badlogic.gdx.maps.tiled.BaseTiledMapLoader[P](resolver$p) {
  var xml: com.badlogic.gdx.utils.XmlReader = new com.badlogic.gdx.utils.XmlReader()
  var root: com.badlogic.gdx.utils.XmlReader.Element = null.asInstanceOf[com.badlogic.gdx.utils.XmlReader.Element]
  var templateCache: com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.utils.XmlReader.Element] = null.asInstanceOf[com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.utils.XmlReader.Element]]
  @java.lang.Override
  def getDependencies(fileName: java.lang.String, tmxFile: com.badlogic.gdx.files.FileHandle, parameter: P): com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]] = {
    this.root = this.xml.parse(tmxFile)
    val textureParameter: com.badlogic.gdx.assets.loaders.TextureLoader.TextureParameter = new com.badlogic.gdx.assets.loaders.TextureLoader.TextureParameter()
    if (parameter != null) {
      textureParameter.genMipMaps = parameter.generateMipMaps
      textureParameter.minFilter = parameter.textureMinFilter
      textureParameter.magFilter = parameter.textureMagFilter
    } else ()
    return this.getDependencyAssetDescriptors(tmxFile, textureParameter).asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]]
  }
  @java.lang.Override
  def loadTiledMap(tmxFile: com.badlogic.gdx.files.FileHandle, parameter: P, imageResolver: com.badlogic.gdx.maps.ImageResolver): com.badlogic.gdx.maps.tiled.TiledMap = {
    this.map = new com.badlogic.gdx.maps.tiled.TiledMap()
    this.idToObject = new com.badlogic.gdx.utils.IntMap[com.badlogic.gdx.maps.MapObject]()
    this.runOnEndOfLoadTiled = new com.badlogic.gdx.utils.Array[java.lang.Runnable]()
    this.templateCache = new com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.utils.XmlReader.Element]()
    if (parameter != null) {
      this.convertObjectToTileSpace = parameter.convertObjectToTileSpace
      this.flipY = parameter.flipY
      this.loadProjectFile(parameter.projectFilePath)
    } else {
      this.convertObjectToTileSpace = false
      this.flipY = true
    }
    val mapOrientation: java.lang.String = this.root.getAttribute("orientation", null)
    val mapWidth: scala.Int = this.root.getIntAttribute("width", 0)
    val mapHeight: scala.Int = this.root.getIntAttribute("height", 0)
    val tileWidth: scala.Int = this.root.getIntAttribute("tilewidth", 0)
    val tileHeight: scala.Int = this.root.getIntAttribute("tileheight", 0)
    val hexSideLength: scala.Int = this.root.getIntAttribute("hexsidelength", 0)
    val staggerAxis: java.lang.String = this.root.getAttribute("staggeraxis", null)
    val staggerIndex: java.lang.String = this.root.getAttribute("staggerindex", null)
    val mapBackgroundColor: java.lang.String = this.root.getAttribute("backgroundcolor", null)
    val mapProperties: com.badlogic.gdx.maps.MapProperties = map.getProperties()
    if (mapOrientation != null) {
      mapProperties.put("orientation", mapOrientation)
    } else ()
    mapProperties.put("width", mapWidth.asInstanceOf[java.lang.Integer])
    mapProperties.put("height", mapHeight.asInstanceOf[java.lang.Integer])
    mapProperties.put("tilewidth", tileWidth.asInstanceOf[java.lang.Integer])
    mapProperties.put("tileheight", tileHeight.asInstanceOf[java.lang.Integer])
    mapProperties.put("hexsidelength", hexSideLength.asInstanceOf[java.lang.Integer])
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
    val properties: com.badlogic.gdx.utils.XmlReader.Element = this.root.getChildByName("properties")
    if (properties != null) {
      this.loadProperties(map.getProperties(), properties)
    } else ()
    val tilesets: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.XmlReader.Element] = this.root.getChildrenByName("tileset")
    for (element <- tilesets) {
      this.loadTileSet(element, tmxFile, imageResolver)
      this.root.removeChild(element)
    };
    { var i: scala.Int = 0; val j: scala.Int = this.root.getChildCount(); while (i < j) { {
      val element: com.badlogic.gdx.utils.XmlReader.Element = this.root.getChild(i)
      this.loadLayer(map, map.getLayers(), element, tmxFile, imageResolver)
    }; i = i + 1 } }
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
  def loadLayer(map: com.badlogic.gdx.maps.tiled.TiledMap, parentLayers: com.badlogic.gdx.maps.MapLayers, element: com.badlogic.gdx.utils.XmlReader.Element, tmxFile: com.badlogic.gdx.files.FileHandle, imageResolver: com.badlogic.gdx.maps.ImageResolver): scala.Unit = {
    val name: java.lang.String = element.getName()
    if (name.equals("group")) {
      this.loadLayerGroup(map, parentLayers, element, tmxFile, imageResolver)
    } else {
      if (name.equals("layer")) {
        this.loadTileLayer(map, parentLayers, element)
      } else {
        if (name.equals("objectgroup")) {
          this.loadObjectGroup(map, parentLayers, element, tmxFile)
        } else {
          if (name.equals("imagelayer")) {
            this.loadImageLayer(map, parentLayers, element, tmxFile, imageResolver)
          } else ()
        }
      }
    }
  }
  def loadLayerGroup(map: com.badlogic.gdx.maps.tiled.TiledMap, parentLayers: com.badlogic.gdx.maps.MapLayers, element: com.badlogic.gdx.utils.XmlReader.Element, tmxFile: com.badlogic.gdx.files.FileHandle, imageResolver: com.badlogic.gdx.maps.ImageResolver): scala.Unit = {
    if (element.getName().equals("group")) {
      val groupLayer: com.badlogic.gdx.maps.MapGroupLayer = new com.badlogic.gdx.maps.MapGroupLayer()
      this.loadBasicLayerInfo(groupLayer, element)
      val properties: com.badlogic.gdx.utils.XmlReader.Element = element.getChildByName("properties")
      if (properties != null) {
        this.loadProperties(groupLayer.getProperties(), properties)
      } else ();
      { var i: scala.Int = 0; val j: scala.Int = element.getChildCount(); while (i < j) { {
        val child: com.badlogic.gdx.utils.XmlReader.Element = element.getChild(i)
        this.loadLayer(map, groupLayer.getLayers(), child, tmxFile, imageResolver)
      }; i = i + 1 } }
      for (layer <- groupLayer.getLayers()) {
        layer.setParent(groupLayer)
      }
      parentLayers.add(groupLayer)
    } else ()
  }
  def loadTileLayer(map: com.badlogic.gdx.maps.tiled.TiledMap, parentLayers: com.badlogic.gdx.maps.MapLayers, element: com.badlogic.gdx.utils.XmlReader.Element): scala.Unit = {
    if (element.getName().equals("layer")) {
      val width: scala.Int = element.getIntAttribute("width", 0)
      val height: scala.Int = element.getIntAttribute("height", 0)
      val tileWidth: scala.Int = map.getProperties().get[java.lang.Integer]("tilewidth", classOf[java.lang.Integer])
      val tileHeight: scala.Int = map.getProperties().get[java.lang.Integer]("tileheight", classOf[java.lang.Integer])
      val layer: com.badlogic.gdx.maps.tiled.TiledMapTileLayer = new com.badlogic.gdx.maps.tiled.TiledMapTileLayer(width, height, tileWidth, tileHeight)
      this.loadBasicLayerInfo(layer, element)
      val ids: scala.Array[scala.Int] = BaseTmxMapLoader.getTileIds(element, width, height)
      val tilesets: com.badlogic.gdx.maps.tiled.TiledMapTileSets = map.getTileSets();
      { var y: scala.Int = 0; while (y < height) { {
        { var x: scala.Int = 0; while (x < width) { {
          val id: scala.Int = ids((y * width) + x)
          val flipHorizontally: scala.Boolean = (id & com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.FLAG_FLIP_HORIZONTALLY) != 0
          val flipVertically: scala.Boolean = (id & com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.FLAG_FLIP_VERTICALLY) != 0
          val flipDiagonally: scala.Boolean = (id & com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.FLAG_FLIP_DIAGONALLY) != 0
          val tile: com.badlogic.gdx.maps.tiled.TiledMapTile = tilesets.getTile(id & (~com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.MASK_CLEAR))
          if (tile != null) {
            val cell: com.badlogic.gdx.maps.tiled.TiledMapTileLayer.Cell = this.createTileLayerCell(flipHorizontally, flipVertically, flipDiagonally)
            cell.setTile(tile)
            layer.setCell(x, if (flipY) (height - 1) - y else y, cell)
          } else ()
        }; x = x + 1 } }
      }; y = y + 1 } }
      val properties: com.badlogic.gdx.utils.XmlReader.Element = element.getChildByName("properties")
      if (properties != null) {
        this.loadProperties(layer.getProperties(), properties)
      } else ()
      parentLayers.add(layer)
    } else ()
  }
  def loadObjectGroup(map: com.badlogic.gdx.maps.tiled.TiledMap, parentLayers: com.badlogic.gdx.maps.MapLayers, element: com.badlogic.gdx.utils.XmlReader.Element, tmxFile: com.badlogic.gdx.files.FileHandle): scala.Unit = {
    if (element.getName().equals("objectgroup")) {
      val layer: com.badlogic.gdx.maps.MapLayer = new com.badlogic.gdx.maps.MapLayer()
      this.loadBasicLayerInfo(layer, element)
      val properties: com.badlogic.gdx.utils.XmlReader.Element = element.getChildByName("properties")
      if (properties != null) {
        this.loadProperties(layer.getProperties(), properties)
      } else ()
      for (objectElement <- element.getChildrenByName("object")) {
        var elementToLoad: com.badlogic.gdx.utils.XmlReader.Element = objectElement
        if (objectElement.hasAttribute("template")) {
          elementToLoad = this.resolveTemplateObject(map, layer, objectElement, tmxFile)
        } else ()
        this.loadObject(map, layer, elementToLoad)
      }
      parentLayers.add(layer)
    } else ()
  }
  def loadImageLayer(map: com.badlogic.gdx.maps.tiled.TiledMap, parentLayers: com.badlogic.gdx.maps.MapLayers, element: com.badlogic.gdx.utils.XmlReader.Element, tmxFile: com.badlogic.gdx.files.FileHandle, imageResolver: com.badlogic.gdx.maps.ImageResolver): scala.Unit = {
    if (element.getName().equals("imagelayer")) {
      var x: scala.Float = 0
      var y: scala.Float = 0
      if (element.hasAttribute("offsetx")) {
        x = java.lang.Float.parseFloat(element.getAttribute("offsetx", "0"))
      } else {
        x = java.lang.Float.parseFloat(element.getAttribute("x", "0"))
      }
      if (element.hasAttribute("offsety")) {
        y = java.lang.Float.parseFloat(element.getAttribute("offsety", "0"))
      } else {
        y = java.lang.Float.parseFloat(element.getAttribute("y", "0"))
      }
      if (flipY) {
        y = mapHeightInPixels - y
      } else ()
      val repeatX: scala.Boolean = element.getIntAttribute("repeatx", 0) == 1
      val repeatY: scala.Boolean = element.getIntAttribute("repeaty", 0) == 1
      var texture: com.badlogic.gdx.graphics.g2d.TextureRegion = null
      val image: com.badlogic.gdx.utils.XmlReader.Element = element.getChildByName("image")
      if (image != null) {
        val source: java.lang.String = image.getAttribute("source")
        val handle: com.badlogic.gdx.files.FileHandle = com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.getRelativeFileHandle(tmxFile, source)
        texture = imageResolver.getImage(handle.path())
        y = y - texture.getRegionHeight()
      } else ()
      val layer: com.badlogic.gdx.maps.tiled.TiledMapImageLayer = new com.badlogic.gdx.maps.tiled.TiledMapImageLayer(texture, x, y, repeatX, repeatY)
      this.loadBasicLayerInfo(layer, element)
      val properties: com.badlogic.gdx.utils.XmlReader.Element = element.getChildByName("properties")
      if (properties != null) {
        this.loadProperties(layer.getProperties(), properties)
      } else ()
      parentLayers.add(layer)
    } else ()
  }
  def loadBasicLayerInfo(layer: com.badlogic.gdx.maps.MapLayer, element: com.badlogic.gdx.utils.XmlReader.Element): scala.Unit = {
    val name: java.lang.String = element.getAttribute("name", null)
    val opacity: scala.Float = java.lang.Float.parseFloat(element.getAttribute("opacity", "1.0"))
    val tintColor: java.lang.String = element.getAttribute("tintcolor", "#ffffffff")
    val visible: scala.Boolean = element.getIntAttribute("visible", 1) == 1
    val offsetX: scala.Float = element.getFloatAttribute("offsetx", 0)
    val offsetY: scala.Float = element.getFloatAttribute("offsety", 0)
    val parallaxX: scala.Float = element.getFloatAttribute("parallaxx", 1.0f)
    val parallaxY: scala.Float = element.getFloatAttribute("parallaxy", 1.0f)
    layer.setName(name)
    layer.setOpacity(opacity)
    layer.setVisible(visible)
    layer.setOffsetX(offsetX)
    layer.setOffsetY(offsetY)
    layer.setParallaxX(parallaxX)
    layer.setParallaxY(parallaxY)
    layer.setTintColor(com.badlogic.gdx.graphics.Color.valueOf(com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.tiledColorToLibGDXColor(tintColor)))
  }
  def loadObject(map: com.badlogic.gdx.maps.tiled.TiledMap, layer: com.badlogic.gdx.maps.MapLayer, element: com.badlogic.gdx.utils.XmlReader.Element): scala.Unit = {
    this.loadObject(map, layer.getObjects(), element, mapHeightInPixels)
  }
  def loadObject(map: com.badlogic.gdx.maps.tiled.TiledMap, tile: com.badlogic.gdx.maps.tiled.TiledMapTile, element: com.badlogic.gdx.utils.XmlReader.Element): scala.Unit = {
    this.loadObject(map, tile.getObjects(), element, tile.getTextureRegion().getRegionHeight())
  }
  def loadObject(map: com.badlogic.gdx.maps.tiled.TiledMap, objects: com.badlogic.gdx.maps.MapObjects, element: com.badlogic.gdx.utils.XmlReader.Element, heightInPixels: scala.Float): scala.Unit = {
    if (element.getName().equals("object")) {
      var `object`: com.badlogic.gdx.maps.MapObject = null
      val scaleX: scala.Float = if (convertObjectToTileSpace) 1.0f / mapTileWidth else 1.0f
      val scaleY: scala.Float = if (convertObjectToTileSpace) 1.0f / mapTileHeight else 1.0f
      val x: scala.Float = element.getFloatAttribute("x", 0) * scaleX
      val y: scala.Float = (if (flipY) heightInPixels - element.getFloatAttribute("y", 0) else element.getFloatAttribute("y", 0)) * scaleY
      val width: scala.Float = element.getFloatAttribute("width", 0) * scaleX
      val height: scala.Float = element.getFloatAttribute("height", 0) * scaleY
      if (element.getChildCount() > 0) {
        var child: com.badlogic.gdx.utils.XmlReader.Element = null
        if ({
          child = element.getChildByName("polygon")
          child
        } != null) {
          val points: scala.Array[java.lang.String] = child.getAttribute("points").split(" ")
          val vertices: scala.Array[scala.Float] = new scala.Array[scala.Float](points.length * 2);
          { var i: scala.Int = 0; while (i < points.length) { {
            val point: scala.Array[java.lang.String] = points(i).split(",")
            vertices(i * 2) = java.lang.Float.parseFloat(point(0)) * scaleX
            vertices((i * 2) + 1) = (java.lang.Float.parseFloat(point(1)) * scaleY) * (if (flipY) -1 else 1)
          }; i = i + 1 } }
          val polygon: com.badlogic.gdx.math.Polygon = new com.badlogic.gdx.math.Polygon(vertices)
          polygon.setPosition(x, y)
          `object` = new com.badlogic.gdx.maps.objects.PolygonMapObject(polygon)
        } else {
          if ({
            child = element.getChildByName("polyline")
            child
          } != null) {
            val points: scala.Array[java.lang.String] = child.getAttribute("points").split(" ")
            val vertices: scala.Array[scala.Float] = new scala.Array[scala.Float](points.length * 2);
            { var i: scala.Int = 0; while (i < points.length) { {
              val point: scala.Array[java.lang.String] = points(i).split(",")
              vertices(i * 2) = java.lang.Float.parseFloat(point(0)) * scaleX
              vertices((i * 2) + 1) = (java.lang.Float.parseFloat(point(1)) * scaleY) * (if (flipY) -1 else 1)
            }; i = i + 1 } }
            val polyline: com.badlogic.gdx.math.Polyline = new com.badlogic.gdx.math.Polyline(vertices)
            polyline.setPosition(x, y)
            `object` = new com.badlogic.gdx.maps.objects.PolylineMapObject(polyline)
          } else {
            if ({
              child = element.getChildByName("ellipse")
              child
            } != null) {
              `object` = new com.badlogic.gdx.maps.objects.EllipseMapObject(x, if (flipY) y - height else y, width, height)
            } else {
              if ({
                child = element.getChildByName("point")
                child
              } != null) {
                `object` = new com.badlogic.gdx.maps.objects.PointMapObject(x, if (flipY) y - height else y)
              } else {
                if ({
                  child = element.getChildByName("text")
                  child
                } != null) {
                  val textMapObject: com.badlogic.gdx.maps.objects.TextMapObject = new com.badlogic.gdx.maps.objects.TextMapObject(x, if (flipY) y - height else y, width, height, child.getText())
                  textMapObject.setFontFamily(child.getAttribute("fontfamily", ""))
                  textMapObject.setPixelSize(child.getIntAttribute("pixelSize", 16))
                  textMapObject.setHorizontalAlign(child.getAttribute("halign", "left"))
                  textMapObject.setVerticalAlign(child.getAttribute("valign", "top"))
                  textMapObject.setBold(child.getIntAttribute("bold", 0) == 1)
                  textMapObject.setItalic(child.getIntAttribute("italic", 0) == 1)
                  textMapObject.setUnderline(child.getIntAttribute("underline", 0) == 1)
                  textMapObject.setStrikeout(child.getIntAttribute("strikeout", 0) == 1)
                  textMapObject.setWrap(child.getIntAttribute("wrap", 0) == 1)
                  textMapObject.setKerning(child.getIntAttribute("kerning", 1) == 1)
                  val textColor: java.lang.String = child.getAttribute("color", "#000000")
                  textMapObject.setColor(com.badlogic.gdx.graphics.Color.valueOf(com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.tiledColorToLibGDXColor(textColor)))
                  `object` = textMapObject
                } else ()
              }
            }
          }
        }
      } else ()
      if (`object` == null) {
        var gid: java.lang.String = null
        if ({
          gid = element.getAttribute("gid", null)
          gid
        } != null) {
          val id: scala.Int = java.lang.Long.parseLong(gid).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
          val flipHorizontally: scala.Boolean = (id & com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.FLAG_FLIP_HORIZONTALLY) != 0
          val flipVertically: scala.Boolean = (id & com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.FLAG_FLIP_VERTICALLY) != 0
          val tile: com.badlogic.gdx.maps.tiled.TiledMapTile = map.getTileSets().getTile(id & (~com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.MASK_CLEAR))
          val tiledMapTileMapObject: com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject = new com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject(tile, flipHorizontally, flipVertically)
          val textureRegion: com.badlogic.gdx.graphics.g2d.TextureRegion = tiledMapTileMapObject.getTextureRegion()
          tiledMapTileMapObject.getProperties().put("gid", id.asInstanceOf[java.lang.Integer])
          tiledMapTileMapObject.setX(x)
          tiledMapTileMapObject.setY(if (flipY) y else y - height)
          val objectWidth: scala.Float = element.getFloatAttribute("width", textureRegion.getRegionWidth())
          val objectHeight: scala.Float = element.getFloatAttribute("height", textureRegion.getRegionHeight())
          tiledMapTileMapObject.setScaleX(scaleX * (objectWidth / textureRegion.getRegionWidth()))
          tiledMapTileMapObject.setScaleY(scaleY * (objectHeight / textureRegion.getRegionHeight()))
          tiledMapTileMapObject.setRotation(element.getFloatAttribute("rotation", 0))
          `object` = tiledMapTileMapObject
        } else {
          `object` = new com.badlogic.gdx.maps.objects.RectangleMapObject(x, if (flipY) y - height else y, width, height)
        }
      } else ()
      `object`.setName(element.getAttribute("name", null))
      val rotation: java.lang.String = element.getAttribute("rotation", null)
      if (rotation != null) {
        `object`.getProperties().put("rotation", java.lang.Float.parseFloat(rotation).asInstanceOf[java.lang.Float])
      } else ()
      val `type`: java.lang.String = element.getAttribute("type", null)
      if (`type` != null) {
        `object`.getProperties().put("type", `type`)
      } else ()
      val id: scala.Int = element.getIntAttribute("id", 0)
      if (id != 0) {
        `object`.getProperties().put("id", id.asInstanceOf[java.lang.Integer])
      } else ()
      `object`.getProperties().put("x", x.asInstanceOf[java.lang.Float])
      if (`object`.isInstanceOf[com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject]) {
        `object`.getProperties().put("y", y.asInstanceOf[java.lang.Float])
      } else {
        `object`.getProperties().put("y", (if (flipY) y - height else y).asInstanceOf[java.lang.Float])
      }
      `object`.getProperties().put("width", width.asInstanceOf[java.lang.Float])
      `object`.getProperties().put("height", height.asInstanceOf[java.lang.Float])
      `object`.setVisible(element.getIntAttribute("visible", 1) == 1)
      val properties: com.badlogic.gdx.utils.XmlReader.Element = element.getChildByName("properties")
      if (properties != null) {
        this.loadProperties(`object`.getProperties(), properties)
      } else ()
      this.loadMapPropertiesClassDefaults(`type`, `object`.getProperties())
      idToObject.put(id, `object`)
      objects.add(`object`)
    } else ()
  }
  def resolveTemplateObject(map: com.badlogic.gdx.maps.tiled.TiledMap, layer: com.badlogic.gdx.maps.MapLayer, mapElement: com.badlogic.gdx.utils.XmlReader.Element, tmxFile: com.badlogic.gdx.files.FileHandle): com.badlogic.gdx.utils.XmlReader.Element = {
    val txFileName: java.lang.String = mapElement.getAttribute("template")
    var templateElement: com.badlogic.gdx.utils.XmlReader.Element = this.templateCache.get(txFileName)
    if (templateElement == null) {
      val templateFile: com.badlogic.gdx.files.FileHandle = com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.getRelativeFileHandle(tmxFile, txFileName)
      try {
        templateElement = this.xml.parse(templateFile)
      } catch {
        case e: java.lang.Exception => {
          throw new com.badlogic.gdx.utils.GdxRuntimeException("Error parsing template file: " + txFileName, e)
        }
      }
      this.templateCache.put(txFileName, templateElement)
    } else ()
    val templateObjectElement: com.badlogic.gdx.utils.XmlReader.Element = templateElement.getChildByName("object")
    return this.mergeParentElementWithTemplate(mapElement, templateObjectElement)
  }
  def cloneElementShallow(sourceElement: com.badlogic.gdx.utils.XmlReader.Element): com.badlogic.gdx.utils.XmlReader.Element = {
    val copyElement: com.badlogic.gdx.utils.XmlReader.Element = new com.badlogic.gdx.utils.XmlReader.Element(sourceElement.getName(), null)
    val attrs: com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.String] = sourceElement.getAttributes()
    if (attrs != null) {
      for (entry <- attrs.entries()) {
        copyElement.setAttribute(entry.key, entry.value)
      }
    } else ()
    if (sourceElement.getText() != null) {
      copyElement.setText(sourceElement.getText())
    } else ()
    return copyElement
  }
  def mergeProperties(parentProps: com.badlogic.gdx.utils.XmlReader.Element, templateProps: com.badlogic.gdx.utils.XmlReader.Element): com.badlogic.gdx.utils.XmlReader.Element = {
    if (templateProps == null) {
      return parentProps
    } else ()
    if (parentProps == null) {
      return templateProps
    } else ()
    val merged: com.badlogic.gdx.utils.XmlReader.Element = new com.badlogic.gdx.utils.XmlReader.Element("properties", null)
    for (property <- templateProps.getChildrenByName("property")) {
      merged.addChild(this.cloneElementShallow(property))
    }
    for (property <- parentProps.getChildrenByName("property")) {
      val name: java.lang.String = property.getAttribute("name", null)
      var existing: com.badlogic.gdx.utils.XmlReader.Element = null;
      { var i: scala.Int = 0; while (i < merged.getChildCount()) { {
        val child: com.badlogic.gdx.utils.XmlReader.Element = merged.getChild(i)
        if ("property".equals(child.getName()) && name.equals(child.getAttribute("name", null))) {
          existing = child
          /* break */ ()
        } else ()
      }; i = i + 1 } }
      if (existing != null) {
        merged.removeChild(existing)
      } else ()
      merged.addChild(this.cloneElementShallow(property))
    }
    return merged
  }
  def mergeParentElementWithTemplate(parent: com.badlogic.gdx.utils.XmlReader.Element, template: com.badlogic.gdx.utils.XmlReader.Element): com.badlogic.gdx.utils.XmlReader.Element = {
    if (template == null) {
      return parent
    } else ()
    if (parent == null) {
      return template
    } else ()
    val merged: com.badlogic.gdx.utils.XmlReader.Element = new com.badlogic.gdx.utils.XmlReader.Element(template.getName(), null)
    if (template.getAttributes() != null) {
      for (a <- template.getAttributes().entries()) {
        merged.setAttribute(a.key, a.value)
      }
    } else ()
    if (parent.getAttributes() != null) {
      for (a <- parent.getAttributes().entries()) {
        merged.setAttribute(a.key, a.value)
      }
    } else ()
    val txt: java.lang.String = if ((parent.getText() != null) && (parent.getText().length() > 0)) parent.getText() else template.getText()
    if (txt != null) {
      merged.setText(txt)
    } else ()
    val tagNames: com.badlogic.gdx.utils.ObjectSet[java.lang.String] = new com.badlogic.gdx.utils.ObjectSet[java.lang.String]();
    { var i: scala.Int = 0; while (i < template.getChildCount()) { {
      tagNames.add(template.getChild(i).getName())
    }; i = i + 1 } };
    { var i: scala.Int = 0; while (i < parent.getChildCount()) { {
      tagNames.add(parent.getChild(i).getName())
    }; i = i + 1 } }
    for (tag <- tagNames) {
      val mapChild: com.badlogic.gdx.utils.XmlReader.Element = parent.getChildByName(tag)
      val tmplChild: com.badlogic.gdx.utils.XmlReader.Element = template.getChildByName(tag)
      val mergedChild: com.badlogic.gdx.utils.XmlReader.Element = if ("properties".equals(tag)) this.mergeProperties(mapChild, tmplChild) else this.mergeParentElementWithTemplate(mapChild, tmplChild)
      merged.addChild(mergedChild)
    }
    return merged
  }
  def loadProperties(properties: com.badlogic.gdx.maps.MapProperties, element: com.badlogic.gdx.utils.XmlReader.Element): scala.Unit = {
    if (element == null) {
      return
    } else ()
    if (element.getName().equals("properties")) {
      for (property <- element.getChildrenByName("property")) {
        val name: java.lang.String = property.getAttribute("name", null)
        val value: java.lang.String = BaseTmxMapLoader.getPropertyValue(property)
        val `type`: java.lang.String = property.getAttribute("type", null)
        if ("object".equals(`type`)) {
          this.loadObjectProperty(properties, name, value)
        } else {
          if ("class".equals(`type`)) {
            val classProperties: com.badlogic.gdx.maps.MapProperties = new com.badlogic.gdx.maps.MapProperties()
            val className: java.lang.String = property.getAttribute("propertytype")
            classProperties.put("type", className)
            properties.put(name, classProperties)
            this.loadClassProperties(className, classProperties, property.getChildByName("properties"))
          } else {
            this.loadBasicProperty(properties, name, value, `type`)
          }
        }
      }
    } else ()
  }
  def loadClassProperties(className: java.lang.String, classProperties: com.badlogic.gdx.maps.MapProperties, classElement: com.badlogic.gdx.utils.XmlReader.Element): scala.Unit = {
    if (projectClassInfo == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("No class information loaded to support class properties. Did you set the 'projectFilePath' parameter?")
    } else ()
    if (projectClassInfo.isEmpty()) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("No class information available. Did you set the correct Tiled project path in the 'projectFilePath' parameter?")
    } else ()
    val projectClassMembers: com.badlogic.gdx.utils.Array[com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.ProjectClassMember] = projectClassInfo.get(className)
    if (projectClassMembers == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException(("There is no class with name '" + className) + "' in given Tiled project file.")
    } else ()
    for (projectClassMember <- projectClassMembers) {
      val propName: java.lang.String = projectClassMember.name
      val classProp: com.badlogic.gdx.utils.XmlReader.Element = if (classElement == null) null.asInstanceOf[com.badlogic.gdx.utils.XmlReader.Element] else this.getPropertyByName(classElement, propName)
      projectClassMember.`type` match {
        case "object" => {
          val value: java.lang.String = if (classProp == null) projectClassMember.defaultValue.asString() else BaseTmxMapLoader.getPropertyValue(classProp)
          this.loadObjectProperty(classProperties, propName, value)
        }
        case "class" => {
          val nestedClassProperties: com.badlogic.gdx.maps.MapProperties = new com.badlogic.gdx.maps.MapProperties()
          val nestedClassName: java.lang.String = projectClassMember.propertyType
          nestedClassProperties.put("type", nestedClassName)
          classProperties.put(propName, nestedClassProperties)
          if (classProp == null) {
            this.loadJsonClassProperties(nestedClassName, nestedClassProperties, projectClassMember.defaultValue)
          } else {
            this.loadClassProperties(nestedClassName, nestedClassProperties, classProp)
          }
        }
        case _ => {
          val value: java.lang.String = if (classProp == null) projectClassMember.defaultValue.asString() else BaseTmxMapLoader.getPropertyValue(classProp)
          this.loadBasicProperty(classProperties, propName, value, projectClassMember.`type`)
        }
      }
    }
  }
  def getPropertyByName(classElement: com.badlogic.gdx.utils.XmlReader.Element, propName: java.lang.String): com.badlogic.gdx.utils.XmlReader.Element = {
    for (property <- classElement.getChildrenByNameRecursively("property")) {
      if (propName.equals(property.getAttribute("name"))) {
        return property
      } else ()
    }
    return null
  }
  def loadTileSet(element$arg: com.badlogic.gdx.utils.XmlReader.Element, tmxFile: com.badlogic.gdx.files.FileHandle, imageResolver: com.badlogic.gdx.maps.ImageResolver): scala.Unit = {
    var element: com.badlogic.gdx.utils.XmlReader.Element = element$arg
    if (element.getName().equals("tileset")) {
      val firstgid: scala.Int = element.getIntAttribute("firstgid", 1)
      var imageSource: java.lang.String = ""
      var imageWidth: scala.Int = 0
      var imageHeight: scala.Int = 0
      var image: com.badlogic.gdx.files.FileHandle = null
      val source: java.lang.String = element.getAttribute("source", null)
      if (source != null) {
        val tsx: com.badlogic.gdx.files.FileHandle = com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.getRelativeFileHandle(tmxFile, source)
        try {
          element = this.xml.parse(tsx)
          val imageElement: com.badlogic.gdx.utils.XmlReader.Element = element.getChildByName("image")
          if (imageElement != null) {
            imageSource = imageElement.getAttribute("source")
            imageWidth = imageElement.getIntAttribute("width", 0)
            imageHeight = imageElement.getIntAttribute("height", 0)
            image = com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.getRelativeFileHandle(tsx, imageSource)
          } else ()
        } catch {
          case e: com.badlogic.gdx.utils.SerializationException => {
            throw new com.badlogic.gdx.utils.GdxRuntimeException("Error parsing external tileset.")
          }
        }
      } else {
        val imageElement: com.badlogic.gdx.utils.XmlReader.Element = element.getChildByName("image")
        if (imageElement != null) {
          imageSource = imageElement.getAttribute("source")
          imageWidth = imageElement.getIntAttribute("width", 0)
          imageHeight = imageElement.getIntAttribute("height", 0)
          image = com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.getRelativeFileHandle(tmxFile, imageSource)
        } else ()
      }
      val name: java.lang.String = element.get("name", null)
      val tilewidth: scala.Int = element.getIntAttribute("tilewidth", 0)
      val tileheight: scala.Int = element.getIntAttribute("tileheight", 0)
      val spacing: scala.Int = element.getIntAttribute("spacing", 0)
      val margin: scala.Int = element.getIntAttribute("margin", 0)
      val offset: com.badlogic.gdx.utils.XmlReader.Element = element.getChildByName("tileoffset")
      var offsetX: scala.Int = 0
      var offsetY: scala.Int = 0
      if (offset != null) {
        offsetX = offset.getIntAttribute("x", 0)
        offsetY = offset.getIntAttribute("y", 0)
      } else ()
      val tileSet: com.badlogic.gdx.maps.tiled.TiledMapTileSet = new com.badlogic.gdx.maps.tiled.TiledMapTileSet()
      tileSet.setName(name)
      val tileSetProperties: com.badlogic.gdx.maps.MapProperties = tileSet.getProperties()
      val properties: com.badlogic.gdx.utils.XmlReader.Element = element.getChildByName("properties")
      if (properties != null) {
        this.loadProperties(tileSetProperties, properties)
      } else ()
      tileSetProperties.put("firstgid", firstgid.asInstanceOf[java.lang.Integer])
      val tileElements: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.XmlReader.Element] = element.getChildrenByName("tile")
      this.addStaticTiles(tmxFile, imageResolver, tileSet, element, tileElements, name, firstgid, tilewidth, tileheight, spacing, margin, source, offsetX, offsetY, imageSource, imageWidth, imageHeight, image)
      val animatedTiles: com.badlogic.gdx.utils.Array[com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile]()
      for (tileElement <- tileElements) {
        val localtid: scala.Int = tileElement.getIntAttribute("id", 0)
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
  def addStaticTiles(tmxFile: com.badlogic.gdx.files.FileHandle, imageResolver: com.badlogic.gdx.maps.ImageResolver, tileset: com.badlogic.gdx.maps.tiled.TiledMapTileSet, element: com.badlogic.gdx.utils.XmlReader.Element, tileElements: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.XmlReader.Element], name: java.lang.String, firstgid: scala.Int, tilewidth: scala.Int, tileheight: scala.Int, spacing: scala.Int, margin: scala.Int, source: java.lang.String, offsetX: scala.Int, offsetY: scala.Int, imageSource: java.lang.String, imageWidth: scala.Int, imageHeight: scala.Int, image: com.badlogic.gdx.files.FileHandle): scala.Unit
  def addTileProperties(tile: com.badlogic.gdx.maps.tiled.TiledMapTile, tileElement: com.badlogic.gdx.utils.XmlReader.Element): scala.Unit = {
    val terrain: java.lang.String = tileElement.getAttribute("terrain", null)
    val tileProperties: com.badlogic.gdx.maps.MapProperties = tile.getProperties()
    if (terrain != null) {
      tileProperties.put("terrain", terrain)
    } else ()
    val probability: java.lang.String = tileElement.getAttribute("probability", null)
    if (probability != null) {
      tileProperties.put("probability", probability)
    } else ()
    val `type`: java.lang.String = tileElement.getAttribute("type", null)
    if (`type` != null) {
      tileProperties.put("type", `type`)
    } else ()
    val properties: com.badlogic.gdx.utils.XmlReader.Element = tileElement.getChildByName("properties")
    if (properties != null) {
      this.loadProperties(tileProperties, properties)
    } else ()
    this.loadMapPropertiesClassDefaults(`type`, tileProperties)
  }
  def addTileObjectGroup(tile: com.badlogic.gdx.maps.tiled.TiledMapTile, tileElement: com.badlogic.gdx.utils.XmlReader.Element): scala.Unit = {
    val objectgroupElement: com.badlogic.gdx.utils.XmlReader.Element = tileElement.getChildByName("objectgroup")
    if (objectgroupElement != null) {
      for (objectElement <- objectgroupElement.getChildrenByName("object")) {
        this.loadObject(map, tile, objectElement)
      }
    } else ()
  }
  def createAnimatedTile(tileSet: com.badlogic.gdx.maps.tiled.TiledMapTileSet, tile: com.badlogic.gdx.maps.tiled.TiledMapTile, tileElement: com.badlogic.gdx.utils.XmlReader.Element, firstgid: scala.Int): com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile = {
    val animationElement: com.badlogic.gdx.utils.XmlReader.Element = tileElement.getChildByName("animation")
    if (animationElement != null) {
      val staticTiles: com.badlogic.gdx.utils.Array[com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile]()
      val intervals: com.badlogic.gdx.utils.IntArray = new com.badlogic.gdx.utils.IntArray()
      for (frameElement <- animationElement.getChildrenByName("frame")) {
        staticTiles.add(tileSet.getTile(firstgid + frameElement.getIntAttribute("tileid")).asInstanceOf[com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile])
        intervals.add(frameElement.getIntAttribute("duration"))
      }
      val animatedTile: com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile = new com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile(intervals, staticTiles)
      animatedTile.setId(tile.getId())
      return animatedTile
    } else ()
    return null
  }
}
object BaseTmxMapLoader {
  export com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.{getPropertyValue => _, getTileIds => _, *}
  private def getPropertyValue(classProp: com.badlogic.gdx.utils.XmlReader.Element): java.lang.String = {
    return classProp.getAttribute("value", classProp.getText())
  }
  def getTileIds(element: com.badlogic.gdx.utils.XmlReader.Element, width: scala.Int, height: scala.Int): scala.Array[scala.Int] = {
    val data: com.badlogic.gdx.utils.XmlReader.Element = element.getChildByName("data")
    val encoding: java.lang.String = data.getAttribute("encoding", null)
    if (encoding == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Unsupported encoding (XML) for TMX Layer Data")
    } else ()
    val ids: scala.Array[scala.Int] = new scala.Array[scala.Int](width * height)
    if (encoding.equals("csv")) {
      val array: scala.Array[java.lang.String] = data.getText().split(",");
      { var i: scala.Int = 0; while (i < array.length) { {
        ids(i) = java.lang.Long.parseLong(array(i).trim()).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
      }; i = i + 1 } }
    } else {
      if (true) {
        if (encoding.equals("base64")) {
          var is: java.io.InputStream = null
          try {
            val compression: java.lang.String = data.getAttribute("compression", null)
            val bytes: scala.Array[scala.Byte] = com.badlogic.gdx.utils.Base64Coder.decode(data.getText())
            if (compression == null) {
              is = new java.io.ByteArrayInputStream(bytes)
            } else {
              if (compression.equals("gzip")) {
                is = new java.io.BufferedInputStream(new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(bytes), bytes.length))
              } else {
                if (compression.equals("zlib")) {
                  is = new java.io.BufferedInputStream(new java.util.zip.InflaterInputStream(new java.io.ByteArrayInputStream(bytes)))
                } else {
                  throw new com.badlogic.gdx.utils.GdxRuntimeException(("Unrecognised compression (" + compression) + ") for TMX Layer Data")
                }
              }
            }
            val temp: scala.Array[scala.Byte] = new scala.Array[scala.Byte](4);
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
                  throw new com.badlogic.gdx.utils.GdxRuntimeException("Error Reading TMX Layer Data: Premature end of tile data")
                } else ()
                ids((y * width) + x) = ((com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.unsignedByteToInt(temp(0)) | (com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.unsignedByteToInt(temp(1)) << 8)) | (com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.unsignedByteToInt(temp(2)) << 16)) | (com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.unsignedByteToInt(temp(3)) << 24)
              }; x = x + 1 } }
            }; y = y + 1 } }
          } catch {
            case e: java.io.IOException => {
              throw new com.badlogic.gdx.utils.GdxRuntimeException("Error Reading TMX Layer Data - IOException: " + e.getMessage())
            }
          } finally {
            com.badlogic.gdx.utils.StreamUtils.closeQuietly(is)
          }
        } else {
          throw new com.badlogic.gdx.utils.GdxRuntimeException(("Unrecognised encoding (" + encoding) + ") for TMX Layer Data")
        }
      } else ()
    }
    return ids
  }
}