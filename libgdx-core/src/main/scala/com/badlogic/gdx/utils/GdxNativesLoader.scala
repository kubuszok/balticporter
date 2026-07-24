package com.badlogic.gdx.utils

object GdxNativesLoader {
  var disableNativesLoading: scala.Boolean = false
  private var nativesLoaded: scala.Boolean = false
  def load(): scala.Unit = {
    if (GdxNativesLoader.nativesLoaded) {
      return
    } else ()
    if (GdxNativesLoader.disableNativesLoading) {
      return
    } else ()
    new com.badlogic.gdx.utils.SharedLibraryLoader().load("gdx")
    GdxNativesLoader.nativesLoaded = true
  }
}