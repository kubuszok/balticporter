package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.tir.Decision

/** JAVA'S FOUR ACCESS LEVELS, pinned through the pipeline — `DESIGN.md` §8.7.
  *
  * Three of Java's four levels used to collapse onto "no modifier at all": `protected` was dropped
  * wholesale during an error burn-down, package-private could not even be STATED in the TIR, and a
  * type's `private` was erased at the class header. Every one of those is a WIDENING that no
  * compile, no check count and no test can see — the port compiles perfectly with every member
  * public — which is why the mapping needs specs rather than a measurement.
  *
  * Each negative below asserts the RECORDED fallback, not merely the absence of the qualifier: a
  * widening the port cannot state is the failure this whole section exists to remove.
  */
class VisibilitySpec extends PortSuite:

  private def widenings(p: balticporter.testkit.Ported): List[Decision] =
    p.emitter.ownDecisions.filter(_.kind == Decision.Kind.WidenedVisibility)

  private def causes(p: balticporter.testkit.Ported): List[String] =
    widenings(p).flatMap(_.detail.get("cause"))

  // -------------------------------------------------------------------------
  // The mapping matrix
  // -------------------------------------------------------------------------

  test("the four levels render, and only the residue records") {
    val p = port(
      """package demo.util;
        |public class Holder {
        |  private int hidden;
        |  int shared;
        |  protected int guarded;
        |  public int open;
        |  private void hide() {}
        |  void share() {}
        |  protected void guard() {}
        |  public void show() {}
        |}
        |""".stripMargin
    )
    assertEmits(p, "private var hidden")
    assertEmits(p, "private[util] var shared")
    assertEmits(p, "protected[util] var guarded")
    assertEmits(p, "var open")
    assertEmits(p, "private def hide()")
    assertEmits(p, "private[util] def share()")
    assertEmits(p, "protected[util] def guard()")
    assertEmits(p, "def show()")
    // the mapping IS the diff (§4.575): a faithful rendering records nothing.
    assertEquals(causes(p), Nil)
  }

  test("a top-level package-private TYPE is bare `private` — which already means its package") {
    // Scala's top-level `private` is `private[enclosingPackage]`, so no qualifier is needed and the
    // one form that IS barred from a public signature (an unqualified private NESTED type) cannot
    // arise here. This is the anim8 §7.8 gap: the level used to be erased at the class header, so
    // nothing could render it, record it or check it.
    val p = port(
      """package demo.util;
        |class Internal { int v; }
        |""".stripMargin
    )
    assertEmits(p, "private class Internal")
    assertEquals(causes(p), Nil)
  }

  test("a NESTED type keeps its level, qualified — java's own scope for it") {
    val p = port(
      """package demo.util;
        |public class Outer {
        |  private static class Secret { int v; }
        |  static class Shared { int v; }
        |  public Secret make() { return new Secret(); }
        |}
        |""".stripMargin
    )
    // JLS 6.6.1: java's `private` reaches throughout the TOP-LEVEL enclosure, which is exactly
    // `private[Outer]` — an exact rendering, not a widening, so nothing records.
    assertEmits(p, "private[Outer] class Secret")
    assertEmits(p, "private[util] class Shared")
    // …and a public member may still expose it, which is what retires the blanket erasure.
    assertEmits(p, "def make(): demo.util.Outer.Secret")
    assertEquals(causes(p), Nil)
  }

  test("a `protected static` NESTED TYPE widens with the members — same companion, same reason") {
    // The type moves to the companion `object` exactly as a static member does, so P8's argument
    // is the same one: nothing subclasses an object, and a qualified form there would DENY the
    // cross-package subclass access java grants. Its CONSTRUCTOR is not static and keeps its own
    // qualified `protected`, which still admits a subclass in any package.
    val p = port(
      """package demo.util;
        |public class Outer {
        |  protected static class Guarded { int v; }
        |}
        |""".stripMargin
    )
    assertEmits(p, "class Guarded protected[util] ()")
    assertNotEmits(p, "protected[util] class Guarded")
    assertEquals(causes(p), List("protected-static"))
  }

  test("a package-private CONSTRUCTOR renders on the promoted primary and on a secondary") {
    val p = port(
      """package demo.util;
        |public class Made {
        |  public int v;
        |  Made(int v) { this.v = v; }
        |}
        |""".stripMargin
    )
    assertEmits(p, "class Made private[util] (")
  }

  // -------------------------------------------------------------------------
  // JLS-EFFECTIVE visibility: "no modifier" is not always package-private
  // -------------------------------------------------------------------------

  test("an INTERFACE member is implicitly public — never package-private") {
    val p = port(
      """package demo.util;
        |public interface Sink {
        |  int LIMIT = 4;
        |  void accept(int v);
        |  class Helper { int v; }
        |}
        |""".stripMargin
    )
    assertNotEmits(p, "private[util] def accept")
    assertNotEmits(p, "private[util] inline val LIMIT")
    assertNotEmits(p, "private[util] class Helper")
  }

  // -------------------------------------------------------------------------
  // The residues — each recorded, none silent
  // -------------------------------------------------------------------------

  test("a `protected static` widens to public and RECORDS it") {
    // P8: the member moves to the companion `object`, and a subclass of the class is not a subclass
    // of its companion — so `protected[pkg]` there would DENY java's cross-package subclass access.
    // Public is the only side to err on, and it is a residue rather than a mapping.
    val p = port(
      """package demo.util;
        |public class Registry {
        |  protected static int seed = 1;
        |  public static int open = 2;
        |}
        |""".stripMargin
    )
    assertEmits(p, "var seed")
    assertNotEmits(p, "protected[util] var seed")
    assertEquals(causes(p), List("protected-static"))
  }

  test("a §4.55 field RENAME widens too, and the widening RECORDS — the clash pass is the decider") {
    // Both clash passes strip `private`/`protected` from every field they rename, unconditionally,
    // and they must: a renamed field has to stay reachable from wherever java read it, which
    // scala's own access rules do not grant at the new name. The RENAME was recorded and the
    // WIDENING was not — the emitted visibility is unchanged, the compile is unchanged, and
    // `NoteCoverageCheck` compares decisions to NOTES rather than to reality, so nothing could see
    // a widening with no decision.
    val p = port(
      """package demo.util;
        |public class Holder {
        |  private int align;                 // shadowed by the method below — field-vs-method
        |  public int align() { return align; }
        |}
        |""".stripMargin
    )
    assertEmits(p, "var align$field")
    assertNotEmits(p, "private var align$field")
    assertEquals(causes(p), List("member-rename"))
    val w = widenings(p).head
    assertEquals(w.detail.get("clash"), Some("field-vs-method"))
    assertEquals(w.detail.get("from"), Some("private"))
    assertEquals(w.detail.get("to"), Some("public"))
    // …and the rename beside it carries the SAME `clash`, so the two rows read as one act
    assert(p.emitter.ownDecisions.exists(d =>
      d.kind == Decision.Kind.RenamedMember && d.detail.get("clash") == Some("field-vs-method")))
  }

  test("…and a renamed field that was ALREADY public records nothing — no row for a non-change") {
    val p = port(
      """package demo.util;
        |public class Holder {
        |  public int align;
        |  public int align() { return align; }
        |}
        |""".stripMargin
    )
    assertEmits(p, "var align$field")
    assertEquals(causes(p), Nil)
  }

  test("a field SHADOWING an inherited member widens under its own `clash` value") {
    val p = port(
      """package demo.util;
        |public class Parent { public Object data; }
        |""".stripMargin,
    )
    val q = portAll(List(
      "Parent.java" -> """package demo.util;
        |public class Parent { public Object data; }
        |""".stripMargin,
      "Child.java" -> """package demo.util;
        |public class Child extends Parent { protected float[] data; }
        |""".stripMargin))
    assertEmits(q, "data$shadow")
    assertEquals(causes(q), List("member-rename"))
    assertEquals(widenings(q).head.detail.get("clash"), Some("shadows-inherited"))
    assertEquals(widenings(q).head.detail.get("from"), Some("protected"))
    assertEquals(causes(p), Nil)
  }

  test("a CROSS-PACKAGE protected override takes the nearest common ancestor, and records") {
    // P5/P14: the child can keep neither bare `protected` nor its own package's qualifier — both
    // are "has weaker access privileges" — but it CAN name any ENCLOSING package, and the nearest
    // common one covers the parent's boundary while still enclosing the child.
    val p = portAll(List(
      "Parent.java" ->
        """package demo.a.q;
          |public class Parent {
          |  protected void hook() {}
          |}
          |""".stripMargin,
      "Child.java" ->
        """package demo.a.r;
          |public class Child extends demo.a.q.Parent {
          |  protected void hook() {}
          |}
          |""".stripMargin,
    ))
    assertEmits(p, "protected[q] def hook()")
    assertEmits(p, "protected[a] override def hook()")
    assertEquals(causes(p), List("x-pkg-protected-override"))
  }

  test("…and an OVERLOADED name at that arity does not hide the member actually overridden") {
    // The override graph here is keyed on (name, TOTAL ARITY) — D1's identity — and a java class
    // overloads freely, so one key can name SEVERAL parent members. Held one-per-key, the index kept
    // whichever came last in the parent's body: `hook(Object)` is public, so it constrains nothing,
    // and the `protected` `hook(String)` the child really overrides was simply not in the list. The
    // child then shipped its OWN package's qualifier over a parent qualified with the parent's —
    // "has weaker access privileges", an ERROR, and one `RefChecks` does not report until the port
    // is already at zero (`ENGINE-LIMITS.md` K28).
    //
    // Every member at the key is held instead, which is the SAFE direction and not a compromise: the
    // fold takes the common package of all of them, and an override may be WIDER than what it
    // overrides and never narrower.
    //
    // The parent's two members are in the order the library wrote them, and the ORDER is what made
    // this silent: the index kept the LAST at each key, so the public overload won and the widening
    // simply did not happen. Swapped, the same defect answers correctly by luck — which is why the
    // fixture states java's order rather than a convenient one.
    val p = portAll(List(
      "Parent.java" ->
        """package demo.a.q;
          |public class Parent {
          |  protected void hook(String s) {}
          |  public void hook(Object o) { hook(String.valueOf(o)); }
          |}
          |""".stripMargin,
      "Child.java" ->
        """package demo.a.r;
          |public class Child extends demo.a.q.Parent {
          |  protected void hook(String s) {}
          |}
          |""".stripMargin,
    ))
    assertEmits(p, "protected[q] def hook(s: java.lang.String)")
    assertEmits(p, "protected[a] override def hook(s: java.lang.String)")
    assertEquals(causes(p), List("x-pkg-protected-override"))
  }

  test("a child NESTED under the parent's package keeps the PARENT's qualifier") {
    val p = portAll(List(
      "Parent.java" ->
        """package demo.a.q;
          |public class Parent {
          |  protected void hook() {}
          |}
          |""".stripMargin,
      "Child.java" ->
        """package demo.a.q.sub;
          |public class Child extends demo.a.q.Parent {
          |  protected void hook() {}
          |}
          |""".stripMargin,
    ))
    assertEmits(p, "protected[q] override def hook()")
    assertEquals(causes(p), List("x-pkg-protected-override"))
  }

  test("a SAME-PACKAGE override keeps the ordinary qualifier and records nothing") {
    val p = portAll(List(
      "Parent.java" ->
        """package demo.a.q;
          |public class Parent {
          |  protected void hook() {}
          |}
          |""".stripMargin,
      "Child.java" ->
        """package demo.a.q;
          |public class Child extends Parent {
          |  protected void hook() {}
          |}
          |""".stripMargin,
    ))
    assertEmits(p, "protected[q] override def hook()")
    assertEquals(causes(p), Nil)
  }

  test("the QUALIFIER-SHADOWED guard fires loudly rather than narrowing silently") {
    // P12: `private[util]` inside a type named `util` binds to the CLASS, not to the package — a
    // silent narrowing with a green compile. The guard widens and says so.
    val p = port(
      """package demo.util;
        |public class util {
        |  int shared;
        |}
        |""".stripMargin
    )
    assertNotEmits(p, "private[util] var shared")
    assertEquals(causes(p), List("qualifier-shadowed"))
  }

  test("the DEFAULT package has no name a qualifier can spell — widen and record") {
    val p = port(
      """public class Loose {
        |  int shared;
        |}
        |""".stripMargin
    )
    assertNotEmits(p, "private[] var shared")
    assertEquals(causes(p), List("unnameable-package"))
  }

  // -------------------------------------------------------------------------
  // The rule-scoping corrections the mapping forces
  // -------------------------------------------------------------------------

  test("`override` is dropped for java `private` and KEPT for package-private") {
    // A java `private` method is invisible to subclasses, so it overrides NOTHING and the pair
    // `private override` is both illegal and contradictory. A package-private one DOES override
    // within its package (P10) and needs the keyword — so the rule is scoped to the LEVEL, never
    // to the presence of a qualifier.
    val p = portAll(List(
      "Parent.java" ->
        """package demo.a;
          |public class Parent {
          |  void shared() {}
          |}
          |""".stripMargin,
      "Child.java" ->
        """package demo.a;
          |public class Child extends Parent {
          |  void shared() {}
          |}
          |""".stripMargin,
    ))
    assertEmits(p, "private[a] override def shared()")
  }

  test("a companion re-export does NOT forward a parent static that is not public") {
    // P11: `export P.*` publishes a forwarder at the EXPORTING object's visibility, so a
    // same-package companion re-exporting a `private[p]` static hands it to every package —
    // silently undoing the mapping for exactly the members java scoped most tightly.
    val p = portAll(List(
      "Base.java" ->
        """package demo.a;
          |public class Base {
          |  static final int SECRET = 1;
          |  public static final int OPEN = 2;
          |  public int f;
          |}
          |""".stripMargin,
      "Sub.java" ->
        """package demo.a;
          |public class Sub extends Base {
          |  public int g;
          |}
          |""".stripMargin,
    ))
    assertEmitsMatch(p, """export demo\.a\.Base\.\{SECRET => _, \*\}""")
  }
