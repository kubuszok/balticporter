package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.CollectionsTransform

/** Two emitter seams that had no spec at all, pinned THROUGH THE PIPELINE — a java snippet in, the
  * emitted Scala asserted. Both are §4.4's defect class (valid Scala meaning something else), and
  * both were built from a failure found by running a ported test suite rather than by compiling.
  *
  *   - **the enhanced-for BINDING** (ENGINE-LIMITS K7). `for (Object e : xs)` is a DECLARATION:
  *     java resolves every use of `e` against `Object`. Scala's `for (e <- xs)` binds at the
  *     ITERABLE's element type, so the type of `e` silently changes.
  *   - **a `return` inside a LAMBDA.** Scala has no non-local return, so a java lambda that returns
  *     early needs a `def` to return from — and the two things that decide it (does this subtree
  *     return from the construct that OWNS it, and what result type can be given) each have a
  *     failure mode of their own.
  *
  * No phase is involved: `port(java)` with no phases is the emitter's own identity fixture, which
  * is what makes these tests about the emitter and not about a transform.
  */
class EmitterBindingAndReturnSpec extends PortSuite:

  // -------------------------------------------------------------------------------------------
  // the enhanced-for BINDING
  // -------------------------------------------------------------------------------------------

  test("a binding declared at a SUPERTYPE is re-bound — scala would otherwise change `e`'s type") {
    // `Object e` over a `List<String>`: java gives the body an `Object`, scala's own binding gives
    // it a `String`. Over a WILDCARD collection the same loss is fatal rather than merely different
    // (`Found: ?1.CAP`), and no retyping at the collection fixes it — the loss is at the binding.
    val p = port(
      """package demo;
        |import java.util.List;
        |class B { void each(List<String> xs) { for (Object e : xs) { e.hashCode(); } } }
        |""".stripMargin
    )
    // K9: `xs` is a `java.util.List` the pipeline kept, so the emitter uses java's own desugaring
    // (JLS 14.14.2) — a while-loop over `iterator()`/`hasNext()`/`next()` — rather than `for`,
    // which would fail with "value foreach is not a member of java.util.List".
    assertEmits(p, "e$it = xs.iterator()")
    assertEmits(p, "val e: java.lang.Object = e$it.next().asInstanceOf[java.lang.Object]")
    // the alias is INSIDE the body, so it is re-bound each iteration exactly as java's is.
    assertNotEmits(p, "for (e <- xs)")
  }

  test("…and it does NOT fire when the two agree — a difference must be PROVABLE") {
    // Treating an unreadable element type as "differs" would put a cast on every for-each in the
    // corpus to fix the handful that need one.
    val p = port(
      """package demo;
        |import java.util.List;
        |class B {
        |  void exact(List<String> xs)  { for (String s : xs) { s.length(); } }
        |  void array(String[] xs)      { for (String s : xs) { s.length(); } }
        |}
        |""".stripMargin
    )
    assertEmits(p, "for (s <- xs)")
    assertNotEmits(p, "s$e")
    assertNotEmits(p, "asInstanceOf[java.lang.String]")
  }

  test("the fresh name is derived from the RAW name and escaped as a WHOLE — `object$e`, never `` `object`$e ``") {
    // Appending to the escaped form gives `` `object`$e ``, which is not an identifier: measured
    // 0 -> 3 on libGDX main, as an E040 syntax error in a file that had compiled for weeks. A
    // suffixed keyword needs no escape — but only because `esc` is applied to the whole name.
    val p = port(
      """package demo;
        |import java.util.List;
        |class B { void each(List<String> xs) { for (Object object : xs) { object.hashCode(); } } }
        |""".stripMargin
    )
    // K9: `xs` is a `java.util.List` the pipeline kept, so the while-loop form is emitted.
    // The iterator variable derives from the RAW name: `object$it`, not `` `object`$it ``.
    assertEmits(p, "object$it = xs.iterator()")
    assertEmits(p, "val `object`: java.lang.Object = object$it.next().asInstanceOf[java.lang.Object]")
    assertNotEmits(p, "`object`$it")
  }

  // -------------------------------------------------------------------------------------------
  // a `return` inside a LAMBDA
  // -------------------------------------------------------------------------------------------

  test("a VOID lambda that returns early becomes a `def` — scala has no non-local return") {
    // Emitted bare, the `return` either does not compile or returns from the ENCLOSING method.
    // `Unit` is the one result type derivable without reading the functional interface's SAM, and
    // it is exact when every `return` in the body is valueless.
    val p = port(
      """package demo;
        |class L {
        |  void go(boolean b) { Runnable r = () -> { if (b) return; System.out.println("x"); }; r.run(); }
        |}
        |""".stripMargin
    )
    assertEmitsMatch(p, """\{ def body\$1\(\): scala\.Unit = """)
    assertEmits(p, "body$1() }")
  }

  test("a lambda with NO return of its own is emitted plainly — the wrapper is not free") {
    val p = port(
      """package demo;
        |class L { void go() { Runnable r = () -> { System.out.println("x"); }; r.run(); } }
        |""".stripMargin
    )
    assertNotEmits(p, "def body$")
  }

  test("a VALUE-returning lambda takes the SAM's result — ADAPTED at the target where it is generic") {
    // `Supplier<String>.get` is declared `T get()`; the reference says what `T` is and Spoon's own
    // `TypeAdaptor` substitutes it, so the nested `def` can be named. This used to be I9's standing
    // refusal, whose stated reason was a missing mechanism rather than a property of the language.
    val p = port(
      """package demo;
        |import java.util.function.Supplier;
        |class L { void go() { Supplier<String> s = () -> { return "a"; }; s.get(); } }
        |""".stripMargin
    )
    assertEmitsMatch(p, """def body\$\d+\(\): java\.lang\.String = """)
    assertEmits(p, "return \"a\"")
  }

  test("…and it is REFUSED, not guessed, where the adaptation cannot answer — a RAW target") {
    // A `def` with a wrong result type would COMPILE. Left alone this is a bare `return` under a
    // function literal — scala's NON-LOCAL RETURN — which is why `OmissionCheck` counts it
    // (ENGINE-LIMITS M6/I9: "refuse loudly" is a claim about the emitted text, and here it is false).
    val p = port(
      """package demo;
        |import java.util.function.Supplier;
        |@SuppressWarnings("rawtypes")
        |class L2 { void go() { Supplier s = () -> { return "a"; }; s.get(); } }
        |""".stripMargin
    )
    assertNotEmits(p, "def body$")
    assertEmits(p, "return \"a\"")
  }

  test("`returnsIn` stops at a NESTED lambda — the inner `return` belongs to the inner construct") {
    // The same rule `breaksOut` follows for a nested loop. Reading through would wrap the OUTER
    // lambda for a `return` that is not its own, and leave the inner one bare.
    val p = port(
      """package demo;
        |class L {
        |  void go(boolean b) {
        |    Runnable outer = () -> {
        |      Runnable inner = () -> { if (b) return; System.out.println("i"); };
        |      inner.run();
        |    };
        |    outer.run();
        |  }
        |}
        |""".stripMargin
    )
    // exactly ONE wrapper, and it is the inner lambda's.
    assertEquals(clue(p.out).sliding("def body$".length).count(_ == "def body$"), 1)
    assertEmits(p, "val inner: java.lang.Runnable = () => { def body$1(): scala.Unit =")
  }

  // -------------------------------------------------------------------------------------------
  // a `return` inside an ENHANCED-FOR body — JS-S26
  // -------------------------------------------------------------------------------------------

  test("a `return` inside an enhanced-for body lowers to a while loop — avoiding a non-local return") {
    // Java's enhanced-for desugars to `.foreach(x => ...)` in Scala, so `return` inside the body
    // becomes a non-local return from the enclosing method. Under `-Werror`, this is an error.
    // The fix: lower to a while loop over `.iterator/.hasNext/.next()`, which avoids the lambda.
    val p = port(
      """package demo;
        |enum Token { A, B, C;
        |  static Token fromName(String name) {
        |    for (Token t : values()) { if (name.equals(t.name())) return t; }
        |    return null;
        |  }
        |}
        |""".stripMargin
    )
    // must NOT emit `for (t <- ...)` — that desugars to a lambda
    assertNotEmits(p, "for (t <-")
    // must emit a while loop with Scala-style iterator calls (no parens on .iterator/.hasNext)
    assertEmits(p, ".iterator;")
    assertEmits(p, ".hasNext)")
    assertEmits(p, ".next()")
    // the `return` stays as a plain method-level return — NOT a boundary.break
    assertEmits(p, "return t")
  }

  test("an enhanced-for body with NO return keeps the `for` form — the lowering is not free") {
    val p = port(
      """package demo;
        |class C {
        |  void each(String[] xs) { for (String x : xs) { System.out.println(x); } }
        |}
        |""".stripMargin
    )
    // no return => plain `for` comprehension, NOT a while loop
    assertEmits(p, "for (x <-")
    assertNotEmits(p, ".iterator")
  }

  // -------------------------------------------------------------------------------------------
  // F9 arity: iterator/hasNext parens decided from the CALLEE SYMBOL, never `program.owns`
  // -------------------------------------------------------------------------------------------

  test("F9: a program-declared iterable keeps java arity — iterator()/hasNext() with parens") {
    // A class implementing `Iterable<T>` declares `iterator()` with `()`. After the for-each is
    // lowered to a while loop, the emitted `iterator()/hasNext()` must carry parens because the
    // callee SYMBOL declares them. `program.owns` happened to get this right; the callee-symbol
    // rule must preserve it.
    val p = port(
      """package demo;
        |import java.util.Iterator;
        |class Items implements Iterable<String> {
        |  public Iterator<String> iterator() { return null; }
        |  static String find(Items xs) {
        |    for (String s : xs) { if (s.length() > 0) return s; }
        |    return null;
        |  }
        |}
        |""".stripMargin
    )
    assertNotEmits(p, "for (s <-")
    // program-declared iterator()/hasNext() — must have parens
    assertEmits(p, ".iterator()")
    assertEmits(p, ".hasNext()")
    assertEmits(p, ".next()")
  }

  test("F9: a scala Array uses parenless iterator/hasNext — extension methods have no parens") {
    // `Token.values()` returns a `Token[]` → `scala.Array[Token]`. The `iterator` comes from
    // `ArrayOps` (an extension method), which is parenless. The existing test above already covers
    // this; this test pins the assertion explicitly at the arity level.
    val p = port(
      """package demo;
        |class C {
        |  static String find(String[] xs) {
        |    for (String s : xs) { if (s.length() > 0) return s; }
        |    return null;
        |  }
        |}
        |""".stripMargin
    )
    assertNotEmits(p, "for (s <-")
    // scala Array — parenless iterator/hasNext (extension methods via ArrayOps)
    assertEmits(p, ".iterator;")
    assertEmits(p, ".hasNext)")
    assertNotEmits(p, ".iterator()")
    assertNotEmits(p, ".hasNext()")
    assertEmits(p, ".next()")
  }

  test("F9: a runtime shim receiver (JavaIterable) uses java arity — iterator()/hasNext() with parens") {
    // After CollectionsTransform, a program-declared class extending `java.lang.Iterable` is
    // re-parented to `JavaIterable`. The shim's `iterator()` and `JavaIterator.hasNext()` are
    // declared WITH `()` (CLAUDE.md §4.5). The old `program.owns` heuristic returned `false` for
    // these external types and emitted parenless calls — the defect F9 corrects.
    val p = port(
      """package demo;
        |import java.util.Iterator;
        |class Items implements Iterable<String> {
        |  public Iterator<String> iterator() { return null; }
        |  static String find(Items xs) {
        |    for (String s : xs) { if (s.length() > 0) return s; }
        |    return null;
        |  }
        |}
        |""".stripMargin,
      new CollectionsTransform()
    )
    assertNotEmits(p, "for (s <-")
    // After CollectionsTransform, the type is now JavaIterable — must keep java arity
    assertEmits(p, ".iterator()")
    assertEmits(p, ".hasNext()")
    assertEmits(p, ".next()")
  }
