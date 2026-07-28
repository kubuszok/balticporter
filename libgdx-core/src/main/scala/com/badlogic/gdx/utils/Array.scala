package com.badlogic.gdx.utils

class Array[T] extends balticporter.runtime.JavaIterable[T] {
  var items: scala.Array[T] = null.asInstanceOf[scala.Array[T]]
  var size: scala.Int = 0
  var ordered: scala.Boolean = false
  private var iterable: com.badlogic.gdx.utils.Array.ArrayIterable[T] = null.asInstanceOf[com.badlogic.gdx.utils.Array.ArrayIterable[T]]
  private var predicateIterable: com.badlogic.gdx.utils.Predicate.PredicateIterable[T] = null.asInstanceOf[com.badlogic.gdx.utils.Predicate.PredicateIterable[T]]
  def this(ordered: scala.Boolean, capacity: scala.Int, arraySupplier: com.badlogic.gdx.utils.ArraySupplier[scala.Array[T]]) = {
    this()
    this.ordered = ordered
    this.items = arraySupplier.get(capacity).asInstanceOf[scala.Array[T]]
  }
  def this(ordered: scala.Boolean, capacity: scala.Int) = {
    this(ordered, capacity, com.badlogic.gdx.utils.ArraySupplier.`object`())
  }
  def this(capacity: scala.Int) = {
    this(true, capacity)
  }
  def this(arraySupplier: com.badlogic.gdx.utils.ArraySupplier[scala.Array[T]]) = {
    this(true, 16, arraySupplier)
  }
  def this(array: Array[? <: T]) = {
    this()
    this.items = java.util.Arrays.copyOf(array.items.asInstanceOf[scala.Array[java.lang.Object]], array.size).asInstanceOf[scala.Array[T]]
    this.ordered = array.ordered
    this.size = array.size
  }
  def this(ordered: scala.Boolean, array: scala.Array[T], start: scala.Int, count: scala.Int) = {
    this()
    this.items = java.util.Arrays.copyOfRange(array.asInstanceOf[scala.Array[java.lang.Object]], start, start + count).asInstanceOf[scala.Array[T]]
    this.ordered = ordered
    this.size = count
  }
  def this(array: scala.Array[T]) = {
    this(true, array, 0, array.length)
  }
  def add(value: T): scala.Unit = {
    var items: scala.Array[T] = this.items
    if (this.size == items.length) {
      items = this.resize(java.lang.Math.max(8, (this.size * 1.75f).asInstanceOf[scala.Int].asInstanceOf[scala.Int])).asInstanceOf[scala.Array[T]]
    } else ()
    items({ this.size += 1; this.size }) = value
  }
  def add(value1: T, value2: T): scala.Unit = {
    var items: scala.Array[T] = this.items
    if ((this.size + 1) >= items.length) {
      items = this.resize(java.lang.Math.max(8, (this.size * 1.75f).asInstanceOf[scala.Int].asInstanceOf[scala.Int])).asInstanceOf[scala.Array[T]]
    } else ()
    items(this.size) = value1
    items(this.size + 1) = value2
    this.size = this.size + 2
  }
  def add(value1: T, value2: T, value3: T): scala.Unit = {
    var items: scala.Array[T] = this.items
    if ((this.size + 2) >= items.length) {
      items = this.resize(java.lang.Math.max(8, (this.size * 1.75f).asInstanceOf[scala.Int].asInstanceOf[scala.Int])).asInstanceOf[scala.Array[T]]
    } else ()
    items(this.size) = value1
    items(this.size + 1) = value2
    items(this.size + 2) = value3
    this.size = this.size + 3
  }
  def add(value1: T, value2: T, value3: T, value4: T): scala.Unit = {
    var items: scala.Array[T] = this.items
    if ((this.size + 3) >= items.length) {
      items = this.resize(java.lang.Math.max(8, (this.size * 1.8f).asInstanceOf[scala.Int].asInstanceOf[scala.Int])).asInstanceOf[scala.Array[T]]
    } else ()
    items(this.size) = value1
    items(this.size + 1) = value2
    items(this.size + 2) = value3
    items(this.size + 3) = value4
    this.size = this.size + 4
  }
  def addAll(array: Array[? <: T]): scala.Unit = {
    this.addAll(array.items.asInstanceOf[scala.Array[T]], 0, array.size)
  }
  def addAll(array: Array[? <: T], start: scala.Int, count: scala.Int): scala.Unit = {
    if ((start + count) > array.size) {
      throw new java.lang.IllegalArgumentException((((("start + count must be <= size: " + start) + " + ") + count) + " <= ") + array.size)
    } else ()
    this.addAll(array.items.asInstanceOf[scala.Array[T]], start, count)
  }
  def addAll(array: scala.Array[T]): scala.Unit = {
    this.addAll(array, 0, array.length)
  }
  def addAll(array: scala.Array[T], start: scala.Int, count: scala.Int): scala.Unit = {
    var items: scala.Array[T] = this.items
    val sizeNeeded: scala.Int = this.size + count
    if (sizeNeeded > items.length) {
      items = this.resize(java.lang.Math.max(java.lang.Math.max(8, sizeNeeded), (this.size * 1.75f).asInstanceOf[scala.Int].asInstanceOf[scala.Int])).asInstanceOf[scala.Array[T]]
    } else ()
    java.lang.System.arraycopy(array, start, items, this.size, count)
    this.size = sizeNeeded
  }
  def get(index: scala.Int): T = {
    if (index >= this.size) {
      throw new java.lang.IndexOutOfBoundsException((("index can't be >= size: " + index) + " >= ") + this.size)
    } else ()
    return this.items(index)
  }
  def set(index: scala.Int, value: T): scala.Unit = {
    if (index >= this.size) {
      throw new java.lang.IndexOutOfBoundsException((("index can't be >= size: " + index) + " >= ") + this.size)
    } else ()
    this.items(index) = value
  }
  def insert(index: scala.Int, value: T): scala.Unit = {
    if (index > this.size) {
      throw new java.lang.IndexOutOfBoundsException((("index can't be > size: " + index) + " > ") + this.size)
    } else ()
    var items: scala.Array[T] = this.items
    if (this.size == items.length) {
      items = this.resize(java.lang.Math.max(8, (this.size * 1.75f).asInstanceOf[scala.Int].asInstanceOf[scala.Int])).asInstanceOf[scala.Array[T]]
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
      this.items = this.resize(java.lang.Math.max(java.lang.Math.max(8, sizeNeeded), (this.size * 1.75f).asInstanceOf[scala.Int].asInstanceOf[scala.Int])).asInstanceOf[scala.Array[T]]
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
    val items: scala.Array[T] = this.items
    val firstValue: T = items(first)
    items(first) = items(second)
    items(second) = firstValue
  }
  def replaceFirst(value: T, identity: scala.Boolean, replacement: T): scala.Boolean = {
    val items: scala.Array[T] = this.items
    if (identity || (value == null)) {
      { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
        if (items(i) == value) {
          items(i) = replacement
          return true
        } else ()
      }; i = i + 1 } }
    } else {
      { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
        if (value.equals(items(i).asInstanceOf[java.lang.Object])) {
          items(i) = replacement
          return true
        } else ()
      }; i = i + 1 } }
    }
    return false
  }
  def replaceAll(value: T, identity: scala.Boolean, replacement: T): scala.Int = {
    val items: scala.Array[T] = this.items
    var replacements: scala.Int = 0
    if (identity || (value == null)) {
      { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
        if (items(i) == value) {
          items(i) = replacement
          replacements = replacements + 1
        } else ()
      }; i = i + 1 } }
    } else {
      { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
        if (value.equals(items(i).asInstanceOf[java.lang.Object])) {
          items(i) = replacement
          replacements = replacements + 1
        } else ()
      }; i = i + 1 } }
    }
    return replacements
  }
  def contains(value: T, identity: scala.Boolean): scala.Boolean = {
    val items: scala.Array[T] = this.items
    var i: scala.Int = this.size - 1
    if (identity || (value == null)) {
      while (i >= 0) {
        if (items({ i -= 1; i }) == value) {
          return true
        } else ()
      }
    } else {
      while (i >= 0) {
        if (value.equals(items({ i -= 1; i }).asInstanceOf[java.lang.Object])) {
          return true
        } else ()
      }
    }
    return false
  }
  def containsAll(values: Array[? <: T], identity: scala.Boolean): scala.Boolean = {
    val items: scala.Array[T] = values.items.asInstanceOf[scala.Array[T]];
    { var i: scala.Int = 0; val n: scala.Int = values.size; while (i < n) { {
      if (!this.contains(items(i), identity)) {
        return false
      } else ()
    }; i = i + 1 } }
    return true
  }
  def containsAny(values: Array[? <: T], identity: scala.Boolean): scala.Boolean = {
    val items: scala.Array[T] = values.items.asInstanceOf[scala.Array[T]];
    { var i: scala.Int = 0; val n: scala.Int = values.size; while (i < n) { {
      if (this.contains(items(i), identity)) {
        return true
      } else ()
    }; i = i + 1 } }
    return false
  }
  def indexOf(value: T, identity: scala.Boolean): scala.Int = {
    val items: scala.Array[T] = this.items
    if (identity || (value == null)) {
      { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
        if (items(i) == value) {
          return i
        } else ()
      }; i = i + 1 } }
    } else {
      { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
        if (value.equals(items(i).asInstanceOf[java.lang.Object])) {
          return i
        } else ()
      }; i = i + 1 } }
    }
    return -1
  }
  def lastIndexOf(value: T, identity: scala.Boolean): scala.Int = {
    val items: scala.Array[T] = this.items
    if (identity || (value == null)) {
      { var i: scala.Int = this.size - 1; while (i >= 0) { {
        if (items(i) == value) {
          return i
        } else ()
      }; i = i - 1 } }
    } else {
      { var i: scala.Int = this.size - 1; while (i >= 0) { {
        if (value.equals(items(i).asInstanceOf[java.lang.Object])) {
          return i
        } else ()
      }; i = i - 1 } }
    }
    return -1
  }
  def removeValue(value: T, identity: scala.Boolean): scala.Boolean = {
    val items: scala.Array[T] = this.items
    if (identity || (value == null)) {
      { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
        if (items(i) == value) {
          this.removeIndex(i)
          return true
        } else ()
      }; i = i + 1 } }
    } else {
      { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
        if (value.equals(items(i).asInstanceOf[java.lang.Object])) {
          this.removeIndex(i)
          return true
        } else ()
      }; i = i + 1 } }
    }
    return false
  }
  def removeIndex(index: scala.Int): T = {
    if (index >= this.size) {
      throw new java.lang.IndexOutOfBoundsException((("index can't be >= size: " + index) + " >= ") + this.size)
    } else ()
    val items: scala.Array[T] = this.items
    val value: T = items(index)
    this.size = this.size - 1
    if (this.ordered) {
      java.lang.System.arraycopy(items, index + 1, items, index, this.size - index)
    } else {
      items(index) = items(this.size)
    }
    items(this.size) = null.asInstanceOf[T]
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
    val items: scala.Array[T] = this.items
    val count: scala.Int = (`end` - start) + 1
    val lastIndex: scala.Int = n - count
    if (this.ordered) {
      java.lang.System.arraycopy(items, start + count, items, start, n - (start + count))
    } else {
      var i: scala.Int = java.lang.Math.max(lastIndex, `end` + 1)
      java.lang.System.arraycopy(items, i, items, start, n - i)
    };
    { var i: scala.Int = lastIndex; while (i < n) { {
      items(i) = null.asInstanceOf[T]
    }; i = i + 1 } }
    this.size = n - count
  }
  def removeAll(array: Array[? <: T], identity: scala.Boolean): scala.Boolean = {
    var size: scala.Int = this.size
    val startSize: scala.Int = size
    val items: scala.Array[T] = this.items
    if (identity) {
      { var i: scala.Int = 0; val n: scala.Int = array.size; while (i < n) { {
        val item: T = array.get(i);
        { var ii: scala.Int = 0; while (ii < size) { {
          if (item == items(ii)) {
            this.removeIndex(ii)
            size = size - 1
            /* break */ ()
          } else ()
        }; ii = ii + 1 } }
      }; i = i + 1 } }
    } else {
      { var i: scala.Int = 0; val n: scala.Int = array.size; while (i < n) { {
        val item: T = array.get(i);
        { var ii: scala.Int = 0; while (ii < size) { {
          if (item.equals(items(ii).asInstanceOf[java.lang.Object])) {
            this.removeIndex(ii)
            size = size - 1
            /* break */ ()
          } else ()
        }; ii = ii + 1 } }
      }; i = i + 1 } }
    }
    return size != startSize
  }
  def pop(): T = {
    if (this.size == 0) {
      throw new java.lang.IllegalStateException("Array is empty.")
    } else ()
    this.size = this.size - 1
    val item: T = this.items(this.size)
    this.items(this.size) = null.asInstanceOf[T]
    return item
  }
  def peek(): T = {
    if (this.size == 0) {
      throw new java.lang.IllegalStateException("Array is empty.")
    } else ()
    return this.items(this.size - 1)
  }
  def first(): T = {
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
    java.util.Arrays.fill(this.items.asInstanceOf[scala.Array[java.lang.Object]], 0, this.size, null)
    this.size = 0
  }
  def shrink(): scala.Array[T] = {
    if (this.items.length != this.size) {
      this.resize(this.size)
    } else ()
    return this.items
  }
  def ensureCapacity(additionalCapacity: scala.Int): scala.Array[T] = {
    if (additionalCapacity < 0) {
      throw new java.lang.IllegalArgumentException("additionalCapacity must be >= 0: " + additionalCapacity)
    } else ()
    val sizeNeeded: scala.Int = this.size + additionalCapacity
    if (sizeNeeded > this.items.length) {
      this.resize(java.lang.Math.max(java.lang.Math.max(8, sizeNeeded), (this.size * 1.75f).asInstanceOf[scala.Int].asInstanceOf[scala.Int]))
    } else ()
    return this.items
  }
  def setSize(newSize: scala.Int): scala.Array[T] = {
    this.truncate(newSize)
    if (newSize > this.items.length) {
      this.resize(java.lang.Math.max(8, newSize))
    } else ()
    this.size = newSize
    return this.items
  }
  def resize(newSize: scala.Int): scala.Array[T] = {
    this.items = java.util.Arrays.copyOf(this.items.asInstanceOf[scala.Array[java.lang.Object]], newSize).asInstanceOf[scala.Array[T]]
    return this.items
  }
  def sort(): scala.Unit = {
    com.badlogic.gdx.utils.Sort.instance().sort(this.items.asInstanceOf[scala.Array[java.lang.Object]], 0, this.size)
  }
  def sort(comparator: java.util.Comparator[? >: T]): scala.Unit = {
    com.badlogic.gdx.utils.Sort.instance().sort(this.items, comparator, 0, this.size)
  }
  def selectRanked(comparator: java.util.Comparator[T], kthLowest: scala.Int): T = {
    if (kthLowest < 1) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("nth_lowest must be greater than 0, 1 = first, 2 = second...")
    } else ()
    return com.badlogic.gdx.utils.Select.instance().select(this.items, comparator, kthLowest, this.size).asInstanceOf[T]
  }
  def selectRankedIndex(comparator: java.util.Comparator[T], kthLowest: scala.Int): scala.Int = {
    if (kthLowest < 1) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("nth_lowest must be greater than 0, 1 = first, 2 = second...")
    } else ()
    return com.badlogic.gdx.utils.Select.instance().selectIndex(this.items, comparator, kthLowest, this.size)
  }
  def reverse(): scala.Unit = {
    val items: scala.Array[T] = this.items;
    { var i: scala.Int = 0; val lastIndex: scala.Int = this.size - 1; val n: scala.Int = this.size / 2; while (i < n) { {
      val ii: scala.Int = lastIndex - i
      val temp: T = items(i)
      items(i) = items(ii)
      items(ii) = temp
    }; i = i + 1 } }
  }
  def shuffle(): scala.Unit = {
    val items: scala.Array[T] = this.items;
    { var i: scala.Int = this.size - 1; while (i >= 0) { {
      val ii: scala.Int = (com.badlogic.gdx.math.MathUtils.random: (scala.Int) => scala.Int)(i)
      val temp: T = items(i)
      items(i) = items(ii)
      items(ii) = temp
    }; i = i - 1 } }
  }
  def iterator(): com.badlogic.gdx.utils.Array.ArrayIterator[T] = {
    if (com.badlogic.gdx.utils.Collections.allocateIterators) {
      return new com.badlogic.gdx.utils.Array.ArrayIterator[T](this.asInstanceOf[Array[T]], true)
    } else ()
    if (this.iterable == null) {
      this.iterable = new com.badlogic.gdx.utils.Array.ArrayIterable[T](this.asInstanceOf[Array[T]])
    } else ()
    return this.iterable.iterator()
  }
  def select(predicate: com.badlogic.gdx.utils.Predicate[T]): balticporter.runtime.JavaIterable[T] = {
    if (com.badlogic.gdx.utils.Collections.allocateIterators) {
      return new com.badlogic.gdx.utils.Predicate.PredicateIterable[T](this.asInstanceOf[balticporter.runtime.JavaIterable[T]], predicate)
    } else ()
    if (this.predicateIterable == null) {
      this.predicateIterable = new com.badlogic.gdx.utils.Predicate.PredicateIterable[T](this.asInstanceOf[balticporter.runtime.JavaIterable[T]], predicate)
    } else {
      this.predicateIterable.set(this, predicate)
    }
    return this.predicateIterable
  }
  def truncate(newSize: scala.Int): scala.Unit = {
    if (newSize < 0) {
      throw new java.lang.IllegalArgumentException("newSize must be >= 0: " + newSize)
    } else ()
    if (this.size <= newSize) {
      return
    } else ();
    { var i: scala.Int = newSize; while (i < this.size) { {
      this.items(i) = null.asInstanceOf[T]
    }; i = i + 1 } }
    this.size = newSize
  }
  @com.badlogic.gdx.utils.Null
  def random(): T = {
    if (this.size == 0) {
      return null.asInstanceOf[T]
    } else ()
    return this.items((com.badlogic.gdx.math.MathUtils.random: (scala.Int, scala.Int) => scala.Int)(0, this.size - 1))
  }
  def toArray(): scala.Array[T] = {
    return java.util.Arrays.copyOf(this.items.asInstanceOf[scala.Array[java.lang.Object]], this.size).asInstanceOf[scala.Array[T]]
  }
  def toArray(arraySupplier: com.badlogic.gdx.utils.ArraySupplier[scala.Array[T]]): scala.Array[T] = {
    val result: scala.Array[T] = arraySupplier.get(this.size).asInstanceOf[scala.Array[T]]
    java.lang.System.arraycopy(this.items, 0, result, 0, this.size)
    return result
  }
  def hashCode(): scala.Int = {
    if (!this.ordered) {
      return super.hashCode()
    } else ()
    val items: scala.Array[java.lang.Object] = this.items.asInstanceOf[scala.Array[java.lang.Object]]
    var h: scala.Int = 1;
    { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
      h = h * 31
      val item: java.lang.Object = items(i)
      if (item != null) {
        h = h + item.hashCode()
      } else ()
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
    if (!`object`.isInstanceOf[Array[T]]) {
      return false
    } else ()
    val array: Array[T] = `object`.asInstanceOf[Array[T]].asInstanceOf[Array[T]]
    if (!array.ordered) {
      return false
    } else ()
    val n: scala.Int = this.size
    if (n != array.size) {
      return false
    } else ()
    val items1: scala.Array[java.lang.Object] = this.items.asInstanceOf[scala.Array[java.lang.Object]]
    val items2: scala.Array[java.lang.Object] = array.asInstanceOf[Array[java.lang.Object]].items;
    { var i: scala.Int = 0; while (i < n) { {
      val o1: java.lang.Object = items1(i)
      val o2: java.lang.Object = items2(i)
      if (!(if (o1 == null) o2 == null else o1.equals(o2))) {
        return false
      } else ()
    }; i = i + 1 } }
    return true
  }
  def equalsIdentity(`object`: java.lang.Object): scala.Boolean = {
    if (`object` == this) {
      return true
    } else ()
    if (!this.ordered) {
      return false
    } else ()
    if (!`object`.isInstanceOf[Array[T]]) {
      return false
    } else ()
    val array: Array[T] = `object`.asInstanceOf[Array[T]].asInstanceOf[Array[T]]
    if (!array.ordered) {
      return false
    } else ()
    val n: scala.Int = this.size
    if (n != array.size) {
      return false
    } else ()
    val items1: scala.Array[java.lang.Object] = this.items.asInstanceOf[scala.Array[java.lang.Object]]
    val items2: scala.Array[java.lang.Object] = array.asInstanceOf[Array[java.lang.Object]].items;
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
    val items: scala.Array[T] = this.items
    val buffer: java.lang.StringBuilder = new java.lang.StringBuilder(32)
    buffer.append('[')
    buffer.append(items(0).asInstanceOf[java.lang.Object]);
    { var i: scala.Int = 1; while (i < this.size) { {
      buffer.append(", ")
      buffer.append(items(i).asInstanceOf[java.lang.Object])
    }; i = i + 1 } }
    buffer.append(']')
    return buffer.toString()
  }
  def toString(separator: java.lang.String): java.lang.String = {
    if (this.size == 0) {
      return ""
    } else ()
    val items: scala.Array[T] = this.items
    val buffer: java.lang.StringBuilder = new java.lang.StringBuilder(32)
    buffer.append(items(0).asInstanceOf[java.lang.Object]);
    { var i: scala.Int = 1; while (i < this.size) { {
      buffer.append(separator)
      buffer.append(items(i).asInstanceOf[java.lang.Object])
    }; i = i + 1 } }
    return buffer.toString()
  }
}
object Array {
  def of[T](arraySupplier: com.badlogic.gdx.utils.ArraySupplier[scala.Array[T]]): Array[T] = {
    return new Array[T](arraySupplier)
  }
  def of[T](ordered: scala.Boolean, capacity: scala.Int, arraySupplier: com.badlogic.gdx.utils.ArraySupplier[scala.Array[T]]): Array[T] = {
    return new Array[T](ordered, capacity, arraySupplier)
  }
  def `with`[T](array: scala.Array[T]): Array[T] = {
    return new Array(array).asInstanceOf[Array[T]]
  }
  class ArrayIterator[T](array$p: Array[T], allowRemove$p: scala.Boolean) extends balticporter.runtime.JavaIterator[T] with balticporter.runtime.JavaIterable[T] {
    private var array: Array[T] = null.asInstanceOf[Array[T]]
    private var allowRemove: scala.Boolean = false
    var index: scala.Int = 0
    var valid: scala.Boolean = true
    def this(array: Array[T]) = {
      this(array, true)
    }
    this.array = array$p
    this.allowRemove = allowRemove$p
    def hasNext(): scala.Boolean = {
      if (!this.valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      return this.index < this.array.size
    }
    def next(): T = {
      if (this.index >= this.array.size) {
        throw new java.util.NoSuchElementException(java.lang.String.valueOf(this.index))
      } else ()
      if (!this.valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      return this.array.items({ this.index += 1; this.index })
    }
    def remove(): scala.Unit = {
      if (!this.allowRemove) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Remove not allowed.")
      } else ()
      this.index = this.index - 1
      this.array.removeIndex(this.index)
    }
    def reset(): scala.Unit = {
      this.index = 0
    }
    def iterator(): com.badlogic.gdx.utils.Array.ArrayIterator[T] = {
      return this
    }
  }
  class ArrayIterable[T](array$p: Array[T], allowRemove$p: scala.Boolean) extends balticporter.runtime.JavaIterable[T] {
    private var array: Array[T] = null.asInstanceOf[Array[T]]
    private var allowRemove: scala.Boolean = false
    private var iterator1: com.badlogic.gdx.utils.Array.ArrayIterator[T] = null.asInstanceOf[com.badlogic.gdx.utils.Array.ArrayIterator[T]]
    private var iterator2: com.badlogic.gdx.utils.Array.ArrayIterator[T] = null.asInstanceOf[com.badlogic.gdx.utils.Array.ArrayIterator[T]]
    def this(array: Array[T]) = {
      this(array, true)
    }
    this.array = array$p
    this.allowRemove = allowRemove$p
    def iterator(): com.badlogic.gdx.utils.Array.ArrayIterator[T] = {
      if (com.badlogic.gdx.utils.Collections.allocateIterators) {
        return new com.badlogic.gdx.utils.Array.ArrayIterator[T](this.array, this.allowRemove)
      } else ()
      if (this.iterator1 == null) {
        this.iterator1 = new com.badlogic.gdx.utils.Array.ArrayIterator[T](this.array, this.allowRemove)
        this.iterator2 = new com.badlogic.gdx.utils.Array.ArrayIterator[T](this.array, this.allowRemove)
      } else ()
      if (!this.iterator1.valid) {
        this.iterator1.index = 0
        this.iterator1.valid = true
        this.iterator2.valid = false
        return this.iterator1
      } else ()
      this.iterator2.index = 0
      this.iterator2.valid = true
      this.iterator1.valid = false
      return this.iterator2
    }
  }
}