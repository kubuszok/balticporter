package balticporter.corpus

import balticporter.runner.PortRun
import balticporter.transform.SuppressionPhase

/** `SuppressionPhase` is derived unconditionally by `PortRun` — not declared per port.
  *
  * ==Why==
  * The phase scans the FINAL tree and adds `@nowarn` annotations for two concerns:
  *   1. `.orNull` calls (minted by `NullabilityTransform` with a `Named` target) — deprecated
  *      lint under `-Werror -deprecation`.
  *   2. Exhaustive enum match defaults — a `match` on a java enum where all constants are covered
  *      and a `case _ =>` (from java's `default:`) is unreachable. Scalac proves exhaustiveness
  *      (E030) but java has no such rule.
  *
  * Declaring it per port is the §1.5 drift the conditional-lane pattern exists to prevent: the
  * next port using `Named` would silently lose its suppressions. `PortRun.derivedPhases` includes
  * it unconditionally, the same way `remedyPhases` includes the remedy phases.
  */
class SuppressionPhaseSpec extends munit.FunSuite:

  test("PortRun.derivedPhases includes SuppressionPhase") {
    val phases = PortRun.derivedPhases
    assert(
      clue(phases).exists(_.isInstanceOf[SuppressionPhase]),
      "SuppressionPhase must be in PortRun.derivedPhases — it is §1(a) universal and a no-op " +
        "when no Named nullability target is in the pipeline"
    )
  }

  test("SuppressionPhase has correct ordering constraints") {
    val phase = new SuppressionPhase
    // Must run AFTER every retyping phase (so it sees the FINAL tree)
    assert(clue(phase.runsAfter).contains("nullability"))
    assert(clue(phase.runsAfter).contains("java-collections->scala"))
    assert(clue(phase.runsAfter).contains("type-redirect"))
    assert(clue(phase.runsAfter).contains("globals->implicits"))
    // Must run BEFORE package-rename (the annotation FQN is in the scala namespace)
    assert(clue(phase.runsBefore).contains("package-rename"))
  }

  test("SuppressionPhase name is suppressed-warnings") {
    assertEquals(new SuppressionPhase().name, SuppressionPhase.Name)
    assertEquals(SuppressionPhase.Name, "suppressed-warnings")
  }

  test("isExhaustiveEnumMatch detects all-covered enum with default") {
    import balticporter.tir.*

    // Build a minimal enum type with two constants
    val enumSym = SymId(1)
    val addSym  = SymId(2)
    val remSym  = SymId(3)
    val methSym = SymId(4)
    val unitSym = SymId(5)

    val enumDef = Tree.ClassDef(
      symbol     = enumSym,
      parents    = Nil,
      selfType   = None,
      body       = Nil,
      origin     = Origin.synthetic,
      enumCases  = List(
        Tree.EnumCase(addSym, Nil, Nil, Origin.synthetic),
        Tree.EnumCase(remSym, Nil, Nil, Origin.synthetic),
      ),
    )
    val symbols = SymbolTable(List(
      Symbol(enumSym, "Type", "pkg.Type", Flags(isEnum = true), SymId.None, TypeRepr.NoType),
      Symbol(addSym, "Add", "pkg.Type.Add", Flags(), enumSym, TypeRepr.NoType),
      Symbol(remSym, "Remove", "pkg.Type.Remove", Flags(), enumSym, TypeRepr.NoType),
      Symbol(methSym, "process", "pkg.Foo#process()", Flags(), SymId.None, TypeRepr.NoType),
      Symbol(unitSym, "Unit", "scala.Unit", Flags(), SymId.None, TypeRepr.NoType),
    ))
    val enumTypeRef = TypeRepr.TypeRef(TypeRepr.NoPrefix, enumSym)
    val unitType    = TypeRepr.TypeRef(TypeRepr.NoPrefix, unitSym)

    // A match with all enum values covered + a default arm
    val exhaustiveMatch = Tree.Match(
      scrutinee = Tree.Ident(addSym, enumTypeRef, Origin.synthetic),
      cases = List(
        Tree.CaseDef(
          labels    = List(Tree.Literal(Constant.NullC, enumTypeRef, Origin.synthetic)),
          guard     = None,
          body      = Tree.Literal(Constant.UnitC, unitType, Origin.synthetic),
          isDefault = false,
        ),
        Tree.CaseDef(
          labels    = List(Tree.Select(Tree.Ident(enumSym, enumTypeRef, Origin.synthetic), addSym, enumTypeRef, Origin.synthetic)),
          guard     = None,
          body      = Tree.Literal(Constant.UnitC, unitType, Origin.synthetic),
          isDefault = false,
        ),
        Tree.CaseDef(
          labels    = List(Tree.Select(Tree.Ident(enumSym, enumTypeRef, Origin.synthetic), remSym, enumTypeRef, Origin.synthetic)),
          guard     = None,
          body      = Tree.Literal(Constant.UnitC, unitType, Origin.synthetic),
          isDefault = false,
        ),
        Tree.CaseDef(
          labels    = List(Tree.Ident(SymId.None, unitType, Origin.synthetic)),
          guard     = None,
          body      = Tree.Literal(Constant.UnitC, unitType, Origin.synthetic),
          isDefault = true,
        ),
      ),
      tpe    = unitType,
      origin = Origin.synthetic,
    )

    val xref = Xref.build(List(enumDef))
    val program = new Program(List(enumDef), symbols, xref, MemberIndex.empty)

    assert(
      SuppressionPhase.isExhaustiveEnumMatch(exhaustiveMatch, program),
      "A match with all enum constants covered and a default arm should be detected as exhaustive"
    )

    // A match missing one constant — the default IS reachable
    val nonExhaustiveMatch = exhaustiveMatch.copy(
      cases = exhaustiveMatch.cases.filter { c =>
        c.isDefault || c.labels.exists {
          case Tree.Select(_, s, _, _) => s != remSym
          case _                       => true
        }
      }
    )

    assert(
      !SuppressionPhase.isExhaustiveEnumMatch(nonExhaustiveMatch, program),
      "A match missing an enum constant should NOT be detected as exhaustive"
    )
  }
