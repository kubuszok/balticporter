package balticporter.transform

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline

/** `ContextHolder.capture`: a static's VALUE becomes a field of the type plus companion applies; the
  * type takes no clause, callers pass the context's value at construction (DESIGN.md §8.4). */
class ContextCaptureSpec extends munit.FunSuite:
  private val java =
    """package com.demo;
      |import java.io.File;
      |interface Files { String getExternalStoragePath (); }
      |class Gdx { public static Files files; }
      |class Handle {
      |  protected File f;
      |  protected int t;
      |  public Handle (String n) { f = new File(n); t = 0; }
      |  protected Handle (File f, int t) { this.f = f; this.t = t; }
      |  public File file () {
      |    if (t == 1) return new File(Gdx.files.getExternalStoragePath(), f.getPath());
      |    return f;
      |  }
      |  public Handle child (String n) { return new Handle(new File(f, n), t); }
      |  public static Handle tmp () { return new Handle("t"); }
      |}
      |class Sub extends Handle {
      |  Sub () { super("s"); }
      |}
      |class User {
      |  Handle h () { return new Handle("x"); }
      |}
      |""".stripMargin

  private def holder(capture: Map[String, String]) = ContextHolder(
    holder   = "com.demo.Gdx",
    context  = ContextType.Injected("com.demo.Ctx"),
    members  = Map("files" -> "files"),
    attach   = ContextAttach.Class,
    reader   = ContextReader.Summon,
    boundary = ContextBoundary.Refuse,
    capture  = capture)

  private def section(out: String, from: String, to: String): String =
    out.linesIterator.dropWhile(!_.contains(from)).takeWhile(!_.contains(to)).mkString("\n")

  test("the captured type carries the value as a field, applies per constructor, and no clause") {
    val phase = new GlobalsToImplicitsTransform(holders = List(holder(
      Map("com.demo.Handle" -> "files.getExternalStoragePath() as externalStoragePath = null"))))
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSource(java, "Demo.java"), List(phase))
    val out = new TirEmitter(after, notes = log).emit
    val handle = section(out, "class Handle", "class Sub")
    assert(clue(handle).contains("var externalStoragePath: java.lang.String = null"))
    assert(!handle.linesIterator.exists(l => l.contains("class Handle") && l.contains("using com.demo.Ctx")), handle)
    // the read inside the type is the field
    assert(handle.contains("new java.io.File(this.externalStoragePath, this.f.getPath())"))
    // an in-type construction forwards the field through the companion apply
    assert(handle.contains("Handle.apply(new java.io.File(this.f, n), this.t, this.externalStoragePath)"))
    // a STATIC member of the type is a caller like any other: the context's value, and it threads
    assert(handle.contains("Handle.apply(\"t\", scala.Predef.summon[com.demo.Ctx].files.getExternalStoragePath())"), handle)
    assert(handle.linesIterator.exists(l => l.contains("def tmp") && l.contains("using com.demo.Ctx")), handle)
    // one apply per constructor, the value last
    assert(handle.contains("def apply(n: java.lang.String, externalStoragePath: java.lang.String): Handle"), handle)
    assert(handle.contains("def apply(f: java.io.File, t: scala.Int, externalStoragePath: java.lang.String): Handle"), handle)
    // a declared subclass assigns the field at construction and is threaded for it
    val sub = section(out, "class Sub", "class User")
    assert(clue(sub).contains("this.externalStoragePath = scala.Predef.summon[com.demo.Ctx].files.getExternalStoragePath()"))
    assert(sub.linesIterator.exists(l => l.contains("class Sub") && l.contains("using com.demo.Ctx")), sub)
    // an outside caller passes the value and is threaded
    val user = section(out, "class User", "<<end>>")
    assert(clue(user).contains("Handle.apply(\"x\", scala.Predef.summon[com.demo.Ctx].files.getExternalStoragePath())"))
    assert(user.linesIterator.exists(l => l.contains("class User") && l.contains("using com.demo.Ctx")), user)
    assert(phase.policyReport.findings.isEmpty, phase.policyReport.findings.mkString("\n"))
  }

  test("a malformed entry is reported and nothing is captured") {
    val phase = new GlobalsToImplicitsTransform(holders = List(holder(Map("com.demo.Handle" -> "files.nope as x"))))
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSource(java, "Demo.java"), List(phase))
    val out = new TirEmitter(after, notes = log).emit
    assert(clue(out).linesIterator.exists(l => l.contains("class Handle") && l.contains("using com.demo.Ctx")))
    assert(phase.policyReport.findings.exists(_.detail.contains("as <param>")), phase.policyReport.findings.mkString("\n"))
  }
