package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.TypeRedirectTransform

/** `TypeRedirectTransform` promises that EVERY reference to a redirected type moves together —
  * "so a partial redirect is impossible", in its own words. This suite is what holds it to that.
  *
  * The promise was false for two years of one library's use, because the first port to configure
  * the phase redirected a type with no static surface and no annotation use. A second library
  * redirected ten third-party types and found the gap: `transformType` reaches TYPE occurrences,
  * and a static call's receiver, a static field read and an annotation are not type occurrences —
  * the emitter renders the first two from a `Tree.Ident`'s SYMBOL and the third from
  * `Symbol.annotations`. Every one of them kept naming the type the port was configured not to
  * have. Nothing counted it: the emitted file simply does not compile, at exactly the sites the
  * redirect existed to fix.
  *
  * So each case below is one KIND of occurrence, asserted through the emitted text, and the
  * negative half (`assertNotEmits`) is the half that would have caught the original gap.
  */
class TypeRedirectTransformSpec extends PortSuite:

  private val java =
    """package com.demo;
      |
      |@interface Marker {}
      |
      |class Helper {
      |  static final int LIMIT = 4;
      |  static void check(boolean ok) {}
      |  int size() { return 0; }
      |}
      |
      |class Uses {
      |  Helper h = new Helper();
      |
      |  @Marker
      |  Helper make(Helper other) {
      |    Helper.check(other != null);
      |    int n = Helper.LIMIT + h.size();
      |    return new Helper();
      |  }
      |}
      |""".stripMargin

  private def redirected =
    port(java, new TypeRedirectTransform(Map(
      "com.demo.Helper" -> "com.other.Slab",
      "com.demo.Marker" -> "com.other.Tag",
    )))

  test("a TYPE occurrence moves — field type, parameter, result, and `new`") {
    val p = redirected
    assertEmits(p, "com.other.Slab")
    assertNotEmits(p, "com.demo.Helper")
  }

  test("a STATIC CALL's receiver moves — the case `transformType` cannot see") {
    // `Helper.check(…)` is an `Apply` whose `fun` selects through an `Ident` of the TYPE symbol.
    // The emitter renders that `Ident` from its SYMBOL, so the node's `TypeRepr` being remapped
    // changes nothing at all. Left alone this emitted `com.demo.Helper.check(…)` in a port that
    // does not declare `com.demo.Helper`.
    assertEmits(redirected, "com.other.Slab.check(")
  }

  test("a STATIC FIELD read moves too — the same `Ident`, a different member kind") {
    assertEmits(redirected, "com.other.Slab.LIMIT")
  }

  test("an ANNOTATION's type moves — it lives on the SYMBOL, not in the tree") {
    // `Symbol.annotations` is routed through `transformType` by `StandardTraversal.mapSymbols`.
    // Before that it was not, so every retyping phase in the engine had the same blind spot and
    // this is the assertion that fails if the traversal is narrowed again.
    val p = redirected
    assertEmits(p, "@com.other.Tag")
    assertNotEmits(p, "com.demo.Marker")
  }

  test("an INSTANCE member is untouched — the redirect moves TYPES, never call sites") {
    // `h.size()` still reads `size` off whatever `h` now is. A redirect that renamed members would
    // be a different phase with a different contract; the replacement type is required to be
    // shape-compatible precisely so that this needs no rewriting.
    assertEmits(redirected, ".size()")
  }

  test("an empty map is a no-op, byte for byte") {
    val plain = port(java)
    val empty = port(java, new TypeRedirectTransform(Map.empty))
    assertEquals(empty.out, plain.out)
  }

  test("a source that occurs nowhere is reported, not silently ignored") {
    val phase = new TypeRedirectTransform(Map("com.demo.Absent" -> "com.other.Slab"))
    port(java, phase)
    val findings = phase.policyReport.findings
    assertEquals(clue(findings).size, 1)
    assertEquals(findings.head.key, "com.demo.Absent")
  }
