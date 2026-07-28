package com.badlogic.gdx.math

class Vector3Test extends munit.FunSuite {
  test("testToString")({
    assertEquals(new com.badlogic.gdx.math.Vector3(-5.0f, 42.00055f, 44444.32f).toString(), "(-5.0,42.00055,44444.32)")
  })
  test("testFromString")({
    assertEquals(new com.badlogic.gdx.math.Vector3().fromString("(-5,42.00055,44444.32)"), new com.badlogic.gdx.math.Vector3(-5.0f, 42.00055f, 44444.32f))
  })
}