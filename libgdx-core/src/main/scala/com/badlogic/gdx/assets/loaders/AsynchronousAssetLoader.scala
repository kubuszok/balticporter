package com.badlogic.gdx.assets.loaders

abstract class AsynchronousAssetLoader[T <: java.lang.Object, P <: com.badlogic.gdx.assets.AssetLoaderParameters[T]] extends com.badlogic.gdx.assets.loaders.AssetLoader[T, P] {
  def this(resolver: com.badlogic.gdx.assets.loaders.FileHandleResolver) = {
    this()
    this.resolver = resolver
  }
  def loadAsync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: P): scala.Unit
  def unloadAsync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: P): scala.Unit = {
    ()
  }
  def loadSync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: P): T
}