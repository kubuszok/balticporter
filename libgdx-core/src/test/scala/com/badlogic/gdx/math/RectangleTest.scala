package com.badlogic.gdx.math

class RectangleTest extends munit.FunSuite {
  test("testToString")({
    assertEquals(new com.badlogic.gdx.math.Rectangle(5.0f, -4.1f, 0.03f, -0.02f).toString(), "[5.0,-4.1,0.03,-0.02]")
  })
  test("testFromString")({
    assertEquals(new com.badlogic.gdx.math.Rectangle().fromString("[5.0,-4.1,0.03,-0.02]"), new com.badlogic.gdx.math.Rectangle(5.0f, -4.1f, 0.03f, -0.02f))
  })
}