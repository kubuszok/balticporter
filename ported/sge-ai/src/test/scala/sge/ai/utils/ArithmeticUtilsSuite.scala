// ---------------------------------------------------------------------------------------------
// DIFFERENTIAL SUITE — a copy of the REFERENCE HAND PORT's own MUnit suite
//   ../sge/sge-extension/ai/src/test/scala/sge/ai/utils/ArithmeticUtilsSuite.scala
// run against THIS port's mechanically emitted `sge.ai.*`. It is HAND-WRITTEN Scala and must
// never be counted as a ported test (`CLAUDE.md` §3, and the jbump differential probe's rule);
// `PROGRESS.md` §10.7.12 is the census that says why this file is here and its siblings are not.
//
// Class (b) of that census. NO ASSERTION IS EDITED — an assertion changed is evidence
// destroyed, and a file whose assertions could not survive the mapping is class (c) and was
// left out rather than repaired. The only edits are the mapping rows below, each a NAME or
// SHIM substitution between the hand port's surface and this port's emitted one, and each
// applied to CODE only — a comment is the hand port's own prose.
//
// mapping rows applied here: M2
// ---------------------------------------------------------------------------------------------
package sge
package ai
package utils


class ArithmeticUtilsSuite extends munit.FunSuite {

  private val Eps = 1e-5f

  // ── wrapAngleAroundZero ────────────────────────────────────────────────

  test("wrapAngleAroundZero: 0 stays 0") {
    assertEqualsFloat(ArithmeticUtils.wrapAngleAroundZero(0f), 0f, Eps)
  }

  test("wrapAngleAroundZero: PI stays PI") {
    assertEqualsFloat(ArithmeticUtils.wrapAngleAroundZero(sge.math.MathUtils.PI), sge.math.MathUtils.PI, Eps)
  }

  test("wrapAngleAroundZero: -PI stays -PI") {
    assertEqualsFloat(ArithmeticUtils.wrapAngleAroundZero(-sge.math.MathUtils.PI), -sge.math.MathUtils.PI, Eps)
  }

  test("wrapAngleAroundZero: angle > PI wraps negative") {
    val angle   = sge.math.MathUtils.PI + 0.5f
    val wrapped = ArithmeticUtils.wrapAngleAroundZero(angle)
    assert(wrapped >= -sge.math.MathUtils.PI && wrapped <= sge.math.MathUtils.PI, s"wrapped=$wrapped not in [-PI, PI]")
    assertEqualsFloat(wrapped, -sge.math.MathUtils.PI + 0.5f, Eps)
  }

  test("wrapAngleAroundZero: angle < -PI wraps positive") {
    val angle   = -sge.math.MathUtils.PI - 0.5f
    val wrapped = ArithmeticUtils.wrapAngleAroundZero(angle)
    assert(wrapped >= -sge.math.MathUtils.PI && wrapped <= sge.math.MathUtils.PI, s"wrapped=$wrapped not in [-PI, PI]")
    assertEqualsFloat(wrapped, sge.math.MathUtils.PI - 0.5f, Eps)
  }

  test("wrapAngleAroundZero: 2*PI wraps to 0") {
    assertEqualsFloat(ArithmeticUtils.wrapAngleAroundZero(sge.math.MathUtils.PI2), 0f, Eps)
  }

  test("wrapAngleAroundZero: -2*PI wraps to 0") {
    assertEqualsFloat(ArithmeticUtils.wrapAngleAroundZero(-sge.math.MathUtils.PI2), 0f, Eps)
  }

  // ── gcdPositive ────────────────────────────────────────────────────────

  test("gcdPositive: basic cases") {
    assertEquals(ArithmeticUtils.gcdPositive(12, 8), 4)
    assertEquals(ArithmeticUtils.gcdPositive(54, 24), 6)
    assertEquals(ArithmeticUtils.gcdPositive(7, 7), 7)
  }

  test("gcdPositive: gcd(0, x) = x") {
    assertEquals(ArithmeticUtils.gcdPositive(0, 5), 5)
  }

  test("gcdPositive: gcd(x, 0) = x") {
    assertEquals(ArithmeticUtils.gcdPositive(5, 0), 5)
  }

  test("gcdPositive: gcd(0, 0) = 0") {
    assertEquals(ArithmeticUtils.gcdPositive(0, 0), 0)
  }

  test("gcdPositive: coprime numbers") {
    assertEquals(ArithmeticUtils.gcdPositive(17, 13), 1)
    assertEquals(ArithmeticUtils.gcdPositive(3, 7), 1)
  }

  // ── lcmPositive ────────────────────────────────────────────────────────

  test("lcmPositive: basic cases") {
    assertEquals(ArithmeticUtils.lcmPositive(4, 6), 12)
    assertEquals(ArithmeticUtils.lcmPositive(3, 5), 15)
    assertEquals(ArithmeticUtils.lcmPositive(7, 7), 7)
  }

  test("lcmPositive: lcm(0, x) = 0") {
    assertEquals(ArithmeticUtils.lcmPositive(0, 5), 0)
  }

  test("lcmPositive: lcm(x, 0) = 0") {
    assertEquals(ArithmeticUtils.lcmPositive(5, 0), 0)
  }

  // ── mulAndCheck ────────────────────────────────────────────────────────

  test("mulAndCheck: normal multiplication") {
    assertEquals(ArithmeticUtils.mulAndCheck(6, 7), 42)
    assertEquals(ArithmeticUtils.mulAndCheck(-3, 4), -12)
    assertEquals(ArithmeticUtils.mulAndCheck(0, 100), 0)
  }

  test("mulAndCheck: overflow throws ArithmeticException") {
    intercept[ArithmeticException] {
      ArithmeticUtils.mulAndCheck(Int.MaxValue, 2)
    }
    intercept[ArithmeticException] {
      ArithmeticUtils.mulAndCheck(Int.MinValue, 2)
    }
  }
}
