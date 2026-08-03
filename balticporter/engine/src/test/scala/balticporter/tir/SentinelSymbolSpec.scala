package balticporter.tir

/** SENTINEL SYMBOLS — the marker's shape from before there was a marker, COUNTED rather than
  * converted.
  *
  * `SpoonTir` mints two: `?T` for a type variable it cannot resolve, `?var$name` for a variable
  * reference it cannot resolve. Each is a name that must never be printed, and each one's only
  * defence is an emitter rule that does not print it. Nothing counted them, so a port could lose a
  * type argument or a reference with a green compile, no finding and no moved number.
  *
  * `DESIGN.md` §6.5's mint list has both of them on it, and this is the one entry that is not
  * simply converted. The reason is the emission gate: an OPEN marker refuses the emission, so a
  * site that mints one is a site that stops a port building — and whether any corpus port mints a
  * sentinel was, until this lane existed, a question nothing in the engine could answer. Converting
  * a site whose live frequency nobody has measured is how a mechanism takes a whole corpus down.
  * So the number comes first and the conversion is a decision somebody takes ON it — which is
  * `ENGINE-LIMITS.md` M6's own discipline, turned on the engine's refusals rather than on a
  * library's constructs.
  */
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
