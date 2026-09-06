package balticporter.transform

import balticporter.tir.*

/** PURE-MOVE FLOW PROPAGATION — "a scoped rewrite carries its call sites with it", as a value.
  * Grows a [[balticporter.tir.RuleScope]]'s seeds by walking PURE MOVE edges (assignment, bare
  * initialiser, `return`, argument-to-parameter — read off the TIR's `SymId`s, CLAUDE.md §4.56;
  * ARITHMETIC breaks the chain deliberately). Hand-written, DELIBERATELY BOUNDED (CLAUDE.md §3):
  * a missed edge fails LOUD at the site, never silently wrong. Lives in `api`, not `engine`. */
object FlowPropagation:

  /** Grow `seeds` to every ELIGIBLE symbol connected by pure-move flows — a UNION-FIND over the
    * flow edges (symmetric, transitive by construction). Restricting to `eligible` BEFORE the
    * union keeps a chain from leaking through a non-candidate symbol. @param eligible which
    * symbols may join, from the phase's OWN retyping record (§4.56), never a name test. @return
    * eligible seeds plus everything reachable — an ineligible seed contributes nothing. */
  def grow(program: Program, seeds: Set[SymId], eligible: SymId => Boolean): Set[SymId] =
    grow(edges(program), seeds, eligible)

  /** …over an edge set the caller ALREADY has. Not an optimisation for its own sake: a scope that
    * must attribute each grown declaration to the POLICY ENTRY that reached it (CLAUDE.md §4.575)
    * grows once per entry, and re-walking a 600-file program per entry is the difference between a
    * usable knob and an unusable one. */
  def grow(edges: List[(SymId, SymId)], seeds: Set[SymId], eligible: SymId => Boolean): Set[SymId] =
    val parent = collection.mutable.Map[SymId, SymId]()
    def find(x: SymId): SymId =
      var r = x
      while parent.getOrElse(r, r) != r do r = parent(r)
      parent(x) = r; r
    def union(a: SymId, b: SymId): Unit = parent(find(a)) = find(b)

    val es = edges.filter((a, b) => eligible(a) && eligible(b))
    es.foreach((a, b) => union(a, b))
    val roots    = seeds.filter(eligible).map(find)
    val universe = (es.flatMap((a, b) => List(a, b)) ++ seeds).toSet.filter(eligible)
    universe.filter(s => roots.contains(find(s)))

  /** Every pure-move flow edge in the program. `(a, b)` means a value moves between `a` and `b`
    * without arithmetic, so they must share a type. Exposed separately from [[grow]] so a spec can
    * pin the edge set itself — the thing a phase's behaviour is actually a function of. */
  def edges(program: Program): List[(SymId, SymId)] =
    val out = collection.mutable.ListBuffer[(SymId, SymId)]()

    /** the symbol a term REFERS to, when it is a bare reference — a nullary call counts (`x =
      * o.get()` moves whatever `get` returns). An ARRAY ELEMENT READ returns the ARRAY's symbol
      * (a pure move of what it holds, mirroring O3's array-as-carrier); element WRITE flows through
      * this too when `ArrayAccess` is the LHS of an `Assign`. */
    def refSym(t: Term): Option[SymId] = t match
      case Tree.Ident(s, _, _)         => Some(s)
      case Tree.Select(_, s, _, _)     => Some(s)
      case Tree.Apply(_, Nil, m, _, _) => Some(m)
      case Tree.ArrayAccess(arr, _, _, _) => refSym(arr)
      case Tree.Commented(_, inner)    => refSym(inner)
      case _                           => scala.None

    def walkTerm(t: Term, encl: SymId): Unit = t match
      case Tree.Block(stats, e, _, _, _) => stats.foreach(walkStat(_, encl)); walkTerm(e, encl)
      case Tree.Assign(l, r, _, _, _) =>
        for a <- refSym(l); b <- refSym(r) do out += ((a, b))
        walkTerm(l, encl); walkTerm(r, encl)
      case Tree.Return(Some(e), _, _) =>
        if encl != SymId.None then tailRefs(e).foreach(s => out += ((encl, s)))
        walkTerm(e, encl)
      case Tree.Apply(fun, args, m, _, _) =>
        program.definitionOf(m) match
          case Some(d: Tree.DefDef) =>
            args.zip(d.paramss.flatten).foreach((arg, pd) => refSym(arg).foreach(s => out += ((s, pd.symbol))))
          case _ => ()
        walkTerm(fun, encl); args.foreach(walkTerm(_, encl))
      case Tree.If(c, a, b, _, _)    => walkTerm(c, encl); walkTerm(a, encl); walkTerm(b, encl)
      case Tree.While(c, b, _, _, _) => walkTerm(c, encl); walkTerm(b, encl)
      case Tree.Commented(_, inner)  => walkTerm(inner, encl)
      case Tree.Select(q, _, _, _)   => walkTerm(q, encl)
      case Tree.ArrayAccess(a, i, _, _) => walkTerm(a, encl); walkTerm(i, encl)
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
      case Tree.Block(_, e, _, _, _)  => tailRefs(e)
      case Tree.If(_, a, b, _, _)     => tailRefs(a) ++ tailRefs(b)
      case Tree.Commented(_, inner)   => tailRefs(inner)
      case other                      => refSym(other).toList

    program.units.foreach(walkStat(_, SymId.None))
    // the OVERRIDE edge: a method and what it overrides share one signature (§4.55, whole
    // component or nothing) — its result moves with theirs, its i-th parameter with their i-th.
    // `Screen.render(delta)` retyped alone left `ScreenAdapter.render(float)` behind (1 error).
    val graph = balticporter.tir.OverrideGraph.build(program)
    program.symbols.all.foreach { s =>
      if s.flags.isOverride && program.owns(s.id) then
        program.definitionOf(s.id) match
          case Some(d: Tree.DefDef) =>
            val mine = d.paramss.flatten.map(_.symbol)
            graph.overridden(s.id).foreach { o =>
              out += ((s.id, o))
              program.definitionOf(o) match
                case Some(od: Tree.DefDef) =>
                  mine.zip(od.paramss.flatten.map(_.symbol)).foreach(out += _)
                case _ => ()
            }
          case _ => ()
    }
    out.toList
