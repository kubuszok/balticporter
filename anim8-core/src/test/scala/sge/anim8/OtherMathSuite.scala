package sge.anim8

/** `OtherMath` — anim8's bit-pattern approximations, checked against the JDK's exact functions.
  *
  * This is where CLAUDE.md §4.4's defect class would live if it lived anywhere in this port: every
  * one of these methods is integer bit-twiddling over `NumberUtils.floatToIntBits`, hex float
  * literals (`0x1p-8`), shifts and post-increments, and every one of them translates to valid Scala
  * whatever the emitter does with it. A wrong sign, a `>>` where java wrote `>>>`, a post-increment
  * read after the update — none moves a compile-error count and all of them show up here.
  *
  * Adapted from the reference hand port's own `OtherMathSuite`, with the tolerances kept: these are
  * deliberately approximate functions and the assertions pin them against `java.lang.Math`.
  */
class OtherMathSuite extends munit.FunSuite:

  private def near(actual: Float, expected: Float, delta: Float)(using munit.Location): Unit =
    assert(Math.abs(actual - expected) <= delta, s"expected $expected +/- $delta but got $actual")

  test("barronSpline maps the unit interval onto itself: 0 -> 0, 1 -> 1") {
    near(OtherMath.barronSpline(0f, 0.5f, 0.5f), 0f, 0.001f)
    near(OtherMath.barronSpline(1f, 0.5f, 0.5f), 1f, 0.001f)
  }

  test("barronSpline with shape = turning = 0.5 is the identity at the midpoint") {
    near(OtherMath.barronSpline(0.5f, 0.5f, 0.5f), 0.5f, 0.01f)
  }

  test("barronSpline is MONOTONIC across the unit interval") {
    // A property the endpoints cannot see: a sign error inside the spline keeps both of them and
    // folds everything between.
    var prev = -1f
    var i    = 0
    while i <= 100 do
      val v = OtherMath.barronSpline(i / 100f, 0.5f, 0.5f)
      assert(v >= prev - 1e-4f, s"barronSpline is not monotonic at ${i / 100f}: $v after $prev")
      prev = v
      i += 1
  }

  test("atan2 approximates Math.atan2 in all four quadrants") {
    for (y, x) <- List((0f, 1f), (1f, 0f), (1f, 1f), (-1f, 1f), (0f, -1f), (-1f, -1f), (1f, -1f), (-1f, 0f)) do
      near(OtherMath.atan2(y, x), Math.atan2(y.toDouble, x.toDouble).toFloat, 0.001f)
  }

  test("probit: 0.5 maps to ~0, and it is symmetric about 0.5") {
    assert(Math.abs(OtherMath.probit(0.5)) < 0.01, s"probit(0.5) should be ~0, was ${OtherMath.probit(0.5)}")
    val lo = OtherMath.probit(0.1)
    val hi = OtherMath.probit(0.9)
    assert(Math.abs(lo + hi) < 0.01, s"probit(0.1)=$lo and probit(0.9)=$hi should be symmetric")
    assert(lo < 0 && hi > 0, s"probit must be increasing: probit(0.1)=$lo, probit(0.9)=$hi")
  }

  test("probitF: 0.5 maps to ~0, and it tracks the double version") {
    assert(Math.abs(OtherMath.probitF(0.5f)) < 0.01f, s"probitF(0.5) was ${OtherMath.probitF(0.5f)}")
    for p <- List(0.1f, 0.25f, 0.75f, 0.9f) do
      near(OtherMath.probitF(p), OtherMath.probit(p.toDouble).toFloat, 0.05f)
  }

  test("cbrt approximates the cube root, including for negatives") {
    near(OtherMath.cbrt(1f), 1f, 0.001f)
    near(OtherMath.cbrt(8f), 2f, 0.01f)
    near(OtherMath.cbrt(27f), 3f, 0.02f)
    near(OtherMath.cbrt(-8f), -2f, 0.01f)
    near(OtherMath.cbrt(0.125f), 0.5f, 0.01f)
  }

  test("centralize pushes middling bytes toward the centre and leaves the extremes") {
    val mid = OtherMath.centralize(128.toByte) & 0xff
    assert(Math.abs(mid - 128) < 30, s"centralize(128) should stay near 128, was $mid")
    val high = OtherMath.centralize(255.toByte) & 0xff
    assert(high > 200, s"centralize(255) should stay high, was $high")
    assertEquals(OtherMath.centralize(0.toByte) & 0xff, 0, "centralize(0) must stay at the floor")
  }
