package com.badlogic.gdx.utils

class DefaultPool[T] extends com.badlogic.gdx.utils.Pool[T] {
  private var poolTypeSupplier: PoolSupplier[T] = null.asInstanceOf[PoolSupplier[T]]
  def this(supplier: PoolSupplier[T], initialCapacity: scala.Int, max: scala.Int) = {
    this()
    this.poolTypeSupplier = supplier
  }
  def this(supplier: PoolSupplier[T], initialCapacity: scala.Int) = {
    this(supplier, initialCapacity, java.lang.Integer.MAX_VALUE)
  }
  def this(supplier: PoolSupplier[T]) = {
    this(supplier, 16, java.lang.Integer.MAX_VALUE)
  }
  protected def newObject(): T = {
    return this.poolTypeSupplier.get()
  }
  trait PoolSupplier[T] {
    def get(): T
  }
}