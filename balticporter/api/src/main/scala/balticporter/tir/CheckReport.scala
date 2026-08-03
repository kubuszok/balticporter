package balticporter.tir

import java.nio.file.{Files, Path}

/** PERSISTENCE and DIFF for the engine's checks — `before->after`, mechanically.
  *
  * ## The problem this closes
  *
  * Every check is computed on every migration run and then printed, truncated, to stdout.
  * There was no way to answer "did my change move omissions from 31 to 33" except scrollback
  * archaeology, which is precisely the question CLAUDE.md §5 requires every commit subject to
  * answer. Truncation makes it worse: a `take(20)` in the caller means the 21st finding has never
  * been seen by anyone.
  *
  * So: every check records its FULL result here; this writes it to a machine-readable file and
  * diffs it against a committed baseline. Truncation stays where it belongs — the human stdout
  * render — and never reaches the artifact.
  *
  * ## The ORCHESTRATOR records; a check is a pure function
  *
  * The checks used to call `record` themselves. That was a stopgap with one real virtue — it could
  * not be forgotten by a caller — taken while check invocation was copy-paste in each migration
  * program, which is how `LibgdxTestMigrate` went its whole life without calling
  * `PortabilityCheck`. `balticporter.runner.PortRun` cured the disease it was guarding against: it
  * invokes every check unconditionally, so the guard is no longer needed and the checks are pure
  * functions of a `Program` again — testable with no artifact directory in sight.
  *
  * The virtue is not lost, it MOVED UP: `PortRun.RequiredChecks` names every check a run must have
  * recorded and is compared against [[snapshot]] before the run finishes, so a number that reaches
  * stdout and never reaches `findings.tsv` fails the run. That is a stronger guarantee than
  * recording from inside a check, which could only ever assert something about checks that were
  * called.
  *
  * The recording is a no-op unless [[enabled]], so a run stays artifact-free in every context that
  * has not opted in — including the unit tests.
  *
  * ## A check that did not RUN is not a check that found NOTHING
  *
  * `record` registers the check's name even for an empty result. So `counts.tsv` distinguishes
  * "omissions: 0" from "omissions: never invoked", and [[diff]] reports the disappearance of a
  * whole check as a WARNING rather than as an improvement of N to zero. That distinction is not
  * hypothetical: a migration program silently missing a check is a defect this repository has
  * actually shipped.
  *
  * It survives the move intact, and is exactly why `PortRun` records EVERY check with its complete
  * result — `Nil` included — rather than skipping the empty ones.
  *
  * ## Format, and why
  *
  * `findings.tsv` — one tab-separated line per finding, `#`-prefixed header, sorted by
  * (check, kind, owner, path, line, detail). TSV rather than JSON because the consumers are
  * `diff`, `sort`, `cut`, `grep` and an agent reading a terminal; because a one-finding change is
  * a ONE-LINE diff (a pretty-printed JSON array re-indents, a compact one is a single enormous
  * line); and because there is no nesting to represent.
  *
  * Determinism is the whole point (a noisy diff is a diff nobody reads), so the file contains:
  * no timestamps, no absolute paths (see [[relativise]]), no hash-order iteration, and no
  * `SymId` — symbol ids are interning-order dependent and change when an unrelated file is added.
  *
  * `id` is the first 12 hex of sha-256 over `check|kind|owner|path|detail`. The LINE NUMBER is
  * carried but excluded from the id, so an upstream whitespace edit moves a finding without
  * orphaning its baseline entry (DESIGN.md §6.3 and §6.5, risk R7).
  */
