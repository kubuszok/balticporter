package balticporter.tir

/** SENTINEL SYMBOLS — the marker's shape from before there was a marker, COUNTED rather than
  * converted. */
class SentinelSymbolSpec extends munit.FunSuite:

  private def programWith(syms: List[Symbol], units: List[Tree.ClassDef]): Program =
    new Program(units, SymbolTable(syms), Xref.build(units), MemberIndex.empty)

  test("a `?T` sentinel referenced from an owned declaration is a `sentinel` finding") {
    val clsId  = SymId(0)
    val defId  = SymId(1)
    val stubId = SymId(2)
    val o      = Origin("p/Holder.java", 3, 7)
    val stub   = Symbol(stubId, "T", Symbol.UnresolvedTypeVarPrefix + "T", Flags(), SymId.None, TypeRepr.NoType)
    val cls    = Symbol(clsId, "Holder", "p.Holder", Flags(), SymId.None, TypeRepr.NoType)
    val mem    = Symbol(defId, "get", "p.Holder#get", Flags(), clsId, TypeRepr.NoType)
    val body   = Tree.Ident(stubId, TypeRepr.TypeRef(TypeRepr.NoPrefix, stubId), o)
    val dd     = Tree.DefDef(defId, Nil, TypeTree(TypeRepr.NoType, o), Some(body), o)
    val unit   = Tree.ClassDef(clsId, Nil, scala.None, List(dd), o)
    val p      = programWith(List(cls, mem, stub), List(unit))

    val fs = MarkerCheck.sentinels(p, p.units)
    assertEquals(fs.map(_.kind), List("sentinel"))
    assertEquals(fs.head.owner, "p.Holder#get")
    assert(fs.head.detail.contains("?T"), fs.head.detail)
    assert(fs.head.detail.contains("type variable"), fs.head.detail)
  }

  test("…and it does NOT feed the emission gate — a sentinel is counted, never a refusal") {
    // The whole reason the two mint sites were not simply converted to markers: an OPEN marker
    // refuses the emission, and a sentinel must not, because nobody had measured how many there
    // are. Same fixture as above, read through the gate's own entry point.
    val clsId  = SymId(0)
    val defId  = SymId(1)
    val stubId = SymId(2)
    val o      = Origin("p/Holder.java", 3, 7)
    val stub   = Symbol(stubId, "T", Symbol.UnresolvedTypeVarPrefix + "T", Flags(), SymId.None, TypeRepr.NoType)
    val cls    = Symbol(clsId, "Holder", "p.Holder", Flags(), SymId.None, TypeRepr.NoType)
    val mem    = Symbol(defId, "get", "p.Holder#get", Flags(), clsId, TypeRepr.NoType)
    val body   = Tree.Ident(stubId, TypeRepr.TypeRef(TypeRepr.NoPrefix, stubId), o)
    val dd     = Tree.DefDef(defId, Nil, TypeTree(TypeRepr.NoType, o), Some(body), o)
    val unit   = Tree.ClassDef(clsId, Nil, scala.None, List(dd), o)
    val p      = programWith(List(cls, mem, stub), List(unit))
    assertEquals(MarkerCheck.openMarkers(p, p.units), Nil, "a sentinel must not reach the gate")
    assert(MarkerCheck.sentinels(p, p.units).nonEmpty, "…and it must still be counted")
  }

  test("a program with no sentinels reports none — the negative the lane needs to mean anything") {
    val clsId = SymId(0)
    val o     = Origin("p/Clean.java", 1, 1)
    val cls   = Symbol(clsId, "Clean", "p.Clean", Flags(), SymId.None, TypeRepr.NoType)
    val unit  = Tree.ClassDef(clsId, Nil, scala.None, Nil, o)
    val p     = programWith(List(cls), List(unit))
    assertEquals(MarkerCheck.sentinels(p, p.units), Nil)
  }

  test("a `?`-PREFIXED name that is not a sentinel is NOT one — the filter is equality, not a prefix") {
    // The defect this test pins, and it was found by RUNNING the lane rather than by reading it.
    // `Symbol.isUnresolvedTypeVar` is `startsWith("?")`, and the frontend also mints a `?` prefix
    // for a member whose OWNER it could not name (`Minter.fullNameOf`'s fallback) — `?#actual`,
    // `?#points`, `?#stride`, which are ordinary method PARAMETERS. Asked the prefix question, this
    // lane reported 10,417 of them on libGDX core and 29 on its own test set, not one a sentinel.
    val clsId = SymId(0)
    val defId = SymId(1)
    val parId = SymId(2)
    val o     = Origin("p/Holder.java", 3, 7)
    val param = Symbol(parId, "actual", "?#actual", Flags(), SymId.None, TypeRepr.NoType)
    val cls   = Symbol(clsId, "Holder", "p.Holder", Flags(), SymId.None, TypeRepr.NoType)
    val mem   = Symbol(defId, "get", "p.Holder#get", Flags(), clsId, TypeRepr.NoType)
    val dd    = Tree.DefDef(defId, Nil, TypeTree(TypeRepr.NoType, o),
      Some(Tree.Ident(parId, TypeRepr.NoType, o)), o)
    val unit  = Tree.ClassDef(clsId, Nil, scala.None, List(dd), o)
    val p     = programWith(List(cls, mem, param), List(unit))
    assertEquals(MarkerCheck.sentinels(p, p.units), Nil,
      "a member whose OWNER could not be named is not an unresolvable name")
  }

  test("the `?var$` sentinel is recognised too, and named as a variable rather than a type") {
    val clsId = SymId(0)
    val defId = SymId(1)
    val varId = SymId(2)
    val o     = Origin("p/V.java", 9, 2)
    val stub  = Symbol(varId, "count", MarkerCheck.VarSentinelPrefix + "count", Flags(), SymId.None, TypeRepr.NoType)
    val cls   = Symbol(clsId, "V", "p.V", Flags(), SymId.None, TypeRepr.NoType)
    val mem   = Symbol(defId, "run", "p.V#run", Flags(), clsId, TypeRepr.NoType)
    val dd    = Tree.DefDef(defId, Nil, TypeTree(TypeRepr.NoType, o),
      Some(Tree.Ident(varId, TypeRepr.NoType, o)), o)
    val unit  = Tree.ClassDef(clsId, Nil, scala.None, List(dd), o)
    val p     = programWith(List(cls, mem, stub), List(unit))
    val fs    = MarkerCheck.sentinels(p, p.units)
    assertEquals(fs.size, 1)
    assert(fs.head.detail.contains("variable reference"), fs.head.detail)
  }
