package balticporter.tir

/** THE THREE IDIOM LANES — what the idiom layer converted, what it declined, and what it moved and
  * did not rewrite.
  *
  * ==Why three lanes and not one number==
  * The trivia family's argument, one artifact over. `idiom(refused) = 0` is a bar a run could hold
  * by converting NOTHING, and `idiom(converted) = N` says nothing about the population N was drawn
  * from — so the positive, the refusal population and the residue are reported APART, from ONE log
  * ([[IdiomLog]]), and each row carries `kind=` so a lane stays readable per transformer without
  * three lanes per transformer.
  *
  * ==The DENOMINATOR is published beside the numerator, every run==
  * Chunk 18's shape, and for its reason: a narrowing whose denominator nobody prints is a number a
  * reader cannot judge. [[summary]] recomputes `considered / converted / refused / residue` per kind
  * on every run, so it cannot go stale as prose can. An idiom transformer's whole safety argument is
  * a REFUSAL ENUMERATION rather than a suite result (`CLAUDE.md` §5), and an enumeration whose
  * population is not printed is an assertion.
  *
  * ==Required of EVERY port, including one with no idiom phase==
  * All three names are in `PortRun.RequiredChecks`, carrying the argument `JdkSurface`,
  * `BaseSurface` and `RewriteCallSitesCheck` already carry: a port with no idiom phase records three
  * rows of ZERO, and a run that asked nothing is otherwise indistinguishable from a run whose
  * recording was skipped.
  *
  * ==The data comes from the PHASES, never from a second walk==
  * A check that re-derived "would this have converted" would be a second answer to the phase's own
  * question, free to disagree with it — §4.6's one-mechanism-one-seam, and the shape `ENGINE-LIMITS`
  * K2.5 measured (five findings that had been misreading themselves for the life of a port). The
  * phase holds both halves at the moment it files, which is exactly what makes the refusal lane
  * trustworthy: a refusal whose guard the phase itself could pass is an ENGINE BUG and not a
  * residue, and only the phase can tell the two apart.
  */
object IdiomCheck:

  val Converted = "idiom(converted)"
  val Refused   = "idiom(refused)"
  val Residue   = "idiom(residue)"

  /** the three, in report order. */
  val Lanes: List[String] = List(Converted, Refused, Residue)

  private def lane(v: IdiomVerdict): String = v match
    case IdiomVerdict.Converted    => Converted
    case IdiomVerdict.Refused(_,_) => Refused
    case IdiomVerdict.Residue(_)   => Residue

  /** §1's classification, per lane — the first question a reader of a row has (§4.45). */
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

  /** the findings for one lane. One row per SITE, so the count IS the population. */
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

  /** the scale line — `considered / converted / refused / residue`, per kind, recomputed every run.
    *
    * `ran` is the set of kinds the run's PIPELINE carries (`IdiomPhase.idiomKinds`), and it is what
    * makes a zero mean something. A kind whose phase ran and found nothing prints `0 considered`; a
    * kind with no phase prints no row at all. Averaged into one "nothing here" line those two are
    * indistinguishable — and the first is a number a reader needs, because a census whose population
    * fell to zero is exactly what a conversion regression looks like from here and no other
    * instrument can see it (§3: a check reporting zero is only as good as its coverage). */
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

  /** the refusal population grouped by GUARD, which is the line a reconcile pass reads: a guard
    * with a large count is either a real delta this layer will always carry or a conversion nobody
    * has written, and the two are told apart by reading the guard's own sentence. */
  def refusalsByGuard(log: IdiomLog): List[String] =
    log.all.collect { case IdiomCandidate(k, IdiomVerdict.Refused(g, _), _, _, _) => (k, g) }
      .groupBy(identity).toList.sortBy((kg, cs) => (kg._1.toString, -cs.size, kg._2))
      .map { case ((k, g), cs) => s"  $k $g: ${cs.size}" }
