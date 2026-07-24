package com.badlogic.gdx.graphics.g3d.particles

class ResourceData[T] extends com.badlogic.gdx.utils.Json.Serializable {
  private var uniqueData: com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.graphics.g3d.particles.ResourceData.SaveData] = null.asInstanceOf[com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.graphics.g3d.particles.ResourceData.SaveData]]
  private var data: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ResourceData.SaveData] = null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ResourceData.SaveData]]
  var sharedAssets: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ResourceData.AssetData[?]] = null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ResourceData.AssetData[?]]]
  private var currentLoadIndex: scala.Int = 0
  var resource: T = null.asInstanceOf[T]
  def this(resource: T) = {
    this()
    this.resource = resource
  }
  this.uniqueData = new com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.graphics.g3d.particles.ResourceData.SaveData]()
  this.data = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ResourceData.SaveData](true, 3, (() => new scala.Array[com.badlogic.gdx.graphics.g3d.particles.ResourceData.SaveData]()))
  this.sharedAssets = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ResourceData.AssetData[?]]()
  this.currentLoadIndex = 0
  def getAssetData[K](filename: java.lang.String, `type`: java.lang.Class[K]): scala.Int = {
    var i: scala.Int = 0
    for (data <- this.sharedAssets) {
      if (data.filename.equals(filename) && data.`type`.equals(`type`)) {
        return i
      } else ()
      i = i + 1
    }
    return -1
  }
  def getAssetDescriptors(): com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]] = {
    val descriptors: com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]()
    for (data <- this.sharedAssets) {
      descriptors.add(new com.badlogic.gdx.assets.AssetDescriptor[T](data.filename, data.`type`))
    }
    return descriptors
  }
  def getAssets(): com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ResourceData.AssetData[?]] = {
    return this.sharedAssets
  }
  def createSaveData(): com.badlogic.gdx.graphics.g3d.particles.ResourceData.SaveData = {
    val saveData: com.badlogic.gdx.graphics.g3d.particles.ResourceData.SaveData = new com.badlogic.gdx.graphics.g3d.particles.ResourceData.SaveData(this)
    this.data.add(saveData)
    return saveData
  }
  def createSaveData(key: java.lang.String): com.badlogic.gdx.graphics.g3d.particles.ResourceData.SaveData = {
    val saveData: com.badlogic.gdx.graphics.g3d.particles.ResourceData.SaveData = new com.badlogic.gdx.graphics.g3d.particles.ResourceData.SaveData(this)
    if (this.uniqueData.containsKey(key)) {
      throw new java.lang.RuntimeException("Key already used, data must be unique, use a different key")
    } else ()
    this.uniqueData.put(key, saveData)
    return saveData
  }
  def getSaveData(): com.badlogic.gdx.graphics.g3d.particles.ResourceData.SaveData = {
    return this.data.get({ this.currentLoadIndex += 1; this.currentLoadIndex })
  }
  def getSaveData(key: java.lang.String): com.badlogic.gdx.graphics.g3d.particles.ResourceData.SaveData = {
    return this.uniqueData.get(key)
  }
  def write(json: com.badlogic.gdx.utils.Json): scala.Unit = {
    json.writeValue("unique", this.uniqueData, classOf[com.badlogic.gdx.utils.ObjectMap[?, ?]])
    json.writeValue("data", this.data, classOf[com.badlogic.gdx.utils.Array[?]], classOf[com.badlogic.gdx.graphics.g3d.particles.ResourceData.SaveData])
    json.writeValue("assets", this.sharedAssets.toArray((() => new scala.Array[com.badlogic.gdx.graphics.g3d.particles.ResourceData.AssetData[?]]())), classOf[scala.Array[com.badlogic.gdx.graphics.g3d.particles.ResourceData.AssetData[?]]])
    json.writeValue("resource", this.resource, null)
  }
  def read(json: com.badlogic.gdx.utils.Json, jsonData: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    this.uniqueData = json.readValue("unique", classOf[com.badlogic.gdx.utils.ObjectMap[?, ?]], jsonData)
    for (entry <- this.uniqueData.entries()) {
      entry.value.resources = this
    }
    this.data = json.readValue("data", classOf[com.badlogic.gdx.utils.Array[?]], classOf[com.badlogic.gdx.graphics.g3d.particles.ResourceData.SaveData], jsonData)
    for (saveData <- this.data) {
      saveData.resources = this
    }
    this.sharedAssets.addAll(json.readValue("assets", classOf[com.badlogic.gdx.utils.Array[?]], classOf[com.badlogic.gdx.graphics.g3d.particles.ResourceData.AssetData[?]], jsonData))
    this.resource = json.readValue("resource", null, jsonData)
  }
}
object ResourceData {
  trait Configurable[T] {
    def save(manager: com.badlogic.gdx.assets.AssetManager, resources: ResourceData[T]): scala.Unit
    def load(manager: com.badlogic.gdx.assets.AssetManager, resources: ResourceData[T]): scala.Unit
  }
  class SaveData extends com.badlogic.gdx.utils.Json.Serializable {
    var data: com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Object] = null.asInstanceOf[com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Object]]
    var assets: com.badlogic.gdx.utils.IntArray = null.asInstanceOf[com.badlogic.gdx.utils.IntArray]
    private var loadIndex: scala.Int = 0
    var resources: ResourceData[?] = null.asInstanceOf[ResourceData[?]]
    def this(resources: ResourceData[?]) = {
      this()
      this.data = new com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Object]()
      this.assets = new com.badlogic.gdx.utils.IntArray()
      this.loadIndex = 0
      this.resources = resources
    }
    this.data = new com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Object]()
    this.assets = new com.badlogic.gdx.utils.IntArray()
    this.loadIndex = 0
    def saveAsset[K](filename: java.lang.String, `type`: java.lang.Class[K]): scala.Unit = {
      var i: scala.Int = this.resources.getAssetData(filename, `type`)
      if (i == (-1)) {
        this.resources.sharedAssets.add(new com.badlogic.gdx.graphics.g3d.particles.ResourceData.AssetData(filename, `type`))
        i = this.resources.sharedAssets.size - 1
      } else ()
      this.assets.add(i)
    }
    def save(key: java.lang.String, value: java.lang.Object): scala.Unit = {
      this.data.put(key, value)
    }
    def loadAsset(): com.badlogic.gdx.assets.AssetDescriptor[?] = {
      if (this.loadIndex == this.assets.size) {
        return null
      } else ()
      val data: com.badlogic.gdx.graphics.g3d.particles.ResourceData.AssetData[?] = this.resources.sharedAssets.get(this.assets.get({ this.loadIndex += 1; this.loadIndex })).asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ResourceData.AssetData[?]]
      return new com.badlogic.gdx.assets.AssetDescriptor(data.filename, data.`type`)
    }
    def load[K](key: java.lang.String): K = {
      return this.data.get(key).asInstanceOf[K]
    }
    def write(json: com.badlogic.gdx.utils.Json): scala.Unit = {
      json.writeValue("data", this.data, classOf[com.badlogic.gdx.utils.ObjectMap[?, ?]])
      json.writeValue("indices", this.assets.toArray(), classOf[scala.Array[scala.Int]])
    }
    def read(json: com.badlogic.gdx.utils.Json, jsonData: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
      this.data = json.readValue("data", classOf[com.badlogic.gdx.utils.ObjectMap[?, ?]], jsonData)
      this.assets.addAll(json.readValue("indices", classOf[scala.Array[scala.Int]], jsonData))
    }
  }
  class AssetData[T] extends com.badlogic.gdx.utils.Json.Serializable {
    var filename: java.lang.String = null.asInstanceOf[java.lang.String]
    var `type`: java.lang.Class[T] = null.asInstanceOf[java.lang.Class[T]]
    def this(filename: java.lang.String, `type`: java.lang.Class[T]) = {
      this()
      this.filename = filename
      this.`type` = `type`
    }
    def write(json: com.badlogic.gdx.utils.Json): scala.Unit = {
      json.writeValue("filename", this.filename)
      json.writeValue("type", this.`type`.getName())
    }
    def read(json: com.badlogic.gdx.utils.Json, jsonData: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
      this.filename = json.readValue("filename", classOf[java.lang.String], jsonData)
      val className: java.lang.String = json.readValue("type", classOf[java.lang.String], jsonData)
      try {
        this.`type` = com.badlogic.gdx.utils.reflect.ClassReflection.forName(className).asInstanceOf[java.lang.Class[T]]
      } catch {
        case e: com.badlogic.gdx.utils.reflect.ReflectionException => {
          throw new com.badlogic.gdx.utils.GdxRuntimeException("Class not found: " + className, e)
        }
      }
    }
  }
}