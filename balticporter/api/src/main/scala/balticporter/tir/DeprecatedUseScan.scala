package balticporter.tir

/** The deprecated uses a declaration owns, counted for its `@nowarn("msg=deprecated")`: a call or selection of a member carrying `@Deprecated`/`@deprecated` (interned by the frontend for class-file
  * members), an `orNull` selection, and a `val`/`def` statement's initialiser. An anonymous class's uses belong to its own members — a duplicate annotation is itself a warning under
  * `-Wunused:nowarn`.
  */
object DeprecatedUseScan:
  private val deprecatedAnnots = Set("java.lang.Deprecated", "scala.deprecated")

  /** a member scalac warns on when used: a class-file or an owned `@Deprecated` (rendered as scala's `@deprecated`), or lls's `orNull` */
  def isDeprecated(s: SymId)(using p: Program): Boolean = p.symbolOf(s).exists(sym => sym.name == "orNull" || annotated(sym))

  private def annotated(sym: Symbol)(using p: Program): Boolean = sym.annotations.exists(a =>
    a.tpe match
      case TypeRepr.TypeRef(_, t) => p.symbolOf(t).exists(x => deprecatedAnnots(x.fullName))
      case _                      => false
  )

  /** is this declaration, or one enclosing it, itself deprecated? scalac reports no deprecated use inside one, so an `@nowarn` there would be unused */
  def withinDeprecated(s: SymId)(using p: Program): Boolean =
    Iterator.iterate(p.symbolOf(s))(_.flatMap(x => if x.owner == SymId.None || x.owner == x.id then scala.None else p.symbolOf(x.owner))).take(64).takeWhile(_.isDefined).flatten.exists(annotated)

  private def constructsDeprecated(n: Tree.New)(using p: Program): Boolean =
    def head(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s)     => Some(s)
      case TypeRepr.AppliedType(c, _) => head(c)
      case _                          => scala.None
    head(n.tpt.tpe).exists(s => p.symbolOf(s).exists(annotated))

  def count(t: Term)(using p: Program): Int = StandardTraversal.scanTerm(t, 0) {
    case (n, Tree.Select(_, s, _, _)) if isDeprecated(s)   => n + 1
    case (n, Tree.Apply(_, _, s, _, _)) if isDeprecated(s) => n + 1
    case (n, Tree.Ident(s, _, _)) if isDeprecated(s)       => n + 1
    case (n, nw: Tree.New) if constructsDeprecated(nw)     => n + 1 - nw.anon.map(a => count(a.body)).getOrElse(0)
    // a Template-rendered call is raw text, not a Select
    case (n, o: Tree.Opaque) if o.raw.contains(".orNull") => n + 1
    // the traversal also descends into the anonymous body: net it to zero here
    case (n, Tree.New(_, _, _, Some(anon))) => n - count(anon.body)
    case (n, _)                             => n
  }

  def count(ss: List[Statement])(using Program): Int = ss.map {
    case t: Term        => count(t)
    case v: Tree.ValDef => v.rhs.map(count).getOrElse(0)
    case d: Tree.DefDef => d.rhs.map(count).getOrElse(0)
    case _ => 0
  }.sum
