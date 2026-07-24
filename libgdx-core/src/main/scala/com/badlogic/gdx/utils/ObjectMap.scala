package com.badlogic.gdx.utils

class ObjectMap[K, V] extends scala.collection.Iterable[com.badlogic.gdx.utils.ObjectMap.Entry[K, V]] {
  var size: scala.Int = 0
  var keyTable: scala.Array[K] = null.asInstanceOf[scala.Array[K]]
  var valueTable: scala.Array[V] = null.asInstanceOf[scala.Array[V]]
  var loadFactor: scala.Float = 0.0f
  var threshold: scala.Int = 0
  var shift: scala.Int = 0
  var mask: scala.Int = 0
  var entries1: com.badlogic.gdx.utils.ObjectMap.Entries[?, ?] = null.asInstanceOf[com.badlogic.gdx.utils.ObjectMap.Entries[?, ?]]
  var entries2: com.badlogic.gdx.utils.ObjectMap.Entries[?, ?] = null.asInstanceOf[com.badlogic.gdx.utils.ObjectMap.Entries[?, ?]]
  var values1: com.badlogic.gdx.utils.ObjectMap.Values[?] = null.asInstanceOf[com.badlogic.gdx.utils.ObjectMap.Values[?]]
  var values2: com.badlogic.gdx.utils.ObjectMap.Values[?] = null.asInstanceOf[com.badlogic.gdx.utils.ObjectMap.Values[?]]
  var keys1: com.badlogic.gdx.utils.ObjectMap.Keys[?] = null.asInstanceOf[com.badlogic.gdx.utils.ObjectMap.Keys[?]]
  var keys2: com.badlogic.gdx.utils.ObjectMap.Keys[?] = null.asInstanceOf[com.badlogic.gdx.utils.ObjectMap.Keys[?]]
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
    this.valueTable = new scala.Array[java.lang.Object](tableSize).asInstanceOf[scala.Array[V]].asInstanceOf[scala.Array[V]]
  }
  def this(initialCapacity: scala.Int) = {
    this(initialCapacity, 0.8f)
  }
  def this(map: ObjectMap[? <: K, ? <: V]) = {
    this((map.keyTable.length * map.loadFactor).asInstanceOf[scala.Int].asInstanceOf[scala.Int], map.loadFactor)
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
        if (other.equals(key.asInstanceOf[java.lang.Object])) {
          return i
        } else ()
      }; i = (i + 1) & this.mask } }
    }
    throw new java.lang.RuntimeException("unreachable")
  }
  def put(key: K, value: V): V = {
    var i: scala.Int = this.locateKey(key)
    if (i >= 0) {
      val oldValue: V = this.valueTable(i)
      this.valueTable(i) = value
      return oldValue
    } else ()
    i = -(i + 1)
    this.keyTable(i) = key
    this.valueTable(i) = value
    if ({ this.size += 1; this.size } >= this.threshold) {
      this.resize(this.keyTable.length << 1)
    } else ()
    return null.asInstanceOf[V]
  }
  def putAll(map: ObjectMap[? <: K, ? <: V]): scala.Unit = {
    this.ensureCapacity(map.size)
    val keyTable: scala.Array[K] = map.keyTable.asInstanceOf[scala.Array[K]]
    val valueTable: scala.Array[V] = map.valueTable.asInstanceOf[scala.Array[V]]
    var key: K = null.asInstanceOf[K];
    { var i: scala.Int = 0; val n: scala.Int = keyTable.length; while (i < n) { {
      key = keyTable(i)
      if (key != null) {
        this.put(key, valueTable(i))
      } else ()
    }; i = i + 1 } }
  }
  private def putResize(key: K, value: V): scala.Unit = {
    val keyTable: scala.Array[K] = this.keyTable;
    { var i: scala.Int = this.place(key); while (true) { {
      if (keyTable(i) == null) {
        keyTable(i) = key
        this.valueTable(i) = value
        return
      } else ()
    }; i = (i + 1) & this.mask } }
  }
  def get[T <: K](key: T): V = {
    val i: scala.Int = this.locateKey(key)
    return if (i < 0) null.asInstanceOf[V] else this.valueTable(i)
  }
  def get(key: K, defaultValue: V): V = {
    val i: scala.Int = this.locateKey(key)
    return if (i < 0) defaultValue else this.valueTable(i)
  }
  def remove(key$arg: K): V = {
    var key: K = key$arg
    var i: scala.Int = this.locateKey(key)
    if (i < 0) {
      return null.asInstanceOf[V]
    } else ()
    val keyTable: scala.Array[K] = this.keyTable
    val valueTable: scala.Array[V] = this.valueTable
    val oldValue: V = valueTable(i)
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
    valueTable(i) = null.asInstanceOf[V]
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
    java.util.Arrays.fill(this.valueTable.asInstanceOf[scala.Array[java.lang.Object]], null)
  }
  def containsValue(value: java.lang.Object, identity: scala.Boolean): scala.Boolean = {
    val valueTable: scala.Array[V] = this.valueTable
    if (value == null) {
      val keyTable: scala.Array[K] = this.keyTable;
      { var i: scala.Int = valueTable.length - 1; while (i >= 0) { {
        if ((keyTable(i) != null) && (valueTable(i) == null)) {
          return true
        } else ()
      }; i = i - 1 } }
    } else {
      if (identity) {
        { var i: scala.Int = valueTable.length - 1; while (i >= 0) { {
          if (valueTable(i) == value) {
            return true
          } else ()
        }; i = i - 1 } }
      } else {
        { var i: scala.Int = valueTable.length - 1; while (i >= 0) { {
          if (value.equals(valueTable(i).asInstanceOf[java.lang.Object])) {
            return true
          } else ()
        }; i = i - 1 } }
      }
    }
    return false
  }
  def containsKey(key: K): scala.Boolean = {
    return this.locateKey(key) >= 0
  }
  def findKey(value: java.lang.Object, identity: scala.Boolean): K = {
    val valueTable: scala.Array[V] = this.valueTable
    if (value == null) {
      val keyTable: scala.Array[K] = this.keyTable;
      { var i: scala.Int = valueTable.length - 1; while (i >= 0) { {
        if ((keyTable(i) != null) && (valueTable(i) == null)) {
          return keyTable(i)
        } else ()
      }; i = i - 1 } }
    } else {
      if (identity) {
        { var i: scala.Int = valueTable.length - 1; while (i >= 0) { {
          if (valueTable(i) == value) {
            return this.keyTable(i)
          } else ()
        }; i = i - 1 } }
      } else {
        { var i: scala.Int = valueTable.length - 1; while (i >= 0) { {
          if (value.equals(valueTable(i).asInstanceOf[java.lang.Object])) {
            return this.keyTable(i)
          } else ()
        }; i = i - 1 } }
      }
    }
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
    val oldValueTable: scala.Array[V] = this.valueTable
    this.keyTable = new scala.Array[java.lang.Object](newSize).asInstanceOf[scala.Array[K]].asInstanceOf[scala.Array[K]]
    this.valueTable = new scala.Array[java.lang.Object](newSize).asInstanceOf[scala.Array[V]].asInstanceOf[scala.Array[V]]
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
    val valueTable: scala.Array[V] = this.valueTable;
    { var i: scala.Int = 0; val n: scala.Int = keyTable.length; while (i < n) { {
      val key: K = keyTable(i)
      if (key != null) {
        h = h + key.hashCode()
        val value: V = valueTable(i)
        if (value != null) {
          h = h + value.hashCode()
        } else ()
      } else ()
    }; i = i + 1 } }
    return h
  }
  def equals(obj: java.lang.Object): scala.Boolean = {
    if (obj == this) {
      return true
    } else ()
    if (!obj.isInstanceOf[ObjectMap[?, ?]]) {
      return false
    } else ()
    val other: ObjectMap[?, ?] = obj.asInstanceOf[ObjectMap[?, ?]].asInstanceOf[ObjectMap[?, ?]]
    if (other.size != this.size) {
      return false
    } else ()
    val keyTable: scala.Array[K] = this.keyTable
    val valueTable: scala.Array[V] = this.valueTable;
    { var i: scala.Int = 0; val n: scala.Int = keyTable.length; while (i < n) { {
      val key: K = keyTable(i)
      if (key != null) {
        val value: V = valueTable(i)
        if (value == null) {
          if (other.get(key, ObjectMap.dummy) != null) {
            return false
          } else ()
        } else {
          if (!value.equals(other.get(key))) {
            return false
          } else ()
        }
      } else ()
    }; i = i + 1 } }
    return true
  }
  def equalsIdentity(obj: java.lang.Object): scala.Boolean = {
    if (obj == this) {
      return true
    } else ()
    if (!obj.isInstanceOf[ObjectMap[?, ?]]) {
      return false
    } else ()
    val other: ObjectMap[?, ?] = obj.asInstanceOf[ObjectMap[?, ?]].asInstanceOf[ObjectMap[?, ?]]
    if (other.size != this.size) {
      return false
    } else ()
    val keyTable: scala.Array[K] = this.keyTable
    val valueTable: scala.Array[V] = this.valueTable;
    { var i: scala.Int = 0; val n: scala.Int = keyTable.length; while (i < n) { {
      val key: K = keyTable(i)
      if ((key != null) && (valueTable(i) != other.get(key, ObjectMap.dummy))) {
        return false
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
  def toString(separator: java.lang.String, braces: scala.Boolean): java.lang.String = {
    if (this.size == 0) {
      return if (braces) "{}" else ""
    } else ()
    val buffer: java.lang.StringBuilder = new java.lang.StringBuilder(32)
    if (braces) {
      buffer.append('{')
    } else ()
    val keyTable: scala.Array[K] = this.keyTable
    val valueTable: scala.Array[V] = this.valueTable
    var i: scala.Int = keyTable.length
    while ({ i -= 1; i } > 0) {
      val key: K = keyTable(i)
      if (key == null) {
        /* continue */ ()
      } else ()
      buffer.append(if (key == this) "(this)" else key)
      buffer.append('=')
      val value: V = valueTable(i)
      buffer.append(if (value == this) "(this)" else value)
      /* break */ ()
    }
    while ({ i -= 1; i } > 0) {
      val key: K = keyTable(i)
      if (key == null) {
        /* continue */ ()
      } else ()
      buffer.append(separator)
      buffer.append(if (key == this) "(this)" else key)
      buffer.append('=')
      val value: V = valueTable(i)
      buffer.append(if (value == this) "(this)" else value)
    }
    if (braces) {
      buffer.append('}')
    } else ()
    return buffer.toString()
  }
  def iterator(): com.badlogic.gdx.utils.ObjectMap.Entries[K, V] = {
    return this.entries()
  }
  def entries(): com.badlogic.gdx.utils.ObjectMap.Entries[K, V] = {
    if (com.badlogic.gdx.utils.Collections.allocateIterators) {
      return new com.badlogic.gdx.utils.ObjectMap.Entries(this)
    } else ()
    if (this.entries1 == null) {
      this.entries1 = new com.badlogic.gdx.utils.ObjectMap.Entries(this)
      this.entries2 = new com.badlogic.gdx.utils.ObjectMap.Entries(this)
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
  def values(): com.badlogic.gdx.utils.ObjectMap.Values[V] = {
    if (com.badlogic.gdx.utils.Collections.allocateIterators) {
      return new com.badlogic.gdx.utils.ObjectMap.Values(this)
    } else ()
    if (this.values1 == null) {
      this.values1 = new com.badlogic.gdx.utils.ObjectMap.Values(this)
      this.values2 = new com.badlogic.gdx.utils.ObjectMap.Values(this)
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
  def keys(): com.badlogic.gdx.utils.ObjectMap.Keys[K] = {
    if (com.badlogic.gdx.utils.Collections.allocateIterators) {
      return new com.badlogic.gdx.utils.ObjectMap.Keys(this)
    } else ()
    if (this.keys1 == null) {
      this.keys1 = new com.badlogic.gdx.utils.ObjectMap.Keys(this)
      this.keys2 = new com.badlogic.gdx.utils.ObjectMap.Keys(this)
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
object ObjectMap {
  final val dummy: java.lang.Object = new java.lang.Object()
  class Entry[K, V] {
    var key: K = null.asInstanceOf[K]
    var value: V = null.asInstanceOf[V]
    def toString(): java.lang.String = {
      return (java.lang.String.valueOf(this.key) + "=") + this.value
    }
  }
  private abstract class MapIterator[K, V, I] extends scala.collection.Iterable[I] with scala.collection.Iterator[I] {
    var hasNext$field: scala.Boolean = false
    var map: ObjectMap[K, V] = null.asInstanceOf[ObjectMap[K, V]]
    var nextIndex: scala.Int = 0
    var currentIndex: scala.Int = 0
    var valid: scala.Boolean = true
    def this(map: ObjectMap[K, V]) = {
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
          this.hasNext$field = true
          return
        } else ()
      };  } }
      this.hasNext$field = false
    }
    def remove(): scala.Unit = {
      var i: scala.Int = this.currentIndex
      if (i < 0) {
        throw new java.lang.IllegalStateException("next must be called before remove.")
      } else ()
      val keyTable: scala.Array[K] = this.map.keyTable
      val valueTable: scala.Array[V] = this.map.valueTable
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
      valueTable(i) = null.asInstanceOf[V]
      this.map.size = this.map.size - 1
      if (i != this.currentIndex) {
        this.nextIndex = this.nextIndex - 1
      } else ()
      this.currentIndex = -1
    }
  }
  class Entries[K, V] extends com.badlogic.gdx.utils.ObjectMap.MapIterator[K, V, com.badlogic.gdx.utils.ObjectMap.Entry[K, V]] {
    var entry: com.badlogic.gdx.utils.ObjectMap.Entry[K, V] = new com.badlogic.gdx.utils.ObjectMap.Entry[K, V]()
    def this(map: ObjectMap[K, V]) = {
      this()
    }
    def next(): com.badlogic.gdx.utils.ObjectMap.Entry[K, V] = {
      if (!hasNext$field) {
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
      return hasNext$field
    }
    def iterator(): com.badlogic.gdx.utils.ObjectMap.Entries[K, V] = {
      return this
    }
  }
  class Values[V] extends com.badlogic.gdx.utils.ObjectMap.MapIterator[java.lang.Object, V, V] {
    def this(map: ObjectMap[?, V]) = {
      this()
    }
    def hasNext(): scala.Boolean = {
      if (!valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      return hasNext$field
    }
    def next(): V = {
      if (!hasNext$field) {
        throw new java.util.NoSuchElementException()
      } else ()
      if (!valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      val value: V = this.map.valueTable(nextIndex)
      currentIndex = nextIndex
      this.findNextIndex()
      return value
    }
    def iterator(): com.badlogic.gdx.utils.ObjectMap.Values[V] = {
      return this
    }
    def toArray(): com.badlogic.gdx.utils.Array[V] = {
      return this.toArray(new com.badlogic.gdx.utils.Array(true, this.map.size))
    }
    def toArray(array: com.badlogic.gdx.utils.Array[V]): com.badlogic.gdx.utils.Array[V] = {
      while (hasNext$field) {
        array.add(this.next())
      }
      return array
    }
  }
  class Keys[K] extends com.badlogic.gdx.utils.ObjectMap.MapIterator[K, java.lang.Object, K] {
    def this(map: ObjectMap[K, ?]) = {
      this()
    }
    def hasNext(): scala.Boolean = {
      if (!valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      return hasNext$field
    }
    def next(): K = {
      if (!hasNext$field) {
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
    def iterator(): com.badlogic.gdx.utils.ObjectMap.Keys[K] = {
      return this
    }
    def toArray(): com.badlogic.gdx.utils.Array[K] = {
      return this.toArray(new com.badlogic.gdx.utils.Array[K](true, this.map.size))
    }
    def toArray(array: com.badlogic.gdx.utils.Array[K]): com.badlogic.gdx.utils.Array[K] = {
      while (hasNext$field) {
        array.add(this.next())
      }
      return array
    }
  }
}