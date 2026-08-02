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

  // -- a Scala KEYWORD as a PACKAGE SEGMENT ---------------------------------------------------
  //
  // Java's keyword set is not Scala's, so `com.fasterxml.jackson.core.type.TypeReference` is a
  // legal java FQN and an unparseable scala path. `esc` answers for an IDENTIFIER and every name
  // the emitter renders by hand goes through it; a `Symbol.fullName` is a PATH and used to reach
  // the output verbatim, so no package segment was ever escaped. Cut only at §4.56's separators.

  test("escPath backticks every keyword segment, cutting only at a separator") {
    assertEquals(TirEmitter.escPath("com.x.type.Ref"), "com.x.`type`.Ref")
    assertEquals(TirEmitter.escPath("a.object.b.val.C$given#do"), "a.`object`.b.`val`.C$`given`#`do`")
    // a keyword is a whole SEGMENT or it is nothing — `typescript` and `values` are not keywords
    assertEquals(TirEmitter.escPath("com.typescript.values.T"), "com.typescript.values.T")
    // the top-level type's own simple name is a segment like any other
    assertEquals(TirEmitter.escPath("com.x.end"), "com.x.`end`")
    assertEquals(TirEmitter.escPath(""), "")
    assertEquals(TirEmitter.escPath("Foo"), "Foo")
  }

  test("a keyword package segment is escaped in the package clause, in a type and in a receiver") {
    val KFOO = SymId(11) // a type WE declare, in package `demo.type`
    val KREF = SymId(12) // an EXTERNAL type in package `com.x.type`
    val KFLD = SymId(13)
    val KSTA = SymId(14) // a static of the external type — a value-position path

    val fld = Tree.ValDef(KFLD, tt(TypeRef(NoPrefix, KREF)),
      rhs = Some(Tree.Select(Tree.Ident(KREF, TypeRef(NoPrefix, KREF), O), KSTA, TypeRef(NoPrefix, KREF), O)),
      origin = O)
    val cd  = Tree.ClassDef(KFOO, parents = Nil, selfType = None, body = List(fld), origin = O)
    val syms = SymbolTable(
      List(
        Symbol(KFOO, "Foo", "demo.type.Foo", Flags(), SymId.None, TypeRef(NoPrefix, KFOO)),
        Symbol(KREF, "Ref", "com.x.type.Ref", Flags(), SymId.None, NoType),
        Symbol(KFLD, "ref", "demo.type.Foo#ref", Flags(), KFOO, TypeRef(NoPrefix, KREF)),
        Symbol(KSTA, "EMPTY", "com.x.type.Ref#EMPTY", Flags(isStatic = true), KREF, TypeRef(NoPrefix, KREF)),
      )
    )
    val text = new TirEmitter(new Program(List(cd), syms, Xref.build(List(cd)), MemberIndex.empty)).emit
    assert(clue(text).contains("package demo.`type`"))
    assert(text.contains("com.x.`type`.Ref"))
    assert(!text.contains("x.type."), clue(text)) // no unescaped segment survives anywhere
  }

  test("a keyword MEMBER name is escaped — already true, and pinned so it stays true") {
    val MFOO = SymId(21)
    val MVAL = SymId(22)
    val MGET = SymId(23)
    val get = Tree.DefDef(MGET, paramss = List(Nil), returnTpt = tt(tInt),
      rhs = Some(Tree.Block(Nil,
        Tree.Return(Some(Tree.Select(Tree.This(MFOO, TypeRef(NoPrefix, MFOO), O), MVAL, tInt, O)), tInt, O), tInt, O)),
      origin = O)
    val cd = Tree.ClassDef(MFOO, parents = Nil, selfType = None,
      body = List(Tree.ValDef(MVAL, tt(tInt), rhs = None, origin = O), get), origin = O)
    val syms = SymbolTable(
      List(
        Symbol(MFOO, "Foo", "demo.Foo", Flags(), SymId.None, TypeRef(NoPrefix, MFOO)),
        Symbol(INT, "Int", "scala.Int", Flags(), SymId.None, NoType),
        Symbol(MVAL, "type", "demo.Foo#type", Flags(isMutable = true), MFOO, tInt),
        Symbol(MGET, "match", "demo.Foo#match", Flags(), MFOO, MethodType(Nil, tInt)),
      )
    )
    val text = new TirEmitter(new Program(List(cd), syms, Xref.build(List(cd)), MemberIndex.empty)).emit
    assert(clue(text).contains("var `type`: scala.Int"))
    assert(text.contains("def `match`(): scala.Int"))
    assert(text.contains("return this.`type`"))
  }
