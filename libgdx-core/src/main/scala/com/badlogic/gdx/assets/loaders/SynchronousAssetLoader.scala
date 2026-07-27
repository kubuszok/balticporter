package com.badlogic.gdx.assets.loaders

abstract class SynchronousAssetLoader[T, P <: com.badlogic.gdx.assets.AssetLoaderParameters[T]](resolver$p: com.badlogic.gdx.assets.loaders.FileHandleResolver) extends com.badlogic.gdx.assets.loaders.AssetLoader[T, P](resolver$p) {
  def load(assetManager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: P): T
}