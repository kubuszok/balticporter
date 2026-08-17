package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.{CollectionInternalCheck, CollectionsTransform}
import balticporter.transform.CollectionInternalCheck.Issue

/** The IN-PROGRAM half of the collections residue — every site where java's own subtyping carried a
  * value across an edge the mapping has no image for.
  *
  * Every test here is paired with the NEGATIVE that decides it, because each arm has a shape that
  * looks identical and is correct:
  *
  *   - a library's own collection routinely carries BOTH ends of the split as parents
  *     (`OrderedSet extends mutable.Set with JavaIterable`), so an arm that only looked for an end
  *     on the far side would report every one of its CORRECT slots;
  *   - a type variable bound twice to the SAME side is java's ordinary generic call and there is
  *     nothing to report;
  *   - and the arm that is NOT here at all: a call at a symbol the phase MINTED, whose operands
  *     span the edge and whose helper may well take both.
  *
  * And one negative for the check as a whole: `CollectionBoundaryCheck` must not report the same
  * sites, or the two lanes count one residue twice and a baseline diff can attribute neither.
  */
class CollectionInternalCheckSpec extends PortSuite:

  private def findings(java: String) =
    val ph = new CollectionsTransform
    val p  = port(java, ph)
    (p, ph.internal(p.after), ph.boundary(p.after))

  // -------------------------------------------------------------------------------------------
  // DeclaredSubtype — the program's own class, on the far side of the edge from the slot
  // -------------------------------------------------------------------------------------------

  private val ownSet =
    """package demo;
      |import java.util.*;
      |class Own<E> implements Set<E> { }
      |class Holder<E> {
      |  Own<E> own;
      |  Collection<E> all() { return own; }
      |  Set<E> asSet() { return own; }
      |}
      |""".stripMargin

  test("the DeclaredSubtype seam is BRIDGED at the slot, so the lane that named it now reads zero") {
    val (p, fs, _) = findings(ownSet)
    // the phase re-parented the class onto `Set`'s target and the slot onto `Collection`'s, and
    // those two have no relation: `JavaCollection` is standalone BECAUSE §4.5 says it must be.
    assertEmits(p, "extends scala.collection.mutable.Set[E]")
    assertEmits(p, "def all(): balticporter.runtime.JavaCollection[E]")
    // …and THAT is what `coerce` now closes: the class really IS a `mutable.Set` here because this
    // phase made it one, so `JavaCollection.fromSet` conforms and the value is wrapped at the slot
    // rather than left to a java subtyping edge with no scala image (`ENGINE-LIMITS.md` K26).
    assertEmits(p, "balticporter.runtime.JavaCollection.fromSet(this.own)")
    assertEquals(clue(fs.filter(_.issue == Issue.DeclaredSubtype)), Nil)
    // The ARM is kept as a GUARD rather than deleted: it fires wherever `coerce` has no factory for
    // the pair, and the one such cell left (`Kind.Map` into `JavaCollection`) is one java itself
    // cannot write — a `Map` is not a `Collection`. Its vocabulary is asserted here so a row that
    // DOES appear arrives with the §1 classification a bare typer error cannot give (§4.45).
    assert(clue(Issue.classification(Issue.DeclaredSubtype)).contains("§1(a)"))
    assert(clue(CollectionInternalCheck.summary(Nil)).contains("none"))
  }

  test("NEGATIVE: the SAME class at the SAME target's slot was never a seam — and still is not") {
    val (p, fs, _) = findings(ownSet)
    // `asSet()` returns the same value at `Set`'s own target. Nothing is wrong with it, and a rule
    // that looked only for "an ancestor on the far side" would report it, because `Own` really does
    // inherit `JavaIterable` through `Set <: Collection <: Iterable`. It must not be WRAPPED either:
    // a factory call around a value that already conforms is emitted text for nothing.
    assertEquals(clue(fs.filter(f => f.issue == Issue.DeclaredSubtype && f.slot == "return")), Nil)
    assertEmits(p, "def asSet(): scala.collection.mutable.Set[E]")
  }

  test("NEGATIVE: the boundary lane reports NEITHER — which is why this lane exists") {
    val (_, _, bnd) = findings(ownSet)
    // `sideOf` reads a head FQN, and a program-declared class is `Other` on both of them.
    assert(clue(bnd.filter(_.slot == "return")).isEmpty)
  }

  // -------------------------------------------------------------------------------------------
  // SplitTypeVariable — the disagreement is at no formal's head
  // -------------------------------------------------------------------------------------------

  // THE SHAPE IS NOW THE RESIDUE, not the population — and that is the pass draining the lane.
  // `set(Key<V> k, V v)` used to be this fixture, and `CollectionsTransform` now answers it: `Key<V>`
  // is INVARIANT, so the key argument fixes `V` and the value is coerced TO it (`ENGINE-LIMITS.md`
  // K26, measured `collection-internal` 5 -> 0 with its five errors). What no substitution can answer
  // is the shape with NO parameterised formal to read the variable off — TWO BARE occurrences, where
  // java infers the lub and the phase has no standing to pick one side — so that is what this lane
  // counts now, and the drained shape is the case below it.
  private val splitVar =
    """package demo;
      |import java.util.*;
      |class Key<T> { }
      |class Store {
      |  <V> void put(V a, V b) { }
      |  <V> void keyed(Key<V> k, V v) { }
      |  void crossing(Collection<String> c, ArrayList<String> xs) { put(c, xs); }
      |  void same(ArrayList<String> a, ArrayList<String> b) { put(a, b); }
      |  void drained(Key<Collection<String>> k, ArrayList<String> xs) { keyed(k, xs); }
      |}
      |""".stripMargin

  test("one type variable bound to BOTH sides of the edge — java widened, scala cannot") {
    val (_, fs, _) = findings(splitVar)
    val sv = fs.filter(_.issue == Issue.SplitTypeVariable)
    assertEquals(clue(sv).size, 1)
    assert(clue(sv.head.slot).startsWith("type variable V of put"))
    assertEquals(sv.head.edge, "java.util.ArrayList <: java.util.Collection")
    assert(clue(sv.head.targets).contains("scala.collection.mutable.ArrayBuffer"))
    assert(clue(Issue.classification(Issue.SplitTypeVariable)).contains("§1(a)"))
  }

  test("NEGATIVE: the same variable bound TWICE to one side is an ordinary generic call") {
    val (_, fs, _) = findings(splitVar)
    // `same(…)` binds `V` to `ArrayBuffer` from both arguments. One finding, from `crossing` only.
    assertEquals(clue(fs.count(_.issue == Issue.SplitTypeVariable)), 1)
  }

  test("NEGATIVE: a SIBLING PARAMETERISED formal fixes the variable, so the pass drains it here") {
    val (p, fs, _) = findings(splitVar)
    // `keyed(k, xs)` is the very shape this lane used to be written on, and it reports nothing now
    // because the coercion runs at the inference site and the seam is CLOSED rather than merely
    // uncounted — which is the distinction `CLAUDE.md` §5 asks a falling lane to make. The emitted
    // wrap is the evidence: a lane reading zero because a check stopped asking looks identical.
    assertEmits(p, "balticporter.runtime.JavaCollection.from(xs)")
    assertEquals(clue(fs.filter(f => f.issue == Issue.SplitTypeVariable && f.slot.contains("keyed"))), Nil)
  }

  test("NEGATIVE: a CLASS's type parameter is not this call's to bind") {
    // `Key<T>`'s own `T` appears in `put`'s first formal and is owned by the CLASS, so the arm must
    // not treat it as a variable this call binds — otherwise every generic receiver reports.
    val (_, fs, _) = findings(
      """package demo;
        |import java.util.*;
        |class Box<T> { void set(T a, T b) { } }
        |class Use {
        |  void f(Box<Collection<String>> b, Collection<String> c, ArrayList<String> xs) { b.set(c, xs); }
        |}
        |""".stripMargin)
    assert(clue(fs.filter(_.issue == Issue.SplitTypeVariable)).isEmpty)
  }

  // -------------------------------------------------------------------------------------------
  // the arm that is deliberately ABSENT — a call at a symbol the phase MINTED
  // -------------------------------------------------------------------------------------------

  test("NEGATIVE: a MINTED helper's operands span the edge and NOTHING is reported") {
    val (p, fs, _) = findings(
      """package demo;
        |import java.util.*;
        |class M {
        |  HashSet<String> s = new HashSet<>();
        |  void f(Collection<String> c) { s.addAll(c); }
        |}
        |""".stripMargin)
    // the rewrite really does hand a `mutable.HashSet` and a `JavaCollection` to one helper, and
    // the helper has NO signature to check either against — which is exactly why an arm reading the
    // operands alone cannot tell this site (2 compile errors) from
    // `JavaCollections.containsAll`, whose `IterableOnce[?] | JavaIterable[?]` formal exists for
    // this shape and closes it. `ENGINE-LIMITS.md` K2.5's defect; the repair is signatures on the
    // mints, not a wider guard here.
    assertEmits(p, "balticporter.runtime.JavaCollections.addAll(")
    assertEquals(clue(fs), Nil)
  }

  // -------------------------------------------------------------------------------------------
  // the no-op
  // -------------------------------------------------------------------------------------------

  test("NEGATIVE: a program that mixes no families reports nothing") {
    val (_, fs, _) = findings(
      """package demo;
        |import java.util.*;
        |class Plain {
        |  List<String> xs = new ArrayList<>();
        |  void add(String s) { xs.add(s); }
        |  List<String> all() { return xs; }
        |}
        |""".stripMargin)
    assertEquals(clue(fs), Nil)
  }
