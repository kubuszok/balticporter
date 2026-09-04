package balticporter.tir

import java.nio.file.{Files, Path}

/** Attribute a COMPILER ERROR or a TEST FAILURE over emitted Scala back to the member that
  * produced it and the Java it came from (DESIGN.md §6.3, CLAUDE.md §4.4). TWO LANES: scalac
  * errors and test-runner output, both joined to (member, java origin), diffed run-over-run. A
  * failing test anchors on the first STACK FRAME in ported code. EXPECTED FAILURES ARE DERIVED
  * FIRST from `dropped-types.tsv`; `expected-failures.tsv` is the explicit fallback, kept APART. */
object Correlate:

  // ===========================================================================
  // scalac
  // ===========================================================================

  final case class ScalacError(code: String, kind: String, path: String, line: Int, col: Int, message: String)

  private val ErrHeader = raw"^-- (?:\[(E\d+)\] )?(.*?)Error(?:: (.+?):(\d+):(\d+))?\s*-*$$".r
  private val PipeLine  = raw"^\s*\|(.*)$$".r

  /** Parse a dotty/scala-cli compile log. Both the coded and bare header forms are recognised —
    * counting only coded errors silently undercounts. */
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
  /** MUnit's THIRD terminal marker, `==> s <suite>.<name> skipped 0.0s` — a suite abandoned after
    * a fatal Error takes its remaining tests with it (§3's silent-omission shape). Needs its own
    * pattern: widening FailLine's lazy `(.+?)` would swallow "skipped" into the name. Matched
    * BEFORE the other two. */
  private val SkipLine  = raw"^==> s (\S+) skipped\s+[0-9]+(?:\.[0-9]+)?s\s*$$".r
  private val FrameLine = raw"^\s+at ([^(\s]+)\(([^:)]+)(?::(\d+))?\)\s*$$".r
  private val AssertAt  = raw"([^\s:]+\.scala):(\d+)".r

  /** `X` failed, `s` never ran, anything else (`i`/`I`) was ignored on purpose. `skipped` is kept
    * apart from `ignored`: an ignored test is a DECISION, a skipped one is PREVENTION. */
  private def marked(suite: String, mark: String, full: String, detail: String): Outcome =
    val name = if suite.nonEmpty && full.startsWith(suite + ".") then full.substring(suite.length + 1)
               else full.reverse.takeWhile(_ != '.').reverse
    val status = mark match
      case "X" => "fail"
      case "s" => "skipped"
      case _   => "ignored"
    Outcome(if suite.nonEmpty then suite else full.stripSuffix("." + name), name,
            status, detail.trim, Nil)

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
        case SkipLine(full)           => flush(); out += marked(suite, "s", full, "")
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
  // expected failures — derived from the port's own drops, never from a name in here
  // ===========================================================================

  /** @param derived true when computed from `Substitutions.dropTypes` rather than the hand-written
    *                escape hatch — a declared entry is a CLAIM, a derived one a consequence.
    * @param frame   the PORTED CLASS this failure is expected AT (normally main-frame). See
    *                [[anchorHolds]]. */
  final case class Expected(suite: String, test: String, reason: String, derived: Boolean = false,
                            frame: Option[String] = scala.None):
    def matches(o: Outcome): Boolean = o.suite == suite && (test == "*" || test == o.name)
    def source: String = if derived then "derived" else "declared"

    /** DOES THE REASON STILL HOLD? — the half `(suite, test)` cannot answer. A declared entry is
      * keyed on the test's NAME, so a NEW failure with a different cause in the same test is
      * absorbed silently. A row may carry the ANCHOR the correlator already computes; where it
      * stops matching, the failure counts UNEXPECTED. OPTIONAL for compatibility with rows
      * predating the column. */
    def anchorHolds(anchoredUnit: Option[String]): Boolean =
      frame.isEmpty || frame == anchoredUnit

  val ExpectedHeader = "#suite\ttest\treason\t[frame=<ported class>]"

  /** the anchor column's TAG — tagged rather than positional because `reason` absorbs every
    * trailing field, and `k=v` is the grammar a porter note already writes decisions in (§4.575). */
  private val FrameTag = "frame="

  /** the DECLARED escape hatch. Normally empty: a failure a drop explains needs no entry here. */
  def parseExpected(p: Path): List[Expected] =
    if !Files.isRegularFile(p) then Nil
    else
      Files.readAllLines(p).toArray(Array.empty[String]).toList.flatMap { l =>
        if l.startsWith("#") || l.isBlank then scala.None
        else
          l.split('\t').toList match
            case s :: t :: rest =>
              val (tagged, prose) = rest.map(_.trim).partition(_.startsWith(FrameTag))
              Some(Expected(s.trim, t.trim, prose.mkString(" ").trim,
                            frame = tagged.headOption.map(_.drop(FrameTag.length).trim).filter(_.nonEmpty)))
            case _ => scala.None
      }

  /** One `Substitutions.dropTypes` entry, IN BOTH NAMESPACES: a port's policy is written UPSTREAM
    * and its package rename runs LAST (§4.56), so recording only one namespace left this rule dead
    * on every renaming port. Both names are written by the run that knows the map.
    * @param upstream the FQN as the manifest declares it.
    * @param emitted  the FQN the port emits it under — what a stack frame and a compiler report. */
  final case class Dropped(upstream: String, emitted: String):
    def tsv: String = s"$upstream\t$emitted"
    /** how to name it to a human: the manifest key, plus the emitted name when they differ. */
    def render: String = if upstream == emitted then upstream else s"$upstream (emitted as $emitted)"

  object Dropped:
    /** a port with no package rename: the two namespaces coincide. */
    def apply(fqn: String): Dropped = Dropped(fqn, fqn)

  val DroppedHeader = "#upstream\temitted"

  /** The FQNs a port declares in `Substitutions.dropTypes`, as `PortRun` wrote them: `upstream`
    * TAB `emitted`. A single-column line means the two namespaces coincide. */
  def parseDropped(p: Path): Set[Dropped] =
    if !Files.isRegularFile(p) then Set.empty
    else
      Files.readAllLines(p).toArray(Array.empty[String]).toList
        .filterNot(l => l.startsWith("#") || l.isBlank)
        .flatMap { l =>
          l.split('\t').map(_.trim).filter(_.nonEmpty) match
            case Array(u)        => Some(Dropped(u, u))
            case Array(u, e, _*) => Some(Dropped(u, e))
            case _               => scala.None
        }.toSet

  /** `.` separates packages and the top-level type, `$` precedes a nested type or a companion, `#`
    * a member — the three separators `Symbol.fullName` and a JVM class name are cut at. */
  private def isBoundary(c: Char): Boolean = c == '.' || c == '$' || c == '#'

  /** Does the runtime class `cls` name `fqn` itself, or something NESTED inside it? Cut only at a
    * separator, never a bare prefix (§4.56). */
  private[tir] def covers(fqn: String, cls: String): Boolean =
    fqn.nonEmpty && cls.startsWith(fqn) &&
      (cls.length == fqn.length || isBoundary(cls.charAt(fqn.length)))

  /** Is this failure explained BY CONSTRUCTION — does its stack reach a type the port deliberately
    * does not have? The whole stack is consulted (a drop shows up below the anchor's member), and
    * the FIRST dropped type encountered is reported. Matched against RAW frames, never source-mapped
    * ones: a dropped type is the one type the port does NOT emit, so it has no source-map entry —
    * its replacement is INJECTED Scala the emitter never saw. */
  def derivedExpectation(t: Outcome, dropped: Set[Dropped]): Option[Expected] =
    if dropped.isEmpty || t.status != "fail" then scala.None
    else
      t.frames.iterator
        .flatMap(f => dropped.find(d => covers(d.emitted, f.cls)))
        .nextOption()
        .map { d =>
          Expected(t.suite, t.name,
            s"Substitutions.dropTypes ${d.render} — the port deliberately does not translate this " +
              "type, and this failure's stack reaches it", derived = true)
        }

  // ===========================================================================
  // the join
  // ===========================================================================

  /** which of DESIGN.md §6.3's lanes a diagnostic fell into. */
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
    /** the engine DECLARED this one: a `scala.compiletime.error` the emitter wrote itself under
      * `PortRun(preview = true)`. Its own lane, classified BEFORE the source-map lookup, since it
      * is the opposite of a finding — counted with engine gaps it would drown them. */
    case Declared

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
      // DECLARED first, by the MESSAGE the engine itself wrote — the lane must not depend on
      // whether the map happened to cover the file.
      val declared = e.message.contains(DeclaredMarker)
      idx.resolveFile(e.path, e.line) match
        case Some(x) if declared                           => LocatedError(e, Some(x), Lane.Declared)
        case Some(x) if markers(s"${x.unit}\t${x.member}") => LocatedError(e, Some(x), Lane.Approx)
        case Some(x)                                       => LocatedError(e, Some(x), Lane.EngineGap)
        case scala.None if declared                        => LocatedError(e, scala.None, Lane.Declared)
        case scala.None                                    => LocatedError(e, scala.None, Lane.Unmapped)
    }

  /** the prefix `TirEmitter.unrenderable` puts on every `scala.compiletime.error` it writes. */
  val DeclaredMarker = "balticporter: "

  final case class LocatedTest(
      outcome: Outcome,
      anchor: String,                      // main-frame | test-frame | assert-site | suite | none
      entry: Option[SrcMap.Entry],
      portedFrames: List[(Frame, SrcMap.Entry)],
      expected: Option[Expected],
      digestChanged: Boolean,
      /** a DECLARED entry matching this test by name whose ANCHOR no longer does. Reported apart,
        * NOT put in [[expected]] — the failure counts unexpected. Always empty for a derived
        * expectation, which has no claim to go stale. */
      staleExpectation: Option[Expected] = scala.None,
      /** how many members of the ANCHORED UNIT changed since the baseline — the unit count catches
        * a class-initialiser cycle triggered by a sibling member the per-member flag misses. */
      unitChanged: Int = 0,
  ):
    def tsv: String =
      val e = entry
      s"${outcome.suite}\t${outcome.name}\t${outcome.status}\t$anchor\t${e.map(_.unit).getOrElse("")}\t" +
      s"${e.map(_.member).getOrElse("")}\t${e.map(_.javaPath).getOrElse("")}\t${e.map(_.javaLine).getOrElse(0)}\t" +
      s"${expected.map(x => s"expected#${x.source}")
           .orElse(staleExpectation.map(_ => "unexpected#stale-declaration"))
           .getOrElse("unexpected")}\t${digestChanged}\t$unitChanged\t${outcome.detail}"

  val FailuresHeader =
    "#suite\ttest\tstatus\tanchor\tunit\tmember\tjavaPath\tjavaLine\texpectation\tdigestChanged\tunitChanged\tdetail"
  val TestsHeader = "#suite\ttest\tstatus"

  /** Anchor a failing test. Frames are walked TOP-DOWN, so the deepest ported frame wins. A
    * `main`-scope frame is preferred over `test`-scope even when deeper — a stack reaching the
    * library at all makes the library the subject. */
  def locateTests(
      outs: List[Outcome],
      idx: SrcMap.Index,
      expected: List[Expected] = Nil,
      changedMembers: Set[String] = Set.empty,
      droppedTypes: Set[Dropped] = Set.empty,
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
      // DERIVED first: a drop is a fact about the manifest, a declaration a claim about it.
      // A DECLARED entry is admitted only while its ANCHOR still holds (Expected.anchorHolds).
      val declared = expected.find(_.matches(o))
      val holds    = declared.filter(_.anchorHolds(entry.map(_.unit)))
      val why      = derivedExpectation(o, droppedTypes).orElse(holds)
      LocatedTest(o, anchor, entry, ported, why,
                  entry.exists(x => changedMembers(s"${x.unit}\t${x.member}")),
                  // reported only where nothing else explains the failure.
                  staleExpectation = if why.isDefined then scala.None else declared,
                  unitChanged = entry.flatMap(x => changedPerUnit.get(x.unit)).getOrElse(0))
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
      /** a test the runner SKIPPED that the baseline does not record as skipped. See [[regressed]]. */
      newlySkipped: List[LocatedTest] = Nil,
      /** a DECLARED expectation whose ANCHOR no longer holds. Reported apart from
        * [[expectedButPassing]] since the next action differs (delete the row vs. read the new
        * failure). Not its own gate: the failure is already in [[newlyFailing]]/[[stillFailing]]. */
      staleExpectations: List[LocatedTest] = Nil,
  ):
    /** the gate: an UNEXPECTED newly-failing test, or a test that stopped RUNNING — a SKIP (still
      * counted, no assertion produced) or a DISAPPEARANCE (a conversion regression that stops
      * emitting a suite removes its tests from both sides, reporting success on a smaller suite).
      * A deliberate deletion is ACKNOWLEDGED by re-accepting the baseline. */
    def regressed: Boolean = newlyFailing.nonEmpty || newlySkipped.nonEmpty || disappeared.nonEmpty

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
      // A test the baseline does not ALREADY record as skipped.
      newlySkipped       = latest.filter(t => t.outcome.status == "skipped" &&
                                              !baseline.get(t.outcome.id).contains("skipped"))
                                 .sortBy(_.outcome.id),
      staleExpectations  = latest.filter(_.staleExpectation.isDefined).sortBy(_.outcome.id),
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
    case Lane.Declared  => "DECLARED by the port under `preview = true`: the engine had no faithful " +
      "Scala here and said so in the output. Each message carries the construct, the reason, the " +
      "action and the java origin; none of these is a compiler finding about the port"

  def renderTests(all: List[LocatedTest], d: TestDiff, limit: Int = 25): String =
    val sb    = new StringBuilder
    val pass  = all.count(_.outcome.status == "pass")
    val fail  = all.count(_.outcome.status == "fail")
    // NOT folded into either count: a skipped test asserted nothing.
    val skip  = all.count(_.outcome.status == "skipped")
    sb.append(s"tests: ${all.size}  passing=$pass  failing=$fail  " +
              s"(expected ${d.expectedFailing.size}, unexpected ${fail - d.expectedFailing.size})" +
              (if skip > 0 then s"  SKIPPED=$skip — these never ran" else "") + "\n")
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
          t.expected.foreach(e => sb.append(s"        ${e.source}: ${e.reason}\n"))
          sb.append(s"        at ${where(t)}\n")
          if t.outcome.detail.nonEmpty then sb.append(s"        ${t.outcome.detail.take(200)}\n")
          t.portedFrames.take(6).foreach((f, e) =>
            sb.append(s"          · ${e.scope}  ${e.member}  [${e.javaAt}]  (${f.file}:${f.line})\n"))
        }
        if xs.sizeIs > limit then sb.append(s"   … ${xs.size - limit} more (see test-failures.tsv)\n")
    block("NEWLY FAILING", d.newlyFailing,
          "the highest-value signal this engine produces — a DIGEST CHANGED line names the member that moved")
    block("NEWLY SKIPPED", d.newlySkipped,
          "the runner did NOT run these — a skip moves no pass count and no fail count, so it is " +
          "gated on its own. Usually the tail of a suite abandoned after a fatal error")
    block("still failing", d.stillFailing)
    block("newly passing", d.newlyPassing)
    block("failing AS EXPECTED", d.expectedFailing,
          s"${d.expectedFailing.count(_.expected.exists(_.derived))} DERIVED from the port's " +
          s"Substitutions.dropTypes, ${d.expectedFailing.count(_.expected.exists(!_.derived))} declared " +
          "in baseline/expected-failures.tsv")
    if d.expectedButPassing.nonEmpty then
      sb.append(s"\n-- expected to fail but PASSED (${d.expectedButPassing.size}) — remove from expected-failures.tsv\n")
      d.expectedButPassing.foreach(e => sb.append(s"   ${e.suite}.${e.test}\n"))
    if d.staleExpectations.nonEmpty then
      sb.append(s"\n-- EXPECTED FOR A REASON THAT NO LONGER HOLDS (${d.staleExpectations.size}) — the " +
        "declared row still names this test and its ANCHOR has moved, so what fails is not what the " +
        "row is about. The failure counts UNEXPECTED above; read it, then re-anchor the row or delete it\n")
      d.staleExpectations.take(limit).foreach { t =>
        t.staleExpectation.foreach(e =>
          sb.append(s"   ${e.suite}.${e.test}\n" +
            s"        declared frame=${e.frame.getOrElse("?")}, now anchored at " +
            s"${t.entry.map(_.unit).getOrElse("(not located)")} (${t.anchor})\n" +
            s"        the claim: ${e.reason.take(160)}\n"))
      }
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
    // tests.tsv is the PROMOTABLE artifact: three columns, so a baseline moves only when behaviour does.
    Files.writeString(out.resolve("tests.tsv"),
      (TestsHeader :: sorted.map(t => s"${t.outcome.suite}\t${t.outcome.name}\t${t.outcome.status}")).mkString("", "\n", "\n"))
    Files.writeString(out.resolve("test-failures.tsv"),
      (FailuresHeader :: sorted.filter(_.outcome.status != "pass").map(_.tsv)).mkString("", "\n", "\n"))

  /** members whose emitted text differs from the baseline, as `unit\tmember` keys. */
  def changedMembers(baseline: Map[String, String], latest: Map[String, String]): Set[String] =
    (baseline.keySet ++ latest.keySet).filter(k => baseline.get(k) != latest.get(k))
