package com.badlogic.gdx.utils

class AtomicQueueTest extends munit.FunSuite {
  test("PutTest")({
    val atomicQueue: com.badlogic.gdx.utils.AtomicQueue[java.lang.Integer] = new com.badlogic.gdx.utils.AtomicQueue[java.lang.Integer](2)
    assert(atomicQueue.put(1.asInstanceOf[java.lang.Integer]))
    assertEquals(atomicQueue.put(2.asInstanceOf[java.lang.Integer]), false)
  })
  test("PullTest")({
    val atomicQueue: com.badlogic.gdx.utils.AtomicQueue[java.lang.Integer] = new com.badlogic.gdx.utils.AtomicQueue[java.lang.Integer](3)
    atomicQueue.put(1.asInstanceOf[java.lang.Integer])
    atomicQueue.put(2.asInstanceOf[java.lang.Integer])
    atomicQueue.put(3.asInstanceOf[java.lang.Integer])
    assertEquals(atomicQueue.poll().asInstanceOf[scala.Int].longValue(), 1)
    assertEquals(atomicQueue.poll().asInstanceOf[scala.Int].longValue(), 2)
    assertEquals(atomicQueue.poll(), null)
  })
  test("LoopAroundTest")({
    val atomicQueue: com.badlogic.gdx.utils.AtomicQueue[java.lang.Integer] = new com.badlogic.gdx.utils.AtomicQueue[java.lang.Integer](2)
    assert(atomicQueue.put(1.asInstanceOf[java.lang.Integer]))
    assertEquals(atomicQueue.put(2.asInstanceOf[java.lang.Integer]), false)
    assertEquals(atomicQueue.poll().asInstanceOf[scala.Int].longValue(), 1)
    assert(atomicQueue.put(2.asInstanceOf[java.lang.Integer]))
    assertEquals(atomicQueue.poll().asInstanceOf[scala.Int].longValue(), 2)
  })
}