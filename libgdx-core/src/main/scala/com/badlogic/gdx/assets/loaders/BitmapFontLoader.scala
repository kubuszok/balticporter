package com.badlogic.gdx.assets.loaders

class BitmapFontLoader extends com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader[com.badlogic.gdx.graphics.g2d.BitmapFont, com.badlogic.gdx.assets.loaders.BitmapFontLoader.BitmapFontParameter] {
  var data: com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData]
  def this(resolver: com.badlogic.gdx.assets.loaders.FileHandleResolver) = {
    this()
  }
  def getDependencies(fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.BitmapFontLoader.BitmapFontParameter): com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]] = {
    val deps: com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]]
    if ((parameter != null) && (parameter.bitmapFontData != null)) {
      this.data = parameter.bitmapFontData
      return deps.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]]
    } else ()
    this.data = new com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData(file, (parameter != null) && parameter.flip)
    if ((parameter != null) && (parameter.atlasName != null)) {
      deps.add(new com.badlogic.gdx.assets.AssetDescriptor(parameter.atlasName, classOf[com.badlogic.gdx.graphics.g2d.TextureAtlas]))
    } else {
      { var i: scala.Int = 0; while (i < this.data.getImagePaths().length) { {
        val path: java.lang.String = this.data.getImagePath(i)
        val resolved: com.badlogic.gdx.files.FileHandle = this.resolve(path)
        val textureParams: com.badlogic.gdx.assets.loaders.TextureLoader.TextureParameter = new com.badlogic.gdx.assets.loaders.TextureLoader.TextureParameter()
        if (parameter != null) {
          textureParams.genMipMaps = parameter.genMipMaps
          textureParams.minFilter = parameter.minFilter
          textureParams.magFilter = parameter.magFilter
        } else ()
        val descriptor: com.badlogic.gdx.assets.AssetDescriptor[?] = new com.badlogic.gdx.assets.AssetDescriptor(resolved, classOf[com.badlogic.gdx.graphics.Texture], textureParams).asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[?]]
        deps.add(descriptor)
      }; i = i + 1 } }
    }
    return deps.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor[?]]]
  }
  def loadAsync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.BitmapFontLoader.BitmapFontParameter): scala.Unit = {
    ()
  }
  def loadSync(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: com.badlogic.gdx.assets.loaders.BitmapFontLoader.BitmapFontParameter): com.badlogic.gdx.graphics.g2d.BitmapFont = {
    if ((parameter != null) && (parameter.atlasName != null)) {
      val atlas: com.badlogic.gdx.graphics.g2d.TextureAtlas = manager.get(parameter.atlasName, classOf[com.badlogic.gdx.graphics.g2d.TextureAtlas])
      val name: java.lang.String = file.sibling(this.data.imagePaths(0)).nameWithoutExtension().toString()
      val region: com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion = atlas.findRegion(name)
      if (region == null) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException((("Could not find font region " + name) + " in atlas ") + parameter.atlasName)
      } else ()
      return new com.badlogic.gdx.graphics.g2d.BitmapFont(file, region)
    } else {
      val n: scala.Int = this.data.getImagePaths().length
      val regs: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.TextureRegion] = new com.badlogic.gdx.utils.Array(n).asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.TextureRegion]];
      { var i: scala.Int = 0; while (i < n) { {
        regs.add(new com.badlogic.gdx.graphics.g2d.TextureRegion(manager.get(this.data.getImagePath(i), classOf[com.badlogic.gdx.graphics.Texture])))
      }; i = i + 1 } }
      return new com.badlogic.gdx.graphics.g2d.BitmapFont(this.data, regs, true)
    }
  }
}
object BitmapFontLoader {
  class BitmapFontParameter extends com.badlogic.gdx.assets.AssetLoaderParameters[com.badlogic.gdx.graphics.g2d.BitmapFont] {
    var flip: scala.Boolean = false
    var genMipMaps: scala.Boolean = false
    var minFilter: com.badlogic.gdx.graphics.Texture.TextureFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest
    var magFilter: com.badlogic.gdx.graphics.Texture.TextureFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest
    var bitmapFontData: com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData = null
    var atlasName: java.lang.String = null
  }
  object BitmapFontParameter {
    export com.badlogic.gdx.assets.AssetLoaderParameters.*
  }
}