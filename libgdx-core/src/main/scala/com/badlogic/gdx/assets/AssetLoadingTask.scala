package com.badlogic.gdx.assets

class AssetLoadingTask(manager$p: com.badlogic.gdx.assets.AssetManager, assetDesc$p: com.badlogic.gdx.assets.AssetDescriptor[?], loader$p: com.badlogic.gdx.assets.loaders.AssetLoader[?, ?], threadPool: com.badlogic.gdx.utils.async.AsyncExecutor) extends com.badlogic.gdx.utils.async.AsyncTask[java.lang.Void] {
  var manager: com.badlogic.gdx.assets.AssetManager = null.asInstanceOf[com.badlogic.gdx.assets.AssetManager]
  var assetDesc: com.badlogic.gdx.assets.AssetDescriptor[?] = null.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[?]]
  var loader: com.badlogic.gdx.assets.loaders.AssetLoader[?, ?] = null.asInstanceOf[com.badlogic.gdx.assets.loaders.AssetLoader[?, ?]]
  var executor: com.badlogic.gdx.utils.async.AsyncExecutor = null.asInstanceOf[com.badlogic.gdx.utils.async.AsyncExecutor]
  var startTime: scala.Long = 0L
  var asyncDone: scala.Boolean = false
  var dependenciesLoaded: scala.Boolean = false
  var dependencies: com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]] = null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]]
  var depsFuture: com.badlogic.gdx.utils.async.AsyncResult[java.lang.Void] = null.asInstanceOf[com.badlogic.gdx.utils.async.AsyncResult[java.lang.Void]]
  var loadFuture: com.badlogic.gdx.utils.async.AsyncResult[java.lang.Void] = null.asInstanceOf[com.badlogic.gdx.utils.async.AsyncResult[java.lang.Void]]
  var asset: java.lang.Object = null.asInstanceOf[java.lang.Object]
  var cancel: scala.Boolean = false
  this.manager = manager$p
  this.assetDesc = assetDesc$p.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[?]]
  this.loader = loader$p.asInstanceOf[com.badlogic.gdx.assets.loaders.AssetLoader[?, ?]]
  this.executor = threadPool
  this.startTime = if (manager$p.log.getLevel() == com.badlogic.gdx.utils.Logger.DEBUG) com.badlogic.gdx.utils.TimeUtils.nanoTime() else 0
  @java.lang.Override
  override def call(): ?T = {
    if (this.cancel) {
      return null
    } else ()
    val asyncLoader: com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[?, ?] = this.loader.asInstanceOf[com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[?, ?]].asInstanceOf[com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[?, ?]]
    if (!this.dependenciesLoaded) {
      this.dependencies = asyncLoader.asInstanceOf[com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[java.lang.Object, com.badlogic.gdx.assets.AssetLoaderParameters[java.lang.Object]]].getDependencies(this.assetDesc.fileName, this.resolve(this.loader.asInstanceOf[com.badlogic.gdx.assets.loaders.AssetLoader[?, ?]], this.assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[?]]), this.assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].params.asInstanceOf[com.badlogic.gdx.assets.AssetLoaderParameters[java.lang.Object]]).asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]]
      if (this.dependencies != null) {
        this.removeDuplicates(this.dependencies.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]])
        this.manager.injectDependencies(this.assetDesc.fileName, this.dependencies.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]])
      } else {
        asyncLoader.asInstanceOf[com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[java.lang.Object, com.badlogic.gdx.assets.AssetLoaderParameters[java.lang.Object]]].loadAsync(this.manager, this.assetDesc.fileName, this.resolve(this.loader.asInstanceOf[com.badlogic.gdx.assets.loaders.AssetLoader[?, ?]], this.assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[?]]), this.assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].params.asInstanceOf[com.badlogic.gdx.assets.AssetLoaderParameters[java.lang.Object]])
        this.asyncDone = true
      }
    } else {
      asyncLoader.asInstanceOf[com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[java.lang.Object, com.badlogic.gdx.assets.AssetLoaderParameters[java.lang.Object]]].loadAsync(this.manager, this.assetDesc.fileName, this.resolve(this.loader.asInstanceOf[com.badlogic.gdx.assets.loaders.AssetLoader[?, ?]], this.assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[?]]), this.assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].params.asInstanceOf[com.badlogic.gdx.assets.AssetLoaderParameters[java.lang.Object]])
      this.asyncDone = true
    }
    return null
  }
  def update(): scala.Boolean = {
    if (this.loader.isInstanceOf[com.badlogic.gdx.assets.loaders.SynchronousAssetLoader[?, ?]]) {
      this.handleSyncLoader()
    } else {
      this.handleAsyncLoader()
    }
    return this.asset != null
  }
  private def handleSyncLoader(): scala.Unit = {
    val syncLoader: com.badlogic.gdx.assets.loaders.SynchronousAssetLoader[?, ?] = this.loader.asInstanceOf[com.badlogic.gdx.assets.loaders.SynchronousAssetLoader[?, ?]].asInstanceOf[com.badlogic.gdx.assets.loaders.SynchronousAssetLoader[?, ?]]
    if (!this.dependenciesLoaded) {
      this.dependenciesLoaded = true
      this.dependencies = syncLoader.asInstanceOf[com.badlogic.gdx.assets.loaders.SynchronousAssetLoader[java.lang.Object, com.badlogic.gdx.assets.AssetLoaderParameters[java.lang.Object]]].getDependencies(this.assetDesc.fileName, this.resolve(this.loader.asInstanceOf[com.badlogic.gdx.assets.loaders.AssetLoader[?, ?]], this.assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[?]]), this.assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].params.asInstanceOf[com.badlogic.gdx.assets.AssetLoaderParameters[java.lang.Object]]).asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]]
      if (this.dependencies == null) {
        this.asset = syncLoader.asInstanceOf[com.badlogic.gdx.assets.loaders.SynchronousAssetLoader[java.lang.Object, com.badlogic.gdx.assets.AssetLoaderParameters[java.lang.Object]]].load(this.manager, this.assetDesc.fileName, this.resolve(this.loader.asInstanceOf[com.badlogic.gdx.assets.loaders.AssetLoader[?, ?]], this.assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[?]]), this.assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].params.asInstanceOf[com.badlogic.gdx.assets.AssetLoaderParameters[java.lang.Object]])
        return
      } else ()
      this.removeDuplicates(this.dependencies.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]])
      this.manager.injectDependencies(this.assetDesc.fileName, this.dependencies.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]])
    } else {
      this.asset = syncLoader.asInstanceOf[com.badlogic.gdx.assets.loaders.SynchronousAssetLoader[java.lang.Object, com.badlogic.gdx.assets.AssetLoaderParameters[java.lang.Object]]].load(this.manager, this.assetDesc.fileName, this.resolve(this.loader.asInstanceOf[com.badlogic.gdx.assets.loaders.AssetLoader[?, ?]], this.assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[?]]), this.assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].params.asInstanceOf[com.badlogic.gdx.assets.AssetLoaderParameters[java.lang.Object]])
    }
  }
  private def handleAsyncLoader(): scala.Unit = {
    val asyncLoader: com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[?, ?] = this.loader.asInstanceOf[com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[?, ?]].asInstanceOf[com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[?, ?]]
    if (!this.dependenciesLoaded) {
      if (this.depsFuture == null) {
        this.depsFuture = this.executor.submit(this)
      } else {
        if (this.depsFuture.isDone()) {
          try {
            this.depsFuture.get()
          } catch {
            case e: java.lang.Exception => {
              throw new com.badlogic.gdx.utils.GdxRuntimeException("Couldn't load dependencies of asset: " + this.assetDesc.fileName, e)
            }
          }
          this.dependenciesLoaded = true
          if (this.asyncDone) {
            this.asset = asyncLoader.asInstanceOf[com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[java.lang.Object, com.badlogic.gdx.assets.AssetLoaderParameters[java.lang.Object]]].loadSync(this.manager, this.assetDesc.fileName, this.resolve(this.loader.asInstanceOf[com.badlogic.gdx.assets.loaders.AssetLoader[?, ?]], this.assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[?]]), this.assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].params.asInstanceOf[com.badlogic.gdx.assets.AssetLoaderParameters[java.lang.Object]])
          } else ()
        } else ()
      }
    } else {
      if ((this.loadFuture == null) && (!this.asyncDone)) {
        this.loadFuture = this.executor.submit(this)
      } else {
        if (this.asyncDone) {
          this.asset = asyncLoader.asInstanceOf[com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[java.lang.Object, com.badlogic.gdx.assets.AssetLoaderParameters[java.lang.Object]]].loadSync(this.manager, this.assetDesc.fileName, this.resolve(this.loader.asInstanceOf[com.badlogic.gdx.assets.loaders.AssetLoader[?, ?]], this.assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[?]]), this.assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].params.asInstanceOf[com.badlogic.gdx.assets.AssetLoaderParameters[java.lang.Object]])
        } else {
          if (this.loadFuture.isDone()) {
            try {
              this.loadFuture.get()
            } catch {
              case e: java.lang.Exception => {
                throw new com.badlogic.gdx.utils.GdxRuntimeException("Couldn't load asset: " + this.assetDesc.fileName, e)
              }
            }
            this.asset = asyncLoader.asInstanceOf[com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[java.lang.Object, com.badlogic.gdx.assets.AssetLoaderParameters[java.lang.Object]]].loadSync(this.manager, this.assetDesc.fileName, this.resolve(this.loader.asInstanceOf[com.badlogic.gdx.assets.loaders.AssetLoader[?, ?]], this.assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[?]]), this.assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].params.asInstanceOf[com.badlogic.gdx.assets.AssetLoaderParameters[java.lang.Object]])
          } else ()
        }
      }
    }
  }
  def unload(): scala.Unit = {
    if (this.loader.isInstanceOf[com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[?, ?]]) {
      this.loader.asInstanceOf[com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[?, ?]].asInstanceOf[com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[java.lang.Object, com.badlogic.gdx.assets.AssetLoaderParameters[java.lang.Object]]].unloadAsync(this.manager, this.assetDesc.fileName, this.resolve(this.loader.asInstanceOf[com.badlogic.gdx.assets.loaders.AssetLoader[?, ?]], this.assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[?]]), this.assetDesc.asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].params.asInstanceOf[com.badlogic.gdx.assets.AssetLoaderParameters[java.lang.Object]])
    } else ()
  }
  private def resolve(loader: com.badlogic.gdx.assets.loaders.AssetLoader[?, ?], assetDesc: com.badlogic.gdx.assets.AssetDescriptor[?]): com.badlogic.gdx.files.FileHandle = {
    if (assetDesc.file == null) {
      assetDesc.file = loader.resolve(assetDesc.fileName)
    } else ()
    return assetDesc.file
  }
  private def removeDuplicates(array: com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]): scala.Unit = {
    var ordered: scala.Boolean = array.ordered
    array.ordered = true;
    { var i: scala.Int = 0; while (i < array.size) { {
      val fn: java.lang.String = array.get(i).fileName
      val `type`: java.lang.Class[?] = array.get(i).asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].`type`.asInstanceOf[java.lang.Class[?]];
      { var j: scala.Int = array.size - 1; while (j > i) { {
        if ((`type` == array.get(j).asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[java.lang.Object]].`type`) && fn.equals(array.get(j).fileName)) {
          array.removeIndex(j)
        } else ()
      }; j = j - 1 } }
    }; i = i + 1 } }
    array.ordered = ordered
  }
}