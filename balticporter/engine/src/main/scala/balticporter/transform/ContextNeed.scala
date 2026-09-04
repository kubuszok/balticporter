package balticporter.transform

import balticporter.tir.*

/** THE CLOSURE — which declarations must be able to supply the context, and where the closure stops.
  *
  * A DIRECTED reachability over five edge kinds (DESIGN.md §8.4): [[ContextNeed.Edge.Kind.Seed]] (a
  * mapped static read), [[ContextNeed.Edge.Kind.Use]] (a call/reference to a threaded declaration),
  * [[ContextNeed.Edge.Kind.Override]] (the whole override component, up and down),
  * [[ContextNeed.Edge.Kind.Instantiate]] (`new C` and its subclasses), and
  * [[ContextNeed.Edge.Kind.Capture]] (a nested body's need lands on its enclosing declaration).
  * Computed once per holder and exposed via [[edges]] so a spec can pin the derivation itself.
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
    /** the `sites` entries that BOUND: the policy key → the symbols it named. Empty is the pre-CT6
      * code path. // ENGINE-LIMITS CT6 */
    boundSites: Map[String, List[SymId]] = Map.empty,
    /** the `selfSupplied` entries that BOUND: the TYPE a framework instantiates → the policy key
      * that said so. Empty is the pre-CT7 code path. // ENGINE-LIMITS CT7 */
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
    * Every step is structural. A lexically nested type (anonymous-class body, enum-constant body)
    * has no `ClassDef` of its own, so the climb continues through its owner and the read CAPTURES.
    * A class or field initialiser has no signature and stops the climb. */
  def siteOf(from: SymId): Site = siteCache.getOrElseUpdate(from, climb(from, captured = false, 64))

  /** the climb AS IT WAS BEFORE ANY DEFERRAL — the one question the deferral scan may ask.
    *
    * Uncached on purpose: a deferral-aware climb consulted while the plan is being built is a
    * cycle, since [[deferrals]] is derived from sites this climb finds unreachable. */
  private def preSiteOf(from: SymId): Site = climb(from, captured = false, 64, deferAware = false)

  @annotation.tailrec
  private def climb(s: SymId, captured: Boolean, fuel: Int, deferAware: Boolean = true): Site =
    if s == SymId.None || fuel <= 0 then Site.Boundary(s, "it is outside any declaration")
    // a DEFERRED static is now a `def` over a cache that takes the clause, on the field's OWN
    // symbol — not a boundary, or the climb would refuse the very body the deferral moved.
    else if deferAware && deferredFields.contains(s) then Site.Method(s, captured)
    else program.symbolOf(s) match
      case scala.None => Site.Boundary(s, "it is outside any declaration")
      case Some(sym) =>
        if isType(s) then
          if isDeclaredClass(s) then
            if holder.attach == ContextAttach.Class then Site.Cls(s, captured)
            else Site.Boundary(s, "it is a class body statement and `attach = method`")
          // an anonymous/enum-constant body: signature is fixed by what it implements, so the
          // need lands OUTSIDE and captures lexically.
          else climb(anonHome.getOrElse(s, sym.owner), captured = true, fuel - 1, deferAware)
        // a MEMBER of such a body must look UP one level first, or an anonymous `Runnable#run`
        // reads as an ordinary method and gets a clause its signature (`Runnable`'s) may not have.
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
    * The frontend interns an anonymous class with its enclosing CLASS as owner, losing the method,
    * so the lexical home is read off the `New` node's usage site instead (whose `enclosing` is where
    * it was written) rather than the owner chain (CLAUDE.md §3). The usage KIND is not consulted —
    * `Xref.walkType` mislabels a generic constructor's `Instantiate` as `Tycon`. // ENGINE-LIMITS CT6
    */
  private val anonHome: Map[SymId, SymId] =
    program.referenced.toList.flatMap(program.usages).collect {
      case Usage(_, n: Tree.New, enc) if n.anon.isDefined && enc != SymId.None =>
        n.anon.get.symbol -> enc
    }.toMap

  /** Is this usage of `c` a CONSTRUCTION of `c`? — reads the `New` NODE's constructed head rather
    * than the recorded `UsageKind`, because `Xref.walkType`'s `AppliedType` arm mislabels a generic
    * constructor's `Instantiate` as `Tycon`. A kind-blind "any usage at a `New` site" would also be
    * wrong: `Cell` in `new Pool<Cell>()` is a TYPE ARGUMENT, not a construction. Off a `New`, the
    * recorded kind is still the answer — `Tree.NewArray` has no constructed head to read.
    * // ENGINE-LIMITS CT6
    */
  private def instantiates(u: Usage, c: SymId): Boolean = u.site match
    case n: Tree.New => constructedBy(n) == c
    case _           => u.kind == UsageKind.Instantiate

  /** `C::new` IS a construction of `C`, which [[instantiates]] cannot answer: `Xref` records the
    * reference's TYPE at the qualifier's `TypeTree` (`UsageKind.TypeRefPos`), a site every type
    * mention shares, so the constructor's own symbol is read off the `MethodRef` node instead.
    * Consulted by both the growth (impose the need) and [[constructedByProgram]] (stop warning that
    * nothing constructs a class a factory reference builds). // ENGINE-LIMITS CT6
    */
  private def ctorRefUses(c: SymId): List[Usage] =
    ctorsOf(c).flatMap(program.usages).filter(_.site.isInstanceOf[Tree.MethodRef])

  /** the constructors THIS PROGRAM declares for `c`. Read off the `ClassDef` and the frontend's own
    * `<init>` name, which is engine-minted and therefore a structural fact rather than a §4.56 string
    * test — the same reading [[climb]] makes one line above its own boundary test. */
  private def ctorsOf(c: SymId): List[SymId] =
    program.definitionOf(c).toList.collect { case cd: Tree.ClassDef => cd }.flatMap(_.body.collect {
      case d: Tree.DefDef if program.symbolOf(d.symbol).exists(_.name == CtorName) => d.symbol
    })

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
    * The trigger is the POLICY, not a read: candidates come from the `sites` entries themselves
    * (via [[boundSites]]), with the read-derived set kept beside them as a subset that also covers
    * keys the binder refuses (`<clinit>`). // ENGINE-LIMITS CT6
    */
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
        // A `lazy-init` entry is judged by its OUTCOME, not by this lookup — counting it here would
        // mark it fired before anything was planned. // ENGINE-LIMITS CT6
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
        case Tree.Assign(lhs, rhs, _, _, _) =>
          lhsSym(lhs)
            .filter(f => program.symbolOf(f).exists(x => x.flags.isStatic && x.owner == owner))
            .filter(_ => needsContext(rhs))
            .map(f => Deferral(d.symbol, f, rhs, key))
        case _ => scala.None
      case _ => scala.None
    }

  /** a STATIC FIELD CARRYING ITS OWN INITIALISER — the shape no read reaches.
    *
    * A static initialiser runs at class initialisation before anything could pass it a context, and
    * names no mapped static, so the read-derived trigger never sees it. No `<clinit>` to strip here
    * — the `ValDef` itself is what [[DeferredInit]] replaces, so the deferral's `clinit` is
    * [[SymId.None]]. STATIC only: an instance field under `attach = class` is not a boundary.
    */
  private def fromField(v: Tree.ValDef, key: String): List[Deferral] =
    if !program.symbolOf(v.symbol).exists(_.flags.isStatic) then Nil
    else v.rhs.filter(needsContext).map(rhs => Deferral(SymId.None, v.symbol, rhs, key)).toList

  /** Does this initialiser reach the context AT ALL? Either it READS a mapped static, or it
    * CONSTRUCTS a type this program declares. The second is a deliberate over-approximation — the
    * deferral plan runs BEFORE the growth, so `threadedClasses` does not exist yet — made safe by
    * `lazy-init` being per-site opt-in, never a default. // ENGINE-LIMITS CT6
    */
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

  /** Does this term read any of the given symbols? */
  private def readsAnyOf(t: Term, syms: Set[SymId]): Boolean =
    StandardTraversal.scanTerm(t, false) { (acc, x) =>
      acc || (x match
        case Tree.Ident(s, _, _)     => syms.contains(s)
        case Tree.Select(_, s, _, _) => syms.contains(s)
        case _                       => false)
    }

  /** the read sites the deferral moved into a method that DOES take a clause. */
  private val deferredReads: Set[(SymId, Origin)] = deferrals.flatMap(d => staticIn(d.rhs)).toSet

  /** WHICH `sites` entries this run's decisions actually turned on — the input to the phase's
    * dead-binding report. A `lazy-init` entry counts as fired iff it produced a [[Deferral]]; the
    * other two count when [[policyFor]] resolved a boundary through them. Read AFTER [[readPlan]]
    * has been forced. // ENGINE-LIMITS CT6
    */
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

    // CT11: static field holders — seeds readers of held fields, re-runs to fixpoint.
    discoverFieldHolders()

    // AFTER the fixpoint, because both questions are about the finished closure: whether a
    // self-supplied class's PARENT ended up threaded, and which threaded classes nothing constructs.
    selfS.toList.sortBy(_.raw).foreach(checkSelfSupplied)
    classes.toList.sortBy(_.raw).foreach(warnUnconstructed)

  // ---- STATIC FIELD HOLDERS (CT11) ------------------------------------------------------------

  /** static fields whose initialisers construct a threaded class — they become holders with a
    * throwing accessor, initialised at the head of every threaded static method on the same class.
    * Populated by [[discoverFieldHolders]] after the first growth pass. */
  private val fieldHolderSet = collection.mutable.LinkedHashMap.empty[SymId, Term]

  /** clinit statements that read a held field — they are deferred alongside the field's initialiser,
    * because the static block and the field are ONE JLS step-9 sequence (CT11). Key is the owning
    * type's symbol. */
  private val fieldHolderClinitStmts = collection.mutable.LinkedHashMap.empty[SymId, List[Statement]]

  /** the held fields: field symbol -> initialiser term. Read AFTER [[grow]]. */
  def fieldHolders: Map[SymId, Term] = fieldHolderSet.toMap

  /** clinit statements that read a held field, grouped by owning type. These are REMOVED from the
    * clinit body and prepended to the holder assignment at the head of threaded methods. */
  def fieldHolderClinit: Map[SymId, List[Statement]] = fieldHolderClinitStmts.toMap

  /** Does this initialiser construct a type the growth already threaded? More precise than
    * [[constructsOwned]] — only types whose constructors WILL change are relevant. */
  private def constructsThreaded(t: Term): Boolean =
    StandardTraversal.scanTerm(t, false) { (acc, x) =>
      acc || (x match
        case n: Tree.New =>
          val head = constructedBy(n)
          head != SymId.None && classes.contains(head)
        case _ => false)
    }

  /** CT11: find static fields whose initialisers construct a threaded class, seed readers, re-grow. */
  private def discoverFieldHolders(): Unit =
    val candidates = program.units.flatMap(u => StandardTraversal.allClassDefs(u)).flatMap { cd =>
      cd.body.collect {
        case v: Tree.ValDef
          if program.symbolOf(v.symbol).exists(s => s.flags.isStatic && !s.flags.isMutable) &&
             v.rhs.exists(constructsThreaded) &&
             !statics.contains(v.symbol) &&
             !deferredFields.contains(v.symbol) =>
          v.symbol -> v.rhs.get
      }
    }
    if candidates.isEmpty then return

    candidates.foreach { (field, rhs) =>
      fieldHolderSet += field -> rhs
      program.usages(field).foreach {
        case Usage(UsageKind.TermRef, site, enc) if enc != SymId.None =>
          if !inScope(enc) then scopedS += enc
          else siteOf(enc) match
            case Site.Method(m, _) =>
              if !methods(m) then viaMap.getOrElseUpdate(m, "uses-held-field")
              enqueue(Node.M(m), Edge(Edge.Kind.Instantiate, field, m, site.origin))
            case Site.Cls(c, _) =>
              if !classes(c) then viaMap.getOrElseUpdate(c, "uses-held-field")
              enqueue(Node.C(c), Edge(Edge.Kind.Instantiate, field, c, site.origin))
            case Site.Boundary(sub, why) =>
              seam(ContextSeamCheck.Kind.StaticFieldHolder, fqn(sub), holder.holder,
                s"this declaration reads `${fqn(field)}`, whose initialiser now needs the " +
                  s"context, and $why", site.origin, sub)
        case _ => ()
      }
    }

    // Clinit statements reading a held field are deferred alongside it (JLS step 9, CT11).
    val heldSyms = fieldHolderSet.keySet
    program.units.flatMap(u => StandardTraversal.allClassDefs(u)).foreach { cd =>
      val owner = cd.symbol
      cd.body.foreach {
        case d: Tree.DefDef if program.symbolOf(d.symbol).exists(_.name == ClinitName) =>
          val stmts = statementsOf(d.rhs).collect {
            case s: Term if readsAnyOf(s, heldSyms.toSet) => s
          }
          if stmts.nonEmpty then
            fieldHolderClinitStmts.updateWith(owner) {
              case Some(existing) => Some(existing ++ stmts)
              case scala.None => Some(stmts)
            }
        case _ => ()
      }
    }

    while work.nonEmpty do
      work.dequeue() match
        case Node.M(m) => expandMethod(m)
        case Node.C(c) => expandClass(c)

    fieldHolderSet.keys.toList.foreach { field =>
      val owner = program.symbolOf(field).map(_.owner).getOrElse(SymId.None)
      val hasThreadedStatic = program.definitionOf(owner).toList.collect { case cd: Tree.ClassDef => cd }
        .flatMap(_.body).exists {
          case d: Tree.DefDef =>
            methods(d.symbol) && !deferredFields(d.symbol) &&
              program.symbolOf(d.symbol).exists(s => s.flags.isStatic && s.name != CtorName)
          case _ => false
        }
      if !hasThreadedStatic then
        fieldHolderSet -= field
        seam(ContextSeamCheck.Kind.UnsuppliableUse, fqn(field), holder.holder,
          s"this static field's initialiser constructs `${fqn(field)}` which now needs the context, " +
            "and no static method on this class was threaded — there is nowhere to assign the holder",
          Decision.originOf(program, field), field)
    }

  /** A SELF-SUPPLIED CLASS WHOSE PARENT TOOK THE CLAUSE — the one shape the third answer cannot
    * cover. A `given` member is in scope for the class BODY, not for its `extends` clause: the
    * parent constructor runs before this class's own members exist, so there is no argument to
    * pass and no rewrite that repairs it — refused, named and counted. CLAUDE.md §1
    */
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
    * the program (the shape a reflectively-instantiated test suite has). WARNS rather than refuses:
    * the engine cannot distinguish a framework construction from an ordinary caller passing the
    * given. Fires when no `Instantiate` edge reaches it (nor a constructed descendant) AND its
    * ancestry has a declared ancestor, other than `java.lang.Object`, this program does not own.
    * // ENGINE-LIMITS CT7
    */
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

  /** Does anything THIS PROGRAM declares construct `c`, or a descendant of it?
    *
    * An ARRAY ALLOCATION is not a construction: `new Suite[8]` runs no constructor, so it is
    * excluded here rather than in [[instantiates]] — that other caller (the threading closure)
    * over-threads on the same edge, but fixing it there moves emitted signatures, a separate
    * change. This one decides only whether a warning fires. // ENGINE-LIMITS CT7
    */
  private def constructedByProgram(c: SymId): Boolean =
    (c :: graph.descendantsOf(c)).exists(t =>
      program.usages(t).exists(u =>
        instantiates(u, t) && !u.site.isInstanceOf[Tree.NewArray] && u.enclosing != SymId.None) ||
      // …and `T::new` builds one on every call of the factory it becomes (see [[ctorRefUses]]).
      ctorRefUses(t).exists(_.enclosing != SymId.None))

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
    // THE THIRD ANSWER (ENGINE-LIMITS CT7): a framework-constructed class takes the context WITHOUT
    // a parameter — no signature edit, no propagation — but its body still reads it via a `given`
    // member the emitter fills, so this is a resolution and not a refusal.
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
    // INSTANTIATE: `new C` needs a context in scope wherever written. Reads the NODE, not the
    // recorded kind — a generic `new` is labelled `Tycon` by the shared index. ENGINE-LIMITS CT6
    program.usages(c).foreach { u =>
      if instantiates(u, c) && u.enclosing != SymId.None && u.enclosing != c then
        impose(u.enclosing, c, u.site.origin, Edge.Kind.Instantiate)
    }
    // …and `C::new`, which the class's own usages cannot report (see [[ctorRefUses]]).
    ctorRefUses(c).foreach { u =>
      if u.enclosing != SymId.None && u.enclosing != c then
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
        // A declaration that CANNOT take a clause uses one that requires it — its own kind
        // (`UnsuppliableUse`), distinct from a residual global read: this is `No given` at that
        // line every time. PROGRESS.md §10.8.9
        seam(ContextSeamCheck.Kind.UnsuppliableUse, fqn(sub), holder.holder,
          s"this declaration ${useVerb(kind)} `${fqn(from)}`, which now takes a context, and $why — " +
            "so the emitted code has no given in scope at this line", at, sub)

  /** what the boundary DID with the threaded declaration, in the finding's own sentence —
    * distinguishes "constructs" from "uses" so a reader need not open the file to find out. §4.45 */
  private def useVerb(k: Edge.Kind): String = k match
    case Edge.Kind.Instantiate => "CONSTRUCTS"
    case _                     => "uses"

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
          // `supplies`, not `classes`: a self-supplied type's body has a given in scope too, so its
          // reads are threaded reads, not residual globals.
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
