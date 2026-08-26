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
  * positive: nothing in the output is a conversion; `.orNull` is now the SLOT spelling (see below).
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

  private def phase = new NullabilityTransform(Set("demo.Null"), Target.Named(W))

  private lazy val ported = port(java, phase)

  test("an annotated declaration is retyped to the configured WRAPPER, fully qualified") {
    assertEmits(ported, s"var parent: $W[demo.Actor]")
    assertEmits(ported, s"def give(): $W[demo.Actor]")
  }

  test("declaration-vs-init and argument-vs-formal unwrap with `.orNull` — java's slots accept null") {
    assertEmits(ported, "val a: demo.Actor = this.parent.orNull")
    assertEmits(ported, "this.take(this.parent.orNull)")
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

  test("nothing in the output is a CONVERSION — `orNull` is the SLOT spelling, not a conversion") {
    assertNotEmits(ported, "given Conversion")
    assertNotEmits(ported, "Conversion[")
    // `.orNull` IS emitted at slot coercions — that is the faithful spelling for java slots that
    // accept null. What is NOT emitted is `given Conversion`, which is the measured dead end (K2).
    assertEmits(ported, ".orNull")
  }

  // -------------------------------------------------------------------------
  // the SLOT-NULLABILITY RULE — `.get` at a dereference, `.orNull` at a slot
  // -------------------------------------------------------------------------

  test("member selection on a wrapped receiver uses `.get` — java NPEs on null dereference") {
    assertEmits(ported, "this.parent.get.child")
    assertNotEmits(ported, "this.parent.orNull.child")
  }

  test("slot coercion uses `.orNull` — java's unannotated slots accept null") {
    // declaration-vs-init: the val is an unannotated reference type, java accepts null
    assertEmits(ported, "this.parent.orNull")
    assertNotEmits(ported, "val a: demo.Actor = this.parent.get")
    // argument-vs-formal: `take(Actor a)` is unannotated, java accepts null
    assertEmits(ported, "this.take(this.parent.orNull)")
    assertNotEmits(ported, "this.take(this.parent.get)")
  }

  private val primitiveSlot =
    """package demo;
      |import java.lang.annotation.*;
      |@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
      |@interface Null {}
      |class Num {
      |  @Null Integer wrapped() { return null; }
      |  void primSlot() { int x = wrapped(); }
      |  void refSlot() { Object o = wrapped(); }
      |}
      |""".stripMargin

  test("a PRIMITIVE slot gets `.get` — unboxing null NPEs in java, and `.orNull` would widen") {
    val p = port(primitiveSlot, phase)
    assertEmits(p, "this.wrapped().get")
    // the reference slot gets `.orNull` — java's `Object` accepts null
    assertEmits(p, "val o: java.lang.Object = this.wrapped().orNull")
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
    val w = new NullabilityTransform(Set("demo.Null"), Target.Named(W))
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

  // -------------------------------------------------------------------------
  // a LAMBDA BODY is a slot — the function's result (screens' `pushScreen` seam)
  // -------------------------------------------------------------------------

  private val lambdas =
    """package demo;
      |import java.lang.annotation.*;
      |import java.util.function.Supplier;
      |@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
      |@interface Null {}
      |class Transition {}
      |interface Own<T> { @Null T give(); }
      |class Manager<T extends Transition> {
      |  void push(Supplier<T> s) {}
      |  void hold(Own<T> o) {}
      |  void go(@Null T transition) {
      |    push(() -> transition);
      |    hold(() -> transition);
      |  }
      |}
      |""".stripMargin

  test("a wrapped value captured as a CLASS-FILE SAM's result is unwrapped at the body and COUNTED") {
    val ph = phase
    val (after, _) = Pipeline.runTraced(PortFixture.parse(lambdas), List(ph))
    val p = port(lambdas, phase)
    assertEmits(p, "this.push(() => transition.orNull)")
    val seams = ph.boundary(after.units).filter(_.issue == Issue.UncoercibleSeam)
    assertEquals(seams.map(_.subject), List("java.util.function.Supplier"))
  }

  test("…and one captured as an OWNED, annotated SAM's result stays wrapped — that slot is ours") {
    val p = port(lambdas, phase)
    // WRAPPED, and with NO ascription: the SAM's retyped result is `W[T]` in `Own`'s own `T`, which
    // is not writable inside `Manager` — the ascription this used to emit read
    // `asInstanceOf[lowlevel.Nullable[T]]` and was correct only because `Manager`'s parameter
    // happens to be spelled `T` too (`CLAUDE.md` §4.56's name hazard, at a type variable). The
    // types agree once `Own[T]` is read through the slot, so the honest emission is the value.
    assertEmits(p, "this.hold(() => transition)")
    assertNotEmits(p, "hold(() => transition.get")
  }

  // -------------------------------------------------------------------------
  // a CAST over a wrapped value — the node keeps the type the EMITTER renders
  // -------------------------------------------------------------------------

  /** `(int) poll()` at junit's `assertEquals(long, long)`: java unboxed at `int` and WIDENED to the
    * slot, and the port has to unwrap the `Nullable` under that cast. The unwrap is the OPERAND's
    * business and the cast is untouched — so the node still emits `.asInstanceOf[scala.Int]`, and
    * recording the slot's `long` on it is a type the emitted Scala does not have
    * (`ENGINE-LIMITS.md` §0). `TestFrameworkTransform.promote` is the reader that pays for it.
    *
    * Junit is DECLARED here rather than resolved: `SpoonTir.fromSources` runs `noClasspath` with no
    * source classpath, so an unresolved `org.junit.Assert` interns with no `MethodType` at all —
    * and this defect is entirely about the FORMAL the coercion reads. A fixture that could not see
    * `long` would exercise the no-formal refusal instead and pass whatever the arm does. */
  private val junitStub =
    """package org.junit;
      |public @interface Test {}
      |""".stripMargin

  private val junitAssert =
    """package org.junit;
      |public class Assert {
      |  public static void assertEquals(long expected, long actual) {}
      |}
      |""".stripMargin

  private val castAtWideSlot =
    """package demo;
      |import java.lang.annotation.*;
      |import org.junit.Assert;
      |import org.junit.Test;
      |@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
      |@interface Null {}
      |public class QueueTest {
      |  @Null Integer poll() { return null; }
      |  @Test public void pull() { Assert.assertEquals(1, (int) poll()); }
      |}
      |""".stripMargin

  private def castSources =
    List("Test.java" -> junitStub, "Assert.java" -> junitAssert, "QueueTest.java" -> castAtWideSlot)

  test("a cast over a wrapped value keeps ITS OWN type — the slot's `long` is not recorded on it") {
    val p = portAll(castSources, phase)
    assertEmits(p, "this.poll().get.asInstanceOf[scala.Int]")
    // …and the assertion the emitted text cannot make: every `Tree.Typed` in the output records the
    // type the emitter renders it at. `TirEmitter` reads `tpt` (`castTarget`) and every later rule
    // reads `tpe`, so a node where the two disagree lies to every reader but the emitter — and
    // nothing else in a run can see it.
    given Program = p.after
    val mismatched = p.after.units.flatMap { u =>
      StandardTraversal.scanClassDef(u, List.empty[Tree.Typed]) {
        case (acc, x: Tree.Typed) if x.tpt.tpe != x.tpe => x :: acc
        case (acc, _)                                   => acc
      }
    }
    assertEquals(clue(mismatched).map(x => (x.tpt.tpe, x.tpe)), Nil)
  }

  test("…so `TestFrameworkTransform` does not widen the literal against it — 4 E172 on libGDX's suite") {
    val p = portAll(castSources, phase, new balticporter.transform.TestFrameworkTransform)
    assertEmits(p, "munit.Assertions.assertEquals(this.poll().get.asInstanceOf[scala.Int], 1)")
    // java's binary numeric promotion (JS-E07) has nothing to do here: both operands are `Int` once
    // the cast is read honestly. Widened, the pair is `Int` against `Long` and MUnit's
    // `Compare[A, B]` rejects it — `E172 Can't compare Int and Long`.
    assertNotEmits(p, "1.toLong")
  }

  // -------------------------------------------------------------------------
  // a call from ONE unit into a RETYPED member of another — the dependent's shape
  // -------------------------------------------------------------------------

  /** A dependent port's `Program` CONTAINS its base's units (`ENGINE-LIMITS.md` D2), and the
    * inherited phase runs over both — so a base member the annotations retype has to be seen as
    * retyped at a call site in the other unit, or the dependent emits a call to a signature that no
    * longer exists.
    *
    * `get(K)` beside `get(K, V)` is the shape that makes the failure LOUD rather than silent, and it
    * is libGDX's own `ObjectMap`: with the one-argument result wrapped, an un-unwrapped call no
    * longer conforms to the assignment's type, scalac falls through to the TWO-argument overload,
    * and the message is `E171 missing argument for parameter defaultValue` — which names neither
    * null nor the wrapper and reads as an overload bug. Measured once, on gdx-ai's own
    * `BehaviorTreeParser` (1 error), where the site was a `MethodBodyTransform` body a human wrote:
    * the mechanical sites in the same port were already correct, which is what this asserts. */
  private val baseUnit =
    """package base;
      |import java.lang.annotation.*;
      |@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
      |@interface Null {}
      |public class Cache<K, V> {
      |  @Null public V get(K key) { return null; }
      |  public V get(K key, V defaultValue) { return defaultValue; }
      |}
      |""".stripMargin

  private val dependentUnit =
    """package dep;
      |import base.Cache;
      |public class Reader {
      |  private Cache<String, Reader> cache = new Cache<String, Reader>();
      |  Reader find(String name) { Reader r = cache.get(name); return r; }
      |}
      |""".stripMargin

  test("a call into a BASE member the inherited phase retyped unwraps in the DEPENDENT's unit") {
    val p = portAll(List("Cache.java" -> baseUnit, "Reader.java" -> dependentUnit),
                    new NullabilityTransform(Set("base.Null"), Target.Named(W)))
    assertEmits(p, s"def get(key: K): $W[V]")
    assertEmits(p, "val r: Reader = this.cache.get(name).orNull")
    // and NOT the bare call, which resolves against `get(K, V)` and reports `E171 missing argument
    // for parameter defaultValue` — an overload message for a nullability fact.
    assertNotEmits(p, "val r: Reader = this.cache.get(name)\n")
  }

  // -------------------------------------------------------------------------
  // an OVERRIDE inherits the contract — the edge the annotation travels down
  // -------------------------------------------------------------------------

  /** Java's marker is a fact about the MEMBER and javac ignores it, so an upstream has no reason to
    * repeat it on an override and routinely does not. Scala has no such freedom: a wrapper retype
    * moves the SIGNATURE, so an override that keeps the upstream spelling is `E038 … a different
    * signature than the overridden declaration` — and at a GENERIC result it is `E007 Found: W[T] /
    * Required: T` in a body that returns exactly what the parent handed it.
    *
    * Both were measured on DEPENDENTS and on nothing else, which is what the shape predicts: a base
    * carrying such a pair would not compile, so the corpus's bases have none. TextraTypist's
    * `setParent` ×2 (`E038`, invisible until the port reached 0 typer errors — `CLAUDE.md` §3) and
    * VisUI's `DragPane#findActor` (`E007`, 8 -> 7). */
  private val overrideChain =
    """package demo;
      |import java.lang.annotation.*;
      |@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
      |@interface Null {}
      |class Actor {}
      |class Group {
      |  @Null Actor found;
      |  @Null Actor find(String name) { return null; }
      |  void adopt(@Null Group parent) {}
      |}
      |class Pane extends Group {
      |  @Override Actor find(String name) { return null; }
      |  @Override void adopt(Group parent) {}
      |}
      |""".stripMargin

  test("an UNANNOTATED override of an annotated member moves with it — result and parameter alike") {
    val p = port(overrideChain, phase)
    assertEmits(p, s"def find(name: java.lang.String): $W[demo.Actor]")
    assertEmits(p, s"def adopt(parent: $W[demo.Group]): scala.Unit")
    // both ends, and BOTH SHAPES: `Pane` declares neither annotation and gets both types.
    assertEmits(p, s"override def find(name: java.lang.String): $W[demo.Actor]")
    assertEmits(p, s"override def adopt(parent: $W[demo.Group]): scala.Unit")
  }

  test("…and a CONSTRUCTOR is not an override edge, however the graph matches names") {
    // every `<init>` is named `<init>`, so a name-and-signature graph reads a subclass's
    // same-shaped constructor as an "override" of its superclass's. Java has no such edge, and
    // travelling it retyped 17 members on libGDX core and minted 4 spurious erasure-clash rows, at
    // 0 errors either way — the shape only `members.tsv` could see.
    val java =
      """package demo;
        |import java.lang.annotation.*;
        |@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
        |@interface Null {}
        |class Drawable {}
        |class Skin {}
        |class Button { Button(@Null Drawable up) {} Button(Skin skin) {} }
        |class ImageButton extends Button { ImageButton(Drawable up) { super(up); } ImageButton(Skin s) { super(s); } }
        |""".stripMargin
    val p = port(java, phase)
    assertEmits(p, s"up: $W[demo.Drawable]")          // the ANNOTATED constructor moved
    assertEmits(p, "def this(up: demo.Drawable)")     // …and the subclass's own did NOT
    assertNotEmits(p, s"$W[demo.Skin]")
  }

  // -------------------------------------------------------------------------
  // the OVERLOAD SET a wrapper would erase flat
  // -------------------------------------------------------------------------

  test("two overloads java kept apart BY ERASURE keep their upstream types, and are COUNTED") {
    val java =
      """package demo;
        |import java.lang.annotation.*;
        |@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
        |@interface Null {}
        |class Font {}
        |class BitmapFont {}
        |class Style {
        |  Style(@Null Font font) {}
        |  Style(@Null BitmapFont font) {}
        |}
        |""".stripMargin
    val ph = phase
    val (after, _) = Pipeline.runTraced(PortFixture.parse(java), List(ph))
    val clashes = ph.boundary(after.units).filter(_.issue == Issue.OverloadErasureClash)
    // BOTH sides, because the distinction is carried by the pair and refusing one end is an
    // arbitrary choice between two declarations.
    assertEquals(clue(clashes).size, 2)
    val p = port(java, phase)
    assertNotEmits(p, s"font: $W[demo.Font]")
    assertNotEmits(p, s"font: $W[demo.BitmapFont]")
  }

  test("`W.empty` is never ASCRIBED — it conforms at every element type by the wrapper contract") {
    // the one operand that reaches a slot whose element is written in a scope the site does not
    // have: a companion or `static` member (`ENGINE-LIMITS.md` G20) and a super-constructor
    // argument list. An ascription there is `E006 Not found: type T`; the bare `W.empty` is right
    // wherever the slot is, so the arm declines structurally rather than asking about scope.
    List(ported, port(overrideChain, phase), portAll(castSources, phase))
      .foreach(p => assertNotEmits(p, "empty.asInstanceOf"))
  }
