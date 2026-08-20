package balticporter.transform

import balticporter.core.{MergeablePolicy, PolicyFinding, PolicyIssue, PolicyReport, PolicySource}
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
  * ==Shared surface, and the half of it a DEPENDENT may add to==
  * It changes emitted signatures, so its holders live in the BASE manifest: a dependent resolves
  * against the base's Java and must see the same threading, or the two ports each compile alone and
  * cannot compile together (§1.5).
  *
  * But `sites` and `selfSupplied` are keyed on DECLARATIONS, and a dependent's boundaries are in the
  * DEPENDENT's own types — which the base neither governs nor parses. Measured: four counted seams in
  * a dependent whose own diagnostic told its reader to *give the site a `sites` policy*, with no
  * manifest in which to write one (`ENGINE-LIMITS.md` CT8). So this declares `MergeablePolicy`, the
  * shared half ([[ContextHolder.sharedSurface]]) must AGREE between two instances, the
  * per-declaration half UNIONS refusing same-key-different-value, and what a dependent writes is a
  * [[ContextHolderExtension]] — a value with no field in which the shared half could be restated.
  */
final class GlobalsToImplicitsTransform(
    val holders: List[ContextHolder] = Nil,
    /** what a DEPENDENT contributes — the per-declaration half of a holder the BASE declares
      * (`ENGINE-LIMITS.md` CT8). Empty in a base, which is why every existing fingerprint is
      * byte-identical. */
    val extensions: List[ContextHolderExtension] = Nil,
) extends Phase, Rewrite, PolicySource, MergeablePolicy, PolicyBound:

  import GlobalsToImplicitsTransform.*

  def name = "globals->implicits"

  /** the lane that counts every place the threading STOPPED — a declaration a framework constructs
    * reflectively, a hand-written caller no manifest key can add a clause to, a residual global
    * (`Rewrite`, and CLAUDE.md §1's "a class a FRAMEWORK instantiates has no caller to change"). */
  def accountedBy: Set[String] = Set(ContextSeamCheck.Name)

  /** every policy key is written in the UPSTREAM namespace, and the package rename runs LAST
    * (§4.56). */
  override def runsBefore: Set[String] = Set("package-rename")

  /** THE HOLDERS THIS INSTANCE ACTUALLY RUNS — each with the extensions that name it folded in.
    *
    * Everything downstream reads this and not [[holders]]: an extension is policy, and a phase that
    * bound one table and ran another would be the §1(b) silent no-op twice over. A DANGLING
    * extension — one naming a holder nothing in the chain declares — folds into nothing and is
    * reported by [[danglingFindings]]. */
  lazy val effectiveHolders: List[ContextHolder] =
    holders.map(h => extensions.filter(_.holder == h.holder).foldLeft(h)(_ extendedBy _))

  private lazy val dangling: List[ContextHolderExtension] =
    extensions.filterNot(e => holders.exists(_.holder == e.holder))

  /** the effective policy, sorted and rendered — two modules that agree must compare equal (§1.5).
    *
    * Read off [[effectiveHolders]] and not off the two lists, so a module that states a holder with
    * its entries INLINE and one that states the same thing as holder-plus-extension fingerprint the
    * same — which is what makes §8.13's containment test (`bases.mergedWith(mine)` leaves `mine`
    * unchanged) work for a `mirroring` module. A dangling extension is rendered beside them, or a
    * dependent that contributes only extensions would be indistinguishable from a phase with no
    * policy at all. */
  def surfaceFingerprint: String =
    (effectiveHolders.map(_.fingerprint) ++ dangling.map(_.fingerprint)).sorted.mkString(";")

  /** every shared-surface SUBJECT this instance's policy is keyed on — the holder FQNs (of holders
    * AND of extensions, so a dependent naming a base's holder is a subject the screen can see), plus
    * every per-declaration key, every promotion and every scope entry, each through
    * [[MergeablePolicy.subjectOf]].
    *
    * '''The per-declaration keys are the half that matters''', and they are why this phase needed
    * the screen as much as the merge: a `sites` or `selfSupplied` key names a DECLARATION, and a
    * dependent that names one of the BASE's re-shapes a surface it does not own — the base emitted
    * that declaration threaded and the dependent would emit it deferred, or unthreaded, or holding
    * a `given` the base never wrote. A dependent naming its OWN types passes, which is the whole
    * point; the base's own holder FQN is in the base's subjects too, so a merge never reports it as
    * ADDED and an extension of an inherited holder is admitted for the honest reason. */
  def subjects: Set[String] =
    val fromHolders = holders.flatMap(h =>
      (Set(h.holder) ++ h.sites.keySet ++ h.selfSupplied.keySet ++ h.retain.keySet ++
       h.cache.keySet ++ h.promoteToClass ++ h.scope.entries))
    val fromExts = extensions.flatMap(e => Set(e.holder) ++ e.keys)
    (fromHolders ++ fromExts).map(MergeablePolicy.subjectOf).toSet

  /** THE MERGE CONTRACT (DESIGN.md §8.13), and the division is `ContextHolder.sharedSurface` —
    * which is a value on the policy rather than a list here, because "which half of this is the
    * SHARED SURFACE" is a fact about the policy and a phase that spelled it twice would drift.
    *
    *   - '''holders UNION by holder FQN.''' A holder only one side declares is an addition — a
    *     dependent with a global of its own is entitled to one — and the `governs` screen is what
    *     refuses it when the FQN is inside a base's claim.
    *   - '''a holder BOTH sides declare must AGREE on its shared surface, or the merge refuses.'''
    *     Two answers for the context type, the member map, the attachment mode, the read shape or
    *     the boundary default is a choice, and a choice is the thing a refusal exists to prevent:
    *     the base emitted its own types with one of them and the dependent resolves against that
    *     Java, so the two ports would each compile alone and could not compile together.
    *   - '''`sites` and `selfSupplied` UNION, refusing same-key-different-value.''' They are keyed
    *     on DECLARATIONS, and CT8 is exactly the case where the declaration is the dependent's.
    *   - '''extensions carry across, and a dangling one becomes an ordinary extension of whatever
    *     the merge just brought into scope.''' That is the mechanism: vfx's extension names
    *     `com.badlogic.gdx.Gdx`, which is dangling in vfx's own instance and folds into the base's
    *     holder in the merged one.
    *
    * `added` is the SUBJECT side of what the later instance contributes — every subject it holds
    * that this one did not. Those are the names a dependent could use to re-shape a base's emitted
    * surface, which is what `SurfaceFold` screens against `governs`, and they are the keys the run
    * holds this module's own policy findings to.
    */
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
      // …and a RETAINED member's NAME, which is emitted surface: two modules that retain one type's
      // context under two names emit two members, and whichever `selfSupplied` expression names one
      // of them compiles against exactly one of the two ports.
      val retainClash = for
        k        <- (mine.keySet & theirs.keySet).toList.sorted
        (key, v) <- theirs(k).retain.toList.sorted
        v2       <- mine(k).retain.get(key)
        if v2 != v
      yield s"""both modules RETAIN the context on "$key", as "$v2" and as "$v""""
      // …and a CACHED accessor's name, for the retained member's reason exactly: it is emitted
      // surface, and whichever `selfSupplied` expression reads `<Type>.<name>` compiles against one
      // of the two ports and not the other.
      val cacheClash = for
        k        <- (mine.keySet & theirs.keySet).toList.sorted
        (key, v) <- theirs(k).cache.toList.sorted
        v2       <- mine(k).cache.get(key)
        if v2 != v
      yield s"""both modules CACHE the context on "$key", as "$v2" and as "$v""""
      (surfaceClash ++ siteClash ++ selfClash ++ retainClash ++ cacheClash) match
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
            new GlobalsToImplicitsTransform(merged, (extensions ++ o.extensions).distinct),
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
  /** the `sites` entries the binder RESOLVED, per holder: key → the symbols it named. Both halves
    * are needed — the symbols are a `lazy-init` entry's candidate subjects, and the KEY SET is what
    * the dead-binding report is the complement of (`ENGINE-LIMITS.md` CT6). */
  private var boundSites: Map[String, Map[String, List[SymId]]]   = Map.empty
  /** the `selfSupplied` entries the binder RESOLVED, per holder: the TYPE symbol → its policy key.
    * The key is kept beside the symbol because it is the string an agent edits (§4.575) and it is
    * what the decision's `Reason.Configured` carries. */
  private var boundSelf: Map[String, Map[SymId, String]]          = Map.empty
  /** the `retain` entries the binder RESOLVED, per holder: the TYPE symbol → the POLICY KEY that
    * named it — the KEY and not the member name, for [[boundSelf]]'s reason: the key is the string an
    * agent edits (§4.575), it is what a `Reason.Configured` carries, and it is what the dead-binding
    * report has to name. The member name is one lookup away through [[ContextHolder.retain]], and
    * going the other way is a match on a VALUE two entries could share. */
  private var boundRetain: Map[String, Map[SymId, String]]        = Map.empty
  /** the `cache` entries the binder RESOLVED, per holder: the TYPE symbol → the POLICY KEY that
    * named it. [[boundRetain]]'s shape and [[boundRetain]]'s reasons — the key is what an agent
    * edits, what a `Reason.Configured` carries and what the dead-binding report names. */
  private var boundCache: Map[String, Map[SymId, String]]         = Map.empty

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
    malformed = bad.toList
    records   = binder.recordsFor(name)

  /** the never-fired half (from the BINDING, so it is complete whether or not this phase ran) plus
    * this phase's own malformed entries and counted refusals. */
  def policyReport: PolicyReport =
    PolicyReport.fromBindings(records) ++
      PolicyReport(malformed ++ danglingFindings ++ refusals.toList ++ deadSites.toList)

  /** AN EXTENSION NAMING A HOLDER NOTHING DECLARES — CT8's own never-fired shape.
    *
    * An extension is the per-declaration half of somebody else's holder, so it does nothing at all
    * unless a manifest in the chain declares that holder. `PolicyBinder` cannot see this: the
    * extension's own keys bind perfectly against a program that has them, and it is the HOLDER the
    * chain is missing. Derived from the policy rather than from a run, so a phase that never ran
    * reports it too — the reason `PolicyReport.fromBindings` exists one layer up. */
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
    if effectiveHolders.isEmpty then return program
    effectiveHolders.foldLeft(program)((p, h) => runHolder(p, h))

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
    /** the type → the MEMBER NAME its retained context is readable under. Bound entries only; a
      * malformed name is already reported and must not also emit a member, for the same reason a
      * `selfSupplied` entry with an empty expression must not also leave its type unthreaded. */
    val retainOf: Map[SymId, String] =
      boundRetain.getOrElse(h.holder, Map.empty).flatMap((s, k) => h.retain.get(k).map(s -> _))
    /** the type → the MEMBER NAME its CACHED context is readable under — [[retainOf]]'s shape at
      * the fifth key, bound entries only for the same reason. */
    val cacheOf: Map[SymId, String] =
      boundCache.getOrElse(h.holder, Map.empty).flatMap((s, k) => h.cache.get(k).map(s -> _))
    /** the types a `cache` entry ACTUALLY minted on — the run's own record, and the complement of
      * the dead-binding report. A `cache` key binds against a real class whether or not the closure
      * threaded a single method of it, which is exactly the blindness `ENGINE-LIMITS.md` CT6
      * measured for `sites` and `retain`. */
    val cacheFired = collection.mutable.Set.empty[SymId]
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

      /** THE FIFTH ANSWER (`ContextHolder.cache`): the private holder, the throwing accessor, and
        * `<held> = summon[T]` at the head of every threaded METHOD this type declares.
        *
        * It runs AHEAD of the three arms below and independently of all of them, because the shape
        * it serves is in none of their populations: an all-`static` holder takes its clause on its
        * methods, so it is in no `threadedClasses`, and the arm that would have seen it is the
        * `t` that returns its input unchanged.
        *
        * A CONSTRUCTOR is excluded even where the closure threaded one. Its body is the constructor
        * region's (`DESIGN.md` §8.2) and a statement prepended there is a statement the funnel may
        * promote, replay or re-order — and a cache written from a constructor is [[ContextHolder.retain]]'s
        * question, which has its own answer one key up. */
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

      override def transformClassDef(t0: Tree.ClassDef)(using Program): Tree.ClassDef =
        val t = cached(t0)
        // THE THIRD ANSWER (`ENGINE-LIMITS.md` CT7): no clause anywhere, and a `given` member at the
        // HEAD of the body instead. At the head because a class body is a constructor: a statement
        // that uses the context before the given is initialised would read `null`, and the reference
        // hand port writes it first for the same reason.
        if need.selfSuppliedClasses(t.symbol) then
          t.copy(body = mint.givenMember(t.symbol, ctxFqn, ctxRef, selfSource(t.symbol), t.origin) :: t.body)
        else if !need.threadedClasses(t.symbol) then t
        else
          // THE RETAINED MEMBER, at the HEAD for the reason the `given` above is: a class body is a
          // constructor, and a statement reading it earlier would read `null`. It rides on the
          // threaded arm and nowhere else — a type with no clause has no context to keep, and the
          // policy entry that named one is a counted `NeverMatched` instead.
          val retained = retainOf.get(t.symbol)
            .map(nm => mint.retainedMember(t.symbol, nm, ctxRef, contextExpr, t.origin)).toList
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
    recordDeadSelf(h, need)
    recordDeadRetain(h, need)
    recordDeadCache(h, cacheFired.toSet)
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

  /** ONE ROW PER TYPE THAT KEPT ITS CONTEXT — `ContextHolder.retain`.
    *
    * An `InjectedMember` for `recordSelfSupplied`'s reason and with the opposite emphasis: the
    * signature DID move (the clause is on the constructors either way) and what this decision is
    * about is the MEMBER, which is emitted SURFACE a consumer can see and which java never declared.
    * A reader of the generated file finds a public `val` with no upstream line behind it, and the
    * note above the `class` is the only place that can say who asked for it and what reads it. */
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

  /** ONE ROW PER TYPE THAT CACHED ITS CONTEXT — `ContextHolder.cache`.
    *
    * An `InjectedMember` for [[recordRetained]]'s reason: no signature moved (the clauses were
    * already on the methods) and what the port gained is two members the engine put on the emitted
    * companion — one of them PUBLIC, so it is surface a consumer can see and java never declared.
    * The subject is the TYPE, so the note sits above the emitted `object`/`class` line, where a
    * reader of the generated file finds an accessor with no upstream line behind it. */
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

  /** A BOUND `cache` ENTRY ON A TYPE THAT DECLARES NO THREADED METHOD — [[recordDeadRetain]]'s shape
    * at the fifth key, and it fails in exactly the same place: the entry exists BECAUSE some other
    * declaration's `selfSupplied` expression reads the accessor, so an entry that emitted nothing
    * leaves that expression naming something that is not there, in a DIFFERENT file from the key. */
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

  /** A BOUND `retain` ENTRY ON A TYPE THE CLOSURE NEVER THREADED — [[recordDeadSelf]]'s shape at the
    * fourth key, and it is the one where silence would be worst: the entry exists BECAUSE some other
    * declaration's `selfSupplied` expression names the member, so an entry that emitted nothing
    * leaves that expression naming something that is not there — a compile error in a DIFFERENT file
    * from the key that caused it. */
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

    /** THE RETAINED CONTEXT: `val <nm>: <ctx> = <the context expression>`, at the head of a threaded
      * type's body ([[ContextHolder.retain]]).
      *
      * NAMED and PUBLIC, which is the opposite of the two members above and is the whole point:
      * those two are machinery nothing outside the type reads, and this one exists precisely so that
      * something outside the type CAN. Its name is emitted surface and comes from the policy for
      * that reason. It is NOT `given` — the clause is already in scope inside this body, and a second
      * candidate of the same type would make every `summon` in it ambiguous.
      *
      * The RHS is the phase's own context expression (`summon[T]`, or `T.apply()`), built
      * STRUCTURALLY by the caller: a minted context's FQN is upstream and the package rename runs
      * last, so a name spliced as text here would be the one reference the rename cannot see.
      *
      * One per owner, so a class visited twice does not grow two members. */
    def retainedMember(owner: SymId, nm: String, ctxRef: TypeRepr, rhs: Term, at: Origin): Tree.ValDef =
      val id = retains.getOrElseUpdate(owner,
        member(nm, MemberKey(program.symbolOf(owner).map(_.fullName).getOrElse("?"), nm).render,
               owner, ctxRef, Flags(isFinal = true)))
      Tree.ValDef(id, TypeTree(ctxRef, at), Some(rhs), at)

    // ---- THE CACHED CONTEXT (`ContextHolder.cache`) ------------------------------------------

    private val caches = collection.mutable.Map.empty[SymId, (SymId, SymId)]

    /** THE CACHED CONTEXT: a PRIVATE `var` holder and a PUBLIC accessor over it, both `static`, so
      * the emitter puts them on the type's companion — which is where an all-`static` java holder's
      * threaded methods already are.
      *
      * TWO members and not one, and that is the whole design rather than a detail. A public `var`
      * would let anything write it and would answer `null` before anything had; the accessor is a
      * `def` that THROWS, which is the same contract java's own `getSkin()`-shaped preconditions
      * state (`IllegalStateException`) and is louder than java rather than quieter (CLAUDE.md §1's
      * rule for an obligation the engine's own translation created).
      *
      * The holder carries NO initialiser: the emitter renders a `var` with no rhs as
      * `scala.compiletime.uninitialized`, which is what every other field this engine emits without
      * one already reads, and `eq null` is exactly the test that answers it.
      *
      * The message names the type's SIMPLE name, which is stable under a package rename — the FQN
      * is not, and a message naming the upstream one would say something false about the file the
      * reader is holding (§4.56). One pair per owner, so a class visited twice does not grow two. */
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

    /** `<held> = <the context expression>` at the HEAD of a threaded method's body — the capture.
      *
      * At the head for [[givenMember]]'s reason read at a method: anything in the body that reaches
      * the accessor (directly, or through a callee this method invokes) must find the value already
      * there, and java's own lifecycle methods do exactly that. */
    def prependStore(hold: SymId, ctxRef: TypeRepr, rhs: Term, body: Term): Term =
      val store = Tree.Assign(Tree.Ident(hold, ctxRef, body.origin), rhs, TypeRepr.NoType, body.origin)
      body match
        case b: Tree.Block => b.copy(stats = store :: b.stats)
        case other         => Tree.Block(List(store), other, other.tpe, other.origin)

    /** `eq` — reference identity, the faithful spelling of java's `== null` (CLAUDE.md §4.4). The
      * `scala.<op>#` prefix is what the emitter reads to render an operator infix. */
    private lazy val eqOp: SymId = member("eq", "scala.<op>#eq", SymId.None, TypeRepr.NoType, Flags())
    private lazy val illegalStateSym: SymId = tpe("IllegalStateException", "java.lang.IllegalStateException")
    private lazy val illegalStateRef: TypeRepr = TypeRepr.TypeRef(TypeRepr.NoPrefix, illegalStateSym)
    private lazy val illegalStateCtor: SymId =
      member("<init>", MemberKey("java.lang.IllegalStateException", "<init>").render,
             illegalStateSym, TypeRepr.NoType, Flags())
