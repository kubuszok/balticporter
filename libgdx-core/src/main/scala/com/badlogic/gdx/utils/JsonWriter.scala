package com.badlogic.gdx.utils

class JsonWriter extends java.io.Writer {
  var writer: java.io.Writer = null.asInstanceOf[java.io.Writer]
  private final val stack: com.badlogic.gdx.utils.IntArray = new com.badlogic.gdx.utils.IntArray()
  private var current: scala.Int = 0
  private var named: scala.Boolean = false
  private var outputType: com.badlogic.gdx.utils.JsonWriter.OutputType = com.badlogic.gdx.utils.JsonWriter.OutputType.json
  private var quoteLongValues: scala.Boolean = false
  def this(writer: java.io.Writer) = {
    this()
    this.writer = writer
  }
  def setWriter(writer: java.io.Writer): scala.Unit = {
    this.writer = writer
  }
  def getWriter(): java.io.Writer = {
    return this.writer
  }
  def setOutputType(outputType: com.badlogic.gdx.utils.JsonWriter.OutputType): scala.Unit = {
    this.outputType = outputType
  }
  def setQuoteLongValues(quoteLongValues: scala.Boolean): scala.Unit = {
    this.quoteLongValues = quoteLongValues
  }
  def `object`(): JsonWriter = {
    this.requireCommaOrName()
    this.writer.write('{')
    this.stack.add(this.current)
    this.current = JsonWriter.object$field
    return this
  }
  def array(): JsonWriter = {
    this.requireCommaOrName()
    this.writer.write('[')
    this.stack.add(this.current)
    this.current = JsonWriter.array$field
    return this
  }
  def value(value$arg: java.lang.Object): JsonWriter = {
    var value: java.lang.Object = value$arg
    if (this.quoteLongValues && (((value.isInstanceOf[java.lang.Long] || value.isInstanceOf[java.lang.Double]) || value.isInstanceOf[java.math.BigDecimal]) || value.isInstanceOf[java.math.BigInteger])) {
      value = value.toString()
    } else {
      if (value.isInstanceOf[java.lang.Number]) {
        val number: java.lang.Number = value.asInstanceOf[java.lang.Number]
        val longValue: scala.Long = number.longValue()
        if (number.doubleValue() == longValue) {
          value = longValue
        } else ()
      } else ()
    }
    this.requireCommaOrName()
    this.writer.write(this.outputType.quoteValue(value))
    return this
  }
  def value(value: java.lang.String): JsonWriter = {
    this.requireCommaOrName()
    this.writer.write(this.outputType.quoteValue(value))
    return this
  }
  def value(value: scala.Boolean): JsonWriter = {
    this.requireCommaOrName()
    this.writer.write(if (value) "true" else "false")
    return this
  }
  def value(value: scala.Int): JsonWriter = {
    this.requireCommaOrName()
    this.writer.write(java.lang.Integer.toString(value))
    return this
  }
  def value(value: scala.Long): JsonWriter = {
    if (this.quoteLongValues) {
      this.value(java.lang.Long.toString(value))
    } else {
      this.requireCommaOrName()
      this.writer.write(java.lang.Long.toString(value))
    }
    return this
  }
  def value(value: scala.Float): JsonWriter = {
    this.requireCommaOrName()
    this.writer.write(java.lang.Float.toString(value))
    return this
  }
  def value(value: scala.Double): JsonWriter = {
    if (this.quoteLongValues) {
      this.value(java.lang.Double.toString(value))
    } else {
      this.requireCommaOrName()
      this.writer.write(java.lang.Double.toString(value))
    }
    return this
  }
  def json(json: java.lang.String): JsonWriter = {
    this.requireCommaOrName()
    this.writer.write(json)
    return this
  }
  private def requireCommaOrName(): scala.Unit = {
    if ((this.current & JsonWriter.isObject) != 0) {
      if (!this.named) {
        throw new java.lang.IllegalStateException("Name must be set.")
      } else ()
      this.named = false
    } else {
      if ((this.current & JsonWriter.needsComma) != 0) {
        this.writer.write(',')
      } else {
        if (this.current != JsonWriter.none) {
          this.current = this.current | JsonWriter.needsComma
        } else ()
      }
    }
  }
  def name(name: java.lang.String): JsonWriter = {
    this.nameValue(name)
    this.named = true
    return this
  }
  private def nameValue(name: java.lang.String): scala.Unit = {
    if ((this.current & JsonWriter.isObject) == 0) {
      throw new java.lang.IllegalStateException("Current item must be an object.")
    } else ()
    if ((this.current & JsonWriter.needsComma) != 0) {
      this.writer.write(',')
    } else {
      this.current = this.current | JsonWriter.needsComma
    }
    this.writer.write(this.outputType.quoteName(name))
    this.writer.write(':')
  }
  def `object`(name: java.lang.String): JsonWriter = {
    this.nameValue(name)
    this.writer.write('{')
    this.stack.add(this.current)
    this.current = JsonWriter.object$field
    return this
  }
  def array(name: java.lang.String): JsonWriter = {
    this.nameValue(name)
    this.writer.write('[')
    this.stack.add(this.current)
    this.current = JsonWriter.array$field
    return this
  }
  def set(name: java.lang.String, value: java.lang.Object): JsonWriter = {
    this.name(name)
    this.value(value)
    return this
  }
  def set(name: java.lang.String, value: java.lang.String): JsonWriter = {
    this.nameValue(name)
    this.writer.write(this.outputType.quoteValue(value))
    return this
  }
  def set(name: java.lang.String, value: scala.Boolean): JsonWriter = {
    this.nameValue(name)
    this.writer.write(if (value) "true" else "false")
    return this
  }
  def set(name: java.lang.String, value: scala.Int): JsonWriter = {
    this.nameValue(name)
    this.writer.write(java.lang.Integer.toString(value))
    return this
  }
  def set(name: java.lang.String, value: scala.Long): JsonWriter = {
    if (this.quoteLongValues) {
      this.set(name, java.lang.Long.toString(value))
    } else {
      this.nameValue(name)
      this.writer.write(java.lang.Long.toString(value))
    }
    return this
  }
  def set(name: java.lang.String, value: scala.Float): JsonWriter = {
    this.nameValue(name)
    this.writer.write(java.lang.Float.toString(value))
    return this
  }
  def set(name: java.lang.String, value: scala.Double): JsonWriter = {
    if (this.quoteLongValues) {
      this.set(name, java.lang.Double.toString(value))
    } else {
      this.nameValue(name)
      this.writer.write(java.lang.Double.toString(value))
    }
    return this
  }
  def json(name: java.lang.String, json: java.lang.String): JsonWriter = {
    this.nameValue(name)
    this.writer.write(json)
    return this
  }
  def pop(): JsonWriter = {
    if (this.named) {
      throw new java.lang.IllegalStateException("Expected an object, array, or value since a name was set.")
    } else ()
    this.writer.write((this.current >> 1).asInstanceOf[scala.Char])
    this.current = if (this.stack.size == 0) JsonWriter.none else this.stack.items({ this.stack.size -= 1; this.stack.size })
    return this
  }
  def write(cbuf: scala.Array[scala.Char], off: scala.Int, len: scala.Int): scala.Unit = {
    this.writer.write(cbuf, off, len)
  }
  def flush(): scala.Unit = {
    this.writer.flush()
  }
  def close(): scala.Unit = {
    while (this.stack.size > 0) {
      this.pop()
    }
    this.writer.close()
  }
}
object JsonWriter {
  private final val none: scala.Int = 0
  private final val needsComma: scala.Int = 1
  final val object$field: scala.Int = '}' << 1
  final val array$field: scala.Int = ']' << 1
  private final val isObject: scala.Int = 64
  sealed abstract class OutputType {
    def quoteValue(value: java.lang.Object): java.lang.String = {
      if (value == null) {
        return "null"
      } else ()
      var string: java.lang.String = value.toString()
      if (value.isInstanceOf[java.lang.Number] || value.isInstanceOf[java.lang.Boolean]) {
        return string
      } else ()
      var quote: scala.Boolean = false;
      { var i: scala.Int = 0; while (i < string.length()) { {
        string.charAt(i) match {
          case '\\' | '\r' | '\n' | '\t' => {
            string = com.badlogic.gdx.utils.JsonWriter.OutputType.escape(string, i)
            quote = true
          }
          case '\"' => {
            quote = true
          }
        }
      }; i = i + 1 } }
      if ((((((this == com.badlogic.gdx.utils.JsonWriter.OutputType.minimal) && (!string.equals("true"))) && (!string.equals("false"))) && (!string.equals("null"))) && (!string.contains("//"))) && (!string.contains("/*"))) {
        val length: scala.Int = string.length()
        if (((length > 0) && (string.charAt(length - 1) != ' ')) && com.badlogic.gdx.utils.JsonWriter.OutputType.minimalValuePattern.matcher(string).matches()) {
          return string
        } else ()
      } else ()
      return if (quote) com.badlogic.gdx.utils.JsonWriter.OutputType.escapeQuote(string) else ('\"' + string) + '\"'
    }
    def quoteName(value$arg: java.lang.String): java.lang.String = {
      var value: java.lang.String = value$arg
      var quote: scala.Boolean = false;
      { var i: scala.Int = 0; while (i < value.length()) { {
        value.charAt(i) match {
          case '\\' | '\r' | '\n' | '\t' => {
            value = com.badlogic.gdx.utils.JsonWriter.OutputType.escape(value, i)
            quote = true
          }
          case '\"' => {
            quote = true
          }
        }
      }; i = i + 1 } }
      this match {
        case com.badlogic.gdx.utils.JsonWriter.OutputType.minimal => {
          if (((!value.contains("//")) && (!value.contains("/*"))) && com.badlogic.gdx.utils.JsonWriter.OutputType.minimalNamePattern.matcher(value).matches()) {
            return value
          } else ()
          if (com.badlogic.gdx.utils.JsonWriter.OutputType.javascriptPattern.matcher(value).matches()) {
            return value
          } else ()
        }
        case com.badlogic.gdx.utils.JsonWriter.OutputType.javascript => {
          if (com.badlogic.gdx.utils.JsonWriter.OutputType.javascriptPattern.matcher(value).matches()) {
            return value
          } else ()
        }
      }
      return if (quote) com.badlogic.gdx.utils.JsonWriter.OutputType.escapeQuote(value) else ('\"' + value) + '\"'
    }
  }
  object OutputType {
    case object json extends OutputType
    case object javascript extends OutputType
    case object minimal extends OutputType
    def values(): Array[OutputType] = Array(json, javascript, minimal)
    private var javascriptPattern: java.util.regex.Pattern = java.util.regex.Pattern.compile("^[a-zA-Z_$][a-zA-Z_$0-9]*$")
    private var minimalNamePattern: java.util.regex.Pattern = java.util.regex.Pattern.compile("^[^\":,}/ ][^:]*$")
    private var minimalValuePattern: java.util.regex.Pattern = java.util.regex.Pattern.compile("^[^\":,{\\[\\]/ ][^}\\],]*$")
    private def escape(value: java.lang.String, i$arg: scala.Int): java.lang.String = {
      var i: scala.Int = i$arg
      val buffer: java.lang.StringBuilder = new java.lang.StringBuilder(value.length() + 6)
      buffer.append(value, 0, i);
      { ; while (i < value.length()) { {
        val c: scala.Char = value.charAt(i)
        c match {
          case '\\' => {
            buffer.append("\\\\")
          }
          case '\r' => {
            buffer.append("\\r")
          }
          case '\n' => {
            buffer.append("\\n")
          }
          case '\t' => {
            buffer.append("\\t")
          }
          case _ => {
            buffer.append(c)
          }
        }
      }; i = i + 1 } }
      return buffer.toString()
    }
    private def escapeQuote(value: java.lang.String): java.lang.String = {
      val buffer: java.lang.StringBuilder = new java.lang.StringBuilder(value.length() + 6)
      buffer.append('\"');
      { var i: scala.Int = 0; while (i < value.length()) { {
        val c: scala.Char = value.charAt(i)
        if (c == '\"') {
          buffer.append("\\\"")
        } else {
          buffer.append(c)
        }
      }; i = i + 1 } }
      buffer.append('\"')
      return buffer.toString()
    }
  }
}