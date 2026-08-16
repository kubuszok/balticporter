package balticporter.transform

import balticporter.tir.*

/** The collections residue INSIDE the program — every site where java's own subtyping carried a
  * value across an edge this mapping has no image for, counted.
  *
  * ==Why a fourth collection lane==
  * `CollectionBoundaryCheck` counts the seam where one side of a slot is the JDK's OWN: a type the
  * mapping left alone, or an external callee whose formal lives in a class file. That is the half a
  * reader meets first and it is not the whole residue. On the first library large enough to show
  * it, **sixteen of the twenty-four attributed compile errors were the collections family's
  * IN-PROGRAM seam and that lane read them as zero** — both sides of every one of those slots are
  * the phase's own output, so there is no JDK type anywhere in the comparison and no arm fires.
  * `PROGRESS.md` §10.6.3 called that "the one thing on this list that is wrong rather than merely
  * refused", and this is the lane it asked for.
  *
  * ==The one fact underneath all of it: a BROKEN EDGE==
  * `CollectionsTransform.typeMap` sends `java.util.Collection` to a STANDALONE shim, because a java
  * interface may not be modelled on a scala collection trait (`CLAUDE.md` §4.5) — and it sends
  * every java SUBTYPE of `Collection` (`List`, `Set`, `ArrayList`, `HashSet`, …) to a
  * `scala.collection.*` type. So java's `List <: Collection` has NO image on the scala side: the
  * mapping's own entry for `Collection` says so in as many words, and calls the residue
  * "repairable, by `coerce`, at the slot". This lane is what counts the slots `coerce` did not
  * reach.
  *
  * That is deliberately not [[CollectionClosureCheck]]'s question. That one asks *is the mapping
  * closed downwards* — a MAPPED supertype with an UNMAPPED subtype — and answers about types. This
  * one asks about a pair where BOTH ends are mapped and the two TARGETS are unrelated, and answers
  * about sites. A port can hold `collection-closure` at zero and have every site here.
  *
  * ==Which sites, and why each is invisible to the boundary lane==
  * Two, kept apart because the reader's next action differs and because each is invisible for a
  * DIFFERENT structural reason — a single count would say "seven" and name no mechanism:
  *
  *   - [[Issue.SplitTypeVariable]] — the disagreement is not at any formal's HEAD. The callee's own
  *     type variable is bound twice inside one argument list, once to each side of a broken edge
  *     (`set(DataKey<Collection<E>>, ArrayList<E>)`). `CollectionBoundaryCheck.check`'s `slot`
  *     compares two head FQNs and a type VARIABLE has neither side, so it files nothing;
  *   - [[Issue.DeclaredSubtype]] — one side is a type the PROGRAM declares. `sideOf` reads a head
  *     FQN against the JDK family, the shims and `scala.collection.*`, so a library's own
  *     `OrderedSet implements java.util.Set` is `Other` at every slot it meets a shim at — even
  *     though the phase itself re-parented it onto the far side of the edge.
  *
  * A THIRD arm is deliberately absent and measured: a call at a symbol THIS PHASE MINTED carries no
  * `MethodType` at all, so the boundary lane's argument arm skips every one of them and the only
  * evidence left is the call's own OPERANDS spanning a broken edge. Read that way it reports a
  * runtime helper written to take BOTH families (`JavaCollections.containsAll`'s
  * `IterableOnce[?] | JavaIterable[?]` formal exists precisely for this shape) as a residue —
  * `ENGINE-LIMITS.md` K2.5's defect exactly, a count that cannot tell a refusal from a closed seam.
  * Its repair is to mint those helpers WITH their signatures, at which point the ordinary arms see
  * them and no third kind is needed. Measured at 2 rows on the one port that has any, of which 1
  * was false.
  *
  * ==What this lane does NOT do==
  * It bridges nothing and it fixes nothing. The value of a row is that it arrives ATTRIBUTED — with
  * the java edge that was broken, the two targets, and §1's classification — instead of as a bare
  * `Found: … / Required: …` an agent has to investigate (`CLAUDE.md` §4.45). A residue with no lane
  * is a residue that vanishes the moment a status section stops being maintained. Note it counts
  * SITES and the compiler reports STATEMENTS: 16 rows against 9 errors on one source set, all one
  * seam, which is the lane being MORE complete than the instrument it explains rather than less.
  *
  * ==Universal, parameterised by the mapping==
  * §1(a) in mechanism — one library's type hierarchy mapping onto two unrelated families is a fact
  * about java and scala — and every question it asks about a type is a membership test in what the
  * PHASE ITSELF did (`CLAUDE.md` §4.56). It holds no type list of its own: the java hierarchy is
  * [[CollectionClosureCheck.jdkSupertypes]] and the split is
  * [[CollectionsTransform.standaloneTargets]]. An empty mapping makes it a no-op by arithmetic,
  * because there are then no edges at all.
  */
