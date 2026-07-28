package com.badlogic.gdx.math

class MathUtilsTest {
  @org.junit.Test
  def lerpAngle(): scala.Unit = {
    org.junit.Assert.assertEquals(com.badlogic.gdx.math.MathUtils.PI / 18.0f, com.badlogic.gdx.math.MathUtils.lerpAngle(com.badlogic.gdx.math.MathUtils.PI / 18.0f, com.badlogic.gdx.math.MathUtils.PI / 6.0f, 0.0f), 0.01f)
    org.junit.Assert.assertEquals(com.badlogic.gdx.math.MathUtils.PI / 9.0f, com.badlogic.gdx.math.MathUtils.lerpAngle(com.badlogic.gdx.math.MathUtils.PI / 18.0f, com.badlogic.gdx.math.MathUtils.PI / 6.0f, 0.5f), 0.01f)
    org.junit.Assert.assertEquals(com.badlogic.gdx.math.MathUtils.PI / 6.0f, com.badlogic.gdx.math.MathUtils.lerpAngle(com.badlogic.gdx.math.MathUtils.PI / 18.0f, com.badlogic.gdx.math.MathUtils.PI / 6.0f, 1.0f), 0.01f);
    { var c: scala.Float = -1.0f; while (c <= 1.0f) { {
      org.junit.Assert.assertEquals((com.badlogic.gdx.math.MathUtils.PI + java.lang.Math.copySign(com.badlogic.gdx.math.MathUtils.HALF_PI, c)) + c, com.badlogic.gdx.math.MathUtils.lerpAngle(0, ((com.badlogic.gdx.math.MathUtils.PI2 + com.badlogic.gdx.math.MathUtils.PI) + c) + c, 0.5f), 0.01f)
      org.junit.Assert.assertEquals((com.badlogic.gdx.math.MathUtils.PI + java.lang.Math.copySign(com.badlogic.gdx.math.MathUtils.HALF_PI, c)) + c, com.badlogic.gdx.math.MathUtils.lerpAngle(((com.badlogic.gdx.math.MathUtils.PI2 + com.badlogic.gdx.math.MathUtils.PI) + c) + c, 0, 0.5f), 0.01f)
    }; c = c + 0.003f } }
  }
  @org.junit.Test
  def lerpAngleDeg(): scala.Unit = {
    org.junit.Assert.assertEquals(10, com.badlogic.gdx.math.MathUtils.lerpAngleDeg(10, 30, 0.0f), 0.01f)
    org.junit.Assert.assertEquals(20, com.badlogic.gdx.math.MathUtils.lerpAngleDeg(10, 30, 0.5f), 0.01f)
    org.junit.Assert.assertEquals(30, com.badlogic.gdx.math.MathUtils.lerpAngleDeg(10, 30, 1.0f), 0.01f);
    { var c: scala.Float = -80.0f; while (c <= 80.0f) { {
      org.junit.Assert.assertEquals((180.0f + java.lang.Math.copySign(90.0f, c)) + c, com.badlogic.gdx.math.MathUtils.lerpAngleDeg(0, (540 + c) + c, 0.5f), 0.01f)
      org.junit.Assert.assertEquals((180.0f + java.lang.Math.copySign(90.0f, c)) + c, com.badlogic.gdx.math.MathUtils.lerpAngleDeg((540 + c) + c, 0, 0.5f), 0.01f)
    }; c = c + 0.3f } }
  }
  @org.junit.Test
  def lerpAngleDegCrossingZero(): scala.Unit = {
    org.junit.Assert.assertEquals(350, com.badlogic.gdx.math.MathUtils.lerpAngleDeg(350, 10, 0.0f), 0.01f)
    org.junit.Assert.assertEquals(0, com.badlogic.gdx.math.MathUtils.lerpAngleDeg(350, 10, 0.5f), 0.01f)
    org.junit.Assert.assertEquals(10, com.badlogic.gdx.math.MathUtils.lerpAngleDeg(350, 10, 1.0f), 0.01f)
  }
  @org.junit.Test
  def lerpAngleDegCrossingZeroBackwards(): scala.Unit = {
    org.junit.Assert.assertEquals(10, com.badlogic.gdx.math.MathUtils.lerpAngleDeg(10, 350, 0.0f), 0.01f)
    org.junit.Assert.assertEquals(0, com.badlogic.gdx.math.MathUtils.lerpAngleDeg(10, 350, 0.5f), 0.01f)
    org.junit.Assert.assertEquals(350, com.badlogic.gdx.math.MathUtils.lerpAngleDeg(10, 350, 1.0f), 0.01f)
  }
  @org.junit.Test
  def testNorm(): scala.Unit = {
    org.junit.Assert.assertEquals(-1.0f, com.badlogic.gdx.math.MathUtils.norm(10.0f, 20.0f, 0.0f), 0.01f)
    org.junit.Assert.assertEquals(0.0f, com.badlogic.gdx.math.MathUtils.norm(10.0f, 20.0f, 10.0f), 0.01f)
    org.junit.Assert.assertEquals(0.5f, com.badlogic.gdx.math.MathUtils.norm(10.0f, 20.0f, 15.0f), 0.01f)
    org.junit.Assert.assertEquals(1.0f, com.badlogic.gdx.math.MathUtils.norm(10.0f, 20.0f, 20.0f), 0.01f)
    org.junit.Assert.assertEquals(2.0f, com.badlogic.gdx.math.MathUtils.norm(10.0f, 20.0f, 30.0f), 0.01f)
  }
  @org.junit.Test
  def testMap(): scala.Unit = {
    org.junit.Assert.assertEquals(0.0f, com.badlogic.gdx.math.MathUtils.map(10.0f, 20.0f, 100.0f, 200.0f, 0.0f), 0.01f)
    org.junit.Assert.assertEquals(100.0f, com.badlogic.gdx.math.MathUtils.map(10.0f, 20.0f, 100.0f, 200.0f, 10.0f), 0.01f)
    org.junit.Assert.assertEquals(150.0f, com.badlogic.gdx.math.MathUtils.map(10.0f, 20.0f, 100.0f, 200.0f, 15.0f), 0.01f)
    org.junit.Assert.assertEquals(200.0f, com.badlogic.gdx.math.MathUtils.map(10.0f, 20.0f, 100.0f, 200.0f, 20.0f), 0.01f)
    org.junit.Assert.assertEquals(300.0f, com.badlogic.gdx.math.MathUtils.map(10.0f, 20.0f, 100.0f, 200.0f, 30.0f), 0.01f)
  }
  @org.junit.Test
  def testRandomLong(): scala.Unit = {
    var r: scala.Long = 0L;
    { var i: scala.Int = 0; while (i < 512) { {
      org.junit.Assert.assertTrue(({
        r = (com.badlogic.gdx.math.MathUtils.random: (scala.Long, scala.Long) => scala.Long)(1L, 5L)
        r
      } >= 1L) && (r <= 5L))
      org.junit.Assert.assertTrue(({
        r = (com.badlogic.gdx.math.MathUtils.random: (scala.Long, scala.Long) => scala.Long)(6L, 1L)
        r
      } >= 1L) && (r <= 6L))
      org.junit.Assert.assertTrue(({
        r = (com.badlogic.gdx.math.MathUtils.random: (scala.Long, scala.Long) => scala.Long)(-1L, -7L)
        r
      } <= (-1L)) && (r >= (-7L)))
      org.junit.Assert.assertTrue(({
        r = (com.badlogic.gdx.math.MathUtils.random: (scala.Long, scala.Long) => scala.Long)(-8L, -1L)
        r
      } <= (-1L)) && (r >= (-8L)))
    }; i = i + 1 } }
  }
  @org.junit.Test
  def testSinDeg(): scala.Unit = {
    org.junit.Assert.assertEquals(0.0f, com.badlogic.gdx.math.MathUtils.sinDeg(0.0f), 0.0f)
    org.junit.Assert.assertEquals(1.0f, com.badlogic.gdx.math.MathUtils.sinDeg(90.0f), 0.0f)
    org.junit.Assert.assertEquals(0.0f, com.badlogic.gdx.math.MathUtils.sinDeg(180.0f), 0.0f)
    org.junit.Assert.assertEquals(-1.0f, com.badlogic.gdx.math.MathUtils.sinDeg(270.0f), 0.0f)
  }
  @org.junit.Test
  def testCosDeg(): scala.Unit = {
    org.junit.Assert.assertEquals(1.0f, com.badlogic.gdx.math.MathUtils.cosDeg(0.0f), 0.0f)
    org.junit.Assert.assertEquals(0.0f, com.badlogic.gdx.math.MathUtils.cosDeg(90.0f), 0.0f)
    org.junit.Assert.assertEquals(-1.0f, com.badlogic.gdx.math.MathUtils.cosDeg(180.0f), 0.0f)
    org.junit.Assert.assertEquals(0.0f, com.badlogic.gdx.math.MathUtils.cosDeg(270.0f), 0.0f)
  }
  @org.junit.Test
  def testTanDeg(): scala.Unit = {
    org.junit.Assert.assertEquals(0.0f, com.badlogic.gdx.math.MathUtils.tanDeg(0.0f), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
    org.junit.Assert.assertEquals(java.lang.Math.tan(java.lang.Math.toRadians(45.0f)), com.badlogic.gdx.math.MathUtils.tanDeg(45.0f), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
    org.junit.Assert.assertEquals(java.lang.Math.tan(java.lang.Math.toRadians(135.0f)), com.badlogic.gdx.math.MathUtils.tanDeg(135.0f), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
    org.junit.Assert.assertEquals(0.0f, com.badlogic.gdx.math.MathUtils.tanDeg(180.0f), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
  }
  @org.junit.Test
  def testAtan2Deg360(): scala.Unit = {
    org.junit.Assert.assertEquals(0.0f, com.badlogic.gdx.math.MathUtils.atan2Deg360(0.0f, 1.0f), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
    org.junit.Assert.assertEquals(45.0f, com.badlogic.gdx.math.MathUtils.atan2Deg360(1.0f, 1.0f), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
    org.junit.Assert.assertEquals(90.0f, com.badlogic.gdx.math.MathUtils.atan2Deg360(1.0f, 0.0f), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
    org.junit.Assert.assertEquals(135.0f, com.badlogic.gdx.math.MathUtils.atan2Deg360(1.0f, -1.0f), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
    org.junit.Assert.assertEquals(180.0f, com.badlogic.gdx.math.MathUtils.atan2Deg360(0.0f, -1.0f), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
    org.junit.Assert.assertEquals(225.0f, com.badlogic.gdx.math.MathUtils.atan2Deg360(-1.0f, -1.0f), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
    org.junit.Assert.assertEquals(270.0f, com.badlogic.gdx.math.MathUtils.atan2Deg360(-1.0f, 0.0f), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
    org.junit.Assert.assertEquals(315.0f, com.badlogic.gdx.math.MathUtils.atan2Deg360(-1.0f, 1.0f), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
  }
}