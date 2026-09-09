// ---------------------------------------------------------------------------------------------
// DIFFERENTIAL SUITE — a copy of the REFERENCE HAND PORT's own MUnit suite
//   ../sge/sge-extension/textra/src/test/scala/sge/textra/TypingConfigSuite.scala
// run against THIS port's mechanically emitted `sge.textra.*`. It is HAND-WRITTEN Scala and must
// never be counted as a ported test (`CLAUDE.md` §3, and the jbump differential probe's rule);
// `PROGRESS.md` §10.8.15 is the census that says why this file is here and its siblings are not.
//
// Class (a) of that census. NO ASSERTION IS EDITED — an assertion changed is evidence
// destroyed, and a file whose assertions could not survive the mapping is class (c) and was
// left out rather than repaired. The only edits are the mapping rows below, each a NAME or
// SHIM substitution between the hand port's surface and this port's emitted one, and each
// applied to CODE only — a comment is the hand port's own prose.
//
// mapping rows applied here: T2
// ---------------------------------------------------------------------------------------------
package sge
package textra

class TypingConfigSuite extends munit.FunSuite {

  test("DEFAULT_SPEED_PER_CHAR has expected default value") {
    assertEquals(TypingConfig.DEFAULT_SPEED_PER_CHAR, 0.05f)
  }

  test("DEFAULT_WAIT_VALUE has expected default value") {
    assertEquals(TypingConfig.DEFAULT_WAIT_VALUE, 0.250f)
  }

  test("DEFAULT_CLEAR_COLOR is white") {
    // ISS-724 c5: dropped the near-tautological `!= null` guard — DEFAULT_CLEAR_COLOR is a
    // statically non-nullable `Color`, and the four channel assertions below already fail (NPE)
    // if it were null, so they are the real coverage.
    assertEquals(TypingConfig.DEFAULT_CLEAR_COLOR.r, 1f)
    assertEquals(TypingConfig.DEFAULT_CLEAR_COLOR.g, 1f)
    assertEquals(TypingConfig.DEFAULT_CLEAR_COLOR.b, 1f)
    assertEquals(TypingConfig.DEFAULT_CLEAR_COLOR.a, 1f)
  }

  test("40 effect start tokens are registered") {
    assert(
      TypingConfig.EFFECT_START_TOKENS.size >= 40,
      s"Expected at least 40 effect tokens, got ${TypingConfig.EFFECT_START_TOKENS.size}"
    )
  }

  test("known effects are registered") {
    val knownEffects = Seq(
      "WAVE",
      "SHAKE",
      "RAINBOW",
      "FADE",
      "BLINK",
      "JOLT",
      "SPIRAL",
      "SPIN",
      "CROWD",
      "SHRINK",
      "EMERGE",
      "OCEAN"
    )
    knownEffects.foreach { name =>
      assert(TypingConfig.EFFECT_START_TOKENS.containsKey(name), s"Missing effect: $name")
      assert(TypingConfig.EFFECT_END_TOKENS.containsKey("END" + name), s"Missing end token: END$name")
    }
  }
}
