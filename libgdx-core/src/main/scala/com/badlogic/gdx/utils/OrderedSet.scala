package com.badlogic.gdx.utils

class OrderedSet[T] extends com.badlogic.gdx.utils.ObjectSet[T] {
  var items: com.badlogic.gdx.utils.Array[T] = null.asInstanceOf[com.badlogic.gdx.utils.Array[T]]
  var iterator1: com.badlogic.gdx.utils.OrderedSet.OrderedSetIterator[?] = null.asInstanceOf[com.badlogic.gdx.utils.OrderedSet.OrderedSetIterator[?]]
  var iterator2: com.badlogic.gdx.utils.OrderedSet.OrderedSetIterator[?] = null.asInstanceOf[com.badlogic.gdx.utils.OrderedSet.OrderedSetIterator[?]]
  def this(initialCapacity: scala.Int, loadFactor: scala.Float) = {
    this()
    this.items = new com.badlogic.gdx.utils.Array(initialCapacity)
  }
  def this(initialCapacity: scala.Int) = {
    this()
    this.items = new com.badlogic.gdx.utils.Array(initialCapacity)
  }
  def this(set: OrderedSet[? <: T]) = {
    this()
    this.items = new com.badlogic.gdx.utils.Array(set.items)
  }
  this.items = new com.badlogic.gdx.utils.Array()
  def add(key: T): scala.Boolean = {
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
  def ensureCapacity(additionalCapacity: scala.Int): scala.Unit = {
    super.ensureCapacity(additionalCapacity)
    this.items.ensureCapacity(additionalCapacity)
  }
  def remove(key: T): scala.Boolean = {
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
  def clear(maximumCapacity: scala.Int): scala.Unit = {
    this.items.clear()
    super.clear(maximumCapacity)
  }
  def clear(): scala.Unit = {
    this.items.clear()
    super.clear()
  }
  def orderedItems(): com.badlogic.gdx.utils.Array[T] = {
    return this.items
  }
  def first(): T = {
    return this.items.first().asInstanceOf[T]
  }
  def hashCode(): scala.Int = {
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
  def equals(obj: java.lang.Object): scala.Boolean = {
    if (!obj.isInstanceOf[com.badlogic.gdx.utils.ObjectSet[?]]) {
      return false
    } else ()
    val other: com.badlogic.gdx.utils.ObjectSet[?] = obj.asInstanceOf[com.badlogic.gdx.utils.ObjectSet[?]].asInstanceOf[com.badlogic.gdx.utils.ObjectSet[?]]
    if (other.size != size) {
      return false
    } else ()
    val items: scala.Array[T] = this.items.items;
    { var i: scala.Int = 0; val n: scala.Int = this.items.size; while (i < n) { {
      if ((items(i) != null) && (!other.contains(items(i)))) {
        return false
      } else ()
    }; i = i + 1 } }
    return true
  }
  def iterator(): com.badlogic.gdx.utils.OrderedSet.OrderedSetIterator[T] = {
    if (com.badlogic.gdx.utils.Collections.allocateIterators) {
      return new com.badlogic.gdx.utils.OrderedSet.OrderedSetIterator(this)
    } else ()
    if (this.iterator1 == null) {
      this.iterator1 = new com.badlogic.gdx.utils.OrderedSet.OrderedSetIterator(this)
      this.iterator2 = new com.badlogic.gdx.utils.OrderedSet.OrderedSetIterator(this)
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
  def toString(): java.lang.String = {
    if (size == 0) {
      return "{}"
    } else ()
    val items: scala.Array[T] = this.items.items
    val buffer: java.lang.StringBuilder = new java.lang.StringBuilder(32)
    buffer.append('{')
    buffer.append(items(0));
    { var i: scala.Int = 1; while (i < size) { {
      buffer.append(", ")
      buffer.append(items(i))
    }; i = i + 1 } }
    buffer.append('}')
    return buffer.toString()
  }
  def toString(separator: java.lang.String): java.lang.String = {
    return this.items.toString(separator)
  }
}
object OrderedSet {
  def `with`[T](array: scala.Array[T]): OrderedSet[T] = {
    val set: OrderedSet[T] = new OrderedSet[T]()
    set.addAll(array.asInstanceOf[scala.Array[java.lang.Object]])
    return set
  }
  class OrderedSetIterator[K] extends com.badlogic.gdx.utils.ObjectSet.ObjectSetIterator[K] {
    private var items: com.badlogic.gdx.utils.Array[K] = null.asInstanceOf[com.badlogic.gdx.utils.Array[K]]
    def this(set: OrderedSet[K]) = {
      this()
      this.items = set.items
    }
    def reset(): scala.Unit = {
      nextIndex = 0
      hasNext$field = this.set.size > 0
    }
    def next(): K = {
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
    def remove(): scala.Unit = {
      if (nextIndex < 0) {
        throw new java.lang.IllegalStateException("next must be called before remove.")
      } else ()
      nextIndex = nextIndex - 1
      set.asInstanceOf[OrderedSet[?]].removeIndex(nextIndex)
    }
    def toArray(array: com.badlogic.gdx.utils.Array[K]): com.badlogic.gdx.utils.Array[K] = {
      array.addAll(this.items, nextIndex, this.items.size - nextIndex)
      nextIndex = this.items.size
      hasNext$field = false
      return array
    }
    def toArray(): com.badlogic.gdx.utils.Array[K] = {
      return this.toArray(new com.badlogic.gdx.utils.Array(true, this.set.size - nextIndex))
    }
  }
}