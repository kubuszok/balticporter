package balticporter.tir

/** Pre- and post-rewrite signature verification.
  *
  * [[impact]]: every usage site of the given symbols (BEFORE a rewrite).
  * [[check]]: every usage that disagrees with its declaration's current signature (AFTER).
  * Only reports on symbols this program defines; external symbols are skipped. */
object RewriteTrace:

  /** A site that references `sym`, and where it is. */
  final case class Site(sym: SymId, name: String, kind: UsageKind, origin: Origin, enclosing: SymId)

  /** A use that disagrees with its declaration's current signature. */
  final case class Mismatch(what: String, sym: SymId, name: String, expected: Int, found: Int, origin: Origin):
    def render: String = s"$what: $name expects $expected, found $found  (${origin.javaPath}:${origin.line})"
    def report: CheckReport.Finding =
      CheckReport.Finding("signature", what, name, CheckReport.relativise(origin.javaPath), origin.line,
        s"expects $expected, found $found")

  /** Every usage site of the given symbols. */
  def impact(program: Program, syms: Set[SymId]): List[Site] =
    syms.toList.flatMap { s =>
      val name = program.symbolOf(s).map(_.fullName).getOrElse("?")
      program.usages(s).map(u => Site(s, name, u.kind, u.site.origin, u.enclosing))
    }

  /** Same as [[impact]], grouped for reporting. */
  def impactSummary(program: Program, syms: Set[SymId]): String =
    val sites = impact(program, syms)
    if sites.isEmpty then "no usages"
    else
      sites.groupBy(_.name).toList.sortBy(-_._2.size)
        .map((n, ss) => s"  $n: ${ss.size} site(s) [${ss.groupBy(_.kind).toList.sortBy(-_._2.size).map((k, v) => s"$k×${v.size}").mkString(", ")}]")
        .mkString("\n")

  /** Uses that disagree with their declaration's current signature: call arity, type-argument
    * arity, and orphaned calls (member with no declaration left). */
  def check(program: Program): List[Mismatch] =
    callArity(program) ++ typeArity(program) ++ orphanedCalls(program)

  private def callArity(program: Program): List[Mismatch] =
    program.referenced.toList.flatMap { s =>
      program.definitionOf(s) match
        case Some(d: Tree.DefDef) =>
          val ps      = d.paramss.headOption.getOrElse(Nil)
          val varargs = ps.lastOption.exists(p => program.symbolOf(p.symbol).exists(_.flags.isVararg))
          val name    = program.symbolOf(s).map(_.fullName).getOrElse("?")
          def disagrees(n: Int): Boolean =
            if varargs then n < ps.size - 1 else n != ps.size
          program.usages(s).collect {
            case Usage(UsageKind.Call, a: Tree.Apply, _) if disagrees(a.args.size) =>
              Mismatch("call arity", s, name, ps.size, a.args.size, a.origin)
          }
        case _ => Nil
    }

  /** Calls to members whose owner we define but which have no declaration left.
    * Confined to owned types; external members have no declaration by construction. */
  private def orphanedCalls(program: Program): List[Mismatch] =
    program.referenced.toList.flatMap { s =>
      val sym    = program.symbolOf(s)
      val ownerD = sym.map(_.owner).filter(_ != SymId.None).flatMap(program.definitionOf)
      val known  = program.definitionOf(s).isDefined
      // `values`/`valueOf` are synthesised by both java and scala enum
      val enumSynthetic = (sym.map(_.name).exists(Set("values", "valueOf"))) &&
        ownerD.exists(d => program.symbolOf(d.symbol).exists(_.flags.isEnum))
      // …and a member SPLICED AS VERBATIM TEXT has no `Definition` BY CONSTRUCTION (`add-members`,
      // `registry`): its declaration sits in the owner's body as a `Tree.Opaque`, where
      // `definitionOf` cannot reach it, so "no declaration left" would be a false claim
      // (`ENGINE-LIMITS.md` P10). The text is the only reading available.
      val asText = ownerD.collect { case cd: Tree.ClassDef => cd }
        .exists(cd => sym.exists(x => declaresAsText(cd, x.name)))
      if known || enumSynthetic || asText || !ownerD.exists(_.isInstanceOf[Tree.ClassDef]) then Nil
      else
        val name = sym.map(_.fullName).getOrElse("?")
        program.usages(s).collect {
          case Usage(UsageKind.Call, a: Tree.Apply, _) =>
            Mismatch("call to a member with no declaration", s, name, 0, a.args.size, a.origin)
        }
    }

  /** Does this class body DECLARE `name` as spliced TEXT? A member the engine minted verbatim has
    * no `Definition`, so the text itself is the only evidence there is: a `def`/`val`/`var` at that
    * name in one of the body's `Tree.Opaque` statements (`ENGINE-LIMITS.md` P10). */
  private[tir] def declaresAsText(cd: Tree.ClassDef, name: String): Boolean =
    cd.body.exists {
      case o: Tree.Opaque => List("def", "val", "var").exists(k => o.raw.contains(s"$k $name"))
      case _              => false
    }

  private def typeArity(program: Program): List[Mismatch] =
    // declared arity of every owned type (0 for non-generic)
    val arity: Map[SymId, Int] = program.symbols.all.iterator.flatMap { sym =>
      program.definitionOf(sym.id).collect { case cd: Tree.ClassDef => sym.id -> cd.tparams.size }
    }.toMap

    def walk(t: TypeRepr, origin: Origin): List[Mismatch] = t match
      case TypeRepr.AppliedType(tc, args) =>
        val here = headSym(tc).flatMap(arity.get) match
          case Some(n) if n != args.size =>
            List(Mismatch("type-argument arity", headSym(tc).get,
                          program.symbolOf(headSym(tc).get).map(_.fullName).getOrElse("?"), n, args.size, origin))
          case _ => Nil
        here ++ walk(tc, origin) ++ args.flatMap(walk(_, origin))
      case TypeRepr.AndType(l, r)          => walk(l, origin) ++ walk(r, origin)
      case TypeRepr.OrType(l, r)           => walk(l, origin) ++ walk(r, origin)
      case TypeRepr.ByNameType(u)          => walk(u, origin)
      case TypeRepr.TypeBounds(lo, hi)     => walk(lo, origin) ++ walk(hi, origin)
      case TypeRepr.Refinement(p, _, i)    => walk(p, origin) ++ walk(i, origin)
      case TypeRepr.MethodType(ps, r, _)   => ps.flatMap((_, pt) => walk(pt, origin)) ++ walk(r, origin)
      case TypeRepr.PolyType(_, r)         => walk(r, origin)
      case TypeRepr.TypeLambda(_, b)       => walk(b, origin)
      case _                               => Nil

    program.symbols.all.toList.flatMap { sym =>
      program.definitionOf(sym.id) match
        case Some(d: Tree.ValDef) => walk(d.tpt.tpe, d.origin)
        case Some(d: Tree.DefDef) =>
          d.paramss.flatten.flatMap(p => walk(p.tpt.tpe, p.origin)) ++ walk(d.returnTpt.tpe, d.origin)
        case _ => Nil
    }.distinct

  private def headSym(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)  => Some(s)
    case TypeRepr.AppliedType(tc, _) => headSym(tc)
    case _                       => scala.None

