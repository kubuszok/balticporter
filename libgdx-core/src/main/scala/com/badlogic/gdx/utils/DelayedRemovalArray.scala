package com.badlogic.gdx.utils

class DelayedRemovalArray[T <: java.lang.Object] extends com.badlogic.gdx.utils.Array[T] {
  private var iterating: scala.Int = 0
  var remove$field: com.badlogic.gdx.utils.IntArray = new com.badlogic.gdx.utils.IntArray(0)
  var clear$field: scala.Int = 0
  def this(array: com.badlogic.gdx.utils.Array[T]) = {
    this()
    this.items = java.util.Arrays.copyOf(array.items.asInstanceOf[scala.Array[java.lang.Object]], array.size).asInstanceOf[scala.Array[T]]
    this.ordered = array.ordered
    this.size = array.size
  }
  def this(ordered: scala.Boolean, capacity: scala.Int, arraySupplier: com.badlogic.gdx.utils.ArraySupplier[scala.Array[T]]) = {
    this()
    this.ordered = ordered
    this.items = arraySupplier.get(capacity).asInstanceOf[scala.Array[T]]
  }
  def this(ordered: scala.Boolean, capacity: scala.Int) = {
    this()
    this.ordered = ordered
    this.items = com.badlogic.gdx.utils.ArraySupplier.`object`().get(capacity).asInstanceOf[scala.Array[T]]
  }
  def this(ordered: scala.Boolean, array: scala.Array[T], startIndex: scala.Int, count: scala.Int) = {
    this()
    this.items = java.util.Arrays.copyOfRange(array.asInstanceOf[scala.Array[java.lang.Object]], startIndex, startIndex + count).asInstanceOf[scala.Array[T]]
    this.ordered = ordered
    this.size = count
  }
  def this(arraySupplier: com.badlogic.gdx.utils.ArraySupplier[scala.Array[T]]) = {
    this()
    this.ordered = true
    this.items = arraySupplier.get(16).asInstanceOf[scala.Array[T]]
  }
  def this(capacity: scala.Int) = {
    this()
    this.ordered = true
    this.items = com.badlogic.gdx.utils.ArraySupplier.`object`().get(capacity).asInstanceOf[scala.Array[T]]
  }
  def this(array: scala.Array[T]) = {
    this()
    this.items = java.util.Arrays.copyOfRange(array.asInstanceOf[scala.Array[java.lang.Object]], 0, 0 + array.length).asInstanceOf[scala.Array[T]]
    this.ordered = true
    this.size = array.length
  }
  def begin(): scala.Unit = {
    this.iterating = this.iterating + 1
  }
  def `end`(): scala.Unit = {
    if (this.iterating == 0) {
      throw new java.lang.IllegalStateException("begin must be called before end.")
    } else ()
    this.iterating = this.iterating - 1
    if (this.iterating == 0) {
      if ((this.clear$field > 0) && (this.clear$field == size)) {
        this.remove$field.clear()
        this.clear()
      } else {
        { var i: scala.Int = 0; val n: scala.Int = this.remove$field.size; while (i < n) { {
          val index: scala.Int = this.remove$field.pop()
          if (index >= this.clear$field) {
            this.removeIndex(index)
          } else ()
        }; i = i + 1 } };
        { var i: scala.Int = this.clear$field - 1; while (i >= 0) { {
          this.removeIndex(i)
        }; i = i - 1 } }
      }
      this.clear$field = 0
    } else ()
  }
  private def remove(index: scala.Int): scala.Unit = {
    if (index < this.clear$field) {
      return
    } else ();
    { var i: scala.Int = 0; val n: scala.Int = this.remove$field.size; while (i < n) { {
      val removeIndex: scala.Int = this.remove$field.get(i)
      if (index == removeIndex) {
        return
      } else ()
      if (index < removeIndex) {
        this.remove$field.insert(i, index)
        return
      } else ()
    }; i = i + 1 } }
    this.remove$field.add(index)
  }
  override def removeValue(value: T, identity: scala.Boolean): scala.Boolean = {
    if (this.iterating > 0) {
      val index: scala.Int = this.indexOf(value, identity)
      if (index == (-1)) {
        return false
      } else ()
      this.remove(index)
      return true
    } else ()
    return super.removeValue(value, identity)
  }
  override def removeIndex(index: scala.Int): T = {
    if (this.iterating > 0) {
      this.remove(index)
      return this.get(index).asInstanceOf[T]
    } else ()
    return super.removeIndex(index).asInstanceOf[T]
  }
  override def removeRange(start: scala.Int, `end`: scala.Int): scala.Unit = {
    if (this.iterating > 0) {
      { var i: scala.Int = `end`; while (i >= start) { {
        this.remove(i)
      }; i = i - 1 } }
    } else {
      super.removeRange(start, `end`)
    }
  }
  override def clear(): scala.Unit = {
    if (this.iterating > 0) {
      this.clear$field = size
      return
    } else ()
    super.clear()
  }
  override def set(index: scala.Int, value: T): scala.Unit = {
    if (this.iterating > 0) {
      throw new java.lang.IllegalStateException("Invalid between begin/end.")
    } else ()
    super.set(index, value)
  }
  override def insert(index: scala.Int, value: T): scala.Unit = {
    if (this.iterating > 0) {
      throw new java.lang.IllegalStateException("Invalid between begin/end.")
    } else ()
    super.insert(index, value)
  }
  override def insertRange(index: scala.Int, count: scala.Int): scala.Unit = {
    if (this.iterating > 0) {
      throw new java.lang.IllegalStateException("Invalid between begin/end.")
    } else ()
    super.insertRange(index, count)
  }
  override def swap(first: scala.Int, second: scala.Int): scala.Unit = {
    if (this.iterating > 0) {
      throw new java.lang.IllegalStateException("Invalid between begin/end.")
    } else ()
    super.swap(first, second)
  }
  override def replaceFirst(value: T, identity: scala.Boolean, replacement: T): scala.Boolean = {
    if (this.iterating > 0) {
      throw new java.lang.IllegalStateException("Invalid between begin/end.")
    } else ()
    return super.replaceFirst(value, identity, replacement)
  }
  override def replaceAll(value: T, identity: scala.Boolean, replacement: T): scala.Int = {
    if (this.iterating > 0) {
      throw new java.lang.IllegalStateException("Invalid between begin/end.")
    } else ()
    return super.replaceAll(value, identity, replacement)
  }
  override def pop(): T = {
    if (this.iterating > 0) {
      throw new java.lang.IllegalStateException("Invalid between begin/end.")
    } else ()
    return super.pop().asInstanceOf[T]
  }
  override def sort(): scala.Unit = {
    if (this.iterating > 0) {
      throw new java.lang.IllegalStateException("Invalid between begin/end.")
    } else ()
    super.sort()
  }
  override def sort(comparator: java.util.Comparator[? >: T]): scala.Unit = {
    if (this.iterating > 0) {
      throw new java.lang.IllegalStateException("Invalid between begin/end.")
    } else ()
    super.sort(comparator)
  }
  override def reverse(): scala.Unit = {
    if (this.iterating > 0) {
      throw new java.lang.IllegalStateException("Invalid between begin/end.")
    } else ()
    super.reverse()
  }
  override def shuffle(): scala.Unit = {
    if (this.iterating > 0) {
      throw new java.lang.IllegalStateException("Invalid between begin/end.")
    } else ()
    super.shuffle()
  }
  override def truncate(newSize: scala.Int): scala.Unit = {
    if (this.iterating > 0) {
      throw new java.lang.IllegalStateException("Invalid between begin/end.")
    } else ()
    super.truncate(newSize)
  }
  override def setSize(newSize: scala.Int): scala.Array[T] = {
    if (this.iterating > 0) {
      throw new java.lang.IllegalStateException("Invalid between begin/end.")
    } else ()
    return super.setSize(newSize).asInstanceOf[scala.Array[T]]
  }
}
object DelayedRemovalArray {
  export com.badlogic.gdx.utils.Array.{`with` => _, *}
  override def `with`[T <: java.lang.Object](array: scala.Array[T]): DelayedRemovalArray[T] = {
    return new DelayedRemovalArray(array).asInstanceOf[DelayedRemovalArray[T]]
  }
}