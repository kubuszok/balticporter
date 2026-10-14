package balticporter.frontend.ts

/** A reference member that differs from upstream on purpose, and the consumer's record saying so: it keeps its reference body whatever the translator offers, because a faithful translation would
  * silently undo the decision. `file` is the reference file relative to the reference root; `params` names one overload by its parameter types, `None` every member of that name in the file.
  */
final case class RecordedDivergence(file: String, member: String, params: Option[List[String]], record: String):

  /** True for `method` of `relativeFile`; a method whose parameter types the reader could not see is covered by any overload's record, so a doubt keeps the reference. */
  def covers(relativeFile: String, method: ReferenceSkeleton.ParsedMethod): Boolean =
    relativeFile == file && method.name == member && (params.isEmpty || method.params.forall(ps => params.contains(ps.map(RecordedDivergence.normal))))

  /** The record as one line of a report. */
  def describe: String = s"$file#$member${params.fold("")(_.mkString("(", ", ", ")"))} [$record]"

object RecordedDivergence:

  /** Why a covered member kept its reference body; the record follows the colon. */
  def why(record: String): String = s"recorded-divergence:$record"

  /** A type as compared: whitespace dropped, so a record written `Map[String, Int]` matches `Map[String,Int]`. */
  def normal(tpe: String): String = tpe.filterNot(_.isWhitespace)

  /** One record from the parts a consumer's table holds; `params` is `*` for every overload, otherwise the comma-separated parameter types (`-` for none). */
  def parse(file: String, member: String, params: String, record: String): RecordedDivergence =
    val ps = params.trim match
      case "*" => None
      case "-" => Some(Nil)
      case s   => Some(splitTopLevel(s).map(normal))
    RecordedDivergence(file.trim, member.trim, ps, record.trim)

  /** `a, Map[b, c], d` split at the commas outside brackets and parentheses. */
  private def splitTopLevel(s: String): List[String] =
    val parts = List.newBuilder[String]
    val cur   = new StringBuilder
    var depth = 0
    for ch <- s do
      ch match
        case '[' | '('         => depth += 1; cur += ch
        case ']' | ')'         => depth -= 1; cur += ch
        case ',' if depth == 0 => parts += cur.result().trim; cur.clear()
        case _                 => cur += ch
    if cur.nonEmpty then parts += cur.result().trim
    parts.result().filter(_.nonEmpty)
