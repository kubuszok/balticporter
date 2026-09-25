package balticporter.transform

import balticporter.core.PolicyIssue
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline
import balticporter.transform.CallSiteSubstitutionTransform.{ Entry, Hole, Template, ThisRefused }

/** [[CallSiteSubstitutionTransform]]'s `{this}` hole: the instance of the nearest named class whose member encloses the call, rendered as a tree so the emitter qualifies it inside a nested class body;
  * refused and counted wherever no such instance exists.
  */
class CallSiteSubstitutionThisSpec extends munit.FunSuite:

  private val sources = List(
    "demo/Err.java" ->
      """package demo;
        |public class Err extends RuntimeException { public Err(String m) { super(m); } }
        |""".stripMargin,
    "dep/Handle.java" ->
      """package dep;
        |public class Handle {
        |  void plain() { throw new demo.Err("plain"); }
        |  void anon() {
        |    Runnable r = new Runnable() { public void run() { throw new demo.Err("anon"); } };
        |  }
        |  void lambda() { Runnable r = () -> { throw new demo.Err("lambda"); }; }
        |  static void stat() { throw new demo.Err("static"); }
        |  static void staticAnon() {
        |    Runnable r = new Runnable() { public void run() { throw new demo.Err("staticAnon"); } };
        |  }
        |  class Inner { void in() { throw new demo.Err("inner"); } }
        |}
        |""".stripMargin
  )

  private val ctor = "demo.Err#<init>(String)"

  private def emit(phase: CallSiteSubstitutionTransform): String =
    new TirEmitter(Pipeline.run(SpoonTir.fromSources(sources), List(phase))).emit

  private def withThis = new CallSiteSubstitutionTransform(List(Entry(ctor, "demo.Errors.read({this}, {arg0})")))

  test("the grammar parses {this} as its own hole") {
    val t = Template.parse("f({this}, {arg0})").fold(w => fail(w), identity)
    assertEquals(t.holes, List(Hole.This, Hole.Arg(0)))
    assert(t.usesThis && !t.usesRecv)
  }

  test("a plain instance method: {this} renders the enclosing instance") {
    val out = emit(withThis)
    assert(clue(out).contains("""demo.Errors.read(this, "plain")"""))
    // a named inner class's member names the INNER instance
    assert(clue(out).contains("""demo.Errors.read(this, "inner")"""))
  }

  test("inside an anonymous class, {this} names the OUTER class — never the anonymous instance") {
    val out = emit(withThis)
    assert(clue(out).contains("""demo.Errors.read(Handle.this, "anon")"""))
  }

  test("inside a lambda, {this} is Scala's lexical this — the lambda does not rebind it") {
    val phase = withThis
    val out   = emit(phase)
    assert(clue(out).contains("""demo.Errors.read(this, "lambda")"""))
    assert(!phase.refusals.exists(_._2.contains("lambda")), phase.refusals)
  }

  test("a STATIC member has no instance: refused, counted, and the call left as java wrote it") {
    val phase = withThis
    val out   = emit(phase)
    assert(clue(out).contains("""new demo.Err("static")"""))
    // an anonymous class in a static member has no enclosing instance either
    assert(clue(out).contains("""new demo.Err("staticAnon")"""))
    val reasons = phase.refusals.map(_._2)
    assertEquals(reasons.size, 2, reasons)
    assert(reasons.forall(r => r.startsWith(ThisRefused) && r.contains("static member")), reasons)
    assert(clue(phase.policyReport.findings).count(f => f.key == ctor && f.issue == PolicyIssue.Unverifiable) == 2)
    assertEquals(phase.substituted, List(ctor -> 4))
  }

  test("a template WITHOUT {this} is untouched by the new guard: every site rewritten, static ones included") {
    val tmpl  = "demo.Errors.plain({arg0})"
    val phase = new CallSiteSubstitutionTransform(List(Entry(ctor, tmpl)))
    val out   = emit(phase)
    for m <- List("plain", "anon", "lambda", "static", "staticAnon", "inner") do assert(clue(out).contains(s"""demo.Errors.plain("$m")"""), m)
    assertEquals(phase.refusals, Nil)
    assertEquals(phase.substituted, List(ctor -> 6))
    assertEquals(phase.surfaceFingerprint, s"$ctor=${tmpl.hashCode.toHexString}")
  }
