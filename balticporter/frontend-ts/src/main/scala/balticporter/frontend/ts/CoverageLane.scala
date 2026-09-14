package balticporter.frontend.ts

/** Coverage analysis over `ParityDerive` output — per-module RAST vs reference
  * breakdown, regression detection, and TSV reporting.
  *
  * Phase 4 of the endgame plan: the data layer for re-scale on non-Java ports.
  */
object CoverageLane:

  final case class CoverageReport(
      moduleName: String,
      totalMethods: Int,
      rastDerived: Int,
      referenceDerived: Int,
      rastPercent: Double,
      byReason: Map[String, Int],
  )

  def analyze(bodies: List[ParityDerive.BodyEntry], moduleName: String): CoverageReport =
    val rast = bodies.count(_.source == "rast")
    val ref = bodies.count(_.source == "reference")
    val total = bodies.size
    val pct = if total > 0 then rast * 100.0 / total else 0.0
    val byReason = bodies
      .filter(_.source == "reference")
      .groupBy(b => if b.why.isEmpty then "unclassified" else b.why)
      .map { case (k, v) => k -> v.size }
    CoverageReport(moduleName, total, rast, ref, pct, byReason)

  def formatReport(reports: List[CoverageReport]): String =
    val sb = new StringBuilder
    sb.append(f"${"Module"}%-30s ${"Total"}%6s ${"RAST"}%6s ${"Ref"}%6s ${"Rate"}%7s\n")
    sb.append("-" * 55)
    sb.append("\n")
    var tTotal = 0; var tRast = 0; var tRef = 0
    for r <- reports do
      sb.append(f"${r.moduleName}%-30s ${r.totalMethods}%6d ${r.rastDerived}%6d ${r.referenceDerived}%6d ${r.rastPercent}%6.1f%%\n")
      tTotal += r.totalMethods; tRast += r.rastDerived; tRef += r.referenceDerived
    sb.append("-" * 55)
    sb.append("\n")
    val tPct = if tTotal > 0 then tRast * 100.0 / tTotal else 0.0
    sb.append(f"${"TOTAL"}%-30s ${tTotal}%6d ${tRast}%6d ${tRef}%6d ${tPct}%6.1f%%\n")
    sb.toString

  def formatDetailedReport(report: CoverageReport): String =
    val sb = new StringBuilder
    sb.append(s"=== ${report.moduleName} ===\n")
    sb.append(f"RAST-derived: ${report.rastDerived}/${report.totalMethods} (${report.rastPercent}%.1f%%)\n")
    if report.byReason.nonEmpty then
      sb.append("Reference bodies by reason:\n")
      for (reason, count) <- report.byReason.toList.sortBy(-_._2) do
        sb.append(f"  $reason%-40s $count%4d\n")
    sb.toString

  def checkRegressions(
      baseline: List[ParityDerive.BodyEntry],
      current: List[ParityDerive.BodyEntry],
  ): List[String] =
    val baselineRast = baseline.filter(_.source == "rast").map(_.methodName).toSet
    val currentRef = current.filter(_.source == "reference").map(_.methodName).toSet
    (baselineRast & currentRef).toList.sorted

  def formatBodiesTsv(bodies: List[ParityDerive.BodyEntry]): String =
    val header = "method_name\tsource\twhy\trefusal_count"
    val rows = bodies.map(b => s"${b.methodName}\t${b.source}\t${b.why}\t${b.rastRefusalCount}")
    (header :: rows).mkString("\n")

  def parseBodiesTsv(tsv: String): List[ParityDerive.BodyEntry] =
    val lines = tsv.split("\n").toList
    if lines.size <= 1 then Nil
    else lines.tail.map { line =>
      val parts = line.split("\t", -1)
      ParityDerive.BodyEntry(
        methodName = parts.lift(0).getOrElse(""),
        source = parts.lift(1).getOrElse(""),
        why = parts.lift(2).getOrElse(""),
        rastRefusalCount = parts.lift(3).flatMap(_.toIntOption).getOrElse(0),
      )
    }
