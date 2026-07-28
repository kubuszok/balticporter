package com.badlogic.gdx.assets.loaders

abstract class ModelLoader[P <: com.badlogic.gdx.assets.loaders.ModelLoader.ModelParameters](resolver$p: com.badlogic.gdx.assets.loaders.FileHandleResolver) extends com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[com.badlogic.gdx.graphics.g3d.Model, P](resolver$p) {
  var items: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.ObjectMap.Entry[java.lang.String, com.badlogic.gdx.graphics.g3d.model.data.ModelData]] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.ObjectMap.Entry[java.lang.String, com.badlogic.gdx.graphics.g3d.model.data.ModelData]]()
  var defaultParameters: com.badlogic.gdx.assets.loaders.ModelLoader.ModelParameters = new com.badlogic.gdx.assets.loaders.ModelLoader.ModelParameters()
  def loadModelData(fileHandle: com.badlogic.gdx.files.FileHandle, parameters: P): com.badlogic.gdx.graphics.g3d.model.data.ModelData
  def loadModelData(fileHandle: com.badlogic.gdx.files.FileHandle): com.badlogic.gdx.graphics.g3d.model.data.ModelData = {
    return this.loadModelData(fileHandle, null.asInstanceOf[P])
  }
  def loadModel(fileHandle: com.badlogic.gdx.files.FileHandle, textureProvider: com.badlogic.gdx.graphics.g3d.utils.TextureProvider, parameters: P): com.badlogic.gdx.graphics.g3d.Model = {
    val data: com.badlogic.gdx.graphics.g3d.model.data.ModelData = this.loadModelData(fileHandle, parameters)
    return if (data == null) null.asInstanceOf[com.badlogic.gdx.graphics.g3d.Model] else new com.badlogic.gdx.graphics.g3d.Model(data, textureProvider)
  }
  def loadModel(fileHandle: com.badlogic.gdx.files.FileHandle, parameters: P): com.badlogic.gdx.graphics.g3d.Model = {
    return this.loadModel(fileHandle, new com.badlogic.gdx.graphics.g3d.utils.TextureProvider.FileTextureProvider(), parameters)
  }
  def loadModel(fileHandle: com.badlogic.gdx.files.FileHandle, textureProvider: com.badlogic.gdx.graphics.g3d.utils.TextureProvider): com.badlogic.gdx.graphics.g3d.Model = {
    return this.loadModel(fileHandle, textureProvider, null.asInstanceOf[P])
  }
  def loadModel(fileHandle: com.badlogic.gdx.files.FileHandle): com.badlogic.gdx.graphics.g3d.Model = {
    return this.loadModel(fileHandle, new com.badlogic.gdx.graphics.g3d.utils.TextureProvider.FileTextureProvider(), null.asInstanceOf[P])
  }
  @java.lang.Override
  def getDependencies(fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameters: P): com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]] = {
    val deps: com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]]
    val data: com.badlogic.gdx.graphics.g3d.model.data.ModelData = this.loadModelData(file, parameters)
    if (data == null) {
      return deps.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]]
    } else ()
    val item: com.badlogic.gdx.utils.ObjectMap.Entry[java.lang.String, com.badlogic.gdx.graphics.g3d.model.data.ModelData] = new com.badlogic.gdx.utils.ObjectMap.Entry[java.lang.String, com.badlogic.gdx.graphics.g3d.model.data.ModelData]()
    item.key = fileName
    item.value = data
    this.items.synchronized {
      this.items.add(item)
    }
    val textureParameter: com.badlogic.gdx.assets.loaders.TextureLoader.TextureParameter = if (parameters != null) parameters.textureParameter else this.defaultParameters.textureParameter
    for (modelMaterial <- data.materials) {
      if (modelMaterial.textures != null) {
        for (modelTexture <- modelMaterial.textures) {
          deps.add(new com.badlogic.gdx.assets.AssetDescriptor(modelTexture.fileName, classOf[com.badlogic.gdx.graphics.Texture], textureParameter))
        }
      } else ()
    }
    return deps.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]]
  }
  @java.lang.Override
  def loadAsync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameters: P): scala.Unit = {
    ()
  }
  @java.lang.Override
  def loadSync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameters: P): com.badlogic.gdx.graphics.g3d.Model = {
    var data: com.badlogic.gdx.graphics.g3d.model.data.ModelData = null
    this.items.synchronized {
      { var i: scala.Int = 0; while (i < this.items.size) { {
        if (this.items.get(i).key.equals(fileName)) {
          data = this.items.get(i).value
          this.items.removeIndex(i)
        } else ()
      }; i = i + 1 } }
    }
    if (data == null) {
      return null
    } else ()
    val result: com.badlogic.gdx.graphics.g3d.Model = new com.badlogic.gdx.graphics.g3d.Model(data, new com.badlogic.gdx.graphics.g3d.utils.TextureProvider.AssetTextureProvider(manager))
    val disposables: balticporter.runtime.JavaIterator[com.badlogic.gdx.utils.Disposable] = result.getManagedDisposables().iterator
    while (disposables.hasNext) {
      val disposable: com.badlogic.gdx.utils.Disposable = disposables.next
      if (disposable.isInstanceOf[com.badlogic.gdx.graphics.Texture]) {
        disposables.remove()
      } else ()
    }
    return result
  }
}
object ModelLoader {
  class ModelParameters extends com.badlogic.gdx.assets.AssetLoaderParameters[com.badlogic.gdx.graphics.g3d.Model] {
    var textureParameter: com.badlogic.gdx.assets.loaders.TextureLoader.TextureParameter = null.asInstanceOf[com.badlogic.gdx.assets.loaders.TextureLoader.TextureParameter]
    this.textureParameter = new com.badlogic.gdx.assets.loaders.TextureLoader.TextureParameter()
    this.textureParameter.minFilter = {
      this.textureParameter.magFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear
      this.textureParameter.magFilter
    }
    this.textureParameter.wrapU = {
      this.textureParameter.wrapV = com.badlogic.gdx.graphics.Texture.TextureWrap.Repeat
      this.textureParameter.wrapV
    }
  }
  object ModelParameters {
    export com.badlogic.gdx.assets.AssetLoaderParameters.*
  }
}