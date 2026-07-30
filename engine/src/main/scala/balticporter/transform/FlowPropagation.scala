package balticporter.transform

import balticporter.tir.*

/** PURE-MOVE FLOW PROPAGATION — "a scoped rewrite carries its call sites with it", as a value.
  *
  * ==What it is for==
  * Every rule that RETYPES a declaration has the same second half: the declaration is one symbol,
  * and the program is full of OTHER symbols that must move with it or the port stops type-checking.
  * A field becomes an opaque type and its getter's return type has to follow; a parameter becomes a
  * Scala collection and every local initialised from it has to follow. Naming all of them in the
  * port's policy is not viable — the whole reason [[balticporter.tir.RuleScope]] can be short is
  * that the seeds it names are GROWN here.
  *
  * The growth is not a guess. Java resolved every reference STATICALLY, and the frontend recorded
  * the resolution as a `SymId` on every node — so "this value moves from `a` to `b`" is read off
  * the TIR rather than inferred, which is CLAUDE.md §4.56's rule ("decide structurally, never from
  * a name") applied to flow instead of to ownership.
  *
  * ==What counts as a PURE MOVE==
  * An edge `(a, b)` means a value moves between `a` and `b` with nothing done to it, so the two
  * must share a type:
  *
  *   - `a = b` — assignment between two references;
  *   - `val a = b` — an initialiser that is a bare reference;
  *   - `return b` inside `a` — including a method body's tail, through blocks and `if` branches;
  *   - `f(b)` where `b` lands on `f`'s parameter `a`.
  *
  * ARITHMETIC is deliberately not an edge. `layer + 1` yields a plain value, which correctly BREAKS
  * the chain — that is what makes an opaque type an opaque type rather than an alias, and it is why
  * a rule using this can insert a coercion at the break instead of retyping through it.
  *
  * ==Why the walk is hand-written, and the one thing that makes that safe==
  * CLAUDE.md §3 says to walk with `StandardTraversal` and never a private recursion, because a
  * hand-rolled walk stops at whatever its author forgot. Here — as in
  * `CollectionsTransform.coerceReturns` and `CollectionBoundaryCheck.returnsIn` — the walk is
  * DELIBERATELY BOUNDED: it visits only the node kinds that can carry a pure move, the default arm
  * does not descend, and a node kind it does not know is therefore a MISSED EDGE.
  *
  * Read the failure direction, because it is the whole argument: a missed edge is a declaration
  * NOT brought into the rewrite, which is a value of the old type meeting a slot of the new one —
  * a compile error at that line for an opaque rule, and a `CollectionBoundaryCheck` finding for a
  * scoped collections rule. Loud, attributable, at the site. A spurious edge would be the opposite:
  * a declaration silently retyped for a flow that does not exist. So the walk is allowed to be
  * incomplete and is not allowed to be wrong, and it is written to fail on that side.
  *
  * Known and unclosed, for the same reason: bodies reached only through a `Lambda`, an anonymous
  * class (`Tree.New.anon`), a `Try`, a `Match`, a `for`/`foreach` or a `Tree.Commented` wrapper
  * contribute no edges. Each is a missed edge, never a wrong one.
  */
