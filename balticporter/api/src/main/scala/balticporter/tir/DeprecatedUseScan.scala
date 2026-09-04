package balticporter.tir

/** The DEPRECATED uses a declaration OWNS, for its `@nowarn("msg=deprecated")`: a call or
  * selection of a member carrying `@Deprecated`/`@deprecated` (interned by the frontend for
  * class-file members), lls's `orNull`, and a `val`/`def` statement's initialiser. An anonymous
  * class's uses belong to its own members — a duplicate annotation is itself a warning under
  * `-Wunused:nowarn`. CLAUDE.md §4.4. */
object DeprecatedUseScan:
  private val deprecatedAnnots = Set("java.lang.Deprecated", "scala.deprecated")

  /** external only: an OWNED member's `@Deprecated` is not rendered, so scalac never warns on it */
  def isDeprecated(s: SymId)(using p: Program): Boolean = !p.owns(s) && p.symbolOf(s).exists { sym =>
    sym.name == "orNull" || sym.annotations.exists(a => a.tpe match
      case TypeRepr.TypeRef(_, t) => p.symbolOf(t).exists(x => deprecatedAnnots(x.fullName))
      case _                      => false)
  }

  def count(t: Term)(using p: Program): Int = StandardTraversal.scanTerm(t, 0) {
    case (n, Tree.Select(_, s, _, _)) if isDeprecated(s)   => n + 1
    case (n, Tree.Apply(_, _, s, _, _)) if isDeprecated(s) => n + 1
    case (n, Tree.Ident(s, _, _)) if isDeprecated(s)       => n + 1
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