object CheckReport:

  /** one finding, flattened to the columns every check has in common. `check` is the gate that
    * produced it, `kind` its own classification of the finding. */
  final case class Finding(check: String, kind: String, owner: String, path: String, line: Int, detail: String, seq: Int = 0):
    /** identity WITHOUT the line number, so an upstream whitespace edit does not orphan a
      * baseline entry. */
    def baseId: String = TirPrinter.sha256(s"$check|$kind|$owner|$path|$detail").take(12)
    /** …and a disambiguator for the case that follows from excluding the line: one member with
      * three identical findings at three different lines has one base id and three findings.
      * Without the suffix the diff cannot see two of the three being fixed. Assigned at write
      * time by [[assignSeq]] in line order. */
    def id: String = if seq == 0 then baseId else s"$baseId/$seq"
    def tsv: String = s"$id\t$check\t${clean(kind)}\t${clean(owner)}\t${clean(path)}\t$line\t${clean(detail)}"
    def render: String = s"$kind: $owner — $detail  ($path:$line)"

  /** number the findings that share a base id, in the order given. The first keeps the bare id, so
    * a member whose findings do not repeat is unaffected. */
  def assignSeq(fs: List[Finding]): List[Finding] =
    val seen = collection.mutable.Map.empty[String, Int]
    fs.map { f =>
      val n = seen.getOrElse(f.baseId, 0) + 1
      seen(f.baseId) = n
      if n == 1 then f.copy(seq = 0) else f.copy(seq = n)
    }

  private def clean(s: String): String = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim

  val Header = "#id\tcheck\tkind\towner\tpath\tline\tdetail"

  private def seqOf(id: String): Int =
    val i = id.indexOf('/')
    if i < 0 then 0 else id.substring(i + 1).toIntOption.getOrElse(0)

  def parse(line: String): Option[Finding] =
    if line.startsWith("#") || line.isBlank then scala.None
    else
      line.split('\t') match
        case Array(id, check, kind, owner, path, ln, detail) =>
          Some(Finding(check, kind, owner, path, ln.toIntOption.getOrElse(0), detail, seqOf(id)))
        case Array(id, check, kind, owner, path, ln) =>
          Some(Finding(check, kind, owner, path, ln.toIntOption.getOrElse(0), "", seqOf(id)))
        case _ => scala.None

  def parseAll(p: Path): List[Finding] =
    if !Files.isRegularFile(p) then Nil
    else Files.readAllLines(p).toArray(Array.empty[String]).toList.flatMap(parse)

  // ---------------------------------------------------------------------------
  // enablement and location
  // ---------------------------------------------------------------------------

  /** On when the build supplies `balticporter.root` (it does for a corpus migration and would for
    * any `PortRun`), or when a report dir is named explicitly, or when forced with
    * `balticporter.report=on`. Off inside a plain unit-test JVM, which has none of those — a test
    * suite must not litter a repository with run artifacts. `balticporter.report=off` disables it
    * everywhere. */
  def enabled: Boolean =
    DebugFlags.get("report").map(_ == "off") match
      case Some(true) => false
      case _ =>
        DebugFlags.bool("report") ||
        DebugFlags.get("reportDir").isDefined ||
        sys.props.contains(DebugFlags.Prefix + "root")

  /** `balticporter.reportDir`, else `port-report/<main class simple name>` under the root.
    *
    * Deriving it from the main class is deliberate: it is per-PORT without the engine knowing any
    * port's name (CLAUDE.md §1 — nothing in `core` may name a ported library), it separates two
    * migration programs in the same repository automatically, and it needs no configuration at
    * the call site, which is the only way it could work at all while the migration programs are
    * copy-paste. */
  def dir: Path =
    DebugFlags.path("reportDir").getOrElse(DebugFlags.root.resolve(s"port-report/$mainClassKey"))

  private def mainClassKey: String =
    Option(System.getProperty("sun.java.command"))
      .map(_.split(' ').head)
      .map(c => c.substring(c.lastIndexOf('.') + 1))
      .filter(s => s.nonEmpty && s.forall(c => c.isLetterOrDigit || c == '_' || c == '-'))
      .getOrElse("default")

  def runDir: Path      = dir.resolve("run-latest")
  def baselineDir: Path = dir.resolve("baseline")

  /** Make a Java source path relative and machine-independent. `balticporter.reportPathRoot` is
    * the source root a port was built from; without it, paths fall back to relative-to-root,
    * which is still deterministic but only within one checkout layout. An ABSOLUTE path in the
    * artifact would make every diff machine-specific, so it is the one thing never emitted. */
  def relativise(p: String): String =
    val root = DebugFlags.path("reportPathRoot").getOrElse(DebugFlags.root)
    try
      val abs = Path.of(p)
      if !abs.isAbsolute then p
      else
        // Resolve SYMLINKS on both sides first. A checkout reached through a symlinked parent (a
        // git worktree, a mounted source tree) otherwise relativises to a stack of `..` segments
        // that depends on where the link lives — deterministic, but different from the same
        // baseline computed in the primary checkout, which defeats the point.
        //
        // Through `RealPath` (§5.4's one implementation) rather than a local helper. The local one
        // fell back to a BARE `normalize`, so a relative root stayed relative, `relativize` threw
        // "different type of Path", and the outer catch below returned `p` — the raw absolute path
        // this function's own doc promises never to emit.
        balticporter.core.RealPath.relativize(root, abs).toString.replace('\\', '/')
    catch case _: Exception => p

  // ---------------------------------------------------------------------------
  // recording
  // ---------------------------------------------------------------------------

  private val recorded  = collection.mutable.LinkedHashMap.empty[String, List[Finding]]
  private var hooked    = false

  /** Record a check's COMPLETE result. Registers the check name even for `Nil`. No-op when
    * disabled. Idempotent per check name within a run: a second call replaces the first, so a
    * migration that filters and re-records (portability does) reports the filtered number. */
  def record(check: String, findings: Seq[Finding]): Unit =
    if enabled then
      synchronized {
        recorded(check) = findings.toList
        if !hooked then
          hooked = true
          Runtime.getRuntime.addShutdownHook(new Thread(() => try writeNow() catch { case e: Exception =>
            System.err.println(s"[balticporter] check report could not be written: $e") }))
      }

  /** what has been recorded so far — for a caller that wants the numbers in-process. */
  def snapshot(): Map[String, List[Finding]] = synchronized(recorded.toMap)

  def reset(): Unit = synchronized(recorded.clear())

  // ---------------------------------------------------------------------------
  // writing
  // ---------------------------------------------------------------------------

  /** the shutdown-hook path. Nothing recorded ⇒ nothing written: a run that never invoked a check
    * must not leave an artifact claiming every check found zero. */
  def writeNow(): Unit = if enabled && synchronized(recorded.nonEmpty) then write(runDir)

  def write(out: Path): Unit =
    val findings = synchronized(recorded.toList)
    Files.createDirectories(out)
    // run outputs are build products; only `baseline/` is meant to be committed.
    Files.writeString(dir.resolve(".gitignore"), "run-latest/\n")
    val all = assignSeq(findings.flatMap(_._2).sortBy(f => (f.check, f.kind, f.owner, f.path, f.line, f.detail)))
    Files.writeString(out.resolve("findings.tsv"), (Header :: all.map(_.tsv)).mkString("", "\n", "\n"))
    Files.writeString(out.resolve("counts.tsv"),
      ("#check\tcount" :: findings.map((c, fs) => s"$c\t${fs.size}").sorted).mkString("", "\n", "\n"))
    Files.writeString(out.resolve("report.md"), reportMd(findings))
    val d = diff(parseAll(baselineDir.resolve("findings.tsv")), all,
                 baselineChecks(baselineDir), findings.map(_._1).toSet,
                 hasBaseline = Files.isRegularFile(baselineDir.resolve("findings.tsv")))
    Files.writeString(out.resolve("diff.txt"), renderDiff(d))
    Files.writeString(out.resolve("subject.txt"), subject(d) + "\n")

  private def baselineChecks(b: Path): Set[String] =
    val c = b.resolve("counts.tsv")
    if !Files.isRegularFile(c) then Set.empty
    else Files.readAllLines(c).toArray(Array.empty[String]).toList
      .filterNot(_.startsWith("#")).flatMap(_.split('\t').headOption).filter(_.nonEmpty).toSet

  /** The operator document. NOT diffed and NOT committed as a baseline — it may contain anything
    * a human finds useful, including the path root the run used, which the tsv deliberately
    * omits. */
  private def reportMd(findings: List[(String, List[Finding])]): String =
    val sb = new StringBuilder
    sb.append("# Port check report\n\n")
    sb.append(s"path root: `${DebugFlags.path("reportPathRoot").getOrElse(DebugFlags.root)}`\n\n")
    // What the §4.6 flags actually were IN THIS RUN. The flags are resolved in the migration's own
    // forked JVM from files the operator may since have edited, so "did my flag reach the run" is
    // not answerable after the fact from anything else — `just debug-flags PORT` reads this line.
    // Recorded even when empty: "(none)" is the answer to that question as often as a flag is.
    sb.append(s"debug flags: ${DebugFlags.active match { case Nil => "(none)"; case on => on.mkString(" ") }}\n\n")
    sb.append("| check | findings |\n|---|---|\n")
    findings.map((c, fs) => s"| $c | ${fs.size} |").sorted.foreach(l => sb.append(l).append('\n'))
    findings.sortBy(_._1).foreach { (c, fs) =>
      sb.append(s"\n## $c — ${fs.size}\n\n")
      if fs.isEmpty then sb.append("none\n")
      else fs.sortBy(f => (f.kind, f.owner, f.path, f.line)).foreach(f => sb.append(s"- `${f.id}` ${f.render}\n"))
    }
    sb.result()

  // ---------------------------------------------------------------------------
  // diff
  // ---------------------------------------------------------------------------

  final case class CheckDelta(check: String, before: Int, after: Int, appeared: List[Finding], disappeared: List[Finding], ran: Boolean, inBaseline: Boolean)
  final case class Diff(hasBaseline: Boolean, deltas: List[CheckDelta]):
    def changed: Boolean = deltas.exists(d => d.before != d.after || !d.ran)

  def diff(baseline: List[Finding], latest: List[Finding], baselineChecks: Set[String], latestChecks: Set[String], hasBaseline: Boolean): Diff =
    val checks = (baselineChecks ++ latestChecks ++ baseline.map(_.check) ++ latest.map(_.check)).toList.sorted
    Diff(hasBaseline, checks.map { c =>
      val b = baseline.filter(_.check == c)
      val a = latest.filter(_.check == c)
      val bIds = b.map(_.id).toSet
      val aIds = a.map(_.id).toSet
      CheckDelta(c, b.size, a.size,
        a.filterNot(f => bIds(f.id)).sortBy(f => (f.kind, f.owner, f.path, f.line)),
        b.filterNot(f => aIds(f.id)).sortBy(f => (f.kind, f.owner, f.path, f.line)),
        ran = latestChecks(c), inBaseline = baselineChecks(c) || b.nonEmpty)
    })

  /** the commit-subject fragment CLAUDE.md §5 asks for, computed rather than remembered. */
  def subject(d: Diff): String =
    d.deltas.map { x =>
      // a check that no longer runs must never be summarised as "N->0": that reads as a fix.
      if !x.ran then s"${x.check} ${x.before}->NOT-RUN"
      else if !d.hasBaseline || x.before == x.after then s"${x.check} ${x.after}"
      else s"${x.check} ${x.before}->${x.after}"
    }.mkString(", ")

  /** How many appeared/disappeared findings to spell out per check. The FULL lists are in
    * `findings.tsv`; this is the terminal render, and a diff that scrolls off the screen is not
    * read. */
  val RenderLimit = 12

  def renderDiff(d: Diff): String =
    val sb = new StringBuilder
    if !d.hasBaseline then
      sb.append("NO BASELINE — these are the current numbers, nothing to compare against.\n")
      sb.append(s"Accept them with:  just baseline-accept ${dir.getFileName}\n\n")
    d.deltas.foreach { x =>
      val arrow =
        if !x.ran then s"${x.before} -> (CHECK DID NOT RUN)"
        else if !d.hasBaseline || !x.inBaseline then s"${x.after} (new check)"
        else s"${x.before} -> ${x.after}"
      val mark = if !x.ran then "!!" else if x.before != x.after then " *" else "  "
      sb.append(f"$mark ${x.check}%-28s $arrow\n")
      if !x.ran && x.before > 0 then
        sb.append("     a check that stopped running is NOT a check that found nothing —\n")
        sb.append("     the migration program no longer invokes it.\n")
      x.appeared.take(RenderLimit).foreach(f => sb.append(s"     + [${f.id}] ${f.render}\n"))
      if x.appeared.size > RenderLimit then sb.append(s"     + … ${x.appeared.size - RenderLimit} more (see findings.tsv)\n")
      x.disappeared.take(RenderLimit).foreach(f => sb.append(s"     - [${f.id}] ${f.render}\n"))
      if x.disappeared.size > RenderLimit then sb.append(s"     - … ${x.disappeared.size - RenderLimit} more (see findings.tsv)\n")
    }
    sb.append(s"\nsubject: ${subject(d)}\n")
    sb.result()

