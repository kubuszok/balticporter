package balticporter.tir

/** RENAME A MEMBER — and everything that must move with it (DESIGN.md §8.5). Closes what the four
  * §4.55 passes leave open (all Universal): METHOD renames, override edges, external-anchor
  * refusal, `Reason.Configured`. A SYMBOL TABLE rewrite — reference propagation is free since java
  * resolved statically. Expands through [[OverrideGraph.closureOf]] first: an unmovable closure
  * REFUSES WHOLE (counted); requests refuse in GROUPS ([[Request.group]]). */
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

  /** One rename asked for. @param member any declaration of the component; the closure finds the
    * rest @param reason the caller's §1 classification @param key the declared policy entry
    * @param group requests that stand or fall together, defaults to [[key]] @param detachedParents
    * external types the CALLER has already re-parented away from THIS member's hierarchy, by FQN —
    * states what the graph cannot derive (§4.56), PER REQUEST since removal is a per-class fact. */
  final case class Request(member: SymId, newName: String, reason: Reason, key: String, group: String,
                           detachedParents: Set[String] = Set.empty)

  object Request:
    def apply(member: SymId, newName: String, reason: Reason, key: String): Request =
      Request(member, newName, reason, key, key)

  /** A request that was NOT applied, and why — the counted skip. `anchors` is empty for a refusal
    * that is not about the world (a collision, a conflicting request). */
  final case class Refusal(request: Request, why: String, anchors: Set[(String, String)] = Set.empty):
    def render: String = s"""${request.key}: "${request.newName}" — $why"""

  /** Expand, screen, then rewrite ONCE. @return the rewritten program (identical, `eq`-wise, when
    * nothing applied) and one [[Refusal]] per request that did not. */
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
        val c0 = graph.closureOf(r.member)
        val c  = if r.detachedParents.isEmpty then c0
                 else c0.copy(externalAnchors =
                   c0.externalAnchors.filterNot((t, _) => r.detachedParents(t)))
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
    // `eff` reads the PENDING assignment first, so a descendant is held against what its ancestor
    // will actually be called — reading original names is §4.55's own mistake (moved a collision
    // up a level instead of resolving it). Parents-first is what keeps `SuffixUntilFree` stable.
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

    /** the collision pass over the SURVIVORS, from an empty table. Rebuilding rather than
      * patching: `SuffixUntilFree` writes its answer per request, and patching would strand a
      * survivor's component on its RAW requested name — undetected, since nothing counts it.
      * Re-running is what makes it honest: `collidersOf` reads PENDING assignments, so fewer of
      * them can change who a member collides with. The caller loops until no new refusal appears. */

    // members of a request GROUP getting the SAME target name are OVERLOADS of one java member
    // (java already ensures distinct erasures), not colliders — they coexist under the new name.
    val sameGroupMembers = collection.mutable.Map.empty[String, Set[SymId]]
    closures.foreach { (r, c) =>
      val key = r.group + "=" + r.newName
      sameGroupMembers(key) = sameGroupMembers.getOrElse(key, Set.empty) ++ c.members
    }

    def collisionPass(): Unit =
      assign.clear()
      val live = ordered.filterNot((r, _) => refusals.contains(r))
      // seed with the requested names FIRST, so `eff` holds every survivor's pending name while any
      // one of them is being tested (§4.55: effective names, parents-first).
      live.foreach((r, c) => c.members.foreach(m => assign(m) = r.newName))
      live.foreach { (r, c) =>
        val owners  = c.members.map(graph.ownerOf).filter(_ != SymId.None).toList.distinct
        val visible = owners.flatMap(graph.relativesOf).distinct
        // members of the SAME group+target are overloads of one java member — not colliders.
        val groupKey  = r.group + "=" + r.newName
        val siblings  = sameGroupMembers.getOrElse(groupKey, Set.empty)
        def collidersOf(nm: String): List[SymId] =
          visible.flatMap(t => graph.membersOf(t)).distinct
            .filterNot(m => c.members.contains(m) || siblings.contains(m))
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

  /** is this collider one the emitter's §4.55 passes will move out of the way? Exactly a
    * NON-STATIC FIELD: `resolveMemberClashes`/`resolveFieldShadowing` rename a field clashing with
    * or shadowing a method; a STATIC field emits into the companion, which neither pass reaches. */
  private def isMovableField(p: Program, m: SymId): Boolean =
    p.symbolOf(m).exists(s => !PolicyBinder.isExecutable(s.info) && !s.flags.isStatic)

  // ---- SYMBOLIC NAME SUPPORT — `@scala.annotation.targetName` (CLAUDE.md §1(b)) ----

  /** Is this a SYMBOLIC Scala member name — entirely operator characters, or `unary_`-prefixed?
    * Scala has ALPHANUMERIC and SYMBOLIC identifiers; a symbolic name on the JVM must carry
    * `@targetName` for binary compatibility and `-Werror`-clean output. */
  def isSymbolic(name: String): Boolean =
    name.nonEmpty && (isOperatorName(name) || isUnaryName(name))

  /** A name composed entirely of Scala's operator characters — `+`, `*`, `<=`, `+=`, etc.
    *
    * SLS 1.1: an operator character is a Unicode Sm/So character, or one of the seven ASCII
    * symbols `!#%&*+-/<=>?@\^|~` that are not letters, digits, underscores, whitespace, or
    * delimiters. */
  private def isOperatorName(name: String): Boolean =
    name.nonEmpty && name.forall(isOperatorChar)

  /** The four prefix operator names: `unary_-`, `unary_+`, `unary_!`, `unary_~`. */
  def isUnaryName(name: String): Boolean =
    name.startsWith("unary_") && name.length == 7 && isOperatorChar(name.charAt(6))

  /** Is this character a Scala operator character?
    *
    * Deliberately a WHITELIST matching SLS 1.1 rather than a blacklist of what is NOT an operator:
    * a blacklist is what `isPlain` was, and the failure mode of getting it wrong is a name that
    * reaches the symbol table and emits text the parser cannot read. */
  private def isOperatorChar(c: Char): Boolean =
    // the seven ASCII operator characters
    "!#%&*+-/<=>?@\\^|~".indexOf(c) >= 0 ||
      // Unicode math/symbol categories (Sm, So) — what Scala's spec includes
      (Character.getType(c) == Character.MATH_SYMBOL.toInt ||
       Character.getType(c) == Character.OTHER_SYMBOL.toInt)

  /** Is this a valid Scala MEMBER NAME — either an alphanumeric identifier (letter/digit/`_`/`$`)
    * or a symbolic one, or one of the `unary_` prefix names?
    *
    * Used by [[MemberRenameTransform]] to validate the rename target. This replaces the old
    * `isPlain` and admits both families. */
  def isValidMemberName(name: String): Boolean =
    isAlphanumericName(name) || isSymbolic(name)

  /** An ALPHANUMERIC member name — the shape `isPlain` used to test for. */
  private[tir] def isAlphanumericName(name: String): Boolean =
    name.nonEmpty && !name.head.isDigit && name.forall(c => c.isLetterOrDigit || c == '_' || c == '$')

