package balticporter.tir

import balticporter.catalog.FixKind

/** Classifies every `java.*` member the emitted code calls by disposition: `Shimmed`, `Mapped`,
  * `Mappable`, `Refused`, `Kept`, `Unhandled`, `KeptIterable` (K9), or `StaleRefusal`.
  * `Unhandled` = phase retyped the owner but has no entry for the member.
  * `Kept` = untouched JDK code (counted, not reported as a finding).
  * Reads from post-pipeline surface; a fully rewritten call is simply absent. */
object JdkSurfaceCheck extends RemedySource:

  /** the check's name in `findings.tsv`. */
  val Name = "jdk-surface"

  /** Namespace selection for this report. Cut at separator per §4.56. */
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

  /** A deliberate non-translation, with its reason and its citation. Check data, not a decision:
    * `decisions.tsv` records what changed an emitted DECLARATION, and a kept JDK call changes no
    * declaration — this is a fact about the SURFACE. `cite` is required because this table's value
    * is entirely in being followed: a `why` with no pointer is the doc comment one step removed. */
  final case class Refusal(api: String, why: String, cite: String):
    /** matches a member key (`owner#name`) exactly. */
    def matches(member: String): Boolean = member == api

  /** one sentence, three owners — java re-declares `spliterator()` down its own hierarchy and a
    * refusal keyed on `owner#name` has to be spelled at each, so the REASON is stated once. */
  private val SpliteratorWhy =
    "a `Spliterator` is a PARALLEL-DECOMPOSITION protocol (`trySplit`, `estimateSize`, " +
      "`characteristics`) and not an iterator: its only purpose is to be consumed by " +
      "`java.util.stream`, which this phase COLLAPSES rather than models. Unlike `listIterator`, " +
      "the receiver has no capability to build one out of — and the near miss is worth naming, " +
      "because it is the reason this stayed refused when its sibling did not: `buf.asJava` yields " +
      "a `java.util.List` whose `spliterator()` is `AbstractCollection`'s DEFAULT, reporting " +
      "NEITHER `ORDERED` nor `SIZED` where the `ArrayList` java had reports both. A consumer that " +
      "reads `characteristics()` would get a different answer silently, which is CLAUDE.md §4.4's " +
      "defect class bought for a member nothing calls"

  /** The engine's own refusals — each one previously living in a doc comment or a `case _ => None`
    * arm, where nothing could read it and no run could report it.
    *
    * Per-library refusals belong beside them and are not built yet: they are a fact about the
    * SHARED SURFACE, so their home is the manifest (§1.5's inherited column), not a conf key. */
  val Refusals: List[Refusal] = List(
    // `Collections#unmodifiableList`/`Set`/`Map` and `Collectors#toSet`/`toMap` stood here and are
    // GONE, removed by the STALE-REFUSAL guard: all five are now rewritten (`ENGINE-LIMITS.md` K6).
    // Their citation was a claim about the STDLIB that stopped being true once the runtime
    // supplied the view — a refusal outliving its reason sends its reader to a wall not there (§4.45).
    Refusal("java.util.Map$Entry#setValue",
      "a `java.util.Map.Entry` becomes a `Tuple2`, which has no write-through to the map. A " +
        "`setValue` must fail to COMPILE rather than be turned into a write to a detached copy",
      "CollectionsTransform.rewrite, the `entrySet` arm"),
    Refusal("java.util.Map.Entry#setValue",
      "the dotted spelling of the same member, for a frontend that names nested types with `.`",
      "CollectionsTransform.rewrite, the `entrySet` arm"),
    // `java.util.List#listIterator`/`spliterator` and `Set#spliterator` stood here and are GONE
    // (STALE-REFUSAL guard, `ENGINE-LIMITS.md` K23) — the refusal was about the WRAPPER's reported
    // characteristics, not the receiver; java declares `spliterator()` a default method per owner.
    // `Collection` STAYS: a `JavaCollection` shim receiver is skipped by `rewrite`'s blanket guard,
    // so this is keyed at `Collection` alone, or a `List` receiver falls through to an unhandled wall.
    Refusal("java.util.Collection#spliterator", SpliteratorWhy,
      "ENGINE-LIMITS.md K23; CollectionsTransform.rewrite skips a shim receiver before any arm"),
  )

  /** the instance-table key for an arm that matches every collection kind. */
  val AnyKind = "*"

  /** the member NAME a constructor is interned under — see [[Mapping.constructors]]. */
  val Constructor = "<init>"

  /** What a retyping phase DID, as data the check reads — never re-derived here. `ran` is the
    * difference between a demand and an offer: with the phase in the pipeline an unhandled member
    * on a retyped owner is a hole the phase MADE (a finding); absent, it's untouched JDK code the
    * port chose to keep. The empty value makes the check a report of `Kept` rows plus K9 — §1(b)'s
    * discipline applied to a check. */
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
      /** does the phase rewrite `new` on every type it retypes? A CONSTRUCTOR is not a member
        * call: retyping the type IS the rewrite for `new`, and arity correspondence is the phase's
        * own business (`ENGINE-LIMITS.md` K11). Without this the check reports every `new
        * HashMap()` as a hole (18 such rows measured). `false` leaves a constructor classified
        * like any other member, so a phase that retypes without touching `new` is reported. */
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

  // -------------------------------------------------------------------------------------------
  // THE MENU (`DESIGN.md` §8.16) — what a port may ASK FOR at one of these rows
  // -------------------------------------------------------------------------------------------

  /** THE EMITTED CALL IS RIGHT AS IT STANDS — the port read this JDK member and states so. An
    * `Unhandled` row claims the phase has no entry, NOT that the emission is broken (coverage may
    * be by COINCIDENCE) — a reading only the port can settle. Keyed at the EXTERNAL CALLEE
    * (`Remedy.Subject.ExternalMember`). NOT emission-affecting. `kept-iterable`/`stale-refusal`
    * are deliberately NOT on this menu (would silence a real defect or the guard's own signal). */
  val AcceptJdkMember: Remedy = Remedy(
    // …the kind read off the DISPOSITION's own `label`, never a literal: that is the string a
    // `findings.tsv` row carries and the string `resolved` matches through, and three spellings of
    // one lane's kind is what `Remedy.lane` being a constant already refuses one field over.
    id = "accept-jdk-member", lane = Name, kind = Disposition.Unhandled("").label,
    emissionAffecting = false, fix = FixKind.Parameterised,
    subject = Remedy.Subject.ExternalMember,
    what = "the port has READ this JDK member and states that the call it emits against the JDK is " +
      "correct here — coverage by coincidence, examined and recorded rather than left to be " +
      "rediscovered")

  def remedies: List[Remedy] = List(AcceptJdkMember)

  /** DRAIN what this port selected — `CLAUDE.md` §5's move, through the one function every lane
    * uses. Returns the rows that were NOT drained. */
  def resolved(plan: ResolutionPlan, findings: List[Finding]): List[Finding] =
    plan.drain(remedies, findings)(f =>
      ResolutionPlan.Residue(f.disposition.label, f.at, f.subject, f.origin, f.detail))

  /** one classified row.
    *
    * @param at the symbol a per-location selection keys on — the EXTERNAL MEMBER for a member row
    *   (that is what the subject column names), and `SymId.None` for a K9 row, which is a fact about
    *   a SITE and offers this menu nothing. */
  final case class Finding(subject: String, disposition: Disposition, sites: Int, origin: Origin,
                           at: SymId = SymId.None):
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
      case (r, d) if d.isFinding => Finding(r.key.getOrElse(r.fullName), d, r.sites, r.firstOrigin, r.symbol)
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
    * JDK namespace. Reads the POST-PIPELINE type, not a `typeMap` lookup — a scoped-out or
    * phase-less port both keep a real `java.util.List` that a table would call mapped either way
    * (§4.56's rule at its strongest). `retypedTo` guards only against a phase whose target is
    * itself `java.*` (empty today). Per SITE, not per member. An OWNED receiver or ARRAY is out of scope. */
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
