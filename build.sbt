ThisBuild / organization     := "com.kubuszok"
ThisBuild / organizationName := "Baltic Porter"
// The one Scala version this build compiles with, named because the `runtime` project matrix has
// to state it per platform row as well (`ThisBuild / scalaVersion` is not visible to the matrix's
// axis constructors, which run while the build is being assembled).
val scalaV = "3.8.4"

ThisBuild / scalaVersion     := scalaV

// ---------------------------------------------------------------------------------------------
// VERSION SCHEME
//
// early-semver, driven from ONE place. `BALTICPORTER_VERSION` is what a release build sets (a tag
// name, `0.1.0`); everything else is the snapshot of the next patch. There is no `version.sbt` and
// no dynver: the version must be reproducible from the environment alone, because it is baked into
// generated code (`balticporter.core.BuildVersion` -> `EngineInfo.version`) and therefore into
// every emitted port's header and its `balticporter-runtime` dependency. A version derived from
// local git state would make two checkouts of the same commit emit different bytes.
//
// Pre-1.0 policy (early-semver): the MINOR is the compatibility unit. A change to any emitted
// construct, to the TIR, or to `balticporter.runtime`'s SHAPE bumps the minor; a fix that leaves
// all four checks and the emitted bytes alone bumps the patch. `balticporter-runtime` shares the
// version with the engine on purpose — see `RuntimeArtifact` for why divergence there is a
// correctness bug and not a packaging preference.
// ---------------------------------------------------------------------------------------------
ThisBuild / version       := sys.env.getOrElse("BALTICPORTER_VERSION", "0.1.0-SNAPSHOT")
ThisBuild / versionScheme := Some("early-semver")

ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Wunused:all",
)

// ---------------------------------------------------------------------------------------------
// PUBLISHING (Maven-Central-shaped)
//
// Applied at `ThisBuild`, so every module inherits it; the modules that must NOT ship say
// `publish / skip := true` individually (`corpus`, `sge`, `root`).
//
// NOT set up here, deliberately: CI credentials and the Sonatype Central portal bundle upload.
// `publishTo` below is the classic OSSRH staging/snapshot shape, which is what `publishSigned` and
// `sbt-sonatype` consume; moving to the Central Portal is a plugin plus a token, not a build
// restructure. `publishLocal` — the only publish this repository can perform unattended — is
// unaffected by either choice.
// ---------------------------------------------------------------------------------------------
ThisBuild / description := "Baltic Porter — a deterministic engine for porting Java libraries to Scala 3."
ThisBuild / homepage    := Some(uri("https://github.com/kubuszok/balticporter"))
ThisBuild / licenses    := Seq("Apache-2.0" -> uri("https://www.apache.org/licenses/LICENSE-2.0.txt"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    uri("https://github.com/kubuszok/balticporter"),
    "scm:git:git@github.com:kubuszok/balticporter.git",
  )
)
ThisBuild / developers := List(
  Developer(
    id = "kubuszok",
    name = "Mateusz Kubuszok",
    email = "mateusz@kubuszok.com",
    url = uri("https://kubuszok.com"),
  )
)
ThisBuild / publishMavenStyle := true
ThisBuild / publishTo := Some(
  if ((ThisBuild / version).value.endsWith("SNAPSHOT"))
    "sonatype-snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots"
  else
    "sonatype-staging" at "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2"
)
ThisBuild / pomIncludeRepository   := { _ => false }
ThisBuild / Test / publishArtifact := false

// SERIAL TESTS, ACROSS THE WHOLE BUILD, and not as tidiness: `CheckReport` is gated on process-global
// system properties (`balticporter.report`, `balticporter.reportDir`), which `PortRunSpec.withReport`
// sets and restores around one run. sbt runs suites in parallel BOTH within a module and across
// modules, and `ManifestSpec`, `PortRunSpec` and `corpus` all execute `PortRun` — so any two of
// them race on `reportDir`, one run's report lands in another's directory, and whichever test then
// reads `run-latest/*.tsv` fails.
//
// Measured: 39 tests in the former `runner` module alone, a DIFFERENT one failing about one run in
// three, each passing in isolation. Present at f1df4b6 and before, which is why it read as noise
// rather than as the measurement gate this project's discipline (CLAUDE.md §5) rests on being
// unreliable. Scoping the setting to that module fixed the module and NOT the build: `ManifestSpec`
// failed again as soon as a whole-build `testFull` ran it beside `corpus`.
//
// The DESIGN fix is to make the report directory a value the run owns rather than a process-global
// flag — the same rule §5.1 already states for the source map ("`TirEmitter.srcMap` is a value one
// emitter owns, never a process-global table"), one level up. Until that lands, serial is the honest
// holding position rather than a retry or a tolerance.
ThisBuild / Test / parallelExecution := false
// …and that setting alone is NOT enough, measured: it serialises test CLASSES within one project,
// while sbt still runs different projects' test TASKS concurrently in the same unforked JVM. Four
// suites (CheckReportSpec, PipelineDebugSpec, PortRunSpec, SrcMapEmitSpec — four separate projects
// before the module graph was consolidated, all in `engine` now, with `corpus` still beside it) open
// set-and-restore windows on the same `balticporter.report*` system properties — the §4.6 flag
// channel, which is process-global BY DESIGN because production is one migration per JVM — and an
// overlap flips CheckReport on under another suite's run. Measured: ManifestSpec 1-in-5 under
// `testFull`; on another run CheckReportSpec and SrcMapEmitSpec — the two contamination detectors,
// in two projects — failed in the same instant. `Global / concurrentRestrictions += Tags.limit(
// Tags.Test, 1)` was tried first and measured NOT to prevent the overlap (1 contaminated run in 6
// with the line in place), so the fix is the one that cannot miss: a JVM per project's test task.
// Properties cannot cross processes, and "one migration per JVM" becomes true of tests too.
// Side effect, welcome: a forked test JVM is fresh, so the M5.5 classloader-layer staleness cannot
// bite a test run.
ThisBuild / Test / fork := true

val munit = "org.scalameta" %% "munit" % "1.2.0" % Test

// ---------------------------------------------------------------------------------------------
// `balticporter-runtime` — the support types EMITTED CODE links against.
//
// Not a part of the engine: nothing here is imported by `core`, and `core` deliberately does not
// `dependsOn` it. It is the artifact a PORT depends on, so that two ports of two modules of the
// same library (sge is core plus 17) share ONE `balticporter.runtime.JavaIterator` instead of each
// carrying a copy at the same FQN — which the Scala.js and Native linkers reject outright, and
// which the JVM merely tolerates while the bodies silently diverge between engine versions.
//
// Constraints this module lives under, which its tests do not check for you:
//   * NOTHING JVM-ONLY. No reflection, no threads, no I/O, no `java.*` beyond what Scala.js and
//     Native implement (`UnsupportedOperationException` is fine). Emitted ports target the same
//     platforms sge does.
//   * The layout is plain `src/main/scala`, SHARED by all three rows. That is what a
//     `projectMatrix` buys over a `crossProject`: the matrix keeps one base directory and one
//     source tree and varies only the compiler, so there is no `shared/src` and no directory
//     move — which matters because `engine`'s resource generator VENDORS this exact tree, and a
//     vendored copy that is one platform's view of the module is not the module.
//   * The three rows are the platforms sge and ssg publish for (CLAUDE.md §1.5): the JVM row keeps
//     the artifact name `balticporter-runtime` (`balticporter-runtime_3`), the others take the
//     platform suffix their linkers key on (`_sjs1_3`, `_native0.5_3`). A port resolving `%%%`
//     therefore finds the same version of the same types on whichever platform it links for,
//     which is the whole reason this is a published artifact rather than a per-port source drop.
//   * The JS and Native rows are the ONLY instrument that checks the first constraint above.
//     Nothing else in this build can fail when a `java.*` that only the JVM implements arrives
//     here — the JVM row compiles it, every port compiles against it, and the link error lands
//     in the consumer's repository (CLAUDE.md §4.45).
//   * Version-locked to the engine (`ThisBuild / version`); see the version-scheme note above.
//
// Project ids: `runtimeJVM`, `runtimeJS`, `runtimeNative`. The Scala version is the only default
// axis, so the platform is always spelled out — the alternative (JVM as a default axis, giving a
// bare `runtime`) would make the one row that is NOT checking anything the unmarked case.
// ---------------------------------------------------------------------------------------------
lazy val runtime = (projectMatrix in file("balticporter/runtime"))
  .defaultAxes(VirtualAxis.scalaABIVersion(scalaV))
  .settings(
    name        := "balticporter-runtime",
    description := "Support types that Baltic-Porter-emitted Scala links against.",
    libraryDependencies += munit,
  )
  .jvmPlatform(scalaVersions = Seq(scalaV))
  // `ThisBuild / Test / fork := true` (see the SERIAL TESTS note above) is a JVM statement: the
  // Scala.js and Native test tasks refuse a forked JVM outright, and neither can be reached by
  // the system-property contamination that setting exists to prevent — a JS test runs in Node and
  // a Native one is a linked binary, so there is no shared JVM to contaminate.
  .jsPlatform(scalaVersions = Seq(scalaV), settings = Seq(Test / fork := false))
  .nativePlatform(
    scalaVersions = Seq(scalaV),
    settings = Seq(
      Test / fork := false,
      // munit 1.2.0 is built against scala-native `test-interface` 0.5.8 and the toolchain here is
      // 0.5.12, which sbt's STRICT eviction reads as a binary-compatibility conflict and refuses.
      // They are compatible within 0.5.x — `../sge/build.sbt` carries the same downgrade for the
      // same pair — and the alternative, pinning the test framework to whatever the toolchain
      // happens to ship, would make a munit bump a toolchain decision.
      evictionErrorLevel := Level.Warn,
    ),
  )

