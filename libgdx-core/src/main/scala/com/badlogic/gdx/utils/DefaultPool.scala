package com.badlogic.gdx.utils

class DefaultPool[T <: java.lang.Object](supplier: com.badlogic.gdx.utils.DefaultPool.PoolSupplier[T], initialCapacity: scala.Int, max$p: scala.Int) extends com.badlogic.gdx.utils.Pool[T](initialCapacity, max$p) {
  private var poolTypeSupplier: com.badlogic.gdx.utils.DefaultPool.PoolSupplier[T] = null.asInstanceOf[com.badlogic.gdx.utils.DefaultPool.PoolSupplier[T]]
  def this(supplier: com.badlogic.gdx.utils.DefaultPool.PoolSupplier[T]) = {
    this(supplier, 16, java.lang.Integer.MAX_VALUE)
  }
  def this(supplier: com.badlogic.gdx.utils.DefaultPool.PoolSupplier[T], initialCapacity: scala.Int) = {
    this(supplier, initialCapacity, java.lang.Integer.MAX_VALUE)
  }
  this.poolTypeSupplier = supplier
  @java.lang.Override
  override def newObject(): T = {
    return this.poolTypeSupplier.get().asInstanceOf[T]
  }
}
object DefaultPool {
  export com.badlogic.gdx.utils.Pool.{PoolSupplier => _, *}
  trait PoolSupplier[T <: java.lang.Object] {
    def get(): T
  }
}