ThisBuild / organization     := "com.kubuszok"
ThisBuild / organizationName := "Baltic Porter"
ThisBuild / scalaVersion     := "3.8.4"

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
//   * The layout is plain `src/main/scala` rather than a `crossProject`'s `shared/src`. Adding
//     sbt-scalajs / sbt-scala-native and turning this into a `crossProject` is a directory move
//     plus two plugin lines — no source change — and is deliberately deferred until a port
//     actually builds for those platforms.
//   * Version-locked to the engine (`ThisBuild / version`); see the version-scheme note above.
// ---------------------------------------------------------------------------------------------
lazy val runtime = project
  .in(file("balticporter/runtime"))
  .settings(
    name        := "balticporter-runtime",
    description := "Support types that Baltic-Porter-emitted Scala links against.",
    libraryDependencies += munit,
  )

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
           |  val runtimeArtifact: String = "${(runtime / name).value}"
           |""".stripMargin,
      )
      Seq(f)
    }.taskValue,
    // VENDORING: the runtime module's real sources, copied verbatim into the engine's resources so
    // `RuntimeArtifact.sourceOf` can write them next to a zero-dependency port. A COPY, never a
    // second text — a divergence between the published trait and the vendored string is exactly
    // the bug the published artifact exists to prevent, one level down.
    Compile / resourceGenerators += Def.task {
      val srcDir = (runtime / Compile / scalaSource).value
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
      IO.write(f, (runtime / Compile / scalaSource).value.getAbsolutePath)
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
        (runtime / Compile / scalaSource).value,
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

// A migration TARGET, not part of the tool: the TIR-emitted Scala of libGDX's core module
// (`gdx/src`, 605 types), produced by `corpus/runMain …LibgdxCoreMigrate`. Standalone
// (JDK-only, like libGDX core itself) and NOT aggregated by root, so a work-in-progress port
// can't break the main build. Lenient scalacOptions: this is generated code under burn-down.
//
// The project — its directory, its sbt id and the port's own `label` — is named for the module
// of the REFERENCE PORT it targets (`../sge/build.sbt`'s `sge`), not for the upstream library:
// what this repository produces is sge's core module, and the upstream name says nothing about
// where the output belongs. Every ported library under `ported/` follows that rule. The migrator
// CLASS name and the `port-report/` directory keyed on it deliberately do NOT — that is the
// measurement identity, and it is stable across a rename.
//
// Its sources live in `src_managed/`, gitignored and removed by `clean` — the same layout
// `SbtGen` writes for a real port (see `SbtGen.managedDir` for why, and `SbtGen.managedSources`
// for the settings this mirrors). Only `src/` would ever be committed, and this port has none.
lazy val sge = project
  .in(file("ported/sge"))
  .settings(
    name := "balticporter-sge",
    scalacOptions := Seq("-nowarn"),
    publish / skip := true,
    libraryDependencies += "org.scalameta" %% "munit" % "1.2.0" % Test,
    Compile / sourceGenerators += Def.task {
      ((baseDirectory.value / "src_managed" / "main" / "scala") ** "*.scala").get()
    }.taskValue,
    Test / sourceGenerators += Def.task {
      ((baseDirectory.value / "src_managed" / "test" / "scala") ** "*.scala").get()
    }.taskValue,
    cleanFiles += baseDirectory.value / "src_managed",
  )

lazy val root = project
  .in(file("."))
  .aggregate(runtime, api, `frontend-spoon`, engine, testkit, corpus)
  .settings(
    name := "balticporter",
    publish / skip := true,
  )
