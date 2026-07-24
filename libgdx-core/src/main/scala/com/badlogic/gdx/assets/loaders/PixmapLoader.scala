package com.badlogic.gdx.assets.loaders

class PixmapLoader extends com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[com.badlogic.gdx.graphics.Pixmap, PixmapParameter] {
  var pixmap: com.badlogic.gdx.graphics.Pixmap = null.asInstanceOf[com.badlogic.gdx.graphics.Pixmap]
  def this(resolver: com.badlogic.gdx.assets.loaders.FileHandleResolver) = {
    this()
  }
  def loadAsync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: PixmapParameter): scala.Unit = {
    this.pixmap = null
    this.pixmap = new com.badlogic.gdx.graphics.Pixmap(file)
  }
  def loadSync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: PixmapParameter): com.badlogic.gdx.graphics.Pixmap = {
    var pixmap: com.badlogic.gdx.graphics.Pixmap = this.pixmap
    this.pixmap = null
    return pixmap
  }
  def getDependencies(fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: PixmapParameter): com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor] = {
    return null
  }
  class PixmapParameter extends com.badlogic.gdx.assets.AssetLoaderParameters[com.badlogic.gdx.graphics.Pixmap]
}