package com.badlogic.gdx.graphics.glutils

class KTXTextureData(file$p: com.badlogic.gdx.files.FileHandle, genMipMaps: scala.Boolean) extends com.badlogic.gdx.graphics.TextureData with com.badlogic.gdx.graphics.CubemapData {
  private var file: com.badlogic.gdx.files.FileHandle = null.asInstanceOf[com.badlogic.gdx.files.FileHandle]
  private var glType: scala.Int = 0
  private var glTypeSize: scala.Int = 0
  private var glFormat: scala.Int = 0
  private var glInternalFormat: scala.Int = 0
  private var glBaseInternalFormat: scala.Int = 0
  private var pixelWidth: scala.Int = -1
  private var pixelHeight: scala.Int = -1
  private var pixelDepth: scala.Int = -1
  private var numberOfArrayElements: scala.Int = 0
  private var numberOfFaces: scala.Int = 0
  private var numberOfMipmapLevels: scala.Int = 0
  private var imagePos: scala.Int = 0
  private var compressedData: java.nio.ByteBuffer = null.asInstanceOf[java.nio.ByteBuffer]
  var useMipMaps$field: scala.Boolean = false
  this.file = file$p
  this.useMipMaps$field = genMipMaps
  @java.lang.Override
  override def getType(): com.badlogic.gdx.graphics.TextureData.TextureDataType = {
    return com.badlogic.gdx.graphics.TextureData.TextureDataType.Custom
  }
  @java.lang.Override
  override def isPrepared(): scala.Boolean = {
    return this.compressedData != null
  }
  @java.lang.Override
  override def prepare(): scala.Unit = {
    if (this.compressedData != null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Already prepared")
    } else ()
    if (this.file == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Need a file to load from")
    } else ()
    if (this.file.name().endsWith(".zktx")) {
      val buffer: scala.Array[scala.Byte] = new scala.Array[scala.Byte](1024 * 10)
      var in: java.io.DataInputStream = null
      try {
        in = new java.io.DataInputStream(new java.io.BufferedInputStream(new java.util.zip.GZIPInputStream(this.file.read())))
        val fileSize: scala.Int = in.readInt()
        this.compressedData = com.badlogic.gdx.utils.BufferUtils.newUnsafeByteBuffer(fileSize)
        var readBytes: scala.Int = 0
        while ({
          readBytes = in.read(buffer)
          readBytes
        } != (-1)) {
          this.compressedData.put(buffer, 0, readBytes)
        }
        this.compressedData.asInstanceOf[java.nio.Buffer].position(0)
        this.compressedData.asInstanceOf[java.nio.Buffer].limit(this.compressedData.capacity())
      } catch {
        case e: java.lang.Exception => {
          throw new com.badlogic.gdx.utils.GdxRuntimeException(("Couldn't load zktx file '" + this.file) + "'", e)
        }
      } finally {
        com.badlogic.gdx.utils.StreamUtils.closeQuietly(in)
      }
    } else {
      this.compressedData = java.nio.ByteBuffer.wrap(this.file.readBytes())
    }
    if (this.compressedData.get() != 171.asInstanceOf[scala.Byte]) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Invalid KTX Header")
    } else ()
    if (this.compressedData.get() != 75.asInstanceOf[scala.Byte]) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Invalid KTX Header")
    } else ()
    if (this.compressedData.get() != 84.asInstanceOf[scala.Byte]) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Invalid KTX Header")
    } else ()
    if (this.compressedData.get() != 88.asInstanceOf[scala.Byte]) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Invalid KTX Header")
    } else ()
    if (this.compressedData.get() != 32.asInstanceOf[scala.Byte]) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Invalid KTX Header")
    } else ()
    if (this.compressedData.get() != 49.asInstanceOf[scala.Byte]) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Invalid KTX Header")
    } else ()
    if (this.compressedData.get() != 49.asInstanceOf[scala.Byte]) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Invalid KTX Header")
    } else ()
    if (this.compressedData.get() != 187.asInstanceOf[scala.Byte]) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Invalid KTX Header")
    } else ()
    if (this.compressedData.get() != 13.asInstanceOf[scala.Byte]) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Invalid KTX Header")
    } else ()
    if (this.compressedData.get() != 10.asInstanceOf[scala.Byte]) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Invalid KTX Header")
    } else ()
    if (this.compressedData.get() != 26.asInstanceOf[scala.Byte]) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Invalid KTX Header")
    } else ()
    if (this.compressedData.get() != 10.asInstanceOf[scala.Byte]) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Invalid KTX Header")
    } else ()
    val endianTag: scala.Int = this.compressedData.getInt()
    if ((endianTag != 67305985) && (endianTag != 16909060)) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Invalid KTX Header")
    } else ()
    if (endianTag != 67305985) {
      this.compressedData.order(if (this.compressedData.order() == java.nio.ByteOrder.BIG_ENDIAN) java.nio.ByteOrder.LITTLE_ENDIAN else java.nio.ByteOrder.BIG_ENDIAN)
    } else ()
    this.glType = this.compressedData.getInt()
    this.glTypeSize = this.compressedData.getInt()
    this.glFormat = this.compressedData.getInt()
    this.glInternalFormat = this.compressedData.getInt()
    this.glBaseInternalFormat = this.compressedData.getInt()
    this.pixelWidth = this.compressedData.getInt()
    this.pixelHeight = this.compressedData.getInt()
    this.pixelDepth = this.compressedData.getInt()
    this.numberOfArrayElements = this.compressedData.getInt()
    this.numberOfFaces = this.compressedData.getInt()
    this.numberOfMipmapLevels = this.compressedData.getInt()
    if (this.numberOfMipmapLevels == 0) {
      this.numberOfMipmapLevels = 1
      this.useMipMaps$field = true
    } else ()
    val bytesOfKeyValueData: scala.Int = this.compressedData.getInt()
    this.imagePos = this.compressedData.position() + bytesOfKeyValueData
    if (!this.compressedData.isDirect()) {
      var pos: scala.Int = this.imagePos;
      { var level: scala.Int = 0; while (level < this.numberOfMipmapLevels) { {
        val faceLodSize: scala.Int = this.compressedData.getInt(pos)
        val faceLodSizeRounded: scala.Int = (faceLodSize + 3) & (~3)
        pos = pos + ((faceLodSizeRounded * this.numberOfFaces) + 4)
      }; level = level + 1 } }
      this.compressedData.asInstanceOf[java.nio.Buffer].limit(pos)
      this.compressedData.asInstanceOf[java.nio.Buffer].position(0)
      val directBuffer: java.nio.ByteBuffer = com.badlogic.gdx.utils.BufferUtils.newUnsafeByteBuffer(pos)
      directBuffer.order(this.compressedData.order())
      directBuffer.put(this.compressedData)
      this.compressedData = directBuffer
    } else ()
  }
  @java.lang.Override
  override def consumeCubemapData(): scala.Unit = {
    this.consumeCustomData(com.badlogic.gdx.graphics.GL20.GL_TEXTURE_CUBE_MAP)
  }
  @java.lang.Override
  override def consumeCustomData(target$arg: scala.Int): scala.Unit = {
    var target: scala.Int = target$arg
    if (this.compressedData == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Call prepare() before calling consumeCompressedData()")
    } else ()
    val buffer: java.nio.IntBuffer = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(16)
    var compressed: scala.Boolean = false
    if ((this.glType == 0) || (this.glFormat == 0)) {
      if ((this.glType + this.glFormat) != 0) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("either both or none of glType, glFormat must be zero")
      } else ()
      compressed = true
    } else ()
    var textureDimensions: scala.Int = 1
    var glTarget: scala.Int = KTXTextureData.GL_TEXTURE_1D
    if (this.pixelHeight > 0) {
      textureDimensions = 2
      glTarget = com.badlogic.gdx.graphics.GL20.GL_TEXTURE_2D
    } else ()
    if (this.pixelDepth > 0) {
      textureDimensions = 3
      glTarget = KTXTextureData.GL_TEXTURE_3D
    } else ()
    if (this.numberOfFaces == 6) {
      if (textureDimensions == 2) {
        glTarget = com.badlogic.gdx.graphics.GL20.GL_TEXTURE_CUBE_MAP
      } else {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("cube map needs 2D faces")
      }
    } else {
      if (this.numberOfFaces != 1) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("numberOfFaces must be either 1 or 6")
      } else ()
    }
    if (this.numberOfArrayElements > 0) {
      if (glTarget == KTXTextureData.GL_TEXTURE_1D) {
        glTarget = KTXTextureData.GL_TEXTURE_1D_ARRAY_EXT
      } else {
        if (glTarget == com.badlogic.gdx.graphics.GL20.GL_TEXTURE_2D) {
          glTarget = KTXTextureData.GL_TEXTURE_2D_ARRAY_EXT
        } else {
          throw new com.badlogic.gdx.utils.GdxRuntimeException("No API for 3D and cube arrays yet")
        }
      }
      textureDimensions = textureDimensions + 1
    } else ()
    if (glTarget == 4660) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Unsupported texture format (only 2D texture are supported in LibGdx for the time being)")
    } else ()
    var singleFace: scala.Int = -1
    if ((this.numberOfFaces == 6) && (target != com.badlogic.gdx.graphics.GL20.GL_TEXTURE_CUBE_MAP)) {
      if (!((com.badlogic.gdx.graphics.GL20.GL_TEXTURE_CUBE_MAP_POSITIVE_X <= target) && (target <= com.badlogic.gdx.graphics.GL20.GL_TEXTURE_CUBE_MAP_NEGATIVE_Z))) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("You must specify either GL_TEXTURE_CUBE_MAP to bind all 6 faces of the cube or the requested face GL_TEXTURE_CUBE_MAP_POSITIVE_X and followings.")
      } else ()
      singleFace = target - com.badlogic.gdx.graphics.GL20.GL_TEXTURE_CUBE_MAP_POSITIVE_X
      target = com.badlogic.gdx.graphics.GL20.GL_TEXTURE_CUBE_MAP_POSITIVE_X
    } else {
      if ((this.numberOfFaces == 6) && (target == com.badlogic.gdx.graphics.GL20.GL_TEXTURE_CUBE_MAP)) {
        target = com.badlogic.gdx.graphics.GL20.GL_TEXTURE_CUBE_MAP_POSITIVE_X
      } else {
        if ((target != glTarget) && (!(((com.badlogic.gdx.graphics.GL20.GL_TEXTURE_CUBE_MAP_POSITIVE_X <= target) && (target <= com.badlogic.gdx.graphics.GL20.GL_TEXTURE_CUBE_MAP_NEGATIVE_Z)) && (target == com.badlogic.gdx.graphics.GL20.GL_TEXTURE_2D)))) {
          throw new com.badlogic.gdx.utils.GdxRuntimeException((("Invalid target requested : 0x" + java.lang.Integer.toHexString(target)) + ", expecting : 0x") + java.lang.Integer.toHexString(glTarget))
        } else ()
      }
    }
    com.badlogic.gdx.Gdx.gl.glGetIntegerv(com.badlogic.gdx.graphics.GL20.GL_UNPACK_ALIGNMENT, buffer)
    val previousUnpackAlignment: scala.Int = buffer.get(0)
    if (previousUnpackAlignment != 4) {
      com.badlogic.gdx.Gdx.gl.glPixelStorei(com.badlogic.gdx.graphics.GL20.GL_UNPACK_ALIGNMENT, 4)
    } else ()
    val glInternalFormat: scala.Int = this.glInternalFormat
    val glFormat: scala.Int = this.glFormat
    var pos: scala.Int = this.imagePos;
    { var level: scala.Int = 0; while (level < this.numberOfMipmapLevels) { {
      val pixelWidth: scala.Int = java.lang.Math.max(1, this.pixelWidth >> level)
      var pixelHeight: scala.Int = java.lang.Math.max(1, this.pixelHeight >> level)
      var pixelDepth: scala.Int = java.lang.Math.max(1, this.pixelDepth >> level)
      this.compressedData.asInstanceOf[java.nio.Buffer].position(pos)
      val faceLodSize: scala.Int = this.compressedData.getInt()
      val faceLodSizeRounded: scala.Int = (faceLodSize + 3) & (~3)
      pos = pos + 4;
      { var face: scala.Int = 0; while (face < this.numberOfFaces) { {
        this.compressedData.asInstanceOf[java.nio.Buffer].position(pos)
        pos = pos + faceLodSizeRounded
        if ((singleFace != (-1)) && (singleFace != face)) {
          /* continue */ ()
        } else ()
        val data: java.nio.ByteBuffer = this.compressedData.slice()
        data.asInstanceOf[java.nio.Buffer].limit(faceLodSizeRounded)
        if (textureDimensions == 1) {
          ()
        } else {
          if (textureDimensions == 2) {
            if (this.numberOfArrayElements > 0) {
              pixelHeight = this.numberOfArrayElements
            } else ()
            if (compressed) {
              if (glInternalFormat == com.badlogic.gdx.graphics.glutils.ETC1.ETC1_RGB8_OES) {
                if (!com.badlogic.gdx.Gdx.graphics.supportsExtension("GL_OES_compressed_ETC1_RGB8_texture")) {
                  val etcData: com.badlogic.gdx.graphics.glutils.ETC1.ETC1Data = new com.badlogic.gdx.graphics.glutils.ETC1.ETC1Data(pixelWidth, pixelHeight, data, 0)
                  val pixmap: com.badlogic.gdx.graphics.Pixmap = com.badlogic.gdx.graphics.glutils.ETC1.decodeImage(etcData, com.badlogic.gdx.graphics.Pixmap.Format.RGB888)
                  com.badlogic.gdx.Gdx.gl.glTexImage2D(target + face, level, pixmap.getGLInternalFormat(), pixmap.getWidth(), pixmap.getHeight(), 0, pixmap.getGLFormat(), pixmap.getGLType(), pixmap.getPixels())
                  pixmap.dispose()
                } else {
                  com.badlogic.gdx.Gdx.gl.glCompressedTexImage2D(target + face, level, glInternalFormat, pixelWidth, pixelHeight, 0, faceLodSize, data)
                }
              } else {
                com.badlogic.gdx.Gdx.gl.glCompressedTexImage2D(target + face, level, glInternalFormat, pixelWidth, pixelHeight, 0, faceLodSize, data)
              }
            } else {
              com.badlogic.gdx.Gdx.gl.glTexImage2D(target + face, level, glInternalFormat, pixelWidth, pixelHeight, 0, glFormat, this.glType, data)
            }
          } else {
            if (textureDimensions == 3) {
              if (this.numberOfArrayElements > 0) {
                pixelDepth = this.numberOfArrayElements
              } else ()
            } else ()
          }
        }
      }; face = face + 1 } }
    }; level = level + 1 } }
    if (previousUnpackAlignment != 4) {
      com.badlogic.gdx.Gdx.gl.glPixelStorei(com.badlogic.gdx.graphics.GL20.GL_UNPACK_ALIGNMENT, previousUnpackAlignment)
    } else ()
    if (this.useMipMaps()) {
      com.badlogic.gdx.Gdx.gl.glGenerateMipmap(target)
    } else ()
    this.disposePreparedData()
  }
  def disposePreparedData(): scala.Unit = {
    if (this.compressedData != null) {
      com.badlogic.gdx.utils.BufferUtils.disposeUnsafeByteBuffer(this.compressedData)
    } else ()
    this.compressedData = null
  }
  @java.lang.Override
  override def consumePixmap(): com.badlogic.gdx.graphics.Pixmap = {
    throw new com.badlogic.gdx.utils.GdxRuntimeException("This TextureData implementation does not return a Pixmap")
  }
  @java.lang.Override
  override def disposePixmap(): scala.Boolean = {
    throw new com.badlogic.gdx.utils.GdxRuntimeException("This TextureData implementation does not return a Pixmap")
  }
  @java.lang.Override
  override def getWidth(): scala.Int = {
    return this.pixelWidth
  }
  @java.lang.Override
  override def getHeight(): scala.Int = {
    return this.pixelHeight
  }
  def getNumberOfMipMapLevels(): scala.Int = {
    return this.numberOfMipmapLevels
  }
  def getNumberOfFaces(): scala.Int = {
    return this.numberOfFaces
  }
  def getGlInternalFormat(): scala.Int = {
    return this.glInternalFormat
  }
  def getData(requestedLevel: scala.Int, requestedFace: scala.Int): java.nio.ByteBuffer = {
    var pos: scala.Int = this.imagePos;
    { var level: scala.Int = 0; while (level < this.numberOfMipmapLevels) { {
      val faceLodSize: scala.Int = this.compressedData.getInt(pos)
      val faceLodSizeRounded: scala.Int = (faceLodSize + 3) & (~3)
      pos = pos + 4
      if (level == requestedLevel) {
        { var face: scala.Int = 0; while (face < this.numberOfFaces) { {
          if (face == requestedFace) {
            this.compressedData.asInstanceOf[java.nio.Buffer].position(pos)
            val data: java.nio.ByteBuffer = this.compressedData.slice()
            data.asInstanceOf[java.nio.Buffer].limit(faceLodSizeRounded)
            return data
          } else ()
          pos = pos + faceLodSizeRounded
        }; face = face + 1 } }
      } else {
        pos = pos + (faceLodSizeRounded * this.numberOfFaces)
      }
    }; level = level + 1 } }
    return null
  }
  @java.lang.Override
  override def getFormat(): com.badlogic.gdx.graphics.Pixmap.Format = {
    throw new com.badlogic.gdx.utils.GdxRuntimeException("This TextureData implementation directly handles texture formats.")
  }
  @java.lang.Override
  override def useMipMaps(): scala.Boolean = {
    return this.useMipMaps$field
  }
  @java.lang.Override
  override def isManaged(): scala.Boolean = {
    return true
  }
}
object KTXTextureData {
  export com.badlogic.gdx.graphics.TextureData.{GL_TEXTURE_1D => _, GL_TEXTURE_1D_ARRAY_EXT => _, GL_TEXTURE_2D_ARRAY_EXT => _, GL_TEXTURE_3D => _, *}
  private final val GL_TEXTURE_1D: scala.Int = 4660
  private final val GL_TEXTURE_3D: scala.Int = 4660
  private final val GL_TEXTURE_1D_ARRAY_EXT: scala.Int = 4660
  private final val GL_TEXTURE_2D_ARRAY_EXT: scala.Int = 4660
}