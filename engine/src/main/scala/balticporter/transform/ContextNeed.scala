package balticporter.transform

import balticporter.tir.*

/** THE CLOSURE — which declarations must be able to supply the context, and where the closure stops.
  *
  * A DIRECTED reachability over five edge kinds (DESIGN.md §8.4), computed once per holder and
  * exposed as a value so a spec can pin the edge set itself rather than only its result. The edges,
  * and why only one of them is a call-graph fact:
  *
  *   1. [[ContextNeed.Edge.Kind.Seed]] — a body READS a mapped static. Read from the phase's own
  *      record of what it mapped ([[statics]]), never from a name (§4.56).
  *   2. [[ContextNeed.Edge.Kind.Use]] — a declaration uses a threaded one. CALLS and REFERENCES
  *      both, because a `lazy-init` rewrite turns a FIELD into a method whose reads stay written as
  *      references.
  *   3. [[ContextNeed.Edge.Kind.Override]] — the whole override COMPONENT, up and down
  *      ([[OverrideGraph.closureOf]]). Java resolved every virtual call to the DECLARED member, so
  *      threading an implementation without its declaration is a broken `override`, and threading a
  *      declaration without its implementations is a call with no argument. All of a component or
  *      none of it; an ANCHORED component is refused whole and counted.
  *   4. [[ContextNeed.Edge.Kind.Instantiate]] — `new C` where `C`'s constructors took the clause,
  *      and every subclass of such a `C`, whose own `extends` would otherwise have no argument.
  *   5. [[ContextNeed.Edge.Kind.Capture]] — a lexically nested body (an anonymous class, an
  *      enum-constant body) does NOT thread its own signature-frozen method; the need lands on the
  *      ENCLOSING declaration and `summon` inside the nested body resolves that clause. This is what
  *      makes an external-interface SAM body a non-problem in the common case, and it is why the
  *      reads inside 156 anonymous bodies in one corpus library cost no signature at all.
  *
  * ==Why not `FlowPropagation`==
  * That utility is a union-find over SYMMETRIC pure-move edges (*these two must share a type*); this
  * is DIRECTED need (*this one must be able to supply that one*). Bending one into the other either
  * over-unions — threading everything a threaded method merely assigns from — or requires exactly
  * this walk anyway. What IS reused is its shape: [[edges]] is exposed separately from the growth.
  *
  * ==Over-approximation is benign and PRICED==
  * Threading a component member that never reads the holder costs one trailing anonymous clause at
  * the declaration, NOTHING at the call sites, and one extra reference argument at run time. Each
  * such member's decision says `via=override-component`, so a reader of the emitted file can see why
  * a parameter it never uses is there.
  */
