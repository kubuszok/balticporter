package balticporter.tir

import TypeRepr.*

/** A hand-built four-member program, shared by the printer and pipeline specs.
  *
  * ```java
  * class Foo<T> extends Base {
  *   int count = 0;
  *   int add(T x) { count = count + 1; return count; }
  * }
  * ```
  *
  * Small enough that a golden rendering can be read and argued with, and wide enough to exercise
  * a type parameter, a parent, a field with an initialiser, a parameter list, a block, an
  * assignment, a nested call and two literals.
  */
object TinyProgram:
  val FOO   = SymId(1)
  val T     = SymId(2)
  val BASE  = SymId(3)
  val INT   = SymId(4)
  val COUNT = SymId(5)
  val ADD   = SymId(6)
  val X     = SymId(7)
  val PLUS  = SymId(8)

  val O: Origin = Origin("/abs/src/p/Foo.java", 3, 1)
  def tt(t: TypeRepr): TypeTree = TypeTree(t, O)

  val tInt: TypeRepr  = TypeRef(NoPrefix, INT)
  val tT: TypeRepr    = TypeRef(NoPrefix, T)
  val tBase: TypeRepr = TypeRef(NoPrefix, BASE)

  private def s(id: SymId, full: String, info: TypeRepr, flags: Flags = Flags()) =
    Symbol(id, full.substring(full.lastIndexOf('.') + 1), full, flags, SymId.None, info, origin = O)

  val symbols: SymbolTable = SymbolTable(List(
    s(FOO, "p.Foo", TypeRef(NoPrefix, FOO)),
    s(T, "p.Foo.T", AnyBounds),
    s(BASE, "p.Base", tBase),
    s(INT, "scala.Int", tInt),
    s(COUNT, "p.Foo.count", tInt, Flags(isMutable = true)),
    s(ADD, "p.Foo.add", MethodType(List("x" -> tT), tInt)),
    s(X, "p.Foo.add.x", tT, Flags(isParam = true)),
    s(PLUS, "scala.Int.+", MethodType(List("y" -> tInt), tInt)),
  ))

  private def countRef = Tree.Ident(COUNT, tInt, O)

  val countDef: Tree.ValDef = Tree.ValDef(COUNT, tt(tInt), Some(Tree.Literal(Constant.IntC(0), tInt, O)), O)

  val addDef: Tree.DefDef = Tree.DefDef(
    ADD,
    paramss = List(List(Tree.ValDef(X, tt(tT), scala.None, O))),
    returnTpt = tt(tInt),
    rhs = Some(Tree.Block(
      stats = List(Tree.Assign(
        countRef,
        Tree.Apply(Tree.Select(countRef, PLUS, tInt, O), List(Tree.Literal(Constant.IntC(1), tInt, O)), PLUS, tInt, O),
        tInt, O)),
      expr = countRef, tpe = tInt, origin = O)),
    origin = O,
  )

  val foo: Tree.ClassDef = Tree.ClassDef(
    symbol = FOO,
    parents = List(tt(tBase)),
    selfType = scala.None,
    body = List(countDef, addDef),
    origin = O,
    tparams = List(Tree.TypeDef(T, tt(AnyBounds), O)),
  )

  def program: Program = new Program(List(foo), symbols, Xref.build(List(foo)))
