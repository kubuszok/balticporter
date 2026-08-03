package balticporter.frontend.spoon

import balticporter.tir.*

/** THE FIRST MINT SITE (`DESIGN.md` §6.5): `SpoonTir.unsupported`'s two default dispatch arms.
  *
  * §6.5 stages the frontend's refusal points first, and it is the right place to start for a
  * measurable reason: the throw is not per-site. It fails the whole COMPILATION UNIT, so one node
  * the frontend has no arm for costs every other type in that file — which is what makes adopting a
  * new syntax family all-or-nothing rather than an incremental measured step.
  *
  * What must be true after the conversion, and is what this spec asserts:
  *
  *   - the unit TRANSLATES. Every declaration beside the unmodelled one survives;
  *   - the refusal is still there and is now LOCATED, taxonomised, and joined to the kind registry
  *     and to the difference catalog;
  *   - the port still does not ship — the emission gate is what enforces that (§6.4), and it is
  *     tested where it lives.
  */
class UnloweredNodeSpec extends munit.FunSuite:

  private def markers(p: Program): List[Tree.Unportable] =
    given Program = p
    p.units.flatMap { cd =>
      def terms(c: Tree.ClassDef): List[Term] = c.body.flatMap {
        case d: Tree.DefDef   => d.rhs.toList
        case v: Tree.ValDef   => v.rhs.toList
        case n: Tree.ClassDef => terms(n)
        case t: Term          => List(t)
        case _                => Nil
      }
      terms(cd).flatMap(t => StandardTraversal.scanTerm(t, List.empty[Tree.Unportable]) {
        case (acc, m: Tree.Unportable) => m :: acc
        case (acc, _)                  => acc
      })
    }

  test("a SWITCH EXPRESSION mints a marker — and the rest of the class still translates") {
    // `CtSwitchExpression` extends `CtExpression` and `CtAbstractSwitch` and NOT `CtSwitch`, so the
    // switch arm cannot catch it and it lands on `exprNoCast`'s default. Before the marker this
    // threw, and the whole file was lost.
    val p = SpoonTir.fromSource(
      """package p;
        |public class Sw {
        |  public int untouched(int a) { return a + 1; }
        |  public int pick(int a) { return switch (a) { default -> 7; }; }
        |}
        |""".stripMargin)

    val ms = markers(p)
    assertEquals(ms.size, 1, s"expected exactly one marker, got ${ms.map(_.what)}")
    assertEquals(ms.head.kind, UnportableKind.UnmodelledNodeKind("CtSwitchExpression"))
    assertEquals(ms.head.state, MarkerState.Open)
    // the CATALOG id comes from the kind registry, so the two artifacts join and a report can say
    // WHICH known difference this is rather than only that something was refused.
    assertEquals(ms.head.diff.map(_.toString), Some("JS-S09"))
    // the marker points at real Java — §6.2's rule and `markerKey`'s precondition.
    assert(ms.head.origin.line > 0, ms.head.origin.toString)

    // …and the point of the whole conversion: the sibling method is still here.
    val names = p.symbols.all.map(_.name).toSet
    assert(names.contains("untouched"), s"the unit lost declarations it should have kept: $names")
  }

  test("the marker names the kind the REGISTRY knows, not the parser's implementation class") {
    val p  = SpoonTir.fromSource(
      "package p; public class S2 { public int f(int a) { return switch (a) { default -> 1; }; } }")
    val ms = markers(p)
    assertEquals(ms.map(_.kind.detail), List(Some("CtSwitchExpression")))
    // the join is what the name is FOR: a kind outside the registry would report a name nothing can
    // be looked up by, and `NodeKindTotalitySpec` is what fails on that.
    assert(SpoonKinds.byName.contains("CtSwitchExpression"))
  }

  test("SpoonKinds.nameOf resolves an implementation to its MOST SPECIFIC registered interface") {
    // `CtSwitchExpressionImpl` implements `CtSwitchExpression` AND `CtExpression`; answering the
    // supertype would say the node is one the frontend handles.
    val cls = Class.forName("spoon.support.reflect.code.CtSwitchExpressionImpl")
    assertEquals(SpoonKinds.nameOf(cls), "CtSwitchExpression")
    val lit = Class.forName("spoon.support.reflect.code.CtLiteralImpl")
    assertEquals(SpoonKinds.nameOf(lit), "CtLiteral")
  }

  test("a construct the frontend DOES lower mints nothing — the fixture proves the negative") {
    val p = SpoonTir.fromSource(
      "package p; public class Ok { public int f(int a) { switch (a) { case 1: return 2; } return 0; } }")
    assertEquals(markers(p), Nil)
  }
