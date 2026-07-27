package com.badlogic.gdx.maps

trait ImageResolver {
  def getImage(name: java.lang.String): com.badlogic.gdx.graphics.g2d.TextureRegion
}
object ImageResolver {
  class DirectImageResolver(images$p: com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.graphics.Texture]) extends ImageResolver {
    private var images: com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.graphics.Texture] = null.asInstanceOf[com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.graphics.Texture]]
    this.images = images$p
    def getImage(name: java.lang.String): com.badlogic.gdx.graphics.g2d.TextureRegion = {
      return new com.badlogic.gdx.graphics.g2d.TextureRegion(this.images.get(name))
    }
  }
  object DirectImageResolver {
    export ImageResolver.*
  }
  class AssetManagerImageResolver(assetManager$p: com.badlogic.gdx.assets.AssetManager) extends ImageResolver {
    private var assetManager: com.badlogic.gdx.assets.AssetManager = null.asInstanceOf[com.badlogic.gdx.assets.AssetManager]
    this.assetManager = assetManager$p
    def getImage(name: java.lang.String): com.badlogic.gdx.graphics.g2d.TextureRegion = {
      return new com.badlogic.gdx.graphics.g2d.TextureRegion(this.assetManager.get(name, classOf[com.badlogic.gdx.graphics.Texture]))
    }
  }
  object AssetManagerImageResolver {
    export ImageResolver.*
  }
  class TextureAtlasImageResolver(atlas$p: com.badlogic.gdx.graphics.g2d.TextureAtlas) extends ImageResolver {
    private var atlas: com.badlogic.gdx.graphics.g2d.TextureAtlas = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureAtlas]
    this.atlas = atlas$p
    def getImage(name: java.lang.String): com.badlogic.gdx.graphics.g2d.TextureRegion = {
      return this.atlas.findRegion(name)
    }
  }
  object TextureAtlasImageResolver {
    export ImageResolver.*
  }
}