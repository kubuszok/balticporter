package balticporter.tir

/** The `.orNull` calls a declaration OWNS, for its `@nowarn("msg=deprecated")`: an anonymous
  * class's calls belong to its own members (a duplicate annotation is itself a warning under
  * `-Wunused:nowarn`), and a `val`/`def` statement's initialiser counts. CLAUDE.md §4.4. */
object OrNullScan:
  def count(t: Term)(using p: Program): Int = StandardTraversal.scanTerm(t, 0) {
    case (n, Tree.Select(_, s, _, _)) if p.symbolOf(s).exists(_.name == "orNull") => n + 1
    // a Template-rendered call is raw text, not a Select
    case (n, o: Tree.Opaque) if o.raw.contains(".orNull") => n + 1
    // the traversal also descends into the anonymous body: net it to zero here
    case (n, Tree.New(_, _, _, Some(anon))) => n - count(anon.body)
    case (n, _) => n
  }

  def count(ss: List[Statement])(using Program): Int = ss.map {
    case t: Term        => count(t)
    case v: Tree.ValDef => v.rhs.map(count).getOrElse(0)
    case d: Tree.DefDef => d.rhs.map(count).getOrElse(0)
    case _              => 0
  }.sum
