package com.badlogic.gdx.math

class Vector4Test extends munit.FunSuite {
  test("testToString")({
    assertEquals(new com.badlogic.gdx.math.Vector4(-5.0f, 42.00055f, 44444.32f, -1.975f).toString(), "(-5.0,42.00055,44444.32,-1.975)")
  })
  test("testFromString")({
    assertEquals(new com.badlogic.gdx.math.Vector4().fromString("(-5,42.00055,44444.32,-1.9750)"), new com.badlogic.gdx.math.Vector4(-5.0f, 42.00055f, 44444.32f, -1.975f))
  })
}