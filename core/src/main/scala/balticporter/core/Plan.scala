package balticporter.core

import java.nio.file.Path

/** Provenance stamped into every generated file's header. */
final case class Provenance(
    upstreamName: String,
    upstreamCommit: String,
    originalLicense: String,
    /** path prefix shown in headers before the unit's sourcePath, e.g. "liqp/src/main/java". */
    sourcePathPrefix: String,
)

/** M0 subset of the PortPlan from PLAN.md §3.1: one module, explicit file list. */
final case class UnitPlan(
    sourceRoot: Path,
    files: List[String],
    classpath: List[Path],
    outDir: Path,
    provenance: Provenance,
)

object EngineInfo:
  val version = "0.1.0-M0"
  /** Bumped whenever any pass/printer behavior changes; part of the header + cache key. */
  val fingerprint = s"balticporter/$version"
