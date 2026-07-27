package com.badlogic.gdx.utils

class DataOutput(out: java.io.OutputStream) extends java.io.DataOutputStream(out) {
  def writeInt(value$arg: scala.Int, optimizePositive: scala.Boolean): scala.Int = {
    var value: scala.Int = value$arg
    if (!optimizePositive) {
      value = (value << 1) ^ (value >> 31)
    } else ()
    if ((value >>> 7) == 0) {
      this.write(value.asInstanceOf[scala.Byte])
      return 1
    } else ()
    this.write(((value & 127) | 128).asInstanceOf[scala.Byte])
    if ((value >>> 14) == 0) {
      this.write((value >>> 7).asInstanceOf[scala.Byte])
      return 2
    } else ()
    this.write(((value >>> 7) | 128).asInstanceOf[scala.Byte])
    if ((value >>> 21) == 0) {
      this.write((value >>> 14).asInstanceOf[scala.Byte])
      return 3
    } else ()
    this.write(((value >>> 14) | 128).asInstanceOf[scala.Byte])
    if ((value >>> 28) == 0) {
      this.write((value >>> 21).asInstanceOf[scala.Byte])
      return 4
    } else ()
    this.write(((value >>> 21) | 128).asInstanceOf[scala.Byte])
    this.write((value >>> 28).asInstanceOf[scala.Byte])
    return 5
  }
  def writeString(value: java.lang.String): scala.Unit = {
    if (value == null) {
      this.write(0)
      return
    } else ()
    val charCount: scala.Int = value.length()
    if (charCount == 0) {
      this.writeByte(1)
      return
    } else ()
    this.writeInt(charCount + 1, true)
    var charIndex: scala.Int = 0;
    { ; while (charIndex < charCount) { {
      val c: scala.Int = value.charAt(charIndex)
      if (c > 127) {
        /* break */ ()
      } else ()
      this.write(c.asInstanceOf[scala.Byte])
    }; charIndex = charIndex + 1 } }
    if (charIndex < charCount) {
      this.writeString_slow(value, charCount, charIndex)
    } else ()
  }
  private def writeString_slow(value: java.lang.String, charCount: scala.Int, charIndex$arg: scala.Int): scala.Unit = {
    var charIndex: scala.Int = charIndex$arg;
    { ; while (charIndex < charCount) { {
      val c: scala.Int = value.charAt(charIndex)
      if (c <= 127) {
        this.write(c.asInstanceOf[scala.Byte])
      } else {
        if (c > 2047) {
          this.write((224 | ((c >> 12) & 15)).asInstanceOf[scala.Byte])
          this.write((128 | ((c >> 6) & 63)).asInstanceOf[scala.Byte])
          this.write((128 | (c & 63)).asInstanceOf[scala.Byte])
        } else {
          this.write((192 | ((c >> 6) & 31)).asInstanceOf[scala.Byte])
          this.write((128 | (c & 63)).asInstanceOf[scala.Byte])
        }
      }
    }; charIndex = charIndex + 1 } }
  }
}