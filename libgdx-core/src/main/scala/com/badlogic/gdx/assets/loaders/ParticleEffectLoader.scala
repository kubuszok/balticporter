package com.badlogic.gdx.assets.loaders

class ParticleEffectLoader extends com.badlogic.gdx.assets.loaders.SynchronousAssetLoader[com.badlogic.gdx.graphics.g2d.ParticleEffect, com.badlogic.gdx.assets.loaders.ParticleEffectLoader.ParticleEffectParameter] {
  def this(resolver: com.badlogic.gdx.assets.loaders.FileHandleResolver) = {
    this()
  }
  def load(am: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, param: com.badlogic.gdx.assets.loaders.ParticleEffectLoader.ParticleEffectParameter): com.badlogic.gdx.graphics.g2d.ParticleEffect = {
    val effect: com.badlogic.gdx.graphics.g2d.ParticleEffect = new com.badlogic.gdx.graphics.g2d.ParticleEffect()
    if ((param != null) && (param.atlasFile != null)) {
      effect.load(file, am.get(param.atlasFile, classOf[com.badlogic.gdx.graphics.g2d.TextureAtlas]), param.atlasPrefix)
    } else {
      if ((param != null) && (param.imagesDir != null)) {
        effect.load(file, param.imagesDir)
      } else {
        effect.load(file, file.parent())
      }
    }
    return effect
  }
  def getDependencies(fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, param: com.badlogic.gdx.assets.loaders.ParticleEffectLoader.ParticleEffectParameter): com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]] = {
    var deps: com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]] = null
    if ((param != null) && (param.atlasFile != null)) {
      deps = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]]
      deps.add(new com.badlogic.gdx.assets.AssetDescriptor[com.badlogic.gdx.graphics.g2d.TextureAtlas](param.atlasFile, classOf[com.badlogic.gdx.graphics.g2d.TextureAtlas]))
    } else ()
    return deps
  }
}
object ParticleEffectLoader {
  class ParticleEffectParameter extends com.badlogic.gdx.assets.AssetLoaderParameters[com.badlogic.gdx.graphics.g2d.ParticleEffect] {
    var atlasFile: java.lang.String = null.asInstanceOf[java.lang.String]
    var atlasPrefix: java.lang.String = null.asInstanceOf[java.lang.String]
    var imagesDir: com.badlogic.gdx.files.FileHandle = null.asInstanceOf[com.badlogic.gdx.files.FileHandle]
  }
  object ParticleEffectParameter {
    export com.badlogic.gdx.assets.AssetLoaderParameters.*
  }
}