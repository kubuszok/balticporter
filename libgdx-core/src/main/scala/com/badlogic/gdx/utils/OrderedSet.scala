package com.badlogic.gdx.utils

class OrderedSet[T <: java.lang.Object] extends com.badlogic.gdx.utils.ObjectSet[T] {
  var items: com.badlogic.gdx.utils.Array[T] = null.asInstanceOf[com.badlogic.gdx.utils.Array[T]]
  var iterator1: com.badlogic.gdx.utils.OrderedSet.OrderedSetIterator[?] = null.asInstanceOf[com.badlogic.gdx.utils.OrderedSet.OrderedSetIterator[?]]
  var iterator2: com.badlogic.gdx.utils.OrderedSet.OrderedSetIterator[?] = null.asInstanceOf[com.badlogic.gdx.utils.OrderedSet.OrderedSetIterator[?]]
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
    this.keyTable = new scala.Array[java.lang.Object](tableSize).asInstanceOf[scala.Array[T]].asInstanceOf[scala.Array[T]]
    this.items = new com.badlogic.gdx.utils.Array[T](initialCapacity).asInstanceOf[com.badlogic.gdx.utils.Array[T]]
  }
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
    this.keyTable = new scala.Array[java.lang.Object](tableSize).asInstanceOf[scala.Array[T]].asInstanceOf[scala.Array[T]]
    this.items = new com.badlogic.gdx.utils.Array[T](initialCapacity).asInstanceOf[com.badlogic.gdx.utils.Array[T]]
  }
  def this(set: OrderedSet[? <: T]) = {
    this()
    if ((set.loadFactor <= 0.0f) || (set.loadFactor >= 1.0f)) {
      throw new java.lang.IllegalArgumentException("loadFactor must be > 0 and < 1: " + set.loadFactor)
    } else ()
    this.loadFactor = set.loadFactor
    val tableSize: scala.Int = com.badlogic.gdx.utils.ObjectSet.tableSize((set.keyTable.length * set.loadFactor).asInstanceOf[scala.Int].asInstanceOf[scala.Int], set.loadFactor)
    this.threshold = (tableSize * set.loadFactor).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    this.mask = tableSize - 1
    this.shift = java.lang.Long.numberOfLeadingZeros(this.mask)
    this.keyTable = new scala.Array[java.lang.Object](tableSize).asInstanceOf[scala.Array[T]].asInstanceOf[scala.Array[T]]
    java.lang.System.arraycopy(set.keyTable, 0, this.keyTable, 0, set.keyTable.length)
    this.size = set.size
    this.items = new com.badlogic.gdx.utils.Array[T](set.items).asInstanceOf[com.badlogic.gdx.utils.Array[T]]
  }
  this.items = new com.badlogic.gdx.utils.Array[T]().asInstanceOf[com.badlogic.gdx.utils.Array[T]]
  override def add(key: T): scala.Boolean = {
    if (!super.add(key)) {
      return false
    } else ()
    this.items.add(key)
    return true
  }
  def add(key: T, index: scala.Int): scala.Boolean = {
    if (!super.add(key)) {
      val oldIndex: scala.Int = this.items.indexOf(key, true)
      if (oldIndex != index) {
        this.items.insert(index, this.items.removeIndex(oldIndex))
      } else ()
      return false
    } else ()
    this.items.insert(index, key)
    return true
  }
  def addAll(set: OrderedSet[T]): scala.Unit = {
    this.ensureCapacity(set.size)
    val keys: scala.Array[T] = set.items.items;
    { var i: scala.Int = 0; val n: scala.Int = set.items.size; while (i < n) { {
      this.add(keys(i))
    }; i = i + 1 } }
  }
  override def ensureCapacity(additionalCapacity: scala.Int): scala.Unit = {
    super.ensureCapacity(additionalCapacity)
    this.items.ensureCapacity(additionalCapacity)
  }
  override def remove(key: T): scala.Boolean = {
    if (!super.remove(key)) {
      return false
    } else ()
    this.items.removeValue(key, false)
    return true
  }
  def removeIndex(index: scala.Int): T = {
    val key: T = this.items.removeIndex(index).asInstanceOf[T]
    super.remove(key)
    return key
  }
  def alter(before: T, after: T): scala.Boolean = {
    if (this.contains(after)) {
      return false
    } else ()
    if (!super.remove(before)) {
      return false
    } else ()
    super.add(after)
    this.items.set(this.items.indexOf(before, false), after)
    return true
  }
  def alterIndex(index: scala.Int, after: T): scala.Boolean = {
    if (((index < 0) || (index >= size)) || this.contains(after)) {
      return false
    } else ()
    super.remove(this.items.get(index))
    super.add(after)
    this.items.set(index, after)
    return true
  }
  override def clear(maximumCapacity: scala.Int): scala.Unit = {
    this.items.clear()
    super.clear(maximumCapacity)
  }
  override def clear(): scala.Unit = {
    this.items.clear()
    super.clear()
  }
  def orderedItems(): com.badlogic.gdx.utils.Array[T] = {
    return this.items
  }
  override def first(): T = {
    return this.items.first().asInstanceOf[T]
  }
  override def hashCode(): scala.Int = {
    var h: scala.Int = size
    val items: scala.Array[T] = this.items.items;
    { var i: scala.Int = 0; val n: scala.Int = this.items.size; while (i < n) { {
      val key: T = items(i)
      if (key != null) {
        h = h + key.hashCode()
      } else ()
    }; i = i + 1 } }
    return h
  }
  override def equals(obj: java.lang.Object): scala.Boolean = {
    if (!obj.isInstanceOf[com.badlogic.gdx.utils.ObjectSet[T]]) {
      return false
    } else ()
    val other: com.badlogic.gdx.utils.ObjectSet[T] = obj.asInstanceOf[com.badlogic.gdx.utils.ObjectSet[T]].asInstanceOf[com.badlogic.gdx.utils.ObjectSet[T]]
    if (other.size != size) {
      return false
    } else ()
    val items: scala.Array[T] = this.items.items;
    { var i: scala.Int = 0; val n: scala.Int = this.items.size; while (i < n) { {
      if ((items(i) != null) && (!other.asInstanceOf[com.badlogic.gdx.utils.ObjectSet[java.lang.Object]].contains(items(i).asInstanceOf[java.lang.Object]))) {
        return false
      } else ()
    }; i = i + 1 } }
    return true
  }
  override def iterator(): balticporter.runtime.JavaIterator[T] = {
    if (com.badlogic.gdx.utils.Collections.allocateIterators) {
      return new com.badlogic.gdx.utils.OrderedSet.OrderedSetIterator(this).asInstanceOf[com.badlogic.gdx.utils.OrderedSet.OrderedSetIterator[T]]
    } else ()
    if (this.iterator1 == null) {
      this.iterator1 = new com.badlogic.gdx.utils.OrderedSet.OrderedSetIterator(this).asInstanceOf[com.badlogic.gdx.utils.OrderedSet.OrderedSetIterator[?]]
      this.iterator2 = new com.badlogic.gdx.utils.OrderedSet.OrderedSetIterator(this).asInstanceOf[com.badlogic.gdx.utils.OrderedSet.OrderedSetIterator[?]]
    } else ()
    if (!this.iterator1.valid) {
      this.iterator1.reset()
      this.iterator1.valid = true
      this.iterator2.valid = false
      return this.iterator1.asInstanceOf[com.badlogic.gdx.utils.OrderedSet.OrderedSetIterator[T]]
    } else ()
    this.iterator2.reset()
    this.iterator2.valid = true
    this.iterator1.valid = false
    return this.iterator2.asInstanceOf[com.badlogic.gdx.utils.OrderedSet.OrderedSetIterator[T]]
  }
  override def toString(): java.lang.String = {
    if (size == 0) {
      return "{}"
    } else ()
    val items: scala.Array[T] = this.items.items
    val buffer: java.lang.StringBuilder = new java.lang.StringBuilder(32)
    buffer.append('{')
    buffer.append(items(0).asInstanceOf[java.lang.Object]);
    { var i: scala.Int = 1; while (i < size) { {
      buffer.append(", ")
      buffer.append(items(i).asInstanceOf[java.lang.Object])
    }; i = i + 1 } }
    buffer.append('}')
    return buffer.toString()
  }
  override def toString(separator: java.lang.String): java.lang.String = {
    return this.items.toString(separator)
  }
}
object OrderedSet {
  export com.badlogic.gdx.utils.ObjectSet.{OrderedSetIterator => _, `with` => _, *}
  override def `with`[T <: java.lang.Object](array: scala.Array[T]): OrderedSet[T] = {
    val set: OrderedSet[T] = new OrderedSet[T]()
    set.addAll(array)
    return set
  }
  class OrderedSetIterator[K <: java.lang.Object](set$p: OrderedSet[K]) extends com.badlogic.gdx.utils.ObjectSet.ObjectSetIterator[K](set$p) {
    private var items: com.badlogic.gdx.utils.Array[K] = null.asInstanceOf[com.badlogic.gdx.utils.Array[K]]
    this.items = set$p.items
    override def reset(): scala.Unit = {
      nextIndex = 0
      hasNext$field = this.set.size > 0
    }
    override def next(): ?E = {
      if (!hasNext$field) {
        throw new java.util.NoSuchElementException()
      } else ()
      if (!valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      val key: K = this.items.get(nextIndex).asInstanceOf[K]
      nextIndex = nextIndex + 1
      hasNext$field = nextIndex < this.set.size
      return key
    }
    override def remove(): scala.Unit = {
      if (nextIndex < 0) {
        throw new java.lang.IllegalStateException("next must be called before remove.")
      } else ()
      nextIndex = nextIndex - 1
      set.asInstanceOf[OrderedSet[?]].removeIndex(nextIndex)
    }
    override def toArray(array: com.badlogic.gdx.utils.Array[K]): com.badlogic.gdx.utils.Array[K] = {
      array.addAll(this.items.asInstanceOf[com.badlogic.gdx.utils.Array[? <: K]], nextIndex, this.items.size - nextIndex)
      nextIndex = this.items.size
      hasNext$field = false
      return array
    }
    override def toArray(): com.badlogic.gdx.utils.Array[K] = {
      return this.toArray(new com.badlogic.gdx.utils.Array(true, this.set.size - nextIndex))
    }
  }
}