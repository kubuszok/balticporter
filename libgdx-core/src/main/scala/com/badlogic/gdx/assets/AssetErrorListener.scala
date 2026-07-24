package com.badlogic.gdx.assets

trait AssetErrorListener {
  def error(asset: com.badlogic.gdx.assets.AssetDescriptor, throwable: java.lang.Throwable): scala.Unit
}