// The JVM row, which is the one every other module means when it says `runtime`: the vendoring
// resource generator, `BuildVersion.runtimeArtifact` and the source-root lists all read it.
// Naming it once here keeps those readers from each spelling out a matrix lookup.
lazy val runtimeJvmRow: Project = runtime.jvm(scalaV)

// ---------------------------------------------------------------------------------------------
// `balticporter-api` — what a TRANSFORM OR CHECK AUTHOR compiles against, and nothing more.
//
// The consumer of this framework is an agent in ANOTHER repository (CLAUDE.md §4.45) writing a
// §1(c) rule for its own library. What that costs it must be one dependency, and that dependency
// must not drag the emitter, the orchestrator or Spoon in behind it. So this module is the MODEL
// and the CONTRACTS: the TIR (`Tree`, `Symbol`, `SymId`, `TypeRepr`, `Origin`, `Trivia`,
// `Program`, `Xref`), `Phase`/`Plugin`/`StandardTraversal`/`Pipeline`, the decision model
// (`Decision`, `Reason`, `DecisionLog`), the recording surface a check reports through
// (`CheckReport`, `PolicyReport`), the debug-flag surface (`DebugFlags`, `TirTrace`,
// `TirPrinter`), the frontend contract (`Frontend`, `FrontendConfig`, `Unsupported`, and the
// frozen BIR a frontend still populates) and `PortManifest`/`Substitutions` — the port's policy
// as a value.
//
// It depends on NOTHING. That is the property worth keeping: the day it needs the emitter or the
// runner to compile, it has stopped being the surface a rule author codes against. See DESIGN.md
// §3.2 for the cut and the two judgement calls in it.
// ---------------------------------------------------------------------------------------------
lazy val api = project
  .in(file("balticporter/api"))
  .settings(
    name := "balticporter-api",
    description := "The Baltic Porter model and contracts a transform, check or frontend is written against.",
    libraryDependencies += munit,
  )

// ---------------------------------------------------------------------------------------------
// `balticporter-engine` — the machinery. Everything that is not the surface above: the universal
// and parameterised transforms, every check implementation, the phase pipeline's callers, the
// TIR→Scala emitter, the vocabulary tables, the sbt project generator, the verification passes,
// the BIR passes and printer, and `PortRun` — the one entry point.
//
// It depends on `frontend-spoon` because `PortRun` models a source set with `SpoonTir`; the
// direction is engine → frontend, never the reverse, which is what keeps the insulation rule
// (DESIGN.md §3.2) true: no Spoon type is visible here.
// ---------------------------------------------------------------------------------------------
lazy val engine = project
  .in(file("balticporter/engine"))
  .dependsOn(api, `frontend-spoon`)
  .settings(
    name := "balticporter-engine",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "scalameta" % "4.17.2", // `verify` — skeleton diff over emitted Scala
      // The CONFIG front door (`PortConfig`, `PortConfigMain`). Deliberately here and not in `api`:
      // the SPI a rule author implements takes `balticporter.tir.ConfigView`, so `api` keeps the
      // property DESIGN.md §3.2 asks of it — it depends on nothing. No derivation library beside
      // it: the schema is ~15 keys read by hand, and a derived reader could not produce the
      // unknown-key refusal that is half the point (HOCON tolerates junk; a port must not).
      "com.typesafe"   % "config"    % "1.4.5",
      munit,
    ),
    // The engine's coordinates, generated from the build so `EngineInfo.version` cannot drift from
    // the artifact version a port resolves. This is what makes `RuntimeArtifact.version` a lock.
    Compile / sourceGenerators += Def.task {
      val f = (Compile / sourceManaged).value / "balticporter" / "core" / "BuildVersion.scala"
      IO.write(
        f,
        s"""package balticporter.core
           |
           |/** Generated from build.sbt — DO NOT EDIT. The single place the engine's coordinates
           |  * are written; `EngineInfo` and `RuntimeArtifact` both read them from here, so the
           |  * version stamped into a generated project is the version that actually built it. */
           |private[balticporter] object BuildVersion:
           |  val organization: String    = "${organization.value}"
           |  val version: String         = "${version.value}"
           |  val scalaVersion: String    = "${scalaVersion.value}"
           |  val runtimeArtifact: String = "${(runtimeJvmRow / name).value}"
           |""".stripMargin,
      )
      Seq(f)
    }.taskValue,
    // VENDORING: the runtime module's real sources, copied verbatim into the engine's resources so
    // `RuntimeArtifact.sourceOf` can write them next to a zero-dependency port. A COPY, never a
    // second text — a divergence between the published trait and the vendored string is exactly
    // the bug the published artifact exists to prevent, one level down.
    Compile / resourceGenerators += Def.task {
      val srcDir = (runtimeJvmRow / Compile / scalaSource).value
      val outDir = (Compile / resourceManaged).value / "balticporter" / "vendored-runtime"
      IO.createDirectory(outDir)
      // One file per TYPE, named after it — that is what `RuntimeArtifact` keys the vendored map
      // by. `package.scala` carries the module's admission rule and declares nothing, so it is not
      // a vendorable unit; any other doc-only file must be excluded here too.
      val srcs = (srcDir ** "*.scala").get().filter(_.getName != "package.scala").sortBy(_.getName)
      val copied = srcs.map { f => val t = outDir / f.getName; IO.copyFile(f, t); t }
      val index  = outDir / "index.txt"
      IO.write(index, srcs.map(_.getName).mkString("", "\n", "\n"))
      copied :+ index
    }.taskValue,
    // …and the path back to the originals, for the test that proves the copy IS a copy.
    Test / resourceGenerators += Def.task {
      val f = (Test / resourceManaged).value / "balticporter" / "runtime-source-dir.txt"
      IO.write(f, (runtimeJvmRow / Compile / scalaSource).value.getAbsolutePath)
      Seq(f)
    }.taskValue,
    // …and the engine's own source root, for `PolicyKeyLintSpec` — a check whose subject is the
    // TEXT of the transform package, so it must find those files wherever the suite is run from.
    Test / resourceGenerators += Def.task {
      val f = (Test / resourceManaged).value / "balticporter" / "engine-source-dir.txt"
      IO.write(f, (Compile / scalaSource).value.getAbsolutePath)
      Seq(f)
    }.taskValue,
    // …and EVERY production source root, one per line, for `RealPathSpec`'s duplication scan. The
    // subject there is not one package but the whole shipped engine: §5.4's helper was reimplemented
    // four times in three modules, which is precisely the failure a single-module scan cannot see.
    Test / resourceGenerators += Def.task {
      val f = (Test / resourceManaged).value / "balticporter" / "production-source-dirs.txt"
      val roots = Seq(
        (api / Compile / scalaSource).value,
        (Compile / scalaSource).value,
        (`frontend-spoon` / Compile / scalaSource).value,
        (runtimeJvmRow / Compile / scalaSource).value,
      )
      IO.write(f, roots.map(_.getAbsolutePath).mkString("", "\n", "\n"))
      Seq(f)
    }.taskValue,
  )

