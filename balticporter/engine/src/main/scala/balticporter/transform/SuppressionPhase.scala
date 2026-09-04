package balticporter.transform

import balticporter.tir.*

/** A LATE phase (after every retyping phase) that annotates members to suppress two scalac
  * warnings a `-Werror` build cannot avoid: `@nowarn("msg=deprecated")` on a body calling
  * `.orNull`, and `@nowarn("msg=Unreachable case")` on a `match` translating a java enum's
  * `default:` where scalac proves exhaustiveness java has no such rule for. `runsBefore`
  * `package-rename` (FQN is scala-side). CLAUDE.md §1(a) */
final class SuppressionPhase extends Phase:

  def name = SuppressionPhase.Name

  override def runsAfter: Set[String] = Set(
    "nullability",
    "java-collections->scala",
    "type-redirect",
    "globals->implicits",
  )
  override def runsBefore: Set[String] = Set("package-rename")

  override def run(program: Program): Program =
    given Program = program

    // Scan 1: members whose bodies call `.orNull` (deprecated); OrNullScan owns the counting
    val deprecatedMembers = collection.mutable.Set[SymId]()

    def hasOrNull(body: Term): Boolean = OrNullScan.count(body) > 0

    // Scan 2: members whose bodies contain a match on an enum type where all constants are
    // covered and a `case _ =>` exists — scalac's E030 fires where java's own switch has no
    // exhaustiveness rule (JLS 14.11), so the honest image is @nowarn rather than a dropped arm.
    val unreachableCaseMembers = collection.mutable.Set[SymId]()

    def hasExhaustiveEnumDefault(body: Term): Boolean = StandardTraversal.scanTerm(body, false) {
      case (true, _) => true
      case (_, m: Tree.Match) if m.cases.exists(_.isDefault) =>
        SuppressionPhase.isExhaustiveEnumMatch(m, program) || false
      case (acc, _) => acc
    }

    /** Scan a class body for both `.orNull` references and exhaustive enum matches. Runs after
      * TestFrameworkTransform, so a converted test body is already a class-body statement. */
    def scanBody(members: List[Statement], classSym: SymId): Unit =
      members.foreach {
        // a constructor's rendered statements are the emitter's (`TirEmitter.orNullCtors`)
        case d: Tree.DefDef if program.symbolOf(d.symbol).exists(_.name == "<init>") => ()
        case d: Tree.DefDef =>
          d.rhs.foreach { body =>
            if hasOrNull(body) then deprecatedMembers += d.symbol
            if hasExhaustiveEnumDefault(body) then unreachableCaseMembers += d.symbol
          }
        case v: Tree.ValDef =>
          v.rhs.foreach { body =>
            if hasOrNull(body) then deprecatedMembers += v.symbol
            if hasExhaustiveEnumDefault(body) then unreachableCaseMembers += v.symbol
          }
        case _: Tree.ClassDef | _: Tree.TypeDef => ()
        case t: Term =>
          // a class-body statement (ctor code, or an inlined test body) — annotate the class
          if hasOrNull(t) then deprecatedMembers += classSym
          if hasExhaustiveEnumDefault(t) then unreachableCaseMembers += classSym
      }

    program.units.foreach { u =>
      StandardTraversal.allClassDefs(u).foreach { cd =>
        scanBody(cd.body, cd.symbol)
        cd.enumCases.foreach(ec => scanBody(ec.body, ec.symbol))
        StandardTraversal.allAnonClasses(cd).foreach { (anon, _) =>
          scanBody(anon.body, anon.symbol)
        }
      }
    }

    if deprecatedMembers.isEmpty && unreachableCaseMembers.isEmpty then return program

    // Find or create the @nowarn symbol
    val existingNowarn = program.symbols.all.find(_.fullName == "scala.annotation.nowarn").map(_.id)
    val nowarnSym = existingNowarn.getOrElse {
      val minId = program.symbols.all.map(_.id.raw).minOption.getOrElse(0)
      SymId(math.min(minId - 1, -2))
    }
    def mkNowarn(msg: String): Annot = Annot(
      tpe    = TypeRepr.TypeRef(TypeRepr.NoPrefix, nowarnSym),
      args   = List("value" -> Tree.Literal(
        Constant.StringC(msg),
        TypeRepr.TypeRef(TypeRepr.NoPrefix, SymId.None),
        Origin.synthetic)),
      origin = Origin.synthetic,
    )
    val deprecatedAnnot      = mkNowarn("msg=deprecated")
    val unreachableCaseAnnot = mkNowarn("msg=Unreachable case")

    // only add if the symbol does not already carry an @nowarn with the same msg; a member may
    // legitimately need both concerns, each its own annotation
    def hasNowarnMsg(s: Symbol, msg: String): Boolean = s.annotations.exists { a =>
      val isNowarn = program.symbolOf(a.tpe match {
        case TypeRepr.TypeRef(_, sym) => sym
        case _ => SymId.None
      }).exists(_.fullName == "scala.annotation.nowarn")
      isNowarn && a.args.exists {
        case ("value", Tree.Literal(Constant.StringC(v), _, _)) => v == msg
        case _ => false
      }
    }

    val toAddDeprecated      = deprecatedMembers.toSet.filter(id =>
      program.symbolOf(id).exists(s => !hasNowarnMsg(s, "msg=deprecated")))
    val toAddUnreachableCase = unreachableCaseMembers.toSet.filter(id =>
      program.symbolOf(id).exists(s => !hasNowarnMsg(s, "msg=Unreachable case")))

    if toAddDeprecated.isEmpty && toAddUnreachableCase.isEmpty then return program

    val updated = program.symbols.all.map { s =>
      val addDep   = toAddDeprecated.contains(s.id)
      val addUnr   = toAddUnreachableCase.contains(s.id)
      if addDep || addUnr then
        val newAnnots = s.annotations ++
          (if addDep then List(deprecatedAnnot) else Nil) ++
          (if addUnr then List(unreachableCaseAnnot) else Nil)
        s.copy(annotations = newAnnots)
      else s
    }
    val allSyms = if existingNowarn.isDefined then updated
                  else updated ++ List(Symbol(
                    nowarnSym, "nowarn", "scala.annotation.nowarn",
                    Flags(), SymId.None, TypeRepr.NoType))

    toAddDeprecated.toList.sortBy(_.raw).foreach { id =>
      program.symbolOf(id).foreach { s =>
        record(Decision(
          kind       = Decision.Kind.SuppressedWarning,
          subject    = id,
          subjectFqn = s.fullName,
          detail = Map(
            "annotation" -> "@nowarn(\"msg=deprecated\")",
            "why"        -> ("this member's body calls `.orNull` (the null-preserving unwrap at a " +
              "slot that accepts null); lls deprecates `orNull` as a lint so every usage needs " +
              "`@nowarn` — the same pattern sge uses at every Java interop boundary"),
          ),
          reason = Reason.Universal("suppressed-warning(orNull)"),
          origin = Decision.originOf(program, id),
        ))
      }
    }
    toAddUnreachableCase.toList.sortBy(_.raw).foreach { id =>
      program.symbolOf(id).foreach { s =>
        record(Decision(
          kind       = Decision.Kind.SuppressedWarning,
          subject    = id,
          subjectFqn = s.fullName,
          detail = Map(
            "annotation" -> "@nowarn(\"msg=Unreachable case\")",
            "why"        -> ("this member's body contains a match on a java enum where all " +
              "constants are covered by explicit cases and the `default` arm is translated as " +
              "`case _ =>`; scalac proves enum exhaustiveness (E030) but java has no such rule — " +
              "the default is java's defensive programming against future enum extensions"),
          ),
          reason = Reason.Universal("suppressed-warning(unreachable-enum-default)"),
          origin = Decision.originOf(program, id),
        ))
      }
    }

    program.rebuilt(symbols = SymbolTable(allSyms))

object SuppressionPhase:
  val Name = "suppressed-warnings"

  /** A `Tree.Match` whose scrutinee type is an enum, whose non-default/non-null case labels
    * cover every enum constant, and which has a `case _ =>` default arm — JLS 14.11 does not
    * require exhaustiveness for a classic switch, so scalac's proof is a fact java lacks. */
  def isExhaustiveEnumMatch(m: Tree.Match, program: Program): Boolean =
    val enumSym = m.scrutinee.tpe match
      case TypeRepr.TypeRef(_, sym) => sym
      case _ => return false

    val sym = program.symbolOf(enumSym)
    if !sym.exists(_.flags.isEnum) then return false

    val enumCaseCount = program.definitionOf(enumSym) match
      case Some(cd: Tree.ClassDef) => cd.enumCases.size
      case _ => return false
    if enumCaseCount == 0 then return false

    // exclude `case null =>` (§4.4's NPE guard), which the emitter adds and which is not java's own arm
    val explicitLabels = m.cases
      .filterNot(_.isDefault)
      .flatMap(_.labels)
      .count {
        case Tree.Literal(Constant.NullC, _, _) => false
        case _ => true
      }

    explicitLabels >= enumCaseCount
