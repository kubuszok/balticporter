package com.badlogic.gdx.graphics.g2d

class PolygonRegionLoader extends com.badlogic.gdx.assets.loaders.SynchronousAssetLoader[com.badlogic.gdx.graphics.g2d.PolygonRegion, PolygonRegionParameters] {
  private var defaultParameters: PolygonRegionParameters = new PolygonRegionParameters()
  private var triangulator: com.badlogic.gdx.math.EarClippingTriangulator = new com.badlogic.gdx.math.EarClippingTriangulator()
  def this(resolver: com.badlogic.gdx.assets.loaders.FileHandleResolver) = {
    this()
  }
  def load(manager: com.badlogic.gdx.assets.AssetManager, fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, parameter: PolygonRegionParameters): com.badlogic.gdx.graphics.g2d.PolygonRegion = {
    val texture: com.badlogic.gdx.graphics.Texture = manager.get(manager.getDependencies(fileName).first())
    return this.load(new com.badlogic.gdx.graphics.g2d.TextureRegion(texture), file)
  }
  def getDependencies(fileName: java.lang.String, file: com.badlogic.gdx.files.FileHandle, params$arg: PolygonRegionParameters): com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor] = {
    var params: PolygonRegionParameters = params$arg
    if (params == null) {
      params = this.defaultParameters
    } else ()
    var image: java.lang.String = null
    try {
      val reader: java.io.BufferedReader = file.reader(params.readerBuffer)
      { var line: java.lang.String = reader.readLine(); while (line != null) { {
        if (line.startsWith(params.texturePrefix)) {
          image = line.substring(params.texturePrefix.length())
          /* break */ ()
        } else ()
      }; line = reader.readLine() } }
      reader.close()
    } catch {
      case e: java.io.IOException => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Error reading " + fileName, e)
      }
    }
    if ((image == null) && (params.textureExtensions != null)) {
      for (`extension` <- params.textureExtensions) {
        val sibling: com.badlogic.gdx.files.FileHandle = file.sibling(file.nameWithoutExtension().concat("." + `extension`))
        if (sibling.exists()) {
          image = sibling.name()
        } else ()
      }
    } else ()
    if (image != null) {
      val deps: com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.assets.AssetDescriptor](1)
      deps.add(new com.badlogic.gdx.assets.AssetDescriptor[com.badlogic.gdx.graphics.Texture](file.sibling(image), classOf[java.lang.Class]))
      return deps
    } else ()
    return null
  }
  def load(textureRegion: com.badlogic.gdx.graphics.g2d.TextureRegion, file: com.badlogic.gdx.files.FileHandle): com.badlogic.gdx.graphics.g2d.PolygonRegion = {
    val reader: java.io.BufferedReader = file.reader(256)
    try {
      while (true) {
        val line: java.lang.String = reader.readLine()
        if (line == null) {
          /* break */ ()
        } else ()
        if (line.startsWith("s")) {
          val polygonStrings: scala.Array[java.lang.String] = line.substring(1).trim().split(",")
          val vertices: scala.Array[scala.Float] = new Array[scala.Float](polygonStrings.length)
          { var i: scala.Int = 0; val n: scala.Int = vertices.length; while (i < n) { {
            vertices(i) = java.lang.Float.parseFloat(polygonStrings(i))
          }; i = i + 1 } }
          return new com.badlogic.gdx.graphics.g2d.PolygonRegion(textureRegion, vertices, this.triangulator.computeTriangles(vertices).toArray())
        } else ()
      }
    } catch {
      case ex: java.io.IOException => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Error reading polygon shape file: " + file, ex)
      }
    } finally {
      com.badlogic.gdx.utils.StreamUtils.closeQuietly(reader)
    }
    throw new com.badlogic.gdx.utils.GdxRuntimeException("Polygon shape not found: " + file)
  }
  class PolygonRegionParameters extends com.badlogic.gdx.assets.AssetLoaderParameters[com.badlogic.gdx.graphics.g2d.PolygonRegion] {
    var texturePrefix: java.lang.String = "i "
    var readerBuffer: scala.Int = 1024
    var textureExtensions: scala.Array[java.lang.String] = Array[java.lang.String]("png", "PNG", "jpeg", "JPEG", "jpg", "JPG", "cim", "CIM", "etc1", "ETC1", "ktx", "KTX", "zktx", "ZKTX")
  }
}