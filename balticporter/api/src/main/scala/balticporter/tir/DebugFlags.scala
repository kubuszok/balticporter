package balticporter.tir

import java.nio.file.{Files, Path}

/** Run-time switches for DIAGNOSIS — the kill switch of CLAUDE.md §4.6, promoted from folklore to
  * a flag. Resolves (increasing precedence): `run.properties` (script-written), `debug.properties`
  * (operator-written), system properties — never a shell env var or `-D`, since `sbt -client`'s
  * migration JVM is forked from a server neither reaches. `<root>` is `-Dbalticporter.root` or the
  * cwd; absent files/props means every flag is empty. See `just debug-flags`. */
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
    * keeps the last wins. `ignored` holds entries `get` will never look up (missing the
    * `balticporter.` prefix or a misspelled key) — a flag that silently does nothing, the §1(b)
    * no-op this engine refuses everywhere. */
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
    * fallback (a tool with no port configuration, or §4.45's consumer before it has a manifest).
    * `just debug-flags` marks them: their effect is on EMITTED TEXT, so a leftover entry makes a
    * checkout emit differently at the same commit with every count identical (§4.6). */
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

  /** EXTRA directories to look for a base module's published port map in — THE FALLBACK ONLY (see
    * [[PortSupplied]]). §4.45's consumer has no `port-report/` tree of this checkout's shape, so
    * the default search root finds nothing. A PORT states this itself (`PortManifest.baseReports`)
    * where it can; a leftover flag entry here makes two checkouts at the same commit emit
    * differently, with every count identical. `PortMap.searchPath` CHOOSES rather than merges. */
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
