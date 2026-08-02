package balticporter.core

import balticporter.core.ManifestAgreement.Kind
import balticporter.tir.{Phase, RuleScope}
import balticporter.transform.*

/** `ENGINE-LIMITS.md` CT8 — a DEPENDENT declaring the per-declaration half of a base's holder.
  *
  * The phase is `SurfacePolicy` and its holders live in the BASE manifest, correctly: a base and a
  * dependent that thread differently emit signatures that each compile alone and cannot compile
  * together (§1.5). But `sites` and `selfSupplied` keys name DECLARATIONS, and a dependent's
  * boundaries are in the DEPENDENT's own types. Measured in `gdx-vfx`: four counted seams whose own
  * diagnostic said *give the site a `sites` policy*, with no manifest in which to write one, and two
  * of them materialising as scalac errors the correlator classified `EngineGap` — correctly, because
  * the port had nowhere to put the fix.
  *
  * What makes this a SPLIT rather than a merge is the second half of the problem: a `sites` entry
  * belongs to a HOLDER, so naming one would mean restating the holder — and with the context type,
  * the member map, the attachment mode, the read shape and the boundary default all agree-or-refuse,
  * restating the holder means restating the base's whole member map in the dependent's manifest,
  * which is exactly what §1.5 forbids. `ContextHolderExtension` has no field in which the shared
  * half could be restated.
  *
  * Every negative here is negative-tested: this mechanism relaxes `SurfaceDivergence`, so the check
  * it relaxes must keep firing on everything it fired on before (CLAUDE.md §3).
  */
