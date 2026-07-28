package com.badlogic.gdx.math

class Vector2Test extends munit.FunSuite {
  test("testToString")({
    balticporter.runtime.Asserts.assertEquals("(-5.0,42.00055)", new com.badlogic.gdx.math.Vector2(-5.0f, 42.00055f).toString())
  })
  test("testFromString")({
    balticporter.runtime.Asserts.assertEquals(new com.badlogic.gdx.math.Vector2(-5.0f, 42.00055f), new com.badlogic.gdx.math.Vector2().fromString("(-5,42.00055)"))
  })
  test("testAngle")({
    balticporter.runtime.Asserts.assertEquals(270.0f, new com.badlogic.gdx.math.Vector2(0, -1.0f).angleDeg(), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
  })
  test("testAngleRelative")({
    balticporter.runtime.Asserts.assertEquals(270.0f, new com.badlogic.gdx.math.Vector2(0, -1.0f).angleDeg(com.badlogic.gdx.math.Vector2.X), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
  })
  test("testAngleStatic")({
    balticporter.runtime.Asserts.assertEquals(270.0f, com.badlogic.gdx.math.Vector2.angleDeg(0, -1.0f), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
  })
  test("testAngleRad")({
    balticporter.runtime.Asserts.assertEquals(-com.badlogic.gdx.math.MathUtils.HALF_PI, new com.badlogic.gdx.math.Vector2(0, -1.0f).angleRad(), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
  })
  test("testAngleRadRelative")({
    balticporter.runtime.Asserts.assertEquals(-com.badlogic.gdx.math.MathUtils.HALF_PI, new com.badlogic.gdx.math.Vector2(0, -1.0f).angleRad(com.badlogic.gdx.math.Vector2.X), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
  })
  test("testAngleRadStatic")({
    balticporter.runtime.Asserts.assertEquals(-com.badlogic.gdx.math.MathUtils.HALF_PI, com.badlogic.gdx.math.Vector2.angleRad(0, -1.0f), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
  })
}