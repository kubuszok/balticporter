package balticporter.testkit

import balticporter.catalog.{Attaches, Differences, JS, Status}
import balticporter.tir.HeapPollutionCheck

/** THE `JS-G` EDGE-CASE SUITE — the generics rows the engine wires, at the shape each row is about.
  *
  * Same contract as [[CatalogAreaESpec]], [[CatalogAreaSSpec]] and [[CatalogAreaCSpec]]: each test
  * asserts BOTH that the branch was live (`assertConsults`) and that the emitted Scala means what
  * java meant, because the obligation wrapper detects an ABSENT consult and cannot detect a WRONG
  * one.
  *
  * WHAT MAKES AREA G DIFFERENT FROM THE THREE BEFORE IT. Its rows are not about a node kind at all;
  * they are about a SLOT and about a TYPE. Two consequences run through the whole suite:
  *
  *   - the slot rows (`JS-G09`, `JS-G13`, `JS-G14`) attach at SEVEN dispatches, because java's
  *     assignment conversion (JLS 5.2) is one conversion reached from a local's initialiser, an
  *     assignment, a `return`, a call argument, a `new`'s argument, an array initialiser's element
  *     and a FIELD's initialiser. `SpoonTir.slotConsults` is the one place that states them, and it
  *     is called from the ARM rather than from `coerce` — `coerce` is not reached for a local with
  *     no initialiser or a zero-argument call, so a consult inside it would report a hole at
  *     exactly the nodes where the difference does not apply. The seventh is the one that needed a
  *     third `Dispatch`: a `CtField` is neither a statement nor an expression, so for as long as
  *     the enumeration was "the kinds we dispatch on" that slot could not be owed anywhere;
  *   - ELEVEN rows are still `Unmechanised`, and the last test asserts exactly which. Every one of
  *     them is decided while lowering or rendering a TYPE REFERENCE, which is neither of the
  *     frontend's two dispatches (a `CtTypeReference` is not a statement or an expression) nor the
  *     emitter's (a `TypeTree` is a `Tree` that is not a `Statement`) — plus `JS-G20`, which is
  *     per-phase discipline rather than one mechanism. `JS-G41` was on that list and is not any
  *     more: having nothing to TRANSLATE is not having nothing to DECIDE, and the decision an
  *     emitted declaration takes about java's heap pollution is to carry it, which is a consult at
  *     `Rendered("DefDef")` and a count in `HeapPollutionCheck`.
  *
  * `JS-G48` is `Cited("collections")` and has no test here: a citation needs the phase, and the
  * phase's own reified rewrites are exercised by `balticporter.corpus.CollectionsReifiedSpec`. The
  * partition test asserts the attachment so the two cannot drift apart silently.
  */
