package balticporter.sbt

import java.nio.file.Path

/** Runs a non-Java port via RAST export + dedicated emitters.
  *
  * Skeleton — the actual implementation will:
  *   1. Export RAST (if no pre-exported rastDir)
  *   2. Load the library's dedicated emitter
  *   3. Run ParityDerive against the reference
  *   4. Write emitted Scala + bodies.tsv to outputDir
  */
object NonJavaPortTask:

  case class Config(
    library: String,
    language: String,
    upstream: Path,
    reference: Option[Path],
    rastDir: Option[Path],
    outputDir: Path,
  )

  def run(config: Config, log: String => Unit): Seq[Path] =
    log(s"[sbt-balticporter] Non-Java port: ${config.library} (${config.language})")
    log(s"[sbt-balticporter] Upstream: ${config.upstream}")
    config.reference.foreach(r => log(s"[sbt-balticporter] Reference: $r"))
    config.rastDir.foreach(r => log(s"[sbt-balticporter] RAST: $r"))
    log(s"[sbt-balticporter] Output: ${config.outputDir}")
    // Skeleton — will call RAST export + ParityDerive + dedicated emitters in Phase 2
    Seq.empty
