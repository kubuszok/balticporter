package com.badlogic.gdx.assets.loaders

abstract class SynchronousAssetLoader[T, P <: com.badlogic.gdx.assets.AssetLoaderParameters[T]] extends com.badlogic.gdx.assets.loaders.AssetLoader[T, P] {
  def this(resolver: com.badlogic.gdx.assets.loaders.FileHandleResolver) = {
    this()
  }
  def load(assetManager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: P): T
}