package com.badlogic.gdx.maps.tiled

class TmxMapLoader extends com.badlogic.gdx.maps.tiled.BaseTmxMapLoader[com.badlogic.gdx.maps.tiled.BaseTiledMapLoader#Parameters] {
  def this(resolver: com.badlogic.gdx.assets.loaders.FileHandleResolver) = {
    this()
  }
  def load(fileName: java.lang.String): com.badlogic.gdx.maps.tiled.TiledMap = {
    return this.load(fileName, new com.badlogic.gdx.maps.tiled.BaseTiledMapLoader#Parameters())
  }
  def load(fileName: java.lang.String, parameter: com.badlogic.gdx.maps.tiled.BaseTiledMapLoader#Parameters): com.badlogic.gdx.maps.tiled.TiledMap = {
    val tmxFile: com.badlogic.gdx.files.FileHandle = this.resolve(fileName)
    this.root = xml.parse(tmxFile)
    val textures: com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.graphics.Texture] = new com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.graphics.Texture]()
    val textureFiles: com.badlogic.gdx.utils.Array[com.badlogic.gdx.files.FileHandle] = this.getDependencyFileHandles(tmxFile)
    for (textureFile <- textureFiles) {
      val texture: com.badlogic.gdx.graphics.Texture = new com.badlogic.gdx.graphics.Texture(textureFile, parameter.generateMipMaps)
      texture.setFilter(parameter.textureMinFilter, parameter.textureMagFilter)
      textures.put(textureFile.path(), texture)
    }
    val map: com.badlogic.gdx.maps.tiled.TiledMap = this.loadTiledMap(tmxFile, parameter, new com.badlogic.gdx.maps.ImageResolver#DirectImageResolver(textures))
    map.setOwnedResources(textures.values().toArray())
    return map
  }
  def loadAsync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, tmxFile: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.maps.tiled.BaseTiledMapLoader#Parameters): scala.Unit = {
    this.map = this.loadTiledMap(tmxFile, parameter, new com.badlogic.gdx.maps.ImageResolver#AssetManagerImageResolver(manager))
  }
  def loadSync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.maps.tiled.BaseTiledMapLoader#Parameters): com.badlogic.gdx.maps.tiled.TiledMap = {
    return map
  }
  protected def getDependencyAssetDescriptors(tmxFile: com.badlogic.gdx.files.FileHandle, textureParameter: com.badlogic.gdx.assets.loaders.TextureLoader#TextureParameter): com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor] = {
    val descriptors: com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor]()
    val fileHandles: com.badlogic.gdx.utils.Array[com.badlogic.gdx.files.FileHandle] = this.getDependencyFileHandles(tmxFile)
    for (handle <- fileHandles) {
      descriptors.add(new com.badlogic.gdx.assets.AssetDescriptor(handle, classOf[java.lang.Class], textureParameter))
    }
    return descriptors
  }
  protected def getDependencyFileHandles(tmxFile: com.badlogic.gdx.files.FileHandle): com.badlogic.gdx.utils.Array[com.badlogic.gdx.files.FileHandle] = {
    val fileHandles: com.badlogic.gdx.utils.Array[com.badlogic.gdx.files.FileHandle] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.files.FileHandle]()
    for (tileset <- root.getChildrenByNameRecursively("tileset")) {
      this.getTileSetDependencyFileHandle(fileHandles, tmxFile, tileset)
    }
    for (imageLayer <- root.getChildrenByNameRecursively("imagelayer")) {
      val image: com.badlogic.gdx.utils.XmlReader#Element = imageLayer.getChildByName("image")
      val source: java.lang.String = image.getAttribute("source", null)
      if (source != null) {
        val handle: com.badlogic.gdx.files.FileHandle = com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.getRelativeFileHandle(tmxFile, source)
        fileHandles.add(handle)
      } else ()
    }
    return fileHandles
  }
  protected def getTileSetDependencyFileHandle(tmxFile: com.badlogic.gdx.files.FileHandle, tileset: com.badlogic.gdx.utils.XmlReader#Element): com.badlogic.gdx.utils.Array[com.badlogic.gdx.files.FileHandle] = {
    val fileHandles: com.badlogic.gdx.utils.Array[com.badlogic.gdx.files.FileHandle] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.files.FileHandle]()
    return this.getTileSetDependencyFileHandle(fileHandles, tmxFile, tileset)
  }
  protected def getTileSetDependencyFileHandle(fileHandles: com.badlogic.gdx.utils.Array[com.badlogic.gdx.files.FileHandle], tmxFile: com.badlogic.gdx.files.FileHandle, tileset$arg: com.badlogic.gdx.utils.XmlReader#Element): com.badlogic.gdx.utils.Array[com.badlogic.gdx.files.FileHandle] = {
    var tileset: com.badlogic.gdx.utils.XmlReader#Element = tileset$arg
    var tsxFile: com.badlogic.gdx.files.FileHandle = null.asInstanceOf[com.badlogic.gdx.files.FileHandle]
    val source: java.lang.String = tileset.getAttribute("source", null)
    if (source != null) {
      tsxFile = com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.getRelativeFileHandle(tmxFile, source)
      tileset = xml.parse(tsxFile)
    } else {
      tsxFile = tmxFile
    }
    val imageElement: com.badlogic.gdx.utils.XmlReader#Element = tileset.getChildByName("image")
    if (imageElement != null) {
      val imageSource: java.lang.String = imageElement.getAttribute("source")
      val image: com.badlogic.gdx.files.FileHandle = com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.getRelativeFileHandle(tsxFile, imageSource)
      fileHandles.add(image)
    } else {
      for (tile <- tileset.getChildrenByName("tile")) {
        val imageSource: java.lang.String = tile.getChildByName("image").getAttribute("source")
        val image: com.badlogic.gdx.files.FileHandle = com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.getRelativeFileHandle(tsxFile, imageSource)
        fileHandles.add(image)
      }
    }
    return fileHandles
  }
  protected def addStaticTiles(tmxFile: com.badlogic.gdx.files.FileHandle, imageResolver: com.badlogic.gdx.maps.ImageResolver, tileSet: com.badlogic.gdx.maps.tiled.TiledMapTileSet, element: com.badlogic.gdx.utils.XmlReader#Element, tileElements: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.XmlReader#Element], name: java.lang.String, firstgid: scala.Int, tilewidth: scala.Int, tileheight: scala.Int, spacing: scala.Int, margin: scala.Int, source: java.lang.String, offsetX: scala.Int, offsetY: scala.Int, imageSource$arg: java.lang.String, imageWidth: scala.Int, imageHeight: scala.Int, image$arg: com.badlogic.gdx.files.FileHandle): scala.Unit = {
    var imageSource: java.lang.String = imageSource$arg
    var image: com.badlogic.gdx.files.FileHandle = image$arg
    val props: com.badlogic.gdx.maps.MapProperties = tileSet.getProperties()
    if (image != null) {
      val texture: com.badlogic.gdx.graphics.g2d.TextureRegion = imageResolver.getImage(image.path())
      props.put("imagesource", imageSource)
      props.put("imagewidth", imageWidth)
      props.put("imageheight", imageHeight)
      props.put("tilewidth", tilewidth)
      props.put("tileheight", tileheight)
      props.put("margin", margin)
      props.put("spacing", spacing)
      val stopWidth: scala.Int = texture.getRegionWidth() - tilewidth
      val stopHeight: scala.Int = texture.getRegionHeight() - tileheight
      var id: scala.Int = firstgid
      { var y: scala.Int = margin; while (y <= stopHeight) { {
        { var x: scala.Int = margin; while (x <= stopWidth) { {
          val tileRegion: com.badlogic.gdx.graphics.g2d.TextureRegion = new com.badlogic.gdx.graphics.g2d.TextureRegion(texture, x, y, tilewidth, tileheight)
          val tileId: scala.Int = { id += 1; id }
          this.addStaticTiledMapTile(tileSet, tileRegion, tileId, offsetX, offsetY)
        }; x = x + (tilewidth + spacing) } }
      }; y = y + (tileheight + spacing) } }
    } else {
      for (tileElement <- tileElements) {
        val imageElement: com.badlogic.gdx.utils.XmlReader#Element = tileElement.getChildByName("image")
        if (imageElement != null) {
          imageSource = imageElement.getAttribute("source")
          if (source != null) {
            image = com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.getRelativeFileHandle(com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.getRelativeFileHandle(tmxFile, source), imageSource)
          } else {
            image = com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.getRelativeFileHandle(tmxFile, imageSource)
          }
        } else ()
        val texture: com.badlogic.gdx.graphics.g2d.TextureRegion = imageResolver.getImage(image.path())
        val tileId: scala.Int = firstgid + tileElement.getIntAttribute("id")
        this.addStaticTiledMapTile(tileSet, texture, tileId, offsetX, offsetY)
      }
    }
  }
}