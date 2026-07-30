package balticporter.corpus

import balticporter.core.{FrontendConfig, PortManifest, Provenance, RuntimeMode}
import balticporter.runner.{Determinism, PortRun, SourceSet, VendoredCommit}
import balticporter.transform.{CollectionsTransform, MutableParamsTransform}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Migrate **simple-graphs** (`src/main/java`, 29 types — a dependency-free graph library).
  *
  *   corpus-tests/runMain balticporter.corpus.SimpleGraphsMigrate [--determinism=full]
  *
  * ==Why simple-graphs is the third corpus library==
  * It is small, but it was not chosen for that. It is the first library to exercise four things
  * neither libGDX nor Ashley can:
  *
  *   1. **A package rename on a REAL library.** `PackageRenameTransform` is a Tier-1 item
  *      (`LIBRARY-READINESS.md` §1.5) — sge is `package sge`, ssg-liquid is `package ssg.liquid`,
  *      and neither repository can adopt output in the upstream namespace. It has been implemented
  *      and unit-tested since, but has never actually run over a library: libGDX and Ashley both
  *      keep their upstream names. The reference hand-port renames this one to `sge.graphs`, so
  *      the policy is real rather than invented for the exercise.
  *   2. **A standalone BASE port.** No resolution roots at all, where Ashley resolves against
  *      605 libGDX types. A different shape, and the one every first module of a library has.
  *   3. **`java.util.function` and `java.util.stream`** — `Predicate`, `Consumer`, `Collectors`.
  *      A whole API family the corpus has never translated, and one that flexmark and liqp both
  *      use heavily, so what it costs here is worth knowing before those.
  *   4. **Extending `java.util.AbstractCollection`.** `NodeCollection` and `VertexCollection`
  *      inherit from a JDK abstract class. That is CLAUDE.md §4.5 territory from the other
  *      direction — the rule there is about not modelling a Java interface ON a Scala collection
  *      trait; this is Java source that genuinely does inherit from a JDK collection, and what the
  *      engine should do about it is an open question this library will answer with a number.
  *
  * It also has a real behavioural gate: 7 test files, 17 `@Test`. See [[SimpleGraphsTestMigrate]].
  *
  * ==Licence — a discrepancy worth recording==
  * Upstream ships **MIT** (`LICENSE`: "MIT License, Copyright (c) 2020 earlygrey"). The reference
  * hand-port's file headers say "Licensed under the ISC License". One of the two is wrong, and
  * since a port is a derived work the upstream file is the authority — this port states MIT.
  */
object SimpleGraphsMigrate:

  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val base     = repoRoot.resolve("../sge/original-src/simple-graphs/src/main/java").normalize

    val files = Files.walk(base).iterator().asScala
      .filter(p => p.toString.endsWith(".java"))
      .map(p => base.relativize(p).toString)
      .filterNot(f => f.endsWith("package-info.java") || f.endsWith("module-info.java"))
      .toList.sorted

    PortRun(
      label     = "simple-graphs",
      portRoot  = repoRoot.resolve("simplegraphs-core"),
      sourceSet = SourceSet.Main,
      // A BASE port: nothing is merely resolved against, everything is converted.
      frontend  = FrontendConfig(base, files, Nil, Nil),
      phases    = Nil, // supplied by the manifest — the two sources are mutually exclusive
      manifest  = Some(SimpleGraphsPolicy.core),
      provenance = Some(Provenance(
        upstreamName     = "simple-graphs",
        upstreamCommit   = VendoredCommit.of(base),
        originalLicense  = "MIT",
        sourcePathPrefix = "src/main/java",
        sourceRoot       = base.toString,
      )),
      // A single source set compiled standalone by `scala-cli`: the support types the collections
      // phase retypes onto ship beside the emitted code.
      runtimeMode = RuntimeMode.Vendored,
      determinism = Determinism.fromArgs(args.toSeq),
      nextStep    = "scala-cli compile simplegraphs-core/src_managed/main/scala",
    ).execute()

/** simple-graphs' policy. A BASE manifest — it has no bases of its own.
  *
  * `packageRenames` is DATA rather than a phase (CLAUDE.md §4.56): the rename must run after every
  * other phase, because all of their policy is written in the upstream namespace, and `runsAfter`
  * cannot state "after everything". `PortRun` appends it last and verifies it with
  * `PackageRenameTransform.check` — run before, it counts what will move; run after with the same
  * map, every prefix must come back unmatched.
  */
object SimpleGraphsPolicy:

  def core: PortManifest = PortManifest(
    name    = "simple-graphs",
    governs = Set("space.earlygrey.simplegraphs"),
    // The reference hand-port's target namespace. This is the first time the engine renames a real
    // library, so treat a surprise here as information rather than noise.
    packageRenames = Map("space.earlygrey.simplegraphs" -> "sge.graphs"),
    surface = List(new CollectionsTransform, new MutableParamsTransform),
  )

  /** the upstream JUnit suite, as a dependent of [[core]]. */
  def test: PortManifest = core.extendedBy(PortManifest(
    name    = "simple-graphs-test",
    surface = List(new balticporter.transform.TestFrameworkTransform()),
  ))
