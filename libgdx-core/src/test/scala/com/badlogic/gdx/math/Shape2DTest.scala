package com.badlogic.gdx.math

class Shape2DTest extends balticporter.runtime.PortedSuite {
  testCase("testCircle", {
    val c1: com.badlogic.gdx.math.Circle = new com.badlogic.gdx.math.Circle(0, 0, 1)
    val c2: com.badlogic.gdx.math.Circle = new com.badlogic.gdx.math.Circle(0, 0, 1)
    val c3: com.badlogic.gdx.math.Circle = new com.badlogic.gdx.math.Circle(2, 0, 1)
    val c4: com.badlogic.gdx.math.Circle = new com.badlogic.gdx.math.Circle(0, 0, 2)
    balticporter.runtime.Asserts.assertTrue(c1.overlaps(c1))
    balticporter.runtime.Asserts.assertTrue(c1.overlaps(c2))
    balticporter.runtime.Asserts.assertFalse(c1.overlaps(c3))
    balticporter.runtime.Asserts.assertTrue(c1.overlaps(c4))
    balticporter.runtime.Asserts.assertTrue(c4.overlaps(c1))
    balticporter.runtime.Asserts.assertTrue(c1.contains(0, 1))
    balticporter.runtime.Asserts.assertFalse(c1.contains(0, 2))
    balticporter.runtime.Asserts.assertTrue(c1.contains(c1))
    balticporter.runtime.Asserts.assertFalse(c1.contains(c4))
    balticporter.runtime.Asserts.assertTrue(c4.contains(c1))
  })
  testCase("testRectangle", {
    val r1: com.badlogic.gdx.math.Rectangle = new com.badlogic.gdx.math.Rectangle(0, 0, 1, 1)
    val r2: com.badlogic.gdx.math.Rectangle = new com.badlogic.gdx.math.Rectangle(1, 0, 2, 1)
    balticporter.runtime.Asserts.assertTrue(r1.overlaps(r1))
    balticporter.runtime.Asserts.assertFalse(r1.overlaps(r2))
    balticporter.runtime.Asserts.assertTrue(r1.contains(0, 0))
  })
}