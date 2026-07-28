package com.badlogic.gdx.assets.loaders.resolvers

class PrefixFileHandleResolver(baseResolver$p: com.badlogic.gdx.assets.loaders.FileHandleResolver, prefix$p: java.lang.String) extends com.badlogic.gdx.assets.loaders.FileHandleResolver {
  private var prefix: java.lang.String = null.asInstanceOf[java.lang.String]
  private var baseResolver: com.badlogic.gdx.assets.loaders.FileHandleResolver = null.asInstanceOf[com.badlogic.gdx.assets.loaders.FileHandleResolver]
  this.baseResolver = baseResolver$p
  this.prefix = prefix$p
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
  @java.lang.Override
  override def resolve(fileName: java.lang.String): com.badlogic.gdx.files.FileHandle = {
    return this.baseResolver.resolve(this.prefix + fileName)
  }
}