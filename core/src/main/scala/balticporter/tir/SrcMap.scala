package balticporter.tir

import java.nio.file.{Files, Path}

/** MEMBER-LEVEL PROVENANCE for emitted Scala — the source map that makes a compiler error, or a
  * test failure, attributable to a member and to the Java it came from.
  * (UNPORTABLE-DESIGN.md §5.2, Stage 1(a).)
  *
  * ## The problem this closes
  *
  * Every TIR node carries an [[Origin]], and the emitter knows the line it is writing — but the two
  * never met. Emitted Scala therefore had no provenance at all below the FILE: an agent reading
  * `Array.scala:1183: value + is not a member of …` had to open the file, read the emitted code,
  * work out which member it landed in, and reverse the emitter to guess which Java produced it.
  * For every error, on every iteration, without this session's context — and the consumer of this
  * engine is an agent in ANOTHER repository (CLAUDE.md §4.45).
  *
  * With `srcmap.tsv` that join is mechanical: file+line → member → Java file+line.
  *
  * ## Two artifacts, because they answer two questions and churn at different rates
  *
  *   - `srcmap.tsv` — `member → emitted line range → Java origin`. POSITIONAL, so it moves whenever
  *     anything above a member moves. It is a build product, never a baseline.
  *   - `members.tsv` — `member → digest of its emitted text`. Line-free by construction, so it
  *     changes exactly when a member's OUTPUT changes. That is the promotable baseline, and the
  *     one that answers "which members did my engine change" — the blast radius, before any
  *     compile cycle (UNPORTABLE-DESIGN.md §5.3). The measured limit it addresses is recorded in
  *     the same document: with the whole transform pipeline switched off, all four checks report
  *     IDENTICAL numbers, so the check diff cannot see a transform regression. A member digest can.
  *
  * ## Why the Java path here does NOT come from `balticporter.reportPathRoot`
  *
  * CLAUDE.md §4.6: a value carrying MEASUREMENT IDENTITY must come from the PORT, not the
  * operator. [[CheckReport.relativise]] anchors on a flag that only the measure scripts set, so a
  * migration run directly produces a baseline that diffs as removed-and-re-added against one
  * produced through the script.
  *
  * A port already knows its own source root and does not have to be told: a unit's `Origin` ends
  * in the unit's own package path (`…/com/badlogic/gdx/utils/Array.java` for
  * `com.badlogic.gdx.utils.Array`), so stripping that suffix YIELDS the root
  * ([[sourceRootOf]]). No flag, no script, same answer from any checkout. The flag is consulted
  * only as a fallback, for a unit whose emitted FQN no longer matches its origin (a package
  * rename), and the raw path is the last resort.
  */
