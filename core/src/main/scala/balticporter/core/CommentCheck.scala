package balticporter.core

/** The comment-preservation invariant: every comment in the Java source must appear in the
  * generated Scala (or be explicitly listed as dropped). Comments are compared on normalized
  * body text so that reflowing/indentation changes don't count as loss.
  */
object CommentCheck:

  def normalize(text: String): String =
    text.linesIterator
      .map(_.trim.stripPrefix("/**").stripPrefix("/*").stripSuffix("*/").stripPrefix("*").stripPrefix("//").trim)
      .filter(_.nonEmpty)
      .mkString(" ")

  final case class Missing(sourcePath: String, comment: String)

  def check(unit: BUnit, output: String, dropped: Set[String] = Set.empty): List[Missing] =
    val outNorm = normalize(output)
    unit.allComments
      .map(t => normalize(t.text))
      .filter(_.nonEmpty)
      .filterNot(dropped.contains)
      .filterNot(c => outNorm.contains(c))
      .map(Missing(unit.sourcePath, _))
