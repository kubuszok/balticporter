package balticporter.corpus.simplegraphs

import balticporter.runner.PortConfig

import java.nio.file.Path

/** Migrate **simple-graphs** (`src/main/java`, 29 types — a dependency-free graph library).
  *
  *   corpus/runMain balticporter.corpus.simplegraphs.SimpleGraphsMigrate [--determinism=full]
  *
  * ==This program is ONE LINE, and that is the point==
  * The whole port is `corpus/ports/simplegraphs/main.conf` — read that, not this file. What remains
  * here is a `main` whose only job is to name the configuration and give the run its report
  * identity: `CheckReport.dir` is derived from the main class's simple name, so a per-port `main`
  * is what keeps `port-report/SimpleGraphsMigrate` a stable measurement baseline.
  * `balticporter.runner.PortConfigMain` runs any conf without one.
  *
  * The conversion is the corpus's acceptance proof for the config front door (DESIGN.md §5.7):
  * `just sg-measure` measures the conf-driven port and requires every check count and every member
  * digest to be byte-identical to what the hand-written `PortRun(...)` produced.
  *
  * ==Why simple-graphs is the third corpus library==
  * It is small, but it was not chosen for that. It is the first library to exercise four things
  * neither libGDX nor Ashley can:
  *
  *   1. **A package rename on a REAL library.** `PackageRenameTransform` is a Tier-1 adoption
  *      blocker — sge is `package sge`, ssg-liquid is `package ssg.liquid`,
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
  * It also has a real behavioural gate: 7 test files, 16 `@Test` (a 17th, `GraphBuilderTest.testExample`, is commented out upstream). See [[SimpleGraphsTestMigrate]].
  */
object SimpleGraphsMigrate:

  def main(args: Array[String]): Unit =
    PortConfig.load(SimpleGraphsPort.conf("main.conf"), args.toSeq).execute()

/** Where this port's two configuration files live, for the two `main`s that name them. */
object SimpleGraphsPort:

  def repoRoot: Path =
    Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize

  def conf(name: String): Path = repoRoot.resolve("corpus/ports/simplegraphs").resolve(name)
