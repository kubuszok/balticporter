package balticporter.transform

import balticporter.tir.*

class DefnodeNormalizationRuleSpec extends munit.FunSuite:

  private val o        = Origin.synthetic
  private val unitSym  = SymId(1)
  private val initSym  = SymId(2)
  private val fnSym    = SymId(3)
  private val baseSym  = SymId(4)
  private val localSym = SymId(5)

  private val builder = RuntimeClassBuilder("defineClass", "Node_", "Node_Base")

  /** `val local = defineClass("Leaf", "left right", null, null, Base)` inside one method of one unit. */
  private def program(): Program =
    def lit(c: Constant) = Tree.Literal(c, TypeRepr.NoType, o)
    val call             = Tree.Apply(
      Tree.Ident(fnSym, TypeRepr.NoType, o),
      List(lit(Constant.StringC("Leaf")), lit(Constant.StringC("left right")), lit(Constant.NullC), lit(Constant.NullC), Tree.Ident(baseSym, TypeRepr.NoType, o)),
      fnSym,
      TypeRepr.NoType,
      o
    )
    val local = Tree.ValDef(localSym, TypeTree(TypeRepr.NoType, o), Some(call), o)
    val body  = Tree.Block(List(local), lit(Constant.UnitC), TypeRepr.NoType, o)
    val init  = Tree.DefDef(initSym, Nil, TypeTree(TypeRepr.NoType, o), Some(body), o)
    val unit = Tree.ClassDef(unitSym, Nil, None, List(init), o)
    val syms = SymbolTable(
      List(
        Symbol(unitSym, "Module", "pkg.Module", Flags(), SymId.None, TypeRepr.NoType),
        Symbol(initSym, "init", "pkg.Module.init", Flags(), unitSym, TypeRepr.NoType),
        Symbol(fnSym, "defineClass", "pkg.Module.defineClass", Flags(), unitSym, TypeRepr.NoType),
        Symbol(baseSym, "Base", "pkg.Module.Base", Flags(), unitSym, TypeRepr.NoType),
        Symbol(localSym, "Leaf", "pkg.Module.init.Leaf", Flags(), initSym, TypeRepr.NoType)
      )
    )
    new Program(List(unit), syms, Xref.build(List(unit)), MemberIndex.empty)

  test("the empty builder recognises nothing, whatever the scope") {
    val before = program()
    val after  = DefnodeNormalizationRule(RuleScope.Only(Set("Leaf"))).run(before)
    assert(after eq before)
  }

  test("the empty scope recognises nothing, whatever the builder") {
    val before = program()
    assert(DefnodeNormalizationRule(RuleScope.Only(Set.empty), builder).run(before) eq before)
  }

  test("a builder call in scope mints the class the consumer's prefix spells, with its fields") {
    val after  = DefnodeNormalizationRule(RuleScope.Only(Set("Leaf")), builder).run(program())
    val minted = after.units.drop(1)
    assertEquals(minted.size, 1)
    val cls = after.symbols(minted.head.symbol)
    assertEquals(cls.fullName, "Node_Leaf")
    val fields = minted.head.body.collect { case v: Tree.ValDef => after.symbols(v.symbol).fullName }
    assertEquals(fields, List("Node_Leaf.left", "Node_Leaf.right"))
  }

  test("an empty policy contributes no fingerprint segment; the builder is surface") {
    assertEquals(DefnodeNormalizationRule(RuleScope.Only(Set.empty)).surfaceFingerprint, "")
    assertEquals(
      DefnodeNormalizationRule(RuleScope.Only(Set("Leaf")), builder).surfaceFingerprint,
      "scope=Leaf;builder=defineClass,Node_,Node_Base"
    )
  }

  test("two different builders refuse to merge; an empty one adopts the other") {
    val other = RuntimeClassBuilder("makeClass", "N", "NBase")
    assert(DefnodeNormalizationRule(RuleScope.Only(Set("A")), builder).mergedWith(DefnodeNormalizationRule(RuleScope.Only(Set("B")), other)).isLeft)
    val merged = DefnodeNormalizationRule(RuleScope.Only(Set("A"))).mergedWith(DefnodeNormalizationRule(RuleScope.Only(Set("B")), builder))
    assertEquals(merged.map(_.phase.asInstanceOf[DefnodeNormalizationRule].builder), Right(builder))
  }
