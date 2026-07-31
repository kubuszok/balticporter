package balticporter.transform

import balticporter.core.{PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy}
import balticporter.tir.*

/** GLOBALS → CONTEXT: a Java class whose `static` state is really an ambient CONTEXT becomes a value
  * threaded through the program as a Scala 3 `using` parameter (DESIGN.md §8.4).
  *
  * ==What survives from the predecessor, and why it is worth naming==
  * A call into a threaded method changes '''nothing at the call site''' — the argument arrives from
  * the `using` in scope. That is what makes the mechanism scale to the 562 read sites measured in
  * one corpus library, and it is why "no decision row per call site" is a derivation rather than a
  * shortcut. The `Reason.Configured` provenance shape, the factory's refusal of an absent key, and
  * the traversal-based rewrite are kept for the same reason: they were right.
  *
  * ==Two live SILENT mistranslations this replacement closes==
  * Both were in the predecessor's core, both produced broken emitted code with zero decisions and
  * zero findings, and they are why this is a replacement rather than an extension:
  *
  *   - a `static { }` block is a synthetic class-initialiser `DefDef` with a `MethodType`, so it
  *     passed the is-a-method test, SEEDED, and received a `using` parameter — and the emitter
  *     inlines only its BODY into the companion, dropping the parameter and leaving the context
  *     identifier unresolved;
  *   - a FIELD initialiser's read is enclosed by the FIELD symbol, which failed the is-a-method seed
  *     test, and the rewrite visited only `DefDef` arms — so the initialiser still named a member
  *     that was no longer static.
  *
  * Here a class initialiser and a field initialiser are BOUNDARIES by construction: they have no
  * signature to thread anything through, [[ContextNeed.siteOf]] resolves them as such, and every one
  * of them is a counted [[ContextSeamCheck]] row.
  *
  * ==The closure is a directed reachability over five edge kinds==
  * See [[ContextNeed]]. The predecessor closed its seeds under `callersOf` alone, which is unsound
  * in BOTH directions at once because Java resolved every virtual call to the DECLARED member.
  *
  * ==The read shape is an anonymous `(using T)` plus a summon, and that is forced by evidence==
  * A reference hand port repaired two files AWAY from named context parameters, with the reason
  * recorded: a parameter named after the renamed root package SHADOWS it and breaks every qualified
  * reference in scope — and this engine emits ONLY fully-qualified names. Nothing reads the name
  * (`using` resolution and `summon` never do), so anonymity costs nothing, and 98.2 % of that hand
  * port's 557 context reads are the inline summon idiom. The clause is therefore emitted as
  * `(using T)` with no parameter name at all.
  *
  * ==The member map is PATH-valued==
  * `gl -> "graphics.gl20"` is a two-hop rewrite, and in the reference port two-hop reads are 305 of
  * 557 (56 %). The same shape answers the WRITE problem — the bundle stays immutable and the
  * mutability lives on the service — so `Holder.f = x` write-throughs along the mapped path.
  * '''The mechanism never mints mutability the mapped type does not declare''': a path ending on a
  * `val` is a compile error at that one line, attributable through the source map.
  *
  * ==There is no ambient `given`, and that is the load-bearing reversal==
  * The predecessor synthesised `given C = new C()` in the companion, which made every
  * unthreaded→threaded seam compile silently and reintroduced the global with extra steps. Without
  * it, an unthreaded owned caller of a threaded callee is IMPOSSIBLE BY CONSTRUCTION — the closure
  * would have threaded it — except across a refused boundary, and those sites are exactly what
  * [[ContextSeamCheck]] counts.
  *
  * ==Kind==
  * CLAUDE.md §1(b). The mechanism — find the reads, close over five edges, add a clause, rewrite the
  * read through a path — is a fact about Java and Scala. WHICH class is an ambient context, what its
  * counterpart is called and which of its fields map where is a fact about one library and arrives
  * as a constructor parameter ([[ContextHolder]]). An empty `holders` list is a structural no-op:
  * `run` returns its input before building anything.
  *
  * ==Shared surface==
  * It changes emitted signatures, so it implements `SurfacePolicy` and its holders live in the BASE
  * manifest: a dependent resolves against the base's Java and must see the same threading, or the
  * two ports each compile alone and cannot compile together (§1.5).
  */
