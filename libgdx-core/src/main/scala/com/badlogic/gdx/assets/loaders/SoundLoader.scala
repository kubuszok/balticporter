package com.badlogic.gdx.assets.loaders

class SoundLoader extends com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[com.badlogic.gdx.audio.Sound, com.badlogic.gdx.assets.loaders.SoundLoader.SoundParameter] {
  private var sound: com.badlogic.gdx.audio.Sound = null.asInstanceOf[com.badlogic.gdx.audio.Sound]
  def this(resolver: com.badlogic.gdx.assets.loaders.FileHandleResolver) = {
    this()
  }
  def getLoadedSound(): com.badlogic.gdx.audio.Sound = {
    return this.sound
  }
  def loadAsync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.SoundLoader.SoundParameter): scala.Unit = {
    this.sound = com.badlogic.gdx.Gdx.audio.newSound(file)
  }
  def loadSync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.SoundLoader.SoundParameter): com.badlogic.gdx.audio.Sound = {
    var sound: com.badlogic.gdx.audio.Sound = this.sound
    this.sound = null
    return sound
  }
  def getDependencies(fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.SoundLoader.SoundParameter): com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]] = {
    return null
  }
}
object SoundLoader {
  class SoundParameter extends com.badlogic.gdx.assets.AssetLoaderParameters[com.badlogic.gdx.audio.Sound]
}