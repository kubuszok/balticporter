package com.badlogic.gdx.utils

class LongMap[V <: java.lang.Object](initialCapacity: scala.Int, loadFactor$p: scala.Float) extends balticporter.runtime.JavaIterable[com.badlogic.gdx.utils.LongMap.Entry[V]] {
  var size: scala.Int = 0
  var keyTable: scala.Array[scala.Long] = null.asInstanceOf[scala.Array[scala.Long]]
  var valueTable: scala.Array[V] = null.asInstanceOf[scala.Array[V]]
  var zeroValue: V = null.asInstanceOf[V]
  var hasZeroValue: scala.Boolean = false
  private var loadFactor: scala.Float = 0.0f
  private var threshold: scala.Int = 0
  var shift: scala.Int = 0
  var mask: scala.Int = 0
  private var entries1: com.badlogic.gdx.utils.LongMap.Entries[V] = null.asInstanceOf[com.badlogic.gdx.utils.LongMap.Entries[V]]
  private var entries2: com.badlogic.gdx.utils.LongMap.Entries[V] = null.asInstanceOf[com.badlogic.gdx.utils.LongMap.Entries[V]]
  private var values1: com.badlogic.gdx.utils.LongMap.Values[V] = null.asInstanceOf[com.badlogic.gdx.utils.LongMap.Values[V]]
  private var values2: com.badlogic.gdx.utils.LongMap.Values[V] = null.asInstanceOf[com.badlogic.gdx.utils.LongMap.Values[V]]
  private var keys1: com.badlogic.gdx.utils.LongMap.Keys = null.asInstanceOf[com.badlogic.gdx.utils.LongMap.Keys]
  private var keys2: com.badlogic.gdx.utils.LongMap.Keys = null.asInstanceOf[com.badlogic.gdx.utils.LongMap.Keys]
  val tableSize: scala.Int = com.badlogic.gdx.utils.ObjectSet.tableSize(initialCapacity, loadFactor$p)
  def this() = {
    this(51, 0.8f)
  }
  def this(initialCapacity: scala.Int) = {
    this(initialCapacity, 0.8f)
  }
  def this(map: LongMap[? <: V]) = {
    this((map.keyTable.length * map.loadFactor).asInstanceOf[scala.Int].asInstanceOf[scala.Int], map.loadFactor)
    java.lang.System.arraycopy(map.keyTable, 0, this.keyTable, 0, map.keyTable.length)
    java.lang.System.arraycopy(map.valueTable, 0, this.valueTable, 0, map.valueTable.length)
    this.size = map.size
    this.zeroValue = map.zeroValue.asInstanceOf[V]
    this.hasZeroValue = map.hasZeroValue
  }
  if ((loadFactor$p <= 0.0f) || (loadFactor$p >= 1.0f)) {
    throw new java.lang.IllegalArgumentException("loadFactor must be > 0 and < 1: " + loadFactor$p)
  } else ()
  this.loadFactor = loadFactor$p
  this.threshold = (tableSize * loadFactor$p).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
  this.mask = tableSize - 1
  this.shift = java.lang.Long.numberOfLeadingZeros(this.mask)
  this.keyTable = new scala.Array[scala.Long](tableSize)
  this.valueTable = new scala.Array[java.lang.Object](tableSize).asInstanceOf[scala.Array[V]].asInstanceOf[scala.Array[V]]
  def place(item: scala.Long): scala.Int = {
    return (((item ^ (item >>> 32)) * -7046029254386353131L) >>> this.shift).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
  }
  private def locateKey(key: scala.Long): scala.Int = {
    {
      val keyTable: scala.Array[scala.Long] = this.keyTable;
      { var i: scala.Int = this.place(key); while (true) { {
        val other: scala.Long = keyTable(i)
        if (other == 0) {
          return -(i + 1)
        } else ()
        if (other == key) {
          return i
        } else ()
      }; i = (i + 1) & this.mask } }
    }
    throw new java.lang.RuntimeException("unreachable")
  }
  @com.badlogic.gdx.utils.Null
  def put(key: scala.Long, value: V): V = {
    if (key == 0) {
      val oldValue: V = this.zeroValue
      this.zeroValue = value
      if (!this.hasZeroValue) {
        this.hasZeroValue = true
        this.size = this.size + 1
      } else ()
      return oldValue
    } else ()
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
  def putAll(map: LongMap[? <: V]): scala.Unit = {
    this.ensureCapacity(map.size)
    if (map.hasZeroValue) {
      this.put(0, map.zeroValue)
    } else ()
    val keyTable: scala.Array[scala.Long] = map.keyTable
    val valueTable: scala.Array[V] = map.valueTable.asInstanceOf[scala.Array[V]];
    { var i: scala.Int = 0; val n: scala.Int = keyTable.length; while (i < n) { {
      val key: scala.Long = keyTable(i)
      if (key != 0) {
        this.put(key, valueTable(i))
      } else ()
    }; i = i + 1 } }
  }
  private def putResize(key: scala.Long, value: V): scala.Unit = {
    val keyTable: scala.Array[scala.Long] = this.keyTable;
    { var i: scala.Int = this.place(key); while (true) { {
      if (keyTable(i) == 0) {
        keyTable(i) = key
        this.valueTable(i) = value
        return
      } else ()
    }; i = (i + 1) & this.mask } }
  }
  @com.badlogic.gdx.utils.Null
  def get(key: scala.Long): V = {
    if (key == 0) {
      return if (this.hasZeroValue) this.zeroValue else null.asInstanceOf[V]
    } else ()
    val i: scala.Int = this.locateKey(key)
    return if (i >= 0) this.valueTable(i) else null.asInstanceOf[V]
  }
  def get(key: scala.Long, defaultValue: V): V = {
    if (key == 0) {
      return if (this.hasZeroValue) this.zeroValue else defaultValue
    } else ()
    val i: scala.Int = this.locateKey(key)
    return if (i >= 0) this.valueTable(i) else defaultValue
  }
  @com.badlogic.gdx.utils.Null
  def remove(key$arg: scala.Long): V = {
    var key: scala.Long = key$arg
    if (key == 0) {
      if (!this.hasZeroValue) {
        return null.asInstanceOf[V]
      } else ()
      this.hasZeroValue = false
      val oldValue: V = this.zeroValue
      this.zeroValue = null.asInstanceOf[V]
      this.size = this.size - 1
      return oldValue
    } else ()
    var i: scala.Int = this.locateKey(key)
    if (i < 0) {
      return null.asInstanceOf[V]
    } else ()
    val keyTable: scala.Array[scala.Long] = this.keyTable
    val valueTable: scala.Array[V] = this.valueTable
    val oldValue: V = valueTable(i)
    val mask: scala.Int = this.mask
    var next: scala.Int = (i + 1) & mask
    while ({
      key = keyTable(next)
      key
    } != 0) {
      val placement: scala.Int = this.place(key)
      if (((next - placement) & mask) > ((i - placement) & mask)) {
        keyTable(i) = key
        valueTable(i) = valueTable(next)
        i = next
      } else ()
      next = (next + 1) & mask
    }
    keyTable(i) = 0
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
    this.hasZeroValue = false
    this.zeroValue = null.asInstanceOf[V]
    this.resize(tableSize)
  }
  def clear(): scala.Unit = {
    if (this.size == 0) {
      return
    } else ()
    this.size = 0
    java.util.Arrays.fill(this.keyTable, 0)
    java.util.Arrays.fill(this.valueTable.asInstanceOf[scala.Array[java.lang.Object]], null)
    this.zeroValue = null.asInstanceOf[V]
    this.hasZeroValue = false
  }
  def containsValue(value: java.lang.Object, identity: scala.Boolean): scala.Boolean = {
    val valueTable: scala.Array[V] = this.valueTable
    if (value == null) {
      if (this.hasZeroValue && (this.zeroValue == null)) {
        return true
      } else ()
      val keyTable: scala.Array[scala.Long] = this.keyTable;
      { var i: scala.Int = valueTable.length - 1; while (i >= 0) { {
        if ((keyTable(i) != 0) && (valueTable(i) == null)) {
          return true
        } else ()
      }; i = i - 1 } }
    } else {
      if (identity) {
        if (value == this.zeroValue) {
          return true
        } else ();
        { var i: scala.Int = valueTable.length - 1; while (i >= 0) { {
          if (valueTable(i) == value) {
            return true
          } else ()
        }; i = i - 1 } }
      } else {
        if (this.hasZeroValue && value.equals(this.zeroValue.asInstanceOf[java.lang.Object])) {
          return true
        } else ();
        { var i: scala.Int = valueTable.length - 1; while (i >= 0) { {
          if (value.equals(valueTable(i).asInstanceOf[java.lang.Object])) {
            return true
          } else ()
        }; i = i - 1 } }
      }
    }
    return false
  }
  def containsKey(key: scala.Long): scala.Boolean = {
    if (key == 0) {
      return this.hasZeroValue
    } else ()
    return this.locateKey(key) >= 0
  }
  def findKey(value: java.lang.Object, identity: scala.Boolean, notFound: scala.Long): scala.Long = {
    val valueTable: scala.Array[V] = this.valueTable
    if (value == null) {
      if (this.hasZeroValue && (this.zeroValue == null)) {
        return 0
      } else ()
      val keyTable: scala.Array[scala.Long] = this.keyTable;
      { var i: scala.Int = valueTable.length - 1; while (i >= 0) { {
        if ((keyTable(i) != 0) && (valueTable(i) == null)) {
          return keyTable(i)
        } else ()
      }; i = i - 1 } }
    } else {
      if (identity) {
        if (value == this.zeroValue) {
          return 0
        } else ();
        { var i: scala.Int = valueTable.length - 1; while (i >= 0) { {
          if (valueTable(i) == value) {
            return this.keyTable(i)
          } else ()
        }; i = i - 1 } }
      } else {
        if (this.hasZeroValue && value.equals(this.zeroValue.asInstanceOf[java.lang.Object])) {
          return 0
        } else ();
        { var i: scala.Int = valueTable.length - 1; while (i >= 0) { {
          if (value.equals(valueTable(i).asInstanceOf[java.lang.Object])) {
            return this.keyTable(i)
          } else ()
        }; i = i - 1 } }
      }
    }
    return notFound
  }
  def ensureCapacity(additionalCapacity: scala.Int): scala.Unit = {
    val tableSize: scala.Int = com.badlogic.gdx.utils.ObjectSet.tableSize(this.size + additionalCapacity, this.loadFactor)
    if (this.keyTable.length < tableSize) {
      this.resize(tableSize)
    } else ()
  }
  private def resize(newSize: scala.Int): scala.Unit = {
    val oldCapacity: scala.Int = this.keyTable.length
    this.threshold = (newSize * this.loadFactor).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    this.mask = newSize - 1
    this.shift = java.lang.Long.numberOfLeadingZeros(this.mask)
    val oldKeyTable: scala.Array[scala.Long] = this.keyTable
    val oldValueTable: scala.Array[V] = this.valueTable
    this.keyTable = new scala.Array[scala.Long](newSize)
    this.valueTable = new scala.Array[java.lang.Object](newSize).asInstanceOf[scala.Array[V]].asInstanceOf[scala.Array[V]]
    if (this.size > 0) {
      { var i: scala.Int = 0; while (i < oldCapacity) { {
        val key: scala.Long = oldKeyTable(i)
        if (key != 0) {
          this.putResize(key, oldValueTable(i))
        } else ()
      }; i = i + 1 } }
    } else ()
  }
  override def hashCode(): scala.Int = {
    var h: scala.Int = this.size
    if (this.hasZeroValue && (this.zeroValue != null)) {
      h = h + this.zeroValue.hashCode()
    } else ()
    val keyTable: scala.Array[scala.Long] = this.keyTable
    val valueTable: scala.Array[V] = this.valueTable;
    { var i: scala.Int = 0; val n: scala.Int = keyTable.length; while (i < n) { {
      val key: scala.Long = keyTable(i)
      if (key != 0) {
        h = (h + (key * 31)).asInstanceOf[scala.Int]
        val value: V = valueTable(i)
        if (value != null) {
          h = h + value.hashCode()
        } else ()
      } else ()
    }; i = i + 1 } }
    return h
  }
  override def equals(obj: java.lang.Object): scala.Boolean = {
    if (obj == this) {
      return true
    } else ()
    if (!obj.isInstanceOf[LongMap[?]]) {
      return false
    } else ()
    val other: LongMap[?] = obj.asInstanceOf[LongMap[?]].asInstanceOf[LongMap[?]]
    if (other.size != this.size) {
      return false
    } else ()
    if (other.hasZeroValue != this.hasZeroValue) {
      return false
    } else ()
    if (this.hasZeroValue) {
      if (other.asInstanceOf[LongMap[java.lang.Object]].zeroValue == null) {
        if (this.zeroValue != null) {
          return false
        } else ()
      } else {
        if (!other.asInstanceOf[LongMap[java.lang.Object]].zeroValue.equals(this.zeroValue.asInstanceOf[java.lang.Object])) {
          return false
        } else ()
      }
    } else ()
    val keyTable: scala.Array[scala.Long] = this.keyTable
    val valueTable: scala.Array[V] = this.valueTable;
    { var i: scala.Int = 0; val n: scala.Int = keyTable.length; while (i < n) { {
      val key: scala.Long = keyTable(i)
      if (key != 0) {
        val value: V = valueTable(i)
        if (value == null) {
          if (other.asInstanceOf[LongMap[java.lang.Object]].get(key, com.badlogic.gdx.utils.ObjectMap.dummy.asInstanceOf[java.lang.Object]) != null) {
            return false
          } else ()
        } else {
          if (!value.equals(other.get(key).asInstanceOf[java.lang.Object])) {
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
    if (!obj.isInstanceOf[LongMap[?]]) {
      return false
    } else ()
    val other: LongMap[?] = obj.asInstanceOf[LongMap[?]].asInstanceOf[LongMap[?]]
    if (other.size != this.size) {
      return false
    } else ()
    if (other.hasZeroValue != this.hasZeroValue) {
      return false
    } else ()
    if (this.hasZeroValue && (this.zeroValue != other.asInstanceOf[LongMap[java.lang.Object]].zeroValue)) {
      return false
    } else ()
    val keyTable: scala.Array[scala.Long] = this.keyTable
    val valueTable: scala.Array[V] = this.valueTable;
    { var i: scala.Int = 0; val n: scala.Int = keyTable.length; while (i < n) { {
      val key: scala.Long = keyTable(i)
      if ((key != 0) && (valueTable(i) != other.asInstanceOf[LongMap[java.lang.Object]].get(key, com.badlogic.gdx.utils.ObjectMap.dummy.asInstanceOf[java.lang.Object]))) {
        return false
      } else ()
    }; i = i + 1 } }
    return true
  }
  override def toString(): java.lang.String = {
    if (this.size == 0) {
      return "[]"
    } else ()
    val buffer: java.lang.StringBuilder = new java.lang.StringBuilder(32)
    buffer.append('[')
    val keyTable: scala.Array[scala.Long] = this.keyTable
    val valueTable: scala.Array[V] = this.valueTable
    var i: scala.Int = keyTable.length
    if (this.hasZeroValue) {
      buffer.append("0=")
      buffer.append(this.zeroValue.asInstanceOf[java.lang.Object])
    } else {
      while ({ i -= 1; i } > 0) {
        val key: scala.Long = keyTable(i)
        if (key == 0) {
          /* continue */ ()
        } else ()
        buffer.append(key)
        buffer.append('=')
        buffer.append(valueTable(i).asInstanceOf[java.lang.Object])
        /* break */ ()
      }
    }
    while ({ i -= 1; i } > 0) {
      val key: scala.Long = keyTable(i)
      if (key == 0) {
        /* continue */ ()
      } else ()
      buffer.append(", ")
      buffer.append(key)
      buffer.append('=')
      buffer.append(valueTable(i).asInstanceOf[java.lang.Object])
    }
    buffer.append(']')
    return buffer.toString()
  }
  override def iterator(): balticporter.runtime.JavaIterator[com.badlogic.gdx.utils.LongMap.Entry[V]] = {
    return this.entries()
  }
  def entries(): com.badlogic.gdx.utils.LongMap.Entries[V] = {
    if (com.badlogic.gdx.utils.Collections.allocateIterators) {
      return new com.badlogic.gdx.utils.LongMap.Entries[V](this).asInstanceOf[com.badlogic.gdx.utils.LongMap.Entries[V]]
    } else ()
    if (this.entries1 == null) {
      this.entries1 = new com.badlogic.gdx.utils.LongMap.Entries[V](this).asInstanceOf[com.badlogic.gdx.utils.LongMap.Entries[V]]
      this.entries2 = new com.badlogic.gdx.utils.LongMap.Entries[V](this).asInstanceOf[com.badlogic.gdx.utils.LongMap.Entries[V]]
    } else ()
    if (!this.entries1.valid) {
      this.entries1.reset()
      this.entries1.valid = true
      this.entries2.valid = false
      return this.entries1.asInstanceOf[com.badlogic.gdx.utils.LongMap.Entries[V]]
    } else ()
    this.entries2.reset()
    this.entries2.valid = true
    this.entries1.valid = false
    return this.entries2.asInstanceOf[com.badlogic.gdx.utils.LongMap.Entries[V]]
  }
  def values(): com.badlogic.gdx.utils.LongMap.Values[V] = {
    if (com.badlogic.gdx.utils.Collections.allocateIterators) {
      return new com.badlogic.gdx.utils.LongMap.Values[V](this.asInstanceOf[LongMap[V]]).asInstanceOf[com.badlogic.gdx.utils.LongMap.Values[V]]
    } else ()
    if (this.values1 == null) {
      this.values1 = new com.badlogic.gdx.utils.LongMap.Values[V](this.asInstanceOf[LongMap[V]]).asInstanceOf[com.badlogic.gdx.utils.LongMap.Values[V]]
      this.values2 = new com.badlogic.gdx.utils.LongMap.Values[V](this.asInstanceOf[LongMap[V]]).asInstanceOf[com.badlogic.gdx.utils.LongMap.Values[V]]
    } else ()
    if (!this.values1.valid) {
      this.values1.reset()
      this.values1.valid = true
      this.values2.valid = false
      return this.values1.asInstanceOf[com.badlogic.gdx.utils.LongMap.Values[V]]
    } else ()
    this.values2.reset()
    this.values2.valid = true
    this.values1.valid = false
    return this.values2.asInstanceOf[com.badlogic.gdx.utils.LongMap.Values[V]]
  }
  def keys(): com.badlogic.gdx.utils.LongMap.Keys = {
    if (com.badlogic.gdx.utils.Collections.allocateIterators) {
      return new com.badlogic.gdx.utils.LongMap.Keys(this)
    } else ()
    if (this.keys1 == null) {
      this.keys1 = new com.badlogic.gdx.utils.LongMap.Keys(this)
      this.keys2 = new com.badlogic.gdx.utils.LongMap.Keys(this)
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
object LongMap {
  class Entry[V <: java.lang.Object] {
    var key: scala.Long = 0L
    var value: V = null.asInstanceOf[V]
    override def toString(): java.lang.String = {
      return (java.lang.String.valueOf(this.key) + "=") + this.value
    }
  }
  class MapIterator[V <: java.lang.Object](map$p: LongMap[V]) {
    var hasNext$field: scala.Boolean = false
    var map: LongMap[V] = null.asInstanceOf[LongMap[V]]
    var nextIndex: scala.Int = 0
    var currentIndex: scala.Int = 0
    var valid: scala.Boolean = true
    this.map = map$p
    this.reset()
    def reset(): scala.Unit = {
      this.currentIndex = com.badlogic.gdx.utils.LongMap.MapIterator.INDEX_ILLEGAL
      this.nextIndex = com.badlogic.gdx.utils.LongMap.MapIterator.INDEX_ZERO
      if (this.map.hasZeroValue) {
        this.hasNext$field = true
      } else {
        this.findNextIndex()
      }
    }
    def findNextIndex(): scala.Unit = {
      val keyTable: scala.Array[scala.Long] = this.map.keyTable;
      { val n: scala.Int = keyTable.length; while ({ this.nextIndex += 1; this.nextIndex } < n) { {
        if (keyTable(this.nextIndex) != 0) {
          this.hasNext$field = true
          return
        } else ()
      };  } }
      this.hasNext$field = false
    }
    def remove(): scala.Unit = {
      var i: scala.Int = this.currentIndex
      if ((i == com.badlogic.gdx.utils.LongMap.MapIterator.INDEX_ZERO) && this.map.hasZeroValue) {
        this.map.hasZeroValue = false
        this.map.zeroValue = null.asInstanceOf[V]
      } else {
        if (i < 0) {
          throw new java.lang.IllegalStateException("next must be called before remove.")
        } else {
          val keyTable: scala.Array[scala.Long] = this.map.keyTable
          val valueTable: scala.Array[V] = this.map.valueTable
          val mask: scala.Int = this.map.mask
          var next: scala.Int = (i + 1) & mask
          var key: scala.Long = 0L
          while ({
            key = keyTable(next)
            key
          } != 0) {
            val placement: scala.Int = this.map.place(key)
            if (((next - placement) & mask) > ((i - placement) & mask)) {
              keyTable(i) = key
              valueTable(i) = valueTable(next)
              i = next
            } else ()
            next = (next + 1) & mask
          }
          keyTable(i) = 0
          valueTable(i) = null.asInstanceOf[V]
          if (i != this.currentIndex) {
            this.nextIndex = this.nextIndex - 1
          } else ()
        }
      }
      this.currentIndex = com.badlogic.gdx.utils.LongMap.MapIterator.INDEX_ILLEGAL
      this.map.size = this.map.size - 1
    }
  }
  object MapIterator {
    private final val INDEX_ILLEGAL: scala.Int = -2
    final val INDEX_ZERO: scala.Int = -1
  }
  class Entries[V <: java.lang.Object](map$p: LongMap[?]) extends com.badlogic.gdx.utils.LongMap.MapIterator[V](map$p.asInstanceOf[LongMap[java.lang.Object]]) with balticporter.runtime.JavaIterable[com.badlogic.gdx.utils.LongMap.Entry[V]] with balticporter.runtime.JavaIterator[com.badlogic.gdx.utils.LongMap.Entry[V]] {
    private final val entry: com.badlogic.gdx.utils.LongMap.Entry[V] = new com.badlogic.gdx.utils.LongMap.Entry[V]().asInstanceOf[com.badlogic.gdx.utils.LongMap.Entry[V]]
    override def next(): com.badlogic.gdx.utils.LongMap.Entry[V] = {
      if (!hasNext$field) {
        throw new java.util.NoSuchElementException()
      } else ()
      if (!valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      val keyTable: scala.Array[scala.Long] = this.map.keyTable
      if (nextIndex == com.badlogic.gdx.utils.LongMap.MapIterator.INDEX_ZERO) {
        this.entry.key = 0
        this.entry.value = this.map.zeroValue
      } else {
        this.entry.key = keyTable(nextIndex)
        this.entry.value = this.map.valueTable(nextIndex)
      }
      currentIndex = nextIndex
      this.findNextIndex()
      return this.entry
    }
    override def hasNext(): scala.Boolean = {
      if (!valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      return hasNext$field
    }
    override def iterator(): balticporter.runtime.JavaIterator[com.badlogic.gdx.utils.LongMap.Entry[V]] = {
      return this
    }
  }
  object Entries {
    export com.badlogic.gdx.utils.LongMap.MapIterator.*
  }
  class Values[V <: java.lang.Object](map$p: LongMap[V]) extends com.badlogic.gdx.utils.LongMap.MapIterator[V](map$p) with balticporter.runtime.JavaIterable[V] with balticporter.runtime.JavaIterator[V] {
    override def hasNext(): scala.Boolean = {
      if (!valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      return hasNext$field
    }
    @com.badlogic.gdx.utils.Null
    override def next(): V = {
      if (!hasNext$field) {
        throw new java.util.NoSuchElementException()
      } else ()
      if (!valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      var value: V = null.asInstanceOf[V]
      if (nextIndex == com.badlogic.gdx.utils.LongMap.MapIterator.INDEX_ZERO) {
        value = this.map.zeroValue
      } else {
        value = this.map.valueTable(nextIndex)
      }
      currentIndex = nextIndex
      this.findNextIndex()
      return value
    }
    override def iterator(): balticporter.runtime.JavaIterator[V] = {
      return this
    }
    def toArray(): com.badlogic.gdx.utils.Array[V] = {
      val array: com.badlogic.gdx.utils.Array[?] = new com.badlogic.gdx.utils.Array(true, this.map.size).asInstanceOf[com.badlogic.gdx.utils.Array[?]]
      while (hasNext$field) {
        array.asInstanceOf[com.badlogic.gdx.utils.Array[java.lang.Object]].add(this.next().asInstanceOf[java.lang.Object])
      }
      return array.asInstanceOf[com.badlogic.gdx.utils.Array[V]]
    }
  }
  object Values {
    export com.badlogic.gdx.utils.LongMap.MapIterator.*
  }
  class Keys(map$p: LongMap[?]) extends com.badlogic.gdx.utils.LongMap.MapIterator[java.lang.Object](map$p.asInstanceOf[LongMap[java.lang.Object]]) {
    def next(): scala.Long = {
      if (!hasNext$field) {
        throw new java.util.NoSuchElementException()
      } else ()
      if (!valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      val key: scala.Long = if (nextIndex == com.badlogic.gdx.utils.LongMap.MapIterator.INDEX_ZERO) 0 else this.map.keyTable(nextIndex)
      currentIndex = nextIndex
      this.findNextIndex()
      return key
    }
    def toArray(): com.badlogic.gdx.utils.LongArray = {
      val array: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(true, this.map.size)
      while (hasNext$field) {
        array.add(this.next())
      }
      return array
    }
    def toArray(array: com.badlogic.gdx.utils.LongArray): com.badlogic.gdx.utils.LongArray = {
      while (hasNext$field) {
        array.add(this.next())
      }
      return array
    }
  }
  object Keys {
    export com.badlogic.gdx.utils.LongMap.MapIterator.*
  }
}