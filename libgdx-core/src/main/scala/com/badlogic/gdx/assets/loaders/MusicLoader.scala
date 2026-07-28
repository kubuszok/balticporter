package com.badlogic.gdx.assets.loaders

class MusicLoader(resolver$p: com.badlogic.gdx.assets.loaders.FileHandleResolver) extends com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[com.badlogic.gdx.audio.Music, com.badlogic.gdx.assets.loaders.MusicLoader.MusicParameter](resolver$p) {
  private var music: com.badlogic.gdx.audio.Music = null.asInstanceOf[com.badlogic.gdx.audio.Music]
  def getLoadedMusic(): com.badlogic.gdx.audio.Music = {
    return this.music
  }
  @java.lang.Override
  override def loadAsync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.MusicLoader.MusicParameter): scala.Unit = {
    this.music = com.badlogic.gdx.Gdx.audio.newMusic(file)
  }
  @java.lang.Override
  override def loadSync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.MusicLoader.MusicParameter): com.badlogic.gdx.audio.Music = {
    var music: com.badlogic.gdx.audio.Music = this.music
    this.music = null
    return music
  }
  @java.lang.Override
  override def getDependencies(fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.MusicLoader.MusicParameter): com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[com.badlogic.gdx.audio.Music]] = {
    return null
  }
}
object MusicLoader {
  class MusicParameter extends com.badlogic.gdx.assets.AssetLoaderParameters[com.badlogic.gdx.audio.Music]
  object MusicParameter {
    export com.badlogic.gdx.assets.AssetLoaderParameters.*
  }
}