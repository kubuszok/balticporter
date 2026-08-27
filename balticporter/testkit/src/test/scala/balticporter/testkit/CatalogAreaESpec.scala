package balticporter.testkit

import balticporter.catalog.{Attaches, Differences, JS, Status}
import balticporter.tir.{CastConversionCheck, Phase, Program, Term, Tree, TypeRepr}

/** THE `JS-E` EDGE-CASE SUITE — one test per expression row the engine wires, at the shape the row
  * is about.
  *
  * This suite is the half of the guarantee the obligation wrapper does NOT provide, and it is why
  * `DESIGN.md` §2.8 states the wrapper's claim at the strength it actually holds. The wrapper
  * detects an ABSENT consult; it cannot detect a WRONG one, because an arm that consults a row and
  * hands it a predicate which never returns `Some` discharges the obligation and emits the same
  * wrong code. So each test below asserts BOTH — that the branch was live (`assertConsults`) and
  * that the emitted Scala means what java meant. Neither on its own is worth much: the log without
  * the text says the question was asked and not that the answer was right; the text without the log
  * passes for a lowering that never asked.
  *
  * A row that is `NoObligation` gets no test here and owes none — there is nothing to discharge at
  * a site, and the last test asserts exactly that partition rather than leaving it to a reader.
  */
