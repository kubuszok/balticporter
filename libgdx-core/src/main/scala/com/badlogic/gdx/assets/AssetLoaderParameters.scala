package com.badlogic.gdx.assets

class AssetLoaderParameters[T] {
  var loadedCallback: LoadedCallback = null.asInstanceOf[LoadedCallback]
  trait LoadedCallback {
    def finishedLoading(assetManager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, `type`: java.lang.Class): scala.Unit
  }
}