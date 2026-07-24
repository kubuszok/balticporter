package com.badlogic.gdx.utils

class UBJsonReader extends com.badlogic.gdx.utils.BaseJsonReader {
  var oldFormat: scala.Boolean = true
  def parse(input: java.io.InputStream): com.badlogic.gdx.utils.JsonValue = {
    var din: java.io.DataInputStream = null
    try {
      din = new java.io.DataInputStream(input)
      return this.parse(din)
    } catch {
      case ex: java.io.IOException => {
        throw new com.badlogic.gdx.utils.SerializationException(ex)
      }
    } finally {
      com.badlogic.gdx.utils.StreamUtils.closeQuietly(din)
    }
  }
  def parse(file: com.badlogic.gdx.files.FileHandle): com.badlogic.gdx.utils.JsonValue = {
    try {
      return this.parse(file.read(8192))
    } catch {
      case ex: java.lang.Exception => {
        throw new com.badlogic.gdx.utils.SerializationException("Error parsing file: " + file, ex)
      }
    }
  }
  def parse(din: java.io.DataInputStream): com.badlogic.gdx.utils.JsonValue = {
    try {
      return this.parse(din, din.readByte())
    } finally {
      com.badlogic.gdx.utils.StreamUtils.closeQuietly(din)
    }
  }
  protected def parse(din: java.io.DataInputStream, `type`: scala.Byte): com.badlogic.gdx.utils.JsonValue = {
    if (`type` == '[') {
      return this.parseArray(din)
    } else {
      if (`type` == '{') {
        return this.parseObject(din)
      } else {
        if (`type` == 'Z') {
          return new com.badlogic.gdx.utils.JsonValue(com.badlogic.gdx.utils.JsonValue.ValueType.nullValue)
        } else {
          if (`type` == 'T') {
            return new com.badlogic.gdx.utils.JsonValue(true)
          } else {
            if (`type` == 'F') {
              return new com.badlogic.gdx.utils.JsonValue(false)
            } else {
              if (`type` == 'B') {
                return new com.badlogic.gdx.utils.JsonValue(this.readUChar(din).asInstanceOf[scala.Long])
              } else {
                if (`type` == 'U') {
                  return new com.badlogic.gdx.utils.JsonValue(this.readUChar(din).asInstanceOf[scala.Long])
                } else {
                  if (`type` == 'i') {
                    return new com.badlogic.gdx.utils.JsonValue(if (this.oldFormat) din.readShort().asInstanceOf[scala.Long] else din.readByte().asInstanceOf[scala.Long])
                  } else {
                    if (`type` == 'I') {
                      return new com.badlogic.gdx.utils.JsonValue(if (this.oldFormat) din.readInt().asInstanceOf[scala.Long] else din.readShort().asInstanceOf[scala.Long])
                    } else {
                      if (`type` == 'l') {
                        return new com.badlogic.gdx.utils.JsonValue(din.readInt().asInstanceOf[scala.Long])
                      } else {
                        if (`type` == 'L') {
                          return new com.badlogic.gdx.utils.JsonValue(din.readLong())
                        } else {
                          if (`type` == 'd') {
                            return new com.badlogic.gdx.utils.JsonValue(din.readFloat())
                          } else {
                            if (`type` == 'D') {
                              return new com.badlogic.gdx.utils.JsonValue(din.readDouble())
                            } else {
                              if ((`type` == 's') || (`type` == 'S')) {
                                return new com.badlogic.gdx.utils.JsonValue(this.parseString(din, `type`))
                              } else {
                                if ((`type` == 'a') || (`type` == 'A')) {
                                  return this.parseData(din, `type`)
                                } else {
                                  if (`type` == 'C') {
                                    return new com.badlogic.gdx.utils.JsonValue(din.readChar())
                                  } else {
                                    throw new com.badlogic.gdx.utils.GdxRuntimeException("Unrecognized data type")
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
  protected def parseArray(din: java.io.DataInputStream): com.badlogic.gdx.utils.JsonValue = {
    val result: com.badlogic.gdx.utils.JsonValue = new com.badlogic.gdx.utils.JsonValue(com.badlogic.gdx.utils.JsonValue.ValueType.array)
    var `type`: scala.Byte = din.readByte()
    var valueType: scala.Byte = 0
    if (`type` == '$') {
      valueType = din.readByte()
      `type` = din.readByte()
    } else ()
    var size: scala.Int = -1
    if (`type` == '#') {
      size = this.parseSize(din, false, -1).asInstanceOf[scala.Int]
      if (size < 0) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Unrecognized data type")
      } else ()
      if (size == 0) {
        return result
      } else ()
      `type` = if (valueType == 0) din.readByte() else valueType
    } else ()
    var prev: com.badlogic.gdx.utils.JsonValue = null
    var c: scala.Int = 0
    while ((din.available() > 0) && (`type` != ']')) {
      val `val`: com.badlogic.gdx.utils.JsonValue = this.parse(din, `type`)
      `val`.parent$field = result
      if (prev != null) {
        `val`.prev$field = prev
        prev.next$field = `val`
      } else {
        result.child$field = `val`
      }
      prev = `val`
      c = c + 1
      if ((size > 0) && (c >= size)) {
        /* break */ ()
      } else ()
      `type` = if (valueType == 0) din.readByte() else valueType
    }
    result.size$field = c
    result.last$field = prev
    return result
  }
  protected def parseObject(din: java.io.DataInputStream): com.badlogic.gdx.utils.JsonValue = {
    val result: com.badlogic.gdx.utils.JsonValue = new com.badlogic.gdx.utils.JsonValue(com.badlogic.gdx.utils.JsonValue.ValueType.`object`)
    var `type`: scala.Byte = din.readByte()
    var valueType: scala.Byte = 0
    if (`type` == '$') {
      valueType = din.readByte()
      `type` = din.readByte()
    } else ()
    var size: scala.Int = -1
    if (`type` == '#') {
      size = this.parseSize(din, false, -1).asInstanceOf[scala.Int]
      if (size < 0) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Unrecognized data type")
      } else ()
      if (size == 0) {
        return result
      } else ()
      `type` = din.readByte()
    } else ()
    var prev: com.badlogic.gdx.utils.JsonValue = null
    var c: scala.Int = 0
    while ((din.available() > 0) && (`type` != '}')) {
      val key: java.lang.String = this.parseString(din, true, `type`)
      var child: com.badlogic.gdx.utils.JsonValue = this.parse(din, if (valueType == 0) din.readByte() else valueType)
      child.setName(key)
      child.parent$field = result
      if (prev != null) {
        child.prev$field = prev
        prev.next$field = child
      } else {
        result.child$field = child
      }
      prev = child
      c = c + 1
      if ((size > 0) && (c >= size)) {
        /* break */ ()
      } else ()
      `type` = din.readByte()
    }
    result.size$field = c
    result.last$field = prev
    return result
  }
  protected def parseData(din: java.io.DataInputStream, blockType: scala.Byte): com.badlogic.gdx.utils.JsonValue = {
    val dataType: scala.Byte = din.readByte()
    var size: scala.Int = (if (blockType == 'A') this.readUInt(din) else this.readUChar(din)).asInstanceOf[scala.Int]
    val result: com.badlogic.gdx.utils.JsonValue = new com.badlogic.gdx.utils.JsonValue(com.badlogic.gdx.utils.JsonValue.ValueType.array)
    var prev: com.badlogic.gdx.utils.JsonValue = null
    { var i: scala.Long = 0; while (i < size) { {
      val `val`: com.badlogic.gdx.utils.JsonValue = this.parse(din, dataType)
      `val`.parent$field = result
      if (prev != null) {
        prev.next$field = `val`
      } else {
        result.child$field = `val`
      }
      prev = `val`
    }; i = i + 1 } }
    result.size$field = size
    result.last$field = prev
    return result
  }
  protected def parseString(din: java.io.DataInputStream, `type`: scala.Byte): java.lang.String = {
    return this.parseString(din, false, `type`)
  }
  protected def parseString(din: java.io.DataInputStream, sOptional: scala.Boolean, `type`: scala.Byte): java.lang.String = {
    var size: scala.Long = -1
    if (`type` == 'S') {
      size = this.parseSize(din, true, -1)
    } else {
      if (`type` == 's') {
        size = this.readUChar(din).asInstanceOf[scala.Long]
      } else {
        if (sOptional) {
          size = this.parseSize(din, `type`, false, -1)
        } else ()
      }
    }
    if (size < 0) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Unrecognized data type, string expected")
    } else ()
    return if (size > 0) this.readString(din, size) else ""
  }
  protected def parseSize(din: java.io.DataInputStream, useIntOnError: scala.Boolean, defaultValue: scala.Long): scala.Long = {
    return this.parseSize(din, din.readByte(), useIntOnError, defaultValue)
  }
  protected def parseSize(din: java.io.DataInputStream, `type`: scala.Byte, useIntOnError: scala.Boolean, defaultValue: scala.Long): scala.Long = {
    if (`type` == 'i') {
      return this.readUChar(din).asInstanceOf[scala.Long]
    } else ()
    if (`type` == 'I') {
      return this.readUShort(din).asInstanceOf[scala.Long]
    } else ()
    if (`type` == 'l') {
      return this.readUInt(din).asInstanceOf[scala.Long]
    } else ()
    if (`type` == 'L') {
      return din.readLong()
    } else ()
    if (useIntOnError) {
      var result: scala.Long = (`type`.asInstanceOf[scala.Short] & 255).asInstanceOf[scala.Long] << 24
      result = result | ((din.readByte().asInstanceOf[scala.Short] & 255).asInstanceOf[scala.Long] << 16)
      result = result | ((din.readByte().asInstanceOf[scala.Short] & 255).asInstanceOf[scala.Long] << 8)
      result = result | (din.readByte().asInstanceOf[scala.Short] & 255).asInstanceOf[scala.Long]
      return result
    } else ()
    return defaultValue
  }
  protected def readUChar(din: java.io.DataInputStream): scala.Short = {
    return (din.readByte().asInstanceOf[scala.Short] & 255).asInstanceOf[scala.Short]
  }
  protected def readUShort(din: java.io.DataInputStream): scala.Int = {
    return din.readShort().asInstanceOf[scala.Int] & 65535
  }
  protected def readUInt(din: java.io.DataInputStream): scala.Long = {
    return din.readInt().asInstanceOf[scala.Long] & -1
  }
  protected def readString(din: java.io.DataInputStream, size: scala.Long): java.lang.String = {
    val data: scala.Array[scala.Byte] = new Array[scala.Byte](size.asInstanceOf[scala.Int])
    din.readFully(data)
    return new java.lang.String(data, "UTF-8")
  }
}