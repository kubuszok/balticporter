package balticporter.testkit

import balticporter.catalog.{Attaches, Differences, JS, Status}
import balticporter.tir.Decision

/** THE `JS-C` EDGE-CASE SUITE — one test per class/member/initialisation row the engine wires, at
  * the shape the row is about.
  *
  * Same contract as [[CatalogAreaESpec]] and [[CatalogAreaSSpec]]: each test asserts BOTH that the
  * branch was live (`assertConsults`) and that the emitted Scala means what java meant, because the
  * obligation wrapper detects an ABSENT consult and cannot detect a WRONG one.
  *
  * AREA C IS THE FIRST AREA WHOSE ROWS ARE ABOUT DECLARATIONS, and that is what this suite is really
  * exercising. `JS-E`'s rows discharge at the frontend's expression dispatch and `JS-S`'s mostly at
  * the emitter's statement dispatch; these are decided while rendering a `Tree.ClassDef`, a
  * `Tree.DefDef` or a `Tree.ValDef` — which are Statements, so `Rendering.of` already reaches them —
  * plus two whole-program renaming passes that CITE rather than consult, because a pass that walks
  * the program rather than a node kind has no dispatch to be wrapped at.
  *
  * The proposal predicted "most JS-C rows discharge in PHASES". They do not: chunk 0's re-derivation
  * put a `SpoonTir` or `TirEmitter` symbol against almost every one, and only `resolveFieldShadowing`
  * (JS-C04) and `resolveMemberClashes` (JS-C46) are whole-program passes. The consequence is that
  * area C needed no new surface at all — one hole had to be closed first, and it is stated where it
  * was closed: `TirEmitter.emitUnit` reached `classDef` directly, so a TOP-LEVEL type never entered
  * the rendering dispatch and every `Rendered("ClassDef")` row would have been owed only by nested
  * ones.
  *
  * A row that is `NoObligation` gets no test and owes none; a row the registry calls `Open` or
  * `Absent` gets the OPPOSITE assertion, because rule (ii) makes consulting one a finding. The last
  * tests assert those partitions rather than leaving them to a reader.
  */
