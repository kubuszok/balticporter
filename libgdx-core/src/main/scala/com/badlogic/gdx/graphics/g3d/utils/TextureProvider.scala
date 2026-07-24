package com.badlogic.gdx.graphics.g3d.utils

trait TextureProvider {
  def load(fileName: java.lang.String): com.badlogic.gdx.graphics.Texture
}
object TextureProvider {
  class FileTextureProvider extends TextureProvider {
    private var minFilter: com.badlogic.gdx.graphics.Texture.TextureFilter = null.asInstanceOf[com.badlogic.gdx.graphics.Texture.TextureFilter]
    private var magFilter: com.badlogic.gdx.graphics.Texture.TextureFilter = null.asInstanceOf[com.badlogic.gdx.graphics.Texture.TextureFilter]
    private var uWrap: com.badlogic.gdx.graphics.Texture.TextureWrap = null.asInstanceOf[com.badlogic.gdx.graphics.Texture.TextureWrap]
    private var vWrap: com.badlogic.gdx.graphics.Texture.TextureWrap = null.asInstanceOf[com.badlogic.gdx.graphics.Texture.TextureWrap]
    private var useMipMaps: scala.Boolean = false
    def this(minFilter: com.badlogic.gdx.graphics.Texture.TextureFilter, magFilter: com.badlogic.gdx.graphics.Texture.TextureFilter, uWrap: com.badlogic.gdx.graphics.Texture.TextureWrap, vWrap: com.badlogic.gdx.graphics.Texture.TextureWrap, useMipMaps: scala.Boolean) = {
      this()
      this.minFilter = minFilter
      this.magFilter = magFilter
      this.uWrap = uWrap
      this.vWrap = vWrap
      this.useMipMaps = useMipMaps
    }
    this.minFilter = {
      this.magFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear
      this.magFilter
    }
    this.uWrap = {
      this.vWrap = com.badlogic.gdx.graphics.Texture.TextureWrap.Repeat
      this.vWrap
    }
    this.useMipMaps = false
    def load(fileName: java.lang.String): com.badlogic.gdx.graphics.Texture = {
      val result: com.badlogic.gdx.graphics.Texture = new com.badlogic.gdx.graphics.Texture(com.badlogic.gdx.Gdx.files.internal(fileName), this.useMipMaps)
      result.setFilter(this.minFilter, this.magFilter)
      result.setWrap(this.uWrap, this.vWrap)
      return result
    }
  }
  class AssetTextureProvider extends TextureProvider {
    var assetManager: com.badlogic.gdx.assets.AssetManager = null.asInstanceOf[com.badlogic.gdx.assets.AssetManager]
    def this(assetManager: com.badlogic.gdx.assets.AssetManager) = {
      this()
      this.assetManager = assetManager
    }
    def load(fileName: java.lang.String): com.badlogic.gdx.graphics.Texture = {
      return this.assetManager.get(fileName, classOf[com.badlogic.gdx.graphics.Texture])
    }
  }
}