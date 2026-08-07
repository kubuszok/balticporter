package balticporter.corpus

import balticporter.catalog.JS
import balticporter.testkit.PortSuite
import balticporter.tir.Decision

/** JAVA `record` — `JS-C43`, JLS 8.10.
  *
  * ZERO CORPUS SITES. No library in the corpus declares a record (they predate SE16), so these
  * fixtures are the whole of the evidence and there is no port whose numbers could move if one of
  * them were wrong — the same position `SwitchExpressionSpec` opened in, and the same discipline:
  * the assertions about EMITTED TEXT say what the engine writes, and the assertions at the bottom
  * COMPILE AND RUN the shapes it writes, because "does `Tuple1` work as a one-element extractor",
  * "does `Double.compare` make `NaN` equal itself" and "what does `String.valueOf` do to a
  * `char[]`" are claims about scala and about the JDK that no text assertion settles.
  *
  * Every expected value below was MEASURED against `javac` before it was written down — the
  * `toString` renderings, the `hashCode` numbers, and both float edge cases. They are javac's
  * answers, not this engine's.
  */
class RecordSpec extends PortSuite:

  private def rec(body: String, header: String = "public record Point(int x, int y)") =
    port(s"package p;\n$header {\n$body}\n")

  // ---------------------------------------------------------------------------------------------
  // THE DECLARATION
  // ---------------------------------------------------------------------------------------------

  test("a record's four derived members are written out, and the row is consulted") {
    val p = rec("")
    assertEmits(p, "override def equals(o$rec: scala.Any): scala.Boolean")
    assertEmits(p, "override def hashCode(): scala.Int")
    assertEmits(p, "override def toString(): java.lang.String")
    assertEmits(p, "def unapply(r$rec: Point): (scala.Int, scala.Int)")
    // …and the STRUCTURAL half, which the text cannot give: the difference was considered here and
    // it APPLIED. A record whose emitted text happened to be right without the row being consulted
    // is exactly the shape §2.8's obligation surface exists to catch.
    assertConsults(p, JS.C(43), fired = true)
    // …through `emissionDecisions` and not `assertDecides`, which reads the PIPELINE's log: this is
    // a decision the emitter takes while rendering, exactly as `WidenedSeal` is.
    val ds = p.emitter.emissionDecisions.filter(_.kind == Decision.Kind.RecordMembers)
    assertEquals(ds.map(_.subjectFqn), List("p.Point"))
    assertEquals(ds.head.detail.get("components"), Some("2"))
    assertEquals(ds.head.detail.get("synthesised"), Some("equals,hashCode,toString,unapply"))
    assertEquals(ds.head.detail.get("declared"), Some("none"))
    // the residue no image closes, named on the decision so a reader of the emitted class can find it
    assert(clue(ds.head.detail("reflective")).contains("isRecord=false"))
    // …and the note beside the code (§4.575), which is the only form §4.45's agent can find
    assertEmits(p, "/* porter: record-members reason=universal rule=record-members(JS-C43)")
  }

  test("…and the class still extends java.lang.Record, which is now CONCRETE") {
    // The parent is what made the pre-lowering emission fail at §3's gate rather than silently:
    // `java.lang.Record` declares all three abstract. Keeping it is the faithful half of a residue
    // whose other half cannot be closed — `x instanceof java.lang.Record` answers as java's does.
    val p = rec("")
    assertEmits(p, "extends java.lang.Record")
    assertEmits(p, "final class Point")
  }

  test("toString is java's FORMAT, not a case class's — the three ways they differ") {
    // javac: `Point[x=1, y=2]`. A scala case class: `Point(1,2)`. Bracket, field names, space.
    val p = rec("")
    assertEmits(p, """"Point[" + "x=" + java.lang.String.valueOf(this.x$field) + ", " + "y=" + java.lang.String.valueOf(this.y$field) + "]"""")
  }

  test("hashCode is javac's 31-fold from zero, per component, through the WRAPPER's static") {
    val p = rec("")
    assertEmits(p, "var hash$rec: scala.Int = 0")
    assertEmits(p, "hash$rec = hash$rec * 31 + java.lang.Integer.hashCode(this.x$field)")
  }

  test("equals compares double and float with compare(), and references with Objects.equals") {
    val p = rec("", "public record Mixed(double d, float f, String s)")
    assertEmits(p, "java.lang.Double.compare(this.d$field, that$rec.d$field) == 0")
    assertEmits(p, "java.lang.Float.compare(this.f$field, that$rec.f$field) == 0")
    assertEmits(p, "java.util.Objects.equals(this.s$field.asInstanceOf[java.lang.Object], that$rec.s$field.asInstanceOf[java.lang.Object])")
  }

  test("a member the RECORD declares replaces the derived one — by NAME AND ARITY") {
    val p = rec("  @Override public String toString() { return \"OWN\"; }\n")
    assert(clue(p.out).contains("return \"OWN\""), p.out)
    // exactly one `toString`, and it is java's
    assertEquals(clue(p.out).sliding("def toString".length).count(_ == "def toString"), 1, p.out)
    assertEmits(p, "override def equals(o$rec: scala.Any)")
  }

  test("…and an unrelated overload of the same NAME does not count as one") {
    // JLS 8.10.3's rule is about the member with the right SIGNATURE. A record may declare
    // `equals(int, int)` beside java's own, and reading the bare name would then suppress the
    // derived `equals(Object)` and leave the class abstract — `ENGINE-LIMITS.md` K5.7's shape at a
    // synthesis instead of at a body substitution.
    val p = rec("  boolean equals(int a, int b) { return a == b; }\n")
    assertEmits(p, "override def equals(o$rec: scala.Any): scala.Boolean")
  }

  test("the ZERO-component record still gets all four — java does") {
    val p = port("package p;\npublic record Empty() { }\n")
    assertEmits(p, "override def equals(o$rec: scala.Any): scala.Boolean = o$rec.isInstanceOf[Empty]")
    assertEmits(p, "override def hashCode(): scala.Int = 0")
    assertEmits(p, """override def toString(): java.lang.String = "Empty[]"""")
    assertEmits(p, "def unapply(r$rec: Empty): scala.Boolean = true")
  }

  test("the ONE-component extractor is a Tuple1 — the only product type of arity one") {
    val p = port("package p;\npublic record One(String only) { }\n")
    assertEmits(p, "def unapply(r$rec: One): scala.Tuple1[java.lang.String] = scala.Tuple1(r$rec.only())")
  }

  test("a GENERIC record's extractor re-declares the class's own parameters") {
    val p = port("package p;\npublic record Box<T>(T a, int n) { }\n")
    assertEmits(p, "def unapply[T <: java.lang.Object](r$rec: Box[T]): (T, scala.Int)")
    assertEmits(p, "case that$rec: Box[?] =>")
  }

  test("the extractor deconstructs through the ACCESSORS, which is what java's record pattern reads") {
    // JLS 14.30.1. An OVERRIDDEN accessor is the whole of the difference: java's `toString`/`equals`
    // read the FIELD (measured: `Over[x=3]` for a record whose accessor doubles), and a record
    // pattern binds the ACCESSOR's answer (measured: `6`). A case class's generated `unapply` reads
    // the parameter, which is the field — so it would bind `3`.
    val p = rec("  public int y() { return y * 2; }\n")
    assertEmits(p, "def unapply(r$rec: Point): (scala.Int, scala.Int) = (r$rec.x(), r$rec.y())")
    assertEmits(p, """"y=" + java.lang.String.valueOf(this.y$field)""")
  }

  test("a record that declares its own `unapply` keeps it — no duplicate definition") {
    val p = rec("  static Object unapply(Point p) { return null; }\n")
    assertEquals(clue(p.out).sliding("def unapply".length).count(_ == "def unapply"), 1, p.out)
  }

  // ---------------------------------------------------------------------------------------------
  // THE THREE THINGS THE PARSER HANDS OVER WRONG
  //
  // None of these is visible to a compile, and two of them are silent at run time as well. Each
  // assertion is written against what the emitted class must SAY, because the fixture path has no
  // way to run it.
  // ---------------------------------------------------------------------------------------------

  test("a COMPACT constructor gets JLS 8.10.4's appended field assignments") {
    // Spoon models the written body and not the appended half, so every backing field kept its
    // default and every accessor answered `0` — with a green compile.
    val p = rec("", "public record Point(int x, int y)\n  { if (x < 0) throw new IllegalArgumentException(\"neg\"); }\npublic record Unused(int q)")
    assertEmits(p, "this.x$field = x$p")
    assertEmits(p, "this.y$field = y$p")
    // …AFTER the validation, which is what lets a compact constructor normalise its arguments.
    val body = p.out
    assert(clue(body).indexOf("IllegalArgumentException") < clue(body).indexOf("this.x$field = x$p"), body)
  }

  test("the canonical constructor's parameters are the HEADER's order, not the parser's field order") {
    // Spoon builds the implicit constructor from `getFields()`, which for
    // `record Prims(boolean, byte, short, char, int, long, float, double)` hands back
    // `(bo, du, fl, lo, in, ch, sh, by)`. The funnel promotes those into the emitted class's own
    // parameter list while every translated `new Prims(…)` keeps java's argument order.
    val p = port("package p;\npublic record Prims(boolean bo, byte by, short sh, char ch, int in, long lo, float fl, double du) { }\n")
    assertEmits(p, "final class Prims(bo$p: scala.Boolean, by$p: scala.Byte, sh$p: scala.Short, ch$p: scala.Char, " +
      "in$p: scala.Int, lo$p: scala.Long, fl$p: scala.Float, du$p: scala.Double)")
  }

  test("a NESTED record gets a canonical constructor and accessors that read the FIELD") {
    // Spoon synthesises neither for a nested declaration (`getConstructors.size` is 1 for a
    // top-level record and 0 for a nested one, probed on one file), and the accessor's field read
    // does not resolve — so `def bo()` emitted `return bo`, which in scala's ONE namespace is the
    // method, and the accessor calls itself forever. Green compile, StackOverflow at the first read.
    val p = port("package p;\npublic class Outer {\n  public record In(int a, String b) { }\n}\n")
    assertEmits(p, "final class In(a$p: scala.Int, b$p: java.lang.String)")
    assertEmits(p, "this.a$field = a$p")
    assertEmits(p, "return this.a$field")
    assert(!clue(p.out).contains("return a\n"), p.out)
  }

  // ---------------------------------------------------------------------------------------------
  // THE SCALAC PROBE — the emitted shapes, COMPILED AND RUN, against javac's own answers
  //
  // Hand-written scala in `TirEmitter.recordMembers`' exact shape. Every expected value here was
  // produced by `javac` first.
  // ---------------------------------------------------------------------------------------------

  /** the emitted image of `record Pt(int x, int y)`. */
  final class Pt(x$p: scala.Int, y$p: scala.Int) extends java.lang.Record:
    var x$field: scala.Int = 0
    var y$field: scala.Int = 0
    this.x$field = x$p
    this.y$field = y$p
    def x(): scala.Int = this.x$field
    def y(): scala.Int = this.y$field
    override def equals(o$rec: scala.Any): scala.Boolean = o$rec match
      case that$rec: Pt => this.x$field == that$rec.x$field && this.y$field == that$rec.y$field
      case _            => false
    override def hashCode(): scala.Int =
      var hash$rec: scala.Int = 0
      hash$rec = hash$rec * 31 + java.lang.Integer.hashCode(this.x$field)
      hash$rec = hash$rec * 31 + java.lang.Integer.hashCode(this.y$field)
      hash$rec
    override def toString(): java.lang.String =
      "Pt[" + "x=" + java.lang.String.valueOf(this.x$field) + ", " + "y=" + java.lang.String.valueOf(this.y$field) + "]"

  object Pt:
    def unapply(r$rec: Pt): (scala.Int, scala.Int) = (r$rec.x(), r$rec.y())

  /** the emitted image of `record Ref(String s, double d)` — the reference and float arms. */
  final class Ref(s$p: java.lang.String, d$p: scala.Double) extends java.lang.Record:
    var s$field: java.lang.String = null
    var d$field: scala.Double = 0.0d
    this.s$field = s$p
    this.d$field = d$p
    override def equals(o$rec: scala.Any): scala.Boolean = o$rec match
      case that$rec: Ref =>
        java.util.Objects.equals(this.s$field.asInstanceOf[java.lang.Object], that$rec.s$field.asInstanceOf[java.lang.Object]) &&
        java.lang.Double.compare(this.d$field, that$rec.d$field) == 0
      case _ => false
    override def hashCode(): scala.Int =
      var hash$rec: scala.Int = 0
      hash$rec = hash$rec * 31 + java.util.Objects.hashCode(this.s$field.asInstanceOf[java.lang.Object])
      hash$rec = hash$rec * 31 + java.lang.Double.hashCode(this.d$field)
      hash$rec
    override def toString(): java.lang.String =
      "Ref[" + "s=" + java.lang.String.valueOf(this.s$field.asInstanceOf[java.lang.Object]) +
        ", " + "d=" + java.lang.String.valueOf(this.d$field) + "]"

  /** the emitted image of `record One(String only)` — the arity-1 extractor. */
  final class One1(only$p: java.lang.String) extends java.lang.Record:
    var only$field: java.lang.String = null
    this.only$field = only$p
    def only(): java.lang.String = this.only$field
    override def equals(o$rec: scala.Any): scala.Boolean = o$rec.isInstanceOf[One1]
    override def hashCode(): scala.Int = 0
    override def toString(): java.lang.String = "One[]"

  object One1:
    def unapply(r$rec: One1): scala.Tuple1[java.lang.String] = scala.Tuple1(r$rec.only())

  test("PROBE: a plain class MAY extend java.lang.Record, and the three members satisfy it") {
    // javac REFUSES `extends java.lang.Record` outright (JLS 8.1.4) and scalac accepts it, which is
    // why the flag and not the parent is what the engine reasons from. What it buys is the one
    // observable a scala class can keep: `instanceof java.lang.Record`.
    val p = new Pt(1, 2)
    assert(p.isInstanceOf[java.lang.Record])
  }

  test("PROBE: toString, hashCode and equals answer exactly what javac's record answers") {
    assertEquals(new Pt(1, 2).toString, "Pt[x=1, y=2]")   // javac: Pt[x=1, y=2]
    assertEquals(new Pt(1, 2).hashCode(), 33)              // javac: 33
    assertEquals(new Ref("ab", 1.5d).hashCode(), 1073313791) // javac: 1073313791
    assertEquals(new Ref(null, 1.5d).toString, "Ref[s=null, d=1.5]") // javac: Ref[s=null, d=1.5]
    assert(new Pt(1, 2).equals(new Pt(1, 2)))
    assert(!new Pt(1, 2).equals(new Pt(1, 3)))
    assert(!new Pt(1, 2).equals("x"))
  }

  test("PROBE: Double.compare makes NaN equal itself and +0.0 UNequal -0.0 — scala's `==` does neither") {
    // javac's record: NaN eq = true, +0/-0 eq = false. A scala case class over the same components
    // answers false and true, which is the pair of silent divergences a case-class image would ship.
    assert(new Ref(null, scala.Double.NaN).equals(new Ref(null, scala.Double.NaN)))
    assert(!new Ref(null, 0.0d).equals(new Ref(null, -0.0d)))
    assert(scala.Double.NaN != scala.Double.NaN)
    assert(0.0d == -0.0d)
  }

  test("PROBE: the derived extractors deconstruct, at arity 2 and at arity 1") {
    val any: scala.Any = new Pt(3, 4)
    any match
      case Pt(a, b) => assertEquals((a, b), (3, 4))
      case _        => fail("the arity-2 extractor did not match")
    (new One1("z"): scala.Any) match
      case One1(s) => assertEquals(s, "z")
      case _       => fail("the Tuple1 extractor did not match")
  }

  test("PROBE: a REFERENCE component prints through String.valueOf(Object), which a char[] needs") {
    // Unascribed, `String.valueOf(this.f)` on a `char[]` resolves `valueOf(char[])` and prints the
    // CHARACTERS; javac's record concat uses `valueOf(Object)` and prints `[C@…`. This is what the
    // `asInstanceOf[java.lang.Object]` on every reference component is for.
    val cs = Array('a', 'b')
    assertEquals(java.lang.String.valueOf(cs), "ab")
    assert(java.lang.String.valueOf(cs.asInstanceOf[java.lang.Object]).startsWith("[C@"))
  }
