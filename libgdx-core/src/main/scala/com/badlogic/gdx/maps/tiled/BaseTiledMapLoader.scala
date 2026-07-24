package com.badlogic.gdx.maps.tiled

abstract class BaseTiledMapLoader[P <: Parameters] extends com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[com.badlogic.gdx.maps.tiled.TiledMap, P] {
  protected var convertObjectToTileSpace: scala.Boolean = false
  protected var flipY: scala.Boolean = true
  protected var mapTileWidth: scala.Int = 0
  protected var mapTileHeight: scala.Int = 0
  protected var mapWidthInPixels: scala.Int = 0
  protected var mapHeightInPixels: scala.Int = 0
  protected var map: com.badlogic.gdx.maps.tiled.TiledMap = null.asInstanceOf[com.badlogic.gdx.maps.tiled.TiledMap]
  protected var idToObject: com.badlogic.gdx.utils.IntMap[com.badlogic.gdx.maps.MapObject] = null.asInstanceOf[com.badlogic.gdx.utils.IntMap[com.badlogic.gdx.maps.MapObject]]
  protected var runOnEndOfLoadTiled: com.badlogic.gdx.utils.Array[java.lang.Runnable] = null.asInstanceOf[com.badlogic.gdx.utils.Array[java.lang.Runnable]]
  protected var projectClassInfo: com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.utils.Array[ProjectClassMember]] = null.asInstanceOf[com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.utils.Array[ProjectClassMember]]]
  def this(resolver: com.badlogic.gdx.assets.loaders.FileHandleResolver) = {
    this()
  }
  protected def getDependencyAssetDescriptors(mapFile: com.badlogic.gdx.files.FileHandle, textureParameter: com.badlogic.gdx.assets.loaders.TextureLoader#TextureParameter): com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor]
  protected def loadTiledMap(mapFile: com.badlogic.gdx.files.FileHandle, parameter: P, imageResolver: com.badlogic.gdx.maps.ImageResolver): com.badlogic.gdx.maps.tiled.TiledMap
  def getIdToObject(): com.badlogic.gdx.utils.IntMap[com.badlogic.gdx.maps.MapObject] = {
    return this.idToObject
  }
  protected def castProperty(name: java.lang.String, value: java.lang.String, `type`: java.lang.String): java.lang.Object = {
    if (((`type` == null) || "string".equals(`type`)) || "file".equals(`type`)) {
      return value
    } else {
      if (`type`.equals("int")) {
        return java.lang.Integer.valueOf(value)
      } else {
        if (`type`.equals("float")) {
          return java.lang.Float.valueOf(value)
        } else {
          if (`type`.equals("bool")) {
            return java.lang.Boolean.valueOf(value)
          } else {
            if (`type`.equals("color")) {
              return com.badlogic.gdx.graphics.Color.valueOf(BaseTiledMapLoader.tiledColorToLibGDXColor(value))
            } else {
              throw new com.badlogic.gdx.utils.GdxRuntimeException(((("Wrong type given for property " + name) + ", given : ") + `type`) + ", supported : string, file, bool, int, float, color")
            }
          }
        }
      }
    }
  }
  protected def createTileLayerCell(flipHorizontally: scala.Boolean, flipVertically: scala.Boolean, flipDiagonally: scala.Boolean): com.badlogic.gdx.maps.tiled.TiledMapTileLayer#Cell = {
    val cell: com.badlogic.gdx.maps.tiled.TiledMapTileLayer#Cell = new com.badlogic.gdx.maps.tiled.TiledMapTileLayer#Cell()
    if (flipDiagonally) {
      if (flipHorizontally && flipVertically) {
        cell.setFlipHorizontally(true)
        cell.setRotation(com.badlogic.gdx.maps.tiled.TiledMapTileLayer.Cell.ROTATE_270)
      } else {
        if (flipHorizontally) {
          cell.setRotation(com.badlogic.gdx.maps.tiled.TiledMapTileLayer.Cell.ROTATE_270)
        } else {
          if (flipVertically) {
            cell.setRotation(com.badlogic.gdx.maps.tiled.TiledMapTileLayer.Cell.ROTATE_90)
          } else {
            cell.setFlipVertically(true)
            cell.setRotation(com.badlogic.gdx.maps.tiled.TiledMapTileLayer.Cell.ROTATE_270)
          }
        }
      }
    } else {
      cell.setFlipHorizontally(flipHorizontally)
      cell.setFlipVertically(flipVertically)
    }
    return cell
  }
  protected def addStaticTiledMapTile(tileSet: com.badlogic.gdx.maps.tiled.TiledMapTileSet, textureRegion: com.badlogic.gdx.graphics.g2d.TextureRegion, tileId: scala.Int, offsetX: scala.Float, offsetY: scala.Float): scala.Unit = {
    val tile: com.badlogic.gdx.maps.tiled.TiledMapTile = new com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile(textureRegion)
    tile.setId(tileId)
    tile.setOffsetX(offsetX)
    tile.setOffsetY(if (this.flipY) -offsetY else offsetY)
    tileSet.putTile(tileId, tile)
  }
  protected def loadObjectProperty(properties: com.badlogic.gdx.maps.MapProperties, name: java.lang.String, value: java.lang.String): scala.Unit = {
    try {
      val id: scala.Int = java.lang.Integer.parseInt(value)
      val fetch: java.lang.Runnable = new java.lang.Runnable()
      this.runOnEndOfLoadTiled.add(fetch)
    } catch {
      case exception: java.lang.Exception => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException(("Error parsing property [\" + name + \"] of type \"object\" with value: [" + value) + "]", exception)
      }
    }
  }
  protected def loadBasicProperty(properties: com.badlogic.gdx.maps.MapProperties, name: java.lang.String, value: java.lang.String, `type`: java.lang.String): scala.Unit = {
    val castValue: java.lang.Object = this.castProperty(name, value, `type`)
    properties.put(name, castValue)
  }
  protected def loadProjectFile(projectFilePath: java.lang.String): scala.Unit = {
    this.projectClassInfo = new com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.utils.Array[ProjectClassMember]]()
    if ((projectFilePath == null) || projectFilePath.trim().isEmpty()) {
      return
    } else ()
    val projectFile: com.badlogic.gdx.files.FileHandle = this.resolve(projectFilePath)
    val projectRoot: com.badlogic.gdx.utils.JsonValue = new com.badlogic.gdx.utils.JsonReader().parse(projectFile)
    val propertyTypes: com.badlogic.gdx.utils.JsonValue = projectRoot.get("propertyTypes")
    if (propertyTypes == null) {
      return
    } else ()
    for (propertyType <- propertyTypes) {
      if (!"class".equals(propertyType.getString("type"))) {
        /* continue */ ()
      } else ()
      val className: java.lang.String = propertyType.getString("name")
      val members: com.badlogic.gdx.utils.JsonValue = propertyType.get("members")
      if (members.isEmpty()) {
        /* continue */ ()
      } else ()
      val projectClassMembers: com.badlogic.gdx.utils.Array[ProjectClassMember] = new com.badlogic.gdx.utils.Array[ProjectClassMember]()
      this.projectClassInfo.put(className, projectClassMembers)
      for (member <- members) {
        val projectClassMember: ProjectClassMember = new ProjectClassMember()
        projectClassMember.name = member.getString("name")
        projectClassMember.`type` = member.getString("type")
        projectClassMember.propertyType = member.getString("propertyType", null)
        projectClassMember.defaultValue = member.get("value")
        projectClassMembers.add(projectClassMember)
      }
    }
  }
  protected def loadJsonClassProperties(className: java.lang.String, classProperties: com.badlogic.gdx.maps.MapProperties, classElement: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    if (this.projectClassInfo == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("No class information loaded to support class properties. Did you set the 'projectFilePath' parameter?")
    } else ()
    if (this.projectClassInfo.isEmpty()) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("No class information available. Did you set the correct Tiled project path in the 'projectFilePath' parameter?")
    } else ()
    val projectClassMembers: com.badlogic.gdx.utils.Array[ProjectClassMember] = this.projectClassInfo.get(className)
    if (projectClassMembers == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException(("There is no class with name '" + className) + "' in given Tiled project file.")
    } else ()
    for (projectClassMember <- projectClassMembers) {
      val propName: java.lang.String = projectClassMember.name
      var classProp: com.badlogic.gdx.utils.JsonValue = classElement.get(propName)
      projectClassMember.`type` match {
        case "object" => {
          val value: java.lang.String = if (classProp == null) projectClassMember.defaultValue.asString() else classProp.asString()
          this.loadObjectProperty(classProperties, propName, value)
        }
        case "class" => {
          if (classProp == null) {
            classProp = projectClassMember.defaultValue
          } else ()
          val nestedClassProperties: com.badlogic.gdx.maps.MapProperties = new com.badlogic.gdx.maps.MapProperties()
          val nestedClassName: java.lang.String = projectClassMember.propertyType
          nestedClassProperties.put("type", nestedClassName)
          classProperties.put(propName, nestedClassProperties)
          this.loadJsonClassProperties(nestedClassName, nestedClassProperties, classProp)
        }
        case _ => {
          val value: java.lang.String = if (classProp == null) projectClassMember.defaultValue.asString() else classProp.asString()
          this.loadBasicProperty(classProperties, propName, value, projectClassMember.`type`)
        }
      }
    }
  }
  protected def loadMapPropertiesClassDefaults(className: java.lang.String, mapProperties: com.badlogic.gdx.maps.MapProperties): scala.Unit = {
    if (this.projectClassInfo == null) {
      com.badlogic.gdx.Gdx.app.log("TiledMapLoader", "WARN: There is at least one property of type class or an object with a class defined. " + "Use the 'projectFilePath' parameter to correctly load the default values of a class.")
      this.projectClassInfo = new com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.utils.Array[ProjectClassMember]]()
      return
    } else ()
    if ((className == null) || (!this.projectClassInfo.containsKey(className))) {
      return
    } else ()
    val classMembers: com.badlogic.gdx.utils.Array[ProjectClassMember] = this.projectClassInfo.get(className)
    for (classMember <- classMembers) {
      val propName: java.lang.String = classMember.name
      if (mapProperties.containsKey(propName)) {
        /* continue */ ()
      } else ()
      if ("class".equals(classMember.`type`)) {
        val nestedClassProperties: com.badlogic.gdx.maps.MapProperties = new com.badlogic.gdx.maps.MapProperties()
        val nestedClassName: java.lang.String = classMember.propertyType
        nestedClassProperties.put("type", nestedClassName)
        mapProperties.put(propName, nestedClassProperties)
        this.loadJsonClassProperties(classMember.propertyType, nestedClassProperties, classMember.defaultValue)
        /* continue */ ()
      } else ()
      val value: java.lang.String = classMember.defaultValue.asString()
      if ("object".equals(classMember.`type`)) {
        this.loadObjectProperty(mapProperties, propName, value)
      } else {
        this.loadBasicProperty(mapProperties, propName, value, classMember.`type`)
      }
    }
  }
  class Parameters extends com.badlogic.gdx.assets.AssetLoaderParameters[com.badlogic.gdx.maps.tiled.TiledMap] {
    var generateMipMaps: scala.Boolean = false
    var textureMinFilter: com.badlogic.gdx.graphics.Texture#TextureFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest
    var textureMagFilter: com.badlogic.gdx.graphics.Texture#TextureFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest
    var convertObjectToTileSpace: scala.Boolean = false
    var flipY: scala.Boolean = true
    var projectFilePath: java.lang.String = null
    var forceTextureFilters: scala.Boolean = false
  }
  protected class ProjectClassMember {
    var name: java.lang.String = null.asInstanceOf[java.lang.String]
    var `type`: java.lang.String = null.asInstanceOf[java.lang.String]
    var propertyType: java.lang.String = null.asInstanceOf[java.lang.String]
    var defaultValue: com.badlogic.gdx.utils.JsonValue = null.asInstanceOf[com.badlogic.gdx.utils.JsonValue]
    def toString(): java.lang.String = {
      return ((((((((((("ProjectClassMember{" + "name='") + this.name) + "'") + ", type='") + this.`type`) + "'") + ", propertyType='") + this.propertyType) + "'") + ", defaultValue=") + this.defaultValue) + "}"
    }
  }
}
object BaseTiledMapLoader {
  protected final val FLAG_FLIP_HORIZONTALLY: scala.Int = -2147483648
  protected final val FLAG_FLIP_VERTICALLY: scala.Int = 1073741824
  protected final val FLAG_FLIP_DIAGONALLY: scala.Int = 536870912
  protected final val MASK_CLEAR: scala.Int = -536870912
  protected def unsignedByteToInt(b: scala.Byte): scala.Int = {
    return b & 255
  }
  protected def getRelativeFileHandle(file: com.badlogic.gdx.files.FileHandle, path: java.lang.String): com.badlogic.gdx.files.FileHandle = {
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
  def tiledColorToLibGDXColor(tiledColor: java.lang.String): java.lang.String = {
    val alpha: java.lang.String = if (tiledColor.length() == 9) tiledColor.substring(1, 3) else "ff"
    val color: java.lang.String = if (tiledColor.length() == 9) tiledColor.substring(3) else tiledColor.substring(1)
    return color + alpha
  }
}