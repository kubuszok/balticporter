package com.badlogic.gdx.utils

class IntSet(initialCapacity: scala.Int, loadFactor$p: scala.Float) {
  var size: scala.Int = 0
  var keyTable: scala.Array[scala.Int] = null.asInstanceOf[scala.Array[scala.Int]]
  var hasZeroValue: scala.Boolean = false
  private var loadFactor: scala.Float = 0.0f
  private var threshold: scala.Int = 0
  var shift: scala.Int = 0
  var mask: scala.Int = 0
  private var iterator1: com.badlogic.gdx.utils.IntSet.IntSetIterator = null.asInstanceOf[com.badlogic.gdx.utils.IntSet.IntSetIterator]
  private var iterator2: com.badlogic.gdx.utils.IntSet.IntSetIterator = null.asInstanceOf[com.badlogic.gdx.utils.IntSet.IntSetIterator]
  val tableSize: scala.Int = com.badlogic.gdx.utils.ObjectSet.tableSize(initialCapacity, loadFactor$p)
  def this() = {
    this(51, 0.8f)
  }
  def this(initialCapacity: scala.Int) = {
    this(initialCapacity, 0.8f)
  }
  def this(set: IntSet) = {
    this((set.keyTable.length * set.loadFactor).asInstanceOf[scala.Int].asInstanceOf[scala.Int], set.loadFactor)
    java.lang.System.arraycopy(set.keyTable, 0, this.keyTable, 0, set.keyTable.length)
    this.size = set.size
    this.hasZeroValue = set.hasZeroValue
  }
  if ((loadFactor$p <= 0.0f) || (loadFactor$p >= 1.0f)) {
    throw new java.lang.IllegalArgumentException("loadFactor must be > 0 and < 1: " + loadFactor$p)
  } else ()
  this.loadFactor = loadFactor$p
  this.threshold = (tableSize * loadFactor$p).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
  this.mask = tableSize - 1
  this.shift = java.lang.Long.numberOfLeadingZeros(this.mask)
  this.keyTable = new scala.Array[scala.Int](tableSize)
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
  def add(key: scala.Int): scala.Boolean = {
    if (key == 0) {
      if (this.hasZeroValue) {
        return false
      } else ()
      this.hasZeroValue = true
      this.size = this.size + 1
      return true
    } else ()
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
  def addAll(array: com.badlogic.gdx.utils.IntArray): scala.Unit = {
    this.addAll(array.items, 0, array.size)
  }
  def addAll(array: com.badlogic.gdx.utils.IntArray, offset: scala.Int, length: scala.Int): scala.Unit = {
    if ((offset + length) > array.size) {
      throw new java.lang.IllegalArgumentException((((("offset + length must be <= size: " + offset) + " + ") + length) + " <= ") + array.size)
    } else ()
    this.addAll(array.items, offset, length)
  }
  def addAll(array: scala.Array[scala.Int]): scala.Unit = {
    this.addAll(array, 0, array.length)
  }
  def addAll(array: scala.Array[scala.Int], offset: scala.Int, length: scala.Int): scala.Unit = {
    this.ensureCapacity(length);
    { var i: scala.Int = offset; val n: scala.Int = i + length; while (i < n) { {
      this.add(array(i))
    }; i = i + 1 } }
  }
  def addAll(set: IntSet): scala.Unit = {
    this.ensureCapacity(set.size)
    if (set.hasZeroValue) {
      this.add(0)
    } else ()
    val keyTable: scala.Array[scala.Int] = set.keyTable;
    { var i: scala.Int = 0; val n: scala.Int = keyTable.length; while (i < n) { {
      val key: scala.Int = keyTable(i)
      if (key != 0) {
        this.add(key)
      } else ()
    }; i = i + 1 } }
  }
  private def addResize(key: scala.Int): scala.Unit = {
    val keyTable: scala.Array[scala.Int] = this.keyTable;
    { var i: scala.Int = this.place(key); while (true) { {
      if (keyTable(i) == 0) {
        keyTable(i) = key
        return
      } else ()
    }; i = (i + 1) & this.mask } }
  }
  def remove(key$arg: scala.Int): scala.Boolean = {
    var key: scala.Int = key$arg
    if (key == 0) {
      if (!this.hasZeroValue) {
        return false
      } else ()
      this.hasZeroValue = false
      this.size = this.size - 1
      return true
    } else ()
    var i: scala.Int = this.locateKey(key)
    if (i < 0) {
      return false
    } else ()
    val keyTable: scala.Array[scala.Int] = this.keyTable
    val mask: scala.Int = this.mask
    var next: scala.Int = (i + 1) & mask
    while ({
      key = keyTable(next)
      key
    } != 0) {
      val placement: scala.Int = this.place(key)
      if (((next - placement) & mask) > ((i - placement) & mask)) {
        keyTable(i) = key
        i = next
      } else ()
      next = (next + 1) & mask
    }
    keyTable(i) = 0
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
    this.size = 0
    java.util.Arrays.fill(this.keyTable, 0)
    this.hasZeroValue = false
  }
  def contains(key: scala.Int): scala.Boolean = {
    if (key == 0) {
      return this.hasZeroValue
    } else ()
    return this.locateKey(key) >= 0
  }
  def first(): scala.Int = {
    if (this.hasZeroValue) {
      return 0
    } else ()
    val keyTable: scala.Array[scala.Int] = this.keyTable;
    { var i: scala.Int = 0; val n: scala.Int = keyTable.length; while (i < n) { {
      if (keyTable(i) != 0) {
        return keyTable(i)
      } else ()
    }; i = i + 1 } }
    throw new java.lang.IllegalStateException("IntSet is empty.")
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
    this.keyTable = new scala.Array[scala.Int](newSize)
    if (this.size > 0) {
      { var i: scala.Int = 0; while (i < oldCapacity) { {
        val key: scala.Int = oldKeyTable(i)
        if (key != 0) {
          this.addResize(key)
        } else ()
      }; i = i + 1 } }
    } else ()
  }
  def hashCode(): scala.Int = {
    var h: scala.Int = this.size
    val keyTable: scala.Array[scala.Int] = this.keyTable;
    { var i: scala.Int = 0; val n: scala.Int = keyTable.length; while (i < n) { {
      val key: scala.Int = keyTable(i)
      if (key != 0) {
        h = h + key
      } else ()
    }; i = i + 1 } }
    return h
  }
  def equals(obj: java.lang.Object): scala.Boolean = {
    if (!obj.isInstanceOf[IntSet]) {
      return false
    } else ()
    val other: IntSet = obj.asInstanceOf[IntSet].asInstanceOf[IntSet]
    if (other.size != this.size) {
      return false
    } else ()
    if (other.hasZeroValue != this.hasZeroValue) {
      return false
    } else ()
    val keyTable: scala.Array[scala.Int] = this.keyTable;
    { var i: scala.Int = 0; val n: scala.Int = keyTable.length; while (i < n) { {
      if ((keyTable(i) != 0) && (!other.contains(keyTable(i)))) {
        return false
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
    var i: scala.Int = keyTable.length
    if (this.hasZeroValue) {
      buffer.append("0")
    } else {
      while ({ i -= 1; i } > 0) {
        val key: scala.Int = keyTable(i)
        if (key == 0) {
          /* continue */ ()
        } else ()
        buffer.append(key)
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
    }
    buffer.append(']')
    return buffer.toString()
  }
  def iterator(): com.badlogic.gdx.utils.IntSet.IntSetIterator = {
    if (com.badlogic.gdx.utils.Collections.allocateIterators) {
      return new com.badlogic.gdx.utils.IntSet.IntSetIterator(this)
    } else ()
    if (this.iterator1 == null) {
      this.iterator1 = new com.badlogic.gdx.utils.IntSet.IntSetIterator(this)
      this.iterator2 = new com.badlogic.gdx.utils.IntSet.IntSetIterator(this)
    } else ()
    if (!this.iterator1.valid) {
      this.iterator1.reset()
      this.iterator1.valid = true
      this.iterator2.valid = false
      return this.iterator1
    } else ()
    this.iterator2.reset()
    this.iterator2.valid = true
    this.iterator1.valid = false
    return this.iterator2
  }
}
object IntSet {
  def `with`(array: scala.Array[scala.Int]): IntSet = {
    val set: IntSet = new IntSet()
    set.addAll(array)
    return set
  }
  class IntSetIterator(set$p: IntSet) {
    var hasNext: scala.Boolean = false
    var set: IntSet = null.asInstanceOf[IntSet]
    var nextIndex: scala.Int = 0
    var currentIndex: scala.Int = 0
    var valid: scala.Boolean = true
    this.set = set$p
    this.reset()
    def reset(): scala.Unit = {
      this.currentIndex = com.badlogic.gdx.utils.IntSet.IntSetIterator.INDEX_ILLEGAL
      this.nextIndex = com.badlogic.gdx.utils.IntSet.IntSetIterator.INDEX_ZERO
      if (this.set.hasZeroValue) {
        this.hasNext = true
      } else {
        this.findNextIndex()
      }
    }
    def findNextIndex(): scala.Unit = {
      val keyTable: scala.Array[scala.Int] = this.set.keyTable;
      { val n: scala.Int = keyTable.length; while ({ this.nextIndex += 1; this.nextIndex } < n) { {
        if (keyTable(this.nextIndex) != 0) {
          this.hasNext = true
          return
        } else ()
      };  } }
      this.hasNext = false
    }
    def remove(): scala.Unit = {
      var i: scala.Int = this.currentIndex
      if ((i == com.badlogic.gdx.utils.IntSet.IntSetIterator.INDEX_ZERO) && this.set.hasZeroValue) {
        this.set.hasZeroValue = false
      } else {
        if (i < 0) {
          throw new java.lang.IllegalStateException("next must be called before remove.")
        } else {
          val keyTable: scala.Array[scala.Int] = this.set.keyTable
          val mask: scala.Int = this.set.mask
          var next: scala.Int = (i + 1) & mask
          var key: scala.Int = 0
          while ({
            key = keyTable(next)
            key
          } != 0) {
            val placement: scala.Int = this.set.place(key)
            if (((next - placement) & mask) > ((i - placement) & mask)) {
              keyTable(i) = key
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
      this.currentIndex = com.badlogic.gdx.utils.IntSet.IntSetIterator.INDEX_ILLEGAL
      this.set.size = this.set.size - 1
    }
    def next(): scala.Int = {
      if (!this.hasNext) {
        throw new java.util.NoSuchElementException()
      } else ()
      if (!this.valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      val key: scala.Int = if (this.nextIndex == com.badlogic.gdx.utils.IntSet.IntSetIterator.INDEX_ZERO) 0 else this.set.keyTable(this.nextIndex)
      this.currentIndex = this.nextIndex
      this.findNextIndex()
      return key
    }
    def toArray(): com.badlogic.gdx.utils.IntArray = {
      val array: com.badlogic.gdx.utils.IntArray = new com.badlogic.gdx.utils.IntArray(true, this.set.size)
      while (this.hasNext) {
        array.add(this.next())
      }
      return array
    }
  }
  object IntSetIterator {
    private final val INDEX_ILLEGAL: scala.Int = -2
    private final val INDEX_ZERO: scala.Int = -1
  }
}