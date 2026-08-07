package balticporter.core

import balticporter.core.ManifestAgreement.{BasePort, Kind, SharedType}
import balticporter.tir.SrcMap

/** What a base's PUBLISHED map lets the agreement check see that RE-DERIVATION cannot.
  *
  * Each test below is a case where the two sources of truth give different answers, and the map is
  * right — which is the only justification for reading it at all. The re-derivation path is pinned
  * too: it is the fallback whenever a map is missing or stale, so a regression in it would be
  * invisible on a corpus where every base has been run.
  */
class ManifestAgreementSpec extends munit.FunSuite:

  private def mapOf(
      module: String = "base",
      emitted: List[String] = Nil,
      dropTypes: Set[String] = Set.empty,
      dropMethods: Set[String] = Set.empty,
      injected: Set[String] = Set.empty,
      renames: Map[String, String] = Map.empty,
  ) = PortMap.of(module, "eng", emitted, SrcMap.Recording(Nil), dropTypes, dropMethods, injected,
    Set.empty, renames)

  private val base = PortManifest(name = "base", governs = Set("up"), dropTypes = Set("up.Gone"))
  private val dependent = base.extendedBy(PortManifest(name = "dep"))

  /** The static layer's `InheritedKeyNeverFired` is dropped throughout: it is a property of the
    * `Substitutions` tally, which nothing populates when the check is driven by hand, and every one
    * of these tests is about the DYNAMIC layer. Keeping it would make each assertion carry a
    * constant that says nothing about what is being tested. */
  private def run(shared: List[SharedType], ports: List[BasePort]) =
    ManifestAgreement.check(Some(dependent), shared, foreignRoots = true, ports)
      .filterNot(_.kind == Kind.InheritedKeyNeverFired)

  private def kinds(fs: List[ManifestAgreement.Finding]) =
    fs.map(_.kind).filterNot(_ == Kind.InheritedKeyNeverFired).sortBy(_.toString)

  test("with a fresh map the shared surface agrees, and nothing is reported") {
    val m = mapOf(emitted = List("up.Kept"), dropTypes = Set("up.Gone"))
    val fs = run(
      List(SharedType("up.Kept", "up.Kept", substituted = false),
           SharedType("up.Gone", "up.Gone", substituted = true)),
      List(BasePort(base, Some(m), "run-latest")),
    )
    assertEquals(clue(fs).map(_.render), Nil)
  }

  test("a type the base's map NEITHER emitted NOR dropped is a finding re-derivation cannot make") {
    // The whole of hole 5. The base's manifest is silent about `up.Missing` — it declares no drop
    // for it — so re-derivation concludes "translate it mechanically, like the base did", and the
    // dependent emits a reference to a class the base never wrote. The map says the base's output
    // has no such type, which is the only place that fact exists.
    val m  = mapOf(emitted = List("up.Kept"), dropTypes = Set("up.Gone"))
    val sh = List(SharedType("up.Missing", "up.Missing", substituted = false))

    assertEquals(kinds(run(sh, List(BasePort(base, Some(m), "run-latest")))), List(Kind.BaseSurfaceAbsent))
    // …and with no map, the same input is silently clean apart from the note saying so. That is
    // the gap being closed.
    assertEquals(kinds(run(sh, List(BasePort(base)))), List(Kind.BaseMapMissing))
  }

  test("tag parity comes from the base's OUTPUT, so a drop that never fired is caught") {
    // The base DECLARES `up.Gone` dropped, and its map shows it emitted anyway — a key that matched
    // nothing, the failure `PolicyReport` exists for, seen here from the dependent's side. The
    // dependent inherited the declaration and tagged the type; the base did not. Re-derivation
    // compares declaration against declaration and reports nothing at all.
    val m  = mapOf(emitted = List("up.Gone"))
    val sh = List(SharedType("up.Gone", "up.Gone", substituted = true))
    assertEquals(kinds(run(sh, List(BasePort(base, Some(m), "run-latest")))), List(Kind.TagUnexpected))
    assertEquals(kinds(run(sh, List(BasePort(base)))), List(Kind.BaseMapMissing))
  }

  test("a SUBSTITUTED type and a DROPPED type both oblige the dependent to tag it") {
    // Only the map distinguishes these two, and it must NOT distinguish them here: `Dropped` means
    // nothing stands at the name and `Substituted` means injected Scala does, but neither is a
    // mechanical translation, so a dependent that translated one mechanically is wrong either way.
    val m = mapOf(dropTypes = Set("up.Gone", "up.Swapped"), injected = Set("up.Swapped"))
    val fs = run(
      List(SharedType("up.Gone", "up.Gone", substituted = false),
           SharedType("up.Swapped", "up.Swapped", substituted = false)),
      List(BasePort(base, Some(m), "run-latest")),
    )
    assertEquals(kinds(fs), List(Kind.TagMissing, Kind.TagMissing))
    assert(clue(fs.map(_.detail)).exists(_.contains("Substituted")))
    assert(clue(fs.map(_.detail)).exists(_.contains("Dropped")))
  }

  test("the emitted NAME is compared against what the base wrote, not against its rename map") {
    // A base whose rename failed to reach an owned symbol satisfies its own rename map and not its
    // own output. Re-derivation asks the map; only the port map knows what was written. Here the
    // base renamed `up` to `port` in policy and emitted `up.Kept` regardless.
    val renaming = PortManifest(name = "base", governs = Set("up"), packageRenames = Map("up" -> "port"))
    val dep      = renaming.extendedBy(PortManifest(name = "dep"))
    val m        = mapOf(emitted = List("up.Kept")) // no rename applied in the base's OUTPUT
    val sh       = List(SharedType("up.Kept", "port.Kept", substituted = false))

    // re-derived: the dependent emits `port.Kept`, the base's rename map says `port.Kept`. Clean.
    assertEquals(kinds(ManifestAgreement.check(Some(dep), sh, true, List(BasePort(renaming)))),
      List(Kind.BaseMapMissing))
    // published: the base actually emitted `up.Kept`, so the two ports cannot compile together.
    val fs = ManifestAgreement.check(Some(dep), sh, true, List(BasePort(renaming, Some(m), "run-latest")))
    assertEquals(kinds(fs), List(Kind.SurfaceNameDivergence))
    assert(clue(fs.head.detail).contains("emits"))
  }

  test("a STALE map is REFUSED and reported — never used quietly") {
    // The orchestrator proves staleness (it has the filesystem); this pins what the check does with
    // the answer. The map's entries must have no effect, so the very input that is a finding with a
    // fresh map is only the staleness note with a stale one, and the fallback is re-derivation.
    val sh = List(SharedType("up.Missing", "up.Missing", substituted = false))
    val fs = run(sh, List(BasePort(base, scala.None, "run-latest", stale = List("the base's Java has changed"))))
    assertEquals(kinds(fs), List(Kind.BaseMapStale))
    assert(!Kind.BaseMapStale.fatal, "staleness is operational — it must not abort a run that is otherwise coherent")
  }

  test("an UNVERIFIED map is used, and said so — absence of proof is not proof") {
    val m  = mapOf(emitted = List("up.Kept"), dropTypes = Set("up.Gone"))
    val sh = List(SharedType("up.Missing", "up.Missing", substituted = false))
    val fs = run(sh, List(BasePort(base, Some(m), "baseline", unverified = List("no source fingerprint"))))
    assertEquals(kinds(fs), List(Kind.BaseMapUnverified, Kind.BaseSurfaceAbsent))
  }

  test("an EMPTY base manifest is not asked for a map — that is how a non-ported root is declared") {
    // CLAUDE.md §1.5: a resolution root that is not a ported module is declared with an empty
    // manifest, and holding one to the obligation to publish a map would turn a statement into a
    // finding. It also must not claim any namespace, so nothing under it is `BaseSurfaceAbsent`.
    val vendored = PortManifest(name = "vendored")
    val dep      = vendored.extendedBy(PortManifest(name = "dep"))
    val fs = ManifestAgreement.check(Some(dep),
      List(SharedType("third.party.T", "third.party.T", substituted = false)), true, List(BasePort(vendored)))
    assertEquals(clue(fs).map(_.render), Nil)
  }

  // ---------------------------------------------------------------------------
  // M6 — the PER-TYPE half of the rename policy is shared surface, and is INHERITED
  // ---------------------------------------------------------------------------

  private val moving = PortManifest(
    name               = "base",
    governs            = Set("up"),
    typeRenames        = Map("up.Map" -> "MapFilter"),
    subPackages        = Map("up.Impl" -> "internal"),
    flattenNestedTypes = Set("up.Conn$Directed"),
  )

  private def statics(m: PortManifest) =
    ManifestAgreement.check(Some(m), Nil, foreignRoots = true, Nil)
      .filterNot(_.kind == Kind.InheritedKeyNeverFired)

  test("a dependent that INHERITS the base's per-type renames agrees, and nothing is reported") {
    assertEquals(clue(statics(moving.extendedBy(PortManifest(name = "dep")))).map(_.render), Nil)
  }

  test("a dependent that RESTATES them longhand and gets one wrong is FATAL, per entry") {
    val dep = PortManifest(
      name = "dep",
      // `up.Map` moved to a different destination, `up.Impl` not moved at all, `up.Conn$Directed`
      // correct — three declarations, two disagreements.
      typeRenames        = Map("up.Map" -> "GdxMap"),
      flattenNestedTypes = Set("up.Conn$Directed"),
    ).mirroring(moving)
    val fs = statics(dep).filter(_.kind == Kind.TypeRenameDivergence)
    assertEquals(fs.map(_.subject).sorted, List("up.Impl", "up.Map"))
    assert(fs.forall(_.kind.fatal), clue = "two ports that name one class two ways cannot compile together")
    assert(clue(fs.find(_.subject == "up.Map").get.detail).contains("typeRenames=GdxMap"))
  }

  test("a dependent that moves a type INSIDE the base's namespace the base leaves alone is caught") {
    val dep = PortManifest(name = "dep", subPackages = Map("up.Extra" -> "internal"))
      .mirroring(PortManifest(name = "base", governs = Set("up")))
    val fs = statics(dep).filter(_.kind == Kind.TypeRenameDivergence)
    assertEquals(fs.map(_.subject), List("up.Extra"))
    assert(clue(fs.head.detail).contains("claims this namespace"))
  }

  test("a DECLARED boundary split is half of the rename: inheriting one and not the other is caught") {
    val split = PortManifest(name = "base", governs = Set("up"),
                             typeRenames = Map("up.A" -> "other.A"), allowPackageSplit = Set("up.A"))
    val dep = PortManifest(name = "dep", typeRenames = Map("up.A" -> "other.A")).mirroring(split)
    val fs  = statics(dep).filter(_.kind == Kind.TypeRenameDivergence)
    assertEquals(fs.map(_.subject), List("up.A"))
    assert(clue(fs.head.detail).contains("DELIBERATE"))
    // …and inheriting BOTH is silent, which is the whole point of `extendedBy`.
    assertEquals(clue(statics(split.extendedBy(PortManifest(name = "dep2")))).map(_.render), Nil)
  }

  test("the name a base gives a shared type includes its per-TYPE moves, not only its packages") {
    val b   = PortManifest(name = "base", governs = Set("up"), packageRenames = Map("up" -> "sge"),
                           typeRenames = Map("up.Map" -> "MapFilter"))
    val dep = b.extendedBy(PortManifest(name = "dep"))
    assertEquals(dep.renamed("up.Map"), "sge.MapFilter")
    assertEquals(dep.renamed("up.Other"), "sge.Other")
    // a run that emitted the base's name agrees; one that emitted the merely-package-renamed name
    // does not, which is the divergence this half exists to see.
    val ok = ManifestAgreement.check(Some(dep), List(SharedType("up.Map", "sge.MapFilter", false)), true, Nil)
    assertEquals(ok.filter(_.kind == Kind.SurfaceNameDivergence), Nil)
    val bad = ManifestAgreement.check(Some(dep), List(SharedType("up.Map", "sge.Map", false)), true, Nil)
    assertEquals(bad.filter(_.kind == Kind.SurfaceNameDivergence).size, 1)
  }

  /** TARGETS: not inherited, and constrained in one direction only.
    *
    * `targets` decides which findings a module is told about and moves no emitted signature, so a
    * base and a dependent may hold different sets. What they may not do is hold them in the wrong
    * ORDER: a dependent targeting a backend its base does not is a port that depends on emitted
    * Scala nobody checked for that backend, and D2's ownership filter is exactly what stops it
    * seeing the base's findings — so the unbuildable half is the half nothing looks at.
    */
  private def targeted(baseT: Set[balticporter.catalog.Platform],
                       depT: Set[balticporter.catalog.Platform]) =
    val b = PortManifest(name = "base", governs = Set("up"), targets = baseT)
    val d = b.extendedBy(PortManifest(name = "dep", targets = depT))
    ManifestAgreement.check(Some(d), Nil, foreignRoots = true, Nil).filter(_.kind == Kind.TargetWidening)

  private val Jvm    = balticporter.catalog.Platform.Jvm
  private val Js     = balticporter.catalog.Platform.ScalaJs
  private val Native = balticporter.catalog.Platform.ScalaNative

  test("a dependent may target FEWER platforms than its base, and equally many") {
    assertEquals(targeted(Set(Jvm, Js, Native), Set(Jvm)).map(_.render), Nil)
    assertEquals(targeted(Set(Jvm, Native), Set(Jvm, Native)).map(_.render), Nil)
    assertEquals(targeted(Set(Jvm, Js, Native), Set.empty).map(_.render), Nil)
    // …and the DEFAULT on both sides is the ordinary case, which must be silent or every port in
    // the corpus gains a fatal finding for declaring nothing.
    val plain = PortManifest(name = "base", governs = Set("up")).extendedBy(PortManifest(name = "dep"))
    assertEquals(ManifestAgreement.check(Some(plain), Nil, true, Nil)
      .filter(_.kind == Kind.TargetWidening), Nil)
  }

  test("a dependent may NOT target a platform its base does not — and the finding names both sets") {
    val fs = targeted(Set(Jvm, Native), Set(Jvm, Js, Native))
    assertEquals(fs.size, 1)
    assertEquals(fs.head.subject, "ScalaJs")
    assert(clue(fs.head.detail).contains("Jvm/ScalaJs/ScalaNative"))
    assert(clue(fs.head.detail).contains("Jvm/ScalaNative"))
    assert(fs.head.kind.fatal, "a port that cannot be built is not a warning")
  }

  test("a base that narrowed and a dependent that did NOT is the DEFAULT-shaped trap") {
    // The realistic shape: a base declares `targets = [jvm]` deliberately and a dependent simply
    // never declares one, so it inherits nothing and gets the all-three default. That is a widening
    // by omission, and it is the case the rule exists for.
    val b   = PortManifest(name = "base", governs = Set("up"), targets = Set(Jvm))
    val dep = b.extendedBy(PortManifest(name = "dep"))
    val fs  = ManifestAgreement.check(Some(dep), Nil, true, Nil).filter(_.kind == Kind.TargetWidening)
    assertEquals(fs.size, 1)
    assertEquals(fs.head.subject, "ScalaJs, ScalaNative")
  }
