package balticporter.corpus

import balticporter.core.PolicyIssue
import balticporter.testkit.{PortFixture, PortSuite}
import balticporter.tir.*
import balticporter.transform.{MutableParamsTransform, NullabilityBoundaryCheck, NullabilityTransform}
import balticporter.transform.NullabilityBoundaryCheck.Issue
import balticporter.transform.NullabilityTransform.Target

/** UNION FLOOR — the annotation moves out of a marker the Scala compiler ignores and INTO the type.
  *
  * The negatives carry as much weight as the positives here, and are written first in the file's
  * mind if not on its page: an annotated VARARG has no nullable Scala form, an annotated PRIMITIVE
  * cannot be null at all, and an annotation carrying ARGUMENTS is a different annotation. Each is
  * refused, left exactly as the upstream wrote it, and COUNTED — a refusal nobody can see is the
  * §1(b) silent no-op this phase exists to avoid.
  */
class NullabilitySpec extends PortSuite:

  private val java =
    """package demo;
      |import java.lang.annotation.*;
      |@Retention(RetentionPolicy.CLASS)
      |@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
      |@interface Null {}
      |@interface Tag { String value(); }
      |class Actor {}
      |class Group {
      |  @Null Actor parent;
      |  Actor kept;
      |  @Null Actor find(@Null String name, int depth) { return parent; }
      |  Actor plain(String name) { return new Actor(); }
      |  @Null <T extends Actor> T pick() { return null; }
      |  void spread(@Null Actor... rest) {}
      |  @Null int count() { return 0; }
      |  void tagged(@Tag("x") Actor a) {}
      |}
      |""".stripMargin

  private def phase(annotations: Set[String] = Set("demo.Null"),
                    target: Target = Target.Union,
                    scope: RuleScope = RuleScope.Everywhere()) =
    new NullabilityTransform(annotations, target, scope)

  private def run(p: NullabilityTransform): (Program, DecisionLog) =
    Pipeline.runTraced(PortFixture.parse(java), List(p))

  // -------------------------------------------------------------------------
  // positives
  // -------------------------------------------------------------------------

  test("an annotated RETURN, FIELD and PARAMETER each become `T | Null`") {
    val p = port(java, phase())
    assertEmits(p, "def find(name: java.lang.String | scala.Null, depth: scala.Int): demo.Actor | scala.Null")
    assertEmits(p, "var parent: demo.Actor | scala.Null")
  }

  test("an UNANNOTATED declaration is untouched — the annotated set is the whole of what moves") {
    val p = port(java, phase())
    assertEmits(p, "var kept: demo.Actor")
    assertEmits(p, "def plain(name: java.lang.String): demo.Actor")
  }

  test("the consumed annotation is STRIPPED — the type states the fact, and the jar is not re-imposed") {
    // Counted, because "the marker is gone" and "the marker is gone FROM THE RIGHT DECLARATIONS"
    // are different claims and only the second is worth asserting. THREE markers reach the emitted
    // baseline — `find`, `pick`, `count` — and not five: the emitter renders a class's and a
    // method's annotations and neither a field's nor a parameter's, so `parent`'s and `spread`'s
    // are invisible in the output whatever this phase does. The phase consumes `find`'s and
    // `pick`'s and refuses `count`'s, so exactly the refused one survives.
    def markers(s: String) = s.linesIterator.count(_.trim == "@demo.Null")
    assertEquals(markers(port(java).out), 3)
    assertEquals(markers(port(java, phase()).out), 1)
  }

  test("a GENERIC return retires the `null.asInstanceOf[T]` placeholder — the floor's one real win") {
    // `def pick[T <: Actor](): T = null` does not type-check (`Null <: T` does not hold at an
    // abstract `T`), which is exactly why the frontend inserts the cast. `T | Null = null` does.
    val bare = port(java)
    assertEmits(bare, "null.asInstanceOf[T]")
    val p = port(java, phase())
    assertEmits(p, "def pick[T <: demo.Actor](): T | scala.Null")
    assertNotEmits(p, "null.asInstanceOf[T]")
  }

  test("a DECISION per retyped declaration, `Reason.Configured` with the annotation FQN as the key") {
    val (_, log) = run(phase())
    val rows = log.of(Decision.Kind.RetypedSignature)
    assertEquals(rows.map(_.subjectFqn).sorted,
                 List("demo.Group#find", "demo.Group#parent", "demo.Group#pick"))
    rows.foreach { d =>
      assertEquals(d.reason, Reason.Configured("nullability", "demo.Null"))
      assertEquals(d.detail("key"), "demo.Null")
    }
    // a parameter's retype is attributed to its METHOD — one row per declaration, never one per
    // parameter (§5.1) — and the row says which positions moved.
    assertEquals(rows.find(_.subjectFqn == "demo.Group#find").get.detail("positions"), "param:name,return")
  }

  // -------------------------------------------------------------------------
  // negatives — each a counted refusal, none silent
  // -------------------------------------------------------------------------

  private def issuesOf(p: NullabilityTransform, after: Program): Map[Issue, List[String]] =
    p.boundary(after.units).groupBy(_.issue).view.mapValues(_.map(_.subject)).toMap

  test("an annotated VARARG is refused loudly — a Scala vararg has no nullable form") {
    val ph = phase()
    val (after, _) = run(ph)
    val by = issuesOf(ph, after)
    assertEquals(by.get(Issue.VarargParameter).map(_.size), Some(1))
  }

  test("an annotated PRIMITIVE is refused loudly — a primitive cannot be null at all") {
    val ph = phase()
    val (after, _) = run(ph)
    assertEquals(issuesOf(ph, after).get(Issue.PrimitiveType), Some(List("demo.Group#count")))
    // …and the emitted signature is exactly what it was.
    assertEmits(port(java, phase()), "def count(): scala.Int")
  }

  test("an annotated ABSTRACT TYPE is retyped AND counted — the one place the floor is not free") {
    // `Null` is a subtype of every CONCRETE reference type and NOT of an abstract `T`, so
    // `T | Null` does not conform to `T` and every use of `pick()`'s result in a `T` slot is a
    // compile error. Nothing at the declaration is wrong — so this is counted, not refused, and
    // the row is the only warning a port gets before it compiles.
    val ph = phase()
    val (after, log) = run(ph)
    assertEquals(issuesOf(ph, after).get(Issue.AbstractTypeParameter), Some(List("demo.Group#pick")))
    assert(log.of(Decision.Kind.RetypedSignature).exists(_.subjectFqn == "demo.Group#pick"),
           "counted is not refused — the declaration still moved")
  }

  test("an annotation carrying ARGUMENTS is refused — `@A(x)` is not `@A`") {
    val ph = phase(annotations = Set("demo.Tag"))
    val (after, _) = run(ph)
    assertEquals(issuesOf(ph, after).get(Issue.AnnotationArguments).map(_.size), Some(1))
  }

  test("a REFUSED site keeps its annotation AND its signature — nothing is half-done") {
    val p = port(java, phase())
    assertEmits(p, "@demo.Null")
    assertEmits(p, "def count(): scala.Int")
    // …and a refused PARAMETER is invisible in the output whatever happens, because the emitter
    // renders no parameter annotation at all. That asymmetry is exactly why the refusal has to be
    // a COUNTED finding and not "the annotation is still there, look": for `spread` there is
    // nothing to look at.
    assertEmits(p, "def spread(rest: scala.Array[demo.Actor]): scala.Unit")
  }

  test("an uninitialised annotated field defaults to `null`, not to a cast standing in for one") {
    // A Java field with no initialiser has no Scala default, so the declaration needs a PLACEHOLDER;
    // a union WITH `Null` STATES its own, so the cast the union was introduced to retire goes at the
    // declaration as well as at the generic return.
    //
    // The placeholder off the union path is `scala.compiletime.uninitialized` rather than the
    // `null.asInstanceOf[T]` this test was written against — A1's residue, scala's own word for the
    // JVM default. That substitution is keyed on the CAST specifically and not on "field with no
    // initialiser": applied to every one it took the union default here back off to
    // `uninitialized`, which is the same cast-shaped answer in a different spelling and defeats the
    // second assertion below. Both halves are asserted together for exactly that reason.
    assertEmits(port(java), "var parent: demo.Actor = scala.compiletime.uninitialized")
    assertEmits(port(java, phase()), "var parent: demo.Actor | scala.Null = null")
  }

  // -------------------------------------------------------------------------
  // the parameter whose SLOT an earlier phase moved
  // -------------------------------------------------------------------------

  private val reassigning =
    """package demo;
      |import java.lang.annotation.*;
      |@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
      |@interface Null {}
      |class Group {
      |  void trim(@Null String s) { s = "x"; }
      |}
      |""".stripMargin

  test("a parameter the reassigned-param transform DEMOTED still moves its method's SIGNATURE") {
    // Java lets a method reassign its parameters and Scala does not, so that transform repurposes
    // the parameter symbol as a local `var` and mints `s$arg` for the slot — WITHOUT touching the
    // method's `MethodType`, whose parameter is still called `s`. Matched by NAME, the emitted
    // parameter moved and the signature silently did not: a disagreement no count can see, and
    // the reason the two lists are joined BY POSITION. Both ends asserted, because asserting the
    // emitted text alone is exactly what missed it.
    val p = port(reassigning, new MutableParamsTransform, phase())
    assertEmits(p, "def trim(s$arg: java.lang.String | scala.Null)")
    val m = p.after.symbols.all.find(_.fullName == "demo.Group#trim").getOrElse(fail("no `trim`"))
    val firstParam = m.info match
      case TypeRepr.MethodType(ps, _, _) => ps.head._2
      case other                         => fail(s"not a method type: $other")
    assert(clue(firstParam).isInstanceOf[TypeRepr.OrType], "the SIGNATURE did not move with the slot")
  }

  // -------------------------------------------------------------------------
  // §1(b) — the policy half
  // -------------------------------------------------------------------------

  test("EMPTY annotations is a no-op, byte-for-byte — the (b) requirement, as an identity") {
    assertEquals(port(java, phase(annotations = Set.empty)).out, port(java).out)
  }

  test("an UNKNOWN annotation FQN never fires, and the BINDER says so") {
    val ph = phase(annotations = Set("demo.Null", "com.nowhere.Nullable"))
    val (after, _) = run(ph)
    val never = ph.policyReport.of(PolicyIssue.NeverMatched).map(_.key)
    assertEquals(never, List("com.nowhere.Nullable"))
    // …and the one that DID bind still did its work.
    assert(after.symbols.all.exists(s => s.fullName == "demo.Group#parent" && isUnion(s.info)))
  }

  test("`scope { only }` and `scope { except }` fence the retype, and the exclusion is RECORDED") {
    val only = phase(scope = RuleScope.Only(Set("demo.Group#parent")))
    val (_, log) = Pipeline.runTraced(PortFixture.parse(java), List(only))
    assertEquals(log.of(Decision.Kind.RetypedSignature).map(_.subjectFqn), List("demo.Group#parent"))
    // the held-back declarations get the COMPLEMENT row — the one that explains why a declaration
    // kept its upstream type while the code around it moved.
    assert(log.of(Decision.Kind.ScopedOut).map(_.subjectFqn).contains("demo.Group#find"))

    val except = phase(scope = RuleScope.Everywhere(Set("demo.Group#parent")))
    val (_, log2) = Pipeline.runTraced(PortFixture.parse(java), List(except))
    assertEquals(log2.of(Decision.Kind.RetypedSignature).map(_.subjectFqn).contains("demo.Group#parent"), false)
  }

  test("the SURFACE fingerprint carries the annotations, the target and the scope") {
    assertEquals(phase().surfaceFingerprint, "demo.Null|union|")
    assertEquals(phase(target = Target.Wrapper("lowlevel.Nullable")).surfaceFingerprint,
                 "demo.Null|wrapper:lowlevel.Nullable|")
  }

  // -------------------------------------------------------------------------
  // the two things the output must NEVER contain (ENGINE-LIMITS K2, and the lint tripwire)
  // -------------------------------------------------------------------------

  test("no `given Conversion` and no `orNull` reach the output — measured dead end, and a tripwire") {
    val p = port(java, phase())
    assertNotEmits(p, "given Conversion")
    assertNotEmits(p, "Conversion[")
    assertNotEmits(p, ".orNull")
  }

  private def isUnion(t: TypeRepr): Boolean = t match
    case TypeRepr.OrType(_, _) => true
    case _                     => false