class GlobalsToImplicitsMergeSpec extends munit.FunSuite:

  /** the base's holder, in the shape the reference bundle's config uses. */
  private def holder(f: ContextHolder => ContextHolder = identity) = f(ContextHolder(
    holder  = "com.demo.Gdx",
    context = ContextType.Injected("port.Ctx"),
    members = Map("graphics" -> "graphics", "gl" -> "graphics.gl20"),
    attach  = ContextAttach.Class,
  ))

  private def globals(hs: List[ContextHolder] = Nil, es: List[ContextHolderExtension] = Nil) =
    new GlobalsToImplicitsTransform(hs, es)

  /** a base that CLAIMS a namespace, so the `governs` screen has something to read. */
  private def base(surface: List[Phase], drops: Set[String] = Set.empty) =
    PortManifest("base", governs = Set("com.demo"), dropTypes = drops, surface = surface)

  private def merged(dep: PortManifest): GlobalsToImplicitsTransform =
    dep.effectiveSurface.collectFirst { case t: GlobalsToImplicitsTransform => t }.get

  // -------------------------------------------------------------------------------------------
  // the positive: the vfx shape — a dependent naming sites in its OWN types
  // -------------------------------------------------------------------------------------------

  test("a dependent's EXTENSION folds into the base's holder — the vfx shape, and it merges") {
    // the four seams vfx measured, as the two subjects they are: a class initialiser reading the
    // holder, and a static field constructing a threaded type. Both are `com.dep.*`.
    val b = base(List(globals(List(holder()))))
    val dep = b.extendedBy(PortManifest("dep", surface = List(globals(es = List(
      ContextHolderExtension("com.demo.Gdx", sites = Map(
        "com.dep.gl.GlUtils#<clinit>"        -> ContextSite.LazyInit,
        "com.dep.buffer.FrameBuffer#tmpCam"  -> ContextSite.LazyInit)))))))

    assertEquals(dep.effectiveSurface.map(_.name), List("globals->implicits"))
    assertEquals(dep.surfaceFold.refusals, Nil)
    val eff = merged(dep).effectiveHolders
    assertEquals(clue(eff).size, 1)
    // the SHARED half is the base's, untouched, and the per-declaration half is both
    assertEquals(eff.head.sharedSurface, holder().sharedSurface)
    assertEquals(eff.head.sites.keySet,
      Set("com.dep.gl.GlUtils#<clinit>", "com.dep.buffer.FrameBuffer#tmpCam"))
    // …and nothing is reported: not the divergence, not the base's absorbed phase as missing, and
    // not an intrusion for the dependent naming `com.demo.Gdx` as the holder it extends.
    assertEquals(ManifestAgreement.check(Some(dep), Nil, foreignRoots = true).map(_.kind), Nil)
  }

  test("a dependent's `selfSupplied` entry for its OWN type merges the same way") {
    val b = base(List(globals(List(holder()))))
    val dep = b.extendedBy(PortManifest("dep", surface = List(globals(es = List(
      ContextHolderExtension("com.demo.Gdx",
        selfSupplied = Map("com.dep.FrameBufferSuite" -> "com.dep.TestFixture.ctx()")))))))
    assertEquals(dep.surfaceFold.refusals, Nil)
    assertEquals(merged(dep).effectiveHolders.head.selfSupplied,
      Map("com.dep.FrameBufferSuite" -> "com.dep.TestFixture.ctx()"))
    assertEquals(ManifestAgreement.check(Some(dep), Nil, foreignRoots = true).map(_.kind), Nil)
  }

  test("a dependent may declare a holder of its OWN — union by holder FQN") {
    val b = base(List(globals(List(holder()))))
    val dep = b.extendedBy(PortManifest("dep", surface = List(globals(List(
      ContextHolder("com.dep.Env", ContextType.Minted("com.dep.EnvCtx"), Map("home" -> "home")))))))
    assertEquals(dep.surfaceFold.refusals, Nil)
    assertEquals(clue(merged(dep).effectiveHolders).map(_.holder), List("com.demo.Gdx", "com.dep.Env"))
    assertEquals(ManifestAgreement.check(Some(dep), Nil, foreignRoots = true).map(_.kind), Nil)
  }

  test("D1: the merge does not reach the BASE's own effective surface") {
    val b      = base(List(globals(List(holder()))))
    val before = b.effectiveSurface.map(PortManifest.fingerprint)
    val dep = b.extendedBy(PortManifest("dep", surface = List(globals(es = List(
      ContextHolderExtension("com.demo.Gdx",
        sites = Map("com.dep.U#<clinit>" -> ContextSite.LazyInit)))))))
    assertEquals(b.effectiveSurface.map(PortManifest.fingerprint), before)
    assertNotEquals(dep.effectiveSurface.map(PortManifest.fingerprint), before)
    assert(clue(dep.surfaceFold.absorbed).contains(before.head))
  }

  test("the merged fingerprint MOVES, and equals the same policy stated INLINE") {
    // §8.13's third obligation, plus the containment test a `mirroring` module is held to: a module
    // that writes one instance holding both halves must fingerprint the same as base-plus-extension,
    // or it would be `SurfaceMissing` for a phase it demonstrably runs.
    val inline = globals(List(holder(_.copy(sites = Map("com.dep.U#<clinit>" -> ContextSite.LazyInit)))))
    val b   = base(List(globals(List(holder()))))
    val dep = b.extendedBy(PortManifest("dep", surface = List(globals(es = List(
      ContextHolderExtension("com.demo.Gdx",
        sites = Map("com.dep.U#<clinit>" -> ContextSite.LazyInit)))))))
    assertEquals(merged(dep).surfaceFingerprint, inline.surfaceFingerprint)
    assertNotEquals(merged(dep).surfaceFingerprint, globals(List(holder())).surfaceFingerprint)
  }

  // -------------------------------------------------------------------------------------------
  // the negatives — the SHARED half is not a dependent's to restate differently
  // -------------------------------------------------------------------------------------------

  test("REFUSED: a dependent that changes the ATTACHMENT MODE") {
    val dep = base(List(globals(List(holder()))))
      .extendedBy(PortManifest("dep",
        surface = List(globals(List(holder(_.copy(attach = ContextAttach.Method)))))))
    assertEquals(dep.effectiveSurface.size, 2, "a refused pair stays in the pipeline")
    assertEquals(dep.surfaceFold.refusals.map(_.cause), List(SurfaceFold.Cause.Conflict))
    val f = ManifestAgreement.check(Some(dep), Nil, foreignRoots = true)
    assertEquals(f.map(_.kind), List(Kind.SurfaceDivergence))
    assert(Kind.SurfaceDivergence.fatal)
    assert(clue(f.head.detail).contains("SHARED SURFACE differs"))
    assert(clue(f.head.detail).contains("com.demo.Gdx"))
  }

  test("REFUSED: a dependent that ADDS a member mapping — it re-points reads it does not own") {
    val dep = base(List(globals(List(holder()))))
      .extendedBy(PortManifest("dep", surface = List(globals(List(
        holder(h => h.copy(members = h.members + ("files" -> "files"))))))))
    assertEquals(dep.surfaceFold.refusals.map(_.cause), List(SurfaceFold.Cause.Conflict))
    assertEquals(ManifestAgreement.check(Some(dep), Nil, foreignRoots = true).map(_.kind),
      List(Kind.SurfaceDivergence))
  }

  test("REFUSED: a dependent that names a different CONTEXT TYPE") {
    val dep = base(List(globals(List(holder()))))
      .extendedBy(PortManifest("dep", surface = List(globals(List(
        holder(_.copy(context = ContextType.Minted("com.dep.Ctx"))))))))
    assertEquals(dep.surfaceFold.refusals.map(_.cause), List(SurfaceFold.Cause.Conflict))
  }

  test("REFUSED: two `sites` policies for ONE site") {
    val b = base(List(globals(List(holder(_.copy(
      sites = Map("com.dep.U#<clinit>" -> ContextSite.LazyInit)))))))
    val dep = b.extendedBy(PortManifest("dep", surface = List(globals(List(holder(_.copy(
      sites = Map("com.dep.U#<clinit>" -> ContextSite.ResidualGlobal))))))))
    assertEquals(dep.surfaceFold.refusals.map(_.cause), List(SurfaceFold.Cause.Conflict))
    val f = ManifestAgreement.check(Some(dep), Nil, foreignRoots = true)
    assertEquals(f.map(_.kind), List(Kind.SurfaceDivergence))
    assert(clue(f.head.detail).contains("lazy-init"))
    assert(clue(f.head.detail).contains("residual-global"))
  }

  test("REFUSED: two CONTEXT SOURCES for one self-supplied type") {
    val b = base(List(globals(List(holder(_.copy(
      selfSupplied = Map("com.dep.S" -> "com.dep.A.ctx()")))))))
    val dep = b.extendedBy(PortManifest("dep", surface = List(globals(List(holder(_.copy(
      selfSupplied = Map("com.dep.S" -> "com.dep.B.ctx()"))))))))
    assertEquals(dep.surfaceFold.refusals.map(_.cause), List(SurfaceFold.Cause.Conflict))
    val f = ManifestAgreement.check(Some(dep), Nil, foreignRoots = true)
    assert(clue(f.head.detail).contains("self-supplied"))
    assert(clue(f.head.detail).contains("com.dep.B.ctx()"))
  }

  test("…and the SAME entry spelled twice is agreement, not drift") {
    val b = base(List(globals(List(holder(_.copy(
      sites = Map("com.dep.U#<clinit>" -> ContextSite.LazyInit)))))))
    val dep = b.extendedBy(PortManifest("dep", surface = List(globals(List(holder(_.copy(
      sites = Map("com.dep.U#<clinit>" -> ContextSite.LazyInit))))))))
    assertEquals(dep.surfaceFold.refusals, Nil)
    assertEquals(dep.effectiveSurface.size, 1)
    assertEquals(ManifestAgreement.check(Some(dep), Nil, foreignRoots = true).map(_.kind), Nil)
  }

  // -------------------------------------------------------------------------------------------
  // the `governs` screen — the rule CT8 asked for by name
  // -------------------------------------------------------------------------------------------

  test("INTRUSION: a dependent whose `sites` key names a BASE declaration is FATAL") {
    // the shape the merge newly permits and the screen exists for: the base emitted
    // `com.demo.Utils#<clinit>` threaded, and a dependent quietly defers it — two ports that each
    // compile alone and cannot compile together.
    val dep = base(List(globals(List(holder()))))
      .extendedBy(PortManifest("dep", surface = List(globals(es = List(
        ContextHolderExtension("com.demo.Gdx",
          sites = Map("com.demo.Utils#<clinit>" -> ContextSite.LazyInit)))))))
    assertEquals(dep.surfaceFold.intrusions.map(_.subject), List("com.demo.Utils"))
    val f = ManifestAgreement.check(Some(dep), Nil, foreignRoots = true)
    assertEquals(f.map(_.kind), List(Kind.SurfaceIntrusion))
    assert(Kind.SurfaceIntrusion.fatal)
    assert(clue(f.head.detail).contains("com.demo.Utils"))
  }

  test("INTRUSION: a dependent declaring the phase its base does NOT have is screened whole") {
    // the no-counterpart arm — one instance, no divergence, no merge, and every type the base emits
    // mechanically available to re-point. `subjects` is what lets the screen run here at all.
    val dep = base(Nil).extendedBy(PortManifest("dep", surface = List(globals(List(holder())))))
    assertEquals(dep.surfaceFold.intrusions.map(_.subject), List("com.demo.Gdx"))
    val f = ManifestAgreement.check(Some(dep), Nil, foreignRoots = true)
    assertEquals(f.map(_.kind), List(Kind.SurfaceIntrusion))
    assert(clue(f.head.detail).contains("com.demo.Gdx"))
  }

  test("…and the base's OWN holder FQN is never an intrusion for a module that extends it") {
    // the base already holds `com.demo.Gdx` as a subject, so a merge reports it as nothing ADDED.
    // Without that, every extension of an inherited holder would be refused — which is CT8 closed
    // in name and open in fact.
    val b = base(List(globals(List(holder()))))
    val dep = b.extendedBy(PortManifest("dep", surface = List(globals(es = List(
      ContextHolderExtension("com.demo.Gdx",
        sites = Map("com.dep.U#<clinit>" -> ContextSite.LazyInit)))))))
    assert(clue(globals(List(holder())).subjects).contains("com.demo.Gdx"))
    assertEquals(dep.surfaceFold.refusals, Nil)
  }

  // -------------------------------------------------------------------------------------------
  // a DANGLING extension — the never-fired shape no binder can see
  // -------------------------------------------------------------------------------------------

  test("an extension naming a holder NOTHING declares is a counted `Malformed`, not a no-op") {
    // Its own keys would bind perfectly against a program that has them; what is missing is the
    // HOLDER, which is not a program fact at all. Derived from the policy, so a phase that never ran
    // reports it too.
    val p  = globals(es = List(ContextHolderExtension("com.demo.Gdx",
      sites = Map("com.dep.U#<clinit>" -> ContextSite.LazyInit))))
    val fs = p.policyReport.findings.filter(_.issue == PolicyIssue.Malformed)
    assertEquals(clue(fs).map(_.key), List("com.demo.Gdx"), fs.toString)
    assert(fs.head.detail.contains("neither it nor any of its bases declares"))
    assertEquals(p.effectiveHolders, Nil)
    // …and once the base is merged in, the extension is no longer dangling and the finding is gone.
    val dep = base(List(globals(List(holder())))).extendedBy(PortManifest("dep", surface = List(p)))
    assertEquals(merged(dep).policyReport.of(PolicyIssue.Malformed), Nil)
  }

  test("a dangling extension does not fingerprint as an empty policy") {
    // or a dependent that contributes only extensions would be indistinguishable from a phase with
    // no policy at all, and `PortManifest.fingerprint` is what the freshness comparison reads.
    val p = globals(es = List(ContextHolderExtension("com.demo.Gdx",
      sites = Map("com.dep.U#<clinit>" -> ContextSite.LazyInit))))
    assertNotEquals(p.surfaceFingerprint, globals().surfaceFingerprint)
  }

  // -------------------------------------------------------------------------------------------
  // the composing halves — `promoteToClass` and `scope`
  // -------------------------------------------------------------------------------------------

  test("a differing SCOPE is part of the shared half, so it refuses rather than composing quietly") {
    val dep = base(List(globals(List(holder()))))
      .extendedBy(PortManifest("dep", surface = List(globals(List(
        holder(_.copy(scope = RuleScope.Only(Set("com.dep")))))))))
    assertEquals(dep.surfaceFold.refusals.map(_.cause), List(SurfaceFold.Cause.Conflict))
    assert(clue(dep.surfaceFold.refusals.head.why).contains("SHARED SURFACE"))
  }

  test("NOT a `GlobalsToImplicitsTransform` at all is refused with a sentence, not a crash") {
    val p = globals(List(holder()))
    assert(clue(p.mergedWith(new balticporter.transform.MutableParamsTransform)).isLeft)
  }