// The ONLY module that sees Spoon types. It depends on `api` alone for the TIR path; the BIR path
// (`SpoonFrontend`) is served by the frozen BIR model, which is why that model lives in `api` too.
lazy val `frontend-spoon` = project
  .in(file("balticporter/frontend-spoon"))
  .dependsOn(api)
  .settings(
    name := "balticporter-frontend-spoon",
    libraryDependencies ++= Seq(
      "fr.inria.gforge.spoon" % "spoon-core" % "11.5.0",
      munit,
    ),
  )

// Helpers a CONSUMER writing tests against the engine needs: run Java source through phases and
// assert on the emitted Scala. Filled from what `corpus/src/test` repeats verbatim in every
// spec (see `PortFixture`). `munit` is a COMPILE dependency here — a testkit whose users write
// MUnit suites has to hand them the framework.
lazy val testkit = project
  .in(file("balticporter/testkit"))
  .dependsOn(api, engine, `frontend-spoon`)
  .settings(
    name := "balticporter-testkit",
    libraryDependencies += "org.scalameta" %% "munit" % "1.2.0",
  )

lazy val corpus = project
  .in(file("balticporter/corpus"))
  .dependsOn(api, engine, testkit, `frontend-spoon`)
  .settings(
    name := "balticporter-corpus",
    libraryDependencies += munit,
    publish / skip := true,
    Compile / run / fork := true,
    Compile / run / javaOptions += s"-Dbalticporter.root=${(ThisBuild / baseDirectory).value}",
    // …AND FOR TESTS, which is not symmetry — it is the one thing that makes an `assume`-guarded
    // spec RUN. `balticporter.root` was set for `run` only, so a FORKED test JVM answered `.` — the
    // SUBPROJECT directory — and every spec resolving a vendored upstream tree from it looked under
    // `balticporter/corpus/../sge`, which does not exist. `PortMapAcceptanceSpec` therefore
    // `assume`d itself away on every run of the corpus suite, silently: `sbt testOnly *` prints
    // `Skipped 1` and exits 0 (`CLAUDE.md` §5.1). That is the same spec whose hard-coded expectation
    // had already gone stale once for exactly this reason, and the previous fix addressed the OTHER
    // precondition. Nothing else about the build changes: a test that does not read the property is
    // unaffected, and one that does was not running.
    Test / javaOptions += s"-Dbalticporter.root=${(ThisBuild / baseDirectory).value}",
  )

// ---------------------------------------------------------------------------------------------
// PORTED LIBRARIES — sbt subprojects under `ported/`, one per destination module.
//
// Each is a `projectMatrix` over JVM, Scala.js and Scala Native so that the three platform
// compiles the lanes require are sbt tasks rather than separate scala-cli invocations — zinc's
// incremental compile and sbt 2's compile cache then make a policy iteration ONE migration plus
// ONE incremental JVM compile, with the JS/Native/ref compiles deferred to `-measure-full`.
//
// PROJECT IDS are prefixed `port-` to keep them visually and tab-complete separate from the
// engine modules. Matrix row ids follow sbt-projectmatrix's convention: `port-sgeJVM`,
// `port-sgeJS`, `port-sgeNative`. The prefix is cosmetic to sbt but structural to a human
// scanning `sbt projects` output.
//
// SOURCES are `src_managed/{main,test}/scala` (via `sourceGenerators`, exactly as the previous
// stub did) plus any hand-written `src/` the port carries. `src_managed/` stays a build product
// (CLAUDE.md §5.5): gitignored, removed by `clean`, regenerated by every migration.
//
// DEPENDENCIES per port come from the Justfile's `*_deps` variables, translated from scala-cli
// `--dependency` flags to `libraryDependencies`. A dependent port `dependsOn` its base project's
// JVM row (ashley on sge, etc.), which is how the base's emitted Scala reaches the dependent's
// classpath without being on the same scala-cli command line.
//
// REFERENCE-FLAG compiles stay in the measure lanes (`flags_compile` in scripts/_lib.sh),
// which continue to use scala-cli with the reference repo's scalacOptions. The sbt subprojects
// handle the JVM, JS and Native compiles; the ref compile is an additional check on the same
// emitted tree with stricter flags, and scala-cli's `--server=false` keeps it self-contained.
//
// These projects are NOT aggregated by `root` — a work-in-progress port must not break the
// main build. A `ports` aggregate of their own is defined below, so `sbt ports/compile` reaches
// them all while `sbt compile` stays the engine's.
// ---------------------------------------------------------------------------------------------

// The port's ACTUAL directory — `projectMatrix` sets `baseDirectory` to `.sbt/matrix/<id>`,
// not to the `in(file(...))` path, so source generators must use the REAL directory. Each port
// passes its directory name (the part after `ported/`) to these helpers, and the actual file
// path is resolved INSIDE each Def.task via `(ThisBuild / baseDirectory).value`.

// Shared settings every ported project carries.
def portSettings(dir: String): Seq[Setting[?]] = Seq(
  publish / skip := true,
  scalacOptions := Seq("-nowarn"),
  cleanFiles += (ThisBuild / baseDirectory).value / "ported" / dir / "src_managed",
)

// Source generators for src_managed/{main,test}/scala — the emitted build products.
// Uses (ThisBuild / baseDirectory) / "ported" / dir (the real directory) instead of
// baseDirectory, because projectMatrix resolves baseDirectory to .sbt/matrix/<id> and the
// sourceGenerators would find an empty directory there.
def portSourceGenerators(dir: String): Seq[Setting[?]] = Seq(
  Compile / sourceGenerators += Def.task {
    val pd = (ThisBuild / baseDirectory).value / "ported" / dir
    ((pd / "src_managed" / "main" / "scala") ** "*.scala").get()
  }.taskValue,
  Test / sourceGenerators += Def.task {
    val pd = (ThisBuild / baseDirectory).value / "ported" / dir
    ((pd / "src_managed" / "test" / "scala") ** "*.scala").get()
  }.taskValue,
  // Also add the port's src/ directory to unmanaged sources (for hand-written shims/tests).
  Compile / unmanagedSourceDirectories += (ThisBuild / baseDirectory).value / "ported" / dir / "src" / "main" / "scala",
  Test / unmanagedSourceDirectories += (ThisBuild / baseDirectory).value / "ported" / dir / "src" / "test" / "scala",
)

// JS/Native platform settings shared by every port's cross rows.
val portJsSettings: Seq[Setting[?]] = Seq(Test / fork := false)
val portNativeSettings: Seq[Setting[?]] = Seq(
  Test / fork := false,
  evictionErrorLevel := Level.Warn, // munit vs scala-native test-interface; same as runtime
)

// ---------------------------------------------------------------------------------------------
// REFERENCE-BUILD SCALAC OPTIONS (DESIGN.md §8.24)
//
// The flag list is READ from the reference repo's SgePlugin / ssg's build.sbt, not hand-copied.
// `-Xmacro-settings:*` is dropped (macro timeouts, not diagnostics). The Justfile declares the
// same strings as shell variables for documentation; these vals are what `port-*-ref` projects
// compile with.
//
// The three are identical today (ssg copied sge's strict set); the three names exist as
// documentation for where each came from and WHY each lane uses the one it does.
// ---------------------------------------------------------------------------------------------
val sgeStrictFlags: Seq[String] = Seq(
  "-deprecation", "-feature", "-language:implicitConversions", "-no-indent",
  "-Werror", "-Wimplausible-patterns", "-Wrecurse-with-default",
  "-Wenum-comment-discard", "-Wunused:imports,privates,locals,patvars,nowarn",
)
val sgeRelaxedFlags: Seq[String] = Seq(
  "-deprecation", "-feature", "-language:implicitConversions", "-no-indent",
  "-Werror", "-Wimplausible-patterns", "-Wrecurse-with-default",
  "-Wenum-comment-discard",
)
val ssgFlags: Seq[String] = Seq(
  "-deprecation", "-feature", "-no-indent",
  "-Werror", "-Wimplausible-patterns", "-Wrecurse-with-default",
  "-Wenum-comment-discard", "-Wunused:imports,privates,locals,patvars,nowarn",
)