class CatalogAreaCSpec extends PortSuite:

  /** two files in a real package — the only way to test the access levels at all, since a java
    * package-private declaration in the DEFAULT package has no spellable Scala qualifier and
    * `Visibility` turns it into a recorded widening instead. */
  private def inPkg(body: String): Ported =
    // TWO units, and the second is not decoration: a SINGLE in-memory unit reaches Spoon's
    // `SourcePositionImpl.getColumn` with a null buffer and the frontend NPEs while reading an
    // origin. Every other multi-unit fixture in this repository passes two, which is why nothing
    // had met it; a package boundary needs `portAll` and `portAll` needs the pair.
    portAll(List(
      "A.java"     -> s"package p;\n$body\n",
      "Other.java" -> "package p;\npublic class Other { }\n"))

  // -- JS-C01 / JS-C02: a java `static` is INHERITED and a scala companion inherits nothing --------

  test("JS-C01 — a static called through a SUBCLASS's name is re-pointed at the declarer") {
    val p = port(
      """public class A {
        |  static class Base { static int m() { return 1; } }
        |  static class Sub extends Base {}
        |  int f() { return Sub.m(); }
        |}""".stripMargin)
    assertConsults(p, JS.C(1), fired = true)
    // …and JS-C02 at the SAME call, because `Sub` is not the declarer. The two rows are the same
    // BFS read at its two edges, and a suite that asserted only the first would not notice the
    // interface half going quiet.
    assertConsults(p, JS.C(2), fired = true)
    // `A.Sub.m()` would not resolve: a companion object inherits nothing.
    assertEmits(p, "A.Base.m()")
  }

  test("JS-C02 — …and the same fact through `implements`, which is the row the interface edge owns") {
    val p = port(
      """public class A {
        |  interface K { int X = 7; }
        |  static class C implements K {}
        |  int f() { return C.X; }
        |}""".stripMargin)
    assertConsults(p, JS.C(2), fired = true)
    assertConsults(p, JS.C(5), fired = true)
  }

  test("JS-C01 / JS-C02 — an INSTANCE call is consulted and neither fires") {
    val p = port(
      """public class A {
        |  static class B { int m() { return 1; } }
        |  int f(B b) { return b.m(); }
        |}""".stripMargin)
    assertConsults(p, JS.C(1))
    assertConsults(p, JS.C(2))
  }

  test("JS-C02 — a static read through its OWN declarer is consulted and does not fire") {
    // The edge a consult count alone cannot see: the branch is live at every static read, and it is
    // INHERITANCE that makes the difference apply. `Base.m()` names its own declarer.
    val p = port(
      """public class A {
        |  static class Base { static int m() { return 1; } }
        |  int f() { return Base.m(); }
        |}""".stripMargin)
    assertConsults(p, JS.C(1), fired = true)
    assertConsults(p, JS.C(2))
  }

  // -- JS-C03 / JS-C34: the companion re-export, and what it must not deliver twice ----------------

  test("JS-C34 — an implementor re-exports the interface's statics, which java inherited for free") {
    val p = port(
      """public class A {
        |  interface K { int X = 7; }
        |  static class C implements K {}
        |}""".stripMargin)
    assertConsults(p, JS.C(34), fired = true)
    assertEmits(p, "export ")
  }

  test("JS-C03 — a class that REDECLARES an inherited static name excludes its own from the export") {
    val p = port(
      """public class A {
        |  interface K { int X = 7; }
        |  static class C implements K { static int X = 9; }
        |}""".stripMargin)
    assertConsults(p, JS.C(3), fired = true)
    assertEmitsMatch(p, "(?s).*export .*X => _.*")
  }

  test("JS-C03 / JS-C34 — a class with no inherited statics is consulted and neither fires") {
    val p = port("public class A { static class C { static int X = 9; } }")
    assertConsults(p, JS.C(3))
    assertConsults(p, JS.C(34))
    assertNotEmits(p, "export ")
  }

  // -- JS-C05: a static nested constant reached through a name -------------------------------------

  test("JS-C05 — an INSTANCE field read is consulted and does not fire") {
    val p = port("public class A { static class B { int x; } int f(B b) { return b.x; } }")
    assertConsults(p, JS.C(5))
  }

  // -- JS-C06: `anInstance.staticMethod()` evaluates the receiver and discards it -------------------

  test("JS-C06 — the receiver of a static call through an instance is KEPT, for its side effects") {
    val p = port(
      """public class A {
        |  static class B { static int m() { return 1; } }
        |  B mk() { return new B(); }
        |  int f() { return mk().m(); }
        |}""".stripMargin)
    assertConsults(p, JS.C(6), fired = true)
    // the companion call has no receiver slot, so the expression has to be evaluated beside it
    assertEmitsMatch(p, "(?s).*\\{ *this\\.mk\\(\\).*A\\.B\\.m\\(\\).*")
  }

  test("JS-C06 — a static call through the TYPE has no receiver to keep; consulted, does not fire") {
    val p = port(
      """public class A {
        |  static class B { static int m() { return 1; } }
        |  int f() { return B.m(); }
        |}""".stripMargin)
    assertConsults(p, JS.C(6))
  }

  // -- JS-C07 / JS-C09: class initialisation (JLS 12.4) ---------------------------------------------

  test("JS-C07 — a class bearing a `static { }` is consulted and fires; the object it lands in runs nothing") {
    val p = port(
      """public class A {
        |  static java.util.List<String> reg = new java.util.ArrayList<String>();
        |  static { reg.add("x"); }
        |}""".stripMargin)
    assertConsults(p, JS.C(7), fired = true)
    assertConsults(p, JS.C(9), fired = true)
  }

  test("JS-C07 / JS-C09 — a class with no class-initialiser content is consulted and neither fires") {
    val p = port("public class A { int x; }")
    assertConsults(p, JS.C(7))
    assertConsults(p, JS.C(9))
  }

  test("JS-C09 — ONE static initialiser is not an ORDER; consulted, and it does not fire") {
    // The row is about several step-9 members sharing one textual sequence. A single one has no
    // order to preserve, and a predicate that fired here would be counting classes rather than
    // the difference.
    val p = port("public class A { static java.util.List<String> reg = new java.util.ArrayList<String>(); }")
    assertConsults(p, JS.C(9))
  }

  // -- JS-C13 / JS-C14 / JS-C19 / JS-C20 / JS-C21: instance creation (JLS 12.5) ----------------------

  test("JS-C13 / JS-C14 — a constructor calling `super(args)` promotes it into the `extends` clause") {
    val p = port(
      """public class A {
        |  static class Base { Base(int n) {} }
        |  static class Sub extends Base { Sub() { super(3); } }
        |}""".stripMargin)
    assertConsults(p, JS.C(13), fired = true)
    assertConsults(p, JS.C(14), fired = true)
    assertEmitsMatch(p, "(?s).*class Sub.*extends A\\.Base\\(3\\).*")
  }

  test("JS-C14 — a class whose parent needs no arguments is consulted and does not fire") {
    val p = port("public class A { static class B { B() {} } }")
    assertConsults(p, JS.C(13), fired = true)
    assertConsults(p, JS.C(14))
  }

  test("JS-C19 / JS-C21 — a SECOND constructor becomes `def this(...)` delegating to the primary") {
    val p = port(
      """public class A {
        |  int n;
        |  A(int n) { this.n = n; }
        |  A() { this(1); }
        |}""".stripMargin)
    assertConsults(p, JS.C(19), fired = true)
    assertConsults(p, JS.C(21), fired = true)
    assertEmits(p, "def this()")
  }

  test("JS-C19 / JS-C21 — a class with ONE constructor is consulted and neither fires") {
    val p = port("public class A { int n; A(int n) { this.n = n; } }")
    assertConsults(p, JS.C(19))
    assertConsults(p, JS.C(21))
  }

  test("JS-C20 — a class java gave a DEFAULT constructor is consulted, and the plan is not synthesised") {
    // Java's implicit `A()` needs nothing here: scala's own primary is nilary too, so the difference
    // exists and does not APPLY. `CtorFunnel` synthesises only where no java constructor can be
    // promoted, which is the shape the `fired` half of this row is about.
    val p = port("public class A { int x = 1; }")
    assertConsults(p, JS.C(20))
  }

  // -- JS-C16 / JS-C18: instance initialiser blocks --------------------------------------------------

  test("JS-C16 — an instance initialiser block runs at construction, so it is emitted INLINE") {
    val p = port("public class A { int x; { x = 2; } }")
    assertConsults(p, JS.C(16), fired = true)
    assertEmits(p, "locally {")
  }

  test("JS-C16 — an ordinary method is consulted and does not fire") {
    val p = port("public class A { int x; void m() { x = 2; } }")
    assertConsults(p, JS.C(16))
    assertNotEmits(p, "locally {")
  }

  test("JS-C18 — a field initialiser and an init BLOCK are ONE step-4 sequence, in java's order") {
    // `ENGINE-LIMITS.md` C12's correction, as an assertion: the block runs FIRST because java wrote
    // it first, and a frontend that grouped the two kinds would have run it last.
    val p = port("public class A { { b = 2; } int b = 5; }")
    assertConsults(p, JS.C(18), fired = true)
    assertEmitsMatch(p, "(?s).*locally \\{.*b = 2.*\\}.*var b: scala\\.Int = 5.*")
  }

  test("JS-C18 — a class with fields and NO init block is consulted and does not fire") {
    val p = port("public class A { int b = 5; }")
    assertConsults(p, JS.C(18))
  }

  // -- JS-C30: method-LOCAL named classes --------------------------------------------------------------

  test("JS-C30 — a method-LOCAL named class LOWERS, and takes java's SOURCE name") {
    val p = port(
      """public class A {
        |  public String run() {
        |    class Local { int v() { return 7; } }
        |    return "" + new Local().v();
        |  }
        |}""".stripMargin)
    assertConsults(p, JS.C(30), fired = true)
    // the name, and NOT spoon's `1Local` — java's qualified name carries a binary disambiguator
    // that is the right interning key and is not an identifier (JLS 3.8 forbids a leading digit).
    assertEmits(p, "class Local")
    assertNotEmits(p, "1Local")
    // …and the REFERENCE is the simple name. The owner is the enclosing EXECUTABLE, so `typeSym`
    // must not reach `nestedPath` — a projection through a method names nothing at all.
    assertEmits(p, "new Local()")
  }

  test("JS-C30 — a local class's CONSTRUCTOR is promoted like any other, and its captures close over") {
    val p = port(
      """public class A {
        |  public int run(final int base) {
        |    class Local {
        |      private final int off;
        |      Local(int off) { this.off = off; }
        |      int v() { return off + base; }
        |    }
        |    return new Local(1).v();
        |  }
        |}""".stripMargin)
    assertConsults(p, JS.C(30), fired = true)
    // a class the funnel does not PLAN emits every constructor as a SECONDARY one delegating to a
    // primary nothing synthesised — which is what `cd.body`-only walks produced before
    // `StandardTraversal.allClassDefs`.
    assertEmitsMatch(p, "(?s).*class Local[^\\n]*\\(off[^\\n]*: scala\\.Int\\).*")
    // a capture needs no lowering: javac synthesises a constructor parameter, scala closes over it.
    assertEmits(p, "base")
  }

  test("JS-C30 — a local class takes NO access modifier, which is exactness and not a widening") {
    val p = port(
      """public class A {
        |  public void run() { class Local { } new Local(); }
        |}""".stripMargin)
    // java gives a local class no modifier (JLS 14.3), so its default access reads as
    // package-private — and a modifier on a scala local definition is a syntax error, not merely
    // redundant. The type therefore emits bare; only its own members carry visibility.
    assertEmitsMatch(p, "(?s).*[^\\]]\\bclass Local\\b.*")
    assertNotEmits(p, "private[p] class Local")
  }

  test("JS-C30 — a NESTED class is not a local one: the row is consulted only at the statement dispatch") {
    val p = port("public class A { static class Nested { } }")
    assertNotConsults(p, JS.C(30))
  }

  // -- JS-C17 / JS-C31: anonymous classes --------------------------------------------------------------

  test("JS-C31 — an anonymous class keeps its BODY; dropping it was 156 silent sites") {
    val p = port("public class A { Runnable r = new Runnable() { public void run() { } }; }")
    assertConsults(p, JS.C(31), fired = true)
    assertEmits(p, "def run()")
  }

  test("JS-C17 — DOUBLE-BRACE initialisation is that construct plus an instance initialiser") {
    val p = port(
      """public class A {
        |  java.util.List<String> l = new java.util.ArrayList<String>() {{ add("x"); }};
        |}""".stripMargin)
    assertConsults(p, JS.C(17), fired = true)
    assertConsults(p, JS.C(31), fired = true)
  }

  test("JS-C17 / JS-C31 — a plain `new` is consulted and neither fires") {
    val p = port("public class A { java.util.List<String> l = new java.util.ArrayList<String>(); }")
    assertConsults(p, JS.C(17))
    assertConsults(p, JS.C(31))
  }

  // -- JS-C25: `override` is mandatory in Scala ---------------------------------------------------------

  test("JS-C25 — a method implementing an interface's gets the modifier java does not write") {
    val p = port(
      """public class A {
        |  interface I { void m(); }
        |  static class B implements I { public void m() { } }
        |}""".stripMargin)
    assertConsults(p, JS.C(25), fired = true)
    assertEmits(p, "override def m()")
  }

  test("JS-C25 — a method overriding nothing is consulted and does not fire") {
    val p = port("public class A { void m() { } }")
    assertConsults(p, JS.C(25))
    assertNotEmits(p, "override def m()")
  }

  // -- JS-C33: the interface default-method diamond -------------------------------------------------------

  test("JS-C33 — a class with a superclass AND a mixin is where linearization can disagree with java") {
    val p = port(
      """public class A {
        |  interface I { default int m() { return 1; } }
        |  static class Base { public int m() { return 2; } }
        |  static class C extends Base implements I { }
        |}""".stripMargin)
    assertConsults(p, JS.C(33), fired = true)
    // JLS 9.4.1 rule 1: the CLASS wins. Scala linearises and would take the last mixin.
    assertEmits(p, "super[Base].m")
  }

  test("JS-C33 — a class with a single parent has no diamond; consulted, does not fire") {
    val p = port("public class A { static class Base { } static class C extends Base { } }")
    assertConsults(p, JS.C(33))
    assertNotEmits(p, "super[")
  }

  // -- JS-C36 / JS-C45: what a FIELD declaration decides ----------------------------------------------------

  test("JS-C36 — an interface field is implicitly `public static final`, so it is a companion member") {
    val p = port("public class A { interface K { int X = 7; } }")
    assertConsults(p, JS.C(36), fired = true)
  }

  test("JS-C36 — a CLASS field is not implicitly anything; consulted, and it does not fire") {
    val p = port("public class A { int x = 7; }")
    assertConsults(p, JS.C(36))
  }

  test("JS-C45 — a `final` field carries the JMM guarantee through `val`") {
    val p = port("public class A { final int x; A(int n) { x = n; } }")
    assertConsults(p, JS.C(45), fired = true)
  }

  test("JS-C45 — a mutable field is a `var` and the guarantee does not apply") {
    val p = port("public class A { int x = 7; }")
    assertConsults(p, JS.C(45))
    assertEmits(p, "var x")
  }

  // -- JS-C08: a CONSTANT VARIABLE is inlined by javac ---------------------------------------------------------

  test("JS-C08 — `static final int X = 0` is `inline val`, so reading it triggers no initialiser") {
    val p = port("public class A { static final int X = 3; }")
    assertConsults(p, JS.C(8), fired = true)
    assertEmits(p, "inline val X = 3")
  }

  test("JS-C08 — a non-constant static field is consulted and does not fire") {
    val p = port("public class A { static java.util.List<String> X = new java.util.ArrayList<String>(); }")
    assertConsults(p, JS.C(8))
    assertNotEmits(p, "inline val X")
  }

  // -- JS-C37 / JS-C38 / JS-C39 / JS-C40: java enums ------------------------------------------------------------

  test("JS-C37 / JS-C39 — `name()`, `values()`, `valueOf` and `ordinal()` are SYNTHESISED") {
    val p = port("public enum A { RED, GREEN }")
    assertConsults(p, JS.C(37), fired = true)
    assertConsults(p, JS.C(39), fired = true)
    assertEmits(p, "def values")
    assertEmits(p, "def ordinal")
  }

  test("JS-C38 — a promoted constructor parameter called `name` collides with `Enum.name()`") {
    val p = port(
      """public enum A {
        |  RED("r");
        |  private final String name;
        |  A(String name) { this.name = name; }
        |}""".stripMargin)
    assertConsults(p, JS.C(38), fired = true)
  }

  test("JS-C38 — an enum whose parameters do not include `name` is consulted and does not fire") {
    val p = port(
      """public enum A {
        |  RED(1);
        |  private final int code;
        |  A(int code) { this.code = code; }
        |}""".stripMargin)
    assertConsults(p, JS.C(38))
  }

  test("JS-C40 — an enum constant with a PER-CONSTANT class body is an anonymous subclass") {
    val p = port(
      """public enum A {
        |  RED { public int v() { return 1; } };
        |  public int v() { return 0; }
        |}""".stripMargin)
    assertConsults(p, JS.C(40), fired = true)
  }

  test("JS-C37 / JS-C40 — an ordinary class is consulted for neither enum row") {
    val p = port("public class A { }")
    assertConsults(p, JS.C(37))
    assertConsults(p, JS.C(40))
  }

  // -- JS-C47 / JS-C48 / JS-C49 / JS-C50: java's four access levels ------------------------------------------

  test("JS-C47 / JS-C50 — java's DEFAULT access is package-private and scala's is public") {
    // The failure mode is "emit nothing", which publishes the member — the one row on this list
    // whose defect is a modifier that is NOT there.
    val p = inPkg("public class A { int x = 1; }")
    assertConsults(p, JS.C(47), fired = true)
    assertConsults(p, JS.C(50), fired = true)
    assertEmits(p, "private[p]")
  }

  test("JS-C48 — java's `protected` also grants SAME-PACKAGE access, which scala's does not") {
    val p = inPkg("public class A { protected int x = 1; }")
    assertConsults(p, JS.C(48), fired = true)
    assertEmits(p, "protected[p]")
  }

  test("JS-C49 — a nested type's `private` member is reachable from the whole enclosing top-level class") {
    val p = inPkg(
      """public class A {
        |  static class Inner { private int x = 1; }
        |  int f(Inner i) { return i.x; }
        |}""".stripMargin)
    assertConsults(p, JS.C(49), fired = true)
    assertEmits(p, "private[A]")
  }

  test("JS-C47 / JS-C48 / JS-C49 — a `public` member is consulted for all three and fires for none") {
    val p = inPkg("public class A { public int x = 1; }")
    assertConsults(p, JS.C(47))
    assertConsults(p, JS.C(48))
    assertConsults(p, JS.C(49))
    assertConsults(p, JS.C(50))
  }

  // -- JS-C04 / JS-C46: the two whole-program renaming passes, which CITE rather than consult ---------------

  test("JS-C04 — a subclass field SHADOWING a superclass field is RENAMED; two cells, not one") {
    // SHORTLIST ROW 4, and the whole of it: "confirm whether the collision detector fires on
    // same-name/SAME-TYPE inherited fields". It does, and the reason is that the trigger is NAME
    // membership in the inherited instance members rather than a type comparison — so this fixture,
    // where both fields are `int`, is the case with no scala compile error to prompt it and no
    // check that could have seen it.
    val p = port(
      """public class A {
        |  static class Base { int v = 1; }
        |  static class Sub extends Base { int v = 2; }
        |}""".stripMargin)
    assertCites(p, JS.C(4), "Sub")
    assertEmitsMatch(p, "(?s).*var v\\$[a-z]+: scala\\.Int = 2.*")
  }

  test("JS-C04 — a subclass with no shadowing field cites nothing; a citation is a DECISION taken") {
    val p = port(
      """public class A {
        |  static class Base { int v = 1; }
        |  static class Sub extends Base { int w = 2; }
        |}""".stripMargin)
    assertEquals(p.catalog.citedAt(JS.C(4)), Nil)
    val _ = p.out
    assertEquals(p.catalog.citedAt(JS.C(4)), Nil)
  }

  test("JS-C46 — java's TWO namespaces let a field `x` sit beside a method `x()`; scala has one") {
    val p = port("public class A { int x; int x() { return x; } }")
    assertCites(p, JS.C(46), "A")
    assertEmitsMatch(p, "(?s).*x\\$(field|method).*")
  }

  test("JS-C46 — a class with no field/method clash cites nothing") {
    val p = port("public class A { int x; int y() { return x; } }")
    val _ = p.out
    assertEquals(p.catalog.citedAt(JS.C(46)), Nil)
  }

  // -- JS-C44: java's `sealed`/`permits` against scala's FILE-SCOPED `sealed` ---------------------------------

  test("JS-C44 — a seal whose permitted subtypes are all in THIS file is reproduced exactly") {
    val p = port(
      """public sealed class A permits A.X, A.Y {
        |  public static final class X extends A { }
        |  public static final class Y extends A { }
        |}""".stripMargin)
    assertConsults(p, JS.C(44), fired = true)
    assertEmits(p, "sealed class A")
    assertEquals(p.emitter.emissionDecisions.filter(_.kind == Decision.Kind.WidenedSeal), Nil)
  }

  test("JS-C44 — a seal reaching ANOTHER emitted file has no image, and the widening is RECORDED") {
    // Scala's `sealed` restricts extension to the declaring FILE and there is no `permits` clause
    // to name `p.B` with, so the type ships OPEN — a widening of who may extend it that is
    // invisible in the emitted text, which is exactly why it is a decision and a porter note
    // rather than nothing at all.
    val p = portAll(List(
      "A.java" -> "package p;\npublic sealed class A permits B { }\n",
      "B.java" -> "package p;\npublic final class B extends A { }\n"))
    assertConsults(p, JS.C(44), fired = true)
    assertNotEmits(p, "sealed class A")
    val ds = p.emitter.emissionDecisions.filter(_.kind == Decision.Kind.WidenedSeal)
    assertEquals(ds.map(_.subjectFqn), List("p.A"))
    assertEquals(ds.head.detail.get("elsewhere"), Some("1"))
    assertEmits(p, "porter: widened-seal")
  }

  test("JS-C44 — a PERMITTED subtype this run never parsed widens the seal, and is counted apart") {
    // The two tests above are both about subtypes the run CAN see — one file or two. This is the
    // shape neither of them reaches and the one a seal decided from the survivors gets wrong: java
    // permits `p.B` and `p.C`, the port ships only `p.B` (the other is excluded, refused, or
    // another module's), and every surviving subtype is in this very file. Read off the parsed
    // extends-edges alone the seal looks EXACT and `sealed p.A` ships — and whatever supplies
    // `p.C`, an injected shim or §4.45's consumer, then cannot extend a type java said it could.
    // Nothing would report it: only the widening is recorded, so a wrongful seal is a decision NOT
    // taken and no instrument has a row for one.
    val p = portAll(List(
      "A.java" -> "package p;\npublic sealed class A permits A.B, C {\n  public static final class B extends A { }\n}\n",
      "D.java" -> "package p;\npublic class D { }\n"))
    assertConsults(p, JS.C(44), fired = true)
    assertNotEmits(p, "sealed class A")
    val ds = p.emitter.emissionDecisions.filter(_.kind == Decision.Kind.WidenedSeal)
    assertEquals(ds.map(_.subjectFqn), List("p.A"))
    // the surviving subtype is in THIS file, so the file-scope half is satisfied and says so —
    // the permits half is the whole of the reason, and the detail has to be able to say which.
    assertEquals(ds.head.detail.get("elsewhere"), Some("0"))
    assertEquals(ds.head.detail.get("permitted"), Some("2"))
    assertEquals(ds.head.detail.get("unaccounted"), Some("1"))
    assertEmits(p, "porter: widened-seal")
  }

  test("JS-C44 — an ordinary class is consulted, does not fire, and records nothing") {
    val p = port("public class A { }")
    assertConsults(p, JS.C(44))
    assertEquals(p.emitter.emissionDecisions.filter(_.kind == Decision.Kind.WidenedSeal), Nil)
  }

  // -- the OPEN and ABSENT rows: rule (ii) makes CONSULTING one a finding ------------------------------------

  test("JS-C12 / JS-C42 — an OPEN row is the WORK LIST and is never consulted") {
    // JS-C12 ATTACHES — a forward reference is decided where the field it reads renders — so it is
    // an `undischarged` hole on every port, which is exactly what a work list is. JS-C42 has no
    // surface at all and says so.
    //
    // JS-C22 and JS-C23 USED TO BE ON THIS LIST and are three tests below: both became `Partial`
    // when the risk counter landed, which is exactly the flip the sentence this test used to carry
    // asked for. They are the worked example of the difference between "no surface exists" and "the
    // surface counts the RISK and refuses the resolution".
    val p = port("public class A { int a = b; int b = 1; }")
    List(JS.C(12), JS.C(42)).foreach { id =>
      assertNotConsults(p, id)
      assert(Differences.byId(id).status.isOpen, s"$id is no longer Open — flip this test with it")
    }
  }

  test("JS-C22 — java's THREE PHASES against scala's one: a vararg candidate beside a fixed-arity one is a counted RISK") {
    // Java tries the fixed-arity phases FIRST and reaches the vararg one only if both fail, so
    // javac bound `f("x")` to `f(String)`. Scala has no such staging. Neither compiler rejects the
    // program, which is why this is a count and not an error — and why the row is `Partial` and not
    // `Handled`: WHICH member scalac binds is not modelled (`ENGINE-LIMITS.md` T17).
    val p = port(
      """public class A {
        |  void f(String a) { }
        |  void f(String... a) { }
        |  void go() { f("x"); }
        |}
        |""".stripMargin)
    assertConsults(p, JS.C(22), fired = true)
    assert(!Differences.byId(JS.C(22)).status.isOpen)
  }

  test("…and a call with ONE candidate consults the row and does NOT fire — the answer at the overwhelming majority of calls") {
    val p = port("public class A { void f(String a) { } void go() { f(\"x\"); } }")
    assertConsults(p, JS.C(22))
    assertConsults(p, JS.C(23))
  }

  test("JS-C23 — scala's relative-weight rule prefers the NON-GENERIC alternative and java's does not") {
    val p = port(
      """public class A {
        |  <T> void f(T a) { }
        |  void f(String a) { }
        |  void go() { f("x"); }
        |}
        |""".stripMargin)
    assertConsults(p, JS.C(23), fired = true)
    assert(!Differences.byId(JS.C(23)).status.isOpen)
  }

  test("JS-C43 — a construct the frontend ABSORBS SILENTLY has no arm to owe a consult") {
    // This test used to hold JS-C30 too, and the pair is worth reading for the difference the
    // local-class wave made. Both were `Absent` and neither owed a consult, for two DIFFERENT
    // reasons: a `record` is ABSORBED SILENTLY — it extends `CtClass`, the class arm takes it, and
    // no arm is even aware a record was there — while a method-local class was REFUSED, which is
    // an absence a lowering arm can simply be written for. It now is (`JS-C30`, four tests above),
    // and only the absorbed one is left. What measures this one is the other instrument,
    // `SpoonKinds` plus `NodeKindTotalitySpec`.
    val p = port("public class A { int x = 1; }")
    assertNotConsults(p, JS.C(43))
    assert(Differences.byId(JS.C(43)).status.isInstanceOf[Status.Absent])
  }

  // -- THE FOURTH SURFACE — the one JS-C row decided while a TYPE is RENDERED --------------------

  test("JS-C29 — a java INNER class is a PATH-DEPENDENT type, and is named by PROJECTION") {
    // Java's nested and inner classes are one syntax and two scala types, and only one of them is
    // path-dependent. Named by simple name inside the enclosing class, `Inner` means `this.Inner`,
    // so the same java type reached through two different instances never unifies — a method
    // bounded `<T extends Inner>` cannot accept an initialiser written against the outer view. The
    // projection is one type for all instances.
    val p = port(
      """public class A {
        |  class Inner { }
        |  Inner make() { return null; }
        |}""".stripMargin)
    assertConsults(p, JS.C(29), fired = true)
    assertEmits(p, "A#Inner")
  }

  test("JS-C29 — a STATIC nested class is the other answer: a VALUE path, not a projection") {
    // It is lowered into the enclosing type's companion `object`, so it is reached through the value
    // path `Outer.Inner` — NOT by simple name (a companion's members are not in the class's scope)
    // and NOT `Outer#Inner` (a projection cannot reach a companion member).
    val p = port(
      """public class A {
        |  static class Nested { }
        |  Nested make() { return null; }
        |}""".stripMargin)
    assertConsults(p, JS.C(29), fired = true)
    assertEmits(p, "A.Nested")
    assertNotEmits(p, "A#Nested")
  }

  test("JS-C29 — a TOP-LEVEL type asks the question and it does not apply") {
    // The negative that makes the two above mean something: every type reference is asked, and a
    // name with no enclosing type in it is the overwhelmingly common answer.
    val p = port("public class A { java.lang.String f() { return null; } }")
    assertConsults(p, JS.C(29))
  }

  // -- the partition, asserted rather than left to a reader ---------------------------------------------------

  test("every JS-C row is wired, declared unmechanised, or owes nothing — and the residue is NAMED") {
    val byKind = Differences.classes.groupBy(d => Differences.leaves(d.attaches) match
      case ls if ls.exists(_.isInstanceOf[Attaches.Unmechanised]) => "unmechanised"
      case ls if ls.exists(_.isInstanceOf[Attaches.LoweredType])  => "lowered-type"
      case ls if ls.exists(_.isInstanceOf[Attaches.RenderedType]) => "rendered-type"
      case ls if ls.exists(_.isInstanceOf[Attaches.Rendered])     => "rendered"
      case ls if ls.exists(_.isInstanceOf[Attaches.Lowered])      => "lowered"
      case ls if ls.exists(_.isInstanceOf[Attaches.Cited])        => "cited"
      case _                                                      => "none")
    assertEquals(byKind.values.map(_.size).sum, Differences.classes.size)
    // THE CHUNK'S OWN BAR. Area C opened with all 47 rows on `Unmechanised` — a claim that nothing
    // was measuring any of them — and the audit point for this wave is whether the rows were really
    // instrumented or renamed to keep a lane green. This is that question in the exact form that can
    // fail: the ONLY rows left are the six whose surface genuinely does not exist, and each names
    // which one it is waiting for.
    assertEquals(byKind.getOrElse("unmechanised", Nil).map(_.id).toSet,
      Set(JS.C(42), JS.C(43)),
      "a JS-C row that is neither a refused construct, an absorbed one, nor a row whose surface " +
        "nobody has built still says nothing is measuring it")
    // JS-C22 and JS-C23 were on that set and left it when the RISK COUNTER landed. The pair is the
    // worked example of the distinction `Unmechanised` is FOR: their sentence said no surface
    // existed to owe a consult, and what did not exist was a RESOLVER — the rendered call is a
    // surface, and what it owes is the risk, not the answer (`ENGINE-LIMITS.md` T17). Both are
    // `Partial`, both attach at `Rendered("Apply")`, and neither claims the resolution.
    assert(!Differences.byId(JS.C(22)).status.isOpen && !Differences.byId(JS.C(23)).status.isOpen)
    // JS-C29 was the sixth and is the one row this area shares with area G's residue: it is decided
    // while RENDERING A TYPE, which is the fourth obligation surface. Asserted here so the row
    // cannot quietly go back.
    assertEquals(byKind.getOrElse("rendered-type", Nil).map(_.id), List(JS.C(29)))
    assert(byKind.getOrElse("rendered", Nil).nonEmpty, "no JS-C row is wired to the RENDERING dispatch")
    assert(byKind.getOrElse("lowered", Nil).nonEmpty, "no JS-C row is wired to the LOWERING dispatch")
    assert(byKind.getOrElse("cited", Nil).nonEmpty, "no JS-C row is wired to the CITATION surface")
    // …and a row claiming NO obligation must not be one the registry calls Open: that would be a gap
    // no lane can see.
    assertEquals(byKind.getOrElse("none", Nil).filter(_.status.isOpen).map(_.id), Nil,
      "an Open row claiming NoObligation is a gap no lane can see")
  }
