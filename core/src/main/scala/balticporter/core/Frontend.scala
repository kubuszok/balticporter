package balticporter.core

import java.nio.file.Path

/** A construct the current engine version cannot translate faithfully. Always fatal:
  * there is no best-effort emission (anti-omission stance, PLAN.md §3.3).
  */
final case class Unsupported(sourcePath: String, position: String, what: String)
    extends RuntimeException(s"$sourcePath:$position — unsupported construct: $what")

final case class FrontendConfig(
    /** root of the upstream Java sources (package dirs below it). */
    sourceRoot: Path,
    /** files to CONVERT, relative to sourceRoot. Order defines unit order. */
    files: List[String],
    /** dependency classpath for full resolution. */
    classpath: List[Path],
    /** additional source roots that participate in RESOLUTION but are not converted
      * (typically the whole vendored tree — source-over-jar avoids version skew). */
    resolutionRoots: List[Path] = Nil,
)

trait Frontend:
  /** Parse + resolve, returning units in the order of `cfg.files`. */
  def parse(cfg: FrontendConfig): List[BUnit]
