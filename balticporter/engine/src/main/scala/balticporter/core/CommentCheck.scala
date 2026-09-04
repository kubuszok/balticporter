package balticporter.core

/** Checks that every Java comment appears in the generated Scala, compared on normalized body text.
  * Comments in `dropped` are exempted. */
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
