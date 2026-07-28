package com.badlogic.gdx.assets

class AssetLoaderParameters[T <: java.lang.Object] {
  var loadedCallback: com.badlogic.gdx.assets.AssetLoaderParameters.LoadedCallback = null.asInstanceOf[com.badlogic.gdx.assets.AssetLoaderParameters.LoadedCallback]
}
object AssetLoaderParameters {
  trait LoadedCallback {
    def finishedLoading(assetManager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, `type`: java.lang.Class[?]): scala.Unit
  }
}