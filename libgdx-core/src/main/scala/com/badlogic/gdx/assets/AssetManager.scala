package com.badlogic.gdx.assets

class AssetManager(resolver$p: com.badlogic.gdx.assets.loaders.FileHandleResolver, defaultLoaders: scala.Boolean) extends com.badlogic.gdx.utils.Disposable {
  final val assets: com.badlogic.gdx.utils.ObjectMap[java.lang.Class[?], com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.assets.AssetManager.RefCountedContainer]] = new com.badlogic.gdx.utils.ObjectMap().asInstanceOf[com.badlogic.gdx.utils.ObjectMap[java.lang.Class[?], com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.assets.AssetManager.RefCountedContainer]]]
  final val assetTypes: com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Class[?]] = new com.badlogic.gdx.utils.ObjectMap().asInstanceOf[com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Class[?]]]
  final val assetDependencies: com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.utils.Array[java.lang.String]] = new com.badlogic.gdx.utils.ObjectMap().asInstanceOf[com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.utils.Array[java.lang.String]]]
  final val injected: com.badlogic.gdx.utils.ObjectSet[java.lang.String] = new com.badlogic.gdx.utils.ObjectSet().asInstanceOf[com.badlogic.gdx.utils.ObjectSet[java.lang.String]]
  final val loaders: com.badlogic.gdx.utils.ObjectMap[java.lang.Class[?], com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.assets.loaders.AssetLoader[?, ?]]] = new com.badlogic.gdx.utils.ObjectMap().asInstanceOf[com.badlogic.gdx.utils.ObjectMap[java.lang.Class[?], com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.assets.loaders.AssetLoader[?, ?]]]]
  final val loadQueue: com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]]
  var executor: com.badlogic.gdx.utils.async.AsyncExecutor = null.asInstanceOf[com.badlogic.gdx.utils.async.AsyncExecutor]
  final val tasks: com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetLoadingTask] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetLoadingTask]]
  var listener: com.badlogic.gdx.assets.AssetErrorListener = null.asInstanceOf[com.badlogic.gdx.assets.AssetErrorListener]
  var loaded: scala.Int = 0
  var toLoad: scala.Int = 0
  var peakTasks: scala.Int = 0
  var resolver: com.badlogic.gdx.assets.loaders.FileHandleResolver = null.asInstanceOf[com.badlogic.gdx.assets.loaders.FileHandleResolver]
  var log: com.badlogic.gdx.utils.Logger = new com.badlogic.gdx.utils.Logger("AssetManager", com.badlogic.gdx.Application.LOG_NONE)
  def this(resolver: com.badlogic.gdx.assets.loaders.FileHandleResolver) = {
    this(resolver, true)
  }
  def this() = {
    this(new com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver())
  }
  this.resolver = resolver$p
  if (defaultLoaders) {
    this.setLoader(classOf[com.badlogic.gdx.graphics.g2d.BitmapFont], new com.badlogic.gdx.assets.loaders.BitmapFontLoader(resolver$p))
    this.setLoader(classOf[com.badlogic.gdx.audio.Music], new com.badlogic.gdx.assets.loaders.MusicLoader(resolver$p))
    this.setLoader(classOf[com.badlogic.gdx.graphics.Pixmap], new com.badlogic.gdx.assets.loaders.PixmapLoader(resolver$p))
    this.setLoader(classOf[com.badlogic.gdx.audio.Sound], new com.badlogic.gdx.assets.loaders.SoundLoader(resolver$p))
    this.setLoader(classOf[com.badlogic.gdx.graphics.g2d.TextureAtlas], new com.badlogic.gdx.assets.loaders.TextureAtlasLoader(resolver$p))
    this.setLoader(classOf[com.badlogic.gdx.graphics.Texture], new com.badlogic.gdx.assets.loaders.TextureLoader(resolver$p))
    this.setLoader(classOf[com.badlogic.gdx.scenes.scene2d.ui.Skin], new com.badlogic.gdx.assets.loaders.SkinLoader(resolver$p))
    this.setLoader(classOf[com.badlogic.gdx.graphics.g2d.ParticleEffect], new com.badlogic.gdx.assets.loaders.ParticleEffectLoader(resolver$p))
    this.setLoader(classOf[com.badlogic.gdx.graphics.g3d.particles.ParticleEffect], new com.badlogic.gdx.graphics.g3d.particles.ParticleEffectLoader(resolver$p))
    this.setLoader(classOf[com.badlogic.gdx.graphics.g2d.PolygonRegion], new com.badlogic.gdx.graphics.g2d.PolygonRegionLoader(resolver$p))
    this.setLoader(classOf[com.badlogic.gdx.utils.I18NBundle], new com.badlogic.gdx.assets.loaders.I18NBundleLoader(resolver$p))
    this.setLoader(classOf[com.badlogic.gdx.graphics.g3d.Model], ".g3dj", new com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader(new com.badlogic.gdx.utils.JsonReader(), resolver$p))
    this.setLoader(classOf[com.badlogic.gdx.graphics.g3d.Model], ".g3db", new com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader(new com.badlogic.gdx.utils.UBJsonReader(), resolver$p))
    this.setLoader(classOf[com.badlogic.gdx.graphics.g3d.Model], ".obj", new com.badlogic.gdx.graphics.g3d.loader.ObjLoader(resolver$p))
    this.setLoader(classOf[com.badlogic.gdx.graphics.glutils.ShaderProgram], new com.badlogic.gdx.assets.loaders.ShaderProgramLoader(resolver$p))
    this.setLoader(classOf[com.badlogic.gdx.graphics.Cubemap], new com.badlogic.gdx.assets.loaders.CubemapLoader(resolver$p))
  } else ()
  this.executor = new com.badlogic.gdx.utils.async.AsyncExecutor(1, "AssetManager")
  def getFileHandleResolver(): com.badlogic.gdx.assets.loaders.FileHandleResolver = {
    return this.resolver
  }
  def get[T](fileName: java.lang.String): T = {
    return this.get(fileName, true).asInstanceOf[T]
  }
  def get[T](fileName: java.lang.String, `type`: java.lang.Class[T]): T = {
    return this.get(fileName, `type`, true).asInstanceOf[T]
  }
  @com.badlogic.gdx.utils.Null
  def get[T](fileName: java.lang.String, required: scala.Boolean): T = {
    val `type`: java.lang.Class[T] = this.assetTypes.get(fileName).asInstanceOf[java.lang.Class[T]]
    if (`type` != null) {
      val assetsByType: com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.assets.AssetManager.RefCountedContainer] = this.assets.get(`type`)
      if (assetsByType != null) {
        val assetContainer: com.badlogic.gdx.assets.AssetManager.RefCountedContainer = assetsByType.get(fileName)
        if (assetContainer != null) {
          return assetContainer.`object`.asInstanceOf[T].asInstanceOf[T]
        } else ()
      } else ()
    } else ()
    if (required) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Asset not loaded: " + fileName)
    } else ()
    return null.asInstanceOf[T]
  }
  @com.badlogic.gdx.utils.Null
  def get[T](fileName: java.lang.String, `type`: java.lang.Class[T], required: scala.Boolean): T = {
    val assetsByType: com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.assets.AssetManager.RefCountedContainer] = this.assets.get(`type`)
    if (assetsByType != null) {
      val assetContainer: com.badlogic.gdx.assets.AssetManager.RefCountedContainer = assetsByType.get(fileName)
      if (assetContainer != null) {
        return assetContainer.`object`.asInstanceOf[T].asInstanceOf[T]
      } else ()
    } else ()
    if (required) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Asset not loaded: " + fileName)
    } else ()
    return null.asInstanceOf[T]
  }
  def get[T](assetDescriptor: com.badlogic.gdx.assets.AssetDescriptor[T]): T = {
    return this.get(assetDescriptor.fileName, assetDescriptor.`type`, true).asInstanceOf[T]
  }
  def getAll[T](`type`: java.lang.Class[T], out: com.badlogic.gdx.utils.Array[T]): com.badlogic.gdx.utils.Array[T] = {
    val assetsByType: com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.assets.AssetManager.RefCountedContainer] = this.assets.get(`type`)
    if (assetsByType != null) {
      for (assetRef <- assetsByType.values()) {
        out.add(assetRef.`object`.asInstanceOf[T])
      }
    } else ()
    return out
  }
  def contains(fileName: java.lang.String): scala.Boolean = {
    if ((this.tasks.size > 0) && this.tasks.first().assetDesc.fileName.equals(fileName)) {
      return true
    } else ();
    { var i: scala.Int = 0; while (i < this.loadQueue.size) { {
      if (this.loadQueue.get(i).fileName.equals(fileName)) {
        return true
      } else ()
    }; i = i + 1 } }
    return this.isLoaded(fileName)
  }
  def contains(fileName: java.lang.String, `type`: java.lang.Class[?]): scala.Boolean = {
    if (this.tasks.size > 0) {
      val assetDesc: com.badlogic.gdx.assets.AssetDescriptor[?] = this.tasks.first().assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[?]]
      if ((assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].`type` == `type`) && assetDesc.fileName.equals(fileName)) {
        return true
      } else ()
    } else ();
    { var i: scala.Int = 0; while (i < this.loadQueue.size) { {
      val assetDesc: com.badlogic.gdx.assets.AssetDescriptor[?] = this.loadQueue.get(i).asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[?]]
      if ((assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].`type` == `type`) && assetDesc.fileName.equals(fileName)) {
        return true
      } else ()
    }; i = i + 1 } }
    return this.isLoaded(fileName, `type`.asInstanceOf[java.lang.Class[?]])
  }
  def unload(fileName: java.lang.String): scala.Unit = {
    if (this.tasks.size > 0) {
      val currentTask: com.badlogic.gdx.assets.AssetLoadingTask = this.tasks.first()
      if (currentTask.assetDesc.fileName.equals(fileName)) {
        this.log.info("Unload (from tasks): " + fileName)
        currentTask.cancel = true
        currentTask.unload()
        return
      } else ()
    } else ()
    val `type`: java.lang.Class[?] = this.assetTypes.get(fileName).asInstanceOf[java.lang.Class[?]]
    var foundIndex: scala.Int = -1;
    { var i: scala.Int = 0; while (i < this.loadQueue.size) { {
      if (this.loadQueue.get(i).fileName.equals(fileName)) {
        foundIndex = i
        /* break */ ()
      } else ()
    }; i = i + 1 } }
    if (foundIndex != (-1)) {
      this.toLoad = this.toLoad - 1
      val desc: com.badlogic.gdx.assets.AssetDescriptor[?] = this.loadQueue.removeIndex(foundIndex).asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[?]]
      this.log.info("Unload (from queue): " + fileName)
      if (((`type` != null) && (desc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].params != null)) && (desc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].params.loadedCallback != null)) {
        desc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].params.loadedCallback.finishedLoading(this, desc.fileName, desc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].`type`.asInstanceOf[java.lang.Class[?]])
      } else ()
      return
    } else ()
    if (`type` == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Asset not loaded: " + fileName)
    } else ()
    val assetRef: com.badlogic.gdx.assets.AssetManager.RefCountedContainer = this.assets.get(`type`).get(fileName)
    assetRef.refCount = assetRef.refCount - 1
    if (assetRef.refCount <= 0) {
      this.log.info("Unload (dispose): " + fileName)
      if (assetRef.`object`.isInstanceOf[com.badlogic.gdx.utils.Disposable]) {
        assetRef.`object`.asInstanceOf[com.badlogic.gdx.utils.Disposable].dispose()
      } else ()
      this.assetTypes.remove(fileName)
      this.assets.get(`type`).remove(fileName)
    } else {
      this.log.info("Unload (decrement): " + fileName)
    }
    val dependencies: com.badlogic.gdx.utils.Array[java.lang.String] = this.assetDependencies.get(fileName)
    if (dependencies != null) {
      for (dependency <- dependencies) {
        if (this.isLoaded(dependency)) {
          this.unload(dependency)
        } else ()
      }
    } else ()
    if (assetRef.refCount <= 0) {
      this.assetDependencies.remove(fileName)
    } else ()
  }
  def containsAsset[T](asset: T): scala.Boolean = {
    val assetsByType: com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.assets.AssetManager.RefCountedContainer] = this.assets.get(asset.getClass())
    if (assetsByType == null) {
      return false
    } else ()
    for (assetRef <- assetsByType.values()) {
      if ((assetRef.`object` == asset) || asset.equals(assetRef.`object`)) {
        return true
      } else ()
    }
    return false
  }
  def getAssetFileName[T](asset: T): java.lang.String = {
    for (assetType <- this.assets.keys()) {
      val assetsByType: com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.assets.AssetManager.RefCountedContainer] = this.assets.get(assetType)
      for (entry <- assetsByType) {
        val `object`: java.lang.Object = entry.value.`object`
        if ((`object` == asset) || asset.equals(`object`)) {
          return entry.key
        } else ()
      }
    }
    return null
  }
  def isLoaded(assetDesc: com.badlogic.gdx.assets.AssetDescriptor[?]): scala.Boolean = {
    return this.isLoaded(assetDesc.fileName)
  }
  def isLoaded(fileName: java.lang.String): scala.Boolean = {
    if (fileName == null) {
      return false
    } else ()
    return this.assetTypes.containsKey(fileName)
  }
  def isLoaded(fileName: java.lang.String, `type`: java.lang.Class[?]): scala.Boolean = {
    val assetsByType: com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.assets.AssetManager.RefCountedContainer] = this.assets.get(`type`)
    if (assetsByType == null) {
      return false
    } else ()
    return assetsByType.get(fileName) != null
  }
  def getLoader[T](`type`: java.lang.Class[T]): com.badlogic.gdx.assets.loaders.AssetLoader[T, ?] = {
    return this.getLoader(`type`, null).asInstanceOf[com.badlogic.gdx.assets.loaders.AssetLoader[T, ?]]
  }
  def getLoader[T](`type`: java.lang.Class[T], fileName: java.lang.String): com.badlogic.gdx.assets.loaders.AssetLoader[T, ?] = {
    val loaders: com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.assets.loaders.AssetLoader[T, ?]] = this.loaders.get(`type`).asInstanceOf[com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.assets.loaders.AssetLoader[T, ?]]]
    if ((loaders == null) || (loaders.size < 1)) {
      return null
    } else ()
    if (fileName == null) {
      return loaders.get("").asInstanceOf[com.badlogic.gdx.assets.loaders.AssetLoader[T, ?]]
    } else ()
    var result: com.badlogic.gdx.assets.loaders.AssetLoader[T, ?] = null
    var length: scala.Int = -1
    for (entry <- loaders.entries()) {
      if ((entry.key.length() > length) && fileName.endsWith(entry.key)) {
        result = entry.value.asInstanceOf[com.badlogic.gdx.assets.loaders.AssetLoader[T, ?]]
        length = entry.key.length()
      } else ()
    }
    return result.asInstanceOf[com.badlogic.gdx.assets.loaders.AssetLoader[T, ?]]
  }
  def load[T](fileName: java.lang.String, `type`: java.lang.Class[T]): scala.Unit = {
    this.load(fileName, `type`, null)
  }
  def load[T](fileName: java.lang.String, `type`: java.lang.Class[T], parameter: com.badlogic.gdx.assets.AssetLoaderParameters[T]): scala.Unit = {
    val loader: com.badlogic.gdx.assets.loaders.AssetLoader[T, ?] = this.getLoader(`type`, fileName).asInstanceOf[com.badlogic.gdx.assets.loaders.AssetLoader[T, ?]]
    if (loader == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("No loader for type: " + `type`.getSimpleName())
    } else ()
    if (this.loadQueue.size == 0) {
      this.loaded = 0
      this.toLoad = 0
      this.peakTasks = 0
    } else ();
    { var i: scala.Int = 0; while (i < this.loadQueue.size) { {
      val desc: com.badlogic.gdx.assets.AssetDescriptor[T] = this.loadQueue.get(i).asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[T]]
      if (desc.fileName.equals(fileName) && (!desc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].`type`.equals(`type`))) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException(((((("Asset with name '" + fileName) + "' already in preload queue, but has different type (expected: ") + `type`.getSimpleName()) + ", found: ") + desc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].`type`.asInstanceOf[java.lang.Class[T]].getSimpleName()) + ")")
      } else ()
    }; i = i + 1 } };
    { var i: scala.Int = 0; while (i < this.tasks.size) { {
      val desc: com.badlogic.gdx.assets.AssetDescriptor[T] = this.tasks.get(i).assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[T]]
      if (desc.fileName.equals(fileName) && (!desc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].`type`.equals(`type`))) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException(((((("Asset with name '" + fileName) + "' already in task list, but has different type (expected: ") + `type`.getSimpleName()) + ", found: ") + desc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].`type`.asInstanceOf[java.lang.Class[T]].getSimpleName()) + ")")
      } else ()
    }; i = i + 1 } }
    val otherType: java.lang.Class[T] = this.assetTypes.get(fileName).asInstanceOf[java.lang.Class[T]]
    if ((otherType != null) && (!otherType.equals(`type`))) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException(((((("Asset with name '" + fileName) + "' already loaded, but has different type (expected: ") + `type`.getSimpleName()) + ", found: ") + otherType.asInstanceOf[java.lang.Class[T]].getSimpleName()) + ")")
    } else ()
    this.toLoad = this.toLoad + 1
    val assetDesc: com.badlogic.gdx.assets.AssetDescriptor[T] = new com.badlogic.gdx.assets.AssetDescriptor[T](fileName, `type`, parameter).asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[T]]
    this.loadQueue.add(assetDesc)
    this.log.debug("Queued: " + assetDesc)
  }
  def load(desc: com.badlogic.gdx.assets.AssetDescriptor[?]): scala.Unit = {
    this.load(desc.fileName, desc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].`type`, desc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].params)
  }
  def update(): scala.Boolean = {
    try {
      if (this.tasks.size == 0) {
        while ((this.loadQueue.size != 0) && (this.tasks.size == 0)) {
          this.nextTask()
        }
        if (this.tasks.size == 0) {
          return true
        } else ()
      } else ()
      return (this.updateTask() && (this.loadQueue.size == 0)) && (this.tasks.size == 0)
    } catch {
      case t: java.lang.Throwable => {
        this.handleTaskError(t)
        return this.loadQueue.size == 0
      }
    }
  }
  def update(millis: scala.Int): scala.Boolean = {
    {
      if (com.badlogic.gdx.Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.WebGL) {
        return this.update()
      } else ()
      val endTime: scala.Long = com.badlogic.gdx.utils.TimeUtils.millis() + millis
      while (true) {
        val done: scala.Boolean = this.update()
        if (done || (com.badlogic.gdx.utils.TimeUtils.millis() > endTime)) {
          return done
        } else ()
        com.badlogic.gdx.utils.async.ThreadUtils.`yield`()
      }
    }
    throw new java.lang.RuntimeException("unreachable")
  }
  def isFinished(): scala.Boolean = {
    return (this.loadQueue.size == 0) && (this.tasks.size == 0)
  }
  def finishLoading(): scala.Unit = {
    this.log.debug("Waiting for loading to complete...")
    while (!this.update()) {
      com.badlogic.gdx.utils.async.ThreadUtils.`yield`()
    }
    this.log.debug("Loading complete.")
  }
  def finishLoadingAsset[T](assetDesc: com.badlogic.gdx.assets.AssetDescriptor[T]): T = {
    return this.finishLoadingAsset(assetDesc.fileName).asInstanceOf[T]
  }
  def finishLoadingAsset[T](fileName: java.lang.String): T = {
    {
      this.log.debug("Waiting for asset to be loaded: " + fileName)
      while (true) {
        this.synchronized {
          val `type`: java.lang.Class[T] = this.assetTypes.get(fileName).asInstanceOf[java.lang.Class[T]]
          if (`type` != null) {
            val assetsByType: com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.assets.AssetManager.RefCountedContainer] = this.assets.get(`type`)
            if (assetsByType != null) {
              val assetContainer: com.badlogic.gdx.assets.AssetManager.RefCountedContainer = assetsByType.get(fileName)
              if (assetContainer != null) {
                this.log.debug("Asset loaded: " + fileName)
                return assetContainer.`object`.asInstanceOf[T].asInstanceOf[T]
              } else ()
            } else ()
          } else ()
          this.update()
        }
        com.badlogic.gdx.utils.async.ThreadUtils.`yield`()
      }
    }
    throw new java.lang.RuntimeException("unreachable")
  }
  def injectDependencies(parentAssetFilename: java.lang.String, dependendAssetDescs: com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]): scala.Unit = {
    val injected: com.badlogic.gdx.utils.ObjectSet[java.lang.String] = this.injected
    for (desc <- dependendAssetDescs) {
      if (injected.contains(desc.fileName)) {
        /* continue */ ()
      } else ()
      injected.add(desc.fileName)
      this.injectDependency(parentAssetFilename, desc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[?]])
    }
    injected.clear(32)
  }
  private def injectDependency(parentAssetFilename: java.lang.String, dependendAssetDesc: com.badlogic.gdx.assets.AssetDescriptor[?]): scala.Unit = {
    var dependencies: com.badlogic.gdx.utils.Array[java.lang.String] = this.assetDependencies.get(parentAssetFilename)
    if (dependencies == null) {
      dependencies = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[java.lang.String]]
      this.assetDependencies.put(parentAssetFilename, dependencies)
    } else ()
    dependencies.add(dependendAssetDesc.fileName)
    if (this.isLoaded(dependendAssetDesc.fileName)) {
      this.log.debug("Dependency already loaded: " + dependendAssetDesc)
      val `type`: java.lang.Class[?] = this.assetTypes.get(dependendAssetDesc.fileName).asInstanceOf[java.lang.Class[?]]
      val assetRef: com.badlogic.gdx.assets.AssetManager.RefCountedContainer = this.assets.get(`type`).get(dependendAssetDesc.fileName)
      assetRef.refCount = assetRef.refCount + 1
      this.incrementRefCountedDependencies(dependendAssetDesc.fileName)
    } else {
      this.log.info("Loading dependency: " + dependendAssetDesc)
      this.addTask(dependendAssetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[?]])
    }
  }
  private def nextTask(): scala.Unit = {
    val assetDesc: com.badlogic.gdx.assets.AssetDescriptor[?] = this.loadQueue.removeIndex(0).asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[?]]
    if (this.isLoaded(assetDesc.fileName)) {
      this.log.debug("Already loaded: " + assetDesc)
      val `type`: java.lang.Class[?] = this.assetTypes.get(assetDesc.fileName).asInstanceOf[java.lang.Class[?]]
      val assetRef: com.badlogic.gdx.assets.AssetManager.RefCountedContainer = this.assets.get(`type`).get(assetDesc.fileName)
      assetRef.refCount = assetRef.refCount + 1
      this.incrementRefCountedDependencies(assetDesc.fileName)
      if ((assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].params != null) && (assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].params.loadedCallback != null)) {
        assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].params.loadedCallback.finishedLoading(this, assetDesc.fileName, assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].`type`.asInstanceOf[java.lang.Class[?]])
      } else ()
      this.loaded = this.loaded + 1
    } else {
      this.log.info("Loading: " + assetDesc)
      this.addTask(assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[?]])
    }
  }
  private def addTask(assetDesc: com.badlogic.gdx.assets.AssetDescriptor[?]): scala.Unit = {
    val loader: com.badlogic.gdx.assets.loaders.AssetLoader[?, ?] = this.getLoader(assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].`type`, assetDesc.fileName).asInstanceOf[com.badlogic.gdx.assets.loaders.AssetLoader[?, ?]]
    if (loader == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("No loader for type: " + assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].`type`.asInstanceOf[java.lang.Class[?]].getSimpleName())
    } else ()
    this.tasks.add(new com.badlogic.gdx.assets.AssetLoadingTask(this, assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[?]], loader.asInstanceOf[com.badlogic.gdx.assets.loaders.AssetLoader[?, ?]], this.executor))
    this.peakTasks = this.peakTasks + 1
  }
  def addAsset[T](fileName: java.lang.String, `type`: java.lang.Class[T], asset: T): scala.Unit = {
    this.assetTypes.put(fileName, `type`)
    var typeToAssets: com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.assets.AssetManager.RefCountedContainer] = this.assets.get(`type`)
    if (typeToAssets == null) {
      typeToAssets = new com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.assets.AssetManager.RefCountedContainer]()
      this.assets.put(`type`, typeToAssets)
    } else ()
    val assetRef: com.badlogic.gdx.assets.AssetManager.RefCountedContainer = new com.badlogic.gdx.assets.AssetManager.RefCountedContainer()
    assetRef.`object` = asset.asInstanceOf[java.lang.Object]
    typeToAssets.put(fileName, assetRef)
  }
  private def updateTask(): scala.Boolean = {
    val task: com.badlogic.gdx.assets.AssetLoadingTask = this.tasks.peek()
    var complete: scala.Boolean = true
    try {
      complete = task.cancel || task.update()
    } catch {
      case ex: java.lang.RuntimeException => {
        task.cancel = true
        this.taskFailed(task.assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[?]], ex)
      }
    }
    if (complete) {
      if (this.tasks.size == 1) {
        this.loaded = this.loaded + 1
        this.peakTasks = 0
      } else ()
      this.tasks.pop()
      if (task.cancel) {
        return true
      } else ()
      this.addAsset(task.assetDesc.fileName, task.assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].`type`, task.asset)
      if ((task.assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].params != null) && (task.assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].params.loadedCallback != null)) {
        task.assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].params.loadedCallback.finishedLoading(this, task.assetDesc.fileName, task.assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].`type`.asInstanceOf[java.lang.Class[?]])
      } else ()
      val endTime: scala.Long = com.badlogic.gdx.utils.TimeUtils.nanoTime()
      this.log.debug((("Loaded: " + ((endTime - task.startTime) / 1000000.0f)) + "ms ") + task.assetDesc)
      return true
    } else ()
    return false
  }
  def taskFailed(assetDesc: com.badlogic.gdx.assets.AssetDescriptor[?], ex: java.lang.RuntimeException): scala.Unit = {
    throw ex
  }
  private def incrementRefCountedDependencies(parent: java.lang.String): scala.Unit = {
    val dependencies: com.badlogic.gdx.utils.Array[java.lang.String] = this.assetDependencies.get(parent)
    if (dependencies == null) {
      return
    } else ()
    for (dependency <- dependencies) {
      val `type`: java.lang.Class[?] = this.assetTypes.get(dependency).asInstanceOf[java.lang.Class[?]]
      val assetRef: com.badlogic.gdx.assets.AssetManager.RefCountedContainer = this.assets.get(`type`).get(dependency)
      assetRef.refCount = assetRef.refCount + 1
      this.incrementRefCountedDependencies(dependency)
    }
  }
  private def handleTaskError(t: java.lang.Throwable): scala.Unit = {
    this.log.error("Error loading asset.", t)
    if (this.tasks.isEmpty()) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException(t)
    } else ()
    val task: com.badlogic.gdx.assets.AssetLoadingTask = this.tasks.pop()
    val assetDesc: com.badlogic.gdx.assets.AssetDescriptor[?] = task.assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[?]]
    if (task.dependenciesLoaded && (task.dependencies != null)) {
      for (desc <- task.dependencies) {
        this.unload(desc.fileName)
      }
    } else ()
    this.tasks.clear()
    if (this.listener != null) {
      this.listener.error(assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[?]], t)
    } else {
      throw new com.badlogic.gdx.utils.GdxRuntimeException(t)
    }
  }
  def setLoader[T, P <: com.badlogic.gdx.assets.AssetLoaderParameters[T]](`type`: java.lang.Class[T], loader: com.badlogic.gdx.assets.loaders.AssetLoader[T, P]): scala.Unit = {
    this.setLoader(`type`, null, loader)
  }
  def setLoader[T, P <: com.badlogic.gdx.assets.AssetLoaderParameters[T]](`type`: java.lang.Class[T], suffix: java.lang.String, loader: com.badlogic.gdx.assets.loaders.AssetLoader[T, P]): scala.Unit = {
    if (`type` == null) {
      throw new java.lang.IllegalArgumentException("type cannot be null.")
    } else ()
    if (loader == null) {
      throw new java.lang.IllegalArgumentException("loader cannot be null.")
    } else ()
    this.log.debug((("Loader set: " + `type`.getSimpleName()) + " -> ") + loader.getClass().getSimpleName())
    var loaders: com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.assets.loaders.AssetLoader[T, P]] = this.loaders.get(`type`).asInstanceOf[com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.assets.loaders.AssetLoader[T, P]]]
    if (loaders == null) {
      this.loaders.put(`type`, {
        loaders = new com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.assets.loaders.AssetLoader[T, P]]().asInstanceOf[com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.assets.loaders.AssetLoader[T, P]]]
        loaders
      })
    } else ()
    loaders.put(if (suffix == null) "" else suffix, loader)
  }
  def getLoadedAssets(): scala.Int = {
    return this.assetTypes.size
  }
  def getQueuedAssets(): scala.Int = {
    return this.loadQueue.size + this.tasks.size
  }
  def getProgress(): scala.Float = {
    if (this.toLoad == 0) {
      return 1
    } else ()
    var fractionalLoaded: scala.Float = this.loaded
    if (this.peakTasks > 0) {
      fractionalLoaded = fractionalLoaded + ((this.peakTasks - this.tasks.size) / this.peakTasks.asInstanceOf[scala.Float])
    } else ()
    return java.lang.Math.min(1, fractionalLoaded / this.toLoad)
  }
  def setErrorListener(listener: com.badlogic.gdx.assets.AssetErrorListener): scala.Unit = {
    this.listener = listener
  }
  @java.lang.Override
  def dispose(): scala.Unit = {
    this.log.debug("Disposing.")
    this.clear()
    this.executor.dispose()
  }
  def clear(): scala.Unit = {
    this.synchronized {
      this.loadQueue.clear()
    }
    this.finishLoading()
    this.synchronized {
      val dependencyCount: com.badlogic.gdx.utils.ObjectIntMap[java.lang.String] = new com.badlogic.gdx.utils.ObjectIntMap[java.lang.String]()
      while (this.assetTypes.size > 0) {
        dependencyCount.clear(51)
        val assets: com.badlogic.gdx.utils.Array[java.lang.String] = this.assetTypes.keys().toArray()
        for (asset <- assets) {
          val dependencies: com.badlogic.gdx.utils.Array[java.lang.String] = this.assetDependencies.get(asset)
          if (dependencies == null) {
            /* continue */ ()
          } else ()
          for (dependency <- dependencies) {
            dependencyCount.getAndIncrement(dependency, 0, 1)
          }
        }
        for (asset <- assets) {
          if (dependencyCount.get(asset, 0) == 0) {
            this.unload(asset)
          } else ()
        }
      }
      this.assets.clear(51)
      this.assetTypes.clear(51)
      this.assetDependencies.clear(51)
      this.loaded = 0
      this.toLoad = 0
      this.peakTasks = 0
      this.loadQueue.clear()
      this.tasks.clear()
    }
  }
  def getLogger(): com.badlogic.gdx.utils.Logger = {
    return this.log
  }
  def setLogger(logger: com.badlogic.gdx.utils.Logger): scala.Unit = {
    this.log = logger
  }
  def getReferenceCount(fileName: java.lang.String): scala.Int = {
    val `type`: java.lang.Class[?] = this.assetTypes.get(fileName).asInstanceOf[java.lang.Class[?]]
    if (`type` == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Asset not loaded: " + fileName)
    } else ()
    return this.assets.get(`type`).get(fileName).refCount
  }
  def setReferenceCount(fileName: java.lang.String, refCount: scala.Int): scala.Unit = {
    val `type`: java.lang.Class[?] = this.assetTypes.get(fileName).asInstanceOf[java.lang.Class[?]]
    if (`type` == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Asset not loaded: " + fileName)
    } else ()
    this.assets.get(`type`).get(fileName).refCount = refCount
  }
  def getDiagnostics(): java.lang.String = {
    val buffer: java.lang.StringBuilder = new java.lang.StringBuilder(256)
    for (entry <- this.assetTypes) {
      val fileName: java.lang.String = entry.key
      val `type`: java.lang.Class[?] = entry.value.asInstanceOf[java.lang.Class[?]]
      if (buffer.length() > 0) {
        buffer.append('\n')
      } else ()
      buffer.append(fileName)
      buffer.append(", ")
      buffer.append(`type`.asInstanceOf[java.lang.Class[?]].getSimpleName())
      buffer.append(", refs: ")
      buffer.append(this.assets.get(`type`).get(fileName).refCount)
      val dependencies: com.badlogic.gdx.utils.Array[java.lang.String] = this.assetDependencies.get(fileName)
      if (dependencies != null) {
        buffer.append(", deps: [")
        for (dep <- dependencies) {
          buffer.append(dep)
          buffer.append(',')
        }
        buffer.append(']')
      } else ()
    }
    return buffer.toString()
  }
  def getAssetNames(): com.badlogic.gdx.utils.Array[java.lang.String] = {
    return this.assetTypes.keys().toArray()
  }
  def getDependencies(fileName: java.lang.String): com.badlogic.gdx.utils.Array[java.lang.String] = {
    return this.assetDependencies.get(fileName)
  }
  def getAssetType(fileName: java.lang.String): java.lang.Class[?] = {
    return this.assetTypes.get(fileName).asInstanceOf[java.lang.Class[?]]
  }
}
object AssetManager {
  class RefCountedContainer {
    var `object`: java.lang.Object = null.asInstanceOf[java.lang.Object]
    var refCount: scala.Int = 1
  }
}