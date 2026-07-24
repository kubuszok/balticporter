package com.badlogic.gdx.assets.loaders

class TextureLoader extends com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[com.badlogic.gdx.graphics.Texture, com.badlogic.gdx.assets.loaders.TextureLoader.TextureParameter] {
  var info: com.badlogic.gdx.assets.loaders.TextureLoader.TextureLoaderInfo = new com.badlogic.gdx.assets.loaders.TextureLoader.TextureLoaderInfo()
  def this(resolver: com.badlogic.gdx.assets.loaders.FileHandleResolver) = {
    this()
  }
  def loadAsync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.TextureLoader.TextureParameter): scala.Unit = {
    this.info.filename = fileName
    if ((parameter == null) || (parameter.textureData == null)) {
      var format: com.badlogic.gdx.graphics.Pixmap.Format = null
      var genMipMaps: scala.Boolean = false
      this.info.texture = null
      if (parameter != null) {
        format = parameter.format
        genMipMaps = parameter.genMipMaps
        this.info.texture = parameter.texture
      } else ()
      this.info.data = com.badlogic.gdx.graphics.TextureData.Factory.loadFromFile(file, format, genMipMaps)
    } else {
      this.info.data = parameter.textureData
      this.info.texture = parameter.texture
    }
    if (!this.info.data.isPrepared()) {
      this.info.data.prepare()
    } else ()
  }
  def loadSync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.TextureLoader.TextureParameter): com.badlogic.gdx.graphics.Texture = {
    if (this.info == null) {
      return null
    } else ()
    var texture: com.badlogic.gdx.graphics.Texture = this.info.texture
    if (texture != null) {
      texture.load(this.info.data)
    } else {
      texture = new com.badlogic.gdx.graphics.Texture(this.info.data)
    }
    if (parameter != null) {
      texture.setFilter(parameter.minFilter, parameter.magFilter)
      texture.setWrap(parameter.wrapU, parameter.wrapV)
    } else ()
    return texture
  }
  def getDependencies(fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.TextureLoader.TextureParameter): com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]] = {
    return null
  }
}
object TextureLoader {
  class TextureLoaderInfo {
    var filename: java.lang.String = null.asInstanceOf[java.lang.String]
    var data: com.badlogic.gdx.graphics.TextureData = null.asInstanceOf[com.badlogic.gdx.graphics.TextureData]
    var texture: com.badlogic.gdx.graphics.Texture = null.asInstanceOf[com.badlogic.gdx.graphics.Texture]
  }
  class TextureParameter extends com.badlogic.gdx.assets.AssetLoaderParameters[com.badlogic.gdx.graphics.Texture] {
    var format: com.badlogic.gdx.graphics.Pixmap.Format = null
    var genMipMaps: scala.Boolean = false
    var texture: com.badlogic.gdx.graphics.Texture = null
    var textureData: com.badlogic.gdx.graphics.TextureData = null
    var minFilter: com.badlogic.gdx.graphics.Texture.TextureFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest
    var magFilter: com.badlogic.gdx.graphics.Texture.TextureFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest
    var wrapU: com.badlogic.gdx.graphics.Texture.TextureWrap = com.badlogic.gdx.graphics.Texture.TextureWrap.ClampToEdge
    var wrapV: com.badlogic.gdx.graphics.Texture.TextureWrap = com.badlogic.gdx.graphics.Texture.TextureWrap.ClampToEdge
  }
}