package balticporter.testkit

import balticporter.core.RuntimeArtifact
import balticporter.transform.CollectionsTransform

/** The testkit's own suite — and the worked example a consumer copies.
  *
  * Every assertion below was, until this module had sources, four lines of preamble repeated at
  * the top of each engine spec: parse, run the pipeline, build an emitter with the right external
  * table, look a symbol up by fully-qualified name.
  */
class PortFixtureSpec extends PortSuite:

  private val java =
    """package demo;
      |import java.util.*;
      |class Bag {
      |  private List<String> items = new ArrayList<String>();
      |  void add(String s) { items.add(s); }
      |  String first() { return items.get(0); }
      |}
      |""".stripMargin

  test("port() runs the phases and emits") {
    val p = port(java, new CollectionsTransform)
    assertEmits(p, "scala.collection.mutable.Buffer[java.lang.String]")
    assertEmits(p, "this.items += s")
    assertNotEmits(p, "java.util.")
  }

  test("the xref is available on both sides, so a retyping can be asserted as VACATED") {
    assertVacated(port(java, new CollectionsTransform), "java.util.List")
  }

  test("with no phases the fixture is the emitter's identity — the honest baseline") {
    val p = port(java)
    assertEmits(p, "java.util.List")
    assert(p.plan.isEmpty)
  }

  test("the fixture DERIVES the runtime plan from the phases, as a real run does") {
    val p = port(java, new CollectionsTransform)
    assertEquals(p.plan.dependency, Some(RuntimeArtifact.coordinates))
    // and hands the emitter the external-concrete table without the test naming it
    assertEquals(p.emitter, p.emitter) // force construction
    assertEquals(p.plan.concreteMembers, CollectionsTransform.runtimeConcreteMembers)
  }

  test("a failed assertEmits shows the whole emitted output") {
    val e = intercept[munit.FailException](assertEmits(port(java), "definitely-not-there"))
    assert(e.getMessage.contains("--- emitted ---"), clue(e.getMessage))
    assert(e.getMessage.contains("class Bag"), clue(e.getMessage))
  }

  test("EVERY entry point is FATAL about an undischarged obligation — `parse` most of all") {
    // `port` and `portAll` took `CatalogLog(fatal = true)` and `parse` took `CatalogLog.discarding`
    // — the log for a caller that does not want one. That is backwards: an undischarged obligation
    // is a LOWERING ARM that returned without consulting a difference the catalog attaches to it,
    // so a FRONTEND-ONLY spec is the closest witness there is, and it was the one path where a
    // lowering could stop asking with every spec still green (`DESIGN.md` §2.8 stages enforcement
    // FATAL in the testkit, precisely because a port run has diagnostics to protect and a spec has
    // none).
    val (_, log) = PortFixture.parseWith("package demo; public class P { void f(byte b) { b += 3; } }")
    assert(log.fatal, "the frontend-only fixture must enforce what the porting ones enforce")
    // JS-E03 and JS-E17 are both discharged; nothing is left.
    assertEquals(log.undischarged.map(_.id), Nil)
    assert(log.consulted(balticporter.catalog.JS.E(3)) > 0, "the log is not even live")
    assert(log.consulted(balticporter.catalog.JS.E(17)) > 0, "JS-E17 must be consulted")

    // …and at the expression dispatch: JS-E04 and JS-E17 are both discharged too.
    val (_, open) = PortFixture.parseWith("package demo; public class Q { int f(byte b) { return (b += 3); } }")
    assertEquals(open.undischarged.map(_.id), Nil)
    assert(open.consulted(balticporter.catalog.JS.E(4)) > 0,
      "the expression dispatch owes JS-E04 and discharges it")
    assert(open.consulted(balticporter.catalog.JS.E(17)) > 0,
      "JS-E17 is consulted at the expression dispatch too")
  }
