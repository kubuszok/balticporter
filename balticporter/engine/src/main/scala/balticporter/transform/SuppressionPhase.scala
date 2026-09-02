package balticporter.transform

import balticporter.tir.*

/** A LATE phase that annotates members to suppress scalac warnings a `-Werror` build cannot
  * avoid. Two concerns:
  *
  * 1. **Deprecated references** (`@nowarn("msg=deprecated")`): members whose bodies call `.orNull`
  *    (the null-preserving unwrap lls deprecates as a lint).
  * 2. **Unreachable enum default** (`@nowarn("msg=Unreachable case")`): a `match` on a java enum
  *    where all constants are covered by explicit cases and the `default:` arm is translated as
  *    `case _ =>`. Scalac proves exhaustiveness (E030) but java has no such rule -- the default
  *    is defensive programming against future enum extensions, and dropping it changes semantics
  *    (java falls out, scala throws `MatchError`).
  *
  * ==Why this is a separate phase==
  * The scan was inside `NullabilityTransform`, which runs BEFORE the retarget phases. A retarget
  * that removes the deprecated reference leaves the `@nowarn` annotation in place and
  * `-Wunused:nowarn` reports it: 237 stale annotations on libGDX core after the Array -> DynamicArray
  * retarget. Running the scan LATE -- after every retyping phase -- means it sees the FINAL tree and
  * annotates only members that still contain a deprecated call (or an exhaustive enum match).
  *
  * ==Kind==
  * CLAUDE.md §1(a). The mechanism is universal -- annotate where a deprecated symbol is called or
  * where an enum default is unreachable, so the reference compile under `-Werror` does not fail on
  * a lint the port cannot avoid. No policy: every port under `-Werror` needs this, and which
  * warnings fire is a fact about the TARGET LANGUAGE.
  *
  * ==Position==
  * `runsAfter` every retyping phase: `nullability`, `java-collections->scala`, `type-redirect`,
  * `globals->implicits`. `runsBefore` `package-rename` (the annotation's FQN is in the scala
  * namespace, not the upstream one). A base declares an empty instance at this position (§1.5)
  * so dependents that inherit it get the scan at the right place. */
