package com.badlogic.gdx.math

class Vector2Test {
  @org.junit.Test
  def testToString(): scala.Unit = {
    org.junit.Assert.assertEquals("(-5.0,42.00055)", new com.badlogic.gdx.math.Vector2(-5.0f, 42.00055f).toString())
  }
  @org.junit.Test
  def testFromString(): scala.Unit = {
    org.junit.Assert.assertEquals(new com.badlogic.gdx.math.Vector2(-5.0f, 42.00055f), new com.badlogic.gdx.math.Vector2().fromString("(-5,42.00055)"))
  }
  @org.junit.Test
  def testAngle(): scala.Unit = {
    org.junit.Assert.assertEquals(270.0f, new com.badlogic.gdx.math.Vector2(0, -1.0f).angleDeg(), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
  }
  @org.junit.Test
  def testAngleRelative(): scala.Unit = {
    org.junit.Assert.assertEquals(270.0f, new com.badlogic.gdx.math.Vector2(0, -1.0f).angleDeg(com.badlogic.gdx.math.Vector2.X), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
  }
  @org.junit.Test
  def testAngleStatic(): scala.Unit = {
    org.junit.Assert.assertEquals(270.0f, com.badlogic.gdx.math.Vector2.angleDeg(0, -1.0f), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
  }
  @org.junit.Test
  def testAngleRad(): scala.Unit = {
    org.junit.Assert.assertEquals(-com.badlogic.gdx.math.MathUtils.HALF_PI, new com.badlogic.gdx.math.Vector2(0, -1.0f).angleRad(), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
  }
  @org.junit.Test
  def testAngleRadRelative(): scala.Unit = {
    org.junit.Assert.assertEquals(-com.badlogic.gdx.math.MathUtils.HALF_PI, new com.badlogic.gdx.math.Vector2(0, -1.0f).angleRad(com.badlogic.gdx.math.Vector2.X), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
  }
  @org.junit.Test
  def testAngleRadStatic(): scala.Unit = {
    org.junit.Assert.assertEquals(-com.badlogic.gdx.math.MathUtils.HALF_PI, com.badlogic.gdx.math.Vector2.angleRad(0, -1.0f), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
  }
}