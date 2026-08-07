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

  /** every term this program holds, `StandardTraversal` doing the walking (`CLAUDE.md` §3: never a
    * private recursion — two of the four silent defects were hand-rolled walks that stopped one
    * node short). */
  private def scan[A](p: Program)(f: PartialFunction[Term, A]): List[A] =
    given Program = p
    p.units.flatMap { cd =>
      def terms(c: Tree.ClassDef): List[Term] = c.body.flatMap {
        case d: Tree.DefDef   => d.rhs.toList
        case v: Tree.ValDef   => v.rhs.toList
        case n: Tree.ClassDef => terms(n)
        case t: Term          => List(t)
        case _                => Nil
      }
      terms(cd).flatMap(t => StandardTraversal.scanTerm(t, List.empty[A]) {
        case (acc, x) if f.isDefinedAt(x) => f(x) :: acc
        case (acc, _)                     => acc
      })
    }

  private def markers(p: Program): List[Tree.Unportable] = scan(p) { case m: Tree.Unportable => m }

  test("a PATTERN CASE LABEL mints a marker — and the rest of the class still translates") {
    // `CtCasePattern` is a `CtExpression` and NOT a `CtPattern`, so the switch's case-expression
    // map hands it to `expr`, which has no arm for it and lands on `exprNoCast`'s default. Before
    // the marker this threw, and the whole file was lost.
    //
    // The construct this spec was written against MOVED when `JS-S09` landed: a switch EXPRESSION
    // is lowered now, so it mints nothing. That is the mechanism working — a marker inventory is a
    // work list and a work list shrinks — and it is why the spec names its construct rather than
    // "whichever one is unlowered".
    val p = SpoonTir.fromSource(
      """package p;
        |public class Sw {
        |  public record Pt(int x, int y) {}
        |  public int untouched(int a) { return a + 1; }
        |  public int pick(Object o) { return switch (o) { case Pt(int x, int y) -> x; default -> 7; }; }
        |}
        |""".stripMargin)

    val ms = markers(p)
    assertEquals(ms.size, 1, s"expected exactly one marker, got ${ms.map(_.what)}")
    assertEquals(ms.head.kind, UnportableKind.UnmodelledNodeKind("CtRecordPattern"))
    assertEquals(ms.head.state, MarkerState.Open)
    // the CATALOG id comes from the kind registry, so the two artifacts join and a report can say
    // WHICH known difference this is rather than only that something was refused.
    assertEquals(ms.head.diff.map(_.toString), Some("JS-S10"))
    // the marker points at real Java — §6.2's rule and `markerKey`'s precondition.
    assert(ms.head.origin.line > 0, ms.head.origin.toString)

    // …and the point of the whole conversion: the sibling method is still here.
    val names = p.symbols.all.map(_.name).toSet
    assert(names.contains("untouched"), s"the unit lost declarations it should have kept: $names")
  }

  test("the marker names the kind the REGISTRY knows, not the parser's implementation class") {
    // …and it names the PATTERN, not the `CtCasePattern` wrapper the marker is minted at: the
    // wrapper has an arm (it is `Lowered`), and pointing the marker at it would make the registry
    // describe a kind the frontend handles.
    val p  = SpoonTir.fromSource(
      """package p;
        |public class S2 {
        |  public record Pt(int x, int y) {}
        |  public int f(Object o) { return switch (o) { case Pt(int x, int y) -> x; default -> 0; }; }
        |}
        |""".stripMargin)
    val ms = markers(p)
    assertEquals(ms.map(_.kind.detail), List(Some("CtRecordPattern")))
    // the join is what the name is FOR: a kind outside the registry would report a name nothing can
    // be looked up by, and `NodeKindTotalitySpec` is what fails on that.
    assert(SpoonKinds.byName.contains("CtRecordPattern"))
  }

  test("a TYPE PATTERN case label mints NOTHING — `JS-S10`'s lowered half, and the negative is the evidence") {
    val p = SpoonTir.fromSource(
      "package p; public class S3 { public int f(Object o) { return switch (o) { case String s -> s.length(); default -> 0; }; } }")
    assertEquals(markers(p), Nil)
    assertEquals(scan(p) { case tp: Tree.TypePattern => tp }.size, 1)
  }

  test("an UNNAMED pattern is a TYPE PATTERN whose variable is `_` — and lowers, which is why nothing refuses it") {
    // `SpoonKinds` used to file `CtUnnamedPattern` as a refusal on both pattern paths. It is not
    // reachable at all: `case Object _ ->` is built as a `CtTypePattern` named `_`, which is exactly
    // scala's own `case _: T`. The kind is `NeverVisited` now, and this is the fixture that says so.
    val p = SpoonTir.fromSource(
      "package p; public class S4 { public int f(Object o) { return switch (o) { case Object _ -> 1; default -> 0; }; } }")
    assertEquals(markers(p), Nil)
    assertEquals(scan(p) { case tp: Tree.TypePattern => tp }.size, 1)
  }

  test("an `instanceof` PATTERN marks the whole EXPRESSION — the unit survives, and JS-G21 says why") {
    // The last kind to leave `RefusedLoudly`. `SpoonKinds` used to name the type operand of an
    // `instanceof` as a shape a term-level marker cannot take, which is true of the OPERAND and
    // false of the construct: the whole `instanceof` is a boolean expression. The refusal itself
    // is unchanged (`ENGINE-LIMITS.md` T18 — java's binding is flow-scoped and scala has no
    // expression that binds outside itself); what changed is that it costs one expression.
    val p = SpoonTir.fromSource(
      """package p;
        |public class Iof {
        |  public int untouched(int a) { return a + 1; }
        |  public boolean f(Object o) { return o instanceof String s && s.length() > 2; }
        |}
        |""".stripMargin)

    val ms = markers(p)
    assertEquals(ms.size, 1, s"expected exactly one marker, got ${ms.map(_.what)}")
    assertEquals(ms.head.kind, UnportableKind.UnmodelledNodeKind("CtTypePattern"))
    assertEquals(ms.head.diff.map(_.toString), Some("JS-G21"))
    assert(ms.head.origin.line > 0, ms.head.origin.toString)
    // the whole point: the unit TRANSLATED. Before this it threw and took the sibling with it.
    assert(p.symbols.all.map(_.name).toSet.contains("untouched"))
    // …and the marker says WHY, in the emitted report — a refusal a reader cannot act on is the
    // failure `CLAUDE.md` §4.45 is about.
    assert(ms.head.what.contains("FLOW-SCOPED"), ms.head.what)
  }

  test("an ORDINARY `instanceof` is untouched — the marker arm is a narrowing, not a refusal") {
    val p = SpoonTir.fromSource(
      "package p; public class I2 { public boolean f(Object o) { return o instanceof String; } }")
    assertEquals(markers(p), Nil)
    assertEquals(scan(p) { case i: Tree.InstanceOf => i }.size, 1)
  }

  test("a SWITCH EXPRESSION mints NOTHING — `JS-S09` is lowered, and the negative is the evidence") {
    // The construct the two tests above used to be written against. Asserted rather than deleted,
    // because a marker inventory that shrinks silently is one nobody can tell from a mint site that
    // stopped being reached.
    val p = SpoonTir.fromSource(
      """package p;
        |public class Sw2 {
        |  public int pick(int a) { return switch (a) { case 1 -> 2; default -> 7; }; }
        |}
        |""".stripMargin)
    assertEquals(markers(p), Nil)
    assertEquals(SpoonKinds.byName("CtSwitchExpression").claim,
      SpoonKinds.Claim.Lowered("SpoonTir.switchExpr"))
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

  // -------------------------------------------------------------------------------------------
  // THE OPERATOR ARMS — a blind spot INSIDE a kind the frontend dispatches on.
  //
  // `BinaryOperatorKind` and `UnaryOperatorKind` are java enums from a DEPENDENCY, not sealed Scala
  // ones, so scalac cannot check either match and a Spoon upgrade that adds a kind falls straight
  // through to the default arm. The binary default used to be `"?" + other` — which is not a
  // diagnostic, it is a METHOD NAME: `binApply` builds `l.?NEWKIND(r)` and the emitter renders it,
  // so the port carries a call to a member nobody declares. Best case that is a compile error naming
  // a symbol which appears nowhere in the java; worst case it is nothing at all.
  //
  // The fallback itself cannot be PROBED — a java enum cannot be extended, so no fixture can make
  // the parser hand over a kind that does not exist yet, which is the same reason the unary twin has
  // no direct probe. What IS checkable is the pair of facts it sits between: the jar's enum, and
  // what the emitted text may contain.
  // -------------------------------------------------------------------------------------------

  /** every constant of a Spoon operator enum, READ FROM THE JAR — never a hand-written list, for
    * `NodeKindTotalitySpec`'s reason: a set written down here is one that stops being a measurement
    * the first time the dependency moves. */
  private def constants(fqn: String): Set[String] =
    Class.forName(fqn).getEnumConstants.map(_.toString).toSet

  test("the operator enums are the ones the arms enumerate — a Spoon upgrade fails HERE") {
    // `SpoonTir.opText` answers nineteen and `INSTANCEOF` never reaches it (the arm above branches
    // first); the unary arm answers four increments plus four operators. A twentieth binary kind or
    // a ninth unary one now mints a marker instead of a method name — the right outcome, and one
    // nobody would go looking for, so this is where a dependency bump is meant to stop.
    assertEquals(
      constants("spoon.reflect.code.BinaryOperatorKind"),
      Set("OR", "AND", "BITOR", "BITXOR", "BITAND", "EQ", "NE", "LT", "GT", "LE", "GE",
          "SL", "SR", "USR", "PLUS", "MINUS", "MUL", "DIV", "MOD", "INSTANCEOF"),
      "spoon's binary operator kinds moved — `SpoonTir.opText` enumerates them, and the new one " +
        "mints a FrontendBlindSpot marker until an arm is written for it")
    assertEquals(
      constants("spoon.reflect.code.UnaryOperatorKind"),
      Set("POS", "NEG", "NOT", "COMPL", "PREINC", "PREDEC", "POSTINC", "POSTDEC"),
      "spoon's unary operator kinds moved — `SpoonTir`'s unary arm enumerates them")
  }

  test("no operator java HAS is APPLIED under a `?`-named symbol — the shape the default emitted") {
    // Asserted at the APPLY's own symbol, which is what the emitter renders: `?NEWKIND` is a legal
    // Scala identifier, so nothing downstream can tell it from a real member — the emitted file is
    // the last place this is visible and the first place it is too late. (Read here rather than out
    // of emitted text because `frontend-spoon` does not see the emitter; the name is the same one.)
    // One snippet using every binary operator, both compound-assignment positions included, because
    // all three call sites took their spelling from the same function.
    val p = SpoonTir.fromSource(
      """package p;
        |public class Ops {
        |  public int f(int a, int b, Object o) {
        |    int r = a + b - a * b / (b + 1) % 3;
        |    r = r << 1; r = r >> 1; r = r >>> 1;
        |    r = r & b; r = r | b; r = r ^ b;
        |    r += b; r -= b; r *= b; r /= b; r %= b; r &= b; r |= b; r ^= b;
        |    r <<= 1; r >>= 1; r >>>= 1;
        |    boolean t = (a == b) || (a != b) && (a < b) | (a > b) & (a <= b) ^ (a >= b);
        |    boolean u = o instanceof String;
        |    return t || u ? r : 0;
        |  }
        |}
        |""".stripMargin)
    assertEquals(markers(p), Nil)
    val applied = scan(p) { case a: Tree.Apply => a }
      .flatMap(a => p.symbolOf(a.method)).map(_.name).distinct
    assertEquals(applied.filter(_.startsWith("?")), Nil,
      s"an operator was applied under a `?`-named symbol: $applied")
    // …and the positive, so the assertion above is not passing on an empty walk.
    assert(applied.contains("+") && applied.contains(">>>") && applied.contains("^"),
      s"the walk did not reach the operators at all: $applied")
  }