// Shared settings for `port-*-ref` projects: the reference repo's own scalacOptions instead of
// `-nowarn`. JVM-only plain projects sharing the port's source generators, so a compile under the
// reference build's flags is `sbt --client port-sge-ref/compile`.
// NOTE: `scalacOptions` is NOT set here — each ref project must set it in its own `.settings()`
// block AFTER calling this, because sbt 2.0's `-Wunused` deduplication can drop a project-level
// `-Wunused:imports,...` when `ThisBuild / scalacOptions` carries `-Wunused:all`. Setting it
// separately after this helper avoids the interaction.
def refPortSettings(dir: String): Seq[Setting[?]] = Seq(
  publish / skip := true,
) ++ portSourceGenerators(dir)

// ---------------------------------------------------------------------------------------------
// port-sge — libGDX core (ported/sge).
//
// Standalone (JDK-only like libGDX core itself). sge_strict_flags for the reference compile.
// Dependencies: lls (the emitted types reference lowlevel.Nullable); munit + junit for tests.
// ---------------------------------------------------------------------------------------------
lazy val `port-sge` = (projectMatrix in file("ported/sge"))
  .defaultAxes(VirtualAxis.scalaABIVersion(scalaV))
  .settings(portSettings("sge") *)
  .settings(portSourceGenerators("sge") *)
  .settings(
    name := "balticporter-port-sge",
    libraryDependencies ++= Seq(
      "com.kubuszok"          %% "lls"              % "0.3.0",
      "org.scalameta"         %% "munit"            % "1.2.0" % Test,
      "junit"                  % "junit"             % "4.13.2" % Test,
      "org.junit.jupiter"      % "junit-jupiter"     % "5.10.2" % Test,
    ),
  )
  .jvmPlatform(scalaVersions = Seq(scalaV))
  .jsPlatform(scalaVersions = Seq(scalaV), settings = portJsSettings)
  .nativePlatform(scalaVersions = Seq(scalaV), settings = portNativeSettings)

// The JVM row, which dependents `dependsOn` and lanes reference.
lazy val `port-sgeJVM`: Project = `port-sge`.jvm(scalaV)

// ---------------------------------------------------------------------------------------------
// port-sge-ecs — Ashley (ported/sge-ecs). Dependent on sge. sge_relaxed_flags.
// ---------------------------------------------------------------------------------------------
lazy val `port-sge-ecs` = (projectMatrix in file("ported/sge-ecs"))
  .defaultAxes(VirtualAxis.scalaABIVersion(scalaV))
  .dependsOn(`port-sge`)
  .settings(portSettings("sge-ecs") *)
  .settings(portSourceGenerators("sge-ecs") *)
  .settings(
    name := "balticporter-port-sge-ecs",
    libraryDependencies ++= Seq(
      "com.kubuszok"   %% "lls"            % "0.3.0",
      "org.scalameta"  %% "munit"          % "1.2.0" % Test,
      "junit"           % "junit"           % "4.13.2" % Test,
      "org.mockito"     % "mockito-all"     % "1.10.19" % Test,
    ),
  )
  .jvmPlatform(scalaVersions = Seq(scalaV))
  .jsPlatform(scalaVersions = Seq(scalaV), settings = portJsSettings)
  .nativePlatform(scalaVersions = Seq(scalaV), settings = portNativeSettings)

// ---------------------------------------------------------------------------------------------
// port-sge-graphs — simple-graphs (ported/sge-graphs). Standalone. sge_relaxed_flags.
// ---------------------------------------------------------------------------------------------
lazy val `port-sge-graphs` = (projectMatrix in file("ported/sge-graphs"))
  .defaultAxes(VirtualAxis.scalaABIVersion(scalaV))
  .settings(portSettings("sge-graphs") *)
  .settings(portSourceGenerators("sge-graphs") *)
  .settings(
    name := "balticporter-port-sge-graphs",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "1.2.0" % Test,
      "junit"          % "junit"  % "4.12"  % Test,
    ),
  )
  .jvmPlatform(scalaVersions = Seq(scalaV))
  .jsPlatform(scalaVersions = Seq(scalaV), settings = portJsSettings)
  .nativePlatform(scalaVersions = Seq(scalaV), settings = portNativeSettings)

// ---------------------------------------------------------------------------------------------
// port-sge-anim8 — anim8-gdx (ported/sge-anim8). Dependent on sge. sge_relaxed_flags.
// No upstream suite; hand-written MUnit tests in src/test/scala.
// ---------------------------------------------------------------------------------------------
lazy val `port-sge-anim8` = (projectMatrix in file("ported/sge-anim8"))
  .defaultAxes(VirtualAxis.scalaABIVersion(scalaV))
  .dependsOn(`port-sge`)
  .settings(portSettings("sge-anim8") *)
  .settings(portSourceGenerators("sge-anim8") *)
  .settings(
    name := "balticporter-port-sge-anim8",
    libraryDependencies ++= Seq(
      "com.kubuszok"  %% "lls"   % "0.3.0",
      "org.scalameta" %% "munit" % "1.2.0" % Test,
    ),
  )
  .jvmPlatform(scalaVersions = Seq(scalaV))
  .jsPlatform(scalaVersions = Seq(scalaV), settings = portJsSettings)
  .nativePlatform(scalaVersions = Seq(scalaV), settings = portNativeSettings)

// ---------------------------------------------------------------------------------------------
// port-sge-noise — noise4j (ported/sge-noise). Standalone, no deps. sge_relaxed_flags.
// ---------------------------------------------------------------------------------------------
lazy val `port-sge-noise` = (projectMatrix in file("ported/sge-noise"))
  .defaultAxes(VirtualAxis.scalaABIVersion(scalaV))
  .settings(portSettings("sge-noise") *)
  .settings(portSourceGenerators("sge-noise") *)
  .settings(name := "balticporter-port-sge-noise")
  .jvmPlatform(scalaVersions = Seq(scalaV))
  .jsPlatform(scalaVersions = Seq(scalaV), settings = portJsSettings)
  .nativePlatform(scalaVersions = Seq(scalaV), settings = portNativeSettings)

// ---------------------------------------------------------------------------------------------
// port-sge-jbump — jbump (ported/sge-jbump). Standalone, no deps. sge_relaxed_flags.
// RuntimeMode.Vendored — support types ship inside the emitted source set.
// ---------------------------------------------------------------------------------------------
lazy val `port-sge-jbump` = (projectMatrix in file("ported/sge-jbump"))
  .defaultAxes(VirtualAxis.scalaABIVersion(scalaV))
  .settings(portSettings("sge-jbump") *)
  .settings(portSourceGenerators("sge-jbump") *)
  .settings(name := "balticporter-port-sge-jbump")
  .jvmPlatform(scalaVersions = Seq(scalaV))
  .jsPlatform(scalaVersions = Seq(scalaV), settings = portJsSettings)
  .nativePlatform(scalaVersions = Seq(scalaV), settings = portNativeSettings)

// ---------------------------------------------------------------------------------------------
// port-sge-gltf — gdx-gltf (ported/sge-gltf). Dependent on sge. sge_relaxed_flags.
// ---------------------------------------------------------------------------------------------
lazy val `port-sge-gltf` = (projectMatrix in file("ported/sge-gltf"))
  .defaultAxes(VirtualAxis.scalaABIVersion(scalaV))
  .dependsOn(`port-sge`)
  .settings(portSettings("sge-gltf") *)
  .settings(portSourceGenerators("sge-gltf") *)
  .settings(
    name := "balticporter-port-sge-gltf",
    libraryDependencies ++= Seq(
      "com.kubuszok"  %% "lls"   % "0.3.0",
      "org.scalameta" %% "munit" % "1.2.0" % Test,
      "junit"          % "junit"  % "4.12"  % Test,
    ),
  )
  .jvmPlatform(scalaVersions = Seq(scalaV))
  .jsPlatform(scalaVersions = Seq(scalaV), settings = portJsSettings)
  .nativePlatform(scalaVersions = Seq(scalaV), settings = portNativeSettings)

