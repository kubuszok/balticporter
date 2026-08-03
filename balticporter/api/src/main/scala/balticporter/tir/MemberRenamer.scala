package balticporter.tir

/** RENAME A MEMBER — and everything that must move with it (DESIGN.md §8.5).
  *
  * ==What this adds over the four §4.55 passes already in the emitter==
  * Those rename FIELDS, LOCALS and PARAMETERS — things that do not override — and the field-vs-method
  * pass renames the *field* precisely to avoid touching a method's override obligations. So the
  * engine has never renamed a METHOD, has no member-level override edges, no external-anchor
  * refusal, and no `Reason.Configured` path: all four are Universal by construction, and a policy
  * rename is not. This closes those five gaps and nothing else — it changes NAMES, never shapes.
  * An arity change (`getX()` → `x`) is the CONSUMER's tree edit; see `BeanPropertyTransform`.
  *
  * ==Reference propagation is free, and the reason is §4.55's exactness argument==
  * A rename is a SYMBOL TABLE rewrite. The emitter renders every reference through the symbol's
  * name, every reference node carries its `SymId`, and Java resolved all of this STATICALLY — so
  * each reference already points at the symbol Java chose, and re-pointing the symbol re-points
  * exactly those references and no others. There is one table rewrite for the whole request list,
  * not one per request.
  *
  * ==All of a component, or none of it==
  * Every request is expanded through [[OverrideGraph.closureOf]] before anything is written, and a
  * closure that reaches a declaration this program cannot move — an unparsed parent, a resolution
  * root's declaration — is REFUSED WHOLE and counted. Half a rename compiles and breaks a contract
  * silently, which is the class of defect this type exists to prevent.
  *
  * Requests also refuse in GROUPS. A property is two accessors and half a property is not a
  * property, so [[Request.group]] (defaulting to the policy key) says which requests stand or fall
  * together. Nothing is half-applied: refusals are decided before the single table rewrite.
  *
  * ==Collision handling DELEGATES rather than re-implements==
  * `SuffixUntilFree` is §4.55's own idiom. `DeferToEmitter` is the one that matters for a property
  * rename: the emitter's four passes still run after every transform, so a method that lands on a
  * private field's name is resolved by `TirEmitter.resolveMemberClashes` renaming the FIELD to
  * `x$field` — which is legal, and which this type does not have to duplicate. It refuses only when
  * the collision is with something those passes will NOT move: another METHOD, or a static field
  * (which lands in the companion, where none of the four passes reaches it).
  */
