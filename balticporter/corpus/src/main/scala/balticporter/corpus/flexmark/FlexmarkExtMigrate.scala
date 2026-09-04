package balticporter.corpus.flexmark

import balticporter.runner.PortConfig

/** Migrate flexmark's EXTENSION half — milestone 2, as ONE dependent port of [[FlexmarkMigrate]].
  *
  *   corpus/runMain balticporter.corpus.flexmark.FlexmarkExtMigrate [--determinism=full]
  *
  * The port is `balticporter/corpus/ports/ssg-md/ext.conf` -- read that, not this file. This is the
  * `main` that names it and gives the run its own report identity (`CLAUDE.md` §2.1), stable across
  * the batch waves that admit the 29 extension modules (each a line in `includeGlobs`). ONE
  * dependent port, not twenty-nine: `ext.conf`'s D-mde-1 argues it; mechanically, an emission
  * identity is the pair (`portRoot`, `sourceSet`) and the reference hand port puts every extension
  * package in the ONE `ssg-md` module. Uses [[FlexmarkClasspath]] unchanged (same
  * `org.jetbrains:annotations` compile-scope dependency as the base).
  */
object FlexmarkExtMigrate:

  def main(args: Array[String]): Unit =
    FlexmarkClasspath.ensure(FlexmarkPort.repoRoot)
    PortConfig.load(FlexmarkPort.conf("ext.conf"), args.toSeq).execute()
