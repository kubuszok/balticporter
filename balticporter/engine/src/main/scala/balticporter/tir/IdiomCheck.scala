package balticporter.tir

/** Three idiom lanes from one [[IdiomLog]]: converted, refused (naming the guard), and
  * unrewritten-usage residue. Required of every port (including a phase-less one — records zero).
  * Data comes from the phases, never a second walk. [[summary]] recomputes the denominator per
  * kind on every run. */
object IdiomCheck:

  val Converted = "idiom(converted)"
  val Refused   = "idiom(refused)"
  val Residue   = "idiom(residue)"

  /** The three lane names, in report order. */
  val Lanes: List[String] = List(Converted, Refused, Residue)

  private def lane(v: IdiomVerdict): String = v match
    case IdiomVerdict.Converted    => Converted
    case IdiomVerdict.Refused(_,_) => Refused
    case IdiomVerdict.Residue(_)   => Residue

  /** Per-lane §1 classification. */
  def classification(l: String): String = l match
    case Converted =>
      "§1(a) ENGINE, and NOT A DEFECT: one row per site an idiom transformer changed. The faithful " +
        "translation was already correct, so this lane is not a fix list — it is the NUMERATOR the " +
        "refusal lane beside it is the complement of, and it is here so that a wave's emitted-text " +
        "blast can be predicted from a run rather than discovered from a diff."
    case Refused =>
      "§1(a) ENGINE: one row per site an idiom transformer CONSIDERED and declined, naming the " +
        "guard. Read `guard=` first — an idiom transform's safety argument is this enumeration, so " +
        "a guard that appears here permanently is a delta the port carries deliberately, while one " +
        "whose condition the phase itself could pass is an engine bug. Nothing here is a port's to " +
        "configure: an idiom transformer is unparameterised by construction."
    case Residue =>
      "§1(a) ENGINE: a usage of a declaration an idiom transformer MOVED that the transformer did " +
        "not rewrite. This is the `Rewrite.accountedBy` lane for the idiom layer — `rewrite-callsites` " +
        "polices that each such phase names it, and this counts what it named. A non-zero count is a " +
        "slot whose two sides disagree and is a defect, not a review list."
    case other => s"unknown idiom lane `$other`"

  /** Findings for one lane. One row per site. */
  def findings(log: IdiomLog, l: String): List[CheckReport.Finding] =
    log.all.filter(c => lane(c.verdict) == l).zipWithIndex.map { (c, i) =>
      val kindPair = s"kind=${c.kind}"
      val detail = c.verdict match
        case IdiomVerdict.Converted     => s"$kindPair — ${c.what}"
        case IdiomVerdict.Refused(g, w) => s"$kindPair guard=$g — ${c.what}; $w"
        case IdiomVerdict.Residue(w)    => s"$kindPair — ${c.what}; unrewritten: $w"
      CheckReport.Finding(l, c.kind.toString, c.subject,
        CheckReport.relativise(c.origin.javaPath), c.origin.line, detail, seq = i)
    }

  /** Scale line per kind, recomputed every run. `ran` distinguishes "phase ran, found nothing"
    * (prints `0 considered`) from "no phase" (no row). */
  def summary(log: IdiomLog, ran: Set[IdiomKind] = Set.empty): String =
    val byKind = log.all.groupBy(_.kind)
    val kinds  = IdiomKind.values.toList.filter(k => ran.contains(k) || byKind.contains(k))
    if kinds.isEmpty then "IDIOM: this pipeline carries no idiom phase"
    else
      val rows = kinds.map { k =>
        val cs   = byKind.getOrElse(k, Nil)
        val conv = cs.count(_.verdict == IdiomVerdict.Converted)
        val ref  = cs.count(_.verdict.isInstanceOf[IdiomVerdict.Refused])
        val res  = cs.count(_.verdict.isInstanceOf[IdiomVerdict.Residue])
        s"  $k: ${cs.size} considered, $conv converted, $ref refused, $res residue"
      }
      ("IDIOM (candidates considered / converted / refused / residue):" :: rows).mkString("\n")

  /** Refusal population grouped by guard. */
  def refusalsByGuard(log: IdiomLog): List[String] =
    log.all.collect { case IdiomCandidate(k, IdiomVerdict.Refused(g, _), _, _, _) => (k, g) }
      .groupBy(identity).toList.sortBy((kg, cs) => (kg._1.toString, -cs.size, kg._2))
      .map { case ((k, g), cs) => s"  $k $g: ${cs.size}" }
