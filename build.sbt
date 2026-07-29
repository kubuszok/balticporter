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
// `publish / skip := true` individually (`corpus-tests`, `libgdx-core`, `root`).
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
  .in(file("runtime"))
  .settings(
    name        := "balticporter-runtime",
    description := "Support types that Baltic-Porter-emitted Scala links against.",
    libraryDependencies += munit,
  )

lazy val core = project
  .in(file("core"))
  .settings(
    name := "balticporter-core",
    libraryDependencies += munit,
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
    // VENDORING: the runtime module's real sources, copied verbatim into core's resources so
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
  )

lazy val `frontend-spoon` = project
  .in(file("frontend-spoon"))
  .dependsOn(core)
  .settings(
    name := "balticporter-frontend-spoon",
    libraryDependencies ++= Seq(
      "fr.inria.gforge.spoon" % "spoon-core" % "11.5.0",
      munit,
    ),
  )

lazy val `scala-emit` = project
  .in(file("scala-emit"))
  .dependsOn(core)
  .settings(
    name := "balticporter-scala-emit",
    libraryDependencies += munit,
  )

lazy val vocab = project
  .in(file("vocab"))
  .dependsOn(core)
  .settings(
    name := "balticporter-vocab",
    libraryDependencies += munit,
  )

lazy val `sbt-gen` = project
  .in(file("sbt-gen"))
  .dependsOn(core)
  .settings(
    name := "balticporter-sbt-gen",
    libraryDependencies += munit,
  )

lazy val verify = project
  .in(file("verify"))
  .dependsOn(core)
  .settings(
    name := "balticporter-verify",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "scalameta" % "4.17.2",
      munit,
    ),
  )

// Helpers a CONSUMER writing tests against the engine needs: run Java source through phases and
// assert on the emitted Scala. Filled from what `corpus-tests/src/test` repeats verbatim in every
// spec (see `PortFixture`). `munit` is a COMPILE dependency here — a testkit whose users write
// MUnit suites has to hand them the framework.
lazy val testkit = project
  .in(file("testkit"))
  .dependsOn(core, `frontend-spoon`, `scala-emit`)
  .settings(
    name := "balticporter-testkit",
    libraryDependencies += "org.scalameta" %% "munit" % "1.2.0",
  )

lazy val runner = project
  .in(file("runner"))
  .dependsOn(core, `frontend-spoon`, `scala-emit`)
  .settings(
    name := "balticporter-runner",
    libraryDependencies += munit,
  )

lazy val `corpus-tests` = project
  .in(file("corpus-tests"))
  .dependsOn(runner, testkit, verify, `sbt-gen`, vocab)
  .settings(
    name := "balticporter-corpus-tests",
    libraryDependencies += munit,
    publish / skip := true,
    Compile / run / fork := true,
    Compile / run / javaOptions += s"-Dbalticporter.root=${(ThisBuild / baseDirectory).value}",
  )

// A migration TARGET, not part of the tool: the TIR-emitted Scala of libGDX's core module
// (`gdx/src`, 605 types), produced by `corpus-tests/runMain …LibgdxCoreMigrate`. Standalone
// (JDK-only, like libGDX core itself) and NOT aggregated by root, so a work-in-progress port
// can't break the main build. Lenient scalacOptions: this is generated code under burn-down.
//
// Its sources live in `src_managed/`, gitignored and removed by `clean` — the same layout
// `SbtGen` writes for a real port (see `SbtGen.managedDir` for why, and `SbtGen.managedSources`
// for the settings this mirrors). Only `src/` would ever be committed, and this port has none.
lazy val `libgdx-core` = project
  .in(file("libgdx-core"))
  .settings(
    name := "balticporter-libgdx-core",
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
  .aggregate(runtime, core, `frontend-spoon`, `scala-emit`, vocab, `sbt-gen`, verify, testkit, runner, `corpus-tests`)
  .settings(
    name := "balticporter",
    publish / skip := true,
  )
