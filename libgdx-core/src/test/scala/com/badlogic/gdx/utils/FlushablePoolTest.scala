package com.badlogic.gdx.utils

class FlushablePoolTest extends munit.FunSuite {
  test("initializeFlushablePoolTest1")({
    val flushablePool: com.badlogic.gdx.utils.FlushablePoolTest#FlushablePoolClass = new FlushablePoolClass()
    balticporter.runtime.Asserts.assertEquals(0, flushablePool.getFree())
    balticporter.runtime.Asserts.assertEquals(java.lang.Integer.MAX_VALUE, flushablePool.max)
  })
  test("initializeFlushablePoolTest2")({
    val flushablePool: com.badlogic.gdx.utils.FlushablePoolTest#FlushablePoolClass = new FlushablePoolClass(10)
    balticporter.runtime.Asserts.assertEquals(0, flushablePool.getFree())
    balticporter.runtime.Asserts.assertEquals(java.lang.Integer.MAX_VALUE, flushablePool.max)
  })
  test("initializeFlushablePoolTest3")({
    val flushablePool: com.badlogic.gdx.utils.FlushablePoolTest#FlushablePoolClass = new FlushablePoolClass(10, 10)
    balticporter.runtime.Asserts.assertEquals(0, flushablePool.getFree())
    balticporter.runtime.Asserts.assertEquals(10, flushablePool.max)
  })
  test("obtainTest")({
    val flushablePool: com.badlogic.gdx.utils.FlushablePoolTest#FlushablePoolClass = new FlushablePoolClass(10, 10)
    flushablePool.newObject()
    balticporter.runtime.Asserts.assertEquals(0, flushablePool.obtained.size)
    flushablePool.obtain()
    balticporter.runtime.Asserts.assertEquals(1, flushablePool.obtained.size)
    flushablePool.flush()
    balticporter.runtime.Asserts.assertEquals(0, flushablePool.obtained.size)
  })
  test("flushTest")({
    val flushablePool: com.badlogic.gdx.utils.FlushablePoolTest#FlushablePoolClass = new FlushablePoolClass(10, 10)
    flushablePool.newObject()
    flushablePool.obtain()
    balticporter.runtime.Asserts.assertEquals(1, flushablePool.obtained.size)
    flushablePool.flush()
    balticporter.runtime.Asserts.assertEquals(0, flushablePool.obtained.size)
  })
  test("freeTest")({
    val flushablePool: com.badlogic.gdx.utils.FlushablePoolTest#FlushablePoolClass = new FlushablePoolClass(10, 10)
    flushablePool.newObject()
    flushablePool.newObject()
    val element1: java.lang.String = flushablePool.obtain()
    val element2: java.lang.String = flushablePool.obtain()
    balticporter.runtime.Asserts.assertTrue(flushablePool.obtained.contains(element1, true))
    balticporter.runtime.Asserts.assertTrue(flushablePool.obtained.contains(element2, true))
    flushablePool.free(element2)
    balticporter.runtime.Asserts.assertTrue(flushablePool.obtained.contains(element1, true))
    balticporter.runtime.Asserts.assertFalse(flushablePool.obtained.contains(element2, true))
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
    balticporter.runtime.Asserts.assertTrue(flushablePool.obtained.contains(element1, true))
    balticporter.runtime.Asserts.assertTrue(flushablePool.obtained.contains(element2, true))
    flushablePool.freeAll(elementArray)
    balticporter.runtime.Asserts.assertFalse(flushablePool.obtained.contains(element1, true))
    balticporter.runtime.Asserts.assertFalse(flushablePool.obtained.contains(element2, true))
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