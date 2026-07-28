package com.badlogic.gdx.math

class MathUtilsTest extends munit.FunSuite {
  test("lerpAngle")({
    balticporter.runtime.Asserts.assertEquals(com.badlogic.gdx.math.MathUtils.PI / 18.0f, com.badlogic.gdx.math.MathUtils.lerpAngle(com.badlogic.gdx.math.MathUtils.PI / 18.0f, com.badlogic.gdx.math.MathUtils.PI / 6.0f, 0.0f), 0.01f)
    balticporter.runtime.Asserts.assertEquals(com.badlogic.gdx.math.MathUtils.PI / 9.0f, com.badlogic.gdx.math.MathUtils.lerpAngle(com.badlogic.gdx.math.MathUtils.PI / 18.0f, com.badlogic.gdx.math.MathUtils.PI / 6.0f, 0.5f), 0.01f)
    balticporter.runtime.Asserts.assertEquals(com.badlogic.gdx.math.MathUtils.PI / 6.0f, com.badlogic.gdx.math.MathUtils.lerpAngle(com.badlogic.gdx.math.MathUtils.PI / 18.0f, com.badlogic.gdx.math.MathUtils.PI / 6.0f, 1.0f), 0.01f);
    { var c: scala.Float = -1.0f; while (c <= 1.0f) { {
      balticporter.runtime.Asserts.assertEquals((com.badlogic.gdx.math.MathUtils.PI + java.lang.Math.copySign(com.badlogic.gdx.math.MathUtils.HALF_PI, c)) + c, com.badlogic.gdx.math.MathUtils.lerpAngle(0, ((com.badlogic.gdx.math.MathUtils.PI2 + com.badlogic.gdx.math.MathUtils.PI) + c) + c, 0.5f), 0.01f)
      balticporter.runtime.Asserts.assertEquals((com.badlogic.gdx.math.MathUtils.PI + java.lang.Math.copySign(com.badlogic.gdx.math.MathUtils.HALF_PI, c)) + c, com.badlogic.gdx.math.MathUtils.lerpAngle(((com.badlogic.gdx.math.MathUtils.PI2 + com.badlogic.gdx.math.MathUtils.PI) + c) + c, 0, 0.5f), 0.01f)
    }; c = c + 0.003f } }
  })
  test("lerpAngleDeg")({
    balticporter.runtime.Asserts.assertEquals(10, com.badlogic.gdx.math.MathUtils.lerpAngleDeg(10, 30, 0.0f), 0.01f)
    balticporter.runtime.Asserts.assertEquals(20, com.badlogic.gdx.math.MathUtils.lerpAngleDeg(10, 30, 0.5f), 0.01f)
    balticporter.runtime.Asserts.assertEquals(30, com.badlogic.gdx.math.MathUtils.lerpAngleDeg(10, 30, 1.0f), 0.01f);
    { var c: scala.Float = -80.0f; while (c <= 80.0f) { {
      balticporter.runtime.Asserts.assertEquals((180.0f + java.lang.Math.copySign(90.0f, c)) + c, com.badlogic.gdx.math.MathUtils.lerpAngleDeg(0, (540 + c) + c, 0.5f), 0.01f)
      balticporter.runtime.Asserts.assertEquals((180.0f + java.lang.Math.copySign(90.0f, c)) + c, com.badlogic.gdx.math.MathUtils.lerpAngleDeg((540 + c) + c, 0, 0.5f), 0.01f)
    }; c = c + 0.3f } }
  })
  test("lerpAngleDegCrossingZero")({
    balticporter.runtime.Asserts.assertEquals(350, com.badlogic.gdx.math.MathUtils.lerpAngleDeg(350, 10, 0.0f), 0.01f)
    balticporter.runtime.Asserts.assertEquals(0, com.badlogic.gdx.math.MathUtils.lerpAngleDeg(350, 10, 0.5f), 0.01f)
    balticporter.runtime.Asserts.assertEquals(10, com.badlogic.gdx.math.MathUtils.lerpAngleDeg(350, 10, 1.0f), 0.01f)
  })
  test("lerpAngleDegCrossingZeroBackwards")({
    balticporter.runtime.Asserts.assertEquals(10, com.badlogic.gdx.math.MathUtils.lerpAngleDeg(10, 350, 0.0f), 0.01f)
    balticporter.runtime.Asserts.assertEquals(0, com.badlogic.gdx.math.MathUtils.lerpAngleDeg(10, 350, 0.5f), 0.01f)
    balticporter.runtime.Asserts.assertEquals(350, com.badlogic.gdx.math.MathUtils.lerpAngleDeg(10, 350, 1.0f), 0.01f)
  })
  test("testNorm")({
    balticporter.runtime.Asserts.assertEquals(-1.0f, com.badlogic.gdx.math.MathUtils.norm(10.0f, 20.0f, 0.0f), 0.01f)
    balticporter.runtime.Asserts.assertEquals(0.0f, com.badlogic.gdx.math.MathUtils.norm(10.0f, 20.0f, 10.0f), 0.01f)
    balticporter.runtime.Asserts.assertEquals(0.5f, com.badlogic.gdx.math.MathUtils.norm(10.0f, 20.0f, 15.0f), 0.01f)
    balticporter.runtime.Asserts.assertEquals(1.0f, com.badlogic.gdx.math.MathUtils.norm(10.0f, 20.0f, 20.0f), 0.01f)
    balticporter.runtime.Asserts.assertEquals(2.0f, com.badlogic.gdx.math.MathUtils.norm(10.0f, 20.0f, 30.0f), 0.01f)
  })
  test("testMap")({
    balticporter.runtime.Asserts.assertEquals(0.0f, com.badlogic.gdx.math.MathUtils.map(10.0f, 20.0f, 100.0f, 200.0f, 0.0f), 0.01f)
    balticporter.runtime.Asserts.assertEquals(100.0f, com.badlogic.gdx.math.MathUtils.map(10.0f, 20.0f, 100.0f, 200.0f, 10.0f), 0.01f)
    balticporter.runtime.Asserts.assertEquals(150.0f, com.badlogic.gdx.math.MathUtils.map(10.0f, 20.0f, 100.0f, 200.0f, 15.0f), 0.01f)
    balticporter.runtime.Asserts.assertEquals(200.0f, com.badlogic.gdx.math.MathUtils.map(10.0f, 20.0f, 100.0f, 200.0f, 20.0f), 0.01f)
    balticporter.runtime.Asserts.assertEquals(300.0f, com.badlogic.gdx.math.MathUtils.map(10.0f, 20.0f, 100.0f, 200.0f, 30.0f), 0.01f)
  })
  test("testRandomLong")({
    var r: scala.Long = 0L;
    { var i: scala.Int = 0; while (i < 512) { {
      balticporter.runtime.Asserts.assertTrue(({
        r = (com.badlogic.gdx.math.MathUtils.random: (scala.Long, scala.Long) => scala.Long)(1L, 5L)
        r
      } >= 1L) && (r <= 5L))
      balticporter.runtime.Asserts.assertTrue(({
        r = (com.badlogic.gdx.math.MathUtils.random: (scala.Long, scala.Long) => scala.Long)(6L, 1L)
        r
      } >= 1L) && (r <= 6L))
      balticporter.runtime.Asserts.assertTrue(({
        r = (com.badlogic.gdx.math.MathUtils.random: (scala.Long, scala.Long) => scala.Long)(-1L, -7L)
        r
      } <= (-1L)) && (r >= (-7L)))
      balticporter.runtime.Asserts.assertTrue(({
        r = (com.badlogic.gdx.math.MathUtils.random: (scala.Long, scala.Long) => scala.Long)(-8L, -1L)
        r
      } <= (-1L)) && (r >= (-8L)))
    }; i = i + 1 } }
  })
  test("testSinDeg")({
    balticporter.runtime.Asserts.assertEquals(0.0f, com.badlogic.gdx.math.MathUtils.sinDeg(0.0f), 0.0f)
    balticporter.runtime.Asserts.assertEquals(1.0f, com.badlogic.gdx.math.MathUtils.sinDeg(90.0f), 0.0f)
    balticporter.runtime.Asserts.assertEquals(0.0f, com.badlogic.gdx.math.MathUtils.sinDeg(180.0f), 0.0f)
    balticporter.runtime.Asserts.assertEquals(-1.0f, com.badlogic.gdx.math.MathUtils.sinDeg(270.0f), 0.0f)
  })
  test("testCosDeg")({
    balticporter.runtime.Asserts.assertEquals(1.0f, com.badlogic.gdx.math.MathUtils.cosDeg(0.0f), 0.0f)
    balticporter.runtime.Asserts.assertEquals(0.0f, com.badlogic.gdx.math.MathUtils.cosDeg(90.0f), 0.0f)
    balticporter.runtime.Asserts.assertEquals(-1.0f, com.badlogic.gdx.math.MathUtils.cosDeg(180.0f), 0.0f)
    balticporter.runtime.Asserts.assertEquals(0.0f, com.badlogic.gdx.math.MathUtils.cosDeg(270.0f), 0.0f)
  })
  test("testTanDeg")({
    balticporter.runtime.Asserts.assertEquals(0.0f, com.badlogic.gdx.math.MathUtils.tanDeg(0.0f), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
    balticporter.runtime.Asserts.assertEquals(java.lang.Math.tan(java.lang.Math.toRadians(45.0f)), com.badlogic.gdx.math.MathUtils.tanDeg(45.0f), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
    balticporter.runtime.Asserts.assertEquals(java.lang.Math.tan(java.lang.Math.toRadians(135.0f)), com.badlogic.gdx.math.MathUtils.tanDeg(135.0f), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
    balticporter.runtime.Asserts.assertEquals(0.0f, com.badlogic.gdx.math.MathUtils.tanDeg(180.0f), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
  })
  test("testAtan2Deg360")({
    balticporter.runtime.Asserts.assertEquals(0.0f, com.badlogic.gdx.math.MathUtils.atan2Deg360(0.0f, 1.0f), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
    balticporter.runtime.Asserts.assertEquals(45.0f, com.badlogic.gdx.math.MathUtils.atan2Deg360(1.0f, 1.0f), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
    balticporter.runtime.Asserts.assertEquals(90.0f, com.badlogic.gdx.math.MathUtils.atan2Deg360(1.0f, 0.0f), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
    balticporter.runtime.Asserts.assertEquals(135.0f, com.badlogic.gdx.math.MathUtils.atan2Deg360(1.0f, -1.0f), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
    balticporter.runtime.Asserts.assertEquals(180.0f, com.badlogic.gdx.math.MathUtils.atan2Deg360(0.0f, -1.0f), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
    balticporter.runtime.Asserts.assertEquals(225.0f, com.badlogic.gdx.math.MathUtils.atan2Deg360(-1.0f, -1.0f), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
    balticporter.runtime.Asserts.assertEquals(270.0f, com.badlogic.gdx.math.MathUtils.atan2Deg360(-1.0f, 0.0f), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
    balticporter.runtime.Asserts.assertEquals(315.0f, com.badlogic.gdx.math.MathUtils.atan2Deg360(-1.0f, 1.0f), com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR)
  })
}