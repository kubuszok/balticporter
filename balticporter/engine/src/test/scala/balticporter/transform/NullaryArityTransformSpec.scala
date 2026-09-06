package balticporter.transform

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.*

/** [[NullaryArityTransform]] — dropping `()` from a getter-like nullary method, and the four
  * guards that decline. */
class NullaryArityTransformSpec extends munit.FunSuite:

  // ---- harness ------------------------------------------------------------------------------

  private case class Ran(before: Program, after: Program, phase: NullaryArityTransform,
                         log: DecisionLog, idioms: IdiomLog):
    def out: String = new TirEmitter(after).emit

  private def ran(java: String, phase: NullaryArityTransform): Ran =
    val before   = SpoonTir.fromSource(java)
    val idioms   = new IdiomLog
    val rewrites = RewriteLog()
    val (after, log) = Pipeline.runTraced(before, List(phase),
      new PolicyBinder(before, before.members), balticporter.catalog.CatalogLog.discarding,
      rewrites, idioms)
    Ran(before, after, phase, log, idioms)

  private def converted(r: Ran): List[IdiomCandidate] =
    r.idioms.all.filter(c => c.kind == IdiomKind.NullaryArity && c.verdict == IdiomVerdict.Converted)

  private def refused(r: Ran): List[IdiomCandidate] =
    r.idioms.all.filter(c => c.kind == IdiomKind.NullaryArity && c.verdict.lane == "refused")

  private def guards(r: Ran): Set[String] =
    refused(r).map(_.verdict).collect { case IdiomVerdict.Refused(g, _) => g }.toSet

  private def refusedFor(r: Ran, member: String): List[IdiomCandidate] =
    refused(r).filter(_.subject.endsWith(member))

  /** the member's name in the OUTPUT program, read through its pre-run identity. */
  private def nameOf(r: Ran, fqn: String): String =
    r.before.symbols.all.find(_.fullName == fqn).map(_.id)
      .flatMap(r.after.symbolOf).map(_.name).getOrElse(s"<no $fqn>")

  private val everywhere = RuleScope.Everywhere()

  // -------------------------------------------------------------------------------------------
  // §1(b): the no-op default, and the fingerprint segment that is OMITTED at it
  // -------------------------------------------------------------------------------------------

  test("scope = Only(Set.empty) is the no-op — the same program, identically") {
    val src = """
      class Thing {
        private int w;
        public int w() { return w; }
      }
    """
    val phase  = new NullaryArityTransform()
    val before = SpoonTir.fromSource(src)
    assert(phase.run(before) eq before, "Only(Set.empty) must return the SAME program")
  }

  test("the no-op files no candidate at all — no converted row, no refused row") {
    val r = ran(
      """
      class Thing { private int w; public int w() { return w; } }
      """,
      new NullaryArityTransform())
    assertEquals(clue(converted(r)).size, 0)
    assertEquals(clue(refused(r)).size, 0)
    assertEquals(nameOf(r, "Thing#w"), "w")
    assert(clue(r.out).contains("def w()"), "the arity must survive the no-op")
  }

  test("surfaceFingerprint OMITS the segment at the default — §1(b) at the fingerprint") {
    assertEquals(new NullaryArityTransform().surfaceFingerprint, "")
    assertEquals(new NullaryArityTransform(RuleScope.Only(Set.empty)).surfaceFingerprint, "")
  }

  test("surfaceFingerprint carries the scope once it is non-empty") {
    val a = new NullaryArityTransform(RuleScope.Only(Set("com.foo")))
    assert(clue(a.surfaceFingerprint).contains("scope="))
    assert(a.surfaceFingerprint.contains("only:com.foo"))
    val b = new NullaryArityTransform(RuleScope.Everywhere(Set("com.bar")))
    assert(clue(b.surfaceFingerprint).contains("except:com.bar"))
  }

  /** `Everywhere(Set.empty)` is the WHOLE-PROGRAM derivation, not the no-op — this phase ADDS a
    * declaration shape, so §1(b)'s adds-vs-retypes rule puts its no-op at `Only(Set.empty)`. The
    * two must therefore FINGERPRINT DIFFERENTLY: rendered equal, `SurfaceMissing` could not tell a
    * port that runs the phase over everything from one that does not run it at all
    * (`ENGINE-LIMITS.md` CT9). */
  test("Everywhere() is NOT the no-op, and does NOT fingerprint equal to it") {
    val on  = new NullaryArityTransform(everywhere)
    val off = new NullaryArityTransform()
    assert(clue(on.surfaceFingerprint).nonEmpty)
    assertNotEquals(on.surfaceFingerprint, off.surfaceFingerprint)
    assertEquals(on.idiomKinds, Set(IdiomKind.NullaryArity))
    assertEquals(off.idiomKinds, Set.empty)
  }

  test("idiomKinds is empty at the no-op and NullaryArity once scoped") {
    assertEquals(new NullaryArityTransform().idiomKinds, Set.empty)
    assertEquals(new NullaryArityTransform(RuleScope.Only(Set("com.foo"))).idiomKinds,
      Set(IdiomKind.NullaryArity))
  }

  test("subjects is the declared entries — what SurfaceIntrusion screens") {
    assertEquals(new NullaryArityTransform(RuleScope.Only(Set("com.foo", "com.bar"))).subjects,
      Set("com.foo", "com.bar"))
    assertEquals(new NullaryArityTransform().subjects, Set.empty)
  }

  // -------------------------------------------------------------------------------------------
  // the positive: the declaration loses `()`, every call site loses it too, and a decision says so
  // -------------------------------------------------------------------------------------------

  test("a getter-like nullary method drops `()`, and every call site is rewritten") {
    val r = ran(
      """
      class Layer {
        private float opacity;
        public float opacity() { return opacity; }
      }
      class Use {
        float go(Layer l) { return l.opacity(); }
      }
      """,
      new NullaryArityTransform(RuleScope.Only(Set("Layer"))))
    assert(clue(r.out).contains("def opacity:"), "the declaration must lose its clause")
    assert(!r.out.contains("def opacity()"))
    assert(clue(r.out).contains("l.opacity"), "the call site must lose its clause")
    assert(!r.out.contains("l.opacity()"))
    assertEquals(clue(converted(r)).size, 1)
    assertEquals(converted(r).head.subject, "Layer#opacity")
  }

  test("the conversion records a ParenlessConversion decision with from/to and a Universal reason") {
    val r = ran(
      """
      class Layer { private float opacity; public float opacity() { return opacity; } }
      """,
      new NullaryArityTransform(RuleScope.Only(Set("Layer"))))
    val ds = r.log.of(Decision.Kind.ParenlessConversion)
    assertEquals(clue(ds).size, 1)
    assertEquals(ds.head.subjectFqn, "Layer#opacity")
    assertEquals(ds.head.detail.get("from"), Some("opacity()"))
    assertEquals(ds.head.detail.get("to"), Some("opacity"))
    assertEquals(ds.head.reason, Reason.Universal("nullary-arity"))
  }

  /** The scope is a REFUSAL like any other: a member the scope declines is one whose `()` the run
    * kept, and §3 wants the seam the scope created counted rather than dropped. */
  test("a type OUTSIDE the scope keeps `()` and files an OutOfScope row") {
    val r = ran(
      """
      class Inside  { private int w; public int w() { return w; } }
      class Outside { private int h; public int h() { return h; } }
      """,
      new NullaryArityTransform(RuleScope.Only(Set("Inside"))))
    assert(clue(r.out).contains("def w:"))
    assert(clue(r.out).contains("def h()"), "the out-of-scope declaration keeps its clause")
    assertEquals(converted(r).map(_.subject), List("Inside#w"))
    val rows = refusedFor(r, "Outside#h")
    assertEquals(clue(rows).size, 1)
    assert(clue(rows.head.verdict.render).contains("OutOfScope"))
  }

  // -------------------------------------------------------------------------------------------
  // the population: EVERY owned nilary value-returning declaration takes exactly one row (§3)
  // -------------------------------------------------------------------------------------------

  /** A `static` is emitted onto the companion. The skip predates the lane; it is the same claim
    * about the emitted surface as every other guard, so it is COUNTED rather than silent. */
  test("StaticMember: a static nilary getter keeps `()` and files a row") {
    val r = ran(
      """
      class Env { public static int mode() { return 1; } }
      """,
      new NullaryArityTransform(everywhere))
    assert(clue(r.out).contains("def mode()"))
    assertEquals(clue(converted(r)), Nil)
    assertEquals(clue(guards(r)), Set("StaticMember"))
  }

  /** The denominator claim: nothing owned, nilary and value-returning leaves the scan without a
    * verdict — converted or refused, one row each, no third outcome. */
  test("every owned nilary value-returning declaration takes exactly one lane row") {
    val r = ran(
      """
      class Mix {
        private int a;
        private int[] xs;
        public int a() { return a; }
        public int bump() { a = a + 1; return a; }
        public String toString() { return "m"; }
        public static int mode() { return 1; }
        public void run() {}
        public int at(int i) { return xs[i]; }
      }
      """,
      new NullaryArityTransform(everywhere))
    val rows = r.idioms.all.filter(_.kind == IdiomKind.NullaryArity)
    // `run()` is void and `at(int)` takes a parameter: neither is in the population.
    assertEquals(clue(rows.map(_.subject).sorted),
      List("Mix#a", "Mix#bump", "Mix#mode", "Mix#toString"))
    assertEquals(clue(rows.size), clue(rows.map(_.subject).distinct.size))
  }

  // -------------------------------------------------------------------------------------------
  // guard 1 — SideEffectingBody: an ASSIGNMENT in the body
  // -------------------------------------------------------------------------------------------

  /** `next()` mutates and returns. Parenless, `c.next` reads as a value access and every reader
    * of the emitted surface would be wrong about what the call DOES — java's own `()` is the only
    * thing distinguishing "read a value" from "do something and answer". */
  test("SideEffectingBody: an assignment in the body keeps `()`") {
    val r = ran(
      """
      class Counter {
        private int n;
        public int next() { n = n + 1; return n; }
      }
      """,
      new NullaryArityTransform(everywhere))
    assertEquals(nameOf(r, "Counter#next"), "next")
    assert(clue(r.out).contains("def next()"))
    assertEquals(clue(converted(r)), Nil)
    assertEquals(clue(guards(r)), Set("SideEffectingBody"))
  }

  test("SideEffectingBody: a call to a NON-NULLARY member keeps `()`") {
    val r = ran(
      """
      class Calc {
        private int a;
        int plus(int x) { return a + x; }
        public int total() { return plus(1); }
      }
      """,
      new NullaryArityTransform(everywhere))
    assert(clue(r.out).contains("def total()"))
    val rows = refusedFor(r, "Calc#total")
    assertEquals(clue(rows).size, 1)
    assert(clue(rows.head.verdict.render).contains("SideEffectingBody"))
    assertEquals(converted(r).map(_.subject), Nil)
  }

  test("the refusal names the guard AND says why — §4.45's classification obligation") {
    val r = ran(
      """
      class Counter { private int n; public int next() { n = n + 1; return n; } }
      """,
      new NullaryArityTransform(everywhere))
    val why = refused(r).map(_.verdict).collect { case IdiomVerdict.Refused(_, w) => w }
    assert(clue(why).exists(_.contains("assignments")),
      "the sentence must say what the guard saw, not merely that it declined")
  }

  // -------------------------------------------------------------------------------------------
  // guard 2 — AnchoredClosure: the component reaches a declaration this program cannot move
  // -------------------------------------------------------------------------------------------

  /** `java.lang.Object` is above every type whether or not the parse lists it, so a `toString()`
    * override is anchored by a declaration no port may re-arity. Dropped, `o.toString` would still
    * compile — scala's own `toString` is parenless — while the emitted OVERRIDE would no longer
    * match what it overrides. */
  test("AnchoredClosure: an override of `java.lang.Object#toString` keeps `()`") {
    val r = ran(
      """
      class Named {
        private String n;
        public String toString() { return n; }
      }
      """,
      new NullaryArityTransform(everywhere))
    assertEquals(nameOf(r, "Named#toString"), "toString")
    assertEquals(clue(converted(r)), Nil)
    assertEquals(clue(guards(r)), Set("AnchoredClosure"))
    val why = refused(r).map(_.verdict).collect { case IdiomVerdict.Refused(_, w) => w }
    assert(clue(why).exists(_.contains("java.lang.Object")),
      "the anchor's own FQN is what an agent classifies the row by")
  }

  /** The SHIM FAMILY (CLAUDE.md §4.5): a library's own class implementing a java collection
    * interface keeps JAVA's arity, because the interface is a class file no phase can move.
    * `hasNext()` is the shape every collection library is made of. */
  test("the shim family: `hasNext()` under `java.util.Iterator` stays `hasNext()`") {
    val r = ran(
      """
      import java.util.Iterator;
      class Cursor implements Iterator<String> {
        private int i;
        private String[] items;
        public boolean hasNext() { return i < items.length; }
        public String next() { return items[i]; }
        public void remove() {}
      }
      """,
      new NullaryArityTransform(everywhere))
    assertEquals(nameOf(r, "Cursor#hasNext"), "hasNext")
    assert(clue(r.out).contains("def hasNext()"))
    assert(clue(guards(r)).contains("AnchoredClosure"))
    assert(clue(refusedFor(r, "Cursor#hasNext")).nonEmpty)
    assertEquals(converted(r).map(_.subject), Nil)
  }

  // -------------------------------------------------------------------------------------------
  // guard 3 — ComponentPartial: an override component that cannot move WHOLE
  // -------------------------------------------------------------------------------------------

  /** An ABSTRACT member has no body to inspect, so it is never getter-like — and its arity is
    * part of its contract (a SAM ascription reads it). The concrete implementor is therefore in a
    * component that cannot move whole, and moving HALF a component breaks the override edge:
    * `def size` does not implement `def size()`. */
  test("ComponentPartial: a concrete getter under an ABSTRACT declaration keeps `()`") {
    val r = ran(
      """
      interface Shape { int size(); }
      class Box implements Shape {
        private int n;
        public int size() { return n; }
      }
      """,
      new NullaryArityTransform(everywhere))
    assertEquals(nameOf(r, "Box#size"), "size")
    assertEquals(nameOf(r, "Shape#size"), "size")
    assert(clue(r.out).contains("def size()"))
    assert(!r.out.contains("def size:"))
    assertEquals(clue(converted(r)), Nil)
    assertEquals(clue(refusedFor(r, "Box#size")).size, 1)
    assert(clue(refusedFor(r, "Box#size").head.verdict.render).contains("ComponentPartial"))
  }

  test("the abstract member itself is refused too, so the component's every row is counted") {
    val r = ran(
      """
      interface Shape { int size(); }
      class Box implements Shape { private int n; public int size() { return n; } }
      """,
      new NullaryArityTransform(everywhere))
    assertEquals(clue(refusedFor(r, "Shape#size")).size, 1)
    assertEquals(clue(guards(r)), Set("SideEffectingBody", "ComponentPartial"))
  }

  /** …and the same component with BOTH halves concrete and getter-like moves WHOLE. That is what
    * makes the guard above a component test rather than an abstractness test. */
  test("a component whose every member qualifies is converted WHOLE") {
    val r = ran(
      """
      class Base { protected int n; public int size() { return n; } }
      class Sub extends Base { public int size() { return n; } }
      class Use { int go(Base b) { return b.size(); } }
      """,
      new NullaryArityTransform(everywhere))
    assertEquals(clue(converted(r)).map(_.subject).sorted, List("Base#size", "Sub#size"))
    assert(clue(r.out).contains("b.size"))
    assert(!r.out.contains("def size()"))
    assertEquals(clue(refused(r)), Nil)
  }

  // -------------------------------------------------------------------------------------------
  // a VOID nullary method is not a getter at all, and files nothing
  // -------------------------------------------------------------------------------------------

  test("a void nullary method is not a candidate — no row in either lane") {
    val r = ran(
      """
      class Res { public void close() {} }
      """,
      new NullaryArityTransform(everywhere))
    assert(clue(r.out).contains("def close()"))
    assertEquals(clue(converted(r)), Nil)
    assertEquals(clue(refused(r)), Nil)
  }

  // -------------------------------------------------------------------------------------------
  // guard 4 — Overloaded: the owner declares a parameterful method of the same name
  // -------------------------------------------------------------------------------------------

  /** `toArray()` beside `toArray(Class)`: dropping `()` from the nilary one makes
    * `a.toArray(classOf[X])` resolve to the parenless `toArray` APPLIED to the argument, rather
    * than calling the 1-arg overload. The guard fires at the DECLARATION. */
  test("Overloaded: a nilary method whose owner also declares a parameterful overload keeps `()`") {
    val r = ran(
      """
      class Arr {
        private int[] data;
        public int[] toArray() { return data; }
        public int[] toArray(int[] target) { System.arraycopy(data, 0, target, 0, data.length); return target; }
      }
      """,
      new NullaryArityTransform(everywhere))
    assertEquals(nameOf(r, "Arr#toArray"), "toArray")
    assert(clue(r.out).contains("def toArray()"), "the nilary overload must keep its clause")
    assertEquals(clue(converted(r)), Nil)
    val rows = refusedFor(r, "Arr#toArray")
    assertEquals(clue(rows).size, 1)
    assert(clue(rows.head.verdict.render).contains("Overloaded"))
  }

  /** The ABSENCE of a parameterful sibling means no ambiguity: a nilary method on its own is safe
    * to convert. This test verifies the guard does NOT fire when no overload exists. */
  test("a nilary method with NO parameterful sibling is NOT refused by Overloaded") {
    val r = ran(
      """
      class Layer {
        private float opacity;
        public float opacity() { return opacity; }
      }
      """,
      new NullaryArityTransform(everywhere))
    assert(clue(r.out).contains("def opacity:"), "should be converted")
    assertEquals(clue(converted(r)).size, 1)
    assert(!clue(guards(r)).contains("Overloaded"))
  }

  // -------------------------------------------------------------------------------------------
  // MergeablePolicy — §1.5: two modules scoping this differently emit signatures that cannot
  // compile together, so the composition is the PHASE's answer and a disagreement is a finding
  // -------------------------------------------------------------------------------------------

  test("mergedWith unions two Only scopes") {
    val a = new NullaryArityTransform(RuleScope.Only(Set("com.a")))
    val b = new NullaryArityTransform(RuleScope.Only(Set("com.b")))
    val merged = a.mergedWith(b)
    assert(clue(merged).isRight)
    val mp = merged.toOption.get.phase.asInstanceOf[NullaryArityTransform]
    assertEquals(mp.arityScope, RuleScope.Only(Set("com.a", "com.b")))
    assertEquals(merged.toOption.get.added, Set("com.b"))
  }

  test("mergedWith unions two Everywhere opt-out lists") {
    val a = new NullaryArityTransform(RuleScope.Everywhere(Set("com.a")))
    val b = new NullaryArityTransform(RuleScope.Everywhere(Set("com.b")))
    val mp = a.mergedWith(b).toOption.get.phase.asInstanceOf[NullaryArityTransform]
    assertEquals(mp.arityScope, RuleScope.Everywhere(Set("com.a", "com.b")))
  }

  test("mergedWith lets the no-op side defer — an empty dependent inherits the base's scope") {
    val base = new NullaryArityTransform(everywhere)
    val dep  = new NullaryArityTransform()
    assertEquals(base.mergedWith(dep).toOption.get.phase
      .asInstanceOf[NullaryArityTransform].arityScope, everywhere)
    assertEquals(dep.mergedWith(base).toOption.get.phase
      .asInstanceOf[NullaryArityTransform].arityScope, everywhere)
  }

  test("mergedWith REFUSES a mixed Only/Everywhere pair, naming the disagreement") {
    val a = new NullaryArityTransform(RuleScope.Only(Set("com.a")))
    val b = new NullaryArityTransform(RuleScope.Everywhere(Set("com.b")))
    val merged = a.mergedWith(b)
    assert(clue(merged).isLeft)
    assert(clue(merged.swap.toOption.get).contains("disagrees"))
  }

  test("mergedWith refuses a phase that is not a NullaryArityTransform") {
    val a = new NullaryArityTransform(everywhere)
    assert(a.mergedWith(new BeanPropertyTransform()).isLeft)
  }

  // -------------------------------------------------------------------------------------------
  // ordering and the retyping contract
  // -------------------------------------------------------------------------------------------

  test("the phase runs AFTER bean-properties and BEFORE package-rename") {
    val p = new NullaryArityTransform(everywhere)
    assert(clue(p.runsAfter).contains("bean-properties"))
    assert(clue(p.runsBefore).contains("package-rename"))
    assertEquals(p.name, NullaryArityTransform.PhaseName)
  }

  test("accountedBy names the lane that counts this phase's residue") {
    assertEquals(new NullaryArityTransform(everywhere).accountedBy, Set(IdiomCheck.Residue))
  }

  // -------------------------------------------------------------------------------------------
  // substituted-owner filter (D14, §1.5)
  // -------------------------------------------------------------------------------------------

  test("candidates on a substituted owner type are skipped") {
    val src = """
      class Json {
        private boolean ignoreUnknownFields;
        public boolean getIgnoreUnknownFields() { return this.ignoreUnknownFields; }
      }
    """
    val phase  = new NullaryArityTransform(everywhere)
    val before = SpoonTir.fromSource(src)
    // simulate a dependent run where Json is a substituted type
    val scope  = RunScope.of(before.units.map(_.symbol).toSet, Map.empty,
                             substituted = Set("Json"))
    val idioms   = new IdiomLog
    val rewrites = RewriteLog()
    val (after, _) = Pipeline.runTraced(before, List(phase),
      new PolicyBinder(before, before.members, scope), balticporter.catalog.CatalogLog.discarding,
      rewrites, idioms)
    val conv = idioms.all.filter(c =>
      c.kind == IdiomKind.NullaryArity && c.verdict == IdiomVerdict.Converted)
    assertEquals(clue(conv.size), 0,
      "a substituted owner's members must not be converted")
    // the method should keep its `()`
    val sym = after.symbols.all.find(_.fullName.contains("getIgnoreUnknownFields"))
    val defn = sym.flatMap(s => after.definitionOf(s.id)).collect { case d: Tree.DefDef => d }
    assert(defn.exists(_.paramss.nonEmpty), "the method must keep its empty parameter clause")
  }

  test("a declaration in a unit this run does not EMIT keeps its arity — refused as NotEmitted (K51)") {
    val base = """package com.base;
                 |public class Box { public int width () { return 1; } }
                 |""".stripMargin
    val demo = """package com.demo;
                 |class Use { int go (com.base.Box b) { return b.width(); } int size () { return 2; } }
                 |""".stripMargin
    val before = SpoonTir.fromSources(List("Box.java" -> base, "Use.java" -> demo))
    val theirs = before.units.map(_.symbol).filter(u => before.symbolOf(u).exists(_.fullName == "com.base.Box")).toSet
    val phase  = new NullaryArityTransform(everywhere)
    val idioms = new IdiomLog
    val (after, _) = Pipeline.runTraced(before, List(phase),
      new PolicyBinder(before, before.members, RunScope.of(emitted = before.units.map(_.symbol).toSet -- theirs, own = Map.empty)),
      balticporter.catalog.CatalogLog.discarding, RewriteLog(), idioms)
    val out = new TirEmitter(after).emit
    assert(out.contains("b.width()"), out)
    assert(out.contains("def size: scala.Int"), out)
    val guards = idioms.all.collect { case c if c.kind == IdiomKind.NullaryArity => c.verdict }.collect { case IdiomVerdict.Refused(g, _) => g }
    assert(guards.contains("NotEmitted"), guards.toString)
  }

  test("`force` drops `()` on a named member the body guard would refuse — every other guard still applies") {
    val src = """
      class Clip {
        private int reads;
        public boolean hasContents() { reads++; return true; }
        public boolean hasOther() { reads++; return false; }
      }
    """
    val r = ran(src, new NullaryArityTransform(everywhere, force = Set("Clip#hasContents")))
    assertEquals(nameOf(r, "Clip#hasContents"), "hasContents")
    assert(r.out.contains("def hasContents: scala.Boolean"), r.out)
    assert(r.out.contains("def hasOther(): scala.Boolean"), r.out)
    assert(refusedFor(r, "Clip#hasOther").nonEmpty)
  }
