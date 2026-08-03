package balticporter.corpus

import balticporter.testkit.{PortFixture, PortSuite}
import balticporter.tir.*
import balticporter.transform.NullabilityBoundaryCheck.Issue
import balticporter.transform.NullabilityTransform
import balticporter.transform.NullabilityTransform.Target

/** WRAPPER MODE — the retype plus EXPLICIT coercion at every slot, and never an implicit.
  *
  * `given Conversion` is a measured dead end (`ENGINE-LIMITS.md` K2: it does not fire through an
  * overloaded call, and the annotation-heaviest upstream is also the overload-heaviest), so the
  * seam is attacked at the SLOT — before overload resolution ever runs, with the argument's type
  * already exactly the formal. The negative half of that claim is asserted here as plainly as the
  * positive: nothing in the output is a conversion, and nothing in it is the wrapper's `orNull`.
  */
class NullabilityWrapperSpec extends PortSuite:

  private val W = "lowlevel.Nullable"

  private val java =
    """package demo;
      |import java.lang.annotation.*;
      |@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
      |@interface Null {}
      |class Actor { Actor child; }
      |class Group {
      |  @Null Actor parent;
      |  void take(Actor a) {}
      |  @Null Actor give() { return null; }
      |  void use() {
      |    Actor a = parent;
      |    parent = new Actor();
      |    take(parent);
      |    Actor c = parent.child;
      |    System.out.println(parent);
      |  }
      |  void clear() { parent = null; }
      |  boolean gone() { return parent == null; }
      |  boolean here() { return parent != null; }
      |}
      |""".stripMargin

  private def phase = new NullabilityTransform(Set("demo.Null"), Target.Wrapper(W))

  private lazy val ported = port(java, phase)

  test("an annotated declaration is retyped to the configured WRAPPER, fully qualified") {
    assertEmits(ported, s"var parent: $W[demo.Actor]")
    assertEmits(ported, s"def give(): $W[demo.Actor]")
  }

  test("declaration-vs-init and argument-vs-formal unwrap with `.get` — never with a conversion") {
    assertEmits(ported, "val a: demo.Actor = this.parent.get")
    assertEmits(ported, "this.take(this.parent.get)")
  }

  test("member selection on a wrapped receiver unwraps first") {
    assertEmits(ported, "this.parent.get.child")
  }

  test("assignment-vs-RHS wraps, and a bare `null` becomes `empty` rather than `apply(null)`") {
    assertEmits(ported, s"this.parent = $W(new demo.Actor())")
    assertEmits(ported, s"this.parent = $W.empty")
  }

  test("return-vs-result wraps") {
    assertEmits(ported, s"return $W.empty")
  }

  test("`x == null` becomes `.isEmpty` — on an opaque wrapper the comparison is a COMPILE ERROR") {
    assertEmits(ported, "this.parent.isEmpty")
    assertEmits(ported, "!this.parent.isEmpty")
    assertNotEmits(ported, "this.parent == null")
    assertNotEmits(ported, "this.parent != null")
  }

  test("a slot with NO FORMAL is counted, never guessed") {
    // `java.io.PrintStream#println` is an external the frontend interned without a signature, so
    // there is nothing to coerce against. Refused and COUNTED — the same shape the collection
    // boundary's scoped-out receiver has, and for the same reason.
    val ph = phase
    val (after, _) = Pipeline.runTraced(PortFixture.parse(java), List(ph))
    val seams = ph.boundary(after.units).filter(_.issue == Issue.UncoercibleSeam)
    assertEquals(seams.size, 1)
  }

  test("nothing in the output is a CONVERSION, and nothing in it is `orNull`") {
    assertNotEmits(ported, "given Conversion")
    assertNotEmits(ported, "Conversion[")
    assertNotEmits(ported, ".orNull")
  }

  // -------------------------------------------------------------------------
  // the override graph the wrapper cannot cross yet
  // -------------------------------------------------------------------------

  private val overriding =
    """package demo;
      |import java.lang.annotation.*;
      |@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
      |@interface Null {}
      |class Actor {}
      |class Base { Actor find() { return null; } }
      |class Sub extends Base { @Null Actor find() { return null; } }
      |""".stripMargin

  test("WRAPPER mode REFUSES an override-crossing member and counts it; UNION mode moves it") {
    val w = new NullabilityTransform(Set("demo.Null"), Target.Wrapper(W))
    val (afterW, logW) = Pipeline.runTraced(PortFixture.parse(overriding), List(w))
    assertEquals(w.boundary(afterW.units).map(_.issue), List(Issue.OverrideCrossing))
    assertEquals(logW.of(Decision.Kind.RetypedSignature), Nil)

    // Union mode has no such constraint, and that is MEASURED rather than assumed: without
    // `-Yexplicit-nulls` an override may narrow a `T | Null` return or widen a `T` one, both
    // compile, so one end of the pair may move alone.
    val u = new NullabilityTransform(Set("demo.Null"), Target.Union)
    val (_, logU) = Pipeline.runTraced(PortFixture.parse(overriding), List(u))
    assertEquals(logU.of(Decision.Kind.RetypedSignature).map(_.subjectFqn), List("demo.Sub#find"))
  }
