package balticporter.emit

import balticporter.tir.*

/** JAVA'S FOUR ACCESS LEVELS, mapped to Scala's — DESIGN.md §8.7.
  *
  * ## What this decides, and what the emitter decides
  *
  * Everything here is a §1(a) UNIVERSAL fact about the two languages; nothing in it is
  * configurable and no library name may appear in it. The split with [[TirEmitter]] is deliberate:
  *
  *   - this pass decides the LEVEL — public, private, package-private, protected — and the two
  *     cases where a qualifier cannot be the declaration's own package (a cross-package override,
  *     §2 below);
  *   - the emitter turns a level into TEXT, and it is the emitter that supplies the qualifier for
  *     the ordinary case, from the package it is CURRENTLY EMITTING INTO. That is why [[Vis]] has a
  *     `PackagePrivate` and a `ProtectedPkg` case carrying no string: the qualifier is never
  *     derived from an upstream FQN plus a rename map, which would re-create exactly the
  *     two-namespace join CLAUDE.md §4.56 exists to kill. The package rename runs LAST, so at
  *     emission the unit's package IS the emitted one and no join is needed.
  *
  * ## The mapping
  *
  * | Java | Scala | fidelity |
  * |---|---|---|
  * | `public` | *nothing* | exact |
  * | `private` member of a top-level class | `private` | exact — and never qualified (see below) |
  * | `private` member or nested type of a NESTED class | `private[TopLevel]` | exact: Java's `private` reaches throughout the top-level enclosure (JLS 6.6.1) |
  * | package-private member / ctor / nested type | `private[<emitted pkg tail>]` | near-exact |
  * | package-private TOP-LEVEL type | `private` | near-exact — a top-level `private` in Scala already means package-plus-subpackages |
  * | `protected` member / ctor / nested type | `protected[<emitted pkg tail>]` | near-exact — the package half restored, the subclass half kept |
  * | `protected static` | public + recorded widening | residual |
  *
  * Two systematic over-approximations are properties of EVERY qualified boundary rather than of any
  * declaration, so they are documented once (here and in DESIGN §8.7) and never recorded per row:
  * Scala's `private[p]` covers `p` AND its subpackages where Java's boundary is exact, and every
  * port in one namespace tree therefore shares a root package. Both only ever WIDEN, and a
  * dependent's Java could never legally have touched a base's package-private member anyway —
  * javac would have rejected it.
  *
  * ## 2. The one place the qualifier is NOT the declaration's own package
  *
  * Scala checks that an override does not have "weaker access privileges", and it compares the
  * QUALIFIERS: a child in another package can keep neither bare `protected` nor its own package's
  * qualifier over a `protected[parentPkg]` parent. It CAN name any package that ENCLOSES it, and a
  * qualifier at the nearest COMMON enclosing package satisfies both sides — so a cross-package
  * override renders `protected[<common ancestor>]` and only widens to public when the two packages
  * share no named ancestor at all. That is computed PARENTS-FIRST over ALREADY-RENDERED forms
  * (§4.55's rule applies to visibility exactly as it does to names): a grandchild must not compute
  * a narrower qualifier than the child it inherits from, and it can only avoid that by reading what
  * the child rendered rather than what its Java said.
  *
  * ## 3. What records
  *
  * The mapping itself is the visible diff, so a faithful rendering records NOTHING (§4.575's
  * altitude rule — thousands of identical rows would bury every real decision). What records is the
  * residue, all of it as [[Decision.Kind.WidenedVisibility]] for members AND types: the kind is the
  * fact that this declaration ships wider than Java wrote it, and the subject column already
  * distinguishes the two. The causes are the `cause=` pair.
  */
