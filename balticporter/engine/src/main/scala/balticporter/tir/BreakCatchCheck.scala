package balticporter.tir

/** Jumps (`break`/`continue`) that cross a Break-compatible `catch` without a re-throw guard.
  *
  * Walks the tree independently of the emitter; disagrees exactly when the emitter missed a
  * crossing. Findings are §1(a) engine gaps. // CLAUDE.md §4.4 */
object BreakCatchCheck:

  val Name = "break-catch"

  enum Issue:
    /** A jump crosses a Break-compatible catch with no re-throw arm. */
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

  /** @param guarded which `try`s the emitter guarded, keyed by [[Tree.Try.id]] (not `Origin`,
    *                which is not unique across `try`s; not object identity, since the traversal
    *                rebuilds nodes). `_ => false` reproduces the un-repaired engine.
    */
  def check(program: Program, units: List[Tree.ClassDef], guarded: Tree.Try => Boolean): List[Finding] =
    given Program = program
    units.flatMap(inUnit(_, guarded))

  private def inUnit(u: Tree.ClassDef, guarded: Tree.Try => Boolean)(using program: Program): List[Finding] =
    // owner name per try (keyed by Origin -- safe here since it only decides the label, not
    // whether to report)
    val ownerOf = collection.mutable.Map.empty[Origin, String]
    val claim = (s: SymId, t: Option[Term]) =>
      t.foreach(x => tryOriginsIn(x).foreach(o => ownerOf.getOrElseUpdate(o, fqn(s))))
    val owners = new Phase:
      def name: String = "break-catch/owner"
      override def transformDefDef(d: Tree.DefDef)(using Program): Tree.DefDef = { claim(d.symbol, d.rhs); d }
      override def transformValDef(v: Tree.ValDef)(using Program): Tree.ValDef = { claim(v.symbol, v.rhs); v }
    StandardTraversal.mapClassDef(owners, u)

    // which enclosing constructs re-bind each jump kind
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
          case _: Tree.Match    => underJumpable ++= triesIn(t)
          // non-loop label: `break L` only (`continue` needs a loop)
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

  /** Every `try` in this subtree, by token (traversal rebuilds nodes, so identity is unavailable). */
  private def triesIn(t: Term)(using Program): Set[TryId] =
    StandardTraversal.scanTerm(t, Set.empty[TryId]) { (acc, x) =>
      x match
        case tr: Tree.Try => acc + tr.id
        case _            => acc
    }

  /** Same subtree's `try` origins (for the owner-name table keyed by `Origin`). */
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
