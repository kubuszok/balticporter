package balticporter.core

import balticporter.core.ManifestAgreement.Kind
import balticporter.tir.{Phase, RuleScope}
import balticporter.transform.{ClassTableTransform, NullabilityTransform, TypeRedirectTransform}

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
    val b = a.extendedBy(PortManifest("mid", governs = Set("com.mid"), surface = List(redirect("com.other.B" -> "com.dep.B"))))
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

  test("REFUSED: a BARE key and an OVERLOAD key of one member are one member, not two") {
    // `dispose` and `dispose()` are two map keys and ONE member — the bare form is every overload,
    // the nilary form is one of them. Compared as raw strings the merge succeeded, and the drift
    // then arrived at `MemberRenamer` as its non-fatal two-claimants refusal: a `PolicyIssue`
    // where the merge contract owes a fatal `SurfaceDivergence`.
    val b = base(List(new TypeRedirectTransform(
      Map("com.other.A" -> "com.dep.A"), Map("com.other.A" -> Map("dispose" -> "close")))))
    val dep = b.extendedBy(PortManifest("dep", surface = List(new TypeRedirectTransform(
      Map("com.other.A" -> "com.dep.A"), Map("com.other.A" -> Map("dispose()" -> "shutdown"))))))
    assertEquals(dep.surfaceFold.refusals.map(_.cause), List(SurfaceFold.Cause.Conflict))
    val f = ManifestAgreement.check(Some(dep), Nil, foreignRoots = true)
    assertEquals(f.map(_.kind), List(Kind.SurfaceDivergence))
    assert(Kind.SurfaceDivergence.fatal)
    assert(clue(f.head.detail).contains("ONE member"))
    assert(clue(f.head.detail).contains("shutdown"))
    assert(clue(f.head.detail).contains("close"))
  }

  test("…and two spellings of one member agreeing on the TARGET is not a conflict") {
    val b = base(List(new TypeRedirectTransform(
      Map("com.other.A" -> "com.dep.A"), Map("com.other.A" -> Map("dispose" -> "close")))))
    val dep = b.extendedBy(PortManifest("dep", surface = List(new TypeRedirectTransform(
      Map("com.other.A" -> "com.dep.A"), Map("com.other.A" -> Map("dispose()" -> "close"))))))
    assertEquals(dep.surfaceFold.refusals, Nil)
    assertEquals(ManifestAgreement.check(Some(dep), Nil, foreignRoots = true).map(_.kind), Nil)
  }

  test("a MALFORMED member segment still compares — the refusal may not depend on a parse") {
    // an unparseable segment falls back to its own text, so two modules disagreeing about it are
    // still refused rather than merged behind a `None`.
    val b = base(List(new TypeRedirectTransform(
      Map("com.other.A" -> "com.dep.A"), Map("com.other.A" -> Map("dispose<T>" -> "close")))))
    val dep = b.extendedBy(PortManifest("dep", surface = List(new TypeRedirectTransform(
      Map("com.other.A" -> "com.dep.A"), Map("com.other.A" -> Map("dispose<T>" -> "shutdown"))))))
    assertEquals(dep.surfaceFold.refusals.map(_.cause), List(SurfaceFold.Cause.Conflict))
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

  test("two EQUAL instances of a contract-less phase COLLAPSE TO ONE, and report nothing") {
    // The pre-CT9 pipeline keyed phases by NAME and ran one of two; ordering INSTANCES turned the
    // same append into "the phase runs TWICE over one program", which is a promise no implementor
    // of a contract-less phase ever made. Proving them equal is what licenses the dedup, so the
    // dedup is where the proof lands — and `effectiveSurface.size` is the assertion that sees it.
    val table = Map("com.demo.W#of" -> "com.demo.T#classFor")
    val b   = base(List(new ClassTableTransform(table)))
    val dep = b.extendedBy(PortManifest("dep", surface = List(new ClassTableTransform(table))))
    assertEquals(dep.effectiveSurface.size, 1, "ONE instance runs — the pre-CT9 semantics, restored")
    assertEquals(dep.effectiveSurface.map(PortManifest.fingerprint), fps(b),
                 "…and it is the BASE's, at the base's position: a merge changes a table, never an order")
    assertEquals(dep.surfaceFold.refusals, Nil, "equal policy is not drift, so it explains nothing")
    assertEquals(ManifestAgreement.check(Some(dep), Nil, foreignRoots = true), Nil)
  }

  test("…and the dedup does not make the base's phase `SurfaceMissing` — one instance IS the base's") {
    val table = Map("com.demo.W#of" -> "com.demo.T#classFor")
    val b   = base(List(new ClassTableTransform(table)))
    val dep = b.extendedBy(PortManifest("dep", surface = List(new ClassTableTransform(table))))
    assertEquals(ManifestAgreement.check(Some(dep), Nil, foreignRoots = true).map(_.kind), Nil)
  }

  // -------------------------------------------------------------------------------------------
  // …and a phase that is not even a `SurfacePolicy` cannot be COMPARED, so equality is not assumed
  // -------------------------------------------------------------------------------------------

  /** a parameterised phase that implements NEITHER contract — the shape whose fingerprint is its
    * NAME, so two configurations of it render identically. Declared here rather than borrowed from
    * a production phase: which engine phase happens to lack `SurfacePolicy` is a fact that should
    * change (and F2 changed one), and a spec pinned to it would silently stop testing this. */
  private final class Unreadable(val table: Map[String, String]) extends Phase:
    def name: String = "unreadable"

  test("UNVERIFIABLE: two instances of a phase with no `SurfacePolicy` are FATAL, however configured") {
    // The blind spot `PortManifest.fingerprint` documents, reached through the fold: these two
    // tables differ and the rendering cannot say so. Deduping would drop one policy silently —
    // CT9 Face B under a new name — so the engine refuses instead of guessing.
    val dep = base(List(new Unreadable(Map("a" -> "1"))))
      .extendedBy(PortManifest("dep", surface = List(new Unreadable(Map("a" -> "2")))))
    assertEquals(dep.effectiveSurface.size, 2)
    assertEquals(dep.surfaceFold.refusals.map(_.cause), List(SurfaceFold.Cause.Unverifiable))
    val f = ManifestAgreement.check(Some(dep), Nil, foreignRoots = true)
    assertEquals(f.map(_.kind), List(Kind.SurfaceDivergence))
    assert(clue(f.head.detail).contains("Equality cannot be verified"))
    assert(f.head.kind.fatal, "…and it stops the run: `surfaceGate` keeps only the fatal ones")
    assertEquals(ManifestAgreement.surfaceGate(Some(dep)).map(_.kind), List(Kind.SurfaceDivergence))
  }

  test("…and two IDENTICALLY-configured instances of one are refused just the same") {
    // The point of the entry: the engine cannot TELL that these agree. Reporting nothing here is
    // reporting nothing for every unreadable pair, since every unreadable pair looks like this one.
    val table = Map("a" -> "1")
    val dep = base(List(new Unreadable(table)))
      .extendedBy(PortManifest("dep", surface = List(new Unreadable(table))))
    assertEquals(dep.surfaceFold.refusals.map(_.cause), List(SurfaceFold.Cause.Unverifiable))
    val f = ManifestAgreement.check(Some(dep), Nil, foreignRoots = true)
    assertEquals(f.map(_.kind), List(Kind.SurfaceDivergence))
    assert(clue(f.head.detail).contains("EQUAL AS RENDERED"),
           "the message says the two fingerprints matched and that this is not evidence")
  }

  test("ONE instance of an unreadable phase is untouched — this is a PAIR rule, not a phase ban") {
    val dep = base(Nil).extendedBy(PortManifest("dep", surface = List(new Unreadable(Map("a" -> "1")))))
    assertEquals(dep.effectiveSurface.size, 1)
    assertEquals(dep.surfaceFold.refusals, Nil)
    assertEquals(ManifestAgreement.check(Some(dep), Nil, foreignRoots = true), Nil)
  }

  // -------------------------------------------------------------------------------------------
  // the `governs` screen
  // -------------------------------------------------------------------------------------------

  test("INTRUSION: a merged-in subject the base EMITS is fatal, and is not a plain divergence") {
    val dep = base(List(redirect("com.other.A" -> "com.dep.A")))
      .extendedBy(PortManifest("dep", surface = List(redirect("com.demo.Widget" -> "com.dep.Widget"))))
    assertEquals(dep.surfaceFold.intrusions.map(_.subject), List("com.demo.Widget"))
    // the merge STANDS — an intrusion is a statement about the base's OUTPUT, not a failure to
    // compose two policies, and a confirmed one stops the run at the gate (DESIGN.md §8.13)
    assertEquals(dep.effectiveSurface.size, 1)
    assertEquals(dep.surfaceFold.refusals, Nil)
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

  // -------------------------------------------------------------------------------------------
  // …and the admission is "NOTHING STANDS AT THAT NAME", which a drop alone does not say
  // -------------------------------------------------------------------------------------------

  /** an injection root holding one ready-made file at `rel`. */
  private def injectRoot(rel: String): java.nio.file.Path =
    val root = java.nio.file.Files.createTempDirectory("inject")
    val p    = root.resolve(rel)
    java.nio.file.Files.createDirectories(p.getParent)
    java.nio.file.Files.writeString(p, "package x\nclass Y\n")
    root

  test("a drop the base REPLACES is an intrusion — the injected shim IS shared surface") {
    // §1.5's asymmetry read correctly: a drop and its replacement are two decisions, and the second
    // one puts a file at that FQN. Re-pointing references at a type of this module's own would
    // compile alone and could not compile against the base — the very failure the screen is for.
    val b = PortManifest("base", governs = Set("com.demo"), dropTypes = Set("com.demo.Widget"),
      inject = List(injectRoot("com/demo/Widget.scala")),
      surface = List(redirect("com.other.A" -> "com.dep.A")))
    val dep = b.extendedBy(PortManifest("dep", dropTypes = Set("com.demo.Widget"),
      surface = List(redirect("com.demo.Widget" -> "com.dep.Widget"))))
    assertEquals(dep.surfaceFold.intrusions.map(_.subject), List("com.demo.Widget"))
    val f = ManifestAgreement.check(Some(dep), Nil, foreignRoots = true, fired = Set("com.demo.Widget"))
    assertEquals(f.map(_.kind), List(Kind.SurfaceIntrusion))
    assert(clue(f.head.detail).contains("DROPS and REPLACES"))
  }

  test("…and the SAME drop with no injection is admitted — nothing stands at the name") {
    val b = PortManifest("base", governs = Set("com.demo"), dropTypes = Set("com.demo.Widget"),
      inject = List(injectRoot("com/demo/Other.scala")), // a replacement for a DIFFERENT type
      surface = List(redirect("com.other.A" -> "com.dep.A")))
    val dep = b.extendedBy(PortManifest("dep", dropTypes = Set("com.demo.Widget"),
      surface = List(redirect("com.demo.Widget" -> "com.dep.Widget"))))
    assertEquals(dep.surfaceFold.refusals, Nil)
    assertEquals(dep.effectiveSurface.size, 1)
  }

  test("the injection is matched in the EMITTED namespace — a renaming base is the normal case") {
    // the two sides are in different namespaces: the drop key is upstream, the shim's FQN is where
    // the file sits in the port. Compared directly, this screen would never fire on a renaming
    // port — §4.56, the failure `PortMap`'s `Substituted` was bitten by.
    val b = PortManifest("base", governs = Set("com.demo"), dropTypes = Set("com.demo.Widget"),
      packageRenames = Map("com.demo" -> "sge"),
      inject = List(injectRoot("sge/Widget.scala")),
      surface = List(redirect("com.other.A" -> "com.dep.A")))
    assert(b.shipsInjectionAt("com.demo.Widget"), "upstream key, emitted file")
    val dep = b.extendedBy(PortManifest("dep", dropTypes = Set("com.demo.Widget"),
      packageRenames = Map("com.demo" -> "sge"),
      surface = List(redirect("com.demo.Widget" -> "com.dep.Widget"))))
    assertEquals(dep.surfaceFold.intrusions.map(_.subject), List("com.demo.Widget"))
  }

  test("a base whose injection ROOT does not exist ships nothing — the run's own answer") {
    val b = PortManifest("base", governs = Set("com.demo"), dropTypes = Set("com.demo.Widget"),
      inject = List(java.nio.file.Path.of("/no/such/overrides")),
      surface = List(redirect("com.other.A" -> "com.dep.A")))
    assert(!b.shipsInjectionAt("com.demo.Widget"))
    val dep = b.extendedBy(PortManifest("dep", dropTypes = Set("com.demo.Widget"),
      surface = List(redirect("com.demo.Widget" -> "com.dep.Widget"))))
    assertEquals(dep.surfaceFold.refusals, Nil)
  }

  test("a subject OUTSIDE every base's claim is allowed, and the claim cuts at a separator") {
    val b = PortManifest("base", governs = Set("com.demo"), surface = List(redirect("com.other.A" -> "com.dep.A")))
    // `com.demo` must not cover `com.demonstrate` (§4.56)
    val ok = b.extendedBy(PortManifest("dep", surface = List(redirect("com.demonstrate.W" -> "com.dep.W"))))
    assertEquals(ok.surfaceFold.refusals, Nil)
    val bad = b.extendedBy(PortManifest("dep", surface = List(redirect("com.demo.W" -> "com.dep.W"))))
    assertEquals(bad.surfaceFold.intrusions.map(_.subject), List("com.demo.W"))
  }

  test("INTRUSION without a merge: a phase NO base declares is screened exactly the same") {
    // The hole the screen had: reached only from the `Right(Merged(...))` arm, a dependent that
    // declared a phase its base does not have was appended to the pipeline UNSCREENED — one
    // instance, so no divergence, and no merge, so no `added` to read. It could re-point any type
    // the base emits mechanically, which is the very thing `SurfaceIntrusion` says is fatal.
    val b   = PortManifest("base", governs = Set("com.demo"))   // NO type-redirect of its own
    val dep = b.extendedBy(PortManifest("dep", surface = List(redirect("com.demo.Widget" -> "com.dep.Widget"))))
    assertEquals(dep.effectiveSurface.size, 1, "the phase still runs; the FINDING is what stops the run")
    assertEquals(dep.surfaceFold.intrusions.map(_.subject), List("com.demo.Widget"))
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

  // -------------------------------------------------------------------------------------------
  // …and the criterion is what the base EMITS, which only its PUBLISHED MAP can say
  // (ENGINE-LIMITS.md CT9 Face A, DESIGN.md §8.13)
  // -------------------------------------------------------------------------------------------

  /** the base, as a run FOUND it: a manifest and a usable published map. */
  private def published(b: PortManifest, entries: PortMap.Entry*): ManifestAgreement.BasePort =
    ManifestAgreement.BasePort(b, Some(PortMap.Map0(b.name, "engine", entries.toList)), "run-latest")

  private def emits(fqn: String)   = PortMap.Entry("type", fqn, fqn, PortMap.Disposition.Ported)
  private def drops(fqn: String)   = PortMap.Entry("type", fqn, "", PortMap.Disposition.Dropped)
  private def replaces(fqn: String, at: String) =
    PortMap.Entry("type", fqn, at, PortMap.Disposition.Substituted)

  /** a dependent whose OWN declaration lives inside the base's claimed namespace — the whole of
    * CT9 Face A. `com.demo.WidgetTest` is a test module's suite beside `com.demo.Widget`. */
  private def intruding(subject: String) =
    val b = PortManifest("base", governs = Set("com.demo"), surface = List(redirect("com.other.A" -> "com.dep.A")))
    b -> b.extendedBy(PortManifest("dep", surface = List(redirect(subject -> "com.dep.X"))))

  test("a key at an FQN the base's map has NO ENTRY for is ADMITTED — the base declares nothing there") {
    // libGDX's own suites are declared INSIDE `com.badlogic.gdx`, so no prefix separates the two
    // modules and the base never parses the test tree at all. A drop is a statement about a type the
    // base HAS; this is a name it has never heard of, and the manifest cannot tell them apart.
    val (b, dep) = intruding("com.demo.WidgetTest")
    assertEquals(dep.surfaceFold.intrusions.map(_.subject), List("com.demo.WidgetTest"),
                 "the fold still names it — it is a CANDIDATE, screened by the layer holding the map")
    val f = ManifestAgreement.check(Some(dep), Nil, foreignRoots = true,
                                    ports = List(published(b, emits("com.demo.Widget"))))
    assertEquals(f.map(_.kind), Nil)
    assertEquals(dep.effectiveSurface.size, 1, "…and the merge stands, which is the point")
  }

  test("…and a key at an FQN the base's map EMITS is still REFUSED, with the map as the evidence") {
    val (b, dep) = intruding("com.demo.Widget")
    val f = ManifestAgreement.check(Some(dep), Nil, foreignRoots = true,
                                    ports = List(published(b, emits("com.demo.Widget"))))
    assertEquals(f.map(_.kind), List(Kind.SurfaceIntrusion))
    assert(Kind.SurfaceIntrusion.fatal)
    assert(clue(f.head.detail).contains("published map emits it"))
  }

  test("a map entry that is DROPPED admits — `nothing stands at that name`, read off the OUTPUT") {
    val (b, dep) = intruding("com.demo.Widget")
    assertEquals(
      ManifestAgreement.check(Some(dep), Nil, foreignRoots = true,
                              ports = List(published(b, drops("com.demo.Widget")))).map(_.kind),
      Nil)
  }

  test("…and a SUBSTITUTED one refuses: an injected replacement IS shared surface") {
    val (b, dep) = intruding("com.demo.Widget")
    assertEquals(
      ManifestAgreement.check(Some(dep), Nil, foreignRoots = true,
                              ports = List(published(b, replaces("com.demo.Widget", "sge.Widget")))).map(_.kind),
      List(Kind.SurfaceIntrusion))
  }

  test("NO USABLE MAP falls back to re-derivation — the answer that shipped, and it says so") {
    // D1's rule: `BasePort.map` is empty for a map never published AND for one proven stale, and the
    // two take the same path. The fallback REFUSES, which is the safe direction for a screen, and it
    // is reported as weaker beside this finding rather than silently taken.
    val (b, dep) = intruding("com.demo.WidgetTest")
    val f = ManifestAgreement.check(Some(dep), Nil, foreignRoots = true,
                                    ports = List(ManifestAgreement.BasePort(b)))
    assertEquals(f.map(_.kind), List(Kind.SurfaceIntrusion, Kind.BaseMapMissing))
    assert(clue(f.head.detail).contains("no usable port map"))
    assert(!Kind.BaseMapMissing.fatal, "the operational half is loud, not fatal")
  }

  test("…and a run that looked up no maps at all behaves identically — a base port asks nothing") {
    val (_, dep) = intruding("com.demo.WidgetTest")
    assertEquals(ManifestAgreement.check(Some(dep), Nil, foreignRoots = true).map(_.kind),
                 List(Kind.SurfaceIntrusion))
  }

  test("ONE finding per phase, whatever the number of subjects — one manifest mistake, one row") {
    val b = PortManifest("base", governs = Set("com.demo"), surface = List(redirect("com.other.A" -> "com.dep.A")))
    val dep = b.extendedBy(PortManifest("dep", surface = List(
      redirect("com.demo.Widget" -> "com.dep.W", "com.demo.Gadget" -> "com.dep.G"))))
    val f = ManifestAgreement.check(Some(dep), Nil, foreignRoots = true,
                                    ports = List(published(b, emits("com.demo.Widget"), emits("com.demo.Gadget"))))
    assertEquals(f.map(_.kind), List(Kind.SurfaceIntrusion))
    assert(clue(f.head.detail).contains("2 such subjects"))
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
    val b = a.extendedBy(PortManifest("mid", governs = Set("com.mid"), surface = List(redirect("com.other.B" -> "com.dep.B"))))
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

  // -------------------------------------------------------------------------------------------
  // NULLABILITY — the second phase to declare a merge, and the first whose policy is not a MAP
  //
  // Its three tables compose three different ways, which is the concrete case for `MergeablePolicy`
  // being a contract rather than an engine-side union: the annotation set unions, the target must
  // AGREE, and the scope unions its ENTRIES while its REGION moves in opposite directions for the
  // two constructors.
  // -------------------------------------------------------------------------------------------

  private def nullability(annotations: Set[String],
                          target: NullabilityTransform.Target = NullabilityTransform.Target.Union,
                          scope: RuleScope = RuleScope.Everywhere()): NullabilityTransform =
    new NullabilityTransform(annotations, target, scope)

  private def nulls(m: PortManifest): NullabilityTransform =
    m.effectiveSurface.collectFirst { case n: NullabilityTransform => n }.get

  test("the DEPENDENT-ADDS-AN-ANNOTATION shape: one instance, both annotation sets, base's position") {
    // the shape a second module actually needs — the base consumes its own marker, the dependent's
    // own sources are marked with a third party's, and neither belongs in the other's manifest
    val b = base(List(redirect("com.other.A" -> "com.dep.A"),
                      nullability(Set("com.demo.Null"))))
    val dep = b.extendedBy(PortManifest("dep",
      surface = List(nullability(Set("org.third.Nullable")))))
    assertEquals(dep.effectiveSurface.map(_.name), List("type-redirect", "nullability"))
    assertEquals(nulls(dep).annotations, Set("com.demo.Null", "org.third.Nullable"))
    assertEquals(dep.surfaceFold.refusals, Nil)
    assertEquals(ManifestAgreement.check(Some(dep), Nil, foreignRoots = true).map(_.kind), Nil)
    // …and the merged table PUBLISHES as different, which is `SurfaceFold`'s third obligation
    assertNotEquals(PortManifest.fingerprint(nulls(dep)), PortManifest.fingerprint(nulls(b)))
  }

  test("D1 holds for nullability too: the base's own effective surface never sees the merge") {
    val b      = base(List(nullability(Set("com.demo.Null"))))
    val before = fps(b)
    val dep    = b.extendedBy(PortManifest("dep", surface = List(nullability(Set("org.third.Nullable")))))
    assertEquals(fps(b), before)
    assertEquals(nulls(b).annotations, Set("com.demo.Null"))
    assert(clue(dep.surfaceFold.absorbed).contains(before.head))
  }

  test("`Everywhere` unions its EXCEPTS — every entry either module wrote is honoured") {
    val b = base(List(nullability(Set("com.demo.Null"), scope = RuleScope.Everywhere(Set("com.demo.Box")))))
    val dep = b.extendedBy(PortManifest("dep", surface = List(
      nullability(Set("org.third.Nullable"), scope = RuleScope.Everywhere(Set("org.third.Bag"))))))
    assertEquals(nulls(dep).scope, RuleScope.Everywhere(Set("com.demo.Box", "org.third.Bag")))
    assertEquals(dep.surfaceFold.refusals, Nil)
  }

  test("…and `Only` unions its INCLUDES — the same set operation, the OPPOSITE region") {
    // the point of the contract: an entry EXCLUDES under one constructor and INCLUDES under the
    // other, so honouring both inputs' entries makes the covered region shrink in the first case
    // and grow in the second. A merge written as "compose the region" is right for one and silently
    // wrong for the other.
    val b = base(List(nullability(Set("com.demo.Null"), scope = RuleScope.Only(Set("com.demo.Box")))))
    val dep = b.extendedBy(PortManifest("dep", surface = List(
      nullability(Set("com.demo.Null"), scope = RuleScope.Only(Set("org.third.Bag"))))))
    assertEquals(nulls(dep).scope, RuleScope.Only(Set("com.demo.Box", "org.third.Bag")))
    assert(!nulls(dep).scope.includes("com.other.Untouched"), "`Only` still names what it names")
    assertEquals(dep.surfaceFold.refusals, Nil)
  }

  test("REFUSED: two TARGETS is a choice of emitted shape, not a composition") {
    val b = base(List(nullability(Set("com.demo.Null"))))
    val dep = b.extendedBy(PortManifest("dep", surface = List(
      nullability(Set("com.demo.Null"), target = NullabilityTransform.Target.Wrapper("com.dep.Opt")))))
    assertEquals(dep.effectiveSurface.size, 2, "a refused pair stays in the pipeline")
    assertEquals(dep.surfaceFold.refusals.map(_.cause), List(SurfaceFold.Cause.Conflict))
    val f = ManifestAgreement.check(Some(dep), Nil, foreignRoots = true)
    assertEquals(f.map(_.kind), List(Kind.SurfaceDivergence))
    assert(Kind.SurfaceDivergence.fatal)
    assert(clue(f.head.detail).contains("union"))
    assert(clue(f.head.detail).contains("wrapper:com.dep.Opt"))
  }

  test("REFUSED: an `Everywhere` base and an `Only` dependent point in OPPOSITE directions") {
    val b = base(List(nullability(Set("com.demo.Null"), scope = RuleScope.Everywhere(Set("com.demo.Box")))))
    val dep = b.extendedBy(PortManifest("dep", surface = List(
      nullability(Set("com.demo.Null"), scope = RuleScope.Only(Set("org.third.Bag"))))))
    assertEquals(dep.surfaceFold.refusals.map(_.cause), List(SurfaceFold.Cause.Conflict))
    val f = ManifestAgreement.check(Some(dep), Nil, foreignRoots = true)
    assertEquals(f.map(_.kind), List(Kind.SurfaceDivergence))
    assert(clue(f.head.detail).contains("OPPOSITE"))
  }

  test("…and the DEFAULT `Everywhere(Set.empty)` is a DIRECTION, not the absence of one") {
    // "the whole program" is what an unscoped instance says, and an `Only` merged into it would
    // silently move every declaration the `Only` side deliberately left out.
    val b   = base(List(nullability(Set("com.demo.Null"))))
    val dep = b.extendedBy(PortManifest("dep", surface = List(
      nullability(Set("com.demo.Null"), scope = RuleScope.Only(Set("org.third.Bag"))))))
    assertEquals(dep.surfaceFold.refusals.map(_.cause), List(SurfaceFold.Cause.Conflict))
  }

  test("REFUSED: a target clash AND a scope clash are reported TOGETHER, not one at a time") {
    val b = base(List(nullability(Set("com.demo.Null"), scope = RuleScope.Everywhere(Set("com.demo.Box")))))
    val dep = b.extendedBy(PortManifest("dep", surface = List(nullability(
      Set("com.demo.Null"), target = NullabilityTransform.Target.Wrapper("com.dep.Opt"),
      scope = RuleScope.Only(Set("org.third.Bag"))))))
    val f = ManifestAgreement.check(Some(dep), Nil, foreignRoots = true)
    assertEquals(f.map(_.kind), List(Kind.SurfaceDivergence))
    assert(clue(f.head.detail).contains("TARGET"))
    assert(clue(f.head.detail).contains("OPPOSITE"))
  }

  test("REFUSED: another phase's instance is not a policy this one can compose") {
    // unreachable through the fold, which pairs by NAME — asserted directly so the arm is not the
    // one branch nothing ever evaluates
    assert(nullability(Set("com.demo.Null")).mergedWith(redirect("com.other.A" -> "com.dep.A")).isLeft)
  }

  test("`subjects` is the annotation FQNs AND the scope entries — the scope is what re-shapes a surface") {
    val p = nullability(Set("com.demo.Null"), scope = RuleScope.Everywhere(Set("com.demo.Box", "com.demo.Bag#at")))
    assertEquals(p.subjects, Set("com.demo.Null", "com.demo.Box", "com.demo.Bag"))
  }

  test("INTRUSION: a dependent that scopes out a type its BASE emits is fatal") {
    // the failure this screen exists for, in nullability's own terms: the base emitted the type's
    // annotated members as `T | Null` and the dependent would hold its own overrides of them back —
    // half an override pair, two modules that each compile alone and cannot compile together.
    val b = base(List(nullability(Set("com.demo.Null"))))
    val dep = b.extendedBy(PortManifest("dep", surface = List(
      nullability(Set("com.demo.Null"), scope = RuleScope.Everywhere(Set("com.demo.Widget"))))))
    assertEquals(dep.surfaceFold.intrusions.map(_.subject), List("com.demo.Widget"))
    val f = ManifestAgreement.check(Some(dep), Nil, foreignRoots = true)
    assertEquals(f.map(_.kind), List(Kind.SurfaceIntrusion))
    assert(Kind.SurfaceIntrusion.fatal)
    assert(clue(f.head.detail).contains("com.demo.Widget"))
  }

  test("…and an annotation FQN inside a base's claim is screened by the same rule") {
    val b   = base(List(nullability(Set("com.demo.Null"))))
    val dep = b.extendedBy(PortManifest("dep",
      surface = List(nullability(Set("com.demo.Null", "com.demo.MaybeNull")))))
    assertEquals(dep.surfaceFold.intrusions.map(_.subject), List("com.demo.MaybeNull"))
    assert(clue(ManifestAgreement.check(Some(dep), Nil, foreignRoots = true).head.detail)
      .contains("com.demo.MaybeNull"))
  }

  test("`ownKeys` carries the dependent's own annotation, so a typo in it is reported HERE") {
    // after a merge the module's own declared instance never runs, so the run resolves it to the
    // instance that absorbed it and filters that instance's findings to what THIS manifest added.
    val b   = base(List(nullability(Set("com.demo.Null"))))
    val dep = b.extendedBy(PortManifest("dep", surface = List(nullability(Set("org.third.Nullable")))))
    assertEquals(dep.surfaceFold.ownKeys, Map("nullability" -> Set("org.third.Nullable")))
  }

  test("restating the base's annotation adds NOTHING — agreement is not a contribution") {
    val b   = base(List(nullability(Set("com.demo.Null"))))
    val dep = b.extendedBy(PortManifest("dep", surface = List(nullability(Set("com.demo.Null")))))
    assertEquals(dep.surfaceFold.refusals, Nil)
    assertEquals(dep.surfaceFold.ownKeys, Map("nullability" -> Set.empty[String]))
    assertEquals(nulls(dep).annotations, Set("com.demo.Null"))
  }
