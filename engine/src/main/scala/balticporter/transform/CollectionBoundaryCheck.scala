package balticporter.transform

import balticporter.core.RuntimeArtifact
import balticporter.tir.*

/** The JDK/Scala collection BOUNDARY, counted — every slot the retyping opened and did not close.
  *
  * ==Why this exists==
  * `CollectionsTransform` retypes a library's collection signatures while its bodies, and every
  * JDK type the mapping does not cover, stay where they are. That creates two collection worlds
  * that cannot meet — a problem java does not have, where a `List` IS an `Iterable`
  * (ENGINE-LIMITS K2). `coerce` bridges the slots it knows; everything else arrives at the
  * compiler as a bare `Found: … / Required: …`, which is CLAUDE.md §4.45's exact complaint: an
  * error an agent cannot classify as (a) an engine bug, (b) a phase to configure or (c) a
  * library-specific rule costs it a full investigation, and these are the BULK of a new library's
  * first wall.
  *
  * So the residue is a NUMBER with an origin and a §1 classification, available before any
  * compiler runs. A new port's first wall becomes a triaged list.
  *
  * ==What counts as a stranded slot==
  * Four slot kinds — the same four `coerce` reaches, which is not a coincidence: this measures
  * exactly what that seam did not close.
  *
  *   - a call ARGUMENT against its formal parameter,
  *   - a `val`/field DECLARATION against its initialiser,
  *   - an ASSIGNMENT target against its right-hand side,
  *   - a `return` against the enclosing method's declared return type.
  *
  * A slot is STRANDED when the two sides are on opposite sides of one of two lines the phase
  * itself drew:
  *
  *   - **JDK against retyped** — one side is a JDK collection-family type the mapping left alone,
  *     the other is a `scala.collection.*` or `balticporter.runtime` type the mapping produced.
  *     `Stream<String> st = f.stream()` is the shape (K6): the value collapsed to a `Buffer`, the
  *     declaration still says `Stream`.
  *   - **shim against scala** — both sides are things the phase produced, but on opposite sides of
  *     the `JavaCollection`/`mutable.Buffer` split the mapping is built on. This is K2's residue
  *     proper, and it is the one a reader would not think to look for, because both types look
  *     "already ported". `m.keySet()` into a `Collection` slot is the standing example, refused on
  *     purpose (`coerce.isKeySetView`) and therefore still counted here: a deliberate refusal is a
  *     finding, not an absence.
  *
  * A wrap `coerce` DID insert is excluded by construction and needs no special case: the wrap is
  * typed as the EXPECTED type, so the two sides agree and the slot is not a boundary at all. Same
  * for any node the phase minted — its symbol carries `NoType`, so it offers no formals and the
  * argument arm skips it.
  *
  * ==Both directions of `Kind`==
  * `scala.Tuple2` is a mapping target and is deliberately NOT on the scala side of the line: it is
  * `Kind.Entry`, a pair, not a collection, so a `Tuple2` meeting a JDK slot is not a collection
  * boundary and reporting it would be noise (`coerce`'s own coverage table says the same).
  *
  * ==Universal, parameterised by the mapping==
  * §1(a) in mechanism — two type families that cannot meet is a fact about java and scala — and it
  * takes the mapping as a PARAMETER, so an empty mapping makes it a no-op by arithmetic. It holds
  * no type list of its own except [[CollectionClosureCheck.jdkFamily]], which is the JDK's, and
  * the stream family below, which is the JDK's too.
  */
