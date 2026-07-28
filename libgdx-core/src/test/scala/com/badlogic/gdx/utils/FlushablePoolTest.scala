package com.badlogic.gdx.utils

class FlushablePoolTest {
  def initializeFlushablePoolTest1(): scala.Unit = {
    val flushablePool: com.badlogic.gdx.utils.FlushablePoolTest#FlushablePoolClass = new FlushablePoolClass()
    org.junit.Assert.assertEquals(0, flushablePool.getFree())
    org.junit.Assert.assertEquals(java.lang.Integer.MAX_VALUE, flushablePool.max)
  }
  def initializeFlushablePoolTest2(): scala.Unit = {
    val flushablePool: com.badlogic.gdx.utils.FlushablePoolTest#FlushablePoolClass = new FlushablePoolClass(10)
    org.junit.Assert.assertEquals(0, flushablePool.getFree())
    org.junit.Assert.assertEquals(java.lang.Integer.MAX_VALUE, flushablePool.max)
  }
  def initializeFlushablePoolTest3(): scala.Unit = {
    val flushablePool: com.badlogic.gdx.utils.FlushablePoolTest#FlushablePoolClass = new FlushablePoolClass(10, 10)
    org.junit.Assert.assertEquals(0, flushablePool.getFree())
    org.junit.Assert.assertEquals(10, flushablePool.max)
  }
  def obtainTest(): scala.Unit = {
    val flushablePool: com.badlogic.gdx.utils.FlushablePoolTest#FlushablePoolClass = new FlushablePoolClass(10, 10)
    flushablePool.newObject()
    org.junit.Assert.assertEquals(0, flushablePool.obtained.size)
    flushablePool.obtain()
    org.junit.Assert.assertEquals(1, flushablePool.obtained.size)
    flushablePool.flush()
    org.junit.Assert.assertEquals(0, flushablePool.obtained.size)
  }
  def flushTest(): scala.Unit = {
    val flushablePool: com.badlogic.gdx.utils.FlushablePoolTest#FlushablePoolClass = new FlushablePoolClass(10, 10)
    flushablePool.newObject()
    flushablePool.obtain()
    org.junit.Assert.assertEquals(1, flushablePool.obtained.size)
    flushablePool.flush()
    org.junit.Assert.assertEquals(0, flushablePool.obtained.size)
  }
  def freeTest(): scala.Unit = {
    val flushablePool: com.badlogic.gdx.utils.FlushablePoolTest#FlushablePoolClass = new FlushablePoolClass(10, 10)
    flushablePool.newObject()
    flushablePool.newObject()
    val element1: java.lang.String = flushablePool.obtain()
    val element2: java.lang.String = flushablePool.obtain()
    org.junit.Assert.assertTrue(flushablePool.obtained.contains(element1, true))
    org.junit.Assert.assertTrue(flushablePool.obtained.contains(element2, true))
    flushablePool.free(element2)
    org.junit.Assert.assertTrue(flushablePool.obtained.contains(element1, true))
    org.junit.Assert.assertFalse(flushablePool.obtained.contains(element2, true))
  }
  def freeAllTest(): scala.Unit = {
    val flushablePool: com.badlogic.gdx.utils.FlushablePoolTest#FlushablePoolClass = new FlushablePoolClass(5, 5)
    flushablePool.newObject()
    flushablePool.newObject()
    val element1: java.lang.String = flushablePool.obtain()
    val element2: java.lang.String = flushablePool.obtain()
    val elementArray: com.badlogic.gdx.utils.Array[java.lang.String] = new com.badlogic.gdx.utils.Array[java.lang.String]()
    elementArray.add(element1)
    elementArray.add(element2)
    org.junit.Assert.assertTrue(flushablePool.obtained.contains(element1, true))
    org.junit.Assert.assertTrue(flushablePool.obtained.contains(element2, true))
    flushablePool.freeAll(elementArray)
    org.junit.Assert.assertFalse(flushablePool.obtained.contains(element1, true))
    org.junit.Assert.assertFalse(flushablePool.obtained.contains(element2, true))
  }
  class FlushablePoolClass extends com.badlogic.gdx.utils.FlushablePool[java.lang.String] {
    def this(initialCapacity: scala.Int) = {
      this()
      this.freeObjects = new com.badlogic.gdx.utils.Array[T](false, initialCapacity).asInstanceOf[com.badlogic.gdx.utils.Array[T]]
      this.max = java.lang.Integer.MAX_VALUE
    }
    def this(initialCapacity: scala.Int, max: scala.Int) = {
      this()
      this.freeObjects = new com.badlogic.gdx.utils.Array[T](false, initialCapacity).asInstanceOf[com.badlogic.gdx.utils.Array[T]]
      this.max = max
    }
    def newObject(): java.lang.String = {
      return java.lang.Integer.toString(this.getFree())
    }
  }
  object FlushablePoolClass {
    export com.badlogic.gdx.utils.FlushablePool.*
  }
}