package com.badlogic.gdx.utils

class AtomicQueueTest extends balticporter.runtime.PortedSuite {
  testCase("PutTest", {
    val atomicQueue: com.badlogic.gdx.utils.AtomicQueue[java.lang.Integer] = new com.badlogic.gdx.utils.AtomicQueue[java.lang.Integer](2)
    assertTrue(atomicQueue.put(1.asInstanceOf[java.lang.Integer]))
    assertFalse(atomicQueue.put(2.asInstanceOf[java.lang.Integer]))
  })
  testCase("PullTest", {
    val atomicQueue: com.badlogic.gdx.utils.AtomicQueue[java.lang.Integer] = new com.badlogic.gdx.utils.AtomicQueue[java.lang.Integer](3)
    atomicQueue.put(1.asInstanceOf[java.lang.Integer])
    atomicQueue.put(2.asInstanceOf[java.lang.Integer])
    atomicQueue.put(3.asInstanceOf[java.lang.Integer])
    assertEquals(1, atomicQueue.poll().asInstanceOf[scala.Int].longValue())
    assertEquals(2, atomicQueue.poll().asInstanceOf[scala.Int].longValue())
    assertNull(atomicQueue.poll())
  })
  testCase("LoopAroundTest", {
    val atomicQueue: com.badlogic.gdx.utils.AtomicQueue[java.lang.Integer] = new com.badlogic.gdx.utils.AtomicQueue[java.lang.Integer](2)
    assertTrue(atomicQueue.put(1.asInstanceOf[java.lang.Integer]))
    assertFalse(atomicQueue.put(2.asInstanceOf[java.lang.Integer]))
    assertEquals(1, atomicQueue.poll().asInstanceOf[scala.Int].longValue())
    assertTrue(atomicQueue.put(2.asInstanceOf[java.lang.Integer]))
    assertEquals(2, atomicQueue.poll().asInstanceOf[scala.Int].longValue())
  })
}