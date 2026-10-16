package balticporter.transform

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline

/** `ContextHolder.attachClass`: under a holder that attaches on methods, a named type takes the clause on its constructor and its members read it from there.
  */
class ContextAttachClassSpec extends munit.FunSuite:
  private val java =
    """package com.demo;
      |interface Clock { float time (); }
      |class Ai { public static Clock clock; }
      |class Walker {
      |  float last;
      |  void step () { last = Ai.clock.time(); }
      |}
      |class Runner {
      |  float last;
      |  void step () { last = Ai.clock.time(); }
      |}
      |""".stripMargin

  private def holder(attachClass: Set[String]) = ContextHolder(
    holder = "com.demo.Ai",
    context = ContextType.Injected("com.demo.Ctx"),
    members = Map("clock" -> "clock"),
    attach = ContextAttach.Method,
    attachClass = attachClass
  )

  private def emit(h: ContextHolder): (String, GlobalsToImplicitsTransform) =
    val phase        = new GlobalsToImplicitsTransform(holders = List(h))
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSource(java, "Demo.java"), List(phase))
    (new TirEmitter(after, notes = log).emit, phase)

  test("a named type takes the clause on its constructor; every other reader on its method") {
    val (out, phase) = emit(holder(Set("com.demo.Walker")))
    val walker       = out.linesIterator.dropWhile(!_.contains("class Walker")).takeWhile(!_.contains("class Runner")).mkString("\n")
    assert(clue(walker).linesIterator.next().contains("(using com.demo.Ctx)"), out)
    assert(!walker.contains("def step()(using"), out)
    assert(clue(out).contains("def step()(using com.demo.Ctx)"), out)
    assert(phase.policyReport.findings.isEmpty, phase.policyReport.findings.mkString("\n"))
  }

  test("an empty key is the method attachment it was, and fingerprints the same") {
    val (out, _) = emit(holder(Set.empty))
    assert(!clue(out).linesIterator.exists(l => l.contains("class Walker") && l.contains("using")), out)
    assertEquals(holder(Set.empty).fingerprint, holder(Set.empty).copy(attachClass = Set.empty).fingerprint)
    assertNotEquals(holder(Set.empty).fingerprint, holder(Set("com.demo.Walker")).fingerprint)
  }
