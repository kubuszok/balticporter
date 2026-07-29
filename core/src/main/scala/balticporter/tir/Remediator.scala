package balticporter.tir

/** Turns a portability FINDING into the manifest line that would fix it.
  *
  * ==Why this exists==
  *
  * CLAUDE.md §4.45: the engine's consumer is an agent in ANOTHER repository, and "an error an
  * agent cannot classify as (a) engine bug, (b) configure an existing phase, or (c) write a
  * library-specific rule costs it a full investigation." `PortReport` already answers *which kind*
  * a finding is, per check. This is the next step down: for the shapes the engine can recognise
  * with certainty, it answers *which line to paste*, computed from the program being ported.
  *
  * Every FQN in a rendered snippet comes from the `Program`; the engine names only its own
  * mechanisms (`Substitutions`, `StaticForwarderTransform.Forwarder`, `ClassTableTransform`). The
  * CLAUDE.md §1 grep therefore stays clean by construction — there is no library table here to
  * grow.
  *
  * ==The design constraint that shaped it: a WRONG suggestion is worse than none==
  *
  * A suggestion an agent must disprove costs it a cycle, which is more than the finding alone
  * would have. So this is deliberately three templates and a fallback, not a suggestion engine:
  *
  *  - each template states a fact that is CHECKED against the program, never inferred from a name;
  *  - a template that cannot verify its precondition does not fire;
  *  - anything not covered by a template is reported as an OBSERVATION — what was measured about
  *    the finding's distribution — with no fix proposed.
  *
  * The confidence on every suggestion is about the ENGINE's part of the claim, and stops there.
  * "Every use of these APIs is inside one type" is measurable and is what `High` asserts; whether
  * that type may be dropped, and what should replace it, is the port's judgement and is always
  * stated as the operator's remaining decision.
  *
  * ==What it can see, and the frontend fix that made two of the three possible==
  *
  * Two templates need to know which external MEMBER a call reaches, not merely which external
  * type. That was unavailable until external members carried their owner id (`Minter.external`):
  * an external member's `fullName` is an interning key, so `owner#name` is the only place its
  * identity lives, and it was `SymId.None` for the whole history of the project. The same defect
  * blinded nine `PortabilityCheck` rules. Both are fixed by the same one-field change.
  */
