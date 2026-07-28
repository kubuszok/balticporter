package com.badlogic.gdx.math

class Vector2Test extends munit.FunSuite {
  test("testToString")({
    assertEquals(new com.badlogic.gdx.math.Vector2(-5.0f, 42.00055f).toString(), "(-5.0,42.00055)")
  })
  test("testFromString")({
    assertEquals(new com.badlogic.gdx.math.Vector2().fromString("(-5,42.00055)"), new com.badlogic.gdx.math.Vector2(-5.0f, 42.00055f))
  })
  test("testAngle")({
    assertEquals(com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR, new com.badlogic.gdx.math.Vector2(0, -1.0f).angleDeg(), 270.0f)
  })
  test("testAngleRelative")({
    assertEquals(com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR, new com.badlogic.gdx.math.Vector2(0, -1.0f).angleDeg(com.badlogic.gdx.math.Vector2.X), 270.0f)
  })
  test("testAngleStatic")({
    assertEquals(com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR, com.badlogic.gdx.math.Vector2.angleDeg(0, -1.0f), 270.0f)
  })
  test("testAngleRad")({
    assertEquals(com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR, new com.badlogic.gdx.math.Vector2(0, -1.0f).angleRad(), -com.badlogic.gdx.math.MathUtils.HALF_PI)
  })
  test("testAngleRadRelative")({
    assertEquals(com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR, new com.badlogic.gdx.math.Vector2(0, -1.0f).angleRad(com.badlogic.gdx.math.Vector2.X), -com.badlogic.gdx.math.MathUtils.HALF_PI)
  })
  test("testAngleRadStatic")({
    assertEquals(com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR, com.badlogic.gdx.math.Vector2.angleRad(0, -1.0f), -com.badlogic.gdx.math.MathUtils.HALF_PI)
  })
}