final class GlobalsToImplicitsTransform(val holders: List[ContextHolder] = Nil)
    extends Phase, PolicySource, SurfacePolicy, PolicyBound:

  import GlobalsToImplicitsTransform.*

  def name = "globals->implicits"

  /** every policy key is written in the UPSTREAM namespace, and the package rename runs LAST
    * (§4.56). */
  override def runsBefore: Set[String] = Set("package-rename")

  /** the holders, sorted and rendered — two modules that agree must compare equal (§1.5). */
  def surfaceFingerprint: String = holders.map(_.fingerprint).sorted.mkString(";")

  // ---- policy, bound before the pipeline starts ---------------------------------------------

  private var records: List[PolicyBinder.Record]                  = Nil
  private var malformed: List[PolicyFinding]                      = Nil
  private var boundStatics: Map[String, Map[String, List[SymId]]] = Map.empty
  private var boundHolder: Map[String, SymId]                     = Map.empty
  private var boundPromote: Map[String, Set[SymId]]               = Map.empty

  def bindPolicy(binder: PolicyBinder): Unit =
    val bad = collection.mutable.ListBuffer.empty[PolicyFinding]
    def malformedEntry(h: ContextHolder, setting: String, key: String, what: String): Unit =
      bad += PolicyFinding(name, s"GlobalsToImplicitsTransform(holders) `${h.holder}`.$setting",
        key, PolicyIssue.Malformed, what)

    holders.foreach { h =>
      // the HOLDER is a TYPE key; naming a member here is a different mistake with a different fix.
      binder.bindType(name, s"GlobalsToImplicitsTransform(holders) `${h.holder}`.holder", h.holder)
        .toOption.foreach(s => boundHolder = boundHolder.updated(h.holder, s))

      if h.members.isEmpty then
        malformedEntry(h, "members", h.holder, "no field is mapped onto the context, so every read " +
          "would be un-mappable and the phase would thread nothing — the §1(b) silent no-op this " +
          "engine refuses. Map at least one static onto a path on the context type")

      // CLASS ATTACHMENT IS NOT EMITTABLE YET, and it says so rather than shipping code that does
      // not compile. The TIR edit is complete and correct — the clause lands on every constructor,
      // the closure propagates down the hierarchy and across `new` — but the emitter's constructor
      // funnel undoes it three ways, measured on this phase's own end-to-end fixture (5 errors):
      //
      //   - a constructor that has GAINED a parameter is no longer nilary, so the funnel declines to
      //     promote it (`ENGINE-LIMITS.md` C1) and emits a synthetic nilary primary beside it — the
      //     class body then has no context in scope at all;
      //   - where it DOES promote, the synthetic primary's parameter list is built from the funnel's
      //     own plan rather than through `paramClause`, so the `given` grouping is dropped and the
      //     clause renders as an ordinary `class Scene($p: demo.Ctx)` — again no given in scope;
      //   - and a subclass of the first shape sees TWO applicable constructors (`()` and
      //     `()(using T)`) and reports an ambiguous overload.
      //
      // All three live in `CtorFunnel`/`CtorPlan`/the emitter's constructor region, which DESIGN.md
      // §8.2's work owns. A finding, not a silent emission: CLAUDE.md §1(b)'s rule that a knob whose
      // seam is silent is worse than no knob.
      if h.attach == ContextAttach.Class then
        bad += PolicyFinding(name, s"GlobalsToImplicitsTransform(holders) `${h.holder}`.attach",
          h.holder, PolicyIssue.Unverifiable,
          "`attach = \"class\"` puts the context clause on the class's CONSTRUCTORS, and the " +
            "emitter's constructor funnel does not carry it: a constructor that gained a parameter " +
            "is not promoted to the primary (so the class body has no given in scope), a promoted " +
            "one loses the `using` grouping and renders as an ordinary class parameter, and a " +
            "subclass of the first shape sees an ambiguous overload. Measured: 5 errors on the " +
            "phase's own fixture. Use `attach = \"method\"` until the synthetic-primary work " +
            "(DESIGN.md §8.2) lands; the closure and the TIR edit are already correct")

      h.context match
        case ContextType.Minted(fqn) =>
          h.members.filter((_, p) => p.contains('.')).toList.sorted.foreach((f, p) =>
            malformedEntry(h, "members", s"${h.holder}#$f", s"`$p` is a two-hop PATH and the context " +
              s"type is MINTED — the engine synthesises `$fqn`'s own members and has no intermediate " +
              "type to hang a second hop off. Map this field onto a single member, or `inject` a " +
              "context type you wrote, which is where a service path belongs"))
          if h.reader == ContextReader.Apply then
            malformedEntry(h, "reader", fqn, "`apply` reads through an `inline def apply()(using T): T` " +
              "on the context's companion, which a MINTED type does not declare. Use `summon`, or " +
              "`inject` a context type that declares the sugar")
        case ContextType.Injected(_) => ()

      // A member key names a static ON THE HOLDER. Bare on purpose — a field has no parameter list.
      boundStatics = boundStatics.updated(h.holder, h.members.keys.map { f =>
        val key = MemberKey(h.holder, f).render
        f -> binder.bindMembers(name, s"GlobalsToImplicitsTransform(holders) `${h.holder}`.members", key)
          .toOption.getOrElse(Nil).flatMap(_.sym)
      }.toMap)

      h.sites.keys.toList.sorted.foreach(k =>
        binder.bindMembers(name, s"GlobalsToImplicitsTransform(holders) `${h.holder}`.sites", k))

      boundPromote = boundPromote.updated(h.holder, h.promoteToClass.flatMap(t =>
        binder.bindType(name, s"GlobalsToImplicitsTransform(holders) `${h.holder}`.promoteToClass", t)
          .toOption))

      h.scope.entries.foreach(e =>
        binder.bindScope(name, s"GlobalsToImplicitsTransform(holders) `${h.holder}`.scope", e))
    }
    malformed = bad.toList
    records   = binder.recordsFor(name)

  /** the never-fired half (from the BINDING, so it is complete whether or not this phase ran) plus
    * this phase's own malformed entries and counted refusals. */
  def policyReport: PolicyReport =
    PolicyReport.fromBindings(records) ++ PolicyReport(malformed ++ refusals.toList)

  private val refusals = collection.mutable.ListBuffer.empty[PolicyFinding]

  // ---- the seams, recorded as the run makes them --------------------------------------------

  private val seamLog = collection.mutable.ListBuffer.empty[ContextSeamCheck.Finding]

  /** Every seam this run drew, restricted to the units it actually EMITS.
    *
    * The same filter `OmissionCheck` and `CollectionBoundaryCheck` carry, for the same measured
    * reason: a DEPENDENT port's `Program` holds its base module's units too, and a seam inside one
    * of those is the BASE's finding, reported by a repository that cannot act on it
    * (`ENGINE-LIMITS.md` D2). A base port passes `program.units` and this is the identity. */
  def seams(program: Program, units: List[Tree.ClassDef]): List[ContextSeamCheck.Finding] =
    val own = units.map(_.symbol).toSet
    def unitOf(s: SymId, fuel: Int = 64): SymId =
      if s == SymId.None || fuel <= 0 then SymId.None
      else if own.contains(s) then s
      else program.symbolOf(s).map(x => unitOf(x.owner, fuel - 1)).getOrElse(SymId.None)
    seamLog.toList.filter(f => unitOf(f.enclosing) != SymId.None)

  def seams(program: Program): List[ContextSeamCheck.Finding] = seams(program, program.units)

  // ---- the run ------------------------------------------------------------------------------

  override def run(program: Program): Program =
    seamLog.clear(); refusals.clear()
    if holders.isEmpty then return program
    holders.foldLeft(program)((p, h) => runHolder(p, h))

  private def runHolder(program0: Program, h: ContextHolder): Program =
    val statics: Map[SymId, String] =
      boundStatics.getOrElse(h.holder, Map.empty).toList.flatMap { (field, syms) =>
        syms.filter(s => program0.owns(s) && program0.symbolOf(s).exists(_.flags.isStatic))
          .map(_ -> h.members(field))
      }.toMap
    if statics.isEmpty then return program0

    given Program = program0
    val mint  = new Minter(program0)
    val graph = OverrideGraph.build(program0)
    val need  = new ContextNeed(program0, graph, h, statics, boundPromote.getOrElse(h.holder, Set.empty),
                                (k, s, key, d, o, e) => seamLog += ContextSeamCheck.Finding(k, s, key, d, o, e),
                                (s, why) => refuse(h, why))
    need.grow()

    // ---- the context TYPE, and the terms that read through it ---------------------------------
    val ctxFqn = h.context.fqn
    val ctxSym = mint.selfTyped(ctxFqn.split('.').last, ctxFqn, Flags(isFinal = true))
    val ctxRef = TypeRepr.TypeRef(TypeRepr.NoPrefix, ctxSym)
    val o      = Origin.synthetic

    val predefSym = mint.tpe("Predef", "scala.Predef")
    val summonSym = mint.member("summon", "scala.Predef#summon", predefSym, ctxRef, Flags(isStatic = true))
    val applySym  = mint.member("apply", s"$ctxFqn#apply", ctxSym, ctxRef, Flags(isStatic = true))
    val globalSym = mint.member("global", s"$ctxFqn#global", ctxSym, ctxRef,
                                Flags(isStatic = true, isMutable = true))
    val segCache  = collection.mutable.Map.empty[String, SymId]
    def segSym(seg: String): SymId =
      segCache.getOrElseUpdate(seg, mint.member(seg, s"$ctxFqn#$seg", ctxSym, TypeRepr.NoType, Flags()))

    /** `scala.Predef.summon[T]`, or `T.apply()`. Built STRUCTURALLY and not as text: a minted
      * context's FQN is in the upstream namespace and the package rename runs last, so a name
      * spliced into a string would be the one reference the rename cannot see. */
    def contextExpr: Term = h.reader match
      case ContextReader.Summon =>
        Tree.TypeApply(Tree.Ident(summonSym, ctxRef, o), List(TypeTree(ctxRef, o)), ctxRef, o)
      case ContextReader.Apply => Tree.Apply(Tree.Ident(applySym, ctxRef, o), Nil, applySym, ctxRef, o)

    def pathOn(base: Term, path: String, tpe: TypeRepr, at: Origin): Term =
      val segs = path.split('.').toList.filter(_.nonEmpty)
      segs.zipWithIndex.foldLeft(base) { case (q, (seg, i)) =>
        Tree.Select(q, segSym(seg), if i == segs.size - 1 then tpe else TypeRepr.NoType, at)
      }

    // ---- the DEFERRED-INIT rewrite, first: it MINTS a threaded method the read pass then visits --
    val deferred = new DeferredInit(program0, h, mint, ctxRef, need.deferrals)
    deferred.deferrals.foreach { d =>
      program0.symbolOf(d.field).foreach { s =>
        seamLog += ContextSeamCheck.Finding(ContextSeamCheck.Kind.DeferredInit, s.fullName, d.key,
          "initialised at first READ instead of at class initialisation", Decision.originOf(program0, d.field), d.field)
        record(Decision(
          kind = Decision.Kind.DeferredInit, subject = d.field, subjectFqn = s.fullName,
          // no `key` in the DETAIL: `Reason.Configured` already carries it, and a porter note
          // renders the classification's pairs first and the detail's after — so a duplicate key
          // appears TWICE in the emitted comment.
          detail = Map(
            "from" -> "assigned by the class initialiser",
            "to"   -> s"a `def` over a cache, taking `(using $ctxFqn)`",
            "why"  -> ("java runs a class initialiser at first ACTIVE USE of the class and this " +
              "runs at first READ of the field — an eager→lazy change the `sites` policy asked for"),
          ),
          reason = Reason.Configured(name, d.key),
          origin = Decision.originOf(program0, d.field),
        ))
      }
    }

    // ---- what each READ SITE becomes -----------------------------------------------------------
    val plan = need.readPlan
    val rewrite = new Phase:
      def name = "globals->implicits/read"
      override def transformIdent(t: Tree.Ident)(using Program): Term = read(t.sym, t.tpe, t.origin).getOrElse(t)
      override def transformSelect(t: Tree.Select)(using Program): Term = read(t.sym, t.tpe, t.origin).getOrElse(t)
      private def read(s: SymId, tpe: TypeRepr, at: Origin): Option[Term] =
        statics.get(s).flatMap(path => plan.get(s -> at) match
          case Some(ReadPlan.Threaded) => Some(pathOn(contextExpr, path, tpe, at))
          case Some(ReadPlan.Global)   => Some(pathOn(Tree.Ident(globalSym, ctxRef, o), path, tpe, at))
          case _                       => scala.None)

    // ---- the signature edits --------------------------------------------------------------------
    val deferredFields = need.deferrals.map(_.field).toSet
    val edit = new Phase:
      def name = "globals->implicits/thread"

      override def transformDefDef(t: Tree.DefDef)(using Program): Tree.DefDef =
        // a deferral's own `def` was minted WITH its clause; adding a second one would be two.
        if need.threadedMethods(t.symbol) && !deferredFields(t.symbol) then
          t.copy(paramss = t.paramss :+ List(mint.usingParam(t.symbol, ctxFqn, ctxRef, t.origin)))
        else t

      override def transformClassDef(t: Tree.ClassDef)(using Program): Tree.ClassDef =
        if !need.threadedClasses(t.symbol) then t
        else
          val ctors = t.body.collect { case d: Tree.DefDef if isCtor(summon[Program], d.symbol) => d.symbol }
          if ctors.isEmpty then
            // A java INTERFACE has no constructor, so a trait the manifest promoted to an abstract
            // class has nothing to hang the clause on and one is minted — the promotion is only half
            // done otherwise, which is the shape of defect a refusal is supposed to prevent.
            val at   = t.origin
            val ctor = mint.member(ContextNeed.CtorName,
              s"${summon[Program].symbolOf(t.symbol).map(_.fullName).getOrElse("?")}#<init>",
              t.symbol, TypeRepr.MethodType(Nil, TypeRepr.NoType), Flags())
            t.copy(body = Tree.DefDef(ctor, List(List(mint.usingParam(ctor, ctxFqn, ctxRef, at))),
              TypeTree(TypeRepr.NoType, at), Some(Tree.Block(Nil, Tree.Literal(Constant.UnitC, TypeRepr.NoType, at),
                TypeRepr.NoType, at)), at) :: t.body)
          else
            // The clause lands on EVERY constructor. A Scala class parameter is in scope throughout
            // the body, so instance methods summon it with no signature change — but a SECONDARY
            // constructor is a method, and one that did not take the clause could not delegate.
            t.copy(body = t.body.map {
              case d: Tree.DefDef if ctors.contains(d.symbol) =>
                d.copy(paramss = d.paramss :+ List(mint.usingParam(d.symbol, ctxFqn, ctxRef, d.origin)))
              case s => s
            })

    // ---- apply, in order --------------------------------------------------------------------
    val promotedTbl = need.promoted.foldLeft(program0.symbols) { (tbl, t) =>
      tbl.get(t).map(s => tbl.updated(s.copy(flags = s.flags.copy(isTrait = false, isAbstract = true))))
        .getOrElse(tbl)
    }
    val prog1  = program0.rebuilt(symbols = SymbolTable(promotedTbl.all ++ mint.minted))
    val units1 = prog1.units.map(u => deferred.apply(u)(using prog1))
    val units2 = units1.map(u => StandardTraversal.mapClassDef(rewrite, u)(using prog1))
    val units3 = units2.map(u => StandardTraversal.mapClassDef(edit, u)(using prog1))
    val prog2  = prog1.rebuilt(units = units3, symbols = SymbolTable(prog1.symbols.all ++ mint.minted))
    val prog3  = prog2.rebuilt(xref = Xref.build(prog2.units))

    val withMint = h.context match
      case ContextType.Injected(_) => prog3
      case ContextType.Minted(fqn) => mintContext(prog3, h, fqn, ctxSym, ctxRef, statics, globalSym, mint)

    val out = residualHolder(withMint, h, statics)
    recordDecisions(out, h, need, ctxFqn)
    out.rebuilt(xref = Xref.build(out.units))

  // ---- the minted context type ----------------------------------------------------------------

  /** Synthesize the context type: one `var` per mapped field, plus `var global` when a residual read
    * exists.
    *
    * Every member is a `var` with a defaulted initialiser, which is the HOLDER'S OWN shape (a bag of
    * mutable statics) moved onto an instance — so a consumer's bootstrap sets them exactly where it
    * used to set `Holder.field = …`, and a global rebinding still write-throughs. A hand port writes
    * an immutable case class with a private constructor, `@implicitNotFound` and accessor sugar
    * instead; that is precisely what `inject` is for, and the mint deliberately does not guess it. */
  private def mintContext(p: Program, h: ContextHolder, fqn: String, ctxSym: SymId, ctxRef: TypeRepr,
                          statics: Map[SymId, String], globalSym: SymId, mint: Minter): Program =
    val o = Origin.synthetic
    val fields = statics.toList
      .flatMap((s, path) => p.symbolOf(s).map(sym => path -> sym.info))
      .filterNot((path, _) => path.contains('.'))
      .distinctBy(_._1).sortBy(_._1)
      .map((path, info) => mint.member(path, s"$fqn#$path", ctxSym, info, Flags(isMutable = true)) -> info)
    val hasGlobal = seamLog.exists(f => f.kind == ContextSeamCheck.Kind.ResidualGlobalRead)
    val body: List[Statement] =
      fields.map((id, info) => Tree.ValDef(id, TypeTree(info, o), scala.None, o)) ++
        (if hasGlobal then List(Tree.ValDef(globalSym, TypeTree(ctxRef, o), scala.None, o)) else Nil)
    record(Decision(
      kind = Decision.Kind.InjectedMember, subject = ctxSym, subjectFqn = fqn,
      detail = Map(
        "minted"  -> "context-type",
        "holder"  -> h.holder,
        "members" -> statics.values.toList.filterNot(_.contains('.')).distinct.sorted.mkString("|"),
        "why"     -> ("the port asked for a MINTED context, so this type is the engine's own: one " +
          "mutable member per mapped holder static, set by the consumer's bootstrap where it used " +
          "to set the statics. `inject` a type of your own for anything richer"),
      ),
      reason = Reason.Configured(name, h.holder),
      origin = Origin.synthetic,
    ))
    p.rebuilt(units  = p.units :+ Tree.ClassDef(ctxSym, Nil, scala.None, body, o),
              symbols = SymbolTable(p.symbols.all ++ mint.minted))

  // ---- the DERIVED residual holder ------------------------------------------------------------

  /** The holder survives iff something still READS it — DERIVED, neither a knob nor a fixed answer.
    *
    * Every mapped static whose reads all moved onto the context is dropped from the holder; a static
    * with a residual read stays, and that read is already a counted `residual-global-read` seam. A
    * deprecated forwarding object would be the ambient `given` with extra steps, and a policy knob
    * would be a second way to state what the closure has already computed (§5.1's *derived, not
    * listed*). */
  private def residualHolder(p: Program, h: ContextHolder, statics: Map[SymId, String]): Program =
    val gone = statics.keySet.filter(s => !p.usages(s).exists(_.kind == UsageKind.TermRef))
    if gone.isEmpty then return p
    gone.toList.sortBy(_.raw).foreach { s =>
      // the SUBJECT is the OWNING TYPE, not the member: a dropped member has no declaration for a
      // note to sit above, so `PorterNote.InBody` puts it at the head of the type's body — which the
      // emitter looks up by the TYPE's symbol. The member's own name is `subjectFqn`.
      p.symbolOf(s).foreach(sym => record(Decision(
        kind = Decision.Kind.DroppedMember, subject = sym.owner, subjectFqn = sym.fullName,
        detail = Map(
          "holder" -> h.holder,
          "to"     -> s"${h.context.fqn}.${statics(s)}",
          "why"    -> ("every read of this static now goes through the threaded context, so the " +
            "global it stood for has no reader left — what remains of the holder is what still does"),
        ),
        reason = Reason.Configured(name, h.holder),
        origin = Decision.originOf(p, s),
      )))
    }
    val strip = new Phase:
      def name = "globals->implicits/residual"
      override def transformClassDef(t: Tree.ClassDef)(using Program): Tree.ClassDef =
        t.copy(body = t.body.filterNot { case v: Tree.ValDef => gone(v.symbol); case _ => false })
    given Program = p
    p.rebuilt(units = p.units.map(u => StandardTraversal.mapClassDef(strip, u)))

  // ---- provenance -----------------------------------------------------------------------------

  /** One row per DECLARATION whose emitted signature moved. Nothing for a CALL into a threaded
    * declaration: its argument is supplied by the `using` in scope, so the call site did not change
    * at all — which is the whole reason `using` was chosen over an explicit parameter. */
  private def recordDecisions(p: Program, h: ContextHolder, need: ContextNeed, ctxFqn: String): Unit =
    val deferredFields = need.deferrals.map(_.field).toSet
    def row(s: SymId, to: String): Unit =
      p.symbolOf(s).foreach(sym => record(Decision(
        kind = Decision.Kind.RetypedSignature, subject = s, subjectFqn = sym.fullName,
        detail = Map("from" -> "reads the holder's static state, or reaches something that does",
                     "to" -> to, "key" -> h.holder) ++ need.via(s).map("via" -> _) ++
          Map("why" -> ("the ambient state this declaration read is threaded to it explicitly; a " +
            "call into it is unchanged, since the argument comes from the `using` in scope")),
        reason = Reason.Configured(name, h.holder),
        origin = Decision.originOf(p, s),
      )))
    need.threadedMethods.toList.filterNot(deferredFields).sortBy(_.raw)
      .foreach(m => row(m, s"takes a trailing `(using $ctxFqn)`"))
    need.threadedClasses.toList.sortBy(_.raw)
      .foreach(c => row(c, s"its constructors take `(using $ctxFqn)`"))
    need.scopedOut.toList.sortBy(_.raw).foreach { s =>
      p.symbolOf(s).foreach(sym => record(Decision(
        kind = Decision.Kind.ScopedOut, subject = s, subjectFqn = sym.fullName,
        detail = Map("scope" -> h.scope.fingerprint, "key" -> h.holder,
          "why" -> ("this declaration reads the holder and the holder's `scope` deliberately held " +
            "it back, so it keeps the upstream global while the code around it moved")),
        reason = Reason.Configured(name, h.holder),
        origin = Decision.originOf(p, s),
      )))
    }

  private def refuse(h: ContextHolder, why: String): Unit =
    refusals += PolicyFinding(name, s"GlobalsToImplicitsTransform(holders) `${h.holder}`",
      h.holder, PolicyIssue.Unverifiable, why)

