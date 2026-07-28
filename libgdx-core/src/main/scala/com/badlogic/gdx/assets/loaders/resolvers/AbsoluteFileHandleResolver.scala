package com.badlogic.gdx.assets.loaders.resolvers

class AbsoluteFileHandleResolver extends com.badlogic.gdx.assets.loaders.FileHandleResolver {
  @java.lang.Override
  override def resolve(fileName: java.lang.String): com.badlogic.gdx.files.FileHandle = {
    return com.badlogic.gdx.Gdx.files.absolute(fileName)
  }
}