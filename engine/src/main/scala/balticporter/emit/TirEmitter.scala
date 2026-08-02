package balticporter.emit

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
):
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
          TirEmitter.resolveMemberClashes(source, own, surface), own, surface), own), own)
  /** which Java constructor becomes each class's Scala primary, and which `super(args)` can be
    * replayed as statements — whole-program decisions. */
  private val plans = CtorFunnel.Plans(prepared, Some(surface))
  // a replayed parent constructor's statements execute one level down, so the private members
  // they reach must be visible there. Widening only rewrites symbol FLAGS — the trees `plans`
  // was computed over are untouched, so it still applies.
  private val program = TirEmitter.widen(prepared, plans.widenedMembers, own)

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
    val body = classDef(cd, 0)
    val pkg  = if full.contains('.') then s"package ${full.substring(0, full.lastIndexOf('.'))}\n\n" else ""
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
    // …and the comments the attachment channel could not place, put back beside the member they
    // were written in. BEFORE the source map is computed, not after: a post-pass over finished text
    // would desync `srcmap.tsv` and `members.tsv` from the file, and the rule that a join happens
    // on a recorded id rather than on a rendering applies to line ranges too.
    val text = recoverTrivia(cd, text0)
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

  private val recordedEmission =
    collection.mutable.LinkedHashMap.empty[String, collection.mutable.ListBuffer[Decision]]

  /** what the EMITTER decided while rendering, per unit — idempotent, so re-emitting a unit does
    * not double it. Drained by `PortRun` into the run's log after emission. */
  def emissionDecisions: List[Decision] = recordedEmission.values.toList.flatten

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
      recordedEmission.getOrElseUpdate(currentUnitName, collection.mutable.ListBuffer.empty) += d
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
      )
    }

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

  /** every type symbol that appears as a parent (extends/mixin) anywhere in the program — an
    * all-static class in this set must stay a `class`, since an `object` can't be extended. */
  private lazy val extendedTypes: Set[SymId] =
    val acc = collection.mutable.Set[SymId]()
    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s)      => Some(s)
      case TypeRepr.AppliedType(tc, _) => headSym(tc)
      case _                           => None
    def scan(cd: Tree.ClassDef): Unit =
      cd.parents.foreach {
        case tt: TypeTree => headSym(tt.tpe).foreach(acc += _)
        case term: Term   => headSym(term.tpe).foreach(acc += _)
      }
      cd.body.foreach { case c: Tree.ClassDef => scan(c); case _ => () }
    program.units.foreach(scan)
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

  private def declaredTypes(cd: Tree.ClassDef): Set[SymId] =
    val acc = collection.mutable.Set[SymId](cd.symbol)
    cd.body.foreach { case c: Tree.ClassDef => acc ++= declaredTypes(c); case _ => () }
    acc.toSet

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
      cd.body.foreach { case c: Tree.ClassDef => scan(c); case _ => () }
    program.units.foreach(scan)
    acc.toSet

  /** each type → the names of the `static` members it DECLARES itself. */
  private lazy val ownStaticsBySym: Map[SymId, Set[String]] =
    val m = collection.mutable.Map[SymId, Set[String]]()
    def scan(cd: Tree.ClassDef): Unit =
      m(cd.symbol) = basePublishedStatics(cd.symbol).getOrElse(
        cd.body.collect { case d: Definition if sym(d.symbol).flags.isStatic => esc(sym(d.symbol).name) }.toSet)
      cd.body.foreach { case c: Tree.ClassDef => scan(c); case _ => () }
    program.units.foreach(scan); m.toMap

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
      cd.body.foreach { case c: Tree.ClassDef => scan(c); case _ => () }
    program.units.foreach(scan); m.toMap

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
    def scan(cd: Tree.ClassDef): Unit =
      m(cd.symbol) = parentSymsOf(cd); cd.body.foreach { case c: Tree.ClassDef => scan(c); case _ => () }
    program.units.foreach(scan); m.toMap

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

  private val keywords = Set(
    "type", "object", "val", "var", "def", "class", "trait", "enum", "given", "match", "case",
    "if", "else", "while", "do", "for", "yield", "then", "with", "extends", "new", "this", "super",
    "null", "true", "false", "import", "package", "override", "final", "abstract", "sealed", "private",
    "protected", "implicit", "lazy", "return", "throw", "try", "catch", "finally", "forSome", "using",
    "export", "inline", "opaque", "transparent", "derives", "extension", "macro", "end", "as", "wait",
  )
  /** backtick an identifier that collides with a Scala keyword. */
  private def esc(name: String): String = if keywords(name) then s"`$name`" else name
  /** a TYPE symbol's rendered name. FULLY QUALIFIED by default — for the structural Java→Scala
    * phase we emit fully-qualified references and generate NO imports, which deletes the entire
    * import-decision bug class (import-vs-projection, shadowing, static-receiver qualification):
    * a reference is now a context-free function of the symbol's owner chain. Only two things
    * stay unqualified: type params, and a type declared in THIS unit (in scope by simple name).
    * Human-readable imports are a separate, optional beautification backend, not a correctness
    * prerequisite. (A later refinement handles givens/extensions, which FQN genuinely can't name.) */
  private def typeSym(id: SymId): String =
    val s = sym(id)
    if tparamSubst.contains(id) then tpe(tparamSubst(id)) // ctor type param → its bound
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
      if !sx.fullName.contains('$') then Some(sx.fullName)
      else if sx.owner == SymId.None || program.symbolOf(sx.owner).isEmpty then None
      else go(sx.owner).map(p => p + (if sx.flags.isStatic then "." else "#") + esc(sx.name))
    // The fallback fires exactly when an owner is UNKNOWN, which for a type we do not define means
    // an external/JDK one. Name those with `.`: a Java nested type is reached as `Outer.Inner` in
    // Scala, and a `#` projection is not even available — it needs the prefix to be an immutable
    // path, which a bare external class name is not (`java.nio.channels.FileChannel#MapMode`).
    go(id).getOrElse:
      val sep = if program.definitionOf(id).isEmpty then '.' else '#'
      sym(id).fullName.replace('$', sep)

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
    def index(cd: Tree.ClassDef): Unit =
      declOf(cd.symbol) = cd
      cd.body.foreach { case c: Tree.ClassDef => index(c); case _ => () }
      cd.enumCases.foreach(_.body.foreach { case c: Tree.ClassDef => index(c); case _ => () })
    program.units.foreach(index)
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
    val prim    =
      if plan.isSynthesised then
        s" protected (${(plan.synthetic.map((n, t) => s"$n: ${tpe(t)}") ++ markerParam).mkString(", ")})$givenClause"
      else if pparams.nonEmpty then s"${if ctorVis.isEmpty then "" else " " + ctorVis}(${pparams.map(param).mkString(", ")})$givenClause"
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
      val ob0 = orderBody(members, paramfulPrimary).map(memberStat(_, i + 1)).filter(_.nonEmpty).mkString("\n")
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
    val body1   = joinStats(orderBody(instance, paramfulPrimary).map(memberStat(_, i + 1)).filter(_.nonEmpty))
    val body0   = if bnote.isEmpty then body1 else joinStats(bnote :: List(body1).filter(_.nonEmpty))
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
    val cls     =
      if s.flags.isAnnotation then
        s"${leading(cd.leading, i)}$cnote${annots(s, i)}${ind(i)}class ${esc(s.name)}$tps$prim extends scala.annotation.StaticAnnotation"
      else s"${leading(cd.leading ++ ctorLead, i)}$cnote${annots(s, i)}${ind(i)}${mods(s, privateQualifier(s.owner))}$abs$kw ${esc(s.name)}$tps$prim$ext$open"
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
    val hasCompanion = !(statics.isEmpty && parentExports.isEmpty && markerDecl.isEmpty)
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
      val sb = (parentExports ++ markerDecl ++ orderBody(statics).map(memberStat(_, i + 1)).filter(_.nonEmpty)).mkString("\n")
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
      orderBody(instance).map(memberStat(_, i + 1)).filter(_.nonEmpty) ++
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

  private def isStatic(s: Statement): Boolean = s match
    case d: Tree.ClassDef => sym(d.symbol).flags.isStatic
    case d: Definition    => sym(d.symbol).flags.isStatic
    case _                => false

  /** Scala secondary constructors must delegate to a PRECEDING constructor, so order fields first,
    * then constructors in DELEGATION-TOPOLOGICAL order (each ctor's `this(args)` target emitted
    * before it), then everything else. Arity is not a reliable proxy — a 3-arg convenience ctor can
    * delegate to a 1-arg one (`Texture(pixmap,fmt,mip)` → `Texture(data)`), so we follow the actual
    * `this(...)` edges, keyed by the target ctor's own symbol. */
  private def orderBody(body: List[Statement], paramfulPrimary: Boolean = false): List[Statement] =
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
    val fields = body.collect { case v: Tree.ValDef => v }
    val rest   = body.filterNot(s => isCtor(s) || s.isInstanceOf[Tree.ValDef])
    fields ++ ordered.toList ++ rest

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

  private def substTp(t: TypeRepr, m: Map[SymId, TypeRepr]): TypeRepr = t match
    case TypeRepr.TypeRef(_, s) if m.contains(s) => m(s)
    case TypeRepr.AppliedType(tc, as)            => TypeRepr.AppliedType(substTp(tc, m), as.map(substTp(_, m)))
    case other                                   => other

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

  private def stat(s: Statement, i: Int): String = s match
    // a commented STATEMENT: its comments at the statement's own indent, then the statement. A
    // DEFINITION never arrives here wrapped — it carries its own `leading` field — so this is
    // exactly the block-statement case and nothing else.
    case c: Tree.Commented => leading(c.leading, i) + stat(c.stmt, i)
    case c: Tree.ClassDef => classDef(c, i)
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
    case d: Tree.DefDef if isInitBlock(d) =>
      d.rhs.map(r => s"${declNotes(d.symbol, i)}${ind(i)}locally ${term(r, i)}").getOrElse("")
    case d: Tree.DefDef   => defDef(d, i)
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
      supName.toList.flatMap { sn =>
        sup.toList.filter((k, _) => mixins(k) && !ownKeys(k)).sortBy((k, _) => k._1).map { (_, d) =>
          val n   = esc(sym(d.symbol).name)
          val pss = d.paramss.map(paramClause).mkString
          val as  = d.paramss.map(ps => ps.map(v => esc(sym(v.symbol).name)).mkString("(", ", ", ")")).mkString
          s"${ind(i)}override def $n$pss: ${tpe(d.returnTpt.tpe)} = super[$sn].$n$as"
        }
      }

  private def defDef(d: Tree.DefDef, i: Int): String =
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
    val needsUnreachable = !isCtor && !isUnitType(d.returnTpt.tpe) && d.rhs.exists(endsInInfiniteLoop)
    val rhs =
      if isCtor then s" = ${ctorBody(d, i)}"
      else d.rhs.map(r =>
        if needsUnreachable then s" = {\n${ind(i + 1)}${term(r, i + 1)}\n${ind(i + 1)}throw new java.lang.RuntimeException(\"unreachable\")\n${ind(i)}}"
        else s" = ${term(r, i)}").getOrElse("")
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
  /** names the `def` that carries a lambda body containing `return` — see the `Tree.Lambda` case. */
  private var lambdaSeq = 0
  private def inLoop[A](brk: Option[String], cont: Option[String])(f: => A): A =
    val (sb, sc) = (breakTarget, contTarget)
    breakTarget = brk; contTarget = cont
    try f finally { breakTarget = sb; contTarget = sc }
  private def inSwitch[A](brk: Option[String])(f: => A): A =
    val sb = breakTarget
    breakTarget = brk
    try f finally breakTarget = sb

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

  /** the element type of something java could put in an enhanced-for: an applied generic's single
    * argument, or an array's element. `None` = not readable, which callers must treat as no evidence
    * rather than as a difference. */
  private def elementTpe(t: TypeRepr): Option[TypeRepr] = t match
    case TypeRepr.AppliedType(_, List(el)) => Some(el)
    case _                                 => scala.None

  private def loopWithJumps(body: Tree, label: Option[String], render: (=> String) => String,
                            bodyStr: => String): String =
    val lblB = label.filter(l => jumpsTo(body, l, brk = true))
    val lblC = label.filter(l => jumpsTo(body, l, brk = false))
    val hasB = breaksOut(body) || lblB.isDefined
    val hasC = continuesIn(body) || lblC.isDefined
    if !hasB && !hasC then render(bodyStr)
    else
      labelSeq += 1
      val seq  = labelSeq
      // the break boundary must be named when a body boundary sits inside it, when a labelled
      // `break` names it from a nested loop, or when some construct INSIDE the body renders with a
      // boundary of its own (`interposes`) — all three put another `Label` nearer than this one.
      val shielded = interposes(body)
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
      interposes(m.scrutinee) || m.cases.exists(c => caseNeedsBoundary(c.body) || interposes(c.body))
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

  /** a `break L` / `continue L` naming this loop, at ANY depth — a labelled jump crosses nested
    * loops and switches by definition, which is what it is for. */
  private def jumpsTo(t: Any, label: String, brk: Boolean): Boolean = t match
    case Tree.Break(Some(l), _, _) if brk     => l == label
    case Tree.Continue(Some(l), _, _) if !brk => l == label
    case xs: Iterable[?]                      => xs.exists(jumpsTo(_, label, brk))
    case Some(x)                              => jumpsTo(x, label, brk)
    case p: Product                           => p.productIterator.exists(jumpsTo(_, label, brk))
    case _                                    => false

  /** an unlabelled `continue` belonging to THIS loop. Unlike `breaksOut` it does NOT stop at a
    * `match`: java's `continue` inside a switch continues the enclosing LOOP. */
  private def continuesIn(t: Any): Boolean = t match
    case Tree.Continue(scala.None, _, _)                                  => true
    case _: Tree.While | _: Tree.DoWhile | _: Tree.For | _: Tree.ForEach  => false
    case xs: Iterable[?]                                                  => xs.exists(continuesIn)
    case Some(x)                                                          => continuesIn(x)
    case p: Product                                                       => p.productIterator.exists(continuesIn)
    case _                                                                => false

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
    * `Unit` exactly when every `return` in the body is VALUELESS — a java `void` lambda, which is
    * what a functional interface with a `void` SAM produces and the only case this can type without
    * reading the interface's abstract method. `None` means "do not rewrite", not "use Any". */
  private def lambdaResultType(body: Tree): Option[String] =
    val valued = collectReturns(body).exists(_.expr.isDefined)
    Option.when(!valued)("scala.Unit")

  private def collectReturns(t: Any): List[Tree.Return] = t match
    case r: Tree.Return                                   => List(r)
    case _: Tree.Lambda | _: Tree.DefDef | _: Tree.AnonClass => Nil
    case xs: Iterable[?]                                  => xs.toList.flatMap(collectReturns)
    case Some(x)                                          => collectReturns(x)
    case p: Product                                       => p.productIterator.toList.flatMap(collectReturns)
    case _                                                => Nil

  private def breaksOut(t: Any): Boolean = t match
    case Tree.Break(scala.None, _, _)                     => true
    case _: Tree.While | _: Tree.DoWhile | _: Tree.Match |
         _: Tree.For | _: Tree.ForEach                    => false // binds to the inner one
    case xs: Iterable[?]                                  => xs.exists(breaksOut)
    case Some(x)                                          => breaksOut(x)
    // Product reflection rather than a hand-written case per node: a hand-rolled walk that stops
    // one node short is exactly how two of this project's silent defects survived (CLAUDE.md §3),
    // and there is no generic child accessor on the TIR to use instead.
    case p: Product                                       => p.productIterator.exists(breaksOut)
    case _                                                => false

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

  private val primitiveNames = CtorFunnel.primitiveTypeNames

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
    s"${esc(sym(v.symbol).name)}: ${tpe(overrideAlign.getOrElse(v.symbol, v.tpt.tpe))}"

  private def valDef(v: Tree.ValDef, i: Int): String =
    // trivia, then the porter note, then the `val` — see `defDef` for why that order is a rule.
    val note = declNotes(v.symbol, i)
    if v.leading.nonEmpty then leading(v.leading, i) + note + valDef0(v.copy(leading = Nil), i)
    else note + valDef0(v, i)

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

  /** a java CONSTANT VARIABLE: `static final`, primitive or `String`, literal initialiser. */
  private def isJavaConstant(v: Tree.ValDef, s: Symbol): Boolean =
    s.flags.isStatic && s.flags.isFinal && !s.flags.isMutable &&
      (v.rhs match { case Some(_: Tree.Literal) => true; case _ => false }) &&
      (v.tpt.tpe match
        case TypeRepr.TypeRef(_, x) =>
          val n = sym(x).fullName
          primitiveNames(n) || n == "java.lang.String"
        case _ => false)

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
                 else s"(${a.args.map((k, v) => s"$k = ${term(v, i)}").mkString(", ")})"
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
      if f.isSealed then "sealed " else "",
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
    if s.flags.isStatic && s.fullName.contains('$') then s.fullName.replace('$', '.')
    else if currentDeclared(id) || inheritedNested(s.owner) then esc(s.name)
    else s.fullName.replace('$', '.')

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

  private def term(t: Term, i: Int): String = t match
    case Tree.Ident(s, _, _)            => if isTypeRef(s) then typeValue(s) else staticRef(s)
    case Tree.Literal(c, _, _)          => constant(c)
    case Tree.This(s, _, _)             => thisRef(s)
    case Tree.Super(_, _, _)            => "super"
    case Tree.Select(q, s, _, _)        => s"${term(q, i)}.${local(s)}"
    case Tree.New(tpt, _, _, anon)      => s"new ${ctorTpe(tpt.tpe)}${anonBody(anon, i)}"
    case Tree.Apply(fun, args, _, _, _) => applyStr(fun, args, i)
    case Tree.TypeApply(fun, targs, _, _) => s"${term(fun, i)}[${targs.map(a => tpe(a.tpe)).mkString(", ")}]"
    case Tree.Assign(l, r, _, _)        => s"${term(l, i)} = ${term(r, i)}"
    case Tree.Block(stats, expr, _, _, tr) => block(stats, expr, tr, i)
    case Tree.Lambda(ps, body, _, _)    =>
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
      if !returnsIn(body) then head + term(body, i)
      else lambdaResultType(body) match
        case Some(rt) =>
          lambdaSeq += 1
          val n = s"body$$$lambdaSeq"
          head + s"{ def $n(): $rt = ${term(body, i)}; $n() }"
        // REFUSED rather than guessed: a value-returning lambda needs the SAM's result type, which
        // the TIR carries as the functional interface rather than as the method. Left alone this is
        // a loud compile error naming the exact line — which is the right outcome per ENGINE-LIMITS
        // M6, and strictly better than a `def` with a wrong result type that compiles.
        case None => head + term(body, i)
    case Tree.If(c, th, el, _, _)       => s"if (${term(c, i)}) ${term(th, i)} else ${term(el, i)}"
    case Tree.Typed(e, tpt, _, _)       => s"${operand(e, i)}.asInstanceOf[${tpe(castTarget(e, tpt.tpe))}]" // Java cast
    case Tree.Repeated(es, _, _)        => es.map(term(_, i)).mkString(", ")
    case Tree.Return(e, _, _)           => "return" + e.map(x => " " + term(x, i)).getOrElse("")
    case Tree.While(c, b, _, _, lbl)    =>
      loopWithJumps(b, lbl, bd => s"while (${term(c, i)}) $bd", term(b, i))
    case Tree.Throw(e, _, _)            => s"throw ${term(e, i)}"
    case Tree.InstanceOf(e, tpt, _, _)  => s"${term(e, i)}.isInstanceOf[${tpe(tpt.tpe)}]"
    case Tree.ArrayAccess(a, idx, _, _) => s"${term(a, i)}(${term(idx, i)})"
    case Tree.ArrayLength(a, _, _)      => s"${term(a, i)}.length"
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
      widenedBinding(b, it) match
        case None       => loopWithJumps(body, lbl, bd => s"for ($name <- ${term(it, i)}) $bd", term(body, i))
        case Some(decl) =>
          // the alias is INSIDE the loop body, so it is re-bound each iteration exactly as java's is,
          // and outside any `continue` boundary `loopWithJumps` adds — which is where java runs it.
          // Derive the fresh name from the RAW one and escape THAT: appending to the escaped form
          // gives `` `object`$e ``, which is not an identifier at all (measured, 0 -> 3 on libGDX,
          // as an E040 syntax error). A suffixed keyword needs no escape, so `esc` is a no-op here —
          // but only because it is applied to the whole name.
          val fresh = esc(s"$raw$$e")
          loopWithJumps(body, lbl,
            bd => s"for ($fresh <- ${term(it, i)}) { val $name: $decl = $fresh.asInstanceOf[$decl]; $bd }",
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
      loopWithJumps(body, lbl, bd => s"{ $is; while ($c) { $bd; $u } }", term(body, i))
    case Tree.Try(res, body, catches, fin, _, _) => tryStr(res, body, catches, fin, i)
    case Tree.Match(scr, cases, _, _)   => matchStr(scr, cases, i)
    case Tree.MethodRef(q, s, mrT, _)   =>
      val isCtor = sym(s).name == "<init>" // `Type::new` → a factory function `() => new Type()`
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

  private def applyStr(fun: Term, args: List[Term], i: Int): String = fun match
    case Tree.New(tpt, _, _, anon) =>
      s"new ${ctorTpe(tpt.tpe)}(${args.map(term(_, i)).mkString(", ")})${anonBody(anon, i)}"
    // operators (populator tags them `scala.<op>#…`) render infix / prefix, not `.op(x)`.
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
      val as = (fun match
        case Tree.Select(_, m, _, _) => alignedArgs(m, args, i)
        case Tree.Ident(m, _, _)     => alignedArgs(m, args, i)
        case _                       => scala.None
      ).getOrElse(args.map(term(_, i)))
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
    * as an operand (`a + if (c) x else y`) needs parens or Scala reads "end of statement". */
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

  private def tryStr(res: List[Tree.ValDef], body: Term, catches: List[Tree.CatchCase], fin: Option[Term], i: Int): String =
    val r  = res.map(v => s"${ind(i + 1)}${valDef(v, 0)}\n").mkString
    val cs = catches.map(c => s"${ind(i + 1)}case ${esc(sym(c.param.symbol).name)}: ${tpe(c.param.tpt.tpe)} => ${term(c.body, i + 1)}").mkString("\n")
    val cl = if catches.isEmpty then "" else s" catch {\n$cs\n${ind(i)}}"
    val fl = fin.map(f => s" finally ${term(f, i)}").getOrElse("")
    s"try ${term(body, i)}$cl$fl" // resources: r prepended when the backend lowers auto-close

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
    * "nothing can nest here" case worth the risk. */
  private def matchStr(scr: Term, cases: List[Tree.CaseDef], i: Int): String =
    val cs = cases.map { c =>
      val pat = if c.isDefault then "_" else c.labels.map(term(_, i)).mkString(" | ")
      if !caseNeedsBoundary(c.body) then s"${ind(i + 1)}case $pat => ${inSwitch(scala.None)(term(c.body, i + 1))}"
      else
        labelSeq += 1
        val n = s"case$$$labelSeq"
        val b = inSwitch(Some(n))(term(c.body, i + 1))
        s"${ind(i + 1)}case $pat => scala.util.boundary { ($n: scala.util.boundary.Label[scala.Unit]) ?=> $b }"
    }.mkString("\n")
    // the SCRUTINEE is outside the switch — a `break` cannot occur in a java expression — but it
    // is rendered AFTER the arms so that the boundary numbering does not move for a switch that
    // needed no change.
    s"${inSwitch(scala.None)(term(scr, i))} match {\n$cs\n${ind(i)}}"

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

  private def tpe(t: TypeRepr): String = t match
    case TypeRepr.NoType | TypeRepr.NoPrefix   => "Any"
    case TypeRepr.TypeRef(_, s)                => typeSym(s)
    case TypeRepr.TermRef(_, s)                => s"${typeSym(s)}.type"
    case TypeRepr.ThisType(_)                  => "this.type"
    case TypeRepr.SuperType(_, sup)            => tpe(sup)
    case TypeRepr.ConstantType(c)              => constant(c)
    case TypeRepr.AppliedType(tc, as)          => s"${tpe(tc)}[${as.map(tpe).mkString(", ")}]"
    case TypeRepr.AndType(l, r)                => s"${tpe(l)} & ${tpe(r)}"
    case TypeRepr.OrType(l, r)                 => s"${tpe(l)} | ${tpe(r)}"
    case TypeRepr.ByNameType(u)                => s"=> ${tpe(u)}"
    case TypeRepr.TypeBounds(TypeRepr.NoType, TypeRepr.NoType) => "?"
    case TypeRepr.TypeBounds(lo, hi) =>
      val l = if lo == TypeRepr.NoType then "" else s" >: ${tpe(lo)}"
      val h = if hi == TypeRepr.NoType then "" else s" <: ${tpe(hi)}"
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

object TirEmitter:

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
  def funnelParamRenames(p: Program, out: collection.mutable.Buffer[Decision] = collection.mutable.ListBuffer.empty): Program =
    val renames = collection.mutable.Map[SymId, String]()
    val plans = CtorFunnel.Plans(p)
    def nm(id: SymId): String = p.symbolOf(id).map(_.name).getOrElse("")
    def parentSyms(cd: Tree.ClassDef): List[SymId] =
      def hs(t: TypeRepr): Option[SymId] = t match
        case TypeRepr.TypeRef(_, s)      => Some(s)
        case TypeRepr.AppliedType(tc, _) => hs(tc)
        case _                           => scala.None
      cd.parents.flatMap { case tt: TypeTree => hs(tt.tpe); case t: Term => hs(t.tpe) }
    val declOf   = collection.mutable.Map[SymId, Tree.ClassDef]()
    def index(cd: Tree.ClassDef): Unit =
      declOf(cd.symbol) = cd
      cd.body.foreach { case c: Tree.ClassDef => index(c); case _ => () }
    p.units.foreach(index)
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
      cd.body.foreach { case c: Tree.ClassDef => scan(c); case _ => () }
    p.units.foreach(scan)
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
                            surface: Surface = null): Program =
    val view    = if surface eq null then TrivialSurface(p) else surface
    val renames = collection.mutable.Map[SymId, String]()
    def nm(id: SymId): String = p.symbolOf(id).map(_.name).getOrElse("")
    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s) => Some(s); case TypeRepr.AppliedType(tc, _) => headSym(tc); case _ => None
    val declOf  = collection.mutable.Map[SymId, Tree.ClassDef]()
    def index(cd: Tree.ClassDef): Unit =
      declOf(cd.symbol) = cd
      cd.body.foreach { case c: Tree.ClassDef => index(c); case _ => () }
      cd.enumCases.foreach(_.body.foreach { case c: Tree.ClassDef => index(c); case _ => () })
    p.units.foreach(index)
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
    val scanned = collection.mutable.Set[SymId]()
    def scan(cd: Tree.ClassDef): Unit =
      if scanned(cd.symbol) then return
      scanned += cd.symbol
      // parents FIRST, so `eff` above already reflects an ancestor's rename
      cd.parents.flatMap { case tt: TypeTree => headSym(tt.tpe); case t: Term => headSym(t.tpe) }
        .flatMap(declOf.get).foreach(scan)
      val shadowed = inherited(cd)
      cd.body.foreach {
        case v: Tree.ValDef if shadowed(nm(v.symbol)) && !p.symbolOf(v.symbol).exists(_.flags.isStatic) &&
                               !TirEmitter.followsBase(p, view, renames, v.symbol, "shadows-inherited") =>
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
        case c: Tree.ClassDef => scan(c)
        case _                => ()
      }
      cd.enumCases.foreach(_.body.foreach { case c: Tree.ClassDef => scan(c); case _ => () })
    p.units.foreach(scan)
    if renames.isEmpty then p
    else
      // same visibility relaxation as `resolveMemberClashes`: a renamed field must stay reachable
      // from wherever java read it — and RECORDED, see `recordClashWidening`.
      recordClashWidening(p, out, renames.keys, "shadows-inherited")
      val syms = p.symbols.all.map(s =>
        renames.get(s.id).map(n => s.copy(name = n, flags = s.flags.copy(isPrivate = false, isProtected = false))).getOrElse(s)
      )
      p.rebuilt(symbols = SymbolTable(syms))

  /** A field this run does NOT emit: does the BASE's published name settle it, so nothing is
    * re-derived? `true` when the caller must not compute a rename of its own.
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
    * a run that guessed here would emit exactly the text this exists to stop. */
  private def followsBase(p: Program, view: Surface, renames: collection.mutable.Map[SymId, String],
                          field: SymId, clash: String): Boolean =
    if view.owns(field) then false
    else
      view.memberShape(field) match
        case Surface.Answer.Own => false // cannot happen: `owns` above is the complement
        case Surface.Answer.Published(shape, _) =>
          if shape.name.nonEmpty then renames(field) = shape.name
          true
        case Surface.Answer.Unknown(why, module) =>
          view.gap(Surface.Gap(p.symbolOf(field).map(_.fullName).getOrElse("?"),
            why + s" — this run would rename it for a $clash it can only see because its own " +
              "declarations are in the same program as the base's; the local derivation stands, and " +
              "it may not be the name the base emitted",
            module, fatal = false,
            fix = "§1(b) PER-LIBRARY: declare the module that emits this field as a base " +
              "(`base = \"…\"`) and re-run it with this engine so its port map carries a `name=` row"))
          false

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
                           surface: Surface = null): Program =
    val view    = if surface eq null then TrivialSurface(p) else surface
    val renames = collection.mutable.Map[SymId, String]()
    def nm(id: SymId): String = p.symbolOf(id).map(_.name).getOrElse("")
    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s) => Some(s); case TypeRepr.AppliedType(tc, _) => headSym(tc); case _ => None
    def isModule(c: SymId): Boolean  = p.symbolOf(c).exists(_.flags.isModule)
    /** where a member of `owner` is EMITTED — the companion, or the class/object body itself. */
    def inCompanion(m: SymId, owner: SymId): Boolean =
      p.symbolOf(m).exists(_.flags.isStatic) && !isModule(owner)
    // per-class method names BY PLACEMENT, and the parent edges the instance scope is inherited along
    val instMethodsOf = collection.mutable.Map[SymId, Set[String]]()
    val statMethodsOf = collection.mutable.Map[SymId, Set[String]]()
    val childrenOf = collection.mutable.Map[SymId, List[SymId]]().withDefaultValue(Nil)
    def index(cd: Tree.ClassDef): Unit =
      val (stat, inst) = cd.body.collect { case d: Tree.DefDef => d }.partition(d => inCompanion(d.symbol, cd.symbol))
      instMethodsOf(cd.symbol) = inst.map(d => nm(d.symbol)).toSet
      statMethodsOf(cd.symbol) = stat.map(d => nm(d.symbol)).toSet
      cd.parents.foreach { case tt: TypeTree => headSym(tt.tpe).foreach(pp => childrenOf(pp) = cd.symbol :: childrenOf(pp)); case _ => () }
      cd.body.foreach { case c: Tree.ClassDef => index(c); case _ => () }
    p.units.foreach(index)
    def selfOrDescMethods(c: SymId, seen: Set[SymId] = Set.empty): Set[String] =
      if seen(c) then Set.empty
      else instMethodsOf.getOrElse(c, Set.empty) ++ childrenOf(c).flatMap(ch => selfOrDescMethods(ch, seen + c))
    def scan(cd: Tree.ClassDef): Unit =
      val instClashNames = selfOrDescMethods(cd.symbol)
      val statClashNames = statMethodsOf.getOrElse(cd.symbol, Set.empty)
      def clashes(v: Tree.ValDef): Boolean =
        if inCompanion(v.symbol, cd.symbol) then statClashNames(nm(v.symbol)) else instClashNames(nm(v.symbol))
      cd.body.foreach {
        case v: Tree.ValDef if clashes(v) && !TirEmitter.followsBase(p, view, renames, v.symbol, "field-vs-method") =>
          renames(v.symbol) = nm(v.symbol) + "$field"
          // the note's own text is unchanged by this refinement, deliberately: it already says
          // "a method of this class or of a SUBCLASS", which is the INSTANCE scope and now the
          // only thing the pass claims. A reworded `why` is emitted text (§4.575) and would move
          // every member carrying this note in every port, hiding the three that really changed.
          note(out, Decision.Kind.RenamedMember, p, v.symbol,
            Map(
              "from"  -> nm(v.symbol),
              "to"    -> renames(v.symbol),
              "clash" -> "field-vs-method",
              "owner" -> p.symbolOf(cd.symbol).map(_.fullName).getOrElse("?"),
              "why"   -> ("java keeps fields and methods in SEPARATE namespaces, so a field may " +
                "share a name with a method of this class or of a SUBCLASS; scala has one " +
                "namespace and forbids it"),
            ),
            MemberRenameRule)
        case c: Tree.ClassDef                           => scan(c)
        case _                                           => ()
      }
      cd.enumCases.foreach(_.body.foreach { case c: Tree.ClassDef => scan(c); case _ => () })
    p.units.foreach(scan)
    if renames.isEmpty then p
    else
      // also relax visibility: Java lets the enclosing class read a nested class's private
      // field (`point.x`); Scala does not, so a renamed clash-field must stay accessible — and
      // RECORDED, see `recordClashWidening`.
      recordClashWidening(p, out, renames.keys, "field-vs-method")
      val syms = p.symbols.all.map(s =>
        renames.get(s.id).map(n => s.copy(name = n, flags = s.flags.copy(isPrivate = false, isProtected = false))).getOrElse(s)
      )
      p.rebuilt(symbols = SymbolTable(syms))
