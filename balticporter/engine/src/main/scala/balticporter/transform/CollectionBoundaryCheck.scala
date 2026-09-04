package balticporter.transform

import balticporter.catalog.FixKind
import balticporter.core.RuntimeArtifact
import balticporter.tir.*

/** The JDK/Scala collection BOUNDARY, counted — every slot the retyping opened and did not close.
  *
  * `CollectionsTransform` retypes signatures while JDK types the mapping does not cover stay put,
  * so a `Found: … / Required: …` reaches the compiler with no §1 classification (CLAUDE.md §4.45).
  * This counts it first, as a triaged finding, over the same four slot kinds `coerce` reaches — a
  * call ARGUMENT, a `val`/field DECLARATION, an ASSIGNMENT, a `return` — plus one they cannot
  * express: an argument to a call on a SCOPED-OUT receiver, whose formal is unknown ([[check]]'s
  * `onScopedReceiver`). A slot is STRANDED when its two sides fall on opposite sides of one of
  * THREE lines the phase itself drew (JDK/retyped, shim/scala, scoped-out/rewritten) — never from
  * a wrap `coerce` inserted, which retypes the slot to agreement by construction.
  *
  * §1(a) in mechanism, parameterised by the mapping: an empty mapping is a no-op by arithmetic.
  */
