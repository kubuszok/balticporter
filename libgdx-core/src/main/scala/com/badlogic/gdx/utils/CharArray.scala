package com.badlogic.gdx.utils

class CharArray extends java.lang.CharSequence with java.lang.Appendable {
  var items: scala.Array[scala.Char] = null.asInstanceOf[scala.Array[scala.Char]]
  var size: scala.Int = 0
  var ordered: scala.Boolean = false
  def this(ordered: scala.Boolean, capacity: scala.Int) = {
    this()
    this.ordered = ordered
    this.items = new scala.Array[scala.Char](capacity)
  }
  def this(capacity: scala.Int) = {
    this(true, capacity)
  }
  def this(array: CharArray) = {
    this()
    this.ordered = array.ordered
    this.size = array.size
    this.items = new scala.Array[scala.Char](this.size)
    java.lang.System.arraycopy(array.items, 0, this.items, 0, this.size)
  }
  def this(ordered: scala.Boolean, array: scala.Array[scala.Char], start: scala.Int, count: scala.Int) = {
    this(ordered, count)
    this.size = count
    java.lang.System.arraycopy(array, start, this.items, 0, count)
  }
  def this(array: scala.Array[scala.Char]) = {
    this(true, array, 0, array.length)
  }
  def this(seq: java.lang.CharSequence) = {
    this(seq.length() + CharArray.CAPACITY)
    this.append(seq)
  }
  def this(str: java.lang.String) = {
    this(str.length() + CharArray.CAPACITY)
    this.append(str)
  }
  def this(str: java.lang.StringBuilder) = {
    this(str.length() + CharArray.CAPACITY)
    this.append(str)
  }
  private def this(initialBuffer: scala.Array[scala.Char], length: scala.Int) = {
    this()
    java.util.Objects.requireNonNull(initialBuffer, "initialBuffer")
    this.items = initialBuffer
    this.size = length
  }
  def add(value: scala.Char): scala.Unit = {
    if (this.size == this.items.length) {
      this.resizeBuffer(this.size + 1)
    } else ()
    this.items({ this.size += 1; this.size }) = value
  }
  def add(value1: scala.Char, value2: scala.Char): scala.Unit = {
    if ((this.size + 1) >= this.items.length) {
      this.resizeBuffer(this.size + 2)
    } else ()
    this.items(this.size) = value1
    this.items(this.size + 1) = value2
    this.size = this.size + 2
  }
  def add(value1: scala.Char, value2: scala.Char, value3: scala.Char): scala.Unit = {
    if ((this.size + 2) >= this.items.length) {
      this.resizeBuffer(this.size + 3)
    } else ()
    val items: scala.Array[scala.Char] = this.items
    items(this.size) = value1
    items(this.size + 1) = value2
    items(this.size + 2) = value3
    this.size = this.size + 3
  }
  def add(value1: scala.Char, value2: scala.Char, value3: scala.Char, value4: scala.Char): scala.Unit = {
    if ((this.size + 3) >= this.items.length) {
      this.resizeBuffer(this.size + 4)
    } else ()
    val items: scala.Array[scala.Char] = this.items
    items(this.size) = value1
    items(this.size + 1) = value2
    items(this.size + 2) = value3
    items(this.size + 3) = value4
    this.size = this.size + 4
  }
  def addAll(array: CharArray): scala.Unit = {
    this.addAll(array.items, 0, array.size)
  }
  def addAll(array: CharArray, offset: scala.Int, length: scala.Int): scala.Unit = {
    if ((offset + length) > array.size) {
      throw new java.lang.IllegalArgumentException((((("offset + length must be <= size: " + offset) + " + ") + length) + " <= ") + array.size)
    } else ()
    this.addAll(array.items, offset, length)
  }
  def addAll(array: scala.Array[scala.Char]): scala.Unit = {
    this.addAll(array, 0, array.length)
  }
  def addAll(array: scala.Array[scala.Char], offset: scala.Int, length: scala.Int): scala.Unit = {
    val sizeNeeded: scala.Int = this.size + length
    if (sizeNeeded > this.items.length) {
      this.resizeBuffer(sizeNeeded)
    } else ()
    java.lang.System.arraycopy(array, offset, this.items, this.size, length)
    this.size = this.size + length
  }
  def get(index: scala.Int): scala.Char = {
    if (index >= this.size) {
      throw new java.lang.IndexOutOfBoundsException((("index can't be >= size: " + index) + " >= ") + this.size)
    } else ()
    return this.items(index)
  }
  def set(index: scala.Int, value: scala.Char): scala.Unit = {
    if (index >= this.size) {
      throw new java.lang.IndexOutOfBoundsException((("index can't be >= size: " + index) + " >= ") + this.size)
    } else ()
    this.items(index) = value
  }
  def incr(index: scala.Int, value: scala.Char): scala.Unit = {
    if (index >= this.size) {
      throw new java.lang.IndexOutOfBoundsException((("index can't be >= size: " + index) + " >= ") + this.size)
    } else ()
    this.items(index) = (this.items(index) + value).asInstanceOf[scala.Char]
  }
  def incr(value: scala.Char): scala.Unit = {
    val items: scala.Array[scala.Char] = this.items;
    { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
      items(i) = (items(i) + value).asInstanceOf[scala.Char]
    }; i = i + 1 } }
  }
  def mul(index: scala.Int, value: scala.Char): scala.Unit = {
    if (index >= this.size) {
      throw new java.lang.IndexOutOfBoundsException((("index can't be >= size: " + index) + " >= ") + this.size)
    } else ()
    this.items(index) = (this.items(index) * value).asInstanceOf[scala.Char]
  }
  def mul(value: scala.Char): scala.Unit = {
    val items: scala.Array[scala.Char] = this.items;
    { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
      items(i) = (items(i) * value).asInstanceOf[scala.Char]
    }; i = i + 1 } }
  }
  def swap(first: scala.Int, second: scala.Int): scala.Unit = {
    if (first >= this.size) {
      throw new java.lang.IndexOutOfBoundsException((("first can't be >= size: " + first) + " >= ") + this.size)
    } else ()
    if (second >= this.size) {
      throw new java.lang.IndexOutOfBoundsException((("second can't be >= size: " + second) + " >= ") + this.size)
    } else ()
    val items: scala.Array[scala.Char] = this.items
    val firstValue: scala.Char = items(first)
    items(first) = items(second)
    items(second) = firstValue
  }
  def replaceFirst(value: scala.Char, replacement: scala.Char): scala.Boolean = {
    if (value != replacement) {
      val items: scala.Array[scala.Char] = this.items;
      { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
        if (items(i) == value) {
          items(i) = replacement
          return true
        } else ()
      }; i = i + 1 } }
    } else ()
    return false
  }
  def replaceAll(value: scala.Char, replacement: scala.Char): scala.Int = {
    var replacements: scala.Int = 0
    if (value != replacement) {
      val items: scala.Array[scala.Char] = this.items;
      { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
        if (items(i) == value) {
          items(i) = replacement
          replacements = replacements + 1
        } else ()
      }; i = i + 1 } }
    } else ()
    return replacements
  }
  def contains(value: scala.Char): scala.Boolean = {
    var i: scala.Int = this.size - 1
    val items: scala.Array[scala.Char] = this.items
    while (i >= 0) {
      if (items({ i -= 1; i }) == value) {
        return true
      } else ()
    }
    return false
  }
  def indexOf(value: scala.Char): scala.Int = {
    val items: scala.Array[scala.Char] = this.items;
    { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
      if (items(i) == value) {
        return i
      } else ()
    }; i = i + 1 } }
    return -1
  }
  def lastIndexOf(value: scala.Char): scala.Int = {
    val items: scala.Array[scala.Char] = this.items;
    { var i: scala.Int = this.size - 1; while (i >= 0) { {
      if (items(i) == value) {
        return i
      } else ()
    }; i = i - 1 } }
    return -1
  }
  def removeValue(value: scala.Char): scala.Boolean = {
    val items: scala.Array[scala.Char] = this.items;
    { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
      if (items(i) == value) {
        this.removeIndex(i)
        return true
      } else ()
    }; i = i + 1 } }
    return false
  }
  def removeIndex(index: scala.Int): scala.Char = {
    this.validateIndex(index)
    val items: scala.Array[scala.Char] = this.items
    val value: scala.Char = items(index)
    this.size = this.size - 1
    if (this.ordered) {
      java.lang.System.arraycopy(items, index + 1, items, index, this.size - index)
    } else {
      items(index) = items(this.size)
    }
    return value
  }
  def removeRange(start: scala.Int, `end`: scala.Int): scala.Unit = {
    val n: scala.Int = this.size
    this.validateRange(start, `end`)
    val count: scala.Int = (`end` - start) + 1
    val lastIndex: scala.Int = n - count
    if (this.ordered) {
      java.lang.System.arraycopy(this.items, start + count, this.items, start, n - (start + count))
    } else {
      val i: scala.Int = java.lang.Math.max(lastIndex, `end` + 1)
      java.lang.System.arraycopy(this.items, i, this.items, start, n - i)
    }
    this.size = n - count
  }
  def removeAll(array: CharArray): scala.Boolean = {
    var size: scala.Int = this.size
    val startSize: scala.Int = size
    val items: scala.Array[scala.Char] = this.items;
    { var i: scala.Int = 0; val n: scala.Int = array.size; while (i < n) { {
      val item: scala.Char = array.get(i);
      { var ii: scala.Int = 0; while (ii < size) { {
        if (item == items(ii)) {
          this.removeIndex(ii)
          size = size - 1
          /* break */ ()
        } else ()
      }; ii = ii + 1 } }
    }; i = i + 1 } }
    return size != startSize
  }
  def pop(): scala.Char = {
    return this.items({ this.size -= 1; this.size })
  }
  def peek(): scala.Char = {
    return this.items(this.size - 1)
  }
  def first(): scala.Char = {
    if (this.size == 0) {
      throw new java.lang.IllegalStateException("Array is empty.")
    } else ()
    return this.items(0)
  }
  def notEmpty(): scala.Boolean = {
    return this.size > 0
  }
  def isEmpty(): scala.Boolean = {
    return this.size == 0
  }
  def clear(): scala.Unit = {
    this.size = 0
  }
  def setSize(newSize: scala.Int): scala.Array[scala.Char] = {
    if (newSize < 0) {
      throw new java.lang.IllegalArgumentException("newSize must be >= 0: " + newSize)
    } else ()
    if (newSize > this.items.length) {
      this.resize(java.lang.Math.max(8, newSize))
    } else ()
    this.size = newSize
    return this.items
  }
  def shrink(): scala.Array[scala.Char] = {
    if (this.items.length > this.size) {
      this.resize(this.size)
    } else ()
    return this.items
  }
  def trimToSize(): scala.Unit = {
    this.shrink()
  }
  def ensureCapacity(additionalCapacity: scala.Int): scala.Array[scala.Char] = {
    if (additionalCapacity < 0) {
      throw new java.lang.IllegalArgumentException("additionalCapacity must be >= 0: " + additionalCapacity)
    } else ()
    val sizeNeeded: scala.Int = this.size + additionalCapacity
    if ((sizeNeeded - this.items.length) > 0) {
      this.resizeBuffer(sizeNeeded)
    } else ()
    return this.items
  }
  private def require(additionalCapacity: scala.Int): scala.Unit = {
    val sizeNeeded: scala.Int = this.size + additionalCapacity
    if ((sizeNeeded - this.items.length) > 0) {
      this.resizeBuffer(sizeNeeded)
    } else ()
  }
  private def resizeBuffer(minCapacity: scala.Int): scala.Unit = {
    val oldCapacity: scala.Int = this.items.length
    var newCapacity: scala.Int = ((oldCapacity >> 1) + oldCapacity) + 2
    if ((newCapacity ^ -2147483648) < (minCapacity ^ -2147483648)) {
      newCapacity = minCapacity
    } else ()
    if ((newCapacity ^ -2147483648) > (CharArray.MAX_BUFFER_SIZE ^ -2147483648)) {
      if (minCapacity < 0) {
        throw new java.lang.RuntimeException("Unable to allocate array size: " + (minCapacity & 4294967295L))
      } else ()
      newCapacity = java.lang.Math.max(minCapacity, CharArray.MAX_BUFFER_SIZE)
    } else ()
    this.resize(newCapacity)
  }
  def resize(newSize: scala.Int): scala.Array[scala.Char] = {
    this.items = java.util.Arrays.copyOf(this.items, newSize)
    return this.items
  }
  def sort(): scala.Unit = {
    java.util.Arrays.sort(this.items, 0, this.size)
  }
  def shuffle(): scala.Unit = {
    val items: scala.Array[scala.Char] = this.items;
    { var i: scala.Int = this.size - 1; while (i >= 0) { {
      val ii: scala.Int = com.badlogic.gdx.math.MathUtils.random(i)
      val temp: scala.Char = items(i)
      items(i) = items(ii)
      items(ii) = temp
    }; i = i - 1 } }
  }
  def truncate(newSize: scala.Int): scala.Unit = {
    if (newSize < 0) {
      throw new java.lang.IllegalArgumentException("newSize must be >= 0: " + newSize)
    } else ()
    if (this.size > newSize) {
      this.size = newSize
    } else ()
  }
  def random(): scala.Char = {
    if (this.size == 0) {
      return ' '
    } else ()
    return this.items(com.badlogic.gdx.math.MathUtils.random(0, this.size - 1))
  }
  def toArray(): scala.Array[scala.Char] = {
    val array: scala.Array[scala.Char] = new scala.Array[scala.Char](this.size)
    java.lang.System.arraycopy(this.items, 0, array, 0, this.size)
    return array
  }
  def append(value: scala.Boolean): CharArray = {
    if (value) {
      this.require(CharArray.TRUE_STRING_SIZE)
      this.appendTrue(this.size)
    } else {
      this.require(CharArray.FALSE_STRING_SIZE)
      this.appendFalse(this.size)
    }
    return this
  }
  def append(value: scala.Char): CharArray = {
    this.require(1)
    this.items({ this.size += 1; this.size }) = value
    return this
  }
  def append(ch: scala.Array[scala.Char]): CharArray = {
    if (ch == null) {
      return this.appendNull()
    } else ()
    val strLength: scala.Int = ch.length
    if (strLength > 0) {
      this.require(strLength)
      java.lang.System.arraycopy(ch, 0, this.items, this.size, strLength)
      this.size = this.size + strLength
    } else ()
    return this
  }
  def append(ch: scala.Array[scala.Char], start: scala.Int, length: scala.Int): CharArray = {
    if (ch == null) {
      return this.appendNull()
    } else ()
    if ((start < 0) || (start > ch.length)) {
      throw new java.lang.IndexOutOfBoundsException("Invalid start: " + start)
    } else ()
    if ((length < 0) || ((start + length) > ch.length)) {
      throw new java.lang.IndexOutOfBoundsException("Invalid length: " + length)
    } else ()
    if (length > 0) {
      this.require(length)
      java.lang.System.arraycopy(ch, start, this.items, this.size, length)
      this.size = this.size + length
    } else ()
    return this
  }
  def append(str: java.nio.CharBuffer): CharArray = {
    if (str == null) {
      this.appendNull()
    } else {
      this.append(str, 0, str.length())
    }
    return this
  }
  def append(buf: java.nio.CharBuffer, start: scala.Int, `end`: scala.Int): CharArray = {
    if (buf == null) {
      return this.appendNull()
    } else ()
    if (buf.hasArray()) {
      val totalLength: scala.Int = buf.remaining()
      if ((((start < 0) || (`end` < 0)) || (start > `end`)) || (`end` > totalLength)) {
        throw new java.lang.IndexOutOfBoundsException()
      } else ()
      val length: scala.Int = `end` - start
      this.require(length)
      java.lang.System.arraycopy(buf.array(), (buf.arrayOffset() + buf.position()) + start, this.items, this.size, length)
      this.size = this.size + length
    } else {
      this.append(buf.toString(), start, `end`)
    }
    return this
  }
  def append(seq: java.lang.CharSequence): CharArray = {
    if (seq == null) {
      return this.appendNull()
    } else ()
    if (seq.isInstanceOf[CharArray]) {
      return this.append(seq.asInstanceOf[CharArray])
    } else ()
    if (seq.isInstanceOf[java.lang.StringBuilder]) {
      return this.append(seq.asInstanceOf[java.lang.StringBuilder])
    } else ()
    if (seq.isInstanceOf[java.lang.StringBuffer]) {
      return this.append(seq.asInstanceOf[java.lang.StringBuffer])
    } else ()
    if (seq.isInstanceOf[java.nio.CharBuffer]) {
      return this.append(seq.asInstanceOf[java.nio.CharBuffer])
    } else ()
    return this.append(seq.toString())
  }
  def append(seq: java.lang.CharSequence, start: scala.Int, `end`: scala.Int): CharArray = {
    if (seq == null) {
      return this.appendNull()
    } else ()
    if ((((start < 0) || (`end` < 0)) || (start > `end`)) || (`end` > seq.length())) {
      throw new java.lang.IndexOutOfBoundsException()
    } else ()
    return this.append(seq.toString(), start, `end`)
  }
  def append(value: scala.Double): CharArray = {
    return this.append(java.lang.String.valueOf(value))
  }
  def append(value: scala.Float): CharArray = {
    return this.append(java.lang.String.valueOf(value))
  }
  def append(value: scala.Int): CharArray = {
    return this.append(value, 0, '0')
  }
  def append(value: scala.Int, minLength: scala.Int): CharArray = {
    return this.append(value, minLength, '0')
  }
  def append(value$arg: scala.Int, minLength: scala.Int, prefix: scala.Char): CharArray = {
    var value: scala.Int = value$arg
    if (value == java.lang.Integer.MIN_VALUE) {
      this.append("-2147483648")
      return this
    } else ()
    if (value < 0) {
      this.append('-')
      value = -value
    } else ()
    if (minLength > 1) {
      { var j: scala.Int = minLength - CharArray.numChars(value, 10); while (j > 0) { {
        this.append(prefix)
      }; j = j - 1 } }
    } else ()
    if (value >= 10000) {
      if (value >= 1000000000) {
        this.append(CharArray.DIGITS(((value.asInstanceOf[scala.Long] % 10000000000L) / 1000000000L).asInstanceOf[scala.Int]))
      } else ()
      if (value >= 100000000) {
        this.append(CharArray.DIGITS((value % 1000000000) / 100000000))
      } else ()
      if (value >= 10000000) {
        this.append(CharArray.DIGITS((value % 100000000) / 10000000))
      } else ()
      if (value >= 1000000) {
        this.append(CharArray.DIGITS((value % 10000000) / 1000000))
      } else ()
      if (value >= 100000) {
        this.append(CharArray.DIGITS((value % 1000000) / 100000))
      } else ()
      this.append(CharArray.DIGITS((value % 100000) / 10000))
    } else ()
    if (value >= 1000) {
      this.append(CharArray.DIGITS((value % 10000) / 1000))
    } else ()
    if (value >= 100) {
      this.append(CharArray.DIGITS((value % 1000) / 100))
    } else ()
    if (value >= 10) {
      this.append(CharArray.DIGITS((value % 100) / 10))
    } else ()
    this.append(CharArray.DIGITS(value % 10))
    return this
  }
  def append(value: scala.Long): CharArray = {
    return this.append(value, 0, '0')
  }
  def append(value: scala.Long, minLength: scala.Int): CharArray = {
    return this.append(value, minLength, '0')
  }
  def append(value$arg: scala.Long, minLength: scala.Int, prefix: scala.Char): CharArray = {
    var value: scala.Long = value$arg
    if (value == java.lang.Long.MIN_VALUE) {
      this.append("-9223372036854775808")
      return this
    } else ()
    if (value < 0L) {
      this.append('-')
      value = -value
    } else ()
    if (minLength > 1) {
      { var j: scala.Int = minLength - CharArray.numChars(value, 10); while (j > 0) { {
        this.append(prefix)
      }; j = j - 1 } }
    } else ()
    if (value >= 10000) {
      if (value >= 1000000000000000000L) {
        this.append(CharArray.DIGITS(((value % 1.0E19) / 1000000000000000000L).asInstanceOf[scala.Int]))
      } else ()
      if (value >= 100000000000000000L) {
        this.append(CharArray.DIGITS(((value % 1000000000000000000L) / 100000000000000000L).asInstanceOf[scala.Int]))
      } else ()
      if (value >= 10000000000000000L) {
        this.append(CharArray.DIGITS(((value % 100000000000000000L) / 10000000000000000L).asInstanceOf[scala.Int]))
      } else ()
      if (value >= 1000000000000000L) {
        this.append(CharArray.DIGITS(((value % 10000000000000000L) / 1000000000000000L).asInstanceOf[scala.Int]))
      } else ()
      if (value >= 100000000000000L) {
        this.append(CharArray.DIGITS(((value % 1000000000000000L) / 100000000000000L).asInstanceOf[scala.Int]))
      } else ()
      if (value >= 10000000000000L) {
        this.append(CharArray.DIGITS(((value % 100000000000000L) / 10000000000000L).asInstanceOf[scala.Int]))
      } else ()
      if (value >= 1000000000000L) {
        this.append(CharArray.DIGITS(((value % 10000000000000L) / 1000000000000L).asInstanceOf[scala.Int]))
      } else ()
      if (value >= 100000000000L) {
        this.append(CharArray.DIGITS(((value % 1000000000000L) / 100000000000L).asInstanceOf[scala.Int]))
      } else ()
      if (value >= 10000000000L) {
        this.append(CharArray.DIGITS(((value % 100000000000L) / 10000000000L).asInstanceOf[scala.Int]))
      } else ()
      if (value >= 1000000000L) {
        this.append(CharArray.DIGITS(((value % 10000000000L) / 1000000000L).asInstanceOf[scala.Int]))
      } else ()
      if (value >= 100000000L) {
        this.append(CharArray.DIGITS(((value % 1000000000L) / 100000000L).asInstanceOf[scala.Int]))
      } else ()
      if (value >= 10000000L) {
        this.append(CharArray.DIGITS(((value % 100000000L) / 10000000L).asInstanceOf[scala.Int]))
      } else ()
      if (value >= 1000000L) {
        this.append(CharArray.DIGITS(((value % 10000000L) / 1000000L).asInstanceOf[scala.Int]))
      } else ()
      if (value >= 100000L) {
        this.append(CharArray.DIGITS(((value % 1000000L) / 100000L).asInstanceOf[scala.Int]))
      } else ()
      this.append(CharArray.DIGITS(((value % 100000L) / 10000L).asInstanceOf[scala.Int]))
    } else ()
    if (value >= 1000L) {
      this.append(CharArray.DIGITS(((value % 10000L) / 1000L).asInstanceOf[scala.Int]))
    } else ()
    if (value >= 100L) {
      this.append(CharArray.DIGITS(((value % 1000L) / 100L).asInstanceOf[scala.Int]))
    } else ()
    if (value >= 10L) {
      this.append(CharArray.DIGITS(((value % 100L) / 10L).asInstanceOf[scala.Int]))
    } else ()
    this.append(CharArray.DIGITS((value % 10L).asInstanceOf[scala.Int]))
    return this
  }
  def append(obj: java.lang.Object): CharArray = {
    if (obj == null) {
      return this.appendNull()
    } else ()
    if (obj.isInstanceOf[java.lang.CharSequence]) {
      return this.append(obj.asInstanceOf[java.lang.CharSequence].asInstanceOf[java.lang.CharSequence])
    } else ()
    return this.append(obj.toString())
  }
  def append(str: java.lang.String): CharArray = {
    if (str == null) {
      this.appendNull()
    } else {
      val length: scala.Int = str.length()
      this.require(length)
      str.getChars(0, length, this.items, this.size)
      this.size = this.size + length
    }
    return this
  }
  def append(str: java.lang.String, separator: java.lang.String): CharArray = {
    if (this.size > 0) {
      this.append(separator)
    } else ()
    this.append(str)
    return this
  }
  def append(str: java.lang.String, start: scala.Int, `end`: scala.Int): CharArray = {
    if (str == null) {
      return this.appendNull()
    } else ()
    if ((((start < 0) || (`end` < 0)) || (start > `end`)) || (`end` > str.length())) {
      throw new java.lang.IndexOutOfBoundsException()
    } else ()
    val length: scala.Int = `end` - start
    if (length > 0) {
      this.require(length)
      str.getChars(start, `end`, this.items, this.size)
      this.size = this.size + length
    } else ()
    return this
  }
  def append(str: java.lang.StringBuffer): CharArray = {
    if (str == null) {
      this.appendNull()
    } else {
      this.append(str, 0, str.length())
    }
    return this
  }
  def append(str: java.lang.StringBuffer, start: scala.Int, `end`: scala.Int): CharArray = {
    if (str == null) {
      return this.appendNull()
    } else ()
    if ((((start < 0) || (`end` < 0)) || (start > `end`)) || (`end` > str.length())) {
      throw new java.lang.IndexOutOfBoundsException()
    } else ()
    val length: scala.Int = `end` - start
    if (length > 0) {
      this.require(length)
      str.getChars(start, `end`, this.items, this.size)
      this.size = this.size + length
    } else ()
    return this
  }
  def append(str: java.lang.StringBuilder): CharArray = {
    if (str == null) {
      this.appendNull()
    } else {
      this.append(str, 0, str.length())
    }
    return this
  }
  def append(str: java.lang.StringBuilder, start: scala.Int, `end`: scala.Int): CharArray = {
    if (str == null) {
      return this.appendNull()
    } else ()
    if ((((start < 0) || (`end` < 0)) || (start > `end`)) || (`end` > str.length())) {
      throw new java.lang.IndexOutOfBoundsException()
    } else ()
    val length: scala.Int = `end` - start
    if (length > 0) {
      this.require(length)
      str.getChars(start, `end`, this.items, this.size)
      this.size = this.size + length
    } else ()
    return this
  }
  def append(str: CharArray): CharArray = {
    if (str == null) {
      this.appendNull()
    } else {
      this.append(str, 0, str.size)
    }
    return this
  }
  def append(str: CharArray, start: scala.Int, `end`: scala.Int): CharArray = {
    if (str == null) {
      return this.appendNull()
    } else ()
    if ((((start < 0) || (`end` < 0)) || (start > `end`)) || (`end` > str.size)) {
      throw new java.lang.IndexOutOfBoundsException()
    } else ()
    val length: scala.Int = `end` - start
    if (length > 0) {
      this.require(length)
      str.getChars(start, `end`, this.items, this.size)
      this.size = this.size + length
    } else ()
    return this
  }
  def appendAll(iterable: scala.collection.Iterable[?]): CharArray = {
    { val iter: scala.collection.Iterator[?] = iterable.iterator.asInstanceOf[scala.collection.Iterator[?]]; while (iter.hasNext) { {
      this.append(iter.next)
    };  } }
    return this
  }
  def appendAll(iter: scala.collection.Iterator[?]): CharArray = {
    while (iter.hasNext) {
      this.append(iter.next.asInstanceOf[java.lang.Object])
    }
    return this
  }
  final def appendAll[T](array: scala.Array[T]): CharArray = {
    if (array != null) {
      for (element <- array) {
        this.append(element)
      }
    } else ()
    return this
  }
  private def appendFalse(index$arg: scala.Int): scala.Unit = {
    var index: scala.Int = index$arg
    this.items({ index += 1; index }) = 'f'
    this.items({ index += 1; index }) = 'a'
    this.items({ index += 1; index }) = 'l'
    this.items({ index += 1; index }) = 's'
    this.items(index) = 'e'
    this.size = this.size + CharArray.FALSE_STRING_SIZE
  }
  def appendFixedWidthPadLeft(value: scala.Int, width: scala.Int, padChar: scala.Char): CharArray = {
    return this.appendFixedWidthPadLeft(java.lang.String.valueOf(value), width, padChar)
  }
  def appendFixedWidthPadLeft(obj: java.lang.Object, width: scala.Int, padChar: scala.Char): CharArray = {
    if (width > 0) {
      this.require(width)
      val str: java.lang.String = java.util.Objects.toString(obj, CharArray.NULL)
      val strLength: scala.Int = str.length()
      if (strLength >= width) {
        str.getChars(strLength - width, strLength, this.items, this.size)
      } else {
        val padLen: scala.Int = width - strLength
        val toIndex: scala.Int = this.size + padLen
        java.util.Arrays.fill(this.items, this.size, toIndex, padChar)
        str.getChars(0, strLength, this.items, toIndex)
      }
      this.size = this.size + width
    } else ()
    return this
  }
  def appendFixedWidthPadRight(value: scala.Int, width: scala.Int, padChar: scala.Char): CharArray = {
    return this.appendFixedWidthPadRight(java.lang.String.valueOf(value), width, padChar)
  }
  def appendFixedWidthPadRight(obj: java.lang.Object, width: scala.Int, padChar: scala.Char): CharArray = {
    if (width > 0) {
      this.require(width)
      val str: java.lang.String = java.util.Objects.toString(obj, CharArray.NULL)
      val strLength: scala.Int = str.length()
      if (strLength >= width) {
        str.getChars(0, width, this.items, this.size)
      } else {
        str.getChars(0, strLength, this.items, this.size)
        val fromIndex: scala.Int = this.size + strLength
        java.util.Arrays.fill(this.items, fromIndex, (fromIndex + width) - strLength, padChar)
      }
      this.size = this.size + width
    } else ()
    return this
  }
  def appendln(value: scala.Boolean): CharArray = {
    return this.append(value).appendLine()
  }
  def appendln(ch: scala.Char): CharArray = {
    return this.append(ch).appendLine()
  }
  def appendln(ch: scala.Array[scala.Char]): CharArray = {
    return this.append(ch).appendLine()
  }
  def appendln(ch: scala.Array[scala.Char], start: scala.Int, length: scala.Int): CharArray = {
    return this.append(ch, start, length).appendLine()
  }
  def appendln(value: scala.Double): CharArray = {
    return this.append(value).appendLine()
  }
  def appendln(value: scala.Float): CharArray = {
    return this.append(value).appendLine()
  }
  def appendln(value: scala.Int): CharArray = {
    return this.append(value).appendLine()
  }
  def appendln(value: scala.Long): CharArray = {
    return this.append(value).appendLine()
  }
  def appendln(obj: java.lang.Object): CharArray = {
    return this.append(obj).appendLine()
  }
  def appendln(str: java.lang.String): CharArray = {
    this.append(str)
    return this.append('\n')
  }
  def appendLine(str: java.lang.String): CharArray = {
    this.append(str)
    return this.append('\n')
  }
  def appendln(str: java.lang.String, start: scala.Int, `end`: scala.Int): CharArray = {
    return this.append(str, start, `end`).appendLine()
  }
  def appendln(str: java.lang.StringBuffer): CharArray = {
    return this.append(str).appendLine()
  }
  def appendln(str: java.lang.StringBuffer, start: scala.Int, `end`: scala.Int): CharArray = {
    return this.append(str, start, `end`).appendLine()
  }
  def appendln(str: java.lang.StringBuilder): CharArray = {
    return this.append(str).appendLine()
  }
  def appendln(str: java.lang.StringBuilder, start: scala.Int, `end`: scala.Int): CharArray = {
    return this.append(str, start, `end`).appendLine()
  }
  def appendln(str: CharArray): CharArray = {
    return this.append(str).appendLine()
  }
  def appendln(str: CharArray, start: scala.Int, `end`: scala.Int): CharArray = {
    return this.append(str, start, `end`).appendLine()
  }
  def appendln(): CharArray = {
    return this.append('\n')
  }
  def appendLine(): CharArray = {
    return this.append('\n')
  }
  def appendNull(): CharArray = {
    this.require(4)
    val length: scala.Int = this.size
    this.items(length) = 'n'
    this.items(length + 1) = 'u'
    this.items(length + 2) = 'l'
    this.items(length + 3) = 'l'
    this.size = length + 4
    return this
  }
  def appendPadding(padCount: scala.Int, padChar: scala.Char): CharArray = {
    if (padCount > 0) {
      this.require(padCount)
      java.util.Arrays.fill(this.items, this.size, this.size + padCount, padChar)
      this.size = this.size + padCount
    } else ()
    return this
  }
  def appendSeparator(separator: scala.Char): CharArray = {
    if (this.notEmpty()) {
      this.append(separator)
    } else ()
    return this
  }
  def appendSeparator(standard: scala.Char, defaultIfEmpty: scala.Char): CharArray = {
    if (this.isEmpty()) {
      this.append(defaultIfEmpty)
    } else {
      this.append(standard)
    }
    return this
  }
  def appendSeparator(separator: scala.Char, loopIndex: scala.Int): CharArray = {
    if (loopIndex > 0) {
      this.append(separator)
    } else ()
    return this
  }
  def appendSeparator(separator: java.lang.String): CharArray = {
    return this.appendSeparator(separator, null)
  }
  def appendSeparator(separator: java.lang.String, loopIndex: scala.Int): CharArray = {
    if ((separator != null) && (loopIndex > 0)) {
      this.append(separator)
    } else ()
    return this
  }
  def appendSeparator(standard: java.lang.String, defaultIfEmpty: java.lang.String): CharArray = {
    val str: java.lang.String = if (this.isEmpty()) defaultIfEmpty else standard
    if (str != null) {
      this.append(str)
    } else ()
    return this
  }
  def appendTo(appendable: java.lang.Appendable): scala.Unit = {
    if (appendable.isInstanceOf[java.io.Writer]) {
      appendable.asInstanceOf[java.io.Writer].write(this.items, 0, this.size)
    } else {
      if (appendable.isInstanceOf[java.lang.StringBuilder]) {
        appendable.asInstanceOf[java.lang.StringBuilder].append(this.items, 0, this.size)
      } else {
        if (appendable.isInstanceOf[java.lang.StringBuffer]) {
          appendable.asInstanceOf[java.lang.StringBuffer].append(this.items, 0, this.size)
        } else {
          if (appendable.isInstanceOf[java.nio.CharBuffer]) {
            appendable.asInstanceOf[java.nio.CharBuffer].put(this.items, 0, this.size)
          } else {
            appendable.append(this)
          }
        }
      }
    }
  }
  private def appendTrue(index$arg: scala.Int): scala.Unit = {
    var index: scala.Int = index$arg
    this.items({ index += 1; index }) = 't'
    this.items({ index += 1; index }) = 'r'
    this.items({ index += 1; index }) = 'u'
    this.items(index) = 'e'
    this.size = this.size + CharArray.TRUE_STRING_SIZE
  }
  def appendWithSeparators(iterable: scala.collection.Iterable[?], separator: java.lang.String): CharArray = {
    this.appendWithSeparators(iterable.iterator, separator)
    return this
  }
  def appendWithSeparators(it: scala.collection.Iterator[?], separator: java.lang.String): CharArray = {
    val sep: java.lang.String = java.util.Objects.toString(separator, "")
    while (it.hasNext) {
      this.append(it.next.asInstanceOf[java.lang.Object])
      if (it.hasNext) {
        this.append(sep)
      } else ()
    }
    return this
  }
  def appendWithSeparators(array: scala.Array[java.lang.Object], separator: java.lang.String): CharArray = {
    if (array.length > 0) {
      val sep: java.lang.String = java.util.Objects.toString(separator, "")
      this.append(array(0));
      { var i: scala.Int = 1; while (i < array.length) { {
        this.append(sep)
        this.append(array(i))
      }; i = i + 1 } }
    } else ()
    return this
  }
  def appendCodePoint(codePoint: scala.Int): CharArray = {
    this.append(java.lang.Character.toChars(codePoint))
    return this
  }
  def reader(): java.io.Reader = {
    return new CharArrayReader()
  }
  def writer(): java.io.Writer = {
    return new CharArrayWriter()
  }
  def charAt(index: scala.Int): scala.Char = {
    return this.items(index)
  }
  def codePointAt(index: scala.Int): scala.Int = {
    this.validateIndex(index)
    return java.lang.Character.codePointAt(this.items, index, this.size)
  }
  def codePointBefore(index: scala.Int): scala.Int = {
    if ((index < 1) || (index > this.size)) {
      throw new java.lang.IndexOutOfBoundsException((("index: " + index) + ", size: ") + this.size)
    } else ()
    return java.lang.Character.codePointBefore(this.items, index)
  }
  def codePointCount(begin: scala.Int, `end`: scala.Int): scala.Int = {
    if (((begin < 0) || (`end` > this.size)) || (begin > `end`)) {
      throw new java.lang.IndexOutOfBoundsException()
    } else ()
    return java.lang.Character.codePointCount(this.items, begin, `end` - begin)
  }
  def offsetByCodePoints(index: scala.Int, codePointOffset: scala.Int): scala.Int = {
    return java.lang.Character.offsetByCodePoints(this.items, 0, this.size, index, codePointOffset)
  }
  def contains(str: java.lang.String): scala.Boolean = {
    return this.indexOf(str, 0) >= 0
  }
  def containsIgnoreCase(str: java.lang.String): scala.Boolean = {
    return this.indexOfIgnoreCase(str, 0) != (-1)
  }
  def delete(start: scala.Int, `end`: scala.Int): CharArray = {
    val actualEnd: scala.Int = this.validateRange(start, `end`)
    val length: scala.Int = actualEnd - start
    if (length > 0) {
      this.delete(start, actualEnd, length)
    } else ()
    return this
  }
  def deleteAll(ch: scala.Char): CharArray = {
    { var i: scala.Int = 0; while (i < this.size) { {
      if (this.items(i) == ch) {
        val start: scala.Int = i
        while ({ i += 1; i } < this.size) {
          if (this.items(i) != ch) {
            /* break */ ()
          } else ()
        }
        val length: scala.Int = i - start
        this.delete(start, i, length)
        i = i - length
      } else ()
    }; i = i + 1 } }
    return this
  }
  def deleteAll(str: java.lang.String): CharArray = {
    if (str == null) {
      throw new java.lang.IllegalArgumentException("str cannot be null.")
    } else ()
    val length: scala.Int = str.length()
    if (length > 0) {
      var index: scala.Int = this.indexOf(str, 0)
      while (index >= 0) {
        this.delete(index, index + length, length)
        index = this.indexOf(str, index)
      }
    } else ()
    return this
  }
  def deleteCharAt(index: scala.Int): CharArray = {
    this.validateIndex(index)
    this.delete(index, index + 1, 1)
    return this
  }
  def deleteFirst(ch: scala.Char): CharArray = {
    { var i: scala.Int = 0; while (i < this.size) { {
      if (this.items(i) == ch) {
        this.delete(i, i + 1, 1)
        /* break */ ()
      } else ()
    }; i = i + 1 } }
    return this
  }
  def deleteFirst(str: java.lang.String): CharArray = {
    if (str == null) {
      throw new java.lang.IllegalArgumentException("str cannot be null.")
    } else ()
    val length: scala.Int = str.length()
    if (length > 0) {
      val index: scala.Int = this.indexOf(str, 0)
      if (index >= 0) {
        this.delete(index, index + length, length)
      } else ()
    } else ()
    return this
  }
  private def delete(start: scala.Int, `end`: scala.Int, length: scala.Int): scala.Unit = {
    java.lang.System.arraycopy(this.items, `end`, this.items, start, this.size - `end`)
    this.size = this.size - length
  }
  def drainChar(index: scala.Int): scala.Char = {
    this.validateIndex(index)
    val c: scala.Char = this.items(index)
    this.deleteCharAt(index)
    return c
  }
  def drainChars(start: scala.Int, `end`: scala.Int, target: scala.Array[scala.Char], targetIndex: scala.Int): scala.Int = {
    val length: scala.Int = `end` - start
    if ((this.isEmpty() || (length == 0)) || (target.length == 0)) {
      return 0
    } else ()
    val actualLength: scala.Int = java.lang.Math.min(java.lang.Math.min(this.size, length), target.length - targetIndex)
    this.getChars(start, start + actualLength, target, targetIndex)
    this.delete(start, start + actualLength)
    return actualLength
  }
  def endsWith(str: java.lang.String): scala.Boolean = {
    val length: scala.Int = str.length()
    if (length == 0) {
      return true
    } else ()
    if (length > this.size) {
      return false
    } else ()
    var pos: scala.Int = this.size - length;
    { var i: scala.Int = 0; while (i < length) { {
      if (this.items(pos) != str.charAt(i)) {
        return false
      } else ()
    }; i = i + 1; pos = pos + 1 } }
    return true
  }
  def getChars(target$arg: scala.Array[scala.Char]): scala.Array[scala.Char] = {
    var target: scala.Array[scala.Char] = target$arg
    val length: scala.Int = this.size
    if ((target == null) || (target.length < length)) {
      target = new scala.Array[scala.Char](length)
    } else ()
    java.lang.System.arraycopy(this.items, 0, target, 0, length)
    return target
  }
  def getChars(start: scala.Int, `end`: scala.Int, target: scala.Array[scala.Char], targetIndex: scala.Int): scala.Unit = {
    if (start < 0) {
      throw new java.lang.IndexOutOfBoundsException("start: " + start)
    } else ()
    if ((`end` < 0) || (`end` > this.size)) {
      throw new java.lang.IndexOutOfBoundsException((("end: " + `end`) + ", size: ") + this.size)
    } else ()
    if (start > `end`) {
      throw new java.lang.IndexOutOfBoundsException("end < start")
    } else ()
    java.lang.System.arraycopy(this.items, start, target, targetIndex, `end` - start)
  }
  def indexOf(ch: scala.Char, start$arg: scala.Int): scala.Int = {
    var start: scala.Int = start$arg
    start = java.lang.Math.max(0, start)
    if (start >= this.size) {
      return -1
    } else ()
    val thisBuf: scala.Array[scala.Char] = this.items;
    { var i: scala.Int = start; val n: scala.Int = this.size; while (i < n) { {
      if (thisBuf(i) == ch) {
        return i
      } else ()
    }; i = i + 1 } }
    return -1
  }
  def indexOf(str: java.lang.String): scala.Int = {
    return this.indexOf(str, 0)
  }
  def indexOf(str: java.lang.String, start$arg: scala.Int): scala.Int = {
    var start: scala.Int = start$arg
    if (str == null) {
      throw new java.lang.IllegalArgumentException("str cannot be null.")
    } else ()
    start = java.lang.Math.max(0, start)
    if (start >= this.size) {
      return -1
    } else ()
    val strLen: scala.Int = str.length()
    if (strLen == 1) {
      return this.indexOf(str.charAt(0), start)
    } else ()
    if (strLen == 0) {
      return start
    } else ()
    if (strLen > this.size) {
      return -1
    } else ()
    val thisBuf: scala.Array[scala.Char] = this.items
    val searchLen: scala.Int = (this.size - strLen) + 1;
    { var i: scala.Int = start; while (i < searchLen) { {
      var found: scala.Boolean = true;
      { var j: scala.Int = 0; while ((j < strLen) && found) { {
        found = str.charAt(j) == thisBuf(i + j)
      }; j = j + 1 } }
      if (found) {
        return i
      } else ()
    }; i = i + 1 } }
    return -1
  }
  def indexOfIgnoreCase(str: java.lang.String, start$arg: scala.Int): scala.Int = {
    {
      var start: scala.Int = start$arg
      if (start < 0) {
        start = 0
      } else ()
      val length: scala.Int = str.length()
      if (length == 0) {
        return if ((start < this.size) || (start == 0)) start else this.size
      } else ()
      val maxIndex: scala.Int = this.size - length
      if (start > maxIndex) {
        return -1
      } else ()
      val firstUpper: scala.Char = java.lang.Character.toUpperCase(str.charAt(0))
      val firstLower: scala.Char = java.lang.Character.toLowerCase(firstUpper)
      while (true) {
        var i: scala.Int = start
        var found: scala.Boolean = false;
        { ; while (i <= maxIndex) { {
          val c: scala.Char = this.items(i)
          if ((c == firstUpper) || (c == firstLower)) {
            found = true
            /* break */ ()
          } else ()
        }; i = i + 1 } }
        if (!found) {
          return -1
        } else ()
        var o1: scala.Int = i
        var o2: scala.Int = 0
        while ({ o2 += 1; o2 } < length) {
          val c: scala.Char = this.items({ o1 += 1; o1 })
          val upper: scala.Char = java.lang.Character.toUpperCase(str.charAt(o2))
          if ((c != upper) && (c != java.lang.Character.toLowerCase(upper))) {
            /* break */ ()
          } else ()
        }
        if (o2 == length) {
          return i
        } else ()
        start = i + 1
      }
    }
    throw new java.lang.RuntimeException("unreachable")
  }
  def insert(index: scala.Int, value: scala.Boolean): CharArray = {
    this.validateIndex(index)
    if (value) {
      this.require(CharArray.TRUE_STRING_SIZE)
      java.lang.System.arraycopy(this.items, index, this.items, index + CharArray.TRUE_STRING_SIZE, this.size - index)
      this.appendTrue(index)
    } else {
      this.require(CharArray.FALSE_STRING_SIZE)
      java.lang.System.arraycopy(this.items, index, this.items, index + CharArray.FALSE_STRING_SIZE, this.size - index)
      this.appendFalse(index)
    }
    return this
  }
  def insert(index: scala.Int, value: scala.Char): scala.Unit = {
    this.validateIndex(index)
    this.require(1)
    val items: scala.Array[scala.Char] = this.items
    if (this.ordered) {
      java.lang.System.arraycopy(items, index, items, index + 1, this.size - index)
    } else {
      items(this.size) = items(index)
    }
    this.size = this.size + 1
    items(index) = value
  }
  def insertRange(index: scala.Int, count: scala.Int): scala.Unit = {
    this.validateIndex(index)
    val sizeNeeded: scala.Int = this.size + count
    if (sizeNeeded > this.items.length) {
      this.items = this.resize(java.lang.Math.max(java.lang.Math.max(8, sizeNeeded), (this.size * 1.75f).asInstanceOf[scala.Int].asInstanceOf[scala.Int]))
    } else ()
    java.lang.System.arraycopy(this.items, index, this.items, index + count, this.size - index)
    this.size = sizeNeeded
  }
  def insert(index: scala.Int, ch: scala.Array[scala.Char]): CharArray = {
    this.validateIndex(index)
    if (ch == null) {
      return this.insert(index, CharArray.NULL)
    } else ()
    val length: scala.Int = ch.length
    if (length > 0) {
      this.require(length)
      java.lang.System.arraycopy(this.items, index, this.items, index + length, this.size - index)
      java.lang.System.arraycopy(ch, 0, this.items, index, length)
      this.size = this.size + length
    } else ()
    return this
  }
  def insert(index: scala.Int, ch: scala.Array[scala.Char], offset: scala.Int, length: scala.Int): CharArray = {
    this.validateIndex(index)
    if (ch == null) {
      return this.insert(index, CharArray.NULL)
    } else ()
    if ((offset < 0) || (offset > ch.length)) {
      throw new java.lang.IndexOutOfBoundsException("Invalid offset: " + offset)
    } else ()
    if ((length < 0) || ((offset + length) > ch.length)) {
      throw new java.lang.IndexOutOfBoundsException("Invalid length: " + length)
    } else ()
    if (length > 0) {
      this.require(length)
      java.lang.System.arraycopy(this.items, index, this.items, index + length, this.size - index)
      java.lang.System.arraycopy(ch, offset, this.items, index, length)
      this.size = this.size + length
    } else ()
    return this
  }
  def insert(index: scala.Int, value: scala.Double): CharArray = {
    return this.insert(index, java.lang.String.valueOf(value))
  }
  def insert(index: scala.Int, value: scala.Float): CharArray = {
    return this.insert(index, java.lang.String.valueOf(value))
  }
  def insert(index: scala.Int, value: scala.Int): CharArray = {
    return this.insert(index, java.lang.String.valueOf(value))
  }
  def insert(index: scala.Int, value: scala.Long): CharArray = {
    return this.insert(index, java.lang.String.valueOf(value))
  }
  def insert(index: scala.Int, obj: java.lang.Object): CharArray = {
    if (obj == null) {
      return this.insert(index, CharArray.NULL)
    } else ()
    return this.insert(index, obj.toString())
  }
  def insert(index: scala.Int, str$arg: java.lang.String): CharArray = {
    var str: java.lang.String = str$arg
    this.validateIndex(index)
    if (str == null) {
      str = CharArray.NULL
    } else ()
    val strLength: scala.Int = str.length()
    if (strLength > 0) {
      this.require(strLength)
      java.lang.System.arraycopy(this.items, index, this.items, index + strLength, this.size - index)
      this.size = this.size + strLength
      str.getChars(0, strLength, this.items, index)
    } else ()
    return this
  }
  def lastIndexOf(ch: scala.Char, start$arg: scala.Int): scala.Int = {
    var start: scala.Int = start$arg
    start = if (start >= this.size) this.size - 1 else start
    if (start < 0) {
      return -1
    } else ();
    { var i: scala.Int = start; while (i >= 0) { {
      if (this.items(i) == ch) {
        return i
      } else ()
    }; i = i - 1 } }
    return -1
  }
  def lastIndexOf(str: java.lang.String): scala.Int = {
    return this.lastIndexOf(str, this.size - 1)
  }
  def lastIndexOf(str: java.lang.String, start$arg: scala.Int): scala.Int = {
    var start: scala.Int = start$arg
    if (str == null) {
      throw new java.lang.IllegalArgumentException("str cannot be null.")
    } else ()
    start = if (start >= this.size) this.size - 1 else start
    if (start < 0) {
      return -1
    } else ()
    val strLen: scala.Int = str.length()
    if (strLen == 0) {
      return start
    } else ()
    if (strLen > this.size) {
      return -1
    } else ()
    if (strLen == 1) {
      return this.lastIndexOf(str.charAt(0), start)
    } else ();
    { var i: scala.Int = (start - strLen) + 1; while (i >= 0) { {
      var found: scala.Boolean = true;
      { var j: scala.Int = 0; while ((j < strLen) && found) { {
        found = str.charAt(j) == this.items(i + j)
      }; j = j + 1 } }
      if (found) {
        return i
      } else ()
    }; i = i - 1 } }
    return -1
  }
  def leftString(length: scala.Int): java.lang.String = {
    if (length <= 0) {
      return ""
    } else ()
    if (length >= this.size) {
      return new java.lang.String(this.items, 0, this.size)
    } else ()
    return new java.lang.String(this.items, 0, length)
  }
  def length(): scala.Int = {
    return this.size
  }
  def capacity(): scala.Int = {
    return this.items.length
  }
  def midString(index$arg: scala.Int, length: scala.Int): java.lang.String = {
    var index: scala.Int = index$arg
    if (index < 0) {
      index = 0
    } else ()
    if ((length <= 0) || (index >= this.size)) {
      return ""
    } else ()
    if (this.size <= (index + length)) {
      return new java.lang.String(this.items, index, this.size - index)
    } else ()
    return new java.lang.String(this.items, index, length)
  }
  def readFrom(charBuffer: java.nio.CharBuffer): scala.Int = {
    val oldSize: scala.Int = this.size
    val remaining: scala.Int = charBuffer.remaining()
    this.require(remaining)
    charBuffer.get(this.items, this.size, remaining)
    this.size = this.size + remaining
    return this.size - oldSize
  }
  def readFrom(readable: java.lang.Readable): scala.Int = {
    if (readable.isInstanceOf[java.io.Reader]) {
      return this.readFrom(readable.asInstanceOf[java.io.Reader])
    } else ()
    if (readable.isInstanceOf[java.nio.CharBuffer]) {
      return this.readFrom(readable.asInstanceOf[java.nio.CharBuffer])
    } else ()
    val oldSize: scala.Int = this.size
    while (true) {
      this.require(1)
      val buf: java.nio.CharBuffer = java.nio.CharBuffer.wrap(this.items, this.size, this.items.length - this.size)
      val read: scala.Int = readable.read(buf)
      if (read == CharArray.EOS) {
        /* break */ ()
      } else ()
      this.size = this.size + read
    }
    return this.size - oldSize
  }
  def readFrom(reader: java.io.Reader): scala.Int = {
    val oldSize: scala.Int = this.size
    this.require(1)
    var readCount: scala.Int = reader.read(this.items, this.size, this.items.length - this.size)
    if (readCount == CharArray.EOS) {
      return CharArray.EOS
    } else ()
    while ({ {
      this.size = this.size + readCount
      this.require(1)
      readCount = reader.read(this.items, this.size, this.items.length - this.size)
    }; readCount != CharArray.EOS }) ()
    return this.size - oldSize
  }
  def readFrom(reader: java.io.Reader, count: scala.Int): scala.Int = {
    if (count <= 0) {
      return 0
    } else ()
    val oldSize: scala.Int = this.size
    this.require(count)
    var target: scala.Int = count
    var readCount: scala.Int = reader.read(this.items, this.size, target)
    if (readCount == CharArray.EOS) {
      return CharArray.EOS
    } else ()
    while ({ {
      target = target - readCount
      this.size = this.size + readCount
      readCount = reader.read(this.items, this.size, target)
    }; (target > 0) && (readCount != CharArray.EOS) }) ()
    return this.size - oldSize
  }
  def replace(start: scala.Int, end$arg: scala.Int, replaceStr: java.lang.String): CharArray = {
    var `end`: scala.Int = end$arg
    `end` = this.validateRange(start, `end`)
    this.replace(start, `end`, `end` - start, replaceStr, replaceStr.length())
    return this
  }
  def replaceAll(searchStr: java.lang.String, replaceStr: java.lang.String): CharArray = {
    val searchLength: scala.Int = searchStr.length()
    if (searchLength > 0) {
      val replaceLength: scala.Int = replaceStr.length()
      var index: scala.Int = this.indexOf(searchStr, 0)
      while (index >= 0) {
        this.replace(index, index + searchLength, searchLength, replaceStr, replaceLength)
        index = this.indexOf(searchStr, index + replaceLength)
      }
    } else ()
    return this
  }
  def replace(find: scala.Char, replace: java.lang.String): CharArray = {
    {
      val replaceLength: scala.Int = replace.length()
      var index: scala.Int = 0
      while (true) {
        while (true) {
          if (index == this.size) {
            return this
          } else ()
          if (this.items(index) == find) {
            /* break */ ()
          } else ()
          index = index + 1
        }
        this.replace(index, index + 1, 1, replace, replaceLength)
        index = index + replaceLength
      }
    }
    throw new java.lang.RuntimeException("unreachable")
  }
  def replaceFirst(searchStr: java.lang.String, replaceStr: java.lang.String): CharArray = {
    val searchLength: scala.Int = searchStr.length()
    if (searchLength > 0) {
      val index: scala.Int = this.indexOf(searchStr, 0)
      if (index >= 0) {
        val replaceLength: scala.Int = replaceStr.length()
        this.replace(index, index + searchLength, searchLength, replaceStr, replaceLength)
      } else ()
    } else ()
    return this
  }
  private def replace(start: scala.Int, `end`: scala.Int, removeLength: scala.Int, insertStr: java.lang.String, insertLength: scala.Int): scala.Unit = {
    val newSize: scala.Int = (this.size - removeLength) + insertLength
    if (insertLength != removeLength) {
      this.require(newSize)
      java.lang.System.arraycopy(this.items, `end`, this.items, start + insertLength, this.size - `end`)
      this.size = newSize
    } else ()
    if (insertLength > 0) {
      insertStr.getChars(0, insertLength, this.items, start)
    } else ()
  }
  def reverse(): scala.Unit = {
    val items: scala.Array[scala.Char] = this.items;
    { var i: scala.Int = 0; val lastIndex: scala.Int = this.size - 1; val n: scala.Int = this.size / 2; while (i < n) { {
      val ii: scala.Int = lastIndex - i
      val temp: scala.Char = items(i)
      items(i) = items(ii)
      items(ii) = temp
    }; i = i + 1 } }
  }
  def reverseCodePoints(): CharArray = {
    if (this.size < 2) {
      return this
    } else ()
    var `end`: scala.Int = this.size - 1
    var frontHigh: scala.Char = this.items(0)
    var endLow: scala.Char = this.items(`end`)
    var allowFrontSur: scala.Boolean = true
    var allowEndSur: scala.Boolean = true;
    { var i: scala.Int = 0; val mid: scala.Int = this.size / 2; while (i < mid) { {
      val frontLow: scala.Char = this.items(i + 1)
      val endHigh: scala.Char = this.items(`end` - 1)
      val surAtFront: scala.Boolean = (((allowFrontSur && (frontLow >= 56320)) && (frontLow <= 57343)) && (frontHigh >= 55296)) && (frontHigh <= 56319)
      if (surAtFront && (this.size < 3)) {
        return this
      } else ()
      val surAtEnd: scala.Boolean = (((allowEndSur && (endHigh >= 55296)) && (endHigh <= 56319)) && (endLow >= 56320)) && (endLow <= 57343)
      allowFrontSur = {
        allowEndSur = true
        allowEndSur
      }
      if (surAtFront == surAtEnd) {
        if (surAtFront) {
          this.items(`end`) = frontLow
          this.items(`end` - 1) = frontHigh
          this.items(i) = endHigh
          this.items(i + 1) = endLow
          frontHigh = this.items(i + 2)
          endLow = this.items(`end` - 2)
          i = i + 1
          `end` = `end` - 1
        } else {
          this.items(`end`) = frontHigh
          this.items(i) = endLow
          frontHigh = frontLow
          endLow = endHigh
        }
      } else {
        if (surAtFront) {
          this.items(`end`) = frontLow
          this.items(i) = endLow
          endLow = endHigh
          allowFrontSur = false
        } else {
          this.items(`end`) = frontHigh
          this.items(i) = endHigh
          frontHigh = frontLow
          allowEndSur = false
        }
      }
    }; i = i + 1; `end` = `end` - 1 } }
    if (((this.size & 1) == 1) && ((!allowFrontSur) || (!allowEndSur))) {
      this.items(`end`) = if (allowFrontSur) endLow else frontHigh
    } else ()
    return this
  }
  def rightString(length: scala.Int): java.lang.String = {
    if (length <= 0) {
      return ""
    } else ()
    if (length >= this.size) {
      return new java.lang.String(this.items, 0, this.size)
    } else ()
    return new java.lang.String(this.items, this.size - length, length)
  }
  def set(str: java.lang.CharSequence): CharArray = {
    this.clear()
    this.append(str)
    return this
  }
  def setCharAt(index: scala.Int, ch: scala.Char): CharArray = {
    this.validateIndex(index)
    this.items(index) = ch
    return this
  }
  def setLength(length: scala.Int): CharArray = {
    if (length < 0) {
      throw new java.lang.IndexOutOfBoundsException("length: " + length)
    } else ()
    if (length < this.size) {
      this.size = length
    } else {
      if (length > this.size) {
        this.require(length - this.size)
        val oldEnd: scala.Int = this.size
        this.size = length
        java.util.Arrays.fill(this.items, oldEnd, length, ' ')
      } else ()
    }
    return this
  }
  def startsWith(str: java.lang.String): scala.Boolean = {
    val length: scala.Int = str.length()
    if (length == 0) {
      return true
    } else ()
    if (length > this.size) {
      return false
    } else ();
    { var i: scala.Int = 0; while (i < length) { {
      if (this.items(i) != str.charAt(i)) {
        return false
      } else ()
    }; i = i + 1 } }
    return true
  }
  def subSequence(start: scala.Int, `end`: scala.Int): java.lang.CharSequence = {
    this.validateRange(start, `end`)
    return this.substring(start, `end`)
  }
  def substring(start: scala.Int): java.lang.String = {
    return this.substring(start, this.size)
  }
  def substring(start: scala.Int, end$arg: scala.Int): java.lang.String = {
    var `end`: scala.Int = end$arg
    `end` = this.validateRange(start, `end`)
    return new java.lang.String(this.items, start, `end` - start)
  }
  def toCharArray(): scala.Array[scala.Char] = {
    return java.util.Arrays.copyOf(this.items, this.size)
  }
  def toCharArray(start: scala.Int, end$arg: scala.Int): scala.Array[scala.Char] = {
    var `end`: scala.Int = end$arg
    `end` = this.validateRange(start, `end`)
    return java.util.Arrays.copyOfRange(this.items, start, `end`)
  }
  def toString(): java.lang.String = {
    if (this.size == 0) {
      return ""
    } else ()
    return new java.lang.String(this.items, 0, this.size)
  }
  def toString(separator: java.lang.String): java.lang.String = {
    if (this.size == 0) {
      return ""
    } else ()
    val items: scala.Array[scala.Char] = this.items
    val buffer: java.lang.StringBuilder = new java.lang.StringBuilder(32)
    buffer.append(items(0));
    { var i: scala.Int = 1; while (i < this.size) { {
      buffer.append(separator)
      buffer.append(items(i))
    }; i = i + 1 } }
    return buffer.toString()
  }
  def toStringAndClear(): java.lang.String = {
    val string: java.lang.String = this.toString()
    this.clear()
    return string
  }
  def trim(): CharArray = {
    if (this.size == 0) {
      return this
    } else ()
    var length: scala.Int = this.size
    val buf: scala.Array[scala.Char] = this.items
    var pos: scala.Int = 0
    while ((pos < length) && (buf(pos) <= ' ')) {
      pos = pos + 1
    }
    while ((pos < length) && (buf(length - 1) <= ' ')) {
      length = length - 1
    }
    if (length < this.size) {
      this.delete(length, this.size)
    } else ()
    if (pos > 0) {
      this.delete(0, pos)
    } else ()
    return this
  }
  def validateIndex(index: scala.Int): scala.Unit = {
    if ((index < 0) || (index > this.size)) {
      throw new java.lang.IndexOutOfBoundsException((("index: " + index) + ", size: ") + this.size)
    } else ()
  }
  def validateRange(start: scala.Int, `end`: scala.Int): scala.Int = {
    if (start < 0) {
      throw new java.lang.IndexOutOfBoundsException("start: " + start)
    } else ()
    if (`end` > this.size) {
      throw new java.lang.IndexOutOfBoundsException((("end: " + `end`) + ", size: ") + this.size)
    } else ()
    if (start > `end`) {
      throw new java.lang.IndexOutOfBoundsException((("start: " + start) + ", end: ") + `end`)
    } else ()
    return `end`
  }
  def equals(`object`: java.lang.Object): scala.Boolean = {
    if (this == `object`) {
      return true
    } else ()
    if (!this.ordered) {
      return false
    } else ()
    if (`object` == null) {
      return false
    } else ()
    if (!`object`.isInstanceOf[CharArray]) {
      return false
    } else ()
    val other: CharArray = `object`.asInstanceOf[CharArray].asInstanceOf[CharArray]
    if (!other.ordered) {
      return false
    } else ()
    val length: scala.Int = this.size
    if (length != other.size) {
      return false
    } else ()
    val chars: scala.Array[scala.Char] = this.items
    val chars2: scala.Array[scala.Char] = other.items;
    { var i: scala.Int = 0; while (i < length) { {
      if (chars(i) != chars2(i)) {
        return false
      } else ()
    }; i = i + 1 } }
    return true
  }
  def equals(other: CharArray): scala.Boolean = {
    if (this == other) {
      return true
    } else ()
    if (other == null) {
      return false
    } else ()
    val length: scala.Int = this.size
    if (length != other.size) {
      return false
    } else ()
    val chars: scala.Array[scala.Char] = this.items
    val chars2: scala.Array[scala.Char] = other.items;
    { var i: scala.Int = 0; while (i < length) { {
      if (chars(i) != chars2(i)) {
        return false
      } else ()
    }; i = i + 1 } }
    return true
  }
  def equalsIgnoreCase(other: CharArray): scala.Boolean = {
    if (this == other) {
      return true
    } else ()
    if (other == null) {
      return false
    } else ()
    val length: scala.Int = this.size
    if (length != other.size) {
      return false
    } else ()
    val chars: scala.Array[scala.Char] = this.items
    val chars2: scala.Array[scala.Char] = other.items;
    { var i: scala.Int = 0; while (i < length) { {
      val c: scala.Char = chars(i)
      val upper: scala.Char = java.lang.Character.toUpperCase(chars2(i))
      if ((c != upper) && (c != java.lang.Character.toLowerCase(upper))) {
        return false
      } else ()
    }; i = i + 1 } }
    return true
  }
  def equalsString(other: java.lang.String): scala.Boolean = {
    if (other == null) {
      return false
    } else ()
    val length: scala.Int = this.size
    if (length != other.length()) {
      return false
    } else ()
    val chars: scala.Array[scala.Char] = this.items;
    { var i: scala.Int = 0; while (i < length) { {
      if (chars(i) != other.charAt(i)) {
        return false
      } else ()
    }; i = i + 1 } }
    return true
  }
  def equalsIgnoreCase(other: java.lang.String): scala.Boolean = {
    if (other == null) {
      return false
    } else ()
    val length: scala.Int = this.size
    if (length != other.length()) {
      return false
    } else ()
    val chars: scala.Array[scala.Char] = this.items;
    { var i: scala.Int = 0; while (i < length) { {
      val c: scala.Char = chars(i)
      val upper: scala.Char = java.lang.Character.toUpperCase(other.charAt(i))
      if ((c != upper) && (c != java.lang.Character.toLowerCase(upper))) {
        return false
      } else ()
    }; i = i + 1 } }
    return true
  }
  def hashCode(): scala.Int = {
    if (!this.ordered) {
      return super.hashCode()
    } else ()
    val chars: scala.Array[scala.Char] = this.items
    var result: scala.Int = 31 + this.size;
    { var index: scala.Int = 0; while (index < this.size) { {
      result = (31 * result) + chars(index)
    }; index = index + 1 } }
    return result
  }
  class CharArrayReader extends java.io.Reader {
    var mark$field: scala.Int = 0
    private var pos: scala.Int = 0
    def close(): scala.Unit = {
      ()
    }
    def mark(readAheadLimit: scala.Int): scala.Unit = {
      this.mark$field = this.pos
    }
    def markSupported(): scala.Boolean = {
      return true
    }
    def read(): scala.Int = {
      if (!this.ready()) {
        return -1
      } else ()
      return CharArray.this.charAt({ this.pos += 1; this.pos })
    }
    def read(b: scala.Array[scala.Char], off: scala.Int, length$arg: scala.Int): scala.Int = {
      var length: scala.Int = length$arg
      if (((((off < 0) || (length < 0)) || (off > b.length)) || ((off + length) > b.length)) || ((off + length) < 0)) {
        throw new java.lang.IndexOutOfBoundsException()
      } else ()
      if (length == 0) {
        return 0
      } else ()
      if (this.pos >= CharArray.this.size) {
        return -1
      } else ()
      if ((this.pos + length) > CharArray.this.size) {
        length = CharArray.this.size - this.pos
      } else ()
      CharArray.this.getChars(this.pos, this.pos + length, b, off)
      this.pos = this.pos + length
      return length
    }
    def ready(): scala.Boolean = {
      return this.pos < CharArray.this.size
    }
    def reset(): scala.Unit = {
      this.pos = this.mark$field
    }
    def skip(n$arg: scala.Long): scala.Long = {
      var n: scala.Long = n$arg
      if ((this.pos + n) > CharArray.this.size) {
        n = CharArray.this.size - this.pos
      } else ()
      if (n < 0) {
        return 0
      } else ()
      this.pos = (this.pos + n.asInstanceOf[scala.Int]).asInstanceOf[scala.Int]
      return n
    }
  }
  class CharArrayWriter extends java.io.Writer {
    def close(): scala.Unit = {
      ()
    }
    def flush(): scala.Unit = {
      ()
    }
    def write(cbuf: scala.Array[scala.Char]): scala.Unit = {
      CharArray.this.append(cbuf)
    }
    def write(cbuf: scala.Array[scala.Char], off: scala.Int, length: scala.Int): scala.Unit = {
      CharArray.this.append(cbuf, off, length)
    }
    def write(c: scala.Int): scala.Unit = {
      CharArray.this.append(c.asInstanceOf[scala.Char].asInstanceOf[scala.Char])
    }
    def write(str: java.lang.String): scala.Unit = {
      CharArray.this.append(str)
    }
    def write(str: java.lang.String, off: scala.Int, length: scala.Int): scala.Unit = {
      CharArray.this.append(str, off, length)
    }
  }
}
object CharArray {
  private final val CAPACITY: scala.Int = 16
  private final val EOS: scala.Int = -1
  private final val FALSE_STRING_SIZE: scala.Int = 5
  private final val TRUE_STRING_SIZE: scala.Int = 4
  private final val NULL: java.lang.String = "null"
  private final val DIGITS: scala.Array[scala.Char] = scala.Array[scala.Char]('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
  private final val MAX_BUFFER_SIZE: scala.Int = java.lang.Integer.MAX_VALUE - 8
  def wrap(initialBuffer: scala.Array[scala.Char]): CharArray = {
    return new CharArray(initialBuffer, initialBuffer.length)
  }
  def wrap(initialBuffer: scala.Array[scala.Char], length: scala.Int): CharArray = {
    return new CharArray(initialBuffer, length)
  }
  def `with`(array: scala.Array[scala.Char]): CharArray = {
    return new CharArray(array)
  }
  def numChars(value$arg: scala.Int, radix: scala.Int): scala.Int = {
    var value: scala.Int = value$arg
    var result: scala.Int = if (value < 0) 2 else 1
    while ({
      value = value / radix
      value
    } != 0) {
      result = result + 1
    }
    return result
  }
  def numChars(value$arg: scala.Long, radix: scala.Int): scala.Int = {
    var value: scala.Long = value$arg
    var result: scala.Int = if (value < 0) 2 else 1
    while ({
      value = value / radix
      value
    } != 0) {
      result = result + 1
    }
    return result
  }
}