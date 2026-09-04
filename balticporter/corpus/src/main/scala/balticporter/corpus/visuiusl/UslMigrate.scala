package balticporter.corpus.visuiusl

import balticporter.runner.PortConfig

import java.nio.file.Path

/** Migrate **USL** -- VisUI's skin-definition language compiler (`usl/src/main/java`, 18 files /
  * 1,604 LOC: a lexer, a recursive parser, a style merger and a JSON writer).
  *
  *   corpus/runMain balticporter.corpus.visuiusl.UslMigrate [--determinism=full]
  *
  * The whole port is `balticporter/corpus/ports/visui-usl/main.conf` -- read that, not this file.
  * This `main` only names it and gives the run its report identity.
  *
  * A STANDALONE port, not a scope edit to `sge-visui` (which ports the sibling `ui/` module):
  * upstream publishes the two as independent maven coordinates at independent versions, and
  * `com.kotcrab.vis.ui`/`com.kotcrab.vis.usl` are siblings rather than one package root.
  *
  * The corpus's first chance to EXCEED a reference port: the reference hand port never ported USL
  * at all (CLAUDE.md §3.5 -- a skip is not a model), so this is a whole capability sge does not
  * have. Forces the engine to get right: a hand-written CHARACTER SCANNER (`Lexer`/`Parser`,
  * driven by a `char` index -- 28 post-increment sites, all in STATEMENT position and none read as
  * a VALUE, MEASURED not assumed, `PROGRESS.md` §10.9.13.2); and a ZERO-AUTHORING ORACLE (upstream
  * ships both `.usl` fixtures and their expected `.json` output, see `test.conf`).
  */
object UslMigrate:

  def main(args: Array[String]): Unit =
    PortConfig.load(UslPort.conf("main.conf"), args.toSeq).execute()

/** Where this port's configuration lives, for the `main` that names it. */
object UslPort:

  def repoRoot: Path =
    Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize

  def conf(name: String): Path = repoRoot.resolve("balticporter/corpus/ports/visui-usl").resolve(name)
