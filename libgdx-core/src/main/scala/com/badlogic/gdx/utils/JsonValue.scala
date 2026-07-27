package com.badlogic.gdx.utils

class JsonValue extends scala.collection.Iterable[JsonValue] {
  var type$field: com.badlogic.gdx.utils.JsonValue.ValueType = null.asInstanceOf[com.badlogic.gdx.utils.JsonValue.ValueType]
  private var stringValue: java.lang.String = null.asInstanceOf[java.lang.String]
  private var doubleValue: scala.Double = 0.0
  private var longValue: scala.Long = 0L
  var name$field: java.lang.String = null.asInstanceOf[java.lang.String]
  var child$field: JsonValue = null.asInstanceOf[JsonValue]
  var last$field: JsonValue = null.asInstanceOf[JsonValue]
  var parent$field: JsonValue = null.asInstanceOf[JsonValue]
  var next$field: JsonValue = null.asInstanceOf[JsonValue]
  var prev$field: JsonValue = null.asInstanceOf[JsonValue]
  var size$field: scala.Int = 0
  def this(`type`: com.badlogic.gdx.utils.JsonValue.ValueType) = {
    this()
    this.type$field = `type`
  }
  def this(value: java.lang.String) = {
    this()
    this.set(value)
  }
  def this(value: scala.Double) = {
    this()
    this.set(value, null)
  }
  def this(value: scala.Long) = {
    this()
    this.set(value, null)
  }
  def this(value: scala.Double, stringValue: java.lang.String) = {
    this()
    this.set(value, stringValue)
  }
  def this(value: scala.Long, stringValue: java.lang.String) = {
    this()
    this.set(value, stringValue)
  }
  def this(value: scala.Boolean) = {
    this()
    this.set(value)
  }
  private def this(other: JsonValue, otherLast: JsonValue, parent: JsonValue) = {
    this()
    this.type$field = other.type$field
    this.stringValue = other.stringValue
    this.doubleValue = other.doubleValue
    this.longValue = other.longValue
    this.name$field = other.name$field
    this.parent$field = parent
    if (other.child$field != null) {
      this.child$field = new JsonValue(other.child$field, other.last$field, this)
    } else ()
    if (other == otherLast) {
      parent.last$field = this
    } else ()
    if ((parent != null) && (other.next$field != null)) {
      this.next$field = new JsonValue(other.next$field, otherLast, parent)
      this.next$field.prev$field = this
    } else ()
    this.size$field = other.size$field
  }
  def this(value: JsonValue) = {
    this(value, null, null)
  }
  def get(index$arg: scala.Int): JsonValue = {
    var index: scala.Int = index$arg
    if (index == (this.size$field - 1)) {
      return this.last$field
    } else ()
    var current: JsonValue = this.child$field
    while ((current != null) && (index > 0)) {
      index = index - 1
      current = current.next$field
    }
    return current
  }
  def get(name: java.lang.String): JsonValue = {
    var current: JsonValue = this.child$field
    while ((current != null) && ((current.name$field == null) || (!current.name$field.equals(name)))) {
      current = current.next$field
    }
    return current
  }
  def getIgnoreCase(name: java.lang.String): JsonValue = {
    var current: JsonValue = this.child$field
    while ((current != null) && ((current.name$field == null) || (!current.name$field.equalsIgnoreCase(name)))) {
      current = current.next$field
    }
    return current
  }
  def has(name: java.lang.String): scala.Boolean = {
    return this.get(name) != null
  }
  def iterator(name: java.lang.String): com.badlogic.gdx.utils.JsonValue#JsonIterator = {
    val current: JsonValue = this.get(name)
    if (current == null) {
      val iter: com.badlogic.gdx.utils.JsonValue#JsonIterator = new JsonIterator()
      iter.entry = null
      return iter
    } else ()
    return current.iterator()
  }
  def require(index: scala.Int): JsonValue = {
    val current: JsonValue = this.get(index)
    if (current == null) {
      throw new java.lang.IllegalArgumentException("Child not found with index: " + index)
    } else ()
    return current
  }
  def require(name: java.lang.String): JsonValue = {
    val current: JsonValue = this.get(name)
    if (current == null) {
      throw new java.lang.IllegalArgumentException("Child not found with name: " + name)
    } else ()
    return current
  }
  def remove(index: scala.Int): JsonValue = {
    var child: JsonValue = this.get(index)
    if (child == null) {
      return null
    } else ()
    if (this.last$field == child) {
      this.last$field = child.prev$field
    } else ()
    if (child.prev$field == null) {
      this.child$field = child.next$field
      if (this.child$field != null) {
        this.child$field.prev$field = null
      } else ()
    } else {
      child.prev$field.next$field = child.next$field
      if (child.next$field != null) {
        child.next$field.prev$field = child.prev$field
      } else ()
    }
    this.size$field = this.size$field - 1
    return child
  }
  def remove(name: java.lang.String): JsonValue = {
    var child: JsonValue = this.get(name)
    if (child == null) {
      return null
    } else ()
    if (this.last$field == child) {
      this.last$field = child.prev$field
    } else ()
    if (child.prev$field == null) {
      this.child$field = child.next$field
      if (this.child$field != null) {
        this.child$field.prev$field = null
      } else ()
    } else {
      child.prev$field.next$field = child.next$field
      if (child.next$field != null) {
        child.next$field.prev$field = child.prev$field
      } else ()
    }
    this.size$field = this.size$field - 1
    return child
  }
  def remove(): scala.Unit = {
    if (this.parent$field == null) {
      throw new java.lang.IllegalStateException()
    } else ()
    if (this.parent$field.last$field == this) {
      this.parent$field.last$field = this.prev$field
    } else ()
    if (this.prev$field == null) {
      this.parent$field.child$field = this.next$field
      if (this.parent$field.child$field != null) {
        this.parent$field.child$field.prev$field = null
      } else ()
    } else {
      this.prev$field.next$field = this.next$field
      if (this.next$field != null) {
        this.next$field.prev$field = this.prev$field
      } else ()
    }
    this.parent$field.size$field = this.parent$field.size$field - 1
  }
  def notEmpty(): scala.Boolean = {
    return this.size$field > 0
  }
  def isEmpty(): scala.Boolean = {
    return this.size$field == 0
  }
  def size(): scala.Int = {
    return this.size$field
  }
  def asString(): java.lang.String = {
    this.type$field match {
      case com.badlogic.gdx.utils.JsonValue.ValueType.stringValue => {
        return this.stringValue
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.doubleValue => {
        return if (this.stringValue != null) this.stringValue else java.lang.Double.toString(this.doubleValue)
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.longValue => {
        return if (this.stringValue != null) this.stringValue else java.lang.Long.toString(this.longValue)
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.booleanValue => {
        return if (this.longValue != 0) "true" else "false"
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.nullValue => {
        return null
      }
    }
    throw new java.lang.IllegalStateException("Value cannot be converted to string: " + this.type$field)
  }
  def asFloat(): scala.Float = {
    this.type$field match {
      case com.badlogic.gdx.utils.JsonValue.ValueType.stringValue => {
        return java.lang.Float.parseFloat(this.stringValue)
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.doubleValue => {
        return this.doubleValue.asInstanceOf[scala.Float].asInstanceOf[scala.Float]
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.longValue => {
        return this.longValue
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.booleanValue => {
        return if (this.longValue != 0) 1 else 0
      }
    }
    throw new java.lang.IllegalStateException("Value cannot be converted to float: " + this.type$field)
  }
  def asDouble(): scala.Double = {
    this.type$field match {
      case com.badlogic.gdx.utils.JsonValue.ValueType.stringValue => {
        return java.lang.Double.parseDouble(this.stringValue)
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.doubleValue => {
        return this.doubleValue
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.longValue => {
        return this.longValue
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.booleanValue => {
        return if (this.longValue != 0) 1 else 0
      }
    }
    throw new java.lang.IllegalStateException("Value cannot be converted to double: " + this.type$field)
  }
  def asLong(): scala.Long = {
    this.type$field match {
      case com.badlogic.gdx.utils.JsonValue.ValueType.stringValue => {
        return java.lang.Long.parseLong(this.stringValue)
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.doubleValue => {
        return this.doubleValue.asInstanceOf[scala.Long].asInstanceOf[scala.Long]
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.longValue => {
        return this.longValue
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.booleanValue => {
        return if (this.longValue != 0) 1 else 0
      }
    }
    throw new java.lang.IllegalStateException("Value cannot be converted to long: " + this.type$field)
  }
  def asInt(): scala.Int = {
    this.type$field match {
      case com.badlogic.gdx.utils.JsonValue.ValueType.stringValue => {
        return java.lang.Integer.parseInt(this.stringValue)
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.doubleValue => {
        return this.doubleValue.asInstanceOf[scala.Int].asInstanceOf[scala.Int]
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.longValue => {
        return this.longValue.asInstanceOf[scala.Int].asInstanceOf[scala.Int]
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.booleanValue => {
        return if (this.longValue != 0) 1 else 0
      }
    }
    throw new java.lang.IllegalStateException("Value cannot be converted to int: " + this.type$field)
  }
  def asBoolean(): scala.Boolean = {
    this.type$field match {
      case com.badlogic.gdx.utils.JsonValue.ValueType.stringValue => {
        return this.stringValue.equalsIgnoreCase("true")
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.doubleValue => {
        return this.doubleValue != 0
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.longValue => {
        return this.longValue != 0
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.booleanValue => {
        return this.longValue != 0
      }
    }
    throw new java.lang.IllegalStateException("Value cannot be converted to boolean: " + this.type$field)
  }
  def asByte(): scala.Byte = {
    this.type$field match {
      case com.badlogic.gdx.utils.JsonValue.ValueType.stringValue => {
        return java.lang.Byte.parseByte(this.stringValue)
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.doubleValue => {
        return this.doubleValue.asInstanceOf[scala.Byte].asInstanceOf[scala.Byte]
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.longValue => {
        return this.longValue.asInstanceOf[scala.Byte].asInstanceOf[scala.Byte]
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.booleanValue => {
        return if (this.longValue != 0) 1.asInstanceOf[scala.Byte] else 0
      }
    }
    throw new java.lang.IllegalStateException("Value cannot be converted to byte: " + this.type$field)
  }
  def asShort(): scala.Short = {
    this.type$field match {
      case com.badlogic.gdx.utils.JsonValue.ValueType.stringValue => {
        return java.lang.Short.parseShort(this.stringValue)
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.doubleValue => {
        return this.doubleValue.asInstanceOf[scala.Short].asInstanceOf[scala.Short]
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.longValue => {
        return this.longValue.asInstanceOf[scala.Short].asInstanceOf[scala.Short]
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.booleanValue => {
        return if (this.longValue != 0) 1.asInstanceOf[scala.Short] else 0
      }
    }
    throw new java.lang.IllegalStateException("Value cannot be converted to short: " + this.type$field)
  }
  def asChar(): scala.Char = {
    this.type$field match {
      case com.badlogic.gdx.utils.JsonValue.ValueType.stringValue => {
        return if (this.stringValue.length() == 0) 0 else this.stringValue.charAt(0)
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.doubleValue => {
        return this.doubleValue.asInstanceOf[scala.Char].asInstanceOf[scala.Char]
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.longValue => {
        return this.longValue.asInstanceOf[scala.Char].asInstanceOf[scala.Char]
      }
      case com.badlogic.gdx.utils.JsonValue.ValueType.booleanValue => {
        return if (this.longValue != 0) 1.asInstanceOf[scala.Char] else 0
      }
    }
    throw new java.lang.IllegalStateException("Value cannot be converted to char: " + this.type$field)
  }
  def asStringArray(): scala.Array[java.lang.String] = {
    if (this.type$field != com.badlogic.gdx.utils.JsonValue.ValueType.array) {
      throw new java.lang.IllegalStateException("Value is not an array: " + this.type$field)
    } else ()
    val array: scala.Array[java.lang.String] = new scala.Array[java.lang.String](this.size$field)
    var i: scala.Int = 0;
    { var value: JsonValue = this.child$field; while (value != null) { {
      var v: java.lang.String = null.asInstanceOf[java.lang.String]
      value.type$field match {
        case com.badlogic.gdx.utils.JsonValue.ValueType.stringValue => {
          v = value.stringValue
        }
        case com.badlogic.gdx.utils.JsonValue.ValueType.doubleValue => {
          v = if (this.stringValue != null) this.stringValue else java.lang.Double.toString(value.doubleValue)
        }
        case com.badlogic.gdx.utils.JsonValue.ValueType.longValue => {
          v = if (this.stringValue != null) this.stringValue else java.lang.Long.toString(value.longValue)
        }
        case com.badlogic.gdx.utils.JsonValue.ValueType.booleanValue => {
          v = if (value.longValue != 0) "true" else "false"
        }
        case com.badlogic.gdx.utils.JsonValue.ValueType.nullValue => {
          v = null
        }
        case _ => {
          throw new java.lang.IllegalStateException("Value cannot be converted to string: " + value.type$field)
        }
      }
      array(i) = v
    }; value = value.next$field; i = i + 1 } }
    return array
  }
  def asFloatArray(): scala.Array[scala.Float] = {
    if (this.type$field != com.badlogic.gdx.utils.JsonValue.ValueType.array) {
      throw new java.lang.IllegalStateException("Value is not an array: " + this.type$field)
    } else ()
    val array: scala.Array[scala.Float] = new scala.Array[scala.Float](this.size$field)
    var i: scala.Int = 0;
    { var value: JsonValue = this.child$field; while (value != null) { {
      var v: scala.Float = 0.0f
      value.type$field match {
        case com.badlogic.gdx.utils.JsonValue.ValueType.stringValue => {
          v = java.lang.Float.parseFloat(value.stringValue)
        }
        case com.badlogic.gdx.utils.JsonValue.ValueType.doubleValue => {
          v = value.doubleValue.asInstanceOf[scala.Float].asInstanceOf[scala.Float]
        }
        case com.badlogic.gdx.utils.JsonValue.ValueType.longValue => {
          v = value.longValue
        }
        case com.badlogic.gdx.utils.JsonValue.ValueType.booleanValue => {
          v = if (value.longValue != 0) 1 else 0
        }
        case _ => {
          throw new java.lang.IllegalStateException("Value cannot be converted to float: " + value.type$field)
        }
      }
      array(i) = v
    }; value = value.next$field; i = i + 1 } }
    return array
  }
  def asDoubleArray(): scala.Array[scala.Double] = {
    if (this.type$field != com.badlogic.gdx.utils.JsonValue.ValueType.array) {
      throw new java.lang.IllegalStateException("Value is not an array: " + this.type$field)
    } else ()
    val array: scala.Array[scala.Double] = new scala.Array[scala.Double](this.size$field)
    var i: scala.Int = 0;
    { var value: JsonValue = this.child$field; while (value != null) { {
      var v: scala.Double = 0.0
      value.type$field match {
        case com.badlogic.gdx.utils.JsonValue.ValueType.stringValue => {
          v = java.lang.Double.parseDouble(value.stringValue)
        }
        case com.badlogic.gdx.utils.JsonValue.ValueType.doubleValue => {
          v = value.doubleValue
        }
        case com.badlogic.gdx.utils.JsonValue.ValueType.longValue => {
          v = value.longValue
        }
        case com.badlogic.gdx.utils.JsonValue.ValueType.booleanValue => {
          v = if (value.longValue != 0) 1 else 0
        }
        case _ => {
          throw new java.lang.IllegalStateException("Value cannot be converted to double: " + value.type$field)
        }
      }
      array(i) = v
    }; value = value.next$field; i = i + 1 } }
    return array
  }
  def asLongArray(): scala.Array[scala.Long] = {
    if (this.type$field != com.badlogic.gdx.utils.JsonValue.ValueType.array) {
      throw new java.lang.IllegalStateException("Value is not an array: " + this.type$field)
    } else ()
    val array: scala.Array[scala.Long] = new scala.Array[scala.Long](this.size$field)
    var i: scala.Int = 0;
    { var value: JsonValue = this.child$field; while (value != null) { {
      var v: scala.Long = 0L
      value.type$field match {
        case com.badlogic.gdx.utils.JsonValue.ValueType.stringValue => {
          v = java.lang.Long.parseLong(value.stringValue)
        }
        case com.badlogic.gdx.utils.JsonValue.ValueType.doubleValue => {
          v = value.doubleValue.asInstanceOf[scala.Long].asInstanceOf[scala.Long]
        }
        case com.badlogic.gdx.utils.JsonValue.ValueType.longValue => {
          v = value.longValue
        }
        case com.badlogic.gdx.utils.JsonValue.ValueType.booleanValue => {
          v = if (value.longValue != 0) 1 else 0
        }
        case _ => {
          throw new java.lang.IllegalStateException("Value cannot be converted to long: " + value.type$field)
        }
      }
      array(i) = v
    }; value = value.next$field; i = i + 1 } }
    return array
  }
  def asIntArray(): scala.Array[scala.Int] = {
    if (this.type$field != com.badlogic.gdx.utils.JsonValue.ValueType.array) {
      throw new java.lang.IllegalStateException("Value is not an array: " + this.type$field)
    } else ()
    val array: scala.Array[scala.Int] = new scala.Array[scala.Int](this.size$field)
    var i: scala.Int = 0;
    { var value: JsonValue = this.child$field; while (value != null) { {
      var v: scala.Int = 0
      value.type$field match {
        case com.badlogic.gdx.utils.JsonValue.ValueType.stringValue => {
          v = java.lang.Integer.parseInt(value.stringValue)
        }
        case com.badlogic.gdx.utils.JsonValue.ValueType.doubleValue => {
          v = value.doubleValue.asInstanceOf[scala.Int].asInstanceOf[scala.Int]
        }
        case com.badlogic.gdx.utils.JsonValue.ValueType.longValue => {
          v = value.longValue.asInstanceOf[scala.Int].asInstanceOf[scala.Int]
        }
        case com.badlogic.gdx.utils.JsonValue.ValueType.booleanValue => {
          v = if (value.longValue != 0) 1 else 0
        }
        case _ => {
          throw new java.lang.IllegalStateException("Value cannot be converted to int: " + value.type$field)
        }
      }
      array(i) = v
    }; value = value.next$field; i = i + 1 } }
    return array
  }
  def asBooleanArray(): scala.Array[scala.Boolean] = {
    if (this.type$field != com.badlogic.gdx.utils.JsonValue.ValueType.array) {
      throw new java.lang.IllegalStateException("Value is not an array: " + this.type$field)
    } else ()
    val array: scala.Array[scala.Boolean] = new scala.Array[scala.Boolean](this.size$field)
    var i: scala.Int = 0;
    { var value: JsonValue = this.child$field; while (value != null) { {
      var v: scala.Boolean = false
      value.type$field match {
        case com.badlogic.gdx.utils.JsonValue.ValueType.stringValue => {
          v = java.lang.Boolean.parseBoolean(value.stringValue)
        }
        case com.badlogic.gdx.utils.JsonValue.ValueType.doubleValue => {
          v = value.doubleValue == 0
        }
        case com.badlogic.gdx.utils.JsonValue.ValueType.longValue => {
          v = value.longValue == 0
        }
        case com.badlogic.gdx.utils.JsonValue.ValueType.booleanValue => {
          v = value.longValue != 0
        }
        case _ => {
          throw new java.lang.IllegalStateException("Value cannot be converted to boolean: " + value.type$field)
        }
      }
      array(i) = v
    }; value = value.next$field; i = i + 1 } }
    return array
  }
  def asByteArray(): scala.Array[scala.Byte] = {
    if (this.type$field != com.badlogic.gdx.utils.JsonValue.ValueType.array) {
      throw new java.lang.IllegalStateException("Value is not an array: " + this.type$field)
    } else ()
    val array: scala.Array[scala.Byte] = new scala.Array[scala.Byte](this.size$field)
    var i: scala.Int = 0;
    { var value: JsonValue = this.child$field; while (value != null) { {
      var v: scala.Byte = 0
      value.type$field match {
        case com.badlogic.gdx.utils.JsonValue.ValueType.stringValue => {
          v = java.lang.Byte.parseByte(value.stringValue)
        }
        case com.badlogic.gdx.utils.JsonValue.ValueType.doubleValue => {
          v = value.doubleValue.asInstanceOf[scala.Byte].asInstanceOf[scala.Byte]
        }
        case com.badlogic.gdx.utils.JsonValue.ValueType.longValue => {
          v = value.longValue.asInstanceOf[scala.Byte].asInstanceOf[scala.Byte]
        }
        case com.badlogic.gdx.utils.JsonValue.ValueType.booleanValue => {
          v = if (value.longValue != 0) 1.asInstanceOf[scala.Byte] else 0
        }
        case _ => {
          throw new java.lang.IllegalStateException("Value cannot be converted to byte: " + value.type$field)
        }
      }
      array(i) = v
    }; value = value.next$field; i = i + 1 } }
    return array
  }
  def asShortArray(): scala.Array[scala.Short] = {
    if (this.type$field != com.badlogic.gdx.utils.JsonValue.ValueType.array) {
      throw new java.lang.IllegalStateException("Value is not an array: " + this.type$field)
    } else ()
    val array: scala.Array[scala.Short] = new scala.Array[scala.Short](this.size$field)
    var i: scala.Int = 0;
    { var value: JsonValue = this.child$field; while (value != null) { {
      var v: scala.Short = 0
      value.type$field match {
        case com.badlogic.gdx.utils.JsonValue.ValueType.stringValue => {
          v = java.lang.Short.parseShort(value.stringValue)
        }
        case com.badlogic.gdx.utils.JsonValue.ValueType.doubleValue => {
          v = value.doubleValue.asInstanceOf[scala.Short].asInstanceOf[scala.Short]
        }
        case com.badlogic.gdx.utils.JsonValue.ValueType.longValue => {
          v = value.longValue.asInstanceOf[scala.Short].asInstanceOf[scala.Short]
        }
        case com.badlogic.gdx.utils.JsonValue.ValueType.booleanValue => {
          v = if (value.longValue != 0) 1.asInstanceOf[scala.Short] else 0
        }
        case _ => {
          throw new java.lang.IllegalStateException("Value cannot be converted to short: " + value.type$field)
        }
      }
      array(i) = v
    }; value = value.next$field; i = i + 1 } }
    return array
  }
  def asCharArray(): scala.Array[scala.Char] = {
    if (this.type$field != com.badlogic.gdx.utils.JsonValue.ValueType.array) {
      throw new java.lang.IllegalStateException("Value is not an array: " + this.type$field)
    } else ()
    val array: scala.Array[scala.Char] = new scala.Array[scala.Char](this.size$field)
    var i: scala.Int = 0;
    { var value: JsonValue = this.child$field; while (value != null) { {
      var v: scala.Char = '\u0000'
      value.type$field match {
        case com.badlogic.gdx.utils.JsonValue.ValueType.stringValue => {
          v = if (value.stringValue.length() == 0) 0 else value.stringValue.charAt(0)
        }
        case com.badlogic.gdx.utils.JsonValue.ValueType.doubleValue => {
          v = value.doubleValue.asInstanceOf[scala.Char].asInstanceOf[scala.Char]
        }
        case com.badlogic.gdx.utils.JsonValue.ValueType.longValue => {
          v = value.longValue.asInstanceOf[scala.Char].asInstanceOf[scala.Char]
        }
        case com.badlogic.gdx.utils.JsonValue.ValueType.booleanValue => {
          v = if (value.longValue != 0) 1.asInstanceOf[scala.Char] else 0
        }
        case _ => {
          throw new java.lang.IllegalStateException("Value cannot be converted to char: " + value.type$field)
        }
      }
      array(i) = v
    }; value = value.next$field; i = i + 1 } }
    return array
  }
  def hasChild(name: java.lang.String): scala.Boolean = {
    return this.getChild(name) != null
  }
  def getChild(name: java.lang.String): JsonValue = {
    val child: JsonValue = this.get(name)
    return if (child == null) null.asInstanceOf[JsonValue] else child.child$field
  }
  def getString(name: java.lang.String, defaultValue: java.lang.String): java.lang.String = {
    val child: JsonValue = this.get(name)
    return if (((child == null) || (!child.isValue())) || child.isNull()) defaultValue else child.asString()
  }
  def getFloat(name: java.lang.String, defaultValue: scala.Float): scala.Float = {
    val child: JsonValue = this.get(name)
    return if (((child == null) || (!child.isValue())) || child.isNull()) defaultValue else child.asFloat()
  }
  def getDouble(name: java.lang.String, defaultValue: scala.Double): scala.Double = {
    val child: JsonValue = this.get(name)
    return if (((child == null) || (!child.isValue())) || child.isNull()) defaultValue else child.asDouble()
  }
  def getLong(name: java.lang.String, defaultValue: scala.Long): scala.Long = {
    val child: JsonValue = this.get(name)
    return if (((child == null) || (!child.isValue())) || child.isNull()) defaultValue else child.asLong()
  }
  def getInt(name: java.lang.String, defaultValue: scala.Int): scala.Int = {
    val child: JsonValue = this.get(name)
    return if (((child == null) || (!child.isValue())) || child.isNull()) defaultValue else child.asInt()
  }
  def getBoolean(name: java.lang.String, defaultValue: scala.Boolean): scala.Boolean = {
    val child: JsonValue = this.get(name)
    return if (((child == null) || (!child.isValue())) || child.isNull()) defaultValue else child.asBoolean()
  }
  def getByte(name: java.lang.String, defaultValue: scala.Byte): scala.Byte = {
    val child: JsonValue = this.get(name)
    return if (((child == null) || (!child.isValue())) || child.isNull()) defaultValue else child.asByte()
  }
  def getShort(name: java.lang.String, defaultValue: scala.Short): scala.Short = {
    val child: JsonValue = this.get(name)
    return if (((child == null) || (!child.isValue())) || child.isNull()) defaultValue else child.asShort()
  }
  def getChar(name: java.lang.String, defaultValue: scala.Char): scala.Char = {
    val child: JsonValue = this.get(name)
    return if (((child == null) || (!child.isValue())) || child.isNull()) defaultValue else child.asChar()
  }
  def getString(name: java.lang.String): java.lang.String = {
    val child: JsonValue = this.get(name)
    if (child == null) {
      throw new java.lang.IllegalArgumentException("Named value not found: " + name)
    } else ()
    return child.asString()
  }
  def getFloat(name: java.lang.String): scala.Float = {
    val child: JsonValue = this.get(name)
    if (child == null) {
      throw new java.lang.IllegalArgumentException("Named value not found: " + name)
    } else ()
    return child.asFloat()
  }
  def getDouble(name: java.lang.String): scala.Double = {
    val child: JsonValue = this.get(name)
    if (child == null) {
      throw new java.lang.IllegalArgumentException("Named value not found: " + name)
    } else ()
    return child.asDouble()
  }
  def getLong(name: java.lang.String): scala.Long = {
    val child: JsonValue = this.get(name)
    if (child == null) {
      throw new java.lang.IllegalArgumentException("Named value not found: " + name)
    } else ()
    return child.asLong()
  }
  def getInt(name: java.lang.String): scala.Int = {
    val child: JsonValue = this.get(name)
    if (child == null) {
      throw new java.lang.IllegalArgumentException("Named value not found: " + name)
    } else ()
    return child.asInt()
  }
  def getBoolean(name: java.lang.String): scala.Boolean = {
    val child: JsonValue = this.get(name)
    if (child == null) {
      throw new java.lang.IllegalArgumentException("Named value not found: " + name)
    } else ()
    return child.asBoolean()
  }
  def getByte(name: java.lang.String): scala.Byte = {
    val child: JsonValue = this.get(name)
    if (child == null) {
      throw new java.lang.IllegalArgumentException("Named value not found: " + name)
    } else ()
    return child.asByte()
  }
  def getShort(name: java.lang.String): scala.Short = {
    val child: JsonValue = this.get(name)
    if (child == null) {
      throw new java.lang.IllegalArgumentException("Named value not found: " + name)
    } else ()
    return child.asShort()
  }
  def getChar(name: java.lang.String): scala.Char = {
    val child: JsonValue = this.get(name)
    if (child == null) {
      throw new java.lang.IllegalArgumentException("Named value not found: " + name)
    } else ()
    return child.asChar()
  }
  def getString(index: scala.Int): java.lang.String = {
    val child: JsonValue = this.get(index)
    if (child == null) {
      throw new java.lang.IllegalArgumentException("Indexed value not found: " + this.name$field)
    } else ()
    return child.asString()
  }
  def getFloat(index: scala.Int): scala.Float = {
    val child: JsonValue = this.get(index)
    if (child == null) {
      throw new java.lang.IllegalArgumentException("Indexed value not found: " + this.name$field)
    } else ()
    return child.asFloat()
  }
  def getDouble(index: scala.Int): scala.Double = {
    val child: JsonValue = this.get(index)
    if (child == null) {
      throw new java.lang.IllegalArgumentException("Indexed value not found: " + this.name$field)
    } else ()
    return child.asDouble()
  }
  def getLong(index: scala.Int): scala.Long = {
    val child: JsonValue = this.get(index)
    if (child == null) {
      throw new java.lang.IllegalArgumentException("Indexed value not found: " + this.name$field)
    } else ()
    return child.asLong()
  }
  def getInt(index: scala.Int): scala.Int = {
    val child: JsonValue = this.get(index)
    if (child == null) {
      throw new java.lang.IllegalArgumentException("Indexed value not found: " + this.name$field)
    } else ()
    return child.asInt()
  }
  def getBoolean(index: scala.Int): scala.Boolean = {
    val child: JsonValue = this.get(index)
    if (child == null) {
      throw new java.lang.IllegalArgumentException("Indexed value not found: " + this.name$field)
    } else ()
    return child.asBoolean()
  }
  def getByte(index: scala.Int): scala.Byte = {
    val child: JsonValue = this.get(index)
    if (child == null) {
      throw new java.lang.IllegalArgumentException("Indexed value not found: " + this.name$field)
    } else ()
    return child.asByte()
  }
  def getShort(index: scala.Int): scala.Short = {
    val child: JsonValue = this.get(index)
    if (child == null) {
      throw new java.lang.IllegalArgumentException("Indexed value not found: " + this.name$field)
    } else ()
    return child.asShort()
  }
  def getChar(index: scala.Int): scala.Char = {
    val child: JsonValue = this.get(index)
    if (child == null) {
      throw new java.lang.IllegalArgumentException("Indexed value not found: " + this.name$field)
    } else ()
    return child.asChar()
  }
  def `type`(): com.badlogic.gdx.utils.JsonValue.ValueType = {
    return this.type$field
  }
  def setType(`type`: com.badlogic.gdx.utils.JsonValue.ValueType): scala.Unit = {
    if (`type` == null) {
      throw new java.lang.IllegalArgumentException("type cannot be null.")
    } else ()
    this.type$field = `type`
  }
  def isArray(): scala.Boolean = {
    return this.type$field == com.badlogic.gdx.utils.JsonValue.ValueType.array
  }
  def isObject(): scala.Boolean = {
    return this.type$field == com.badlogic.gdx.utils.JsonValue.ValueType.`object`
  }
  def isString(): scala.Boolean = {
    return this.type$field == com.badlogic.gdx.utils.JsonValue.ValueType.stringValue
  }
  def isNumber(): scala.Boolean = {
    return (this.type$field == com.badlogic.gdx.utils.JsonValue.ValueType.doubleValue) || (this.type$field == com.badlogic.gdx.utils.JsonValue.ValueType.longValue)
  }
  def isDouble(): scala.Boolean = {
    return this.type$field == com.badlogic.gdx.utils.JsonValue.ValueType.doubleValue
  }
  def isLong(): scala.Boolean = {
    return this.type$field == com.badlogic.gdx.utils.JsonValue.ValueType.longValue
  }
  def isBoolean(): scala.Boolean = {
    return this.type$field == com.badlogic.gdx.utils.JsonValue.ValueType.booleanValue
  }
  def isNull(): scala.Boolean = {
    return this.type$field == com.badlogic.gdx.utils.JsonValue.ValueType.nullValue
  }
  def isValue(): scala.Boolean = {
    this.type$field match {
      case com.badlogic.gdx.utils.JsonValue.ValueType.stringValue | com.badlogic.gdx.utils.JsonValue.ValueType.doubleValue | com.badlogic.gdx.utils.JsonValue.ValueType.longValue | com.badlogic.gdx.utils.JsonValue.ValueType.booleanValue | com.badlogic.gdx.utils.JsonValue.ValueType.nullValue => {
        return true
      }
    }
    return false
  }
  def name(): java.lang.String = {
    return this.name$field
  }
  def setName(name: java.lang.String): scala.Unit = {
    this.name$field = name
  }
  def parent(): JsonValue = {
    return this.parent$field
  }
  def child(): JsonValue = {
    return this.child$field
  }
  def last(): JsonValue = {
    return this.last$field
  }
  def setChild(name: java.lang.String, value: JsonValue): scala.Unit = {
    if (name == null) {
      throw new java.lang.IllegalArgumentException("name cannot be null.")
    } else ()
    value.name$field = name
    this.setChild(value)
  }
  def setChild(value: JsonValue): scala.Unit = {
    val name: java.lang.String = value.name$field
    if (name == null) {
      throw new java.lang.IllegalStateException("An object child requires a name: " + value)
    } else ()
    var current: JsonValue = this.child$field
    while (current != null) {
      if (current.name$field.equals(name)) {
        current.replace(value)
        return
      } else ()
      current = current.next$field
    }
    this.addChild(value)
  }
  def replace(value: JsonValue): scala.Unit = {
    if (this.parent$field.last$field == this) {
      this.parent$field.last$field = value
    } else ()
    if (this.prev$field != null) {
      this.prev$field.next$field = value
    } else {
      this.parent$field.child$field = value
    }
    value.prev$field = this.prev$field
    value.next$field = this.next$field
    if (this.next$field != null) {
      this.next$field.prev$field = value
    } else ()
    value.parent$field = this.parent$field
    this.prev$field = null
    this.next$field = null
    this.parent$field = null
  }
  def addChild(name: java.lang.String, value: JsonValue): scala.Unit = {
    if (name == null) {
      throw new java.lang.IllegalArgumentException("name cannot be null.")
    } else ()
    value.name$field = name
    this.addChild(value)
  }
  def addChild(value: JsonValue): scala.Unit = {
    if ((this.type$field == com.badlogic.gdx.utils.JsonValue.ValueType.`object`) && (value.name$field == null)) {
      throw new java.lang.IllegalStateException("An object child requires a name: " + value)
    } else ()
    value.parent$field = this
    value.next$field = null
    if (this.child$field == null) {
      value.prev$field = null
      this.child$field = value
    } else {
      this.last$field.next$field = value
      value.prev$field = this.last$field
    }
    this.last$field = value
    this.size$field = this.size$field + 1
  }
  def addChildFirst(value: JsonValue): scala.Unit = {
    if ((this.type$field == com.badlogic.gdx.utils.JsonValue.ValueType.`object`) && (value.name$field == null)) {
      throw new java.lang.IllegalStateException("An object child requires a name: " + value)
    } else ()
    value.parent$field = this
    value.next$field = this.child$field
    value.prev$field = null
    if (this.child$field == null) {
      this.child$field = value
      this.last$field = value
    } else {
      this.child$field.prev$field = value
      this.child$field = value
    }
    this.size$field = this.size$field + 1
  }
  def next(): JsonValue = {
    return this.next$field
  }
  def setNext(next: JsonValue): scala.Unit = {
    this.next$field = next
  }
  def prev(): JsonValue = {
    return this.prev$field
  }
  def setPrev(prev: JsonValue): scala.Unit = {
    this.prev$field = prev
  }
  def set(value: JsonValue): scala.Unit = {
    this.type$field = value.type$field
    this.stringValue = value.stringValue
    this.doubleValue = value.doubleValue
    this.longValue = value.longValue
  }
  def set(value: java.lang.String): scala.Unit = {
    this.stringValue = value
    this.type$field = if (value == null) com.badlogic.gdx.utils.JsonValue.ValueType.nullValue else com.badlogic.gdx.utils.JsonValue.ValueType.stringValue
  }
  def setNull(): scala.Unit = {
    this.stringValue = null
    this.type$field = com.badlogic.gdx.utils.JsonValue.ValueType.nullValue
  }
  def set(value: scala.Double, stringValue: java.lang.String): scala.Unit = {
    this.doubleValue = value
    this.longValue = value.asInstanceOf[scala.Long].asInstanceOf[scala.Long]
    this.stringValue = stringValue
    this.type$field = com.badlogic.gdx.utils.JsonValue.ValueType.doubleValue
  }
  def set(value: scala.Long, stringValue: java.lang.String): scala.Unit = {
    this.longValue = value
    this.doubleValue = value
    this.stringValue = stringValue
    this.type$field = com.badlogic.gdx.utils.JsonValue.ValueType.longValue
  }
  def set(value: scala.Boolean): scala.Unit = {
    this.longValue = if (value) 1 else 0
    this.type$field = com.badlogic.gdx.utils.JsonValue.ValueType.booleanValue
  }
  def equalsString(value: java.lang.String): scala.Boolean = {
    return java.util.Objects.equals(this.asString(), value)
  }
  def nameEquals(value: java.lang.String): scala.Boolean = {
    return java.util.Objects.equals(this.name$field, value)
  }
  def toJson(outputType: com.badlogic.gdx.utils.JsonWriter.OutputType): java.lang.String = {
    if (this.isValue()) {
      return this.asString()
    } else ()
    val writer: java.io.StringWriter = new java.io.StringWriter(512)
    try {
      this.toJson(outputType, writer)
    } catch {
      case ex: java.io.IOException => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException(ex)
      }
    }
    return writer.toString()
  }
  def toJson(outputType: com.badlogic.gdx.utils.JsonWriter.OutputType, writer: java.io.Writer): scala.Unit = {
    if (this.isObject()) {
      writer.write('{');
      { var child: JsonValue = this.child$field; while (child != null) { {
        writer.write(outputType.quoteName(child.name$field))
        writer.write(':')
        child.toJson(outputType, writer)
        if (child.next$field != null) {
          writer.write(',')
        } else ()
      }; child = child.next$field } }
      writer.write('}')
    } else {
      if (this.isArray()) {
        writer.write('[');
        { var child: JsonValue = this.child$field; while (child != null) { {
          child.toJson(outputType, writer)
          if (child.next$field != null) {
            writer.write(',')
          } else ()
        }; child = child.next$field } }
        writer.write(']')
      } else {
        if (this.isString()) {
          writer.write(outputType.quoteValue(this.asString()))
        } else {
          if (this.isDouble()) {
            val doubleValue: scala.Double = this.asDouble()
            val longValue: scala.Long = this.asLong()
            writer.write(if (doubleValue == longValue) java.lang.Long.toString(longValue) else java.lang.Double.toString(doubleValue))
          } else {
            if (this.isLong()) {
              writer.write(java.lang.Long.toString(this.asLong()))
            } else {
              if (this.isBoolean()) {
                writer.write(if (this.asBoolean()) "true" else "false")
              } else {
                if (this.isNull()) {
                  writer.write("null")
                } else {
                  throw new com.badlogic.gdx.utils.SerializationException("Unknown object type: " + this)
                }
              }
            }
          }
        }
      }
    }
  }
  def iterator(): com.badlogic.gdx.utils.JsonValue#JsonIterator = {
    return new JsonIterator()
  }
  def toString(): java.lang.String = {
    if (this.isValue()) {
      return if (this.name$field == null) this.asString() else (this.name$field + ": ") + this.asString()
    } else ()
    return (if (this.name$field == null) "" else this.name$field + ": ") + this.prettyPrint(com.badlogic.gdx.utils.JsonWriter.OutputType.minimal, 0)
  }
  def trace(): java.lang.String = {
    if (this.parent$field == null) {
      if (this.type$field == com.badlogic.gdx.utils.JsonValue.ValueType.array) {
        return "[]"
      } else ()
      if (this.type$field == com.badlogic.gdx.utils.JsonValue.ValueType.`object`) {
        return "{}"
      } else ()
      return ""
    } else ()
    var trace: java.lang.String = null.asInstanceOf[java.lang.String]
    if (this.parent$field.type$field == com.badlogic.gdx.utils.JsonValue.ValueType.array) {
      trace = "[]"
      var i: scala.Int = 0;
      { var child: JsonValue = this.parent$field.child$field; while (child != null) { {
        if (child == this) {
          trace = ("[" + i) + "]"
          /* break */ ()
        } else ()
      }; child = child.next$field; i = i + 1 } }
    } else {
      if (this.name$field.indexOf('.') != (-1)) {
        trace = (".\"" + this.name$field.replace("\"", "\\\"")) + "\""
      } else {
        trace = java.lang.String.valueOf('.') + this.name$field
      }
    }
    return this.parent$field.trace() + trace
  }
  def prettyPrint(outputType: com.badlogic.gdx.utils.JsonWriter.OutputType, singleLineColumns: scala.Int): java.lang.String = {
    val settings: com.badlogic.gdx.utils.JsonValue.PrettyPrintSettings = new com.badlogic.gdx.utils.JsonValue.PrettyPrintSettings()
    settings.outputType = outputType
    settings.singleLineColumns = singleLineColumns
    return this.prettyPrint(settings)
  }
  def prettyPrint(settings: com.badlogic.gdx.utils.JsonValue.PrettyPrintSettings): java.lang.String = {
    val buffer: java.lang.StringBuilder = new java.lang.StringBuilder(512)
    this.prettyPrint(this, buffer, 0, settings)
    return buffer.toString()
  }
  private def prettyPrint(`object`: JsonValue, buffer: java.lang.StringBuilder, indent: scala.Int, settings: com.badlogic.gdx.utils.JsonValue.PrettyPrintSettings): scala.Unit = {
    val outputType: com.badlogic.gdx.utils.JsonWriter.OutputType = settings.outputType
    if (`object`.isObject()) {
      if (`object`.child$field == null) {
        buffer.append("{}")
      } else {
        var newLines: scala.Boolean = !JsonValue.isFlat(`object`)
        val start: scala.Int = buffer.length()
        while (true) {
          buffer.append(if (newLines) "{\n" else "{ ");
          { var child: JsonValue = `object`.child$field; while (child != null) { {
            if (newLines) {
              JsonValue.indent(indent, buffer)
            } else ()
            buffer.append(outputType.quoteName(child.name$field))
            buffer.append(": ")
            this.prettyPrint(child, buffer, indent + 1, settings)
            if (((!newLines) || (outputType != com.badlogic.gdx.utils.JsonWriter.OutputType.minimal)) && (child.next$field != null)) {
              buffer.append(',')
            } else ()
            buffer.append(if (newLines) '\n' else ' ')
            if ((!newLines) && ((buffer.length() - start) > settings.singleLineColumns)) {
              buffer.setLength(start)
              newLines = true
              /* continue */ ()
            } else ()
          }; child = child.next$field } }
          /* break */ ()
        }
        if (newLines) {
          JsonValue.indent(indent - 1, buffer)
        } else ()
        buffer.append('}')
      }
    } else {
      if (`object`.isArray()) {
        if (`object`.child$field == null) {
          buffer.append("[]")
        } else {
          var newLines: scala.Boolean = !JsonValue.isFlat(`object`)
          val wrap: scala.Boolean = settings.wrapNumericArrays || (!JsonValue.isNumeric(`object`))
          val start: scala.Int = buffer.length()
          while (true) {
            buffer.append(if (newLines) "[\n" else "[ ");
            { var child: JsonValue = `object`.child$field; while (child != null) { {
              if (newLines) {
                JsonValue.indent(indent, buffer)
              } else ()
              this.prettyPrint(child, buffer, indent + 1, settings)
              if (((!newLines) || (outputType != com.badlogic.gdx.utils.JsonWriter.OutputType.minimal)) && (child.next$field != null)) {
                buffer.append(',')
              } else ()
              buffer.append(if (newLines) '\n' else ' ')
              if ((wrap && (!newLines)) && ((buffer.length() - start) > settings.singleLineColumns)) {
                buffer.setLength(start)
                newLines = true
                /* continue */ ()
              } else ()
            }; child = child.next$field } }
            /* break */ ()
          }
          if (newLines) {
            JsonValue.indent(indent - 1, buffer)
          } else ()
          buffer.append(']')
        }
      } else {
        if (`object`.isString()) {
          buffer.append(outputType.quoteValue(`object`.asString()))
        } else {
          if (`object`.isDouble()) {
            val doubleValue: scala.Double = `object`.asDouble()
            val longValue: scala.Long = `object`.asLong()
            buffer.append(if (doubleValue == longValue) longValue else doubleValue)
          } else {
            if (`object`.isLong()) {
              buffer.append(`object`.asLong())
            } else {
              if (`object`.isBoolean()) {
                buffer.append(`object`.asBoolean())
              } else {
                if (`object`.isNull()) {
                  buffer.append("null")
                } else {
                  throw new com.badlogic.gdx.utils.SerializationException("Unknown object type: " + `object`)
                }
              }
            }
          }
        }
      }
    }
  }
  def prettyPrint(outputType: com.badlogic.gdx.utils.JsonWriter.OutputType, writer: java.io.Writer): scala.Unit = {
    val settings: com.badlogic.gdx.utils.JsonValue.PrettyPrintSettings = new com.badlogic.gdx.utils.JsonValue.PrettyPrintSettings()
    settings.outputType = outputType
    this.prettyPrint(this, writer, 0, settings)
  }
  private def prettyPrint(`object`: JsonValue, writer: java.io.Writer, indent: scala.Int, settings: com.badlogic.gdx.utils.JsonValue.PrettyPrintSettings): scala.Unit = {
    val outputType: com.badlogic.gdx.utils.JsonWriter.OutputType = settings.outputType
    if (`object`.isObject()) {
      if (`object`.child$field == null) {
        writer.write("{}")
      } else {
        val newLines: scala.Boolean = (!JsonValue.isFlat(`object`)) || (`object`.size$field > 6)
        writer.write(if (newLines) "{\n" else "{ ")
        val i: scala.Int = 0;
        { var child: JsonValue = `object`.child$field; while (child != null) { {
          if (newLines) {
            JsonValue.indent(indent, writer)
          } else ()
          writer.write(outputType.quoteName(child.name$field))
          writer.write(": ")
          this.prettyPrint(child, writer, indent + 1, settings)
          if (((!newLines) || (outputType != com.badlogic.gdx.utils.JsonWriter.OutputType.minimal)) && (child.next$field != null)) {
            writer.write(',')
          } else ()
          writer.write(if (newLines) '\n' else ' ')
        }; child = child.next$field } }
        if (newLines) {
          JsonValue.indent(indent - 1, writer)
        } else ()
        writer.write('}')
      }
    } else {
      if (`object`.isArray()) {
        if (`object`.child$field == null) {
          writer.write("[]")
        } else {
          val newLines: scala.Boolean = !JsonValue.isFlat(`object`)
          writer.write(if (newLines) "[\n" else "[ ")
          val i: scala.Int = 0;
          { var child: JsonValue = `object`.child$field; while (child != null) { {
            if (newLines) {
              JsonValue.indent(indent, writer)
            } else ()
            this.prettyPrint(child, writer, indent + 1, settings)
            if (((!newLines) || (outputType != com.badlogic.gdx.utils.JsonWriter.OutputType.minimal)) && (child.next$field != null)) {
              writer.write(',')
            } else ()
            writer.write(if (newLines) '\n' else ' ')
          }; child = child.next$field } }
          if (newLines) {
            JsonValue.indent(indent - 1, writer)
          } else ()
          writer.write(']')
        }
      } else {
        if (`object`.isString()) {
          writer.write(outputType.quoteValue(`object`.asString()))
        } else {
          if (`object`.isDouble()) {
            val doubleValue: scala.Double = `object`.asDouble()
            val longValue: scala.Long = `object`.asLong()
            writer.write(java.lang.Double.toString(if (doubleValue == longValue) longValue else doubleValue))
          } else {
            if (`object`.isLong()) {
              writer.write(java.lang.Long.toString(`object`.asLong()))
            } else {
              if (`object`.isBoolean()) {
                writer.write(java.lang.Boolean.toString(`object`.asBoolean()))
              } else {
                if (`object`.isNull()) {
                  writer.write("null")
                } else {
                  throw new com.badlogic.gdx.utils.SerializationException("Unknown object type: " + `object`)
                }
              }
            }
          }
        }
      }
    }
  }
  class JsonIterator extends scala.collection.Iterator[JsonValue] with scala.collection.Iterable[JsonValue] {
    var entry: JsonValue = child$field
    var current: JsonValue = null.asInstanceOf[JsonValue]
    def hasNext(): scala.Boolean = {
      return this.entry != null
    }
    def next(): JsonValue = {
      this.current = this.entry
      if (this.current == null) {
        throw new java.util.NoSuchElementException()
      } else ()
      this.entry = this.current.next$field
      return this.current
    }
    def remove(): scala.Unit = {
      this.current.remove()
    }
    def iterator(): scala.collection.Iterator[JsonValue] = {
      return this
    }
  }
}
object JsonValue {
  private def isFlat(`object`: JsonValue): scala.Boolean = {
    { var child: JsonValue = `object`.child$field; while (child != null) { {
      if (child.isObject() || child.isArray()) {
        return false
      } else ()
    }; child = child.next$field } }
    return true
  }
  private def isNumeric(`object`: JsonValue): scala.Boolean = {
    { var child: JsonValue = `object`.child$field; while (child != null) { {
      if (!child.isNumber()) {
        return false
      } else ()
    }; child = child.next$field } }
    return true
  }
  private def indent(count: scala.Int, buffer: java.lang.StringBuilder): scala.Unit = {
    { var i: scala.Int = 0; while (i < count) { {
      buffer.append('\t')
    }; i = i + 1 } }
  }
  private def indent(count: scala.Int, writer: java.io.Writer): scala.Unit = {
    { var i: scala.Int = 0; while (i < count) { {
      writer.write('\t')
    }; i = i + 1 } }
  }
  sealed abstract class ValueType {
    def name(): java.lang.String = this.toString()
  }
  object ValueType {
    case object `object` extends ValueType
    case object array extends ValueType
    case object stringValue extends ValueType
    case object doubleValue extends ValueType
    case object longValue extends ValueType
    case object booleanValue extends ValueType
    case object nullValue extends ValueType
    def values(): scala.Array[ValueType] = scala.Array(`object`, array, stringValue, doubleValue, longValue, booleanValue, nullValue)
    def valueOf(name: java.lang.String): ValueType = name match {
      case "`object`" => `object`
      case "array" => array
      case "stringValue" => stringValue
      case "doubleValue" => doubleValue
      case "longValue" => longValue
      case "booleanValue" => booleanValue
      case "nullValue" => nullValue
      case _ => throw new java.lang.IllegalArgumentException(name)
    }
  }
  class PrettyPrintSettings {
    var outputType: com.badlogic.gdx.utils.JsonWriter.OutputType = null.asInstanceOf[com.badlogic.gdx.utils.JsonWriter.OutputType]
    var singleLineColumns: scala.Int = 0
    var wrapNumericArrays: scala.Boolean = false
  }
}