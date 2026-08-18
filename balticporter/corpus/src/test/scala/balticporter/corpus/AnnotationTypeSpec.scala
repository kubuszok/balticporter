package balticporter.corpus

import balticporter.testkit.PortSuite

/** A JAVA `@interface` AND ITS ELEMENTS — `ENGINE-LIMITS.md` T22.
  *
  * The declaration used to emit as `class Tag extends scala.annotation.StaticAnnotation` with
  * every element gone, and nothing in the pipeline could report it: `omissions` counts a dropped
  * annotation USAGE and has no row for an annotation TYPE's own members. What moved instead was
  * `trivia(recovered)` (each accessor's javadoc had no declaration left to sit on) and `srcmap`
  * (four planned-and-never-written members, `!! UNLOCATABLE`) — two artifacts of the SAME
  * discarded rendering.
  *
  * This file was `AbsorbedProbeSpec`, which PINNED that defect and was written to fail when it was
  * fixed. It failed; this is the repair, asserted. The corpus's earlier `@interface`s are all
  * MARKERS, for which the elementless class was the right answer — which is why nothing but a
  * fixture could see this until a library declared an annotation with elements AND read them back.
  */
class AnnotationTypeSpec extends PortSuite:

  test("an @interface's ELEMENTS become the emitted class's parameters, with java's defaults") {
    val p = port("package p;\npublic @interface Tag {\n  String value() default \"none\";\n  int n() default 3;\n}\n")
    // JAVA'S OWN NAME at the parameter, because that is the half a USE writes — `@p.Tag(n = 4)` —
    // and the half `TirEmitter.annots` renders for a port that claims the family. `val`, so the
    // element is a member and the read below resolves; the default is JLS 9.6.2's own.
    assertEmits(p, "class Tag(val value: java.lang.String = \"none\", val n: scala.Int = 3) " +
                   "extends scala.annotation.StaticAnnotation")
  }

  test("an element with NO default takes none — a use must supply it, exactly as java demands") {
    val p = port("package p;\npublic @interface Req {\n  String key();\n}\n")
    assertEmits(p, "class Req(val key: java.lang.String) extends scala.annotation.StaticAnnotation")
    assertNotEmits(p, "val key: java.lang.String =")
  }

  test("a MARKER annotation is unchanged — no parameter list, which is what every earlier port emitted") {
    val p = port("package p;\npublic @interface Marker {\n}\n")
    assertEmits(p, "class Marker extends scala.annotation.StaticAnnotation")
    assertNotEmits(p, "class Marker(")
  }

  test("a READ of an element loses its parens — java's `a.value()` is a field selection here") {
    // The negative that makes this a measurement: `value()` with parens is what the port emitted
    // before, and it does not resolve against a `val`. Scala cannot give one name to both java's
    // roles (a class parameter beside a same-named parameterless `def` is E120), so the read is
    // the side that moves.
    val p = port(
      "package p;\n" +
      "@interface Tag { String value() default \"none\"; }\n" +
      "class Reader { String read(Tag t) { return t.value(); } }\n")
    assertEmits(p, "return t.value")
    assertNotEmits(p, "t.value()")
  }

  test("an EXTERNAL annotation's element keeps its parens — the class file is java's, not this port's") {
    // §4.56: the arm asks PROGRAM OWNERSHIP, never a name and never a package prefix. Scalac reads
    // `java.lang.annotation.Retention` out of a class file, where `value()` is a method.
    //
    // MEASURED, so the next reader is not misled about which conjunct this exercises: with
    // `program.owns` removed the test still passes, because the frontend interns an EXTERNAL symbol
    // with default flags and `isAnnotation` is therefore false for every class-file annotation.
    // Ownership is the conjunct that stays right when it is not (a RESOLUTION ROOT's `@interface` is
    // parsed, carries the flag, and IS emitted — by its own port, with this same rule), and it is
    // kept for that reason rather than because this fixture can fail without it.
    val p = port(
      "package p;\n" +
      "import java.lang.annotation.Retention;\n" +
      "class Reader { Object read(Retention r) { return r.value(); } }\n")
    assertEmits(p, "r.value()")
  }
