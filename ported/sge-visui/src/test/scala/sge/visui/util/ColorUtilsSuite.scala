// ---------------------------------------------------------------------------------------------
// DIFFERENTIAL SUITE — a copy of the REFERENCE HAND PORT's own MUnit suite
//   ../sge/sge-extension/visui/src/test/scala/sge/visui/util/ColorUtilsSuite.scala
// run against THIS port's mechanically emitted `sge.visui.*`. It is HAND-WRITTEN Scala and must
// never be counted as a ported test (`CLAUDE.md` §3); `PROGRESS.md` §10.9.12 is the census that
// says why this file is here and its eight siblings are not.
//
// Class (a) of that census. NO ASSERTION IS EDITED — an assertion changed is evidence destroyed,
// and a file whose assertions could not survive the mapping is counted as incompatible rather
// than repaired. The only edits are the mapping rows below, each a NAME or SHIM substitution
// between the hand port's surface and this port's emitted one, each applied to COMMENT-MASKED
// code and PER RECEIVER (`CLAUDE.md` §4.56 — one spelling can name two different members).
//
// THE COMPILE IS SCOPED. This port stands at an 8-error floor (`PROGRESS.md` §10.9.10), so
// `visui-diff-measure` compiles the five emitted files these suites transitively name rather
// than the whole tree — with any typer error outstanding `RefChecks` never runs and scalac
// writes no class file. The lane verifies that none of those five is a file the port fails on.
//
// mapping rows applied here: none — this file compiles unadapted
// ---------------------------------------------------------------------------------------------
package sge
package visui
package util

class ColorUtilsSuite extends munit.FunSuite {

  test("HSVtoRGB: pure red (h=0, s=100, v=100)") {
    val c = ColorUtils.HSVtoRGB(0, 100, 100)
    assertEqualsFloat(c.r, 1.0f, 0.01f)
    assertEqualsFloat(c.g, 0.0f, 0.01f)
    assertEqualsFloat(c.b, 0.0f, 0.01f)
  }

  test("HSVtoRGB: pure green (h=120, s=100, v=100)") {
    val c = ColorUtils.HSVtoRGB(120, 100, 100)
    assertEqualsFloat(c.r, 0.0f, 0.01f)
    assertEqualsFloat(c.g, 1.0f, 0.01f)
    assertEqualsFloat(c.b, 0.0f, 0.01f)
  }

  test("HSVtoRGB: pure blue (h=240, s=100, v=100)") {
    val c = ColorUtils.HSVtoRGB(240, 100, 100)
    assertEqualsFloat(c.r, 0.0f, 0.01f)
    assertEqualsFloat(c.g, 0.0f, 0.01f)
    assertEqualsFloat(c.b, 1.0f, 0.01f)
  }

  test("HSVtoRGB: white (h=0, s=0, v=100)") {
    val c = ColorUtils.HSVtoRGB(0, 0, 100)
    assertEqualsFloat(c.r, 1.0f, 0.01f)
    assertEqualsFloat(c.g, 1.0f, 0.01f)
    assertEqualsFloat(c.b, 1.0f, 0.01f)
  }

  test("HSVtoRGB with alpha preserves alpha") {
    val c = ColorUtils.HSVtoRGB(0, 100, 100, 0.5f)
    assertEqualsFloat(c.a, 0.5f, 0.001f)
  }

  test("RGBtoHSV: pure red returns h~0, s=100, v=100") {
    val hsv = ColorUtils.RGBtoHSV(1.0f, 0.0f, 0.0f)
    assertEquals(hsv(0), 0)
    assertEquals(hsv(1), 100)
    assertEquals(hsv(2), 100)
  }

  test("RGBtoHSV: round-trip HSV->RGB->HSV") {
    val c   = ColorUtils.HSVtoRGB(200, 75, 60)
    val hsv = ColorUtils.RGBtoHSV(c)
    // Allow rounding tolerance
    assert(Math.abs(hsv(0) - 200) <= 2, s"hue: ${hsv(0)}")
    assert(Math.abs(hsv(1) - 75) <= 2, s"saturation: ${hsv(1)}")
    assert(Math.abs(hsv(2) - 60) <= 2, s"value: ${hsv(2)}")
  }

  private def assertEqualsFloat(actual: Float, expected: Float, delta: Float)(using munit.Location): Unit =
    assert(Math.abs(actual - expected) <= delta, s"expected $expected +/- $delta but got $actual")
}
