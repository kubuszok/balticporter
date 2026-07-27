package com.badlogic.gdx.utils

abstract class Pool[T] {
  var max: scala.Int = 0
  var peak: scala.Int = 0
  private var freeObjects: com.badlogic.gdx.utils.Array[T] = null.asInstanceOf[com.badlogic.gdx.utils.Array[T]]
  def this(initialCapacity: scala.Int, max: scala.Int) = {
    this()
    this.freeObjects = new com.badlogic.gdx.utils.Array[T](false, initialCapacity).asInstanceOf[com.badlogic.gdx.utils.Array[T]]
    this.max = max
  }
  def this(initialCapacity: scala.Int) = {
    this(initialCapacity, java.lang.Integer.MAX_VALUE)
  }
  def newObject(): T
  def obtain(): T = {
    return if (this.freeObjects.size == 0) this.newObject() else this.freeObjects.pop()
  }
  def free(`object`: T): scala.Unit = {
    if (`object` == null) {
      throw new java.lang.IllegalArgumentException("object cannot be null.")
    } else ()
    if (this.freeObjects.size < this.max) {
      this.freeObjects.add(`object`)
      this.peak = java.lang.Math.max(this.peak, this.freeObjects.size)
      this.reset(`object`)
    } else {
      this.discard(`object`)
    }
  }
  def fill(size: scala.Int): scala.Unit = {
    { var i: scala.Int = 0; while (i < size) { {
      if (this.freeObjects.size < this.max) {
        this.freeObjects.add(this.newObject())
      } else ()
    }; i = i + 1 } }
    this.peak = java.lang.Math.max(this.peak, this.freeObjects.size)
  }
  def reset(`object`: T): scala.Unit = {
    if (`object`.isInstanceOf[com.badlogic.gdx.utils.Pool.Poolable]) {
      `object`.asInstanceOf[com.badlogic.gdx.utils.Pool.Poolable].reset()
    } else ()
  }
  def discard(`object`: T): scala.Unit = {
    this.reset(`object`)
  }
  def freeAll(objects: com.badlogic.gdx.utils.Array[T]): scala.Unit = {
    if (objects == null) {
      throw new java.lang.IllegalArgumentException("objects cannot be null.")
    } else ()
    val freeObjects: com.badlogic.gdx.utils.Array[T] = this.freeObjects
    val max: scala.Int = this.max;
    { var i: scala.Int = 0; val n: scala.Int = objects.size; while (i < n) { {
      val `object`: T = objects.get(i).asInstanceOf[T]
      if (`object` == null) {
        /* continue */ ()
      } else ()
      if (freeObjects.size < max) {
        freeObjects.add(`object`)
        this.reset(`object`)
      } else {
        this.discard(`object`)
      }
    }; i = i + 1 } }
    this.peak = java.lang.Math.max(this.peak, freeObjects.size)
  }
  def clear(): scala.Unit = {
    val freeObjects: com.badlogic.gdx.utils.Array[T] = this.freeObjects;
    { var i: scala.Int = 0; val n: scala.Int = freeObjects.size; while (i < n) { {
      this.discard(freeObjects.get(i))
    }; i = i + 1 } }
    freeObjects.clear()
  }
  def getFree(): scala.Int = {
    return this.freeObjects.size
  }
}
object Pool {
  trait Poolable {
    def reset(): scala.Unit
  }
}