package com.badlogic.gdx.assets.loaders

class SkinLoader extends com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[com.badlogic.gdx.scenes.scene2d.ui.Skin, com.badlogic.gdx.assets.loaders.SkinLoader.SkinParameter] {
  def this(resolver: com.badlogic.gdx.assets.loaders.FileHandleResolver) = {
    this()
  }
  def getDependencies(fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.SkinLoader.SkinParameter): com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]] = {
    val deps: com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]]
    if ((parameter == null) || (parameter.textureAtlasPath == null)) {
      deps.add(new com.badlogic.gdx.assets.AssetDescriptor(file.pathWithoutExtension() + ".atlas", classOf[com.badlogic.gdx.graphics.g2d.TextureAtlas]))
    } else {
      if (parameter.textureAtlasPath != null) {
        deps.add(new com.badlogic.gdx.assets.AssetDescriptor(parameter.textureAtlasPath, classOf[com.badlogic.gdx.graphics.g2d.TextureAtlas]))
      } else ()
    }
    return deps.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]]
  }
  def loadAsync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.SkinLoader.SkinParameter): scala.Unit = {
    ()
  }
  def loadSync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.SkinLoader.SkinParameter): com.badlogic.gdx.scenes.scene2d.ui.Skin = {
    var textureAtlasPath: java.lang.String = file.pathWithoutExtension() + ".atlas"
    var resources: com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Object] = null
    if (parameter != null) {
      if (parameter.textureAtlasPath != null) {
        textureAtlasPath = parameter.textureAtlasPath
      } else ()
      if (parameter.resources != null) {
        resources = parameter.resources
      } else ()
    } else ()
    val atlas: com.badlogic.gdx.graphics.g2d.TextureAtlas = manager.get(textureAtlasPath, classOf[com.badlogic.gdx.graphics.g2d.TextureAtlas])
    val skin: com.badlogic.gdx.scenes.scene2d.ui.Skin = this.newSkin(atlas)
    if (resources != null) {
      for (entry <- resources.entries()) {
        skin.add(entry.key, entry.value)
      }
    } else ()
    skin.load(file)
    return skin
  }
  def newSkin(atlas: com.badlogic.gdx.graphics.g2d.TextureAtlas): com.badlogic.gdx.scenes.scene2d.ui.Skin = {
    return new com.badlogic.gdx.scenes.scene2d.ui.Skin(atlas)
  }
}
object SkinLoader {
  class SkinParameter extends com.badlogic.gdx.assets.AssetLoaderParameters[com.badlogic.gdx.scenes.scene2d.ui.Skin] {
    var textureAtlasPath: java.lang.String = null.asInstanceOf[java.lang.String]
    var resources: com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Object] = null.asInstanceOf[com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Object]]
    def this(textureAtlasPath: java.lang.String, resources: com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Object]) = {
      this()
      this.textureAtlasPath = textureAtlasPath
      this.resources = resources
    }
    def this(resources: com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Object]) = {
      this(null, resources)
    }
    def this(textureAtlasPath: java.lang.String) = {
      this(textureAtlasPath, null)
    }
  }
  object SkinParameter {
    export com.badlogic.gdx.assets.AssetLoaderParameters.*
  }
}