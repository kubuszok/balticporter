package balticporter.tir

import java.nio.file.{Files, Path}

/** Run-time switches for DIAGNOSIS — the kill switch of CLAUDE.md §4.6, promoted from folklore
  * (edit a function to return early, gate it on a marker file, recompile) to a flag.
  *
  * ## Why there are two sources, and why a shell environment variable is not one of them
  *
  * The canonical way this engine is driven is `sbt -client "corpus/runMain …"`. `-client`
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
  * | `balticporter.baseReports=<p1:p2>`     | EXTRA directories to look for a base's published port map in |
  *
  * The marker FILES are read once and cached — a flag must not change under a run, or two halves
  * of one measurement disagree (CLAUDE.md §5). The cache is keyed on [[root]], which is the one
  * thing that cannot change under a run, so pointing the root at another directory (a test, a
  * diagnostic asked about another checkout) re-reads rather than answering from another root's
  * files. The accessors themselves are `def`s over `System.getProperty`, which is what lets a main
  * class set a flag for the pipeline it is about to run, and lets a test exercise one without a
  * fresh JVM.
  *
  * ## Why the resolution is also a VALUE ([[resolution]])
  *
  * "Why did my flag not reach the run" is the question this whole mechanism generates, and it is
  * unanswerable from the flags themselves: [[get]] returns the winner and says nothing about which
  * layer supplied it, which layers it shadowed, or which entries no layer will ever supply because
  * they are missing the `balticporter.` prefix. So the merge is exposed as data — one [[Resolved]]
  * per key, carrying its source and what it shadowed — and `just debug-flags` prints it. It is the
  * SAME fold [[get]] uses, deliberately: a diagnostic that recomputes the precedence rule is a
  * second truth about it, and would eventually disagree with the runs it claims to explain.
  */
