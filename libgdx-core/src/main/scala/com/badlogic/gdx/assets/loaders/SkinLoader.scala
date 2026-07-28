package com.badlogic.gdx.assets.loaders

class SkinLoader(resolver$p: com.badlogic.gdx.assets.loaders.FileHandleResolver) extends com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[com.badlogic.gdx.scenes.scene2d.ui.Skin, com.badlogic.gdx.assets.loaders.SkinLoader.SkinParameter](resolver$p) {
  @java.lang.Override
  override def getDependencies(fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.SkinLoader.SkinParameter): com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[com.badlogic.gdx.scenes.scene2d.ui.Skin]] = {
    val deps: com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[com.badlogic.gdx.scenes.scene2d.ui.Skin]] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[com.badlogic.gdx.scenes.scene2d.ui.Skin]]]
    if ((parameter == null) || (parameter.textureAtlasPath == null)) {
      deps.add(new com.badlogic.gdx.assets.AssetDescriptor(file.pathWithoutExtension() + ".atlas", classOf[com.badlogic.gdx.graphics.g2d.TextureAtlas]).asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[com.badlogic.gdx.scenes.scene2d.ui.Skin]])
    } else {
      if (parameter.textureAtlasPath != null) {
        deps.add(new com.badlogic.gdx.assets.AssetDescriptor(parameter.textureAtlasPath, classOf[com.badlogic.gdx.graphics.g2d.TextureAtlas]).asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[com.badlogic.gdx.scenes.scene2d.ui.Skin]])
      } else ()
    }
    return deps.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[com.badlogic.gdx.scenes.scene2d.ui.Skin]]]
  }
  @java.lang.Override
  override def loadAsync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.SkinLoader.SkinParameter): scala.Unit = {
    ()
  }
  @java.lang.Override
  override def loadSync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.SkinLoader.SkinParameter): com.badlogic.gdx.scenes.scene2d.ui.Skin = {
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
  class SkinParameter(textureAtlasPath$p: java.lang.String, resources$p: com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Object]) extends com.badlogic.gdx.assets.AssetLoaderParameters[com.badlogic.gdx.scenes.scene2d.ui.Skin] {
    var textureAtlasPath: java.lang.String = null.asInstanceOf[java.lang.String]
    var resources: com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Object] = null.asInstanceOf[com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Object]]
    def this() = {
      this(null, null)
    }
    def this(resources: com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Object]) = {
      this(null, resources)
    }
    def this(textureAtlasPath: java.lang.String) = {
      this(textureAtlasPath, null)
    }
    this.textureAtlasPath = textureAtlasPath$p
    this.resources = resources$p
  }
  object SkinParameter {
    export com.badlogic.gdx.assets.AssetLoaderParameters.*
  }
}