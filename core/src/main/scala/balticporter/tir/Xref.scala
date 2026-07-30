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
    // the nearest enclosing definition symbol — recorded on each usage so `callersOf`
    // is a real call-graph edge (a call knows which method contains it).
    var enclosing: SymId = SymId.None

    def rec(sym: SymId, kind: UsageKind, site: Tree): Unit =
      if sym != SymId.None then
        usages.getOrElseUpdate(sym, collection.mutable.ListBuffer.empty) += Usage(kind, site, enclosing)

    def within[A](d: SymId)(body: => A): A =
      val saved = enclosing
      enclosing = d
      try body
      finally enclosing = saved

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
      within(cd.symbol) {
        cd.tparams.foreach(walkTypeDef)
        cd.parents.zipWithIndex.foreach { case (p, i) =>
          val k = if i == 0 then UsageKind.Extends else UsageKind.Mixin
          p match
            case tt: TypeTree => walkType(tt.tpe, k, tt)
            case term: Term   => walkType(term.tpe, k, term); walkTerm(term)
        }
        cd.selfType.foreach(tt => walkType(tt.tpe, UsageKind.SelfType, tt))
      }
      cd.body.foreach(walkStat)
      cd.enumCases.foreach(ec => within(ec.symbol) { ec.ctorArgs.foreach(walkTerm); ec.body.foreach(walkStat) })

    def walkStat(s: Statement): Unit = s match
      case c: Tree.ClassDef => walkClassDef(c)
      case d: Tree.DefDef =>
        defOf(d)
        within(d.symbol) {
          d.tparams.foreach(walkTypeDef)
          d.paramss.foreach(_.foreach(walkValDef))
          walkType(d.returnTpt.tpe, UsageKind.MemberType, d.returnTpt)
          d.rhs.foreach(walkTerm)
        }
      case v: Tree.ValDef   => walkValDef(v)
      case td: Tree.TypeDef => walkTypeDef(td)
      case t: Term          => walkTerm(t)

    def walkValDef(v: Tree.ValDef): Unit =
      defOf(v)
      within(v.symbol) {
        walkType(v.tpt.tpe, UsageKind.MemberType, v.tpt)
        v.rhs.foreach(walkTerm)
      }

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
      case n @ Tree.New(tpt, _, _, anon)    =>
        walkType(tpt.tpe, UsageKind.Instantiate, n)
        // an anonymous class's members are real declarations with real usages — index them, or
        // every check derived from this index (portability, rewrite-trace) is blind inside them.
        anon.foreach(a => within(a.symbol)(a.body.foreach(walkStat)))
      // `this` is an implicit self-reference, not a cross-reference to rewrite — recording
      // it would flood every enclosing class with a usage per method body. Self-TYPES are
      // captured separately via `walkType`'s `ThisType` case.
      case _: Tree.This | _: Tree.Super     => ()
      case Tree.Typed(expr, tpt, _, _)      => walkTerm(expr); walkType(tpt.tpe, UsageKind.TypeRefPos, tpt)
      case Tree.Assign(lhs, rhs, _, _)      => walkTerm(lhs); walkTerm(rhs)
      case Tree.Block(stats, expr, _, _)    => stats.foreach(walkStat); walkTerm(expr)
      case Tree.Lambda(params, body, _, _)  => params.foreach(walkValDef); walkTerm(body)
      case Tree.If(c, th, el, _, _)         => walkTerm(c); walkTerm(th); walkTerm(el)
      case Tree.Repeated(elems, _, _)       => elems.foreach(walkTerm)
      case Tree.Return(e, _, _)             => e.foreach(walkTerm)
      case Tree.While(c, b, _, _, _)           => walkTerm(c); walkTerm(b)
      case Tree.Throw(e, _, _)              => walkTerm(e)
      case io @ Tree.InstanceOf(e, tpt, _, _) => walkTerm(e); walkType(tpt.tpe, UsageKind.TypeRefPos, io)
      case Tree.ArrayAccess(a, i, _, _)     => walkTerm(a); walkTerm(i)
      case Tree.ArrayLength(a, _, _)        => walkTerm(a)
      case na @ Tree.NewArray(el, dims, init, _, _) =>
        walkType(el.tpe, UsageKind.Instantiate, na); dims.foreach(walkTerm); init.foreach(_.foreach(walkTerm))
      case Tree.ForEach(b, it, body, _, _, _)  => walkValDef(b); walkTerm(it); walkTerm(body)
      case Tree.For(init, c, upd, body, _, _, _) =>
        init.foreach(walkStat); c.foreach(walkTerm); upd.foreach(walkStat); walkTerm(body)
      case Tree.Try(res, body, catches, fin, _, _) =>
        res.foreach(walkValDef)
        walkTerm(body)
        catches.foreach { c => walkValDef(c.param); walkTerm(c.body) }
        fin.foreach(walkTerm)
      case Tree.Match(scrut, cases, _, _) =>
        walkTerm(scrut)
        cases.foreach { c => c.labels.foreach(walkTerm); c.guard.foreach(walkTerm); walkTerm(c.body) }
      case mr @ Tree.MethodRef(qual, method, _, _) =>
        rec(method, UsageKind.Call, mr)
        qual match
          case Left(tpt)   => walkType(tpt.tpe, UsageKind.TypeRefPos, tpt)
          case Right(term) => walkTerm(term)
      case _: Tree.Break | _: Tree.Continue => () // control-flow leaves, no symbol refs
      case Tree.Assert(c, m, _, _)          => walkTerm(c); m.foreach(walkTerm)
      case Tree.IncDec(t, _, _, _, _)       => walkTerm(t)
      case Tree.DoWhile(b, c, _, _, _)         => walkTerm(b); walkTerm(c)
      case Tree.Synchronized(l, b, _, _)    => walkTerm(l); walkTerm(b)
      // a comment carries no references; the statement under it carries all of them
      case Tree.Commented(_, s)             => walkTerm(s)
      case l @ Tree.Literal(Constant.ClassOfC(tp), _, _) => walkType(tp, UsageKind.TypeArg, l)
      case _: Tree.Literal                  => ()
      case _: Tree.Opaque                   => ()

    units.foreach(walkClassDef)
    new XrefIndex(defs.toMap, usages.view.mapValues(_.toList).toMap)
