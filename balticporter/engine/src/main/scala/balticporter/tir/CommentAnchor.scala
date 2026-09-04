package balticporter.tir

/** Which declaration a comment belongs to, decided from the java text.
  * Shared by the recovery backstop and [[TriviaCheck]] (one derivation, two consumers). */
object CommentAnchor:

  /** One declaration of a java file, by the line it starts on.
    * @param emitted false for a member the port drops (its javadoc is classified as deliberate).
    */
  final case class Member(line: Int, emitted: Boolean)

  /** The key both consumers must use (realpath, not raw -- symlinks in worktrees). // CLAUDE.md §5.4 */
  def key(javaPath: String): String =
    if javaPath.isEmpty then javaPath
    else
      try balticporter.core.RealPath.str(java.nio.file.Path.of(javaPath))
      catch case _: Throwable => javaPath

  def membersOf(program: Program): Map[String, List[Member]] =
    given Program = program
    val acc  = collection.mutable.ListBuffer.empty[(String, Member)]
    val keys = collection.mutable.Map.empty[String, String]
    def take(o: Origin, emitted: Boolean): Unit =
      if o.javaPath.nonEmpty && o.line > 0 then
        acc += (keys.getOrElseUpdate(o.javaPath, key(o.javaPath)) -> Member(o.line, emitted))
    val scan = new Phase:
      def name: String = "comment-anchor/declarations"
      override def transformClassDef(t: Tree.ClassDef)(using Program): Tree.ClassDef = { take(t.origin, true); t }
      override def transformDefDef(t: Tree.DefDef)(using Program): Tree.DefDef       = { take(t.origin, true); t }
      override def transformValDef(t: Tree.ValDef)(using Program): Tree.ValDef       = { take(t.origin, true); t }
      override def transformTypeDef(t: Tree.TypeDef)(using Program): Tree.TypeDef    = { take(t.origin, true); t }
    program.units.foreach(u => StandardTraversal.mapClassDef(scan, u))
    program.members.all.foreach { (_, facts) => if facts.dropped then take(facts.origin, false) }
    acc.toList.groupMap(_._1)(_._2).view.mapValues(_.distinct.sortBy(_.line)).toMap

  /** The declaration a comment documents: the next one if only blanks/annotations separate them
    * (javadoc), otherwise the previous one (body comment). `None` when there is no member nearby.
    * @param lines the java file's lines, 0-based.
    */
  def owner(lines: Array[String], startLine: Int, endLine: Int, members: List[Member]): Option[Member] =
    val next = members.find(_.line > endLine)
    val prev = members.filter(_.line <= startLine).lastOption
    val documents = next.exists { n =>
      // between comment end and declaration start: only blanks or annotations
      (endLine until (n.line - 1)).forall { i =>
        val l = if i >= 0 && i < lines.length then lines(i).trim else ""
        l.isEmpty || l.startsWith("@")
      }
    }
    if documents then next else prev.orElse(next)
