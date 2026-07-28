package com.badlogic.gdx.graphics

object PixmapIO {
  def writeCIM(file: com.badlogic.gdx.files.FileHandle, pixmap: com.badlogic.gdx.graphics.Pixmap): scala.Unit = {
    com.badlogic.gdx.graphics.PixmapIO.CIM.write(file, pixmap)
  }
  def readCIM(file: com.badlogic.gdx.files.FileHandle): com.badlogic.gdx.graphics.Pixmap = {
    return com.badlogic.gdx.graphics.PixmapIO.CIM.read(file)
  }
  def writePNG(file: com.badlogic.gdx.files.FileHandle, pixmap: com.badlogic.gdx.graphics.Pixmap, compression: scala.Int, flipY: scala.Boolean): scala.Unit = {
    try {
      val writer: com.badlogic.gdx.graphics.PixmapIO.PNG = new com.badlogic.gdx.graphics.PixmapIO.PNG(((pixmap.getWidth() * pixmap.getHeight()) * 1.5f).asInstanceOf[scala.Int].asInstanceOf[scala.Int])
      try {
        writer.setFlipY(flipY)
        writer.setCompression(compression)
        writer.write(file, pixmap)
      } finally {
        writer.dispose()
      }
    } catch {
      case ex: java.io.IOException => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Error writing PNG: " + file, ex)
      }
    }
  }
  def writePNG(file: com.badlogic.gdx.files.FileHandle, pixmap: com.badlogic.gdx.graphics.Pixmap): scala.Unit = {
    PixmapIO.writePNG(file, pixmap, java.util.zip.Deflater.DEFAULT_COMPRESSION, false)
  }
  object CIM {
    private final val BUFFER_SIZE: scala.Int = 32000
    private final val writeBuffer: scala.Array[scala.Byte] = new scala.Array[scala.Byte](com.badlogic.gdx.graphics.PixmapIO.CIM.BUFFER_SIZE)
    private final val readBuffer: scala.Array[scala.Byte] = new scala.Array[scala.Byte](com.badlogic.gdx.graphics.PixmapIO.CIM.BUFFER_SIZE)
    def write(file: com.badlogic.gdx.files.FileHandle, pixmap: com.badlogic.gdx.graphics.Pixmap): scala.Unit = {
      var out: java.io.DataOutputStream = null
      try {
        val deflaterOutputStream: java.util.zip.DeflaterOutputStream = new java.util.zip.DeflaterOutputStream(file.write(false))
        out = new java.io.DataOutputStream(deflaterOutputStream)
        out.writeInt(pixmap.getWidth())
        out.writeInt(pixmap.getHeight())
        out.writeInt(com.badlogic.gdx.graphics.Pixmap.Format.toGdx2DPixmapFormat(pixmap.getFormat()))
        val pixelBuf: java.nio.ByteBuffer = pixmap.getPixels()
        pixelBuf.asInstanceOf[java.nio.Buffer].position(0)
        pixelBuf.asInstanceOf[java.nio.Buffer].limit(pixelBuf.capacity())
        val remainingBytes: scala.Int = pixelBuf.capacity() % com.badlogic.gdx.graphics.PixmapIO.CIM.BUFFER_SIZE
        val iterations: scala.Int = pixelBuf.capacity() / com.badlogic.gdx.graphics.PixmapIO.CIM.BUFFER_SIZE
        com.badlogic.gdx.graphics.PixmapIO.CIM.writeBuffer.synchronized {
          { var i: scala.Int = 0; while (i < iterations) { {
            pixelBuf.get(com.badlogic.gdx.graphics.PixmapIO.CIM.writeBuffer)
            out.write(com.badlogic.gdx.graphics.PixmapIO.CIM.writeBuffer)
          }; i = i + 1 } }
          pixelBuf.get(com.badlogic.gdx.graphics.PixmapIO.CIM.writeBuffer, 0, remainingBytes)
          out.write(com.badlogic.gdx.graphics.PixmapIO.CIM.writeBuffer, 0, remainingBytes)
        }
        pixelBuf.asInstanceOf[java.nio.Buffer].position(0)
        pixelBuf.asInstanceOf[java.nio.Buffer].limit(pixelBuf.capacity())
      } catch {
        case e: java.lang.Exception => {
          throw new com.badlogic.gdx.utils.GdxRuntimeException(("Couldn't write Pixmap to file '" + file) + "'", e)
        }
      } finally {
        com.badlogic.gdx.utils.StreamUtils.closeQuietly(out)
      }
    }
    def read(file: com.badlogic.gdx.files.FileHandle): com.badlogic.gdx.graphics.Pixmap = {
      var in: java.io.DataInputStream = null
      try {
        in = new java.io.DataInputStream(new java.util.zip.InflaterInputStream(new java.io.BufferedInputStream(file.read())))
        val width: scala.Int = in.readInt()
        val height: scala.Int = in.readInt()
        val format: com.badlogic.gdx.graphics.Pixmap.Format = com.badlogic.gdx.graphics.Pixmap.Format.fromGdx2DPixmapFormat(in.readInt())
        val pixmap: com.badlogic.gdx.graphics.Pixmap = new com.badlogic.gdx.graphics.Pixmap(width, height, format)
        val pixelBuf: java.nio.ByteBuffer = pixmap.getPixels()
        pixelBuf.asInstanceOf[java.nio.Buffer].position(0)
        pixelBuf.asInstanceOf[java.nio.Buffer].limit(pixelBuf.capacity())
        com.badlogic.gdx.graphics.PixmapIO.CIM.readBuffer.synchronized {
          var readBytes: scala.Int = 0
          while ({
            readBytes = in.read(com.badlogic.gdx.graphics.PixmapIO.CIM.readBuffer)
            readBytes
          } > 0) {
            pixelBuf.put(com.badlogic.gdx.graphics.PixmapIO.CIM.readBuffer, 0, readBytes)
          }
        }
        pixelBuf.asInstanceOf[java.nio.Buffer].position(0)
        pixelBuf.asInstanceOf[java.nio.Buffer].limit(pixelBuf.capacity())
        return pixmap
      } catch {
        case e: java.lang.Exception => {
          throw new com.badlogic.gdx.utils.GdxRuntimeException(("Couldn't read Pixmap from file '" + file) + "'", e)
        }
      } finally {
        com.badlogic.gdx.utils.StreamUtils.closeQuietly(in)
      }
    }
  }
  class PNG(initialBufferSize: scala.Int) extends com.badlogic.gdx.utils.Disposable {
    private var buffer: com.badlogic.gdx.graphics.PixmapIO.PNG.ChunkBuffer = null.asInstanceOf[com.badlogic.gdx.graphics.PixmapIO.PNG.ChunkBuffer]
    private var deflater: java.util.zip.Deflater = null.asInstanceOf[java.util.zip.Deflater]
    private var lineOutBytes: com.badlogic.gdx.utils.ByteArray = null.asInstanceOf[com.badlogic.gdx.utils.ByteArray]
    private var curLineBytes: com.badlogic.gdx.utils.ByteArray = null.asInstanceOf[com.badlogic.gdx.utils.ByteArray]
    private var prevLineBytes: com.badlogic.gdx.utils.ByteArray = null.asInstanceOf[com.badlogic.gdx.utils.ByteArray]
    private var flipY: scala.Boolean = true
    private var lastLineLen: scala.Int = 0
    def this() = {
      this(128 * 128)
    }
    this.buffer = new com.badlogic.gdx.graphics.PixmapIO.PNG.ChunkBuffer(initialBufferSize)
    this.deflater = new java.util.zip.Deflater()
    def setFlipY(flipY: scala.Boolean): scala.Unit = {
      this.flipY = flipY
    }
    def setCompression(level: scala.Int): scala.Unit = {
      this.deflater.setLevel(level)
    }
    def write(file: com.badlogic.gdx.files.FileHandle, pixmap: com.badlogic.gdx.graphics.Pixmap): scala.Unit = {
      val output: java.io.OutputStream = file.write(false)
      try {
        this.write(output, pixmap)
      } finally {
        com.badlogic.gdx.utils.StreamUtils.closeQuietly(output)
      }
    }
    def write(output: java.io.OutputStream, pixmap: com.badlogic.gdx.graphics.Pixmap): scala.Unit = {
      val deflaterOutput: java.util.zip.DeflaterOutputStream = new java.util.zip.DeflaterOutputStream(this.buffer, this.deflater)
      val dataOutput: java.io.DataOutputStream = new java.io.DataOutputStream(output)
      dataOutput.write(com.badlogic.gdx.graphics.PixmapIO.PNG.SIGNATURE)
      this.buffer.writeInt(com.badlogic.gdx.graphics.PixmapIO.PNG.IHDR)
      this.buffer.writeInt(pixmap.getWidth())
      this.buffer.writeInt(pixmap.getHeight())
      this.buffer.writeByte(8)
      this.buffer.writeByte(com.badlogic.gdx.graphics.PixmapIO.PNG.COLOR_ARGB)
      this.buffer.writeByte(com.badlogic.gdx.graphics.PixmapIO.PNG.COMPRESSION_DEFLATE)
      this.buffer.writeByte(com.badlogic.gdx.graphics.PixmapIO.PNG.FILTER_NONE)
      this.buffer.writeByte(com.badlogic.gdx.graphics.PixmapIO.PNG.INTERLACE_NONE)
      this.buffer.endChunk(dataOutput)
      this.buffer.writeInt(com.badlogic.gdx.graphics.PixmapIO.PNG.IDAT)
      this.deflater.reset()
      val lineLen: scala.Int = pixmap.getWidth() * 4
      var lineOut: scala.Array[scala.Byte] = null.asInstanceOf[scala.Array[scala.Byte]]
      var curLine: scala.Array[scala.Byte] = null.asInstanceOf[scala.Array[scala.Byte]]
      var prevLine: scala.Array[scala.Byte] = null.asInstanceOf[scala.Array[scala.Byte]]
      if (this.lineOutBytes == null) {
        lineOut = {
          this.lineOutBytes = new com.badlogic.gdx.utils.ByteArray(lineLen)
          this.lineOutBytes
        }.items
        curLine = {
          this.curLineBytes = new com.badlogic.gdx.utils.ByteArray(lineLen)
          this.curLineBytes
        }.items
        prevLine = {
          this.prevLineBytes = new com.badlogic.gdx.utils.ByteArray(lineLen)
          this.prevLineBytes
        }.items
      } else {
        lineOut = this.lineOutBytes.ensureCapacity(lineLen)
        curLine = this.curLineBytes.ensureCapacity(lineLen)
        prevLine = this.prevLineBytes.ensureCapacity(lineLen);
        { var i: scala.Int = 0; val n: scala.Int = this.lastLineLen; while (i < n) { {
          prevLine(i) = 0.asInstanceOf[scala.Byte]
        }; i = i + 1 } }
      }
      this.lastLineLen = lineLen
      val pixels: java.nio.ByteBuffer = pixmap.getPixels()
      val oldPosition: scala.Int = pixels.position()
      val rgba8888: scala.Boolean = pixmap.getFormat() == com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888;
      { var y: scala.Int = 0; val h: scala.Int = pixmap.getHeight(); while (y < h) { {
        val py: scala.Int = if (this.flipY) (h - y) - 1 else y
        if (rgba8888) {
          pixels.asInstanceOf[java.nio.Buffer].position(py * lineLen)
          pixels.get(curLine, 0, lineLen)
        } else {
          { var px: scala.Int = 0; var x: scala.Int = 0; while (px < pixmap.getWidth()) { {
            val pixel: scala.Int = pixmap.getPixel(px, py)
            curLine({ x += 1; x }) = ((pixel >> 24) & 255).asInstanceOf[scala.Byte].asInstanceOf[scala.Byte]
            curLine({ x += 1; x }) = ((pixel >> 16) & 255).asInstanceOf[scala.Byte].asInstanceOf[scala.Byte]
            curLine({ x += 1; x }) = ((pixel >> 8) & 255).asInstanceOf[scala.Byte].asInstanceOf[scala.Byte]
            curLine({ x += 1; x }) = (pixel & 255).asInstanceOf[scala.Byte].asInstanceOf[scala.Byte]
          }; px = px + 1 } }
        }
        lineOut(0) = (curLine(0) - prevLine(0)).asInstanceOf[scala.Byte].asInstanceOf[scala.Byte]
        lineOut(1) = (curLine(1) - prevLine(1)).asInstanceOf[scala.Byte].asInstanceOf[scala.Byte]
        lineOut(2) = (curLine(2) - prevLine(2)).asInstanceOf[scala.Byte].asInstanceOf[scala.Byte]
        lineOut(3) = (curLine(3) - prevLine(3)).asInstanceOf[scala.Byte].asInstanceOf[scala.Byte];
        { var x: scala.Int = 4; while (x < lineLen) { {
          val a: scala.Int = curLine(x - 4) & 255
          val b: scala.Int = prevLine(x) & 255
          var c: scala.Int = prevLine(x - 4) & 255
          val p: scala.Int = (a + b) - c
          var pa: scala.Int = p - a
          if (pa < 0) {
            pa = -pa
          } else ()
          var pb: scala.Int = p - b
          if (pb < 0) {
            pb = -pb
          } else ()
          var pc: scala.Int = p - c
          if (pc < 0) {
            pc = -pc
          } else ()
          if ((pa <= pb) && (pa <= pc)) {
            c = a
          } else {
            if (pb <= pc) {
              c = b
            } else ()
          }
          lineOut(x) = (curLine(x) - c).asInstanceOf[scala.Byte].asInstanceOf[scala.Byte]
        }; x = x + 1 } }
        deflaterOutput.write(com.badlogic.gdx.graphics.PixmapIO.PNG.PAETH)
        deflaterOutput.write(lineOut, 0, lineLen)
        val temp: scala.Array[scala.Byte] = curLine
        curLine = prevLine
        prevLine = temp
      }; y = y + 1 } }
      pixels.asInstanceOf[java.nio.Buffer].position(oldPosition)
      deflaterOutput.finish()
      this.buffer.endChunk(dataOutput)
      this.buffer.writeInt(com.badlogic.gdx.graphics.PixmapIO.PNG.IEND)
      this.buffer.endChunk(dataOutput)
      output.flush()
    }
    @java.lang.SuppressWarnings(scala.Array[java.lang.String]("javadoc"))
    def dispose(): scala.Unit = {
      this.deflater.`end`()
    }
  }
  object PNG {
    private final val SIGNATURE: scala.Array[scala.Byte] = scala.Array[scala.Byte](137.asInstanceOf[scala.Byte].asInstanceOf[scala.Byte], 80.asInstanceOf[scala.Byte], 78.asInstanceOf[scala.Byte], 71.asInstanceOf[scala.Byte], 13.asInstanceOf[scala.Byte], 10.asInstanceOf[scala.Byte], 26.asInstanceOf[scala.Byte], 10.asInstanceOf[scala.Byte])
    private final val IHDR: scala.Int = 1229472850
    private final val IDAT: scala.Int = 1229209940
    private final val IEND: scala.Int = 1229278788
    private final val COLOR_ARGB: scala.Byte = 6.asInstanceOf[scala.Byte]
    private final val COMPRESSION_DEFLATE: scala.Byte = 0.asInstanceOf[scala.Byte]
    private final val FILTER_NONE: scala.Byte = 0.asInstanceOf[scala.Byte]
    private final val INTERLACE_NONE: scala.Byte = 0.asInstanceOf[scala.Byte]
    private final val PAETH: scala.Byte = 4.asInstanceOf[scala.Byte]
    class ChunkBuffer(buffer$p: java.io.ByteArrayOutputStream, crc$p: java.util.zip.CRC32) extends java.io.DataOutputStream(new java.util.zip.CheckedOutputStream(buffer$p, crc$p)) {
      var buffer: java.io.ByteArrayOutputStream = null.asInstanceOf[java.io.ByteArrayOutputStream]
      var crc: java.util.zip.CRC32 = null.asInstanceOf[java.util.zip.CRC32]
      def this(initialSize: scala.Int) = {
        this(new java.io.ByteArrayOutputStream(initialSize), new java.util.zip.CRC32())
      }
      this.buffer = buffer$p
      this.crc = crc$p
      def endChunk(target: java.io.DataOutputStream): scala.Unit = {
        this.flush()
        target.writeInt(this.buffer.size() - 4)
        this.buffer.writeTo(target)
        target.writeInt(this.crc.getValue().asInstanceOf[scala.Int].asInstanceOf[scala.Int])
        this.buffer.reset()
        this.crc.reset()
      }
    }
  }
}