package com.badlogic.gdx.utils

class LittleEndianInputStream extends java.io.FilterInputStream with java.io.DataInput {
  private var din: java.io.DataInputStream = null.asInstanceOf[java.io.DataInputStream]
  def this(in: java.io.InputStream) = {
    this()
    this.din = new java.io.DataInputStream(in)
  }
  def readFully(b: scala.Array[scala.Byte]): scala.Unit = {
    this.din.readFully(b)
  }
  def readFully(b: scala.Array[scala.Byte], off: scala.Int, len: scala.Int): scala.Unit = {
    this.din.readFully(b, off, len)
  }
  def skipBytes(n: scala.Int): scala.Int = {
    return this.din.skipBytes(n)
  }
  def readBoolean(): scala.Boolean = {
    return this.din.readBoolean()
  }
  def readByte(): scala.Byte = {
    return this.din.readByte()
  }
  def readUnsignedByte(): scala.Int = {
    return this.din.readUnsignedByte()
  }
  def readShort(): scala.Short = {
    val low: scala.Int = this.din.read()
    val high: scala.Int = this.din.read()
    return ((high << 8) | (low & 255)).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
  }
  def readUnsignedShort(): scala.Int = {
    val low: scala.Int = this.din.read()
    val high: scala.Int = this.din.read()
    return ((high & 255) << 8) | (low & 255)
  }
  def readChar(): scala.Char = {
    return this.din.readChar()
  }
  def readInt(): scala.Int = {
    val res: scala.Array[scala.Int] = new Array[scala.Int](4);
    { var i: scala.Int = 3; while (i >= 0) { {
      res(i) = this.din.read()
    }; i = i - 1 } }
    return ((((res(0) & 255) << 24) | ((res(1) & 255) << 16)) | ((res(2) & 255) << 8)) | (res(3) & 255)
  }
  def readLong(): scala.Long = {
    val res: scala.Array[scala.Int] = new Array[scala.Int](8);
    { var i: scala.Int = 7; while (i >= 0) { {
      res(i) = this.din.read()
    }; i = i - 1 } }
    return ((((((((res(0) & 255).asInstanceOf[scala.Long] << 56) | ((res(1) & 255).asInstanceOf[scala.Long] << 48)) | ((res(2) & 255).asInstanceOf[scala.Long] << 40)) | ((res(3) & 255).asInstanceOf[scala.Long] << 32)) | ((res(4) & 255).asInstanceOf[scala.Long] << 24)) | ((res(5) & 255).asInstanceOf[scala.Long] << 16)) | ((res(6) & 255).asInstanceOf[scala.Long] << 8)) | (res(7) & 255).asInstanceOf[scala.Long]
  }
  def readFloat(): scala.Float = {
    return java.lang.Float.intBitsToFloat(this.readInt())
  }
  def readDouble(): scala.Double = {
    return java.lang.Double.longBitsToDouble(this.readLong())
  }
  final def readLine(): java.lang.String = {
    return this.din.readLine()
  }
  def readUTF(): java.lang.String = {
    return this.din.readUTF()
  }
}