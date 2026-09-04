package balticporter.transform

import balticporter.tir.*

/** The collections residue INSIDE the program — every site where java's own subtyping carried a
  * value across an edge the mapping has no image for, counted. `CollectionBoundaryCheck` sees only
  * the half of a slot that is the JDK's own; this counts the third population, where BOTH sides are
  * the phase's own output, so no JDK type is in the comparison and no boundary arm fires. Distinct
  * from [[CollectionClosureCheck]] (mapped supertype / unmapped subtype, about types): this is about
  * SITES where both ends are mapped and the two targets are unrelated. Universal in mechanism,
  * parameterised by the mapping — an empty mapping is a no-op by arithmetic.
  * CLAUDE.md §1's third-population paragraph, §4.45, §4.56; ENGINE-LIMITS K2.5
  */
object CollectionInternalCheck:

  /** The check's name in `findings.tsv`. */
  val Name = "collection-internal"

  /** what kind of in-program seam this is. */
  enum Issue:
    /** one of the callee's own type variables is bound, inside a single argument list, to types on
      * opposite sides of a broken edge. */
    case SplitTypeVariable
    /** one side is a type the PROGRAM declares whose own emitted ancestry lies on the far side of a
      * broken edge from the slot. */
    case DeclaredSubtype

  object Issue:
    /** which of §1's three kinds the fix is (CLAUDE.md §4.45). */
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

  /** one in-program seam site. `edge` is java's own relation that carried the value
    * (`java.util.HashSet <: java.util.Collection`); `targets` is what the two ends became. Both are
    * printed — the edge says why the source compiled, the targets say why the port does not. */
  final case class Finding(issue: Issue, slot: String, edge: String, targets: String,
                           origin: Origin, enclosing: SymId):
    def detail: String = s"$slot: java's $edge became $targets, which are unrelated"
    def render: String = s"$issue $slot — $edge became $targets  (${origin.javaPath}:${origin.line})"
    def report(using program: Program): CheckReport.Finding =
      CheckReport.Finding(Name, issue.toString,
        program.symbolOf(enclosing).map(_.fullName).getOrElse("?"),
        CheckReport.relativise(origin.javaPath), origin.line, detail)

  /** Every in-program seam in `program`, which must be the program AFTER the phase ran. `mapped`,
    * `targetOf` and `standalone` are the phase's own policy, read back (§4.56). Held to units the
    * run EMITS — a dependent's `Program` carries its base's units, whose seams are the base's
    * finding (ENGINE-LIMITS D2). */
  def check(program: Program, units: List[Tree.ClassDef], mapped: Set[String],
            targetOf: String => String, standalone: Set[String]): List[Finding] =
    // broken edges: a java subtype pair both of whose ends the mapping covers, whose two targets
    // sit on opposite sides of the standalone split. The label a target pair is reported under is
    // derived deterministically (sorted sub, most-general reachable sup) so it does not vary by run.
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

      /** the java edge these two TARGETS broke, in either direction — `None` for every ordinary pair. */
      def broken(a: String, b: String): Option[String] =
        edges.get((a, b)).orElse(edges.get((b, a)))

      def brokenT(a: TypeRepr, b: TypeRepr): Option[(String, String)] =
        for
          x <- fqn(a); y <- fqn(b); e <- broken(x, y)
        yield (e, s"$x / $y")

      /** every type a class the PROGRAM declares was emitted UNDER — read from the tree, not a name
        * test (§4.56). Asked only of a program-declared head — an external type's ancestry is a
        * class-file fact this run cannot read (§4.6). */
      def ancestry(h: SymId): List[String] =
        graph.externalAncestorsOf(h) ++
          graph.ancestorsOf(h).flatMap(program.symbolOf).map(_.fullName)

      def found(issue: Issue, slot: String, e: (String, String), origin: Origin, enclosing: SymId): Unit =
        out += Finding(issue, slot, e._1, e._2, origin, enclosing)

      /** a slot where the VALUE is a type the program declares, so the boundary lane read it as
        * `Other` and filed nothing. Conformance is asked FIRST — a library's own collection may
        * carry both ends of the split as parents, so a correct slot must not report as a seam. */
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
          * unrelated targets. Which variables are the CALL's to bind is read from OWNERSHIP, never
          * a name (§4.56) — a class's own type parameter is fixed by the receiver and skipped. */
        private def typeVariableSplit(t: Tree.Apply)(using Program): Unit =
          program.symbolOf(t.method).map(_.info).collect {
            case TypeRepr.MethodType(ps, _, _) if ps.sizeIs == t.args.size => ps.map(_._2)
          }.foreach { formals =>
            val bound = collection.mutable.LinkedHashMap.empty[SymId, TypeRepr]
            formals.zip(t.args).foreach((f, a) => bind(t.method, f, a.tpe, bound, a.origin, t.method))
          }

        /** structural first-order binding — the seam is the SECOND binding: a variable already
          * bound to one end of a broken edge, met again at the other. */
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

  /** grouped one-line summary, worst family first, each with its §1 classification. */
  def summary(fs: List[Finding]): String =
    if fs.isEmpty then "  none"
    else
      fs.groupBy(_.issue).toList.sortBy((_, v) => -v.size).map { (issue, vs) =>
        val head  = s"  ${vs.size} × $issue\n  ${Issue.classification(issue)}"
        val sites = vs.groupBy(f => (f.slot, f.edge, f.targets)).toList.sortBy((_, v) => -v.size).take(10)
          .map { case ((slot, e, tg), ss) => s"    ${ss.size} × $slot: $e became $tg" }
        (head :: sites).mkString("\n")
      }.mkString("\n")
