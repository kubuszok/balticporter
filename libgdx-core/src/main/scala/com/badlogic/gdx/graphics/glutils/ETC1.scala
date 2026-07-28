package com.badlogic.gdx.graphics.glutils

object ETC1 {
  var PKM_HEADER_SIZE: scala.Int = 16
  var ETC1_RGB8_OES: scala.Int = 36196
  private val getCompressedDataSize$20594$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("getCompressedDataSize").orElseThrow(), java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT))
  private val formatHeader$20597$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("formatHeader").orElseThrow(), java.lang.foreign.FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT))
  private val getWidthPKM$20588$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("getWidthPKM").orElseThrow(), java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT))
  private val getHeightPKM$20590$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("getHeightPKM").orElseThrow(), java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT))
  private val isValidPKM$20606$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("isValidPKM").orElseThrow(), java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_BOOLEAN, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT))
  private val decodeImage$20593$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("decodeImage").orElseThrow(), java.lang.foreign.FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT))
  private val encodeImage$20571$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("encodeImage").orElseThrow(), java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT))
  private val encodeImagePKM$20578$handle: java.lang.invoke.MethodHandle = java.lang.foreign.Linker.nativeLinker().downcallHandle(java.lang.foreign.Linker.nativeLinker().defaultLookup().find("encodeImagePKM").orElseThrow(), java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT))
  private def getPixelSize(format: com.badlogic.gdx.graphics.Pixmap.Format): scala.Int = {
    if (format == com.badlogic.gdx.graphics.Pixmap.Format.RGB565) {
      return 2
    } else ()
    if (format == com.badlogic.gdx.graphics.Pixmap.Format.RGB888) {
      return 3
    } else ()
    throw new com.badlogic.gdx.utils.GdxRuntimeException("Can only handle RGB565 or RGB888 images")
  }
  def encodeImage(pixmap: com.badlogic.gdx.graphics.Pixmap): com.badlogic.gdx.graphics.glutils.ETC1.ETC1Data = {
    val pixelSize: scala.Int = ETC1.getPixelSize(pixmap.getFormat())
    val compressedData: java.nio.ByteBuffer = ETC1.encodeImage(pixmap.getPixels(), 0, pixmap.getWidth(), pixmap.getHeight(), pixelSize)
    com.badlogic.gdx.utils.BufferUtils.newUnsafeByteBuffer(compressedData)
    return new com.badlogic.gdx.graphics.glutils.ETC1.ETC1Data(pixmap.getWidth(), pixmap.getHeight(), compressedData, 0)
  }
  def encodeImagePKM(pixmap: com.badlogic.gdx.graphics.Pixmap): com.badlogic.gdx.graphics.glutils.ETC1.ETC1Data = {
    val pixelSize: scala.Int = ETC1.getPixelSize(pixmap.getFormat())
    val compressedData: java.nio.ByteBuffer = ETC1.encodeImagePKM(pixmap.getPixels(), 0, pixmap.getWidth(), pixmap.getHeight(), pixelSize)
    com.badlogic.gdx.utils.BufferUtils.newUnsafeByteBuffer(compressedData)
    return new com.badlogic.gdx.graphics.glutils.ETC1.ETC1Data(pixmap.getWidth(), pixmap.getHeight(), compressedData, 16)
  }
  def decodeImage(etc1Data: com.badlogic.gdx.graphics.glutils.ETC1.ETC1Data, format: com.badlogic.gdx.graphics.Pixmap.Format): com.badlogic.gdx.graphics.Pixmap = {
    var dataOffset: scala.Int = 0
    var width: scala.Int = 0
    var height: scala.Int = 0
    if (etc1Data.hasPKMHeader()) {
      dataOffset = 16
      width = ETC1.getWidthPKM(etc1Data.compressedData, 0)
      height = ETC1.getHeightPKM(etc1Data.compressedData, 0)
    } else {
      dataOffset = 0
      width = etc1Data.width
      height = etc1Data.height
    }
    val pixelSize: scala.Int = ETC1.getPixelSize(format)
    val pixmap: com.badlogic.gdx.graphics.Pixmap = new com.badlogic.gdx.graphics.Pixmap(width, height, format)
    ETC1.decodeImage(etc1Data.compressedData, dataOffset, pixmap.getPixels(), 0, width, height, pixelSize)
    return pixmap
  }
  def getCompressedDataSize(width: scala.Int, height: scala.Int): scala.Int = getCompressedDataSize$20594$handle.invokeExact(width, height).asInstanceOf[scala.Int]
  def formatHeader(header: java.nio.ByteBuffer, offset: scala.Int, width: scala.Int, height: scala.Int): scala.Unit = { formatHeader$20597$handle.invokeExact(header, offset, width, height); () }
  def getWidthPKM(header: java.nio.ByteBuffer, offset: scala.Int): scala.Int = getWidthPKM$20588$handle.invokeExact(header, offset).asInstanceOf[scala.Int]
  def getHeightPKM(header: java.nio.ByteBuffer, offset: scala.Int): scala.Int = getHeightPKM$20590$handle.invokeExact(header, offset).asInstanceOf[scala.Int]
  def isValidPKM(header: java.nio.ByteBuffer, offset: scala.Int): scala.Boolean = isValidPKM$20606$handle.invokeExact(header, offset).asInstanceOf[scala.Boolean]
  private def decodeImage(compressedData: java.nio.ByteBuffer, offset: scala.Int, decodedData: java.nio.ByteBuffer, offsetDec: scala.Int, width: scala.Int, height: scala.Int, pixelSize: scala.Int): scala.Unit = { decodeImage$20593$handle.invokeExact(compressedData, offset, decodedData, offsetDec, width, height, pixelSize); () }
  private def encodeImage(imageData: java.nio.ByteBuffer, offset: scala.Int, width: scala.Int, height: scala.Int, pixelSize: scala.Int): java.nio.ByteBuffer = encodeImage$20571$handle.invokeExact(imageData, offset, width, height, pixelSize).asInstanceOf[java.nio.ByteBuffer]
  private def encodeImagePKM(imageData: java.nio.ByteBuffer, offset: scala.Int, width: scala.Int, height: scala.Int, pixelSize: scala.Int): java.nio.ByteBuffer = encodeImagePKM$20578$handle.invokeExact(imageData, offset, width, height, pixelSize).asInstanceOf[java.nio.ByteBuffer]
  final class ETC1Data extends com.badlogic.gdx.utils.Disposable {
    var width: scala.Int = 0
    var height: scala.Int = 0
    var compressedData: java.nio.ByteBuffer = null.asInstanceOf[java.nio.ByteBuffer]
    var dataOffset: scala.Int = 0
    def this(width: scala.Int, height: scala.Int, compressedData: java.nio.ByteBuffer, dataOffset: scala.Int) = {
      this()
      this.width = width
      this.height = height
      this.compressedData = compressedData
      this.dataOffset = dataOffset
      this.checkNPOT()
    }
    def this(pkmFile: com.badlogic.gdx.files.FileHandle) = {
      this()
      val buffer: scala.Array[scala.Byte] = new scala.Array[scala.Byte](1024 * 10)
      var in: java.io.DataInputStream = null
      try {
        in = new java.io.DataInputStream(new java.io.BufferedInputStream(new java.util.zip.GZIPInputStream(pkmFile.read())))
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
          throw new com.badlogic.gdx.utils.GdxRuntimeException(("Couldn't load pkm file '" + pkmFile) + "'", e)
        }
      } finally {
        com.badlogic.gdx.utils.StreamUtils.closeQuietly(in)
      }
      this.width = ETC1.getWidthPKM(this.compressedData, 0)
      this.height = ETC1.getHeightPKM(this.compressedData, 0)
      this.dataOffset = ETC1.PKM_HEADER_SIZE
      this.compressedData.asInstanceOf[java.nio.Buffer].position(this.dataOffset)
      this.checkNPOT()
    }
    private def checkNPOT(): scala.Unit = {
      if ((!com.badlogic.gdx.math.MathUtils.isPowerOfTwo(this.width)) || (!com.badlogic.gdx.math.MathUtils.isPowerOfTwo(this.height))) {
        java.lang.System.out.println("ETC1Data " + "warning: non-power-of-two ETC1 textures may crash the driver of PowerVR GPUs")
      } else ()
    }
    def hasPKMHeader(): scala.Boolean = {
      return this.dataOffset == 16
    }
    def write(file: com.badlogic.gdx.files.FileHandle): scala.Unit = {
      var write: java.io.DataOutputStream = null
      val buffer: scala.Array[scala.Byte] = new scala.Array[scala.Byte](10 * 1024)
      var writtenBytes: scala.Int = 0
      this.compressedData.asInstanceOf[java.nio.Buffer].position(0)
      this.compressedData.asInstanceOf[java.nio.Buffer].limit(this.compressedData.capacity())
      try {
        write = new java.io.DataOutputStream(new java.util.zip.GZIPOutputStream(file.write(false)))
        write.writeInt(this.compressedData.capacity())
        while (writtenBytes != this.compressedData.capacity()) {
          val bytesToWrite: scala.Int = java.lang.Math.min(this.compressedData.remaining(), buffer.length)
          this.compressedData.get(buffer, 0, bytesToWrite)
          write.write(buffer, 0, bytesToWrite)
          writtenBytes = writtenBytes + bytesToWrite
        }
      } catch {
        case e: java.lang.Exception => {
          throw new com.badlogic.gdx.utils.GdxRuntimeException(("Couldn't write PKM file to '" + file) + "'", e)
        }
      } finally {
        com.badlogic.gdx.utils.StreamUtils.closeQuietly(write)
      }
      this.compressedData.asInstanceOf[java.nio.Buffer].position(this.dataOffset)
      this.compressedData.asInstanceOf[java.nio.Buffer].limit(this.compressedData.capacity())
    }
    override def dispose(): scala.Unit = {
      com.badlogic.gdx.utils.BufferUtils.disposeUnsafeByteBuffer(this.compressedData)
    }
    override def toString(): java.lang.String = {
      if (this.hasPKMHeader()) {
        return ((((((if (ETC1.isValidPKM(this.compressedData, 0)) "valid" else "invalid") + " pkm [") + ETC1.getWidthPKM(this.compressedData, 0)) + "x") + ETC1.getHeightPKM(this.compressedData, 0)) + "], compressed: ") + (this.compressedData.capacity() - ETC1.PKM_HEADER_SIZE)
      } else {
        return (((("raw [" + this.width) + "x") + this.height) + "], compressed: ") + (this.compressedData.capacity() - ETC1.PKM_HEADER_SIZE)
      }
    }
  }
}