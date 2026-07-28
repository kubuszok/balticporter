package com.badlogic.gdx.assets.loaders

class CubemapLoader(resolver$p: com.badlogic.gdx.assets.loaders.FileHandleResolver) extends com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[com.badlogic.gdx.graphics.Cubemap, com.badlogic.gdx.assets.loaders.CubemapLoader.CubemapParameter](resolver$p) {
  var info: com.badlogic.gdx.assets.loaders.CubemapLoader.CubemapLoaderInfo = new com.badlogic.gdx.assets.loaders.CubemapLoader.CubemapLoaderInfo()
  @java.lang.Override
  override def loadAsync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.CubemapLoader.CubemapParameter): scala.Unit = {
    this.info.filename = fileName
    if ((parameter == null) || (parameter.cubemapData == null)) {
      var format: com.badlogic.gdx.graphics.Pixmap.Format = null
      val genMipMaps: scala.Boolean = false
      this.info.cubemap = null
      if (parameter != null) {
        format = parameter.format
        this.info.cubemap = parameter.cubemap
      } else ()
      if (fileName.contains(".ktx") || fileName.contains(".zktx")) {
        this.info.data = new com.badlogic.gdx.graphics.glutils.KTXTextureData(file, genMipMaps)
      } else ()
    } else {
      this.info.data = parameter.cubemapData
      this.info.cubemap = parameter.cubemap
    }
    if (!this.info.data.isPrepared()) {
      this.info.data.prepare()
    } else ()
  }
  @java.lang.Override
  override def loadSync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.CubemapLoader.CubemapParameter): ?T = {
    if (this.info == null) {
      return null
    } else ()
    var cubemap: com.badlogic.gdx.graphics.Cubemap = this.info.cubemap
    if (cubemap != null) {
      cubemap.load(this.info.data)
    } else {
      cubemap = new com.badlogic.gdx.graphics.Cubemap(this.info.data)
    }
    if (parameter != null) {
      cubemap.setFilter(parameter.minFilter, parameter.magFilter)
      cubemap.setWrap(parameter.wrapU, parameter.wrapV)
    } else ()
    return cubemap
  }
  @java.lang.Override
  override def getDependencies(fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.CubemapLoader.CubemapParameter): com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]] = {
    return null
  }
}
object CubemapLoader {
  class CubemapLoaderInfo {
    var filename: java.lang.String = null.asInstanceOf[java.lang.String]
    var data: com.badlogic.gdx.graphics.CubemapData = null.asInstanceOf[com.badlogic.gdx.graphics.CubemapData]
    var cubemap: com.badlogic.gdx.graphics.Cubemap = null.asInstanceOf[com.badlogic.gdx.graphics.Cubemap]
  }
  class CubemapParameter extends com.badlogic.gdx.assets.AssetLoaderParameters[com.badlogic.gdx.graphics.Cubemap] {
    var format: com.badlogic.gdx.graphics.Pixmap.Format = null
    var cubemap: com.badlogic.gdx.graphics.Cubemap = null
    var cubemapData: com.badlogic.gdx.graphics.CubemapData = null
    var minFilter: com.badlogic.gdx.graphics.Texture.TextureFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest
    var magFilter: com.badlogic.gdx.graphics.Texture.TextureFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest
    var wrapU: com.badlogic.gdx.graphics.Texture.TextureWrap = com.badlogic.gdx.graphics.Texture.TextureWrap.ClampToEdge
    var wrapV: com.badlogic.gdx.graphics.Texture.TextureWrap = com.badlogic.gdx.graphics.Texture.TextureWrap.ClampToEdge
  }
  object CubemapParameter {
    export com.badlogic.gdx.assets.AssetLoaderParameters.*
  }
}