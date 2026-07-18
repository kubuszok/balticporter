package balticporter.tir

/** The transform pipeline — shaped by Scala 3's COMPILER PLUGIN model.
  *
  *   - `Plugin`     ~ `dotc.plugins.Plugin`: a named bundle of phases.
  *   - `Phase`      ~ `dotc.plugins.PluginPhase` / `MiniPhase`: a named transform with
  *                    `runsAfter`/`runsBefore` ordering and `transformX` hooks you
  *                    override only for the nodes you touch. The framework does the
  *                    traversal (bottom-up), so a transform is small and declarative.
  *   - full control ~ `ResearchPlugin`: override `run` for whole-program analyses
  *                    (e.g. globals→implicits needs the call graph before rewriting).
  *
  * Every hook runs with the whole-program `Program` in scope (`using`), so a transform
  * can ask `usagesOf` / `callersOf` / `symbolOf` while rewriting — the thing Quotes
  * and scalafix-over-SemanticDB cannot give you across the program, before emission.
  *
  * `transformType` is applied by the traversal at EVERY type occurrence in the tree
  * (parents, self-types, tpts, type args, `new`, ascriptions) AND over every symbol's
  * `info`, so a type rewrite lands everywhere the xref reads — and the rebuilt index
  * reflects it. That is the "responds to rewrites" contract.
  */
trait Phase:
  def name: String
  def runsAfter: Set[String]  = Set.empty
  def runsBefore: Set[String] = Set.empty

  /** Full-control entry point. Default applies the hooks below via the standard
    * bottom-up traversal (MiniPhase-style), then rewrites symbol infos with
    * `transformType` so signatures stay consistent with the rewritten trees. Override
    * for whole-program passes. The pipeline rebuilds the xref afterwards. */
  def run(program: Program): Program =
    given Program = program
    val units   = program.units.map(u => StandardTraversal.mapClassDef(this, u))
    val symbols = StandardTraversal.mapSymbols(this, program.symbols)
    new Program(units, symbols, program.xref) // xref rebuilt by the Pipeline

  // ---- MiniPhase-style hooks (identity by default) ----
  def transformClassDef(t: Tree.ClassDef)(using Program): Tree.ClassDef = t
  def transformDefDef(t: Tree.DefDef)(using Program): Tree.DefDef       = t
  def transformValDef(t: Tree.ValDef)(using Program): Tree.ValDef       = t
  def transformTypeDef(t: Tree.TypeDef)(using Program): Tree.TypeDef    = t

  def transformIdent(t: Tree.Ident)(using Program): Term         = t
  def transformSelect(t: Tree.Select)(using Program): Term       = t
  def transformApply(t: Tree.Apply)(using Program): Term         = t
  def transformTypeApply(t: Tree.TypeApply)(using Program): Term = t
  def transformNew(t: Tree.New)(using Program): Term             = t
  def transformLambda(t: Tree.Lambda)(using Program): Term       = t
  def transformBlock(t: Tree.Block)(using Program): Term         = t
  def transformTerm(t: Term)(using Program): Term                = t // catch-all, after the specific hook

  /** rewrite a type occurrence (e.g. java.util.List → scala List). Applied by the
    * traversal at every constituent, bottom-up. */
  def transformType(t: TypeRepr)(using Program): TypeRepr = t

/** A named bundle of phases (`dotc.plugins.Plugin`). */
trait Plugin:
  def name: String
  def description: String
  def phases: List[Phase]

/** Orders phases by `runsAfter`/`runsBefore` and runs them over the `Program`,
  * rebuilding the xref index between phases (immutable-rebuild; optimize later). */
