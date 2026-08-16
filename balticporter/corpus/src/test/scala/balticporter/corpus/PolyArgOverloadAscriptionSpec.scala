package balticporter.corpus

import balticporter.testkit.PortSuite

/** A LAMBDA AT AN OVERLOADED SLOT — the one shape where leaving a poly expression bare is not the
  * faithful emission.
  *
  * `SpoonTir.polyExpression` says a lambda has no type of its own in EITHER language and takes one
  * from the slot it fills, so the frontend never casts one — and that rule is exactly right for as
  * long as the slot is a single formal. Where the callee's name stands for TWO alternatives of the
  * same arity, scala types the function literal BEFORE it can use an expected type, so no
  * alternative matches and the error names none of them:
  *
  * {{{
  * // java: two candidates at arity 2, resolved by the ARGUMENT's shape
  * T tagLine(CharSequence tag, boolean voidElement);
  * T tagLine(CharSequence tag, Runnable body);
  *
  * fa.tagLine("li", () => fa.text("x"))                          // E134 — none of the alternatives
  * fa.tagLine("li", (() => fa.text("x")): java.lang.Runnable)    // what javac resolved, written down
  * }}}
  *
  * ==an ASCRIPTION, never a CAST==
  * `polyExpression`'s refusal is about `asInstanceOf`: written as a cast the literal elaborates to a
  * `Function0` first and the cast then asserts that a `Function0` is a `Runnable`, which throws at
  * run time. `TirEmitter.polyOperand` renders a `Tree.Typed` over a poly term as `(e: T)` — scala's
  * own SAM conversion at an expected type — which is why the mint is a `Tree.Typed` and why no
  * emitter arm had to be added for it.
  *
  * ==the negatives are what make it a rule rather than a widening==
  * `CLAUDE.md` §5: ascribing every lambda would be CORRECT, would move emitted text on every port
  * that has one, and no count could see it. So the three below pin the conjuncts — an unoverloaded
  * callee (the shape that sits in the SAME java statement as the positive), alternatives that agree
  * at the lambda's own index, and a method REFERENCE, which `TirEmitter.samAscribed` already answers
  * for the forms it renders as a function literal and which renders as a bare qualified NAME in the
  * static form, where an ascription would APPLY a nilary method.
  */
class PolyArgOverloadAscriptionSpec extends PortSuite:

  /** the positive and its in-statement negative together: `tagLine` is overloaded at arity 2 and
    * `tagIndent` is not, and the java calls them from one expression. */
  private val src =
    """package demo;
      |class Appender {
      |  Appender tagLine(CharSequence tag)                    { return this; }
      |  Appender tagLine(CharSequence tag, boolean voidTag)   { return this; }
      |  Appender tagLine(CharSequence tag, Runnable body)     { return this; }
      |  Appender tagIndent(CharSequence tag, Runnable body)   { return this; }
      |  Appender text(CharSequence s)                         { return this; }
      |}
      |class Uses {
      |  void go(Appender fa) {
      |    fa.tagIndent("ul", () -> fa.tagLine("li", () -> fa.text("item")));
      |  }
      |}
      |""".stripMargin

  test("a lambda at an OVERLOADED slot is ascribed to the functional interface java resolved") {
    val p = port(src)
    // asserted at the CALL and not on the substring, because every declaration in the fixture
    // mentions both types too — a spec that reads the whole output cannot tell a formal from an
    // ascription.
    assertEmits(p, "fa.tagLine(\"li\", ((() => fa.text(\"item\")): java.lang.Runnable))")
    // the target is the LAMBDA's own type — the interface javac picked — and not a formal
    // re-derived from the callee, so the ascription names `Runnable` and never `boolean`.
    assertNotEmits(p, "): scala.Boolean)")
  }

  test("NEGATIVE — the UNOVERLOADED callee in the same statement gets nothing") {
    val p = port(src)
    // `tagIndent` has one alternative of this arity, so scala already has the expected type and
    // SAM-converts the bare literal. Ascribing here would be emitted text for nothing (§5), and
    // this is the case that sits closest to the positive: one java statement, two callees.
    assertEmits(p, "fa.tagIndent(\"ul\", () =>")
  }

  test("NEGATIVE — alternatives that AGREE at the lambda's index are no reason to ascribe") {
    val p = port(
      """package demo;
        |class Sink {
        |  void run(String name, Runnable body) { }
        |  void run(int id, Runnable body)      { }
        |}
        |class Uses { void go(Sink s) { s.run("a", () -> { }); } }
        |""".stripMargin)
    // two alternatives of arity 2, and both take a `Runnable` at index 1 — the lambda discriminates
    // nothing, so scala has one expected type for it whichever alternative wins. Asserted at the
    // CALL, since both declarations name `Runnable` in the same output.
    assertEmits(p, "s.run(\"a\", () =>")
    assertNotEmits(p, "): java.lang.Runnable))")
  }

  test("NEGATIVE — a METHOD REFERENCE at an overloaded slot is left to the emitter") {
    val p = port(
      """package demo;
        |class Sink {
        |  void accept(String name, boolean flag) { }
        |  void accept(String name, Runnable body) { }
        |}
        |class Uses {
        |  void tick() { }
        |  void go(Sink s) { s.accept("a", this::tick); }
        |}
        |""".stripMargin)
    // a reference is a poly expression too, and `TirEmitter.samAscribed` is the one mechanism that
    // answers for it — the STATIC form renders as a bare qualified name, where an ascription applies
    // a nilary method rather than converting it. Two mechanisms for one question is F8's finding.
    assertNotEmits(p, "this.tick: java.lang.Runnable")
  }

  test("NEGATIVE — a cast the JAVA SOURCE wrote is kept, and not doubled") {
    val p = port(
      """package demo;
        |class Sink {
        |  void accept(String name, boolean flag)  { }
        |  void accept(String name, Runnable body) { }
        |}
        |class Uses { void go(Sink s) { s.accept("a", (Runnable) () -> { }); } }
        |""".stripMargin)
    // `polyArgsUncast` keeps what java wrote, so the term is ALREADY a `Tree.Typed` when this runs;
    // a second ascription would be one layer of nothing over java's own disambiguation.
    assertNotEmits(p, "java.lang.Runnable): java.lang.Runnable")
  }
