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
