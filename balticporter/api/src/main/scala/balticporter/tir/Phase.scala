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

  // ---- catalog citation (`DESIGN.md` §2.8: the THIRD discharge surface) ----

  /** What CATALOG ROWS this phase decided, and at which declarations, for the run in progress.
    *
    * Owned and drained exactly as [[decisions]] is, and for the same reason: a phase instance
    * reused across two translations must not report the first run's citations as the second's. */
  private[tir] val cites = collection.mutable.ListBuffer.empty[(balticporter.catalog.DiffId, String)]

  /** CITE a catalog row at a DECLARATION this phase decided about.
    *
    * Deliberately weaker than the frontend's obligation, and reported apart from it: a phase does
    * not walk one node kind, so nothing can assert that it *should have* considered a difference at
    * a declaration it never visited. What a citation buys is the other half of the coverage
    * question — `catalog(unreached)` can be true of phases as well as of lowering arms.
    *
    * One row per DECLARATION, never one per expression, which is the granularity `Decision` already
    * uses (`CLAUDE.md` §5.1): a site-level rewrite is visible in the diff a reader is holding, and
    * what the diff cannot say is which difference produced it. */
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
    * application and carries the arguments across verbatim (`ENGINE-LIMITS.md` K20).
    *
    * ==Why the traversal owes a hook at all==
    * `transformType` is applied at every constituent and is POSITION-BLIND, which is exactly right
    * for a type that is a claim about a SLOT. A generic type argument is not always one: it
    * survives erasure into the class file's generic signature, and a third party reads it back at
    * run time and CONSTRUCTS from it — jackson's `TypeReference<Map<String,Object>>`, Gson's
    * `TypeToken<T>`, Guice's `Key<T>`/`TypeLiteral<T>`, and `java.lang.Class<T>`, the one java
    * itself guarantees. Retyped there, the port is not describing a slot; it is telling the
    * framework to instantiate a type that has no constructor:
    * `Cannot construct instance of scala.collection.mutable.Map`.
    *
    * There is no NODE to translate — the occurrence is a type argument in a declaration, so a phase
    * that walked every `InstanceOf` and every `Typed` (K18's two reified positions, both written in
    * the source) visits nothing — and no instrument sees it: the port compiles, every check count is
    * flat, and the evidence is an exception from someone else's library. So the answer has to be at
    * the traversal, which is the one place that knows it is about to descend into an argument.
    *
    * ==…and why it is a HOOK and not a table the traversal owns==
    * Whether a carrier's argument may move depends on what the phase is DOING to it, not on the
    * carrier. A RETYPING phase moves a type to a different runtime class, so the argument must stay
    * java's; a RENAMING phase moves the port's OWN class to the name the port emits it under, and
    * `Class[ssg.liquid.Foo]` is then exactly right — the emitted class IS the runtime class. A
    * single engine-wide carrier list would break the second to fix the first.
    *
    * The default is `false`, so a phase that declares nothing traverses as it always did; WHICH
    * carriers a retyping phase names is §1(b) policy on that phase. Called with the UNMAPPED type
    * constructor, before either it or its arguments are visited. */
  def preservesTypeArgsOf(tc: TypeRepr)(using Program): Boolean = false

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
    *
    * ==DECLARATION ORDER IS THE TIE-BREAK GLOBALLY, and a FIFO could not give that==
    * "Stable by declaration order on ties" is the contract every port relies on — a phase whose name
    * no `runsBefore` can spell places itself by writing it earlier in `surface` (`bean-properties`'
    * own doc says exactly this about the opaque-type phase). Kahn's algorithm with a QUEUE does not
    * deliver it: a node becomes ready when its last predecessor is processed, so a CONSTRAINED phase
    * that only just became ready is appended AFTER an unconstrained phase declared later, and the
    * declaration order between the two is silently inverted.
    *
    * That is not a corner: it is what happens whenever a phase is ADDED with a `runsBefore` edge
    * onto an existing one. Measured — adding two edge-bearing phases at the HEAD of a two-phase
    * pipeline (`collections`, then `mutable-params`) inverted those two, because `mutable-params`
    * had no edges and sat in the initial ready set while `collections` had to wait for the new
    * phases. The added phases rewrote NOTHING and the port's `collection-boundary` count still moved
    * 22 -> 20 with two member digests, which is the worst shape a defect can have here: an inert
    * phase that is inert on the TREE and not on the PIPELINE, with the evidence landing on a check
    * that has nothing to do with it.
    *
    * So `ready` is a MIN-HEAP on declaration index rather than a queue. The result is the unique
    * topological order that is lexicographically smallest by declaration index — i.e. every phase
    * runs as early as its constraints allow, in the order the port wrote them — which is what the
    * sentence above always claimed and what a caller adding a phase is entitled to assume.
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
    runTraced(program, phases, binder, balticporter.catalog.CatalogLog.discarding)

  /** …and with the run's CATALOG LOG, so a phase's `cite` reaches the same log the frontend's
    * consults do. One log per run, three surfaces feeding it (`DESIGN.md` §2.8) — three per-surface
    * artifacts would answer three narrower questions and never "was this row reached at all". */
  def runTraced(program: Program, phases: List[Phase], binder: PolicyBinder,
                catalog: balticporter.catalog.CatalogLog): (Program, DecisionLog) =
    runTraced(program, phases, binder, catalog, RewriteLog.discarding)

  /** …and with the run's REWRITE LOG, which records what each phase MOVED.
    *
    * The record is taken HERE and not by the phases, and that is the point of it. A phase declares
    * which check lane counts its residue ([[Rewrite.accountedBy]]) and is not asked what it retyped,
    * because the pipeline can SEE that: it holds the symbol table on both sides of every phase, so
    * "which owned declarations did this phase's `info` rewrite move" is a comparison rather than a
    * claim. A phase cannot under-report its own reach to make `rewrite-callsites` quiet, and a phase
    * that stops maintaining a hand-written set cannot exist.
    *
    * Owned symbols only, and present-on-BOTH-sides only. A symbol the phase MINTED has no `info` to
    * have moved, and an external's signature is a fact about a class file that no phase may move at
    * all (§4.56, and `mapSymbols` already refuses to walk one) — counting either would make the set
    * mean something other than "declarations this rewrite retyped".
    */
  def runTraced(program: Program, phases: List[Phase], binder: PolicyBinder,
                catalog: balticporter.catalog.CatalogLog, rewrites: RewriteLog): (Program, DecisionLog) =
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
        val out  = phase.run(prog)
        val next = out.rebuilt(xref = Xref.build(out.units))
        recordPatch(rewrites, phase, prog, next)
        log.recordAll(phase.decisions.drain())
        phase.cites.foreach((id, decl) => catalog.cite(id, decl))
        phase.cites.clear()
        if DebugFlags.tracePhases then
          println(s"[balticporter] phase '${phase.name}': ${next.units.size} units, ${next.symbols.all.size} symbols" +
            (if log.isEmpty then "" else s", decisions so far: ${log.size}"))
        dump(DebugFlags.dumpTirAfter, phase.name, "AFTER", next)
        next
    }
    (out, log)

  /** WHICH declarations this phase's rewrite MOVED, derived rather than declared — see the
    * `runTraced` overload above for why the pipeline takes this record and the phase does not.
    *
    * Nothing is recorded where nothing moved: a phase that retyped no declaration has no seam to
    * account for, so it owes no lane and produces no row. That is what makes `rewrite-callsites` a
    * question about RETYPING phases without anybody having to maintain a list of which phases those
    * are — the list is derived from what each one did, on every run.
    *
    * ==A declaration's type has TWO records and a phase may move either==
    * The symbol's `info` and the DECLARATION IN THE TREE (`ValDef.tpt`, `DefDef.returnTpt` and its
    * parameters' `tpt`s) say the same thing, and `StandardTraversal` routes both through
    * `transformType` — which is why every retyping phase written so far moved both and an
    * `info`-only comparison saw all of them. It is not a property of the IR: a phase that overrides
    * `transformValDef`/`transformDefDef` and rebuilds the `tpt` moves only the tree, and the tree is
    * what the EMITTER prints. Read through `info` alone that phase produces no row, no
    * `Unaccounted` finding, and a `rewrite-callsites` lane reporting a confident zero about a phase
    * that retyped every declaration in the program — the exact silence this check exists to replace,
    * arriving through the half of the question it was not asking.
    *
    * The tree half costs nothing beyond the `info` half: `Xref.build` already indexes every
    * definition on every phase boundary (`runTraced` rebuilds it), so this is a map lookup per owned
    * symbol and not a second traversal.
    *
    * ==What stays out of reach, stated rather than counted as a zero==
    * A SYMBOL SWAP — a phase that replaces `ValDef(s1)` with `ValDef(s2)` — is invisible to both
    * halves, and deliberately: `s1`'s declaration is simply gone from `after`, which is also what a
    * legitimate DROP looks like (`Substitutions`, a policy that removes a member), and a phase that
    * dropped a declaration has not retyped one. Reporting the shape would put a false `Unaccounted`
    * row on every phase that removes anything. `ENGINE-LIMITS.md` K5.6 carries the residue. */
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

  /** every `Tree.ClassDef` a unit CONTAINS — itself, its nested types, the types inside an enum
    * constant's body, and a METHOD-LOCAL class standing in a member's block (JLS 14.3, catalog
    * `JS-C30`).
    *
    * The hand-rolled `cd.body.foreach { case c: Tree.ClassDef => scan(c) }` recursions this exists
    * to replace each stop at the class BODY, which is one node short of java: a local class is a
    * `BlockStatement`, not a type member, so every one of them answers "no nested types here" about
    * a type the program declares. That is CLAUDE.md §3's defect shape — a hand-rolled traversal
    * that stops one node short — and it is invisible for as long as the frontend refuses the
    * construct, because the answer is then right by accident.
    *
    * `transformClassDef` rather than the term `scanner`, for the reason stated above it: a scan
    * that needs DEFINITIONS overrides the definition hook, and overriding one hook is what keeps
    * this complete as node kinds are added. Bottom-up, like every other scan here. */
  def allClassDefs(t: Tree.ClassDef)(using Program): List[Tree.ClassDef] =
    val acc = List.newBuilder[Tree.ClassDef]
    val ph = new Phase:
      def name: String = "standard-traversal/scan-classes"
      override def transformClassDef(x: Tree.ClassDef)(using Program): Tree.ClassDef = { acc += x; x }
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
      // THE MARKER (`DESIGN.md` §6.2). Recursed into and REBUILT, exactly like the comment wrapper
      // above and for a sharper reason: every phase's hooks must reach INSIDE an approximation,
      // because the whole point of keeping the marked tree is that a later whole-program transform
      // might be what fixes it. A phase that matches for a specific shape simply fails to match a
      // wrapped one and leaves it alone — so the safe default is marker-preserved, code-untouched,
      // and ERASING a marker takes a deliberate match on `Tree.Unportable` plus a replacement.
      // That asymmetry is the design: a discharge is `state = Resolved`, a deletion is a defect,
      // and `MarkerCheck` can only tell them apart because the two look different HERE.
      case x: Tree.Unportable => x.copy(inner = mapTerm(ph, x.inner), tpe = mapType(ph, x.tpe))
    ph.transformTerm(rebuilt)