// ---------------------------------------------------------------------------------------------
// port-sge-screens — libgdx-screenmanager (ported/sge-screens). Dependent on sge.
// sge_relaxed_flags. Hand-written src/ for both main and test.
// ---------------------------------------------------------------------------------------------
lazy val `port-sge-screens` = (projectMatrix in file("ported/sge-screens"))
  .defaultAxes(VirtualAxis.scalaABIVersion(scalaV))
  .dependsOn(`port-sge` % "test->test;compile->compile")
  .settings(portSettings("sge-screens") *)
  .settings(portSourceGenerators("sge-screens") *)
  .settings(
    name := "balticporter-port-sge-screens",
    libraryDependencies ++= Seq(
      "com.kubuszok"  %% "lls"   % "0.3.0",
      "org.scalameta" %% "munit" % "1.2.0" % Test,
    ),
  )
  .jvmPlatform(scalaVersions = Seq(scalaV))
  .jsPlatform(scalaVersions = Seq(scalaV), settings = portJsSettings)
  .nativePlatform(scalaVersions = Seq(scalaV), settings = portNativeSettings)

// ---------------------------------------------------------------------------------------------
// port-sge-vfx — gdx-vfx (ported/sge-vfx). Dependent on sge. sge_relaxed_flags.
// ---------------------------------------------------------------------------------------------
lazy val `port-sge-vfx` = (projectMatrix in file("ported/sge-vfx"))
  .defaultAxes(VirtualAxis.scalaABIVersion(scalaV))
  .dependsOn(`port-sge` % "test->test;compile->compile")
  .settings(portSettings("sge-vfx") *)
  .settings(portSourceGenerators("sge-vfx") *)
  .settings(
    name := "balticporter-port-sge-vfx",
    libraryDependencies ++= Seq(
      "com.kubuszok"  %% "lls"   % "0.3.0",
      "org.scalameta" %% "munit" % "1.2.0" % Test,
    ),
  )
  .jvmPlatform(scalaVersions = Seq(scalaV))
  .jsPlatform(scalaVersions = Seq(scalaV), settings = portJsSettings)
  .nativePlatform(scalaVersions = Seq(scalaV), settings = portNativeSettings)

// ---------------------------------------------------------------------------------------------
// port-sge-ai — gdx-ai (ported/sge-ai). Dependent on sge. sge_relaxed_flags.
// ---------------------------------------------------------------------------------------------
lazy val `port-sge-ai` = (projectMatrix in file("ported/sge-ai"))
  .defaultAxes(VirtualAxis.scalaABIVersion(scalaV))
  .dependsOn(`port-sge`)
  .settings(portSettings("sge-ai") *)
  .settings(portSourceGenerators("sge-ai") *)
  .settings(
    name := "balticporter-port-sge-ai",
    libraryDependencies ++= Seq(
      "com.kubuszok"  %% "lls"   % "0.3.0",
      "org.scalameta" %% "munit" % "1.2.0" % Test,
      "junit"          % "junit"  % "4.12"  % Test,
    ),
  )
  .jvmPlatform(scalaVersions = Seq(scalaV))
  .jsPlatform(scalaVersions = Seq(scalaV), settings = portJsSettings)
  .nativePlatform(scalaVersions = Seq(scalaV), settings = portNativeSettings)

// ---------------------------------------------------------------------------------------------
// port-sge-textra — TextraTypist (ported/sge-textra). Dependent on sge. sge_relaxed_flags.
// TextraTypist declares `com.github.tommyettinger:regexodus` as a dependency, which is declared
// in the port's manifest and derived by the lane via `declared_dep_flags`.
// ---------------------------------------------------------------------------------------------
lazy val `port-sge-textra` = (projectMatrix in file("ported/sge-textra"))
  .defaultAxes(VirtualAxis.scalaABIVersion(scalaV))
  .dependsOn(`port-sge`)
  .settings(portSettings("sge-textra") *)
  .settings(portSourceGenerators("sge-textra") *)
  .settings(
    name := "balticporter-port-sge-textra",
    libraryDependencies ++= Seq(
      "com.kubuszok"              %% "lls"       % "0.3.0",
      // regexodus — declared by the port's manifest (`TextraTypistPolicy.dependencies`), derived
      // from `run-latest/dependencies.tsv` in the scala-cli lanes via `declared_dep_flags`. Here
      // it is the build.sbt coordinate, pinned to the version the manifest declares. The emitted
      // Scala names six of its classes outright.
      "com.github.tommyettinger"   % "regexodus" % "0.1.21",
      "org.scalameta"             %% "munit"     % "1.2.0" % Test,
    ),
  )
  .jvmPlatform(scalaVersions = Seq(scalaV))
  .jsPlatform(scalaVersions = Seq(scalaV), settings = portJsSettings)
  .nativePlatform(scalaVersions = Seq(scalaV), settings = portNativeSettings)

// ---------------------------------------------------------------------------------------------
// port-sge-visui — VisUI's ui/ module (ported/sge-visui). Dependent on sge. sge_relaxed_flags.
// ---------------------------------------------------------------------------------------------
lazy val `port-sge-visui` = (projectMatrix in file("ported/sge-visui"))
  .defaultAxes(VirtualAxis.scalaABIVersion(scalaV))
  .dependsOn(`port-sge`)
  .settings(portSettings("sge-visui") *)
  .settings(portSourceGenerators("sge-visui") *)
  .settings(
    name := "balticporter-port-sge-visui",
    libraryDependencies ++= Seq(
      "com.kubuszok"  %% "lls"   % "0.3.0",
      "org.scalameta" %% "munit" % "1.2.0" % Test,
    ),
  )
  .jvmPlatform(scalaVersions = Seq(scalaV))
  .jsPlatform(scalaVersions = Seq(scalaV), settings = portJsSettings)
  .nativePlatform(scalaVersions = Seq(scalaV), settings = portNativeSettings)

// ---------------------------------------------------------------------------------------------
// port-sge-visui-usl — VisUI's USL skin-language compiler (ported/sge-visui-usl).
// Standalone (imports NO libGDX). sge_relaxed_flags.
// ---------------------------------------------------------------------------------------------
lazy val `port-sge-visui-usl` = (projectMatrix in file("ported/sge-visui-usl"))
  .defaultAxes(VirtualAxis.scalaABIVersion(scalaV))
  .settings(portSettings("sge-visui-usl") *)
  .settings(portSourceGenerators("sge-visui-usl") *)
  .settings(
    name := "balticporter-port-sge-visui-usl",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "1.2.0" % Test,
    ),
    // The test `.usl` fixtures the suite reads through `getResourceAsStream("/test-*.usl")` —
    // upstream paths, unchanged, because the lookup is a STRING LITERAL no rename may touch (§4.56).
    Test / unmanagedResourceDirectories += (ThisBuild / baseDirectory).value / ".." / "sge" / "original-src" / "vis-ui" / "usl" / "src" / "test" / "resources",
  )
  .jvmPlatform(scalaVersions = Seq(scalaV))
  .jsPlatform(scalaVersions = Seq(scalaV), settings = portJsSettings)
  .nativePlatform(scalaVersions = Seq(scalaV), settings = portNativeSettings)

