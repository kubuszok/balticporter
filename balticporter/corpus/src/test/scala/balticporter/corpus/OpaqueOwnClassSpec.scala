package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{ Decision, OpaqueSpec, Pipeline }
import balticporter.transform.{ OpaqueBoundaryCheck, PackageRenameTransform, PrimitiveToOpaqueTransform }

/** `OpaqueSpec.Target.OwnClass`: a java constants class becomes `opaque type N = Prim` plus `object N` at its own name — constants typed `N`, statics taking the primitive first turned into extensions
  * with their calls rewritten, and every shape it cannot take refused and counted.
  */
class OpaqueOwnClassSpec extends munit.FunSuite:

  private val align =
    """package demo;
      |public class Align {
      |  public static final int center = 1;
      |  public static final int top = 1 << 1;
      |  public static final int left = 1 << 3;
      |  public static final int topLeft = top | left;
      |  private Align() {}
      |  public static boolean isLeft(int align) { return (align & left) != 0; }
      |  public static boolean has(int align, int other) { return (align & other) != 0; }
      |  public static String toString(int align) { return isLeft(align) ? "left" : "other"; }
      |  public static int of(boolean l) { return l ? left : center; }
      |}
      |""".stripMargin

  private val label =
    """class Label {
      |  private int align = Align.center;
      |  public int getAlign() { return align; }
      |  public boolean isLeftTop() { return Align.isLeft(align) && Align.has(align, Align.top); }
      |  public String show() { return Align.toString(align); }
      |}
      |""".stripMargin

  private def spec(target: OpaqueSpec.Target = OpaqueSpec.Target.OwnClass(), hints: Set[String] = Set("demo.Label#align")) =
    OpaqueSpec(fqn = "demo.Align", hints = hints, target = target)

  private final case class Run(out: String, findings: List[OpaqueBoundaryCheck.Finding], decisions: List[Decision])

  private def run(src: String, s: OpaqueSpec = spec(), extra: List[balticporter.tir.Phase] = Nil): Run =
    val ph         = new PrimitiveToOpaqueTransform(s)
    val (p, log)   = Pipeline.runTraced(SpoonTir.fromSource(src), ph :: extra)
    val out        = new TirEmitter(p).emit
    Run(out, ph.boundary(p.units), log.all)

  private lazy val base = run(align + label)

  test("emission: the class becomes an opaque type over its primitive plus a companion object holding its statics") {
    val out = base.out
    assert(clue(out).contains("opaque type Align = scala.Int\nobject Align {"))
    assert(!out.contains("class Align"), "no class is left at the name")
    assert(!out.contains("def this("), "the constructor goes with the class")
    // the two coercions the conversion mints, inline so a coercion does not initialise the object
    assert(out.contains("inline def apply(v: scala.Int): demo.Align = v"))
    assert(out.contains("extension (v: demo.Align) inline def unwrap: scala.Int = v"))
    assert(base.decisions.exists(d => d.kind == Decision.Kind.RetypedSignature && d.subjectFqn == "demo.Align" && d.detail("to").startsWith("opaque type")))
  }

  test("constants: typed at the opaque type; a literal one is an `inline def`, reading no initialiser; a computed one stays the `final val` it was") {
    val out = base.out
    assert(clue(out).contains("inline def center: demo.Align = 1"))
    assert(out.contains("final val top: demo.Align = Align(1 << 1)"))
    assert(out.contains("final val topLeft: demo.Align = Align(Align.unwrap(Align.top) | Align.unwrap(Align.left))"))
    // a hinted field initialised from a constant keeps the constant's value, no coercion
    assert(out.contains("private val align: demo.Align = demo.Align.center"))
    // a static returning the primitive is not the type unless a seed says so
    assert(out.contains("def of(l: scala.Boolean): scala.Int"))
  }

  test("statics taking the primitive first become extensions, and their calls read `a.m(…)` outside the object and `N.m(a)(…)` inside it") {
    val out = base.out
    assert(clue(out).contains("extension (align: demo.Align) def isLeft: scala.Boolean"))
    assert(out.contains("extension (align: demo.Align) def has(other: scala.Int): scala.Boolean"))
    assert(out.contains("return this.align.isLeft && this.align.has(demo.Align.top.unwrap)"))
    // inside the object the type IS the primitive, so the prefix form keeps the extension selected
    assert(out.contains("if (Align.isLeft(align)) \"left\" else \"other\""))
    // the call-site rewrite is counted: one row per declaration whose calls moved
    val redirected = base.decisions.filter(_.kind == Decision.Kind.RedirectedCall).map(_.subjectFqn).toSet
    assert(clue(redirected) == Set("demo.Label#isLeftTop", "demo.Align#toString"))
  }

  test("a static named like a member every value has stays a plain member of the object, counted — an extension `toString` is never selected") {
    val out = base.out
    assert(clue(out).contains("def toString(align: demo.Align): java.lang.String"))
    assert(!out.contains("def toString: "))
    assert(out.contains("return demo.Align.toString(this.align)"))
    val declined = base.findings.filter(_.issue == OpaqueBoundaryCheck.Issue.ExtensionDeclined)
    assertEquals(declined.map(_.subject), List("demo.Align#toString"))
  }

  test("a static a method REFERENCE names stays a plain member, counted") {
    val r = run(align + "class Label { java.util.function.IntPredicate p = Align::isLeft; }\n", spec(hints = Set.empty))
    assert(clue(r.out).contains("def isLeft(align: demo.Align): scala.Boolean"))
    assert(r.findings.exists(f => f.issue == OpaqueBoundaryCheck.Issue.ExtensionDeclined && f.subject == "demo.Align#isLeft"))
  }

  test("a literal constant a case label names is a `final val` (a pattern needs a stable value), counted as the initialiser it now triggers") {
    val r = run(align + "class Sw { int pick(int a) { switch (a) { case Align.center: return 1; default: return 0; } } }\n", spec(hints = Set.empty))
    assert(clue(r.out).contains("final val center: demo.Align = Align(1)"))
    assert(r.out.contains("case demo.Align.center =>"))
    assert(r.findings.exists(f => f.issue == OpaqueBoundaryCheck.Issue.ConstantInitialises && f.subject == "demo.Align#center"))
  }

  private def refused(src: String, guard: String): Unit =
    val r = run(src, spec(hints = Set.empty))
    val f = r.findings.filter(_.issue == OpaqueBoundaryCheck.Issue.OwnClassRefused)
    assert(clue(f.map(_.detail)).exists(_.startsWith(s"guard=$guard ")))
    assert(!r.out.contains("opaque type"), "a refused class is emitted as java wrote it")
    assert(r.out.contains("inline val center = 1") && r.out.contains("def isLeft(align: scala.Int)"), "and nothing is retyped")

  test("refused: an instance member") {
    refused(align.replace("private Align() {}", "private int x; private Align() {}"), "instance-member")
  }

  test("refused: a construction anywhere") {
    refused(align.replace("private Align() {}", "") + "class Use { Object o = new Align(); }\n", "constructed")
  }

  test("refused: a subclass") {
    refused(align.replace("private Align() {}", "") + "class Sub extends Align {}\n", "subclassed")
  }

  test("refused: a use as a type") {
    refused(align + "class Use { Align a; }\n", "used-as-type")
  }

  test("refused: a class-literal use counts as a type use") {
    refused(align + "class Use { Class<?> c = Align.class; }\n", "used-as-type")
  }

  test("refused: a shape an opaque type cannot take") {
    refused(align.replace("public class Align {", "public class Align extends java.util.Random {"), "shape")
    refused(align.replace("public static int of(", "public static int apply("), "shape")
  }

  test("a class the program does not declare is a key that matched nothing, and nothing moves") {
    val ph  = new PrimitiveToOpaqueTransform(OpaqueSpec(fqn = "demo.Nope", hints = Set("demo.Label#align"), target = OpaqueSpec.Target.OwnClass()))
    val p   = Pipeline.run(SpoonTir.fromSource(align + label), List(ph))
    val out = new TirEmitter(p).emit
    assert(ph.policyReport.findings.exists(f => f.key == "demo.Nope" && f.issue == balticporter.core.PolicyIssue.NeverMatched))
    assert(!out.contains("opaque type"))
  }

  test("a package rename moves the opaque type and its object together") {
    val r = run(align + label, spec(), List(new PackageRenameTransform(renames = Map("demo" -> "port"))))
    assert(clue(r.out).contains("package port\n\nopaque type Align = scala.Int\nobject Align {"))
    assert(r.out.contains("private val align: port.Align = port.Align.center"))
    assert(!r.out.contains("demo.Align"))
  }

  test("surface: the target reaches the fingerprint and the subjects; the default target contributes no segment") {
    val own  = new PrimitiveToOpaqueTransform(spec())
    val mint = new PrimitiveToOpaqueTransform(spec(target = OpaqueSpec.Target.Mint))
    assert(clue(own.surfaceFingerprint).contains(";target=own-class;wrap=apply;unwrap=unwrap"))
    assert(!mint.surfaceFingerprint.contains("target="))
    assert(own.subjects.contains("demo.Align"))
    assert(own.surfaceFingerprint != new PrimitiveToOpaqueTransform(spec(target = OpaqueSpec.Target.OwnClass(unwrapName = "toInt"))).surfaceFingerprint)
  }

  test("no-op: without the own-class target the constants class is emitted exactly as before") {
    val src   = align + label
    val plain = new TirEmitter(SpoonTir.fromSource(src)).emit
    val off   = new TirEmitter(Pipeline.run(SpoonTir.fromSource(src), List(new PrimitiveToOpaqueTransform(OpaqueSpec(fqn = "demo.Align"))))).emit
    assertEquals(off, plain)
    assert(plain.contains("object Align {") && !plain.contains("opaque type"))
  }

  test("the target is refused at construction for a nested class and for colliding coercion names") {
    intercept[IllegalArgumentException](OpaqueSpec(fqn = "demo.Outer$Align", target = OpaqueSpec.Target.OwnClass()))
    intercept[IllegalArgumentException](OpaqueSpec.Target.OwnClass(wrapName = "x", unwrapName = "x"))
  }
