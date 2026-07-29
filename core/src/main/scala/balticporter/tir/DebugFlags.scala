package balticporter.tir

import java.nio.file.{Files, Path}

/** Run-time switches for DIAGNOSIS — the kill switch of CLAUDE.md §4.6, promoted from folklore
  * (edit a function to return early, gate it on a marker file, recompile) to a flag.
  *
  * ## Why there are two sources, and why a shell environment variable is not one of them
  *
  * The canonical way this engine is driven is `sbt -client "corpus-tests/runMain …"`. `-client`
  * talks to a LONG-RUNNING sbt server that was started with whatever environment the shell had at
  * the time, and the migration then runs in a JVM forked from THAT server. So:
  *
  *   - an environment variable exported in the operator's shell never reaches the migration —
  *     this is the trap CLAUDE.md §4.6 records;
  *   - a `-D` on the operator's command line does not either, because the forked JVM's options
  *     come from the build definition, not the caller.
  *
  * Therefore flags resolve from, in increasing precedence:
  *
  *   1. `<root>/.balticporter/run.properties`   — written by a SCRIPT before it invokes sbt
  *   2. `<root>/.balticporter/debug.properties` — hand-written by the operator/agent, wins over (1)
  *   3. system properties                       — for a direct `java`/test invocation, and for a
  *                                                main class that sets them before it runs a pipeline
  *
  * `<root>` is `-Dbalticporter.root` when the build supplies it (it does for the corpus programs),
  * else the working directory. `.balticporter/` is already gitignored, so a marker file is never a
  * commit hazard. Both files are optional; with neither present and no system property set, every
  * flag below is empty and every consumer is a no-op.
  *
  * ## The flags
  *
  * | key | effect |
  * |---|---|
  * | `balticporter.skipPhases=a,b` (or `*`) | [[Pipeline.run]] does not run those phases |
  * | `balticporter.dumpTirBefore=a`         | print the TIR of the program BEFORE phase `a` |
  * | `balticporter.dumpTirAfter=a`          | print the TIR of the program AFTER phase `a` |
  * | `balticporter.dumpOnly=com.x.Y`        | narrow both dumps to one unit's full name |
  * | `balticporter.tracePhases=true`        | one line per phase: name, units, symbols |
  * | `balticporter.traceNode=Typed,Apply`   | [[TirTrace]] prints construction provenance |
  * | `balticporter.report=off`              | disable check persistence ([[CheckReport]]) |
  * | `balticporter.reportDir=<path>`        | where check results go (default derived, see there) |
  * | `balticporter.reportPathRoot=<path>`   | source root to make finding paths RELATIVE to |
  *
  * The marker FILES are read once and cached — a flag must not change under a run, or two halves
  * of one measurement disagree (CLAUDE.md §5). The accessors themselves are `def`s over
  * `System.getProperty`, which is what lets a main class set a flag for the pipeline it is about
  * to run, and lets a test exercise one without a fresh JVM.
  */
object DebugFlags:

  val Prefix = "balticporter."

  /** repository/working root — the anchor for marker files and for relative flag paths. */
  def root: Path =
    Path.of(sys.props.getOrElse(Prefix + "root", ".")).toAbsolutePath.normalize

  /** the marker files consulted, in increasing precedence. Exposed so a diagnostic can print
    * WHERE a flag came from — an agent in another repository cannot guess. */
  def markerFiles: List[Path] =
    List(root.resolve(".balticporter/run.properties"), root.resolve(".balticporter/debug.properties"))

  private lazy val fileProps: Map[String, String] =
    markerFiles.foldLeft(Map.empty[String, String]) { (acc, f) =>
      if !Files.isRegularFile(f) then acc
      else
        val p = new java.util.Properties()
        val in = Files.newInputStream(f)
        try p.load(in)
        finally in.close()
        acc ++ p.stringPropertyNames().toArray(Array.empty[String]).map(k => k -> p.getProperty(k)).toMap
    }

  /** raw lookup: system property wins, then marker files (debug over run). */
  def get(key: String): Option[String] =
    val full = if key.startsWith(Prefix) then key else Prefix + key
    Option(System.getProperty(full)).orElse(fileProps.get(full)).map(_.trim).filter(_.nonEmpty)

  /** a comma-separated flag as a set. Empty when unset — so "off" needs no code path. */
  def list(key: String): Set[String] =
    get(key).map(_.split(',').iterator.map(_.trim).filter(_.nonEmpty).toSet).getOrElse(Set.empty)

  def bool(key: String, default: Boolean = false): Boolean =
    get(key).map(v => v == "true" || v == "yes" || v == "on" || v == "1").getOrElse(default)

  def path(key: String): Option[Path] =
    get(key).map(v => root.resolve(v).normalize)

  // ---- pipeline diagnosis ----
  def skipPhases: Set[String]    = list("skipPhases")
  def dumpTirBefore: Set[String] = list("dumpTirBefore")
  def dumpTirAfter: Set[String]  = list("dumpTirAfter")
  def dumpOnly: Option[String]   = get("dumpOnly")
  def tracePhases: Boolean       = bool("tracePhases")
  def traceNode: Set[String]     = list("traceNode")

  /** `*` skips everything — the whole-pipeline kill switch, one run, no source edit. */
  def skips(phaseName: String): Boolean = skipPhases.contains("*") || skipPhases.contains(phaseName)

  /** a one-line statement of what is switched on, for the run's own output. Silence when nothing
    * is set; a diagnostic that prints on a clean run trains the operator to ignore it. */
  def banner: Option[String] =
    val on = List(
      Option.when(skipPhases.nonEmpty)(s"skipPhases=${skipPhases.toList.sorted.mkString(",")}"),
      Option.when(dumpTirBefore.nonEmpty)(s"dumpTirBefore=${dumpTirBefore.toList.sorted.mkString(",")}"),
      Option.when(dumpTirAfter.nonEmpty)(s"dumpTirAfter=${dumpTirAfter.toList.sorted.mkString(",")}"),
      dumpOnly.map(f => s"dumpOnly=$f"),
      Option.when(traceNode.nonEmpty)(s"traceNode=${traceNode.toList.sorted.mkString(",")}"),
    ).flatten
    Option.when(on.nonEmpty)(s"[balticporter] DEBUG FLAGS: ${on.mkString(" ")}")
