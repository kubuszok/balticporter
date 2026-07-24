package com.badlogic.gdx.utils

class LongQueue {
  var values: scala.Array[scala.Long] = null.asInstanceOf[scala.Array[scala.Long]]
  var head: scala.Int = 0
  var tail: scala.Int = 0
  var size: scala.Int = 0
  def this(initialSize: scala.Int) = {
    this()
    this.values = new scala.Array[scala.Long](initialSize)
  }
  def addLast(value: scala.Long): scala.Unit = {
    var values: scala.Array[scala.Long] = this.values
    if (this.size == values.length) {
      this.resize(values.length << 1)
      values = this.values
    } else ()
    values({ this.tail += 1; this.tail }) = value
    if (this.tail == values.length) {
      this.tail = 0
    } else ()
    this.size = this.size + 1
  }
  def addFirst(value: scala.Long): scala.Unit = {
    var values: scala.Array[scala.Long] = this.values
    if (this.size == values.length) {
      this.resize(values.length << 1)
      values = this.values
    } else ()
    var head: scala.Int = this.head
    head = head - 1
    if (head == (-1)) {
      head = values.length - 1
    } else ()
    values(head) = value
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
    var values: scala.Array[scala.Long] = this.values
    var head: scala.Int = this.head
    var tail: scala.Int = this.tail
    val newArray: scala.Array[scala.Long] = new scala.Array[scala.Long](newSize)
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
  def removeFirst(): scala.Long = {
    if (this.size == 0) {
      throw new java.util.NoSuchElementException("Queue is empty.")
    } else ()
    val values: scala.Array[scala.Long] = this.values
    val result: scala.Long = values(this.head)
    this.head = this.head + 1
    if (this.head == values.length) {
      this.head = 0
    } else ()
    this.size = this.size - 1
    return result
  }
  def removeLast(): scala.Long = {
    if (this.size == 0) {
      throw new java.util.NoSuchElementException("Queue is empty.")
    } else ()
    val values: scala.Array[scala.Long] = this.values
    var tail: scala.Int = this.tail
    tail = tail - 1
    if (tail == (-1)) {
      tail = values.length - 1
    } else ()
    val result: scala.Long = values(tail)
    this.tail = tail
    this.size = this.size - 1
    return result
  }
  def indexOf(value: scala.Long): scala.Int = {
    if (this.size == 0) {
      return -1
    } else ()
    val values: scala.Array[scala.Long] = this.values
    val head: scala.Int = this.head
    val tail: scala.Int = this.tail
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
    return -1
  }
  def removeValue(value: scala.Long): scala.Boolean = {
    val index: scala.Int = this.indexOf(value)
    if (index == (-1)) {
      return false
    } else ()
    this.removeIndex(index)
    return true
  }
  def removeIndex(index$arg: scala.Int): scala.Long = {
    var index: scala.Int = index$arg
    if (index < 0) {
      throw new java.lang.IndexOutOfBoundsException("index can't be < 0: " + index)
    } else ()
    if (index >= this.size) {
      throw new java.lang.IndexOutOfBoundsException((("index can't be >= size: " + index) + " >= ") + this.size)
    } else ()
    val values: scala.Array[scala.Long] = this.values
    var head: scala.Int = this.head
    var tail: scala.Int = this.tail
    index = index + head
    var value: scala.Long = 0L
    if (head < tail) {
      value = values(index)
      java.lang.System.arraycopy(values, index + 1, values, index, tail - index)
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
  def first(): scala.Long = {
    if (this.size == 0) {
      throw new java.util.NoSuchElementException("Queue is empty.")
    } else ()
    return this.values(this.head)
  }
  def last(): scala.Long = {
    if (this.size == 0) {
      throw new java.util.NoSuchElementException("Queue is empty.")
    } else ()
    val values: scala.Array[scala.Long] = this.values
    var tail: scala.Int = this.tail
    tail = tail - 1
    if (tail == (-1)) {
      tail = values.length - 1
    } else ()
    return values(tail)
  }
  def get(index: scala.Int): scala.Long = {
    if (index < 0) {
      throw new java.lang.IndexOutOfBoundsException("index can't be < 0: " + index)
    } else ()
    if (index >= this.size) {
      throw new java.lang.IndexOutOfBoundsException((("index can't be >= size: " + index) + " >= ") + this.size)
    } else ()
    val values: scala.Array[scala.Long] = this.values
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
    this.head = 0
    this.tail = 0
    this.size = 0
  }
  def toString(): java.lang.String = {
    if (this.size == 0) {
      return "[]"
    } else ()
    val values: scala.Array[scala.Long] = this.values
    val head: scala.Int = this.head
    val tail: scala.Int = this.tail
    val sb: java.lang.StringBuilder = new java.lang.StringBuilder(64)
    sb.append('[')
    sb.append(values(head));
    { var i: scala.Int = (head + 1) % values.length; while (i != tail) { {
      sb.append(", ").append(values(i))
    }; i = (i + 1) % values.length } }
    sb.append(']')
    return sb.toString()
  }
  def toString(separator: java.lang.String): java.lang.String = {
    if (this.size == 0) {
      return ""
    } else ()
    val values: scala.Array[scala.Long] = this.values
    val head: scala.Int = this.head
    val tail: scala.Int = this.tail
    val sb: java.lang.StringBuilder = new java.lang.StringBuilder(64)
    sb.append(values(head));
    { var i: scala.Int = (head + 1) % values.length; while (i != tail) { {
      sb.append(separator).append(values(i))
    }; i = (i + 1) % values.length } }
    return sb.toString()
  }
  def hashCode(): scala.Int = {
    val size: scala.Int = this.size
    val values: scala.Array[scala.Long] = this.values
    val backingLength: scala.Int = values.length
    var index: scala.Int = this.head
    var hash: scala.Int = size + 1;
    { var s: scala.Int = 0; while (s < size) { {
      val value: scala.Long = values(index)
      hash = hash + ((value ^ (value >>> 32)).asInstanceOf[scala.Int] * 31)
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
    if ((o == null) || (!o.isInstanceOf[LongQueue])) {
      return false
    } else ()
    val q: LongQueue = o.asInstanceOf[LongQueue].asInstanceOf[LongQueue]
    val size: scala.Int = this.size
    if (q.size != size) {
      return false
    } else ()
    val myValues: scala.Array[scala.Long] = this.values
    val myBackingLength: scala.Int = myValues.length
    val itsValues: scala.Array[scala.Long] = q.values
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