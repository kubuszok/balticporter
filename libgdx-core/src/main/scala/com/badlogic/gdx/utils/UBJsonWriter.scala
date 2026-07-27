package com.badlogic.gdx.utils

class UBJsonWriter extends java.io.Closeable {
  var out: java.io.DataOutputStream = null.asInstanceOf[java.io.DataOutputStream]
  private var current: com.badlogic.gdx.utils.UBJsonWriter#JsonObject = null.asInstanceOf[com.badlogic.gdx.utils.UBJsonWriter#JsonObject]
  private var named: scala.Boolean = false
  private final val stack: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.UBJsonWriter#JsonObject] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.UBJsonWriter#JsonObject]]
  def this(out$arg: java.io.OutputStream) = {
    this()
    var out: java.io.OutputStream = out$arg
    if (!out.isInstanceOf[java.io.DataOutputStream]) {
      out = new java.io.DataOutputStream(out)
    } else ()
    this.out = out.asInstanceOf[java.io.DataOutputStream]
  }
  def `object`(): UBJsonWriter = {
    if (this.current != null) {
      if (!this.current.array) {
        if (!this.named) {
          throw new java.lang.IllegalStateException("Name must be set.")
        } else ()
        this.named = false
      } else ()
    } else ()
    this.stack.add({
      this.current = new JsonObject(false)
      this.current
    })
    return this
  }
  def `object`(name: java.lang.String): UBJsonWriter = {
    this.name(name).`object`()
    return this
  }
  def array(): UBJsonWriter = {
    if (this.current != null) {
      if (!this.current.array) {
        if (!this.named) {
          throw new java.lang.IllegalStateException("Name must be set.")
        } else ()
        this.named = false
      } else ()
    } else ()
    this.stack.add({
      this.current = new JsonObject(true)
      this.current
    })
    return this
  }
  def array(name: java.lang.String): UBJsonWriter = {
    this.name(name).array()
    return this
  }
  def name(name: java.lang.String): UBJsonWriter = {
    if ((this.current == null) || this.current.array) {
      throw new java.lang.IllegalStateException("Current item must be an object.")
    } else ()
    val bytes: scala.Array[scala.Byte] = name.getBytes("UTF-8")
    if (bytes.length <= java.lang.Byte.MAX_VALUE) {
      this.out.writeByte('i')
      this.out.writeByte(bytes.length)
    } else {
      if (bytes.length <= java.lang.Short.MAX_VALUE) {
        this.out.writeByte('I')
        this.out.writeShort(bytes.length)
      } else {
        this.out.writeByte('l')
        this.out.writeInt(bytes.length)
      }
    }
    this.out.write(bytes)
    this.named = true
    return this
  }
  def value(value: scala.Byte): UBJsonWriter = {
    this.checkName()
    this.out.writeByte('i')
    this.out.writeByte(value)
    return this
  }
  def value(value: scala.Short): UBJsonWriter = {
    this.checkName()
    this.out.writeByte('I')
    this.out.writeShort(value)
    return this
  }
  def value(value: scala.Int): UBJsonWriter = {
    this.checkName()
    this.out.writeByte('l')
    this.out.writeInt(value)
    return this
  }
  def value(value: scala.Long): UBJsonWriter = {
    this.checkName()
    this.out.writeByte('L')
    this.out.writeLong(value)
    return this
  }
  def value(value: scala.Float): UBJsonWriter = {
    this.checkName()
    this.out.writeByte('d')
    this.out.writeFloat(value)
    return this
  }
  def value(value: scala.Double): UBJsonWriter = {
    this.checkName()
    this.out.writeByte('D')
    this.out.writeDouble(value)
    return this
  }
  def value(value: scala.Boolean): UBJsonWriter = {
    this.checkName()
    this.out.writeByte(if (value) 'T' else 'F')
    return this
  }
  def value(value: scala.Char): UBJsonWriter = {
    this.checkName()
    this.out.writeByte('I')
    this.out.writeChar(value)
    return this
  }
  def value(value: java.lang.String): UBJsonWriter = {
    this.checkName()
    val bytes: scala.Array[scala.Byte] = value.getBytes("UTF-8")
    this.out.writeByte('S')
    if (bytes.length <= java.lang.Byte.MAX_VALUE) {
      this.out.writeByte('i')
      this.out.writeByte(bytes.length)
    } else {
      if (bytes.length <= java.lang.Short.MAX_VALUE) {
        this.out.writeByte('I')
        this.out.writeShort(bytes.length)
      } else {
        this.out.writeByte('l')
        this.out.writeInt(bytes.length)
      }
    }
    this.out.write(bytes)
    return this
  }
  def value(values: scala.Array[scala.Byte]): UBJsonWriter = {
    this.array()
    this.out.writeByte('$')
    this.out.writeByte('i')
    this.out.writeByte('#')
    this.value(values.length);
    { var i: scala.Int = 0; val n: scala.Int = values.length; while (i < n) { {
      this.out.writeByte(values(i))
    }; i = i + 1 } }
    this.pop(true)
    return this
  }
  def value(values: scala.Array[scala.Short]): UBJsonWriter = {
    this.array()
    this.out.writeByte('$')
    this.out.writeByte('I')
    this.out.writeByte('#')
    this.value(values.length);
    { var i: scala.Int = 0; val n: scala.Int = values.length; while (i < n) { {
      this.out.writeShort(values(i))
    }; i = i + 1 } }
    this.pop(true)
    return this
  }
  def value(values: scala.Array[scala.Int]): UBJsonWriter = {
    this.array()
    this.out.writeByte('$')
    this.out.writeByte('l')
    this.out.writeByte('#')
    this.value(values.length);
    { var i: scala.Int = 0; val n: scala.Int = values.length; while (i < n) { {
      this.out.writeInt(values(i))
    }; i = i + 1 } }
    this.pop(true)
    return this
  }
  def value(values: scala.Array[scala.Long]): UBJsonWriter = {
    this.array()
    this.out.writeByte('$')
    this.out.writeByte('L')
    this.out.writeByte('#')
    this.value(values.length);
    { var i: scala.Int = 0; val n: scala.Int = values.length; while (i < n) { {
      this.out.writeLong(values(i))
    }; i = i + 1 } }
    this.pop(true)
    return this
  }
  def value(values: scala.Array[scala.Float]): UBJsonWriter = {
    this.array()
    this.out.writeByte('$')
    this.out.writeByte('d')
    this.out.writeByte('#')
    this.value(values.length);
    { var i: scala.Int = 0; val n: scala.Int = values.length; while (i < n) { {
      this.out.writeFloat(values(i))
    }; i = i + 1 } }
    this.pop(true)
    return this
  }
  def value(values: scala.Array[scala.Double]): UBJsonWriter = {
    this.array()
    this.out.writeByte('$')
    this.out.writeByte('D')
    this.out.writeByte('#')
    this.value(values.length);
    { var i: scala.Int = 0; val n: scala.Int = values.length; while (i < n) { {
      this.out.writeDouble(values(i))
    }; i = i + 1 } }
    this.pop(true)
    return this
  }
  def value(values: scala.Array[scala.Boolean]): UBJsonWriter = {
    this.array();
    { var i: scala.Int = 0; val n: scala.Int = values.length; while (i < n) { {
      this.out.writeByte(if (values(i)) 'T' else 'F')
    }; i = i + 1 } }
    this.pop()
    return this
  }
  def value(values: scala.Array[scala.Char]): UBJsonWriter = {
    this.array()
    this.out.writeByte('$')
    this.out.writeByte('C')
    this.out.writeByte('#')
    this.value(values.length);
    { var i: scala.Int = 0; val n: scala.Int = values.length; while (i < n) { {
      this.out.writeChar(values(i))
    }; i = i + 1 } }
    this.pop(true)
    return this
  }
  def value(values: scala.Array[java.lang.String]): UBJsonWriter = {
    this.array()
    this.out.writeByte('$')
    this.out.writeByte('S')
    this.out.writeByte('#')
    this.value(values.length);
    { var i: scala.Int = 0; val n: scala.Int = values.length; while (i < n) { {
      val bytes: scala.Array[scala.Byte] = values(i).getBytes("UTF-8")
      if (bytes.length <= java.lang.Byte.MAX_VALUE) {
        this.out.writeByte('i')
        this.out.writeByte(bytes.length)
      } else {
        if (bytes.length <= java.lang.Short.MAX_VALUE) {
          this.out.writeByte('I')
          this.out.writeShort(bytes.length)
        } else {
          this.out.writeByte('l')
          this.out.writeInt(bytes.length)
        }
      }
      this.out.write(bytes)
    }; i = i + 1 } }
    this.pop(true)
    return this
  }
  def value(value: com.badlogic.gdx.utils.JsonValue): UBJsonWriter = {
    if (value.isObject()) {
      if (value.name$field != null) {
        this.`object`(value.name$field)
      } else {
        this.`object`()
      };
      { var child: com.badlogic.gdx.utils.JsonValue = value.child$field; while (child != null) { {
        this.value(child)
      }; child = child.next$field } }
      this.pop()
    } else {
      if (value.isArray()) {
        if (value.name$field != null) {
          this.array(value.name$field)
        } else {
          this.array()
        };
        { var child: com.badlogic.gdx.utils.JsonValue = value.child$field; while (child != null) { {
          this.value(child)
        }; child = child.next$field } }
        this.pop()
      } else {
        if (value.isBoolean()) {
          if (value.name$field != null) {
            this.name(value.name$field)
          } else ()
          this.value(value.asBoolean())
        } else {
          if (value.isDouble()) {
            if (value.name$field != null) {
              this.name(value.name$field)
            } else ()
            this.value(value.asDouble())
          } else {
            if (value.isLong()) {
              if (value.name$field != null) {
                this.name(value.name$field)
              } else ()
              this.value(value.asLong())
            } else {
              if (value.isString()) {
                if (value.name$field != null) {
                  this.name(value.name$field)
                } else ()
                this.value(value.asString())
              } else {
                if (value.isNull()) {
                  if (value.name$field != null) {
                    this.name(value.name$field)
                  } else ()
                  this.value()
                } else {
                  throw new java.io.IOException("Unhandled JsonValue type")
                }
              }
            }
          }
        }
      }
    }
    return this
  }
  def value(`object`: java.lang.Object): UBJsonWriter = {
    if (`object` == null) {
      return this.value()
    } else {
      if (`object`.isInstanceOf[java.lang.Number]) {
        val number: java.lang.Number = `object`.asInstanceOf[java.lang.Number].asInstanceOf[java.lang.Number]
        if (`object`.isInstanceOf[java.lang.Byte]) {
          return this.value(number.byteValue())
        } else ()
        if (`object`.isInstanceOf[java.lang.Short]) {
          return this.value(number.shortValue())
        } else ()
        if (`object`.isInstanceOf[java.lang.Integer]) {
          return this.value(number.intValue())
        } else ()
        if (`object`.isInstanceOf[java.lang.Long]) {
          return this.value(number.longValue())
        } else ()
        if (`object`.isInstanceOf[java.lang.Float]) {
          return this.value(number.floatValue())
        } else ()
        if (`object`.isInstanceOf[java.lang.Double]) {
          return this.value(number.doubleValue())
        } else ()
      } else {
        if (`object`.isInstanceOf[java.lang.Character]) {
          return this.value(`object`.asInstanceOf[java.lang.Character].charValue())
        } else {
          if (`object`.isInstanceOf[java.lang.CharSequence]) {
            return this.value(`object`.toString())
          } else {
            throw new java.io.IOException("Unknown object type.")
          }
        }
      }
    }
    return this
  }
  def value(): UBJsonWriter = {
    this.checkName()
    this.out.writeByte('Z')
    return this
  }
  def set(name: java.lang.String, value: scala.Byte): UBJsonWriter = {
    return this.name(name).value(value)
  }
  def set(name: java.lang.String, value: scala.Short): UBJsonWriter = {
    return this.name(name).value(value)
  }
  def set(name: java.lang.String, value: scala.Int): UBJsonWriter = {
    return this.name(name).value(value)
  }
  def set(name: java.lang.String, value: scala.Long): UBJsonWriter = {
    return this.name(name).value(value)
  }
  def set(name: java.lang.String, value: scala.Float): UBJsonWriter = {
    return this.name(name).value(value)
  }
  def set(name: java.lang.String, value: scala.Double): UBJsonWriter = {
    return this.name(name).value(value)
  }
  def set(name: java.lang.String, value: scala.Boolean): UBJsonWriter = {
    return this.name(name).value(value)
  }
  def set(name: java.lang.String, value: scala.Char): UBJsonWriter = {
    return this.name(name).value(value)
  }
  def set(name: java.lang.String, value: java.lang.String): UBJsonWriter = {
    return this.name(name).value(value)
  }
  def set(name: java.lang.String, value: scala.Array[scala.Byte]): UBJsonWriter = {
    return this.name(name).value(value)
  }
  def set(name: java.lang.String, value: scala.Array[scala.Short]): UBJsonWriter = {
    return this.name(name).value(value)
  }
  def set(name: java.lang.String, value: scala.Array[scala.Int]): UBJsonWriter = {
    return this.name(name).value(value)
  }
  def set(name: java.lang.String, value: scala.Array[scala.Long]): UBJsonWriter = {
    return this.name(name).value(value)
  }
  def set(name: java.lang.String, value: scala.Array[scala.Float]): UBJsonWriter = {
    return this.name(name).value(value)
  }
  def set(name: java.lang.String, value: scala.Array[scala.Double]): UBJsonWriter = {
    return this.name(name).value(value)
  }
  def set(name: java.lang.String, value: scala.Array[scala.Boolean]): UBJsonWriter = {
    return this.name(name).value(value)
  }
  def set(name: java.lang.String, value: scala.Array[scala.Char]): UBJsonWriter = {
    return this.name(name).value(value)
  }
  def set(name: java.lang.String, value: scala.Array[java.lang.String]): UBJsonWriter = {
    return this.name(name).value(value)
  }
  def set(name: java.lang.String): UBJsonWriter = {
    return this.name(name).value()
  }
  private def checkName(): scala.Unit = {
    if (this.current != null) {
      if (!this.current.array) {
        if (!this.named) {
          throw new java.lang.IllegalStateException("Name must be set.")
        } else ()
        this.named = false
      } else ()
    } else ()
  }
  def pop(): UBJsonWriter = {
    return this.pop(false)
  }
  def pop(silent: scala.Boolean): UBJsonWriter = {
    if (this.named) {
      throw new java.lang.IllegalStateException("Expected an object, array, or value since a name was set.")
    } else ()
    if (silent) {
      this.stack.pop()
    } else {
      this.stack.pop().close()
    }
    this.current = if (this.stack.size == 0) null.asInstanceOf[com.badlogic.gdx.utils.UBJsonWriter#JsonObject] else this.stack.peek()
    return this
  }
  def flush(): scala.Unit = {
    this.out.flush()
  }
  def close(): scala.Unit = {
    while (this.stack.size > 0) {
      this.pop()
    }
    this.out.close()
  }
  class JsonObject {
    var array: scala.Boolean = false
    def this(array: scala.Boolean) = {
      this()
      this.array = array
      out.writeByte(if (array) '[' else '{')
    }
    def close(): scala.Unit = {
      out.writeByte(if (this.array) ']' else '}')
    }
  }
}