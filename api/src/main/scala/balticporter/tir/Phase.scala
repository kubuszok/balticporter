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

  // ---- decision provenance (CLAUDE.md §4.45: make it obvious HOW the porter got here) ----

  /** What this phase DECIDED, for the run currently in progress.
    *
    * Owned by the phase for the duration of one `run` and DRAINED by [[Pipeline.runTraced]] into
    * the run's log the moment the phase returns — so a phase instance reused across two
    * translations (which `Determinism.Full` does, and a porting program that ports two source sets
    * with one phase list does) never reports the first run's decisions as the second's. It is a
    * value the phase owns, never a process-global table (§5.1).
    */
  final val decisions: DecisionLog = new DecisionLog

  /** Record why this phase changed something. Every note carries its §1 classification because the
    * reader's first question is which repository the fix lives in ([[Reason]]):
    * {{{
    * record(Decision(Decision.Kind.RetypedSignature, sym.id, sym.fullName,
    *                 Map("from" -> was, "to" -> now), Reason.Configured(name, key), origin))
    * }}}
    * Recording is CHEAP and unconditional — a decision is not gated on an artifact directory, so a
    * phase can be tested on its decisions with no filesystem in sight. */
  final def record(d: Decision): Unit = decisions.record(d)

  /** Full-control entry point. Default applies the hooks below via the standard
    * bottom-up traversal (MiniPhase-style), then rewrites symbol infos with
    * `transformType` so signatures stay consistent with the rewritten trees. Override
    * for whole-program passes. The pipeline rebuilds the xref afterwards. */
  def run(program: Program): Program =
    given Program = program
    val units   = program.units.map(u => StandardTraversal.mapClassDef(this, u))
    val symbols = StandardTraversal.mapSymbols(this, program.symbols)
    program.rebuilt(units, symbols) // xref rebuilt by the Pipeline

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

/** A phase whose POLICY is a set of declared KEYS — implemented so the RUN can bind them ONCE,
  * before the pipeline starts.
  *
  * Binding before any phase runs is not a scheduling convenience. Every policy key is written in the
  * UPSTREAM namespace and the package rename runs LAST (§4.56); binding at the front resolves every
  * key against the names its author wrote, structurally, so a phase's position in the pipeline stops
  * being able to change what its keys mean. And "did this key ever fire?" becomes a property of the
  * policy and the program rather than of the order phases happened to run in — which is what lets
  * five phases lose their private `var report` and one value own the never-fired answer.
  */
trait PolicyBound:
  /** Bind every key this phase declares. Called exactly once per translation, by the run. */
  def bindPolicy(binder: PolicyBinder): Unit

/** A named bundle of phases (`dotc.plugins.Plugin`). */
trait Plugin:
  def name: String
  def description: String
  def phases: List[Phase]

/** Orders phases by `runsAfter`/`runsBefore` and runs them over the `Program`,
  * rebuilding the xref index between phases (immutable-rebuild; optimize later). */
