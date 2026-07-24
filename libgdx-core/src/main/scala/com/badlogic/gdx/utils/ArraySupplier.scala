package com.badlogic.gdx.utils

trait ArraySupplier[T] {
  def get(size: scala.Int): T
}
object ArraySupplier {
  final val ANY: ArraySupplier[?] = (() => new scala.Array[java.lang.Object]())
  def `object`[T](): ArraySupplier[scala.Array[T]] = {
    return ArraySupplier.ANY.asInstanceOf[ArraySupplier[scala.Array[T]]]
  }
}