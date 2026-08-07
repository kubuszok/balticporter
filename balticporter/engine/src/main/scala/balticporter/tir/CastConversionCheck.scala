package balticporter.tir

/** Every emitted CAST whose target is a primitive and whose operand is a WRAPPER of a DIFFERENT
  * primitive — java's unboxing conversion, rendered as a scala type ASSERTION that throws.
  *
  * ==The difference, at the one cell that is checkable here (catalog `JS-E06`, `ENGINE-LIMITS.md` K17)==
  * `(double) aLong` is a CONVERSION in java: JLS 5.1.8 unboxes at the wrapper's own primitive and
  * 5.1.2 widens from there, so the value is `7.0`. `aLong.asInstanceOf[scala.Double]` is
  * `unboxToDouble`, which demands a `java.lang.Double` and throws `ClassCastException` on a `Long`.
  * The two are the same syntax and different programs, with a green compile and no moved count.
  *
  * '''The frontend already answers it''' — `SpoonTir.castOf`/`coerce` emit `v.doubleValue()` for
  * exactly this shape, from the type the operand HAS in the java. So the cell cannot arise from a
  * translation, and this check exists for the residue the row's own status names: **a value some
  * later PHASE retypes after the frontend has decided.** A retyping moves an operand's static type
  * and moves no cast, so a `Tree.Typed` that was an assertion when it was built can become a
  * conversion by the time it is rendered — with nothing between the two to notice.
  *
  * ==Why it is a lane that reports ZERO, and why that is the point==
  * No corpus port retypes a wrapper into another wrapper, so this counts 0 on all fifteen. That is
  * the same state `try-resource` is in, and `try-resource`'s own history is the argument: the
  * construct was dropped WHOLE for the life of a backend precisely because nothing was counting a
  * path nobody had exercised. A lane at zero is a claim that can fail; a path with no lane is not.
  *
  * '''The predicate is stated ONCE''' and read by the emitter's `JS-E06` consult as well as by this
  * check, so the obligation and the count cannot disagree about which casts the row is about
  * (`ENGINE-LIMITS.md` F8's rule). A SAME-type unbox (`Integer` at `scala.Int`) is deliberately not
  * here: scalac's `unboxToInt` is java's own conversion at that cell, and forcing anything would
  * only perturb the resolution around it.
  */
object CastConversionCheck:

  val Name = "cast-conversion"

  /** java's eight wrappers and the emitted primitive each one unboxes to (JLS 5.1.8). java's own
    * table, not a library's. */
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
    /** the operand is a wrapper, the target another primitive: java converts and this asserts. */
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

  /** THE PREDICATE. `None` at every cast that is not this cell, which is every cast in the corpus. */
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

  /** Over the units the run EMITS — the same D2 filter every other per-site report carries. */
  def check(program: Program, units: List[Tree.ClassDef]): List[Finding] =
    given Program = program
    val out = collection.mutable.ListBuffer.empty[Finding]
    // `StandardTraversal`, never a private recursion (§3): a cast inside an anonymous class's body
    // lives in a TERM, and it is exactly the position a retyping phase reaches.
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