object CollectionBoundaryCheck:

  /** The check's name in `findings.tsv`. */
  val Name = "collection-boundary"

  /** what kind of stranding this is, which is what decides who fixes it (CLAUDE.md §1). */
  enum Issue:
    /** the JDK type is a SUBTYPE of one the mapping covers — the closure hole
      * [[CollectionClosureCheck]] reports as a type, met here as a site. */
    case UnmappedSubtype
    /** a JDK collection family the engine deliberately does not retype at all. */
    case UntranslatedFamily
    /** a type that IS in the mapping and reached this slot anyway. */
    case MappedTypeSurvived
    /** both sides are the phase's own output, on opposite sides of the shim/scala split. */
    case ShimBoundary

  object Issue:
    /** which of §1's three kinds the fix is — the thing a bare typer error cannot say. */
    def classification(i: Issue): String = i match
      case UnmappedSubtype =>
        "§1(b): the JDK type is a subtype of one CollectionsTransform maps, so the mapping is not " +
          "closed downwards — add the type to `typeMap` with a target that keeps the JDK relation " +
          "(CollectionClosureCheck reports the same hole as a type)."
      case UntranslatedFamily =>
        "§1(a) unbuilt, and REFUSED on purpose: this JDK family is not retyped, so the slot has no " +
          "translation and the error is loud rather than silent (ENGINE-LIMITS K6, M6). Closing it " +
          "needs the family retyped, not a wider guard on an existing rewrite."
      case MappedTypeSurvived =>
        "§1(a) engine bug: this type IS in `typeMap`, so no occurrence of it should have survived " +
          "`transformType`. A node minted with a type computed from an unmapped one is the usual cause."
      case ShimBoundary =>
        "§1(a) engine gap: a scala collection meets a `balticporter.runtime` shim slot (or the " +
          "reverse) with no wrap — extend `CollectionsTransform.coerce` to this slot, or, if the " +
          "cell is a deliberate refusal, it stays counted here (ENGINE-LIMITS K2's coverage table)."

  /** one stranded slot. */
  final case class Finding(issue: Issue, slot: String, expected: String, actual: String, origin: Origin, enclosing: SymId):
    def detail: String = s"$slot: Found $actual / Required $expected"
    def render: String = s"$issue $slot — Found $actual / Required $expected  (${origin.javaPath}:${origin.line})"
    def report(using program: Program): CheckReport.Finding =
      CheckReport.Finding(Name, issue.toString, program.symbolOf(enclosing).map(_.fullName).getOrElse("?"),
        CheckReport.relativise(origin.javaPath), origin.line, detail)

  /** JDK collection families that are NOT in the closure of anything `typeMap` covers and are not
    * retyped at all. `java.util.stream` is the whole of it today and the reason the list exists:
    * its refusal is deliberate and its sites must still be counted, or "we know about that one"
    * lives in prose (CLAUDE.md §5.1's objection to a hand-maintained expected-failure list). */
  val untranslatedFamilies: List[String] = List("java.util.stream.")

  private enum Side:
    case Jdk, Shim, Scala, Other

  /** which side of the boundary a type is on, decided from the MAPPING's own targets wherever a
    * choice exists.
    *
    * The shim side is exactly `targets` restricted to the runtime package — not a hardcoded list
    * of three names — so a mapping that adds a fourth shim widens this with nothing to edit. The
    * scala side is decided by PACKAGE rather than by membership, because the phase also mints
    * `scala.collection.Set` (the `keySet` view type) and `scala.collection.mutable.Buffer` (the
    * stream collapse's type) without either being a `typeMap` target. */
  private def sideOf(fqn: String, shims: Set[String]): Side =
    if shims.contains(fqn) then Side.Shim
    else if fqn.startsWith("scala.collection.") then Side.Scala
    else if CollectionClosureCheck.jdkFamily.contains(fqn) || untranslatedFamilies.exists(fqn.startsWith) then Side.Jdk
    else Side.Other

  /** Every stranded slot in `program`, which must be the program AFTER the phase ran: this counts
    * the residue the retyping created, so running it before means counting nothing.
    *
    * `mapped` is `CollectionsTransform.mappedTypes` and `targets` is `retypedTargets` — the
    * phase's own policy, read back, so the check concludes about a type only from what the phase
    * did to it (CLAUDE.md §4.56). */
  def check(program: Program, mapped: Set[String], targets: Set[String] = Set.empty): List[Finding] =
    check(program, program.units, mapped, targets)

  /** …restricted to the units the run actually EMITS — the same filter `OmissionCheck` and
    * `PortabilityCheck.inEmittedCode` carry, and for the same measured reason: a DEPENDENT port's
    * `Program` holds the base module's units too (`FrontendConfig.resolutionRoots`), and a stranded
    * slot inside one of those is the BASE's finding, reported by a repository that cannot act on it
    * (ENGINE-LIMITS D2). A base port passes `program.units` and this is the identity. */
  def check(program: Program, units: List[Tree.ClassDef], mapped: Set[String], targets: Set[String]): List[Finding] =
    val out   = collection.mutable.ListBuffer[Finding]()
    val shims = targets.filter(_.startsWith(RuntimeArtifact.Package + "."))
    given Program = program

    def fqn(t: TypeRepr): Option[String] = headSym(t).flatMap(program.symbolOf).map(_.fullName)

    def issueFor(jdk: String): Issue =
      if mapped.contains(jdk) then Issue.MappedTypeSurvived
      else if untranslatedFamilies.exists(jdk.startsWith) then Issue.UntranslatedFamily
      else if CollectionClosureCheck.supertypesOf(jdk).exists(mapped.contains) then Issue.UnmappedSubtype
      // a JDK family type with no mapped supertype at all: the mapping never touched anything it
      // relates to, so the slot is a gap in COVERAGE rather than a broken relation.
      else Issue.UnmappedSubtype

    def slot(kind: String, expected: TypeRepr, actual: TypeRepr, origin: Origin, enclosing: SymId): Unit =
      (fqn(expected), fqn(actual)) match
        case (Some(e), Some(a)) if e != a =>
          (sideOf(e, shims), sideOf(a, shims)) match
            case (Side.Jdk, Side.Scala | Side.Shim) => out += Finding(issueFor(e), kind, e, a, origin, enclosing)
            case (Side.Scala | Side.Shim, Side.Jdk) => out += Finding(issueFor(a), kind, e, a, origin, enclosing)
            case (Side.Shim, Side.Scala) | (Side.Scala, Side.Shim) =>
              out += Finding(Issue.ShimBoundary, kind, e, a, origin, enclosing)
            case _ => ()
        case _ => ()

    val scan = new Phase:
      def name: String = "collection-boundary-check"

      /** the enclosing DEFINITION, for attribution. The traversal is bottom-up, so this is set by
        * the DefDef hook AFTER its body — which is why the returns are collected from the DefDef
        * hook itself rather than as they are visited. */
      override def transformApply(t: Tree.Apply)(using Program): Term =
        val formals = program.symbolOf(t.method).map(_.info).collect {
          case TypeRepr.MethodType(ps, _, _)                       => ps.map(_._2)
          case TypeRepr.PolyType(_, TypeRepr.MethodType(ps, _, _)) => ps.map(_._2)
        }.getOrElse(Nil)
        if formals.sizeIs == t.args.size then
          t.args.zip(formals).foreach((a, f) => slot("argument", f, a.tpe, a.origin, t.method))
        t

      override def transformTerm(t: Term)(using Program): Term =
        t match
          case a: Tree.Assign => slot("assignment", a.lhs.tpe, a.rhs.tpe, a.origin, SymId.None)
          case _              => ()
        t

      override def transformValDef(t: Tree.ValDef)(using Program): Tree.ValDef =
        t.rhs.foreach(r => slot("declaration", t.tpt.tpe, r.tpe, t.origin, t.symbol))
        t

      override def transformDefDef(t: Tree.DefDef)(using Program): Tree.DefDef =
        t.rhs.foreach(b => returnsIn(b).foreach { r =>
          r.expr.foreach(e => slot("return", t.returnTpt.tpe, e.tpe, r.origin, t.symbol))
        })
        t

    units.foreach(u => StandardTraversal.mapClassDef(scan, u))
    out.toList

  /** every `return` that belongs to THIS method — the same DELIBERATELY BOUNDED walk
    * `CollectionsTransform.coerceReturns` performs, for the same reason and with the same failure
    * direction.
    *
    * A `return` inside a lambda, an anonymous class's method or a local class returns from THAT,
    * so comparing it against the enclosing method's declared type would be a WRONG finding. Only
    * the statement-carrying node kinds are followed and the default arm does NOT descend, which
    * makes a node kind added later a MISSED finding — loud by construction, never wrong. (Java's
    * `return` is a statement, so it cannot occur inside an argument or an operand.)
    *
    * It is a separate walk from the transform's because that one MAPS and this one COLLECTS, and
    * `CollectionsTransformSpec`/`CollectionBoundaryCheckSpec` pin the two against each other: a
    * lambda-local `return` is asserted uncoerced there and unreported here, so the day the two
    * lists diverge one of them fails. */
  def returnsIn(t: Term): List[Tree.Return] = t match
    case x: Tree.Return       => List(x)
    case x: Tree.Block        => x.stats.collect { case s: Term => returnsIn(s) }.flatten ++ returnsIn(x.expr)
    case x: Tree.If           => returnsIn(x.thenp) ++ returnsIn(x.elsep)
    case x: Tree.While        => returnsIn(x.body)
    case x: Tree.DoWhile      => returnsIn(x.body)
    case x: Tree.For          => returnsIn(x.body)
    case x: Tree.ForEach      => returnsIn(x.body)
    case x: Tree.Synchronized => returnsIn(x.body)
    case x: Tree.Try          => returnsIn(x.body) ++ x.catches.flatMap(c => returnsIn(c.body)) ++ x.finalizer.toList.flatMap(returnsIn)
    case x: Tree.Match        => x.cases.flatMap(c => returnsIn(c.body))
    case _                    => Nil

  private def headSym(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)      => Some(s)
    case TypeRepr.AppliedType(tc, _) => headSym(tc)
    case _                           => scala.None

  /** grouped one-line summary, worst family first, each with its §1 classification — the whole
    * point of the check is that a reader does not have to work out who fixes it. */
  def summary(fs: List[Finding]): String =
    if fs.isEmpty then "  none"
    else
      fs.groupBy(_.issue).toList.sortBy((_, v) => -v.size).map { (issue, vs) =>
        val head = s"  ${vs.size} × $issue\n  ${Issue.classification(issue)}"
        val sites = vs.groupBy(f => (f.slot, f.expected, f.actual)).toList.sortBy((_, v) => -v.size).take(10)
          .map { case ((slot, e, a), ss) => s"    ${ss.size} × $slot: Found $a / Required $e" }
        (head :: sites).mkString("\n")
      }.mkString("\n")
