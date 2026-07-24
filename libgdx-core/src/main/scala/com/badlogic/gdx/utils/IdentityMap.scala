package com.badlogic.gdx.utils

class IdentityMap[K, V] extends com.badlogic.gdx.utils.ObjectMap[K, V] {
  def this(initialCapacity: scala.Int) = {
    this()
  }
  def this(initialCapacity: scala.Int, loadFactor: scala.Float) = {
    this()
  }
  def this(map: IdentityMap[K, V]) = {
    this()
  }
  def place(item: K): scala.Int = {
    return ((java.lang.System.identityHashCode(item.asInstanceOf[java.lang.Object]) * -7046029254386353131L) >>> shift).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
  }
  def locateKey(key: K): scala.Int = {
    {
      if (key == null) {
        throw new java.lang.IllegalArgumentException("key cannot be null.")
      } else ()
      val keyTable: scala.Array[K] = this.keyTable;
      { var i: scala.Int = this.place(key); while (true) { {
        val other: K = keyTable(i)
        if (other == null) {
          return -(i + 1)
        } else ()
        if (other == key) {
          return i
        } else ()
      }; i = (i + 1) & mask } }
    }
    throw new java.lang.RuntimeException("unreachable")
  }
  def hashCode(): scala.Int = {
    var h: scala.Int = size
    val keyTable: scala.Array[K] = this.keyTable
    val valueTable: scala.Array[V] = this.valueTable;
    { var i: scala.Int = 0; val n: scala.Int = keyTable.length; while (i < n) { {
      val key: K = keyTable(i)
      if (key != null) {
        h = h + java.lang.System.identityHashCode(key.asInstanceOf[java.lang.Object])
        val value: V = valueTable(i)
        if (value != null) {
          h = h + value.hashCode()
        } else ()
      } else ()
    }; i = i + 1 } }
    return h
  }
}
object IdentityMap {
  export com.badlogic.gdx.utils.ObjectMap.*
}