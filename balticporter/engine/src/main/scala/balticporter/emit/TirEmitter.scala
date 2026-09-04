package balticporter.emit

import balticporter.catalog.{CatalogLog, JS, Obligations, Rendering, Typing}
import balticporter.core.{EngineInfo, Provenance, Substituted}
import balticporter.tir.*

/** Emission backend: transformed TIR to Scala 3 source.
  *
  * @param externalConcrete concrete instance members of injected supertypes, keyed by FQN
  *   as `(name, param counts)`. Required for [[diamondOverrides]] to see injected parents.
  * @param provenance upstream attribution header stamped on every emitted unit. `None` = no header.
  * @param notes the run's decision log (read-only); decisions render as porter notes beside emitted code.
  */
final class TirEmitter(
    source: Program,
    externalConcrete: Map[String, Set[(String, List[Int])]] = Map.empty,
    provenance: Option[Provenance] = scala.None,
    notes: DecisionLog = new DecisionLog,
    /** Diagnostic mode: render counted refusals as `compiletime.error` instead of residue comments. */
    preview: Boolean = false,
    /** Best-effort emission (DESIGN.md §6.4): open markers render as inner term in comment fences
      * instead of `compiletime.error`. Byte-identical to normal mode at zero open markers. */
    bestEffort: Boolean = false,
    /** View over types this run does not emit (DESIGN.md §8.3). `None` = whole program is surface. */
    surfaceView: Option[Surface] = scala.None,
    /** Upstream Java source text by `Origin.javaPath`, for comment-recovery (DESIGN.md §8.8).
      * Injected so in-memory fixtures can supply text. Default reads the file. */
    javaSource: String => Option[String] = TirEmitter.readJavaSource,
    /** Catalog obligation log. Default `discarding` is correct for secondary emitters
      * (determinism twin, preview, best-effort) to avoid double-counting. */
    catalog: CatalogLog = CatalogLog.discarding,
    /** Member surface of injected Scala files (param types and arity), so emitted overrides
      * adopt the injected signature. Populated by `PortRun`; empty for specs. */
    injectedSurface: InjectedSurface.Surface = InjectedSurface.Empty,
    /** External member FQNs (`Owner#member`) emitted without parens. Calls only, not signatures. */
    externalParenless: Set[String] = Set.empty,
):
  private given CatalogLog = catalog
  private val surface: Surface = surfaceView.getOrElse(TrivialSurface(source))
  /** Decisions made during normalisation, exposed to the orchestrator. */
  private val own = collection.mutable.ListBuffer.empty[Decision]

  // Normalize away Java member-name clashes before rendering. Capture-rename runs LAST
  // so it reads effective names from the three passes above. // CLAUDE.md §4.55
  private val prepared =
    TirEmitter.resolveCapturedLocalClashes(
      TirEmitter.funnelParamRenames(
        TirEmitter.resolveFieldShadowing(
          TirEmitter.resolveMemberClashes(source, own, surface, catalog), own, surface, catalog), own, surface), own)
  /** Constructor funnel plans: which ctor becomes primary, which super(args) can be replayed. */
  private val plans = CtorFunnel.Plans(prepared, Some(surface))
  // Widen private members reached by replayed parent-ctor statements (they execute one level down).
  private val program =
    TirEmitter.widen(prepared, plans.widenedMembers, own, plans.externalReplayWidenings)

  /** Same-named overload candidates for program-declared types. Shared with `overload-risk` check. */
  private[balticporter] lazy val overloads = new balticporter.tir.OverloadRiskCheck.Overloads(program)

  /** Symbols assigned/incremented anywhere in the program. Unwritten `ValDef`s emit as `val`. */
  private lazy val writtenSyms: Set[SymId] =
    balticporter.transform.BeanCollapse.writtenSymbols(program)
  /** Whether this `ValDef` is written anywhere in the program (by SymId). */
  private def isWritten(v: Tree.ValDef): Boolean =
    writtenSyms.contains(v.symbol)

  /** Whole-program visibility plan mapping SymIds to access levels. // DESIGN.md §8.7 */
  private val visPlan: Map[SymId, Visibility.Vis] = Visibility.plan(program, own)

  /** Constructors whose replayed statements contain `.orNull`, needing `@nowarn("msg=deprecated")`.
    * Excludes ctors already bearing that annotation from NullabilityTransform. */
  private val replayOrNullCtors: Set[SymId] = {
    given Program = program
    val result = collection.mutable.Set.empty[SymId]
    def hasDeprecatedNowarn(s: Symbol): Boolean = s.annotations.exists(_.args.exists(_._2 match {
      case Tree.Literal(Constant.StringC(v), _, _) => v.contains("deprecated"); case _ => false
    }))
    program.units.foreach { u =>
      StandardTraversal.allClassDefs(u).foreach { cd =>
        CtorFunnel.ctorsOf(program, cd.body).foreach { d =>
          val s = sym(d.symbol)
          if s.name == "<init>" && !hasDeprecatedNowarn(s) then
            plans.replayFor(cd, d) match
              case Some(replay) if replay.nonEmpty && replayHasOrNull(replay) =>
                result += d.symbol
                TirEmitter.note(own, Decision.Kind.SuppressedWarning, program, d.symbol,
                  Map(
                    "annotation" -> "@nowarn(\"msg=deprecated\")",
                    "why" -> ("this constructor's REPLAY statements contain `.orNull` calls from " +
                      "the parent constructor (NullabilityTransform inserted them there); the same " +
                      "@nowarn pattern sge uses at every Java interop boundary"),
                  ),
                  "ctor-replay-orNull-suppression",
                )
              case _ => ()
        }
      }
    }
    result.toSet
  }

  /** The decisions THIS emitter made — the three §4.55 renaming passes, the replay widening, and
    * the replay `@nowarn` suppression.
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
    // Nested ClassDef goes through `stat`; top-level reaches `classDef` directly.
    val body = stat(cd, 0)
    val pkg  = if full.contains('.') then s"package ${escPath(full.substring(0, full.lastIndexOf('.')))}\n\n" else ""
    // Order: banner, upstream header (licence), porter notes, package clause.
    val text0 = header(cd) + leading(cd.unitLeading, 0) + unitNotes(cd) + pkg + body
    // Best-effort banner (assembled after body since markers are only known then).
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
    // Recover unplaced comments BEFORE source map computation.
    val text = banner + recoverTrivia(cd, text0)
    if SrcMap.enabled then recordedMap(full) = srcMapOf(full, cd, text)
    text

  /** This emitter's source map. Idempotent per unit; never process-global. */
  def srcMap: SrcMap.Recording = SrcMap.Recording(recordedMap.values.toList.flatten, recordedMisses.toList)

  private val recordedMap    = collection.mutable.LinkedHashMap.empty[String, List[SrcMap.Entry]]
  private val recordedMisses = collection.mutable.ListBuffer.empty[String]

  // Base-surface contract (DESIGN.md §8.3): recorded at emission, never re-derived.
  // Covers nested types too, not only units.

  private val recordedTypeShapes   = collection.mutable.LinkedHashMap.empty[String, Surface.TypeShape]
  private val recordedMemberShapes = collection.mutable.LinkedHashMap.empty[String, Surface.MemberShape]

  /** Emitted type/member shapes, keyed by emitted name. Per-emitter, not process-global. */
  def emittedShapes: TirEmitter.Shapes =
    TirEmitter.Shapes(recordedTypeShapes.toMap, recordedMemberShapes.toMap)

  /** Surface contract gaps: unanswerable questions plus D6 cross-module `object` collisions. */
  def surfaceGaps: List[Surface.Gap] = collapsedBaseTypesNamed

  /** Members renamed by this emitter's §4.55 passes, by symbol to original Java name.
    * Only emitter renames, not phase renames. // ENGINE-LIMITS K28.1 */
  private lazy val renamedMembers: Map[SymId, String] =
    own.iterator.collect {
      case d if d.kind == Decision.Kind.RenamedMember && d.subject != SymId.None &&
                d.detail.get("to").contains(program.symbolOf(d.subject).map(_.name).getOrElse("")) &&
                d.detail.get("from").exists(_.nonEmpty) =>
        d.subject -> d.detail("from")
    }.toMap

  /** Access level as actually rendered: `private`, `private[Outer]`, or `public`.
    * Java `protected` is emitted as public (loosening can only remove errors). */
  private def visOf(s: Symbol, ownerSym: SymId): String =
    if !s.flags.isPrivate then "public"
    else privateQualifier(ownerSym).fold("private")(o => s"private[$o]")

  private def recordTypeShape(cd: Tree.ClassDef, form: String, plan: CtorFunnel.Plan,
                              companion: Boolean, statics: List[String]): Unit =
    val s  = sym(cd.symbol)
    val ps = plan.primary.map(_.symbol)
    // Primary's slots in descriptor grammar; synthesised primaries derive from plan pairs.
    val primary: Option[Descriptor] =
      if s.flags.isTrait || s.flags.isModule then scala.None
      else if plan.isSynthesised then
        Some(Descriptor(plan.synthetic.map((_, t) => descriptorParam(t)) ++
          // Marker slot is spelled by simple name only. // DESIGN.md §8.1 F4
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

  /** Emitted secondary constructors: not promoted, not delegation-only nilary. */
  private def secondariesOf(cd: Tree.ClassDef, plan: CtorFunnel.Plan): List[Descriptor] =
    if sym(cd.symbol).flags.isModule then Nil
    else
      given Program = program
      val paramful = plans.paramfulPrimaryOf(cd)
      CtorFunnel.ctorsOf(program, cd.body)
        .filterNot(d => plan.primary.exists(_.symbol == d.symbol))
        .filterNot(d => !paramful && CtorFunnel.delegationOnlyNilary(program, d).isDefined)
        .map(d => Descriptor(CtorFunnel.valueParams(program, d).map(v => descriptorParam(v.tpt.tpe))))

  /** One type in the descriptor grammar, via `Descriptor.ofInfo`. */
  private def descriptorParam(t: TypeRepr): Param =
    Descriptor.ofInfo(program, TypeRepr.MethodType(List("_" -> t), TypeRepr.NoType))
      .flatMap(_.params.headOption).getOrElse(Param.Unresolved)

  // Porter notes: indexed by SymId (survives renames), not by name.

  private lazy val noteIndex: Map[SymId, List[Decision]] =
    notes.all.filter(d => PorterNote.Rendered(d.kind) && d.subject != SymId.None)
      .groupBy(_.subject)
      // Stable sort within a subject for deterministic output.
      .view.mapValues(_.sortBy(d => (d.kind.toString, d.reason.className, d.reason.detail, d.tsv))).toMap

  /** Decisions with no subject symbol, grouped by FQN (drops/injections of uninterned types). */
  private lazy val noteIndexByFqn: Map[String, List[Decision]] =
    notes.all.filter(d => PorterNote.Rendered(d.kind) && d.subject == SymId.None)
      .groupBy(_.subjectFqn).view.mapValues(_.sortBy(_.tsv)).toMap

  // Per-unit, cleared on re-emission for idempotence.
  private val recordedNotes = collection.mutable.LinkedHashMap.empty[String, collection.mutable.ListBuffer[PorterNote.Printed]]
  private def printedNotes: collection.mutable.ListBuffer[PorterNote.Printed] =
    recordedNotes.getOrElseUpdate(currentUnitName, collection.mutable.ListBuffer.empty)

  /** All notes this emitter printed, in order. Input to [[NoteCoverageCheck]]. */
  def notesPrinted: List[PorterNote.Printed] = recordedNotes.values.toList.flatten

  // Context clause: track when a `(using T)` clause cannot be rendered (trait, enum, nilary).
  // // ENGINE-LIMITS CT5
  private val clauseLost = collection.mutable.LinkedHashMap.empty[SymId, TirEmitter.ClauseLoss]

  /** Types whose constructors carry a context clause the emitted header does not render. */
  def contextClauseLosses: List[TirEmitter.ClauseLoss] = clauseLost.values.toList

  /** Record whether the emitted header dropped a context clause. */
  private def checkClause(cd: Tree.ClassDef, rendered: Boolean, form: String): Unit =
    if !rendered && CtorFunnel.ctorsCarryGivens(program, cd) then
      clauseLost(cd.symbol) = TirEmitter.ClauseLoss(
        cd.symbol, sym(cd.symbol).fullName, form, cd.origin)
    else clauseLost.remove(cd.symbol)

  /** Render notes for `s` matching `kinds` at indent `i`. Returns `""` when none. */
  private def noteBlock(s: SymId, i: Int, kinds: Set[Decision.Kind]): String =
    noteIndex.get(s).map(_.filter(d => kinds(d.kind))) match
      case Some(ds) if ds.nonEmpty =>
        val ind0 = ind(i)
        ds.map { d =>
          printedNotes += PorterNote.Printed(d.kind, d.subject, d.subjectFqn, currentUnitName)
          PorterNote.render(d, ind0)
        }.mkString
      case _ => ""

  /** Notes above a definition (def/val/nested class). */
  private def declNotes(s: SymId, i: Int): String = noteBlock(s, i, PorterNote.AtDeclaration)

  /** Notes at body head for dropped members (no declaration to attach to). */
  private def bodyNotes(s: SymId, i: Int): String = noteBlock(s, i, PorterNote.InBody).stripSuffix("\n")

  /** File-level notes for the top-level unit symbol (e.g. namespace rename). */
  private def unitNotes(cd: Tree.ClassDef): String = declNotes(cd.symbol, 0)

  private var currentUnitName: String = ""

  // Preview mode: `compiletime.error` instead of residue comments. // ENGINE-LIMITS M6
  // Per-unit, cleared on re-emission for idempotence.
  private val recordedEmission =
    collection.mutable.LinkedHashMap.empty[String, collection.mutable.ListBuffer[Decision]]
  private def emissionOf: collection.mutable.ListBuffer[Decision] =
    recordedEmission.getOrElseUpdate(currentUnitName, collection.mutable.ListBuffer.empty)

  /** Emission-time decisions, per unit (idempotent). Drained by `PortRun`. */
  def emissionDecisions: List[Decision] = recordedEmission.values.toList.flatten

  /** Whether rendering `unit` recorded decisions or notes (makes it ineligible for action cache). */
  def recordedForCache(unit: String): Boolean =
    recordedEmission.get(unit).exists(_.nonEmpty) || recordedNotes.get(unit).exists(_.nonEmpty)

  /** Emit residue comment (shipping) or `compiletime.error` (preview) for an unrenderable construct. */
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

  // Source map: member -> emitted line range -> Java Origin. // DESIGN.md §6.3
  // Positions recovered by searching finished text for remembered slot strings (pre-order).

  private final class Slot(val member: String, val kind: String, val origin: Origin, val indent: Int):
    var text: String = ""
  private val slots   = collection.mutable.ArrayBuffer.empty[Slot]
  private val stmtSeq = collection.mutable.Map.empty[String, Int]

  /** Like [[stat]] but records the rendered text for source-map and comment recovery. */
  private def memberStat(s: Statement, i: Int): String =
    val slot = new Slot(memberKey(s), memberKind(s), s.origin, i)
    slots += slot
    recordMemberShape(slot.member, s)
    val t = stat(s, i)
    slot.text = t
    t

  /** Record member's contract row (DESIGN.md §8.3), keyed by source-map key. */
  private def recordMemberShape(key: String, st: Statement): Unit =
    val symId = st match
      case d: Definition => Some(d.symbol)
      case _             => scala.None
    symId.foreach { id =>
      val m = sym(id)
      recordedMemberShapes(key) = Surface.MemberShape(
        // Emitted simple name, only where it differs from Java's.
        name      = renamedMembers.get(id).filter(_ != m.name).map(_ => m.name).getOrElse(""),
        vis       = visOf(m, currentOwnerSym),
        // Static members land in the companion.
        placement = if m.flags.isStatic then "companion" else "class",
        // Whether this member is a collapsed bean pair and into which shape.
        form      = collapsedForms.getOrElse(id, ""),
      )
    }

  /** Collapsed bean properties by symbol, from phase decisions (not emitter's own renames). */
  private lazy val collapsedForms: Map[SymId, String] =
    notes.all.iterator.collect {
      case d if d.kind == Decision.Kind.CollapsedProperty && d.subject != SymId.None &&
                d.detail.get("form").exists(_.nonEmpty) =>
        d.subject -> d.detail("form")
      // NullaryArity: dropped `()` is `form=parenless`.
      case d if d.kind == Decision.Kind.ParenlessConversion && d.subject != SymId.None =>
        d.subject -> "parenless"
    }.toMap

  /** Stable member identity: `owner#name(paramTypes)` for defs, `owner#name` for others,
    * ordinal for unsymboled statements. */
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

  /** Simple type rendering for overload disambiguation in member keys. */
  private def shortTpe(t: TypeRepr): String = t match
    case TypeRepr.AppliedType(tc, as) if as.nonEmpty => shortTpe(tc) + as.map(shortTpe).mkString("<", ",", ">")
    case _                                           => headSymOf(t).map(x => sym(x).name).getOrElse("?")

  // Recovery backstop (DESIGN.md §8.8): unplaced Java comments are put back after the
  // enclosing member slot, with java coordinates. Dropped members' comments are not recovered.

  /** All declarations (emitted and dropped) per java file, computed once. */
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

  /** Repo-relative source path for headers. Compares via `toRealPath` (CLAUDE.md §5.4).
    * Falls back to raw path with warning when unconfigured. */
  private def sourcePathOf(o: Origin, p: Provenance): String =
    val raw = o.javaPath
    if raw.isEmpty || raw == "<synthetic>" || raw == "<unknown>" then
      "<unknown — the frontend recorded no source origin for this unit>"
    else
      val root   = p.sourceRoot.stripSuffix("/")
      val marker = p.sourcePathPrefix.stripSuffix("/")
      // §5.4: realpath both operands via `RealPath`.
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

  /** Every class at any depth, via `StandardTraversal.allClassDefs`. // ENGINE-LIMITS F8 */
  private lazy val allDeclaredClasses: List[Tree.ClassDef] =
    program.units.flatMap(u => StandardTraversal.allClassDefs(u)(using program))

  /** Type symbols appearing as parents anywhere -- prevents collapsing to `object`. */
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

  /** Type symbols the program instantiates -- prevents collapsing to `object`. */
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

  /** Type symbols named in type positions elsewhere (declaration types + class literals).
    * Prevents collapsing to `object`. Excludes self-references via owner chain;
    * class literals in own unit DO count (log-tag idiom). */
  private lazy val typeNamedElsewhere: Set[SymId] =
    given Program = program
    val out = collection.mutable.Set[SymId]()

    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s)      => Some(s)
      case TypeRepr.AppliedType(tc, _) => headSym(tc)
      case _                           => None

    /** Enclosing symbols from `s` up through its owner chain. */
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

    // (2) class literals (own unit NOT subtracted -- log-tag idiom).
    program.units.foreach { u =>
      out ++= StandardTraversal.scanClassDef(u, Set.empty[SymId]) { (acc, term) =>
        term match
          case Tree.Literal(Constant.ClassOfC(t), _, _) => acc ++ typesIn(t)
          case _                                        => acc
      }
    }
    out.toSet

  /** Cross-module D6: base types this module names in type position that the base collapsed to `object`. */
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
          // Non-published types are ordinary (JDK) and not reported here.
          case _ => Nil
      }
      .sortBy(_.subject)

  /** All type symbols declared in this unit (via `StandardTraversal`). */
  private def declaredTypes(cd: Tree.ClassDef): Set[SymId] =
    StandardTraversal.allClassDefs(cd)(using program).map(_.symbol).toSet

  /** head symbols of a class's parent types (extends + mixins). */
  private def parentSymsOf(cd: Tree.ClassDef): List[SymId] =
    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s) => Some(s)
      case TypeRepr.AppliedType(tc, _) => headSym(tc)
      case _ => None
    cd.parents.flatMap { case tt: TypeTree => headSym(tt.tpe); case term: Term => headSym(term.tpe) }

  /** Statics for a type: reads base's published `statics=` for non-owned types. // DESIGN.md §8.3 */
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

  /** Each type's own declared static member names. */
  private lazy val ownStaticsBySym: Map[SymId, Set[String]] =
    val m = collection.mutable.Map[SymId, Set[String]]()
    def scan(cd: Tree.ClassDef): Unit =
      m(cd.symbol) = basePublishedStatics(cd.symbol).getOrElse(
        cd.body.collect { case d: Definition if sym(d.symbol).flags.isStatic => esc(sym(d.symbol).name) }.toSet)
    allDeclaredClasses.foreach(scan); m.toMap

  /** Static names delivered by companion re-export of `s`, mapped to declaring type. */
  private def staticOwnersOf(s: SymId, seen: Set[SymId] = Set.empty): Map[String, SymId] =
    if seen(s) then Map.empty
    else
      val inherited = parentsBySym.getOrElse(s, Nil)
        .foldLeft(Map.empty[String, SymId])((acc, p) => staticOwnersOf(p, seen + s) ++ acc)
      inherited ++ ownStaticsBySym.getOrElse(s, Set.empty).map(_ -> s).toMap

  /** Each type's static members by emitted name, with their symbol. */
  private lazy val ownStaticSymsBySym: Map[SymId, Map[String, SymId]] =
    val m = collection.mutable.Map[SymId, Map[String, SymId]]()
    def scan(cd: Tree.ClassDef): Unit =
      // Exclude static initializer blocks (`<clinit>` cannot be a Scala identifier).
      m(cd.symbol) = cd.body.collect {
        case d: Definition if sym(d.symbol).flags.isStatic &&
          (!d.isInstanceOf[Tree.DefDef] || !isInitBlock(d.asInstanceOf[Tree.DefDef])) =>
          esc(sym(d.symbol).name) -> d.symbol
      }.toMap
    allDeclaredClasses.foreach(scan); m.toMap

  /** Non-public statics to exclude from companion re-export (prevents visibility leak). */
  private def nonPublicStatics(delivered: Map[String, SymId]): Set[String] =
    delivered.collect {
      case (n, owner) if ownStaticSymsBySym.getOrElse(owner, Map.empty).get(n)
        .exists(id => visPlan.getOrElse(id, Visibility.Vis.Public) != Visibility.Vis.Public) => n
    }.toSet

  /** Each type's parent symbols. */
  private lazy val parentsBySym: Map[SymId, List[SymId]] =
    val m = collection.mutable.Map[SymId, List[SymId]]()
    allDeclaredClasses.foreach(cd => m(cd.symbol) = parentSymsOf(cd)); m.toMap

  /** Whether this type or any ancestor has static members. */
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

  /** A method symbol's declared parameter types, or empty for non-method info. */
  private def methodParams(id: SymId): List[TypeRepr] = sym(id).info match
    case TypeRepr.MethodType(ps, _, _)                       => ps.map(_._2)
    case TypeRepr.PolyType(_, TypeRepr.MethodType(ps, _, _)) => ps.map(_._2)
    case _                                                   => Nil

  /** Members indexed by (owner, name) for callee-based arity resolution. // CLAUDE.md §4.56 */
  private lazy val membersByOwnerName: Map[(SymId, String), List[SymId]] =
    val buf = collection.mutable.Map.empty[(SymId, String), List[SymId]]
    program.symbols.all.foreach { s =>
      if s.owner != SymId.None && s.name.nonEmpty then
        val key = (s.owner, s.name)
        buf(key) = s.id :: buf.getOrElse(key, Nil)
    }
    buf.toMap

  /** Whether the callee for `memberName` on `typeSym` has parens. Walks ancestry,
    * falls back to injected surface, externalParenless, subtypes, then runtime shims.
    * Default `false` (parenless, as for extension methods). */
  private def calleeHasParens(typeSym: SymId, memberName: String): Boolean =
    def checkMember(owner: SymId): Option[Boolean] =
      membersByOwnerName.get((owner, memberName)).flatMap { ids =>
        ids.collectFirst {
          case id if program.owns(id) =>
            program.definitionOf(id) match
              case Some(d: Tree.DefDef) => d.paramss.nonEmpty
              case _                    => true
          case id =>
            sym(id).info match
              case _: TypeRepr.MethodType => true
              case _                     => false
        }
      }

    def walkAncestors(s: SymId, seen: Set[SymId]): Option[Boolean] =
      if seen(s) || s == SymId.None then None
      else
        checkMember(s).orElse {
          val newSeen = seen + s
          parentsBySym.getOrElse(s, Nil).iterator
            .filterNot(newSeen)
            .flatMap(a => walkAncestors(a, newSeen))
            .nextOption()
        }

    walkAncestors(typeSym, Set.empty).getOrElse {
      // Fallbacks: (0) injected surface, (0.5) externalParenless, (1) subtypes, (2) runtime shims.
      val ownerFqn = program.symbolOf(typeSym).map(_.fullName).getOrElse("")
      val fromInjected = injectedSurface.memberHasParens(ownerFqn, memberName)
      if fromInjected.isDefined then fromInjected.get
      // Fallback 0.5: manifest-declared external parenless members.
      else if externalParenless.contains(s"$ownerFqn#$memberName") then false
      else if program.owns(typeSym) then false
      else
        val visited = ancestorsOf(typeSym) + typeSym
        // Fallback 1: check program-declared subtypes
        val fromSubtype = parentsBySym.iterator.exists { case (child, parents) =>
          program.owns(child) && parents.exists(visited) &&
            checkMember(child).contains(true)
        }
        if fromSubtype then true
        else
          // Fallback 2: runtime shim types use java arity. // CLAUDE.md §4.5
          val runtimePrefix = balticporter.core.RuntimeArtifact.Package + ".Java"
          program.symbolOf(typeSym).exists(_.fullName.startsWith(runtimePrefix))
    }

  /** Is this member listed in `externalParenless`? Matches `Owner#name` against the set. */
  private def isExternalParenless(m: SymId): Boolean =
    if externalParenless.isEmpty then false
    else
      val ownerSym = sym(m).owner
      if ownerSym == SymId.None then false
      else externalParenless.contains(s"${sym(ownerSym).fullName}#${sym(m).name}")

  /** backtick an identifier that collides with a Scala keyword. */
  private def esc(name: String): String = TirEmitter.esc(name)

  /** backtick every keyword SEGMENT of a qualified name (§4.56 separators). */
  private def escPath(path: String): String = TirEmitter.escPath(path)

  /** Whether this type is an unresolved type variable (marker name, must not reach output). */
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
      else go(sx.owner).map(p =>
        if sx.flags.isStatic then s"$p.${esc(sx.name)}" else s"$p${outerFill(sx.owner)}#${esc(sx.name)}")
    // The fallback fires exactly when an owner is UNKNOWN, which for a type we do not define means
    // an external/JDK one. Name those with `.`: a Java nested type is reached as `Outer.Inner` in
    // Scala, and a `#` projection is not even available — it needs the prefix to be an immutable
    // path, which a bare external class name is not (`java.nio.channels.FileChannel#MapMode`).
    go(id).getOrElse:
      val sep = if program.definitionOf(id).isEmpty then '.' else '#'
      escPath(sym(id).fullName).replace('$', sep)

  /** THE `[?, …]` A PROJECTION'S PREFIX NEEDS when the enclosing class is GENERIC.
    *
    * `Outer#Inner` is not a legal projection where `Outer` takes type parameters: the prefix has to
    * be a TYPE, and an unapplied type constructor is not one — scalac reads it as
    * `Found: Outer / Required: ?{ Inner: ? }`, which names neither the missing arguments nor the
    * construct. Java writes exactly this: an inner class of a generic outer, referred to RAW from
    * another file (`import …ListView.ListAdapterListener; … ListAdapterListener viewListener;`) is
    * ordinary java and carries no arguments for the port to render.
    *
    * `?` per parameter is the reference hand port's own rendering of every raw generic (§3.5), and
    * it is the only answer available here: the TIR's reference is to the NESTED symbol and the
    * outer's arguments are nowhere on it, so filling from the enclosing scope would be inventing an
    * instantiation java did not write. PROBED against scalac 3.8.4 before it was written —
    * `ListView[?]#ListAdapterListener` type-checks as a field type, as a formal, across an override
    * edge, and at a call passing `new lv.ListAdapterListener`.
    *
    * Empty for a non-generic owner, which is every other projection this emitter writes. */
  private def outerFill(owner: SymId): String =
    program.definitionOf(owner).collect { case c: Tree.ClassDef => c.tparams.size }
      .filter(_ > 0).map(n => List.fill(n)("?").mkString("[", ", ", "]")).getOrElse("")

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

  /** Parameter symbol to type for raw-parent override alignment. Uses `deWildcardedArgs`
    * substitution so parent and override agree by construction. */
  private def rawParentAlignment: Map[SymId, TypeRepr] =
    val out    = collection.mutable.Map[SymId, TypeRepr]()
    val done   = collection.mutable.Set[SymId]()
    val declOf = collection.mutable.Map[SymId, Tree.ClassDef]()
    allDeclaredClasses.foreach(cd => declOf(cd.symbol) = cd)
    def methodsOf(cd: Tree.ClassDef) = cd.body.collect {
      case d: Tree.DefDef if sym(d.symbol).name != "<init>" => d
    }
    // Lazy: only built when a wildcard-carrying override needs it.
    lazy val graph = OverrideGraph.build(program)
    val chainOf = collection.mutable.Map[SymId, Set[SymId]]()
    /** Override of `od` reached through parent `pcd`, via `OverrideGraph` (filtered to that edge's chain). */
    def inheritedThrough(od: Tree.DefDef, pcd: Tree.ClassDef): Option[Tree.DefDef] =
      val chain = chainOf.getOrElseUpdate(pcd.symbol,
        (pcd.symbol :: graph.ancestorsOf(pcd.symbol)).toSet)
      graph.overridden(od.symbol).iterator
        .filter(m => chain(graph.ownerOf(m)))
        .flatMap(m => program.definitionOf(m).collect { case d: Tree.DefDef => d })
        .nextOption()
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
          for
            od <- ours
            pd <- inheritedThrough(od, pcd)
            (ops, pps) <- od.paramss.zip(pd.paramss)
            (op, pp)   <- ops.zip(pps)
            if hasWildcardArg(op.tpt.tpe) && !out.contains(op.symbol)
          do
            val aligned = substTp(out.getOrElse(pp.symbol, pp.tpt.tpe), subst)
            // Require head constructor agreement (guard for approximate descriptor-missing edges).
            if !hasWildcardArg(aligned) && headSymOf(aligned) == headSymOf(op.tpt.tpe) then
              out(op.symbol) = aligned
              // JS-G06 citation (not obligation -- whole-program pass).
              catalog.cite(JS.G(6), sym(od.symbol).fullName)
    program.units.foreach(u => declOf.values.foreach(visit(_, Set.empty)))
    out.toMap

  private lazy val overrideAlign: Map[SymId, TypeRepr] = rawParentAlignment

  /** Override alignment against injected parents' wildcard bounds. // ENGINE-LIMITS K35 (closed) */
  private lazy val injectedOverrideTypes: Map[SymId, TypeRepr] =
    if injectedSurface.isEmpty then Map.empty
    else
      val out = collection.mutable.Map[SymId, TypeRepr]()
      for
        cd  <- allDeclaredClasses
        p   <- cd.parents
        pt   = p match { case tt: TypeTree => tt.tpe; case t: Term => t.tpe }
        pSym <- headSymOf(pt).flatMap(program.symbolOf)
        if Substituted.tags(pSym)
      do
        // Build a type-parameter substitution map from the parent's type params to the child's
        // actual type arguments. e.g. for `extends Pool[T]` where Pool has type param `A`:
        // Map("A" -> TypeRepr representing T)
        val parentTParams = injectedSurface.typeParams.getOrElse(pSym.fullName, Nil)
        val actualArgs: List[TypeRepr] = pt match
          case TypeRepr.AppliedType(_, args) => args
          case _ => Nil
        val tparamSubst: Map[String, TypeRepr] =
          if parentTParams.size == actualArgs.size then parentTParams.zip(actualArgs).toMap
          else Map.empty

        for
          od  <- cd.body.collect { case d: Tree.DefDef if sym(d.symbol).name != "<init>" => d }
          injSig <- injectedSurface.lookup(pSym.fullName, sym(od.symbol).name,
                      od.paramss.flatten.size)
          injParams = injSig.paramTypes.flatten
          (ops, ip) <- od.paramss.flatten.zip(injParams)
          if !overrideAlign.contains(ops.symbol) // do not override rawParentAlignment
        do
          // Detect if the injected type has a wildcard bound that the TIR type does not.
          // Parse the injected type string to detect `? <: X` patterns and build a TypeRepr.
          val tirType = ops.tpt.tpe
          val injRendered = ip.rendered
          val aligned = alignToInjected(tirType, injRendered, tparamSubst)
          if aligned != tirType then out(ops.symbol) = aligned
      out.toMap

  /** Align a TIR type to an injected parent's wildcard bounds. */
  private def alignToInjected(tirType: TypeRepr, injected: String,
                               tparamSubst: Map[String, TypeRepr]): TypeRepr =
    // Quick check for wildcard in injected type.
    if !injected.contains("?") then tirType
    else tirType match
      case TypeRepr.AppliedType(tc, tirArgs) =>
        // Parse injected type's arguments for wildcard bounds.
        val injArgStr = extractTypeArgs(injected)
        if injArgStr.size != tirArgs.size then tirType
        else
          val newArgs = tirArgs.zip(injArgStr).map { (tirArg, injArg) =>
            val trimmed = injArg.trim
            if trimmed.startsWith("?") then
              // Parse bound: `? <: X`, `? >: X`, or bare `?`.
              val upperBound = """^\?\s*<:\s*(.+)$""".r
              val lowerBound = """^\?\s*>:\s*(.+)$""".r
              trimmed match
                case upperBound(boundName) =>
                  val resolvedBound = tparamSubst.getOrElse(boundName.trim, tirArg)
                  TypeRepr.TypeBounds(TypeRepr.NoType, resolvedBound)
                case lowerBound(boundName) =>
                  val resolvedBound = tparamSubst.getOrElse(boundName.trim, tirArg)
                  TypeRepr.TypeBounds(resolvedBound, TypeRepr.NoType)
                case _ =>
                  TypeRepr.TypeBounds(TypeRepr.NoType, TypeRepr.NoType)
            else tirArg
          }
          if newArgs == tirArgs then tirType
          else TypeRepr.AppliedType(tc, newArgs)
      case _ => tirType

  /** Extract type argument strings from a rendered type like `Foo[A, B, ? <: C]`. */
  private def extractTypeArgs(rendered: String): List[String] =
    val i = rendered.indexOf('[')
    if i < 0 then Nil
    else
      val inner = rendered.substring(i + 1, rendered.lastIndexOf(']'))
      // Split on commas at depth 0 (not inside nested brackets)
      val args = List.newBuilder[String]
      var depth = 0
      val sb = new StringBuilder
      for c <- inner do
        if c == '[' then { depth += 1; sb.append(c) }
        else if c == ']' then { depth -= 1; sb.append(c) }
        else if c == ',' && depth == 0 then { args += sb.toString; sb.clear() }
        else sb.append(c)
      if sb.nonEmpty then args += sb.toString
      args.result()

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
    // …and a CONDITIONAL over them, which JLS 15.25 makes a poly expression in its own right: java
    // pushes the target type through the `?:` and types each branch against it. Rendered as a CAST
    // this is the failure `polyExpression`'s refusal is about, one node out — the branches
    // elaborate to `Function1`s first and the cast then asserts that a `Function1` is the
    // functional interface, which throws. As an ASCRIPTION scala propagates the expected type into
    // both arms exactly as java did.
    case Tree.If(_, th, el, _, _)           => polyOperand(th) && polyOperand(el)
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
              case Tree.Assign(Tree.Ident(sy, _, _), _, _, _, _) if ps(sy) => acc += sy
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
    // A class-to-trait converted parent has no constructor, so super args must NOT be rendered
    // on the extends clause even when the funnel's plan carries them (the funnel promoted the
    // widest super-calling constructor as primary so its params become class params, but the
    // super args target a trait's abstract vals, not a constructor). Without this guard the
    // emitter renders `extends Pool(cap$p, max$p)` — `Pool does not take parameters`.
    def parentIsTrait: Boolean =
      def headSym(t: TypeRepr): Option[SymId] = t match
        case TypeRepr.TypeRef(_, s) => Some(s); case TypeRepr.AppliedType(tc, _) => headSym(tc); case _ => None
      superTpe.flatMap(headSym).flatMap(program.symbolOf).exists(_.flags.isTrait)
    val parents = cd.parents.map(parent).filter(_.nonEmpty) match
      case Nil                          => Nil
      case h :: t if superArgs.nonEmpty && !parentIsTrait =>
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
      val ob0 = classBodyStats(orderBody(members, cd.symbol, paramfulPrimary), plan, i + 1).filter(_.nonEmpty).mkString("\n")
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
    val (statics, instance0) = if s.flags.isModule then (Nil, loweredBody) else loweredBody.partition(isStatic)
    // T22 — an `@interface`'s ELEMENTS (JLS 9.6.1), which are the whole of its instance side. They
    // become the emitted class's CONSTRUCTOR PARAMETERS, so they are taken out of the body here,
    // BEFORE `memberStat` runs: rendered as members they were emitted into a `body` the annotation
    // arm below then discards, which left four planned-and-never-written slots per port — one
    // `!! UNLOCATABLE` row each, under a key whose owner had been composed twice, and a javadoc the
    // trivia backstop then relocated because its declaration was not there (§4.58's recovery lane
    // reading high for a category that still wants a home). Both are that discarded rendering, not
    // two defects.
    val (annotElems, instance) =
      if !s.flags.isAnnotation then (Nil, instance0)
      else instance0.partition {
        case d: Tree.DefDef => sym(d.symbol).name != "<init>" && d.paramss.forall(_.isEmpty)
        case _              => false
      }
    val self    = cd.selfType.map(st => s"${ind(i + 1)}self: ${tpe(st.tpe)} =>\n").getOrElse("")
    val body1   = joinStats(classBodyStats(orderBody(instance, cd.symbol, paramfulPrimary), plan, i + 1).filter(_.nonEmpty))
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
    // JS-C44 — note placed after cnote per §4.575 order.
    val (seal, sealNote) = sealOf(cd, s, i)
    // T22 — `@interface` elements become the class's parameter list (a `val`, keeping java's
    // element name); a read becomes the field selection `applyStr0` renders parenless. No JVM
    // retention: `getAnnotation` still cannot recover one reflectively.
    val annotElemParams = annotElems.collect { case d: Tree.DefDef =>
      val nm  = esc(sym(d.symbol).name)
      val df  = d.rhs.map(r => s" = ${term(r, i)}").getOrElse("")
      s"val $nm: ${tpe(d.returnTpt.tpe)}$df"
    }
    val annotPrim = if annotElemParams.isEmpty then prim else s"(${annotElemParams.mkString(", ")})$prim"
    // each element's Javadoc joins the class's, since scala documents a primary ctor's params there.
    val annotLead = annotElems.flatMap { case d: Tree.DefDef => d.leading; case _ => Nil }
    val cls     =
      if s.flags.isAnnotation then
        s"${leading(cd.leading ++ annotLead, i)}$cnote${annots(s, i)}${ind(i)}class ${esc(s.name)}$tps$annotPrim extends scala.annotation.StaticAnnotation"
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
  /** A java ENUM takes ONE of two shapes, and which one is a fact about java's own declaration —
    * [[balticporter.tir.EnumShape]], read here, at every `values()` call site and by
    * `OmissionCheck.enumShapeRefusals`, so the three can never disagree.
    *
    * The scala 3 `enum` is the shape that IS a `java.lang.Enum[X]`, which no `sealed abstract class`
    * may claim ("only enums defined with the enum syntax can"). Everything a bound like
    * `<E extends Enum<E> & BitField>` asks of a ported enum — and everything `EnumSet`, `EnumMap`
    * and `Comparable<E>` ask — is answered by that supertype and by nothing else.
    *
    * Where a constant carries a class BODY, or an emitted member would collide with one of the
    * members java made FINAL on `java.lang.Enum`, the `enum` form cannot express java's declaration
    * at all and the pre-existing sealed shape is kept — a REFUSAL, counted at
    * `OmissionCheck.enumShapeRefusals` rather than silently chosen. */
  private def enumDef(cd: Tree.ClassDef, i: Int): String =
    if balticporter.tir.EnumShape.isScalaEnum(program, cd) then scalaEnumDef(cd, i) else sealedEnumDef(cd, i)

  /** The parts BOTH enum shapes are made of, derived ONCE.
    *
    * The two arms differ in the header they write and in the members java.lang.Enum does or does not
    * supply; they do not differ about which constructor is the primary, which parameters it
    * promotes, which field a parameter supersedes or which of its statements survive. Read twice
    * those would be two derivations free to drift — the failure `CtorFunnel.enumPrimaryCtor` exists
    * to prevent one level down (§4.56). */
  private final case class EnumParts(ctorParams: List[Tree.ValDef], paramNames: Set[String],
                                     instance: List[Statement], ctorStats: List[Statement],
                                     statics: List[Statement], eprimary: String)

  private def enumParts(cd: Tree.ClassDef): EnumParts =
    val (statics, instance0) = cd.body.partition(isStatic)
    // A Java enum constructor's PARAMS become the emitted type's primary constructor params (as
    // `var` fields), so `Nearest extends TextureFilter(GL_NEAREST)` has somewhere to pass its arg.
    // Drop the constructor itself and any field that a param supersedes (same name).
    // JAVA's parameters, never `paramss.flatten` (`CtorFunnel.valueParams`, and the same rule the
    // funnel applies one level up). A context clause a phase put on this constructor is not a java
    // parameter and cannot become a `var` field: the parameter is ANONYMOUS, so it would render as
    // `var : sge.Sge`, and an enum's primary is reached by every `case object` — each of which
    // would have to pass an argument for a clause the emitter has no way to supply. So it is
    // dropped from the parameter list and COUNTED as a lost clause instead (`ENGINE-LIMITS.md`
    // CT5); an enum whose body needs an ambient context is a port-level decision, not a rendering.
    // THE ROOT, never `ctors.head`. For the single-constructor enum every corpus library had, the
    // two are the same and nothing could tell them apart; for an overloaded one the head is
    // whichever java wrote first, and taking ITS parameters gave a delegating `Flags()` beside
    // `Flags(int)` an EMPTY primary — `case object X extends Flags(3)` is `too many arguments`, and
    // every constant that named the nilary overload silently got the field's DEFAULT where java ran
    // `this(1)`. `CtorFunnel.enumPrimaryCtor` is the shared derivation (§4.56) and
    // `OmissionCheck.overloadedEnumCtors` counts what it refuses.
    val primaryCtor = CtorFunnel.enumPrimaryCtor(program, cd)
    val ctorParams = primaryCtor.map(CtorFunnel.valueParams(program, _)).getOrElse(Nil)
    checkClause(cd, rendered = false, form = "enum")
    val paramNames = ctorParams.map(v => sym(v.symbol).name).toSet
    // WHICH field a parameter supersedes is `CtorFunnel`'s to answer, matched on the name AND the
    // TYPE: java's two variable scopes let a constructor parameter name a field it is not and then
    // COMPUTE that field from it, and a name test drops the field and emits the parameter's type in
    // its place. `funnelParamRenames` reads the same function, so the field that survives here is
    // exactly the one the parameter was renamed out of the way of (§4.56).
    val superseded = CtorFunnel.enumSupersededFields(program, cd)
    val instance   = instance0.filterNot {
      case d: Tree.DefDef => sym(d.symbol).name == "<init>"
      case v: Tree.ValDef => superseded(v.symbol)
      case _              => false
    }
    // …and the self-assignment drop follows the SAME set, not `paramNames`. `this.f = f` is
    // redundant only because the promotion performed it; where the field survives, the assignment is
    // what fills it and dropping it would leave the field at its default, silently.
    val supersededNames = instance0.collect {
      case v: Tree.ValDef if superseded(v.symbol) => sym(v.symbol).name
    }.toSet
    // the ctor BODY must run too, not just the params, or a field it assigns stays at its default.
    // CtorFunnel is not consulted: an enum's shape is fixed (the sealed class's primary IS the java
    // ctor), so the lowering runs off the ROOT — the one non-delegating overload.
    val ctorStats =
      primaryCtor.toList.flatMap(CtorFunnel.stmtsOf).filterNot {
        // java's implicit super(), reaching java.lang.Enum with no expression here.
        case Tree.Apply(Tree.Select(_, m, _, _), _, _, _, _) => sym(m).name == "<init>"
        // this.glEnum = glEnum is a self-assignment the promotion already performs; drop only that
        // exact shape (an assignment that computes anything stays).
        case a: Tree.Assign                                  => selfAssignedParam(a).exists(supersededNames)
        case _                                               => false
      }
    // A superseded field's modifiers come with its value: the parameter is emitted AS that field,
    // so its access level, `private[Enum]` qualification (java's private reaches the whole
    // top-level enclosure; scala's bare `private` on a class param does not) and `val`-vs-`var`
    // (decided by whether the body still writes it) all carry over from the field, never from the
    // parameter (which has none of them — JLS 8.8.1). A parameter superseding nothing keeps `var`.
    val standsFor: Map[SymId, SymId] =
      val dropped = instance0.collect { case v: Tree.ValDef if superseded(v.symbol) => v.symbol }.toSet
      CtorFunnel.enumSupersededBy(program, cd).filter((_, f) => dropped(f))
    // every write the EMITTED body still performs, to the field or to the parameter that replaced
    // it — the dropped self-assignment is already gone from `ctorStats`, so it cannot vote here.
    val writtenAfterPromotion: Set[SymId] =
      if standsFor.isEmpty then Set.empty
      else
        val watched = standsFor.keySet ++ standsFor.values
        val acc     = collection.mutable.Set.empty[SymId]
        val scan = new Phase:
          def name: String = "emit/written-enum-params"
          override def transformTerm(t: Term)(using Program): Term =
            t match
              case Tree.Assign(Tree.Ident(sy, _, _), _, _, _, _) if watched(sy)                 => acc += sy
              case Tree.Assign(Tree.Select(_: Tree.This, sy, _, _), _, _, _, _) if watched(sy)  => acc += sy
              case _                                                                          => ()
            t
        StandardTraversal.mapClassDef(scan, cd.copy(body = instance ++ ctorStats))(using source)
        acc.toSet
    // the qualifier a bare `private` takes here — the enclosing top-level type where there is one
    // (`privateQualifier`'s own answer for a NESTED enum), and otherwise the enum itself.
    val es           = sym(cd.symbol)
    val enumPrivateIn = privateQualifier(es.owner).orElse(Some(esc(es.name)))
    def enumParam(v: Tree.ValDef): String =
      val nm = esc(sym(v.symbol).name)
      val ty = tpe(v.tpt.tpe)
      standsFor.get(v.symbol) match
        case Some(f) =>
          val fs = sym(f)
          val kw = if fs.flags.isMutable || writtenAfterPromotion(f) || writtenAfterPromotion(v.symbol)
                   then "var" else "val"
          s"${vis(fs, enumPrivateIn)}$kw $nm: $ty"
        case None => s"var $nm: $ty"
    val eprimary = if ctorParams.isEmpty then "" else s"(${ctorParams.map(enumParam).mkString(", ")})"
    EnumParts(ctorParams, paramNames, instance, ctorStats, statics, eprimary)

  /** THE PRE-EXISTING SHAPE — a `sealed abstract class` plus one `case object` per constant.
    *
    * It is what a java enum the scala 3 `enum` cannot express is emitted as, and the ONE thing it
    * does not do is extend `java.lang.Enum[X]` (scalac: "only enums defined with the enum syntax
    * can"), which is why `name()`, `ordinal()`, `values()` and `valueOf` are all supplied by hand
    * below. `EnumShape.refusal` decides which enums arrive here and `OmissionCheck` counts them. */
  private def sealedEnumDef(cd: Tree.ClassDef, i: Int): String =
    val s       = sym(cd.symbol)
    val name    = esc(s.name)
    val parents = cd.parents.map(parent).filter(p => p.nonEmpty && !p.startsWith("java.lang.Enum"))
    val ext     = if parents.isEmpty then "" else " extends " + parents.mkString(" with ")
    val parts   = enumParts(cd)
    import parts.{ctorParams, paramNames, instance, ctorStats, statics, eprimary}
    // Java's final Enum.name() — a case object's toString IS its declared name, so name() returns
    // it. A promoted ctor parameter counts as a declared `name` too (CLAUDE.md §4.55).
    val hasName = paramNames("name") ||
      instance.exists { case d: Definition => sym(d.symbol).name == "name"; case _ => false }
    val nameM   = if hasName then Nil else List(s"${ind(i + 1)}def name(): java.lang.String = this.toString()")
    // Java's final Enum.ordinal() — emitted as an abstract member with one override per constant
    // (O(1), matching java) rather than derived from values().indexOf(this).
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
    // §8.7 governs the enum TYPE; its constructor is left public — java's implicit `private`
    // (JLS 8.9.2) has no declaration left to carry it, since the params ARE the primary.
    val cls     = s"${leading(cd.leading, i)}$cnote${ind(i)}${vis(s, privateQualifier(s.owner))}sealed abstract class $name$eprimary$ext" + (if cbody.isEmpty then "" else s" {\n$cbody\n${ind(i)}}")
    val cases = cd.enumCases.zipWithIndex.map { (ec, idx) =>
      val cn   = esc(sym(ec.symbol).name)
      // the ROOT's arguments: a constant naming a delegating overload passes what that overload's
      // this(...) passes, since the emitted primary IS the root.
      val cargs = CtorFunnel.enumConstantArgs(program, cd, ec.ctorArgs).getOrElse(ec.ctorArgs)
      val args = if cargs.isEmpty then "" else s"(${cargs.map(term(_, i + 1)).mkString(", ")})"
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
    // sealed abstract class + companion of case objects; CtorFunnel is not consulted for an enum,
    // so the primary IS the java constructor and its slots are ctorParams.
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

  /** `enum X(…) extends java.lang.Enum[X] with …` — the shape used where a caller depends on the
    * `java.lang.Enum` supertype itself (a bound, `EnumSet`/`EnumMap`, `Comparable`, `isEnum`).
    * `name()`/`ordinal()` are FINAL there and `values`/`valueOf` come from the `enum` desugaring, so
    * `EnumShape.Reserved` refuses an enum whose own declaration needs one of those names
    * (CLAUDE.md §4.55, ENGINE-LIMITS T11). `values` renders parenless here; `applyStr0` matches. */
  private def scalaEnumDef(cd: Tree.ClassDef, i: Int): String =
    val s     = sym(cd.symbol)
    val name  = esc(s.name)
    // java.lang.Enum[X] first, then every interface parent (JLS 8.9 — enums may implement only),
    // deduplicating one the frontend already carried as java.lang.Enum.
    val mixins = cd.parents.map(parent).filter(p => p.nonEmpty && !p.startsWith("java.lang.Enum"))
    val ext    = s" extends java.lang.Enum[$name]" + mixins.map(p => s" with $p").mkString
    val parts  = enumParts(cd)
    import parts.{ctorParams, instance, ctorStats, statics, eprimary}
    val cnote  = if cd.symbol == currentTopLevelSym then "" else declNotes(cd.symbol, i)
    val bnote  = bodyNotes(cd.symbol, i + 1)
    // cases go last: the desugaring lifts them out of the template, so their position among the
    // real statements is free — keep it after members to match the sealed arm's body order.
    val cases = cd.enumCases.map { ec =>
      val cn = esc(sym(ec.symbol).name)
      val cargs = CtorFunnel.enumConstantArgs(program, cd, ec.ctorArgs).getOrElse(ec.ctorArgs)
      val args  = if cargs.isEmpty then "" else s"(${cargs.map(term(_, i + 1)).mkString(", ")})"
      // `case A extends X(…)`, one spelling per constant, so each constant keeps its own comment.
      s"${leading(ec.leading, i + 1)}${ind(i + 1)}case $cn extends $name$args"
    }
    val members = List(bnote).filter(_.nonEmpty) ++
      orderBody(instance, cd.symbol).map(memberStat(_, i + 1)).filter(_.nonEmpty) ++
      ctorStats.map(memberStat(_, i + 1)).filter(_.nonEmpty) ++ cases
    val cbody = members.mkString("\n")
    val cls   = s"${leading(cd.leading, i)}$cnote${ind(i)}${vis(s, privateQualifier(s.owner))}enum $name$eprimary$ext" +
      (if cbody.isEmpty then "" else s" {\n$cbody\n${ind(i)}}")
    val objBody = statics.map(memberStat(_, i + 1)).filter(_.nonEmpty)
    // form = "enum", not "enum-class": the two shapes publish different surfaces, so base-surface
    // can see a dependent disagreement. companion = true unconditionally — the enum desugaring
    // always makes one, regardless of whether objBody (java's statics) is empty.
    recordedTypeShapes(s.fullName) = Surface.TypeShape(
      form        = "enum",
      companion   = true,
      statics     = statics.collect { case d: Definition => esc(sym(d.symbol).name) }.distinct,
      primary     = Some(Descriptor(ctorParams.map(v => descriptorParam(v.tpt.tpe)))),
      primaryKind = "not-funnelled",
      primaryVis  = "public",
      parents     = parentSymsOf(cd).map(p => sym(p).fullName),
      flags       = List("enum"),
    )
    if objBody.isEmpty then cls
    else s"$cls\n${ind(i)}object $name {\n${objBody.mkString("\n")}\n${ind(i)}}"

  // a Java `static` nested class has no instance home in Scala → it moves to the companion
  // `object` alongside static vals/defs. A non-static inner class stays in the class body.
  /** Replace the constructor `CtorFunnel` promoted to Scala's PRIMARY by its own body statements,
    * which run at construction. `super(args)` is already in the `extends` clause and its params in
    * the class's parameter list; every other constructor stays a secondary `def this(...)`. */
  private def lowerCtors(body: List[Statement], plan: CtorFunnel.Plan): List[Statement] =
    plan.primary match
      case None    => body
      case Some(c) => body.flatMap { case d: Tree.DefDef if d.symbol == c.symbol => plan.primaryBody; case s => List(s) }

  /** the local `def` a PROMOTED constructor body carrying a `return` is wrapped in. Named per
    * class, not per program, so it cannot renumber under an unrelated edit (ENGINE-LIMITS M10). */
  private val CtorBodyName = "ctorBody$"

  /** JS-C51 — java `return` in a constructor leaves the constructor (JLS 14.17); promoted into the
    * class body it becomes `E091 return outside method definition`. Wrapped in a local `def` (a
    * `return`'s only valid target, and unlike `boundary.Break` not swallowed by a `catch
    * (Exception)` the promoted body may hold — §4.4), itself wrapped in a block so the `def` is not
    * a class member and does not appear in `members.tsv`. Only `plan.primaryBody` moves inside, at
    * the constructor's own position; fields and init blocks are already hoisted above it by
    * `orderBody` (§4.55). [[returnsIn]] stops at a nested `Tree.Lambda`/`DefDef`/`AnonClass`, so a
    * `return` belonging to an inner listener is not a reason to wrap. A value-carrying `return` is
    * refused and left as is (javac itself rejects it in a constructor). */
  private def classBodyStats(ordered: List[Statement], plan: CtorFunnel.Plan, i: Int): List[String] =
    val promoted = plan.primaryBody
    def inBody(s: Statement) = promoted.exists(_ eq s)
    if promoted.isEmpty || !returnsIn(promoted) || collectReturns(promoted).exists(_.expr.isDefined)
    then ordered.map(memberStat(_, i))
    else
      val first = ordered.indexWhere(inBody)
      ordered.zipWithIndex.flatMap { (s, k) =>
        if !inBody(s) then List(memberStat(s, i))
        else if k != first then Nil // rendered inside the wrapper, at the FIRST promoted statement
        else
          // rendered at i+2 here too, or srcMapOf's lookup-by-produced-text cannot locate it.
          val stats = ordered.filter(inBody).map(memberStat(_, i + 2))
          List(s"${ind(i)}{\n${ind(i + 1)}def $CtorBodyName(): scala.Unit = {\n" +
               s"${stats.mkString("\n")}\n${ind(i + 1)}}\n${ind(i + 1)}$CtorBodyName()\n${ind(i)}}")
      }

  /** a Java `static { … }` / instance `{ … }` initializer block, carried as a synthetic member. */
  private def isInitBlock(d: Tree.DefDef): Boolean =
    val n = sym(d.symbol).name
    n == "<clinit>" || n == "<initblock>"

  /** does this member list carry java CLASS INITIALISER content — JLS 12.4.2 step 9, never the
    * instance initialiser? Delegates to `ClassInitTriggerCheck.stepNine`, the one definition the
    * repair and its watchdog share — keyed on the `static { }` block alone this missed a
    * registration written as a static field initialiser (same `<clinit>` sequence). The constant
    * variable stays outside it: javac inlines it (JS-C08). */
  private def hasClinit(members: List[Statement]): Boolean =
    balticporter.tir.ClassInitTriggerCheck.stepNine(members)(using program)

  // K22 — a java class initialiser (JLS 12.4.2 step 9: static field initialisers + `static { }`
  // blocks, one sequence) runs at class initialisation; a scala companion initialises only when
  // something touches the OBJECT, which `new T(…)` does not. So it is emitted into the companion
  // and reproduced only at java's own trigger list (JLS 12.4.1): INSTANTIATION (forced ahead of
  // every field initialiser, at the class body's head), STATIC ACCESS (already an access to the
  // object, needs nothing), and SUBCLASS INITIALISATION (item 7 — force the nearest bearing
  // ancestor's companion). Never "call it from every use": java's constant-variable inlining means
  // a plain read triggers nothing, and forcing there would re-enter the Vector3/Matrix4 init cycle
  // (§4.4). REFLECTION cannot be reproduced (a reflective load of `T` does not touch `T$`) and is
  // stated rather than counted (ENGINE-LIMITS K22). `val _ = <fully-qualified path>`: bare would be
  // `E176 unused value` under the consumer's own `-Wall` (§4.45); qualified because an unqualified
  // simple name inside the body can resolve to a same-named MEMBER instead (§4.56).

  /** The note and statement that force `target`'s companion, recorded as one [[Decision]] about
    * `cd` so the note is DERIVED (§4.575) and `NoteCoverageCheck` sees the pair. `target` is `cd`
    * itself for the instantiation trigger, an ANCESTOR for the subclass one (JLS 12.4.1 item 7).
    * @param trigger which of JLS 12.4.1's actions this statement stands for. */
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

  /** An argument lifted into the `extends` clause whose declared type kept a wildcard fill
    * (`map$p: IntMap[?]`) but the parent's constructor formal needs a real type there. Take the
    * parent's formal under its actual instantiation when available (matches a named type parameter
    * exactly); fall back to the isolated wildcard elimination only when the parent cannot be seen
    * into. */
  private def superArg(parent: TypeRepr, a: Term, n: Int, i: Int): String =
    if !hasWildcardArg(a.tpe) then term(a, i)
    else
      val target = superFormal(parent, n).filterNot(hasWildcardArg).map(tpe)
        .getOrElse(deWildcarded(a.tpe, named = false))
      s"${term(a, i)}.asInstanceOf[$target]"

  private def parent(p: Term | TypeTree): String = p match
    case tt: TypeTree  => parentTpe(tt.tpe)
    case t: Term  => parentTpe(t.tpe)

  /** a parent type in an `extends` clause: a wildcard type argument is ILLEGAL here, so each `?` is
    * replaced with its upper bound (or `AnyRef`). Only the HEAD is a `namedInner` position — a type
    * ARGUMENT's inner-class simple name is not in scope in an `extends` clause. */
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

  /** ONE substitution function for the whole engine — [[ParentSubst.subst]], §4.56. */
  private def substTp(t: TypeRepr, m: Map[SymId, TypeRepr]): TypeRepr = ParentSubst.subst(t, m)

  /** Render a type with every WILDCARD argument eliminated — illegal in an `extends` clause and as
    * a cast target. A wildcard becomes its own written bound, else the type parameter's declared
    * upper bound (never a blanket `AnyRef`, which can fail a bound like `T <: ParticleBatch[D]`),
    * resolved LEFT TO RIGHT so a later bound can name an earlier parameter. `named` is a Boolean
    * rather than a by-name combinator because the latter's mutable flag does not survive being
    * passed through a strict function parameter. */
  /** The de-wildcarding CHOICE, as types rather than text — the same decision [[deWildcarded]]
    * renders, exposed so a parent's elimination and its overrides derive from ONE answer. `None`
    * where the slot stays a wildcard (F-bounded, or nothing to fill from). */
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
            // An F-BOUNDED parameter (N extends Node<N,V,A>) cannot be eliminated: no finite type
            // satisfies the bound except a real subclass, and java does not check it at an erased
            // use while scala does. Only the wildcard asserts "some type satisfies the bound".
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

  /** THE STATEMENT RENDERING DISPATCH — §2.3(c)'s emitter surface, one of two. The obligation
    * wrapper is here, at the dispatch, so no arm can escape it (ENGINE-LIMITS F8). */
  private def stat(s: Statement, i: Int): String =
    Rendering.of(TirKinds.of(s), s.origin, s)(statArm(s, i))

  /** JS-C47 / C48 / C49 / C50 — java's four access levels, consulted once here (where the three
    * rendering arms converge) rather than once per declaration kind (ENGINE-LIMITS F8). JS-C47/C50
    * fire together: java's package-private default vs scala's public one. */
  private def declVisibility(s: Symbol, at: Origin)(using Obligations): Unit =
    // read off visPlan (the DECIDED level), never the raw java flags — Visibility.decide may widen.
    val v = visPlan.getOrElse(s.id, Visibility.Vis.Public)
    val packagePrivate = v match
      case Visibility.Vis.PackagePrivate | Visibility.Vis.PrivateAt(_) => true
      case _                                                           => false
    Obligations.consult(JS.C(47), at)(Option.when(packagePrivate)(()))
    Obligations.consult(JS.C(48), at)(Option.when(v match
      case Visibility.Vis.ProtectedPkg | Visibility.Vis.ProtectedAt(_) => true
      case _                                                           => false)(()))
    // JS-C49: java's private on a NESTED type's member reaches the whole enclosing top-level class.
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

  /** JS-C44 — java's `sealed`/`permits` (naming subclasses anywhere) against scala's file-scoped
    * `sealed` (containing them). `sealed` is emitted only where the program-declared subtype set
    * ACCOUNTS FOR every permitted type ([[balticporter.tir.Symbol.permits]]) — never from the
    * parsed survivors alone, since an excluded or refused unit leaves no edge and a wrongful seal
    * would be invisible everywhere else. Otherwise the type ships OPEN, recorded as a residue
    * (ENGINE-LIMITS M6). Returns the keyword and the note pair. */
  private def sealOf(cd: Tree.ClassDef, s: Symbol, i: Int): (String, String) =
    if !s.flags.isSealed then ("", "")
    else
      val mine     = topLevelOf(cd.symbol)
      val subs     = subtypesBySym.getOrElse(cd.symbol, Nil)
      val elsewhere = subs.filterNot(x => topLevelOf(x) == mine)
      // types java named that this program does not declare as a subtype at all. Compared as
      // interned ids, never names — the permits list and the emitted FQN are two namespaces (§4.56).
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
            // reported apart: elsewhere is emitted (discoverable), unaccounted is not.
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

  /** JS-C43 — the members javac DERIVES from a record header (JLS 8.10.3), written out on a plain
    * `final class` rather than a `case class` (a case class loses `toString`/`hashCode` format,
    * `equals` on double/float NaN and -0.0, an explicit accessor beside a component, record-pattern
    * deconstruction via accessors instead of constructor params, and adds a surface java never had).
    * `equals`/`hashCode`/`toString` read the FIELDS; the extractor reads the ACCESSORS (java's own
    * split — JLS 8.10.3). Skipped where the record already declares the member, by SIGNATURE (arity
    * for `hashCode`/`toString`, the `Object`-typed one-arg form for `equals`). Residues that cannot
    * be closed: `Class.isRecord`/`getRecordComponents` (scalac emits no JVM record), and the
    * extractor's tuple-`unapply` calling every accessor eagerly instead of stopping at the first
    * failing component and propagating rather than wrapping in `MatchException`. Reference
    * components are cast to `java.lang.Object` for the `Objects.equals`/`hashCode`/`String.valueOf`
    * formals (a type variable may not conform) and to avoid the `char[]` overload diverging from
    * java's `Object`-typed concat.
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

      // THE EXTRACTOR - scala's half of JLS 14.30.1, deconstructing through the ACCESSORS as
      // java's record pattern does. Declined where the record already declares a colliding
      // unapply - collision meaning a STATIC single-param unapply whose param IS the record;
      // an instance unapply or a differently-typed static one cannot clash.
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

      // the members the RECORD declared for itself — reported alongside the synthesised ones
      // so a reader can see java's own member is right there in the file.
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
          // scalac emits no JVM record whatever the extends clause says, so isRecord/
          // getRecordComponents cannot be reproduced.
          "reflective" -> "isRecord=false;getRecordComponents=null",
          // the extractor's shape decides two residues no assertion here can close: a scala
          // unapply returning a tuple evaluates every accessor eagerly (java stops at the first
          // failing component) and propagates what an accessor throws (java wraps it in
          // MatchException).
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

  /** THE AREA-C ROWS A TYPE DECLARATION OWES — consulted at the dispatch, above every arm, since
    * `classDef` forks into `enumDef`/`classDef1` and a row consulted inside only one would be a
    * HOLE at the other shape. Predicates are read off the tree and symbol table rather than by
    * re-running the emitter, so a `fired` count means "this class has the shape", not "the repair
    * emitted text". */
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
    // JS-C51 — a `return` in the PROMOTED constructor body, which the class body has no method to
    // return from. Consulted at every class with a promoted body and fires where one really holds
    // a `return` of its own — `returnsIn` stops at a lambda, a nested `def` and an anonymous class,
    // so a listener the constructor installs is not this row's construct. See `classBodyStats`.
    Obligations.consult(JS.C(51), at)(Option.when(returnsIn(plan.primaryBody))(()))

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
    // JS-G05/JS-G11 are one fold at two outcomes: a wildcard is illegal in an extends clause, so
    // deWildcardedArgs eliminates it (to its own bound, else the parameter's, else AnyRef) and
    // refuses for an F-bounded parameter, where only the wildcard's weaker claim satisfies scalac.
    // Consulted HERE, not at the type dispatch: the elimination happens above TirEmitter.tpe, so
    // the TypeBounds arm never sees this slot (JS-G39's rule at the other pipeline end).
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
    // DEFINITION never arrives here wrapped — it carries its own `leading` field.
    case c: Tree.Commented => leading(c.leading, i) + stat(c.stmt, i)
    case c: Tree.ClassDef =>
      classConsults(c)
      classDef(c, i)
    // a Java initializer block is carried as a synthetic member; emit its BODY inline (locally { })
    // rather than a def, since orderBody has already placed it after the fields it fills. `locally`
    // is required: a bare `{ }` after a field initialised with `new T(…)` parses as that
    // constructor's anonymous-class body. JS-S25 is consulted HERE and not inside defDef, because a
    // Tree.DefDef reaches the page through two arms and the rule must sit where they converge.
    case d: Tree.DefDef   =>
      Obligations.consult(JS.S(25), d.origin)(Option.when(needsUnreachableTail(d))(()))
      // JS-C16 — an instance initialiser block; JS-C25 — override, which java does not write.
      // Consulted here (not in defDef) since an init block never carries isOverride.
      Obligations.consult(JS.C(16), d.origin)(Option.when(isInitBlock(d))(()))
      Obligations.consult(JS.C(25), d.origin)(Option.when(sym(d.symbol).flags.isOverride)(()))
      // JS-G35's other declaration kind — a method's own formal parameters carry the same F-bound.
      Obligations.consult(JS.G(35), d.origin)(Option.when(fBounded(d.tparams))(()))
      // JS-G41: an unreifiable vararg component carries java's HEAP POLLUTION and is reproduced
      // as-is (no scala image for the warning/@SafeVarargs); HeapPollutionCheck counts it.
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

  /** Disambiguate a member that arrives CONCRETE from both the superclass and a mixin. Java has
    * single inheritance of implementation, so this is never ambiguous there; scala linearises and
    * refuses ("inherits conflicting members"). So declare it, forwarding to the SUPERCLASS (the
    * head of the parents list) — the member java would have run.
    * A `final` superclass member takes NO forwarder: scala already agrees there (nothing to
    * disambiguate), and a forwarder minted for one overrides a `final` member, which scala forbids
    * outright (ENGINE-LIMITS K28). Read off the superclass member's own `final` flag. */
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
        sup.toList
          .filter((k, d) => mixins(k) && !ownKeys(k) && !sym(d.symbol).flags.isFinal)
          .sortBy((k, _) => k._1).map { (_, d) =>
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
    // trivia first, porter note last, member next (§4.575) — the note must not displace the licence.
    // a ctor whose REPLAY contains .orNull needs @nowarn("msg=deprecated") (detected at construction
    // time into replayOrNullCtors, recorded as a SuppressedWarning decision).
    val replayNowarn =
      if isCtor && replayOrNullCtors.contains(d.symbol)
      then s"${ind(i)}@scala.annotation.nowarn(\"msg=deprecated\")\n"
      else ""
    s"${leading(d.leading, i)}${declNotes(d.symbol, i)}${annots(s, i)}$replayNowarn${ind(i)}${mods(s, privateQualifier(s.owner))}def $name$tps$pss$ret$rhs"

  /** does a list of replay statements contain a `.orNull` call? Called at construction time to
    * compute `replayOrNullCtors` and record a `SuppressedWarning` decision for each (§4.575). */
  private def replayHasOrNull(stmts: List[Statement]): Boolean =
    given Program = program
    stmts.exists {
      case t: Term =>
        StandardTraversal.scanTerm(t, false) {
          case (true, _)                                               => true
          case (_, Tree.Select(_, m, _, _)) if sym(m).name == "orNull" => true
          case (acc, _)                                                => acc
        }
      case _ => false
    }

  /** Loop-jump scope, as scala `boundary` nesting. `break` and `continue` need boundaries in
    * DIFFERENT places — one around the whole loop, one around the BODY — and when a loop needs
    * both, the body boundary is innermost, so the outer one must be NAMED or an un-annotated
    * `break(())` inside it would continue instead.
    * `breakTarget`: `None` = no enclosing loop boundary (a `break` here belongs to a switch);
    * `Some("")` = an unnamed one is innermost; `Some(name)` = named because another boundary sits
    * inside it. Re-pointed by `match` at the CASE's own boundary (`contTarget` is not, since a
    * `continue` inside a switch still continues the loop). */
  private var breakTarget: Option[String] = scala.None
  private var contTarget: Option[String]  = scala.None
  private var labelSeq = 0
  /** names the `def` that carries a lambda body containing `return`. SCOPED TO ONE DECLARATION by
    * [[inDeclaration]], never to the program, or the name would renumber under an unrelated edit
    * (ENGINE-LIMITS M10). */
  private var lambdaSeq = 0
  /** counter for lvalue-binding temporaries (`$lv1`, `$lv2`, …), scoped identically to
    * `lambdaSeq` — a compound-assignment temporary in one member must not consume another's. */
  private var lvSeq = 0

  /** run `f` with the synthetic-name counters SAVED, reset, and restored — a nested declaration
    * (an anonymous class's method inside a lambda) is its own naming scope and must not consume
    * the enclosing member's numbers. */
  private def inDeclaration[A](f: => A): A =
    val saved = lambdaSeq
    val savedLv = lvSeq
    lambdaSeq = 0
    lvSeq = 0
    try f finally { lambdaSeq = saved; lvSeq = savedLv }
  private def inLoop[A](brk: Option[String], cont: Option[String])(f: => A): A =
    val (sb, sc) = (breakTarget, contTarget)
    breakTarget = brk; contTarget = cont
    try f finally { breakTarget = sb; contTarget = sc }
  private def inSwitch[A](brk: Option[String])(f: => A): A =
    val sb = breakTarget
    breakTarget = brk
    try f finally breakTarget = sb

  /** the value-carrying `Label` a non-tail `yield` must name — a switch EXPRESSION's arm boundary.
    * Kept apart from [[breakTarget]]: a `break` carries `Unit` and a `yield` the switch's own
    * type, so one boundary cannot serve both (and JLS 15.28 keeps them from ever coexisting).
    * ALWAYS named, so nothing nearer can steal `break(v)(using n)`. */
  private var yieldTarget: Option[String] = scala.None
  private def inYield[A](y: Option[String])(f: => A): A =
    val sy = yieldTarget
    yieldTarget = y
    try f finally yieldTarget = sy

  /** java LABEL -> the scala boundary name a `break`/`continue` naming it must target. A labelled
    * jump can sit at any depth, so unlike the unlabelled ones these are looked up, not scoped. */
  private val labelBreak = collection.mutable.Map[String, String]()
  private val labelCont  = collection.mutable.Map[String, String]()

  /** Render a loop with whatever boundaries its jumps need — up to two, one around the LOOP for
    * `break`, one around the BODY for `continue`, the outer one NAMED when both are present. */
  /** A java enhanced-for BINDING is a declaration with its own type; scala's `for (x <- xs)` binds
    * at the ITERABLE's element type, and java lets them differ (`for (Object e : collection)` over
    * a raw/wildcarded `Collection`) — resolving `e` against `Object` where scala resolves against
    * an unusable wildcard capture (`Found: ?1.CAP`). Returns the declared type to re-bind at, or
    * `None` when scala's binding is already exact. Conservative in ONE direction: an unreadable
    * element type is treated as agreeing rather than inventing a cast on no evidence. */
  private def widenedBinding(b: Tree.ValDef, it: Term): Option[String] =
    elementTpe(it.tpe).filter(_ != b.tpt.tpe).map(_ => tpe(b.tpt.tpe))

  /** is the enhanced-for BINDING written to inside the loop body? Java's binding is an ordinary
    * local and may be assigned; scala's generator binds a `val`. Scanned with `StandardTraversal`
    * (§3), counting `IncDec` beside `Assign`; over-approximating costs only an unneeded `var`. */
  private def reassignsBinding(body: Tree, binding: SymId): Boolean =
    given Program = program
    body match
      case t: Term => StandardTraversal.scanTerm(t, false) { (found, x) =>
        x match
          case Tree.Assign(Tree.Ident(s, _, _), _, _, _, _) if s == binding    => true
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

  /** ENGINE-LIMITS K9: is the iterable's POST-PIPELINE type a JDK `Iterable` the pipeline left in
    * the java namespace? Such a type has no scala `foreach`, so `for (x <- xs)` does not compile.
    * Decided from the NODE (§4.56): external (not program-owned) AND in `java.*`/`javax.*`. A
    * retyped type is no longer in that namespace by the time the emitter runs, and arrays are
    * excluded by construction (`headSymOf` returns `None` for them). */
  private def isKeptJdkIterable(iterableTpe: TypeRepr): Boolean =
    headSymOf(iterableTpe).flatMap(program.symbolOf).exists { s =>
      !program.owns(s.id) && {
        val fqn = s.fullName
        fqn.startsWith("java.") || fqn.startsWith("javax.")
      }
    }

  private def loopWithJumps(body: Tree, label: Option[String], render: (=> String) => String,
                            bodyStr: => String)(using Obligations): String =
    val lblB = label.filter(l => jumpsTo(body, l, brk = true))
    val lblC = label.filter(l => jumpsTo(body, l, brk = false))
    val hasB = breaksOut(body) || lblB.isDefined
    val hasC = continuesIn(body) || lblC.isDefined
    // JS-S01 — java's unlabelled jump binds LEXICALLY to this loop; scala's boundary.break binds to
    // the innermost Label. Fires wherever a jump really belongs to this loop.
    Obligations.consult(JS.S(1), body.origin)(Option.when(hasB || hasC)(()))
    // JS-S03 — a boundary this emitter INTERPOSES steals the enclosing loop's un-annotated jumps.
    // `&&` skips the extra traversal on loops with no jump to steal.
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
    * `boundary.break(())` with no `using` resolves the innermost `Label`, so an interposed boundary
    * silently retargets an un-annotated jump under it. Two constructs do it: a [[Tree.Labeled]]
    * actually broken to, and a switch case with a mid-case `break`. Deliberately an
    * OVER-approximation (naming an unneeded boundary costs one identifier; missing one is a silent
    * control-flow change — §4.4). Stops at a nested loop, lambda, `def` or anonymous class. */
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

  /** the result type to give the `def` that carries a lambda body containing `return`. TWO
    * SOURCES, tried in order (ENGINE-LIMITS I9): (1) the node's own `resultTpt`, a FACT the
    * program carries when the lambda came from a converted anonymous class's SAM method; (2) the
    * body — every `return` VALUELESS is a java `void` lambda. `None` means DO NOT REWRITE, never
    * "use `Any`": a guessed result type compiles and means something else, so the refusal is a
    * loud error naming the line (M6), counted by `OmissionCheck.unnameableLambdaReturn`. */
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
    // INLINED BODY — the parent constructor chain's post-delegation statements, when reached
    // through resolvedThroughParent: substituted and retyped into this class's scope, positioned
    // like `replay` (after the delegation, before this secondary's own body); never overlaps it.
    val inlined = currentClass.flatMap(plans.inlinedBodyFor(_, cdef)).getOrElse(Nil)
    // the head is read THROUGH its comments, re-emitted above the delegation that replaces it.
    val headTrivia = stats.headOption.collect { case t: Term => Tree.triviaOn(t) }.getOrElse(Nil)
    val plan  = currentClass.map(plans(_)).getOrElse(CtorFunnel.Plan.none)
    // A ROOT of a SYNTHESISED primary delegates with the whole slot list, so the leading `this.f = e`
    // statements those field values came from are dropped (Plan.consumed/delegations are ONE
    // derivation, so the drop and the written argument cannot disagree about which assignment went
    // where).
    val headIsDelegation = CtorFunnel.headStmt(cdef) match
      case Some(Tree.Apply(Tree.Select(_, m, _, _), _, _, _, _)) => sym(m).name == "<init>"
      case _                                                     => false
    val after = if headIsDelegation then stats.tail else stats
    val eaten = plan.delegations.get(cdef.symbol).map(_ => plan.consumed.getOrElse(cdef.symbol, 0)).getOrElse(0)
    val (deleg, rest) = plan.delegations.get(cdef.symbol) match
      case Some(args) =>
        val extra = currentClass.zip(plan.marker).map(markerArg(_, _)).toList
        val as    = args.zipWithIndex.map((a, k) => slotArg(a, plan.synthetic.lift(k).map(_._2), i + 1))
        (s"this(${(as ++ extra).mkString(", ")})", after.drop(eaten))
      case None => CtorFunnel.headStmt(cdef) match
        case Some(Tree.Apply(Tree.Select(r, m, _, _), args, _, _, _)) if sym(m).name == "<init>" =>
          val d = r match
            case _: Tree.Super => superDelegation(args, i + 1)
            case _             => s"this(${args.map(term(_, i + 1)).mkString(", ")})"
          (d, stats.tail)
        case _ => ("this()", stats)
    // §4.58 — a CONSUMED `this.f = e` does not disappear from the file, so its comment rides THIS
    // secondary's delegation, the one place a reader will find it.
    val eatenTrivia = after.take(eaten).collect { case t: Term => Tree.triviaOn(t) }.flatten
    // A10 / ENGINE-LIMITS C7 — PREFIX STRIP: where this constructor ESCAPES the promotion and its
    // own body BEGINS with the promoted body, the class body already ran those statements, so
    // re-emitting them duplicates. `Plans.residualBody` is the same function `promotionEscapes`
    // subtracts, so the emitter and the omission count cannot disagree.
    val body  = currentClass.flatMap(plans.residualBody(_, cdef)).getOrElse(rest)
    val carried = (if rest eq stats then Nil else headTrivia) ++ eatenTrivia
    val head  = leading(carried, i + 1) + ind(i + 1) + deleg
    // the block's END-OF-BODY comments: this rendering reconstructs braces from stmtsOf's list
    // rather than the body Tree.Block, so no other path carries this slot.
    val trail = CtorFunnel.trailingOf(cdef).map(triviaText(_, i + 1))
    val lines = (head :: (replay ++ inlined ++ body).map(stat(_, i + 1)).filter(_.trim.nonEmpty)) ++ trail
    s"{\n${joinStats(lines)}\n${ind(i)}}"

  /** A secondary constructor's `super(args)` — which scala cannot write — expressed as a
    * delegation to the PRIMARY, whose own `extends Parent(…)` makes the call. The DECISION is
    * `CtorFunnel.Plans.superCall`; this only renders it, so `OmissionCheck` can count a `Dropped`
    * super call independently of what this method lowers it to. */
  /** the DISAMBIGUATOR's argument, when the class's primary takes one. ASCRIBED, never a bare
    * `null`: `null` inhabits every reference type, so an unascribed `this(null)` against a class
    * also declaring `C(String)` is `E051 Ambiguous overload` (ENGINE-LIMITS C8) — `(null:
    * C.Funnel)` has exactly one applicable candidate, since nothing else declares that type. */
  private def markerArg(cd: Tree.ClassDef, name: String): String =
    s"(null: ${typeValue(cd.symbol)}.${esc(name)})"

  /** the same ascription at a slot argument: a synthesised primary's delegation is an argument
    * list JAVA NEVER WROTE, so a root that does not assign a hoisted field contributes that
    * field's own (often `null`) java initialiser, which is ambiguous the same way [[markerArg]]'s
    * is. `CtorFunnel.Plans`'s `shadowed` predicate still sees the unascribed terms, so which
    * classes get a marker is unaffected — the ascription touches only the CALL. Declines on a
    * delegation JAVA WROTE (§4.56) and at an ABSTRACT type slot (`Null` does not conform). */
  private def slotArg(a: Term, slot: Option[TypeRepr], i: Int): String = (a, slot) match
    case (Tree.Literal(Constant.NullC, _, _), Some(t)) if !abstractSlot(t) => s"(null: ${tpe(t)})"
    case _                                                                => term(a, i)

  /** does this slot's type name a TYPE PARAMETER this program declares? `Null` does not conform to
    * one, which is the same fact `CtorFunnel.javaDefault` refuses to mint a `null` for. */
  private def abstractSlot(t: TypeRepr): Boolean = t match
    case TypeRepr.TypeRef(_, s) => program.definitionOf(s).exists(_.isInstanceOf[Tree.TypeDef])
    case _                      => false

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
    // NO TYPE = a parameter a PHASE left for scalac to infer, for a LOWERED unbound method
    // reference (java writes such a qualifier RAW, so an annotated type would be an unusable
    // capture); a lambda parameter is the only ValDef a phase mints without one. An INJECTED
    // parent's parameter type wins over the TIR-derived one, since the injected file may declare a
    // DIFFERENT signature than java's (K35 CLOSED).
    val t = injectedOverrideTypes.getOrElse(v.symbol,
              overrideAlign.getOrElse(v.symbol, v.tpt.tpe))
    if t == TypeRepr.NoType then esc(sym(v.symbol).name)
    else s"${esc(sym(v.symbol).name)}: ${tpe(t)}"

  private def valDef(v: Tree.ValDef, i: Int)(using Obligations): String =
    // JS-S19 — java's definite assignment (JLS 16) rejects a read before assignment; scala
    // requires an initialiser instead. Row stays Partial: this closes the FIELD half only.
    Obligations.consult(JS.S(19), v.origin)(Option.when(v.rhs.isEmpty)(()))
    // the area-C rows a FIELD owes — valDef is the one arm a Tree.ValDef reaches.
    val vs      = sym(v.symbol)
    val ownerCd = program.definitionOf(vs.owner).collect { case c: Tree.ClassDef => c }
    // JS-C08 — a java CONSTANT VARIABLE is inlined by javac, triggering no class initialiser;
    // fires exactly where valDef0 renders `inline val`.
    Obligations.consult(JS.C(8), v.origin)(Option.when(v.rhs.isDefined && isJavaConstant(v, vs))(()))
    // JS-C36 — an interface field is implicitly public static final: a companion member, not abstract.
    Obligations.consult(JS.C(36), v.origin)(
      Option.when(vs.flags.isStatic && ownerCd.exists(c => sym(c.symbol).flags.isTrait))(()))
    // JS-C45 — a final FIELD's safe-publication guarantee, carried by val (a local has no such moment).
    Obligations.consult(JS.C(45), v.origin)(Option.when(!vs.flags.isMutable && ownerCd.isDefined)(()))
    declVisibility(vs, v.origin)
    // trivia, then porter note, then annotations, then the val (see defDef). annots renders
    // @nowarn — without it a phase-attached annotation is silently dropped (ENGINE-LIMITS T26.1).
    val note = declNotes(v.symbol, i)
    val an   = annots(sym(v.symbol), i)
    inDeclaration {
      if v.leading.nonEmpty then leading(v.leading, i) + note + an + valDef0(v.copy(leading = Nil), i)
      else note + an + valDef0(v, i)
    }

  private def valDef0(v: Tree.ValDef, i: Int): String =
    val s = sym(v.symbol)
    if s.flags.isGiven then
      // An EMPTY NAME renders an ANONYMOUS given (ENGINE-LIMITS CT3): a minted name could shadow
      // an emitted root package, since this backend emits only fully-qualified references.
      val kw = if s.flags.isPrivate then "private given" else "given"
      val nm = if s.name.isEmpty then "" else s"${esc(s.name)}: "
      return s"${ind(i)}$kw $nm${tpe(v.tpt.tpe)}${v.rhs.map(r => s" = ${term(r, i)}").getOrElse("")}"
    // A FIELD SLOT — the constructor funnel hoisted this field's value into the synthesised
    // primary's parameter list, so the field binds that parameter (java's initialiser is gone).
    // val where nothing else writes it (A1 — slot-eligibility and single-write are two conditions).
    currentClass.flatMap(cc => plans(cc).fieldSlots.find(_.field == v.symbol)) match
      case Some(fs) =>
        val kw = if fs.mutable then "var" else "val"
        val q  = privateQualifier(s.owner)
        val m  = if kw == "var" then mods(s, q).replace("final ", "") else mods(s, q)
        return s"${ind(i)}$m$kw ${esc(s.name)}: ${tpe(v.tpt.tpe)} = ${fs.name}"
      case None => ()
    v.rhs match
      case Some(r) if isJavaConstant(v, s) && !isAnonOwner(s.owner) =>
        // a java CONSTANT VARIABLE (JLS 4.12.4) is INLINED by javac, so reading it triggers no
        // class initialiser — a typed `val` would (§4.4's Vector3/Matrix4 cycle). `inline val`
        // WITHOUT the type ascription, which would defeat the constant type. An ANONYMOUS CLASS
        // has no companion, so it stays an ordinary `val` in the anonymous body there.
        s"${ind(i)}${mods(s).replace("final ", "")}inline val ${esc(s.name)} = ${constAt(r, v.tpt.tpe)}"
      case Some(r) =>
        // a non-final java local or PRIVATE field never ASSIGNED anywhere in the program (the
        // write set is BeanCollapse.writtenSymbols, §4.55) emits val instead of var. NON-PRIVATE
        // fields stay var even when unwritten HERE — a dependent port may write them (§1.5).
        val isField = program.definitionOf(s.owner).exists(_.isInstanceOf[Tree.ClassDef])
        val kw = if s.flags.isMutable && (isWritten(v) || (isField && !s.flags.isPrivate))
                 then "var" else "val"
        val q  = privateQualifier(s.owner)
        val m  = if kw == "var" then mods(s, q).replace("final ", "") else mods(s, q)
        s"${ind(i)}$m$kw ${esc(s.name)}: ${tpe(v.tpt.tpe)} = ${term(r, i)}"
      case None =>
        // an uninitialized java field: a var placeholder so constructors can assign it (a bare
        // `val x: T` is abstract); `final` is dropped (`final var` is contradictory). Substitutes
        // `scala.compiletime.uninitialized` ONLY for defaultFor's `null.asInstanceOf[T]` fallback —
        // never for a stated default (0/false/a T|Null union's own null), which the nullability
        // phase's union needs kept. ONLY FOR A FIELD: `uninitialized` may only be the RHS of a
        // mutable FIELD, and this same function also renders a method's local var (0 -> 380 errors
        // without the gate).
        val fieldOfAClass = program.definitionOf(s.owner).exists(_.isInstanceOf[Tree.ClassDef])
        val stated = defaultFor(v.tpt.tpe)
        val blank  = if fieldOfAClass && stated.contains(".asInstanceOf[") then "scala.compiletime.uninitialized" else stated
        s"${ind(i)}${mods(s, privateQualifier(s.owner)).replace("final ", "")}var ${esc(s.name)}: ${tpe(v.tpt.tpe)} = $blank"

  /** the literal rendered AT the field's declared type. `inline val` takes its constant type from
    * the literal (a type ascription is rejected), so `static final float degFull = 360` must emit
    * `360.0f` and not `360`, or the field becomes an Int constant and divisions using it round. */
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
    * Delegates to `ClassInitTriggerCheck.constantVariable` — the same predicate the class-init
    * census uses, so the two can never disagree about which fields are step-9 content (K22). */
  private def isJavaConstant(v: Tree.ValDef, s: Symbol): Boolean =
    balticporter.tir.ClassInitTriggerCheck.constantVariable(v, s)(using program)

  private def defaultFor(t: TypeRepr): String = t match
    // a union with Null STATES its own default; the union was introduced to retire this cast.
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
                 // Java's single-element @A(x) names its value `value`; scala takes it positionally.
                 else if a.args.sizeIs == 1 && a.args.head._1 == "value" then s"(${term(a.args.head._2, i)})"
                 // a NAMED arg goes through esc — a java element name may be a scala keyword.
                 else s"(${a.args.map((k, v) => s"${esc(k)} = ${term(v, i)}").mkString(", ")})"
      s"${ind(i)}@${tpe(a.tpe)}$args\n"
    }.mkString

  /** The top-level type a symbol lives in, when it is NOT that type itself — the qualifier a
    * nested class's `private` member needs. Java scopes `private` to the enclosing TOP-LEVEL
    * class; scala's bare `private` is class-only, so the faithful rendering is
    * `private[TopLevel]`. Applied ONLY to a NESTED class's members — qualifying a top-level
    * class's own `private` widens java's already-exact meaning and demands `override` where java
    * needed none (regressed libGDX one error). */
  private def privateQualifier(owner: SymId): Option[String] =
    Option.when(currentTopLevel.nonEmpty && currentOwnerSym != currentTopLevelSym)(currentTopLevel)

  /** The ACCESS modifier alone — [[Visibility]] decided the level, this supplies the qualifier.
    * The two package-scoped levels take [[currentPkgTail]], the package being written right now;
    * only a cross-package override carries its own (enclosing) package. */
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
    // `private override` is illegal in scala: a java private method is invisible to subclasses, so
    // it overrides nothing (true of both bare and private[TopLevel]; NOT true of package-private,
    // which does override and needs the keyword — so the rule is scoped to the LEVEL).
    val javaPrivate = visPlan.get(s.id).contains(Visibility.Vis.Private)
    val parts = List(
      vis(s, privateIn),
      if f.isOverride && !javaPrivate then "override " else "",
      if f.isFinal then "final " else "",
      // NOT `sealed`: java's seal and scala's disagree about where subtypes must land, so only
      // [[sealOf]] may answer that question — a flag-shaped answer here would double it.
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

  /** Is the given symbol an anonymous class? Decided from the symbol's `<anon>` NAME (the frontend
    * creates anonymous class symbols with `name = "<anon>"`), never from the `$NNN` suffix in its
    * `fullName` — §4.56: decide from the symbol's anonymous flag, not from a string pattern. */
  private def isAnonOwner(id: SymId): Boolean =
    id != SymId.None && program.symbolOf(id).exists(_.name == "<anon>")

  /** a static member lives in the companion `object`; even inside its own class it must be
    * named `Owner.member`, since a Scala class doesn't see its companion's members unqualified.
    * An ANONYMOUS CLASS has no nameable path (its FQN's numeric suffix becomes a syntax error
    * after package rename), so its members render bare, decided from the `<anon>` name (§4.56). */
  private def staticRef(s: SymId): String =
    val sm = sym(s)
    if sm.flags.isStatic && sm.owner != SymId.None && !isAnonOwner(sm.owner) &&
       program.symbolOf(sm.owner).exists(_.info.isInstanceOf[TypeRepr.TypeRef])
    then s"${typeValue(sm.owner)}.${esc(sm.name)}"
    else if shadowedByCompanionStatic(s) then s"this.${esc(sm.name)}"
    else local(s)

  /** Does a bare reference to this INSTANCE member collide with a static of the same name the
    * enclosing companion carries or re-exports? Java has one namespace for statics and instance
    * fields (the inherited instance field simply wins); scala reports the bare name as ambiguous.
    * `this.` says what java meant. Decided from the TIR symbol, not the frontend (which cannot see
    * it under noClasspath). */
  private def shadowedByCompanionStatic(s: SymId): Boolean =
    val sm = sym(s)
    !sm.flags.isStatic && sm.owner != SymId.None && sm.info != TypeRepr.NoType &&
      !sm.info.isInstanceOf[TypeRepr.MethodType] && !sm.info.isInstanceOf[TypeRepr.PolyType] &&
      classStack.lastOption.exists { cur =>
        // an INHERITED member (declaring it here would shadow the static on its own)
        cur != sm.owner && ancestorsOf(cur).contains(sm.owner) &&
          classStack.exists(c => staticOwnersOf(c).contains(esc(sm.name)))
      }

  /** THE TERM RENDERING DISPATCH — the other half of §2.3(c)'s emitter surface. Not disjoint from
    * [[stat]]: a Term reached as a statement is handed straight here, so every consult happens in
    * the inner scope, joined by NODE IDENTITY rather than kind or origin. */
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
      // JS-C06 — anInstance.staticMethod() evaluates the receiver for its side effects and
      // discards it; a companion call has no receiver slot to keep that evaluation in.
      Obligations.consult(JS.C(6), a.origin)(Option.when(fun match
        case Tree.Select(recv, m, _, _) => staticThroughInstance(recv, m)
        case _                          => false)(()))
      // JS-C22/C23 — java resolves an overload in THREE PHASES and scala in ONE; the decision here
      // is to render the call AS JAVA WROTE IT rather than model a resolver (ENGINE-LIMITS T17).
      // Two rows for JLS 15.12.2's two clauses: the phases, and the most-specific tie-break.
      locally {
        // the ENCLOSING type is java's candidate set (OverloadRiskCheck.rootOf), needed so the
        // emitter's consult and the check's count cannot disagree about which calls they cover.
        val risks = balticporter.tir.OverloadRiskCheck
          .risks(a, overloads, classStack.lastOption.getOrElse(balticporter.tir.SymId.None))(using program)
        Obligations.consult(JS.C(22), a.origin)(
          Option.when(risks.exists(_.issue != balticporter.tir.OverloadRiskCheck.Issue.GenericTieBreak))(()))
        Obligations.consult(JS.C(23), a.origin)(
          Option.when(risks.exists(_.issue == balticporter.tir.OverloadRiskCheck.Issue.GenericTieBreak))(()))
      }
      // JS-G39, the EMITTER half — an external callee's T... reads as a REPEATED parameter, so the
      // pack becomes the tail of the argument list. HERE because argTerms flattens the Tree.Repeated
      // node before the dispatch would ever see it, so an arm there could never be consulted.
      Obligations.consult(JS.G(39), a.origin)(
        Option.when(args.exists(_.isInstanceOf[Tree.Repeated]))(()))
      applyStr(fun, args, i)
    case Tree.TypeApply(fun, targs, _, _) => s"${term(fun, i)}[${targs.map(a => tpe(a.tpe)).mkString(", ")}]"
    case Tree.Assign(l, r, _, _, compoundOp) =>
      // F7 (CLAUDE.md §4.4, JLS 15.26.2): a COMPOUND ASSIGNMENT evaluates the lvalue ONCE; the
      // direct rendering evaluates it TWICE. Non-trivial lvalue subexpressions get bound to a
      // temporary; simple lvalues (ident/this/literal) keep the direct form.
      compoundOp match
        case Some((op, narrow)) if hasNonTrivialSubexpr(l) =>
          val (bindings, lv) = bindLvalue(l, i)
          val rhsStr = operand(r, i)
          val expr = s"$lv $op $rhsStr"
          val rhs = narrow.fold(expr)(nt => s"($expr).asInstanceOf[${tpe(nt)}]")
          s"{ ${bindings.mkString("; ")}; $lv = $rhs }"
        case Some((op, narrow)) =>
          // compound but simple lvalue: direct form, lhs rendered twice
          val expr = s"${term(l, i)} $op ${operand(r, i)}"
          val rhs = narrow.fold(expr)(nt => s"($expr).asInstanceOf[${tpe(nt)}]")
          s"${term(l, i)} = $rhs"
        case None =>
          s"${term(l, i)} = ${term(r, i)}"
    case Tree.Block(stats, expr, _, _, tr) => block(stats, expr, tr, i)
    case lam @ Tree.Lambda(ps, body, _, _, _) =>
      val head = s"(${ps.map(param).mkString(", ")}) => "
      // JS-S21 — a java lambda BODY is a method body, so `return` is legal and means "leave the
      // lambda" (JLS 15.27.2); scala's lambda is an expression and rejects `return` outright. A
      // NESTED `def` restores java's meaning exactly (a `def`'s return cannot be captured by an
      // enclosing loop's `boundary`, unlike a `break`/`continue` would need to be — §4.4).
      Obligations.consult(JS.S(21), body.origin)(Option.when(returnsIn(body))(()))
      if !returnsIn(body) then head + term(body, i)
      else lambdaResultType(lam) match
        case Some(rt) =>
          lambdaSeq += 1
          val n = s"body$$$lambdaSeq"
          head + s"{ def $n(): $rt = ${term(body, i)}; $n() }"
        // REFUSED rather than guessed (ENGINE-LIMITS I9): the def needs the SAM method's result
        // type, and a source-written lambda carries no method to read it off. Counted by
        // OmissionCheck.unnameableLambdaReturn.
        case None => head + term(body, i)
    case Tree.If(c, th, el, _, _)       => s"if (${term(c, i)}) ${term(th, i)} else ${term(el, i)}"
    // A cast ON A POLY EXPRESSION is an ASCRIPTION (ENGINE-LIMITS K17 face 1): javac's cast there
    // supplies the expected type without ever being a runtime cast, and rendered asInstanceOf a
    // literal elaborates to Function0 FIRST, throwing where java's cast never would. `operand`
    // parenthesises the lambda since `(x => y: T)` ascribes the BODY.
    // A METHOD-VALUE ASCRIPTION — `(recv.m: (A, B) => R)`, the shape that PINS which overload scala
    // binds (OverloadRiskCheck.AscribeJavacChoice). Structurally unambiguous as its own arm: JAVA
    // HAS NO METHOD TYPES, so a Tree.Typed target of MethodType cannot be a source-written cast.
    case ty @ Tree.Typed(e, tpt, _, _) if tpt.tpe.isInstanceOf[TypeRepr.MethodType] =>
      // discharged NOT FIRED: this node is neither JS-G34's intersection cast nor JS-E06's
      // unboxing conversion, so both answers are facts rather than defaults (catalog(undischarged)).
      Obligations.consult(JS.G(34), ty.origin)(scala.None)
      Obligations.consult(JS.E(6), ty.origin)(scala.None)
      s"(${operand(e, i)}: ${tpe(tpt.tpe)})"
    case ty @ Tree.Typed(e, tpt, _, _)  =>
      val target = castTarget(e, tpt.tpe)
      // JS-G34 — java's INTERSECTION cast (`(A & B) x`, JLS 4.9) becomes scala's `A & B`.
      Obligations.consult(JS.G(34), ty.origin)(Option.when(target.isInstanceOf[TypeRepr.AndType])(()))
      // JS-E06 — a cast to a PRIMITIVE over a WRAPPER of a different primitive is java's UNBOXING
      // CONVERSION (JLS 5.1.8+5.1.2); asInstanceOf is an assertion that throws instead.
      Obligations.consult(JS.E(6), ty.origin)(
        CastConversionCheck.crossTypeUnbox(ty)(using program).map(_ => ()))
      if polyOperand(e) then s"(${operand(e, i)}: ${tpe(target)})"
      else s"${operand(e, i)}.asInstanceOf[${tpe(target)}]" // Java cast
    // JS-G39 at the position argTerms does NOT reach — a Tree.Repeated outside an argument list
    // still stands for a sequence of its own.
    case r @ Tree.Repeated(es, _, _)    =>
      Obligations.consult(JS.G(39), r.origin)(Some(()))
      es.map(term(_, i)).mkString(", ")
    // `xs*` — CLAUDE.md §6's spread, never `: _*`. operand because `*` binds tighter than the
    // expression it spreads. JS-G40 — java forwards the array whole through an external T...
    // slot, where a bare array would conform as ONE element.
    case s @ Tree.Spread(e, _, _)       =>
      Obligations.consult(JS.G(40), s.origin)(Some(()))
      s"${operand(e, i)}*"
    case Tree.Return(e, _, _)           => "return" + e.map(x => " " + term(x, i)).getOrElse("")
    case Tree.While(c, b, _, _, lbl)    =>
      loopWithJumps(b, lbl, bd => s"while (${term(c, i)}) $bd", term(b, i))
    case Tree.Throw(e, _, _)            => s"throw ${term(e, i)}"
    // JS-G21 — java restricts instanceof to a REIFIABLE type; isInstanceOf tests the erased class
    // exactly as java's does. Partial for the OTHER half — SE16's pattern BINDING has no image.
    case io @ Tree.InstanceOf(e, tpt, _, _) =>
      Obligations.consult(JS.G(21), io.origin)(Some(()))
      s"${operand(e, i)}.isInstanceOf[${tpe(tpt.tpe)}]"
    case Tree.ArrayAccess(a, idx, _, _) => s"${operand(a, i)}(${term(idx, i)})"
    // JS-G17 — java's .length is a FIELD of the array and scala's is a method.
    case al @ Tree.ArrayLength(a, _, _) =>
      Obligations.consult(JS.G(17), al.origin)(Some(()))
      s"${operand(a, i)}.length"
    case Tree.NewArray(el, dims, init, _, _) =>
      init match
        // scala.Array, fully qualified: a bare Array collides with libGDX's own com.badlogic.gdx.utils.Array.
        case Some(es) => s"scala.Array[${tpe(el.tpe)}](${es.map(term(_, i)).mkString(", ")})"
        // java's new T[a][b] sizes every dimension; scala's new Array takes only ONE, so a
        // multi-dimension allocation lowers to Array.ofDim[base](a, b).
        case None if dims.sizeIs > 1 => s"scala.Array.ofDim[${tpe(baseElem(el.tpe))}](${dims.map(term(_, i)).mkString(", ")})"
        case None     => s"new scala.Array[${tpe(el.tpe)}](${dims.map(term(_, i)).mkString(", ")})"
    case Tree.ForEach(b, it, body, _, _, lbl) =>
      val raw  = sym(b.symbol).name
      val name = esc(raw)
      // JS-S15 — java's enhanced-for evaluates the ITERABLE once; satisfied by construction (the
      // generator interpolates term(it, …) exactly once).
      Obligations.consult(JS.S(15), it.origin)(Some(()))
      // TWO independent reasons to re-bind into one alias (K7 + F16): the DECLARED TYPE may differ
      // from the iterable's element type, and the binding may be REASSIGNED, which scala's
      // generator val does not permit.
      val mutable = reassignsBinding(body, b.symbol)
      val kw      = if mutable then "var" else "val"
      // JS-S16 — the binding may be REASSIGNED or DECLARED at a supertype; scala's generator is a
      // val of the element's own type and permits neither.
      Obligations.consult(JS.S(16), b.origin)(Option.when(mutable || widenedBinding(b, it).isDefined)(()))
      // JS-G04 — a captured WILDCARD on iteration has no nameable type (java relates the element
      // and its collection as ONE capture; scala captures per use) — the same repair as JS-S16, at
      // the shape with no scala name at all (ENGINE-LIMITS K7).
      Obligations.consult(JS.G(4), b.origin)(Option.when(it.tpe match
        case TypeRepr.AppliedType(_, args) => args.exists(_.isInstanceOf[TypeRepr.TypeBounds])
        case _                             => false)(()))
      // JS-S26 — a return inside an enhanced-for body becomes a NON-LOCAL RETURN under .foreach
      // desugaring; the lowering avoids it by emitting a while loop instead.
      Obligations.consult(JS.S(26), body.origin)(Option.when(returnsIn(body))(()))
      // K9 — a JDK Iterable the pipeline LEFT in the java namespace has no scala foreach; emit
      // java's own desugaring (JLS 14.14.2) instead. Decided from the POST-PIPELINE type (§4.56):
      // a retyped type or runtime shim already has foreach, so only an external java.*/javax.*
      // type needs the protocol.
      val keptJdk = isKeptJdkIterable(it.tpe)
      val hasReturn = returnsIn(body)
      (widenedBinding(b, it), mutable, keptJdk, hasReturn) match
        case (None, false, false, false) => loopWithJumps(body, lbl, bd => s"for ($name <- ${term(it, i)}) $bd", term(body, i))
        case (_, _, true, _) =>
          // JLS 14.14.2's own desugaring: evaluate the iterable ONCE, obtain its iterator, loop
          // with hasNext()/next(); break/continue go through loopWithJumps as the `for` form does.
          val itVar = esc(s"$raw$$it")
          val widened = widenedBinding(b, it)
          val decl = widened.getOrElse(tpe(b.tpt.tpe))
          val nextExpr = if widened.isDefined then s"$itVar.next().asInstanceOf[$decl]" else s"$itVar.next()"
          loopWithJumps(body, lbl,
            bd => s"{ val $itVar = ${term(it, i)}.iterator(); while ($itVar.hasNext()) { $kw $name: $decl = $nextExpr; $bd } }",
            term(body, i))
        case (_, _, _, true) =>
          // a return inside a for-each body: lower to a while loop to avoid the non-local return
          // .foreach desugaring would produce.
          // PARENS: decided from the CALLEE SYMBOL's declaration, not receiver ownership (§4.56) —
          // program.owns was wrong in both directions for the runtime shims and for a converted
          // iterator.
          val iterHeadSym = headSymOf(it.tpe).getOrElse(SymId.None)
          val iterHasParens = calleeHasParens(iterHeadSym, "iterator")
          val iterCall = if iterHasParens then ".iterator()" else ".iterator"
          // hasNext's arity follows iterator's — one protocol (java or scala) is consistent
          // throughout, and the iterator TYPE's own members may not be interned yet to look up.
          val hasNextCall = if iterHasParens then ".hasNext()" else ".hasNext"
          val itVar = esc(s"$raw$$it")
          val widened = widenedBinding(b, it)
          val decl = widened.getOrElse(tpe(b.tpt.tpe))
          val nextExpr = if widened.isDefined then s"$itVar.next().asInstanceOf[$decl]" else s"$itVar.next()"
          loopWithJumps(body, lbl,
            bd => s"{ val $itVar = ${term(it, i)}$iterCall; while ($itVar$hasNextCall) { $kw $name: $decl = $nextExpr; $bd } }",
            term(body, i))
        case (widened, _, _, _) =>
          // the alias is INSIDE the loop body, re-bound each iteration outside any continue
          // boundary loopWithJumps adds — java's own semantics. Derive the fresh name from the RAW
          // one, not the escaped one: appending to an escaped keyword produces a non-identifier
          // (measured, 0 -> 3 on libGDX).
          val fresh = esc(s"$raw$$e")
          // the CAST belongs to the widening only: a reassignment-only rebind already yields the
          // declared type.
          val decl = widened.getOrElse(tpe(b.tpt.tpe))
          val rhs  = if widened.isDefined then s"$fresh.asInstanceOf[$decl]" else fresh
          loopWithJumps(body, lbl,
            bd => s"for ($fresh <- ${term(it, i)}) { $kw $name: $decl = $rhs; $bd }",
            term(body, i))
    case Tree.For(init, cond, upd, body, _, _, lbl) =>
      // the UPDATE must run on a continue too, so it sits OUTSIDE the per-iteration boundary.
      // ONE LINE joined by `;`: a comment here would swallow the rest of the loop header, so any
      // that reached this far is stripped rather than emitting a broken file.
      val is = init.map(flatStat).mkString("; ")
      val c  = cond.map(term(_, i)).getOrElse("true")
      val u  = upd.map(flatStat).mkString("; ")
      // JS-S17 — java's classic for scopes ForInit to the loop and runs UPDATE on a continue too;
      // while has neither clause, so both must be PLACED explicitly.
      Obligations.consult(JS.S(17), body.origin)(Option.when(init.nonEmpty || upd.nonEmpty)(()))
      loopWithJumps(body, lbl, bd => s"{ $is; while ($c) { $bd; $u } }", term(body, i))
    case t: Tree.Try                    => tryStr(t, i)
    case m: Tree.Match                  => matchStr(m, i)
    case mr @ Tree.MethodRef(q, s, mrT, _, referent) =>
      val isCtor = sym(s).name == "<init>" // `Type::new` → a factory function `() => new Type()`
      val isStaticRef = referent.isInstanceOf[Referent.Static]
      // JS-G43, the EMITTER half — five java forms share one syntax and each becomes a DIFFERENT
      // scala lambda, discriminated right here (isCtor, isStatic, the array element test below).
      Obligations.consult(JS.G(43), mr.origin)(Some(()))
      // JS-G17's third face — T[]::new is an IntFunction[T[]], not a no-arg supplier (a scala
      // array needs a LENGTH). Fires only at the array-constructor form.
      Obligations.consult(JS.G(17), mr.origin)(Option.when(isCtor && (q match
        case Left(tt) => tt.tpe match
          case TypeRepr.AppliedType(TypeRepr.TypeRef(_, as), List(_)) => sym(as).fullName == "scala.Array"
          case _                                                     => false
        case _ => false))(()))
      // JS-G33 — SAM CONVERSION eligibility, asked from every Left form (constructor, unbound
      // instance, static — the static form is now an explicit lambda too) and every Right form.
      Obligations.consult(JS.G(33), mr.origin)(Option.when(q match
        case Left(_) => true
        case Right(_) => true)(()))
      // JS-C52 — @FunctionalInterface governs eta-expansion warnings; the static arm now emits
      // explicit lambdas at EVERY arity to avoid the warning, so this fires at every static reference.
      Obligations.consult(JS.C(52), mr.origin)(Option.when(isStaticRef && !isCtor)(()))
      q match
        // an ARRAY constructor reference T[]::new is an IntFunction[T[]] (size) => new T[size].
        // Route through ctorTpe, which drops wildcard arguments the scala compiler would reject as
        // "type argument must be fully defined", erasing a wildcard element to Object.
        case Left(tt) if isCtor => tt.tpe match
          case TypeRepr.AppliedType(TypeRepr.TypeRef(_, as), List(el)) if sym(as).fullName == "scala.Array" =>
            val elem = el match
              case _: TypeRepr.TypeBounds => "java.lang.Object"
              case other                  => tpe(other)
            s"((size: scala.Int) => new scala.Array[$elem](size))"
          // an ordinary T::new takes THE CONSTRUCTOR'S OWN PARAMETERS (not just a nilary factory),
          // read off Tree.MethodRef.referent; parameters go un-annotated since a constructor
          // reference is a poly expression samAscribed's target types.
          case _ =>
            val ps = referent match
              case Referent.Instance(n) => (0 until n).map(k => s"a$k$$").toList
              case Referent.Static(_)   => Nil // a constructor is never static; JLS 8.8.3
            samAscribed(s"((${ps.mkString(", ")}) => new ${ctorTpe(tt.tpe)}(${ps.mkString(", ")}))",
                        mrT, tt.tpe)
        // Type::method is TWO java forms sharing one syntax: a STATIC method is Type.method; an
        // INSTANCE method is an UNBOUND reference (the receiver becomes the first parameter).
        // At arity ZERO the qualified name is not a function at all — scala 3 eta-expands a
        // parameterful method but refuses a nullary one — so a nilary static reference (a
        // Supplier<T>-shaped default) takes the lambda form (ENGINE-LIMITS G32); every other
        // arity keeps the name.
        case Left(tt) if isStaticRef && referent == Referent.Static(0) =>
          samAscribed(s"(() => ${tpe(tt.tpe)}.${local(s)}())", mrT, tt.tpe)
        // a static method reference at NON-ZERO arity: bare name where the target SAM type carries
        // @FunctionalInterface (eta-expansion is warning-free), else an explicit lambda to avoid
        // the -Werror'd eta-expansion warning. An unreadable annotation set is treated as
        // UNANNOTATED — the safe direction is the lambda, never a bare name scalac might reject.
        case Left(tt) if isStaticRef && targetHasFunctionalInterface(mrT) =>
          s"${tpe(tt.tpe)}.${local(s)}"
        case Left(tt) if isStaticRef =>
          val arity = referent match { case Referent.Static(n) => n; case _ => 0 }
          val formals = methodParams(s)
          val named = formals.sizeIs == arity && !hasWildcardArg(tt.tpe)
          val ps = (0 until arity).map(k =>
            if named then s"a$k$$: ${tpe(formals(k))}" else s"a$k$$").mkString(", ")
          val as = (0 until arity).map(k => s"a$k$$").mkString(", ")
          samAscribed(s"(($ps) => ${tpe(tt.tpe)}.${local(s)}($as))", mrT, tt.tpe)
        case Left(tt) =>
          val self  = "self$"
          // ARITY is java's, off the node; parameter TYPES are the SYMBOL's, and the two can
          // disagree for an external member with no readable MethodType — where they disagree the
          // whole lambda goes un-annotated rather than half-annotated (same poly-expression rule).
          val arity = referent match
            case Referent.Instance(n) => n
            case Referent.Static(n)   => n // unreachable: the arm above took it
          val formals = methodParams(s)
          val named   = formals.sizeIs == arity && !hasWildcardArg(tt.tpe)
          val as      = (0 until arity).map(k => s"a$k$$").mkString(", ")
          // the receiver parameter is ANNOTATED only when the qualifier names a real type: a RAW
          // qualifier annotated with it makes the call return an unusable capture, since java's
          // reference takes its meaning entirely from the TARGET (e.g. Comparator.comparing).
          val recvT = if named then s": ${tpe(tt.tpe)}" else ""
          val extra = (0 until arity).toList.map(k =>
            if named then s"a$k$$: ${tpe(formals(k))}" else s"a$k$$")
          val ps    = (s"$self$recvT" :: extra).mkString(", ")
          samAscribed(s"(($ps) => $self.${local(s)}($as))", mrT, tt.tpe)
        case Right(e)           => s"${term(e, i)}.${local(s)}"
    // Java's break leaves the loop; scala.util.boundary/break is the faithful shape (§4.4). A
    // LABELLED break reaches it through Tree.Labeled or the loop's own label field.
    case Tree.Break(scala.None, _, _) if breakTarget.isDefined =>
      breakTarget.filter(_.nonEmpty) match
        case Some(n) => s"scala.util.boundary.break(())(using $n)" // another boundary sits inside
        case _       => "scala.util.boundary.break(())"
    case Tree.Break(Some(l), _, _) if labelBreak.contains(l) =>
      val n = labelBreak(l)
      if n.isEmpty then "scala.util.boundary.break(())" else s"scala.util.boundary.break(())(using $n)"
    // an unlabelled break with no boundary belongs to a SWITCH terminator, already stripped by the
    // frontend — one reaching here is unrecognised. Say WHICH (§4.45).
    case b @ Tree.Break(scala.None, _, _)   =>
      unrenderable("break", "no enclosing loop or switch, and the frontend did not recognise it as " +
        "a switch-case terminator", "give the enclosing construct a `boundary`, or teach the " +
        "frontend this jump's shape", b.origin, "/* break: no enclosing loop or switch */ ()")
    case b @ Tree.Break(Some(l), _, _)      =>
      unrenderable("break", s"labelled `break $l` whose label is not in scope at this point",
        s"the labelled statement `$l` needs a NAMED boundary (§4.4); check `Tree.Labeled` reached it",
        b.origin, s"/* break $l: label not in scope */ ()")
    // a NON-TAIL yield (JLS 14.21) leaves the switch expression's arm with this value; matchStr
    // has put a named, value-carrying boundary around the arm. A TAIL yield never reaches here.
    // `case String s ->` — a java TYPE PATTERN as a case label (JLS 14.11.1); scala's typed
    // pattern is the exact image.
    case Tree.TypePattern(b, tpt, _, _) => s"${local(b)}: ${tpe(tpt.tpe)}"
    // `case Point(x, y)` — java's RECORD PATTERN derives over the record's ACCESSORS (JLS
    // 14.30.1, JS-C43). Named through typeValue (the companion's value path, where unapply is).
    case Tree.RecordPattern(tpt, ps, _, _) =>
      val nm = headSymOf(tpt.tpe).map(typeValue).getOrElse(tpe(tpt.tpe))
      s"$nm(${ps.map(term(_, i)).mkString(", ")})"
    // an UNCONDITIONAL component: the binding alone (a type test would be a different program).
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
    // `name: stmt` — java's label on a NON-loop statement; the boundary goes around the STATEMENT
    // (§4.4), always named since a labelled jump crosses nested loops and switches by definition.
    case Tree.Labeled(name, s, _, _) =>
      // JS-S02 — java's label sits on ANY statement (JLS 14.7); scala has no labelled statement,
      // so the image is a NAMED boundary. Fires only where something really breaks to the label.
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
    // java's POST-increment yields the value BEFORE the update; the temporary is what makes it exact.
    case Tree.IncDec(tgt, op, post, _, _) =>
      // F7 (CLAUDE.md §4.4, JLS 15.14.2/15.15.1): same lvalue-once rule as compound assignment.
      if hasNonTrivialSubexpr(tgt) then
        val (bindings, lv) = bindLvalue(tgt, i)
        val prefix = bindings.mkString("; ")
        if post then s"{ $prefix; val ${'$'}prev = $lv; $lv $op= 1; ${'$'}prev }"
        else s"{ $prefix; $lv $op= 1; $lv }"
      else
        if post then s"{ val ${'$'}prev = ${term(tgt, i)}; ${term(tgt, i)} $op= 1; ${'$'}prev }"
        else s"{ ${term(tgt, i)} $op= 1; ${term(tgt, i)} }"
    case Tree.DoWhile(b, c, _, _, lbl)  => // Scala 3 has no do-while
      // JS-S18 — scala 3 removed do-while, so the body is lifted into the condition instead.
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

  /** Render a marker. `Open` has two answers: best-effort (the approximation inside deterministic
    * comment fences, since a comment cannot change program shape) or, by default,
    * `scala.compiletime.error` — the loudest available answer, deliberately opposite
    * `unrenderable`'s default, since here the engine has nothing to say at all. Either way the
    * refusal is recorded as `Decision.Kind.Unrenderable`. */
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

  /** A Java constructor reference (`Foo::new`) is typed by the TARGET functional interface java
    * resolved, not by `Foo`. Emitted bare, `() => new Foo()` is a `Function0`, which scala
    * SAM-converts to ANY single-abstract-method type, making an overload set AMBIGUOUS where
    * java's was not — so the resolved target is re-stated as an ascription. Strictly guarded
    * (concrete type, not the constructed type itself) so this can only ever narrow, never mis-type. */
  /** Does the TARGET SAM type carry `@FunctionalInterface`? REFUTER polarity (§4.56): an
    * unreadable annotation set is treated as UNANNOTATED, since the safe direction is the
    * explicit lambda nobody warns about. */
  private def targetHasFunctionalInterface(target: TypeRepr): Boolean =
    headSymOf(target).flatMap(program.symbolOf).exists(
      _.annotations.exists(_.tpe match
        case TypeRepr.TypeRef(_, a) => sym(a).fullName == "java.lang.FunctionalInterface"
        case _                     => false))

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

  // -- F7 lvalue binding (CLAUDE.md §4.4, JLS 15.26.2 / 15.14.2 / 15.15.1) ---------------------

  /** Does this lvalue contain a subexpression whose re-evaluation could have an effect?
    *
    * `effectFree` conservatively returns `false` for every `ArrayAccess`, but `arr(0)` with both
    * `arr` and `0` effect-free does not need binding — evaluating them twice is evaluating them
    * once. This function looks ONE LEVEL inside an assignable form and asks whether any
    * constituent subexpression is non-trivial, which is the question the compound-assignment and
    * increment arms need. */
  private def hasNonTrivialSubexpr(lv: Term): Boolean = lv match
    case _: Tree.Ident | _: Tree.This | _: Tree.Literal => false
    case Tree.Select(q, _, _, _)                        => !effectFree(q)
    case Tree.ArrayAccess(arr, idx, _, _)               => !effectFree(arr) || !effectFree(idx)
    case _                                              => true

  /** Bind the non-trivial subexpressions of an assignable lvalue to temporaries, returning
    * (list-of-val-bindings, bound-lvalue-string).
    *
    * For `arr(f())`: `(List("val $lv1 = f()"), "arr($lv1)")`
    * For `g().field`: `(List("val $lv1 = g()"), "$lv1.field")` */
  private def bindLvalue(lv: Term, i: Int): (List[String], String) = lv match
    case Tree.ArrayAccess(arr, idx, _, _) =>
      val bindings = List.newBuilder[String]
      val arrStr =
        if effectFree(arr) then term(arr, i)
        else { lvSeq += 1; val n = s"$$lv$lvSeq"; bindings += s"val $n = ${term(arr, i)}"; n }
      val idxStr =
        if effectFree(idx) then term(idx, i)
        else { lvSeq += 1; val n = s"$$lv$lvSeq"; bindings += s"val $n = ${term(idx, i)}"; n }
      (bindings.result(), s"$arrStr($idxStr)")
    case Tree.Select(qual, fld, _, _) =>
      lvSeq += 1
      val n = s"$$lv$lvSeq"
      (List(s"val $n = ${term(qual, i)}"), s"$n.${local(fld)}")
    case _ =>
      // fallback: bind the whole thing
      lvSeq += 1
      val n = s"$$lv$lvSeq"
      (List(s"val $n = ${term(lv, i)}"), n)

  // `compoundAssignParts` removed — the compound-assignment fact is now carried on `Tree.Assign`'s
  // `compound` field, set by the frontend. No shape reconstruction needed.

  /** A `Tree.Repeated` in an ARGUMENT position is the argument list's TAIL, not one argument —
    * decisive at ZERO elements, where a node rendering `""` would leave `f(a, )` instead of `f(a)`
    * (java's f(a) against f(A, B...), e.g. Paths.get(".")). Flattened HERE, a fact about the
    * position: the same node elsewhere still stands for a sequence of its own. */
  private def argTerms(args: List[Term]): List[Term] =
    if !args.exists(_.isInstanceOf[Tree.Repeated]) then args
    else args.flatMap { case Tree.Repeated(es, _, _) => es; case a => List(a) }

  private def applyStr(fun: Term, argsIn: List[Term], i: Int): String =
    applyStr0(fun, argTerms(argsIn), i)

  private def applyStr0(fun: Term, args: List[Term], i: Int): String = fun match
    case Tree.New(tpt, _, _, anon) =>
      s"new ${ctorTpe(tpt.tpe)}(${args.map(term(_, i)).mkString(", ")})${anonBody(anon, i)}"
    // operators (populator tags them scala.<op>#…) render infix/prefix, not .op(x) — EXCEPT on a
    // super receiver, where scala's grammar admits super only as a selection qualifier (`super
    // ++= m` is a syntax error; `super.++=(m)` is the only legal spelling).
    case Tree.Select(recv: Tree.Super, m, _, _) if sym(m).fullName.startsWith("scala.<op>#") =>
      s"${operand(recv, i)}.${esc(sym(m).name)}(${args.map(term(_, i)).mkString(", ")})"
    case Tree.Select(recv, m, _, _) if sym(m).fullName.startsWith("scala.<op>#") =>
      val op = sym(m).name
      if op.startsWith("unary_") then prefixOp(op.stripPrefix("unary_"), operand(recv, i))
      else s"${operand(recv, i)} $op ${args.map(operand(_, i)).mkString(", ")}"
    case Tree.Select(recv, m, _, _) if sym(m).name == "<init>" =>
      val kw = recv match { case _: Tree.Super => "super"; case _ => "this" }
      s"$kw(${args.map(term(_, i)).mkString(", ")})"
    // JAVA PERMITS A STATIC MEMBER CALLED THROUGH AN INSTANCE (`family.one(…)`, `one` static);
    // scala's static emits into the companion, unreachable from an instance. Java evaluates and
    // DISCARDS the receiver, so an effectful receiver is evaluated first in a block (§4.4) and an
    // effect-free one is simply dropped.
    case Tree.Select(recv, m, _, _) if staticThroughInstance(recv, m) =>
      val call = s"${typeValue(sym(m).owner)}.${local(m)}(${args.map(term(_, i)).mkString(", ")})"
      if effectFree(recv) then call else s"{ ${term(recv, i)}; $call }"
    case Tree.Select(recv, m, _, _) if numericOverloadAscription(recv, m).isDefined =>
      s"(${term(fun, i)}: ${numericOverloadAscription(recv, m).get})(${args.map(term(_, i)).mkString(", ")})"
    // `X.values()` on an enum this emitter renders as a scala 3 enum: the desugaring's values is
    // PARENLESS, so the parens come off here. Asked of the ENUM'S OWN DECLARATION (EnumShape), not
    // the callee symbol — the frontend interns an enum's synthesised values under an anonymous
    // owner (§4.59), but the QUALIFIER's class symbol is exact.
    case Tree.Select(qual, m, _, _) if args.isEmpty && sym(m).name == "values" && scalaEnumQualifier(qual) =>
      term(fun, i)
    // T22 — a.name() on an ANNOTATION THIS PROGRAM DECLARES: java's element is both the write-name
    // and the read-accessor, but the emitted class keeps java's name at the constructor parameter
    // only, so the read is a field selection and the parens come off. Asked of the callee's OWNER
    // and PROGRAM OWNERSHIP (§4.56), never the name — an external annotation stays a method call.
    case Tree.Select(_, m, _, _) if args.isEmpty && emittedAnnotationElement(m) =>
      term(fun, i)
    // P11 — EXTERNAL PARENLESS: a member listed in `externalParenless` is called WITHOUT `()`.
    // Legal on the JVM too (Scala 3 auto-applies a Java nullary method), and required on JS/Native
    // where the platform shim declares the member parenless.
    case Tree.Select(_, m, _, _) if args.isEmpty && isExternalParenless(m) =>
      term(fun, i)
    case _ =>
      // through an ASCRIPTION, which wraps the callee without changing which member it is, so a
      // pinned resolutions selection does not disable the raw-parent alignment.
      def callee(t: Term): Option[SymId] = t match
        case Tree.Select(_, m, _, _)    => Some(m)
        case Tree.Ident(m, _, _)        => Some(m)
        case Tree.Typed(inner, _, _, _) => callee(inner)
        case _                          => scala.None
      val as = callee(fun).flatMap(alignedArgs(_, args, i)).getOrElse(args.map(term(_, i)))
      s"${term(fun, i)}(${as.mkString(", ")})"

  /** does this qualifier NAME a type this emitter renders as a scala 3 `enum`? A TYPE and never a
    * value: a `Select` is admitted beside an `Ident` for a NESTED enum's `Outer.Inner`, and the
    * answer comes from `EnumShape`, so a value of enum type (`l.values()`) cannot be mistaken —
    * its symbol is a local, not a class. */
  private def scalaEnumQualifier(qual: Term): Boolean =
    val s = qual match
      case Tree.Ident(s, _, _)     => s
      case Tree.Select(_, s, _, _) => s
      case _                       => SymId.None
    program.definitionOf(s).collect { case cd: Tree.ClassDef => cd }
      .exists(balticporter.tir.EnumShape.isScalaEnum(program, _))

  /** is this callee an ELEMENT of an `@interface` THIS PROGRAM DECLARES — a constructor parameter
    * `classDef1`'s annotation arm emitted? Three structural conjuncts, none a name (§4.56): owner
    * is program-OWNED, owner's flag says java wrote @interface, and callee takes no parameters
    * (JLS 9.6 admits only elements, constants and member types). */
  private def emittedAnnotationElement(m: SymId): Boolean =
    val o = sym(m).owner
    o != SymId.None && program.owns(o) && sym(o).flags.isAnnotation &&
      (sym(m).info match
        case TypeRepr.MethodType(ps, _, _) => ps.isEmpty
        case _                             => false)

  /** widening rank — a value of rank r converts implicitly to any numeric type of higher rank.
    * `Char` and `Short` share a rank because neither widens to the other. */
  private val numericRank = Map("scala.Byte" -> 1, "scala.Short" -> 2, "scala.Char" -> 2,
                                "scala.Int" -> 3, "scala.Long" -> 4, "scala.Float" -> 5,
                                "scala.Double" -> 6)

  /** Java resolves an overload by EXACT match; scala widens numerics first and then finds no
    * most-specific alternative (`setRegion(int×4)` beside `setRegion(float×4)`, four Int args).
    * Ascribing the method's function type names the alternative java chose. Fires only where a
    * sibling of the same name/arity is WEAKLY WIDER at every position and strictly wider at one, so
    * checking the direction avoids ascribing every numeric call (measured 175 sites, 1 ambiguous).
    * The RESULT goes through [[ParentSubst]] since a declared result may name the declaring type's
    * own type parameter (G12), which the call site does not have; parameters need no substitution
    * ([[numericParams]] admits only formals whose head is a numeric primitive). A variable the
    * substitution cannot reach (unowned receiver, raw receiver, the callee's own method variable)
    * DECLINES the ascription and the call renders as java wrote it (T17's stated refusal). */
  private def numericOverloadAscription(recv: Term, m: SymId): Option[String] =
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
      res     = ParentSubst.subst(d.returnTpt.tpe, receiverSubst(recv))
      // a RAW receiver binds the variable to a wildcard, which names nothing either — declined the
      // same way `OverloadRiskCheck.ascription` does (its bareWildcard is top-level only: List[?]
      // is a nameable result).
      if !res.isInstanceOf[TypeRepr.TypeBounds] && !namesForeignTypeParam(res)
    yield s"(${ps.map(tpe).mkString(", ")}) => ${tpe(res)}"

  /** what the RECEIVER's static type says an ancestor's type parameters are — [[ParentSubst.of]]
    * composed with the receiver's OWN application, collapsing `Bar.T` to `Int` for `Foo[Int] <:
    * Bar[X]` in one map. Empty for a receiver this program does not declare, or a raw one — the
    * caller's own nameability test then declines. */
  private def receiverSubst(recv: Term): Map[SymId, TypeRepr] =
    def headArgs(t: TypeRepr): Option[(SymId, List[TypeRepr])] = t match
      case TypeRepr.AppliedType(TypeRepr.TypeRef(_, s), as) => Some(s -> as)
      case TypeRepr.TypeRef(_, s)                           => Some(s -> Nil)
      case TypeRepr.ThisType(s)                             => Some(s -> Nil)
      case _                                                => scala.None
    headArgs(recv.tpe).flatMap { (s, as) =>
      program.definitionOf(s).collect { case c: Tree.ClassDef => c }.map { cd =>
        val own = cd.tparams.map(_.symbol).zip(as).toMap
        ParentSubst.of(cd)(using program).view.mapValues(ParentSubst.subst(_, own)).toMap ++ own
      }
    }.getOrElse(Map.empty)

  /** does this type mention a type PARAMETER no enclosing declaration here binds? Structural
    * (§4.56): a parameter's symbol is OWNED by the declaration that wrote it, so the question is
    * whether that owner is one of the classes this emitter is currently inside. */
  private def namesForeignTypeParam(t: TypeRepr): Boolean =
    def foreign(s: SymId): Boolean =
      val sm = sym(s)
      sm.flags.isParam && !classStack.contains(sm.owner)
    def go(x: TypeRepr): Boolean = x match
      case TypeRepr.TypeRef(_, s)        => foreign(s)
      case TypeRepr.AppliedType(tc, as)  => go(tc) || as.exists(go)
      case TypeRepr.AndType(l, r)        => go(l) || go(r)
      case TypeRepr.OrType(l, r)         => go(l) || go(r)
      case TypeRepr.ByNameType(u)        => go(u)
      case TypeRepr.TypeBounds(lo, hi)   => go(lo) || go(hi)
      case TypeRepr.Refinement(p, _, in) => go(p) || go(in)
      case TypeRepr.MethodType(pss, r, _) => pss.exists((_, pt) => go(pt)) || go(r)
      case _                             => false
    go(t)

  /** A Java anonymous class's body → Scala's anonymous-class expression `new Base(args) { … }`.
    * The symbol is pushed on `classStack` while members render, so `thisRef` qualifies an
    * enclosing reference as `Outer.this.m`. Captured locals need no lowering — scala closes over
    * them where javac synthesised ctor parameters. `Some(Nil)` still renders braces: `new Base()`
    * and `new Base(){}` are DIFFERENT types. */
  private def anonBody(anon: Option[Tree.AnonClass], i: Int): String = anon match
    case None    => ""
    case Some(a) =>
      classStack.append(a.symbol)
      val members = try a.body.map(stat(_, i + 1)).filter(_.trim.nonEmpty) finally classStack.removeLast()
      if members.isEmpty then " {}" else s" {\n${joinStats(members)}\n${ind(i)}}"

  /** parenthesize a term when it is an operand, where bare juxtaposition would misparse: an
    * operator application (precedence) and any control-flow expression (`if`/`match`, which scala
    * reads as "end of statement" otherwise). A RECEIVER IS AN OPERAND TOO — `(c ? a :
    * b).toString()` is ordinary java, and unparenthesised the method call binds to one branch only
    * (§4.4). Covers Select's qualifier, InstanceOf's, ArrayLength's and ArrayAccess's. */
  private def operand(t: Term, i: Int): String = t match
    case Tree.Apply(Tree.Select(_, m, _, _), _, _, _, _) if sym(m).fullName.startsWith("scala.<op>#") =>
      s"(${term(t, i)})"
    case _: Tree.If | _: Tree.Match | _: Tree.Lambda => s"(${term(t, i)})"
    case _ => term(t, i)

  /** the same question for a term spliced into a [[Tree.Opaque]] HOLE, which is harder: a hole's
    * neighbours are whatever a policy entry wrote around `{recv}`, so this over-approximates by
    * three more node kinds than [[operand]] — a redundant parens pair costs two characters.
    * NOT folded into `operand`, which is on every emitted expression's path in every port. */
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

  /** The first character a PARSER would see in `s` — comments skipped, exactly as scala's scanner
    * skips them. A bare `l.trim.startsWith("{")` test misses a preceding comment, which is
    * whitespace to the parser (measured: 2 "anonymous class cannot extend final class" errors). */
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

  /** A java `try`, plus the arm that keeps a translated JUMP out of its handlers. Java's
    * break/continue is not an exception and no catch can intercept one, at any breadth; scala's
    * translation IS one (`boundary.Break extends RuntimeException`), so a broad catch would
    * silently swallow the jump — not incidental, since dotty's `DropBreaks.prepareForTry` shadows
    * every enclosing label, so a break under a try is ALWAYS the exception form. The repair is a
    * re-throw arm ahead of the java arms, interposed only where a jump really CROSSES the catch
    * (`crossesCatch`, exact from the emitter's own boundary state). `finally` is untouched, since
    * both languages run it and let the jump through. */
  private def tryStr(t: Tree.Try, i: Int)(using Obligations): String =
    val (res, body, catches, fin) = (t.resources, t.body, t.catches, t.finalizer)
    // JS-S13 — try-with-resources closes on ANY completion, in reverse order, BEFORE this try's
    // own catch (ENGINE-LIMITS F5).
    Obligations.consult(JS.S(13), t.origin)(Option.when(res.nonEmpty)(()))
    // JS-S12 — a finally completing abruptly DISCARDS the try's own abrupt completion. Row stays
    // Partial: no corpus fixture has a finally that is itself the source (§2.3(a)).
    Obligations.consult(JS.S(12), t.origin)(Option.when(fin.isDefined)(()))
    val guard =
      if catches.exists(c => Jumps.catchesBreak(c.param.tpt.tpe)(using program)) && crossesCatch(body) then
        breakGuarded += t.id
        s"${ind(i + 1)}case ${TirEmitter.BreakGuard}: scala.util.boundary.Break[?] => throw ${TirEmitter.BreakGuard}" +
          s" // §4.4: a java jump is not catchable\n"
      else ""
    // JS-S11 — a translated CATCH swallows a translated JUMP; read off the guard just decided so
    // the consult cannot drift from the decision.
    Obligations.consult(JS.S(11), t.origin)(Option.when(guard.nonEmpty)(()))
    // A MULTI-CATCH's union type must be PARENTHESISED in a typed pattern: bare `case e: A | B =>`
    // parses `|` as a PATTERN ALTERNATIVE, which may not bind a variable. Narrowed to the union.
    def catchTpe(t: TypeRepr): String = t match
      case _: TypeRepr.OrType => s"(${tpe(t)})"
      case _                  => tpe(t)
    val cs = catches.map { c =>
      // an unused catch variable is emitted as `_` — java commonly declares one it never reads
      // (`catch (Exception ignored)`), and `-Wunused:patvars` flags the name under `-Werror`.
      // The test: does any Ident in the body reference this symbol?
      val paramUsed = StandardTraversal.scanTerm(c.body, false) {
        case (true, _) => true
        case (_, Tree.Ident(s, _, _)) if s == c.param.symbol => true
        case (acc, _) => acc
      }(using program)
      val pname = if paramUsed then esc(sym(c.param.symbol).name) else "_"
      s"${ind(i + 1)}case $pname: ${catchTpe(c.param.tpt.tpe)} => ${term(c.body, i + 1)}"
    }.mkString("\n")
    val cl = if catches.isEmpty then "" else s" catch {\n$guard$cs\n${ind(i)}}"
    val fl = fin.map(f => s" finally ${term(f, i)}").getOrElse("")
    // The RESOURCES wrap the BODY and nothing else — JLS 14.20.3.2 defines an extended
    // try-with-resources as the basic one nested inside `try … Catches Finally`, i.e. every
    // resource is closed BEFORE this try's own `catch`/`finally` runs.
    if res.isEmpty then s"try ${term(body, i)}$cl$fl"
    else
      resourceLowered += t.id
      s"try ${resourceStr(res, body, i)}$cl$fl"

  /** JLS 14.20.3.1's lowering of a try-with-resources, emitted INLINE — one nesting per resource,
    * as statements rather than a `Using`/lambda combinator (this emitter's `return` and
    * `break`/`continue` render bound to labels outside the try and cannot survive being moved into
    * a lambda body). Reproduces java's contract: reverse declaration order (falls out of the
    * nesting), every `close()` attempted even after an earlier one throws, suppression rather than
    * replacement on the body's own exception, and closed on ANY completion including a jump — a
    * jump takes the `Break` arm ahead of the recorder (java's break carries no exception to
    * suppress into, and `boundary.Break` is constructed with suppression disabled, so leaving
    * `primary` null routes the finally to a bare `close()`). The catch-all binder and `primary`
    * are numbered per nesting level to avoid shadowing across resources in one statement. */
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
        // the JUMP arm, AHEAD of the recorder — see the doc above.
        b ++= s"${ind(i + 1)}catch { case ${TirEmitter.BreakGuard}: scala.util.boundary.Break[?] => throw ${TirEmitter.BreakGuard} // §4.4: a java jump carries no exception to suppress into\n"
        b ++= s"${ind(i + 2)}case $thr: java.lang.Throwable => { $p = $thr; throw $thr } }\n"
        b ++= s"${ind(i + 1)}finally if $name != null then {\n"
        b ++= s"${ind(i + 2)}if $p != null then { try $name.close() catch { case $sup: java.lang.Throwable => $p.addSuppressed($sup) } }\n"
        b ++= s"${ind(i + 2)}else $name.close()\n"
        b ++= s"${ind(i + 1)}}\n"
        b ++= s"${ind(i)}}"
        b.toString

  /** one counter for every resource block this emitter opens — see [[resourceStr]] for why the
    * primary/thrown/suppressed binders may not repeat across a nesting. */
  private var resourceSeq = 0

  /** every `try` whose RESOURCES this emitter lowered — input to `try-resource`, which finds the
    * resource-carrying trys independently and reports the ones nothing closed. Keyed by
    * [[Tree.Try.id]]: an Origin is not unique across trys, nor is object identity after a rebuild. */
  private val resourceLowered = collection.mutable.Set.empty[TryId]
  def resourceLowerings: Tree.Try => Boolean = t => resourceLowered.contains(t.id)
  def resourceLoweringCount: Int = resourceLowered.size

  /** does a jump in this try BODY leave the try — i.e. is its `boundary` outside it? Read off the
    * emitter's own boundary state: a jump rendering as `boundary.break` has its target in scope
    * HERE, opened by a construct enclosing this try. A label bound INSIDE the body is not in these
    * maps yet, so the labelled lanes need no extra test to exclude it. */
  private def crossesCatch(body: Term): Boolean =
    (breakTarget.isDefined && Jumps.breaksOut(body)) ||
      (contTarget.isDefined && Jumps.continuesIn(body)) ||
      labelBreak.keysIterator.exists(l => Jumps.jumpsTo(body, l, brk = true)) ||
      labelCont.keysIterator.exists(l => Jumps.jumpsTo(body, l, brk = false))

  /** every `try` this emitter put a [[TirEmitter.BreakGuard]] arm on — input to `break-catch`,
    * which finds the crossings independently and reports the ones nothing guarded. Keyed by the
    * try's own TOKEN, never by Origin (two trys can share path/line/column, e.g. every
    * phase-synthesised one) and never by object identity (StandardTraversal rebuilds every node;
    * Tree.Try.id survives a rebuild because copy carries it). */
  private val breakGuarded = collection.mutable.Set.empty[TryId]
  def breakGuards: Tree.Try => Boolean = t => breakGuarded.contains(t.id)
  /** how many trys that is. */
  def breakGuardCount: Int = breakGuarded.size

  /** A java `switch`, with a boundary around any case body that still contains an unlabelled
    * `break` (the frontend strips only the CASE-TERMINATING one, so one reaching here is mid-case
    * fallthrough) — a scala `match` arm cannot be left early. Also emits a `case null => throw`
    * arm ahead of the java arms for a REFERENCE-typed selector java's own switch NPEs on
    * implicitly (JLS 14.11.2), unless the switch already declares its own `case null`
    * (SE21's pattern-switch escape hatch). The boundary is ALWAYS named. */
  private def matchStr(m: Tree.Match, i: Int)(using Obligations): String =
    val (scr, cases) = (m.scrutinee, m.cases)
    // JS-S06 — an unlabelled break in the MIDDLE of a case ends the CASE, and a match arm cannot
    // be left early. Fires where an arm really needs the boundary.
    Obligations.consult(JS.S(6), m.origin)(Option.when(cases.exists(c => caseNeedsBoundary(c.body)))(()))
    // JS-S08 — java throws NPE on a null reference selector IMPLICITLY (JLS 14.11.2); read off
    // selectorCanBeNull, the emitter's own decision (§4.56).
    Obligations.consult(JS.S(8), m.origin)(Option.when(selectorCanBeNull(scr, cases))(()))
    val cs = cases.map { c =>
      val bare = if c.isDefault then "_" else c.labels.map(term(_, i)).mkString(" | ")
      val pat = bare + c.guard.fold("")(g => s" if ${term(g, i)}")
      // a switch EXPRESSION's arm with a non-tail yield gets a VALUE-carrying boundary, mutually
      // exclusive with a mid-case break by java's own rules (JLS 15.28/14.21) — the Label's type
      // (Unit vs the expression's own) is what makes them two arms. caseYieldsOut descends through
      // a nested switch STATEMENT, whose own yield belongs to an arm further out.
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
    * Both needed: the selector's type is a REFERENCE type (a primitive cannot be null; decided
    * from the emitted type's head against scala's value classes), and no case label is already
    * `null` (SE21's pattern switch may deliberately handle it, JLS 14.11.1). */
  private def selectorCanBeNull(scr: Term, cases: List[Tree.CaseDef]): Boolean =
    val isValueClass = headSymOf(scr.tpe).map(s => sym(s).fullName).exists(TirEmitter.ScalaValueClasses.contains)
    val writesNull = cases.exists(_.labels.exists {
      case Tree.Literal(Constant.NullC, _, _) => true
      case _                                  => false
    })
    !isValueClass && !writesNull

  /** every switch this emitter gave a `case null` arm — input to `switch-null`. Keyed by
    * [[Tree.Match.id]], the reason `breakGuarded` is keyed by `Tree.Try.id`. */
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

  /** THE TYPE DISPATCH — the emitter half of the catalog's FOURTH obligation surface. At the
    * dispatch and never in an arm ([[stat]]'s reason): a TypeRepr is not a Tree, so [[Rendering]]
    * could never enter one. The subject is the TypeRepr; the origin is the enclosing scope's
    * (`CatalogLog.currentOrigin`), since a type has no position of its own. */
  private def tpe(t: TypeRepr): String =
    Typing.ofRepr(TirKinds.ofType(t), t)(tpeArm(t))

  /** JS-C29 and JS-G12 — the two questions a NAME is asked at the type dispatch's TypeRef arm,
    * read off the SYMBOL rather than by re-running typeSym's cascade. */
  private def typeRefConsults(s: SymId)(using Obligations): Unit =
    val full    = sym(s).fullName
    val marker  = Symbol.isUnresolvedTypeVar(full)
    // JS-C29 — a java NESTED type is one of two different scala types, only one path-dependent.
    // Every $ in a full name is that question; a marker is excluded (it is the other row's).
    Obligations.consult(JS.C(29), catalog.currentOrigin)(Option.when(!marker && full.contains('$'))(()))
    // JS-G12 — the emitter's half: an unresolved type variable is a MARKER, so ? is emitted
    // (ENGINE-LIMITS G2 — one occurrence took out the statement around it).
    Obligations.consult(JS.G(12), catalog.currentOrigin)(Option.when(marker)(()))

  /** JS-G01's EMITTER half — the bound GRAMMAR, stated once and called from BOTH TypeBounds arms
    * (the bare-wildcard fast path would otherwise be a hole at every plain `?`, ENGINE-LIMITS F8). */
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
    // a BOUND that is an unresolved type variable says nothing, and saying it is worse than
    // silence, so it is dropped, leaving a bare ? (G2).
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

  /** A PREFIX operator and its operand, with the two kept as two tokens. Scala's lexer takes a
    * maximal run of operator characters as ONE identifier, so a prefix `-` against an operand
    * already rendering with a leading `-` produces `--`, a different token (e.g. java negating a
    * literal whose value is already negative — measured 48 errors in one method). Parenthesising
    * the operand is the only fix that cannot mis-lex; a separating space would misparse as infix. */
  private def prefixOp(op: String, rendered: String): String =
    if op.nonEmpty && rendered.nonEmpty && isOpChar(op.last) && isOpChar(rendered.head)
    then s"$op($rendered)"
    else s"$op$rendered"

  /** the ASCII half of Scala's `opchar` (SLS 1.1); the Unicode `Sm`/`So` half cannot begin any
    * rendering this emitter produces. */
  private def isOpChar(c: Char): Boolean = "!#%&*+-/:<=>?@\\^|~".indexOf(c.toInt) >= 0

  /** Render a string or char literal's VALUE as Scala source that denotes the same value. Every
    * character needs escaping that would otherwise: end the literal (raw `\n`), be an "illegal
    * character" (a raw control char), or silently change on UTF-8 write-out (a lone surrogate).
    * Everything else, including ordinary non-ASCII text, is emitted verbatim. `\uXXXX` is a scala 3
    * escape sequence expanded inside the literal only, so an emitted `\\u` cannot leak into one. */
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

/** THE `Tree` KIND, as the rendering dispatch names it. `Tree` is sealed and every case is a case
  * class, so the name is the compiler's own `productPrefix` — a function, not a table, since a new
  * node kind needs no listing to be covered. */
private object TirKinds:
  def of(t: Tree): String = t match
    case p: Product => p.productPrefix
    // unreachable: every concrete Tree is a case class.
    case _          => "?"

  /** the TYPE algebra's, for the fourth obligation surface — same derivation, same reason. */
  def ofType(t: TypeRepr): String = t match
    case p: Product => p.productPrefix
    case _          => "?"

object TirEmitter:

  /** the binder of the re-throw arm that keeps a translated jump out of a java handler (§4.4).
    * `$`-suffixed like this emitter's other minted names, spelled ONCE so the spec and the emitter
    * cannot drift. */
  val BreakGuard = "brkThru$"

  /** the emitted types a JAVA PRIMITIVE renders as — "this value cannot be null". Spelled ONCE and
    * read by both the emitter and `SwitchNullCheck`, in EMITTED names (a java int arrives here as
    * scala.Int after retyping). */
  val ScalaValueClasses: Set[String] = Set(
    "scala.Boolean", "scala.Byte", "scala.Short", "scala.Char",
    "scala.Int", "scala.Long", "scala.Float", "scala.Double", "scala.Unit")

  /** the WRAPPER whose static `hashCode` javac uses for a primitive record component (JS-C43),
    * keyed on [[ScalaValueClasses]]'s own spellings (minus `scala.Unit`, which no component has). */
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

  /** THE SAME RULE, APPLIED TO EVERY SEGMENT OF A QUALIFIED NAME. `esc` answers for an
    * IDENTIFIER; a `Symbol.fullName` is a PATH, and a java package segment java was free to name
    * `type`/`object`/`val`/`package` emits an unparseable reference reaching the output verbatim.
    * Cut only at §4.56's three separators (`.`, `$`, `#`), carried across verbatim, so this is
    * safe on an external FQN the port does not own. */
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

  /** A class whose constructors carry a CONTEXT CLAUSE the emitted header does not (ENGINE-LIMITS
    * CT5). A value the emitter records and the run reports; the emitter names no check or phase,
    * since the fact is about EMISSION.
    * @param form what WAS emitted for this type: class, trait, object, enum. */
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

  /** THE BASE-SURFACE CONTRACT, as one emitter recorded it (DESIGN.md §8.3).
    * @param types         emitted FQN → what was emitted at that name
    * @param members       emitted member key (the source map's spelling) → the same, per member */
  final case class Shapes(
      types: Map[String, Surface.TypeShape],
      members: Map[String, Surface.MemberShape],
  ):
    /** rendered, the form the port map's `shape` column takes. */
    def renderedTypes: Map[String, String]   = types.view.mapValues(Surface.render).toMap
    def renderedMembers: Map[String, String] =
      members.view.mapValues(Surface.render).toMap.filter(_._2.nonEmpty)

  object Shapes:
    val empty: Shapes = Shapes(Map.empty, Map.empty)

  /** RECORD one of this file's decisions. Every decider here is [[Reason.Universal]] — a §4.55/
    * §4.56 fact about the two languages, never anybody's policy, so none takes a parameter. */
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
    * fields; when REPLAYED one level down (`CtorFunnel.replayFor`) they execute in the subclass,
    * where `private` no longer reaches. Widening can only remove access errors, never behaviour.
    * `forDependents` is the same widening asked by a subclass THIS RUN CANNOT SEE
    * (`CtorFunnel.externalReplayWidenings`, ENGINE-LIMITS C15) — kept separate only for the note's
    * sake, so a reader is not told "replayed in this subclass" about a class with none here. */
  def widen(p: Program, members: Set[SymId],
            out: collection.mutable.Buffer[Decision] = collection.mutable.ListBuffer.empty,
            forDependents: Set[SymId] = Set.empty): Program =
    val all = members ++ forDependents
    if all.isEmpty then p
    else
      val src = p
      // ALL THREE of java's non-public levels, not isPrivate alone — clearing one flag is silently
      // a no-op for the other two (ENGINE-LIMITS C15's second face).
      def level(f: Flags): String =
        if f.isPrivate then "private" else if f.isProtected then "protected" else "package-private"
      def widened(f: Flags): Flags =
        f.copy(isPrivate = false, isProtected = false, isPackagePrivate = false)
      def narrower(f: Flags): Boolean = f.isPrivate || f.isProtected || f.isPackagePrivate
      val syms = p.symbols.all.map(s => if all(s.id) then s.copy(flags = widened(s.flags)) else s)
      // one row per member that ACTUALLY LOST a modifier — a member already public was in
      // widenedMembers because the planner could not know.
      p.symbols.all.foreach { s =>
        if all(s.id) && narrower(s.flags) then
          // an OBSERVED replay wins the sentence where a member is in both sets — the stronger claim.
          val observed = members(s.id)
          note(out, Decision.Kind.WidenedVisibility, src, s.id,
            Map(
              // the same cause= pair every §8.7 residue carries, so this is one grep over decisions.tsv.
              "cause" -> "ctor-replay-widening",
              "from" -> level(s.flags),
              "to"   -> "public",
              "scope" -> (if observed then "this-run" else "dependent-modules"),
              "why"  ->
                (if observed then
                   "a parent constructor's statements are REPLAYED in this subclass " +
                     "(CtorFunnel.replayFor), and java let them touch a private member that scala's " +
                     "replay cannot reach one level down; widening can only remove access errors"
                 else
                   "a paramful constructor of this class writes this member, and a SUBCLASS IN " +
                     "ANOTHER MODULE can only express its `super(args)` as a replay one level down " +
                     "(scala lets only the primary reach super) — where `private` does not reach " +
                     "and where nothing but this run can widen it (ENGINE-LIMITS.md C15); widening " +
                     "can only remove access errors"),
            ),
            "ctor-replay-widening")
      }
      p.rebuilt(symbols = SymbolTable(syms))

  /** Promoting a constructor to Scala's primary widens the SCOPE of everything it declares: its
    * params and top-level locals become class members visible to the whole body, which risks a
    * double definition against an own member or a silent capture of an inherited one. Suffixing
    * `$p` removes both — the rename is invisible everywhere it matters. */
  def funnelParamRenames(p: Program, out: collection.mutable.Buffer[Decision] = collection.mutable.ListBuffer.empty,
                         surface: Surface = null): Program =
    val renames = collection.mutable.Map[SymId, String]()
    // THE RUN'S OWN VIEW, not a TrivialSurface — a dependent's fixpoint spans its base, so the
    // plan must read the base's actually-emitted promoted parameters (ENGINE-LIMITS D4).
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
    // EFFECTIVE names: a parent's already-renamed promoted param must read as TAKEN here, or the
    // collision simply moves up a level (measured on DepthShader extends DefaultShader). Requires
    // the parents-first scan below.
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
      // AN ENUM PROMOTES ITS CONSTRUCTOR PARAMETERS TOO, by a different route: enumDef renders
      // each as a var field without consulting CtorFunnel (ENGINE-LIMITS T11's remaining half —
      // the collidee here is a DECLARED member, e.g. Flavor's isLiquidStyleInclude parameter
      // against its own isLiquidStyleInclude()). NARROW, unlike the plan-based arm below: an enum
      // parameter is EMITTED SURFACE (a public var), so only a real collision renames one. Two
      // names are NOT collidees: the parameter's own name, and a body field it SUPERSEDES
      // (enumDef drops that ValDef, so it never clashes and renaming would un-supersede it).
      // "supersedes" is a (name, TYPE) question asked of CtorFunnel so the two cannot disagree.
      val enumParams =
        if !p.symbolOf(cd.symbol).exists(_.flags.isEnum) then Nil
        else CtorFunnel.enumPrimaryCtor(p, cd).map(CtorFunnel.valueParams(p, _)).getOrElse(Nil)
      if enumParams.nonEmpty then
        val superseded = CtorFunnel.enumSupersededFields(p, cd)
        // built from the PARTS rather than subtracted from visibleNames: a name may be BOTH a
        // superseded field and a declared method, and subtracting would take the collidee with it.
        val takenE = collection.mutable.Set.from(
          cd.body.collect {
            case d: Tree.DefDef if nm(d.symbol) != "<init>" => eff(d.symbol)
            case c: Tree.ClassDef                           => eff(c.symbol)
            case v: Tree.ValDef if !superseded(v.symbol)    => eff(v.symbol)
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

  /** Rename any field that SHADOWS an inherited member. Java fields shadow rather than override
    * and resolve by the STATIC type of the receiver, so `ParallelArray.Channel`'s `Object data`
    * and `FloatChannel`'s `float[] data` are different storage; scala has no such thing, so the
    * shadowing field gets a fresh name. Exact because java resolved these statically: every TIR
    * reference already points at the symbol java chose, so renaming that symbol re-points exactly
    * what java meant. A field shadowing an inherited METHOD is the same defect through java's
    * separate namespaces, renamed here too; statics are exempt (they land in the companion, which
    * inherits nothing). The inherited member may be one this program never parsed (`finalize` on
    * java.lang.Object, above every type whether or not a parent list says so) — narrowed to
    * `isKnown(fqn) && mayDeclare(fqn, sig)`, since `mayDeclare`'s UNKNOWN-is-YES default is right
    * for its other readers (*may I rename*, over-refusal free) and wrong here (*must I rename*
    * would move a field on every unparsed-parent class for no evidence — ENGINE-LIMITS K28.2). */
  def resolveFieldShadowing(p: Program, out: collection.mutable.Buffer[Decision] = collection.mutable.ListBuffer.empty,
                            surface: Surface = null,
                            /** JS-C04's citation surface: a whole-program pass CITES the row once per
                              * declaration decided about (Decision's own granularity), discarding by
                              * default so the determinism twin does not double the count. */
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
    /** the same question asked of ancestors this program did NOT parse — OverrideGraph is where
      * that walk lives (§4.56), so it is not re-derived here. LAZY: java.lang.Object's own members
      * are answered without building it. */
    lazy val graph = OverrideGraph.build(p)
    /** does an UNPARSED ancestor declare a member this field's name would collide with? A field is
      * arity 0, so the far side can only be a PARAMETERLESS method. javaLangObjectDeclares is asked
      * first: that type is above every java type whether or not the parent list shows it. */
    def externallyShadowed(cd: Tree.ClassDef, name: String): Boolean =
      val sig = OverrideGraph.Signature(name, Some(Descriptor.empty), 0, approximate = false)
      ExternalSurface.javaLangObjectDeclares(sig) ||
        graph.externalAncestorsOf(cd.symbol)
          .exists(fqn => ExternalSurface.default.isKnown(fqn) && ExternalSurface.default.mayDeclare(fqn, sig))
    /** the inherited DECLARATIONS behind those names — a name alone cannot answer
      * [[implementsInherited]] (§4.56). */
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
      // A field the BASE emits: its published name settles this. Renamed hands this run the base's
      // own name; Kept really is "nothing to do" here, since shadowing is decided against
      // ANCESTORS and a base class cannot extend a dependent's — unlike resolveMemberClashes,
      // whose clash is decided against DESCENDANTS a dependent has and the base did not.
      def settledByBase(v: Tree.ValDef): Boolean =
        TirEmitter.baseName(p, view, v.symbol, "shadows-inherited") match
          case TirEmitter.BaseName.Derive      => false
          case TirEmitter.BaseName.Renamed(to) => renames(v.symbol) = to; true
          case TirEmitter.BaseName.Kept        => true
      /** A scala `val`/`var` and a scala PARAMETERLESS `def` of the same name, one inherited from
        * the other's type, are an IMPLEMENTATION pair and not a shadowing one — moving the field
        * would break that contract silently until the port reaches 0 typer errors (§3). EXACT
        * because java cannot produce a parameterless method, so an emitted `paramss == Nil` is
        * always a property conversion's accessor over the field it stands for. `exists`, not
        * `forall`, because the same name can reach a class from TWO directions (a shadowing field
        * AND a collapsed implementation obligation); `forall` would silently stop implementing the
        * member (ENGINE-LIMITS K5.7's trade: an unimplemented member is invisible until 0 typer
        * errors, a shadowing var is a typer error immediately, so the implementation wins). */
      lazy val inheritedDecls = inheritedSyms(cd)
      def implementsInherited(v: Tree.ValDef): Boolean =
        val n    = nm(v.symbol)
        val same = inheritedDecls.filter(s => eff(s) == n)
        // ABSTRACT only: a concrete parameterless def is a def-pair getter over its OWN owner's
        // field, unrelated to a descendant's plain shadow (2 RefChecks errors measured otherwise).
        same.exists(s => p.definitionOf(s) match
          case Some(d: Tree.DefDef) => d.paramss.isEmpty && d.rhs.isEmpty
          case _                    => false)

      cd.body.foreach {
        case v: Tree.ValDef if (shadowed(nm(v.symbol)) || externallyShadowed(cd, nm(v.symbol))) &&
                               !p.symbolOf(v.symbol).exists(_.flags.isStatic) &&
                               !implementsInherited(v) && !settledByBase(v) =>
          // The fresh name must not ITSELF be inherited (CheckBox.style shadows TextButton.style
          // shadows Button.style) — keep appending until free, checking the external half too.
          var fresh = nm(v.symbol) + "$shadow"
          while shadowed(fresh) || externallyShadowed(cd, fresh) do fresh += "$"
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
    // driven over EVERY declared class, method-local ones included (JS-C30); `scanned` and the
    // parents-first recursion above keep §4.55's ordering, not the walk order.
    p.units.foreach(u => StandardTraversal.allClassDefs(u)(using p).foreach(scan))
    // JS-C04 — a subclass field SHADOWS a superclass field: two storage cells in java, ONE member
    // in scala. Cited per renamed declaration only, never for a class with nothing to rename.
    renames.keys.foreach(id =>
      catalog.cite(JS.C(4), p.symbolOf(id).map(_.fullName).getOrElse(id.toString)))
    if renames.isEmpty then p
    else
      // same visibility relaxation as resolveMemberClashes: a renamed field must stay reachable
      // from wherever java read it.
      recordClashWidening(p, out, renames.keys, "shadows-inherited")
      val syms = p.symbols.all.map(s =>
        renames.get(s.id).map(n => s.copy(name = n, flags = s.flags.copy(isPrivate = false, isProtected = false))).getOrElse(s)
      )
      p.rebuilt(symbols = SymbolTable(syms))

  /** WHAT THE BASE SAYS ABOUT A FIELD THIS RUN WOULD RENAME — three answers (see [[baseName]]). */
  private[emit] enum BaseName:
    /** this run EMITS the field, or no base publishes a row for it: the local derivation stands,
      * recorded as a gap first in the second case. */
    case Derive
    /** the base RENAMED it, and this is the name it emitted. Nothing is re-derived. */
    case Renamed(to: String)
    /** the base published a row and KEPT java's name. This run may not move the field — the
      * clash it saw is ITS OWN, made by declarations only this run has. */
    case Kept

  /** A field this run does NOT emit: does the BASE's published name settle it? A dependent's
    * Program CONTAINS its base with EXTRA descendants the base's own run never saw, so a dependent
    * subclass declaring `def x()` could rename the base's field independently, spelling a name the
    * base never wrote and producing a module that cannot compile against what it resolves against
    * (§1.5, ENGINE-LIMITS D4's shape at the renaming passes). The base's answer is FOLLOWED, not
    * merely respected. `Kept` is NOT "nothing to do": it settles only the base's HALF of the clash
    * — the other half is this module's own declaration, and `Kept` hands the caller that fact so
    * it can move the half it owns (base `p.Base{int x}`, dependent `q.Heir extends p.Base{int
    * x()}` would otherwise emit an uncompilable erased-signature clash with zero findings). */
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

  /** THE OTHER HALF OF A §4.55 FIELD RENAME: the member also ships WIDER than java wrote it. Both
    * clash passes strip private/protected unconditionally so a renamed field stays reachable from
    * wherever java read it, but the rename was recorded and the widening was not — a public member
    * carrying a RenamedMember row said nothing about visibility. One row per member that ACTUALLY
    * LOST a modifier (the discipline [[widen]] already keeps); `clash` matches the RenamedMember
    * row beside it, so both questions are one grep. */
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

  /** Rename an enclosing method's LOCAL or PARAMETER that a nested class's member shadows — the
    * fourth face of §4.55, running the other way: here the CAPTURE moves, not the member. Java's
    * two namespaces (methods vs variables) let a parameter and a nested class's same-named method
    * coexist; scala has one namespace and resolves innermost-first, so the member wins both, and
    * scala can qualify an outer MEMBER but cannot name a shadowed LOCAL at all — so the capture is
    * renamed (exact per §4.55, since java resolved it statically). TWO RULES, because scala has
    * two failure shapes and java has one: UNNAMEABLE (the body references the capture, and the
    * class declares or inherits the name — the member simply wins) and AMBIGUOUS (the body
    * references an INHERITED member and an enclosing scope defines that name too — scala 3's
    * `E049`, which java has no rule for at all: an inherited field always shadows an enclosing
    * local/parameter, probed against javac; ENGINE-LIMITS C16). Same remedy in both — move the
    * outer declaration — which in the second case also PRESERVES java's binding. Renames only
    * where really shadowed (local referenced inside the nested body AND the body declares/inherits
    * the name); the second rule ranges over the whole enclosing scope instead, since it has no
    * reference to read the capture off, over-approximating only onto an out-of-scope local (safe).
    * Does not reach an outer FIELD OF AN ENCLOSING CLASS (a different pass's business — a
    * qualification, not a rename) or a member inherited from an unparsed (JDK) supertype. */
  def resolveCapturedLocalClashes(p: Program, out: collection.mutable.Buffer[Decision] = collection.mutable.ListBuffer.empty): Program =
    given Program = p
    def nm(id: SymId): String = p.symbolOf(id).map(_.name).getOrElse("")
    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s) => Some(s); case TypeRepr.AppliedType(tc, _) => headSym(tc); case _ => None

    /** every nested BODY, its own type symbol and its parents. A StandardTraversal, not a walk
      * over cd.body: an anonymous class's body lives inside a TERM. */
    val bodies  = collection.mutable.ListBuffer[(SymId, List[Statement], List[SymId])]()
    val methods = collection.mutable.Set[SymId]()
    val declOf  = collection.mutable.Map[SymId, Tree.ClassDef]()
    /** body symbol -> the METHODS it is lexically inside. Each method re-walks its OWN body once
      * and claims what it finds (the traversal is post-order, so a push/pop stack cannot see it). */
    val enclosedBy = collection.mutable.Map[SymId, Set[SymId]]()
    /** Discover the enclosing method scope for anonymous classes inside a term that is NEITHER a
      * DefDef rhs NOR a Lambda body — e.g. TestFrameworkTransform's inlined `test("…")({ block })`,
      * whose locals keep the ORIGINAL method symbol as owner though no transformDefDef fires for
      * it. Walks the term, maps any anonymous classes found to the unknown owner (C16.1). */
    def discoverScope(t: Term)(using p0: Program): Unit =
      val localOwners = collection.mutable.Set[SymId]()
      val innerBodies = collection.mutable.Set[SymId]()
      val ownerScan = new Phase:
        def name: String = "tir-emitter/scope-scan"
        override def transformValDef(v: Tree.ValDef)(using p: Program): Tree.ValDef =
          val ow = p.symbolOf(v.symbol).map(_.owner).getOrElse(SymId.None)
          if ow != SymId.None && !declOf.contains(ow) && !methods.contains(ow) then localOwners += ow
          v
        override def transformClassDef(c: Tree.ClassDef)(using Program): Tree.ClassDef = { innerBodies += c.symbol; c }
        override def transformNew(n: Tree.New)(using Program): Term = { n.anon.foreach(a => innerBodies += a.symbol); n }
      StandardTraversal.mapTerm(ownerScan, t)
      if localOwners.nonEmpty && innerBodies.nonEmpty then
        localOwners.foreach(methods += _)
        innerBodies.foreach(b => localOwners.foreach(m =>
          enclosedBy(b) = enclosedBy.getOrElse(b, Set.empty) + m))
    val collector = new Phase:
      def name: String = "tir-emitter/captured-local-scan"
      override def transformClassDef(t: Tree.ClassDef)(using Program): Tree.ClassDef =
        declOf(t.symbol) = t
        bodies += ((t.symbol, t.body,
                    t.parents.flatMap { case tt: TypeTree => headSym(tt.tpe); case tm: Term => headSym(tm.tpe) }))
        t
      override def transformNew(t: Tree.New)(using Program): Term =
        t.anon.foreach(a => bodies += ((a.symbol, a.body, headSym(t.tpt.tpe).toList)))
        t
      override def transformDefDef(t: Tree.DefDef)(using Program): Tree.DefDef =
        methods += t.symbol
        val inner = collection.mutable.Set[SymId]()
        val scan = new Phase:
          def name: String = "tir-emitter/captured-local-enclosing"
          override def transformClassDef(c: Tree.ClassDef)(using Program): Tree.ClassDef = { inner += c.symbol; c }
          override def transformNew(n: Tree.New)(using Program): Term = { n.anon.foreach(a => inner += a.symbol); n }
        t.rhs.foreach(r => StandardTraversal.mapTerm(scan, r))
        inner.foreach(b => enclosedBy(b) = enclosedBy.getOrElse(b, Set.empty) + t.symbol)
        t
      override def transformLambda(t: Tree.Lambda)(using p0: Program): Term =
        discoverScope(t.body)
        t.params.foreach { v =>
          val ow = p.symbolOf(v.symbol).map(_.owner).getOrElse(SymId.None)
          if ow != SymId.None && !declOf.contains(ow) && !methods.contains(ow) then methods += ow
        }
        t
    p.units.foreach(StandardTraversal.mapClassDef(collector, _))

    /** POST-PASS: bodies with no enclosedBy entry — anonymous classes inside a scope that is
      * neither DefDef nor Lambda. Deliberately a post-pass rather than a transformBlock hook,
      * which would re-walk every block in the program instead of only the ones that need it. */
    val unenclosed = bodies.collect { case (sym, _, _) if !enclosedBy.contains(sym) && !declOf.contains(sym) => sym }.toSet
    if unenclosed.nonEmpty then
      for u <- p.units do
        // walk every class body in the program, not just top-level units — the test class is nested
        StandardTraversal.allClassDefs(u)(using p).foreach { cd =>
          for stat <- cd.body do
            stat match
              case t: Term => discoverScope(t)(using p)
              case _ =>
        }

    /** the parameters and locals each method owns — the enclosing SCOPE a nested body sits in.
      * Ownership is the structural fact (§4.56): the frontend interns a field under the CLASS and a
      * local or parameter under the enclosing EXECUTABLE, so this partition is a symbol lookup and
      * never a name or an origin test. */
    val scopeOf: Map[SymId, List[Symbol]] =
      p.symbols.all.filter(s => methods(s.owner)).groupBy(_.owner).view.mapValues(_.toList).toMap

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
    /** the member SYMBOLS a body INHERITS — symbols, not names, because the ambiguity question
      * needs to ask whether the body referenced the INHERITED member (a self-declared member is
      * never ambiguous). Falls back to the SYMBOL TABLE for a RESOLUTION-ROOT parent with no
      * indexed ClassDef — a level-1 read, unable to walk further ancestors, but covering every
      * rename a surface phase made on the parent's own declarations. */
    def visibleMembers(parents: List[SymId], seen: Set[SymId]): Set[SymId] =
      parents.filterNot(seen).flatMap { pid =>
        declOf.get(pid) match
          case Some(cd) =>
            cd.body.collect {
              case d: Tree.DefDef if nm(d.symbol) != "<init>" => d.symbol
              case v: Tree.ValDef                             => v.symbol
              case c: Tree.ClassDef                           => c.symbol
            } ++ visibleMembers(
              cd.parents.flatMap { case tt: TypeTree => headSym(tt.tpe); case tm: Term => headSym(tm.tpe) },
              seen + cd.symbol)
          case None =>
            p.symbols.all.collect {
              case s if s.owner == pid && s.name != "<init>" => s.id
            }
      }.toSet

    val renames = collection.mutable.Map[SymId, String]()
    bodies.foreach { (bodySym, stats, parents) =>
      val declared  = memberNames(stats)
      val inherited = visibleMembers(parents, Set.empty)
      val shadowing = declared ++ inherited.map(nm)
      if shadowing.nonEmpty then
        val (defs, refs) = survey(stats)

        /** rename `s` out of the way, and say under which of the two rules. */
        def move(s: SymId, clash: String, why: String): Unit =
          val n = renames.getOrElse(s, nm(s))
          var fresh = n + "$local"
          while shadowing(fresh) do fresh += "$"
          renames(s) = fresh
          val owner = p.symbolOf(s).map(_.owner).getOrElse(SymId.None)
          note(out, Decision.Kind.RenamedMember, p, owner,
            Map("from" -> n, "to" -> fresh, "clash" -> clash, "why" -> why),
            MemberRenameRule)

        // (1) UNNAMEABLE — a capture referenced here, declared elsewhere, and OWNED BY A METHOD
        // (a local or parameter, not a field — java and scala shadow a field the same way).
        (refs -- defs).toList.sortBy(_.raw).foreach { s =>
          if shadowing(renames.getOrElse(s, nm(s))) && p.symbolOf(s).exists(sy => methods(sy.owner)) then
            move(s, "captured-local-vs-nested-member",
              "java keeps methods and variables in SEPARATE namespaces, so a class " +
              "nested in this method may declare a member with the same name as one of its " +
              "locals and both stay reachable; scala has one namespace and the member wins, " +
              "leaving the capture unnameable")
        }

        // (2) AMBIGUOUS — the body names an INHERITED member and an enclosing method's scope
        // defines that name too. Java bound the inherited member; scala 3 refuses to choose
        // (E049), so the OUTER definition moves, leaving the bare name binding what java bound.
        val ambiguous = refs.intersect(inherited).map(nm).filterNot(declared)
        if ambiguous.nonEmpty then
          enclosedBy.getOrElse(bodySym, Set.empty).toList.sortBy(_.raw)
            .flatMap(m => scopeOf.getOrElse(m, Nil)).sortBy(_.id.raw).foreach { s =>
              if ambiguous(renames.getOrElse(s.id, s.name)) && !defs(s.id) then
                move(s.id, "captured-local-vs-inherited-member",
                  "this name is declared in this method AND inherited by a class nested inside " +
                  "it; java resolves that to the INHERITED member, and scala 3 resolves it to " +
                  "neither — the reference is ambiguous. Moving the enclosing declaration is what " +
                  "leaves the bare name binding what java bound")
            }
    }
    if renames.isEmpty then p
    else p.rebuilt(symbols = SymbolTable(p.symbols.all.map(s => renames.get(s.id).map(n => s.copy(name = n)).getOrElse(s))))

  /** Rename any field whose simple name collides with a method in the same EMITTED SCOPE (legal in
    * java, illegal in scala) by suffixing `$field`. "Same emitted scope" is the whole rule, and it
    * is PLACEMENT, not name: a java `static` member leaves the class for the companion object, so a
    * static factory and an instance field of the same name cannot collide (Ashley's `Family`).
    * The two scopes are asymmetric: the INSTANCE scope is inherited (a field clashes with a method
    * declared in any DESCENDANT), the STATIC scope is not (a companion inherits nothing). A
    * `module` symbol has one body for both, so the partition collapses there. */
  def resolveMemberClashes(p: Program, out: collection.mutable.Buffer[Decision] = collection.mutable.ListBuffer.empty,
                           surface: Surface = null,
                           /** JS-C46's citation surface — see [[resolveFieldShadowing]]'s. */
                           catalog: CatalogLog = CatalogLog.discarding): Program =
    val view    = if surface eq null then TrivialSurface(p) else surface
    val renames = collection.mutable.Map[SymId, String]()
    /** METHOD renames, kept apart from [[renames]]: the field map also drives the visibility strip
      * below (a renamed field needs it), and widening a renamed method's visibility would move
      * emitted surface for no cause. */
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

    // LAZY: building it walks the whole program, and only the 0-corpus-site branch (a base field
    // this run may not move) needs it.
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
        // TO A NAME THAT IS FREE, the idiom both sibling passes use — held against both the method
        // names that decided the clash and the sibling fields' EFFECTIVE names (§4.55).
        val taken = clashNames(v) ++ cd.body.collect { case w: Tree.ValDef if w.symbol != v.symbol => eff(w.symbol) }
        var fresh = nm(v.symbol) + "$field"
        while taken(fresh) do fresh += "$"
        renames(v.symbol) = fresh
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

      /** the answer when the FIELD IS THE BASE'S AND THE BASE KEPT JAVA'S NAME: move the half of
        * the clash this module owns. The clashing methods are necessarily this run's own
        * declarations (a `Kept` answer means the base's own run saw no such descendant). The
        * rename still must be SOUND: a method implementing an interface or overriding a parent
        * this module does not own cannot move, and that closure is refused and RECORDED
        * (DESIGN.md §8.3). */
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
                // FREE against everything the component can see, read through effective names so
                // two renames in one hierarchy cannot land on each other (§4.55).
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
    // driven over EVERY declared class, method-local ones included (JS-C30).
    p.units.foreach(u => StandardTraversal.allClassDefs(u)(using p).foreach(scan))
    // JS-C46 — java has TWO name namespaces and scala has one. Cited per declaration this pass
    // moved, in either direction.
    (renames.keys ++ methodRenames.keys).foreach(id =>
      catalog.cite(JS.C(46), p.symbolOf(id).map(_.fullName).getOrElse(id.toString)))
    if renames.isEmpty && methodRenames.isEmpty then p
    else
      // also relax visibility: java lets an enclosing class read a nested class's private field,
      // scala does not, so a renamed clash-field must stay accessible. FIELDS only.
      recordClashWidening(p, out, renames.keys, "field-vs-method")
      val syms = p.symbols.all.map { s =>
        renames.get(s.id).map(n => s.copy(name = n, flags = s.flags.copy(isPrivate = false, isProtected = false)))
          // `name`, never `fullName`: the join key four artifacts share is owner#<java name>.
          .orElse(methodRenames.get(s.id).map(n => s.copy(name = n)))
          .getOrElse(s)
      }
      p.rebuilt(symbols = SymbolTable(syms))
