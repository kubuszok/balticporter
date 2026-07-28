package com.badlogic.gdx.utils

class BitsTest {
  def testHashcodeAndEquals(): scala.Unit = {
    val b1: com.badlogic.gdx.utils.Bits = new com.badlogic.gdx.utils.Bits()
    val b2: com.badlogic.gdx.utils.Bits = new com.badlogic.gdx.utils.Bits()
    b1.set(1)
    b2.set(1)
    org.junit.Assert.assertEquals(b1.hashCode(), b2.hashCode())
    org.junit.Assert.assertTrue(b1.equals(b2))
    b2.set(420)
    b2.clear(420)
    org.junit.Assert.assertEquals(b1.hashCode(), b2.hashCode())
    org.junit.Assert.assertTrue(b1.equals(b2))
    b1.set(810)
    b1.clear(810)
    org.junit.Assert.assertEquals(b1.hashCode(), b2.hashCode())
    org.junit.Assert.assertTrue(b1.equals(b2))
  }
  def testXor(): scala.Unit = {
    val b1: com.badlogic.gdx.utils.Bits = new com.badlogic.gdx.utils.Bits()
    val b2: com.badlogic.gdx.utils.Bits = new com.badlogic.gdx.utils.Bits()
    b2.set(200)
    b1.xor(b2)
    org.junit.Assert.assertTrue(b1.get(200))
    b1.set(1024)
    b2.xor(b1)
    org.junit.Assert.assertTrue(b2.get(1024))
  }
  def testOr(): scala.Unit = {
    val b1: com.badlogic.gdx.utils.Bits = new com.badlogic.gdx.utils.Bits()
    val b2: com.badlogic.gdx.utils.Bits = new com.badlogic.gdx.utils.Bits()
    b2.set(200)
    b1.or(b2)
    org.junit.Assert.assertTrue(b1.get(200))
    b1.set(1024)
    b2.or(b1)
    org.junit.Assert.assertTrue(b2.get(1024))
  }
  def testAnd(): scala.Unit = {
    val b1: com.badlogic.gdx.utils.Bits = new com.badlogic.gdx.utils.Bits()
    val b2: com.badlogic.gdx.utils.Bits = new com.badlogic.gdx.utils.Bits()
    b2.set(200)
    b2.and(b1)
    org.junit.Assert.assertFalse(b2.get(200))
    b1.set(400)
    b1.and(b2)
    org.junit.Assert.assertFalse(b1.get(400))
  }
  def testCopyConstructor(): scala.Unit = {
    val b1: com.badlogic.gdx.utils.Bits = new com.badlogic.gdx.utils.Bits()
    b1.set(50)
    b1.set(100)
    b1.set(150)
    val b2: com.badlogic.gdx.utils.Bits = new com.badlogic.gdx.utils.Bits(b1)
    org.junit.Assert.assertTrue(b1 != b2)
    org.junit.Assert.assertTrue(b1.containsAll(b2))
    org.junit.Assert.assertTrue(b2.containsAll(b1))
    org.junit.Assert.assertTrue(b1.equals(b2))
  }
}