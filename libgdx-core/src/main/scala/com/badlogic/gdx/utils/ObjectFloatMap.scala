package com.badlogic.gdx.utils

class ObjectFloatMap[K] extends scala.collection.Iterable[com.badlogic.gdx.utils.ObjectFloatMap.Entry[K]] {
  var size: scala.Int = 0
  var keyTable: scala.Array[K] = null.asInstanceOf[scala.Array[K]]
  var valueTable: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  var loadFactor: scala.Float = 0.0f
  var threshold: scala.Int = 0
  var shift: scala.Int = 0
  var mask: scala.Int = 0
  var entries1: com.badlogic.gdx.utils.ObjectFloatMap.Entries[?] = null.asInstanceOf[com.badlogic.gdx.utils.ObjectFloatMap.Entries[?]]
  var entries2: com.badlogic.gdx.utils.ObjectFloatMap.Entries[?] = null.asInstanceOf[com.badlogic.gdx.utils.ObjectFloatMap.Entries[?]]
  var values1: com.badlogic.gdx.utils.ObjectFloatMap.Values = null.asInstanceOf[com.badlogic.gdx.utils.ObjectFloatMap.Values]
  var values2: com.badlogic.gdx.utils.ObjectFloatMap.Values = null.asInstanceOf[com.badlogic.gdx.utils.ObjectFloatMap.Values]
  var keys1: com.badlogic.gdx.utils.ObjectFloatMap.Keys[?] = null.asInstanceOf[com.badlogic.gdx.utils.ObjectFloatMap.Keys[?]]
  var keys2: com.badlogic.gdx.utils.ObjectFloatMap.Keys[?] = null.asInstanceOf[com.badlogic.gdx.utils.ObjectFloatMap.Keys[?]]
  def this(initialCapacity: scala.Int, loadFactor: scala.Float) = {
    this()
    if ((loadFactor <= 0.0f) || (loadFactor >= 1.0f)) {
      throw new java.lang.IllegalArgumentException("loadFactor must be > 0 and < 1: " + loadFactor)
    } else ()
    this.loadFactor = loadFactor
    val tableSize: scala.Int = com.badlogic.gdx.utils.ObjectSet.tableSize(initialCapacity, loadFactor)
    this.threshold = (tableSize * loadFactor).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    this.mask = tableSize - 1
    this.shift = java.lang.Long.numberOfLeadingZeros(this.mask)
    this.keyTable = new scala.Array[java.lang.Object](tableSize).asInstanceOf[scala.Array[K]].asInstanceOf[scala.Array[K]]
    this.valueTable = new scala.Array[scala.Float](tableSize)
  }
  def this(initialCapacity: scala.Int) = {
    this(initialCapacity, 0.8f)
  }
  def this(map: ObjectFloatMap[? <: K]) = {
    this(java.lang.Math.floor(map.keyTable.length * map.loadFactor).asInstanceOf[scala.Int].asInstanceOf[scala.Int], map.loadFactor)
    java.lang.System.arraycopy(map.keyTable, 0, this.keyTable, 0, map.keyTable.length)
    java.lang.System.arraycopy(map.valueTable, 0, this.valueTable, 0, map.valueTable.length)
    this.size = map.size
  }
  def place(item: K): scala.Int = {
    return ((item.hashCode() * -7046029254386353131L) >>> this.shift).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
  }
  def locateKey(key: K): scala.Int = {
    {
      if (key == null) {
        throw new java.lang.IllegalArgumentException("key cannot be null.")
      } else ()
      val keyTable: scala.Array[K] = this.keyTable;
      { var i: scala.Int = this.place(key); while (true) { {
        val other: K = keyTable(i)
        if (other == null) {
          return -(i + 1)
        } else ()
        if (other.equals(key)) {
          return i
        } else ()
      }; i = (i + 1) & this.mask } }
    }
    throw new java.lang.RuntimeException("unreachable")
  }
  def put(key: K, value: scala.Float): scala.Unit = {
    var i: scala.Int = this.locateKey(key)
    if (i >= 0) {
      this.valueTable(i) = value
      return
    } else ()
    i = -(i + 1)
    this.keyTable(i) = key
    this.valueTable(i) = value
    if ({ this.size += 1; this.size } >= this.threshold) {
      this.resize(this.keyTable.length << 1)
    } else ()
  }
  def put(key: K, value: scala.Float, defaultValue: scala.Float): scala.Float = {
    var i: scala.Int = this.locateKey(key)
    if (i >= 0) {
      val oldValue: scala.Float = this.valueTable(i)
      this.valueTable(i) = value
      return oldValue
    } else ()
    i = -(i + 1)
    this.keyTable(i) = key
    this.valueTable(i) = value
    if ({ this.size += 1; this.size } >= this.threshold) {
      this.resize(this.keyTable.length << 1)
    } else ()
    return defaultValue
  }
  def putAll(map: ObjectFloatMap[? <: K]): scala.Unit = {
    this.ensureCapacity(map.size)
    val keyTable: scala.Array[K] = map.keyTable.asInstanceOf[scala.Array[K]]
    val valueTable: scala.Array[scala.Float] = map.valueTable
    var key: K = null.asInstanceOf[K];
    { var i: scala.Int = 0; val n: scala.Int = keyTable.length; while (i < n) { {
      key = keyTable(i)
      if (key != null) {
        this.put(key, valueTable(i))
      } else ()
    }; i = i + 1 } }
  }
  private def putResize(key: K, value: scala.Float): scala.Unit = {
    val keyTable: scala.Array[K] = this.keyTable;
    { var i: scala.Int = this.place(key); while (true) { {
      if (keyTable(i) == null) {
        keyTable(i) = key
        this.valueTable(i) = value
        return
      } else ()
    }; i = (i + 1) & this.mask } }
  }
  def get(key: K, defaultValue: scala.Float): scala.Float = {
    val i: scala.Int = this.locateKey(key)
    return if (i < 0) defaultValue else this.valueTable(i)
  }
  def getAndIncrement(key: K, defaultValue: scala.Float, increment: scala.Float): scala.Float = {
    var i: scala.Int = this.locateKey(key)
    if (i >= 0) {
      val oldValue: scala.Float = this.valueTable(i)
      this.valueTable(i) = this.valueTable(i) + increment
      return oldValue
    } else ()
    i = -(i + 1)
    this.keyTable(i) = key
    this.valueTable(i) = defaultValue + increment
    if ({ this.size += 1; this.size } >= this.threshold) {
      this.resize(this.keyTable.length << 1)
    } else ()
    return defaultValue
  }
  def remove(key$arg: K, defaultValue: scala.Float): scala.Float = {
    var key: K = key$arg
    var i: scala.Int = this.locateKey(key)
    if (i < 0) {
      return defaultValue
    } else ()
    val keyTable: scala.Array[K] = this.keyTable
    val valueTable: scala.Array[scala.Float] = this.valueTable
    val oldValue: scala.Float = valueTable(i)
    val mask: scala.Int = this.mask
    var next: scala.Int = (i + 1) & mask
    while ({
      key = keyTable(next)
      key
    } != null) {
      val placement: scala.Int = this.place(key)
      if (((next - placement) & mask) > ((i - placement) & mask)) {
        keyTable(i) = key
        valueTable(i) = valueTable(next)
        i = next
      } else ()
      next = (next + 1) & mask
    }
    keyTable(i) = null.asInstanceOf[K]
    this.size = this.size - 1
    return oldValue
  }
  def notEmpty(): scala.Boolean = {
    return this.size > 0
  }
  def isEmpty(): scala.Boolean = {
    return this.size == 0
  }
  def shrink(maximumCapacity: scala.Int): scala.Unit = {
    if (maximumCapacity < 0) {
      throw new java.lang.IllegalArgumentException("maximumCapacity must be >= 0: " + maximumCapacity)
    } else ()
    val tableSize: scala.Int = com.badlogic.gdx.utils.ObjectSet.tableSize(maximumCapacity, this.loadFactor)
    if (this.keyTable.length > tableSize) {
      this.resize(tableSize)
    } else ()
  }
  def clear(maximumCapacity: scala.Int): scala.Unit = {
    val tableSize: scala.Int = com.badlogic.gdx.utils.ObjectSet.tableSize(maximumCapacity, this.loadFactor)
    if (this.keyTable.length <= tableSize) {
      this.clear()
      return
    } else ()
    this.size = 0
    this.resize(tableSize)
  }
  def clear(): scala.Unit = {
    if (this.size == 0) {
      return
    } else ()
    this.size = 0
    java.util.Arrays.fill(this.keyTable.asInstanceOf[scala.Array[java.lang.Object]], null)
  }
  def containsValue(value: scala.Float): scala.Boolean = {
    val keyTable: scala.Array[K] = this.keyTable
    val valueTable: scala.Array[scala.Float] = this.valueTable;
    { var i: scala.Int = valueTable.length - 1; while (i >= 0) { {
      if ((keyTable(i) != null) && (valueTable(i) == value)) {
        return true
      } else ()
    }; i = i - 1 } }
    return false
  }
  def containsValue(value: scala.Float, epsilon: scala.Float): scala.Boolean = {
    val keyTable: scala.Array[K] = this.keyTable
    val valueTable: scala.Array[scala.Float] = this.valueTable;
    { var i: scala.Int = valueTable.length - 1; while (i >= 0) { {
      if ((keyTable(i) != null) && (java.lang.Math.abs(valueTable(i) - value) <= epsilon)) {
        return true
      } else ()
    }; i = i - 1 } }
    return false
  }
  def containsKey(key: K): scala.Boolean = {
    return this.locateKey(key) >= 0
  }
  def findKey(value: scala.Float): K = {
    val keyTable: scala.Array[K] = this.keyTable
    val valueTable: scala.Array[scala.Float] = this.valueTable;
    { var i: scala.Int = valueTable.length - 1; while (i >= 0) { {
      val key: K = keyTable(i)
      if ((key != null) && (valueTable(i) == value)) {
        return key
      } else ()
    }; i = i - 1 } }
    return null.asInstanceOf[K]
  }
  def findKey(value: scala.Float, epsilon: scala.Float): K = {
    val keyTable: scala.Array[K] = this.keyTable
    val valueTable: scala.Array[scala.Float] = this.valueTable;
    { var i: scala.Int = valueTable.length - 1; while (i >= 0) { {
      val key: K = keyTable(i)
      if ((key != null) && (java.lang.Math.abs(valueTable(i) - value) <= epsilon)) {
        return key
      } else ()
    }; i = i - 1 } }
    return null.asInstanceOf[K]
  }
  def ensureCapacity(additionalCapacity: scala.Int): scala.Unit = {
    val tableSize: scala.Int = com.badlogic.gdx.utils.ObjectSet.tableSize(this.size + additionalCapacity, this.loadFactor)
    if (this.keyTable.length < tableSize) {
      this.resize(tableSize)
    } else ()
  }
  final def resize(newSize: scala.Int): scala.Unit = {
    val oldCapacity: scala.Int = this.keyTable.length
    this.threshold = (newSize * this.loadFactor).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    this.mask = newSize - 1
    this.shift = java.lang.Long.numberOfLeadingZeros(this.mask)
    val oldKeyTable: scala.Array[K] = this.keyTable
    val oldValueTable: scala.Array[scala.Float] = this.valueTable
    this.keyTable = new scala.Array[java.lang.Object](newSize).asInstanceOf[scala.Array[K]].asInstanceOf[scala.Array[K]]
    this.valueTable = new scala.Array[scala.Float](newSize)
    if (this.size > 0) {
      { var i: scala.Int = 0; while (i < oldCapacity) { {
        val key: K = oldKeyTable(i)
        if (key != null) {
          this.putResize(key, oldValueTable(i))
        } else ()
      }; i = i + 1 } }
    } else ()
  }
  def hashCode(): scala.Int = {
    var h: scala.Int = this.size
    val keyTable: scala.Array[K] = this.keyTable
    val valueTable: scala.Array[scala.Float] = this.valueTable;
    { var i: scala.Int = 0; val n: scala.Int = keyTable.length; while (i < n) { {
      val key: K = keyTable(i)
      if (key != null) {
        h = h + (key.hashCode() + com.badlogic.gdx.utils.NumberUtils.floatToRawIntBits(valueTable(i)))
      } else ()
    }; i = i + 1 } }
    return h
  }
  def equals(obj: java.lang.Object): scala.Boolean = {
    if (obj == this) {
      return true
    } else ()
    if (!obj.isInstanceOf[ObjectFloatMap[?]]) {
      return false
    } else ()
    val other: ObjectFloatMap[?] = obj.asInstanceOf[ObjectFloatMap[?]].asInstanceOf[ObjectFloatMap[?]]
    if (other.size != this.size) {
      return false
    } else ()
    val keyTable: scala.Array[K] = this.keyTable
    val valueTable: scala.Array[scala.Float] = this.valueTable;
    { var i: scala.Int = 0; val n: scala.Int = keyTable.length; while (i < n) { {
      val key: K = keyTable(i)
      if (key != null) {
        val otherValue: scala.Float = other.get(key, 0)
        if ((otherValue == 0) && (!other.containsKey(key))) {
          return false
        } else ()
        if (otherValue != valueTable(i)) {
          return false
        } else ()
      } else ()
    }; i = i + 1 } }
    return true
  }
  def toString(separator: java.lang.String): java.lang.String = {
    return this.toString(separator, false)
  }
  def toString(): java.lang.String = {
    return this.toString(", ", true)
  }
  private def toString(separator: java.lang.String, braces: scala.Boolean): java.lang.String = {
    if (this.size == 0) {
      return if (braces) "{}" else ""
    } else ()
    val buffer: java.lang.StringBuilder = new java.lang.StringBuilder(32)
    if (braces) {
      buffer.append('{')
    } else ()
    val keyTable: scala.Array[K] = this.keyTable
    val valueTable: scala.Array[scala.Float] = this.valueTable
    var i: scala.Int = keyTable.length
    while ({ i -= 1; i } > 0) {
      val key: K = keyTable(i)
      if (key == null) {
        /* continue */ ()
      } else ()
      buffer.append(key)
      buffer.append('=')
      buffer.append(valueTable(i))
      /* break */ ()
    }
    while ({ i -= 1; i } > 0) {
      val key: K = keyTable(i)
      if (key == null) {
        /* continue */ ()
      } else ()
      buffer.append(separator)
      buffer.append(key)
      buffer.append('=')
      buffer.append(valueTable(i))
    }
    if (braces) {
      buffer.append('}')
    } else ()
    return buffer.toString()
  }
  def iterator(): com.badlogic.gdx.utils.ObjectFloatMap.Entries[K] = {
    return this.entries()
  }
  def entries(): com.badlogic.gdx.utils.ObjectFloatMap.Entries[K] = {
    if (com.badlogic.gdx.utils.Collections.allocateIterators) {
      return new com.badlogic.gdx.utils.ObjectFloatMap.Entries(this)
    } else ()
    if (this.entries1 == null) {
      this.entries1 = new com.badlogic.gdx.utils.ObjectFloatMap.Entries(this)
      this.entries2 = new com.badlogic.gdx.utils.ObjectFloatMap.Entries(this)
    } else ()
    if (!this.entries1.valid) {
      this.entries1.reset()
      this.entries1.valid = true
      this.entries2.valid = false
      return this.entries1
    } else ()
    this.entries2.reset()
    this.entries2.valid = true
    this.entries1.valid = false
    return this.entries2
  }
  def values(): com.badlogic.gdx.utils.ObjectFloatMap.Values = {
    if (com.badlogic.gdx.utils.Collections.allocateIterators) {
      return new com.badlogic.gdx.utils.ObjectFloatMap.Values(this)
    } else ()
    if (this.values1 == null) {
      this.values1 = new com.badlogic.gdx.utils.ObjectFloatMap.Values(this)
      this.values2 = new com.badlogic.gdx.utils.ObjectFloatMap.Values(this)
    } else ()
    if (!this.values1.valid) {
      this.values1.reset()
      this.values1.valid = true
      this.values2.valid = false
      return this.values1
    } else ()
    this.values2.reset()
    this.values2.valid = true
    this.values1.valid = false
    return this.values2
  }
  def keys(): com.badlogic.gdx.utils.ObjectFloatMap.Keys[K] = {
    if (com.badlogic.gdx.utils.Collections.allocateIterators) {
      return new com.badlogic.gdx.utils.ObjectFloatMap.Keys(this)
    } else ()
    if (this.keys1 == null) {
      this.keys1 = new com.badlogic.gdx.utils.ObjectFloatMap.Keys(this)
      this.keys2 = new com.badlogic.gdx.utils.ObjectFloatMap.Keys(this)
    } else ()
    if (!this.keys1.valid) {
      this.keys1.reset()
      this.keys1.valid = true
      this.keys2.valid = false
      return this.keys1
    } else ()
    this.keys2.reset()
    this.keys2.valid = true
    this.keys1.valid = false
    return this.keys2
  }
}
object ObjectFloatMap {
  class Entry[K] {
    var key: K = null.asInstanceOf[K]
    var value: scala.Float = 0.0f
    def toString(): java.lang.String = {
      return (java.lang.String.valueOf(this.key) + "=") + this.value
    }
  }
  private class MapIterator[K] {
    var hasNext: scala.Boolean = false
    var map: ObjectFloatMap[K] = null.asInstanceOf[ObjectFloatMap[K]]
    var nextIndex: scala.Int = 0
    var currentIndex: scala.Int = 0
    var valid: scala.Boolean = true
    def this(map: ObjectFloatMap[K]) = {
      this()
      this.map = map
      this.reset()
    }
    def reset(): scala.Unit = {
      this.currentIndex = -1
      this.nextIndex = -1
      this.findNextIndex()
    }
    def findNextIndex(): scala.Unit = {
      val keyTable: scala.Array[K] = this.map.keyTable;
      { val n: scala.Int = keyTable.length; while ({ this.nextIndex += 1; this.nextIndex } < n) { {
        if (keyTable(this.nextIndex) != null) {
          this.hasNext = true
          return
        } else ()
      };  } }
      this.hasNext = false
    }
    def remove(): scala.Unit = {
      var i: scala.Int = this.currentIndex
      if (i < 0) {
        throw new java.lang.IllegalStateException("next must be called before remove.")
      } else ()
      val keyTable: scala.Array[K] = this.map.keyTable
      val valueTable: scala.Array[scala.Float] = this.map.valueTable
      val mask: scala.Int = this.map.mask
      var next: scala.Int = (i + 1) & mask
      var key: K = null.asInstanceOf[K]
      while ({
        key = keyTable(next)
        key
      } != null) {
        val placement: scala.Int = this.map.place(key)
        if (((next - placement) & mask) > ((i - placement) & mask)) {
          keyTable(i) = key
          valueTable(i) = valueTable(next)
          i = next
        } else ()
        next = (next + 1) & mask
      }
      keyTable(i) = null.asInstanceOf[K]
      this.map.size = this.map.size - 1
      if (i != this.currentIndex) {
        this.nextIndex = this.nextIndex - 1
      } else ()
      this.currentIndex = -1
    }
  }
  class Entries[K] extends com.badlogic.gdx.utils.ObjectFloatMap.MapIterator[K] with scala.collection.Iterable[com.badlogic.gdx.utils.ObjectFloatMap.Entry[K]] with scala.collection.Iterator[com.badlogic.gdx.utils.ObjectFloatMap.Entry[K]] {
    var entry: com.badlogic.gdx.utils.ObjectFloatMap.Entry[K] = new com.badlogic.gdx.utils.ObjectFloatMap.Entry[K]()
    def this(map: ObjectFloatMap[K]) = {
      this()
    }
    def next(): com.badlogic.gdx.utils.ObjectFloatMap.Entry[K] = {
      if (!hasNext) {
        throw new java.util.NoSuchElementException()
      } else ()
      if (!valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      val keyTable: scala.Array[K] = this.map.keyTable
      this.entry.key = keyTable(nextIndex)
      this.entry.value = this.map.valueTable(nextIndex)
      currentIndex = nextIndex
      this.findNextIndex()
      return this.entry
    }
    def hasNext(): scala.Boolean = {
      if (!valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      return hasNext
    }
    def iterator(): com.badlogic.gdx.utils.ObjectFloatMap.Entries[K] = {
      return this
    }
  }
  class Values extends com.badlogic.gdx.utils.ObjectFloatMap.MapIterator[java.lang.Object] {
    def this(map: ObjectFloatMap[?]) = {
      this()
    }
    def hasNext(): scala.Boolean = {
      if (!valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      return hasNext
    }
    def next(): scala.Float = {
      if (!hasNext) {
        throw new java.util.NoSuchElementException()
      } else ()
      if (!valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      val value: scala.Float = this.map.valueTable(nextIndex)
      currentIndex = nextIndex
      this.findNextIndex()
      return value
    }
    def iterator(): com.badlogic.gdx.utils.ObjectFloatMap.Values = {
      return this
    }
    def toArray(): com.badlogic.gdx.utils.FloatArray = {
      val array: com.badlogic.gdx.utils.FloatArray = new com.badlogic.gdx.utils.FloatArray(true, this.map.size)
      while (hasNext) {
        array.add(this.next())
      }
      return array
    }
    def toArray(array: com.badlogic.gdx.utils.FloatArray): com.badlogic.gdx.utils.FloatArray = {
      while (hasNext) {
        array.add(this.next())
      }
      return array
    }
  }
  class Keys[K] extends com.badlogic.gdx.utils.ObjectFloatMap.MapIterator[K] with scala.collection.Iterable[K] with scala.collection.Iterator[K] {
    def this(map: ObjectFloatMap[K]) = {
      this()
    }
    def hasNext(): scala.Boolean = {
      if (!valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      return hasNext
    }
    def next(): K = {
      if (!hasNext) {
        throw new java.util.NoSuchElementException()
      } else ()
      if (!valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      val key: K = this.map.keyTable(nextIndex)
      currentIndex = nextIndex
      this.findNextIndex()
      return key
    }
    def iterator(): com.badlogic.gdx.utils.ObjectFloatMap.Keys[K] = {
      return this
    }
    def toArray(): com.badlogic.gdx.utils.Array[K] = {
      return this.toArray(new com.badlogic.gdx.utils.Array[K](true, this.map.size))
    }
    def toArray(array: com.badlogic.gdx.utils.Array[K]): com.badlogic.gdx.utils.Array[K] = {
      while (hasNext) {
        array.add(this.next())
      }
      return array
    }
  }
}