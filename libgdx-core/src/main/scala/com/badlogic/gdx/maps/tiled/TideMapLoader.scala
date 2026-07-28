package com.badlogic.gdx.maps.tiled

class TideMapLoader extends com.badlogic.gdx.assets.loaders.SynchronousAssetLoader[com.badlogic.gdx.maps.tiled.TiledMap, com.badlogic.gdx.maps.tiled.TideMapLoader.Parameters](new com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver()) {
  private var xml: com.badlogic.gdx.utils.XmlReader = new com.badlogic.gdx.utils.XmlReader()
  private var root: com.badlogic.gdx.utils.XmlReader.Element = null.asInstanceOf[com.badlogic.gdx.utils.XmlReader.Element]
  def this(resolver: com.badlogic.gdx.assets.loaders.FileHandleResolver) = {
    this()
    this.resolver = resolver
  }
  def load(fileName: java.lang.String): com.badlogic.gdx.maps.tiled.TiledMap = {
    try {
      val tideFile: com.badlogic.gdx.files.FileHandle = this.resolve(fileName)
      this.root = this.xml.parse(tideFile)
      val textures: com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.graphics.Texture] = new com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.graphics.Texture]()
      for (textureFile <- this.loadTileSheets(this.root, tideFile)) {
        textures.put(textureFile.path(), new com.badlogic.gdx.graphics.Texture(textureFile))
      }
      val imageResolver: com.badlogic.gdx.maps.ImageResolver.DirectImageResolver = new com.badlogic.gdx.maps.ImageResolver.DirectImageResolver(textures)
      val map: com.badlogic.gdx.maps.tiled.TiledMap = this.loadMap(this.root, tideFile, imageResolver)
      map.setOwnedResources(textures.values().toArray())
      return map
    } catch {
      case e: java.io.IOException => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException(("Couldn't load tilemap '" + fileName) + "'", e)
      }
    }
  }
  @java.lang.Override
  def load(assetManager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, tideFile: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.maps.tiled.TideMapLoader.Parameters): com.badlogic.gdx.maps.tiled.TiledMap = {
    try {
      return this.loadMap(this.root, tideFile, new com.badlogic.gdx.maps.ImageResolver.AssetManagerImageResolver(assetManager))
    } catch {
      case e: java.lang.Exception => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException(("Couldn't load tilemap '" + fileName) + "'", e)
      }
    }
  }
  @java.lang.Override
  def getDependencies(fileName: java.lang.String, tmxFile: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.maps.tiled.TideMapLoader.Parameters): com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]] = {
    val dependencies: com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]]
    try {
      this.root = this.xml.parse(tmxFile)
      for (image <- this.loadTileSheets(this.root, tmxFile)) {
        dependencies.add(new com.badlogic.gdx.assets.AssetDescriptor(image.path(), classOf[com.badlogic.gdx.graphics.Texture]))
      }
      return dependencies.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]]
    } catch {
      case e: java.io.IOException => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException(("Couldn't load tilemap '" + fileName) + "'", e)
      }
    }
  }
  private def loadMap(root: com.badlogic.gdx.utils.XmlReader.Element, tmxFile: com.badlogic.gdx.files.FileHandle, imageResolver: com.badlogic.gdx.maps.ImageResolver): com.badlogic.gdx.maps.tiled.TiledMap = {
    val map: com.badlogic.gdx.maps.tiled.TiledMap = new com.badlogic.gdx.maps.tiled.TiledMap()
    val properties: com.badlogic.gdx.utils.XmlReader.Element = root.getChildByName("Properties")
    if (properties != null) {
      this.loadProperties(map.getProperties(), properties)
    } else ()
    val tilesheets: com.badlogic.gdx.utils.XmlReader.Element = root.getChildByName("TileSheets")
    for (tilesheet <- tilesheets.getChildrenByName("TileSheet")) {
      this.loadTileSheet(map, tilesheet, tmxFile, imageResolver)
    }
    val layers: com.badlogic.gdx.utils.XmlReader.Element = root.getChildByName("Layers")
    for (layer <- layers.getChildrenByName("Layer")) {
      this.loadLayer(map, layer)
    }
    return map
  }
  private def loadTileSheets(root: com.badlogic.gdx.utils.XmlReader.Element, tideFile: com.badlogic.gdx.files.FileHandle): com.badlogic.gdx.utils.Array[com.badlogic.gdx.files.FileHandle] = {
    val images: com.badlogic.gdx.utils.Array[com.badlogic.gdx.files.FileHandle] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.files.FileHandle]()
    val tilesheets: com.badlogic.gdx.utils.XmlReader.Element = root.getChildByName("TileSheets")
    for (tileset <- tilesheets.getChildrenByName("TileSheet")) {
      val imageSource: com.badlogic.gdx.utils.XmlReader.Element = tileset.getChildByName("ImageSource")
      val image: com.badlogic.gdx.files.FileHandle = TideMapLoader.getRelativeFileHandle(tideFile, imageSource.getText())
      images.add(image)
    }
    return images
  }
  private def loadTileSheet(map: com.badlogic.gdx.maps.tiled.TiledMap, element: com.badlogic.gdx.utils.XmlReader.Element, tideFile: com.badlogic.gdx.files.FileHandle, imageResolver: com.badlogic.gdx.maps.ImageResolver): scala.Unit = {
    if (element.getName().equals("TileSheet")) {
      val id: java.lang.String = element.getAttribute("Id")
      val description: java.lang.String = element.getChildByName("Description").getText()
      val imageSource: java.lang.String = element.getChildByName("ImageSource").getText()
      val alignment: com.badlogic.gdx.utils.XmlReader.Element = element.getChildByName("Alignment")
      val sheetSize: java.lang.String = alignment.getAttribute("SheetSize")
      val tileSize: java.lang.String = alignment.getAttribute("TileSize")
      val margin: java.lang.String = alignment.getAttribute("Margin")
      val spacing: java.lang.String = alignment.getAttribute("Spacing")
      val sheetSizeParts: scala.Array[java.lang.String] = sheetSize.split(" x ")
      val sheetSizeX: scala.Int = java.lang.Integer.parseInt(sheetSizeParts(0))
      val sheetSizeY: scala.Int = java.lang.Integer.parseInt(sheetSizeParts(1))
      val tileSizeParts: scala.Array[java.lang.String] = tileSize.split(" x ")
      val tileSizeX: scala.Int = java.lang.Integer.parseInt(tileSizeParts(0))
      val tileSizeY: scala.Int = java.lang.Integer.parseInt(tileSizeParts(1))
      val marginParts: scala.Array[java.lang.String] = margin.split(" x ")
      val marginX: scala.Int = java.lang.Integer.parseInt(marginParts(0))
      val marginY: scala.Int = java.lang.Integer.parseInt(marginParts(1))
      val spacingParts: scala.Array[java.lang.String] = margin.split(" x ")
      val spacingX: scala.Int = java.lang.Integer.parseInt(spacingParts(0))
      val spacingY: scala.Int = java.lang.Integer.parseInt(spacingParts(1))
      val image: com.badlogic.gdx.files.FileHandle = TideMapLoader.getRelativeFileHandle(tideFile, imageSource)
      val texture: com.badlogic.gdx.graphics.g2d.TextureRegion = imageResolver.getImage(image.path())
      val tilesets: com.badlogic.gdx.maps.tiled.TiledMapTileSets = map.getTileSets()
      var firstgid: scala.Int = 1
      for (tileset <- tilesets) {
        firstgid = firstgid + tileset.size()
      }
      val tileset: com.badlogic.gdx.maps.tiled.TiledMapTileSet = new com.badlogic.gdx.maps.tiled.TiledMapTileSet()
      tileset.setName(id)
      tileset.getProperties().put("firstgid", firstgid.asInstanceOf[java.lang.Integer])
      var gid: scala.Int = firstgid
      val stopWidth: scala.Int = texture.getRegionWidth() - tileSizeX
      val stopHeight: scala.Int = texture.getRegionHeight() - tileSizeY;
      { var y: scala.Int = marginY; while (y <= stopHeight) { {
        { var x: scala.Int = marginX; while (x <= stopWidth) { {
          val tile: com.badlogic.gdx.maps.tiled.TiledMapTile = new com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile(new com.badlogic.gdx.graphics.g2d.TextureRegion(texture, x, y, tileSizeX, tileSizeY))
          tile.setId(gid)
          tileset.putTile({ gid += 1; gid }, tile)
        }; x = x + (tileSizeX + spacingX) } }
      }; y = y + (tileSizeY + spacingY) } }
      val properties: com.badlogic.gdx.utils.XmlReader.Element = element.getChildByName("Properties")
      if (properties != null) {
        this.loadProperties(tileset.getProperties(), properties)
      } else ()
      tilesets.addTileSet(tileset)
    } else ()
  }
  private def loadLayer(map: com.badlogic.gdx.maps.tiled.TiledMap, element: com.badlogic.gdx.utils.XmlReader.Element): scala.Unit = {
    if (element.getName().equals("Layer")) {
      val id: java.lang.String = element.getAttribute("Id")
      val visible: java.lang.String = element.getAttribute("Visible")
      val dimensions: com.badlogic.gdx.utils.XmlReader.Element = element.getChildByName("Dimensions")
      val layerSize: java.lang.String = dimensions.getAttribute("LayerSize")
      val tileSize: java.lang.String = dimensions.getAttribute("TileSize")
      val layerSizeParts: scala.Array[java.lang.String] = layerSize.split(" x ")
      val layerSizeX: scala.Int = java.lang.Integer.parseInt(layerSizeParts(0))
      val layerSizeY: scala.Int = java.lang.Integer.parseInt(layerSizeParts(1))
      val tileSizeParts: scala.Array[java.lang.String] = tileSize.split(" x ")
      val tileSizeX: scala.Int = java.lang.Integer.parseInt(tileSizeParts(0))
      val tileSizeY: scala.Int = java.lang.Integer.parseInt(tileSizeParts(1))
      val layer: com.badlogic.gdx.maps.tiled.TiledMapTileLayer = new com.badlogic.gdx.maps.tiled.TiledMapTileLayer(layerSizeX, layerSizeY, tileSizeX, tileSizeY)
      layer.setName(id)
      layer.setVisible(visible.equalsIgnoreCase("True"))
      val tileArray: com.badlogic.gdx.utils.XmlReader.Element = element.getChildByName("TileArray")
      val rows: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.XmlReader.Element] = tileArray.getChildrenByName("Row")
      val tilesets: com.badlogic.gdx.maps.tiled.TiledMapTileSets = map.getTileSets()
      var currentTileSet: com.badlogic.gdx.maps.tiled.TiledMapTileSet = null
      var firstgid: scala.Int = 0
      var x: scala.Int = 0
      var y: scala.Int = 0;
      { var row: scala.Int = 0; val rowCount: scala.Int = rows.size; while (row < rowCount) { {
        val currentRow: com.badlogic.gdx.utils.XmlReader.Element = rows.get(row)
        y = (rowCount - 1) - row
        x = 0;
        { var child: scala.Int = 0; val childCount: scala.Int = currentRow.getChildCount(); while (child < childCount) { {
          val currentChild: com.badlogic.gdx.utils.XmlReader.Element = currentRow.getChild(child)
          val name: java.lang.String = currentChild.getName()
          if (name.equals("TileSheet")) {
            currentTileSet = tilesets.getTileSet(currentChild.getAttribute("Ref"))
            firstgid = currentTileSet.getProperties().get[java.lang.Integer]("firstgid", classOf[java.lang.Integer])
          } else {
            if (name.equals("Null")) {
              x = x + currentChild.getIntAttribute("Count")
            } else {
              if (name.equals("Static")) {
                val cell: com.badlogic.gdx.maps.tiled.TiledMapTileLayer.Cell = new com.badlogic.gdx.maps.tiled.TiledMapTileLayer.Cell()
                cell.setTile(currentTileSet.getTile(firstgid + currentChild.getIntAttribute("Index")))
                layer.setCell({ x += 1; x }, y, cell)
              } else {
                if (name.equals("Animated")) {
                  val interval: scala.Int = currentChild.getInt("Interval")
                  val frames: com.badlogic.gdx.utils.XmlReader.Element = currentChild.getChildByName("Frames")
                  val frameTiles: com.badlogic.gdx.utils.Array[com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile]();
                  { var frameChild: scala.Int = 0; val frameChildCount: scala.Int = frames.getChildCount(); while (frameChild < frameChildCount) { {
                    val frame: com.badlogic.gdx.utils.XmlReader.Element = frames.getChild(frameChild)
                    val frameName: java.lang.String = frame.getName()
                    if (frameName.equals("TileSheet")) {
                      currentTileSet = tilesets.getTileSet(frame.getAttribute("Ref"))
                      firstgid = currentTileSet.getProperties().get[java.lang.Integer]("firstgid", classOf[java.lang.Integer])
                    } else {
                      if (frameName.equals("Static")) {
                        frameTiles.add(currentTileSet.getTile(firstgid + frame.getIntAttribute("Index")).asInstanceOf[com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile])
                      } else ()
                    }
                  }; frameChild = frameChild + 1 } }
                  val cell: com.badlogic.gdx.maps.tiled.TiledMapTileLayer.Cell = new com.badlogic.gdx.maps.tiled.TiledMapTileLayer.Cell()
                  cell.setTile(new com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile(interval / 1000.0f, frameTiles))
                  layer.setCell({ x += 1; x }, y, cell)
                } else ()
              }
            }
          }
        }; child = child + 1 } }
      }; row = row + 1 } }
      val properties: com.badlogic.gdx.utils.XmlReader.Element = element.getChildByName("Properties")
      if (properties != null) {
        this.loadProperties(layer.getProperties(), properties)
      } else ()
      map.getLayers().add(layer)
    } else ()
  }
  private def loadProperties(properties: com.badlogic.gdx.maps.MapProperties, element: com.badlogic.gdx.utils.XmlReader.Element): scala.Unit = {
    if (element.getName().equals("Properties")) {
      for (property <- element.getChildrenByName("Property")) {
        val key: java.lang.String = property.getAttribute("Key", null)
        val `type`: java.lang.String = property.getAttribute("Type", null)
        val value: java.lang.String = property.getText()
        if (`type`.equals("Int32")) {
          properties.put(key, java.lang.Integer.parseInt(value).asInstanceOf[java.lang.Integer])
        } else {
          if (`type`.equals("String")) {
            properties.put(key, value)
          } else {
            if (`type`.equals("Boolean")) {
              properties.put(key, value.equalsIgnoreCase("true").asInstanceOf[java.lang.Boolean])
            } else {
              properties.put(key, value)
            }
          }
        }
      }
    } else ()
  }
}
object TideMapLoader {
  private def getRelativeFileHandle(file: com.badlogic.gdx.files.FileHandle, path: java.lang.String): com.badlogic.gdx.files.FileHandle = {
    val tokenizer: java.util.StringTokenizer = new java.util.StringTokenizer(path, "\\/")
    var result: com.badlogic.gdx.files.FileHandle = file.parent()
    while (tokenizer.hasMoreElements()) {
      val token: java.lang.String = tokenizer.nextToken()
      if (token.equals("..")) {
        result = result.parent()
      } else {
        result = result.child(token)
      }
    }
    return result
  }
  class Parameters extends com.badlogic.gdx.assets.AssetLoaderParameters[com.badlogic.gdx.maps.tiled.TiledMap]
  object Parameters {
    export com.badlogic.gdx.assets.AssetLoaderParameters.*
  }
}