object Pipeline:
  def order(phases: List[Phase]): List[Phase] =
    val byName = phases.map(p => p.name -> p).toMap
    // edges: a -> b means a runs before b
    val edges: Map[String, Set[String]] =
      phases.foldLeft(Map.empty[String, Set[String]].withDefaultValue(Set.empty)) { (m, p) =>
        val afters  = p.runsAfter.filter(byName.contains).map(a => (a, p.name))  // a before p
        val befores = p.runsBefore.filter(byName.contains).map(b => (p.name, b)) // p before b
        (afters ++ befores).foldLeft(m)((mm, e) => mm.updated(e._1, mm(e._1) + e._2))
      }
    // Kahn's algorithm (deterministic: stable by declaration order on ties)
    val indeg = collection.mutable.Map[String, Int]().withDefaultValue(0)
    phases.foreach(p => indeg.getOrElseUpdate(p.name, 0))
    edges.values.flatten.foreach(t => indeg(t) += 1)
    val ready = collection.mutable.Queue(phases.filter(p => indeg(p.name) == 0).map(_.name)*)
    val out   = collection.mutable.ListBuffer[String]()
    while ready.nonEmpty do
      val n = ready.dequeue()
      out += n
      edges(n).toList.sorted.foreach { m => indeg(m) -= 1; if indeg(m) == 0 then ready.enqueue(m) }
    if out.size != phases.size then
      throw new IllegalStateException(s"phase ordering has a cycle among: ${phases.map(_.name).toSet -- out.toSet}")
    out.toList.map(byName)

  /** Run phases in dependency order, rebuilding the xref from the rewritten tree after
    * each — so every phase sees an index consistent with the prior phase's rewrites. */
  def run(program: Program, phases: List[Phase]): Program =
    order(phases).foldLeft(program) { (prog, phase) =>
      val out = phase.run(prog)
      new Program(out.units, out.symbols, Xref.build(out.units))
    }

/** The standard bottom-up traversal that fuses a phase's hooks over a tree. Children
  * are transformed first, then the node's specific hook, then the `transformTerm`
  * catch-all. Every type occurrence (in a `TypeTree` or a term/definition `tpe`/`info`)
  * is routed through `transformType`, applied bottom-up over the `TypeRepr`. */
