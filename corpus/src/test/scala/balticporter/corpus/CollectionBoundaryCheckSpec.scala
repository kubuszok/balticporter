package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.{CollectionBoundaryCheck, CollectionsTransform}
import balticporter.transform.CollectionBoundaryCheck.Issue

/** The JDK/Scala collection BOUNDARY, counted — every slot `CollectionsTransform` opened and
  * `coerce` did not close.
  *
  * The first test is the PROBE and stays first: it shows what a stranded slot looks like today,
  * which is a bare `Found: … / Required: …` from scalac with no classification, no origin in the
  * JAVA source and nothing to say which of CLAUDE.md §1's three kinds the fix is. That is the bulk
  * of a new library's first wall (§4.45). Everything after it is the check turning those into a
  * triaged list, and the negative test asserting the ZERO.
  */
class CollectionBoundaryCheckSpec extends PortSuite:

  private def findings(java: String) =
    val ph = new CollectionsTransform
    val p  = port(java, ph)
    (p, ph.boundary(p.after))

  // -------------------------------------------------------------------------------------------
  // THE PROBE — a stranded slot, as the engine leaves it today
  // -------------------------------------------------------------------------------------------

  private val streamSlot =
    """package demo;
      |import java.util.*;
      |import java.util.stream.*;
      |import java.util.function.Predicate;
      |class S {
      |  List<String> f;
      |  List<String> viaLocal(Predicate<String> p) {
      |    Stream<String> st = f.stream();
      |    return st.filter(p).collect(Collectors.toList());
      |  }
      |}
      |""".stripMargin

  test("PROBE: the retyping strands a slot and nothing counts it — the value moved, the slot did not") {
    val (p, _) = findings(streamSlot)
    // the SOURCE collapses to a scala `Buffer` — with NO accessor, because `f` is a
    // `java.util.List` and retypes to a `Buffer`, which already IS the sequence. `asScalaBuffer` is
    // the SHIM's accessor and this receiver is not one (`CollectionsTransform.streamSource`) …
    assertEmits(p, "= this.f\n")
    // … while the DECLARATION still says `java.util.stream.Stream`, because the stream family is
    // deliberately not retyped (ENGINE-LIMITS K6). Two types that java made agree, and no number
    // in the pipeline moves: this is not an omission, not a portability site, not a signature
    // mismatch. Measured as 2 compile errors, and until now that was the ONLY evidence.
    assertEmits(p, "val st: java.util.stream.Stream[java.lang.String] =")
  }

  test("…and the check names it, with an origin and a §1 classification") {
    val (_, fs) = findings(streamSlot)
    val decl = fs.filter(f => f.slot == "declaration" && f.expected.startsWith("java.util.stream."))
    assertEquals(clue(decl).size, 1)
    assertEquals(decl.head.issue, Issue.UntranslatedFamily)
    assertEquals(decl.head.actual, "scala.collection.mutable.Buffer")
    assert(decl.head.origin.line > 0)
    // the classification is the whole point: an agent in another repository can act on this
    // without investigating which of the three kinds it is (CLAUDE.md §4.45).
    assert(clue(Issue.classification(Issue.UntranslatedFamily)).contains("§1(a)"))
    assert(clue(CollectionBoundaryCheck.summary(fs)).contains("UntranslatedFamily"))
  }

  // -------------------------------------------------------------------------------------------
  // the second line: both sides are the phase's own output
  // -------------------------------------------------------------------------------------------

  test("a shim slot fed a scala collection is counted — the refusals are findings, not absences") {
    // `m.keySet()` is REFUSED by `coerce` on purpose (its node type overstates the scala the
    // emitter prints), so the slot stays open and the port fails to compile there. A refusal that
    // nothing counts is indistinguishable from a case nobody thought of, which is exactly the
    // "we always ignore those four" rot CLAUDE.md §5.1 objects to.
    val (_, fs) = findings(
      """package demo;
        |import java.util.*;
        |class K {
        |  void take(Collection<String> c) {}
        |  void argMapKeys(Map<String,String> m) { take(m.keySet()); }
        |}
        |""".stripMargin
    )
    val shim = fs.filter(_.issue == Issue.ShimBoundary)
    assertEquals(clue(shim).size, 1)
    assertEquals(shim.head.slot, "argument")
    assertEquals(shim.head.expected, "balticporter.runtime.JavaCollection")
    assertEquals(shim.head.actual, "scala.collection.mutable.Set")
  }

  test("the Kind.Map -> JavaCollection cell — refused by `coerce`, counted here") {
    val (_, fs) = findings(
      """package demo;
        |import java.util.*;
        |class M {
        |  void takeColl(Collection<Map.Entry<String,String>> c) {}
        |  void argColl(Map<String,String> m) { takeColl(m.entrySet()); }
        |}
        |""".stripMargin
    )
    val shim = fs.filter(_.issue == Issue.ShimBoundary)
    assertEquals(clue(shim).map(f => (f.slot, f.expected, f.actual)),
      List(("argument", "balticporter.runtime.JavaCollection", "scala.collection.mutable.Map")))
  }

  test("an UNMAPPED JDK subtype meeting a retyped slot is the closure hole, met as a SITE") {
    // The same defect `CollectionClosureCheck` reports as a TYPE. Both are wanted: the closure
    // check says the mapping is incomplete whether or not the corpus mixes the two, and this says
    // exactly where it did.
    val (_, fs) = findings(
      """package demo;
        |import java.util.*;
        |class U {
        |  Vector<String> own = new Vector<String>();
        |  List<String> widen() { return own; }
        |  void take(List<String> xs) {}
        |  void call() { take(own); }
        |}
        |""".stripMargin
    )
    assertEquals(clue(fs).map(_.issue).distinct, List(Issue.UnmappedSubtype))
    assertEquals(fs.map(_.slot).sorted, List("argument", "return"))
    assert(fs.forall(_.actual == "java.util.Vector"), clue(fs).toString)
    assert(fs.forall(_.expected == "scala.collection.mutable.Buffer"), clue(fs).toString)
  }

  // -------------------------------------------------------------------------------------------
  // the ZERO, and the walk's bounds
  // -------------------------------------------------------------------------------------------

  test("the ZERO is asserted: a program `coerce` fully bridges strands NOTHING") {
    // Every slot kind `coerce` covers, in one program — argument, declaration, assignment, return
    // — over both source kinds it bridges. If this reported anything the check would be measuring
    // its own false positives rather than the port.
    val (_, fs) = findings(
      """package demo;
        |import java.util.*;
        |class C {
        |  Collection<String> fld;
        |  void take(Collection<String> c) {}
        |  void takeIt(Iterable<String> i) {}
        |  Collection<String> retNew()                          { return new ArrayList<String>(); }
        |  Collection<String> retVar(List<String> xs)           { return xs; }
        |  Iterable<String> retIterable(List<String> xs)        { return xs; }
        |  Collection<String> retIf(List<String> xs, boolean b) { if (b) return xs; return null; }
        |  Collection<String> retSet(Set<String> s)             { return s; }
        |  Iterable<String> retSetIterable(Set<String> s)       { return s; }
        |  void argSet(Set<String> s)    { take(s); }
        |  void argSetIt(Set<String> s)  { takeIt(s); }
        |  void declSet(Set<String> s)   { Collection<String> c = s; }
        |  void assignSet(Set<String> s) { fld = s; }
        |  void plain(List<String> xs, Map<String,Integer> m, Set<String> s) {
        |    xs.add("a"); m.put("k", 1); s.add("b");
        |    for (String x : xs) { take(new ArrayList<String>()); }
        |  }
        |}
        |""".stripMargin
    )
    assertEquals(clue(fs).map(_.render), Nil)
  }

  test("a `return` inside a LAMBDA is not measured against the enclosing method — the walk is BOUNDED") {
    // The one way this walk could be wrong SILENTLY rather than merely short. `coerceReturns` has
    // the same bound and the same default-does-not-descend rule; these two tests are what keep the
    // two node lists honest about each other.
    // The inner `return xs` is a `Buffer` and the ENCLOSING method returns a `Collection`, i.e. the
    // shim — so a walk that descended into the lambda would report a `ShimBoundary` here, at a
    // slot that does not exist. A bare `return;` would not prove this: it carries no expression,
    // so it is skipped whether the walk descends or not.
    val (_, fs) = findings(
      """package demo;
        |import java.util.*;
        |import java.util.function.Supplier;
        |class L {
        |  Collection<String> outer(List<String> xs) {
        |    Supplier<List<String>> f = () -> { return xs; };
        |    return xs;
        |  }
        |}
        |""".stripMargin
    )
    assertEquals(clue(fs).map(_.render), Nil)
  }

  test("a stranded slot is held to the units the run EMITS — a dependent never reports its base's (D2)") {
    // The same D2 filter `OmissionCheck` and `PortabilityCheck.inEmittedCode` carry: a dependent
    // port's program contains its base's units, and a slot stranded inside one of those is the
    // base's finding.
    val ph = new CollectionsTransform
    val p = port(
      """package demo;
        |import java.util.*;
        |class Base { Vector<String> v = new Vector<String>(); List<String> w() { return v; } }
        |class Dep  { List<String> l = new ArrayList<String>(); List<String> w() { return l; } }
        |""".stripMargin,
      ph,
    )
    assert(clue(ph.boundary(p.after)).nonEmpty)
    def unit(n: String) = p.after.units.filter(u => p.after.symbolOf(u.symbol).exists(_.fullName == n))
    assertEquals(clue(ph.boundary(p.after, unit("demo.Dep"))), Nil)
    assert(ph.boundary(p.after, unit("demo.Base")).nonEmpty)
  }

  test("the check is a NO-OP with an empty mapping — an empty policy needs no code path (§1(b))") {
    // The program a phase with no mapping produces is the UNCHANGED program, so there is no scala
    // side for anything to be stranded against. Note what this test must NOT be: running the check
    // with an empty mapping over a program a REAL mapping already retyped still reports, and
    // rightly — the boundary is in the program by then, whatever set is passed. The no-op is a
    // property of the pair, which is why `CollectionsTransform.boundary` is the only caller that
    // can get it wrong and why it passes its own policy.
    val p = port(streamSlot)
    assertEquals(clue(CollectionBoundaryCheck.check(p.after, Set.empty, Set.empty)), Nil)
  }
