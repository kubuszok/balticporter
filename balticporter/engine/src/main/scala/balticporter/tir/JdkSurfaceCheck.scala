package balticporter.tir

/** The port's JDK WALL, named — every `java.*` member the emitted code still calls, classified by
  * what the port's own machinery does about it, before any compiler runs.
  *
  * ==Why this exists==
  * JDK coverage in this engine grew one compile error and one failing test at a time: a shim was
  * written when a call would not compile, a mapping arm added when a lane went red, a refusal
  * recorded in a doc comment and a `case _ => None`. Every one of those decisions is real; NONE of
  * them was ever written down anywhere a new library's first run could read. So a `Collections.swap`
  * demand surfaced as a compile error three lanes after the library was added, and ENGINE-LIMITS K9
  * — enhanced-for over a JDK iterable the port KEPT — surfaced as two `value foreach is not a
  * member of java.util.List` errors that read like an engine bug.
  *
  * The demand was always visible: [[ExternalUsage]] is the enumeration the portability check
  * already performed. This classifies it.
  *
  * ==The classification, and where each answer comes from==
  * Every answer comes from a table SOMETHING ELSE owns — CLAUDE.md §4.56's general form, that a
  * conclusion about a type may only come from what a phase itself DID to that type:
  *
  * | disposition | source of truth | who fixes it |
  * |---|---|---|
  * | `Shimmed` | the runtime artifact's member map, pinned to the published sources by a spec | nobody — engine, done |
  * | `Mapped` | the retyping phase's own tables, and the phase RAN | nobody |
  * | `Mappable` | the same tables, and the phase did NOT run in this port | §1(b) — enable the phase, or keep the JDK deliberately |
  * | `Refused` | [[Refusals]], each entry carrying `why` and its `ENGINE-LIMITS.md` citation | §1(a) with a citation |
  * | `Kept` | nothing claims this member: it is emitted verbatim against the JDK and compiles | nobody |
  * | `Unhandled` | — | '''the finding''': a phase retyped this member's owner and has no rewrite for it |
  * | `KeptIterable` | — | '''the finding''': K9, derived |
  * | `StaleRefusal` | — | '''the finding''': a refusal contradicted by a table that now handles it |
  *
  * ==`Kept` is not a hole in "anything unclassified is a finding"==
  * It is what makes that sentence mean something. `java.lang.Math#max` is referenced hundreds of
  * times across this corpus, is available on the JVM, Scala.js and Native alike, and is emitted
  * verbatim — reporting it would put hundreds of rows of noise in front of the one row that says
  * `Collections#rotate` has no translation, and a report whose false positives must be routinely
  * ignored trains its readers to ignore it (CLAUDE.md §4.45). A member is a FINDING when the port's
  * own machinery has already moved the ground under it: the owner was retyped and the member was
  * not, or the loop that iterates it will not compile. `Kept` names everything the port never
  * touched, and it is COUNTED in the summary rather than hidden.
  *
  * ==The rows are the surface AFTER the pipeline, and that is the whole point==
  * A member whose call the phase actually REWROTE is no longer referenced, so it does not appear
  * here at all — the strongest possible outcome, and the reason the summary's `mapped` count is
  * smaller than a reader expects. `Mapped` survives for the rewrites that keep the java member
  * SYMBOL and change only its shape: every `parenless` strip (`xs.size()` → `xs.size`), `get(i)` →
  * `xs(i)`, and every arm that re-selects the same `m` on a retyped receiver. So the disposition
  * still means "the phase's table names this member", and a `mapped` row is a call the port emits
  * against a Scala collection rather than a dependency on the JDK.
  *
  * ==What `Unhandled` does and does not claim==
  * It claims the phase has no ENTRY for this member. It does not claim the emitted call is broken:
  * a `java.util.List#indexOf` on a `mutable.Buffer` survives because Scala happens to spell the
  * member the same way. That is coverage by coincidence — nothing recorded it, nothing would notice
  * it changing — so it is a row in the work list and not a silent pass, which is the whole point of
  * baselining the initial numbers.
  */
