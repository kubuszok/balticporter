package com.badlogic.gdx.utils

class Queue[T](initialSize: scala.Int, arraySupplier: com.badlogic.gdx.utils.ArraySupplier[scala.Array[T]]) extends balticporter.runtime.JavaIterable[T] {
  var values: scala.Array[T] = null.asInstanceOf[scala.Array[T]]
  var head: scala.Int = 0
  var tail: scala.Int = 0
  var size: scala.Int = 0
  private var iterable: com.badlogic.gdx.utils.Queue.QueueIterable[T] = null.asInstanceOf[com.badlogic.gdx.utils.Queue.QueueIterable[T]]
  def this(initialSize: scala.Int) = {
    this(initialSize, com.badlogic.gdx.utils.ArraySupplier.`object`())
  }
  def this() = {
    this(16)
  }
  this.values = arraySupplier.get(initialSize).asInstanceOf[scala.Array[T]]
  def addLast(`object`: T): scala.Unit = {
    var values: scala.Array[T] = this.values
    if (this.size == values.length) {
      this.resize(values.length << 1)
      values = this.values
    } else ()
    values({ this.tail += 1; this.tail }) = `object`
    if (this.tail == values.length) {
      this.tail = 0
    } else ()
    this.size = this.size + 1
  }
  def addFirst(`object`: T): scala.Unit = {
    var values: scala.Array[T] = this.values
    if (this.size == values.length) {
      this.resize(values.length << 1)
      values = this.values
    } else ()
    var head: scala.Int = this.head
    head = head - 1
    if (head == (-1)) {
      head = values.length - 1
    } else ()
    values(head) = `object`
    this.head = head
    this.size = this.size + 1
  }
  def ensureCapacity(additional: scala.Int): scala.Unit = {
    val needed: scala.Int = this.size + additional
    if (this.values.length < needed) {
      this.resize(needed)
    } else ()
  }
  def resize(newSize: scala.Int): scala.Unit = {
    var values: scala.Array[T] = this.values
    var head: scala.Int = this.head
    var tail: scala.Int = this.tail
    val newArray: scala.Array[T] = java.util.Arrays.copyOf(values.asInstanceOf[scala.Array[java.lang.Object]], newSize).asInstanceOf[scala.Array[T]]
    if (head < tail) {
      java.lang.System.arraycopy(values, head, newArray, 0, tail - head)
    } else {
      if (this.size > 0) {
        val rest: scala.Int = values.length - head
        java.lang.System.arraycopy(values, head, newArray, 0, rest)
        java.lang.System.arraycopy(values, 0, newArray, rest, tail)
      } else ()
    }
    this.values = newArray
    this.head = 0
    this.tail = this.size
  }
  def removeFirst(): T = {
    if (this.size == 0) {
      throw new java.util.NoSuchElementException("Queue is empty.")
    } else ()
    val values: scala.Array[T] = this.values
    val result: T = values(this.head)
    values(this.head) = null.asInstanceOf[T]
    this.head = this.head + 1
    if (this.head == values.length) {
      this.head = 0
    } else ()
    this.size = this.size - 1
    return result
  }
  def removeLast(): T = {
    if (this.size == 0) {
      throw new java.util.NoSuchElementException("Queue is empty.")
    } else ()
    val values: scala.Array[T] = this.values
    var tail: scala.Int = this.tail
    tail = tail - 1
    if (tail == (-1)) {
      tail = values.length - 1
    } else ()
    val result: T = values(tail)
    values(tail) = null.asInstanceOf[T]
    this.tail = tail
    this.size = this.size - 1
    return result
  }
  def indexOf(value: T, identity: scala.Boolean): scala.Int = {
    if (this.size == 0) {
      return -1
    } else ()
    val values: scala.Array[T] = this.values
    val head: scala.Int = this.head
    val tail: scala.Int = this.tail
    if (identity || (value == null)) {
      if (head < tail) {
        { var i: scala.Int = head; while (i < tail) { {
          if (values(i) == value) {
            return i - head
          } else ()
        }; i = i + 1 } }
      } else {
        { var i: scala.Int = head; val n: scala.Int = values.length; while (i < n) { {
          if (values(i) == value) {
            return i - head
          } else ()
        }; i = i + 1 } };
        { var i: scala.Int = 0; while (i < tail) { {
          if (values(i) == value) {
            return (i + values.length) - head
          } else ()
        }; i = i + 1 } }
      }
    } else {
      if (head < tail) {
        { var i: scala.Int = head; while (i < tail) { {
          if (value.equals(values(i).asInstanceOf[java.lang.Object])) {
            return i - head
          } else ()
        }; i = i + 1 } }
      } else {
        { var i: scala.Int = head; val n: scala.Int = values.length; while (i < n) { {
          if (value.equals(values(i).asInstanceOf[java.lang.Object])) {
            return i - head
          } else ()
        }; i = i + 1 } };
        { var i: scala.Int = 0; while (i < tail) { {
          if (value.equals(values(i).asInstanceOf[java.lang.Object])) {
            return (i + values.length) - head
          } else ()
        }; i = i + 1 } }
      }
    }
    return -1
  }
  def removeValue(value: T, identity: scala.Boolean): scala.Boolean = {
    val index: scala.Int = this.indexOf(value, identity)
    if (index == (-1)) {
      return false
    } else ()
    this.removeIndex(index)
    return true
  }
  def removeIndex(index$arg: scala.Int): T = {
    var index: scala.Int = index$arg
    if (index < 0) {
      throw new java.lang.IndexOutOfBoundsException("index can't be < 0: " + index)
    } else ()
    if (index >= this.size) {
      throw new java.lang.IndexOutOfBoundsException((("index can't be >= size: " + index) + " >= ") + this.size)
    } else ()
    val values: scala.Array[T] = this.values
    var head: scala.Int = this.head
    var tail: scala.Int = this.tail
    index = index + head
    var value: T = null.asInstanceOf[T]
    if (head < tail) {
      value = values(index)
      java.lang.System.arraycopy(values, index + 1, values, index, tail - index)
      values(tail) = null.asInstanceOf[T]
      this.tail = this.tail - 1
    } else {
      if (index >= values.length) {
        index = index - values.length
        value = values(index)
        java.lang.System.arraycopy(values, index + 1, values, index, tail - index)
        this.tail = this.tail - 1
      } else {
        value = values(index)
        java.lang.System.arraycopy(values, head, values, head + 1, index - head)
        values(head) = null.asInstanceOf[T]
        this.head = this.head + 1
        if (this.head == values.length) {
          this.head = 0
        } else ()
      }
    }
    this.size = this.size - 1
    return value
  }
  def notEmpty(): scala.Boolean = {
    return this.size > 0
  }
  def isEmpty(): scala.Boolean = {
    return this.size == 0
  }
  def first(): T = {
    if (this.size == 0) {
      throw new java.util.NoSuchElementException("Queue is empty.")
    } else ()
    return this.values(this.head)
  }
  def last(): T = {
    if (this.size == 0) {
      throw new java.util.NoSuchElementException("Queue is empty.")
    } else ()
    val values: scala.Array[T] = this.values
    var tail: scala.Int = this.tail
    tail = tail - 1
    if (tail == (-1)) {
      tail = values.length - 1
    } else ()
    return values(tail)
  }
  def get(index: scala.Int): T = {
    if (index < 0) {
      throw new java.lang.IndexOutOfBoundsException("index can't be < 0: " + index)
    } else ()
    if (index >= this.size) {
      throw new java.lang.IndexOutOfBoundsException((("index can't be >= size: " + index) + " >= ") + this.size)
    } else ()
    val values: scala.Array[T] = this.values
    var i: scala.Int = this.head + index
    if (i >= values.length) {
      i = i - values.length
    } else ()
    return values(i)
  }
  def clear(): scala.Unit = {
    if (this.size == 0) {
      return
    } else ()
    val values: scala.Array[T] = this.values
    var head: scala.Int = this.head
    var tail: scala.Int = this.tail
    if (head < tail) {
      { var i: scala.Int = head; while (i < tail) { {
        values(i) = null.asInstanceOf[T]
      }; i = i + 1 } }
    } else {
      { var i: scala.Int = head; while (i < values.length) { {
        values(i) = null.asInstanceOf[T]
      }; i = i + 1 } };
      { var i: scala.Int = 0; while (i < tail) { {
        values(i) = null.asInstanceOf[T]
      }; i = i + 1 } }
    }
    this.head = 0
    this.tail = 0
    this.size = 0
  }
  def iterator(): balticporter.runtime.JavaIterator[T] = {
    if (com.badlogic.gdx.utils.Collections.allocateIterators) {
      return new com.badlogic.gdx.utils.Queue.QueueIterator[T](this.asInstanceOf[Queue[T]], true).asInstanceOf[balticporter.runtime.JavaIterator[T]]
    } else ()
    if (this.iterable == null) {
      this.iterable = new com.badlogic.gdx.utils.Queue.QueueIterable[T](this.asInstanceOf[Queue[T]]).asInstanceOf[com.badlogic.gdx.utils.Queue.QueueIterable[T]]
    } else ()
    return this.iterable.iterator().asInstanceOf[balticporter.runtime.JavaIterator[T]]
  }
  def toString(): java.lang.String = {
    if (this.size == 0) {
      return "[]"
    } else ()
    val values: scala.Array[T] = this.values
    val head: scala.Int = this.head
    val tail: scala.Int = this.tail
    val sb: java.lang.StringBuilder = new java.lang.StringBuilder(64)
    sb.append('[')
    sb.append(values(head).asInstanceOf[java.lang.Object]);
    { var i: scala.Int = (head + 1) % values.length; while (i != tail) { {
      sb.append(", ").append(values(i).asInstanceOf[java.lang.Object])
    }; i = (i + 1) % values.length } }
    sb.append(']')
    return sb.toString()
  }
  def toString(separator: java.lang.String): java.lang.String = {
    if (this.size == 0) {
      return ""
    } else ()
    val values: scala.Array[T] = this.values
    val head: scala.Int = this.head
    val tail: scala.Int = this.tail
    val sb: java.lang.StringBuilder = new java.lang.StringBuilder(64)
    sb.append(values(head).asInstanceOf[java.lang.Object]);
    { var i: scala.Int = (head + 1) % values.length; while (i != tail) { {
      sb.append(separator).append(values(i).asInstanceOf[java.lang.Object])
    }; i = (i + 1) % values.length } }
    return sb.toString()
  }
  def hashCode(): scala.Int = {
    val size: scala.Int = this.size
    val values: scala.Array[T] = this.values
    val backingLength: scala.Int = values.length
    var index: scala.Int = this.head
    var hash: scala.Int = size + 1;
    { var s: scala.Int = 0; while (s < size) { {
      val value: T = values(index)
      hash = hash * 31
      if (value != null) {
        hash = hash + value.hashCode()
      } else ()
      index = index + 1
      if (index == backingLength) {
        index = 0
      } else ()
    }; s = s + 1 } }
    return hash
  }
  def equals(o: java.lang.Object): scala.Boolean = {
    if (this == o) {
      return true
    } else ()
    if ((o == null) || (!o.isInstanceOf[Queue[T]])) {
      return false
    } else ()
    val q: Queue[?] = o.asInstanceOf[Queue[?]].asInstanceOf[Queue[?]]
    val size: scala.Int = this.size
    if (q.size != size) {
      return false
    } else ()
    val myValues: scala.Array[T] = this.values
    val myBackingLength: scala.Int = myValues.length
    val itsValues: scala.Array[java.lang.Object] = q.asInstanceOf[Queue[java.lang.Object]].values.asInstanceOf[scala.Array[java.lang.Object]]
    val itsBackingLength: scala.Int = itsValues.length
    var myIndex: scala.Int = this.head
    var itsIndex: scala.Int = q.head;
    { var s: scala.Int = 0; while (s < size) { {
      val myValue: T = myValues(myIndex)
      val itsValue: java.lang.Object = itsValues(itsIndex)
      if (!(if (myValue == null) itsValue == null else myValue.equals(itsValue))) {
        return false
      } else ()
      myIndex = myIndex + 1
      itsIndex = itsIndex + 1
      if (myIndex == myBackingLength) {
        myIndex = 0
      } else ()
      if (itsIndex == itsBackingLength) {
        itsIndex = 0
      } else ()
    }; s = s + 1 } }
    return true
  }
  def equalsIdentity(o: java.lang.Object): scala.Boolean = {
    if (this == o) {
      return true
    } else ()
    if ((o == null) || (!o.isInstanceOf[Queue[T]])) {
      return false
    } else ()
    val q: Queue[?] = o.asInstanceOf[Queue[?]].asInstanceOf[Queue[?]]
    val size: scala.Int = this.size
    if (q.size != size) {
      return false
    } else ()
    val myValues: scala.Array[T] = this.values
    val myBackingLength: scala.Int = myValues.length
    val itsValues: scala.Array[java.lang.Object] = q.asInstanceOf[Queue[java.lang.Object]].values.asInstanceOf[scala.Array[java.lang.Object]]
    val itsBackingLength: scala.Int = itsValues.length
    var myIndex: scala.Int = this.head
    var itsIndex: scala.Int = q.head;
    { var s: scala.Int = 0; while (s < size) { {
      if (myValues(myIndex) != itsValues(itsIndex)) {
        return false
      } else ()
      myIndex = myIndex + 1
      itsIndex = itsIndex + 1
      if (myIndex == myBackingLength) {
        myIndex = 0
      } else ()
      if (itsIndex == itsBackingLength) {
        itsIndex = 0
      } else ()
    }; s = s + 1 } }
    return true
  }
}
object Queue {
  class QueueIterator[T](queue$p: Queue[T], allowRemove$p: scala.Boolean) extends balticporter.runtime.JavaIterator[T] with balticporter.runtime.JavaIterable[T] {
    private var queue: Queue[T] = null.asInstanceOf[Queue[T]]
    private var allowRemove: scala.Boolean = false
    var index: scala.Int = 0
    var valid: scala.Boolean = true
    def this(queue: Queue[T]) = {
      this(queue, true)
    }
    this.queue = queue$p
    this.allowRemove = allowRemove$p
    def hasNext(): scala.Boolean = {
      if (!this.valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      return this.index < this.queue.size
    }
    def next(): T = {
      if (this.index >= this.queue.size) {
        throw new java.util.NoSuchElementException(java.lang.String.valueOf(this.index))
      } else ()
      if (!this.valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      return this.queue.get({ this.index += 1; this.index }).asInstanceOf[T]
    }
    def remove(): scala.Unit = {
      if (!this.allowRemove) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Remove not allowed.")
      } else ()
      this.index = this.index - 1
      this.queue.removeIndex(this.index)
    }
    def reset(): scala.Unit = {
      this.index = 0
    }
    def iterator(): balticporter.runtime.JavaIterator[T] = {
      return this
    }
  }
  class QueueIterable[T](queue$p: Queue[T], allowRemove$p: scala.Boolean) extends balticporter.runtime.JavaIterable[T] {
    private var queue: Queue[T] = null.asInstanceOf[Queue[T]]
    private var allowRemove: scala.Boolean = false
    private var iterator1: com.badlogic.gdx.utils.Queue.QueueIterator[T] = null.asInstanceOf[com.badlogic.gdx.utils.Queue.QueueIterator[T]]
    private var iterator2: com.badlogic.gdx.utils.Queue.QueueIterator[T] = null.asInstanceOf[com.badlogic.gdx.utils.Queue.QueueIterator[T]]
    def this(queue: Queue[T]) = {
      this(queue, true)
    }
    this.queue = queue$p
    this.allowRemove = allowRemove$p
    def iterator(): balticporter.runtime.JavaIterator[T] = {
      if (com.badlogic.gdx.utils.Collections.allocateIterators) {
        return new com.badlogic.gdx.utils.Queue.QueueIterator[T](this.queue, this.allowRemove).asInstanceOf[balticporter.runtime.JavaIterator[T]]
      } else ()
      if (this.iterator1 == null) {
        this.iterator1 = new com.badlogic.gdx.utils.Queue.QueueIterator[T](this.queue, this.allowRemove).asInstanceOf[com.badlogic.gdx.utils.Queue.QueueIterator[T]]
        this.iterator2 = new com.badlogic.gdx.utils.Queue.QueueIterator[T](this.queue, this.allowRemove).asInstanceOf[com.badlogic.gdx.utils.Queue.QueueIterator[T]]
      } else ()
      if (!this.iterator1.valid) {
        this.iterator1.index = 0
        this.iterator1.valid = true
        this.iterator2.valid = false
        return this.iterator1.asInstanceOf[balticporter.runtime.JavaIterator[T]]
      } else ()
      this.iterator2.index = 0
      this.iterator2.valid = true
      this.iterator1.valid = false
      return this.iterator2.asInstanceOf[balticporter.runtime.JavaIterator[T]]
    }
  }
}