package com.badlogic.gdx.graphics

class Texture extends com.badlogic.gdx.graphics.GLTexture(0, 0) {
  var data: com.badlogic.gdx.graphics.TextureData = null.asInstanceOf[com.badlogic.gdx.graphics.TextureData]
  def this(glTarget: scala.Int, glHandle: scala.Int, data: com.badlogic.gdx.graphics.TextureData) = {
    this()
    this.glTarget = glTarget
    this.glHandle = glHandle
    this.load(data)
    if (data.isManaged()) {
      Texture.addManagedTexture(com.badlogic.gdx.Gdx.app, this)
    } else ()
  }
  def this(data: com.badlogic.gdx.graphics.TextureData) = {
    this(com.badlogic.gdx.graphics.GL20.GL_TEXTURE_2D, com.badlogic.gdx.Gdx.gl.glGenTexture(), data)
  }
  def this(file: com.badlogic.gdx.files.FileHandle, format: com.badlogic.gdx.graphics.Pixmap.Format, useMipMaps: scala.Boolean) = {
    this(com.badlogic.gdx.graphics.TextureData.Factory.loadFromFile(file, format, useMipMaps))
  }
  def this(file: com.badlogic.gdx.files.FileHandle) = {
    this(file, null, false)
  }
  def this(internalPath: java.lang.String) = {
    this(com.badlogic.gdx.Gdx.files.internal(internalPath))
  }
  def this(file: com.badlogic.gdx.files.FileHandle, useMipMaps: scala.Boolean) = {
    this(file, null, useMipMaps)
  }
  def this(pixmap: com.badlogic.gdx.graphics.Pixmap) = {
    this(new com.badlogic.gdx.graphics.glutils.PixmapTextureData(pixmap, null, false, false))
  }
  def this(pixmap: com.badlogic.gdx.graphics.Pixmap, useMipMaps: scala.Boolean) = {
    this(new com.badlogic.gdx.graphics.glutils.PixmapTextureData(pixmap, null, useMipMaps, false))
  }
  def this(pixmap: com.badlogic.gdx.graphics.Pixmap, format: com.badlogic.gdx.graphics.Pixmap.Format, useMipMaps: scala.Boolean) = {
    this(new com.badlogic.gdx.graphics.glutils.PixmapTextureData(pixmap, format, useMipMaps, false))
  }
  def this(width: scala.Int, height: scala.Int, format: com.badlogic.gdx.graphics.Pixmap.Format) = {
    this(new com.badlogic.gdx.graphics.glutils.PixmapTextureData(new com.badlogic.gdx.graphics.Pixmap(width, height, format), null, false, true))
  }
  def load(data: com.badlogic.gdx.graphics.TextureData): scala.Unit = {
    if ((this.data != null) && (data.isManaged() != this.data.isManaged())) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("New data must have the same managed status as the old data")
    } else ()
    this.data = data
    if (!data.isPrepared()) {
      data.prepare()
    } else ()
    this.bind()
    com.badlogic.gdx.graphics.GLTexture.uploadImageData(com.badlogic.gdx.graphics.GL20.GL_TEXTURE_2D, data)
    this.unsafeSetFilter(minFilter, magFilter, true)
    this.unsafeSetWrap(uWrap, vWrap, true)
    this.unsafeSetAnisotropicFilter(anisotropicFilterLevel, true)
    com.badlogic.gdx.Gdx.gl.glBindTexture(glTarget, 0)
  }
  @java.lang.Override
  def reload(): scala.Unit = {
    if (!this.isManaged()) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Tried to reload unmanaged Texture")
    } else ()
    glHandle = com.badlogic.gdx.Gdx.gl.glGenTexture()
    this.load(this.data)
  }
  def draw(pixmap: com.badlogic.gdx.graphics.Pixmap, x: scala.Int, y: scala.Int): scala.Unit = {
    if (this.data.isManaged()) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("can't draw to a managed texture")
    } else ()
    this.bind()
    com.badlogic.gdx.Gdx.gl.glTexSubImage2D(glTarget, 0, x, y, pixmap.getWidth(), pixmap.getHeight(), pixmap.getGLFormat(), pixmap.getGLType(), pixmap.getPixels())
  }
  @java.lang.Override
  def getWidth(): scala.Int = {
    return this.data.getWidth()
  }
  @java.lang.Override
  def getHeight(): scala.Int = {
    return this.data.getHeight()
  }
  @java.lang.Override
  def getDepth(): scala.Int = {
    return 0
  }
  def getTextureData(): com.badlogic.gdx.graphics.TextureData = {
    return this.data
  }
  def isManaged(): scala.Boolean = {
    return this.data.isManaged()
  }
  def dispose(): scala.Unit = {
    if (glHandle == 0) {
      return
    } else ()
    this.delete()
    if (this.data.isManaged()) {
      if (Texture.managedTextures.getOrElse(com.badlogic.gdx.Gdx.app, null.asInstanceOf[com.badlogic.gdx.utils.Array[Texture]]) != null) {
        Texture.managedTextures.getOrElse(com.badlogic.gdx.Gdx.app, null.asInstanceOf[com.badlogic.gdx.utils.Array[Texture]]).removeValue(this, true)
      } else ()
    } else ()
  }
  def toString(): java.lang.String = {
    if (this.data.isInstanceOf[com.badlogic.gdx.graphics.glutils.FileTextureData]) {
      return this.data.toString()
    } else ()
    return super.toString()
  }
}
object Texture {
  export com.badlogic.gdx.graphics.GLTexture.{TextureFilter => _, TextureWrap => _, addManagedTexture => _, assetManager => _, clearAllTextures => _, getManagedStatus => _, getNumManagedTextures => _, invalidateAllTextures => _, managedTextures => _, setAssetManager => _, *}
  private var assetManager: com.badlogic.gdx.assets.AssetManager = null.asInstanceOf[com.badlogic.gdx.assets.AssetManager]
  final val managedTextures: scala.collection.mutable.Map[com.badlogic.gdx.Application, com.badlogic.gdx.utils.Array[Texture]] = new scala.collection.mutable.HashMap[com.badlogic.gdx.Application, com.badlogic.gdx.utils.Array[Texture]]()
  private def addManagedTexture(app: com.badlogic.gdx.Application, texture: Texture): scala.Unit = {
    var managedTextureArray: com.badlogic.gdx.utils.Array[Texture] = Texture.managedTextures.getOrElse(app, null.asInstanceOf[com.badlogic.gdx.utils.Array[Texture]])
    if (managedTextureArray == null) {
      managedTextureArray = new com.badlogic.gdx.utils.Array[Texture]()
    } else ()
    managedTextureArray.add(texture)
    Texture.managedTextures.put(app, managedTextureArray).getOrElse(null.asInstanceOf[com.badlogic.gdx.utils.Array[Texture]])
  }
  def clearAllTextures(app: com.badlogic.gdx.Application): scala.Unit = {
    Texture.managedTextures.remove(app).getOrElse(null.asInstanceOf[com.badlogic.gdx.utils.Array[Texture]])
  }
  def invalidateAllTextures(app: com.badlogic.gdx.Application): scala.Unit = {
    val managedTextureArray: com.badlogic.gdx.utils.Array[Texture] = Texture.managedTextures.getOrElse(app, null.asInstanceOf[com.badlogic.gdx.utils.Array[Texture]])
    if (managedTextureArray == null) {
      return
    } else ()
    if (Texture.assetManager == null) {
      { var i: scala.Int = 0; while (i < managedTextureArray.size) { {
        var texture: Texture = managedTextureArray.get(i)
        texture.reload()
      }; i = i + 1 } }
    } else {
      Texture.assetManager.finishLoading()
      val textures: com.badlogic.gdx.utils.Array[Texture] = new com.badlogic.gdx.utils.Array[Texture](managedTextureArray)
      for (texture <- textures) {
        val fileName: java.lang.String = Texture.assetManager.getAssetFileName(texture)
        if (fileName == null) {
          texture.reload()
        } else {
          val refCount: scala.Int = Texture.assetManager.getReferenceCount(fileName)
          Texture.assetManager.setReferenceCount(fileName, 0)
          texture.glHandle = 0
          val params: com.badlogic.gdx.assets.loaders.TextureLoader.TextureParameter = new com.badlogic.gdx.assets.loaders.TextureLoader.TextureParameter()
          params.textureData = texture.getTextureData()
          params.minFilter = texture.getMinFilter()
          params.magFilter = texture.getMagFilter()
          params.wrapU = texture.getUWrap()
          params.wrapV = texture.getVWrap()
          params.genMipMaps = texture.data.useMipMaps()
          params.texture = texture
          params.loadedCallback = new com.badlogic.gdx.assets.AssetLoaderParameters.LoadedCallback() {
            @java.lang.Override
            override def finishedLoading(assetManager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, `type`: java.lang.Class[?]): scala.Unit = {
              assetManager.setReferenceCount(fileName, refCount)
            }
          }
          Texture.assetManager.unload(fileName)
          texture.glHandle = com.badlogic.gdx.Gdx.gl.glGenTexture()
          Texture.assetManager.load(fileName, classOf[Texture], params)
        }
      }
      managedTextureArray.clear()
      managedTextureArray.addAll(textures.asInstanceOf[com.badlogic.gdx.utils.Array[? <: Texture]])
    }
  }
  def setAssetManager(manager: com.badlogic.gdx.assets.AssetManager): scala.Unit = {
    Texture.assetManager = manager
  }
  def getManagedStatus(): java.lang.String = {
    val builder: java.lang.StringBuilder = new java.lang.StringBuilder()
    builder.append("Managed textures/app: { ")
    for (app <- Texture.managedTextures.keySet) {
      builder.append(Texture.managedTextures.getOrElse(app, null.asInstanceOf[com.badlogic.gdx.utils.Array[Texture]]).size)
      builder.append(" ")
    }
    builder.append("}")
    return builder.toString()
  }
  def getNumManagedTextures(): scala.Int = {
    return Texture.managedTextures.getOrElse(com.badlogic.gdx.Gdx.app, null.asInstanceOf[com.badlogic.gdx.utils.Array[Texture]]).size
  }
  sealed abstract class TextureFilter(var glEnum: scala.Int) {
    def isMipMap(): scala.Boolean = {
      return (this.glEnum != com.badlogic.gdx.graphics.GL20.GL_NEAREST) && (this.glEnum != com.badlogic.gdx.graphics.GL20.GL_LINEAR)
    }
    def getGLEnum(): scala.Int = {
      return this.glEnum
    }
    def name(): java.lang.String = this.toString()
  }
  object TextureFilter {
    case object Nearest extends TextureFilter(com.badlogic.gdx.graphics.GL20.GL_NEAREST)
    case object Linear extends TextureFilter(com.badlogic.gdx.graphics.GL20.GL_LINEAR)
    case object MipMap extends TextureFilter(com.badlogic.gdx.graphics.GL20.GL_LINEAR_MIPMAP_LINEAR)
    case object MipMapNearestNearest extends TextureFilter(com.badlogic.gdx.graphics.GL20.GL_NEAREST_MIPMAP_NEAREST)
    case object MipMapLinearNearest extends TextureFilter(com.badlogic.gdx.graphics.GL20.GL_LINEAR_MIPMAP_NEAREST)
    case object MipMapNearestLinear extends TextureFilter(com.badlogic.gdx.graphics.GL20.GL_NEAREST_MIPMAP_LINEAR)
    case object MipMapLinearLinear extends TextureFilter(com.badlogic.gdx.graphics.GL20.GL_LINEAR_MIPMAP_LINEAR)
    def values(): scala.Array[TextureFilter] = scala.Array(Nearest, Linear, MipMap, MipMapNearestNearest, MipMapLinearNearest, MipMapNearestLinear, MipMapLinearLinear)
    def valueOf(name: java.lang.String): TextureFilter = name match {
      case "Nearest" => Nearest
      case "Linear" => Linear
      case "MipMap" => MipMap
      case "MipMapNearestNearest" => MipMapNearestNearest
      case "MipMapLinearNearest" => MipMapLinearNearest
      case "MipMapNearestLinear" => MipMapNearestLinear
      case "MipMapLinearLinear" => MipMapLinearLinear
      case _ => throw new java.lang.IllegalArgumentException(name)
    }
  }
  sealed abstract class TextureWrap(var glEnum: scala.Int) {
    def getGLEnum(): scala.Int = {
      return this.glEnum
    }
    def name(): java.lang.String = this.toString()
  }
  object TextureWrap {
    case object MirroredRepeat extends TextureWrap(com.badlogic.gdx.graphics.GL20.GL_MIRRORED_REPEAT)
    case object ClampToEdge extends TextureWrap(com.badlogic.gdx.graphics.GL20.GL_CLAMP_TO_EDGE)
    case object Repeat extends TextureWrap(com.badlogic.gdx.graphics.GL20.GL_REPEAT)
    def values(): scala.Array[TextureWrap] = scala.Array(MirroredRepeat, ClampToEdge, Repeat)
    def valueOf(name: java.lang.String): TextureWrap = name match {
      case "MirroredRepeat" => MirroredRepeat
      case "ClampToEdge" => ClampToEdge
      case "Repeat" => Repeat
      case _ => throw new java.lang.IllegalArgumentException(name)
    }
  }
}