// ---------------------------------------------------------------------------------------------
// port-ssg-liquid — liqp (ported/ssg-liquid). Standalone. ssg_flags.
// The ANTLR-generated parser classes are a jar/classdir on the classpath, not a source dependency.
// The lane adds them with `unmanagedJars` pointed at the output directory.
// ---------------------------------------------------------------------------------------------
lazy val `port-ssg-liquid` = (projectMatrix in file("ported/ssg-liquid"))
  .defaultAxes(VirtualAxis.scalaABIVersion(scalaV))
  .settings(portSettings("ssg-liquid") *)
  .settings(portSourceGenerators("ssg-liquid") *)
  .settings(
    name := "balticporter-port-ssg-liquid",
    libraryDependencies ++= Seq(
      "org.antlr"                       % "antlr4-runtime"            % "4.13.0",
      "com.fasterxml.jackson.core"      % "jackson-core"              % "2.15.0",
      "com.fasterxml.jackson.core"      % "jackson-databind"          % "2.13.4.2",
      "com.fasterxml.jackson.core"      % "jackson-annotations"       % "2.15.0",
      "com.fasterxml.jackson.datatype"  % "jackson-datatype-jsr310"   % "2.15.0",
      "ua.co.k"                          % "strftime4j"                % "1.0.6",
      // multiarch-serviceloader — declared by the port's manifest (`LiqpPolicy.dependencies`),
      // derived from `run-latest/dependencies.tsv` in the scala-cli lanes via `declared_dep_flags`.
      // The emitted Scala names `multiarch.serviceloader.ServiceProviders` outright. The snapshot
      // resolver is the Central Portal snapshot repository the artifact is published to.
      "com.kubuszok"                    %% "multiarch-serviceloader"   % "0.4.0-12-gc168b2f-SNAPSHOT",
      "org.scalameta"                   %% "munit"                     % "1.2.0" % Test,
      "junit"                            % "junit"                     % "4.13.1" % Test,
    ),
    resolvers += "Central Portal Snapshots" at "https://central.sonatype.com/repository/maven-snapshots",
    // The ANTLR parser class directory, produced by `LiqpClasspath` during the migration.
    // sbt 2.0's `Classpath` is `Seq[Attributed[HashedVirtualFileRef]]`; `fileConverter` converts
    // a `java.io.File` to the `HashedVirtualFileRef` sbt 2.0 expects.
    Compile / unmanagedClasspath ++= {
      val parserDir = (ThisBuild / baseDirectory).value / "out" / "liqp-parser-classes"
      if (parserDir.exists()) {
        val fc = fileConverter.value
        Seq(Attributed.blank(fc.toVirtualFile(parserDir.toPath)))
      } else Nil
    },
    Test / unmanagedClasspath ++= {
      val parserDir = (ThisBuild / baseDirectory).value / "out" / "liqp-parser-classes"
      if (parserDir.exists()) {
        val fc = fileConverter.value
        Seq(Attributed.blank(fc.toVirtualFile(parserDir.toPath)))
      } else Nil
    },
    // The SPI descriptor and test fixture resources.
    Test / unmanagedResourceDirectories += (ThisBuild / baseDirectory).value / "ported" / "ssg-liquid" / "src_managed" / "main" / "resources",
    // The test working directory — liqp's 45 tests read `./snippets/`, `./_includes/` and
    // `src/test/jekyll/` by RELATIVE path (a `new FileInputStream(new File(...))` the process CWD
    // decides, not `user.dir`). The lane creates symlinks under this directory before running.
    Test / baseDirectory := (ThisBuild / baseDirectory).value / ".balticporter" / "tmp" / "liqp-run",
  )
  .jvmPlatform(scalaVersions = Seq(scalaV))
  .jsPlatform(scalaVersions = Seq(scalaV), settings = portJsSettings)
  .nativePlatform(scalaVersions = Seq(scalaV), settings = portNativeSettings)

// ---------------------------------------------------------------------------------------------
// port-ssg-md — flexmark-java core + util modules (ported/ssg-md). Standalone. ssg_flags.
// ---------------------------------------------------------------------------------------------
lazy val `port-ssg-md` = (projectMatrix in file("ported/ssg-md"))
  .defaultAxes(VirtualAxis.scalaABIVersion(scalaV))
  .settings(portSettings("ssg-md") *)
  .settings(portSourceGenerators("ssg-md") *)
  .settings(
    name := "balticporter-port-ssg-md",
    libraryDependencies ++= Seq(
      // The annotation jar — 237 of 468 emitted files name `@org.jetbrains.annotations.NotNull`
      // because a MARKER annotation is carried into emitted Scala whatever the port claims. Without
      // it the compile reports 1976 unresolved references as this port's wall (see `md_deps`).
      "org.jetbrains"  % "annotations" % "24.0.1",
      "org.scalameta" %% "munit"       % "1.2.0" % Test,
      "junit"          % "junit"        % "4.13.2" % Test,
    ),
    // flexmark resources needed by the test run: the CommonMark spec files, the HTML5 entity
    // table (shipped by the port itself in src_managed/main/resources), and the flexmark-test-util
    // module-root marker. These are the three `--resource-dir` flags the scala-cli lane had.
    Test / unmanagedResourceDirectories ++= Seq(
      (ThisBuild / baseDirectory).value / ".." / "ssg" / "original-src" / "flexmark-java" / "flexmark-test-specs" / "src" / "main" / "resources",
      (ThisBuild / baseDirectory).value / "ported" / "ssg-md" / "src_managed" / "main" / "resources",
      (ThisBuild / baseDirectory).value / ".." / "ssg" / "original-src" / "flexmark-java" / "flexmark-test-util" / "src" / "main" / "resources",
    ),
  )
  .jvmPlatform(scalaVersions = Seq(scalaV))
  .jsPlatform(scalaVersions = Seq(scalaV), settings = portJsSettings)
  .nativePlatform(scalaVersions = Seq(scalaV), settings = portNativeSettings)

// ---------------------------------------------------------------------------------------------
// port-ssg-md-ext — flexmark extension modules (ported/ssg-md-ext). Dependent on ssg-md.
// ssg_flags.
// ---------------------------------------------------------------------------------------------
lazy val `port-ssg-md-ext` = (projectMatrix in file("ported/ssg-md-ext"))
  .defaultAxes(VirtualAxis.scalaABIVersion(scalaV))
  .dependsOn(`port-ssg-md`)
  .settings(portSettings("ssg-md-ext") *)
  .settings(portSourceGenerators("ssg-md-ext") *)
  .settings(
    name := "balticporter-port-ssg-md-ext",
    libraryDependencies ++= Seq(
      // The TWO third-party coordinates the extension modules declare.
      "org.nibor.autolink"   % "autolink"            % "0.6.0",
      "com.vladsch.flexmark" % "flexmark-ext-emoji"  % "0.64.8" % Test,
      "org.scalameta"       %% "munit"               % "1.2.0"  % Test,
      "junit"                % "junit"                % "4.13.2" % Test,
    ),
  )
  .jvmPlatform(scalaVersions = Seq(scalaV))
  .jsPlatform(scalaVersions = Seq(scalaV), settings = portJsSettings)
  .nativePlatform(scalaVersions = Seq(scalaV), settings = portNativeSettings)

// ---------------------------------------------------------------------------------------------
// DIFFERENTIAL LANE PROJECTS — test-only projects that compile the HAND-WRITTEN adapted suite
// against the port's emitted main classpath, WITHOUT including the port's emitted test sources
// (`src_managed/test/scala`). The three differential lanes (`ai-diff-measure`,
// `textra-diff-measure`, `visui-diff-measure`) each need this separation because they run the
// reference hand port's OWN suite, not the migrated one. `dependsOn` gives them the main
// classpath; their test sources come from `ported/<module>/src/test/scala`.
//
// These are JVM-only: the differential lanes do not carry xplat compiles (they compile
// hand-port tests, not emitted code).
// ---------------------------------------------------------------------------------------------

// port-sge-ai-diff — gdx-ai's differential gate. Test sources from ported/sge-ai/src/test/scala.
lazy val `port-sge-ai-diff` = (project in file(".ports/sge-ai-diff"))
  .dependsOn(`port-sge-ai`.jvm(scalaV))
  .settings(
    name := "balticporter-port-sge-ai-diff",
    publish / skip := true,
    scalacOptions := Seq("-nowarn"),
    Test / unmanagedSourceDirectories := Seq(
      (ThisBuild / baseDirectory).value / "ported" / "sge-ai" / "src" / "test" / "scala"
    ),
    libraryDependencies ++= Seq(
      "com.kubuszok"  %% "lls"   % "0.3.0",
      "org.scalameta" %% "munit" % "1.2.0" % Test,
      "junit"          % "junit"  % "4.12"  % Test,
    ),
  )

