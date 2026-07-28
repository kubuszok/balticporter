package com.badlogic.gdx.assets.loaders.resolvers

class InternalFileHandleResolver extends com.badlogic.gdx.assets.loaders.FileHandleResolver {
  @java.lang.Override
  def resolve(fileName: java.lang.String): com.badlogic.gdx.files.FileHandle = {
    return com.badlogic.gdx.Gdx.files.internal(fileName)
  }
}