package com.badlogic.gdx.utils

class AtomicQueueTest extends balticporter.runtime.PortedSuite {
  testCase("PutTest", {
    val atomicQueue: com.badlogic.gdx.utils.AtomicQueue[java.lang.Integer] = new com.badlogic.gdx.utils.AtomicQueue[java.lang.Integer](2)
    balticporter.runtime.Asserts.assertTrue(atomicQueue.put(1.asInstanceOf[java.lang.Integer]))
    balticporter.runtime.Asserts.assertFalse(atomicQueue.put(2.asInstanceOf[java.lang.Integer]))
  })
  testCase("PullTest", {
    val atomicQueue: com.badlogic.gdx.utils.AtomicQueue[java.lang.Integer] = new com.badlogic.gdx.utils.AtomicQueue[java.lang.Integer](3)
    atomicQueue.put(1.asInstanceOf[java.lang.Integer])
    atomicQueue.put(2.asInstanceOf[java.lang.Integer])
    atomicQueue.put(3.asInstanceOf[java.lang.Integer])
    balticporter.runtime.Asserts.assertEquals(1, atomicQueue.poll().asInstanceOf[scala.Int].longValue())
    balticporter.runtime.Asserts.assertEquals(2, atomicQueue.poll().asInstanceOf[scala.Int].longValue())
    balticporter.runtime.Asserts.assertNull(atomicQueue.poll())
  })
  testCase("LoopAroundTest", {
    val atomicQueue: com.badlogic.gdx.utils.AtomicQueue[java.lang.Integer] = new com.badlogic.gdx.utils.AtomicQueue[java.lang.Integer](2)
    balticporter.runtime.Asserts.assertTrue(atomicQueue.put(1.asInstanceOf[java.lang.Integer]))
    balticporter.runtime.Asserts.assertFalse(atomicQueue.put(2.asInstanceOf[java.lang.Integer]))
    balticporter.runtime.Asserts.assertEquals(1, atomicQueue.poll().asInstanceOf[scala.Int].longValue())
    balticporter.runtime.Asserts.assertTrue(atomicQueue.put(2.asInstanceOf[java.lang.Integer]))
    balticporter.runtime.Asserts.assertEquals(2, atomicQueue.poll().asInstanceOf[scala.Int].longValue())
  })
}