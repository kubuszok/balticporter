package com.badlogic.gdx.utils

class DefaultPool[T] extends com.badlogic.gdx.utils.Pool[T] {
  private var poolTypeSupplier: com.badlogic.gdx.utils.DefaultPool.PoolSupplier[T] = null.asInstanceOf[com.badlogic.gdx.utils.DefaultPool.PoolSupplier[T]]
  def this(supplier: com.badlogic.gdx.utils.DefaultPool.PoolSupplier[T], initialCapacity: scala.Int, max: scala.Int) = {
    this()
    this.poolTypeSupplier = supplier
  }
  def this(supplier: com.badlogic.gdx.utils.DefaultPool.PoolSupplier[T]) = {
    this(supplier, 16, java.lang.Integer.MAX_VALUE)
  }
  def this(supplier: com.badlogic.gdx.utils.DefaultPool.PoolSupplier[T], initialCapacity: scala.Int) = {
    this(supplier, initialCapacity, java.lang.Integer.MAX_VALUE)
  }
  def newObject(): T = {
    return this.poolTypeSupplier.get().asInstanceOf[T]
  }
}
object DefaultPool {
  export com.badlogic.gdx.utils.Pool.{PoolSupplier => _, *}
  trait PoolSupplier[T] {
    def get(): T
  }
}