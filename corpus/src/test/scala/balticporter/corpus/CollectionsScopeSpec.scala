package balticporter.corpus

import balticporter.core.PolicyIssue
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.PortSuite
import balticporter.tir.{Decision, Pipeline, Program, Reason, RuleScope}
import balticporter.transform.{CollectionBoundaryCheck, CollectionsTransform}

/** WHERE `CollectionsTransform` applies — its [[RuleScope]], in both directions.
  *
  * Four things are asserted here and nowhere else, in the order they matter:
  *
  *   1. the DEFAULT is a no-op — byte-for-byte the same emitted Scala as the phase produced before
  *      it took a scope at all. That is §1(b)'s requirement stated as a test rather than as prose;
  *      the measure lanes assert the same property over 600 files and this asserts it in a second.
  *   2. an excluded declaration KEEPS its JDK type, and the separator cut holds in the exclusion
  *      direction too — `demo.Bridge` must not carry `demo.BridgeHelper` out with it (§4.56).
  *   3. `Only` PROPAGATES: naming a field brings its getter, because a signature that moves without
  *      its call sites is a compile error one call away.
  *   4. every seam the scope creates is REPORTED. This is the one that makes the knob safe to ship:
  *      no wrap can bridge a `Buffer` into a `java.util.List` slot, so a scope whose boundaries were
  *      silent would be a feature for emitting code that does not compile, with nothing to say why.
  */
