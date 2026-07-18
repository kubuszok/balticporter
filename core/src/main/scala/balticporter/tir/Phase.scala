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
  */
trait Phase:
  def name: String
  def runsAfter: Set[String]  = Set.empty
  def runsBefore: Set[String] = Set.empty

  /** Full-control entry point. Default applies the hooks below via the standard
    * bottom-up traversal (MiniPhase-style). Override for whole-program passes. */
  def run(program: Program): Program =
    given Program = program
    val units = program.units.map(u => StandardTraversal.mapClassDef(this, u))
    // xref is rebuilt by the Pipeline after each phase; here we only replace trees
    new Program(units, program.symbols, program.xref)

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

  /** rewrite a type occurrence (e.g. java.util.List → scala List). */
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
        val afters  = p.runsAfter.filter(byName.contains).map(a => (a, p.name)) // a before p
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

  def run(program: Program, phases: List[Phase])(rebuildXref: Program => Program): Program =
    order(phases).foldLeft(program)((prog, phase) => rebuildXref(phase.run(prog)))

/** The standard bottom-up traversal that fuses a phase's hooks over a tree. Children
  * are transformed first, then the node's specific hook, then the `transformTerm`
  * catch-all. Starter coverage of the defined node set; grown with the node set. */
object StandardTraversal:
  def mapClassDef(ph: Phase, t: Tree.ClassDef)(using Program): Tree.ClassDef =
    ph.transformClassDef(t.copy(body = t.body.map(mapStat(ph, _))))

  def mapStat(ph: Phase, s: Statement)(using Program): Statement = s match
    case c: Tree.ClassDef => mapClassDef(ph, c)
    case d: Tree.DefDef   => ph.transformDefDef(d.copy(paramss = d.paramss.map(_.map(mapValDef(ph, _))), rhs = d.rhs.map(mapTerm(ph, _))))
    case v: Tree.ValDef   => mapValDef(ph, v)
    case td: Tree.TypeDef => ph.transformTypeDef(td)
    case t: Term          => mapTerm(ph, t)

  def mapValDef(ph: Phase, v: Tree.ValDef)(using Program): Tree.ValDef =
    ph.transformValDef(v.copy(rhs = v.rhs.map(mapTerm(ph, _))))

  def mapTerm(ph: Phase, t: Term)(using Program): Term =
    val rebuilt: Term = t match
      case x: Tree.Ident     => ph.transformIdent(x)
      case x: Tree.Select    => ph.transformSelect(x.copy(qual = mapTerm(ph, x.qual)))
      case x: Tree.Apply     => ph.transformApply(x.copy(fun = mapTerm(ph, x.fun), args = x.args.map(mapTerm(ph, _))))
      case x: Tree.TypeApply => ph.transformTypeApply(x.copy(fun = mapTerm(ph, x.fun)))
      case x: Tree.New       => ph.transformNew(x)
      case x: Tree.Lambda    => ph.transformLambda(x.copy(params = x.params.map(mapValDef(ph, _)), body = mapTerm(ph, x.body)))
      case x: Tree.Block     => ph.transformBlock(x.copy(stats = x.stats.map(mapStat(ph, _)), expr = mapTerm(ph, x.expr)))
      case x: Tree.Assign    => x.copy(lhs = mapTerm(ph, x.lhs), rhs = mapTerm(ph, x.rhs))
      case x: Tree.If        => x.copy(cond = mapTerm(ph, x.cond), thenp = mapTerm(ph, x.thenp), elsep = mapTerm(ph, x.elsep))
      case x: Tree.Typed     => x.copy(expr = mapTerm(ph, x.expr))
      case x: Tree.Repeated  => x.copy(elems = x.elems.map(mapTerm(ph, _)))
      case other             => other
    ph.transformTerm(rebuilt)
