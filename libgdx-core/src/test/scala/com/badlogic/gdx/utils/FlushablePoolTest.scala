package com.badlogic.gdx.utils

class FlushablePoolTest extends balticporter.runtime.PortedSuite {
  testCase("initializeFlushablePoolTest1", {
    val flushablePool: com.badlogic.gdx.utils.FlushablePoolTest#FlushablePoolClass = new FlushablePoolClass()
    assertEquals(0, flushablePool.getFree())
    assertEquals(java.lang.Integer.MAX_VALUE, flushablePool.max)
  })
  testCase("initializeFlushablePoolTest2", {
    val flushablePool: com.badlogic.gdx.utils.FlushablePoolTest#FlushablePoolClass = new FlushablePoolClass(10)
    assertEquals(0, flushablePool.getFree())
    assertEquals(java.lang.Integer.MAX_VALUE, flushablePool.max)
  })
  testCase("initializeFlushablePoolTest3", {
    val flushablePool: com.badlogic.gdx.utils.FlushablePoolTest#FlushablePoolClass = new FlushablePoolClass(10, 10)
    assertEquals(0, flushablePool.getFree())
    assertEquals(10, flushablePool.max)
  })
  testCase("obtainTest", {
    val flushablePool: com.badlogic.gdx.utils.FlushablePoolTest#FlushablePoolClass = new FlushablePoolClass(10, 10)
    flushablePool.newObject()
    assertEquals(0, flushablePool.obtained.size)
    flushablePool.obtain()
    assertEquals(1, flushablePool.obtained.size)
    flushablePool.flush()
    assertEquals(0, flushablePool.obtained.size)
  })
  testCase("flushTest", {
    val flushablePool: com.badlogic.gdx.utils.FlushablePoolTest#FlushablePoolClass = new FlushablePoolClass(10, 10)
    flushablePool.newObject()
    flushablePool.obtain()
    assertEquals(1, flushablePool.obtained.size)
    flushablePool.flush()
    assertEquals(0, flushablePool.obtained.size)
  })
  testCase("freeTest", {
    val flushablePool: com.badlogic.gdx.utils.FlushablePoolTest#FlushablePoolClass = new FlushablePoolClass(10, 10)
    flushablePool.newObject()
    flushablePool.newObject()
    val element1: java.lang.String = flushablePool.obtain()
    val element2: java.lang.String = flushablePool.obtain()
    assertTrue(flushablePool.obtained.contains(element1, true))
    assertTrue(flushablePool.obtained.contains(element2, true))
    flushablePool.free(element2)
    assertTrue(flushablePool.obtained.contains(element1, true))
    assertFalse(flushablePool.obtained.contains(element2, true))
  })
  testCase("freeAllTest", {
    val flushablePool: com.badlogic.gdx.utils.FlushablePoolTest#FlushablePoolClass = new FlushablePoolClass(5, 5)
    flushablePool.newObject()
    flushablePool.newObject()
    val element1: java.lang.String = flushablePool.obtain()
    val element2: java.lang.String = flushablePool.obtain()
    val elementArray: com.badlogic.gdx.utils.Array[java.lang.String] = new com.badlogic.gdx.utils.Array[java.lang.String]()
    elementArray.add(element1)
    elementArray.add(element2)
    assertTrue(flushablePool.obtained.contains(element1, true))
    assertTrue(flushablePool.obtained.contains(element2, true))
    flushablePool.freeAll(elementArray)
    assertFalse(flushablePool.obtained.contains(element1, true))
    assertFalse(flushablePool.obtained.contains(element2, true))
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