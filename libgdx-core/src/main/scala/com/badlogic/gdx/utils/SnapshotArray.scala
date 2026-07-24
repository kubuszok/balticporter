package com.badlogic.gdx.utils

class SnapshotArray[T] extends com.badlogic.gdx.utils.Array[T] {
  private var snapshot: scala.Array[T] = null.asInstanceOf[scala.Array[T]]
  private var recycled: scala.Array[T] = null.asInstanceOf[scala.Array[T]]
  private var snapshots: scala.Int = 0
  def this(ordered: scala.Boolean, array: scala.Array[T], startIndex: scala.Int, count: scala.Int) = {
    this()
  }
  def this(ordered: scala.Boolean, capacity: scala.Int, arraySupplier: com.badlogic.gdx.utils.ArraySupplier[scala.Array[T]]) = {
    this()
  }
  def this(ordered: scala.Boolean, capacity: scala.Int, arrayType: java.lang.Class[?]) = {
    this()
  }
  def this(ordered: scala.Boolean, capacity: scala.Int) = {
    this()
  }
  def this(array: com.badlogic.gdx.utils.Array[?]) = {
    this()
  }
  def this(arraySupplier: com.badlogic.gdx.utils.ArraySupplier[scala.Array[T]]) = {
    this()
  }
  def this(arrayType: java.lang.Class[?]) = {
    this()
  }
  def this(capacity: scala.Int) = {
    this()
  }
  def this(array: scala.Array[T]) = {
    this()
  }
  def begin(): scala.Array[T] = {
    this.modified()
    this.snapshot = items
    this.snapshots = this.snapshots + 1
    return items
  }
  def `end`(): scala.Unit = {
    this.snapshots = java.lang.Math.max(0, this.snapshots - 1)
    if (this.snapshot == null) {
      return
    } else ()
    if ((this.snapshot != items) && (this.snapshots == 0)) {
      this.recycled = this.snapshot
      java.util.Arrays.fill(this.recycled.asInstanceOf[scala.Array[java.lang.Object]], null)
    } else ()
    this.snapshot = null
  }
  private def modified(): scala.Unit = {
    if ((this.snapshot == null) || (this.snapshot != items)) {
      return
    } else ()
    if ((this.recycled != null) && (this.recycled.length >= size)) {
      java.lang.System.arraycopy(items, 0, this.recycled, 0, size)
      items = this.recycled
      this.recycled = null
    } else {
      this.resize(this.items.length)
    }
  }
  def set(index: scala.Int, value: T): scala.Unit = {
    this.modified()
    super.set(index, value)
  }
  def insert(index: scala.Int, value: T): scala.Unit = {
    this.modified()
    super.insert(index, value)
  }
  def insertRange(index: scala.Int, count: scala.Int): scala.Unit = {
    this.modified()
    super.insertRange(index, count)
  }
  def swap(first: scala.Int, second: scala.Int): scala.Unit = {
    this.modified()
    super.swap(first, second)
  }
  def replaceFirst(value: T, identity: scala.Boolean, replacement: T): scala.Boolean = {
    this.modified()
    return super.replaceFirst(value, identity, replacement)
  }
  def replaceAll(value: T, identity: scala.Boolean, replacement: T): scala.Int = {
    this.modified()
    return super.replaceAll(value, identity, replacement)
  }
  def removeValue(value: T, identity: scala.Boolean): scala.Boolean = {
    this.modified()
    return super.removeValue(value, identity)
  }
  def removeIndex(index: scala.Int): T = {
    this.modified()
    return super.removeIndex(index).asInstanceOf[T]
  }
  def removeRange(start: scala.Int, `end`: scala.Int): scala.Unit = {
    this.modified()
    super.removeRange(start, `end`)
  }
  def removeAll(array: com.badlogic.gdx.utils.Array[? <: T], identity: scala.Boolean): scala.Boolean = {
    this.modified()
    return super.removeAll(array, identity)
  }
  def pop(): T = {
    this.modified()
    return super.pop().asInstanceOf[T]
  }
  def clear(): scala.Unit = {
    this.modified()
    super.clear()
  }
  def sort(): scala.Unit = {
    this.modified()
    super.sort()
  }
  def sort(comparator: java.util.Comparator[? >: T]): scala.Unit = {
    this.modified()
    super.sort(comparator)
  }
  def reverse(): scala.Unit = {
    this.modified()
    super.reverse()
  }
  def shuffle(): scala.Unit = {
    this.modified()
    super.shuffle()
  }
  def truncate(newSize: scala.Int): scala.Unit = {
    this.modified()
    super.truncate(newSize)
  }
  def setSize(newSize: scala.Int): scala.Array[T] = {
    this.modified()
    return super.setSize(newSize).asInstanceOf[scala.Array[T]]
  }
}
object SnapshotArray {
  export com.badlogic.gdx.utils.Array.{`with` => _, *}
  def `with`[T](array: scala.Array[T]): SnapshotArray[T] = {
    return new SnapshotArray(array)
  }
}