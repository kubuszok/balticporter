package balticporter.tir

import java.nio.file.{Files, Path}

/** PERSISTENCE and DIFF for the engine's checks — `before->after`, mechanically. Every check
  * records its FULL result here; this writes a machine-readable file and diffs against a committed
  * baseline. The ORCHESTRATOR records (checks stay pure functions of a `Program`); `record`
  * registers even an empty result, so `counts.tsv` distinguishes "0" from "never invoked".
  * `findings.tsv` id = sha-256 excluding the LINE NUMBER, so a whitespace edit orphans nothing. */
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

  /** On when the build supplies `balticporter.root`, or a report dir is named, or forced with
    * `balticporter.report=on`. Off in a plain unit-test JVM (no litter). Off WHATEVER the flags
    * say when there is no port identity to name a directory after (`sun.java.command` under sbt 2
    * forked tests answers `WorkerMain`, which would publish into the checkout) — §5.1's gate.
    * An explicit `reportDir` still enables it, since the caller supplied the identity. */
  def enabled: Boolean =
    DebugFlags.get("report").map(_ == "off") match
      case Some(true) => false
      case _ =>
        DebugFlags.get("reportDir").isDefined ||
        ((DebugFlags.bool("report") || sys.props.contains(DebugFlags.Prefix + "root")) && mainClassKey.isDefined)

  /** `balticporter.reportDir`, else `port-report/<main class simple name>` under the root. Derived
    * from the main class deliberately — per-PORT without the engine naming a library (§1), and no
    * call-site configuration needed. [[NoMainClass]] is the total-but-unwritten fallback ([[enabled]]
    * is false there); `PortMap` still discovers a base's map through this directory's PARENT. */
  def dir: Path =
    DebugFlags.path("reportDir").getOrElse(DebugFlags.root.resolve(s"port-report/${mainClassKey.getOrElse(NoMainClass)}"))

  /** the placeholder segment for "this JVM has no port identity"; see [[dir]]. */
  private[tir] val NoMainClass = "default"

  /** Mains that belong to the BUILD, not to a port — a report directory named after one would be
    * a directory of unrequested artifacts. A prefix test §4.56 normally forbids, but there is no
    * structure to read in `sun.java.command`'s bare command line — the honest move is an explicit,
    * short negative rather than an invented structural claim. */
  private val BuildToolMains = List("sbt.", "xsbt.", "org.scalatest.", "munit.", "org.junit.")

  /** the launching main class's simple name, when it is a PORT's own migration program. `None`
    * when launched by the build tool or the command is absent/not a plain class name. Measure
    * lanes are unaffected: each runs a migration `main`, the identity CLAUDE.md §2.1 keeps stable. */
  private[tir] def mainClassKey: Option[String] =
    Option(System.getProperty("sun.java.command"))
      .map(_.split(' ').head)
      .filterNot(c => BuildToolMains.exists(c.startsWith))
      .map(c => c.substring(c.lastIndexOf('.') + 1))
      .filter(s => s.nonEmpty && s.forall(c => c.isLetterOrDigit || c == '_' || c == '-'))

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
        // Resolve SYMLINKS on both sides first — a symlinked parent (a git worktree, a mounted
        // source tree) otherwise relativises to a `..` stack that depends on the link's location,
        // deterministic but different from the primary checkout's baseline (§5.4). Through
        // `RealPath`, not a local helper — the local one fell back to a bare `normalize` and threw.
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

  def reset(): Unit = synchronized { recorded.clear(); upstream = scala.None }

  /** `counts.tsv`'s one NON-CHECK row: which java tree this run measured (CLAUDE.md §5). A moved
    * vendored submodule otherwise reads as a suite regression with no artifact naming the cause.
    * Never a check — [[baselineChecks]] skips the key, so the diff can never call it NOT-RUN. */
  val UpstreamKey = "upstream"

  private var upstream: Option[String] = scala.None

  /** Record this run's upstream pin. A run with no provenance records nothing and writes no row. */
  def recordUpstream(name: String, commit: String): Unit =
    synchronized { upstream = Some(upstreamRow(name, commit)) }

  /** `<name>@<sha>` — the sha as `VendoredCommit` spells it, or the whole pin where it has no `@`. */
  def upstreamRow(name: String, commit: String): String =
    val at  = commit.indexOf('@')
    val sha = if at < 0 then commit.trim else commit.substring(at + 1).takeWhile(!_.isWhitespace)
    s"${clean(name)}@${clean(sha)}"

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
    val upstreamRows = synchronized(upstream).map(u => s"$UpstreamKey\t$u").toList
    Files.writeString(out.resolve("counts.tsv"),
      ("#check\tcount" :: findings.map((c, fs) => s"$c\t${fs.size}").sorted ++ upstreamRows)
        .mkString("", "\n", "\n"))
    Files.writeString(out.resolve("report.md"), reportMd(findings))
    // WHICH JVM THIS RUN RAN ON — the input to the emitted text that no artifact named until a
    // migration on JDK 24 emitted an `override` the JDK-22 compile rejected (`JvmInfo`'s scaladoc
    // carries the measurement). Written here rather than in `PortRun` for the reason every other
    // artifact is: this is the layer that knows a run directory exists and is gated on the artifact
    // layer, so a spec's forked JVM cannot publish one into the checkout.
    Files.writeString(out.resolve("jvm.txt"), balticporter.core.JvmInfo.render)
    val d = diff(parseAll(baselineDir.resolve("findings.tsv")), all,
                 baselineChecks(baselineDir), findings.map(_._1).toSet,
                 hasBaseline = Files.isRegularFile(baselineDir.resolve("findings.tsv")))
    Files.writeString(out.resolve("diff.txt"), renderDiff(d))
    Files.writeString(out.resolve("subject.txt"), subject(d) + "\n")

  private def baselineChecks(b: Path): Set[String] =
    val c = b.resolve("counts.tsv")
    if !Files.isRegularFile(c) then Set.empty
    else Files.readAllLines(c).toArray(Array.empty[String]).toList
      .filterNot(_.startsWith("#")).flatMap(_.split('\t').headOption)
      .filter(k => k.nonEmpty && k != UpstreamKey).toSet

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
    // …and the JVM, for the same reason and in the same document: `jvm.txt` is what a guard reads,
    // and this is what an operator reads when a port map's `jdk=` disagrees with a compile.
    sb.append(s"jvm: ${balticporter.core.JvmInfo.version} (${balticporter.core.JvmInfo.vendor}), " +
      s"spec ${balticporter.core.JvmInfo.specification}, java.home `${balticporter.core.JvmInfo.home}`\n\n")
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

