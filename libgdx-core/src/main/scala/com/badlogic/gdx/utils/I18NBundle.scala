package com.badlogic.gdx.utils

class I18NBundle {
  private var parent: I18NBundle = null.asInstanceOf[I18NBundle]
  private var locale: java.util.Locale = null.asInstanceOf[java.util.Locale]
  private var properties: com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.String] = null.asInstanceOf[com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.String]]
  private var formatter: com.badlogic.gdx.utils.TextFormatter = null.asInstanceOf[com.badlogic.gdx.utils.TextFormatter]
  protected def load(reader: java.io.Reader): scala.Unit = {
    this.properties = new com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.String]()
    com.badlogic.gdx.utils.PropertiesUtils.load(this.properties, reader)
  }
  def getLocale(): java.util.Locale = {
    return this.locale
  }
  private def setLocale(locale: java.util.Locale): scala.Unit = {
    this.locale = locale
    this.formatter = new com.badlogic.gdx.utils.TextFormatter(locale, !I18NBundle.simpleFormatter)
  }
  def get(key: java.lang.String): java.lang.String = {
    var result: java.lang.String = this.properties.get(key)
    if (result == null) {
      if (this.parent != null) {
        result = this.parent.get(key)
      } else ()
      if (result == null) {
        if (I18NBundle.exceptionOnMissingKey) {
          throw new java.util.MissingResourceException("Can't find bundle key " + key, this.getClass().getName(), key)
        } else {
          return ("???" + key) + "???"
        }
      } else ()
    } else ()
    return result
  }
  def keys(): scala.collection.mutable.Set[java.lang.String] = {
    val result: scala.collection.mutable.Set[java.lang.String] = new scala.collection.mutable.LinkedHashSet[java.lang.String]()
    val keys: com.badlogic.gdx.utils.ObjectMap#Keys[java.lang.String] = this.properties.keys()
    if (keys != null) {
      for (key <- keys) {
        result += key
      }
    } else ()
    return result
  }
  def format(key: java.lang.String, args: scala.Array[java.lang.Object]): java.lang.String = {
    return this.formatter.format(this.get(key), args)
  }
  def debug(placeholder: java.lang.String): scala.Unit = {
    val keys: com.badlogic.gdx.utils.ObjectMap#Keys[java.lang.String] = this.properties.keys()
    if (keys == null) {
      return
    } else ()
    for (s <- keys) {
      this.properties.put(s, placeholder)
    }
  }
}
object I18NBundle {
  private final val DEFAULT_ENCODING: java.lang.String = "UTF-8"
  private var simpleFormatter: scala.Boolean = false
  private var exceptionOnMissingKey: scala.Boolean = true
  def getSimpleFormatter(): scala.Boolean = {
    return I18NBundle.simpleFormatter
  }
  def setSimpleFormatter(enabled: scala.Boolean): scala.Unit = {
    I18NBundle.simpleFormatter = enabled
  }
  def getExceptionOnMissingKey(): scala.Boolean = {
    return I18NBundle.exceptionOnMissingKey
  }
  def setExceptionOnMissingKey(enabled: scala.Boolean): scala.Unit = {
    I18NBundle.exceptionOnMissingKey = enabled
  }
  def createBundle(baseFileHandle: com.badlogic.gdx.files.FileHandle): I18NBundle = {
    return I18NBundle.createBundleImpl(baseFileHandle, java.util.Locale.getDefault(), I18NBundle.DEFAULT_ENCODING)
  }
  def createBundle(baseFileHandle: com.badlogic.gdx.files.FileHandle, locale: java.util.Locale): I18NBundle = {
    return I18NBundle.createBundleImpl(baseFileHandle, locale, I18NBundle.DEFAULT_ENCODING)
  }
  def createBundle(baseFileHandle: com.badlogic.gdx.files.FileHandle, encoding: java.lang.String): I18NBundle = {
    return I18NBundle.createBundleImpl(baseFileHandle, java.util.Locale.getDefault(), encoding)
  }
  def createBundle(baseFileHandle: com.badlogic.gdx.files.FileHandle, locale: java.util.Locale, encoding: java.lang.String): I18NBundle = {
    return I18NBundle.createBundleImpl(baseFileHandle, locale, encoding)
  }
  private def createBundleImpl(baseFileHandle: com.badlogic.gdx.files.FileHandle, locale: java.util.Locale, encoding: java.lang.String): I18NBundle = {
    if (((baseFileHandle == null) || (locale == null)) || (encoding == null)) {
      throw new java.lang.NullPointerException()
    } else ()
    var bundle: I18NBundle = null
    var baseBundle: I18NBundle = null
    var targetLocale: java.util.Locale = locale
    while ({ {
      val candidateLocales: scala.collection.mutable.Buffer[java.util.Locale] = I18NBundle.getCandidateLocales(targetLocale)
      bundle = I18NBundle.loadBundleChain(baseFileHandle, encoding, candidateLocales, 0, baseBundle)
      if (bundle != null) {
        val bundleLocale: java.util.Locale = bundle.getLocale()
        val isBaseBundle: scala.Boolean = bundleLocale.equals(java.util.Locale.ROOT)
        if ((!isBaseBundle) || bundleLocale.equals(locale)) {
          /* break */ ()
        } else ()
        if ((candidateLocales.size == 1) && bundleLocale.equals(candidateLocales(0))) {
          /* break */ ()
        } else ()
        if (baseBundle == null) {
          baseBundle = bundle
        } else ()
      } else ()
      targetLocale = I18NBundle.getFallbackLocale(targetLocale)
    }; targetLocale != null }) ()
    if (bundle == null) {
      if (baseBundle == null) {
        throw new java.util.MissingResourceException((("Can't find bundle for base file handle " + baseFileHandle.path()) + ", locale ") + locale, (baseFileHandle + "_") + locale, "")
      } else ()
      bundle = baseBundle
    } else ()
    return bundle
  }
  private def getCandidateLocales(locale: java.util.Locale): scala.collection.mutable.Buffer[java.util.Locale] = {
    val language: java.lang.String = locale.getLanguage()
    val country: java.lang.String = locale.getCountry()
    val variant: java.lang.String = locale.getVariant()
    val locales: scala.collection.mutable.Buffer[java.util.Locale] = new scala.collection.mutable.ArrayBuffer[java.util.Locale](4)
    if (!variant.isEmpty()) {
      locales += locale
    } else ()
    if (!country.isEmpty()) {
      locales += (if (locales.isEmpty) locale else new java.util.Locale(language, country))
    } else ()
    if (!language.isEmpty()) {
      locales += (if (locales.isEmpty) locale else new java.util.Locale(language))
    } else ()
    locales += java.util.Locale.ROOT
    return locales
  }
  private def getFallbackLocale(locale: java.util.Locale): java.util.Locale = {
    val defaultLocale: java.util.Locale = java.util.Locale.getDefault()
    return if (locale.equals(defaultLocale)) null else defaultLocale
  }
  private def loadBundleChain(baseFileHandle: com.badlogic.gdx.files.FileHandle, encoding: java.lang.String, candidateLocales: scala.collection.mutable.Buffer[java.util.Locale], candidateIndex: scala.Int, baseBundle: I18NBundle): I18NBundle = {
    val targetLocale: java.util.Locale = candidateLocales(candidateIndex)
    var parent: I18NBundle = null
    if (candidateIndex != (candidateLocales.size - 1)) {
      parent = I18NBundle.loadBundleChain(baseFileHandle, encoding, candidateLocales, candidateIndex + 1, baseBundle)
    } else {
      if ((baseBundle != null) && targetLocale.equals(java.util.Locale.ROOT)) {
        return baseBundle
      } else ()
    }
    val bundle: I18NBundle = I18NBundle.loadBundle(baseFileHandle, encoding, targetLocale)
    if (bundle != null) {
      bundle.parent = parent
      return bundle
    } else ()
    return parent
  }
  private def loadBundle(baseFileHandle: com.badlogic.gdx.files.FileHandle, encoding: java.lang.String, targetLocale: java.util.Locale): I18NBundle = {
    var bundle: I18NBundle = null
    var reader: java.io.Reader = null
    try {
      val fileHandle: com.badlogic.gdx.files.FileHandle = I18NBundle.toFileHandle(baseFileHandle, targetLocale)
      if (I18NBundle.checkFileExistence(fileHandle)) {
        bundle = new I18NBundle()
        reader = fileHandle.reader(encoding)
        bundle.load(reader)
      } else ()
    } catch {
      case e: java.io.IOException => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException(e)
      }
    } finally {
      com.badlogic.gdx.utils.StreamUtils.closeQuietly(reader)
    }
    if (bundle != null) {
      bundle.setLocale(targetLocale)
    } else ()
    return bundle
  }
  private def checkFileExistence(fh: com.badlogic.gdx.files.FileHandle): scala.Boolean = {
    try {
      fh.read().close()
      return true
    } catch {
      case e: java.lang.Exception => {
        return false
      }
    }
  }
  private def toFileHandle(baseFileHandle: com.badlogic.gdx.files.FileHandle, locale: java.util.Locale): com.badlogic.gdx.files.FileHandle = {
    val sb: java.lang.StringBuilder = new java.lang.StringBuilder(baseFileHandle.name())
    if (!locale.equals(java.util.Locale.ROOT)) {
      val language: java.lang.String = locale.getLanguage()
      val country: java.lang.String = locale.getCountry()
      val variant: java.lang.String = locale.getVariant()
      if (!((language.isEmpty() && country.isEmpty()) && variant.isEmpty())) {
        sb.append('_')
        if (!variant.isEmpty()) {
          sb.append(language).append('_').append(country).append('_').append(variant)
        } else {
          if (!country.isEmpty()) {
            sb.append(language).append('_').append(country)
          } else {
            sb.append(language)
          }
        }
      } else ()
    } else ()
    return baseFileHandle.sibling(sb.append(".properties").toString())
  }
}