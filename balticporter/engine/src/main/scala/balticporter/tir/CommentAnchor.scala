package balticporter.tir

/** WHICH DECLARATION a comment belongs to, decided from the java text — the one question the
  * recovery backstop and the trivia check both have to answer, answered once.
  *
  * Two consumers, and they must agree or the port contradicts itself: the emitter decides whether
  * to RECOVER a comment (it must not, when the member it documents is one the port deliberately
  * does not emit), and [[TriviaCheck]] decides which LANE a comment that never arrived belongs in.
  * A second derivation of "whose comment is this" would let a run recover a comment its own report
  * then classified as deliberate.
  */
object CommentAnchor:

  /** one declaration of a java file, by the line it starts on.
    *
    * @param emitted false for a member the port DROPS. The port has no `def` for it, so its javadoc
    *   has no home either — and that is a DECISION, not an engine gap. `TriviaCheck`'s
    *   deliberate-drop exemption was TYPE-level only, so a dropped MEMBER's javadoc was counted as
    *   engine loss on every run.
    */
  final case class Member(line: Int, emitted: Boolean)

  /** Every declaration the program holds, per java file, sorted by line — the EMITTED ones from the
    * tree and the DROPPED ones from [[MemberIndex]], which is the last place a dropped member
    * exists at all (after the frontend filters it out it has no symbol, no `DefDef` and no row in
    * the symbol table).
    *
    * Walked with `StandardTraversal`, never a private recursion (CLAUDE.md §3): a node kind added
    * later is covered for free, and a walk that stops one node short here silently misattributes
    * every comment after it.
    */
  /** The key BOTH consumers must use, because they hold the path in different spellings: the
    * emitter has what the parser recorded and the check has what the orchestrator resolved. CLAUDE.md
    * §5.4 — realpath both operands, always. Compared raw, the check's lookup silently missed EVERY
    * file in a worktree (`.claude/worktrees/<x>/../sge` is a symlink), so the deliberate lane read
    * zero on a port with a dozen dropped members and the difference showed up nowhere else. */
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

  /** The declaration a comment documents, or `None` when the file declares nothing around it.
    *
    * Java puts a member's documentation ABOVE it and everything else INSIDE a body, so the rule is:
    * the NEXT declaration when only blank lines and annotations separate the comment from it — that
    * is a javadoc — and otherwise the PREVIOUS one, whose body the comment is written in. Reading
    * only one of the two directions misfiles exactly half the corpus: a javadoc would be charged to
    * the member above it, or a body comment to the member below.
    *
    * @param lines the java file's lines, 0-based (the caller holds them; splitting per comment is
    *   the same file split thousands of times).
    */
  def owner(lines: Array[String], startLine: Int, endLine: Int, members: List[Member]): Option[Member] =
    val next = members.find(_.line > endLine)
    val prev = members.filter(_.line <= startLine).lastOption
    val documents = next.exists { n =>
      // strictly between the comment's last line and the declaration's first: blank, or an
      // annotation, which java routinely writes on its own line under the javadoc.
      (endLine until (n.line - 1)).forall { i =>
        val l = if i >= 0 && i < lines.length then lines(i).trim else ""
        l.isEmpty || l.startsWith("@")
      }
    }
    if documents then next else prev.orElse(next)
