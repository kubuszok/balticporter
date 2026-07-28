package com.badlogic.gdx.maps.tiled

class TmjMapLoader extends com.badlogic.gdx.maps.tiled.BaseTmjMapLoader[com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters](new com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver()) {
  def this(resolver: com.badlogic.gdx.assets.loaders.FileHandleResolver) = {
    this()
    this.resolver = resolver
  }
  def load(fileName: java.lang.String): com.badlogic.gdx.maps.tiled.TiledMap = {
    return this.load(fileName, new com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters())
  }
  def load(fileName: java.lang.String, parameter: com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters): com.badlogic.gdx.maps.tiled.TiledMap = {
    val tmjFile: com.badlogic.gdx.files.FileHandle = this.resolve(fileName)
    this.root = json.parse(tmjFile)
    val textures: com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.graphics.Texture] = new com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.graphics.Texture]()
    val textureFiles: com.badlogic.gdx.utils.Array[com.badlogic.gdx.files.FileHandle] = this.getDependencyFileHandles(tmjFile)
    for (textureFile <- textureFiles) {
      val texture: com.badlogic.gdx.graphics.Texture = new com.badlogic.gdx.graphics.Texture(textureFile, parameter.generateMipMaps)
      texture.setFilter(parameter.textureMinFilter, parameter.textureMagFilter)
      textures.put(textureFile.path(), texture)
    }
    val map: com.badlogic.gdx.maps.tiled.TiledMap = this.loadTiledMap(tmjFile, parameter, new com.badlogic.gdx.maps.ImageResolver.DirectImageResolver(textures))
    map.setOwnedResources(textures.values().toArray())
    return map
  }
  @java.lang.Override
  override def loadAsync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, tmjFile: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters): scala.Unit = {
    this.map = this.loadTiledMap(tmjFile, parameter, new com.badlogic.gdx.maps.ImageResolver.AssetManagerImageResolver(manager))
  }
  @java.lang.Override
  override def loadSync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters): com.badlogic.gdx.maps.tiled.TiledMap = {
    return map
  }
  @java.lang.Override
  override def getDependencyAssetDescriptors(tmjFile: com.badlogic.gdx.files.FileHandle, textureParameter: com.badlogic.gdx.assets.loaders.TextureLoader.TextureParameter): com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[com.badlogic.gdx.maps.tiled.TiledMap]] = {
    val descriptors: com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]]
    val fileHandles: com.badlogic.gdx.utils.Array[com.badlogic.gdx.files.FileHandle] = this.getDependencyFileHandles(tmjFile)
    for (handle <- fileHandles) {
      descriptors.add(new com.badlogic.gdx.assets.AssetDescriptor(handle, classOf[com.badlogic.gdx.graphics.Texture], textureParameter))
    }
    return descriptors.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]]
  }
  def getDependencyFileHandles(tmjFile: com.badlogic.gdx.files.FileHandle): com.badlogic.gdx.utils.Array[com.badlogic.gdx.files.FileHandle] = {
    val fileHandles: com.badlogic.gdx.utils.Array[com.badlogic.gdx.files.FileHandle] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.files.FileHandle]()
    for (tileSet <- root.get("tilesets")) {
      this.getTileSetDependencyFileHandle(fileHandles, tmjFile, tileSet)
    }
    this.collectImageLayerFileHandles(root.get("layers"), tmjFile, fileHandles)
    return fileHandles
  }
  private def collectImageLayerFileHandles(layers: com.badlogic.gdx.utils.JsonValue, tmjFile: com.badlogic.gdx.files.FileHandle, fileHandles: com.badlogic.gdx.utils.Array[com.badlogic.gdx.files.FileHandle]): scala.Unit = {
    if (layers == null) {
      return
    } else ()
    for (layer <- layers) {
      val `type`: java.lang.String = layer.getString("type", "")
      if (`type`.equals("imagelayer")) {
        val source: java.lang.String = layer.getString("image", null)
        if (source != null) {
          val handle: com.badlogic.gdx.files.FileHandle = com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.getRelativeFileHandle(tmjFile, source)
          fileHandles.add(handle)
        } else ()
      } else {
        if (`type`.equals("group")) {
          this.collectImageLayerFileHandles(layer.get("layers"), tmjFile, fileHandles)
        } else ()
      }
    }
  }
  def getTileSetDependencyFileHandle(tmjFile: com.badlogic.gdx.files.FileHandle, tileSet: com.badlogic.gdx.utils.JsonValue): com.badlogic.gdx.utils.Array[com.badlogic.gdx.files.FileHandle] = {
    val fileHandles: com.badlogic.gdx.utils.Array[com.badlogic.gdx.files.FileHandle] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.files.FileHandle]()
    return this.getTileSetDependencyFileHandle(fileHandles, tmjFile, tileSet)
  }
  def getTileSetDependencyFileHandle(fileHandles: com.badlogic.gdx.utils.Array[com.badlogic.gdx.files.FileHandle], tmjFile: com.badlogic.gdx.files.FileHandle, tileSet$arg: com.badlogic.gdx.utils.JsonValue): com.badlogic.gdx.utils.Array[com.badlogic.gdx.files.FileHandle] = {
    var tileSet: com.badlogic.gdx.utils.JsonValue = tileSet$arg
    var tsjFile: com.badlogic.gdx.files.FileHandle = null.asInstanceOf[com.badlogic.gdx.files.FileHandle]
    val source: java.lang.String = tileSet.getString("source", null)
    if (source != null) {
      tsjFile = com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.getRelativeFileHandle(tmjFile, source)
      tileSet = json.parse(tsjFile)
    } else {
      tsjFile = tmjFile
    }
    if (tileSet.has("image")) {
      val imageSource: java.lang.String = tileSet.getString("image")
      val image: com.badlogic.gdx.files.FileHandle = com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.getRelativeFileHandle(tsjFile, imageSource)
      fileHandles.add(image)
    } else {
      val tiles: com.badlogic.gdx.utils.JsonValue = tileSet.get("tiles")
      if (tiles != null) {
        for (tile <- tiles) {
          val imageSource: java.lang.String = tile.getString("image")
          val image: com.badlogic.gdx.files.FileHandle = com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.getRelativeFileHandle(tsjFile, imageSource)
          fileHandles.add(image)
        }
      } else ()
    }
    return fileHandles
  }
  @java.lang.Override
  override def addStaticTiles(tmjFile: com.badlogic.gdx.files.FileHandle, imageResolver: com.badlogic.gdx.maps.ImageResolver, tileSet: com.badlogic.gdx.maps.tiled.TiledMapTileSet, element: com.badlogic.gdx.utils.JsonValue, tiles: com.badlogic.gdx.utils.JsonValue, name: java.lang.String, firstgid: scala.Int, tilewidth: scala.Int, tileheight: scala.Int, spacing: scala.Int, margin: scala.Int, source: java.lang.String, offsetX: scala.Int, offsetY: scala.Int, imageSource$arg: java.lang.String, imageWidth: scala.Int, imageHeight: scala.Int, image$arg: com.badlogic.gdx.files.FileHandle): scala.Unit = {
    var imageSource: java.lang.String = imageSource$arg
    var image: com.badlogic.gdx.files.FileHandle = image$arg
    val props: com.badlogic.gdx.maps.MapProperties = tileSet.getProperties()
    if (image != null) {
      val texture: com.badlogic.gdx.graphics.g2d.TextureRegion = imageResolver.getImage(image.path())
      props.put("imagesource", imageSource)
      props.put("imagewidth", imageWidth.asInstanceOf[java.lang.Integer])
      props.put("imageheight", imageHeight.asInstanceOf[java.lang.Integer])
      props.put("tilewidth", tilewidth.asInstanceOf[java.lang.Integer])
      props.put("tileheight", tileheight.asInstanceOf[java.lang.Integer])
      props.put("margin", margin.asInstanceOf[java.lang.Integer])
      props.put("spacing", spacing.asInstanceOf[java.lang.Integer])
      val stopWidth: scala.Int = texture.getRegionWidth() - tilewidth
      val stopHeight: scala.Int = texture.getRegionHeight() - tileheight
      var id: scala.Int = firstgid;
      { var y: scala.Int = margin; while (y <= stopHeight) { {
        { var x: scala.Int = margin; while (x <= stopWidth) { {
          val tileRegion: com.badlogic.gdx.graphics.g2d.TextureRegion = new com.badlogic.gdx.graphics.g2d.TextureRegion(texture, x, y, tilewidth, tileheight)
          val tileId: scala.Int = { id += 1; id }
          this.addStaticTiledMapTile(tileSet, tileRegion, tileId, offsetX, offsetY)
        }; x = x + (tilewidth + spacing) } }
      }; y = y + (tileheight + spacing) } }
    } else {
      for (tile <- tiles) {
        if (tile.has("image")) {
          imageSource = tile.getString("image")
          if (source != null) {
            image = com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.getRelativeFileHandle(com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.getRelativeFileHandle(tmjFile, source), imageSource)
          } else {
            image = com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.getRelativeFileHandle(tmjFile, imageSource)
          }
        } else ()
        val texture: com.badlogic.gdx.graphics.g2d.TextureRegion = imageResolver.getImage(image.path())
        val tileId: scala.Int = firstgid + tile.getInt("id")
        this.addStaticTiledMapTile(tileSet, texture, tileId, offsetX, offsetY)
      }
    }
  }
}
object TmjMapLoader {
  export com.badlogic.gdx.maps.tiled.BaseTmjMapLoader.*
}