object CollectionBoundaryCheck extends RemedySource:

  /** The check's name in `findings.tsv`. */
  val Name = "collection-boundary"

  /** THE MENU — see [[balticporter.tir.Remedy]] and `DESIGN.md` §8.16.
    *
    * Two entries, both `accept`-shaped, both at an EXTERNAL callee — the ONE-SPELLING rule rules
    * out the rest: `UnmappedSubtype` and `ScopedOut` already have a manifest key (`typeMap`/`scope`
    * — the latter also RULED OUT as a general residue reduction, K16), and `OpaqueEgress`'s wrap is
    * `reflectiveSinks`. `wrap-at-seam` and `copy-detach` are ABSENT rather than unspelled: a row
    * here only exists where the phase found no factory (K2.5) or a copy would silently detach both
    * directions (K15). `InexpressibleParent`/`ReifiedOccurrence` are known divergences the engine
    * refuses to repair (K5.7, K18), not review lists an `accept` could drain.
    */
  def remedies: List[Remedy] = List(
    Remedy(
      id = "accept-external-callee", lane = Name, kind = Issue.ExternalCallee.toString,
      emissionAffecting = false, fix = FixKind.Parameterised,
      subject = Remedy.Subject.ExternalMember,
      what = "the port has READ this seam and states that the value crossing it is one this callee " +
        "handles — the row moves to remediation(resolved), the emitted text does not change, and " +
        "nothing is wrapped or copied"),
    Remedy(
      id = "accept-opaque-egress", lane = Name, kind = Issue.OpaqueEgress.toString,
      emissionAffecting = false, fix = FixKind.Parameterised,
      subject = Remedy.Subject.ExternalMember,
      what = "the port has READ this external method and states that it does NOT read the runtime " +
        "representation it is handed — the complement of a `reflectiveSinks` entry, which is the " +
        "only answer this review list previously had no way to record"),
  )

  /** what kind of stranding this is, which is what decides who fixes it (CLAUDE.md §1). */
  enum Issue:
    /** the JDK type is a SUBTYPE of one the mapping covers — [[CollectionClosureCheck]]'s closure
      * hole, met here as a site. */
    case UnmappedSubtype
    /** a JDK collection family the engine deliberately does not retype at all. */
    case UntranslatedFamily
    /** a type that IS in the mapping and reached this slot anyway. */
    case MappedTypeSurvived
    /** both sides are the phase's own output, on opposite sides of the shim/scala split. */
    case ShimBoundary
    /** the same slot where the VALUE is not the phase's output: it is produced by a call the
      * phase's own static arms cover and DECLINED to rewrite, so the value is java's however the
      * position-blind node type reads. Kept apart from [[ShimBoundary]], whose classification
      * points at `coerce` instead. */
    case RefusedSource
    /** one side is a declaration the phase's [[balticporter.tir.RuleScope]] deliberately held back,
      * so it kept its JDK type while the code meeting it moved. */
    case ScopedOut
    /** the same slot where NO POLICY held the declaration back: it OVERRIDES a member whose
      * signature lives in a COMPILED CLASS FILE, so the phase could not move its formals (§4.56).
      * Kept apart from [[ScopedOut]] — there is no key anywhere to change here. */
    case ClassFileOverride
    /** one side is a method the PROGRAM DOES NOT DECLARE, whose signature cannot be retyped and
      * whose seam this check cannot otherwise see. Recorded BY THE PHASE while the external
      * signature is still readable, reported here so the whole residue is in one place. */
    case ExternalCallee
    /** a class the program declares IMPLEMENTS a java type this phase maps, and the target CANNOT
      * BE A PARENT, so the parent is left as java's rather than emitted at a type that cannot
      * carry it. */
    case InexpressibleParent
    /** a class the phase RE-PARENTED onto a `scala.collection` target owes a member that target
      * declares, and the bridge could not be built (`ENGINE-LIMITS.md` K28.1) — not a slot: the
      * class is simply missing a member scalac will demand the moment `RefChecks` runs. */
    case UnbridgedMember
    /** an `instanceof` or a downcast at a type this phase retyped, whose TARGET no live view can
      * be — a REIFIED occurrence, asking about a runtime object the retyping did not move, so it
      * is the one seam with no slot and no compile error behind it. */
    case ReifiedOccurrence
    /** an external callee with a `java.lang.Object` FORMAL, reached by a value this phase cannot
      * prove it did not retype. The slot is fine — the value conforms — but if the callee READS the
      * representation it was handed, it sees something different from the java it was ported from. */
    case OpaqueEgress

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
      case RefusedSource =>
        "§1(a) engine, and REFUSED on purpose UPSTREAM OF THIS SLOT: the value here comes from a " +
          "call `CollectionsTransform` covers and declined to rewrite — the aliasing " +
          "`Arrays.asList(arr)` is the measured one — so the emitted text keeps the JDK name and " +
          "the value really is a `java.util.*`. The `Found` side above is the NODE's type, which " +
          "the position-blind retyping moved on both sides of that call; do not read it as the " +
          "value's. Do NOT close this by wrapping: a factory over a refused value names the " +
          "WRAPPER instead of the boundary and the refusal stops being findable (ENGINE-LIMITS " +
          "K2.5, K6.5). It closes when the REFUSAL closes — for `asList` that is one frontend fact, " +
          "the erased element type."
      case ScopedOut =>
        "§1(b) PER-LIBRARY: one side of this slot is a declaration this port's " +
          "`CollectionsTransform(scope)` deliberately held back, so it kept its JDK type while the " +
          "other side moved. The direction that CAN be closed already is — a retyped value at a " +
          "held-back java formal goes through `JavaCollections.toJava`, a live view — so what is " +
          "left here is the other one: a value the held-back declaration PRODUCES, arriving where " +
          "the port expects a scala collection. Widen the scope to cover this declaration, or " +
          "narrow it to exclude the other side of the slot too. The engine needs no change; a " +
          "scope that produced this seam SILENTLY would be worse than no scope."
      case ClassFileOverride =>
        "§1(a) engine, and REFUSED on purpose — WITH NO KEY TO CHANGE, which is the whole reason " +
          "this is not a `ScopedOut` row. One side of this slot is a declaration that OVERRIDES a " +
          "member declared in a COMPILED CLASS FILE (a java class the port extends but does not " +
          "convert), so its formals are a fact about that class file and no phase may move them " +
          "(§4.56): retyped, the member overrides NOTHING and its own `super.<same>(…)` call " +
          "cannot compile. The phase therefore holds the whole declaration literally, exactly as a " +
          "scope would — and the seam moves here, to the callers that hand it a value this phase " +
          "DID retype. The direction that can be closed already is: a retyped value at the held " +
          "java formal goes through `JavaCollections.toJava`, a live view. What is left is the " +
          "other one — a value the held-back member PRODUCES, arriving where the port expects a " +
          "scala collection. Nothing in this port's manifest widens or narrows this; it closes " +
          "when the class file's own type is one the mapping covers, i.e. when the parent is a " +
          "shim rather than a java class the mapping leaves alone (`collection-closure` names it)."
      case InexpressibleParent =>
        "§1(a) engine, and REFUSED on purpose: this class IMPLEMENTS a java type the mapping " +
          "covers, and the target cannot BE a parent — `scala.Tuple2` is final, has no `setValue` " +
          "and takes its two components in its constructor, so `extends Tuple2[K, V]` is three " +
          "errors with no fix available from inside the class. The parent is therefore left as " +
          "JAVA's: the class really does implement `java.util.Map.Entry`, which is on the " +
          "classpath and whose members it already declares, so the class compiles and the error " +
          "moves to the SLOTS where the port hands it to a `Tuple2` — which is where it belongs. " +
          "A second target for the implements-case is NOT the fix: `entrySet()` yields a `Tuple2` " +
          "everywhere in every port, so a shim-typed class would need a coercion at every crossing " +
          "in both directions, which is a second truth about one java type (ENGINE-LIMITS K5.7)."
      case UnbridgedMember =>
        "§1(a) engine: this class IMPLEMENTS a java collection interface, so the mapping emitted a " +
          "`scala.collection` parent for it — and that parent declares a member the class has no " +
          "java member to build from, or whose java member could not be renamed out of the way. " +
          "The bridge (`ENGINE-LIMITS.md` K28.1) renames java's member and synthesises scala's " +
          "over it, delegating; the guard that declined is named in the slot. Nothing is wrong with " +
          "any SLOT here — the class is simply missing a member scalac demands, which `RefChecks` " +
          "does not report until the port reaches 0 typer errors (CLAUDE.md §3), so this count is " +
          "the only instrument that sees it before then."
      case ReifiedOccurrence =>
        "§1(a) engine, and REFUSED on purpose: this is an `instanceof` or a downcast at a type the " +
          "mapping covers — a REIFIED occurrence, which asks about a RUNTIME OBJECT while the " +
          "retyping moved only the static type. `JavaCollections.Reified` answers java's question " +
          "over both representations wherever the target is one a live view can BE " +
          "(`mutable.Buffer`/`Set`/`Map`, the three shims); this site's target is a CONCRETE one " +
          "(`mutable.HashMap`, `ArrayBuffer`, `ArrayDeque`, `scala.Tuple2`) that no view can be, so " +
          "there is nothing to coerce to. Note there is no compile error to look for and never " +
          "was: the emitted test and cast are valid Scala asking a DIFFERENT question, so this " +
          "count is the only instrument that sees the site (`ENGINE-LIMITS.md` K18). Closing it " +
          "needs the mapping to send this java type to an abstract target, or the java code to " +
          "test the interface rather than the implementation class."
      case OpaqueEgress =>
        "§1(b) PER-LIBRARY, and NOTHING IS BROKEN AT THIS SLOT — which is the whole reason it is " +
          "reported. The formal is `java.lang.Object`, so a collection this port retyped conforms " +
          "perfectly and there is no compile error, no wrong type and no failing check to look " +
          "for. What changed is what the CALLEE sees: java handed it a `java.util.*` and this port " +
          "hands it a `scala.collection.*`, so anything that reads the value's runtime " +
          "representation — a serialiser, a bean mapper, an injector, a `toString` — answers " +
          "differently (`ENGINE-LIMITS.md` K21 face 1: a JSON filter emitted " +
          "`{\"scala$collection$mutable$HashMap$$table\":[…]}` where java emitted the entries). " +
          "This row is a REVIEW LIST and not a defect: one line per external method, and most of " +
          "them do not care. Where one does, name its owner in " +
          "`CollectionsTransform(reflectiveSinks)` and the phase bridges the argument through " +
          "`JavaCollections.Reified.toJavaValue` — a run-time, deep, live view, because the " +
          "argument's own static type is `Object` too and nothing at the call site can decide it. " +
          "The engine cannot derive the list: which dependency reflects is a fact about the " +
          "library, and java itself guarantees no such type."
      case ExternalCallee =>
        "§1(a) engine, and REFUSED here on purpose: the other side of this slot is a method the " +
          "program does not declare, so its signature is a fact about a compiled class file and no " +
          "phase can move it. Where a LIVE wrapper exists the phase inserts one " +
          "(`JavaCollections.fromJava`/`toJava`); this site is one where it does not — a target " +
          "with no converter, a nested element type a one-level wrap would silently lie about, or " +
          "a class file the frontend could only partly resolve. A COPY would compile and detach " +
          "both directions (§4.4), so the seam is counted instead. If the dependency is itself " +
          "portable, the answer is to PORT it rather than to bridge it. Where the formal is " +
          "`java.lang.Object` there is no compile error to look for and never was: the retyped " +
          "value CONFORMS, and what changed is what the callee's `toString`, `instanceof` and " +
          "serializer see."

  /** one stranded slot. */
  final case class Finding(issue: Issue, slot: String, expected: String, actual: String, origin: Origin, enclosing: SymId):
    def detail: String = s"$slot: Found $actual / Required $expected"
    def render: String = s"$issue $slot — Found $actual / Required $expected  (${origin.javaPath}:${origin.line})"
    def report(using program: Program): CheckReport.Finding =
      CheckReport.Finding(Name, issue.toString, owner, CheckReport.relativise(origin.javaPath),
                          origin.line, detail)

    /** the string both artifacts key on — the drained row's `remediation(resolved)` finding uses this
      * one too, so a reader can join the two halves of the move by the column they are looking at.
      * For the two remedied kinds `enclosing` is the CALLEE, which is what a selection names. */
    def owner(using program: Program): String =
      program.symbolOf(enclosing).map(_.fullName).getOrElse("?")

  /** DRAIN what this port selected — see [[remedies]] and `CLAUDE.md` §5. Returns the findings that
    * remain; the rest are in the plan's ledger and become `remediation(resolved)` rows. */
  def resolved(plan: ResolutionPlan, findings: List[Finding])(using Program): List[Finding] =
    plan.drain(remedies, findings)(f =>
      ResolutionPlan.Residue(f.issue.toString, f.enclosing, f.owner, f.origin, f.detail))

  /** JDK collection families that are NOT in the closure of anything `typeMap` covers and are not
    * retyped at all. `java.util.stream` is the whole of it today; the refusal is deliberate and
    * its sites must still be counted, not left as prose (CLAUDE.md §5.1). */
  val untranslatedFamilies: List[String] = List("java.util.stream.")

  /** WHY a declaration's type is read LITERALLY rather than through the mapping. An enum rather
    * than a flag because the two refusals have different §1 classifications: `Scoped` names a
    * manifest key a port can edit (`CollectionsTransform(scope)`); `ClassFile` — java put this
    * signature in a compiled class file — names none. Reported as one kind, half the rows would
    * send their reader after a key that does not exist. */
  enum Held:
    case No, Scoped, ClassFile

  object Held:
    /** the SLOT name for the receiver arm — kept verbatim per case rather than generalised, because
      * these strings are baselined (`findings.tsv`) and a rename is a diff nobody can read. */
    def slotOf(h: Held): String = h match
      case ClassFile => "argument (class-file-override receiver)"
      case _         => "argument (scoped-out receiver)"

  private enum Side:
    case Jdk, Shim, Scala, Universal, Other

  /** which side of the boundary a type is on, decided from the MAPPING's own targets wherever a
    * choice exists. The shim side is `targets` restricted to the runtime package, not a hardcoded
    * list, so a fourth shim widens this with nothing to edit; the scala side is decided by PACKAGE
    * since the phase also mints `scala.collection.Set`/`Buffer` types outside `typeMap`.
    * `Universal` (`java.lang.Object`) is its own side, not `Other`: a retyped value CONFORMS there,
    * producing no compile error, while the callee behind it may see a value java never handed it. */
  private def sideOf(fqn: String, shims: Set[String]): Side =
    if shims.contains(fqn) then Side.Shim
    else if fqn.startsWith("scala.collection.") then Side.Scala
    else if CollectionClosureCheck.jdkFamily.contains(fqn) || untranslatedFamilies.exists(fqn.startsWith) then Side.Jdk
    else if fqn == CollectionsTransform.ObjectFqn then Side.Universal
    else Side.Other

  /** Every stranded slot in `program`, which must be the program AFTER the phase ran: this counts
    * the residue the retyping created, so running it before means counting nothing. `mapped` and
    * `targets` are the phase's own policy, read back, so the check concludes about a type only
    * from what the phase did to it (CLAUDE.md §4.56). */
  def check(program: Program, mapped: Set[String], targets: Set[String] = Set.empty,
            scopedOut: Set[SymId] = Set.empty, classFileOverrides: Set[SymId] = Set.empty): List[Finding] =
    check(program, program.units, mapped, targets, scopedOut, classFileOverrides)

  /** …restricted to the units the run actually EMITS: a DEPENDENT port's `Program` holds the base
    * module's units too, and a stranded slot inside one of those is the BASE's finding, reported by
    * a repository that cannot act on it (ENGINE-LIMITS D2). A base port passes `program.units`. */
  def check(program: Program, units: List[Tree.ClassDef], mapped: Set[String], targets: Set[String],
            scopedOut: Set[SymId], classFileOverrides: Set[SymId]): List[Finding] =
    val out   = collection.mutable.ListBuffer[Finding]()
    val shims = targets.filter(_.startsWith(RuntimeArtifact.Package + "."))
    given Program = program

    def fqn(t: TypeRepr): Option[String] = headSym(t).flatMap(program.symbolOf).map(_.fullName)

    def issueFor(jdk: String, held: Held, external: Boolean = false): Issue =
      // a MAPPED type reached from a CLASS FILE's own signature is not the same fact as one
      // surviving `transformType`, and must not be reported under `MappedTypeSurvived` (§1(a)
      // engine bug) — check `external` first.
      if external && mapped.contains(jdk) then Issue.ExternalCallee
      else if held == Held.Scoped && mapped.contains(jdk) then Issue.ScopedOut
      else if held == Held.ClassFile && mapped.contains(jdk) then Issue.ClassFileOverride
      else if mapped.contains(jdk) then Issue.MappedTypeSurvived
      else if untranslatedFamilies.exists(jdk.startsWith) then Issue.UntranslatedFamily
      else if CollectionClosureCheck.supertypesOf(jdk).exists(mapped.contains) then Issue.UnmappedSubtype
      // no mapped supertype at all: a gap in COVERAGE rather than a broken relation.
      else Issue.UnmappedSubtype

    /** The type a term REALLY has: for a reference to a declaration the phase read LITERALLY, not
      * the one the node carries — `CollectionsTransform.scopedType`, read through the same function
      * the transform uses, so what it refuses to rewrite and what this counts agree. Returns WHICH
      * literal-reading set answered, since the two carry different §1 classifications. */
    def actualOf(t: Term): (TypeRepr, Held) =
      CollectionsTransform.scopedType(t, scopedOut).map(_ -> Held.Scoped)
        .orElse(CollectionsTransform.scopedType(t, classFileOverrides).map(_ -> Held.ClassFile))
        .getOrElse(t.tpe -> Held.No)

    /** which set held THIS declaration back — the same question at a symbol rather than at a term. */
    def heldOf(s: SymId): Held =
      if scopedOut(s) then Held.Scoped else if classFileOverrides(s) then Held.ClassFile else Held.No

    /** is this callee a THIRD PARTY's, rather than one of the collection API's own members? Excludes
      * e.g. `java.util.List#indexOf(Object)` whose RECEIVER has already been retyped — the call
      * binds to scala's own `indexOf`, so the class file's formal describes nothing emitted. */
    def foreign(m: SymId): Boolean =
      !program.owns(m) && !program.symbolOf(m).flatMap(c => program.symbolOf(c.owner))
        .exists(o => mapped.contains(o.fullName) || targets.contains(o.fullName))

    /** is this value produced by a call the phase's own static arms cover and DECLINED to rewrite?
      * Every arm that FIRED left its minted helper's symbol behind, so a callee still standing at
      * one of these `owner#name` keys is one the phase left under the JDK's name (§4.56). */
    def refusedSource(t: Term): Boolean = t match
      case a: Tree.Apply =>
        program.symbolOf(a.method).flatMap(c => program.symbolOf(c.owner).map(o => MemberKey(o.fullName, c.name).render))
          .exists(CollectionsTransform.handledStatics.contains)
      case _ => false

    def slot(kind: String, expected: TypeRepr, actual: Term, origin: Origin, enclosing: SymId,
             expectedHeld: Held, expectedExternal: Boolean = false,
             expectedForeign: Boolean = false): Unit =
      val (actualT, actualHeld) = actualOf(actual)
      val held = if expectedHeld != Held.No then expectedHeld else actualHeld
      (fqn(expected), fqn(actualT)) match
        case (Some(e), Some(a)) if e != a =>
          (sideOf(e, shims), sideOf(a, shims)) match
            // `expectedExternal` describes the EXPECTED side only.
            case (Side.Jdk, Side.Scala | Side.Shim) => out += Finding(issueFor(e, held, expectedExternal), kind, e, a, origin, enclosing)
            case (Side.Scala | Side.Shim, Side.Jdk) => out += Finding(issueFor(a, held), kind, e, a, origin, enclosing)
            case (Side.Shim, Side.Scala) | (Side.Scala, Side.Shim) =>
              // a call still standing at a `handledStatic` name is one the phase declined to
              // rewrite, so the value is java's however the node reads.
              out += Finding(if refusedSource(actual) then Issue.RefusedSource else Issue.ShimBoundary,
                             kind, e, a, origin, enclosing)
            // java's UNIVERSAL formal at a CLASS FILE: produces no compile error, since a retyped
            // collection conforms. OWNED callees and the collection API's own members (see
            // `foreign`) are excluded by `expectedForeign`.
            case (Side.Universal, Side.Scala | Side.Shim) if expectedForeign =>
              out += Finding(Issue.ExternalCallee, kind, e, a, origin, enclosing)
            case _ => ()
        case _ => ()

    val scan = new Phase:
      def name: String = "collection-boundary-check"

      override def transformApply(t: Tree.Apply)(using Program): Term =
        val formals = program.symbolOf(t.method).map(_.info).collect {
          case TypeRepr.MethodType(ps, _, _)                       => ps.map(_._2)
          case TypeRepr.PolyType(_, TypeRepr.MethodType(ps, _, _)) => ps.map(_._2)
        }.getOrElse(Nil)
        if formals.sizeIs == t.args.size then
          val external = !program.owns(t.method)
          val third    = foreign(t.method)
          t.args.zip(formals).foreach((a, f) =>
            slot("argument", f, a, a.origin, t.method, heldOf(t.method), external, third))
        else onScopedReceiver(t)
        t

      /** The scope seam the four slot kinds CANNOT reach: an argument whose FORMAL is unknown. A
        * call on a scoped-out declaration binds to the JDK's own API — `b.raw.addAll(mine)` where
        * `raw` kept its `java.util.List` — and `addAll` is an EXTERNAL symbol with no interned
        * signature, so the arm above skips it entirely. So the JDK's own contract stands in,
        * licensed only where the receiver resolves THROUGH A SCOPED-OUT DECLARATION (never the
        * node's own, position-blind-retyped type) to a mapped type. Gated on
        * `scopedOut`/`classFileOverrides`, so a run holding nothing back cannot reach this at all. */
      private def onScopedReceiver(t: Tree.Apply)(using Program): Unit = t.fun match
        case Tree.Select(recv, _, _, _) =>
          val (recvT, recvHeld) = actualOf(recv)
          if recvHeld != Held.No then
            fqn(recvT).filter(r => sideOf(r, shims) == Side.Jdk).foreach { r =>
              t.args.foreach { a =>
                val (actualT, _) = actualOf(a)
                fqn(actualT).filter(x => sideOf(x, shims) == Side.Scala || sideOf(x, shims) == Side.Shim)
                  .foreach(x => out += Finding(issueFor(r, recvHeld), Held.slotOf(recvHeld),
                                              r, x, a.origin, t.method))
              }
            }
        case _ => ()

      override def transformTerm(t: Term)(using Program): Term =
        t match
          case a: Tree.Assign =>
            val (lhsT, lhsScoped) = actualOf(a.lhs)
            slot("assignment", lhsT, a.rhs, a.origin, SymId.None, lhsScoped)
          case _ => ()
        t

      override def transformValDef(t: Tree.ValDef)(using Program): Tree.ValDef =
        t.rhs.foreach(r => slot("declaration", t.tpt.tpe, r, t.origin, t.symbol, heldOf(t.symbol)))
        t

      override def transformDefDef(t: Tree.DefDef)(using Program): Tree.DefDef =
        t.rhs.foreach(b => returnsIn(b).foreach { r =>
          r.expr.foreach(e => slot("return", t.returnTpt.tpe, e, r.origin, t.symbol, heldOf(t.symbol)))
        })
        t

    units.foreach(u => StandardTraversal.mapClassDef(scan, u))
    out.toList

  /** every `return` that belongs to THIS method — the same DELIBERATELY BOUNDED walk
    * `CollectionsTransform.coerceReturns` performs. A `return` inside a lambda, an anonymous
    * class's method or a local class returns from THAT, so comparing it against the enclosing
    * method's declared type would be a WRONG finding — only statement-carrying node kinds are
    * followed, and the default arm does NOT descend, making a node kind added later a MISSED
    * finding rather than a wrong one. Pinned against `CollectionsTransform.coerceReturns` by spec. */
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
