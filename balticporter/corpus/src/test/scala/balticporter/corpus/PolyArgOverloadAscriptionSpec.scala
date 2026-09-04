package balticporter.corpus

import balticporter.testkit.PortSuite

/** A LAMBDA AT AN OVERLOADED SLOT — the one shape where leaving a poly expression bare is not the
  * faithful emission. */
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

  test("a slot that is a TYPE VARIABLE the call has yet to infer is ascribed too") {
    val p = port(
      """package demo;
        |interface Key<T> { }
        |interface NullableKey<T> { }
        |interface Xform { String apply(String s); }
        |class Store {
        |  <T> Store set(Key<T> key, T value)         { return this; }
        |  <T> Store set(NullableKey<T> key, T value) { return this; }
        |}
        |class Uses {
        |  static final Key<Xform> K = null;
        |  void go(Store st) { st.set(K, s -> s); }
        |}
        |""".stripMargin)
    // the alternatives AGREE at index 1 — both formals spell `T` — so the index-local comparison
    // declines, and scala still has no expected type there: it must resolve the overload before it
    // can solve `T`, and it resolves by typing the arguments. Java solved `T` from the KEY first.
    assertEmits(p, "): demo.Xform))")
  }

  test("a poly CONDITIONAL carries the lambda, and the ascription goes on the whole `if`") {
    val p = port(
      """package demo;
        |interface Key<T> { }
        |interface NullableKey<T> { }
        |interface Xform { String apply(String s); }
        |class Store {
        |  <T> Store set(Key<T> key, T value)         { return this; }
        |  <T> Store set(NullableKey<T> key, T value) { return this; }
        |}
        |class Uses {
        |  static final Key<Xform> K = null;
        |  void go(Store st, boolean flag) { st.set(K, flag ? s -> s : s -> s + "!"); }
        |}
        |""".stripMargin)
    // JLS 15.25 — java pushes the target THROUGH the conditional and types each branch against it,
    // so one branch names the target for both and the ascription goes on the `if`. Two ascriptions,
    // one per branch, would write the same type twice and leave the conditional's own type inferred.
    assertEmits(p, "): demo.Xform))")
    assertNotEmits(p, "s): demo.Xform) else")
    // …and it must render as an ASCRIPTION and never a cast: `asInstanceOf` would elaborate both
    // branches to `Function1`s first and then assert that a `Function1` is a `demo.Xform`, which
    // throws. `TirEmitter.polyOperand` answers for the conditional for exactly this reason.
    assertNotEmits(p, ".asInstanceOf[demo.Xform]")
  }

  test("NEGATIVE — a type-variable slot at an UNOVERLOADED callee gets nothing") {
    val p = port(
      """package demo;
        |interface Key<T> { }
        |interface Xform { String apply(String s); }
        |class Store { <T> Store set(Key<T> key, T value) { return this; } }
        |class Uses {
        |  static final Key<Xform> K = null;
        |  void go(Store st) { st.set(K, s -> s); }
        |}
        |""".stripMargin)
    // one alternative, so scala solves `T` from the sibling exactly as java does and SAM-converts
    // the bare literal. The overload conjunct is what keeps this case out, which is why the slot's
    // shape is a DISJUNCT under it rather than a rule of its own.
    assertNotEmits(p, "): demo.Xform))")
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
