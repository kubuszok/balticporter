package com.badlogic.gdx.maps.tiled

class TiledMapLoader(resolver$p: com.badlogic.gdx.assets.loaders.FileHandleResolver) extends com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[com.badlogic.gdx.maps.tiled.TiledMap, com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters](resolver$p) {
  private var tmxMapLoader: com.badlogic.gdx.maps.tiled.TmxMapLoader = null.asInstanceOf[com.badlogic.gdx.maps.tiled.TmxMapLoader]
  private var tmjMapLoader: com.badlogic.gdx.maps.tiled.TmjMapLoader = null.asInstanceOf[com.badlogic.gdx.maps.tiled.TmjMapLoader]
  private var atlasTmxMapLoader: com.badlogic.gdx.maps.tiled.AtlasTmxMapLoader = null.asInstanceOf[com.badlogic.gdx.maps.tiled.AtlasTmxMapLoader]
  private var xmlReader: com.badlogic.gdx.utils.XmlReader = null.asInstanceOf[com.badlogic.gdx.utils.XmlReader]
  private var atlasTmjMapLoader: com.badlogic.gdx.maps.tiled.AtlasTmjMapLoader = null.asInstanceOf[com.badlogic.gdx.maps.tiled.AtlasTmjMapLoader]
  private var jsonReader: com.badlogic.gdx.utils.JsonReader = null.asInstanceOf[com.badlogic.gdx.utils.JsonReader]
  def this() = {
    this(new com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver())
  }
  this.tmxMapLoader = new com.badlogic.gdx.maps.tiled.TmxMapLoader(resolver$p)
  this.tmjMapLoader = new com.badlogic.gdx.maps.tiled.TmjMapLoader(resolver$p)
  this.atlasTmxMapLoader = new com.badlogic.gdx.maps.tiled.AtlasTmxMapLoader(resolver$p)
  this.xmlReader = new com.badlogic.gdx.utils.XmlReader()
  this.atlasTmjMapLoader = new com.badlogic.gdx.maps.tiled.AtlasTmjMapLoader(resolver$p)
  this.jsonReader = new com.badlogic.gdx.utils.JsonReader()
  def load(fileName: java.lang.String): com.badlogic.gdx.maps.tiled.TiledMap = {
    return this.load(fileName, new com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters())
  }
  def load(fileName: java.lang.String, parameter$arg: com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters): com.badlogic.gdx.maps.tiled.TiledMap = {
    var parameter: com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters = parameter$arg
    if (parameter == null) {
      parameter = new com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters()
    } else ()
    val file: com.badlogic.gdx.files.FileHandle = this.resolve(fileName)
    val `extension`: java.lang.String = file.`extension`().toLowerCase()
    if (`extension`.equals("tmx")) {
      if (this.usesAtlas(file)) {
        return this.atlasTmxMapLoader.load(fileName, parameter)
      } else {
        return this.tmxMapLoader.load(fileName, parameter)
      }
    } else {
      if (`extension`.equals("tmj")) {
        if (this.usesAtlas(file)) {
          return this.atlasTmjMapLoader.load(fileName, parameter)
        } else {
          return this.tmjMapLoader.load(fileName, parameter)
        }
      } else {
        throw new com.badlogic.gdx.utils.GdxRuntimeException((("Unsupported map format: '" + `extension`) + "' in file: ") + fileName)
      }
    }
  }
  def getDependencies(fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter$arg: com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters): com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]] = {
    var parameter: com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters = parameter$arg
    if (parameter == null) {
      parameter = new com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters()
    } else ()
    val `extension`: java.lang.String = file.`extension`().toLowerCase()
    if (`extension`.equals("tmx")) {
      if (this.usesAtlas(file)) {
        return this.atlasTmxMapLoader.getDependencies(fileName, file, parameter).asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]]
      } else {
        return this.tmxMapLoader.getDependencies(fileName, file, parameter).asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]]
      }
    } else {
      if (`extension`.equals("tmj")) {
        if (this.usesAtlas(file)) {
          return this.atlasTmjMapLoader.getDependencies(fileName, file, parameter).asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]]
        } else {
          return this.tmjMapLoader.getDependencies(fileName, file, parameter).asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]]
        }
      } else {
        throw new java.lang.IllegalArgumentException("Unsupported map format: " + `extension`)
      }
    }
  }
  def loadAsync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter$arg: com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters): scala.Unit = {
    var parameter: com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters = parameter$arg
    if (parameter == null) {
      parameter = new com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters()
    } else ()
    val `extension`: java.lang.String = file.`extension`().toLowerCase()
    if (`extension`.equals("tmx")) {
      if (this.usesAtlas(file)) {
        this.atlasTmxMapLoader.loadAsync(manager, fileName, file, parameter)
      } else {
        this.tmxMapLoader.loadAsync(manager, fileName, file, parameter)
      }
    } else {
      if (`extension`.equals("tmj")) {
        if (this.usesAtlas(file)) {
          this.atlasTmjMapLoader.loadAsync(manager, fileName, file, parameter)
        } else {
          this.tmjMapLoader.loadAsync(manager, fileName, file, parameter)
        }
      } else {
        throw new java.lang.IllegalArgumentException("Unsupported map format: " + `extension`)
      }
    }
  }
  def loadSync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter$arg: com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters): com.badlogic.gdx.maps.tiled.TiledMap = {
    var parameter: com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters = parameter$arg
    var map: com.badlogic.gdx.maps.tiled.TiledMap = null.asInstanceOf[com.badlogic.gdx.maps.tiled.TiledMap]
    if (parameter == null) {
      parameter = new com.badlogic.gdx.maps.tiled.BaseTiledMapLoader.Parameters()
    } else ()
    val `extension`: java.lang.String = file.`extension`().toLowerCase()
    if (`extension`.equals("tmx")) {
      if (this.usesAtlas(file)) {
        map = this.atlasTmxMapLoader.loadSync(manager, fileName, file, parameter)
      } else {
        map = this.tmxMapLoader.loadSync(manager, fileName, file, parameter)
      }
    } else {
      if (`extension`.equals("tmj")) {
        if (this.usesAtlas(file)) {
          map = this.atlasTmjMapLoader.loadSync(manager, fileName, file, parameter)
        } else {
          map = this.tmjMapLoader.loadSync(manager, fileName, file, parameter)
        }
      } else {
        throw new java.lang.IllegalArgumentException("Unsupported map format: " + `extension`)
      }
    }
    return map
  }
  private def usesAtlas(file: com.badlogic.gdx.files.FileHandle): scala.Boolean = {
    val `extension`: java.lang.String = file.`extension`().toLowerCase()
    if (`extension`.equals("tmx")) {
      val root: com.badlogic.gdx.utils.XmlReader.Element = this.xmlReader.parse(file)
      val properties: com.badlogic.gdx.utils.XmlReader.Element = root.getChildByName("properties")
      if (properties != null) {
        for (property <- properties.getChildrenByName("property")) {
          val name: java.lang.String = property.getAttribute("name", "")
          if ("atlas".equals(name)) {
            return true
          } else ()
        }
      } else ()
    } else {
      if (`extension`.equals("tmj")) {
        val root: com.badlogic.gdx.utils.JsonValue = this.jsonReader.parse(file)
        val properties: com.badlogic.gdx.utils.JsonValue = root.get("properties")
        if (properties != null) {
          for (property <- properties) {
            val name: java.lang.String = property.getString("name", "")
            if ("atlas".equals(name)) {
              return true
            } else ()
          }
        } else ()
      } else ()
    }
    return false
  }
}