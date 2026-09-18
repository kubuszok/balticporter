package balticporter.corpus

import java.nio.file.Path

/** The corpus's support files — every `*-overrides*` directory, `ports/` and `shims/` — as the published jar carries them. A consumer that resolved the artifact calls [[root]] where a checkout would
  * have passed its repository root: the result has the same `balticporter/corpus/<dir>` layout the policies resolve against, and the classpath caches (`out/`) land beside it.
  */
object BundledCorpus:

  val Prefix = "balticporter-bundled/corpus"

  def root(target: Path): Path =
    balticporter.runner.BundledTree.extract(getClass.getClassLoader, Prefix, target.resolve("balticporter/corpus"))
    target.toAbsolutePath.normalize
