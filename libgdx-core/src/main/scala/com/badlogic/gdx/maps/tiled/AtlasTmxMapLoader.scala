package com.badlogic.gdx.maps.tiled

class AtlasTmxMapLoader extends com.badlogic.gdx.maps.tiled.BaseTmxMapLoader[com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters](new com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver()) {
  var trackedTextures: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.Texture] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.Texture]()
  var atlasResolver: com.badlogic.gdx.maps.tiled.AtlasTmxMapLoader.AtlasResolver = null.asInstanceOf[com.badlogic.gdx.maps.tiled.AtlasTmxMapLoader.AtlasResolver]
  def this(resolver: com.badlogic.gdx.assets.loaders.FileHandleResolver) = {
    this()
    this.resolver = resolver
  }
  def load(fileName: java.lang.String): com.badlogic.gdx.maps.tiled.TiledMap = {
    return this.load(fileName, new com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters())
  }
  def load(fileName: java.lang.String, parameter: com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters): com.badlogic.gdx.maps.tiled.TiledMap = {
    val tmxFile: com.badlogic.gdx.files.FileHandle = this.resolve(fileName)
    this.root = xml.parse(tmxFile)
    val atlasFileHandle: com.badlogic.gdx.files.FileHandle = this.getAtlasFileHandle(tmxFile)
    val atlas: com.badlogic.gdx.graphics.g2d.TextureAtlas = new com.badlogic.gdx.graphics.g2d.TextureAtlas(atlasFileHandle)
    this.atlasResolver = new com.badlogic.gdx.maps.tiled.AtlasTmxMapLoader.AtlasResolver.DirectAtlasResolver(atlas)
    val map: com.badlogic.gdx.maps.tiled.TiledMap = this.loadTiledMap(tmxFile, parameter, this.atlasResolver)
    map.setOwnedResources(new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.TextureAtlas](scala.Array[com.badlogic.gdx.graphics.g2d.TextureAtlas](atlas)))
    this.setTextureFilters(parameter.textureMinFilter, parameter.textureMagFilter)
    return map
  }
  @java.lang.Override
  override def loadAsync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, tmxFile: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters): scala.Unit = {
    val atlasHandle: com.badlogic.gdx.files.FileHandle = this.getAtlasFileHandle(tmxFile)
    this.atlasResolver = new com.badlogic.gdx.maps.tiled.AtlasTmxMapLoader.AtlasResolver.AssetManagerAtlasResolver(manager, atlasHandle.path())
    this.map = this.loadTiledMap(tmxFile, parameter, this.atlasResolver)
  }
  @java.lang.Override
  override def loadSync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters): com.badlogic.gdx.maps.tiled.TiledMap = {
    if (parameter != null) {
      this.setTextureFilters(parameter.textureMinFilter, parameter.textureMagFilter)
    } else ()
    return map
  }
  @java.lang.Override
  override def getDependencyAssetDescriptors(tmxFile: com.badlogic.gdx.files.FileHandle, textureParameter: com.badlogic.gdx.assets.loaders.TextureLoader.TextureParameter): com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[com.badlogic.gdx.maps.tiled.TiledMap]] = {
    val descriptors: com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[com.badlogic.gdx.maps.tiled.TiledMap]] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[com.badlogic.gdx.maps.tiled.TiledMap]]().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[com.badlogic.gdx.maps.tiled.TiledMap]]]
    val atlasFileHandle: com.badlogic.gdx.files.FileHandle = this.getAtlasFileHandle(tmxFile)
    if (atlasFileHandle != null) {
      descriptors.add(new com.badlogic.gdx.assets.AssetDescriptor[com.badlogic.gdx.maps.tiled.TiledMap](atlasFileHandle, classOf[com.badlogic.gdx.graphics.g2d.TextureAtlas]))
    } else ()
    return descriptors.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[com.badlogic.gdx.maps.tiled.TiledMap]]]
  }
  @java.lang.Override
  override def addStaticTiles(tmxFile: com.badlogic.gdx.files.FileHandle, imageResolver: com.badlogic.gdx.maps.ImageResolver, tileSet: com.badlogic.gdx.maps.tiled.TiledMapTileSet, element: com.badlogic.gdx.utils.XmlReader.Element, tileElements: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.XmlReader.Element], name: java.lang.String, firstgid: scala.Int, tilewidth: scala.Int, tileheight: scala.Int, spacing: scala.Int, margin: scala.Int, source: java.lang.String, offsetX: scala.Int, offsetY: scala.Int, imageSource: java.lang.String, imageWidth: scala.Int, imageHeight: scala.Int, image: com.badlogic.gdx.files.FileHandle): scala.Unit = {
    val atlas: com.badlogic.gdx.graphics.g2d.TextureAtlas = this.atlasResolver.getAtlas()
    val regionsName: java.lang.String = name
    for (texture <- atlas.getTextures()) {
      this.trackedTextures.add(texture)
    }
    val props: com.badlogic.gdx.maps.MapProperties = tileSet.getProperties()
    props.put("imagesource", imageSource)
    props.put("imagewidth", imageWidth.asInstanceOf[java.lang.Integer])
    props.put("imageheight", imageHeight.asInstanceOf[java.lang.Integer])
    props.put("tilewidth", tilewidth.asInstanceOf[java.lang.Integer])
    props.put("tileheight", tileheight.asInstanceOf[java.lang.Integer])
    props.put("margin", margin.asInstanceOf[java.lang.Integer])
    props.put("spacing", spacing.asInstanceOf[java.lang.Integer])
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
    for (tileElement <- tileElements) {
      val tileId: scala.Int = firstgid + tileElement.getIntAttribute("id", 0)
      val tile: com.badlogic.gdx.maps.tiled.TiledMapTile = tileSet.getTile(tileId)
      if (tile == null) {
        val imageElement: com.badlogic.gdx.utils.XmlReader.Element = tileElement.getChildByName("image")
        if (imageElement != null) {
          var regionName: java.lang.String = imageElement.getAttribute("source")
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
  def getAtlasFileHandle(tmxFile: com.badlogic.gdx.files.FileHandle): com.badlogic.gdx.files.FileHandle = {
    val properties: com.badlogic.gdx.utils.XmlReader.Element = root.getChildByName("properties")
    var atlasFilePath: java.lang.String = null
    if (properties != null) {
      for (property <- properties.getChildrenByName("property")) {
        val name: java.lang.String = property.getAttribute("name")
        if (name.startsWith("atlas")) {
          atlasFilePath = property.getAttribute("value")
          /* break */ ()
        } else ()
      }
    } else ()
    if (atlasFilePath == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("The map is missing the 'atlas' property")
    } else {
      val fileHandle: com.badlogic.gdx.files.FileHandle = com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.getRelativeFileHandle(tmxFile, atlasFilePath)
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
object AtlasTmxMapLoader {
  export com.badlogic.gdx.maps.tiled.BaseTmxMapLoader.{AtlasResolver => _, parseRegionName => _, *}
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
    export com.badlogic.gdx.maps.ImageResolver.{AssetManagerAtlasResolver => _, DirectAtlasResolver => _, *}
    class DirectAtlasResolver(atlas$p: com.badlogic.gdx.graphics.g2d.TextureAtlas) extends com.badlogic.gdx.maps.tiled.AtlasTmxMapLoader.AtlasResolver {
      private var atlas: com.badlogic.gdx.graphics.g2d.TextureAtlas = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureAtlas]
      this.atlas = atlas$p
      @java.lang.Override
      override def getAtlas(): com.badlogic.gdx.graphics.g2d.TextureAtlas = {
        return this.atlas
      }
      @java.lang.Override
      override def getImage(name: java.lang.String): com.badlogic.gdx.graphics.g2d.TextureRegion = {
        val regionName: java.lang.String = AtlasTmxMapLoader.parseRegionName(name)
        return this.atlas.findRegion(regionName)
      }
    }
    object DirectAtlasResolver {
      export com.badlogic.gdx.maps.tiled.AtlasTmxMapLoader.AtlasResolver.*
    }
    class AssetManagerAtlasResolver(assetManager$p: com.badlogic.gdx.assets.AssetManager, atlasName$p: java.lang.String) extends com.badlogic.gdx.maps.tiled.AtlasTmxMapLoader.AtlasResolver {
      private var assetManager: com.badlogic.gdx.assets.AssetManager = null.asInstanceOf[com.badlogic.gdx.assets.AssetManager]
      private var atlasName: java.lang.String = null.asInstanceOf[java.lang.String]
      this.assetManager = assetManager$p
      this.atlasName = atlasName$p
      @java.lang.Override
      override def getAtlas(): com.badlogic.gdx.graphics.g2d.TextureAtlas = {
        return this.assetManager.get(this.atlasName, classOf[com.badlogic.gdx.graphics.g2d.TextureAtlas])
      }
      @java.lang.Override
      override def getImage(name: java.lang.String): com.badlogic.gdx.graphics.g2d.TextureRegion = {
        val regionName: java.lang.String = AtlasTmxMapLoader.parseRegionName(name)
        return this.getAtlas().findRegion(regionName)
      }
    }
    object AssetManagerAtlasResolver {
      export com.badlogic.gdx.maps.tiled.AtlasTmxMapLoader.AtlasResolver.*
    }
  }
}