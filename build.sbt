import kubuszok.sbt.KubuszokPlugin.autoImport._

ThisBuild / organization     := "com.kubuszok"
ThisBuild / organizationName := "Baltic Porter"
// The one Scala version this build compiles with, named because the `runtime` project matrix has
// to state it per platform row as well (`ThisBuild / scalaVersion` is not visible to the matrix's
// axis constructors, which run while the build is being assembled).
val scalaV = "3.8.4"

ThisBuild / scalaVersion     := scalaV

// ---------------------------------------------------------------------------------------------
// VERSION — sbt-kubuszok's git-describe derivation, same as every kubuszok org repo.
//
// Tagged commit → release (e.g. `v0.1.0` → `0.1.0`); untagged → snapshot with commit hash
// (e.g. `0.1.0-3-gabcdef1-SNAPSHOT`). The version is baked into generated code
// (`balticporter.core.BuildVersion` → `EngineInfo.version`) and into every emitted port's header
// and its `balticporter-runtime` dependency.
//
// Pre-1.0 policy (early-semver): the MINOR is the compatibility unit. A change to any emitted
// construct, to the TIR, or to `balticporter.runtime`'s SHAPE bumps the minor; a fix that leaves
// all four checks and the emitted bytes alone bumps the patch. `balticporter-runtime` shares the
// version with the engine on purpose — see `RuntimeArtifact` for why divergence there is a
// correctness bug and not a packaging preference.
// ---------------------------------------------------------------------------------------------

ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Wunused:all",
)