object MemberRenamer:

  /** what to do when the requested name is already taken in the component's classes. */
  enum OnCollision:
    /** the requested name is POLICY; landing somewhere else silently betrays it. */
    case Refuse
    /** §4.55's fresh-name idiom — append `$` until free. Applied PER REQUEST, so a caller whose
      * requests must keep a fixed relation to each other (a `x` / `x_=` pair) must not use it. */
    case SuffixUntilFree
    /** the clash is one the emitter's §4.55 passes will resolve by moving the OTHER member — true
      * exactly when every collider is a non-static FIELD. Anything else refuses. */
    case DeferToEmitter

  /** One rename asked for.
    *
    * @param member
    *   any declaration of the component; the closure finds the rest.
    * @param reason
    *   the caller's §1 classification, verbatim on every decision this produces. A policy rename is
    *   `Reason.Configured(phase, key)` with the manifest entry as the key (§4.575).
    * @param key
    *   the declared policy entry, for the finding and the note.
    * @param group
    *   requests that stand or fall together. Defaults to [[key]], which is what a per-entry policy
    *   wants; a caller with two entries that must agree names one group for both.
    */
  final case class Request(member: SymId, newName: String, reason: Reason, key: String, group: String)

  object Request:
    def apply(member: SymId, newName: String, reason: Reason, key: String): Request =
      Request(member, newName, reason, key, key)

  /** A request that was NOT applied, and why — the counted skip. `anchors` is empty for a refusal
    * that is not about the world (a collision, a conflicting request). */
  final case class Refusal(request: Request, why: String, anchors: Set[(String, String)] = Set.empty):
    def render: String = s"""${request.key}: "${request.newName}" — $why"""

  /** Expand, screen, then rewrite ONCE.
    *
    * @return
    *   the rewritten program (identical, `eq`-wise, when nothing applied) and one [[Refusal]] per
    *   request that did not.
    */
  def rename(p: Program, graph: OverrideGraph, requests: List[Request],
             onCollision: OnCollision, out: DecisionLog): (Program, List[Refusal]) =

    val refusals = collection.mutable.LinkedHashMap.empty[Request, Refusal]
    def refuse(r: Request, why: String, anchors: Set[(String, String)] = Set.empty): Unit =
      if !refusals.contains(r) then refusals(r) = Refusal(r, why, anchors)

    // ---- 1. expand every request through its closure, and screen the world ----
    val closures = collection.mutable.LinkedHashMap.empty[Request, OverrideGraph.Closure]
    requests.foreach { r =>
      if r.newName.isEmpty then
        refuse(r, "the requested name is empty, which names nothing")
      else if !p.owns(r.member) then
        refuse(r, "the request names a symbol this program REFERENCES and does not DECLARE, so " +
          "there is no declaration to rename")
      else
        val c = graph.closureOf(r.member)
        c.anchorReason(p) match
          case Some(why) => refuse(r, why, c.externalAnchors)
          case scala.None => closures(r) = c
    }

    // ---- 2. groups stand or fall together ----
    def sweepGroups(): Unit =
      var again = true
      while again do
        val broken = refusals.keys.map(_.group).toSet
        val newly  = closures.keys.filter(r => broken.contains(r.group)).toList
        newly.foreach { r =>
          closures.remove(r)
          refuse(r, s"another request in group `${r.group}` was refused, and a group is applied " +
            "whole or not at all")
        }
        again = newly.nonEmpty
    sweepGroups()

    // ---- 3. conflicting assignments: two requests, one symbol, two names ----
    // Checked before any collision work, because a symbol with two futures has no effective name
    // to hold anything against.
    val claimants = collection.mutable.Map.empty[SymId, collection.mutable.Set[Request]]
    closures.foreach((r, c) => c.members.foreach(m =>
      claimants.getOrElseUpdate(m, collection.mutable.LinkedHashSet.empty) += r))
    claimants.foreach { (m, rs) =>
      val names = rs.map(_.newName).toSet
      if names.sizeIs > 1 then
        val fqn = p.symbolOf(m).map(_.fullName).getOrElse("?")
        rs.foreach(r => refuse(r, s"`$fqn` is claimed by ${rs.size} requests asking for different " +
          s"names (${names.toList.sorted.mkString(", ")}) — a symbol has one name"))
    }
    sweepGroups()

    // ---- 4. collisions, against EFFECTIVE names, PARENTS-FIRST (§4.55) ----
    //
    // `eff` reads the PENDING assignment first, so a descendant is held against what its ancestor
    // will actually be called. Reading original names is the mistake §4.55 records: it renamed
    // `CheckBox.style` and `TextButton.style` to the same `style$shadow` and moved the collision up
    // a level. Parents-first ordering is what makes `SuffixUntilFree`'s answer stable.
    val assign = collection.mutable.Map.empty[SymId, String]
    def nameOf(m: SymId): String = p.symbolOf(m).map(_.name).getOrElse("")
    def eff(m: SymId): String    = assign.getOrElse(m, nameOf(m))

    // depth = the longest ancestor chain of the component's topmost owner; ascending order is
    // parents-first.
    val depthCache = collection.mutable.Map.empty[SymId, Int]
    def depthOf(t: SymId, fuel: Int = 64): Int = depthCache.getOrElseUpdate(t,
      if fuel <= 0 then 0
      else graph.parentsOf(t).map(x => 1 + depthOf(x, fuel - 1)).maxOption.getOrElse(0))

    val ordered = closures.toList.sortBy { (r, c) =>
      val owners = c.members.map(graph.ownerOf).filter(_ != SymId.None)
      (owners.map(depthOf(_)).minOption.getOrElse(0), r.key, r.member.raw)
    }

    /** the collision pass over the SURVIVORS, from an empty table.
      *
      * Rebuilding rather than patching is the whole point. Two requests may name members of ONE
      * component — step 3 only refuses them when they ask for DIFFERENT names — and
      * `SuffixUntilFree` writes its answer per request, so rolling one back has to undo an
      * assignment the other still needs. Patched (remove the refused request's members, then
      * backfill anything unassigned with `r.newName`), the survivor's component came out on the
      * RAW requested name: the suffix search's entire answer discarded, and the component landed on
      * the very name the search had found taken. Nothing sees that — it compiles as an ordinary
      * clash somewhere else, or not at all, and no count moves.
      *
      * Re-running is also what makes the answer honest: `collidersOf` reads PENDING assignments
      * through `eff`, so with fewer of them a member may keep its original name and collide
      * differently. The caller loops until no new refusal appears. */
    def collisionPass(): Unit =
      assign.clear()
      val live = ordered.filterNot((r, _) => refusals.contains(r))
      // seed with the requested names FIRST, so `eff` holds every survivor's pending name while any
      // one of them is being tested (§4.55: effective names, parents-first).
      live.foreach((r, c) => c.members.foreach(m => assign(m) = r.newName))
      live.foreach { (r, c) =>
        val owners  = c.members.map(graph.ownerOf).filter(_ != SymId.None).toList.distinct
        val visible = owners.flatMap(graph.relativesOf).distinct
        def collidersOf(nm: String): List[SymId] =
          visible.flatMap(t => graph.membersOf(t)).distinct
            .filterNot(c.members.contains)
            .filter(m => eff(m) == nm)
        onCollision match
          case OnCollision.SuffixUntilFree =>
            var fresh = r.newName
            var fuel  = 64
            while collidersOf(fresh).nonEmpty && fuel > 0 do { fresh += "$"; fuel -= 1 }
            if collidersOf(fresh).nonEmpty then
              refuse(r, s"no free name near `${r.newName}` after 64 suffixes")
            else c.members.foreach(m => assign(m) = fresh)
          case mode =>
            val colliders = collidersOf(r.newName)
            val movable   = mode == OnCollision.DeferToEmitter && colliders.forall(isMovableField(p, _))
            if colliders.nonEmpty && !movable then
              val names = colliders.flatMap(p.symbolOf).map(_.fullName).sorted.distinct
              refuse(r, s"`${r.newName}` is already taken in the component's classes by " +
                s"${names.mkString(", ")}" +
                (if mode == OnCollision.DeferToEmitter then
                   " — and at least one of them is not a member the emitter's §4.55 passes will move"
                 else ""))
      }

    // …until it settles. A refusal here (or in the group sweep it triggers) invalidates the
    // assignments made beside it, and refusals only ever GROW, so this terminates in at most one
    // round per request.
    var before = -1
    while refusals.size != before do
      before = refusals.size
      collisionPass()
      sweepGroups()

    // ---- 5. one decision per renamed DECLARATION, then ONE table rewrite ----
    val applied = closures.toList.filterNot((r, _) => refusals.contains(r))
    applied.foreach { (r, c) =>
      c.members.toList.sortBy(_.raw).foreach { m =>
        val s   = p.symbolOf(m)
        val to  = assign.getOrElse(m, r.newName)
        val fqn = s.map(_.fullName).filter(_.nonEmpty).getOrElse("?")
        out.record(Decision(
          kind       = Decision.Kind.RenamedMember,
          subject    = m,
          subjectFqn = fqn,
          detail = Map(
            "from"      -> s.map(_.name).getOrElse("?"),
            "to"        -> to,
            "owner"     -> p.symbolOf(graph.ownerOf(m)).map(_.fullName).getOrElse("?"),
            "component" -> c.members.size.toString,
            "why"       -> ("the port renames this member by policy, and every declaration of the " +
              "override component moves with it — java resolved each reference statically, so " +
              "re-pointing the symbol re-points exactly the references java meant"),
          ),
          reason = r.reason,
          origin = Decision.originOf(p, m),
        ))
      }
    }

    if assign.isEmpty then (p, refusals.values.toList)
    else
      val syms = p.symbols.all.map(s => assign.get(s.id).map(n => s.copy(name = n, fullName = renamed(s, n))).getOrElse(s))
      (p.rebuilt(symbols = SymbolTable(syms)), refusals.values.toList)

  /** A member's `fullName` is `owner#name` ([[MemberKey]]), so a rename cuts at the LAST `#` and
    * carries everything before it across verbatim (§4.56). A name that does not end in the member
    * segment is left alone rather than reconstructed: guessing an owner from a string is the trap
    * that rule is about. */
  private def renamed(s: Symbol, to: String): String =
    val i = s.fullName.lastIndexOf('#')
    if i >= 0 && s.fullName.substring(i + 1) == s.name then s.fullName.substring(0, i + 1) + to
    else s.fullName

  /** is this collider one the emitter's §4.55 passes will move out of the way?
    *
    * Exactly a NON-STATIC FIELD: `resolveMemberClashes` renames a field clashing with a method of
    * its class or any descendant, and `resolveFieldShadowing` renames one shadowing an inherited
    * member — between them, a field above, below or beside the renamed method. A STATIC field is
    * neither: it emits into the companion, which neither pass reaches. */
  private def isMovableField(p: Program, m: SymId): Boolean =
    p.symbolOf(m).exists(s => !PolicyBinder.isExecutable(s.info) && !s.flags.isStatic)
