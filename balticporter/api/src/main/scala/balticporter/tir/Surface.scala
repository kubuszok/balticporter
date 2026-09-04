package balticporter.tir

/** What a run may CONCLUDE about a type it did not emit — replaces bare `Program` access for every
  * non-owned question (`DESIGN.md` §8.3). A dependent's `Program` CONTAINS its base, so
  * recomputing an answer over it is not the base's answer (`ENGINE-LIMITS.md` D2/D4/D5/D6); this
  * is a construction-time restriction — ask, get one of three [[Answer]]s, and an
  * [[Answer.Unknown]] shaping emitted text fails the run. `Program.owned` is the WRONG predicate. */
trait Surface:

  /** Does THIS run emit the declaration of `s`? The one structural climb (§4.56): the owner chain
    * reaches a unit this run CONVERTS. Fuel-bounded; exhaustion is `false`. */
  def owns(s: SymId): Boolean

  /** the units this run converts — the roots [[owns]] climbs to, and the domain every whole-program
    * index must be built over. */
  def ownedUnits: List[Tree.ClassDef]

  /** what a base published about a type it emitted. [[Answer.Own]] when this run emits it. */
  def typeShape(s: SymId): Surface.Answer[Surface.TypeShape]

  /** …and about one of its members. */
  def memberShape(s: SymId): Surface.Answer[Surface.MemberShape]

  /** Every question this run could NOT answer, in the order they were asked.
    *
    * An `Unknown` is not automatically a failure — most of them are about a base type this module
    * merely mentions. It becomes one when its answer SHAPED EMITTED TEXT, and only the consumer
    * knows that, which is why [[Gap.fatal]] is set by the asker and not here. */
  def gaps: List[Surface.Gap]

  /** Record a question that could not be answered. */
  def gap(g: Surface.Gap): Unit

