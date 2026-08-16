package balticporter.tir

/** The map from an ANCESTOR's type PARAMETERS to the arguments a subclass instantiates them with —
  * *the one derivation every synthesiser that copies a parent signature into a subclass must run*.
  *
  * ==What it is for==
  * Java lets a class inherit a member whose signature is written in its PARENT's scope. Every
  * mechanism that materialises such a member INTO the subclass — a diamond-disambiguating forwarder
  * ([[TirEmitter]]), a synthesised primary constructor whose slots are the parent constructor's
  * formals ([[CtorFunnel]]), a replayed constructor body — copies types that mention the parent's
  * type parameters, and **the subclass declares none of them**. The result is valid-looking Scala
  * naming a type that is not in scope:
  *
  * {{{
  *   // java:  class Impl extends Base<Leaf>          Base<T> { T[] split(char c); Base(Node<T> n); }
  *   override def split(c: Char): Array[T] = …        // Not found: type T
  *   class Impl protected (sup$0: Node[T])            // Not found: type T
  * }}}
  *
  * The instantiation is written in the `extends` clause, so the substitution is EXACT rather than a
  * guess: `Base[Leaf]` says `T = Leaf`, and every occurrence of the parent's `T` in a member
  * materialised here means `Leaf`.
  *
  * ==Why it is ONE derivation and not one per caller==
  * `CLAUDE.md` §4.56: three sites needed this map, one had it (the constructor replay), and the
  * other two were emitting the parent's own spelling. A rule derived per caller is a rule two of
  * whose callers are wrong, and the two that were wrong were wrong in the same way at the same
  * hierarchy. Stated once here, growing a fourth synthesiser costs a call rather than a rediscovery.
  *
  * ==TRANSITIVE, because the member need not come from the immediate parent==
  * A forwarder disambiguates a method the SUPERCLASS chain supplies, and a replay lifts statements
  * that may be a grandparent's. `Impl extends Mid[Leaf]`, `Mid<X> extends Base<X>`, and the copied
  * signature names `Base`'s `T`: the walk composes each level's instantiation through the one below
  * it, so `T -> X -> Leaf` collapses to `T -> Leaf` in a single map.
  *
  * A subclass that passes its OWN parameter through (`class Foo[X] extends Bar[X]`) maps `Bar`'s
  * `T` to `Foo`'s `X` — a type the emitted class really does declare, which is why the answer is a
  * substitution and never an erasure.
  *
  * ==What it deliberately does not do==
  * It maps only the parameters of ancestors this program DECLARES: an external parent's type
  * parameters are a fact about a class file and no phase may move them (§4.56), and a class file the
  * run could not read supplies no `ClassDef` to read `tparams` off. An unmapped parameter is left
  * exactly as it was, which is the previous behaviour — so an empty map is a no-op and a caller can
  * always run it. */
object ParentSubst:

  /** the ancestors' type parameters, mapped to what `cd` instantiates them with. Empty for a class
    * whose parents are non-generic, external, or not applied. */
  def of(cd: Tree.ClassDef)(using program: Program): Map[SymId, TypeRepr] =
    def classOfSym(s: SymId): Option[Tree.ClassDef] =
      program.definitionOf(s).collect { case c: Tree.ClassDef => c }
    // A NON-GENERIC PARENT IS STILL A STEP IN THE CHAIN, and reading only the applied ones is how
    // this walk was first written — right for the immediate parent, and silently answering "there
    // is nothing above here" for every hierarchy with a plain class in the middle. The corpus shape
    // is exactly that: `Mapped extends Impl`, `Impl extends Base[Leaf]`, and the forwarded member's
    // `T` is `Base`'s. A bare `TypeRef` binds nothing of its own (there are no arguments to bind)
    // and must not stop the climb.
    def headArgs(t: TypeRepr): Option[(SymId, List[TypeRepr])] = t match
      case TypeRepr.AppliedType(TypeRepr.TypeRef(_, s), as) => Some(s -> as)
      case TypeRepr.TypeRef(_, s)                           => Some(s -> Nil)
      case _                                                => None
    // FUEL, not a `seen` set: a hierarchy the frontend built from a cyclic class file must not hang
    // a phase, and eight levels is deeper than any corpus library's parent chain.
    def walk(c: Tree.ClassDef, acc: Map[SymId, TypeRepr], depth: Int): Map[SymId, TypeRepr] =
      if depth > 8 then acc
      else
        c.parents.flatMap {
          case tt: TypeTree => headArgs(tt.tpe)
          case t: Term      => headArgs(t.tpe)
        }.foldLeft(acc) { case (m, (psym, as)) =>
          classOfSym(psym) match
            case None     => m
            case Some(pc) =>
              // each argument is itself read THROUGH the map built so far, which is what makes the
              // composition collapse `T -> X -> Leaf` rather than leaving two hops to chase.
              val here = pc.tparams.map(_.symbol).zip(as.map(subst(_, m))).toMap
              walk(pc, m ++ here, depth + 1)
        }
    walk(cd, Map.empty, 0)

  /** rewrite every occurrence of a mapped type parameter in `t`.
    *
    * COMPLETE over [[TypeRepr]] rather than over the two shapes the first caller happened to need:
    * a forwarder's return type is `Array[T]`, a constructor formal is `Node[T]`, and the next one
    * will be an intersection or a method type. A partial recursion here is `CLAUDE.md` §4.56's
    * fast-path guard read at a type walk — right for the target it was written against, silently
    * answering "nothing to substitute" for every target added since.
    *
    * BINDERS ARE NOT ENTERED. `PolyType`/`TypeLambda` introduce their own parameters and a
    * substitution that descended into one could capture; nothing in a java-derived signature needs
    * it, and refusing is the answer that cannot be wrong. */
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