class CollectionsScopeSpec extends PortSuite:

  private val src =
    """package demo;
      |import java.util.*;
      |class Model {
      |  private List<String> items = new ArrayList<String>();
      |  private List<String> legacy = new ArrayList<String>();
      |  public List<String> getItems() { return items; }
      |  public List<String> getLegacy() { return legacy; }
      |  public void take(List<String> more) { items.addAll(more); }
      |}
      |class Client {
      |  void feed(Model m) { m.take(m.getItems()); }
      |  void reuse(Model m) { m.take(m.getLegacy()); }
      |}
      |class Bridge {
      |  private List<String> raw = new ArrayList<String>();
      |  public List<String> getRaw() { return raw; }
      |}
      |class BridgeHelper {
      |  private List<String> helped = new ArrayList<String>();
      |}
      |""".stripMargin

  private def ported(scope: RuleScope): (CollectionsTransform, Program, String) =
    val ph    = new CollectionsTransform(scope)
    val after = Pipeline.run(SpoonTir.fromSource(src), List(ph))
    (ph, after, new TirEmitter(after).emit)

  private val Buffer = "scala.collection.mutable.Buffer[java.lang.String]"
  private val JList  = "java.util.List[java.lang.String]"

  // -------------------------------------------------------------------------
  // 1. the default is a no-op
  // -------------------------------------------------------------------------

  test("the DEFAULT scope emits byte-for-byte what the unparameterised phase emitted") {
    val (ph, _, withDefault) = ported(RuleScope.Everywhere())
    val bare = new TirEmitter(Pipeline.run(SpoonTir.fromSource(src), List(new CollectionsTransform))).emit
    assertEquals(withDefault, bare)
    assertEquals(ph.scopedOut, Set.empty)
    assert(ph.policyReport.isEmpty)
    assert(withDefault.contains(Buffer))
    assert(!withDefault.contains("java.util.List"))
  }

  test("a scope whose entries match NOTHING is the same no-op — the excluded set is empty by arithmetic") {
    val (ph, _, out) = ported(RuleScope.Everywhere(Set("demo.NoSuchType")))
    val bare = new TirEmitter(Pipeline.run(SpoonTir.fromSource(src), List(new CollectionsTransform))).emit
    assertEquals(out, bare)
    assertEquals(ph.scopedOut, Set.empty)
  }

  // -------------------------------------------------------------------------
  // 2. Everywhere(except)
  // -------------------------------------------------------------------------

  test("an excluded TYPE keeps its JDK collection while the rest of the port moves") {
    val (_, _, out) = ported(RuleScope.Everywhere(Set("demo.Bridge")))
    assert(clue(out).contains(s"var raw: $JList"), "the excluded field kept java.util.List")
    assert(out.contains("new java.util.ArrayList["), "…and its initialiser was not rewritten either")
    assert(out.contains(s"var items: $Buffer"), "the rest of the port still moved")
  }

  test("the SEPARATOR CUT holds in the exclusion direction — demo.Bridge does not carry demo.BridgeHelper") {
    val (_, _, out) = ported(RuleScope.Everywhere(Set("demo.Bridge")))
    assert(clue(out).contains(s"var helped: $Buffer"), "BridgeHelper is a different type and is in scope")
  }

  test("a MEMBER entry excludes one field and leaves its siblings alone") {
    val (_, _, out) = ported(RuleScope.Everywhere(Set("demo.Model#legacy")))
    assert(clue(out).contains(s"var legacy: $JList"))
    assert(out.contains(s"var items: $Buffer"))
  }

  test("an excluded METHOD keeps its signature AND its body — half a rewritten body is not a translation") {
    val (_, _, out) = ported(RuleScope.Everywhere(Set("demo.Model#take")))
    assert(clue(out).contains(s"def take(more: $JList)"))
    assert(out.contains("this.items.addAll(more)"), "the body kept java's call shape, not `++=`")
  }

  // -------------------------------------------------------------------------
  // 3. Only(include), and propagation
  // -------------------------------------------------------------------------

  test("Only(type) rewrites that type and NOTHING else") {
    val (_, _, out) = ported(RuleScope.Only(Set("demo.Model")))
    assert(clue(out).contains(s"var items: $Buffer"))
    assert(out.contains(s"var raw: $JList"), "Bridge was never named and is left whole")
    assert(out.contains(s"var helped: $JList"))
  }

  test("Only(field) PROPAGATES to the getter that returns it — the call sites follow the declaration") {
    val (_, _, out) = ported(RuleScope.Only(Set("demo.Model#items")))
    assert(clue(out).contains(s"var items: $Buffer"))
    assert(out.contains(s"def getItems(): $Buffer"), "`return items` is a pure move, so the getter follows")
    // …and it follows TRANSITIVELY, across types: `Client.feed` passes `getItems()` to `take`, so
    // `take`'s parameter joins — and `Client.reuse` passes `getLegacy()` to the same parameter, so
    // `legacy` joins through it. That chain is what a scope of one field would otherwise leave as
    // four compile errors, and it is why an opt-in is a SEED rather than a list.
    assert(out.contains(s"def take(more: $Buffer)"))
    assert(out.contains(s"var legacy: $Buffer"), "reached through take's parameter, transitively")
    // …while a declaration no flow connects is untouched, whatever its type.
    assert(out.contains(s"var raw: $JList"))
    assert(out.contains(s"var helped: $JList"))
  }

  test("Only(Set.empty) rewrites nothing at all — the honest reading of 'only these' of nothing") {
    val (_, _, out) = ported(RuleScope.Only(Set.empty))
    assert(!clue(out).contains("scala.collection.mutable"))
    assert(out.contains(s"var items: $JList"))
  }

  // -------------------------------------------------------------------------
  // 4. every seam the scope creates is REPORTED
  // -------------------------------------------------------------------------

  test("a seam the scope creates is a ScopedOut finding, classified §1(b) and told which entry to move") {
    // `Model.take(List)` is excluded, so its formal stays `java.util.List`, while `Client.feed`
    // hands it `m.getItems()` — which moved. NOTHING can wrap that, so it must be counted.
    val (ph, after, _) = ported(RuleScope.Everywhere(Set("demo.Model#take")))
    val scoped = ph.boundary(after).filter(_.issue == CollectionBoundaryCheck.Issue.ScopedOut)
    assert(clue(scoped).nonEmpty, "the scope opened a slot; the check must see it AND blame the scope")
    assert(scoped.exists(f => f.expected == "java.util.List" && f.actual.startsWith("scala.collection.")))
    assert(CollectionBoundaryCheck.Issue.classification(CollectionBoundaryCheck.Issue.ScopedOut).contains("§1(b)"))
  }

  test("a reference to a scoped-out DECLARATION is seen through the DECLARATION, not through the node") {
    // `Client.reuse` passes `m.getLegacy()` — scoped out, so it still returns `java.util.List` — to
    // `Model.take`, whose formal moved. The `Apply` node's own `tpe` was nevertheless remapped by
    // the position-blind `transformType`, so a check reading it compares `Buffer` against `Buffer`
    // and reports ZERO: the one seam a scope is guaranteed to create would be the one seam
    // invisible to the check written to find it. `actualOf` reads the declaration instead.
    val (ph, after, _) = ported(RuleScope.Everywhere(Set("demo.Model#getLegacy")))
    val scoped = ph.boundary(after).filter(_.issue == CollectionBoundaryCheck.Issue.ScopedOut)
    assert(clue(scoped).exists(f => f.actual == "java.util.List" && f.expected.startsWith("scala.collection.")))
  }

  test("the DEFAULT scope reports no ScopedOut finding — a check that fires when it should not is worse") {
    val (ph, after, _) = ported(RuleScope.Everywhere())
    assertEquals(ph.boundary(after).count(_.issue == CollectionBoundaryCheck.Issue.ScopedOut), 0)
  }

  // -------------------------------------------------------------------------
  // policy report and decision provenance
  // -------------------------------------------------------------------------

  test("an entry that named nothing is a §1(b) NeverMatched finding — a silent no-op policy is the failure") {
    val (ph, _, _) = ported(RuleScope.Everywhere(Set("demo.Bridge", "demo.Typo")))
    val fs = ph.policyReport.of(PolicyIssue.NeverMatched)
    assertEquals(fs.map(_.key), List("demo.Typo"))
    assert(clue(fs.head.render).contains("§1(b)"))
  }

  test("a held-back declaration leaves a ScopedOut row naming the entry VERBATIM (§4.575)") {
    val ph  = new CollectionsTransform(RuleScope.Everywhere(Set("demo.Bridge")))
    val log = Pipeline.runTraced(SpoonTir.fromSource(src), List(ph))._2
    val ds  = log.of(Decision.Kind.ScopedOut)
    assert(clue(ds.map(_.subjectFqn)).contains("demo.Bridge#raw"))
    assert(ds.forall(_.reason == Reason.Configured("java-collections->scala", "demo.Bridge")))
    assertEquals(ds.head.reason.section, "§1(b) PER-LIBRARY POLICY")
    assert(ds.forall(_.detail("key") == "demo.Bridge"))
    // …and nothing outside the entry is claimed by it.
    assert(!ds.map(_.subjectFqn).exists(_.startsWith("demo.Model")))
  }

  test("under Only, a retyped declaration's row is Configured — the port ASKED for this one") {
    val ph  = new CollectionsTransform(RuleScope.Only(Set("demo.Model")))
    val log = Pipeline.runTraced(SpoonTir.fromSource(src), List(ph))._2
    val ds  = log.of(Decision.Kind.RetypedSignature).filter(_.subjectFqn.startsWith("demo.Model"))
    assert(clue(ds).nonEmpty)
    assert(ds.forall(_.reason == Reason.Configured("java-collections->scala", "demo.Model")))
  }

  test("under the DEFAULT scope every retyping row stays Universal — nobody asked, java simply has no counterpart") {
    val ph  = new CollectionsTransform()
    val log = Pipeline.runTraced(SpoonTir.fromSource(src), List(ph))._2
    val ds  = log.of(Decision.Kind.RetypedSignature)
    assert(clue(ds).nonEmpty)
    assert(ds.forall(_.reason == Reason.Universal("collections-retype")))
    assertEquals(log.of(Decision.Kind.ScopedOut), Nil)
  }

  test("the surface fingerprint separates two differently-scoped modules and is empty by default") {
    assertEquals(new CollectionsTransform().surfaceFingerprint, "")
    assertNotEquals(
      new CollectionsTransform(RuleScope.Only(Set("demo.Model"))).surfaceFingerprint,
      new CollectionsTransform(RuleScope.Everywhere(Set("demo.Model"))).surfaceFingerprint,
    )
  }