class CatalogAreaGSpec extends PortSuite:

  // -- JS-G31: a POLY EXPRESSION is typed by its TARGET, so nothing may cast it ------------------
  //
  // `ENGINE-LIMITS.md` K17 face 1. The defect this suite exists to keep closed is SILENT at the
  // compile — `(() => …).asInstanceOf[Supplier[? <: Path]]` has the right static type and throws
  // `ClassCastException` at run time — so `assertNotEmits("asInstanceOf")` is the whole assertion
  // and no error count could ever stand in for it.

  test("JS-G31 — a LAMBDA at a wildcard-applied SAM slot is emitted BARE, never cast") {
    // THE MEASURED SITE, reduced: `Optional<T>.orElseGet(Supplier<? extends T>)`. The receiver's
    // instantiation is known (`Optional<Path>`), so `knownReceiverArgs` substituted the formal and
    // cast the literal into it — which asserts that a `Function0` is a `Supplier`, and it is not.
    val p = port(
      """import java.util.Optional;
        |import java.nio.file.Path;
        |import java.nio.file.Paths;
        |public class A {
        |  Path f(Path location) {
        |    return Optional.ofNullable(location).orElseGet(() -> Paths.get(".").toAbsolutePath());
        |  }
        |}""".stripMargin)
    assertConsults(p, JS.G(31), fired = true)
    assertNotEmits(p, "asInstanceOf[java.util.function.Supplier")
    assertEmits(p, "orElseGet(() =>")
  }

  test("JS-G31 — the same call with a NON-poly argument is consulted and does NOT fire") {
    // The edge a consult count alone cannot see: the branch is live at every invocation, and a
    // value argument is an ordinary one that the cast arms are still free to convert.
    val p = port(
      """import java.util.Optional;
        |import java.nio.file.Path;
        |public class A {
        |  Path f(Path location, Path other) { return Optional.ofNullable(location).orElse(other); }
        |}""".stripMargin)
    assertConsults(p, JS.G(31))
  }

  test("JS-G31 — a METHOD REFERENCE is the same row at the other poly shape") {
    // The method reference fills `Comparator.comparing`'s `Function<? super T, ? extends U>`, which
    // is the slot this row is about — the enclosing `sort(Comparator<? super E>)` takes an ordinary
    // INVOCATION result, and the arms remain free to convert that one.
    val p = port(
      """import java.util.List;
        |import java.util.Comparator;
        |public class A {
        |  void f(List<String> l) { l.sort(Comparator.comparing(String::length)); }
        |}""".stripMargin)
    assertConsults(p, JS.G(31), fired = true)
    assertNotEmits(p, "asInstanceOf[java.util.function.Function")
  }

  test("JS-G31 — a lambda at a CONSTRUCTOR argument is answered too, at the other dispatch") {
    // `ctorCall` runs three more argument arms, and one of them (`appliedCtorArgs`) carried a copy
    // of the exclusion list that omitted the method-reference case the other copy had. The row
    // ATTACHES at the invocation dispatch; this consult is recorded without being owed.
    val p = port(
      """import java.util.function.Supplier;
        |public class A {
        |  static class Box<T> { Box(Supplier<? extends T> s) {} }
        |  Box<String> f() { return new Box<String>(() -> "x"); }
        |}""".stripMargin)
    assertConsults(p, JS.G(31), fired = true)
    assertNotEmits(p, ").asInstanceOf[java.util.function.Supplier")
  }

  test("JS-G31 — a java-written cast ON A POLY EXPRESSION is an ASCRIPTION, never an assertion") {
    // The case the test below DODGES: it keeps a java-written cast and checks it survives, at a
    // NON-poly operand where `asInstanceOf` is the right rendering. On a poly one it is not, and
    // this cast is not an unusual shape — java REQUIRES it wherever the target does not determine
    // the lambda's type: an overload to disambiguate, an `Object`/generic slot, a return of
    // `Object`. `polyArgsUncast` is right to keep it (it is the source's own, the innermost
    // `getTypeCasts` layers); what the emitter may not do is write it as an assertion, because the
    // literal then elaborates to a `scala.Function0` FIRST and the cast asserts that a `Function0`
    // is a `Callable`, which is K17 face 1's ClassCastException one syntax along.
    //
    // Scala SAM-converts at an ASCRIPTION — probed on 3.8.4 at a bare slot, a wildcard-applied one,
    // a two-parameter one and a bare method name — so the ascription is the same fix face 1 took
    // (hand the expected type to scalac where javac had it) written where the source demanded a
    // cast rather than deleted.
    val p = port(
      """public class A {
        |  Object f() { return (java.util.concurrent.Callable<String>) () -> "x"; }
        |  Object g() { return (Runnable) () -> { System.out.println("y"); }; }
        |}""".stripMargin)
    assertNotEmits(p, "asInstanceOf[java.util.concurrent.Callable")
    assertNotEmits(p, "asInstanceOf[java.lang.Runnable")
    assertEmits(p, "): java.util.concurrent.Callable[java.lang.String])")
    assertEmits(p, "): java.lang.Runnable)")
  }

  test("JS-G31 — …and at an ARGUMENT, where the row is consulted and the cast is java's own") {
    // The same rendering at the dispatch that consults the row, so the two halves cannot drift:
    // `polyArgsUncast` keeps the source's cast and the emitter still may not assert it.
    val p = port(
      """public class A {
        |  static void run(Runnable r) {}
        |  static void run(String s) {}
        |  void f() { run((Runnable) () -> {}); }
        |}""".stripMargin)
    assertConsults(p, JS.G(31), fired = true)
    assertNotEmits(p, "asInstanceOf[java.lang.Runnable")
    assertEmits(p, "): java.lang.Runnable)")
  }

  test("JS-G31 — a java-written CAST on the argument SURVIVES; only the arms' own casts are undone") {
    // The line the fix must not cross. `expr` folds the source's casts innermost-first, one node
    // per `getTypeCasts` entry, so those are exactly the layers this rule keeps — a rule that
    // stripped every `Tree.Typed` would delete a conversion the java author wrote.
    val p = port(
      """public class A {
        |  Object f(Object o) { return (String) o; }
        |}""".stripMargin)
    assertEmits(p, "asInstanceOf[java.lang.String]")
  }

  test("JS-G31 — a VARARG PACK changes the call's arity, and the row is answered per INDEX") {
    // The guard that used to stand here was `args.sizeIs != argEs.size => None`, which reads to the
    // catalog as "the difference does not APPLY at this call" — a vacuous guard, and the one shape
    // that reaches it is not rare: java's own vararg materialisation collapses N trailing arguments
    // into ONE array term, so EVERY vararg call with two or more variadic arguments declined,
    // including for the poly expression sitting in the FIXED prefix, which lines up perfectly.
    //
    // Per index now: the fixed prefix pairs with `argEs` position by position and the packed tail
    // is answered INSIDE the array, element by element, against the arguments it was built from.
    val p = port(
      """public class A {
        |  static void run(Runnable r, Object... rest) {}
        |  void f() { run(() -> {}, "a", "b"); }
        |}""".stripMargin)
    assertConsults(p, JS.G(31), fired = true)
    assertNotEmits(p, "asInstanceOf[java.lang.Runnable")
  }

  test("JS-G31 — …and a poly expression INSIDE the pack is reached too") {
    val p = port(
      """public class A {
        |  static void all(String name, Runnable... rest) {}
        |  void f() { all("n", () -> {}, () -> {}); }
        |}""".stripMargin)
    assertConsults(p, JS.G(31), fired = true)
    assertNotEmits(p, "asInstanceOf[java.lang.Runnable")
  }

  test("JS-G31 — a lambda whose target is one of OUR OWN interfaces is not cast either") {
    // The rule is about the EXPRESSION, not about who owns the interface: an in-program functional
    // interface is SAM-converted by scala on exactly the same rule as a JDK one.
    val p = port(
      """public class A {
        |  interface Fn { String get(); }
        |  static String use(Fn f) { return f.get(); }
        |  String f() { return use(() -> "x"); }
        |}""".stripMargin)
    assertConsults(p, JS.G(31), fired = true)
    assertNotEmits(p, "asInstanceOf[A.Fn]")
  }

  // -- the SLOT rows: JS-G09 / JS-G13 / JS-G14, at six dispatches and one predicate each ---------

  test("JS-G13 — a java array flows COVARIANTLY into a wider array slot; scala's Array is invariant") {
    // `String[] <: Object[]` in java (JLS 10.10, with an `ArrayStoreException` to pay for it) and
    // nothing like it in scala, so the store needs the cast written out.
    val p = port("public class A { Object[] f(String[] xs) { return xs; } }")
    assertConsults(p, JS.G(13), fired = true)
    assertEmits(p, "asInstanceOf[scala.Array[java.lang.Object]]")
  }

  test("JS-G13 — the SAME array type on both sides is consulted and does not fire") {
    val p = port("public class A { String[] f(String[] xs) { return xs; } }")
    assertConsults(p, JS.G(13))
    assertNotEmits(p, "asInstanceOf[scala.Array[java.lang.Object]]")
  }

  test("JS-G14 — a primitive at a reference slot boxes to the WRAPPER, not to the formal") {
    // The row's whole content is the TARGET of the boxing: `java.lang.Integer`, which satisfies both
    // the erased `Object` slot and a real `Integer`/`Number` one — where casting straight to
    // `Object` fails an `Integer` parameter Spoon erased at the call reference.
    val p = port("public class A { Object f(int x) { return x; } }")
    assertConsults(p, JS.G(14), fired = true)
    assertEmits(p, "asInstanceOf[java.lang.Integer]")
  }

  test("JS-G14 — a primitive at a PRIMITIVE slot is consulted and does not fire") {
    val p = port("public class A { int f(int x) { return x; } }")
    assertConsults(p, JS.G(14))
    assertNotEmits(p, "java.lang.Integer")
  }

  test("JS-G09 — a RAW value at a parameterised slot is java's UNCHECKED CONVERSION, and scala has none") {
    val p = port(
      """import java.util.List;
        |public class A { List<String> f(List raw) { return raw; } }""".stripMargin)
    assertConsults(p, JS.G(9), fired = true)
    assertEmits(p, "asInstanceOf[java.util.List[java.lang.String]]")
  }

  test("JS-G09 — a fully parameterised value at the same slot is consulted and does not fire") {
    val p = port(
      """import java.util.List;
        |public class A { List<String> f(List<String> l) { return l; } }""".stripMargin)
    assertConsults(p, JS.G(9))
  }

  test("JS-G09 / JS-G13 / JS-G14 — the slot rows are owed at a LOCAL, an ASSIGNMENT and a `new`'s argument too") {
    // The six-dispatch attachment, exercised at three of the six that are not a `return`. A consult
    // stated once per arm is a rule the next arm will not have (`ENGINE-LIMITS.md` F8), so what this
    // asserts is that all three arms reach the SAME function and none of them is a hole.
    val p = port(
      """public class A {
        |  static class Box { Box(Object o) {} }
        |  Object field;
        |  void f(int n, String[] xs) {
        |    Object local = n;
        |    field = xs;
        |    new Box(n);
        |  }
        |}""".stripMargin)
    assertConsults(p, JS.G(14), fired = true)
    assertConsults(p, JS.G(9))
  }

  test("JS-G09 / JS-G13 / JS-G14 — a FIELD INITIALISER is the seventh slot, and the only one at no term dispatch") {
    // The six above are all statements or expressions. A `CtField` is neither, so it enters neither
    // of the frontend's two dispatches and NO row could be owed at it — the slot was not unattached,
    // it was unreachable, and a library whose only boxing sites are field initialisers read
    // `consulted = 0` on all three rows, which is indistinguishable from "the difference does not
    // arise here". `Dispatch.Declaration` is that third position.
    val p = port(
      """public class A {
        |  Object boxed = 1;
        |  Object[] widened = new String[0];
        |}""".stripMargin)
    assertConsults(p, JS.G(14), fired = true)
    assertConsults(p, JS.G(13), fired = true)
    assertConsults(p, JS.G(9))
    // …and the translation was already right at this slot: `coercedExprOf` is the same call the
    // local arm makes. What was missing was only the record that it had been considered, which is
    // exactly the shape a coverage lane cannot report on its own.
    assertEmits(p, "asInstanceOf[java.lang.Integer]")
  }

  test("JS-G09 / JS-G13 / JS-G14 — a field with NO initialiser has no slot; consulted, none fires") {
    // The declaration dispatch's half of the rule the local one states below: the scope opens for
    // every field, and a field with nothing to convert answers `None` three times.
    val p = port("public class A { Object f; }")
    assertConsults(p, JS.G(9))
    assertConsults(p, JS.G(13))
    assertConsults(p, JS.G(14))
  }

  test("JS-G09 / JS-G13 / JS-G14 — a local with NO initialiser has no slot; consulted, none fires") {
    // The reason the consult may not live inside `coerce`: this arm never calls it, so a consult
    // there would report a hole at a node where the difference does not apply.
    val p = port("public class A { void f() { int n; n = 1; } }")
    assertConsults(p, JS.G(9))
    assertConsults(p, JS.G(13))
    assertConsults(p, JS.G(14))
  }

  // -- arrays: JS-G15 (the cast idiom), JS-G17 (`.length` and `T[]::new`) ------------------------

  test("JS-G15 — java forbids `new T[n]`, so the CAST IDIOM is the only generic array creation there is") {
    val p = port(
      """public class A {
        |  <T> T[] f(int n) { return (T[]) new Object[n]; }
        |}""".stripMargin)
    assertConsults(p, JS.G(15), fired = true)
    assertEmits(p, "new scala.Array[java.lang.Object](")
  }

  test("JS-G15 — an ordinary array creation is consulted and does not fire") {
    val p = port("public class A { int[] f(int n) { return new int[n]; } }")
    assertConsults(p, JS.G(15))
  }

  test("JS-G17 — java's array `length` is a FIELD and scala's is a method") {
    val p = port("public class A { int f(String[] xs) { return xs.length; } }")
    assertConsults(p, JS.G(17), fired = true)
    assertEmits(p, ".length")
  }

  test("JS-G17 — `T[]::new` is an IntFunction, not a no-arg supplier: a scala array needs a LENGTH") {
    val p = port(
      """import java.util.function.IntFunction;
        |public class A { IntFunction<String[]> f() { return String[]::new; } }""".stripMargin)
    assertConsults(p, JS.G(17), fired = true)
    assertEmits(p, "(size: scala.Int) => new scala.Array[java.lang.String](size)")
  }

  test("JS-G17 — an ordinary method reference is consulted for it and does NOT fire") {
    val p = port(
      """import java.util.function.Function;
        |public class A { Function<String, Integer> f() { return Integer::parseInt; } }""".stripMargin)
    assertConsults(p, JS.G(17))
  }

  // -- method references and SAM conversion: JS-G43, JS-G33 --------------------------------------

  test("JS-G43 — the five method-reference forms share one java syntax and are five DIFFERENT lambdas") {
    // The UNBOUND-INSTANCE form makes the receiver the function's first parameter, which is what
    // `self$` in the emitted text is; `Tree.MethodRef.referent` is the whole discriminator.
    val p = port(
      """import java.util.List;
        |import java.util.Comparator;
        |public class A {
        |  void f(List<String> l) { l.sort(Comparator.comparing(String::length)); }
        |}""".stripMargin)
    assertConsults(p, JS.G(43), fired = true)
    assertEmits(p, "self$")
  }

  // The two halves of the SAME defect, and they fail in OPPOSITE directions — which is why both are
  // here and why either one alone would have been fixed the wrong way round. Both referenced
  // methods are EXTERNAL, and that is the whole of it: an external member is interned with no
  // `Flags` (so `flags.isStatic` says *not static* about every JDK static) and with `NoType` for an
  // `info` whose slots cannot be named scope-free (so `methodParams` says *takes no arguments*
  // about a method whose one parameter is a type VARIABLE). An IN-PROGRAM reference has both facts
  // on its symbol, which is exactly why the corpus's own fixtures above never saw either.

  test("JS-G43 — a STATIC reference at an EXTERNAL method is a qualified NAME, not an unbound one") {
    val p = port(
      """import java.util.Objects;
        |import java.util.function.Predicate;
        |public class A { Predicate<Object> f() { return Objects::isNull; } }""".stripMargin)
    assertConsults(p, JS.G(43), fired = true)
    assertEmits(p, "java.util.Objects.isNull")
    // the negative: no receiver parameter was invented for a method that has no receiver.
    assertNotEmits(p, "self$: java.util.Objects")
  }

  test("JS-G43 — an UNBOUND reference keeps java's ARITY even where the formal is a type VARIABLE") {
    val p = port(
      """import java.util.Comparator;
        |public class A { Comparator<String> f() { return Comparable::compareTo; } }""".stripMargin)
    assertConsults(p, JS.G(43), fired = true)
    // `compareTo(T)` is arity 1, so the lambda is arity 2 — the receiver plus java's one argument.
    // Rendered off the symbol's `MethodType` it was `((self$) => self$.compareTo())`, which is a
    // one-parameter function at a `Comparator`: E086, and nothing else could see it.
    assertEmitsMatch(p, """self\$[^)]*, a0\$[^)]*\) => self\$\.compareTo\(a0\$\)""")
  }

  test("JS-G43 — `T::new` takes THE CONSTRUCTOR'S parameters, not none") {
    val p = port(
      """import java.util.function.Function;
        |public class A {
        |  static class Box { Box(String s) {} }
        |  Function<String, Box> f() { return Box::new; }
        |}""".stripMargin)
    assertConsults(p, JS.G(43), fired = true)
    assertEmitsMatch(p, """\(a0\$\) => new [^()]*Box\(a0\$\)""")
  }

  test("JS-G43 — a NILARY `T::new` is still the no-argument factory it always was") {
    val p = port(
      """import java.util.function.Supplier;
        |public class A {
        |  static class Box { Box() {} }
        |  Supplier<Box> f() { return Box::new; }
        |}""".stripMargin)
    assertEmits(p, "() => new ")
    assertNotEmits(p, "a0$")
  }

  test("JS-G33 — a SAM conversion is ASCRIBED where the reference becomes a function literal") {
    val p = port(
      """import java.util.function.Supplier;
        |public class A {
        |  static class Box { Box() {} }
        |  Supplier<Box> f() { return Box::new; }
        |}""".stripMargin)
    assertConsults(p, JS.G(33), fired = true)
    assertConsults(p, JS.G(43), fired = true)
  }

  test("JS-G33 — a STATIC method reference is a qualified NAME and needs no conversion; consulted, does not fire") {
    val p = port(
      """import java.util.function.Function;
        |public class A {
        |  static int len(String s) { return s.length(); }
        |  Function<String, Integer> f() { return A::len; }
        |}""".stripMargin)
    assertConsults(p, JS.G(33))
    assertConsults(p, JS.G(43), fired = true)
  }

  // -- the call rows: JS-G18, JS-G32, JS-G37…JS-G40, JS-G42 --------------------------------------

  test("JS-G37 — an in-program `T...` is an `Array[T]`, so the CALL materialises the array java built") {
    val p = port(
      """public class A {
        |  static void run(String... xs) {}
        |  void f() { run("a", "b"); }
        |}""".stripMargin)
    assertConsults(p, JS.G(37), fired = true)
    assertConsults(p, JS.G(38))
    assertEmits(p, "scala.Array[java.lang.String](\"a\", \"b\")")
  }

  test("JS-G38 — a slot ALREADY holding the array must not be re-packed; the rows swap over") {
    val p = port(
      """public class A {
        |  static void run(String... xs) {}
        |  void f(String[] xs) { run(xs); }
        |}""".stripMargin)
    assertConsults(p, JS.G(38), fired = true)
    assertConsults(p, JS.G(37))
    assertNotEmits(p, "scala.Array[java.lang.String](xs)")
  }

  test("JS-G42 — a GENERIC vararg component is not nameable at the call site, so it has four sources") {
    val p = port(
      """public class A {
        |  static <T> void all(T... xs) {}
        |  void f() { all("a", "b"); }
        |}""".stripMargin)
    assertConsults(p, JS.G(42), fired = true)
  }

  test("JS-G42 — a CONCRETE vararg component needs no inference; consulted, does not fire") {
    val p = port(
      """public class A {
        |  static void all(String... xs) {}
        |  void f() { all("a"); }
        |}""".stripMargin)
    assertConsults(p, JS.G(42))
  }

  test("JS-G37…JS-G40 — a call to a NON-variadic callee is consulted for all four and fires for none") {
    val p = port(
      """public class A {
        |  static void one(String x) {}
        |  void f() { one("a"); }
        |}""".stripMargin)
    List(JS.G(37), JS.G(38), JS.G(39), JS.G(40)).foreach(assertConsults(p, _))
  }

  test("JS-G39 — an EXTERNAL callee's `T...` is a CLASS FILE's, which scalac reads as a REPEATED parameter") {
    // The emitter half is answered at the enclosing `Apply` and NOT at a `Rendered("Repeated")`:
    // `argTerms` flattens a `Tree.Repeated` in an argument position before the dispatch sees it, so
    // an attachment at that kind could never be consulted and could never be reported as a hole
    // either — coverage that cannot fail. This assertion is what holds the two halves together.
    val p = port(
      """public class A { String f() { return String.format("%s", "a"); } }""".stripMargin)
    assertConsults(p, JS.G(39), fired = true)
    assertConsults(p, JS.G(40))
    assertConsults(p, JS.G(18), fired = true)
  }

  test("JS-G40 — …and an ARRAY forwarded through that slot is the COMPOSITION, which is java's own idiom") {
    // `String.format(fmt, args)`: a bare array conforms as ONE element wherever the repeated element
    // is `Object`, so `%s` prints the array and the second `%s` throws — `CLAUDE.md` §4.4, no error
    // and no moved count. The spread is what makes it java's arity again.
    val p = port(
      """public class A { String f(String fmt, Object[] args) { return String.format(fmt, args); } }""".stripMargin)
    assertConsults(p, JS.G(40), fired = true)
    assertConsults(p, JS.G(39), fired = true)
    assertConsults(p, JS.G(38), fired = true)
    assertEmits(p, "*")
  }

  test("JS-G18 — a call to an IN-PROGRAM callee is consulted and does not fire") {
    // The row is about the program's EDGE: under `noClasspath` an executable REFERENCE erases its
    // generic formals and a DECLARATION does not, so the two readings only meet at a callee whose
    // declaration is a bytecode shadow. Ours are not.
    val p = port(
      """public class A {
        |  static String id(String s) { return s; }
        |  Object f() { return id("a"); }
        |}""".stripMargin)
    assertConsults(p, JS.G(18))
  }

  test("JS-G22 — every invocation is asked whether its receiver was read through an ERASED VIEW") {
    // Consulted, and the FIRING shape is not one a classpath-free snippet reliably produces:
    // `erasedReceiverView` needs a raw or wildcard receiver whose callee's formals depend on the
    // receiver's own variables, which is a resolution the fixture frontend does not always have.
    // The repair itself is covered by `ErasedReceiverResultSpec` on real sources, and what this
    // asserts is that the branch is LIVE at every call — which is the half a suite can see.
    val p = port(
      """import java.util.List;
        |public class A { Object f(List<?> l) { return l.get(0); } }""".stripMargin)
    assertConsults(p, JS.G(22))
  }

  test("JS-G32 — a formal written in the CALLEE's own type variables does not resolve at the caller") {
    val p = port(
      """import java.util.List;
        |public class A {
        |  static <T> T pick(List<T> l) { return l.get(0); }
        |  Object f(List<String> l) { return pick(l); }
        |}""".stripMargin)
    assertConsults(p, JS.G(32), fired = true)
    assertConsults(p, JS.G(29), fired = true)
  }

  test("JS-G29 / JS-G30 / JS-G32 — a call to a NON-generic callee is consulted for all three and fires for none") {
    val p = port(
      """public class A {
        |  static String id(String s) { return s; }
        |  Object f() { return id("a"); }
        |}""".stripMargin)
    List(JS.G(29), JS.G(30), JS.G(32)).foreach(assertConsults(p, _))
  }

  test("JS-G30 — a type parameter NO formal mentions is constrained only by its bound: java infers the BOUND") {
    // Scala infers `Nothing` for the same variable, and then selects a member from it. The fork from
    // JS-G29 is exactly "does a formal mention it", which is what both consults read.
    val p = port(
      """public class A {
        |  static <T extends java.lang.Number> T make() { return null; }
        |  Object f() { return make(); }
        |}""".stripMargin)
    assertConsults(p, JS.G(30), fired = true)
    assertConsults(p, JS.G(29))
  }

  // -- casts and declarations: JS-G34, JS-G35, JS-G21 --------------------------------------------

  test("JS-G34 — a java INTERSECTION in a cast becomes scala's `&`") {
    val p = port(
      """import java.io.Serializable;
        |public class A { Object f(Object o) { return (Serializable & Cloneable) o; } }""".stripMargin)
    assertConsults(p, JS.G(34), fired = true)
    assertEmits(p, "java.io.Serializable & java.lang.Cloneable")
  }

  test("JS-G34 — an ordinary cast is consulted and does not fire") {
    val p = port("public class A { Object f(Object o) { return (String) o; } }")
    assertConsults(p, JS.G(34))
    assertEmits(p, "asInstanceOf[java.lang.String]")
  }

  test("JS-G35 — scala CHECKS an F-bound at every use where javac does not, on a CLASS") {
    val p = port("public class A { static class Node<N extends Node<N>> { } }")
    assertConsults(p, JS.G(35), fired = true)
  }

  test("JS-G35 — …and on a METHOD, which is the other declaration kind the row attaches to") {
    val p = port(
      """public class A {
        |  static <N extends Comparable<N>> N max(N a, N b) { return a; }
        |}""".stripMargin)
    assertConsults(p, JS.G(35), fired = true)
  }

  test("JS-G35 — a plain type parameter is consulted and does not fire") {
    val p = port("public class A { static class Box<T> { } }")
    assertConsults(p, JS.G(35))
  }

  test("JS-G21 — `instanceof` is asked the reifiability question at every occurrence") {
    // ALWAYS fires and that is the honest answer: java restricts the operand to a REIFIABLE type
    // (JLS 4.7) and `isInstanceOf` tests the erased runtime class exactly as java's does, so every
    // `instanceof` is the population the question is asked of. What keeps the row `Partial` is the
    // OTHER half — SE16's pattern binding, which has no representation at all.
    val p = port("public class A { boolean f(Object o) { return o instanceof String; } }")
    assertConsults(p, JS.G(21), fired = true)
    assertEmits(p, "isInstanceOf[java.lang.String]")
    assert(Differences.byId(JS.G(21)).status.isInstanceOf[Status.Partial],
      "the SE16 pattern binding is still absent — flip this assertion with the row")
  }

  // -- THE FOURTH SURFACE — the rows decided while a TYPE is lowered or rendered -----------------
  //
  // `SpoonTir.tpe` and `TirEmitter.tpe` (`DESIGN.md` §2.8). Each of these was `Unmechanised` until
  // the surface existed, and each is asserted BOTH ways for the reason the file's header states:
  // the wrapper detects an ABSENT consult and cannot detect a wrong one, so a suite that only ever
  // showed the difference firing would pass against a predicate wired to a constant.

  test("JS-G01 — a wildcard with a WRITTEN bound crosses into scala's grammar, at both ends") {
    // The frontend chooses the IMAGE (a `TypeBounds`) and the emitter chooses the TEXT (`? <: X`),
    // and the row attaches at both because they are two decisions. `? extends Object` is NOT this
    // row — it is a bare `?`, the one form both languages spell the same way — which is the negative
    // below.
    val p = port("public class A { void f(java.util.List<? extends Number> l) { } }")
    assertConsults(p, JS.G(1), fired = true)
    assertEmits(p, "java.util.List[? <: java.lang.Number]")
  }

  test("JS-G01 — a BARE wildcard is consulted at both ends and fires at neither") {
    val p = port("public class A { void f(java.util.List<?> l) { } }")
    assertConsults(p, JS.G(1))
    assertEmits(p, "java.util.List[?]")
    assertNotEmits(p, "? <:")
  }

  test("JS-G03 — `? super Object` is the one wildcard that is not a family") {
    // Java has no supertype of `Object`, so the lower bound admits exactly `Object` and naming it
    // loses nothing. Rendered `[?]` — which is what `? extends Object` also becomes — the two
    // collapse to one type and a call java accepts by capture conversion unifies to `Nothing`.
    val p = port("public class A { void f(java.util.List<? super Object> l) { } }")
    assertConsults(p, JS.G(3), fired = true)
    assertEmits(p, "java.util.List[java.lang.Object]")
  }

  test("JS-G03 — `? super X` for any OTHER X really is a family; consulted, does not fire") {
    val p = port("public class A { void f(java.util.List<? super String> l) { } }")
    assertConsults(p, JS.G(3))
    assertEmits(p, "java.util.List[? >: java.lang.String]")
  }

  test("JS-G07 — a RAW use erases the REFERENCE's generics, and the arguments are re-supplied") {
    val p = port("public class A { java.util.List f() { return null; } }")
    assertConsults(p, JS.G(7), fired = true)
    assertEmits(p, "java.util.List[?]")
  }

  test("JS-G07 — a PARAMETERISED use is consulted and does not fire") {
    val p = port("public class A { java.util.List<String> f() { return null; } }")
    assertConsults(p, JS.G(7))
    assertEmits(p, "java.util.List[java.lang.String]")
  }

  test("JS-G07 — a NON-generic reference is consulted and does not fire, and so is a primitive") {
    // The primitive is the case the consult would have missed: `int` reaches the SAME Spoon kind as
    // `String` does, through a different arm of `SpoonTir.tpe`, so a rule stated in the general arm
    // alone is a hole at every primitive in the program. It is stated once (`rawUseConsults`) and
    // called from both.
    val p = port("public class A { int f(String s) { return 0; } }")
    assertConsults(p, JS.G(7))
    assertEmits(p, "scala.Int")
  }

  test("JS-G08 — the SAME raw java type renders two ways, and which one depends on the frame") {
    // `Entries` is nested in `Box<T>`, so a raw use inside a non-static member fills from the
    // enclosing instantiation's own name, while a STATIC frame — where the class's parameters are
    // not in scope at all — must fall back to a wildcard. One java type, two renderings, and both
    // are right (`ENGINE-LIMITS.md` G3, G20).
    val p = port(
      """public class A {
        |  static class Box<T> {
        |    static class Entries<E> { }
        |    Entries here() { return null; }
        |    static Entries there() { return null; }
        |  }
        |}""".stripMargin)
    assertConsults(p, JS.G(8), fired = true)
  }

  test("JS-G08 — a raw use with NO enclosing instantiation to read is consulted and does not fire") {
    val p = port("public class A { java.util.List f() { return null; } }")
    assertConsults(p, JS.G(8))
  }

  test("JS-G05 — a wildcard is ILLEGAL in an `extends` clause, so it takes the declared bound") {
    // `Box`'s parameter is bounded, so the parent's `?` — which our own raw fill produced — becomes
    // that bound rather than `AnyRef`. The plain `AnyRef` fill is what got this wrong before
    // `declBounds` was consulted: it produced a parent that failed its own bounds.
    val p = port(
      """public class A {
        |  static class Box<T extends Number> { }
        |  static class Sub extends Box { }
        |}""".stripMargin)
    assertConsults(p, JS.G(5), fired = true)
    assertEmits(p, "extends A.Box[java.lang.Number]")
  }

  test("JS-G05 — a class with no wildcard in its parents is consulted and does not fire") {
    val p = port("public class A { static class Box<T> { } static class Sub extends Box<String> { } }")
    assertConsults(p, JS.G(5))
  }

  test("JS-G11 — an F-BOUNDED slot cannot be eliminated at all, and the refusal is CONSULTED") {
    // No finite type satisfies `N <: Node[N]` except a real subclass, and every unrolling fails the
    // same bound because `Node` is invariant. Java carries the bound and does not check it at an
    // erased use; scala checks. The WILDCARD asserts only that SOME type satisfies it, which is
    // exactly the erased claim — so the slot stays `?` and the row stays a refusal
    // (`ENGINE-LIMITS.md` G8).
    val p = port(
      """public class A {
        |  static class Node<N extends Node<N>> { }
        |  static class Holder extends Node { }
        |}""".stripMargin)
    assertConsults(p, JS.G(11), fired = true)
    assert(Differences.byId(JS.G(11)).status.isInstanceOf[Status.Refused],
      "JS-G11 is no longer a refusal — flip this test with it")
    assertEmits(p, "extends A.Node[?]")
  }

  test("JS-G11 — a NON-F-bounded wildcard parent is eliminated, so the refusal does not fire") {
    val p = port(
      """public class A {
        |  static class Box<T extends Number> { }
        |  static class Sub extends Box { }
        |}""".stripMargin)
    assertConsults(p, JS.G(11))
  }

  test("JS-G06 — a de-wildcarded raw PARENT and its overrides come from ONE answer, and it is CITED") {
    // The parent could not keep its wildcard (`extends Cfg[?]` is illegal), so it was eliminated to
    // `Cfg[Number]`; the override's own parameter was independently rendered `Box[?]` by the raw
    // fill. Two renderings of one raw type in one class, and the override implemented neither.
    // `rawParentAlignment` applies the parent's OWN substitution to the inherited signature, so
    // agreement is by construction — and it is a whole-program pass, so what it owes is a CITATION
    // and not an obligation (`DESIGN.md` §2.8).
    val p = port(
      """public class A {
        |  static class Box<T> { }
        |  interface Cfg<T extends Number> { void save(Box<T> b); }
        |  static class Impl implements Cfg { public void save(Box b) { } }
        |}""".stripMargin)
    assertCites(p, JS.G(6), "save")
  }

  test("JS-G06 — a class declaring BOTH of an inherited overload set aligns each on ITS OWN parent") {
    // §4.55's *a `find` IS that map*, at the derivation JS-G06 names. The parent member used to be
    // picked by `(name, param counts)` taking the FIRST hit up the chain, so a class declaring both
    // of an interface's overloads — ordinary java — had the SECOND one aligned onto the FIRST one's
    // formal, and an `asInstanceOf` the source never wrote inserted at every call to make it fit.
    //
    // The head-constructor guard cannot separate this pair, which is what says the KEY was wrong
    // rather than the guard one case short: BOTH formals are `scala.Array`, and they differ only
    // INSIDE the type argument. `OverrideGraph.overridden` is keyed by name and DESCRIPTOR, so the
    // wildcarded overload aligns onto the wildcarded parent, the `!hasWildcardArg` arm declines, and
    // the port emits what java wrote.
    val p = port(
      """public class A {
        |  static class Cell<T> { }
        |  interface Layout {
        |    void conv(String... a);
        |    void conv(Cell<?>... c);
        |  }
        |  static class Impl implements Layout {
        |    public void conv(String... a) { }
        |    public void conv(Cell<?>... c) { }
        |    void go(Cell<?>[] cs) { conv(cs); }
        |  }
        |}""".stripMargin)
    // THE OVERRIDE, not the interface's own member. The interface's rendering is the CONTROL that
    // identified the defect (`PROGRESS.md` §10.9.7 family 5) and it was always right, so an
    // assertion that only asks whether the wildcard is writable here proves nothing at all —
    // mis-aligned, this emitted `override def conv(c: scala.Array[java.lang.String])` beside a
    // trait that still read `A.Cell[?]`. The parameter NAME is what separates the pair.
    assertEmits(p, "override def conv(c: scala.Array[A.Cell[?]])")
    // …and the OTHER reader of the same map. `alignedArgs` casts every argument reaching a
    // re-rendered parameter, so the mis-alignment put an `asInstanceOf` the java never wrote at the
    // call in `go` — which is how the defect reached emitted code that could not compile.
    assertNotEmits(p, "asInstanceOf[scala.Array[")
  }

  test("JS-G12 — every type VARIABLE is asked whether it has a binder here, and this one does") {
    val p = port("public class A<T> { java.util.List<T> xs; }")
    assertConsults(p, JS.G(12))
    assertEmits(p, "java.util.List[T]")
  }

  test("JS-G12 — …and the marker NEVER reaches the output, which is the whole obligation") {
    // WHAT THIS TEST CANNOT DO, stated rather than faked: the frontend's mint is unreachable from a
    // single in-memory unit. `resolveTypeParam` searches every enclosing frame BY NAME, so a
    // nested — even `static` nested — class still resolves the outer `T`, and the shape that does
    // reach the mint needs a receiver instantiation read under `atDeclScope`, where an EXECUTABLE
    // frame is skipped. Eight fixtures were probed across that space and every one resolved. So the
    // FIRE is a corpus measurement (`catalog.tsv`'s `JS-G12` row) and what an edge-case suite can
    // hold is the standing obligation itself: whatever the frontend minted, `?E` is neither a type
    // nor a token sequence scala can lex, and one occurrence took out the statement around it
    // (`ENGINE-LIMITS.md` G2). `TirEmitterSpec` holds the same line from the other side, on a
    // hand-built marker, which is the only way to construct one deliberately.
    val p = port(
      """public class A<T> {
        |  static class Inner { java.util.List<T> xs; }
        |  <U> java.util.List<U> g(java.util.List<U> u) { return u; }
        |}""".stripMargin)
    assertConsults(p, JS.G(12))
    assertNotEmits(p, balticporter.tir.Symbol.UnresolvedTypeVarPrefix)
  }

  // -- a REFUSAL is a decision the lane can count: JS-G10 -----------------------------------------

  test("JS-G10 — a RAW anonymous class WITH a body has no faithful image, and the refusal is CONSULTED") {
    // Without a body scala infers the argument from the expected type; with one the anonymous
    // class's type is fixed, so a raw use gives `Box[Nothing]` and naming the argument does not help
    // either — the body is written against the erasure. `ENGINE-LIMITS.md` G10 left it refused and
    // reported rather than approximated, and this is what makes the refusal countable.
    val p = port(
      """public class A {
        |  interface Box<T> { T get(); }
        |  Object f() { return new Box() { public Object get() { return null; } }; }
        |}""".stripMargin)
    assertConsults(p, JS.G(10), fired = true)
    assert(Differences.byId(JS.G(10)).status.isInstanceOf[Status.Refused],
      "JS-G10 is no longer a refusal — flip this test with it")
  }

  test("JS-G10 — a PARAMETERISED anonymous class is the ordinary case; consulted, does not fire") {
    val p = port(
      """public class A {
        |  interface Box<T> { T get(); }
        |  Object f() { return new Box<String>() { public String get() { return "x"; } }; }
        |}""".stripMargin)
    assertConsults(p, JS.G(10))
  }

  // -- the OPEN rows: rule (ii) makes CONSULTING one a finding ------------------------------------

  test("JS-G16 / JS-G36 — an OPEN row that ATTACHES is the WORK LIST, and is never consulted") {
    // Both have a kind that genuinely owns them — `Array[T]` construction is decided where a
    // `Tree.NewArray` renders, and an override's type-parameter bounds where its `Tree.DefDef` does
    // — so each is an `undischarged` hole on every port that emits one, which is what a work list
    // is. `CatalogLog.knownHole` is why that is not fatal in the testkit.
    val p = port("public class A { int[] f(int n) { return new int[n]; } }")
    List(JS.G(16), JS.G(36)).foreach { id =>
      assertNotConsults(p, id)
      assert(Differences.byId(id).status.isOpen, s"$id is no longer Open — flip this test with it")
    }
  }

  test("JS-G02 — an OPEN row at the TYPE surface is the work list too, and is never consulted") {
    // The third row in this file's `Open` family and the one the fourth surface added. Capture
    // conversion relates two USES of one wildcard in a single expression; scala captures per use,
    // and no rewrite is synthesised — so every `CtWildcardReference` is a hole, which is what a work
    // list is. `CatalogLog.knownHole` is why the testkit's fatal mode does not raise on it.
    val p = port("public class A { int f(java.util.List<?> l) { return l.size(); } }")
    assertNotConsults(p, JS.G(2))
    assert(Differences.byId(JS.G(2)).status.isOpen, "JS-G02 is no longer Open — flip this test with it")
  }

  test("JS-G20 — a row with NO surface at all is counted, not claimed") {
    val p = port("public class A { int x = 1; }")
    assertNotConsults(p, JS.G(20))
    assert(Differences.leaves(Differences.byId(JS.G(20)).attaches).exists(_.isInstanceOf[Attaches.Unmechanised]),
      "JS-G20 gained a surface — move it out of this test and give it one of its own")
  }

  // -- JS-G41: heap pollution, which is COUNTED because there is nothing to translate -------------
  //
  // The row's decision at a declaration is to CARRY java's unsoundness — the port reproduces it
  // exactly, and a phase that "fixed" it would emit a different program. So the consult is reached
  // at every method and fires where the declaration is one javac had an opinion about; the number
  // lives in `HeapPollutionCheck` beside it, through the same predicate.

  test("JS-G41 — a vararg whose component is a TYPE VARIABLE fires, and it is UNACKNOWLEDGED") {
    val p = port("public class A { @SafeVarargs final <T> void f(T... xs) { } void g(String s) { } }")
    assertConsults(p, JS.G(41), fired = true)
    val fs = HeapPollutionCheck.check(p.after, p.after.units)
    assertEquals(clue(fs).map(_.issue), List(HeapPollutionCheck.Issue.Acknowledged))
    assertEquals(fs.head.param, "xs")
  }

  test("JS-G41 — javac's warning has no scala image, so an UNANNOTATED one is the row's other half") {
    // The population that matters and the one a census keyed on the annotation would miss: java
    // WARNED at this declaration and the author left it, so nothing in the emitted file mentions it
    // at all. `Class<? extends Number>` is JLS 4.7's second shape — a parameterised type whose
    // argument is a BOUNDED wildcard — and it is two characters from the reifiable `Class<?>`.
    val p = port("public class A { void f(java.lang.Class<? extends Number>... xs) { } }")
    assertConsults(p, JS.G(41), fired = true)
    val fs = HeapPollutionCheck.check(p.after, p.after.units)
    assertEquals(clue(fs).map(_.issue), List(HeapPollutionCheck.Issue.Unacknowledged))
  }

  test("JS-G41 — a REIFIABLE vararg component is consulted and does not fire") {
    // The negative, and it is what keeps the counter from being a census of every vararg: `String…`,
    // `Class<?>…` and `String[]…` are all reifiable (JLS 4.7), javac warns at none of them, and
    // nothing is carried. The ARRAY is the one that needs its own clause and would otherwise be
    // reported: an array is spelled as an APPLICATION here, so the parameterised-type rule sees
    // `scala.Array[java.lang.String]` and a non-wildcard argument. A lane counting a risk that is
    // not there is worse than one counting none.
    val p = port(
      """public class A {
        |  void f(String... xs) { }
        |  void g(java.lang.Class<?>... ys) { }
        |  void h(String[]... zs) { }
        |}""".stripMargin)
    assertConsults(p, JS.G(41))
    assertNoFindings(HeapPollutionCheck.check(p.after, p.after.units).map(_.report))
  }

  test("JS-G41 — …but an array OF a type variable is not reifiable, so the clause is not a blanket") {
    // The other side of the same clause, because "arrays are reifiable" is exactly the shape of
    // over-approximation that turns a counter off: JLS 4.7 makes an array reifiable IFF its
    // component is, and `T[]…` has a type variable at the bottom.
    val p = port("public class A { <T> void f(T[]... xs) { } }")
    assertConsults(p, JS.G(41), fired = true)
    assertEquals(clue(HeapPollutionCheck.check(p.after, p.after.units)).size, 1)
  }

  test("JS-G41 — the acknowledgement is EMITTED, onto a method that is not even variadic in scala") {
    // Why the row is `Partial` and not `Handled`: `SpoonTir.annotationsOf` has no ignore list, so
    // the marker crosses — and `JS-G37` has already turned the vararg into a plain `Array`
    // parameter, so what a reader of the emitted file sees is java's acknowledgement of a risk on a
    // declaration where scalac derives nothing from it.
    val p = port("public class A { @SafeVarargs final <T> void f(T... xs) { } }")
    assertEmits(p, "@java.lang.SafeVarargs")
    assertEmits(p, "xs: scala.Array[T]")
  }

  // -- the partition, asserted rather than left to a reader ---------------------------------------

  test("every JS-G row is wired, declared unmechanised, or owes nothing — and the residue is NAMED") {
    val byKind = Differences.generics.groupBy(d => Differences.leaves(d.attaches) match
      case ls if ls.exists(_.isInstanceOf[Attaches.Unmechanised]) => "unmechanised"
      case ls if ls.exists(_.isInstanceOf[Attaches.LoweredType])  => "lowered-type"
      case ls if ls.exists(_.isInstanceOf[Attaches.RenderedType]) => "rendered-type"
      case ls if ls.exists(_.isInstanceOf[Attaches.Rendered])     => "rendered"
      case ls if ls.exists(_.isInstanceOf[Attaches.Lowered])      => "lowered"
      case ls if ls.exists(_.isInstanceOf[Attaches.Cited])        => "cited"
      case _                                                      => "none")
    assertEquals(byKind.values.map(_.size).sum, Differences.generics.size)
    // THE CHUNK'S OWN BAR, in the form that can fail. Area G opened with 38 of its 40 rows on
    // `Unmechanised` — the largest such claim in the registry — chunk 12 took it to eleven, and the
    // fourth surface takes it to TWO, and `JS-G41`'s counter to ONE. The audit question is
    // unchanged: were the rows instrumented, or renamed to keep a lane green. What is left is
    // `JS-G20`'s per-phase discipline — a fact about every retyping phase rather than one
    // mechanism, which `collection-retarget` measures.
    assertEquals(byKind.getOrElse("unmechanised", Nil).map(_.id).toSet,
      Set(JS.G(20)),
      "a JS-G row that is not JS-G20's per-phase discipline still says nothing is measuring it")
    assert(byKind.getOrElse("rendered", Nil).nonEmpty, "no JS-G row is wired to the RENDERING dispatch")
    assert(byKind.getOrElse("lowered", Nil).nonEmpty, "no JS-G row is wired to the LOWERING dispatch")
    // …and the FOURTH surface, BOTH halves — asked of the LEAVES and not of the bucket, because the
    // bucket is first-match and area G's two type rows attach at both ends (`Attaches.Both`), so a
    // bucket test would report the emitter half as absent while `JS-G01` and `JS-G12` are wired to
    // it. That is `JS-G39`'s lesson in a spec: a question about a LEAF may not be asked of a row.
    def hasLeaf(f: Attaches => Boolean) = Differences.generics.exists(d => Differences.leaves(d.attaches).exists(f))
    assert(hasLeaf(_.isInstanceOf[Attaches.LoweredType]), "no JS-G row is wired to the TYPE-REFERENCE dispatch")
    assert(hasLeaf(_.isInstanceOf[Attaches.RenderedType]), "no JS-G row is wired to the emitter's TYPE dispatch")
    // The area's TWO citations. `JS-G48` is asserted here rather than tested above because a
    // citation needs `CollectionsTransform`, whose reified rewrites
    // `balticporter.corpus.CollectionsReifiedSpec` exercises; `JS-G06` HAS a test above, and is
    // listed anyway so that the row losing its surface fails here too. Without this line either
    // could lose it and nothing in this file would say so.
    assertEquals(byKind.getOrElse("cited", Nil).map(_.id), List(JS.G(6), JS.G(48)))
    // …and a row claiming NO obligation must not be one the registry calls Open.
    assertEquals(byKind.getOrElse("none", Nil).filter(_.status.isOpen).map(_.id), Nil,
      "an Open row claiming NoObligation is a gap no lane can see")
  }
