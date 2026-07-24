package com.badlogic.gdx.maps.tiled

class AtlasTmjMapLoader extends com.badlogic.gdx.maps.tiled.BaseTmjMapLoader[com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters](new com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver()) {
  var trackedTextures: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.Texture] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.Texture]()
  var atlasResolver: com.badlogic.gdx.maps.tiled.AtlasTmjMapLoader.AtlasResolver = null.asInstanceOf[com.badlogic.gdx.maps.tiled.AtlasTmjMapLoader.AtlasResolver]
  def this(resolver: com.badlogic.gdx.assets.loaders.FileHandleResolver) = {
    this()
  }
  def load(fileName: java.lang.String): com.badlogic.gdx.maps.tiled.TiledMap = {
    return this.load(fileName, new com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters())
  }
  def load(fileName: java.lang.String, parameter: com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters): com.badlogic.gdx.maps.tiled.TiledMap = {
    val tmjFile: com.badlogic.gdx.files.FileHandle = this.resolve(fileName)
    this.root = json.parse(tmjFile)
    val atlasFileHandle: com.badlogic.gdx.files.FileHandle = this.getAtlasFileHandle(tmjFile)
    val atlas: com.badlogic.gdx.graphics.g2d.TextureAtlas = new com.badlogic.gdx.graphics.g2d.TextureAtlas(atlasFileHandle)
    this.atlasResolver = new com.badlogic.gdx.maps.tiled.AtlasTmjMapLoader.AtlasResolver.DirectAtlasResolver(atlas)
    val map: com.badlogic.gdx.maps.tiled.TiledMap = this.loadTiledMap(tmjFile, parameter, this.atlasResolver)
    map.setOwnedResources(new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.TextureAtlas](scala.Array[com.badlogic.gdx.graphics.g2d.TextureAtlas](atlas).asInstanceOf[scala.Array[java.lang.Object]]))
    this.setTextureFilters(parameter.textureMinFilter, parameter.textureMagFilter)
    return map
  }
  def loadAsync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, tmjFile: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters): scala.Unit = {
    val atlasHandle: com.badlogic.gdx.files.FileHandle = this.getAtlasFileHandle(tmjFile)
    this.atlasResolver = new com.badlogic.gdx.maps.tiled.AtlasTmjMapLoader.AtlasResolver.AssetManagerAtlasResolver(manager, atlasHandle.path())
    this.map = this.loadTiledMap(tmjFile, parameter, this.atlasResolver)
  }
  def loadSync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters): com.badlogic.gdx.maps.tiled.TiledMap = {
    if (parameter != null) {
      this.setTextureFilters(parameter.textureMinFilter, parameter.textureMagFilter)
    } else ()
    return map
  }
  def getDependencyAssetDescriptors(tmxFile: com.badlogic.gdx.files.FileHandle, textureParameter: com.badlogic.gdx.assets.loaders.TextureLoader.TextureParameter): com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]] = {
    val descriptors: com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]()
    val atlasFileHandle: com.badlogic.gdx.files.FileHandle = this.getAtlasFileHandle(tmxFile)
    if (atlasFileHandle != null) {
      descriptors.add(new com.badlogic.gdx.assets.AssetDescriptor(atlasFileHandle, classOf[com.badlogic.gdx.graphics.g2d.TextureAtlas]))
    } else ()
    return descriptors
  }
  def addStaticTiles(tmjFile: com.badlogic.gdx.files.FileHandle, imageResolver: com.badlogic.gdx.maps.ImageResolver, tileSet: com.badlogic.gdx.maps.tiled.TiledMapTileSet, element: com.badlogic.gdx.utils.JsonValue, tiles: com.badlogic.gdx.utils.JsonValue, name: java.lang.String, firstgid: scala.Int, tilewidth: scala.Int, tileheight: scala.Int, spacing: scala.Int, margin: scala.Int, source: java.lang.String, offsetX: scala.Int, offsetY: scala.Int, imageSource: java.lang.String, imageWidth: scala.Int, imageHeight: scala.Int, image: com.badlogic.gdx.files.FileHandle): scala.Unit = {
    val atlas: com.badlogic.gdx.graphics.g2d.TextureAtlas = this.atlasResolver.getAtlas()
    val regionsName: java.lang.String = name
    for (texture <- atlas.getTextures()) {
      this.trackedTextures.add(texture)
    }
    val props: com.badlogic.gdx.maps.MapProperties = tileSet.getProperties()
    props.put("imagesource", imageSource)
    props.put("imagewidth", imageWidth.asInstanceOf[java.lang.Object])
    props.put("imageheight", imageHeight.asInstanceOf[java.lang.Object])
    props.put("tilewidth", tilewidth.asInstanceOf[java.lang.Object])
    props.put("tileheight", tileheight.asInstanceOf[java.lang.Object])
    props.put("margin", margin.asInstanceOf[java.lang.Object])
    props.put("spacing", spacing.asInstanceOf[java.lang.Object])
    if ((imageSource != null) && (imageSource.length() > 0)) {
      val lastgid: scala.Int = (firstgid + ((imageWidth / tilewidth) * (imageHeight / tileheight))) - 1
      for (region <- atlas.findRegions(regionsName)) {
        if (region != null) {
          val tileId: scala.Int = firstgid + region.index
          if ((tileId >= firstgid) && (tileId <= lastgid)) {
            this.addStaticTiledMapTile(tileSet, region, tileId, offsetX, offsetY)
          } else ()
        } else ()
      }
    } else ()
    for (tileElement <- tiles) {
      val tileId: scala.Int = firstgid + tileElement.getInt("id", 0)
      val tile: com.badlogic.gdx.maps.tiled.TiledMapTile = tileSet.getTile(tileId)
      if (tile == null) {
        val imageElement: com.badlogic.gdx.utils.JsonValue = tileElement.get("image")
        if (imageElement != null) {
          var regionName: java.lang.String = imageElement.asString()
          regionName = regionName.substring(0, regionName.lastIndexOf('.'))
          val region: com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion = atlas.findRegion(regionName)
          if (region == null) {
            throw new com.badlogic.gdx.utils.GdxRuntimeException("Tileset atlasRegion not found: " + regionName)
          } else ()
          this.addStaticTiledMapTile(tileSet, region, tileId, offsetX, offsetY)
        } else ()
      } else ()
    }
  }
  def getAtlasFileHandle(tmjFile: com.badlogic.gdx.files.FileHandle): com.badlogic.gdx.files.FileHandle = {
    val properties: com.badlogic.gdx.utils.JsonValue = root.get("properties")
    var atlasFilePath: java.lang.String = null
    if (properties != null) {
      for (property <- properties) {
        val name: java.lang.String = property.getString("name", "")
        if (name.startsWith("atlas")) {
          atlasFilePath = property.getString("value", "")
          /* break */ ()
        } else ()
      }
    } else ()
    if ((atlasFilePath == null) || atlasFilePath.isEmpty()) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("The map is missing the 'atlas' property")
    } else {
      val fileHandle: com.badlogic.gdx.files.FileHandle = com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.getRelativeFileHandle(tmjFile, atlasFilePath)
      if (!fileHandle.exists()) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException(("The 'atlas' file could not be found: '" + atlasFilePath) + "'")
      } else ()
      return fileHandle
    }
  }
  def setTextureFilters(min: com.badlogic.gdx.graphics.Texture.TextureFilter, mag: com.badlogic.gdx.graphics.Texture.TextureFilter): scala.Unit = {
    for (texture <- this.trackedTextures) {
      texture.setFilter(min, mag)
    }
    this.trackedTextures.clear()
  }
}
object AtlasTmjMapLoader {
  export com.badlogic.gdx.maps.tiled.BaseTmjMapLoader.*
  def parseRegionName(name: java.lang.String): java.lang.String = {
    if (name.contains("atlas_imagelayer")) {
      val lastSlash: scala.Int = name.lastIndexOf('/')
      return if (lastSlash >= 0) name.substring(lastSlash + 1) else name
    } else {
      return name
    }
  }
  trait AtlasResolver extends com.badlogic.gdx.maps.ImageResolver {
    def getAtlas(): com.badlogic.gdx.graphics.g2d.TextureAtlas
  }
  object AtlasResolver {
    export com.badlogic.gdx.maps.ImageResolver.*
    class DirectAtlasResolver extends com.badlogic.gdx.maps.tiled.AtlasTmjMapLoader.AtlasResolver {
      private var atlas: com.badlogic.gdx.graphics.g2d.TextureAtlas = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureAtlas]
      def this(atlas: com.badlogic.gdx.graphics.g2d.TextureAtlas) = {
        this()
        this.atlas = atlas
      }
      def getAtlas(): com.badlogic.gdx.graphics.g2d.TextureAtlas = {
        return this.atlas
      }
      def getImage(name: java.lang.String): com.badlogic.gdx.graphics.g2d.TextureRegion = {
        val regionName: java.lang.String = AtlasTmjMapLoader.parseRegionName(name)
        return this.atlas.findRegion(regionName)
      }
    }
    object DirectAtlasResolver {
      export com.badlogic.gdx.maps.tiled.AtlasTmjMapLoader.AtlasResolver.*
    }
    class AssetManagerAtlasResolver extends com.badlogic.gdx.maps.tiled.AtlasTmjMapLoader.AtlasResolver {
      private var assetManager: com.badlogic.gdx.assets.AssetManager = null.asInstanceOf[com.badlogic.gdx.assets.AssetManager]
      private var atlasName: java.lang.String = null.asInstanceOf[java.lang.String]
      def this(assetManager: com.badlogic.gdx.assets.AssetManager, atlasName: java.lang.String) = {
        this()
        this.assetManager = assetManager
        this.atlasName = atlasName
      }
      def getAtlas(): com.badlogic.gdx.graphics.g2d.TextureAtlas = {
        return this.assetManager.get(this.atlasName, classOf[com.badlogic.gdx.graphics.g2d.TextureAtlas])
      }
      def getImage(name: java.lang.String): com.badlogic.gdx.graphics.g2d.TextureRegion = {
        val regionName: java.lang.String = AtlasTmjMapLoader.parseRegionName(name)
        return this.getAtlas().findRegion(regionName)
      }
    }
    object AssetManagerAtlasResolver {
      export com.badlogic.gdx.maps.tiled.AtlasTmjMapLoader.AtlasResolver.*
    }
  }
}