object JdkSurfaceCheck:

  /** the check's name in `findings.tsv`. */
  val Name = "jdk-surface"

  /** The namespaces this report is ABOUT.
    *
    * A namespace SELECTION — which surface is being reported — and not a structural conclusion
    * about a type, which is the thing §4.56 forbids deciding from a name: nothing below decides
    * that a member is portable, shimmed, mapped or refused because of what it is called. Cut at a
    * separator for §4.56's other corollary, so `javax` never covers `javaxfoo`. */
  val JdkNamespaces: List[String] = List("java", "javax")

  private def inJdk(fqn: String): Boolean =
    JdkNamespaces.exists(ns => fqn == ns || fqn.startsWith(ns + "."))

  /** what the port does about one referenced JDK member. */
  enum Disposition:
    case Shimmed(shim: String)
    case Mapped(target: String)
    case Mappable(target: String)
    case Refused(why: String, cite: String)
    case Kept
    case Unhandled(target: String)
    /** ENGINE-LIMITS K9, derived: an enhanced-for over an iterable the port KEPT. The subject is a
      * TYPE and a SITE rather than a member — `for (x <- xs)` emits `foreach`, which a kept
      * `java.util.List` does not have. */
    case KeptIterable(tpe: String)
    /** a refusal the tables now contradict. `toCollection` was refused in a comment for a release
      * after the `into` arm started handling it. */
    case StaleRefusal(api: String)

    def isFinding: Boolean = this match
      case Unhandled(_) | KeptIterable(_) | StaleRefusal(_) => true
      case _                                                => false

    def label: String = this match
      case Shimmed(_)      => "shimmed"
      case Mapped(_)       => "mapped"
      case Mappable(_)     => "mappable"
      case Refused(_, _)   => "refused"
      case Kept            => "kept"
      case Unhandled(_)    => "unhandled"
      case KeptIterable(_) => "kept-iterable"
      case StaleRefusal(_) => "stale-refusal"

    /** which of §1's three kinds the fix is — the thing a bare typer error cannot say (§4.45). */
    def classification: String = this match
      case Unhandled(target) =>
        s"§1(b) CONFIGURE: a phase retyped this member's owner to `$target` and its tables have no " +
          "entry for the member. Add the mapping (the retyping phase's static/instance tables and " +
          "the runtime helper beside them), or record a CITED refusal — a member that survives " +
          "only because Scala happens to spell it the same way is coverage nothing recorded."
      case KeptIterable(t) =>
        s"§1(b) UNBUILT, ENGINE-LIMITS K9: `$t` is iterated with an enhanced-for and is neither " +
          "retyped by a phase nor the shipped iterable shim, so the emitted `for (x <- xs)` asks " +
          "for a `foreach` it does not have. The fix K9 specifies is a phase with an EMPTY default " +
          "that rewrites a declared set of kept iterables to the iterator protocol — not a " +
          "universal emitter change (it would move every foreach digest in every port) and not a " +
          "test on the type's NAME (§4.56 forbids it, and it fails in both directions here)."
      case StaleRefusal(api) =>
        s"§1(a) ENGINE: `$api` is recorded as refused AND handled by a phase table. One of the two " +
          "is out of date, and a refusal that names a case the code handles is worse than no " +
          "refusal — it is the reason not to look."
      case _ => ""

  /** A deliberate non-translation, with its reason and its citation.
    *
    * ==Check data, not a decision==
    * `decisions.tsv` records what changed an emitted DECLARATION (§5.1's altitude rule), and a kept
    * JDK call changes no declaration at all: it is a fact about the SURFACE, which is what a check
    * table is for. None of `Decision.Kind`'s cases fits an external member, and inventing one would
    * put a row in every port's provenance artifact for something the port did not do.
    *
    * ==An UNCITED refusal is not a refusal==
    * `cite` is required because the value of this table is entirely in being followed: a `why`
    * with no pointer is the doc comment these entries came from, one indirection further away. */
  final case class Refusal(api: String, why: String, cite: String):
    /** matches a member key (`owner#name`) exactly. */
    def matches(member: String): Boolean = member == api

  /** The engine's own refusals — each one previously living in a doc comment or a `case _ => None`
    * arm, where nothing could read it and no run could report it.
    *
    * Per-library refusals belong beside them and are not built yet: they are a fact about the
    * SHARED SURFACE, so their home is the manifest (§1.5's inherited column), not a conf key. */
  val Refusals: List[Refusal] = List(
    // `Collections#unmodifiableList`/`Set`/`Map` and `Collectors#toSet`/`toMap` stood here and are
    // GONE, removed by the STALE-REFUSAL guard rather than by anyone remembering: all five are now
    // rewritten (ENGINE-LIMITS K6). Their citations said "no read-only view exists to map onto" and
    // "the target type cannot be guessed", and the first was a claim about the STDLIB that stopped
    // being true the moment the runtime supplied the view. A refusal that outlives its reason is a
    // finding that sends its reader to a wall which is not there (§4.45).
    Refusal("java.util.Map$Entry#setValue",
      "a `java.util.Map.Entry` becomes a `Tuple2`, which has no write-through to the map. A " +
        "`setValue` must fail to COMPILE rather than be turned into a write to a detached copy",
      "CollectionsTransform.rewrite, the `entrySet` arm"),
    Refusal("java.util.Map.Entry#setValue",
      "the dotted spelling of the same member, for a frontend that names nested types with `.`",
      "CollectionsTransform.rewrite, the `entrySet` arm"),
  )

  /** the instance-table key for an arm that matches every collection kind. */
  val AnyKind = "*"

  /** the member NAME a constructor is interned under — see [[Mapping.constructors]]. */
  val Constructor = "<init>"

  /** What a retyping phase DID, as data the check reads — never re-derived here.
    *
    * `ran` is the difference between a demand and an offer: with the phase in the pipeline an
    * unhandled member on a retyped owner is a hole the phase MADE, and is a finding; with the phase
    * absent the same member is untouched JDK code the port chose to keep, and the row says only
    * that a mapping exists if the port wants it.
    *
    * The empty value makes the whole check a report of `Kept` rows plus K9 — the §1(b) discipline
    * that an empty parameter is a no-op, applied to a check. */
  final case class Mapping(
      phase: String,
      ran: Boolean,
      /** java FQN → (target FQN, the kind key its instance arms are written against) */
      types: Map[String, (String, String)],
      /** `owner#name` for every STATIC the phase rewrites */
      statics: Set[String],
      /** kind key → instance member names; [[AnyKind]] for an arm that matches every kind */
      instance: Map[String, Set[String]],
      /** shim FQN → the members it declares */
      shimMembers: Map[String, Set[String]],
      /** the FQN of the iterable shim, whose `foreach` extension is what makes an enhanced-for
        * work — K9's "covered by the shipped iterable shim" half */
      iterableShim: Option[String],
      /** does the phase rewrite `new` on every type it retypes?
        *
        * A CONSTRUCTOR is not a member call and cannot be in a member table: retyping the type IS
        * the rewrite for `new`, and the arity correspondence between the java constructor and the
        * scala one is the phase's own business (ENGINE-LIMITS K11 is exactly that correspondence
        * failing and being fixed). Without this the check reports `java.util.HashMap#<init>()` as a
        * hole at every port that constructs one, while the emitted line reads
        * `new scala.collection.mutable.HashMap()` and calls nothing java at all — 18 such rows on
        * the first run, in front of the `clear`/`contains` rows that are the real work list.
        *
        * `false` — [[noMapping]]'s value — leaves a constructor classified like any other member, so
        * a phase that retypes without touching `new` is reported rather than assumed. */
      constructors: Boolean = false,
  ):
    def handles(owner: String, name: String, kind: String): Boolean =
      (constructors && name == Constructor) ||
        statics.contains(s"$owner#$name") ||
        instance.getOrElse(AnyKind, Set.empty).contains(name) ||
        instance.getOrElse(kind, Set.empty).contains(name)

    /** the owners the STATIC table names — derived, so `Collections#rotate` is seen as a hole in a
      * family the phase does rewrite rather than as an untouched utility class. */
    lazy val staticOwners: Set[String] = statics.map(_.takeWhile(_ != '#'))

  val noMapping: Mapping =
    Mapping("", ran = false, Map.empty, Set.empty, Map.empty, Map.empty, scala.None)

  /** one classified row. */
  final case class Finding(subject: String, disposition: Disposition, sites: Int, origin: Origin):
    def detail: String = disposition match
      case Disposition.Unhandled(t)    => s"$subject — retyped to $t, no rewrite ($sites site(s))"
      case Disposition.KeptIterable(_) => s"enhanced-for over $subject, which nothing retyped and no shim covers"
      case Disposition.StaleRefusal(a) => s"$a is recorded as refused AND handled by a phase table"
      case d                           => s"$subject — ${d.label} ($sites site(s))"
    def render: String = s"${disposition.label} $detail  (${origin.javaPath}:${origin.line})"
    /** the SUBJECT column is the MEMBER, not the enclosing declaration: a `jdk-surface` row is a
      * fact about the surface (one member, however many call sites), and keying it on whichever
      * method happened to call it first would re-key the finding every time an unrelated caller
      * moved. K9's rows are per SITE and carry the type as their subject for the same reason. */
    def report: CheckReport.Finding =
      CheckReport.Finding(Name, disposition.label, subject,
        CheckReport.relativise(origin.javaPath), origin.line, detail)

  type Row = ExternalUsage.Row

  /** Classify every external JDK member `rows` holds, plus K9's ForEach demand over `units`.
    *
    * @param rows  the EMITTED lane of [[ExternalUsage]] — this module's own dependencies (D2)
    * @param units the units this run emits, for the ForEach walk
    * @param m     what the retyping phase did, or [[noMapping]] */
  def check(program: Program, rows: List[Row], units: List[Tree.ClassDef], m: Mapping): List[Finding] =
    members(rows, m) ++ keptIterables(program, units, m)

  /** every classified row, findings and non-findings alike — what the SUMMARY counts. Only
    * `isFinding` rows reach `findings.tsv`. */
  def classify(rows: List[Row], m: Mapping): List[(Row, Disposition)] =
    rows.filter(r => r.owner.exists(inJdk)).map(r => r -> dispositionOf(r, m))

  private def members(rows: List[Row], m: Mapping): List[Finding] =
    classify(rows, m).collect {
      case (r, d) if d.isFinding => Finding(r.key.getOrElse(r.fullName), d, r.sites, r.firstOrigin)
    }

  private def dispositionOf(r: Row, m: Mapping): Disposition =
    val owner = r.owner.getOrElse("")
    val key   = s"$owner#${r.name}"
    Refusals.find(_.matches(key)) match
      case Some(ref) =>
        // the stale-refusal guard: a refusal the tables contradict is itself a finding, in the
        // direction that actually happened (a comment naming a case the code handles).
        if m.ran && m.handles(owner, r.name, m.types.get(owner).map(_._2).getOrElse(AnyKind))
        then Disposition.StaleRefusal(ref.api)
        else Disposition.Refused(ref.why, ref.cite)
      case scala.None =>
        m.types.get(owner) match
          case Some((target, kind)) =>
            if m.shimMembers.getOrElse(target, Set.empty).contains(r.name) then Disposition.Shimmed(target)
            else if m.handles(owner, r.name, kind) then
              if m.ran then Disposition.Mapped(target) else Disposition.Mappable(target)
            else if m.ran then Disposition.Unhandled(target)
            else Disposition.Mappable(target)
          case scala.None =>
            // a receiver-less utility whose FAMILY the phase does rewrite — `Collections.rotate`
            // beside the `Collections.sort`/`swap`/`shuffle` it handles.
            if m.staticOwners.contains(owner) then
              if m.statics.contains(key) then
                if m.ran then Disposition.Mapped(owner) else Disposition.Mappable(owner)
              else if m.ran then Disposition.Unhandled(owner)
              else Disposition.Kept
            else Disposition.Kept

  /** ENGINE-LIMITS K9 as a DERIVED demand: an enhanced-for whose receiver the pipeline LEFT in the
    * JDK namespace.
    *
    * ==It reads the POST-PIPELINE type, which is stronger than reading a table==
    * The obvious spelling — "the receiver's java type is absent from the phase's `typeMap`" — is
    * the wrong side of the mapping and gets both directions wrong. A port that declares the phase
    * but SCOPES this declaration out (`RuleScope`) keeps a real `java.util.List`, which the table
    * says is mapped; a port with no phase at all keeps the same type, which the table also says is
    * mapped. Asking the NODE instead answers both: whatever any phase intended, the type standing
    * in the receiver slot when emission begins is the type the emitted `for (x <- xs)` will be
    * applied to. That is §4.56's rule at its strongest — the conclusion comes from what the phase
    * actually did to this expression, not from a table lookup and never from the type's name.
    *
    * `retypedTo` is therefore a guard against ONE thing only: a phase whose target is itself a
    * `java.*` type (a `LinkedList` → `ArrayDeque` style remap). Every target the engine ships today
    * is a `scala.*` or a `balticporter.runtime.*` type, so the set is empty in practice and the
    * guard costs nothing — but a check that would silently mis-report the day such a mapping is
    * added is not a check.
    *
    * Per SITE, not per member: the repair is per loop and each one is a distinct emission that will
    * not compile, where a missing mapping is one table entry however many call sites it has.
    *
    * An OWNED receiver is not this check's business — the port emits that type and whatever
    * `foreach` it has, and a dependent's program owns its base's types too (D2) — and neither is an
    * ARRAY, which is not a `TypeRef` at all and which Scala iterates natively. */
  private def keptIterables(program: Program, units: List[Tree.ClassDef], m: Mapping): List[Finding] =
    given Program = program
    val retypedTo = m.types.values.map(_._1).toSet ++ m.iterableShim
    val out = collection.mutable.ListBuffer.empty[Finding]
    val ph = new Phase:
      def name: String = "jdk-surface/kept-iterable"
      // the catch-all term hook, because `StandardTraversal` has no `ForEach`-specific one — and
      // going through the STANDARD traversal is the point (§3): an anonymous class's body is a node
      // there and a hand-rolled walk would stop one node short of it.
      override def transformTerm(t: Term)(using Program): Term =
        t match
          case f: Tree.ForEach =>
            headSym(f.iterable.tpe).flatMap(program.symbolOf).foreach { s =>
              val fqn  = s.fullName
              val kept = !program.owns(s.id) && inJdk(fqn) && !retypedTo.contains(fqn)
              if kept then out += Finding(fqn, Disposition.KeptIterable(fqn), 1, f.origin)
            }
          case _ => ()
        t
    units.foreach(StandardTraversal.mapClassDef(ph, _))
    out.toList

  private def headSym(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)     => Some(s)
    case TypeRepr.AppliedType(c, _) => headSym(c)
    case _                          => scala.None

  /** the one-line summary a run prints — N shimmed, M mapped, K refused, J unclassified. */
  def summary(classified: List[(Row, Disposition)], k9: Int): String =
    val byLabel = classified.groupBy(_._2.label).view.mapValues(_.size).toMap
    val order   = List("shimmed", "mapped", "mappable", "refused", "kept", "unhandled", "stale-refusal")
    val counts  = order.filter(byLabel.contains).map(l => s"$l ${byLabel(l)}") ++
      (if k9 == 0 then Nil else List(s"kept-iterable $k9"))
    if counts.isEmpty then "  no external java.* members referenced from emitted code"
    else "  " + counts.mkString(", ")

  /** the §1 classification lines a run prints under the headline, one per finding KIND present. */
  def classifications(fs: List[Finding]): List[String] =
    fs.map(_.disposition).map {
      case d: Disposition.Unhandled    => "unhandled" -> d.classification
      case d: Disposition.KeptIterable => "kept-iterable" -> d.classification
      case d: Disposition.StaleRefusal => "stale-refusal" -> d.classification
      case d                           => d.label -> ""
    }.filter(_._2.nonEmpty).distinctBy(_._1).map(_._2)
