package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.PortSuite
import balticporter.tir.{Decision, Pipeline, PorterNote, Program, Reason}
import balticporter.transform.{CollectionBoundaryCheck, CollectionsTransform}

/** A MEMBER THAT OVERRIDES A CLASS FILE keeps its formals — `CLAUDE.md` §4.56 read at an OVERRIDE
  * rather than at a call.
  *
  * ==What this pins, and why each half needs a fixture==
  * `class Holder extends java.util.AbstractSet<String>` declares `containsAll(Collection<?>)` and
  * opens it with `super.containsAll(c)`. `java.util.AbstractSet` is a JDK type the mapping does NOT
  * cover, so its `containsAll` still takes a `java.util.Collection` however the port retypes its
  * own; retyped, the emitted member overrides nothing and its own `super` call cannot compile.
  *
  * The NEGATIVES are the whole of the difficulty and each one is a measurement:
  *
  *   - a class extending a MAPPED collection (`extends java.util.ArrayList`) emits the SHIM as its
  *     parent, so its overrides belong in shim shape and must move. Held back, they would break in
  *     the other direction — this is the case that decides correctness on every port that is not
  *     this one;
  *   - a member overriding an interface THIS PROGRAM DECLARES must move, with its interface. Asked
  *     through `OverrideGraph.Closure.externalAnchors` instead of `overridden`, this failed: 104
  *     members were held on ssg-md over `java.util.function.Function#getAfterDependents`, because
  *     `ExternalSurface.mayDeclare` answers YES for an unparsed type on purpose (69 → 113);
  *   - a member that overrides NOTHING is not a candidate at all, whatever its signature says.
  *
  * And the CLASSIFICATION is asserted here rather than left to prose: the seam this refusal creates
  * is `Issue.ClassFileOverride` and NOT `Issue.ScopedOut`, whose sentence tells its reader to widen
  * a `CollectionsTransform(scope)` that has nothing to do with it and that no port can write for a
  * java class file.
  */
class CollectionsClassFileOverrideSpec extends PortSuite:

  /** `Holder` is the positive; `Fast` is the mapped-parent negative; `Ours`/`Impl` are the
    * program-declared-ancestor negative; `Holder#absorb` is the not-an-override negative. */
  private val src =
    """package demo;
      |import java.util.*;
      |public class Holder extends java.util.AbstractSet<String> {
      |  private final List<String> backing = new ArrayList<String>();
      |  public boolean containsAll(Collection<?> c) { return super.containsAll(c); }
      |  public Iterator<String> iterator() { return backing.iterator(); }
      |  public int size() { return backing.size(); }
      |  public void absorb(Collection<String> more) { backing.addAll(more); }
      |}
      |class Fast extends java.util.ArrayList<String> {
      |  public boolean addAll(Collection<? extends String> c) { return super.addAll(c); }
      |}
      |interface Ours { List<String> names(); }
      |class Impl implements Ours { public List<String> names() { return new ArrayList<String>(); } }
      |class Caller {
      |  boolean ask(Holder h, List<String> mine) { return h.containsAll(mine); }
      |}
      |""".stripMargin

  private def ported: (CollectionsTransform, Program, String) =
    val ph    = new CollectionsTransform()
    val after = Pipeline.run(SpoonTir.fromSource(src), List(ph))
    (ph, after, new TirEmitter(after).emit)

  private def heldNames(ph: CollectionsTransform, p: Program): Set[String] =
    ph.classFileOverrides.flatMap(p.symbolOf).map(_.fullName)

  // -------------------------------------------------------------------------
  // the positive
  // -------------------------------------------------------------------------

  test("a member overriding an UNMAPPED java parent keeps java's formal") {
    val (ph, p, out) = ported
    assert(clue(heldNames(ph, p)).exists(_.endsWith("Holder#containsAll")),
           "Holder#containsAll overrides java.util.AbstractSet's and must be held literally")
    assert(out.contains("def containsAll(c: java.util.Collection[?])"),
           s"the emitted formal is not java's\n--- emitted ---\n$out")
  }

  test("…and the refusal is a RECORDED decision, universal, naming what it overrides") {
    val (ph, p, _) = ported
    val log = Pipeline.runTraced(SpoonTir.fromSource(src), List(new CollectionsTransform()))._2
    val ds  = log.of(Decision.Kind.RetainedSignature).filter(_.subjectFqn.endsWith("Holder#containsAll"))
    assertEquals(clue(ds).size, 1)
    assert(ds.head.reason.isInstanceOf[Reason.Universal], clue(ds.head.reason).toString)
    assertEquals(PorterNote.pairs(ds.head).toMap.get("overrides"), Some("java.util.AbstractSet#containsAll"))
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