// port-sge-textra-diff — TextraTypist's differential gate. Test sources from ported/sge-textra/src/test/scala.
lazy val `port-sge-textra-diff` = (project in file(".ports/sge-textra-diff"))
  .dependsOn(`port-sge-textra`.jvm(scalaV))
  .settings(
    name := "balticporter-port-sge-textra-diff",
    publish / skip := true,
    scalacOptions := Seq("-nowarn"),
    Test / unmanagedSourceDirectories := Seq(
      (ThisBuild / baseDirectory).value / "ported" / "sge-textra" / "src" / "test" / "scala"
    ),
    libraryDependencies ++= Seq(
      "com.kubuszok"  %% "lls"   % "0.3.0",
      "org.scalameta" %% "munit" % "1.2.0" % Test,
    ),
  )

// port-sge-visui-diff — VisUI's differential gate. The SCOPED variant: the port is not at zero
// (8 attributed errors), so the main sources are restricted to the CLOSURE — the files the
// adapted suites transitively name — rather than the whole emitted tree. The closure is declared
// in the Justfile (`visui_closure`) and verified against `errors.tsv` by the lane.
//
// This project depends on `port-sge` (the BASE, which compiles clean) and carries the closure
// files as its OWN main sources, bypassing `port-sge-visui` entirely — if it `dependsOn`
// port-sge-visui, sbt would try to compile that project first and fail at the 8 errors. The
// closure files are picked by a sourceGenerator keyed on the same list the Justfile declares.
// Test sources from ported/sge-visui/src/test/scala.
lazy val `port-sge-visui-diff` = (project in file(".ports/sge-visui-diff"))
  .dependsOn(`port-sge`.jvm(scalaV))
  .settings(
    name := "balticporter-port-sge-visui-diff",
    publish / skip := true,
    scalacOptions := Seq("-nowarn"),
    // The CLOSURE — specific files from `src_managed/main/scala/sge/visui/`, the same list as
    // `visui_closure` in the Justfile. A sourceGenerator rather than unmanagedSourceDirectories
    // because we need individual FILES, not a whole directory.
    Compile / sourceGenerators += Def.task {
      val visuiEmit = (ThisBuild / baseDirectory).value / "ported" / "sge-visui" / "src_managed" / "main" / "scala" / "sge" / "visui"
      // The closure files — keep in sync with `visui_closure` in the Justfile.
      val closureFiles = Seq(
        "Sizes.scala",
        "util/ColorUtils.scala",
        "util/OsUtils.scala",
        "util/Validators.scala",
        "util/InputValidator.scala",
      )
      closureFiles.flatMap { f =>
        val p = visuiEmit / f
        if (p.exists()) Seq(p) else Nil
      }
    }.taskValue,
    Test / unmanagedSourceDirectories := Seq(
      (ThisBuild / baseDirectory).value / "ported" / "sge-visui" / "src" / "test" / "scala"
    ),
    libraryDependencies ++= Seq(
      "com.kubuszok"  %% "lls"   % "0.3.0",
      "org.scalameta" %% "munit" % "1.2.0" % Test,
    ),
  )

// ---------------------------------------------------------------------------------------------
// REFERENCE-FLAG COMPILE PROJECTS — `port-*-ref`, one per port.
//
// JVM-only plain projects that share the port's source generators and hand-written `src/`
// directories but compile with the reference repo's scalacOptions rather than `-nowarn`. This
// is the FOURTH compile in every lane (after JVM, JS, Native): a port that is green under
// `-nowarn` and red under `-no-indent -Werror -Wunused:…` is not at the bar (DESIGN.md §8.24).
//
// A dependent's `-ref` project `dependsOn` the base port's JVM row (NOT the base's ref),
// because the base's emitted Scala is compiled with `-nowarn` — its own ref lane already
// counted its warnings — and recompiling it under strict flags would double-count them.
// This is the same scoping `flags_compile` did by filtering the base's diagnostics.
// ---------------------------------------------------------------------------------------------

lazy val `port-sge-ref` = (project in file(".ports/sge-ref"))
  .settings(refPortSettings("sge") *)
  .settings(
    name := "balticporter-port-sge-ref",
    scalacOptions := sgeStrictFlags,
    libraryDependencies ++= Seq(
      "com.kubuszok"          %% "lls"              % "0.3.0",
      "org.scalameta"         %% "munit"            % "1.2.0" % Test,
      "junit"                  % "junit"             % "4.13.2" % Test,
      "org.junit.jupiter"      % "junit-jupiter"     % "5.10.2" % Test,
    ),
  )

lazy val `port-sge-ecs-ref` = (project in file(".ports/sge-ecs-ref"))
  .dependsOn(`port-sge`.jvm(scalaV))
  .settings(refPortSettings("sge-ecs") *)
  .settings(
    name := "balticporter-port-sge-ecs-ref",
    scalacOptions := sgeRelaxedFlags,
    libraryDependencies ++= Seq(
      "com.kubuszok"   %% "lls"            % "0.3.0",
      "org.scalameta"  %% "munit"          % "1.2.0" % Test,
      "junit"           % "junit"           % "4.13.2" % Test,
      "org.mockito"     % "mockito-all"     % "1.10.19" % Test,
    ),
  )

lazy val `port-sge-graphs-ref` = (project in file(".ports/sge-graphs-ref"))
  .settings(refPortSettings("sge-graphs") *)
  .settings(
    name := "balticporter-port-sge-graphs-ref",
    scalacOptions := sgeRelaxedFlags,
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "1.2.0" % Test,
      "junit"          % "junit"  % "4.12"  % Test,
    ),
  )

lazy val `port-sge-anim8-ref` = (project in file(".ports/sge-anim8-ref"))
  .dependsOn(`port-sge`.jvm(scalaV))
  .settings(refPortSettings("sge-anim8") *)
  .settings(
    name := "balticporter-port-sge-anim8-ref",
    scalacOptions := sgeRelaxedFlags,
    libraryDependencies ++= Seq(
      "com.kubuszok"  %% "lls"   % "0.3.0",
      "org.scalameta" %% "munit" % "1.2.0" % Test,
    ),
  )

lazy val `port-sge-noise-ref` = (project in file(".ports/sge-noise-ref"))
  .settings(refPortSettings("sge-noise") *)
  .settings(
    name := "balticporter-port-sge-noise-ref",
    scalacOptions := sgeRelaxedFlags,
  )

lazy val `port-sge-jbump-ref` = (project in file(".ports/sge-jbump-ref"))
  .settings(refPortSettings("sge-jbump") *)
  .settings(
    name := "balticporter-port-sge-jbump-ref",
    scalacOptions := sgeRelaxedFlags,
  )

lazy val `port-sge-gltf-ref` = (project in file(".ports/sge-gltf-ref"))
  .dependsOn(`port-sge`.jvm(scalaV))
  .settings(refPortSettings("sge-gltf") *)
  .settings(
    name := "balticporter-port-sge-gltf-ref",
    scalacOptions := sgeRelaxedFlags,
    libraryDependencies ++= Seq(
      "com.kubuszok"  %% "lls"   % "0.3.0",
      "org.scalameta" %% "munit" % "1.2.0" % Test,
      "junit"          % "junit"  % "4.12"  % Test,
    ),
  )

lazy val `port-sge-screens-ref` = (project in file(".ports/sge-screens-ref"))
  .dependsOn(`port-sge`.jvm(scalaV))
  .settings(refPortSettings("sge-screens") *)
  .settings(
    name := "balticporter-port-sge-screens-ref",
    scalacOptions := sgeRelaxedFlags,
    libraryDependencies ++= Seq(
      "com.kubuszok"  %% "lls"   % "0.3.0",
      "org.scalameta" %% "munit" % "1.2.0" % Test,
    ),
  )

lazy val `port-sge-vfx-ref` = (project in file(".ports/sge-vfx-ref"))
  .dependsOn(`port-sge`.jvm(scalaV))
  .settings(refPortSettings("sge-vfx") *)
  .settings(
    name := "balticporter-port-sge-vfx-ref",
    scalacOptions := sgeRelaxedFlags,
    libraryDependencies ++= Seq(
      "com.kubuszok"  %% "lls"   % "0.3.0",
      "org.scalameta" %% "munit" % "1.2.0" % Test,
    ),
  )

