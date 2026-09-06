package balticporter.emit

import balticporter.tir.*

/** Maps Java's four access levels to Scala qualifiers. DESIGN.md §8.7. Cross-package overrides use
  * the nearest common ancestor package as qualifier, computed parents-first over already-rendered
  * forms. Records residue as [[Decision.Kind.WidenedVisibility]] when a declaration ships wider
  * than Java's. */
object Visibility:

  /** The level a declaration renders at. Package cases carry no qualifier string —
    * the emitter supplies its own package tail at emission time. */
  enum Vis:
    case Public
    /** Bare `private` on top-level members; `private[TopLevel]` inside nested types. */
    case Private
    case PackagePrivate
    case ProtectedPkg
    /** Cross-package `private[pkg]` where `pkg` encloses but is not the declaration's own. */
    case PrivateAt(pkg: String)
    /** Cross-package `protected[pkg]`. */
    case ProtectedAt(pkg: String)

  /** Does this level need the emitter's own package tail as a qualifier? */
  def needsOwnPackage(v: Vis): Boolean = v == Vis.PackagePrivate || v == Vis.ProtectedPkg

  private val Rule = "visibility(§8.7)"

  /** Why a declaration ships wider than Java wrote it. */
  private enum Cause(val slug: String, val why: String):
    /** `protected static` in a companion: no qualified form can express cross-package subclass access. */
    case ProtectedStatic extends Cause("protected-static",
      "java grants a cross-package subclass access to this static; scala's companion object is not " +
        "a supertype of anything, so no qualified form can express it and the port widens instead")

    /** No common ancestor package to qualify both child and parent. */
    case NoCommonAncestor extends Cause("x-pkg-protected-override",
      "this overrides a protected member declared in another package tree; a scala override may " +
        "not have weaker access privileges and no enclosing package covers both, so it ships public")

    /** JLS 8.4.8.1: cross-package pkg-private is not an override in Java; Scala has one virtual chain. */
    case PkgPrivateOverride extends Cause("x-pkg-pkg-private-override",
      "java treats this as a NEW method rather than an override (JLS 8.4.8.1) because the parent's " +
        "package-private member is not inherited across packages; scala has one virtual chain, so " +
        "the port overrides and widens — DISPATCH DIFFERS where a caller held the parent type")

    /** Qualifier segment shadowed by an enclosing type or nested package of the same name. */
    case QualifierShadowed extends Cause("qualifier-shadowed",
      "the qualifier this declaration needs is shadowed by an enclosing type or a nested package " +
        "of the same name, so it would bind to that instead and silently NARROW the boundary")

    /** Default package: no name for a qualifier to spell. */
    case NoPackage extends Cause("unnameable-package",
      "the declaration is in the default package, which has no name a qualifier can spell")

  /** Computes visibility for every non-public symbol; records widenings in `out`. */
  def plan(p: Program, out: collection.mutable.Buffer[Decision]): Map[SymId, Vis] =
    given Program = p
    val unitSyms  = p.units.map(_.symbol).toSet
    val classDefs = collection.mutable.LinkedHashMap.empty[SymId, Tree.ClassDef]
    // allClassDefs: includes method-local classes that a body walk would miss
    p.units.foreach(u => StandardTraversal.allClassDefs(u).foreach(cd => classDefs(cd.symbol) = cd))

    // Anonymous classes: carried by Tree.New, not ClassDef. Needed for
    // cross-package protected overrides in anonymous factory bodies.
    val anonParents = collection.mutable.LinkedHashMap.empty[SymId, List[SymId]]
    val anonBody    = collection.mutable.LinkedHashMap.empty[SymId, List[Statement]]
    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s)      => Some(s)
      case TypeRepr.AppliedType(tc, _) => headSym(tc)
      case _                           => None
    p.units.foreach { u =>
      StandardTraversal.scanClassDef(u, ()) { (_, t) =>
        t match
          case n: Tree.New => n.anon.foreach { a =>
            anonParents(a.symbol) = headSym(n.tpt.tpe).toList
            anonBody(a.symbol)    = a.body
          }
          case _ => ()
      }
    }

    def symOf(id: SymId): Option[Symbol] = p.symbolOf(id)

    /** The top-level unit a symbol belongs to, by ownership climb. */
    val topOfMemo = collection.mutable.Map.empty[SymId, Option[SymId]]
    def topOf(id: SymId): Option[SymId] = topOfMemo.getOrElseUpdate(id, {
      def climb(x: SymId, fuel: Int): Option[SymId] =
        if fuel <= 0 || x == SymId.None then None
        else if unitSyms(x) then Some(x)
        else symOf(x).flatMap(s => climb(s.owner, fuel - 1))
      climb(id, 64)
    })

    /** Emitted package of a declaration; `""` in the default package. */
    def pkgOf(id: SymId): String =
      topOf(id).flatMap(symOf).map(_.fullName) match
        case Some(f) if f.contains('.') => f.substring(0, f.lastIndexOf('.'))
        case _                          => ""

    /** Every enclosing type's simple name, for qualifier shadow detection. */
    def enclosingTypeNames(id: SymId): List[String] =
      def climb(x: SymId, fuel: Int, acc: List[String]): List[String] =
        if fuel <= 0 || x == SymId.None then acc
        else symOf(x) match
          case Some(s) if classDefs.contains(x) => climb(s.owner, fuel - 1, s.name :: acc)
          case Some(s)                          => climb(s.owner, fuel - 1, acc)
          case None                             => acc
      symOf(id).map(s => climb(s.owner, 64, Nil)).getOrElse(Nil)

    /** Whether `pkg`'s last segment resolves unambiguously as a qualifier at `id`.
      * False when shadowed by an enclosing type or a deeper segment of the same name. */
    def qualifierResolves(pkg: String, id: SymId): Boolean =
      pkg.nonEmpty && {
        val tail  = pkg.substring(pkg.lastIndexOf('.') + 1)
        val own   = pkgOf(id)
        val segs  = own.split('.').toList
        val depth = pkg.split('.').length
        !enclosingTypeNames(id).contains(tail) &&
          // cut only at a SEPARATOR (§4.56): `demo.a` must not cover `demo.abc`.
          (own == pkg || own.startsWith(pkg + ".")) &&
          segs.zipWithIndex.forall((s, i) => s != tail || i == depth - 1)
      }

    /** the last segment of a dotted package — the only form a Scala qualifier has. */
    def tailOf(pkg: String): String = pkg.substring(pkg.lastIndexOf('.') + 1)

    /** the longest package both share, `""` when they share no named ancestor. */
    def commonPkg(a: String, b: String): String =
      if a == b then a
      else
        val common = a.split('.').zip(b.split('.')).takeWhile(_ == _).map(_._1)
        common.mkString(".")

    // ---- override graph (name, arity) ----
    val parentsOf: Map[SymId, List[SymId]] =
      classDefs.view.mapValues(cd =>
        cd.parents.flatMap { case tt: TypeTree => headSym(tt.tpe); case t: Term => headSym(t.tpe) }
      ).toMap ++ anonParents

    /** Instance methods keyed by (name, arity), ALL overloads per key.
      * Keeps every candidate so the fold takes the common (widest) package.
      * Excludes statics (hide, not override) and privates (not inherited). */
    def methodsIn(body: List[Statement]): Map[(String, Int), List[SymId]] = body.collect {
      case d: Tree.DefDef
        if !symOf(d.symbol).exists(s => s.flags.isStatic || s.flags.isPrivate) =>
        (symOf(d.symbol).map(_.name).getOrElse(""), d.paramss.map(_.size).sum) -> d.symbol
    }.groupMap(_._1)(_._2)

    val methodsOf: Map[SymId, Map[(String, Int), List[SymId]]] =
      classDefs.view.mapValues(cd => methodsIn(cd.body)).toMap ++ anonBody.view.mapValues(methodsIn).toMap

    /** Nearest ancestors declaring a member at the same (name, arity), breadth-first. */
    def declaringAncestors(owner: SymId, key: (String, Int)): List[SymId] =
      def bfs(front: List[SymId], seen: Set[SymId], fuel: Int): List[SymId] =
        if front.isEmpty || fuel <= 0 then Nil
        else
          val hits = front.flatMap(c => methodsOf.getOrElse(c, Map.empty).getOrElse(key, Nil))
          if hits.nonEmpty then hits
          else
            val next = front.flatMap(c => parentsOf.getOrElse(c, Nil)).filterNot(seen).distinct
            bfs(next, seen ++ next, fuel - 1)
      bfs(parentsOf.getOrElse(owner, Nil).distinct, Set(owner), 32)

    // ---- the plan itself ----
    val decided = collection.mutable.LinkedHashMap.empty[SymId, Vis]
    val inFlight = collection.mutable.Set.empty[SymId]

    def record(id: SymId, cause: Cause, from: String, to: String): Unit =
      val s = symOf(id)
      out += Decision(
        kind       = Decision.Kind.WidenedVisibility,
        subject    = id,
        subjectFqn = s.map(_.fullName).filter(_.nonEmpty).getOrElse("?"),
        detail     = Map("cause" -> cause.slug, "from" -> from, "to" -> to, "why" -> cause.why),
        reason     = Reason.Universal(Rule),
        origin     = Decision.originOf(p, id),
      )

    /** Package the qualifier names; `""` for public or bare private. */
    def qualifierPkgOf(id: SymId, v: Vis): String = v match
      case Vis.Public                              => ""
      case Vis.Private                             => ""
      case Vis.PackagePrivate | Vis.ProtectedPkg   => pkgOf(id)
      case Vis.PrivateAt(q)                        => q
      case Vis.ProtectedAt(q)                      => q

    def visOf(id: SymId): Vis = decided.getOrElse(id, compute(id))

    def compute(id: SymId): Vis =
      if inFlight(id) then Vis.Public // a cycle in the class graph: refuse to constrain, do not hang
      else
        inFlight += id
        val v = try decide(id) finally inFlight -= id
        decided(id) = v
        v

    def decide(id: SymId): Vis =
      val s   = symOf(id).get
      val f   = s.flags
      val pkg = pkgOf(id)
      val isType = classDefs.contains(id)
      val topLevel = unitSyms(id)

      /** widen, loudly. Every path out of this function that is not the mapping goes through it. */
      def widen(c: Cause, from: String): Vis = { record(id, c, from, "public"); Vis.Public }

      // Method-local class: no modifier (JLS 14.3). Owner is not a declared type.
      if isType && !topLevel && !classDefs.contains(s.owner) then Vis.Public
      else if f.isPrivate then Vis.Private
      else if f.isProtected then
        // protected static in companion: no qualified form expresses subclass access
        if f.isStatic then widen(Cause.ProtectedStatic, "protected")
        else if pkg.isEmpty then widen(Cause.NoPackage, "protected")
        else
          val target = overrideTarget(id, s, pkg)
          if target.isEmpty then widen(Cause.NoCommonAncestor, "protected")
          else if !qualifierResolves(target, id) then widen(Cause.QualifierShadowed, "protected")
          else if target == pkg then Vis.ProtectedPkg
          else
            record(id, Cause.NoCommonAncestor, "protected", s"protected[${tailOf(target)}]")
            Vis.ProtectedAt(target)
      else if f.isPackagePrivate then
        // top-level type: Scala's `private` already means package scope
        if isType && topLevel then Vis.Private
        else if pkg.isEmpty then widen(Cause.NoPackage, "package-private")
        else
          // types hide rather than override, so only members need override-target widening
          val target = if isType then pkg else overrideTarget(id, s, pkg)
          if target.isEmpty then widen(Cause.PkgPrivateOverride, "package-private")
          else if !qualifierResolves(target, id) then widen(Cause.QualifierShadowed, "package-private")
          else if target == pkg then Vis.PackagePrivate
          else
            record(id, Cause.PkgPrivateOverride, "package-private", s"private[${tailOf(target)}]")
            Vis.PrivateAt(target)
      else Vis.Public

    /** Parents-first: common package covering all rendered parent qualifiers. `""` = widen to public. */
    def overrideTarget(id: SymId, s: Symbol, pkg: String): String =
      if !s.flags.isOverride then pkg
      else
        val owner = s.owner
        val key   = (s.name, s.info match { case TypeRepr.MethodType(ps, _, _) => ps.size; case _ => 0 })
        val parents = declaringAncestors(owner, key)
        parents.foldLeft(pkg) { (acc, pm) =>
          if acc.isEmpty then ""
          else
            val pv = visOf(pm)
            val pq = qualifierPkgOf(pm, pv)
            // parent public — widened by this plan or by a declared package move that already
            // cleared its flags (K47): the child has nothing narrower to keep. A parent public in
            // the java never reaches here (java forbids the narrower override).
            if pv == Vis.Public then ""
            else if pq.isEmpty then acc
            else commonPkg(acc, pq)
        }

    p.symbols.all.foreach { s =>
      val f = s.flags
      if (f.isPrivate || f.isProtected || f.isPackagePrivate) && topOf(s.id).isDefined then
        visOf(s.id)
    }
    decided.filterNot(_._2 == Vis.Public).toMap
