package com.badlogic.gdx.math

class Shape2DTest {
  @org.junit.Test
  def testCircle(): scala.Unit = {
    val c1: com.badlogic.gdx.math.Circle = new com.badlogic.gdx.math.Circle(0, 0, 1)
    val c2: com.badlogic.gdx.math.Circle = new com.badlogic.gdx.math.Circle(0, 0, 1)
    val c3: com.badlogic.gdx.math.Circle = new com.badlogic.gdx.math.Circle(2, 0, 1)
    val c4: com.badlogic.gdx.math.Circle = new com.badlogic.gdx.math.Circle(0, 0, 2)
    org.junit.Assert.assertTrue(c1.overlaps(c1))
    org.junit.Assert.assertTrue(c1.overlaps(c2))
    org.junit.Assert.assertFalse(c1.overlaps(c3))
    org.junit.Assert.assertTrue(c1.overlaps(c4))
    org.junit.Assert.assertTrue(c4.overlaps(c1))
    org.junit.Assert.assertTrue(c1.contains(0, 1))
    org.junit.Assert.assertFalse(c1.contains(0, 2))
    org.junit.Assert.assertTrue(c1.contains(c1))
    org.junit.Assert.assertFalse(c1.contains(c4))
    org.junit.Assert.assertTrue(c4.contains(c1))
  }
  @org.junit.Test
  def testRectangle(): scala.Unit = {
    val r1: com.badlogic.gdx.math.Rectangle = new com.badlogic.gdx.math.Rectangle(0, 0, 1, 1)
    val r2: com.badlogic.gdx.math.Rectangle = new com.badlogic.gdx.math.Rectangle(1, 0, 2, 1)
    org.junit.Assert.assertTrue(r1.overlaps(r1))
    org.junit.Assert.assertFalse(r1.overlaps(r2))
    org.junit.Assert.assertTrue(r1.contains(0, 0))
  }
}