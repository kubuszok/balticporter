package balticporter.runner

import balticporter.core.*
import balticporter.sbtgen.SbtGen
import balticporter.transform.CollectionsTransform

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** BUILD-PROJECT GENERATION IS OPTIONAL — and this is the spec that says so in file names.
  *
  * The consumer this engine actually has (CLAUDE.md §4.45) calls `PortRun` from INSIDE a build it
  * already owns: an existing sbt project, a Maven module, a Bazel target. Its build file, its
  * `.gitignore` and its dependency declarations are DECISIONS that repository has already made, and
  * a porting engine that overwrites any of them is not usable there at all. So `project` is an
  * `Option`, `None` is the default, and with it the run must write the SOURCES and nothing else.
  *
  * Asserting the exact FILE SET rather than "build.sbt is absent" is the point. Every artifact this
  * run could leak is a file somebody has to notice, and the failure mode is always the same shape —
  * a write that looked incidental beside the one being reviewed (`PortMap.write` published maps into
  * the checkout for exactly that reason, CLAUDE.md §5.1). A set comparison fails on the NEXT one
  * too, which a named-file assertion cannot.
  *
  * The mirror direction is asserted in the same test rather than trusted: a gate that is never
  * observed to be OPEN is indistinguishable from a feature that was deleted.
  */
