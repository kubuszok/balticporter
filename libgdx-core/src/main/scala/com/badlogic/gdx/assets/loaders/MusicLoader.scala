package com.badlogic.gdx.assets.loaders

class MusicLoader extends com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[com.badlogic.gdx.audio.Music, com.badlogic.gdx.assets.loaders.MusicLoader.MusicParameter] {
  private var music: com.badlogic.gdx.audio.Music = null.asInstanceOf[com.badlogic.gdx.audio.Music]
  def this(resolver: com.badlogic.gdx.assets.loaders.FileHandleResolver) = {
    this()
  }
  def getLoadedMusic(): com.badlogic.gdx.audio.Music = {
    return this.music
  }
  def loadAsync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.MusicLoader.MusicParameter): scala.Unit = {
    this.music = com.badlogic.gdx.Gdx.audio.newMusic(file)
  }
  def loadSync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.MusicLoader.MusicParameter): com.badlogic.gdx.audio.Music = {
    var music: com.badlogic.gdx.audio.Music = this.music
    this.music = null
    return music
  }
  def getDependencies(fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.MusicLoader.MusicParameter): com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]] = {
    return null
  }
}
object MusicLoader {
  class MusicParameter extends com.badlogic.gdx.assets.AssetLoaderParameters[com.badlogic.gdx.audio.Music]
  object MusicParameter {
    export com.badlogic.gdx.assets.AssetLoaderParameters.*
  }
}