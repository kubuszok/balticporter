// ---------------------------------------------------------------------------------------------
// DIFFERENTIAL SUITE — a copy of the REFERENCE HAND PORT's own MUnit suite
//   ../sge/sge-extension/visui/src/test/scalajvm/sge/visui/util/OsUtilsSuite.scala
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
// mapping rows applied here: V1 (a java parameterless method is emitted `def f()` and Scala 3 requires the parens, 14 lines)
// ---------------------------------------------------------------------------------------------
package sge
package visui
package util

class OsUtilsSuite extends munit.FunSuite {

  test("exactly one of isWindows, isMac, isUnix is true (or none on exotic OS)") {
    val count = List(OsUtils.isWindows(), OsUtils.isMac(), OsUtils.isUnix()).count(identity)
    assert(count <= 1, s"At most one OS flag should be true, got $count")
  }

  test("at least one desktop OS is detected on standard platforms") {
    // This test runs on CI (linux, mac, windows) so at least one should be true
    val anyDetected = OsUtils.isWindows() || OsUtils.isMac() || OsUtils.isUnix()
    assert(anyDetected, "Expected at least one of isWindows/isMac/isUnix to be true")
  }

  test("OS detection is consistent across calls") {
    assertEquals(OsUtils.isWindows(), OsUtils.isWindows())
    assertEquals(OsUtils.isMac(), OsUtils.isMac())
    assertEquals(OsUtils.isUnix(), OsUtils.isUnix())
  }

  test("on macOS, isMac is true") {
    val os = System.getProperty("os.name", "").toLowerCase
    if (os.contains("mac")) {
      assert(OsUtils.isMac())
      assert(!OsUtils.isWindows())
      assert(!OsUtils.isUnix())
    }
  }

  test("on Linux, isUnix is true") {
    val os = System.getProperty("os.name", "").toLowerCase
    if (os.contains("nux") || os.contains("nix")) {
      assert(OsUtils.isUnix())
      assert(!OsUtils.isWindows())
      assert(!OsUtils.isMac())
    }
  }

  test("on Windows, isWindows is true") {
    val os = System.getProperty("os.name", "").toLowerCase
    if (os.contains("win")) {
      assert(OsUtils.isWindows())
      assert(!OsUtils.isMac())
      assert(!OsUtils.isUnix())
    }
  }
}
