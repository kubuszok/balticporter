package com.badlogic.gdx.assets.loaders

trait FileHandleResolver {
  def resolve(fileName: java.lang.String): com.badlogic.gdx.files.FileHandle
}