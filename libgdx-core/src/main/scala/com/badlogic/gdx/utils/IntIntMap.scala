package com.badlogic.gdx.utils

class IntIntMap(initialCapacity: scala.Int, loadFactor$p: scala.Float) extends scala.collection.Iterable[com.badlogic.gdx.utils.IntIntMap.Entry] {
  var size: scala.Int = 0
  var keyTable: scala.Array[scala.Int] = null.asInstanceOf[scala.Array[scala.Int]]
  var valueTable: scala.Array[scala.Int] = null.asInstanceOf[scala.Array[scala.Int]]
  var zeroValue: scala.Int = 0
  var hasZeroValue: scala.Boolean = false
  private var loadFactor: scala.Float = 0.0f
  private var threshold: scala.Int = 0
  var shift: scala.Int = 0
  var mask: scala.Int = 0
  private var entries1: com.badlogic.gdx.utils.IntIntMap.Entries = null.asInstanceOf[com.badlogic.gdx.utils.IntIntMap.Entries]
  private var entries2: com.badlogic.gdx.utils.IntIntMap.Entries = null.asInstanceOf[com.badlogic.gdx.utils.IntIntMap.Entries]
  private var values1: com.badlogic.gdx.utils.IntIntMap.Values = null.asInstanceOf[com.badlogic.gdx.utils.IntIntMap.Values]
  private var values2: com.badlogic.gdx.utils.IntIntMap.Values = null.asInstanceOf[com.badlogic.gdx.utils.IntIntMap.Values]
  private var keys1: com.badlogic.gdx.utils.IntIntMap.Keys = null.asInstanceOf[com.badlogic.gdx.utils.IntIntMap.Keys]
  private var keys2: com.badlogic.gdx.utils.IntIntMap.Keys = null.asInstanceOf[com.badlogic.gdx.utils.IntIntMap.Keys]
  val tableSize: scala.Int = com.badlogic.gdx.utils.ObjectSet.tableSize(initialCapacity, loadFactor$p)
  def this() = {
    this(51, 0.8f)
  }
  def this(initialCapacity: scala.Int) = {
    this(initialCapacity, 0.8f)
  }
  def this(map: IntIntMap) = {
    this((map.keyTable.length * map.loadFactor).asInstanceOf[scala.Int].asInstanceOf[scala.Int], map.loadFactor)
    java.lang.System.arraycopy(map.keyTable, 0, this.keyTable, 0, map.keyTable.length)
    java.lang.System.arraycopy(map.valueTable, 0, this.valueTable, 0, map.valueTable.length)
    this.size = map.size
    this.zeroValue = map.zeroValue
    this.hasZeroValue = map.hasZeroValue
  }
  if ((loadFactor$p <= 0.0f) || (loadFactor$p >= 1.0f)) {
    throw new java.lang.IllegalArgumentException("loadFactor must be > 0 and < 1: " + loadFactor$p)
  } else ()
  this.loadFactor = loadFactor$p
  this.threshold = (tableSize * loadFactor$p).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
  this.mask = tableSize - 1
  this.shift = java.lang.Long.numberOfLeadingZeros(this.mask)
  this.keyTable = new scala.Array[scala.Int](tableSize)
  this.valueTable = new scala.Array[scala.Int](tableSize)
  def place(item: scala.Int): scala.Int = {
    return ((item * -7046029254386353131L) >>> this.shift).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
  }
  private def locateKey(key: scala.Int): scala.Int = {
    {
      val keyTable: scala.Array[scala.Int] = this.keyTable;
      { var i: scala.Int = this.place(key); while (true) { {
        val other: scala.Int = keyTable(i)
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
  def put(key: scala.Int, value: scala.Int): scala.Unit = {
    if (key == 0) {
      this.zeroValue = value
      if (!this.hasZeroValue) {
        this.hasZeroValue = true
        this.size = this.size + 1
      } else ()
      return
    } else ()
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
  def put(key: scala.Int, value: scala.Int, defaultValue: scala.Int): scala.Int = {
    if (key == 0) {
      val oldValue: scala.Int = this.zeroValue
      this.zeroValue = value
      if (!this.hasZeroValue) {
        this.hasZeroValue = true
        this.size = this.size + 1
        return defaultValue
      } else ()
      return oldValue
    } else ()
    var i: scala.Int = this.locateKey(key)
    if (i >= 0) {
      val oldValue: scala.Int = this.valueTable(i)
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
  def putAll(map: IntIntMap): scala.Unit = {
    this.ensureCapacity(map.size)
    if (map.hasZeroValue) {
      this.put(0, map.zeroValue)
    } else ()
    val keyTable: scala.Array[scala.Int] = map.keyTable
    val valueTable: scala.Array[scala.Int] = map.valueTable;
    { var i: scala.Int = 0; val n: scala.Int = keyTable.length; while (i < n) { {
      val key: scala.Int = keyTable(i)
      if (key != 0) {
        this.put(key, valueTable(i))
      } else ()
    }; i = i + 1 } }
  }
  private def putResize(key: scala.Int, value: scala.Int): scala.Unit = {
    val keyTable: scala.Array[scala.Int] = this.keyTable;
    { var i: scala.Int = this.place(key); while (true) { {
      if (keyTable(i) == 0) {
        keyTable(i) = key
        this.valueTable(i) = value
        return
      } else ()
    }; i = (i + 1) & this.mask } }
  }
  def get(key: scala.Int, defaultValue: scala.Int): scala.Int = {
    if (key == 0) {
      return if (this.hasZeroValue) this.zeroValue else defaultValue
    } else ()
    val i: scala.Int = this.locateKey(key)
    return if (i >= 0) this.valueTable(i) else defaultValue
  }
  def getAndIncrement(key: scala.Int, defaultValue: scala.Int, increment: scala.Int): scala.Int = {
    if (key == 0) {
      if (!this.hasZeroValue) {
        this.hasZeroValue = true
        this.zeroValue = defaultValue + increment
        this.size = this.size + 1
        return defaultValue
      } else ()
      val oldValue: scala.Int = this.zeroValue
      this.zeroValue = this.zeroValue + increment
      return oldValue
    } else ()
    var i: scala.Int = this.locateKey(key)
    if (i >= 0) {
      val oldValue: scala.Int = this.valueTable(i)
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
  def remove(key$arg: scala.Int, defaultValue: scala.Int): scala.Int = {
    var key: scala.Int = key$arg
    if (key == 0) {
      if (!this.hasZeroValue) {
        return defaultValue
      } else ()
      this.hasZeroValue = false
      this.size = this.size - 1
      return this.zeroValue
    } else ()
    var i: scala.Int = this.locateKey(key)
    if (i < 0) {
      return defaultValue
    } else ()
    val keyTable: scala.Array[scala.Int] = this.keyTable
    val valueTable: scala.Array[scala.Int] = this.valueTable
    val oldValue: scala.Int = valueTable(i)
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
    this.resize(tableSize)
  }
  def clear(): scala.Unit = {
    if (this.size == 0) {
      return
    } else ()
    java.util.Arrays.fill(this.keyTable, 0)
    this.size = 0
    this.hasZeroValue = false
  }
  def containsValue(value: scala.Int): scala.Boolean = {
    if (this.hasZeroValue && (this.zeroValue == value)) {
      return true
    } else ()
    val keyTable: scala.Array[scala.Int] = this.keyTable
    val valueTable: scala.Array[scala.Int] = this.valueTable;
    { var i: scala.Int = valueTable.length - 1; while (i >= 0) { {
      if ((keyTable(i) != 0) && (valueTable(i) == value)) {
        return true
      } else ()
    }; i = i - 1 } }
    return false
  }
  def containsKey(key: scala.Int): scala.Boolean = {
    if (key == 0) {
      return this.hasZeroValue
    } else ()
    return this.locateKey(key) >= 0
  }
  def findKey(value: scala.Int, notFound: scala.Int): scala.Int = {
    if (this.hasZeroValue && (this.zeroValue == value)) {
      return 0
    } else ()
    val keyTable: scala.Array[scala.Int] = this.keyTable
    val valueTable: scala.Array[scala.Int] = this.valueTable;
    { var i: scala.Int = valueTable.length - 1; while (i >= 0) { {
      val key: scala.Int = keyTable(i)
      if ((key != 0) && (valueTable(i) == value)) {
        return key
      } else ()
    }; i = i - 1 } }
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
    val oldKeyTable: scala.Array[scala.Int] = this.keyTable
    val oldValueTable: scala.Array[scala.Int] = this.valueTable
    this.keyTable = new scala.Array[scala.Int](newSize)
    this.valueTable = new scala.Array[scala.Int](newSize)
    if (this.size > 0) {
      { var i: scala.Int = 0; while (i < oldCapacity) { {
        val key: scala.Int = oldKeyTable(i)
        if (key != 0) {
          this.putResize(key, oldValueTable(i))
        } else ()
      }; i = i + 1 } }
    } else ()
  }
  def hashCode(): scala.Int = {
    var h: scala.Int = this.size
    if (this.hasZeroValue) {
      h = h + this.zeroValue
    } else ()
    val keyTable: scala.Array[scala.Int] = this.keyTable
    val valueTable: scala.Array[scala.Int] = this.valueTable;
    { var i: scala.Int = 0; val n: scala.Int = keyTable.length; while (i < n) { {
      val key: scala.Int = keyTable(i)
      if (key != 0) {
        h = h + ((key * 31) + valueTable(i))
      } else ()
    }; i = i + 1 } }
    return h
  }
  def equals(obj: java.lang.Object): scala.Boolean = {
    if (obj == this) {
      return true
    } else ()
    if (!obj.isInstanceOf[IntIntMap]) {
      return false
    } else ()
    val other: IntIntMap = obj.asInstanceOf[IntIntMap].asInstanceOf[IntIntMap]
    if (other.size != this.size) {
      return false
    } else ()
    if (other.hasZeroValue != this.hasZeroValue) {
      return false
    } else ()
    if (this.hasZeroValue) {
      if (other.zeroValue != this.zeroValue) {
        return false
      } else ()
    } else ()
    val keyTable: scala.Array[scala.Int] = this.keyTable
    val valueTable: scala.Array[scala.Int] = this.valueTable;
    { var i: scala.Int = 0; val n: scala.Int = keyTable.length; while (i < n) { {
      val key: scala.Int = keyTable(i)
      if (key != 0) {
        val otherValue: scala.Int = other.get(key, 0)
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
  def toString(): java.lang.String = {
    if (this.size == 0) {
      return "[]"
    } else ()
    val buffer: java.lang.StringBuilder = new java.lang.StringBuilder(32)
    buffer.append('[')
    val keyTable: scala.Array[scala.Int] = this.keyTable
    val valueTable: scala.Array[scala.Int] = this.valueTable
    var i: scala.Int = keyTable.length
    if (this.hasZeroValue) {
      buffer.append("0=")
      buffer.append(this.zeroValue)
    } else {
      while ({ i -= 1; i } > 0) {
        val key: scala.Int = keyTable(i)
        if (key == 0) {
          /* continue */ ()
        } else ()
        buffer.append(key)
        buffer.append('=')
        buffer.append(valueTable(i))
        /* break */ ()
      }
    }
    while ({ i -= 1; i } > 0) {
      val key: scala.Int = keyTable(i)
      if (key == 0) {
        /* continue */ ()
      } else ()
      buffer.append(", ")
      buffer.append(key)
      buffer.append('=')
      buffer.append(valueTable(i))
    }
    buffer.append(']')
    return buffer.toString()
  }
  def iterator(): scala.collection.Iterator[com.badlogic.gdx.utils.IntIntMap.Entry] = {
    return this.entries()
  }
  def entries(): com.badlogic.gdx.utils.IntIntMap.Entries = {
    if (com.badlogic.gdx.utils.Collections.allocateIterators) {
      return new com.badlogic.gdx.utils.IntIntMap.Entries(this)
    } else ()
    if (this.entries1 == null) {
      this.entries1 = new com.badlogic.gdx.utils.IntIntMap.Entries(this)
      this.entries2 = new com.badlogic.gdx.utils.IntIntMap.Entries(this)
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
  def values(): com.badlogic.gdx.utils.IntIntMap.Values = {
    if (com.badlogic.gdx.utils.Collections.allocateIterators) {
      return new com.badlogic.gdx.utils.IntIntMap.Values(this)
    } else ()
    if (this.values1 == null) {
      this.values1 = new com.badlogic.gdx.utils.IntIntMap.Values(this)
      this.values2 = new com.badlogic.gdx.utils.IntIntMap.Values(this)
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
  def keys(): com.badlogic.gdx.utils.IntIntMap.Keys = {
    if (com.badlogic.gdx.utils.Collections.allocateIterators) {
      return new com.badlogic.gdx.utils.IntIntMap.Keys(this)
    } else ()
    if (this.keys1 == null) {
      this.keys1 = new com.badlogic.gdx.utils.IntIntMap.Keys(this)
      this.keys2 = new com.badlogic.gdx.utils.IntIntMap.Keys(this)
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
object IntIntMap {
  class Entry {
    var key: scala.Int = 0
    var value: scala.Int = 0
    def toString(): java.lang.String = {
      return (java.lang.String.valueOf(this.key) + "=") + this.value
    }
  }
  class MapIterator(map$p: IntIntMap) {
    var hasNext$field: scala.Boolean = false
    var map: IntIntMap = null.asInstanceOf[IntIntMap]
    var nextIndex: scala.Int = 0
    var currentIndex: scala.Int = 0
    var valid: scala.Boolean = true
    this.map = map$p
    this.reset()
    def reset(): scala.Unit = {
      this.currentIndex = com.badlogic.gdx.utils.IntIntMap.MapIterator.INDEX_ILLEGAL
      this.nextIndex = com.badlogic.gdx.utils.IntIntMap.MapIterator.INDEX_ZERO
      if (this.map.hasZeroValue) {
        this.hasNext$field = true
      } else {
        this.findNextIndex()
      }
    }
    def findNextIndex(): scala.Unit = {
      val keyTable: scala.Array[scala.Int] = this.map.keyTable;
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
      if ((i == com.badlogic.gdx.utils.IntIntMap.MapIterator.INDEX_ZERO) && this.map.hasZeroValue) {
        this.map.hasZeroValue = false
      } else {
        if (i < 0) {
          throw new java.lang.IllegalStateException("next must be called before remove.")
        } else {
          val keyTable: scala.Array[scala.Int] = this.map.keyTable
          val valueTable: scala.Array[scala.Int] = this.map.valueTable
          val mask: scala.Int = this.map.mask
          var next: scala.Int = (i + 1) & mask
          var key: scala.Int = 0
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
          if (i != this.currentIndex) {
            this.nextIndex = this.nextIndex - 1
          } else ()
        }
      }
      this.currentIndex = com.badlogic.gdx.utils.IntIntMap.MapIterator.INDEX_ILLEGAL
      this.map.size = this.map.size - 1
    }
  }
  object MapIterator {
    private final val INDEX_ILLEGAL: scala.Int = -2
    final val INDEX_ZERO: scala.Int = -1
  }
  class Entries(map$p: IntIntMap) extends com.badlogic.gdx.utils.IntIntMap.MapIterator(map$p) with scala.collection.Iterable[com.badlogic.gdx.utils.IntIntMap.Entry] with scala.collection.Iterator[com.badlogic.gdx.utils.IntIntMap.Entry] {
    private final val entry: com.badlogic.gdx.utils.IntIntMap.Entry = new com.badlogic.gdx.utils.IntIntMap.Entry()
    def next(): com.badlogic.gdx.utils.IntIntMap.Entry = {
      if (!hasNext$field) {
        throw new java.util.NoSuchElementException()
      } else ()
      if (!valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      val keyTable: scala.Array[scala.Int] = this.map.keyTable
      if (nextIndex == com.badlogic.gdx.utils.IntIntMap.MapIterator.INDEX_ZERO) {
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
    def hasNext(): scala.Boolean = {
      if (!valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      return hasNext$field
    }
    def iterator(): scala.collection.Iterator[com.badlogic.gdx.utils.IntIntMap.Entry] = {
      return this
    }
  }
  object Entries {
    export com.badlogic.gdx.utils.IntIntMap.MapIterator.*
  }
  class Values(map$p: IntIntMap) extends com.badlogic.gdx.utils.IntIntMap.MapIterator(map$p) {
    def hasNext(): scala.Boolean = {
      if (!valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      return hasNext$field
    }
    def next(): scala.Int = {
      if (!hasNext$field) {
        throw new java.util.NoSuchElementException()
      } else ()
      if (!valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      val value: scala.Int = if (nextIndex == com.badlogic.gdx.utils.IntIntMap.MapIterator.INDEX_ZERO) this.map.zeroValue else this.map.valueTable(nextIndex)
      currentIndex = nextIndex
      this.findNextIndex()
      return value
    }
    def iterator(): com.badlogic.gdx.utils.IntIntMap.Values = {
      return this
    }
    def toArray(): com.badlogic.gdx.utils.IntArray = {
      val array: com.badlogic.gdx.utils.IntArray = new com.badlogic.gdx.utils.IntArray(true, this.map.size)
      while (hasNext$field) {
        array.add(this.next())
      }
      return array
    }
    def toArray(array: com.badlogic.gdx.utils.IntArray): com.badlogic.gdx.utils.IntArray = {
      while (hasNext$field) {
        array.add(this.next())
      }
      return array
    }
  }
  object Values {
    export com.badlogic.gdx.utils.IntIntMap.MapIterator.*
  }
  class Keys(map$p: IntIntMap) extends com.badlogic.gdx.utils.IntIntMap.MapIterator(map$p) {
    def next(): scala.Int = {
      if (!hasNext$field) {
        throw new java.util.NoSuchElementException()
      } else ()
      if (!valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      val key: scala.Int = if (nextIndex == com.badlogic.gdx.utils.IntIntMap.MapIterator.INDEX_ZERO) 0 else this.map.keyTable(nextIndex)
      currentIndex = nextIndex
      this.findNextIndex()
      return key
    }
    def toArray(): com.badlogic.gdx.utils.IntArray = {
      val array: com.badlogic.gdx.utils.IntArray = new com.badlogic.gdx.utils.IntArray(true, this.map.size)
      while (hasNext$field) {
        array.add(this.next())
      }
      return array
    }
    def toArray(array: com.badlogic.gdx.utils.IntArray): com.badlogic.gdx.utils.IntArray = {
      while (hasNext$field) {
        array.add(this.next())
      }
      return array
    }
  }
  object Keys {
    export com.badlogic.gdx.utils.IntIntMap.MapIterator.*
  }
}