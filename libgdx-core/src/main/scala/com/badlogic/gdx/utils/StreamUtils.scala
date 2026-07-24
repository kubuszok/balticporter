package com.badlogic.gdx.utils

object StreamUtils {
  final val DEFAULT_BUFFER_SIZE: scala.Int = 4096
  final val EMPTY_BYTES: scala.Array[scala.Byte] = new Array[scala.Byte](0)
  def copyStream(input: java.io.InputStream, output: java.io.OutputStream): scala.Unit = {
    StreamUtils.copyStream(input, output, new Array[scala.Byte](StreamUtils.DEFAULT_BUFFER_SIZE))
  }
  def copyStream(input: java.io.InputStream, output: java.io.OutputStream, bufferSize: scala.Int): scala.Unit = {
    StreamUtils.copyStream(input, output, new Array[scala.Byte](bufferSize))
  }
  def copyStream(input: java.io.InputStream, output: java.io.OutputStream, buffer: scala.Array[scala.Byte]): scala.Unit = {
    var bytesRead: scala.Int = 0
    while ({
      bytesRead = input.read(buffer)
      bytesRead
    } != (-1)) {
      output.write(buffer, 0, bytesRead)
    }
  }
  def copyStream(input: java.io.InputStream, output: java.nio.ByteBuffer): scala.Unit = {
    StreamUtils.copyStream(input, output, new Array[scala.Byte](StreamUtils.DEFAULT_BUFFER_SIZE))
  }
  def copyStream(input: java.io.InputStream, output: java.nio.ByteBuffer, bufferSize: scala.Int): scala.Unit = {
    StreamUtils.copyStream(input, output, new Array[scala.Byte](bufferSize))
  }
  def copyStream(input: java.io.InputStream, output: java.nio.ByteBuffer, buffer: scala.Array[scala.Byte]): scala.Int = {
    val startPosition: scala.Int = output.position()
    var total: scala.Int = 0
    var bytesRead: scala.Int = 0
    while ({
      bytesRead = input.read(buffer)
      bytesRead
    } != (-1)) {
      com.badlogic.gdx.utils.BufferUtils.copy(buffer, 0, output, bytesRead)
      total = total + bytesRead
      output.asInstanceOf[java.nio.Buffer].position(startPosition + total)
    }
    output.asInstanceOf[java.nio.Buffer].position(startPosition)
    return total
  }
  def copyStreamToByteArray(input: java.io.InputStream): scala.Array[scala.Byte] = {
    return StreamUtils.copyStreamToByteArray(input, input.available())
  }
  def copyStreamToByteArray(input: java.io.InputStream, estimatedSize: scala.Int): scala.Array[scala.Byte] = {
    val baos: java.io.ByteArrayOutputStream = new com.badlogic.gdx.utils.StreamUtils.OptimizedByteArrayOutputStream(java.lang.Math.max(0, estimatedSize))
    StreamUtils.copyStream(input, baos)
    return baos.toByteArray()
  }
  def copyStreamToString(input: java.io.InputStream): java.lang.String = {
    return StreamUtils.copyStreamToString(input, input.available(), null)
  }
  def copyStreamToString(input: java.io.InputStream, estimatedSize: scala.Int): java.lang.String = {
    return StreamUtils.copyStreamToString(input, estimatedSize, null)
  }
  def copyStreamToString(input: java.io.InputStream, estimatedSize: scala.Int, charset: java.lang.String): java.lang.String = {
    val reader: java.io.InputStreamReader = if (charset == null) new java.io.InputStreamReader(input) else new java.io.InputStreamReader(input, charset)
    val writer: java.io.StringWriter = new java.io.StringWriter(java.lang.Math.max(0, estimatedSize))
    val buffer: scala.Array[scala.Char] = new Array[scala.Char](StreamUtils.DEFAULT_BUFFER_SIZE)
    var charsRead: scala.Int = 0
    while ({
      charsRead = reader.read(buffer)
      charsRead
    } != (-1)) {
      writer.write(buffer, 0, charsRead)
    }
    return writer.toString()
  }
  def closeQuietly(c: java.io.Closeable): scala.Unit = {
    if (c != null) {
      try {
        c.close()
      } catch {
        case ignored: java.lang.Throwable => {
          ()
        }
      }
    } else ()
  }
  class OptimizedByteArrayOutputStream extends java.io.ByteArrayOutputStream {
    def this(initialSize: scala.Int) = {
      this()
    }
    def toByteArray(): scala.Array[scala.Byte] = {
      if (count == this.buf.length) {
        return buf
      } else ()
      return super.toByteArray()
    }
    def getBuffer(): scala.Array[scala.Byte] = {
      return buf
    }
  }
}