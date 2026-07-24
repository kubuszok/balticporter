package com.badlogic.gdx.graphics

class Cubemap extends com.badlogic.gdx.graphics.GLTexture {
  var data: com.badlogic.gdx.graphics.CubemapData = null.asInstanceOf[com.badlogic.gdx.graphics.CubemapData]
  def this(data: com.badlogic.gdx.graphics.CubemapData) = {
    this()
    this.data = data
    this.load(data)
    if (data.isManaged()) {
      Cubemap.addManagedCubemap(com.badlogic.gdx.Gdx.app, this)
    } else ()
  }
  def this(positiveX: com.badlogic.gdx.graphics.TextureData, negativeX: com.badlogic.gdx.graphics.TextureData, positiveY: com.badlogic.gdx.graphics.TextureData, negativeY: com.badlogic.gdx.graphics.TextureData, positiveZ: com.badlogic.gdx.graphics.TextureData, negativeZ: com.badlogic.gdx.graphics.TextureData) = {
    this(new com.badlogic.gdx.graphics.glutils.FacedCubemapData(positiveX, negativeX, positiveY, negativeY, positiveZ, negativeZ))
  }
  def this(positiveX: com.badlogic.gdx.files.FileHandle, negativeX: com.badlogic.gdx.files.FileHandle, positiveY: com.badlogic.gdx.files.FileHandle, negativeY: com.badlogic.gdx.files.FileHandle, positiveZ: com.badlogic.gdx.files.FileHandle, negativeZ: com.badlogic.gdx.files.FileHandle, useMipMaps: scala.Boolean) = {
    this(com.badlogic.gdx.graphics.TextureData.Factory.loadFromFile(positiveX, useMipMaps), com.badlogic.gdx.graphics.TextureData.Factory.loadFromFile(negativeX, useMipMaps), com.badlogic.gdx.graphics.TextureData.Factory.loadFromFile(positiveY, useMipMaps), com.badlogic.gdx.graphics.TextureData.Factory.loadFromFile(negativeY, useMipMaps), com.badlogic.gdx.graphics.TextureData.Factory.loadFromFile(positiveZ, useMipMaps), com.badlogic.gdx.graphics.TextureData.Factory.loadFromFile(negativeZ, useMipMaps))
  }
  def this(positiveX: com.badlogic.gdx.files.FileHandle, negativeX: com.badlogic.gdx.files.FileHandle, positiveY: com.badlogic.gdx.files.FileHandle, negativeY: com.badlogic.gdx.files.FileHandle, positiveZ: com.badlogic.gdx.files.FileHandle, negativeZ: com.badlogic.gdx.files.FileHandle) = {
    this(positiveX, negativeX, positiveY, negativeY, positiveZ, negativeZ, false)
  }
  def this(positiveX: com.badlogic.gdx.graphics.Pixmap, negativeX: com.badlogic.gdx.graphics.Pixmap, positiveY: com.badlogic.gdx.graphics.Pixmap, negativeY: com.badlogic.gdx.graphics.Pixmap, positiveZ: com.badlogic.gdx.graphics.Pixmap, negativeZ: com.badlogic.gdx.graphics.Pixmap, useMipMaps: scala.Boolean) = {
    this(if (positiveX == null) null.asInstanceOf[com.badlogic.gdx.graphics.TextureData] else new com.badlogic.gdx.graphics.glutils.PixmapTextureData(positiveX, null, useMipMaps, false), if (negativeX == null) null.asInstanceOf[com.badlogic.gdx.graphics.TextureData] else new com.badlogic.gdx.graphics.glutils.PixmapTextureData(negativeX, null, useMipMaps, false), if (positiveY == null) null.asInstanceOf[com.badlogic.gdx.graphics.TextureData] else new com.badlogic.gdx.graphics.glutils.PixmapTextureData(positiveY, null, useMipMaps, false), if (negativeY == null) null.asInstanceOf[com.badlogic.gdx.graphics.TextureData] else new com.badlogic.gdx.graphics.glutils.PixmapTextureData(negativeY, null, useMipMaps, false), if (positiveZ == null) null.asInstanceOf[com.badlogic.gdx.graphics.TextureData] else new com.badlogic.gdx.graphics.glutils.PixmapTextureData(positiveZ, null, useMipMaps, false), if (negativeZ == null) null.asInstanceOf[com.badlogic.gdx.graphics.TextureData] else new com.badlogic.gdx.graphics.glutils.PixmapTextureData(negativeZ, null, useMipMaps, false))
  }
  def this(positiveX: com.badlogic.gdx.graphics.Pixmap, negativeX: com.badlogic.gdx.graphics.Pixmap, positiveY: com.badlogic.gdx.graphics.Pixmap, negativeY: com.badlogic.gdx.graphics.Pixmap, positiveZ: com.badlogic.gdx.graphics.Pixmap, negativeZ: com.badlogic.gdx.graphics.Pixmap) = {
    this(positiveX, negativeX, positiveY, negativeY, positiveZ, negativeZ, false)
  }
  def this(width: scala.Int, height: scala.Int, depth: scala.Int, format: com.badlogic.gdx.graphics.Pixmap.Format) = {
    this(new com.badlogic.gdx.graphics.glutils.PixmapTextureData(new com.badlogic.gdx.graphics.Pixmap(depth, height, format), null, false, true), new com.badlogic.gdx.graphics.glutils.PixmapTextureData(new com.badlogic.gdx.graphics.Pixmap(depth, height, format), null, false, true), new com.badlogic.gdx.graphics.glutils.PixmapTextureData(new com.badlogic.gdx.graphics.Pixmap(width, depth, format), null, false, true), new com.badlogic.gdx.graphics.glutils.PixmapTextureData(new com.badlogic.gdx.graphics.Pixmap(width, depth, format), null, false, true), new com.badlogic.gdx.graphics.glutils.PixmapTextureData(new com.badlogic.gdx.graphics.Pixmap(width, height, format), null, false, true), new com.badlogic.gdx.graphics.glutils.PixmapTextureData(new com.badlogic.gdx.graphics.Pixmap(width, height, format), null, false, true))
  }
  def load(data: com.badlogic.gdx.graphics.CubemapData): scala.Unit = {
    if (!data.isPrepared()) {
      data.prepare()
    } else ()
    this.bind()
    this.unsafeSetFilter(minFilter, magFilter, true)
    this.unsafeSetWrap(uWrap, vWrap, true)
    this.unsafeSetAnisotropicFilter(anisotropicFilterLevel, true)
    data.consumeCubemapData()
    com.badlogic.gdx.Gdx.gl.glBindTexture(glTarget, 0)
  }
  def getCubemapData(): com.badlogic.gdx.graphics.CubemapData = {
    return this.data
  }
  def isManaged(): scala.Boolean = {
    return this.data.isManaged()
  }
  def reload(): scala.Unit = {
    if (!this.isManaged()) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Tried to reload an unmanaged Cubemap")
    } else ()
    glHandle = com.badlogic.gdx.Gdx.gl.glGenTexture()
    this.load(this.data)
  }
  def getWidth(): scala.Int = {
    return this.data.getWidth()
  }
  def getHeight(): scala.Int = {
    return this.data.getHeight()
  }
  def getDepth(): scala.Int = {
    return 0
  }
  def dispose(): scala.Unit = {
    if (glHandle == 0) {
      return
    } else ()
    this.delete()
    if (this.data.isManaged()) {
      if (Cubemap.managedCubemaps.getOrElse(com.badlogic.gdx.Gdx.app, null.asInstanceOf[com.badlogic.gdx.utils.Array[Cubemap]]) != null) {
        Cubemap.managedCubemaps.getOrElse(com.badlogic.gdx.Gdx.app, null.asInstanceOf[com.badlogic.gdx.utils.Array[Cubemap]]).removeValue(this, true)
      } else ()
    } else ()
  }
}
object Cubemap {
  export com.badlogic.gdx.graphics.GLTexture.{assetManager => _, managedCubemaps => _, addManagedCubemap => _, clearAllCubemaps => _, invalidateAllCubemaps => _, setAssetManager => _, getManagedStatus => _, getNumManagedCubemaps => _, CubemapSide => _, *}
  private var assetManager: com.badlogic.gdx.assets.AssetManager = null.asInstanceOf[com.badlogic.gdx.assets.AssetManager]
  final val managedCubemaps: scala.collection.mutable.Map[com.badlogic.gdx.Application, com.badlogic.gdx.utils.Array[Cubemap]] = new scala.collection.mutable.HashMap[com.badlogic.gdx.Application, com.badlogic.gdx.utils.Array[Cubemap]]()
  private def addManagedCubemap(app: com.badlogic.gdx.Application, cubemap: Cubemap): scala.Unit = {
    var managedCubemapArray: com.badlogic.gdx.utils.Array[Cubemap] = Cubemap.managedCubemaps.getOrElse(app, null.asInstanceOf[com.badlogic.gdx.utils.Array[Cubemap]])
    if (managedCubemapArray == null) {
      managedCubemapArray = new com.badlogic.gdx.utils.Array[Cubemap]()
    } else ()
    managedCubemapArray.add(cubemap)
    Cubemap.managedCubemaps.update(app, managedCubemapArray)
  }
  def clearAllCubemaps(app: com.badlogic.gdx.Application): scala.Unit = {
    Cubemap.managedCubemaps -= app
  }
  def invalidateAllCubemaps(app: com.badlogic.gdx.Application): scala.Unit = {
    val managedCubemapArray: com.badlogic.gdx.utils.Array[Cubemap] = Cubemap.managedCubemaps.getOrElse(app, null.asInstanceOf[com.badlogic.gdx.utils.Array[Cubemap]])
    if (managedCubemapArray == null) {
      return
    } else ()
    if (Cubemap.assetManager == null) {
      { var i: scala.Int = 0; while (i < managedCubemapArray.size) { {
        var cubemap: Cubemap = managedCubemapArray.get(i)
        cubemap.reload()
      }; i = i + 1 } }
    } else {
      Cubemap.assetManager.finishLoading()
      val cubemaps: com.badlogic.gdx.utils.Array[Cubemap] = new com.badlogic.gdx.utils.Array[Cubemap](managedCubemapArray)
      for (cubemap <- cubemaps) {
        val fileName: java.lang.String = Cubemap.assetManager.getAssetFileName(cubemap)
        if (fileName == null) {
          cubemap.reload()
        } else {
          val refCount: scala.Int = Cubemap.assetManager.getReferenceCount(fileName)
          Cubemap.assetManager.setReferenceCount(fileName, 0)
          cubemap.glHandle = 0
          val params: com.badlogic.gdx.assets.loaders.CubemapLoader.CubemapParameter = new com.badlogic.gdx.assets.loaders.CubemapLoader.CubemapParameter()
          params.cubemapData = cubemap.getCubemapData()
          params.minFilter = cubemap.getMinFilter()
          params.magFilter = cubemap.getMagFilter()
          params.wrapU = cubemap.getUWrap()
          params.wrapV = cubemap.getVWrap()
          params.cubemap = cubemap
          params.loadedCallback = new com.badlogic.gdx.assets.AssetLoaderParameters.LoadedCallback()
          Cubemap.assetManager.unload(fileName)
          cubemap.glHandle = com.badlogic.gdx.Gdx.gl.glGenTexture()
          Cubemap.assetManager.load(fileName, classOf[Cubemap], params)
        }
      }
      managedCubemapArray.clear()
      managedCubemapArray.addAll(cubemaps)
    }
  }
  def setAssetManager(manager: com.badlogic.gdx.assets.AssetManager): scala.Unit = {
    Cubemap.assetManager = manager
  }
  def getManagedStatus(): java.lang.String = {
    val builder: java.lang.StringBuilder = new java.lang.StringBuilder()
    builder.append("Managed cubemap/app: { ")
    for (app <- Cubemap.managedCubemaps.keySet) {
      builder.append(Cubemap.managedCubemaps.getOrElse(app, null.asInstanceOf[com.badlogic.gdx.utils.Array[Cubemap]]).size)
      builder.append(" ")
    }
    builder.append("}")
    return builder.toString()
  }
  def getNumManagedCubemaps(): scala.Int = {
    return Cubemap.managedCubemaps.getOrElse(com.badlogic.gdx.Gdx.app, null.asInstanceOf[com.badlogic.gdx.utils.Array[Cubemap]]).size
  }
  sealed abstract class CubemapSide(var index: scala.Int, var glEnum: scala.Int, var upX: scala.Float, var upY: scala.Float, var upZ: scala.Float, var directionX: scala.Float, var directionY: scala.Float, var directionZ: scala.Float) {
    var up: com.badlogic.gdx.math.Vector3 = null.asInstanceOf[com.badlogic.gdx.math.Vector3]
    var direction: com.badlogic.gdx.math.Vector3 = null.asInstanceOf[com.badlogic.gdx.math.Vector3]
    def getGLEnum(): scala.Int = {
      return this.glEnum
    }
    def getUp(out: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
      return out.set(this.up)
    }
    def getDirection(out: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
      return out.set(this.direction)
    }
    def name(): java.lang.String = this.toString()
  }
  object CubemapSide {
    case object PositiveX extends CubemapSide(0, com.badlogic.gdx.graphics.GL20.GL_TEXTURE_CUBE_MAP_POSITIVE_X, 0, -1, 0, 1, 0, 0)
    case object NegativeX extends CubemapSide(1, com.badlogic.gdx.graphics.GL20.GL_TEXTURE_CUBE_MAP_NEGATIVE_X, 0, -1, 0, -1, 0, 0)
    case object PositiveY extends CubemapSide(2, com.badlogic.gdx.graphics.GL20.GL_TEXTURE_CUBE_MAP_POSITIVE_Y, 0, 0, 1, 0, 1, 0)
    case object NegativeY extends CubemapSide(3, com.badlogic.gdx.graphics.GL20.GL_TEXTURE_CUBE_MAP_NEGATIVE_Y, 0, 0, -1, 0, -1, 0)
    case object PositiveZ extends CubemapSide(4, com.badlogic.gdx.graphics.GL20.GL_TEXTURE_CUBE_MAP_POSITIVE_Z, 0, -1, 0, 0, 0, 1)
    case object NegativeZ extends CubemapSide(5, com.badlogic.gdx.graphics.GL20.GL_TEXTURE_CUBE_MAP_NEGATIVE_Z, 0, -1, 0, 0, 0, -1)
    def values(): scala.Array[CubemapSide] = scala.Array(PositiveX, NegativeX, PositiveY, NegativeY, PositiveZ, NegativeZ)
    def valueOf(name: java.lang.String): CubemapSide = name match {
      case "PositiveX" => PositiveX
      case "NegativeX" => NegativeX
      case "PositiveY" => PositiveY
      case "NegativeY" => NegativeY
      case "PositiveZ" => PositiveZ
      case "NegativeZ" => NegativeZ
      case _ => throw new java.lang.IllegalArgumentException(name)
    }
  }
}