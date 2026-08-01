package balticporter.transform

import balticporter.tir.*
import balticporter.transform.CallSiteSubstitutionTransform.{Bound, Hole, Template, receiverOf, siteFault}

/** The two halves of the call-site seam that need NO program: the TEMPLATE GRAMMAR, and the
  * per-SITE refusals.
  *
  * Both are pure functions on purpose, and this suite is why. A template fault has to be reportable
  * before the pipeline runs (DESIGN.md §8.1's rule for keys, applied to their values), and a
  * refusal has to be decidable from the call node alone so that the pass recording decisions and
  * the traversal performing the rewrite cannot disagree about which sites were rewritten. Testing
  * them through a frontend would prove neither property and would hide the shapes a frontend does
  * not produce — the vararg SPREAD below is exactly one of those.
  */
class CallSiteTemplateSpec extends munit.FunSuite:

  private val o = Origin("Demo.java", 7, 3)
  private def lit(n: Int)  = Tree.Literal(Constant.IntC(n), TypeRepr.NoType, o)
  private def id(n: Int)   = Tree.Ident(SymId(n), TypeRepr.NoType, o)

  /** a call with `args`, through a receiver when `recv` is given. */
  private def call(recv: Option[Term], args: List[Term]): Tree.Apply =
    val fun = recv match
      case Some(r) => Tree.Select(r, SymId(99), TypeRepr.NoType, o)
      case None    => Tree.Ident(SymId(99), TypeRepr.NoType, o)
    Tree.Apply(fun, args, SymId(99), TypeRepr.NoType, o)

  private def parsed(t: String): Template =
    Template.parse(t).fold(w => fail(s"template did not parse: $w"), identity)

  // -------------------------------------------------------------------------
  // the grammar
  // -------------------------------------------------------------------------

  test("a template splits into literal parts and numbered holes, parts always one more than holes") {
    val t = parsed("a.B.c({recv}, {arg0}, {arg1})")
    assertEquals(t.holes, List(Hole.Recv, Hole.Arg(0), Hole.Arg(1)))
    assertEquals(t.parts, List("a.B.c(", ", ", ", ", ")"))
    assertEquals(t.parts.size, t.holes.size + 1)
    assertEquals(t.maxArg, Some(1))
    assert(t.usesRecv)
  }

  test("a template with NO holes is legal — a call replaced by a constant is still a substitution") {
    val t = parsed("scala.Predef.???")
    assertEquals(t.holes, Nil)
    assertEquals(t.parts, List("scala.Predef.???"))
    assertEquals(t.maxArg, None)
    assert(!t.usesRecv)
  }

  test("`{{` and `}}` are literal braces — a block body is writable without escaping every char") {
    val t = parsed("{{ val x = {arg0}; x }}")
    assertEquals(t.holes, List(Hole.Arg(0)))
    assertEquals(t.parts, List("{ val x = ", "; x }"))
  }

  test("a hole name the grammar does not have is MALFORMED, and the message says what to write") {
    val Left(why) = Template.parse("f({argument0})"): @unchecked
    assert(clue(why).contains("{argument0}"))
    assert(clue(why).contains("{arg0}"))
  }

  test("an unclosed `{` and an unmatched `}` are each malformed, not carried through as text") {
    // Carried through, either one emits a brace into generated Scala where it is a compile error
    // naming a file nobody wrote — the failure mode `MemberKey.parse` refuses a lenient parse for.
    assert(Template.parse("f({arg0").isLeft)
    assert(Template.parse("f(x})").isLeft)
  }

  test("a NEGATIVE hole index is not an argument position") {
    assert(Template.parse("f({arg-1})").isLeft)
  }

  // -------------------------------------------------------------------------
  // splicing — the holes are TREES
  // -------------------------------------------------------------------------

  test("splice builds ONE Opaque carrying the terms; nothing is rendered at the phase") {
    val t = parsed("a.B.c({recv}, {arg0})")
    val out = t.splice(Some(id(1)), List(lit(4)), TypeRepr.NoType, o)
    val op  = out.asInstanceOf[Tree.Opaque]
    assertEquals(op.holes, List(id(1), lit(4)))
    // the marker is not text a template author can write, so a hole can never be forged by one
    assertEquals(op.spliced { case Tree.Ident(s, _, _) => s"#${s.raw}"; case _ => "4" },
      "a.B.c(#1, 4)")
  }

  test("Opaque with NO holes is returned verbatim — a marker-shaped byte in it cannot be misread") {
    val raw = "already " + Tree.Opaque.hole(0) + " written"
    assertEquals(Tree.Opaque(raw, TypeRepr.NoType, o).spliced(_ => "X"), raw)
  }

  // -------------------------------------------------------------------------
  // the per-site refusals
  // -------------------------------------------------------------------------

  private def bound(tmpl: String, arity: Int) =
    Bound("demo.C#m", parsed(tmpl), arity, dropped = false)

  test("an ordinary site with the declared arity is accepted") {
    assertEquals(siteFault(call(Some(id(1)), List(lit(2), lit(3))), bound("f({recv},{arg1})", 2)), None)
  }

  test("a VARARG SPREAD is refused: a positional hole names a term, not a group") {
    val spread = Tree.Repeated(List(lit(1), lit(2)), TypeRepr.NoType, o)
    val why = siteFault(call(Some(id(1)), List(lit(0), spread)), bound("f({arg1})", 2))
    assert(clue(why).exists(_.contains("VARARG SPREAD")))
  }

  test("…and it is refused even when the template names no argument — the CALL is still variadic") {
    // The template could be spliced safely here, and it is still refused: what the port asked for
    // is "replace this call", and a call whose shape the engine cannot describe is one it must not
    // claim to have replaced. Refusing narrowly would make `refusals` a function of the template
    // rather than of the call.
    val spread = Tree.Repeated(List(lit(1)), TypeRepr.NoType, o)
    assert(siteFault(call(Some(id(1)), List(spread)), bound("scala.Predef.???", 1)).isDefined)
  }

  test("an argument count this site does not have is refused — the positions moved") {
    val why = siteFault(call(Some(id(1)), List(lit(2))), bound("f({arg1})", 2))
    assert(clue(why).exists(_.contains("1 argument term")))
  }

  test("`{recv}` on a call with no receiver TERM is refused, and that is not the same as no call") {
    val why = siteFault(call(None, List(lit(2))), bound("f({recv})", 1))
    assert(clue(why).exists(_.contains("{recv}")))
    // …and without `{recv}` the very same site is fine
    assertEquals(siteFault(call(None, List(lit(2))), bound("f({arg0})", 1)), None)
  }

  test("the receiver is seen THROUGH an explicit type application") {
    // Matching only `Select` skips every explicitly-instantiated call, which is the shape
    // `CollectionsTransform` records having been bitten by — silently, since the site then simply
    // does not rewrite.
    val sel   = Tree.Select(id(1), SymId(99), TypeRepr.NoType, o)
    val tapp  = Tree.TypeApply(sel, Nil, TypeRepr.NoType, o)
    val apply = Tree.Apply(tapp, List(lit(2)), SymId(99), TypeRepr.NoType, o)
    assertEquals(receiverOf(apply), Some(id(1)))
    assertEquals(siteFault(apply, bound("f({recv})", 1)), None)
  }
