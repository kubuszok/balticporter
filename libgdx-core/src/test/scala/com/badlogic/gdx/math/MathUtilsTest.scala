package com.badlogic.gdx.math

class MathUtilsTest extends munit.FunSuite {
  test("lerpAngle")({
    assertEquals(0.01f, com.badlogic.gdx.math.MathUtils.lerpAngle(com.badlogic.gdx.math.MathUtils.PI / 18.0f, com.badlogic.gdx.math.MathUtils.PI / 6.0f, 0.0f), com.badlogic.gdx.math.MathUtils.PI / 18.0f)
    assertEquals(0.01f, com.badlogic.gdx.math.MathUtils.lerpAngle(com.badlogic.gdx.math.MathUtils.PI / 18.0f, com.badlogic.gdx.math.MathUtils.PI / 6.0f, 0.5f), com.badlogic.gdx.math.MathUtils.PI / 9.0f)
    assertEquals(0.01f, com.badlogic.gdx.math.MathUtils.lerpAngle(com.badlogic.gdx.math.MathUtils.PI / 18.0f, com.badlogic.gdx.math.MathUtils.PI / 6.0f, 1.0f), com.badlogic.gdx.math.MathUtils.PI / 6.0f);
    { var c: scala.Float = -1.0f; while (c <= 1.0f) { {
      assertEquals(0.01f, com.badlogic.gdx.math.MathUtils.lerpAngle(0, ((com.badlogic.gdx.math.MathUtils.PI2 + com.badlogic.gdx.math.MathUtils.PI) + c) + c, 0.5f), (com.badlogic.gdx.math.MathUtils.PI + java.lang.Math.copySign(com.badlogic.gdx.math.MathUtils.HALF_PI, c)) + c)
      assertEquals(0.01f, com.badlogic.gdx.math.MathUtils.lerpAngle(((com.badlogic.gdx.math.MathUtils.PI2 + com.badlogic.gdx.math.MathUtils.PI) + c) + c, 0, 0.5f), (com.badlogic.gdx.math.MathUtils.PI + java.lang.Math.copySign(com.badlogic.gdx.math.MathUtils.HALF_PI, c)) + c)
    }; c = c + 0.003f } }
  })
  test("lerpAngleDeg")({
    assertEquals(0.01f, com.badlogic.gdx.math.MathUtils.lerpAngleDeg(10, 30, 0.0f), 10)
    assertEquals(0.01f, com.badlogic.gdx.math.MathUtils.lerpAngleDeg(10, 30, 0.5f), 20)
    assertEquals(0.01f, com.badlogic.gdx.math.MathUtils.lerpAngleDeg(10, 30, 1.0f), 30);
    { var c: scala.Float = -80.0f; while (c <= 80.0f) { {
      assertEquals(0.01f, com.badlogic.gdx.math.MathUtils.lerpAngleDeg(0, (540 + c) + c, 0.5f), (180.0f + java.lang.Math.copySign(90.0f, c)) + c)
      assertEquals(0.01f, com.badlogic.gdx.math.MathUtils.lerpAngleDeg((540 + c) + c, 0, 0.5f), (180.0f + java.lang.Math.copySign(90.0f, c)) + c)
    }; c = c + 0.3f } }
  })
  test("lerpAngleDegCrossingZero")({
    assertEquals(0.01f, com.badlogic.gdx.math.MathUtils.lerpAngleDeg(350, 10, 0.0f), 350)
    assertEquals(0.01f, com.badlogic.gdx.math.MathUtils.lerpAngleDeg(350, 10, 0.5f), 0)
    assertEquals(0.01f, com.badlogic.gdx.math.MathUtils.lerpAngleDeg(350, 10, 1.0f), 10)
  })
  test("lerpAngleDegCrossingZeroBackwards")({
    assertEquals(0.01f, com.badlogic.gdx.math.MathUtils.lerpAngleDeg(10, 350, 0.0f), 10)
    assertEquals(0.01f, com.badlogic.gdx.math.MathUtils.lerpAngleDeg(10, 350, 0.5f), 0)
    assertEquals(0.01f, com.badlogic.gdx.math.MathUtils.lerpAngleDeg(10, 350, 1.0f), 350)
  })
  test("testNorm")({
    assertEquals(0.01f, com.badlogic.gdx.math.MathUtils.norm(10.0f, 20.0f, 0.0f), -1.0f)
    assertEquals(0.01f, com.badlogic.gdx.math.MathUtils.norm(10.0f, 20.0f, 10.0f), 0.0f)
    assertEquals(0.01f, com.badlogic.gdx.math.MathUtils.norm(10.0f, 20.0f, 15.0f), 0.5f)
    assertEquals(0.01f, com.badlogic.gdx.math.MathUtils.norm(10.0f, 20.0f, 20.0f), 1.0f)
    assertEquals(0.01f, com.badlogic.gdx.math.MathUtils.norm(10.0f, 20.0f, 30.0f), 2.0f)
  })
  test("testMap")({
    assertEquals(0.01f, com.badlogic.gdx.math.MathUtils.map(10.0f, 20.0f, 100.0f, 200.0f, 0.0f), 0.0f)
    assertEquals(0.01f, com.badlogic.gdx.math.MathUtils.map(10.0f, 20.0f, 100.0f, 200.0f, 10.0f), 100.0f)
    assertEquals(0.01f, com.badlogic.gdx.math.MathUtils.map(10.0f, 20.0f, 100.0f, 200.0f, 15.0f), 150.0f)
    assertEquals(0.01f, com.badlogic.gdx.math.MathUtils.map(10.0f, 20.0f, 100.0f, 200.0f, 20.0f), 200.0f)
    assertEquals(0.01f, com.badlogic.gdx.math.MathUtils.map(10.0f, 20.0f, 100.0f, 200.0f, 30.0f), 300.0f)
  })
  test("testRandomLong")({
    var r: scala.Long = 0L;
    { var i: scala.Int = 0; while (i < 512) { {
      assert(({
        r = (com.badlogic.gdx.math.MathUtils.random: (scala.Long, scala.Long) => scala.Long)(1L, 5L)
        r
      } >= 1L) && (r <= 5L))
      assert(({
        r = (com.badlogic.gdx.math.MathUtils.random: (scala.Long, scala.Long) => scala.Long)(6L, 1L)
        r
      } >= 1L) && (r <= 6L))
      assert(({
        r = (com.badlogic.gdx.math.MathUtils.random: (scala.Long, scala.Long) => scala.Long)(-1L, -7L)
        r
      } <= (-1L)) && (r >= (-7L)))
      assert(({
        r = (com.badlogic.gdx.math.MathUtils.random: (scala.Long, scala.Long) => scala.Long)(-8L, -1L)
        r
      } <= (-1L)) && (r >= (-8L)))
    }; i = i + 1 } }
  })
  test("testSinDeg")({
    assertEquals(0.0f, com.badlogic.gdx.math.MathUtils.sinDeg(0.0f), 0.0f)
    assertEquals(0.0f, com.badlogic.gdx.math.MathUtils.sinDeg(90.0f), 1.0f)
    assertEquals(0.0f, com.badlogic.gdx.math.MathUtils.sinDeg(180.0f), 0.0f)
    assertEquals(0.0f, com.badlogic.gdx.math.MathUtils.sinDeg(270.0f), -1.0f)
  })
  test("testCosDeg")({
    assertEquals(0.0f, com.badlogic.gdx.math.MathUtils.cosDeg(0.0f), 1.0f)
    assertEquals(0.0f, com.badlogic.gdx.math.MathUtils.cosDeg(90.0f), 0.0f)
    assertEquals(0.0f, com.badlogic.gdx.math.MathUtils.cosDeg(180.0f), -1.0f)
    assertEquals(0.0f, com.badlogic.gdx.math.MathUtils.cosDeg(270.0f), 0.0f)
  })
  test("testTanDeg")({
    assertEquals(com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR, com.badlogic.gdx.math.MathUtils.tanDeg(0.0f), 0.0f)
    assertEquals(com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR, com.badlogic.gdx.math.MathUtils.tanDeg(45.0f), java.lang.Math.tan(java.lang.Math.toRadians(45.0f)))
    assertEquals(com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR, com.badlogic.gdx.math.MathUtils.tanDeg(135.0f), java.lang.Math.tan(java.lang.Math.toRadians(135.0f)))
    assertEquals(com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR, com.badlogic.gdx.math.MathUtils.tanDeg(180.0f), 0.0f)
  })
  test("testAtan2Deg360")({
    assertEquals(com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR, com.badlogic.gdx.math.MathUtils.atan2Deg360(0.0f, 1.0f), 0.0f)
    assertEquals(com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR, com.badlogic.gdx.math.MathUtils.atan2Deg360(1.0f, 1.0f), 45.0f)
    assertEquals(com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR, com.badlogic.gdx.math.MathUtils.atan2Deg360(1.0f, 0.0f), 90.0f)
    assertEquals(com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR, com.badlogic.gdx.math.MathUtils.atan2Deg360(1.0f, -1.0f), 135.0f)
    assertEquals(com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR, com.badlogic.gdx.math.MathUtils.atan2Deg360(0.0f, -1.0f), 180.0f)
    assertEquals(com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR, com.badlogic.gdx.math.MathUtils.atan2Deg360(-1.0f, -1.0f), 225.0f)
    assertEquals(com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR, com.badlogic.gdx.math.MathUtils.atan2Deg360(-1.0f, 0.0f), 270.0f)
    assertEquals(com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR, com.badlogic.gdx.math.MathUtils.atan2Deg360(-1.0f, 1.0f), 315.0f)
  })
}