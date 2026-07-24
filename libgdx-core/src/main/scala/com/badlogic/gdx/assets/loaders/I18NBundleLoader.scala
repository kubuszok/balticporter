package com.badlogic.gdx.assets.loaders

class I18NBundleLoader extends com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[com.badlogic.gdx.utils.I18NBundle, I18NBundleParameter] {
  var bundle: com.badlogic.gdx.utils.I18NBundle = null.asInstanceOf[com.badlogic.gdx.utils.I18NBundle]
  def this(resolver: com.badlogic.gdx.assets.loaders.FileHandleResolver) = {
    this()
  }
  def loadAsync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: I18NBundleParameter): scala.Unit = {
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
  def loadSync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: I18NBundleParameter): com.badlogic.gdx.utils.I18NBundle = {
    var bundle: com.badlogic.gdx.utils.I18NBundle = this.bundle
    this.bundle = null
    return bundle
  }
  def getDependencies(fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: I18NBundleParameter): com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor] = {
    return null
  }
  class I18NBundleParameter extends com.badlogic.gdx.assets.AssetLoaderParameters[com.badlogic.gdx.utils.I18NBundle] {
    var locale: java.util.Locale = null.asInstanceOf[java.util.Locale]
    var encoding: java.lang.String = null.asInstanceOf[java.lang.String]
    def this(locale: java.util.Locale, encoding: java.lang.String) = {
      this()
      this.locale = locale
      this.encoding = encoding
    }
    def this(locale: java.util.Locale) = {
      this(locale, null)
    }
  }
}