class PortRunProjectSpec extends munit.FunSuite:

  private def java(dir: Path, rel: String, src: String): Unit =
    val p = dir.resolve(rel)
    Files.createDirectories(p.getParent)
    Files.writeString(p, src)

  private def fixture(): (Path, Path) =
    val root = Files.createTempDirectory("portrun-project")
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

  /** every regular FILE under `dir`, relative and `/`-separated. Directories are excluded on
    * purpose: an empty directory is not an artifact a `git status` can see, and asserting on one
    * would make this spec fail for a reason nobody cares about. */
  private def files(dir: Path): List[String] =
    if !Files.exists(dir) then Nil
    else Files.walk(dir).iterator().asScala.filter(Files.isRegularFile(_))
      .map(p => dir.relativize(p).toString.replace('\\', '/')).toList.sorted

  private def run(portRoot: Path, src: Path, set: SourceSet = SourceSet.Main)(
      f: PortRun => PortRun = identity
  ): PortResult =
    f(PortRun(
      label     = "demo",
      portRoot  = portRoot,
      sourceSet = set,
      frontend  = FrontendConfig(src, List("com/demo/Widget.java", "com/demo/Gadget.java"), Nil),
      phases    = Nil,
    )).execute()

  test("project = None writes the SOURCES and nothing else — no build.sbt, no .gitignore, no engine pin") {
    val (root, src) = fixture()
    val port = root.resolve("port")
    val r = run(port, src)()
    assertEquals(files(port), List(
      "src_managed/main/scala/com/demo/Gadget.scala",
      "src_managed/main/scala/com/demo/Widget.scala",
    ))
    // stated individually too, because these are the four names a reader of this spec is looking for
    assert(!Files.exists(port.resolve("build.sbt")), "an existing build must not be overwritten")
    assert(!Files.exists(port.resolve(".gitignore")), "the consumer's ignore rules are its own")
    assert(!Files.exists(port.resolve(EnginePin.fileName)), "the pin is part of the generated build")
    assert(!Files.exists(port.resolve("project/build.properties")), "sbt's version is not this run's business")
    assertEquals(r.outDir, SbtGen.managedMain(port))
  }

  test("project = Some emits the skeleton — the same run, the gate OPEN") {
    val (root, src) = fixture()
    val port = root.resolve("port")
    val spec = SbtGen.ProjectSpec("demo", "org.demo", "3.8.4", "2.0.0-M4", Nil, engineFingerprint = "test")
    run(port, src)(_.copy(project = Some(spec)))
    assertEquals(files(port), List(
      ".gitignore",
      EnginePin.fileName,
      "build.sbt",
      "project/build.properties",
      "src_managed/main/scala/com/demo/Gadget.scala",
      "src_managed/main/scala/com/demo/Widget.scala",
    ).sorted)
  }

  test("the output location is the CALLER's: portRoot + sourceSet, and a test set creates no main tree") {
    // The knob a consumer needs is the one it already has. `portRoot` is an arbitrary directory —
    // it need not be an sbt project, need not exist, and nothing below it is assumed except the
    // `src_managed/<set>/scala` convention the caller opted into by naming a `SourceSet`. A test
    // run must therefore not materialise the MAIN side of that layout: doing so would be the engine
    // asserting a build shape on a repository that never asked for one.
    val (root, src) = fixture()
    val port = root.resolve("anywhere/at/all")
    val r = run(port, src, SourceSet.Test)()
    assertEquals(r.outDir, SbtGen.managedTest(port))
    assertEquals(files(port), List(
      "src_managed/test/scala/com/demo/Gadget.scala",
      "src_managed/test/scala/com/demo/Widget.scala",
    ))
    assert(!Files.exists(port.resolve("src_managed/main")), "a test source set is not half of a project skeleton")
  }

  test("everything a port SHIPS still lands under the output directory — injection, support, vendored runtime") {
    // The three other ways a file reaches the port. Each is a SOURCE, so each is written with
    // `project = None`; if one of them ever grew a build-shaped side effect this set would say so.
    val (root, src) = fixture()
    val port   = root.resolve("port")
    val inject = root.resolve("overrides")
    java(inject, "com/demo/Widget.scala", "package com.demo\nclass Widget { def label(): String = \"w\" }")
    val r = run(port, src)(_.copy(
      phases         = List(new CollectionsTransform),
      runtimeMode    = RuntimeMode.Vendored,
      subs           = Substitutions(dropTypes = Set("com.demo.Widget"), inject = List(inject)),
      supportSources = Map("com.demo.support.Helper" -> "package com.demo.support\nobject Helper"),
    ))
    val out = files(port)
    assert(out.forall(_.startsWith("src_managed/main/scala/")), clue(out))
    assert(out.contains("src_managed/main/scala/com/demo/Widget.scala"), clue(out))         // injected
    assert(out.contains("src_managed/main/scala/com/demo/support/Helper.scala"), clue(out)) // supportSources
    assertEquals(out.count(_.startsWith("src_managed/main/scala/balticporter/runtime/")),
                 CollectionsTransform.runtimeTypes.size)                                    // vendored
    assertEquals(r.injected, 1)
  }

  test("a TEST source set with a generated project vendors the runtime ONCE, into the test tree") {
    // The two-writer defect. `PortRun` writes the vendored runtime into `outDir` — which respects
    // `sourceSet` — and `SbtGen.emitPort` wrote it AGAIN into `managedMain(root)`, which cannot
    // respect anything because a build generator does not know which set the run is producing. So
    // this exact combination — Test + `project = Some` + `Vendored` — defined every support type
    // twice, in two trees compiled together, which is the failure `PortRun.runtimeMode`'s scaladoc
    // names ("a port that vendors into both `main` and `test`").
    //
    // Asserted as a COUNT over the whole port, not as "the main copy is absent": the question is
    // how many definitions of each FQN this port ships, and only one of the two writers may answer.
    val (root, src) = fixture()
    val port = root.resolve("port")
    val spec = SbtGen.ProjectSpec("demo", "org.demo", "3.8.4", "2.0.0-M4", Nil, engineFingerprint = "test")
    run(port, src, SourceSet.Test)(_.copy(
      project     = Some(spec),
      phases      = List(new CollectionsTransform),
      runtimeMode = RuntimeMode.Vendored,
    ))
    val runtime = files(port).filter(_.contains("/balticporter/runtime/"))
    assertEquals(runtime.size, CollectionsTransform.runtimeTypes.size, clue(runtime))
    assert(runtime.forall(_.startsWith("src_managed/test/scala/")), clue(runtime))
    // stated the other way too, because a count that happens to match is not a location
    assert(!Files.exists(port.resolve("src_managed/main/scala/balticporter")),
           "the build generator must not guess a source set the run already knows")
    // the gate is OPEN in the same run: the skeleton IS emitted, so this is not passing by absence
    assert(Files.exists(port.resolve("build.sbt")))
  }

  test("a manifest's `dependencies` reach the GENERATED BUILD — at this run's own configuration") {
    // The other end of `dependency-coverage`. The check CREDITS a declaration and stops reporting
    // the requirement; if the coordinate never reached a build file the credit would be a lie, and
    // nothing else in the run could say so — no count moves, no emitted source changes, and the
    // port compiles on the one backend somebody happened to test. So the two halves are asserted
    // together, in the file that owns the build-generation gate.
    val (root, src) = fixture()
    val dep = balticporter.catalog.ArtifactDep("io.github.cquiroz", "scala-java-time", "2.6.0")
    val spec = SbtGen.ProjectSpec("demo", "org.demo", "3.8.4", "2.0.0-M4", Nil, engineFingerprint = "test")
    val mf   = PortManifest(name = "demo", governs = Set("com.demo"), dependencies = List(dep))

    val main = root.resolve("main-port")
    run(main, src)(_.copy(project = Some(spec), manifest = Some(mf)))
    val mainBuild = Files.readString(main.resolve("build.sbt"))
    assert(mainBuild.contains(""""io.github.cquiroz" %% "scala-java-time" % "2.6.0""""), clue(mainBuild))
    assert(!mainBuild.contains("% Test"), clue(mainBuild))

    // …and a TEST source set's artifacts are its SUITE's: written into main's `libraryDependencies`
    // they would publish, on the shipped library, a coordinate only its tests call.
    val test = root.resolve("test-port")
    run(test, src, SourceSet.Test)(_.copy(project = Some(spec), manifest = Some(mf)))
    val testBuild = Files.readString(test.resolve("build.sbt"))
    assert(testBuild.contains(""""io.github.cquiroz" %% "scala-java-time" % "2.6.0" % Test"""), clue(testBuild))

    // the gate still holds over both: no manifest declaration writes a build where none was asked for
    val none = root.resolve("no-project")
    run(none, src)(_.copy(manifest = Some(mf)))
    assert(!Files.exists(none.resolve("build.sbt")), "a declared dependency is not a request for a build")
  }

  // -- the upstream NOTICE, for a library whose licence lives in ONE file (CLAUDE.md §4.57) -------
  //
  // The per-file banner NAMES a licence; MIT's single condition is that the copyright and
  // permission notice be INCLUDED. An MIT library commonly carries no per-file headers at all, so
  // reproducing every comment (§4.58) reproduces nothing, and no check in the pipeline can see the
  // difference — the port compiles, every count is flat, and the obligation is simply unmet.

  test("a declared notice is COPIED beside the emitted code, into the build product") {
    val (root, src) = fixture()
    val port    = root.resolve("port")
    val license = root.resolve("upstream/LICENSE")
    Files.createDirectories(license.getParent)
    Files.writeString(license, "MIT License\n\nCopyright (c) 2010 Someone\n")
    run(port, src)(_.copy(provenance = Some(Provenance(
      upstreamName = "demo", upstreamCommit = "abc", originalLicense = "MIT",
      sourcePathPrefix = "java", sourceRoot = src.toString, notices = List(license)))))
    // beside the sources, in `src_managed/` — the tree `clean` removes and `.gitignore` names, never
    // the port ROOT, where an untracked file blurs decision and artefact (§5.5). Byte-for-byte: the
    // port ships the upstream's own notice, not a rendering of it.
    assertEquals(Files.readString(port.resolve("src_managed/LICENSE")), Files.readString(license))
    assert(files(port).contains("src_managed/LICENSE"), clue(files(port)))
  }

  test("…and a port that declares NONE writes none — the empty default is the no-op") {
    val (root, src) = fixture()
    val port = root.resolve("port")
    run(port, src)(_.copy(provenance = Some(Provenance(
      upstreamName = "demo", upstreamCommit = "abc", originalLicense = "Apache-2.0",
      sourcePathPrefix = "java", sourceRoot = src.toString))))
    // the whole file set, so this fails on the NEXT stray artifact too — an Apache-2.0 port meets
    // the obligation through its per-file headers and must gain nothing here.
    assertEquals(files(port), List(
      "src_managed/main/scala/com/demo/Gadget.scala",
      "src_managed/main/scala/com/demo/Widget.scala",
    ))
  }

  test("a declared notice that is NOT THERE is fatal — never a port that silently ships no notice") {
    val (root, src) = fixture()
    val port = root.resolve("port")
    val e = intercept[RuntimeException](run(port, src)(_.copy(provenance = Some(Provenance(
      upstreamName = "demo", upstreamCommit = "abc", originalLicense = "MIT",
      sourcePathPrefix = "java", sourceRoot = src.toString,
      notices = List(root.resolve("upstream/NOTICE")))))))
    assert(clue(e.getMessage).contains("notice"))
  }
