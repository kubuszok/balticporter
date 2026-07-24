package com.badlogic.gdx.utils

object Base64Coder {
  private final val systemLineSeparator: java.lang.String = "\n"
  final val regularMap: com.badlogic.gdx.utils.Base64Coder.CharMap = new com.badlogic.gdx.utils.Base64Coder.CharMap('+', '/')
  final val urlsafeMap: com.badlogic.gdx.utils.Base64Coder.CharMap = new com.badlogic.gdx.utils.Base64Coder.CharMap('-', '_')
  def encodeString(s: java.lang.String): java.lang.String = {
    return Base64Coder.encodeString(s, false)
  }
  def encodeString(s: java.lang.String, useUrlsafeEncoding: scala.Boolean): java.lang.String = {
    try {
      return new java.lang.String(Base64Coder.encode(s.getBytes("UTF-8"), if (useUrlsafeEncoding) Base64Coder.urlsafeMap.encodingMap else Base64Coder.regularMap.encodingMap))
    } catch {
      case e: java.io.UnsupportedEncodingException => {
        return ""
      }
    }
  }
  def encodeLines(in: scala.Array[scala.Byte]): java.lang.String = {
    return Base64Coder.encodeLines(in, 0, in.length, 76, Base64Coder.systemLineSeparator, Base64Coder.regularMap.encodingMap)
  }
  def encodeLines(in: scala.Array[scala.Byte], iOff: scala.Int, iLen: scala.Int, lineLen: scala.Int, lineSeparator: java.lang.String, charMap: com.badlogic.gdx.utils.Base64Coder.CharMap): java.lang.String = {
    return Base64Coder.encodeLines(in, iOff, iLen, lineLen, lineSeparator, charMap.encodingMap)
  }
  def encodeLines(in: scala.Array[scala.Byte], iOff: scala.Int, iLen: scala.Int, lineLen: scala.Int, lineSeparator: java.lang.String, charMap: scala.Array[scala.Char]): java.lang.String = {
    val blockLen: scala.Int = (lineLen * 3) / 4
    if (blockLen <= 0) {
      throw new java.lang.IllegalArgumentException()
    } else ()
    val lines: scala.Int = ((iLen + blockLen) - 1) / blockLen
    val bufLen: scala.Int = (((iLen + 2) / 3) * 4) + (lines * lineSeparator.length())
    val buf: java.lang.StringBuilder = new java.lang.StringBuilder(bufLen)
    var ip: scala.Int = 0
    while (ip < iLen) {
      val l: scala.Int = java.lang.Math.min(iLen - ip, blockLen)
      buf.append(Base64Coder.encode(in, iOff + ip, l, charMap))
      buf.append(lineSeparator)
      ip = ip + l
    }
    return buf.toString()
  }
  def encode(in: scala.Array[scala.Byte]): scala.Array[scala.Char] = {
    return Base64Coder.encode(in, Base64Coder.regularMap.encodingMap)
  }
  def encode(in: scala.Array[scala.Byte], charMap: com.badlogic.gdx.utils.Base64Coder.CharMap): scala.Array[scala.Char] = {
    return Base64Coder.encode(in, 0, in.length, charMap)
  }
  def encode(in: scala.Array[scala.Byte], charMap: scala.Array[scala.Char]): scala.Array[scala.Char] = {
    return Base64Coder.encode(in, 0, in.length, charMap)
  }
  def encode(in: scala.Array[scala.Byte], iLen: scala.Int): scala.Array[scala.Char] = {
    return Base64Coder.encode(in, 0, iLen, Base64Coder.regularMap.encodingMap)
  }
  def encode(in: scala.Array[scala.Byte], iOff: scala.Int, iLen: scala.Int, charMap: com.badlogic.gdx.utils.Base64Coder.CharMap): scala.Array[scala.Char] = {
    return Base64Coder.encode(in, iOff, iLen, charMap.encodingMap)
  }
  def encode(in: scala.Array[scala.Byte], iOff: scala.Int, iLen: scala.Int, charMap: scala.Array[scala.Char]): scala.Array[scala.Char] = {
    val oDataLen: scala.Int = ((iLen * 4) + 2) / 3
    val oLen: scala.Int = ((iLen + 2) / 3) * 4
    val out: scala.Array[scala.Char] = new Array[scala.Char](oLen)
    var ip: scala.Int = iOff
    val iEnd: scala.Int = iOff + iLen
    var op: scala.Int = 0
    while (ip < iEnd) {
      val i0: scala.Int = in({ ip += 1; ip }) & 255
      val i1: scala.Int = if (ip < iEnd) in({ ip += 1; ip }) & 255 else 0
      val i2: scala.Int = if (ip < iEnd) in({ ip += 1; ip }) & 255 else 0
      val o0: scala.Int = i0 >>> 2
      val o1: scala.Int = ((i0 & 3) << 4) | (i1 >>> 4)
      val o2: scala.Int = ((i1 & 15) << 2) | (i2 >>> 6)
      val o3: scala.Int = i2 & 63
      out({ op += 1; op }) = charMap(o0)
      out({ op += 1; op }) = charMap(o1)
      out(op) = if (op < oDataLen) charMap(o2) else '='
      op = op + 1
      out(op) = if (op < oDataLen) charMap(o3) else '='
      op = op + 1
    }
    return out
  }
  def decodeString(s: java.lang.String): java.lang.String = {
    return Base64Coder.decodeString(s, false)
  }
  def decodeString(s: java.lang.String, useUrlSafeEncoding: scala.Boolean): java.lang.String = {
    return new java.lang.String(Base64Coder.decode(s.toCharArray(), if (useUrlSafeEncoding) Base64Coder.urlsafeMap.decodingMap else Base64Coder.regularMap.decodingMap))
  }
  def decodeLines(s: java.lang.String): scala.Array[scala.Byte] = {
    return Base64Coder.decodeLines(s, Base64Coder.regularMap.decodingMap)
  }
  def decodeLines(s: java.lang.String, inverseCharMap: com.badlogic.gdx.utils.Base64Coder.CharMap): scala.Array[scala.Byte] = {
    return Base64Coder.decodeLines(s, inverseCharMap.decodingMap)
  }
  def decodeLines(s: java.lang.String, inverseCharMap: scala.Array[scala.Byte]): scala.Array[scala.Byte] = {
    val buf: scala.Array[scala.Char] = new Array[scala.Char](s.length())
    var p: scala.Int = 0;
    { var ip: scala.Int = 0; while (ip < s.length()) { {
      val c: scala.Char = s.charAt(ip)
      if ((((c != ' ') && (c != '\r')) && (c != '\n')) && (c != '\t')) {
        buf({ p += 1; p }) = c
      } else ()
    }; ip = ip + 1 } }
    return Base64Coder.decode(buf, 0, p, inverseCharMap)
  }
  def decode(s: java.lang.String): scala.Array[scala.Byte] = {
    return Base64Coder.decode(s.toCharArray())
  }
  def decode(s: java.lang.String, inverseCharMap: com.badlogic.gdx.utils.Base64Coder.CharMap): scala.Array[scala.Byte] = {
    return Base64Coder.decode(s.toCharArray(), inverseCharMap)
  }
  def decode(in: scala.Array[scala.Char], inverseCharMap: scala.Array[scala.Byte]): scala.Array[scala.Byte] = {
    return Base64Coder.decode(in, 0, in.length, inverseCharMap)
  }
  def decode(in: scala.Array[scala.Char], inverseCharMap: com.badlogic.gdx.utils.Base64Coder.CharMap): scala.Array[scala.Byte] = {
    return Base64Coder.decode(in, 0, in.length, inverseCharMap)
  }
  def decode(in: scala.Array[scala.Char]): scala.Array[scala.Byte] = {
    return Base64Coder.decode(in, 0, in.length, Base64Coder.regularMap.decodingMap)
  }
  def decode(in: scala.Array[scala.Char], iOff: scala.Int, iLen: scala.Int, inverseCharMap: com.badlogic.gdx.utils.Base64Coder.CharMap): scala.Array[scala.Byte] = {
    return Base64Coder.decode(in, iOff, iLen, inverseCharMap.decodingMap)
  }
  def decode(in: scala.Array[scala.Char], iOff: scala.Int, iLen$arg: scala.Int, inverseCharMap: scala.Array[scala.Byte]): scala.Array[scala.Byte] = {
    var iLen: scala.Int = iLen$arg
    if ((iLen % 4) != 0) {
      throw new java.lang.IllegalArgumentException("Length of Base64 encoded input string is not a multiple of 4.")
    } else ()
    while ((iLen > 0) && (in((iOff + iLen) - 1) == '=')) {
      iLen = iLen - 1
    }
    val oLen: scala.Int = (iLen * 3) / 4
    val out: scala.Array[scala.Byte] = new Array[scala.Byte](oLen)
    var ip: scala.Int = iOff
    val iEnd: scala.Int = iOff + iLen
    var op: scala.Int = 0
    while (ip < iEnd) {
      val i0: scala.Int = in({ ip += 1; ip })
      val i1: scala.Int = in({ ip += 1; ip })
      val i2: scala.Int = if (ip < iEnd) in({ ip += 1; ip }) else 'A'
      val i3: scala.Int = if (ip < iEnd) in({ ip += 1; ip }) else 'A'
      if ((((i0 > 127) || (i1 > 127)) || (i2 > 127)) || (i3 > 127)) {
        throw new java.lang.IllegalArgumentException("Illegal character in Base64 encoded data.")
      } else ()
      val b0: scala.Int = inverseCharMap(i0)
      val b1: scala.Int = inverseCharMap(i1)
      val b2: scala.Int = inverseCharMap(i2)
      val b3: scala.Int = inverseCharMap(i3)
      if ((((b0 < 0) || (b1 < 0)) || (b2 < 0)) || (b3 < 0)) {
        throw new java.lang.IllegalArgumentException("Illegal character in Base64 encoded data.")
      } else ()
      val o0: scala.Int = (b0 << 2) | (b1 >>> 4)
      val o1: scala.Int = ((b1 & 15) << 4) | (b2 >>> 2)
      val o2: scala.Int = ((b2 & 3) << 6) | b3
      out({ op += 1; op }) = o0.asInstanceOf[scala.Byte].asInstanceOf[scala.Byte]
      if (op < oLen) {
        out({ op += 1; op }) = o1.asInstanceOf[scala.Byte].asInstanceOf[scala.Byte]
      } else ()
      if (op < oLen) {
        out({ op += 1; op }) = o2.asInstanceOf[scala.Byte].asInstanceOf[scala.Byte]
      } else ()
    }
    return out
  }
  class CharMap {
    final val encodingMap: scala.Array[scala.Char] = new Array[scala.Char](64)
    final val decodingMap: scala.Array[scala.Byte] = new Array[scala.Byte](128)
    def this(char63: scala.Char, char64: scala.Char) = {
      this()
      var i: scala.Int = 0
      { var c: scala.Char = 'A'; while (c <= 'Z') { {
        this.encodingMap({ i += 1; i }) = c
      }; c = c + 1 } }
      { var c: scala.Char = 'a'; while (c <= 'z') { {
        this.encodingMap({ i += 1; i }) = c
      }; c = c + 1 } }
      { var c: scala.Char = '0'; while (c <= '9') { {
        this.encodingMap({ i += 1; i }) = c
      }; c = c + 1 } }
      this.encodingMap({ i += 1; i }) = char63
      this.encodingMap({ i += 1; i }) = char64
      { i = 0; while (i < this.decodingMap.length) { {
        this.decodingMap(i) = (-1).asInstanceOf[scala.Byte]
      }; i = i + 1 } }
      { i = 0; while (i < 64) { {
        this.decodingMap(this.encodingMap(i)) = i.asInstanceOf[scala.Byte].asInstanceOf[scala.Byte]
      }; i = i + 1 } }
    }
    def getDecodingMap(): scala.Array[scala.Byte] = {
      return this.decodingMap
    }
    def getEncodingMap(): scala.Array[scala.Char] = {
      return this.encodingMap
    }
  }
}