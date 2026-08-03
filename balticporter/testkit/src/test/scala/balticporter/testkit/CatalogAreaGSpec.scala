package balticporter.testkit

import balticporter.catalog.JS

/** THE `JS-G` EDGE-CASE SUITE — the generics rows the engine wires, at the shape each row is about.
  *
  * Same contract as [[CatalogAreaESpec]]: each test asserts BOTH that the branch was live
  * (`assertConsults`) and that the emitted Scala means what java meant, because the obligation
  * wrapper detects an ABSENT consult and cannot detect a WRONG one.
  *
  * Only `JS-G31` is here today, and that is the honest state rather than a stub: it is the one
  * `JS-G` row with a discharge surface, every other area-G row carrying `Unmechanised` and being
  * counted as such. A suite that invented tests for rows nothing consults would report coverage the
  * mechanism does not have.
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