object GlobalsToImplicitsTransform:

  /** what a read site BECOMES — the phase's own record, per (symbol, origin). */
  enum ReadPlan:
    /** through the context in scope: `summon[T].<path>`. */
    case Threaded
    /** through the context companion's `global`: still a global read, and counted as one. */
    case Global
    /** left exactly as it is — the `refuse` boundary, and a scoped-out declaration. Also counted. */
    case Leave

  def isCtor(p: Program, s: SymId): Boolean = p.symbolOf(s).exists(_.name == "<init>")

  /** A symbol MINTER for one holder's run. A value the run owns, never phase-instance state: the
    * predecessor kept a `ListBuffer` on the phase object and drained it never, so a phase instance
    * run twice accumulated the first run's symbols into the second's table. */
  final class Minter(program: Program):
    private var next = program.symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1
    private val buf  = collection.mutable.ListBuffer.empty[Symbol]
    private val usings = collection.mutable.Map.empty[SymId, SymId]

    def minted: List[Symbol] = buf.toList

    private def fresh(): SymId = { val id = SymId(next); next += 1; id }

    /** an external TYPE symbol — owner `SymId.None`, so `Program.owned` says false for it. */
    def tpe(nm: String, full: String): SymId = selfTyped(nm, full, Flags())

    /** a type symbol whose `info` is its own `TypeRef`, which is how every type in the TIR describes
      * itself. */
    def selfTyped(nm: String, full: String, flags: Flags): SymId =
      val id = fresh()
      buf += Symbol(id, nm, full, flags, SymId.None, TypeRepr.TypeRef(TypeRepr.NoPrefix, id))
      id

    def member(nm: String, full: String, owner: SymId, info: TypeRepr, flags: Flags): SymId =
      val id = fresh()
      buf += Symbol(id, nm, full, flags, owner, info)
      id

    /** THE CLAUSE. Anonymous — the emitted parameter has no name at all, because a context parameter
      * named after an emitted root package shadows it and breaks every fully-qualified reference in
      * scope, and this engine emits nothing but fully-qualified references. Nothing reads the name:
      * `using` resolution and `summon` never do. One per owner, so a declaration visited twice does
      * not grow two clauses. */
    def usingParam(owner: SymId, ctxFqn: String, ctxRef: TypeRepr, at: Origin): Tree.ValDef =
      val id = usings.getOrElseUpdate(owner,
        member("", s"$ctxFqn#<using>", owner, ctxRef, Flags(isParam = true, isGiven = true)))
      Tree.ValDef(id, TypeTree(ctxRef, at), scala.None, at)
