package balticporter.tir

/** Every translated JUMP that leaves a translated `try` through a handler broad enough to catch it,
  * and was not guarded.
  *
  * The §4.4 defect this counts: java's `break`/`continue` is a jump, so no `catch` can ever
  * intercept one — while scala's translation of it is `scala.util.boundary.Break`, which
  * `extends RuntimeException` (3.8.x library source; deliberately not a `ControlThrowable`, so
  * `NonFatal` matches it too). A `boundary.break` inside a `try` whose boundary is outside it is
  * therefore swallowed by any `catch (Exception|RuntimeException|Throwable)` in between: the loop
  * runs on, and the handler's body runs for a condition java never had. The emitter repairs it by
  * interposing a re-throw arm ahead of the java arms ([[balticporter.emit.TirEmitter.BreakGuard]]).
  *
  * Nothing else in the pipeline can see the failure — it compiles, it moves no error count, no
  * other check counts it, and only a behavioural test finds it (measured in the reference
  * ecosystem as ssg `ed8ce078`, a date filter that silently stopped parsing). That is precisely
  * CLAUDE.md §3's "every translation path gets a check at the same time it gets a translation".
  *
  * '''Why this is not the emitter's own answer read back.''' The crossings are found HERE, from the
  * trees, by walking with `StandardTraversal` and asking [[Jumps]] which construct each jump binds
  * to; what the emitter contributes is only the set of sites it actually guarded. So the two
  * disagree exactly when the emitter's boundary state missed a shape this walk can see — which is
  * the failure worth counting, and the one an emitter-owned tally could never report.
  */
