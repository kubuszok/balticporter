package com.badlogic.gdx.math

class Vector2Test extends balticporter.runtime.PortedSuite {
  testCase("testToString", {
    org.junit.Assert.assertEquals("(-5.0,42.00055)", new com.badlogic.gdx.math.Vector2(-5.0f, 42.00055f).toString())
  })
  testCase("testFromString", {
    org.junit.Assert.assertEquals(new com.badlogic.gdx.math.Vector2(-5.0f, 42.00055f), new com.badlogic.gdx.math.Vector2().fromString("(-5,42.00055)"))
  })
  testCase("testAngle", {
    org.junit.Assert.assertEquals(270.0f, new com.badlogic.gdx.math.Vector2(0, -1.0f).angleDeg(), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
  })
  testCase("testAngleRelative", {
    org.junit.Assert.assertEquals(270.0f, new com.badlogic.gdx.math.Vector2(0, -1.0f).angleDeg(com.badlogic.gdx.math.Vector2.X), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
  })
  testCase("testAngleStatic", {
    org.junit.Assert.assertEquals(270.0f, com.badlogic.gdx.math.Vector2.angleDeg(0, -1.0f), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
  })
  testCase("testAngleRad", {
    org.junit.Assert.assertEquals(-com.badlogic.gdx.math.MathUtils.HALF_PI, new com.badlogic.gdx.math.Vector2(0, -1.0f).angleRad(), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
  })
  testCase("testAngleRadRelative", {
    org.junit.Assert.assertEquals(-com.badlogic.gdx.math.MathUtils.HALF_PI, new com.badlogic.gdx.math.Vector2(0, -1.0f).angleRad(com.badlogic.gdx.math.Vector2.X), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
  })
  testCase("testAngleRadStatic", {
    org.junit.Assert.assertEquals(-com.badlogic.gdx.math.MathUtils.HALF_PI, com.badlogic.gdx.math.Vector2.angleRad(0, -1.0f), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
  })
}