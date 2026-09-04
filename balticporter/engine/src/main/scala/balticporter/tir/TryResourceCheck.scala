package balticporter.tir

/** Try-with-resources whose resources the emitter did not lower (JLS 14.20.3).
  *
  * Walks the tree independently of the emitter; disagrees when a `try` with resources
  * reached the output without lowering. Findings are §1(a) engine gaps.
  * Currently reports 0 on all ports (no corpus library uses try-with-resources). */
object TryResourceCheck:

  val Name = "try-resource"

  enum Issue:
    /** A `try` carries resources and the emitter lowered none of them. */
    case UnloweredResource

  object Issue:
    def classification(i: Issue): String = i match
      case UnloweredResource =>
        "§1(a) ENGINE: java's try-with-resources is a universal java-vs-scala fact, never " +
          "per-library policy. `TirEmitter.resourceStr` emits JLS 14.20.3.1's own lowering — the " +
          "resource binding, a `finally` that closes in reverse declaration order, and " +
          "`addSuppressed` for a `close()` that throws while the body is already completing " +
          "abruptly. A finding here means a `try` with resources reached the output through a " +
          "path that does not lower: fix the emitter, not the port."

  /** @param resources the resource names the java statement declared, in declaration order */
  final case class Finding(issue: Issue, owner: String, resources: List[String], origin: Origin):
    def detail: String =
      s"try-with-resources declaring ${resources.mkString(", ")} — the emitted `try` binds none of " +
        "them and calls no `close()`, so a resource opened for its side effect vanishes silently " +
        "(JLS 14.20.3.1)"
    def render: String = s"$issue $owner: try(${resources.mkString("; ")})  (${origin.javaPath}:${origin.line})"
    def report: CheckReport.Finding =
      CheckReport.Finding(Name, issue.toString, owner, CheckReport.relativise(origin.javaPath),
        origin.line, detail)

  /** @param lowered which `try`s the emitter lowered, keyed by [[Tree.Try.id]].
    *                `_ => false` reproduces the un-repaired engine.
    */
  def check(program: Program, units: List[Tree.ClassDef], lowered: Tree.Try => Boolean): List[Finding] =
    given Program = program
    units.flatMap(inUnit(_, lowered))

  private def inUnit(u: Tree.ClassDef, lowered: Tree.Try => Boolean)(using program: Program): List[Finding] =
    // owner name per try (Origin key -- same shape as BreakCatchCheck)
    val ownerOf = collection.mutable.Map.empty[Origin, String]
    val claim = (s: SymId, t: Option[Term]) =>
      t.foreach(x => tryOriginsIn(x).foreach(o => ownerOf.getOrElseUpdate(o, fqn(s))))
    val owners = new Phase:
      def name: String = "try-resource/owner"
      override def transformDefDef(d: Tree.DefDef)(using Program): Tree.DefDef = { claim(d.symbol, d.rhs); d }
      override def transformValDef(v: Tree.ValDef)(using Program): Tree.ValDef = { claim(v.symbol, v.rhs); v }
    StandardTraversal.mapClassDef(owners, u)

    val out = collection.mutable.ListBuffer.empty[Finding]
    StandardTraversal.scanClassDef(u, ()) { (_, t) =>
      t match
        case tr: Tree.Try if tr.resources.nonEmpty && !lowered(tr) =>
          val names = tr.resources.map(v => program.symbolOf(v.symbol).map(_.name).getOrElse("?"))
          out += Finding(Issue.UnloweredResource, ownerOf.getOrElse(tr.origin, fqn(u.symbol)), names, tr.origin)
        case _ => ()
      ()
    }
    out.toList

  private def tryOriginsIn(t: Term)(using Program): Set[Origin] =
    StandardTraversal.scanTerm(t, Set.empty[Origin]) { (acc, x) =>
      x match
        case tr: Tree.Try => acc + tr.origin
        case _            => acc
    }

  private def fqn(s: SymId)(using program: Program): String =
    program.symbolOf(s).map(_.fullName).getOrElse("?")

  def summary(fs: List[Finding]): String =
    if fs.isEmpty then "  none"
    else
      fs.groupBy(_.issue).toList.sortBy((_, v) => -v.size).map { (issue, vs) =>
        val head  = s"  ${vs.size} × $issue\n  ${Issue.classification(issue)}"
        val sites = vs.sortBy(f => (f.origin.javaPath, f.origin.line)).take(10).map("    " + _.render)
        (head :: sites).mkString("\n")
      }.mkString("\n")
