package balticporter.tir

import TypeRepr.*

/** The printer's own gate. A pretty-printer with no test is a pretty-printer that quietly stops
  * descending — the same defect shape CLAUDE.md §3 records for hand-rolled traversals, and one
  * that is invisible precisely because the output still *looks* fine. */
class TirPrinterSpec extends munit.FunSuite:

  given Program = TinyProgram.program

  private val expectedCanonical =
    """ClassDef class p.Foo
      |  tparams
      |    TypeDef p.Foo.T = ?
      |  parents
      |    TypeTree p.Base
      |  body
      |    ValDef var p.Foo.count: scala.Int
      |      rhs
      |        Literal 0 : scala.Int
      |    DefDef p.Foo.add: scala.Int
      |      params[0]
      |        ValDef val p.Foo.add.x: p.Foo.T
      |      rhs
      |        Block : scala.Int
      |          stats
      |            Assign : scala.Int
      |              lhs
      |                Ident p.Foo.count : scala.Int
      |              rhs
      |                Apply scala.Int.+ : scala.Int
      |                  fun
      |                    Select scala.Int.+ : scala.Int
      |                      qual
      |                        Ident p.Foo.count : scala.Int
      |                  args
      |                    Literal 1 : scala.Int
      |          expr
      |            Ident p.Foo.count : scala.Int
      |""".stripMargin

  test("canonical rendering of a whole unit is exactly this") {
    assertNoDiff(TirPrinter.canonical(TinyProgram.foo), expectedCanonical)
  }

  test("canonical carries no SymId and no source location — both are unstable across runs") {
    val out = TirPrinter.canonical(TinyProgram.foo)
    assert(!out.contains("#"), s"canonical leaked a symbol id:\n$out")
    assert(!out.contains("Foo.java"), s"canonical leaked a source location:\n$out")
  }

  test("debug rendering carries the SymId and the Java origin — that is what it is for") {
    val out = TirPrinter.render(TinyProgram.foo, TirPrinter.Style.debug)
    assert(out.contains("p.Foo#1"), out)
    assert(out.contains("@/abs/src/p/Foo.java:3"), out)
  }

  test("digest is stable for the same tree and moves when the tree's MEANING changes") {
    val a = TirPrinter.digest(TinyProgram.foo)
    assertEquals(a, TirPrinter.digest(TinyProgram.foo))
    // same shape, different initialiser
    val changed = TinyProgram.foo.copy(body = List(
      TinyProgram.countDef.copy(rhs = Some(Tree.Literal(Constant.IntC(7), TinyProgram.tInt, TinyProgram.O))),
      TinyProgram.addDef))
    assertNotEquals(a, TirPrinter.digest(changed))
  }

  test("digest ignores what only DEBUG shows — a line shift is not a semantic change") {
    val moved = TinyProgram.foo.copy(origin = Origin("/abs/src/p/Foo.java", 99, 4))
    assertEquals(TirPrinter.digest(TinyProgram.foo), TirPrinter.digest(moved))
  }

  test("ParamRef renders by binder-relative NAME, never by re-printing the binder") {
    val binder = MethodType(List("self" -> TinyProgram.tBase, "n" -> TinyProgram.tInt), TinyProgram.tInt)
    assertEquals(TirPrinter.tpe(ParamRef(binder, 0), TirPrinter.Style.canonical), "self")
    assertEquals(TirPrinter.tpe(ParamRef(binder, 1), TirPrinter.Style.canonical), "n")
    // out of range is a name, not an exception and not a blank
    assertEquals(TirPrinter.tpe(ParamRef(binder, 5), TirPrinter.Style.canonical), "_$5")
    // and a ParamRef INSIDE its own binder does not re-expand it
    val inside = MethodType(List("a" -> TinyProgram.tInt), ParamRef(binder, 0))
    assertEquals(TirPrinter.tpe(inside, TirPrinter.Style.canonical), "(a: scala.Int): self")
  }

  test("types print in surface syntax, once") {
    val wildcardList = AppliedType(TypeRef(NoPrefix, TinyProgram.BASE), List(TypeBounds(NoType, TinyProgram.tInt)))
    assertEquals(TirPrinter.tpe(wildcardList, TirPrinter.Style.canonical), "p.Base[? <: scala.Int]")
    assertEquals(TirPrinter.tpe(AndType(TinyProgram.tBase, TinyProgram.tInt), TirPrinter.Style.canonical),
                 "(p.Base & scala.Int)")
    assertEquals(TirPrinter.tpe(ByNameType(TinyProgram.tInt), TirPrinter.Style.canonical), "=> scala.Int")
  }

  test("an unresolvable symbol is NAMED as unknown, never silently blank") {
    val orphan = Tree.Ident(SymId(999), TinyProgram.tInt, Origin.synthetic)
    assert(TirPrinter.canonical(orphan).contains("<unknown:999>"), TirPrinter.canonical(orphan))
  }

  test("program() orders units by full name, not by the frontend's file walk") {
    val bar = TinyProgram.foo.copy(symbol = TinyProgram.BASE, body = Nil, tparams = Nil, parents = Nil)
    val p1  = new Program(List(TinyProgram.foo, bar), TinyProgram.symbols, Xref.build(List(TinyProgram.foo, bar)))
    val p2  = new Program(List(bar, TinyProgram.foo), TinyProgram.symbols, Xref.build(List(bar, TinyProgram.foo)))
    assertEquals(TirPrinter.program()(using p1), TirPrinter.program()(using p2))
  }
