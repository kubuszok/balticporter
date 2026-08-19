// ---------------------------------------------------------------------------------------------
// DIFFERENTIAL SUITE — a copy of the REFERENCE HAND PORT's own MUnit suite
//   ../sge/sge-extension/textra/src/test/scala/sge/textra/utils/BlockUtilsSuite.scala
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
// mapping rows applied here: none — this file compiles unadapted
// ---------------------------------------------------------------------------------------------
package sge
package textra
package utils

class BlockUtilsSuite extends munit.FunSuite {

  test("BOX_DRAWING has correct number of entries") {
    // u2500 to u259F = 160 entries total
    // u2500-u256C (109) + u256D-u2573 (7 empty) + u2574-u2590 (29) + u2591-u2593 (3 empty) + u2594-u259F (12) = 160
    assertEquals(BlockUtils.BOX_DRAWING.length, 160)
  }

  test("BOX_DRAWING thin horizontal line is 4 floats") {
    // u2500 ─ thin horizontal line: {0, THIN_START, 1, THIN_ACROSS}
    val entry = BlockUtils.BOX_DRAWING(0)
    assertEquals(entry.length, 4)
    assertEqualsFloat(entry(0), 0f, 0.001f)
    assertEqualsFloat(entry(1), BlockUtils.THIN_START, 0.001f)
    assertEqualsFloat(entry(2), 1f, 0.001f)
    assertEqualsFloat(entry(3), BlockUtils.THIN_ACROSS, 0.001f)
  }

  test("BOX_DRAWING full block is unit square") {
    // u2588 █ full block: {0, 0, 1, 1} — index = 0x2588 - 0x2500 = 0x88 = 136
    val entry = BlockUtils.BOX_DRAWING(0x88)
    assertEquals(entry.length, 4)
    assertEqualsFloat(entry(0), 0f, 0.001f)
    assertEqualsFloat(entry(1), 0f, 0.001f)
    assertEqualsFloat(entry(2), 1f, 0.001f)
    assertEqualsFloat(entry(3), 1f, 0.001f)
  }

  test("BOX_DRAWING unused entries are empty") {
    // u256D-u2573 (rounded corners, diagonals) are NOT USED
    val idx256D = 0x256d - 0x2500 // 109
    for (i <- idx256D until idx256D + 7)
      assertEquals(BlockUtils.BOX_DRAWING(i).length, 0, s"Index $i should be empty")
  }

  test("isBlockGlyph recognizes box-drawing range") {
    assert(BlockUtils.isBlockGlyph(0x2500)) // ─
    assert(BlockUtils.isBlockGlyph(0x2588)) // █
    assert(BlockUtils.isBlockGlyph(0x259f)) // ▟
    assert(!BlockUtils.isBlockGlyph(0x24ff)) // below range
    assert(!BlockUtils.isBlockGlyph(0x25a0)) // above range
    // gap: 256D-2573 are not block glyphs
    assert(!BlockUtils.isBlockGlyph(0x256d))
    assert(!BlockUtils.isBlockGlyph(0x2573))
  }

  test("BlockUtils constants are consistent") {
    // THIN is smaller than WIDE
    assert(BlockUtils.THIN_ACROSS < BlockUtils.WIDE_ACROSS)
    // START + ACROSS < END (they define a line)
    assertEqualsFloat(BlockUtils.THIN_START + BlockUtils.THIN_ACROSS, BlockUtils.THIN_END, 0.001f)
    assertEqualsFloat(BlockUtils.WIDE_START + BlockUtils.WIDE_ACROSS, BlockUtils.WIDE_END, 0.001f)
  }

  test("ALL_BLOCK_CHARS contains only renderable block chars") {
    // ALL_BLOCK_CHARS excludes the gaps (u256D-u2573, u2591-u2593) = 160 - 10 = 150
    assertEquals(BlockUtils.ALL_BLOCK_CHARS.length, 150)
    // Every char in the string should be recognized as a block glyph
    for (c <- BlockUtils.ALL_BLOCK_CHARS)
      assert(BlockUtils.isBlockGlyph(c.toInt), s"Char ${c.toInt.toHexString} should be a block glyph")
  }
}
