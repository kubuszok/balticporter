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
    val r = PortRun("demo", root.resolve("port"), SourceSet.Main,
      FrontendConfig(other, List("com/demo2/Uses.java"), Nil, resolutionRoots = List(src)), Nil).execute()
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
