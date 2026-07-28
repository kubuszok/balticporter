package com.badlogic.gdx.assets.loaders

class PixmapLoader(resolver$p: com.badlogic.gdx.assets.loaders.FileHandleResolver) extends com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[com.badlogic.gdx.graphics.Pixmap, com.badlogic.gdx.assets.loaders.PixmapLoader.PixmapParameter](resolver$p) {
  var pixmap: com.badlogic.gdx.graphics.Pixmap = null.asInstanceOf[com.badlogic.gdx.graphics.Pixmap]
  @java.lang.Override
  def loadAsync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.PixmapLoader.PixmapParameter): scala.Unit = {
    this.pixmap = null
    this.pixmap = new com.badlogic.gdx.graphics.Pixmap(file)
  }
  @java.lang.Override
  def loadSync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.PixmapLoader.PixmapParameter): com.badlogic.gdx.graphics.Pixmap = {
    var pixmap: com.badlogic.gdx.graphics.Pixmap = this.pixmap
    this.pixmap = null
    return pixmap
  }
  @java.lang.Override
  def getDependencies(fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.PixmapLoader.PixmapParameter): com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]] = {
    return null
  }
}
object PixmapLoader {
  class PixmapParameter extends com.badlogic.gdx.assets.AssetLoaderParameters[com.badlogic.gdx.graphics.Pixmap]
  object PixmapParameter {
    export com.badlogic.gdx.assets.AssetLoaderParameters.*
  }
}