object Remediator:

  enum Confidence(val label: String):
    /** the precondition was verified against the program, and the snippet is complete. */
    case High extends Confidence("high")
    /** the precondition was verified; the snippet has a slot only the port can fill. */
    case Medium extends Confidence("needs one value from you")
    /** nothing is proposed — this is a measurement of the finding's shape. */
    case Observation extends Confidence("observation")

  /** One remediation. `observed` is always populated and is the part that is measured; `snippet`
    * is present only when a template verified its precondition. */
  final case class Suggestion(
      mechanism: String,
      subject: String,
      confidence: Confidence,
      observed: String,
      snippet: Option[String] = None,
      caveat: Option[String] = None,
  ):
    def render: String =
      val head = s"  [$mechanism] $subject — ${confidence.label}\n      $observed"
      val snip = snippet.map(s => s"\n      >>> $s").getOrElse("")
      val cav  = caveat.map(c => s"\n      ! $c").getOrElse("")
      head + snip + cav

    /** the persisted form. The snippet leads the detail because that is the actionable half and a
      * human render truncates from the right; `path` stays `-` rather than being repurposed, since
      * every other consumer of that column treats it as a source path. */
    def report: CheckReport.Finding =
      CheckReport.Finding("remediation", mechanism, subject, "-", 0,
        snippet.map(s => s"$s  ·  ").getOrElse("") +
          s"${confidence.label} — $observed${caveat.map(c => " ! " + c).getOrElse("")}")

  // -------------------------------------------------------------------------

  /** Suggestions for the violations given. Pass the EMITTED violations: a violation inside a type
    * the manifest already drops has been remediated, and re-suggesting it is exactly the wasted
    * cycle this file exists to avoid. */
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

  /** the `CheckReport` feed, so the snippets are persisted and diffed rather than scrolling past
    * in a terminal — the failure LIBRARY-READINESS.md §2.2 names for the checks themselves. */
  def record(suggestions: List[Suggestion]): Unit =
    CheckReport.record("remediation", suggestions.map(_.report))

  def summary(suggestions: List[Suggestion]): String =
    if suggestions.isEmpty then "  none"
    else suggestions.map(_.render).mkString("\n")

  // -------------------------------------------------------------------------
  // Template 1 — every use of an unportable API is inside ONE type the port declares.
  // -------------------------------------------------------------------------

  /** `Substitutions(dropTypes = Set(W))`.
    *
    * The measured claim is exact: if every site of APIs a1…an lies inside `W`, then removing `W`
    * removes all of them, and nothing else in the port has to change. That is the shape libGDX's
    * `NetJavaImpl` and `ReflectionPool` drops actually had, and it is the one fact about a
    * portability finding that a manifest edit can act on directly.
    *
    * Two grades, and the difference is measured rather than guessed: a `W` nothing else references
    * can be dropped outright (`High`); a `W` other units reference needs an injected replacement
    * at the same FQN or the port stops compiling, which `SubstitutionCheck` would then report as a
    * dangling reference (`Medium`).
    */
  private def dropSuggestions(
      program: Program,
      sited: List[(PortabilityCheck.Violation, Option[SymId])],
  ): List[(Suggestion, Set[String])] =
    // an api is chokepointed when EVERY one of its sites resolves to the same declared type.
    // NB `.toList` before the flatMap, and it is load-bearing: flat-mapping the Map directly
    // builds a Map keyed by the wrapper, so a type that chokepoints four APIs kept ONE of them
    // and the other three fell through to the observation fallback — a suggestion that understated
    // itself while looking complete.
    val choke = sited.groupBy(_._1.api).toList.flatMap { (api, vs) =>
      vs.map(_._2).distinct match
        case List(Some(w)) if declares(program, w) => Some(w -> api)
        case _                                     => scala.None
    }
    choke.groupBy(_._1).toList.sortBy((w, _) => fullName(program, w)).map { (w, pairs) =>
      val apis  = pairs.map(_._2).toSet
      val name  = fullName(program, w)
      // sites OF THE CHOKEPOINTED APIS, not every violation inside `w`: the type may also hold a
      // site of an api that occurs elsewhere too, and counting it here would overstate the claim.
      val sites = sited.count((v, o) => o.contains(w) && apis(v.api))
      val outside = externalReferrers(program, w)
      val obs = s"all $sites site(s) of ${apis.size} JVM-only API(s) are inside this one declared " +
        s"type (${apis.toList.sorted.mkString(", ")})"
      if outside.isEmpty then
        (Suggestion("substitutions-drop", name, Confidence.High,
          s"$obs, and no other unit in this port references it — it can be dropped outright",
          Some(s"""Substitutions(dropTypes = Set("$name"))"""),
          Some("verify the drop with SubstitutionCheck: a reference left behind is a dangling type, not a smaller number")),
         apis)
      else
        val (touched, total) = memberSpread(program, w, sited)
        // Dropping a type to remove an API that two of its ninety members use is true and useless.
        // The ratio is stated rather than thresholded — an engine that decided "disproportionate"
        // for the operator would be guessing at the port's shape.
        val proportion =
          // `touched` counts enclosing DEFINITIONS, which may be nested (an anonymous class body),
          // so it is only comparable to the declared-member count when it does not exceed it.
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
          Some("the replacement must declare the SAME FQN, or every referring type stops compiling")),
         apis)
    }

  /** how much of `w` is implicated: (members holding a violation site, members declared). */
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

  /** `StaticForwarderTransform.Forwarder(wrapper, receiver, members)`.
    *
    * The precondition is checked, not named: a declared `static W.m(x: X, rest…)` whose body calls
    * `X#m` — the same member name, on an external type, with the method's own first parameter's
    * type as the owner. That is exactly the rewrite the phase performs, so a match is evidence
    * rather than a resemblance.
    *
    * The important half is what is EXCLUDED. A wrapper member that forwards to an `X#m` which is
    * itself an unportable finding must NOT be inlined: forwarding relocates the JVM dependency
    * from the wrapper to the call site and the port is no more portable than before. Those are
    * listed by name as the residue that still needs a real replacement — which is the same
    * residue `StaticForwarderTransform`'s own doc comment says the phase exists to keep explicit.
    */
  private def forwarderSuggestions(
      program: Program,
      sited: List[(PortabilityCheck.Violation, Option[SymId])],
  ): List[(Suggestion, Set[String])] =
    val implicated = sited.flatMap(_._2).toSet
    if implicated.isEmpty then Nil
    else
      val unportableMembers = sited.map(_._1.api).toSet
      // (wrapper, receiver) -> forwarded member names, and the ones that must not be forwarded
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
                "know about relocates the dependency silently.")),
            Set.empty[String]))
        case _ => scala.None
      }

  /** `enc` is a declared static method named `member` whose FIRST parameter has type `receiver` —
    * i.e. the shape `StaticForwarderTransform` rewrites. Returns the type that declares it. */
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

  /** `ClassTableTransform(Map(callee -> table))`.
    *
    * Scoped to `Class.forName` alone, and that is not timidity. The phase replaces the whole
    * receiver-and-call with `Table.member(args)`, which is faithful only for a STATIC lookup whose
    * argument is the name — `c.newInstance()` rewritten the same way would silently lose `c`. A
    * template that guessed which reflective calls are static would produce exactly the wrong
    * suggestion this file's design forbids.
    *
    * `Medium` because the destination table cannot be computed: it is a type the port has to
    * write, listing the classes it can name. The KEY is exact, and the key is the part that is
    * easy to get wrong — it is `owner#member` of the CALLEE, which is not what a reader looking at
    * the emitted code would guess.
    */
  private def classTableSuggestions(
      program: Program,
      sited: List[(PortabilityCheck.Violation, Option[SymId])],
  ): List[(Suggestion, Set[String])] =
    val lookups = sited.filter((v, _) => v.api == ClassForName)
    if lookups.isEmpty then Nil
    else
      // prefer the port's OWN forwarding wrapper as the key when there is exactly one: redirecting
      // `W.forName` leaves W's body (the `Class.forName` call itself) as the only thing to drop,
      // which is what makes the wrapper removable at all.
      val wrappers = lookups.flatMap { (v, _) =>
        program.symbolOf(v.enclosing).filter(s => s.flags.isStatic && declares(program, s.owner))
          .map(s => s"${fullName(program, s.owner)}#${s.name}")
      }.distinct.sorted
      val keys = if wrappers.sizeIs == 1 then wrappers else List(ClassForName)
      val via  = if wrappers.sizeIs == 1 then
        s"reached through this port's own static wrapper ${wrappers.head}, so redirecting the WRAPPER " +
          "leaves nothing but the wrapper's own body to remove"
      else s"${lookups.size} direct site(s); redirect the callee itself"
      List((Suggestion("class-table", keys.head, Confidence.Medium,
        s"a runtime class lookup by name — $via",
        Some(s"""new ClassTableTransform(Map("${keys.head}" -> "<your.pkg.TypeTable>#classFor"))"""),
        Some("you supply the table: an injected object mapping each name this port can round-trip to a `classOf[…]` literal")),
        Set(ClassForName)))

  private val ClassForName = "java.lang.Class#forName"

  // -------------------------------------------------------------------------
  // Fallback — measured, never proposed.
  // -------------------------------------------------------------------------

  /** Everything no template matched. This says what the distribution IS and stops: an api used
    * from eight unrelated types has no single seam, and inventing one for it is the failure mode
    * that costs the reader more than silence would.
    */
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

  /** every DECLARED type other than `w` that references `w`. */
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
