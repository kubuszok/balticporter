package com.badlogic.gdx.assets.loaders.resolvers

class PrefixFileHandleResolver extends com.badlogic.gdx.assets.loaders.FileHandleResolver {
  private var prefix: java.lang.String = null.asInstanceOf[java.lang.String]
  private var baseResolver: com.badlogic.gdx.assets.loaders.FileHandleResolver = null.asInstanceOf[com.badlogic.gdx.assets.loaders.FileHandleResolver]
  def this(baseResolver: com.badlogic.gdx.assets.loaders.FileHandleResolver, prefix: java.lang.String) = {
    this()
    this.baseResolver = baseResolver
    this.prefix = prefix
  }
  def setBaseResolver(baseResolver: com.badlogic.gdx.assets.loaders.FileHandleResolver): scala.Unit = {
    this.baseResolver = baseResolver
  }
  def getBaseResolver(): com.badlogic.gdx.assets.loaders.FileHandleResolver = {
    return this.baseResolver
  }
  def setPrefix(prefix: java.lang.String): scala.Unit = {
    this.prefix = prefix
  }
  def getPrefix(): java.lang.String = {
    return this.prefix
  }
  def resolve(fileName: java.lang.String): com.badlogic.gdx.files.FileHandle = {
    return this.baseResolver.resolve(this.prefix + fileName)
  }
}