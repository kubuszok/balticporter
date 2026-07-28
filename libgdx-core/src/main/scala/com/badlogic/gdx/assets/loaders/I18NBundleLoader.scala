package com.badlogic.gdx.assets.loaders

class I18NBundleLoader(resolver$p: com.badlogic.gdx.assets.loaders.FileHandleResolver) extends com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[com.badlogic.gdx.utils.I18NBundle, com.badlogic.gdx.assets.loaders.I18NBundleLoader.I18NBundleParameter](resolver$p) {
  var bundle: com.badlogic.gdx.utils.I18NBundle = null.asInstanceOf[com.badlogic.gdx.utils.I18NBundle]
  @java.lang.Override
  override def loadAsync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.I18NBundleLoader.I18NBundleParameter): scala.Unit = {
    this.bundle = null
    var locale: java.util.Locale = null.asInstanceOf[java.util.Locale]
    var encoding: java.lang.String = null.asInstanceOf[java.lang.String]
    if (parameter == null) {
      locale = java.util.Locale.getDefault()
      encoding = null
    } else {
      locale = if (parameter.locale == null) java.util.Locale.getDefault() else parameter.locale
      encoding = parameter.encoding
    }
    if (encoding == null) {
      this.bundle = com.badlogic.gdx.utils.I18NBundle.createBundle(file, locale)
    } else {
      this.bundle = com.badlogic.gdx.utils.I18NBundle.createBundle(file, locale, encoding)
    }
  }
  @java.lang.Override
  override def loadSync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.I18NBundleLoader.I18NBundleParameter): ?T = {
    var bundle: com.badlogic.gdx.utils.I18NBundle = this.bundle
    this.bundle = null
    return bundle
  }
  @java.lang.Override
  override def getDependencies(fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.I18NBundleLoader.I18NBundleParameter): com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]] = {
    return null
  }
}
object I18NBundleLoader {
  class I18NBundleParameter(locale$p: java.util.Locale, encoding$p: java.lang.String) extends com.badlogic.gdx.assets.AssetLoaderParameters[com.badlogic.gdx.utils.I18NBundle] {
    var locale: java.util.Locale = null.asInstanceOf[java.util.Locale]
    var encoding: java.lang.String = null.asInstanceOf[java.lang.String]
    def this() = {
      this(null, null)
    }
    def this(locale: java.util.Locale) = {
      this(locale, null)
    }
    this.locale = locale$p
    this.encoding = encoding$p
  }
  object I18NBundleParameter {
    export com.badlogic.gdx.assets.AssetLoaderParameters.*
  }
}