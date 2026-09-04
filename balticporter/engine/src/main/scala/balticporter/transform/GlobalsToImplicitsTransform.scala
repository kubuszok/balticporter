package balticporter.transform

import balticporter.core.{MergeablePolicy, PolicyFinding, PolicyIssue, PolicyReport, PolicySource}
import balticporter.tir.*

/** GLOBALS → CONTEXT: a Java class whose `static` state is an ambient CONTEXT becomes a value
  * threaded through the program as a Scala 3 `using` parameter, found by a five-edge closure over
  * [[ContextNeed]] and rewritten through [[ContextHolder]]'s member map. A class/field initialiser
  * is a BOUNDARY (no signature to thread through); a framework-constructed class takes
  * [[ContextHolder.selfSupplied]] instead of a clause.
  * CLAUDE.md §1(b), §1.5; DESIGN.md §8.4; ENGINE-LIMITS CT4, CT6, CT7, CT8, CT9
  */
final class GlobalsToImplicitsTransform(
    val holders: List[ContextHolder] = Nil,
    /** the per-declaration half of a holder the BASE declares; empty in a base. ENGINE-LIMITS CT8 */
    val extensions: List[ContextHolderExtension] = Nil,
    /** Adds `(using GivenType[T])` to a class's constructors, `T` its own type parameter at a given
      * index. Keyed on the class's upstream FQN; value is the given type FQN, optionally suffixed
      * `:N` for the type-parameter index (0-based, default 0). Propagates to subclass constructors
      * through `extends`. Empty map is the no-op. */
    val requiredGivens: Map[String, String] = Map.empty,
) extends Phase, Rewrite, PolicySource, MergeablePolicy, PolicyBound:

  import GlobalsToImplicitsTransform.*

  def name = "globals->implicits"

  /** counts every place threading stopped: a reflectively-constructed declaration, a hand-written
    * caller no key can add a clause to, a residual global. */
  def accountedBy: Set[String] = Set(ContextSeamCheck.Name)

  /** policy keys are written in the UPSTREAM namespace; package rename runs LAST (§4.56). */
  override def runsBefore: Set[String] = Set("package-rename")

  /** the holders this instance runs, each with its extensions folded in. A dangling extension —
    * naming a holder nothing in the chain declares — folds into nothing; see [[danglingFindings]]. */
  lazy val effectiveHolders: List[ContextHolder] =
    holders.map(h => extensions.filter(_.holder == h.holder).foldLeft(h)(_ extendedBy _))

  private lazy val dangling: List[ContextHolderExtension] =
    extensions.filterNot(e => holders.exists(_.holder == e.holder))

  /** the effective policy, sorted and rendered — two modules that agree must compare equal (§1.5).
    * Read off [[effectiveHolders]] so a holder stated inline and one stated as holder+extension
    * fingerprint the same. */
  def surfaceFingerprint: String =
    val rg = if requiredGivens.isEmpty then "" else
      "|rg=" + requiredGivens.toList.sorted.map((k, v) => s"$k->$v").mkString(",")
    (effectiveHolders.map(_.fingerprint) ++ dangling.map(_.fingerprint)).sorted.mkString(";") + rg

  /** every shared-surface SUBJECT this instance's policy is keyed on — holder FQNs (of holders and
    * of extensions), every per-declaration key, every promotion and every scope entry, through
    * [[MergeablePolicy.subjectOf]]. A dependent naming one of the base's own DECLARATIONS re-shapes
    * a surface it does not own; naming its own types is the point. */
  def subjects: Set[String] =
    val fromHolders = holders.flatMap(h =>
      (Set(h.holder) ++ h.sites.keySet ++ h.selfSupplied.keySet ++ h.retain.keySet ++
       h.cache.keySet ++ h.promoteToClass ++ h.scope.entries))
    val fromExts = extensions.flatMap(e => Set(e.holder) ++ e.keys)
    val fromGivens = requiredGivens.keySet
    (fromHolders ++ fromExts ++ fromGivens).map(MergeablePolicy.subjectOf).toSet

  /** THE MERGE CONTRACT (DESIGN.md §8.13); division is `ContextHolder.sharedSurface`.
    *   - holders UNION by holder FQN; a holder only one side declares is an addition.
    *   - a holder BOTH sides declare must AGREE on its shared surface, or the merge refuses.
    *   - `sites` and `selfSupplied` UNION, refusing same-key-different-value.
    *   - extensions carry across; a dangling one folds into whatever the merge brings into scope.
    *
    * `added` is every subject the later instance holds that this one did not — what `SurfaceFold`
    * screens against `governs`. */
  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged] = later match
    case o: GlobalsToImplicitsTransform =>
      val mine   = holders.map(h => h.holder -> h).toMap
      val theirs = o.holders.map(h => h.holder -> h).toMap
      val surfaceClash = (mine.keySet & theirs.keySet).toList.sorted
        .filter(k => mine(k).sharedSurface != theirs(k).sharedSurface)
        .map(k => s"""both modules declare the holder "$k" and its SHARED SURFACE differs — """ +
          s""""${mine(k).sharedSurface}" against "${theirs(k).sharedSurface}". The context type, """ +
          "the member map, the attachment mode, the read shape, the boundary default, the " +
          "promotions and the scope are all facts about the SIGNATURES this policy emits, so two " +
          "answers is a choice and not a composition. A dependent adds `sites`/`selfSupplied` " +
          "entries for its OWN declarations and inherits the rest")
      val siteClash = for
        k         <- (mine.keySet & theirs.keySet).toList.sorted
        (key, v)  <- theirs(k).sites.toList.sortBy(_._1)
        v2        <- mine(k).sites.get(key)
        if v2 != v
      yield s"""both modules give the site "$key" a policy, "${v2.token}" and "${v.token}""""
      val selfClash = for
        k        <- (mine.keySet & theirs.keySet).toList.sorted
        (key, v) <- theirs(k).selfSupplied.toList.sorted
        v2       <- mine(k).selfSupplied.get(key)
        if v2 != v
      yield s"""both modules make "$key" self-supplied, from "$v2" and from "$v""""
      // a retained member's NAME is emitted surface: two names for one type's retained context
      // means whichever `selfSupplied` expression names one compiles against only one port.
      val retainClash = for
        k        <- (mine.keySet & theirs.keySet).toList.sorted
        (key, v) <- theirs(k).retain.toList.sorted
        v2       <- mine(k).retain.get(key)
        if v2 != v
      yield s"""both modules RETAIN the context on "$key", as "$v2" and as "$v""""
      // same reason for a CACHED accessor's name.
      val cacheClash = for
        k        <- (mine.keySet & theirs.keySet).toList.sorted
        (key, v) <- theirs(k).cache.toList.sorted
        v2       <- mine(k).cache.get(key)
        if v2 != v
      yield s"""both modules CACHE the context on "$key", as "$v2" and as "$v""""
      val givenClash = for
        (k, v) <- o.requiredGivens.toList.sorted
        v2     <- requiredGivens.get(k)
        if v2 != v
      yield s"""both modules require a given on "$k", "$v2" and "$v""""
      (surfaceClash ++ siteClash ++ selfClash ++ retainClash ++ cacheClash ++ givenClash) match
        case Nil =>
          val merged = (mine.keySet ++ theirs.keySet).toList.sorted.map { k =>
            (mine.get(k), theirs.get(k)) match
              case (Some(a), Some(b)) => a.copy(sites = a.sites ++ b.sites,
                                                selfSupplied = a.selfSupplied ++ b.selfSupplied,
                                                retain = a.retain ++ b.retain,
                                                cache = a.cache ++ b.cache)
              case (Some(a), None)    => a
              case (None, Some(b))    => b
              case (None, None)       => sys.error("unreachable: a key from the union of two maps")
          }
          Right(MergeablePolicy.Merged(
            new GlobalsToImplicitsTransform(merged, (extensions ++ o.extensions).distinct,
              requiredGivens ++ o.requiredGivens),
            o.subjects -- subjects))
        case whys => Left(whys.mkString("; ") +
          " — two answers for one key is a threading whose outcome depends on which manifest was read")
    case other =>
      Left(s"`${other.name}` is not a `GlobalsToImplicitsTransform`, so there is no policy to compose")

  // ---- policy, bound before the pipeline starts ---------------------------------------------

  private var records: List[PolicyBinder.Record]                  = Nil
  private var malformed: List[PolicyFinding]                      = Nil
  private var boundStatics: Map[String, Map[String, List[SymId]]] = Map.empty
  private var boundHolder: Map[String, SymId]                     = Map.empty
  private var boundPromote: Map[String, Set[SymId]]               = Map.empty
  /** `sites` entries resolved per holder: key -> symbols named. Used for the CT6 dead-binding
    * report and as a `lazy-init` entry's candidate subjects. */
  private var boundSites: Map[String, Map[String, List[SymId]]]   = Map.empty
  /** `selfSupplied` entries resolved per holder: TYPE symbol -> its policy key (§4.575). */
  private var boundSelf: Map[String, Map[SymId, String]]          = Map.empty
  /** `retain` entries resolved per holder: TYPE symbol -> the policy key that named it. */
  private var boundRetain: Map[String, Map[SymId, String]]        = Map.empty
  /** `cache` entries resolved per holder: TYPE symbol -> the policy key that named it. */
  private var boundCache: Map[String, Map[SymId, String]]         = Map.empty
  /** `requiredGivens` entries resolved to the class symbol — class SymId -> given type FQN. */
  private var boundGivens: Map[SymId, String]                     = Map.empty

  def bindPolicy(binder: PolicyBinder): Unit =
    val bad = collection.mutable.ListBuffer.empty[PolicyFinding]
    def malformedEntry(h: ContextHolder, setting: String, key: String, what: String): Unit =
      bad += PolicyFinding(name, s"GlobalsToImplicitsTransform(holders) `${h.holder}`.$setting",
        key, PolicyIssue.Malformed, what)

    effectiveHolders.foreach { h =>
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

      // …and `retain`'s keys are TYPE keys for the same reason, with the VALUE screened here rather
      // than at emission: it is spliced into a `val <name>:` header, so anything that is not a plain
      // identifier is a SYNTAX error in the emitted file — an error scalac reports at a line the port
      // never wrote, which is the one shape §4.45 says a policy must not produce.
      h.retain.toList.sorted.foreach { (t, nm) =>
        if !isPlainIdentifier(nm) then
          malformedEntry(h, "retain", t, s"`$nm` is not a plain identifier, and this value is spliced " +
            "into the header of the `val` this type will carry — anything else is a SYNTAX error in " +
            "the emitted file, at a line the port never wrote. Give the MEMBER NAME the retained " +
            "context should be readable under (the reference hand port spells its own like a field)")
      }
      boundRetain = boundRetain.updated(h.holder, h.retain.toList.sorted.flatMap((t, nm) =>
        binder.bindType(name, s"GlobalsToImplicitsTransform(holders) `${h.holder}`.retain", t)
          .toOption.filter(_ => isPlainIdentifier(nm)).map(_ -> t)).toMap)

      // …and `cache`'s, screened the same way and for the same reason one key over: the name is
      // spliced into a `def <name>:` header AND into the private holder's, so anything that is not
      // a plain identifier is a SYNTAX error in the emitted file at a line the port never wrote.
      h.cache.toList.sorted.foreach { (t, nm) =>
        if !isPlainIdentifier(nm) then
          malformedEntry(h, "cache", t, s"`$nm` is not a plain identifier, and this value is spliced " +
            "into the headers of the accessor and of the private holder this type will carry — " +
            "anything else is a SYNTAX error in the emitted file, at a line the port never wrote. " +
            "Give the MEMBER NAME the cached context should be readable under")
      }
      boundCache = boundCache.updated(h.holder, h.cache.toList.sorted.flatMap((t, nm) =>
        binder.bindType(name, s"GlobalsToImplicitsTransform(holders) `${h.holder}`.cache", t)
          .toOption.filter(_ => isPlainIdentifier(nm)).map(_ -> t)).toMap)

      boundPromote = boundPromote.updated(h.holder, h.promoteToClass.flatMap(t =>
        binder.bindType(name, s"GlobalsToImplicitsTransform(holders) `${h.holder}`.promoteToClass", t)
          .toOption))

      h.scope.entries.foreach(e =>
        binder.bindScope(name, s"GlobalsToImplicitsTransform(holders) `${h.holder}`.scope", e))
    }
    requiredGivens.foreach { (cls, givenFqn) =>
      binder.bindType(name, s"GlobalsToImplicitsTransform.requiredGivens", cls)
        .toOption.foreach(s => boundGivens = boundGivens.updated(s, givenFqn))
    }
    malformed = bad.toList
    records   = binder.recordsFor(name)

  /** the never-fired half plus this phase's own malformed entries and counted refusals. */
  def policyReport: PolicyReport =
    PolicyReport.fromBindings(records) ++
      PolicyReport(malformed ++ danglingFindings ++ refusals.toList ++ deadSites.toList)

  /** an extension naming a holder nothing in the chain declares — `PolicyBinder` cannot see this,
    * since the extension's own keys bind against a program that has them; it is the HOLDER
    * that's missing. ENGINE-LIMITS CT8 */
  private def danglingFindings: List[PolicyFinding] = dangling.map { e =>
    PolicyFinding(name, "GlobalsToImplicitsTransform(extensions)", e.holder, PolicyIssue.Malformed,
      "this module extends a holder that neither it nor any of its bases declares, so every entry " +
        "in the extension names a site of a threading that is not happening. An extension carries " +
        "the PER-DECLARATION half of a holder the shared surface already states (§1.5); declare " +
        "the holder in the base manifest, or fix the FQN if it was meant to name a different one")
  }

  /** a name the emitter may splice into a `val <nm>:` header. Deliberately NOT scala's full
    * identifier grammar — a backquoted or operator name would be legal scala and an awful member to
    * put on a ported surface, and a port that wants one can say so when the case exists. */
  private def isPlainIdentifier(nm: String): Boolean =
    nm.nonEmpty && (nm.head.isLetter || nm.head == '_') && nm.forall(c => c.isLetterOrDigit || c == '_')

  private val refusals = collection.mutable.ListBuffer.empty[PolicyFinding]

  /** a bound `sites` entry that selected no site — `bindMembers` asks whether the program declares
    * the member, not whether the run ever reaches it, so this reports the residue that binding alone
    * cannot see. Only entries whose binding succeeded are reported, so a truly absent member is
    * reported once, by the binder, and not twice. ENGINE-LIMITS CT6 */
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

  /** every seam this run drew, restricted to the units it actually emits — a dependent's `Program`
    * holds its base's units too, and a seam inside one of those is the base's finding. ENGINE-LIMITS D2 */
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
    val afterHolders =
      if effectiveHolders.isEmpty then program
      else effectiveHolders.foldLeft(program)((p, h) => runHolder(p, h))
    if boundGivens.isEmpty then afterHolders
    else applyRequiredGivens(afterHolders)

  /** Adds `(using GivenType[T])` clauses to constructors of classes listed in `requiredGivens`,
    * `T` the class's own first type parameter, built structurally so the package rename reaches it.
    * Unlike holder-based threading this runs no closure: the class is named directly, and callers
    * supply the given by inline resolution. An abstract class's clause propagates through `extends`.
    */
  private def applyRequiredGivens(program0: Program): Program =
    val mint = new Minter(program0)
    val o = Origin.synthetic

    // transitive closure: a generic class C[T] constructing a bounded-given class B[T] with its
    // own first type parameter needs the same given threaded through its own constructors.
    val allClassDefs = program0.units.flatMap(StandardTraversal.allClassDefs(_)(using program0))
    def findBoundedConstruction(cd: Tree.ClassDef): Option[String] =
      if cd.tparams.isEmpty then return None
      val firstTp = cd.tparams.head.symbol
      boundGivens.view.flatMap { (givenClassSym, givenFqn) =>
        def hasInstantiation(stmts: List[Statement]): Boolean = stmts.exists(hasNew)
        def hasNew(t: Tree): Boolean = t match
          case Tree.New(tpt, _, _, _) =>
            tpt.tpe match
              case TypeRepr.AppliedType(TypeRepr.TypeRef(_, headSym), args) =>
                headSym == givenClassSym && args.headOption.exists {
                  case TypeRepr.TypeRef(_, s) => s == firstTp
                  case _ => false
                }
              case TypeRepr.TypeRef(_, headSym) =>
                headSym == givenClassSym
              case _ => false
          case a: Tree.Apply => hasNew(a.fun) || a.args.exists(hasNew)
          case ta: Tree.TypeApply => hasNew(ta.fun)
          case b: Tree.Block => b.stats.exists(hasNew) || hasNew(b.expr)
          case sel: Tree.Select => hasNew(sel.qual)
          case typed: Tree.Typed => hasNew(typed.expr)
          case ifc: Tree.If => hasNew(ifc.cond) || hasNew(ifc.thenp) || hasNew(ifc.elsep)
          case d: Tree.DefDef => d.rhs.exists(hasNew)
          case v: Tree.ValDef => v.rhs.exists(hasNew)
          case a: Tree.Assign => hasNew(a.rhs)
          case _ => false
        if hasInstantiation(cd.body) then Some(givenFqn)
        else None
      }.headOption
    var changed = true
    while changed do
      changed = false
      for cd <- allClassDefs do
        if !boundGivens.contains(cd.symbol) then
          findBoundedConstruction(cd).foreach { givenFqn =>
            boundGivens = boundGivens.updated(cd.symbol, givenFqn)
            changed = true
          }

    // a value may contain `|` to name MULTIPLE givens, each optionally suffixed `:N` for the
    // type-parameter index (0-based, default 0) — e.g. "lowlevel.MkArray:0|lowlevel.MkArray:1"
    def parseGivenSpec(raw: String): List[(String, Int)] =
      raw.split('|').toList.map { part =>
        part.lastIndexOf(':') match
          case -1 => (part, 0)
          case i  =>
            val suffix = part.substring(i + 1)
            scala.util.Try(suffix.toInt).toOption match
              case Some(idx) => (part.substring(0, i), idx)
              case None      => (part, 0)
      }

    // build a map from class SymId -> list of (givenTypeSym, appliedTypeRef)
    val givenEntries: Map[SymId, List[(SymId, TypeRepr)]] = boundGivens.flatMap { (classSym, givenFqnRaw) =>
      val specs = parseGivenSpec(givenFqnRaw)
      val classDef = program0.definitionOf(classSym).collect { case cd: Tree.ClassDef => cd }
      classDef.map { cd =>
        val entries = specs.flatMap { (givenFqn, tpIndex) =>
          cd.tparams.lift(tpIndex).map { tp =>
            val tpRef = TypeRepr.TypeRef(TypeRepr.NoPrefix, tp.symbol)
            val givenTypeSym = mint.tpe(givenFqn.split('.').last + (if tpIndex > 0 then s"$$$tpIndex" else ""), givenFqn)
            val appliedType = TypeRepr.AppliedType(TypeRepr.TypeRef(TypeRepr.NoPrefix, givenTypeSym), List(tpRef))
            (givenTypeSym, appliedType)
          }
        }
        classSym -> entries
      }.filter(_._2.nonEmpty)
    }

    if givenEntries.isEmpty then return program0

    val edit = new Phase:
      def name = "globals->implicits/required-givens"
      override def transformClassDef(t: Tree.ClassDef)(using p: Program): Tree.ClassDef =
        givenEntries.get(t.symbol) match
          case Some(entries) if entries.nonEmpty =>
            val ctors = t.body.collect { case d: Tree.DefDef if isCtor(p, d.symbol) => d.symbol }
            if ctors.isEmpty then t
            else t.copy(body = t.body.map {
              case d: Tree.DefDef if ctors.contains(d.symbol) =>
                val params = entries.map { (_, appliedType) =>
                  mint.usingParam(d.symbol, appliedType.toString, appliedType, d.origin)
                }
                d.copy(paramss = d.paramss :+ params)
              case s => s
            })
          case _ => t

    val prog1 = program0.rebuilt(symbols = SymbolTable(program0.symbols.all ++ mint.minted))
    val units1 = prog1.units.map(u => StandardTraversal.mapClassDef(edit, u)(using prog1))
    val prog2 = prog1.rebuilt(units = units1, symbols = SymbolTable(prog1.symbols.all ++ mint.minted))

    // record decisions
    boundGivens.foreach { (classSym, givenFqn) =>
      program0.symbolOf(classSym).foreach { s =>
        record(Decision(
          kind = Decision.Kind.RequiredGiven,
          subject = classSym,
          subjectFqn = s.fullName,
          detail = Map("given" -> givenFqn, "why" -> ("a retarget construction inside this class's " +
            "body needs MkArray[T] and the factory's inline summon cannot resolve a type parameter")),
          reason = Reason.Configured(name, s"requiredGivens/${s.fullName}"),
          origin = Decision.originOf(program0, classSym),
        ))
      }
    }

    prog2.rebuilt(xref = Xref.build(prog2.units))

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
    // an entry with an empty expression is malformed and reported; it must not also unthread its type.
    val selfSupplied = boundSelf.getOrElse(h.holder, Map.empty)
      .filter((_, k) => h.selfSupplied.get(k).exists(_.trim.nonEmpty))
    /** the type → the Scala the port wrote for it, spliced verbatim by the emitter. */
    val selfSource: Map[SymId, String] = selfSupplied.map((s, k) => s -> h.selfSupplied(k))
    /** the type → the member name its retained context is readable under. Bound entries only. */
    val retainOf: Map[SymId, String] =
      boundRetain.getOrElse(h.holder, Map.empty).flatMap((s, k) => h.retain.get(k).map(s -> _))
    /** the type → the member name its CACHED context is readable under. */
    val cacheOf: Map[SymId, String] =
      boundCache.getOrElse(h.holder, Map.empty).flatMap((s, k) => h.cache.get(k).map(s -> _))
    /** the types a `cache` entry actually minted on — complements the dead-binding report, since a
      * `cache` key binds against a real class whether or not the closure threaded it. ENGINE-LIMITS CT6 */
    val cacheFired = collection.mutable.Set.empty[SymId]
    val need  = new ContextNeed(program0, graph, h, statics, boundPromote.getOrElse(h.holder, Set.empty),
                                (k, s, key, d, o, e) => seamLog += ContextSeamCheck.Finding(k, s, key, d, o, e),
                                (s, why) => refuse(h, why),
                                boundSites.getOrElse(h.holder, Map.empty),
                                selfSupplied)
    need.grow()

    // CT11: remove stale UnsuppliableUse seams for fields that became holders — the growth
    // records the seam BEFORE discoverFieldHolders resolves it, so the stale row stays.
    if need.fieldHolders.nonEmpty then
      val held = need.fieldHolders.keySet
      seamLog.filterInPlace(f =>
        !(f.kind == ContextSeamCheck.Kind.UnsuppliableUse && held.contains(f.enclosing)))

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

    /** `scala.Predef.summon[T]`, or `T.apply()`. Built structurally, not as text — a name spliced
      * into a string would be the one reference the package rename (§4.56) cannot see. */
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
          // no `key` in detail: `Reason.Configured` already carries it (§4.575).
          detail = Map(
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

      /** [[ContextHolder.cache]]: emits the private holder, the throwing accessor, and
        * `<held> = summon[T]` at the head of every threaded METHOD this type declares. Runs ahead
        * of the arms below, for an all-`static` holder no `threadedClasses` arm would otherwise
        * see. A constructor is excluded — its body is the constructor region (DESIGN.md §8.2), and
        * a cache written from one is [[ContextHolder.retain]]'s question instead. */
      private def cached(t: Tree.ClassDef)(using Program): Tree.ClassDef =
        cacheOf.get(t.symbol).fold(t) { nm =>
          val p = summon[Program]
          val mine = t.body.collect {
            case d: Tree.DefDef
              if need.threadedMethods(d.symbol) && !deferredFields(d.symbol) && !isCtor(p, d.symbol) =>
              d.symbol
          }.toSet
          if mine.isEmpty then t
          else
            cacheFired += t.symbol
            val (hold, acc) = mint.cachedContext(t.symbol, nm, ctxFqn, ctxRef, t.origin)
            t.copy(body = hold :: acc :: t.body.map {
              case d: Tree.DefDef if mine(d.symbol) =>
                d.copy(rhs = d.rhs.map(r => mint.prependStore(hold.symbol, ctxRef, contextExpr, r)))
              case s => s
            })
        }

      /** CT11: static field constructing a threaded class becomes a holder + throwing accessor.
        * No manifest key -- the accessor keeps the field's name. Like [[cached]], runs ahead of
        * the arms below. */
      private def fieldHeld(t: Tree.ClassDef)(using Program): Tree.ClassDef =
        val p = summon[Program]
        val fh = need.fieldHolders
        val myFields = t.body.collect {
          case v: Tree.ValDef if fh.contains(v.symbol) => v
        }
        if myFields.isEmpty then return t
        val myThreaded = t.body.collect {
          case d: Tree.DefDef
            if need.threadedMethods(d.symbol) && !deferredFields(d.symbol) &&
               p.symbolOf(d.symbol).exists(s => s.flags.isStatic && s.name != ContextNeed.CtorName) =>
            d.symbol
        }.toSet
        if myThreaded.isEmpty then return t
        val built = myFields.map { v =>
          val initRhs = fh(v.symbol)
          val (hold, acc) = mint.fieldHolder(v.symbol, v.tpt.tpe, v.origin)
          (v.symbol, hold, acc, initRhs)
        }
        val holdMap: Map[SymId, (SymId, Term)] = built.map { case (field, hold, _, initRhs) =>
          field -> (hold.symbol, initRhs)
        }.toMap
        val fieldSyms = holdMap.keySet
        val newMembers: List[Statement] = built.flatMap { case (_, hold, acc, _) => List(hold, acc) }
        val clinitStmts = need.fieldHolderClinit.getOrElse(t.symbol, Nil)
        t.copy(body = newMembers ++ t.body.flatMap {
          case v: Tree.ValDef if fieldSyms(v.symbol) => Nil
          case d: Tree.DefDef if p.symbolOf(d.symbol).exists(_.name == ContextNeed.ClinitName) &&
                                 clinitStmts.nonEmpty =>
            stripClinitStmts(d, clinitStmts)
          case d: Tree.DefDef if myThreaded(d.symbol) =>
            val stored = holdMap.values.foldLeft(d.rhs) { case (body, (holdSym, initRhs)) =>
              body.map(b => mint.prependFieldInit(holdSym, initRhs, b, clinitStmts))
            }
            List(d.copy(rhs = stored))
          case s => List(s)
        })

      /** Strip statements from the clinit that were moved to the holder init. If nothing remains,
        * drop the clinit entirely. */
      private def stripClinitStmts(d: Tree.DefDef, moved: List[Statement]): List[Statement] =
        val movedSet = moved.toSet
        d.rhs.map(Tree.uncomment) match
          case Some(b: Tree.Block) =>
            val remaining = (b.stats :+ b.expr).filterNot(movedSet.contains)
            val (init, expr) = remaining.lastOption match
              case Some(t: Term) => (remaining.dropRight(1), t)
              case _             => (remaining, Tree.Literal(Constant.UnitC, TypeRepr.NoType, d.origin))
            if init.isEmpty && isUnitLiteral(expr) then Nil
            else List(d.copy(rhs = Some(b.copy(stats = init, expr = expr))))
          case Some(t) if movedSet.contains(t) => Nil
          case _                               => List(d)

      private def isUnitLiteral(t: Term): Boolean = Tree.uncomment(t) match
        case Tree.Literal(Constant.UnitC, _, _) => true
        case _                                  => false

      /** Does this type's companion need its own `given` too? A scala `class` and its `object` are
        * two scopes, so a `private given` at the head of the class body reaches no `summon` in a
        * `static` member. Skipped where the type emits as a MODULE (one shared scope — a second
        * given would collide) or declares no static member (nothing for it to serve). */
      private def needsStaticGiven(t: Tree.ClassDef)(using p: Program): Boolean =
        !p.symbolOf(t.symbol).exists(_.flags.isModule) &&
          t.body.exists { case d: Definition => p.symbolOf(d.symbol).exists(_.flags.isStatic)
                          case _             => false }

      override def transformClassDef(t0: Tree.ClassDef)(using Program): Tree.ClassDef =
        val t = fieldHeld(cached(t0))
        // ENGINE-LIMITS CT7: no clause anywhere; a `given` member at the HEAD of the body instead
        // (a class body is a constructor, so a use ahead of it would read `null`).
        if need.selfSuppliedClasses(t.symbol) then
          t.copy(body = mint.givenMembers(t.symbol, ctxFqn, ctxRef, selfSource(t.symbol), t.origin,
                                          companion = needsStaticGiven(t)) ++ t.body)
        else if !need.threadedClasses(t.symbol) then t
        else
          // the retained member, at the HEAD for the `given` case's reason. Rides on the threaded
          // arm only — a type with no clause has no context to keep.
          val retained = retainOf.get(t.symbol)
            .map(nm => mint.retainedMember(t.symbol, nm, ctxRef, contextExpr, t.origin)).toList
          val ctors = t.body.collect { case d: Tree.DefDef if isCtor(summon[Program], d.symbol) => d.symbol }
          if ctors.isEmpty then
            // a java interface has no constructor; a trait promoted to an abstract class needs one minted.
            val at   = t.origin
            val ctor = mint.member(ContextNeed.CtorName,
              MemberKey(summon[Program].symbolOf(t.symbol).map(_.fullName).getOrElse("?"),
                        ContextNeed.CtorName).render,
              t.symbol, TypeRepr.MethodType(Nil, TypeRepr.NoType), Flags())
            t.copy(body = retained ++ (Tree.DefDef(ctor, List(List(mint.usingParam(ctor, ctxFqn, ctxRef, at))),
              TypeTree(TypeRepr.NoType, at), Some(Tree.Block(Nil, Tree.Literal(Constant.UnitC, TypeRepr.NoType, at),
                TypeRepr.NoType, at)), at) :: t.body))
          else
            // The clause lands on EVERY constructor. A Scala class parameter is in scope throughout
            // the body, so instance methods summon it with no signature change — but a SECONDARY
            // constructor is a method, and one that did not take the clause could not delegate.
            t.copy(body = retained ++ t.body.map {
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
    recordRetained(out, h, need, ctxFqn, retainOf)
    recordCached(out, h, ctxFqn, cacheOf, cacheFired.toSet)
    recordFieldHolders(out, h, need, ctxFqn)
    recordDeadSelf(h, need)
    recordDeadRetain(h, need)
    recordDeadCache(h, cacheFired.toSet)
    // LAST: `readPlan` above consults residual-global/refuse `sites` entries.
    recordDeadSites(h, need.firedSites)
    out.rebuilt(xref = Xref.build(out.units))

  // ---- the minted context type ----------------------------------------------------------------

  /** Synthesizes the context type: one `var` per mapped field, plus `var global` when a residual read
    * exists — the holder's own shape (a bag of mutable statics) moved onto an instance, so a
    * consumer's bootstrap sets them where it used to set `Holder.field = …`. `inject` a richer type
    * (immutable case class, accessor sugar) instead of relying on this to guess one. */
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

  /** The holder survives iff something still READS it — derived, not a knob. Every mapped static
    * whose reads all moved onto the context is dropped; a residual read stays, already counted as a
    * `residual-global-read` seam. */
  private def residualHolder(p: Program, h: ContextHolder, statics: Map[SymId, String]): Program =
    val gone = statics.keySet.filter(s => !p.usages(s).exists(_.kind == UsageKind.TermRef))
    if gone.isEmpty then return p
    gone.toList.sortBy(_.raw).foreach { s =>
      // subject is the OWNING TYPE: a dropped member has no declaration for `PorterNote.InBody`.
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
    * declaration — its argument is supplied by the `using` in scope, so the call site is unchanged. */
  private def recordDecisions(p: Program, h: ContextHolder, need: ContextNeed, ctxFqn: String): Unit =
    val deferredFields = need.deferrals.map(_.field).toSet
    def row(s: SymId, to: String): Unit =
      p.symbolOf(s).foreach(sym => record(Decision(
        kind = Decision.Kind.RetypedSignature, subject = s, subjectFqn = sym.fullName,
        // no `key`: `Reason.Configured(name, h.holder)` below already carries it.
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

  /** One row per FRAMEWORK-INSTANTIATED type — CLAUDE.md §1(b)'s third answer, recorded. An
    * `InjectedMember` and not a `RetypedSignature`: the signature did not move, the port gained a
    * member instead. Subject is the TYPE, so the note sits above the emitted `class` line. */
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

  /** ONE ROW PER TYPE THAT KEPT ITS CONTEXT — `ContextHolder.retain`. An `InjectedMember`: the
    * signature did move (the clause is on the constructors either way), and this decision is about
    * the MEMBER — emitted surface java never declared. */
  private def recordRetained(p: Program, h: ContextHolder, need: ContextNeed, ctxFqn: String,
                             retained: Map[SymId, String]): Unit =
    val keyOf = boundRetain.getOrElse(h.holder, Map.empty)
    retained.toList.filter((c, _) => need.threadedClasses(c)).sortBy(_._1.raw).foreach { (c, nm) =>
      p.symbolOf(c).foreach(sym => record(Decision(
        kind = Decision.Kind.InjectedMember, subject = c, subjectFqn = sym.fullName,
        detail = Map(
          "member" -> nm,
          "type"   -> ctxFqn,
          "from"   -> "a constructor clause nothing outside this type can name",
          "to"     -> s"a `val $nm: $ctxFqn` this type keeps, readable from anything holding one",
          "why"    -> ("the clause the threading attaches is a CONSTRUCTOR PARAMETER, in scope in " +
            "this body and nameable nowhere else — so a declaration the closure could not reach, " +
            "holding one of these, has the context in its hand and no way to spell it. This port " +
            "asked for it to be kept under a name, which is what a `selfSupplied` expression on " +
            "such a holder then reads"),
        ),
        reason = Reason.Configured(name, keyOf.getOrElse(c, sym.fullName)),
        origin = Decision.originOf(p, c),
      )))
    }

  /** ONE ROW PER TYPE THAT CACHED ITS CONTEXT — `ContextHolder.cache`. An `InjectedMember`: no
    * signature moved, the port gained two companion members, one PUBLIC. Subject is the TYPE. */
  private def recordCached(p: Program, h: ContextHolder, ctxFqn: String,
                           cached: Map[SymId, String], fired: Set[SymId]): Unit =
    val keyOf = boundCache.getOrElse(h.holder, Map.empty)
    cached.toList.filter((c, _) => fired(c)).sortBy(_._1.raw).foreach { (c, nm) =>
      p.symbolOf(c).foreach(sym => record(Decision(
        kind = Decision.Kind.InjectedMember, subject = c, subjectFqn = sym.fullName,
        detail = Map(
          "member" -> nm,
          "type"   -> ctxFqn,
          "from"   -> "a `using` clause on this type's own methods, live only for one call",
          "to"     -> s"a private holder assigned at the head of each of them and a `def $nm: $ctxFqn` over it",
          "why"    -> ("this type takes the context on its STATIC METHODS, so it is in no threaded " +
            "class and there is no constructor parameter to keep — the value exists and nothing " +
            "outside can name it. This port asked for it to be captured under a name, which is " +
            "what a `selfSupplied` expression elsewhere then reads as `<Type>." + nm + "`. The " +
            "accessor THROWS when nothing has captured one yet, which is the java contract's own " +
            "refusal rather than a `null` that reaches its caller as a plausible wrong answer"),
        ),
        reason = Reason.Configured(name, keyOf.getOrElse(c, sym.fullName)),
        origin = Decision.originOf(p, c),
      )))
    }

  /** CT11: one `InjectedMember` row per field holder. */
  private def recordFieldHolders(p: Program, h: ContextHolder, need: ContextNeed, ctxFqn: String): Unit =
    need.fieldHolders.toList.sortBy(_._1.raw).foreach { (field, rhs) =>
      p.symbolOf(field).foreach(sym => record(Decision(
        kind = Decision.Kind.InjectedMember, subject = field, subjectFqn = sym.fullName,
        detail = Map(
          "from"   -> "a static field whose initialiser constructs a threaded class",
          "to"     -> (s"a private `var` holder + throwing accessor `def ${sym.name}` -- the " +
            "initialiser runs at the head of every threaded static method behind an `eq null` guard"),
          "why"    -> ("this field's initialiser cannot run at companion-initialisation time because " +
            s"it constructs a type whose constructor now takes `(using $ctxFqn)` and there is no " +
            "given in scope at that point. The accessor keeps the field's name so no new public " +
            "name is minted"),
        ),
        reason = Reason.Universal("static-field-holder (CT11)"),
        origin = Decision.originOf(p, field),
      )))
    }

  /** A bound `cache` entry on a type that declares no threaded method: no holder/accessor emitted,
    * so a `selfSupplied` expression reading the accessor names something not there. */
  private def recordDeadCache(h: ContextHolder, fired: Set[SymId]): Unit =
    boundCache.getOrElse(h.holder, Map.empty).toList
      .filterNot((c, _) => fired(c)).map((_, k) => k).distinct.sorted.foreach { k =>
        deadSites += PolicyFinding(name, s"GlobalsToImplicitsTransform(holders) `${h.holder}`.cache",
          k, PolicyIssue.NeverMatched, "the entry names a type of this program that declares NO " +
            "method the closure threaded — nothing in it reads the holder, nothing it uses is " +
            "threaded, or its attachment landed on the CONSTRUCTORS instead, which is `retain`'s " +
            "question and not this one. There is no clause for a captured value to come from, so " +
            "no holder and no accessor were emitted, and any `selfSupplied` expression written to " +
            "read the accessor names something that is not there — a compile error in a different " +
            "file from this key. Fix the key, use `retain` if the type is threaded at its " +
            "constructors, or find out why nothing in it is threaded")
      }

  /** A bound `retain` entry on a type the closure never threaded: no member emitted, so a
    * `selfSupplied` expression naming it is a compile error in a different file from this key. */
  private def recordDeadRetain(h: ContextHolder, need: ContextNeed): Unit =
    boundRetain.getOrElse(h.holder, Map.empty).toList
      .filterNot((c, _) => need.threadedClasses(c)).map((_, k) => k).distinct.sorted.foreach { k =>
        deadSites += PolicyFinding(name, s"GlobalsToImplicitsTransform(holders) `${h.holder}`.retain",
          k, PolicyIssue.NeverMatched, "the entry names a type of this program that took NO " +
            "constructor clause — either the closure never reached it, or it is `selfSupplied`, or " +
            "it was scoped out. There is no context for the member to keep, so none was emitted, and " +
            "any `selfSupplied` expression written to read it names something that is not there — a " +
            "compile error in a different file from this key. Fix the key, or find out why the type " +
            "is not threaded")
      }

  /** A bound `selfSupplied` entry the closure never reached: emits no `given` member, so removing
    * it changes no emitted byte — the same blindness CT6 measured for `sites`. */
  private def recordDeadSelf(h: ContextHolder, need: ContextNeed): Unit =
    val reached = need.selfSuppliedClasses
    boundSelf.getOrElse(h.holder, Map.empty).toList.filterNot((s, _) => reached(s))
      .map((_, k) => k)
      // an entry with no expression is already `Malformed`; do not also report it here.
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

  /** A symbol MINTER for one holder's run — a value the run owns, never phase-instance state. */
  final class Minter(program: Program):
    private var next = program.symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1
    private val buf  = collection.mutable.ListBuffer.empty[Symbol]
    private val usings  = collection.mutable.Map.empty[SymId, SymId]
    private val givens  = collection.mutable.Map.empty[SymId, SymId]
    private val retains = collection.mutable.Map.empty[SymId, SymId]

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

    /** the clause — anonymous, since a named parameter would shadow a fully-qualified reference and
      * nothing reads the name (`using`/`summon` never do). One per owner. */
    def usingParam(owner: SymId, ctxFqn: String, ctxRef: TypeRepr, at: Origin): Tree.ValDef =
      val id = usings.getOrElseUpdate(owner,
        member("", MemberKey(ctxFqn, "<using>").render, owner, ctxRef, Flags(isParam = true, isGiven = true)))
      Tree.ValDef(id, TypeTree(ctxRef, at), scala.None, at)

    /** `private given <ctx> = <the port's expression>`, at the head of a framework-instantiated
      * type's body (ENGINE-LIMITS CT7). Anonymous and `private` for [[usingParam]]'s reasons — off
      * the published surface. RHS is [[Tree.Opaque]] (Scala the frontend never saw), emitted
      * verbatim and not type-checked here; the target compiler is the gate. */
    def givenMember(owner: SymId, ctxFqn: String, ctxRef: TypeRepr, src: String, at: Origin): Tree.ValDef =
      val id = givens.getOrElseUpdate(owner,
        member("", MemberKey(ctxFqn, "<given>").render, owner, ctxRef,
               Flags(isGiven = true, isPrivate = true)))
      Tree.ValDef(id, TypeTree(ctxRef, at), Some(Tree.Opaque(src, ctxRef, at)), at)

    /** the same member for the COMPANION, where `companion` says the type has one — two scopes, so
      * a `static` method's summon needs its own given; a second id for the same owner. */
    def givenMembers(owner: SymId, ctxFqn: String, ctxRef: TypeRepr, src: String, at: Origin,
                     companion: Boolean): List[Tree.ValDef] =
      val inst = givenMember(owner, ctxFqn, ctxRef, src, at)
      if !companion then List(inst)
      else
        val id = staticGivens.getOrElseUpdate(owner,
          member("", MemberKey(ctxFqn, "<given-static>").render, owner, ctxRef,
                 Flags(isGiven = true, isPrivate = true, isStatic = true)))
        List(inst, Tree.ValDef(id, TypeTree(ctxRef, at), Some(Tree.Opaque(src, ctxRef, at)), at))

    private val staticGivens = collection.mutable.Map.empty[SymId, SymId]

    /** `val <nm>: <ctx> = <context expr>`, at the head of a threaded type's body
      * ([[ContextHolder.retain]]) — NAMED and PUBLIC, unlike the machinery members above, so code
      * outside the type can read it. Not `given` — a second candidate would make `summon` ambiguous
      * inside this body. One per owner. */
    def retainedMember(owner: SymId, nm: String, ctxRef: TypeRepr, rhs: Term, at: Origin): Tree.ValDef =
      val id = retains.getOrElseUpdate(owner,
        member(nm, MemberKey(program.symbolOf(owner).map(_.fullName).getOrElse("?"), nm).render,
               owner, ctxRef, Flags(isFinal = true)))
      Tree.ValDef(id, TypeTree(ctxRef, at), Some(rhs), at)

    // ---- THE CACHED CONTEXT (`ContextHolder.cache`) ------------------------------------------

    private val caches = collection.mutable.Map.empty[SymId, (SymId, SymId)]

    /** a PRIVATE `var` holder and a PUBLIC accessor, both `static`, on the type's companion. Two
      * members deliberately: a public `var` would answer `null` before anything wrote it, so the
      * accessor THROWS instead (`IllegalStateException`, java's own precondition contract, CLAUDE.md
      * §1). The holder has no initialiser (renders as `scala.compiletime.uninitialized`, tested with
      * `eq null`). The message names the type's SIMPLE name, stable under a package rename (§4.56). */
    def cachedContext(owner: SymId, nm: String, ctxFqn: String, ctxRef: TypeRepr,
                      at: Origin): (Tree.ValDef, Tree.DefDef) =
      val ownerFqn = program.symbolOf(owner).map(_.fullName).getOrElse("?")
      val (hold, acc) = caches.getOrElseUpdate(owner, (
        member(s"$nm$$cache", MemberKey(ownerFqn, s"$nm$$cache").render, owner, ctxRef,
               Flags(isStatic = true, isMutable = true, isPrivate = true)),
        member(nm, MemberKey(ownerFqn, nm).render, owner, TypeRepr.MethodType(Nil, ctxRef),
               Flags(isStatic = true)),
      ))
      val simple = ownerFqn.split('.').last.split('$').last
      val ctxSimple = ctxFqn.split('.').last
      val read = Tree.Ident(hold, ctxRef, at)
      val cond = Tree.Apply(Tree.Select(read, eqOp, TypeRepr.NoType, at),
                            List(Tree.Literal(Constant.NullC, TypeRepr.NoType, at)),
                            eqOp, TypeRepr.NoType, at)
      val boom = Tree.Throw(Tree.Apply(
        Tree.New(TypeTree(illegalStateRef, at), illegalStateRef, at),
        List(Tree.Literal(Constant.StringC(
          s"$simple has captured no $ctxSimple yet — call one of its context-taking members first"),
          TypeRepr.NoType, at)),
        illegalStateCtor, illegalStateRef, at), TypeRepr.NoType, at)
      (Tree.ValDef(hold, TypeTree(ctxRef, at), scala.None, at),
       Tree.DefDef(acc, Nil, TypeTree(ctxRef, at),
                   Some(Tree.If(cond, boom, Tree.Ident(hold, ctxRef, at), ctxRef, at)), at))

    /** `<held> = <context expr>` at the HEAD of a threaded method's body — the capture, so anything
      * the body calls finds the value already there. */
    def prependStore(hold: SymId, ctxRef: TypeRepr, rhs: Term, body: Term): Term =
      val store = Tree.Assign(Tree.Ident(hold, ctxRef, body.origin), rhs, TypeRepr.NoType, body.origin)
      body match
        case b: Tree.Block => b.copy(stats = store :: b.stats)
        case other         => Tree.Block(List(store), other, other.tpe, other.origin)

    // ---- STATIC FIELD HOLDERS (CT11) -----------------------------------------------------------

    private val fieldHolderCache = collection.mutable.Map.empty[SymId, (SymId, SymId)]

    /** CT11: a `private var` holder + throwing `def` accessor for a static field whose initialiser
      * constructs a threaded class. Accessor keeps the field's name. Parallel to [[cachedContext]]. */
    def fieldHolder(field: SymId, fieldTpe: TypeRepr, at: Origin): (Tree.ValDef, Tree.DefDef) =
      val sym   = program.symbolOf(field)
      val nm    = sym.map(_.name).getOrElse("f")
      val owner = sym.map(_.owner).getOrElse(SymId.None)
      val full  = sym.map(_.fullName).getOrElse(nm)
      val (hold, acc) = fieldHolderCache.getOrElseUpdate(field, (
        member(s"$nm$$holder", MemberKey(full, s"$nm$$holder").render, owner, fieldTpe,
               Flags(isStatic = true, isMutable = true, isPrivate = true)),
        member(nm, MemberKey(full, nm).render, owner, TypeRepr.MethodType(Nil, fieldTpe),
               Flags(isStatic = true)),
      ))
      val ownerSimple = program.symbolOf(owner).map(_.fullName).getOrElse("?").split('.').last.split('$').last
      val read = Tree.Ident(hold, fieldTpe, at)
      val cond = Tree.Apply(Tree.Select(read, eqOp, TypeRepr.NoType, at),
                            List(Tree.Literal(Constant.NullC, TypeRepr.NoType, at)),
                            eqOp, TypeRepr.NoType, at)
      val boom = Tree.Throw(Tree.Apply(
        Tree.New(TypeTree(illegalStateRef, at), illegalStateRef, at),
        List(Tree.Literal(Constant.StringC(
          s"$ownerSimple.$nm has not been initialised yet — call one of its context-taking members first"),
          TypeRepr.NoType, at)),
        illegalStateCtor, illegalStateRef, at), TypeRepr.NoType, at)
      (Tree.ValDef(hold, TypeTree(fieldTpe, at), scala.None, at),
       Tree.DefDef(acc, Nil, TypeTree(fieldTpe, at),
                   Some(Tree.If(cond, boom, Tree.Ident(hold, fieldTpe, at), fieldTpe, at)), at))

    /** CT11: `if (<held> eq null) { <held> = <init>; <clinit stmts> }` at the head of a threaded
      * method. The method already has `(using T)` from the thread pass. */
    def prependFieldInit(hold: SymId, rhs: Term, body: Term,
                         clinitStmts: List[Statement] = Nil): Term =
      val tpe = rhs.tpe
      val at  = body.origin
      val read = Tree.Ident(hold, tpe, at)
      val cond = Tree.Apply(Tree.Select(read, eqOp, TypeRepr.NoType, at),
                            List(Tree.Literal(Constant.NullC, TypeRepr.NoType, at)),
                            eqOp, TypeRepr.NoType, at)
      val store = Tree.Assign(Tree.Ident(hold, tpe, at), rhs, TypeRepr.NoType, at)
      val thenBody: Term =
        if clinitStmts.isEmpty then store
        else Tree.Block(store :: clinitStmts.collect { case t: Term => t },
                        Tree.Literal(Constant.UnitC, TypeRepr.NoType, at), TypeRepr.NoType, at)
      val init  = Tree.If(cond, thenBody, Tree.Literal(Constant.UnitC, TypeRepr.NoType, at),
                          TypeRepr.NoType, at)
      body match
        case b: Tree.Block => b.copy(stats = init :: b.stats)
        case other         => Tree.Block(List(init), other, other.tpe, other.origin)

    /** `eq` — reference identity, the faithful spelling of java's `== null` (CLAUDE.md §4.4). The
      * `scala.<op>#` prefix is what the emitter reads to render an operator infix. */
    private lazy val eqOp: SymId = member("eq", "scala.<op>#eq", SymId.None, TypeRepr.NoType, Flags())
    private lazy val illegalStateSym: SymId = tpe("IllegalStateException", "java.lang.IllegalStateException")
    private lazy val illegalStateRef: TypeRepr = TypeRepr.TypeRef(TypeRepr.NoPrefix, illegalStateSym)
    private lazy val illegalStateCtor: SymId =
      member("<init>", MemberKey("java.lang.IllegalStateException", "<init>").render,
             illegalStateSym, TypeRepr.NoType, Flags())
