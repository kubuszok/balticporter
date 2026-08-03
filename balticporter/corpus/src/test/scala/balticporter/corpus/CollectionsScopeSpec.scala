package balticporter.corpus

import balticporter.core.PolicyIssue
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.PortSuite
import balticporter.tir.{Decision, Pipeline, PorterNote, Program, Reason, RuleScope}
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
  *   4. every seam the scope creates is either BRIDGED or REPORTED. This is the one that makes the
  *      knob safe to ship. The CONSUMER direction is bridged — a retyped `Buffer` reaching a
  *      held-back `java.util.List` slot goes through `JavaCollections.toJava`, a live view — and
  *      the PRODUCER direction is not, because a value the held-back declaration hands back arrives
  *      already typed as java's. A scope whose remaining boundaries were silent would be a feature
  *      for emitting code that does not compile, with nothing to say why.
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

  /** The other half of a scope, and the one an audit had to execute to find: IN-SCOPE code that
    * REACHES a scoped-out declaration. `Client` moves; `Bridge`'s two fields do not, and every
    * mention of them in `Client` is a node whose `tpe` the position-blind `transformType` remapped
    * anyway. */
  private val callSrc =
    """package demo;
      |import java.util.*;
      |class Bridge {
      |  public List<String> raw = new ArrayList<String>();
      |  public Map<String, String> m = new HashMap<String, String>();
      |}
      |class Client {
      |  void push(Bridge b, List<String> mine) { b.raw.addAll(mine); }
      |  String read(Bridge b) { return b.m.get("k"); }
      |}
      |""".stripMargin

  private def ported(scope: RuleScope, source: String = src): (CollectionsTransform, Program, String) =
    val ph    = new CollectionsTransform(scope)
    val after = Pipeline.run(SpoonTir.fromSource(source), List(ph))
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

  test("a held-back FORMAL is bridged where the scope let a retyped value reach it") {
    // `Model.take(List)` is excluded, so its parameter stays `java.util.List`, while `Client.feed`
    // hands it `m.getItems()` — which moved. That is the CONSUMER direction and it has a live
    // wrapper, so §1(b)'s first obligation applies before its second: insert the coercion.
    val (ph, after, out) = ported(RuleScope.Everywhere(Set("demo.Model#take")))
    assert(clue(out).contains(s"def take(more: $JList)"), "the excluded formal kept its JDK type")
    assert(out.contains("m.take(balticporter.runtime.JavaCollections.toJava(m.getItems()))"))
    assertEquals(ph.boundary(after).count(_.issue == CollectionBoundaryCheck.Issue.ScopedOut), 0)
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

  test("a CALL on a scoped-out receiver keeps java's call shape — the rewrite reads the DECLARATION") {
    // The seam the node types alone cannot show, in the direction that EMITS BROKEN CODE rather
    // than merely under-counting: `b.raw`/`b.m` are excluded, so they stay `java.util.List` /
    // `java.util.Map` — but their reference nodes were remapped by the position-blind
    // `transformType`, and a rewrite keyed on the node's type therefore fired `++=` and
    // `getOrElse` against JDK receivers that have neither. Two compile errors produced BY the
    // scope that exists to protect those declarations.
    val (_, _, out) = ported(RuleScope.Everywhere(Set("demo.Bridge")), callSrc)
    assert(clue(out).contains("b.raw.addAll("), "java's method, against the JDK type it kept")
    assert(out.contains("""b.m.get("k")"""), "…and java's `Map.get`, not scala's `getOrElse`")
    assert(!out.contains("++="), "the scala-shaped rewrite must not reach a scoped-out receiver")
    assert(!out.contains("getOrElse"))
  }

  test("…and the INHERITED-KIND fallback does not reopen it — the declaring type is java's either way") {
    // The rewrite now falls back to the RESOLVED METHOD's declaring type when the receiver's own
    // type is not one this phase minted, which is what lets `this.get(k)` inside a class extending
    // `HashMap` translate. That fallback would rewrite a scoped-out receiver too, and for the worst
    // possible reason: `b.raw.addAll(mine)` resolves to `java.util.List#addAll` whatever the scope
    // said, so the fallback alone re-emits exactly the broken `++=` the test above pins as absent.
    // Suppressed on `actualOf`'s scoped flag, which reads `false` for every port that sets no scope.
    val (_, _, out) = ported(RuleScope.Everywhere(Set("demo.Bridge")), callSrc)
    assert(clue(out).contains("b.raw.addAll("))
    assert(!out.contains("b.raw ++="), "the declaring-type fallback must stop at a scoped-out receiver")
  }

  test("…and the seam that call leaves is BRIDGED — §1(b) asks for a wrap first, a count second") {
    // Refusing the rewrite is only half of §1(b)'s obligation: `Client.push` still hands its own
    // `Buffer` to the `java.util.List` slot `b.raw.addAll` kept. That USED to be uncloseable and
    // counted, on the reasoning that a `mutable.Buffer` is not a `java.util.List` — which is true
    // of the TYPE and false of the value, because `asJava` is a live view in both directions. The
    // formal became readable when the frontend started interning external members with their
    // `MethodType` (`ENGINE-LIMITS.md` K15), and §1(b) is explicit about the order: where a
    // coercion exists, insert it; only where none can, refuse and report.
    val (ph, after, out) = ported(RuleScope.Everywhere(Set("demo.Bridge")), callSrc)
    assert(clue(out).contains("b.raw.addAll(balticporter.runtime.JavaCollections.toJava(mine))"))
    assertEquals(ph.boundary(after).count(_.issue == CollectionBoundaryCheck.Issue.ScopedOut), 0,
                 "a bridged slot is not a residue — counting it would be a number nobody can act on")
  }

  test("…while the PRODUCER direction of the same scope is still COUNTED, and blames the scope") {
    // The half no wrap closes, and the reason the count above going to zero is not the check going
    // blind: `Model.getLegacy` is held back, so it HANDS BACK a `java.util.List` where the port's
    // own code expects a `Buffer`. Nothing at the call site can change what a declaration returns.
    val (ph, after, _) = ported(RuleScope.Everywhere(Set("demo.Model#getLegacy")))
    val scoped = ph.boundary(after).filter(_.issue == CollectionBoundaryCheck.Issue.ScopedOut)
    assert(clue(scoped).nonEmpty, "the scope opened a slot; the check must see it AND blame the scope")
    assert(clue(CollectionBoundaryCheck.Issue.classification(CollectionBoundaryCheck.Issue.ScopedOut))
             .contains("PRODUCES"))
  }

  test("the same source under the DEFAULT scope is byte-for-byte the unscoped port, and counts zero") {
    val (ph, after, out) = ported(RuleScope.Everywhere(), callSrc)
    val bare = new TirEmitter(Pipeline.run(SpoonTir.fromSource(callSrc), List(new CollectionsTransform))).emit
    assertEquals(out, bare)
    assert(clue(out).contains("++="), "with nothing excluded the rewrite still fires everywhere")
    assertEquals(ph.boundary(after).count(_.issue == CollectionBoundaryCheck.Issue.ScopedOut), 0)
  }

  test("the DEFAULT scope reports no ScopedOut finding — a check that fires when it should not is worse") {
    val (ph, after, _) = ported(RuleScope.Everywhere())
    assertEquals(ph.boundary(after).count(_.issue == CollectionBoundaryCheck.Issue.ScopedOut), 0)
  }

  // -------------------------------------------------------------------------
  // policy report and decision provenance
  // -------------------------------------------------------------------------

  test("an entry naming a JDK TYPE fires on the interned EXTERNAL, does nothing, and must be REPORTED") {
    // `java.util.List` is in the symbol table — the frontend interned it on first reference — so the
    // entry matched, was counted as fired, and produced output byte-identical to the unscoped port.
    // A knob that reads as configured and does nothing is the §1(b) silent no-op exactly; ownership
    // is structural (`Program.owned`), and the report says which knob the author actually wants.
    val (ph, _, out) = ported(RuleScope.Everywhere(Set("java.util.List")))
    val bare = new TirEmitter(Pipeline.run(SpoonTir.fromSource(src), List(new CollectionsTransform))).emit
    assertEquals(out, bare, "an entry that names no DECLARATION cannot change the emitted port")
    assertEquals(ph.scopedOut, Set.empty)
    val fs = ph.policyReport.of(PolicyIssue.NeverMatched)
    assertEquals(fs.map(_.key), List("java.util.List"))
    assert(clue(fs.head.render).contains("THIS PROGRAM DOES NOT DECLARE"))
    assert(fs.head.render.contains("MAPPING, not the scope"), "…and which knob to reach for instead")
  }

  test("the same holds for Only — a JDK entry admits nothing, and says so") {
    val (ph, _, out) = ported(RuleScope.Only(Set("java.util.List")))
    val nothing = new TirEmitter(Pipeline.run(SpoonTir.fromSource(src), List(new CollectionsTransform(RuleScope.Only(Set.empty))))).emit
    assertEquals(out, nothing, "nothing was admitted, so this is `Only(Set.empty)`")
    assertEquals(ph.policyReport.of(PolicyIssue.NeverMatched).map(_.key), List("java.util.List"))
  }

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
    // …ONCE. The entry lives in `Reason.Configured` and nowhere else: a decider that also puts it
    // in `detail` renders `key=demo.Bridge key=demo.Bridge` in the porter note beside the code.
    assert(ds.forall(!_.detail.contains("key")))
    assertEquals(PorterNote.pairs(ds.head).count(_._1 == "key"), 1)
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
