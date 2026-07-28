package com.badlogic.gdx.utils

class ObjectSet[T <: java.lang.Object] extends balticporter.runtime.JavaIterable[T] {
  var size: scala.Int = 0
  var keyTable: scala.Array[T] = null.asInstanceOf[scala.Array[T]]
  var loadFactor: scala.Float = 0.0f
  var threshold: scala.Int = 0
  var shift: scala.Int = 0
  var mask: scala.Int = 0
  private var iterator1: com.badlogic.gdx.utils.ObjectSet.ObjectSetIterator[?] = null.asInstanceOf[com.badlogic.gdx.utils.ObjectSet.ObjectSetIterator[?]]
  private var iterator2: com.badlogic.gdx.utils.ObjectSet.ObjectSetIterator[?] = null.asInstanceOf[com.badlogic.gdx.utils.ObjectSet.ObjectSetIterator[?]]
  def this(initialCapacity: scala.Int, loadFactor: scala.Float) = {
    this()
    if ((loadFactor <= 0.0f) || (loadFactor >= 1.0f)) {
      throw new java.lang.IllegalArgumentException("loadFactor must be > 0 and < 1: " + loadFactor)
    } else ()
    this.loadFactor = loadFactor
    val tableSize: scala.Int = ObjectSet.tableSize(initialCapacity, loadFactor)
    this.threshold = (tableSize * loadFactor).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    this.mask = tableSize - 1
    this.shift = java.lang.Long.numberOfLeadingZeros(this.mask)
    this.keyTable = new scala.Array[java.lang.Object](tableSize).asInstanceOf[scala.Array[T]].asInstanceOf[scala.Array[T]]
  }
  def this(initialCapacity: scala.Int) = {
    this(initialCapacity, 0.8f)
  }
  def this(set: ObjectSet[? <: T]) = {
    this((set.keyTable.length * set.loadFactor).asInstanceOf[scala.Int].asInstanceOf[scala.Int], set.loadFactor)
    java.lang.System.arraycopy(set.keyTable, 0, this.keyTable, 0, set.keyTable.length)
    this.size = set.size
  }
  def place(item: T): scala.Int = {
    return ((item.hashCode() * -7046029254386353131L) >>> this.shift).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
  }
  def locateKey(key: T): scala.Int = {
    {
      if (key == null) {
        throw new java.lang.IllegalArgumentException("key cannot be null.")
      } else ()
      val keyTable: scala.Array[T] = this.keyTable;
      { var i: scala.Int = this.place(key); while (true) { {
        val other: T = keyTable(i)
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
  def add(key: T): scala.Boolean = {
    var i: scala.Int = this.locateKey(key)
    if (i >= 0) {
      return false
    } else ()
    i = -(i + 1)
    this.keyTable(i) = key
    if ({ this.size += 1; this.size } >= this.threshold) {
      this.resize(this.keyTable.length << 1)
    } else ()
    return true
  }
  def addAll(array: com.badlogic.gdx.utils.Array[? <: T]): scala.Unit = {
    this.addAll(array.items.asInstanceOf[scala.Array[T]], 0, array.size)
  }
  def addAll(array: com.badlogic.gdx.utils.Array[? <: T], offset: scala.Int, length: scala.Int): scala.Unit = {
    if ((offset + length) > array.size) {
      throw new java.lang.IllegalArgumentException((((("offset + length must be <= size: " + offset) + " + ") + length) + " <= ") + array.size)
    } else ()
    this.addAll(array.items.asInstanceOf[scala.Array[T]], offset, length)
  }
  def addAll(array: scala.Array[T]): scala.Boolean = {
    return this.addAll(array, 0, array.length)
  }
  def addAll(array: scala.Array[T], offset: scala.Int, length: scala.Int): scala.Boolean = {
    this.ensureCapacity(length)
    val oldSize: scala.Int = this.size;
    { var i: scala.Int = offset; val n: scala.Int = i + length; while (i < n) { {
      this.add(array(i))
    }; i = i + 1 } }
    return oldSize != this.size
  }
  def addAll(set: ObjectSet[T]): scala.Unit = {
    this.ensureCapacity(set.size)
    val keyTable: scala.Array[T] = set.keyTable;
    { var i: scala.Int = 0; val n: scala.Int = keyTable.length; while (i < n) { {
      val key: T = keyTable(i)
      if (key != null) {
        this.add(key)
      } else ()
    }; i = i + 1 } }
  }
  private def addResize(key: T): scala.Unit = {
    val keyTable: scala.Array[T] = this.keyTable;
    { var i: scala.Int = this.place(key); while (true) { {
      if (keyTable(i) == null) {
        keyTable(i) = key
        return
      } else ()
    }; i = (i + 1) & this.mask } }
  }
  def remove(key$arg: T): scala.Boolean = {
    var key: T = key$arg
    var i: scala.Int = this.locateKey(key)
    if (i < 0) {
      return false
    } else ()
    val keyTable: scala.Array[T] = this.keyTable
    val mask: scala.Int = this.mask
    var next: scala.Int = (i + 1) & mask
    while ({
      key = keyTable(next)
      key
    } != null) {
      val placement: scala.Int = this.place(key)
      if (((next - placement) & mask) > ((i - placement) & mask)) {
        keyTable(i) = key
        i = next
      } else ()
      next = (next + 1) & mask
    }
    keyTable(i) = null.asInstanceOf[T]
    this.size = this.size - 1
    return true
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
    val tableSize: scala.Int = ObjectSet.tableSize(maximumCapacity, this.loadFactor)
    if (this.keyTable.length > tableSize) {
      this.resize(tableSize)
    } else ()
  }
  def clear(maximumCapacity: scala.Int): scala.Unit = {
    val tableSize: scala.Int = ObjectSet.tableSize(maximumCapacity, this.loadFactor)
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
  def contains(key: T): scala.Boolean = {
    return this.locateKey(key) >= 0
  }
  @com.badlogic.gdx.utils.Null
  def get(key: T): T = {
    val i: scala.Int = this.locateKey(key)
    return if (i < 0) null.asInstanceOf[T] else this.keyTable(i)
  }
  def first(): T = {
    val keyTable: scala.Array[T] = this.keyTable;
    { var i: scala.Int = 0; val n: scala.Int = keyTable.length; while (i < n) { {
      if (keyTable(i) != null) {
        return keyTable(i)
      } else ()
    }; i = i + 1 } }
    throw new java.lang.IllegalStateException("ObjectSet is empty.")
  }
  def ensureCapacity(additionalCapacity: scala.Int): scala.Unit = {
    val tableSize: scala.Int = ObjectSet.tableSize(this.size + additionalCapacity, this.loadFactor)
    if (this.keyTable.length < tableSize) {
      this.resize(tableSize)
    } else ()
  }
  private def resize(newSize: scala.Int): scala.Unit = {
    val oldCapacity: scala.Int = this.keyTable.length
    this.threshold = (newSize * this.loadFactor).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    this.mask = newSize - 1
    this.shift = java.lang.Long.numberOfLeadingZeros(this.mask)
    val oldKeyTable: scala.Array[T] = this.keyTable
    this.keyTable = new scala.Array[java.lang.Object](newSize).asInstanceOf[scala.Array[T]].asInstanceOf[scala.Array[T]]
    if (this.size > 0) {
      { var i: scala.Int = 0; while (i < oldCapacity) { {
        val key: T = oldKeyTable(i)
        if (key != null) {
          this.addResize(key)
        } else ()
      }; i = i + 1 } }
    } else ()
  }
  override def hashCode(): scala.Int = {
    var h: scala.Int = this.size
    val keyTable: scala.Array[T] = this.keyTable;
    { var i: scala.Int = 0; val n: scala.Int = keyTable.length; while (i < n) { {
      val key: T = keyTable(i)
      if (key != null) {
        h = h + key.hashCode()
      } else ()
    }; i = i + 1 } }
    return h
  }
  override def equals(obj: java.lang.Object): scala.Boolean = {
    if (!obj.isInstanceOf[ObjectSet[?]]) {
      return false
    } else ()
    val other: ObjectSet[?] = obj.asInstanceOf[ObjectSet[?]].asInstanceOf[ObjectSet[?]]
    if (other.size != this.size) {
      return false
    } else ()
    val keyTable: scala.Array[T] = this.keyTable;
    { var i: scala.Int = 0; val n: scala.Int = keyTable.length; while (i < n) { {
      if ((keyTable(i) != null) && (!other.asInstanceOf[ObjectSet[java.lang.Object]].contains(keyTable(i).asInstanceOf[java.lang.Object]))) {
        return false
      } else ()
    }; i = i + 1 } }
    return true
  }
  override def toString(): java.lang.String = {
    return (java.lang.String.valueOf('{') + this.toString(", ")) + '}'
  }
  def toString(separator: java.lang.String): java.lang.String = {
    if (this.size == 0) {
      return ""
    } else ()
    val buffer: java.lang.StringBuilder = new java.lang.StringBuilder(32)
    val keyTable: scala.Array[T] = this.keyTable
    var i: scala.Int = keyTable.length
    while ({ i -= 1; i } > 0) {
      val key: T = keyTable(i)
      if (key == null) {
        /* continue */ ()
      } else ()
      buffer.append(if (key == this) "(this)" else key)
      /* break */ ()
    }
    while ({ i -= 1; i } > 0) {
      val key: T = keyTable(i)
      if (key == null) {
        /* continue */ ()
      } else ()
      buffer.append(separator)
      buffer.append(if (key == this) "(this)" else key)
    }
    return buffer.toString()
  }
  override def iterator(): com.badlogic.gdx.utils.ObjectSet.ObjectSetIterator[T] = {
    if (com.badlogic.gdx.utils.Collections.allocateIterators) {
      return new com.badlogic.gdx.utils.ObjectSet.ObjectSetIterator(this).asInstanceOf[com.badlogic.gdx.utils.ObjectSet.ObjectSetIterator[T]]
    } else ()
    if (this.iterator1 == null) {
      this.iterator1 = new com.badlogic.gdx.utils.ObjectSet.ObjectSetIterator(this).asInstanceOf[com.badlogic.gdx.utils.ObjectSet.ObjectSetIterator[?]]
      this.iterator2 = new com.badlogic.gdx.utils.ObjectSet.ObjectSetIterator(this).asInstanceOf[com.badlogic.gdx.utils.ObjectSet.ObjectSetIterator[?]]
    } else ()
    if (!this.iterator1.valid) {
      this.iterator1.reset()
      this.iterator1.valid = true
      this.iterator2.valid = false
      return this.iterator1.asInstanceOf[com.badlogic.gdx.utils.ObjectSet.ObjectSetIterator[T]]
    } else ()
    this.iterator2.reset()
    this.iterator2.valid = true
    this.iterator1.valid = false
    return this.iterator2.asInstanceOf[com.badlogic.gdx.utils.ObjectSet.ObjectSetIterator[T]]
  }
}
object ObjectSet {
  def `with`[T <: java.lang.Object](array: scala.Array[T]): ObjectSet[T] = {
    val set: ObjectSet[T] = new ObjectSet[T]()
    set.addAll(array)
    return set
  }
  def tableSize(capacity: scala.Int, loadFactor: scala.Float): scala.Int = {
    if (capacity < 0) {
      throw new java.lang.IllegalArgumentException("capacity must be >= 0: " + capacity)
    } else ()
    val tableSize: scala.Int = com.badlogic.gdx.math.MathUtils.nextPowerOfTwo(java.lang.Math.max(2, java.lang.Math.ceil(capacity / loadFactor).asInstanceOf[scala.Int].asInstanceOf[scala.Int]))
    if (tableSize > (1 << 30)) {
      throw new java.lang.IllegalArgumentException("The required capacity is too large: " + capacity)
    } else ()
    return tableSize
  }
  class ObjectSetIterator[K <: java.lang.Object](set$p: ObjectSet[K]) extends balticporter.runtime.JavaIterable[K] with balticporter.runtime.JavaIterator[K] {
    var hasNext$field: scala.Boolean = false
    var set: ObjectSet[K] = null.asInstanceOf[ObjectSet[K]]
    var nextIndex: scala.Int = 0
    var currentIndex: scala.Int = 0
    var valid: scala.Boolean = true
    this.set = set$p
    this.reset()
    def reset(): scala.Unit = {
      this.currentIndex = -1
      this.nextIndex = -1
      this.findNextIndex()
    }
    private def findNextIndex(): scala.Unit = {
      val keyTable: scala.Array[K] = this.set.keyTable;
      { val n: scala.Int = this.set.keyTable.length; while ({ this.nextIndex += 1; this.nextIndex } < n) { {
        if (keyTable(this.nextIndex) != null) {
          this.hasNext$field = true
          return
        } else ()
      };  } }
      this.hasNext$field = false
    }
    override def remove(): scala.Unit = {
      var i: scala.Int = this.currentIndex
      if (i < 0) {
        throw new java.lang.IllegalStateException("next must be called before remove.")
      } else ()
      val keyTable: scala.Array[K] = this.set.keyTable
      val mask: scala.Int = this.set.mask
      var next: scala.Int = (i + 1) & mask
      var key: K = null.asInstanceOf[K]
      while ({
        key = keyTable(next)
        key
      } != null) {
        val placement: scala.Int = this.set.place(key)
        if (((next - placement) & mask) > ((i - placement) & mask)) {
          keyTable(i) = key
          i = next
        } else ()
        next = (next + 1) & mask
      }
      keyTable(i) = null.asInstanceOf[K]
      this.set.size = this.set.size - 1
      if (i != this.currentIndex) {
        this.nextIndex = this.nextIndex - 1
      } else ()
      this.currentIndex = -1
    }
    override def hasNext(): scala.Boolean = {
      if (!this.valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      return this.hasNext$field
    }
    override def next(): K = {
      if (!this.hasNext$field) {
        throw new java.util.NoSuchElementException()
      } else ()
      if (!this.valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      val key: K = this.set.keyTable(this.nextIndex)
      this.currentIndex = this.nextIndex
      this.findNextIndex()
      return key
    }
    override def iterator(): com.badlogic.gdx.utils.ObjectSet.ObjectSetIterator[K] = {
      return this
    }
    def toArray(array: com.badlogic.gdx.utils.Array[K]): com.badlogic.gdx.utils.Array[K] = {
      while (this.hasNext$field) {
        array.add(this.next())
      }
      return array
    }
    def toArray(): com.badlogic.gdx.utils.Array[K] = {
      return this.toArray(new com.badlogic.gdx.utils.Array[K](true, this.set.size))
    }
  }
}