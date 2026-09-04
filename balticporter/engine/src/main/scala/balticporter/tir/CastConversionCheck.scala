package balticporter.tir

/** Casts where the operand is a wrapper of a DIFFERENT primitive than the target -- java's
  * unboxing conversion (JLS 5.1.8) rendered as a scala type assertion that throws.
  *
  * The frontend handles this shape (`SpoonTir.castOf`); this check catches the residue where
  * a later phase retyped the operand. The predicate is shared with the emitter's consult.
  * // ENGINE-LIMITS K17, catalog JS-E06 */
object CastConversionCheck:

  val Name = "cast-conversion"

  /** Java's eight wrappers and the primitive each one unboxes to (JLS 5.1.8). */
  private val Unboxes = Map(
    "java.lang.Byte"      -> "scala.Byte",
    "java.lang.Short"     -> "scala.Short",
    "java.lang.Character" -> "scala.Char",
    "java.lang.Integer"   -> "scala.Int",
    "java.lang.Long"      -> "scala.Long",
    "java.lang.Float"     -> "scala.Float",
    "java.lang.Double"    -> "scala.Double",
    "java.lang.Boolean"   -> "scala.Boolean",
  )

  enum Issue:
    /** Operand is a wrapper, target is a different primitive: java converts, scala asserts. */
    case UnboxAsserted

  object Issue:
    def classification(i: Issue): String = i match
      case UnboxAsserted =>
        "§1(a) ENGINE: java's unboxing conversion (JLS 5.1.8 + 5.1.2) rendered as a scala type " +
          "ASSERTION, which throws ClassCastException where java produced a value. The frontend " +
          "emits the explicit `xxxValue()` for this shape (`SpoonTir.castOf`), so a finding here " +
          "means a PHASE retyped the operand after the frontend decided — fix the phase's own " +
          "coercion, never the port."

  final case class Finding(issue: Issue, owner: String, operand: String, target: String, origin: Origin):
    def detail: String =
      s"cast to `$target` over an operand typed `$operand`: java UNBOXES at the wrapper's own " +
        "primitive and widens (JLS 5.1.8, 5.1.2), and the emitted `asInstanceOf` asserts the " +
        "runtime class instead — a ClassCastException where java produced a value, with no compile " +
        "error and no other count able to see it"
    def render: String = s"$issue $owner: ($target) $operand  (${origin.javaPath}:${origin.line})"
    def report: CheckReport.Finding =
      CheckReport.Finding(Name, issue.toString, owner, CheckReport.relativise(origin.javaPath),
        origin.line, detail)

  /** `None` at every cast that is not a cross-type unbox. */
  def crossTypeUnbox(t: Tree.Typed)(using p: Program): Option[(String, String)] =
    for
      src <- fqn(t.expr.tpe)
      tgt <- fqn(t.tpt.tpe)
      own <- Unboxes.get(src)
      if own != tgt && Unboxes.valuesIterator.contains(tgt)
    yield (src, tgt)

  private def fqn(t: TypeRepr)(using p: Program): Option[String] = t match
    case TypeRepr.TypeRef(_, s)      => p.symbolOf(s).map(_.fullName)
    case TypeRepr.AppliedType(tc, _) => fqn(tc)
    case _                           => scala.None

  /** Over the units the run emits (D2 ownership filter). */
  def check(program: Program, units: List[Tree.ClassDef]): List[Finding] =
    given Program = program
    val out = collection.mutable.ListBuffer.empty[Finding]
    val scan = new Phase:
      def name: String = "cast-conversion/scan"
      private def claim(owner: SymId, t: Option[Term])(using p: Program): Unit =
        t.foreach(x => StandardTraversal.scanTerm(x, ()) { (_, n) =>
          n match
            case ty: Tree.Typed =>
              crossTypeUnbox(ty).foreach((s, g) =>
                out += Finding(Issue.UnboxAsserted, p.symbolOf(owner).map(_.fullName).getOrElse("?"),
                  s, g, ty.origin))
            case _ => ()
          ()
        })
      override def transformDefDef(d: Tree.DefDef)(using Program): Tree.DefDef = { claim(d.symbol, d.rhs); d }
      override def transformValDef(v: Tree.ValDef)(using Program): Tree.ValDef = { claim(v.symbol, v.rhs); v }
    units.foreach(u => StandardTraversal.mapClassDef(scan, u))
    out.toList.distinct.sortBy(f => (f.origin.javaPath, f.origin.line, f.operand))

  def summary(fs: List[Finding]): String =
    if fs.isEmpty then "  none"
    else
      fs.groupBy(_.issue).toList.sortBy((_, v) => -v.size).map { (issue, vs) =>
        val head  = s"  ${vs.size} × $issue\n  ${Issue.classification(issue)}"
        val sites = vs.sortBy(f => (f.origin.javaPath, f.origin.line)).take(10).map("    " + _.render)
        (head :: sites).mkString("\n")
      }.mkString("\n")
