// ---------------------------------------------------------------------------------------------
// DIFFERENTIAL SUITE — a copy of the REFERENCE HAND PORT's own MUnit suite
//   ../sge/sge-extension/ai/src/test/scala/sge/ai/DefaultTimepieceSuite.scala
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
// mapping rows applied here: M3
// ---------------------------------------------------------------------------------------------
package sge
package ai

class DefaultTimepieceSuite extends munit.FunSuite {

  private val Eps = 1e-6f

  // ── maxDeltaTime defaults ─────────────────────────────────────────────

  test("default maxDeltaTime is Float.PositiveInfinity") {
    val tp = new DefaultTimepiece()
    assertEquals(tp.maxDeltaTime, Float.PositiveInfinity)
  }

  test("custom maxDeltaTime via constructor") {
    val tp = new DefaultTimepiece(maxDeltaTime$p = 0.25f)
    assertEqualsFloat(tp.maxDeltaTime, 0.25f, Eps)
  }

  // ── clamping behavior ─────────────────────────────────────────────────

  test("delta time is clamped when exceeding maxDeltaTime") {
    val tp = new DefaultTimepiece(maxDeltaTime$p = 0.1f)
    tp.update(0.5f)
    assertEqualsFloat(tp.deltaTime, 0.1f, Eps)
  }

  test("delta time is NOT clamped when below maxDeltaTime") {
    val tp = new DefaultTimepiece(maxDeltaTime$p = 1.0f)
    tp.update(0.016f)
    assertEqualsFloat(tp.deltaTime, 0.016f, Eps)
  }

  test("delta time equals maxDeltaTime when exactly at limit") {
    val tp = new DefaultTimepiece(maxDeltaTime$p = 0.1f)
    tp.update(0.1f)
    assertEqualsFloat(tp.deltaTime, 0.1f, Eps)
  }

  // ── time accumulation uses clamped delta ──────────────────────────────

  test("time accumulates with clamped delta, not raw delta") {
    val tp = new DefaultTimepiece(maxDeltaTime$p = 0.1f)
    tp.update(0.5f) // clamped to 0.1
    tp.update(0.3f) // clamped to 0.1
    assertEqualsFloat(tp.time, 0.2f, Eps)
  }

  test("time accumulates normally without clamping") {
    val tp = new DefaultTimepiece()
    tp.update(0.016f)
    tp.update(0.016f)
    tp.update(0.016f)
    assertEqualsFloat(tp.time, 0.048f, Eps)
  }

  // ── initial state ─────────────────────────────────────────────────────

  test("initial time and deltaTime are zero") {
    val tp = new DefaultTimepiece()
    assertEqualsFloat(tp.time, 0f, Eps)
    assertEqualsFloat(tp.deltaTime, 0f, Eps)
  }
}
