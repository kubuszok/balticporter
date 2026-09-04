package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.tir.{Program, SymId, TypeRepr}
import balticporter.transform.FlowPropagation

/** [[FlowPropagation]] on its own — the shared second half of every retyping rule. */
class FlowPropagationSpec extends PortSuite:

  private val src =
    """package demo;
      |class Sprite {
      |  private int layer = 0;
      |  private int unrelated = 7;
      |  private int derived = 0;
      |  public int getLayer() { return layer; }
      |  public void setLayer(int l) { this.layer = l; }
      |  public void bump() { derived = layer + 1; }
      |  public int copy() { int c = layer; return c; }
      |}
      |""".stripMargin

  private def program: Program = balticporter.testkit.PortFixture.parse(src)

  private def isInt(p: Program)(id: SymId): Boolean =
    p.symbolOf(id).exists(s =>
      s.info match
        case TypeRepr.TypeRef(_, t)            => p.symbolOf(t).exists(_.fullName == "scala.Int")
        case TypeRepr.MethodType(_, r, _)      => isIntType(p, r)
        case t                                 => isIntType(p, t))

  private def isIntType(p: Program, t: TypeRepr): Boolean = t match
    case TypeRepr.TypeRef(_, s) => p.symbolOf(s).exists(_.fullName == "scala.Int")
    case TypeRepr.AppliedType(tc, List(elem)) =>
      isArrayHead(p, tc) && isIntType(p, elem)
    case _ => false

  private def isArrayHead(p: Program, t: TypeRepr): Boolean = t match
    case TypeRepr.TypeRef(_, s) => p.symbolOf(s).exists(_.fullName == "scala.Array")
    case _ => false

  private def idOf(p: Program, fqn: String): SymId =
    p.symbols.all.find(_.fullName == fqn).map(_.id).getOrElse(SymId.None)

  private def grownFrom(p: Program, seedFqn: String): Set[String] =
    FlowPropagation.grow(p, Set(idOf(p, seedFqn)), isInt(p)).flatMap(p.symbolOf).map(_.fullName)

  test("a field seed reaches its GETTER (return), its SETTER's parameter and a local it initialises") {
    val p = program
    val grown = grownFrom(p, "demo.Sprite#layer")
    assert(clue(grown).contains("demo.Sprite#layer"))    // the seed itself
    assert(grown.contains("demo.Sprite#getLayer"))       // `return layer`
    assert(grown.contains("demo.Sprite#copy"))           // `return c`, the local's own chain
    assert(grown.contains("c"))                          // `int c = layer` — a local, named bare
    // …and the setter's PARAMETER, under its full name `Class#method#param` (until wave 2.8 the
    // frontend qualified a parameter against its method BEFORE the method's own record was set and
    // the name was `?#l`; [[balticporter.tir.RuleScope]] decides scope through the OWNER chain and
    // never from a symbol's own name, which is why that defect was invisible to every scope).
    assert(clue(grown).contains("demo.Sprite#setLayer#l"))
  }

  test("ARITHMETIC is not a pure move — the chain BREAKS, which is the whole point") {
    val p = program
    val grown = grownFrom(p, "demo.Sprite#layer")
    // `derived = layer + 1` yields a plain int; a rule that propagated through it would have no
    // boundary left to coerce at, and an opaque type would be an alias.
    assert(!clue(grown).contains("demo.Sprite#derived"))
  }

  test("an unconnected declaration of the SAME type is not reached") {
    val p = program
    assert(!clue(grownFrom(p, "demo.Sprite#layer")).contains("demo.Sprite#unrelated"))
  }

  test("an INELIGIBLE seed contributes nothing and is not returned — an inert hint is silent") {
    val p = program
    // `getLayer` is eligible (its result is an int); a type symbol is not.
    assertEquals(FlowPropagation.grow(p, Set(idOf(p, "demo.Sprite")), isInt(p)), Set.empty[SymId])
  }

  test("an empty seed set grows to nothing — a scope that names nothing rewrites nothing") {
    val p = program
    assertEquals(FlowPropagation.grow(p, Set.empty, isInt(p)), Set.empty[SymId])
  }

  test("eligibility is applied BEFORE the union, so a chain cannot leak through a non-candidate") {
    val p = program
    // with NOTHING eligible, every edge is dropped and no seed survives — the arithmetic guarantee
    // stated positively: this function's output is a function of `eligible`, not only of the edges.
    assertEquals(FlowPropagation.grow(p, Set(idOf(p, "demo.Sprite#layer")), _ => false), Set.empty[SymId])
  }

  test("the flow edges are SYMMETRIC in effect — growing from the getter reaches the field") {
    val p = program
    assert(clue(grownFrom(p, "demo.Sprite#getLayer")).contains("demo.Sprite#layer"))
  }

  test("edges are read off RESOLVED symbols, not from names — the assignment edge is exact") {
    val p     = program
    val es    = FlowPropagation.edges(p)
    val field = idOf(p, "demo.Sprite#layer")
    // the setter's parameter, found STRUCTURALLY (by owner), because its own `fullName` is `?#l`.
    val setter = idOf(p, "demo.Sprite#setLayer")
    val param  = p.symbols.all.find(s => s.owner == setter && s.name == "l").map(_.id).get
    assert(clue(es).contains((field, param)), "`this.layer = l` is an assignment edge")
  }

  // ---- O8: array element read/write is a pure move of the ELEMENT ----

  private val arraySrc =
    """package demo;
      |class Shader {
      |  private int[] locations = new int[16];
      |  private int unrelated = 0;
      |  public int loc(int i) { return (i >= 0 && i < locations.length) ? locations[i] : -1; }
      |  public void store(int i, int v) { locations[i] = v; }
      |}
      |""".stripMargin

  private def arrayProgram: Program = balticporter.testkit.PortFixture.parse(arraySrc)

  test("an ELEMENT READ from a seeded array reaches the method — O8's return direction") {
    val p = arrayProgram
    // `return locations[i]` is a pure move of the element: refSym sees through ArrayAccess to
    // the array's symbol, and the tail-ref edge connects `loc` to `locations`.
    val grown = grownFrom(p, "demo.Shader#locations")
    assert(clue(grown).contains("demo.Shader#loc"), "loc must be reached from the seeded array")
  }

  test("an ELEMENT WRITE to a seeded array reaches the value's source — O8's assignment direction") {
    val p = arrayProgram
    // `locations[i] = v` creates an edge between `locations` and `v`.  Growing from `locations`
    // reaches the setter's parameter.
    val grown = grownFrom(p, "demo.Shader#locations")
    assert(clue(grown).contains("demo.Shader#store#v"), "the stored value's parameter must be reached")
  }

  test("array element flow does NOT leak to unrelated declarations of the same type") {
    val p = arrayProgram
    assert(!clue(grownFrom(p, "demo.Shader#locations")).contains("demo.Shader#unrelated"))
  }
