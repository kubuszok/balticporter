package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.PortSuite
import balticporter.tir.{Decision, Pipeline, PorterNote, Program, Reason}
import balticporter.transform.{CollectionBoundaryCheck, CollectionsTransform}

/** A MEMBER THAT OVERRIDES A CLASS FILE keeps its formals — `CLAUDE.md` §4.56 read at an OVERRIDE
  * rather than at a call. */
class CollectionsClassFileOverrideSpec extends PortSuite:

  /** `Holder` is the positive; `Fast` is the mapped-parent negative; `Ours`/`Impl` are the
    * program-declared-ancestor negative; `Holder#absorb` is the not-an-override negative. */
  private val src =
    """package demo;
      |import java.util.*;
      |public class Holder extends java.util.AbstractMap<String, String> {
      |  private final Map<String, String> backing = new HashMap<String, String>();
      |  public void putAll(Map<? extends String, ? extends String> m) { super.putAll(m); }
      |  public Set<Map.Entry<String, String>> entrySet() { return backing.entrySet(); }
      |  public void absorb(Collection<String> more) { }
      |}
      |class Fast extends java.util.ArrayList<String> {
      |  public boolean addAll(Collection<? extends String> c) { return super.addAll(c); }
      |}
      |interface Ours { List<String> names(); }
      |class Impl implements Ours { public List<String> names() { return new ArrayList<String>(); } }
      |class Caller {
      |  void ask(Holder h, Map<String, String> mine) { h.putAll(mine); }
      |}
      |""".stripMargin

  /** the FQN this fixture's positive stands on. Named once so the premise test and the assertions
    * cannot drift apart, which is exactly how the `AbstractSet` version went stale. */
  private val UnmappedBase = "java.util.AbstractMap"

  private def ported: (CollectionsTransform, Program, String) =
    val ph    = new CollectionsTransform()
    val after = Pipeline.run(SpoonTir.fromSource(src), List(ph))
    (ph, after, new TirEmitter(after).emit)

  private def heldNames(ph: CollectionsTransform, p: Program): Set[String] =
    ph.classFileOverrides.flatMap(p.symbolOf).map(_.fullName)

  // -------------------------------------------------------------------------
  // the positive
  // -------------------------------------------------------------------------

  test("THE PREMISE — this fixture's parent really is a type the mapping does not cover") {
    // Asked of the phase's own table, not assumed. Wave 12 mapped the type this spec used to stand
    // on, and every assertion below then failed saying `heldNames = Set()` — which reads as a broken
    // refusal rather than as a moved example. This row is what turns that into a sentence.
    assert(!clue(CollectionsTransform.typeMap).contains(UnmappedBase),
           s"$UnmappedBase is now MAPPED, so it can no longer play the unmapped-parent role here. " +
             "Move this fixture to a base that is still absent from `typeMap` (java.util.AbstractList, " +
             "java.util.AbstractSequentialList) — and see ENGINE-LIMITS.md K29, because mapping an " +
             "abstract base is exactly the step that owes the JDK defaults a definer calls through `super`.")
  }

  test("a member overriding an UNMAPPED java parent keeps java's formal") {
    val (ph, p, out) = ported
    assert(clue(heldNames(ph, p)).exists(_.endsWith("Holder#putAll")),
           s"Holder#putAll overrides $UnmappedBase's and must be held literally")
    assert(out.contains("def putAll(m: java.util.Map[? <: java.lang.String, ? <: java.lang.String])"),
           s"the emitted formal is not java's\n--- emitted ---\n$out")
  }

  test("…and the refusal is a RECORDED decision, universal, naming what it overrides") {
    val (ph, p, _) = ported
    val log = Pipeline.runTraced(SpoonTir.fromSource(src), List(new CollectionsTransform()))._2
    val ds  = log.of(Decision.Kind.RetainedSignature).filter(_.subjectFqn.endsWith("Holder#putAll"))
    assertEquals(clue(ds).size, 1)
    assert(ds.head.reason.isInstanceOf[Reason.Universal], clue(ds.head.reason).toString)
    assertEquals(PorterNote.pairs(ds.head).toMap.get("overrides"), Some(s"$UnmappedBase#putAll"))
    // …and it is RENDERED at the declaration: a signature that did not move shows nothing in a diff
    // against the java, so the note is the only evidence at the line (§4.575).
    assert(PorterNote.Rendered.contains(Decision.Kind.RetainedSignature))
    assert(PorterNote.AtDeclaration.contains(Decision.Kind.RetainedSignature))
    // the fixture must not pass because the phase held nothing at all
    assert(ph.classFileOverrides.nonEmpty)
  }

  // -------------------------------------------------------------------------
  // the negatives
  // -------------------------------------------------------------------------

  test("a class extending a MAPPED collection keeps its retyped override — the parent is the shim") {
    val (ph, p, out) = ported
    assert(!clue(heldNames(ph, p)).exists(_.endsWith("Fast#addAll")),
           "Fast extends java.util.ArrayList, which the mapping covers: its override must MOVE")
    assert(!out.contains("def addAll(c: java.util.Collection[? <: java.lang.String])"),
           s"Fast#addAll kept java's formal under a shim parent\n--- emitted ---\n$out")
  }

  test("a member overriding an interface THIS PROGRAM declares is not held") {
    val (ph, p, _) = ported
    assert(!clue(heldNames(ph, p)).exists(_.contains("Impl#names")))
    assert(!clue(heldNames(ph, p)).exists(_.contains("Ours#names")))
  }

  test("a member that overrides NOTHING is not held, whatever its signature mentions") {
    val (ph, p, _) = ported
    assert(!clue(heldNames(ph, p)).exists(_.endsWith("Holder#absorb")))
  }

  test("…nor is one overriding a program-declared GENERIC interface INSTANTIATED at an argument") {
    // The negative above stands on a NON-generic interface, where the two descriptors are the same
    // string. Instantiate the interface and they are not: `names(T)` above, `names(String)` below.
    // `OverrideGraph.matchingUp` reads that edge through `ParentSubst` now; before it did not, so
    // `overridden` answered EMPTY, this phase concluded the member must override a CLASS FILE, and
    // the only external ancestor an enum has is `java.lang.
    val generic =
      """package demo;
        |import java.util.*;
        |interface Keyed<T> { List<T> keys(T seed); }
        |enum Kinds implements Keyed<String> {
        |  ONE;
        |  public List<String> keys(String seed) { return new ArrayList<String>(); }
        |}
        |class Plain implements Keyed<String> {
        |  public List<String> keys(String seed) { return new ArrayList<String>(); }
        |}
        |""".stripMargin
    val ph  = new CollectionsTransform()
    val out = new TirEmitter(Pipeline.run(SpoonTir.fromSource(generic), List(ph))).emit
    val held = ph.classFileOverrides.flatMap(SpoonTir.fromSource(generic).symbolOf).map(_.fullName)
    assert(clue(held).isEmpty, "an override of a program-declared interface must MOVE, generic or not")
    assert(!clue(out).contains("java.util.List[java.lang.String]"),
           s"a java formal was held under a program-declared parent\n--- emitted ---\n$out")
  }

  test("NEGATIVE: an ANONYMOUS class's member is out of the refusal's reach and is not held") {
    // `restoreExcluded` splices along a `Tree.ClassDef`'s DECLARATION SPINE; an anonymous body hangs
    // off a `Tree.New` inside a term and is not on it. Held there, the SYMBOL would go literal and
    // the TREE would stay mapped — and the porter note would claim a signature the emitted `def`
    // does not have, which is what shipped on liqp for one `new ThreadLocal<Map<K,V>>(){ … }`.
    val anon =
      """package demo;
        |import java.util.*;
        |class Holder2 {
        |  static ThreadLocal<Map<String, Object>> local = new ThreadLocal<Map<String, Object>>() {
        |    protected Map<String, Object> initialValue() { return new HashMap<String, Object>(); }
        |  };
        |}
        |""".stripMargin
    val ph  = new CollectionsTransform()
    val p   = Pipeline.run(SpoonTir.fromSource(anon), List(ph))
    val out = new TirEmitter(p).emit
    assert(!heldNames(ph, p).exists(_.contains("initialValue")), clue(heldNames(ph, p)).toString)
    assert(!out.contains("porter: retained-signature"),
           s"a note was emitted for a member the restore cannot reach\n--- emitted ---\n$out")
  }

  test("a program with no unconverted java parent holds NOTHING — the no-op, by arithmetic") {
    val plain =
      """package demo;
        |import java.util.*;
        |class Solo { private List<String> xs = new ArrayList<String>();
        |  public List<String> all() { return xs; } }
        |""".stripMargin
    val ph = new CollectionsTransform()
    Pipeline.run(SpoonTir.fromSource(plain), List(ph))
    assertEquals(ph.classFileOverrides, Set.empty[balticporter.tir.SymId])
  }

  // -------------------------------------------------------------------------
  // the seam, and its classification
  // -------------------------------------------------------------------------

  test("the seam at a CALLER is ClassFileOverride, and its sentence names no key to change") {
    val (ph, p, _) = ported
    val rows = ph.boundary(p).filter(_.issue == CollectionBoundaryCheck.Issue.ClassFileOverride)
    // the caller passes a retyped Buffer at a formal that stayed java's; where `coerce` bridges it
    // with a live view the slot closes, and either way NO row may be filed as `ScopedOut`.
    assertEquals(ph.boundary(p).count(_.issue == CollectionBoundaryCheck.Issue.ScopedOut), 0,
                 "this run set no scope: a ScopedOut row would send its reader after a key that does not exist")
    val sentence = CollectionBoundaryCheck.Issue.classification(CollectionBoundaryCheck.Issue.ClassFileOverride)
    assert(sentence.contains("§1(a)"), clue(sentence))
    assert(!sentence.contains("§1(b)"), clue(sentence))
    assert(rows.forall(_.expected.startsWith("java.")), clue(rows).toString)
  }
