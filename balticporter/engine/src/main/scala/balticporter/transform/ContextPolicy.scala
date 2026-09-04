package balticporter.transform

import balticporter.tir.RuleScope

/** The POLICY half of [[GlobalsToImplicitsTransform]] — CLAUDE.md §1(b), one value per holder,
  * turning a static global into a threaded `using` parameter. @param holder/context/members/attach/
  * reader/boundary/sites the threading itself (an absent field is a counted residual global)
  * @param selfSupplied/retain/cache escape hatches for a caller the closure cannot reach
  * (`ENGINE-LIMITS.md` CT7/CT8) @param promoteToClass explicit class promotions @param scope the standard grammar. */
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

  /** THE HALF A DEPENDENT MAY NOT RESTATE — `ENGINE-LIMITS.md` CT8. These fields are facts about
    * the EMITTED SIGNATURES of the types this policy threads, so a base and dependent must AGREE
    * (§1.5) — except `promoteToClass`/`scope`, which compose by ENTRY since both key on types a
    * dependent may itself own. */
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

/** WHAT A DEPENDENT MAY ADD to a base's holder — `ENGINE-LIMITS.md` CT8. [[ContextHolder]] is
  * SHARED SURFACE (§1.5); `sites`/`selfSupplied` key on DECLARATIONS a dependent may itself own,
  * so an extension has no field to restate the shared half in — structural, not convention. In
  * config, a `holders` entry with no `context` block IS an extension. A holder no chain manifest
  * declares is a counted `Malformed`. */
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

/** a PER-SITE override of [[ContextBoundary]]. [[LazyInit]] changes semantics and is never a
  * default: java runs a class initialiser at first ACTIVE USE, the rewritten form at first READ of
  * the field — coinciding only when nothing else in the class is touched first, which the
  * mechanism cannot know. So it is per-site opt-in, a `DeferredInit` decision, a porter note and a
  * counted seam. */
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