object BreakCatchCheck:

  val Name = "break-catch"

  enum Issue:
    /** a jump crosses a Break-compatible catch and nothing re-throws it: the handler eats the jump. */
    case UnguardedJump

  object Issue:
    def classification(i: Issue): String = i match
      case UnguardedJump =>
        "§1(a) ENGINE: this is a universal java-vs-scala fact, never per-library policy. The " +
          "emitter interposes `case brkThru$: scala.util.boundary.Break[?] => throw brkThru$` " +
          "ahead of the java arms wherever a jump crosses a catch that could match it " +
          "(`TirEmitter.tryStr`/`crossesCatch`). A finding here means this walk sees a crossing " +
          "the emitter's boundary state did not — fix `crossesCatch`, not the port."

  /** @param jump  the java jump that crosses — `break`, `continue`, `break L`, `continue L`
    * @param caught the arm that would swallow it, as emitted */
  final case class Finding(issue: Issue, owner: String, jump: String, caught: String, origin: Origin):
    def detail: String =
      s"`$jump` leaves this try, and `catch ($caught)` matches scala.util.boundary.Break — " +
        "java's jump is not catchable, so the handler runs for a condition java never had"
    def render: String = s"$issue $owner: $jump vs catch($caught)  (${origin.javaPath}:${origin.line})"
    def report: CheckReport.Finding =
      CheckReport.Finding(Name, issue.toString, owner, CheckReport.relativise(origin.javaPath),
        origin.line, detail)

  /** The complete result. Pure — persisting it is the orchestrator's job.
    *
    * @param guarded which `try`s the emitter put a re-throw arm on
    *                ([[balticporter.emit.TirEmitter.breakGuards]], keyed by [[Tree.Try.id]]).
    *                `_ => false` is the un-repaired engine, which is how the negative test asks this
    *                check whether it can report at all.
    *
    *                Asked of the NODE and answered from its TOKEN, never from its `Origin`: a
    *                path/line/column triple is not unique across `try`s — a nested one-liner shares
    *                one, and every synthesised `try` carries `Origin.synthetic` — so an
    *                origin-keyed answer lets a GUARDED try vouch for an unguarded sibling and this
    *                check reports nothing for it. Object identity would not do either: this walk
    *                rebuilds every node, so nothing here is the object the emitter held.
    */
  def check(program: Program, units: List[Tree.ClassDef], guarded: Tree.Try => Boolean): List[Finding] =
    given Program = program
    units.flatMap(inUnit(_, guarded))

  private def inUnit(u: Tree.ClassDef, guarded: Tree.Try => Boolean)(using program: Program): List[Finding] =
    // ---- which member each `try` sits in, innermost first (the walk is bottom-up, so a nested
    // `def`'s tries are claimed before the enclosing one sees them).
    //
    // The ONE table still keyed by `Origin`, and harmlessly: what it decides is the OWNER NAME
    // printed in a finding, so two `try`s sharing an origin share a label and nothing else — while
    // every table that decides WHETHER to report is keyed below by the try's own token, where an
    // alias would silently drop a finding.
    val ownerOf = collection.mutable.Map.empty[Origin, String]
    val claim = (s: SymId, t: Option[Term]) =>
      t.foreach(x => tryOriginsIn(x).foreach(o => ownerOf.getOrElseUpdate(o, fqn(s))))
    val owners = new Phase:
      def name: String = "break-catch/owner"
      override def transformDefDef(d: Tree.DefDef)(using Program): Tree.DefDef = { claim(d.symbol, d.rhs); d }
      override def transformValDef(v: Tree.ValDef)(using Program): Tree.ValDef = { claim(v.symbol, v.rhs); v }
    StandardTraversal.mapClassDef(owners, u)

    // ---- the boundaries each `try` sits INSIDE. A jump's target is outside the try exactly when
    // the construct that owns the jump encloses the try, so this is the whole of the outer context
    // the crossing test needs: which tries are under a loop (a `continue`'s boundary), under a loop
    // or a switch (an unlabelled `break`'s), and under which labels (a labelled jump's).
    val underLoop     = collection.mutable.Set.empty[TryId]
    val underJumpable = collection.mutable.Set.empty[TryId]
    val brkLabels     = collection.mutable.Map.empty[TryId, Set[String]].withDefaultValue(Set.empty)
    val contLabels    = collection.mutable.Map.empty[TryId, Set[String]].withDefaultValue(Set.empty)
    val tries         = collection.mutable.ListBuffer.empty[Tree.Try]

    StandardTraversal.scanClassDef(u, ()) { (_, t) =>
      t match
        case tr: Tree.Try => tries += tr
        case _            => ()
      if Jumps.isLoop(t) then
        val ts = triesIn(t)
        underLoop ++= ts
        underJumpable ++= ts
        Jumps.loopLabel(t).foreach(l => ts.foreach { o =>
          brkLabels(o) = brkLabels(o) + l
          contLabels(o) = contLabels(o) + l
        })
      else
        t match
          // a switch ARM carries an unlabelled `break`'s boundary — `matchStr` opens one for
          // exactly the arms whose body still holds one, which is the same body this test reads.
          case _: Tree.Match    => underJumpable ++= triesIn(t)
          // a label on a NON-loop statement: `break L` only (`continue` needs a loop).
          case l: Tree.Labeled  => triesIn(l.stmt).foreach(o => brkLabels(o) = brkLabels(o) + l.name)
          case _                => ()
      ()
    }

    val out = collection.mutable.ListBuffer.empty[Finding]
    for tr <- tries do
      val armed = tr.catches.filter(c => Jumps.catchesBreak(c.param.tpt.tpe))
      if armed.nonEmpty && !guarded(tr) then
        val caught = armed.map(c => typeName(c.param.tpt.tpe)).mkString(" | ")
        val owner  = ownerOf.getOrElse(tr.origin, fqn(u.symbol))
        val id     = tr.id
        val jumps  =
          Option.when(underJumpable(id) && Jumps.breaksOut(tr.body))("break").toList
            ++ Option.when(underLoop(id) && Jumps.continuesIn(tr.body))("continue")
            ++ brkLabels(id).filter(l => Jumps.jumpsTo(tr.body, l, brk = true)).toList.sorted.map("break " + _)
            ++ contLabels(id).filter(l => Jumps.jumpsTo(tr.body, l, brk = false)).toList.sorted.map("continue " + _)
        jumps.foreach(j => out += Finding(Issue.UnguardedJump, owner, j, caught, tr.origin))
    out.toList

  /** every `try` in this subtree, BY TOKEN. `StandardTraversal` rather than a walk of this file's
    * own: the set has to be complete as node kinds are added, and it is the one walk in the engine
    * that is kept so (CLAUDE.md §3).
    *
    * The token rather than the node, because this traversal REBUILDS what it walks — a `scan` is a
    * `map` with a side effect — so no object here is the one the caller holds, while `copy` carries
    * `Tree.Try.id` across every rebuild. */
  private def triesIn(t: Term)(using Program): Set[TryId] =
    StandardTraversal.scanTerm(t, Set.empty[TryId]) { (acc, x) =>
      x match
        case tr: Tree.Try => acc + tr.id
        case _            => acc
    }

  /** …and the same subtree's `try` ORIGINS, for the one table that cannot use identity — see
    * `ownerOf`. */
  private def tryOriginsIn(t: Term)(using Program): Set[Origin] =
    StandardTraversal.scanTerm(t, Set.empty[Origin]) { (acc, x) =>
      x match
        case tr: Tree.Try => acc + tr.origin
        case _            => acc
    }

  private def fqn(s: SymId)(using program: Program): String =
    program.symbolOf(s).map(_.fullName).getOrElse("?")

  private def typeName(t: TypeRepr)(using program: Program): String = t match
    case TypeRepr.OrType(l, r)      => s"${typeName(l)} | ${typeName(r)}"
    case TypeRepr.AppliedType(c, _) => typeName(c)
    case TypeRepr.TypeRef(_, s)     => fqn(s)
    case other                      => other.toString

  def summary(fs: List[Finding]): String =
    if fs.isEmpty then "  none"
    else
      fs.groupBy(_.issue).toList.sortBy((_, v) => -v.size).map { (issue, vs) =>
        val head  = s"  ${vs.size} × $issue\n  ${Issue.classification(issue)}"
        val sites = vs.sortBy(f => (f.origin.javaPath, f.origin.line)).take(10).map("    " + _.render)
        (head :: sites).mkString("\n")
      }.mkString("\n")
