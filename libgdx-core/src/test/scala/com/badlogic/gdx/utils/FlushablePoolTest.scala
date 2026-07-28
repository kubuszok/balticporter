package com.badlogic.gdx.utils

class FlushablePoolTest extends munit.FunSuite {
  test("initializeFlushablePoolTest1")({
    val flushablePool: com.badlogic.gdx.utils.FlushablePoolTest#FlushablePoolClass = new FlushablePoolClass()
    assertEquals(flushablePool.getFree(), 0)
    assertEquals(flushablePool.max, java.lang.Integer.MAX_VALUE)
  })
  test("initializeFlushablePoolTest2")({
    val flushablePool: com.badlogic.gdx.utils.FlushablePoolTest#FlushablePoolClass = new FlushablePoolClass(10)
    assertEquals(flushablePool.getFree(), 0)
    assertEquals(flushablePool.max, java.lang.Integer.MAX_VALUE)
  })
  test("initializeFlushablePoolTest3")({
    val flushablePool: com.badlogic.gdx.utils.FlushablePoolTest#FlushablePoolClass = new FlushablePoolClass(10, 10)
    assertEquals(flushablePool.getFree(), 0)
    assertEquals(flushablePool.max, 10)
  })
  test("obtainTest")({
    val flushablePool: com.badlogic.gdx.utils.FlushablePoolTest#FlushablePoolClass = new FlushablePoolClass(10, 10)
    flushablePool.newObject()
    assertEquals(flushablePool.obtained.size, 0)
    flushablePool.obtain()
    assertEquals(flushablePool.obtained.size, 1)
    flushablePool.flush()
    assertEquals(flushablePool.obtained.size, 0)
  })
  test("flushTest")({
    val flushablePool: com.badlogic.gdx.utils.FlushablePoolTest#FlushablePoolClass = new FlushablePoolClass(10, 10)
    flushablePool.newObject()
    flushablePool.obtain()
    assertEquals(flushablePool.obtained.size, 1)
    flushablePool.flush()
    assertEquals(flushablePool.obtained.size, 0)
  })
  test("freeTest")({
    val flushablePool: com.badlogic.gdx.utils.FlushablePoolTest#FlushablePoolClass = new FlushablePoolClass(10, 10)
    flushablePool.newObject()
    flushablePool.newObject()
    val element1: java.lang.String = flushablePool.obtain()
    val element2: java.lang.String = flushablePool.obtain()
    assert(flushablePool.obtained.contains(element1, true))
    assert(flushablePool.obtained.contains(element2, true))
    flushablePool.free(element2)
    assert(flushablePool.obtained.contains(element1, true))
    assertEquals(flushablePool.obtained.contains(element2, true), false)
  })
  test("freeAllTest")({
    val flushablePool: com.badlogic.gdx.utils.FlushablePoolTest#FlushablePoolClass = new FlushablePoolClass(5, 5)
    flushablePool.newObject()
    flushablePool.newObject()
    val element1: java.lang.String = flushablePool.obtain()
    val element2: java.lang.String = flushablePool.obtain()
    val elementArray: com.badlogic.gdx.utils.Array[java.lang.String] = new com.badlogic.gdx.utils.Array[java.lang.String]()
    elementArray.add(element1)
    elementArray.add(element2)
    assert(flushablePool.obtained.contains(element1, true))
    assert(flushablePool.obtained.contains(element2, true))
    flushablePool.freeAll(elementArray)
    assertEquals(flushablePool.obtained.contains(element1, true), false)
    assertEquals(flushablePool.obtained.contains(element2, true), false)
  })
  class FlushablePoolClass extends com.badlogic.gdx.utils.FlushablePool[java.lang.String] {
    def this(initialCapacity: scala.Int) = {
      this()
      this.freeObjects = new com.badlogic.gdx.utils.Array[java.lang.String](false, initialCapacity).asInstanceOf[com.badlogic.gdx.utils.Array[java.lang.String]]
      this.max = java.lang.Integer.MAX_VALUE
    }
    def this(initialCapacity: scala.Int, max: scala.Int) = {
      this()
      this.freeObjects = new com.badlogic.gdx.utils.Array[java.lang.String](false, initialCapacity).asInstanceOf[com.badlogic.gdx.utils.Array[java.lang.String]]
      this.max = max
    }
    @java.lang.Override
    override def newObject(): java.lang.String = {
      return java.lang.Integer.toString(this.getFree())
    }
  }
  object FlushablePoolClass {
    export com.badlogic.gdx.utils.FlushablePool.*
  }
}