object Pipeline:

  /** Order the phases by `runsAfter` / `runsBefore`.
    *
    * '''It orders INSTANCES, and it used to order NAMES.''' The topological sort ran over
    * `phases.map(p => p.name -> p).toMap` and ended in `out.map(byName)`, so two instances of one
    * phase NAME collapsed to whichever the map kept — the LATER one — and the other silently never
    * ran. Nothing anywhere could see it: the run emitted, every check counted the same, and the
    * absent phase's `decisions` were absent because the phase was.
    *
    * Two same-name instances is not a hypothetical: it is exactly what a `MergeablePolicy` merge
    * that was DECLINED or REFUSED leaves behind (`SurfaceFold`, DESIGN.md §8.13), which is the
    * pre-merge behaviour the contract deliberately keeps. Measured on the first refused merge to
    * reach production: a base's whole `globals->implicits` holder did not run for one module — 0
    * decisions, no error, no count, no finding (`ENGINE-LIMITS.md` CT9 Face B). A refused pair is
    * now FATAL BEFORE THE PIPELINE STARTS (`ManifestAgreement.surfaceGate`), and this is the other
    * half of the same fix: whatever list arrives here is run in full, so a future caller that
    * assembles two instances for a reason nobody has thought of yet gets both of them, not one.
    *
    * An ordering edge NAMES a phase, and a name may stand for two instances, so an edge to a name
    * is an edge to EACH of them — "after X" means after every X, which is the only reading that
    * cannot silently under-constrain. Ties stay stable in declaration order and successors are
    * still visited in NAME order, so a pipeline with no duplicate names orders byte-identically to
    * the way it always did.
    */
  def order(phases: List[Phase]): List[Phase] =
    val instances = phases.toVector
    val byName: Map[String, List[Int]] =
      instances.indices.groupBy(i => instances(i).name).view.mapValues(_.toList).toMap
    // edges: i -> j means the INSTANCE at i runs before the instance at j
    val edges: Map[Int, Set[Int]] =
      instances.indices.foldLeft(Map.empty[Int, Set[Int]].withDefaultValue(Set.empty)) { (m, i) =>
        val p       = instances(i)
        val afters  = p.runsAfter.toList.flatMap(a => byName.getOrElse(a, Nil)).map(a => (a, i))  // a before p
        val befores = p.runsBefore.toList.flatMap(b => byName.getOrElse(b, Nil)).map(b => (i, b)) // p before b
        (afters ++ befores).foldLeft(m)((mm, e) => mm.updated(e._1, mm(e._1) + e._2))
      }
    // Kahn's algorithm (deterministic: stable by declaration order on ties)
    val indeg = collection.mutable.Map[Int, Int]().withDefaultValue(0)
    instances.indices.foreach(i => indeg.getOrElseUpdate(i, 0))
    edges.values.flatten.foreach(t => indeg(t) += 1)
    val ready = collection.mutable.Queue(instances.indices.filter(i => indeg(i) == 0)*)
    val out   = collection.mutable.ListBuffer[Int]()
    while ready.nonEmpty do
      val n = ready.dequeue()
      out += n
      edges(n).toList.sortBy(m => (instances(m).name, m))
        .foreach { m => indeg(m) -= 1; if indeg(m) == 0 then ready.enqueue(m) }
    if out.size != instances.size then
      throw new IllegalStateException(
        s"phase ordering has a cycle among: ${(instances.indices.toSet -- out.toSet).map(instances(_).name)}")
    out.toList.map(instances)

  /** Run phases in dependency order, rebuilding the xref from the rewritten tree after
    * each — so every phase sees an index consistent with the prior phase's rewrites.
    *
    * DIAGNOSIS (CLAUDE.md §4.6, "a kill switch beats another condition"). Because the phase list
    * is NAMED, three questions that used to cost a source edit and a recompile each are answered
    * by a flag on one run — see [[DebugFlags]] for where a flag is read from and why an
    * environment variable is not one of those places:
    *
    *   - "is this phase even responsible for that construct?" — `balticporter.skipPhases=<name>`
    *     (or `*` for all of them) drops it from the run. The measurement to make is the DIFF in
    *     emitted output, not the error count.
    *   - "what did the tree look like going in / coming out?" — `balticporter.dumpTirBefore` /
    *     `balticporter.dumpTirAfter`, narrowed to one unit with `balticporter.dumpOnly=<fqn>`.
    *   - "did the phase run at all, and did it change the program's size?" —
    *     `balticporter.tracePhases=true`.
    *
    * A name in `skipPhases` that matches no phase is REPORTED, not silently ignored: a typo'd
    * kill switch that quietly does nothing is worse than no kill switch, because the run that
    * "shows the phase is not responsible" never skipped anything.
    */
  def run(program: Program, phases: List[Phase]): Program = runTraced(program, phases)._1

  /** [[run]], plus the DECISION LOG the phases filled while it ran.
    *
    * A separate entry point rather than a changed signature: every caller that only wants the
    * rewritten program keeps compiling, and a caller that wants provenance asks for it. The log is
    * a value THIS CALL owns — each phase's own buffer is cleared before it runs and drained after,
    * so nothing survives into the next translation and two runs in one JVM cannot contaminate each
    * other (the failure §5.1 records for the source map's process-global predecessor).
    *
    * A SKIPPED phase (`balticporter.skipPhases`) records nothing, which is the honest answer: the
    * decisions it would have made were not made.
    */
  def runTraced(program: Program, phases: List[Phase]): (Program, DecisionLog) =
    runTraced(program, phases, new PolicyBinder(program, program.members))

  /** …with a binder the CALLER owns, so it can read the bindings afterwards.
    *
    * '''Binding happens here, not in each caller.''' A `PolicyBound` phase that is run without
    * being bound matches nothing and rewrites nothing — silently, which is the §1(b) failure this
    * whole seam exists to remove, reintroduced one layer up. A caller that has to remember a step
    * is a caller that will not, and a §1(c) rule author reaching for `Pipeline.run` has no reason
    * to know the step exists. */
  def runTraced(program: Program, phases: List[Phase], binder: PolicyBinder): (Program, DecisionLog) =
    val log     = new DecisionLog
    val ordered = order(phases)
    ordered.foreach { case p: PolicyBound => p.bindPolicy(binder); case _ => () }
    DebugFlags.banner.foreach(b => println(s"$b  (phases: ${ordered.map(_.name).mkString(", ")})"))
    val unknown = (DebugFlags.skipPhases - "*") -- ordered.map(_.name).toSet
    if unknown.nonEmpty then
      println(s"[balticporter] WARNING: skipPhases names no such phase: ${unknown.toList.sorted.mkString(", ")}" +
        s" — this pipeline has: ${ordered.map(_.name).mkString(", ")}")
    val out = ordered.foldLeft(program) { (prog, phase) =>
      dump(DebugFlags.dumpTirBefore, phase.name, "BEFORE", prog)
      if DebugFlags.skips(phase.name) then
        println(s"[balticporter] SKIPPED phase '${phase.name}' (balticporter.skipPhases)")
        prog
      else
        phase.decisions.clear() // this run's decisions only — see `runTraced`
        val out  = phase.run(prog)
        val next = out.rebuilt(xref = Xref.build(out.units))
        log.recordAll(phase.decisions.drain())
        if DebugFlags.tracePhases then
          println(s"[balticporter] phase '${phase.name}': ${next.units.size} units, ${next.symbols.all.size} symbols" +
            (if log.isEmpty then "" else s", decisions so far: ${log.size}"))
        dump(DebugFlags.dumpTirAfter, phase.name, "AFTER", next)
        next
    }
    (out, log)

  /** print the TIR at a phase boundary. `balticporter.dumpOnly=<fqn>` narrows it to one unit —
    * without that a whole-library dump is megabytes and nobody reads it. */
  private def dump(when: Set[String], phaseName: String, label: String, prog: Program): Unit =
    if when.contains(phaseName) || when.contains("*") then
      given Program = prog
      val body = DebugFlags.dumpOnly match
        case Some(fqn) =>
          TirPrinter.unit(fqn, TirPrinter.Style.debug)
            .getOrElse(s"<no unit named '$fqn' in this program (${prog.units.size} units)>")
        case scala.None => TirPrinter.program(TirPrinter.Style.debug)
      println(s"===== TIR $label phase '$phaseName'${DebugFlags.dumpOnly.map(f => s" [$f]").getOrElse("")} =====")
      println(body)
      println(s"===== end TIR $label phase '$phaseName' =====")

