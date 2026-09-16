package balticporter.transform

import balticporter.core.{ PolicyIssue, PortManifest }
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{ Decision, DecisionLog, Pipeline, Program }

/** ThreadConfinedStaticsTransform — a listed `static final` scratch field becomes a per-thread holder behind a same-named `def`; every other shape is refused and counted. */
class ThreadConfinedStaticsTransformSpec extends munit.FunSuite:

  private def tcs(keys: String*) = new ThreadConfinedStaticsTransform(keys.toSet)

  private case class Ported(after: Program, out: String, log: DecisionLog)

  private def run(java: String, p: ThreadConfinedStaticsTransform): Ported =
    val before       = SpoonTir.fromSource(java, "Demo.java")
    val (after, log) = Pipeline.runTraced(before, List(p))
    Ported(after, new TirEmitter(after, notes = log).emit, log)

  private val scratch =
    """package com.demo;
      |public class Demo {
      |  static final Demo tmp = new Demo();
      |  static Demo open = new Demo();
      |  static final Demo written = new Demo();
      |  static final Demo shared = pick();
      |  static Demo pick() { return null; }
      |  public float x;
      |  public Demo use(Demo other) { tmp.x = other.x; return tmp; }
      |  public Demo other() { return Demo.tmp; }
      |}""".stripMargin

  test("empty set: empty fingerprint, no-op") {
    assertEquals(tcs().surfaceFingerprint, "")
    val ported = run(scratch, tcs())
    assertEquals(ported.log.all.count(_.kind == Decision.Kind.ThreadConfinedStatic), 0)
    assert(!ported.out.contains("ThreadLocal"))
  }

  test("fingerprint is the sorted key list; independent keys union on merge") {
    assertEquals(tcs("a.B#y", "a.B#x").surfaceFingerprint, "fields=a.B#x,a.B#y")
    val b   = PortManifest("base", governs = Set("com.demo"), surface = List(tcs("com.demo.A#x")))
    val dep = b.extendedBy(PortManifest("dep", surface = List(tcs("com.demo.A#x", "com.dep.B#y"))))
    assertEquals(dep.surfaceFold.refusals, Nil)
    val eff = dep.effectiveSurface.collect { case t: ThreadConfinedStaticsTransform => t }
    assertEquals(clue(eff.size), 1)
    assertEquals(eff.head.fields, Set("com.demo.A#x", "com.dep.B#y"))
  }

  test(
    "a static final fresh-allocated field becomes a ThreadLocal holder behind a same-named def; references keep their spelling"
  ) {
    val phase  = tcs("com.demo.Demo#tmp")
    val ported = run(scratch, phase)
    val ds     = ported.log.all.filter(_.kind == Decision.Kind.ThreadConfinedStatic)
    assertEquals(clue(ds.size), 1)
    assertEquals(ds.head.subjectFqn, "com.demo.Demo#tmp")
    // `initialValue`, never `withInitial`: the Scala.js javalib ships no such factory (the reference port's own ISS-832 note)
    assert(
      clue(ported.out).contains("private final val tmp$tl: java.lang.ThreadLocal[Demo] = new java.lang.ThreadLocal[Demo] {")
    )
    assert(clue(ported.out).contains("override def initialValue(): Demo = new Demo()"))
    assert(!ported.out.contains("withInitial"))
    assert(clue(ported.out).contains("def tmp: Demo = tmp$tl.get()"))
    assert(ported.out.contains("Demo.tmp"))
    assert(clue(ported.out).contains("porter: thread-confined-static"))
    assertEquals(phase.policyReport.findings, Nil)
  }

  test("refusals are counted by guard: not-final, assigned-after-init, initialiser-not-fresh-allocation") {
    val java =
      """package com.demo;
        |public class Demo {
        |  static Demo open = new Demo();
        |  static final Demo written = new Demo();
        |  static final Demo shared = pick();
        |  final Demo inst = new Demo();
        |  static Demo pick() { return null; }
        |  static void reset() { Demo.written.copy(open); }
        |  void copy(Demo d) { }
        |  static void swap() { open = null; }
        |  void late() { }
        |}""".stripMargin
    // `written` is assigned in a nested class so the scan must reach past the declaring body.
    val nested = java.replace("  void late() { }", "  static class W { void go() { Demo.written = null; } }")
    val phase  = tcs("com.demo.Demo#open", "com.demo.Demo#written", "com.demo.Demo#shared", "com.demo.Demo#inst")
    val ported = run(nested, phase)
    assertEquals(ported.log.all.count(_.kind == Decision.Kind.ThreadConfinedStatic), 0)
    assert(!ported.out.contains("ThreadLocal"))
    val guards = phase.policyReport.findings.map(f => f.key -> f.detail.takeWhile(_ != ':')).toMap
    assertEquals(guards.get("com.demo.Demo#open"), Some("refused by `not-final`"))
    assertEquals(guards.get("com.demo.Demo#written"), Some("refused by `assigned-after-init`"))
    assertEquals(guards.get("com.demo.Demo#shared"), Some("refused by `initialiser-not-fresh-allocation`"))
    assertEquals(guards.get("com.demo.Demo#inst"), Some("refused by `not-static`"))
    assert(phase.policyReport.findings.forall(f => f.issue == PolicyIssue.Malformed || f.issue == PolicyIssue.Unverifiable))
  }
