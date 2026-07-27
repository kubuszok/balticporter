package com.badlogic.gdx.assets.loaders.resolvers

class ResolutionFileResolver(baseResolver$p: com.badlogic.gdx.assets.loaders.FileHandleResolver, descriptors$p: scala.Array[com.badlogic.gdx.assets.loaders.resolvers.ResolutionFileResolver.Resolution]) extends com.badlogic.gdx.assets.loaders.FileHandleResolver {
  var baseResolver: com.badlogic.gdx.assets.loaders.FileHandleResolver = null.asInstanceOf[com.badlogic.gdx.assets.loaders.FileHandleResolver]
  var descriptors: scala.Array[com.badlogic.gdx.assets.loaders.resolvers.ResolutionFileResolver.Resolution] = null.asInstanceOf[scala.Array[com.badlogic.gdx.assets.loaders.resolvers.ResolutionFileResolver.Resolution]]
  if (descriptors$p.length == 0) {
    throw new java.lang.IllegalArgumentException("At least one Resolution needs to be supplied.")
  } else ()
  this.baseResolver = baseResolver$p
  this.descriptors = descriptors$p
  def resolve(fileName: java.lang.String): com.badlogic.gdx.files.FileHandle = {
    val bestResolution: com.badlogic.gdx.assets.loaders.resolvers.ResolutionFileResolver.Resolution = ResolutionFileResolver.choose(this.descriptors)
    val originalHandle: com.badlogic.gdx.files.FileHandle = new com.badlogic.gdx.files.FileHandle(fileName)
    var handle: com.badlogic.gdx.files.FileHandle = this.baseResolver.resolve(this.resolve(originalHandle, bestResolution.folder))
    if (!handle.exists()) {
      handle = this.baseResolver.resolve(fileName)
    } else ()
    return handle
  }
  def resolve(originalHandle: com.badlogic.gdx.files.FileHandle, suffix: java.lang.String): java.lang.String = {
    var parentString: java.lang.String = ""
    val parent: com.badlogic.gdx.files.FileHandle = originalHandle.parent()
    if ((parent != null) && (!parent.name().equals(""))) {
      parentString = java.lang.String.valueOf(parent) + "/"
    } else ()
    return ((parentString + suffix) + "/") + originalHandle.name()
  }
}
object ResolutionFileResolver {
  def choose(descriptors: scala.Array[com.badlogic.gdx.assets.loaders.resolvers.ResolutionFileResolver.Resolution]): com.badlogic.gdx.assets.loaders.resolvers.ResolutionFileResolver.Resolution = {
    val w: scala.Int = com.badlogic.gdx.Gdx.graphics.getBackBufferWidth()
    val h: scala.Int = com.badlogic.gdx.Gdx.graphics.getBackBufferHeight()
    var best: com.badlogic.gdx.assets.loaders.resolvers.ResolutionFileResolver.Resolution = descriptors(0)
    if (w < h) {
      { var i: scala.Int = 0; val n: scala.Int = descriptors.length; while (i < n) { {
        val other: com.badlogic.gdx.assets.loaders.resolvers.ResolutionFileResolver.Resolution = descriptors(i)
        if ((((w >= other.portraitWidth) && (other.portraitWidth >= best.portraitWidth)) && (h >= other.portraitHeight)) && (other.portraitHeight >= best.portraitHeight)) {
          best = descriptors(i)
        } else ()
      }; i = i + 1 } }
    } else {
      { var i: scala.Int = 0; val n: scala.Int = descriptors.length; while (i < n) { {
        val other: com.badlogic.gdx.assets.loaders.resolvers.ResolutionFileResolver.Resolution = descriptors(i)
        if ((((w >= other.portraitHeight) && (other.portraitHeight >= best.portraitHeight)) && (h >= other.portraitWidth)) && (other.portraitWidth >= best.portraitWidth)) {
          best = descriptors(i)
        } else ()
      }; i = i + 1 } }
    }
    return best
  }
  class Resolution(portraitWidth$p: scala.Int, portraitHeight$p: scala.Int, folder$p: java.lang.String) {
    var portraitWidth: scala.Int = 0
    var portraitHeight: scala.Int = 0
    var folder: java.lang.String = null.asInstanceOf[java.lang.String]
    this.portraitWidth = portraitWidth$p
    this.portraitHeight = portraitHeight$p
    this.folder = folder$p
  }
}