lazy val `port-sge-ai-ref` = (project in file(".ports/sge-ai-ref"))
  .dependsOn(`port-sge`.jvm(scalaV))
  .settings(refPortSettings("sge-ai") *)
  .settings(
    name := "balticporter-port-sge-ai-ref",
    scalacOptions := sgeRelaxedFlags,
    libraryDependencies ++= Seq(
      "com.kubuszok"  %% "lls"   % "0.3.0",
      "org.scalameta" %% "munit" % "1.2.0" % Test,
      "junit"          % "junit"  % "4.12"  % Test,
    ),
  )

lazy val `port-sge-textra-ref` = (project in file(".ports/sge-textra-ref"))
  .dependsOn(`port-sge`.jvm(scalaV))
  .settings(refPortSettings("sge-textra") *)
  .settings(
    name := "balticporter-port-sge-textra-ref",
    scalacOptions := sgeRelaxedFlags,
    libraryDependencies ++= Seq(
      "com.kubuszok"              %% "lls"       % "0.3.0",
      "com.github.tommyettinger"   % "regexodus" % "0.1.21",
      "org.scalameta"             %% "munit"     % "1.2.0" % Test,
    ),
  )

lazy val `port-sge-visui-ref` = (project in file(".ports/sge-visui-ref"))
  .dependsOn(`port-sge`.jvm(scalaV))
  .settings(refPortSettings("sge-visui") *)
  .settings(
    name := "balticporter-port-sge-visui-ref",
    scalacOptions := sgeRelaxedFlags,
    libraryDependencies ++= Seq(
      "com.kubuszok"  %% "lls"   % "0.3.0",
      "org.scalameta" %% "munit" % "1.2.0" % Test,
    ),
  )

lazy val `port-sge-visui-usl-ref` = (project in file(".ports/sge-visui-usl-ref"))
  .settings(refPortSettings("sge-visui-usl") *)
  .settings(
    name := "balticporter-port-sge-visui-usl-ref",
    scalacOptions := sgeRelaxedFlags,
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "1.2.0" % Test,
    ),
    Test / unmanagedResourceDirectories += (ThisBuild / baseDirectory).value / ".." / "sge" / "original-src" / "vis-ui" / "usl" / "src" / "test" / "resources",
  )

lazy val `port-ssg-liquid-ref` = (project in file(".ports/ssg-liquid-ref"))
  .settings(refPortSettings("ssg-liquid") *)
  .settings(
    name := "balticporter-port-ssg-liquid-ref",
    scalacOptions := ssgFlags,
    libraryDependencies ++= Seq(
      "org.antlr"                       % "antlr4-runtime"            % "4.13.0",
      "com.fasterxml.jackson.core"      % "jackson-core"              % "2.15.0",
      "com.fasterxml.jackson.core"      % "jackson-databind"          % "2.13.4.2",
      "com.fasterxml.jackson.core"      % "jackson-annotations"       % "2.15.0",
      "com.fasterxml.jackson.datatype"  % "jackson-datatype-jsr310"   % "2.15.0",
      "ua.co.k"                          % "strftime4j"                % "1.0.6",
      "com.kubuszok"                    %% "multiarch-serviceloader"   % "0.4.0-12-gc168b2f-SNAPSHOT",
      "org.scalameta"                   %% "munit"                     % "1.2.0" % Test,
      "junit"                            % "junit"                     % "4.13.1" % Test,
    ),
    resolvers += "Central Portal Snapshots" at "https://central.sonatype.com/repository/maven-snapshots",
    Compile / unmanagedClasspath ++= {
      val parserDir = (ThisBuild / baseDirectory).value / "out" / "liqp-parser-classes"
      if (parserDir.exists()) {
        val fc = fileConverter.value
        Seq(Attributed.blank(fc.toVirtualFile(parserDir.toPath)))
      } else Nil
    },
    Test / unmanagedClasspath ++= {
      val parserDir = (ThisBuild / baseDirectory).value / "out" / "liqp-parser-classes"
      if (parserDir.exists()) {
        val fc = fileConverter.value
        Seq(Attributed.blank(fc.toVirtualFile(parserDir.toPath)))
      } else Nil
    },
  )

lazy val `port-ssg-md-ref` = (project in file(".ports/ssg-md-ref"))
  .settings(refPortSettings("ssg-md") *)
  .settings(
    name := "balticporter-port-ssg-md-ref",
    scalacOptions := ssgFlags,
    libraryDependencies ++= Seq(
      "org.jetbrains"  % "annotations" % "24.0.1",
      "org.scalameta" %% "munit"       % "1.2.0" % Test,
      "junit"          % "junit"        % "4.13.2" % Test,
    ),
  )

lazy val `port-ssg-md-ext-ref` = (project in file(".ports/ssg-md-ext-ref"))
  .dependsOn(`port-ssg-md`.jvm(scalaV))
  .settings(refPortSettings("ssg-md-ext") *)
  .settings(
    name := "balticporter-port-ssg-md-ext-ref",
    scalacOptions := ssgFlags,
    libraryDependencies ++= Seq(
      "org.nibor.autolink"   % "autolink"            % "0.6.0",
      "com.vladsch.flexmark" % "flexmark-ext-emoji"  % "0.64.8" % Test,
      "org.scalameta"       %% "munit"               % "1.2.0"  % Test,
      "junit"                % "junit"                % "4.13.2" % Test,
    ),
  )

// ---------------------------------------------------------------------------------------------
// `ports` — an aggregate of EVERY ported module, NOT part of `root`. `sbt ports/compile`
// reaches them all; `sbt compile` stays the engine's. The aggregate is over projectRefs so
// that every platform row is included.
// ---------------------------------------------------------------------------------------------
lazy val ports = project
  .in(file(".ports"))
  .aggregate(
    `port-sge`.projectRefs *
  )
  .aggregate(
    `port-sge-ecs`.projectRefs *
  )
  .aggregate(
    `port-sge-graphs`.projectRefs *
  )
  .aggregate(
    `port-sge-anim8`.projectRefs *
  )
  .aggregate(
    `port-sge-noise`.projectRefs *
  )
  .aggregate(
    `port-sge-jbump`.projectRefs *
  )
  .aggregate(
    `port-sge-gltf`.projectRefs *
  )
  .aggregate(
    `port-sge-screens`.projectRefs *
  )
  .aggregate(
    `port-sge-vfx`.projectRefs *
  )
  .aggregate(
    `port-sge-ai`.projectRefs *
  )
  .aggregate(
    `port-sge-textra`.projectRefs *
  )
  .aggregate(
    `port-sge-visui`.projectRefs *
  )
  .aggregate(
    `port-sge-visui-usl`.projectRefs *
  )
  .aggregate(
    `port-ssg-liquid`.projectRefs *
  )
  .aggregate(
    `port-ssg-md`.projectRefs *
  )
  .aggregate(
    `port-ssg-md-ext`.projectRefs *
  )
  .aggregate(`port-sge-ai-diff`, `port-sge-textra-diff`, `port-sge-visui-diff`)
  .aggregate(
    `port-sge-ref`, `port-sge-ecs-ref`, `port-sge-graphs-ref`, `port-sge-anim8-ref`,
    `port-sge-noise-ref`, `port-sge-jbump-ref`, `port-sge-gltf-ref`, `port-sge-screens-ref`,
    `port-sge-vfx-ref`, `port-sge-ai-ref`, `port-sge-textra-ref`, `port-sge-visui-ref`,
    `port-sge-visui-usl-ref`, `port-ssg-liquid-ref`, `port-ssg-md-ref`, `port-ssg-md-ext-ref`,
  )
  .settings(
    name := "balticporter-ports",
    publish / skip := true,
  )

lazy val root = project
  .in(file("."))
  // every row of the runtime matrix, not just the JVM one — `runtime` is a `ProjectMatrix` and
  // has no single reference, so a build-wide `compile`/`publishLocal` reaches all three or none.
  .aggregate(runtime.projectRefs *)
  .aggregate(api, `frontend-spoon`, engine, testkit, corpus)
  .settings(
    name := "balticporter",
    publish / skip := true,
  )