class CatalogAreaESpec extends PortSuite:

  // -- JS-E01: `==` on references is IDENTITY in java; scala's `==` calls `equals` ----------------

  test("JS-E01 — a reference `==` becomes `eq`, and the row is consulted where it does") {
    val p = port("public class A { boolean f(Object a, Object b) { return a == b; } }")
    assertConsults(p, JS.E(1), fired = true)
    assertEmits(p, " eq ")
    assertNotEmits(p, "a == b")
  }

  test("JS-E01 — a PRIMITIVE `==` is consulted and does NOT fire; `==` is already java's meaning") {
    // The edge the wrapper alone cannot see: the branch is live at every binary operator, and
    // firing it here would turn a value comparison into an identity one.
    val p = port("public class A { boolean f(int a, int b) { return a == b; } }")
    assertConsults(p, JS.E(1))
    assertEmits(p, "==")
    assertNotEmits(p, " eq ")
  }

  test("JS-E01 — `== null` stays `== null`: java's null test needs no identity operator") {
    val p = port("public class A { boolean f(Object a) { return a == null; } }")
    assertConsults(p, JS.E(1))
    assertNotEmits(p, " eq ")
  }

  // -- JS-E02: `++`/`--` USED AS A VALUE yields the value BEFORE the update -----------------------

  test("JS-E02 — a POSTFIX increment used as a value keeps java's before-the-update semantics") {
    val p = port("public class B { int f(int i) { return i++; } }")
    assertConsults(p, JS.E(2), fired = true)
    // the emitter renders the temporary; what matters is that the RETURNED value is the old one,
    // which the naive `{ i += 1; i }` gets wrong and every circular buffer noticed (§4.4).
    assertEmitsMatch(p, "(?s).*i \\+= 1.*")
  }

  test("JS-E02 — a PREFIX increment is the same row at the other shape") {
    val p = port("public class B { int f(int i) { return ++i; } }")
    assertConsults(p, JS.E(2), fired = true)
  }

  test("JS-E02 — a unary `!` is consulted and does not fire") {
    val p = port("public class B { boolean f(boolean b) { return !b; } }")
    assertConsults(p, JS.E(2))
    assertEmits(p, "!b")
  }

  // -- JS-E03: compound assignment NARROWS implicitly, in STATEMENT position ----------------------

  test("JS-E03 — `byte += byte` narrows back, because java's promotion computed an `int`") {
    val p = port("public class C { void f(byte b) { b += 3; } }")
    assertConsults(p, JS.E(3), fired = true)
    assertEmits(p, "scala.Byte")
  }

  test("JS-E03 — `int += int` is consulted and does NOT narrow") {
    val p = port("public class C { void f(int i) { i += 3; } }")
    assertConsults(p, JS.E(3))
    intercept[munit.FailException](assertConsults(p, JS.E(3), fired = true))
  }

  test("JS-E03 — a REFERENCE compound assignment (`String +=`) is consulted and does not narrow") {
    val p = port("public class C { void f(String s) { s += \"x\"; } }")
    assertConsults(p, JS.E(3))
    intercept[munit.FailException](assertConsults(p, JS.E(3), fired = true))
  }

  // -- JS-E04: the SAME difference in EXPRESSION position -----------------------------------------
  //
  // The row this whole mechanism was designed around: two arms lower a `CtOperatorAssignment`, the
  // statement one narrowed and the expression one did not, twelve lines apart, for the whole life of
  // the file. The predicate is ONE function (`SpoonTir.compoundNarrow`) and both dispatches consult
  // it now — which is what these tests assert in BOTH directions, because a predicate consulted and
  // never firing discharges the obligation and emits the same wrong code.

  test("JS-E04 — `(b += 3)` used as a VALUE narrows back, exactly as the statement form does") {
    assertEquals(Differences.byId(JS.E(4)).status, Status.Handled)
    val p = port("public class D { int f(byte b) { return (b += 3); } }")
    assertConsults(p, JS.E(4), fired = true)
    assertEmits(p, "scala.Byte")
  }

  test("JS-E04 — `short` and `char` are the other two shapes java's promotion widens past") {
    val p = port("public class D { int f(short s, char c) { return (s += 1) + (c += 1); } }")
    assertConsults(p, JS.E(4), fired = true)
    assertEmits(p, "scala.Short")
    assertEmits(p, "scala.Char")
  }

  test("JS-E04 — `int += int` as a value is consulted and does NOT narrow") {
    val p = port("public class D { int f(int i) { return (i += 3); } }")
    assertConsults(p, JS.E(4))
    intercept[munit.FailException](assertConsults(p, JS.E(4), fired = true))
  }

  test("JS-E04 — a REFERENCE compound assignment (`String +=`) as a value is consulted and does not narrow") {
    val p = port("public class D { String f(String s) { return (s += \"x\"); } }")
    assertConsults(p, JS.E(4))
    intercept[munit.FailException](assertConsults(p, JS.E(4), fired = true))
  }

  test("JS-E04 — the STATEMENT form of the same node consults JS-E03 and not JS-E04") {
    // The dispatch discriminator, asserted at the pair it was introduced for: one Spoon kind, two
    // rows, and a kind-keyed attachment could not have told them apart.
    val p = port("public class D { void f(byte b) { b += 3; } }")
    assertConsults(p, JS.E(3), fired = true)
    assertNotConsults(p, JS.E(4))
  }

  test("JS-E04 is discharged, and JS-E17 beside it is discharged too — no remaining work at this kind") {
    // JS-E17 (lvalue single evaluation, F7) is now `Handled`: the emitter binds non-trivial
    // lvalue subexpressions so each is evaluated exactly once. Both rows are consulted and the
    // undischarged list is empty.
    val p = port("public class D { int f(byte b) { return (b += 3); } }")
    assertEquals(p.catalog.undischarged.map(_.id), Nil)
  }

  // -- JS-E17: compound assignment and ++/-- evaluate the LVALUE ONCE (F7) -----------------------

  test("JS-E17 — array index with a CALL: subexpressions bound, each evaluated once") {
    val p = port("""
      public class E17a {
        int[] a; int seq;
        int next() { return seq++; }
        void f() { a[next()] += 5; }
      }""")
    assertConsults(p, JS.E(17), fired = true)
    // the index `next()` is bound to a temporary
    assertEmits(p, "$lv1")
    // the lvalue rendered with the temporary, not with the call repeated
    assertNotEmits(p, "this.a(this.next()) = this.a(this.next())")
  }

  test("JS-E17 — field select through a CALL: qualifier bound") {
    val p = port("""
      public class E17b {
        int x;
        static E17b get() { return new E17b(); }
        static void f() { get().x += 3; }
      }""")
    assertConsults(p, JS.E(17), fired = true)
    assertEmits(p, "$lv1")
  }

  test("JS-E17 — post-increment in EXPRESSION position with non-trivial target") {
    // The expression path goes through CtUnaryOperator -> incDecOf -> Tree.IncDec.
    // JS-E17 is NOT owed at CtUnaryOperator (it attaches to CtOperatorAssignment), but the
    // emitter's Tree.IncDec arm still binds the target. Assert on emitted text.
    val p = port("""
      public class E17c {
        int[] a; int seq;
        int next() { return seq++; }
        int f() { return a[next()]++; }
      }""")
    // the IncDec arm binds the target
    assertEmits(p, "$lv1")
    // post-increment yields the value BEFORE the update
    assertEmits(p, "$prev")
  }

  test("JS-E17 — pre-increment with non-trivial target") {
    val p = port("""
      public class E17d {
        int[] a;
        int next() { return 0; }
        int f() { return ++a[next()]; }
      }""")
    assertEmits(p, "$lv1")
    // pre-increment: the bound lvalue is incremented then read back
    assertEmits(p, "this.a($lv1) += 1; this.a($lv1)")
  }

  test("JS-E17 — simple lvalue left DIRECT: no binding, no digest churn") {
    val p = port("""
      public class E17e {
        int x;
        void f() { x += 5; }
      }""")
    assertConsults(p, JS.E(17), fired = true)
    // no temporaries minted for a simple ident lvalue
    assertNotEmits(p, "$lv")
    assertEmits(p, "this.x = this.x + 5")
  }

  test("JS-E17 — field.items compound multiply: non-trivial index bound") {
    val p = port("""
      public class E17g {
        float[] items;
        int colOffset;
        void f(int o) {
          items[(o + colOffset) + 1] *= 0.5f;
        }
      }""")
    assertConsults(p, JS.E(17), fired = true)
    // the index expression (o + colOffset) + 1 is non-trivial, so binding occurs
    assertEmits(p, "$lv")
  }

  test("JS-E17 — narrowing cast preserved with bound lvalue") {
    val p = port("""
      public class E17f {
        byte[] a; int seq;
        int next() { return seq++; }
        void f() { a[next()] += 3; }
      }""")
    assertConsults(p, JS.E(17), fired = true)
    assertEmits(p, "$lv1")
    // the narrowing cast must survive
    assertEmits(p, "scala.Byte")
  }

  test("JS-E17 — a phase that REBUILDS a compound Assign via copy preserves the compound field") {
    // The coordinator's hazard: a phase that `.copy(rhs = ...)` an Assign must carry `compound`
    // through, or the emitter silently reverts to the direct form and the lvalue is evaluated twice.
    // This test runs a no-op phase (whose `mapTerm` rebuilds every node via `copy`) and checks that
    // the binding still fires.
    import balticporter.tir.Phase
    val identity = new Phase:
      val name = "identity-rebuild"
      // override transformTerm to trigger mapTerm's copy path on every node
      override def transformTerm(t: Term)(using Program): Term = t
    val p = port("""
      public class E17h {
        int[] a; int seq;
        int next() { return seq++; }
        void f() { a[next()] += 5; }
      }""", identity)
    assertConsults(p, JS.E(17), fired = true)
    // the compound field survived the phase rebuild, so binding still fires
    assertEmits(p, "$lv1")
    assertNotEmits(p, "this.a(this.next()) = this.a(this.next())")
  }

  // -- JS-E05: the conditional operator's type is COMPUTED, not the lub of its branches -----------

  test("JS-E05 — a null branch is ascribed to the conditional's own type") {
    val p = port("public class E { String f(boolean b, String s) { return b ? s : null; } }")
    assertConsults(p, JS.E(5), fired = true)
  }

  test("JS-E05 — a conditional with no null branch is consulted all the same") {
    val p = port("public class E { int f(boolean b) { return b ? 1 : 2; } }")
    assertConsults(p, JS.E(5))
  }

  test("JS-E05 — MIXED BOXED NUMERICS: java UNBOXES both branches, so the port must too") {
    // `ENGINE-LIMITS.md` K17 face 2, at the shape that produced it. JLS 15.25.2 gives a conditional
    // whose operands are `Long` and `Double` binary numeric promotion: both are unboxed, promoted to
    // `double`, and the result re-boxed — so the expression's type really is `Double` and the `Long`
    // branch really does become one. Scala's `if` types as the lub (`java.lang.Number`) and the
    // branch value stays a `Long`, which is why writing java's type as a CAST at the enclosing slot
    // throws: a cast is not a conversion.
    val p = port(
      """public class E {
        |  Number f(String s) { return s.matches("d+") ? Long.valueOf(s) : Double.valueOf(s); }
        |}""".stripMargin)
    assertConsults(p, JS.E(5), fired = true)
    // BOTH branches, and the `Double` one is the half a coercion rule that only fixes cross-type
    // unboxing would miss — leaving the `if` at a lub of `Double` and `scala.Double`.
    assertEmitsMatch(p, "(?s).*java\\.lang\\.Long\\.valueOf\\(s\\)\\.doubleValue\\(\\).*")
    assertEmitsMatch(p, "(?s).*java\\.lang\\.Double\\.valueOf\\(s\\)\\.doubleValue\\(\\).*")
  }

  test("JS-E05 — BULLET 2: a `byte` branch against a representable constant `int` stays `byte`") {
    // The case where an always-promote rule would be UNFAITHFUL. JLS 15.25.2 bullet 2 keeps the
    // conditional at `byte`, so the constant narrows and the `byte` does not widen. The engine gets
    // this by construction rather than by a rule of its own: the target is java's OWN answer for the
    // conditional, never a promotion this pass computes.
    val p = port("public class E { byte f(boolean b, byte v) { return b ? v : 1; } }")
    assertConsults(p, JS.E(5), fired = true)
    // the CONSTANT moves, not the `byte` — assert the direction, because `scala.Byte` alone is in
    // the emitted text either way (it is the parameter's own type) and would pass vacuously.
    assertEmitsMatch(p, "(?s).*else 1\\.asInstanceOf\\[scala\\.Byte\\].*")
  }

  test("JS-E05 — a WIDENING branch is converted TOO: scala 3 has NO weak conformance") {
    // The cell this suite used to enshrine, and it was FALSE. Scala 2's weak conformance made
    // `if (b) i else d` a `Double`; SCALA 3 DROPPED IT, so the two branches type as `Int | Double`,
    // the `Int` branch BOXES, and the expression java computed as a `double` is a
    // `java.lang.Integer` at run time. PROBED on 3.8.4 before this was written: `("" + x)` prints
    // `3` where java prints `3.0`, and the `asInstanceOf[java.lang.Double]` an enclosing slot then
    // writes throws — which is `ENGINE-LIMITS.md` K17's own defect, one cell along.
    //
    // The conversion is REDUNDANT wherever an expected type reaches the branch (a `double` return
    // harmonises the `Int` on its own) and it is never WRONG: `asInstanceOf` between two statically
    // primitive types is a CONVERSION in scala, in both directions (JS-E06).
    val p = port("public class E { double f(boolean b, int i, double d) { return b ? i : d; } }")
    assertConsults(p, JS.E(5), fired = true)
    assertEmits(p, "i.asInstanceOf[scala.Double]")
    assertNotEmits(p, "doubleValue")
  }

  test("JS-E05 — …and the widening one that has NO expected type to fall back on") {
    // Where the conditional feeds a string concatenation there is no expected type at all, so the
    // union is what the runtime sees: java's `+` sees the promoted `double` and prints `3.0`.
    val p = port("public class E { String f(boolean b, int i, double d) { return \"\" + (b ? i : d); } }")
    assertConsults(p, JS.E(5), fired = true)
    assertEmits(p, "i.asInstanceOf[scala.Double]")
  }

  test("JS-E05 — an operand the SOURCE already cast is read AFTER its cast, and ONCE") {
    // The defect this pass shipped and the CORPUS caught, on the first `measure-all`. `be.getType`
    // is the type Spoon records BEFORE the source's own casts, which `expr` applies on top — so a
    // `(float) Math.asin(…)` operand of a `float` conditional reads as a `double`, earns a
    // narrowing, and gets one more `asInstanceOf[scala.Float]` stacked on a term that is already a
    // `Float`. It says nothing, it moves a member digest, and neither a compile nor any count can
    // see it.
    //
    // ONE cast now, not two. This assertion used to demand two — "the source's own and the return
    // coercion's" — and the second was `coerce` asking `e.getType` the same stale question one
    // level out: at a `float` return slot, an operand java already converted to `float` is owed
    // nothing. `ENGINE-LIMITS.md` K17 named that redundancy at `JsonValue.asByte` and said the fix
    // "belongs at `coerce` reading the TIR type it is handed, which is its own change and its own
    // measurement"; K17 face 3 is that change, and this is the assertion it moves. Note WHY the
    // old form passed: it asserted a PRESENCE that the defect supplied, so it would have failed the
    // correct emission — the same trap face 2's `assertNotEmits` fell into, one direction over.
    val p = port(
      """public class E {
        |  float f(boolean b, float g) { return b ? (float) Math.asin(g) : g * 0.5f; }
        |}""".stripMargin)
    assertConsults(p, JS.E(5))
    assertEmits(p, "asInstanceOf[scala.Float]")
    assertNotEmits(p, "asInstanceOf[scala.Float].asInstanceOf[scala.Float]")
  }

  test("JS-E05 — TWO source casts: the effective type is the OUTERMOST one, which is the HEAD") {
    // The test above holds for ONE cast whichever end of `getTypeCasts` a reader takes, so it
    // could not see the order. Spoon lists the casts OUTERMOST FIRST — `expr` folds them with
    // `foldRight`, which makes the head the outer `Tree.Typed`, and the emitted text for
    // `(Integer)(Object) o` is `o.asInstanceOf[Object].asInstanceOf[Integer]`, java's own order.
    // A reader taking `lastOption` therefore gets the INNERMOST cast: here `(double)`, which reads
    // as a `double` operand of a `float` conditional and earns a narrowing the source already
    // wrote. `SpoonTir.castType` is the one place the question is asked (`CLAUDE.md` §4.6's
    // "one idiom, six sites").
    val p = port(
      """public class E {
        |  float f(boolean b, double d, float g) { return b ? (float)(double) d : g; }
        |}""".stripMargin)
    assertConsults(p, JS.E(5))
    assertEmits(p, "d.asInstanceOf[scala.Double].asInstanceOf[scala.Float]")
    assertNotEmits(p, "asInstanceOf[scala.Float].asInstanceOf[scala.Float].asInstanceOf[scala.Float]")
  }

  test("JS-E05 — a REFERENCE conditional is consulted and no branch is converted") {
    // The other half of the row, and the one that must NOT move: java's type for a reference
    // conditional is a lub and scala's is also a lub. Only the numeric case is a conversion.
    val p = port("public class E { Object f(boolean b, String s, Integer i) { return b ? s : i; } }")
    assertConsults(p, JS.E(5))
    assertNotEmits(p, "intValue")
  }

  // -- JS-E06: a cast expression's TYPE is the cast's, and the enclosing context converts THAT ----
  //
  // The row attaches at `Rendered("Typed")` — a cast IS a node in the emitter's rendering dispatch,
  // which the row denied for as long as it was `Unmechanised`. What the consult asks is the ONE
  // checkable cell (a primitive target over a wrapper of a DIFFERENT primitive), so most of these
  // fixtures consult it without firing: the frontend's answer has already put the conversion where
  // java had it, and the cell that remains needs a RETYPING to reach — which is the last test here.
  // The emitted text is still the bulk of the evidence, which is why every one of these asserts a
  // PRESENCE and not only an absence (`ENGINE-LIMITS.md` K17's own lesson about the E05 spec that
  // enshrined the wrong claim).

  test("JS-E06 — a cast expression at a REFERENCE slot boxes at the CAST's type, not the operand's") {
    // `ENGINE-LIMITS.md` K17 face 3, at the shape that produced it. JLS 5.1.7 boxes the expression's
    // OWN type, and a cast expression's type is the cast's — so `(long) Math.ceil(d)` returned from
    // a method declared `Object` is a `java.lang.Long`. Read as the operand's pre-cast `double` the
    // port wrote `.asInstanceOf[scala.Long].asInstanceOf[java.lang.Double]`, which is an ASSERTION
    // that a `Long` is a `Double`: `class java.lang.Long cannot be cast to class java.lang.Double`,
    // at run time, with a green compile and every check count flat.
    val p = port(
      """public class E {
        |  public Object f(double d) { return (long) Math.ceil(d); }
        |}""".stripMargin)
    assertConsults(p, JS.E(6))
    assertEmits(p, "asInstanceOf[scala.Long].asInstanceOf[java.lang.Long]")
    assertNotEmits(p, "java.lang.Double")
  }

  test("JS-E06 — every primitive cast boxes at its OWN wrapper, including the two non-`Number`s") {
    // The whole table, because the defect was a lookup keyed on the wrong type and a lookup is
    // exactly the thing that can be right for one row and wrong for the next. `char`/`boolean` are
    // the rows a `Number`-shaped assumption would get wrong twice over.
    val p = port(
      """public class E {
        |  Object b(int v)  { return (byte) v; }
        |  Object s(int v)  { return (short) v; }
        |  Object c(int v)  { return (char) v; }
        |  Object l(double v) { return (long) v; }
        |  Object f(double v) { return (float) v; }
        |  Object i(double v) { return (int) v; }
        |}""".stripMargin)
    assertEmits(p, "asInstanceOf[scala.Byte].asInstanceOf[java.lang.Byte]")
    assertEmits(p, "asInstanceOf[scala.Short].asInstanceOf[java.lang.Short]")
    assertEmits(p, "asInstanceOf[scala.Char].asInstanceOf[java.lang.Character]")
    assertEmits(p, "asInstanceOf[scala.Long].asInstanceOf[java.lang.Long]")
    assertEmits(p, "asInstanceOf[scala.Float].asInstanceOf[java.lang.Float]")
    assertEmits(p, "asInstanceOf[scala.Int].asInstanceOf[java.lang.Integer]")
  }

  test("JS-E06 — the OTHER direction is already faithful and must stay a bare `asInstanceOf`") {
    // The half this row was PREDICTED to need and does not, kept as a test because "we checked and
    // the answer was do nothing" is otherwise indistinguishable from nobody having looked.
    //
    // PROBED against javac and scalac 3.8.4, the same instrument K17 faces 1 and 2 were settled
    // with. Java's `(prim) objectExpr` is NOT a conversion and performs NO `Number` dispatch: JLS
    // 5.5 gives it a narrowing reference conversion to the EXACT wrapper followed by an unbox, so
    // `(double) o` on an `Object` holding a `Long` throws `ClassCastException` — and so does it on
    // a `Number`-typed operand, which is the shape that most invites the mistake. Scala's
    // `asInstanceOf[scala.Double]` on the same operand compiles to `unboxToDouble`, which throws in
    // exactly the same cells: all 45 of (9 runtime classes x 5 primitives) agree between the two
    // languages, `Character` and `Boolean` included.
    //
    // So a checked unbox-and-convert helper here would CONVERT where java THROWS — it would turn a
    // faithful port into an unfaithful one, and it would do it while making tests pass.
    val p = port(
      """public class E {
        |  double f(Object o) { return (double) o; }
        |  int g(Number n)    { return (int) n; }
        |}""".stripMargin)
    // …and the consult is REACHED and does not fire, which is the assertion that separates "java
    // does no conversion here" from "nobody asked": `Object` and `Number` are not wrappers, so the
    // cell this row can check is not this one.
    assertConsults(p, JS.E(6))
    assertNoFindings(CastConversionCheck.check(p.after, p.after.units).map(_.report))
    assertEmits(p, "o.asInstanceOf[scala.Double]")
    assertEmits(p, "n.asInstanceOf[scala.Int]")
    // no runtime dispatch, and in particular not the `Number` accessors a conversion would need
    assertNotEmits(p, "doubleValue")
    assertNotEmits(p, "intValue")
  }

  test("JS-E06 — a WRAPPER-typed operand at a primitive slot UNBOXES, because that is a conversion") {
    // The cell that IS a conversion in java and must not be swept up by the row above: JLS 5.1.8
    // unboxes at the wrapper's own primitive and 5.1.2 widens from there, so `(double) aLong` is
    // `7.0`. `Long` is statically known here, which is exactly what makes it decidable — the
    // `Object` operand above is the same expression with the knowledge removed, and java raises
    // there for want of that knowledge rather than converting.
    //
    // This is the row's own sentence at the CAST rather than at the slot, and it was broken in both
    // directions before it was written: the emission was `v.asInstanceOf[scala.Double]`, a
    // `unboxToDouble` that demands a `java.lang.Double` and throws on a `Long`. The older form put a
    // `.doubleValue()` AFTER that checkcast, where nothing can reach it. No corpus site exercises
    // the shape, so no count and no compile could ever have said so.
    val p = port("public class E { double f(Long v) { return (double) v; } }")
    assertEmits(p, "v.doubleValue()")
    assertNotEmits(p, "asInstanceOf[scala.Double]")
  }

  test("JS-E06 — `Character` is not a `Number`, so its unbox is TWO steps and never `intValue`") {
    // `Character` and `Boolean` carry `charValue()`/`booleanValue()` and none of the `Number`
    // family, so the collapse that is exact for the six numeric wrappers names a member no class in
    // the chain declares. Loud rather than silent, which is the one thing in its favour — and the
    // reason this is asserted rather than assumed. Java's own two steps: unbox at `char`, widen to
    // `int`.
    val p = port("public class E { int f(Character c) { return (int) c; } }")
    assertEmits(p, "c.charValue().asInstanceOf[scala.Int]")
    assertNotEmits(p, "c.intValue()")
  }

  test("JS-E06 — the RESIDUE is a value a later PHASE retyped, and the emitter COUNTS it") {
    // The cell this row's `Partial` names, and the reason it has never been measured: the frontend
    // decides a cast from the type the operand has IN THE JAVA (`SpoonTir.castOf`), so a wrapper at
    // a primitive target is already `v.doubleValue()` before the emitter sees it. What no frontend
    // reading can answer for is a RETYPING that lands after it — a phase moves an operand's static
    // type and moves no cast, so an assertion that was right when it was built is java's CONVERSION
    // by the time it is rendered, with a green compile and no count able to see it.
    //
    // No corpus port has the shape — this lane reads 0 on all fifteen — which is exactly why the
    // FIXTURE is the evidence. The phase below is the smallest thing that produces it: it retypes
    // one `Object`-typed operand to `java.lang.Long` and touches nothing else, which is what a
    // retyping phase does to a slot.
    val retype = new Phase:
      def name: String = "spec/retype-operand"
      override def transformIdent(i: Tree.Ident)(using p: Program): Term =
        val long = p.symbols.all.find(_.fullName == "java.lang.Long").map(_.id)
        if p.symbolOf(i.sym).exists(_.name == "o") && long.isDefined
        then i.copy(tpe = TypeRepr.TypeRef(TypeRepr.NoPrefix, long.get))
        else i
    val src =
      """public class E {
        |  int f(Object o) { return (int) o; }
        |  Long keep() { return null; }
        |}""".stripMargin
    // the PREMISE, asserted so the test cannot pass for the wrong reason: without the phase the
    // frontend's own answer stands and there is nothing here to count.
    val plain = port(src)
    assertNoFindings(CastConversionCheck.check(plain.after, plain.after.units).map(_.report))
    val p = port(src, retype)
    assertConsults(p, JS.E(6), fired = true)
    assertFinds(CastConversionCheck.check(p.after, p.after.units).map(_.report), "UnboxAsserted")
  }

  // -- JS-E14: string concatenation with a NON-`String` left operand ------------------------------

  test("JS-E14 — `obj + \"s\"` stringifies the left, because scala has no `+` on `obj`") {
    val p = port("public class F { String f(Object o) { return o + \"x\"; } }")
    assertConsults(p, JS.E(14), fired = true)
    assertEmits(p, "valueOf")
  }

  test("JS-E14 — a `String` left operand needs nothing, and the row is consulted anyway") {
    val p = port("public class F { String f(String s) { return s + \"x\"; } }")
    assertConsults(p, JS.E(14))
    assertNotEmits(p, "valueOf")
  }

  test("JS-E14 — numeric `+` is consulted and does not fire") {
    val p = port("public class F { int f(int a, int b) { return a + b; } }")
    assertConsults(p, JS.E(14))
  }

  // -- JS-E15: an assignment IS an expression, with the assigned value ----------------------------

  test("JS-E15 — an assignment used as a value yields the assigned value") {
    val p = port("public class G { int f(int a, int b) { return a = b; } }")
    assertConsults(p, JS.E(15), fired = true)
  }

  test("JS-E15 — the SAME assignment as a statement never reaches the expression dispatch") {
    // The dispatch is the key, and this is the pair that proves it: one java construct, two
    // positions, and only one of them owes the row.
    val p = port("public class G { void f(int a, int b) { a = b; } }")
    assertNotConsults(p, JS.E(15))
  }

  // -- JS-E07: the PHASE surface ------------------------------------------------------------------

  test("JS-E07 — the citation comes from a PHASE and names a DECLARATION, not a site") {
    val junit =
      """package p;
        |import org.junit.Assert;
        |import org.junit.Test;
        |public class H {
        |  @Test public void widened() { long v = 2L; Assert.assertEquals(1, v); }
        |}
        |""".stripMargin
    val p = port(junit, new balticporter.transform.TestFrameworkTransform())
    assertCites(p, JS.E(7), about = "widened")
  }

  test("JS-E07 — a widening in a FIELD INITIALISER is cited at the FIELD, not at the next member") {
    // The citation state is a flag `promote` sets and the `DefDef` hook reads, which the bottom-up
    // traversal reaches after the body. A field's initialiser is not inside a `DefDef` — a lambda in
    // one holds the promotion perfectly well — so the flag survived to the next declaration the
    // traversal reached and that one took the citation: here the class's own `<init>`, and with the
    // field last in a body, a member of the NEXT class.
    //
    // Nothing else can see this. The emitted text is identical, every check count is identical, and
    // `catalog(consulted)` counts the row either way; what moves is only WHICH declaration an agent
    // is sent to, and it is sent to one with nothing in it (§4.575).
    val a =
      """package p;
        |import org.junit.Assert;
        |public class A {
        |  static Runnable check = () -> Assert.assertEquals(1, 2L);
        |}
        |""".stripMargin
    val b =
      """package p;
        |import org.junit.Assert;
        |import org.junit.Test;
        |public class B {
        |  @Test public void untouched() { Assert.assertEquals("a", "a"); }
        |}
        |""".stripMargin
    val p = portAll(List("A.java" -> a, "B.java" -> b), new balticporter.transform.TestFrameworkTransform())
    assertCites(p, JS.E(7), about = "A#check")
    val at = p.catalog.citedAt(JS.E(7))
    assert(!at.exists(_.contains("<init>")) && !at.exists(_.contains("untouched")),
      s"the widening is in `check` and the citation names a member the phase never touched: $at")
  }



  // -- the partition, asserted rather than left to a reader ---------------------------------------

  test("every JS-E row is either wired, declared unmechanised, or owes nothing — and says which") {
    val byKind = Differences.expressions.groupBy(_.attaches match
      case _: Attaches.Lowered      => "lowered"
      case _: Attaches.Rendered     => "rendered"
      case _: Attaches.Cited        => "cited"
      case _: Attaches.Unmechanised => "unmechanised"
      case _                        => "none")
    // Every row is in exactly one bucket by construction; what this asserts is that no bucket has
    // silently swallowed the others. A wave that marked area E `Unmechanised` to keep a lane green
    // is what this test exists to catch.
    assertEquals(byKind.values.map(_.size).sum, Differences.expressions.size)
    assert(byKind.getOrElse("lowered", Nil).nonEmpty, "no JS-E row is wired to the lowering dispatch")
    assert(byKind.getOrElse("cited", Nil).nonEmpty, "no JS-E row is wired to the phase surface")
    // …and every row that claims NO obligation is one the registry also calls a non-difference or
    // handled-by-construction. A row with an open status and no obligation would be a gap nothing
    // counts.
    val silentlyExcused = byKind.getOrElse("none", Nil).filter(d => d.status.isOpen)
    assertEquals(silentlyExcused.map(_.id), Nil,
      "an Open row claiming NoObligation is a gap no lane can see")
  }
