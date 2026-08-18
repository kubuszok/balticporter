package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.PortSuite
import balticporter.tir.{OmissionCheck, Pipeline}

/** A ported java enum IS a `java.lang.Enum` — the shape that says so, and the shapes that cannot.
  *
  * ==Why the shape had to change==
  * `class E extends Enum<E>` is a TYPE fact, not decoration: `EnumSet.noneOf`, `EnumMap`,
  * `Comparable<E>` and any library that writes `<E extends Enum<E> & I>` bound on it. A
  * `sealed abstract class` may not name that supertype at all — scalac answers *"only enums defined
  * with the enum syntax can"* — so a port whose enums are sealed classes cannot satisfy one such
  * bound at any call site anywhere. Measured on flexmark, whose `BitFieldSet<E extends Enum<E> &
  * BitField>` is exactly that shape.
  *
  * ==…and the `enum` syntax cannot express every java enum==
  * A scala 3 enum CASE has no template body, and a member of the emitted type may not collide with
  * one java made FINAL on `java.lang.Enum` — which java itself permits, because java has two
  * namespaces and a FIELD called `name` sits happily beside the final `name()`. Each such enum keeps
  * the pre-existing sealed shape and is COUNTED, never silently chosen.
  */
class EnumJavaLangEnumSpec extends PortSuite:

  private def emit(src: String) =
    val p = Pipeline.run(SpoonTir.fromSource(src), Nil)
    (new TirEmitter(p).emit, OmissionCheck.enumShapeRefusals(p))

  test("an expressible enum is a scala 3 `enum extends java.lang.Enum[X]`, with its interfaces after it") {
    val (out, fs) = emit(
      """package en1;
        |interface Bits { int getBits(); }
        |enum Flags implements Bits {
        |  LINK_TEXT(3),
        |  NODE_TEXT,
        |  ;
        |  final int bits;
        |  Flags() { this(1); }
        |  Flags(int bits) { this.bits = bits; }
        |  public int getBits() { return bits; }
        |}
        |""".stripMargin)
    // the promoted parameter carries the modifiers of the field it SUPERSEDES — `final int bits` is
    // package-private and final in java (`EnumPromotedParamFlagsSpec`).
    assert(clue(out).contains("enum Flags(private[en1] val bits: scala.Int) extends java.lang.Enum[Flags] with en1.Bits"))
    // the constants, with the ROOT constructor's arguments — `NODE_TEXT` named the delegating
    // overload and java ran `this(1)`, which is the T11.5 derivation this arm shares.
    assert(out.contains("case LINK_TEXT extends Flags(3)"))
    assert(out.contains("case NODE_TEXT extends Flags(1)"))
    // FOUR members the sealed shape wrote are absent, and each would be an ERROR rather than a
    // duplicate: `name()`/`ordinal()` are final on java.lang.Enum, `values`/`valueOf` are the
    // desugaring's own companion members.
    assert(!out.contains("def name()"))
    assert(!out.contains("def ordinal()"))
    assert(!out.contains("def values()"))
    assert(!out.contains("def valueOf("))
    assert(!out.contains("sealed abstract class Flags"))
    assert(!out.contains("case object"))
    assertEquals(clue(fs), Nil)
  }

  test("java's `X.values()` loses its parens — and ONLY on an enum this emitter conformed") {
    val (out, _) = emit(
      """package en2;
        |enum Kind { A, B }
        |enum Bodied {
        |  ONE { public int n() { return 1; } },
        |  TWO { public int n() { return 2; } },
        |  ;
        |  public abstract int n();
        |}
        |class Use {
        |  int conformed() { return Kind.values().length; }
        |  int refused()   { return Bodied.values().length; }
        |  int other(java.util.Map<String, String> m) { return m.values().size(); }
        |}
        |""".stripMargin)
    assert(clue(out).contains("en2.Kind.values.length"))
    // the REFUSED enum still emits `def values(): Array[Bodied]`, so its call site keeps java's own
    // shape. One rule, asked of the enum's own declaration, answering differently for two enums in
    // one file is the whole point of reading `EnumShape` rather than the name.
    assert(out.contains("en2.Bodied.values()"))
    // and a `values()` that is not an enum's at all is untouched.
    assert(out.contains(".values()"))
  }

  test("REFUSED — a constant with a class body keeps the sealed shape, and is COUNTED") {
    val (out, fs) = emit(
      """package en3;
        |enum Mode {
        |  SQUARE { public int area(int w) { return w * w; } },
        |  LINE   { public int area(int w) { return w; } },
        |  ;
        |  public abstract int area(int w);
        |}
        |""".stripMargin)
    assert(clue(out).contains("sealed abstract class Mode"))
    assert(out.contains("case object SQUARE extends Mode"))
    assert(!out.contains("java.lang.Enum[Mode]"))
    assertEquals(clue(fs).map(_.owner), List("en3.Mode"))
    assert(fs.head.detail.contains("class body"))
    assert(fs.head.detail.contains("SQUARE"))
  }

  test("REFUSED — a promoted parameter named `name` cannot coexist with the final `Enum.name()`") {
    // java's TWO namespaces let a `String name` constructor parameter sit beside the final method;
    // scala's ONE cannot, and the promotion makes the parameter a member (`CLAUDE.md` §4.55).
    val (out, fs) = emit(
      """package en4;
        |enum Dither {
        |  WREN("Wren"), OTHER("Other");
        |  final String name;
        |  Dither(String name) { this.name = name; }
        |}
        |""".stripMargin)
    assert(clue(out).contains("sealed abstract class Dither(private[en4] val name: java.lang.String)"))
    assertEquals(clue(fs).map(_.owner), List("en4.Dither"))
    assert(fs.head.detail.contains("`name`"))
  }

  test("REFUSED — an enum with NO constants, which a scala 3 `enum` cannot declare") {
    val (out, fs) = emit(
      """package en5;
        |enum Holder {
        |  ;
        |  static int counter = 0;
        |}
        |""".stripMargin)
    assert(clue(out).contains("sealed abstract class Holder"))
    assertEquals(clue(fs).map(_.owner), List("en5.Holder"))
    assert(fs.head.detail.contains("no constants"))
  }

  test("the COMPANION carries java's statics, and is emitted only where java gave it some") {
    val (bare, _) = emit(
      """package en6;
        |enum Plain { A, B }
        |""".stripMargin)
    // no companion at all: the constants are the desugaring's, and an empty `object Plain {}` would
    // be a type a consumer can name that java never had.
    assert(clue(bare).contains("enum Plain extends java.lang.Enum[Plain]"))
    assert(!bare.contains("object Plain"))

    val (withStatics, _) = emit(
      """package en7;
        |enum Sized {
        |  A, B;
        |  static final String TAG = "t";
        |  static Sized first() { return A; }
        |}
        |""".stripMargin)
    assert(clue(withStatics).contains("object Sized"))
    assert(withStatics.contains("def first()"))
  }
