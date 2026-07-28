package com.badlogic.gdx.utils

class LittleEndianInputStream(in: java.io.InputStream) extends java.io.FilterInputStream(in) with java.io.DataInput {
  private var din: java.io.DataInputStream = null.asInstanceOf[java.io.DataInputStream]
  this.din = new java.io.DataInputStream(in)
  override def readFully(b: scala.Array[scala.Byte]): scala.Unit = {
    this.din.readFully(b)
  }
  override def readFully(b: scala.Array[scala.Byte], off: scala.Int, len: scala.Int): scala.Unit = {
    this.din.readFully(b, off, len)
  }
  override def skipBytes(n: scala.Int): scala.Int = {
    return this.din.skipBytes(n)
  }
  override def readBoolean(): scala.Boolean = {
    return this.din.readBoolean()
  }
  override def readByte(): scala.Byte = {
    return this.din.readByte()
  }
  override def readUnsignedByte(): scala.Int = {
    return this.din.readUnsignedByte()
  }
  override def readShort(): scala.Short = {
    val low: scala.Int = this.din.read()
    val high: scala.Int = this.din.read()
    return ((high << 8) | (low & 255)).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
  }
  override def readUnsignedShort(): scala.Int = {
    val low: scala.Int = this.din.read()
    val high: scala.Int = this.din.read()
    return ((high & 255) << 8) | (low & 255)
  }
  override def readChar(): scala.Char = {
    return this.din.readChar()
  }
  override def readInt(): scala.Int = {
    val res: scala.Array[scala.Int] = new scala.Array[scala.Int](4);
    { var i: scala.Int = 3; while (i >= 0) { {
      res(i) = this.din.read()
    }; i = i - 1 } }
    return ((((res(0) & 255) << 24) | ((res(1) & 255) << 16)) | ((res(2) & 255) << 8)) | (res(3) & 255)
  }
  override def readLong(): scala.Long = {
    val res: scala.Array[scala.Int] = new scala.Array[scala.Int](8);
    { var i: scala.Int = 7; while (i >= 0) { {
      res(i) = this.din.read()
    }; i = i - 1 } }
    return ((((((((res(0) & 255).asInstanceOf[scala.Long] << 56) | ((res(1) & 255).asInstanceOf[scala.Long] << 48)) | ((res(2) & 255).asInstanceOf[scala.Long] << 40)) | ((res(3) & 255).asInstanceOf[scala.Long] << 32)) | ((res(4) & 255).asInstanceOf[scala.Long] << 24)) | ((res(5) & 255).asInstanceOf[scala.Long] << 16)) | ((res(6) & 255).asInstanceOf[scala.Long] << 8)) | (res(7) & 255).asInstanceOf[scala.Long]
  }
  override def readFloat(): scala.Float = {
    return java.lang.Float.intBitsToFloat(this.readInt())
  }
  override def readDouble(): scala.Double = {
    return java.lang.Double.longBitsToDouble(this.readLong())
  }
  override final def readLine(): java.lang.String = {
    return this.din.readLine()
  }
  override def readUTF(): java.lang.String = {
    return this.din.readUTF()
  }
}