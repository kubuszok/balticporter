package com.badlogic.gdx.utils

class OrderedMap[K <: java.lang.Object, V <: java.lang.Object] extends com.badlogic.gdx.utils.ObjectMap[K, V] {
  var keys$field: com.badlogic.gdx.utils.Array[K] = null.asInstanceOf[com.badlogic.gdx.utils.Array[K]]
  def this(initialCapacity: scala.Int) = {
    this()
    if ((0.8f <= 0.0f) || (0.8f >= 1.0f)) {
      throw new java.lang.IllegalArgumentException("loadFactor must be > 0 and < 1: " + 0.8f)
    } else ()
    this.loadFactor = 0.8f
    val tableSize: scala.Int = com.badlogic.gdx.utils.ObjectSet.tableSize(initialCapacity, 0.8f)
    this.threshold = (tableSize * 0.8f).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    this.mask = tableSize - 1
    this.shift = java.lang.Long.numberOfLeadingZeros(this.mask)
    this.keyTable = new scala.Array[java.lang.Object](tableSize).asInstanceOf[scala.Array[K]].asInstanceOf[scala.Array[K]]
    this.valueTable = new scala.Array[java.lang.Object](tableSize).asInstanceOf[scala.Array[V]].asInstanceOf[scala.Array[V]]
    this.keys$field = new com.badlogic.gdx.utils.Array(initialCapacity).asInstanceOf[com.badlogic.gdx.utils.Array[K]]
  }
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
    this.keys$field = new com.badlogic.gdx.utils.Array(initialCapacity).asInstanceOf[com.badlogic.gdx.utils.Array[K]]
  }
  def this(map: OrderedMap[? <: K, ? <: V]) = {
    this()
    if ((map.loadFactor <= 0.0f) || (map.loadFactor >= 1.0f)) {
      throw new java.lang.IllegalArgumentException("loadFactor must be > 0 and < 1: " + map.loadFactor)
    } else ()
    this.loadFactor = map.loadFactor
    val tableSize: scala.Int = com.badlogic.gdx.utils.ObjectSet.tableSize((map.keyTable.length * map.loadFactor).asInstanceOf[scala.Int].asInstanceOf[scala.Int], map.loadFactor)
    this.threshold = (tableSize * map.loadFactor).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    this.mask = tableSize - 1
    this.shift = java.lang.Long.numberOfLeadingZeros(this.mask)
    this.keyTable = new scala.Array[java.lang.Object](tableSize).asInstanceOf[scala.Array[K]].asInstanceOf[scala.Array[K]]
    this.valueTable = new scala.Array[java.lang.Object](tableSize).asInstanceOf[scala.Array[V]].asInstanceOf[scala.Array[V]]
    java.lang.System.arraycopy(map.keyTable, 0, this.keyTable, 0, map.keyTable.length)
    java.lang.System.arraycopy(map.valueTable, 0, this.valueTable, 0, map.valueTable.length)
    this.size = map.size
    this.keys$field = new com.badlogic.gdx.utils.Array(map.keys$field).asInstanceOf[com.badlogic.gdx.utils.Array[K]]
  }
  this.keys$field = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[K]]
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
    return null.asInstanceOf[V]
  }
  def putAll[T <: K](map: OrderedMap[T, ? <: V]): scala.Unit = {
    this.ensureCapacity(map.size)
    val keys: scala.Array[K] = map.keys$field.items.asInstanceOf[scala.Array[K]];
    { var i: scala.Int = 0; val n: scala.Int = map.keys$field.size; while (i < n) { {
      val key: K = keys(i)
      this.put(key, map.get(key.asInstanceOf[T]))
    }; i = i + 1 } }
  }
  def remove(key: K): V = {
    this.keys$field.removeValue(key, false)
    return super.remove(key).asInstanceOf[V]
  }
  def removeIndex(index: scala.Int): V = {
    return super.remove(this.keys$field.removeIndex(index)).asInstanceOf[V]
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
  def iterator(): com.badlogic.gdx.utils.ObjectMap.Entries[K, V] = {
    return this.entries()
  }
  def entries(): com.badlogic.gdx.utils.ObjectMap.Entries[K, V] = {
    if (com.badlogic.gdx.utils.Collections.allocateIterators) {
      return new com.badlogic.gdx.utils.OrderedMap.OrderedMapEntries[K, V](this.asInstanceOf[OrderedMap[K, V]]).asInstanceOf[com.badlogic.gdx.utils.ObjectMap.Entries[K, V]]
    } else ()
    if (entries1 == null) {
      entries1 = new com.badlogic.gdx.utils.OrderedMap.OrderedMapEntries[K, V](this.asInstanceOf[OrderedMap[K, V]]).asInstanceOf[com.badlogic.gdx.utils.ObjectMap.Entries[K, V]]
      entries2 = new com.badlogic.gdx.utils.OrderedMap.OrderedMapEntries[K, V](this.asInstanceOf[OrderedMap[K, V]]).asInstanceOf[com.badlogic.gdx.utils.ObjectMap.Entries[K, V]]
    } else ()
    if (!this.entries1.valid) {
      entries1.reset()
      this.entries1.valid = true
      this.entries2.valid = false
      return entries1.asInstanceOf[com.badlogic.gdx.utils.ObjectMap.Entries[K, V]]
    } else ()
    entries2.reset()
    this.entries2.valid = true
    this.entries1.valid = false
    return entries2.asInstanceOf[com.badlogic.gdx.utils.ObjectMap.Entries[K, V]]
  }
  def values(): com.badlogic.gdx.utils.ObjectMap.Values[V] = {
    if (com.badlogic.gdx.utils.Collections.allocateIterators) {
      return new com.badlogic.gdx.utils.OrderedMap.OrderedMapValues[V](this).asInstanceOf[com.badlogic.gdx.utils.ObjectMap.Values[V]]
    } else ()
    if (values1 == null) {
      values1 = new com.badlogic.gdx.utils.OrderedMap.OrderedMapValues[V](this).asInstanceOf[com.badlogic.gdx.utils.ObjectMap.Values[V]]
      values2 = new com.badlogic.gdx.utils.OrderedMap.OrderedMapValues[V](this).asInstanceOf[com.badlogic.gdx.utils.ObjectMap.Values[V]]
    } else ()
    if (!this.values1.valid) {
      values1.reset()
      this.values1.valid = true
      this.values2.valid = false
      return values1.asInstanceOf[com.badlogic.gdx.utils.ObjectMap.Values[V]]
    } else ()
    values2.reset()
    this.values2.valid = true
    this.values1.valid = false
    return values2.asInstanceOf[com.badlogic.gdx.utils.ObjectMap.Values[V]]
  }
  def keys(): com.badlogic.gdx.utils.ObjectMap.Keys[K] = {
    if (com.badlogic.gdx.utils.Collections.allocateIterators) {
      return new com.badlogic.gdx.utils.OrderedMap.OrderedMapKeys[K](this).asInstanceOf[com.badlogic.gdx.utils.ObjectMap.Keys[K]]
    } else ()
    if (keys1 == null) {
      keys1 = new com.badlogic.gdx.utils.OrderedMap.OrderedMapKeys[K](this).asInstanceOf[com.badlogic.gdx.utils.ObjectMap.Keys[K]]
      keys2 = new com.badlogic.gdx.utils.OrderedMap.OrderedMapKeys[K](this).asInstanceOf[com.badlogic.gdx.utils.ObjectMap.Keys[K]]
    } else ()
    if (!this.keys1.valid) {
      keys1.reset()
      this.keys1.valid = true
      this.keys2.valid = false
      return keys1.asInstanceOf[com.badlogic.gdx.utils.ObjectMap.Keys[K]]
    } else ()
    keys2.reset()
    this.keys2.valid = true
    this.keys1.valid = false
    return keys2.asInstanceOf[com.badlogic.gdx.utils.ObjectMap.Keys[K]]
  }
  def toString(separator: java.lang.String, braces: scala.Boolean): java.lang.String = {
    if (size == 0) {
      return if (braces) "{}" else ""
    } else ()
    val buffer: java.lang.StringBuilder = new java.lang.StringBuilder(32)
    if (braces) {
      buffer.append('{')
    } else ()
    val keys: com.badlogic.gdx.utils.Array[K] = this.keys$field;
    { var i: scala.Int = 0; val n: scala.Int = keys.size; while (i < n) { {
      val key: K = keys.get(i).asInstanceOf[K]
      if (i > 0) {
        buffer.append(separator)
      } else ()
      buffer.append(if (key == this) "(this)" else key)
      buffer.append('=')
      val value: V = this.get(key).asInstanceOf[V]
      buffer.append(if (value == this) "(this)" else value)
    }; i = i + 1 } }
    if (braces) {
      buffer.append('}')
    } else ()
    return buffer.toString()
  }
}
object OrderedMap {
  export com.badlogic.gdx.utils.ObjectMap.{OrderedMapEntries => _, OrderedMapKeys => _, OrderedMapValues => _, *}
  class OrderedMapEntries[K <: java.lang.Object, V <: java.lang.Object](map$p: OrderedMap[K, V]) extends com.badlogic.gdx.utils.ObjectMap.Entries[K, V](map$p) {
    private var keys: com.badlogic.gdx.utils.Array[K] = null.asInstanceOf[com.badlogic.gdx.utils.Array[K]]
    this.keys = map$p.keys$field
    def reset(): scala.Unit = {
      currentIndex = -1
      nextIndex = 0
      hasNext$field = this.map.size > 0
    }
    def next(): com.badlogic.gdx.utils.ObjectMap.Entry[K, V] = {
      if (!hasNext$field) {
        throw new java.util.NoSuchElementException()
      } else ()
      if (!valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      currentIndex = nextIndex
      this.entry.key = this.keys.get(nextIndex).asInstanceOf[K]
      this.entry.value = map.get(this.entry.key).asInstanceOf[V]
      nextIndex = nextIndex + 1
      hasNext$field = nextIndex < this.map.size
      return entry.asInstanceOf[com.badlogic.gdx.utils.ObjectMap.Entry[K, V]]
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
  class OrderedMapKeys[K <: java.lang.Object](map$p: OrderedMap[K, ?]) extends com.badlogic.gdx.utils.ObjectMap.Keys[K](map$p.asInstanceOf[OrderedMap[K, java.lang.Object]]) {
    private var keys: com.badlogic.gdx.utils.Array[K] = null.asInstanceOf[com.badlogic.gdx.utils.Array[K]]
    this.keys = map$p.asInstanceOf[OrderedMap[java.lang.Object, java.lang.Object]].keys$field.asInstanceOf[com.badlogic.gdx.utils.Array[K]]
    def reset(): scala.Unit = {
      currentIndex = -1
      nextIndex = 0
      hasNext$field = this.map.size > 0
    }
    def next(): K = {
      if (!hasNext$field) {
        throw new java.util.NoSuchElementException()
      } else ()
      if (!valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      val key: K = this.keys.get(nextIndex).asInstanceOf[K]
      currentIndex = nextIndex
      nextIndex = nextIndex + 1
      hasNext$field = nextIndex < this.map.size
      return key
    }
    def remove(): scala.Unit = {
      if (currentIndex < 0) {
        throw new java.lang.IllegalStateException("next must be called before remove.")
      } else ()
      map.asInstanceOf[OrderedMap[K, ?]].removeIndex(currentIndex)
      nextIndex = currentIndex
      currentIndex = -1
    }
    def toArray(array: com.badlogic.gdx.utils.Array[K]): com.badlogic.gdx.utils.Array[K] = {
      array.addAll(this.keys.asInstanceOf[com.badlogic.gdx.utils.Array[? <: K]], nextIndex, this.keys.size - nextIndex)
      nextIndex = this.keys.size
      hasNext$field = false
      return array
    }
    def toArray(): com.badlogic.gdx.utils.Array[K] = {
      return this.toArray(new com.badlogic.gdx.utils.Array(true, this.keys.size - nextIndex))
    }
  }
  class OrderedMapValues[V <: java.lang.Object](map$p: OrderedMap[?, V]) extends com.badlogic.gdx.utils.ObjectMap.Values[V](map$p.asInstanceOf[OrderedMap[java.lang.Object, V]]) {
    private var keys: com.badlogic.gdx.utils.Array[?] = null.asInstanceOf[com.badlogic.gdx.utils.Array[?]]
    this.keys = map$p.asInstanceOf[OrderedMap[java.lang.Object, java.lang.Object]].keys$field.asInstanceOf[com.badlogic.gdx.utils.Array[?]]
    def reset(): scala.Unit = {
      currentIndex = -1
      nextIndex = 0
      hasNext$field = this.map.size > 0
    }
    def next(): V = {
      if (!hasNext$field) {
        throw new java.util.NoSuchElementException()
      } else ()
      if (!valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      val value: V = map.get(this.keys.get(nextIndex)).asInstanceOf[V]
      currentIndex = nextIndex
      nextIndex = nextIndex + 1
      hasNext$field = nextIndex < this.map.size
      return value
    }
    def remove(): scala.Unit = {
      if (currentIndex < 0) {
        throw new java.lang.IllegalStateException("next must be called before remove.")
      } else ()
      map.asInstanceOf[OrderedMap[?, V]].removeIndex(currentIndex)
      nextIndex = currentIndex
      currentIndex = -1
    }
    def toArray(array: com.badlogic.gdx.utils.Array[V]): com.badlogic.gdx.utils.Array[V] = {
      val n: scala.Int = this.keys.size
      array.ensureCapacity(n - nextIndex)
      val keys: scala.Array[java.lang.Object] = this.keys.asInstanceOf[com.badlogic.gdx.utils.Array[java.lang.Object]].items;
      { var i: scala.Int = nextIndex; while (i < n) { {
        array.add(map.get(keys(i)))
      }; i = i + 1 } }
      currentIndex = n - 1
      nextIndex = n
      hasNext$field = false
      return array
    }
    def toArray(): com.badlogic.gdx.utils.Array[V] = {
      return this.toArray(new com.badlogic.gdx.utils.Array(true, this.keys.size - nextIndex))
    }
  }
}