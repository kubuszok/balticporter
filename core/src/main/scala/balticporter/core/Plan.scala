package balticporter.core

import java.nio.file.Path

/** Provenance stamped into every generated file's header.
  *
  * Attribution is a LICENCE obligation, not decoration: every port in this project's reach is a
  * derived work of a licensed original (Apache-2.0 for the libraries ported so far), and a derived
  * work ships its notice. A generated file with no header is a compliance gap that a green build
  * cannot report.
  */
final case class Provenance(
    upstreamName: String,
    upstreamCommit: String,
    originalLicense: String,
    /** path prefix shown in headers before the unit's sourcePath, e.g. "liqp/src/main/java". */
    sourcePathPrefix: String,
    /** ABSOLUTE root the Java sources were parsed from — normally `UnitPlan.sourceRoot` /
      * `FrontendConfig.sourceRoot`.
      *
      * The BIR frontend carried a repo-relative `sourcePath` per unit; the TIR carries an
      * `Origin` holding whatever absolute path the parser saw, which is a MACHINE-LOCAL string.
      * Emitting it raw would make generated output differ between checkouts, and CLAUDE.md §5's
      * measurement discipline depends on a diff meaning something. Given this root the emitter
      * relativises each unit's origin and the header is reproducible; left empty it falls back to
      * locating `sourcePathPrefix` inside the path, and failing that says so rather than
      * inventing one.
      */
    sourceRoot: String = "",
)

/** M0 subset of the PortPlan from PLAN.md §3.1: one module, explicit file list. */
final case class UnitPlan(
    sourceRoot: Path,
    files: List[String],
    classpath: List[Path],
    outDir: Path,
    provenance: Provenance,
    /** source roots participating in resolution but not converted (see FrontendConfig). */
    resolutionRoots: List[Path] = Nil,
)

object EngineInfo:
  val version = "0.1.0-M0"
  /** Bumped whenever any pass/printer behavior changes; part of the header + cache key. */
  val fingerprint = s"balticporter/$version"