object Surface:

  /** An unanswered question, with what the run did about it.
    * @param fatal the answer SHAPED EMITTED TEXT — `PortRun` fails on any of these, since a local
    *   fallback is exactly how `ENGINE-LIMITS.md` D4 produced 3 compile errors with every check clean.
    * @param fix which of §1's three kinds the fix is, for an agent in another repository (§4.45). */
  final case class Gap(subject: String, why: String, module: Option[String], fatal: Boolean, fix: String):
    def render: String =
      s"${if fatal then "FATAL" else "gap"}: $subject — $why" +
        module.fold("")(m => s"  [base: $m]") + s"  [$fix]"

  /** Three answers, and the third is the point: a question about a symbol this run does not emit
    * is either answered by the base's published contract, or NOT ANSWERED — never recomputed over
    * a program the base never had. `Own` CARRIES NOTHING (a departure from §8.3's sketch): an
    * owned type's shape does not exist until the emitter takes the branch that decides it, so
    * carrying a value would be a second derivation free to disagree with what was written. */
  enum Answer[+A]:
    /** this run emits the declaration: derive locally, and the derivation IS the answer. */
    case Own
    /** read from a base's published contract. */
    case Published(a: A, module: String)
    /** not answerable. `module` names the base it should have come from when one is identifiable —
      * a consumer that fails on this has to be able to say which repository must change (§4.45). */
    case Unknown(why: String, module: Option[String])

  object Answer:
    extension [A](a: Answer[A])
      /** the published value, if there is one. `None` for [[Answer.Own]] too — a caller that wants
        * a value must say what it does about an owned symbol, and "derive locally" is not
        * `getOrElse`. */
      def published: Option[A] = a match
        case Published(x, _) => Some(x)
        case _               => scala.None
      def module: Option[String] = a match
        case Published(_, m) => Some(m)
        case Unknown(_, m)   => m
        case Own             => scala.None

  /** How a type was EMITTED. Every name in it is an EMITTED name (§4.56): each consumer compares it
    * against emitted text — a reference, a `super[X]`, an `export` selector, a stack frame. The
    * `upstream` column of the row that carries it is the join key and stays upstream. */
  final case class TypeShape(
      /** `class` | `object` | `trait` | `annotation` | `enum-class`. `object` is the collapse an
        * all-static Java class undergoes, and it is the answer a CONSUMER needs: naming a collapsed
        * object in a type position is `ENGINE-LIMITS.md` D6's cross-module face. */
      form: String,
      /** does the emitted type have a companion `object`? `export X.*` against one that has none is
        * an error outright. */
      companion: Boolean = false,
      /** the EMITTED names the companion declares itself — what an `export` exclusion list must be
        * built from, rather than recomputed from the base's Java. */
      statics: List[String] = Nil,
      /** The emitted primary's parameter slots, in §8.1's DESCRIPTOR grammar (`int,String`) — same
        * spelling a manifest key uses. `Some(Descriptor.empty)` is nilary; `None` means no
        * constructor question (a trait). A collapsed `form=object` still reports the planned
        * primary — `form` is what makes it unreachable. A `?` slot is either an unspellable type or
        * a §8.2 marker, told apart by `disambiguator`. */
      primary: Option[Descriptor] = scala.None,
      /** WHY that one — `unique-root` | `widest-root` | `no-arg-root` | `promoted-nilary` |
        * `synthesised-primary` | `not-funnelled` | `no-constructor`. It tells a dependent what would
        * change the answer, which is the reader's next question after `primary`. */
      primaryKind: String = "",
      /** `public` | `protected` | `private`. A synthesised primary is `protected` — narrow enough
        * that no client calls a constructor Java never exposed, wide enough that a subclass in ANY
        * module reaches it from its `extends` clause. */
      primaryVis: String = "",
      /** `marker` when the funnel added a final companion-`protected` marker parameter to make the
        * primary declarable beside, and unreachable past, the class's real constructors; `none`
        * otherwise. NEVER the marker type's FQN: a companion-`protected` type is not a name any
        * consumer may resolve (`DESIGN.md` §8.1 F4). */
      disambiguator: String = "none",
      /** the emitted `def this` signatures, descriptor-spelled. */
      secondaries: List[Descriptor] = Nil,
      /** the emitted type-parameter list with bounds — what an override must COPY for a base
        * parent. */
      tparams: String = "",
      /** emitted parents, in emitted order. `diamondOverrides` picks the superclass; a dependent
        * must pick the same one. */
      parents: List[String] = Nil,
      /** `abstract`, `sealed`, `final`, `case` — sorted. */
      flags: List[String] = Nil,
      /** emitted type visibility. */
      vis: String = "public",
  ):
    /** the primary's arity, or `-1` where the type has no constructor. */
    def primaryArity: Int = primary.fold(-1)(_.arity)
    /** Was the primary SYNTHESISED — a constructor no Java declared? The one question a consumer
      * must not answer by reading `disambiguator` or an empty parameter list: a class disambiguated
      * by the marker ALONE has an empty slot list and is still synthesised. */
    def synthesised: Boolean = primaryKind == "synthesised-primary"

  /** How a MEMBER was emitted. */
  final case class MemberShape(
      /** the emitted SIMPLE name, when it differs from the upstream one — §4.55's renames, which
        * until now existed only in `decisions.tsv` and were published nowhere a consumer looks. */
      name: String = "",
      /** `public` | `protected` | `private` | `private[p]`. "May a replay reach this?" is a lookup
        * against this and not a widening of the run's own symbol table. */
      vis: String = "public",
      /** `class` | `companion`. A Java static lands in the companion; a dependent emitting
        * `Base.m()` needs the BASE's answer, not its own. */
      placement: String = "class",
      /** THE ONE KEY A `Dropped` MEMBER ROW CARRIES: the ENGINE RULE that refused to emit it, in
        * the `Reason` grammar (`ctor-funnel/nilary-dropped(C11)`). Whose decision it was decides
        * which of §1's kinds the fix is: absent from `dropMethods` is the base's POLICY; an engine
        * refusal is §1(a), worked around via `inject`. Empty for a policy drop (the ordinary case). */
      refusal: String = "",
      /** `var` | `val` — this member is a java BEAN PAIR the base COLLAPSED into a property, in
        * this shape. Empty for what java declared as-is. Must be PUBLISHED: the collapse verdict is
        * WHOLE-PROGRAM (`overriddenBelow`/`writtenSymbols` over the run's descendants), so a
        * dependent whose model CONTAINS the base's units can RE-DERIVE `Refuse` at an EQUAL
        * fingerprint (§1.5). Absence of an accessor row is not evidence (a drop looks the same). */
      form: String = "",
  )

  /** '''NOT carried, and named rather than left to be discovered.''' §8.3's schema listed a
    * `promotedParam` key — but a promoted parameter IS the class's parameter list, so the source
    * map has no row for it to hang a key on; `primary=` already answers the constructor question.
    * Named here because a key silently absent from a schema reads as an oversight. */
  private[tir] val NotCarried: List[String] = List("promotedParam")

  // ---------------------------------------------------------------------------
  // the `k=v` payload — ONE grammar, `KeyValues`, shared with the porter note
  // ---------------------------------------------------------------------------

  /** Render a type shape. Sorted throughout, and every DEFAULT is omitted: the payload is sparse by
    * design, so a row that says nothing costs one empty column rather than twelve `k=default`
    * pairs. */
  def render(t: TypeShape): String =
    val d = TypeShape("")
    KeyValues.render(List(
      Option.when(t.companion)("companion" -> "yes"),
      Option.when(t.disambiguator != d.disambiguator)("disambiguator" -> t.disambiguator),
      Option.when(t.flags.nonEmpty)("flags" -> t.flags.sorted.mkString(",")),
      Some("form" -> t.form),
      Option.when(t.parents.nonEmpty)("parents" -> t.parents.mkString(",")),
      t.primary.map(p => "primary" -> s"(${p.render})"),
      Option.when(t.primaryKind.nonEmpty)("primaryKind" -> t.primaryKind),
      Option.when(t.primaryVis.nonEmpty)("primaryVis" -> t.primaryVis),
      Option.when(t.secondaries.nonEmpty)("secondaries" -> t.secondaries.map(s => s"(${s.render})").mkString(";")),
      Option.when(t.statics.nonEmpty)("statics" -> t.statics.sorted.mkString(",")),
      Option.when(t.tparams.nonEmpty)("tparams" -> t.tparams),
      Option.when(t.vis != d.vis)("vis" -> t.vis),
    ).flatten.sortBy(_._1))

  def render(m: MemberShape): String =
    val d = MemberShape()
    KeyValues.render(List(
      Option.when(m.form.nonEmpty)("form" -> m.form),
      Option.when(m.name.nonEmpty)("name" -> m.name),
      Option.when(m.placement != d.placement)("placement" -> m.placement),
      Option.when(m.refusal.nonEmpty)("refusal" -> m.refusal),
      Option.when(m.vis != d.vis)("vis" -> m.vis),
    ).flatten.sortBy(_._1))

  /** …and back. `None` for a payload with no `form=` — which is what a schema-2 row, and every
    * member row, look like from here. Not an error: a map published by an older engine must degrade
    * PER QUESTION (`DESIGN.md` §8.3), so the caller turns this into an [[Answer.Unknown]] naming the
    * engine that wrote it, never into a wholesale `Stale`. */
  def parseType(payload: String): Option[TypeShape] =
    val kv = KeyValues.parse(payload)
    kv.get("form").map { form =>
      def list(k: String, sep: Char = ','): List[String] =
        kv.get(k).filter(_.nonEmpty).toList.flatMap(_.split(sep).toList.filter(_.nonEmpty))
      TypeShape(
        form          = form,
        companion     = kv.get("companion").contains("yes"),
        statics       = list("statics"),
        primary       = kv.get("primary").map(p => descriptorOf(p.stripPrefix("(").stripSuffix(")"))),
        primaryKind   = kv.getOrElse("primaryKind", ""),
        primaryVis    = kv.getOrElse("primaryVis", ""),
        disambiguator = kv.getOrElse("disambiguator", "none"),
        secondaries   = list("secondaries", ';').map(s => descriptorOf(s.stripPrefix("(").stripSuffix(")"))),
        tparams       = kv.getOrElse("tparams", ""),
        parents       = list("parents"),
        flags         = list("flags"),
        vis           = kv.getOrElse("vis", "public"),
      )
    }

  def parseMember(payload: String): MemberShape =
    val kv = KeyValues.parse(payload)
    MemberShape(
      name      = kv.getOrElse("name", ""),
      vis       = kv.getOrElse("vis", "public"),
      placement = kv.getOrElse("placement", "class"),
      refusal   = kv.getOrElse("refusal", ""),
      // A map published by an engine that did not carry this key answers "" — which reads as "not
      // collapsed" and would be a FABRICATED FACT if it reached a comparison (§4.6). What keeps it
      // from doing so is `PortMap.freshness`: a map written by another engine build is `Stale`, so
      // the base is refused wholesale and the question is `Unknown` rather than wrongly answered.
      form      = kv.getOrElse("form", ""),
    )

  /** A descriptor from its own rendering. Not `Descriptor.total`: a slot the publisher could not
    * spell is `Param.Unresolved` here too, and refusing the whole descriptor for it would turn one
    * unspellable parameter into "this type has no primary", which is a different and wrong claim. */
  private def descriptorOf(rendered: String): Descriptor =
    if rendered.isEmpty then Descriptor.empty
    else Descriptor(rendered.split(',').toList.map(Descriptor.paramOf))

