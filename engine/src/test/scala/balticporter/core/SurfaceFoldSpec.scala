package balticporter.core

import balticporter.core.ManifestAgreement.Kind
import balticporter.tir.Phase
import balticporter.transform.{ClassTableTransform, TypeRedirectTransform}

/** The merge contract — DESIGN.md §8.13, closing `ENGINE-LIMITS.md` D9.
  *
  * Every negative here is negative-tested: a check that has never fired is not known to work
  * (CLAUDE.md §3), and this mechanism's whole reason for existing is that the CHECK it relaxes
  * (`SurfaceDivergence`) must keep firing on everything it fired on before.
  */
class SurfaceFoldSpec extends munit.FunSuite:

  private def redirect(rs: (String, String)*): TypeRedirectTransform =
    new TypeRedirectTransform(rs.toMap)

  private def fps(m: PortManifest): List[String] = m.effectiveSurface.map(PortManifest.fingerprint)

  /** a base that CLAIMS a namespace, so the `governs` screen has something to read. */
  private def base(surface: List[Phase], drops: Set[String] = Set.empty) =
    PortManifest("base", governs = Set("com.demo"), dropTypes = drops, surface = surface)

  // -------------------------------------------------------------------------------------------
  // the positive: two disjoint tables become ONE phase, at the BASE's position
  // -------------------------------------------------------------------------------------------

  test("disjoint tables MERGE into one instance, at the base's pipeline position") {
    val b   = base(List(new ClassTableTransform(Map("com.demo.W#of" -> "com.demo.T#classFor")),
                        redirect("com.other.A" -> "com.dep.A")))
    val dep = b.extendedBy(PortManifest("dep", surface = List(redirect("com.other.B" -> "com.dep.B"))))

    // one phase per NAME, and the redirect is still SECOND — a merge changes a table, never an order
    assertEquals(dep.effectiveSurface.map(_.name), List("class-table", "type-redirect"))
    assertEquals(
      dep.effectiveSurface.collectFirst { case t: TypeRedirectTransform => t.redirects }.get,
      Map("com.other.A" -> "com.dep.A", "com.other.B" -> "com.dep.B"))
    assertEquals(dep.surfaceFold.refusals, Nil)
    // …and NOTHING is reported: not the divergence, and not the base's absorbed phase as missing
    assertEquals(ManifestAgreement.check(Some(dep), Nil, foreignRoots = true).map(_.kind), Nil)
  }

  test("`memberRenames` merge with the redirects, and an agreeing duplicate key is not a conflict") {
    val b = base(List(new TypeRedirectTransform(
      Map("com.other.A" -> "com.dep.A"), Map("com.other.A" -> Map("dispose" -> "close")))))
    val dep = b.extendedBy(PortManifest("dep", surface = List(new TypeRedirectTransform(
      // the SAME redirect, spelled again — agreement, not drift — plus one of its own
      Map("com.other.A" -> "com.dep.A", "com.other.B" -> "com.dep.B"),
      Map("com.other.B" -> Map("free" -> "close"))))))
    val merged = dep.effectiveSurface.collectFirst { case t: TypeRedirectTransform => t }.get
    assertEquals(merged.memberRenames,
      Map("com.other.A" -> Map("dispose" -> "close"), "com.other.B" -> Map("free" -> "close")))
    assertEquals(ManifestAgreement.check(Some(dep), Nil, foreignRoots = true).map(_.kind), Nil)
  }

  test("the fold is IDEMPOTENT and its instance is STABLE — a merged phase is built once") {
    val dep = base(List(redirect("com.other.A" -> "com.dep.A")))
      .extendedBy(PortManifest("dep", surface = List(redirect("com.other.B" -> "com.dep.B"))))
    // a `def` would hand the pipeline one instance and the policy report another (DESIGN.md §8.13)
    assert(dep.effectiveSurface.head eq dep.effectiveSurface.head)
    assertEquals(dep.effectiveSurface.map(_ eq dep.surfaceFold.phases.head), List(true))
  }

  // -------------------------------------------------------------------------------------------
  // D1: the base is the base AS THE BASE RAN IT
  // -------------------------------------------------------------------------------------------

  test("D1: a dependent's merge does not reach the BASE's own effective surface") {
    val b      = base(List(redirect("com.other.A" -> "com.dep.A")))
    val before = fps(b)
    val dep    = b.extendedBy(PortManifest("dep", surface = List(redirect("com.other.B" -> "com.dep.B"))))
    // the value the base's published `policy=` digest is compared against, unchanged — which is the
    // whole reason the fold runs on `policyChain` rather than in the run
    assertEquals(fps(b), before)
    assertEquals(fps(b), List("type-redirect[com.other.A->com.dep.A]"))
    assertNotEquals(fps(dep), fps(b))
    // and the base's fingerprint is ABSORBED, so it is not also reported missing from the dependent
    assert(clue(dep.surfaceFold.absorbed).contains("type-redirect[com.other.A->com.dep.A]"))
  }

  test("D1 down a CHAIN: what the middle module publishes is exactly what the last one absorbs") {
    val a = base(List(redirect("com.other.A" -> "com.dep.A")))
    val b = a.extendedBy(PortManifest("mid", surface = List(redirect("com.other.B" -> "com.dep.B"))))
    val c = b.extendedBy(PortManifest("last", surface = List(redirect("com.other.C" -> "com.dep.C"))))
    assertEquals(fps(b), List("type-redirect[com.other.A->com.dep.A,com.other.B->com.dep.B]"))
    assert(clue(c.surfaceFold.absorbed).contains(fps(b).head))
    assertEquals(ManifestAgreement.check(Some(c), Nil, foreignRoots = true).map(_.kind), Nil)
  }

  // -------------------------------------------------------------------------------------------
  // the negatives
  // -------------------------------------------------------------------------------------------

  test("REFUSED: same key, different value is still a fatal SurfaceDivergence") {
    val dep = base(List(redirect("com.other.A" -> "com.dep.A")))
      .extendedBy(PortManifest("dep", surface = List(redirect("com.other.A" -> "com.dep.OTHER"))))
    assertEquals(dep.effectiveSurface.size, 2, "a refused pair stays in the pipeline")
    assertEquals(dep.surfaceFold.refusals.map(_.cause), List(SurfaceFold.Cause.Conflict))
    val f = ManifestAgreement.check(Some(dep), Nil, foreignRoots = true)
    assertEquals(f.map(_.kind), List(Kind.SurfaceDivergence))
    assert(Kind.SurfaceDivergence.fatal)
    // the phase's OWN sentence reaches the reader, not just two opaque fingerprints
    assert(clue(f.head.detail).contains("both modules redirect"))
    assert(clue(f.head.detail).contains("com.dep.OTHER"))
  }

  test("REFUSED: two member renames of one member to two names") {
    val b = base(List(new TypeRedirectTransform(
      Map("com.other.A" -> "com.dep.A"), Map("com.other.A" -> Map("dispose" -> "close")))))
    val dep = b.extendedBy(PortManifest("dep", surface = List(new TypeRedirectTransform(
      Map("com.other.A" -> "com.dep.A"), Map("com.other.A" -> Map("dispose" -> "shutdown"))))))
    assertEquals(dep.surfaceFold.refusals.map(_.cause), List(SurfaceFold.Cause.Conflict))
    val f = ManifestAgreement.check(Some(dep), Nil, foreignRoots = true)
    assertEquals(f.map(_.kind), List(Kind.SurfaceDivergence))
    assert(clue(f.head.detail).contains("`dispose`"))
    assert(clue(f.head.detail).contains("com.other.A"))
  }

  test("NO CONTRACT: a phase that declares no merge diverges exactly as it did before") {
    val dep = base(List(new ClassTableTransform(Map("com.demo.W#of" -> "com.demo.T#classFor"))))
      .extendedBy(PortManifest("dep",
        surface = List(new ClassTableTransform(Map("com.demo.W#of" -> "com.demo.OTHER#classFor")))))
    assertEquals(dep.effectiveSurface.size, 2)
    assertEquals(dep.surfaceFold.refusals.map(_.cause), List(SurfaceFold.Cause.NoContract))
    val f = ManifestAgreement.check(Some(dep), Nil, foreignRoots = true)
    assertEquals(f.map(_.kind), List(Kind.SurfaceDivergence))
    assert(clue(f.head.detail).contains("declares no `MergeablePolicy`"))
  }

  test("two EQUAL instances of a contract-less phase report nothing, as before") {
    val table = Map("com.demo.W#of" -> "com.demo.T#classFor")
    val dep = base(List(new ClassTableTransform(table)))
      .extendedBy(PortManifest("dep", surface = List(new ClassTableTransform(table))))
    assertEquals(dep.effectiveSurface.size, 2)
    assertEquals(dep.surfaceFold.refusals, Nil, "equal policy is not drift, so it explains nothing")
    assertEquals(ManifestAgreement.check(Some(dep), Nil, foreignRoots = true), Nil)
  }

  // -------------------------------------------------------------------------------------------
  // the `governs` screen
  // -------------------------------------------------------------------------------------------

  test("INTRUSION: a merged-in subject the base EMITS is fatal, and is not a plain divergence") {
    val dep = base(List(redirect("com.other.A" -> "com.dep.A")))
      .extendedBy(PortManifest("dep", surface = List(redirect("com.demo.Widget" -> "com.dep.Widget"))))
    assertEquals(dep.surfaceFold.refusals.map(_.cause), List(SurfaceFold.Cause.Intrusion))
    assertEquals(dep.effectiveSurface.size, 2, "a refused merge leaves the pre-merge pipeline")
    val f = ManifestAgreement.check(Some(dep), Nil, foreignRoots = true)
    assertEquals(f.map(_.kind), List(Kind.SurfaceIntrusion))
    assert(Kind.SurfaceIntrusion.fatal)
    assert(clue(f.head.detail).contains("com.demo.Widget"))
    assert(clue(f.head.detail).contains("base"))
    assert(clue(Kind.SurfaceIntrusion.classification).contains("§1"))
  }

  test("…and a subject the base DROPS is ALLOWED — nothing stands at that name in its output") {
    // the shape every real consumer of this phase has: the base drops a type outright and a
    // dependent that still uses it re-points its references at a replacement it ships itself
    val dep = base(List(redirect("com.other.A" -> "com.dep.A")), drops = Set("com.demo.Widget"))
      .extendedBy(PortManifest("dep", dropTypes = Set("com.demo.Widget"),
        surface = List(redirect("com.demo.Widget" -> "com.dep.Widget"))))
    assertEquals(dep.surfaceFold.refusals, Nil)
    assertEquals(dep.effectiveSurface.size, 1)
    // …and the only thing left is the base's own drop key not having fired in a run with no
    // program at all, which is not a disagreement
    assertEquals(
      ManifestAgreement.check(Some(dep), Nil, foreignRoots = true, fired = Set("com.demo.Widget")).map(_.kind),
      Nil)
  }

  test("a subject OUTSIDE every base's claim is allowed, and the claim cuts at a separator") {
    val b = PortManifest("base", governs = Set("com.demo"), surface = List(redirect("com.other.A" -> "com.dep.A")))
    // `com.demo` must not cover `com.demonstrate` (§4.56)
    val ok = b.extendedBy(PortManifest("dep", surface = List(redirect("com.demonstrate.W" -> "com.dep.W"))))
    assertEquals(ok.surfaceFold.refusals, Nil)
    val bad = b.extendedBy(PortManifest("dep", surface = List(redirect("com.demo.W" -> "com.dep.W"))))
    assertEquals(bad.surfaceFold.refusals.map(_.cause), List(SurfaceFold.Cause.Intrusion))
  }

  test("INTRUSION without a merge: a phase NO base declares is screened exactly the same") {
    // The hole the screen had: reached only from the `Right(Merged(...))` arm, a dependent that
    // declared a phase its base does not have was appended to the pipeline UNSCREENED — one
    // instance, so no divergence, and no merge, so no `added` to read. It could re-point any type
    // the base emits mechanically, which is the very thing `SurfaceIntrusion` says is fatal.
    val b   = PortManifest("base", governs = Set("com.demo"))   // NO type-redirect of its own
    val dep = b.extendedBy(PortManifest("dep", surface = List(redirect("com.demo.Widget" -> "com.dep.Widget"))))
    assertEquals(dep.effectiveSurface.size, 1, "the phase still runs; the FINDING is what stops the run")
    assertEquals(dep.surfaceFold.refusals.map(_.cause), List(SurfaceFold.Cause.Intrusion))
    val f = ManifestAgreement.check(Some(dep), Nil, foreignRoots = true)
    assertEquals(f.map(_.kind), List(Kind.SurfaceIntrusion))
    assert(Kind.SurfaceIntrusion.fatal)
    assert(clue(f.head.detail).contains("com.demo.Widget"))
    assert(clue(f.head.subject) == "type-redirect")
  }

  test("…and the same screen admits it when the base DROPS the type — ashley's real shape") {
    val b   = PortManifest("base", governs = Set("com.demo"), dropTypes = Set("com.demo.Widget"))
    val dep = b.extendedBy(PortManifest("dep", dropTypes = Set("com.demo.Widget"),
      surface = List(redirect("com.demo.Widget" -> "com.dep.Widget"))))
    assertEquals(dep.surfaceFold.refusals, Nil)
    assertEquals(
      ManifestAgreement.check(Some(dep), Nil, foreignRoots = true, fired = Set("com.demo.Widget")).map(_.kind),
      Nil)
  }

  test("an unmerged intrusion is reported ONCE, and a refused merge is not reported twice") {
    // both channels can see one phase name: the divergence arm (two fingerprints) and the refusal
    // list. The intrusion finding is derived from the second only where the first did not fire.
    val dep = base(List(redirect("com.other.A" -> "com.dep.A")))
      .extendedBy(PortManifest("dep", surface = List(redirect("com.demo.Widget" -> "com.dep.Widget"))))
    assertEquals(
      ManifestAgreement.check(Some(dep), Nil, foreignRoots = true).map(_.kind),
      List(Kind.SurfaceIntrusion))
  }

  test("`subjects` is every key's leading FQN — a rename OWNER counts as one") {
    val p = new TypeRedirectTransform(
      Map("com.other.A" -> "com.dep.A"), Map("com.other.B" -> Map("dispose" -> "close")))
    assertEquals(p.subjects, Set("com.other.A", "com.other.B"))
  }

  test("a base with NO governs claim screens nothing — no claim is not `everything`") {
    val dep = PortManifest("base", surface = List(redirect("com.other.A" -> "com.dep.A")))
      .extendedBy(PortManifest("dep", surface = List(redirect("com.demo.Widget" -> "com.dep.Widget"))))
    assertEquals(dep.surfaceFold.refusals, Nil)
    assertEquals(dep.effectiveSurface.size, 1)
  }

  // -------------------------------------------------------------------------------------------
  // what the fold hands the run
  // -------------------------------------------------------------------------------------------

  test("`ownKeys` records only the FOLDED manifest's contribution — a §1(b) finding must be fixable here") {
    val a = base(List(redirect("com.other.A" -> "com.dep.A")))
    val b = a.extendedBy(PortManifest("mid", surface = List(redirect("com.other.B" -> "com.dep.B"))))
    val c = b.extendedBy(PortManifest("last", surface = List(redirect("com.other.C" -> "com.dep.C"))))
    assertEquals(c.surfaceFold.ownKeys, Map("type-redirect" -> Set("com.other.C")))
    assertEquals(b.surfaceFold.ownKeys, Map("type-redirect" -> Set("com.other.B")))
    // an UNMERGED phase records nothing, which is the "no filter" answer the run needs
    assertEquals(a.surfaceFold.ownKeys, Map.empty[String, Set[String]])
  }

  test("a MIRRORING module that restates the base's table in full SUBSUMES it, and is not `SurfaceMissing`") {
    // `mirroring` inherits nothing, so there is no fold to read — the containment question is asked
    // through the phase's own `mergedWith` instead of a second notion of it (DESIGN.md §8.13).
    val b   = base(List(redirect("com.other.A" -> "com.dep.A")))
    val ext = PortManifest("ext", governs = Set("com.dep"),
      surface = List(redirect("com.other.A" -> "com.dep.A", "com.other.B" -> "com.dep.B"))).mirroring(b)
    assertEquals(ManifestAgreement.check(Some(ext), Nil, foreignRoots = true).map(_.kind), Nil)

    // …and one that restates it WRONGLY is still caught, on the key it got wrong
    val drift = PortManifest("ext",
      surface = List(redirect("com.other.A" -> "com.dep.OTHER"))).mirroring(b)
    assertEquals(
      ManifestAgreement.check(Some(drift), Nil, foreignRoots = true).map(_.kind),
      List(Kind.SurfaceMissing))
  }

  test("a subject is a key's leading FQN, cut at `#` — one body, the `dropMethods` convention") {
    assertEquals(MergeablePolicy.subjectOf("com.demo.W"), "com.demo.W")
    assertEquals(MergeablePolicy.subjectOf("com.demo.W#dispose"), "com.demo.W")
    assertEquals(MergeablePolicy.subjectOf("com.demo.W#dispose() -> close"), "com.demo.W")
  }

  test("one phase INSTANCE inherited through two paths is still folded once") {
    val shared = redirect("com.other.A" -> "com.dep.A")
    val a = PortManifest("a", surface = List(shared))
    val b = a.extendedBy(PortManifest("b", surface = List(shared)))
    assertEquals(b.effectiveSurface.map(_ eq shared), List(true))
    assertEquals(b.surfaceFold.refusals, Nil)
  }

  test("the merged phase is a NEW value — neither input is mutated") {
    val mine  = redirect("com.other.A" -> "com.dep.A")
    val yours = redirect("com.other.B" -> "com.dep.B")
    val dep = PortManifest("base", surface = List(mine))
      .extendedBy(PortManifest("dep", surface = List(yours)))
    assert(!(dep.effectiveSurface.head eq mine))
    assertEquals(mine.redirects, Map("com.other.A" -> "com.dep.A"))
    assertEquals(yours.redirects, Map("com.other.B" -> "com.dep.B"))
  }
