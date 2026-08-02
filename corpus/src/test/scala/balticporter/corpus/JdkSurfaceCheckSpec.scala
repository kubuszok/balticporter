package balticporter.corpus

import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.PortSuite
import balticporter.tir.{ExternalUsage, JdkSurfaceCheck, Pipeline, Program, SymId}
import balticporter.transform.CollectionsTransform

/** The port's JDK wall, classified — `JdkSurfaceCheck`.
  *
  * Every answer this check gives has to come from a table something else owns, so every test below
  * asserts a DISPOSITION rather than a count: which of §1's three kinds an agent is being sent to
  * is the whole product (CLAUDE.md §4.45), and a check that says "3 findings" without saying who
  * fixes them costs its reader the investigation it was built to save.
  */
class JdkSurfaceCheckSpec extends PortSuite:

  private def rows(p: Program) = ExternalUsage.external(p)

  private def ported(src: String, withPhase: Boolean): Program =
    val before = SpoonTir.fromSource(src)
    if withPhase then Pipeline.run(before, List(new CollectionsTransform)) else before

  private def dispositions(src: String, withPhase: Boolean): Map[String, String] =
    val after = ported(src, withPhase)
    JdkSurfaceCheck.classify(rows(after), CollectionsTransform.jdkMapping(ran = withPhase))
      .map((r, d) => r.member.getOrElse(r.fullName) -> d.label)
      .toMap

  private def findings(src: String, withPhase: Boolean) =
    val after = ported(src, withPhase)
    JdkSurfaceCheck.check(after, rows(after), after.units,
                          CollectionsTransform.jdkMapping(ran = withPhase))

  // -------------------------------------------------------------------------------------------
  // the member lanes
  // -------------------------------------------------------------------------------------------

  private val statics =
    """package demo;
      |import java.util.*;
      |class Util {
      |  void a(List<String> xs) { Collections.swap(xs, 0, 1); }
      |  void b(List<String> xs) { Collections.rotate(xs, 1); }
      |  int  c(int x, int y)    { return Math.max(x, y); }
      |}
      |""".stripMargin

  test("a static the phase REWRITES leaves the SURFACE — the rows are what the port still calls") {
    // the strongest outcome there is, and the reason `mapped` is rarer than a reader expects: the
    // call is gone, so the dependency is gone. A check reading the PRE-pipeline program would show
    // a comfortable `mapped` row for a member the port no longer references at all.
    assertEquals(clue(dispositions(statics, withPhase = true)).get("java.util.Collections#swap"), scala.None)
  }

  test("…and it is `mappable` with the phase off — an OFFER, not a hole the phase made") {
    assertEquals(clue(dispositions(statics, withPhase = false)).get("java.util.Collections#swap"), Some("mappable"))
  }

  test("a rewrite that keeps the java SYMBOL is `mapped` — `xs.size()` becomes `xs.size`, still a call") {
    val src =
      """package demo;
        |import java.util.*;
        |class S { int n(List<String> xs) { return xs.size(); } }
        |""".stripMargin
    assertEquals(clue(dispositions(src, withPhase = true)).get("java.util.List#size"), Some("mapped"))
    assertEquals(clue(dispositions(src, withPhase = false)).get("java.util.List#size"), Some("mappable"))
  }

  test("a static in a rewritten FAMILY with no arm is the FINDING — a `Collections.swap` demand, one lane earlier") {
    val d = dispositions(statics, withPhase = true)
    assertEquals(clue(d).get("java.util.Collections#rotate"), Some("unhandled"))
    val f = findings(statics, withPhase = true).filter(_.subject.contains("rotate"))
    assertEquals(clue(f).size, 1)
    assert(clue(f.head.disposition.classification).contains("§1(b)"))
  }

  test("…and the same static is `kept` with the phase off: nothing retyped, so nothing is a hole") {
    assertEquals(clue(dispositions(statics, withPhase = false)).get("java.util.Collections#rotate"), Some("kept"))
  }

  test("NEGATIVE: a JDK member no phase touches is `kept` and never a finding — `Math.max` is not a work list") {
    assertEquals(clue(dispositions(statics, withPhase = true)).get("java.lang.Math#max"), Some("kept"))
    assert(!findings(statics, withPhase = true).exists(_.subject.contains("Math#max")))
  }

  test("a REFUSAL is reported with its citation, never as the wall") {
    // `Map.Entry#setValue` and not `Collections#unmodifiableList`: that one WAS the example here
    // and is now rewritten, which the stale-refusal guard below is what caught. This refusal is
    // one no runtime type can lift — a `Tuple2` has no write-through to the map it came from, so
    // the call must fail to COMPILE rather than become a write to a detached copy.
    val src =
      """package demo;
        |import java.util.*;
        |class U { void bump(Map.Entry<String, Integer> e) { e.setValue(1); } }
        |""".stripMargin
    val d = dispositions(src, withPhase = true)
    assert(clue(d).exists((k, v) => k.endsWith("#setValue") && v == "refused"))
    val r = JdkSurfaceCheck.Refusals.find(_.api.endsWith("#setValue")).get
    assert(clue(r.cite).nonEmpty)
  }

  test("every engine refusal carries a citation — an uncited refusal is not a refusal") {
    JdkSurfaceCheck.Refusals.foreach { r =>
      assert(clue(r.why).nonEmpty, r.api)
      assert(clue(r.cite).nonEmpty, r.api)
    }
  }

  test("STALE-REFUSAL guard: a refusal the tables now handle is itself a finding") {
    // `toCollection` was refused in a comment for a release after the `into` arm started handling
    // it — "a comment that still names a case the code handles is the reason not to look".
    // The synthetic stale entry used to be `Collections#unmodifiableList`, and this guard is what
    // RETIRED it: that member is now rewritten, so the pair stopped being a contradiction and the
    // test went red — which is the guard reporting on its own table rather than on a fixture.
    // `Map.Entry#setValue` is a live refusal (a `Tuple2` has no write-through), so pairing it with
    // a mapping that claims to handle it reproduces the contradiction.
    val m = CollectionsTransform.jdkMapping(ran = true)
      .copy(statics = CollectionsTransform.handledStatics + "java.util.Map$Entry#setValue")
    val row = ExternalUsage.Row(SymId(1), "setValue",
      Some("java.util.Map$Entry"), "setValue", scala.None, Nil)
    assertEquals(JdkSurfaceCheck.classify(List(row), m).head._2.label, "stale-refusal")
  }

  test("a REFUSAL whose member no longer exists is caught by the same guard, in the other direction") {
    // the stale-refusal guard is only half of "an uncited refusal is not a refusal": a citation
    // naming a member the phase HANDLES is stale, and so is one naming a member nothing has. The
    // second half is checkable here because the refusal keys are `owner#name` in the same grammar
    // the phase's own table uses.
    val handled = CollectionsTransform.handledStatics
    val clashing = JdkSurfaceCheck.Refusals.map(_.api).filter(handled.contains)
    assertEquals(clue(clashing), Nil,
      "a refusal names a member `CollectionsTransform` already rewrites — one of the two is out of date")
  }

  test("a CONSTRUCTOR of a retyped type is not a hole — retyping the type IS the rewrite for `new`") {
    val src =
      """package demo;
        |import java.util.*;
        |class C { Map<String, String> m = new HashMap<String, String>(); }
        |""".stripMargin
    assertEquals(clue(dispositions(src, withPhase = true)).get("java.util.HashMap#<init>"), Some("mapped"))
    // …and the flag is not an assumption: a phase that retypes without touching `new` reports it.
    val after = ported(src, withPhase = true)
    val noCtors = CollectionsTransform.jdkMapping(ran = true).copy(constructors = false)
    assertEquals(JdkSurfaceCheck.classify(rows(after), noCtors)
      .collectFirst { case (r, d) if r.member.contains("java.util.HashMap#<init>") => d.label },
      Some("unhandled"))
  }

  test("the EMPTY mapping makes the whole check a `kept` report — an empty parameter is a no-op") {
    val after = ported(statics, withPhase = false)
    val ds = JdkSurfaceCheck.classify(rows(after), JdkSurfaceCheck.noMapping).map(_._2.label).distinct
    assertEquals(clue(ds.filterNot(_ == "kept").filterNot(_ == "refused")), Nil)
    assertEquals(clue(JdkSurfaceCheck.check(after, rows(after), after.units, JdkSurfaceCheck.noMapping)), Nil)
  }

  // -------------------------------------------------------------------------------------------
  // K9, as a DERIVED demand
  // -------------------------------------------------------------------------------------------

  private val foreachSrc =
    """package demo;
      |import java.util.*;
      |class Rooms {
      |  private List<String> rooms = new ArrayList<String>();
      |  void go() { for (String r : rooms) { System.out.println(r); } }
      |}
      |""".stripMargin

  test("K9: an enhanced-for over a KEPT java.util.List is a named finding — before any compile") {
    val fs = findings(foreachSrc, withPhase = false).filter(_.disposition.label == "kept-iterable")
    assertEquals(clue(fs).size, 1)
    assertEquals(fs.head.subject, "java.util.List")
    assert(clue(fs.head.disposition.classification).contains("K9"))
  }

  test("K9 NEGATIVE: the same loop over a RETYPED receiver is clean — decided from the phase's table") {
    val fs = findings(foreachSrc, withPhase = true).filter(_.disposition.label == "kept-iterable")
    assertEquals(clue(fs), Nil)
  }

  test("K9 NEGATIVE: a loop over a type the PROGRAM declares is not this check's business") {
    val src =
      """package demo;
        |class Own implements java.lang.Iterable<String> {
        |  public java.util.Iterator<String> iterator() { return null; }
        |}
        |class Uses { void go(Own o) { for (String s : o) { } } }
        |""".stripMargin
    val fs = findings(src, withPhase = false).filter(_.disposition.label == "kept-iterable")
    assert(!clue(fs).exists(_.subject.contains("Own")))
  }

  test("K9 NEGATIVE: an ARRAY is iterated natively and is never a demand") {
    val src =
      """package demo;
        |class A { void go(String[] xs) { for (String x : xs) { } } }
        |""".stripMargin
    assertEquals(clue(findings(src, withPhase = false)).filter(_.disposition.label == "kept-iterable"), Nil)
  }
