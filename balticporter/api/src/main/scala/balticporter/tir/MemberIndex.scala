package balticporter.tir

/** WHAT THE FRONTEND SAW — every executable it walked, INCLUDING the ones it was about to drop.
  *
  * ==Why this has to exist, and why it has to be the FRONTEND that publishes it==
  * A `dropMethods` key names a member that, by the time any phase runs, has no `SymId`, no `Symbol`
  * and no row in the symbol table: the frontend filters the executable out BEFORE the method symbol
  * is minted. So the key cannot be resolved against a `Program` at all, and a binder that tried
  * would report every drop that WORKED as a typo.
  *
  * The general rule, worth stating because it also explains why a dropped TYPE has no `srcmap` entry
  * (CLAUDE.md §4.56): '''policy that REMOVES something can only be bound where the thing still
  * exists, which is the frontend.''' That is what makes [[PolicyBinder]] two-stage, and this is
  * stage one.
  *
  * ==What it is deliberately NOT==
  * It is not "every member the engine can see". A member the ENGINE minted after the frontend ran —
  * a synthetic constructor, a lowered lifecycle method — is in NEITHER of the two places a policy
  * key may legitimately name, and that is exactly how [[PolicyBinder]] tells a typo (`NeverMatched`)
  * from a key naming something policy has no standing to address (`SyntheticTarget`). The two read
  * identically in a findings file and mean opposite things. Adding synthetics here would delete that
  * distinction.
  *
  * It also subsumes the mutable `matchedKeys` tally `Substitutions` carried, whose own scaladoc
  * apologised for being a mutable field on a `case class` that `copy()` silently empties.
  */
final class MemberIndex(
    /** a LIST per key, not one entry. Two members can share one identity in this grammar — a class
      * with two `static { }` blocks has two `<clinit>()`s — and a map would silently keep one of
      * them, which is the shape of every defect this index exists to prevent. */
    private val byKey: Map[MemberKey, List[MemberFacts]],
    /** every TYPE the frontend walked, by qualified name.
      *
      * A separate field and not `byKey.map(_.owner)`, because the question it answers is "did the
      * frontend SEE this owner" and a type with no executables at all would otherwise answer no.
      * That is the whole of `PolicyBinder`'s structural test for an engine-minted member — the
      * frontend walked the owner and did not record the member — and it is wrong for exactly the
      * types a derived set would omit. */
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
    *
    * A precise key is matched through `Descriptor.matches` over the overload set rather than by a
    * `Map` lookup on the whole `MemberKey`, so a parameter written QUALIFIED — which is how every
    * report shows one — names the member it obviously means. The lookup is per POLICY KEY and there
    * are tens of those in a manifest, so the scan costs nothing a run can measure. */
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
  *
  * @param sym
  *   its interned symbol — `None` for a member the frontend DROPPED, which is the whole reason this
  *   record exists rather than a `Set[MemberKey]`.
  * @param dropped
  *   did policy remove it? A dropped member is still an ANSWER: its key fired, and reporting it as
  *   never-matched is the failure this index prevents.
  */
final case class MemberFacts(sym: Option[SymId], flags: Flags, origin: Origin, dropped: Boolean)
