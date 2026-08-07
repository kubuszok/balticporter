package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.tir.*
import balticporter.tir.OverloadRiskCheck.Issue

/** `JS-C22` and `JS-C23` — java's three-phase overload applicability, counted as a RISK.
  *
  * Every test is in both directions, and the ones that matter most are the NEGATIVES: an
  * over-approximation is only worth having if it declines to report the shapes both languages agree
  * about, and those are the tests a widening of the rule would break first.
  */
class OverloadRiskSpec extends PortSuite:

  private def report(java: String) =
    val p = port(java)
    val ov = new OverloadRiskCheck.Overloads(p.after)
    (p, OverloadRiskCheck.check(p.after, p.after.units, ov))

  // -- the three phase boundaries, each fired ---------------------------------------------------

  test("VarargPhaseSpan — a fixed-arity and a variable-arity candidate both applicable is java's 2/3 boundary") {
    val (_, r) = report(
      """public class A {
        |  void f(String a) { }
        |  void f(String... a) { }
        |  void go() { f("x"); }
        |}
        |""".stripMargin)
    assert(r.findings.map(_.issue).contains(Issue.VarargPhaseSpan), r.findings.toString)
  }

  test("BoxingPhaseSpan — a primitive against its wrapper at one position is java's 1/2 boundary") {
    val (_, r) = report(
      """public class A {
        |  void f(int a) { }
        |  void f(Integer a) { }
        |  void go() { f(1); }
        |}
        |""".stripMargin)
    assert(r.findings.map(_.issue).contains(Issue.BoxingPhaseSpan), r.findings.toString)
  }

  test("…and a primitive against a UNIVERSAL slot is the same boundary — java admits `f(int)` without boxing and stops") {
    val (_, r) = report(
      """public class A {
        |  void f(int a) { }
        |  void f(Object a) { }
        |  void go() { f(1); }
        |}
        |""".stripMargin)
    assert(r.findings.map(_.issue).contains(Issue.BoxingPhaseSpan), r.findings.toString)
  }

  test("GenericTieBreak — scala's relative-weight rule prefers the NON-generic alternative and java's does not") {
    val (_, r) = report(
      """public class A {
        |  <T> void f(T a) { }
        |  void f(String a) { }
        |  void go() { f("x"); }
        |}
        |""".stripMargin)
    assert(r.findings.map(_.issue).contains(Issue.GenericTieBreak), r.findings.toString)
  }

  // -- the NEGATIVES: what the narrowing declines, and why ---------------------------------------

  test("two candidates of DIFFERENT ARITY are not a risk — neither language has a choice to make") {
    val (_, r) = report(
      """public class A {
        |  void f(String a) { }
        |  void f(String a, String b) { }
        |  void go() { f("x"); }
        |}
        |""".stripMargin)
    assertEquals(r.findings, Nil)
    assertEquals(r.overloaded, 0)
  }

  test("two candidates separated only by UNRELATED REFERENCE types are counted in the denominator and NOT reported") {
    val (_, r) = report(
      """public class A {
        |  void f(String a) { }
        |  void f(Thread a) { }
        |  void go() { f("x"); }
        |}
        |""".stripMargin)
    assertEquals(r.findings, Nil)
    assert(r.overloaded >= 1, "the call must still reach the denominator, or the rate is unreadable")
  }

  test("a call with ONE candidate is not overloaded at all — the denominator counts it as a call and nothing more") {
    val (_, r) = report(
      """public class A {
        |  void f(String a) { }
        |  void go() { f("x"); }
        |}
        |""".stripMargin)
    assertEquals(r.findings, Nil)
    assertEquals(r.overloaded, 0)
    assert(r.calls >= 1)
  }

  test("an EXTERNAL callee is not reported: its overloads are in a class file this check cannot see") {
    val (_, r) = report(
      """public class A {
        |  void go() { String.valueOf(1); }
        |}
        |""".stripMargin)
    assertEquals(r.findings, Nil)
  }

  // -- the denominator, which is what makes the over-approximation readable ---------------------

  test("the report carries its own denominator — calls, overloaded calls, and the reported subset") {
    val (_, r) = report(
      """public class A {
        |  void f(int a) { }
        |  void f(Integer a) { }
        |  void g(String a) { }
        |  void go() { f(1); g("x"); }
        |}
        |""".stripMargin)
    assert(r.calls > r.overloaded, "not every call is overloaded, and the summary must be able to say so")
    assert(r.overloaded >= r.findings.size)
    assert(OverloadRiskCheck.summary(r).contains("applicable candidate"))
  }

  test("every issue carries a §1 classification and names its catalog row (§4.45)") {
    Issue.values.foreach { i =>
      val c = OverloadRiskCheck.Issue.classification(i)
      assert(c.contains("§1("), s"$i does not say which of §1's three kinds the fix is")
      assert(c.contains("JS-C2"), s"$i does not name the catalog row it counts")
    }
  }

  // -- INHERITED overloads, which is where a same-owner-only candidate set would read zero -------

  test("a candidate inherited from a program-declared ANCESTOR is in the set") {
    val (_, r) = report(
      """public class A {
        |  static class P { void f(Object a) { } }
        |  static class C extends P { void f(int a) { } void go() { f(1); } }
        |}
        |""".stripMargin)
    assert(r.findings.map(_.issue).contains(Issue.BoxingPhaseSpan), r.findings.toString)
  }
