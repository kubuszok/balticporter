package balticporter.tir

/** Reference-typed `switch` selectors where `null` falls out instead of throwing NPE.
  *
  * Walks the tree independently of the emitter; disagrees when the emitter missed a guard.
  * A switch whose java writes `case null ->` (SE21) is excluded. Findings are §1(a) engine
  * gaps. // CLAUDE.md §4.4 */
object SwitchNullCheck:

  val Name = "switch-null"

  enum Issue:
    /** A reference-typed selector reaches the default arm, where java throws NPE. */
    case NullFallsOut

  object Issue:
    def classification(i: Issue): String = i match
      case NullFallsOut =>
        "§1(a) ENGINE: java's implicit null check on a reference-typed switch selector is a " +
          "universal java-vs-scala fact, never per-library policy. `TirEmitter.matchStr` emits " +
          "`case null => throw new java.lang.NullPointerException(…)` ahead of the java arms for " +
          "every selector whose type is not a scala value class. A finding here means a switch " +
          "reached the output through a path that does not guard: fix the emitter, not the port."

  /** @param selector the emitted type of the switch selector — what makes it nullable */
  final case class Finding(issue: Issue, owner: String, selector: String, origin: Origin):
    def detail: String =
      s"switch on `$selector`, a reference type: java throws NullPointerException on a null " +
        "selector (JLS 14.11) and this `match` falls out to the default arm instead — no error, " +
        "no moved count, and the exceptional path became a silent no-op"
    def render: String = s"$issue $owner: switch($selector)  (${origin.javaPath}:${origin.line})"
    def report: CheckReport.Finding =
      CheckReport.Finding(Name, issue.toString, owner, CheckReport.relativise(origin.javaPath),
        origin.line, detail)

  /** @param guarded which switches the emitter guarded, keyed by [[Tree.Match.id]]. */
  def check(program: Program, units: List[Tree.ClassDef], guarded: Tree.Match => Boolean): List[Finding] =
    given Program = program
    units.flatMap(inUnit(_, guarded))

  private def inUnit(u: Tree.ClassDef, guarded: Tree.Match => Boolean)(using program: Program): List[Finding] =
    val ownerOf = collection.mutable.Map.empty[Origin, String]
    val claim = (s: SymId, t: Option[Term]) =>
      t.foreach(x => matchOriginsIn(x).foreach(o => ownerOf.getOrElseUpdate(o, fqn(s))))
    val owners = new Phase:
      def name: String = "switch-null/owner"
      override def transformDefDef(d: Tree.DefDef)(using Program): Tree.DefDef = { claim(d.symbol, d.rhs); d }
      override def transformValDef(v: Tree.ValDef)(using Program): Tree.ValDef = { claim(v.symbol, v.rhs); v }
    StandardTraversal.mapClassDef(owners, u)

    val out = collection.mutable.ListBuffer.empty[Finding]
    StandardTraversal.scanClassDef(u, ()) { (_, t) =>
      t match
        case m: Tree.Match if nullable(m.scrutinee.tpe) && !writesNull(m) && !guarded(m) =>
          out += Finding(Issue.NullFallsOut, ownerOf.getOrElse(m.origin, fqn(u.symbol)),
            typeName(m.scrutinee.tpe), m.origin)
        case _ => ()
      ()
    }
    out.toList

  /** True unless the selector is a scala value class (reads the emitter's own set). */
  private def nullable(t: TypeRepr)(using Program): Boolean =
    !headSymOf(t).map(fqn).exists(balticporter.emit.TirEmitter.ScalaValueClasses.contains)

  /** True if java already writes `case null ->` (SE21 opt-out). */
  private def writesNull(m: Tree.Match): Boolean =
    m.cases.exists(_.labels.exists {
      case Tree.Literal(Constant.NullC, _, _) => true
      case _                                  => false
    })

  private def headSymOf(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)      => Some(s)
    case TypeRepr.AppliedType(tc, _) => headSymOf(tc)
    case _                           => None

  private def matchOriginsIn(t: Term)(using Program): Set[Origin] =
    StandardTraversal.scanTerm(t, Set.empty[Origin]) { (acc, x) =>
      x match
        case m: Tree.Match => acc + m.origin
        case _             => acc
    }

  private def fqn(s: SymId)(using program: Program): String =
    program.symbolOf(s).map(_.fullName).getOrElse("?")

  private def typeName(t: TypeRepr)(using Program): String =
    headSymOf(t).map(fqn).getOrElse(t.toString)

  def summary(fs: List[Finding]): String =
    if fs.isEmpty then "  none"
    else
      fs.groupBy(_.issue).toList.sortBy((_, v) => -v.size).map { (issue, vs) =>
        val head  = s"  ${vs.size} × $issue\n  ${Issue.classification(issue)}"
        val sites = vs.sortBy(f => (f.origin.javaPath, f.origin.line)).take(10).map("    " + _.render)
        (head :: sites).mkString("\n")
      }.mkString("\n")
