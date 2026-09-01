package balticporter.transform

import balticporter.runner.PortRun
import balticporter.tir.*

class UnusedSymbolTransformSpec extends munit.FunSuite:

  test("PortRun.derivedPhases includes UnusedSymbolTransform") {
    val phases = PortRun.derivedPhases
    assert(
      clue(phases).exists(_.isInstanceOf[UnusedSymbolTransform]),
      "UnusedSymbolTransform must be in PortRun.derivedPhases — it is §1(a) universal"
    )
  }

  test("UnusedSymbolTransform has correct ordering constraints") {
    val phase = new UnusedSymbolTransform
    assert(clue(phase.runsAfter).contains("nullability"))
    assert(clue(phase.runsAfter).contains("java-collections->scala"))
    assert(clue(phase.runsAfter).contains("type-redirect"))
    assert(clue(phase.runsAfter).contains("globals->implicits"))
    assert(clue(phase.runsBefore).contains("package-rename"))
    assert(clue(phase.runsBefore).contains(SuppressionPhase.Name),
      "must run before SuppressionPhase so the @nowarn annotations are visible to it")
  }

  test("UnusedSymbolTransform name") {
    assertEquals(new UnusedSymbolTransform().name, UnusedSymbolTransform.Name)
    assertEquals(UnusedSymbolTransform.Name, "unused-symbols")
  }

  test("isSideEffectFree: literal") {
    assert(UnusedSymbolTransform.isSideEffectFreeTerm(
      Tree.Literal(Constant.IntC(0), TypeRepr.NoType, Origin.synthetic)))
  }

  test("isSideEffectFree: null literal") {
    assert(UnusedSymbolTransform.isSideEffectFreeTerm(
      Tree.Literal(Constant.NullC, TypeRepr.NoType, Origin.synthetic)))
  }

  test("isSideEffectFree: ident (variable read)") {
    assert(UnusedSymbolTransform.isSideEffectFreeTerm(
      Tree.Ident(SymId(1), TypeRepr.NoType, Origin.synthetic)))
  }

  test("isSideEffectFree: this.field select") {
    assert(UnusedSymbolTransform.isSideEffectFreeTerm(
      Tree.Select(Tree.This(SymId(1), TypeRepr.NoType, Origin.synthetic),
                  SymId(2), TypeRepr.NoType, Origin.synthetic)))
  }

  test("isSideEffectFree: method call is NOT side-effect-free") {
    assert(!UnusedSymbolTransform.isSideEffectFreeTerm(
      Tree.Apply(
        Tree.Select(Tree.Ident(SymId(1), TypeRepr.NoType, Origin.synthetic),
                    SymId(2), TypeRepr.NoType, Origin.synthetic),
        Nil, SymId(2), TypeRepr.NoType, Origin.synthetic)))
  }

  test("isSideEffectFree: new object is NOT side-effect-free") {
    assert(!UnusedSymbolTransform.isSideEffectFreeTerm(
      Tree.New(TypeTree(TypeRepr.NoType, Origin.synthetic),
               TypeRepr.NoType, Origin.synthetic)))
  }

  test("isSideEffectFree: None (no init)") {
    assert(UnusedSymbolTransform.isSideEffectFree(None))
  }

  test("Decision.Kind.UnusedSymbolHandled exists") {
    // Verify the enum case compiles and has the right name
    assertEquals(Decision.Kind.UnusedSymbolHandled.toString, "UnusedSymbolHandled")
  }

  test("UnusedSymbolTransform runs BEFORE SuppressionPhase in derivedPhases") {
    val phases = PortRun.derivedPhases
    val unusedIdx = phases.indexWhere(_.isInstanceOf[UnusedSymbolTransform])
    val suppressionIdx = phases.indexWhere(_.isInstanceOf[SuppressionPhase])
    assert(unusedIdx >= 0, "UnusedSymbolTransform must be in derivedPhases")
    assert(suppressionIdx >= 0, "SuppressionPhase must be in derivedPhases")
    assert(unusedIdx < suppressionIdx,
      s"UnusedSymbolTransform (at $unusedIdx) must come before SuppressionPhase (at $suppressionIdx)")
  }
