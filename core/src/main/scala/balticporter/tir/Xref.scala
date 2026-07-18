package balticporter.tir

/** Builds the whole-program [[XrefIndex]] by walking every unit's TREE — the same
  * structure phases rewrite — so the index RESPONDS to phase/plugin rewrites the
  * moment the pipeline rebuilds it. Every symbol occurrence is recorded with a
  * [[UsageKind]] naming its position, so `usagesOf(sym)` traces a type used as an
  * external type, a type argument, a member type, a mixin, a bound, a self-type — not
  * just as a call. Descends BOTH the tree structure and the `TypeRepr` inside every
  * type occurrence, since a symbol can hide arbitrarily deep in an applied/bounded type.
  *
  * Externals (JDK/library symbols) need no definition here: they appear as `TypeRef`
  * targets and are recorded as usages with no `definitionOf`, which is exactly what
  * `usagesOf(java.util.List)` needs for the collection/FFI rewrites.
  *
  * Known model gap: class-level type parameters are not yet distinct tree nodes, so a
  * class F-bound `class C[T <: IRich[T]]` is not walked here; method/poly signatures
  * and wildcard bounds ARE (via `TypeBounds`/`PolyType` inside walked types).
  */
object Xref:
  def build(units: List[Tree.ClassDef]): XrefIndex =
    val defs   = collection.mutable.Map.empty[SymId, Definition]
    val usages = collection.mutable.Map.empty[SymId, collection.mutable.ListBuffer[Usage]]

    def rec(sym: SymId, kind: UsageKind, site: Tree): Unit =
      if sym != SymId.None then
        usages.getOrElseUpdate(sym, collection.mutable.ListBuffer.empty) += Usage(kind, site)

    def defOf(d: Definition): Unit = defs(d.symbol) = d

    // --- descend a type occurrence, recording every symbol at its position kind ---
    def walkType(t: TypeRepr, kind: UsageKind, site: Tree): Unit = t match
      case TypeRepr.TypeRef(prefix, sym) => rec(sym, kind, site); walkType(prefix, UsageKind.TypeRefPos, site)
      case TypeRepr.TermRef(prefix, sym) => rec(sym, kind, site); walkType(prefix, UsageKind.TypeRefPos, site)
      case TypeRepr.ThisType(cls)        => rec(cls, kind, site)
      case TypeRepr.SuperType(a, b)      => walkType(a, kind, site); walkType(b, kind, site)
      case TypeRepr.AppliedType(tycon, args) =>
        walkType(tycon, UsageKind.Tycon, site); args.foreach(walkType(_, UsageKind.TypeArg, site))
      case TypeRepr.AndType(l, r)        => walkType(l, UsageKind.Mixin, site); walkType(r, UsageKind.Mixin, site)
      case TypeRepr.OrType(l, r)         => walkType(l, kind, site); walkType(r, kind, site)
      case TypeRepr.ByNameType(u)        => walkType(u, kind, site)
      case TypeRepr.TypeBounds(lo, hi)   => walkType(lo, UsageKind.Bound, site); walkType(hi, UsageKind.Bound, site)
      case TypeRepr.Refinement(parent, _, info) =>
        walkType(parent, kind, site); walkType(info, UsageKind.MemberType, site)
      case TypeRepr.MethodType(params, res, _) =>
        params.foreach((_, pt) => walkType(pt, UsageKind.MemberType, site)); walkType(res, UsageKind.MemberType, site)
      case TypeRepr.PolyType(params, res) =>
        params.foreach((_, b) => walkType(b, UsageKind.Bound, site)); walkType(res, UsageKind.MemberType, site)
      case TypeRepr.TypeLambda(params, body) =>
        params.foreach((_, b) => walkType(b, UsageKind.Bound, site)); walkType(body, kind, site)
      case TypeRepr.ConstantType(Constant.ClassOfC(tp)) => walkType(tp, kind, site)
      case TypeRepr.ConstantType(_)      => ()
      case TypeRepr.ParamRef(_, _)       => () // binder-local index, names no symbol
      case TypeRepr.NoPrefix | TypeRepr.NoType => ()

    // --- descend the tree structure ---
    def walkTypeDef(td: Tree.TypeDef): Unit =
      defOf(td)
      // a type param's rhs is its TypeBounds; walkType descends into Bound positions,
      // so a class/method F-bound (T <: IRich[T]) records IRich as a Tycon and T as
      // a Bound/TypeArg — the class type parameter is now traced across positions.
      walkType(td.rhs.tpe, UsageKind.TypeRefPos, td.rhs)

    def walkClassDef(cd: Tree.ClassDef): Unit =
      defOf(cd)
      cd.tparams.foreach(walkTypeDef)
      cd.parents.zipWithIndex.foreach { case (p, i) =>
        val k = if i == 0 then UsageKind.Extends else UsageKind.Mixin
        p match
          case tt: TypeTree => walkType(tt.tpe, k, tt)
          case term: Term   => walkType(term.tpe, k, term); walkTerm(term)
      }
      cd.selfType.foreach(tt => walkType(tt.tpe, UsageKind.SelfType, tt))
      cd.body.foreach(walkStat)

    def walkStat(s: Statement): Unit = s match
      case c: Tree.ClassDef => walkClassDef(c)
      case d: Tree.DefDef =>
        defOf(d)
        d.tparams.foreach(walkTypeDef)
        d.paramss.foreach(_.foreach(walkValDef))
        walkType(d.returnTpt.tpe, UsageKind.MemberType, d.returnTpt)
        d.rhs.foreach(walkTerm)
      case v: Tree.ValDef   => walkValDef(v)
      case td: Tree.TypeDef => walkTypeDef(td)
      case t: Term          => walkTerm(t)

    def walkValDef(v: Tree.ValDef): Unit =
      defOf(v)
      walkType(v.tpt.tpe, UsageKind.MemberType, v.tpt)
      v.rhs.foreach(walkTerm)

    def walkTerm(t: Term): Unit = t match
      case i @ Tree.Ident(sym, _, _)        => rec(sym, UsageKind.TermRef, i)
      case s @ Tree.Select(qual, sym, _, _) => rec(sym, UsageKind.TermRef, s); walkTerm(qual)
      case a @ Tree.Apply(fun, args, method, _, _) =>
        rec(method, UsageKind.Call, a)
        fun match
          case _: Tree.Ident              => ()                      // method ident already recorded as Call
          case Tree.Select(qual, _, _, _) => walkTerm(qual)          // record receiver, not the method twice
          case other                      => walkTerm(other)
        args.foreach(walkTerm)
      case Tree.TypeApply(fun, targs, _, _) =>
        walkTerm(fun); targs.foreach(tt => walkType(tt.tpe, UsageKind.TypeArg, tt))
      case n @ Tree.New(tpt, _, _)          => walkType(tpt.tpe, UsageKind.Instantiate, n)
      case th @ Tree.This(cls, _, _)        => rec(cls, UsageKind.TermRef, th)
      case Tree.Typed(expr, tpt, _, _)      => walkTerm(expr); walkType(tpt.tpe, UsageKind.TypeRefPos, tpt)
      case Tree.Assign(lhs, rhs, _, _)      => walkTerm(lhs); walkTerm(rhs)
      case Tree.Block(stats, expr, _, _)    => stats.foreach(walkStat); walkTerm(expr)
      case Tree.Lambda(params, body, _, _)  => params.foreach(walkValDef); walkTerm(body)
      case Tree.If(c, th, el, _, _)         => walkTerm(c); walkTerm(th); walkTerm(el)
      case Tree.Repeated(elems, _, _)       => elems.foreach(walkTerm)
      case l @ Tree.Literal(Constant.ClassOfC(tp), _, _) => walkType(tp, UsageKind.TypeArg, l)
      case _: Tree.Literal                  => ()
      case _: Tree.Opaque                   => ()

    units.foreach(walkClassDef)
    new XrefIndex(defs.toMap, usages.view.mapValues(_.toList).toMap)
