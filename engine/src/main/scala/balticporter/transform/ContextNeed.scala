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
):
  import ContextNeed.*
  import GlobalsToImplicitsTransform.ReadPlan

  private given Program = program

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

  @annotation.tailrec
  private def climb(s: SymId, captured: Boolean, fuel: Int): Site =
    if s == SymId.None || fuel <= 0 then Site.Boundary(s, "it is outside any declaration")
    else program.symbolOf(s) match
      case scala.None => Site.Boundary(s, "it is outside any declaration")
      case Some(sym) =>
        if isType(s) then
          if isDeclaredClass(s) then
            if holder.attach == ContextAttach.Class then Site.Cls(s, captured)
            else Site.Boundary(s, "it is a class body statement and `attach = method`")
          // an anonymous-class or enum-constant body: its members' signatures are fixed by what they
          // implement, so the need lands OUTSIDE and the body captures it lexically.
          else climb(anonHome.getOrElse(s, sym.owner), captured = true, fuel - 1)
        // …and a MEMBER of such a body is reached from the member, not from the body: the climb has
        // to look UP one level before it decides, or an anonymous `Runnable#run` reads as an
        // ordinary method, gets a clause it may not have (its signature is `Runnable`'s), and the
        // enclosing declaration — the one that can actually supply the context — is never asked.
        else if isType(sym.owner) && !isDeclaredClass(sym.owner) then
          climb(sym.owner, captured = true, fuel - 1)
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
            case Some(o) if PolicyBinder.isExecutable(o.info) => climb(sym.owner, captured, fuel - 1)
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
    * `new T(){ … }` is an `Instantiate` usage of `T` whose SITE is the `New` node carrying the body
    * and whose `enclosing` is the declaration it was written in. Read from there, so nothing has to
    * re-walk the tree with its own notion of "where am I" (CLAUDE.md §3). */
  private val anonHome: Map[SymId, SymId] =
    program.referenced.toList.flatMap(program.usages).collect {
      case Usage(UsageKind.Instantiate, n: Tree.New, enc) if n.anon.isDefined && enc != SymId.None =>
        n.anon.get.symbol -> enc
    }.toMap

  private def isType(s: SymId): Boolean = graph.types.contains(s)
  private def isDeclaredClass(s: SymId): Boolean =
    program.definitionOf(s).exists(_.isInstanceOf[Tree.ClassDef])

  private def inScope(s: SymId): Boolean =
    program.symbolOf(s).forall(x => holder.scope.includes(program, x))

  // -------------------------------------------------------------------------
  // 2. the DEFERRED-INIT plan — read BEFORE the growth, because it creates seeds
  // -------------------------------------------------------------------------

  /** the statics whose initialisation a `sites` policy asked to move out of a class initialiser. */
  val deferrals: List[Deferral] =
    reads.flatMap((_, _, enc) => siteOf(enc) match
      case Site.Boundary(sub, _) if policyFor(sub) == ContextSite.LazyInit => Some(sub)
      case _                                                              => scala.None
    ).distinct.flatMap(planDeferral)

  /** the per-site policy for a boundary subject, falling back to the holder's default. */
  private def policyFor(subject: SymId): ContextSite =
    program.symbolOf(subject).map(_.fullName).flatMap(holder.sites.get).getOrElse(holder.boundary match
      case ContextBoundary.Refuse         => ContextSite.Refuse
      case ContextBoundary.ResidualGlobal => ContextSite.ResidualGlobal)

  private def planDeferral(clinit: SymId): List[Deferral] =
    val key   = program.symbolOf(clinit).map(_.fullName).getOrElse("")
    val owner = program.symbolOf(clinit).map(_.owner).getOrElse(SymId.None)
    program.definitionOf(clinit).collect { case d: Tree.DefDef => d }.toList.flatMap { d =>
      statementsOf(d.rhs).flatMap {
        case t: Term => Tree.uncomment(t) match
          case Tree.Assign(lhs, rhs, _, _) =>
            lhsSym(lhs)
              .filter(f => program.symbolOf(f).exists(x => x.flags.isStatic && x.owner == owner))
              .filter(_ => readsHolder(rhs))
              .map(f => Deferral(clinit, f, rhs, key))
          case _ => scala.None
        case _ => scala.None
      }
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

  // -------------------------------------------------------------------------
  // 3. the growth
  // -------------------------------------------------------------------------

  private val edgeLog   = collection.mutable.ListBuffer.empty[Edge]
  private val methods   = collection.mutable.LinkedHashSet.empty[SymId]
  private val classes   = collection.mutable.LinkedHashSet.empty[SymId]
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

  private def enqueue(n: Node, edge: Edge): Unit =
    edgeLog += edge
    n match
      case Node.M(m) if !methods(m) && !frozen(m) => work.enqueue(n)
      case Node.C(c) if !classes(c) && !frozen(c) => work.enqueue(n)
      case _                                      => ()

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
    if classes(c) || frozen(c) then return
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
    // INSTANTIATE: `new C` needs a context in scope wherever it is written.
    program.usages(c).foreach {
      case Usage(UsageKind.Instantiate, site, enc) if enc != SymId.None && enc != c =>
        impose(enc, c, site.origin, Edge.Kind.Instantiate)
      case _ => ()
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
          case Site.Cls(c, cap) if classes(c) =>
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

  /** One static whose initialisation moves out of a class initialiser and onto first READ.
    *
    * @param clinit the class initialiser the assignment is removed from
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