/** The surface of a run that has NO base: every unit in the program is this run's own. The
  * default for every consumer, so a single-module port, a spec, `DebugEmit` all behave exactly as
  * before the view existed — and a consumer cannot take a different path under test than in a
  * port. Answers `Unknown` for a symbol it does not own (a JDK type), truthfully. Lives in `api`
  * so a §1(c) rule's own spec does not have to reach into the engine for a `Surface`. */
final class TrivialSurface(program: Program) extends Surface:
  private lazy val owned: Set[SymId] = program.owned
  def owns(s: SymId): Boolean        = owned(s)
  def ownedUnits: List[Tree.ClassDef] = program.units
  def typeShape(s: SymId): Surface.Answer[Surface.TypeShape] =
    if owns(s) then Surface.Answer.Own
    else Surface.Answer.Unknown("this run declares no base port, so nothing publishes a contract", scala.None)
  def memberShape(s: SymId): Surface.Answer[Surface.MemberShape] =
    if owns(s) then Surface.Answer.Own
    else Surface.Answer.Unknown("this run declares no base port, so nothing publishes a contract", scala.None)
  private val recorded = collection.mutable.ListBuffer.empty[Surface.Gap]
  def gaps: List[Surface.Gap]   = recorded.toList.distinct
  def gap(g: Surface.Gap): Unit = recorded += g