object FlowPropagation:

  /** Grow `seeds` to every ELIGIBLE symbol connected to one of them by pure-move flows.
    *
    * A UNION-FIND over the flow edges, because the relation is symmetric and transitive by
    * construction: if `a` and `b` must share a type and `b` and `c` must share a type, all three
    * must. Restricting to `eligible` BEFORE the union is what keeps a chain from leaking through a
    * symbol the caller does not consider a candidate (an `Object`-typed temporary between two
    * `int`s is not a move of an `int`).
    *
    * @param eligible which symbols may join a chain at all — for an opaque rule, "its `info` is the
    *                 underlying primitive"; for a collections rule, "its `info` names a mapped
    *                 collection". A phase supplies this from ITS OWN record of what it retypes
    *                 (§4.56), never from a name test.
    * @return the seeds that are eligible, plus everything reachable from them. A seed that is not
    *         eligible contributes nothing and is not returned — an inert hint is silent, which is
    *         the honest answer and is what a never-fired policy report exists to say instead.
    */
  def grow(program: Program, seeds: Set[SymId], eligible: SymId => Boolean): Set[SymId] =
    val parent = collection.mutable.Map[SymId, SymId]()
    def find(x: SymId): SymId =
      var r = x
      while parent.getOrElse(r, r) != r do r = parent(r)
      parent(x) = r; r
    def union(a: SymId, b: SymId): Unit = parent(find(a)) = find(b)

    val es = edges(program).filter((a, b) => eligible(a) && eligible(b))
    es.foreach((a, b) => union(a, b))
    val roots    = seeds.filter(eligible).map(find)
    val universe = (es.flatMap((a, b) => List(a, b)) ++ seeds).toSet.filter(eligible)
    universe.filter(s => roots.contains(find(s)))

  /** Every pure-move flow edge in the program. `(a, b)` means a value moves between `a` and `b`
    * without arithmetic, so they must share a type. Exposed separately from [[grow]] so a spec can
    * pin the edge set itself — the thing a phase's behaviour is actually a function of. */
  def edges(program: Program): List[(SymId, SymId)] =
    val out = collection.mutable.ListBuffer[(SymId, SymId)]()

    /** the symbol a term REFERS to, when it is a bare reference. A nullary call is one: `x = o.get()`
      * moves whatever `get` returns, and the method's own symbol is what carries that type. */
    def refSym(t: Term): Option[SymId] = t match
      case Tree.Ident(s, _, _)         => Some(s)
      case Tree.Select(_, s, _, _)     => Some(s)
      case Tree.Apply(_, Nil, m, _, _) => Some(m)
      case _                           => scala.None

    def walkTerm(t: Term, encl: SymId): Unit = t match
      case Tree.Block(stats, e, _, _) => stats.foreach(walkStat(_, encl)); walkTerm(e, encl)
      case Tree.Assign(l, r, _, _) =>
        for a <- refSym(l); b <- refSym(r) do out += ((a, b))
        walkTerm(l, encl); walkTerm(r, encl)
      case Tree.Return(Some(e), _, _) =>
        if encl != SymId.None then refSym(e).foreach(s => out += ((encl, s)))
        walkTerm(e, encl)
      case Tree.Apply(fun, args, m, _, _) =>
        program.definitionOf(m) match
          case Some(d: Tree.DefDef) =>
            args.zip(d.paramss.flatten).foreach((arg, pd) => refSym(arg).foreach(s => out += ((s, pd.symbol))))
          case _ => ()
        walkTerm(fun, encl); args.foreach(walkTerm(_, encl))
      case Tree.If(c, a, b, _, _)    => walkTerm(c, encl); walkTerm(a, encl); walkTerm(b, encl)
      case Tree.While(c, b, _, _, _) => walkTerm(c, encl); walkTerm(b, encl)
      case Tree.Select(q, _, _, _)   => walkTerm(q, encl)
      case _                         => ()

    def walkStat(s: Statement, encl: SymId): Unit = s match
      case c: Tree.ClassDef => c.body.foreach(walkStat(_, c.symbol))
      case d: Tree.DefDef   => d.rhs.foreach(r => { tailRefs(r).foreach(s => out += ((d.symbol, s))); walkTerm(r, d.symbol) })
      case v: Tree.ValDef   => v.rhs.foreach(r => { refSym(r).foreach(s => out += ((v.symbol, s))); walkTerm(r, encl) })
      case t: Term          => walkTerm(t, encl)
      case _                => ()

    /** references returnable from a method body's TAIL (a bare-expression body, or the last
      * expression of a block), which flow to the method's own symbol exactly as a `return` does. */
    def tailRefs(t: Term): List[SymId] = t match
      case Tree.Block(_, e, _, _) => tailRefs(e)
      case Tree.If(_, a, b, _, _) => tailRefs(a) ++ tailRefs(b)
      case other                  => refSym(other).toList

    program.units.foreach(walkStat(_, SymId.None))
    out.toList
