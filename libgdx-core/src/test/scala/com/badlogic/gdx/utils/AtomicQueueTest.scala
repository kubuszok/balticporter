package com.badlogic.gdx.utils

class AtomicQueueTest {
  @org.junit.Test
  def PutTest(): scala.Unit = {
    val atomicQueue: com.badlogic.gdx.utils.AtomicQueue[java.lang.Integer] = new com.badlogic.gdx.utils.AtomicQueue[java.lang.Integer](2)
    org.junit.Assert.assertTrue(atomicQueue.put(1.asInstanceOf[java.lang.Integer]))
    org.junit.Assert.assertFalse(atomicQueue.put(2.asInstanceOf[java.lang.Integer]))
  }
  @org.junit.Test
  def PullTest(): scala.Unit = {
    val atomicQueue: com.badlogic.gdx.utils.AtomicQueue[java.lang.Integer] = new com.badlogic.gdx.utils.AtomicQueue[java.lang.Integer](3)
    atomicQueue.put(1.asInstanceOf[java.lang.Integer])
    atomicQueue.put(2.asInstanceOf[java.lang.Integer])
    atomicQueue.put(3.asInstanceOf[java.lang.Integer])
    org.junit.Assert.assertEquals(1, atomicQueue.poll().asInstanceOf[scala.Int].longValue())
    org.junit.Assert.assertEquals(2, atomicQueue.poll().asInstanceOf[scala.Int].longValue())
    org.junit.Assert.assertNull(atomicQueue.poll())
  }
  @org.junit.Test
  def LoopAroundTest(): scala.Unit = {
    val atomicQueue: com.badlogic.gdx.utils.AtomicQueue[java.lang.Integer] = new com.badlogic.gdx.utils.AtomicQueue[java.lang.Integer](2)
    org.junit.Assert.assertTrue(atomicQueue.put(1.asInstanceOf[java.lang.Integer]))
    org.junit.Assert.assertFalse(atomicQueue.put(2.asInstanceOf[java.lang.Integer]))
    org.junit.Assert.assertEquals(1, atomicQueue.poll().asInstanceOf[scala.Int].longValue())
    org.junit.Assert.assertTrue(atomicQueue.put(2.asInstanceOf[java.lang.Integer]))
    org.junit.Assert.assertEquals(2, atomicQueue.poll().asInstanceOf[scala.Int].longValue())
  }
}