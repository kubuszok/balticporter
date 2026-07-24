package com.badlogic.gdx.utils

abstract class FlushablePool[T] extends com.badlogic.gdx.utils.Pool[T] {
  var obtained: com.badlogic.gdx.utils.Array[T] = new com.badlogic.gdx.utils.Array[T]()
  def this(initialCapacity: scala.Int, max: scala.Int) = {
    this()
  }
  def this(initialCapacity: scala.Int) = {
    this()
  }
  def obtain(): T = {
    val result: T = super.obtain()
    this.obtained.add(result)
    return result
  }
  def flush(): scala.Unit = {
    super.freeAll(this.obtained)
    this.obtained.clear()
  }
  def free(`object`: T): scala.Unit = {
    this.obtained.removeValue(`object`, true)
    super.free(`object`)
  }
  def freeAll(objects: com.badlogic.gdx.utils.Array[T]): scala.Unit = {
    this.obtained.removeAll(objects, true)
    super.freeAll(objects)
  }
}