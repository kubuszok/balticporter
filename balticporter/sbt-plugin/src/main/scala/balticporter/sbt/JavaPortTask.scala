package balticporter.sbt

import java.nio.file.Path

/** Runs a Java→Scala port via the Baltic Porter engine.
  *
  * Skeleton — the actual implementation will call `PortConfig.load(conf).execute()`
  * from the published `balticporter-corpus` artifact.
  */
object JavaPortTask:

  case class Config(
    conf: Path,
    upstream: Path,
    outputDir: Path,
  )

  def run(config: Config, log: String => Unit): Seq[Path] =
    log(s"[sbt-balticporter] Java port from ${config.conf}")
    log(s"[sbt-balticporter] Upstream: ${config.upstream}")
    log(s"[sbt-balticporter] Output: ${config.outputDir}")
    // Skeleton — will call PortConfig.load + PortRun in Phase 2
    Seq.empty
