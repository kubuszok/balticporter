package balticporter.corpus.simplegraphs

import balticporter.runner.PortConfig

import java.nio.file.Path

/** Migrate **simple-graphs** (`src/main/java`, 29 types -- a dependency-free graph library).
  *
  *   corpus/runMain balticporter.corpus.simplegraphs.SimpleGraphsMigrate [--determinism=full]
  *
  * The whole port is `balticporter/corpus/ports/simplegraphs/main.conf` -- read that, not this
  * file. This `main` only names it and gives the run its report identity. Also the corpus's
  * acceptance proof for the config front door (DESIGN.md §5.7): `just sg-measure` requires every
  * check count and member digest to be byte-identical to the hand-written `PortRun(...)`.
  *
  * The third corpus library, exercising four things neither libGDX nor Ashley can: a package
  * rename on a REAL library (`PackageRenameTransform`, a Tier-1 adoption blocker, never actually
  * run over a library before this -- the reference hand port renames this one to `sge.graphs`); a
  * standalone BASE port with no resolution roots at all; `java.util.function`/`java.util.stream`
  * (a whole API family flexmark and liqp both use heavily); and extending
  * `java.util.AbstractCollection` (`NodeCollection`/`VertexCollection`, CLAUDE.md §4.5 from the
  * other direction).
  *
  * A real behavioural gate: 7 test files, 16 `@Test`. See [[SimpleGraphsTestMigrate]].
  */
object SimpleGraphsMigrate:

  def main(args: Array[String]): Unit =
    PortConfig.load(SimpleGraphsPort.conf("main.conf"), args.toSeq).execute()

/** Where this port's two configuration files live, for the two `main`s that name them. */
object SimpleGraphsPort:

  def repoRoot: Path =
    Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize

  def conf(name: String): Path = repoRoot.resolve("balticporter/corpus/ports/simplegraphs").resolve(name)
