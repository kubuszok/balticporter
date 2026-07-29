package balticporter.tir

/** Signature-changing rewrites, traced.
  *
  * A per-library adjustment routinely changes a DECLARATION's shape — dropping a parameter,
  * adding a type parameter (`Array[T]` → `Array[T: ClassTag]`), replacing a reflective lookup
  * with a factory function. The declaration is the easy part; the risk is the USES. Miss one and
  * the port either fails far from the cause or, worse, still compiles while meaning something
  * else.
  *
  * This gives a rewrite author the two things needed to change a signature safely:
  *
  *   - [[impact]] — BEFORE: every site that will have to change, kinded and located, so the blast
  *     radius is known up front rather than discovered by the compiler.
  *   - [[check]] — AFTER: every site that does NOT match its declaration's CURRENT signature.
  *     This is the completeness guarantee. It is derived from the tree, not from a list the author
  *     maintains, so it catches uses nobody remembered to look for.
  *
  * Both read the [[XrefIndex]], which the pipeline re-derives after each phase — so `check` sees
  * the post-rewrite program, and a stale call site is a finding rather than a silent hazard.
  *
  * Deliberately reports only what it can prove from OUR OWN declarations: a symbol we do not
  * define (JDK/library) has no known signature here, and is skipped rather than guessed at.
  */
object RewriteTrace:

  /** a site that references `sym`, and where it is. */
  final case class Site(sym: SymId, name: String, kind: UsageKind, origin: Origin, enclosing: SymId)

  /** a use that disagrees with its declaration's current signature. */
  final case class Mismatch(what: String, sym: SymId, name: String, expected: Int, found: Int, origin: Origin):
    def render: String = s"$what: $name expects $expected, found $found  (${origin.javaPath}:${origin.line})"
    def report: CheckReport.Finding =
      CheckReport.Finding("signature", what, name, CheckReport.relativise(origin.javaPath), origin.line,
        s"expects $expected, found $found")

  /** BEFORE a rewrite — every site that must be updated when these symbols' signatures change. */
  def impact(program: Program, syms: Set[SymId]): List[Site] =
    syms.toList.flatMap { s =>
      val name = program.symbolOf(s).map(_.fullName).getOrElse("?")
      program.usages(s).map(u => Site(s, name, u.kind, u.site.origin, u.enclosing))
    }

  /** BEFORE a rewrite — the same, grouped for reporting. */
  def impactSummary(program: Program, syms: Set[SymId]): String =
    val sites = impact(program, syms)
    if sites.isEmpty then "no usages"
    else
      sites.groupBy(_.name).toList.sortBy(-_._2.size)
        .map((n, ss) => s"  $n: ${ss.size} site(s) [${ss.groupBy(_.kind).toList.sortBy(-_._2.size).map((k, v) => s"$k×${v.size}").mkString(", ")}]")
        .mkString("\n")

  /** AFTER a rewrite — uses that do not match their declaration's current signature.
    *
    * Two classes of disagreement are detectable from the tree alone:
    *   - CALL ARITY: an `Apply` whose argument count contradicts the callee's parameter list
    *     (varargs allow a tail, so those require only the fixed prefix).
    *   - TYPE-ARGUMENT ARITY: an `F[…]` whose argument count contradicts `F`'s declared type
    *     parameters — the exact failure mode of adding a type parameter and missing a use.
    *   - ORPHANED CALL: a call to a member of a type we DO define, where the member itself has no
    *     declaration left — the failure mode of `Substitutions.dropMethods`, and invisible to the
    *     arity check (with nothing to compare against, it has nothing to say).
    */
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

  /** A call to a member whose OWNER is a type this port defines, but which has no declaration in
    * the program. Dropping a method (or one overload of it) leaves exactly this: a live call site
    * pointing at nothing. [[callArity]] cannot see it — with no parameter list there is no arity to
    * contradict — so without this the completeness guarantee has a hole precisely where signature
    * rewrites are performed. Confined to owners we define; an external (JDK/library) member has no
    * declaration here by construction and is not a finding.
    */
  private def orphanedCalls(program: Program): List[Mismatch] =
    program.referenced.toList.flatMap { s =>
      val sym    = program.symbolOf(s)
      val ownerD = sym.map(_.owner).filter(_ != SymId.None).flatMap(program.definitionOf)
      val known  = program.definitionOf(s).isDefined
      // `values`/`valueOf` on an enum are not declared by anyone — Java synthesises them and so
      // does the Scala `enum` we emit, so a call to one is correct precisely BECAUSE it has no
      // declaration in the tree.
      val enumSynthetic = (sym.map(_.name).exists(Set("values", "valueOf"))) &&
        ownerD.exists(d => program.symbolOf(d.symbol).exists(_.flags.isEnum))
      if known || enumSynthetic || !ownerD.exists(_.isInstanceOf[Tree.ClassDef]) then Nil
      else
        val name = sym.map(_.fullName).getOrElse("?")
        program.usages(s).collect {
          case Usage(UsageKind.Call, a: Tree.Apply, _) =>
            Mismatch("call to a member with no declaration", s, name, 0, a.args.size, a.origin)
        }
    }

  private def typeArity(program: Program): List[Mismatch] =
    // declared arity of every type we define (0 for non-generic)
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

