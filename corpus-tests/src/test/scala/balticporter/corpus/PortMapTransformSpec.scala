package balticporter.corpus

import balticporter.core.{FrontendConfig, PolicyIssue, PortMap}
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{Pipeline, Program}
import balticporter.transform.PortMapTransform

import java.nio.file.{Files, Path}

/** Mechanical call migration for a DEPENDENT, from the base's published map.
  *
  * The acceptance case is the one `PORT-MAP-DESIGN.md` names: an entity-component library's
  * `ImmutableArray.toArray(Class)` is a one-line forwarder to the collection library's
  * `Array.toArray(Class)`, which the base drops because it is reflective. Today that surfaces as
  * `RewriteTrace`'s orphaned-call finding AFTER emission, saying only that a member has no
  * declaration. Against a published map it is a lookup, answerable before translation, and the
  * message names the module that dropped it.
  *
  * The shapes below are the real ones — the forwarder's body, the base's two `toArray` overloads,
  * the base's own manifest key — and where the base's map has been published in this checkout the
  * test reads THAT file rather than a fabricated one, so the artifact and the consumer are pinned
  * against each other and not merely against a shared assumption.
  */
class PortMapTransformSpec extends munit.FunSuite:

  // -------------------------------------------------------------------------
  // a two-module Java tree: `base/` is resolved against, `dep/` is converted
  // -------------------------------------------------------------------------

  private def tree(base: Map[String, String], dep: Map[String, String]): (Path, Path, List[String]) =
    val root = Files.createTempDirectory("portmap-xform")
    def put(under: Path, files: Map[String, String]) = files.foreach { (rel, body) =>
      val p = under.resolve(rel)
      Files.createDirectories(p.getParent)
      Files.writeString(p, body)
    }
    put(root.resolve("base"), base)
    put(root.resolve("dep"), dep)
    (root.resolve("base"), root.resolve("dep"), dep.keys.toList.sorted)

  private def model(base: Map[String, String], dep: Map[String, String]): Program =
    val (baseRoot, depRoot, files) = tree(base, dep)
    val types = SpoonTir.buildModel(
      FrontendConfig(depRoot, files, Nil, resolutionRoots = List(baseRoot)), lenient = true)
    SpoonTir.fromTypes(types)

  private def run(p: Program, maps: List[PortMap.Map0]) =
    val phase = new PortMapTransform(maps)
    val out   = Pipeline.run(p, List(phase))
    (phase, out)

  /** [[run]], keeping the DECISION LOG. `Pipeline.run` drains each phase's buffer into a log it
    * then discards, so a spec that read `phase.decisions` afterwards would assert on an empty
    * one — which is the point of the drain (a phase instance reused across two translations must
    * not report the first run's decisions as the second's). */
  private def runTraced(p: Program, maps: List[PortMap.Map0]) =
    val phase      = new PortMapTransform(maps)
    val (out, log) = Pipeline.runTraced(p, List(phase))
    (phase, out, log)

  // -------------------------------------------------------------------------
  // the acceptance case
  // -------------------------------------------------------------------------

  /** `com.badlogic.gdx.utils.Array`, reduced to the two `toArray` overloads that matter: the
    * reflective one the base drops and the portable one beside it, which is what makes the choice
    * between them a real decision rather than a formality. */
  private val baseArray = Map(
    "com/badlogic/gdx/utils/Array.java" ->
      """package com.badlogic.gdx.utils;
        |public class Array<T> {
        |  public Object[] toArray() { return null; }
        |  public <V> V[] toArray(Class<V> type) { return null; }
        |}
        |""".stripMargin,
  )

  /** Ashley's `ImmutableArray`, reduced to the forwarder pair from `ImmutableArray.java:73-79`. */
  private val dependent = Map(
    "com/badlogic/ashley/utils/ImmutableArray.java" ->
      """package com.badlogic.ashley.utils;
        |import com.badlogic.gdx.utils.Array;
        |public class ImmutableArray<T> {
        |  private final Array<T> array = null;
        |  public Object[] toArray() { return array.toArray(); }
        |  public <V> V[] toArray(Class<V> type) { return array.toArray(type); }
        |}
        |""".stripMargin,
  )

  /** The base's map AS PUBLISHED in this checkout, if a run has produced one; otherwise the two
    * rows the published one is known to contain, so the test states the same thing either way and
    * a fresh clone that has never run a migration still runs it. Which source was used is printed,
    * because a test that silently degrades to a fixture proves less than it looks like it does. */
  private def baseMap: PortMap.Map0 =
    val published = List("run-latest", "baseline").iterator
      .map(d => Path.of("port-report/LibgdxCoreMigrate", d, "port-map.tsv"))
      .filter(Files.isRegularFile(_))
      .flatMap(p => PortMap.read(p).toOption)
      .find(_.byUpstream("member").contains("com.badlogic.gdx.utils.Array#toArray(Class)"))
    published match
      case Some(m) => m
      case scala.None =>
        PortMap.of("libgdx-core", "eng", List("com.badlogic.gdx.utils.Array"),
          balticporter.tir.SrcMap.Recording(List(balticporter.tir.SrcMap.Entry(
            "com.badlogic.gdx.utils.Array", "com.badlogic.gdx.utils.Array#toArray()", "def", 1, 2,
            "com/badlogic/gdx/utils/Array.java", 589, "d0"))),
          dropTypes = Set.empty,
          dropMethods = Set("com.badlogic.gdx.utils.Array#toArray(Class)"),
          injectedFqns = Set.empty, bodyKeys = Set.empty, renames = Map.empty)

  test("ACCEPTANCE: a forwarder into a member the base DROPPED is reported before emission") {
    val (phase, _) = run(model(baseArray, dependent), List(baseMap))
    val dropped = phase.findings.filter(_.issue == PortMapTransform.Issue.DroppedMember)

    assertEquals(clue(dropped).size, 1)
    val f = dropped.head
    // the three things the message has to carry, and that `RewriteTrace`'s orphaned-call finding
    // cannot: WHICH member, WHICH module decided, and WHAT it decided.
    assertEquals(f.symbol, "com.badlogic.gdx.utils.Array#toArray(Class)")
    assertEquals(f.base, "libgdx-core")
    assert(clue(f.detail).contains("Dropped"))
    // and it is located in the DEPENDENT's Java, at the forwarder — the site an author has to fix.
    assert(clue(f.origin.javaPath).endsWith("ImmutableArray.java"))

    // The nilary twin beside it is NOT reported. That is the whole reason arity has to separate the
    // overloads: both are `com.badlogic.gdx.utils.Array#toArray` to a TIR symbol, and reporting the
    // portable one would make the check noise on the very call the port is supposed to keep.
    assert(!phase.findings.exists(_.symbol == "com.badlogic.gdx.utils.Array#toArray()"))
    // …and nothing was reported as undecidable. An `Ambiguous` here would mean the overloads were
    // never separated at all and the one finding above is luck.
    assertEquals(clue(phase.findings).filter(_.issue == PortMapTransform.Issue.Ambiguous), Nil)
  }

  test("the same program with NO map produces nothing — the phase is a total no-op unconfigured") {
    val (phase, out) = run(model(baseArray, dependent), Nil)
    assertEquals(phase.findings, Nil)
    assertEquals(phase.policyReport.findings, Nil)
    assertEquals(phase.renamedSymbols, 0)
    assertEquals(out.units.size, Pipeline.run(model(baseArray, dependent), Nil).units.size)
  }

  // -------------------------------------------------------------------------
  // the other two things a map carries
  // -------------------------------------------------------------------------

  test("a RENAMED type re-points, without the dependent restating the rename") {
    // The base emitted its `Array` at another name. The dependent inherits no rename map here —
    // that is the point: the only statement of the move is the base's published output.
    val m = PortMap.of("libgdx-core", "eng", List("sge.utils.Array"),
      balticporter.tir.SrcMap.Recording(Nil), Set.empty, Set.empty, Set.empty, Set.empty,
      renames = Map("com.badlogic.gdx" -> "sge"))
    assertEquals(m.types.map(e => (e.upstream, e.emitted, e.disposition)),
      List(("com.badlogic.gdx.utils.Array", "sge.utils.Array", PortMap.Disposition.Renamed)))

    val (phase, out) = run(model(baseArray, dependent), List(m))
    val names = out.symbols.all.map(_.fullName).toSet
    assert(clue(names).contains("sge.utils.Array"))
    assert(!names.contains("com.badlogic.gdx.utils.Array"))
    // the member came across with it — a prefix is cut at a separator and the suffix carried
    // verbatim, so `#toArray` did not have to be listed anywhere
    assert(clue(names).exists(_.startsWith("sge.utils.Array#toArray")))
    // …and the DEPENDENT's own namespace is untouched: a rename is the base's fact about the base.
    assert(clue(names).contains("com.badlogic.ashley.utils.ImmutableArray"))
    assert(phase.renamedSymbols > 0)
  }

  test("the re-point leaves a DECISION naming the base's map entry — the only statement of it") {
    val m = PortMap.of("libgdx-core", "eng", List("sge.utils.Array"),
      balticporter.tir.SrcMap.Recording(Nil), Set.empty, Set.empty, Set.empty, Set.empty,
      renames = Map("com.badlogic.gdx" -> "sge"))
    val (_, _, log) = runTraced(model(baseArray, dependent), List(m))
    val ds = log.of(balticporter.tir.Decision.Kind.RetypedSignature)

    // Every row is the DEPENDENT's own — never the base's. A dependent's `Program` CONTAINS the
    // base (`resolutionRoots` parses it), so `Array`'s own references to `Array` are in the model
    // too, and reporting them tells this module's author about a module they do not own
    // (ENGINE-LIMITS D2). The filter is the phase's own `ownedByBase`, as `scan` already uses.
    assert(clue(ds).nonEmpty)
    assert(ds.forall(_.subjectFqn.startsWith("com.badlogic.ashley.")), clue(ds.map(_.render)))

    val d = ds.head
    // the KEY is the BASE's entry, not this module's manifest: grepping the dependent's policy for
    // this rename finds nothing, and re-running the base is the only thing that changes it
    assertEquals(d.reason,
      balticporter.tir.Reason.Configured("port-map-migration",
        "com.badlogic.gdx.utils.Array -> sge.utils.Array"))
    assertEquals(d.detail("base"), "libgdx-core")
    assertEquals(d.detail("to"), "sge.utils.Array")
  }

  test("no map, no decisions — the unconfigured phase is silent as well as inert") {
    val (_, _, log) = runTraced(model(baseArray, dependent), Nil)
    assertEquals(log.all, Nil)
  }

  test("a call into a HAND-SUPPLIED body is reported — the signature cannot show it") {
    val m = PortMap.of("libgdx-core", "eng", List("com.badlogic.gdx.utils.Array"),
      balticporter.tir.SrcMap.Recording(List(balticporter.tir.SrcMap.Entry(
        "com.badlogic.gdx.utils.Array", "com.badlogic.gdx.utils.Array#toArray()", "def", 1, 2,
        "com/badlogic/gdx/utils/Array.java", 589, "d0"))),
      Set.empty, Set.empty, Set.empty,
      bodyKeys = Set("com.badlogic.gdx.utils.Array#toArray()"), renames = Map.empty)

    val (phase, _) = run(model(baseArray, dependent), List(m))
    val body = phase.findings.filter(_.issue == PortMapTransform.Issue.SubstitutedBody)
    assertEquals(clue(body).map(_.symbol), List("com.badlogic.gdx.utils.Array#toArray()"))
    assertEquals(body.head.base, "libgdx-core")
    // Exactly ONE, and that is the assertion with teeth. A TIR symbol's `fullName` is `X#toArray`
    // for BOTH overloads, so the 1-argument call is a second, distinct symbol matching the same
    // bare key; attributing it to the map's 0-argument record reported the base's decision about a
    // member nobody called. Arity is the whole of overload identity here.
    assertEquals(clue(phase.findings).size, 1)
  }

  test("a call whose arity matches NO recorded overload is a MISS, never the nearest record") {
    // Direct on `select`, because the property is about what is NOT reported and a corpus cannot
    // show an absence convincingly. The map knows only the nilary overload; a one-argument call
    // must select nothing rather than inherit its disposition.
    val nilary  = ("base", PortMap.Entry("member", "p.C#m()", "p.C#m()", PortMap.Disposition.Dropped))
    val unary   = ("base", PortMap.Entry("member", "p.C#m(int)", "p.C#m(int)", PortMap.Disposition.Ported))
    val field   = ("base", PortMap.Entry("member", "p.C#f", "p.C#f", PortMap.Disposition.Dropped))
    assertEquals(PortMapTransform.select(List(nilary), Some(1)), Nil)
    assertEquals(PortMapTransform.select(List(nilary, unary), Some(0)), List(nilary))
    assertEquals(PortMapTransform.select(List(nilary, unary), Some(1)), List(unary))
    // no arity to go on (a method reference, not an `Apply`) — every candidate survives, and the
    // caller reports `Ambiguous` if they disagree rather than picking one
    assertEquals(PortMapTransform.select(List(nilary, unary), scala.None), List(nilary, unary))
    // a key with no parameter list is a field and is never excluded by arity
    assertEquals(PortMapTransform.select(List(field), Some(2)), List(field))
  }

  test("a reference to a DROPPED type is reported; a SUBSTITUTED one is not") {
    // The distinction is the whole content of the entry for a caller: `Substituted` means injected
    // Scala stands at the name and the call is fine, `Dropped` means nothing does. A check that
    // conflated them would fire on every substitution the base ships, which is most of them.
    def mapWith(injected: Set[String]) = PortMap.of("libgdx-core", "eng", Nil,
      balticporter.tir.SrcMap.Recording(Nil),
      dropTypes = Set("com.badlogic.gdx.utils.Array"), dropMethods = Set.empty,
      injectedFqns = injected, bodyKeys = Set.empty, renames = Map.empty)

    val (dropped, _) = run(model(baseArray, dependent), List(mapWith(Set.empty)))
    assert(clue(dropped.findings).exists(f =>
      f.issue == PortMapTransform.Issue.DroppedType && f.symbol == "com.badlogic.gdx.utils.Array"))

    val (replaced, _) = run(model(baseArray, dependent), List(mapWith(Set("com.badlogic.gdx.utils.Array"))))
    assert(!replaced.findings.exists(_.issue == PortMapTransform.Issue.DroppedType))
  }

  test("a map that matches NOTHING is REPORTED — a wrong or stale map must not be silently inert") {
    // The failure this phase could otherwise have: hand it the wrong module's map, or one published
    // before a namespace moved, and it does nothing at all while looking configured. There is
    // deliberately no per-ENTRY report — a base publishes tens of thousands and a dependent touches
    // a few hundred — so the granularity is the map.
    val wrong = PortMap.of("some-other-module", "eng", List("nothing.At.All"),
      balticporter.tir.SrcMap.Recording(Nil), Set.empty, Set.empty, Set.empty, Set.empty, Map.empty)
    val (phase, _) = run(model(baseArray, dependent), List(wrong))
    assertEquals(phase.findings, Nil)
    assertEquals(phase.policyReport.findings.map(_.issue), List(PolicyIssue.NeverMatched))
    assert(clue(phase.policyReport.findings.head.key).contains("some-other-module"))
  }