final class ContextNeed(
    program: Program,
    graph: OverrideGraph,
    holder: ContextHolder,
    /** the mapped statics: symbol → the access path on the context. The phase's OWN record. */
    val statics: Map[SymId, String],
    /** traits the manifest allows to become abstract classes. */
    promoteAllowed: Set[SymId],
    seam: (ContextSeamCheck.Kind, String, String, String, Origin, SymId) => Unit,
    refuse: (SymId, String) => Unit,
    /** the `sites` entries that BOUND: the policy key → the symbols it named. The phase's OWN record
      * of what the binder resolved, so a `lazy-init` entry can name a site NO READ reaches — which
      * is `ENGINE-LIMITS.md` CT6's second face. Empty is the pre-CT6 code path. */
    boundSites: Map[String, List[SymId]] = Map.empty,
    /** the `selfSupplied` entries that BOUND: the TYPE a framework instantiates → the policy key
      * that said so. Such a type is threaded in every way except the one that changes its
      * signature — `ENGINE-LIMITS.md` CT7's third answer. Empty is the pre-CT7 code path. */
    selfSupplied: Map[SymId, String] = Map.empty,
):
  import ContextNeed.*
  import GlobalsToImplicitsTransform.ReadPlan

  private given Program = program

  /** the `sites` keys something in this run decided through — FIRST, because [[policyFor]] writes to
    * it while [[deferrals]] is still being built and a `val` declared later is `null` there. */
  private val firedS = collection.mutable.Set.empty[String]

  // -------------------------------------------------------------------------
  // 1. resolution — where does a read at this site attach?
  // -------------------------------------------------------------------------

  /** every read of a mapped static: `(static, site origin, enclosing declaration)`.
    *
    * Keyed by `(symbol, origin)` and not by node identity, because [[StandardTraversal]] rebuilds
    * every node on the way down (`copy(tpe = …)`) and identity does not survive it. Two reads of one
    * static at one file/line/column are the same read. */
  val reads: List[(SymId, Origin, SymId)] =
    statics.keys.toList.flatMap(s => program.usages(s).collect {
      case Usage(UsageKind.TermRef, site, enc) => (s, site.origin, enc)
    }).distinct.sortBy((s, o, _) => (o.javaPath, o.line, o.col, s.raw))

  private val siteCache = collection.mutable.Map.empty[SymId, Site]

  /** The climb: from the declaration a read is IN, to the declaration that can carry a clause.
    *
    * Every step is structural. A LEXICALLY NESTED type (an anonymous-class body, an enum-constant
    * body) has no `ClassDef` of its own and its owner is the declaration it was written inside, so
    * the climb continues through it and the read CAPTURES. A class initialiser and a field
    * initialiser have no signature at all and stop the climb: those are the two sites the
    * predecessor mistranslated in silence — one by seeding a `<clinit>` that then lost its parameter
    * at emission, the other by never seeding a field initialiser and never rewriting it either. */
  def siteOf(from: SymId): Site = siteCache.getOrElseUpdate(from, climb(from, captured = false, 64))

  /** the climb AS IT WAS BEFORE ANY DEFERRAL — the one question the deferral scan may ask.
    *
    * [[deferrals]] is computed from the sites the closure cannot reach, and a deferred field then
    * BECOMES a reachable one, so a deferral-aware climb consulted while the plan is being built is
    * a cycle. It is also uncached on purpose: the answer it gives is the pre-deferral one, and
    * leaving it in [[siteCache]] would hand it to the growth as well. */
  private def preSiteOf(from: SymId): Site = climb(from, captured = false, 64, deferAware = false)

  @annotation.tailrec
  private def climb(s: SymId, captured: Boolean, fuel: Int, deferAware: Boolean = true): Site =
    if s == SymId.None || fuel <= 0 then Site.Boundary(s, "it is outside any declaration")
    // a DEFERRED static is no longer a field whose initialiser runs at class initialisation: the
    // rewrite has made it a `def` over a cache that takes the clause, on the field's OWN symbol. A
    // climb that still called it a boundary would report a seam against the exit the `sites` policy
    // just took, and refuse to thread the very body the deferral moved.
    else if deferAware && deferredFields.contains(s) then Site.Method(s, captured)
    else program.symbolOf(s) match
      case scala.None => Site.Boundary(s, "it is outside any declaration")
      case Some(sym) =>
        if isType(s) then
          if isDeclaredClass(s) then
            if holder.attach == ContextAttach.Class then Site.Cls(s, captured)
            else Site.Boundary(s, "it is a class body statement and `attach = method`")
          // an anonymous-class or enum-constant body: its members' signatures are fixed by what they
          // implement, so the need lands OUTSIDE and the body captures it lexically.
          else climb(anonHome.getOrElse(s, sym.owner), captured = true, fuel - 1, deferAware)
        // …and a MEMBER of such a body is reached from the member, not from the body: the climb has
        // to look UP one level before it decides, or an anonymous `Runnable#run` reads as an
        // ordinary method, gets a clause it may not have (its signature is `Runnable`'s), and the
        // enclosing declaration — the one that can actually supply the context — is never asked.
        else if isType(sym.owner) && !isDeclaredClass(sym.owner) then
          climb(sym.owner, captured = true, fuel - 1, deferAware)
        else if PolicyBinder.isExecutable(sym.info) then
          if sym.name == ClinitName then Site.Boundary(s, "a class initialiser has no signature")
          else if sym.name == InitBlockName then
            if holder.attach == ContextAttach.Class && isDeclaredClass(sym.owner) then Site.Cls(sym.owner, captured)
            else Site.Boundary(s, "an instance initialiser block has no signature and `attach = method`")
          else if holder.attach == ContextAttach.Class && isDeclaredClass(sym.owner) &&
                  (sym.name == CtorName || !sym.flags.isStatic) then Site.Cls(sym.owner, captured)
          else Site.Method(s, captured)
        else
          // a FIELD, a parameter or a method-LOCAL. A local's owner is its METHOD, so the climb
          // continues; a field's owner is a TYPE, and a field initialiser has no signature.
          program.symbolOf(sym.owner) match
            case Some(o) if PolicyBinder.isExecutable(o.info) => climb(sym.owner, captured, fuel - 1, deferAware)
            case Some(_) if isType(sym.owner) =>
              if !sym.flags.isStatic && holder.attach == ContextAttach.Class && isDeclaredClass(sym.owner) then
                Site.Cls(sym.owner, captured)
              else if sym.flags.isStatic then
                Site.Boundary(s, "a static field's initialiser runs at class initialisation, before " +
                  "anything could pass it a context")
              else Site.Boundary(s, "a field initialiser has no signature and `attach = method`")
            case _ => Site.Boundary(s, "it is outside any declaration")

  /** an anonymous-class body → the DECLARATION it was WRITTEN INSIDE.
    *
    * The frontend interns an anonymous class with its enclosing CLASS as owner, because that is
    * where its emitted name comes from (`Outer$1`) — so the owner chain reaches the class and loses
    * the method, and a capture landing on the class would be a boundary under `attach = method` and
    * the wrong constructor under `attach = class`. The xref does hold the lexical home: every
    * `new T(){ … }` is a usage of `T` whose SITE is the `New` node carrying the body and whose
    * `enclosing` is the declaration it was written in. Read from there, so nothing has to re-walk
    * the tree with its own notion of "where am I" (CLAUDE.md §3).
    *
    * The KIND of that usage is not asked for, and that is `ENGINE-LIMITS.md` CT6: `Xref.walkType`'s
    * `AppliedType` arm re-labels `Instantiate` as `Tycon`, so an anonymous subclass of a GENERIC
    * parent — `new Pool<Cell>(){ … }` — had no lexical home at all and every capture inside it
    * climbed to the enclosing CLASS instead. The home comes from the NODE (which body this `New`
    * carries), so the recorded kind decides nothing here. */
  private val anonHome: Map[SymId, SymId] =
    program.referenced.toList.flatMap(program.usages).collect {
      case Usage(_, n: Tree.New, enc) if n.anon.isDefined && enc != SymId.None =>
        n.anon.get.symbol -> enc
    }.toMap

  /** Is this usage of `c` a CONSTRUCTION of `c`? — `ENGINE-LIMITS.md` CT6's first face.
    *
    * `Xref.walkType(tpt.tpe, UsageKind.Instantiate, n)` at a [[Tree.New]] reaches the constructed
    * class as `Tycon` whenever that class takes type parameters, because the `AppliedType` arm
    * re-labels the kind it was called with — and the frontend applies a RAW `new Cell()` too. The
    * instantiate edge of DESIGN.md §8.4 was therefore absent for EVERY generic class: no threading,
    * no [[impose]], and so no seam either, which is a boundary the engine cannot see rather than one
    * it refuses (CLAUDE.md §1).
    *
    * The fix is HERE and not in `Xref`. `UsageKind` is a shared index read by the portability check,
    * the rewrite trace and the external-surface walk; re-labelling that arm is its own change with
    * its own thirteen-port measure cycle. A usage whose SITE is a `New` is an instantiation of
    * whatever that `New` CONSTRUCTS — a structural fact about the node this phase is holding, never
    * a conclusion drawn from a recorded name (§4.56).
    *
    * Reading the node is also what keeps it EXACT. A kind-blind "any usage at a `New` site" would
    * make `new Pool<Cell>()` an instantiation of `Cell`, which it is not: `Cell` is named there as a
    * TYPE ARGUMENT, and constructing a `Pool` constructs no `Cell`. Off a `New` the recorded kind is
    * still the answer — `Tree.NewArray` records `Instantiate` for its element type and has no
    * constructed head to read. */
  private def instantiates(u: Usage, c: SymId): Boolean = u.site match
    case n: Tree.New => constructedBy(n) == c
    case _           => u.kind == UsageKind.Instantiate

  /** the class a `new` constructs: the head of the type it was WRITTEN at, with any application
    * stripped. `SymId.None` where there is no head to read. */
  private def constructedBy(n: Tree.New): SymId = headOf(n.tpt.tpe)

  @annotation.tailrec
  private def headOf(t: TypeRepr): SymId = t match
    case TypeRepr.AppliedType(tycon, _) => headOf(tycon)
    case TypeRepr.TypeRef(_, s)         => s
    case _                              => SymId.None

  private def isType(s: SymId): Boolean = graph.types.contains(s)
  private def isDeclaredClass(s: SymId): Boolean =
    program.definitionOf(s).exists(_.isInstanceOf[Tree.ClassDef])

  private def inScope(s: SymId): Boolean =
    program.symbolOf(s).forall(x => holder.scope.includes(program, x))

  // -------------------------------------------------------------------------
  // 2. the DEFERRED-INIT plan — read BEFORE the growth, because it creates seeds
  // -------------------------------------------------------------------------

  /** the statics whose initialisation a `sites` policy asked to move out of an initialiser.
    *
    * ==The trigger is the POLICY, not a read — `ENGINE-LIMITS.md` CT6's second face==
    * This used to be derived from [[reads]] alone: a read of a MAPPED STATIC whose site resolved to
    * a `lazy-init` boundary. Every seam the phase draws tells its reader to *give the site a `sites`
    * policy* — and for the shape that most needs it, an initialiser that CONSTRUCTS a now-threaded
    * type, there was no read of a mapped static anywhere in it, so the entry could name nothing. It
    * BOUND (it named a real member), it changed no emitted byte, and `policy` stayed at its floor:
    * a policy entry that is accepted, does nothing, and is invisible to every check in the run.
    *
    * So the candidates are the entries themselves, resolved through [[boundSites]], with the
    * read-derived set kept beside them — it is a subset by construction (a read boundary reaches
    * `lazy-init` only through a `sites` entry naming it) but it also covers the keys the binder
    * refuses, `<clinit>` above all, which is an engine-minted member policy may still name here. */
  val deferrals: List[Deferral] = lazyInitSubjects.flatMap(planDeferral)

  /** the deferred fields, as [[climb]] reads them. Derived from [[deferrals]] and therefore
    * initialised after it — [[preSiteOf]] is what the plan itself is allowed to ask. */
  private val deferredFields: Set[SymId] = deferrals.map(_.field).toSet

  /** every subject a `lazy-init` entry could be about, in a deterministic order. */
  private def lazyInitSubjects: List[SymId] =
    val fromReads = reads.flatMap((_, _, enc) => preSiteOf(enc) match
      case Site.Boundary(sub, _) if policyFor(sub) == ContextSite.LazyInit => Some(sub)
      case _                                                              => scala.None)
    val fromPolicy = holder.sites.toList.collect { case (k, ContextSite.LazyInit) => k }
      .sorted.flatMap(k => boundSites.getOrElse(k, Nil))
    (fromReads ++ fromPolicy).distinct

  /** the per-site policy for a boundary subject, falling back to the holder's default. */
  private def policyFor(subject: SymId): ContextSite =
    val key = program.symbolOf(subject).map(_.fullName)
    key.flatMap(holder.sites.get) match
      case Some(s) =>
        // A `lazy-init` entry is judged by its OUTCOME, not by this lookup: the lookup is how the
        // deferral scan ASKS the question, so counting it here would mark the entry fired before
        // anything was planned — which is exactly the blindness CT6 measured. The other two decide
        // at this call and nowhere else.
        if s != ContextSite.LazyInit then key.foreach(firedS += _)
        s
      case scala.None => holder.boundary match
        case ContextBoundary.Refuse         => ContextSite.Refuse
        case ContextBoundary.ResidualGlobal => ContextSite.ResidualGlobal

  private def planDeferral(subject: SymId): List[Deferral] =
    val key = program.symbolOf(subject).map(_.fullName).getOrElse("")
    val out = program.definitionOf(subject) match
      case Some(d: Tree.DefDef) => fromInitialiser(d, key)
      case Some(v: Tree.ValDef) => fromField(v, key)
      case _                    => Nil
    if out.nonEmpty then firedS += key
    out

  /** a CLASS INITIALISER: every assignment in it to a static of its own owner. */
  private def fromInitialiser(d: Tree.DefDef, key: String): List[Deferral] =
    val owner = program.symbolOf(d.symbol).map(_.owner).getOrElse(SymId.None)
    statementsOf(d.rhs).flatMap {
      case t: Term => Tree.uncomment(t) match
        case Tree.Assign(lhs, rhs, _, _) =>
          lhsSym(lhs)
            .filter(f => program.symbolOf(f).exists(x => x.flags.isStatic && x.owner == owner))
            .filter(_ => needsContext(rhs))
            .map(f => Deferral(d.symbol, f, rhs, key))
        case _ => scala.None
      case _ => scala.None
    }

  /** a STATIC FIELD CARRYING ITS OWN INITIALISER — the shape no read reaches.
    *
    * `static final Pool<Cell> cellPool = new Pool<Cell>(){ … new Cell() … }` is a boundary because a
    * static initialiser runs at class initialisation, before anything could pass it a context; it
    * names no mapped static, so the read-derived trigger never saw it. There is no `<clinit>` to
    * strip here — the ValDef itself is what [[DeferredInit]] replaces — which is why the deferral's
    * `clinit` is [[SymId.None]].
    *
    * STATIC only: the cache pair [[DeferredInit]] mints is static, and an instance field under
    * `attach = class` is not a boundary at all. A `sites` entry naming an instance field selects no
    * site and is reported as such rather than silently doing something else. */
  private def fromField(v: Tree.ValDef, key: String): List[Deferral] =
    if !program.symbolOf(v.symbol).exists(_.flags.isStatic) then Nil
    else v.rhs.filter(needsContext).map(rhs => Deferral(SymId.None, v.symbol, rhs, key)).toList

  /** Does this initialiser reach the context AT ALL?
    *
    * Two ways, and the second is CT6's: it READS a mapped static, or it CONSTRUCTS a type this
    * program declares — whose constructors the closure may thread. The second is an
    * over-approximation and cannot be anything else here: the deferral plan is read BEFORE the
    * growth (it creates seeds), so `threadedClasses` does not exist yet, and computing it twice to
    * refine this would report every boundary the first pass drew and then unreport it. The gate that
    * makes the approximation safe is that the port had to NAME this site: `lazy-init` is per-site
    * opt-in, a `DeferredInit` decision, a porter note and a counted seam, never a default. */
  private def needsContext(t: Term): Boolean = readsHolder(t) || constructsOwned(t)

  private def constructsOwned(t: Term): Boolean =
    StandardTraversal.scanTerm(t, false) { (acc, x) =>
      acc || (x match
        case n: Tree.New => constructedBy(n) != SymId.None && program.owns(constructedBy(n))
        case _           => false)
    }

  private def statementsOf(rhs: Option[Term]): List[Statement] = rhs.map(Tree.uncomment).toList.flatMap {
    case b: Tree.Block => b.stats :+ b.expr
    case t             => List(t)
  }

  private def lhsSym(t: Term): Option[SymId] = Tree.uncomment(t) match
    case Tree.Ident(s, _, _)     => Some(s)
    case Tree.Select(_, s, _, _) => Some(s)
    case _                       => scala.None

  private def staticIn(t: Term): List[(SymId, Origin)] =
    StandardTraversal.scanTerm(t, List.empty[(SymId, Origin)]) { (acc, x) =>
      x match
        case Tree.Ident(s, _, o) if statics.contains(s)     => (s -> o) :: acc
        case Tree.Select(_, s, _, o) if statics.contains(s) => (s -> o) :: acc
        case _                                              => acc
    }

  private def readsHolder(t: Term): Boolean = staticIn(t).nonEmpty

  /** the read sites the deferral moved into a method that DOES take a clause. */
  private val deferredReads: Set[(SymId, Origin)] = deferrals.flatMap(d => staticIn(d.rhs)).toSet

  /** WHICH `sites` entries this run's decisions actually turned on — the input to the phase's
    * dead-binding report (`ENGINE-LIMITS.md` CT6).
    *
    * A `lazy-init` entry counts as fired iff it produced a [[Deferral]]; the other two count when
    * [[policyFor]] resolved a boundary through them. Read it AFTER [[readPlan]] has been forced, or
    * the residual-global and refuse entries have not been consulted yet. */
  def firedSites: Set[String] = firedS.toSet

  // -------------------------------------------------------------------------
  // 3. the growth
  // -------------------------------------------------------------------------

  private val edgeLog   = collection.mutable.ListBuffer.empty[Edge]
  private val methods   = collection.mutable.LinkedHashSet.empty[SymId]
  private val classes   = collection.mutable.LinkedHashSet.empty[SymId]
  private val selfS     = collection.mutable.LinkedHashSet.empty[SymId]
  private val frozen    = collection.mutable.LinkedHashSet.empty[SymId]
  private val promotedS = collection.mutable.LinkedHashSet.empty[SymId]
  private val viaMap    = collection.mutable.Map.empty[SymId, String]
  private val scopedS   = collection.mutable.LinkedHashSet.empty[SymId]
  private val work      = collection.mutable.Queue.empty[Node]

  /** every edge the closure took, in the order it took them — pinnable by a spec, so the DERIVATION
    * and not only its result is under test. */
  def edges: List[Edge] = edgeLog.toList

  def threadedMethods: Set[SymId]   = methods.toSet
  def threadedClasses: Set[SymId]   = classes.toSet
  def promoted: Set[SymId]          = promotedS.toSet
  def scopedOut: Set[SymId]         = scopedS.toSet
  def via(s: SymId): Option[String] = viaMap.get(s)

  /** the classes the port declared framework-instantiated that this run REACHED — CT7's third
    * answer, applied. They carry no clause and are not in [[threadedClasses]]; what they carry is a
    * `given` member the emitter fills from the policy's expression. */
  def selfSuppliedClasses: Set[SymId] = selfS.toSet

  /** a class whose body may `summon` the context — it either took the clause or supplies its own.
    * The read plan and the seam report both ask THIS and not [[threadedClasses]], because a read
    * inside a self-supplied class resolves perfectly and is not a residual global. */
  private def supplies(c: SymId): Boolean = classes(c) || selfS(c)

  private def enqueue(n: Node, edge: Edge): Unit =
    edgeLog += edge
    n match
      case Node.M(m) if !methods(m) && !frozen(m)                => work.enqueue(n)
      case Node.C(c) if !classes(c) && !frozen(c) && !selfS(c)   => work.enqueue(n)
      case _                                                     => ()

  /** the seeds and the fixpoint. Order-independent by construction (a set closed under the edges)
    * and cycle-safe (a node is expanded once). */
  def grow(): Unit =
    reads.foreach { (st, at, enc) =>
      if !inScope(enc) then scopedS += enc
      else siteOf(enc) match
        case Site.Method(m, _)   => enqueue(Node.M(m), Edge(Edge.Kind.Seed, st, m, at))
        case Site.Cls(c, _)      => enqueue(Node.C(c), Edge(Edge.Kind.Seed, st, c, at))
        case Site.Boundary(_, _) => ()
    }
    // a deferred static becomes a METHOD whose readers need the context — seed it as one, and the
    // `Use` edge then carries the need to every reader.
    deferrals.foreach(d =>
      enqueue(Node.M(d.field), Edge(Edge.Kind.Seed, d.field, d.field, Decision.originOf(program, d.field))))

    while work.nonEmpty do
      work.dequeue() match
        case Node.M(m) => expandMethod(m)
        case Node.C(c) => expandClass(c)

    // AFTER the fixpoint, because both questions are about the finished closure: whether a
    // self-supplied class's PARENT ended up threaded, and which threaded classes nothing constructs.
    selfS.toList.sortBy(_.raw).foreach(checkSelfSupplied)
    classes.toList.sortBy(_.raw).foreach(warnUnconstructed)

  /** A SELF-SUPPLIED CLASS WHOSE PARENT TOOK THE CLAUSE — the one shape the third answer cannot
    * cover, and it is a hard compile error rather than a lost suite.
    *
    * A `given` member of a class body is in scope for the body. It is NOT in scope in the `extends`
    * clause: the parent constructor runs before this class's own members exist, so `class Suite
    * extends Threaded` has no argument to pass and nothing to pass it from. There is no rewrite that
    * repairs it here — the parent's signature is the base's, not this type's — so it is refused,
    * named, and counted, which is what a boundary the engine cannot fix is owed (CLAUDE.md §1). */
  private def checkSelfSupplied(c: SymId): Unit =
    graph.parentsOf(c).filter(classes).sortBy(_.raw).foreach { p =>
      seam(ContextSeamCheck.Kind.SelfSupplied, fqn(c), selfSupplied.getOrElse(c, holder.holder),
        s"UNSATISFIED: this type takes the context from a `given` member, and its parent " +
          s"`${fqn(p)}` took a constructor clause — a given member is not in scope in an `extends` " +
          "clause, so the super call has no argument. Give the PARENT a `selfSupplied` entry too, " +
          "or scope it out", Decision.originOf(program, c), c)
      refuse(c, s"`${fqn(c)}` is `selfSupplied` and its parent `${fqn(p)}` takes a constructor " +
        "clause: a `given` member cannot supply an `extends` clause's argument")
    }

  /** THE CT7 WARNING — a threaded class NOTHING IN THIS PROGRAM CONSTRUCTS, whose ancestry leaves
    * the program.
    *
    * This is the check the measured loss lacked. Every part of the closure worked: the suite
    * constructed a threaded type, the instantiate edge threaded the suite, and the clause landed on
    * its constructor — while nothing in the program ever instantiates a test suite, because a test
    * RUNNER does, reflectively, and a reflective instantiation cannot supply a `using`. The emitted
    * file compiled at 0 errors with 0 seams and the only evidence was five tests that stopped
    * running.
    *
    * Both halves are structural and neither names a library (§1):
    *
    *   - '''nothing constructs it''' — no `Instantiate` edge into it from an owned declaration, and
    *     no owned DESCENDANT that is constructed either. A subclass that IS constructed supplies the
    *     parent's clause through its own `extends`, so the parent is exercised and is not this.
    *   - '''its ancestry leaves the program''' — a strict ancestor this program does not declare,
    *     other than `java.lang.Object`, which is every class's parent and would make this fire on
    *     the whole port. That is what a framework-constructed type looks like from inside the
    *     program: the framework's own base type is on the classpath and is not ported.
    *
    * It WARNS rather than refuses because the engine cannot distinguish "a framework constructs
    * this" from "your users construct this" — both are external constructions, and the second is an
    * ordinary ported API whose callers pass the given. A refusal would make the second unportable;
    * a silence made the first invisible. */
  private def warnUnconstructed(c: SymId): Unit =
    if selfS(c) || constructedByProgram(c) then return
    val external = graph.externalAncestorsOf(c).filterNot(_ == JavaLangObject).sorted
    if external.isEmpty then return
    program.symbolOf(c).foreach(s => seam(ContextSeamCheck.Kind.UnconstructedThread, s.fullName,
      holder.holder, s"threaded, and NOTHING IN THIS PROGRAM CONSTRUCTS IT, while it extends " +
        s"`${external.head}` which this program does not declare — the shape a framework " +
        "instantiates. A reflective construction cannot supply the clause this class now takes; if " +
        "that is what builds it, add a `selfSupplied` entry naming the expression that yields the " +
        "context", Decision.originOf(program, c), c))

  /** Does anything THIS PROGRAM declares construct `c`, or a descendant of it? */
  private def constructedByProgram(c: SymId): Boolean =
    (c :: graph.descendantsOf(c)).exists(t =>
      program.usages(t).exists(u => instantiates(u, t) && u.enclosing != SymId.None))

  private def expandMethod(m: SymId): Unit =
    if methods(m) || frozen(m) then return
    val component = graph.closureOf(m)
    val members   = if component.members.isEmpty then Set(m) else component.members
    if component.isAnchored then
      val why = component.anchorReason(program).getOrElse("the component is anchored")
      members.foreach { x =>
        frozen += x
        program.symbolOf(x).foreach(s => seam(ContextSeamCheck.Kind.FrozenComponent, s.fullName,
          holder.holder, s"not threaded: $why", Decision.originOf(program, x), x))
      }
      refuse(m, s"the override component of `${fqn(m)}` cannot take a context clause: $why")
      return
    members.toList.sortBy(_.raw).foreach { x =>
      if !frozen(x) then
        if x != m then
          viaMap.getOrElseUpdate(x, "override-component")
          edgeLog += Edge(Edge.Kind.Override, m, x, Decision.originOf(program, x))
        methods += x
        // USE edges: everything that calls or references a threaded declaration must supply it.
        program.usages(x).foreach {
          case Usage(UsageKind.Call | UsageKind.TermRef, site, enc) if enc != SymId.None && enc != x =>
            impose(enc, x, site.origin, Edge.Kind.Use)
          case _ => ()
        }
    }

  private def expandClass(c: SymId): Unit =
    if classes(c) || frozen(c) || selfS(c) then return
    // THE THIRD ANSWER, before anything else this method does (`ENGINE-LIMITS.md` CT7). A class a
    // FRAMEWORK constructs takes the context WITHOUT taking a parameter, so it joins no signature
    // edit and propagates neither down the hierarchy nor to its instantiation sites — its
    // constructors are exactly what java declared, and there is nothing for a `new` to supply.
    // Its BODY still reads the context, which is why this is a resolution and not a refusal: the
    // reads inside it are `ReadPlan.Threaded` and the given member the emitter writes is what they
    // resolve against.
    if selfSupplied.contains(c) then
      selfS += c
      program.symbolOf(c).foreach(s => seam(ContextSeamCheck.Kind.SelfSupplied, s.fullName,
        selfSupplied(c), "not threaded: the port declared this type framework-instantiated, so it " +
          "takes the context from a `given` member of its own rather than from a constructor " +
          "parameter no reflective instantiation could supply",
        Decision.originOf(program, c), c))
      return
    val sym = program.symbolOf(c)
    if !program.owns(c) || !isDeclaredClass(c) then
      frozen += c
      sym.foreach(s => seam(ContextSeamCheck.Kind.FrozenComponent, s.fullName, holder.holder,
        "not threaded: this program does not declare the type, so its constructors are not its to " +
          "re-sign", Decision.originOf(program, c), c))
      return
    if sym.exists(_.flags.isTrait) then
      if promoteAllowed(c) then promotedS += c
      else
        frozen += c
        sym.foreach(s => seam(ContextSeamCheck.Kind.FrozenComponent, s.fullName, holder.holder,
          "a trait cannot carry a constructor clause and its own body needs the context — add it to " +
            "`promoteToClass` to emit it as an abstract class, or move the context-needing member " +
            "onto the implementors", Decision.originOf(program, c), c))
        refuse(c, s"`${fqn(c)}` is a TRAIT whose body needs the context and has no `promoteToClass` entry")
        return
    classes += c
    // DOWN the hierarchy: a subclass of a class whose constructors take the clause must take it too,
    // or its own `extends` has no argument to pass.
    graph.descendantsOf(c).filter(isDeclaredClass).foreach { d =>
      if !classes(d) then viaMap.getOrElseUpdate(d, "subclass-of-threaded")
      enqueue(Node.C(d), Edge(Edge.Kind.Instantiate, c, d, Decision.originOf(program, d)))
    }
    // INSTANTIATE: `new C` needs a context in scope wherever it is written. Read through
    // `instantiates`, which asks the NODE and not the recorded kind — a generic `new` is labelled
    // `Tycon` by the shared index (`ENGINE-LIMITS.md` CT6).
    program.usages(c).foreach { u =>
      if instantiates(u, c) && u.enclosing != SymId.None && u.enclosing != c then
        impose(u.enclosing, c, u.site.origin, Edge.Kind.Instantiate)
    }

  /** impose the need on whatever declaration encloses `enc`, or record the seam if nothing can. */
  private def impose(enc: SymId, from: SymId, at: Origin, kind: Edge.Kind): Unit =
    if !inScope(enc) then scopedS += enc
    else siteOf(enc) match
      case Site.Method(m, cap) =>
        if cap then captured(enc, at)
        if !methods(m) then viaMap.getOrElseUpdate(m, kindVia(kind))
        enqueue(Node.M(m), Edge(kind, from, m, at))
      case Site.Cls(c, cap) =>
        if cap then captured(enc, at)
        if !classes(c) then viaMap.getOrElseUpdate(c, kindVia(kind))
        enqueue(Node.C(c), Edge(kind, from, c, at))
      case Site.Boundary(sub, why) =>
        // A declaration that CANNOT take a clause uses one that requires it. This is the seam the
        // deleted ambient `given` used to hide: with `given T = new T()` in scope it compiled
        // silently and the global was back. It is loud here, and attributable.
        seam(ContextSeamCheck.Kind.ResidualGlobalRead, fqn(sub), holder.holder,
          s"unsuppliable use: this declaration uses `${fqn(from)}`, which now takes a context, and " +
            s"$why — give the site a `sites` policy, or move the use into a declaration the closure " +
            "can reach", at, sub)

  private def kindVia(k: Edge.Kind): String = k match
    case Edge.Kind.Use         => "calls-threaded"
    case Edge.Kind.Instantiate => "instantiates-threaded"
    case Edge.Kind.Override    => "override-component"
    case Edge.Kind.Seed        => "reads-holder"
    case Edge.Kind.Capture     => "captures"

  private val capturedSeen = collection.mutable.Set.empty[(SymId, Origin)]

  /** counted ONCE per site: the growth reaches a captured site through its USE edges and the read
    * plan reaches it again, and a seam counted twice is a number nobody can compare. */
  private def captured(enc: SymId, at: Origin): Unit =
    if capturedSeen.add(enc -> at) then
      edgeLog += Edge(Edge.Kind.Capture, enc, enc, at)
      seam(ContextSeamCheck.Kind.CapturedContext, fqn(enc), holder.holder,
        "the read is inside a lexically nested body whose own signature could not change, so the " +
          "context is captured from the enclosing declaration's clause", at, enc)

  private def fqn(s: SymId): String = program.symbolOf(s).map(_.fullName).getOrElse("?")

  // -------------------------------------------------------------------------
  // 4. what each READ becomes — after the growth, because a refused component changes it
  // -------------------------------------------------------------------------

  /** the per-site plan: `(static, origin)` → what the rewrite does there. Every entry that is not
    * [[ReadPlan.Threaded]] is also a counted seam. */
  lazy val readPlan: Map[(SymId, Origin), ReadPlan] =
    reads.map { (st, at, enc) =>
      val key = st -> at
      if deferredReads.contains(key) then key -> ReadPlan.Threaded
      else if scopedS.contains(enc) then key -> ReadPlan.Leave
      else
        siteOf(enc) match
          case Site.Method(m, cap) if methods(m) =>
            if cap then captured(enc, at)
            key -> ReadPlan.Threaded
          // `supplies` and not `classes`: a self-supplied type's body has a given in scope, so its
          // reads are threaded reads and reporting them as residual globals would count a seam that
          // is not there — and would leave the read naming the holder the port is retiring.
          case Site.Cls(c, cap) if supplies(c) =>
            if cap then captured(enc, at)
            key -> ReadPlan.Threaded
          case Site.Method(m, _)       => key -> global(at, m, "its override component is refused")
          case Site.Cls(c, _)          => key -> global(at, c, "its type could not carry a constructor clause")
          case Site.Boundary(sub, why) => key -> global(at, sub, why)
    }.toMap

  private def global(at: Origin, subject: SymId, why: String): ReadPlan =
    val plan = policyFor(subject) match
      case ContextSite.ResidualGlobal => ReadPlan.Global
      case _                          => ReadPlan.Leave
    val what =
      if plan == ReadPlan.Global then s"rewritten to `${holder.context.fqn}.global`"
      else s"left naming `${holder.holder}` — set `boundary = \"residual-global\"`, or a `sites` policy"
    seam(ContextSeamCheck.Kind.ResidualGlobalRead, fqn(subject), holder.holder, s"$why; $what", at, subject)
    plan

