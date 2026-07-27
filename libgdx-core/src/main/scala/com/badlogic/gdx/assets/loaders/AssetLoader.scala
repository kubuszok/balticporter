package com.badlogic.gdx.assets.loaders

abstract class AssetLoader[T, P <: com.badlogic.gdx.assets.AssetLoaderParameters[T]] {
  var resolver: com.badlogic.gdx.assets.loaders.FileHandleResolver = null.asInstanceOf[com.badlogic.gdx.assets.loaders.FileHandleResolver]
  def this(resolver: com.badlogic.gdx.assets.loaders.FileHandleResolver) = {
    this()
    this.resolver = resolver
  }
  def resolve(fileName: java.lang.String): com.badlogic.gdx.files.FileHandle = {
    return this.resolver.resolve(fileName)
  }
  def getDependencies(fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: P): com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[T]]
}