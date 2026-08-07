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

  // -- an argument-position `Repeated` is the argument list's TAIL --------------------------------
  //
  // Invisible for one element or more (the node renders comma-joined, and so does the arg list);
  // decisive for ZERO, where a node rendering "" leaves `f(a, )`. Java's `Paths.get(".")` against
  // `get(String, String...)` is exactly that call.

  test("a Repeated argument flattens into the argument list, and an EMPTY one disappears") {
    val CLS = SymId(31)
    val M   = SymId(32)  // the emitted member
    val EXT = SymId(33)  // an external vararg callee
    val TS  = TypeRef(NoPrefix, SymId(34))

    def call(args: List[Term]) =
      Tree.Apply(Tree.Ident(EXT, NoType, O), args, EXT, TS, O)
    val body = Tree.Block(
      List(
        call(List(Tree.Literal(Constant.StringC("."), TS, O), Tree.Repeated(Nil, NoType, O))),
        call(List(Tree.Literal(Constant.StringC("%s"), TS, O),
                  Tree.Repeated(List(Tree.Literal(Constant.StringC("a"), TS, O),
                                     Tree.Literal(Constant.StringC("b"), TS, O)), NoType, O))),
      ),
      Tree.Literal(Constant.UnitC, NoType, O), NoType, O)
    val d = Tree.DefDef(M, paramss = List(Nil), returnTpt = tt(NoType), rhs = Some(body), origin = O)
    val cd = Tree.ClassDef(CLS, parents = Nil, selfType = None, body = List(d), origin = O)
    val syms = SymbolTable(List(
      Symbol(CLS, "Use", "demo.Use", Flags(), SymId.None, TypeRef(NoPrefix, CLS)),
      Symbol(M, "run", "demo.Use#run", Flags(), CLS, MethodType(Nil, NoType)),
      Symbol(EXT, "get", "ext.P#get", Flags(isStatic = true), SymId.None, NoType),
      Symbol(SymId(34), "String", "java.lang.String", Flags(), SymId.None, NoType),
    ))
    val text = new TirEmitter(new Program(List(cd), syms, Xref.build(List(cd)), MemberIndex.empty)).emit
    assert(clue(text).contains("""(".")"""), "an empty Repeated must leave no trailing separator")
    assert(!text.contains("""".", )"""), clue(text))
    assert(text.contains(""""%s", "a", "b""""))
  }

  // -- …and a `Spread` is ONE argument, rendered `xs*` --------------------------------------------
  //
  // The mirror node (K6.5, fourth case): java forwards an array it already holds through a `T...`
  // slot. Unspread at an EXTERNAL callee the array conforms as one element, silently where the
  // repeated element is `Object`. §6: `xs*`, never `xs: _*`.

  test("a Spread argument renders as the scala spread, and stays ONE argument") {
    val CLS = SymId(41)
    val M   = SymId(42)
    val EXT = SymId(43)
    val ARG = SymId(44)
    val TS  = TypeRef(NoPrefix, SymId(45))

    val body = Tree.Block(
      List(Tree.Apply(Tree.Ident(EXT, NoType, O),
        List(Tree.Literal(Constant.StringC("%s"), TS, O),
             Tree.Spread(Tree.Ident(ARG, TS, O), TS, O)), EXT, TS, O)),
      Tree.Literal(Constant.UnitC, NoType, O), NoType, O)
    val d = Tree.DefDef(M, paramss = List(List(Tree.ValDef(ARG, tt(TS), rhs = None, origin = O))),
      returnTpt = tt(NoType), rhs = Some(body), origin = O)
    val cd = Tree.ClassDef(CLS, parents = Nil, selfType = None, body = List(d), origin = O)
    val syms = SymbolTable(List(
      Symbol(CLS, "Fwd", "demo.Fwd", Flags(), SymId.None, TypeRef(NoPrefix, CLS)),
      Symbol(M, "run", "demo.Fwd#run", Flags(), CLS, MethodType(List("args" -> TS), NoType)),
      Symbol(ARG, "args", "demo.Fwd#run(args)", Flags(), M, TS),
      Symbol(EXT, "format", "java.lang.String#format", Flags(isStatic = true), SymId.None, NoType),
      Symbol(SymId(45), "String", "java.lang.String", Flags(), SymId.None, NoType),
    ))
    val text = new TirEmitter(new Program(List(cd), syms, Xref.build(List(cd)), MemberIndex.empty)).emit
    assert(clue(text).contains("""("%s", args*)"""), "a Spread is `xs*`, and one argument")
    assert(!text.contains(": _*"), clue(text))
  }

  // -- an INFERENCE VARIABLE must never reach the output (F5's emitter half) ----------------------
  //
  // `new ArrayList<>(((Collection<?>) value))` — liqp `LValue.java:154`. The diamond's argument has
  // no binder in the reading scope, so the frontend interns a marker symbol
  // (`Symbol.UnresolvedTypeVarPrefix`). Printed, it read `JavaCollection[? <: ?E]`: `?E` names
  // nothing, does not lex, and took the statement around it plus two further errors with it.
  // G2 settles the rendering — `?`, everywhere.

  test("an unresolved type variable renders as `?`, never as its marker name") {
    val CLS  = SymId(41)
    val FLD  = SymId(42)
    val LIST = SymId(43)
    val STUB = SymId(44) // the marker the frontend mints for an inferred argument

    // `JavaCollection[? <: ?E]` — a wildcard whose UPPER BOUND is the marker.
    val bounded = AppliedType(TypeRef(NoPrefix, LIST),
      List(TypeBounds(NoType, TypeRef(NoPrefix, STUB))))
    val cd = Tree.ClassDef(CLS, parents = Nil, selfType = None,
      body = List(Tree.ValDef(FLD, tt(bounded), rhs = None, origin = O)), origin = O)
    val syms = SymbolTable(List(
      Symbol(CLS, "Use", "demo.Use", Flags(), SymId.None, TypeRef(NoPrefix, CLS)),
      Symbol(LIST, "Coll", "rt.Coll", Flags(), SymId.None, NoType),
      Symbol(FLD, "xs", "demo.Use#xs", Flags(), CLS, bounded),
      Symbol(STUB, "E", Symbol.UnresolvedTypeVarPrefix + "E", Flags(), SymId.None, NoType),
    ))
    val text = new TirEmitter(new Program(List(cd), syms, Xref.build(List(cd)), MemberIndex.empty)).emit
    assert(!clue(text).contains("?E"), "an inference variable reached the output")
    // the bound said nothing, so the wildcard alone is the whole of what java said
    assert(text.contains("rt.Coll[?]"), clue(text))
  }

  test("a bare unresolved type variable renders `?` rather than its marker") {
    val CLS  = SymId(51)
    val FLD  = SymId(52)
    val STUB = SymId(53)
    val t = TypeRef(NoPrefix, STUB)
    val cd = Tree.ClassDef(CLS, parents = Nil, selfType = None,
      body = List(Tree.ValDef(FLD, tt(t), rhs = None, origin = O)), origin = O)
    val syms = SymbolTable(List(
      Symbol(CLS, "U2", "demo.U2", Flags(), SymId.None, TypeRef(NoPrefix, CLS)),
      Symbol(FLD, "x", "demo.U2#x", Flags(), CLS, t),
      Symbol(STUB, "T", Symbol.UnresolvedTypeVarPrefix + "T", Flags(), SymId.None, NoType),
    ))
    val text = new TirEmitter(new Program(List(cd), syms, Xref.build(List(cd)), MemberIndex.empty)).emit
    assert(!clue(text).contains("?T"), "an inference variable reached the output")
  }

  // -- an enhanced-for BINDING REASSIGNED in the body (F16) ---------------------------------------
  //
  // Java's `for (Object obj : array)` binding is an ordinary local and `obj = …` is legal; Scala's
  // generator binds a `val`, so the same body reads `Reassignment to val obj` (liqp
  // `Sort.java:111`). K7 covers the binding's declared TYPE and says nothing about its mutability.
  // The alias is re-bound each iteration, which is java's own semantics: java assigns the binding
  // afresh from the iterator every time round, so no write can leak into the next iteration.

  private def foreachBody(assignBinding: Boolean): String =
    val CLS  = SymId(61)
    val M    = SymId(62)
    val ARR  = SymId(63)
    val BND  = SymId(64)
    val OBJ  = TypeRef(NoPrefix, SymId(65))
    val arrT = AppliedType(TypeRef(NoPrefix, SymId(66)), List(OBJ))

    val bind = Tree.ValDef(BND, tt(OBJ), rhs = None, origin = O)
    val write: List[Statement] =
      if assignBinding then
        List(Tree.Assign(Tree.Ident(BND, OBJ, O), Tree.Literal(Constant.NullC, OBJ, O), NoType, O))
      else Nil
    val loop = Tree.ForEach(bind, Tree.Ident(ARR, arrT, O),
      Tree.Block(write, Tree.Ident(BND, OBJ, O), OBJ, O), NoType, O)
    val d = Tree.DefDef(M, paramss = List(Nil), returnTpt = tt(NoType),
      rhs = Some(Tree.Block(List(loop), Tree.Literal(Constant.UnitC, NoType, O), NoType, O)), origin = O)
    val cd = Tree.ClassDef(CLS, parents = Nil, selfType = None, body = List(d), origin = O)
    val syms = SymbolTable(List(
      Symbol(CLS, "S", "demo.S", Flags(), SymId.None, TypeRef(NoPrefix, CLS)),
      Symbol(M, "run", "demo.S#run", Flags(), CLS, MethodType(Nil, NoType)),
      Symbol(ARR, "array", "demo.S#array", Flags(), CLS, arrT),
      Symbol(BND, "obj", "demo.S#run$obj", Flags(), M, OBJ),
      Symbol(SymId(65), "Object", "java.lang.Object", Flags(), SymId.None, NoType),
      Symbol(SymId(66), "Array", "scala.Array", Flags(), SymId.None, NoType),
    ))
    new TirEmitter(new Program(List(cd), syms, Xref.build(List(cd)), MemberIndex.empty)).emit

  test("a for-each binding WRITTEN TO in the body becomes a shadowing var") {
    val text = foreachBody(assignBinding = true)
    assert(clue(text).contains("for (obj$e <- "), clue(text))
    assert(text.contains("var obj: java.lang.Object = obj$e"), clue(text))
    // no CAST: the widening is K7's reason to re-bind and this is not it — the generator already
    // yields the declared type.
    assert(!text.contains("asInstanceOf"), clue(text))
  }

  test("a for-each binding that is NOT written to keeps the plain generator") {
    val text = foreachBody(assignBinding = false)
    assert(clue(text).contains("for (obj <- "), clue(text))
    assert(!text.contains("obj$e"), clue(text))
  }

  // -- a CASE's GUARD ---------------------------------------------------------------------------
  //
  // `Tree.CaseDef.guard` reached the emitter, was carried by `Phase.mapTerm`'s `Match` arm and
  // printed by `TirPrinter`, and `matchStr` never rendered it — so every diagnostic said the guard
  // was there while the emitted arm matched every scrutinee the PATTERN matched. `ENGINE-LIMITS.md`
  // F5's shape, and unreachable from the corpus (nothing mints a guard: java's classic switch has
  // none and the pattern switch is refused), which is why it is pinned here rather than measured.
  // Found by `EmissionFieldCoverageSpec`, which is the instrument that can see a field the emitter
  // silently does not read.

  private def guardedSwitch(guard: Option[Term]): String =
    val CLS = SymId(80); val M = SymId(81); val X = SymId(82); val I = SymId(83); val BL = SymId(84)
    val tI  = TypeRef(NoPrefix, I)
    val scr = Tree.Select(Tree.This(CLS, TypeRef(NoPrefix, CLS), O), X, tI, O)
    val mtch = Tree.Match(scr, List(
      Tree.CaseDef(List(Tree.Literal(Constant.IntC(1), tI, O)), guard,
        Tree.Literal(Constant.UnitC, NoType, O), isDefault = false),
      Tree.CaseDef(Nil, None, Tree.Literal(Constant.UnitC, NoType, O), isDefault = true),
    ), NoType, O)
    val d  = Tree.DefDef(M, List(Nil), TypeTree(NoType, O), rhs = Some(mtch), origin = O)
    val cd = Tree.ClassDef(CLS, parents = Nil, selfType = None, body = List(d), origin = O)
    val syms = SymbolTable(List(
      Symbol(CLS, "S", "demo.S", Flags(), SymId.None, TypeRef(NoPrefix, CLS)),
      Symbol(M, "run", "demo.S#run", Flags(), CLS, MethodType(Nil, NoType)),
      Symbol(X, "x", "demo.S#x", Flags(), CLS, tI),
      Symbol(I, "Int", "scala.Int", Flags(), SymId.None, NoType),
      Symbol(BL, "Boolean", "scala.Boolean", Flags(), SymId.None, NoType),
    ))
    new TirEmitter(new Program(List(cd), syms, Xref.build(List(cd)), MemberIndex.empty)).emit

  test("a guarded case renders its guard — a dropped one WIDENS the arm to every matching scrutinee") {
    val text = guardedSwitch(Some(Tree.Literal(Constant.BoolC(true), TypeRef(NoPrefix, SymId(84)), O)))
    assert(clue(text).contains("case 1 if true =>"), clue(text))
  }

  test("an UNGUARDED case is byte-identical to what it always was — the fix adds nothing where there is no guard") {
    val text = guardedSwitch(None)
    assert(clue(text).contains("case 1 =>"), clue(text))
    assert(!text.contains(" if "), clue(text))
  }
