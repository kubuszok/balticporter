package balticporter.frontend.spoon

import balticporter.tir.*

/** Locks in body-construct coverage: a single method exercising arrays (new/access/length),
  * classic-for with break/continue, for-each, while, if, try/catch/finally, switch,
  * instanceof, lambda, operators, return. If any construct regresses to `Unsupported`,
  * `fromSource` throws and this fails — a construct-level regression net independent of the
  * corpus. Also checks the call graph survives all of them. */
class SpoonTirBodySpec extends munit.FunSuite:

  private val src =
    """package demo;
      |class Ops {
      |  int[] data;
      |  int sum() {
      |    int total = 0;
      |    for (int i = 0; i < data.length; i++) {
      |      total = total + data[i];
      |      if (total < 0) { continue; }
      |      if (total > 100) { break; }
      |    }
      |    for (Object o : new Object[]{ "a", "b" }) {
      |      if (o instanceof String) { total = total + 1; }
      |    }
      |    while (total > 0) { total = total - 1; }
      |    try { total = risky(); } catch (RuntimeException e) { total = -1; } finally { total = total + 0; }
      |    switch (total) { case 0: total = 0; break; default: total = 9; }
      |    java.util.function.IntUnaryOperator f = x -> x + 1;
      |    return total;
      |  }
      |  int risky() { return 1; }
      |}
      |""".stripMargin

  private val program = SpoonTir.fromSource(src) // throws on any Unsupported construct

  private def member(full: String): SymId =
    program.symbols.all.find(_.fullName == full).map(_.id).getOrElse(fail(s"no member $full"))

  test("a method using every supported construct translates with no Unsupported") {
    // reaching here means fromSource did not throw — all constructs translated.
    assert(program.definitionOf(member("demo.Ops#sum")).isDefined)
  }

  test("the call graph survives all body constructs") {
    val sum   = member("demo.Ops#sum")
    val risky = member("demo.Ops#risky")
    // sum() calls risky() inside a try block — the edge is still traced.
    assert(program.usagesOf(risky, UsageKind.Call).nonEmpty)
    assertEquals(program.callersOf(risky), List(sum))
  }

  test("instanceof records the tested type as a usage") {
    val string = program.symbols.all.find(_.fullName == "java.lang.String").map(_.id)
    assert(string.exists(id => program.usagesOf(id).nonEmpty))
  }

  // second batch: constructs surfaced by the flexmark corpus
  private val src2 =
    """package demo;
      |class More {
      |  int run(int n) {
      |    int i = 0;
      |    int total = 0;
      |    assert n > 0 : "positive";
      |    do { total = total + n; i++; } while (i < n);
      |    synchronized (this) { total = total + 1; }
      |    int[] a = new int[3];
      |    a[i % 3] = a[0]++;                 // inc/dec in expression + array write
      |    int x;
      |    while ((x = a[0]) > 0) { break; }  // assignment as expression
      |    switch (n) {
      |      case 1: total = total + 1;       // genuine fallthrough
      |      case 2: total = total + 2; break;
      |      default: total = 0;
      |    }
      |    return total;
      |  }
      |}
      |""".stripMargin

  test("assert / do-while / synchronized / inc-dec-expr / assign-expr / switch-fallthrough all translate") {
    val p = SpoonTir.fromSource(src2) // throws on any Unsupported
    assert(p.symbols.all.exists(_.fullName == "demo.More#run"))
  }

  test("try-with-resources translates (resources kept structural on the Try)") {
    val p = SpoonTir.fromSource(
      """package demo;
        |import java.io.*;
        |class R {
        |  void go() throws Exception {
        |    try (BufferedReader r = new BufferedReader(new FileReader("x"))) { r.readLine(); }
        |    catch (IOException e) { }
        |  }
        |}
        |""".stripMargin
    )
    assert(p.symbols.all.exists(_.fullName == "demo.R#go"))
  }

  /** CLAUDE.md §4.4 row 7, for the shape a String switch takes.
    *
    * Java FALLS OUT of a `switch` when no label matches; Scala's `match` throws `MatchError`. The
    * fall-out is routinely the NORMAL path — a three-label switch over a comparison operator
    * leaves the variable at whatever the code before it set — so the difference is not an edge
    * case, and it costs no compile error and no check count: the emitted `match` is valid Scala
    * that throws where java returned.
    *
    * Asserted on the TREE rather than the emitted text because this is the frontend's contract:
    * every `Tree.Match` built from a java `switch` carries a default arm, whatever the scrutinee's
    * type, and `TirEmitter` renders `isDefault` as `case _`. */
  private def matchesOf(p: Program, member: String): List[Tree.Match] =
    val sym = p.symbols.all.find(_.fullName == member).map(_.id)
      .getOrElse(fail(s"no member $member"))
    p.definitionOf(sym) match
      case Some(d: Tree.DefDef) =>
        given Program = p
        StandardTraversal.scanTerm(d.rhs.getOrElse(fail("no body")), List.empty[Tree.Match]) {
          case (acc, m: Tree.Match) => m :: acc
          case (acc, _)             => acc
        }
      case _ => fail(s"$member is not a method")

  test("a String switch with NO default gains the fall-out arm java already had") {
    val p = SpoonTir.fromSource(
      """package demo;
        |class Cmp {
        |  boolean apply(String op, int l, int r) {
        |    boolean out = false;
        |    switch (op) {
        |      case "<":  out = l <  r; break;
        |      case "<=": out = l <= r; break;
        |      case ">":  out = l >  r; break;
        |    }
        |    return out;
        |  }
        |}
        |""".stripMargin)
    val ms = matchesOf(p, "demo.Cmp#apply")
    assertEquals(ms.size, 1)
    // without this arm every operator OUTSIDE the three labels — which java answers `false` —
    // becomes a MatchError at run time, and no compile and no check says so.
    assert(clue(ms.head.cases.map(_.isDefault)).contains(true))
    assertEquals(ms.head.cases.count(_.isDefault), 1)
  }

  test("a switch that HAS a default gains no second one") {
    val p = SpoonTir.fromSource(
      """package demo;
        |class Cmp2 {
        |  int apply(String op) {
        |    switch (op) { case "a": return 1; default: return 2; }
        |  }
        |}
        |""".stripMargin)
    assertEquals(matchesOf(p, "demo.Cmp2#apply").head.cases.count(_.isDefault), 1)
  }

  // -- a java VARARG PACK stops at the program's edge (`ENGINE-LIMITS.md` K6.5, third case) ------
  //
  // `T...` is emitted as `Array[T]`, so a positional call has to materialise the array java would
  // have built — and that is right only while BOTH halves are ours. An EXTERNAL callee's half is a
  // class file, where scalac reads `T...` as a REPEATED parameter, so the pack is one argument too
  // many. The loud face is `Paths.get(".", Array[String]())`; the silent one is
  // `String.format(fmt, Array[Object](a, b))`, which CONFORMS (`Array[Object] <: Object`) and
  // passes the whole array as one `%s`.

  private def callsIn(p: Program, member: String): List[Tree.Apply] =
    given Program = p
    p.definitionOf(p.symbols.all.find(_.fullName == member).map(_.id).getOrElse(fail(s"no $member"))) match
      case Some(d: Tree.DefDef) =>
        StandardTraversal.scanTerm(d.rhs.getOrElse(fail("no body")), List.empty[Tree.Apply]) {
          case (acc, a: Tree.Apply) => a :: acc
          case (acc, _)             => acc
        }
      case _ => fail(s"$member is not a method")

  private val varargProgram = SpoonTir.fromSource(
    """package demo;
      |class Va {
      |  static int pick(String... xs) { return xs.length; }
      |  String use() {
      |    pick("a", "b");
      |    java.nio.file.Paths.get(".");
      |    return String.format("%s %s", "a", "b");
      |  }
      |}
      |""".stripMargin)

  private def lastArgOf(name: String): Option[Tree] =
    callsIn(varargProgram, "demo.Va#use")
      .find(a => varargProgram.symbolOf(a.method).exists(_.name == name))
      .flatMap(_.args.lastOption)

  test("an IN-PROGRAM vararg call still materialises the array both halves agree on") {
    assert(clue(lastArgOf("pick")).exists(_.isInstanceOf[Tree.NewArray]))
  }

  test("an EXTERNAL vararg call packs into Tree.Repeated — the elements, never an Array") {
    // `String.format(String, Object...)` — the SILENT face: an Array here compiles and is wrong.
    lastArgOf("format") match
      case Some(Tree.Repeated(es, _, _)) => assertEquals(es.size, 2)
      case other                         => fail(s"expected Repeated, got $other")
  }

  test("ZERO variadic arguments at an external callee is an EMPTY Repeated, not an empty Array") {
    // `Paths.get(String, String...)` called as `get(".")`. An `Array[String]()` here is the loud
    // face (`Found: Array[String] / Required: String`); an empty Repeated renders as nothing at
    // all, which is what java's own call site says.
    lastArgOf("get") match
      case Some(Tree.Repeated(es, _, _)) => assertEquals(es, Nil)
      case other                         => fail(s"expected an empty Repeated, got $other")
  }

  // -- …and the MIRROR: java PASSES AN ARRAY THROUGH the same slot (K6.5, fourth case) -----------
  //
  // `String.format(fmt, args)` is java's own vararg-FORWARDING idiom, not an edge case. Where the
  // callee is ours the array is passed as it stands, because the parameter is emitted `Array[T]`.
  // Where it is a class file the bare array conforms as ONE element: silent where the repeated
  // element is `Object` (measured on 3.8.4 — `String.format("%s-%s", arr)` prints the array for the
  // first `%s` and throws `MissingFormatArgumentException` for the second), loud otherwise.

  private val passThroughProgram = SpoonTir.fromSource(
    """package demo;
      |class Fwd {
      |  static int pick(String... xs) { return xs.length; }
      |  String forward(Object[] args, String[] parts) {
      |    pick(parts);
      |    java.nio.file.Paths.get(".", parts);
      |    return String.format("%s %s", args);
      |  }
      |}
      |""".stripMargin)

  private def lastFwdArgOf(name: String): Option[Tree] =
    callsIn(passThroughProgram, "demo.Fwd#forward")
      .find(a => passThroughProgram.symbolOf(a.method).exists(_.name == name))
      .flatMap(_.args.lastOption)

  test("an array passed through an EXTERNAL vararg slot is SPREAD — the silent, Object-element face") {
    // `String.format(String, Object...)`: `Array[Object] <: Object`, so the unspread array COMPILES
    // and formats as a single `%s`. Nothing but this can see it — no error, no moved count.
    lastFwdArgOf("format") match
      case Some(Tree.Spread(_, _, _)) => ()
      case other                      => fail(s"expected Spread, got $other")
  }

  test("…and the loud face is spread by the SAME rule, not by a second one") {
    // `Paths.get(String, String...)` forwarded a `String[]`: unspread it reads
    // `Found: Array[String] / Required: String`. One rule, both faces.
    lastFwdArgOf("get") match
      case Some(Tree.Spread(_, _, _)) => ()
      case other                      => fail(s"expected Spread, got $other")
  }

  test("an IN-PROGRAM callee keeps the PASS-THROUGH — the negative test for the mirror") {
    // `pick(String...)` is emitted `def pick(xs: Array[String])`, so both halves agree on the bare
    // array and a spread here would be an argument of the wrong shape.
    lastFwdArgOf("pick") match
      case Some(_: Tree.Spread) => fail("an owned vararg callee must keep the array as it stands")
      case Some(_)              => ()
      case None                 => fail("no argument at all")
  }

  // -- T14: a java STATIC is INHERITED by every subclass; a scala companion inherits NOTHING ------
  //
  // `ZoneOffset.systemDefault()` is ordinary java: `systemDefault` is declared `static` on
  // `java.time.ZoneId`, `ZoneOffset extends ZoneId`, and java lets a static be named through ANY
  // subclass. Emitted verbatim that is `value systemDefault is not a member of object
  // java.time.ZoneOffset`, every time.
  //
  // Java resolved the member STATICALLY, so the receiver that means the same thing in both
  // languages is the member's DECLARING type — which is the interned symbol's OWNER, never a test
  // on the written name (`CLAUDE.md` §4.56).

  private val staticProgram = SpoonTir.fromSources(List(
    "Base.java"   -> """package demo;
                       |public class Base { public static int make() { return 1; } public static final int SEED = 3; }
                       |""".stripMargin,
    "Sub.java"    -> """package demo;
                       |public class Sub extends Base { }
                       |""".stripMargin,
    "Consts.java" -> """package demo;
                       |public interface Consts { int MAX = 7; }
                       |""".stripMargin,
    "Impl.java"   -> """package demo;
                       |public class Impl implements Consts { }
                       |""".stripMargin,
    "Use.java"    -> """package demo;
                       |public class Use {
                       |  int viaSubclass()       { return Sub.make(); }
                       |  int viaOwnClass()       { return Base.make(); }
                       |  int fieldViaSubclass()  { return Sub.SEED; }
                       |  int fieldViaOwnClass()  { return Base.SEED; }
                       |  int fieldViaInterface() { return Impl.MAX; }
                       |  Object jdkViaSubclass() { return java.time.ZoneOffset.systemDefault(); }
                       |  Object jdkViaOwnClass() { return java.time.ZoneId.systemDefault(); }
                       |}
                       |""".stripMargin))

  /** the type the emitted receiver NAMES, for the one static access in `demo.Use#<name>`. */
  private def staticReceiverIn(name: String): String =
    given Program = staticProgram
    val id = staticProgram.symbols.all.find(_.fullName == s"demo.Use#$name").map(_.id)
      .getOrElse(fail(s"no member demo.Use#$name"))
    staticProgram.definitionOf(id) match
      case Some(d: Tree.DefDef) =>
        StandardTraversal.scanTerm(d.rhs.getOrElse(fail("no body")), List.empty[String]) {
          case (acc, Tree.Select(Tree.Ident(q, _, _), _, _, _)) =>
            staticProgram.symbolOf(q).map(_.fullName).getOrElse("?") :: acc
          case (acc, _) => acc
        }.headOption.getOrElse(fail(s"no static access in demo.Use#$name"))
      case _ => fail(s"demo.Use#$name is not a method")

  test("a static METHOD reached through a SUBCLASS name is emitted at its DECLARING type") {
    assertEquals(staticReceiverIn("viaSubclass"), "demo.Base")
  }

  test("…and the same in a JDK hierarchy, where no companion re-export can reach it") {
    // `ZoneOffset extends ZoneId`. An in-program parent's statics are ALSO delivered by the
    // companion re-export `TirEmitter.classDef` writes; an EXTERNAL parent has no such reach, so
    // this is the case with no second mechanism standing behind it.
    assertEquals(staticReceiverIn("jdkViaSubclass"), "java.time.ZoneId")
  }

  test("a static FIELD reached through a SUBCLASS name is emitted at its DECLARING type") {
    assertEquals(staticReceiverIn("fieldViaSubclass"), "demo.Base")
  }

  test("a static field reached through an IMPLEMENTING class resolves to the INTERFACE") {
    // java interface constants are `static` and inherited through `implements` — `CLAUDE.md` §1(a)'s
    // own example. A walk up the SUPERCLASS chain alone never reaches one.
    assertEquals(staticReceiverIn("fieldViaInterface"), "demo.Consts")
  }

  test("a static reached through its OWN declaring type is left alone — the negative") {
    assertEquals(staticReceiverIn("viaOwnClass"), "demo.Base")
    assertEquals(staticReceiverIn("fieldViaOwnClass"), "demo.Base")
    assertEquals(staticReceiverIn("jdkViaOwnClass"), "java.time.ZoneId")
  }
