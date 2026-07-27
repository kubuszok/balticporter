package com.badlogic.gdx.utils

class DataInput(in: java.io.InputStream) extends java.io.DataInputStream(in) {
  private var chars: scala.Array[scala.Char] = new scala.Array[scala.Char](32)
  def readInt(optimizePositive: scala.Boolean): scala.Int = {
    var b: scala.Int = this.readByte()
    var result: scala.Int = b & 127
    if ((b & 128) != 0) {
      b = this.readByte()
      result = result | ((b & 127) << 7)
      if ((b & 128) != 0) {
        b = this.readByte()
        result = result | ((b & 127) << 14)
        if ((b & 128) != 0) {
          b = this.readByte()
          result = result | ((b & 127) << 21)
          if ((b & 128) != 0) {
            b = this.readByte()
            result = result | ((b & 127) << 28)
          } else ()
        } else ()
      } else ()
    } else ()
    return if (optimizePositive) result else (result >>> 1) ^ (-(result & 1))
  }
  def readString(): java.lang.String = {
    var charCount: scala.Int = this.readInt(true)
    charCount match {
      case 0 => {
        return null
      }
      case 1 => {
        return ""
      }
    }
    charCount = charCount - 1
    if (this.chars.length < charCount) {
      this.chars = new scala.Array[scala.Char](charCount)
    } else ()
    var chars: scala.Array[scala.Char] = this.chars
    var charIndex: scala.Int = 0
    var b: scala.Int = 0
    while (charIndex < charCount) {
      b = this.readByte()
      if (b < 0) {
        /* break */ ()
      } else ()
      chars({ charIndex += 1; charIndex }) = b.asInstanceOf[scala.Char].asInstanceOf[scala.Char]
    }
    if (charIndex < charCount) {
      this.readUtf8_slow(charCount, charIndex, b & 255)
    } else ()
    return new java.lang.String(chars, 0, charCount)
  }
  private def readUtf8_slow(charCount: scala.Int, charIndex$arg: scala.Int, b$arg: scala.Int): scala.Unit = {
    var charIndex: scala.Int = charIndex$arg
    var b: scala.Int = b$arg
    val chars: scala.Array[scala.Char] = this.chars
    while (true) {
      b >> 4 match {
        case 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 => {
          chars(charIndex) = b.asInstanceOf[scala.Char].asInstanceOf[scala.Char]
        }
        case 12 | 13 => {
          chars(charIndex) = (((b & 31) << 6) | (this.readByte() & 63)).asInstanceOf[scala.Char].asInstanceOf[scala.Char]
        }
        case 14 => {
          chars(charIndex) = ((((b & 15) << 12) | ((this.readByte() & 63) << 6)) | (this.readByte() & 63)).asInstanceOf[scala.Char].asInstanceOf[scala.Char]
        }
      }
      if ({ charIndex += 1; charIndex } >= charCount) {
        /* break */ ()
      } else ()
      b = this.readByte() & 255
    }
  }
}