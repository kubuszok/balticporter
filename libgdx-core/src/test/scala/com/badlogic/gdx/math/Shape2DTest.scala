package com.badlogic.gdx.math

class Shape2DTest extends munit.FunSuite {
  test("testCircle")({
    val c1: com.badlogic.gdx.math.Circle = new com.badlogic.gdx.math.Circle(0, 0, 1)
    val c2: com.badlogic.gdx.math.Circle = new com.badlogic.gdx.math.Circle(0, 0, 1)
    val c3: com.badlogic.gdx.math.Circle = new com.badlogic.gdx.math.Circle(2, 0, 1)
    val c4: com.badlogic.gdx.math.Circle = new com.badlogic.gdx.math.Circle(0, 0, 2)
    assert(c1.overlaps(c1))
    assert(c1.overlaps(c2))
    assertEquals(c1.overlaps(c3), false)
    assert(c1.overlaps(c4))
    assert(c4.overlaps(c1))
    assert(c1.contains(0, 1))
    assertEquals(c1.contains(0, 2), false)
    assert(c1.contains(c1))
    assertEquals(c1.contains(c4), false)
    assert(c4.contains(c1))
  })
  test("testRectangle")({
    val r1: com.badlogic.gdx.math.Rectangle = new com.badlogic.gdx.math.Rectangle(0, 0, 1, 1)
    val r2: com.badlogic.gdx.math.Rectangle = new com.badlogic.gdx.math.Rectangle(1, 0, 2, 1)
    assert(r1.overlaps(r1))
    assertEquals(r1.overlaps(r2), false)
    assert(r1.contains(0, 0))
  })
}