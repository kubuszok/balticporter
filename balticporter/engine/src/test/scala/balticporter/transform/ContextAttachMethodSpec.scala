package balticporter.transform

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline

/** `attachMethod`: under a holder that attaches on the class, a named type takes the clause on the constructors and methods that need it, so its context-free constructor stays context-free.
  */
class ContextAttachMethodSpec extends munit.FunSuite:
  private val java =
    """package com.demo;
      |interface Files { String read (String name); }
      |class App { public static Files files; }
      |class Glyphs {
      |  String text;
      |  Glyphs () { text = ""; }
      |  Glyphs (String name) { text = App.files.read(name); }
      |  String reload (String name) { return App.files.read(name); }
      |  int length () { return text.length(); }
      |}
      |class Panel {
      |  String text;
      |  void load () { text = App.files.read("panel"); }
      |}
      |""".stripMargin

  private def holder = ContextHolder(
    holder = "com.demo.App",
    context = ContextType.Injected("com.demo.Ctx"),
    members = Map("files" -> "files"),
    attach = ContextAttach.Class
  )

  private def emit(exts: List[ContextHolderExtension]): (String, GlobalsToImplicitsTransform) =
    val phase        = new GlobalsToImplicitsTransform(holders = List(holder), extensions = exts)
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSource(java, "Demo.java"), List(phase))
    (new TirEmitter(after, notes = log).emit, phase)

  private def section(out: String, from: String, to: String): String =
    out.linesIterator.dropWhile(!_.contains(from)).takeWhile(l => to.isEmpty || !l.contains(to)).mkString("\n")

  test("a named type takes the clause on the members that read the context; every other type on its class") {
    val (out, phase) = emit(List(ContextHolderExtension("com.demo.App", attachMethod = Set("com.demo.Glyphs"))))
    val glyphs       = section(out, "class Glyphs", "class Panel")
    assert(!clue(glyphs).linesIterator.next().contains("using"), out)
    assert(glyphs.contains("def reload(name: java.lang.String)(using com.demo.Ctx)"), out)
    assert(
      glyphs.linesIterator.exists(l => l.contains("def this(name: java.lang.String)") && l.contains("(using com.demo.Ctx)")),
      out
    )
    assert(!glyphs.linesIterator.exists(l => l.contains("def length") && l.contains("using")), out)
    assert(section(out, "class Panel", "").linesIterator.next().contains("(using com.demo.Ctx)"), out)
    assert(phase.policyReport.findings.isEmpty, phase.policyReport.findings.mkString("\n"))
  }

  test("an empty key is the class attachment it was, and fingerprints the same") {
    val (out, _) = emit(List(ContextHolderExtension("com.demo.App")))
    assert(clue(section(out, "class Glyphs", "class Panel")).linesIterator.next().contains("(using com.demo.Ctx)"), out)
    assertEquals(holder.fingerprint, holder.extendedBy(ContextHolderExtension("com.demo.App")).fingerprint)
    assertNotEquals(
      holder.fingerprint,
      holder.extendedBy(ContextHolderExtension("com.demo.App", attachMethod = Set("com.demo.Glyphs"))).fingerprint
    )
  }
