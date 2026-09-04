package balticporter.tir

/** The map from an ANCESTOR's type PARAMETERS to the arguments a subclass instantiates them with —
  * the one derivation any synthesiser copying a parent signature into a subclass must run (a
  * diamond forwarder, `CtorFunnel`, a replayed body). The `extends` clause makes it EXACT.
  * TRANSITIVE — composes each level (`T -> X -> Leaf` collapses to `T -> Leaf`); maps only
  * ancestors this program DECLARES (an external parent's params are a class-file fact, §4.56). */
object ParentSubst:

  /** the ancestors' type parameters, mapped to what `cd` instantiates them with. Empty for a class
    * whose parents are non-generic, external, or not applied. */
  def of(cd: Tree.ClassDef)(using Program): Map[SymId, TypeRepr] =
    ofParents(cd.parents.map { case tt: TypeTree => tt.tpe; case t: Term => t.tpe })

  /** …from the PARENT TYPES alone, for a declaration that is not a [[Tree.ClassDef]]. An ANONYMOUS
    * CLASS needs it: `new Base<Leaf>() { … }` instantiates its parent in the `Tree.New`'s `tpt`,
    * with no `ClassDef` to read a clause off. [[of]] is this against a class's own clause, so the
    * two can never derive different maps for one hierarchy. */
  def ofParents(parents: List[TypeRepr])(using program: Program): Map[SymId, TypeRepr] =
    def classOfSym(s: SymId): Option[Tree.ClassDef] =
      program.definitionOf(s).collect { case c: Tree.ClassDef => c }
    // A NON-GENERIC PARENT IS STILL A STEP IN THE CHAIN — reading only applied parents silently
    // answers "nothing above here" for a plain class in the middle (`Mapped extends Impl extends
    // Base[Leaf]`, forwarded `T` is `Base`'s). A bare `TypeRef` binds nothing but must not stop the climb.
    def headArgs(t: TypeRepr): Option[(SymId, List[TypeRepr])] = t match
      case TypeRepr.AppliedType(TypeRepr.TypeRef(_, s), as) => Some(s -> as)
      case TypeRepr.TypeRef(_, s)                           => Some(s -> Nil)
      case _                                                => None
    // FUEL, not a `seen` set: a hierarchy the frontend built from a cyclic class file must not hang
    // a phase, and eight levels is deeper than any corpus library's parent chain.
    def walk(ps: List[TypeRepr], acc: Map[SymId, TypeRepr], depth: Int): Map[SymId, TypeRepr] =
      if depth > 8 then acc
      else
        ps.flatMap(headArgs).foldLeft(acc) { case (m, (psym, as)) =>
          classOfSym(psym) match
            case None     => m
            case Some(pc) =>
              // each argument is itself read THROUGH the map built so far, which is what makes the
              // composition collapse `T -> X -> Leaf` rather than leaving two hops to chase.
              val here = pc.tparams.map(_.symbol).zip(as.map(subst(_, m))).toMap
              walk(pc.parents.map { case tt: TypeTree => tt.tpe; case t: Term => t.tpe },
                   m ++ here, depth + 1)
        }
    walk(parents, Map.empty, 0)

  /** rewrite every occurrence of a mapped type parameter in `t`. COMPLETE over [[TypeRepr]] rather
    * than the shapes the first caller happened to need — a partial recursion is §4.56's fast-path
    * guard at a type walk. BINDERS ARE NOT ENTERED: `PolyType`/`TypeLambda` could capture, and
    * nothing in a java-derived signature needs it. */
  def subst(t: TypeRepr, m: Map[SymId, TypeRepr]): TypeRepr =
    if m.isEmpty then t
    else
      def go(x: TypeRepr): TypeRepr = x match
        case TypeRepr.TypeRef(_, s) if m.contains(s) => m(s)
        case TypeRepr.AppliedType(tc, as)            => TypeRepr.AppliedType(go(tc), as.map(go))
        case TypeRepr.AndType(l, r)                  => TypeRepr.AndType(go(l), go(r))
        case TypeRepr.OrType(l, r)                   => TypeRepr.OrType(go(l), go(r))
        case TypeRepr.ByNameType(u)                  => TypeRepr.ByNameType(go(u))
        case TypeRepr.TypeBounds(lo, hi)             => TypeRepr.TypeBounds(go(lo), go(hi))
        case TypeRepr.Refinement(p, n, i)            => TypeRepr.Refinement(go(p), n, go(i))
        case TypeRepr.MethodType(ps, res, impl)      => TypeRepr.MethodType(ps.map((n, pt) => (n, go(pt))), go(res), impl)
        case other                                   => other
      go(t)

  /** `subst` against `cd`'s own map — the two-step form every caller writes. */
  def through(cd: Tree.ClassDef, t: TypeRepr)(using Program): TypeRepr = subst(t, of(cd))
