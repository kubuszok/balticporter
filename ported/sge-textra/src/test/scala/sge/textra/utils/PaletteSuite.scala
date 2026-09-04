// ---------------------------------------------------------------------------------------------
// DIFFERENTIAL SUITE — a copy of the REFERENCE HAND PORT's own MUnit suite
//   ../sge/sge-extension/textra/src/test/scala/sge/textra/utils/PaletteSuite.scala
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
// mapping rows applied here: T2, T7
// ---------------------------------------------------------------------------------------------
package sge
package textra
package utils

class PaletteSuite extends munit.FunSuite {

  test("named colors RED, GREEN, BLUE, WHITE, BLACK exist") {
    assert(Palette.NAMED.containsKey("RED"), "RED missing")
    assert(Palette.NAMED.containsKey("GREEN"), "GREEN missing")
    assert(Palette.NAMED.containsKey("BLUE"), "BLUE missing")
    assert(Palette.NAMED.containsKey("WHITE"), "WHITE missing")
    assert(Palette.NAMED.containsKey("BLACK"), "BLACK missing")
  }

  test("lowercase color names exist") {
    assert(Palette.NAMED.containsKey("red"), "red missing")
    assert(Palette.NAMED.containsKey("green"), "green missing")
    assert(Palette.NAMED.containsKey("blue"), "blue missing")
    assert(Palette.NAMED.containsKey("white"), "white missing")
    assert(Palette.NAMED.containsKey("black"), "black missing")
  }

  test("color values match expected RGBA8888") {
    assertEquals(Palette.white, 0xffffffff)
    assertEquals(Palette.black, 0x000000ff)
    assertEquals(Palette.red, 0xff0000ff.toInt)
    assertEquals(Palette.green, 0x00ff00ff)
    assertEquals(Palette.blue, 0x0000ffff)
  }

  test("LIST is populated with expected count") {
    // 49 lowercase + 34 uppercase = 83 entries (including aliases merged into NAMED,
    // but LIST is populated from the entries Seq which has 83 items)
    assert(Palette.LIST.size >= 80, s"Expected at least 80 colors in LIST, got ${Palette.LIST.size}")
  }

  test("NAMES is sorted alphabetically") {
    val names  = Palette.NAMES.toArray.toSeq
    val sorted = names.sorted
    assertEquals(names, sorted)
  }
}
