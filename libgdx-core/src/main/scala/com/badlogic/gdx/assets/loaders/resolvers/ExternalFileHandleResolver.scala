package com.badlogic.gdx.assets.loaders.resolvers

class ExternalFileHandleResolver extends com.badlogic.gdx.assets.loaders.FileHandleResolver {
  def resolve(fileName: java.lang.String): com.badlogic.gdx.files.FileHandle = {
    return com.badlogic.gdx.Gdx.files.external(fileName)
  }
}