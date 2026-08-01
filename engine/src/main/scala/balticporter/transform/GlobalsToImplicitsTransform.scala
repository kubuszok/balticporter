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
  * ==A class a FRAMEWORK instantiates has no caller to change==
  * The closure reasons from the program: it may add a parameter because it can see, and fix, every
  * `new`. A test suite, a `ServiceLoader` implementation and a bean are constructed reflectively from
  * OUTSIDE, so the closure sees no instantiation at all and concludes, correctly and uselessly, that
  * nothing has to be fixed — and a `using` clause on such a constructor emits code that compiles
  * perfectly and cannot be constructed at run time. Measured: 0 scalac errors, 0 seams, 0 policy
  * findings, and a whole suite silently gone (`ENGINE-LIMITS.md` CT7).
  *
  * So attachment has a THIRD answer beside "take the clause" and "be a boundary" —
  * [[ContextHolder.selfSupplied]]: this declaration takes the context WITHOUT taking a parameter,
  * from a `private given` member whose expression the PORT supplies. Which declarations those are is
  * not derivable; the SHAPE is, and [[ContextSeamCheck.Kind.UnconstructedThread]] warns on it.
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
  /** the `sites` entries the binder RESOLVED, per holder: key → the symbols it named. Both halves
    * are needed — the symbols are a `lazy-init` entry's candidate subjects, and the KEY SET is what
    * the dead-binding report is the complement of (`ENGINE-LIMITS.md` CT6). */
  private var boundSites: Map[String, Map[String, List[SymId]]]   = Map.empty
  /** the `selfSupplied` entries the binder RESOLVED, per holder: the TYPE symbol → its policy key.
    * The key is kept beside the symbol because it is the string an agent edits (§4.575) and it is
    * what the decision's `Reason.Configured` carries. */
  private var boundSelf: Map[String, Map[SymId, String]]          = Map.empty

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

      // CLASS ATTACHMENT USED TO BE REFUSED HERE with a counted `Unverifiable` finding, because the
      // TIR edit was correct and the EMISSION was not: the constructor funnel undid it three ways
      // (`ENGINE-LIMITS.md` CT4, 5 scalac errors on this phase's own fixture) — a constructor that
      // had gained a parameter stopped counting as java's nilary root, a promoted primary's
      // parameter list was rebuilt flat and lost the `using` grouping, and a subclass of the first
      // shape saw two applicable constructors. All three were in the constructor region `DESIGN.md`
      // §8.2 owns, and all three are closed there: the plan models parameter GROUPS
      // (`CtorFunnel.Plan.givens`), every "is this constructor nilary" question in the funnel reads
      // `CtorFunnel.valueParams`, and the emitter renders the clause through `paramClause`. This
      // phase needs no code for it — which is the point, and the reason the refusal was a refusal
      // rather than a workaround: a clause the funnel will not carry is not a clause, and every
      // workaround would have been a second constructor plan.

      h.context match
        case ContextType.Minted(fqn) =>
          h.members.filter((_, p) => p.contains('.')).toList.sorted.foreach((f, p) =>
            malformedEntry(h, "members", MemberKey(h.holder, f).render, s"`$p` is a two-hop PATH and the context " +
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

      boundSites = boundSites.updated(h.holder, h.sites.keys.toList.sorted.flatMap(k =>
        binder.bindMembers(name, s"GlobalsToImplicitsTransform(holders) `${h.holder}`.sites", k)
          .toOption.map(hits => k -> hits.flatMap(_.sym))).toMap)

      // THE THIRD ANSWER's keys are TYPE keys (`ENGINE-LIMITS.md` CT7): the shape is a class a
      // framework CONSTRUCTS, so what a port names here is a type. A member key would be a different
      // question (a method a framework CALLS reflectively) with a different answer, and `bindType`
      // reports the `#` form as malformed rather than guessing which was meant.
      h.selfSupplied.toList.sorted.foreach { (t, src) =>
        if src.trim.isEmpty then
          malformedEntry(h, "selfSupplied", t, "the entry names a type and supplies no expression, " +
            "so the type would take neither a constructor clause nor a `given` member and every " +
            "`summon` in its body would be a compile error at a line the port never wrote. Give " +
            "the expression that yields the context — a call into a fixture this port hand-wrote")
      }
      boundSelf = boundSelf.updated(h.holder, h.selfSupplied.keys.toList.sorted.flatMap(t =>
        binder.bindType(name, s"GlobalsToImplicitsTransform(holders) `${h.holder}`.selfSupplied", t)
          .toOption.map(_ -> t)).toMap)

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
    PolicyReport.fromBindings(records) ++ PolicyReport(malformed ++ refusals.toList ++ deadSites.toList)

  private val refusals = collection.mutable.ListBuffer.empty[PolicyFinding]

  /** A BOUND `sites` ENTRY THAT SELECTED NO SITE — `ENGINE-LIMITS.md` CT6's second face, and the
    * third face of "never fired".
    *
    * `PolicyBinder.bindMembers` asks *does this program declare this member*, and a real field
    * answers `yes` whether or not anything in the run ever reaches it. CT6 measured exactly that:
    * two `lazy-init` keys were added to a real port, both BOUND, `policy` stayed at its floor, and
    * the emitted output was byte-identical with them and without them. A byte-identity experiment is
    * not a report; this is.
    *
    * Only entries whose BINDING succeeded are reported, or an entry naming a member this program
    * does not have would be reported twice — once by the binder as `NeverMatched` and once here —
    * for one mistake with one fix. */
  private val deadSites = collection.mutable.ListBuffer.empty[PolicyFinding]

  private def recordDeadSites(h: ContextHolder, fired: Set[String]): Unit =
    boundSites.getOrElse(h.holder, Map.empty).keySet.diff(fired).toList.sorted.foreach { k =>
      val what = h.sites.get(k) match
        case Some(ContextSite.LazyInit) =>
          "the entry names a member of this program and NO initialisation could be moved off it: " +
            "either it is not a static with an initialiser of its own (and not a class initialiser " +
            "assigning one), or that initialiser neither reads a mapped static nor constructs a " +
            "type this program declares, so there is nothing for a context to arrive for. " +
            "`PolicyBinder` cannot see this — it asks whether the MEMBER exists, which a real field " +
            "answers whether or not the phase ever reaches it"
        case _ =>
          "the entry names a member of this program and no READ of a mapped static resolved to it, " +
            "so it overrode nothing and removing it would change no emitted byte. `residual-global` " +
            "and `refuse` decide how a READ is spelled at a boundary; an UNSUPPLIABLE USE — a " +
            "declaration that constructs or calls something threaded — has no read to spell, and " +
            "its exit is `lazy-init` or moving the use into a declaration the closure can reach"
      deadSites += PolicyFinding(name, s"GlobalsToImplicitsTransform(holders) `${h.holder}`.sites",
        k, PolicyIssue.NeverMatched, s"$what. Delete the entry, or fix the key if it was meant to " +
          "name a different member")
    }

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
    seamLog.clear(); refusals.clear(); deadSites.clear()
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
    // an entry whose expression is empty is MALFORMED and reported as such; it must not also take
    // the type out of the threading, or one mistake would silently produce a second, worse one.
    val selfSupplied = boundSelf.getOrElse(h.holder, Map.empty)
      .filter((_, k) => h.selfSupplied.get(k).exists(_.trim.nonEmpty))
    /** the type → the Scala the port wrote for it, which the emitter splices verbatim. */
    val selfSource: Map[SymId, String] = selfSupplied.map((s, k) => s -> h.selfSupplied(k))
    val need  = new ContextNeed(program0, graph, h, statics, boundPromote.getOrElse(h.holder, Set.empty),
                                (k, s, key, d, o, e) => seamLog += ContextSeamCheck.Finding(k, s, key, d, o, e),
                                (s, why) => refuse(h, why),
                                boundSites.getOrElse(h.holder, Map.empty),
                                selfSupplied)
    need.grow()

    // ---- the context TYPE, and the terms that read through it ---------------------------------
    val ctxFqn = h.context.fqn
    val ctxSym = mint.selfTyped(ctxFqn.split('.').last, ctxFqn, Flags(isFinal = true))
    val ctxRef = TypeRepr.TypeRef(TypeRepr.NoPrefix, ctxSym)
    val o      = Origin.synthetic

    val predefSym = mint.tpe("Predef", "scala.Predef")
    val summonSym = mint.member("summon", "scala.Predef#summon", predefSym, ctxRef, Flags(isStatic = true))
    val applySym  = mint.member("apply", MemberKey(ctxFqn, "apply").render, ctxSym, ctxRef, Flags(isStatic = true))
    val globalSym = mint.member("global", MemberKey(ctxFqn, "global").render, ctxSym, ctxRef,
                                Flags(isStatic = true, isMutable = true))
    val segCache  = collection.mutable.Map.empty[String, SymId]
    def segSym(seg: String): SymId =
      segCache.getOrElseUpdate(seg, mint.member(seg, MemberKey(ctxFqn, seg).render, ctxSym, TypeRepr.NoType, Flags()))

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
            // WHICH initialiser it came out of — a `<clinit>` assignment or the field's own
            // initialiser. A note that names the wrong one says something false about code the
            // reader is holding, which is the whole reason a note sits beside the declaration.
            "from" -> (if d.clinit == SymId.None then "the field's own initialiser"
                       else "assigned by the class initialiser"),
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
        // THE THIRD ANSWER (`ENGINE-LIMITS.md` CT7): no clause anywhere, and a `given` member at the
        // HEAD of the body instead. At the head because a class body is a constructor: a statement
        // that uses the context before the given is initialised would read `null`, and the reference
        // hand port writes it first for the same reason.
        if need.selfSuppliedClasses(t.symbol) then
          t.copy(body = mint.givenMember(t.symbol, ctxFqn, ctxRef, selfSource(t.symbol), t.origin) :: t.body)
        else if !need.threadedClasses(t.symbol) then t
        else
          val ctors = t.body.collect { case d: Tree.DefDef if isCtor(summon[Program], d.symbol) => d.symbol }
          if ctors.isEmpty then
            // A java INTERFACE has no constructor, so a trait the manifest promoted to an abstract
            // class has nothing to hang the clause on and one is minted — the promotion is only half
            // done otherwise, which is the shape of defect a refusal is supposed to prevent.
            val at   = t.origin
            val ctor = mint.member(ContextNeed.CtorName,
              MemberKey(summon[Program].symbolOf(t.symbol).map(_.fullName).getOrElse("?"),
                        ContextNeed.CtorName).render,
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
    recordSelfSupplied(out, h, need, ctxFqn, selfSupplied, selfSource)
    recordDeadSelf(h, need)
    // LAST: `readPlan` above is what consults a residual-global/refuse `sites` entry, so anything
    // read before it would report an entry that had not been asked yet.
    recordDeadSites(h, need.firedSites)
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
      .map((path, info) => mint.member(path, MemberKey(fqn, path).render, ctxSym, info, Flags(isMutable = true)) -> info)
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
        // no `key`: `Reason.Configured(name, h.holder)` below already carries it, and a decider
        // that spells it twice renders `key=… key=…` in the porter note.
        detail = Map("from" -> "reads the holder's static state, or reaches something that does",
                     "to" -> to) ++ need.via(s).map("via" -> _) ++
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
        detail = Map("scope" -> h.scope.fingerprint,
          "why" -> ("this declaration reads the holder and the holder's `scope` deliberately held " +
            "it back, so it keeps the upstream global while the code around it moved")),
        reason = Reason.Configured(name, h.holder),
        origin = Decision.originOf(p, s),
      )))
    }

  /** One row per FRAMEWORK-INSTANTIATED type — CLAUDE.md §1(b)'s third answer, recorded.
    *
    * It is an `InjectedMember` and not a `RetypedSignature` because that is precisely what happened:
    * the signature did NOT move (which is the whole point), and what the port gained is a member the
    * engine put there. The subject is the TYPE, so the porter note sits above the emitted `class`
    * line — where an agent reading the generated file asks the question. */
  private def recordSelfSupplied(p: Program, h: ContextHolder, need: ContextNeed, ctxFqn: String,
                                 bound: Map[SymId, String], src: Map[SymId, String]): Unit =
    need.selfSuppliedClasses.toList.sortBy(_.raw).foreach { c =>
      p.symbolOf(c).foreach(sym => record(Decision(
        kind = Decision.Kind.InjectedMember, subject = c, subjectFqn = sym.fullName,
        detail = Map(
          "given"  -> ctxFqn,
          "source" -> src.getOrElse(c, ""),
          "from"   -> "a constructor clause the closure would otherwise have attached",
          "to"     -> s"a `private given $ctxFqn` member of this type",
          "why"    -> ("this type is constructed by a FRAMEWORK, not by this program, and a " +
            "reflective construction cannot supply a `using` — so it takes the context without " +
            "taking a parameter, from an expression this port wrote"),
        ),
        reason = Reason.Configured(name, bound.getOrElse(c, h.holder)),
        origin = Decision.originOf(p, c),
      )))
    }

  /** A BOUND `selfSupplied` ENTRY THE CLOSURE NEVER REACHED — the third answer's own dead binding.
    *
    * `PolicyBinder.bindType` asks *does this program declare this type*, which a real class answers
    * whether or not the threading would ever have touched it. An entry naming a class the closure
    * does not reach takes nothing out of the threading and emits no `given` member, so removing it
    * would change no emitted byte — the exact blindness CT6 measured for `sites`, one key over. */
  private def recordDeadSelf(h: ContextHolder, need: ContextNeed): Unit =
    val reached = need.selfSuppliedClasses
    boundSelf.getOrElse(h.holder, Map.empty).toList.filterNot((s, _) => reached(s))
      .map((_, k) => k)
      // an entry with no expression is already reported as `Malformed`, and one mistake gets one
      // finding: reported as both, the second reading ("your key names nothing the closure reached")
      // contradicts the first and the reader has to work out which is true.
      .filter(k => h.selfSupplied.get(k).exists(_.trim.nonEmpty))
      .sorted.foreach { k =>
        deadSites += PolicyFinding(name, s"GlobalsToImplicitsTransform(holders) `${h.holder}`.selfSupplied",
          k, PolicyIssue.NeverMatched, "the entry names a type of this program that the closure " +
            "never reached: nothing in it reads the holder and nothing it uses is threaded, so it " +
            "would have taken no constructor clause and there is no context for a `given` member " +
            "to supply. No `given` was emitted and removing the entry would change no emitted byte. " +
            "Delete it, or fix the key if it was meant to name a different type")
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
    private val givens = collection.mutable.Map.empty[SymId, SymId]

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
        member("", MemberKey(ctxFqn, "<using>").render, owner, ctxRef, Flags(isParam = true, isGiven = true)))
      Tree.ValDef(id, TypeTree(ctxRef, at), scala.None, at)

    /** THE THIRD ANSWER's member: `private given <ctx> = <the port's expression>`, at the head of a
      * framework-instantiated type's body (`ENGINE-LIMITS.md` CT7).
      *
      * ANONYMOUS, for the same reason [[usingParam]] is: a name here would be a name this engine
      * minted into a class whose every other reference is fully qualified, and nothing reads a
      * given's name. `private`, which is the reference hand port's shape and is what keeps the
      * member off the type's published surface — it is machinery, not API.
      *
      * The RHS is [[Tree.Opaque]] — the node for a term the TIR does not model, kept typed so the
      * tree stays whole — because the expression is Scala the frontend never saw. It is emitted
      * verbatim and is NOT type-checked by the engine: the target compiler is the gate, and a
      * mis-spelled fixture is one error at one line the source map attributes.
      */
    def givenMember(owner: SymId, ctxFqn: String, ctxRef: TypeRepr, src: String, at: Origin): Tree.ValDef =
      val id = givens.getOrElseUpdate(owner,
        member("", MemberKey(ctxFqn, "<given>").render, owner, ctxRef,
               Flags(isGiven = true, isPrivate = true)))
      Tree.ValDef(id, TypeTree(ctxRef, at), Some(Tree.Opaque(src, ctxRef, at)), at)
