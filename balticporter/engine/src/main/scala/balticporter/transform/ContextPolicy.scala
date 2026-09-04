package balticporter.transform

import balticporter.tir.RuleScope

/** The POLICY half of [[GlobalsToImplicitsTransform]] — CLAUDE.md §1(b), as one value per holder.
  *
  * Turns a static global into a threaded `using` parameter; which class is the context, what it is
  * called, which fields map where, and what happens at the edges are all policy here. Empty list is
  * a structural no-op.
  *
  * @param holder
  *   the class whose `static` members ARE the context, by fully-qualified UPSTREAM name.
  * @param context
  *   the type the context is threaded AS — injected (the port wrote it) or minted (the engine
  *   synthesises it).
  * @param members
  *   holder static field name → the ACCESS PATH on the context type (a dot-path). A field ABSENT
  *   from the map is a residual global, counted.
  * @param attach
  *   WHERE the clause lands. `Method` puts a trailing `(using T)` on each threaded method; `Class`
  *   puts it on the class's constructors, so instance methods summon it with no signature change.
  * @param reader
  *   how a read spells the context: `summon[T]`, or `T.apply()` for a context type that declares the
  *   `inline def apply()(using T): T` sugar.
  * @param boundary
  *   the DEFAULT for a site the closure cannot reach. `Refuse` leaves the read naming the upstream
  *   holder; `ResidualGlobal` rewrites it to the context companion's `global`. Both are counted.
  * @param sites
  *   per-site overrides, keyed by MEMBER key (`com.foo.Utils#<clinit>`).
  * @param selfSupplied
  *   THE THIRD ANSWER (CLAUDE.md §1(b), `ENGINE-LIMITS.md` CT7), keyed by TYPE FQN: *this
  *   declaration takes the context WITHOUT taking a parameter*. Constructors keep java's signature
  *   and the engine emits `private given <context> = <expr>` at the head of the body instead, for a
  *   type a FRAMEWORK instantiates (a test suite, `ServiceLoader`, a bean) with no caller to add a
  *   `using` clause to. Key upstream, value in the EMITTED namespace (§4.56) — the value is verbatim
  *   Scala the frontend never saw, so nothing rewrites it (CLAUDE.md §6: fully-qualified, no
  *   imports). Warned on structurally where derivable
  *   ([[ContextSeamCheck.Kind.UnconstructedThread]]).
  * @param retain
  *   WHAT `selfSupplied` DRAWS ON, keyed by TYPE FQN → the member NAME to emit: *this threaded type
  *   keeps its context as a readable member*. `val <name>: <context> = summon[<context>]` at the
  *   head of the threaded type's body, so a declaration outside the closure holding an instance can
  *   read `<that value>.<name>`. `Only(Set.empty)` default (§1's ADD rule) — unrestricted this would
  *   name every threaded class in every port. Must UNION across a dependent (`ENGINE-LIMITS.md` CT8).
  *   A name on a type the closure did not thread is a counted `NeverMatched`.
  * @param cache
  *   [[retain]]'s question asked where the threading attached to a STATIC METHOD instead of a
  *   constructor (an all-`static` lifecycle holder — `load`/`init`/`dispose` — has no
  *   `threadedClasses` entry for `retain` to bind). One `cache` entry mints a PRIVATE mutable holder
  *   and a PUBLIC accessor on the companion, prepends `<held> = summon[<context>]` to every threaded
  *   method's body, and a [[selfSupplied]] expression elsewhere reads `<Type>.<name>`. The accessor
  *   THROWS when unset — java's own `IllegalStateException`, never a `null` (CLAUDE.md §1). Same
  *   `Only(Set.empty)` default; a name on a type with no threaded method is `NeverMatched`.
  * @param promoteToClass
  *   traits this port allows to become `abstract class`es so they can carry a constructor clause.
  *   EXPLICIT rather than derived — what a dependent may mix in is shared surface (§1.5). A
  *   promotion demand with no entry is a counted refusal naming the trait.
  * @param scope
  *   the standard grammar. A read inside a scoped-out declaration is left exactly as it is and
  *   recorded as `ScopedOut`.
  */
final case class ContextHolder(
    holder: String,
    context: ContextType,
    members: Map[String, String] = Map.empty,
    attach: ContextAttach = ContextAttach.Method,
    reader: ContextReader = ContextReader.Summon,
    boundary: ContextBoundary = ContextBoundary.Refuse,
    sites: Map[String, ContextSite] = Map.empty,
    selfSupplied: Map[String, String] = Map.empty,
    retain: Map[String, String] = Map.empty,
    cache: Map[String, String] = Map.empty,
    promoteToClass: Set[String] = Set.empty,
    scope: RuleScope = RuleScope.everywhere,
):
  /** a stable, order-independent rendering — two modules that agree must compare equal (§1.5). */
  def fingerprint: String =
    s"$sharedSurface|${ContextHolder.perDeclaration(sites, selfSupplied, retain, cache)}"

  /** THE HALF A DEPENDENT MAY NOT RESTATE — `ENGINE-LIMITS.md` CT8.
    *
    * These fields are facts about the EMITTED SIGNATURES of the types this policy threads, so a base
    * and a dependent must AGREE on them rather than compose (§1.5) — except `promoteToClass` and
    * `scope`, which compose by ENTRY since both are keyed on types a dependent may itself own.
    */
  def sharedSurface: String =
    val ms = members.toList.sorted.map((k, v) => s"$k->$v").mkString(",")
    s"$holder|${context.token}|$ms|${attach.token}|${reader.token}|${boundary.token}|" +
      s"${promoteToClass.toList.sorted.mkString(",")}|${scope.fingerprint}"

  /** this holder with a dependent's per-declaration entries folded in; clashing keys have already
    * been refused by the caller, so this only composes. */
  def extendedBy(e: ContextHolderExtension): ContextHolder =
    copy(sites = sites ++ e.sites, selfSupplied = selfSupplied ++ e.selfSupplied,
         retain = retain ++ e.retain, cache = cache ++ e.cache)

