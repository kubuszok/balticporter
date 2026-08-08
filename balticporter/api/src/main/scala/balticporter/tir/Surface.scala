package balticporter.tir

/** What a run may CONCLUDE about a type it did not emit — the view that replaces bare `Program`
  * access for every non-owned question (`DESIGN.md` §8.3).
  *
  * ==Why a view and not a check==
  * A dependent module's `Program` CONTAINS its base: `resolutionRoots` parses the base's Java, so
  * every whole-program index the emitter and the funnel build spans units the base emitted and this
  * run will not. Recomputing an answer over that program is not the base's answer — the dependent's
  * own units are extra inputs — and `ENGINE-LIMITS.md` D2/D4/D5/D6 are six faces of exactly that.
  *
  * A drift CHECK cannot close it: D4's own write-up records that NOTHING in the dependent's run
  * disagrees with itself, so a check comparing recomputed answers has nothing to compare against;
  * it would have to hold the published answer too, at which point it is already reading the
  * contract and the only remaining question is whether it reads it BEFORE or AFTER emitting the
  * wrong text. So this is a construction-time restriction: ask the view, get one of three answers,
  * and an [[Answer.Unknown]] that shaped emitted text fails the run.
  *
  * ==`Program.owned` is the WRONG predicate, and that is the substrate bug==
  * `Program.owned` roots its climb on `program.units` — ALL of them, the base's included — so it is
  * a '''program-vs-JDK''' filter, not a '''mine-vs-base''' filter, and in a dependent it answers
  * `true` for every base symbol. It is still exactly right for what it is asked for there (a rename
  * must not rewrite the JDK; a `RuleScope` entry naming an external type did not fire). [[owns]] is
  * the second question, and it is the one six independent copies of a fuel-bounded climb were each
  * answering differently.
  *
  * ==The failure direction is NAMED, once==
  * Exhausting the fuel counts as '''NOT owned''': the run then asks the contract and gets an honest
  * [[Answer.Unknown]] rather than silently computing an answer over the wrong program. Deciding on a
  * guess is the failure this whole mechanism exists to prevent.
  *
  * ==The honest scope statement==
  * "A dependent answers every non-owned question from the contract" is NOT achievable. Three
  * questions have no local repair, because the base is already emitted and gone: a base type
  * collapsed to an `object` and named here in a TYPE position, a base primary this module's
  * subclass cannot reach, a base `private` member a replay needs. For those the contract buys
  * ATTRIBUTION and REFUSE-AND-COUNT — a bare typer error becomes a finding naming the module that
  * must change and which of §1's three kinds the fix is — which is a smaller claim than "answer from
  * it" and the one §4.45 measures a check by.
  *
  * ==Honest about the guarantee==
  * A future phase can still write `program.units.foreach`; Scala has no capability type here. What
  * it CANNOT get anywhere else is an answer about a base type — those live only behind this view.
  * That is the same lever `RuleScope` and `FlowPropagation` already use, and as close to structural
  * refusal as the language gives.
  */
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
    *
    * @param fatal the answer SHAPED EMITTED TEXT. `PortRun` fails on any of these, because falling
    *              back to a local derivation is exactly how `ENGINE-LIMITS.md` D4 produced three
    *              compile errors while every check in the run reported clean.
    * @param fix   which of §1's three kinds the fix is, spelled for an agent in another repository
    *              (§4.45). A finding an agent cannot classify costs it a full investigation.
    */
  final case class Gap(subject: String, why: String, module: Option[String], fatal: Boolean, fix: String):
    def render: String =
      s"${if fatal then "FATAL" else "gap"}: $subject — $why" +
        module.fold("")(m => s"  [base: $m]") + s"  [$fix]"

  /** Three answers, and the third is the point. A question about a symbol this run does not emit is
    * either answered by the base's published contract or it is NOT ANSWERED — never quietly
    * recomputed over a program the base never had.
    *
    * '''`Own` CARRIES NOTHING, and that is a deliberate departure from §8.3's sketch''' (which spelt
    * it `Own(a: A)`). An owned type's shape is what THIS run emits, and most of it — the
    * class-vs-object collapse above all — does not exist until the emitter takes the branch that
    * decides it. A view that carried the value would have to pre-compute every answer, from the same
    * indexes the emitter reads, before emission: a SECOND derivation of exactly the thing this view
    * exists to stop being derived twice, and one free to disagree with what was written. `Own` says
    * "you emit this declaration — your own derivation is the answer", which is both the truth and
    * the only shape that cannot drift. */
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
      /** The emitted primary's parameter slots, in §8.1's DESCRIPTOR grammar (`int,String`) — the
        * same source-level spelling a manifest key uses, so a contract row and a policy key are
        * never in two grammars. `Some(Descriptor.empty)` is a nilary primary.
        *
        * `None` where the declaration has no constructor question at all: a Java interface, emitted
        * as a `trait`. A type COLLAPSED to `form=object` still reports the primary the funnel
        * planned for its Java class — the collapse is what makes it unreachable, and `form` is the
        * key that says so. Reading `primary` without `form` is therefore not a complete question.
        *
        * A `?` slot is a parameter whose type this engine could not spell in the descriptor
        * grammar, and it is also what a §8.2 MARKER renders as: the marker's type is
        * companion-`protected` and is deliberately never named (`disambiguator=marker` is the fact).
        * The two are told apart by `disambiguator`, which is the only place the distinction is
        * needed. */
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
      /** THE ONE KEY A `Dropped` MEMBER ROW CARRIES: the ENGINE RULE that refused to emit it, in the
        * `Reason` grammar the decision log uses (`ctor-funnel/nilary-dropped(C11)`).
        *
        * Every other key here describes an emitted declaration, and a refused member has none. What a
        * consumer needs instead is WHOSE DECISION IT WAS, because that decides which of §1's three
        * kinds the fix is and therefore which repository it lives in: a member absent from
        * `dropMethods` is the base's POLICY and the dependent can ask for it back, while a member the
        * engine could not render is a §1(a) refusal the base must work around by hand (§1.5's
        * `inject`). Empty for every row a policy drop produced, which is the ordinary case.
        *
        * Rule-shaped and therefore whitespace-free on purpose: the `k=v` grammar quotes a value
        * containing whitespace, and a sentence here would put a quoted paragraph in a TSV column. The
        * sentence belongs in `decisions.tsv` and in the porter note, both of which the base already
        * writes. */
      refusal: String = "",
      /** `var` | `val` — this member is a java BEAN PAIR the base COLLAPSED into a property, and
        * this is the shape it emitted. Empty for every member that is what java declared, which is
        * almost all of them.
        *
        * It has to be published, and a dependent cannot derive it. The collapse verdict is
        * WHOLE-PROGRAM: `overriddenBelow` ranges over the run's descendants and `writtenSymbols`
        * over the run's assignments, and a dependent's model CONTAINS its base's units — so a
        * dependent that declares one subclass overriding the accessor, or one write of the field,
        * RE-DERIVES `Refuse` for a pair the base collapsed. Two ports that each compile alone and
        * cannot compile together (§1.5), at an EQUAL `SurfacePolicy` fingerprint, because the
        * manifest entry is identical on both sides and only the program differs.
        *
        * What the base's map said WITHOUT this key could not answer it either: a collapsed pair
        * emits a member row keyed on the PROPERTY and no rows for the accessors, and so does a
        * member java simply declared under that name. The absence of an accessor row is not
        * evidence — a `dropMethods` entry produces the same absence — so the answer is stated. */
      form: String = "",
  )

  /** '''NOT carried, and named rather than left to be discovered.''' §8.3's schema listed a
    * `promotedParam` key — "this member exists only because the funnel promoted a constructor
    * parameter", which a dependent's §4.55 pass has to see because it is a member in Scala and not
    * in Java. A member row exists for every EMITTED DECLARATION, and a promoted parameter has none:
    * it IS the class's parameter list, so the source map records no row for it and there is nothing
    * for the key to hang on. `primary=` says what the parameters ARE, which answers the constructor
    * question; the §4.55 clash question stays open until the map carries rows for engine-minted
    * members. Recorded here because a key silently absent from a schema reads as an oversight. */
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

/** The surface of a run that has NO base: every unit in the program is this run's own.
  *
  * The default parameter for every consumer, so a single-module port, a spec, a snippet and
  * `DebugEmit` all behave exactly as they did before the view existed — and so that a consumer
  * cannot take a different code path under test than it does in a port, which is how a mechanism
  * ships untested. It answers `Unknown` for a symbol it does not own (a JDK type), which is the
  * truth: no contract describes `java.lang.Object`, and nothing asks.
  *
  * It lives in `api` beside the trait because a §1(c) rule in another repository writing a spec for
  * its own phase needs a `Surface` and must not have to reach into the engine for one. */
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