object DebugFlags:

  val Prefix = "balticporter."

  /** repository/working root — the anchor for marker files and for relative flag paths. */
  def root: Path =
    Path.of(sys.props.getOrElse(Prefix + "root", ".")).toAbsolutePath.normalize

  /** the marker files consulted, in increasing precedence. Exposed so a diagnostic can print
    * WHERE a flag came from — an agent in another repository cannot guess. */
  def markerFiles: List[Path] = markerFilesIn(root)

  /** …under an explicit root, for a diagnostic asked about a checkout it is not running in. */
  def markerFilesIn(r: Path): List[Path] =
    List(r.resolve(".balticporter/run.properties"), r.resolve(".balticporter/debug.properties"))

  /** One source of flags, named. Layers are always listed in INCREASING precedence, so a fold that
    * keeps the last wins — which is the whole of the resolution rule, in one place.
    *
    * `ignored` is the half of the answer nothing else has: a properties file may hold entries
    * `get` will never look up, because a key without the `balticporter.` prefix cannot be reached
    * by any accessor. Written by hand — `skipPhases=*` for `balticporter.skipPhases=*` — that is a
    * flag that silently does nothing, which is the §1(b) no-op this engine refuses everywhere. */
  final case class Layer(name: String, file: Option[Path], props: Map[String, String], ignored: Map[String, String]):
    def present: Boolean = file.forall(Files.isRegularFile(_))

  /** one key's resolved value, its source, and every layer it beat. */
  final case class Resolved(key: String, value: String, source: String, shadowed: List[(String, String)])

  private def readProps(f: Path): Map[String, String] =
    if !Files.isRegularFile(f) then Map.empty
    else
      val p  = new java.util.Properties()
      val in = Files.newInputStream(f)
      try p.load(in)
      finally in.close()
      p.stringPropertyNames().toArray(Array.empty[String]).map(k => k -> p.getProperty(k)).toMap

  private def split(name: String, file: Option[Path], all: Map[String, String]): Layer =
    val (mine, theirs) = all.partition(_._1.startsWith(Prefix))
    Layer(name, file, mine, theirs)

  /** the FILE layers under `r`, freshly read — no cache, because a diagnostic that answers from a
    * cache filled before the operator edited the file answers the wrong question. */
  def fileLayers(r: Path): List[Layer] =
    markerFilesIn(r).map(f => split(f.getFileName.toString, Some(f), readProps(f)))

  /** this JVM's `balticporter.*` system properties. */
  def sysFlags: Map[String, String] =
    sys.props.toMap.filter(_._1.startsWith(Prefix))

  /** every layer, in increasing precedence. */
  def layers(r: Path, sysProps: Map[String, String]): List[Layer] =
    fileLayers(r) :+ split("system properties", scala.None, sysProps)

  /** The merge, as data: what each key resolves to, where it came from, and what it shadowed.
    * `just debug-flags` prints this; [[get]] performs the same fold. */
  def resolution(r: Path = root, sysProps: Map[String, String] = sysFlags): List[Resolved] =
    val ls = layers(r, sysProps)
    ls.flatMap(_.props.keys).distinct.sorted.map { k =>
      val hits = ls.filter(_.props.contains(k)).map(l => l.name -> l.props(k))
      val (src, v) = hits.last
      Resolved(k, v, src, hits.init)
    }

  private var cached: Option[(Path, Map[String, String])] = scala.None

  /** the merged file layers, cached per root (see the class comment for why per root). */
  private def fileProps: Map[String, String] = synchronized {
    val r = root
    cached match
      case Some((cr, m)) if cr == r => m
      case _ =>
        val m = fileLayers(r).foldLeft(Map.empty[String, String])((acc, l) => acc ++ l.props)
        cached = Some(r -> m)
        m
  }

  /** Every key an accessor below actually asks for — the table in the class comment, as data.
    *
    * A key that is not in this list is a key nothing will ever look up: `skipPhase` for
    * `skipPhases` sets a flag that does nothing, and the run it was written for then looks
    * completely normal. Nothing else can see that, so `just debug-flags` marks it. */
  val known: List[String] = List(
    "root", "skipPhases", "dumpTirBefore", "dumpTirAfter", "dumpOnly", "tracePhases", "traceNode",
    "report", "reportDir", "reportPathRoot", "baseReports",
  ).map(Prefix + _)

  /** Keys a PORT normally supplies from its own configuration, for which this flag is only the
    * fallback — for a tool that has no port configuration at all (`DebugEmit`, `CorrelateMain`) and
    * for §4.45's consumer before it has written a manifest.
    *
    * `just debug-flags` marks them, and the marking is the point: these are the flags whose effect is
    * on EMITTED TEXT rather than on a diagnostic, so a leftover entry is a checkout that emits
    * differently at the same commit with every count identical (§4.6's `reportPathRoot` lesson). An
    * operator seeing one set here should be asking whether the port ought to be stating it instead. */
  val PortSupplied: Set[String] = Set(Prefix + "baseReports")

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

  /** EXTRA directories to look for a base module's published port map in, in order — THE FALLBACK
    * ONLY (see [[PortSupplied]]).
    *
    * §4.45's consumer is an agent in ANOTHER REPOSITORY, pointing a published Baltic Porter at its
    * own Java. It has no `port-report/` tree of this checkout's shape — its base's map arrives from
    * wherever that base's port was run, or unpacked from an artifact — so the default search root
    * (the parent of THIS run's report directory) finds nothing and every base-surface question
    * degrades to `Unknown` with no way to say otherwise.
    *
    * '''A PORT states this itself''' (`PortManifest.baseReports`), and where it does, this flag is
    * not consulted at all: a base's map decides EMITTED TEXT, so which maps a run discovers belongs
    * to the run's identity and not to an operator's session — left here, a leftover entry makes two
    * checkouts at the same commit emit differently with every count identical, which is exactly
    * `reportPathRoot`'s lesson one input further in. `PortMap.searchPath` is the one place the two
    * meet, and it CHOOSES rather than merges: merging would leave that failure in place for every
    * port that had stated its own.
    *
    * Separated by the platform path separator, so a value is pasteable from a classpath. Relative
    * entries resolve against [[root]], as every other path flag does. Empty is the ordinary case and
    * makes this a no-op by arithmetic rather than by a branch. */
  def baseReports: List[Path] =
    get("baseReports").toList
      .flatMap(_.split(java.io.File.pathSeparatorChar).toList)
      .map(_.trim).filter(_.nonEmpty)
      .map(v => root.resolve(v).normalize)

  // ---- pipeline diagnosis ----
  def skipPhases: Set[String]    = list("skipPhases")
  def dumpTirBefore: Set[String] = list("dumpTirBefore")
  def dumpTirAfter: Set[String]  = list("dumpTirAfter")
  def dumpOnly: Option[String]   = get("dumpOnly")
  def tracePhases: Boolean       = bool("tracePhases")
  def traceNode: Set[String]     = list("traceNode")

  /** `*` skips everything — the whole-pipeline kill switch, one run, no source edit. */
  def skips(phaseName: String): Boolean = skipPhases.contains("*") || skipPhases.contains(phaseName)

  /** what is switched on, as pairs. Every DIAGNOSIS flag is listed — a flag missing from here is
    * one whose effect an operator sees and cannot attribute, which is the same defect as a flag
    * that is never read. */
  def active: List[String] = List(
    Option.when(skipPhases.nonEmpty)(s"skipPhases=${skipPhases.toList.sorted.mkString(",")}"),
    Option.when(dumpTirBefore.nonEmpty)(s"dumpTirBefore=${dumpTirBefore.toList.sorted.mkString(",")}"),
    Option.when(dumpTirAfter.nonEmpty)(s"dumpTirAfter=${dumpTirAfter.toList.sorted.mkString(",")}"),
    dumpOnly.map(f => s"dumpOnly=$f"),
    Option.when(tracePhases)("tracePhases=true"),
    Option.when(traceNode.nonEmpty)(s"traceNode=${traceNode.toList.sorted.mkString(",")}"),
    Option.when(baseReports.nonEmpty)(s"baseReports=${baseReports.mkString(java.io.File.pathSeparator)}"),
  ).flatten

  /** a one-line statement of what is switched on, for the run's own output. Silence when nothing
    * is set; a diagnostic that prints on a clean run trains the operator to ignore it. */
  def banner: Option[String] =
    Option.when(active.nonEmpty)(s"[balticporter] DEBUG FLAGS: ${active.mkString(" ")}")