object ContextNeed:

  /** the frontend's names for the two executables that are not methods. Engine-minted, not a
    * library's, so naming them here is a structural fact and not a §4.56 string test. */
  val ClinitName    = "<clinit>"
  val InitBlockName = "<initblock>"
  val CtorName      = "<init>"

  /** every class's ancestor, which is why the CT7 warning excludes it: "has an external ancestor" is
    * true of the whole program with this one counted. A JDK name, not a ported library's (§1). */
  val JavaLangObject = "java.lang.Object"

  /** WHERE a declaration's need attaches. */
  enum Site:
    /** a trailing `(using T)` on this method. */
    case Method(sym: SymId, captured: Boolean)
    /** `(using T)` on this class's constructors; its instance members summon it. */
    case Cls(sym: SymId, captured: Boolean)
    /** nothing here can carry a clause, and `why` is the sentence a finding prints. */
    case Boundary(sym: SymId, why: String)

  /** one node of the closure. */
  enum Node:
    case M(sym: SymId)
    case C(sym: SymId)

  /** One static whose initialisation moves out of an initialiser and onto first READ.
    *
    * @param clinit the class initialiser the assignment is removed from, or [[SymId.None]] when the
    *               FIELD carried its own initialiser and there is nothing to strip
    * @param field  the static being initialised — it becomes a `def` over a cache, taking the clause
    * @param rhs    the initialiser expression, moved verbatim; its own reads are then threaded
    * @param key    the `sites` entry that asked for this, verbatim — the string an agent edits
    */
  final case class Deferral(clinit: SymId, field: SymId, rhs: Term, key: String)

  /** one edge the closure took — exposed so a spec can pin the DERIVATION and not only its result. */
  final case class Edge(kind: Edge.Kind, from: SymId, to: SymId, at: Origin)

  object Edge:
    enum Kind:
      case Seed, Use, Override, Instantiate, Capture