final class SuppressionPhase extends Phase:

  def name = SuppressionPhase.Name

  // Run AFTER every retyping phase so the scan sees the final tree
  override def runsAfter: Set[String] = Set(
    "nullability",
    "java-collections->scala",
    "type-redirect",
    "globals->implicits",
  )
  override def runsBefore: Set[String] = Set("package-rename")

  override def run(program: Program): Program =
    given Program = program

    // ---------------------------------------------------------------
    // Scan 1: members whose bodies reference `.orNull` (deprecated)
    // ---------------------------------------------------------------
    val orNullSyms: Set[SymId] = program.symbols.all.iterator
      .filter(s => s.name == "orNull" && s.fullName.endsWith(".orNull"))
      .map(_.id).toSet

    val deprecatedMembers = collection.mutable.Set[SymId]()

    def hasOrNull(body: Term): Boolean = StandardTraversal.scanTerm(body, false) {
      case (true, _) => true
      case (_, Tree.Select(_, s, _, _)) if orNullSyms(s) => true
      // Template-produced `.orNull` appears in Tree.Opaque raw text, not as a structured
      // Tree.Select. A Template like `"$recv.get($0).orNull"` renders into an Opaque whose
      // `raw` contains the literal text `.orNull`. Scan for it so SuppressionPhase places
      // `@nowarn("msg=deprecated")` on the enclosing member.
      case (_, t: Tree.Opaque) if t.raw.contains(".orNull") => true
      case (acc, _) => acc
    }

    // ---------------------------------------------------------------
    // Scan 2: members whose bodies contain a match on an enum type
    // where all enum constants are covered and a `case _ =>` exists.
    //
    // ==Why==
    // Java's `switch` on an enum commonly includes a `default` arm even when all constants are
    // listed — defensive programming against future enum extensions. The emitter faithfully
    // translates `default: break;` to `case _ => ()` and `default: throw X` to `case _ => throw X`.
    // Scalac proves enum exhaustiveness and warns `E030 Match case Unreachable`, which `-Werror`
    // promotes to an error. Java has no such exhaustiveness rule for enum switches, so the arm is
    // dead in SCALA ALONE — the honest image is `@nowarn("msg=Unreachable case")`.
    //
    // ==Kind==
    // CLAUDE.md §1(a). Universal — a fact about Java enums and Scala's exhaustiveness, true of
    // every codebase.
    // ---------------------------------------------------------------
    val unreachableCaseMembers = collection.mutable.Set[SymId]()

    def hasExhaustiveEnumDefault(body: Term): Boolean = StandardTraversal.scanTerm(body, false) {
      case (true, _) => true
      case (_, m: Tree.Match) if m.cases.exists(_.isDefault) =>
        SuppressionPhase.isExhaustiveEnumMatch(m, program) || false
      case (acc, _) => acc
    }

    /** Scan a class body for BOTH .orNull references and exhaustive enum matches.
      *
      * This phase runs AFTER TestFrameworkTransform, so a converted test's body is already a
      * class-body statement — the `case t: Term` arm catches it. No ownerFallback annotation
      * on the class is needed (the old scan in NullabilityTransform annotated the class too
      * because TestFrameworkTransform might later remove the DefDef; HERE the tree is final). */
    def scanBody(members: List[Statement], classSym: SymId): Unit =
      members.foreach {
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
          // a class-body STATEMENT (primary constructor code, or a test body that
          // TestFrameworkTransform already inlined) — annotate the CLASS
          if hasOrNull(t) then deprecatedMembers += classSym
          if hasExhaustiveEnumDefault(t) then unreachableCaseMembers += classSym
      }

    program.units.foreach { u =>
      StandardTraversal.allClassDefs(u).foreach { cd =>
        scanBody(cd.body, cd.symbol)
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

    // For each concern: only add if the symbol does not already carry ANY @nowarn with the same msg.
    // A member may legitimately need BOTH (deprecated + unreachable case) — each is a separate
    // annotation with its own msg filter.
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

    // Record decisions
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
    * cover every enum constant, and which has a `case _ =>` default arm.
    *
    * Java's `switch` on an enum commonly includes a `default` even when all constants are
    * listed (JLS 14.11 does not require exhaustiveness for classic switch statements). The
    * emitter translates `default:` to `case _ =>`, and scalac proves the arm unreachable
    * because all enum values are matched — an exhaustiveness fact JAVA LACKS. The honest
    * image is `@nowarn("msg=Unreachable case")`. */
  def isExhaustiveEnumMatch(m: Tree.Match, program: Program): Boolean =
    // 1. Resolve the scrutinee type to its symbol
    val enumSym = m.scrutinee.tpe match
      case TypeRepr.TypeRef(_, sym) => sym
      case _ => return false

    // 2. Check the symbol has isEnum
    val sym = program.symbolOf(enumSym)
    if !sym.exists(_.flags.isEnum) then return false

    // 3. Get the enum constant count from the ClassDef
    val enumCaseCount = program.definitionOf(enumSym) match
      case Some(cd: Tree.ClassDef) => cd.enumCases.size
      case _ => return false
    if enumCaseCount == 0 then return false

    // 4. Count non-default, non-null labels — each java case arm maps to one CaseDef
    // with one label (the enum constant reference). Filter out `case null =>` (§4.4)
    // which the emitter adds for the NPE guard.
    val explicitLabels = m.cases
      .filterNot(_.isDefault)
      .flatMap(_.labels)
      .count {
        case Tree.Literal(Constant.NullC, _, _) => false
        case _ => true
      }

    explicitLabels >= enumCaseCount
