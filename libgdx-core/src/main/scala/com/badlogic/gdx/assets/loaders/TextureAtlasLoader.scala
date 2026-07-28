package com.badlogic.gdx.assets.loaders

class TextureAtlasLoader(resolver$p: com.badlogic.gdx.assets.loaders.FileHandleResolver) extends com.badlogic.gdx.assets.loaders.SynchronousAssetLoader[com.badlogic.gdx.graphics.g2d.TextureAtlas, com.badlogic.gdx.assets.loaders.TextureAtlasLoader.TextureAtlasParameter](resolver$p) {
  var data: com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData]
  @java.lang.Override
  override def load(assetManager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.TextureAtlasLoader.TextureAtlasParameter): ?T = {
    for (page <- this.data.getPages()) {
      var texture: com.badlogic.gdx.graphics.Texture = assetManager.get(page.textureFile.path().replaceAll("\\\\", "/"), classOf[com.badlogic.gdx.graphics.Texture])
      page.texture = texture
    }
    val atlas: com.badlogic.gdx.graphics.g2d.TextureAtlas = new com.badlogic.gdx.graphics.g2d.TextureAtlas(this.data)
    this.data = null
    return atlas
  }
  @java.lang.Override
  override def getDependencies(fileName: java.lang.String, atlasFile: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.TextureAtlasLoader.TextureAtlasParameter): com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]] = {
    val imgDir: com.badlogic.gdx.files.FileHandle = atlasFile.parent()
    if (parameter != null) {
      this.data = new com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData(atlasFile, imgDir, parameter.flip)
    } else {
      this.data = new com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData(atlasFile, imgDir, false)
    }
    val dependencies: com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]]
    for (page <- this.data.getPages()) {
      val params: com.badlogic.gdx.assets.loaders.TextureLoader.TextureParameter = new com.badlogic.gdx.assets.loaders.TextureLoader.TextureParameter()
      params.format = page.format
      params.genMipMaps = page.useMipMaps
      params.minFilter = page.minFilter
      params.magFilter = page.magFilter
      dependencies.add(new com.badlogic.gdx.assets.AssetDescriptor(page.textureFile, classOf[com.badlogic.gdx.graphics.Texture], params))
    }
    return dependencies.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]]
  }
}
object TextureAtlasLoader {
  class TextureAtlasParameter extends com.badlogic.gdx.assets.AssetLoaderParameters[com.badlogic.gdx.graphics.g2d.TextureAtlas] {
    var flip: scala.Boolean = false
    def this(flip: scala.Boolean) = {
      this()
      this.flip = flip
    }
  }
  object TextureAtlasParameter {
    export com.badlogic.gdx.assets.AssetLoaderParameters.*
  }
}