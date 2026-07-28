package com.badlogic.gdx.utils

class IdentityMap[K <: java.lang.Object, V <: java.lang.Object] extends com.badlogic.gdx.utils.ObjectMap[K, V] {
  def this(initialCapacity: scala.Int) = {
    this()
    if ((0.8f <= 0.0f) || (0.8f >= 1.0f)) {
      throw new java.lang.IllegalArgumentException("loadFactor must be > 0 and < 1: " + 0.8f)
    } else ()
    this.loadFactor = 0.8f
    val tableSize: scala.Int = com.badlogic.gdx.utils.ObjectSet.tableSize(initialCapacity, 0.8f)
    this.threshold = (tableSize * 0.8f).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    this.mask = tableSize - 1
    this.shift = java.lang.Long.numberOfLeadingZeros(this.mask)
    this.keyTable = new scala.Array[java.lang.Object](tableSize).asInstanceOf[scala.Array[K]].asInstanceOf[scala.Array[K]]
    this.valueTable = new scala.Array[java.lang.Object](tableSize).asInstanceOf[scala.Array[V]].asInstanceOf[scala.Array[V]]
  }
  def this(initialCapacity: scala.Int, loadFactor: scala.Float) = {
    this()
    if ((loadFactor <= 0.0f) || (loadFactor >= 1.0f)) {
      throw new java.lang.IllegalArgumentException("loadFactor must be > 0 and < 1: " + loadFactor)
    } else ()
    this.loadFactor = loadFactor
    val tableSize: scala.Int = com.badlogic.gdx.utils.ObjectSet.tableSize(initialCapacity, loadFactor)
    this.threshold = (tableSize * loadFactor).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    this.mask = tableSize - 1
    this.shift = java.lang.Long.numberOfLeadingZeros(this.mask)
    this.keyTable = new scala.Array[java.lang.Object](tableSize).asInstanceOf[scala.Array[K]].asInstanceOf[scala.Array[K]]
    this.valueTable = new scala.Array[java.lang.Object](tableSize).asInstanceOf[scala.Array[V]].asInstanceOf[scala.Array[V]]
  }
  def this(map: IdentityMap[K, V]) = {
    this()
    if ((map.loadFactor <= 0.0f) || (map.loadFactor >= 1.0f)) {
      throw new java.lang.IllegalArgumentException("loadFactor must be > 0 and < 1: " + map.loadFactor)
    } else ()
    this.loadFactor = map.loadFactor
    val tableSize: scala.Int = com.badlogic.gdx.utils.ObjectSet.tableSize((map.keyTable.length * map.loadFactor).asInstanceOf[scala.Int].asInstanceOf[scala.Int], map.loadFactor)
    this.threshold = (tableSize * map.loadFactor).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    this.mask = tableSize - 1
    this.shift = java.lang.Long.numberOfLeadingZeros(this.mask)
    this.keyTable = new scala.Array[java.lang.Object](tableSize).asInstanceOf[scala.Array[K]].asInstanceOf[scala.Array[K]]
    this.valueTable = new scala.Array[java.lang.Object](tableSize).asInstanceOf[scala.Array[V]].asInstanceOf[scala.Array[V]]
    java.lang.System.arraycopy(map.keyTable, 0, this.keyTable, 0, map.keyTable.length)
    java.lang.System.arraycopy(map.valueTable, 0, this.valueTable, 0, map.valueTable.length)
    this.size = map.size
  }
  override def place(item: K): scala.Int = {
    return ((java.lang.System.identityHashCode(item.asInstanceOf[java.lang.Object]) * -7046029254386353131L) >>> shift).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
  }
  override def locateKey(key: K): scala.Int = {
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
  override def hashCode(): scala.Int = {
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