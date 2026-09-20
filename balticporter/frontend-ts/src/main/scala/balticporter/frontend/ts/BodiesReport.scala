package balticporter.frontend.ts

import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path }

/** `bodies.tsv`: one row per reference member saying whether its emitted body is the translation or the reference, and why a reference body was kept.
  *
  * Rows are ordered by file, then by the member's line; paths are relative to the reference directory, so two checkouts write the same table.
  */
object BodiesReport:

  val FileName        = "bodies.tsv"
  val SummaryFileName = "bodies-summary.txt"
  val Header          = "file\tmember\toccurrence\tsource\twhy"

  /** `occurrence` is the member's index among the same-named members the skeleton reader offers in that file, `-` for a member it does not offer. */
  final case class Row(file: String, member: String, occurrence: String, source: String, why: String):
    def tsv: String = List(file, member, occurrence, source, why).map(clean).mkString("\t")

  /** `translated + sum(byWhy) == total`; `byWhy` is keyed by the part of `why` before its first `:`. */
  final case class Summary(translated: Int, total: Int, byWhy: List[(String, Int)]):
    def percent: Double = if total > 0 then translated * 100.0 / total else 0.0
    def line:    String =
      val reasons = if byWhy.isEmpty then "" else byWhy.map((why, n) => s"$why=$n").mkString("; reference: ", ", ", "")
      // The root locale, so the same run writes the same line on every machine.
      s"translated $translated/$total (${String.format(java.util.Locale.ROOT, "%.1f", Double.box(percent))}%)$reasons"

  /** The rows of one derived file, offered and unoffered members together, in line order. */
  def rows(file: String, result: ParityDerive.ParityResult): List[Row] =
    (result.bodies ++ result.unoffered).sortBy(e => (e.line, e.methodName)).map { e =>
      val why =
        if e.source == ParityDerive.Source.Translated then ""
        else if e.why.isEmpty then ParityDerive.Why.Unclassified
        else e.why
      Row(file.replace('\\', '/'), e.methodName, if e.offered then e.occurrence.toString else "-", e.source, why)
    }

  def summarize(rows: List[Row]): Summary =
    val reference = rows.filter(_.source != ParityDerive.Source.Translated)
    val byWhy     = reference.groupBy(_.why.takeWhile(_ != ':')).map((k, v) => k -> v.size).toList.sortBy(_._1)
    Summary(rows.size - reference.size, rows.size, byWhy)

  def format(rows: List[Row]): String =
    (Header :: rows.map(_.tsv)).mkString("", "\n", "\n")

  /** Writes `bodies.tsv` and the one-line `bodies-summary.txt` into `reportDir`, creating it. */
  def write(reportDir: Path, rows: List[Row]): Summary =
    val summary = summarize(rows)
    Files.createDirectories(reportDir)
    Files.write(reportDir.resolve(FileName), format(rows).getBytes(StandardCharsets.UTF_8))
    Files.write(reportDir.resolve(SummaryFileName), (summary.line + "\n").getBytes(StandardCharsets.UTF_8))
    summary

  private def clean(cell: String): String = cell.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
