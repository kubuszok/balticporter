package com.badlogic.gdx.graphics.g3d.particles

class ResourceData[T] extends com.badlogic.gdx.utils.Json#Serializable {
  private var uniqueData: com.badlogic.gdx.utils.ObjectMap[java.lang.String, SaveData] = null.asInstanceOf[com.badlogic.gdx.utils.ObjectMap[java.lang.String, SaveData]]
  private var data: com.badlogic.gdx.utils.Array[SaveData] = null.asInstanceOf[com.badlogic.gdx.utils.Array[SaveData]]
  var sharedAssets: com.badlogic.gdx.utils.Array[AssetData] = null.asInstanceOf[com.badlogic.gdx.utils.Array[AssetData]]
  private var currentLoadIndex: scala.Int = 0
  var resource: T = null.asInstanceOf[T]
  def this(resource: T) = {
    this()
    this.resource = resource
  }
  def this() = {
    this()
    this.uniqueData = new com.badlogic.gdx.utils.ObjectMap[java.lang.String, SaveData]()
    this.data = new com.badlogic.gdx.utils.Array[SaveData](true, 3, scala.Array[SaveData].<init>)
    this.sharedAssets = new com.badlogic.gdx.utils.Array[AssetData]()
    this.currentLoadIndex = 0
  }
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
  def getAssetDescriptors(): com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor] = {
    val descriptors: com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor]()
    for (data <- this.sharedAssets) {
      descriptors.add(new com.badlogic.gdx.assets.AssetDescriptor[T](data.filename, data.`type`))
    }
    return descriptors
  }
  def getAssets(): com.badlogic.gdx.utils.Array[AssetData] = {
    return this.sharedAssets
  }
  def createSaveData(): SaveData = {
    val saveData: SaveData = new SaveData(this)
    this.data.add(saveData)
    return saveData
  }
  def createSaveData(key: java.lang.String): SaveData = {
    val saveData: SaveData = new SaveData(this)
    if (this.uniqueData.containsKey(key)) {
      throw new java.lang.RuntimeException("Key already used, data must be unique, use a different key")
    } else ()
    this.uniqueData.put(key, saveData)
    return saveData
  }
  def getSaveData(): SaveData = {
    return this.data.get({ this.currentLoadIndex += 1; this.currentLoadIndex })
  }
  def getSaveData(key: java.lang.String): SaveData = {
    return this.uniqueData.get(key)
  }
  def write(json: com.badlogic.gdx.utils.Json): scala.Unit = {
    json.writeValue("unique", this.uniqueData, classOf[java.lang.Class])
    json.writeValue("data", this.data, classOf[java.lang.Class], classOf[java.lang.Class])
    json.writeValue("assets", this.sharedAssets.toArray(scala.Array[AssetData].<init>), classOf[java.lang.Class])
    json.writeValue("resource", this.resource, null)
  }
  def read(json: com.badlogic.gdx.utils.Json, jsonData: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    this.uniqueData = json.readValue("unique", classOf[java.lang.Class], jsonData)
    for (entry <- this.uniqueData.entries()) {
      entry.value.resources = this
    }
    this.data = json.readValue("data", classOf[java.lang.Class], classOf[java.lang.Class], jsonData)
    for (saveData <- this.data) {
      saveData.resources = this
    }
    this.sharedAssets.addAll(json.readValue("assets", classOf[java.lang.Class], classOf[java.lang.Class], jsonData))
    this.resource = json.readValue("resource", null, jsonData)
  }
  trait Configurable[T] {
    def save(manager: com.badlogic.gdx.assets.AssetManager, resources: ResourceData[T]): scala.Unit
    def load(manager: com.badlogic.gdx.assets.AssetManager, resources: ResourceData[T]): scala.Unit
  }
  class SaveData extends com.badlogic.gdx.utils.Json#Serializable {
    var data: com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Object] = null.asInstanceOf[com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Object]]
    var assets: com.badlogic.gdx.utils.IntArray = null.asInstanceOf[com.badlogic.gdx.utils.IntArray]
    private var loadIndex: scala.Int = 0
    protected var resources: ResourceData = null.asInstanceOf[ResourceData]
    def this(resources: ResourceData) = {
      this()
      this.data = new com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Object]()
      this.assets = new com.badlogic.gdx.utils.IntArray()
      this.loadIndex = 0
      this.resources = resources
    }
    def this() = {
      this()
      this.data = new com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Object]()
      this.assets = new com.badlogic.gdx.utils.IntArray()
      this.loadIndex = 0
    }
    def saveAsset[K](filename: java.lang.String, `type`: java.lang.Class[K]): scala.Unit = {
      var i: scala.Int = this.resources.getAssetData(filename, `type`)
      if (i == (-1)) {
        this.resources.sharedAssets.add(new AssetData(filename, `type`))
        i = this.resources.sharedAssets.size - 1
      } else ()
      this.assets.add(i)
    }
    def save(key: java.lang.String, value: java.lang.Object): scala.Unit = {
      this.data.put(key, value)
    }
    def loadAsset(): com.badlogic.gdx.assets.AssetDescriptor = {
      if (this.loadIndex == this.assets.size) {
        return null
      } else ()
      val data: AssetData = this.resources.sharedAssets.get(this.assets.get({ this.loadIndex += 1; this.loadIndex })).asInstanceOf[AssetData]
      return new com.badlogic.gdx.assets.AssetDescriptor(data.filename, data.`type`)
    }
    def load[K](key: java.lang.String): K = {
      return this.data.get(key).asInstanceOf[K]
    }
    def write(json: com.badlogic.gdx.utils.Json): scala.Unit = {
      json.writeValue("data", this.data, classOf[java.lang.Class])
      json.writeValue("indices", this.assets.toArray(), classOf[java.lang.Class])
    }
    def read(json: com.badlogic.gdx.utils.Json, jsonData: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
      this.data = json.readValue("data", classOf[java.lang.Class], jsonData)
      this.assets.addAll(json.readValue("indices", classOf[java.lang.Class], jsonData))
    }
  }
  class AssetData[T] extends com.badlogic.gdx.utils.Json#Serializable {
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
      this.filename = json.readValue("filename", classOf[java.lang.Class], jsonData)
      val className: java.lang.String = json.readValue("type", classOf[java.lang.Class], jsonData)
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