object SrcMap:

  /** one emitted member. `start`/`end` are 1-based, inclusive, in the unit's emitted FILE — which
    * is what a compiler and a stack trace both report. */
  final case class Entry(
      unit: String,
      member: String,
      kind: String,
      start: Int,
      end: Int,
      javaPath: String,
      javaLine: Int,
      digest: String,
      /** `main` or `test` — which of a port's two source sets the unit was emitted into. Set when
        * the map is LOADED, not when it is written: the emitter does not know, and the correlator
        * is the only consumer that cares (it prefers a library frame over a test frame). */
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

  /** The directory a unit's Java file sits under once its package path is removed — i.e. the
    * port's Java source root, derived from the port's own data rather than from a flag.
    *
    * `scala.None` when the origin does not end in the unit's package path, which is exactly the
    * case (a renamed package, a synthetic origin) where guessing would be wrong. */
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
  // recording — the same stopgap shape as CheckReport, for the same reason
  // ---------------------------------------------------------------------------

  /** Gated on exactly what [[CheckReport]] is gated on, so one switch (`balticporter.report=off`)
    * turns the whole artifact layer off and a unit-test JVM produces nothing. */
  def enabled: Boolean = CheckReport.enabled

  private val recorded = collection.mutable.LinkedHashMap.empty[String, List[Entry]]
  private var hooked   = false

  /** Record one emitted unit's members. Idempotent per unit: a second emission of the same unit
    * replaces the first, so an emitter that is run twice does not double the map. */
  def record(unit: String, entries: Seq[Entry]): Unit =
    if enabled then
      synchronized {
        recorded(unit) = entries.toList
        if !hooked then
          hooked = true
          Runtime.getRuntime.addShutdownHook(new Thread(() =>
            try writeNow()
            catch case e: Exception => System.err.println(s"[balticporter] srcmap could not be written: $e")))
      }

  private val misses = collection.mutable.ListBuffer.empty[String]

  /** A member the emitter rendered but could not locate in the finished unit. It is a HOLE in the
    * map — a diagnostic landing there is attributed to whatever member happens to enclose it — so
    * it is counted and printed rather than dropped. Zero on this corpus; if it ever is not, the
    * cause is a rendering path that post-processes a member's text after `memberStat` returned it,
    * and the fix is to record it at the site that does. */
  def missed(unit: String, member: String): Unit = synchronized { misses += s"$unit#$member" }

  def snapshot(): List[Entry] = synchronized(recorded.values.toList.flatten)
  def reset(): Unit           = synchronized { recorded.clear(); misses.clear() }

  def writeNow(): Unit = if enabled && synchronized(recorded.nonEmpty) then write(CheckReport.runDir)

  def write(out: Path): Unit =
    val all = synchronized(recorded.values.toList.flatten)
      .sortBy(e => (e.unit, e.start, e.member))
    Files.createDirectories(out)
    Files.writeString(out.resolve("srcmap.tsv"), (Header :: all.map(_.tsv)).mkString("", "\n", "\n"))
    // members.tsv is sorted by NAME, not by position: a member that only moved must not move in
    // the file that is meant to show what CHANGED.
    Files.writeString(out.resolve("members.tsv"),
      (MembersHeader :: all.sortBy(e => (e.unit, e.member, e.kind)).map(_.memberTsv)).mkString("", "\n", "\n"))
    val lost = synchronized(misses.toList)
    println(s"[balticporter] srcmap: ${all.size} members over ${all.map(_.unit).distinct.size} units -> $out" +
            (if lost.isEmpty then "" else s"  !! ${lost.size} UNLOCATABLE: ${lost.take(5).mkString(", ")}"))

  // ---------------------------------------------------------------------------
  // lookup
  // ---------------------------------------------------------------------------

  /** Resolution of a compiler position or a stack frame back to an emitted member.
    *
    * Neither input names a unit directly: a compiler reports a FILE PATH under whatever output
    * root the port chose, and a stack frame reports a runtime CLASS name that may be a nested
    * type, a companion (`Foo$`) or a lambda (`Foo$$anonfun$3`). Both are resolved by SUFFIX /
    * PREFIX matching against the units the map knows, so the map never has to record the output
    * directory — which the emitter does not know either (the porting program picks it). */
  final class Index(val entries: List[Entry]):
    val byUnit: Map[String, List[Entry]] = entries.groupBy(_.unit)
    val units: Set[String]               = byUnit.keySet
    private val byPathForm: Map[String, String] = units.iterator.map(u => u.replace('.', '/') -> u).toMap

    def isEmpty: Boolean = entries.isEmpty

    /** A compiler-reported path → the unit
      * (`…/src_managed/main/scala/com/badlogic/gdx/utils/Array.scala` → `…utils.Array`). Longest
      * matching path suffix wins, so an output root that happens to contain package-shaped
      * directories cannot shorten the match. */
    def unitForFile(file: String): Option[String] =
      val n = file.replace('\\', '/').stripSuffix(".scala")
      if n.isEmpty then scala.None
      else
        val cands = 0 :: n.iterator.zipWithIndex.collect { case ('/', i) => i + 1 }.toList
        cands.iterator.flatMap(i => byPathForm.get(n.substring(i))).nextOption()

    /** A stack frame's runtime class → the top-level unit whose FILE those bytes were emitted into
      * (`com.badlogic.gdx.utils.ObjectMap$Entries` / `…$` / `…$$anonfun$3` all → `…utils.ObjectMap`).
      * Cuts only at `.` and `$`, longest first, so `com.foo.Bar` never matches `com.foo.Barn`. */
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

    /** the unit's own entry — the answer when a line falls between members (a blank line, the
      * closing brace, the package clause). Better than nothing: it still names the Java FILE. */
    def unitEntry(unit: String): Option[Entry] =
      byUnit.get(unit).flatMap(_.find(e => e.member == unit && e.kind == "class"))
        .orElse(byUnit.get(unit).flatMap(_.minByOption(_.start)))

  object Index:
    val empty: Index = new Index(Nil)
    def of(es: List[Entry]): Index = new Index(es)
