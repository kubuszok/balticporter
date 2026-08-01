package balticporter.corpus

import balticporter.core.PolicyIssue
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{Phase, Pipeline, Program}
import balticporter.transform.{CallSiteSubstitutionTransform, CollectionsTransform, RetargetBoundaryCheck}

/** `java.util.Comparator` → `scala.math.Ordering`, END TO END — the first RETARGET entry, and the
  * first real input to the call-site seam.
  *
  * ==Why a retarget and not a `typeMap` row==
  * Scala declares `trait Ordering[T] extends java.util.Comparator[T]`. Everything follows from that
  * one fact, and it is what makes this a retarget rather than a collection mapping:
  *
  *   - a declaration moves with NO bridge — an `Ordering` reaching a slot that still says
  *     `Comparator` already IS one, so `coerce` has nothing to do and no boundary is created;
  *   - `implements Comparator<T>` becomes `extends Ordering[T]` and the `compare(a, b)` under it is
  *     structurally unchanged, because `compare` is `Ordering`'s ONE abstract member (which is also
  *     what keeps a java lambda SAM-convertible);
  *   - every call site keeps type-checking with no rewrite at all, including the engine's own
  *     `JavaCollections.sort(xs, cmp)` — its parameter is a `java.util.Comparator`, and an
  *     `Ordering` is one.
  *
  * That last point is this policy's whole scope, and it is the measured answer to "does the sort
  * call-site table ride the M4 mechanism or extend the statics table": after the retarget, NEITHER.
  * The shape `sortInPlace()(using ord)` is expressible in the template language and does not
  * type-check against `mutable.Buffer`, which is the phase's own target for `java.util.List` — see
  * the two tests at the bottom, which carry the compiler's words.
  *
  * Both halves are DEFAULT-OFF: an empty `retarget` and an empty `calls` map, and every corpus lane
  * is byte-identical without them.
  *
  * {{{ scala-cli compile --scala 3.8.4 --server=false <the directory printed below> }}}
  */
class ComparatorOrderingPortSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |import java.util.ArrayList;
      |import java.util.Collections;
      |import java.util.Comparator;
      |import java.util.List;
      |
      |class ByLength implements Comparator<String> {
      |  public int compare(String a, String b) { return a.length() - b.length(); }
      |}
      |
      |class Sorter {
      |  Comparator<String> cmp = new ByLength();
      |  List<String> items = new ArrayList<String>();
      |
      |  void sortItems() { Collections.sort(items, cmp); }
      |  int use(String a, String b) { return cmp.compare(a, b); }
      |  void setCmp(Comparator<String> c) { this.cmp = c; }
      |}
      |""".stripMargin

  /** kept APART from `src` so the probe below compiles green: this snippet carries a pre-existing
    * engine gap that has nothing to do with the retarget. See its own test. */
  private val arraySrc =
    """package demo;
      |import java.util.Arrays;
      |import java.util.Comparator;
      |class ArraySorter {
      |  void sortArray(String[] xs, Comparator<String> cmp) { Arrays.sort(xs, cmp); }
      |}
      |""".stripMargin

  /** the policy, exactly as a port's manifest would carry it. */
  private val Retarget = Map("java.util.Comparator" -> "scala.math.Ordering")

  private def ported(phases: List[Phase], source: String = src) =
    val after = Pipeline.run(SpoonTir.fromSource(source), phases)
    (after, new TirEmitter(after).emit)

  private def emit(phases: List[Phase], source: String = src) = ported(phases, source)._2

  // -------------------------------------------------------------------------
  // the retarget alone
  // -------------------------------------------------------------------------

  test("every Comparator OCCURRENCE moves — field, parameter, parent — and nothing is bridged") {
    val phase = new CollectionsTransform(retarget = Retarget)
    val out   = emit(List(phase))
    assert(clue(out).contains("scala.math.Ordering[java.lang.String]"))
    assert(!out.contains("java.util.Comparator"))
    assertEquals(phase.policyReport.findings, Nil)
    // the PARENT moved with the rest, and the method under it is structurally untouched: `compare`
    // is `Ordering`'s one abstract member, so nothing is invented and nothing is dropped.
    assert(clue(out).contains("extends scala.math.Ordering[java.lang.String]"))
    assert(clue(out).contains("def compare(a: java.lang.String, b: java.lang.String): scala.Int"))
  }

  test("a retarget creates NO boundary — a retyped value reaches a Comparator slot BARE") {
    // The property that distinguishes a retarget from a collection mapping. The receiving slot here
    // is the engine's own `JavaCollections.sort`, whose second parameter is a real
    // `java.util.Comparator` — and an `Ordering` is one, so there is no wrapper, no factory and
    // nothing for `CollectionBoundaryCheck` to count.
    val phase        = new CollectionsTransform(retarget = Retarget)
    val (after, out) = ported(List(phase))
    assert(clue(out).contains("balticporter.runtime.JavaCollections.sort(this.items, this.cmp)"))
    assertEquals(phase.boundary(after), Nil)
  }

  test("an empty retarget is a TOTAL no-op — byte-identical to the phase without one") {
    assertEquals(emit(List(new CollectionsTransform())),
                 emit(List(new CollectionsTransform(retarget = Map.empty))))
  }

  test("a retarget key the COLLECTION mapping already answers is refused, never merged") {
    // Two answers for one type is a rewrite whose outcome depends on which table was read.
    val phase = new CollectionsTransform(retarget = Map("java.util.List" -> "scala.List"))
    val out   = emit(List(phase))
    assertEquals(phase.policyReport.findings.map(_.issue), List(PolicyIssue.Malformed))
    assert(clue(out).contains("scala.collection.mutable.Buffer"))  // the mapping stands
    assert(!out.contains("scala.List["))
  }

  test("a retarget key naming nothing in this program is reported, not silently inert") {
    val phase = new CollectionsTransform(retarget = Map("java.util.Nosuch" -> "scala.math.Ordering"))
    emit(List(phase))
    assertEquals(phase.policyReport.findings.map(_.issue), List(PolicyIssue.NeverMatched))
  }

  test("the retarget is part of the SURFACE fingerprint — a base and a dependent must agree") {
    val plain = new CollectionsTransform()
    val with_ = new CollectionsTransform(retarget = Retarget)
    assertNotEquals(plain.surfaceFingerprint, with_.surfaceFingerprint)
    // …and a port that declares none has the string it always had, so no baseline moves
    assertEquals(plain.surfaceFingerprint, new CollectionsTransform(retarget = Map.empty).surfaceFingerprint)
  }

  test("a retyped declaration is attributed to the ENTRY, never to the engine") {
    // §4.45: the reader's first question is which repository the fix lives in, and a retarget is a
    // line in their manifest. Reported as `Universal` it would send them to `CollectionsTransform`.
    val phase        = new CollectionsTransform(retarget = Retarget)
    val (_, log)     = Pipeline.runTraced(SpoonTir.fromSource(src), List(phase))
    val rows         = log.all.filter(_.detail.get("to").exists(_.contains("Ordering")))
    assert(clue(rows).nonEmpty)
    assert(rows.forall(_.reason.className == "configured"))
    assert(rows.forall(_.reason.detail.contains("java.util.Comparator -> scala.math.Ordering")))
  }

  // -------------------------------------------------------------------------
  // the call-site half — measured, and the reason it ships EMPTY
  // -------------------------------------------------------------------------

  test("the template language CAN express a `using` argument — that half of the question is yes") {
    // Rewriting `Collections.sort` is a legibility decision, so if it is made at all it is a
    // per-library entry in the call-site table and never an arm in the engine's universal statics
    // table. The mechanism needs nothing new for it: a `using` clause is ordinary text around a
    // hole, and the entry must be placed BEFORE `CollectionsTransform`, whose statics arm re-points
    // the same callee.
    val m4  = new CallSiteSubstitutionTransform(Map(
      "java.util.Collections#sort(List,Comparator)" -> "{arg0}.sortInPlace()(using {arg1})"))
    val out = emit(List(m4, new CollectionsTransform(retarget = Retarget)))
    assert(clue(out).contains("this.items.sortInPlace()(using this.cmp)"))
    assertEquals(m4.substituted, List("java.util.Collections#sort(List,Comparator)" -> 1))
    assertEquals(m4.policyReport.findings, Nil)
    // …and the receiver is a HOLE, so the collections phase running AFTERWARDS still retyped it
    assert(clue(out).contains("scala.collection.mutable.Buffer[java.lang.String]"))
  }

  test("…and that shape is REFUTED by the compiler, which is why this policy ships no entry") {
    // MEASURED, `scala-cli compile --scala 3.8.4`:
    //
    //   value sortInPlace is not a member of scala.collection.mutable.Buffer[String]
    //
    // `sortInPlace` is a `mutable.IndexedSeqOps` member and `java.util.List` maps to
    // `mutable.Buffer`, which is not one — so the idiomatic shape does not type-check against the
    // phase's OWN target. Nothing in the emitted text says so, no count moves, and the substitution
    // is performed exactly as asked: the seam did its job and the POLICY is wrong. Recorded here as
    // the test that would start failing the day the mapping or the stdlib changes.
    //
    // The correct target is the one already there: after the retarget, `JavaCollections.sort`
    // accepts an `Ordering` unchanged (`Ordering[T] <: Comparator[T]`) and compiles.
    assert(clue(emit(List(new CollectionsTransform(retarget = Retarget))))
      .contains("balticporter.runtime.JavaCollections.sort("))
  }

  test("`Arrays.sort`'s erasure cast is PRE-EXISTING — this policy neither causes nor fixes it") {
    // MEASURED, both with and without the retarget:
    //
    //   Found: java.util.Comparator[String] / Required: java.util.Comparator[? >: Object]
    //
    // The frontend renders the java call against the erased `sort(Object[], Comparator)`, so the
    // ARRAY argument carries an `asInstanceOf` while the comparator keeps its element type. Reading
    // that as something the retarget did would send the next agent to the wrong file, which is what
    // §4.45 costs an investigation for — so it is asserted identical on both sides.
    //
    // It is also why this policy ships no `Arrays.sort` entry even as a legibility rewrite: the
    // idiomatic counterpart is `scala.util.Sorting.stableSort`, whose using clause takes a
    // `ClassTag` as well as the `Ordering`, and a positional template cannot name a SUMMONED
    // argument; `quickSort` has the one using parameter and is NOT stable, where java's
    // `Arrays.sort` is guaranteed to be. Trading a documented guarantee for legibility is not a
    // trade a seam may make silently (CLAUDE.md §4.4's whole family).
    val plain      = emit(Nil, arraySrc)
    val retargeted = emit(List(new CollectionsTransform(retarget = Retarget)), arraySrc)
    assert(clue(plain).contains("xs.asInstanceOf[scala.Array[java.lang.Object]]"))
    assert(clue(retargeted).contains("xs.asInstanceOf[scala.Array[java.lang.Object]]"))
  }

  // -------------------------------------------------------------------------
  // the PRODUCER direction — the half the subtyping argument does not license
  // -------------------------------------------------------------------------

  /** every shape in which the JDK HANDS BACK a `Comparator`. None of these occurs in the corpus,
    * which is why the counter had to be written against a synthetic one: a residue nobody can
    * produce on demand is a residue nobody can prove is counted. */
  private val producerSrc =
    """package demo;
      |import java.util.Collections;
      |import java.util.Comparator;
      |class Producers {
      |  Comparator<String> fromJdk = Collections.reverseOrder();
      |  Comparator<String> natural = Comparator.naturalOrder();
      |  Comparator<String> insensitive = String.CASE_INSENSITIVE_ORDER;
      |  Comparator<String> viaCast(Object o) { return (Comparator<String>) o; }
      |}
      |""".stripMargin

  test("a JDK-PRODUCED Comparator reaching a retyped slot is COUNTED — every shape") {
    // The retarget's licence is one-directional: `Ordering[T] <: Comparator[T]` covers a retyped
    // value flowing INTO a `Comparator` slot, and says nothing about a `Comparator` the JDK returns
    // flowing into a slot this phase moved. The position-blind `transformType` has already retyped
    // the node, so BOTH sides of such a slot read `Ordering` and `CollectionBoundaryCheck` — which
    // compares node types, and which a retarget deliberately contributes nothing to — reports 0.
    val phase        = new CollectionsTransform(retarget = Retarget)
    val (after, out) = ported(List(phase), producerSrc)
    val fs           = phase.retargetBoundary(after)
    assertEquals(clue(fs).map(_.issue).distinct.sorted(Ordering.by(_.toString)),
                 List(RetargetBoundaryCheck.Issue.CastToTarget,
                      RetargetBoundaryCheck.Issue.ExternalProducer,
                      RetargetBoundaryCheck.Issue.StaticReceiver))
    // the three producers the JDK owns, plus the cast
    assertEquals(fs.count(_.issue == RetargetBoundaryCheck.Issue.ExternalProducer), 3)
    assertEquals(fs.count(_.issue == RetargetBoundaryCheck.Issue.StaticReceiver), 1)
    assertEquals(fs.count(_.issue == RetargetBoundaryCheck.Issue.CastToTarget), 1)
    // …and every one of them says which of §1's three kinds the fix is (§4.45)
    assert(fs.forall(f => RetargetBoundaryCheck.Issue.classification(f.issue).contains("§1")))
    // the emitted text is the evidence that none of this is a compile error the port would see:
    // the declaration moved and the producer did not.
    assert(clue(out).contains("scala.math.Ordering[java.lang.String]"))
    assert(clue(out).contains("java.util.Collections.reverseOrder"))
    assert(clue(phase.boundary(after)).isEmpty, "the check that exists reports ZERO on all of it")
  }

  test("…and it counts NOTHING on a program whose producers are its OWN — the corpus's shape") {
    // Every `Comparator` in the corpus is produced by code the port emits (a `new`, an owned field,
    // an owned method), and those move WITH their declarations. A counter that also fired on those
    // would report the retarget working correctly as a residue.
    val phase = new CollectionsTransform(retarget = Retarget)
    val after = Pipeline.run(SpoonTir.fromSource(src), List(phase))
    assertEquals(phase.retargetBoundary(after), Nil)
  }

  test("no retarget, no counter — an empty table is a no-op by arithmetic") {
    val phase = new CollectionsTransform()
    val after = Pipeline.run(SpoonTir.fromSource(producerSrc), List(phase))
    assertEquals(phase.retargetBoundary(after), Nil)
  }

  test("ORDER MATTERS, and getting it wrong is a FINDING rather than a silence") {
    // `CollectionsTransform`'s universal statics table already maps `Collections.sort` onto the
    // runtime helper. Placed after it, a call-site entry's callee occurs nowhere and it rewrites
    // nothing — with every count unchanged and the emitted code exactly what the port asked to
    // change. This is the §1(b) silent no-op in its most expensive form, so it has its own finding.
    val m4  = new CallSiteSubstitutionTransform(Map(
      "java.util.Collections#sort(List,Comparator)" -> "{arg0}.sortInPlace()(using {arg1})"))
    val out = emit(List(new CollectionsTransform(retarget = Retarget), m4))
    assertEquals(m4.substituted, Nil)
    val f = m4.policyReport.findings
    assertEquals(f.map(_.issue), List(PolicyIssue.NeverMatched))
    assert(clue(f.head.detail).contains("ran EARLIER"))
    assert(clue(out).contains("JavaCollections.sort"))
  }

  // -------------------------------------------------------------------------
  // the probe — one file per UNIT, which is what a port writes
  // -------------------------------------------------------------------------

  test("emitted probe is written for a real compiler") {
    // A string assertion is not evidence that `class ByLength extends Ordering[String]` and
    // `JavaCollections.sort(buffer, ordering)` type-check — that is a claim about scalac, and a
    // forked test JVM cannot be handed one (`BeanPropertyPortSpec` uses the same device). ONE FILE
    // PER UNIT, because `TirEmitter.emit` concatenates units for convenience and two `package`
    // clauses in one file is not a thing a port ever writes.
    val phase       = new CollectionsTransform(retarget = Retarget)
    val (after, _)  = ported(List(phase))
    val emitter     = new TirEmitter(after)
    val dir = _root_.java.nio.file.Path
      .of(sys.props.getOrElse("balticporter.dumpProbe", s"${sys.props("user.dir")}/target/probe"),
        "comparator-ordering")
    _root_.java.nio.file.Files.createDirectories(dir)
    after.units.zipWithIndex.foreach { (u, i) =>
      _root_.java.nio.file.Files.writeString(dir.resolve(s"Unit$i.scala"), emitter.emitUnit(u))
    }
    println(s"[comparator-ordering-probe] wrote ${after.units.size} unit(s) to ${dir.toAbsolutePath}")
    assert(after.units.nonEmpty)
  }
