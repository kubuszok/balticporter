package balticporter.tir

/** WHAT THE FRONTEND SAW — every executable it walked, INCLUDING the ones it was about to drop.
  * A `dropMethods` key names a member that by the time any phase runs has no `SymId` — the
  * frontend filters it BEFORE minting one — so policy that REMOVES something can only be bound
  * where the thing still exists (CLAUDE.md §4.56), making [[PolicyBinder]] two-stage. NOT every
  * member the ENGINE minted afterward — that distinguishes `NeverMatched` from `SyntheticTarget`. */
final class MemberIndex(
    /** a LIST per key, not one entry. Two members can share one identity in this grammar — a class
      * with two `static { }` blocks has two `<clinit>()`s — and a map would silently keep one of
      * them, which is the shape of every defect this index exists to prevent. */
    private val byKey: Map[MemberKey, List[MemberFacts]],
    /** every TYPE the frontend walked, by qualified name. A separate field, not `byKey.map(_.owner)`
      * — a type with no executables would otherwise answer "not seen", which is wrong for
      * `PolicyBinder`'s structural test (frontend walked the owner, did not record the member). */
    val types: Set[String],
):

  /** exactly this identity — normally one member, occasionally more (see [[byKey]]). */
  def exact(k: MemberKey): List[MemberFacts] = byKey.getOrElse(k, Nil)

  /** every overload of `owner#name`, in a stable order — what a BARE key names, and what an
    * ambiguity report has to list. */
  def overloads(owner: String, name: String): List[(MemberKey, MemberFacts)] =
    byKey.iterator.filter((k, _) => k.owner == owner && k.name == name)
      .flatMap((k, fs) => fs.map(k -> _)).toList.sortBy(_._1.render)

  /** …for a key, precise or bare. A precise key names one identity; a bare key names the set.
    * Matched through `Descriptor.matches` over the overload set, not a `Map` lookup on the whole
    * `MemberKey`, so a QUALIFIED parameter (as every report shows one) still names the obvious
    * member. */
  def matching(k: MemberKey): List[(MemberKey, MemberFacts)] =
    if k.isBare then overloads(k.owner, k.name)
    else
      exact(k).map(f => k -> f) match
        case Nil  => overloads(k.owner, k.name)
                       .filter((k2, _) => k2.descriptor.exists(d => k.descriptor.exists(_.matches(d))))
        case hits => hits

  def all: List[(MemberKey, MemberFacts)] =
    byKey.toList.flatMap((k, fs) => fs.map(k -> _)).sortBy(_._1.render)
  def size: Int        = byKey.valuesIterator.map(_.size).sum
  def isEmpty: Boolean = byKey.isEmpty

  /** union — for a run that translates two source sets through one index. */
  def ++(that: MemberIndex): MemberIndex =
    MemberIndex(all ++ that.all, types ++ that.types)

object MemberIndex:
  /** the index of a program nobody parsed. NOT a default parameter anywhere: see `Program`. */
  val empty: MemberIndex = new MemberIndex(Map.empty, Set.empty)

  def apply(entries: Iterable[(MemberKey, MemberFacts)], types: Set[String]): MemberIndex =
    new MemberIndex(entries.groupMap(_._1)(_._2).view.mapValues(_.toList).toMap, types)

/** What the frontend knew about one executable at the moment it walked it.
  * @param sym its interned symbol — `None` for a DROPPED member, the whole reason this exists
  *   rather than a `Set[MemberKey]` @param dropped did policy remove it? A dropped member is still
  *   an ANSWER — reporting it never-matched is the failure this index prevents. */
final case class MemberFacts(sym: Option[SymId], flags: Flags, origin: Origin, dropped: Boolean)
