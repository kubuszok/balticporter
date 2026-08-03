package balticporter.runner

import balticporter.core.*
import balticporter.sbtgen.SbtGen
import balticporter.tir.*
import balticporter.transform.{CollectionsTransform, PackageRenameTransform}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** [[PortRun]] end to end, over a real (tiny) Java tree on disk.
  *
  * The properties asserted here are the ones a porting program used to have to get right by
  * copying: the output goes to `src_managed`, resolution roots are not re-emitted, dropped types
  * are skipped, injected sources survive the wipe, the substitution checks are fatal, and the
  * package rename runs last. Each was a hand-written line in one migration program and absent from
  * the other.
  */
class PortRunSpec extends munit.FunSuite:

  private def java(dir: Path, rel: String, src: String): Unit =
    val p = dir.resolve(rel)
    Files.createDirectories(p.getParent)
    Files.writeString(p, src)

  private def fixture(): (Path, Path) =
    val root = Files.createTempDirectory("portrun")
    val src  = root.resolve("java")
    java(src, "com/demo/Widget.java",
      """package com.demo;
        |public class Widget {
        |  public int size;
        |  public String label() { return "w" + size; }
        |}""".stripMargin)
    java(src, "com/demo/Gadget.java",
      """package com.demo;
        |public class Gadget {
        |  public Widget w = new Widget();
        |}""".stripMargin)
    (root, src)

  private def run(root: Path, src: Path, files: List[String] = Nil)(
      f: PortRun => PortRun = identity
  ): PortResult =
    val fs = if files.nonEmpty then files else List("com/demo/Widget.java", "com/demo/Gadget.java")
    f(PortRun(
      label     = "demo",
      portRoot  = root.resolve("port"),
      sourceSet = SourceSet.Main,
      frontend  = FrontendConfig(src, fs, Nil),
      phases    = Nil,
    )).execute()

  private def emitted(out: Path): List[String] =
    if !Files.exists(out) then Nil
    else Files.walk(out).iterator().asScala.filter(_.toString.endsWith(".scala"))
      .map(p => out.relativize(p).toString.replace('\\', '/')).toList.sorted

  test("output goes to src_managed/main/scala — derived from SbtGen, not composed by the caller") {
    val (root, src) = fixture()
    val r = run(root, src)()
    assertEquals(r.outDir, SbtGen.managedMain(root.resolve("port")))
    assert(clue(r.outDir.toString).endsWith("src_managed/main/scala"))
    assertEquals(emitted(r.outDir), List("com/demo/Gadget.scala", "com/demo/Widget.scala"))
    assertEquals(r.written, 2)
  }

  test("a test source set lands in src_managed/test/scala with the same mechanics") {
    val (root, src) = fixture()
    val r = PortRun("demo", root.resolve("port"), SourceSet.Test,
      FrontendConfig(src, List("com/demo/Widget.java"), Nil), Nil).execute()
    assertEquals(r.outDir, SbtGen.managedTest(root.resolve("port")))
    assertEquals(emitted(r.outDir), List("com/demo/Widget.scala"))
  }

  test("a resolution root is RESOLVED against, never emitted") {
    val (root, src) = fixture()
    val other = root.resolve("java2")
    java(other, "com/demo2/Uses.java",
      """package com.demo2;
        |import com.demo.Widget;
        |public class Uses { public Widget w = new Widget(); }""".stripMargin)
    // resolving against another module's sources makes this a DEPENDENT port, and a dependent port
    // must name the module it depends on — see ManifestSpec for what that buys and for the run
    // that refuses when it does not.
    val r = PortRun("demo", root.resolve("port"), SourceSet.Main,
      FrontendConfig(other, List("com/demo2/Uses.java"), Nil, resolutionRoots = List(src)), Nil,
      manifest = Some(PortManifest("base").extendedBy(PortManifest("dependent")))).execute()
    assertEquals(emitted(r.outDir), List("com/demo2/Uses.scala"))
    assert(r.program.units.size > 1, "the model must still SPAN the resolution root")
  }

  test("a dropped type is parsed but not emitted, and an injected replacement takes its place") {
    val (root, src) = fixture()
    val inject = root.resolve("overrides")
    java(inject, "com/demo/Widget.scala", "package com.demo\nclass Widget { def label(): String = \"w\" }")
    val r = run(root, src) { p =>
      p.copy(subs = Substitutions(dropTypes = Set("com.demo.Widget"), inject = List(inject)))
    }
    assertEquals(r.dropped, 1)
    assertEquals(r.injected, 1)
    // the file at the dropped FQN is the INJECTED one, not the mechanical translation
    assert(Files.readString(r.outDir.resolve("com/demo/Widget.scala")).contains("class Widget { def label()"))
  }

  test("a dangling substitution is FATAL — declared, unreplaced, still referenced") {
    val (root, src) = fixture()
    val e = intercept[RuntimeException] {
      run(root, src)(_.copy(subs = Substitutions(dropTypes = Set("com.demo.Widget"))))
    }
    assert(clue(e.getMessage).contains("fatal finding"))
  }

  test("PackageRenameTransform may not be passed as a phase — PortRun owns its ordering") {
    val (root, src) = fixture()
    val e = intercept[IllegalArgumentException] {
      run(root, src)(_.copy(phases = List(new PackageRenameTransform(Map("com.demo" -> "org.port")))))
    }
    assert(clue(e.getMessage).contains("has to run AFTER every other phase"))
  }

  test("packageRenames moves the port's namespace, and the rename runs after every other phase") {
    val (root, src) = fixture()
    val r = run(root, src)(_.copy(packageRenames = Map("com.demo" -> "org.port")))
    assertEquals(emitted(r.outDir), List("org/port/Gadget.scala", "org/port/Widget.scala"))
    // run AFTER the phase, every declared prefix must come back unmatched (see the phase's doc)
    assertEquals(r.report.rename.unmatched, List("com.demo"))
  }

  test("a per-TYPE rename moves ONE type, composes with the package rename, and lands in the FILE") {
    val (root, src) = fixture()
    val r = run(root, src)(_.copy(packageRenames = Map("com.demo" -> "org.port"),
                                  typeRenames = Map("com.demo.Widget" -> "Gizmo")))
    assertEquals(emitted(r.outDir), List("org/port/Gadget.scala", "org/port/Gizmo.scala"))
    // and the check verifies the per-type key the same way it verifies a prefix: zero residue.
    assertEquals(r.report.rename.unmatched.sorted, List("com.demo", "com.demo.Widget"))
    assertEquals(r.report.policy.findings, Nil)
  }

  test("a REFUSED per-type rename is a §1(b) `policy` finding, and the type does not move") {
    // The whole point of the seam: a rename that cannot be carried out must not be a silent no-op.
    val (root, src) = fixture()
    val r = run(root, src)(_.copy(typeRenames = Map("com.demo.Widget" -> "Gadget")))
    assertEquals(emitted(r.outDir), List("com/demo/Gadget.scala", "com/demo/Widget.scala"))
    assertEquals(r.report.policy.findings.map(f => (f.phase, f.key, f.issue)),
                 List(("package-rename", "com.demo.Widget", PolicyIssue.Malformed)))
    assert(clue(r.report.policy.render).contains("§1(b)"))
  }

  test("dropped-types.tsv carries a per-TYPE rename in BOTH namespaces (§4.56)") {
    // The same two-namespace obligation `packageRenames` has, one level finer: an artifact that
    // joined the manifest's upstream FQN to an EMITTED stack frame matched nothing, silently.
    val (root, src) = fixture()
    val rep    = root.resolve("report")
    val inject = root.resolve("overrides")
    java(inject, "com/demo/Widget.scala", "package sge\nclass Gizmo { def label(): String = \"w\" }")
    withReport(rep) {
      run(root, src)(_.copy(subs = Substitutions(dropTypes = Set("com.demo.Widget"), inject = List(inject)),
                            packageRenames = Map("com.demo" -> "sge"),
                            typeRenames = Map("com.demo.Widget" -> "Gizmo")))
    }
    assertEquals(Correlate.parseDropped(rep.resolve("run-latest/dropped-types.tsv")),
                 Set(Correlate.Dropped("com.demo.Widget", "sge.Gizmo")))
  }

  // ---------------------------------------------------------------------------------------------
  // a MERGED phase's policy findings, held to the manifest that can fix them (DESIGN.md §8.13)
  // ---------------------------------------------------------------------------------------------

  /** a base and a dependent that each declare a `type-redirect` with one MALFORMED member key, so
    * the fold merges them and one instance carries both findings. */
  private def mergedRedirects(): PortManifest =
    val base = PortManifest("base", surface = List(new balticporter.transform.TypeRedirectTransform(
      Map("com.demo.Widget" -> "com.dep.W2"),
      Map("com.demo.Widget" -> Map("theirs<T>" -> "y")))))
    base.extendedBy(PortManifest("dep", surface = List(new balticporter.transform.TypeRedirectTransform(
      Map("com.demo.Gadget" -> "com.dep.G2"),
      Map("com.demo.Gadget" -> Map("mine<T>" -> "x"))))))

  test("a typo'd DEPENDENT key on a merged phase surfaces its `policy` finding") {
    // The filter reads a finding's key for its subject, cut at `#`. Keyed by the bare segment, the
    // dependent's own malformed entry had NO type FQN in it, matched no contributed subject, and
    // was dropped — a typo silently no-oping on exactly the seam `PolicyReport` exists to close.
    val (root, src) = fixture()
    val r = run(root, src)(_.copy(manifest = Some(mergedRedirects())))
    val mine = r.report.policy.findings.filter(_.phase == "type-redirect")
    assertEquals(mine.map(f => (f.key, f.issue)),
                 List(("com.demo.Gadget#mine<T>", PolicyIssue.Malformed)))
    assert(clue(r.report.policy.render).contains("type ARGUMENT"))
  }

  test("…and the BASE's key is withheld from the dependent's report — it is not fixable here") {
    val (root, src) = fixture()
    val r = run(root, src)(_.copy(manifest = Some(mergedRedirects())))
    assert(!clue(r.report.policy.render).contains("theirs<T>"),
           "an inherited key lives in the base's manifest; `ManifestAgreement` reports that half")
  }

  // ---------------------------------------------------------------------------------------------
  // a REFUSED merge stops the run BEFORE the pipeline (ENGINE-LIMITS.md CT9 Face B)
  // ---------------------------------------------------------------------------------------------

  /** a base and a dependent that each declare a `class-table` — a `SurfacePolicy` with NO
    * `MergeablePolicy` — with different tables for one key. The fold cannot compose them, so both
    * instances stay in the effective pipeline: the shape that used to run only the later one. */
  private def refusedPair(): PortManifest =
    PortManifest("base", surface = List(new balticporter.transform.ClassTableTransform(
      Map("com.demo.Widget#of" -> "com.demo.Widget#classFor"))))
      .extendedBy(PortManifest("dep", surface = List(new balticporter.transform.ClassTableTransform(
        Map("com.demo.Widget#of" -> "com.demo.Gadget#classFor")))))

  test("a REFUSED merge FAILS THE RUN, and BOTH instances' policies are named") {
    // the silent-drop shape reproduced, then caught. `Pipeline.order` now keeps both instances, so
    // running would apply two policies for one key; the refusal is what stops it, and it stops it
    // before anything is parsed.
    val (root, src) = fixture()
    val m = refusedPair()
    assertEquals(m.effectiveSurface.size, 2, "the pre-merge pipeline: two instances, one name")
    assertEquals(Pipeline.order(m.effectiveSurface).size, 2, "…and BOTH would run (CT9 Face B)")
    val err = intercept[RuntimeException](run(root, src)(_.copy(manifest = Some(m))))
    assert(clue(err.getMessage).contains("SurfaceDivergence"))
    // BOTH policies, so the reader has the pair to reconcile — the thing the silent drop hid
    assert(err.getMessage.contains("com.demo.Widget#classFor"))
    assert(err.getMessage.contains("com.demo.Gadget#classFor"))
    assert(err.getMessage.contains("§1"), "every finding says which of §1's three kinds the fix is")
    assert(err.getMessage.contains("before any phase runs"))
    // …and nothing was emitted: the gate runs ahead of the translation, not after it
    assert(!Files.exists(root.resolve("port").resolve("src_managed/main/scala/com/demo/Widget.scala")))
  }

  test("NEGATIVE: two EQUAL instances COLLAPSE TO ONE, and the run is green") {
    // REPINNED. This used to assert only that the run was green, and it was green for the wrong
    // reason: `Pipeline.order` keeps both instances since CT9 Face B, so the phase ran TWICE and
    // the emitted file was correct only because `ClassTableTransform`'s rewrite happens to be
    // IDEMPOTENT — a property of that one phase, which nothing asked of it and which the next
    // contract-less phase need not have. The fold now proves the pair equal and drops one, so the
    // pipeline itself is the assertion and the green is no longer an accident.
    val (root, src) = fixture()
    val table = Map("com.demo.Widget#of" -> "com.demo.Widget#classFor")
    val m = PortManifest("base", surface = List(new balticporter.transform.ClassTableTransform(table)))
      .extendedBy(PortManifest("dep", surface = List(new balticporter.transform.ClassTableTransform(table))))
    assertEquals(m.effectiveSurface.size, 1, "ONE instance in the effective pipeline…")
    assertEquals(Pipeline.order(m.effectiveSurface).size, 1, "…and ONE runs")
    val r = run(root, src)(_.copy(manifest = Some(m)))
    assert(clue(emitted(r.outDir)).contains("com/demo/Widget.scala"))
  }

  // ---------------------------------------------------------------------------------------------
  // the `governs` screen asks what the base EMITS (ENGINE-LIMITS.md CT9 Face A), end to end
  // ---------------------------------------------------------------------------------------------

  /** a dependent whose OWN declaration lives INSIDE the base's claimed namespace — a library's own
    * test module, which is the shape no prefix can separate from the module it tests. */
  private def sharedNamespaceFixture(): (Path, Path, Path) =
    val (root, src) = fixture()
    val other = root.resolve("java3")
    java(other, "com/demo/WidgetTest.java",
      """package com.demo;
        |public class WidgetTest { public Widget w = new Widget(); }""".stripMargin)
    (root, src, other)

  private def claimingRun(root: Path, src: Path, other: Path, phases: List[Phase]) =
    PortRun("demo", root.resolve("port"), SourceSet.Main,
      FrontendConfig(other, List("com/demo/WidgetTest.java"), Nil, resolutionRoots = List(src)), Nil,
      manifest = Some(PortManifest("basemod", governs = Set("com.demo"))
        .extendedBy(PortManifest("dependent", surface = phases))))

  test("a dependent's key at an FQN the base's published map does NOT emit is ADMITTED") {
    // CT9 Face A. `com.demo.WidgetTest` is inside the base's `governs` claim and the base has never
    // parsed it — a drop cannot say that, and the base's map does: no entry, nothing stands there.
    val (root, src, other) = sharedNamespaceFixture()
    val rep = root.resolve("report")
    publishBase(root, "basemod", List("com.demo.Widget", "com.demo.Gadget"))
    val r = withReport(rep)(claimingRun(root, src, other, List(
      new balticporter.transform.TypeRedirectTransform(
        Map("com.demo.WidgetTest" -> "com.dep.WidgetTest")))).execute())
    assert(clue(emitted(r.outDir)).contains("com/demo/WidgetTest.scala"))
    assert(!clue(r.report.manifest.map(_.kind.toString)).contains("SurfaceIntrusion"))
  }

  test("…and one at an FQN it DOES emit is refused, before any phase runs") {
    val (root, src, other) = sharedNamespaceFixture()
    val rep = root.resolve("report")
    publishBase(root, "basemod", List("com.demo.Widget", "com.demo.Gadget"))
    val err = intercept[RuntimeException] {
      withReport(rep)(claimingRun(root, src, other, List(
        new balticporter.transform.TypeRedirectTransform(
          Map("com.demo.Widget" -> "com.dep.Widget")))).execute())
    }
    assert(clue(err.getMessage).contains("SurfaceIntrusion"))
    assert(err.getMessage.contains("com.demo.Widget"))
    assert(err.getMessage.contains("published map emits it"), "the map is the evidence, not the manifest")
    assert(!Files.exists(root.resolve("port").resolve("src_managed/main/scala/com/demo/WidgetTest.scala")))
  }

  test("externalConcrete is DERIVED from the phases: RuntimePlan, never a caller argument") {
    val (root, src) = fixture()
    val r = run(root, src)(_.copy(phases = List(new CollectionsTransform)))
    assertEquals(r.runtime.required, CollectionsTransform.runtimeTypes)
    assertEquals(r.runtime.concreteMembers, CollectionsTransform.runtimeConcreteMembers)
    assertEquals(r.runtime.dependency.map(_.artifact), Some(RuntimeArtifact.artifact))
  }

  test("Vendored writes the support sources beside the emitted code; Dependency writes none") {
    val (root, src) = fixture()
    val dep = run(root, src)(_.copy(phases = List(new CollectionsTransform)))
    assertEquals(emitted(dep.outDir).count(_.startsWith("balticporter/runtime/")), 0)
    val ven = run(root, src)(_.copy(phases = List(new CollectionsTransform), runtimeMode = RuntimeMode.Vendored))
    assertEquals(emitted(ven.outDir).count(_.startsWith("balticporter/runtime/")), CollectionsTransform.runtimeTypes.size)
  }

  test("provenance is stamped on every emitted file — a licence obligation, not an option") {
    val (root, src) = fixture()
    val r = run(root, src)(_.copy(provenance = Some(Provenance("Demo", "abc123", "Apache-2.0", "java", src.toString))))
    val out = Files.readString(r.outDir.resolve("com/demo/Widget.scala"))
    assert(clue(out).contains("Original license: Apache-2.0"))
    assert(clue(out).contains("Ported from: java/com/demo/Widget.java"))
  }

  test("the sbt skeleton is emitted with the runtime dependency the PHASES made necessary") {
    val (root, src) = fixture()
    val spec = SbtGen.ProjectSpec("demo", "org.demo", "3.8.4", "2.0.0-M4", Nil, engineFingerprint = "test")
    val r = run(root, src)(_.copy(phases = List(new CollectionsTransform), project = Some(spec)))
    val build = Files.readString(root.resolve("port/build.sbt"))
    assert(clue(build).contains(RuntimeArtifact.artifact), "the run must add the dependency the caller never declared")
    assert(Files.exists(root.resolve("port/.gitignore")))
    assert(Files.exists(root.resolve("port").resolve(EnginePin.fileName)))
  }

  test("determinism: emission double-translation runs, and a nondeterministic phase is caught") {
    val (root, src) = fixture()
    run(root, src)(_.copy(determinism = Determinism.Emission)) // passes
    run(root, src)(_.copy(determinism = Determinism.Full))     // parses twice, byte-compares
  }

  test("Determinism.fromArgs: default is Emission, and the flags are honoured") {
    assertEquals(Determinism.fromArgs(Nil), Determinism.Emission)
    assertEquals(Determinism.fromArgs(Seq("--determinism=full")), Determinism.Full)
    assertEquals(Determinism.fromArgs(Seq("--determinism=off")), Determinism.Off)
  }

  test("the action cache reproduces byte-identical output — it is advisory, never authoritative") {
    val (root, src) = fixture()
    val dir  = root.resolve("cache")
    val cold = run(root, src)(_.copy(cache = Some(dir)))
    val coldText = emitted(cold.outDir).map(f => f -> Files.readString(cold.outDir.resolve(f))).toMap
    assert(Files.exists(dir), "a cold run must POPULATE the cache")
    val warm = run(root, src)(_.copy(cache = Some(dir)))
    val warmText = emitted(warm.outDir).map(f => f -> Files.readString(warm.outDir.resolve(f))).toMap
    assertEquals(warmText, coldText)
    // …and so does a run with no cache at all
    val none = run(root, src)()
    assertEquals(emitted(none.outDir).map(f => f -> Files.readString(none.outDir.resolve(f))).toMap, coldText)
  }

  test("a signature change re-keys the dependent unit, a body change does not (early cutoff)") {
    val (root, src) = fixture()
    val program = PortRun("k", root.resolve("port"), SourceSet.Main,
      FrontendConfig(src, List("com/demo/Widget.java", "com/demo/Gadget.java"), Nil), Nil).execute().program
    val keys = TirCacheKey.forUnits(program, program.units)
    assertEquals(keys.size, program.units.size)
    assert(keys.values.toSet.size == keys.size, "two units must not share an action key")

    // change Widget's METHOD BODY only
    java(src, "com/demo/Widget.java",
      """package com.demo;
        |public class Widget {
        |  public int size;
        |  public String label() { return "CHANGED" + size; }
        |}""".stripMargin)
    val p2 = PortRun("k", root.resolve("port2"), SourceSet.Main,
      FrontendConfig(src, List("com/demo/Widget.java", "com/demo/Gadget.java"), Nil), Nil).execute().program
    val k2 = byName(p2, TirCacheKey.forUnits(p2, p2.units))
    val k1 = byName(program, keys)
    assertNotEquals(k1("com.demo.Widget"), k2("com.demo.Widget"), "Widget's own content changed")
    assertEquals(k1("com.demo.Gadget"), k2("com.demo.Gadget"), "a BODY change must not re-key a dependent")

    // now change Widget's SIGNATURE
    java(src, "com/demo/Widget.java",
      """package com.demo;
        |public class Widget {
        |  public int size;
        |  public String label(int n) { return "CHANGED" + size; }
        |}""".stripMargin)
    val p3 = PortRun("k", root.resolve("port3"), SourceSet.Main,
      FrontendConfig(src, List("com/demo/Widget.java", "com/demo/Gadget.java"), Nil), Nil).execute().program
    val k3 = byName(p3, TirCacheKey.forUnits(p3, p3.units))
    assertNotEquals(k2("com.demo.Gadget"), k3("com.demo.Gadget"), "a SIGNATURE change must re-key its dependents")
  }

  private def byName(p: Program, keys: Map[SymId, String]): Map[String, String] =
    keys.flatMap((id, k) => p.symbolOf(id).map(_.fullName -> k))

  // =========================================================================================
  // the artifact layer: PortRun owns it, because a run is what an artifact describes
  // =========================================================================================

  /** Turn the artifact layer on, into a directory of this test's own. `CheckReport` is gated on
    * process-global flags, so this is set and restored around one run. */
  private def withReport[A](dir: Path)(f: => A): A =
    val keys  = List("balticporter.report" -> "on", "balticporter.reportDir" -> dir.toString)
    val saved = keys.map((k, _) => k -> Option(System.getProperty(k)))
    keys.foreach((k, v) => System.setProperty(k, v))
    try f
    finally saved.foreach {
      case (k, Some(v))    => System.setProperty(k, v)
      case (k, scala.None) => System.clearProperty(k)
    }

  test("with the artifact layer OFF a run writes NOTHING outside its output directory") {
    // The port map was written unconditionally, into `<cwd>/port-report/<main class>/run-latest`.
    // Under a forked test JVM the working directory is the SUBPROJECT's, so this suite published
    // maps into the repository — `runner/port-report/`, and once a committed `port-report/jar/`
    // holding this file's own `PortRun("k", …)` fixture. A `git status` that cannot tell a decision
    // from an artefact is precisely what §5.5's discipline rests on.
    val here   = DebugFlags.root.resolve("port-report")
    def listed = if !Files.exists(here) then Set.empty[String]
                 else Files.walk(here).iterator().asScala.map(_.toString).toSet
    val before = listed
    val (root, src) = fixture()
    run(root, src)()
    assertEquals(listed, before, "a run that was not asked for artifacts must not leave any")
  }

  test("PortRun writes the source map — the emitter no longer records through a global") {
    val (root, src) = fixture()
    val rep = root.resolve("report")
    val r = withReport(rep)(run(root, src)())
    val map = SrcMap.parseAll(rep.resolve("run-latest/srcmap.tsv"))
    assertEquals(map.map(_.unit).distinct.sorted, List("com.demo.Gadget", "com.demo.Widget"))
    assert(Files.isRegularFile(rep.resolve("run-latest/members.tsv")))
    // the map describes THIS run's units and no others — with a global table, every emission the
    // JVM had ever performed (this suite's other fixtures included) landed in the same file.
    assert(map.forall(_.unit.startsWith("com.demo.")), clue(map.map(_.unit).distinct))
    assert(r.written == 2)
  }

  test("expected failures are GENERATED from the manifest: dropped-types.tsv, not a hand-written list") {
    val (root, src) = fixture()
    val rep = root.resolve("report")
    val inject = root.resolve("overrides")
    java(inject, "com/demo/Widget.scala", "package com.demo\nclass Widget { def label(): String = \"w\" }")
    withReport(rep) {
      run(root, src)(_.copy(subs = Substitutions(dropTypes = Set("com.demo.Widget"), inject = List(inject))))
    }
    assertEquals(Correlate.parseDropped(rep.resolve("run-latest/dropped-types.tsv")),
                 Set(Correlate.Dropped("com.demo.Widget", "com.demo.Widget")))
    // …and the correlator classifies a failure reaching that type as expected, with nothing declared
    val t = Correlate.locateTests(
      Correlate.parseTests(
        "com.demo.WidgetTest:\n==> X com.demo.WidgetTest.labels  0.0s boom\n    at com.demo.Widget.label(Widget.scala:3)\n"),
      SrcMap.Index.of(SrcMap.parseAll(rep.resolve("run-latest/srcmap.tsv"))),
      Nil, Set.empty, Correlate.parseDropped(rep.resolve("run-latest/dropped-types.tsv")))
    assertEquals(t.flatMap(_.expected).map(_.source), List("derived"))
  }

  test("a RENAMING port writes both namespaces, so the drop reaches a stack frame that says `sge.`") {
    // The measurement-integrity defect this closes: policy is written UPSTREAM and the rename runs
    // LAST (§4.56), so an artifact holding only the manifest FQN was compared against emitted
    // frames and matched nothing — the derived classifier had never fired on a renaming port.
    val (root, src) = fixture()
    val rep = root.resolve("report")
    val inject = root.resolve("overrides")
    java(inject, "com/demo/Widget.scala", "package sge\nclass Widget { def label(): String = \"w\" }")
    withReport(rep) {
      run(root, src)(_.copy(subs = Substitutions(dropTypes = Set("com.demo.Widget"), inject = List(inject)),
                            packageRenames = Map("com.demo" -> "sge")))
    }
    val dropped = Correlate.parseDropped(rep.resolve("run-latest/dropped-types.tsv"))
    assertEquals(dropped, Set(Correlate.Dropped("com.demo.Widget", "sge.Widget")))
    // the frame is in the EMITTED namespace, and a dropped type has no source-map entry to resolve
    // through — its replacement is injected Scala the emitter never saw.
    val t = Correlate.locateTests(
      Correlate.parseTests(
        "sge.WidgetTest:\n==> X sge.WidgetTest.labels  0.0s boom\n    at sge.Widget.label(Widget.scala:3)\n"),
      SrcMap.Index.of(SrcMap.parseAll(rep.resolve("run-latest/srcmap.tsv"))),
      Nil, Set.empty, dropped)
    assertEquals(t.flatMap(_.expected).map(_.source), List("derived"))
    assert(clue(t.flatMap(_.expected).head.reason).contains("com.demo.Widget"))
  }

  test("every RequiredCheck reaches findings.tsv — a number on stdout that is not persisted fails the run") {
    val (root, src) = fixture()
    val rep = root.resolve("report")
    withReport(rep) {
      run(root, src)()
      // the shutdown hook's path, forced now: `CheckReport.dir` must resolve INSIDE the flag scope
      CheckReport.write(rep.resolve("run-latest"))
    }
    val counts = Files.readAllLines(rep.resolve("run-latest/counts.tsv")).toArray(Array.empty[String]).toList
      .filterNot(_.startsWith("#")).flatMap(_.split('\t').headOption).toSet
    assertEquals(PortRun.RequiredChecks -- counts, Set.empty[String])
    // a check that found NOTHING is still named, or `counts.tsv` cannot tell it from one that
    // never ran — the distinction the whole persistence layer exists to keep.
    assert(counts.contains(PortRun.PortabilityInjected), "nothing was injected, and it must still be named")
  }

  test("base-surface: an UNCONSUMED Unknown is a finding, and the FATAL half is kept apart") {
    // the negative case the registration exists for. A gap nothing emitted is specified as a
    // FINDING, and until it had a check name it was a line of stdout: no `counts.tsv` row, so
    // nothing could diff it and a port could start asking unanswerable questions with no number
    // moving anywhere.
    val unconsumed = Surface.Gap("p.Base#m", "no declared base publishes a contract row", Some("base-mod"),
                                 fatal = false, fix = "\u00a71(b) PER-LIBRARY: declare the base")
    val consumed   = unconsumed.copy(subject = "p.Other", fatal = true)
    val fs = PortRun.baseSurfaceFindings(List(unconsumed, consumed))
    assertEquals(fs.map(_.check).distinct, List(PortRun.BaseSurface))
    assertEquals(fs.map(f => f.kind -> f.owner),
                 List("unanswered" -> "p.Base#m", "shaped emitted text" -> "p.Other"))
    // \u00a74.45 \u2014 the classification rides in `detail`, so an agent holding only findings.tsv has it
    assert(clue(fs.head.detail).contains("\u00a71(b) PER-LIBRARY"), fs.head.detail)
    assert(fs.head.detail.contains("[base: base-mod]"), fs.head.detail)
    // no origin: a contract question is about a SYMBOL, and a plausible-looking path would be worse
    assertEquals(fs.map(f => f.path -> f.line).distinct, List("" -> 0))
    assertEquals(PortRun.baseSurfaceFindings(Nil), Nil)
  }

  test("\u2026and a run that asks NOTHING still names the check \u2014 a zero is not an absence") {
    val (root, src) = fixture()
    val rep = root.resolve("report")
    withReport(rep) {
      run(root, src)()
      CheckReport.write(rep.resolve("run-latest"))
    }
    val counts = Files.readAllLines(rep.resolve("run-latest/counts.tsv")).toArray(Array.empty[String]).toList
      .filterNot(_.startsWith("#")).map(_.split('\t').toList)
    assertEquals(clue(counts).collectFirst { case k :: v :: _ if k == PortRun.BaseSurface => v }, Some("0"))
  }

  test("correlation runs IN-PROCESS against this run's own report directory") {
    val (root, src) = fixture()
    val rep = root.resolve("report")
    val result = withReport(rep) {
      run(root, src)()
      val log = root.resolve("compile.txt")
      Files.writeString(log,
        "-- [E007] Type Mismatch Error: /anywhere/com/demo/Widget.scala:5:2 ---\n" +
        " 5 |  x\n   |  ^\n   |  Found: String\n")
      PortRun("demo", root.resolve("port"), SourceSet.Main, FrontendConfig(src, Nil, Nil), Nil)
        .correlate(scalac = Some(log))
    }.getOrElse(fail("the artifact layer was ON — correlation must have run"))
    // the error is joined back to the MEMBER and the Java line it came from — no second JVM
    assertEquals(result.errors.size, 1)
    assertEquals(result.errors.head.entry.map(_.unit), Some("com.demo.Widget"))
    assert(Files.isRegularFile(rep.resolve("run-latest/errors.tsv")))
  }

  test("with the artifact layer OFF, correlation creates NO directory — it is a write like any other") {
    // `correlate` passed `out = CheckReport.runDir` unconditionally, and `CorrelateRun.run` creates
    // that directory before it validates its inputs. With reporting off `runDir` falls back to
    // `<cwd>/port-report/<sun.java.command>/run-latest`, and a forked test JVM's cwd is the
    // SUBPROJECT — so an empty artifact directory appeared in the checkout, from a run that had not
    // opted in and whose source map had (correctly) never been written. Same defect, same shape and
    // same fix as the unconditional `PortMap.write` above.
    //
    // Asserted on the FILESYSTEM, not on the return value: what is being pinned is that nothing was
    // created, and a `None` proves only that a branch was taken.
    val here   = DebugFlags.root.resolve("port-report")
    def listed = if !Files.exists(here) then Set.empty[String]
                 else Files.walk(here).iterator().asScala.map(_.toString).toSet
    val before    = listed
    val runDir    = CheckReport.runDir
    val runDirWas = Files.exists(runDir)
    val (root, src) = fixture()
    val log = root.resolve("compile.txt")
    Files.writeString(log, "-- [E007] Type Mismatch Error: /anywhere/com/demo/Widget.scala:5:2 ---\n")
    val out = PortRun("demo", root.resolve("port"), SourceSet.Main, FrontendConfig(src, Nil, Nil), Nil)
      .correlate(scalac = Some(log))
    assertEquals(out, scala.None)
    assertEquals(listed, before, "a correlation nobody asked to publish must not create its own home")
    assertEquals(Files.exists(runDir), runDirWas, clue(runDir))
  }

  test("omissions are checked over EMITTED units only — a dropped type's findings are not this port's") {
    // Both classes have the shape that yields a genuine `super(args) dropped` finding: a WALL — two
    // roots reaching two DIFFERENT parent constructors, so nothing is synthesised and the nilary
    // root is promoted — whose `super(cap)` the REPLAY cannot express either, because `Par()`
    // assigns a field `Par(int)` does not and replaying it would not leave the state java left.
    // One is emitted and must be reported; one is dropped-and-replaced, so its "omission" describes
    // code the port never emits — the classpath holds the injected replacement — and reporting it
    // hands an agent a finding it cannot act on in any file this run wrote.
    val (root, src) = fixture()
    val par =
      """package com.demo;
        |public class Par {
        |  public int cap; public String tag;
        |  public Par()        { this.tag = "t"; }
        |  public Par(int cap) { this.cap = cap; }
        |}""".stripMargin
    def sub(name: String) =
      s"""package com.demo;
         |public class $name extends Par {
         |  public $name()        { }
         |  public $name(int cap) { super(cap); }
         |}""".stripMargin
    java(src, "com/demo/Par.java", par)
    java(src, "com/demo/Kept.java", sub("Kept"))
    java(src, "com/demo/Gone.java", sub("Gone"))
    val inject = root.resolve("overrides")
    java(inject, "com/demo/Gone.scala", "package com.demo\nclass Gone(cap: Int) extends Par(cap)")
    val r = run(root, src,
      files = List("com/demo/Par.java", "com/demo/Kept.java", "com/demo/Gone.java")) { p =>
      p.copy(subs = Substitutions(dropTypes = Set("com.demo.Gone"), inject = List(inject)))
    }
    val supers = r.report.omissions.filter(_.what == "super(args) dropped")
    assertEquals(supers.map(_.owner), List("com.demo.Kept"))
  }

  test("a DROPPED NILARY CONSTRUCTOR carries a porter note in the body it is missing from (C11)") {
    // `ENGINE-LIMITS.md` C11: `Font()` delegates WITH ARGUMENTS in front of a class whose primary is
    // scala's own implicit nilary one, so it cannot be emitted and cannot be replaced by anything
    // that is not a wrong answer. `OmissionCheck` gives that a NUMBER; the number answers an agent
    // holding the run directory, and the agent this engine has is reading the emitted file, where
    // the missing `def this()` has nothing to grep for. Hence the note — `InBody`, at the head of
    // the owning type, which is where somebody looking for the constructor looks (§4.575).
    val (root, src) = fixture()
    java(src, "com/demo/Font.java",
      """package com.demo;
        |public class Font {
        |  public int size; public String name;
        |  public Font()                      { this(seed(), "d"); }
        |  public Font(int size)              { this(size, "d"); }
        |  public Font(int size, String name) { this.size = size; this.name = name; grow(size); }
        |  static int seed() { return 12; }
        |  void grow(int by) { size = size + by; }
        |}""".stripMargin)
    // the argument-free `extends` is what takes the paramful promotion back, leaving `Plan.none`
    java(src, "com/demo/Sub.java", "package com.demo; public class Sub extends Font { }")
    val r = run(root, src, files = List("com/demo/Font.java", "com/demo/Sub.java"))()
    val out = Files.readString(r.outDir.resolve("com/demo/Font.scala"))

    // the DECLARATION, not the string: the note's own `why` quotes `def this()` on purpose, and a
    // `contains` here would pass or fail on the explanation rather than on the code.
    assert(!out.linesIterator.exists(_.trim.startsWith("def this()")), out)
    assert(clue(out).contains("/* porter: dropped-member reason=universal " +
      "rule=ctor-funnel/nilary-dropped(C11) arguments=2 member=<init>() owner=com.demo.Font"), out)
    // the note heads the CLASS BODY, not some member's declaration: the subject has none.
    assert(clue(out.indexOf("porter: dropped-member")) > out.indexOf("class Font"), out)
    // …and the omission it explains is still counted. Two records of ONE predicate's answer, for
    // two audiences — never two derivations.
    assertEquals(r.report.omissions.filter(_.what == "nilary constructor dropped").map(_.owner),
                 List("com.demo.Font"))
  }

  test("the source map describes what is ON DISK — a dropped unit leaves no phantom entries") {
    // The emitter records every unit it renders, including the ones the run then refuses to
    // write; left in the map, a stack frame inside the INJECTED replacement resolved to a
    // fabricated member of the never-written unit, with a Java origin to match. Filtered at the
    // write, keyed by EMITTED name (the drop is declared upstream, the map is post-rename).
    val (root, src) = fixture()
    val rep = root.resolve("report")
    val inject = root.resolve("overrides")
    java(inject, "com/demo/Widget.scala", "package sge\nclass Widget { def label(): String = \"w\" }")
    withReport(rep) {
      run(root, src)(_.copy(subs = Substitutions(dropTypes = Set("com.demo.Widget"), inject = List(inject)),
                            packageRenames = Map("com.demo" -> "sge")))
    }
    val units = SrcMap.parseAll(rep.resolve("run-latest/srcmap.tsv")).map(_.unit).distinct
    assertEquals(units, List("sge.Gadget"))
  }

  // =========================================================================================
  // a SYNTHESISED unit belongs to ONE module (ENGINE-LIMITS.md §13 O5, CLAUDE.md §1.5)
  // =========================================================================================

  /** the smallest phase that reproduces O5: it MINTS a top-level unit with no `Origin`.
    *
    * `PortRun.converted` classifies a unit by its recorded origin and CONVERTS one it cannot place,
    * deliberately — refusing to emit on a missing origin would be a silent omission. So a phase that
    * mints in every module of a chain gets its unit written once per module, and no count anywhere
    * moves: the base's report cannot see a file a dependent wrote. */
  private final class MintUnit(fqn: String) extends Phase:
    def name = s"mint-unit:$fqn"
    override def run(program: Program): Program =
      val id  = SymId(program.symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1)
      val sym = Symbol(id, fqn.substring(fqn.lastIndexOf('.') + 1), fqn,
                       Flags(isModule = true), SymId.None, TypeRepr.NoType)
      program.rebuilt(program.units :+ Tree.ClassDef(id, Nil, scala.None, Nil, Origin.synthetic),
                      SymbolTable(program.symbols.all.toList :+ sym))

  /** publish a base's map claiming one EMITTED type, where `PortMap.discover` will find it. */
  private def publishBase(reportRoot: Path, module: String, emits: List[String],
                          dropped: List[String] = Nil): Unit =
    val entries =
      emits.map(f => PortMap.Entry("type", f, f, PortMap.Disposition.Ported)) ++
        dropped.map(f => PortMap.Entry("type", f, "", PortMap.Disposition.Dropped))
    PortMap.write(reportRoot.resolve(module).resolve("run-latest"),
                  PortMap.Map0(module, EngineInfo.fingerprint, entries))

  private def dependentRun(root: Path, src: Path, other: Path, base: String, phases: List[Phase]) =
    PortRun("demo", root.resolve("port"), SourceSet.Main,
      FrontendConfig(other, List("com/demo2/Uses.java"), Nil, resolutionRoots = List(src)), Nil,
      manifest = Some(PortManifest(base).extendedBy(PortManifest("dependent", surface = phases))))

  private def dependentFixture(): (Path, Path, Path) =
    val (root, src) = fixture()
    val other = root.resolve("java2")
    java(other, "com/demo2/Uses.java",
      """package com.demo2;
        |import com.demo.Widget;
        |public class Uses { public Widget w = new Widget(); }""".stripMargin)
    (root, src, other)

  test("a SYNTHESISED unit at an FQN a base already emits FAILS THE RUN") {
    // The belt to the phase's own suspenders. `PrimitiveToOpaqueTransform` now fences its mint on
    // `RunScope.emits`; this is what catches the NEXT phase to mint without asking, which will not
    // have read O5. Nothing else can see it — the duplicate compiles nowhere and counts nothing.
    val (root, src, other) = dependentFixture()
    val rep = root.resolve("report")
    publishBase(root, "basemod", List("com.demo.Handle"))
    val err = intercept[RuntimeException] {
      withReport(rep)(dependentRun(root, src, other, "basemod", List(new MintUnit("com.demo.Handle"))).execute())
    }
    assert(clue(err.getMessage).contains("com.demo.Handle"))
    assert(err.getMessage.contains("basemod"))
    // the message says which of §1's three kinds the fix is, and where the rule is (§4.45)
    assert(err.getMessage.contains("§1(a) ENGINE"))
    assert(err.getMessage.contains("RunScope.emits"))
    // …and nothing was written: the refusal runs before the emission loop
    assert(!Files.exists(root.resolve("port").resolve("src_managed/main/scala/com/demo/Handle.scala")))
  }

  test("NEGATIVE: a synthesised unit the base does NOT emit is written, and the run is green") {
    val (root, src, other) = dependentFixture()
    val rep = root.resolve("report")
    publishBase(root, "basemod", List("com.demo.Widget"))
    val r = withReport(rep)(
      dependentRun(root, src, other, "basemod", List(new MintUnit("com.demo.Handle"))).execute())
    assert(clue(emitted(r.outDir)).contains("com/demo/Handle.scala"))
  }

  test("NEGATIVE: a base's DROPPED type is not a claim — it emits nothing to collide with") {
    val (root, src, other) = dependentFixture()
    val rep = root.resolve("report")
    publishBase(root, "basemod", List("com.demo.Widget"), dropped = List("com.demo.Handle"))
    val r = withReport(rep)(
      dependentRun(root, src, other, "basemod", List(new MintUnit("com.demo.Handle"))).execute())
    assert(clue(emitted(r.outDir)).contains("com/demo/Handle.scala"))
  }

  test("the refusal is a pure function of what this run would write and what its bases published") {
    // Testable without a run directory — the same division `discoverBasePorts` documents. The four
    // rows are the four ways a unit and a map can meet.
    val (root, src) = fixture()
    val r = run(root, src)()
    val p = r.program
    val parsed = p.units.head                                   // a real unit, with a Java origin
    val mintedFqn = "com.demo.Handle"
    val id  = SymId(p.symbols.all.map(_.id.raw).max + 1)
    val sym = Symbol(id, "Handle", mintedFqn, Flags(isModule = true), SymId.None, TypeRepr.NoType)
    val q   = p.rebuilt(symbols = SymbolTable(p.symbols.all.toList :+ sym))
    val minted = Tree.ClassDef(id, Nil, scala.None, Nil, Origin.synthetic)

    def claims(entries: List[PortMap.Entry]) =
      PortRun.claimedSynthetic(q, List(minted), List("basemod" -> PortMap.Map0("basemod", "e", entries)))

    // 1. minted, and the base emits that name → the refusal
    assertEquals(claims(List(PortMap.Entry("type", mintedFqn, mintedFqn, PortMap.Disposition.Ported)))
                   .map(c => c.fqn -> c.base), List(mintedFqn -> "basemod"))
    // 2. minted, and the base emits something else → nothing
    assertEquals(claims(List(PortMap.Entry("type", "com.demo.Other", "com.demo.Other", PortMap.Disposition.Ported))), Nil)
    // 3. minted, and the base DROPS that name → nothing to collide with
    assertEquals(claims(List(PortMap.Entry("type", mintedFqn, "", PortMap.Disposition.Dropped))), Nil)
    // 4. no bases at all — a BASE port asks this question and always answers `Nil`, by arithmetic
    assertEquals(PortRun.claimedSynthetic(q, List(minted), Nil), Nil)

    // …and a PARSED unit is never synthesised, whatever a base's map says about its name. The whole
    // point of reading the ORIGIN: this is the case `converted` already decides correctly.
    assert(!PortRun.isSynthesised(parsed.origin), clue(parsed.origin))
    assert(PortRun.isSynthesised(minted.origin))
  }
