package com.badlogic.gdx.maps

trait ImageResolver {
  def getImage(name: java.lang.String): com.badlogic.gdx.graphics.g2d.TextureRegion
  class DirectImageResolver extends ImageResolver {
    private var images: com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.graphics.Texture] = null.asInstanceOf[com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.graphics.Texture]]
    def this(images: com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.graphics.Texture]) = {
      this()
      this.images = images
    }
    def getImage(name: java.lang.String): com.badlogic.gdx.graphics.g2d.TextureRegion = {
      return new com.badlogic.gdx.graphics.g2d.TextureRegion(this.images.get(name))
    }
  }
  class AssetManagerImageResolver extends ImageResolver {
    private var assetManager: com.badlogic.gdx.assets.AssetManager = null.asInstanceOf[com.badlogic.gdx.assets.AssetManager]
    def this(assetManager: com.badlogic.gdx.assets.AssetManager) = {
      this()
      this.assetManager = assetManager
    }
    def getImage(name: java.lang.String): com.badlogic.gdx.graphics.g2d.TextureRegion = {
      return new com.badlogic.gdx.graphics.g2d.TextureRegion(this.assetManager.get(name, classOf[java.lang.Class]))
    }
  }
  class TextureAtlasImageResolver extends ImageResolver {
    private var atlas: com.badlogic.gdx.graphics.g2d.TextureAtlas = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureAtlas]
    def this(atlas: com.badlogic.gdx.graphics.g2d.TextureAtlas) = {
      this()
      this.atlas = atlas
    }
    def getImage(name: java.lang.String): com.badlogic.gdx.graphics.g2d.TextureRegion = {
      return this.atlas.findRegion(name)
    }
  }
}