object Visibility:

  /** The LEVEL a declaration renders at. The two unparameterised package cases carry no string on
    * purpose — see the class doc. */
  enum Vis:
    /** no modifier. */
    case Public

    /** Java `private`. Rendered bare on a top-level class's member (qualifying it is a DIFFERENT
      * PROGRAM: a member that overrode nothing suddenly does, which scalac then demands `override`
      * for — measured as a 1-error regression) and `private[TopLevel]` inside a nested one. */
    case Private

    /** `private[<the emitter's current package tail>]`. */
    case PackagePrivate

    /** `protected[<the emitter's current package tail>]`. */
    case ProtectedPkg

    /** `private[<tail of pkg>]` where `pkg` ENCLOSES the declaration but is not its own — the
      * package-private half of §2. */
    case PrivateAt(pkg: String)

    /** `protected[<tail of pkg>]` where `pkg` encloses the declaration but is not its own — §2. */
    case ProtectedAt(pkg: String)

  /** Does this level need the emitter's own package tail as a qualifier? */
  def needsOwnPackage(v: Vis): Boolean = v == Vis.PackagePrivate || v == Vis.ProtectedPkg

  private val Rule = "visibility(§8.7)"

  /** Why a declaration ships wider than Java wrote it. Each is probe-backed; each is a RESIDUE of
    * the mapping, never one of its ordinary outcomes. */
  private enum Cause(val slug: String, val why: String):
    /** A `protected static` moves to the companion `object`, and a subclass of a class is not a
      * subclass of its companion — so `protected[pkg]` there would DENY the cross-package subclass
      * access Java grants, which is a narrowing and not a widening. Public is the only faithful
      * side to err on. */
    case ProtectedStatic extends Cause("protected-static",
      "java grants a cross-package subclass access to this static; scala's companion object is not " +
        "a supertype of anything, so no qualified form can express it and the port widens instead")

    /** The child's package and the declaring parent's share no named ancestor, so no qualifier can
      * enclose the child AND cover the parent. */
    case NoCommonAncestor extends Cause("x-pkg-protected-override",
      "this overrides a protected member declared in another package tree; a scala override may " +
        "not have weaker access privileges and no enclosing package covers both, so it ships public")

    /** Java's "a same-signature method in a different-package subclass of a package-private method
      * is NOT an override" (JLS 8.4.8.1) has no Scala form: scala demands `override`, and adding it
      * CHANGES DISPATCH, where Java kept two independent virtual chains. */
    case PkgPrivateOverride extends Cause("x-pkg-pkg-private-override",
      "java treats this as a NEW method rather than an override (JLS 8.4.8.1) because the parent's " +
        "package-private member is not inherited across packages; scala has one virtual chain, so " +
        "the port overrides and widens — DISPATCH DIFFERS where a caller held the parent type")

    /** An enclosing TYPE is named like the package segment the qualifier would use, so the
      * qualifier would bind to the CLASS and silently narrow the boundary. Loud, not silent. */
    case QualifierShadowed extends Cause("qualifier-shadowed",
      "the qualifier this declaration needs is shadowed by an enclosing type or a nested package " +
        "of the same name, so it would bind to that instead and silently NARROW the boundary")

    /** The declaration is in the DEFAULT package, which has no name and therefore no qualifier. */
    case NoPackage extends Cause("unnameable-package",
      "the declaration is in the default package, which has no name a qualifier can spell")

  /** The whole plan: every declared symbol whose emitted visibility is not simply public.
    *
    * @param p   the program as the emitter will render it — after every rename, so `fullName` is
    *            the EMITTED name and reading a package off it is not a cross-namespace join.
    * @param out the run's decision buffer; one row per declaration that ships WIDER than Java's.
    */
  def plan(p: Program, out: collection.mutable.Buffer[Decision]): Map[SymId, Vis] =
    given Program = p
    val unitSyms  = p.units.map(_.symbol).toSet
    val classDefs = collection.mutable.LinkedHashMap.empty[SymId, Tree.ClassDef]
    // `allClassDefs` and not a body recursion: a METHOD-LOCAL class (`JS-C30`) stands in a member's
    // block, so a walk over class bodies does not reach it — and a type missing from this index is
    // one `decide` answers about as if it were a MEMBER.
    p.units.foreach(u => StandardTraversal.allClassDefs(u).foreach(cd => classDefs(cd.symbol) = cd))

    /** ANONYMOUS classes, which are not `ClassDef`s at all — a `Tree.New` carries its body, so a
      * walk over class bodies finds none of them (CLAUDE.md §3, and the reason this uses
      * `StandardTraversal` rather than a second hand-rolled recursion).
      *
      * They matter here for one reason and it is not a corner case: `new Pool<T>() { protected T
      * newObject() { … } }` is how a Java library writes a factory, and every one of those is a
      * CROSS-PACKAGE `protected` override. Missed, they render with their own package's qualifier
      * over a parent qualified with ITS package — "has weaker access privileges", 14 of them in one
      * library, and every one an ERROR rather than a silent widening. */
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

    /** the TOP-LEVEL unit a symbol belongs to, by the §4.56 ownership climb — never by its name. */
    val topOfMemo = collection.mutable.Map.empty[SymId, Option[SymId]]
    def topOf(id: SymId): Option[SymId] = topOfMemo.getOrElseUpdate(id, {
      def climb(x: SymId, fuel: Int): Option[SymId] =
        if fuel <= 0 || x == SymId.None then None
        else if unitSyms(x) then Some(x)
        else symOf(x).flatMap(s => climb(s.owner, fuel - 1))
      climb(id, 64)
    })

    /** the EMITTED package of a declaration — `""` in the default package. */
    def pkgOf(id: SymId): String =
      topOf(id).flatMap(symOf).map(_.fullName) match
        case Some(f) if f.contains('.') => f.substring(0, f.lastIndexOf('.'))
        case _                          => ""

    /** every enclosing TYPE's simple name — the P12 shadow guard's input. */
    def enclosingTypeNames(id: SymId): List[String] =
      def climb(x: SymId, fuel: Int, acc: List[String]): List[String] =
        if fuel <= 0 || x == SymId.None then acc
        else symOf(x) match
          case Some(s) if classDefs.contains(x) => climb(s.owner, fuel - 1, s.name :: acc)
          case Some(s)                          => climb(s.owner, fuel - 1, acc)
          case None                             => acc
      symOf(id).map(s => climb(s.owner, 64, Nil)).getOrElse(Nil)

    /** Can `pkg`'s LAST SEGMENT be written as a qualifier inside `at`, and mean `pkg`?
      *
      * Scala's qualifier is a SIMPLE identifier resolved in the enclosing scopes, innermost first
      * (no dotted form exists in the language), so it means `pkg` only when no nearer enclosing
      * entity carries that name. Two ways it can fail, and both are silent NARROWINGS rather than
      * errors, which is why each is a guard rather than something the compile would catch:
      * an enclosing TYPE of that name, or — for a qualifier naming an ANCESTOR package — the same
      * segment reappearing deeper in the declaration's own package path. */
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

    // ---- the override graph, by name and total arity (D1's rule until §8.1's identity lands) ----
    val parentsOf: Map[SymId, List[SymId]] =
      classDefs.view.mapValues(cd =>
        cd.parents.flatMap { case tt: TypeTree => headSym(tt.tpe); case t: Term => headSym(t.tpe) }
      ).toMap ++ anonParents

    /** a class's own INSTANCE methods, keyed by name and arity. A static never overrides — it
      * hides — and a private one is not inherited at all, so neither can constrain a child. */
    def methodsIn(body: List[Statement]): Map[(String, Int), SymId] = body.collect {
      case d: Tree.DefDef
        if !symOf(d.symbol).exists(s => s.flags.isStatic || s.flags.isPrivate) =>
        (symOf(d.symbol).map(_.name).getOrElse(""), d.paramss.map(_.size).sum) -> d.symbol
    }.toMap

    val methodsOf: Map[SymId, Map[(String, Int), SymId]] =
      classDefs.view.mapValues(cd => methodsIn(cd.body)).toMap ++ anonBody.view.mapValues(methodsIn).toMap

    /** the NEAREST ancestors declaring a member this one overrides — breadth-first, so a
      * re-declaration on the way up shadows the one above it. */
    def declaringAncestors(owner: SymId, key: (String, Int)): List[SymId] =
      def bfs(front: List[SymId], seen: Set[SymId], fuel: Int): List[SymId] =
        if front.isEmpty || fuel <= 0 then Nil
        else
          val hits = front.flatMap(c => methodsOf.getOrElse(c, Map.empty).get(key))
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

    /** the PACKAGE a decided level's qualifier names — `""` for public (no boundary at all) and for
      * a bare `private`, which no override may widen past anyway. What §2's parents-first fold
      * reads. */
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

      if f.isPrivate then Vis.Private
      else if f.isProtected then
        // P8: a `protected static` — member OR nested type — moves to the companion `object`, and
        // a subclass of the class is not a subclass of its companion, so NO qualified form there
        // can express java's cross-package subclass access. Public is the only side to err on.
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
        // a package-private TOP-LEVEL type needs no qualifier at all: Scala's top-level `private`
        // already means "this package and its subpackages", which is the mapping's own answer —
        // and it is the one form that stays spellable in the default package.
        if isType && topLevel then Vis.Private
        else if pkg.isEmpty then widen(Cause.NoPackage, "package-private")
        else
          // a TYPE does not override — java hides rather than overrides — so only a member's
          // qualifier can be forced outward by what it inherits.
          val target = if isType then pkg else overrideTarget(id, s, pkg)
          if target.isEmpty then widen(Cause.PkgPrivateOverride, "package-private")
          else if !qualifierResolves(target, id) then widen(Cause.QualifierShadowed, "package-private")
          else if target == pkg then Vis.PackagePrivate
          else
            record(id, Cause.PkgPrivateOverride, "package-private", s"private[${tailOf(target)}]")
            Vis.PrivateAt(target)
      else Vis.Public

    /** §2, parents-first: the package a member's qualifier must name so that it covers every
      * already-rendered parent form AND still encloses this declaration. `""` means no enclosing
      * package can, i.e. widen to public. */
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
            // a parent that itself widened to public leaves the child nothing narrower to keep
            if pv == Vis.Public && symOf(pm).exists(x => x.flags.isProtected || x.flags.isPackagePrivate) then ""
            else if pq.isEmpty then acc
            else commonPkg(acc, pq)
        }

    p.symbols.all.foreach { s =>
      val f = s.flags
      if (f.isPrivate || f.isProtected || f.isPackagePrivate) && topOf(s.id).isDefined then
        visOf(s.id)
    }
    decided.filterNot(_._2 == Vis.Public).toMap