object StandardTraversal:
  // -- types --
  def mapType(ph: Phase, t: TypeRepr)(using Program): TypeRepr =
    val mapped: TypeRepr = t match
      case TypeRepr.TypeRef(p, s)       => TypeRepr.TypeRef(mapType(ph, p), s)
      case TypeRepr.TermRef(p, s)       => TypeRepr.TermRef(mapType(ph, p), s)
      case TypeRepr.SuperType(a, b)     => TypeRepr.SuperType(mapType(ph, a), mapType(ph, b))
      case TypeRepr.AppliedType(tc, as) => TypeRepr.AppliedType(mapType(ph, tc), as.map(mapType(ph, _)))
      case TypeRepr.AndType(l, r)       => TypeRepr.AndType(mapType(ph, l), mapType(ph, r))
      case TypeRepr.OrType(l, r)        => TypeRepr.OrType(mapType(ph, l), mapType(ph, r))
      case TypeRepr.ByNameType(u)       => TypeRepr.ByNameType(mapType(ph, u))
      case TypeRepr.TypeBounds(lo, hi)  => TypeRepr.TypeBounds(mapType(ph, lo), mapType(ph, hi))
      case TypeRepr.Refinement(p, n, i) => TypeRepr.Refinement(mapType(ph, p), n, mapType(ph, i))
      case TypeRepr.MethodType(ps, r, im) =>
        TypeRepr.MethodType(ps.map((n, pt) => (n, mapType(ph, pt))), mapType(ph, r), im)
      case TypeRepr.PolyType(ps, r)  => TypeRepr.PolyType(ps.map((n, b) => (n, mapBounds(ph, b))), mapType(ph, r))
      case TypeRepr.TypeLambda(ps, b) => TypeRepr.TypeLambda(ps.map((n, bd) => (n, mapBounds(ph, bd))), mapType(ph, b))
      case TypeRepr.ConstantType(Constant.ClassOfC(tp)) => TypeRepr.ConstantType(Constant.ClassOfC(mapType(ph, tp)))
      case other => other
    ph.transformType(mapped)

  private def mapBounds(ph: Phase, b: TypeRepr.TypeBounds)(using Program): TypeRepr.TypeBounds =
    TypeRepr.TypeBounds(mapType(ph, b.low), mapType(ph, b.hi))

  private def mapTpt(ph: Phase, tt: TypeTree)(using Program): TypeTree = TypeTree(mapType(ph, tt.tpe), tt.origin)

  def mapSymbols(ph: Phase, tbl: SymbolTable)(using Program): SymbolTable =
    tbl.all.foldLeft(tbl)((t, s) => t.updated(s.copy(info = mapType(ph, s.info))))

  // -- trees --
  def mapClassDef(ph: Phase, t: Tree.ClassDef)(using Program): Tree.ClassDef =
    val parents: List[Term | TypeTree] = t.parents.map {
      case tt: TypeTree => mapTpt(ph, tt)
      case term: Term   => mapTerm(ph, term)
    }
    ph.transformClassDef(
      t.copy(
        parents = parents,
        selfType = t.selfType.map(mapTpt(ph, _)),
        body = t.body.map(mapStat(ph, _)),
        tparams = t.tparams.map(mapTypeParam(ph, _)),
      )
    )

  private def mapTypeParam(ph: Phase, tp: Tree.TypeDef)(using Program): Tree.TypeDef =
    ph.transformTypeDef(tp.copy(rhs = mapTpt(ph, tp.rhs)))

  def mapStat(ph: Phase, s: Statement)(using Program): Statement = s match
    case c: Tree.ClassDef => mapClassDef(ph, c)
    case d: Tree.DefDef =>
      ph.transformDefDef(
        d.copy(
          tparams = d.tparams.map(mapTypeParam(ph, _)),
          paramss = d.paramss.map(_.map(mapValDef(ph, _))),
          returnTpt = mapTpt(ph, d.returnTpt),
          rhs = d.rhs.map(mapTerm(ph, _)),
        )
      )
    case v: Tree.ValDef   => mapValDef(ph, v)
    case td: Tree.TypeDef => ph.transformTypeDef(td.copy(rhs = mapTpt(ph, td.rhs)))
    case t: Term          => mapTerm(ph, t)

  def mapValDef(ph: Phase, v: Tree.ValDef)(using Program): Tree.ValDef =
    ph.transformValDef(v.copy(tpt = mapTpt(ph, v.tpt), rhs = v.rhs.map(mapTerm(ph, _))))

  def mapTerm(ph: Phase, t: Term)(using Program): Term =
    val rebuilt: Term = t match
      case x: Tree.Ident  => ph.transformIdent(x.copy(tpe = mapType(ph, x.tpe)))
      case x: Tree.Select => ph.transformSelect(x.copy(qual = mapTerm(ph, x.qual), tpe = mapType(ph, x.tpe)))
      case x: Tree.Apply =>
        ph.transformApply(x.copy(fun = mapTerm(ph, x.fun), args = x.args.map(mapTerm(ph, _)), tpe = mapType(ph, x.tpe)))
      case x: Tree.TypeApply =>
        ph.transformTypeApply(
          x.copy(fun = mapTerm(ph, x.fun), targs = x.targs.map(mapTpt(ph, _)), tpe = mapType(ph, x.tpe))
        )
      case x: Tree.New => ph.transformNew(x.copy(tpt = mapTpt(ph, x.tpt), tpe = mapType(ph, x.tpe)))
      case x: Tree.Lambda =>
        ph.transformLambda(x.copy(params = x.params.map(mapValDef(ph, _)), body = mapTerm(ph, x.body), tpe = mapType(ph, x.tpe)))
      case x: Tree.Block =>
        ph.transformBlock(x.copy(stats = x.stats.map(mapStat(ph, _)), expr = mapTerm(ph, x.expr), tpe = mapType(ph, x.tpe)))
      case x: Tree.Assign => x.copy(lhs = mapTerm(ph, x.lhs), rhs = mapTerm(ph, x.rhs), tpe = mapType(ph, x.tpe))
      case x: Tree.If =>
        x.copy(cond = mapTerm(ph, x.cond), thenp = mapTerm(ph, x.thenp), elsep = mapTerm(ph, x.elsep), tpe = mapType(ph, x.tpe))
      case x: Tree.Typed    => x.copy(expr = mapTerm(ph, x.expr), tpt = mapTpt(ph, x.tpt), tpe = mapType(ph, x.tpe))
      case x: Tree.Repeated => x.copy(elems = x.elems.map(mapTerm(ph, _)), tpe = mapType(ph, x.tpe))
      case x: Tree.This     => x.copy(tpe = mapType(ph, x.tpe))
      case x: Tree.Literal  => x.copy(tpe = mapType(ph, x.tpe))
      case x: Tree.Opaque   => x.copy(tpe = mapType(ph, x.tpe))
    ph.transformTerm(rebuilt)
