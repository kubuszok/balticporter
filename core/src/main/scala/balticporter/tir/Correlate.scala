package balticporter.tir

import java.nio.file.{Files, Path}

/** Attribute a COMPILER ERROR or a TEST FAILURE over emitted Scala back to the member that
  * produced it and to the Java it came from. UNPORTABLE-DESIGN.md §6.3 (Stage 1(b)) plus the
  * amendment LIBRARY-READINESS.md §2.4 forces on it.
  *
  * ## The two lanes, and why the second one is the point
  *
  * §6.3 as written triages **scalac errors**: an error at a region the engine already marked
  * approximate is *classified* and carries its remediation; an error anywhere else is an *engine
  * gap*, and this joins it to (member, Java origin) so nobody has to reverse the emitter by hand.
  * With no marker set — Stage 2 is deliberately not built — every error lands in the second lane,
  * which is correct, not a degradation: the lane exists so that Stage 2 has somewhere to put the
  * first one.
  *
  * But CLAUDE.md §4.4 lists ten Java forms that translate to *valid* Scala meaning something else:
  * reference `==`, post-increment as a value, `break`/`continue`, `switch` fall-out, `static final`
  * inlining, a dropped `super(args)`, `@Before`. **None of them moves a compile-error count.** All
  * of them were found by running the ported tests. A correlation lane that only reads the compiler
  * is therefore blind to the entire defect class this project keeps re-discovering — so the same
  * join runs over the TEST RUNNER's output, and the pass/fail sets are diffed run-over-run exactly
  * as §5.3 diffs findings.
  *
  * ## How reliable the test anchor is — stated plainly, because it varies
  *
  * A failing test is anchored by the first STACK FRAME that lands in ported code, not by its name.
  * That gives three qualities of answer, and the artifact says which one it got:
  *
  *   - `main-frame` — the failure threw inside the ported LIBRARY. The anchor is the guilty member.
  *     This is the §4.4 case and the anchor is exact.
  *   - `test-frame` — the top ported frame is the test body itself, which is what a plain
  *     `assertEquals` mismatch produces: the assertion throws in the framework, and the deepest
  *     ported frame is the assertion's caller. The anchor then names WHERE THE FAILURE WAS
  *     OBSERVED, not where the wrong value was computed. Still the right starting point (it names
  *     the Java test and the line), but it does not name the defect.
  *   - `assert-site` / `suite` / `none` — progressively weaker fallbacks for a runner that trimmed
  *     the stack.
  *
  * The join with member digests is what recovers the missing half of a `test-frame` answer: a test
  * that newly fails, plus the list of members whose emitted text changed since the baseline, is a
  * far smaller suspect set than "the diff".
  *
  * ## Expected failures are DATA
  *
  * A port that deliberately substitutes a type ships tests that must fail. Those are read from
  * `expected-failures.tsv` in the BASELINE directory (`suite`, `test` — `*` for the whole suite —
  * and a reason), never from a name in this file: `core` may not know a ported library exists
  * (CLAUDE.md §1). An expected failure is reported as expected and is not a regression; an
  * expected failure that PASSES is reported too, because a substitution that started working is
  * news.
  */
