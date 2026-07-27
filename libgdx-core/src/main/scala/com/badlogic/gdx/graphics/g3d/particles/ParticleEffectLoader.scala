package com.badlogic.gdx.graphics.g3d.particles

class ParticleEffectLoader(resolver$p: com.badlogic.gdx.assets.loaders.FileHandleResolver) extends com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[com.badlogic.gdx.graphics.g3d.particles.ParticleEffect, com.badlogic.gdx.graphics.g3d.particles.ParticleEffectLoader.ParticleEffectLoadParameter](resolver$p) {
  var items: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.ObjectMap.Entry[java.lang.String, com.badlogic.gdx.graphics.g3d.particles.ResourceData[com.badlogic.gdx.graphics.g3d.particles.ParticleEffect]]] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.ObjectMap.Entry[java.lang.String, com.badlogic.gdx.graphics.g3d.particles.ResourceData[com.badlogic.gdx.graphics.g3d.particles.ParticleEffect]]]()
  def loadAsync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.graphics.g3d.particles.ParticleEffectLoader.ParticleEffectLoadParameter): scala.Unit = {
    ()
  }
  def getDependencies(fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.graphics.g3d.particles.ParticleEffectLoader.ParticleEffectLoadParameter): com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]] = {
    val json: com.badlogic.gdx.utils.Json = new com.badlogic.gdx.utils.Json()
    val data: com.badlogic.gdx.graphics.g3d.particles.ResourceData[com.badlogic.gdx.graphics.g3d.particles.ParticleEffect] = json.fromJson(classOf[com.badlogic.gdx.graphics.g3d.particles.ResourceData[?]], file).asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ResourceData[com.badlogic.gdx.graphics.g3d.particles.ParticleEffect]]
    var assets: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ResourceData.AssetData[?]] = null
    this.items.synchronized {
      val entry: com.badlogic.gdx.utils.ObjectMap.Entry[java.lang.String, com.badlogic.gdx.graphics.g3d.particles.ResourceData[com.badlogic.gdx.graphics.g3d.particles.ParticleEffect]] = new com.badlogic.gdx.utils.ObjectMap.Entry[java.lang.String, com.badlogic.gdx.graphics.g3d.particles.ResourceData[com.badlogic.gdx.graphics.g3d.particles.ParticleEffect]]()
      entry.key = fileName
      entry.value = data
      this.items.add(entry)
      assets = data.getAssets().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ResourceData.AssetData[?]]]
    }
    val descriptors: com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]]
    for (assetData <- assets) {
      if (!this.resolve(assetData.filename).exists()) {
        assetData.filename = file.parent().child(com.badlogic.gdx.Gdx.files.internal(assetData.filename).name()).path()
      } else ()
      if (assetData.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ResourceData.AssetData[java.lang.Object]].`type` == classOf[com.badlogic.gdx.graphics.g3d.particles.ParticleEffect]) {
        descriptors.add(new com.badlogic.gdx.assets.AssetDescriptor(assetData.filename, assetData.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ResourceData.AssetData[java.lang.Object]].`type`, parameter))
      } else {
        descriptors.add(new com.badlogic.gdx.assets.AssetDescriptor(assetData.filename, assetData.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ResourceData.AssetData[java.lang.Object]].`type`))
      }
    }
    return descriptors.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]]
  }
  def save(effect: com.badlogic.gdx.graphics.g3d.particles.ParticleEffect, parameter: com.badlogic.gdx.graphics.g3d.particles.ParticleEffectLoader.ParticleEffectSaveParameter): scala.Unit = {
    val data: com.badlogic.gdx.graphics.g3d.particles.ResourceData[com.badlogic.gdx.graphics.g3d.particles.ParticleEffect] = new com.badlogic.gdx.graphics.g3d.particles.ResourceData[com.badlogic.gdx.graphics.g3d.particles.ParticleEffect](effect)
    effect.save(parameter.manager, data)
    if (parameter.batches != null) {
      for (batch <- parameter.batches) {
        var save: scala.Boolean = false
        for (controller <- effect.getControllers()) {
          if (controller.renderer.isCompatible(batch)) {
            save = true
            /* break */ ()
          } else ()
        }
        if (save) {
          batch.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.batches.ParticleBatch[com.badlogic.gdx.graphics.g3d.particles.renderers.ParticleControllerRenderData]].save(parameter.manager, data.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ResourceData[com.badlogic.gdx.graphics.g3d.particles.renderers.ParticleControllerRenderData]])
        } else ()
      }
    } else ()
    val json: com.badlogic.gdx.utils.Json = new com.badlogic.gdx.utils.Json(parameter.jsonOutputType)
    if (parameter.prettyPrint) {
      val prettyJson: java.lang.String = json.prettyPrint(data)
      parameter.file.writeString(prettyJson, false)
    } else {
      json.toJson(data, parameter.file)
    }
  }
  def loadSync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.graphics.g3d.particles.ParticleEffectLoader.ParticleEffectLoadParameter): com.badlogic.gdx.graphics.g3d.particles.ParticleEffect = {
    var effectData: com.badlogic.gdx.graphics.g3d.particles.ResourceData[com.badlogic.gdx.graphics.g3d.particles.ParticleEffect] = null
    this.items.synchronized {
      { var i: scala.Int = 0; while (i < this.items.size) { {
        val entry: com.badlogic.gdx.utils.ObjectMap.Entry[java.lang.String, com.badlogic.gdx.graphics.g3d.particles.ResourceData[com.badlogic.gdx.graphics.g3d.particles.ParticleEffect]] = this.items.get(i)
        if (entry.key.equals(fileName)) {
          effectData = entry.value
          this.items.removeIndex(i)
          /* break */ ()
        } else ()
      }; i = i + 1 } }
    }
    effectData.resource.load(manager, effectData)
    if (parameter != null) {
      if (parameter.batches != null) {
        for (batch <- parameter.batches) {
          batch.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.batches.ParticleBatch[com.badlogic.gdx.graphics.g3d.particles.renderers.ParticleControllerRenderData]].load(manager, effectData.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ResourceData[com.badlogic.gdx.graphics.g3d.particles.renderers.ParticleControllerRenderData]])
        }
      } else ()
      effectData.resource.setBatch(parameter.batches)
    } else ()
    return effectData.resource
  }
  private def find[T](array: com.badlogic.gdx.utils.Array[?], `type`: java.lang.Class[T]): T = {
    for (`object` <- array) {
      if (`type`.isAssignableFrom(`object`.getClass())) {
        return `object`.asInstanceOf[T].asInstanceOf[T]
      } else ()
    }
    return null.asInstanceOf[T]
  }
}
object ParticleEffectLoader {
  class ParticleEffectLoadParameter(batches$p: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.batches.ParticleBatch[?]]) extends com.badlogic.gdx.assets.AssetLoaderParameters[com.badlogic.gdx.graphics.g3d.particles.ParticleEffect] {
    var batches: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.batches.ParticleBatch[?]] = null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.batches.ParticleBatch[?]]]
    this.batches = batches$p
  }
  object ParticleEffectLoadParameter {
    export com.badlogic.gdx.assets.AssetLoaderParameters.*
  }
  class ParticleEffectSaveParameter(file$p: com.badlogic.gdx.files.FileHandle, manager$p: com.badlogic.gdx.assets.AssetManager, batches$p: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.batches.ParticleBatch[?]], jsonOutputType$p: com.badlogic.gdx.utils.JsonWriter.OutputType, prettyPrint$p: scala.Boolean) extends com.badlogic.gdx.assets.AssetLoaderParameters[com.badlogic.gdx.graphics.g3d.particles.ParticleEffect] {
    var batches: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.batches.ParticleBatch[?]] = null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.batches.ParticleBatch[?]]]
    var file: com.badlogic.gdx.files.FileHandle = null.asInstanceOf[com.badlogic.gdx.files.FileHandle]
    var manager: com.badlogic.gdx.assets.AssetManager = null.asInstanceOf[com.badlogic.gdx.assets.AssetManager]
    var jsonOutputType: com.badlogic.gdx.utils.JsonWriter.OutputType = null.asInstanceOf[com.badlogic.gdx.utils.JsonWriter.OutputType]
    var prettyPrint: scala.Boolean = false
    def this(file: com.badlogic.gdx.files.FileHandle, manager: com.badlogic.gdx.assets.AssetManager, batches: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.batches.ParticleBatch[?]]) = {
      this(file, manager, batches, com.badlogic.gdx.utils.JsonWriter.OutputType.minimal, false)
    }
    this.batches = batches$p
    this.file = file$p
    this.manager = manager$p
    this.jsonOutputType = jsonOutputType$p
    this.prettyPrint = prettyPrint$p
  }
  object ParticleEffectSaveParameter {
    export com.badlogic.gdx.assets.AssetLoaderParameters.*
  }
}