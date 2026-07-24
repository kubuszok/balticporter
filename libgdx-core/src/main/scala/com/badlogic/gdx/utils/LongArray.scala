package com.badlogic.gdx.utils

class LongArray {
  var items: scala.Array[scala.Long] = null.asInstanceOf[scala.Array[scala.Long]]
  var size: scala.Int = 0
  var ordered: scala.Boolean = false
  def this(ordered: scala.Boolean, capacity: scala.Int) = {
    this()
    this.ordered = ordered
    this.items = new scala.Array[scala.Long](capacity)
  }
  def this(array: LongArray) = {
    this()
    this.ordered = array.ordered
    this.size = array.size
    this.items = new scala.Array[scala.Long](this.size)
    java.lang.System.arraycopy(array.items, 0, this.items, 0, this.size)
  }
  def this(ordered: scala.Boolean, array: scala.Array[scala.Long], startIndex: scala.Int, count: scala.Int) = {
    this(ordered, count)
    this.size = count
    java.lang.System.arraycopy(array, startIndex, this.items, 0, count)
  }
  def this(capacity: scala.Int) = {
    this(true, capacity)
  }
  def this(array: scala.Array[scala.Long]) = {
    this(true, array, 0, array.length)
  }
  def add(value: scala.Long): scala.Unit = {
    var items: scala.Array[scala.Long] = this.items
    if (this.size == items.length) {
      items = this.resize(java.lang.Math.max(8, (this.size * 1.75f).asInstanceOf[scala.Int]))
    } else ()
    items({ this.size += 1; this.size }) = value
  }
  def add(value1: scala.Long, value2: scala.Long): scala.Unit = {
    var items: scala.Array[scala.Long] = this.items
    if ((this.size + 1) >= items.length) {
      items = this.resize(java.lang.Math.max(8, (this.size * 1.75f).asInstanceOf[scala.Int]))
    } else ()
    items(this.size) = value1
    items(this.size + 1) = value2
    this.size = this.size + 2
  }
  def add(value1: scala.Long, value2: scala.Long, value3: scala.Long): scala.Unit = {
    var items: scala.Array[scala.Long] = this.items
    if ((this.size + 2) >= items.length) {
      items = this.resize(java.lang.Math.max(8, (this.size * 1.75f).asInstanceOf[scala.Int]))
    } else ()
    items(this.size) = value1
    items(this.size + 1) = value2
    items(this.size + 2) = value3
    this.size = this.size + 3
  }
  def add(value1: scala.Long, value2: scala.Long, value3: scala.Long, value4: scala.Long): scala.Unit = {
    var items: scala.Array[scala.Long] = this.items
    if ((this.size + 3) >= items.length) {
      items = this.resize(java.lang.Math.max(8, (this.size * 1.8f).asInstanceOf[scala.Int]))
    } else ()
    items(this.size) = value1
    items(this.size + 1) = value2
    items(this.size + 2) = value3
    items(this.size + 3) = value4
    this.size = this.size + 4
  }
  def addAll(array: LongArray): scala.Unit = {
    this.addAll(array.items, 0, array.size)
  }
  def addAll(array: LongArray, offset: scala.Int, length: scala.Int): scala.Unit = {
    if ((offset + length) > array.size) {
      throw new java.lang.IllegalArgumentException((((("offset + length must be <= size: " + offset) + " + ") + length) + " <= ") + array.size)
    } else ()
    this.addAll(array.items, offset, length)
  }
  def addAll(array: scala.Array[scala.Long]): scala.Unit = {
    this.addAll(array, 0, array.length)
  }
  def addAll(array: scala.Array[scala.Long], offset: scala.Int, length: scala.Int): scala.Unit = {
    var items: scala.Array[scala.Long] = this.items
    val sizeNeeded: scala.Int = this.size + length
    if (sizeNeeded > items.length) {
      items = this.resize(java.lang.Math.max(java.lang.Math.max(8, sizeNeeded), (this.size * 1.75f).asInstanceOf[scala.Int]))
    } else ()
    java.lang.System.arraycopy(array, offset, items, this.size, length)
    this.size = this.size + length
  }
  def get(index: scala.Int): scala.Long = {
    if (index >= this.size) {
      throw new java.lang.IndexOutOfBoundsException((("index can't be >= size: " + index) + " >= ") + this.size)
    } else ()
    return this.items(index)
  }
  def set(index: scala.Int, value: scala.Long): scala.Unit = {
    if (index >= this.size) {
      throw new java.lang.IndexOutOfBoundsException((("index can't be >= size: " + index) + " >= ") + this.size)
    } else ()
    this.items(index) = value
  }
  def incr(index: scala.Int, value: scala.Long): scala.Unit = {
    if (index >= this.size) {
      throw new java.lang.IndexOutOfBoundsException((("index can't be >= size: " + index) + " >= ") + this.size)
    } else ()
    this.items(index) = this.items(index) + value
  }
  def incr(value: scala.Long): scala.Unit = {
    val items: scala.Array[scala.Long] = this.items;
    { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
      items(i) = items(i) + value
    }; i = i + 1 } }
  }
  def mul(index: scala.Int, value: scala.Long): scala.Unit = {
    if (index >= this.size) {
      throw new java.lang.IndexOutOfBoundsException((("index can't be >= size: " + index) + " >= ") + this.size)
    } else ()
    this.items(index) = this.items(index) * value
  }
  def mul(value: scala.Long): scala.Unit = {
    val items: scala.Array[scala.Long] = this.items;
    { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
      items(i) = items(i) * value
    }; i = i + 1 } }
  }
  def insert(index: scala.Int, value: scala.Long): scala.Unit = {
    if (index > this.size) {
      throw new java.lang.IndexOutOfBoundsException((("index can't be > size: " + index) + " > ") + this.size)
    } else ()
    var items: scala.Array[scala.Long] = this.items
    if (this.size == items.length) {
      items = this.resize(java.lang.Math.max(8, (this.size * 1.75f).asInstanceOf[scala.Int]))
    } else ()
    if (this.ordered) {
      java.lang.System.arraycopy(items, index, items, index + 1, this.size - index)
    } else {
      items(this.size) = items(index)
    }
    this.size = this.size + 1
    items(index) = value
  }
  def insertRange(index: scala.Int, count: scala.Int): scala.Unit = {
    if (index > this.size) {
      throw new java.lang.IndexOutOfBoundsException((("index can't be > size: " + index) + " > ") + this.size)
    } else ()
    val sizeNeeded: scala.Int = this.size + count
    if (sizeNeeded > this.items.length) {
      this.items = this.resize(java.lang.Math.max(java.lang.Math.max(8, sizeNeeded), (this.size * 1.75f).asInstanceOf[scala.Int]))
    } else ()
    java.lang.System.arraycopy(this.items, index, this.items, index + count, this.size - index)
    this.size = sizeNeeded
  }
  def swap(first: scala.Int, second: scala.Int): scala.Unit = {
    if (first >= this.size) {
      throw new java.lang.IndexOutOfBoundsException((("first can't be >= size: " + first) + " >= ") + this.size)
    } else ()
    if (second >= this.size) {
      throw new java.lang.IndexOutOfBoundsException((("second can't be >= size: " + second) + " >= ") + this.size)
    } else ()
    val items: scala.Array[scala.Long] = this.items
    val firstValue: scala.Long = items(first)
    items(first) = items(second)
    items(second) = firstValue
  }
  def replaceFirst(value: scala.Long, replacement: scala.Long): scala.Boolean = {
    val items: scala.Array[scala.Long] = this.items;
    { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
      if (items(i) == value) {
        items(i) = replacement
        return true
      } else ()
    }; i = i + 1 } }
    return false
  }
  def replaceAll(value: scala.Long, replacement: scala.Long): scala.Int = {
    val items: scala.Array[scala.Long] = this.items
    var replacements: scala.Int = 0;
    { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
      if (items(i) == value) {
        items(i) = replacement
        replacements = replacements + 1
      } else ()
    }; i = i + 1 } }
    return replacements
  }
  def contains(value: scala.Long): scala.Boolean = {
    var i: scala.Int = this.size - 1
    val items: scala.Array[scala.Long] = this.items
    while (i >= 0) {
      if (items({ i -= 1; i }) == value) {
        return true
      } else ()
    }
    return false
  }
  def indexOf(value: scala.Long): scala.Int = {
    val items: scala.Array[scala.Long] = this.items;
    { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
      if (items(i) == value) {
        return i
      } else ()
    }; i = i + 1 } }
    return -1
  }
  def lastIndexOf(value: scala.Long): scala.Int = {
    val items: scala.Array[scala.Long] = this.items;
    { var i: scala.Int = this.size - 1; while (i >= 0) { {
      if (items(i) == value) {
        return i
      } else ()
    }; i = i - 1 } }
    return -1
  }
  def removeValue(value: scala.Long): scala.Boolean = {
    val items: scala.Array[scala.Long] = this.items;
    { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
      if (items(i) == value) {
        this.removeIndex(i)
        return true
      } else ()
    }; i = i + 1 } }
    return false
  }
  def removeIndex(index: scala.Int): scala.Long = {
    if (index >= this.size) {
      throw new java.lang.IndexOutOfBoundsException((("index can't be >= size: " + index) + " >= ") + this.size)
    } else ()
    val items: scala.Array[scala.Long] = this.items
    val value: scala.Long = items(index)
    this.size = this.size - 1
    if (this.ordered) {
      java.lang.System.arraycopy(items, index + 1, items, index, this.size - index)
    } else {
      items(index) = items(this.size)
    }
    return value
  }
  def removeRange(start: scala.Int, `end`: scala.Int): scala.Unit = {
    val n: scala.Int = this.size
    if (`end` >= n) {
      throw new java.lang.IndexOutOfBoundsException((("end can't be >= size: " + `end`) + " >= ") + this.size)
    } else ()
    if (start > `end`) {
      throw new java.lang.IndexOutOfBoundsException((("start can't be > end: " + start) + " > ") + `end`)
    } else ()
    val count: scala.Int = (`end` - start) + 1
    val lastIndex: scala.Int = n - count
    if (this.ordered) {
      java.lang.System.arraycopy(this.items, start + count, this.items, start, n - (start + count))
    } else {
      val i: scala.Int = java.lang.Math.max(lastIndex, `end` + 1)
      java.lang.System.arraycopy(this.items, i, this.items, start, n - i)
    }
    this.size = n - count
  }
  def removeAll(array: LongArray): scala.Boolean = {
    var size: scala.Int = this.size
    val startSize: scala.Int = size
    val items: scala.Array[scala.Long] = this.items;
    { var i: scala.Int = 0; val n: scala.Int = array.size; while (i < n) { {
      val item: scala.Long = array.get(i);
      { var ii: scala.Int = 0; while (ii < size) { {
        if (item == items(ii)) {
          this.removeIndex(ii)
          size = size - 1
          /* break */ ()
        } else ()
      }; ii = ii + 1 } }
    }; i = i + 1 } }
    return size != startSize
  }
  def pop(): scala.Long = {
    if (this.size <= 0) {
      throw new java.lang.IllegalStateException("Array is empty.")
    } else ()
    return this.items({ this.size -= 1; this.size })
  }
  def peek(): scala.Long = {
    if (this.size <= 0) {
      throw new java.lang.IllegalStateException("Array is empty.")
    } else ()
    return this.items(this.size - 1)
  }
  def first(): scala.Long = {
    if (this.size == 0) {
      throw new java.lang.IllegalStateException("Array is empty.")
    } else ()
    return this.items(0)
  }
  def notEmpty(): scala.Boolean = {
    return this.size > 0
  }
  def isEmpty(): scala.Boolean = {
    return this.size == 0
  }
  def clear(): scala.Unit = {
    this.size = 0
  }
  def shrink(): scala.Array[scala.Long] = {
    if (this.items.length != this.size) {
      this.resize(this.size)
    } else ()
    return this.items
  }
  def ensureCapacity(additionalCapacity: scala.Int): scala.Array[scala.Long] = {
    if (additionalCapacity < 0) {
      throw new java.lang.IllegalArgumentException("additionalCapacity must be >= 0: " + additionalCapacity)
    } else ()
    val sizeNeeded: scala.Int = this.size + additionalCapacity
    if (sizeNeeded > this.items.length) {
      this.resize(java.lang.Math.max(java.lang.Math.max(8, sizeNeeded), (this.size * 1.75f).asInstanceOf[scala.Int]))
    } else ()
    return this.items
  }
  def setSize(newSize: scala.Int): scala.Array[scala.Long] = {
    if (newSize < 0) {
      throw new java.lang.IllegalArgumentException("newSize must be >= 0: " + newSize)
    } else ()
    if (newSize > this.items.length) {
      this.resize(java.lang.Math.max(8, newSize))
    } else ()
    this.size = newSize
    return this.items
  }
  def resize(newSize: scala.Int): scala.Array[scala.Long] = {
    val newItems: scala.Array[scala.Long] = new scala.Array[scala.Long](newSize)
    var items: scala.Array[scala.Long] = this.items
    java.lang.System.arraycopy(items, 0, newItems, 0, java.lang.Math.min(this.size, newItems.length))
    this.items = newItems
    return newItems
  }
  def sort(): scala.Unit = {
    java.util.Arrays.sort(this.items, 0, this.size)
  }
  def reverse(): scala.Unit = {
    val items: scala.Array[scala.Long] = this.items;
    { var i: scala.Int = 0; val lastIndex: scala.Int = this.size - 1; val n: scala.Int = this.size / 2; while (i < n) { {
      val ii: scala.Int = lastIndex - i
      val temp: scala.Long = items(i)
      items(i) = items(ii)
      items(ii) = temp
    }; i = i + 1 } }
  }
  def shuffle(): scala.Unit = {
    val items: scala.Array[scala.Long] = this.items;
    { var i: scala.Int = this.size - 1; while (i >= 0) { {
      val ii: scala.Int = com.badlogic.gdx.math.MathUtils.random(i)
      val temp: scala.Long = items(i)
      items(i) = items(ii)
      items(ii) = temp
    }; i = i - 1 } }
  }
  def truncate(newSize: scala.Int): scala.Unit = {
    if (newSize < 0) {
      throw new java.lang.IllegalArgumentException("newSize must be >= 0: " + newSize)
    } else ()
    if (this.size > newSize) {
      this.size = newSize
    } else ()
  }
  def random(): scala.Long = {
    if (this.size == 0) {
      return 0
    } else ()
    return this.items(com.badlogic.gdx.math.MathUtils.random(0, this.size - 1))
  }
  def toArray(): scala.Array[scala.Long] = {
    val array: scala.Array[scala.Long] = new scala.Array[scala.Long](this.size)
    java.lang.System.arraycopy(this.items, 0, array, 0, this.size)
    return array
  }
  def hashCode(): scala.Int = {
    if (!this.ordered) {
      return super.hashCode()
    } else ()
    val items: scala.Array[scala.Long] = this.items
    var h: scala.Int = 1;
    { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
      val item: scala.Long = items(i)
      h = (h * 31) + (item ^ (item >>> 32)).asInstanceOf[scala.Int]
    }; i = i + 1 } }
    return h
  }
  def equals(`object`: java.lang.Object): scala.Boolean = {
    if (`object` == this) {
      return true
    } else ()
    if (!this.ordered) {
      return false
    } else ()
    if (!`object`.isInstanceOf[LongArray]) {
      return false
    } else ()
    val array: LongArray = `object`.asInstanceOf[LongArray]
    if (!array.ordered) {
      return false
    } else ()
    val n: scala.Int = this.size
    if (n != array.size) {
      return false
    } else ()
    val items1: scala.Array[scala.Long] = this.items
    val items2: scala.Array[scala.Long] = array.items;
    { var i: scala.Int = 0; while (i < n) { {
      if (items1(i) != items2(i)) {
        return false
      } else ()
    }; i = i + 1 } }
    return true
  }
  def toString(): java.lang.String = {
    if (this.size == 0) {
      return "[]"
    } else ()
    val items: scala.Array[scala.Long] = this.items
    val buffer: java.lang.StringBuilder = new java.lang.StringBuilder(32)
    buffer.append('[')
    buffer.append(items(0));
    { var i: scala.Int = 1; while (i < this.size) { {
      buffer.append(", ")
      buffer.append(items(i))
    }; i = i + 1 } }
    buffer.append(']')
    return buffer.toString()
  }
  def toString(separator: java.lang.String): java.lang.String = {
    if (this.size == 0) {
      return ""
    } else ()
    val items: scala.Array[scala.Long] = this.items
    val buffer: java.lang.StringBuilder = new java.lang.StringBuilder(32)
    buffer.append(items(0));
    { var i: scala.Int = 1; while (i < this.size) { {
      buffer.append(separator)
      buffer.append(items(i))
    }; i = i + 1 } }
    return buffer.toString()
  }
}
object LongArray {
  def `with`(array: scala.Array[scala.Long]): LongArray = {
    return new LongArray(array)
  }
}