object ContextHolder:

  /** the PER-DECLARATION half of a holder or of an extension, rendered by ONE body so a fingerprint
    * cannot depend on which side of a merge stated an entry. `selfSupplied` is DIGESTED (arbitrary
    * Scala); `retain`/`cache` are spelled out (one identifier, emitted surface). */
  private[transform] def perDeclaration(sites: Map[String, ContextSite],
                                        selfSupplied: Map[String, String],
                                        retain: Map[String, String],
                                        cache: Map[String, String]): String =
    val ss = sites.toList.map((k, v) => s"$k->${v.token}").sorted.mkString(",")
    val fs = selfSupplied.toList.map((k, v) => s"$k=>${v.hashCode.toHexString}").sorted.mkString(",")
    val rs = retain.toList.map((k, v) => s"$k~$v").sorted.mkString(",")
    // `retain` and `cache` are separate segments: two different emissions, so one type keyed in
    // both is two members, not a contradiction. Segment omitted when empty (CLAUDE.md §1(b)'s
    // no-op rule read at the fingerprint) so an unused key taxes no baseline.
    val cs = cache.toList.map((k, v) => s"$k^$v").sorted.mkString(",")
    s"$ss|$fs|$rs" + (if cs.isEmpty then "" else s"|$cs")

/** WHAT A DEPENDENT MAY ADD to a base's holder — `ENGINE-LIMITS.md` CT8.
  *
  * [[ContextHolder]] is SHARED SURFACE and lives in the base manifest (§1.5); `sites` and
  * `selfSupplied` are keyed on DECLARATIONS a dependent may itself own, so an extension states only
  * the per-declaration half and has no field to restate the shared one in — structural, not
  * convention. In config, a `holders` entry with no `context` block IS an extension.
  *
  * An extension naming a holder no manifest in the chain declares is a counted `Malformed` finding.
  */
final case class ContextHolderExtension(
    holder: String,
    sites: Map[String, ContextSite] = Map.empty,
    selfSupplied: Map[String, String] = Map.empty,
    retain: Map[String, String] = Map.empty,
    cache: Map[String, String] = Map.empty,
):
  /** `+` marks it as an EXTENSION, so it can never fingerprint-collide with the holder it names. */
  def fingerprint: String =
    s"$holder|+${ContextHolder.perDeclaration(sites, selfSupplied, retain, cache)}"

  /** every per-declaration key, for the never-fired report and for the `governs` screen. */
  def keys: Set[String] = sites.keySet ++ selfSupplied.keySet ++ retain.keySet ++ cache.keySet

/** WHERE the context type comes from: `Injected` (the port wrote the Scala by hand — member paths
  * are unvalidated, so a bad path is a compile error at that line) or `Minted` (the engine
  * synthesises one mutable member per mapped field — member paths ARE validated; a two-hop path is
  * refused at bind time, since there is no intermediate type to hang the second hop off). */
enum ContextType(val fqn: String):
  case Injected(override val fqn: String) extends ContextType(fqn)
  case Minted(override val fqn: String)   extends ContextType(fqn)

  def token: String = this match
    case Injected(f) => s"inject:$f"
    case Minted(f)   => s"mint:$f"

/** WHERE the `using` clause lands. See [[ContextHolder.attach]]. */
enum ContextAttach(val token: String):
  case Method extends ContextAttach("method")
  case Class  extends ContextAttach("class")

/** HOW a read spells the context. */
enum ContextReader(val token: String):
  case Summon extends ContextReader("summon")
  case Apply  extends ContextReader("apply")

/** the DEFAULT for a site the closure cannot reach. */
enum ContextBoundary(val token: String):
  case Refuse         extends ContextBoundary("refuse")
  case ResidualGlobal extends ContextBoundary("residual-global")

/** a PER-SITE override of [[ContextBoundary]].
  *
  * [[LazyInit]] is the one that changes semantics and it is therefore never a default: Java runs a
  * class initialiser at first ACTIVE USE of the class, and the rewritten form runs at first READ of
  * the field. The two coincide only when nothing else in the class is touched first, and the
  * mechanism cannot know that — so it is per-site opt-in, a `DeferredInit` decision, a porter note
  * and a counted seam.
  */
enum ContextSite(val token: String):
  case LazyInit       extends ContextSite("lazy-init")
  case ResidualGlobal extends ContextSite("residual-global")
  case Refuse         extends ContextSite("refuse")

object ContextSite:
  def fromToken(s: String): Option[ContextSite] = values.find(_.token == s)

object ContextAttach:
  def fromToken(s: String): Option[ContextAttach] = values.find(_.token == s)

object ContextReader:
  def fromToken(s: String): Option[ContextReader] = values.find(_.token == s)

object ContextBoundary:
  def fromToken(s: String): Option[ContextBoundary] = values.find(_.token == s)
