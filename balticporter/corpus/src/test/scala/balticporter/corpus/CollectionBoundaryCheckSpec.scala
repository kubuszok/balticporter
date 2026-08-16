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

  test("the two MAP VIEWS are no longer counted — the seam CLOSED, so the count is zero") {
    // This test used to be two, and each pinned a `ShimBoundary` row that was an honest refusal at
    // the time: `m.keySet()` emitted a `scala.collection.Set` its node claimed was a `mutable.Set`,
    // and `m.entrySet()` handed back the MAP, which is no `Collection` view of anything. The
    // rewrites now emit live `mutable.Set` views, so both are ordinary `Kind.Set` sources with a
    // factory on `coerce`'s first table.
    //
    // Asserted as a ZERO rather than deleted, and that is §4.56's rule read at a check: a residue
    // count is only as good as the assumption that everything able to close it RAN, so the day a
    // view stops being emitted this reads two findings instead of silence. They were also the only
    // `ShimBoundary` pair VALID JAVA could reach — the remaining cells of that table are
    // unreachable because java itself forbids the assignment (a `Map` is no `Collection`, a
    // `Collection` is no `List`) — so the row is empty on all fifteen ports and its classification
    // is what a consumer would meet if a new mapping target ever reopened it. That sentence is
    // pinned here rather than left to the day it fires (§4.45).
    val (_, fs) = findings(
      """package demo;
        |import java.util.*;
        |class M {
        |  void takeKeys(Collection<String> c) {}
        |  void takeColl(Collection<Map.Entry<String,String>> c) {}
        |  void argKeys(Map<String,String> m) { takeKeys(m.keySet()); }
        |  void argColl(Map<String,String> m) { takeColl(m.entrySet()); }
        |}
        |""".stripMargin
    )
    assertEquals(clue(fs.filter(_.issue == Issue.ShimBoundary)), Nil)
    assert(clue(Issue.classification(Issue.ShimBoundary)).contains("§1(a)"))
    assert(clue(Issue.classification(Issue.ShimBoundary)).contains("coerce"))
  }

  test("an UNMAPPED JDK subtype meeting a retyped slot is the closure hole, met as a SITE") {
    // The same defect `CollectionClosureCheck` reports as a TYPE. Both are wanted: the closure
    // check says the mapping is incomplete whether or not the corpus mixes the two, and this says
    // exactly where it did.
    val (_, fs) = findings(
      """package demo;
        |import java.util.*;
        |import java.util.concurrent.*;
        |class U {
        |  CopyOnWriteArrayList<String> own = new CopyOnWriteArrayList<String>();
        |  List<String> widen() { return own; }
        |  void take(List<String> xs) {}
        |  void call() { take(own); }
        |}
        |""".stripMargin
    )
    assertEquals(clue(fs).map(_.issue).distinct, List(Issue.UnmappedSubtype))
    assertEquals(fs.map(_.slot).sorted, List("argument", "return"))
    assert(fs.forall(_.actual == "java.util.concurrent.CopyOnWriteArrayList"), clue(fs).toString)
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
        |import java.util.concurrent.*;
        |class Base { CopyOnWriteArrayList<String> v = new CopyOnWriteArrayList<String>(); List<String> w() { return v; } }
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
