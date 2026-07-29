package balticporter.corpus

import balticporter.core.{FrontendConfig, Provenance, RuntimeMode}
import balticporter.runner.{Determinism, PortRun, SourceSet}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Port libGDX's own JUnit suite (`gdx/test`) through the same pipeline as `gdx/src`.
  *
  * This is the port's only BEHAVIOURAL gate. Everything `LibgdxCoreMigrate` measures is *compiles*,
  * and four silent correctness defects have already been found in this corpus that compiled green —
  * dropped `static { }` blocks, dropped `super(args)`, dropped anonymous-class bodies, and the
  * typer-only blind spot itself. `gdx/test` carries 221 `@Test` methods making ~900 assertions;
  * those assertions are the only evidence of behaviour this project can have.
  *
  * The test sources are CONVERTED; `gdx/src` is a RESOLUTION root only — it is ported separately by
  * [[LibgdxCoreMigrate]], and re-emitting it here would fork the output. That split is what
  * `FrontendConfig.resolutionRoots` is for, and deciding which units it makes this run's own is now
  * [[balticporter.runner.PortRun]]'s job rather than a hand-maintained FQN set here.
  *
  * The transform pipeline is not restated here at all: it arrives from `LibgdxPolicy.core`'s
  * manifest, which this module extends with `TestFrameworkTransform`. The version of this file that
  * listed the shared phases again — minus two, with a comment arguing that test code touches
  * neither the reflection wrapper nor the class table — was right, but nothing checked it and the
  * next module would have had to make the same argument from scratch. That is the drift
  * `CLAUDE.md` §1 warns about, and a `PortManifest` is what removes the opportunity for it.
  *
  * This program had never called `PortabilityCheck` — the check existed, the main port ran it, and
  * this one silently did not, because check invocation was copy-paste. It runs every check now
  * because `PortRun` runs every check; there is no longer a way to write a port that does not.
  */
object LibgdxTestMigrate:

  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val srcRoot  = repoRoot.resolve("../sge/original-src/libgdx/gdx/src").normalize
    val testRoot = repoRoot.resolve("../sge/original-src/libgdx/gdx/test").normalize

    val files = Files.walk(testRoot).iterator().asScala
      .filter(p => p.toString.endsWith(".java"))
      .map(p => testRoot.relativize(p).toString)
      .filterNot(f => f.endsWith("package-info.java") || f.endsWith("module-info.java"))
      .toList.sorted

    PortRun(
      label     = "libgdx-test",
      portRoot  = repoRoot.resolve("libgdx-core"),
      sourceSet = SourceSet.Test,
      frontend  = FrontendConfig(testRoot, files, Nil, resolutionRoots = List(srcRoot)),
      phases    = Nil, // supplied by the manifest — the two sources are mutually exclusive
      // A DEPENDENT of `LibgdxPolicy.core`: the shared surface arrives as a value, not as a copy.
      // It adds `TestFrameworkTransform` and inherits everything else, and `ManifestAgreement`
      // verifies on every run that the 605 types it resolves against but does not convert are
      // modelled exactly as the module that emits them models them.
      manifest  = Some(LibgdxPolicy.test(repoRoot)),
      provenance = Some(Provenance(
        upstreamName     = "libGDX",
        upstreamCommit   = "vendored in ../sge/original-src/libgdx",
        originalLicense  = "Apache-2.0",
        sourcePathPrefix = "gdx/test",
        sourceRoot       = testRoot.toString,
      )),
      // NOT `Vendored`: the MAIN source set of this same module already vendored the collection
      // shims, and the two sets are compiled together. Vendoring twice defines every support type
      // twice — which the JVM tolerates only while the copies agree and the Scala.js/Native
      // linkers reject outright.
      runtimeMode = RuntimeMode.Dependency,
      // NOTHING is injected alongside the converted suites, so there are no `supportSources`.
      // `TestFrameworkTransform` used to write a `balticporter.runtime.Asserts` façade re-declaring
      // JUnit's assertions in java's argument order; that was SHAPE ADAPTATION the transform can do
      // itself, and it has been deleted. The suites retype onto `munit.FunSuite` — a dependency the
      // generated project declares, not a source this run emits.
      //
      // The rule that decides this, and the only rule that admits anything to `balticporter-runtime`:
      // semantics the target LACKS become a dependency (the collection shims — scala has no
      // removal-capable iterator); shapes the engine can emit correctly are emitted, and nothing
      // ships.
      determinism = Determinism.fromArgs(args.toSeq),
      nextStep    = "bash scripts/gdx_test_measure.sh",
    ).execute()
