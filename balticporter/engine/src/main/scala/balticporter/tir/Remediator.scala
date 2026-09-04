package balticporter.tir

/** Turns a portability finding into a manifest-line suggestion, computed from the program.
  * Three templates (drop, static-forwarder, class-table) each verify a precondition against
  * the program before firing; anything unmatched is reported as an observation with no fix.
  * // CLAUDE.md §4.45 */
object Remediator:

  enum Confidence(val label: String):
    /** the precondition was verified against the program, and the snippet is complete. */
    case High extends Confidence("high")
    /** the precondition was verified; the snippet has a slot only the port can fill. */
    case Medium extends Confidence("needs one value from you")
    /** nothing is proposed — this is a measurement of the finding's shape. */
    case Observation extends Confidence("observation")

  /** One suggestion. `snippet` is present only when a template verified its precondition. */
  final case class Suggestion(
      mechanism: String,
      subject: String,
      confidence: Confidence,
      observed: String,
      snippet: Option[String] = None,
      caveat: Option[String] = None,
      /** Machine-readable values of the same computation `snippet` renders.
        * Empty for the observation fallback. */
      payload: Map[String, String] = Map.empty,
  ):
    def render: String =
      val head = s"  [$mechanism] $subject — ${confidence.label}\n      $observed"
      val snip = snippet.map(s => s"\n      >>> $s").getOrElse("")
      val cav  = caveat.map(c => s"\n      ! $c").getOrElse("")
      head + snip + cav

    /** Persisted form. Snippet leads the detail; `path` stays `-`. */
    def report: CheckReport.Finding =
      CheckReport.Finding("remediation", mechanism, subject, "-", 0,
        snippet.map(s => s"$s  ·  ").getOrElse("") +
          s"${confidence.label} — $observed${caveat.map(c => " ! " + c).getOrElse("")}")

  // -------------------------------------------------------------------------

  /** Suggestions for the given violations. Pass the EMITTED violations only. */
  def suggest(program: Program, violations: List[PortabilityCheck.Violation]): List[Suggestion] =
    if violations.isEmpty then Nil
    else
      val sited = violations.map(v => v -> PortabilityCheck.owningType(program, v.enclosing))
      val chokepoints = dropSuggestions(program, sited)
      val forwarders  = forwarderSuggestions(program, sited)
      val classTables = classTableSuggestions(program, sited)
      val covered     = (chokepoints ++ forwarders ++ classTables).flatMap(_._2).toSet
      val rest        = observations(program, sited.filterNot((v, _) => covered(v.api)))
      (chokepoints ++ forwarders ++ classTables).map(_._1) ++ rest

  /** Findings for `CheckReport`. Returned, not written; `PortRun` records them. */
  def reports(suggestions: List[Suggestion]): List[CheckReport.Finding] = suggestions.map(_.report)

  def summary(suggestions: List[Suggestion]): String =
    if suggestions.isEmpty then "  none"
    else suggestions.map(_.render).mkString("\n")

  // -------------------------------------------------------------------------
  // Template 1 — every use of an unportable API is inside ONE type the port declares.
  // -------------------------------------------------------------------------

  /** Template 1: `Substitutions(dropTypes = Set(W))`.
    * `High` when nothing else references `W`; `Medium` when other units do (needs injection). */
  private def dropSuggestions(
      program: Program,
      sited: List[(PortabilityCheck.Violation, Option[SymId])],
  ): List[(Suggestion, Set[String])] =
    // `.toList` before flatMap: flat-mapping a Map directly would lose APIs sharing a wrapper
    val choke = sited.groupBy(_._1.api).toList.flatMap { (api, vs) =>
      vs.map(_._2).distinct match
        case List(Some(w)) if declares(program, w) => Some(w -> api)
        case _                                     => scala.None
    }
    choke.groupBy(_._1).toList.sortBy((w, _) => fullName(program, w)).map { (w, pairs) =>
      val apis  = pairs.map(_._2).toSet
      val name  = fullName(program, w)
      // sites of the chokepointed APIs only, not every violation inside `w`
      val sites = sited.count((v, o) => o.contains(w) && apis(v.api))
      val outside = externalReferrers(program, w)
      val obs = s"all $sites site(s) of ${apis.size} JVM-only API(s) are inside this one declared " +
        s"type (${apis.toList.sorted.mkString(", ")})"
      if outside.isEmpty then
        (Suggestion("substitutions-drop", name, Confidence.High,
          s"$obs, and no other unit in this port references it — it can be dropped outright",
          Some(s"""Substitutions(dropTypes = Set("$name"))"""),
          Some("verify the drop with SubstitutionCheck: a reference left behind is a dangling type, not a smaller number"),
          Map("type" -> name, "sites" -> sites.toString, "referrers" -> "0")),
         apis)
      else
        val (touched, total) = memberSpread(program, w, sited)
        val proportion =
          if total > 0 && touched > 0 && touched <= total then
            s"; $touched of its $total member(s) touch the API" +
              (if touched * 2 < total then
                 " — `Substitutions.dropMethods` on just those members is the smaller seam when nothing calls them"
               else "")
          else ""
        (Suggestion("substitutions-drop", name, Confidence.Medium,
          s"$obs; ${outside.size} other type(s) reference it (${outside.take(3).mkString(", ")}" +
            (if outside.sizeIs > 3 then ", …)" else ")") + proportion,
          Some(s"""Substitutions(dropTypes = Set("$name"), inject = List(<dir holding your replacement>))"""),
          Some("the replacement must declare the SAME FQN, or every referring type stops compiling"),
          Map("type" -> name, "sites" -> sites.toString, "referrers" -> outside.size.toString)),
         apis)
    }

  /** (members holding a violation site, total members declared in `w`). */
  private def memberSpread(
      program: Program,
      w: SymId,
      sited: List[(PortabilityCheck.Violation, Option[SymId])],
  ): (Int, Int) =
    val touched = sited.collect { case (v, Some(`w`)) => v.enclosing }.distinct.size
    val total = program.definitionOf(w).collect { case c: Tree.ClassDef =>
      c.body.count(_.isInstanceOf[Tree.DefDef | Tree.ValDef])
    }.getOrElse(0)
    (touched, total)

  // -------------------------------------------------------------------------
  // Template 2 — a declared static wrapper that merely forwards to the real receiver.
  // -------------------------------------------------------------------------

  /** Template 2: `StaticForwarderTransform.Forwarder(wrapper, receiver, members)`.
    * Precondition checked: a static `W.m(x: X, ...)` forwarding to `X#m`.
    * Excludes members that are themselves unportable (forwarding would relocate the dependency). */
  private def forwarderSuggestions(
      program: Program,
      sited: List[(PortabilityCheck.Violation, Option[SymId])],
  ): List[(Suggestion, Set[String])] =
    val implicated = sited.flatMap(_._2).toSet
    if implicated.isEmpty then Nil
    else
      val unportableMembers = sited.map(_._1.api).toSet
      val hits = collection.mutable.Map.empty[(SymId, SymId), (Set[String], Set[String])]
      for
        m <- program.symbols.all
        x <- program.symbolOf(m.owner) if isExternalType(program, x)
        u <- program.usages(m.id) if u.kind == UsageKind.Call
        w <- forwardingWrapperOf(program, u.enclosing, m.name, x.id) if implicated(w)
      do
        val key      = (w, x.id)
        val (ok, no) = hits.getOrElse(key, (Set.empty[String], Set.empty[String]))
        if unportableMembers.contains(s"${x.fullName}#${m.name}")
        then hits(key) = (ok, no + m.name)
        else hits(key) = (ok + m.name, no)
      hits.toList.sortBy((k, _) => (fullName(program, k._1), fullName(program, k._2))).flatMap {
        case ((w, x), (ok, blocked)) if ok.nonEmpty =>
          val wn = fullName(program, w)
          val xn = fullName(program, x)
          val members = ok.toList.sorted.map(m => s""""$m"""").mkString(", ")
          Some((Suggestion("static-forwarder-inline", wn, Confidence.High,
            s"${ok.size} static member(s) of this type forward their first argument to the " +
              s"same-named member of $xn; inlining them removes those calls from the wrapper",
            Some(s"""StaticForwarderTransform.Forwarder(wrapper = "$wn", receiver = "$xn", members = Set($members))"""),
            Some(
              (if blocked.nonEmpty then
                 s"NOT forwardable, because $xn declares them and they are themselves JVM-only: " +
                   s"${blocked.toList.sorted.mkString(", ")} — forwarding these relocates the dependency, " +
                   "it does not remove it; they are the residue that needs a real replacement. "
               else "") +
              // the exclusion is only as good as the rule list, and saying so is the difference
              // between a suggestion and a claim: a member the rules do not know about is offered
              // here, and inlining an unportable one moves the problem to every call site.
              s"The exclusions above are the members `PortabilityCheck` has a rule for. Confirm the " +
                s"rest are on YOUR target's $xn before pasting — an inlined member the rules do not " +
                "know about relocates the dependency silently."),
            Map("wrapper" -> wn, "receiver" -> xn, "members" -> ok.toList.sorted.mkString(","))),
            Set.empty[String]))
        case _ => scala.None
      }

  /** Returns the declaring type if `enc` is a static forwarding wrapper for `member` on `receiver`. */
  private def forwardingWrapperOf(program: Program, enc: SymId, member: String, receiver: SymId): Option[SymId] =
    for
      s  <- program.symbolOf(enc)
      if s.name == member && s.flags.isStatic
      d  <- program.definitionOf(enc).collect { case dd: Tree.DefDef => dd }
      ps <- d.paramss.headOption
      p1 <- ps.headOption
      if headSym(p1.tpt.tpe).contains(receiver)
      w  <- Some(s.owner) if declares(program, w)
    yield w

  // -------------------------------------------------------------------------
  // Template 3 — a runtime class lookup by NAME.
  // -------------------------------------------------------------------------

  /** Template 3: `ClassTableTransform(Map(callee -> table))`. Scoped to `Class.forName`.
    * `Medium` because the destination table must be hand-written. Key must be an OWNED member;
    * a direct `Class.forName` call has no selectable key and gets an observation only. */
  private def classTableSuggestions(
      program: Program,
      sited: List[(PortabilityCheck.Violation, Option[SymId])],
  ): List[(Suggestion, Set[String])] =
    val lookups = sited.filter((v, _) => v.api == ClassForName)
    if lookups.isEmpty then Nil
    else
      // only the port's own forwarding wrapper is a bindable key
      val wrappers = lookups.flatMap { (v, _) =>
        program.symbolOf(v.enclosing).filter(s => s.flags.isStatic && declares(program, s.owner))
          .map(s => s"${fullName(program, s.owner)}#${s.name}")
      }.distinct.sorted
      wrappers match
        case List(w) =>
          List((Suggestion("class-table", w, Confidence.Medium,
            "a runtime class lookup by name — reached through this port's own static wrapper " +
              s"$w, so redirecting the WRAPPER leaves nothing but the wrapper's own body to remove",
            Some(s"""new ClassTableTransform(Map("$w" -> "<your.pkg.TypeTable>#classFor"))"""),
            Some("you supply the table: an injected object mapping each name this port can round-trip to a `classOf[…]` literal"),
            Map("callee" -> w, "sites" -> lookups.size.toString)),
            Set(ClassForName)))
        case ws =>
          val shape =
            if ws.isEmpty then s"${lookups.size} direct site(s) of `$ClassForName`"
            else s"${lookups.size} site(s) reached through ${ws.size} of this port's own static " +
              s"wrappers (${ws.take(3).mkString(", ")}${if ws.sizeIs > 3 then ", …" else ""})"
          List((Suggestion("class-table", ClassForName, Confidence.Observation,
            s"$shape — NO SELECTABLE KEY: the redirect is keyed on a member this program DECLARES " +
              s"(both `ClassTableTransform(redirects)` and a `class-table` selection bind at " +
              s"`Ownership.Owned`), and `$ClassForName` is java's own, so a key naming it binds " +
              "nowhere. Route these through one static wrapper this port declares and select at " +
              (if ws.isEmpty then "THAT" else "ONE of them") + ", or hand-port each site",
            scala.None,
            Some("no snippet, deliberately: a pasted `java.lang.Class#forName` key is `ExternalOnly` " +
              "at both doors and costs its reader a cycle to disprove"),
            Map("sites" -> lookups.size.toString, "wrappers" -> ws.size.toString)),
            Set(ClassForName)))

  private val ClassForName = "java.lang.Class#forName"

  // -------------------------------------------------------------------------
  // Fallback — measured, never proposed.
  // -------------------------------------------------------------------------

  /** Observation fallback for APIs no template matched. Reports distribution, proposes nothing. */
  private def observations(
      program: Program,
      sited: List[(PortabilityCheck.Violation, Option[SymId])],
  ): List[Suggestion] =
    sited.groupBy(_._1.api).toList.sortBy(-_._2.size).map { (api, vs) =>
      val types = vs.flatMap(_._2).distinct.map(fullName(program, _)).sorted
      val shown = types.take(4).mkString(", ") + (if types.sizeIs > 4 then s", … (${types.size} total)" else "")
      Suggestion("observation", api, Confidence.Observation,
        s"${vs.size} site(s) across ${types.size} declared type(s): $shown — no single engine seam " +
          "covers them; each site is its own decision (accept as JVM-only, drop the type, or hand-port it)")
    }

  // -------------------------------------------------------------------------

  private def declares(program: Program, s: SymId): Boolean =
    s != SymId.None && program.definitionOf(s).exists(_.isInstanceOf[Tree.ClassDef])

  private def isExternalType(program: Program, s: Symbol): Boolean =
    !declares(program, s.id) && s.owner == SymId.None && s.fullName.contains('.') && !s.fullName.contains('#')

  private def fullName(program: Program, s: SymId): String =
    program.symbolOf(s).map(_.fullName).getOrElse("?")

  /** Every declared type other than `w` that references `w`. */
  private def externalReferrers(program: Program, w: SymId): List[String] =
    program.usages(w)
      .flatMap(u => PortabilityCheck.owningType(program, u.enclosing))
      .filterNot(_ == w)
      .distinct
      .map(fullName(program, _))
      .sorted

  private def headSym(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)      => Some(s)
    case TypeRepr.AppliedType(tc, _) => headSym(tc)
    case TypeRepr.ByNameType(u)      => headSym(u)
    case _                           => scala.None