// ---------------------------------------------------------------------------------------------
// PUBLISHING — sbt-kubuszok provides publishTo (Maven Central Snapshots for SNAPSHOT, local
// staging for release), sbt-pgp for signing, ci-release command, and projectType-based gating.
// Modules that must NOT ship set projectType := NonPublished.
// Skip doc generation for snapshots — DottydocRunner nondeterministically NPEs on JDK 24/25
// (ISS-799, scala/scala3#24183, C2 JIT miscompilation of SignatureBuilder). Snapshot consumers
// never read the docs; release builds generate them (the blocking `docs` CI job keeps them green).
ThisBuild / packageDoc / publishArtifact := !isSnapshot.value
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
// Side effect, welcome: a forked test JVM is fresh, so classloader-layer staleness cannot
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
    projectType := ProjectType.ScalaLibrary,
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
// runner to compile, it has stopped being the surface a rule author codes against.
// ---------------------------------------------------------------------------------------------
lazy val api = project
  .in(file("balticporter/api"))
  .settings(
    name := "balticporter-api",
    description := "The Baltic Porter model and contracts a transform, check or frontend is written against.",
    projectType := ProjectType.JarOnly,
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
// true: no Spoon type is visible here.
// ---------------------------------------------------------------------------------------------
lazy val engine = project
  .in(file("balticporter/engine"))
  .dependsOn(api, `frontend-spoon`)
  .settings(
    name := "balticporter-engine",
    projectType := ProjectType.JarOnly,
    libraryDependencies ++= Seq(
      "org.scalameta" %% "scalameta" % "4.17.2", // `verify` — skeleton diff over emitted Scala
      // The CONFIG front door (`PortConfig`, `PortConfigMain`). Deliberately here and not in `api`:
      // the SPI a rule author implements takes `balticporter.tir.ConfigView`, so `api` keeps the
      // property of depending on nothing. No derivation library beside
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
    projectType := ProjectType.JarOnly,
    libraryDependencies ++= Seq(
      "fr.inria.gforge.spoon" % "spoon-core" % "11.5.0",
      munit,
    ),
  )

// TypeScript frontend — reads RAST v1 JSON (produced by the Node.js exporter) and builds a TIR
// Program. No dependency on Spoon or on Node.js at compile time; the exporter is invoked as a
// subprocess or pointed at pre-exported JSON.
lazy val `frontend-ts` = project
  .in(file("balticporter/frontend-ts"))
  .dependsOn(api)
  .settings(
    name := "balticporter-frontend-ts",
    projectType := ProjectType.JarOnly,
    libraryDependencies ++= Seq(
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core"   % "2.36.4",
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.36.4" % Provided,
      "org.scalameta" %% "scalameta" % "4.17.2", // reference Scala read structurally: declaring owners, member lines
      munit,
    ),
    // The TS exporter bundle: `npm ci && npm run build` in exporter/, then copy dist/export.js
    // and a manifest into the jar so a consumer can extract it at build time without a sibling
    // engine checkout.
    Compile / resourceGenerators += Def.task {
      val exporterDir = (ThisProject / baseDirectory).value / "exporter"
      val outDir      = (Compile / resourceManaged).value / "balticporter" / "frontend" / "ts" / "exporter"
      val marker      = outDir / ".built-marker"
      val pkgJson     = exporterDir / "package.json"
      // keyed on the CONTENTS of package.json, the lock file and every source, never on paths
      val inputs      = (pkgJson +: (exporterDir / "package-lock.json") +: (exporterDir / "src").listFiles.toList.sortBy(_.getName))
      val expected    = inputs.filter(_.isFile).map(f => s"${f.getName}=${IO.read(f).hashCode}").mkString(";")
      val cached      = marker.exists && IO.read(marker).trim == expected
      if (!cached) {
        val log = streams.value.log
        log.info("[frontend-ts] Building TS exporter bundle (npm ci && npm run build)")
        val npmCi = scala.sys.process.Process(Seq("npm", "ci"), exporterDir).!
        if (npmCi != 0) sys.error("[frontend-ts] npm ci failed")
        val npmBuild = scala.sys.process.Process(Seq("npm", "run", "build"), exporterDir).!
        if (npmBuild != 0) sys.error("[frontend-ts] npm run build failed")
        IO.createDirectory(outDir)
        IO.copyFile(exporterDir / "dist" / "export.js", outDir / "export.js")
        // Manifest: the exporter version and the typescript version it depends on, read with regex
        val pkgContent = IO.read(pkgJson)
        val exporterVersion = """"version"\s*:\s*"([^"]+)"""".r.findFirstMatchIn(pkgContent).map(_.group(1)).getOrElse("unknown")
        val tsVersion = """"typescript"\s*:\s*"([^"]+)"""".r.findFirstMatchIn(pkgContent).map(_.group(1)).getOrElse("unknown")
        IO.write(outDir / "manifest.properties", s"exporter.version=$exporterVersion\ntypescript.version=$tsVersion\n")
        IO.write(marker, expected)
        log.info(s"[frontend-ts] Exporter bundle built: export.js + manifest (typescript $tsVersion)")
      }
      Seq(outDir / "export.js", outDir / "manifest.properties")
    }.taskValue,
  )

// Dart frontend — stub awaiting Dart SDK. The exporter uses package:analyzer for resolved ASTs.
// Phase 3 of the non-Java frontends plan. Primary target: dart-sass (ssg-sass).
lazy val `frontend-dart` = project
  .in(file("balticporter/frontend-dart"))
  .dependsOn(api, `frontend-ts`)
  .settings(
    name := "balticporter-frontend-dart",
    projectType := ProjectType.JarOnly,
    libraryDependencies ++= Seq(
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core"   % "2.36.4",
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.36.4" % Provided,
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
    projectType := ProjectType.JarOnly,
    libraryDependencies += "org.scalameta" %% "munit" % "1.2.0",
  )

// Library-free regression-fixture module. Not published: the consumers pin balticporter-engine.
lazy val corpus = project
  .in(file("balticporter/corpus"))
  .dependsOn(api, engine, testkit, `frontend-spoon`)
  .settings(
    name := "balticporter-corpus",
    publish / skip := true,
    libraryDependencies += munit,
    Compile / run / fork := true,
    Compile / run / javaOptions += s"-Dbalticporter.root=${(ThisBuild / baseDirectory).value}",
    Test / javaOptions += s"-Dbalticporter.root=${(ThisBuild / baseDirectory).value}",
  )


lazy val root = project
  .in(file("."))
  .aggregate(runtime.projectRefs *)
  .aggregate(api, `frontend-spoon`, engine, testkit, corpus, `frontend-ts`, `frontend-dart`)
  .settings(
    name := "balticporter",
    publish / skip := true,
  )
