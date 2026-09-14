package balticporter.frontend.ts

/** Justification table for reference-derived method bodies — each body that
  * the RAST pipeline did NOT produce gets a machine-classified or human-reviewed
  * verdict explaining why.
  *
  * Phase 4 of the endgame plan: the justification layer for re-scale audits.
  */
object BodyVerdicts:

  final case class Verdict(
      member: String,
      status: String,
      evidence: String,
      category: String,
  )

  def autoClassify(bodies: List[ParityDerive.BodyEntry]): List[Verdict] =
    bodies.filter(_.source == "reference").map { b =>
      val (status, category) = b.why match
        case s if s.startsWith("uncompilable-pattern:") => ("justified", "uncompilable-pattern")
        case "no-rast-symbol"                           => ("structural", "structural-mismatch")
        case s if s.startsWith("translator-refusal:")    => ("justified", "translator-limitation")
        case ""                                         => ("unjustified", "unclassified")
        case other                                      => ("unjustified", other)
      Verdict(b.methodName, status, b.why, category)
    }

  def formatVerdictsTsv(verdicts: List[Verdict]): String =
    val header = "member\tstatus\tevidence\tcategory"
    val rows = verdicts.map(v => s"${v.member}\t${v.status}\t${v.evidence}\t${v.category}")
    (header :: rows).mkString("\n")

  def parseVerdictsTsv(tsv: String): List[Verdict] =
    val lines = tsv.split("\n").toList
    if lines.size <= 1 then Nil
    else lines.tail.map { line =>
      val parts = line.split("\t", -1)
      Verdict(
        member = parts.lift(0).getOrElse(""),
        status = parts.lift(1).getOrElse(""),
        evidence = parts.lift(2).getOrElse(""),
        category = parts.lift(3).getOrElse(""),
      )
    }

  def countByStatus(verdicts: List[Verdict]): Map[String, Int] =
    verdicts.groupBy(_.status).map { case (k, v) => k -> v.size }

  def countByCategory(verdicts: List[Verdict]): Map[String, Int] =
    verdicts.groupBy(_.category).map { case (k, v) => k -> v.size }

  def formatSummary(verdicts: List[Verdict]): String =
    val sb = new StringBuilder
    val byStatus = countByStatus(verdicts)
    val byCategory = countByCategory(verdicts)
    sb.append(s"Body verdicts: ${verdicts.size} reference-derived methods\n")
    sb.append("By status:\n")
    for (status, count) <- byStatus.toList.sortBy(-_._2) do
      sb.append(f"  $status%-15s $count%4d\n")
    sb.append("By category:\n")
    for (category, count) <- byCategory.toList.sortBy(-_._2) do
      sb.append(f"  $category%-30s $count%4d\n")
    sb.toString
