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
    }
    // the error is joined back to the MEMBER and the Java line it came from — no second JVM
    assertEquals(result.errors.size, 1)
    assertEquals(result.errors.head.entry.map(_.unit), Some("com.demo.Widget"))
    assert(Files.isRegularFile(rep.resolve("run-latest/errors.tsv")))
  }

  test("omissions are checked over EMITTED units only — a dropped type's findings are not this port's") {
    // Both classes have the shape that yields a genuine `super(args) dropped` finding: a promoted
    // (Object, int) primary, and a second root whose String argument fits none of its parameters.
    // One is emitted and must be reported; one is dropped-and-replaced, so its "omission" describes
    // code the port never emits — the classpath holds the injected replacement — and reporting it
    // hands an agent a finding it cannot act on in any file this run wrote.
    val (root, src) = fixture()
    val par =
      """package com.demo;
        |public class Par {
        |  public Object a; public int b;
        |  public Par(Object a, int b) { this.a = a; this.b = b; }
        |}""".stripMargin
    def sub(name: String) =
      s"""package com.demo;
         |public class $name extends Par {
         |  public $name(Object a, int b) { super(a, b); }
         |  public $name(String s)        { super(s, 7); }
         |}""".stripMargin
    java(src, "com/demo/Par.java", par)
    java(src, "com/demo/Kept.java", sub("Kept"))
    java(src, "com/demo/Gone.java", sub("Gone"))
    val inject = root.resolve("overrides")
    java(inject, "com/demo/Gone.scala", "package com.demo\nclass Gone(a: Object, b: Int) extends Par(a, b)")
    val r = run(root, src,
      files = List("com/demo/Par.java", "com/demo/Kept.java", "com/demo/Gone.java")) { p =>
      p.copy(subs = Substitutions(dropTypes = Set("com.demo.Gone"), inject = List(inject)))
    }
    val supers = r.report.omissions.filter(_.what == "super(args) dropped")
    assertEquals(supers.map(_.owner), List("com.demo.Kept"))
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
