package com.badlogic.gdx.utils

class JsonString {
  var buffer: java.lang.StringBuilder = null.asInstanceOf[java.lang.StringBuilder]
  private final val stack: com.badlogic.gdx.utils.IntArray = new com.badlogic.gdx.utils.IntArray()
  private var current: scala.Int = 0
  private var named: scala.Boolean = false
  private var outputType: com.badlogic.gdx.utils.JsonWriter.OutputType = com.badlogic.gdx.utils.JsonWriter.OutputType.json
  private var quoteLongValues: scala.Boolean = false
  def this(initialBufferSize: scala.Int) = {
    this()
    this.buffer = new java.lang.StringBuilder(initialBufferSize)
  }
  def getBuffer(): java.lang.StringBuilder = {
    return this.buffer
  }
  def setOutputType(outputType: com.badlogic.gdx.utils.JsonWriter.OutputType): scala.Unit = {
    this.outputType = outputType
  }
  def setQuoteLongValues(quoteLongValues: scala.Boolean): scala.Unit = {
    this.quoteLongValues = quoteLongValues
  }
  def `object`(): JsonString = {
    this.requireCommaOrName()
    this.buffer.append('{')
    this.stack.add(this.current)
    this.current = JsonString.object$field
    return this
  }
  def array(): JsonString = {
    this.requireCommaOrName()
    this.buffer.append('[')
    this.stack.add(this.current)
    this.current = JsonString.array$field
    return this
  }
  def value(value$arg: java.lang.Object): JsonString = {
    var value: java.lang.Object = value$arg
    if (this.quoteLongValues && (((value.isInstanceOf[java.lang.Long] || value.isInstanceOf[java.lang.Double]) || value.isInstanceOf[java.math.BigDecimal]) || value.isInstanceOf[java.math.BigInteger])) {
      value = value.toString()
    } else {
      if (value.isInstanceOf[java.lang.Number]) {
        val number: java.lang.Number = value.asInstanceOf[java.lang.Number]
        val longValue: scala.Long = number.longValue()
        if (number.doubleValue() == longValue) {
          value = longValue.asInstanceOf[java.lang.Object]
        } else ()
      } else ()
    }
    this.requireCommaOrName()
    this.buffer.append(this.outputType.quoteValue(value))
    return this
  }
  def value(value: java.lang.String): JsonString = {
    this.requireCommaOrName()
    this.buffer.append(this.outputType.quoteValue(value))
    return this
  }
  def value(value: scala.Boolean): JsonString = {
    this.requireCommaOrName()
    this.buffer.append(value)
    return this
  }
  def value(value: scala.Int): JsonString = {
    this.requireCommaOrName()
    this.buffer.append(value)
    return this
  }
  def value(value: scala.Long): JsonString = {
    if (this.quoteLongValues) {
      this.value(java.lang.Long.toString(value))
    } else {
      this.requireCommaOrName()
      this.buffer.append(value)
    }
    return this
  }
  def value(value: scala.Float): JsonString = {
    this.requireCommaOrName()
    this.buffer.append(value)
    return this
  }
  def value(value: scala.Double): JsonString = {
    if (this.quoteLongValues) {
      this.value(java.lang.Double.toString(value))
    } else {
      this.requireCommaOrName()
      this.buffer.append(value)
    }
    return this
  }
  def json(json: java.lang.String): JsonString = {
    this.requireCommaOrName()
    this.buffer.append(json)
    return this
  }
  private def requireCommaOrName(): scala.Unit = {
    if ((this.current & JsonString.isObject) != 0) {
      if (!this.named) {
        throw new java.lang.IllegalStateException("Name must be set.")
      } else ()
      this.named = false
    } else {
      if ((this.current & JsonString.needsComma) != 0) {
        this.buffer.append(',')
      } else {
        if (this.current != JsonString.none) {
          this.current = this.current | JsonString.needsComma
        } else ()
      }
    }
  }
  def name(name: java.lang.String): JsonString = {
    this.nameValue(name)
    this.named = true
    return this
  }
  private def nameValue(name: java.lang.String): scala.Unit = {
    if ((this.current & JsonString.isObject) == 0) {
      throw new java.lang.IllegalStateException("Current item must be an object.")
    } else ()
    if ((this.current & JsonString.needsComma) != 0) {
      this.buffer.append(',')
    } else {
      this.current = this.current | JsonString.needsComma
    }
    this.buffer.append(this.outputType.quoteName(name))
    this.buffer.append(':')
  }
  def `object`(name: java.lang.String): JsonString = {
    this.nameValue(name)
    this.buffer.append('{')
    this.stack.add(this.current)
    this.current = JsonString.object$field
    return this
  }
  def array(name: java.lang.String): JsonString = {
    this.nameValue(name)
    this.buffer.append('[')
    this.stack.add(this.current)
    this.current = JsonString.array$field
    return this
  }
  def set(name: java.lang.String, value: java.lang.Object): JsonString = {
    this.name(name)
    this.value(value)
    return this
  }
  def set(name: java.lang.String, value: java.lang.String): JsonString = {
    this.nameValue(name)
    this.buffer.append(this.outputType.quoteValue(value))
    return this
  }
  def set(name: java.lang.String, value: scala.Boolean): JsonString = {
    this.nameValue(name)
    this.buffer.append(value)
    return this
  }
  def set(name: java.lang.String, value: scala.Int): JsonString = {
    this.nameValue(name)
    this.buffer.append(value)
    return this
  }
  def set(name: java.lang.String, value: scala.Long): JsonString = {
    if (this.quoteLongValues) {
      this.set(name, java.lang.Long.toString(value))
    } else {
      this.nameValue(name)
      this.buffer.append(value)
    }
    return this
  }
  def set(name: java.lang.String, value: scala.Float): JsonString = {
    this.nameValue(name)
    this.buffer.append(value)
    return this
  }
  def set(name: java.lang.String, value: scala.Double): JsonString = {
    if (this.quoteLongValues) {
      this.set(name, java.lang.Double.toString(value))
    } else {
      this.nameValue(name)
      this.buffer.append(value)
    }
    return this
  }
  def json(name: java.lang.String, json: java.lang.String): JsonString = {
    this.nameValue(name)
    this.buffer.append(json)
    return this
  }
  def pop(): JsonString = {
    if (this.named) {
      throw new java.lang.IllegalStateException("Expected an object, array, or value since a name was set.")
    } else ()
    this.buffer.append((this.current >> 1).asInstanceOf[scala.Char].asInstanceOf[scala.Char])
    this.current = if (this.stack.size == 0) JsonString.none else this.stack.items({ this.stack.size -= 1; this.stack.size })
    return this
  }
  def close(): JsonString = {
    while (this.stack.size > 0) {
      this.pop()
    }
    return this
  }
  def reset(): scala.Unit = {
    this.buffer.setLength(0)
    this.stack.size = 0
    this.current = JsonString.none
    this.named = false
  }
  def toString(): java.lang.String = {
    return this.buffer.toString()
  }
}
object JsonString {
  private final val none: scala.Int = 0
  private final val needsComma: scala.Int = 1
  final val object$field: scala.Int = '}' << 1
  final val array$field: scala.Int = ']' << 1
  private final val isObject: scala.Int = 64
}