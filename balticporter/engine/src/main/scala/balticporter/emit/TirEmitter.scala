package balticporter.emit

import balticporter.catalog.{CatalogLog, JS, Obligations, Rendering, Typing}
import balticporter.core.{EngineInfo, Provenance}
import balticporter.tir.*

/** Emission backend: the TRANSFORMED typed TIR → Scala 3 source (DESIGN.md §2.5,
  * "source pretty-printing"). Because every node carries its resolved `TypeRepr` and every
  * reference a `SymId`, the emitter inserts the right form by construction — it looks names
  * up in the `Program`'s symbol table rather than re-deriving them, so the inference/diamond
  * bugs of the string-printer era cannot occur.
  *
  * First cut: covers the whole node set. A few Java-only control forms have no direct Scala
  * surface and are lowered approximately (marked inline) — `break`/`continue` (need
  * `boundary`), do-while (dropped in Scala 3), and inc/dec used as a value; those are the
  * emitter's known refinement points, not populator gaps.
  */
/** @param externalConcrete
  *   concrete instance members of parents the program does NOT contain — a phase that INJECTS a
  *   supertype as ready Scala (the collection shims) must declare what it brought, keyed by FQN as
  *   `(name, param counts)`. Without it [[diamondOverrides]] sees the injected parent as empty and
  *   misses every conflict against it.
  * @param provenance
  *   attribution for the upstream this port derives from, stamped as a header on every emitted
  *   unit (see [[emitUnit]]). `None` emits no header, which keeps snippet/demo call sites and the
  *   engine's own tests terse — but a real port ALWAYS passes it: the originals this project ports
  *   are licensed (Apache-2.0 so far), and a derived work ships its notice. A green build cannot
  *   report a missing one, so nothing but passing it makes the output compliant.
  */
/** @param notes
  *   the RUN's decision log, read at emission so every decision whose subject this emitter is
  *   rendering can carry a [[balticporter.tir.PorterNote]] beside the code (CLAUDE.md §4.57's
  *   neighbourhood: the porter note grammar). READ-ONLY here: the emitter's own decisions are
  *   exposed as [[ownDecisions]] and recorded by the orchestrator exactly once, so the
  *   determinism double-emission — a SECOND emitter over the same program, sharing this log —
  *   renders the same notes without recording a second copy of them.
  *
  *   A value the run owns, never a process-global table: two emitters in one JVM contaminated
  *   the source map's global predecessor and would contaminate this the same way (§5.1).
  */
final class TirEmitter(
    source: Program,
    externalConcrete: Map[String, Set[(String, List[Int])]] = Map.empty,
    provenance: Option[Provenance] = scala.None,
    notes: DecisionLog = new DecisionLog,
    /** DIAGNOSTIC mode (E9): render each counted refusal as `scala.compiletime.error` instead of
      * the residue comment M6 counts. Orthogonal to `RuntimeMode` and OFF by default — the
      * shipping emission is byte-identical with it off, which `members.tsv` proves. */
    preview: Boolean = false,
    /** BEST-EFFORT emission (`DESIGN.md` §6.4). Not a second code path — one emitter, one flag:
      * an OPEN marker renders as its inner term inside deterministic comment fences instead of the
      * `compiletime.error` the shipping default emits, and each affected file gains a banner naming
      * the regions. The orchestrator supplies the rest of the mode (a separate output directory, a
      * sentinel, a nonzero exit), because those are facts about a RUN and not about a rendering.
      *
      * At ZERO open markers this changes nothing at all — no marker, no fence, no banner — which is
      * §6.4's standing claim reduced to what makes it true: the two modes are the same emitter over
      * the same tree, so byte-identity is by construction rather than by a comparison somebody
      * remembers to run. `BestEffortIdentitySpec` is that claim under test. */
    bestEffort: Boolean = false,
    /** What this run may CONCLUDE about a type it does not emit (`DESIGN.md` §8.3). Two reads go
      * through it today — the constructor plan (via `CtorFunnel.Plans`) and the class-vs-object
      * collapse — and the rest of this file's whole-program indexes are still bare `program.units`
      * scans, which is stated rather than implied: they are correct for the SUBJECTS this emitter
      * renders (all owned) and wrong only as answers ABOUT a base type, which is what the view is
      * for. The default is a surface over the whole program, so a spec, a snippet and a
      * single-module port behave exactly as they did.
      *
      * `Option`, and not a defaulted `TrivialSurface(source)`, because a Scala class's default
      * argument cannot refer to another parameter of the SAME list. `None` reads as what it is —
      * "no view was supplied, so this run is its own surface". */
    surfaceView: Option[Surface] = scala.None,
    /** The UPSTREAM JAVA of a unit, for the comment-recovery backstop (`DESIGN.md` §8.8) — by
      * `Origin.javaPath`, `None` when there is nothing to read.
      *
      * Injected rather than read inline so an in-memory fixture can supply the text it parsed:
      * a snippet's `javaPath` names a file that does not exist, and the one path that could not
      * exercise the recovery would be the one every spec uses. The default reads the file, once
      * per path — the frontend already read it, and re-reading is what keeps this a pure function
      * of (program, sources) rather than of an object somebody had to thread through. */
    javaSource: String => Option[String] = TirEmitter.readJavaSource,
    /** The run's OBLIGATION LOG — §2.3(c)'s second discharge surface (`balticporter.catalog`).
      *
      * Most `JS-S` rows are decided HERE and not in the frontend: a `switch` with no `default`, a
      * `break` in the middle of a case, a `boundary` this emitter interposes, a `try`'s resources.
      * The frontend has already discharged its obligations correctly by the time any of them arise,
      * so a lowering-only mechanism can say nothing about them — which is what
      * `Attaches.Unmechanised` said, and counted, until this parameter existed.
      *
      * `CatalogLog.discarding` is the default and it is not a no-op flag: it is the honest answer
      * for a SECOND emitter over the same tree. The determinism twin, the preview emitter and the
      * best-effort emitter all re-render every unit, and a shared log would count every consult
      * twice — the same reason those instances do not share a source map (`CLAUDE.md` §5.1). Exactly
      * one emitter per run holds the run's log: the one whose text is shipped. */
    catalog: CatalogLog = CatalogLog.discarding,
):
  private given CatalogLog = catalog
  private val surface: Surface = surfaceView.getOrElse(TrivialSurface(source))
  /** what the NORMALISATION below decided — a value, handed to the orchestrator rather than
    * recorded from here, so constructing an emitter has no side effect on the run's log. */
  private val own = collection.mutable.ListBuffer.empty[Decision]

  // normalize away Java member-name clashes (a field `x` alongside a method `x()`) before
  // rendering — Scala forbids them; renaming the field symbol propagates to every reference.
  // …and LAST, the one that renames a CAPTURE rather than a member: it reads the names the three
  // above have already settled, so a member renamed to `hasNext$field` is what a captured local is
  // then held against (§4.55's "read EFFECTIVE names").
  private val prepared =
    TirEmitter.resolveCapturedLocalClashes(
      TirEmitter.funnelParamRenames(
        TirEmitter.resolveFieldShadowing(
          TirEmitter.resolveMemberClashes(source, own, surface, catalog), own, surface, catalog), own, surface), own)
  /** which Java constructor becomes each class's Scala primary, and which `super(args)` can be
    * replayed as statements — whole-program decisions. */
  private val plans = CtorFunnel.Plans(prepared, Some(surface))
  // a replayed parent constructor's statements execute one level down, so the private members
  // they reach must be visible there. Widening only rewrites symbol FLAGS — the trees `plans`
  // was computed over are untouched, so it still applies.
  private val program = TirEmitter.widen(prepared, plans.widenedMembers, own)

  /** every same-named candidate a program-declared type and its program-declared ancestors declare
    * — the index `JS-C22`/`JS-C23`'s consults read at each rendered call, and the one the RUN's
    * `overload-risk` check reads too.
    *
    * EXPOSED and shared rather than rebuilt by the check, for `HeapPollutionCheck`'s reason one
    * level up: the predicate is stated once so the obligation and the count cannot disagree about
    * which calls the rows are about, and the INDEX that predicate reads is the other half of the
    * same statement. Built over `program` — the emitter's own widened view, which is what a reader
    * of the emitted call is looking at. */
  private[balticporter] lazy val overloads = new balticporter.tir.OverloadRiskCheck.Overloads(program)

  /** Java's four access levels, decided once over the whole program — DESIGN §8.7, and the doc on
    * [[Visibility]] for why the LEVEL is decided there and the QUALIFIER supplied here. Computed at
    * construction like the renames above it, so its residual widenings travel with
    * [[ownDecisions]] and are in the run's log before the first unit renders a note. */
  private val visPlan: Map[SymId, Visibility.Vis] = Visibility.plan(program, own)

  /** The decisions THIS emitter made — the three §4.55 renaming passes and the replay widening.
    *
    * A value rather than a recording, for the reason the `notes` parameter gives: the orchestrator
    * records these once, from the emitter it keeps, and the determinism twin's identical copy is
    * simply never read. Recording from the constructor would double every row on any run that
    * builds two emitters — which is every run, since `Determinism.Emission` is the default. */
  val ownDecisions: List[Decision] = own.toList

  def emit: String = program.units.map(emitUnit).mkString("\n\n")

  /** types declared in the unit currently being rendered (in scope by simple name). */
  private var currentDeclared: Set[SymId] = Set.empty
  /** the class whose body is being rendered — a constructor's funnel plan is looked up by it. */
  private var currentClass: Option[Tree.ClassDef] = None
  /** simple name of the TOP-LEVEL type being rendered — the qualifier a java `private` needs. */
  private var currentTopLevel: String = ""
  /** the top-level type's symbol, and the class whose body is being rendered right now. A java
    * `private` needs a qualifier only when the two DIFFER, i.e. the member lives in a NESTED class. */
  private var currentTopLevelSym: SymId = SymId.None
  private var currentOwnerSym: SymId    = SymId.None
  /** last segment of the package this unit is being EMITTED into — the qualifier a Java
    * package-private or `protected` declaration renders with (DESIGN §8.7). Read from the unit the
    * emitter is writing and never from a symbol's upstream FQN plus a rename map: the rename runs
    * LAST (§4.56), so this string is already the emitted fact and no two-namespace join exists.
    * Empty in the default package, which [[Visibility]] has already turned into a recorded
    * widening rather than an unspellable qualifier. */
  private var currentPkgTail: String = ""

  def emitUnit(cd: Tree.ClassDef): String =
    currentDeclared = declaredTypes(cd)
    currentTopLevel = esc(sym(cd.symbol).name)
    currentTopLevelSym = cd.symbol
    currentOwnerSym = cd.symbol
    currentPkgTail = TirEmitter.packageTailOf(sym(cd.symbol).fullName)
    slots.clear(); stmtSeq.clear()
    val full = sym(cd.symbol).fullName
    currentUnitName = full
    printedNotes.clear()
    // …and the EMISSION DECISIONS beside them, which is what makes `emissionDecisions`' documented
    // idempotence true. The two are one record read from two sides — a note is derived from a
    // decision — so clearing one and appending to the other is how they come to disagree.
    emissionOf.clear()
    // …taken BEFORE the body renders, so the best-effort banner below names THIS file's regions.
    // The marker list is per-emitter and cumulative (it is the run's inventory); the slice from
    // here on is this unit's, and re-emitting a unit — which `Determinism.Emission` does for every
    // unit on every run — appends to it exactly as it re-renders the text.
    val markersBefore = recordedMarkers.size
    // …through the RENDERING DISPATCH, not straight into `classDef`. A nested type reaches the page
    // through `stat` and a TOP-LEVEL one reached `classDef` directly, so every row attaching at
    // `Rendered("ClassDef")` would have been owed — and discharged — only for the nested ones, which
    // is `ENGINE-LIMITS.md` F8's shape at the entry rather than in an arm. `statArm`'s `ClassDef`
    // case is `classDef(c, i)` and nothing else, so the emitted text is unchanged by construction.
    val body = stat(cd, 0)
    val pkg  = if full.contains('.') then s"package ${escPath(full.substring(0, full.lastIndexOf('.')))}\n\n" else ""
    // The generated banner says what the FILE is; the upstream's own header — its licence — follows
    // verbatim, before the `package` clause it sat above in Java. Both, in that order: the banner
    // carries the `Original license:` SPDX line and the upstream commit, which is the machine-
    // readable half, and the notice itself is the half the licence actually obliges us to
    // reproduce. Neither substitutes for the other (CLAUDE.md §4.57).
    // FILE-LEVEL porter notes sit between the upstream's own header and the `package` clause. Not
    // above the licence: the banner and the notice are the ORIGINAL's (§4.57/§4.58) and a note
    // wedged into them reads as part of the attribution. Below them and above `package` is the
    // first line that is the port speaking for itself — and it is exactly where a reader who has
    // just noticed the package is not the upstream one is looking.
    val text0 = header(cd) + leading(cd.unitLeading, 0) + unitNotes(cd) + pkg + body
    // …and, in BEST-EFFORT mode only, the banner naming the regions this file was degraded at.
    // Assembled after the body because the marker set is only known once the body has rendered,
    // and prepended above everything for the reason a sentinel exists at all: a file that looks
    // like deliverable output and is not is the single thing this mode must never produce.
    val banner =
      if !bestEffort then ""
      else
        val mine = recordedMarkers.drop(markersBefore).toList
        if mine.isEmpty then ""
        else
          "// ############################################################################\n" +
          s"// BEST-EFFORT OUTPUT — ${mine.size} region(s) in this file are NOT a faithful\n" +
          "// translation. This file MUST NOT ship. Each region is fenced in the body below.\n" +
          mine.map(m => s"//   ${m.kind.label}${m.diff.fold("")(d => s" [$d]")} at " +
            s"${m.origin.javaPath}:${m.origin.line} — ${Tree.Unportable.safe(m.what)}\n").mkString +
          "// ############################################################################\n"
    // …and the comments the attachment channel could not place, put back beside the member they
    // were written in. BEFORE the source map is computed, not after: a post-pass over finished text
    // would desync `srcmap.tsv` and `members.tsv` from the file, and the rule that a join happens
    // on a recorded id rather than on a rendering applies to line ranges too.
    val text = banner + recoverTrivia(cd, text0)
    if SrcMap.enabled then recordedMap(full) = srcMapOf(full, cd, text)
    text

  /** THIS emitter's source map — never a process-global table. Idempotent per unit: re-emitting a
    * unit replaces its entries, so an emitter run twice does not double the map. The orchestrator
    * writes it (`SrcMap.write`); two emitters in one JVM cannot see each other's. */
  def srcMap: SrcMap.Recording = SrcMap.Recording(recordedMap.values.toList.flatten, recordedMisses.toList)

  private val recordedMap    = collection.mutable.LinkedHashMap.empty[String, List[SrcMap.Entry]]
  private val recordedMisses = collection.mutable.ListBuffer.empty[String]

  // ---------------------------------------------------------------------------
  // THE BASE-SURFACE CONTRACT (`DESIGN.md` §8.3) — what this emitter EMITTED, per declaration.
  //
  // Recorded AT EMISSION, from the same values the rendering reads, and never re-derived
  // afterwards. That is the whole property the artifact has to have: a dependent reads this row
  // instead of recomputing the answer over a program the base never had, so a row derived by a
  // second pass — however careful — would be a THIRD derivation free to disagree with both. The
  // class-vs-object collapse is the case that makes it concrete: it is decided inline from four
  // whole-program index reads, and the only place that answer exists is the branch that took it.
  //
  // Recorded for NESTED types too, not only units. `Plans` plans every class in the program and a
  // dependent extends a base's nested class as readily as its top-level one; a contract that
  // covered only units would answer `Unknown` for exactly the constructor questions §8.3 exists for.
  // ---------------------------------------------------------------------------

  private val recordedTypeShapes   = collection.mutable.LinkedHashMap.empty[String, Surface.TypeShape]
  private val recordedMemberShapes = collection.mutable.LinkedHashMap.empty[String, Surface.MemberShape]

  /** What this emitter WROTE, keyed by EMITTED name — types by FQN, members by the same key
    * [[srcMap]] uses, so the orchestrator can attach each to its row without a second join.
    *
    * A value this emitter owns, exactly like [[srcMap]] and for the same reason: two emitters in one
    * JVM (`Determinism.Emission` builds a second) would contaminate a process-global table, and the
    * determinism twin's identical copy is simply never read. */
  def emittedShapes: TirEmitter.Shapes =
    TirEmitter.Shapes(recordedTypeShapes.toMap, recordedMemberShapes.toMap)

  /** Every contract question THIS emitter asked and could not answer, plus D6's cross-module face —
    * a base type this module names where the contract says the base emitted an `object`.
    *
    * Read by the orchestrator after emission, together with the funnel's own gaps (which the shared
    * `Surface` already holds), so a run has ONE list. */
  def surfaceGaps: List[Surface.Gap] = collapsedBaseTypesNamed

  /** every member this emitter's §4.55 passes RENAMED, by symbol → the name Java gave it.
    *
    * Read off the emitter's own decisions rather than recomputed: the passes rewrite the symbol
    * table, so by the time anything renders, the original name exists nowhere else. Held to the
    * decisions whose `to` is the symbol's CURRENT name, which is what makes the join exact when a
    * name was appended to twice (§4.55's "keep appending until the name is free"). */
  private lazy val renamedMembers: Map[SymId, String] =
    own.iterator.collect {
      case d if d.kind == Decision.Kind.RenamedMember && d.subject != SymId.None &&
                d.detail.get("to").contains(program.symbolOf(d.subject).map(_.name).getOrElse("")) &&
                d.detail.get("from").exists(_.nonEmpty) =>
        d.subject -> d.detail("from")
    }.toMap

  /** `private`, `private[Outer]` or `public` — what [[mods]] actually renders for this symbol, read
    * as the flags ARE. Java `protected` is emitted PUBLIC by this backend (loosening visibility can
    * only remove access errors), so `protected` is deliberately not a value here: the contract
    * records what was emitted, not what Java declared. */
  private def visOf(s: Symbol, ownerSym: SymId): String =
    if !s.flags.isPrivate then "public"
    else privateQualifier(ownerSym).fold("private")(o => s"private[$o]")

  private def recordTypeShape(cd: Tree.ClassDef, form: String, plan: CtorFunnel.Plan,
                              companion: Boolean, statics: List[String]): Unit =
    val s  = sym(cd.symbol)
    val ps = plan.primary.map(_.symbol)
    // the primary's slots, in §8.1's DESCRIPTOR grammar. A SYNTHESISED primary has no Java
    // constructor behind it, so its slots come from the plan's own (name, type) pairs and the
    // descriptor is derived engine-side — the case `Descriptor.ofInfo` exists for, and the reason
    // a contract row and a policy key are never in two spellings.
    val primary: Option[Descriptor] =
      if s.flags.isTrait || s.flags.isModule then scala.None
      else if plan.isSynthesised then
        Some(Descriptor(plan.synthetic.map((_, t) => descriptorParam(t)) ++
          // the MARKER is a slot of the emitted signature and is spelled by its simple name only —
          // never an FQN. A companion-`protected` type is not a name a consumer may resolve, so
          // `disambiguator=marker` is the fact and the type is not (`DESIGN.md` §8.1 F4).
          plan.marker.map(_ => Param.Unresolved).toList))
      else Some(Descriptor(plan.primaryParams.map(v => descriptorParam(v.tpt.tpe))))
    recordedTypeShapes(s.fullName) = Surface.TypeShape(
      form          = form,
      companion     = companion,
      statics       = statics,
      primary       = primary,
      primaryKind   = if s.flags.isTrait || s.flags.isModule then "" else plans.shape(cd),
      primaryVis    = if primary.isEmpty then ""
                      else if plan.isSynthesised then "protected"
                      else ps.map(p => visOf(sym(p), cd.symbol)).getOrElse("public"),
      disambiguator = if plan.marker.isDefined then "marker" else "none",
      secondaries   = secondariesOf(cd, plan),
      tparams       = if cd.tparams.isEmpty then "" else cd.tparams.map(typeParam).mkString("[", ", ", "]"),
      parents       = parentSymsOf(cd).map(p => sym(p).fullName),
      flags         = List(
                        Option.when(form == "class" && s.flags.isAbstract)("abstract"),
                        Option.when(s.flags.isSealed)("sealed"),
                        Option.when(s.flags.isFinal)("final"),
                      ).flatten,
      vis           = "public", // the emitter drops a type's `private` outright — see `classDef1`
    )

  /** the emitted `def this` signatures — every constructor the funnel did NOT promote, MINUS the
    * ones `orderBody` drops in front of a nilary primary.
    *
    * The second subtraction is not a refinement, it is the difference between a contract and a
    * guess. A `Plan.none` class's nilary java constructor is never emitted (`E120` beside scala's
    * implicit primary), and publishing `()` among the secondaries told every dependent that
    * `new BitmapFont()` reaches a `def this()` this module does not have. Read through
    * `CtorFunnel.delegationOnlyNilary`, which is the predicate the emission itself drops with. */
  private def secondariesOf(cd: Tree.ClassDef, plan: CtorFunnel.Plan): List[Descriptor] =
    if sym(cd.symbol).flags.isModule then Nil
    else
      given Program = program
      val paramful = plans.paramfulPrimaryOf(cd)
      CtorFunnel.ctorsOf(program, cd.body)
        .filterNot(d => plan.primary.exists(_.symbol == d.symbol))
        .filterNot(d => !paramful && CtorFunnel.delegationOnlyNilary(program, d).isDefined)
        .map(d => Descriptor(CtorFunnel.valueParams(program, d).map(v => descriptorParam(v.tpt.tpe))))

  /** one emitted type, in the descriptor grammar's [[Param]] vocabulary — through
    * `Descriptor.ofInfo`, the engine's own derivation, so a contract row and a manifest key can
    * never be in two spellings (an array is `int[]` on both sides, never `Array`). */
  private def descriptorParam(t: TypeRepr): Param =
    Descriptor.ofInfo(program, TypeRepr.MethodType(List("_" -> t), TypeRepr.NoType))
      .flatMap(_.params.headOption).getOrElse(Param.Unresolved)

  // ---------------------------------------------------------------------------
  // PORTER NOTES — one `Decision`, rendered beside the code it explains.
  //
  // Read-only over the run's log and INDEXED BY `SymId`, never by name: three of this emitter's
  // own passes rename the symbol before it is rendered, so a name-keyed index would be empty on
  // exactly the decisions (`style` -> `style$shadow`) the notes exist for. The decision carries
  // the id, and the id is what survives every rename in this file.
  //
  // A note is EMITTED TEXT, so it moves member digests — which is the intended and accepted blast
  // of adding them, and the reason the emitter records what it printed (`printedNotes`) rather
  // than letting a coverage check re-derive it from the text.
  // ---------------------------------------------------------------------------

  private lazy val noteIndex: Map[SymId, List[Decision]] =
    notes.all.filter(d => PorterNote.Rendered(d.kind) && d.subject != SymId.None)
      .groupBy(_.subject)
      // stable within a subject: the artifact sorts, and so must the emitted text, or two runs of
      // one program disagree byte-for-byte on a member with two decisions.
      .view.mapValues(_.sortBy(d => (d.kind.toString, d.reason.className, d.reason.detail, d.tsv))).toMap

  /** decisions with NO subject symbol, grouped by the FQN they name — the rows a policy key makes
    * about a type this run never interned (a drop that matched nothing, an injected file). Keyed by
    * name because that is all such a row has. */
  private lazy val noteIndexByFqn: Map[String, List[Decision]] =
    notes.all.filter(d => PorterNote.Rendered(d.kind) && d.subject == SymId.None)
      .groupBy(_.subjectFqn).view.mapValues(_.sortBy(_.tsv)).toMap

  // per UNIT, and cleared when that unit is re-emitted — the same idempotence `recordedMap` has,
  // for the same reason: `Determinism` and the action cache both re-render units, and an
  // append-only list would report each note twice and make the coverage check's "one note per
  // decision" arithmetic a function of how many times the emitter was called.
  private val recordedNotes = collection.mutable.LinkedHashMap.empty[String, collection.mutable.ListBuffer[PorterNote.Printed]]
  private def printedNotes: collection.mutable.ListBuffer[PorterNote.Printed] =
    recordedNotes.getOrElseUpdate(currentUnitName, collection.mutable.ListBuffer.empty)

  /** every note THIS emitter printed, in printing order — the input to [[NoteCoverageCheck]]. */
  def notesPrinted: List[PorterNote.Printed] = recordedNotes.values.toList.flatten

  // ---- THE CONTEXT CLAUSE, checked against what was actually written -------------------------
  //
  // A phase may put a `(using T)` clause on a class's constructors (`DESIGN.md` §8.4), and three
  // shapes of class have nowhere to put it: one whose primary is scala's own implicit nilary
  // constructor and whose plan carries no clause, a trait, and a java enum. The emitted file
  // COMPILES with the clause gone wherever the class's own body happens not to summon anything —
  // and the run's decision row and porter note both claim a clause that is not there. Nothing else
  // in the pipeline can see it (CLAUDE.md §3), so the emitter records the disagreement between what
  // the constructors CARRIED and what the header it just wrote RENDERS, and the run reports each as
  // a `lost-clause` seam (`ENGINE-LIMITS.md` CT5).
  //
  // Read off the RENDERED text, not off the plan: the plan is exactly what may have dropped the
  // clause, and a check reading it would have passed on the day CT4 flattened one into a value
  // parameter. Keyed by symbol so re-emission (the determinism twin, the action cache) overwrites
  // rather than duplicates, the same idempotence `recordedNotes` has.
  private val clauseLost = collection.mutable.LinkedHashMap.empty[SymId, TirEmitter.ClauseLoss]

  /** every type this emitter rendered whose constructors carry a context clause its emitted header
    * does not — the input to `context-seam`'s `lost-clause` lane. Empty for every port that threads
    * nothing, since nothing then puts a clause on a constructor. */
  def contextClauseLosses: List[TirEmitter.ClauseLoss] = clauseLost.values.toList

  /** record what the header just written did with the class's context clause. `form` is what was
    * emitted, because the reader's next question is which of the three shapes this is. */
  private def checkClause(cd: Tree.ClassDef, rendered: Boolean, form: String): Unit =
    if !rendered && CtorFunnel.ctorsCarryGivens(program, cd) then
      clauseLost(cd.symbol) = TirEmitter.ClauseLoss(
        cd.symbol, sym(cd.symbol).fullName, form, cd.origin)
    else clauseLost.remove(cd.symbol)

  /** the notes for `s` whose kind is in `kinds`, rendered at indent `i` and recorded as printed.
    * `""` when there are none, which is the overwhelming majority of members — so this can be
    * spliced into every definition site unconditionally. */
  private def noteBlock(s: SymId, i: Int, kinds: Set[Decision.Kind]): String =
    noteIndex.get(s).map(_.filter(d => kinds(d.kind))) match
      case Some(ds) if ds.nonEmpty =>
        val ind0 = ind(i)
        ds.map { d =>
          printedNotes += PorterNote.Printed(d.kind, d.subject, d.subjectFqn, currentUnitName)
          PorterNote.render(d, ind0)
        }.mkString
      case _ => ""

  /** notes above a DEFINITION — a `def`, a `val`, a nested `class`. */
  private def declNotes(s: SymId, i: Int): String = noteBlock(s, i, PorterNote.AtDeclaration)

  /** notes at the HEAD of a type's body: what is NOT in this type and why (a dropped member has no
    * declaration to sit above). Placed first so a reader scanning for a member it cannot find sees
    * the answer before the members that are there. */
  private def bodyNotes(s: SymId, i: Int): String = noteBlock(s, i, PorterNote.InBody).stripSuffix("\n")

  /** the FILE-level notes: everything decided about the TOP-LEVEL unit's own symbol — the namespace
    * rename above all, which is a fact about the whole file rather than about a declaration in it.
    * A NESTED type's notes are rendered at its own `class` keyword by [[declNotes]] instead. */
  private def unitNotes(cd: Tree.ClassDef): String = declNotes(cd.symbol, 0)

  private var currentUnitName: String = ""

  // ---------------------------------------------------------------------------
  // PREVIEW MODE (E9) — say it in the OUTPUT, or refuse and count.
  //
  // `ENGINE-LIMITS.md` M6: where the engine has no faithful Scala it refuses and carries a NUMBER,
  // and a residue comment count is itself a measure. That is right for a port that ships. It is
  // wrong for the first week of a NEW library, where the operator is an agent in another repository
  // that has to find the residue at all, and a comment reading `()` compiles perfectly.
  //
  // `preview = true` turns each such site into `scala.compiletime.error("balticporter: …")` — the
  // port stops compiling, on purpose, and every error says WHAT could not be rendered, WHY, WHAT
  // the agent must do, and the JAVA ORIGIN. It is a diagnostic mode, not an emission strategy:
  // `preview = false` is the shipping default and emits the same bytes it always did, which
  // `members.tsv` proves rather than this comment.
  //
  // The decision is recorded per unit and drained by the orchestrator (`emissionDecisions`) — it is
  // made at EMISSION, so it cannot travel with `ownDecisions`, which is a value fixed at
  // construction. The porter note is printed beside it so `NoteCoverageCheck` sees the pair.
  // ---------------------------------------------------------------------------

  // per UNIT and CLEARED when that unit is re-emitted, exactly as `recordedNotes` is and for the
  // same reason. The doc below claimed that idempotence and the buffer was never cleared, so a
  // second `emitUnit` for one unit appended a second copy of every emission decision — and the
  // notes, which ARE cleared, then disagreed with it: `NoteCoverageCheck`'s "one note per decision"
  // arithmetic became a function of how many times the emitter had been called.
  private val recordedEmission =
    collection.mutable.LinkedHashMap.empty[String, collection.mutable.ListBuffer[Decision]]
  private def emissionOf: collection.mutable.ListBuffer[Decision] =
    recordedEmission.getOrElseUpdate(currentUnitName, collection.mutable.ListBuffer.empty)

  /** what the EMITTER decided while rendering, per unit — idempotent, so re-emitting a unit does
    * not double it. Drained by `PortRun` into the run's log after emission. */
  def emissionDecisions: List[Decision] = recordedEmission.values.toList.flatten

  /** Did rendering `unit` record anything the EMITTED TEXT alone cannot carry?
    *
    * The action cache's question, and the reason it is asked of the emitter rather than answered in
    * `PortRun`: emission is not a pure function of the unit — beside the text it produces DECISIONS
    * (`WidenedSeal`, `ForcedClassInit`, preview's `Unrenderable`) and the NOTE RECORDS that
    * `NoteCoverageCheck` joins against. A cache HIT returns the text without rendering, so both
    * vanish while the cached text still CARRIES the note — §4.575's exact defect, and invisible to
    * the check that exists for it, because the check's two inputs both derive from the rendering
    * that did not happen.
    *
    * So a unit that recorded either is never STORED, and a hit is therefore equal to a miss by
    * construction. Refusal rather than replay: a `Decision` and a `PorterNote.Printed` are keyed on
    * a `SymId`, which is interning order and dies with the run (see `Decision`'s class doc), so a
    * value written to disk by one run and replayed by another would join against the wrong symbols
    * — and re-resolving it by NAME is the join `NoteCoverageCheck` documents as empty on exactly
    * the decisions it exists for. */
  def recordedForCache(unit: String): Boolean =
    recordedEmission.get(unit).exists(_.nonEmpty) || recordedNotes.get(unit).exists(_.nonEmpty)

  /** A construct with NO faithful Scala. Under the shipping default this is `residue` — the
    * comment M6 counts; under `preview` it is a `scala.compiletime.error` carrying the whole
    * diagnosis, plus the porter note that makes it derivable.
    *
    * `what/why/action/origin`, in that order and all four mandatory: an agent that cannot classify
    * a diagnostic pays a full investigation for it (§4.45), and "what an agent must do" is the one
    * an error message almost never carries. */
  private def unrenderable(what: String, why: String, action: String, o: Origin, residue: String): String =
    if !preview then residue
    else
      val d = Decision(
        kind       = Decision.Kind.Unrenderable,
        subject    = currentOwnerSym,
        subjectFqn = if currentOwnerSym == SymId.None then currentUnitName else sym(currentOwnerSym).fullName,
        detail     = Map("construct" -> what, "why" -> why, "action" -> action),
        reason     = Reason.Universal(s"unrenderable/$what"),
        origin     = o,
      )
      emissionOf += d
      printedNotes += PorterNote.Printed(d.kind, d.subject, d.subjectFqn, currentUnitName)
      val msg = PorterNote.safe(s"balticporter: $what: $why; $action; origin ${o.javaPath}:${o.line}")
      PorterNote.render(d, "").stripSuffix("\n") + " " +
        "scala.compiletime.error(\"" + escape(msg) + "\")"

  // ---------------------------------------------------------------------------
  // SOURCE MAP — member → emitted line range → Java Origin (DESIGN.md §6.3)
  //
  // Emitted Scala had provenance only at the FILE level, so a scalac error or a stack frame could
  // not be attributed to a member, let alone to the Java that produced it. Every TIR node already
  // carries an `Origin` and the emitter knows the text it is writing; the two just never met.
  //
  // Positions are recovered by SEARCH rather than by threading an offset through every rendering
  // function: each class-body member is remembered as the exact string it rendered to, and
  // `srcMapOf` locates those strings in the finished unit. That keeps the instrumentation to one
  // wrapper — `memberStat` — instead of a cursor parameter on ~40 methods, and it cannot change a
  // byte of output, which is the property that lets the map be added without re-measuring the port.
  //
  // The search is sound because slots are reserved PRE-ORDER (a slot is appended before its member
  // renders, so a nested class precedes its own members) and the cursor only ever moves forward by
  // one character past a match. A nested member is therefore still findable inside its owner, while
  // two textually identical siblings resolve to two different positions.
  // ---------------------------------------------------------------------------

  private final class Slot(val member: String, val kind: String, val origin: Origin, val indent: Int):
    var text: String = ""
  private val slots   = collection.mutable.ArrayBuffer.empty[Slot]
  private val stmtSeq = collection.mutable.Map.empty[String, Int]

  /** [[stat]] for a member of a CLASS BODY, remembering what it rendered to. Identical to `stat`
    * in every observable way.
    *
    * UNCONDITIONAL, where it used to be skipped with the artifact layer off. The slots are no
    * longer only the source map's input: the recovery backstop anchors on them, so they decide
    * EMITTED TEXT — and a run with reporting off would otherwise emit a different file from the
    * same program, which is the one thing `Determinism` and the action cache both assume cannot
    * happen. */
  private def memberStat(s: Statement, i: Int): String =
    val slot = new Slot(memberKey(s), memberKind(s), s.origin, i)
    slots += slot
    recordMemberShape(slot.member, s)
    val t = stat(s, i)
    slot.text = t
    t

  /** …and the same member's CONTRACT row (`DESIGN.md` §8.3), keyed identically, so the orchestrator
    * attaches it to the port-map row without a second join.
    *
    * Keyed on the source-map key rather than on the symbol because that is what the port map's rows
    * are keyed by — and the key is built from the EMITTED name, which for a renamed member is not
    * the one a consumer holds. That is exactly why `name=` exists: the map's `upstream` column
    * already spells Java's name (the §4.55 passes rewrite `Symbol.name`, not `Symbol.fullName`),
    * and the EMITTED name is the half no artifact carried. */
  private def recordMemberShape(key: String, st: Statement): Unit =
    val symId = st match
      case d: Definition => Some(d.symbol)
      case _             => scala.None
    symId.foreach { id =>
      val m = sym(id)
      recordedMemberShapes(key) = Surface.MemberShape(
        // the emitted SIMPLE name, and only where it differs from Java's — sparse by design.
        name      = renamedMembers.get(id).filter(_ != m.name).map(_ => m.name).getOrElse(""),
        vis       = visOf(m, currentOwnerSym),
        // a java `static` lands in the COMPANION; a dependent emitting `Base.m()` needs the base's
        // answer, not its own re-derivation from the base's Java.
        placement = if m.flags.isStatic then "companion" else "class",
        // …and whether this member is a java BEAN PAIR this run COLLAPSED, and into which shape.
        form      = collapsedForms.getOrElse(id, ""),
      )
    }

  /** every member the `bean-properties` collapse turned into a PROPERTY, by symbol → `var`/`val`.
    *
    * Read off the phase's own `Decision`, never re-derived from the symbol table. `isMutable` is
    * true of every non-`final` java field this port emits, so it says "this is a `var`" and nothing
    * at all about whether a PAIR was collapsed into it — and the whole point of publishing this key
    * is that a dependent cannot tell the two apart (`Surface.MemberShape.form`). The decision is the
    * one place both halves are known, and the phase already records it per collapsed declaration.
    *
    * From `notes`, the PIPELINE's log, and not from `own`: this decision belongs to a phase, where
    * `renamedMembers` above belongs to the emitter's own §4.55 passes. */
  private lazy val collapsedForms: Map[SymId, String] =
    notes.all.iterator.collect {
      case d if d.kind == Decision.Kind.CollapsedProperty && d.subject != SymId.None &&
                d.detail.get("form").exists(_.nonEmpty) =>
        d.subject -> d.detail("form")
    }.toMap

  /** A member's stable identity. `owner#name` for anything that has a symbol — the form the rest
    * of this engine already uses (`Substitutions.dropMethods`, `RewriteTrace`) — with the
    * parameter types appended for a `def`, because Java overloading routinely puts eight `encode`s
    * in one class and a key that merges them cannot say which one changed.
    *
    * A class-body statement with NO symbol (a `@Test` body that a phase lowered to
    * `test("…"){ … }`, an initialiser) gets an ordinal within its owner. Ordinals shift when a
    * sibling is added, which over-reports change and never under-reports it; the statement's own
    * `Origin` — recorded beside it — is the part that actually locates the Java. */
  private def memberKey(s: Statement): String =
    val owner = classStack.lastOption.map(x => sym(x).fullName).getOrElse("?")
    s match
      case d: Tree.DefDef if !isInitBlock(d) =>
        s"${sym(d.symbol).fullName}(${d.paramss.flatten.map(v => shortTpe(v.tpt.tpe)).mkString(",")})"
      case d: Definition => sym(d.symbol).fullName
      case _ =>
        val n = stmtSeq.getOrElse(owner, 0) + 1
        stmtSeq(owner) = n
        s"$owner#<stmt$n>"

  private def memberKind(s: Statement): String = s match
    case _: Tree.ClassDef => "class"
    case d: Tree.DefDef   =>
      if isInitBlock(d) then "init" else if sym(d.symbol).name == "<init>" then "ctor" else "def"
    case _: Tree.ValDef  => "val"
    case _: Tree.TypeDef => "type"
    case _               => "stmt"

  /** simple, structural rendering of a parameter type — enough to tell two overloads apart without
    * dragging fully-qualified names (and their churn) into a key. */
  private def shortTpe(t: TypeRepr): String = t match
    case TypeRepr.AppliedType(tc, as) if as.nonEmpty => shortTpe(tc) + as.map(shortTpe).mkString("<", ",", ">")
    case _                                           => headSymOf(t).map(x => sym(x).name).getOrElse("?")

  // ---------------------------------------------------------------------------
  // THE RECOVERY BACKSTOP (`DESIGN.md` §8.8) — the completeness half of comment preservation.
  //
  // The attachment channel is the PRIMARY one and stays so: it is the only carrier of
  // statement-level position through a rewrite, and it places the overwhelming majority correctly.
  // What it cannot do is be COMPLETE. A construct the emission consumes takes its comments with it
  // — a promoted constructor has no braces left for a body comment to sit in, a `for` header
  // renders on one line, an expression position cannot hold a comment at all — and a comment that
  // reaches no emitted file is a comment the port lost, licence text included.
  //
  // So after the unit's text is built, every comment in this unit's JAVA that is not in it is put
  // back, after the member whose java span contains it, with the java coordinates on the line
  // above. That marker is the answer to V1's objection to hoisting: a comment relocated WITH its
  // source position is a quotation, not a false statement about the code below it.
  //
  // Two properties that are load-bearing rather than incidental:
  //
  //   - the insertion is BETWEEN slots, so no member's rendered text changes and no member digest
  //     moves — only the whole-file digest does;
  //   - a comment whose member the port DROPS is not recovered. Its absence is a decision, and
  //     `CommentAnchor` is the one place that answers "whose comment is this", so the run cannot
  //     recover a comment its own report then calls deliberate.
  // ---------------------------------------------------------------------------

  /** every declaration of every java file this program holds, emitted and dropped — computed once. */
  private lazy val anchorMembers: Map[String, List[CommentAnchor.Member]] = CommentAnchor.membersOf(program)

  private def recoverTrivia(cd: Tree.ClassDef, text: String): String =
    val path = cd.origin.javaPath
    if path.isEmpty || path == "<synthetic>" || path == "<unknown>" then text
    else javaSource(path) match
      case scala.None                 => text
      case Some(java) if java.isEmpty => text
      case Some(java) =>
        // WHICH comments are this unit's. A java file may declare several top-level types and
        // becomes that many scala files; without a window each of them would recover the others'
        // comments, and the same comment would land in every one.
        val here    = program.units.filter(_.origin.javaPath == path).sortBy(_.origin.line)
        val idx     = here.indexWhere(_.symbol == cd.symbol)
        val from    = if idx <= 0 then 0 else cd.origin.line
        val until   = if idx >= 0 && idx + 1 < here.size then here(idx + 1).origin.line else Int.MaxValue
        val members = anchorMembers.getOrElse(CommentAnchor.key(path), Nil)
        val lines   = java.linesIterator.toArray
        // PRESENCE is tested through the check's own normalisation — the shared function, never a
        // fork of it, or the emitter and the check disagree about what "already there" means. And
        // the engine's own commentary is stripped first: a porter note names an upstream FQN on
        // purpose, and a marker names an upstream PATH, so either can match a comment that is not
        // actually in the file.
        val hay  = TriviaCheck.normalize(TriviaMark.stripAll(text))
        val seen = collection.mutable.Set.empty[String]
        val put  = collection.mutable.ListBuffer.empty[(Int, String)]
        balticporter.core.CommentScanner.scanAt(java).foreach { a =>
          val line = a.line(java)
          val body = TriviaCheck.normalize(a.text)
          if body.nonEmpty && line >= from && line < until && !hay.contains(body) && seen.add(body) then
            val endLine = line + a.text.count(_ == '\n')
            val owner   = CommentAnchor.owner(lines, line, endLine, members)
            // a member the port drops has no declaration for its javadoc to sit above, and putting
            // it in the file anyway would document a member that is not there.
            if owner.forall(_.emitted) then
              val at   = slots.lastIndexWhere(s => s.origin.javaPath == path && s.origin.line <= line)
              val lvl  = if at >= 0 then slots(at).indent else 0
              val kind = a.kind match
                case balticporter.core.TriviaKind.Line    => TriviaKind.Line
                case balticporter.core.TriviaKind.Block   => TriviaKind.Block
                case balticporter.core.TriviaKind.Javadoc => TriviaKind.Javadoc
              val where = provenance.map(p => sourcePathOf(Origin(path, line, 0), p)).getOrElse(path)
              // …rendered through `triviaText`, so §4.58's rules hold for a recovered comment
              // exactly as for a placed one: a block comment Scala would NEST on goes out
              // line-by-line as `//`, and the indent is re-derived rather than reproduced.
              put += at -> (ind(lvl) + TriviaMark.render(where, line) + "\n" + triviaText(Trivia(kind, a.text), lvl))
        }
        if put.isEmpty then text else splice(text, put.toList)

  /** Insert each rendered block after the slot it anchors on (`-1` = after everything the unit
    * emitted, for a comment that precedes the first member).
    *
    * Slot positions come from the SAME forward-cursor search `srcMapOf` uses — one implementation
    * of "where did this member land", so an anchor and a source-map entry can never disagree.
    *
    * '''An ENCLOSING slot gains the insertion too.''' Slots nest: a nested class's own text
    * contains its members' text, so a comment placed after a nested member falls INSIDE the nested
    * class's recorded string, and `srcMapOf` — which finds a member by searching for exactly that
    * string — then cannot find it at all. Measured the first time this shipped: 2 UNLOCATABLE
    * members on libGDX core, a silent hole in the map that attributes every later error in those
    * types to the wrong member. The enclosing member's digest DOES move, and that is honest: it
    * really did gain a line. Only a comment appended AFTER a member (`off == end`, never inside)
    * leaves every digest alone, which is the ordinary case. */
  private def splice(text: String, put: List[(Int, String)]): String =
    val starts = Array.fill(slots.size)(-1)
    val ends   = Array.fill(slots.size)(-1)
    var cursor = 0
    slots.zipWithIndex.foreach { (s, k) =>
      if s.text.nonEmpty then
        val at = text.indexOf(s.text, cursor)
        if at >= 0 then { cursor = at + 1; starts(k) = at; ends(k) = at + s.text.length }
    }
    val ins = put.zipWithIndex.map { case ((slot, rendered), n) =>
      val off = if slot >= 0 && slot < ends.length && ends(slot) >= 0 then ends(slot) else text.length
      (off, n, "\n" + rendered)
    // back to front, so an earlier insertion cannot move a later offset — for the unit text and
    // for each slot's own copy alike. Stable within one offset: several comments anchored on one
    // member keep their source order.
    }.sortBy((off, n, _) => (-off, -n))
    val sb = new java.lang.StringBuilder(text)
    ins.foreach { (off, _, s) =>
      sb.insert(off, s)
      slots.zipWithIndex.foreach { (slot, k) =>
        if starts(k) >= 0 && off > starts(k) && off < ends(k) then
          val rel = off - starts(k)
          slot.text = slot.text.substring(0, rel) + s + slot.text.substring(rel)
      }
    }
    sb.toString

  /** Locate every remembered member in the finished unit text. The unit itself is always entry
    * one, spanning the whole file: a line that falls between members (a brace, a blank line, the
    * package clause) then still resolves to the right Java FILE instead of to nothing. */
  private def srcMapOf(unit: String, cd: Tree.ClassDef, text: String): List[SrcMap.Entry] =
    val root   = SrcMap.sourceRootOf(unit, cd.origin.javaPath)
    val starts = collection.mutable.ArrayBuffer(0)
    var k      = text.indexOf('\n')
    while k >= 0 do { starts += k + 1; k = text.indexOf('\n', k + 1) }
    val ls = starts.toArray
    def lineOf(off: Int): Int =
      var lo = 0; var hi = ls.length - 1
      while lo < hi do { val mid = (lo + hi + 1) / 2; if ls(mid) <= off then lo = mid else hi = mid - 1 }
      lo + 1
    val out = collection.mutable.ListBuffer(
      SrcMap.Entry(unit, unit, "class", 1, lineOf(math.max(0, text.length - 1)),
                   SrcMap.relativise(cd.origin.javaPath, root), cd.origin.line,
                   TirPrinter.sha256(text).take(16)))
    var cursor = 0
    slots.foreach { s =>
      if s.text.nonEmpty then
        val at = text.indexOf(s.text, cursor)
        // A member that cannot be found in the finished text is a hole in the map, and a map with
        // silent holes attributes an error to the wrong member. Counted and printed (SrcMap.write),
        // never swallowed — CLAUDE.md §3: the check arrives with the translation.
        if at < 0 then recordedMisses += s"$unit#${s.member}"
        else
          cursor = at + 1
          val st = lineOf(at)
          out += SrcMap.Entry(unit, s.member, s.kind, st, st + s.text.count(_ == '\n'),
                              SrcMap.relativise(s.origin.javaPath, root), s.origin.line,
                              TirPrinter.sha256(s.text).take(16))
    }
    out.toList

  /** The attribution + do-not-edit banner, in the same shape the BIR printer
    * ([[balticporter.emit.ScalaPrinter.header]]) has always emitted — one header, so a port that
    * still runs both backends produces one kind of file. Empty when no [[Provenance]] was given.
    *
    * The "Ported from" line names the ORIGINAL JAVA FILE for THIS unit, taken from the unit's own
    * `Origin` rather than reconstructed from its package: a nested or renamed type does not live at
    * the path its FQN suggests, and after [[balticporter.transform.PackageRenameTransform]] the FQN
    * is not the upstream one at all — attribution has to point at the upstream file, which is
    * exactly what `Origin` records and nothing else does. */
  private def header(cd: Tree.ClassDef): String = provenance match
    case scala.None => ""
    case Some(p) =>
      s"""|/*
          | * Generated by Baltic Porter ${EngineInfo.version} — DO NOT EDIT; regenerate instead.
          | *
          | * Ported from: ${sourcePathOf(cd.origin, p)}
          | * Original license: ${p.originalLicense} (see ${p.upstreamName} upstream)
          | * upstream-commit: ${p.upstreamCommit}
          | */
          |""".stripMargin

  /** The unit's Java source path as it should READ in a header: repo-relative where we can make it
    * so, honest where we cannot.
    *
    * `Origin.javaPath` is whatever absolute path the parser saw, so it is machine-local; three
    * outcomes, in order:
    *   1. relative to `Provenance.sourceRoot` — the reproducible answer, and what a real port sets;
    *   2. failing that, cut at `sourcePathPrefix` if the path contains it — the same answer without
    *      the configuration, when the prefix happens to be a real path segment;
    *   3. failing both, the path AS RECORDED — and flagged when it is ABSOLUTE, since only then is
    *      it machine-local. A wrong-but-plausible path is worse than a visibly unconfigured one:
    *      this is attribution, and the point of the line is that someone can find the original.
    *      Synthetic/unknown origins say so outright rather than naming a file that was never read.
    *
    * Case 3 is a §1(b) diagnostic — configure `Provenance.sourceRoot` — not an engine defect.
    *
    * Case 1 compares PATHS through `toRealPath`, on both sides (CLAUDE.md §5.4) — the third part of
    * the engine bitten by the same symlink. A worktree reaches the sibling source checkout through
    * `.claude/worktrees/<x>/../sge`, so the CONFIGURED root is a symlinked spelling while the
    * parser RECORDED the real one; compared lexically, case 1 silently failed only in worktrees and
    * the header fell to the marker cut, which cuts at the FIRST occurrence of the prefix and
    * rendered `gdx-vfx/gdx-vfx/core/…` there against `gdx-vfx/core/…` in the primary checkout.
    * Same commit, two spellings — and every whole-file digest a worktree-accepted baseline carried
    * was one the primary checkout could not reproduce. */
  private def sourcePathOf(o: Origin, p: Provenance): String =
    val raw = o.javaPath
    if raw.isEmpty || raw == "<synthetic>" || raw == "<unknown>" then
      "<unknown — the frontend recorded no source origin for this unit>"
    else
      val root   = p.sourceRoot.stripSuffix("/")
      val marker = p.sourcePathPrefix.stripSuffix("/")
      // §5.4: realpath where the path exists, normalize where it does not — on BOTH operands, and
      // through `RealPath`, the one implementation of that rule. The local helper caught
      // `IOException` alone, so a `SecurityException` or an `InvalidPathException` escaped and
      // killed the emission of the whole port.
      def realOrNormal(s: String): java.nio.file.Path = balticporter.core.RealPath.of(java.nio.file.Path.of(s))
      val rel =
        if root.nonEmpty then
          val rraw  = realOrNormal(raw)
          val rroot = realOrNormal(root)
          if rraw.startsWith(rroot) then Some(rroot.relativize(rraw).toString.replace('\\', '/'))
          else scala.None
        else scala.None
      val rel2 = rel.orElse {
        if marker.nonEmpty && raw.contains(marker + "/") then
          Some(raw.substring(raw.indexOf(marker + "/") + marker.length + 1))
        else scala.None
      }
      rel2 match
        case Some(r) if marker.nonEmpty                       => s"$marker/$r"
        case Some(r)                                          => r
        case scala.None if new java.io.File(raw).isAbsolute() =>
          s"$raw  (path as recorded — set Provenance.sourceRoot to relativise it)"
        case scala.None => raw // already relative: reproducible as it stands

  /** EVERY class the program declares, at any depth — the one walk the whole-program passes below
    * share, and the reason they are written as a `foreach` over a list rather than as thirteen
    * recursions.
    *
    * Each of those recursions descended `cd.body`, which is the class's MEMBERS. That is one node
    * short of java: a method-LOCAL class (JLS 14.3, catalog `JS-C30`) is a `BlockStatement` in a
    * member's body, so a body walk never reaches it — and thirteen separate passes then answered
    * about a type the program declares as if it were not there. Stated once, a node kind that
    * starts appearing in a new position is a fix in one place (`ENGINE-LIMITS.md` F8), and
    * `StandardTraversal` is the walk that is kept complete (CLAUDE.md §3).
    *
    * Enum-constant bodies are included by the same traversal, which is why the passes below no
    * longer carry a second `enumCases.foreach` line beside the first. */
  private lazy val allDeclaredClasses: List[Tree.ClassDef] =
    program.units.flatMap(u => StandardTraversal.allClassDefs(u)(using program))

  /** every type symbol that appears as a parent (extends/mixin) anywhere in the program — an
    * all-static class in this set must stay a `class`, since an `object` can't be extended. */
  private lazy val extendedTypes: Set[SymId] =
    val acc = collection.mutable.Set[SymId]()
    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s)      => Some(s)
      case TypeRepr.AppliedType(tc, _) => headSym(tc)
      case _                           => None
    allDeclaredClasses.foreach { cd =>
      cd.parents.foreach {
        case tt: TypeTree => headSym(tt.tpe).foreach(acc += _)
        case term: Term   => headSym(term.tpe).foreach(acc += _)
      }
    }
    acc.toSet

  /** every type symbol the program INSTANTIATES — an all-static class in this set must stay a
    * `class`, for the same reason as [[extendedTypes]]: you cannot `new` an object.
    *
    * The case that needed it is an EMPTY Java class. `private static class Dummy { }` has no
    * members, so "every member is static" is VACUOUSLY true and the collapse fired; the
    * `cd.body.nonEmpty` guard did not stop it, because the TIR carries a synthesised default
    * constructor and the body is therefore not empty. It emitted as `object Dummy`, and every
    * `new Dummy()` and every `Signal<Dummy>` in Ashley's suite stopped compiling — 26 errors from
    * one empty class.
    *
    * Walks with the STANDARD traversal rather than a private recursion (CLAUDE.md §3): a `new` can
    * appear anywhere a term can, including inside an anonymous class body, and a hand-rolled walk
    * here would find the ones its author remembered. */
  private lazy val instantiatedTypes: Set[SymId] =
    val acc = collection.mutable.Set[SymId]()
    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s)      => Some(s)
      case TypeRepr.AppliedType(tc, _) => headSym(tc)
      case _                           => None
    val collect = new Phase:
      def name: String = "emit/instantiated-types"
      override def transformNew(t: Tree.New)(using Program): Term =
        headSym(t.tpt.tpe).foreach(acc += _)
        t
    given Program = program
    program.units.foreach(u => StandardTraversal.mapClassDef(collect, u))
    acc.toSet

  /** every type symbol ANOTHER compilation unit names in a TYPE position — an all-static class in
    * this set must stay a `class`, for the third face of the same reason as [[extendedTypes]] and
    * [[instantiatedTypes]]: an `object` supplies a VALUE, and no value is a type.
    *
    * A Java class whose every member is `static` is still a TYPE. `class KHRMaterialsUnlit { static
    * final String EXT = …; }` has an implicit public constructor, so `KHRMaterialsUnlit.class` and
    * `T get(Class<T>, String)` at `T = KHRMaterialsUnlit` are ordinary Java, and gdx-gltf writes
    * both. Collapsed to `object`, the emitted Scala loses the name in type position entirely —
    * "type KHRMaterialsUnlit is not a member of sge.gltf.data.extensions", three errors from one
    * eight-line file. libGDX core has 31 all-static classes and never names one as a type, which is
    * why five ports did not see this; a library that CONSUMES another's constant-holders does.
    *
    * ==Read from DECLARATION types and class literals — NOT from every type the traversal visits==
    * `Phase.transformType` sees every type occurrence, which sounds like the safe over-approximation
    * and is the wrong one. A term's `tpe` is an occurrence too, so `Gdx.app` — an ordinary static
    * ACCESS, the one thing a collapsed object is perfect for — makes `Gdx` look named-as-a-type.
    * Measured: reading `transformType` bare de-collapsed **29 of libGDX core's 31** constant
    * holders (`Align`, `Gdx`, `Base64Coder`, `TimeUtils`, …), 36 members of emitted text, for a
    * question none of them answers. The port still compiled, which is exactly what makes a bad
    * approximation here expensive rather than loud.
    *
    * Two positions require a TYPE and nothing else does:
    *
    *   - a DECLARATION's type — every field, parameter, local, return and type argument of one.
    *     Read from `Symbol.info` through [[StandardTraversal.mapType]], so it is complete for every
    *     declaration the program has by construction rather than by an enumeration of node kinds
    *     ([[instantiatedTypes]] records what a hand-rolled walk costs).
    *   - a CLASS LITERAL. `Ext.class` needs the name to be a type even where nothing is declared at
    *     it, and it is the half `Symbol.info` cannot see: `ext(m, KHRMaterialsUnlit.class, EXT)`
    *     infers `T` and declares nothing.
    *
    * `extends` and `new` are the other two, and they already have [[extendedTypes]] and
    * [[instantiatedTypes]].
    *
    * The type's OWN declarations do not count — for the DECLARATION arm: a class names itself in
    * its members' owner types and in its synthesised constructor, so the owner chain of each symbol
    * is climbed and a candidate it reaches is skipped. Without that every class would name itself
    * and the collapse would be disabled outright rather than narrowed.
    *
    * A CLASS LITERAL in the type's own unit DOES count, deliberately. The java idiom for a log tag
    * is `private static final String TAG = VfxGLUtils.class.getSimpleName();` inside `VfxGLUtils`
    * itself; collapsed, `classOf[VfxGLUtils]` names nothing — and `classOf[VfxGLUtils.type]` is NOT
    * the answer, because `getSimpleName` on it is `"VfxGLUtils$"`, a silently different string than
    * java's (CLAUDE.md §3). Keeping the class costs nothing: its statics move to the companion,
    * which is where an `object` put them anyway, so every `X.member` call site is unchanged.
    * (Measured on gdx-vfx; subtracting the literal's own unit — the first shape of this merge —
    * fails exactly that case in `StaticCollapseSpec`.) */
  private lazy val typeNamedElsewhere: Set[SymId] =
    given Program = program
    val out = collection.mutable.Set[SymId]()

    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s)      => Some(s)
      case TypeRepr.AppliedType(tc, _) => headSym(tc)
      case _                           => None

    /** every symbol from `s` up through its owners — what "declared inside the candidate" means. */
    def enclosing(s: SymId): Set[SymId] =
      Iterator.iterate(Option(s))(_.flatMap(program.symbolOf(_).map(_.owner)))
        .take(64).takeWhile(o => o.isDefined && o.get != SymId.None).flatten.toSet

    def typesIn(t: TypeRepr): Set[SymId] =
      val seen = collection.mutable.Set[SymId]()
      val collect = new Phase:
        def name: String = "emit/type-named-elsewhere"
        override def transformType(x: TypeRepr)(using Program): TypeRepr =
          headSym(x).foreach(seen += _); x
      StandardTraversal.mapType(collect, t)
      seen.toSet

    // (1) declaration types, every symbol the program has.
    program.symbols.all.foreach { s => out ++= typesIn(s.info) -- enclosing(s.id) }

    // (2) class literals, which declare nothing. Scanned with the standard traversal so a `.class`
    // inside an anonymous-class body or a lambda is reached like any other term. The literal's OWN
    // unit is NOT subtracted here — `X.class` inside `X` still needs `classOf[X]` (the log-tag
    // idiom; see the doc above). Own-unit self-naming is only excluded on the declaration arm.
    program.units.foreach { u =>
      out ++= StandardTraversal.scanClassDef(u, Set.empty[SymId]) { (acc, term) =>
        term match
          case Tree.Literal(Constant.ClassOfC(t), _, _) => acc ++ typesIn(t)
          case _                                        => acc
      }
    }
    out.toSet

  /** D6's CROSS-MODULE FACE, which no count and no compile of the base could ever see.
    *
    * [[typeNamedElsewhere]] answers the question for a type THIS emitter renders, and it is right
    * about those. The other direction is the one that breaks a joint build: a BASE collapsed an
    * all-static class to a bare `object`, this module names it in a TYPE position, and `object` is a
    * value — no value is a type. The base's own run cannot see it (it has 31 such types and names
    * none of them as a type, which is why five ports did not), and this run's recomputation cannot
    * either, because this run does not emit the base and never takes the collapse branch.
    *
    * '''There is no local repair, and the outcome is ATTRIBUTION rather than a fix''' (§8.3's
    * honest-scope statement). The base is emitted and gone; nothing this module does makes `Align` a
    * type again. What the contract buys is that a bare "type Align is not a member of sge.utils"
    * becomes a finding naming the module that must change and which of §1's three kinds the fix is —
    * which is exactly the difference §4.45 measures a check by.
    *
    * Asked over the types this run NAMES, at the one moment both halves are in hand: the local
    * `typeNamedElsewhere` scan has already collected every type-position occurrence in this
    * program's units, and the view can say which of them a base emitted as an object. */
  private lazy val collapsedBaseTypesNamed: List[Surface.Gap] =
    typeNamedElsewhere.toList
      .filterNot(surface.owns)
      .flatMap { s =>
        val fqn = program.symbolOf(s).map(_.fullName).getOrElse("?")
        surface.typeShape(s) match
          case Surface.Answer.Published(shape, module) if shape.form == "object" =>
            List(Surface.Gap(fqn,
              s"$module emitted this type as a bare `object` (its every member is static), and this " +
                "module names it in a TYPE position. An `object` supplies a VALUE and no value is a " +
                "type, so the two modules cannot compile together",
              Some(module), fatal = false,
              fix = s"§1(b) PER-LIBRARY, IN THE BASE: nothing in this module can repair it — $module is " +
                "already emitted. Either that module keeps the type a `class` (its statics move to a " +
                "companion, so every `X.member` call site is unchanged), or this module stops naming it " +
                "as a type"))
          // Every other non-owned type this module names is either published as something nameable
          // or not published at all, and the SECOND is not a finding here: a type nobody publishes a
          // contract for is the ordinary JDK case, and `Plans` already reports the base types whose
          // absence actually shaped emitted text.
          case _ => Nil
      }
      .sortBy(_.subject)

  /** every type this unit DECLARES — what `typeSym` reads to decide "in scope by simple name".
    *
    * `StandardTraversal.allClassDefs` and not a body recursion, because a METHOD-LOCAL class
    * (`JS-C30`) stands in a member's block: read off `cd.body` alone the answer is "not declared
    * here", and the emitter then names it through `nestedPath` — a projection through the enclosing
    * METHOD, which names nothing at all. */
  private def declaredTypes(cd: Tree.ClassDef): Set[SymId] =
    StandardTraversal.allClassDefs(cd)(using program).map(_.symbol).toSet

  /** head symbols of a class's parent types (extends + mixins). */
  private def parentSymsOf(cd: Tree.ClassDef): List[SymId] =
    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s) => Some(s)
      case TypeRepr.AppliedType(tc, _) => headSym(tc)
      case _ => None
    cd.parents.flatMap { case tt: TypeTree => headSym(tt.tpe); case term: Term => headSym(term.tpe) }

  /** Types that have at least one `static` member, so a companion `object` holds it — and for a type
    * this run does NOT emit, the answer is the base's PUBLISHED `statics=` rather than a
    * re-derivation over the base's Java.
    *
    * The distinction is the whole of `DESIGN.md` §8.3 at this site. What an `export Parent.{… => _,
    * *}` must exclude is the set of names the parent's companion ACTUALLY DELIVERS, and for a base
    * parent that is a fact about the base's EMITTED output: §4.55 may have renamed a static, the
    * manifest may have dropped one, and the base's own run is the only place either happened.
    * Re-deriving it from the Java the dependent happens to have parsed gets Java's names back, which
    * is right exactly when nothing moved. libGDX core publishes 350 `name=` rows.
    *
    * `Unknown` keeps the local derivation — the pre-contract path — and records the question. */
  private def basePublishedStatics(s: SymId): Option[Set[String]] =
    if surface.owns(s) then scala.None
    else
      surface.typeShape(s) match
        case Surface.Answer.Own                 => scala.None
        case Surface.Answer.Published(shape, _) => Some(shape.statics.map(esc).toSet)
        case Surface.Answer.Unknown(why, module) =>
          surface.gap(Surface.Gap(sym(s).fullName,
            why + " — this run re-exports its companion, so it needs the static NAMES that companion " +
              "delivers; the local derivation over the base's java stands, and it does not see a " +
              "static the base renamed or dropped",
            module, fatal = false,
            fix = "§1(b) PER-LIBRARY: declare the module that emits this type as a base " +
              "(`base = \"…\"`) and re-run it with this engine so its port map carries `statics=`"))
          scala.None

  /** our-own types that have at least one `static` member (so a companion `object` holds it). */
  private lazy val typesWithStatics: Set[SymId] =
    val acc = collection.mutable.Set[SymId]()
    def scan(cd: Tree.ClassDef): Unit =
      val statics = basePublishedStatics(cd.symbol) match
        case Some(published) => published.nonEmpty
        case scala.None      => cd.body.exists { case d: Definition => sym(d.symbol).flags.isStatic; case _ => false }
      if statics then acc += cd.symbol
    allDeclaredClasses.foreach(scan)
    acc.toSet

  /** each type → the names of the `static` members it DECLARES itself. */
  private lazy val ownStaticsBySym: Map[SymId, Set[String]] =
    val m = collection.mutable.Map[SymId, Set[String]]()
    def scan(cd: Tree.ClassDef): Unit =
      m(cd.symbol) = basePublishedStatics(cd.symbol).getOrElse(
        cd.body.collect { case d: Definition if sym(d.symbol).flags.isStatic => esc(sym(d.symbol).name) }.toSet)
    allDeclaredClasses.foreach(scan); m.toMap

  /** every static name a companion re-export of `s` delivers, mapped to the type that DECLARES it —
    * `s`'s own statics, then its ancestors' (nearest declaration wins, as in Java). The owner is
    * what makes two exports comparable: the same name from the same declaring type is the same
    * constant arriving twice (a diamond — `GL30Interceptor extends GLInterceptor with GL30`, where
    * `GLInterceptor` implements `GL20` and `GL30` extends it), which Scala rejects as a duplicate
    * definition; the same name from DIFFERENT types is a real redeclaration and must not be merged. */
  private def staticOwnersOf(s: SymId, seen: Set[SymId] = Set.empty): Map[String, SymId] =
    if seen(s) then Map.empty
    else
      val inherited = parentsBySym.getOrElse(s, Nil)
        .foldLeft(Map.empty[String, SymId])((acc, p) => staticOwnersOf(p, seen + s) ++ acc)
      inherited ++ ownStaticsBySym.getOrElse(s, Set.empty).map(_ -> s).toMap

  /** each type → its own `static` members, by the name they are EMITTED under. `ownStaticsBySym`
    * carries the names alone, and a re-export has to reach the SYMBOL to ask how it renders. */
  private lazy val ownStaticSymsBySym: Map[SymId, Map[String, SymId]] =
    val m = collection.mutable.Map[SymId, Map[String, SymId]]()
    def scan(cd: Tree.ClassDef): Unit =
      // A STATIC INITIALIZER BLOCK is not a NAME. Java calls it `<clinit>`, no Scala identifier can
      // spell it, and an exclusion naming it is `export P.{<clinit> => _, *}` — which the parser
      // reads as an XML start tag. Measured as 29 `E040 Syntax` errors the first time this table
      // forgot the filter `ownStaticsBySym`'s twin already carries.
      m(cd.symbol) = cd.body.collect {
        case d: Definition if sym(d.symbol).flags.isStatic &&
          (!d.isInstanceOf[Tree.DefDef] || !isInitBlock(d.asInstanceOf[Tree.DefDef])) =>
          esc(sym(d.symbol).name) -> d.symbol
      }.toMap
    allDeclaredClasses.foreach(scan); m.toMap

  /** The names a companion re-export must NOT forward: a parent static that did not render PUBLIC.
    *
    * `export P.*` creates a forwarder at the EXPORTING object's own visibility, so a same-package
    * companion re-exporting a `private[p]` static publishes it to every package — silently undoing
    * §8.7's mapping for exactly the members Java scoped most tightly. Filtering is also the
    * FAITHFUL rendering rather than a repair: Java's own access to a parent's package-private
    * static is package-scoped, and it is unreachable through a subclass name from outside the
    * package. Nothing this emitter writes depends on the forwarder either — a static reference is
    * emitted through its DECLARING owner (`staticRef`), never through the subclass.
    *
    * A wildcard export of an INACCESSIBLE member is not an error — Scala simply skips it — so this
    * only ever removes a leak the compiler would not have reported. */
  private def nonPublicStatics(delivered: Map[String, SymId]): Set[String] =
    delivered.collect {
      case (n, owner) if ownStaticSymsBySym.getOrElse(owner, Map.empty).get(n)
        .exists(id => visPlan.getOrElse(id, Visibility.Vis.Public) != Visibility.Vis.Public) => n
    }.toSet

  /** each type → its parent symbols (whole program). */
  private lazy val parentsBySym: Map[SymId, List[SymId]] =
    val m = collection.mutable.Map[SymId, List[SymId]]()
    allDeclaredClasses.foreach(cd => m(cd.symbol) = parentSymsOf(cd)); m.toMap

  /** does this type OR any ancestor have static members? (so its companion carries or re-exports
    * them — the export chain must pass THROUGH intermediates that add no statics of their own). */
  private def staticsReachable(s: SymId, seen: Set[SymId] = Set.empty): Boolean =
    !seen(s) && (typesWithStatics(s) || parentsBySym.getOrElse(s, Nil).exists(p => staticsReachable(p, seen + s)))

  /** every strict ancestor of `s`. */
  private def ancestorsOf(s: SymId, seen: Set[SymId] = Set.empty): Set[SymId] =
    parentsBySym.getOrElse(s, Nil).filterNot(seen).foldLeft(Set.empty[SymId]) { (acc, p) =>
      acc + p ++ ancestorsOf(p, seen + s + p)
    }

  // ---- names ----
  private def sym(id: SymId): Symbol = program.symbolOf(id).getOrElse(Symbol(id, "?", "?", Flags(), SymId.None, TypeRepr.NoType))
  private def local(id: SymId): String = esc(sym(id).name)

  /** a method symbol's declared parameter types, empty when its info is not a method type — used to
    * give an unbound method reference the arity java gave it. */
  private def methodParams(id: SymId): List[TypeRepr] = sym(id).info match
    case TypeRepr.MethodType(ps, _, _)                       => ps.map(_._2)
    case TypeRepr.PolyType(_, TypeRepr.MethodType(ps, _, _)) => ps.map(_._2)
    case _                                                   => Nil

  /** backtick an identifier that collides with a Scala keyword. */
  private def esc(name: String): String = TirEmitter.esc(name)

  /** backtick every keyword SEGMENT of a qualified name (§4.56 separators). */
  private def escPath(path: String): String = TirEmitter.escPath(path)

  /** is this type a type VARIABLE the frontend could not resolve ([[Symbol.UnresolvedTypeVarPrefix]])?
    *
    * The emitter's standing obligation is that such a symbol never reaches the output: its name is
    * a MARKER, not a name, so `?E` is neither a type nor a token sequence Scala can lex — one
    * occurrence took out the statement around it and two more errors with it. */
  private def isUnresolvedTypeVar(t: TypeRepr): Boolean = t match
    case TypeRepr.TypeRef(_, s) => Symbol.isUnresolvedTypeVar(sym(s).fullName)
    case _                      => false
  /** a TYPE symbol's rendered name. FULLY QUALIFIED by default — for the structural Java→Scala
    * phase we emit fully-qualified references and generate NO imports, which deletes the entire
    * import-decision bug class (import-vs-projection, shadowing, static-receiver qualification):
    * a reference is now a context-free function of the symbol's owner chain. Only two things
    * stay unqualified: type params, and a type declared in THIS unit (in scope by simple name).
    * Human-readable imports are a separate, optional beautification backend, not a correctness
    * prerequisite. (A later refinement handles givens/extensions, which FQN genuinely can't name.) */
  private def typeSym(id: SymId): String =
    val s = sym(id)
    // an UNRESOLVED type variable is a marker and not a name — never print it (see
    // [[isUnresolvedTypeVar]]). `?` is what G2 settles an un-nameable type argument renders as, and
    // in the one position where `?` is not a type either, it is a CONTAINED error rather than a
    // lexical one that takes the enclosing statement with it.
    if Symbol.isUnresolvedTypeVar(s.fullName) then "?"
    else if tparamSubst.contains(id) then tpe(tparamSubst(id)) // ctor type param → its bound
    else if s.flags.isParam then esc(s.name)
    // a Java `static` nested class is lowered into the enclosing type's companion `object`, so it
    // is named through the value path `Outer.Inner` — NOT by simple name (companion members aren't
    // in the class's scope) and NOT `Outer#Inner` (a type projection can't reach a companion member).
    else if s.flags.isStatic && s.fullName.contains('$') then nestedPath(id)
    // a Java INNER (non-static) class is a PATH-dependent type in Scala: named by simple name inside
    // the enclosing class it means `this.Inner`, so the same Java type reached through two different
    // instances (`pa.Channel` vs `ParallelArray#Channel` from another file) never unifies, and a
    // method bounded `<T extends Channel>` cannot accept an initializer written against the outer
    // view. Name it by PROJECTION everywhere instead — one type for all instances. `extends` and
    // `new` need an instantiable/stable name, so those two positions opt out (see `namedInner`).
    else if program.definitionOf(id).isDefined && currentDeclared(id) then
      if namedInner || !isInnerClass(id) then esc(s.name) // declared here — in scope
      else nestedPath(id)
    else if program.symbolOf(s.owner).exists(_.flags.isModule) then s"${typeValue(s.owner)}.${esc(s.name)}" // object's type member → path-dependent `O.T`
    // An inner class of an ANCESTOR is an INHERITED member type, in scope by its simple name
    // anywhere inside the subclass — `class TextArea extends TextField` sees
    // `TextFieldClickListener` exactly as Java did. The projection is not merely verbose here, it
    // is illegal: `TextField#TextFieldClickListener` needs `TextField` to be an immutable path.
    else if inheritedNested(s.owner) then esc(s.name)
    else nestedPath(id)                                             // non-static inner class elsewhere → `Outer#Inner`

  /** is `owner` an ancestor of some class we are currently rendering inside? */
  private def inheritedNested(owner: SymId): Boolean =
    owner != SymId.None && classStack.exists(c => c != owner && ancestorsOf(c).contains(owner))

  /** the path to a NESTED type, choosing a separator PER LEVEL: `.` where that level is a Java
    * `static` nested class (lowered into the enclosing companion `object`, so reachable only through
    * the value path) and `#` where it is a genuine inner class (a type projection). A blanket
    * `fullName.replace('$', '#')` gets a MIXED chain wrong — `ModelInfluencer.Random` is static and
    * holds the inner `ModelInstancePool`, so the type is `ModelInfluencer.Random#ModelInstancePool`
    * while `ModelInfluencer#Random#ModelInstancePool` names nothing at all. Falls back to the
    * blanket form whenever an owner symbol is unknown, so this can only ever add precision. */
  private def nestedPath(id: SymId): String =
    def go(x: SymId): Option[String] =
      val sx = sym(x)
      if !sx.fullName.contains('$') then Some(escPath(sx.fullName))
      else if sx.owner == SymId.None || program.symbolOf(sx.owner).isEmpty then None
      else go(sx.owner).map(p => p + (if sx.flags.isStatic then "." else "#") + esc(sx.name))
    // The fallback fires exactly when an owner is UNKNOWN, which for a type we do not define means
    // an external/JDK one. Name those with `.`: a Java nested type is reached as `Outer.Inner` in
    // Scala, and a `#` projection is not even available — it needs the prefix to be an immutable
    // path, which a bare external class name is not (`java.nio.channels.FileChannel#MapMode`).
    go(id).getOrElse:
      val sep = if program.definitionOf(id).isEmpty then '.' else '#'
      escPath(sym(id).fullName).replace('$', sep)

  /** a NON-static nested class of one of our own NON-GENERIC classes (not of a companion `object`).
    * A generic enclosing class is excluded: `Octree#OctreeNode` is not a legal projection — the
    * prefix would need type arguments, which the reference does not carry. */
  private def isInnerClass(id: SymId): Boolean =
    val s = sym(id)
    !s.flags.isStatic && s.owner != SymId.None && s.fullName.contains('$') &&
      !program.symbolOf(s.owner).exists(_.flags.isModule) &&
      program.definitionOf(s.owner).exists { case c: Tree.ClassDef => c.tparams.isEmpty; case _ => false }

  /** inside an `extends` clause or a `new`, where a type projection is not legal — render inner
    * classes by their simple (in-scope) name there. */
  private var namedInner = false
  private def byName[A](f: => String): String =
    val prev = namedInner; namedInner = true
    try f finally namedInner = prev

  private def ind(n: Int): String = "  " * n

  // ---------------------------------------------------------------------------
  // TRIVIA — the original Java comments, re-emitted above the node that carried them.
  //
  // Three decisions, all of them made once here so that the output is DETERMINISTIC rather than
  // whitespace-faithful (a port is regenerated on every engine change; a diff that moves because a
  // comment re-wrapped is a diff nobody reads):
  //
  //   1. RE-INDENTED to the node, not reproduced at the column Java used. A `/** … */` on a nested
  //      class's method sat at column 4 upstream and lands at whatever depth the emitted class
  //      nests to; left at its original column it would read as a comment on the enclosing class.
  //      Relative alignment INSIDE the comment is preserved — the common leading whitespace of the
  //      continuation lines is the only thing removed — so a commented-out code block keeps its
  //      shape and a Javadoc keeps its ` * ` gutter.
  //   2. Exactly ONE newline between the comment and its node, whatever the Java had. Blank lines
  //      inside a comment survive; blank lines around it do not, because nothing carries them.
  //   3. VERBATIM otherwise: every non-whitespace character of the original, delimiters included.
  //
  // ## Why no escaping is needed, and the one case where it is
  //
  // A comment's text is inert to Scala's parser in every way that matters BUT ONE. `*/` inside a
  // `//` line comment is nothing; a Javadoc full of `@param`, backticks, `$`, unclosed braces or
  // Scala-significant text is nothing, because none of it is read. Scala's block comments, however,
  // NEST and Java's do not — so a Java block comment whose body contains `/*` (perfectly legal:
  // `/* see the /* marker */` ends at the first `*/` in Java) opens a nested comment in Scala that
  // never closes, and SWALLOWS THE REST OF THE FILE. That is not hypothetical prettiness: it turns
  // one upstream comment into a file that does not compile, with an error pointing at the end of
  // the file.
  //
  // The guard is the minimal one: such a comment is re-emitted LINE BY LINE as `//` comments, so
  // every character of the original — its `/*` and `*/` delimiters included — is still in the
  // output, and nothing in it can open anything.
  // ---------------------------------------------------------------------------

  /** the block of comment lines that precedes a node, with its trailing newline; `""` for none. */
  private def leading(ts: List[Trivia], i: Int): String =
    if ts.isEmpty then "" else ts.map(triviaText(_, i)).mkString("\n") + "\n"

  /** does this block comment contain a delimiter that Scala would read as nesting? */
  private def nests(t: Trivia): Boolean =
    val body = t.text.stripPrefix("/**").stripPrefix("/*").stripSuffix("*/")
    body.contains("/*") || body.contains("*/")

  private def triviaText(t: Trivia, i: Int): String =
    val lines = t.text.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1).toList
    t.kind match
      case TriviaKind.Line                 => ind(i) + lines.head.trim
      case _ if nests(t)                   => lines.map(l => (ind(i) + "//" + l).stripTrailing()).mkString("\n")
      case _                               =>
        val rest   = lines.tail
        val filled = rest.filter(_.trim.nonEmpty)
        val cut    = filled.map(_.takeWhile(c => c == ' ' || c == '\t').length).minOption.getOrElse(0)
        val gutter = filled.nonEmpty && filled.forall(_.trim.startsWith("*"))
        val pre    = ind(i) + (if gutter then " " else "")
        ((ind(i) + lines.head.trim) :: rest.map(l => if l.trim.isEmpty then "" else (pre + l.drop(cut)).stripTrailing()))
          .mkString("\n")

  // ---- definitions ----
  /** the classes currently being rendered, outermost first. Lets a `Tree.This` naming an ENCLOSING
    * class render Java's qualified `Outer.this` rather than a bare `this` (which names the inner one). */
  private val classStack = collection.mutable.ArrayDeque[SymId]()

  private def classDef(cd: Tree.ClassDef, i: Int): String =
    val outer = currentClass
    currentClass = Some(cd)
    classStack.append(cd.symbol)
    try classDef0(cd, i) finally { classStack.removeLast(); currentClass = outer }

  /** Parameter symbol -> the type it must be RENDERED at, because the member overrides one
    * inherited through a RAW parent.
    *
    * Java sees a raw supertype's members ERASED: `class ParticleController implements
    * ResourceData.Configurable` (raw) implements `save(AssetManager, ResourceData)` at the
    * erasure, not at any instantiation. Our raw fill independently rendered the parameter
    * `ResourceData[?]`, while the parent — which cannot keep a wildcard, `extends
    * Configurable[?]` being illegal Scala — was de-wildcarded to `Configurable[Object]`. Two
    * renderings of one raw type in one class, and the override implements neither.
    *
    * Both now come from ONE answer: [[deWildcardedArgs]] decides the parent's arguments, and the
    * same substitution is applied to the parent's declared parameter types to give this class's
    * overriding parameters. Agreement is by construction rather than by two rules happening to
    * coincide — which is what the earlier name-directed inherited-instantiation rule could not
    * promise, and why it was reverted.
    *
    * Only slots where OUR rendering is a wildcard are touched, so an override that already agrees
    * is left exactly as it is. */
  private def rawParentAlignment: Map[SymId, TypeRepr] =
    val out    = collection.mutable.Map[SymId, TypeRepr]()
    val done   = collection.mutable.Set[SymId]()
    val declOf = collection.mutable.Map[SymId, Tree.ClassDef]()
    allDeclaredClasses.foreach(cd => declOf(cd.symbol) = cd)
    def methodsOf(cd: Tree.ClassDef) = cd.body.collect {
      case d: Tree.DefDef if sym(d.symbol).name != "<init>" => d
    }
    /** parents first, so a grandchild aligns against its parent's ALREADY-aligned view. */
    def visit(cd: Tree.ClassDef, seen: Set[SymId]): Unit =
      if !done(cd.symbol) && !seen(cd.symbol) then
        done += cd.symbol
        val ours = methodsOf(cd)
        for
          p    <- cd.parents
          pt    = p match { case tt: TypeTree => tt.tpe; case t: Term => t.tpe }
          tycon <- headSymOf(pt)
          pcd  <- declOf.get(tycon)
        do
          visit(pcd, seen + cd.symbol)
          val subst = pt match
            case TypeRepr.AppliedType(tc, args)
                if args.exists { case _: TypeRepr.TypeBounds => true; case _ => false } &&
                   pcd.tparams.sizeIs == args.size =>
              pcd.tparams.map(_.symbol).zip(deWildcardedArgs(tc, args))
                .collect { case (s, Some(t)) => s -> t }.toMap
            case _ => Map.empty[SymId, TypeRepr]
          // Search the parent CHAIN, not just the direct parent's own body: `RegionInfluencer
          // extends Influencer extends ParticleControllerComponent implements Configurable` — only
          // the last declares `load`, and `Influencer` in between declares nothing at all.
          def findUp(c: Tree.ClassDef, name: String, ar: List[Int], seen: Set[SymId]): Option[Tree.DefDef] =
            if seen(c.symbol) then scala.None
            else methodsOf(c).find(d => sym(d.symbol).name == name && d.paramss.map(_.size) == ar)
              .orElse(c.parents.iterator.map { case tt: TypeTree => tt.tpe; case x: Term => x.tpe }
                .flatMap(t => headSymOf(t).flatMap(declOf.get))
                .flatMap(pp => findUp(pp, name, ar, seen + c.symbol)).nextOption())
          for
            od <- ours
            pd <- findUp(pcd, sym(od.symbol).name, od.paramss.map(_.size), Set.empty)
            (ops, pps) <- od.paramss.zip(pd.paramss)
            (op, pp)   <- ops.zip(pps)
            if hasWildcardArg(op.tpt.tpe) && !out.contains(op.symbol)
          do
            val aligned = substTp(out.getOrElse(pp.symbol, pp.tpt.tpe), subst)
            // The parent member is matched by NAME AND ARITY, which is all java overriding needs
            // — and far too little on its own. `Environment extends Attributes` inherits
            // `remove(long mask)`, which matches `Environment.remove(BaseLight)` on both counts;
            // aligning to it rendered three overloads as `remove(Long)`. An alignment is only ever
            // the SAME type at different arguments, so require the head constructor to agree.
            if !hasWildcardArg(aligned) && headSymOf(aligned) == headSymOf(op.tpt.tpe) then
              out(op.symbol) = aligned
              // JS-G06 — the CITATION surface, and deliberately not an obligation: this is a
              // whole-program pass and nothing can assert that it *should have* considered a
              // difference at a declaration it never visited (`DESIGN.md` §2.8). Cited at the
              // DECLARATION whose signature this moves, which is the granularity `Decision` uses,
              // and idempotent per member for the same reason.
              catalog.cite(JS.G(6), sym(od.symbol).fullName)
    program.units.foreach(u => declOf.values.foreach(visit(_, Set.empty)))
    out.toMap

  private lazy val overrideAlign: Map[SymId, TypeRepr] = rawParentAlignment

  /** An ARGUMENT reaching a parameter [[rawParentAlignment]] re-rendered. Java accepted the call
    * because the callee's formal was RAW there (`ParticleEffect.save(AssetManager, ResourceData)`
    * taking a `ResourceData<ParticleEffect>`); once the formal reads `ResourceData[Object]` the
    * conversion java made silently has to be written. Only fires where the argument disagrees. */
  private def alignedArgs(m: SymId, args: List[Term], i: Int): Option[List[String]] =
    val ps = program.definitionOf(m).collect { case d: Tree.DefDef => d.paramss.flatten }.getOrElse(Nil)
    if ps.sizeIs != args.size || !ps.exists(v => overrideAlign.contains(v.symbol)) then scala.None
    else Some(args.zip(ps).map { (a, v) =>
      overrideAlign.get(v.symbol).filter(_ != a.tpe) match
        case Some(t) => s"${operand(a, i)}.asInstanceOf[${tpe(t)}]"
        case None    => term(a, i)
    })

  /** A cast onto a parameter that [[rawParentAlignment]] re-rendered must land on the type the
    * parameter now HAS. The frontend built these casts against the raw fill's `ResourceData[?]`;
    * once the declaration reads `ResourceData[Object]` the same cast narrows to a wildcard the
    * callee will not take — and a cast to `T[?]` asserts nothing in the first place, so following
    * the alignment loses nothing. Only wildcarded targets on an aligned symbol are touched. */
  /** a POLY EXPRESSION (JLS 15.2) — a lambda or a method reference, the two java forms that have no
    * type of their own in EITHER language and take one from the slot they fill. The emitter's own
    * copy of `SpoonTir.polyExpression`'s question, asked of the TIR rather than of Spoon, because
    * this is the one place a cast on such a term can still be reached: the frontend's rule stops
    * the ENGINE writing one, and a cast the java SOURCE wrote is kept by design. `uncomment`,
    * because trivia wraps a term without changing what it is. */
  private def polyOperand(t: Term): Boolean = Tree.uncomment(t) match
    case _: Tree.Lambda | _: Tree.MethodRef => true
    case _                                  => false

  private def castTarget(e: Term, target: TypeRepr): TypeRepr =
    if !hasWildcardArg(target) then target
    else
      val s = e match
        case Tree.Ident(x, _, _)     => Some(x)
        case Tree.Select(_, x, _, _) => Some(x)
        case _                       => scala.None
      s.flatMap(overrideAlign.get).getOrElse(target)

  /** `this` in Scala always names the INNERMOST class, where Java's `Outer.this` names an enclosing
    * one. Qualify by simple name when the symbol is an enclosing class actually being rendered
    * around this point; anything else (an inherited/unknown owner) keeps the bare `this`.
    *
    * A SUPERTYPE is never qualified even when it also encloses: libGDX nests subclasses inside their
    * own base (`DynamicsModifier.FaceDirection extends DynamicsModifier`), and constructor replay
    * moves the base's `this` statements into the subclass body — there the bare `this` is exactly
    * right, while `DynamicsModifier.this` would name the companion object. */
  private def thisRef(s: SymId): String =
    val inner = classStack.lastOption
    if inner.contains(s) || !classStack.contains(s) || inner.exists(inheritsFrom(_, s)) then "this"
    else s"${esc(sym(s).name)}.this"

  /** is `child` `anc`, or a (transitive) subtype of it, among our own definitions? */
  private def inheritsFrom(child: SymId, anc: SymId): Boolean =
    val seen = collection.mutable.Set[SymId]()
    def go(c: SymId): Boolean =
      c == anc || (seen.add(c) && program.definitionOf(c).collect { case cd: Tree.ClassDef =>
        parentSymsOf(cd).exists(go)
      }.getOrElse(false))
    go(child)

  private def classDef0(cd: Tree.ClassDef, i: Int): String =
    // The owner is set for BOTH lowerings. An enum's own members need a `private` qualifier by the
    // same rule an ordinary nested class's do, and dispatching before the assignment gave a nested
    // enum the ENCLOSING type's context — a bare `private` where java's scope is the whole
    // top-level enclosure.
    val savedOwner = currentOwnerSym
    currentOwnerSym = cd.symbol
    try (if sym(cd.symbol).flags.isEnum then enumDef(cd, i) else classDef1(cd, i))
    finally currentOwnerSym = savedOwner

  private def classDef1(cd: Tree.ClassDef, i: Int): String =
    val s  = sym(cd.symbol)
    // A NESTED type carries its own notes at its `class` keyword; the TOP-LEVEL one's are the
    // file's (`unitNotes`) and must not be printed twice.
    val cnote = if cd.symbol == currentTopLevelSym then "" else declNotes(cd.symbol, i)
    val bnote = bodyNotes(cd.symbol, i + 1)
    val kw =
      if s.flags.isModule then "object"
      else if s.flags.isTrait then "trait"
      else "class"
    val tps     = if cd.tparams.isEmpty then "" else "[" + cd.tparams.map(typeParam).mkString(", ") + "]"
    // lower Java constructors: `CtorFunnel` nominates one to become Scala's PRIMARY. Its body is
    // inlined (those statements run at construction), its `super(args)` moves into the `extends`
    // clause (which also fixes parents that need constructor arguments), and its PARAMETERS become
    // the class's parameters. Every other constructor stays a `def this(...)` delegating to it.
    val plan    = if s.flags.isModule then CtorFunnel.Plan.none else plans(cd)
    val (loweredBody, superArgs) = (lowerCtors(cd.body, plan), plan.superArgs)
    val pparams = plan.primaryParams
    // A SYNTHESISED primary (CtorFunnel.Plan.synthetic) has no java constructor behind it, so its
    // parameters are rendered from the plan's own (name, type) pairs rather than from symbols.
    //
    // It is `protected`, and the reason is a corrected fact. This comment used to assert that
    // "scala's `extends C(args)` can only ever invoke C's PRIMARY, so hiding it would make the class
    // unextendable" — which is FALSE, and was the only thing keeping a constructor java never
    // declared in the published API. Compiled and run: a `private` primary with three secondaries is
    // reached by `class D extends p.C("hello")`, `class E extends p.C()` and `class F(k: Int) extends
    // p.C(k.toString)` from ANOTHER package; a `protected` primary is reached DIRECTLY by a
    // subclass's `extends` clause in another package, and by an anonymous `new G(3, false) {}`.
    //
    // `protected` rather than `private` because `private` is CLASS-private in scala, not
    // package-private: a SAME-package subclass sees only the nilary secondary ("too many arguments
    // for constructor A ... : (): g.A"). Choosing between them per class would mean asking "is this
    // class extended?", which is the whole-program question `ENGINE-LIMITS.md` D4 measures as drift
    // — and it is asked at emission, one module at a time, so a dependent would answer it
    // differently from the base. `protected` needs no such question, cannot be reached by ordinary
    // client code, and is what the reference ports write on every funnel class that is subclassed.
    // Bare `protected`, never `protected[pkg]`: a package qualifier would deny exactly the
    // cross-module subclassing this choice exists to permit (DESIGN.md §8.11).
    //
    // A DISAMBIGUATED synthesis takes one more parameter, of a marker type minted in this class's
    // own companion (`CtorFunnel.Plan.marker`). It is there to change the primary's ARITY, which is
    // what removes it from every `this(<a root's super arguments>)` overload candidate set at once
    // — `ENGINE-LIMITS.md` C8, where a real constructor narrower than the parent's formals won the
    // call and delegated to itself. The type is named through the companion's VALUE path, and it is
    // `protected` there, never `private`: scala requires every type in a member's signature to be
    // at least as visible as the member (C9).
    val markerParam = plan.marker.map(n => s"ctor$$: ${typeValue(cd.symbol)}.${esc(n)}").toList
    // …and the CONTEXT CLAUSE a phase put on this class's constructors (`CtorFunnel.Plan.givens`),
    // rendered as its own GROUP through `paramClause`. Java's parameter list is one list and
    // scala's is a list of groups; flattened into the value parameters the `using` is lost and the
    // class reads `class Scene($p: demo.Ctx)` — an ordinary parameter, no given in scope, every
    // `summon` in the body unresolved. That was one of `ENGINE-LIMITS.md` CT4's three causes, and it
    // is the one that lived HERE: the other two were the funnel reading such a constructor as
    // paramful and declining to promote it. Empty for every port that threads nothing, which is why
    // no emitted byte moves.
    val givenClause = plan.givens.map(paramClause).mkString
    // A PROMOTED java constructor is still a java DECLARATION, so §8.7's mapping governs it exactly
    // as it governs the `def this` secondaries — one rule per kind of declaration (§8.11). The
    // SYNTHESISED primary above is the deliberate exception: it is not a java declaration at all,
    // and bare `protected` is the pair of answers it needs — wider in the subclass direction, so a
    // dependent module in another package can still extend the class, and narrower in the package
    // direction, where nothing legitimate calls it but this class's own secondaries.
    val ctorVis = plan.primary.map(pc => vis(sym(pc.symbol), privateQualifier(s.owner))).getOrElse("")
    // …and a promoted parameter the java constructor ASSIGNS TO is a `var`.
    //
    // A java constructor parameter is an ordinary LOCAL and may be reassigned; a scala class
    // parameter is a `val`. Promoted unchanged, `C(int x) { x = x * 2; this.f = x; }` emits
    // `x$p = x$p * 2` — `E052 Reassignment to val`, loud but uncounted, because no library in this
    // corpus happens to write it. A record's COMPACT constructor is the shape that makes it
    // ordinary rather than exotic: JLS 8.10.4 exists PRECISELY so a record can normalise its
    // components by assigning the parameters, and the appended field assignments then read what the
    // body left.
    //
    // `private var` and not `var`: java's parameter is not a member at all, so the promotion must
    // not put a name on the emitted surface. Class-private is enough for every reference there can
    // be — they are all inside the class that promoted the constructor — and it keeps the header's
    // arity, its types and its descriptor exactly as they were.
    //
    // Decided from the LOWERED body, which is where the promoted statements are (`plan.primaryBody`
    // is only half of the picture once `lowerCtors` has run), and by SYMBOL rather than by name
    // (§4.56). Every write in this IR is a `Tree.Assign` — the frontend desugars `x *= 2`, `x++`
    // and `--x` into one — so the scan is complete.
    val mutatedParams: Set[SymId] =
      if pparams.isEmpty then Set.empty
      else
        val ps  = pparams.map(_.symbol).toSet
        val acc = collection.mutable.Set.empty[SymId]
        val scan = new Phase:
          def name: String = "emit/mutated-primary-params"
          override def transformTerm(t: Term)(using Program): Term =
            t match
              case Tree.Assign(Tree.Ident(sy, _, _), _, _, _) if ps(sy) => acc += sy
              case _                                                    => ()
            t
        StandardTraversal.mapClassDef(scan, cd.copy(body = loweredBody))(using source)
        acc.toSet
    def primaryParam(v: Tree.ValDef): String =
      if mutatedParams(v.symbol) then s"private var ${param(v)}" else param(v)
    val prim    =
      if plan.isSynthesised then
        s" protected (${(plan.synthetic.map((n, t) => s"$n: ${tpe(t)}") ++ markerParam).mkString(", ")})$givenClause"
      else if pparams.nonEmpty then s"${if ctorVis.isEmpty then "" else " " + ctorVis}(${pparams.map(primaryParam).mkString(", ")})$givenClause"
      // a class whose constructor java declared NILARY and the pipeline gave a clause: the clause is
      // the whole parameter list, and `class C(using T)` is what puts the given in scope for the
      // body, the field initialisers and the `extends` clause at once.
      // …and a NILARY constructor that is not public needs somewhere for the modifier to sit. With
      // a context clause that place already exists and the clause must NOT gain an empty group
      // before it — `()(using Ctx)` is a different signature from `(using Ctx)` and every call site
      // would have to change. Without one, `class C private[p] ()` is the only spelling there is.
      else if ctorVis.isEmpty then givenClause
      else if givenClause.nonEmpty then s" $ctorVis$givenClause"
      else if kw == "class" then s" $ctorVis()"
      else givenClause
    // Does the emitted class have a PARAMFUL primary? A synthesised primary is one even though no
    // java constructor backs it, so `plan.primaryParams` is empty for it — reading only that told
    // `orderBody` the primary was nilary, and it then discarded the class's own no-arg constructor
    // as degenerate. `AlgorithmPath()` / `Synth()` simply vanished, and `new AlgorithmPath()` was a
    // compile error at every call site while `Plans.superCall` reported that same root EXPRESSED.
    val paramfulPrimary = plan.isSynthesised || pparams.nonEmpty
    // …and did the header just built KEEP the class's context clause? Asked of the rendered text
    // and not of `plan.givens`, because a clause the plan holds and the rendering flattens into a
    // value parameter is exactly the shape CT4 measured. A `trait` reaches this with no clause on
    // purpose — `CtorFunnel.classGivens` refuses one, since scala's trait parameters are a
    // different feature and the port's `promoteToClass` is the answer — and is counted here.
    checkClause(cd, rendered = prim.contains("(using "), form = kw)
    val superTpe = cd.parents.headOption.map { case tt: TypeTree => tt.tpe; case t: Term => t.tpe }
    val parents = cd.parents.map(parent).filter(_.nonEmpty) match
      case Nil                          => Nil
      case h :: t if superArgs.nonEmpty =>
        val as = superArgs.zipWithIndex.map((a, n) => superArg(superTpe.getOrElse(TypeRepr.NoType), a, n, i))
        s"$h(${as.mkString(", ")})" :: t
      case all                          => all
    val ext     = if parents.isEmpty then "" else " extends " + parents.mkString(" with ")
    // an all-static utility class (no instance state, no supertype) is just an `object` — so its
    // static members and nested types live together and see each other by simple name.
    val hasInstanceState = cd.body.exists {
      case d: Tree.DefDef => sym(d.symbol).name != "<init>" && !sym(d.symbol).flags.isStatic
      case v: Tree.ValDef => !sym(v.symbol).flags.isStatic
      case _              => false
    }
    // an all-static class can only collapse to an `object` if nobody EXTENDS it (you can't extend
    // an object), nobody INSTANTIATES it (you can't `new` one) and nobody NAMES IT AS A TYPE (an
    // object is a value, and no value is a type) — otherwise it stays a `class` with its statics in
    // a companion object.
    if kw == "class" && parents.isEmpty && cd.body.nonEmpty && !hasInstanceState && pparams.isEmpty &&
       !extendedTypes(cd.symbol) && !instantiatedTypes(cd.symbol) && !typeNamedElsewhere(cd.symbol) then
      val members = cd.body.filterNot { case d: Tree.DefDef => sym(d.symbol).name == "<init>"; case _ => false }
      val ob0 = orderBody(members, cd.symbol, paramfulPrimary).map(memberStat(_, i + 1)).filter(_.nonEmpty).mkString("\n")
      val ob  = if bnote.isEmpty then ob0 else s"$bnote\n$ob0"
      // the VISIBILITY only — an `object` takes no `abstract`, and `final object` is redundant.
      // THE COLLAPSE, recorded where it is TAKEN. A consumer that names this type in a type
      // position is naming a value, and nothing else in any artifact says so: `members.tsv` records
      // its kind as `class` (`ENGINE-LIMITS.md` D6's cross-module face). Recorded here rather than
      // re-derived because the four whole-program reads above exist only in this branch.
      recordTypeShape(cd, "object", plan, companion = false, statics = Nil)
      // an `object` has no constructor at all, so a context clause on this class's constructors has
      // nowhere to go here — counted rather than silently dropped (CT5).
      checkClause(cd, rendered = false, form = "object")
      return s"${leading(cd.leading, i)}$cnote${ind(i)}${vis(s, privateQualifier(s.owner))}object ${esc(s.name)}$tps {\n$ob\n${ind(i)}}"
    // Java statics have no instance home in Scala — they move to the companion object.
    val (statics, instance) = if s.flags.isModule then (Nil, loweredBody) else loweredBody.partition(isStatic)
    val self    = cd.selfType.map(st => s"${ind(i + 1)}self: ${tpe(st.tpe)} =>\n").getOrElse("")
    val body1   = joinStats(orderBody(instance, cd.symbol, paramfulPrimary).map(memberStat(_, i + 1)).filter(_.nonEmpty))
    // K22 — the CLASS-INITIALISATION trigger, ahead of every other class-body statement because
    // that is where java ran it. See [[forceCompanion]]; `statics` is where BOTH halves of java's
    // class initialiser lower to — the `static { }` blocks and the static field initialisers — so
    // this is asked of the very list that carries the defect.
    // …and only where there is a CONSTRUCTOR to hang it on. A `trait` body statement runs at every
    // implementor's initialisation, which is MORE than java does (JLS 12.4.1 does not initialise an
    // interface when an implementor is initialised), and the annotation rendering below emits no
    // body at all. Neither shape can arise from java — an interface may not declare a static
    // initialiser (JLS 9.1.1) — so both are left to `class-init-trigger` rather than guessed at.
    // …and never where forcing would RE-ENTER an initialisation already in progress: java tolerates
    // a cyclic pair of class initialisers and a scala companion does not, so the trigger is
    // declined and `class-init-trigger` counts the refusal (`ENGINE-LIMITS.md` K22 face 2).
    val force   = if hasClinit(statics) && kw == "class" && !s.flags.isAnnotation &&
                     !reentrantBearers.contains(cd.symbol)
                  then forceCompanion(cd, cd.symbol, balticporter.tir.ClassInitTriggerCheck.Instantiation, i + 1)
                  else ""
    // JS-C43 — the members javac derives from a record header, which no java declaration carries.
    val (recMembers, recStatics, recNote) = recordMembers(cd, s, i)
    val body0   = joinStats(List(bnote, force, body1, recMembers.mkString("\n")).filter(_.nonEmpty))
    val diamonds = diamondOverrides(cd, i + 1)
    val body    = if diamonds.isEmpty then body0 else joinStats(List(body0).filter(_.nonEmpty) ++ diamonds)
    val open    = if body.isEmpty && self.isEmpty then "" else s" {\n$self$body\n${ind(i)}}"
    val abs     = if kw == "class" && s.flags.isAbstract then "abstract " else ""
    // Scala (unlike Java) forbids a NON-private member from referring to a bare-`private` type in
    // its signature — a public `Values extends MapIterator` / field `pool: ModelInstancePool` where
    // the referent is private is an error. Java nested classes leak this way constantly, which is
    // why this whole modifier used to be ERASED at the class header. It is not erased any more:
    // the rule is about UNQUALIFIED `private` only, and every rendering §8.7 gives a nested type is
    // QUALIFIED (`private[TopLevel]` for a java `private` one, `private[pkg]` for a package-private
    // one) — a public member may expose such a type in its signature, and a cross-package caller
    // may call it and hold the value. The erasure was therefore hiding a real level, which is what
    // the type-level half of §8.7's mapping restores. A top-level java type is never `private`, so
    // the bare form the sentence above is about cannot arise from this path at all.
    // A Java `@interface` is an ANNOTATION TYPE. Emitted as an ordinary interface it becomes a
    // trait, and then nothing can be annotated with it — 161 errors' worth of `@Null` in this
    // corpus alone. Scala's equivalent is a class extending `StaticAnnotation`.
    // The PROMOTED constructor's own Javadoc has no `def` left to sit on — `CtorFunnel` turned it
    // into the class's parameter list — so it joins the class's, which is where Scala documents a
    // primary constructor anyway. Without this it is simply dropped, and `TriviaCheck` counted it:
    // 138 Javadoc losses on libGDX core, the largest single category, most of them exactly this.
    // exactly the constructor `lowerCtors` replaces with its body, so this can never duplicate a
    // doc that is still attached to a `def this` somewhere in the class.
    val ctorLead = plan.primary.toList.flatMap(_.leading)
    // …and its NOTES go the same way, for the same reason and by §4.575's own rule. A PROMOTED
    // constructor has no `def` left for an `AtDeclaration` note to sit above, so a decision
    // subjected at it — a SAM conversion inside a constructor body, a substituted call, any kind in
    // that set — simply produced NO NOTE: `NoteCoverageCheck` reported `decision with no note` and
    // nothing else in the run could see it (measured at 1 on the libGDX base, `ENGINE-LIMITS.md`
    // I9). The class is where scala documents a primary constructor, which is where the javadoc
    // above already goes.
    val ctorNote = plan.primary.toList.map(c => declNotes(c.symbol, i)).mkString
    // JS-C44 — the keyword where java's seal survives the file split, and the note where it does
    // not. The note goes AFTER `cnote` for §4.575's order: the upstream's own trivia first, the
    // port's note last, the member next.
    val (seal, sealNote) = sealOf(cd, s, i)
    val cls     =
      if s.flags.isAnnotation then
        s"${leading(cd.leading, i)}$cnote${annots(s, i)}${ind(i)}class ${esc(s.name)}$tps$prim extends scala.annotation.StaticAnnotation"
      else s"${leading(cd.leading ++ ctorLead, i)}$cnote$ctorNote$sealNote$recNote${annots(s, i)}${ind(i)}${mods(s, privateQualifier(s.owner))}$seal$abs$kw ${esc(s.name)}$tps$prim$ext$open"
    // Java interface/parent CONSTANTS are `static`, so they live in the parent's companion object
    // — which Scala does NOT inherit. Re-export each static-bearing parent's companion so an
    // inherited constant accessed via a subclass (`GL30.GL_LUMINANCE`, declared in `GL20`) resolves.
    // exclude the class's OWN static names from the re-export (a subtype may redeclare a parent
    // constant — OpenGL's GL31 vs GL30 — which would otherwise be a duplicate/conflicting export).
    //
    // A STATIC INITIALIZER BLOCK is not one of those names. Java calls it `<clinit>` — the JVM's
    // name for the synthetic method it compiles a `static { … }` block into — and no Scala
    // identifier can spell it, backticks included: there is no member at that name to hide, so an
    // exclusion naming it is not merely useless, it is `export P.{<clinit> => _, *}`, which the
    // parser reads as an XML start tag. The block has no name in the emitted Scala either
    // ([[isInitBlock]] lowers it into the companion's body), so it can never collide with an
    // inherited constant and has nothing to exclude.
    //
    // Invisible for six ports because it needs BOTH halves at once — a class that inherits statics
    // from a parent AND declares a `static { }` block of its own. libGDX core has 605 types and not
    // one of them; gdx-gltf's attribute hierarchy has three (`PBRColorAttribute`,
    // `PBRCubemapAttribute`, `PBRTextureAttribute`, each `extends` a libGDX `Attribute` subclass
    // whose constants it re-exports, each registering its own aliases in a `static { }`).
    val ownStaticNames = statics.collect {
      case d: Definition if !d.isInstanceOf[Tree.DefDef] || !isInitBlock(d.asInstanceOf[Tree.DefDef]) =>
        esc(sym(d.symbol).name)
    }.distinct
    // Two exports must not both deliver the same name. `GL20Interceptor extends GLInterceptor with
    // GL20` and `GLInterceptor` itself implements `GL20`, so `GLInterceptor`'s companion ALREADY
    // re-exports `GL20`'s constants by this rule — a second `export GL20.*` is a duplicate
    // definition, not extra reach. Drop a parent another exported parent wholly subsumes, and for
    // the DIAMOND that remains (`GLInterceptor` and `GL30` meeting at `GL20`) exclude, from each
    // later export, every name an earlier one already delivered FROM THE SAME DECLARING TYPE. The
    // same-owner test is what keeps this safe: a genuine redeclaration (`GL31` shadowing a `GL30`
    // constant) has a different owner, so it is never silently merged away.
    val exported       = parentSymsOf(cd).filter(p => staticsReachable(p))
    val kept           = exported.filterNot(p => exported.exists(q => q != p && ancestorsOf(q).contains(p)))
    val delivered      = kept.map(staticOwnersOf(_))
    val extraExcl      = Array.fill(kept.size)(Set.empty[String])
    delivered.flatMap(_.keys).distinct.foreach { n =>
      val at = delivered.indices.filter(j => delivered(j).contains(n)).toList
      if at.sizeIs > 1 then
        val owners = at.map(delivered(_)(n)).distinct
        // Same owner everywhere ⇒ the SAME constant arriving twice; keep the first export and drop
        // the rest. Different owners ⇒ a real redeclaration, and the one Java resolves to is the
        // most specific — the owner that descends from all the others. Incomparable owners are
        // ambiguous in Java too, so keep the first and let the redeclaration be the loser rather
        // than guess.
        val winner = at.find(j => owners.forall(o => o == delivered(j)(n) || ancestorsOf(delivered(j)(n)).contains(o)))
          .getOrElse(at.head)
        at.filter(_ != winner).foreach(j => extraExcl(j) = extraExcl(j) + n)
    }
    val parentExports  = kept.zipWithIndex.map { (p, j) =>
      val excluded = (ownStaticNames.toSet ++ extraExcl(j) ++ nonPublicStatics(delivered(j))).toList.sorted
      val sel      = if excluded.isEmpty then "*" else s"{${excluded.map(_ + " => _").mkString(", ")}, *}"
      s"${ind(i + 1)}export ${typeValue(p)}.$sel"
    }
    // the disambiguator's marker type, minted in THIS class's companion — one line, and the reason
    // it is here rather than in `runtime/` is that emitted code then carries no dependency on the
    // engine's runtime artifact for a purely local encoding (`DESIGN.md` §8.2). A class that needs
    // one may have no companion at all, so the companion is emitted for it.
    val markerDecl = plan.marker.toList.map(n => s"${ind(i + 1)}protected final class ${esc(n)}")
    // …and the record's EXTRACTOR, which is the one member of JS-C43's synthesis with no home in
    // the class. A record with no statics has no companion at all, so it is emitted for it — the
    // marker declaration above is the same shape and the same reason.
    val hasCompanion = !(statics.isEmpty && parentExports.isEmpty && markerDecl.isEmpty && recStatics.isEmpty)
    // …and the OTHER three forms, recorded from the values that decided them. `companion` and
    // `statics` are the two an `export Base.*` in a dependent has to read rather than recompute from
    // the base's Java: a base with no companion makes the export an error outright, and a static
    // the base renamed or moved is named wrongly by any recomputation.
    recordTypeShape(cd,
      form      = if s.flags.isAnnotation then "annotation" else kw,
      plan      = plan,
      companion = hasCompanion,
      statics   = ownStaticNames)
    if !hasCompanion then cls
    else
      // K22's SECOND trigger — JLS 12.4.1 item 7. Initialising a class initialises its SUPERCLASS
      // first, and what initialises a class with nothing instantiating it is a bare `S.member`
      // read; in Scala that touches `object S` and reaches no other object, so an ancestor's
      // `static { }` never runs on that path. The force goes FIRST in the companion body, because
      // java ran the ancestor's initialiser before this type's own static field initialisers.
      //
      // The companion is the whole condition — an object that is never initialised runs nothing, so
      // a line inside one can never over-trigger relative to java, whatever put the object there.
      // That is why this asks `hasCompanion` rather than re-deriving "does anything read a static
      // of this type", which is the string-shaped guess §4.56 is about.
      val superForce = nearestClinitAncestor(cd.symbol)
        .filterNot(reentrantBearers.contains)
        .map(a => forceCompanion(cd, a, balticporter.tir.ClassInitTriggerCheck.SubclassInit, i + 1))
        .toList.filter(_.nonEmpty)
      val sb = (superForce ++ parentExports ++ markerDecl ++
                orderBody(statics, cd.symbol).map(memberStat(_, i + 1)).filter(_.nonEmpty) ++
                recStatics).mkString("\n")
      s"$cls\n${ind(i)}object ${esc(s.name)} {\n$sb\n${ind(i)}}"

  /** `this.x = x` — the NAME assigned, when the assignment is a field taking its own same-named
    * source and nothing else. Both sides must resolve to the same simple name and the right-hand
    * side must be a bare identifier, so `this.up = new Vector3(upX, …)` and `this.a = b` are not
    * this shape and are not touched. */
  private def selfAssignedParam(a: Tree.Assign): Option[String] =
    val lhs = a.lhs match
      case Tree.Select(_: Tree.This, m, _, _) => Some(sym(m).name)
      case Tree.Ident(m, _, _)                => Some(sym(m).name)
      case _                                  => scala.None
    val rhs = a.rhs match
      case Tree.Ident(m, _, _) => Some(sym(m).name)
      case _                   => scala.None
    lhs.filter(l => rhs.contains(l))

  /** Java enum → `sealed abstract class Name <parents-minus-Enum> { members }` plus a
    * companion `object` holding each constant as a `case object` and a `values` array. */
  private def enumDef(cd: Tree.ClassDef, i: Int): String =
    val s       = sym(cd.symbol)
    val name    = esc(s.name)
    val parents = cd.parents.map(parent).filter(p => p.nonEmpty && !p.startsWith("java.lang.Enum"))
    val ext     = if parents.isEmpty then "" else " extends " + parents.mkString(" with ")
    val (statics, instance0) = cd.body.partition(isStatic)
    // A Java enum constructor's PARAMS become the sealed class's primary constructor params (as `var`
    // fields), so `case object Nearest extends TextureFilter(GL_NEAREST)` has somewhere to pass its
    // arg. Drop the constructor itself and any field that a param supersedes (same name).
    val ctors      = instance0.collect { case d: Tree.DefDef if sym(d.symbol).name == "<init>" => d }
    // JAVA's parameters, never `paramss.flatten` (`CtorFunnel.valueParams`, and the same rule the
    // funnel applies one level up). A context clause a phase put on this constructor is not a java
    // parameter and cannot become a `var` field: the parameter is ANONYMOUS, so it would render as
    // `var : sge.Sge`, and an enum's primary is reached by every `case object` — each of which
    // would have to pass an argument for a clause the emitter has no way to supply. So it is
    // dropped from the parameter list and COUNTED as a lost clause instead (`ENGINE-LIMITS.md`
    // CT5); an enum whose body needs an ambient context is a port-level decision, not a rendering.
    val ctorParams = ctors.headOption.map(CtorFunnel.valueParams(program, _)).getOrElse(Nil)
    checkClause(cd, rendered = false, form = "enum")
    val paramNames = ctorParams.map(v => sym(v.symbol).name).toSet
    val instance   = instance0.filterNot {
      case d: Tree.DefDef => sym(d.symbol).name == "<init>"
      case v: Tree.ValDef => paramNames(sym(v.symbol).name)
      case _              => false
    }
    // …and it also has a BODY, which RUNS. Keeping only the parameters left every field the
    // constructor assigned at its declared default, silently: libGDX's `Cubemap.CubemapSide` builds
    // `up` and `direction` from six float parameters, so all six sides shipped with `up == null`
    // and `getUp(out)` threw — in a port that compiled with zero errors and moved no check count
    // (CLAUDE.md §3). Found by porting anim8, whose `Dithered.DitherAlgorithm` assigns
    // `legibleName` the same way, so `toString()` returned null for all 22 constants.
    //
    // `CtorFunnel` is deliberately NOT consulted. It plans a class whose constructors it may
    // promote, delegate or synthesise, and an enum's shape is already fixed: the sealed class's
    // primary IS the java constructor, because every `case object` passes its arguments to it. (It
    // also plans nothing here — `Plan.primaryParams` came back empty and the parameter list
    // vanished, which is how that was measured.) So the lowering is the direct one, and it applies
    // only to a SINGLE constructor: an OVERLOADED enum constructor cannot be expressed by this
    // shape at all, since a `case object` can reach only one primary. That is a pre-existing limit
    // this does not widen, and attributing one overload's body to every constant would be worse
    // than leaving it out.
    val ctorStats =
      if ctors.sizeIs != 1 then Nil
      else CtorFunnel.stmtsOf(ctors.head).filterNot {
        // java's implicit `super()`, which reaches `java.lang.Enum` and has no expression here.
        case Tree.Apply(Tree.Select(_, m, _, _), _, _, _, _) => sym(m).name == "<init>"
        // `this.glEnum = glEnum` is what most enum constructors ARE, and the promotion above
        // already performs it — `var glEnum` IS the parameter. Re-emitting it is a self-assignment:
        // correct, and pure churn in four of libGDX's five enums. Dropped only in that exact shape,
        // so an assignment that computes anything (`this.up = new Vector3(upX, upY, upZ)`) stays.
        case a: Tree.Assign                                  => selfAssignedParam(a).exists(paramNames)
        case _                                               => false
      }
    val eprimary = if ctorParams.isEmpty then "" else s"(${ctorParams.map(v => s"var ${esc(sym(v.symbol).name)}: ${tpe(v.tpt.tpe)}").mkString(", ")})"
    // Java's final `Enum.name()` — a `case object`'s `toString` IS its declared name (= the Java
    // constant name), so `name()` returns it. Skip if the enum already declares a `name` member.
    //
    // A PROMOTED CONSTRUCTOR PARAMETER is one, and reading only the body missed it — CLAUDE.md
    // §4.55's "count what the constructor funnel PROMOTES: the chosen constructor's parameters".
    // The promotion above renders every parameter as a `var`, so an enum whose constructor takes a
    // `String name` (anim8's `Dithered.DitherAlgorithm`, which uses it to set `legibleName`) got
    // both `var name` and `def name()` and did not compile: E120 "Conflicting definitions", one
    // error, and the only one this port had left. Java never has to choose, because `Enum.name()`
    // is FINAL there and a parameter is not a member at all.
    val hasName = paramNames("name") ||
      instance.exists { case d: Definition => sym(d.symbol).name == "name"; case _ => false }
    val nameM   = if hasName then Nil else List(s"${ind(i + 1)}def name(): java.lang.String = this.toString()")
    // Java's final `Enum.ordinal()` — the constant's DECLARATION INDEX, and part of every java
    // enum's surface whether the enum mentions it or not. A library reaches for it wherever the
    // constants stand for consecutive integers somewhere else: gdx-vfx passes
    // `lineStyle.ordinal()` straight into a shader `#define`, which is what a comment on the enum
    // says the ordinals are FOR. Absent, that is `value ordinal is not a member of …` — and unlike
    // `name()` there is no plausible substitute a reader would reach for.
    //
    // Emitted as an ABSTRACT member with one override per constant, which is java's own O(1)
    // field read; deriving it from `values().indexOf(this)` would be one line instead of n+1 and
    // would allocate an array on every call. Skipped whole — base and constants together — when
    // the enum declares its own `ordinal`, for the reason `hasName` records: java's two namespaces
    // let a FIELD or a promoted constructor PARAMETER carry the name beside the final method,
    // and scala's one namespace cannot (CLAUDE.md §4.55).
    val hasOrdinal = paramNames("ordinal") ||
      instance.exists { case d: Definition => sym(d.symbol).name == "ordinal"; case _ => false }
    val ordinalM = if hasOrdinal then Nil else List(s"${ind(i + 1)}def ordinal(): scala.Int")
    val cnote   = if cd.symbol == currentTopLevelSym then "" else declNotes(cd.symbol, i)
    val bnote   = bodyNotes(cd.symbol, i + 1)
    // The constructor's statements go LAST among the class body's own, after every declaration:
    // a Scala class body runs its statements in textual order, so an assignment placed above the
    // `var` it targets would not compile, and one placed below runs exactly where java ran it.
    val members = List(bnote).filter(_.nonEmpty) ++
      orderBody(instance, cd.symbol).map(memberStat(_, i + 1)).filter(_.nonEmpty) ++
      ctorStats.map(memberStat(_, i + 1)).filter(_.nonEmpty) ++ nameM ++ ordinalM
    val cbody   = members.mkString("\n")
    // §8.7 governs an enum TYPE like any other. Its CONSTRUCTOR is a different matter and is
    // deliberately left public: java makes it implicitly `private` (JLS 8.9.2), but this lowering
    // has already dissolved it — the parameters ARE the sealed class's primary and every
    // `case object` in the companion passes its arguments to that primary, so there is no
    // constructor declaration left to carry a modifier.
    val cls     = s"${leading(cd.leading, i)}$cnote${ind(i)}${vis(s, privateQualifier(s.owner))}sealed abstract class $name$eprimary$ext" + (if cbody.isEmpty then "" else s" {\n$cbody\n${ind(i)}}")
    val cases = cd.enumCases.zipWithIndex.map { (ec, idx) =>
      val cn   = esc(sym(ec.symbol).name)
      val args = if ec.ctorArgs.isEmpty then "" else s"(${ec.ctorArgs.map(term(_, i + 1)).mkString(", ")})"
      // the constant's own members first, then the `ordinal()` this lowering owes the base.
      val stats = ec.body.map(stat(_, i + 2)) ++
        (if hasOrdinal then Nil else List(s"${ind(i + 2)}override def ordinal(): scala.Int = $idx"))
      val body = if stats.isEmpty then "" else s" {\n${stats.mkString("\n")}\n${ind(i + 1)}}"
      s"${leading(ec.leading, i + 1)}${ind(i + 1)}case object $cn extends $name$args$body"
    }
    // `def` (not `val`) so Java's `E.values()` call site type-checks; also a no-paren read works.
    val values = s"${ind(i + 1)}def values(): scala.Array[$name] = scala.Array(${cd.enumCases.map(ec => esc(sym(ec.symbol).name)).mkString(", ")})"
    // Java's `Enum.valueOf(String)` — resolve a constant by name (throws like the JDK on no match).
    val vArms  = cd.enumCases.map(ec => esc(sym(ec.symbol).name)).map(n => s"""${ind(i + 2)}case "$n" => $n""").mkString("\n")
    val valueOf = s"${ind(i + 1)}def valueOf(name: java.lang.String): $name = name match {\n$vArms\n${ind(i + 2)}case _ => throw new java.lang.IllegalArgumentException(name)\n${ind(i + 1)}}"
    val objBody = (cases :+ values :+ valueOf) ++ statics.map(memberStat(_, i + 1)).filter(_.nonEmpty)
    // A java enum is emitted as a SEALED ABSTRACT CLASS plus a companion holding one `case object`
    // per constant, and neither `class` nor `object` describes that: a dependent naming it needs to
    // know both that the type exists and that its constants are values in the companion. The plan is
    // deliberately `Plan.none` — `CtorFunnel` is not consulted for an enum (see above), so the
    // primary IS the java constructor and its slots are `ctorParams`.
    recordedTypeShapes(s.fullName) = Surface.TypeShape(
      form        = "enum-class",
      companion   = true,
      statics     = statics.collect { case d: Definition => esc(sym(d.symbol).name) }.distinct,
      primary     = Some(Descriptor(ctorParams.map(v => descriptorParam(v.tpt.tpe)))),
      primaryKind = "not-funnelled",
      primaryVis  = "public",
      parents     = parentSymsOf(cd).map(p => sym(p).fullName),
      flags       = List("sealed", "abstract"),
    )
    s"$cls\n${ind(i)}object $name {\n${objBody.mkString("\n")}\n${ind(i)}}"

  // a Java `static` nested class has no instance home in Scala → it moves to the companion
  // `object` alongside static vals/defs. A non-static inner class stays in the class body.
  /** Replace the constructor `CtorFunnel` promoted to Scala's PRIMARY by its own body statements
    * — they run at construction, which is where a Scala class body runs them too. Its `super(args)`
    * has already been lifted into the `extends` clause and its parameters into the class's
    * parameter list; every other constructor stays a secondary `def this(...)`. */
  private def lowerCtors(body: List[Statement], plan: CtorFunnel.Plan): List[Statement] =
    plan.primary match
      case None    => body
      case Some(c) => body.flatMap { case d: Tree.DefDef if d.symbol == c.symbol => plan.primaryBody; case s => List(s) }

  /** a Java `static { … }` / instance `{ … }` initializer block, carried as a synthetic member. */
  private def isInitBlock(d: Tree.DefDef): Boolean =
    val n = sym(d.symbol).name
    n == "<clinit>" || n == "<initblock>"

  /** does this member list carry java CLASS INITIALISER content — JLS 12.4.2 step 9, never the
    * instance initialiser, which runs at construction in both languages?
    *
    * `ClassInitTriggerCheck.stepNine`'s and not a local test, which is the whole point: the repair
    * and its watchdog have to answer this from ONE definition or the check is silent exactly where
    * the emitter is wrong. Keyed on the block alone it missed the registration written as a static
    * FIELD — the same construct, java's `<clinit>` runs both in one sequence — while the constant
    * variable stays out of it, because javac inlines that one and so does the `inline val` arm
    * below (`JS-C08`). */
  private def hasClinit(members: List[Statement]): Boolean =
    balticporter.tir.ClassInitTriggerCheck.stepNine(members)(using program)

  // ---------------------------------------------------------------------------
  // K22 — A JAVA CLASS INITIALISER RUNS AT CLASS INITIALISATION; THE `object` IT LANDS IN IS
  // INITIALISED BY NOTHING.
  //
  // Java's trigger list (JLS 12.4.1) is short and exact — a class `T` is initialised on the first
  // `new T`, the first access to a static `T` DECLARES (a compile-time constant excepted, §4.4's
  // `inline val` row and JS-C08), the initialisation of one of `T`'s subclasses, certain reflective
  // actions, and `main`. Scala has no such list: a companion `object` initialises when something
  // touches the OBJECT, and `new T(…)` touches only the class.
  //
  // So the class initialiser is emitted, faithfully, into the companion and NEVER RUNS. Where its
  // effect is a REGISTRATION — an SPI provider, a factory, a codec, a pool — every later lookup
  // answers "not registered", which a library turns into a plausible WRONG ANSWER rather than an
  // error. Measured on one library as 5 test failures at 0 compile errors with every check count
  // flat, and invisible to every instrument the project has: the code IS emitted, so no omission
  // exists to count (`ENGINE-LIMITS.md` K22).
  //
  // AND "THE CLASS INITIALISER" IS JLS 12.4.2 STEP 9, NOT A NODE KIND. Step 9 runs the static FIELD
  // INITIALISERS and the `static { }` BLOCKS as one sequence in textual order, so
  // `static { Registry.register(…); }` and `static final boolean R = Registry.register(…)` are one
  // construct written two ways and java initialises `T` at `new T` for either. Keyed on the block
  // alone this repair answered for one of them and left the other silent, with the watchdog reading
  // 0 on trees that had the defect. `ClassInitTriggerCheck.stepNine` is the one predicate both ask,
  // and the CONSTANT VARIABLE stays outside it for JS-C08's reason: javac inlines it, the arm below
  // emits `inline val`, and a trigger there would be a trigger java never had — which is also the
  // whole of why this cannot re-enter §4.4's `Vector3`/`Matrix4` cycle.
  //
  // THE REPAIR IS JAVA'S OWN TRIGGER LIST, NEVER "call it from every use". The two are not the same
  // set and the difference is the whole of JS-C08: java's INLINING means reading a constant
  // triggers nothing at all, so a port that forced the object at each use would run the block on
  // paths java never did — and the initialisation CYCLE §4.4 records for `Vector3`/`Matrix4` is
  // exactly what such a path re-enters. Only the triggers java has are reproduced:
  //
  //   - INSTANTIATION, here — a statement at the head of the class body, ahead of every field
  //     initialiser, which is where java ran the class initialiser relative to them;
  //   - a STATIC ACCESS needs nothing: `T.member` is already an access to the object;
  //   - SUBCLASS INITIALISATION — `new S` reaches this same statement through `S`'s super
  //     constructor, and `S.<own static>` is answered in the companion (see the sibling call site).
  //
  // …and REFLECTION is the one trigger no emitted Scala can carry: `Class.forName("T", true, cl)`
  // initialises the java class, and a reflective load of the emitted `T` does not touch `T$`. That
  // residue is stated in `ENGINE-LIMITS.md` K22 rather than counted, because nothing in the program
  // can see a reflective load that lives in its CONSUMER.
  //
  // WHAT IS APPROXIMATE, and deliberately not counted: java initialises the class before `<init>`
  // runs at all — including before the SUPERCLASS constructor — while a class-body statement runs
  // after it. The case that SOUNDS like the problem is the one that largely SELF-HEALS: a super
  // constructor calling a method this class overrides which reads this class's statics is a read
  // that IS an access to the companion, so it initialises it on the spot and the only difference
  // left is that the object was built a few statements later than java built it, with nothing in
  // between able to observe that. What does NOT heal is mutual ordering through a THIRD PARTY —
  // the super constructor asks a registry what is registered and THIS class's initialiser is what
  // registers it, so java answers "yes" and the port answers "no", and no read on either path
  // touches the object early enough to fix it. No criterion for that is cheaper than the
  // whole-program analysis it would take; an over-approximate review list here would be noise
  // (`CLAUDE.md` §1).
  //
  // `val _ = <path>` and not a bare reference: both compile to the same `getstatic MODULE$` and
  // both force, but a bare one is `E176 unused value` under `-Wall` — and the consumer is an agent
  // in ANOTHER repository (§4.45) whose build settings this engine does not choose. The path is
  // FULLY QUALIFIED for §4.56's reason rather than §6's: java lets `class Foo { int Foo; }`, so the
  // simple name inside the body can resolve to a MEMBER, and the force would then read a field and
  // initialise nothing, silently.
  // ---------------------------------------------------------------------------

  /** The note and the statement that force `target`'s companion, recorded as one [[Decision]] about
    * `cd` so the note is DERIVED rather than authored (§4.575) and `NoteCoverageCheck` sees the
    * pair.
    *
    * `target` is `cd` itself for the instantiation trigger and an ANCESTOR for the subclass one —
    * java's item 7 initialises the superclass, not this class, and a note that named this class
    * there would answer the reader's question with the wrong type.
    *
    * @param trigger which of JLS 12.4.1's actions this statement stands for — the reader's real
    *                question is whether THEIR path is covered, and the list is short enough to say.
    */
  private def forceCompanion(cd: Tree.ClassDef, target: SymId, trigger: String, i: Int): String =
    val s  = sym(cd.symbol)
    val tg = sym(target)
    val why =
      if target == cd.symbol then
        "java runs this class's initialiser — its `static { }` blocks and its static field " +
          "initialisers, one sequence, JLS 12.4.2 step 9 — at class initialisation, and a scala " +
          "companion initialises on first access to the OBJECT, which `new` is not"
      else
        s"initialising this type initialises `${tg.fullName}` first (JLS 12.4.1 item 7), which runs " +
          "that type's class initialiser; a scala object's initialisation reaches no other object"
    val d = Decision(
      kind       = Decision.Kind.ForcedClassInit,
      subject    = cd.symbol,
      subjectFqn = s.fullName,
      detail     = Map("trigger" -> trigger, "forces" -> tg.fullName, "why" -> why),
      reason     = Reason.Universal("class-init-trigger(§4.4)"),
      origin     = cd.origin,
    )
    emissionOf += d
    printedNotes += PorterNote.Printed(d.kind, d.subject, d.subjectFqn, currentUnitName)
    forcedClinits += (cd.symbol -> trigger)
    s"${PorterNote.render(d, ind(i))}${ind(i)}val _ = ${escPath(tg.fullName).replace('$', '.')}"

  /** every type this program declares whose class initialiser does anything — [[hasClinit]]'s
    * question (JLS 12.4.2 step 9), asked of the DECLARED body. */
  private lazy val clinitBearers: Map[SymId, Tree.ClassDef] =
    val acc = collection.mutable.Map[SymId, Tree.ClassDef]()
    allDeclaredClasses.foreach(cd => if hasClinit(cd.body) then acc(cd.symbol) = cd)
    acc.toMap

  /** …and the ones whose force would be RE-ENTRANT, which the repair declines and
    * `class-init-trigger` counts. The check's own function, so the refusal and the count cannot
    * disagree about which types it names (`ENGINE-LIMITS.md` K22 face 2). */
  private lazy val reentrantBearers: Map[SymId, SymId] =
    balticporter.tir.ClassInitTriggerCheck.reentrantBearers(program, clinitBearers)

  /** the ancestor edges JLS 12.4.1 item 7 traverses — the SUPERCLASS chain plus a superinterface
    * declaring a default method, and never `parentsBySym`, whose edges are every parent there is. */
  private lazy val item7ParentsBySym: Map[SymId, List[SymId]] =
    balticporter.tir.ClassInitTriggerCheck.item7Parents(program)

  /** the nearest ancestor of `s` carrying a class initialiser — the ONE this type's companion has
    * to force. Java initialises the whole superclass chain, and forcing only the nearest reproduces
    * that because THAT type's companion carries the same line for ITS own nearest, recursively.
    * Breadth-first, so "nearest" means nearest and not "first found down one branch". */
  private def nearestClinitAncestor(s: SymId): Option[SymId] =
    def go(front: List[SymId], seen: Set[SymId]): Option[SymId] =
      val next = front.flatMap(item7ParentsBySym.getOrElse(_, Nil)).filterNot(seen).distinct
      next.find(clinitBearers.contains) match
        case Some(a)         => Some(a)
        case _ if next.isEmpty => scala.None
        case _               => go(next, seen ++ next)
    go(List(s), Set(s))

  /** every (type, trigger) pair this emitter forced — the input to `class-init-trigger`, which
    * takes the CENSUS of `static { }` blocks from the trees itself. An empty set therefore
    * reproduces the un-repaired engine on the same trees, exactly as `switch-null` does. Keyed by
    * the TRIGGER as well as the type because the two are answered at different call sites and a
    * type covered for one is not covered for the other. */
  private val forcedClinits = collection.mutable.Set.empty[(SymId, String)]
  def forcedClassInits: Set[(SymId, String)] = forcedClinits.toSet

  private def isStatic(s: Statement): Boolean = s match
    case d: Tree.ClassDef => sym(d.symbol).flags.isStatic
    case d: Definition    => sym(d.symbol).flags.isStatic
    case _                => false

  /** Scala secondary constructors must delegate to a PRECEDING constructor, so order fields first,
    * then constructors in DELEGATION-TOPOLOGICAL order (each ctor's `this(args)` target emitted
    * before it), then everything else. Arity is not a reliable proxy — a 3-arg convenience ctor can
    * delegate to a 1-arg one (`Texture(pixmap,fmt,mip)` → `Texture(data)`), so we follow the actual
    * `this(...)` edges, keyed by the target ctor's own symbol.
    *
    * `owner` is the class whose body this is, and it decides WHICH `ValDef`s the hoist applies to —
    * `ENGINE-LIMITS.md` C12. Two kinds of `ValDef` reach this list and they are the same node kind:
    *
    *  - the class's own FIELDS, which java runs in step 4 of JLS 12.5 — in textual order, before
    *    any constructor body statement. Hoisting them puts every one ahead of the promoted body,
    *    which is where java runs them, and a field declared BELOW the constructor needs the hoist
    *    to compile at all;
    *
    *    **…but step 4 is not only fields, and "whatever order the java file declared them in" was
    *    an overclaim.** JLS 12.5 step 4 runs field initialisers and INSTANCE INITIALISER BLOCKS as
    *    ONE sequence, in textual order (12.4.2 step 9 says the same of the static pair). A block is
    *    carried as a synthetic `<initblock>`/`<clinit>` member — a `Tree.DefDef`, not a `ValDef` —
    *    so it fell into `rest`, behind every field: `{ b = 2; } int b = 5;` left `b == 2` where
    *    java leaves 5, because the assignment java ran FIRST ran LAST. Same evidence as C12 — valid
    *    Scala, no compile error, no check count, only a run can see it — which is why the hoisted
    *    group is "step-4 members" and their RELATIVE ORDER is java's, rather than "the `ValDef`s";
    *  - a PROMOTED CONSTRUCTOR LOCAL, spliced in by [[lowerCtors]] as part of `plan.primaryBody`.
    *    That declaration is a step-5 constructor BODY statement: java ran it exactly where it stood,
    *    among the constructor's other statements, and the interleaving is what carries every
    *    dependency between them. Hoisted, it initialises itself before the statements java ran
    *    first — measured on liqp's `Template` as 409 of 414 test failures, all `NullPointerException`
    *    on a field the statement above the local assigns, at **0 scalac errors with every check
    *    count flat**.
    *
    * The two are told apart by OWNERSHIP and by nothing else (`CLAUDE.md` §4.56 — never by name,
    * never by origin line, which a real field and a promoted local can share only by accident).
    * The frontend interns a field under the CLASS and a local under the enclosing EXECUTABLE
    * (`SpoonTir.defineLocal` sets `owner = methodId`), so "is this `ValDef` a member of `owner`?"
    * is a symbol lookup. It also generalises past the funnel: any route that splices a
    * constructor's own declarations into a class body produces symbols owned by that constructor,
    * so no caller has to opt in.
    *
    * A promoted local therefore stays in `rest`, in place — the SIMPLEST faithful shape, and the
    * one that needs no `uninitialized`/assign split, because java's definite-assignment rules make
    * a forward reference from an earlier statement to a later local impossible in the first place.
    * A `val` is legal anywhere in a scala class body, so nothing about its position needs
    * repairing; only the hoist did. */
  private def orderBody(body: List[Statement], owner: SymId, paramfulPrimary: Boolean = false): List[Statement] =
    def isCtor(s: Statement) = s match { case d: Tree.DefDef => sym(d.symbol).name == "<init>"; case _ => false }
    // the peer ctor this one delegates to via a leading `this(args)` (NOT super, NOT the no-arg
    // primary) — its symbol identifies the exact target constructor.
    // matched THROUGH a comment wrapper (CtorFunnel.headStmt says why): a `// delegate` above the
    // `this(args)` must not turn a delegating constructor into a non-delegating one.
    def delegateTarget(d: Tree.DefDef): Option[SymId] = CtorFunnel.headStmt(d) match
      case Some(Tree.Apply(Tree.Select(r, m, _, _), args, _, _, _))
          if sym(m).name == "<init>" && args.nonEmpty && !r.isInstanceOf[Tree.Super] => Some(m)
      case _ => None
    // a no-arg constructor whose body is only super/this delegation is degenerate — Scala's
    // implicit primary constructor already is no-arg, and `def this() = this()` self-recurses.
    // Only when the primary IS no-arg: against a PARAMFUL primary a `C() { this(16); }` — or a
    // `C() { super(0, false); }` in front of a SYNTHESISED primary — is the only thing that makes
    // `new C()` legal at all, so it must be emitted. `paramfulPrimary` therefore has to be read off
    // the emitted class, not off `Plan.primaryParams`, which a synthesised primary leaves empty.
    // …and NILARY is a question about what JAVA declared, never `paramss.flatten` — the same
    // distinction `CtorFunnel.valueParams` exists for one level up. A `C()` that gained a `(using
    // T)` clause (`DESIGN.md` §8.4) stopped being degenerate here and was emitted as
    // `def this()(using T)` beside a primary carrying the same clause: `E120` at the declaration
    // ("the same type after erasure"), and an `E051` ambiguous overload at every argument-free
    // `extends` and every `new C()`. That is CT4's third cause reappearing on the `Plan.none` side,
    // and reading value parameters restores exactly the answer this class gets with no clause at
    // all — the degenerate secondary dropped (`ENGINE-LIMITS.md` CT5).
    // …and DEGENERATE is only half of what this predicate drops. A nilary constructor whose
    // delegation CARRIES ARGUMENTS is not degenerate — java ran that delegation and scala's implicit
    // nilary primary does not — and it is dropped all the same, because there is nowhere to put it:
    // `def this()` beside a nilary primary is `E120`. That half is `CtorFunnel.Plans.droppedNilaryCtor`
    // and `OmissionCheck.droppedNilaryCtors` counts it. ONE predicate for both, so the emission and
    // the count cannot disagree about which constructors vanish.
    def dropped(d: Tree.DefDef): Boolean = !paramfulPrimary && CtorFunnel.delegationOnlyNilary(program, d).isDefined
    val ctorList = body.collect { case d: Tree.DefDef if isCtor(d) && !dropped(d) => d }
    val bySym    = ctorList.map(d => d.symbol -> d).toMap
    // DFS post-order = topological order (a target is appended before its caller); `inProgress`
    // breaks any (illegal) cycle so a malformed chain can't loop forever.
    val ordered    = collection.mutable.ListBuffer[Tree.DefDef]()
    val visited    = collection.mutable.Set[SymId]()
    val inProgress = collection.mutable.Set[SymId]()
    def visit(d: Tree.DefDef): Unit =
      if !visited(d.symbol) && !inProgress(d.symbol) then
        inProgress += d.symbol
        delegateTarget(d).flatMap(bySym.get).foreach(visit)
        inProgress -= d.symbol
        visited += d.symbol
        ordered += d
    ctorList.foreach(visit)
    // C12: a FIELD of `owner` — not merely a `ValDef`. See the doc above for why the difference is
    // ownership and for what hoisting the other kind costs.
    def isField(s: Statement) = s match { case v: Tree.ValDef => sym(v.symbol).owner == owner; case _ => false }
    // …and the OTHER kind of step-4 member: an instance (or static) INITIALISER BLOCK. JLS 12.5
    // step 4 runs field initialisers and instance initialisers as ONE sequence in TEXTUAL ORDER
    // (12.4.2 step 9 says the same of the static pair), so a block belongs in the hoisted group and
    // KEEPS ITS PLACE inside it — see the doc above.
    def isStep4(s: Statement) = s match
      case d: Tree.DefDef => isInitBlock(d)
      case _              => isField(s)
    val step4 = body.filter(isStep4)
    val rest  = body.filterNot(s => isCtor(s) || isStep4(s))
    step4 ++ ordered.toList ++ rest

  private def typeParam(td: Tree.TypeDef): String =
    val name = esc(sym(td.symbol).name)
    td.rhs.tpe match
      case TypeRepr.TypeBounds(TypeRepr.NoType, TypeRepr.NoType) => name
      case TypeRepr.TypeBounds(lo, hi) =>
        val l = if lo == TypeRepr.NoType then "" else s" >: ${tpe(lo)}"
        val h = if hi == TypeRepr.NoType then "" else s" <: ${tpe(hi)}"
        s"$name$l$h"
      case other => s"$name <: ${tpe(other)}"

  /** The parent's promoted-constructor formal at position `n`, with the parent's OWN type
    * parameters replaced by whatever the `extends` clause supplies for them — `MapIterator<V>`'s
    * `IntMap<V>` seen from `Entries[V] extends MapIterator[V]` is `IntMap[V]`, `V` now being
    * `Entries`'. `None` when the parent is external, has no promoted constructor, or is applied at
    * a different arity than it declares. */
  private def superFormal(parent: TypeRepr, n: Int): Option[TypeRepr] =
    val actuals = parent match
      case TypeRepr.AppliedType(_, as) => as
      case _                           => Nil
    for
      tycon <- headSymOf(parent)
      pcd   <- program.definitionOf(tycon).collect { case c: Tree.ClassDef => c }
      if pcd.tparams.sizeIs == actuals.size
      p     <- plans(pcd).primaryParams.lift(n)
    // The map is built HERE rather than by `ParentSubst.of` because the question is about ONE named
    // parent applied at a checked arity, not about everything above this class.
    yield substTp(p.tpt.tpe, pcd.tparams.map(_.symbol).zip(actuals).toMap)

  /** An argument lifted into the `extends` clause.
    *
    * The argument kept the wildcard fill its DECLARATION was rendered with (`map$p: IntMap[?]`),
    * but the parent's constructor asks for that same Java raw type read in another position, where
    * a wildcard could not survive. Both are the same type to Java, which passed it unchecked; this
    * writes the conversion down.
    *
    * WHICH type to name is decided by the parent, not by the argument alone. Where the parent's
    * own wildcards were eliminated to reach the `extends` clause ([[deWildcarded]]) — `Keys extends
    * MapIterator` becoming `MapIterator[AnyRef]` — the same elimination is right. But a parent
    * applied to NAMED arguments never lost anything: `Entries[V] extends MapIterator[V]` asks for
    * `IntMap[V]`, and eliminating the argument's wildcard independently produced
    * `IntMap[Object]` — the right shape at the wrong type, which scalac rejects. So take the
    * parent's formal under its actual instantiation whenever it is available, and fall back to
    * the isolated elimination only for a parent we cannot see into. */
  private def superArg(parent: TypeRepr, a: Term, n: Int, i: Int): String =
    if !hasWildcardArg(a.tpe) then term(a, i)
    else
      val target = superFormal(parent, n).filterNot(hasWildcardArg).map(tpe)
        .getOrElse(deWildcarded(a.tpe, named = false))
      s"${term(a, i)}.asInstanceOf[$target]"

  private def parent(p: Term | TypeTree): String = p match
    case tt: TypeTree  => parentTpe(tt.tpe)
    case t: Term  => parentTpe(t.tpe)

  /** a parent type in an `extends` clause: a wildcard type argument (`Foo[?, ?]`, from a raw
    * generic supertype) is ILLEGAL here — replace each `?` with its upper bound (or `AnyRef`). */
  /** Only the HEAD is a `namedInner` position. A type ARGUMENT of the parent is an ordinary type
    * position: the simple name of an inner class is NOT in scope in an `extends` clause
    * (`ParticleEffectPool extends Pool[PooledEffect]` → `Not found: type PooledEffect`), while the
    * projection that `typeSym` would otherwise give is both legal and correct there. */
  private def parentTpe(t: TypeRepr): String = deWildcarded(t, named = true)

  /** the head symbol of a (possibly applied) type. */
  private def headSymOf(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)      => Some(s)
    case TypeRepr.AppliedType(tc, _) => headSymOf(tc)
    case _                           => None

  /** a type's own parameters paired with their declared upper bounds (`NoType` when unbounded). */
  private def declBounds(tycon: SymId): List[(SymId, TypeRepr)] =
    program.definitionOf(tycon).collect { case c: Tree.ClassDef =>
      c.tparams.map(tp => tp.symbol -> (tp.rhs.tpe match
        case TypeRepr.TypeBounds(_, hi) if hi != TypeRepr.NoType => hi
        case _                                                   => TypeRepr.NoType))
    }.getOrElse(Nil)

  /** does this type mention the given symbol anywhere — the F-bound test (`N <: Node[N,V,A]`)? */
  private def mentionsSym(t: TypeRepr, s: SymId): Boolean = t match
    case TypeRepr.TypeRef(_, x)             => x == s
    case TypeRepr.AppliedType(tc, as)       => mentionsSym(tc, s) || as.exists(mentionsSym(_, s))
    case TypeRepr.TypeBounds(lo, hi)        => mentionsSym(lo, s) || mentionsSym(hi, s)
    case TypeRepr.AndType(l, r)             => mentionsSym(l, s) || mentionsSym(r, s)
    case TypeRepr.OrType(l, r)              => mentionsSym(l, s) || mentionsSym(r, s)
    case _                                  => false

  /** ONE substitution function for the whole engine — [[ParentSubst.subst]], §4.56.
    *
    * This was a two-case copy here (`TypeRef`, `AppliedType`) while the constructor replay had a
    * third and the diamond forwarder and the constructor funnel had none at all: four callers, three
    * spellings, two of them silently answering "nothing to substitute". The name stays because five
    * sites read better with it; the derivation does not. */
  private def substTp(t: TypeRepr, m: Map[SymId, TypeRepr]): TypeRepr = ParentSubst.subst(t, m)

  /** Render a type with every WILDCARD argument eliminated — illegal in an `extends` clause, and
    * illegal as the target of a cast.
    *
    * A wildcard becomes its own written bound, else the type PARAMETER's declared upper bound, else
    * `AnyRef`. Consulting the declaration is what the plain `AnyRef` fill got wrong: it produced
    * `extends ParticleControllerRenderer[AnyRef, AnyRef]` for a class whose parameters are
    * `D <: ParticleControllerRenderData, T <: ParticleBatch[D]`, which fails its own bounds.
    * Arguments resolve LEFT TO RIGHT with the earlier choices substituted in, because a later bound
    * may name an earlier parameter — as `T <: ParticleBatch[D]` does.
    *
    * `named` selects the head's rendering. It is a Boolean rather than the `byName` combinator
    * passed as a function because `byName` sets a mutable flag AROUND evaluating its by-name
    * argument; handing it to a strict `String => String` parameter evaluates the head first and
    * silently loses the flag (which turned `extends Channel` into `extends ParallelArray#Channel`). */
  /** The de-wildcarding CHOICE, as types rather than as text — the same decision [[deWildcarded]]
    * renders, exposed so that a parent's elimination and the members that override through it can
    * be driven from ONE answer. `None` where the slot stays a wildcard (F-bounded, or nothing to
    * fill from), which is exactly where an override cannot be aligned either. */
  private def deWildcardedArgs(tc: TypeRepr, args: List[TypeRepr]): List[Option[TypeRepr]] =
    val bounds = headSymOf(tc).map(declBounds).getOrElse(Nil)
    args.zipWithIndex.foldLeft((List.empty[Option[TypeRepr]], Map.empty[SymId, TypeRepr])) {
      case ((acc, m), (a, i)) =>
        val fBounded = bounds.lift(i).exists((p, hi) => hi != TypeRepr.NoType && mentionsSym(hi, p))
        val chosen: Option[TypeRepr] = a match
          case _: TypeRepr.TypeBounds if fBounded                  => scala.None
          case TypeRepr.TypeBounds(_, hi) if hi != TypeRepr.NoType => Some(substTp(hi, m))
          case _: TypeRepr.TypeBounds =>
            bounds.lift(i).map(_._2).filter(_ != TypeRepr.NoType).map(substTp(_, m))
          case other => Some(other)
        val m2 = (bounds.lift(i), chosen) match
          case (Some((pp, _)), Some(c)) => m + (pp -> c)
          case _                        => m
        (acc :+ chosen, m2)
    }._1

  private def deWildcarded(t: TypeRepr, named: Boolean): String =
    def head(f: => String): String = if named then byName(f) else f
    t match
      case TypeRepr.AppliedType(tc, args) =>
        val bounds = headSymOf(tc).map(declBounds).getOrElse(Nil)
        val (as, _) = args.zipWithIndex.foldLeft((List.empty[String], Map.empty[SymId, TypeRepr])) {
          case ((acc, m), (a, i)) =>
            // An F-BOUNDED parameter (`N extends Node<N,V,A>`) cannot be eliminated at all: no
            // finite type satisfies `N <: Node[N,V,A]` except a real subclass. `Node[Object, …]`
            // fails the bound, and so does every unrolling — `Node[Node[Object,…], …]` needs its
            // argument to be the very type being defined, and `Node` is invariant. Java has the
            // same bound and simply does not check it at an erased use; Scala does. The one type
            // that works is the WILDCARD, which asserts only that SOME type satisfies the bound —
            // verified against scalac before writing this. So an F-bounded slot stays `?`.
            val fBounded = bounds.lift(i).exists((p, hi) => hi != TypeRepr.NoType && mentionsSym(hi, p))
            val chosen: Option[TypeRepr] = a match
              case _: TypeRepr.TypeBounds if fBounded                  => scala.None
              case TypeRepr.TypeBounds(_, hi) if hi != TypeRepr.NoType => Some(substTp(hi, m))
              case _: TypeRepr.TypeBounds =>
                bounds.lift(i).map(_._2).filter(_ != TypeRepr.NoType).map(substTp(_, m))
              case other => Some(other)
            val rendered =
              if chosen.isEmpty && fBounded then "?" else chosen.map(tpe).getOrElse("scala.AnyRef")
            val m2 = (bounds.lift(i), chosen) match
              case (Some((p, _)), Some(c)) => m + (p -> c)
              case _                       => m
            (acc :+ rendered, m2)
        }
        s"${head(tpe(tc))}[${as.mkString(", ")}]"
      case _ => head(tpe(t))

  /** does this type carry a wildcard argument anywhere? */
  private def hasWildcardArg(t: TypeRepr): Boolean = t match
    case _: TypeRepr.TypeBounds      => true
    case TypeRepr.AppliedType(tc, a) => hasWildcardArg(tc) || a.exists(hasWildcardArg)
    case _                           => false

  /** a statement rendered on ONE LINE, with any comment stripped — for the two positions where a
    * newline is illegal (a `for` header's init and update clauses). */
  private def flatStat(s: Statement): String = s match
    case t: Term        => stat(Tree.uncomment(t), 0)
    case v: Tree.ValDef => stat(v.copy(leading = Nil), 0)
    case other          => stat(other, 0)

  /** THE STATEMENT RENDERING DISPATCH — §2.3(c)'s emitter surface, one of two.
    *
    * The wrapper is HERE and not in an arm, for the reason `Lowering.of` states about the frontend:
    * an arm that could decline to wrap is an arm that can escape its obligations, which is the same
    * shape as the defect the mechanism exists to catch. Entered at the dispatch, an arm never had
    * the choice. `TirKinds.of` maps the node to the name the registry attaches on — its
    * `productPrefix`, which is what `EmissionFieldCoverageSpec` derives from the class files. */
  private def stat(s: Statement, i: Int): String =
    Rendering.of(TirKinds.of(s), s.origin, s)(statArm(s, i))

  /** JS-C47 / C48 / C49 / C50 — JAVA'S FOUR ACCESS LEVELS, consulted at every DECLARATION.
    *
    * The four rows are one decision seen from four sides — `Visibility.decide` runs over the whole
    * program and [[visOf]] renders its answer at a class, a method and a field alike — so they
    * attach at all three declaration kinds (`Differences.everyDeclaration`) and are consulted HERE,
    * where the three arms of the rendering dispatch converge. Stated inside `classDef1`, `defDef`
    * and `valDef` it would be three copies of one rule, which is `ENGINE-LIMITS.md` F8's shape and
    * has now cost this engine three separate defects.
    *
    * Each fires where the two languages genuinely diverge, and the two package-private rows fire
    * TOGETHER because they are the same fact read at its two ends: JS-C50 is java's DEFAULT being
    * package-private where scala's is public (emit nothing and the member is published), JS-C47 is
    * the `private[pkg]` that translation takes. */
  private def declVisibility(s: Symbol, at: Origin)(using Obligations): Unit =
    // Read off `visPlan` — the DECIDED level — and never off the raw java flags. `Visibility.decide`
    // is allowed to widen (a `protected static` moving to the companion, §8.7's residues), so the
    // flags say what java wrote and the plan says what this port emits, and a consult that answered
    // from the first would be reporting about a decision it had not read.
    val v = visPlan.getOrElse(s.id, Visibility.Vis.Public)
    val packagePrivate = v match
      case Visibility.Vis.PackagePrivate | Visibility.Vis.PrivateAt(_) => true
      case _                                                           => false
    Obligations.consult(JS.C(47), at)(Option.when(packagePrivate)(()))
    Obligations.consult(JS.C(48), at)(Option.when(v match
      case Visibility.Vis.ProtectedPkg | Visibility.Vis.ProtectedAt(_) => true
      case _                                                           => false)(()))
    // JS-C49 fires where java's `private` genuinely reaches further than scala's: a member of a
    // NESTED type, which java scopes to the whole enclosing TOP-LEVEL class. On a top-level class's
    // own member the two rules coincide exactly, and `privateQualifier` emits nothing there —
    // asked through THAT function this would read the emitter's positional state, which at a
    // `Tree.ClassDef` consult is still the ENCLOSING owner and at the render is the class itself.
    Obligations.consult(JS.C(49), at)(
      Option.when(v == Visibility.Vis.Private && s.owner != SymId.None && !topLevelSyms(s.owner))(()))
    Obligations.consult(JS.C(50), at)(Option.when(packagePrivate)(()))

  /** the program's TOP-LEVEL type symbols — one set, for the nested-owner test above. */
  private lazy val topLevelSyms: Set[SymId] = program.units.map(_.symbol).toSet

  /** the top-level type a symbol is emitted INSIDE — which is the emitted FILE, since a unit is a
    * file. `SymId.None` for anything this program does not own, and that answer is load-bearing:
    * a permitted subtype the port does not declare is one no file of ours contains. */
  private def topLevelOf(id: SymId, seen: Set[SymId] = Set.empty): SymId =
    if id == SymId.None || topLevelSyms(id) || seen(id) then id
    else program.symbolOf(id).map(s => topLevelOf(s.owner, seen + id)).getOrElse(SymId.None)

  /** every DIRECT subtype this program declares, by parent — `parentsBySym` inverted. */
  private lazy val subtypesBySym: Map[SymId, List[SymId]] =
    parentsBySym.toList.flatMap((c, ps) => ps.map(p => p -> c)).groupMap(_._1)(_._2)

  /** JS-C44 — java's `sealed`/`permits` against scala's FILE-SCOPED `sealed`.
    *
    * Java seals a hierarchy by NAMING its subclasses, wherever in the module they live; scala seals
    * one by CONTAINING them, in the file the parent is declared in. Where the two coincide — every
    * subtype this program declares lands in the same emitted unit — `sealed` is the exact image and
    * is emitted. Where they do not, there is no image at all: scala has no `permits`, so the type
    * ships OPEN, which is a widening of who may extend it and is invisible in the emitted text.
    * That residue is RECORDED and counted rather than approximated (`ENGINE-LIMITS.md` M6).
    *
    * A sealed type with NO subtype in this program takes the same answer as one whose subtypes are
    * elsewhere, and deliberately: emitting `sealed` there would be a claim this run cannot check,
    * and the direction it fails in is a scala COMPILE ERROR in whatever module holds the subclass.
    * Conservative is the only safe side of that.
    *
    * ==THE SURVIVORS ARE NOT THE PERMITS LIST==
    *
    * `subtypesBySym` is built from the extends-edges this run PARSED, and that is a different set
    * from the one java wrote: a permitted subtype in a file the port excluded (`excludeGlobs`), or
    * in a unit whose translation was refused, leaves no edge at all. Decided from the survivors
    * alone, a hierarchy whose only remaining subclass is in this file reads as "the seal is exact"
    * and ships `sealed` — and the shim injected at the excluded FQN, or §4.45's consumer, then gets
    * a scalac error extending a type java said it could extend. The WIDENING is counted; a wrongful
    * SEAL is invisible in every instrument, because nothing is recorded for a decision not taken.
    *
    * So the seal is kept only where the program-declared subtype set ACCOUNTS FOR every type java
    * PERMITTED ([[balticporter.tir.Symbol.permits]], interned by the frontend), and anything
    * unaccounted widens with the rest. An empty permits list is not a gap: java lets the clause be
    * omitted exactly when every subclass is in the same compilation unit, which is the one case
    * where the parse cannot have missed one.
    *
    * `non-sealed` needs nothing. Where the seal survives, every permitted subtype is in the same
    * file and scala already allows each to be extended further; where it does not, there is no seal
    * for a child to opt out of.
    *
    * Returns the KEYWORD and the note that goes above the declaration — the pair, because a
    * widening with no note is exactly the silence this row was `Open` for. */
  private def sealOf(cd: Tree.ClassDef, s: Symbol, i: Int): (String, String) =
    if !s.flags.isSealed then ("", "")
    else
      val mine     = topLevelOf(cd.symbol)
      val subs     = subtypesBySym.getOrElse(cd.symbol, Nil)
      val elsewhere = subs.filterNot(x => topLevelOf(x) == mine)
      // …and the types java NAMED that this program does not declare as a subtype of it at all —
      // an excluded file, a refused unit, a subtype another module owns. Compared as interned ids,
      // never as names: the permits list and the emitted FQN are two namespaces on a renaming port
      // (§4.56), and a name join would widen every seal there and none anywhere else.
      val unaccounted = s.permits.filterNot(subs.contains)
      if subs.nonEmpty && elsewhere.isEmpty && unaccounted.isEmpty then ("sealed ", "")
      else
        val d = Decision(
          kind       = Decision.Kind.WidenedSeal,
          subject    = cd.symbol,
          subjectFqn = s.fullName,
          detail     = Map(
            "subtypes"  -> subs.size.toString,
            "elsewhere" -> elsewhere.size.toString,
            // the two residues are reported apart because they are different situations with one
            // emitted shape: `elsewhere` is a subtype this run EMITS into another file, and
            // `unaccounted` is one java permitted that this run never saw — which is the only one
            // of the two that a reader cannot discover by looking at the port's output.
            "permitted" -> s.permits.size.toString,
            "unaccounted" -> unaccounted.size.toString,
            "why"       -> ("java sealed this type and named its permitted subclasses; scala's " +
              "`sealed` restricts extension to THIS FILE and has no `permits` clause, so a " +
              "hierarchy whose subtypes are not all emitted here ships open"),
          ),
          reason     = Reason.Universal("sealed-hierarchy(JS-C44)"),
          origin     = cd.origin,
        )
        emissionOf += d
        printedNotes += PorterNote.Printed(d.kind, d.subject, d.subjectFqn, currentUnitName)
        ("", PorterNote.render(d, ind(i)))

  /** JS-C43 — the members javac DERIVES from a record header (JLS 8.10.3), which no java
    * declaration in the tree carries, plus the one member scala needs and java does not have.
    *
    * ==Why a plain class and not a `case class`==
    *
    * A `case class` is the obvious image and it was PRICED against this one, cell by cell, against
    * javac's own answers. It loses six of them, and two of the six cannot be repaired at all:
    *
    *   - `toString` — java `Pt[x=1, y=2]`, case class `Pt(1,2)`: different bracket, no field names,
    *     no space. Overridable, but then the case class's own rendering is dead weight;
    *   - `hashCode` — java folds `31 * h + <boxed component hash>` from 0 (`Pt(1,2)` is `33`); a
    *     case class uses `MurmurHash3.productHash` (`2081183297`). JLS 8.10.3 leaves the ALGORITHM
    *     unspecified, so this one binds nothing on its own — but two values that disagree are two
    *     hash-bucket orders, and a ported test that iterates a `HashMap` sees the difference;
    *   - `equals` on `double`/`float` — java compares with `Double.compare`, so `NaN` equals `NaN`
    *     and `0.0` does NOT equal `-0.0`; scala's `==` on a primitive is the opposite on both
    *     (measured, both directions);
    *   - an EXPLICIT accessor — java lets a record write `public int y() { return y * 2; }` beside
    *     the component. A case class's `val y` and that `def y()` are `E120 Conflicting
    *     definitions`, so the shape is not expressible at all;
    *   - the DECONSTRUCTION — a case class's `unapply` reads the constructor parameters, and java's
    *     record pattern reads the ACCESSOR (JLS 14.30.1). On the record above, java binds `6` and a
    *     case class would bind `3`. Silent, and not repairable while the extractor is generated;
    *   - the added surface — `copy`, `apply`, `productArity`, `productElementName`, `canEqual` are
    *     names java did not put on the type, and `canEqual` exists for a problem records do not
    *     have (a record is final by construction).
    *
    * So the image is a plain `final class` with each of javac's four members written out, which
    * reproduces every one of those cells exactly (all measured against `javac`, both languages run).
    *
    * ==THREE RESIDUES, all on the DECISION==
    *
    * `Class.isRecord`/`getRecordComponents`, because scalac emits no JVM record whatever the
    * `extends` clause says. And two more that the EXTRACTOR's shape decides, both measured against
    * javac 22.0.2 and neither closable by anything in this file: a scala `unapply` returning a
    * tuple is a FUNCTION, so it calls EVERY accessor before one component pattern is tried, where
    * java calls them left to right and stops at the first failing component (`[a]`, not `[a, b]`);
    * and it PROPAGATES what an accessor throws, where java wraps it in a `java.lang.MatchException`
    * with the original as the cause. The exact image is name-based extractors matched lazily, which
    * scala has no form for. Recorded, so the port's own reader can find them.
    *
    * ==What each member reads==
    *
    * `equals`, `hashCode` and `toString` read the FIELDS and the extractor reads the ACCESSORS, and
    * that is java's own split rather than a convenience: an overridden accessor changes what a
    * record PATTERN binds and changes neither the printed form nor the equality (measured).
    *
    * A member the record DECLARES ITSELF replaces the generated one (JLS 8.10.3), so each of the
    * three is skipped where the class already has it — by SIGNATURE, which for `hashCode()` and
    * `toString()` IS the arity (java cannot overload on a return type, so at arity 0 each of those
    * names exactly one member) and for `equals` is the one-argument form whose parameter is
    * `java.lang.Object`. Nothing coarser will do: java resolves `equals(String)` beside
    * `equals(Object)` and derives the second anyway, and suppressing it does not even leave the
    * class abstract — `AnyRef.equals` is concrete, so the record downgrades to REFERENCE equality
    * with a green compile and no moved count. The EXTRACTOR asks a different question again, since
    * it is emitted into the companion: see [[hasUnapply]]'s reasoning at the site.
    *
    * ==The `asInstanceOf[java.lang.Object]` on every reference component==
    *
    * Not decoration and not defensive. `Objects.equals`, `Objects.hashCode` and `String.valueOf`
    * all take `Object`, and a component's type may be a TYPE VARIABLE — `T <: Any` in scala — which
    * does not conform. It also fixes the one place the overloads would diverge from java: a
    * `char[]` component reaches `String.valueOf(char[])` unascribed, which prints the CHARACTERS,
    * where javac's concat uses `String.valueOf(Object)` and prints `[C@…`.
    *
    * @return the members for the CLASS body, the members for the COMPANION, and the porter note. */
  private def recordMembers(cd: Tree.ClassDef, s: Symbol, i: Int): (List[String], List[String], String) =
    if !s.flags.isRecord then (Nil, Nil, "")
    else
      val comps = s.components
      val self  = esc(s.name)
      // the class's own parameters, re-declared on the extractor. Rendered through `typeParam`, the
      // same function the class header uses, so the two spellings cannot drift.
      val tpDecl = if cd.tparams.isEmpty then "" else "[" + cd.tparams.map(typeParam).mkString(", ") + "]"
      val tpArgs = if cd.tparams.isEmpty then "" else "[" + cd.tparams.map(tp => esc(sym(tp.symbol).name)).mkString(", ") + "]"
      val tpWild = if cd.tparams.isEmpty then "" else "[" + cd.tparams.map(_ => "?").mkString(", ") + "]"
      // a member the RECORD ITSELF declares, by (name, java arity) — JLS 8.10.3's own override rule.
      // ARITY is the whole signature for `hashCode()` and `toString()`, which is why they use this:
      // java cannot overload on a return type, so at arity 0 each of those names exactly one member.
      // `equals` is the one that needs more — see [[declaresEquals]].
      val declared: Set[(String, Int)] = cd.body.collect {
        case d: Tree.DefDef => (sym(d.symbol).name, d.paramss.headOption.getOrElse(Nil).size)
      }.toSet
      /** the FQN of a parameter's type head, for the two signature tests below. */
      def paramHead(ps: List[Tree.ValDef]): Option[String] = ps match
        case p :: Nil => headSymOf(p.tpt.tpe).map(x => sym(x).fullName)
        case _        => None
      /** does the record declare JAVA'S `equals` — the ONE-argument one whose parameter is
        * `java.lang.Object` (JLS 8.10.3, 8.4.9)?
        *
        * By SIGNATURE and not by (name, arity), which is `ENGINE-LIMITS.md` K5.7's rule read one
        * cell finer than the arity test can see. Java resolves `equals(String)` and `equals(Object)`
        * separately, so a record declaring the first still gets the second derived; suppressed, the
        * class does not even go abstract — `AnyRef.equals` is concrete — so the record silently
        * downgrades to REFERENCE equality, with a green compile, no moved count and no finding. */
      def declaresEquals: Boolean = cd.body.exists {
        case d: Tree.DefDef if sym(d.symbol).name == "equals" =>
          paramHead(d.paramss.headOption.getOrElse(Nil)).contains("java.lang.Object")
        case _ => false
      }
      val fieldTpe = cd.body.collect { case v: Tree.ValDef => v.symbol -> v.tpt.tpe }.toMap
      /** the emitted VALUE-CLASS name of a component, when it is a java primitive — the whole of
        * "does this compare, hash and print by value". Read through `TirEmitter.ScalaValueClasses`,
        * which is the one place that set is spelled. */
      def primOf(c: RecordComponent): Option[String] =
        headSymOf(fieldTpe.getOrElse(c.field, sym(c.field).info)).map(x => sym(x).fullName)
          .filter(TirEmitter.ScalaValueClasses.contains)
      def boxed(v: String): String = s"$v.asInstanceOf[java.lang.Object]"
      def eqOf(c: RecordComponent, a: String, b: String): String = primOf(c) match
        // JLS 8.10.3 names `Double.compare`/`Float.compare` for exactly these two, which is NOT what
        // `==` does at either end of the float domain.
        case Some("scala.Double") => s"java.lang.Double.compare($a, $b) == 0"
        case Some("scala.Float")  => s"java.lang.Float.compare($a, $b) == 0"
        case Some(_)              => s"$a == $b"
        case None                 => s"java.util.Objects.equals(${boxed(a)}, ${boxed(b)})"
      def hashOf(c: RecordComponent, v: String): String =
        primOf(c).flatMap(TirEmitter.RecordBoxes.get) match
          case Some(box) => s"$box.hashCode($v)"
          case None      => s"java.util.Objects.hashCode(${boxed(v)})"
      def strOf(c: RecordComponent, v: String): String = primOf(c) match
        case Some(_) => s"java.lang.String.valueOf($v)"
        case None    => s"java.lang.String.valueOf(${boxed(v)})"
      def mine(c: RecordComponent): String  = s"this.${local(c.field)}"
      def theirs(c: RecordComponent): String = s"that$$rec.${local(c.field)}"

      val eqM =
        if declaresEquals then Nil
        else if comps.isEmpty then
          List(s"${ind(i + 1)}override def equals(o$$rec: scala.Any): scala.Boolean = o$$rec.isInstanceOf[$self$tpWild]")
        else
          val cmp = comps.map(c => eqOf(c, mine(c), theirs(c))).mkString(" && ")
          List(s"${ind(i + 1)}override def equals(o$$rec: scala.Any): scala.Boolean = o$$rec match {",
               s"${ind(i + 2)}case that$$rec: $self$tpWild => $cmp",
               s"${ind(i + 2)}case _ => false",
               s"${ind(i + 1)}}")
      val hashM =
        if declared(("hashCode", 0)) then Nil
        else if comps.isEmpty then List(s"${ind(i + 1)}override def hashCode(): scala.Int = 0")
        else
          List(s"${ind(i + 1)}override def hashCode(): scala.Int = {",
               s"${ind(i + 2)}var hash$$rec: scala.Int = 0") ++
          comps.map(c => s"${ind(i + 2)}hash$$rec = hash$$rec * 31 + ${hashOf(c, mine(c))}") ++
          List(s"${ind(i + 2)}hash$$rec", s"${ind(i + 1)}}")
      val strM =
        if declared(("toString", 0)) then Nil
        else
          // the SIMPLE name as this port emits it, which is the same answer `enumDef` gives
          // `Enum.name()` and `valueOf`'s arms: a renamed declaration reports the name it now has.
          val parts = comps.map(c => s""""${c.name}=" + ${strOf(c, mine(c))}""").mkString(""" + ", " + """)
          val body  = if comps.isEmpty then s""""$self[]"""" else s""""$self[" + $parts + "]""""
          List(s"${ind(i + 1)}override def toString(): java.lang.String = $body")

      // THE EXTRACTOR — scala's half of JLS 14.30.1, deconstructing through the ACCESSORS exactly as
      // java's record pattern does. Declined where the record already declares an `unapply` that
      // would COLLIDE with it, because a synthesised twin would be a duplicate definition.
      //
      // "Collide" is the whole test, and the bare name is not it. The derived extractor is emitted
      // into the COMPANION, so only a STATIC java member reaches the same scope, and only one whose
      // single parameter is the RECORD ITSELF erases to the same signature. An INSTANCE `unapply`
      // is a member of the class and cannot clash with anything here; a static `unapply(String)` is
      // an ordinary overload beside it. Read on the name alone, either of those DECLINED the
      // synthesis — and then every record pattern over the type names a `Not Found`, which is the
      // loud half of the same defect `declaresEquals` above has silently.
      val hasUnapply = cd.body.exists {
        case d: Tree.DefDef if sym(d.symbol).name == "unapply" && sym(d.symbol).flags.isStatic =>
          paramHead(d.paramss.headOption.getOrElse(Nil)).contains(s.fullName)
        case _ => false
      }
      val unap =
        if hasUnapply then Nil
        else
          val ps = comps.map(c => s"r$$rec.${local(c.accessor)}()")
          val ts = comps.map(c => tpe(fieldTpe.getOrElse(c.field, sym(c.field).info)))
          val sig = s"${ind(i + 1)}def unapply$tpDecl(r$$rec: $self$tpArgs)"
          if comps.isEmpty then List(s"$sig: scala.Boolean = true")
          // `Tuple1` and not the bare component: scala's extractor rules want a result with `_1`,
          // and an arity-1 tuple is the only product type that has exactly one.
          else if comps.sizeIs == 1 then List(s"$sig: scala.Tuple1[${ts.head}] = scala.Tuple1(${ps.head})")
          else List(s"$sig: (${ts.mkString(", ")}) = (${ps.mkString(", ")})")

      // …and the members the RECORD declared for itself, which are the ones this did NOT write. The
      // pair is reported rather than the positive alone: "synthesised=toString" reads as a gap
      // unless the reader can see that java's own `equals` is right there in the file.
      val synthesised = List("equals" -> eqM, "hashCode" -> hashM, "toString" -> strM, "unapply" -> unap)
        .collect { case (n, ms) if ms.nonEmpty => n }
      val kept = List("equals" -> eqM, "hashCode" -> hashM, "toString" -> strM, "unapply" -> unap)
        .collect { case (n, ms) if ms.isEmpty => n }
      val d = Decision(
        kind       = Decision.Kind.RecordMembers,
        subject    = cd.symbol,
        subjectFqn = s.fullName,
        detail     = Map(
          "components" -> comps.size.toString,
          "synthesised" -> (if synthesised.isEmpty then "none" else synthesised.mkString(",")),
          "declared" -> (if kept.isEmpty then "none" else kept.mkString(",")),
          // the residue, and the only part of the construct no image can carry: scalac emits no JVM
          // record, so the class file carries no `Record` attribute whatever its `extends` clause
          // says. `x instanceof java.lang.Record` still answers true; `getClass.isRecord` answers
          // false and `getRecordComponents` answers null, which a framework that discovers records
          // reflectively WILL act on.
          "reflective" -> "isRecord=false;getRecordComponents=null",
          // …and the two residues of the DECONSTRUCTION, which the extractor's shape decides and
          // no assertion in this file can close. A scala `unapply` returning a tuple is a
          // FUNCTION: it evaluates every accessor before one component pattern is tried, and it
          // propagates whatever an accessor throws. Java's record pattern does neither (JLS
          // 14.30.2, both measured against javac 22.0.2). Recorded rather than repaired: the exact
          // image is name-based extractors matched lazily, which scala has no form for at a
          // pattern this engine also has to keep readable.
          "patternAccessors" -> ("ALL, eagerly (java calls them left to right and STOPS at the " +
            "first component pattern that fails)"),
          "patternThrow" -> ("raw (java wraps an accessor's exception in java.lang.MatchException, " +
            "with the original as its cause)"),
          "why" -> ("javac derives equals/hashCode/toString from a record's components and scala " +
            "derives nothing from a plain class; a case class derives all three with different " +
            "answers, so each is written out to java's own contract"),
        ),
        reason     = Reason.Universal("record-members(JS-C43)"),
        origin     = cd.origin,
      )
      emissionOf += d
      printedNotes += PorterNote.Printed(d.kind, d.subject, d.subjectFqn, currentUnitName)
      (eqM ++ hashM ++ strM, unap, PorterNote.render(d, ind(i)))

  /** THE AREA-C ROWS A TYPE DECLARATION OWES — consulted at the dispatch, above every arm.
    *
    * `classDef` forks into `enumDef` and `classDef1`, and most of these rows are decided inside one
    * of the two. Consulted there, every row would be a HOLE at the other shape — an enum's rendering
    * would owe `JS-C34` and never ask it — so the consults sit at the one point both arms are below,
    * which is this dispatch's own `case`.
    *
    * The PREDICATES are read off the tree and the symbol table rather than by re-running the
    * emitter's own machinery. That is deliberate and it is stated because it bounds what a `fired`
    * count means: `consult` asks *does this difference APPLY at this declaration*, and the shape at
    * which it applies is what the tree says. Re-deriving `diamondOverrides` or `orderBody` here
    * would double the only two walks in this file that are not linear, to move a diagnostic number
    * from "this class has the shape" to "the repair emitted text", which the edge-case suite
    * asserts and the emitted diff already shows. */
  private def classConsults(cd: Tree.ClassDef)(using Obligations): Unit =
    val s       = sym(cd.symbol)
    val at      = cd.origin
    val plan    = if s.flags.isModule then CtorFunnel.Plan.none else plans(cd)
    val statics = cd.body.filter(isStatic)
    val inst    = cd.body.filterNot(isStatic)
    val ctors   = cd.body.collect { case d: Tree.DefDef if sym(d.symbol).name == "<init>" => d }
    val exports = parentSymsOf(cd).filter(p => staticsReachable(p))
    /** a member java runs in a class-initialisation step — a field WITH an initialiser, or a block.
      * The same predicate at both steps: JLS 12.4.2 step 9 for the static pair and 12.5 step 4 for
      * the instance one, which is exactly why the two rows below share it. */
    def stepMember(x: Statement): Boolean = x match
      case v: Tree.ValDef => v.rhs.isDefined
      case d: Tree.DefDef => isInitBlock(d)
      case _              => false
    val isEnum = s.flags.isEnum

    // -- statics: java inherits them, a companion inherits nothing ------------------------------
    Obligations.consult(JS.C(3), at)(Option.when(exports.nonEmpty && statics.nonEmpty)(()))
    Obligations.consult(JS.C(34), at)(Option.when(exports.nonEmpty)(()))

    // -- class initialisation (JLS 12.4) ---------------------------------------------------------
    Obligations.consult(JS.C(7), at)(
      Option.when(hasClinit(statics) || nearestClinitAncestor(cd.symbol).isDefined)(()))
    Obligations.consult(JS.C(10), at)(Option.when(reentrantBearers.contains(cd.symbol))(()))
    Obligations.consult(JS.C(9), at)(Option.when(statics.count(stepMember) > 1)(()))

    // -- instance creation (JLS 12.5) ------------------------------------------------------------
    Obligations.consult(JS.C(18), at)(
      Option.when(inst.exists { case v: Tree.ValDef => v.rhs.isDefined; case _ => false } &&
                  inst.exists { case d: Tree.DefDef => isInitBlock(d); case _ => false })(()))
    Obligations.consult(JS.C(13), at)(Option.when(plan.primary.isDefined || plan.isSynthesised)(()))
    Obligations.consult(JS.C(14), at)(Option.when(plan.superArgs.nonEmpty)(()))
    Obligations.consult(JS.C(19), at)(Option.when(ctors.sizeIs > 1)(()))
    Obligations.consult(JS.C(20), at)(Option.when(plan.isSynthesised)(()))
    Obligations.consult(JS.C(21), at)(Option.when(ctors.sizeIs > 1)(()))

    // -- inheritance ------------------------------------------------------------------------------
    // JS-C33's shape and not its repair: `diamondOverrides` declines on `parents.sizeIs < 2` in its
    // own first line, so this predicate is that test and the walk below it happens once.
    Obligations.consult(JS.C(33), at)(Option.when(cd.parents.sizeIs >= 2)(()))
    Obligations.consult(JS.C(44), at)(Option.when(s.flags.isSealed)(()))

    // -- records (JLS 8.10) -------------------------------------------------------------------
    Obligations.consult(JS.C(43), at)(Option.when(s.flags.isRecord)(()))

    // -- enums (JLS 8.9) ---------------------------------------------------------------------------
    Obligations.consult(JS.C(37), at)(Option.when(isEnum)(()))
    // `enumDef.hasName`'s own two disjuncts and not a third spelling of them: java's TWO namespaces
    // let a promoted constructor PARAMETER or a FIELD carry the name beside `Enum.name()`, and
    // reading only the first said "does not apply" at the shape the row is named for.
    Obligations.consult(JS.C(38), at)(Option.when(isEnum &&
      (plan.primaryParams.exists(v => sym(v.symbol).name == "name") ||
       cd.body.exists { case d: Definition => sym(d.symbol).name == "name"; case _ => false }))(()))
    Obligations.consult(JS.C(39), at)(Option.when(isEnum)(()))
    Obligations.consult(JS.C(40), at)(Option.when(isEnum && cd.enumCases.exists(_.body.nonEmpty))(()))

    // JS-G35 — scala CHECKS an F-bound where javac does not, so a naive erasure of `N <: Node[N,…]`
    // is rejected at every use. It is one decision seen from two declaration kinds (a class's formal
    // parameters and a method's), which is why the row attaches at both and why the predicate is
    // `fBounded` — stated once, called from here and from the `DefDef` case of the dispatch.
    Obligations.consult(JS.G(35), at)(Option.when(fBounded(cd.tparams))(()))

    // -- the RAW PARENT (JLS 4.8, 8.1.4) -----------------------------------------------------------
    //
    // JS-G05 and JS-G11 are ONE fold read at its two outcomes: a wildcard is illegal in an `extends`
    // clause, so `deWildcardedArgs` eliminates it — to its own written bound, else the type
    // PARAMETER's declared upper bound, else `AnyRef` — and REFUSES to for an F-bounded parameter,
    // where no finite instantiation satisfies `N <: Node[N,…]` and the wildcard's weaker claim is
    // the only one scalac accepts.
    //
    // CONSULTED HERE and not at the type dispatch, which is where the two rows were expected to
    // land: the elimination is decided ABOVE `TirEmitter.tpe` — the wildcard is REPLACED before any
    // type is rendered, so the `TypeBounds` arm never sees the slot these rows are about. That is
    // `JS-G39`'s rule at the other end of the pipeline (a node its parent consumes positionally
    // owes nothing), and the consuming node is the declaration whose `extends` clause it is.
    // `deWildcardedArgs` is re-run rather than read off a cache, which is the same trade the header
    // states: it is one linear fold over a parent's arguments, not one of this file's two walks.
    val wildcardFills = cd.parents.flatMap { p =>
      (p match { case tt: TypeTree => tt.tpe; case t: Term => t.tpe }) match
        case TypeRepr.AppliedType(tc, args) =>
          args.zip(deWildcardedArgs(tc, args)).collect { case (_: TypeRepr.TypeBounds, chosen) => chosen }
        case _ => Nil
    }
    Obligations.consult(JS.G(5), at)(Option.when(wildcardFills.exists(_.isDefined))(()))
    Obligations.consult(JS.G(11), at)(Option.when(wildcardFills.contains(scala.None))(()))

    declVisibility(s, at)

  /** does any of these type parameters mention ITSELF in its own bound — java's F-bound, which scala
    * checks at every use and javac does not (JS-G35)? */
  private def fBounded(tps: List[Tree.TypeDef]): Boolean =
    tps.exists(tp => mentionsSym(tp.rhs.tpe, tp.symbol))

  private def statArm(s: Statement, i: Int)(using Obligations): String = s match
    // a commented STATEMENT: its comments at the statement's own indent, then the statement. A
    // DEFINITION never arrives here wrapped — it carries its own `leading` field — so this is
    // exactly the block-statement case and nothing else.
    case c: Tree.Commented => leading(c.leading, i) + stat(c.stmt, i)
    case c: Tree.ClassDef =>
      classConsults(c)
      classDef(c, i)
    // a Java initializer block is carried as a synthetic member; emit its BODY inline rather than
    // a `def`, since a block in a class/object body runs at initialisation — where Java runs it
    // too — and `orderBody` has already placed it after the field declarations it fills.
    // `locally` is REQUIRED, not decoration: a bare `{ … }` on the line after a field initialised
    // with `new T(…)` is parsed as that constructor's anonymous-class body
    // (`new Array[Float](n) { … }`), which fails as "anonymous class cannot extend final class".
    // An initialiser block is still a DECLARATION the port can decide about — a policy that
    // replaces its body (`MethodBodyTransform` on a `<clinit>`) records a decision whose subject is
    // this synthetic member. Rendering the block without `declNotes` therefore emitted the decision
    // with no note, which `NoteCoverageCheck` fails the run for (CLAUDE.md §4.575: a note is
    // DERIVED, and the check runs in both directions). The trivia comes first and the note last,
    // for the reason `defDef` states.
    // JS-S25 — consulted HERE and not inside `defDef`, because a `Tree.DefDef` reaches the page
    // through TWO arms and only one of them is `defDef`: a java initializer block is a synthetic
    // member rendered inline, and an obligation discharged in one arm is a hole in the other. The
    // rule goes where the arms CONVERGE (`ENGINE-LIMITS.md` F8, twice) — which is the dispatch's
    // own `case`, before it decides which shape the member takes.
    case d: Tree.DefDef   =>
      Obligations.consult(JS.S(25), d.origin)(Option.when(needsUnreachableTail(d))(()))
      // JS-C16 — an instance initialiser block; and JS-C25 — `override`, which java does not write.
      // HERE for the reason JS-S25 is: a `Tree.DefDef` reaches the page through two arms, and an
      // init block never carries `isOverride`, so a consult inside `defDef` would be a hole at
      // exactly the member JS-C16 is about.
      Obligations.consult(JS.C(16), d.origin)(Option.when(isInitBlock(d))(()))
      Obligations.consult(JS.C(25), d.origin)(Option.when(sym(d.symbol).flags.isOverride)(()))
      // JS-G35's other declaration kind — a METHOD's own formal parameters carry the same F-bound,
      // and are consulted at the same convergence point for the same reason as the two rows above.
      Obligations.consult(JS.G(35), d.origin)(Option.when(fBounded(d.tparams))(()))
      // JS-G41 — a vararg whose component is not reifiable (JLS 4.7) carries java's HEAP POLLUTION,
      // and the decision this arm takes about it is to carry it: the port reproduces java's
      // semantics exactly, and what has no scala image is the WARNING javac gave and the
      // `@SafeVarargs` that answered it. So the row is consulted, never fired into a rewrite, and
      // `HeapPollutionCheck` is the count beside it — through the same predicate, so the obligation
      // and the number cannot disagree about which declarations the row is about.
      Obligations.consult(JS.G(41), d.origin)(
        HeapPollutionCheck.uncheckedVararg(d)(using program).map(_ => ()))
      declVisibility(sym(d.symbol), d.origin)
      if isInitBlock(d) then
        d.rhs.map(r => s"${declNotes(d.symbol, i)}${ind(i)}locally ${term(r, i)}").getOrElse("")
      else defDef(d, i)
    case v: Tree.ValDef   => valDef(v, i)
    case t: Tree.TypeDef  => s"${ind(i)}${if sym(t.symbol).flags.isOpaque then "opaque " else ""}type ${esc(sym(t.symbol).name)} = ${tpe(t.rhs.tpe)}"
    case t: Term     => ind(i) + term(t, i)

  /** ctor type-parameter substitution (Scala secondary ctors can't be generic) → their bounds. */
  private var tparamSubst: Map[SymId, TypeRepr] = Map.empty

  /** Disambiguate a member that arrives CONCRETE from both the superclass and a mixin.
    *
    * Java has single inheritance of implementation, so this is never ambiguous there: a concrete
    * superclass method simply IMPLEMENTS the interface's, default or not. `IntMap.Entries extends
    * MapIterator implements Iterable<Entry>, Iterator<Entry>` gets `MapIterator.remove()`, and
    * java's `Iterator.remove` is satisfied by it. Scala linearises instead and refuses: "class
    * Entries inherits conflicting members … (Note: this can be resolved by declaring an override
    * in class Entries.)" — 11 sites in gdx core.
    *
    * So declare it, forwarding to the parent JAVA would have run: the SUPERCLASS, which is the head
    * of the parents list. This is a rendering repair rather than a tree rewrite because that is all
    * it is — no new symbol exists, no call site changes, and the forwarder is exactly the method
    * the class already had.
    *
    * `Tree.Super`'s `cls` is always the enclosing class ([[SpoonTir.superTerm]]), so a qualified
    * `super[X]` has no TIR form; the text is emitted directly. */
  private def diamondOverrides(cd: Tree.ClassDef, i: Int): List[String] =
    def headOf(t: TypeRepr): Option[SymId] = headSymOf(t)
    val parentTs = cd.parents.map { case tt: TypeTree => tt.tpe; case t: Term => t.tpe }
    if parentTs.sizeIs < 2 then Nil
    else
      def classOf_(t: TypeRepr): Option[Tree.ClassDef] =
        headOf(t).flatMap(x => program.definitionOf(x)).collect { case c: Tree.ClassDef => c }
      /** concrete instance methods, name -> the DefDef, walking a parent chain. */
      def externalOf(t: TypeRepr): Set[(String, List[Int])] =
        headOf(t).map(x => sym(x).fullName).flatMap(externalConcrete.get).getOrElse(Set.empty)
      def concrete(t: TypeRepr, seen: Set[SymId] = Set.empty): Map[(String, List[Int]), Tree.DefDef] =
        classOf_(t) match
          case Some(c) if !seen(c.symbol) =>
            val own = c.body.collect {
              case d: Tree.DefDef if d.rhs.isDefined && sym(d.symbol).name != "<init>" &&
                                     !sym(d.symbol).flags.isStatic =>
                (sym(d.symbol).name, d.paramss.map(_.size)) -> d
            }.toMap
            c.parents.map { case tt: TypeTree => tt.tpe; case x: Term => x.tpe }
              .foldLeft(own)((acc, pt) => concrete(pt, seen + c.symbol) ++ acc)
          case _ => Map.empty
      val sup     = concrete(parentTs.head)
      val mixins  = parentTs.tail.flatMap(t => concrete(t).keySet ++ externalOf(t)).toSet
      val ownKeys = cd.body.collect {
        case d: Tree.DefDef => (sym(d.symbol).name, d.paramss.map(_.size))
      }.toSet
      val supName = classOf_(parentTs.head).map(c => esc(sym(c.symbol).name))
      // THE FORWARDED SIGNATURE IS THE PARENT'S, AND IT IS WRITTEN IN THE PARENT'S SCOPE. `d` is a
      // `DefDef` this class does not declare, so every type parameter its parameters and result
      // mention belongs to whichever ancestor declared it — and the class emitting the forwarder
      // declares none of them. `class Impl extends Base[Leaf] with Leaf` emitted
      // `override def split(c: Char): Array[T]` for `T[] split(char)`: valid-looking Scala naming a
      // type that is not in scope. The instantiation is in the `extends` clause, so [[ParentSubst]]
      // makes it exact — the SAME derivation the constructor funnel and the constructor replay run,
      // never a third spelling of it (§4.56).
      val psub = ParentSubst.of(cd)(using program)
      supName.toList.flatMap { sn =>
        sup.toList.filter((k, _) => mixins(k) && !ownKeys(k)).sortBy((k, _) => k._1).map { (_, d) =>
          val n   = esc(sym(d.symbol).name)
          // …AND THE MEMBER'S OWN TYPE PARAMETERS, which are not the class's and are not
          // substituted away by anything. A java `<V> V get(DataKey<V>)` forwarded without its
          // `[V]` is a method whose signature names a type nothing declares — the SAME error text
          // as the class-parameter face and a different cause, so a fix for one leaves the other.
          // The bounds go through `psub` too: a method parameter may be bounded by the CLASS's.
          val tps = if d.tparams.isEmpty then ""
                    else d.tparams.map(td => typeParam(td.copy(rhs = td.rhs.copy(
                      tpe = ParentSubst.subst(td.rhs.tpe, psub))))).mkString("[", ", ", "]")
          // substituted at the ValDef rather than rendered here, so `paramClause` still decides
          // `using` clauses, override alignment and the un-annotated arms — a second rendering of a
          // parameter list is the drift this whole change is about.
          val pss = d.paramss.map(ps => paramClause(ps.map(v =>
            v.copy(tpt = v.tpt.copy(tpe = ParentSubst.subst(v.tpt.tpe, psub)))))).mkString
          val as  = d.paramss.map(ps => ps.map(v => esc(sym(v.symbol).name)).mkString("(", ", ", ")")).mkString
          s"${ind(i)}override def $n$tps$pss: ${tpe(ParentSubst.subst(d.returnTpt.tpe, psub))} = super[$sn].$n$as"
        }
      }

  /** JS-S25 — java REJECTS unreachable code and Scala allows it, composed with `break`.
    *
    * A body ending in java's `while(true){ … return … }` idiom never falls through, but Scala types
    * `while(true)` as `Unit`, so a non-Unit method needs a tail java did not have. One function
    * because the dispatch consults it and `defDef` renders from it: two derivations of one decision
    * is the F8 shape with a longer fuse. */
  private def needsUnreachableTail(d: Tree.DefDef): Boolean =
    sym(d.symbol).name != "<init>" && !isUnitType(d.returnTpt.tpe) && d.rhs.exists(endsInInfiniteLoop)

  private def defDef(d: Tree.DefDef, i: Int)(using Obligations): String =
    val s     = sym(d.symbol)
    val isCtor = s.name == "<init>"
    val name  = if isCtor then "this" else esc(s.name)
    // a Java generic constructor (`<T extends X> C(...)`) has no Scala form — secondary ctors can't
    // be generic. Drop the type params and substitute each with its upper bound throughout the ctor.
    val savedSubst = tparamSubst
    if isCtor && d.tparams.nonEmpty then
      tparamSubst = savedSubst ++ d.tparams.map(tp => tp.symbol -> (tp.rhs.tpe match
        case TypeRepr.TypeBounds(_, hi) if hi != TypeRepr.NoType => hi
        case _ => TypeRepr.NoType)).toMap // unbounded → `Any`
    val tps   = if isCtor || d.tparams.isEmpty then "" else "[" + d.tparams.map(typeParam).mkString(", ") + "]"
    val pss   = d.paramss.map(paramClause).mkString
    val ret   = if isCtor then "" else s": ${tpe(d.returnTpt.tpe)}"
    // a Java `while(true){ … return … }` idiom: the loop never falls through, but Scala types
    // `while(true)` as Unit, so a non-Unit method needs an unreachable tail after it.
    val needsUnreachable = needsUnreachableTail(d)
    val rhs = inDeclaration {
      if isCtor then s" = ${ctorBody(d, i)}"
      else d.rhs.map(r =>
        if needsUnreachable then s" = {\n${ind(i + 1)}${term(r, i + 1)}\n${ind(i + 1)}throw new java.lang.RuntimeException(\"unreachable\")\n${ind(i)}}"
        else s" = ${term(r, i)}").getOrElse("")
    }
    tparamSubst = savedSubst // restore (ctor type-param substitution was local to this def)
    // ORIGINAL TRIVIA FIRST, porter note LAST, member next. The note explains the port's own
    // decision and the trivia is the upstream's documentation (a licence among them, §4.58) — a
    // note above the Javadoc reads as part of it and displaces the thing the port is obliged to
    // reproduce, so the order here is a rule and not a preference.
    s"${leading(d.leading, i)}${declNotes(d.symbol, i)}${annots(s, i)}${ind(i)}${mods(s, privateQualifier(s.owner))}def $name$tps$pss$ret$rhs"

  /** does this loop body contain an unlabelled `break` that belongs to THIS loop?
    *
    * Stops descending at a nested loop or switch, since java's unlabelled `break` binds to the
    * innermost enclosing one — a `boundary` placed around the outer loop would otherwise catch a
    * break the inner construct owns. */
  /** Loop-jump scope, as scala `boundary` nesting.
    *
    * `break` leaves the loop and `continue` skips to the next iteration, so they need boundaries in
    * DIFFERENT places: one around the whole loop, one around the loop BODY. When a loop needs both,
    * the body boundary is the innermost, so an un-annotated `break(())` inside it would continue
    * rather than break — the outer one has to be NAMED and targeted explicitly
    * (`boundary { brk ?=> … break(())(using brk) }`, verified against scalac).
    *
    * `breakTarget`: `None` = no enclosing loop boundary, so a `break` here belongs to a SWITCH;
    * `Some("")` = an unnamed one is innermost; `Some(name)` = it must be named because another
    * boundary sits inside it. Re-pointed by `match` at the CASE's own boundary, since java's
    * `break` there ends the case — but `contTarget` is NOT, because a `continue` inside a switch
    * still continues the loop.
    *
    * `contTarget` reads the same way for the `continue` boundary. It became a name rather than a
    * flag when `Tree.Labeled` arrived: a `boundary` the emitter introduces for a LABELLED
    * statement sits between the loop's boundaries and any un-annotated `break(())` under it, and
    * `boundary.break` with no `using` binds to the INNERMOST `Label`. */
  private var breakTarget: Option[String] = scala.None
  private var contTarget: Option[String]  = scala.None
  private var labelSeq = 0
  /** names the `def` that carries a lambda body containing `return` — see the `Tree.Lambda` case.
    *
    * SCOPED TO ONE DECLARATION by [[inDeclaration]], never to the program. An emitted NAME keyed on
    * a program-global counter is `ENGINE-LIMITS.md` M10's defect exactly: the name of a construct in
    * one member then depends on how many of them exist in every member emitted before it, so a
    * change anywhere renumbers everything after it and `members.tsv` reports churn instead of blast
    * (M10 measured 122 of 135 rows that way). Measured here at 2 members on the libGDX base — the
    * conversions in `Cubemap` and `Pixmap` renamed `TextField`'s three `body$N` — which is small
    * only because the construct is rare, and it is the same defect at any size. */
  private var lambdaSeq = 0

  /** run `f` with the synthetic-name counters SAVED, reset, and restored.
    *
    * Save-and-restore rather than plain reset, because a declaration nests: an anonymous class's
    * method inside a lambda inside a member is its own scope and must not consume the enclosing
    * member's numbers. Two `def body$1` in different blocks are two different scopes and scala is
    * happy with both, which is what makes the LOCAL name the correct one rather than merely the
    * tidier one. */
  private def inDeclaration[A](f: => A): A =
    val saved = lambdaSeq
    lambdaSeq = 0
    try f finally lambdaSeq = saved
  private def inLoop[A](brk: Option[String], cont: Option[String])(f: => A): A =
    val (sb, sc) = (breakTarget, contTarget)
    breakTarget = brk; contTarget = cont
    try f finally { breakTarget = sb; contTarget = sc }
  private def inSwitch[A](brk: Option[String])(f: => A): A =
    val sb = breakTarget
    breakTarget = brk
    try f finally breakTarget = sb

  /** the value-carrying `Label` a non-tail `yield` must name — a switch EXPRESSION's arm boundary.
    *
    * Kept apart from [[breakTarget]] rather than folded into it, because the two are not the same
    * jump and cannot share a `Label`: a `break` carries `Unit` and a `yield` carries the switch
    * expression's own type, so one boundary cannot serve both. They also never coexist — JLS 15.28
    * forbids a `break`, `continue` or `return` whose target lies outside a switch expression, so a
    * switch-expression arm holds `yield`s and nothing else, and a switch STATEMENT's arm holds no
    * `yield` at all (JLS 14.21). ALWAYS named, for the reason `matchStr`'s break boundary is: the
    * jump is emitted `break(v)(using n)`, so nothing nearer can steal it. */
  private var yieldTarget: Option[String] = scala.None
  private def inYield[A](y: Option[String])(f: => A): A =
    val sy = yieldTarget
    yieldTarget = y
    try f finally yieldTarget = sy

  /** java LABEL -> the scala boundary name a `break`/`continue` naming it must target. A labelled
    * jump can sit at any depth, so unlike the unlabelled ones these are looked up, not scoped. */
  private val labelBreak = collection.mutable.Map[String, String]()
  private val labelCont  = collection.mutable.Map[String, String]()

  /** Render a loop with whatever boundaries its jumps need.
    *
    * Up to two: one around the LOOP for `break`, one around the BODY for `continue`. A loop needing
    * both must NAME the outer one — the body boundary is innermost, so an un-annotated `break(())`
    * inside it would continue instead. A LABELLED loop names whichever of the two its label is
    * actually jumped to, and registers the name for the duration of the body. */
  /** A java enhanced-for BINDING is a declaration with its own type; scala's `for (x <- xs)` binds at
    * the ITERABLE's element type. They agree in the ordinary case and java lets them differ:
    *
    * {{{ for (Object e : collection) if (!contains(e)) …   // Collection<?>, binding widened to Object }}}
    *
    * Java resolves every use of `e` in the body against `Object`; scala resolves them against the
    * element type, which for a wildcard is an unusable capture — so `contains(e)` fails with
    * `Found: ?1.CAP`. `Array.containsAll` and `NodeCollection.containsAll` in simple-graphs are
    * exactly this, and no amount of retyping at the collection fixes it: the loss is at the BINDING.
    *
    * Returns the declared type to re-bind at, or `None` when scala's own binding is already exact.
    *
    * Conservative in ONE direction on purpose. A difference must be PROVABLE — an element type this
    * function cannot read is treated as agreeing, because inventing an alias on no evidence would
    * add a cast to every for-each in the corpus to fix the handful that need one. The cast itself is
    * sound wherever it does fire: java only permits a WIDENING here, so the value already has the
    * declared type at runtime. */
  private def widenedBinding(b: Tree.ValDef, it: Term): Option[String] =
    elementTpe(it.tpe).filter(_ != b.tpt.tpe).map(_ => tpe(b.tpt.tpe))

  /** is the enhanced-for BINDING written to inside the loop body?
    *
    * Java's `for (Object obj : array)` binding is an ordinary local and may be assigned; Scala's
    * generator binds a `val`, so the same body reads `Reassignment to val obj`. The same java fact
    * `MutableParamsTransform` handles for a parameter, and read the same way — with
    * `StandardTraversal` rather than a private recursion (§3), and counting `IncDec` beside
    * `Assign` because `obj++` writes just as much as `obj = …` does.
    *
    * Scanning the whole body cannot produce a false positive: a symbol identifies its binder
    * uniquely, so an assignment to THIS symbol anywhere under the loop is an assignment to this
    * binding. Over-approximating would cost only a `var` where a `val` would do; under-approximating
    * costs a compile error, which is why the scan is total rather than a list of node kinds. */
  private def reassignsBinding(body: Tree, binding: SymId): Boolean =
    given Program = program
    body match
      case t: Term => StandardTraversal.scanTerm(t, false) { (found, x) =>
        x match
          case Tree.Assign(Tree.Ident(s, _, _), _, _, _) if s == binding    => true
          case Tree.IncDec(Tree.Ident(s, _, _), _, _, _, _) if s == binding => true
          case _                                                            => found
      }
      case _ => false

  /** the element type of something java could put in an enhanced-for: an applied generic's single
    * argument, or an array's element. `None` = not readable, which callers must treat as no evidence
    * rather than as a difference. */
  private def elementTpe(t: TypeRepr): Option[TypeRepr] = t match
    case TypeRepr.AppliedType(_, List(el)) => Some(el)
    case _                                 => scala.None

  private def loopWithJumps(body: Tree, label: Option[String], render: (=> String) => String,
                            bodyStr: => String)(using Obligations): String =
    val lblB = label.filter(l => jumpsTo(body, l, brk = true))
    val lblC = label.filter(l => jumpsTo(body, l, brk = false))
    val hasB = breaksOut(body) || lblB.isDefined
    val hasC = continuesIn(body) || lblC.isDefined
    // JS-S01 — java's unlabelled jump binds LEXICALLY to this loop and scala's `boundary.break`
    // binds to the innermost `Label` in implicit scope. Consulted at EVERY loop, whichever of the
    // four `Tree` kinds it is (they all arrive here); it FIRES where a jump really belongs to this
    // one, which is where a boundary has to exist for java's meaning to survive.
    Obligations.consult(JS.S(1), body.origin)(Option.when(hasB || hasC)(()))
    // JS-S03 — a boundary this emitter INTERPOSES steals the enclosing loop's un-annotated jumps,
    // because `boundary.break` with no `using` resolves the innermost `Label`. Consulted at EVERY
    // loop, beside JS-S01 and not down in the branch that emits a boundary: an obligation the arm
    // discharges only on some paths is an obligation the other paths report as a hole. `&&` keeps
    // the extra traversal off the loops that cannot be affected — with no jump there is nothing for
    // an interposed boundary to steal.
    val shielded = (hasB || hasC) && interposes(body)
    Obligations.consult(JS.S(3), body.origin)(Option.when(shielded)(()))
    if !hasB && !hasC then render(bodyStr)
    else
      labelSeq += 1
      val seq  = labelSeq
      // the break boundary must be named when a body boundary sits inside it, when a labelled
      // `break` names it from a nested loop, or when some construct INSIDE the body renders with a
      // boundary of its own (`interposes`) — all three put another `Label` nearer than this one.
      val bName = if hasB && (hasC || lblB.isDefined || shielded) then s"brk$$$seq" else ""
      val cName = if hasC && (lblC.isDefined || shielded) then s"cnt$$$seq" else ""
      lblB.foreach(l => labelBreak(l) = bName)
      lblC.foreach(l => labelCont(l) = cName)
      val inner =
        try inLoop(if hasB then Some(bName) else scala.None, if hasC then Some(cName) else scala.None) {
          if !hasC then bodyStr
          else if cName.isEmpty then s"scala.util.boundary { $bodyStr }"
          else s"scala.util.boundary { ($cName: scala.util.boundary.Label[scala.Unit]) ?=> $bodyStr }"
        }
        finally { lblB.foreach(labelBreak.remove); lblC.foreach(labelCont.remove) }
      val loop = render(inner)
      if !hasB then loop
      else if bName.isEmpty then s"scala.util.boundary { $loop }"
      else s"scala.util.boundary { ($bName: scala.util.boundary.Label[scala.Unit]) ?=> $loop }"

  /** does this loop body contain a construct the emitter renders with a `boundary` of ITS OWN?
    *
    * `scala.util.boundary.break(())` with no `using` resolves the innermost given `Label`, so any
    * boundary the emitter interposes between a loop and an un-annotated jump under it silently
    * retargets that jump. Two constructs do it: a [[Tree.Labeled]] that is actually broken to, and
    * a switch case with a mid-case `break` (see `matchStr`).
    *
    * Deliberately an OVER-approximation — it does not check that an unlabelled jump is really
    * underneath the interposed boundary. Naming a boundary nothing needed costs one identifier;
    * missing one is a silent control-flow change, which is the whole defect class of §4.4. Stops
    * at a nested loop, lambda, `def` or anonymous class for the same reason `breaksOut` does: a
    * jump there belongs to that construct, not to this loop. */
  private def interposes(t: Any): Boolean = t match
    case l: Tree.Labeled => labelNeedsBoundary(l) || interposes(l.stmt)
    case m: Tree.Match   =>
      interposes(m.scrutinee) ||
        m.cases.exists(c => caseNeedsBoundary(c.body) || (m.isExpr && caseYieldsOut(c.body)) ||
                            interposes(c.body))
    case _: Tree.While | _: Tree.DoWhile | _: Tree.For | _: Tree.ForEach     => false
    case _: Tree.Lambda | _: Tree.DefDef | _: Tree.AnonClass | _: Tree.ClassDef => false
    case xs: Iterable[?] => xs.exists(interposes)
    case Some(x)         => interposes(x)
    case p: Product      => p.productIterator.exists(interposes)
    case _               => false

  /** a labelled statement earns a boundary only when something actually breaks to its label —
    * java lets a label sit on a statement nobody jumps to, and an empty boundary would be noise
    * that also has to be shielded against. */
  private def labelNeedsBoundary(l: Tree.Labeled): Boolean = jumpsTo(l.stmt, l.name, brk = true)

  /** an unlabelled `break` in a switch case that is NOT the case terminator. The frontend strips a
    * trailing unlabelled `break` (it is what ends the case, which scala's `match` does anyway) and
    * lowers real fallthrough by duplicating the next case's tail — so a `break` still standing in
    * a case body means "stop HERE and leave the switch", over statements that follow it. */
  private def caseNeedsBoundary(body: Term): Boolean = breaksOut(body)

  /** a non-tail `yield` in a switch EXPRESSION's arm — the value-carrying twin of the predicate
    * above. The frontend peels the TAIL yield into the arm's value, so anything reaching this is a
    * `yield` that leaves the arm from inside an `if`, a nested block or ahead of another statement,
    * and scala has no expression-level jump to render it with. */
  private def caseYieldsOut(body: Term): Boolean = Jumps.yieldsOut(body)

  // The three predicates below say which construct a java jump BELONGS to. They live in
  // `balticporter.tir.Jumps` because the `break-catch` check has to ask the same questions of the
  // same trees (§4.4's jump-in-a-broad-catch row): two copies would be two answers, and the one
  // that is wrong is the one nothing measures.
  private def jumpsTo(t: Any, label: String, brk: Boolean): Boolean = Jumps.jumpsTo(t, label, brk)
  private def continuesIn(t: Any): Boolean = Jumps.continuesIn(t)

  /** does this subtree `return` from the construct that OWNS it?
    *
    * Stops at a nested `Lambda`, `DefDef` or anonymous-class body for the same reason `breaksOut`
    * stops at a nested loop: a `return` there belongs to that construct, not to this one. Product
    * reflection rather than a case per node — a hand-rolled walk that stops one node short is how two
    * of this project's silent defects survived (CLAUDE.md §3). */
  private def returnsIn(t: Any): Boolean = t match
    case _: Tree.Return                                   => true
    case _: Tree.Lambda | _: Tree.DefDef | _: Tree.AnonClass => false // binds to the inner one
    case xs: Iterable[?]                                  => xs.exists(returnsIn)
    case Some(x)                                          => returnsIn(x)
    case p: Product                                       => p.productIterator.exists(returnsIn)
    case _                                                => false

  /** the result type to give the `def` that carries a lambda body containing `return`.
    *
    * TWO SOURCES, and the order between them is the whole of `ENGINE-LIMITS.md` I9:
    *
    *   1. '''the node's own `resultTpt`''', where whoever built the lambda held the SAM METHOD and
    *      therefore its `returnTpt`. `SamLambdaTransform` does — it converts an anonymous class,
    *      whose single `DefDef` states the type java wrote. That is a FACT the program carries, not
    *      an inference, so it is read first and it is read whatever the body looks like;
    *   2. '''the body''', for the one case a body can decide alone: every `return` VALUELESS is a
    *      java `void` lambda. This is what the arm had before a node could carry (1), and it is
    *      exactly why M6's refusal used to be so wide — a value-returning lambda has no `void` to
    *      read and the interface's own type is not the method's.
    *
    * `None` still means '''do not rewrite''', never "use `Any`": a `def` with a guessed result type
    * COMPILES and means something else, where the refusal is a loud error naming the line (M6).
    * `OmissionCheck.unnameableLambdaReturn` counts what is left, so the refusal is a number rather
    * than a silence. */
  private def lambdaResultType(lam: Tree.Lambda): Option[String] =
    lam.resultTpt.map(t => tpe(t.tpe)).orElse {
      val valued = collectReturns(lam.body).exists(_.expr.isDefined)
      Option.when(!valued)("scala.Unit")
    }

  private def collectReturns(t: Any): List[Tree.Return] = t match
    case r: Tree.Return                                   => List(r)
    case _: Tree.Lambda | _: Tree.DefDef | _: Tree.AnonClass => Nil
    case xs: Iterable[?]                                  => xs.toList.flatMap(collectReturns)
    case Some(x)                                          => collectReturns(x)
    case p: Product                                       => p.productIterator.toList.flatMap(collectReturns)
    case _                                                => Nil

  private def breaksOut(t: Any): Boolean = Jumps.breaksOut(t)

  private def isUnitType(t: TypeRepr): Boolean = t match
    case TypeRepr.TypeRef(_, s) => sym(s).fullName == "scala.Unit"
    case _ => false
  /** the method body is (or ends in) an infinite `while(true)` / `for(;;)`. */
  private def endsInInfiniteLoop(t: Term): Boolean = Tree.uncomment(t) match
    // …unless it can BREAK out. Before `break` was emitted the loop really was infinite and the
    // unreachable tail was correct; now `boundary { while (true) … }` returns normally, and the
    // synthetic `throw` after it is reached on every exit.
    case Tree.While(Tree.Literal(Constant.BoolC(true), _, _), b, _, _, _) => !breaksOut(b)
    case Tree.For(_, None, b, _, _, _, _)                                 => !breaksOut(b)
    case Tree.Block(stats, e, _, _, _) =>
      endsInInfiniteLoop(e) || (e match {
        case Tree.Literal(Constant.UnitC, _, _) => stats.lastOption.collect { case x: Term => x }.exists(endsInInfiniteLoop)
        case _ => false
      })
    case _ => false

  /** A Scala secondary constructor must delegate to `this(...)` first — never `super(...)`.
    * Keep a Java `this(args)` delegation. A leading `super(args)` becomes `this()` followed by
    * the parent constructor's own statements, when `CtorFunnel` has established that the two
    * together run exactly what `super(args)` ran; where it has not, the arguments are still lost
    * and `OmissionCheck` still counts them. */
  private def ctorBody(cdef: Tree.DefDef, i: Int): String =
    val stats  = CtorFunnel.stmtsOf(cdef)
    val replay = currentClass.flatMap(plans.replayFor(_, cdef)).getOrElse(Nil)
    // the head is read THROUGH its comments, and the comments are re-emitted above the delegation
    // that replaces it — the call itself is consumed, but what somebody wrote about it is not.
    val headTrivia = stats.headOption.collect { case t: Term => Tree.triviaOn(t) }.getOrElse(Nil)
    val plan  = currentClass.map(plans(_)).getOrElse(CtorFunnel.Plan.none)
    // A ROOT of a SYNTHESISED primary delegates with the whole slot list — its own `super(args)`
    // first, then a value for each hoisted field — and the leading `this.f = e` statements those
    // field values came from are dropped, because the primary now performs them. `Plan.consumed`
    // and `Plan.delegations` are ONE derivation in `CtorFunnel`, so the statements the emitter
    // drops and the arguments it writes cannot disagree about which assignment went where.
    val headIsDelegation = CtorFunnel.headStmt(cdef) match
      case Some(Tree.Apply(Tree.Select(_, m, _, _), _, _, _, _)) => sym(m).name == "<init>"
      case _                                                     => false
    val after = if headIsDelegation then stats.tail else stats
    val eaten = plan.delegations.get(cdef.symbol).map(_ => plan.consumed.getOrElse(cdef.symbol, 0)).getOrElse(0)
    val (deleg, rest) = plan.delegations.get(cdef.symbol) match
      case Some(args) =>
        val extra = currentClass.zip(plan.marker).map(markerArg(_, _)).toList
        (s"this(${(args.map(term(_, i + 1)) ++ extra).mkString(", ")})", after.drop(eaten))
      case None => CtorFunnel.headStmt(cdef) match
        case Some(Tree.Apply(Tree.Select(r, m, _, _), args, _, _, _)) if sym(m).name == "<init>" =>
          val d = r match
            case _: Tree.Super => superDelegation(args, i + 1)
            case _             => s"this(${args.map(term(_, i + 1)).mkString(", ")})"
          (d, stats.tail)
        case _ => ("this()", stats)
    // §4.58 — a CONSUMED `this.f = e` does not disappear from the file, and neither does what
    // somebody wrote about it. Every field slot has N roots contributing to it by construction (a
    // synthesis needs two), so the comment rides THIS secondary's delegation, which is the one
    // place a reader looking at this constructor will find it. The funnel is the one place a
    // statement vanishes without a diff showing where it went, so it is pinned by a spec rather
    // than left to whichever harvest runs last.
    val eatenTrivia = after.take(eaten).collect { case t: Term => Tree.triviaOn(t) }.flatten
    // A10 / ENGINE-LIMITS C7 — PREFIX STRIP. Where this constructor ESCAPES the promotion (java
    // never ran the promoted body on its path) and its own body BEGINS with that body, the class
    // body has already run those statements by the time `this(…)` returns: emitting them again is
    // the duplication C7 measures, and deleting them is exact rather than approximate. The residual
    // comes from `Plans.residualBody`, which is the same function `promotionEscapes` subtracts, so
    // the emitter and the omission count cannot disagree about which paths still duplicate.
    val body  = currentClass.flatMap(plans.residualBody(_, cdef)).getOrElse(rest)
    val carried = (if rest eq stats then Nil else headTrivia) ++ eatenTrivia
    val head  = leading(carried, i + 1) + ind(i + 1) + deleg
    // …and the block's END-OF-BODY comments, which this rendering has to carry itself: it
    // reconstructs the braces from `stmtsOf`'s statement LIST rather than rendering the body
    // `Tree.Block`, so the slot on the block reaches no other path from here.
    val trail = CtorFunnel.trailingOf(cdef).map(triviaText(_, i + 1))
    val lines = (head :: (replay ++ body).map(stat(_, i + 1)).filter(_.trim.nonEmpty)) ++ trail
    s"{\n${joinStats(lines)}\n${ind(i)}}"

  /** A secondary constructor's `super(args)` — which scala cannot write — expressed as a
    * delegation to the PRIMARY, whose own `extends Parent(…)` makes the call.
    *
    * The DECISION is `CtorFunnel.Plans.superCall`; this only renders it. That split is the point:
    * `OmissionCheck` counts a `super(args)` as dropped exactly when the same call returns
    * `Dropped`, so the check cannot report zero for a constructor this method has just lowered to
    * a bare `this()`. It did, for as long as the planner asserted a class-wide flag instead. */
  /** the DISAMBIGUATOR's argument, when the class's primary takes one.
    *
    * ASCRIBED, never a bare `null`, and that is the difference between a marker that disambiguates
    * and one that does not. `null` inhabits every reference type, so `this(null)` against a class
    * that also declares `C(String)` is applicable to BOTH and scalac reports an ambiguous overload
    * — the very failure the marker exists to remove (`ENGINE-LIMITS.md` C8). `(null: C.Funnel)` has
    * exactly one applicable candidate, because nothing else declares a parameter of a type the
    * engine minted for this class alone. A real constructor whose corresponding parameter is
    * `Object` is applicable too and always LOSES most-specific to the marker's own type. */
  private def markerArg(cd: Tree.ClassDef, name: String): String =
    s"(null: ${typeValue(cd.symbol)}.${esc(name)})"

  private def superDelegation(args: List[Term], i: Int): String =
    currentClass.map(plans.superCall(_, args)).getOrElse(CtorFunnel.SuperCall.Dropped) match
      // a DISAMBIGUATED primary takes one more parameter than the slots, so the delegation writes
      // one more argument. `null` is the only value of a marker type and it inhabits nothing else,
      // which is precisely why the extra parameter removes the primary from every other
      // constructor's candidate set (`ENGINE-LIMITS.md` C8).
      case CtorFunnel.SuperCall.Positional(as) =>
        val extra = currentClass.flatMap(cc => plans(cc).marker.map(markerArg(cc, _))).toList
        s"this(${(as.map(term(_, i)) ++ extra).mkString(", ")})"
      case CtorFunnel.SuperCall.Matched(slots) =>
        val rendered = slots.map {
          case CtorFunnel.Slot.Arg(a)    => term(a, i)
          case CtorFunnel.Slot.NullAt(t) => s"null.asInstanceOf[${tpe(t)}]"
          // `Throwable(Throwable cause)` sets message = `cause == null ? null : cause.toString()`.
          // `Objects.toString(o, nullDefault)` IS that expression and evaluates `o` once, so the
          // argument needs no purity condition — a hand-written `if` would have read it twice.
          case CtorFunnel.Slot.CauseMessage(c) =>
            s"java.util.Objects.toString(${term(c, i)}, null)"
        }
        s"this(${rendered.mkString(", ")})"
      // the arguments really are lost here, and `OmissionCheck` says so on the same run
      case CtorFunnel.SuperCall.Dropped        => "this()"

  /** a parameter clause; a clause of `given` params renders as a Scala 3 `using` clause. */
  private def paramClause(ps: List[Tree.ValDef]): String =
    if ps.nonEmpty && ps.forall(p => sym(p.symbol).flags.isGiven) then s"(using ${ps.map(givenParam).mkString(", ")})"
    else s"(${ps.map(param).mkString(", ")})"

  /** A `using` parameter with NO NAME renders ANONYMOUSLY — `(using T)` — and that is not cosmetic.
    *
    * A context parameter named after an emitted root package SHADOWS it and breaks every qualified
    * reference in its scope, and this backend emits nothing but fully-qualified references (§6). The
    * reference hand port repaired two files away from named context parameters for exactly that
    * reason. Nothing reads the name: `using` resolution and `summon` never do.
    *
    * An empty name is otherwise impossible — the frontend gives every parameter Java's own name — so
    * this cannot capture a real one. */
  private def givenParam(v: Tree.ValDef): String =
    if sym(v.symbol).name.isEmpty then tpe(overrideAlign.getOrElse(v.symbol, v.tpt.tpe)) else param(v)

  // NOTE: Java `T...` → Scala `T*` is deferred — it also needs array-spread (`arr: _*`) at call
  // sites and overload-aware resolution, else `f(array)` calls break. Emitting the param type
  // as `Array[T]` keeps varargs callable positionally via the array.
  private def param(v: Tree.ValDef): String =
    // NO TYPE = a parameter a PHASE deliberately left for scalac to infer, and the one construct
    // that needs it is a LOWERED unbound method reference. Java writes such a qualifier RAW
    // (`Map.Entry::getKey`), so the retyped type renders `[?]` and annotating with it makes the
    // body an unusable capture — which is why this backend's own method-reference expansion emits
    // the receiver parameter bare too. Scalac takes the parameter from the expected function type,
    // which is java's own poly-expression rule.
    //
    // Nothing else may reach here with a `NoType`: every declaration the frontend builds carries
    // java's own type, so an unannotated `def` parameter would be a frontend defect and is a case
    // this arm cannot produce — a lambda parameter is the only `ValDef` a phase mints without one.
    val t = overrideAlign.getOrElse(v.symbol, v.tpt.tpe)
    if t == TypeRepr.NoType then esc(sym(v.symbol).name)
    else s"${esc(sym(v.symbol).name)}: ${tpe(t)}"

  private def valDef(v: Tree.ValDef, i: Int)(using Obligations): String =
    // JS-S19 — java's DEFINITE ASSIGNMENT (JLS 16) rejects a read before assignment; Scala requires
    // an initialiser instead, so a declaration java left blank has to be given one here. Consulted
    // at every `val`/`var`; it fires exactly where the emitter supplies a value java did not write,
    // which is where the two languages' rules actually diverge. The row stays `Partial`: the FIELD
    // half is what this branch closes and the LOCAL half is unexamined, so a consult that fired
    // would still be saying nothing about a local silently taking a default.
    Obligations.consult(JS.S(19), v.origin)(Option.when(v.rhs.isEmpty)(()))
    // …and the area-C rows a FIELD owes. `valDef` is the single arm a `Tree.ValDef` reaches, so
    // this IS the convergence point and there is no second place for them to be a hole.
    val vs      = sym(v.symbol)
    val ownerCd = program.definitionOf(vs.owner).collect { case c: Tree.ClassDef => c }
    // JS-C08 — a java CONSTANT VARIABLE is inlined by javac, so reading it triggers no class
    // initialiser; a typed `val` would. Fires exactly where `valDef0` renders `inline val`.
    Obligations.consult(JS.C(8), v.origin)(Option.when(v.rhs.isDefined && isJavaConstant(v, vs))(()))
    // JS-C36 — an interface field is implicitly `public static final`, so it is a companion member
    // of the emitted trait rather than one of its abstract members.
    Obligations.consult(JS.C(36), v.origin)(
      Option.when(vs.flags.isStatic && ownerCd.exists(c => sym(c.symbol).flags.isTrait))(()))
    // JS-C45 — a `final` field's safe-publication guarantee, carried by `val`. A FIELD, not a local:
    // the guarantee is about a construction the JMM freezes, and a local has no such moment.
    Obligations.consult(JS.C(45), v.origin)(Option.when(!vs.flags.isMutable && ownerCd.isDefined)(()))
    declVisibility(vs, v.origin)
    // trivia, then the porter note, then the `val` — see `defDef` for why that order is a rule.
    val note = declNotes(v.symbol, i)
    // …and the synthetic-name counters are this declaration's, for the reason `defDef`'s are.
    inDeclaration {
      if v.leading.nonEmpty then leading(v.leading, i) + note + valDef0(v.copy(leading = Nil), i)
      else note + valDef0(v, i)
    }

  private def valDef0(v: Tree.ValDef, i: Int): String =
    val s = sym(v.symbol)
    if s.flags.isGiven then
      // An EMPTY NAME renders an ANONYMOUS given — the same rule, and the same reason, as the
      // anonymous `using` parameter (`ENGINE-LIMITS.md` CT3): nothing reads a given's name, while a
      // name this engine minted into a class body is a name that can shadow an emitted root package,
      // and this backend emits nothing but fully-qualified references. An empty name is otherwise
      // impossible — the frontend gives every declaration java's own.
      val kw = if s.flags.isPrivate then "private given" else "given"
      val nm = if s.name.isEmpty then "" else s"${esc(s.name)}: "
      return s"${ind(i)}$kw $nm${tpe(v.tpt.tpe)}${v.rhs.map(r => s" = ${term(r, i)}").getOrElse("")}"
    // A FIELD SLOT — the constructor funnel hoisted this field's value into the synthesised
    // primary's parameter list, so the field binds that parameter and its java initialiser (which
    // every constructor overwrote) is gone. `val` where nothing else in the program writes it,
    // which is A1: slot-eligibility and single-write are two conditions, and `CtorFunnel` decides
    // the second one whole-program because a setter in another member is not visible from here.
    currentClass.flatMap(cc => plans(cc).fieldSlots.find(_.field == v.symbol)) match
      case Some(fs) =>
        val kw = if fs.mutable then "var" else "val"
        val q  = privateQualifier(s.owner)
        val m  = if kw == "var" then mods(s, q).replace("final ", "") else mods(s, q)
        return s"${ind(i)}$m$kw ${esc(s.name)}: ${tpe(v.tpt.tpe)} = ${fs.name}"
      case None => ()
    v.rhs match
      case Some(r) if isJavaConstant(v, s) =>
        // Java calls this a CONSTANT VARIABLE (JLS 4.12.4): `static final` of primitive or String
        // type with a constant initialiser. javac INLINES every use, so reading `Matrix4.M00` does
        // NOT trigger `Matrix4`'s class initialiser — which is the only reason libgdx's static
        // initialisers are not a cycle. `Vector3`'s creates a `Matrix4`, whose constructor reads
        // `Matrix4.M00`; emitted as an ordinary typed `val` that call initialises `Matrix4`, which
        // creates a `Vector3` that is still half-built, and the JVM throws
        // `ExceptionInInitializerError`. Scala's equivalent of the java rule is `inline val` —
        // note WITHOUT the type ascription, which would defeat the constant type.
        s"${ind(i)}${mods(s).replace("final ", "")}inline val ${esc(s.name)} = ${constAt(r, v.tpt.tpe)}"
      case Some(r) =>
        val kw = if s.flags.isMutable then "var" else "val"
        val q  = privateQualifier(s.owner)
        val m  = if kw == "var" then mods(s, q).replace("final ", "") else mods(s, q)
        s"${ind(i)}$m$kw ${esc(s.name)}: ${tpe(v.tpt.tpe)} = ${term(r, i)}"
      case None =>
        // An uninitialized Java field: a `var` placeholder so constructors can assign it (a bare
        // `val x: T` is an abstract member and won't compile in a class). `final var` is
        // contradictory in Scala, so `final` is dropped here.
        //
        // `scala.compiletime.uninitialized` is scala's own word for "the JVM default at this type",
        // which is exactly what java put there, and it is the residue A1 leaves: every field the
        // constructor funnel could NOT hoist into a slot keeps this line.
        //
        // IT REPLACES THE CAST AND NOTHING ELSE. `defaultFor` answers honestly for every type that
        // STATES a default — `0`/`false` for a primitive, and `null` for a `T | scala.Null`, which
        // is the whole point of the nullability phase's union — and only falls back to
        // `null.asInstanceOf[T]`, a cast in a position where nothing is being cast, on a value that
        // is not of the type it claims. So the substitution is keyed on that fallback rather than
        // applied to every uninitialised field: written unconditionally it silently took the union
        // default back off `NullabilitySpec`'s `var parent: demo.Actor | scala.Null = null`, which
        // is a rule this port is supposed to be RETIRING the cast for, not re-imposing it on.
        //
        // ONLY FOR A FIELD, and the gate is not a nicety: scalac's rule is "`uninitialized` can only
        // be used as the right hand side of a MUTABLE FIELD definition", and this same function
        // renders a method's LOCAL `var` too. Emitted without the gate it measured **0 -> 380
        // compile errors** on libGDX core, every one that message. The test is structural — the
        // symbol's owner is a class rather than a method — never the shape of the type.
        val fieldOfAClass = program.definitionOf(s.owner).exists(_.isInstanceOf[Tree.ClassDef])
        val stated = defaultFor(v.tpt.tpe)
        val blank  = if fieldOfAClass && stated.contains(".asInstanceOf[") then "scala.compiletime.uninitialized" else stated
        s"${ind(i)}${mods(s, privateQualifier(s.owner)).replace("final ", "")}var ${esc(s.name)}: ${tpe(v.tpt.tpe)} = $blank"

  /** the literal rendered AT the field's declared type.
    *
    * `inline val` takes its constant type from the literal, so the type ascription that would
    * normally carry it is rejected ("inline value must have a literal constant type"). Java's
    * `static private final float degFull = 360` therefore has to emit `360.0f`, not `360`: as an
    * `Int` constant it turned `SIN_COUNT / degFull` into INTEGER division and `MathUtils.cosDeg(90)`
    * returned -0.07 instead of 0. */
  private def constAt(r: Term, t: TypeRepr): String = (r, t) match
    case (Tree.Literal(c, _, _), TypeRepr.TypeRef(_, sy)) =>
      def num: Option[BigDecimal] = c match
        case Constant.ByteC(v)  => Some(BigDecimal(v.toInt)); case Constant.ShortC(v) => Some(BigDecimal(v.toInt))
        case Constant.IntC(v)   => Some(BigDecimal(v));       case Constant.LongC(v)  => Some(BigDecimal(v))
        case Constant.FloatC(v) => Some(BigDecimal(v.toDouble)); case Constant.DoubleC(v) => Some(BigDecimal(v))
        case Constant.CharC(v)  => Some(BigDecimal(v.toInt)); case _ => scala.None
      (sym(sy).fullName, num) match
        case ("scala.Float", Some(n))  => s"${n.toFloat}f"
        case ("scala.Double", Some(n)) => val d = n.toDouble; if d == d.toLong then s"$d" else d.toString
        case ("scala.Long", Some(n))   => s"${n.toLong}L"
        case ("scala.Short", Some(n))  => s"${n.toShort}"
        case ("scala.Byte", Some(n))   => s"${n.toByte}"
        case _                          => constant(c)
    case _ => term(r, 0)

  /** a java CONSTANT VARIABLE: `static final`, primitive or `String`, literal initialiser.
    *
    * `ClassInitTriggerCheck`'s and not a second copy — K22's safety argument is that the fields
    * this arm inlines are exactly the fields the class-init census does NOT count as step-9
    * content, and two spellings of one predicate is how that stops being true. */
  private def isJavaConstant(v: Tree.ValDef, s: Symbol): Boolean =
    balticporter.tir.ClassInitTriggerCheck.constantVariable(v, s)(using program)

  private def defaultFor(t: TypeRepr): String = t match
    // A union with `Null` STATES its own default, so the placeholder cast is not merely redundant
    // here — it is the thing the union was introduced to retire. `null.asInstanceOf[T | Null]`
    // compiles, and reads as an engine that could not express what the declaration already says.
    case TypeRepr.OrType(_, TypeRepr.TypeRef(_, s)) if sym(s).fullName == "scala.Null" => "null"
    case TypeRepr.TypeRef(_, s) => sym(s).fullName match
        case "scala.Int" | "scala.Short" | "scala.Byte" => "0"
        case "scala.Long"                               => "0L"
        case "scala.Float"                              => "0.0f"
        case "scala.Double"                             => "0.0"
        case "scala.Boolean"                            => "false"
        case "scala.Char"                               => "'\\u0000'"
        case "scala.Unit"                               => "()"
        case _                                          => s"null.asInstanceOf[${tpe(t)}]"
    case _ => s"null.asInstanceOf[${tpe(t)}]"

  /** A declaration's Java annotations, rendered ahead of it.
    *
    * FULLY QUALIFIED like every other reference this phase emits, so `@Test` becomes
    * `@org.junit.Test` and needs no import. Losing these is a silent correctness defect: a JUnit
    * suite whose `@Test` did not survive runs ZERO tests and reports SUCCESS. */
  private def annots(s: Symbol, i: Int): String =
    if s.annotations.isEmpty then ""
    else s.annotations.map { a =>
      val args = if a.args.isEmpty then ""
                 // Java's single-element `@A(x)` names its value `value`; Scala takes it positionally.
                 else if a.args.sizeIs == 1 && a.args.head._1 == "value" then s"(${term(a.args.head._2, i)})"
                 // …and a NAMED one goes through `esc` like every other identifier this emitter
                 // writes. Java's identifier space is not Scala's, and the first argument-bearing
                 // annotation the engine ever carried on a type names its element `using` — which
                 // is in this emitter's own keyword list, so the un-escaped form is a parse error
                 // in the middle of a declaration rather than anything a reader could attribute.
                 else s"(${a.args.map((k, v) => s"${esc(k)} = ${term(v, i)}").mkString(", ")})"
      s"${ind(i)}@${tpe(a.tpe)}$args\n"
    }.mkString

  /** The top-level type a symbol lives in, when it is NOT that type itself — i.e. the qualifier a
    * nested class's `private` member needs.
    *
    * Java scopes `private` to the enclosing TOP-LEVEL class: a nested class's private field is
    * readable by the outer class and vice versa, and the outer class's privates are readable from
    * the nested one. Scala's bare `private` is class-only, so the faithful rendering is
    * `private[TopLevel]`. Without it, an outer class reading its own nested class's field —
    * ordinary Java, and what Ashley's `PooledEngineTests` does — does not compile.
    *
    * Applied ONLY to a NESTED class's members. Qualifying a top-level class's own `private` was
    * tried and REGRESSED libGDX by one error: `GL30Interceptor.check` is private, so
    * `GL31Interceptor.check` overrides nothing — and `private[GL30Interceptor]` changed that, which
    * scala then demanded an `override` for. Java's `private` on a top-level class's member is
    * already exactly scala's, so widening it is not a no-op, it is a different program.
    *
    * Deriving the qualifier from the symbol's OWNER chain was tried first and returned nothing; the
    * class currently being rendered is the fact the emitter actually has. */
  private def privateQualifier(owner: SymId): Option[String] =
    Option.when(currentTopLevel.nonEmpty && currentOwnerSym != currentTopLevelSym)(currentTopLevel)

  /** The ACCESS modifier alone — [[Visibility]] decided the level, this supplies the qualifier.
    *
    * The two package-scoped levels take [[currentPkgTail]], which is the package the emitter is
    * writing into right now; only a cross-package override carries a package of its own, and even
    * then it is an ENCLOSING one, so its last segment is a name in scope here. `esc` because a
    * package segment is an ordinary Java identifier and Scala has more keywords than Java does. */
  private def vis(s: Symbol, privateIn: Option[String]): String =
    visPlan.getOrElse(s.id, Visibility.Vis.Public) match
      case Visibility.Vis.Public         => ""
      case Visibility.Vis.Private        => privateIn.fold("private ")(o => s"private[$o] ")
      case Visibility.Vis.PackagePrivate => s"private[${esc(currentPkgTail)}] "
      case Visibility.Vis.ProtectedPkg   => s"protected[${esc(currentPkgTail)}] "
      case Visibility.Vis.PrivateAt(q)   => s"private[${esc(TirEmitter.tailSegment(q))}] "
      case Visibility.Vis.ProtectedAt(q) => s"protected[${esc(TirEmitter.tailSegment(q))}] "

  private def mods(s: Symbol, privateIn: Option[String] = scala.None): String =
    val f = s.flags
    // `private override` is illegal in scala, and the pair is contradictory: a java `private`
    // method is invisible to subclasses, so it overrides nothing — a name/arity agreement with an
    // inherited member is coincidence. That is true of BOTH renderings of java `private` (bare, and
    // `private[TopLevel]` for a nested class's member, which is java's own scope for it) and it is
    // NOT true of package-private, which does override within its package and NEEDS the keyword.
    // So the rule is scoped to the LEVEL and not to the presence of a qualifier.
    val javaPrivate = visPlan.get(s.id).contains(Visibility.Vis.Private)
    val parts = List(
      vis(s, privateIn),
      if f.isOverride && !javaPrivate then "override " else "",
      if f.isFinal then "final " else "",
      // NOT `sealed`, and the omission is the rule. `Flags.isSealed` is java's raw modifier
      // (`SpoonTir.typeFlags`) and java's seal is not scala's — one names its subclasses anywhere
      // in the module, the other contains them in a file — so whether the keyword may be written
      // is a question about where the subtypes LAND. [[sealOf]] is the one place that asks it, and
      // a second, flag-shaped answer here would emit `sealed` at every hierarchy the first one
      // refused. This line existed and was dead for as long as nothing populated the flag.
      if f.isImplicit then "implicit " else "",
      if f.isLazy then "lazy " else "",
    )
    parts.mkString

  // ---- terms ----
  /** true when an `Ident`'s symbol is actually a TYPE used as a value (a static-access
    * receiver like `Float.compare`) — those must render as the (qualified) type name. */
  private def isTypeRef(id: SymId): Boolean = program.definitionOf(id) match
    case Some(_: Tree.ClassDef) => true
    case Some(_)                => false
    case None                   => val f = sym(id).fullName; f.contains('.') && !f.contains('#') && sym(id).info == TypeRepr.NoType

  /** a type used as a VALUE (static-access receiver) — dotted path, never a `#` projection
    * (which is type-position-only syntax). */
  private def typeValue(id: SymId): String =
    val s = sym(id)
    // a static nested type lives in the companion `object`, so name it through the value path
    // `Outer.Inner` even from inside `Outer` (companion members aren't in the class's scope).
    if s.flags.isStatic && s.fullName.contains('$') then escPath(s.fullName).replace('$', '.')
    else if currentDeclared(id) || inheritedNested(s.owner) then esc(s.name)
    else escPath(s.fullName).replace('$', '.')

  /** a static member lives in the companion `object`; even inside its own class it must be
    * named `Owner.member`, since a Scala class doesn't see its companion's members unqualified. */
  private def staticRef(s: SymId): String =
    val sm = sym(s)
    if sm.flags.isStatic && sm.owner != SymId.None && program.symbolOf(sm.owner).exists(_.info.isInstanceOf[TypeRepr.TypeRef])
    then s"${typeValue(sm.owner)}.${esc(sm.name)}"
    else if shadowedByCompanionStatic(s) then s"this.${esc(sm.name)}"
    else local(s)

  /** Does a bare reference to this INSTANCE member collide with a static of the same name that the
    * enclosing companion carries or re-exports?
    *
    * `DepthShader.Config` inherits an instance field `defaultCullFace` and writes it bare, exactly
    * as Java did — but `object DepthShader` also holds a static `defaultCullFace`, and Scala reports
    * the bare name as ambiguous between the two. Java had no such clash: statics and instance
    * fields live in one namespace there, and the inherited instance field simply wins.
    *
    * `this.` says what Java meant. Decided from the TIR symbol — the reference resolves to an
    * instance member of an ANCESTOR — rather than from the frontend, which cannot see it: Spoon
    * does not resolve this reference to a `CtFieldWrite` under noClasspath at all. */
  private def shadowedByCompanionStatic(s: SymId): Boolean =
    val sm = sym(s)
    !sm.flags.isStatic && sm.owner != SymId.None && sm.info != TypeRepr.NoType &&
      !sm.info.isInstanceOf[TypeRepr.MethodType] && !sm.info.isInstanceOf[TypeRepr.PolyType] &&
      classStack.lastOption.exists { cur =>
        // an INHERITED member (declaring it here would shadow the static on its own)
        cur != sm.owner && ancestorsOf(cur).contains(sm.owner) &&
          classStack.exists(c => staticOwnersOf(c).contains(esc(sm.name)))
      }

  /** THE TERM RENDERING DISPATCH — the other half of §2.3(c)'s emitter surface.
    *
    * Not disjoint from [[stat]]: a `Term` reached as a statement is handed straight here, so ONE
    * node is rendered inside TWO scopes and every consult happens in the inner one. That is exactly
    * the delegation seam `Lowering.of` documents in the frontend, and it is why the scopes are
    * joined by NODE IDENTITY rather than by kind or origin — two different nodes of one kind on one
    * line are two obligations. */
  private def term(t: Term, i: Int): String =
    Rendering.of(TirKinds.of(t), t.origin, t)(termArm(t, i))

  private def termArm(t: Term, i: Int)(using Obligations): String = t match
    case Tree.Ident(s, _, _)            => if isTypeRef(s) then typeValue(s) else staticRef(s)
    case Tree.Literal(c, _, _)          => constant(c)
    case Tree.This(s, _, _)             => thisRef(s)
    case Tree.Super(_, _, _)            => "super"
    // A RECEIVER IS AN OPERAND: `.m` binds tighter than every control-flow expression and than
    // every operator, so `(c ? a : b).toString()` rendered from `term` reads
    // `if (c) a else b.toString()` — which parses, and calls the method on ONE BRANCH.
    case Tree.Select(q, s, _, _)        => s"${operand(q, i)}.${local(s)}"
    case Tree.New(tpt, _, _, anon)      => s"new ${ctorTpe(tpt.tpe)}${anonBody(anon, i)}"
    case a @ Tree.Apply(fun, args, _, _, _) =>
      // JS-C06 — `anInstance.staticMethod()` evaluates the receiver FOR ITS SIDE EFFECTS and
      // discards it. A companion call has no receiver slot to put the expression in, so the
      // emitter has to keep the evaluation somewhere. Consulted at the dispatch's own `case`,
      // above `applyStr`'s six arms, so no arm can be the only place it is asked.
      Obligations.consult(JS.C(6), a.origin)(Option.when(fun match
        case Tree.Select(recv, m, _, _) => staticThroughInstance(recv, m)
        case _                          => false)(()))
      // JS-C22 and JS-C23 — java resolves an overload in THREE PHASES and scala in ONE, and the
      // decision this arm takes is to RENDER THE CALL AS JAVA WROTE IT: the port names the member
      // javac bound, and which member scalac then binds is not modelled (`ENGINE-LIMITS.md` T17 —
      // that is a resolver, and a compiler-sized one). So both rows are consulted, never fired into
      // a rewrite, and `OverloadRiskCheck` is the count beside them — through the same predicate and
      // the same index, so the obligation and the number cannot disagree about which calls the rows
      // are about. Two rows and not one because the JLS clauses are two: the PHASES (15.12.2) and
      // the most-specific tie-break inside a phase (15.12.2.5).
      locally {
        // …with the class the call is written IN, which the check's own walk derives from its
        // traversal and this one already holds: a bare `Ident` carries no receiver, and the
        // candidate set java used is the ENCLOSING type's (see `OverloadRiskCheck.rootOf`).
        // Without it the emitter's consult and the count would answer differently about the same
        // call, which is the one thing the shared predicate exists to prevent.
        val risks = balticporter.tir.OverloadRiskCheck
          .risks(a, overloads, classStack.lastOption.getOrElse(balticporter.tir.SymId.None))(using program)
        Obligations.consult(JS.C(22), a.origin)(
          Option.when(risks.exists(_.issue != balticporter.tir.OverloadRiskCheck.Issue.GenericTieBreak))(()))
        Obligations.consult(JS.C(23), a.origin)(
          Option.when(risks.exists(_.issue == balticporter.tir.OverloadRiskCheck.Issue.GenericTieBreak))(()))
      }
      // JS-G39, the EMITTER half — an external callee's `T...` is a class file's, which scalac reads
      // as a REPEATED parameter, so the pack becomes the argument list's TAIL rather than one
      // argument. HERE and not at a `Rendered("Repeated")`, because `argTerms` flattens the node
      // before the dispatch would ever see it (see the row's own note): a `Tree.Repeated` in an
      // argument position never enters `term`, so an arm there could neither be consulted nor
      // reported as a hole. The decision belongs where the flattening is.
      Obligations.consult(JS.G(39), a.origin)(
        Option.when(args.exists(_.isInstanceOf[Tree.Repeated]))(()))
      applyStr(fun, args, i)
    case Tree.TypeApply(fun, targs, _, _) => s"${term(fun, i)}[${targs.map(a => tpe(a.tpe)).mkString(", ")}]"
    case Tree.Assign(l, r, _, _)        => s"${term(l, i)} = ${term(r, i)}"
    case Tree.Block(stats, expr, _, _, tr) => block(stats, expr, tr, i)
    case lam @ Tree.Lambda(ps, body, _, _, _) =>
      val head = s"(${ps.map(param).mkString(", ")}) => "
      // A java LAMBDA BODY IS A METHOD BODY, so `return` is legal in it and means "leave the
      // lambda". Scala's lambda is an expression and rejects `return` outright — `return outside
      // method definition`. A NESTED `def` restores java's meaning exactly, because a scala `def`
      // is the one construct a local `return` does belong to; no `boundary` is needed and none is
      // as faithful, since `boundary`/`break` inside a body that also contains a loop would have to
      // be named to avoid breaking the LOOP instead (CLAUDE.md §4.4's "name the outer one when
      // both") and a `def` cannot be captured by the wrong construct at all.
      //
      // A new member of the §4.4 family, found the way the other ten were — by porting a test
      // suite, not by compiling the library (`AlgorithmsTest`, a `SearchProcessor` that returns
      // early to prune a search).
      // JS-S21 — a `return` in a java lambda BODY leaves the LAMBDA (JLS 15.27.2); scala's lambda is
      // an expression and rejects `return` outright, and a non-local return would leave the
      // enclosing METHOD. Consulted at every lambda, fires where a `return` really occurs.
      Obligations.consult(JS.S(21), body.origin)(Option.when(returnsIn(body))(()))
      if !returnsIn(body) then head + term(body, i)
      else lambdaResultType(lam) match
        case Some(rt) =>
          lambdaSeq += 1
          val n = s"body$$$lambdaSeq"
          head + s"{ def $n(): $rt = ${term(body, i)}; $n() }"
        // REFUSED rather than guessed, and NARROWED (`ENGINE-LIMITS.md` I9): the `def` needs the SAM
        // METHOD's result type, and a lambda the SOURCE wrote carries no method for anything to read
        // it off — javac inferred it from the target type, which is a class file the emitter is not
        // the place to open. A lambda a PHASE built from an anonymous class does carry one, and takes
        // the arm above. Left alone this is a loud compile error naming the exact line — the right
        // outcome per M6, and strictly better than a `def` with a guessed result type that compiles.
        // Counted by `OmissionCheck.unnameableLambdaReturn`, so the residue is a number.
        case None => head + term(body, i)
    case Tree.If(c, th, el, _, _)       => s"if (${term(c, i)}) ${term(th, i)} else ${term(el, i)}"
    // A cast ON A POLY EXPRESSION is an ASCRIPTION — `ENGINE-LIMITS.md` K17 face 1, at the one
    // shape the frontend's answer to it cannot reach. `SpoonTir.polyExpression` stops the ENGINE
    // casting a lambda; a cast the JAVA SOURCE wrote is kept on purpose, and java writes one
    // wherever the target does not determine the literal's type — an overload to disambiguate,
    // an `Object` or generic slot, a `return` of `Object`. Rendered `asInstanceOf` the literal
    // elaborates to a `scala.Function0` FIRST and the cast then asserts that a `Function0` is a
    // `Callable`, which it is not: the same ClassCastException, one syntax along.
    //
    // An ascription is what java's cast MEANT here — it supplies the expected type, which is
    // exactly what javac did with it — and scala SAM-converts at one (probed on 3.8.4 at a bare
    // slot, a wildcard-applied slot, a two-parameter one and a bare method name). `operand`
    // parenthesises the lambda, which is not cosmetic: `(x => y: T)` ascribes the BODY.
    //
    // ONE arm rather than two guarded ones, because the obligation below is owed per NODE and a
    // consult written into the first `case` would be a consult the second never takes — F8's shape
    // in a `match`. The two renderings are unchanged and the branch is the same predicate.
    // A METHOD-VALUE ASCRIPTION — `(recv.m: (A, B) => R)`, the shape that PINS which overload scala
    // binds, and the only thing the engine can do about `JS-C22`/`JS-C23` at a call
    // (`OverloadRiskCheck.AscribeJavacChoice`, minted by a port's own `resolutions` selection).
    //
    // Structurally unambiguous, which is why it is an arm and not a flag: JAVA HAS NO METHOD TYPES,
    // so a `Tree.Typed` whose target is a `MethodType` cannot be a cast the source wrote — the arm
    // below would render it `.asInstanceOf[(A, B) => R]`, which asserts something about the RECEIVER
    // and does not choose an overload at all. `tpe` already renders a `MethodType` as `(A, B) => R`,
    // so the phase mints the node and the emitter does every bit of the printing: a phase that wrote
    // the type as TEXT would be writing the UPSTREAM namespace into a file the rename has not
    // reached yet (§4.56).
    case ty @ Tree.Typed(e, tpt, _, _) if tpt.tpe.isInstanceOf[TypeRepr.MethodType] =>
      // …and the two rows a `Tree.Typed` OWES are discharged here as well, NOT FIRED. The obligation
      // is owed per NODE, so an arm that renders one and consults nothing is an `ENGINE GAP` in
      // `catalog(undischarged)` — which is exactly what this arm's first run reported (5 -> 7 on one
      // port). Both answers are `None` and both are facts rather than defaults: `JS-G34` is java's
      // INTERSECTION in a cast and `JS-E06` is java's UNBOXING CONVERSION, and this node is neither
      // — it is not a cast at all.
      Obligations.consult(JS.G(34), ty.origin)(scala.None)
      Obligations.consult(JS.E(6), ty.origin)(scala.None)
      s"(${operand(e, i)}: ${tpe(tpt.tpe)})"
    case ty @ Tree.Typed(e, tpt, _, _)  =>
      val target = castTarget(e, tpt.tpe)
      // JS-G34 — java's INTERSECTION in a cast (`(A & B) x`, JLS 4.9) becomes scala's `A & B`. Read
      // off the cast's own TARGET, which is the type this arm is about to render.
      Obligations.consult(JS.G(34), ty.origin)(Option.when(target.isInstanceOf[TypeRepr.AndType])(()))
      // JS-E06 — a cast to a PRIMITIVE over a WRAPPER of a DIFFERENT primitive is java's UNBOXING
      // CONVERSION (JLS 5.1.8 + 5.1.2) and `asInstanceOf` is an assertion that throws. The frontend
      // answers it from the type the operand has in the JAVA (`SpoonTir.castOf`), so a node still
      // carrying that shape HERE is one a later PHASE retyped — which is the residue the row's own
      // `Partial` names and the one nothing had ever counted. Consulted at the node the row is
      // about, and counted by `CastConversionCheck` through the same predicate, so the obligation
      // and the number cannot disagree about which casts the row is about.
      Obligations.consult(JS.E(6), ty.origin)(
        CastConversionCheck.crossTypeUnbox(ty)(using program).map(_ => ()))
      if polyOperand(e) then s"(${operand(e, i)}: ${tpe(target)})"
      else s"${operand(e, i)}.asInstanceOf[${tpe(target)}]" // Java cast
    // JS-G39 at the position `argTerms` does NOT reach — a `Tree.Repeated` outside an argument list
    // still stands for a sequence of its own and renders here. Recorded without being owed: the row
    // attaches at `Apply`, which is where the flattening decision is taken and the only place a
    // packed tail can be seen at all.
    case r @ Tree.Repeated(es, _, _)    =>
      Obligations.consult(JS.G(39), r.origin)(Some(()))
      es.map(term(_, i)).mkString(", ")
    // `xs*` — CLAUDE.md §6's spread, never `: _*`. `operand` because the array is an expression the
    // `*` binds tighter than: `(if (c) a else b)*` parses, `if (c) a else b*` does not.
    // JS-G40, the EMITTER half — JS-G38 composed with JS-G39: java already holds the array and
    // forwards it whole through an EXTERNAL `T...` slot, where a bare array would conform as ONE
    // element. `xs*`, never `: _*` (`CLAUDE.md` §6). Always fires, for the reason above.
    case s @ Tree.Spread(e, _, _)       =>
      Obligations.consult(JS.G(40), s.origin)(Some(()))
      s"${operand(e, i)}*"
    case Tree.Return(e, _, _)           => "return" + e.map(x => " " + term(x, i)).getOrElse("")
    case Tree.While(c, b, _, _, lbl)    =>
      loopWithJumps(b, lbl, bd => s"while (${term(c, i)}) $bd", term(b, i))
    case Tree.Throw(e, _, _)            => s"throw ${term(e, i)}"
    // …and the other three receiver positions, by the same rule and for the same misparse.
    // JS-G21 — java restricts `instanceof` to a REIFIABLE type (JLS 4.7), which is a NON-difference:
    // `isInstanceOf` tests the erased runtime class exactly as java's does, and javac already
    // rejected the unreifiable forms. What the row is `Partial` for is the OTHER half — SE16's
    // pattern BINDING, which has no representation at all (zero `CtTypePattern` hits, and
    // `SpoonKinds` records that refusal). Fires at every `instanceof`, which is the population the
    // reifiability question is asked of.
    case io @ Tree.InstanceOf(e, tpt, _, _) =>
      Obligations.consult(JS.G(21), io.origin)(Some(()))
      s"${operand(e, i)}.isInstanceOf[${tpe(tpt.tpe)}]"
    case Tree.ArrayAccess(a, idx, _, _) => s"${operand(a, i)}(${term(idx, i)})"
    // JS-G17 — java's `.length` is a FIELD of the array and scala's is a method; the row's other two
    // faces (an array's `Class` object, and `T[]::new`) are answered at the `MethodRef` arm, which
    // is why it attaches at both. Always fires: every array length is the difference.
    case al @ Tree.ArrayLength(a, _, _) =>
      Obligations.consult(JS.G(17), al.origin)(Some(()))
      s"${operand(a, i)}.length"
    case Tree.NewArray(el, dims, init, _, _) =>
      init match
        // `scala.Array`, fully qualified: a bare `Array` collides with libGDX's own
        // `com.badlogic.gdx.utils.Array` inside that package (same-package name resolution).
        case Some(es) => s"scala.Array[${tpe(el.tpe)}](${es.map(term(_, i)).mkString(", ")})"
        // Java `new T[a][b]` gives every dimension a size; Scala's `new Array` takes only ONE, so a
        // MULTI-dimension allocation lowers to `Array.ofDim[base](a, b)`. A single dim (incl. partial
        // `new T[a][]`) stays `new Array[elem](a)`.
        case None if dims.sizeIs > 1 => s"scala.Array.ofDim[${tpe(baseElem(el.tpe))}](${dims.map(term(_, i)).mkString(", ")})"
        case None     => s"new scala.Array[${tpe(el.tpe)}](${dims.map(term(_, i)).mkString(", ")})"
    case Tree.ForEach(b, it, body, _, _, lbl) =>
      val raw  = sym(b.symbol).name
      val name = esc(raw)
      // JS-S15 — java's enhanced-for evaluates the ITERABLE once, and arrays and `Iterable` differ.
      // Satisfied by construction here: the generator interpolates `term(it, …)` exactly once, so
      // the consult fires at every one of them and the row records that the branch really is the
      // single-evaluation shape rather than something a reader has to check.
      Obligations.consult(JS.S(15), it.origin)(Some(()))
      // TWO independent reasons to re-bind, and they compose into one alias (K7 + F16). The
      // DECLARED TYPE may differ from the iterable's element type, which java resolved every use
      // against; and the binding may be REASSIGNED in the body, which java permits on an ordinary
      // local and scala's generator — a `val` — does not (`Reassignment to val obj`). The second is
      // the same fact `MutableParamsTransform` handles for a parameter, one node kind out.
      val mutable = reassignsBinding(body, b.symbol)
      val kw      = if mutable then "var" else "val"
      // JS-S16 — the enhanced-for BINDING may be REASSIGNED in the body and may be DECLARED at a
      // supertype; a scala generator is a `val` of the element's own type and permits neither. Read
      // off the two decisions the emitter has just taken, never re-derived (§4.56).
      Obligations.consult(JS.S(16), b.origin)(Option.when(mutable || widenedBinding(b, it).isDefined)(()))
      // JS-G04 — a captured WILDCARD on iteration has no nameable type. Java relates the element it
      // reads and the collection it read it from as ONE capture; scala captures per use, so the
      // generator's binding cannot be written at the capture and the image is the same alias plus a
      // widening cast (`ENGINE-LIMITS.md` K7). It is the same repair JS-S16 takes, asked at the one
      // shape that has no scala NAME at all, so the predicate is the iterable's element being a
      // wildcard rather than the repair having fired.
      Obligations.consult(JS.G(4), b.origin)(Option.when(it.tpe match
        case TypeRepr.AppliedType(_, args) => args.exists(_.isInstanceOf[TypeRepr.TypeBounds])
        case _                             => false)(()))
      (widenedBinding(b, it), mutable) match
        case (None, false) => loopWithJumps(body, lbl, bd => s"for ($name <- ${term(it, i)}) $bd", term(body, i))
        case (widened, _) =>
          // the alias is INSIDE the loop body, so it is re-bound each iteration exactly as java's is,
          // and outside any `continue` boundary `loopWithJumps` adds — which is where java runs it.
          // A reassignment therefore cannot leak into the next iteration, which is java's semantics
          // exactly: java's binding is assigned afresh from the iterator each time round.
          // Derive the fresh name from the RAW one and escape THAT: appending to the escaped form
          // gives `` `object`$e ``, which is not an identifier at all (measured, 0 -> 3 on libGDX,
          // as an E040 syntax error). A suffixed keyword needs no escape, so `esc` is a no-op here —
          // but only because it is applied to the whole name.
          val fresh = esc(s"$raw$$e")
          // the CAST belongs to the widening and only to it: where the binding is re-bound purely
          // because java wrote to it, the generator already yields the declared type.
          val decl = widened.getOrElse(tpe(b.tpt.tpe))
          val rhs  = if widened.isDefined then s"$fresh.asInstanceOf[$decl]" else fresh
          loopWithJumps(body, lbl,
            bd => s"for ($fresh <- ${term(it, i)}) { $kw $name: $decl = $rhs; $bd }",
            term(body, i))
    case Tree.For(init, cond, upd, body, _, _, lbl) =>
      // the UPDATE must run on a `continue` too, so it sits OUTSIDE the per-iteration boundary —
      // which is exactly where java's `for` runs it.
      // ONE LINE, joined by `;` — so a comment must never reach here: a `//` would swallow the rest
      // of the loop header. The frontend does not wrap a `for`'s init/update for exactly that
      // reason; this strips any that a later phase introduced rather than emitting a broken file.
      val is = init.map(flatStat).mkString("; ")
      val c  = cond.map(term(_, i)).getOrElse("true")
      val u  = upd.map(flatStat).mkString("; ")
      // JS-S17 — java's classic `for` runs its UPDATE on a `continue` too, and scopes its `ForInit`
      // to the loop. `while` has neither clause, so both have to be PLACED: the update outside the
      // per-iteration boundary (where java runs it) and the init inside a block that ends with the
      // loop. It fires wherever there is anything to place.
      Obligations.consult(JS.S(17), body.origin)(Option.when(init.nonEmpty || upd.nonEmpty)(()))
      loopWithJumps(body, lbl, bd => s"{ $is; while ($c) { $bd; $u } }", term(body, i))
    case t: Tree.Try                    => tryStr(t, i)
    case m: Tree.Match                  => matchStr(m, i)
    case mr @ Tree.MethodRef(q, s, mrT, _) =>
      val isCtor = sym(s).name == "<init>" // `Type::new` → a factory function `() => new Type()`
      // JS-G43, the EMITTER half — five java forms share one syntax and each becomes a DIFFERENT
      // scala lambda. The discrimination is right here (`isCtor`, `Flags.isStatic`, and the array
      // element test below), so the row attaches at both surfaces: the frontend carries the
      // reference as a node, this arm chooses which of the five it is. Always fires.
      Obligations.consult(JS.G(43), mr.origin)(Some(()))
      // JS-G17's third face — `T[]::new` is an `IntFunction[T[]]` and not a no-arg supplier, because
      // a scala array needs a LENGTH. (`.length` is the arm above; the array's `Class` object rides
      // on the same row.) Fires only at the array-constructor form.
      Obligations.consult(JS.G(17), mr.origin)(Option.when(isCtor && (q match
        case Left(tt) => tt.tpe match
          case TypeRepr.AppliedType(TypeRepr.TypeRef(_, as), List(_)) => sym(as).fullName == "scala.Array"
          case _                                                     => false
        case _ => false))(()))
      // JS-G33 — SAM CONVERSION eligibility. `samAscribed` is the one place that asks it, and it is
      // reached from the constructor-reference and unbound-instance forms; the static form is a
      // qualified NAME and needs no conversion at all, which is what the negative half records.
      Obligations.consult(JS.G(33), mr.origin)(Option.when(q match
        case Left(_) => isCtor || !sym(s).flags.isStatic
        case Right(_) => true)(()))
      q match
        // an ARRAY constructor reference `T[]::new` is an `IntFunction[T[]]` — `(size) => new T[size]`
        // (Scala arrays need a length), NOT a no-arg supplier. One-layer element = the array's row type.
        // a constructor reference must name an INSTANTIABLE type: `new T[?]()` is rejected
        // ("type argument must be fully defined"), so route through `ctorTpe`, which drops
        // wildcard arguments and lets Scala infer them — and erase a wildcard array element to
        // `Object`, which is what Java's raw `T[]::new` means anyway.
        case Left(tt) if isCtor => tt.tpe match
          case TypeRepr.AppliedType(TypeRepr.TypeRef(_, as), List(el)) if sym(as).fullName == "scala.Array" =>
            val elem = el match
              case _: TypeRepr.TypeBounds => "java.lang.Object"
              case other                  => tpe(other)
            s"((size: scala.Int) => new scala.Array[$elem](size))"
          case _ => samAscribed(s"(() => new ${ctorTpe(tt.tpe)}())", mrT, tt.tpe)
        // `Type::method` is TWO different java forms sharing one syntax, and only one of them is a
        // qualified name. For a STATIC method it is `Type.method`. For an INSTANCE method it is an
        // UNBOUND reference — the receiver becomes the function's first parameter, so
        // `Edge<V>::getWeight` means `(self: Edge[V]) => self.getWeight()`. Emitted as a name it is
        // `sge.graphs.Edge[V].getWeight`, which is not even a member access: measured as
        // `value Edge is not a member of sge.graphs` in simple-graphs' MinimumWeightSpanningTree,
        // where the reference is a `Comparator` key extractor.
        case Left(tt) if sym(s).flags.isStatic => s"${tpe(tt.tpe)}.${local(s)}"
        case Left(tt) =>
          val self  = "self$"
          val as    = methodParams(s).indices.map(k => s"a$k$$").mkString(", ")
          // The receiver parameter is ANNOTATED only when the qualifier names a real type. A RAW
          // qualifier renders `[?]`, and annotating with it is worse than saying nothing: java's
          // `Comparator.comparing(Edge::getA)` takes its meaning entirely from the TARGET
          // (`Comparator<Connection<V>>` pins the input, which pins `getA()`'s result to something
          // `Comparable`), and writing `self$: Edge[?]` instead makes `getA()` return an unusable
          // capture — `Found: self.V / Required: U where U <: Comparable[? >: U]`.
          //
          // That is the poly-expression rule this frontend already follows elsewhere: a method
          // reference takes its type FROM the target, which is why `uncheckedGeneric` refuses to cast
          // one. Leaving the parameter un-annotated hands scala the same job javac had.
          val recvT = if hasWildcardArg(tt.tpe) then "" else s": ${tpe(tt.tpe)}"
          val extra = methodParams(s).zipWithIndex.map((pt, k) =>
            if recvT.isEmpty then s"a$k$$" else s"a$k$$: ${tpe(pt)}")
          val ps    = (s"$self$recvT" :: extra).mkString(", ")
          samAscribed(s"(($ps) => $self.${local(s)}($as))", mrT, tt.tpe)
        case Right(e)           => s"${term(e, i)}.${local(s)}"
    // Java's `break` leaves the loop; emitted as a no-op it did NOT, and the loop ran on.
    // `CharArray.deleteAll` scanned to the end of the array instead of stopping at the first
    // non-matching char and deleted most of the string. 290 sites, 73 files, all compiling.
    // Scala 3's `boundary`/`break` is the faithful shape, and is what the reference port uses.
    // A LABELLED break reaches the same shape through `Tree.Labeled` (a label on any statement)
    // or the loop's own `label` field (a label on a loop), and the residue count is what is left.
    case Tree.Break(scala.None, _, _) if breakTarget.isDefined =>
      breakTarget.filter(_.nonEmpty) match
        case Some(n) => s"scala.util.boundary.break(())(using $n)" // another boundary sits inside
        case _       => "scala.util.boundary.break(())"
    case Tree.Break(Some(l), _, _) if labelBreak.contains(l) =>
      val n = labelBreak(l)
      if n.isEmpty then "scala.util.boundary.break(())" else s"scala.util.boundary.break(())(using $n)"
    // an unlabelled `break` with no boundary around it belongs to a SWITCH and is the case's
    // TERMINATOR, which scala's `match` does anyway — the frontend has already removed those, so
    // one reaching here is a form neither this emitter nor the frontend recognises. Say WHICH, so
    // the residue count is a diagnosis and not a tally (CLAUDE.md §4.45).
    case b @ Tree.Break(scala.None, _, _)   =>
      unrenderable("break", "no enclosing loop or switch, and the frontend did not recognise it as " +
        "a switch-case terminator", "give the enclosing construct a `boundary`, or teach the " +
        "frontend this jump's shape", b.origin, "/* break: no enclosing loop or switch */ ()")
    case b @ Tree.Break(Some(l), _, _)      =>
      unrenderable("break", s"labelled `break $l` whose label is not in scope at this point",
        s"the labelled statement `$l` needs a NAMED boundary (§4.4); check `Tree.Labeled` reached it",
        b.origin, s"/* break $l: label not in scope */ ()")
    // A NON-TAIL `yield` (JLS 14.21) — the switch expression's arm is left with this value from
    // wherever the `yield` stands. `matchStr` has put a value-carrying `boundary` around the arm and
    // named it; the `using` is explicit for §4.4's reason, so nothing that nests inside the arm can
    // steal the jump. A TAIL yield never reaches here: it is the arm's value and the frontend peels
    // it into the arm block's result term.
    // `case String s ->` — a java TYPE PATTERN as a case label (JLS 14.11.1). Scala's typed pattern
    // is the exact image and needs no help: it binds, it narrows, and it composes with the `if`
    // guard `matchStr` already renders. Valid only in a label position, which is where the frontend
    // mints it; anywhere else this text is not an expression and scalac says so.
    case Tree.TypePattern(b, tpt, _, _) => s"${local(b)}: ${tpe(tpt.tpe)}"
    // `case Point(x, y)` — java's RECORD PATTERN through the extractor `JS-C43` derives over the
    // record's ACCESSORS, which is what JLS 14.30.1 reads. Named through `typeValue` — the
    // COMPANION's value path, where the `unapply` is — and never through `tpe`, which would give the
    // TYPE and, for a static nested record, a projection no extractor lives at.
    case Tree.RecordPattern(tpt, ps, _, _) =>
      val nm = headSymOf(tpt.tpe).map(typeValue).getOrElse(tpe(tpt.tpe))
      s"$nm(${ps.map(term(_, i)).mkString(", ")})"
    // …and an UNCONDITIONAL component: the binding alone. See `Tree.BindPattern` — a type test here
    // would be a different program at a `null` component.
    case Tree.BindPattern(b, _, _)      => local(b)
    case Tree.Yield(v, _, _) if yieldTarget.isDefined =>
      s"scala.util.boundary.break(${term(v, i)})(using ${yieldTarget.get})"
    case y @ Tree.Yield(v, _, _) =>
      unrenderable("yield", "no enclosing switch EXPRESSION arm — a `yield` outside one is not a " +
        "java construct (JLS 14.21), so the tree was built by something other than the switch arm",
        "check that the node was minted by `SpoonTir`'s switch-expression arm; a tail `yield` must " +
        "be peeled into the arm's value rather than carried as a node",
        y.origin, s"/* yield: no enclosing switch expression */ ${term(v, i)}")
    case Tree.Continue(scala.None, _, _) if contTarget.isDefined =>
      contTarget.filter(_.nonEmpty) match
        case Some(n) => s"scala.util.boundary.break(())(using $n)"
        case _       => "scala.util.boundary.break(())"
    case Tree.Continue(Some(l), _, _) if labelCont.contains(l) =>
      val n = labelCont(l)
      if n.isEmpty then "scala.util.boundary.break(())" else s"scala.util.boundary.break(())(using $n)"
    case c @ Tree.Continue(scala.None, _, _) =>
      unrenderable("continue", "no enclosing loop",
        "the loop BODY needs a `boundary` (§4.4); check which construct swallowed it",
        c.origin, "/* continue: no enclosing loop */ ()")
    case c @ Tree.Continue(Some(l), _, _)    =>
      unrenderable("continue", s"labelled `continue $l` whose label is not in scope at this point",
        s"the labelled loop `$l` needs a NAMED boundary around its body (§4.4)",
        c.origin, s"/* continue $l: label not in scope */ ()")
    // `name: stmt` — java's label on a NON-loop statement. `break name` leaves exactly that
    // statement, so the boundary goes around the STATEMENT (CLAUDE.md §4.4). Always named: a
    // labelled jump crosses nested loops and switches by definition, and anything the statement
    // contains may open a nearer `Label`.
    case Tree.Labeled(name, s, _, _) =>
      // JS-S02 — java's label sits on ANY statement (JLS 14.7) and `break L` leaves exactly that
      // statement; scala has no labelled statement, so the image is a NAMED boundary around it.
      // Fires where something really breaks to the label: java lets a label sit on a statement
      // nobody jumps to, and an empty boundary would be noise that then has to be shielded against.
      Obligations.consult(JS.S(2), s.origin)(Option.when(jumpsTo(s, name, brk = true))(()))
      if !jumpsTo(s, name, brk = true) then term(s, i) // a label nobody breaks to is not control flow
      else
        labelSeq += 1
        val n     = s"lbl$$$labelSeq"
        val saved = labelBreak.get(name)
        labelBreak(name) = n
        val inner =
          try term(s, i)
          finally saved match { case Some(v) => labelBreak(name) = v; case _ => labelBreak.remove(name) }
        s"scala.util.boundary { ($n: scala.util.boundary.Label[scala.Unit]) ?=> $inner }"
    case Tree.Assert(c, m, _, _)        => s"assert(${term(c, i)}${m.map(x => ", " + term(x, i)).getOrElse("")})"
    // Java's POST-increment yields the value BEFORE the update; the pre-form yields it after.
    // Rendered identically, `values[tail++] = object` stored at the NEW index — every circular
    // buffer in the corpus was off by one, `Queue.indexOf` among them, and it compiled. The
    // temporary is what makes the post-form exact; the target is still re-evaluated for the
    // assignment, exactly as the pre-form already did.
    case Tree.IncDec(tgt, op, post, _, _) =>
      if post then s"{ val ${'$'}prev = ${term(tgt, i)}; ${term(tgt, i)} $op= 1; ${'$'}prev }"
      else s"{ ${term(tgt, i)} $op= 1; ${term(tgt, i)} }"
    case Tree.DoWhile(b, c, _, _, lbl)  => // Scala 3 has no do-while
      // JS-S18 — Scala 3 REMOVED `do`-`while`, so there is no counterpart keyword and the body must
      // be lifted into the condition. Always fires: every `do` in java needs the image, which is
      // what makes this row's `Both` honest — the frontend chose the node and this chooses the text.
      Obligations.consult(JS.S(18), b.origin)(Some(()))
      loopWithJumps(b, lbl, bd => s"while ({ $bd; ${term(c, i)} }) ()", term(b, i))
    case Tree.Synchronized(l, b, _, _)  => s"${term(l, i)}.synchronized ${term(b, i)}"
    // An EXPRESSION position, where a comment cannot be rendered safely: a `//` would comment out
    // the rest of the line and a `/* */` would sit in the middle of a term. The frontend only ever
    // wraps a STATEMENT (`SpoonTir.withTrivia`), and `stat` handles that case above, so this is
    // reached only if a phase moves a wrapped statement into an operand — the statement is emitted,
    // the comment is not, and `TriviaCheck` reports the loss rather than the file being broken.
    case Tree.Commented(_, s)           => term(s, i)
    // Ready-made Scala, with any HOLES rendered as terms. The closed form (`holes = Nil`) is
    // `raw` verbatim and no scan runs over it — see `Tree.Opaque`.
    case o: Tree.Opaque                 => o.spliced(h => spliceOperand(h, i))
    // THE MARKER (`DESIGN.md` §6.2/§6.4). A RESOLVED one renders as its inner and nothing else: a
    // phase answered it, and a record of work done is not a residue. An OPEN one never ships.
    case m: Tree.Unportable             => unportable(m, i)

  /** Render a marker. `Open` is the case that matters and there are exactly two answers:
    *
    *   - '''best effort''' — the approximation inside deterministic comment fences, so an operator
    *     can read the whole file and see precisely which regions are wrong. A comment cannot change
    *     program shape, which is what makes the fence admissible at all;
    *   - '''anything else, including the shipping default''' — `scala.compiletime.error`. The
    *     orchestrator's gate (§6.4) means a deliverable run never reaches this branch, because the
    *     tree is not written at all; what reaches it is an emitter with no orchestrator around it,
    *     which is every testkit fixture. So the DEFAULT is the loudest available answer rather than
    *     the quietest, which is the opposite of `unrenderable`'s default and deliberately so: that
    *     one degrades an expression the engine can still spell, this one stands where the engine
    *     has nothing to say.
    *
    * Either way the refusal is RECORDED, as `Decision.Kind.Unrenderable` — the same kind
    * `unrenderable` uses and for the same reader's question. §2.6's reconciliation, taken: the
    * marker's own `UnportableKind` and catalog id carry what a second decision kind would have
    * carried, and two kinds whose one-line descriptions are indistinguishable is how a decision log
    * stops being classifiable. */
  private def unportable(m: Tree.Unportable, i: Int): String =
    m.state match
      case MarkerState.Resolved(_, _) => term(m.inner, i)
      case MarkerState.Open =>
        val d = Decision(
          kind       = Decision.Kind.Unrenderable,
          subject    = currentOwnerSym,
          subjectFqn = if currentOwnerSym == SymId.None then currentUnitName else sym(currentOwnerSym).fullName,
          detail     = Map("construct" -> m.kind.label, "why" -> m.what,
            "action" -> m.kind.remedies.headOption.map(_.what).getOrElse("no remedy is recorded for this kind"))
            ++ m.diff.map(dd => "catalog" -> dd.toString),
          reason = Reason.Universal(s"unportable/${m.kind.slug}"),
          origin = m.origin,
        )
        emissionOf += d
        printedNotes += PorterNote.Printed(d.kind, d.subject, d.subjectFqn, currentUnitName)
        recordedMarkers += m
        if bestEffort then
          val (openF, closeF) = Tree.Unportable.fence(m)
          s"$openF ${term(m.inner, i)} $closeF"
        else
          val msg = PorterNote.safe(s"balticporter: ${m.kind.label}: ${m.what}; " +
            m.kind.remedies.headOption.map(_.render).getOrElse("") + s"; origin ${m.origin.javaPath}:${m.origin.line}")
          PorterNote.render(d, "").stripSuffix("\n") + " " +
            "scala.compiletime.error(\"" + escape(msg) + "\")"

  /** every OPEN marker this emitter RENDERED — the input to the best-effort banner, and the
    * emitter's own half of the marker inventory. A value this emitter owns, exactly like the source
    * map and for the same reason (§5.1). */
  def renderedMarkers: List[Tree.Unportable] = recordedMarkers.toList
  private val recordedMarkers = collection.mutable.ListBuffer.empty[Tree.Unportable]

  /** A Java constructor reference (`Foo::new`) is typed by the TARGET functional interface Java
    * resolved, not by `Foo`. Emitted bare, `() => new Foo()` is a `Function0`, which Scala
    * SAM-converts to ANY single-abstract-method type — so an overload set offering two of them
    * (`PoolManager.addPool(Class, Pool)` vs `(Class, PoolSupplier)`) becomes AMBIGUOUS where
    * Java's was not. Re-state the resolved target as an ascription.
    *
    * Strictly guarded, because the ascription is only sound when the frontend really gave us the
    * functional interface: the type must be concrete (no type variables, no `NoType`) and must not
    * be the constructed type itself. Anything else falls back to the bare lambda — the previous
    * behaviour — so this can only ever narrow, never mis-type. */
  private def samAscribed(fn: String, target: TypeRepr, ctor: TypeRepr): String =
    def headOf(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s)      => Some(s)
      case TypeRepr.AppliedType(tc, _) => headOf(tc)
      case _                           => None
    def concrete(t: TypeRepr): Boolean = t match
      case TypeRepr.TypeRef(_, s)       => !sym(s).flags.isParam
      case TypeRepr.AppliedType(tc, as) => concrete(tc) && as.forall(arg)
      case _                            => false
    // a bare `?` is a legal type ARGUMENT (`PoolSupplier[Array[?]]`) though not a legal type
    def arg(t: TypeRepr): Boolean = t match
      case TypeRepr.TypeBounds(TypeRepr.NoType, TypeRepr.NoType) => true
      case other                                                 => concrete(other)
    val ok = concrete(target) && headOf(target) != headOf(ctor)
    if ok then s"($fn: ${tpe(target)})" else fn

  /** a STATIC member reached through an instance expression rather than through its own type. */
  private def staticThroughInstance(recv: Term, m: SymId): Boolean =
    val s = sym(m)
    s.flags.isStatic && s.owner != SymId.None && program.symbolOf(s.owner).isDefined && (recv match
      // already qualified by the owning TYPE — `Family.one(…)` — which is what we want to emit.
      case Tree.Ident(q, _, _)     => q != s.owner
      case Tree.Select(_, q, _, _) => q != s.owner
      case _                       => true)

  /** conservatively: can evaluating this term have an effect? Only shapes that provably cannot are
    * treated as free, because being wrong in the other direction DROPS an effect. */
  private def effectFree(t: Term): Boolean = t match
    case _: Tree.Ident | _: Tree.This | _: Tree.Literal => true
    case Tree.Select(q, _, _, _)                        => effectFree(q)
    case _                                              => false

  /** A `Tree.Repeated` in an ARGUMENT position is the argument list's TAIL, not one argument.
    *
    * The distinction is invisible for one element or more — the node renders comma-joined and the
    * enclosing `mkString(", ")` produces the same text either way — and decisive for ZERO, where a
    * node that renders `""` leaves a call reading `f(a, )`. Java's `f(a)` against `f(A, B...)` is
    * exactly that case (`Paths.get(".")`), so the empty spread is the normal one, not an edge.
    *
    * Flattened HERE rather than at the node, because it is a fact about the position: the same node
    * in any other position still stands for a sequence of its own. */
  private def argTerms(args: List[Term]): List[Term] =
    if !args.exists(_.isInstanceOf[Tree.Repeated]) then args
    else args.flatMap { case Tree.Repeated(es, _, _) => es; case a => List(a) }

  private def applyStr(fun: Term, argsIn: List[Term], i: Int): String =
    applyStr0(fun, argTerms(argsIn), i)

  private def applyStr0(fun: Term, args: List[Term], i: Int): String = fun match
    case Tree.New(tpt, _, _, anon) =>
      s"new ${ctorTpe(tpt.tpe)}(${args.map(term(_, i)).mkString(", ")})${anonBody(anon, i)}"
    // operators (populator tags them `scala.<op>#…`) render infix / prefix, not `.op(x)`.
    // …EXCEPT on a `super` receiver, where scala's grammar admits `super` only as the QUALIFIER of a
    // member selection. `super ++= m` is an E040 SYNTAX error — worse than any type error, since it
    // cannot be attributed to a member and can take the rest of the file with it — while
    // `super.++=(m)` is legal and is the only legal spelling of the same call. Reached when a class
    // that EXTENDS a retyped collection calls an inherited `addAll`/`putAll` through `super`.
    case Tree.Select(recv: Tree.Super, m, _, _) if sym(m).fullName.startsWith("scala.<op>#") =>
      s"${operand(recv, i)}.${esc(sym(m).name)}(${args.map(term(_, i)).mkString(", ")})"
    case Tree.Select(recv, m, _, _) if sym(m).fullName.startsWith("scala.<op>#") =>
      val op = sym(m).name
      if op.startsWith("unary_") then prefixOp(op.stripPrefix("unary_"), operand(recv, i))
      else s"${operand(recv, i)} $op ${args.map(operand(_, i)).mkString(", ")}"
    case Tree.Select(recv, m, _, _) if sym(m).name == "<init>" =>
      val kw = recv match { case _: Tree.Super => "super"; case _ => "this" }
      s"$kw(${args.map(term(_, i)).mkString(", ")})"
    // JAVA PERMITS A STATIC MEMBER TO BE CALLED THROUGH AN INSTANCE — `family.one(…)` where `one`
    // is `static`. Scala does not: a static emits into the companion, which an instance cannot
    // reach. Java evaluates the receiver and DISCARDS it, so the faithful rendering keeps the
    // receiver's effects and calls the static on its owner.
    //
    // A receiver that cannot have effects (a name, `this`, a field read) is simply dropped; one
    // that can (a call, a `new`) is evaluated first in a block, because silently discarding an
    // effect is precisely the class of defect a green compile hides (CLAUDE.md §4.4).
    //
    // Worked example: Ashley's `Family.all(A, B).get().one(C, D)` — `get()` returns a `Family` and
    // `one` is static on it, which javac accepts with a warning and scalac rejects outright.
    case Tree.Select(recv, m, _, _) if staticThroughInstance(recv, m) =>
      val call = s"${typeValue(sym(m).owner)}.${local(m)}(${args.map(term(_, i)).mkString(", ")})"
      if effectFree(recv) then call else s"{ ${term(recv, i)}; $call }"
    case Tree.Select(_, m, _, _) if numericOverloadAscription(m).isDefined =>
      s"(${term(fun, i)}: ${numericOverloadAscription(m).get})(${args.map(term(_, i)).mkString(", ")})"
    case _ =>
      // …through an ASCRIPTION, which wraps the callee without changing which member it is: a
      // `resolutions` selection that pinned this call must not take the raw-parent alignment with
      // it, and reading the symbol through the wrapper is how one rewrite stops disabling another.
      def callee(t: Term): Option[SymId] = t match
        case Tree.Select(_, m, _, _)    => Some(m)
        case Tree.Ident(m, _, _)        => Some(m)
        case Tree.Typed(inner, _, _, _) => callee(inner)
        case _                          => scala.None
      val as = callee(fun).flatMap(alignedArgs(_, args, i)).getOrElse(args.map(term(_, i)))
      s"${term(fun, i)}(${as.mkString(", ")})"

  /** widening rank — a value of rank r converts implicitly to any numeric type of higher rank.
    * `Char` and `Short` share a rank because neither widens to the other. */
  private val numericRank = Map("scala.Byte" -> 1, "scala.Short" -> 2, "scala.Char" -> 2,
                                "scala.Int" -> 3, "scala.Long" -> 4, "scala.Float" -> 5,
                                "scala.Double" -> 6)

  /** Java resolves an overload by EXACT match; Scala widens numerics first and then finds no
    * most-specific alternative.
    *
    * `Sprite.setRegion(int, int, int, int)` and `setRegion(float, float, float, float)` are both
    * applicable to four `Int` arguments — Java simply picks the `int` one, Scala reports an
    * ambiguity. Ascribing the method's function type names the alternative Java chose:
    * `(this.setRegion: (Int, Int, Int, Int) => Unit)(x, y, w, h)`.
    *
    * Fires only where the clash actually exists: a sibling of the same name and arity is WEAKLY
    * WIDER at every position and strictly wider at one, so the very same arguments reach it by
    * widening. `append(int)` beside `append(char)` is not that shape — `char` does not absorb an
    * `int` — and needs no help. Checking the direction is what keeps this from ascribing every
    * numeric call in the program (measured: 175 sites where 1 was ambiguous). */
  private def numericOverloadAscription(m: SymId): Option[String] =
    def numericParams(d: Tree.DefDef): Option[List[TypeRepr]] =
      val ps = d.paramss.flatten.map(_.tpt.tpe)
      Option.when(ps.nonEmpty && ps.forall(p => headSymOf(p).exists(s => numericRank.contains(sym(s).fullName))))(ps)
    def rank(t: TypeRepr): Int = headSymOf(t).flatMap(s => numericRank.get(sym(s).fullName)).getOrElse(0)
    def absorbs(wider: List[TypeRepr], here: List[TypeRepr]): Boolean =
      wider.sizeIs == here.size &&
        wider.zip(here).forall((w, h) => w == h || rank(w) > rank(h)) &&
        wider.zip(here).exists((w, h) => w != h)
    for
      d      <- program.definitionOf(m).collect { case d: Tree.DefDef => d }
      ps     <- numericParams(d)
      owner  <- program.definitionOf(sym(m).owner).collect { case c: Tree.ClassDef => c }
      if owner.body.exists {
        case o: Tree.DefDef =>
          o.symbol != m && sym(o.symbol).name == sym(m).name &&
            numericParams(o).exists(absorbs(_, ps))
        case _ => false
      }
    yield s"(${ps.map(tpe).mkString(", ")}) => ${tpe(d.returnTpt.tpe)}"

  /** A Java anonymous class's body → Scala's anonymous-class expression `new Base(args) { … }`.
    *
    * The anonymous class's own symbol is pushed on `classStack` while its members render, which is
    * what makes `thisRef` qualify an enclosing reference as `Outer.this.m`: inside a Scala
    * anonymous class the bare `this` is the anonymous instance, exactly as in Java, so an enclosing
    * member reached implicitly must be named through the outer instance. Captured locals need no
    * lowering at all — Scala closes over them where javac had to synthesise ctor parameters.
    *
    * `Some(Nil)` (the super-type-token idiom `new Base(){}`) still renders the braces: `new Base()`
    * and `new Base(){}` are DIFFERENT types, and only the latter has the reified supertype. */
  private def anonBody(anon: Option[Tree.AnonClass], i: Int): String = anon match
    case None    => ""
    case Some(a) =>
      classStack.append(a.symbol)
      val members = try a.body.map(stat(_, i + 1)).filter(_.trim.nonEmpty) finally classStack.removeLast()
      if members.isEmpty then " {}" else s" {\n${joinStats(members)}\n${ind(i)}}"

  /** parenthesize a term when it is an operand, where bare juxtaposition would misparse:
    * an operator application (precedence) and any control-flow expression — `if`/`match`
    * as an operand (`a + if (c) x else y`) needs parens or Scala reads "end of statement".
    *
    * A RECEIVER IS AN OPERAND TOO, and for a while it was not asked. `(c ? a : b).toString()` is
    * ordinary java; `if (c) a else b.toString()` is valid scala that calls the method on ONE
    * BRANCH, and where the branches share a type it COMPILES (§4.4 at the emitter rather than at a
    * statement form). Four positions render a receiver — `Select`'s qualifier, `InstanceOf`'s,
    * `ArrayLength`'s and `ArrayAccess`'s — and all four now come through here, as `Typed` and
    * `Spread` already did. */
  private def operand(t: Term, i: Int): String = t match
    case Tree.Apply(Tree.Select(_, m, _, _), _, _, _, _) if sym(m).fullName.startsWith("scala.<op>#") =>
      s"(${term(t, i)})"
    case _: Tree.If | _: Tree.Match | _: Tree.Lambda => s"(${term(t, i)})"
    case _ => term(t, i)

  /** …and the same question for a term spliced into a [[Tree.Opaque]] HOLE, which is strictly
    * harder: an operand's neighbours are the emitter's own output and a hole's neighbours are
    * whatever a policy entry wrote around `{recv}`. `{recv}.close()` after an ascription is
    * `x: T.close()`, which parses and means something else; `{arg0} + 1` after an assignment does
    * not parse at all. So this over-approximates by three more node kinds than [[operand]] — a
    * redundant pair of parentheses costs two characters and cannot change a meaning.
    *
    * Deliberately NOT folded into `operand`: that one is on the path of every emitted expression
    * in every port, and widening it would move member digests everywhere for a case that only
    * arises in ready-made text. */
  private def spliceOperand(t: Term, i: Int): String = Tree.uncomment(t) match
    case _: Tree.Typed | _: Tree.Assign | _: Tree.InstanceOf => s"(${term(t, i)})"
    case _                                                   => operand(t, i)

  private def block(stats: List[Statement], expr: Term, trailing: List[Trivia], i: Int): String =
    // drop a redundant trailing `()` when the block already has statements (Java void bodies).
    val tail = expr match
      case Tree.Literal(Constant.UnitC, _, _) if stats.nonEmpty => Nil
      case _                                                    => List(ind(i + 1) + term(expr, i + 1))
    val lines = (stats.map(stat(_, i + 1)) ++ tail).filter(_.trim.nonEmpty) ++
                trailing.map(triviaText(_, i + 1))
    s"{\n${joinStats(lines)}\n${ind(i)}}"

  /** join block statements, terminating one with `;` when the NEXT begins with `{` — otherwise
    * Scala greedily reads `new T(a)\n{ … }` as an anonymous-class body rather than two statements. */
  private def joinStats(lines: List[String]): String = lines match
    case Nil => ""
    case h :: t =>
      val sb = new StringBuilder(h)
      t.foreach { l => if firstCode(l).contains('{') then sb.append(";"); sb.append("\n").append(l) }
      sb.toString

  /** The first character a PARSER would see in `s` — comments skipped, exactly as Scala's scanner
    * skips them.
    *
    * `l.trim.startsWith("{")` was enough while a statement's first line was always code. It is not
    * once a statement can be preceded by its original comment: `new Array[String](n)` followed by
    * `// Read each page.` and then `{ … }` still parses as that constructor's anonymous-class body,
    * because the comment is whitespace — but the string test saw `//` and skipped the separator.
    * Two errors in libGDX, both reading `anonymous class {...} cannot extend final class Array`. */
  private def firstCode(s: String): Option[Char] =
    var i     = 0
    var depth = 0
    while i < s.length do
      if depth > 0 then
        if s.startsWith("*/", i) then { depth -= 1; i += 2 }
        else if s.startsWith("/*", i) then { depth += 1; i += 2 }
        else i += 1
      else if s.startsWith("//", i) then
        val nl = s.indexOf('\n', i)
        i = if nl < 0 then s.length else nl + 1
      else if s.startsWith("/*", i) then { depth += 1; i += 2 }
      else if s.charAt(i).isWhitespace then i += 1
      else return Some(s.charAt(i))
    scala.None

  /** A java `try`, plus the arm that keeps a translated JUMP out of its handlers.
    *
    * Java's `break`/`continue` is not an exception: no `catch` can intercept one, at any breadth.
    * Scala's translation of it IS one — `scala.util.boundary.Break[T] extends RuntimeException`
    * (read off `scala/util/boundary.scala` in the 3.8.x library, and deliberately not a
    * `ControlThrowable`, so `NonFatal` matches it too). So the moment a `boundary.break` stands
    * inside a `try` whose boundary is OUTSIDE that try, every arm broad enough to match a
    * `RuntimeException` swallows the jump: the loop runs on, and the handler's body runs for a
    * condition java never had.
    *
    * Nor is it incidental. dotty's `DropBreaks` rewrites a same-method break into a labelled jump,
    * which would be immune — but `DropBreaks.prepareForTry` shadows every enclosing label ("Need to
    * suppress labeled returns if there is an intervening try"), so a break under a `try` is ALWAYS
    * the exception form. Measured in the reference ecosystem before it was measured here: ssg
    * `ed8ce078`, where a date parser's early exit was eaten by the `catch (Exception)` that exists
    * to ignore a failed parse, and the whole filter silently stopped working with a green compile.
    *
    * The repair is a re-throw arm ahead of the java arms, and it is EXACT rather than a
    * compromise: java's own semantics say this handler never sees this jump, so re-throwing is
    * what faithfulness means here. It is also the only shape that composes — a `Break` belonging
    * to some other boundary is re-thrown by `boundary.apply` itself for the same reason.
    *
    * Interposed only where a jump really CROSSES the catch, which the emitter's own boundary state
    * answers exactly (see `crossesCatch`) — over-approximating would put the arm on every broad
    * catch in the corpus, and a repair nobody can point at a jump for is a repair nobody can
    * review. `finally` is untouched: a finalizer is not a handler, and both languages run it and
    * let the jump through. */
  private def tryStr(t: Tree.Try, i: Int)(using Obligations): String =
    val (res, body, catches, fin) = (t.resources, t.body, t.catches, t.finalizer)
    // JS-S13 — try-with-resources closes on ANY completion, in reverse order, BEFORE this try's own
    // catch. It fires exactly where there are resources; this is the row `ENGINE-LIMITS.md` F5 is
    // about, and the consult is what makes "the emitter considered it" a recorded fact rather than
    // a property of the code somebody would have to re-read.
    Obligations.consult(JS.S(13), t.origin)(Option.when(res.nonEmpty)(()))
    // JS-S12 — a `finally` completing abruptly DISCARDS the try's own abrupt completion. Consulted
    // at every `try`, fires where a finalizer exists at all; the row stays `Partial` because no
    // corpus fixture has a `finally` that is itself the SOURCE of the abrupt completion, and a
    // consult says the branch is live, never that the fixture exists (§2.3(a)).
    Obligations.consult(JS.S(12), t.origin)(Option.when(fin.isDefined)(()))
    val guard =
      if catches.exists(c => Jumps.catchesBreak(c.param.tpt.tpe)(using program)) && crossesCatch(body) then
        breakGuarded += t.id
        s"${ind(i + 1)}case ${TirEmitter.BreakGuard}: scala.util.boundary.Break[?] => throw ${TirEmitter.BreakGuard}" +
          s" // §4.4: a java jump is not catchable\n"
      else ""
    // JS-S11 — a translated CATCH swallows a translated JUMP, because `scala.util.boundary.Break`
    // extends `RuntimeException` and java's jump is not an exception at all. Read off the guard the
    // emitter just decided to emit, so the consult cannot drift from the decision.
    Obligations.consult(JS.S(11), t.origin)(Option.when(guard.nonEmpty)(()))
    val cs = catches.map(c => s"${ind(i + 1)}case ${esc(sym(c.param.symbol).name)}: ${tpe(c.param.tpt.tpe)} => ${term(c.body, i + 1)}").mkString("\n")
    val cl = if catches.isEmpty then "" else s" catch {\n$guard$cs\n${ind(i)}}"
    val fl = fin.map(f => s" finally ${term(f, i)}").getOrElse("")
    // The RESOURCES wrap the BODY and nothing else — JLS 14.20.3.2 defines an extended
    // try-with-resources as the basic one nested inside `try … Catches Finally`, i.e. every
    // resource is closed BEFORE this try's own `catch`/`finally` runs.
    if res.isEmpty then s"try ${term(body, i)}$cl$fl"
    else
      resourceLowered += t.id
      s"try ${resourceStr(res, body, i)}$cl$fl"

  /** JLS 14.20.3.1's lowering of a try-with-resources, emitted INLINE — one nesting per resource.
    *
    * ==What was here before==
    * `Tree.Try.resources` was populated by the frontend, printed by `TirPrinter`, and **never
    * interpolated into the emitted string**: the resource `val`s, every `close()`, the ordering and
    * the suppression were all silently dropped, behind a trailing comment describing a step that
    * had not been taken. A resource REFERENCED in its own body then failed to compile, which is
    * loud; a resource opened for its side effect alone — `try (var lock = acquire()) { … }`, an
    * idiomatic shape — compiled perfectly with the lock never acquired and never released. That is
    * CLAUDE.md §3's defect class at its worst: a whole java statement FORM gone, no error, no
    * count moving, and nothing in the output to say anything had been there.
    *
    * ==The shape, and why it is statements rather than a combinator==
    * The obvious lowering is `Using(r) { r => body }` or a runtime `withResource` helper, and both
    * put the body inside a LAMBDA. This emitter emits explicit `return`, and `break`/`continue`
    * render as `boundary.break` bound to a label opened OUTSIDE the try — a java jump out of a
    * try-with-resources is legal and must still close (JLS 14.20.3.1), and neither survives being
    * moved into a function body unchanged. So the lowering is java's own, spelled as statements:
    *
    * {{{
    * {
    *   val r = <init>
    *   var primary$n: Throwable = null
    *   try <rest>
    *   catch { case b: scala.util.boundary.Break[?] => throw b
    *           case t$n: Throwable => { primary$n = t$n; throw t$n } }
    *   finally
    *     if r != null then
    *       if primary$n != null then try r.close() catch { case s$n: Throwable => primary$n.addSuppressed(s$n) }
    *       else r.close()
    * }
    * }}}
    *
    * Four properties of java's contract that this reproduces rather than approximates:
    *
    *   - **reverse declaration order** — falls out of the nesting: the LAST resource is innermost,
    *     so its `finally` runs first;
    *   - **every `close()` is attempted** even when an earlier one threw. An inner `close()` that
    *     throws propagates into the enclosing level, becomes ITS `primary`, and the outer resource
    *     still closes in its own `finally`;
    *   - **suppression, not replacement** — a `close()` failure while the body is already
    *     completing abruptly is attached to the body's exception with `addSuppressed`, and the
    *     BODY's exception is the one that propagates. With the body completing normally the
    *     `close()` exception is the statement's own abrupt completion, which is what the bare
    *     `r.close()` arm gives;
    *   - **closed on ANY completion**, jumps included — and a JUMP TAKES THE `Break` ARM, not the
    *     recorder. Java's `break` carries no exception object, so JLS 14.20.3.1 has nothing for a
    *     failing `close()` to be suppressed INTO: the close exception simply replaces the jump and
    *     propagates. Scala's `break` IS an exception (`boundary.Break extends RuntimeException`,
    *     §4.4), so the catch-all recorded it as `primary` and the `finally` took the SUPPRESSING
    *     arm — and `boundary.Break` is constructed with suppression disabled, which makes
    *     `addSuppressed` a documented no-op. The close exception went nowhere at all, the jump
    *     completed, and a resource that failed to close said nothing: no error, no count, and only
    *     `TryResourceBehaviourSpec` able to see it. The arm AHEAD of the recorder is what fixes it —
    *     `primary` stays null, the `finally` calls `close()` bare, and a throwing close completes
    *     the statement abruptly exactly as java's does. It is also still the re-throw that keeps a
    *     jump crossing this catch-all intact (§4.4's broad-handler rule met by construction), so
    *     this arm needs no `BreakGuard` beside it.
    *
    * The catch-all's binder and the `primary` are numbered per nesting level, because two resources
    * in one statement are two of these blocks one inside the other and Scala would otherwise shadow. */
  private def resourceStr(res: List[Tree.ValDef], body: Term, i: Int)(using Obligations): String =
    res match
      case Nil => term(body, i)
      case v :: rest =>
        resourceSeq += 1
        val n    = resourceSeq
        val name = esc(sym(v.symbol).name)
        val p    = s"primary$$$n"
        val thr  = s"thrown$$$n"
        val sup  = s"suppressed$$$n"
        val inner = resourceStr(rest, body, i + 1)
        val b  = new StringBuilder
        b ++= "{\n"
        b ++= s"${ind(i + 1)}${valDef(v, 0)}\n"
        b ++= s"${ind(i + 1)}var $p: java.lang.Throwable = null\n"
        b ++= s"${ind(i + 1)}try $inner\n"
        // the JUMP arm, AHEAD of the recorder — see the note above. Java's jump carries no
        // exception object, so a failing `close()` has nothing to be suppressed into and must
        // propagate; leaving `primary` null is what routes the `finally` to the bare `close()`.
        b ++= s"${ind(i + 1)}catch { case ${TirEmitter.BreakGuard}: scala.util.boundary.Break[?] => throw ${TirEmitter.BreakGuard} // §4.4: a java jump carries no exception to suppress into\n"
        b ++= s"${ind(i + 2)}case $thr: java.lang.Throwable => { $p = $thr; throw $thr } }\n"
        b ++= s"${ind(i + 1)}finally if $name != null then {\n"
        b ++= s"${ind(i + 2)}if $p != null then { try $name.close() catch { case $sup: java.lang.Throwable => $p.addSuppressed($sup) } }\n"
        b ++= s"${ind(i + 2)}else $name.close()\n"
        b ++= s"${ind(i + 1)}}\n"
        b ++= s"${ind(i)}}"
        b.toString

  /** one counter for every resource block this emitter opens — see [[resourceStr]] for why the
    * `primary`/`thrown`/`suppressed` binders may not repeat across a nesting. */
  private var resourceSeq = 0

  /** every `try` whose RESOURCES this emitter lowered — the input to `try-resource`, which finds
    * the resource-carrying `try`s independently and reports the ones nothing closed.
    *
    * Keyed by [[Tree.Try.id]] for exactly the reasons `breakGuarded` is: an `Origin` is not unique
    * across `try`s, and `StandardTraversal` rebuilds every node so object identity is not either. */
  private val resourceLowered = collection.mutable.Set.empty[TryId]
  def resourceLowerings: Tree.Try => Boolean = t => resourceLowered.contains(t.id)
  def resourceLoweringCount: Int = resourceLowered.size

  /** does a jump in this try BODY leave the try — i.e. is its `boundary` outside it?
    *
    * Read off the emitter's own boundary state, which is exact at this point and free: a jump that
    * will render as `boundary.break` is precisely one whose target is in scope HERE, and every
    * target in scope here was opened by a construct enclosing this `try`. A label bound INSIDE the
    * body is not in these maps yet (the enclosing `Labeled`/loop registers it as it renders), so
    * the labelled lanes need no extra test to exclude it.
    *
    * The unlabelled lanes ask `breakTarget`/`contTarget` first, so a jump the emitter will leave as
    * a counted residue (no enclosing loop at all) is not mistaken for one that crosses anything. */
  private def crossesCatch(body: Term): Boolean =
    (breakTarget.isDefined && Jumps.breaksOut(body)) ||
      (contTarget.isDefined && Jumps.continuesIn(body)) ||
      labelBreak.keysIterator.exists(l => Jumps.jumpsTo(body, l, brk = true)) ||
      labelCont.keysIterator.exists(l => Jumps.jumpsTo(body, l, brk = false))

  /** every `try` this emitter put a [[TirEmitter.BreakGuard]] arm on — the input to `break-catch`,
    * which finds the crossings independently and reports the ones nothing guarded.
    *
    * ==Keyed by the try's own TOKEN, never by `Origin`==
    * An `Origin` is a java path, line and column, and two `try`s can share all three: a nested
    * one-liner (java's own "close quietly" idiom), and every `try` a phase SYNTHESISED, which
    * carries `Origin.synthetic`. Keyed by origin, a GUARDED try vouches for its unguarded twin —
    * the check asks "was this origin guarded?", gets `true` from the sibling and reports nothing,
    * which hides a §4.4 defect that compiles, moves no count and fails no test behind the very
    * mechanism written to find it.
    *
    * Object identity cannot be the key either, and that is not obvious: `StandardTraversal` REBUILDS
    * every node it walks (a `scan` is a `map` with a side effect), so the check's `try` is never the
    * object this emitter held. `Tree.Try.id` survives a rebuild because `copy` carries it.
    *
    * Still a SET, so the idempotence `recordedNotes` and `clauseLost` get from keying by unit is
    * here by construction: re-emitting a unit (the determinism twin, the action cache) re-adds the
    * tokens it already holds. */
  private val breakGuarded = collection.mutable.Set.empty[TryId]
  def breakGuards: Tree.Try => Boolean = t => breakGuarded.contains(t.id)
  /** how many `try`s that is — the only thing a caller can ask a membership test that it cannot
    * answer itself, and what a spec asserting "the emitter really did guard" needs. */
  def breakGuardCount: Int = breakGuarded.size

  /** A java `switch`, with a boundary around any case body that still contains an unlabelled
    * `break`.
    *
    * The frontend already deletes the `break` that TERMINATES a case, so one that reaches here is
    * a break in the MIDDLE of the case — `case '[': … if (length >= 0) { …; break; } …` in
    * `GlyphLayout`, whose case then falls through into `default: continue outer`. Emitted as a
    * no-op it did not stop, and the statements after it ran: the colour-tag arm fell into the
    * `continue` and re-scanned the run. Scala's `match` has no way to leave an arm early, so the
    * arm gets its own `boundary`.
    *
    * Note this is the SAME defect as the dropped `break`, one construct along: the fallthrough
    * lowering duplicates the next case's tail INTO this arm, so what runs on after the no-op is
    * code java put in a different case.
    *
    * The boundary is ALWAYS named. It is the innermost `Label` only until something inside it (a
    * labelled statement, a nested switch) opens another, and unlike a loop this node has no cheap
    * "nothing can nest here" case worth the risk.
    *
    * ==…and a NULL selector, which is the fall-out arm's own defect from the other side==
    * Java throws a `NullPointerException` the instant a `switch` on a REFERENCE type sees a null
    * selector — `String`, a boxed primitive, an enum (JLS 14.11.2 for the enum case, 14.11's
    * general text otherwise). It is IMPLICIT: a classic switch has no `case null` syntax to opt out
    * with, so there is no way to write one that tolerates null.
    *
    * Scala's `match` special-cases nothing. `null` simply fails every literal and constructor
    * pattern, so it reaches whatever the LAST arm is — which, since the fall-out arm was added, is
    * an arm that quietly does nothing. So the two §4.4 defects here are one mechanism read at two
    * selector values: without the fall-out arm an ordinary value throws `MatchError` where java
    * falls out; without this one a null value falls out where java throws.
    *
    * A `case null => throw` arm AHEAD of the java arms is the whole repair. It is emitted only for
    * a selector whose type is a REFERENCE type — a `switch` on a primitive `int`/`char` can never
    * see null, and libGDX's scanners are full of those — and only when the java did not write a
    * `null` label itself, which is SE21's pattern-switch escape hatch (JLS 14.11.1) and the one
    * shape that must NOT gain a synthetic exception. */
  private def matchStr(m: Tree.Match, i: Int)(using Obligations): String =
    val (scr, cases) = (m.scrutinee, m.cases)
    // JS-S06 — an unlabelled `break` in the MIDDLE of a case ends the CASE, and a `match` arm
    // cannot be left early. Consulted at every switch; it fires where an arm really needs the
    // boundary that makes java's meaning expressible.
    Obligations.consult(JS.S(6), m.origin)(Option.when(cases.exists(c => caseNeedsBoundary(c.body)))(()))
    // JS-S08 — java throws NPE on a null reference selector IMPLICITLY (JLS 14.11.2), and a classic
    // switch has no `case null` to opt out with. Read off `selectorCanBeNull`, which is the
    // emitter's own decision and not a second copy of it (§4.56: a rule stated once per caller is a
    // rule the next caller will not have).
    Obligations.consult(JS.S(8), m.origin)(Option.when(selectorCanBeNull(scr, cases))(()))
    val cs = cases.map { c =>
      val bare = if c.isDefault then "_" else c.labels.map(term(_, i)).mkString(" | ")
      // …AND ITS GUARD. `Tree.CaseDef.guard` was populated-able, carried through every phase by
      // `Phase.mapTerm`'s `Match` arm, printed by `TirPrinter` — and never rendered here, which is
      // `ENGINE-LIMITS.md` F5's shape exactly one node over: a field every diagnostic says is there
      // and the emitter drops. A guard NARROWS a case, so dropping one widens the arm to every
      // scrutinee matching the pattern — silent, compiling, and wrong in the direction that runs
      // MORE code. Nothing in the corpus mints one today (java's classic switch has no guard and
      // JS-S10's pattern switch is `Absent`), which is why no count could ever have found it; a
      // PHASE that synthesises a narrowed arm is all it takes, and the next one would have been
      // written against a field that does nothing.
      val pat = bare + c.guard.fold("")(g => s" if ${term(g, i)}")
      // A switch EXPRESSION's arm with a non-tail `yield` gets a VALUE-carrying boundary, the same
      // shape a mid-case `break` gets and at the same place. The two are mutually exclusive by
      // java's own rules (JLS 15.28 forbids a jump out of a switch expression; JLS 14.21 forbids a
      // `yield` outside one), so the arm needs at most one of them — and the `Label`'s type is what
      // makes them two arms rather than one: a `break` carries `Unit` and a `yield` carries the
      // switch expression's own type.
      // …and only a switch EXPRESSION opens one. `caseYieldsOut` now descends through a nested
      // switch STATEMENT (JLS 14.21 re-binds a `yield` at an EXPRESSION and nowhere else), so a
      // statement switch can perfectly well hold a `yield` that belongs to an arm further out —
      // and minting a `Label` here would type it at this match's `Unit` and steal the jump.
      if m.isExpr && caseYieldsOut(c.body) then
        labelSeq += 1
        val n = s"yield$$$labelSeq"
        val b = inYield(Some(n))(term(c.body, i + 1))
        s"${ind(i + 1)}case $pat => scala.util.boundary { ($n: scala.util.boundary.Label[${tpe(m.tpe)}]) ?=> $b }"
      else if !caseNeedsBoundary(c.body) then s"${ind(i + 1)}case $pat => ${inSwitch(scala.None)(term(c.body, i + 1))}"
      else
        labelSeq += 1
        val n = s"case$$$labelSeq"
        val b = inSwitch(Some(n))(term(c.body, i + 1))
        s"${ind(i + 1)}case $pat => scala.util.boundary { ($n: scala.util.boundary.Label[scala.Unit]) ?=> $b }"
    }.mkString("\n")
    // the SCRUTINEE is outside the switch — a `break` cannot occur in a java expression — but it
    // is rendered AFTER the arms so that the boundary numbering does not move for a switch that
    // needed no change.
    val sel  = inSwitch(scala.None)(term(scr, i))
    val npe =
      if !selectorCanBeNull(scr, cases) then ""
      else
        nullGuardedSwitches += m.id
        s"${ind(i + 1)}case null => throw new java.lang.NullPointerException(" +
          "\"switch selector was null\") // §4.4: java's switch NPEs on a null reference selector\n"
    s"$sel match {\n$npe$cs\n${ind(i)}}"

  /** does java's implicit null check apply to this switch, and has nothing already written one?
    *
    * Two conditions, and both are needed:
    *
    *   - the selector's type is a REFERENCE type. A primitive `int`/`char`/`long` selector cannot
    *     be null and gaining an unreachable `case null` would be noise on every scanner in the
    *     corpus. Decided from the emitted type's head symbol against scala's value classes —
    *     which is what a java primitive renders as, by construction;
    *   - no case label is already `null`. SE21's pattern switch may write `case null ->` (JLS
    *     14.11.1), which is java code that deliberately handles null, and adding a throw ahead of
    *     it would invert exactly the behaviour that label exists to state. */
  private def selectorCanBeNull(scr: Term, cases: List[Tree.CaseDef]): Boolean =
    val isValueClass = headSymOf(scr.tpe).map(s => sym(s).fullName).exists(TirEmitter.ScalaValueClasses.contains)
    val writesNull = cases.exists(_.labels.exists {
      case Tree.Literal(Constant.NullC, _, _) => true
      case _                                  => false
    })
    !isValueClass && !writesNull

  /** every switch this emitter gave a `case null` arm — the input to `switch-null`, which finds the
    * reference-typed switches independently and reports the ones nothing guarded.
    *
    * Keyed by [[Tree.Match.id]] for the reasons `breakGuarded` is keyed by `Tree.Try.id`: an
    * `Origin` is not unique across nodes, and `StandardTraversal` rebuilds every node it walks. */
  private val nullGuardedSwitches = collection.mutable.Set.empty[MatchId]
  def switchNullGuards: Tree.Match => Boolean = m => nullGuardedSwitches.contains(m.id)
  def switchNullGuardCount: Int = nullGuardedSwitches.size

  // ---- types ----
  /** a type in `new` position: `new Foo[?]` is illegal (you can't instantiate a wildcard), so
    * when a raw generic type carries wildcard args, drop them and let Scala infer the arguments
    * from the expected type (`new Foo(...)`). */
  /** As in `parentTpe`, only the HEAD is a `namedInner` position — the arguments are ordinary. */
  private def ctorTpe(t: TypeRepr): String = t match
    case TypeRepr.AppliedType(tc, args) if args.exists(_.isInstanceOf[TypeRepr.TypeBounds]) => byName(tpe(tc))
    case TypeRepr.AppliedType(tc, args) => s"${byName(tpe(tc))}[${args.map(tpe).mkString(", ")}]"
    case _ => byName(tpe(t))

  /** strip `scala.Array[...]` layers to the base element type (for `Array.ofDim[base](dims)`). */
  private def baseElem(t: TypeRepr): TypeRepr = t match
    case TypeRepr.AppliedType(TypeRepr.TypeRef(_, s), List(e)) if sym(s).fullName == "scala.Array" => baseElem(e)
    case _ => t

  /** THE TYPE DISPATCH — the emitter half of the catalog's FOURTH obligation surface.
    *
    * At the dispatch and never in an arm, for [[stat]]'s reason. A `TypeRepr` is not a `Tree`, so
    * [[Rendering]] could never enter one: it is the algebra a `TypeTree` carries, and a `TypeTree`
    * is rendered through its parent rather than through `stat`/`term`. Every difference about what
    * a type LOOKS LIKE in Scala — a wildcard's bound grammar, the `?` that stands in for an
    * unbindable variable, a nested type reached by projection or by value path — is decided here
    * and nowhere a node wrapper can see.
    *
    * The subject is the `TypeRepr` itself and the origin is the enclosing scope's
    * (`CatalogLog.currentOrigin`): a type is a value the IR shares across every position that names
    * it, so it has no position of its own, and the node it is being rendered FOR is the site a
    * reader of the finding would open. */
  private def tpe(t: TypeRepr): String =
    Typing.ofRepr(TirKinds.ofType(t), t)(tpeArm(t))

  /** JS-C29 and JS-G12 — the two questions a NAME is asked at the type dispatch's `TypeRef` arm.
    *
    * Both are read off the SYMBOL rather than by re-running `typeSym`'s cascade, which is
    * `classConsults`' own trade: the consult asks *does this difference apply at this reference*,
    * and what the repair emits is what the edge-case suite asserts. */
  private def typeRefConsults(s: SymId)(using Obligations): Unit =
    val full    = sym(s).fullName
    val marker  = Symbol.isUnresolvedTypeVar(full)
    // JS-C29 — a java NESTED type is one of two different scala types, and only one of them is
    // path-dependent. Every `$` in a full name is that question being asked; a marker is not a name
    // at all and is excluded, since it is the other row's.
    Obligations.consult(JS.C(29), catalog.currentOrigin)(Option.when(!marker && full.contains('$'))(()))
    // JS-G12 — the emitter's half: an unresolved type variable is a MARKER, so `?` is emitted in
    // its place. `ENGINE-LIMITS.md` G2 — one occurrence took out the statement around it.
    Obligations.consult(JS.G(12), catalog.currentOrigin)(Option.when(marker)(()))

  /** JS-G01's EMITTER half — the bound GRAMMAR, stated once and called from BOTH `TypeBounds` arms.
    *
    * The bare-wildcard arm is a fast path for `TypeBounds(NoType, NoType)`, so a consult written in
    * the general arm alone is a hole at every plain `?` the raw fill produces — which is most of
    * them (`ENGINE-LIMITS.md` F8). It FIRES where a bound survives into the text, which is exactly
    * where java's grammar and scala's differ: a bare `?` is the one form both languages spell the
    * same way, and a bound that is itself a marker is dropped rather than printed. */
  private def boundsConsults(lo: TypeRepr, hi: TypeRepr)(using Obligations): Unit =
    def written(b: TypeRepr) = b != TypeRepr.NoType && !isUnresolvedTypeVar(b)
    Obligations.consult(JS.G(1), catalog.currentOrigin)(Option.when(written(lo) || written(hi))(()))

  private def tpeArm(t: TypeRepr)(using Obligations): String = t match
    case TypeRepr.NoType | TypeRepr.NoPrefix   => "Any"
    case TypeRepr.TypeRef(_, s)                => typeRefConsults(s); typeSym(s)
    case TypeRepr.TermRef(_, s)                => s"${typeSym(s)}.type"
    case TypeRepr.ThisType(_)                  => "this.type"
    case TypeRepr.SuperType(_, sup)            => tpe(sup)
    case TypeRepr.ConstantType(c)              => constant(c)
    case TypeRepr.AppliedType(tc, as)          => s"${tpe(tc)}[${as.map(tpe).mkString(", ")}]"
    case TypeRepr.AndType(l, r)                => s"${tpe(l)} & ${tpe(r)}"
    case TypeRepr.OrType(l, r)                 => s"${tpe(l)} | ${tpe(r)}"
    case TypeRepr.ByNameType(u)                => s"=> ${tpe(u)}"
    case TypeRepr.TypeBounds(TypeRepr.NoType, TypeRepr.NoType) =>
      boundsConsults(TypeRepr.NoType, TypeRepr.NoType); "?"
    // A BOUND that is an unresolved type variable says nothing, and saying it is worse than
    // silence: `? <: ?E` names a type that does not exist and does not even lex. Dropping the
    // bound leaves `?`, which is exactly what G2 settles a raw generic renders as — and the
    // wildcard was already all the java said, since the variable it was bounded by has no binder
    // in this scope either. When BOTH bounds go, so does the whole `TypeBounds`.
    case TypeRepr.TypeBounds(lo, hi) =>
      boundsConsults(lo, hi)
      val l = if lo == TypeRepr.NoType || isUnresolvedTypeVar(lo) then "" else s" >: ${tpe(lo)}"
      val h = if hi == TypeRepr.NoType || isUnresolvedTypeVar(hi) then "" else s" <: ${tpe(hi)}"
      s"?$l$h"
    case TypeRepr.Refinement(p, _, _)          => tpe(p)
    case TypeRepr.MethodType(ps, res, _)       => s"(${ps.map((_, pt) => tpe(pt)).mkString(", ")}) => ${tpe(res)}"
    case TypeRepr.PolyType(_, res)             => tpe(res)
    case TypeRepr.TypeLambda(ps, body)         => s"[${ps.map(_._1).mkString(", ")}] =>> ${tpe(body)}"
    case TypeRepr.ParamRef(_, _)               => "?"

  // ---- constants ----
  private def constant(c: Constant): String = c match
    case Constant.BoolC(v)   => v.toString
    case Constant.ByteC(v)   => v.toString
    case Constant.ShortC(v)  => v.toString
    case Constant.IntC(v)    => v.toString
    case Constant.LongC(v)   => s"${v}L"
    case Constant.FloatC(v)  => s"${v}f"
    case Constant.DoubleC(v) => v.toString
    case Constant.CharC('\'') => "'\\''" // a single-quote char must be escaped inside `'…'`
    case Constant.CharC(v)   => s"'${escape(v.toString)}'"
    case Constant.StringC(v) => "\"" + escape(v) + "\""
    case Constant.NullC      => "null"
    case Constant.UnitC      => "()"
    case Constant.ClassOfC(t) => s"classOf[${tpe(t)}]"

  /** A PREFIX operator and its operand, with the two kept as two tokens.
    *
    * Scala's lexer takes a maximal run of operator characters as ONE identifier, so a prefix `-`
    * placed directly against an operand that already renders with a leading `-` produces `--`,
    * which is a different token and a syntax error — not a double negation. That happens whenever
    * java negates a literal whose VALUE is negative, which is routine in hash-mixing code: anim8's
    * `AnimatedGif` writes `x * -0xC13FA9A902A6328FL`, and `0xC13FA9A902A6328FL` is
    * `-4521708957497675121L` as a `long`, so the operand's rendering starts with the very character
    * the operator ends with. Measured: **48 errors** in one method (`analyzeOverboard`), all E040
    * "',' or ')' expected, but long literal found".
    *
    * A fact about Scala's lexical syntax, so CLAUDE.md §1(a): parenthesising the operand is the
    * general answer and it is the only one that cannot mis-lex — a separating SPACE would leave
    * `- -4L`, which reads as an infix application waiting for a left operand. The test is on the
    * two characters that would meet, never on the operator's name, so an operator this emitter
    * gains later is covered without being listed.
    */
  private def prefixOp(op: String, rendered: String): String =
    if op.nonEmpty && rendered.nonEmpty && isOpChar(op.last) && isOpChar(rendered.head)
    then s"$op($rendered)"
    else s"$op$rendered"

  /** the ASCII half of Scala's `opchar` (SLS 1.1); the Unicode `Sm`/`So` half cannot begin any
    * rendering this emitter produces. */
  private def isOpChar(c: Char): Boolean = "!#%&*+-/:<=>?@\\^|~".indexOf(c.toInt) >= 0

  /** Render a string or char literal's VALUE as Scala source that denotes the same value.
    *
    * A `Constant.StringC` holds the DECODED text, so every character in it has to be put back in a
    * form Scala's lexer accepts inside `"…"` / `'…'`. The set of characters that need an escape is
    * a fact about the two lexers and nothing else — CLAUDE.md §1(a) — and getting it short is not a
    * simplification, it is a file that does not parse:
    *
    *   - a raw `\n` ENDS the literal, and everything after it is then read as code;
    *   - a raw control character (`U+0001`, `\f`, `\b`, DEL) is an "illegal character" outright;
    *   - a lone SURROGATE cannot be encoded in the UTF-8 the file is written as, so it would be
    *     replaced on the way out and the value would silently change.
    *
    * Everything else — including ordinary non-ASCII text — is emitted verbatim, which is what keeps
    * a comment-adjacent literal readable and is safe because the emitted file is UTF-8 and Scala
    * reads it as UTF-8.
    *
    * `\uXXXX` is the general escape and it is a SCALA 3 escape SEQUENCE, not the Scala 2 source
    * pre-processing that was removed: it is expanded inside the literal only, so an emitted `\\u`
    * (an escaped backslash followed by `u`) is left alone, and there is no way for one to leak.
    *
    * Measured on anim8-gdx, whose `ConstantData` holds four ISO-8859-1 string literals of 47,935 /
    * 6,390 / 6,390 / 6,390 characters (a palette preload and three blue-noise grids, decoded with
    * `getBytes(ISO_8859_1)`): the five-case version produced **1,334 errors** from that one file —
    * one unescaped newline ends the literal and every byte after it is parsed as source. No other
    * corpus library had a literal with a character outside the five, which is exactly why this
    * survived three ports.
    */
  private def escape(s: String): String =
    val b = new StringBuilder(s.length)
    s.foreach { c =>
      c match
        case '\\'   => b ++= "\\\\"
        case '"'    => b ++= "\\\""
        case '\b'   => b ++= "\\b"
        case '\t'   => b ++= "\\t"
        case '\n'   => b ++= "\\n"
        case '\f'   => b ++= "\\f"
        case '\r'   => b ++= "\\r"
        case _ if c < ' ' || c.toInt == 0x7f || Character.isSurrogate(c) =>
          b ++= "\\u"; b ++= f"${c.toInt}%04x"
        case _      => b += c
    }
    b.result()

/** THE `Tree` KIND, as the rendering dispatch names it.
  *
  * `SpoonKinds` is this for the java side and has to be a REGISTRY, because `CtElement` is an
  * ordinary interface hierarchy with no sealedness and a kind can be absorbed by a supertype's arm.
  * The TIR side needs none of that: `Tree` is sealed, every case is a case class, and the name is
  * the compiler's own `productPrefix` — the same string `EmissionFieldCoverageSpec` reads out of the
  * class files, so a registry row naming a kind the IR does not have is caught by a spec.
  *
  * A function and not a table for exactly that reason: a table is a list a new node kind is not on.
  */
private object TirKinds:
  def of(t: Tree): String = t match
    case p: Product => p.productPrefix
    // unreachable: every concrete `Tree` is a case class. Named rather than defaulted, because a
    // silent "" here would be a kind nothing attaches to and therefore a scope that owes nothing —
    // which is indistinguishable from a node the catalog has nothing to say about.
    case _          => "?"

  /** …and the TYPE algebra's, for the fourth obligation surface. The same derivation for the same
    * reason: `TypeRepr` is sealed and every case is a case class or a case OBJECT, both of which
    * are `Product`s, so the name is the compiler's own and a table would be a list the next case is
    * not on. */
  def ofType(t: TypeRepr): String = t match
    case p: Product => p.productPrefix
    case _          => "?"

object TirEmitter:

  /** the binder of the re-throw arm that keeps a translated jump out of a java handler (§4.4).
    *
    * `$`-suffixed like every other name this emitter mints (`brk$`, `cnt$`, `lbl$`, `case$`), and
    * spelled ONCE so the check's spec and the emitter cannot drift. Java can declare an identifier
    * of this name and a shadowing warning is the worst it could cost — the arm's body is one
    * `throw` of its own binder. */
  val BreakGuard = "brkThru$"

  /** the emitted types a JAVA PRIMITIVE renders as — the whole of "this value cannot be null".
    *
    * Spelled ONCE and read by both the emitter and `SwitchNullCheck`, so the repair and the check
    * cannot disagree about which switches java's implicit null check applies to. Deliberately the
    * EMITTED names rather than java's: what the emitter holds at a scrutinee is the type after
    * every retyping phase, and a java `int` arrives here as `scala.Int` by construction. */
  val ScalaValueClasses: Set[String] = Set(
    "scala.Boolean", "scala.Byte", "scala.Short", "scala.Char",
    "scala.Int", "scala.Long", "scala.Float", "scala.Double", "scala.Unit")

  /** the WRAPPER whose static `hashCode` javac uses for a primitive record component (JS-C43).
    *
    * `Integer.hashCode(int)` and its seven siblings, and nothing else: javac's generated `hashCode`
    * folds `31 * h + <this>` from zero, which is exactly reproducible and was verified value by
    * value against `javac` for every primitive. Keyed on [[ScalaValueClasses]]' own spellings so the
    * two sets cannot drift, and DELIBERATELY short of it by one — `scala.Unit` is in that set and no
    * record component can have it, so a lookup that misses simply takes the reference arm. */
  val RecordBoxes: Map[String, String] = Map(
    "scala.Boolean" -> "java.lang.Boolean", "scala.Byte"   -> "java.lang.Byte",
    "scala.Short"   -> "java.lang.Short",   "scala.Char"   -> "java.lang.Character",
    "scala.Int"     -> "java.lang.Integer", "scala.Long"   -> "java.lang.Long",
    "scala.Float"   -> "java.lang.Float",   "scala.Double" -> "java.lang.Double")

  /** Scala's reserved words, plus the soft keywords a bare occurrence can still steer the parser
    * into. Backticking one that did not need it costs two characters and can never change meaning,
    * so the set over-approximates deliberately. */
  private val keywords = Set(
    "type", "object", "val", "var", "def", "class", "trait", "enum", "given", "match", "case",
    "if", "else", "while", "do", "for", "yield", "then", "with", "extends", "new", "this", "super",
    "null", "true", "false", "import", "package", "override", "final", "abstract", "sealed", "private",
    "protected", "implicit", "lazy", "return", "throw", "try", "catch", "finally", "forSome", "using",
    "export", "inline", "opaque", "transparent", "derives", "extension", "macro", "end", "as", "wait",
  )

  /** backtick an identifier that collides with a Scala keyword. */
  def esc(name: String): String = if keywords(name) then s"`$name`" else name

  /** THE SAME RULE, APPLIED TO EVERY SEGMENT OF A QUALIFIED NAME.
    *
    * `esc` answers for an IDENTIFIER, and every name this emitter renders by hand — a member, a
    * local, a type's simple name — goes through it. A `Symbol.fullName` does not: it is a PATH, and
    * a path that reaches the output verbatim carries whatever java's package structure happened to
    * spell. Java and Scala do not share a keyword set, so a package segment java was free to name
    * `type`, `object`, `val` or `package` emits an unparseable reference —
    * `com.fasterxml.jackson.core.type.TypeReference` reads as `…core.type` followed by a stray `.`,
    * which is an E119 plus a syntax error and not a type error anywhere near the construct.
    *
    * Cut only at §4.56's three separators (`.` between packages and the top-level type, `$` before
    * a nested type, `#` before a member) and carry each separator across verbatim, so a mixed chain
    * a caller is about to re-separate (`nestedPath`'s per-level `.`/`#` choice) still holds. Nothing
    * here decides ownership from the string — every segment is escaped or not on its own, and a
    * segment that is not a keyword is returned identically, which is why this is safe to apply to
    * an external FQN the port does not own. */
  def escPath(path: String): String =
    if path.isEmpty then path
    else
      val b   = new StringBuilder(path.length)
      var seg = 0
      var i   = 0
      while i <= path.length do
        if i == path.length || path(i) == '.' || path(i) == '$' || path(i) == '#' then
          b ++= esc(path.substring(seg, i))
          if i < path.length then b += path(i)
          seg = i + 1
        i += 1
      b.result()

  /** A class whose constructors carry a CONTEXT CLAUSE the emitted header does not
    * (`ENGINE-LIMITS.md` CT5).
    *
    * A value the emitter records and the run reports; the emitter names no check and no phase,
    * because the fact is about EMISSION — a `using` group the tree holds and the text does not —
    * and the phase that put the clause there is the run's to name.
    *
    * @param form what WAS emitted for this type: `class`, `trait`, `object`, `enum`. The reader's
    *             next question after "which type", and the three that are not `class` each say why
    *             the clause had nowhere to go.
    */
  final case class ClauseLoss(subject: SymId, fqn: String, form: String, origin: Origin)

  /** The default `javaSource`: the upstream file, read once per path.
    *
    * Memoised per JVM because it is a pure function of the path and a port asks for the same file
    * once per top-level type it declares. An unreadable path is `None` and not an exception — an
    * emitter must not fail because a source tree moved after it was parsed; the comment simply
    * cannot be recovered, and `TriviaCheck` (which reads the same file) reports the same absence
    * as an uncompared file rather than as a clean one. */
  private val javaSources = collection.concurrent.TrieMap.empty[String, Option[String]]

  def readJavaSource(path: String): Option[String] =
    javaSources.getOrElseUpdate(path, {
      val p = java.nio.file.Path.of(path)
      if java.nio.file.Files.isRegularFile(p) then
        try Some(java.nio.file.Files.readString(p)) catch case _: Throwable => scala.None
      else scala.None
    })

  /** the last segment of a dotted package name — the only form a Scala access qualifier has, since
    * the language has no dotted qualifier at all (`private[a.b]` does not parse). */
  def tailSegment(pkg: String): String = pkg.substring(pkg.lastIndexOf('.') + 1)

  /** the last segment of the package a TOP-LEVEL FQN lives in; `""` in the default package. */
  def packageTailOf(fullName: String): String =
    if !fullName.contains('.') then "" else tailSegment(fullName.substring(0, fullName.lastIndexOf('.')))

  /** THE BASE-SURFACE CONTRACT, as one emitter recorded it (`DESIGN.md` §8.3).
    *
    * @param types         emitted FQN → what was emitted at that name
    * @param members       emitted member key (the source map's spelling) → the same, per member
    */
  final case class Shapes(
      types: Map[String, Surface.TypeShape],
      members: Map[String, Surface.MemberShape],
  ):
    /** …rendered, which is the form the port map's `shape` column takes. */
    def renderedTypes: Map[String, String]   = types.view.mapValues(Surface.render).toMap
    def renderedMembers: Map[String, String] =
      members.view.mapValues(Surface.render).toMap.filter(_._2.nonEmpty)

  object Shapes:
    val empty: Shapes = Shapes(Map.empty, Map.empty)

  /** RECORD one of this file's decisions.
    *
    * Every decider here is [[Reason.Universal]] and every one of them is a §4.55/§4.56 fact about
    * the two languages: Java lets a name be reused where Scala cannot, and Java lets a constructor
    * write its own private fields where a replay one level down cannot. None of it is anybody's
    * policy, which is why none of these takes a parameter and why the rule strings name the
    * CLAUDE.md section rather than a phase. */
  private def note(
      out: collection.mutable.Buffer[Decision],
      kind: Decision.Kind,
      p: Program,
      s: SymId,
      detail: Map[String, String],
      rule: String,
  ): Unit =
    val fqn = p.symbolOf(s).map(_.fullName).filter(_.nonEmpty).getOrElse("?")
    out += Decision(kind, s, fqn, detail, Reason.Universal(rule), Decision.originOf(p, s))

  /** the §4.55 rule string every member rename carries. ONE string for all three passes, with the
    * pass distinguished by `detail("clash")`: an agent's first question is "why is this name not
    * the Java one", and the answer is one rule with three causes, not three rules. */
  private val MemberRenameRule = "member-rename(§4.55)"

  /** Drop `private` from the given members. Java lets a parent constructor write its own private
    * fields; when those statements are REPLAYED one level down (see `CtorFunnel.replayFor`) they
    * execute in the subclass, where `private` no longer reaches. Widening visibility can only
    * remove access errors, never introduce one, and never changes behaviour. */
  def widen(p: Program, members: Set[SymId],
            out: collection.mutable.Buffer[Decision] = collection.mutable.ListBuffer.empty): Program =
    if members.isEmpty then p
    else
      val src = p
      val syms = p.symbols.all.map(s =>
        if members(s.id) then s.copy(flags = s.flags.copy(isPrivate = false)) else s
      )
      // one row per member that ACTUALLY LOST a modifier — a member already public is in
      // `widenedMembers` because the planner could not know, and a decision about a change that
      // did not happen is a row an agent has to disprove.
      p.symbols.all.foreach { s =>
        if members(s.id) && s.flags.isPrivate then
          note(out, Decision.Kind.WidenedVisibility, src, s.id,
            Map(
              // the same `cause=` pair every §8.7 residue carries, so "what widened, and why" is
              // ONE grep over `decisions.tsv` rather than a join across two grammars.
              "cause" -> "ctor-replay-widening",
              "from" -> "private",
              "to"   -> "public",
              "why"  -> ("a parent constructor's statements are REPLAYED in this subclass " +
                "(CtorFunnel.replayFor), and java let them touch a private member that scala's " +
                "replay cannot reach one level down; widening can only remove access errors"),
            ),
            "ctor-replay-widening")
      }
      p.rebuilt(symbols = SymbolTable(syms))

  /** Promoting a constructor to Scala's primary widens the SCOPE of everything it declares: its
    * parameters become class parameters and its top-level locals become class members, both
    * visible to the whole body instead of to the constructor alone. That is the only hazard in
    * the promotion, and it has two faces — a name shared with one of the class's own members is
    * a double definition, and a name shared with an INHERITED member silently captures every
    * unqualified read of it (`this.viewport = viewport` still works; a bare `viewport` no longer
    * means the field). Suffixing `$p` removes both: parameters are positional and the locals are
    * unreachable from outside, so the rename is invisible everywhere it matters.
    */
  def funnelParamRenames(p: Program, out: collection.mutable.Buffer[Decision] = collection.mutable.ListBuffer.empty,
                         surface: Surface = null): Program =
    val renames = collection.mutable.Map[SymId, String]()
    // THE RUN'S OWN VIEW, not a `TrivialSurface`. This is the fourth site to have built the funnel
    // without one, and the failure is `ENGINE-LIMITS.md` D4 exactly: a dependent's fixpoint spans its
    // base, so the plan this pass reads a base class's PROMOTED PARAMETERS from is not the plan the
    // base emitted, and a `$p` rename derived from it renames a parameter that is not there.
    val plans = CtorFunnel.Plans(p, Option(surface))
    def nm(id: SymId): String = p.symbolOf(id).map(_.name).getOrElse("")
    def parentSyms(cd: Tree.ClassDef): List[SymId] =
      def hs(t: TypeRepr): Option[SymId] = t match
        case TypeRepr.TypeRef(_, s)      => Some(s)
        case TypeRepr.AppliedType(tc, _) => hs(tc)
        case _                           => scala.None
      cd.parents.flatMap { case tt: TypeTree => hs(tt.tpe); case t: Term => hs(t.tpe) }
    val declOf   = collection.mutable.Map[SymId, Tree.ClassDef]()
    // `allClassDefs`, so a METHOD-LOCAL class (`JS-C30`) is indexed too — see `allDeclaredClasses`.
    p.units.foreach(u => StandardTraversal.allClassDefs(u)(using p).foreach(cd => declOf(cd.symbol) = cd))
    // EFFECTIVE names: a parent's promoted param already renamed to `attributes$p` must read as
    // TAKEN here, or the child renames its own `attributes` to the same thing and the collision
    // simply moves up a level (measured on `DepthShader extends DefaultShader`). Requires the
    // parents-first scan below.
    def eff(id: SymId): String = renames.getOrElse(id, nm(id))
    def ownNames(cd: Tree.ClassDef): Set[String] =
      cd.body.collect {
        case d: Tree.DefDef if nm(d.symbol) != "<init>" => eff(d.symbol)
        case v: Tree.ValDef                             => eff(v.symbol)
        case c: Tree.ClassDef                           => eff(c.symbol)
      }.toSet ++ widenedOf(cd).map(v => eff(v.symbol))
    /** everything this class's promoted constructor contributes to the class BODY — its params and
      * its top-level locals. Neither is in `cd.body`, and both become members, so both are names a
      * SUBCLASS must avoid: `DepthShader extends DefaultShader` promotes the same two constructor
      * locals and landed on `attributes$p` twice. */
    def widenedOf(cd: Tree.ClassDef): List[Tree.ValDef] =
      val pl = plans(cd)
      pl.primaryParams ++ pl.primaryBody.collect { case v: Tree.ValDef => v }
    def visibleNames(cd: Tree.ClassDef, seen: Set[SymId] = Set.empty): Set[String] =
      if seen(cd.symbol) then Set.empty
      else ownNames(cd) ++ parentSyms(cd).flatMap(declOf.get).flatMap(visibleNames(_, seen + cd.symbol))
    val scanned = collection.mutable.Set[SymId]()
    def scan(cd: Tree.ClassDef): Unit =
      if scanned(cd.symbol) then return
      scanned += cd.symbol
      parentSyms(cd).flatMap(declOf.get).foreach(scan) // parents first, so `eff` is settled
      // AN ENUM PROMOTES ITS CONSTRUCTOR PARAMETERS TOO, and by a different route: `enumDef`
      // renders each as a `var` field of the sealed class's primary, deliberately without consulting
      // `CtorFunnel` (an enum's shape is already fixed — every `case object` passes its arguments to
      // that one primary). So `plans(cd).primaryParams` is empty here and the pass above sees
      // nothing, which is `ENGINE-LIMITS.md` T11's remaining half: T11 closed the case where the
      // COLLIDEE is the emitter-SYNTHESISED `Enum.name()`, by skipping it, and said the other case
      // "would need a §4.55 pass that can see" the promotion. It does not — the collidee here is
      // DECLARED (`Flavor`'s `isLiquidStyleInclude` parameter against its own
      // `isLiquidStyleInclude()`), and a declared member is exactly what this pass reads.
      //
      // NARROW, unlike the plan-based arm above. A promoted funnel parameter is positional and
      // invisible, so that arm renames every one of them; an enum parameter is EMITTED SURFACE —
      // a public `var` — so renaming one that does not collide would move the API of every enum in
      // the corpus for nothing. Two names are therefore NOT collidees:
      //
      //   - the parameter's own name (it is what is being placed, not something already there);
      //   - a body FIELD the parameter SUPERSEDES. `enumDef` drops a same-named `ValDef` precisely
      //     because the `var` parameter IS that field, so it is never emitted and cannot clash —
      //     and renaming the parameter would UN-supersede it, emitting both and breaking the
      //     self-assignment drop that goes with it (libGDX's `TextureFilter(glEnum)`).
      val enumParams =
        if !p.symbolOf(cd.symbol).exists(_.flags.isEnum) then Nil
        else cd.body.collectFirst { case d: Tree.DefDef if nm(d.symbol) == "<init>" => d }
               .map(CtorFunnel.valueParams(p, _)).getOrElse(Nil)
      if enumParams.nonEmpty then
        val own = enumParams.map(v => nm(v.symbol)).toSet
        // built from the PARTS rather than by subtracting from `visibleNames`, because the two
        // exclusions are not the same set: a name may be BOTH a superseded field and a declared
        // method (`isStyled` the parameter, `styled` the field, `isStyled()` the method), and
        // subtracting the parameter's own name would take the collidee with it.
        val takenE = collection.mutable.Set.from(
          cd.body.collect {
            case d: Tree.DefDef if nm(d.symbol) != "<init>" => eff(d.symbol)
            case c: Tree.ClassDef                           => eff(c.symbol)
            case v: Tree.ValDef if !own(nm(v.symbol))       => eff(v.symbol)
          } ++ parentSyms(cd).flatMap(declOf.get).flatMap(visibleNames(_)))
        enumParams.foreach { v =>
          val n = nm(v.symbol)
          if takenE(n) then
            var fresh = n + "$p"
            while takenE(fresh) do fresh += "$"
            takenE += fresh
            renames(v.symbol) = fresh
            note(out, Decision.Kind.RenamedMember, p, v.symbol,
              Map(
                "from"  -> n,
                "to"    -> fresh,
                "clash" -> "promoted-enum-ctor-scope",
                "owner" -> p.symbolOf(cd.symbol).map(_.fullName).getOrElse("?"),
                "why"   -> ("a java enum's constructor parameter becomes a `var` member of the " +
                  "emitted sealed class, where this name was already taken by a declared member; " +
                  "java's parameter was not a member at all and its two namespaces let the " +
                  "constant carry both"),
              ),
              MemberRenameRule)
        }

      val plan = plans(cd)
      if plan.primary.isDefined then
        val taken = collection.mutable.Set.from(visibleNames(cd))
        // the promoted constructor's params, then the top-level locals of its body (nested
        // blocks keep their own scope and never reach the class body)
        val widened = widenedOf(cd)
        widened.foreach { v =>
          val n = nm(v.symbol)
          if taken(n) then
            var fresh = n + "$p"
            while taken(fresh) do fresh += "$"
            taken += fresh
            renames(v.symbol) = fresh
            note(out, Decision.Kind.RenamedMember, p, v.symbol,
              Map(
                "from"  -> n,
                "to"    -> fresh,
                "clash" -> "promoted-ctor-scope",
                "owner" -> p.symbolOf(cd.symbol).map(_.fullName).getOrElse("?"),
                "why"   -> ("promoting this constructor to scala's PRIMARY widens its parameters " +
                  "and top-level locals into class members, where this name was already taken by " +
                  "an own or INHERITED member — java scoped it to the constructor and scala cannot"),
              ),
              MemberRenameRule)
        }
    // driven over EVERY declared class, method-local ones included (`JS-C30`); the `scanned` memo
    // and the parents-first recursion above are what keep §4.55's ordering, not the walk order.
    p.units.foreach(u => StandardTraversal.allClassDefs(u)(using p).foreach(scan))
    if renames.isEmpty then p
    else p.rebuilt(symbols = SymbolTable(p.symbols.all.map(s => renames.get(s.id).map(n => s.copy(name = n)).getOrElse(s))))

  /** Rename any field that SHADOWS an inherited member.
    *
    * Java fields shadow rather than override, and are resolved by the STATIC type of the receiver:
    * `ParallelArray.Channel` declares `Object data` and `FloatChannel extends Channel` declares
    * `float[] data`, so both objects exist and `((Channel) fc).data` and `fc.data` are different
    * storage. Scala has no such thing — a `var` cannot be overridden at all, let alone at an
    * incompatible type — and there is no rendering that keeps one name.
    *
    * So the shadowing field gets a fresh name. That is exact rather than approximate precisely
    * BECAUSE Java resolved these statically: every reference in the TIR already points at the
    * symbol Java chose, so renaming the symbol re-points exactly the references Java meant and no
    * others. Confirmed against the reference port, which does the same by hand and says why:
    * "renamed to floatData/intData/objectData … Java shadowed the field; Scala can't".
    *
    * Fields shadowing an inherited METHOD (`TextField`'s `layout` field under `Widget.layout()`)
    * are the same defect through Java's separate namespaces for the two, and are renamed here too.
    * Statics are exempt: they land in the companion, which inherits nothing. */
  def resolveFieldShadowing(p: Program, out: collection.mutable.Buffer[Decision] = collection.mutable.ListBuffer.empty,
                            surface: Surface = null,
                            /** JS-C04's citation surface. A whole-program pass does not walk one node
                              * kind, so a `Lowering`/`Rendering` wrapper is the wrong shape for it
                              * (`CatalogLog`'s header): it CITES the row once per declaration it
                              * decided about, which is `Decision`'s own granularity. Discarding by
                              * default, exactly as the emitter's own log parameter is — this pass runs
                              * once per emitter and the determinism twin must not double the count. */
                            catalog: CatalogLog = CatalogLog.discarding): Program =
    val view    = if surface eq null then TrivialSurface(p) else surface
    val renames = collection.mutable.Map[SymId, String]()
    def nm(id: SymId): String = p.symbolOf(id).map(_.name).getOrElse("")
    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s) => Some(s); case TypeRepr.AppliedType(tc, _) => headSym(tc); case _ => None
    val declOf  = collection.mutable.Map[SymId, Tree.ClassDef]()
    // `allClassDefs`, so a METHOD-LOCAL class (`JS-C30`) is indexed too — see `allDeclaredClasses`.
    p.units.foreach(u => StandardTraversal.allClassDefs(u)(using p).foreach(cd => declOf(cd.symbol) = cd))
    /** EFFECTIVE names — a renamed ancestor field contributes its NEW name, so a descendant asking
      * "is this taken?" sees what will actually be emitted. Requires parents-first scanning. */
    def eff(id: SymId): String = renames.getOrElse(id, nm(id))
    def instanceMembers(cd: Tree.ClassDef): Set[String] =
      cd.body.collect {
        case d: Tree.DefDef if nm(d.symbol) != "<init>" && !p.symbolOf(d.symbol).exists(_.flags.isStatic) => eff(d.symbol)
        case v: Tree.ValDef if !p.symbolOf(v.symbol).exists(_.flags.isStatic)                             => eff(v.symbol)
      }.toSet
    def inherited(cd: Tree.ClassDef, seen: Set[SymId] = Set.empty): Set[String] =
      cd.parents.flatMap { case tt: TypeTree => headSym(tt.tpe); case t: Term => headSym(t.tpe) }
        .filterNot(seen)
        .flatMap(declOf.get)
        .flatMap(pcd => instanceMembers(pcd) ++ inherited(pcd, seen + cd.symbol))
        .toSet
    /** the inherited DECLARATIONS behind those names — a name is not enough to answer
      * [[implementsInherited]], and a shadowing decision taken from a name alone is §4.56's rule
      * read at the emitter. */
    def inheritedSyms(cd: Tree.ClassDef, seen: Set[SymId] = Set.empty): List[SymId] =
      cd.parents.flatMap { case tt: TypeTree => headSym(tt.tpe); case t: Term => headSym(t.tpe) }
        .filterNot(seen)
        .flatMap(declOf.get)
        .flatMap(pcd => pcd.body.collect {
          case d: Tree.DefDef if !p.symbolOf(d.symbol).exists(_.flags.isStatic) => d.symbol
          case v: Tree.ValDef if !p.symbolOf(v.symbol).exists(_.flags.isStatic) => v.symbol
        } ++ inheritedSyms(pcd, seen + cd.symbol))
    val scanned = collection.mutable.Set[SymId]()
    def scan(cd: Tree.ClassDef): Unit =
      if scanned(cd.symbol) then return
      scanned += cd.symbol
      // parents FIRST, so `eff` above already reflects an ancestor's rename
      cd.parents.flatMap { case tt: TypeTree => headSym(tt.tpe); case t: Term => headSym(t.tpe) }
        .flatMap(declOf.get).foreach(scan)
      val shadowed = inherited(cd)
      // A field the BASE emits: its published name settles this, in both directions. `Renamed` hands
      // this run the base's own name; `Kept` means the base saw the same ancestors and moved nothing,
      // which here really is "nothing to do" — the shadowing is decided against ANCESTORS, and a
      // base class cannot extend a dependent's, so a shadowing pair of base fields is one the base's
      // own run already saw. (That is exactly what makes `resolveMemberClashes` different: its clash
      // is decided against DESCENDANTS, which a dependent has and the base did not.)
      def settledByBase(v: Tree.ValDef): Boolean =
        TirEmitter.baseName(p, view, v.symbol, "shadows-inherited") match
          case TirEmitter.BaseName.Derive      => false
          case TirEmitter.BaseName.Renamed(to) => renames(v.symbol) = to; true
          case TirEmitter.BaseName.Kept        => true
      /** A scala `val`/`var` and a scala PARAMETERLESS `def` of the same name, one inherited from
        * the other's type, are an IMPLEMENTATION pair and not a shadowing one — so moving the field
        * would break the very contract it is answering, and would do it silently until the port
        * reaches 0 typer errors and `RefChecks` finally runs (§3).
        *
        * The test is EXACT rather than a heuristic, and what makes it exact is that java cannot
        * produce this shape: a java method always has a parameter clause, so it never renders
        * parameterless. An emitted `paramss == Nil` is therefore a member the property conversion
        * made, and the field standing under it is that property's storage. Measured on the first
        * `bean-properties` collapse over a type with an interface above: the pass emitted
        * `var w$shadow` under an abstract `def w` it was the implementation of.
        *
        * A CONCRETE inherited `def` would be an OVERRIDE rather than an implementation and a `var`
        * cannot do that — but the conversion that produces this shape has already refused that case
        * (`BeanCollapse.Guard.ConcreteRelative`), so the two compose and this one does not re-ask.
        *
        * ==THE MIXED SHAPE — `exists`, not `forall`, and the reason is which failure is VISIBLE==
        * The same name can reach a class from TWO directions at once: an ancestor's FIELD, which is
        * a shadowing clash, and an ancestor's collapsed accessor, which is an implementation
        * obligation. Asked as `forall`, the mixed set answers "not an implementation pair" and the
        * field is renamed — so the class silently stops implementing the member, which is the ONE
        * outcome this test exists to prevent, arriving in exactly the shape it was written against.
        *
        * Neither answer is free and there is no third: scala has one namespace, so a name cannot be
        * both moved and kept. What decides it is `ENGINE-LIMITS.md` K5.7's trade — an unimplemented
        * member is invisible until the port reaches 0 typer errors, because `RefChecks` does not run
        * before then (§3), so it arrives on the day the port goes green in a member nobody is
        * looking at; a `var` that shadows an inherited one is a TYPER error, named on the first run.
        * The implementation therefore wins and the shadow is left LOUD. */
      lazy val inheritedDecls = inheritedSyms(cd)
      def implementsInherited(v: Tree.ValDef): Boolean =
        val n    = nm(v.symbol)
        val same = inheritedDecls.filter(s => eff(s) == n)
        same.exists(s => p.definitionOf(s) match
          case Some(d: Tree.DefDef) => d.paramss.isEmpty
          case _                    => false)

      cd.body.foreach {
        case v: Tree.ValDef if shadowed(nm(v.symbol)) && !p.symbolOf(v.symbol).exists(_.flags.isStatic) &&
                               !implementsInherited(v) && !settledByBase(v) =>
          // The fresh name must not ITSELF be inherited. `CheckBox.style` shadows
          // `TextButton.style`, which shadows `Button.style` — renaming both to `style$shadow`
          // just relocated the collision one level up. Keep appending until the name is free
          // (the same idiom `funnelParamRenames` uses).
          var fresh = nm(v.symbol) + "$shadow"
          while shadowed(fresh) do fresh += "$"
          renames(v.symbol) = fresh
          note(out, Decision.Kind.RenamedMember, p, v.symbol,
            Map(
              "from"  -> nm(v.symbol),
              "to"    -> fresh,
              "clash" -> "shadows-inherited",
              "owner" -> p.symbolOf(cd.symbol).map(_.fullName).getOrElse("?"),
              "why"   -> ("java fields SHADOW rather than override and are resolved by the static " +
                "type of the receiver, so both storages exist; scala has no such thing and a var " +
                "cannot be overridden at all. Every reference already points at the symbol java " +
                "chose, so renaming the symbol re-points exactly those and no others"),
            ),
            MemberRenameRule)
        case _                => ()
      }
    // driven over EVERY declared class, method-local ones included (`JS-C30`); `scanned` and the
    // parents-first recursion above are what keep §4.55's ordering, not the walk order.
    p.units.foreach(u => StandardTraversal.allClassDefs(u)(using p).foreach(scan))
    // JS-C04 — a subclass field SHADOWS a superclass field: two storage cells in java, ONE
    // virtually-dispatched member in scala. Cited per renamed declaration, and cited whether or not
    // anything was renamed is NOT what happens: a citation is a statement that this pass decided
    // about THAT declaration, and a class with no shadowing field is one it decided nothing about.
    renames.keys.foreach(id =>
      catalog.cite(JS.C(4), p.symbolOf(id).map(_.fullName).getOrElse(id.toString)))
    if renames.isEmpty then p
    else
      // same visibility relaxation as `resolveMemberClashes`: a renamed field must stay reachable
      // from wherever java read it — and RECORDED, see `recordClashWidening`.
      recordClashWidening(p, out, renames.keys, "shadows-inherited")
      val syms = p.symbols.all.map(s =>
        renames.get(s.id).map(n => s.copy(name = n, flags = s.flags.copy(isPrivate = false, isProtected = false))).getOrElse(s)
      )
      p.rebuilt(symbols = SymbolTable(syms))

  /** WHAT THE BASE SAYS ABOUT A FIELD THIS RUN WOULD RENAME — three answers, and the third is the
    * one that used to be silent (see [[baseName]]). */
  private[emit] enum BaseName:
    /** this run EMITS the field, or no base publishes a row for it: the local derivation stands.
      * For the second case the question is recorded as a gap first. */
    case Derive
    /** the base RENAMED it, and this is the name it emitted. Nothing is re-derived. */
    case Renamed(to: String)
    /** the base published a row and KEPT java's name. This run may not move the field — and the
      * clash it saw is therefore ITS OWN, made by declarations only this run has. */
    case Kept

  /** A field this run does NOT emit: does the BASE's published name settle it, so nothing is
    * re-derived? [[BaseName.Derive]] when the caller must compute its own.
    *
    * §4.55's two field passes are whole-program by construction — a field is renamed iff THIS CLASS
    * OR ANY DESCENDANT declares a method of that name, and shadowing is decided against every
    * ancestor — and a dependent's `Program` CONTAINS its base, with EXTRA descendants the base's own
    * run never saw. So a dependent subclass declaring `def x()` renames the BASE's field `x` to
    * `x$field` in the dependent's symbol table, and every reference the dependent emits then spells a
    * name the base never wrote. It compiles alone and cannot compile against the module it resolves
    * against (§1.5) — `ENGINE-LIMITS.md` D4's shape, at the renaming passes instead of the funnel.
    *
    * **0 corpus sites**, which is why this is a construction-time restriction and not a repair: no
    * port in the corpus has a dependent subclass whose method name collides with a base field, and a
    * check would therefore have reported zero for as long as anybody looked. `BaseSurfaceSpec` builds
    * the shape that has none.
    *
    * The base's answer is FOLLOWED, not merely respected: `name=` is the emitted simple name where it
    * differs from Java's, so a base that DID rename the field hands the dependent that name, and one
    * that did not hands it nothing and the field keeps Java's. Where no base publishes a row the
    * local derivation stands — the pre-contract path — and the question is recorded as a gap, because
    * a run that guessed here would emit exactly the text this exists to stop.
    *
    * '''AND "the base kept java's name" IS NOT "there is nothing to do".''' That branch answered
    * `true` and returned, which withheld the rename and left the clash standing: base `p.Base{int x}`
    * with a dependent `q.Heir extends p.Base { int x() }` emits a `def x()` under an inherited `var
    * x` — the same erased signature, which cannot compile, with ZERO findings and nothing in the run
    * disagreeing with itself. The base's answer settles ONE HALF of the clash and the other half is
    * this module's own declaration; [[BaseName.Kept]] hands the caller that fact so it can move the
    * half it owns. */
  private[emit] def baseName(p: Program, view: Surface, field: SymId, clash: String): BaseName =
    if view.owns(field) then BaseName.Derive
    else
      view.memberShape(field) match
        case Surface.Answer.Own => BaseName.Derive // cannot happen: `owns` above is the complement
        case Surface.Answer.Published(shape, _) =>
          if shape.name.nonEmpty then BaseName.Renamed(shape.name) else BaseName.Kept
        case Surface.Answer.Unknown(why, module) =>
          view.gap(Surface.Gap(p.symbolOf(field).map(_.fullName).getOrElse("?"),
            why + s" — this run would rename it for a $clash it can only see because its own " +
              "declarations are in the same program as the base's; the local derivation stands, and " +
              "it may not be the name the base emitted",
            module, fatal = false,
            fix = "§1(b) PER-LIBRARY: declare the module that emits this field as a base " +
              "(`base = \"…\"`) and re-run it with this engine so its port map carries a `name=` row"))
          BaseName.Derive

  /** THE OTHER HALF OF A §4.55 FIELD RENAME: the member also ships WIDER than Java wrote it.
    *
    * Both clash passes strip `private` and `protected` from every field they rename, unconditionally
    * — and they must: a renamed field has to stay reachable from wherever Java read it (an enclosing
    * class reading a nested class's `private` field, a subclass reading what Java resolved by the
    * receiver's static type). The rename was recorded and the widening was not, so a member emitted
    * `public` where the upstream wrote `private` carried a `RenamedMember` row that says nothing
    * about visibility and NO row that does.
    *
    * Nothing could catch that. The emitted visibility is what it always was, the compile is
    * unchanged, and `NoteCoverageCheck` compares decisions to NOTES rather than decisions to
    * reality — so a widening with no decision is invisible to it in the one direction that matters.
    * It also explains a number that was filed against the wrong decider: `WidenedVisibility` fell
    * 142 -> 135 on the largest port when a policy rename moved seven fields out from under
    * [[widen]]'s `isPrivate` test, and the honest reading is not that the ctor-replay decider lost
    * seven rows — it is that THIS decider never had them.
    *
    * One row per member that ACTUALLY LOST a modifier, the discipline [[widen]] already keeps: a
    * member already public is renamed for a reason that has nothing to do with access, and a
    * decision about a change that did not happen is a row an agent has to disprove. `clash` carries
    * the same value as the `RenamedMember` row beside it, so "why is this called `x$field`" and "why
    * is it public" are one grep and not two.
    */
  private def recordClashWidening(p: Program, out: collection.mutable.Buffer[Decision],
                                  renamed: Iterable[SymId], clash: String): Unit =
    renamed.toList.sortBy(_.raw).foreach { id =>
      p.symbolOf(id).filter(s => s.flags.isPrivate || s.flags.isProtected).foreach { s =>
        note(out, Decision.Kind.WidenedVisibility, p, id,
          Map(
            "cause" -> "member-rename",
            "clash" -> clash,
            "from"  -> (if s.flags.isPrivate then "private" else "protected"),
            "to"    -> "public",
            "why"   -> ("java lets this name be reused where scala cannot, so the field is renamed " +
              "(§4.55) — and a renamed field must stay reachable from every place java read it, " +
              "which scala's own access rules do not grant at the new name; widening can only " +
              "remove access errors, never introduce one, and never changes behaviour"),
          ),
          MemberRenameRule)
      }
    }

  /** Rename an enclosing method's LOCAL or PARAMETER that a nested class's member shadows.
    *
    * The fourth face of §4.55's "Java lets a name be reused where Scala cannot", and the one that
    * runs the other way: here the name that must move is not the member but the CAPTURE.
    *
    * Java keeps methods and variables in separate namespaces, so inside
    *
    * {{{
    * Result check(Item item, CollisionFilter filter) {          // jbump, World.check
    *   CollisionFilter visitedFilter = new CollisionFilter() {
    *     public Response filter(Item a, Item b) {               // a METHOD called filter
    *       if (filter == null) …                                // …and this is the PARAMETER
    *       return filter.filter(a, b);
    *     }
    *   };
    * }}}
    *
    * `filter` in expression position is unambiguously the captured parameter and `filter(a, b)`
    * would be the method. Scala has ONE namespace and resolves innermost-first, so the member wins
    * both: `filter == null` becomes an eta-expansion of the member and `filter.filter(a, b)` is
    * `value filter is not a member of (Item[?], Item[?]) => Response` — the compiler naming a
    * function type nobody wrote, which is how this was found.
    *
    * There is no rendering that keeps the name. Scala can qualify an outer MEMBER (`Outer.this.x`)
    * and cannot name a shadowed LOCAL at all, so the capture is renamed. That is exact rather than
    * approximate for §4.55's reason: Java resolved it statically, so the reference in the TIR
    * already points at the symbol Java chose, and renaming the symbol re-points exactly those.
    *
    * Two things this deliberately does NOT do:
    *
    *   - it renames only where the capture is REALLY shadowed — the local must be referenced inside
    *     the nested body AND the body must declare or inherit its name. A local rename is invisible,
    *     so over-approximating would be safe; a PARAMETER rename is not quite, because the emitted
    *     signature's parameter name is part of the surface, so precision is worth the two extra
    *     sets;
    *   - it reads `declared` through program-declared parents only. A member inherited from a type
    *     the frontend never parsed (a JDK supertype) is invisible here, as it is to every other
    *     pass that walks `declOf`. Nothing in the corpus reaches it, and a case that did would show
    *     up as exactly the error above rather than as silence.
    */
  def resolveCapturedLocalClashes(p: Program, out: collection.mutable.Buffer[Decision] = collection.mutable.ListBuffer.empty): Program =
    given Program = p
    def nm(id: SymId): String = p.symbolOf(id).map(_.name).getOrElse("")
    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s) => Some(s); case TypeRepr.AppliedType(tc, _) => headSym(tc); case _ => None

    /** every nested BODY, its owning type symbol and its parents. An anonymous class's body lives
      * inside a TERM, which is why this is a `StandardTraversal` and not a walk over `cd.body`:
      * a hand-rolled recursion over class bodies alone would miss every `new X() { … }`, which is
      * the only shape this defect has been seen in. */
    val bodies  = collection.mutable.ListBuffer[(List[Statement], List[SymId])]()
    val methods = collection.mutable.Set[SymId]()
    val declOf  = collection.mutable.Map[SymId, Tree.ClassDef]()
    val collector = new Phase:
      def name: String = "tir-emitter/captured-local-scan"
      override def transformClassDef(t: Tree.ClassDef)(using Program): Tree.ClassDef =
        declOf(t.symbol) = t
        bodies += ((t.body, t.parents.flatMap { case tt: TypeTree => headSym(tt.tpe); case tm: Term => headSym(tm.tpe) }))
        t
      override def transformNew(t: Tree.New)(using Program): Term =
        t.anon.foreach(a => bodies += ((a.body, headSym(t.tpt.tpe).toList)))
        t
      override def transformDefDef(t: Tree.DefDef)(using Program): Tree.DefDef = { methods += t.symbol; t }
    p.units.foreach(StandardTraversal.mapClassDef(collector, _))

    /** the definitions and the references a body holds, at any depth. Both come from one walk of the
      * SAME traversal, so "declared inside" and "referenced inside" can never disagree about what
      * "inside" means. */
    def survey(stats: List[Statement]): (Set[SymId], Set[SymId]) =
      val defs = collection.mutable.Set[SymId]()
      val refs = collection.mutable.Set[SymId]()
      val ph = new Phase:
        def name: String = "tir-emitter/captured-local-survey"
        override def transformValDef(t: Tree.ValDef)(using Program): Tree.ValDef     = { defs += t.symbol; t }
        override def transformDefDef(t: Tree.DefDef)(using Program): Tree.DefDef     = { defs += t.symbol; t }
        override def transformClassDef(t: Tree.ClassDef)(using Program): Tree.ClassDef = { defs += t.symbol; t }
        override def transformIdent(t: Tree.Ident)(using Program): Term              = { refs += t.sym; t }
        override def transformSelect(t: Tree.Select)(using Program): Term            = { refs += t.sym; t }
      stats.foreach(StandardTraversal.mapStat(ph, _))
      (defs.toSet, refs.toSet)

    def memberNames(stats: List[Statement]): Set[String] = stats.collect {
      case d: Tree.DefDef if nm(d.symbol) != "<init>" => nm(d.symbol)
      case v: Tree.ValDef                             => nm(v.symbol)
      case c: Tree.ClassDef                           => nm(c.symbol)
    }.toSet
    def visibleNames(parents: List[SymId], seen: Set[SymId]): Set[String] =
      parents.filterNot(seen).flatMap(declOf.get).flatMap(cd =>
        memberNames(cd.body) ++ visibleNames(
          cd.parents.flatMap { case tt: TypeTree => headSym(tt.tpe); case tm: Term => headSym(tm.tpe) },
          seen + cd.symbol)).toSet

    val renames = collection.mutable.Map[SymId, String]()
    bodies.foreach { (stats, parents) =>
      val shadowing = memberNames(stats) ++ visibleNames(parents, Set.empty)
      if shadowing.nonEmpty then
        val (defs, refs) = survey(stats)
        // a capture: referenced here, declared elsewhere, and OWNED BY A METHOD — which is what
        // makes it a local or a parameter rather than a field. A field of the enclosing class is
        // not this pass's business: java shadows it the same way scala does.
        (refs -- defs).toList.sortBy(_.raw).foreach { s =>
          val n = renames.getOrElse(s, nm(s))
          if shadowing(n) && p.symbolOf(s).exists(sy => methods(sy.owner)) then
            var fresh = n + "$local"
            while shadowing(fresh) do fresh += "$"
            renames(s) = fresh
            val owner = p.symbolOf(s).map(_.owner).getOrElse(SymId.None)
            note(out, Decision.Kind.RenamedMember, p, owner,
              Map(
                "from"  -> n,
                "to"    -> fresh,
                "clash" -> "captured-local-vs-nested-member",
                "why"   -> ("java keeps methods and variables in SEPARATE namespaces, so a class " +
                  "nested in this method may declare a member with the same name as one of its " +
                  "locals and both stay reachable; scala has one namespace and the member wins, " +
                  "leaving the capture unnameable"),
              ),
              MemberRenameRule)
        }
    }
    if renames.isEmpty then p
    else p.rebuilt(symbols = SymbolTable(p.symbols.all.map(s => renames.get(s.id).map(n => s.copy(name = n)).getOrElse(s))))

  /** Rename any field whose simple name collides with a method in the same EMITTED SCOPE (legal in
    * Java, illegal in Scala) by suffixing `$field`. Renaming the symbol propagates to every
    * reference, since the emitter reads names from the symbol table.
    *
    * "Same emitted scope" is the whole rule, and it is PLACEMENT, not name. A Java `static` member
    * leaves the class entirely — [[classDef]] partitions the body and emits the statics into the
    * companion `object` — so a `static` factory and an instance field of the same name are two
    * members of two different scopes and cannot collide:
    *
    * {{{
    * private final Bits all;                                    // the class
    * public static final Builder all (Class<? extends Component>... t) { … }   // the companion
    * }}}
    *
    * which is the shape a private constructor forces on every such library (Ashley's `Family`, three
    * fields' worth). Renaming there moved public surface for nothing. `resolveFieldShadowing` already
    * reasons exactly this way — "statics are exempt: they land in the companion, which inherits
    * nothing" — and this is the same fact read one direction further.
    *
    * So the two scopes are read separately, and they are not symmetric:
    *
    *   - the INSTANCE scope is inherited, so a field here still clashes with a method declared in any
    *     DESCENDANT (`hasNext` field + `hasNext()` from `Iterator`);
    *   - the STATIC scope is not. A companion inherits nothing; [[classDef]] re-exports a parent's
    *     companion with this type's OWN static names excluded, so a static field can only ever meet a
    *     static method of the SAME class.
    *
    * A `module` symbol has one body rather than a class and a companion, so both its placements land
    * in the same scope and the partition collapses — stated here rather than assumed, because a
    * synthesized object is exactly where an assumption about `isStatic` stops holding. */
  def resolveMemberClashes(p: Program, out: collection.mutable.Buffer[Decision] = collection.mutable.ListBuffer.empty,
                           surface: Surface = null,
                           /** JS-C46's citation surface — see [[resolveFieldShadowing]]'s. */
                           catalog: CatalogLog = CatalogLog.discarding): Program =
    val view    = if surface eq null then TrivialSurface(p) else surface
    val renames = collection.mutable.Map[SymId, String]()
    /** METHOD renames, kept apart from [[renames]] for one reason that is not tidiness: the field
      * map also drives `recordClashWidening` and the `isPrivate`/`isProtected` strip below, and a
      * renamed field NEEDS that (java let an enclosing class read a nested private field at the old
      * name). A method does not — it is renamed because the FIELD could not be, and widening it
      * would move emitted surface for nothing and file a `WidenedVisibility` row about a change with
      * no cause. */
    val methodRenames = collection.mutable.Map[SymId, String]()
    def nm(id: SymId): String = p.symbolOf(id).map(_.name).getOrElse("")
    def eff(id: SymId): String = renames.getOrElse(id, methodRenames.getOrElse(id, nm(id)))
    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s) => Some(s); case TypeRepr.AppliedType(tc, _) => headSym(tc); case _ => None
    def isModule(c: SymId): Boolean  = p.symbolOf(c).exists(_.flags.isModule)
    /** where a member of `owner` is EMITTED — the companion, or the class/object body itself. */
    def inCompanion(m: SymId, owner: SymId): Boolean =
      p.symbolOf(m).exists(_.flags.isStatic) && !isModule(owner)
    // per-class method names BY PLACEMENT, and the parent edges the instance scope is inherited along
    val instMethodsOf = collection.mutable.Map[SymId, Set[String]]()
    val statMethodsOf = collection.mutable.Map[SymId, Set[String]]()
    // …and the DECLARATIONS behind the instance names, because a clash the base's field cannot
    // resolve has to be resolved at the method, and a name is not a symbol.
    val instMethodSyms = collection.mutable.Map[SymId, List[SymId]]()
    val childrenOf = collection.mutable.Map[SymId, List[SymId]]().withDefaultValue(Nil)
    def index(cd: Tree.ClassDef): Unit =
      val (stat, inst) = cd.body.collect { case d: Tree.DefDef => d }.partition(d => inCompanion(d.symbol, cd.symbol))
      instMethodsOf(cd.symbol) = inst.map(d => nm(d.symbol)).toSet
      instMethodSyms(cd.symbol) = inst.map(_.symbol)
      statMethodsOf(cd.symbol) = stat.map(d => nm(d.symbol)).toSet
      cd.parents.foreach { case tt: TypeTree => headSym(tt.tpe).foreach(pp => childrenOf(pp) = cd.symbol :: childrenOf(pp)); case _ => () }
    // `allClassDefs`, so a METHOD-LOCAL class (`JS-C30`) is indexed too — see `allDeclaredClasses`.
    p.units.foreach(u => StandardTraversal.allClassDefs(u)(using p).foreach(index))
    def selfOrDescMethods(c: SymId, seen: Set[SymId] = Set.empty): Set[String] =
      if seen(c) then Set.empty
      else instMethodsOf.getOrElse(c, Set.empty) ++ childrenOf(c).flatMap(ch => selfOrDescMethods(ch, seen + c))
    def selfOrDescClasses(c: SymId, seen: Set[SymId] = Set.empty): List[SymId] =
      if seen(c) then Nil else c :: childrenOf(c).flatMap(ch => selfOrDescClasses(ch, seen + c))

    // THE OVERRIDE GRAPH IS LAZY. Building it walks the whole program, and the only branch that
    // needs it is the one with 0 corpus sites — a base field this run may not move whose clashing
    // method it owns. `baseUnits` is what makes the graph able to say "this component reaches a
    // declaration a resolution root owns", which is the refusal that keeps the rename honest.
    lazy val graph = OverrideGraph.build(
      p, baseUnits = p.units.map(_.symbol).toSet -- view.ownedUnits.map(_.symbol).toSet)

    def scan(cd: Tree.ClassDef): Unit =
      val instClashNames = selfOrDescMethods(cd.symbol)
      val statClashNames = statMethodsOf.getOrElse(cd.symbol, Set.empty)
      def clashNames(v: Tree.ValDef): Set[String] =
        if inCompanion(v.symbol, cd.symbol) then statClashNames else instClashNames
      def clashes(v: Tree.ValDef): Boolean = clashNames(v)(nm(v.symbol))

      /** rename the FIELD — the ordinary answer, and the only one this pass had. */
      def moveField(v: Tree.ValDef): Unit =
        // …TO A NAME THAT IS FREE, the idiom both sibling passes use (`resolveFieldShadowing`'s
        // `style$shadow`, `funnelParamRenames`). `$` is an ordinary java identifier character, so a
        // class may declare `x`, `x()` AND `x$field`, and appending once just relocates the
        // collision — silently, because the emitted duplicate is a name neither the rename decision
        // nor any count mentions. Held against BOTH members of the emitted scope: the method names
        // that decided the clash, and the sibling fields' EFFECTIVE names (§4.55 — a field this pass
        // has already moved contributes its NEW name, or two renames land on each other).
        val taken = clashNames(v) ++ cd.body.collect { case w: Tree.ValDef if w.symbol != v.symbol => eff(w.symbol) }
        var fresh = nm(v.symbol) + "$field"
        while taken(fresh) do fresh += "$"
        renames(v.symbol) = fresh
        // the note's own text is unchanged by this refinement, deliberately: it already says
        // "a method of this class or of a SUBCLASS", which is the INSTANCE scope and now the
        // only thing the pass claims. A reworded `why` is emitted text (§4.575) and would move
        // every member carrying this note in every port, hiding the three that really changed.
        note(out, Decision.Kind.RenamedMember, p, v.symbol,
          Map(
            "from"  -> nm(v.symbol),
            "to"    -> fresh,
            "clash" -> "field-vs-method",
            "owner" -> p.symbolOf(cd.symbol).map(_.fullName).getOrElse("?"),
            "why"   -> ("java keeps fields and methods in SEPARATE namespaces, so a field may " +
              "share a name with a method of this class or of a SUBCLASS; scala has one " +
              "namespace and forbids it"),
          ),
          MemberRenameRule)

      /** …and the answer when the FIELD IS THE BASE'S AND THE BASE KEPT JAVA'S NAME: move the half
        * of the clash this module owns.
        *
        * The clashing methods are necessarily this run's own declarations, and that is a derivation
        * rather than an assumption: the instance clash is decided against this class AND EVERY
        * DESCENDANT, and a descendant the BASE also had would have made the base's own run see the
        * same clash and publish a `name=`. So a `Kept` answer means every clashing method is one only
        * this program has.
        *
        * The rename still has to be SOUND, and only [[OverrideGraph]] can say so: a method that
        * implements an interface or overrides a parent this module does not own cannot move, because
        * the declaration it answers to stays where it is. That closure is refused and RECORDED — the
        * honest outcome for a clash with no local repair (`DESIGN.md` §8.3: the contract buys
        * attribution and refuse-and-count, not an answer). */
      def moveOwnMethods(v: Tree.ValDef): Unit =
        val n     = nm(v.symbol)
        val mine  = selfOrDescClasses(cd.symbol)
          .flatMap(c => instMethodSyms.getOrElse(c, Nil))
          .filter(m => nm(m) == n && view.owns(m))
        val fieldFqn = p.symbolOf(v.symbol).map(_.fullName).getOrElse("?")
        mine.foreach { m =>
          if !methodRenames.contains(m) then
            val c = graph.closureOf(m)
            c.anchorReason(p) match
              case Some(why) =>
                view.gap(Surface.Gap(p.symbolOf(m).map(_.fullName).getOrElse("?"),
                  s"this method shares a name with `$fieldFqn`, a field the base emitted under java's " +
                    s"own name — so scala's single namespace forbids the pair, this run may not move " +
                    s"the field, and it cannot move the method either: $why",
                  view.memberShape(v.symbol).module, fatal = false,
                  fix = "§1(a) ENGINE, IN THE BASE: only the module that emits the field can rename " +
                    "it, and only if it can see the clash — which it cannot, because the method is " +
                    "declared here. Rename one of the two in the java, or drop this method"))
              case scala.None =>
                // FREE against everything the component can see — the collision rule `MemberRenamer`
                // uses, read through the effective names so two renames in one hierarchy cannot land
                // on each other (§4.55).
                val visible = c.members.map(graph.ownerOf).filter(_ != SymId.None).toList.distinct
                  .flatMap(graph.relativesOf).distinct.flatMap(graph.membersOf).distinct
                  .filterNot(c.members.contains)
                var fresh = n + "$method"
                var fuel  = 64
                while visible.exists(x => eff(x) == fresh) && fuel > 0 do { fresh += "$"; fuel -= 1 }
                c.members.foreach(x => methodRenames(x) = fresh)
                c.members.toList.sortBy(_.raw).foreach { x =>
                  note(out, Decision.Kind.RenamedMember, p, x,
                    Map(
                      "from"      -> nm(x),
                      "to"        -> fresh,
                      "clash"     -> "field-vs-method-in-base",
                      "field"     -> fieldFqn,
                      "owner"     -> p.symbolOf(graph.ownerOf(x)).map(_.fullName).getOrElse("?"),
                      "component" -> c.members.size.toString,
                      "why"       -> ("java keeps fields and methods in SEPARATE namespaces and " +
                        "scala does not; the field is emitted by a BASE module under java's own " +
                        "name, so this run may not move it — the half of the clash this module " +
                        "owns is the method, and every declaration of its override component moves " +
                        "with it"),
                    ),
                    MemberRenameRule)
                }
        }

      cd.body.foreach {
        case v: Tree.ValDef if clashes(v) =>
          TirEmitter.baseName(p, view, v.symbol, "field-vs-method") match
            case TirEmitter.BaseName.Derive      => moveField(v)
            case TirEmitter.BaseName.Renamed(to) => renames(v.symbol) = to
            case TirEmitter.BaseName.Kept        => moveOwnMethods(v)
        case _                                           => ()
      }
    // driven over EVERY declared class, method-local ones included (`JS-C30`) — see
    // `allDeclaredClasses` for why a body walk is one node short of java.
    p.units.foreach(u => StandardTraversal.allClassDefs(u)(using p).foreach(scan))
    // JS-C46 — java has TWO name namespaces and scala has one, so a field `x` beside a method `x()`
    // is legal there and illegal here. Cited per declaration this pass moved, in either direction:
    // the field can take the new name or the METHODS can, and both are the same decision.
    (renames.keys ++ methodRenames.keys).foreach(id =>
      catalog.cite(JS.C(46), p.symbolOf(id).map(_.fullName).getOrElse(id.toString)))
    if renames.isEmpty && methodRenames.isEmpty then p
    else
      // also relax visibility: Java lets the enclosing class read a nested class's private
      // field (`point.x`); Scala does not, so a renamed clash-field must stay accessible — and
      // RECORDED, see `recordClashWidening`. FIELDS only — see `methodRenames`.
      recordClashWidening(p, out, renames.keys, "field-vs-method")
      val syms = p.symbols.all.map { s =>
        renames.get(s.id).map(n => s.copy(name = n, flags = s.flags.copy(isPrivate = false, isProtected = false)))
          // `name` and NOT `fullName`: the member key the source map, the port map and `dropMethods`
          // all join on is `owner#<java name>`, and a §4.55 pass moving it would move that join under
          // four artifacts at once with nothing failing (`MemberClashPlacementSpec` is the gate).
          .orElse(methodRenames.get(s.id).map(n => s.copy(name = n)))
          .getOrElse(s)
      }
      p.rebuilt(symbols = SymbolTable(syms))
