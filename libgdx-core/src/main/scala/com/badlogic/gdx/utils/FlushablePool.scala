package com.badlogic.gdx.utils

abstract class FlushablePool[T] extends com.badlogic.gdx.utils.Pool[T] {
  var obtained: com.badlogic.gdx.utils.Array[T] = new com.badlogic.gdx.utils.Array[T]()
  def this(initialCapacity: scala.Int) = {
    this()
    this.freeObjects = new com.badlogic.gdx.utils.Array[T](false, initialCapacity).asInstanceOf[com.badlogic.gdx.utils.Array[T]]
    this.max = java.lang.Integer.MAX_VALUE
  }
  def this(initialCapacity: scala.Int, max: scala.Int) = {
    this()
    this.freeObjects = new com.badlogic.gdx.utils.Array[T](false, initialCapacity).asInstanceOf[com.badlogic.gdx.utils.Array[T]]
    this.max = max
  }
  @java.lang.Override
  def obtain(): T = {
    val result: T = super.obtain().asInstanceOf[T]
    this.obtained.add(result)
    return result
  }
  def flush(): scala.Unit = {
    super.freeAll(this.obtained)
    this.obtained.clear()
  }
  @java.lang.Override
  def free(`object`: T): scala.Unit = {
    this.obtained.removeValue(`object`, true)
    super.free(`object`)
  }
  @java.lang.Override
  def freeAll(objects: com.badlogic.gdx.utils.Array[T]): scala.Unit = {
    this.obtained.removeAll(objects.asInstanceOf[com.badlogic.gdx.utils.Array[? <: T]], true)
    super.freeAll(objects)
  }
}
object FlushablePool {
  export com.badlogic.gdx.utils.Pool.*
}