package balticporter.transform

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline

/** `ContextHolder.through`: a class handed the service a mapped static lives on reads the static
  * off its own member and takes no clause (DESIGN.md §8.4). */
class ContextThroughSpec extends munit.FunSuite:
  private val java =
    """package com.demo;
      |interface GL20 { void glClear (int mask); }
      |interface Graphics { GL20 getGL20 (); void setGL20 (GL20 gl); int getWidth (); }
      |class Gdx {
      |  public static Graphics graphics;
      |  public static GL20 gl;
      |}
      |class Profiler {
      |  private final Graphics graphics;
      |  public Profiler (Graphics graphics) { this.graphics = graphics; }
      |  void clear () { Gdx.gl.glClear(1); }
      |  int width () { return Gdx.graphics.getWidth(); }
      |  void install (GL20 x) { Gdx.gl = x; }
      |}
      |class User {
      |  int w () { return Gdx.graphics.getWidth(); }
      |}
      |""".stripMargin

  private def holder(through: Map[String, String]) = ContextHolder(
    holder   = "com.demo.Gdx",
    context  = ContextType.Injected("com.demo.Ctx"),
    members  = Map("graphics" -> "graphics", "gl" -> "graphics.getGL20()"),
    attach   = ContextAttach.Class,
    reader   = ContextReader.Summon,
    boundary = ContextBoundary.Refuse,
    through  = through)

  test("a `through` type reads the statics off its own member and takes no clause") {
    val phase = new GlobalsToImplicitsTransform(holders = List(holder(Map("com.demo.Profiler" -> "graphics"))))
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSource(java, "Demo.java"), List(phase))
    val out = new TirEmitter(after, notes = log).emit
    val profiler = out.linesIterator.dropWhile(!_.contains("class Profiler")).takeWhile(!_.contains("class User")).mkString("\n")
    assert(clue(profiler).contains("this.graphics.getGL20().glClear(1)"))
    assert(profiler.contains("this.graphics.getWidth()"))
    assert(profiler.contains("this.graphics.setGL20(x)"))
    assert(!profiler.contains("using com.demo.Ctx"), profiler)
    // an ordinary reader is threaded as before
    assert(clue(out).linesIterator.exists(l => l.contains("class User") && l.contains("(using com.demo.Ctx)")))
    assert(phase.policyReport.findings.isEmpty, phase.policyReport.findings.mkString("\n"))
  }

  test("an entry naming no such member is a counted finding and the type threads as before") {
    val phase = new GlobalsToImplicitsTransform(holders = List(holder(Map("com.demo.Profiler" -> "nothing"))))
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSource(java, "Demo.java"), List(phase))
    val out = new TirEmitter(after, notes = log).emit
    assert(clue(out).contains("class Profiler(graphics$p: com.demo.Graphics)(using com.demo.Ctx)") ||
      out.contains("Profiler(") && out.contains("(using com.demo.Ctx)"))
    assert(phase.policyReport.findings.exists(_.detail.contains("not a non-static field")),
      phase.policyReport.findings.mkString("\n"))
  }

  test("the fingerprint moves with the key and only when it is non-empty") {
    assertEquals(holder(Map.empty).fingerprint, holder(Map.empty).copy(through = Map.empty).fingerprint)
    assertNotEquals(holder(Map.empty).fingerprint, holder(Map("com.demo.Profiler" -> "graphics")).fingerprint)
  }
