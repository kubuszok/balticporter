package balticporter.corpus.flexmark

import balticporter.runner.PortConfig

/** Migrate flexmark's EXTENSION half — milestone 2, as ONE dependent port of [[FlexmarkMigrate]].
  * The port is `.../ports/ssg-md/ext.conf`, stable across the batch waves that admit the 29
  * extension modules (each a line in `includeGlobs`). ONE dependent port, not twenty-nine: an
  * emission identity is the pair (`portRoot`, `sourceSet`) and the reference hand port puts
  * every extension package in the ONE `ssg-md` module. Uses [[FlexmarkClasspath]] unchanged. */
object FlexmarkExtMigrate:

  def main(args: Array[String]): Unit =
    FlexmarkClasspath.ensure(FlexmarkPort.repoRoot)
    PortConfig.load(FlexmarkPort.conf("ext.conf"), args.toSeq).execute()
