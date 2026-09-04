package balticporter.tir

/** The transform pipeline — shaped by Scala 3's COMPILER PLUGIN model (`Phase` ~
  * `PluginPhase`/`MiniPhase`: named, `runsAfter`/`runsBefore`, `transformX` hooks the framework
  * traverses bottom-up). Every hook runs with the whole-program `Program` in scope (`using`), so
  * a transform can ask `usagesOf`/`callersOf`/`symbolOf` while rewriting. `transformType` applies
  * at every type occurrence AND every symbol's `info`. */
trait Phase:
  def name: String
  def runsAfter: Set[String]  = Set.empty
  def runsBefore: Set[String] = Set.empty

  // ---- decision provenance (CLAUDE.md §4.45: make it obvious HOW the porter got here) ----

  /** What this phase DECIDED, for the run currently in progress. Owned by the phase for one `run`
    * and DRAINED by [[Pipeline.runTraced]] the moment it returns, so a phase instance reused across
    * two translations (`Determinism.Full`) never reports the first run's decisions as the second's.
    * Never a process-global table (§5.1). */
  final val decisions: DecisionLog = new DecisionLog

  /** Record why this phase changed something. Every note carries its §1 classification via
    * [[Reason]], since the reader's first question is which repository the fix lives in. Cheap and
    * unconditional — a decision is not gated on an artifact directory, so a phase can be tested on
    * its decisions with no filesystem in sight. */
  final def record(d: Decision): Unit = decisions.record(d)

  // ---- catalog citation (`DESIGN.md` §2.8: the THIRD discharge surface) ----

  /** What CATALOG ROWS this phase decided, and at which declarations, for the run in progress.
    *
    * Owned and drained exactly as [[decisions]] is, and for the same reason: a phase instance
    * reused across two translations must not report the first run's citations as the second's. */
  private[tir] val cites = collection.mutable.ListBuffer.empty[(balticporter.catalog.DiffId, String)]

  /** CITE a catalog row at a DECLARATION this phase decided about. Weaker than the frontend's
    * obligation (a phase does not walk one node kind), reported apart as `catalog(unreached)`. One
    * row per DECLARATION, never per expression — [[Decision]]'s own granularity (CLAUDE.md §5.1):
    * a site-level rewrite is already visible in the diff. */
  final def cite(id: balticporter.catalog.DiffId, decl: String): Unit = cites += (id -> decl)

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

  /** A type constructor whose type ARGUMENTS this phase must NOT map — the traversal stops at the
    * application and carries the arguments verbatim (`ENGINE-LIMITS.md` K20): a generic argument
    * survives erasure and a reified carrier (jackson's `TypeReference`, `java.lang.Class`) reads it
    * back, so retyping it breaks construction. A HOOK not a table (retype vs. rename differ);
    * default `false`, called with the UNMAPPED constructor before descent. */
  def preservesTypeArgsOf(tc: TypeRepr)(using Program): Boolean = false