/** The standard bottom-up traversal that fuses a phase's hooks over a tree. Children
  * are transformed first, then the node's specific hook, then the `transformTerm`
  * catch-all. Every type occurrence (in a `TypeTree` or a term/definition `tpe`/`info`)
  * is routed through `transformType`, applied bottom-up over the `TypeRepr`. */
object StandardTraversal:

  // -- scans (accumulate-only) --
  //
  // A pass that only needs to LOOK still has to reach every node, and the map traversal below is
  // the one walk in the engine that is kept complete as node kinds are added. A hand-rolled
  // recursion is not: it stops at whatever its author forgot, silently, and two of the four
  // correctness defects this project has found were exactly that (CLAUDE.md §3). These give an
  // analysis the same coverage without giving it the power to rewrite — the tree is rebuilt
  // identically and thrown away, which costs one allocation per node and buys the guarantee.
  //
  // `f` sees every TERM, bottom-up, in the same order the map traversal visits them. A scan that
  // needs DEFINITIONS instead should implement `Phase` directly and override `transformDefDef` /
  // `transformValDef`, returning its argument (see `MutableParamsTransform.run`).

  /** fold over every term of a whole compilation unit. */
  def scanClassDef[A](t: Tree.ClassDef, init: A)(f: (A, Term) => A)(using Program): A =
    val (ph, read) = scanner(init)(f)
    mapClassDef(ph, t)
    read()

  /** fold over every term of one expression, `t` itself included. */
  def scanTerm[A](t: Term, init: A)(f: (A, Term) => A)(using Program): A =
    val (ph, read) = scanner(init)(f)
    mapTerm(ph, t)
    read()

  private def scanner[A](init: A)(f: (A, Term) => A): (Phase, () => A) =
    var acc = init
    val ph = new Phase:
      def name: String = "standard-traversal/scan"
      // the catch-all runs on EVERY term after its specific hook, so overriding it alone is what
      // makes this complete; overriding the specific hooks would enumerate node kinds again.
      override def transformTerm(x: Term)(using Program): Term = { acc = f(acc, x); x }
    (ph, () => acc)

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

  /** Route every TYPE a symbol record carries through `ph.transformType` — its `info` AND the type
    * of each of its annotations.
    *
    * The annotations half is not decoration. `Symbol.annotations` is where `@Test`, `@Deprecated`
    * and every library's own marker live, and the emitter renders each from `Annot.tpe` — so a
    * retyping phase that is not shown them re-points a type EVERYWHERE ELSE and leaves the
    * annotation naming the old one. There is no way for the phase to notice: the emitted file is
    * uncompilable at exactly one line per annotated declaration, no check counts it, and the
    * phase's own policy report says the entry matched. Measured on a port whose §1(b) type
    * redirect moved a third-party marker annotation: 3 sites, all of them silent
    * (`ENGINE-LIMITS.md` M5.8 "A symbol's ANNOTATIONS are types too").
    *
    * The annotation's ARGUMENTS are terms, and terms are reached by the tree walk that visits the
    * declaration — not from here, which sees the symbol table alone.
    *
    * ==A symbol the program does not OWN is not walked at all==
    * Its signature is a fact about a COMPILED CLASS FILE, and no phase can move one: whatever a
    * retyping does inside the port, `java.util.Set<String>` is still what that lexer's constructor
    * takes. Mapping it would produce a table that says otherwise, and every consumer of the seam
    * would then read the port's own answer on BOTH sides of it — which is `ENGINE-LIMITS.md` K15's
    * failure shape exactly, one level down from where K15 found it. Ownership is decided
    * structurally by [[Program.owned]] and never from a name (§4.56).
    *
    * This was a no-op until the frontend began interning external members WITH their `MethodType`:
    * before that every external `info` was `NoType` and every external `annotations` empty, so
    * nothing was being mapped here anyway. It is written down because the day that changed is the
    * day the omission would have started costing something, silently. */
  def mapSymbols(ph: Phase, tbl: SymbolTable)(using p: Program): SymbolTable =
    tbl.all.foldLeft(tbl)((t, s) =>
      if !p.owns(s.id) then t
      else
        t.updated(s.copy(
          info        = mapType(ph, s.info),
          annotations = s.annotations.map(a => a.copy(tpe = mapType(ph, a.tpe))),
        ))
    )

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
        enumCases = t.enumCases.map(ec =>
          ec.copy(ctorArgs = ec.ctorArgs.map(mapTerm(ph, _)), body = ec.body.map(mapStat(ph, _)))
        ),
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
      // an anonymous-class body is ordinary program text: its members must be transformed like any
      // other, or a rewrite (collections, globals→implicits, …) silently stops at the `new`.
      case x: Tree.New =>
        ph.transformNew(x.copy(tpt = mapTpt(ph, x.tpt), tpe = mapType(ph, x.tpe),
          anon = x.anon.map(a => a.copy(body = a.body.map(mapStat(ph, _))))))
      case x: Tree.Lambda =>
        ph.transformLambda(x.copy(params = x.params.map(mapValDef(ph, _)), body = mapTerm(ph, x.body), tpe = mapType(ph, x.tpe)))
      case x: Tree.Block =>
        ph.transformBlock(x.copy(stats = x.stats.map(mapStat(ph, _)), expr = mapTerm(ph, x.expr), tpe = mapType(ph, x.tpe)))
      case x: Tree.Assign => x.copy(lhs = mapTerm(ph, x.lhs), rhs = mapTerm(ph, x.rhs), tpe = mapType(ph, x.tpe))
      case x: Tree.If =>
        x.copy(cond = mapTerm(ph, x.cond), thenp = mapTerm(ph, x.thenp), elsep = mapTerm(ph, x.elsep), tpe = mapType(ph, x.tpe))
      case x: Tree.Typed    => x.copy(expr = mapTerm(ph, x.expr), tpt = mapTpt(ph, x.tpt), tpe = mapType(ph, x.tpe))
      case x: Tree.Repeated => x.copy(elems = x.elems.map(mapTerm(ph, _)), tpe = mapType(ph, x.tpe))
      case x: Tree.Return   => x.copy(expr = x.expr.map(mapTerm(ph, _)), tpe = mapType(ph, x.tpe))
      case x: Tree.While    => x.copy(cond = mapTerm(ph, x.cond), body = mapTerm(ph, x.body), tpe = mapType(ph, x.tpe))
      case x: Tree.Throw    => x.copy(expr = mapTerm(ph, x.expr), tpe = mapType(ph, x.tpe))
      case x: Tree.InstanceOf => x.copy(expr = mapTerm(ph, x.expr), tpt = mapTpt(ph, x.tpt), tpe = mapType(ph, x.tpe))
      case x: Tree.ArrayAccess => x.copy(array = mapTerm(ph, x.array), index = mapTerm(ph, x.index), tpe = mapType(ph, x.tpe))
      case x: Tree.ArrayLength => x.copy(array = mapTerm(ph, x.array), tpe = mapType(ph, x.tpe))
      case x: Tree.NewArray =>
        x.copy(elem = mapTpt(ph, x.elem), dims = x.dims.map(mapTerm(ph, _)),
          init = x.init.map(_.map(mapTerm(ph, _))), tpe = mapType(ph, x.tpe))
      case x: Tree.ForEach =>
        x.copy(binding = mapValDef(ph, x.binding), iterable = mapTerm(ph, x.iterable), body = mapTerm(ph, x.body), tpe = mapType(ph, x.tpe))
      case x: Tree.For =>
        x.copy(init = x.init.map(mapStat(ph, _)), cond = x.cond.map(mapTerm(ph, _)),
          update = x.update.map(mapStat(ph, _)), body = mapTerm(ph, x.body), tpe = mapType(ph, x.tpe))
      case x: Tree.Try =>
        x.copy(resources = x.resources.map(mapValDef(ph, _)), body = mapTerm(ph, x.body),
          catches = x.catches.map(c => Tree.CatchCase(mapValDef(ph, c.param), mapTerm(ph, c.body))),
          finalizer = x.finalizer.map(mapTerm(ph, _)), tpe = mapType(ph, x.tpe))
      case x: Tree.Match =>
        x.copy(scrutinee = mapTerm(ph, x.scrutinee),
          cases = x.cases.map(c => Tree.CaseDef(c.labels.map(mapTerm(ph, _)), c.guard.map(mapTerm(ph, _)), mapTerm(ph, c.body), c.isDefault)),
          tpe = mapType(ph, x.tpe))
      case x: Tree.MethodRef =>
        x.copy(qualifier = x.qualifier match { case Left(t) => Left(mapTpt(ph, t)); case Right(e) => Right(mapTerm(ph, e)) },
          tpe = mapType(ph, x.tpe))
      case x: Tree.This     => x.copy(tpe = mapType(ph, x.tpe))
      case x: Tree.Super    => x.copy(tpe = mapType(ph, x.tpe))
      case x: Tree.Break    => x.copy(tpe = mapType(ph, x.tpe))
      case x: Tree.Continue => x.copy(tpe = mapType(ph, x.tpe))
      case x: Tree.Labeled  => x.copy(stmt = mapTerm(ph, x.stmt), tpe = mapType(ph, x.tpe))
      case x: Tree.Assert   => x.copy(cond = mapTerm(ph, x.cond), msg = x.msg.map(mapTerm(ph, _)), tpe = mapType(ph, x.tpe))
      case x: Tree.IncDec   => x.copy(target = mapTerm(ph, x.target), tpe = mapType(ph, x.tpe))
      case x: Tree.DoWhile  => x.copy(body = mapTerm(ph, x.body), cond = mapTerm(ph, x.cond), tpe = mapType(ph, x.tpe))
      case x: Tree.Synchronized => x.copy(lock = mapTerm(ph, x.lock), body = mapTerm(ph, x.body), tpe = mapType(ph, x.tpe))
      case x: Tree.Literal  => x.copy(tpe = mapType(ph, x.tpe))
      // …and INTO its holes. A hole is an ordinary term spliced into ready-made Scala, so every
      // later phase — the package rename above all, which runs last (§4.56) — must reach it exactly
      // as it reaches any other operand. Skipped here it would be the one term in the program no
      // phase can see, with a green compile and a wrong namespace.
      case x: Tree.Opaque   => x.copy(tpe = mapType(ph, x.tpe), holes = x.holes.map(mapTerm(ph, _)))
      // The comment wrapper is REBUILT, never unwrapped: a phase that overrides no hook has to see
      // its statement transformed and get the wrapper back, or every comment vanishes at the first
      // phase that touches the body. Note the inner term goes through the hooks exactly as it would
      // unwrapped — including `transformTerm`, which is what keeps a scan's coverage complete —
      // and the wrapper itself is then offered to `transformTerm` too, one node later.
      case x: Tree.Commented => x.copy(stmt = mapTerm(ph, x.stmt))
    ph.transformTerm(rebuilt)
