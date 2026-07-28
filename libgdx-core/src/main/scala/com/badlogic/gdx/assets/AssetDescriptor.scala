package com.badlogic.gdx.assets

class AssetDescriptor[T <: java.lang.Object] {
  var fileName: java.lang.String = null.asInstanceOf[java.lang.String]
  var `type`: java.lang.Class[T] = null.asInstanceOf[java.lang.Class[T]]
  var params: com.badlogic.gdx.assets.AssetLoaderParameters[T] = null.asInstanceOf[com.badlogic.gdx.assets.AssetLoaderParameters[T]]
  var file: com.badlogic.gdx.files.FileHandle = null.asInstanceOf[com.badlogic.gdx.files.FileHandle]
  def this(fileName: java.lang.String, assetType: java.lang.Class[T], params: com.badlogic.gdx.assets.AssetLoaderParameters[T]) = {
    this()
    this.fileName = fileName
    this.`type` = assetType
    this.params = params.asInstanceOf[com.badlogic.gdx.assets.AssetLoaderParameters[T]]
  }
  def this(fileName: java.lang.String, assetType: java.lang.Class[T]) = {
    this(fileName, assetType, null)
  }
  def this(file: com.badlogic.gdx.files.FileHandle, assetType: java.lang.Class[T], params: com.badlogic.gdx.assets.AssetLoaderParameters[T]) = {
    this()
    this.fileName = file.path()
    this.file = file
    this.`type` = assetType
    this.params = params.asInstanceOf[com.badlogic.gdx.assets.AssetLoaderParameters[T]]
  }
  def this(file: com.badlogic.gdx.files.FileHandle, assetType: java.lang.Class[T]) = {
    this(file, assetType, null)
  }
  @java.lang.Override
  def toString(): java.lang.String = {
    val sb: java.lang.StringBuilder = new java.lang.StringBuilder()
    sb.append(this.fileName)
    sb.append(", ")
    sb.append(this.`type`.getName())
    return sb.toString()
  }
}