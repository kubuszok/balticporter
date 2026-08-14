package balticporter.tir

import java.nio.file.{Files, Path}

/** Attribute a COMPILER ERROR or a TEST FAILURE over emitted Scala back to the member that
  * produced it and to the Java it came from. DESIGN.md §6.3 plus the
  * amendment CLAUDE.md §4.4 forces on it: ten Java forms translate to VALID Scala meaning something
  * else and move no compile-error count, so the same join has to run over the TEST runner's output.
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
  * ## Expected failures are DERIVED first, DECLARED only as a fallback
  *
  * A port that deliberately substitutes a type ships tests that must fail. That is not a list to
  * maintain — it is a CONSEQUENCE of the manifest: a test whose failure stack reaches a type in
  * the port's `Substitutions.dropTypes` fails because the port deliberately does not have that
  * type. `PortRun` writes those FQNs to `dropped-types.tsv` beside the source map on every run,
  * and [[locateTests]] classifies from them. Nothing in this file names a library — `core` may not
  * know one exists (CLAUDE.md §1) — and nothing has to be kept in step by hand.
  *
  * The artifact carries BOTH namespaces per drop ([[Dropped]]), and that is the whole of what made
  * the rule work: policy is written upstream and the package rename runs last (§4.56), so a
  * one-column file of manifest FQNs was being compared against stack frames in the emitted
  * namespace and matched nothing, on every renaming port, since the rule was written.
  *
  * A hand-maintained list is exactly the thing that rots into "we always ignore those four" and
  * then hides a fifth, so the DECLARED form (`expected-failures.tsv` in the BASELINE directory:
  * `suite`, `test` — `*` for the whole suite — and a reason) survives only as the explicit escape
  * hatch for a failure no drop explains. The two are kept APART rather than merged:
  * [[Expected.derived]] says which one classified a test, so a declared entry can never be
  * mistaken for a fact about the manifest, and `#derived` in the artifact is greppable.
  *
  * Either way an expected failure is reported as expected and is not a regression; a DECLARED
  * expected failure that PASSES is reported too, because a substitution that started working is
  * news. (A derived one cannot: a passing test has no failure stack, so it simply stops being
  * expected — which is the same news, arrived at without a file to edit.)
  *
  * ## …and a DECLARED entry may be keyed on the ANCHOR as well as on the name
  *
  * The rot the two forms are kept apart to avoid has a second face, and `(suite, test)` cannot see
  * it: a NEW failure with a DIFFERENT cause, in the same test, is silently absorbed. The row still
  * matches, the artifact still reads `expected#declared`, no pass count moves, and the sentence
  * somebody wrote in the `reason` column is now about something that is not happening — "we always
  * ignore that one" arrived at from the other direction. The derived form does not have this,
  * because a drop is re-checked against the stack on every run.
  *
  * So a declared row may carry `frame=<ported class>` — the `unit` the correlator anchors the
  * failure at, which is the `main-frame` class in the ordinary case and is already in
  * `test-failures.tsv` for a reader to copy. While it holds, the row classifies as before; where it
  * stops holding the failure counts UNEXPECTED and the entry is reported as a reason that no longer
  * holds ([[TestDiff.staleExpectations]]) — apart from `expectedButPassing`, because the next
  * action is different: that one says delete the row, this one says read the new failure first.
  *
  * The column is OPTIONAL, and that is compatibility rather than preference: these files predate
  * it, and a run that read an absent anchor as "does not match" would turn every port's escape
  * hatch into a wall of unexpected failures on the commit that shipped this. A row without it keeps
  * exactly today's behaviour. A NEW row should carry one — a claim that can go stale without saying
  * so is the thing this whole section is about.
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
    * silently undercounts — the same trap the `Justfile`'s `gdx-measure` lane documents. */
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
  /** MUnit's THIRD terminal marker, and the one that was silently dropped: `==> s <suite>.<name>
    * skipped 0.0s`. A skip is what MUnit prints when a test never ran — a suite abandoned after a
    * fatal Error takes its remaining tests with it — so the run loses tests AND reports success,
    * which is the exact silent-omission shape this project keeps finding (CLAUDE.md §3).
    *
    * It needs its own pattern rather than widening `FailLine` to `[XiIs]`: the line ends
    * `… skipped 0.0s`, so that regex's lazy `(.+?)` would swallow the word and record the test
    * under the name `<name> skipped` — present in the artifact, matching nothing in the baseline,
    * and reported as one test that vanished plus one that appeared. Matched BEFORE the other two. */
  private val SkipLine  = raw"^==> s (\S+) skipped\s+[0-9]+(?:\.[0-9]+)?s\s*$$".r
  private val FrameLine = raw"^\s+at ([^(\s]+)\(([^:)]+)(?::(\d+))?\)\s*$$".r
  private val AssertAt  = raw"([^\s:]+\.scala):(\d+)".r

  /** `X` failed, `s` never ran, anything else (`i`/`I`) was ignored on purpose.
    *
    * `skipped` is kept apart from `ignored` because they are opposite kinds of fact: an ignored
    * test is a DECISION somebody wrote down (`@Ignore`), a skipped one is PREVENTION — the runner
    * wanted to run it and could not. Merging them files the second under the first and buries it. */
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

  /** @param derived true when this was computed from the port's `Substitutions.dropTypes` rather
    *                than read from the hand-written escape hatch. The distinction is kept because
    *                a declared entry is a CLAIM and a derived one is a consequence, and a reader
    *                who cannot tell them apart is back to "we always ignore those four".
    * @param frame   the PORTED CLASS this failure is expected AT — the `unit` the correlator
    *                anchors it on, and normally the `main-frame` one. See [[anchorHolds]]. */
  final case class Expected(suite: String, test: String, reason: String, derived: Boolean = false,
                            frame: Option[String] = scala.None):
    def matches(o: Outcome): Boolean = o.suite == suite && (test == "*" || test == o.name)
    def source: String = if derived then "derived" else "declared"

    /** DOES THE REASON STILL HOLD? — the half `(suite, test)` cannot answer.
      *
      * A declared entry is a CLAIM about WHY one test fails, and it is keyed on the test's NAME. So
      * a NEW failure with a different cause, in the same test, is absorbed by it silently: the row
      * still matches, the artifact still says `expected#declared`, the pass count does not move and
      * nothing anywhere reports that the sentence in the `reason` column is now about something
      * that is not happening. That is exactly the rot a hand-maintained list is kept apart from the
      * derived set to avoid, and the escape hatch had it.
      *
      * So a row may carry the ANCHOR the correlator already computes, and the claim then holds only
      * while the failure is still at that class. Where it stops matching the failure counts
      * UNEXPECTED and the entry is reported as a reason that no longer holds — which is the loud
      * direction, because the alternative is a green lane over a defect nobody has read.
      *
      * OPTIONAL, and that is a compatibility statement rather than a preference: this file predates
      * the column, an existing row carries no anchor, and a run that treated an absent one as "does
      * not match" would turn every port's escape hatch into a wall of unexpected failures on the
      * commit that shipped this. A row without it keeps today's behaviour exactly; a row that
      * carries one is the stronger claim, and new rows should. */
    def anchorHolds(anchoredUnit: Option[String]): Boolean =
      frame.isEmpty || frame == anchoredUnit

  val ExpectedHeader = "#suite\ttest\treason\t[frame=<ported class>]"

  /** the anchor column's TAG. The column is tagged rather than positional because the `reason` has
    * always absorbed every trailing field — a positional fourth would be read as reason text by one
    * side and as an anchor by the other, depending on whether a reason happens to hold a tab — and
    * because `k=v` is the grammar a porter note already writes decisions in (§4.575). */
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

  /** One `Substitutions.dropTypes` entry, IN BOTH NAMESPACES.
    *
    * A port's policy is written in the UPSTREAM namespace and its package rename runs LAST
    * (CLAUDE.md §4.56), so `com.badlogic.gdx.utils.Json` is dropped and `sge.utils.Json` is what a
    * stack frame says. Recording only one of the two is what made this whole rule dead code: the
    * artifact held upstream FQNs, every frame was emitted, and the classifier had NEVER fired on a
    * renaming port — the claim "4 deliberate failures" lived in prose only. Both names are written
    * by the run that knows the map, so nothing downstream has to guess at a rename it cannot see.
    *
    * @param upstream the FQN as the manifest declares it — what a reader must go and edit.
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
    * TAB `emitted`. `#` comments and blanks ignored, so the file reads like every other artifact
    * here. A single-column line — the form this file had before renames were carried, and the form
    * a port with no rename could still write — means the two namespaces coincide. */
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
    * separator, never a bare prefix (CLAUDE.md §4.56): `p.Json` covers `p.Json`, `p.Json$`,
    * `p.Json$Ref` and `p.Json$$anonfun$3`, and must never cover `p.JsonTest`. */
  private[tir] def covers(fqn: String, cls: String): Boolean =
    fqn.nonEmpty && cls.startsWith(fqn) &&
      (cls.length == fqn.length || isBoundary(cls.charAt(fqn.length)))

  /** Is this failure explained BY CONSTRUCTION — does its stack reach a type the port deliberately
    * does not have?
    *
    * The whole stack is consulted, not only the anchor: a drop shows up wherever the replacement
    * threw, which is routinely one frame below the member the anchor names. The FIRST dropped type
    * encountered (frames are already ordered outermost-throw-first) is the one reported, so the
    * reason names the type that actually explains the failure.
    *
    * ==Why the RAW frames and not the source-mapped ones==
    *
    * This used to read `SrcMap.Entry.unit` off the frames that resolved. That cannot work for the
    * case it exists for: a dropped type is precisely the one type the port does NOT emit, so it has
    * no honest source-map entry to resolve to — its replacement is INJECTED Scala, which the
    * emitter never saw. The class name in the frame is the only place the dropped type appears at
    * all, so that is what is matched, against [[Dropped.emitted]] because a frame is emitted. */
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
      * `PortRun(preview = true)` because it had no faithful Scala for the construct.
      *
      * Its own lane, and classified BEFORE the source-map lookup, because it is the opposite of a
      * finding: the port is saying what it could not do, at the place it could not do it. Counted
      * with the engine gaps it would drown them — a preview run of a new library is mostly these —
      * and counted as `Unmapped` it would read as "not our problem", which is exactly wrong. */
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
      // DECLARED first, and by the MESSAGE the engine itself wrote: the location is still wanted
      // (it is attached below where the map has one), but the lane must not depend on whether the
      // map happened to cover the file. Nothing but a `compiletime.error` this emitter emitted can
      // carry the marker, so the test cannot capture a real diagnostic.
      val declared = e.message.contains(DeclaredMarker)
      idx.resolveFile(e.path, e.line) match
        case Some(x) if declared                           => LocatedError(e, Some(x), Lane.Declared)
        case Some(x) if markers(s"${x.unit}\t${x.member}") => LocatedError(e, Some(x), Lane.Approx)
        case Some(x)                                       => LocatedError(e, Some(x), Lane.EngineGap)
        case scala.None if declared                        => LocatedError(e, scala.None, Lane.Declared)
        case scala.None                                    => LocatedError(e, scala.None, Lane.Unmapped)
    }

  /** the prefix `TirEmitter.unrenderable` puts on every `scala.compiletime.error` it writes. One
    * string, named here rather than spelled twice, because a lane keyed to a message that drifted
    * would silently empty itself. */
  val DeclaredMarker = "balticporter: "

  final case class LocatedTest(
      outcome: Outcome,
      anchor: String,                      // main-frame | test-frame | assert-site | suite | none
      entry: Option[SrcMap.Entry],
      portedFrames: List[(Frame, SrcMap.Entry)],
      expected: Option[Expected],
      digestChanged: Boolean,
      /** a DECLARED entry that matched this test by name and whose ANCHOR no longer does — so the
        * claim in its `reason` column is about a failure that is not the one happening. Reported
        * apart, and NOT put in [[expected]]: the failure counts unexpected, which is the loud
        * direction. Always empty for a derived expectation, which has no claim to go stale. */
      staleExpectation: Option[Expected] = scala.None,
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
      s"${expected.map(x => s"expected#${x.source}")
           .orElse(staleExpectation.map(_ => "unexpected#stale-declaration"))
           .getOrElse("unexpected")}\t${digestChanged}\t$unitChanged\t${outcome.detail}"

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
      // DERIVED first: a drop is a fact about the manifest, a declaration is somebody's claim about
      // it, and preferring the claim would let a stale line outrank the thing it describes.
      //
      // …and a DECLARED entry is admitted only while its ANCHOR still holds. Keyed on (suite, test)
      // alone the row absorbs a NEW failure with a DIFFERENT cause in the same test: it still
      // matches, the artifact still reads `expected#declared`, no count moves, and the sentence in
      // the `reason` column is now about something that is not happening. An entry that carries no
      // anchor is unaffected, which is the compatibility half (see `Expected.anchorHolds`).
      val declared = expected.find(_.matches(o))
      val holds    = declared.filter(_.anchorHolds(entry.map(_.unit)))
      val why      = derivedExpectation(o, droppedTypes).orElse(holds)
      LocatedTest(o, anchor, entry, ported, why,
                  entry.exists(x => changedMembers(s"${x.unit}\t${x.member}")),
                  // a stale declaration is only ever reported where nothing else explains the
                  // failure: a drop that explains it is a FACT about the manifest, and a claim
                  // beside it is neither wrong nor the reader's next step.
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
      /** a test the runner SKIPPED that the baseline does not record as skipped — it did not run,
        * and no pass/fail count can show that. See [[regressed]]. */
      newlySkipped: List[LocatedTest] = Nil,
      /** a DECLARED expectation whose ANCHOR no longer holds — the test still fails and the reason
        * on the row is about a different failure. Reported apart from [[expectedButPassing]]
        * because the next action differs: that one says DELETE the row, this one says READ the new
        * failure and then decide. Not its own gate: the failure itself is already in
        * [[newlyFailing]] or [[stillFailing]], since the entry no longer excuses it. */
      staleExpectations: List[LocatedTest] = Nil,
  ):
    /** the gate: an UNEXPECTED newly-failing test, or a test that stopped RUNNING — in either of
      * the two ways a test stops running.
      *
      * An expected failure is not a regression. The other two are, and they are the same defect
      * seen from two sides, neither of which moves a pass count or a fail count:
      *
      *  - a SKIP — the test is still there, the runner still counted it, and it produced no
      *    assertion. Without its own gate a suite abandoned mid-way reports success;
      *  - a DISAPPEARANCE — the row is not in the artifact at all. This was reported and NOT gated,
      *    on the grounds that deleting a test is a decision somebody made. That is true of a
      *    deletion and false of the failure this project actually has: a CONVERSION regression that
      *    stops EMITTING a suite removes its tests from both sides at once, so the run reports
      *    success on a smaller suite and the only number that moved is one nobody was holding.
      *
      * A deliberate deletion is ACKNOWLEDGED by re-accepting the baseline, exactly as a fallen
      * error count is (`scripts/_lib.sh error_baseline_guard`) — which turns "somebody decided
      * this" into a recorded fact instead of an assumption the gate has to make. */
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
      // A test the baseline does not ALREADY record as skipped. Absent from the baseline counts:
      // that is what a test looks like the first time the parser stops dropping its marker, and a
      // skip nobody has accepted is exactly the thing that must not pass silently.
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
    // NOT folded into either count: a skipped test asserted nothing, so calling it a pass is a lie
    // and calling it a failure is a different one. It gets its own number, always printed once any
    // exists, because "108 passing, 2 failing" over 112 tests reads as complete and is not.
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
    // tests.tsv is the PROMOTABLE artifact: three columns, no lines, no paths, so a baseline
    // survives every reshuffle of the emitted output and moves only when behaviour moves.
    Files.writeString(out.resolve("tests.tsv"),
      (TestsHeader :: sorted.map(t => s"${t.outcome.suite}\t${t.outcome.name}\t${t.outcome.status}")).mkString("", "\n", "\n"))
    Files.writeString(out.resolve("test-failures.tsv"),
      (FailuresHeader :: sorted.filter(_.outcome.status != "pass").map(_.tsv)).mkString("", "\n", "\n"))

  /** members whose emitted text differs from the baseline, as `unit\tmember` keys. */
  def changedMembers(baseline: Map[String, String], latest: Map[String, String]): Set[String] =
    (baseline.keySet ++ latest.keySet).filter(k => baseline.get(k) != latest.get(k))
