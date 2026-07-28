package com.badlogic.gdx.utils

class PoolManager {
  private final val typePools: com.badlogic.gdx.utils.ObjectMap[java.lang.Class[?], com.badlogic.gdx.utils.Pool[?]] = new com.badlogic.gdx.utils.ObjectMap[java.lang.Class[?], com.badlogic.gdx.utils.Pool[?]]()
  def addPool[T <: java.lang.Object](poolClass: java.lang.Class[T], poolSupplier: com.badlogic.gdx.utils.DefaultPool.PoolSupplier[T]): scala.Unit = {
    this.addPool(poolClass, new com.badlogic.gdx.utils.DefaultPool[T](poolSupplier))
  }
  def addPool[T <: java.lang.Object](poolClass: java.lang.Class[T], pool: com.badlogic.gdx.utils.Pool[T]): scala.Unit = {
    val oldPool: com.badlogic.gdx.utils.Pool[?] = this.typePools.put(poolClass, pool)
    if (oldPool != null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException(((((("Attempt to add pool with already existing class: " + poolClass) + ", register using PoolManager#addPool(") + poolClass.getSimpleName()) + ", ") + poolClass.getSimpleName()) + "::new)")
    } else ()
  }
  def getPool[T <: java.lang.Object](clazz: java.lang.Class[T]): com.badlogic.gdx.utils.Pool[T] = {
    val pool: com.badlogic.gdx.utils.Pool[T] = this.typePools.get(clazz).asInstanceOf[com.badlogic.gdx.utils.Pool[T]].asInstanceOf[com.badlogic.gdx.utils.Pool[T]]
    if (pool == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException(((("Attempt to get pool with unknown class: " + clazz) + ", register using PoolManager#addPool(") + clazz.getSimpleName()) + "::new)")
    } else ()
    return pool
  }
  def getPoolOrNull[T <: java.lang.Object](clazz: java.lang.Class[T]): com.badlogic.gdx.utils.Pool[T] = {
    return this.typePools.get(clazz).asInstanceOf[com.badlogic.gdx.utils.Pool[T]].asInstanceOf[com.badlogic.gdx.utils.Pool[T]]
  }
  def hasPool(clazz: java.lang.Class[?]): scala.Boolean = {
    return this.typePools.containsKey(clazz)
  }
  def obtain[T <: java.lang.Object](clazz: java.lang.Class[T]): T = {
    val pool: com.badlogic.gdx.utils.Pool[T] = this.typePools.get(clazz).asInstanceOf[com.badlogic.gdx.utils.Pool[T]].asInstanceOf[com.badlogic.gdx.utils.Pool[T]]
    if (pool == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException(((("Attempt to get pooled object with unknown class: " + clazz) + ", register using PoolManager#addPool(") + clazz.getSimpleName()) + "::new)")
    } else ()
    return pool.obtain().asInstanceOf[T]
  }
  def obtainOrNull[T <: java.lang.Object](clazz: java.lang.Class[T]): T = {
    val pool: com.badlogic.gdx.utils.Pool[T] = this.typePools.get(clazz).asInstanceOf[com.badlogic.gdx.utils.Pool[T]].asInstanceOf[com.badlogic.gdx.utils.Pool[T]]
    if (pool == null) {
      return null.asInstanceOf[T]
    } else ()
    return pool.obtain().asInstanceOf[T]
  }
  def free[T <: java.lang.Object](`object`: T): scala.Unit = {
    val pool: com.badlogic.gdx.utils.Pool[T] = this.typePools.get(`object`.getClass()).asInstanceOf[com.badlogic.gdx.utils.Pool[T]].asInstanceOf[com.badlogic.gdx.utils.Pool[T]]
    if (pool == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException(((("Attempt to free pooled object with unknown class: " + `object`.getClass()) + ", register using PoolManager#addPool(") + `object`.getClass().getSimpleName()) + "::new)")
    } else ()
    pool.free(`object`)
  }
  def clear(): scala.Unit = {
    for (pool <- this.typePools.values()) {
      pool.clear()
    }
  }
}