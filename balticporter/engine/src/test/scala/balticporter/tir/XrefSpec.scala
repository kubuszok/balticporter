package balticporter.tir

import TypeRepr.*

/** Proves the goal: the TIR traces a type's usages in EVERY position (external type,
  * type argument, member type, mixin, bound) and the xref RESPONDS after a phase
  * rewrites the tree — the old symbol drops to zero usages, the new one inherits them
  * all, and symbol signatures move too. */
class XrefSpec extends munit.FunSuite:

  // ---- ids ----
  private val FOO     = SymId(1)  // the one class we define
  private val BASE    = SymId(2)  // external — primary supertype
  private val WIDGET  = SymId(3)  // external — the type we trace, then rewrite
  private val GADGET  = SymId(4)  // external — rewrite target
  private val LIST    = SymId(5)  // external generic constructor
  private val UNIT    = SymId(6)  // external
  private val PRINTLN = SymId(7)  // external method
  private val W       = SymId(10) // val w: WIDGET
  private val WS      = SymId(11) // val ws: LIST[WIDGET]
  private val BND     = SymId(12) // val bounded: LIST[? <: WIDGET]
  private val RENDER  = SymId(13) // def render(): UNIT

  private def named(id: SymId) = TypeRef(NoPrefix, id)
  private val tWidget   = named(WIDGET)
  private val tBase     = named(BASE)
  private val tUnit     = named(UNIT)
  private def tList(a: TypeRepr) = AppliedType(named(LIST), List(a))
  private val tListWidget    = tList(tWidget)
  private val tBoundedWidget = tList(TypeBounds(NoType, tWidget)) // LIST[? <: WIDGET]

  private val O  = Origin.synthetic
  private def tt(t: TypeRepr) = TypeTree(t, O)
  /** an EXTERNAL symbol — `owner = SymId.None`, which is what makes it external (§4.56: ownership
    * is decided by climbing the owner chain to a unit, never from the name). */
  private def sym(id: SymId, name: String, info: TypeRepr) =
    Symbol(id, name, name, Flags(), SymId.None, info)

  /** …and a MEMBER of `Foo`, which must carry `Foo` as its owner or the whole engine reads it as an
    * external: `StandardTraversal.mapSymbols` does not retype what the program does not own,
    * because an external signature is a fact about a class file that no phase can move. A fixture
    * that leaves the owner off is not a smaller program, it is a different one. */
  private def member(id: SymId, name: String, info: TypeRepr) =
    Symbol(id, name, s"Foo#$name", Flags(), FOO, info)

  // ---- the program: class Foo extends Base with Widget { fields...; def render }
  private val wDef   = Tree.ValDef(W, tt(tWidget), scala.None, O)
  private val wsDef  = Tree.ValDef(WS, tt(tListWidget), scala.None, O)
  private val bndDef = Tree.ValDef(BND, tt(tBoundedWidget), scala.None, O)
  private val renderDef = Tree.DefDef(
    RENDER,
    paramss = List(Nil),
    returnTpt = tt(tUnit),
    rhs = Some(Tree.Apply(Tree.Ident(PRINTLN, tUnit, O), Nil, PRINTLN, tUnit, O)),
    origin = O,
  )
  private val foo = Tree.ClassDef(
    symbol = FOO,
    parents = List(tt(tBase), tt(tWidget)), // Base = Extends, Widget = Mixin
    selfType = scala.None,
    body = List(wDef, wsDef, bndDef, renderDef),
    origin = O,
  )

  private val symbols = SymbolTable(
    List(
      sym(FOO, "Foo", named(FOO)),
      sym(BASE, "Base", NoType),
      sym(WIDGET, "Widget", NoType),
      sym(GADGET, "Gadget", NoType),
      sym(LIST, "List", NoType),
      sym(UNIT, "Unit", NoType),
      sym(PRINTLN, "println", MethodType(Nil, tUnit)),
      member(W, "w", tWidget),
      member(WS, "ws", tListWidget),
      member(BND, "bounded", tBoundedWidget),
      member(RENDER, "render", MethodType(Nil, tUnit)),
    )
  )

  private def program(): Program =
    val units = List(foo)
    new Program(units, symbols, Xref.build(units), MemberIndex.empty)

  private def kinds(p: Program, s: SymId): Set[UsageKind] = p.usages(s).map(_.kind).toSet

  // -------------------------------------------------------------------------
  test("traces one external type across every position it occurs in") {
    val p = program()
    // WIDGET is used as a mixin parent, a member type, a type argument, and a bound.
    assertEquals(kinds(p, WIDGET), Set(UsageKind.Mixin, UsageKind.MemberType, UsageKind.TypeArg, UsageKind.Bound))
    // it is external: usages exist, but there is no local definition.
    assert(p.usagesOf(WIDGET).nonEmpty)
    assertEquals(p.definitionOf(WIDGET), scala.None)
    // BASE is the primary supertype; LIST is the applied constructor.
    assertEquals(kinds(p, BASE), Set(UsageKind.Extends))
    assertEquals(kinds(p, LIST), Set(UsageKind.Tycon))
    // the call site is traced too, and Foo itself is a definition.
    assert(kinds(p, PRINTLN).contains(UsageKind.Call))
    assert(p.definitionOf(FOO).isDefined)
  }

  test("kinded query narrows to a single position") {
    val p = program()
    assertEquals(p.usagesOf(WIDGET, UsageKind.Mixin).size, 1)
    assertEquals(p.usagesOf(WIDGET, UsageKind.MemberType).size, 1)
    assertEquals(p.usagesOf(WIDGET, UsageKind.TypeArg).size, 1)
    assertEquals(p.usagesOf(WIDGET, UsageKind.Bound).size, 1)
    assertEquals(p.usagesOf(WIDGET, UsageKind.Call), Nil)
  }

  // -------------------------------------------------------------------------
  test("xref responds after a phase rewrites the type everywhere") {
    val swap = new Phase:
      def name = "widget->gadget"
      override def transformType(t: TypeRepr)(using Program): TypeRepr = t match
        case TypeRef(p, s) if s == WIDGET => TypeRef(p, GADGET)
        case other                        => other

    val before = program()
    assert(kinds(before, GADGET).isEmpty)

    val after = Pipeline.run(before, List(swap))

    // WIDGET is gone; GADGET inherits EXACTLY the positions WIDGET held.
    assertEquals(after.usagesOf(WIDGET), Nil)
    assertEquals(kinds(after, GADGET), Set(UsageKind.Mixin, UsageKind.MemberType, UsageKind.TypeArg, UsageKind.Bound))
    // signatures moved too: the field symbol's info no longer mentions WIDGET.
    assertEquals(after.symbolOf(W).map(_.info), Some(TypeRef(NoPrefix, GADGET)))
    // an untouched position (the call) is unaffected.
    assert(kinds(after, PRINTLN).contains(UsageKind.Call))
  }

  test("…and an EXTERNAL symbol's signature does NOT move — a class file is not the phase's to edit") {
    // The other half of the line above, and it is not symmetry for its own sake. `println`'s
    // signature is a fact about a compiled class file: whatever the port renames inside itself,
    // that method still takes and returns what it was compiled to take and return.
    val widening = new Phase:
      def name = "unit->widget"
      override def transformType(t: TypeRepr)(using Program): TypeRepr = t match
        case TypeRef(p, s) if s == UNIT => TypeRef(p, WIDGET)
        case other                      => other

    val after = Pipeline.run(program(), List(widening))
    // `render` is ours, so its signature moved…
    assertEquals(after.symbolOf(RENDER).map(_.info), Some(MethodType(Nil, TypeRef(NoPrefix, WIDGET))))
    // …and `println` is a class file's, so it did not.
    assertEquals(after.symbolOf(PRINTLN).map(_.info), Some(MethodType(Nil, tUnit)))
  }
