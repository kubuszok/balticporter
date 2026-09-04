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

  test("check lane names are defined") {
    assertEquals(UnusedSymbolTransform.Handled, "unused-symbol(handled)")
    assertEquals(UnusedSymbolTransform.Refused, "unused-symbol(refused)")
  }

  test("RequiredChecks includes unused-symbol lanes") {
    assert(PortRun.RequiredChecks.contains(UnusedSymbolTransform.Handled),
      "unused-symbol(handled) must be in RequiredChecks — the phase is unconditional")
    assert(PortRun.RequiredChecks.contains(UnusedSymbolTransform.Refused),
      "unused-symbol(refused) must be in RequiredChecks — the phase is unconditional")
  }

  test("refCollector counts Tree.MethodRef references — a private method referenced only through this::visit must not be deleted") {
    // A private method referenced only via a method reference (this::visit, which becomes
    // Tree.MethodRef in the TIR) was falsely deleted because the refCollector did not count
    // Tree.MethodRef.method. Regression: ssg-md 0 -> 45 errors on two visitor classes whose
    // private visit methods are only used via VisitHandler<>(SomeType.class, this::visit).

    val classSym = SymId(100)
    val visitSym = SymId(101)
    val handlerSym = SymId(102)
    val ctorSym = SymId(103)

    val symbols = SymbolTable(List(
      Symbol(classSym, "C", "pkg.C", Flags(), SymId.None, TypeRepr.NoType),
      Symbol(visitSym, "visit", "pkg.C#visit",
        Flags(isPrivate = true), classSym, TypeRepr.NoType),
      Symbol(handlerSym, "myHandler", "pkg.C#myHandler",
        Flags(isPrivate = true), classSym, TypeRepr.NoType),
      Symbol(ctorSym, "<init>", "pkg.C#<init>", Flags(), classSym,
        TypeRepr.MethodType(Nil, TypeRepr.NoType, false)),
    ))

    // The class body: private def visit(...), private val myHandler = <expr using this::visit>
    val methodRefToVisit = Tree.MethodRef(
      qualifier = Right(Tree.This(classSym, TypeRepr.NoType, Origin.synthetic)),
      method = visitSym,
      tpe = TypeRepr.NoType,
      origin = Origin.synthetic,
      referent = Referent.Instance(1),
    )
    val visitDef = Tree.DefDef(
      symbol = visitSym,
      paramss = Nil,
      returnTpt = TypeTree(TypeRepr.NoType, Origin.synthetic),
      rhs = Some(Tree.Literal(Constant.UnitC, TypeRepr.NoType, Origin.synthetic)),
      origin = Origin.synthetic,
    )
    val handlerDef = Tree.ValDef(
      symbol = handlerSym,
      tpt = TypeTree(TypeRepr.NoType, Origin.synthetic),
      rhs = Some(methodRefToVisit),
      origin = Origin.synthetic,
    )
    val ctorDef = Tree.DefDef(
      symbol = ctorSym,
      paramss = Nil,
      returnTpt = TypeTree(TypeRepr.NoType, Origin.synthetic),
      rhs = Some(Tree.Literal(Constant.UnitC, TypeRepr.NoType, Origin.synthetic)),
      origin = Origin.synthetic,
    )

    val classDef = Tree.ClassDef(
      symbol = classSym,
      parents = Nil,
      selfType = None,
      body = List(ctorDef, visitDef, handlerDef),
      origin = Origin.synthetic,
    )

    given program: Program = new Program(List(classDef), symbols, Xref.build(List(classDef)), MemberIndex.empty)

    val phase = new UnusedSymbolTransform
    val result = phase.run(program)

    // visit should NOT have been deleted — it is referenced by the MethodRef
    val resultVisitDefs = result.units.flatMap(u =>
      StandardTraversal.allClassDefs(u)(using result).flatMap(_.body.collect {
        case d: Tree.DefDef if d.symbol == visitSym => d
      })
    )
    assert(resultVisitDefs.nonEmpty,
      "private visit method referenced only via Tree.MethodRef must NOT be deleted — " +
      "the refCollector must count Tree.MethodRef.method (ssg-md regression, 0 -> 45)")
  }

  test("UnusedSymbolHandled is NOT in PorterNote.Rendered") {
    assert(!balticporter.tir.PorterNote.Rendered(Decision.Kind.UnusedSymbolHandled),
      "UnusedSymbolHandled must NOT be in PorterNote.Rendered — deleted subjects have no " +
      "declaration, and suppressed subjects carry @nowarn which is self-documenting")
  }
