package balticporter.core

import balticporter.core.ManifestAgreement.Kind
import balticporter.tir.{Phase, RuleScope}
import balticporter.transform.*

/** `ENGINE-LIMITS.md` CT8 — a DEPENDENT declaring the per-declaration half of a base's holder. */
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

  test("REFUSED: two CACHED ACCESSOR NAMES for one type") {
    // The accessor name is emitted SURFACE and it is what a `selfSupplied` expression READS, so two
    // answers is two ports of which one compiles against whatever expression the other wrote.
    // NEGATIVE: drop `cacheClash` from `mergedWith` and the two entries UNION — the later map wins,
    // silently, and which one that is depends on which manifest was read first.
    val b = base(List(globals(List(holder(_.copy(cache = Map("com.dep.Boot" -> "aCtx")))))))
    val dep = b.extendedBy(PortManifest("dep", surface = List(globals(List(holder(_.copy(
      cache = Map("com.dep.Boot" -> "bCtx"))))))))
    assertEquals(dep.surfaceFold.refusals.map(_.cause), List(SurfaceFold.Cause.Conflict))
    val f = ManifestAgreement.check(Some(dep), Nil, foreignRoots = true)
    assertEquals(f.map(_.kind), List(Kind.SurfaceDivergence))
    assert(clue(f.head.detail).contains("CACHE"))
    assert(clue(f.head.detail).contains("bCtx"))
  }

  test("a dependent's `cache` entry for its OWN type merges, and the fingerprint says so") {
    // CT8's own shape at the fifth key: the holder is the base's and the TYPE is the dependent's, so
    // there is no manifest but the dependent's in which the entry could be written.
    val b   = base(List(globals(List(holder()))))
    val dep = b.extendedBy(PortManifest("dep", surface = List(globals(es = List(
      ContextHolderExtension("com.demo.Gdx", cache = Map("com.dep.Boot" -> "depCtx")))))))
    assertEquals(dep.surfaceFold.refusals, Nil)
    assertEquals(ManifestAgreement.check(Some(dep), Nil, foreignRoots = true).map(_.kind), Nil)
    assert(clue(merged(dep).surfaceFingerprint).contains("com.dep.Boot^depCtx"))
    // …and it is NOT the base's answer: the base still fingerprints without it (§1.5's D1).
    assert(!clue(merged(b).surfaceFingerprint).contains("depCtx"))
  }

  test("a holder with NO `cache` fingerprints exactly as it did before the key existed") {
    // §1(b)'s no-op rule read at the FINGERPRINT: an unused per-declaration key must not move
    // `policy=` in twenty published port maps on the day it is added. The literal is the shape this
    // renderer had before `cache`, so the assertion cannot drift with the code it guards.
    // NEGATIVE: render the segment unconditionally and this reads `…|com.dep.S=>…||`.
    val h = holder(_.copy(selfSupplied = Map("com.dep.S" -> "com.dep.A.ctx()")))
    assertEquals(h.fingerprint,
      s"${h.sharedSurface}||com.dep.S=>${"com.dep.A.ctx()".hashCode.toHexString}|")
    assertEquals(ContextHolderExtension("com.demo.Gdx").fingerprint, "com.demo.Gdx|+||")
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
