package balticporter.core

import balticporter.core.ManifestAgreement.{BasePort, Kind}
import balticporter.tir.SrcMap

/** PER-LOCATION SELECTION AS SHARED SURFACE — the §1.5 half of `resolutions`.
  *
  * A remedy decides emitted text at a declaration, so two modules that answer one location
  * differently produce two ports that each compile alone and cannot compile together. That is the
  * failure this file pins, in both of its shapes: a chain that disagrees with itself, and a
  * dependent quietly answering for a declaration its base EMITS.
  *
  * Both are checked in `surfacePairs`, which means both are load-bearing — `surfaceGate` stops the
  * run before any phase runs rather than reporting the disagreement beside output an operator would
  * then have to read to work out which of the two answers produced it.
  */
class ResolutionAgreementSpec extends munit.FunSuite:

  private def mapOf(module: String = "base", emitted: List[String] = Nil,
                    dropTypes: Set[String] = Set.empty, injected: Set[String] = Set.empty) =
    PortMap.of(module, "eng", emitted, SrcMap.Recording(Nil), dropTypes, Set.empty, injected,
      Set.empty, Map.empty)

  private def kinds(fs: List[ManifestAgreement.Finding]) =
    fs.map(_.kind).filterNot(_ == Kind.InheritedKeyNeverFired).sortBy(_.toString)

  private def check(m: PortManifest, ports: List[BasePort] = Nil) =
    ManifestAgreement.check(Some(m), Nil, foreignRoots = false, ports)

  // -------------------------------------------------------------------------------------------
  // the union, and what it deliberately does NOT hide
  // -------------------------------------------------------------------------------------------

  test("resolutions are INHERITED — a dependent applies its base's selections without restating them") {
    val base = PortManifest(name = "base", governs = Set("up"),
                            resolutions = Map("up.A#m" -> "wrap"))
    val dep  = base.extendedBy(PortManifest(name = "dep", resolutions = Map("dep.B#n" -> "copy")))
    assertEquals(dep.effectiveResolutions, Map("up.A#m" -> "wrap", "dep.B#n" -> "copy"))
  }

  test("the same key with two values anywhere in a chain is FATAL, naming both claimants") {
    val base = PortManifest(name = "base", governs = Set("up"), resolutions = Map("up.A#m" -> "wrap"))
    val dep  = base.extendedBy(PortManifest(name = "dep", resolutions = Map("up.A#m" -> "copy")))
    // the union still answers — nearest wins, which is what makes the effective policy well
    // defined — and the disagreement is a finding beside it rather than a silent override.
    assertEquals(dep.effectiveResolutions, Map("up.A#m" -> "copy"))
    val fs = check(dep).filter(_.kind == Kind.ResolutionDivergence)
    assertEquals(fs.map(_.subject), List("up.A#m"))
    assert(fs.forall(_.kind.fatal))
    assert(clue(fs.head.detail).contains("""`base` selects "wrap""""))
    assert(clue(fs.head.detail).contains("""`dep` selects "copy""""))
  }

  test("…and it STOPS THE RUN: the gate asks it before any phase runs") {
    val base = PortManifest(name = "base", governs = Set("up"), resolutions = Map("up.A#m" -> "wrap"))
    val dep  = base.extendedBy(PortManifest(name = "dep", resolutions = Map("up.A#m" -> "copy")))
    assertEquals(ManifestAgreement.surfaceGate(Some(dep)).map(_.kind), List(Kind.ResolutionDivergence))
  }

  test("agreeing on the same key is not a divergence — a longhand module may restate shared policy") {
    val base = PortManifest(name = "base", governs = Set("up"), resolutions = Map("up.A#m" -> "wrap"))
    val dep  = base.extendedBy(PortManifest(name = "dep", resolutions = Map("up.A#m" -> "wrap")))
    assertEquals(check(dep).filter(_.kind == Kind.ResolutionDivergence), Nil)
  }

  // -------------------------------------------------------------------------------------------
  // …and the comparison is what two keys NAME, never the two strings (§4.56 at a policy key)
  // -------------------------------------------------------------------------------------------

  test("TWO SPELLINGS of one member with two ids is the same divergence — the string test saw none") {
    // `Foo#bar` and `Foo#bar(int)` are both legal and name one member wherever `bar` has one
    // overload. Compared by string this chain agreed with itself while emitting that declaration two
    // ways — the exact failure `ResolutionDivergence` exists for, one spelling out of reach.
    val base = PortManifest(name = "base", governs = Set("up"), resolutions = Map("up.A#m" -> "wrap"))
    val dep  = base.extendedBy(PortManifest(name = "dep", resolutions = Map("up.A#m(int)" -> "copy")))
    val fs   = check(dep).filter(_.kind == Kind.ResolutionDivergence)
    assertEquals(fs.map(_.subject), List("up.A#m"))
    assert(clue(fs.head.detail).contains("""`base` selects "wrap""""))
    assert(clue(fs.head.detail).contains("""`dep` selects "copy" at `up.A#m(int)`"""))
    assert(fs.forall(_.kind.fatal))
  }

  test("…and TWO DISTINCT descriptors are two members, which is the over-approximation refused") {
    // A source-level descriptor is injective within an overload set, so `m(int)` and `m(String)`
    // really are two declarations and one answer each is not a disagreement.
    val base = PortManifest(name = "base", governs = Set("up"), resolutions = Map("up.A#m(int)" -> "wrap"))
    val dep  = base.extendedBy(PortManifest(name = "dep", resolutions = Map("up.A#m(String)" -> "copy")))
    assertEquals(check(dep).filter(_.kind == Kind.ResolutionDivergence), Nil)
  }

  test("…and the same member under the other spelling with the SAME id is agreement, not a finding") {
    val base = PortManifest(name = "base", governs = Set("up"), resolutions = Map("up.A#m" -> "wrap"))
    val dep  = base.extendedBy(PortManifest(name = "dep", resolutions = Map("up.A#m(int)" -> "wrap")))
    assertEquals(check(dep).filter(_.kind == Kind.ResolutionDivergence), Nil)
  }

  // -------------------------------------------------------------------------------------------
  // the intrusion screen — asked of what the base EMITS, never of its `governs` CLAIM
  // -------------------------------------------------------------------------------------------

  test("a dependent selecting a remedy at a declaration its base EMITS is FATAL") {
    val base = PortManifest(name = "base", governs = Set("up"))
    val dep  = base.extendedBy(PortManifest(name = "dep", resolutions = Map("up.A#m" -> "wrap")))
    val fs   = check(dep, List(BasePort(base, Some(mapOf(emitted = List("up.A"))), "run-latest")))
    assertEquals(kinds(fs), List(Kind.ResolutionIntrusion))
    assert(clue(fs.head.detail).contains("""published map emits it as "up.A""""))
  }

  test("…and a declaration the base does NOT emit is admitted — D10's rule, at a member key") {
    // A dependent's own declarations routinely sit inside the base's claimed namespace: a TEST
    // source set always does. Screened by the CLAIM every such key would be an intrusion, which is a
    // rule with no way to comply with it.
    val base = PortManifest(name = "base", governs = Set("up"))
    val dep  = base.extendedBy(PortManifest(name = "dep", resolutions = Map("up.MyTest#m" -> "wrap")))
    val fs   = check(dep, List(BasePort(base, Some(mapOf(emitted = List("up.A"))), "run-latest")))
    assertEquals(kinds(fs), Nil)
  }

  test("…nor is a key the base ITSELF answers: that is inheritance, or already a divergence") {
    val base = PortManifest(name = "base", governs = Set("up"), resolutions = Map("up.A#m" -> "wrap"))
    val dep  = base.extendedBy(PortManifest(name = "dep", resolutions = Map("up.A#m" -> "wrap")))
    val fs   = check(dep, List(BasePort(base, Some(mapOf(emitted = List("up.A"))), "run-latest")))
    assertEquals(kinds(fs), Nil)
  }

  test("…and a base that answers it under the OTHER SPELLING has answered it — not an intrusion") {
    // The false positive that rides on the same string comparison as the missed divergence above:
    // the base said `up.A#m`, this module says `up.A#m(int)`, and `contains` calls that an intrusion
    // into a namespace the base emits. It is the same answer at the same member.
    val base = PortManifest(name = "base", governs = Set("up"), resolutions = Map("up.A#m" -> "wrap"))
    val dep  = base.extendedBy(PortManifest(name = "dep", resolutions = Map("up.A#m(int)" -> "wrap")))
    val fs   = check(dep, List(BasePort(base, Some(mapOf(emitted = List("up.A"))), "run-latest")))
    assertEquals(kinds(fs).filter(_ == Kind.ResolutionIntrusion), Nil)
  }

  test("a base with NO usable map falls back to refusing, which is the safe direction") {
    val base = PortManifest(name = "base", governs = Set("up"), dropTypes = Set("up.Gone"))
    val dep  = base.extendedBy(PortManifest(name = "dep", resolutions = Map("up.A#m" -> "wrap")))
    assert(kinds(check(dep, List(BasePort(base)))).contains(Kind.ResolutionIntrusion))
  }

  test("a subject the base DROPS and leaves empty is the allowed case") {
    val base = PortManifest(name = "base", governs = Set("up"), dropTypes = Set("up.Gone"))
    val dep  = base.extendedBy(PortManifest(name = "dep", resolutions = Map("up.Gone#m" -> "wrap")))
    val m    = mapOf(emitted = List("up.A"), dropTypes = Set("up.Gone"))
    assertEquals(kinds(check(dep, List(BasePort(base, Some(m), "run-latest")))), Nil)
  }

  test("a BASE's own selections are its own business — nothing here reports them") {
    val base = PortManifest(name = "base", governs = Set("up"), resolutions = Map("up.A#m" -> "wrap"))
    val dep  = base.extendedBy(PortManifest(name = "dep"))
    assertEquals(kinds(check(dep, List(BasePort(base, Some(mapOf(emitted = List("up.A"))), "run-latest")))),
                 Nil)
  }

  // -------------------------------------------------------------------------------------------
  // the `mirroring` path — the one shape the CHAIN check cannot see
  // -------------------------------------------------------------------------------------------

  test("a MIRRORING module that omits a base's selection is fatal — the chain check sees one manifest") {
    // `resolutionConflicts` reads `policyChain`, which is `List(this)` here: one manifest, nothing
    // to disagree with. So the omission is invisible to it and to every count, and the two modules
    // emit that declaration two ways.
    val base = PortManifest(name = "base", governs = Set("up"), resolutions = Map("up.A#m" -> "wrap"))
    val dep  = PortManifest(name = "dep").mirroring(base)
    assertEquals(dep.policyChain.map(_.name), List("dep"))
    assertEquals(dep.resolutionConflicts, Nil)
    val fs = check(dep, List(BasePort(base, Some(mapOf(emitted = List("up.A"))), "run-latest")))
      .filter(_.kind == Kind.MissingResolution)
    assertEquals(fs.map(_.subject), List("up.A#m"))
    assert(fs.forall(_.kind.fatal))
    assert(clue(fs.head.detail).contains("""selects "wrap" here; this module selects nothing"""))
  }

  test("…and one that RESTATES it differently is the same divergence, reported the same way") {
    val base = PortManifest(name = "base", governs = Set("up"), resolutions = Map("up.A#m" -> "wrap"))
    val dep  = PortManifest(name = "dep", resolutions = Map("up.A#m" -> "copy")).mirroring(base)
    val fs = check(dep, List(BasePort(base, Some(mapOf(emitted = List("up.A"))), "run-latest")))
      .filter(_.kind == Kind.ResolutionDivergence)
    assertEquals(fs.map(_.subject), List("up.A#m"))
    assert(clue(fs.head.detail).contains("""selects "wrap", this module "copy""""))
  }

  test("…and restating it IDENTICALLY is not a finding — that is what `mirroring` is FOR") {
    val base = PortManifest(name = "base", governs = Set("up"), resolutions = Map("up.A#m" -> "wrap"))
    val dep  = PortManifest(name = "dep", resolutions = Map("up.A#m" -> "wrap")).mirroring(base)
    assertEquals(kinds(check(dep, List(BasePort(base, Some(mapOf(emitted = List("up.A"))), "run-latest")))),
                 Nil)
  }

  test("…nor under the OTHER LEGAL SPELLING, which a string comparison called a MISSING selection") {
    // A mirroring module writes its policy in full, so it may reasonably spell a key more precisely
    // than its base did. Compared by string that is `MissingResolution`, fatal, for agreeing.
    val base = PortManifest(name = "base", governs = Set("up"), resolutions = Map("up.A#m" -> "wrap"))
    val dep  = PortManifest(name = "dep", resolutions = Map("up.A#m(int)" -> "wrap")).mirroring(base)
    assertEquals(kinds(check(dep, List(BasePort(base, Some(mapOf(emitted = List("up.A"))), "run-latest")))),
                 Nil)
  }

  test("…and the same pair with two IDS is the divergence, naming the spelling that differs") {
    val base = PortManifest(name = "base", governs = Set("up"), resolutions = Map("up.A#m" -> "wrap"))
    val dep  = PortManifest(name = "dep", resolutions = Map("up.A#m(int)" -> "copy")).mirroring(base)
    val fs = check(dep, List(BasePort(base, Some(mapOf(emitted = List("up.A"))), "run-latest")))
      .filter(_.kind == Kind.ResolutionDivergence)
    assertEquals(fs.map(_.subject), List("up.A#m"))
    assert(clue(fs.head.detail).contains("the same member under the other spelling"))
  }

  test("…and it STOPS THE RUN, because a selection decides emitted text") {
    val base = PortManifest(name = "base", governs = Set("up"), resolutions = Map("up.A#m" -> "wrap"))
    val dep  = PortManifest(name = "dep").mirroring(base)
    assertEquals(ManifestAgreement.surfaceGate(Some(dep)).map(_.kind), List(Kind.MissingResolution))
  }

  test("the comparison is SCOPED to `!inherit`, so an override is told once and not twice") {
    // For an inheriting module the OMISSION half is vacuous by construction — its effective map
    // contains its base's — while the DIVERGENCE half is not: an override is a real disagreement,
    // and `chainConflicts` already reports it from the chain, naming both claimants. Unscoped, this
    // loop said the same thing again in a shorter sentence, which is one mistake told twice.
    val base = PortManifest(name = "base", governs = Set("up"), resolutions = Map("up.A#m" -> "wrap"))
    val dep  = base.extendedBy(PortManifest(name = "dep", resolutions = Map("up.A#m" -> "copy")))
    val fs   = check(dep, List(BasePort(base, Some(mapOf(emitted = List("up.A"))), "run-latest")))
    assertEquals(kinds(fs), List(Kind.ResolutionDivergence))
    assert(clue(fs.head.detail).contains("""`base` selects "wrap""""))
    // …and a module that adds a key of its own reports nothing at all.
    val other = base.extendedBy(PortManifest(name = "dep", resolutions = Map("dep.B#n" -> "copy")))
    assertEquals(kinds(check(other, List(BasePort(base, Some(mapOf(emitted = List("up.A"))), "run-latest")))),
                 Nil)
  }

  // -------------------------------------------------------------------------------------------
  // the fingerprint
  // -------------------------------------------------------------------------------------------

  test("a selection MOVES the surface fingerprint — a base whose choices changed is not fresh") {
    val a = PortManifest(name = "m")
    val b = PortManifest(name = "m", resolutions = Map("up.A#m" -> "wrap"))
    val c = PortManifest(name = "m", resolutions = Map("up.A#m" -> "copy"))
    assertNotEquals(PortMap.policyDigest(a.surfaceDigestInputs), PortMap.policyDigest(b.surfaceDigestInputs))
    assertNotEquals(PortMap.policyDigest(b.surfaceDigestInputs), PortMap.policyDigest(c.surfaceDigestInputs))
  }

  test("…and a manifest with NO selections digests exactly as the surface list alone did") {
    // what makes this field's arrival provably flat on every port that does not use it.
    val m = PortManifest(name = "m")
    assertEquals(m.surfaceDigestInputs, m.effectiveSurface.map(PortManifest.fingerprint))
  }
