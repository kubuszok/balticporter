package balticporter.emit

import balticporter.catalog.{CatalogLog, JS, Obligations, Rendering, Typing}
import balticporter.core.{EngineInfo, Provenance, Substituted}
import balticporter.tir.*

/** Emission backend: transformed TIR to Scala 3 source.
  * @param externalConcrete concrete instance members of injected supertypes, keyed `(FQN, param
  *   counts)` — required for [[diamondOverrides]] to see injected parents
  * @param provenance upstream attribution header stamped on every emitted unit; `None` = no header
  * @param notes the run's decision log (read-only) — decisions render as porter notes beside code. */
final class TirEmitter(
    private[emit] val source: Program,
    private[emit] val externalConcrete: Map[String, Set[(String, List[Int])]] = Map.empty,
    private[emit] val provenance: Option[Provenance] = scala.None,
    private[emit] val notes: DecisionLog = new DecisionLog,
    /** Diagnostic mode: render counted refusals as `compiletime.error` instead of residue comments. */
    private[emit] val preview: Boolean = false,
    /** Best-effort emission (DESIGN.md §6.4): open markers render as inner term in comment fences
      * instead of `compiletime.error`. Byte-identical to normal mode at zero open markers. */
    private[emit] val bestEffort: Boolean = false,
    /** View over types this run does not emit (DESIGN.md §8.3). `None` = whole program is surface. */
    private[emit] val surfaceView: Option[Surface] = scala.None,
    /** Upstream Java source text by `Origin.javaPath`, for comment-recovery (DESIGN.md §8.8).
      * Injected so in-memory fixtures can supply text. Default reads the file. */
    private[emit] val javaSource: String => Option[String] = TirEmitter.readJavaSource,
    /** Catalog obligation log. Default `discarding` is correct for secondary emitters
      * (determinism twin, preview, best-effort) to avoid double-counting. */
    private[emit] val catalog: CatalogLog = CatalogLog.discarding,
    /** Member surface of injected Scala files (param types and arity), so emitted overrides
      * adopt the injected signature. Populated by `PortRun`; empty for specs. */
    private[emit] val injectedSurface: InjectedSurface.Surface = InjectedSurface.Empty,
    /** External member FQNs (`Owner#member`) emitted without parens. Calls only, not signatures. */
    private[emit] val externalParenless: Set[String] = Set.empty,
) extends TirEmitterNotes, TirEmitterDecls, TirEmitterMembers, TirEmitterExprs:
  private[emit] given CatalogLog = catalog
  private[emit] val surface: Surface = surfaceView.getOrElse(TrivialSurface(source))
  /** Decisions made during normalisation, exposed to the orchestrator. */
  private[emit] val own = collection.mutable.ListBuffer.empty[Decision]

  // Normalize away Java member-name clashes before rendering. Capture-rename runs LAST
  // so it reads effective names from the three passes above. // CLAUDE.md §4.55
  private[emit] val prepared =
    TirEmitter.resolveCapturedLocalClashes(
      TirEmitter.funnelParamRenames(
        TirEmitter.resolveFieldShadowing(
          TirEmitter.resolveMemberClashes(source, own, surface, catalog), own, surface, catalog), own, surface), own)
  /** Constructor funnel plans: which ctor becomes primary, which super(args) can be replayed. */
  private[emit] val plans = CtorFunnel.Plans(prepared, Some(surface))
  // Widen private members reached by replayed parent-ctor statements (they execute one level down).
  private[emit] val program =
    TirEmitter.widen(prepared, plans.widenedMembers, own, plans.externalReplayWidenings)

  /** Same-named overload candidates for program-declared types. Shared with `overload-risk` check. */
  private[balticporter] lazy val overloads = new balticporter.tir.OverloadRiskCheck.Overloads(program)

  /** Symbols assigned/incremented anywhere in the program. Unwritten `ValDef`s emit as `val`. */
  private[emit] lazy val writtenSyms: Set[SymId] =
    balticporter.transform.BeanCollapse.writtenSymbols(program)
  /** Whether this `ValDef` is written anywhere in the program (by SymId). */
  private[emit] def isWritten(v: Tree.ValDef): Boolean =
    writtenSyms.contains(v.symbol)

  /** Whole-program visibility plan mapping SymIds to access levels. // DESIGN.md §8.7 */
  private[emit] val visPlan: Map[SymId, Visibility.Vis] = Visibility.plan(program, own)

  private[emit] def hasDeprecatedNowarn(s: Symbol): Boolean = s.annotations.exists(_.args.exists(_._2 match {
    case Tree.Literal(Constant.StringC(v), _, _) => v.contains("deprecated"); case _ => false
  }))
  private[emit] def nowarnDeprecated(i: Int): String = s"${ind(i)}@scala.annotation.nowarn(\"msg=deprecated\")\n"

  /** Constructors whose RENDERED statements call `.orNull`, and classes whose PROMOTED body or
    * super arguments do — decided here, before emission, from the same lists `ctorBody` and the
    * class arm render (`ctorRendered`), so the decision carries a porter note. CLAUDE.md §4.4. */
  private[emit] val (orNullCtors, orNullClasses): (Set[SymId], Set[SymId]) = {
    given Program = program
    val ctors   = Set.newBuilder[SymId]
    val classes = Set.newBuilder[SymId]
    def record(s: SymId, what: String, slug: String): Unit =
      TirEmitter.note(own, Decision.Kind.SuppressedWarning, program, s,
        Map("annotation" -> "@nowarn(\"msg=deprecated\")",
            "why" -> s"$what call `.orNull` (the null-preserving unwrap at a slot that accepts null); lls deprecates `orNull` as a lint"),
        slug)
    program.units.foreach { u =>
      StandardTraversal.allClassDefs(u).foreach { cd =>
        val s    = sym(cd.symbol)
        val plan = if s.flags.isModule then CtorFunnel.Plan.none else plans(cd)
        if !hasDeprecatedNowarn(s) && DeprecatedUseScan.count(plan.primaryBody ++ plan.superArgs) > 0 then
          classes += cd.symbol
          record(cd.symbol, "this class's promoted constructor body or super arguments", "ctor-promoted-orNull-suppression")
        CtorFunnel.ctorsOf(program, cd.body).foreach { d =>
          val ds = sym(d.symbol)
          if ds.name == "<init>" && !hasDeprecatedNowarn(ds) && DeprecatedUseScan.count(ctorRendered(cd, d)) > 0 then
            ctors += d.symbol
            record(d.symbol, "this constructor's rendered statements (delegation arguments, replayed parent statements, own body)",
              "ctor-replay-orNull-suppression")
        }
      }
    }
    (ctors.result(), classes.result())
  }

  /** The decisions THIS emitter made — the three §4.55 renaming passes, the replay widening, the
    * replay `@nowarn` suppression. A value, not a recording: the orchestrator records once, from
    * the emitter it keeps, and a determinism twin's identical copy is never read — recording from
    * the constructor would double every row on any run building two emitters (the default). */
  val ownDecisions: List[Decision] = own.toList

  def emit: String = program.units.map(emitUnit).mkString("\n\n")

  /** types declared in the unit currently being rendered (in scope by simple name). */
  private[emit] var currentDeclared: Set[SymId] = Set.empty
  /** the class whose body is being rendered — a constructor's funnel plan is looked up by it. */
  private[emit] var currentClass: Option[Tree.ClassDef] = None
  /** simple name of the TOP-LEVEL type being rendered — the qualifier a java `private` needs. */
  private[emit] var currentTopLevel: String = ""
  /** the top-level type's symbol, and the class whose body is being rendered right now. A java
    * `private` needs a qualifier only when the two DIFFER, i.e. the member lives in a NESTED class. */
  private[emit] var currentTopLevelSym: SymId = SymId.None
  private[emit] var currentOwnerSym: SymId    = SymId.None
  /** last segment of the package this unit is being EMITTED into — the qualifier a Java
    * package-private or `protected` declaration renders with (DESIGN §8.7). Read from the unit
    * being written, never from an upstream FQN plus a rename map (the rename runs LAST, §4.56).
    * Empty in the default package, already turned into a recorded widening by [[Visibility]]. */
  private[emit] var currentPkgTail: String = ""

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

  private[emit] val recordedMap    = collection.mutable.LinkedHashMap.empty[String, List[SrcMap.Entry]]
  private[emit] val recordedMisses = collection.mutable.ListBuffer.empty[String]

  // Base-surface contract (DESIGN.md §8.3): recorded at emission, never re-derived.
  // Covers nested types too, not only units.

  private[emit] val recordedTypeShapes   = collection.mutable.LinkedHashMap.empty[String, Surface.TypeShape]
  private[emit] val recordedMemberShapes = collection.mutable.LinkedHashMap.empty[String, Surface.MemberShape]

  /** Emitted type/member shapes, keyed by emitted name. Per-emitter, not process-global. */
  def emittedShapes: TirEmitter.Shapes =
    TirEmitter.Shapes(recordedTypeShapes.toMap, recordedMemberShapes.toMap)

  /** Surface contract gaps: unanswerable questions plus D6 cross-module `object` collisions. */
  def surfaceGaps: List[Surface.Gap] = collapsedBaseTypesNamed

  /** Members renamed by this emitter's §4.55 passes, by symbol to original Java name.
    * Only emitter renames, not phase renames. // ENGINE-LIMITS K28.1 */
  private[emit] lazy val renamedMembers: Map[SymId, String] =
    own.iterator.collect {
      case d if d.kind == Decision.Kind.RenamedMember && d.subject != SymId.None &&
                d.detail.get("to").contains(program.symbolOf(d.subject).map(_.name).getOrElse("")) &&
                d.detail.get("from").exists(_.nonEmpty) =>
        d.subject -> d.detail("from")
    }.toMap

  /** Access level as actually rendered: `private`, `private[Outer]`, or `public`.
    * Java `protected` is emitted as public (loosening can only remove errors). */
  private[emit] def visOf(s: Symbol, ownerSym: SymId): String =
    if !s.flags.isPrivate then "public"
    else privateQualifier(ownerSym).fold("private")(o => s"private[$o]")

  private[emit] def recordTypeShape(cd: Tree.ClassDef, form: String, plan: CtorFunnel.Plan,
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
  private[emit] def secondariesOf(cd: Tree.ClassDef, plan: CtorFunnel.Plan): List[Descriptor] =
    if sym(cd.symbol).flags.isModule then Nil
    else
      given Program = program
      val paramful = plans.paramfulPrimaryOf(cd)
      CtorFunnel.ctorsOf(program, cd.body)
        .filterNot(d => plan.primary.exists(_.symbol == d.symbol))
        .filterNot(d => !paramful && CtorFunnel.delegationOnlyNilary(program, d).isDefined)
        .map(d => Descriptor(CtorFunnel.valueParams(program, d).map(v => descriptorParam(v.tpt.tpe))))

  /** One type in the descriptor grammar. THE derivation, shared with `CtorFunnel`'s local plan so
    * a published slot and a dependent's re-derivation of it cannot be spelled differently
    * (ENGINE-LIMITS D15). */
  private[emit] def descriptorParam(t: TypeRepr): Param = Descriptor.paramOfType(program, t)

  // Porter notes: indexed by SymId (survives renames), not by name.

  private[emit] lazy val noteIndex: Map[SymId, List[Decision]] =
    notes.all.filter(d => PorterNote.Rendered(d.kind) && d.subject != SymId.None)
      .groupBy(_.subject)
      // Stable sort within a subject for deterministic output.
      .view.mapValues(_.sortBy(d => (d.kind.toString, d.reason.className, d.reason.detail, d.tsv))).toMap

  /** Decisions with no subject symbol, grouped by FQN (drops/injections of uninterned types). */
  private[emit] lazy val noteIndexByFqn: Map[String, List[Decision]] =
    notes.all.filter(d => PorterNote.Rendered(d.kind) && d.subject == SymId.None)
      .groupBy(_.subjectFqn).view.mapValues(_.sortBy(_.tsv)).toMap

  // Per-unit, cleared on re-emission for idempotence.
  private[emit] val recordedNotes = collection.mutable.LinkedHashMap.empty[String, collection.mutable.ListBuffer[PorterNote.Printed]]
  private[emit] def printedNotes: collection.mutable.ListBuffer[PorterNote.Printed] =
    recordedNotes.getOrElseUpdate(currentUnitName, collection.mutable.ListBuffer.empty)

  /** All notes this emitter printed, in order. Input to [[NoteCoverageCheck]]. */
  def notesPrinted: List[PorterNote.Printed] = recordedNotes.values.toList.flatten

  // Context clause: track when a `(using T)` clause cannot be rendered (trait, enum, nilary).
  // // ENGINE-LIMITS CT5
  private[emit] val clauseLost = collection.mutable.LinkedHashMap.empty[SymId, TirEmitter.ClauseLoss]

  /** Types whose constructors carry a context clause the emitted header does not render. */
  def contextClauseLosses: List[TirEmitter.ClauseLoss] = clauseLost.values.toList

  /** Record whether the emitted header dropped a context clause. */
  private[emit] def checkClause(cd: Tree.ClassDef, rendered: Boolean, form: String): Unit =
    if !rendered && CtorFunnel.ctorsCarryGivens(program, cd) then
      clauseLost(cd.symbol) = TirEmitter.ClauseLoss(
        cd.symbol, sym(cd.symbol).fullName, form, cd.origin)
    else clauseLost.remove(cd.symbol)

  /** Render notes for `s` matching `kinds` at indent `i`. Returns `""` when none. */
  private[emit] def noteBlock(s: SymId, i: Int, kinds: Set[Decision.Kind]): String =
    noteIndex.get(s).map(_.filter(d => kinds(d.kind))) match
      case Some(ds) if ds.nonEmpty =>
        val ind0 = ind(i)
        ds.map { d =>
          printedNotes += PorterNote.Printed(d.kind, d.subject, d.subjectFqn, currentUnitName)
          PorterNote.render(d, ind0)
        }.mkString
      case _ => ""

  /** Notes above a definition (def/val/nested class). */
  private[emit] def declNotes(s: SymId, i: Int): String = noteBlock(s, i, PorterNote.AtDeclaration)

  /** Notes at body head for dropped members (no declaration to attach to). */
  private[emit] def bodyNotes(s: SymId, i: Int): String = noteBlock(s, i, PorterNote.InBody).stripSuffix("\n")

  /** File-level notes for the top-level unit symbol (e.g. namespace rename). */
  private[emit] def unitNotes(cd: Tree.ClassDef): String = declNotes(cd.symbol, 0)

  private[emit] var currentUnitName: String = ""

  // Preview mode: `compiletime.error` instead of residue comments. // ENGINE-LIMITS M6
  // Per-unit, cleared on re-emission for idempotence.
  private[emit] val recordedEmission =
    collection.mutable.LinkedHashMap.empty[String, collection.mutable.ListBuffer[Decision]]
  private[emit] def emissionOf: collection.mutable.ListBuffer[Decision] =
    recordedEmission.getOrElseUpdate(currentUnitName, collection.mutable.ListBuffer.empty)

  /** Emission-time decisions, per unit (idempotent). Drained by `PortRun`. */
  def emissionDecisions: List[Decision] = recordedEmission.values.toList.flatten

  /** Whether rendering `unit` recorded decisions or notes (makes it ineligible for action cache). */
  def recordedForCache(unit: String): Boolean =
    recordedEmission.get(unit).exists(_.nonEmpty) || recordedNotes.get(unit).exists(_.nonEmpty)

  /** Emit residue comment (shipping) or `compiletime.error` (preview) for an unrenderable construct. */
  private[emit] def unrenderable(what: String, why: String, action: String, o: Origin, residue: String): String =
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
  private[emit] val keywords = Set(
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

  /** The default `javaSource`: the upstream file, read once per path. Memoised per JVM (a pure
    * function of the path, asked once per top-level type). An unreadable path is `None`, not an
    * exception — a source tree that moved after parsing must not fail the emitter; `TriviaCheck`
    * reports the same absence as an uncompared file. */
  private[emit] val javaSources = collection.concurrent.TrieMap.empty[String, Option[String]]

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
  private[emit] def note(
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
  private[emit] val MemberRenameRule = "member-rename(§4.55)"

  /** Drop `private` from the given members. Java lets a parent constructor write its own private
    * fields; REPLAYED one level down (`CtorFunnel.replayFor`) they execute in the subclass, where
    * `private` no longer reaches — widening only removes access errors, never behaviour.
    * `forDependents` is the same widening for a subclass THIS RUN CANNOT SEE
    * (`ENGINE-LIMITS.md` C15), kept separate so the note doesn't misattribute an empty class. */
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
      // AN ENUM PROMOTES ITS CONSTRUCTOR PARAMETERS TOO, by a different route: `enumDef` renders
      // each as a var field without consulting `CtorFunnel` (`ENGINE-LIMITS.md` T11's remaining
      // half). NARROW, unlike the plan-based arm below: an enum parameter is EMITTED SURFACE (a
      // public var), so only a real collision renames one. Two names are NOT collidees: the
      // parameter's own name, and a body field it SUPERSEDES (`enumDef` drops that `ValDef`).
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

  /** Rename any field that SHADOWS an inherited member. Java fields shadow rather than override,
    * resolving by the STATIC receiver type; scala has no such thing, so the field gets a fresh
    * name — exact, since every TIR reference already points at the symbol java chose. A field
    * shadowing an inherited METHOD gets the same treatment. Narrowed to `isKnown(fqn) &&
    * mayDeclare(fqn, sig)`, since UNKNOWN-is-YES would over-rename (`ENGINE-LIMITS.md` K28.2). */
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
        * the other's type, are an IMPLEMENTATION pair, not a shadowing one — java cannot produce a
        * parameterless method, so `paramss == Nil` is always a property conversion's accessor.
        * `exists`, not `forall`: the same name can reach a class from TWO directions, and `forall`
        * would silently stop implementing the member (`ENGINE-LIMITS.md` K5.7's trade). */
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
    * `Program` CONTAINS its base with EXTRA descendants the base's own run never saw, so an
    * independent rename could produce a module that cannot compile against what it resolves
    * against (§1.5). The base's answer is FOLLOWED, not merely respected. `Kept` settles only the
    * base's HALF of the clash — the caller still moves the half it owns. */
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
    * clash passes strip private/protected unconditionally so a renamed field stays reachable, but
    * the rename was recorded and the widening was not. One row per member that ACTUALLY LOST a
    * modifier; `clash` matches the `RenamedMember` row beside it, so both questions are one grep. */
  private[emit] def recordClashWidening(p: Program, out: collection.mutable.Buffer[Decision],
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
    * fourth face of §4.55, running the other way (the CAPTURE moves). Scala resolves
    * innermost-first, so the member wins and the local becomes unnameable. TWO RULES: UNNAMEABLE
    * (body references it, class declares/inherits the name) and AMBIGUOUS (scala 3's `E049`, java
    * has none — `ENGINE-LIMITS.md` C16). Same remedy both ways — move the outer declaration. */
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

  /** Rename any field whose simple name collides with a method in the same EMITTED SCOPE (legal
    * in java, illegal in scala) by suffixing `$field`. "Same emitted scope" is PLACEMENT, not name
    * — a java `static` member leaves for the companion, so an instance field of the same name
    * cannot collide with it. INSTANCE scope is inherited (a descendant's method still clashes);
    * STATIC scope is not. A `module` symbol has one body for both, collapsing the partition. */
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
        * declarations (`Kept` means the base's own run saw no such descendant). The rename must
        * still be SOUND: a method implementing/overriding something this module does not own
        * cannot move, and that closure is refused and RECORDED (DESIGN.md §8.3). */
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
