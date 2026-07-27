package com.badlogic.gdx.utils

object Pools {
  private final val typePools: com.badlogic.gdx.utils.ObjectMap[java.lang.Class[?], com.badlogic.gdx.utils.Pool[?]] = new com.badlogic.gdx.utils.ObjectMap[java.lang.Class[?], com.badlogic.gdx.utils.Pool[?]]()
  var WARN_ON_REFLECTION_POOL_CREATION: scala.Boolean = true
  var THROW_ON_REFLECTION_POOL_CREATION: scala.Boolean = false
  def get[T](`type`: java.lang.Class[T], max: scala.Int): com.badlogic.gdx.utils.Pool[T] = {
    var pool: com.badlogic.gdx.utils.Pool[?] = Pools.typePools.get(`type`)
    if (pool == null) {
      if (Pools.THROW_ON_REFLECTION_POOL_CREATION) {
        throw new java.lang.RuntimeException(("Please manually define a Pool for " + `type`) + " by calling Pools#set before calling Pools#get")
      } else ()
      if (Pools.WARN_ON_REFLECTION_POOL_CREATION && (com.badlogic.gdx.Gdx.app != null)) {
        com.badlogic.gdx.Gdx.app.error("Pools", ("Please manually define a Pool for " + `type`) + " by calling Pools#set before calling Pools#get")
      } else ()
      pool = new com.badlogic.gdx.utils.ReflectionPool(`type`, 4, max)
      Pools.typePools.put(`type`, pool)
    } else ()
    return pool
  }
  def get[T](`type`: java.lang.Class[T]): com.badlogic.gdx.utils.Pool[T] = {
    return Pools.get(`type`, 100)
  }
  def set[T](`type`: java.lang.Class[T], pool: com.badlogic.gdx.utils.Pool[T]): scala.Unit = {
    Pools.typePools.put(`type`, pool)
  }
  def set[T](poolTypeSupplier: com.badlogic.gdx.utils.DefaultPool.PoolSupplier[T], max: scala.Int): scala.Unit = {
    Pools.set(poolTypeSupplier.get().getClass().asInstanceOf[java.lang.Class[T]], new com.badlogic.gdx.utils.DefaultPool[T](poolTypeSupplier, 4, max))
  }
  def set[T](poolTypeSupplier: com.badlogic.gdx.utils.DefaultPool.PoolSupplier[T]): scala.Unit = {
    Pools.set(poolTypeSupplier, 100)
  }
  def obtain[T](`type`: java.lang.Class[T]): T = {
    return Pools.get(`type`).obtain().asInstanceOf[T]
  }
  def free(`object`: java.lang.Object): scala.Unit = {
    if (`object` == null) {
      throw new java.lang.IllegalArgumentException("object cannot be null.")
    } else ()
    val pool: com.badlogic.gdx.utils.Pool[?] = Pools.typePools.get(`object`.getClass())
    if (pool == null) {
      return
    } else ()
    pool.asInstanceOf[com.badlogic.gdx.utils.Pool[java.lang.Object]].free(`object`.asInstanceOf[java.lang.Object])
  }
  def freeAll(objects: com.badlogic.gdx.utils.Array[?]): scala.Unit = {
    Pools.freeAll(objects, false)
  }
  def freeAll(objects: com.badlogic.gdx.utils.Array[?], samePool: scala.Boolean): scala.Unit = {
    if (objects == null) {
      throw new java.lang.IllegalArgumentException("objects cannot be null.")
    } else ()
    var pool: com.badlogic.gdx.utils.Pool[?] = null;
    { var i: scala.Int = 0; val n: scala.Int = objects.size; while (i < n) { {
      val `object`: java.lang.Object = objects.get(i)
      if (`object` == null) {
        /* continue */ ()
      } else ()
      if (pool == null) {
        pool = Pools.typePools.get(`object`.getClass())
        if (pool == null) {
          /* continue */ ()
        } else ()
      } else ()
      pool.asInstanceOf[com.badlogic.gdx.utils.Pool[java.lang.Object]].free(`object`.asInstanceOf[java.lang.Object])
      if (!samePool) {
        pool = null
      } else ()
    }; i = i + 1 } }
  }
}