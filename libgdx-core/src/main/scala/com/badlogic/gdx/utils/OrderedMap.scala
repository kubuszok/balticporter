package com.badlogic.gdx.utils

class OrderedMap[K, V] extends com.badlogic.gdx.utils.ObjectMap[K, V] {
  var keys$field: com.badlogic.gdx.utils.Array[K] = null.asInstanceOf[com.badlogic.gdx.utils.Array[K]]
  def this(initialCapacity: scala.Int, loadFactor: scala.Float) = {
    this()
    this.keys$field = new com.badlogic.gdx.utils.Array(initialCapacity)
  }
  def this(initialCapacity: scala.Int) = {
    this()
    this.keys$field = new com.badlogic.gdx.utils.Array(initialCapacity)
  }
  def this(map: OrderedMap[? <: K, ? <: V]) = {
    this()
    this.keys$field = new com.badlogic.gdx.utils.Array(map.keys$field)
  }
  def this() = {
    this()
    this.keys$field = new com.badlogic.gdx.utils.Array()
  }
  def put(key: K, value: V): V = {
    var i: scala.Int = this.locateKey(key)
    if (i >= 0) {
      val oldValue: V = valueTable(i)
      valueTable(i) = value
      return oldValue
    } else ()
    i = -(i + 1)
    keyTable(i) = key
    valueTable(i) = value
    this.keys$field.add(key)
    if ({ size += 1; size } >= threshold) {
      this.resize(this.keyTable.length << 1)
    } else ()
    return null
  }
  def putAll[T <: K](map: OrderedMap[T, ? <: V]): scala.Unit = {
    this.ensureCapacity(map.size)
    val keys: scala.Array[K] = map.keys$field.items
    { var i: scala.Int = 0; val n: scala.Int = map.keys$field.size; while (i < n) { {
      val key: K = keys(i)
      this.put(key, map.get(key.asInstanceOf[T]))
    }; i = i + 1 } }
  }
  def remove(key: K): V = {
    this.keys$field.removeValue(key, false)
    return super.remove(key)
  }
  def removeIndex(index: scala.Int): V = {
    return super.remove(this.keys$field.removeIndex(index))
  }
  def alter(before: K, after: K): scala.Boolean = {
    if (this.containsKey(after)) {
      return false
    } else ()
    val index: scala.Int = this.keys$field.indexOf(before, false)
    if (index == (-1)) {
      return false
    } else ()
    super.put(after, super.remove(before))
    this.keys$field.set(index, after)
    return true
  }
  def alterIndex(index: scala.Int, after: K): scala.Boolean = {
    if (((index < 0) || (index >= size)) || this.containsKey(after)) {
      return false
    } else ()
    super.put(after, super.remove(this.keys$field.get(index)))
    this.keys$field.set(index, after)
    return true
  }
  def clear(maximumCapacity: scala.Int): scala.Unit = {
    this.keys$field.clear()
    super.clear(maximumCapacity)
  }
  def clear(): scala.Unit = {
    this.keys$field.clear()
    super.clear()
  }
  def orderedKeys(): com.badlogic.gdx.utils.Array[K] = {
    return this.keys$field
  }
  def iterator(): com.badlogic.gdx.utils.ObjectMap#Entries[K, V] = {
    return this.entries()
  }
  def entries(): com.badlogic.gdx.utils.ObjectMap#Entries[K, V] = {
    if (com.badlogic.gdx.utils.Collections.allocateIterators) {
      return new OrderedMapEntries(this)
    } else ()
    if (entries1 == null) {
      entries1 = new OrderedMapEntries(this)
      entries2 = new OrderedMapEntries(this)
    } else ()
    if (!this.entries1.valid) {
      entries1.reset()
      this.entries1.valid = true
      this.entries2.valid = false
      return entries1
    } else ()
    entries2.reset()
    this.entries2.valid = true
    this.entries1.valid = false
    return entries2
  }
  def values(): com.badlogic.gdx.utils.ObjectMap#Values[V] = {
    if (com.badlogic.gdx.utils.Collections.allocateIterators) {
      return new OrderedMapValues(this)
    } else ()
    if (values1 == null) {
      values1 = new OrderedMapValues(this)
      values2 = new OrderedMapValues(this)
    } else ()
    if (!this.values1.valid) {
      values1.reset()
      this.values1.valid = true
      this.values2.valid = false
      return values1
    } else ()
    values2.reset()
    this.values2.valid = true
    this.values1.valid = false
    return values2
  }
  def keys(): com.badlogic.gdx.utils.ObjectMap#Keys[K] = {
    if (com.badlogic.gdx.utils.Collections.allocateIterators) {
      return new OrderedMapKeys(this)
    } else ()
    if (keys1 == null) {
      keys1 = new OrderedMapKeys(this)
      keys2 = new OrderedMapKeys(this)
    } else ()
    if (!this.keys1.valid) {
      keys1.reset()
      this.keys1.valid = true
      this.keys2.valid = false
      return keys1
    } else ()
    keys2.reset()
    this.keys2.valid = true
    this.keys1.valid = false
    return keys2
  }
  protected def toString(separator: java.lang.String, braces: scala.Boolean): java.lang.String = {
    if (size == 0) {
      return if (braces) "{}" else ""
    } else ()
    val buffer: java.lang.StringBuilder = new java.lang.StringBuilder(32)
    if (braces) {
      buffer.append('{')
    } else ()
    val keys: com.badlogic.gdx.utils.Array[K] = this.keys$field
    { var i: scala.Int = 0; val n: scala.Int = keys.size; while (i < n) { {
      val key: K = keys.get(i)
      if (i > 0) {
        buffer.append(separator)
      } else ()
      buffer.append(if (key == this) "(this)" else key)
      buffer.append('=')
      val value: V = this.get(key)
      buffer.append(if (value == this) "(this)" else value)
    }; i = i + 1 } }
    if (braces) {
      buffer.append('}')
    } else ()
    return buffer.toString()
  }
  class OrderedMapEntries[K, V] extends com.badlogic.gdx.utils.ObjectMap#Entries[K, V] {
    private var keys: com.badlogic.gdx.utils.Array[K] = null.asInstanceOf[com.badlogic.gdx.utils.Array[K]]
    def this(map: OrderedMap[K, V]) = {
      this()
      this.keys = map.keys$field
    }
    def reset(): scala.Unit = {
      currentIndex = -1
      nextIndex = 0
      hasNext = this.map.size > 0
    }
    def next(): com.badlogic.gdx.utils.ObjectMap#Entry = {
      if (!hasNext) {
        throw new java.util.NoSuchElementException()
      } else ()
      if (!valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      currentIndex = nextIndex
      this.entry.key = this.keys.get(nextIndex)
      this.entry.value = map.get(this.entry.key)
      nextIndex = nextIndex + 1
      hasNext = nextIndex < this.map.size
      return entry
    }
    def remove(): scala.Unit = {
      if (currentIndex < 0) {
        throw new java.lang.IllegalStateException("next must be called before remove.")
      } else ()
      map.remove(this.entry.key)
      nextIndex = nextIndex - 1
      currentIndex = -1
    }
  }
  class OrderedMapKeys[K] extends com.badlogic.gdx.utils.ObjectMap#Keys[K] {
    private var keys: com.badlogic.gdx.utils.Array[K] = null.asInstanceOf[com.badlogic.gdx.utils.Array[K]]
    def this(map: OrderedMap[K, ?]) = {
      this()
      this.keys = map.keys$field
    }
    def reset(): scala.Unit = {
      currentIndex = -1
      nextIndex = 0
      hasNext = this.map.size > 0
    }
    def next(): K = {
      if (!hasNext) {
        throw new java.util.NoSuchElementException()
      } else ()
      if (!valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      val key: K = this.keys.get(nextIndex)
      currentIndex = nextIndex
      nextIndex = nextIndex + 1
      hasNext = nextIndex < this.map.size
      return key
    }
    def remove(): scala.Unit = {
      if (currentIndex < 0) {
        throw new java.lang.IllegalStateException("next must be called before remove.")
      } else ()
      map.asInstanceOf[OrderedMap].removeIndex(currentIndex)
      nextIndex = currentIndex
      currentIndex = -1
    }
    def toArray(array: com.badlogic.gdx.utils.Array[K]): com.badlogic.gdx.utils.Array[K] = {
      array.addAll(this.keys, nextIndex, this.keys.size - nextIndex)
      nextIndex = this.keys.size
      hasNext = false
      return array
    }
    def toArray(): com.badlogic.gdx.utils.Array[K] = {
      return this.toArray(new com.badlogic.gdx.utils.Array(true, this.keys.size - nextIndex))
    }
  }
  class OrderedMapValues[V] extends com.badlogic.gdx.utils.ObjectMap#Values[V] {
    private var keys: com.badlogic.gdx.utils.Array = null.asInstanceOf[com.badlogic.gdx.utils.Array]
    def this(map: OrderedMap[?, V]) = {
      this()
      this.keys = map.keys$field
    }
    def reset(): scala.Unit = {
      currentIndex = -1
      nextIndex = 0
      hasNext = this.map.size > 0
    }
    def next(): V = {
      if (!hasNext) {
        throw new java.util.NoSuchElementException()
      } else ()
      if (!valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      val value: V = map.get(this.keys.get(nextIndex))
      currentIndex = nextIndex
      nextIndex = nextIndex + 1
      hasNext = nextIndex < this.map.size
      return value
    }
    def remove(): scala.Unit = {
      if (currentIndex < 0) {
        throw new java.lang.IllegalStateException("next must be called before remove.")
      } else ()
      map.asInstanceOf[OrderedMap].removeIndex(currentIndex)
      nextIndex = currentIndex
      currentIndex = -1
    }
    def toArray(array: com.badlogic.gdx.utils.Array[V]): com.badlogic.gdx.utils.Array[V] = {
      val n: scala.Int = this.keys.size
      array.ensureCapacity(n - nextIndex)
      val keys: scala.Array[java.lang.Object] = this.keys.items
      { var i: scala.Int = nextIndex; while (i < n) { {
        array.add(map.get(keys(i)))
      }; i = i + 1 } }
      currentIndex = n - 1
      nextIndex = n
      hasNext = false
      return array
    }
    def toArray(): com.badlogic.gdx.utils.Array[V] = {
      return this.toArray(new com.badlogic.gdx.utils.Array(true, this.keys.size - nextIndex))
    }
  }
}