object Correlate:

  // ===========================================================================
  // scalac
  // ===========================================================================

  final case class ScalacError(code: String, kind: String, path: String, line: Int, col: Int, message: String)

  private val ErrHeader = raw"^-- (?:\[(E\d+)\] )?(.*?)Error(?:: (.+?):(\d+):(\d+))?\s*-*$$".r
  private val PipeLine  = raw"^\s*\|(.*)$$".r

  /** Parse a dotty/scala-cli compile log. Both the coded (`-- [E006] … Error: f:1:2 ---`) and the
    * bare (`-- Error: f:1:2 ---`) header forms are recognised, because counting only coded errors
    * silently undercounts — the same trap `scripts/gdx_measure.sh` documents. */
  def parseScalac(text: String): List[ScalacError] =
    val lines = text.linesIterator.toArray
    val out   = collection.mutable.ListBuffer.empty[ScalacError]
    var i     = 0
    while i < lines.length do
      lines(i) match
        case ErrHeader(code, kind, path, ln, col) =>
          // the explanation is the first `|`-prefixed line that is neither blank nor the caret rule
          var j   = i + 1
          var msg = ""
          while j < lines.length && msg.isEmpty && !lines(j).startsWith("-- ") do
            lines(j) match
              case PipeLine(c) =>
                val t = c.trim
                if t.nonEmpty && !t.forall(_ == '^') then msg = t
              case _ => ()
            j += 1
          out += ScalacError(
            Option(code).getOrElse(""), kind.trim,
            Option(path).getOrElse(""), Option(ln).flatMap(_.toIntOption).getOrElse(0),
            Option(col).flatMap(_.toIntOption).getOrElse(0), msg)
        case _ => ()
      i += 1
    out.toList

  // ===========================================================================
  // the test runner (MUnit)
  // ===========================================================================

  final case class Frame(cls: String, method: String, file: String, line: Int)
  final case class Outcome(suite: String, name: String, status: String, detail: String, frames: List[Frame]):
    def id: String = s"$suite\t$name"

  private val SuiteLine = raw"^([A-Za-z_][\w.$$]*)\s*:\s*$$".r
  private val PassLine  = raw"^\s*\+ (.*?)\s+[0-9]+(?:\.[0-9]+)?s\s*$$".r
  private val FailLine  = raw"^==> ([XiI]) (.+?)\s+[0-9]+(?:\.[0-9]+)?s\s*(.*)$$".r
  /** the same marker with NO duration — how MUnit prints an IGNORED test. Without this the line is
    * skipped, the test vanishes from `tests.tsv`, and the diff reports it as "did not run" — loud,
    * but wrong about why. An ignored test is a DECISION; a missing one is a defect. */
  private val MarkLine  = raw"^==> ([XiI]) (\S+)\s*(.*)$$".r
  private val FrameLine = raw"^\s+at ([^(\s]+)\(([^:)]+)(?::(\d+))?\)\s*$$".r
  private val AssertAt  = raw"([^\s:]+\.scala):(\d+)".r

  private def marked(suite: String, mark: String, full: String, detail: String): Outcome =
    val name = if suite.nonEmpty && full.startsWith(suite + ".") then full.substring(suite.length + 1)
               else full.reverse.takeWhile(_ != '.').reverse
    Outcome(if suite.nonEmpty then suite else full.stripSuffix("." + name), name,
            if mark == "X" then "fail" else "ignored", detail.trim, Nil)

  /** Parse an MUnit console log into per-test outcomes. Passes carry no frames; a failure carries
    * every stack frame printed under it, in order, which is what the anchor is chosen from. */
  def parseTests(text: String): List[Outcome] =
    val out     = collection.mutable.ListBuffer.empty[Outcome]
    var suite   = ""
    var pending = scala.Option.empty[Outcome]
    def flush(): Unit = { pending.foreach(o => out += o.copy(frames = o.frames.reverse)); pending = scala.None }
    text.linesIterator.foreach { l =>
      l match
        case SuiteLine(s)             => flush(); suite = s
        case PassLine(n)              => flush(); out += Outcome(suite, n.trim, "pass", "", Nil)
        case FailLine(mark, full, d) => flush(); pending = Some(marked(suite, mark, full, d))
        case MarkLine(mark, full, d) => flush(); pending = Some(marked(suite, mark, full, d))
        case FrameLine(qual, file, ln) =>
          pending.foreach { o =>
            val cut = qual.lastIndexOf('.')
            val f   = Frame(if cut > 0 then qual.substring(0, cut) else qual,
                            if cut > 0 then qual.substring(cut + 1) else "",
                            file, Option(ln).flatMap(_.toIntOption).getOrElse(0))
            pending = Some(o.copy(frames = f :: o.frames))
          }
        case _ => ()
    }
    flush()
    out.toList

  // ===========================================================================
  // expected failures — read from the port's data, never from a name in here
  // ===========================================================================

  final case class Expected(suite: String, test: String, reason: String):
    def matches(o: Outcome): Boolean = o.suite == suite && (test == "*" || test == o.name)

  val ExpectedHeader = "#suite\ttest\treason"

  def parseExpected(p: Path): List[Expected] =
    if !Files.isRegularFile(p) then Nil
    else
      Files.readAllLines(p).toArray(Array.empty[String]).toList.flatMap { l =>
        if l.startsWith("#") || l.isBlank then scala.None
        else
          l.split('\t').toList match
            case s :: t :: rest => Some(Expected(s.trim, t.trim, rest.mkString(" ").trim))
            case _              => scala.None
      }

  // ===========================================================================
  // the join
  // ===========================================================================

  /** which of UNPORTABLE-DESIGN.md §6.3's lanes a diagnostic fell into. */
  enum Lane:
    /** at a region the engine marked approximate — expected, carries a remediation. Empty until
      * Stage 2 mints markers; the lane exists so that adding them is a data change. */
    case Approx
    /** anywhere else in emitted code — an ENGINE GAP, now auto-located. */
    case EngineGap
    /** in a file the source map does not cover: injected Scala, a runtime shim, a dependency. Not
      * an engine gap and not a marked region — reported separately rather than silently counted
      * as either. */
    case Unmapped

  final case class LocatedError(err: ScalacError, entry: Option[SrcMap.Entry], lane: Lane):
    def tsv: String =
      val e = entry
      s"$lane\t${err.path}\t${err.line}\t${err.code}\t${e.map(_.unit).getOrElse("")}\t" +
      s"${e.map(_.member).getOrElse("")}\t${e.map(_.javaPath).getOrElse("")}\t${e.map(_.javaLine).getOrElse(0)}\t${err.message}"

  val ErrorsHeader = "#lane\tfile\tline\tcode\tunit\tmember\tjavaPath\tjavaLine\tmessage"

  /** `markers` is the set of `unit\tmember` keys the engine marked approximate. Empty input is the
    * Stage-1 state and must stay a legal, meaningful input — an engine that needed markers to
    * report anything would be useless on the first day of a new library. */
  def locateErrors(errs: List[ScalacError], idx: SrcMap.Index, markers: Set[String] = Set.empty): List[LocatedError] =
    errs.map { e =>
      idx.resolveFile(e.path, e.line) match
        case Some(x) if markers(s"${x.unit}\t${x.member}") => LocatedError(e, Some(x), Lane.Approx)
        case Some(x)                                       => LocatedError(e, Some(x), Lane.EngineGap)
        case scala.None                                    => LocatedError(e, scala.None, Lane.Unmapped)
    }

  final case class LocatedTest(
      outcome: Outcome,
      anchor: String,                      // main-frame | test-frame | assert-site | suite | none
      entry: Option[SrcMap.Entry],
      portedFrames: List[(Frame, SrcMap.Entry)],
      expected: Option[Expected],
      digestChanged: Boolean,
      /** how many members of the ANCHORED UNIT changed since the baseline. The per-member flag is
        * exact but narrow: a class-initialiser cycle is *triggered* by a member that did not
        * itself change (its `inline val` siblings did), and the unit count is what points at that
        * without pretending the anchor moved. */
      unitChanged: Int = 0,
  ):
    def tsv: String =
      val e = entry
      s"${outcome.suite}\t${outcome.name}\t${outcome.status}\t$anchor\t${e.map(_.unit).getOrElse("")}\t" +
      s"${e.map(_.member).getOrElse("")}\t${e.map(_.javaPath).getOrElse("")}\t${e.map(_.javaLine).getOrElse(0)}\t" +
      s"${if expected.isDefined then "expected" else "unexpected"}\t${digestChanged}\t$unitChanged\t${outcome.detail}"

  val FailuresHeader =
    "#suite\ttest\tstatus\tanchor\tunit\tmember\tjavaPath\tjavaLine\texpectation\tdigestChanged\tunitChanged\tdetail"
  val TestsHeader = "#suite\ttest\tstatus"

  /** Anchor a failing test. Frames are walked TOP-DOWN, so the deepest ported frame wins — the one
    * that actually threw. A `main`-scope frame is preferred over a `test`-scope one even when the
    * test frame is deeper, because a stack that reaches the library at all makes the library the
    * subject; the test frame is only ever the fallback for an assertion that never got there. */
  def locateTests(
      outs: List[Outcome],
      idx: SrcMap.Index,
      expected: List[Expected] = Nil,
      changedMembers: Set[String] = Set.empty,
  ): List[LocatedTest] =
    val changedPerUnit = changedMembers.groupBy(_.takeWhile(_ != '\t')).view.mapValues(_.size).toMap
    outs.map { o =>
      val ported = o.frames.flatMap(f => idx.resolveFrame(f.cls, f.line).map(f -> _))
      val main   = ported.find(_._2.scope == "main")
      val (anchor, entry) =
        if o.status != "fail" then ("none", scala.None)
        else
          main.map(x => ("main-frame", Some(x._2)))
            .orElse(ported.headOption.map(x => ("test-frame", Some(x._2))))
            .orElse(AssertAt.findFirstMatchIn(o.detail).flatMap(m =>
              idx.resolveFile(m.group(1), m.group(2).toIntOption.getOrElse(0)).map(x => ("assert-site", Some(x)))))
            .orElse(idx.unitEntry(o.suite).map(x => ("suite", Some(x))))
            .getOrElse(("none", scala.None))
      LocatedTest(o, anchor, entry, ported, expected.find(_.matches(o)),
                  entry.exists(x => changedMembers(s"${x.unit}\t${x.member}")),
                  entry.flatMap(x => changedPerUnit.get(x.unit)).getOrElse(0))
    }

  // ---------------------------------------------------------------------------
  // pass/fail diff — §5.3's classification, applied to behaviour
  // ---------------------------------------------------------------------------

  final case class TestDiff(
      hasBaseline: Boolean,
      newlyFailing: List[LocatedTest],
      newlyPassing: List[LocatedTest],
      stillFailing: List[LocatedTest],
      expectedFailing: List[LocatedTest],
      expectedButPassing: List[Expected],
      disappeared: List[String],
      added: List[String],
  ):
    /** the gate: an UNEXPECTED newly-failing test. An expected failure is not a regression, and a
      * test that vanished is reported but does not fail the gate — deleting a test is a decision. */
    def regressed: Boolean = newlyFailing.nonEmpty

  def parseTestsTsv(p: Path): Map[String, String] =
    if !Files.isRegularFile(p) then Map.empty
    else
      Files.readAllLines(p).toArray(Array.empty[String]).toList.flatMap { l =>
        if l.startsWith("#") || l.isBlank then scala.None
        else
          l.split('\t') match
            case Array(s, t, st) => Some(s"$s\t$t" -> st)
            case _               => scala.None
      }.toMap

  def diffTests(baseline: Map[String, String], latest: List[LocatedTest]): TestDiff =
    val byId    = latest.map(t => t.outcome.id -> t).toMap
    val fails   = latest.filter(_.outcome.status == "fail")
    val unex    = fails.filter(_.expected.isEmpty)
    val wasFail = (id: String) => baseline.get(id).contains("fail")
    val known   = baseline.nonEmpty
    TestDiff(
      hasBaseline        = known,
      newlyFailing       = unex.filter(t => !known || !wasFail(t.outcome.id)).sortBy(_.outcome.id),
      newlyPassing       = latest.filter(t => t.outcome.status == "pass" && wasFail(t.outcome.id)).sortBy(_.outcome.id),
      stillFailing       = unex.filter(t => known && wasFail(t.outcome.id)).sortBy(_.outcome.id),
      expectedFailing    = fails.filter(_.expected.isDefined).sortBy(_.outcome.id),
      expectedButPassing = latest.filter(t => t.outcome.status == "pass").flatMap(_.expected).distinct.sortBy(e => (e.suite, e.test)),
      disappeared        = baseline.keys.filterNot(byId.contains).toList.sorted,
      added              = byId.keys.filterNot(baseline.contains).toList.sorted,
    )

  // ---------------------------------------------------------------------------
  // rendering
  // ---------------------------------------------------------------------------

  private def where(t: LocatedTest): String =
    t.entry.map(e => s"${e.member}  [${e.javaAt}]").getOrElse("(not located)")

  def renderErrors(ls: List[LocatedError], limit: Int = 25): String =
    val sb = new StringBuilder
    val byLane = Lane.values.map(l => l -> ls.filter(_.lane == l)).toList
    sb.append(s"scalac errors: ${ls.size}")
    byLane.foreach((l, xs) => sb.append(s"  $l=${xs.size}"))
    sb.append('\n')
    byLane.foreach { (l, xs) =>
      if xs.nonEmpty then
        sb.append(s"\n-- $l — ${laneNote(l)}\n")
        xs.take(limit).foreach { x =>
          val at = x.entry.map(e => s"${e.member}  [${e.javaAt}]").getOrElse(x.err.path)
          sb.append(s"   ${x.err.code} ${x.err.kind}: $at\n        ${x.err.message}\n")
        }
        if xs.sizeIs > limit then sb.append(s"   … ${xs.size - limit} more (see errors.tsv)\n")
    }
    sb.result()

  private def laneNote(l: Lane): String = l match
    case Lane.Approx    => "at a region the engine marked approximate: expected, remediation attached"
    case Lane.EngineGap => "(a) engine gap — located to the member and the Java it came from"
    case Lane.Unmapped  => "outside the source map (injected Scala, shims, dependencies) — NOT an engine gap"

  def renderTests(all: List[LocatedTest], d: TestDiff, limit: Int = 25): String =
    val sb    = new StringBuilder
    val pass  = all.count(_.outcome.status == "pass")
    val fail  = all.count(_.outcome.status == "fail")
    sb.append(s"tests: ${all.size}  passing=$pass  failing=$fail  " +
              s"(expected ${d.expectedFailing.size}, unexpected ${fail - d.expectedFailing.size})\n")
    if !d.hasBaseline then
      sb.append("NO TEST BASELINE — these are the current results, nothing to compare against.\n")
    def block(title: String, xs: List[LocatedTest], note: String = ""): Unit =
      if xs.nonEmpty then
        sb.append(s"\n-- $title (${xs.size})${if note.isEmpty then "" else " — " + note}\n")
        xs.take(limit).foreach { t =>
          sb.append(s"   ${t.outcome.suite}.${t.outcome.name}\n")
          val moved =
            if t.digestChanged then "  THIS MEMBER'S EMITTED TEXT CHANGED SINCE THE BASELINE"
            else if t.unitChanged > 0 then s"  (${t.unitChanged} member(s) of its unit changed since the baseline)"
            else ""
          sb.append(s"        anchor=${t.anchor}$moved\n")
          sb.append(s"        at ${where(t)}\n")
          if t.outcome.detail.nonEmpty then sb.append(s"        ${t.outcome.detail.take(200)}\n")
          t.portedFrames.take(6).foreach((f, e) =>
            sb.append(s"          · ${e.scope}  ${e.member}  [${e.javaAt}]  (${f.file}:${f.line})\n"))
        }
        if xs.sizeIs > limit then sb.append(s"   … ${xs.size - limit} more (see test-failures.tsv)\n")
    block("NEWLY FAILING", d.newlyFailing,
          "the highest-value signal this engine produces — a DIGEST CHANGED line names the member that moved")
    block("still failing", d.stillFailing)
    block("newly passing", d.newlyPassing)
    block("failing AS EXPECTED", d.expectedFailing, "declared in baseline/expected-failures.tsv")
    if d.expectedButPassing.nonEmpty then
      sb.append(s"\n-- expected to fail but PASSED (${d.expectedButPassing.size}) — update expected-failures.tsv\n")
      d.expectedButPassing.foreach(e => sb.append(s"   ${e.suite}.${e.test}\n"))
    if d.disappeared.nonEmpty then
      sb.append(s"\n-- tests in the baseline that DID NOT RUN (${d.disappeared.size}) — a suite that stopped running is not a suite that passed\n")
      d.disappeared.take(limit).foreach(x => sb.append(s"   ${x.replace('\t', '.')}\n"))
    if d.added.nonEmpty then sb.append(s"\n-- new tests since the baseline: ${d.added.size}\n")
    sb.result()

  // ---------------------------------------------------------------------------
  // persistence
  // ---------------------------------------------------------------------------

  def writeErrors(out: Path, ls: List[LocatedError]): Unit =
    Files.createDirectories(out)
    val sorted = ls.sortBy(l => (l.lane.ordinal, l.entry.map(_.unit).getOrElse(""), l.err.path, l.err.line))
    Files.writeString(out.resolve("errors.tsv"), (ErrorsHeader :: sorted.map(_.tsv)).mkString("", "\n", "\n"))

  def writeTests(out: Path, ls: List[LocatedTest]): Unit =
    Files.createDirectories(out)
    val sorted = ls.sortBy(t => (t.outcome.suite, t.outcome.name))
    // tests.tsv is the PROMOTABLE artifact: three columns, no lines, no paths, so a baseline
    // survives every reshuffle of the emitted output and moves only when behaviour moves.
    Files.writeString(out.resolve("tests.tsv"),
      (TestsHeader :: sorted.map(t => s"${t.outcome.suite}\t${t.outcome.name}\t${t.outcome.status}")).mkString("", "\n", "\n"))
    Files.writeString(out.resolve("test-failures.tsv"),
      (FailuresHeader :: sorted.filter(_.outcome.status != "pass").map(_.tsv)).mkString("", "\n", "\n"))

  /** members whose emitted text differs from the baseline, as `unit\tmember` keys. */
  def changedMembers(baseline: Map[String, String], latest: Map[String, String]): Set[String] =
    (baseline.keySet ++ latest.keySet).filter(k => baseline.get(k) != latest.get(k))
