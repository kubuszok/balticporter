package balticporter.emit

import balticporter.catalog.{CatalogLog, JS, Obligations, Rendering, Typing}
import balticporter.core.{EngineInfo, Provenance, Substituted}
import balticporter.tir.*

/** Type-parameter/parent alignment, statement dispatch, member visibility/sealing, def/val/param rendering and control-flow boundary handling split out of TirEmitter (context diet S1). */
private[emit] trait TirEmitterMembers:
  self: TirEmitter =>

  private[emit] def typeParam(td: Tree.TypeDef): String =
    val name = esc(sym(td.symbol).name)
    td.rhs.tpe match
      case TypeRepr.TypeBounds(TypeRepr.NoType, TypeRepr.NoType) => name
      case TypeRepr.TypeBounds(lo, hi) =>
        val l = if lo == TypeRepr.NoType then "" else s" >: ${tpe(lo)}"
        val h = if hi == TypeRepr.NoType then "" else s" <: ${tpe(hi)}"
        s"$name$l$h"
      case other => s"$name <: ${tpe(other)}"

  /** The parent's promoted-constructor formal at position `n`, with the parent's OWN type
    * parameters replaced by whatever the `extends` clause supplies for them — `MapIterator<V>`'s
    * `IntMap<V>` seen from `Entries[V] extends MapIterator[V]` is `IntMap[V]`, `V` now being
    * `Entries`'. `None` when the parent is external, has no promoted constructor, or is applied at
    * a different arity than it declares. */
  private[emit] def superFormal(parent: TypeRepr, n: Int): Option[TypeRepr] =
    val actuals = parent match
      case TypeRepr.AppliedType(_, as) => as
      case _                           => Nil
    for
      tycon <- headSymOf(parent)
      pcd   <- program.definitionOf(tycon).collect { case c: Tree.ClassDef => c }
      if pcd.tparams.sizeIs == actuals.size
      p     <- plans(pcd).primaryParams.lift(n)
    // The map is built HERE rather than by `ParentSubst.of` because the question is about ONE named
    // parent applied at a checked arity, not about everything above this class.
    yield substTp(p.tpt.tpe, pcd.tparams.map(_.symbol).zip(actuals).toMap)

  /** An argument lifted into the `extends` clause whose declared type kept a wildcard fill
    * (`map$p: IntMap[?]`) but the parent's constructor formal needs a real type there. Take the
    * parent's formal under its actual instantiation when available (matches a named type parameter
    * exactly); fall back to the isolated wildcard elimination only when the parent cannot be seen
    * into. */
  private[emit] def superArg(parent: TypeRepr, a: Term, n: Int, i: Int): String =
    if !hasWildcardArg(a.tpe) then term(a, i)
    else
      val target = superFormal(parent, n).filterNot(hasWildcardArg).map(tpe)
        .getOrElse(deWildcarded(a.tpe, named = false))
      s"${term(a, i)}.asInstanceOf[$target]"

  private[emit] def parent(p: Term | TypeTree): String = p match
    case tt: TypeTree  => parentTpe(tt.tpe)
    case t: Term  => parentTpe(t.tpe)

  /** a parent type in an `extends` clause: a wildcard type argument is ILLEGAL here, so each `?` is
    * replaced with its upper bound (or `AnyRef`). Only the HEAD is a `namedInner` position — a type
    * ARGUMENT's inner-class simple name is not in scope in an `extends` clause. */
  private[emit] def parentTpe(t: TypeRepr): String = deWildcarded(t, named = true)

  /** the head symbol of a (possibly applied) type. */
  private[emit] def headSymOf(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)      => Some(s)
    case TypeRepr.AppliedType(tc, _) => headSymOf(tc)
    case _                           => None

  /** a type's own parameters paired with their declared upper bounds (`NoType` when unbounded). */
  private[emit] def declBounds(tycon: SymId): List[(SymId, TypeRepr)] =
    program.definitionOf(tycon).collect { case c: Tree.ClassDef =>
      c.tparams.map(tp => tp.symbol -> (tp.rhs.tpe match
        case TypeRepr.TypeBounds(_, hi) if hi != TypeRepr.NoType => hi
        case _                                                   => TypeRepr.NoType))
    }.getOrElse(Nil)

  /** does this type mention the given symbol anywhere — the F-bound test (`N <: Node[N,V,A]`)? */
  private[emit] def mentionsSym(t: TypeRepr, s: SymId): Boolean = t match
    case TypeRepr.TypeRef(_, x)             => x == s
    case TypeRepr.AppliedType(tc, as)       => mentionsSym(tc, s) || as.exists(mentionsSym(_, s))
    case TypeRepr.TypeBounds(lo, hi)        => mentionsSym(lo, s) || mentionsSym(hi, s)
    case TypeRepr.AndType(l, r)             => mentionsSym(l, s) || mentionsSym(r, s)
    case TypeRepr.OrType(l, r)              => mentionsSym(l, s) || mentionsSym(r, s)
    case _                                  => false

  /** ONE substitution function for the whole engine — [[ParentSubst.subst]], §4.56. */
  private[emit] def substTp(t: TypeRepr, m: Map[SymId, TypeRepr]): TypeRepr = ParentSubst.subst(t, m)

  /** Render a type with every WILDCARD argument eliminated — illegal in an `extends` clause and as
    * a cast target. A wildcard becomes its own written bound, else the type parameter's declared
    * upper bound (never a blanket `AnyRef`), resolved LEFT TO RIGHT. `named` is a Boolean rather
    * than a by-name combinator (the mutable flag would not survive a strict function parameter). */

  /** The de-wildcarding CHOICE, as types rather than text — the same decision [[deWildcarded]]
    * renders, exposed so a parent's elimination and its overrides derive from ONE answer. `None`
    * where the slot stays a wildcard (F-bounded, or nothing to fill from). */
  private[emit] def deWildcardedArgs(tc: TypeRepr, args: List[TypeRepr]): List[Option[TypeRepr]] =
    val bounds = headSymOf(tc).map(declBounds).getOrElse(Nil)
    args.zipWithIndex.foldLeft((List.empty[Option[TypeRepr]], Map.empty[SymId, TypeRepr])) {
      case ((acc, m), (a, i)) =>
        val fBounded = bounds.lift(i).exists((p, hi) => hi != TypeRepr.NoType && mentionsSym(hi, p))
        val chosen: Option[TypeRepr] = a match
          case _: TypeRepr.TypeBounds if fBounded                  => scala.None
          case TypeRepr.TypeBounds(_, hi) if hi != TypeRepr.NoType => Some(substTp(hi, m))
          case _: TypeRepr.TypeBounds =>
            bounds.lift(i).map(_._2).filter(_ != TypeRepr.NoType).map(substTp(_, m))
          case other => Some(other)
        val m2 = (bounds.lift(i), chosen) match
          case (Some((pp, _)), Some(c)) => m + (pp -> c)
          case _                        => m
        (acc :+ chosen, m2)
    }._1

  private[emit] def deWildcarded(t: TypeRepr, named: Boolean): String =
    def head(f: => String): String = if named then byName(f) else f
    t match
      case TypeRepr.AppliedType(tc, args) =>
        val bounds = headSymOf(tc).map(declBounds).getOrElse(Nil)
        val (as, _) = args.zipWithIndex.foldLeft((List.empty[String], Map.empty[SymId, TypeRepr])) {
          case ((acc, m), (a, i)) =>
            // An F-BOUNDED parameter (N extends Node<N,V,A>) cannot be eliminated: no finite type
            // satisfies the bound except a real subclass, and java does not check it at an erased
            // use while scala does. Only the wildcard asserts "some type satisfies the bound".
            val fBounded = bounds.lift(i).exists((p, hi) => hi != TypeRepr.NoType && mentionsSym(hi, p))
            val chosen: Option[TypeRepr] = a match
              case _: TypeRepr.TypeBounds if fBounded                  => scala.None
              case TypeRepr.TypeBounds(_, hi) if hi != TypeRepr.NoType => Some(substTp(hi, m))
              case _: TypeRepr.TypeBounds =>
                bounds.lift(i).map(_._2).filter(_ != TypeRepr.NoType).map(substTp(_, m))
              case other => Some(other)
            val rendered =
              if chosen.isEmpty && fBounded then "?" else chosen.map(tpe).getOrElse("scala.AnyRef")
            val m2 = (bounds.lift(i), chosen) match
              case (Some((p, _)), Some(c)) => m + (p -> c)
              case _                       => m
            (acc :+ rendered, m2)
        }
        s"${head(tpe(tc))}[${as.mkString(", ")}]"
      case _ => head(tpe(t))

  /** does this type carry a wildcard argument anywhere? */
  private[emit] def hasWildcardArg(t: TypeRepr): Boolean = t match
    case _: TypeRepr.TypeBounds      => true
    case TypeRepr.AppliedType(tc, a) => hasWildcardArg(tc) || a.exists(hasWildcardArg)
    case _                           => false

  /** a statement rendered on ONE LINE, with any comment stripped — for the two positions where a
    * newline is illegal (a `for` header's init and update clauses). */
  private[emit] def flatStat(s: Statement): String = s match
    case t: Term        => stat(Tree.uncomment(t), 0)
    case v: Tree.ValDef => stat(v.copy(leading = Nil), 0)
    case other          => stat(other, 0)

  /** THE STATEMENT RENDERING DISPATCH — §2.3(c)'s emitter surface, one of two. The obligation
    * wrapper is here, at the dispatch, so no arm can escape it (ENGINE-LIMITS F8). */
  private[emit] def stat(s: Statement, i: Int): String =
    Rendering.of(TirKinds.of(s), s.origin, s)(statArm(s, i))

  /** JS-C47 / C48 / C49 / C50 — java's four access levels, consulted once here (where the three
    * rendering arms converge) rather than once per declaration kind (ENGINE-LIMITS F8). JS-C47/C50
    * fire together: java's package-private default vs scala's public one. */
  private[emit] def declVisibility(s: Symbol, at: Origin)(using Obligations): Unit =
    // read off visPlan (the DECIDED level), never the raw java flags — Visibility.decide may widen.
    val v = visPlan.getOrElse(s.id, Visibility.Vis.Public)
    val packagePrivate = v match
      case Visibility.Vis.PackagePrivate | Visibility.Vis.PrivateAt(_) => true
      case _                                                           => false
    Obligations.consult(JS.C(47), at)(Option.when(packagePrivate)(()))
    Obligations.consult(JS.C(48), at)(Option.when(v match
      case Visibility.Vis.ProtectedPkg | Visibility.Vis.ProtectedAt(_) => true
      case _                                                           => false)(()))
    // JS-C49: java's private on a NESTED type's member reaches the whole enclosing top-level class.
    Obligations.consult(JS.C(49), at)(
      Option.when(v == Visibility.Vis.Private && s.owner != SymId.None && !topLevelSyms(s.owner))(()))
    Obligations.consult(JS.C(50), at)(Option.when(packagePrivate)(()))

  /** the program's TOP-LEVEL type symbols — one set, for the nested-owner test above. */
  private[emit] lazy val topLevelSyms: Set[SymId] = program.units.map(_.symbol).toSet

  /** the top-level type a symbol is emitted INSIDE — which is the emitted FILE, since a unit is a
    * file. `SymId.None` for anything this program does not own, and that answer is load-bearing:
    * a permitted subtype the port does not declare is one no file of ours contains. */
  private[emit] def topLevelOf(id: SymId, seen: Set[SymId] = Set.empty): SymId =
    if id == SymId.None || topLevelSyms(id) || seen(id) then id
    else program.symbolOf(id).map(s => topLevelOf(s.owner, seen + id)).getOrElse(SymId.None)

  /** every DIRECT subtype this program declares, by parent — `parentsBySym` inverted. */
  private[emit] lazy val subtypesBySym: Map[SymId, List[SymId]] =
    parentsBySym.toList.flatMap((c, ps) => ps.map(p => p -> c)).groupMap(_._1)(_._2)

  /** JS-C44 — java's `sealed`/`permits` (naming subclasses anywhere) against scala's file-scoped
    * `sealed` (containing them). `sealed` is emitted only where the program-declared subtype set
    * ACCOUNTS FOR every permitted type ([[balticporter.tir.Symbol.permits]]), never from parsed
    * survivors alone. Otherwise the type ships OPEN, recorded as a residue (`ENGINE-LIMITS.md` M6). */
  private[emit] def sealOf(cd: Tree.ClassDef, s: Symbol, i: Int): (String, String) =
    if !s.flags.isSealed then ("", "")
    else
      val mine     = topLevelOf(cd.symbol)
      val subs     = subtypesBySym.getOrElse(cd.symbol, Nil)
      val elsewhere = subs.filterNot(x => topLevelOf(x) == mine)
      // types java named that this program does not declare as a subtype at all. Compared as
      // interned ids, never names — the permits list and the emitted FQN are two namespaces (§4.56).
      val unaccounted = s.permits.filterNot(subs.contains)
      if subs.nonEmpty && elsewhere.isEmpty && unaccounted.isEmpty then ("sealed ", "")
      else
        val d = Decision(
          kind       = Decision.Kind.WidenedSeal,
          subject    = cd.symbol,
          subjectFqn = s.fullName,
          detail     = Map(
            "subtypes"  -> subs.size.toString,
            "elsewhere" -> elsewhere.size.toString,
            // reported apart: elsewhere is emitted (discoverable), unaccounted is not.
            "permitted" -> s.permits.size.toString,
            "unaccounted" -> unaccounted.size.toString,
            "why"       -> ("java sealed this type and named its permitted subclasses; scala's " +
              "`sealed` restricts extension to THIS FILE and has no `permits` clause, so a " +
              "hierarchy whose subtypes are not all emitted here ships open"),
          ),
          reason     = Reason.Universal("sealed-hierarchy(JS-C44)"),
          origin     = cd.origin,
        )
        emissionOf += d
        printedNotes += PorterNote.Printed(d.kind, d.subject, d.subjectFqn, currentUnitName)
        ("", PorterNote.render(d, ind(i)))

  /** JS-C43 — the members javac DERIVES from a record header (JLS 8.10.3), on a plain `final
    * class` not a `case class` (loses java's format/NaN/-0.0 semantics, adds surface java never
    * had). `equals`/`hashCode`/`toString` read FIELDS; the extractor reads ACCESSORS. Skipped
    * where the record already declares the member, by SIGNATURE. @return CLASS members, COMPANION
    * members, porter note. */
  private[emit] def recordMembers(cd: Tree.ClassDef, s: Symbol, i: Int): (List[String], List[String], String) =
    if !s.flags.isRecord then (Nil, Nil, "")
    else
      val comps = s.components
      val self  = esc(s.name)
      // the class's own parameters, re-declared on the extractor. Rendered through `typeParam`, the
      // same function the class header uses, so the two spellings cannot drift.
      val tpDecl = if cd.tparams.isEmpty then "" else "[" + cd.tparams.map(typeParam).mkString(", ") + "]"
      val tpArgs = if cd.tparams.isEmpty then "" else "[" + cd.tparams.map(tp => esc(sym(tp.symbol).name)).mkString(", ") + "]"
      val tpWild = if cd.tparams.isEmpty then "" else "[" + cd.tparams.map(_ => "?").mkString(", ") + "]"
      // a member the RECORD ITSELF declares, by (name, java arity) — JLS 8.10.3's own override rule.
      // ARITY is the whole signature for `hashCode()` and `toString()`, which is why they use this:
      // java cannot overload on a return type, so at arity 0 each of those names exactly one member.
      // `equals` is the one that needs more — see [[declaresEquals]].
      val declared: Set[(String, Int)] = cd.body.collect {
        case d: Tree.DefDef => (sym(d.symbol).name, d.paramss.headOption.getOrElse(Nil).size)
      }.toSet
      /** the FQN of a parameter's type head, for the two signature tests below. */
      def paramHead(ps: List[Tree.ValDef]): Option[String] = ps match
        case p :: Nil => headSymOf(p.tpt.tpe).map(x => sym(x).fullName)
        case _        => None
      /** does the record declare JAVA'S `equals` — the ONE-argument one taking `java.lang.Object`
        * (JLS 8.10.3, 8.4.9)? By SIGNATURE, not (name, arity): java resolves `equals(String)` and
        * `equals(Object)` separately, so suppressing the wrong one silently downgrades to
        * REFERENCE equality (`AnyRef.equals` stays concrete, no abstract-class error, no finding). */
      def declaresEquals: Boolean = cd.body.exists {
        case d: Tree.DefDef if sym(d.symbol).name == "equals" =>
          paramHead(d.paramss.headOption.getOrElse(Nil)).contains("java.lang.Object")
        case _ => false
      }
      val fieldTpe = cd.body.collect { case v: Tree.ValDef => v.symbol -> v.tpt.tpe }.toMap
      /** the emitted VALUE-CLASS name of a component, when it is a java primitive — the whole of
        * "does this compare, hash and print by value". Read through `TirEmitter.ScalaValueClasses`,
        * which is the one place that set is spelled. */
      def primOf(c: RecordComponent): Option[String] =
        headSymOf(fieldTpe.getOrElse(c.field, sym(c.field).info)).map(x => sym(x).fullName)
          .filter(TirEmitter.ScalaValueClasses.contains)
      def boxed(v: String): String = s"$v.asInstanceOf[java.lang.Object]"
      def eqOf(c: RecordComponent, a: String, b: String): String = primOf(c) match
        // JLS 8.10.3 names `Double.compare`/`Float.compare` for exactly these two, which is NOT what
        // `==` does at either end of the float domain.
        case Some("scala.Double") => s"java.lang.Double.compare($a, $b) == 0"
        case Some("scala.Float")  => s"java.lang.Float.compare($a, $b) == 0"
        case Some(_)              => s"$a == $b"
        case None                 => s"java.util.Objects.equals(${boxed(a)}, ${boxed(b)})"
      def hashOf(c: RecordComponent, v: String): String =
        primOf(c).flatMap(TirEmitter.RecordBoxes.get) match
          case Some(box) => s"$box.hashCode($v)"
          case None      => s"java.util.Objects.hashCode(${boxed(v)})"
      def strOf(c: RecordComponent, v: String): String = primOf(c) match
        case Some(_) => s"java.lang.String.valueOf($v)"
        case None    => s"java.lang.String.valueOf(${boxed(v)})"
      def mine(c: RecordComponent): String  = s"this.${local(c.field)}"
      def theirs(c: RecordComponent): String = s"that$$rec.${local(c.field)}"

      val eqM =
        if declaresEquals then Nil
        else if comps.isEmpty then
          List(s"${ind(i + 1)}override def equals(o$$rec: scala.Any): scala.Boolean = o$$rec.isInstanceOf[$self$tpWild]")
        else
          val cmp = comps.map(c => eqOf(c, mine(c), theirs(c))).mkString(" && ")
          List(s"${ind(i + 1)}override def equals(o$$rec: scala.Any): scala.Boolean = o$$rec match {",
               s"${ind(i + 2)}case that$$rec: $self$tpWild => $cmp",
               s"${ind(i + 2)}case _ => false",
               s"${ind(i + 1)}}")
      val hashM =
        if declared(("hashCode", 0)) then Nil
        else if comps.isEmpty then List(s"${ind(i + 1)}override def hashCode(): scala.Int = 0")
        else
          List(s"${ind(i + 1)}override def hashCode(): scala.Int = {",
               s"${ind(i + 2)}var hash$$rec: scala.Int = 0") ++
          comps.map(c => s"${ind(i + 2)}hash$$rec = hash$$rec * 31 + ${hashOf(c, mine(c))}") ++
          List(s"${ind(i + 2)}hash$$rec", s"${ind(i + 1)}}")
      val strM =
        if declared(("toString", 0)) then Nil
        else
          // the SIMPLE name as this port emits it, which is the same answer `enumDef` gives
          // `Enum.name()` and `valueOf`'s arms: a renamed declaration reports the name it now has.
          val parts = comps.map(c => s""""${c.name}=" + ${strOf(c, mine(c))}""").mkString(""" + ", " + """)
          val body  = if comps.isEmpty then s""""$self[]"""" else s""""$self[" + $parts + "]""""
          List(s"${ind(i + 1)}override def toString(): java.lang.String = $body")

      // THE EXTRACTOR - scala's half of JLS 14.30.1, deconstructing through the ACCESSORS as
      // java's record pattern does. Declined where the record already declares a colliding
      // unapply - collision meaning a STATIC single-param unapply whose param IS the record;
      // an instance unapply or a differently-typed static one cannot clash.
      val hasUnapply = cd.body.exists {
        case d: Tree.DefDef if sym(d.symbol).name == "unapply" && sym(d.symbol).flags.isStatic =>
          paramHead(d.paramss.headOption.getOrElse(Nil)).contains(s.fullName)
        case _ => false
      }
      val unap =
        if hasUnapply then Nil
        else
          val ps = comps.map(c => s"r$$rec.${local(c.accessor)}()")
          val ts = comps.map(c => tpe(fieldTpe.getOrElse(c.field, sym(c.field).info)))
          val sig = s"${ind(i + 1)}def unapply$tpDecl(r$$rec: $self$tpArgs)"
          if comps.isEmpty then List(s"$sig: scala.Boolean = true")
          // `Tuple1` and not the bare component: scala's extractor rules want a result with `_1`,
          // and an arity-1 tuple is the only product type that has exactly one.
          else if comps.sizeIs == 1 then List(s"$sig: scala.Tuple1[${ts.head}] = scala.Tuple1(${ps.head})")
          else List(s"$sig: (${ts.mkString(", ")}) = (${ps.mkString(", ")})")

      // the members the RECORD declared for itself — reported alongside the synthesised ones
      // so a reader can see java's own member is right there in the file.
      val synthesised = List("equals" -> eqM, "hashCode" -> hashM, "toString" -> strM, "unapply" -> unap)
        .collect { case (n, ms) if ms.nonEmpty => n }
      val kept = List("equals" -> eqM, "hashCode" -> hashM, "toString" -> strM, "unapply" -> unap)
        .collect { case (n, ms) if ms.isEmpty => n }
      val d = Decision(
        kind       = Decision.Kind.RecordMembers,
        subject    = cd.symbol,
        subjectFqn = s.fullName,
        detail     = Map(
          "components" -> comps.size.toString,
          "synthesised" -> (if synthesised.isEmpty then "none" else synthesised.mkString(",")),
          "declared" -> (if kept.isEmpty then "none" else kept.mkString(",")),
          // scalac emits no JVM record whatever the extends clause says, so isRecord/
          // getRecordComponents cannot be reproduced.
          "reflective" -> "isRecord=false;getRecordComponents=null",
          // the extractor's shape decides two residues no assertion here can close: a scala
          // unapply returning a tuple evaluates every accessor eagerly (java stops at the first
          // failing component) and propagates what an accessor throws (java wraps it in
          // MatchException).
          "patternAccessors" -> ("ALL, eagerly (java calls them left to right and STOPS at the " +
            "first component pattern that fails)"),
          "patternThrow" -> ("raw (java wraps an accessor's exception in java.lang.MatchException, " +
            "with the original as its cause)"),
          "why" -> ("javac derives equals/hashCode/toString from a record's components and scala " +
            "derives nothing from a plain class; a case class derives all three with different " +
            "answers, so each is written out to java's own contract"),
        ),
        reason     = Reason.Universal("record-members(JS-C43)"),
        origin     = cd.origin,
      )
      emissionOf += d
      printedNotes += PorterNote.Printed(d.kind, d.subject, d.subjectFqn, currentUnitName)
      (eqM ++ hashM ++ strM, unap, PorterNote.render(d, ind(i)))

  /** THE AREA-C ROWS A TYPE DECLARATION OWES — consulted at the dispatch, above every arm, since
    * `classDef` forks into `enumDef`/`classDef1` and a row consulted inside only one would be a
    * HOLE at the other shape. Predicates are read off the tree and symbol table rather than by
    * re-running the emitter, so a `fired` count means "this class has the shape", not "the repair
    * emitted text". */
  private[emit] def classConsults(cd: Tree.ClassDef)(using Obligations): Unit =
    val s       = sym(cd.symbol)
    val at      = cd.origin
    val plan    = if s.flags.isModule then CtorFunnel.Plan.none else plans(cd)
    val statics = cd.body.filter(isStatic)
    val inst    = cd.body.filterNot(isStatic)
    val ctors   = cd.body.collect { case d: Tree.DefDef if sym(d.symbol).name == "<init>" => d }
    val exports = parentSymsOf(cd).filter(p => staticsReachable(p))
    /** a member java runs in a class-initialisation step — a field WITH an initialiser, or a block.
      * The same predicate at both steps: JLS 12.4.2 step 9 for the static pair and 12.5 step 4 for
      * the instance one, which is exactly why the two rows below share it. */
    def stepMember(x: Statement): Boolean = x match
      case v: Tree.ValDef => v.rhs.isDefined
      case d: Tree.DefDef => isInitBlock(d)
      case _              => false
    val isEnum = s.flags.isEnum

    // -- statics: java inherits them, a companion inherits nothing ------------------------------
    Obligations.consult(JS.C(3), at)(Option.when(exports.nonEmpty && statics.nonEmpty)(()))
    Obligations.consult(JS.C(34), at)(Option.when(exports.nonEmpty)(()))

    // -- class initialisation (JLS 12.4) ---------------------------------------------------------
    Obligations.consult(JS.C(7), at)(
      Option.when(hasClinit(statics) || nearestClinitAncestor(cd.symbol).isDefined)(()))
    Obligations.consult(JS.C(10), at)(Option.when(reentrantBearers.contains(cd.symbol))(()))
    Obligations.consult(JS.C(9), at)(Option.when(statics.count(stepMember) > 1)(()))

    // -- instance creation (JLS 12.5) ------------------------------------------------------------
    Obligations.consult(JS.C(18), at)(
      Option.when(inst.exists { case v: Tree.ValDef => v.rhs.isDefined; case _ => false } &&
                  inst.exists { case d: Tree.DefDef => isInitBlock(d); case _ => false })(()))
    Obligations.consult(JS.C(13), at)(Option.when(plan.primary.isDefined || plan.isSynthesised)(()))
    Obligations.consult(JS.C(14), at)(Option.when(plan.superArgs.nonEmpty)(()))
    Obligations.consult(JS.C(19), at)(Option.when(ctors.sizeIs > 1)(()))
    Obligations.consult(JS.C(20), at)(Option.when(plan.isSynthesised)(()))
    Obligations.consult(JS.C(21), at)(Option.when(ctors.sizeIs > 1)(()))
    // JS-C51 — a `return` in the PROMOTED constructor body, which the class body has no method to
    // return from. Consulted at every class with a promoted body and fires where one really holds
    // a `return` of its own — `returnsIn` stops at a lambda, a nested `def` and an anonymous class,
    // so a listener the constructor installs is not this row's construct. See `classBodyStats`.
    Obligations.consult(JS.C(51), at)(Option.when(returnsIn(plan.primaryBody))(()))

    // -- inheritance ------------------------------------------------------------------------------
    // JS-C33's shape and not its repair: `diamondOverrides` declines on `parents.sizeIs < 2` in its
    // own first line, so this predicate is that test and the walk below it happens once.
    Obligations.consult(JS.C(33), at)(Option.when(cd.parents.sizeIs >= 2)(()))
    Obligations.consult(JS.C(44), at)(Option.when(s.flags.isSealed)(()))

    // -- records (JLS 8.10) -------------------------------------------------------------------
    Obligations.consult(JS.C(43), at)(Option.when(s.flags.isRecord)(()))

    // -- enums (JLS 8.9) ---------------------------------------------------------------------------
    Obligations.consult(JS.C(37), at)(Option.when(isEnum)(()))
    // `enumDef.hasName`'s own two disjuncts and not a third spelling of them: java's TWO namespaces
    // let a promoted constructor PARAMETER or a FIELD carry the name beside `Enum.name()`, and
    // reading only the first said "does not apply" at the shape the row is named for.
    Obligations.consult(JS.C(38), at)(Option.when(isEnum &&
      (plan.primaryParams.exists(v => sym(v.symbol).name == "name") ||
       cd.body.exists { case d: Definition => sym(d.symbol).name == "name"; case _ => false }))(()))
    Obligations.consult(JS.C(39), at)(Option.when(isEnum)(()))
    Obligations.consult(JS.C(40), at)(Option.when(isEnum && cd.enumCases.exists(_.body.nonEmpty))(()))

    // JS-G35 — scala CHECKS an F-bound where javac does not, so a naive erasure of `N <: Node[N,…]`
    // is rejected at every use. It is one decision seen from two declaration kinds (a class's formal
    // parameters and a method's), which is why the row attaches at both and why the predicate is
    // `fBounded` — stated once, called from here and from the `DefDef` case of the dispatch.
    Obligations.consult(JS.G(35), at)(Option.when(fBounded(cd.tparams))(()))

    // -- the RAW PARENT (JLS 4.8, 8.1.4) --
    // JS-G05/JS-G11 are one fold at two outcomes: `deWildcardedArgs` eliminates an illegal
    // wildcard (to its own bound, else the parameter's, else `AnyRef`) and refuses for an
    // F-bounded parameter. Consulted HERE, not at the type dispatch: the elimination runs above
    // `TirEmitter.tpe`, so the `TypeBounds` arm never sees this slot (JS-G39's rule).
    val wildcardFills = cd.parents.flatMap { p =>
      (p match { case tt: TypeTree => tt.tpe; case t: Term => t.tpe }) match
        case TypeRepr.AppliedType(tc, args) =>
          args.zip(deWildcardedArgs(tc, args)).collect { case (_: TypeRepr.TypeBounds, chosen) => chosen }
        case _ => Nil
    }
    Obligations.consult(JS.G(5), at)(Option.when(wildcardFills.exists(_.isDefined))(()))
    Obligations.consult(JS.G(11), at)(Option.when(wildcardFills.contains(scala.None))(()))

    declVisibility(s, at)

  /** does any of these type parameters mention ITSELF in its own bound — java's F-bound, which scala
    * checks at every use and javac does not (JS-G35)? */
  private[emit] def fBounded(tps: List[Tree.TypeDef]): Boolean =
    tps.exists(tp => mentionsSym(tp.rhs.tpe, tp.symbol))

  private[emit] def statArm(s: Statement, i: Int)(using Obligations): String = s match
    // a commented STATEMENT: its comments at the statement's own indent, then the statement. A
    // DEFINITION never arrives here wrapped — it carries its own `leading` field.
    case c: Tree.Commented => leading(c.leading, i) + stat(c.stmt, i)
    case c: Tree.ClassDef =>
      classConsults(c)
      classDef(c, i)
    // a Java initializer block is carried as a synthetic member; emit its BODY inline (locally { })
    // rather than a def, since orderBody has already placed it after the fields it fills. `locally`
    // is required: a bare `{ }` after a field initialised with `new T(…)` parses as that
    // constructor's anonymous-class body. JS-S25 is consulted HERE and not inside defDef, because a
    // Tree.DefDef reaches the page through two arms and the rule must sit where they converge.
    case d: Tree.DefDef   =>
      Obligations.consult(JS.S(25), d.origin)(Option.when(needsUnreachableTail(d))(()))
      // JS-C16 — an instance initialiser block; JS-C25 — override, which java does not write.
      // Consulted here (not in defDef) since an init block never carries isOverride.
      Obligations.consult(JS.C(16), d.origin)(Option.when(isInitBlock(d))(()))
      Obligations.consult(JS.C(25), d.origin)(Option.when(sym(d.symbol).flags.isOverride)(()))
      // JS-G35's other declaration kind — a method's own formal parameters carry the same F-bound.
      Obligations.consult(JS.G(35), d.origin)(Option.when(fBounded(d.tparams))(()))
      // JS-G41: an unreifiable vararg component carries java's HEAP POLLUTION and is reproduced
      // as-is (no scala image for the warning/@SafeVarargs); HeapPollutionCheck counts it.
      Obligations.consult(JS.G(41), d.origin)(
        HeapPollutionCheck.uncheckedVararg(d)(using program).map(_ => ()))
      declVisibility(sym(d.symbol), d.origin)
      if isInitBlock(d) then
        d.rhs.map(r => s"${declNotes(d.symbol, i)}${ind(i)}locally ${term(r, i)}").getOrElse("")
      else defDef(d, i)
    case v: Tree.ValDef   => valDef(v, i)
    case t: Tree.TypeDef  => s"${ind(i)}${if sym(t.symbol).flags.isOpaque then "opaque " else ""}type ${esc(sym(t.symbol).name)} = ${tpe(t.rhs.tpe)}"
    case t: Term     => ind(i) + term(t, i)

  /** ctor type-parameter substitution (Scala secondary ctors can't be generic) → their bounds. */
  private[emit] var tparamSubst: Map[SymId, TypeRepr] = Map.empty

  /** Disambiguate a member that arrives CONCRETE from both the superclass and a mixin. Java has
    * single inheritance, never ambiguous there; scala linearises and refuses. Forward to the
    * SUPERCLASS (parents-list head) — the member java would have run. A `final` superclass member
    * takes NO forwarder: minting one would override a `final` member, which scala forbids
    * (`ENGINE-LIMITS.md` K28). */
  private[emit] def diamondOverrides(cd: Tree.ClassDef, i: Int): List[String] =
    def headOf(t: TypeRepr): Option[SymId] = headSymOf(t)
    val parentTs = cd.parents.map { case tt: TypeTree => tt.tpe; case t: Term => t.tpe }
    if parentTs.sizeIs < 2 then Nil
    else
      def classOf_(t: TypeRepr): Option[Tree.ClassDef] =
        headOf(t).flatMap(x => program.definitionOf(x)).collect { case c: Tree.ClassDef => c }
      /** concrete instance methods, name -> the DefDef, walking a parent chain. */
      def externalOf(t: TypeRepr): Set[(String, List[Int])] =
        headOf(t).map(x => sym(x).fullName).flatMap(externalConcrete.get).getOrElse(Set.empty)
      def concrete(t: TypeRepr, seen: Set[SymId] = Set.empty): Map[(String, List[Int]), Tree.DefDef] =
        classOf_(t) match
          case Some(c) if !seen(c.symbol) =>
            val own = c.body.collect {
              case d: Tree.DefDef if d.rhs.isDefined && sym(d.symbol).name != "<init>" &&
                                     !sym(d.symbol).flags.isStatic =>
                (sym(d.symbol).name, d.paramss.map(_.size)) -> d
            }.toMap
            c.parents.map { case tt: TypeTree => tt.tpe; case x: Term => x.tpe }
              .foldLeft(own)((acc, pt) => concrete(pt, seen + c.symbol) ++ acc)
          case _ => Map.empty
      val sup     = concrete(parentTs.head)
      val mixins  = parentTs.tail.flatMap(t => concrete(t).keySet ++ externalOf(t)).toSet
      val ownKeys = cd.body.collect {
        case d: Tree.DefDef => (sym(d.symbol).name, d.paramss.map(_.size))
      }.toSet
      val supName = classOf_(parentTs.head).map(c => esc(sym(c.symbol).name))
      // THE FORWARDED SIGNATURE IS THE PARENT'S, AND IT IS WRITTEN IN THE PARENT'S SCOPE. `d` is a
      // `DefDef` this class does not declare, so its type parameters belong to an ancestor —
      // emitted raw, `class Impl extends Base[Leaf] with Leaf` produced `Array[T]` naming a type
      // not in scope. [[ParentSubst]] makes it exact, the same derivation `CtorFunnel` and the
      // constructor replay run (§4.56).
      val psub = ParentSubst.of(cd)(using program)
      supName.toList.flatMap { sn =>
        sup.toList
          .filter((k, d) => mixins(k) && !ownKeys(k) && !sym(d.symbol).flags.isFinal)
          .sortBy((k, _) => k._1).map { (_, d) =>
          val n   = esc(sym(d.symbol).name)
          // …AND THE MEMBER'S OWN TYPE PARAMETERS, which are not the class's and are not
          // substituted away by anything. A java `<V> V get(DataKey<V>)` forwarded without its
          // `[V]` is a method whose signature names a type nothing declares — the SAME error text
          // as the class-parameter face and a different cause, so a fix for one leaves the other.
          // The bounds go through `psub` too: a method parameter may be bounded by the CLASS's.
          val tps = if d.tparams.isEmpty then ""
                    else d.tparams.map(td => typeParam(td.copy(rhs = td.rhs.copy(
                      tpe = ParentSubst.subst(td.rhs.tpe, psub))))).mkString("[", ", ", "]")
          // substituted at the ValDef rather than rendered here, so `paramClause` still decides
          // `using` clauses, override alignment and the un-annotated arms — a second rendering of a
          // parameter list is the drift this whole change is about.
          val pss = d.paramss.map(ps => paramClause(ps.map(v =>
            v.copy(tpt = v.tpt.copy(tpe = ParentSubst.subst(v.tpt.tpe, psub)))))).mkString
          val as  = d.paramss.map(ps => ps.map(v => esc(sym(v.symbol).name)).mkString("(", ", ", ")")).mkString
          s"${ind(i)}override def $n$tps$pss: ${tpe(ParentSubst.subst(d.returnTpt.tpe, psub))} = super[$sn].$n$as"
        }
      }

  /** JS-S25 — java REJECTS unreachable code, Scala allows it, composed with `break`. A body ending
    * in java's `while(true){ … return … }` never falls through, but Scala types `while(true)` as
    * `Unit`, so a non-Unit method needs a tail java did not have. One function since the dispatch
    * consults it and `defDef` renders from it (two derivations is the F8 shape). */
  private[emit] def needsUnreachableTail(d: Tree.DefDef): Boolean =
    sym(d.symbol).name != "<init>" && !isUnitType(d.returnTpt.tpe) && d.rhs.exists(endsInInfiniteLoop)

  private[emit] def defDef(d: Tree.DefDef, i: Int)(using Obligations): String =
    val s     = sym(d.symbol)
    val isCtor = s.name == "<init>"
    val name  = if isCtor then "this" else esc(s.name)
    // a Java generic constructor (`<T extends X> C(...)`) has no Scala form — secondary ctors can't
    // be generic. Drop the type params and substitute each with its upper bound throughout the ctor.
    val savedSubst = tparamSubst
    if isCtor && d.tparams.nonEmpty then
      tparamSubst = savedSubst ++ d.tparams.map(tp => tp.symbol -> (tp.rhs.tpe match
        case TypeRepr.TypeBounds(_, hi) if hi != TypeRepr.NoType => hi
        case _ => TypeRepr.NoType)).toMap // unbounded → `Any`
    val tps   = if isCtor || d.tparams.isEmpty then "" else "[" + d.tparams.map(typeParam).mkString(", ") + "]"
    val pss   = d.paramss.map(paramClause).mkString
    val ret   = if isCtor then "" else s": ${tpe(d.returnTpt.tpe)}"
    // a Java `while(true){ … return … }` idiom: the loop never falls through, but Scala types
    // `while(true)` as Unit, so a non-Unit method needs an unreachable tail after it.
    val needsUnreachable = needsUnreachableTail(d)
    val rhs = inDeclaration {
      if isCtor then s" = ${ctorBody(d, i)}"
      else d.rhs.map(r =>
        if needsUnreachable then s" = {\n${ind(i + 1)}${term(r, i + 1)}\n${ind(i + 1)}throw new java.lang.RuntimeException(\"unreachable\")\n${ind(i)}}"
        else s" = ${term(r, i)}").getOrElse("")
    }
    tparamSubst = savedSubst // restore (ctor type-param substitution was local to this def)
    // trivia first, porter note last, member next (§4.575) — the note must not displace the licence.
    val ctorNowarn = if isCtor && orNullCtors(d.symbol) then nowarnDeprecated(i) else ""
    // a JNI `native` method no FFI phase rewrote keeps java's own spelling: `@native`, bodiless
    // (the ladder's L0; `PanamaFfiTransform` clears the flag where it rewrites, ENGINE-LIMITS P-Panama).
    val nativeAnn = if !isCtor && s.flags.isNative && d.rhs.isEmpty then s"${ind(i)}@scala.native\n" else ""
    s"${leading(d.leading, i)}${declNotes(d.symbol, i)}${annots(s, i)}$ctorNowarn$nativeAnn${ind(i)}${mods(s, privateQualifier(s.owner))}def $name$tps$pss$ret$rhs"

  /** a secondary's own statements after its delegation head, minus the ones its delegation consumed. */
  private[emit] def ctorRest(plan: CtorFunnel.Plan, cdef: Tree.DefDef, stats: List[Statement],
                             after: List[Statement], eaten: Int): List[Statement] =
    if plan.delegations.contains(cdef.symbol) then after.drop(eaten)
    else CtorFunnel.headStmt(cdef) match
      case Some(Tree.Apply(Tree.Select(_, m, _, _), _, _, _, _)) if sym(m).name == "<init>" => stats.tail
      case _ => stats

  /** Every statement `ctorBody` renders for a secondary — delegation arguments, replayed parent
    * statements, own residual body — the one list the `@nowarn` decision reads (CLAUDE.md §4.4). */
  private[emit] def ctorRendered(cd: Tree.ClassDef, cdef: Tree.DefDef): List[Statement] =
    val stats = CtorFunnel.stmtsOf(cdef)
    val plan  = plans(cd)
    val headIsDelegation = CtorFunnel.headStmt(cdef) match
      case Some(Tree.Apply(Tree.Select(_, m, _, _), _, _, _, _)) => sym(m).name == "<init>"
      case _                                                     => false
    val after = if headIsDelegation then stats.tail else stats
    val eaten = plan.delegations.get(cdef.symbol).map(_ => plan.consumed.getOrElse(cdef.symbol, 0)).getOrElse(0)
    val body  = plans.residualBody(cd, cdef).getOrElse(ctorRest(plan, cdef, stats, after, eaten))
    val delegTerms: List[Term] = plan.delegations.get(cdef.symbol).getOrElse(CtorFunnel.headStmt(cdef) match
      case Some(Tree.Apply(_, args, _, _, _)) => args
      case _                                  => Nil)
    plans.replayFor(cd, cdef).getOrElse(Nil) ++ body ++ delegTerms

  /** Loop-jump scope, as scala `boundary` nesting. `break`/`continue` need boundaries in DIFFERENT
    * places (loop / body), so when a loop needs both the body boundary is innermost and the outer
    * one must be NAMED. `None` = no enclosing loop boundary; `Some("")` = unnamed, innermost;
    * `Some(name)` = named because another boundary sits inside it. Re-pointed by `match` at the
    * CASE's own boundary (`contTarget` is not — `continue` inside a switch still continues the loop). */
  private[emit] var breakTarget: Option[String] = scala.None
  private[emit] var contTarget: Option[String]  = scala.None
  private[emit] var labelSeq = 0
  /** names the `def` that carries a lambda body containing `return`. SCOPED TO ONE DECLARATION by
    * [[inDeclaration]], never to the program, or the name would renumber under an unrelated edit
    * (ENGINE-LIMITS M10). */
  private[emit] var lambdaSeq = 0
  /** counter for lvalue-binding temporaries (`$lv1`, `$lv2`, …), scoped identically to
    * `lambdaSeq` — a compound-assignment temporary in one member must not consume another's. */
  private[emit] var lvSeq = 0

  /** run `f` with the synthetic-name counters SAVED, reset, and restored — a nested declaration
    * (an anonymous class's method inside a lambda) is its own naming scope and must not consume
    * the enclosing member's numbers. */
  private[emit] def inDeclaration[A](f: => A): A =
    val saved = lambdaSeq
    val savedLv = lvSeq
    lambdaSeq = 0
    lvSeq = 0
    try f finally { lambdaSeq = saved; lvSeq = savedLv }
  private[emit] def inLoop[A](brk: Option[String], cont: Option[String])(f: => A): A =
    val (sb, sc) = (breakTarget, contTarget)
    breakTarget = brk; contTarget = cont
    try f finally { breakTarget = sb; contTarget = sc }
  private[emit] def inSwitch[A](brk: Option[String])(f: => A): A =
    val sb = breakTarget
    breakTarget = brk
    try f finally breakTarget = sb

  /** the value-carrying `Label` a non-tail `yield` must name — a switch EXPRESSION's arm boundary.
    * Kept apart from [[breakTarget]]: a `break` carries `Unit` and a `yield` the switch's own
    * type, so one boundary cannot serve both (and JLS 15.28 keeps them from ever coexisting).
    * ALWAYS named, so nothing nearer can steal `break(v)(using n)`. */
  private[emit] var yieldTarget: Option[String] = scala.None
  private[emit] def inYield[A](y: Option[String])(f: => A): A =
    val sy = yieldTarget
    yieldTarget = y
    try f finally yieldTarget = sy

  /** java LABEL -> the scala boundary name a `break`/`continue` naming it must target. A labelled
    * jump can sit at any depth, so unlike the unlabelled ones these are looked up, not scoped. */
  private[emit] val labelBreak = collection.mutable.Map[String, String]()
  private[emit] val labelCont  = collection.mutable.Map[String, String]()

  /** Render a loop with whatever boundaries its jumps need — up to two, one around the LOOP for
    * `break`, one around the BODY for `continue`, the outer one NAMED when both are present. */

  /** A java enhanced-for BINDING is a declaration with its own type; scala's `for (x <- xs)` binds
    * at the ITERABLE's element type, and java lets them differ (`for (Object e : collection)` over
    * a raw/wildcarded `Collection`). Returns the declared type to re-bind at, or `None` when
    * scala's binding is already exact. Conservative: an unreadable element type agrees rather than
    * inventing a cast on no evidence. */
  private[emit] def widenedBinding(b: Tree.ValDef, it: Term): Option[String] =
    elementTpe(it.tpe).filter(_ != b.tpt.tpe).map(_ => tpe(b.tpt.tpe))

  /** is the enhanced-for BINDING written to inside the loop body? Java's binding is an ordinary
    * local and may be assigned; scala's generator binds a `val`. Scanned with `StandardTraversal`
    * (§3), counting `IncDec` beside `Assign`; over-approximating costs only an unneeded `var`. */
  private[emit] def reassignsBinding(body: Tree, binding: SymId): Boolean =
    given Program = program
    body match
      case t: Term => StandardTraversal.scanTerm(t, false) { (found, x) =>
        x match
          case Tree.Assign(Tree.Ident(s, _, _), _, _, _, _) if s == binding    => true
          case Tree.IncDec(Tree.Ident(s, _, _), _, _, _, _) if s == binding => true
          case _                                                            => found
      }
      case _ => false

  /** the element type of something java could put in an enhanced-for: an applied generic's single
    * argument, or an array's element. `None` = not readable, which callers must treat as no evidence
    * rather than as a difference. */
  private[emit] def elementTpe(t: TypeRepr): Option[TypeRepr] = t match
    case TypeRepr.AppliedType(_, List(el)) => Some(el)
    case _                                 => scala.None

  /** ENGINE-LIMITS K9: is the iterable's POST-PIPELINE type a JDK `Iterable` the pipeline left in
    * the java namespace? Such a type has no scala `foreach`, so `for (x <- xs)` does not compile.
    * Decided from the NODE (§4.56): external (not program-owned) AND in `java.*`/`javax.*`. A
    * retyped type is no longer in that namespace by the time the emitter runs, and arrays are
    * excluded by construction (`headSymOf` returns `None` for them). */
  /** Does an enhanced-for over this type need java's own iterator protocol (K9)? Yes for a JDK
    * iterable the pipeline KEPT, and for a PROGRAM type that reaches `java.lang.Iterable` through
    * parents this program declares while neither it nor any of them declares a `foreach` (the
    * ladder's L0: libGDX's own collections, 225 sites). A retyped or runtime type has `foreach`. */
  private[emit] def isKeptJdkIterable(iterableTpe: TypeRepr): Boolean =
    headSymOf(iterableTpe).flatMap(program.symbolOf).exists { s =>
      if program.owns(s.id) then ownedJavaIterableWithoutForeach(s.id, Set.empty)
      else
        val fqn = s.fullName
        fqn.startsWith("java.") || fqn.startsWith("javax.")
    }

  private def ownedJavaIterableWithoutForeach(id: SymId, seen: Set[SymId]): Boolean =
    if seen(id) then false
    else program.definitionOf(id) match
      case Some(cd: Tree.ClassDef) =>
        val declaresForeach = cd.body.exists {
          case d: Tree.DefDef => sym(d.symbol).name == "foreach"
          case _              => false
        }
        if declaresForeach then false
        else
          val parentIds = cd.parents.flatMap {
            case tt: TypeTree => headSymOf(tt.tpe)
            case t: Term      => headSymOf(t.tpe)
          }
          val reachesJavaIterable = parentIds.exists(pid =>
            program.symbolOf(pid).exists(_.fullName == "java.lang.Iterable"))
          reachesJavaIterable ||
            parentIds.exists(pid => program.owns(pid) && ownedJavaIterableWithoutForeach(pid, seen + id))
      case _ => false

  private[emit] def loopWithJumps(body: Tree, label: Option[String], render: (=> String) => String,
                            bodyStr: => String)(using Obligations): String =
    val lblB = label.filter(l => jumpsTo(body, l, brk = true))
    val lblC = label.filter(l => jumpsTo(body, l, brk = false))
    val hasB = breaksOut(body) || lblB.isDefined
    val hasC = continuesIn(body) || lblC.isDefined
    // JS-S01 — java's unlabelled jump binds LEXICALLY to this loop; scala's boundary.break binds to
    // the innermost Label. Fires wherever a jump really belongs to this loop.
    Obligations.consult(JS.S(1), body.origin)(Option.when(hasB || hasC)(()))
    // JS-S03 — a boundary this emitter INTERPOSES steals the enclosing loop's un-annotated jumps.
    // `&&` skips the extra traversal on loops with no jump to steal.
    val shielded = (hasB || hasC) && interposes(body)
    Obligations.consult(JS.S(3), body.origin)(Option.when(shielded)(()))
    if !hasB && !hasC then render(bodyStr)
    else
      labelSeq += 1
      val seq  = labelSeq
      // the break boundary must be named when a body boundary sits inside it, when a labelled
      // `break` names it from a nested loop, or when some construct INSIDE the body renders with a
      // boundary of its own (`interposes`) — all three put another `Label` nearer than this one.
      val bName = if hasB && (hasC || lblB.isDefined || shielded) then s"brk$$$seq" else ""
      val cName = if hasC && (lblC.isDefined || shielded) then s"cnt$$$seq" else ""
      lblB.foreach(l => labelBreak(l) = bName)
      lblC.foreach(l => labelCont(l) = cName)
      val inner =
        try inLoop(if hasB then Some(bName) else scala.None, if hasC then Some(cName) else scala.None) {
          if !hasC then bodyStr
          else if cName.isEmpty then s"scala.util.boundary { $bodyStr }"
          else s"scala.util.boundary { ($cName: scala.util.boundary.Label[scala.Unit]) ?=> $bodyStr }"
        }
        finally { lblB.foreach(labelBreak.remove); lblC.foreach(labelCont.remove) }
      val loop = render(inner)
      if !hasB then loop
      else if bName.isEmpty then s"scala.util.boundary { $loop }"
      else s"scala.util.boundary { ($bName: scala.util.boundary.Label[scala.Unit]) ?=> $loop }"

  /** does this loop body contain a construct the emitter renders with a `boundary` of ITS OWN?
    * `boundary.break(())` with no `using` resolves the innermost `Label`, so an interposed boundary
    * silently retargets an un-annotated jump — a [[Tree.Labeled]] actually broken to, or a switch
    * case's mid-case `break`. Deliberately OVER-approximates (§4.4); stops at a nested loop,
    * lambda, `def` or anonymous class. */
  private[emit] def interposes(t: Any): Boolean = t match
    case l: Tree.Labeled => labelNeedsBoundary(l) || interposes(l.stmt)
    case m: Tree.Match   =>
      interposes(m.scrutinee) ||
        m.cases.exists(c => caseNeedsBoundary(c.body) || (m.isExpr && caseYieldsOut(c.body)) ||
                            interposes(c.body))
    case _: Tree.While | _: Tree.DoWhile | _: Tree.For | _: Tree.ForEach     => false
    case _: Tree.Lambda | _: Tree.DefDef | _: Tree.AnonClass | _: Tree.ClassDef => false
    case xs: Iterable[?] => xs.exists(interposes)
    case Some(x)         => interposes(x)
    case p: Product      => p.productIterator.exists(interposes)
    case _               => false

  /** a labelled statement earns a boundary only when something actually breaks to its label —
    * java lets a label sit on a statement nobody jumps to, and an empty boundary would be noise
    * that also has to be shielded against. */
  private[emit] def labelNeedsBoundary(l: Tree.Labeled): Boolean = jumpsTo(l.stmt, l.name, brk = true)

  /** an unlabelled `break` in a switch case that is NOT the case terminator. The frontend strips a
    * trailing unlabelled `break` (it is what ends the case, which scala's `match` does anyway) and
    * lowers real fallthrough by duplicating the next case's tail — so a `break` still standing in
    * a case body means "stop HERE and leave the switch", over statements that follow it. */
  private[emit] def caseNeedsBoundary(body: Term): Boolean = breaksOut(body)

  /** a non-tail `yield` in a switch EXPRESSION's arm — the value-carrying twin of the predicate
    * above. The frontend peels the TAIL yield into the arm's value, so anything reaching this is a
    * `yield` that leaves the arm from inside an `if`, a nested block or ahead of another statement,
    * and scala has no expression-level jump to render it with. */
  private[emit] def caseYieldsOut(body: Term): Boolean = Jumps.yieldsOut(body)

  // The three predicates below say which construct a java jump BELONGS to. They live in
  // `balticporter.tir.Jumps` because the `break-catch` check has to ask the same questions of the
  // same trees (§4.4's jump-in-a-broad-catch row): two copies would be two answers, and the one
  // that is wrong is the one nothing measures.
  private[emit] def jumpsTo(t: Any, label: String, brk: Boolean): Boolean = Jumps.jumpsTo(t, label, brk)
  private[emit] def continuesIn(t: Any): Boolean = Jumps.continuesIn(t)

  /** does this subtree `return` from the construct that OWNS it? Stops at a nested `Lambda`,
    * `DefDef` or anonymous-class body, for `breaksOut`'s reason. Product reflection rather than a
    * case per node — a hand-rolled walk stopping short is how two of this project's silent
    * defects survived (CLAUDE.md §3). */
  private[emit] def returnsIn(t: Any): Boolean = t match
    case _: Tree.Return                                   => true
    case _: Tree.Lambda | _: Tree.DefDef | _: Tree.AnonClass => false // binds to the inner one
    case xs: Iterable[?]                                  => xs.exists(returnsIn)
    case Some(x)                                          => returnsIn(x)
    case p: Product                                       => p.productIterator.exists(returnsIn)
    case _                                                => false

  /** the result type to give the `def` that carries a lambda body containing `return`. TWO
    * SOURCES, tried in order (`ENGINE-LIMITS.md` I9): the node's own `resultTpt` (a converted SAM
    * method's fact), then the body (every `return` VALUELESS is a `void` lambda). `None` means DO
    * NOT REWRITE, never "use `Any`" — a guessed type compiles and means something else (M6). */
  private[emit] def lambdaResultType(lam: Tree.Lambda): Option[String] =
    lam.resultTpt.map(t => tpe(t.tpe)).orElse {
      val valued = collectReturns(lam.body).exists(_.expr.isDefined)
      Option.when(!valued)("scala.Unit")
    }

  private[emit] def collectReturns(t: Any): List[Tree.Return] = t match
    case r: Tree.Return                                   => List(r)
    case _: Tree.Lambda | _: Tree.DefDef | _: Tree.AnonClass => Nil
    case xs: Iterable[?]                                  => xs.toList.flatMap(collectReturns)
    case Some(x)                                          => collectReturns(x)
    case p: Product                                       => p.productIterator.toList.flatMap(collectReturns)
    case _                                                => Nil

  private[emit] def breaksOut(t: Any): Boolean = Jumps.breaksOut(t)

  private[emit] def isUnitType(t: TypeRepr): Boolean = t match
    case TypeRepr.TypeRef(_, s) => sym(s).fullName == "scala.Unit"
    case _ => false
  /** the method body is (or ends in) an infinite `while(true)` / `for(;;)`. */
  private[emit] def endsInInfiniteLoop(t: Term): Boolean = Tree.uncomment(t) match
    // …unless it can BREAK out. Before `break` was emitted the loop really was infinite and the
    // unreachable tail was correct; now `boundary { while (true) … }` returns normally, and the
    // synthetic `throw` after it is reached on every exit.
    case Tree.While(Tree.Literal(Constant.BoolC(true), _, _), b, _, _, _) => !breaksOut(b)
    case Tree.For(_, None, b, _, _, _, _)                                 => !breaksOut(b)
    case Tree.Block(stats, e, _, _, _) =>
      endsInInfiniteLoop(e) || (e match {
        case Tree.Literal(Constant.UnitC, _, _) => stats.lastOption.collect { case x: Term => x }.exists(endsInInfiniteLoop)
        case _ => false
      })
    case _ => false

  /** A Scala secondary constructor must delegate to `this(...)` first — never `super(...)`.
    * Keep a Java `this(args)` delegation. A leading `super(args)` becomes `this()` followed by
    * the parent constructor's own statements, when `CtorFunnel` has established that the two
    * together run exactly what `super(args)` ran; where it has not, the arguments are still lost
    * and `OmissionCheck` still counts them. */
  private[emit] def ctorBody(cdef: Tree.DefDef, i: Int): String =
    val stats  = CtorFunnel.stmtsOf(cdef)
    val replay = currentClass.flatMap(plans.replayFor(_, cdef)).getOrElse(Nil)
    // C3 item 4: post-bodies now in the primary's class body, not per-secondary.
    val inlined: List[Statement] = Nil
    // the head is read THROUGH its comments, re-emitted above the delegation that replaces it.
    val headTrivia = stats.headOption.collect { case t: Term => Tree.triviaOn(t) }.getOrElse(Nil)
    val plan  = currentClass.map(plans(_)).getOrElse(CtorFunnel.Plan.none)
    // A ROOT of a SYNTHESISED primary delegates with the whole slot list, so the leading `this.f = e`
    // statements those field values came from are dropped (Plan.consumed/delegations are ONE
    // derivation, so the drop and the written argument cannot disagree about which assignment went
    // where).
    val headIsDelegation = CtorFunnel.headStmt(cdef) match
      case Some(Tree.Apply(Tree.Select(_, m, _, _), _, _, _, _)) => sym(m).name == "<init>"
      case _                                                     => false
    val after = if headIsDelegation then stats.tail else stats
    val eaten = plan.delegations.get(cdef.symbol).map(_ => plan.consumed.getOrElse(cdef.symbol, 0)).getOrElse(0)
    val rest  = ctorRest(plan, cdef, stats, after, eaten)
    val deleg = plan.delegations.get(cdef.symbol) match
      case Some(args) =>
        val extra = currentClass.zip(plan.marker).map(markerArg(_, _)).toList
        val as    = args.zipWithIndex.map((a, k) => slotArg(a, plan.synthetic.lift(k).map(_._2), i + 1))
        s"this(${(as ++ extra).mkString(", ")})"
      case None => CtorFunnel.headStmt(cdef) match
        case Some(Tree.Apply(Tree.Select(r, m, _, _), args, _, _, _)) if sym(m).name == "<init>" =>
          r match
            case _: Tree.Super => superDelegation(args, i + 1)
            case _             => s"this(${args.map(term(_, i + 1)).mkString(", ")})"
        case _ => "this()"
    // §4.58 — a CONSUMED `this.f = e` does not disappear from the file, so its comment rides THIS
    // secondary's delegation, the one place a reader will find it.
    val eatenTrivia = after.take(eaten).collect { case t: Term => Tree.triviaOn(t) }.flatten
    // A10 / ENGINE-LIMITS C7 — PREFIX STRIP: where this constructor ESCAPES the promotion and its
    // own body BEGINS with the promoted body, the class body already ran those statements, so
    // re-emitting them duplicates. `Plans.residualBody` is the same function `promotionEscapes`
    // subtracts, so the emitter and the omission count cannot disagree.
    val body  = currentClass.flatMap(plans.residualBody(_, cdef)).getOrElse(rest)
    val carried = (if rest eq stats then Nil else headTrivia) ++ eatenTrivia
    val head  = leading(carried, i + 1) + ind(i + 1) + deleg
    // the block's END-OF-BODY comments: this rendering reconstructs braces from stmtsOf's list
    // rather than the body Tree.Block, so no other path carries this slot.
    val trail = CtorFunnel.trailingOf(cdef).map(triviaText(_, i + 1))
    val lines = (head :: (replay ++ inlined ++ body).map(stat(_, i + 1)).filter(_.trim.nonEmpty)) ++ trail
    s"{\n${joinStats(lines)}\n${ind(i)}}"

  /** A secondary constructor's `super(args)` — which scala cannot write — expressed as a
    * delegation to the PRIMARY, whose own `extends Parent(…)` makes the call. The DECISION is
    * `CtorFunnel.Plans.superCall`; this only renders it, so `OmissionCheck` can count a `Dropped`
    * super call independently of what this method lowers it to. */

  /** the DISAMBIGUATOR's argument, when the class's primary takes one. ASCRIBED, never a bare
    * `null` — an unascribed `this(null)` against an overload `C(String)` is `E051 Ambiguous
    * overload` (`ENGINE-LIMITS.md` C8); `(null: C.Funnel)` has exactly one candidate. */
  private[emit] def markerArg(cd: Tree.ClassDef, name: String): String =
    s"(null: ${typeValue(cd.symbol)}.${esc(name)})"

  /** the same ascription at a slot argument: a synthesised primary's delegation is an argument
    * list JAVA NEVER WROTE, so a root not assigning a hoisted field contributes that field's own
    * (often `null`) java initialiser — ambiguous the same way [[markerArg]] is. Declines on a
    * delegation JAVA WROTE (§4.56) and at an ABSTRACT type slot (`Null` does not conform). */
  private[emit] def slotArg(a: Term, slot: Option[TypeRepr], i: Int): String = (a, slot) match
    case (Tree.Literal(Constant.NullC, _, _), Some(t)) if !abstractSlot(t) => s"(null: ${tpe(t)})"
    // C3: an `if` with a block body in a synthesised delegation misparsed by Scala 3's
    // `this(...)` grammar -- parenthesise to delimit the expression. // ENGINE-LIMITS C3
    case (_: Tree.If, _) => s"(${term(a, i)})"
    case _               => term(a, i)

  /** does this slot's type name a TYPE PARAMETER this program declares? `Null` does not conform to
    * one, which is the same fact `CtorFunnel.javaDefault` refuses to mint a `null` for. */
  private[emit] def abstractSlot(t: TypeRepr): Boolean = t match
    case TypeRepr.TypeRef(_, s) => program.definitionOf(s).exists(_.isInstanceOf[Tree.TypeDef])
    case _                      => false

  private[emit] def superDelegation(args: List[Term], i: Int): String =
    currentClass.map(plans.superCall(_, args)).getOrElse(CtorFunnel.SuperCall.Dropped) match
      // a DISAMBIGUATED primary takes one more parameter than the slots, so the delegation writes
      // one more argument. `null` is the only value of a marker type and it inhabits nothing else,
      // which is precisely why the extra parameter removes the primary from every other
      // constructor's candidate set (`ENGINE-LIMITS.md` C8).
      case CtorFunnel.SuperCall.Positional(as) =>
        val extra = currentClass.flatMap(cc => plans(cc).marker.map(markerArg(cc, _))).toList
        s"this(${(as.map(term(_, i)) ++ extra).mkString(", ")})"
      case CtorFunnel.SuperCall.Matched(slots) =>
        val rendered = slots.map {
          case CtorFunnel.Slot.Arg(a)    => term(a, i)
          case CtorFunnel.Slot.NullAt(t) => s"null.asInstanceOf[${tpe(t)}]"
          // `Throwable(Throwable cause)` sets message = `cause == null ? null : cause.toString()`.
          // `Objects.toString(o, nullDefault)` IS that expression and evaluates `o` once, so the
          // argument needs no purity condition — a hand-written `if` would have read it twice.
          case CtorFunnel.Slot.CauseMessage(c) =>
            s"java.util.Objects.toString(${term(c, i)}, null)"
        }
        s"this(${rendered.mkString(", ")})"
      // the arguments really are lost here, and `OmissionCheck` says so on the same run
      case CtorFunnel.SuperCall.Dropped        => "this()"

  /** a parameter clause; a clause of `given` params renders as a Scala 3 `using` clause. */
  private[emit] def paramClause(ps: List[Tree.ValDef]): String =
    if ps.nonEmpty && ps.forall(p => sym(p.symbol).flags.isGiven) then s"(using ${ps.map(givenParam).mkString(", ")})"
    else s"(${ps.map(param).mkString(", ")})"

  /** A `using` parameter with NO NAME renders ANONYMOUSLY — `(using T)` — not cosmetic: a named
    * context parameter named after an emitted root package SHADOWS it and breaks every
    * fully-qualified reference in scope (this backend emits nothing else, §6). Nothing reads the
    * name (`using` resolution and `summon` never do); an empty name cannot capture a real one
    * since the frontend gives every parameter java's own name. */
  private[emit] def givenParam(v: Tree.ValDef): String =
    if sym(v.symbol).name.isEmpty then tpe(overrideAlign.getOrElse(v.symbol, v.tpt.tpe)) else param(v)

  // NOTE: Java `T...` → Scala `T*` is deferred — it also needs array-spread (`arr: _*`) at call
  // sites and overload-aware resolution, else `f(array)` calls break. Emitting the param type
  // as `Array[T]` keeps varargs callable positionally via the array.
  private[emit] def param(v: Tree.ValDef): String =
    // NO TYPE = a parameter a PHASE left for scalac to infer, for a LOWERED unbound method
    // reference (java writes such a qualifier RAW, so an annotated type would be an unusable
    // capture); a lambda parameter is the only ValDef a phase mints without one. An INJECTED
    // parent's parameter type wins over the TIR-derived one, since the injected file may declare a
    // DIFFERENT signature than java's (K35 CLOSED).
    val t = injectedOverrideTypes.getOrElse(v.symbol,
              overrideAlign.getOrElse(v.symbol, v.tpt.tpe))
    if t == TypeRepr.NoType then esc(sym(v.symbol).name)
    else s"${esc(sym(v.symbol).name)}: ${tpe(t)}"

  private[emit] def valDef(v: Tree.ValDef, i: Int)(using Obligations): String =
    // JS-S19 — java's definite assignment (JLS 16) rejects a read before assignment; scala
    // requires an initialiser instead. Row stays Partial: this closes the FIELD half only.
    Obligations.consult(JS.S(19), v.origin)(Option.when(v.rhs.isEmpty)(()))
    // the area-C rows a FIELD owes — valDef is the one arm a Tree.ValDef reaches.
    val vs      = sym(v.symbol)
    val ownerCd = program.definitionOf(vs.owner).collect { case c: Tree.ClassDef => c }
    // JS-C08 — a java CONSTANT VARIABLE is inlined by javac, triggering no class initialiser;
    // fires exactly where valDef0 renders `inline val`.
    Obligations.consult(JS.C(8), v.origin)(Option.when(v.rhs.isDefined && isJavaConstant(v, vs))(()))
    // JS-C36 — an interface field is implicitly public static final: a companion member, not abstract.
    Obligations.consult(JS.C(36), v.origin)(
      Option.when(vs.flags.isStatic && ownerCd.exists(c => sym(c.symbol).flags.isTrait))(()))
    // JS-C45 — a final FIELD's safe-publication guarantee, carried by val (a local has no such moment).
    Obligations.consult(JS.C(45), v.origin)(Option.when(!vs.flags.isMutable && ownerCd.isDefined)(()))
    // JS-C53 — java's FINAL on a field also states that no subclass may override the read; `mods`
    // carries it onto the val. Fires where that `final` survives: not at a constant (JS-C08's
    // `inline val` strips it) and not at an uninitialised field (a var placeholder strips it).
    Obligations.consult(JS.C(53), v.origin)(Option.when(
      vs.flags.isFinal && !vs.flags.isMutable && ownerCd.isDefined &&
        v.rhs.isDefined && !isJavaConstant(v, vs))(()))
    declVisibility(vs, v.origin)
    // trivia, then porter note, then annotations, then the val (see defDef). annots renders
    // @nowarn — without it a phase-attached annotation is silently dropped (ENGINE-LIMITS T26.1).
    val note = declNotes(v.symbol, i)
    val an   = annots(sym(v.symbol), i)
    inDeclaration {
      if v.leading.nonEmpty then leading(v.leading, i) + note + an + valDef0(v.copy(leading = Nil), i)
      else note + an + valDef0(v, i)
    }

  private[emit] def valDef0(v: Tree.ValDef, i: Int): String =
    val s = sym(v.symbol)
    if s.flags.isGiven then
      // An EMPTY NAME renders an ANONYMOUS given (ENGINE-LIMITS CT3): a minted name could shadow
      // an emitted root package, since this backend emits only fully-qualified references.
      val kw = if s.flags.isPrivate then "private given" else "given"
      val nm = if s.name.isEmpty then "" else s"${esc(s.name)}: "
      return s"${ind(i)}$kw $nm${tpe(v.tpt.tpe)}${v.rhs.map(r => s" = ${term(r, i)}").getOrElse("")}"
    // A FIELD SLOT — the constructor funnel hoisted this field's value into the synthesised
    // primary's parameter list, so the field binds that parameter (java's initialiser is gone).
    // val where nothing else writes it (A1 — slot-eligibility and single-write are two conditions).
    currentClass.flatMap(cc => plans(cc).fieldSlots.find(_.field == v.symbol)) match
      case Some(fs) =>
        val kw = if fs.mutable then "var" else "val"
        val q  = privateQualifier(s.owner)
        val m  = if kw == "var" then mods(s, q).replace("final ", "") else mods(s, q)
        return s"${ind(i)}$m$kw ${esc(s.name)}: ${tpe(v.tpt.tpe)} = ${fs.name}"
      case None => ()
    v.rhs match
      case Some(r) if isJavaConstant(v, s) && !isAnonOwner(s.owner) =>
        // a java CONSTANT VARIABLE (JLS 4.12.4) is INLINED by javac, so reading it triggers no
        // class initialiser — a typed `val` would (§4.4's Vector3/Matrix4 cycle). `inline val`
        // WITHOUT the type ascription, which would defeat the constant type. An ANONYMOUS CLASS
        // has no companion, so it stays an ordinary `val` in the anonymous body there.
        s"${ind(i)}${mods(s).replace("final ", "")}inline val ${esc(s.name)} = ${constAt(r, v.tpt.tpe)}"
      case Some(r) =>
        // a non-final java local or PRIVATE field never ASSIGNED anywhere in the program (the
        // write set is BeanCollapse.writtenSymbols, §4.55) emits val instead of var. NON-PRIVATE
        // fields stay var even when unwritten HERE — a dependent port may write them (§1.5).
        val isField = program.definitionOf(s.owner).exists(_.isInstanceOf[Tree.ClassDef])
        val kw = if s.flags.isMutable && (isWritten(v) || (isField && !s.flags.isPrivate))
                 then "var" else "val"
        val q  = privateQualifier(s.owner)
        val m  = if kw == "var" then mods(s, q).replace("final ", "") else mods(s, q)
        s"${ind(i)}$m$kw ${esc(s.name)}: ${tpe(v.tpt.tpe)} = ${term(r, i)}"
      case None =>
        // an uninitialized java field: a var placeholder so constructors can assign it (a bare
        // `val x: T` is abstract); `final` is dropped. Substitutes
        // `scala.compiletime.uninitialized` ONLY for `defaultFor`'s `null.asInstanceOf[T]`
        // fallback, never a stated default. ONLY FOR A FIELD: `uninitialized` may only be a mutable
        // FIELD's RHS, and this function also renders a method's local var (0 -> 380 errors without the gate).
        val fieldOfAClass = program.definitionOf(s.owner).exists(_.isInstanceOf[Tree.ClassDef])
        val stated = defaultFor(v.tpt.tpe)
        val blank  = if fieldOfAClass && stated.contains(".asInstanceOf[") then "scala.compiletime.uninitialized" else stated
        s"${ind(i)}${mods(s, privateQualifier(s.owner)).replace("final ", "")}var ${esc(s.name)}: ${tpe(v.tpt.tpe)} = $blank"

  /** the literal rendered AT the field's declared type. `inline val` takes its constant type from
    * the literal (a type ascription is rejected), so `static final float degFull = 360` must emit
    * `360.0f` and not `360`, or the field becomes an Int constant and divisions using it round. */
  private[emit] def constAt(r: Term, t: TypeRepr): String = (r, t) match
    case (Tree.Literal(c, _, _), TypeRepr.TypeRef(_, sy)) =>
      def num: Option[BigDecimal] = c match
        case Constant.ByteC(v)  => Some(BigDecimal(v.toInt)); case Constant.ShortC(v) => Some(BigDecimal(v.toInt))
        case Constant.IntC(v)   => Some(BigDecimal(v));       case Constant.LongC(v)  => Some(BigDecimal(v))
        case Constant.FloatC(v) => Some(BigDecimal(v.toDouble)); case Constant.DoubleC(v) => Some(BigDecimal(v))
        case Constant.CharC(v)  => Some(BigDecimal(v.toInt)); case _ => scala.None
      (sym(sy).fullName, num) match
        case ("scala.Float", Some(n))  => s"${n.toFloat}f"
        case ("scala.Double", Some(n)) => val d = n.toDouble; if d == d.toLong then s"$d" else d.toString
        case ("scala.Long", Some(n))   => s"${n.toLong}L"
        case ("scala.Short", Some(n))  => s"${n.toShort}"
        case ("scala.Byte", Some(n))   => s"${n.toByte}"
        case _                          => constant(c)
    case _ => term(r, 0)

  /** a java CONSTANT VARIABLE: `static final`, primitive or `String`, literal initialiser.
    * Delegates to `ClassInitTriggerCheck.constantVariable` — the same predicate the class-init
    * census uses, so the two can never disagree about which fields are step-9 content (K22). */
  private[emit] def isJavaConstant(v: Tree.ValDef, s: Symbol): Boolean =
    balticporter.tir.ClassInitTriggerCheck.constantVariable(v, s)(using program)

  private[emit] def defaultFor(t: TypeRepr): String = t match
    // a union with Null STATES its own default; the union was introduced to retire this cast.
    case TypeRepr.OrType(_, TypeRepr.TypeRef(_, s)) if sym(s).fullName == "scala.Null" => "null"
    case TypeRepr.TypeRef(_, s) => sym(s).fullName match
        case "scala.Int" | "scala.Short" | "scala.Byte" => "0"
        case "scala.Long"                               => "0L"
        case "scala.Float"                              => "0.0f"
        case "scala.Double"                             => "0.0"
        case "scala.Boolean"                            => "false"
        case "scala.Char"                               => "'\\u0000'"
        case "scala.Unit"                               => "()"
        case _                                          => s"null.asInstanceOf[${tpe(t)}]"
    case _ => s"null.asInstanceOf[${tpe(t)}]"

  /** A declaration's Java annotations, rendered ahead of it.
    *
    * FULLY QUALIFIED like every other reference this phase emits, so `@Test` becomes
    * `@org.junit.Test` and needs no import. Losing these is a silent correctness defect: a JUnit
    * suite whose `@Test` did not survive runs ZERO tests and reports SUCCESS. */
  private[emit] def annots(s: Symbol, i: Int): String =
    if s.annotations.isEmpty then ""
    else s.annotations.map { a =>
      val args = if a.args.isEmpty then ""
                 // Java's single-element @A(x) names its value `value`; scala takes it positionally.
                 else if a.args.sizeIs == 1 && a.args.head._1 == "value" then s"(${term(a.args.head._2, i)})"
                 // a NAMED arg goes through esc — a java element name may be a scala keyword.
                 else s"(${a.args.map((k, v) => s"${esc(k)} = ${term(v, i)}").mkString(", ")})"
      s"${ind(i)}@${tpe(a.tpe)}$args\n"
    }.mkString

  /** The top-level type a symbol lives in, when it is NOT that type itself — the qualifier a
    * nested class's `private` member needs. Java scopes `private` to the enclosing TOP-LEVEL
    * class; scala's bare `private` is class-only, so `private[TopLevel]` is faithful. Applied ONLY
    * to a NESTED class's members (a top-level class's own `private` needs none — regressed once). */
  private[emit] def privateQualifier(owner: SymId): Option[String] =
    Option.when(currentTopLevel.nonEmpty && currentOwnerSym != currentTopLevelSym)(currentTopLevel)

  /** The ACCESS modifier alone — [[Visibility]] decided the level, this supplies the qualifier.
    * The two package-scoped levels take [[currentPkgTail]], the package being written right now;
    * only a cross-package override carries its own (enclosing) package. */
  private[emit] def vis(s: Symbol, privateIn: Option[String]): String =
    visPlan.getOrElse(s.id, Visibility.Vis.Public) match
      case Visibility.Vis.Public         => ""
      case Visibility.Vis.Private        => privateIn.fold("private ")(o => s"private[$o] ")
      case Visibility.Vis.PackagePrivate => s"private[${esc(currentPkgTail)}] "
      case Visibility.Vis.ProtectedPkg   => s"protected[${esc(currentPkgTail)}] "
      case Visibility.Vis.PrivateAt(q)   => s"private[${esc(TirEmitter.tailSegment(q))}] "
      case Visibility.Vis.ProtectedAt(q) => s"protected[${esc(TirEmitter.tailSegment(q))}] "

  private[emit] def mods(s: Symbol, privateIn: Option[String] = scala.None): String =
    val f = s.flags
    // `private override` is illegal in scala: a java private method is invisible to subclasses, so
    // it overrides nothing (true of both bare and private[TopLevel]; NOT true of package-private,
    // which does override and needs the keyword — so the rule is scoped to the LEVEL).
    val javaPrivate = visPlan.get(s.id).contains(Visibility.Vis.Private)
    val parts = List(
      vis(s, privateIn),
      if f.isOverride && !javaPrivate then "override " else "",
      if f.isFinal then "final " else "",
      // NOT `sealed`: java's seal and scala's disagree about where subtypes must land, so only
      // [[sealOf]] may answer that question — a flag-shaped answer here would double it.
      if f.isImplicit then "implicit " else "",
      if f.isLazy then "lazy " else "",
    )
    parts.mkString

