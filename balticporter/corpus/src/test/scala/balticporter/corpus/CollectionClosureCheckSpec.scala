package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.{CollectionClosureCheck, CollectionsTransform}

/** The CLOSURE property of `CollectionsTransform.typeMap`: if a type maps, everything the JDK
  * declares as its subtype must map or be REPORTED.
  *
  * The first test is the PROBE, and it is kept as the first test on purpose: it shows what the
  * engine does today with an unmapped JDK subtype, which is nothing at all — the two sides of an
  * ordinary java assignment come out in different type families, no count moves, and the only
  * evidence is a compile error whose text names neither the mapping nor the phase. The rest of the
  * file is the check turning that into a classified finding, and the negative test asserting that
  * a fully-mapped program reports ZERO (a check that cannot report zero is not a measurement).
  */
class CollectionClosureCheckSpec extends PortSuite:

  // -------------------------------------------------------------------------------------------
  // THE PROBE — today's silent half-translation
  // -------------------------------------------------------------------------------------------

  private val unmapped =
    """package demo;
      |import java.util.List;
      |import java.util.Vector;
      |class U {
      |  Vector<String> own = new Vector<String>();
      |  List<String> widen() { return own; }
      |  void take(List<String> xs) {}
      |  void call() { take(own); }
      |}
      |""".stripMargin

  test("PROBE: an unmapped JDK subtype half-translates, silently — the two sides stop meeting") {
    val p = port(unmapped, new CollectionsTransform)
    // `java.util.List` is mapped and every occurrence of it is retyped …
    assertEmits(p, "scala.collection.mutable.Buffer[java.lang.String]")
    // … while `java.util.Vector`, which java declares a `List`, is left exactly as it was.
    assertEmits(p, "java.util.Vector[java.lang.String]")
    // So `return own` and `take(own)` are now a `java.util.Vector` meeting a `Buffer`. In java
    // these are the SAME assignment that has always compiled; here neither side knows about the
    // other, and nothing in the pipeline said so.
    assertEmits(p, "def widen(): scala.collection.mutable.Buffer[java.lang.String]")
  }

  // -------------------------------------------------------------------------------------------
  // …and the check that names it
  // -------------------------------------------------------------------------------------------

  test("the closure check reports the unmapped subtype, with the NEAREST mapped supertype") {
    val ph = new CollectionsTransform
    val p  = port(unmapped, ph)
    val fs = ph.closure(p.after)
    val vs = fs.filter(_.tpe == "java.util.Vector")
    assert(clue(fs).nonEmpty)
    assert(vs.nonEmpty, "java.util.Vector is referenced and unmapped while java.util.List is mapped")
    // the nearest mapped ancestor, so the finding says which target keeps the relation. Not
    // `java.lang.Iterable`, which is also mapped and also an ancestor — and useless as advice.
    assertEquals(vs.map(_.coveredBy).distinct, List("java.util.List"))
    assertEquals(vs.map(_.mapsTo).distinct, List("scala.collection.mutable.Buffer"))
    // located, so the finding is actionable without opening the emitted file (CLAUDE.md §5.1).
    assert(vs.forall(_.origin.line > 0), clue(vs.map(_.origin.line)))
    assert(clue(CollectionClosureCheck.summary(vs)).contains("java.util.Vector"))
  }

  test("the ZERO is asserted: a program whose every collection type IS mapped reports nothing") {
    // The negative test the check's own value depends on. Every type here is a `typeMap` key, so
    // the closure is complete and there is nothing to say. A check that cannot produce this answer
    // is reporting its own coverage, not the program's.
    val ph = new CollectionsTransform
    val p = port(
      """package demo;
        |import java.util.*;
        |class F {
        |  List<String> l = new ArrayList<String>();
        |  Set<String> s = new HashSet<String>();
        |  Map<String,String> m = new HashMap<String,String>();
        |  Deque<String> d = new ArrayDeque<String>();
        |  Iterator<String> it() { return l.iterator(); }
        |  Collection<String> c() { return l; }
        |  Iterable<String> i() { return l; }
        |}
        |""".stripMargin,
      ph,
    )
    assertEquals(clue(ph.closure(p.after)), Nil)
  }

  test("a JDK type unrelated to anything mapped is NOT reported — the family is decided by the edges") {
    // `java.util.Random` and `java.util.Comparator` live in `java.util` and are not collections.
    // A check that decided the family from the PACKAGE would report both (CLAUDE.md §4.56 — a
    // prefix is not a structural fact), which is exactly the noise that makes a check unread.
    val ph = new CollectionsTransform
    val p = port(
      """package demo;
        |import java.util.*;
        |class N {
        |  Random r = new Random(1);
        |  List<String> xs = new ArrayList<String>();
        |  void sort(Comparator<String> c) { Collections.sort(xs, c); }
        |  int next() { return r.nextInt(3); }
        |}
        |""".stripMargin,
      ph,
    )
    assertEquals(clue(ph.closure(p.after)).map(_.tpe), Nil)
  }

  test("an ABSTRACT base a library extends is covered too — K5's shape, as a finding") {
    // `java.util.AbstractList` is what a library EXTENDS while `java.util.List` is what it
    // DECLARES, and the two disagreeing is 13 of simple-graphs' 20 errors (ENGINE-LIMITS K5).
    // `AbstractCollection` is mapped, so this is the SAME hole one level down.
    val ph = new CollectionsTransform
    val p = port(
      """package demo;
        |import java.util.*;
        |class Own extends AbstractList<String> {
        |  public String get(int i) { return null; }
        |  public int size() { return 0; }
        |}
        |""".stripMargin,
      ph,
    )
    val fs = ph.closure(p.after).filter(_.tpe == "java.util.AbstractList")
    assert(fs.nonEmpty, clue(ph.closure(p.after)).toString)
    // the nearest mapped ancestor is `AbstractCollection`, not `List` — which is the right advice:
    // the target must be whatever `AbstractCollection` already became, or the two split again.
    assertEquals(fs.map(_.coveredBy).distinct, List("java.util.AbstractCollection"))
    assertEquals(fs.map(_.mapsTo).distinct, List("balticporter.runtime.JavaCollection"))
  }

  // -------------------------------------------------------------------------------------------
  // the hierarchy table itself
  // -------------------------------------------------------------------------------------------

  test("supertypesOf is NEAREST-FIRST and transitive — the order the finding's advice rests on") {
    val sup = CollectionClosureCheck.supertypesOf("java.util.LinkedHashSet")
    assertEquals(sup.head, "java.util.HashSet")
    assert(sup.contains("java.util.AbstractSet"))
    assert(sup.contains("java.util.Set"))
    assert(sup.contains("java.util.Collection"))
    assert(sup.contains("java.lang.Iterable"))
    // …and it terminates on a cycle-free table without walking forever.
    assertEquals(CollectionClosureCheck.supertypesOf("java.util.Map$Entry"), Nil)
    assertEquals(CollectionClosureCheck.supertypesOf("java.lang.String"), Nil)
  }

  test("every edge's TARGET is either a table key or a deliberate leaf — no typo survives") {
    // The one failure mode a transcribed table has: a misspelled parent, which silently ends the
    // walk and turns a finding into a silence. Every target must either have its own entry or be
    // one of the two roots this table deliberately stops at.
    val leaves = Set("java.lang.Iterable", "java.util.Map", "java.util.Iterator", "java.util.Map$Entry",
                     "java.util.Map.Entry", "java.util.RandomAccess", "java.util.Dictionary")
    val orphans = CollectionClosureCheck.jdkSupertypes.values.flatten.toSet
      .filterNot(t => CollectionClosureCheck.jdkSupertypes.contains(t) || leaves(t))
    assertEquals(clue(orphans), Set.empty[String])
  }

  test("a finding is held to the units the run EMITS — a dependent never reports its base's (D2)") {
    // Unfiltered, this check reported the SAME two findings for libGDX core, libGDX's test suite
    // and both Ashley source sets: `AsyncExecutor`'s two `java.util.concurrent` queues, seen four
    // times, three of them by a repository that cannot act on them. A finding an agent cannot fix
    // in its own repository is CLAUDE.md §4.45's "cannot classify" failure with a plausible owner
    // attached (ENGINE-LIMITS D2).
    val ph = new CollectionsTransform
    val p = port(
      """package demo;
        |import java.util.*;
        |class Base { Vector<String> v = new Vector<String>(); }
        |class Dep  { List<String> l = new ArrayList<String>(); }
        |""".stripMargin,
      ph,
    )
    assert(clue(ph.closure(p.after)).nonEmpty)
    def unit(n: String) = p.after.units.filter(u => p.after.symbolOf(u.symbol).exists(_.fullName == n))
    assertEquals(clue(ph.closure(p.after, unit("demo.Dep"))), Nil)
    assert(ph.closure(p.after, unit("demo.Base")).nonEmpty)
  }

  test("the check is a NO-OP with an empty mapping — an empty policy needs no code path (§1(b))") {
    val ph = new CollectionsTransform
    val p  = port(unmapped, ph)
    assertEquals(CollectionClosureCheck.check(p.after, Set.empty), Nil)
  }
