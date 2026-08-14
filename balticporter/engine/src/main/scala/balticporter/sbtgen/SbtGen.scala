package balticporter.sbtgen

import java.nio.file.{Files, Path}

import balticporter.core.{EngineInfo, EnginePin, RuntimeArtifact, RuntimeMode, RuntimePlan}
import balticporter.tir.Phase

/** Emits the sbt 2.0 project skeleton for a translated module (DESIGN.md §3.10).
  * Byte-deterministic; everything generated carries the do-not-edit header.
  */
object SbtGen:

  /** @param crossScala `%%` rather than `%` — a Scala library, cross-versioned by the port's own
    *                   `scalaVersion` rather than pinned to the engine's.
    * @param crossPlatform `%%%` — cross-versioned by the SCALA.JS / NATIVE version as well, which is
    *                   a fact about the artifact (`scalajs-weakreferences` is published that way)
    *                   rather than about the port. It needs the crossproject plugin — which a port
    *                   declaring such a dependency necessarily has — so the spelling is emitted
    *                   rather than downgraded: a `%%` written where `%%%` was meant resolves to an
    *                   artifact that does not exist, loudly, which is the right failure. */
  /** @param resolver where this artifact resolves FROM, when the default resolver set does not have
    *                 it. Rendered as a `resolvers` entry beside the dependency and deduplicated
    *                 across the spec, because a repository is a property of the BUILD and a
    *                 coordinate is a property of one line — see [[resolverBlock]] for why the entry
    *                 is NAMED from its own URL rather than from anything a caller could type. */
  final case class Dep(org: String, artifact: String, version: String, crossScala: Boolean = false,
                       crossPlatform: Boolean = false, resolver: Option[String] = None):
    def sbtString: String =
      val sep = if crossPlatform then "%%%" else if crossScala then "%%" else "%"
      s""""$org" $sep "$artifact" % "$version""""

  object Dep:
    def apply(c: RuntimeArtifact.Coordinates): Dep = Dep(c.organization, c.artifact, c.version, c.crossScala)

    /** …and the coordinate a catalog `Verdict.Depend` names, which a port copies into
      * `PortManifest.dependencies` when it takes the recommendation. */
    def of(d: balticporter.catalog.ArtifactDep): Dep = Dep(
      d.org, d.name, d.rev,
      crossScala    = d.cross == balticporter.catalog.CrossKind.Scala,
      crossPlatform = d.cross == balticporter.catalog.CrossKind.Platform,
      resolver      = d.resolver,
    )

  /** @param runtime
    *   what the RUN owes this port: the `balticporter-runtime` dependency, or the vendored sources,
    *   or nothing. Never set by hand — [[emitPort]] derives it from the phases that ran, which is
    *   the whole point (a caller who has to remember the dependency is the footgun). The default is
    *   [[RuntimePlan.none]], so the BIR-path callers that predate this are unaffected.
    */
  final case class ProjectSpec(
      moduleName: String,
      organization: String,
      scalaVersion: String,
      sbtVersion: String,
      deps: List[Dep],
      testDeps: List[Dep] = Nil,
      testFramework: Option[String] = None, // e.g. com.novocode.junit.JUnitFramework fingerprint line
      engineFingerprint: String,
      runtime: RuntimePlan = RuntimePlan.none,
  ):
    /** the declared deps PLUS whatever the run's phases made necessary. What [[emit]] writes. */
    def allDeps: List[Dep] = deps ++ runtime.dependency.map(Dep.apply).toList

  /** Where a port's GENERATED Scala goes: `src_managed/{main,test}/scala`, never `src/`.
    *
    * `src/` is for the human-written part of a port — the hand-written shims and overrides a
    * library needs and the engine cannot derive. Everything the engine emits is a build product:
    * reproducible from the Java sources plus the manifest, invalidated by every engine change, and
    * therefore not something to commit or to diff by hand. Keeping the two in one tree makes it
    * impossible to tell, from a `git status`, whether a change is a decision or an artefact.
    *
    * `src_managed` is sbt's own name for exactly this (its default lives under `target/`); we lift
    * it to the project root only so a porter can read the output without digging through
    * `target/scala-3.x/`. [[emit]] wires it into `Compile/Test sourceGenerators` and into
    * `cleanFiles`, and writes the `.gitignore` — so `clean` removes it and git never sees it. */
  def managedDir(root: Path, config: String): Path =
    managedRoot(root).resolve(config).resolve("scala")

  /** the build-product tree ITSELF — every source set hangs off it, `.gitignore` names it and
    * `cleanFiles` removes it. The home for anything a run produces that is not a source file of one
    * configuration: the upstream LICENSE/NOTICE a port ships beside its derived work (CLAUDE.md
    * §4.57) belongs to the MODULE, not to `main` or to `test`, and both source sets of one port
    * write the same bytes here. */
  def managedRoot(root: Path): Path = root.resolve("src_managed")
  def managedMain(root: Path): Path = managedDir(root, "main")
  def managedTest(root: Path): Path = managedDir(root, "test")

  /** …and the RESOURCE tree of one source set, beside its `scala` one.
    *
    * A `META-INF/services` descriptor is a deliverable of the port and not a source file
    * (`balticporter.tir.ServiceProviders`), so it needs a home the build already treats as a build
    * product. Per SOURCE SET rather than beside the notices, because a resource is on ONE
    * configuration's classpath: a test-set descriptor on the main classpath would register a
    * provider the shipped library does not have. */
  def managedResources(root: Path, config: String): Path =
    managedRoot(root).resolve(config).resolve("resources")

  /** THE entry point for a port's BUILD: emit the skeleton with runtime delivery DERIVED from the
    * phases that were run.
    *
    * `emit(root, spec)` still exists for callers that run no TIR phases at all, but everything on
    * the phase pipeline should come through here. The reason is in CLAUDE.md §1(b): "which support
    * types a port needs" is not policy a porting program should be restating — it is a consequence
    * of the phase list, and deriving it is what makes forgetting impossible. The same
    * [[RuntimePlan]] also carries `concreteMembers` for `TirEmitter`'s `externalConcrete`, so the
    * build file and the emitter cannot disagree about what was injected.
    *
    * ==What this does NOT do: write the vendored sources==
    *
    * It used to, into `managedMain(root)`, which is right exactly when the port has one source set
    * and that set is `main`. `balticporter.runner.PortRun` writes them into ITS OWN `outDir` — the
    * only place that knows which [[balticporter.runner.SourceSet]] the run is producing — so a run
    * with `sourceSet = Test` and a generated project wrote the whole vendored runtime TWICE, once
    * into `src_managed/test` and once into `src_managed/main`, which is the "defines every support
    * type twice" failure `PortRun.runtimeMode`'s own scaladoc warns about. Worse, the second copy
    * appeared only when `project` was `Some`, so the same port emitted a different file set
    * depending on whether it also generated a build.
    *
    * The run owns the SOURCES and this owns the BUILD: writing them here can only ever guess the
    * source set, and a guess that is usually right is the shape of defect this split removes. A
    * caller with no `PortRun` behind it writes them itself from the returned plan —
    * `plan.writeSources(dir)` — naming the directory it means.
    *
    * @param phases the phases that RAN (order irrelevant; only `RequiresRuntime` is read)
    * @param mode   [[RuntimeMode.Dependency]] by default; `Vendored` is the zero-dependency escape
    *               hatch, correct only for a single-module port
    * @return the plan, so the caller can pass `plan.concreteMembers` to the emitter and, if it has
    *         no run behind it, write `plan.sources` where it wants them
    */
  def emitPort(
      root: Path,
      spec: ProjectSpec,
      phases: List[Phase],
      mode: RuntimeMode = RuntimeMode.Dependency,
  ): RuntimePlan =
    val plan = RuntimePlan.of(phases, mode)
    emit(root, spec.copy(runtime = plan))
    plan

  def emit(root: Path, spec: ProjectSpec): Unit =
    Files.createDirectories(root.resolve("project"))
    Files.createDirectories(managedMain(root))
    Files.writeString(
      root.resolve("project/build.properties"),
      s"sbt.version=${spec.sbtVersion}\n",
    )
    Files.writeString(root.resolve(".gitignore"), gitignore)
    if spec.testDeps.nonEmpty then Files.createDirectories(managedTest(root))
    EnginePin.write(root, EnginePin.current(spec.runtime))
    val resolvers = resolverBlock(spec.allDeps ++ spec.testDeps)
    val deps =
      if spec.allDeps.isEmpty then ""
      else
        spec.allDeps
          .map(d => s"  ${d.sbtString},")
          .mkString("\nlibraryDependencies ++= Seq(\n", "\n", "\n)\n")
    val testDeps =
      if spec.testDeps.isEmpty then ""
      else
        spec.testDeps
          .map(d => s"  ${d.sbtString} % Test,")
          .mkString("\nlibraryDependencies ++= Seq(\n", "\n", "\n)\n")
    Files.writeString(
      root.resolve("build.sbt"),
      s"""// Generated by Baltic Porter (${spec.engineFingerprint}) — DO NOT EDIT; regenerate instead.
         |// Engine ${EngineInfo.version}; pinned machine-readably in ${EnginePin.fileName}, which
         |// `balticporter.core.EnginePin.check` compares against the engine regenerating this port.
         |
         |name := "${spec.moduleName}"
         |organization := "${spec.organization}"
         |scalaVersion := "${spec.scalaVersion}"
         |
         |// upstream suites mutate shared registries — run serially, like Maven did
         |Test / parallelExecution := false
         |
         |// upstream code (DecimalFormat/SimpleDateFormat) is locale-sensitive and its
         |// CI runs English — the faithful translation inherits that assumption
         |Test / fork := true
         |Test / javaOptions ++= Seq("-Duser.language=en", "-Duser.country=US")
         |$managedSources$resolvers
         |$deps$testDeps""".stripMargin,
    )

  /** The `resolvers` a spec's dependencies imply — EMPTY where every coordinate resolves from the
    * default set, which is every port that predates the field.
    *
    * ==Why the repository travels with the coordinate==
    * A `libraryDependencies` line naming an artifact the build cannot reach resolves NOTHING, and
    * the failure is a resolver error in somebody's build rather than anything this engine reports.
    * The coordinate already knows where it lives (`ArtifactDep.resolver`), so the build says it
    * here and no port has to remember a second, unlinked fact — which is CLAUDE.md §1.5's rule read
    * at a repository: a build fact stated twice is one that drifts.
    *
    * ==The NAME is derived, and that is not cosmetic==
    * sbt's `"name" at "url"` wants a label, and a label is a value a caller could type — so it is a
    * value that can disagree with the URL beside it, and nothing could ever check it. Deriving it
    * from the URL makes it a rendering of a fact rather than a second fact, keeps the emitted build
    * byte-deterministic (`SbtGenRuntimeSpec`'s own gate), and makes two ports naming one repository
    * emit one entry rather than two that differ by a word. */
  def resolverBlock(deps: List[Dep]): String =
    deps.flatMap(_.resolver).distinct.sorted match
      case Nil => ""
      case rs  =>
        rs.map(u => s"""resolvers += "${resolverName(u)}" at "$u"""")
          .mkString("\n// …and WHERE they resolve from: a coordinate published outside the default\n" +
            "// resolver set is a coordinate this build otherwise cannot fetch.\n", "\n", "\n")

  /** a stable, injective-enough label for a repository URL: the scheme dropped and every character
    * sbt would not want in an identifier folded to `-`. Never a name a caller supplied — see
    * [[resolverBlock]]. */
  def resolverName(url: String): String =
    url.replaceFirst("^[a-zA-Z]+://", "").replaceAll("[^A-Za-z0-9.]+", "-").stripSuffix("-")

  /** `.gitignore` for a port: the emitted Scala is a build product, not source. */
  val gitignore: String =
    """# Generated by Baltic Porter — DO NOT EDIT; regenerate instead.
      |# Emitted Scala is a build product: reproducible from the Java sources plus the
      |# manifest, and invalidated by every engine change. Only src/ is committed.
      |src_managed/
      |target/
      |""".stripMargin

  /** The sbt settings that make `src_managed/` a real managed-source root.
    *
    * A `sourceGenerator` that merely LISTS the directory is the idiomatic way to register sources
    * some other process wrote: sbt re-globs on every compile, so re-running the migration is picked
    * up without a reload, and the files still count as *managed* (excluded from `package-src`,
    * skipped by source formatters). `cleanFiles` is what makes `clean` delete them — sbt only
    * cleans `target/` by default, and the whole point of lifting `src_managed` to the project root
    * is that it is NOT under `target/`. */
  val managedSources: String =
    """
      |// Emitted Scala lives in src_managed/, is gitignored, and `clean` deletes it.
      |Compile / sourceGenerators += Def.task {
      |  ((baseDirectory.value / "src_managed" / "main" / "scala") ** "*.scala").get()
      |}.taskValue
      |Test / sourceGenerators += Def.task {
      |  ((baseDirectory.value / "src_managed" / "test" / "scala") ** "*.scala").get()
      |}.taskValue
      |// …and the RESOURCE half, which a sourceGenerator cannot carry: a `META-INF/services`
      |// descriptor the run wrote is on the CLASSPATH or the ServiceLoader finds nothing, and
      |// "finds nothing" is exactly the silent failure the key exists to remove.
      |Compile / unmanagedResourceDirectories += baseDirectory.value / "src_managed" / "main" / "resources"
      |Test / unmanagedResourceDirectories += baseDirectory.value / "src_managed" / "test" / "resources"
      |cleanFiles += baseDirectory.value / "src_managed"
      |""".stripMargin
