package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{Pipeline, Program}
import balticporter.transform.PackageRenameTransform

/** `PackageRenameTransform` — the §1(b) phase that moves a port out of the upstream namespace.
  *
  * Asserts BOTH sides on every case: the symbol table (what the rename actually did) and the
  * emitted Scala (what a consumer sees). The two can disagree — a rename that reaches `fullName`
  * but not the emitter's nested-type path would leave a symbol table that reads correctly and
  * output that does not compile — which is why neither alone is the test.
  */
class PackageRenameTransformSpec extends munit.FunSuite:

  private val src =
    """package com.example.demo;
      |public class Widget {
      |  public static class Style { public int pad; }
      |  public class Handle { public int id; }
      |  private java.util.List<String> names = new java.util.ArrayList<String>();
      |  private Style style = new Style();
      |  public String label(int i) { return names.get(i); }
      |  public Widget copy() { return new Widget(); }
      |}
      |class Panel {
      |  Widget w = new Widget();
      |  Widget.Style s = new Widget.Style();
      |  Widget.Handle h;
      |}
      |""".stripMargin

  private val before = SpoonTir.fromSource(src)

  private def run(renames: Map[String, String]): Program =
    Pipeline.run(before, List(new PackageRenameTransform(renames)))

  private def names(p: Program): Set[String] = p.symbols.all.map(_.fullName).toSet
  private def emit(p: Program): String       = new TirEmitter(p).emit

  // ---------------------------------------------------------------------------
  // the no-op
  // ---------------------------------------------------------------------------

  test("empty map is a total no-op — every symbol and every byte of output is unchanged") {
    val after = run(Map.empty)
    assertEquals(names(after), names(before))
    // and the simple names too: a rename that only touched `name` would pass the check above.
    assertEquals(after.symbols.all.map(s => s.id -> s.name).toMap, before.symbols.all.map(s => s.id -> s.name).toMap)
    assertEquals(emit(after), emit(before))
    assertEquals(PackageRenameTransform.check(before, Map.empty).matched, Map.empty[String, Int])
  }

  // ---------------------------------------------------------------------------
  // the rename
  // ---------------------------------------------------------------------------

  private val renamed = run(Map("com.example" -> "sge.ui"))
  private val out     = emit(renamed)

  test("renames every owned symbol — types, members, fields and params follow the prefix") {
    assert(names(before).contains("com.example.demo.Widget"))
    assert(!names(renamed).contains("com.example.demo.Widget"), clue = "upstream name survived")
    assert(names(renamed).contains("sge.ui.demo.Widget"))
    assert(names(renamed).contains("sge.ui.demo.Panel"))
    // a MEMBER is `owner#name`; the `#` must be carried across, not cut at.
    assert(names(renamed).contains("sge.ui.demo.Widget#label"))
    assert(names(renamed).contains("sge.ui.demo.Widget#names"))
    // nothing at all is left in the upstream namespace.
    assertEquals(names(renamed).filter(_.startsWith("com.example")), Set.empty[String])
  }

  test("a NESTED type survives the rename — `$` is a boundary, and both path forms still render") {
    // symbol table: the `$` chain is carried verbatim behind the new prefix.
    assert(names(renamed).contains("sge.ui.demo.Widget$Style"))
    assert(names(renamed).contains("sge.ui.demo.Widget$Handle"))
    assert(names(renamed).contains("sge.ui.demo.Widget$Style#pad"))
    // emission: a STATIC nested type is a companion member (`.`), a genuine INNER class is a type
    // projection (`#`). Both are derived from the renamed `fullName` via the owner chain, so a
    // prefix cut in the wrong place would show up only here.
    assert(clue(out).contains("sge.ui.demo.Widget.Style"))
    assert(clue(out).contains("sge.ui.demo.Widget#Handle"))
  }

  test("emitted package clause and every reference move; no upstream name is left in the output") {
    assert(clue(out).contains("package sge.ui.demo"))
    assert(!out.contains("package com.example.demo"))
    assert(!out.contains("com.example"), clue = "upstream namespace leaked into emitted Scala")
  }

  // ---------------------------------------------------------------------------
  // what must NOT be renamed
  // ---------------------------------------------------------------------------

  test("an EXTERNAL symbol is never renamed, even when the map covers its prefix") {
    // a map that deliberately covers the stdlib: only ownership stops this rewriting `java.lang`.
    val hostile = run(Map("com.example" -> "sge.ui", "java" -> "jvm", "scala" -> "s"))
    val n       = names(hostile)
    assert(n.contains("java.lang.String"), clue = "the JDK was rewritten")
    assert(n.contains("java.util.List"))
    assert(n.contains("scala.Int"))
    assertEquals(n.filter(_.startsWith("jvm")), Set.empty[String])
    assert(!emit(hostile).contains("jvm."))
    assert(clue(emit(hostile)).contains("java.lang.String"))
    // the owned half of the same map still ran.
    assert(n.contains("sge.ui.demo.Widget"))
  }

  test("a prefix must end at a separator — `com.exampl` does not cover `com.example`") {
    val after = run(Map("com.exampl" -> "WRONG"))
    assertEquals(names(after), names(before))
    assertEquals(PackageRenameTransform.check(before, Map("com.exampl" -> "WRONG")).unmatched, List("com.exampl"))
  }

  test("longest prefix wins") {
    val after = run(Map("com.example" -> "a.x", "com.example.demo" -> "b.y.demo"))
    assert(names(after).contains("b.y.demo.Widget"))
    assertEquals(names(after).filter(_.startsWith("a.x")), Set.empty[String])
  }

  test("renaming a TYPE (not a package) also moves its simple name, so the emitter renders it") {
    val after = run(Map("com.example.demo.Widget" -> "com.example.demo.Gadget"))
    assert(names(after).contains("com.example.demo.Gadget"))
    assert(names(after).contains("com.example.demo.Gadget$Style"))
    val o = emit(after)
    assert(clue(o).contains("class Gadget"))
    assert(!o.contains("class Widget"))
  }

  // ---------------------------------------------------------------------------
  // the check (CLAUDE.md §3 — a translation path gets a check at the same time)
  // ---------------------------------------------------------------------------

  test("check counts what will move before the phase, and reports zero residue after it") {
    val policy = Map("com.example" -> "sge.ui")
    val pre    = PackageRenameTransform.check(before, policy)
    assert(pre.matched("com.example") > 0)
    assertEquals(pre.unmatched, Nil)
    // after the phase the SAME map must match nothing — any hit is an owned symbol the rename
    // failed to reach, which is the defect this check exists to catch.
    val post = PackageRenameTransform.check(renamed, policy)
    assertEquals(post.matched, Map.empty[String, Int])
    assertEquals(post.unmatched, List("com.example"))
    assert(post.render.contains("§1b"))
  }

  test("ownership is structural: every owned symbol roots at a unit, no external does") {
    val owned = PackageRenameTransform.ownedSymbols(before)
    val fq    = owned.flatMap(before.symbolOf).map(_.fullName)
    assert(fq.contains("com.example.demo.Widget"))
    assert(fq.contains("com.example.demo.Widget$Style"))
    assert(!fq.contains("java.lang.String"))
    assert(!fq.contains("java.util.List"))
  }