object CollectionInternalCheck:

  /** The check's name in `findings.tsv`. */
  val Name = "collection-internal"

  /** what kind of in-program seam this is — and, because each is invisible to
    * [[CollectionBoundaryCheck]] for a different structural reason, WHICH blindness it names. */
  enum Issue:
    /** one of the callee's own type variables is bound, inside a single argument list, to types on
      * opposite sides of a broken edge. */
    case SplitTypeVariable
    /** one side is a type the PROGRAM declares whose own emitted ancestry lies on the far side of a
      * broken edge from the slot. */
    case DeclaredSubtype

  object Issue:
    /** which of §1's three kinds the fix is — the thing a bare typer error cannot say. Every row
      * here shares one cause and the actions differ, which is why the text does. */
    def classification(i: Issue): String = i match
      case SplitTypeVariable =>
        "§1(a) engine gap: java bound ONE type variable from two arguments whose types it related " +
          "by subtyping (`ArrayList <: Collection`), and the mapping sent those two java types to " +
          "targets with no relation — a standalone `balticporter.runtime` shim and a " +
          "`scala.collection.*` type — so no scala type satisfies both slots and there is nothing " +
          "at the call to coerce. Closing it needs the coercion to run at the INFERENCE site (the " +
          "argument whose type fixes the variable), not at the formal, because the formal has no " +
          "head to compare. NOT a `typeMap` row: both java types are already mapped."
      case DeclaredSubtype =>
        "§1(a) engine gap: this class IMPLEMENTS the java type on one end of a broken edge, so the " +
          "phase re-parented it onto that end's target, and the slot it is handed to carries the " +
          "OTHER end's. Java admitted the value by its own subtyping; scala has no such relation " +
          "between the two targets. `CollectionsTransform.coerce` bridges a VALUE at such a slot " +
          "wherever a factory exists; where the value is the program's own declaration, the honest " +
          "answers are to give the class the slot's own target as a parent as well, or to give " +
          "`coerce` a factory for the pair — never a cast, which is a `ClassCastException` with a " +
          "green compile."

  /** one in-program seam site.
    *
    * `edge` is java's own relation that carried the value — `java.util.HashSet <: java.util.Collection`
    * — and `targets` is what the two ends became. Both are printed because neither alone tells a
    * reader what to change: the java edge says why the source compiled, the target pair says why
    * the port does not. */
  final case class Finding(issue: Issue, slot: String, edge: String, targets: String,
                           origin: Origin, enclosing: SymId):
    def detail: String = s"$slot: java's $edge became $targets, which are unrelated"
    def render: String = s"$issue $slot — $edge became $targets  (${origin.javaPath}:${origin.line})"
    def report(using program: Program): CheckReport.Finding =
      CheckReport.Finding(Name, issue.toString,
        program.symbolOf(enclosing).map(_.fullName).getOrElse("?"),
        CheckReport.relativise(origin.javaPath), origin.line, detail)

  /** Every in-program seam in `program`, which must be the program AFTER the phase ran.
    *
    * `mapped` is `CollectionsTransform.mappedTypes`, `targetOf` its target lookup and `standalone`
    * [[CollectionsTransform.standaloneTargets]] — the phase's own policy, read back, so the check
    * concludes about a type only from what the phase did to it (`CLAUDE.md` §4.56).
    *
    * Held to the units the run EMITS, exactly as its three siblings are: a dependent port's
    * `Program` carries its base's units and a seam inside one of those is the BASE's finding
    * (`ENGINE-LIMITS.md` D2). */
  def check(program: Program, units: List[Tree.ClassDef], mapped: Set[String],
            targetOf: String => String, standalone: Set[String]): List[Finding] =
    // the BROKEN EDGES, derived once: a java subtype pair BOTH of whose ends the mapping covers,
    // whose two targets sit on opposite sides of the standalone split. Empty mapping, empty set,
    // and the whole check is a no-op by arithmetic rather than by a branch.
    // …and the LABEL a pair of targets is reported UNDER is derived twice-deterministically, which
    // is not fussiness: several java types share a target (`ArrayList` and `Vector` are both
    // `mutable.ArrayBuffer`; `Collection` and `AbstractCollection` are both the shim), so a plain
    // `toMap` over an unordered set publishes a different sentence per run for one fact — a
    // `findings.tsv` row that moves with nothing behind it, which is the one thing a baseline
    // cannot survive (`CLAUDE.md` §5). The SUB is the sorted first; the SUP is the most GENERAL
    // java type on the far side that reaches this target, because that is the type the java source
    // actually writes — `supertypesOf` is nearest-first, so it is the last of the filtered list.
    val edges: Map[(String, String), String] =
      mapped.toList.sorted.flatMap { sub =>
        CollectionClosureCheck.supertypesOf(sub).filter(mapped.contains).flatMap { sup =>
          val (tSub, tSup) = (targetOf(sub), targetOf(sup))
          Option.when(tSub != tSup && standalone.contains(tSub) != standalone.contains(tSup))(
            (tSub, tSup) -> s"$sub <: $sup")
        }.groupBy(_._1).view.mapValues(_.last._2).toList
      }.groupBy(_._1).view.mapValues(_.head._2).toMap

    if edges.isEmpty then Nil
    else
      val out     = collection.mutable.ListBuffer[Finding]()
      val graph   = OverrideGraph.build(program)
      val targets = mapped.map(targetOf) ++ standalone
      given Program = program

      def fqn(t: TypeRepr): Option[String] = headSym(t).flatMap(program.symbolOf).map(_.fullName)

      /** the java edge these two TARGETS broke, in either direction — `None` when the two are not
        * two ends of one broken edge, which is every ordinary pair. */
      def broken(a: String, b: String): Option[String] =
        edges.get((a, b)).orElse(edges.get((b, a)))

      def brokenT(a: TypeRepr, b: TypeRepr): Option[(String, String)] =
        for
          x <- fqn(a); y <- fqn(b); e <- broken(x, y)
        yield (e, s"$x / $y")

      /** every type a class the PROGRAM declares was emitted UNDER — its own emitted ancestry,
        * program-declared and external alike.
        *
        * Read from the tree because that is what the phase DID to it (`OrderedSet extends
        * mutable.Set[E] with JavaIterable[E]`), and a name test would answer about a package
        * (§4.56). Asked only of a program-declared head: an EXTERNAL type's ancestry is a fact
        * about a class file this run cannot read, and guessing one would be §4.6's fabricated
        * fact. */
      def ancestry(h: SymId): List[String] =
        graph.externalAncestorsOf(h) ++
          graph.ancestorsOf(h).flatMap(program.symbolOf).map(_.fullName)

      def found(issue: Issue, slot: String, e: (String, String), origin: Origin, enclosing: SymId): Unit =
        out += Finding(issue, slot, e._1, e._2, origin, enclosing)

      /** a slot the four ordinary kinds DO reach, asked the question `sideOf` cannot: the VALUE is a
        * type the program declares, so the boundary lane read it as `Other` and filed nothing.
        *
        * CONFORMANCE IS ASKED FIRST and it is not a formality: a library's own collection routinely
        * carries BOTH ends of the split as parents (`OrderedSet extends mutable.Set with
        * JavaIterable`), so a rule that looked only for an end on the far side would report every
        * one of its correct slots as a seam. The slot's own type appearing in the emitted ancestry
        * is the whole test, and it is exact rather than approximate BECAUSE the ancestry is the
        * program's own tree — this is deliberately not asked where the value's head is external
        * (`mutable.HashSet <: mutable.Set` is a class-file fact this run has no parents for). */
      def declaredSlot(kind: String, expected: TypeRepr, actual: TypeRepr, origin: Origin,
                       enclosing: SymId): Unit =
        for
          h <- headSym(actual) if program.owns(h)
          e <- fqn(expected)
          a <- fqn(actual) if a != e
          anc = ancestry(h) if !anc.contains(e)
          x <- anc.filter(targets.contains).flatMap(t => broken(e, t).map(_ -> s"$e / $t")).headOption
        do found(Issue.DeclaredSubtype, kind, x, origin, enclosing)

      val scan = new Phase:
        def name: String = "collection-internal-check"

        override def transformApply(t: Tree.Apply)(using Program): Term =
          typeVariableSplit(t)
          program.symbolOf(t.method).map(_.info).collect {
            case TypeRepr.MethodType(ps, _, _)                       => ps.map(_._2)
            case TypeRepr.PolyType(_, TypeRepr.MethodType(ps, _, _)) => ps.map(_._2)
          }.filter(_.sizeIs == t.args.size)
            .foreach(fs => t.args.zip(fs).foreach((a, f) =>
              declaredSlot("argument", f, a.tpe, a.origin, t.method)))
          t

        /** java bound one type variable from two arguments; the mapping sent the two java types to
          * unrelated targets.
          *
          * WHICH variables are the CALL's to bind is read from OWNERSHIP and never from a name
          * (§4.56, whose hazard is sharpest here — a class's `<V>` and a method's `<V>` are one
          * string). The frontend mints a method's type parameter with `owner = <that method>`
          * (`SpoonTir.mintTypeParams`), so the test is a symbol comparison; a CLASS's parameter,
          * which the receiver fixes and which this call cannot bind, owns to the class and is
          * skipped. There is no `PolyType` to read instead: the frontend builds a plain
          * `MethodType` for every executable and puts the parameters on the `DefDef`. */
        private def typeVariableSplit(t: Tree.Apply)(using Program): Unit =
          program.symbolOf(t.method).map(_.info).collect {
            case TypeRepr.MethodType(ps, _, _) if ps.sizeIs == t.args.size => ps.map(_._2)
          }.foreach { formals =>
            val bound = collection.mutable.LinkedHashMap.empty[SymId, TypeRepr]
            formals.zip(t.args).foreach((f, a) => bind(t.method, f, a.tpe, bound, a.origin, t.method))
          }

        /** structural first-order binding, and the SEAM IS THE SECOND BINDING: a variable already
          * bound to one end of a broken edge, met again at the other, is exactly java's implicit
          * widening with no scala image. Recursion is through matching heads only — an
          * `AppliedType` whose heads differ is a slot the ordinary lane already reports. */
        private def bind(owner: SymId, formal: TypeRepr, actual: TypeRepr,
                         bound: collection.mutable.LinkedHashMap[SymId, TypeRepr],
                         origin: Origin, callee: SymId)(using Program): Unit =
          formal match
            case TypeRepr.TypeRef(_, s) if program.symbolOf(s).exists(_.owner == owner) =>
              bound.get(s) match
                case Some(prev) =>
                  brokenT(prev, actual).foreach(e =>
                    found(Issue.SplitTypeVariable,
                          s"type variable ${program.symbolOf(s).map(_.name).getOrElse("?")}" +
                            s" of ${calleeName(callee)}", e, origin, callee))
                case None => bound(s) = actual
            case TypeRepr.AppliedType(tc, fs) =>
              actual match
                case TypeRepr.AppliedType(atc, as) if fqn(tc) == fqn(atc) && fs.sizeIs == as.size =>
                  fs.zip(as).foreach((f, a) => bind(owner, f, a, bound, origin, callee))
                case _ => ()
            case _ => ()

        override def transformValDef(t: Tree.ValDef)(using Program): Tree.ValDef =
          t.rhs.foreach(r => declaredSlot("declaration", t.tpt.tpe, r.tpe, t.origin, t.symbol))
          t

        override def transformDefDef(t: Tree.DefDef)(using Program): Tree.DefDef =
          t.rhs.foreach(b => CollectionBoundaryCheck.returnsIn(b).foreach(r =>
            r.expr.foreach(e => declaredSlot("return", t.returnTpt.tpe, e.tpe, r.origin, t.symbol))))
          t

        override def transformTerm(t: Term)(using Program): Term =
          t match
            case a: Tree.Assign => declaredSlot("assignment", a.lhs.tpe, a.rhs.tpe, a.origin, SymId.None)
            case _              => ()
          t

      units.foreach(u => StandardTraversal.mapClassDef(scan, u))
      out.toList.distinct

  private def calleeName(m: SymId)(using p: Program): String =
    p.symbolOf(m).map(_.name).getOrElse("?")

  private def headSym(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)      => Some(s)
    case TypeRepr.AppliedType(tc, _) => headSym(tc)
    case _                           => scala.None

  /** grouped one-line summary, worst family first, each with its §1 classification — the shape all
    * three sibling lanes have, for the same reason: a per-site dump is what `findings.tsv` is for. */
  def summary(fs: List[Finding]): String =
    if fs.isEmpty then "  none"
    else
      fs.groupBy(_.issue).toList.sortBy((_, v) => -v.size).map { (issue, vs) =>
        val head  = s"  ${vs.size} × $issue\n  ${Issue.classification(issue)}"
        val sites = vs.groupBy(f => (f.slot, f.edge, f.targets)).toList.sortBy((_, v) => -v.size).take(10)
          .map { case ((slot, e, tg), ss) => s"    ${ss.size} × $slot: $e became $tg" }
        (head :: sites).mkString("\n")
      }.mkString("\n")
