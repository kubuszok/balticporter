package balticporter.tir

import java.nio.file.{Files, Path}

/** Member-level source map: `srcmap.tsv` (positional, build product) and `members.tsv`
  * (digest-only baseline). Joins compiler/test output back to (member, Java origin).
  * Java paths are derived from [[sourceRootOf]], not from `balticporter.reportPathRoot`. */
object SrcMap:

  /** One emitted member. `start`/`end` are 1-based inclusive line numbers in the emitted file. */
  final case class Entry(
      unit: String,
      member: String,
      kind: String,
      start: Int,
      end: Int,
      javaPath: String,
      javaLine: Int,
      digest: String,
      /** `main` or `test`. Set when loaded, not when written. */
      scope: String = "main",
  ):
    def tsv: String        = s"$unit\t$member\t$kind\t$start\t$end\t$javaPath\t$javaLine\t$digest"
    def memberTsv: String  = s"$unit\t$member\t$kind\t$digest"
    def javaAt: String     = if javaLine > 0 then s"$javaPath:$javaLine" else javaPath
    def render: String     = s"$member  ($javaAt)"

  val Header        = "#unit\tmember\tkind\tstart\tend\tjavaPath\tjavaLine\tdigest"
  val MembersHeader = "#unit\tmember\tkind\tdigest"

  def parse(line: String): Option[Entry] =
    if line.startsWith("#") || line.isBlank then scala.None
    else
      line.split('\t') match
        case Array(u, m, k, s, e, jp, jl, d) =>
          Some(Entry(u, m, k, s.toIntOption.getOrElse(0), e.toIntOption.getOrElse(0), jp, jl.toIntOption.getOrElse(0), d))
        case _ => scala.None

  def parseAll(p: Path, scope: String = "main"): List[Entry] =
    if !Files.isRegularFile(p) then Nil
    else Files.readAllLines(p).toArray(Array.empty[String]).toList.flatMap(parse).map(_.copy(scope = scope))

  /** member digests only, keyed by `unit\tmember` — the baseline form. */
  def parseMembers(p: Path): Map[String, String] =
    if !Files.isRegularFile(p) then Map.empty
    else
      Files.readAllLines(p).toArray(Array.empty[String]).toList.flatMap { l =>
        if l.startsWith("#") || l.isBlank then scala.None
        else
          l.split('\t') match
            case Array(u, m, _, d) => Some(s"$u\t$m" -> d)
            case _                 => scala.None
      }.toMap

  // ---------------------------------------------------------------------------
  // the port's own source root
  // ---------------------------------------------------------------------------

  /** The port's Java source root, derived by stripping the unit's package path from `javaPath`.
    * `None` when the origin does not end in the package path (renamed package, synthetic origin). */
  def sourceRootOf(unitFqn: String, javaPath: String): Option[String] =
    val top    = unitFqn.takeWhile(_ != '$')
    val suffix = "/" + top.replace('.', '/') + ".java"
    val norm   = javaPath.replace('\\', '/')
    if norm.endsWith(suffix) then Some(norm.substring(0, norm.length - suffix.length + 1)) else scala.None

  /** Relativise a Java path against a root derived from the port ([[sourceRootOf]]), falling back
    * to [[CheckReport.relativise]] and then to the path as recorded. */
  def relativise(javaPath: String, root: Option[String]): String =
    if javaPath.isEmpty || javaPath.startsWith("<") then javaPath
    else
      val norm = javaPath.replace('\\', '/')
      root.filter(norm.startsWith) match
        case Some(r) => norm.substring(r.length)
        case scala.None => CheckReport.relativise(javaPath)

  // ---------------------------------------------------------------------------
  // recording — a VALUE the emitter accumulates, written by the orchestrator
  // ---------------------------------------------------------------------------

  /** Gated on [[CheckReport.enabled]]. */
  def enabled: Boolean = CheckReport.enabled

  /** One emitter's source map — a value one emitter owns, written by `PortRun`.
    * `missed` entries are holes where a rendered member could not be located in the finished unit. */
  final case class Recording(entries: List[Entry], missed: List[String] = Nil):
    def isEmpty: Boolean = entries.isEmpty
    def units: Int       = entries.map(_.unit).distinct.size

  object Recording:
    val empty: Recording = Recording(Nil, Nil)

  /** Write one emitter's map into a run directory. */
  def write(out: Path, rec: Recording): Unit =
    val all = rec.entries.sortBy(e => (e.unit, e.start, e.member))
    Files.createDirectories(out)
    Files.writeString(out.resolve("srcmap.tsv"), (Header :: all.map(_.tsv)).mkString("", "\n", "\n"))
    // members.tsv sorted by name, not position, so a positional shift does not move a digest row
    Files.writeString(out.resolve("members.tsv"),
      (MembersHeader :: all.sortBy(e => (e.unit, e.member, e.kind)).map(_.memberTsv)).mkString("", "\n", "\n"))
    println(s"[balticporter] srcmap: ${all.size} members over ${all.map(_.unit).distinct.size} units -> $out" +
            (if rec.missed.isEmpty then ""
             else s"  !! ${rec.missed.size} UNLOCATABLE: ${rec.missed.take(5).mkString(", ")}"))

  // ---------------------------------------------------------------------------
  // lookup
  // ---------------------------------------------------------------------------

  /** Resolves a compiler path or stack-frame class name to an emitted member.
    * Uses suffix/prefix matching against known units — no output directory needed. */
  final class Index(val entries: List[Entry]):
    val byUnit: Map[String, List[Entry]] = entries.groupBy(_.unit)
    val units: Set[String]               = byUnit.keySet
    private val byPathForm: Map[String, String] = units.iterator.map(u => u.replace('.', '/') -> u).toMap

    def isEmpty: Boolean = entries.isEmpty

    /** Compiler-reported path → unit. Longest matching path suffix wins. */
    def unitForFile(file: String): Option[String] =
      val n = file.replace('\\', '/').stripSuffix(".scala")
      if n.isEmpty then scala.None
      else
        val cands = 0 :: n.iterator.zipWithIndex.collect { case ('/', i) => i + 1 }.toList
        cands.iterator.flatMap(i => byPathForm.get(n.substring(i))).nextOption()

    /** Runtime class → top-level unit. Cuts at `.` and `$`, longest first. */
    def unitForClass(className: String): Option[String] =
      val n = className.replace('/', '.')
      if units(n) then Some(n)
      else
        val cuts = n.iterator.zipWithIndex.collect { case (c, i) if c == '$' || c == '.' => i }.toList.reverse
        cuts.iterator.map(n.substring(0, _)).flatMap(p => Option.when(units(p))(p)).nextOption()

    /** the INNERMOST member whose emitted range contains `line`. */
    def at(unit: String, line: Int): Option[Entry] =
      byUnit.get(unit).flatMap { es =>
        val in = es.filter(e => e.start <= line && line <= e.end)
        if in.isEmpty then scala.None else Some(in.maxBy(e => (e.start, -e.end)))
      }

    def resolveFile(file: String, line: Int): Option[Entry] =
      unitForFile(file).flatMap(u => at(u, line).orElse(unitEntry(u)))

    def resolveFrame(className: String, line: Int): Option[Entry] =
      unitForClass(className).flatMap(u => at(u, line).orElse(unitEntry(u)))

    /** Fallback entry for a line between members — still names the Java file. */
    def unitEntry(unit: String): Option[Entry] =
      byUnit.get(unit).flatMap(_.find(e => e.member == unit && e.kind == "class"))
        .orElse(byUnit.get(unit).flatMap(_.minByOption(_.start)))

  object Index:
    val empty: Index = new Index(Nil)
    def of(es: List[Entry]): Index = new Index(es)
