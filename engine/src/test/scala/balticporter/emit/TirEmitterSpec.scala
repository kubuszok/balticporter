package balticporter.emit

import balticporter.tir.*
import balticporter.tir.TypeRepr.*

/** Emits a hand-built TIR `Program` to Scala source and checks the shape. Core-only (no
  * frontend), so it pins the backend's output independently of the populator. */
class TirEmitterSpec extends munit.FunSuite:

  private val FOO   = SymId(1)
  private val INT   = SymId(2)
  private val COUNT = SymId(3)
  private val INC   = SymId(4)

  private val O    = Origin.synthetic
  private val tInt = TypeRef(NoPrefix, INT)
  private def tt(t: TypeRepr) = TypeTree(t, O)

  private val countDef = Tree.ValDef(COUNT, tt(tInt), rhs = None, origin = O)
  private val incDef = Tree.DefDef(
    INC,
    paramss = List(Nil),
    returnTpt = tt(tInt),
    rhs = Some(Tree.Block(Nil, Tree.Return(Some(Tree.Select(Tree.This(FOO, TypeRef(NoPrefix, FOO), O), COUNT, tInt, O)), tInt, O), tInt, O)),
    origin = O,
  )
  private val foo = Tree.ClassDef(FOO, parents = Nil, selfType = None, body = List(countDef, incDef), origin = O)

  private val symbols = SymbolTable(
    List(
      Symbol(FOO, "Foo", "demo.Foo", Flags(), SymId.None, TypeRef(NoPrefix, FOO)),
      Symbol(INT, "Int", "scala.Int", Flags(), SymId.None, NoType),
      Symbol(COUNT, "count", "demo.Foo#count", Flags(isMutable = true), FOO, tInt),
      Symbol(INC, "inc", "demo.Foo#inc", Flags(), FOO, MethodType(Nil, tInt)),
    )
  )

  private val program = new Program(List(foo), symbols, Xref.build(List(foo)), MemberIndex.empty)
  private val out     = new TirEmitter(program).emit

  test("emits package, class, field and method with a body") {
    assert(clue(out).contains("package demo"))
    assert(out.contains("class Foo"))
    assert(out.contains("var count: scala.Int")) // external type stays qualified
    assert(out.contains("def inc(): scala.Int ="))
    assert(out.contains("return this.count"))    // symbol-resolved field access
  }
