package com.badlogic.gdx.utils

class ArrayMap[K, V] extends scala.collection.Iterable[com.badlogic.gdx.utils.ObjectMap.Entry[K, V]] {
  var keys$field: scala.Array[K] = null.asInstanceOf[scala.Array[K]]
  var values$field: scala.Array[V] = null.asInstanceOf[scala.Array[V]]
  var size: scala.Int = 0
  var ordered: scala.Boolean = false
  private var entries1: com.badlogic.gdx.utils.ArrayMap.Entries[K, V] = null.asInstanceOf[com.badlogic.gdx.utils.ArrayMap.Entries[K, V]]
  private var entries2: com.badlogic.gdx.utils.ArrayMap.Entries[K, V] = null.asInstanceOf[com.badlogic.gdx.utils.ArrayMap.Entries[K, V]]
  private var values1: com.badlogic.gdx.utils.ArrayMap.Values[V] = null.asInstanceOf[com.badlogic.gdx.utils.ArrayMap.Values[V]]
  private var values2: com.badlogic.gdx.utils.ArrayMap.Values[V] = null.asInstanceOf[com.badlogic.gdx.utils.ArrayMap.Values[V]]
  private var keys1: com.badlogic.gdx.utils.ArrayMap.Keys[K] = null.asInstanceOf[com.badlogic.gdx.utils.ArrayMap.Keys[K]]
  private var keys2: com.badlogic.gdx.utils.ArrayMap.Keys[K] = null.asInstanceOf[com.badlogic.gdx.utils.ArrayMap.Keys[K]]
  def this(ordered: scala.Boolean, capacity: scala.Int, keyArraySupplier: com.badlogic.gdx.utils.ArraySupplier[scala.Array[K]], valueArraySupplier: com.badlogic.gdx.utils.ArraySupplier[scala.Array[V]]) = {
    this()
    this.ordered = ordered
    this.keys$field = keyArraySupplier.get(capacity).asInstanceOf[scala.Array[K]]
    this.values$field = valueArraySupplier.get(capacity).asInstanceOf[scala.Array[V]]
  }
  def this(ordered: scala.Boolean, capacity: scala.Int) = {
    this(ordered, capacity, com.badlogic.gdx.utils.ArraySupplier.`object`(), com.badlogic.gdx.utils.ArraySupplier.`object`())
  }
  def this(capacity: scala.Int) = {
    this(true, capacity)
  }
  def this(keyArraySupplier: com.badlogic.gdx.utils.ArraySupplier[scala.Array[K]], valueArraySupplier: com.badlogic.gdx.utils.ArraySupplier[scala.Array[V]]) = {
    this(false, 16, keyArraySupplier, valueArraySupplier)
  }
  def this(ordered: scala.Boolean, capacity: scala.Int, keyArrayType: java.lang.Class[?], valueArrayType: java.lang.Class[?]) = {
    this(ordered, capacity, (size: scala.Int) => com.badlogic.gdx.utils.reflect.ArrayReflection.newInstance(keyArrayType, size).asInstanceOf[scala.Array[K]], (size: scala.Int) => com.badlogic.gdx.utils.reflect.ArrayReflection.newInstance(valueArrayType, size).asInstanceOf[scala.Array[V]])
  }
  def this(keyArrayType: java.lang.Class[?], valueArrayType: java.lang.Class[?]) = {
    this(false, 16, keyArrayType, valueArrayType)
  }
  def this(array: ArrayMap[K, V]) = {
    this()
    this.ordered = array.ordered
    this.keys$field = java.util.Arrays.copyOf(array.keys$field.asInstanceOf[scala.Array[java.lang.Object]], array.keys$field.length).asInstanceOf[scala.Array[K]]
    this.values$field = java.util.Arrays.copyOf(array.values$field.asInstanceOf[scala.Array[java.lang.Object]], array.values$field.length).asInstanceOf[scala.Array[V]]
    this.size = array.size
  }
  def put(key: K, value: V): scala.Int = {
    var index: scala.Int = this.indexOfKey(key)
    if (index == (-1)) {
      if (this.size == this.keys$field.length) {
        this.resize(java.lang.Math.max(8, (this.size * 1.75f).asInstanceOf[scala.Int].asInstanceOf[scala.Int]))
      } else ()
      index = { this.size += 1; this.size }
    } else ()
    this.keys$field(index) = key
    this.values$field(index) = value
    return index
  }
  def put(key: K, value: V, index: scala.Int): scala.Int = {
    val existingIndex: scala.Int = this.indexOfKey(key)
    if (existingIndex != (-1)) {
      this.removeIndex(existingIndex)
    } else {
      if (this.size == this.keys$field.length) {
        this.resize(java.lang.Math.max(8, (this.size * 1.75f).asInstanceOf[scala.Int].asInstanceOf[scala.Int]))
      } else ()
    }
    java.lang.System.arraycopy(this.keys$field, index, this.keys$field, index + 1, this.size - index)
    java.lang.System.arraycopy(this.values$field, index, this.values$field, index + 1, this.size - index)
    this.keys$field(index) = key
    this.values$field(index) = value
    this.size = this.size + 1
    return index
  }
  def putAll(map: ArrayMap[? <: K, ? <: V]): scala.Unit = {
    this.putAll(map, 0, map.size)
  }
  def putAll(map: ArrayMap[? <: K, ? <: V], offset: scala.Int, length: scala.Int): scala.Unit = {
    if ((offset + length) > map.size) {
      throw new java.lang.IllegalArgumentException((((("offset + length must be <= size: " + offset) + " + ") + length) + " <= ") + map.size)
    } else ()
    val sizeNeeded: scala.Int = (this.size + length) - offset
    if (sizeNeeded >= this.keys$field.length) {
      this.resize(java.lang.Math.max(8, (sizeNeeded * 1.75f).asInstanceOf[scala.Int].asInstanceOf[scala.Int]))
    } else ()
    java.lang.System.arraycopy(map.keys$field, offset, this.keys$field, this.size, length)
    java.lang.System.arraycopy(map.values$field, offset, this.values$field, this.size, length)
    this.size = this.size + length
  }
  def get(key: K): V = {
    return this.get(key, null.asInstanceOf[V]).asInstanceOf[V]
  }
  def get(key: K, defaultValue: V): V = {
    val keys: scala.Array[java.lang.Object] = this.keys$field.asInstanceOf[scala.Array[java.lang.Object]]
    var i: scala.Int = this.size - 1
    if (key == null) {
      { ; while (i >= 0) { {
        if (keys(i) == key) {
          return this.values$field(i)
        } else ()
      }; i = i - 1 } }
    } else {
      { ; while (i >= 0) { {
        if (key.equals(keys(i))) {
          return this.values$field(i)
        } else ()
      }; i = i - 1 } }
    }
    return defaultValue
  }
  def getKey(value: V, identity: scala.Boolean): K = {
    val values: scala.Array[java.lang.Object] = this.values$field.asInstanceOf[scala.Array[java.lang.Object]]
    var i: scala.Int = this.size - 1
    if (identity || (value == null)) {
      { ; while (i >= 0) { {
        if (values(i) == value) {
          return this.keys$field(i)
        } else ()
      }; i = i - 1 } }
    } else {
      { ; while (i >= 0) { {
        if (value.equals(values(i))) {
          return this.keys$field(i)
        } else ()
      }; i = i - 1 } }
    }
    return null.asInstanceOf[K]
  }
  def getKeyAt(index: scala.Int): K = {
    if (index >= this.size) {
      throw new java.lang.IndexOutOfBoundsException(java.lang.String.valueOf(index))
    } else ()
    return this.keys$field(index)
  }
  def getValueAt(index: scala.Int): V = {
    if (index >= this.size) {
      throw new java.lang.IndexOutOfBoundsException(java.lang.String.valueOf(index))
    } else ()
    return this.values$field(index)
  }
  def firstKey(): K = {
    if (this.size == 0) {
      throw new java.lang.IllegalStateException("Map is empty.")
    } else ()
    return this.keys$field(0)
  }
  def firstValue(): V = {
    if (this.size == 0) {
      throw new java.lang.IllegalStateException("Map is empty.")
    } else ()
    return this.values$field(0)
  }
  def setKey(index: scala.Int, key: K): scala.Unit = {
    if (index >= this.size) {
      throw new java.lang.IndexOutOfBoundsException(java.lang.String.valueOf(index))
    } else ()
    this.keys$field(index) = key
  }
  def setValue(index: scala.Int, value: V): scala.Unit = {
    if (index >= this.size) {
      throw new java.lang.IndexOutOfBoundsException(java.lang.String.valueOf(index))
    } else ()
    this.values$field(index) = value
  }
  def insert(index: scala.Int, key: K, value: V): scala.Unit = {
    if (index > this.size) {
      throw new java.lang.IndexOutOfBoundsException(java.lang.String.valueOf(index))
    } else ()
    if (this.size == this.keys$field.length) {
      this.resize(java.lang.Math.max(8, (this.size * 1.75f).asInstanceOf[scala.Int].asInstanceOf[scala.Int]))
    } else ()
    if (this.ordered) {
      java.lang.System.arraycopy(this.keys$field, index, this.keys$field, index + 1, this.size - index)
      java.lang.System.arraycopy(this.values$field, index, this.values$field, index + 1, this.size - index)
    } else {
      this.keys$field(this.size) = this.keys$field(index)
      this.values$field(this.size) = this.values$field(index)
    }
    this.size = this.size + 1
    this.keys$field(index) = key
    this.values$field(index) = value
  }
  def containsKey(key: K): scala.Boolean = {
    val keys: scala.Array[K] = this.keys$field
    var i: scala.Int = this.size - 1
    if (key == null) {
      while (i >= 0) {
        if (keys({ i -= 1; i }) == key) {
          return true
        } else ()
      }
    } else {
      while (i >= 0) {
        if (key.equals(keys({ i -= 1; i }).asInstanceOf[java.lang.Object])) {
          return true
        } else ()
      }
    }
    return false
  }
  def containsValue(value: V, identity: scala.Boolean): scala.Boolean = {
    val values: scala.Array[V] = this.values$field
    var i: scala.Int = this.size - 1
    if (identity || (value == null)) {
      while (i >= 0) {
        if (values({ i -= 1; i }) == value) {
          return true
        } else ()
      }
    } else {
      while (i >= 0) {
        if (value.equals(values({ i -= 1; i }).asInstanceOf[java.lang.Object])) {
          return true
        } else ()
      }
    }
    return false
  }
  def indexOfKey(key: K): scala.Int = {
    val keys: scala.Array[java.lang.Object] = this.keys$field.asInstanceOf[scala.Array[java.lang.Object]]
    if (key == null) {
      { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
        if (keys(i) == key) {
          return i
        } else ()
      }; i = i + 1 } }
    } else {
      { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
        if (key.equals(keys(i))) {
          return i
        } else ()
      }; i = i + 1 } }
    }
    return -1
  }
  def indexOfValue(value: V, identity: scala.Boolean): scala.Int = {
    val values: scala.Array[java.lang.Object] = this.values$field.asInstanceOf[scala.Array[java.lang.Object]]
    if (identity || (value == null)) {
      { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
        if (values(i) == value) {
          return i
        } else ()
      }; i = i + 1 } }
    } else {
      { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
        if (value.equals(values(i))) {
          return i
        } else ()
      }; i = i + 1 } }
    }
    return -1
  }
  def removeKey(key: K): V = {
    val keys: scala.Array[java.lang.Object] = this.keys$field.asInstanceOf[scala.Array[java.lang.Object]]
    if (key == null) {
      { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
        if (keys(i) == key) {
          val value: V = this.values$field(i)
          this.removeIndex(i)
          return value
        } else ()
      }; i = i + 1 } }
    } else {
      { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
        if (key.equals(keys(i))) {
          val value: V = this.values$field(i)
          this.removeIndex(i)
          return value
        } else ()
      }; i = i + 1 } }
    }
    return null.asInstanceOf[V]
  }
  def removeValue(value: V, identity: scala.Boolean): scala.Boolean = {
    val values: scala.Array[java.lang.Object] = this.values$field.asInstanceOf[scala.Array[java.lang.Object]]
    if (identity || (value == null)) {
      { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
        if (values(i) == value) {
          this.removeIndex(i)
          return true
        } else ()
      }; i = i + 1 } }
    } else {
      { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
        if (value.equals(values(i))) {
          this.removeIndex(i)
          return true
        } else ()
      }; i = i + 1 } }
    }
    return false
  }
  def removeIndex(index: scala.Int): scala.Unit = {
    if (index >= this.size) {
      throw new java.lang.IndexOutOfBoundsException(java.lang.String.valueOf(index))
    } else ()
    val keys: scala.Array[java.lang.Object] = this.keys$field.asInstanceOf[scala.Array[java.lang.Object]]
    this.size = this.size - 1
    if (this.ordered) {
      java.lang.System.arraycopy(keys, index + 1, keys, index, this.size - index)
      java.lang.System.arraycopy(this.values$field, index + 1, this.values$field, index, this.size - index)
    } else {
      keys(index) = keys(this.size)
      this.values$field(index) = this.values$field(this.size)
    }
    keys(this.size) = null
    this.values$field(this.size) = null.asInstanceOf[V]
  }
  def notEmpty(): scala.Boolean = {
    return this.size > 0
  }
  def isEmpty(): scala.Boolean = {
    return this.size == 0
  }
  def peekKey(): K = {
    return this.keys$field(this.size - 1)
  }
  def peekValue(): V = {
    return this.values$field(this.size - 1)
  }
  def clear(maximumCapacity: scala.Int): scala.Unit = {
    if (this.keys$field.length <= maximumCapacity) {
      this.clear()
      return
    } else ()
    this.size = 0
    this.resize(maximumCapacity)
  }
  def clear(): scala.Unit = {
    java.util.Arrays.fill(this.keys$field.asInstanceOf[scala.Array[java.lang.Object]], 0, this.size, null)
    java.util.Arrays.fill(this.values$field.asInstanceOf[scala.Array[java.lang.Object]], 0, this.size, null)
    this.size = 0
  }
  def shrink(): scala.Unit = {
    if (this.keys$field.length == this.size) {
      return
    } else ()
    this.resize(this.size)
  }
  def ensureCapacity(additionalCapacity: scala.Int): scala.Unit = {
    if (additionalCapacity < 0) {
      throw new java.lang.IllegalArgumentException("additionalCapacity must be >= 0: " + additionalCapacity)
    } else ()
    val sizeNeeded: scala.Int = this.size + additionalCapacity
    if (sizeNeeded > this.keys$field.length) {
      this.resize(java.lang.Math.max(java.lang.Math.max(8, sizeNeeded), (this.size * 1.75f).asInstanceOf[scala.Int].asInstanceOf[scala.Int]))
    } else ()
  }
  def resize(newSize: scala.Int): scala.Unit = {
    this.keys$field = java.util.Arrays.copyOf(this.keys$field.asInstanceOf[scala.Array[java.lang.Object]], newSize).asInstanceOf[scala.Array[K]]
    this.values$field = java.util.Arrays.copyOf(this.values$field.asInstanceOf[scala.Array[java.lang.Object]], newSize).asInstanceOf[scala.Array[V]]
  }
  def reverse(): scala.Unit = {
    { var i: scala.Int = 0; val lastIndex: scala.Int = this.size - 1; val n: scala.Int = this.size / 2; while (i < n) { {
      val ii: scala.Int = lastIndex - i
      val tempKey: K = this.keys$field(i)
      this.keys$field(i) = this.keys$field(ii)
      this.keys$field(ii) = tempKey
      val tempValue: V = this.values$field(i)
      this.values$field(i) = this.values$field(ii)
      this.values$field(ii) = tempValue
    }; i = i + 1 } }
  }
  def shuffle(): scala.Unit = {
    { var i: scala.Int = this.size - 1; while (i >= 0) { {
      val ii: scala.Int = com.badlogic.gdx.math.MathUtils.random(i)
      val tempKey: K = this.keys$field(i)
      this.keys$field(i) = this.keys$field(ii)
      this.keys$field(ii) = tempKey
      val tempValue: V = this.values$field(i)
      this.values$field(i) = this.values$field(ii)
      this.values$field(ii) = tempValue
    }; i = i - 1 } }
  }
  def truncate(newSize: scala.Int): scala.Unit = {
    if (newSize < 0) {
      throw new java.lang.IllegalArgumentException("newSize must be >= 0: " + newSize)
    } else ()
    if (this.size <= newSize) {
      return
    } else ();
    { var i: scala.Int = newSize; while (i < this.size) { {
      this.keys$field(i) = null.asInstanceOf[K]
      this.values$field(i) = null.asInstanceOf[V]
    }; i = i + 1 } }
    this.size = newSize
  }
  def hashCode(): scala.Int = {
    val keys: scala.Array[K] = this.keys$field
    val values: scala.Array[V] = this.values$field
    var h: scala.Int = 0;
    { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
      val key: K = keys(i)
      val value: V = values(i)
      if (key != null) {
        h = h + (key.hashCode() * 31)
      } else ()
      if (value != null) {
        h = h + value.hashCode()
      } else ()
    }; i = i + 1 } }
    return h
  }
  def equals(obj: java.lang.Object): scala.Boolean = {
    if (obj == this) {
      return true
    } else ()
    if (!obj.isInstanceOf[ArrayMap[K, V]]) {
      return false
    } else ()
    val other: ArrayMap[K, V] = obj.asInstanceOf[ArrayMap[K, V]].asInstanceOf[ArrayMap[K, V]]
    if (other.size != this.size) {
      return false
    } else ()
    val keys: scala.Array[K] = this.keys$field
    val values: scala.Array[V] = this.values$field;
    { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
      val key: K = keys(i)
      val value: V = values(i)
      if (value == null) {
        if (other.asInstanceOf[ArrayMap[java.lang.Object, java.lang.Object]].get(key.asInstanceOf[java.lang.Object], com.badlogic.gdx.utils.ObjectMap.dummy.asInstanceOf[java.lang.Object]) != null) {
          return false
        } else ()
      } else {
        if (!value.equals(other.asInstanceOf[ArrayMap[java.lang.Object, java.lang.Object]].get(key.asInstanceOf[java.lang.Object]))) {
          return false
        } else ()
      }
    }; i = i + 1 } }
    return true
  }
  def equalsIdentity(obj: java.lang.Object): scala.Boolean = {
    if (obj == this) {
      return true
    } else ()
    if (!obj.isInstanceOf[ArrayMap[K, V]]) {
      return false
    } else ()
    val other: ArrayMap[K, V] = obj.asInstanceOf[ArrayMap[K, V]].asInstanceOf[ArrayMap[K, V]]
    if (other.size != this.size) {
      return false
    } else ()
    val keys: scala.Array[K] = this.keys$field
    val values: scala.Array[V] = this.values$field;
    { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
      if (values(i) != other.asInstanceOf[ArrayMap[java.lang.Object, java.lang.Object]].get(keys(i).asInstanceOf[java.lang.Object], com.badlogic.gdx.utils.ObjectMap.dummy.asInstanceOf[java.lang.Object])) {
        return false
      } else ()
    }; i = i + 1 } }
    return true
  }
  def toString(): java.lang.String = {
    if (this.size == 0) {
      return "{}"
    } else ()
    val keys: scala.Array[K] = this.keys$field
    val values: scala.Array[V] = this.values$field
    val buffer: java.lang.StringBuilder = new java.lang.StringBuilder(32)
    buffer.append('{')
    buffer.append(keys(0).asInstanceOf[java.lang.Object])
    buffer.append('=')
    buffer.append(values(0).asInstanceOf[java.lang.Object]);
    { var i: scala.Int = 1; while (i < this.size) { {
      buffer.append(", ")
      buffer.append(keys(i).asInstanceOf[java.lang.Object])
      buffer.append('=')
      buffer.append(values(i).asInstanceOf[java.lang.Object])
    }; i = i + 1 } }
    buffer.append('}')
    return buffer.toString()
  }
  def iterator(): scala.collection.Iterator[com.badlogic.gdx.utils.ObjectMap.Entry[K, V]] = {
    return this.entries()
  }
  def entries(): com.badlogic.gdx.utils.ArrayMap.Entries[K, V] = {
    if (com.badlogic.gdx.utils.Collections.allocateIterators) {
      return new com.badlogic.gdx.utils.ArrayMap.Entries[K, V](this).asInstanceOf[com.badlogic.gdx.utils.ArrayMap.Entries[K, V]]
    } else ()
    if (this.entries1 == null) {
      this.entries1 = new com.badlogic.gdx.utils.ArrayMap.Entries[K, V](this).asInstanceOf[com.badlogic.gdx.utils.ArrayMap.Entries[K, V]]
      this.entries2 = new com.badlogic.gdx.utils.ArrayMap.Entries[K, V](this).asInstanceOf[com.badlogic.gdx.utils.ArrayMap.Entries[K, V]]
    } else ()
    if (!this.entries1.valid) {
      this.entries1.index = 0
      this.entries1.valid = true
      this.entries2.valid = false
      return this.entries1.asInstanceOf[com.badlogic.gdx.utils.ArrayMap.Entries[K, V]]
    } else ()
    this.entries2.index = 0
    this.entries2.valid = true
    this.entries1.valid = false
    return this.entries2.asInstanceOf[com.badlogic.gdx.utils.ArrayMap.Entries[K, V]]
  }
  def values(): com.badlogic.gdx.utils.ArrayMap.Values[V] = {
    if (com.badlogic.gdx.utils.Collections.allocateIterators) {
      return new com.badlogic.gdx.utils.ArrayMap.Values[V](this).asInstanceOf[com.badlogic.gdx.utils.ArrayMap.Values[V]]
    } else ()
    if (this.values1 == null) {
      this.values1 = new com.badlogic.gdx.utils.ArrayMap.Values[V](this).asInstanceOf[com.badlogic.gdx.utils.ArrayMap.Values[V]]
      this.values2 = new com.badlogic.gdx.utils.ArrayMap.Values[V](this).asInstanceOf[com.badlogic.gdx.utils.ArrayMap.Values[V]]
    } else ()
    if (!this.values1.valid) {
      this.values1.index = 0
      this.values1.valid = true
      this.values2.valid = false
      return this.values1.asInstanceOf[com.badlogic.gdx.utils.ArrayMap.Values[V]]
    } else ()
    this.values2.index = 0
    this.values2.valid = true
    this.values1.valid = false
    return this.values2.asInstanceOf[com.badlogic.gdx.utils.ArrayMap.Values[V]]
  }
  def keys(): com.badlogic.gdx.utils.ArrayMap.Keys[K] = {
    if (com.badlogic.gdx.utils.Collections.allocateIterators) {
      return new com.badlogic.gdx.utils.ArrayMap.Keys[K](this).asInstanceOf[com.badlogic.gdx.utils.ArrayMap.Keys[K]]
    } else ()
    if (this.keys1 == null) {
      this.keys1 = new com.badlogic.gdx.utils.ArrayMap.Keys[K](this).asInstanceOf[com.badlogic.gdx.utils.ArrayMap.Keys[K]]
      this.keys2 = new com.badlogic.gdx.utils.ArrayMap.Keys[K](this).asInstanceOf[com.badlogic.gdx.utils.ArrayMap.Keys[K]]
    } else ()
    if (!this.keys1.valid) {
      this.keys1.index = 0
      this.keys1.valid = true
      this.keys2.valid = false
      return this.keys1.asInstanceOf[com.badlogic.gdx.utils.ArrayMap.Keys[K]]
    } else ()
    this.keys2.index = 0
    this.keys2.valid = true
    this.keys1.valid = false
    return this.keys2.asInstanceOf[com.badlogic.gdx.utils.ArrayMap.Keys[K]]
  }
}
object ArrayMap {
  class Entries[K, V] extends scala.collection.Iterable[com.badlogic.gdx.utils.ObjectMap.Entry[K, V]] with scala.collection.Iterator[com.badlogic.gdx.utils.ObjectMap.Entry[K, V]] {
    private var map: ArrayMap[K, V] = null.asInstanceOf[ArrayMap[K, V]]
    var entry: com.badlogic.gdx.utils.ObjectMap.Entry[K, V] = new com.badlogic.gdx.utils.ObjectMap.Entry[K, V]().asInstanceOf[com.badlogic.gdx.utils.ObjectMap.Entry[K, V]]
    var index: scala.Int = 0
    var valid: scala.Boolean = true
    def this(map: ArrayMap[K, V]) = {
      this()
      this.map = map
    }
    def hasNext(): scala.Boolean = {
      if (!this.valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      return this.index < this.map.size
    }
    def iterator(): scala.collection.Iterator[com.badlogic.gdx.utils.ObjectMap.Entry[K, V]] = {
      return this
    }
    def next(): com.badlogic.gdx.utils.ObjectMap.Entry[K, V] = {
      if (this.index >= this.map.size) {
        throw new java.util.NoSuchElementException(java.lang.String.valueOf(this.index))
      } else ()
      if (!this.valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      this.entry.key = this.map.keys$field(this.index)
      this.entry.value = this.map.values$field({ this.index += 1; this.index })
      return this.entry
    }
    def remove(): scala.Unit = {
      this.index = this.index - 1
      this.map.removeIndex(this.index)
    }
    def reset(): scala.Unit = {
      this.index = 0
    }
  }
  class Values[V] extends scala.collection.Iterable[V] with scala.collection.Iterator[V] {
    private var map: ArrayMap[java.lang.Object, V] = null.asInstanceOf[ArrayMap[java.lang.Object, V]]
    var index: scala.Int = 0
    var valid: scala.Boolean = true
    def this(map: ArrayMap[java.lang.Object, V]) = {
      this()
      this.map = map
    }
    def hasNext(): scala.Boolean = {
      if (!this.valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      return this.index < this.map.size
    }
    def iterator(): scala.collection.Iterator[V] = {
      return this
    }
    def next(): V = {
      if (this.index >= this.map.size) {
        throw new java.util.NoSuchElementException(java.lang.String.valueOf(this.index))
      } else ()
      if (!this.valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      return this.map.values$field({ this.index += 1; this.index })
    }
    def remove(): scala.Unit = {
      this.index = this.index - 1
      this.map.removeIndex(this.index)
    }
    def reset(): scala.Unit = {
      this.index = 0
    }
    def toArray(): com.badlogic.gdx.utils.Array[V] = {
      return new com.badlogic.gdx.utils.Array(true, this.map.values$field, this.index, this.map.size - this.index).asInstanceOf[com.badlogic.gdx.utils.Array[V]]
    }
    def toArray(array: com.badlogic.gdx.utils.Array[?]): com.badlogic.gdx.utils.Array[V] = {
      array.asInstanceOf[com.badlogic.gdx.utils.Array[java.lang.Object]].addAll(this.map.values$field.asInstanceOf[scala.Array[java.lang.Object]], this.index, this.map.size - this.index)
      return array.asInstanceOf[com.badlogic.gdx.utils.Array[V]]
    }
  }
  class Keys[K] extends scala.collection.Iterable[K] with scala.collection.Iterator[K] {
    private var map: ArrayMap[K, java.lang.Object] = null.asInstanceOf[ArrayMap[K, java.lang.Object]]
    var index: scala.Int = 0
    var valid: scala.Boolean = true
    def this(map: ArrayMap[K, java.lang.Object]) = {
      this()
      this.map = map
    }
    def hasNext(): scala.Boolean = {
      if (!this.valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      return this.index < this.map.size
    }
    def iterator(): scala.collection.Iterator[K] = {
      return this
    }
    def next(): K = {
      if (this.index >= this.map.size) {
        throw new java.util.NoSuchElementException(java.lang.String.valueOf(this.index))
      } else ()
      if (!this.valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      return this.map.keys$field({ this.index += 1; this.index })
    }
    def remove(): scala.Unit = {
      this.index = this.index - 1
      this.map.removeIndex(this.index)
    }
    def reset(): scala.Unit = {
      this.index = 0
    }
    def toArray(): com.badlogic.gdx.utils.Array[K] = {
      return new com.badlogic.gdx.utils.Array(true, this.map.keys$field, this.index, this.map.size - this.index).asInstanceOf[com.badlogic.gdx.utils.Array[K]]
    }
    def toArray(array: com.badlogic.gdx.utils.Array[?]): com.badlogic.gdx.utils.Array[K] = {
      array.asInstanceOf[com.badlogic.gdx.utils.Array[java.lang.Object]].addAll(this.map.keys$field.asInstanceOf[scala.Array[java.lang.Object]], this.index, this.map.size - this.index)
      return array.asInstanceOf[com.badlogic.gdx.utils.Array[K]]
    }
  }
}