/** A phase whose POLICY is a set of declared KEYS — implemented so the RUN can bind them ONCE,
  * before the pipeline starts. Every key is written in the UPSTREAM namespace and the package
  * rename runs LAST (§4.56), so binding at the front resolves each key structurally; "did this key
  * fire?" becomes a property of policy and program, not of phase order. */
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

  /** Order the phases by `runsAfter` / `runsBefore`. Orders INSTANCES, never NAMES — two same-name
    * instances (a declined/refused `MergeablePolicy` merge) both run; an edge to a name binds EACH
    * instance of it. `ready` is a MIN-HEAP on declaration index (not a FIFO queue), so ties stay
    * stable in declaration order globally — the unique topological order lexicographically
    * smallest by declaration index. `ENGINE-LIMITS.md` CT9 Face B. */
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
    // Kahn's algorithm over a MIN-HEAP on declaration index — see the doc above for why a FIFO
    // silently inverts declaration order across a constraint edge, and for the number that found it.
    val indeg = collection.mutable.Map[Int, Int]().withDefaultValue(0)
    instances.indices.foreach(i => indeg.getOrElseUpdate(i, 0))
    edges.values.flatten.foreach(t => indeg(t) += 1)
    val ready = collection.mutable.TreeSet.from(instances.indices.filter(i => indeg(i) == 0))
    val out   = collection.mutable.ListBuffer[Int]()
    while ready.nonEmpty do
      val n = ready.head
      ready.remove(n)
      out += n
      edges(n).toList.sortBy(m => (instances(m).name, m))
        .foreach { m => indeg(m) -= 1; if indeg(m) == 0 then ready.add(m) }
    if out.size != instances.size then
      throw new IllegalStateException(
        s"phase ordering has a cycle among: ${(instances.indices.toSet -- out.toSet).map(instances(_).name)}")
    out.toList.map(instances)

  /** Run phases in dependency order, rebuilding the xref after each so every phase sees an index
    * consistent with the prior one's rewrites. Three questions a source edit used to cost now cost
    * a flag (CLAUDE.md §4.6): `balticporter.skipPhases=<name>` drops a phase (measure the DIFF, not
    * the error count), `dumpTirBefore`/`dumpTirAfter`/`dumpOnly` inspect the tree, `tracePhases`
    * announces each run. A name in `skipPhases` matching no phase is REPORTED, not ignored. */
  def run(program: Program, phases: List[Phase]): Program = runTraced(program, phases)._1

  /** [[run]], plus the DECISION LOG the phases filled while it ran — a separate entry point so a
    * caller wanting only the rewritten program keeps compiling. The log is a value THIS CALL owns:
    * each phase's buffer is cleared before it runs and drained after, so two runs in one JVM cannot
    * contaminate each other (§5.1). A SKIPPED phase records nothing, honestly. */
  def runTraced(program: Program, phases: List[Phase]): (Program, DecisionLog) =
    runTraced(program, phases, new PolicyBinder(program, program.members))

  /** …with a binder the CALLER owns, so it can read the bindings afterwards. Binding happens here,
    * not in each caller — a `PolicyBound` phase run unbound matches nothing and rewrites nothing,
    * silently, which is the §1(b) failure this seam removes. A caller that has to remember a step
    * is one that will not. */
  def runTraced(program: Program, phases: List[Phase], binder: PolicyBinder): (Program, DecisionLog) =
    runTraced(program, phases, binder, balticporter.catalog.CatalogLog.discarding)

  /** …and with the run's CATALOG LOG, so a phase's `cite` reaches the same log the frontend's
    * consults do. One log per run, three surfaces feeding it (`DESIGN.md` §2.8) — three per-surface
    * artifacts would answer three narrower questions and never "was this row reached at all". */
  def runTraced(program: Program, phases: List[Phase], binder: PolicyBinder,
                catalog: balticporter.catalog.CatalogLog): (Program, DecisionLog) =
    runTraced(program, phases, binder, catalog, RewriteLog.discarding)

  /** …and with the run's REWRITE LOG, which records what each phase MOVED. Taken HERE, not by the
    * phases: the pipeline holds the symbol table on both sides of a phase, so "which owned
    * declarations did its `info` rewrite move" is a comparison, not a self-report ([[Rewrite.accountedBy]]).
    * Owned symbols only, present on BOTH sides — a minted symbol has no prior `info`, an external's
    * signature is a class-file fact no phase may move (§4.56). */
  def runTraced(program: Program, phases: List[Phase], binder: PolicyBinder,
                catalog: balticporter.catalog.CatalogLog, rewrites: RewriteLog): (Program, DecisionLog) =
    runTraced(program, phases, binder, catalog, rewrites, IdiomLog.discarding)

  /** …and with the run's IDIOM LOG, which collects what every [[IdiomPhase]] CONSIDERED. Drained
    * here for [[DecisionLog]]'s reason: a reused phase instance would otherwise double-report a
    * translation's candidates across `Determinism.Full`'s two runs. Unconditional over the phase
    * list, so a census phase and its eventual transformer replacement are indistinguishable to it. */
  def runTraced(program: Program, phases: List[Phase], binder: PolicyBinder,
                catalog: balticporter.catalog.CatalogLog, rewrites: RewriteLog,
                idioms: IdiomLog): (Program, DecisionLog) =
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
        phase.cites.clear()     // …and this run's citations only, for the same reason
        phase match { case ip: IdiomPhase => ip.candidates.clear(); case _ => () } // …and its candidates
        val out  = phase.run(prog)
        val next = out.rebuilt(xref = Xref.build(out.units))
        recordPatch(rewrites, phase, prog, next)
        log.recordAll(phase.decisions.drain())
        phase.cites.foreach((id, decl) => catalog.cite(id, decl))
        phase.cites.clear()
        phase match { case ip: IdiomPhase => idioms.recordAll(ip.candidates.drain()); case _ => () }
        if DebugFlags.tracePhases then
          println(s"[balticporter] phase '${phase.name}': ${next.units.size} units, ${next.symbols.all.size} symbols" +
            (if log.isEmpty then "" else s", decisions so far: ${log.size}"))
        dump(DebugFlags.dumpTirAfter, phase.name, "AFTER", next)
        next
    }
    (out, log)

  /** WHICH declarations this phase's rewrite MOVED, derived rather than declared — nothing is
    * recorded where nothing moved, so `rewrite-callsites` needs no maintained phase list. Compares
    * BOTH records of a type (the symbol's `info` and the tree's own `tpt`s), since a phase that
    * rebuilds only the tree is invisible to an `info`-only comparison. A SYMBOL SWAP is invisible to
    * both, deliberately — it looks like a legitimate DROP (`ENGINE-LIMITS.md` K5.6). */
  private def recordPatch(rewrites: RewriteLog, phase: Phase, before: Program, after: Program): Unit =
    val owned   = before.owned & after.owned
    val moved   = owned.filter(id =>
      val infoMoved = (before.symbolOf(id), after.symbolOf(id)) match
        case (Some(b), Some(a)) => b.info != a.info
        case _                  => false
      infoMoved || declaredMoved(before, after, id))
    if moved.nonEmpty then
      rewrites.record(Patch(phase.name, moved, phase match { case r: Rewrite => r.accountedBy; case _ => Set.empty }))

  /** the DECLARED types a definition WRITES DOWN — the record the emitter prints, as opposed to the
    * symbol's `info`. `None` for a definition kind that declares no type of its own (a `ClassDef`
    * has parents rather than a declared type, and a `TypeDef`'s rhs is its bounds). */
  private def declaredTypes(d: Definition): Option[(TypeRepr, List[TypeRepr])] = d match
    case v: Tree.ValDef => Some(v.tpt.tpe -> Nil)
    case m: Tree.DefDef => Some(m.returnTpt.tpe -> m.paramss.flatten.map(_.tpt.tpe))
    case _              => scala.None

  /** …compared across the phase, for one owned symbol. Present-on-BOTH-sides only, for `recordPatch`'s
    * own reason: a declaration that is gone was dropped, not retyped. */
  private def declaredMoved(before: Program, after: Program, id: SymId): Boolean =
    (before.definitionOf(id).flatMap(declaredTypes), after.definitionOf(id).flatMap(declaredTypes)) match
      case (Some(b), Some(a)) => b != a
      case _                  => false

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
  // A pass that only needs to LOOK still walks the complete traversal below rather than a
  // hand-rolled recursion, which stops wherever its author forgot (CLAUDE.md §3). `f` sees every
  // TERM bottom-up; a scan needing DEFINITIONS implements `Phase` directly instead.

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

  /** every `Tree.ClassDef` a unit CONTAINS — itself, nested types, enum-constant bodies, and a
    * METHOD-LOCAL class (JLS 14.3, catalog `JS-C30`) that a hand-rolled `cd.body.foreach` recursion
    * misses (CLAUDE.md §3's one-node-short defect). Uses `transformClassDef`, not a term `scanner`,
    * to stay complete as node kinds are added. Bottom-up, like every other scan here. */
  def allClassDefs(t: Tree.ClassDef)(using Program): List[Tree.ClassDef] =
    val acc = List.newBuilder[Tree.ClassDef]
    val ph = new Phase:
      def name: String = "standard-traversal/scan-classes"
      override def transformClassDef(x: Tree.ClassDef)(using Program): Tree.ClassDef = { acc += x; x }
    mapClassDef(ph, t)
    acc.result()

  /** …and every ANONYMOUS class body a unit contains, paired with the `new` that names its parent.
    * `Tree.AnonClass` is not a `Tree.ClassDef` (no `parents` of its own — java writes the supertype
    * at the `new`), so [[allClassDefs]] cannot reach it. `OverrideGraph.Collector` already treats
    * the pair as one node; this exposes the same derivation for scans (CLAUDE.md §3). The `TypeTree`
    * returned is the `new`'s own supertype with its arguments. */
  def allAnonClasses(t: Tree.ClassDef)(using Program): List[(Tree.AnonClass, TypeTree)] =
    val acc = List.newBuilder[(Tree.AnonClass, TypeTree)]
    val ph = new Phase:
      def name: String = "standard-traversal/scan-anon-classes"
      override def transformNew(x: Tree.New)(using Program): Term = { x.anon.foreach(a => acc += (a -> x.tpt)); x }
    mapClassDef(ph, t)
    acc.result()

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
      // …the ARGUMENTS are skipped where the phase says this constructor's are reified by someone
      // else (`Phase.preservesTypeArgsOf`, K20). Asked of the UNMAPPED constructor: the question is
      // about the carrier the java named, and the head is still mapped either way — a rename must
      // reach `TypeReference` itself even where nothing inside it may move.
      case TypeRepr.AppliedType(tc, as) =>
        TypeRepr.AppliedType(mapType(ph, tc),
                             if ph.preservesTypeArgsOf(tc) then as else as.map(mapType(ph, _)))
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

  /** Route every TYPE a symbol record carries through `ph.transformType` — its `info` AND its
    * annotations' types (`Symbol.annotations`, rendered from `Annot.tpe`) — or a retyping phase
    * leaves an annotation naming the old type, silently (`ENGINE-LIMITS.md` M5.8). A symbol the
    * program does not OWN is skipped: its signature is a class-file fact no phase may move (§4.56,
    * [[Program.owned]]). */
  def mapSymbols(ph: Phase, tbl: SymbolTable)(using p: Program): SymbolTable =
    mapSymbols(ph, tbl, _ => true)

  /** …restricted to the symbols a phase is SCOPED to. An overload rather than a second fold, so a
    * `RuleScope`-taking phase (CLAUDE.md §1) can hold back a declaration's `info` exactly as it
    * holds back its tree. Default predicate is the pre-scope behaviour; every existing caller
    * keeps it. */
  def mapSymbols(ph: Phase, tbl: SymbolTable, keep: Symbol => Boolean)(using p: Program): SymbolTable =
    tbl.all.foldLeft(tbl)((t, s) =>
      if !p.owns(s.id) || !keep(s) then t
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
        ph.transformLambda(x.copy(params = x.params.map(mapValDef(ph, _)), body = mapTerm(ph, x.body),
          tpe = mapType(ph, x.tpe), resultTpt = x.resultTpt.map(mapTpt(ph, _))))
      case x: Tree.Block =>
        ph.transformBlock(x.copy(stats = x.stats.map(mapStat(ph, _)), expr = mapTerm(ph, x.expr), tpe = mapType(ph, x.tpe)))
      case x: Tree.Assign => x.copy(lhs = mapTerm(ph, x.lhs), rhs = mapTerm(ph, x.rhs), tpe = mapType(ph, x.tpe),
        compound = x.compound.map((op, n) => (op, n.map(mapType(ph, _)))))
      case x: Tree.If =>
        x.copy(cond = mapTerm(ph, x.cond), thenp = mapTerm(ph, x.thenp), elsep = mapTerm(ph, x.elsep), tpe = mapType(ph, x.tpe))
      case x: Tree.Typed    => x.copy(expr = mapTerm(ph, x.expr), tpt = mapTpt(ph, x.tpt), tpe = mapType(ph, x.tpe))
      case x: Tree.Repeated => x.copy(elems = x.elems.map(mapTerm(ph, _)), tpe = mapType(ph, x.tpe))
      case x: Tree.Spread   => x.copy(expr = mapTerm(ph, x.expr), tpe = mapType(ph, x.tpe))
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
      case x: Tree.Yield    => x.copy(value = mapTerm(ph, x.value), tpe = mapType(ph, x.tpe))
      case x: Tree.TypePattern => x.copy(tpt = mapTpt(ph, x.tpt), tpe = mapType(ph, x.tpe))
      case x: Tree.RecordPattern =>
        x.copy(tpt = mapTpt(ph, x.tpt), patterns = x.patterns.map(mapTerm(ph, _)), tpe = mapType(ph, x.tpe))
      case x: Tree.BindPattern => x.copy(tpe = mapType(ph, x.tpe))
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
      // THE MARKER (`DESIGN.md` §6.2). Recursed into and REBUILT: every phase's hooks must reach
      // INSIDE an approximation since a later whole-program transform might fix it. An unmatching
      // phase leaves it alone (marker-preserved, code-untouched); erasing one needs a deliberate
      // match plus a replacement, which is what lets `MarkerCheck` tell a discharge from a deletion.
      case x: Tree.Unportable => x.copy(inner = mapTerm(ph, x.inner), tpe = mapType(ph, x.tpe))
    ph.transformTerm(rebuilt)
